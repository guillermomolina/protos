/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */
package com.guillermomolina.protos.execution;

import static com.guillermomolina.protos.execution.ProtosLinuxConfinedFilesystemTestSupport.assumeConfinementPrimitives;
import static com.guillermomolina.protos.execution.ProtosLinuxConfinedFilesystemTestSupport.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * I156 / PLAT073-C invariants of the FFM bridge: libc {@code syscall} linkage, ABI layouts, errno
 * capture, the four {@code RESOLVE_*} restrictions, Landlock availability and per-thread
 * installation, worker independence (T14) and the absence of any JNI/C path.
 *
 * <p>Host-capability gaps are reported as explicit assumptions; a missing primitive never passes.
 */
final class ProtosI156LinuxFfmAbiTest {
    @TempDir Path temporary;

    // ----- layouts and linkage -----

    @Test
    void nativeLayoutsMatchTheLinuxX86_64Abi() {
        assertEquals(24, ProtosLinuxConfinedFilesystemNative.OPEN_HOW.byteSize());
        assertEquals(0, offset(ProtosLinuxConfinedFilesystemNative.OPEN_HOW, "flags"));
        assertEquals(8, offset(ProtosLinuxConfinedFilesystemNative.OPEN_HOW, "mode"));
        assertEquals(16, offset(ProtosLinuxConfinedFilesystemNative.OPEN_HOW, "resolve"));
        assertEquals(24, ProtosLinuxConfinedFilesystemNative.LANDLOCK_RULESET_ATTR.byteSize());
        // landlock_path_beneath_attr is __attribute__((packed)): 8 + 4 bytes, no padding.
        assertEquals(12, ProtosLinuxConfinedFilesystemNative.LANDLOCK_PATH_BENEATH_ATTR.byteSize());
        assertEquals(
                8, offset(ProtosLinuxConfinedFilesystemNative.LANDLOCK_PATH_BENEATH_ATTR, "parent_fd"));
        assertEquals(144, ProtosLinuxConfinedFilesystemNative.STAT_X86_64.byteSize());
        assertEquals(24, offset(ProtosLinuxConfinedFilesystemNative.STAT_X86_64, "st_mode"));
        assertEquals(48, offset(ProtosLinuxConfinedFilesystemNative.STAT_X86_64, "st_size"));
        // long syscall(long, ...): one C long return, the number, six variadic longs.
        assertEquals(7, ProtosLinuxConfinedFilesystemNative.SYSCALL_DESCRIPTOR.argumentLayouts().size());
        assertEquals(
                ValueLayout.JAVA_LONG,
                ProtosLinuxConfinedFilesystemNative.SYSCALL_DESCRIPTOR.returnLayout().orElseThrow());
        assertEquals(8, ValueLayout.ADDRESS.byteSize(), "LP64 pointers are passed as C long");
    }

    @Test
    void libcSyscallResolvesAndErrnoIsCapturedImmediately() throws Exception {
        assumeConfinementPrimitives();
        ProtosLinuxConfinedFilesystemNative.requireBridge();
        // close(-1): libc returns exactly -1 and the captured errno is EBADF, not a raw -9.
        var badDescriptor =
                assertThrows(
                        ProtosLinuxConfinedFilesystemNative.NativeException.class,
                        () -> ProtosLinuxConfinedFilesystemNative.close(-1));
        assertEquals(ProtosLinuxConfinedFilesystemNative.EBADF, badDescriptor.errno());
        assertFalse(badDescriptor.unavailable());
        // A second, different failure captures its own errno, not a stale one.
        var absent =
                assertThrows(
                        ProtosLinuxConfinedFilesystemNative.NativeException.class,
                        () -> ProtosLinuxConfinedFilesystemNative.openRootDirectory(
                                temporary.resolve("absent").toString()));
        assertEquals(ProtosLinuxConfinedFilesystemNative.ENOENT, absent.errno());
    }

    @Test
    void finalSymlinkRootIsRefused() throws Exception {
        assumeConfinementPrimitives();
        Path real = Files.createDirectory(temporary.resolve("real"));
        Path link = Files.createSymbolicLink(temporary.resolve("link"), real);
        var refused =
                assertThrows(
                        ProtosLinuxConfinedFilesystemNative.NativeException.class,
                        () -> ProtosLinuxConfinedFilesystemNative.openRootDirectory(link.toString()));
        // O_DIRECTORY | O_NOFOLLOW on a final link: ENOTDIR (directory check) or ELOOP.
        assertTrue(
                refused.errno() == ProtosLinuxConfinedFilesystemNative.ENOTDIR
                        || refused.errno() == ProtosLinuxConfinedFilesystemNative.ELOOP,
                refused.getMessage());
    }

    // ----- openat2 restrictions -----

    @Test
    void openat2EnforcesBeneathNoSymlinksNoMagicLinksAndErrnoClasses() throws Exception {
        assumeConfinementPrimitives();
        Path root = temporary.resolve("root");
        Files.createDirectories(root.resolve("d"));
        Files.writeString(root.resolve("d/f"), "x");
        Files.createSymbolicLink(root.resolve("link"), root.resolve("d"));
        int fd = ProtosLinuxConfinedFilesystemNative.openRootDirectory(root.toString());
        try {
            int file = ProtosLinuxConfinedFilesystemNative.openBeneath(
                    fd, "d/f", ProtosLinuxConfinedFilesystemNative.O_RDONLY, 0);
            ProtosLinuxConfinedFilesystemNative.close(file);
            assertErrno(ProtosLinuxConfinedFilesystemNative.ELOOP, () -> openBeneath(fd, "link/f"));
            assertErrno(ProtosLinuxConfinedFilesystemNative.EXDEV, () -> openBeneath(fd, "../root/d/f"));
            assertErrno(ProtosLinuxConfinedFilesystemNative.EXDEV, () -> openBeneath(fd, "/etc/hostname"));
            assertErrno(ProtosLinuxConfinedFilesystemNative.ENOENT, () -> openBeneath(fd, "d/absent"));
            assertErrno(
                    ProtosLinuxConfinedFilesystemNative.EEXIST,
                    () -> ProtosLinuxConfinedFilesystemNative.openBeneath(
                            fd,
                            "d/f",
                            ProtosLinuxConfinedFilesystemNative.O_WRONLY
                                    | ProtosLinuxConfinedFilesystemNative.O_CREAT
                                    | ProtosLinuxConfinedFilesystemNative.O_EXCL,
                            0600));
        } finally {
            ProtosLinuxConfinedFilesystemNative.closeQuietly(fd);
        }
    }

    @Test
    void openat2RefusesMagicLinks() throws Exception {
        assumeConfinementPrimitives();
        // /proc/self is itself a symlink; the numeric directory is the real one.
        int procSelf =
                ProtosLinuxConfinedFilesystemNative.openRootDirectory(
                        "/proc/" + ProcessHandle.current().pid());
        try {
            // fd/N and root are kernel magic links inside the same proc mount.
            assertErrno(ProtosLinuxConfinedFilesystemNative.ELOOP, () -> openBeneath(procSelf, "root"));
        } finally {
            ProtosLinuxConfinedFilesystemNative.closeQuietly(procSelf);
        }
    }

    @Test
    void invalidUtf8AndNulNamesAreRejected() {
        assertFalse(ProtosLinuxConfinedFilesystemNames.isEntryName("a\0b"));
        assertFalse(ProtosLinuxConfinedFilesystemNames.isEntryName("x\uDC00"));
        assertFalse(ProtosLinuxConfinedFilesystemNames.isEntryName(".."));
        assertFalse(ProtosLinuxConfinedFilesystemNames.isEntryName("a/b"));
        assertTrue(ProtosLinuxConfinedFilesystemNames.isEntryName("ñandú"));
        assertEquals(".", ProtosLinuxConfinedFilesystemNames.relativePath(List.of()));
        assertEquals("a/b", ProtosLinuxConfinedFilesystemNames.relativePath(List.of("a", "b")));
    }

    // ----- Landlock -----

    @Test
    void landlockServesReferAndTruncate() throws Exception {
        assumeConfinementPrimitives();
        int abi = ProtosLinuxConfinedFilesystemNative.landlockAbiVersion();
        long handled = ProtosLinuxConfinedFilesystemLandlock.handledFilesystemAccess(abi);
        assertTrue((handled & ProtosLinuxConfinedFilesystemLandlock.LANDLOCK_ACCESS_FS_REFER) != 0);
        assertTrue((handled & ProtosLinuxConfinedFilesystemLandlock.LANDLOCK_ACCESS_FS_TRUNCATE) != 0);
        assertEquals(
                ProtosLinuxConfinedFilesystemLandlock.GRANT_ACCESS,
                handled & ProtosLinuxConfinedFilesystemLandlock.GRANT_ACCESS,
                "every granted right is handled");
        assertEquals(
                0,
                ProtosLinuxConfinedFilesystemLandlock.GRANT_ACCESS
                        & ProtosLinuxConfinedFilesystemLandlock.LANDLOCK_ACCESS_FS_EXECUTE,
                "the grant never allows execution");
    }

    /** The restriction is installed on the worker only; the test thread stays unrestricted. */
    @Test
    void landlockIsInstalledOnTheWorkerThreadOnly() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("root"));
        Path outside = Files.createDirectories(temporary.resolve("outside"));
        try (var backend = ProtosLinuxConfinedFilesystemTestSupport.provision(root)) {
            assertTrue(backend.worker().isRestrictedForTesting());
            assertEquals(
                    ProtosLinuxConfinedFilesystemNative.EACCES,
                    errnoOnWorker(backend.worker(), outside));
            int here = ProtosLinuxConfinedFilesystemNative.openRootDirectory(outside.toString());
            ProtosLinuxConfinedFilesystemNative.close(here);
        }
    }

    /** T14: two grants have distinct restricted workers; neither reaches the other's root. */
    @Test
    void workersOfDifferentGrantsAreIndependent() throws Exception {
        Path rootA = Files.createDirectories(temporary.resolve("a"));
        Path rootB = Files.createDirectories(temporary.resolve("b"));
        try (var a = ProtosLinuxConfinedFilesystemTestSupport.provision(rootA);
                var b = ProtosLinuxConfinedFilesystemTestSupport.provision(rootB)) {
            assertNotSame(a.worker().threadForTesting(), b.worker().threadForTesting());
            assertNotEquals(a.rootDescriptorForTesting(), b.rootDescriptorForTesting());
            assertEquals(ProtosLinuxConfinedFilesystemNative.EACCES, errnoOnWorker(a.worker(), rootB));
            assertEquals(ProtosLinuxConfinedFilesystemNative.EACCES, errnoOnWorker(b.worker(), rootA));
            a.close();
            // Revoking one grant leaves the other fully operational.
            var opened =
                    ProtosLinuxConfinedFilesystemTestSupport.open(
                            b,
                            ProtosLinuxConfinedFilesystemTestSupport.options(false, true, "createNew", false),
                            "still-works");
            assertTrue(opened.succeeded());
            ProtosLinuxConfinedFilesystemTestSupport.close(opened);
        }
    }

    @Test
    void workerWithoutInstalledPolicyRejectsWork() {
        assertThrows(
                IOException.class,
                () -> ProtosLinuxConfinedFilesystemWorker.start(
                        "i156-no-policy",
                        () -> {
                            throw new IOException("policy not installed");
                        }));
    }

    @Test
    void shutdownRejectsQueuedWorkAndEndsTheThread() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("root"));
        var backend = ProtosLinuxConfinedFilesystemTestSupport.provision(root);
        var worker = backend.worker();
        backend.close();
        assertFalse(worker.isAcceptingForTesting());
        assertFalse(worker.threadForTesting().isAlive());
        AtomicReference<String> outcome = new AtomicReference<>();
        assertFalse(
                worker.submit(
                        new ProtosLinuxConfinedFilesystemWorker.Task() {
                            @Override
                            public void run() {
                                outcome.set("ran");
                            }

                            @Override
                            public void rejected() {
                                outcome.set("rejected");
                            }

                            @Override
                            public void failedUnexpectedly(Throwable cause) {
                                outcome.set("failed");
                            }
                        }));
        assertEquals(null, outcome.get(), "a refused submission runs nothing");
    }

    @Test
    void anUnexpectedTaskFailureReceivesADispositionAndTheWorkerKeepsServing() throws Exception {
        assumeConfinementPrimitives();
        Path root = Files.createDirectories(temporary.resolve("root"));
        try (var backend = ProtosLinuxConfinedFilesystemTestSupport.provision(root)) {
            var worker = backend.worker();
            IllegalStateException thrown = new IllegalStateException("i156 unexpected task failure");
            AtomicReference<Throwable> disposition = new AtomicReference<>();
            AtomicReference<String> rejected = new AtomicReference<>();
            CountDownLatch failed = new CountDownLatch(1);
            assertTrue(
                    worker.submit(
                            new ProtosLinuxConfinedFilesystemWorker.Task() {
                                @Override
                                public void run() {
                                    throw thrown;
                                }

                                @Override
                                public void rejected() {
                                    rejected.set("rejected");
                                }

                                @Override
                                public void failedUnexpectedly(Throwable cause) {
                                    disposition.set(cause);
                                    failed.countDown();
                                }
                            }));
            await(failed);
            assertSame(thrown, disposition.get(), "the disposition carries the original cause");
            assertEquals(null, rejected.get(), "a started task is never reported as rejected");

            // The same grant still serves a later operation after the failure.
            var opened =
                    ProtosLinuxConfinedFilesystemTestSupport.open(
                            backend,
                            ProtosLinuxConfinedFilesystemTestSupport.options(true, true, "create", false),
                            "after-failure");
            assertTrue(opened.succeeded());
            ProtosLinuxConfinedFilesystemTestSupport.close(opened);
            assertTrue(worker.isAcceptingForTesting());
        }
    }

    @Test
    void arenasAndDescriptorsAreReleased() throws Exception {
        assumeConfinementPrimitives();
        Path root = Files.createDirectories(temporary.resolve("root"));
        assertTrue(
                openDescriptorsUnder(root).isEmpty(),
                "the private test root must start without open descriptors");
        for (int round = 0; round < 64; round++) {
            int fd = ProtosLinuxConfinedFilesystemNative.openRootDirectory(root.toString());
            ProtosLinuxConfinedFilesystemNative.close(fd);
        }
        assertTrue(
                openDescriptorsUnder(root).isEmpty(),
                "native open/close must not retain descriptors under the test root");
        try (var backend = ProtosLinuxConfinedFilesystemTestSupport.provision(root)) {
            var opened =
                    ProtosLinuxConfinedFilesystemTestSupport.open(
                            backend,
                            ProtosLinuxConfinedFilesystemTestSupport.options(true, true, "create", false),
                            "f");
            ProtosLinuxConfinedFilesystemTestSupport.close(opened);
        }
        List<String> leaked = openDescriptorsUnder(root);
        assertTrue(
                leaked.isEmpty(),
                "no confined Filesystem descriptor outlives its owner: " + leaked);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(16);
            assertTrue(segment.scope().isAlive());
        }
    }

    // ----- no JNI / C path -----

    @Test
    void i156PathHasNoJniOrPrivateNativeLibrary() throws Exception {
        assertFalse(Files.exists(Path.of("src/main/native")), "no C sources for I156");
        for (String source :
                List.of(
                        "ProtosLinuxConfinedFilesystemNative",
                        "ProtosLinuxConfinedFilesystemLandlock",
                        "ProtosLinuxConfinedFilesystemNames",
                        "ProtosLinuxConfinedFilesystemWorker",
                        "ProtosLinuxConfinedReadWriteTreeFilesystemBackend",
                        "ProtosLinuxConfinedReadWriteTreeFileResource")) {
            String text =
                    Files.readString(
                            Path.of("src/main/java/com/guillermomolina/protos/execution/host/io/"
                                    + source + ".java"),
                            StandardCharsets.UTF_8);
            assertFalse(text.contains("System.loadLibrary"), source);
            assertFalse(text.contains("System.load("), source);
            assertFalse(
                    java.util.regex.Pattern.compile(
                                    "(?m)^\\s*(?:(?:public|private|protected|static|final)\\s+)*"
                                            + "native\\s+[\\w<>\\[\\]]+\\s+\\w+\\s*\\(")
                            .matcher(text)
                            .find(),
                    source + " declares a JNI method");
        }
        try (Stream<Path> resources = Files.walk(Path.of("src/main/resources"))) {
            List<Path> libraries = new ArrayList<>();
            resources.filter(p -> p.toString().endsWith(".so")).forEach(libraries::add);
            assertTrue(libraries.isEmpty(), "no private native library: " + libraries);
        }
    }

    // ----- helpers -----

    private static long offset(java.lang.foreign.StructLayout layout, String name) {
        return layout.byteOffset(MemoryLayout.PathElement.groupElement(name));
    }

    private static int openBeneath(int fd, String path) throws IOException {
        int opened =
                ProtosLinuxConfinedFilesystemNative.openBeneath(
                        fd, path, ProtosLinuxConfinedFilesystemNative.O_RDONLY, 0);
        ProtosLinuxConfinedFilesystemNative.closeQuietly(opened);
        return opened;
    }

    @FunctionalInterface
    private interface NativeCall {
        int call() throws IOException;
    }

    private static void assertErrno(int expected, NativeCall call) {
        var failure =
                assertThrows(ProtosLinuxConfinedFilesystemNative.NativeException.class, call::call);
        assertEquals(expected, failure.errno(), failure.getMessage());
    }

    /** Runs one absolute-path directory open on {@code worker}; returns its errno or 0. */
    private static int errnoOnWorker(ProtosLinuxConfinedFilesystemWorker worker, Path directory) {
        int[] errno = {-1};
        CountDownLatch done = new CountDownLatch(1);
        assertTrue(
                worker.submit(
                        new ProtosLinuxConfinedFilesystemWorker.Task() {
                            @Override
                            public void run() {
                                try {
                                    int fd =
                                            ProtosLinuxConfinedFilesystemNative.openAbsoluteDirectory(
                                                    directory.toString());
                                    ProtosLinuxConfinedFilesystemNative.closeQuietly(fd);
                                    errno[0] = 0;
                                } catch (ProtosLinuxConfinedFilesystemNative.NativeException failure) {
                                    errno[0] = failure.errno();
                                } finally {
                                    done.countDown();
                                }
                            }

                            @Override
                            public void rejected() {
                                done.countDown();
                            }

                            @Override
                            public void failedUnexpectedly(Throwable cause) {
                                done.countDown();
                            }
                        }));
        await(done);
        return errno[0];
    }

    /**
     * Inspect only descriptors referencing this test's private filesystem tree.
     * Other parallel tests may legitimately change the process-wide FD count.
     */
    private static List<String> openDescriptorsUnder(Path root) throws IOException {
        String ownedRoot = root.toRealPath().toString();
        List<String> owned = new ArrayList<>();
        try (Stream<Path> descriptors = Files.list(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors.toList()) {
                Path target;
                try {
                    target = Files.readSymbolicLink(descriptor);
                } catch (java.nio.file.NoSuchFileException disappeared) {
                    // Another thread closed an unrelated descriptor during enumeration.
                    continue;
                }
                String location = target.toString();
                if (location.equals(ownedRoot) || location.startsWith(ownedRoot + "/")) {
                    owned.add(descriptor.getFileName() + " -> " + location);
                }
            }
        }
        return owned;
    }
}

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

import static com.guillermomolina.protos.execution.ProtosLinuxConfinedFilesystemTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.io.ProtosFilesystemTreeObservationFlow;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * I156 / PLAT072-E security matrix of the kernel-confined Filesystem grant (T01–T05, T11, T13).
 *
 * <p>T04/T05 are the security blockers: a private synchronization point in the backend stops the
 * worker after the parent descriptor was acquired and before the effect system call; the test
 * thread, an independent host thread, then moves the parent outside the root. Landlock must refuse
 * the effect. A failure of these tests is a security stop condition, not a flaky test.
 */
final class ProtosLinuxConfinedFilesystemSecurityTest {
    @TempDir Path temporary;

    // ----- T01: root acquisition under ancestor substitution -----

    @Test
    void ancestorSubstitutedDuringAcquisitionKeepsThePinnedRoot() throws Exception {
        assumeConfinementPrimitives();
        Path base = temporary.toRealPath();
        Path rootA = Files.createDirectories(base.resolve("A/root"));
        Path rootB = Files.createDirectories(base.resolve("B/root"));
        Path link = base.resolve("L");
        createSymlinkOrSkip(link, base.resolve("A"));

        try (var backend =
                ProtosLinuxConfinedReadWriteTreeFilesystemBackend.provision(
                        link.resolve("root"),
                        () -> retarget(link, base.resolve("B")))) {
            Opened created = open(backend, options(false, true, "createNew", false), "marker");
            assertTrue(created.succeeded());
            close(created);
            // Restoring the ancestor changes nothing either: the descriptor is the authority.
            retarget(link, base.resolve("A"));
            Opened second = open(backend, options(false, true, "createNew", false), "second");
            assertTrue(second.succeeded());
            close(second);
        }
        assertTrue(Files.exists(rootA.resolve("marker")), "the pinned directory was used");
        assertTrue(Files.exists(rootA.resolve("second")));
        try (var listing = Files.list(rootB)) {
            assertEquals(0, listing.count(), "the substituted directory received nothing");
        }
    }

    // ----- T02: mounts -----

    @Test
    void existingNestedMountIsNeverTraversed() throws Exception {
        assumeConfinementPrimitives();
        Optional<Path> mount = nestedMount();
        assumeTrue(mount.isPresent(), "ADMISSION: host exposes no readable nested mount fixture");
        Path mountPoint = mount.orElseThrow();
        try (var backend =
                ProtosLinuxConfinedReadWriteTreeFilesystemBackend.provision(mountPoint.getParent())) {
            String name = mountPoint.getFileName().toString();
            assertTrue(entriesFailed(backend, name), "RESOLVE_NO_XDEV refuses the mount point");
            Optional<Path> child;
            try (var children = Files.list(mountPoint)) {
                child = children.findFirst();
            } catch (IOException unreadable) {
                child = Optional.empty();
            }
            if (child.isPresent()) {
                String childName = child.orElseThrow().getFileName().toString();
                assertTrue(open(backend, options(true, false, null, false), name, childName).failed);
            }
        }
    }

    /**
     * T02a: a bind mount of a source on the same FileStore as the root. Creating it needs
     * privileges; on a host that refuses, the case is an explicit ADMISSION (skipped), never a pass.
     */
    @Test
    void sameDeviceBindMountIsNeverTraversed() throws Exception {
        assumeConfinementPrimitives();
        Path root = temporary.resolve("root");
        Files.createDirectories(root.resolve("bind"));
        Path source = Files.createDirectories(temporary.resolve("source"));
        Files.writeString(source.resolve("secret"), "secret");
        assertEquals(
                Files.getFileStore(root),
                Files.getFileStore(source),
                "T02a requires a source on the root's own FileStore");
        assumeTrue(
                run("mount", "--bind", source.toString(), root.resolve("bind").toString()),
                "ADMISSION T02a: host does not permit a same-device bind mount; not executed");
        assertBindMountRefused(root, source);
    }

    /**
     * T02b: a bind mount of a source on a different FileStore than the root. The FileStore identity
     * is checked before mounting, so this case can never silently repeat T02a. A host without a
     * second writable FileStore, or that refuses the mount, yields a distinct ADMISSION (skipped).
     */
    @Test
    void otherDeviceBindMountIsNeverTraversed() throws Exception {
        assumeConfinementPrimitives();
        Path root = temporary.resolve("root");
        Files.createDirectories(root.resolve("bind"));
        Optional<Path> otherStore = otherWritableFileStoreDirectory(root);
        assumeTrue(
                otherStore.isPresent(),
                "ADMISSION T02b: host offers no writable directory on another FileStore; not executed");
        Path source = Files.createTempDirectory(otherStore.orElseThrow(), "i156-t02b-");
        try {
            Files.writeString(source.resolve("secret"), "secret");
            assertNotEquals(
                    Files.getFileStore(root),
                    Files.getFileStore(source),
                    "T02b requires a source on another FileStore");
            assumeTrue(
                    run("mount", "--bind", source.toString(), root.resolve("bind").toString()),
                    "ADMISSION T02b: host does not permit an other-device bind mount; not executed");
            assertBindMountRefused(root, source);
        } finally {
            Files.deleteIfExists(source.resolve("secret"));
            Files.deleteIfExists(source.resolve("new"));
            Files.deleteIfExists(source);
        }
    }

    /** Runs the refusal checks through a mounted {@code root/bind} and always unmounts it. */
    private void assertBindMountRefused(Path root, Path source) throws Exception {
        try (var backend = ProtosLinuxConfinedReadWriteTreeFilesystemBackend.provision(root)) {
            assertTrue(open(backend, options(true, false, null, false), "bind", "secret").failed);
            assertTrue(open(backend, options(false, true, "createNew", false), "bind", "new").failed);
            assertTrue(remove(backend, path("bind", "secret")).failed);
            assertTrue(entriesFailed(backend, "bind"));
        } finally {
            run("umount", root.resolve("bind").toString());
        }
        assertEquals("secret", Files.readString(source.resolve("secret")));
        assertTrue(Files.notExists(source.resolve("new")));
    }

    private static Optional<Path> otherWritableFileStoreDirectory(Path root) throws IOException {
        var rootStore = Files.getFileStore(root);
        for (String candidate :
                List.of("/dev/shm", System.getProperty("java.io.tmpdir"), "/run/user", "/var/tmp")) {
            if (candidate == null) {
                continue;
            }
            Path directory = Path.of(candidate);
            try {
                if (Files.isDirectory(directory)
                        && Files.isWritable(directory)
                        && !Files.getFileStore(directory).equals(rootStore)) {
                    return Optional.of(directory);
                }
            } catch (IOException unavailable) {
                // Not a usable candidate.
            }
        }
        return Optional.empty();
    }

    // ----- T03: symbolic and magic links -----

    @Test
    void intermediateFinalAndMagicLinkTargetsAreNeverFollowed() throws Exception {
        Path root = temporary.resolve("root");
        Files.createDirectories(root.resolve("d"));
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path secret = Files.writeString(outside.resolve("secret"), "secret");
        createSymlinkOrSkip(root.resolve("d/escape"), outside);
        createSymlinkOrSkip(root.resolve("d/final"), secret);
        createSymlinkOrSkip(root.resolve("d/dangling"), temporary.resolve("dangling-target"));
        createSymlinkOrSkip(root.resolve("d/magic"), Path.of("/proc/self/root"));
        try (var backend = provision(temporary.resolve("root"))) {
            assertTrue(open(backend, options(true, false, null, false), "d", "escape", "secret").failed);
            assertTrue(open(backend, options(false, true, "create", false), "d", "escape", "new").failed);
            assertTrue(entriesFailed(backend, "d", "escape"));
            assertTrue(remove(backend, path("d", "escape", "secret")).failed);
            assertTrue(open(backend, options(true, false, null, false), "d", "final").failed);
            assertTrue(open(backend, options(true, true, null, true), "d", "final").failed);
            assertTrue(open(backend, options(false, true, "create", false), "d", "dangling").failed);
            assertTrue(open(backend, options(false, true, "createNew", false), "d", "dangling").failed);
            assertTrue(open(backend, options(true, false, null, false), "d", "magic", "etc", "hostname").failed);
            assertTrue(entriesFailed(backend, "d", "magic"));
        }
        assertEquals("secret", Files.readString(secret));
        assertTrue(Files.notExists(outside.resolve("new")));
        assertTrue(Files.notExists(temporary.resolve("dangling-target"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void directorySubstitutedBySymlinkIsRefusedAtNextAcquisition() throws Exception {
        Path root = temporary.resolve("root");
        Files.createDirectories(root.resolve("d/sub"));
        Files.writeString(root.resolve("d/sub/f"), "inside");
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Files.writeString(outside.resolve("f"), "outside");
        try (var backend = provision(temporary.resolve("root"))) {
            Opened held = open(backend, options(true, true, null, false), "d", "sub", "f");
            Files.move(root.resolve("d/sub"), root.resolve("d/moved"));
            createSymlinkOrSkip(root.resolve("d/sub"), outside);
            assertTrue(open(backend, options(true, true, null, true), "d", "sub", "f").failed);
            assertTrue(open(backend, options(false, true, "create", false), "d", "sub", "g").failed);
            assertTrue(remove(backend, path("d", "sub", "f")).failed);
            assertTrue(replace(backend, path("d", "moved", "f"), path("d", "sub", "f")).failed);
            // The File acquired before the substitution keeps its resource (T12).
            assertTrue(write(held, 0, "INSIDE").succeeded);
            close(held);
        }
        assertEquals("outside", Files.readString(outside.resolve("f")));
        assertTrue(Files.notExists(outside.resolve("g")));
        assertEquals("INSIDE", Files.readString(root.resolve("d/moved/f")));
    }

    // ----- T04: unlinkat after the parent moved outside the root (security blocker) -----

    @Test
    void removeAfterParentMovedOutsideTheRootChangesNothingOutside() throws Exception {
        Path root = temporary.resolve("root");
        Files.createDirectories(root.resolve("d"));
        Files.writeString(root.resolve("d/f"), "kept");
        Path moved = temporary.resolve("moved-out");
        try (var backend = provision(temporary.resolve("root"))) {
            Mutation mutation =
                    pausedAtEffect(
                            backend,
                            ProtosLinuxConfinedReadWriteTreeFilesystemBackend.NamespaceOperation.REMOVE,
                            () -> move(root.resolve("d"), moved),
                            completion -> backend.remove(path("d", "f"), completion));
            assertTrue(mutation.failed, "Landlock must refuse unlinkat outside the root");
            assertFalse(mutation.effectSucceeded);
        }
        assertEquals("kept", Files.readString(moved.resolve("f")), "nothing outside was removed");
    }

    /** Control for T04: the same paused path, without the move, does remove. */
    @Test
    void removePausedAtEffectWithoutSubstitutionSucceeds() throws Exception {
        Path root = temporary.resolve("root");
        Files.createDirectories(root.resolve("d"));
        Files.writeString(root.resolve("d/f"), "x");
        try (var backend = provision(temporary.resolve("root"))) {
            Mutation mutation =
                    pausedAtEffect(
                            backend,
                            ProtosLinuxConfinedReadWriteTreeFilesystemBackend.NamespaceOperation.REMOVE,
                            () -> {},
                            completion -> backend.remove(path("d", "f"), completion));
            assertTrue(mutation.effectSucceeded);
        }
        assertTrue(Files.notExists(root.resolve("d/f")));
    }

    // ----- T05: renameat2 after a parent moved outside the root (security blocker) -----

    @Test
    void replaceAfterSourceParentMovedOutsideChangesNothingOutside() throws Exception {
        Path root = temporary.resolve("root");
        Files.createDirectories(root.resolve("a"));
        Files.createDirectories(root.resolve("b"));
        Files.writeString(root.resolve("a/src"), "source");
        Path moved = temporary.resolve("moved-a");
        try (var backend = provision(root)) {
            Mutation mutation =
                    pausedAtEffect(
                            backend,
                            ProtosLinuxConfinedReadWriteTreeFilesystemBackend.NamespaceOperation.REPLACE,
                            () -> move(root.resolve("a"), moved),
                            completion -> backend.replace(path("a", "src"), path("b", "dst"), completion));
            assertTrue(mutation.failed, "Landlock must refuse renameat2 from outside the root");
        }
        assertEquals("source", Files.readString(moved.resolve("src")));
        assertTrue(Files.notExists(root.resolve("b/dst"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void replaceAfterTargetParentMovedOutsideChangesNothingOutside() throws Exception {
        Path root = temporary.resolve("root");
        Files.createDirectories(root.resolve("a"));
        Files.createDirectories(root.resolve("b"));
        Files.writeString(root.resolve("a/src"), "source");
        Path moved = temporary.resolve("moved-b");
        try (var backend = provision(root)) {
            Mutation mutation =
                    pausedAtEffect(
                            backend,
                            ProtosLinuxConfinedReadWriteTreeFilesystemBackend.NamespaceOperation.REPLACE,
                            () -> move(root.resolve("b"), moved),
                            completion -> backend.replace(path("a", "src"), path("b", "dst"), completion));
            assertTrue(mutation.failed, "Landlock must refuse renameat2 into outside the root");
        }
        assertTrue(Files.notExists(moved.resolve("dst"), LinkOption.NOFOLLOW_LINKS));
        assertEquals("source", Files.readString(root.resolve("a/src")));
    }

    @Test
    void replaceAfterTargetEntrySwappedOutsideReplacesOnlyInsideNames() throws Exception {
        Path root = temporary.resolve("root");
        Files.createDirectories(root.resolve("a"));
        Files.writeString(root.resolve("a/src"), "source");
        Path outside = Files.writeString(temporary.resolve("outside-target"), "outside");
        try (var backend = provision(root)) {
            Mutation mutation =
                    pausedAtEffect(
                            backend,
                            ProtosLinuxConfinedReadWriteTreeFilesystemBackend.NamespaceOperation.REPLACE,
                            () -> createSymlinkOrSkip(root.resolve("a/dst"), outside),
                            completion -> backend.replace(path("a", "src"), path("a", "dst"), completion));
            // The link entry itself is replaced; its referent outside is never touched.
            assertTrue(mutation.effectSucceeded);
        }
        assertEquals("outside", Files.readString(outside));
        assertEquals("source", Files.readString(root.resolve("a/dst")));
    }

    // ----- T11: entries take name and kind from one record -----

    @Test
    void entriesRefuseUnreportedTypesInvalidNamesAndDuplicates() {
        assertThrows(
                IOException.class,
                () -> ProtosLinuxConfinedReadWriteTreeFilesystemBackend.observeForTesting(
                        List.of(record("x", ProtosLinuxConfinedFilesystemNative.DT_UNKNOWN))));
        assertThrows(
                IOException.class,
                () -> ProtosLinuxConfinedReadWriteTreeFilesystemBackend.observeForTesting(
                        List.of(record("a/b", ProtosLinuxConfinedFilesystemNative.DT_REG))));
        assertThrows(
                IOException.class,
                () -> ProtosLinuxConfinedReadWriteTreeFilesystemBackend.observeForTesting(
                        List.of(
                                record("x", ProtosLinuxConfinedFilesystemNative.DT_REG),
                                record("x", ProtosLinuxConfinedFilesystemNative.DT_DIR))));
    }

    @Test
    void entriesAcrossSubstitutionReportEachObservationConsistently() throws Exception {
        Path root = temporary.resolve("root");
        Files.createDirectories(root.resolve("e"));
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        try (var backend = provision(temporary.resolve("root"))) {
            assertEquals(
                    ProtosFilesystemTreeObservationFlow.EntryKind.DIRECTORY, entries(backend).get("e"));
            Files.delete(root.resolve("e"));
            createSymlinkOrSkip(root.resolve("e"), outside);
            Map<String, ProtosFilesystemTreeObservationFlow.EntryKind> after = entries(backend);
            assertEquals(ProtosFilesystemTreeObservationFlow.EntryKind.LINK, after.get("e"));
            assertTrue(entriesFailed(backend, "e"), "the substituted link is never followed");
        }
    }

    // ----- T13: fail-closed preparation -----

    @Test
    void nonDefaultFilesystemProviderIsRefused() throws Exception {
        Path archive = temporary.resolve("provider.zip");
        URI uri = URI.create("jar:" + archive.toUri());
        try (FileSystem zip = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
            Path directory = Files.createDirectories(zip.getPath("/data"));
            assertThrows(
                    IOException.class,
                    () -> ProtosLinuxConfinedReadWriteTreeFilesystemBackend.provision(directory));
        }
    }

    @Test
    void failedPreparationAdmitsNoWorkAndEndsTheWorker() throws Exception {
        IOException refused =
                assertThrows(
                        IOException.class,
                        () -> ProtosLinuxConfinedFilesystemWorker.start(
                                "i156-refused",
                                () -> {
                                    throw new ProtosLinuxConfinedFilesystemNative.NativeException(
                                            "landlock_restrict_self",
                                            ProtosLinuxConfinedFilesystemNative.EPERM);
                                }));
        assertTrue(refused.getMessage().contains("landlock_restrict_self"));
    }

    // ----- fixtures -----

    /**
     * Starts one namespace operation, waits until the worker reaches the effect barrier, runs
     * {@code substitution} on this (independent) thread, then releases the worker.
     */
    private static Mutation pausedAtEffect(
            ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend,
            ProtosLinuxConfinedReadWriteTreeFilesystemBackend.NamespaceOperation expected,
            Runnable substitution,
            java.util.function.Consumer<Mutation> start) {
        CountDownLatch reached = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        backend.setNamespaceEffectBarrierForTesting(
                operation -> {
                    assertEquals(expected, operation);
                    reached.countDown();
                    await(proceed);
                });
        Mutation mutation = new Mutation();
        start.accept(mutation);
        await(reached);
        try {
            substitution.run();
        } finally {
            proceed.countDown();
        }
        return mutation.awaited();
    }

    private static void move(Path from, Path to) {
        try {
            Files.move(from, to);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void retarget(Path link, Path target) {
        try {
            Files.delete(link);
            Files.createSymbolicLink(link, target);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static ProtosLinuxConfinedFilesystemNative.DirectoryEntry record(String name, int type) {
        return new ProtosLinuxConfinedFilesystemNative.DirectoryEntry(name, type);
    }

    private static boolean run(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException unavailable) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** A real mount point whose parent is a plain readable directory other than {@code /}. */
    private static Optional<Path> nestedMount() {
        Path mountInfo = Path.of("/proc/self/mountinfo");
        if (!Files.isReadable(mountInfo)) {
            return Optional.empty();
        }
        try {
            for (String line : Files.readAllLines(mountInfo)) {
                String[] fields = line.split(" ");
                if (fields.length < 5 || fields[4].contains("\\")) {
                    continue;
                }
                Path mount = Path.of(fields[4]);
                Path parent = mount.getParent();
                if (parent != null
                        && parent.getParent() != null
                        && Files.isDirectory(mount, LinkOption.NOFOLLOW_LINKS)
                        && Files.isReadable(mount)
                        && Files.isReadable(parent)
                        && !Files.isSymbolicLink(parent)
                        && parent.toRealPath().equals(parent)) {
                    return Optional.of(mount);
                }
            }
        } catch (IOException | RuntimeException unavailable) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}

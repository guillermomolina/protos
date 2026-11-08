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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.io.ProtosFilesystemTreeObservationFlow;
import com.guillermomolina.protos.runtime.io.ProtosPathComponents;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * I156 regressions of the Linux FFM confined Filesystem backend: open modes, File I/O, namespace
 * operations, entries and custody (T06–T10, T12, T15). Converted from the I155 NIO suite; its
 * semantic cases are preserved, its pathname/mount-table cases are replaced by the kernel
 * confinement cases of {@link ProtosLinuxConfinedFilesystemSecurityTest}.
 */
final class ProtosLinuxConfinedReadWriteTreeFilesystemBackendTest {
    @TempDir Path temporary;

    private Path rootWith(String relativeDirectories) throws IOException {
        Path root = temporary.resolve("root");
        Files.createDirectories(root.resolve(relativeDirectories));
        return root;
    }

    // ----- provisioning -----

    @Test
    void provisionsExistingRootAndClosesIdempotently() throws Exception {
        ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend = provision(rootWith("."));
        assertFalse(backend.isClosedForTesting());
        assertTrue(backend.worker().isRestrictedForTesting());
        backend.close();
        backend.close();
        assertTrue(backend.isClosedForTesting());
        assertFalse(backend.worker().threadForTesting().isAlive());
    }

    @Test
    void provisioningRefusesAbsentRootFileRootAndRelativeRoot() throws Exception {
        assumeConfinementPrimitives();
        assertThrows(
                IOException.class,
                () -> ProtosLinuxConfinedReadWriteTreeFilesystemBackend.provision(
                        temporary.resolve("missing")));
        Path file = Files.writeString(temporary.resolve("file"), "x");
        assertThrows(
                IOException.class,
                () -> ProtosLinuxConfinedReadWriteTreeFilesystemBackend.provision(file));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosLinuxConfinedReadWriteTreeFilesystemBackend.provision(Path.of("rel")));
        assertTrue(Files.notExists(temporary.resolve("missing")));
    }

    @Test
    void provisioningRefusesFinalRootSymbolicLink() throws Exception {
        assumeConfinementPrimitives();
        Path real = Files.createDirectory(temporary.resolve("real"));
        Path link = temporary.resolve("link");
        createSymlinkOrSkip(link, real);
        assertThrows(
                IOException.class,
                () -> ProtosLinuxConfinedReadWriteTreeFilesystemBackend.provision(link));
    }

    // ----- T08: every valid open mode -----

    @Test
    void readsExistingNestedFileWithoutCommitting() throws Exception {
        Path root = rootWith("a/b");
        Files.writeString(root.resolve("a/b/data.txt"), "nested");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(true, false, null, false), "a", "b", "data.txt");
            assertTrue(file.succeeded());
            assertTrue(file.capabilities.readable());
            assertFalse(file.capabilities.writable());
            assertFalse(file.capabilities.truncatable());
            assertFalse(file.committed, "an existing read-only open publishes no effect");
            assertSame(backend.worker().threadForTesting(), file.completionThread);
            assertEquals("nested", readText(file));
            close(file);
        }
    }

    @Test
    void writesExistingNestedFile() throws Exception {
        Path root = rootWith("a");
        Path target = Files.writeString(root.resolve("a/out.txt"), "xxxx");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(false, true, null, false), "a", "out.txt");
            assertTrue(file.succeeded());
            assertFalse(file.capabilities.readable());
            assertTrue(file.capabilities.writable());
            assertTrue(write(file, 1, "YY").succeeded);
            close(file);
        }
        assertEquals("xYYx", Files.readString(target));
    }

    /**
     * I156: a write task that throws after the kernel accepted its first chunk reports unknown
     * progress, never an invented prefix; the accepted bytes stay written (a committed effect is
     * never declared undone) and the same grant keeps serving the next write.
     */
    @Test
    void aWriteThatThrowsAfterContributingReportsUnknownProgressAndKeepsItsEffect()
            throws Exception {
        Path root = rootWith("a");
        Path target = Files.writeString(root.resolve("a/out.txt"), "xxxx");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(false, true, null, false), "a", "out.txt");
            assertTrue(file.succeeded());
            ProtosLinuxConfinedReadWriteTreeFileResource injectedResource =
                    (ProtosLinuxConfinedReadWriteTreeFileResource) file.resource;
            injectedResource.setFaultAfterContributionForTesting(
                    () -> {
                        throw new IllegalStateException("i156 injected fault after contribution");
                    });
            Written failed;
            try {
                failed = write(file, 0, "YY");
            } finally {
                injectedResource.setFaultAfterContributionForTesting(null);
            }
            assertTrue(failed.unknownProgress, "unknown progress, not a prefix");
            assertEquals(-1, failed.prefix, "no prefix was invented");
            assertFalse(failed.succeeded, "partial success is not reported as success");

            assertTrue(write(file, 2, "ZZ").succeeded, "the same worker keeps serving");
            close(file);
        }
        assertEquals("YYZZ", Files.readString(target));
    }

    @Test
    void readWriteSharesOneSelectedResource() throws Exception {
        Path root = rootWith("d");
        Files.writeString(root.resolve("d/rw.txt"), "abc");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(true, true, null, false), "d", "rw.txt");
            assertTrue(write(file, 3, "def").succeeded);
            assertEquals("abcdef", readText(file));
            close(file);
        }
    }

    @Test
    void readOnlyCreateCreatesEmptyFileWithoutAdvertisingWrite() throws Exception {
        Path root = rootWith("d");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(true, false, "create", false), "d", "new.txt");
            assertTrue(file.succeeded());
            assertTrue(file.committed);
            assertFalse(file.capabilities.writable());
            assertEquals(0, size(file));
            assertNull(read(file, 0, 16), "an empty created file reads as EOF");
            assertFalse(write(file, 0, "x").succeeded, "read-only File refuses writes");
            assertFalse(truncate(file, 0), "read-only File refuses truncation");
            Files.writeString(root.resolve("d/new.txt"), "host");
            assertEquals("host", readText(file));
            close(file);
        }
    }

    @Test
    void createOfExistingFileSelectsItWithoutCommitting() throws Exception {
        Path root = rootWith("d");
        Path existing = Files.writeString(root.resolve("d/keep.txt"), "keep");
        try (var backend = provision(root)) {
            Opened reopened = open(backend, options(true, true, "create", false), "d", "keep.txt");
            assertTrue(reopened.succeeded());
            assertFalse(reopened.committed);
            assertEquals(4, size(reopened));
            close(reopened);
        }
        assertEquals("keep", Files.readString(existing));
    }

    @Test
    void createNewCreatesExclusivelyAndRejectsExistingTargetUnchanged() throws Exception {
        Path root = rootWith("d");
        Path existing = Files.writeString(root.resolve("d/taken.txt"), "old");
        try (var backend = provision(root)) {
            Opened fresh = open(backend, options(false, true, "createNew", false), "d", "f.txt");
            assertTrue(fresh.succeeded());
            assertTrue(fresh.committed);
            assertEquals(0, size(fresh));
            close(fresh);
            Opened taken = open(backend, options(true, true, "createNew", false), "d", "taken.txt");
            assertTrue(taken.failed);
            assertFalse(taken.committed, "EEXIST is a failure without effect, not a commitment");
        }
        assertEquals("old", Files.readString(existing));
    }

    @Test
    void truncateOnOpenCommitsAndEmptiesSelectedFile() throws Exception {
        Path root = rootWith("d");
        Path target = Files.writeString(root.resolve("d/t.txt"), "content");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(true, true, null, true), "d", "t.txt");
            assertTrue(file.succeeded());
            assertTrue(file.committed);
            assertEquals(0, size(file));
            close(file);
        }
        assertEquals(0, Files.size(target));
    }

    @Test
    void truncateOfEmptyFileIsANoOpWithoutCommitment() throws Exception {
        Path root = rootWith("d");
        Files.writeString(root.resolve("d/empty"), "");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(false, true, null, true), "d", "empty");
            assertTrue(file.succeeded());
            assertFalse(file.committed);
            close(file);
        }
    }

    @Test
    void cancellationBeforeTruncateCommitPreservesContentAndReleasesDescriptor() throws Exception {
        Path root = rootWith("d");
        Path target = Files.writeString(root.resolve("d/t.txt"), "content");
        try (var backend = provision(root)) {
            Opened file = openDenyingCommit(backend, options(false, true, null, true), "d", "t.txt");
            assertTrue(file.cancelled);
            assertFalse(file.succeeded());
            assertFalse(file.failed, "cancellation owns the outcome");
            assertEquals(0, backend.openResourceCountForTesting());
        }
        assertEquals("content", Files.readString(target));
    }

    @Test
    void cancellationBeforeCreationCreatesNothing() throws Exception {
        Path root = rootWith("d");
        try (var backend = provision(root)) {
            Opened file = openDenyingCommit(backend, options(false, true, "createNew", false), "d", "n");
            assertTrue(file.cancelled);
            assertFalse(file.failed);
        }
        assertTrue(Files.notExists(root.resolve("d/n"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void truncateOfAbsentExistingTargetFailsWithoutCreating() throws Exception {
        Path root = rootWith("d");
        try (var backend = provision(root)) {
            assertTrue(open(backend, options(false, true, null, true), "d", "none").failed);
        }
        assertTrue(Files.notExists(root.resolve("d/none"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void openRefusesDirectoryAndFifoAsFileResources() throws Exception {
        Path root = rootWith("d/sub");
        Path fifo = root.resolve("d/fifo");
        boolean haveFifo = mkfifo(fifo);
        try (var backend = provision(root)) {
            assertTrue(open(backend, options(true, false, null, false), "d", "sub").failed);
            if (haveFifo) {
                // O_NONBLOCK keeps the worker from blocking; the non-regular result is refused.
                assertTrue(open(backend, options(true, false, null, false), "d", "fifo").failed);
            }
            assertEquals(0, backend.openResourceCountForTesting());
        }
    }

    // ----- T06 / T07: concurrent creation -----

    @Test
    void concurrentCreateNewHasExactlyOneWinner() throws Exception {
        Path root = rootWith("d");
        int contenders = 8;
        try (var backend = provision(root)) {
            List<Opened> results =
                    race(contenders, () -> open(backend, options(false, true, "createNew", false), "d", "race"));
            assertEquals(1, results.stream().filter(Opened::succeeded).count());
            assertEquals(contenders - 1, results.stream().filter(r -> r.failed).count());
            results.stream().filter(Opened::succeeded).forEach(r -> close(r));
        }
        assertTrue(Files.isRegularFile(root.resolve("d/race"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void concurrentOpenOrCreateAllSelectTheSameFile() throws Exception {
        Path root = rootWith("d");
        int contenders = 8;
        try (var backend = provision(root)) {
            List<Opened> results =
                    race(contenders, () -> open(backend, options(true, true, "create", false), "d", "shared"));
            assertTrue(results.stream().allMatch(Opened::succeeded));
            assertEquals(1, results.stream().filter(r -> r.committed).count(), "one creator");
            assertTrue(write(results.get(0), 0, "one").succeeded);
            for (Opened result : results) {
                assertEquals(3, size(result), "every File is bound to the one created resource");
                close(result);
            }
        }
    }

    /** T07: create keeps selecting correctly while another host thread creates and removes. */
    @Test
    void createRacingExternalCreateAndRemoveNeverFailsSpuriously() throws Exception {
        Path root = rootWith("d");
        Path entry = root.resolve("d/flap");
        try (var backend = provision(root)) {
            for (int round = 0; round < 32; round++) {
                CountDownLatch start = new CountDownLatch(1);
                Thread flapper =
                        Thread.ofPlatform()
                                .start(
                                        () -> {
                                            await(start);
                                            try {
                                                Files.writeString(entry, "external");
                                                Files.deleteIfExists(entry);
                                            } catch (IOException ignored) {
                                                // Either state is a valid selection point.
                                            }
                                        });
                start.countDown();
                Opened opened = open(backend, options(true, true, "create", false), "d", "flap");
                flapper.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                assertTrue(opened.succeeded(), "open-or-create must not leak a helper race");
                close(opened);
                Files.deleteIfExists(entry);
            }
        }
    }

    // ----- File semantics (T08, T12) -----

    @Test
    void fileSupportsSeekableSizedTruncatableSyncableSemantics() throws Exception {
        Path root = rootWith("d");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(true, true, "create", false), "d", "f.bin");
            assertTrue(file.capabilities.seekable());
            assertTrue(file.capabilities.sized());
            assertTrue(file.capabilities.truncatable());
            assertTrue(file.capabilities.syncable());
            assertTrue(write(file, 0, "hello world").succeeded);
            assertEquals(11, size(file));
            assertEquals(11, endPosition(file));
            assertEquals("world", new String(read(file, 6, 64), StandardCharsets.UTF_8));
            assertNull(read(file, 11, 4), "EOF is distinct from data");
            assertTrue(truncate(file, 20), "truncation never extends");
            assertEquals(11, size(file));
            assertTrue(truncate(file, 5));
            assertEquals("hello", readText(file));
            assertTrue(sync(file));
            close(file);
        }
    }

    @Test
    void writeBeyondEndLeavesZeroFilledHoleAndEmptyWriteHasNoEffect() throws Exception {
        Path root = rootWith("d");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(true, true, "create", false), "d", "hole.bin");
            assertTrue(write(file, 100, "").succeeded);
            assertEquals(0, size(file), "an empty write neither modifies nor extends");
            assertTrue(write(file, 4, "z").succeeded);
            assertArrayEquals(new byte[] {0, 0, 0, 0, 'z'}, readAll(file));
            close(file);
        }
    }

    @Test
    void writeAtUnrepresentableEndFailsWithZeroPrefix() throws Exception {
        Path root = rootWith("d");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(true, true, "create", false), "d", "big.bin");
            Written outcome = write(file, Long.MAX_VALUE - 1, "abc");
            assertFalse(outcome.succeeded);
            assertEquals(0, outcome.prefix);
            assertEquals(0, size(file));
            close(file);
        }
    }

    @Test
    void openFileStaysBoundAfterItsEntryIsReplacedOrRemoved() throws Exception {
        Path root = rootWith("d");
        Files.writeString(root.resolve("d/f"), "original");
        Files.writeString(root.resolve("d/other"), "other");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(true, false, null, false), "d", "f");
            assertTrue(replace(backend, path("d", "other"), path("d", "f")).succeededOutcome());
            assertEquals("original", readText(file));
            assertTrue(remove(backend, path("d", "f")).succeededOutcome());
            assertEquals("original", readText(file));
            close(file);
        }
    }

    @Test
    void writesThroughFileFollowTheResourceNotThePathname() throws Exception {
        Path root = rootWith("d");
        Files.writeString(root.resolve("d/f"), "abc");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(true, true, null, false), "d", "f");
            assertTrue(replace(backend, path("d", "f"), path("d", "moved")).succeededOutcome());
            Opened replacement = open(backend, options(false, true, "createNew", false), "d", "f");
            assertTrue(replacement.succeeded());
            close(replacement);
            assertTrue(write(file, 3, "def").succeeded);
            close(file);
        }
        assertEquals("abcdef", Files.readString(root.resolve("d/moved")));
        assertEquals("", Files.readString(root.resolve("d/f")));
    }

    @Test
    void parentRenameAfterAcquisitionDoesNotRetargetTheFile() throws Exception {
        Path root = rootWith("p/q");
        Files.writeString(root.resolve("p/q/f"), "bound");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(true, true, null, false), "p", "q", "f");
            assertTrue(replace(backend, path("p"), path("renamed")).succeededOutcome());
            assertTrue(write(file, 5, "!").succeeded);
            close(file);
            assertTrue(open(backend, options(true, false, null, false), "p", "q", "f").failed);
        }
        assertEquals("bound!", Files.readString(root.resolve("renamed/q/f")));
    }

    // ----- entries -----

    @Test
    void entriesObservesRootAndNestedDirectoriesWithExactKinds() throws Exception {
        Path root = rootWith("a/b");
        Files.writeString(root.resolve("file.txt"), "x");
        Files.writeString(root.resolve("a/b/inner.txt"), "y");
        Path outside = Files.writeString(temporary.resolve("outside"), "o");
        boolean linked = tryCreateSymlink(root.resolve("link"), outside);
        boolean haveFifo = mkfifo(root.resolve("fifo"));
        try (var backend = provision(root)) {
            Map<String, ProtosFilesystemTreeObservationFlow.EntryKind> top = entries(backend);
            assertEquals(ProtosFilesystemTreeObservationFlow.EntryKind.REGULAR, top.get("file.txt"));
            assertEquals(ProtosFilesystemTreeObservationFlow.EntryKind.DIRECTORY, top.get("a"));
            if (linked) {
                assertEquals(ProtosFilesystemTreeObservationFlow.EntryKind.LINK, top.get("link"));
            }
            if (haveFifo) {
                assertEquals(ProtosFilesystemTreeObservationFlow.EntryKind.OTHER, top.get("fifo"));
            }
            assertFalse(top.containsKey("."));
            assertFalse(top.containsKey(".."));
            assertFalse(top.containsKey("inner.txt"), "listing is not recursive");
            assertEquals(
                    Map.of("inner.txt", ProtosFilesystemTreeObservationFlow.EntryKind.REGULAR),
                    entries(backend, "a", "b"));
        }
    }

    // ----- T09: replace -----

    @Test
    void replaceAcrossDescendantDirectoriesReplacesExistingTargetAtomically() throws Exception {
        Path root = rootWith("a/x");
        Files.createDirectories(root.resolve("b/y"));
        Files.writeString(root.resolve("a/x/src"), "new");
        Files.writeString(root.resolve("b/y/dst"), "old");
        try (var backend = provision(root)) {
            assertTrue(replace(backend, path("a", "x", "src"), path("b", "y", "dst")).succeededOutcome());
        }
        assertTrue(Files.notExists(root.resolve("a/x/src"), LinkOption.NOFOLLOW_LINKS));
        assertEquals("new", Files.readString(root.resolve("b/y/dst")));
    }

    @Test
    void replaceMovesDirectoriesAndLinksAsEntries() throws Exception {
        Path root = rootWith("dir/inner");
        Path outside = Files.writeString(temporary.resolve("referent"), "outside");
        createSymlinkOrSkip(root.resolve("link"), outside);
        try (var backend = provision(root)) {
            assertTrue(replace(backend, path("dir"), path("moved-dir")).succeededOutcome());
            assertTrue(replace(backend, path("link"), path("moved-link")).succeededOutcome());
        }
        assertTrue(Files.isDirectory(root.resolve("moved-dir/inner")));
        assertTrue(Files.isSymbolicLink(root.resolve("moved-link")));
        assertEquals(outside, Files.readSymbolicLink(root.resolve("moved-link")));
        assertEquals("outside", Files.readString(outside));
    }

    @Test
    void replaceOfAnEntryByItselfIsASuccessfulNoOp() throws Exception {
        Path root = rootWith("d");
        Files.writeString(root.resolve("d/same"), "kept");
        try (var backend = provision(root)) {
            assertTrue(replace(backend, path("d", "same"), path("d", "same")).succeededOutcome());
        }
        assertEquals("kept", Files.readString(root.resolve("d/same")));
    }

    @Test
    void replaceRefusesTheRootAndAbsentSourcesWithoutEffect() throws Exception {
        Path root = rootWith("d");
        Files.writeString(root.resolve("d/kept"), "k");
        try (var backend = provision(root)) {
            assertTrue(replace(backend, path(), path("d", "x")).failed);
            assertTrue(replace(backend, path("d"), path()).failed);
            assertTrue(replace(backend, path("d", "absent"), path("d", "kept")).failed);
        }
        assertEquals("k", Files.readString(root.resolve("d/kept")));
    }

    // ----- T10: remove -----

    @Test
    void removeDeletesFileLinkAndEmptyDirectoryButNotNonemptyDirectoryOrRoot() throws Exception {
        Path root = rootWith("empty");
        Files.createDirectories(root.resolve("full"));
        Files.writeString(root.resolve("full/kept"), "k");
        Files.writeString(root.resolve("file"), "f");
        Path outside = Files.writeString(temporary.resolve("referent"), "outside");
        boolean linked = tryCreateSymlink(root.resolve("link"), outside);
        try (var backend = provision(root)) {
            assertTrue(remove(backend, path("file")).succeededOutcome());
            if (linked) {
                assertTrue(remove(backend, path("link")).succeededOutcome());
            }
            assertTrue(remove(backend, path("empty")).succeededOutcome());
            assertTrue(remove(backend, path("full")).failed);
            assertTrue(remove(backend, path()).failed);
            assertTrue(remove(backend, path("absent")).failed);
        }
        assertTrue(Files.notExists(root.resolve("file"), LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.notExists(root.resolve("link"), LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.notExists(root.resolve("empty"), LinkOption.NOFOLLOW_LINKS));
        assertEquals("k", Files.readString(root.resolve("full/kept")));
        assertEquals("outside", Files.readString(outside));
    }

    // ----- invalid components -----

    @Test
    void invalidComponentsAreRejectedBeforeAnyNativeWork() throws Exception {
        Path root = rootWith("d");
        Files.writeString(temporary.resolve("sibling"), "sibling");
        try (var backend = provision(root)) {
            for (List<String> invalid :
                    List.of(
                            List.of(".."),
                            List.of("d", "..", "..", "sibling"),
                            List.of("."),
                            List.of(""),
                            List.of("d/x"),
                            List.of("/etc"),
                            List.of("a\0b"),
                            List.of("bad\uD800surrogate"),
                            List.of("x".repeat(256)))) {
                assertTrue(open(backend, options(true, false, null, false), invalid).failed, invalid.toString());
                assertTrue(remove(backend, new ProtosPathComponents(invalid)).failed);
            }
            assertTrue(entriesFailed(backend, ".."));
            assertTrue(open(backend, options(true, false, null, false), List.of()).failed, "root is no file");
        }
        assertEquals("sibling", Files.readString(temporary.resolve("sibling")));
    }

    @Test
    void utf8NamesRoundTripExactly() throws Exception {
        Path root = rootWith("d");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(false, true, "createNew", false), "d", "naïve-✓");
            assertTrue(file.succeeded());
            close(file);
            assertTrue(entries(backend, "d").containsKey("naïve-✓"));
        }
        assertTrue(Files.exists(root.resolve("d/naïve-✓")));
    }

    // ----- T15: custody, revocation, idempotent cleanup -----

    @Test
    void closeRevokesEveryOpenResourceAndCleanupIsIdempotent() throws Exception {
        Path root = rootWith("d");
        Files.writeString(root.resolve("d/f"), "x");
        ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend = provision(root);
        Opened first = open(backend, options(true, false, null, false), "d", "f");
        Opened second = open(backend, options(true, true, null, false), "d", "f");
        assertNotSame(first.resource, second.resource);
        assertEquals(2, backend.openResourceCountForTesting());
        backend.close();
        backend.close();
        assertEquals(0, backend.openResourceCountForTesting());
        var revoked = (ProtosLinuxConfinedReadWriteTreeFileResource) second.resource;
        assertTrue(revoked.isClosedForTesting());

        assertTrue(readFails(first));
        Written empty = write(second, 0, "");
        assertFalse(empty.succeeded, "an empty write after revocation must fail");
        assertEquals(0, empty.prefix);
        assertFalse(write(second, 0, "y").succeeded);
        assertEquals(-1, size(second));
        assertEquals(-1, endPosition(second));
        assertFalse(truncate(second, 0));
        assertFalse(sync(second));

        revoked.release();
        revoked.release();
        assertTrue(closeSucceeds(second));
        assertTrue(closeSucceeds(first));
        assertTrue(open(backend, options(true, false, null, false), "d", "f").failed);
        assertEquals("x", Files.readString(root.resolve("d/f")));
    }

    @Test
    void releaseBeforeOrdinaryCloseIsIdempotentAndUnregisters() throws Exception {
        Path root = rootWith("d");
        Files.writeString(root.resolve("d/f"), "x");
        try (var backend = provision(root)) {
            Opened file = open(backend, options(true, true, null, false), "d", "f");
            var resource = (ProtosLinuxConfinedReadWriteTreeFileResource) file.resource;
            resource.release();
            assertTrue(closeSucceeds(file), "close after release succeeds idempotently");
            assertEquals(0, backend.openResourceCountForTesting());
            assertFalse(write(file, 0, "").succeeded);
            resource.release();
        }
        assertEquals("x", Files.readString(root.resolve("d/f")));
    }

    @Test
    void operationsAfterCloseFailWithoutEffects() throws Exception {
        Path root = rootWith("d");
        Files.writeString(root.resolve("d/f"), "x");
        ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend = provision(root);
        backend.close();
        assertTrue(open(backend, options(false, true, "create", false), "d", "n").failed);
        assertTrue(entriesFailed(backend));
        assertTrue(remove(backend, path("d", "f")).failed);
        assertTrue(replace(backend, path("d", "f"), path("d", "g")).failed);
        assertTrue(Files.notExists(root.resolve("d/n")));
        assertEquals("x", Files.readString(root.resolve("d/f")));
    }

    /** A namespace operation blocked before its effect is refused, not published, by revocation. */
    @Test
    void mutationRacingCloseIsRefusedAndCloseWaitsForIt() throws Exception {
        Path root = rootWith("d");
        Path entry = Files.writeString(root.resolve("d/f"), "kept");
        ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend = provision(root);
        CountDownLatch reached = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        backend.setNamespaceEffectBarrierForTesting(
                operation -> {
                    reached.countDown();
                    await(proceed);
                });
        Mutation mutation = new Mutation();
        backend.remove(path("d", "f"), mutation);
        await(reached);
        Thread closer = Thread.ofPlatform().start(backend::close);
        while (!backend.isClosedForTesting()) {
            Thread.onSpinWait();
        }
        assertTrue(closer.isAlive(), "close waits for the in-flight operation");
        proceed.countDown();
        closer.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertFalse(closer.isAlive());
        mutation.awaited();
        assertTrue(mutation.failed);
        assertFalse(mutation.succeededOutcome());
        assertEquals("kept", Files.readString(entry));
    }

    /**
     * I156: an unexpected worker failure racing Process grant revocation must
     * never leave an operation pending or leak its resource custody.
     *
     * The barrier is reached before the namespace effect. The closer establishes
     * revocation first; the worker then throws AssertionError, exercising its
     * failedUnexpectedly disposition rather than an ordinary IOException.
     */
    @Test
    void unexpectedWorkerFailureRacingGrantRevocationRetiresEverything() throws Exception {
        Path root = rootWith("d");
        Path entry = Files.writeString(root.resolve("d/f"), "kept");
        ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend = provision(root);
        Opened held = open(backend, options(true, true, null, false), "d", "f");
        assertTrue(held.succeeded());
        assertEquals(1, backend.openResourceCountForTesting());

        CountDownLatch effectReached = new CountDownLatch(1);
        CountDownLatch releaseEffect = new CountDownLatch(1);
        CountDownLatch revoked = new CountDownLatch(1);
        backend.setRevocationObserverForTesting(revoked::countDown);
        backend.setNamespaceEffectBarrierForTesting(
                operation -> {
                    effectReached.countDown();
                    await(releaseEffect);
                    throw new AssertionError("I156 injected unexpected worker failure");
                });

        Mutation active = new Mutation();
        backend.remove(path("d", "f"), active);
        await(effectReached);

        Thread closer = Thread.ofPlatform().start(backend::close);
        try {
            await(revoked);
            assertTrue(backend.isClosedForTesting());
            assertTrue(closer.isAlive(), "revocation waits for the active task");

            // This request arrived after the revocation cutover.
            Mutation late = new Mutation();
            backend.remove(path("d", "late"), late);
            late.awaited();
            assertTrue(late.failed);
            assertFalse(late.succeededOutcome());
            assertFalse(Files.exists(root.resolve("d/late")));
        } finally {
            releaseEffect.countDown();
            closer.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        }

        assertFalse(closer.isAlive(), "the worker must terminate");
        active.awaited();
        assertTrue(active.failed, "unexpected failure has a terminal disposition");
        assertFalse(active.succeededOutcome(), "no effect was committed");
        assertEquals("kept", Files.readString(entry));
        assertEquals(0, backend.openResourceCountForTesting());
        assertTrue(
                ((ProtosLinuxConfinedReadWriteTreeFileResource) held.resource)
                        .isClosedForTesting());
        assertFalse(backend.worker().isAcceptingForTesting());
        assertFalse(backend.worker().threadForTesting().isAlive());
        assertFalse(backend.worker().restrictedThreadForTesting().isAlive());

        // Neither an acquired File nor a late operation may regain authority.
        assertFalse(write(held, 0, "changed").succeeded);
        assertTrue(open(backend, options(true, false, null, false), "d", "f").failed);
        assertEquals("kept", Files.readString(entry));
    }

    /** Queued work is withdrawn by cancellation before it starts and then never runs. */
    @Test
    void cancellationWithdrawsQueuedWorkBeforeItStarts() throws Exception {
        Path root = rootWith("d");
        Files.writeString(root.resolve("d/f"), "kept");
        try (var backend = provision(root)) {
            CountDownLatch reached = new CountDownLatch(1);
            CountDownLatch proceed = new CountDownLatch(1);
            backend.setNamespaceEffectBarrierForTesting(
                    operation -> {
                        reached.countDown();
                        await(proceed);
                    });
            Mutation blocker = new Mutation();
            backend.replace(path("d", "f"), path("d", "f"), blocker);
            await(reached);
            Mutation queued = new Mutation();
            backend.remove(path("d", "f"), queued).cancel();
            proceed.countDown();
            blocker.awaited();
            backend.setNamespaceEffectBarrierForTesting(operation -> {});
            assertEquals("kept", Files.readString(root.resolve("d/f")));
            assertEquals(1, queued.done.getCount(), "withdrawn work never completes");
        }
    }

    // ----- fixtures -----

    private static boolean mkfifo(Path fifo) {
        try {
            Process process = new ProcessBuilder("mkfifo", fifo.toString()).start();
            return process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException unavailable) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static List<Opened> race(int contenders, java.util.function.Supplier<Opened> attempt)
            throws Exception {
        CyclicBarrier start = new CyclicBarrier(contenders);
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        try {
            List<Future<Opened>> futures = new ArrayList<>();
            for (int index = 0; index < contenders; index++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                                    return attempt.get();
                                }));
            }
            List<Opened> results = new ArrayList<>();
            for (Future<Opened> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}

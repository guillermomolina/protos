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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosFileFlow;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemNamespaceMutationFlow;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemOpenFlow;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemOpenOptions;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemTreeObservationFlow;
import com.guillermomolina.protos.runtime.io.ProtosPathComponents;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Shared I156 fixtures. Backend completions arrive on the grant's restricted worker thread, so
 * every helper waits (bounded) for the completion instead of assuming a synchronous callback.
 */
final class ProtosLinuxConfinedFilesystemTestSupport {
    static final long TIMEOUT_SECONDS = 10;

    private ProtosLinuxConfinedFilesystemTestSupport() {}

    /**
     * The explicit host admission of every I156 test that needs the real primitives. It is an
     * assumption, never a pass: an unsupported host reports the case as skipped, and the
     * fail-closed refusal on such hosts is covered separately.
     */
    static void assumeConfinementPrimitives() {
        boolean linuxX86 =
                System.getProperty("os.name", "").equalsIgnoreCase("linux")
                        && (System.getProperty("os.arch", "").equals("amd64")
                                || System.getProperty("os.arch", "").equals("x86_64"));
        assumeTrue(linuxX86, "I156 requires Linux x86_64");
        int abi;
        try {
            abi = ProtosLinuxConfinedFilesystemNative.landlockAbiVersion();
        } catch (IOException unavailable) {
            assumeTrue(false, "host kernel serves no Landlock: " + unavailable.getMessage());
            return;
        }
        assumeTrue(
                abi >= ProtosLinuxConfinedFilesystemLandlock.MINIMUM_LANDLOCK_ABI,
                "host Landlock ABI " + abi + " lacks REFER/TRUNCATE");
    }

    static ProtosLinuxConfinedReadWriteTreeFilesystemBackend provision(Path root) throws IOException {
        assumeConfinementPrimitives();
        return ProtosLinuxConfinedReadWriteTreeFilesystemBackend.provision(root);
    }

    static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "completion not reached");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    static ProtosFilesystemOpenOptions options(
            boolean read, boolean write, String creation, boolean truncate) {
        ProtosObjectValue options = new ProtosObjectValue(ProtosObjectValue.rootObject());
        options.createLocalSlot("read", read ? Boolean.TRUE : Boolean.FALSE);
        options.createLocalSlot("write", write ? Boolean.TRUE : Boolean.FALSE);
        if (creation != null) {
            options.createLocalSlot(creation, Boolean.TRUE);
        }
        if (truncate) {
            options.createLocalSlot("truncate", Boolean.TRUE);
        }
        return ProtosFilesystemOpenOptions.capture(options);
    }

    static ProtosPathComponents path(String... components) {
        return new ProtosPathComponents(List.of(components));
    }

    static boolean tryCreateSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            return false;
        }
    }

    static void createSymlinkOrSkip(Path link, Path target) {
        assumeTrue(tryCreateSymlink(link, target), "host cannot create symlink fixture");
    }

    /** Open completion that records commitment through the I156 effect boundary. */
    static class Opened implements ProtosStandardFilesystemProtocol.OpenCompletion {
        private final boolean allowCommit;
        final CountDownLatch done = new CountDownLatch(1);
        volatile boolean committed;
        volatile boolean failed;
        volatile boolean cancelled;
        volatile ProtosFileFlow.Resource resource;
        volatile ProtosFileFlow.Capabilities capabilities;
        volatile Thread completionThread;

        Opened(boolean allowCommit) {
            this.allowCommit = allowCommit;
        }

        @Override
        public boolean commitPortableEffect() {
            throw new AssertionError("I156 must not commit before its native effect");
        }

        @Override
        public ProtosFilesystemOpenFlow.EffectOutcome commitPortableEffect(
                ProtosFilesystemOpenFlow.OpenEffect effect) {
            if (!allowCommit) {
                cancelled = true;
                done.countDown();
                return ProtosFilesystemOpenFlow.EffectOutcome.CANCELLED;
            }
            if (!effect.publish()) {
                return ProtosFilesystemOpenFlow.EffectOutcome.NOT_PUBLISHED;
            }
            committed = true;
            return ProtosFilesystemOpenFlow.EffectOutcome.COMMITTED;
        }

        @Override
        public void succeeded(
                ProtosFileFlow.Resource selected,
                ProtosFileFlow.Capabilities selectedCapabilities,
                Runnable releaseIfUntransferred) {
            capabilities = selectedCapabilities;
            resource = selected;
            completionThread = Thread.currentThread();
            done.countDown();
        }

        @Override
        public void failed() {
            failed = true;
            completionThread = Thread.currentThread();
            done.countDown();
        }

        boolean succeeded() {
            return resource != null;
        }

        Opened awaited() {
            await(done);
            return this;
        }
    }

    static Opened open(
            ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend,
            ProtosFilesystemOpenOptions options,
            String... components) {
        return open(backend, options, List.of(components));
    }

    static Opened open(
            ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend,
            ProtosFilesystemOpenOptions options,
            List<String> components) {
        Opened opened = new Opened(true);
        backend.open(new ProtosPathComponents(components), options, opened);
        return opened.awaited();
    }

    static Opened openDenyingCommit(
            ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend,
            ProtosFilesystemOpenOptions options,
            String... components) {
        Opened opened = new Opened(false);
        backend.open(path(components), options, opened);
        return opened.awaited();
    }

    static final class Written implements ProtosFileFlow.WriteCompletion {
        final CountDownLatch done = new CountDownLatch(1);
        volatile boolean succeeded;
        volatile int prefix = -1;

        @Override
        public boolean commitFirstContribution() {
            return true;
        }

        @Override
        public void succeeded() {
            succeeded = true;
            done.countDown();
        }

        /** Set when the backend reported a failure whose progress is unknown. */
        volatile boolean unknownProgress;

        @Override
        public void failed(int contributedPrefix) {
            prefix = contributedPrefix;
            done.countDown();
        }

        @Override
        public void failedWithUnknownProgress() {
            unknownProgress = true;
            done.countDown();
        }
    }

    static Written write(Opened file, long position, String text) {
        Written written = new Written();
        ((ProtosFileFlow.WritableResource) file.resource)
                .writeAt(position, text.getBytes(StandardCharsets.UTF_8), written);
        await(written.done);
        return written;
    }

    /** One read outcome: data, EOF ({@code null} data, not failed) or failure. */
    record ReadOutcome(byte[] data, boolean eof, boolean failed) {}

    static ReadOutcome readOutcome(Opened file, long position, int maxBytes) {
        CountDownLatch done = new CountDownLatch(1);
        byte[][] data = new byte[1][];
        boolean[] flags = new boolean[2];
        ((ProtosFileFlow.ReadableResource) file.resource)
                .readAt(
                        position,
                        maxBytes,
                        new ProtosFileFlow.ReadCompletion() {
                            @Override
                            public void data(byte[] bytes) {
                                data[0] = bytes;
                                done.countDown();
                            }

                            @Override
                            public void eof() {
                                flags[0] = true;
                                done.countDown();
                            }

                            @Override
                            public void failed() {
                                flags[1] = true;
                                done.countDown();
                            }
                        });
        await(done);
        return new ReadOutcome(data[0], flags[0], flags[1]);
    }

    /** The bytes read, or {@code null} at EOF; a read failure fails the test. */
    static byte[] read(Opened file, long position, int maxBytes) {
        ReadOutcome outcome = readOutcome(file, position, maxBytes);
        assertFalse(outcome.failed(), "read failed");
        return outcome.eof() ? null : outcome.data();
    }

    static boolean readFails(Opened file) {
        return readOutcome(file, 0, 1).failed();
    }

    static byte[] readAll(Opened file) {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        long position = 0;
        byte[] chunk;
        while ((chunk = read(file, position, 4096)) != null) {
            all.writeBytes(chunk);
            position += chunk.length;
        }
        return all.toByteArray();
    }

    static String readText(Opened file) {
        return new String(readAll(file), StandardCharsets.UTF_8);
    }

    static long size(Opened file) {
        long[] value = {-1};
        CountDownLatch done = new CountDownLatch(1);
        ((ProtosFileFlow.SizedResource) file.resource).size(integer(value, done));
        await(done);
        return value[0];
    }

    static long endPosition(Opened file) {
        long[] value = {-1};
        CountDownLatch done = new CountDownLatch(1);
        ((ProtosFileFlow.SeekableResource) file.resource).endPosition(integer(value, done));
        await(done);
        return value[0];
    }

    private static ProtosFileFlow.IntegerCompletion integer(long[] value, CountDownLatch done) {
        return new ProtosFileFlow.IntegerCompletion() {
            @Override
            public void succeeded(long result) {
                value[0] = result;
                done.countDown();
            }

            @Override
            public void failed() {
                done.countDown();
            }
        };
    }

    static boolean truncate(Opened file, long size) {
        boolean[] succeeded = new boolean[1];
        CountDownLatch done = new CountDownLatch(1);
        ((ProtosFileFlow.TruncatableResource) file.resource)
                .truncate(
                        size,
                        new ProtosFileFlow.ChangeCompletion() {
                            @Override
                            public boolean commitChange() {
                                return true;
                            }

                            @Override
                            public void succeeded() {
                                succeeded[0] = true;
                                done.countDown();
                            }

                            @Override
                            public void failed() {
                                done.countDown();
                            }
                        });
        await(done);
        return succeeded[0];
    }

    static boolean sync(Opened file) {
        boolean[] succeeded = new boolean[1];
        CountDownLatch done = new CountDownLatch(1);
        ((ProtosFileFlow.SyncableResource) file.resource)
                .sync(
                        new ProtosFileFlow.SyncCompletion() {
                            @Override
                            public boolean commitDurability() {
                                return true;
                            }

                            @Override
                            public void succeeded() {
                                succeeded[0] = true;
                                done.countDown();
                            }

                            @Override
                            public void failed() {
                                done.countDown();
                            }
                        });
        await(done);
        return succeeded[0];
    }

    static boolean closeSucceeds(Opened file) {
        boolean[] succeeded = new boolean[1];
        CountDownLatch done = new CountDownLatch(1);
        file.resource.close(
                new ProtosFileFlow.CloseCompletion() {
                    @Override
                    public void succeeded() {
                        succeeded[0] = true;
                        done.countDown();
                    }

                    @Override
                    public void failed() {
                        done.countDown();
                    }
                });
        await(done);
        return succeeded[0];
    }

    static void close(Opened file) {
        assertTrue(closeSucceeds(file), "File close failed");
    }

    /** Entries keyed by name, or {@code null} when the observation failed. */
    static Map<String, ProtosFilesystemTreeObservationFlow.EntryKind> entriesOrNull(
            ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend, String... components) {
        LinkedHashMap<String, ProtosFilesystemTreeObservationFlow.EntryKind> result =
                new LinkedHashMap<>();
        boolean[] failed = new boolean[1];
        CountDownLatch done = new CountDownLatch(1);
        backend.entries(
                path(components),
                new ProtosFilesystemTreeObservationFlow.EntriesCompletion() {
                    @Override
                    public void succeeded(List<ProtosFilesystemTreeObservationFlow.Entry> observed) {
                        observed.forEach(entry -> result.put(entry.name(), entry.kind()));
                        done.countDown();
                    }

                    @Override
                    public void failed() {
                        failed[0] = true;
                        done.countDown();
                    }
                });
        await(done);
        return failed[0] ? null : result;
    }

    static Map<String, ProtosFilesystemTreeObservationFlow.EntryKind> entries(
            ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend, String... components) {
        Map<String, ProtosFilesystemTreeObservationFlow.EntryKind> observed =
                entriesOrNull(backend, components);
        assertTrue(observed != null, "entries failed");
        return observed;
    }

    static boolean entriesFailed(
            ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend, String... components) {
        return entriesOrNull(backend, components) == null;
    }

    /** Mutation completion that runs the effect like the flow and records its outcome. */
    static class Mutation implements ProtosFilesystemNamespaceMutationFlow.MutationCompletion {
        final CountDownLatch done = new CountDownLatch(1);
        volatile boolean effectSucceeded;
        volatile boolean noOpSucceeded;
        volatile boolean failed;
        volatile Exception failure;

        @Override
        public boolean commitPortableEffect(
                ProtosFilesystemNamespaceMutationFlow.NamespaceEffect effect) {
            try {
                effect.run();
                effectSucceeded = true;
                return true;
            } catch (Exception effectFailure) {
                failure = effectFailure;
                failed = true;
                return false;
            } finally {
                done.countDown();
            }
        }

        @Override
        public void succeeded() {
            noOpSucceeded = true;
            done.countDown();
        }

        @Override
        public void failed() {
            failed = true;
            done.countDown();
        }

        boolean succeededOutcome() {
            return effectSucceeded || noOpSucceeded;
        }

        Mutation awaited() {
            await(done);
            return this;
        }
    }

    static Mutation replace(
            ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend,
            ProtosPathComponents source,
            ProtosPathComponents target) {
        Mutation mutation = new Mutation();
        backend.replace(source, target, mutation);
        return mutation.awaited();
    }

    static Mutation remove(
            ProtosLinuxConfinedReadWriteTreeFilesystemBackend backend, ProtosPathComponents path) {
        Mutation mutation = new Mutation();
        backend.remove(path, mutation);
        return mutation.awaited();
    }
}

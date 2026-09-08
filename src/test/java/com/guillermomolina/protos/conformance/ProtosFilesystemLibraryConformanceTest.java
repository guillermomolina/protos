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
package com.guillermomolina.protos.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosExecutionOutcome;
import com.guillermomolina.protos.execution.ProtosNioReadOnlyTreeFilesystemBackend;
import com.guillermomolina.protos.execution.ProtosRootTaskExecution;
import com.guillermomolina.protos.execution.ProtosSourceCompiler;
import com.guillermomolina.protos.execution.ProtosStandardFilesystemProtocol;
import com.guillermomolina.protos.execution.ProtosStandardLibraryModuleResolver;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosFileFlow;
import com.guillermomolina.protos.runtime.ProtosFilesystemOpenFlow;
import com.guillermomolina.protos.runtime.ProtosFilesystemOpenOptions;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPathValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * LIB004-A1/A2 integration harness.
 *
 * <p>The behavior assertions live in Protos source. Java is restricted to provisioning the
 * confined Filesystem/standard-library resolver and inspecting the host fixture or exact Core
 * Error category where that boundary is not source-constructible.
 */
final class ProtosFilesystemLibraryConformanceTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path CASE_ROOT =
            Path.of("protos", "tests", "conformance", "library", "io");

    @TempDir Path authorityRoot;

    @Test
    void readAllBytesReturnsFreshFutureAndFreshOpenBytesForEmptyFile() throws Exception {
        Files.write(authorityRoot.resolve("empty.bin"), new byte[0]);

        try (Fixture fixture = fixture()) {
            assertSame(
                    ProtosBooleanValue.TRUE,
                    execute("read-all-bytes-empty.protos", fixture.activation()));
        }
    }

    @Test
    void readAllBytesPreservesOrderedContentAcrossMultipleReadWindows() throws Exception {
        byte[] content = new byte[131089];
        Arrays.fill(content, 0, 65536, (byte) 65);
        Arrays.fill(content, 65536, 131072, (byte) 66);
        Arrays.fill(content, 131072, content.length, (byte) 67);
        Files.write(authorityRoot.resolve("multi.bin"), content);

        try (Fixture fixture = fixture()) {
            assertSame(
                    ProtosBooleanValue.TRUE,
                    execute("read-all-bytes-multichunk.protos", fixture.activation()));
        }
    }

    @Test
    void readAllBytesPropagatesOpenFailureAsTheExistingCoreIoError() throws Exception {
        try (Fixture fixture = fixture()) {
            ProtosSignalException signal =
                    assertThrows(
                            ProtosSignalException.class,
                            () ->
                                    execute(
                                            "read-all-bytes-open-error.protos",
                                            fixture.activation()));

            assertSame(
                    ProtosCoreErrors.prototype(
                            fixture.activation(), ProtosCoreErrors.StandardError.I_O_ERROR),
                    signal.error().parent().orElseThrow());
        }
    }


    @Test
    void readAllBytesCancellationAfterAcquiredReadClosesBeforeTerminalCancellation()
            throws Exception {
        ControlledFixture fixture = controlledFixture(ReadMode.PENDING);
        ExecutorService executor = daemonExecutor();
        CompletableFuture<Object> execution =
                executeAsync("read-all-bytes-cancel-after-acquire.protos", fixture.activation(), executor);
        try {
            assertTrue(fixture.resource().awaitCloseStarted(), "File close did not start");
            ProtosFutureValue helperFuture = helperFuture(fixture);

            assertEquals(ProtosFutureValue.State.PENDING, helperFuture.state());
            assertDefaultReadOpen(fixture.backend());
            assertEquals(1, fixture.resource().readStarts());
            assertEquals(1, fixture.resource().readCancellations());
            assertEquals(1, fixture.resource().closeStarts());
            assertEquals(0, fixture.backend().untransferredReleases());

            fixture.resource().succeedClose();
            ProtosObjectValue result =
                    assertInstanceOf(ProtosObjectValue.class, execution.get(5, TimeUnit.SECONDS));

            assertSame(ProtosBooleanValue.TRUE, result.readLocalSlot("sameCancelResult").orElseThrow());
            assertSame(ProtosBooleanValue.TRUE, result.readLocalSlot("cancelled").orElseThrow());
            assertSame(helperFuture, result.readLocalSlot("future").orElseThrow());
            assertEquals(ProtosFutureValue.State.CANCELLED, helperFuture.state());
            assertEquals(1, fixture.resource().readStarts());
        } finally {
            fixture.resource().succeedCloseIfPending();
            shutdown(executor);
        }
    }

    @Test
    void readAllBytesReadFailureWaitsForSuspendingCleanupBeforePropagation()
            throws Exception {
        ControlledFixture fixture = controlledFixture(ReadMode.FAIL_FIRST_THEN_PENDING);
        ExecutorService executor = daemonExecutor();
        CompletableFuture<Object> execution =
                executeAsync("read-all-bytes-observe-io-error.protos", fixture.activation(), executor);
        try {
            assertTrue(fixture.resource().awaitCloseStarted(), "File close did not start");
            ProtosFutureValue helperFuture = helperFuture(fixture);

            assertEquals(ProtosFutureValue.State.PENDING, helperFuture.state());
            assertDefaultReadOpen(fixture.backend());
            assertEquals(2, fixture.resource().readStarts());
            assertEquals(1, fixture.resource().readCancellations());
            assertEquals(1, fixture.resource().closeStarts());

            fixture.resource().succeedClose();
            ProtosObjectValue result =
                    assertInstanceOf(ProtosObjectValue.class, execution.get(5, TimeUnit.SECONDS));

            assertObservedIoFailure(fixture, result, helperFuture);
        } finally {
            fixture.resource().succeedCloseIfPending();
            shutdown(executor);
        }
    }

    @Test
    void readAllBytesCloseFailureAfterSuccessfulReadFailsOnlyAfterCleanupCompletes()
            throws Exception {
        ControlledFixture fixture = controlledFixture(ReadMode.EOF);
        ExecutorService executor = daemonExecutor();
        CompletableFuture<Object> execution =
                executeAsync("read-all-bytes-observe-io-error.protos", fixture.activation(), executor);
        try {
            assertTrue(fixture.resource().awaitCloseStarted(), "File close did not start");
            ProtosFutureValue helperFuture = helperFuture(fixture);

            assertEquals(ProtosFutureValue.State.PENDING, helperFuture.state());
            assertDefaultReadOpen(fixture.backend());
            assertTrue(fixture.resource().readStarts() > 0);
            assertEquals(1, fixture.resource().closeStarts());

            fixture.resource().failClose();
            ProtosObjectValue result =
                    assertInstanceOf(ProtosObjectValue.class, execution.get(5, TimeUnit.SECONDS));

            assertObservedIoFailure(fixture, result, helperFuture);
        } finally {
            fixture.resource().succeedCloseIfPending();
            shutdown(executor);
        }
    }

    @Test
    void readAllBytesLaterCloseFailureComposesWithEarlierReadFailurePrecedence()
            throws Exception {
        ControlledFixture fixture = controlledFixture(ReadMode.FAIL_FIRST_THEN_PENDING);
        ExecutorService executor = daemonExecutor();
        CompletableFuture<Object> execution =
                executeAsync("read-all-bytes-observe-io-error.protos", fixture.activation(), executor);
        try {
            assertTrue(fixture.resource().awaitCloseStarted(), "File close did not start");
            ProtosFutureValue helperFuture = helperFuture(fixture);

            // The body has already established a read failure, but D043 cleanup is
            // still active and the helper cannot expose a terminal result before
            // the later close outcome is known.
            assertEquals(ProtosFutureValue.State.PENDING, helperFuture.state());
            assertEquals(2, fixture.resource().readStarts());
            assertEquals(1, fixture.resource().readCancellations());

            fixture.resource().failClose();
            ProtosObjectValue result =
                    assertInstanceOf(ProtosObjectValue.class, execution.get(5, TimeUnit.SECONDS));

            assertObservedIoFailure(fixture, result, helperFuture);
            assertEquals(1, fixture.resource().closeStarts());
        } finally {
            fixture.resource().succeedCloseIfPending();
            shutdown(executor);
        }
    }

    private Fixture fixture() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(authorityRoot);
        if (!backend.secureConfinementAvailable()) {
            backend.close();
            assumeTrue(false, "host provider has no SecureDirectoryStream");
        }

        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(), activation, backend);
        ProtosFilesystemValue filesystem =
                org.junit.jupiter.api.Assertions.assertInstanceOf(
                        ProtosFilesystemValue.class, rawFilesystem);
        activation.context().createLocalSlot("filesystem", filesystem);
        return new Fixture(activation, backend);
    }


    private ControlledFixture controlledFixture(ReadMode readMode) throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue control = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosFutureValue gate =
                new ProtosFutureValue(prelude.futurePrototype(), activation.executionDomain());
        ControlledReadableResource resource =
                new ControlledReadableResource(readMode, gate, activation);
        ControlledBackend backend = new ControlledBackend(resource);
        ProtosFilesystemValue filesystem =
                assertInstanceOf(
                        ProtosFilesystemValue.class,
                        ProtosStandardFilesystemProtocol.createCapability(
                                prelude.bytesPrototypeForRuntime(), activation, backend));

        activation.context().createLocalSlot("filesystem", filesystem);
        activation.context().createLocalSlot("control", control);
        activation.context().createLocalSlot("gate", gate);
        return new ControlledFixture(activation, prelude, control, backend, resource);
    }

    private static CompletableFuture<Object> executeAsync(
            String file, ProtosActivation activation, ExecutorService executor) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return execute(file, activation);
                    } catch (IOException failure) {
                        throw new CompletionException(failure);
                    }
                },
                executor);
    }

    private static ExecutorService daemonExecutor() {
        return Executors.newSingleThreadExecutor(
                runnable -> {
                    Thread thread = new Thread(runnable, "lib004-a2-controlled-execution");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static ProtosFutureValue helperFuture(ControlledFixture fixture) {
        return assertInstanceOf(
                ProtosFutureValue.class,
                fixture.control().readLocalSlot("future").orElseThrow());
    }

    private static void assertObservedIoFailure(
            ControlledFixture fixture,
            ProtosObjectValue result,
            ProtosFutureValue helperFuture) {
        assertSame(ProtosBooleanValue.TRUE, result.readLocalSlot("failed").orElseThrow());
        assertSame(helperFuture, result.readLocalSlot("future").orElseThrow());
        assertEquals(ProtosFutureValue.State.FAILED, helperFuture.state());
        ProtosObjectValue error = helperFuture.failedError().orElseThrow();
        assertSame(error, result.readLocalSlot("observedError").orElseThrow());
        assertSame(
                ProtosCoreErrors.prototype(
                        fixture.activation(), ProtosCoreErrors.StandardError.I_O_ERROR),
                error.parent().orElseThrow());
    }

    private static void assertDefaultReadOpen(ControlledBackend backend) {
        ProtosFilesystemOpenOptions options = backend.options().get();
        assertTrue(options.readAccess());
        assertFalse(options.writeAccess());
        assertEquals(ProtosFilesystemOpenOptions.Creation.EXISTING, options.creation());
        assertFalse(options.truncateInitialContent());
        assertEquals(ProtosFilesystemOpenOptions.Placement.POSITIONED, options.placement());
        assertEquals(1, backend.openCount());
        assertEquals(0, backend.openCancellations());
    }

    private static Object execute(String file, ProtosActivation activation) throws IOException {
        String source =
                Files.readString(CASE_ROOT.resolve(file), StandardCharsets.UTF_8);
        ProtosExecutionOutcome outcome =
                ProtosRootTaskExecution.execute(
                        new ProtosSourceCompiler().compile(source), activation);

        return switch (outcome.state()) {
            case COMPLETED -> outcome.value();
            case FAILED -> throw new ProtosSignalException(outcome.error());
            case CANCELLED -> throw new AssertionError(
                    "LIB004-A1 conformance root unexpectedly cancelled");
        };
    }



    private enum ReadMode {
        PENDING,
        FAIL_FIRST_THEN_PENDING,
        EOF
    }

    private record ControlledFixture(
            ProtosActivation activation,
            ProtosPrelude prelude,
            ProtosObjectValue control,
            ControlledBackend backend,
            ControlledReadableResource resource) {}

    private static final class ControlledBackend
            implements ProtosStandardFilesystemProtocol.Backend {
        private final ControlledReadableResource resource;
        private final AtomicInteger openCount = new AtomicInteger();
        private final AtomicInteger openCancellations = new AtomicInteger();
        private final AtomicInteger untransferredReleases = new AtomicInteger();
        private final AtomicReference<ProtosFilesystemOpenOptions> options =
                new AtomicReference<>();

        private ControlledBackend(ControlledReadableResource resource) {
            this.resource = resource;
        }

        @Override
        public ProtosFilesystemOpenFlow.Cancellation open(
                ProtosPathValue path,
                ProtosFilesystemOpenOptions capturedOptions,
                ProtosStandardFilesystemProtocol.OpenCompletion completion) {
            openCount.incrementAndGet();
            if (!options.compareAndSet(null, capturedOptions)) {
                throw new IllegalStateException("LIB004-A2 expected exactly one open");
            }
            completion.succeeded(
                    resource,
                    new ProtosFileFlow.Capabilities(
                            true, false, false, false, false, false),
                    untransferredReleases::incrementAndGet);
            return openCancellations::incrementAndGet;
        }

        private AtomicReference<ProtosFilesystemOpenOptions> options() {
            return options;
        }

        private int openCount() {
            return openCount.get();
        }

        private int openCancellations() {
            return openCancellations.get();
        }

        private int untransferredReleases() {
            return untransferredReleases.get();
        }
    }

    private static final class ControlledReadableResource
            implements ProtosFileFlow.ReadableResource {
        private final ReadMode readMode;
        private final ProtosFutureValue gate;
        private final ProtosActivation activation;
        private final AtomicInteger readStarts = new AtomicInteger();
        private final AtomicInteger readCancellations = new AtomicInteger();
        private final AtomicInteger closeStarts = new AtomicInteger();
        private final AtomicReference<ProtosFileFlow.CloseCompletion> closeCompletion =
                new AtomicReference<>();
        private final AtomicBoolean closeCompleted = new AtomicBoolean();
        private final CountDownLatch closeStarted = new CountDownLatch(1);

        private ControlledReadableResource(
                ReadMode readMode, ProtosFutureValue gate, ProtosActivation activation) {
            this.readMode = readMode;
            this.gate = gate;
            this.activation = activation;
        }

        @Override
        public ProtosFileFlow.Cancellation readAt(
                BigInteger position,
                int maxBytes,
                ProtosFileFlow.ReadCompletion completion) {
            int invocation = readStarts.incrementAndGet();
            if (invocation == 1) {
                gate.resolve(ProtosNullValue.INSTANCE, activation);
            }

            if (readMode == ReadMode.EOF) {
                completion.eof();
                return () -> {};
            }
            if (readMode == ReadMode.FAIL_FIRST_THEN_PENDING && invocation == 1) {
                completion.failed();
                return () -> {};
            }
            return readCancellations::incrementAndGet;
        }

        @Override
        public void close(ProtosFileFlow.CloseCompletion completion) {
            if (!closeCompletion.compareAndSet(null, completion)) {
                throw new IllegalStateException("File resource close started more than once");
            }
            closeStarts.incrementAndGet();
            closeStarted.countDown();
        }

        private boolean awaitCloseStarted() throws InterruptedException {
            return closeStarted.await(5, TimeUnit.SECONDS);
        }

        private void succeedClose() {
            completeClose(false);
        }

        private void failClose() {
            completeClose(true);
        }

        private void succeedCloseIfPending() {
            ProtosFileFlow.CloseCompletion completion = closeCompletion.get();
            if (completion != null && closeCompleted.compareAndSet(false, true)) {
                completion.succeeded();
            }
        }

        private void completeClose(boolean fail) {
            ProtosFileFlow.CloseCompletion completion = closeCompletion.get();
            if (completion == null) {
                throw new IllegalStateException("File close has not started");
            }
            if (!closeCompleted.compareAndSet(false, true)) {
                throw new IllegalStateException("File close already completed");
            }
            if (fail) {
                completion.failed();
            } else {
                completion.succeeded();
            }
        }

        private int readStarts() {
            return readStarts.get();
        }

        private int readCancellations() {
            return readCancellations.get();
        }

        private int closeStarts() {
            return closeStarts.get();
        }
    }

    private record Fixture(
            ProtosActivation activation,
            ProtosNioReadOnlyTreeFilesystemBackend backend)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            backend.close();
        }
    }
}

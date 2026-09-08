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
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosFilesystemTreeObservationFlowTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void invalidPathFailsBeforeEitherBackendSeesAuthority() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue entries =
                x.flow.entries(x.activation, new ProtosStringValue("not-a-path"));
        ProtosFutureValue capture =
                x.flow.captureTree(x.activation, new ProtosStringValue("not-a-path"));
        assertInvalid(x, entries);
        assertInvalid(x, capture);
        assertEquals(0, x.backend.entriesCalls.get());
        assertEquals(0, x.backend.captureCalls.get());
    }

    @Test
    void entriesDefensivelySnapshotExactNamesKindsAndPreserveBackendOrder() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future = x.flow.entries(x.activation, path(x.prelude, "root"));
        EntriesInvocation invocation = x.backend.entriesInvocations.remove();

        java.util.ArrayList<ProtosFilesystemTreeObservationFlow.Entry> supplied =
                new java.util.ArrayList<>(
                        List.of(
                                entry("z", ProtosFilesystemTreeObservationFlow.EntryKind.OTHER),
                                entry("a", ProtosFilesystemTreeObservationFlow.EntryKind.REGULAR)));
        invocation.completion.succeeded(supplied);
        supplied.clear();

        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        assertSame(x.entriesResult, future.resolvedValue().orElseThrow());
        assertEquals(
                List.of(
                        entry("z", ProtosFilesystemTreeObservationFlow.EntryKind.OTHER),
                        entry("a", ProtosFilesystemTreeObservationFlow.EntryKind.REGULAR)),
                x.materializer.lastEntries);
        assertFalse(future.cancelRequest());
    }

    @Test
    void duplicateBackendEntryNamesFailAsIoError() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future = x.flow.entries(x.activation, path(x.prelude, "root"));
        EntriesInvocation invocation = x.backend.entriesInvocations.remove();
        invocation.completion.succeeded(
                List.of(
                        entry("same", ProtosFilesystemTreeObservationFlow.EntryKind.REGULAR),
                        entry("same", ProtosFilesystemTreeObservationFlow.EntryKind.DIRECTORY)));
        assertIoError(x, future);
        assertEquals(0, x.materializer.entriesCalls.get());
        assertThrows(
                IllegalArgumentException.class,
                () -> entry(".", ProtosFilesystemTreeObservationFlow.EntryKind.REGULAR));
    }

    @Test
    void captureTransfersCustodyOnlyWhenResultWinsTerminalCutover() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future = x.flow.captureTree(x.activation, path(x.prelude, "root"));
        CaptureInvocation invocation = x.backend.captureInvocations.remove();
        DummyCapture capture = new DummyCapture();
        AtomicInteger releases = new AtomicInteger();
        invocation.completion.succeeded(capture, releases::incrementAndGet);
        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        assertSame(x.captureResult, future.resolvedValue().orElseThrow());
        assertSame(capture, x.materializer.lastCapture);
        assertEquals(0, releases.get());
        assertFalse(future.cancelRequest());
    }

    @Test
    void cancelledCaptureReachesBackendAndReleasesLateResultWithoutMaterialization()
            throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future = x.flow.captureTree(x.activation, path(x.prelude, "root"));
        CaptureInvocation invocation = x.backend.captureInvocations.remove();
        assertTrue(future.cancelRequest());
        assertEquals(ProtosFutureValue.State.CANCELLED, future.state());
        assertEquals(1, invocation.cancellations.get());

        AtomicInteger releases = new AtomicInteger();
        invocation.completion.succeeded(new DummyCapture(), releases::incrementAndGet);
        assertEquals(1, releases.get());
        assertEquals(0, x.materializer.captureCalls.get());
        assertEquals(ProtosFutureValue.State.CANCELLED, future.state());
    }

    @Test
    void invalidCaptureDescriptorFailsAsIoErrorAndNullCaptureReleasesCustody() throws Exception {
        Fixture x = fixture();

        ProtosFutureValue missingRelease =
                x.flow.captureTree(x.activation, path(x.prelude, "one"));
        CaptureInvocation first = x.backend.captureInvocations.remove();
        first.completion.succeeded(new DummyCapture(), null);
        assertIoError(x, missingRelease);

        ProtosFutureValue missingCapture =
                x.flow.captureTree(x.activation, path(x.prelude, "two"));
        CaptureInvocation second = x.backend.captureInvocations.remove();
        AtomicInteger releases = new AtomicInteger();
        second.completion.succeeded(null, releases::incrementAndGet);
        assertIoError(x, missingCapture);
        assertEquals(1, releases.get());
    }

    @Test
    void materializationFailureReleasesCapturedCustodyAndFailsFuture() throws Exception {
        Fixture x = fixture();
        x.materializer.failCapture = true;
        ProtosFutureValue future = x.flow.captureTree(x.activation, path(x.prelude, "root"));
        CaptureInvocation invocation = x.backend.captureInvocations.remove();
        AtomicInteger releases = new AtomicInteger();
        invocation.completion.succeeded(new DummyCapture(), releases::incrementAndGet);
        assertIoError(x, future);
        assertEquals(1, releases.get());
        assertEquals(1, x.materializer.captureCalls.get());
    }

    @Test
    void backendFailureOrThrowBecomesIoErrorAndCallsUseFreshFutures() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue first = x.flow.entries(x.activation, path(x.prelude, "one"));
        ProtosFutureValue second = x.flow.entries(x.activation, path(x.prelude, "two"));
        assertFalse(first == second);
        x.backend.entriesInvocations.remove().completion.failed();
        x.backend.entriesInvocations.remove().completion.failed();
        assertIoError(x, first);
        assertIoError(x, second);

        x.backend.throwCapture = true;
        ProtosFutureValue thrown = x.flow.captureTree(x.activation, path(x.prelude, "three"));
        assertIoError(x, thrown);
    }

    private static Fixture fixture() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosFilesystemValue filesystem = new ProtosFilesystemValue();
        RecordingBackend backend = new RecordingBackend();
        RecordingMaterializer materializer = new RecordingMaterializer();
        ProtosObjectValue entriesResult = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue captureResult = new ProtosObjectValue(ProtosObjectValue.rootObject());
        materializer.entriesResult = entriesResult;
        materializer.captureResult = captureResult;
        ProtosFilesystemTreeObservationFlow flow =
                new ProtosFilesystemTreeObservationFlow(
                        filesystem,
                        activation,
                        backend::entries,
                        backend::captureTree,
                        materializer);
        return new Fixture(
                prelude,
                activation,
                filesystem,
                backend,
                materializer,
                entriesResult,
                captureResult,
                flow);
    }

    private static ProtosFilesystemTreeObservationFlow.Entry entry(
            String name, ProtosFilesystemTreeObservationFlow.EntryKind kind) {
        return new ProtosFilesystemTreeObservationFlow.Entry(name, kind);
    }

    private static ProtosPathValue path(ProtosPrelude prelude, String name) {
        return new ProtosPathValue(
                prelude.pathPrototype(),
                false,
                List.of(new ProtosPathValue.Normal(name)));
    }

    private static void assertInvalid(Fixture x, ProtosFutureValue future) {
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                x.prelude.bindings().readLocalSlot("InvalidIOArgument").orElseThrow(),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    private static void assertIoError(Fixture x, ProtosFutureValue future) {
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                x.prelude.bindings().readLocalSlot("IOError").orElseThrow(),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation,
            ProtosFilesystemValue filesystem,
            RecordingBackend backend,
            RecordingMaterializer materializer,
            ProtosObjectValue entriesResult,
            ProtosObjectValue captureResult,
            ProtosFilesystemTreeObservationFlow flow) {}

    private static final class RecordingBackend {
        private final AtomicInteger entriesCalls = new AtomicInteger();
        private final AtomicInteger captureCalls = new AtomicInteger();
        private final ArrayDeque<EntriesInvocation> entriesInvocations = new ArrayDeque<>();
        private final ArrayDeque<CaptureInvocation> captureInvocations = new ArrayDeque<>();
        private boolean throwCapture;

        ProtosFilesystemTreeObservationFlow.Cancellation entries(
                ProtosPathValue path,
                ProtosFilesystemTreeObservationFlow.EntriesCompletion completion) {
            entriesCalls.incrementAndGet();
            EntriesInvocation invocation = new EntriesInvocation(completion);
            entriesInvocations.add(invocation);
            return invocation.cancellations::incrementAndGet;
        }

        ProtosFilesystemTreeObservationFlow.Cancellation captureTree(
                ProtosPathValue path,
                ProtosFilesystemTreeObservationFlow.CaptureCompletion completion) {
            captureCalls.incrementAndGet();
            if (throwCapture) {
                throw new IllegalStateException("simulated backend failure");
            }
            CaptureInvocation invocation = new CaptureInvocation(completion);
            captureInvocations.add(invocation);
            return invocation.cancellations::incrementAndGet;
        }
    }

    private static final class RecordingMaterializer
            implements ProtosFilesystemTreeObservationFlow.ResultMaterializer {
        private final AtomicInteger entriesCalls = new AtomicInteger();
        private final AtomicInteger captureCalls = new AtomicInteger();
        private List<ProtosFilesystemTreeObservationFlow.Entry> lastEntries;
        private ProtosFilesystemTreeObservationFlow.CapturedTree lastCapture;
        private ProtosObjectValue entriesResult;
        private ProtosObjectValue captureResult;
        private boolean failCapture;

        @Override
        public ProtosObjectValue entries(List<ProtosFilesystemTreeObservationFlow.Entry> entries) {
            entriesCalls.incrementAndGet();
            lastEntries = entries;
            return entriesResult;
        }

        @Override
        public ProtosObjectValue capturedTree(
                ProtosFilesystemTreeObservationFlow.CapturedTree capturedTree) {
            captureCalls.incrementAndGet();
            lastCapture = capturedTree;
            if (failCapture) {
                throw new IllegalStateException("simulated materializer failure");
            }
            return captureResult;
        }
    }

    private static final class EntriesInvocation {
        private final ProtosFilesystemTreeObservationFlow.EntriesCompletion completion;
        private final AtomicInteger cancellations = new AtomicInteger();

        private EntriesInvocation(ProtosFilesystemTreeObservationFlow.EntriesCompletion completion) {
            this.completion = completion;
        }
    }

    private static final class CaptureInvocation {
        private final ProtosFilesystemTreeObservationFlow.CaptureCompletion completion;
        private final AtomicInteger cancellations = new AtomicInteger();

        private CaptureInvocation(ProtosFilesystemTreeObservationFlow.CaptureCompletion completion) {
            this.completion = completion;
        }
    }

    private static final class DummyCapture
            implements ProtosFilesystemTreeObservationFlow.CapturedTree {}
}

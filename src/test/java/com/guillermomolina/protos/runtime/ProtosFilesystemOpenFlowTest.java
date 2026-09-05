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

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProtosFilesystemOpenFlowTest {
    @Test
    void defaultOpenCapturesPortableDefaultsAndTransfersResultCustody() throws Exception {
        Fixture x = fixture();
        ProtosPathValue path = path(x.prelude);
        ProtosFutureValue future = x.flow.open(x.activation, path);

        assertEquals(1, x.backend.invocations.size());
        Invocation invocation = x.backend.invocations.get(0);
        assertSame(path, invocation.path);
        assertTrue(invocation.options.readAccess());
        assertFalse(invocation.options.writeAccess());
        assertEquals(
                ProtosFilesystemOpenOptions.Creation.EXISTING,
                invocation.options.creation());
        assertFalse(invocation.options.truncateInitialContent());
        assertEquals(
                ProtosFilesystemOpenOptions.Placement.POSITIONED,
                invocation.options.placement());

        ProtosObjectValue file = new ProtosObjectValue(ProtosObjectValue.rootObject());
        AtomicInteger releases = new AtomicInteger();
        invocation.completion.succeeded(file, releases::incrementAndGet);

        assertEquals(ProtosFutureValue.State.RESOLVED, future.state());
        assertSame(file, future.resolvedValue().orElseThrow());
        assertEquals(0, releases.get());
    }

    @Test
    void localOptionsAreSnapshottedOnceAndDelegatedSlotsAreIgnored() throws Exception {
        Fixture x = fixture();
        ProtosObjectValue parent = new ProtosObjectValue(ProtosObjectValue.rootObject());
        parent.createLocalSlot("notAStandardOption", ProtosBooleanValue.TRUE);

        ProtosObjectValue options = new ProtosObjectValue(parent);
        options.createLocalSlot("read", ProtosBooleanValue.FALSE);
        options.createLocalSlot("write", ProtosBooleanValue.TRUE);
        options.createLocalSlot("create", ProtosBooleanValue.TRUE);
        ProtosFutureValue future = x.flow.open(x.activation, path(x.prelude), options);

        assertEquals(ProtosFutureValue.State.PENDING, future.state());
        ProtosFilesystemOpenOptions captured = x.backend.invocations.get(0).options;

        options.assignLocalSlot("read", ProtosBooleanValue.TRUE);
        options.assignLocalSlot("write", ProtosBooleanValue.FALSE);
        options.assignLocalSlot("create", ProtosBooleanValue.FALSE);

        assertFalse(captured.readAccess());
        assertTrue(captured.writeAccess());
        assertEquals(ProtosFilesystemOpenOptions.Creation.CREATE, captured.creation());
        assertEquals(
                ProtosFilesystemOpenOptions.Placement.POSITIONED, captured.placement());
    }

    @Test
    void invalidOptionsFailBeforeBackendAuthorityIsExercised() throws Exception {
        Fixture x = fixture();
        List<ProtosObjectValue> invalid = new ArrayList<>();

        ProtosObjectValue noAccess = options();
        noAccess.createLocalSlot("read", ProtosBooleanValue.FALSE);
        invalid.add(noAccess);

        ProtosObjectValue appendWithoutWrite = options();
        appendWithoutWrite.createLocalSlot("append", ProtosBooleanValue.TRUE);
        invalid.add(appendWithoutWrite);

        ProtosObjectValue truncateWithoutWrite = options();
        truncateWithoutWrite.createLocalSlot("truncate", ProtosBooleanValue.TRUE);
        invalid.add(truncateWithoutWrite);

        ProtosObjectValue appendAndTruncate = options();
        appendAndTruncate.createLocalSlot("write", ProtosBooleanValue.TRUE);
        appendAndTruncate.createLocalSlot("append", ProtosBooleanValue.TRUE);
        appendAndTruncate.createLocalSlot("truncate", ProtosBooleanValue.TRUE);
        invalid.add(appendAndTruncate);

        ProtosObjectValue bothCreationModes = options();
        bothCreationModes.createLocalSlot("create", ProtosBooleanValue.TRUE);
        bothCreationModes.createLocalSlot("createNew", ProtosBooleanValue.TRUE);
        invalid.add(bothCreationModes);

        ProtosObjectValue unknownLocalSlot = options();
        unknownLocalSlot.createLocalSlot("mode", ProtosBooleanValue.TRUE);
        invalid.add(unknownLocalSlot);

        ProtosObjectValue nonBoolean = options();
        nonBoolean.createLocalSlot("write", new ProtosIntegerValue(BigInteger.ONE));
        invalid.add(nonBoolean);

        for (ProtosObjectValue candidate : invalid) {
            ProtosFutureValue future = x.flow.open(x.activation, path(x.prelude), candidate);
            assertInvalidIoArgument(x.prelude, future);
        }
        assertTrue(x.backend.invocations.isEmpty());

        ProtosFutureValue nonObjectOptions =
                x.flow.open(x.activation, path(x.prelude), ProtosNullValue.INSTANCE);
        assertInvalidIoArgument(x.prelude, nonObjectOptions);
        assertTrue(x.backend.invocations.isEmpty());
    }

    @Test
    void invalidPathFailsBeforeBackendAuthorityIsExercised() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future =
                x.flow.open(x.activation, new ProtosStringValue("not a Path"));
        assertInvalidIoArgument(x.prelude, future);
        assertTrue(x.backend.invocations.isEmpty());
    }

    @Test
    void cancellationBeforeCommitWinsAndLateAcquisitionIsReleased() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future = x.flow.open(x.activation, path(x.prelude));
        Invocation invocation = x.backend.invocations.get(0);

        assertTrue(future.cancelRequest());
        assertEquals(ProtosFutureValue.State.CANCELLED, future.state());
        assertEquals(1, invocation.cancelCount.get());
        assertFalse(invocation.completion.commitPortableEffect());

        AtomicInteger releases = new AtomicInteger();
        invocation.completion.succeeded(
                new ProtosObjectValue(ProtosObjectValue.rootObject()),
                releases::incrementAndGet);
        assertEquals(ProtosFutureValue.State.CANCELLED, future.state());
        assertEquals(1, releases.get());
    }

    @Test
    void portableEffectCommitDefeatsLaterCancellationAndFailureRemainsFailure()
            throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future = x.flow.open(x.activation, path(x.prelude));
        Invocation invocation = x.backend.invocations.get(0);

        assertTrue(invocation.completion.commitPortableEffect());
        assertTrue(future.cancelRequest());
        assertEquals(ProtosFutureValue.State.PENDING, future.state());
        assertEquals(1, invocation.cancelCount.get());

        invocation.completion.failed();
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                x.prelude.standardErrorPrototype("IOError"),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    @Test
    void actorTerminationRequestsOrdinaryPrecommitCancellation() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue future = x.flow.open(x.activation, path(x.prelude));
        Invocation invocation = x.backend.invocations.get(0);

        x.activation.executionDomain().actorTerminated();

        assertEquals(ProtosFutureValue.State.CANCELLED, future.state());
        assertEquals(1, invocation.cancelCount.get());
    }

    @Test
    void independentOpensAreNotFilesystemWideSerialized() throws Exception {
        Fixture x = fixture();
        ProtosFutureValue first = x.flow.open(x.activation, path(x.prelude));
        ProtosFutureValue second = x.flow.open(x.activation, path(x.prelude));

        assertEquals(2, x.backend.invocations.size());
        assertEquals(ProtosFutureValue.State.PENDING, first.state());
        assertEquals(ProtosFutureValue.State.PENDING, second.state());

        ProtosObjectValue firstFile = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue secondFile = new ProtosObjectValue(ProtosObjectValue.rootObject());
        x.backend.invocations.get(1).completion.succeeded(secondFile, () -> {});
        x.backend.invocations.get(0).completion.succeeded(firstFile, () -> {});

        assertSame(firstFile, first.resolvedValue().orElseThrow());
        assertSame(secondFile, second.resolvedValue().orElseThrow());
    }

    private static void assertInvalidIoArgument(
            ProtosPrelude prelude, ProtosFutureValue future) {
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                prelude.standardErrorPrototype("InvalidIOArgument"),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    private static ProtosObjectValue options() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    private static ProtosPathValue path(ProtosPrelude prelude) {
        return new ProtosPathValue(
                prelude.pathPrototype(),
                false,
                List.of(new ProtosPathValue.Normal("file.bin")));
    }

    private static Fixture fixture() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(java.nio.file.Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue filesystem =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        RecordingBackend backend = new RecordingBackend();
        ProtosFilesystemOpenFlow flow =
                new ProtosFilesystemOpenFlow(filesystem, activation, backend);
        return new Fixture(prelude, activation, backend, flow);
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation,
            RecordingBackend backend,
            ProtosFilesystemOpenFlow flow) {}

    private static final class RecordingBackend
            implements ProtosFilesystemOpenFlow.Backend {
        private final List<Invocation> invocations = new ArrayList<>();

        @Override
        public ProtosFilesystemOpenFlow.Cancellation open(
                ProtosPathValue path,
                ProtosFilesystemOpenOptions options,
                ProtosFilesystemOpenFlow.OpenCompletion completion) {
            Invocation invocation = new Invocation(path, options, completion);
            invocations.add(invocation);
            return invocation.cancelCount::incrementAndGet;
        }
    }

    private static final class Invocation {
        private final ProtosPathValue path;
        private final ProtosFilesystemOpenOptions options;
        private final ProtosFilesystemOpenFlow.OpenCompletion completion;
        private final AtomicInteger cancelCount = new AtomicInteger();

        private Invocation(
                ProtosPathValue path,
                ProtosFilesystemOpenOptions options,
                ProtosFilesystemOpenFlow.OpenCompletion completion) {
            this.path = path;
            this.options = options;
            this.completion = completion;
        }
    }
}

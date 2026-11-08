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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * I156: File write failures by phase. An ordinary backend failure reports the exact accepted
 * prefix and the cursor advances by it; an unexpected failure with unknown progress invents no
 * prefix. A committed write keeps its commitment against later cancellation; a second report is
 * inert.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
final class ProtosFileFlowWriteFailureTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void aKnownPartialPrefixKeepsTheRealNumberOfBytes() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue write = x.write(1, 2, 3, 4);
        Pending pending = x.resource.writes.pollFirst();
        assertTrue(pending.completion.commitFirstContribution());

        pending.completion.failed(2);

        assertIoError(x, write);
        x.write(5);
        assertEquals(2L, x.resource.lastPosition(), "the cursor advanced by the real prefix");
    }

    @Test
    void unknownProgressAfterCommitmentInventsNoPrefix() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue write = x.write(1, 2, 3, 4);
        Pending pending = x.resource.writes.pollFirst();
        assertTrue(pending.completion.commitFirstContribution());

        pending.completion.failedWithUnknownProgress();

        assertIoError(x, write);
        assertIoError(x, x.flow.position(x.activation));
        assertIoError(
                x,
                x.flow.seekBy(x.activation, ProtosNumericValueSupport.integer(1L)));

        int before = x.resource.positions.size();
        assertIoError(x, x.write(5));
        assertEquals(
                before,
                x.resource.positions.size(),
                "unknown cursor must not dispatch another positioned write");
    }

    @Test
    void absoluteSeekRecoversUnknownWritePosition() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue first = x.write(1, 2, 3);
        Pending pending = x.resource.writes.pollFirst();
        assertTrue(pending.completion.commitFirstContribution());
        pending.completion.failedWithUnknownProgress();
        assertIoError(x, first);

        ProtosFutureValue seek =
                x.flow.seek(x.activation, ProtosNumericValueSupport.integer(17L));
        assertEquals(ProtosFutureValue.State.RESOLVED, seek.state());
        assertEquals(
                ProtosFutureValue.State.RESOLVED,
                x.flow.position(x.activation).state());

        x.write(4);
        assertEquals(17L, x.resource.lastPosition());
    }

    @Test
    void seekToEndRecoversUnknownWritePosition() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue first = x.write(1, 2, 3);
        Pending pending = x.resource.writes.pollFirst();
        assertTrue(pending.completion.commitFirstContribution());
        pending.completion.failedWithUnknownProgress();
        assertIoError(x, first);

        ProtosFutureValue seek = x.flow.seekToEnd(x.activation);
        assertEquals(ProtosFutureValue.State.RESOLVED, seek.state());

        x.write(4);
        assertEquals(23L, x.resource.lastPosition());
    }

    @Test
    void duplicateCompletionCannotRestoreAnUnknownCursor() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue first = x.write(1, 2, 3);
        Pending pending = x.resource.writes.pollFirst();
        assertTrue(pending.completion.commitFirstContribution());

        pending.completion.failedWithUnknownProgress();
        Object error = first.failedError().orElseThrow();
        pending.completion.succeeded();
        pending.completion.failed(3);

        assertSame(error, first.failedError().orElseThrow());
        assertIoError(x, x.flow.position(x.activation));
    }

    @Test
    void independentOperationsRemainAvailableWithUnknownCursor() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue first = x.write(1, 2, 3);
        Pending pending = x.resource.writes.pollFirst();

        assertTrue(pending.completion.commitFirstContribution());
        pending.completion.failedWithUnknownProgress();
        assertIoError(x, first);

        assertIoError(x, x.flow.position(x.activation));

        assertEquals(
                ProtosFutureValue.State.RESOLVED,
                x.flow.size(x.activation).state());

        assertEquals(
                ProtosFutureValue.State.RESOLVED,
                x.flow.sync(x.activation).state());

        assertEquals(
                ProtosFutureValue.State.RESOLVED,
                x.flow.truncate(
                                x.activation,
                                ProtosNumericValueSupport.integer(12L))
                        .state());

        // Position-independent operations do not invent a cursor.
        assertIoError(x, x.flow.position(x.activation));
    }

    @Test
    void unknownProgressBeforeCommitmentIsAZeroEffectFailure() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue write = x.write(1, 2);
        Pending pending = x.resource.writes.pollFirst();

        pending.completion.failedWithUnknownProgress();

        assertIoError(x, write);
        assertTrue(!pending.completion.commitFirstContribution(), "no contribution after the failure");
        x.write(9);
        assertEquals(0L, x.resource.lastPosition(), "proven zero effect retains the cursor");
    }

    @Test
    void cancellationBeforeCommitmentWinsOverALaterUnexpectedFailure() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue write = x.write(1, 2);
        Pending pending = x.resource.writes.pollFirst();

        assertTrue(write.cancelRequest());
        pending.completion.failedWithUnknownProgress();

        assertEquals(ProtosFutureValue.State.CANCELLED, write.state());
        assertTrue(!pending.completion.commitFirstContribution(), "a cancelled write never commits");
    }

    @Test
    void cancellationAfterCommitmentCannotReplaceTheFailure() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue write = x.write(1, 2);
        Pending pending = x.resource.writes.pollFirst();
        assertTrue(pending.completion.commitFirstContribution());

        write.cancelRequest();
        assertEquals(ProtosFutureValue.State.PENDING, write.state(), "a committed write is not cancelled");
        pending.completion.failedWithUnknownProgress();

        assertIoError(x, write);
    }

    @Test
    void aDuplicateReportAfterTheFailureIsInert() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue write = x.write(1, 2, 3);
        Pending pending = x.resource.writes.pollFirst();
        assertTrue(pending.completion.commitFirstContribution());
        pending.completion.failed(1);
        Object error = write.failedError().orElseThrow();

        pending.completion.failedWithUnknownProgress();
        pending.completion.failed(3);
        pending.completion.succeeded();

        assertSame(error, write.failedError().orElseThrow(), "the first outcome stands");
        x.write(9);
        assertEquals(1L, x.resource.lastPosition(), "the cursor moved once, by the real prefix");
    }

    @Test
    void closeRacingAnUnexpectedFailureLeavesNothingPending() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue write = x.write(1, 2);
        Pending pending = x.resource.writes.pollFirst();
        assertTrue(pending.completion.commitFirstContribution());

        ProtosFutureValue closing = x.flow.close(x.activation);
        pending.completion.failedWithUnknownProgress();

        assertIoError(x, write);
        assertTrue(closing.state() != ProtosFutureValue.State.PENDING, "the File still closes");
    }

    private static void assertIoError(Fixture x, ProtosFutureValue future) {
        assertEquals(ProtosFutureValue.State.FAILED, future.state());
        assertSame(
                x.prelude.standardErrorPrototype("IOError"),
                future.failedError().orElseThrow().parent().orElseThrow());
    }

    private record Pending(long position, ProtosFileFlow.WriteCompletion completion) {}

    /** A writable resource whose completions the test delivers itself. */
    private static final class ControlledResource
            implements ProtosFileFlow.WritableResource,
                    ProtosFileFlow.SeekableResource,
                    ProtosFileFlow.SizedResource,
                    ProtosFileFlow.TruncatableResource,
                    ProtosFileFlow.SyncableResource {
        final ArrayDeque<Pending> writes = new ArrayDeque<>();
        final List<Long> positions = new ArrayList<>();

        @Override
        public synchronized ProtosFileFlow.Cancellation writeAt(
                long position, byte[] bytes, ProtosFileFlow.WriteCompletion completion) {
            positions.add(position);
            writes.addLast(new Pending(position, completion));
            return () -> {};
        }

        synchronized long lastPosition() {
            return positions.get(positions.size() - 1);
        }

        @Override
        public ProtosFileFlow.Cancellation endPosition(
                ProtosFileFlow.IntegerCompletion completion) {
            completion.succeeded(23L);
            return () -> {};
        }

        @Override
        public ProtosFileFlow.Cancellation size(
                ProtosFileFlow.IntegerCompletion completion) {
            completion.succeeded(23L);
            return () -> {};
        }

        @Override
        public ProtosFileFlow.Cancellation truncate(
                long size, ProtosFileFlow.ChangeCompletion completion) {
            if (completion.commitChange()) {
                completion.succeeded();
            } else {
                completion.failed();
            }
            return () -> {};
        }

        @Override
        public ProtosFileFlow.Cancellation sync(
                ProtosFileFlow.SyncCompletion completion) {
            if (completion.commitDurability()) {
                completion.succeeded();
            } else {
                completion.failed();
            }
            return () -> {};
        }

        @Override
        public void close(ProtosFileFlow.CloseCompletion completion) {
            completion.succeeded();
        }
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation,
            ControlledResource resource,
            ProtosFileFlow flow) {
        static Fixture create() throws Exception {
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
            ProtosActivation activation = prelude.newModuleActivation();
            ControlledResource resource = new ControlledResource();
            ProtosFileFlow flow =
                    new ProtosFileFlow(
                            new ProtosObjectValue(ProtosObjectValue.rootObject()),
                            new ProtosObjectValue(ProtosObjectValue.rootObject()),
                            activation,
                            resource,
                            new ProtosFileFlow.Capabilities(false, true, true, true, true, true));
            return new Fixture(prelude, activation, resource, flow);
        }

        ProtosFutureValue write(int... octets) {
            ProtosBytesValue bytes = new ProtosBytesValue(ProtosObjectValue.rootObject());
            for (int octet : octets) {
                bytes.indexedAdd(ProtosNumericValueSupport.octet(octet));
            }
            return flow.write(activation, bytes);
        }
    }
}

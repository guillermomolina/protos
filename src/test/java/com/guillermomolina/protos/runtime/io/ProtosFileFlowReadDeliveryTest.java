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
package com.guillermomolina.protos.runtime.io;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosFileFlow;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosHostCompletionThread;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * I156 / I158: File flow read publication from a marked host completion thread (the confined
 * Filesystem driver). The callback only snapshots inert host data; the owning domain's targeted
 * completion alone builds the Bytes result, moves the cursor and starts the next queued read.
 * Kept separate from {@code ProtosByteIoReadDeliveryTest}: the two flows are distinct.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
final class ProtosFileFlowReadDeliveryTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void aReusedBackendBufferCannotChangeThePublishedRead() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue read = x.flow.read(x.activation, new ProtosIntegerValue(4));
        ProtosFileFlow.ReadCompletion completion = x.resource.reads.pollFirst();
        byte[] buffer = {1, 2, 3};

        onHostCompletionThread(
                () -> {
                    completion.data(buffer);
                    // The backend reuses its buffer before the Actor admits the completion.
                    buffer[0] = 9;
                    buffer[1] = 9;
                    buffer[2] = 9;
                });

        assertEquals(ProtosFutureValue.State.PENDING, read.state(), "no guest value on the host thread");
        assertTrue(x.domain().dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime());
        assertEquals(List.of(1, 2, 3), bytes(read));
    }

    @Test
    void cancellationBeforeAdmissionPublishesNothingAndKeepsTheCursor() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue read = x.flow.read(x.activation, new ProtosIntegerValue(4));
        ProtosFileFlow.ReadCompletion completion = x.resource.reads.pollFirst();

        boolean cancelled = read.cancelRequest();
        onHostCompletionThread(() -> completion.data(new byte[] {1, 2}));
        while (x.domain().dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime()) {
            // Admit whatever the cancellation and the late callback published.
        }

        assertTrue(cancelled, "an uncommitted read is cancellable");
        assertEquals(ProtosFutureValue.State.CANCELLED, read.state());
        // The cancelled read moved nothing: the next read starts again at position 0.
        x.flow.read(x.activation, new ProtosIntegerValue(1));
        assertEquals(0L, x.resource.positions.get(x.resource.positions.size() - 1));
    }

    @Test
    void terminationBeforeAdmissionDiscardsTheReadWithoutGuestWork() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue read = x.flow.read(x.activation, new ProtosIntegerValue(4));
        ProtosFileFlow.ReadCompletion completion = x.resource.reads.pollFirst();

        x.domain().actorTerminationBegun();
        onHostCompletionThread(() -> completion.data(new byte[] {7}));

        assertFalse(x.domain().hasTargetedRuntimeCompletionForRuntime());
        assertTrue(read.state() != ProtosFutureValue.State.RESOLVED);
    }

    @Test
    void duplicateCallbacksHaveOneSemanticEffect() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue read = x.flow.read(x.activation, new ProtosIntegerValue(4));
        ProtosFileFlow.ReadCompletion completion = x.resource.reads.pollFirst();

        onHostCompletionThread(
                () -> {
                    completion.data(new byte[] {1, 2});
                    completion.data(new byte[] {3});
                    completion.eof();
                    completion.failed();
                });
        while (x.domain().dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime()) {
            // Every published duplicate is admitted; only the first may have an effect.
        }

        assertEquals(List.of(1, 2), bytes(read));
        x.flow.read(x.activation, new ProtosIntegerValue(1));
        assertEquals(2L, x.resource.positions.get(x.resource.positions.size() - 1), "cursor moved once");
    }

    @Test
    void readsStayInFifoOrderAndTheQueueAdvancesOnlyOnAdmission() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue first = x.flow.read(x.activation, new ProtosIntegerValue(2));
        ProtosFutureValue second = x.flow.read(x.activation, new ProtosIntegerValue(2));
        assertEquals(List.of(0L), x.resource.positions, "the second read waits for the first");

        ProtosFileFlow.ReadCompletion firstCompletion = x.resource.reads.pollFirst();
        onHostCompletionThread(() -> firstCompletion.data(new byte[] {1, 2}));
        assertEquals(List.of(0L), x.resource.positions, "no queue advance before admission");
        assertTrue(x.domain().dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime());
        assertEquals(List.of(1, 2), bytes(first));
        assertEquals(List.of(0L, 2L), x.resource.positions, "advanced exactly once");

        ProtosFileFlow.ReadCompletion secondCompletion = x.resource.reads.pollFirst();
        onHostCompletionThread(() -> secondCompletion.data(new byte[] {3}));
        assertTrue(x.domain().dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime());
        assertEquals(List.of(3), bytes(second));
    }

    private static void onHostCompletionThread(Runnable action) throws InterruptedException {
        Thread host =
                new Thread(
                        () -> {
                            ProtosHostCompletionThread.markCurrent();
                            action.run();
                        },
                        "i158-file-read-callback");
        host.start();
        host.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(host.isAlive());
    }

    private static List<Integer> bytes(ProtosFutureValue future) {
        ProtosBytesValue value = (ProtosBytesValue) future.resolvedValue().orElseThrow();
        ArrayList<Integer> result = new ArrayList<>();
        for (Object octet : value.indexedSnapshot()) {
            result.add(Integer.parseInt(ProtosNumericValueSupport.integerDecimalText(octet)));
        }
        return result;
    }

    /** A readable resource whose completions the test delivers itself. */
    private static final class ControlledResource implements ProtosFileFlow.ReadableResource {
        final ArrayDeque<ProtosFileFlow.ReadCompletion> reads = new ArrayDeque<>();
        final List<Long> positions = new ArrayList<>();

        @Override
        public synchronized ProtosFileFlow.Cancellation readAt(
                long position, int maxBytes, ProtosFileFlow.ReadCompletion completion) {
            positions.add(position);
            reads.addLast(completion);
            return () -> {};
        }

        @Override
        public void close(ProtosFileFlow.CloseCompletion completion) {
            completion.succeeded();
        }
    }

    private record Fixture(
            ProtosActivation activation, ControlledResource resource, ProtosFileFlow flow) {
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
                            new ProtosFileFlow.Capabilities(true, false, false, false, false, false));
            return new Fixture(activation, resource, flow);
        }

        ProtosActorExecutionDomain domain() {
            return activation.executionDomain();
        }
    }
}

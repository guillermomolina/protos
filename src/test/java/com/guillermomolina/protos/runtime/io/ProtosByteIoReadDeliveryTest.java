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
import com.guillermomolina.protos.runtime.ProtosByteIoFlow;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
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
 * I158: {@code ProtosByteIoFlow} read publication. A callback inside the active backend
 * {@code read()} call is synchronous; a later callback from another thread carries only inert host
 * data to one targeted completion of the owning domain, which alone builds the guest result.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
final class ProtosByteIoReadDeliveryTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void callbackInsideTheBackendCallKeepsItsSynchronousBehavior() throws Exception {
        Fixture x = Fixture.create();
        x.backend.inline = new byte[] {5};

        ProtosFutureValue read = x.flow.read(x.activation, Long.valueOf(4));

        assertEquals(ProtosFutureValue.State.RESOLVED, read.state());
        assertFalse(x.domain().hasTargetedRuntimeCompletionForRuntime());
        assertEquals(List.of(5), bytes(read));
    }

    @Test
    void duplicateLateCallbacksHaveOneSemanticEffect() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue read = x.flow.read(x.activation, Long.valueOf(4));
        ProtosByteIoFlow.ReadCompletion completion = x.backend.reads.pollFirst();

        onOtherThread(
                () -> {
                    completion.data(new byte[] {1, 2});
                    completion.data(new byte[] {3});
                    completion.eof();
                    completion.failed();
                });

        // Published, not yet admitted: no guest value exists yet.
        assertEquals(ProtosFutureValue.State.PENDING, read.state());
        assertTrue(x.domain().dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime());
        assertEquals(ProtosFutureValue.State.RESOLVED, read.state());
        assertEquals(List.of(1, 2), bytes(read));
        assertFalse(x.domain().dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime());
    }

    @Test
    void readOrderIsKeptAndTheQueueAdvancesOnce() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue first = x.flow.read(x.activation, Long.valueOf(1));
        ProtosFutureValue second = x.flow.read(x.activation, Long.valueOf(1));
        assertEquals(1, x.backend.started, "the second read waits for the first");

        ProtosByteIoFlow.ReadCompletion firstCompletion = x.backend.reads.pollFirst();
        onOtherThread(() -> firstCompletion.data(new byte[] {1}));
        assertEquals(1, x.backend.started, "no queue advance before domain admission");
        assertTrue(x.domain().dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime());
        assertEquals(List.of(1), bytes(first));
        assertEquals(2, x.backend.started, "the queue advanced exactly once");

        ProtosByteIoFlow.ReadCompletion secondCompletion = x.backend.reads.pollFirst();
        onOtherThread(() -> secondCompletion.data(new byte[] {2}));
        assertTrue(x.domain().dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime());
        assertEquals(List.of(2), bytes(second));
        assertEquals(2, x.backend.started);
    }

    @Test
    void aCompletionAfterTerminationBeganIsDiscardedWithoutGuestWork() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue read = x.flow.read(x.activation, Long.valueOf(4));
        ProtosByteIoFlow.ReadCompletion completion = x.backend.reads.pollFirst();

        x.domain().actorTerminationBegun();
        onOtherThread(() -> completion.data(new byte[] {9}));

        assertFalse(x.domain().hasTargetedRuntimeCompletionForRuntime());
        assertTrue(read.state() != ProtosFutureValue.State.RESOLVED);
    }

    @Test
    void aReusedBackendBufferCannotChangeThePublishedRead() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue read = x.flow.read(x.activation, Long.valueOf(4));
        ProtosByteIoFlow.ReadCompletion completion = x.backend.reads.pollFirst();
        byte[] buffer = {1, 2, 3};

        onOtherThread(
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
    void cancellationBeforeAdmissionKeepsTheBytesForTheNextRead() throws Exception {
        Fixture x = Fixture.create();
        ProtosFutureValue read = x.flow.read(x.activation, Long.valueOf(4));
        ProtosByteIoFlow.ReadCompletion completion = x.backend.reads.pollFirst();

        assertTrue(read.cancelRequest(), "an uncommitted read is cancellable");
        onOtherThread(() -> completion.data(new byte[] {4, 5}));
        while (x.domain().dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime()) {
            // Admit whatever the cancellation and the late callback published.
        }

        assertEquals(ProtosFutureValue.State.CANCELLED, read.state());
        // The uncommitted bytes are not lost: the next read observes them first.
        ProtosFutureValue next = x.flow.read(x.activation, Long.valueOf(4));
        assertEquals(ProtosFutureValue.State.RESOLVED, next.state());
        assertEquals(List.of(4, 5), bytes(next));
    }

    private static void onOtherThread(Runnable action) throws InterruptedException {
        Thread host = new Thread(action, "i158-host-read-callback");
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

    private static final class ControlledBackend implements ProtosByteIoFlow.Backend {
        final ArrayDeque<ProtosByteIoFlow.ReadCompletion> reads = new ArrayDeque<>();
        volatile byte[] inline;
        int started;

        @Override
        public synchronized ProtosByteIoFlow.Cancellation read(
                int maxBytes, ProtosByteIoFlow.ReadCompletion completion) {
            started++;
            if (inline != null) {
                completion.data(inline);
            } else {
                reads.addLast(completion);
            }
            return () -> {};
        }

        @Override
        public ProtosByteIoFlow.Cancellation write(
                byte[] bytes, ProtosByteIoFlow.WriteCompletion completion) {
            completion.succeeded();
            return () -> {};
        }
    }

    private record Fixture(
            ProtosActivation activation, ControlledBackend backend, ProtosByteIoFlow flow) {
        static Fixture create() throws Exception {
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
            ProtosActivation activation = prelude.newModuleActivation();
            ControlledBackend backend = new ControlledBackend();
            ProtosByteIoFlow flow =
                    new ProtosByteIoFlow(
                            new ProtosObjectValue(ProtosObjectValue.rootObject()),
                            new ProtosObjectValue(ProtosObjectValue.rootObject()),
                            activation,
                            backend);
            return new Fixture(activation, backend, flow);
        }

        ProtosActorExecutionDomain domain() {
            return activation.executionDomain();
        }
    }
}

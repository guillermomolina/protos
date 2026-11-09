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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * I160: Actor-domain publication and discard boundaries.
 *
 * <p>These tests use the exact targeted completion machinery used
 * by the private KDF facility. They are intentionally independent
 * of Argon2 allocation and host worker timing.
 */
final class ProtosPasswordFutureHandoffTest {

    private static ProtosPrelude prelude;

    @BeforeAll
    static void bootstrap() throws IOException {
        prelude = new ProtosCoreBootstrap().bootstrap(
                Path.of("protos", "lib", "core"),
                new ProtosStandardLibraryModuleResolver(
                        Path.of("protos", "lib")));
    }

    private static ProtosFutureValue future(
            ProtosActivation activation) {

        ProtosActorExecutionDomain domain =
                activation.executionDomain();

        ProtosFutureValue future = new ProtosFutureValue(
                prelude.futurePrototype(), domain);

        domain.registerActorNonTaskFutureForRuntime(future);
        return future;
    }

    @Test
    void cancellationBeforeDispatchPreventsLateKdfPublication() {
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosActorExecutionDomain domain =
                activation.executionDomain();

        ProtosFutureValue pending = future(activation);

        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger attemptedPublications = new AtomicInteger();
        AtomicInteger successfulPublications = new AtomicInteger();

        pending.attachCancellationProducer(() -> {
            cancelled.set(true);
            pending.cancelTerminal();
        });

        domain.enqueueTargetedFutureCompletionForRuntime(
                pending,
                () -> {
                    attemptedPublications.incrementAndGet();

                    // Exactly the precommit gate used by the
                    // I160 password facility.
                    if (!pending.isPending()) {
                        return false;
                    }

                    boolean published = pending.resolve(
                            new ProtosStringValue("not-published"),
                            activation);

                    if (published) {
                        successfulPublications.incrementAndGet();
                    }

                    return published;
                });

        assertTrue(domain.hasTargetedRuntimeCompletionForRuntime());

        // Cancellation occurs after enqueue but before dispatch.
        assertTrue(pending.cancelRequest());
        assertTrue(cancelled.get());

        domain.dispatchUntilIdle();

        assertEquals(
                ProtosFutureValue.State.CANCELLED,
                pending.state());

        assertEquals(1, attemptedPublications.get());
        assertEquals(0, successfulPublications.get());

        assertFalse(domain.hasTargetedRuntimeCompletionForRuntime());
    }

    @Test
    void actorTerminationDiscardsQueuedResultExactlyOnce() {
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosActorExecutionDomain domain =
                activation.executionDomain();

        ProtosFutureValue pending = future(activation);

        AtomicInteger published = new AtomicInteger();
        AtomicInteger discarded = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();

        pending.attachCancellationProducer(() -> {
            cancellations.incrementAndGet();
            pending.cancelTerminal();
        });

        domain.enqueueTargetedFutureCompletionForRuntime(
                pending,
                () -> {
                    published.incrementAndGet();
                    return pending.resolve(
                            new ProtosStringValue("late"), activation);
                },
                discarded::incrementAndGet);

        assertTrue(domain.hasTargetedRuntimeCompletionForRuntime());

        // Actor termination owns the pending Future and the
        // queued-result discard callback.
        domain.actorTerminationBegun();

        assertEquals(0, published.get());
        assertEquals(1, discarded.get());
        assertEquals(1, cancellations.get());

        assertEquals(
                ProtosFutureValue.State.CANCELLED,
                pending.state());

        assertFalse(domain.hasTargetedRuntimeCompletionForRuntime());

        // Repeated termination cannot publish or discard again.
        domain.actorTerminationBegun();
        domain.dispatchUntilIdle();

        assertEquals(0, published.get());
        assertEquals(1, discarded.get());
        assertEquals(1, cancellations.get());
    }

    @Test
    void ordinaryTargetedCompletionPublishesOnce() {
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosActorExecutionDomain domain =
                activation.executionDomain();

        ProtosFutureValue pending = future(activation);
        AtomicInteger publications = new AtomicInteger();

        domain.enqueueTargetedFutureCompletionForRuntime(
                pending,
                () -> {
                    publications.incrementAndGet();
                    return pending.resolve(
                            new ProtosStringValue("complete"),
                            activation);
                });

        assertTrue(pending.isPending());

        domain.dispatchUntilIdle();

        assertEquals(
                ProtosFutureValue.State.RESOLVED,
                pending.state());

        assertEquals(1, publications.get());

        domain.dispatchUntilIdle();

        assertEquals(1, publications.get());
    }
}

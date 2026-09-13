/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIoLifecycle;
import com.guillermomolina.protos.runtime.ProtosIoReleaseExecution;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ProtosPerf006C3FLifecycleReleaseCarrierTest {
    @Test
    void plat030ReleaseOwnedSuspensionIsABytecodeContinuationCarrier()
            throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        AtomicReference<ProtosIoLifecycle> lifecycleRef =
                new AtomicReference<>();
        AtomicReference<ProtosIoReleaseExecution> releaseRef =
                new AtomicReference<>();

        ProtosIoLifecycle lifecycle =
                new ProtosIoLifecycle(
                        receiver,
                        prelude.futurePrototype(),
                        activation.executionDomain(),
                        completion -> {
                            ProtosIoReleaseExecution release =
                                    lifecycleRef
                                            .get()
                                            .beginReleaseExecutionForRuntime(
                                                    activation,
                                                    completion);
                            releaseRef.set(release);
                        });
        lifecycleRef.set(lifecycle);

        ProtosFutureValue close =
                lifecycle.closeWithGuestReleaseForRuntime(activation);
        assertEquals(ProtosFutureValue.State.PENDING, close.state());

        ProtosIoReleaseExecution release = releaseRef.get();
        assertNotNull(release);

        ProtosIoReleaseSuspension suspension =
                ProtosIoReleaseSuspension.pending(
                        release,
                        () -> false,
                        () -> ProtosNullValue.INSTANCE);

        /*
         * PERF006-C3F regression:
         *
         * ProtosIoReleaseCPrimeExecution feeds the result of
         * AwaitIoReleaseTargetFuture into the generic IsContinuation loop.
         * A pending PLAT030 wait is a ProtosIoReleaseSuspension. Omitting that
         * carrier made the loop fall through directly to
         * ApplyIoReleaseTargetOutcome, which then rejected the suspension as
         * "lifecycle release C-prime lower wait produced an invalid carrier".
         */
        assertTrue(
                ProtosBytecodeRootNode.IsContinuation.perform(suspension),
                "PLAT030 release-owned suspension must enter Bytecode yield/resume");
        assertSame(release, suspension.release());

        assertTrue(release.succeed());
        assertEquals(ProtosFutureValue.State.RESOLVED, close.state());
        assertSame(receiver, close.resolvedValue().orElseThrow());
    }
}

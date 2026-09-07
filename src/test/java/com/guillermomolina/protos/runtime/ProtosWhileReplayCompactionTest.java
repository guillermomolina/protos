/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosEvaluatorBridge;
import com.guillermomolina.protos.execution.ProtosSourceCompiler;
import com.oracle.truffle.api.CallTarget;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosWhileReplayCompactionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final class ControlledDependency implements ProtosTask.WaitDependency {
        void complete(ProtosTask task) {
            assertTrue(task.resume(this));
        }
    }

    @Test
    void completedWhileIterationsDoNotGrowRetainedReplayState() throws Exception {
        RetainedState shortRun = suspendAfterCompletedLoop(8);
        RetainedState longRun = suspendAfterCompletedLoop(4096);

        assertEquals(
                shortRun.eventCount(),
                longRun.eventCount(),
                "retained evaluator events must be independent of completed iteration count");
        assertEquals(
                shortRun.activationCount(),
                longRun.activationCount(),
                "retained invocation activations must be independent of completed iteration count");
        assertTrue(longRun.eventCount() < 256, "while replay tape must remain bounded");
        assertTrue(longRun.activationCount() < 64, "while invocation replay state must remain bounded");
    }

    @Test
    void suspendingConditionAndBodyIterationsDoNotGrowRetainedReplayState() throws Exception {
        RetainedState shortRun = suspendInsideConditionAfterSuspendingIterations(8);
        RetainedState longRun = suspendInsideConditionAfterSuspendingIterations(1024);

        assertEquals(
                shortRun.eventCount(),
                longRun.eventCount(),
                "retained evaluator events must be independent of completed suspending iteration count");
        assertEquals(
                shortRun.activationCount(),
                longRun.activationCount(),
                "retained invocation activations must be independent of completed suspending iteration count");
        assertTrue(longRun.eventCount() < 256, "suspending while replay tape must remain bounded");
        assertTrue(
                longRun.activationCount() < 64,
                "suspending while invocation replay state must remain bounded");
    }

    private static RetainedState suspendAfterCompletedLoop(int iterations) throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ControlledDependency dependency = new ControlledDependency();
        activation.context().createLocalSlot(
                "pause",
                ProtosClosureValue.nativeClosure(
                        (callActivation, supplied) -> {
                            assertTrue(supplied.isEmpty());
                            ProtosEvaluatorBridge.await(callActivation, dependency);
                            return ProtosNullValue.INSTANCE;
                        }));

        String source =
                "i: 0\n"
                        + "(() => { i < "
                        + iterations
                        + " }).while() {\n"
                        + "    i = i + 1\n"
                        + "}\n"
                        + "pause()\n"
                        + "i";
        CallTarget target = new ProtosSourceCompiler().compile(source);
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosTask task =
                domain.createTask(
                        null,
                        current -> current.executeProtos(target, activation));

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.SUSPENDED, task.state());
        RetainedState retained =
                new RetainedState(
                        task.evaluatorContinuation().retainedEventCount(),
                        task.evaluatorContinuation().retainedInvocationActivationCount());

        dependency.complete(task);
        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.COMPLETED, task.state());
        ProtosIntegerValue result =
                assertInstanceOf(ProtosIntegerValue.class, task.result().orElseThrow());
        assertEquals(BigInteger.valueOf(iterations), result.value());
        return retained;
    }

    private static RetainedState suspendInsideConditionAfterSuspendingIterations(int iterations)
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ControlledDependency dependency = new ControlledDependency();
        activation.context().createLocalSlot(
                "pause",
                ProtosClosureValue.nativeClosure(
                        (callActivation, supplied) -> {
                            assertTrue(supplied.isEmpty());
                            ProtosEvaluatorBridge.await(callActivation, dependency);
                            return ProtosNullValue.INSTANCE;
                        }));

        String source =
                "i: 0\n"
                        + "(() => {\n"
                        + "    pause()\n"
                        + "    i < "
                        + iterations
                        + "\n"
                        + "}).while() {\n"
                        + "    pause()\n"
                        + "    i = i + 1\n"
                        + "}\n"
                        + "i";
        CallTarget target = new ProtosSourceCompiler().compile(source);
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ProtosTask task =
                domain.createTask(
                        null,
                        current -> current.executeProtos(target, activation));

        int suspensionCount = iterations * 2 + 1;
        RetainedState retained = null;
        for (int suspension = 0; suspension < suspensionCount; suspension++) {
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());

            if (suspension == suspensionCount - 1) {
                retained =
                        new RetainedState(
                                task.evaluatorContinuation().retainedEventCount(),
                                task.evaluatorContinuation().retainedInvocationActivationCount());
            }

            dependency.complete(task);
        }

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.COMPLETED, task.state());
        ProtosIntegerValue result =
                assertInstanceOf(ProtosIntegerValue.class, task.result().orElseThrow());
        assertEquals(BigInteger.valueOf(iterations), result.value());
        return retained;
    }

    private record RetainedState(int eventCount, int activationCount) {}
}

/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosTestPrelude;
import com.oracle.truffle.api.CallTarget;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ProtosEvaluatorSuspensionBridgeTest {
    private static final class ControlledDependency implements ProtosTask.WaitDependency {
        private final AtomicInteger waiterCleanup = new AtomicInteger();
        private Object value;
        @Override public void waitingTaskCancelled(ProtosTask task) { waiterCleanup.incrementAndGet(); }
        void complete(ProtosTask task, Object value) { this.value = value; assertTrue(task.resume(this)); }
        Object value() { return value; }
        int waiterCleanup() { return waiterCleanup.get(); }
    }

    @BeforeAll
    static void installCallProtocol() { ProtosStandardObjectProtocol.install(); }

    @Test
    void realProtosExecutionSuspendsLetsPeerProgressAndResumesWithoutRepeatingEffects() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ControlledDependency dependency = new ControlledDependency();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        AtomicInteger peer = new AtomicInteger();
        Object resumed = new ProtosObjectValue(ProtosObjectValue.rootObject());
        Harness harness = harness(
                "first()\nx: pause()\nsecond()\nx",
                dependency, first, second);

        ProtosTask a = task(domain, harness);
        ProtosTask b = domain.createTask(null, task -> { peer.incrementAndGet(); task.complete("peer"); });

        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.SUSPENDED, a.state());
        assertEquals(1, first.get());
        assertEquals(0, second.get());

        assertTrue(domain.dispatchOne());
        assertEquals(1, peer.get());
        assertEquals(ProtosTask.State.COMPLETED, b.state());

        dependency.complete(a, resumed);
        assertTrue(domain.dispatchOne());
        assertEquals(ProtosTask.State.COMPLETED, a.state());
        assertSame(resumed, a.result().orElseThrow());
        assertEquals(1, first.get(), "pre-suspension side effect must not replay");
        assertEquals(1, second.get(), "post-resume side effect executes exactly once");
    }

    @Test
    void nestedClosureAndExpressionResumeAtExactPoint() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ControlledDependency dependency = new ControlledDependency();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        Harness harness = harness(
                "outer: () => { first(); first() === pause(); second(); ^pauseValue }; outer()",
                dependency, first, second);
        Object resumed = harness.pauseValue;
        ProtosTask task = task(domain, harness);

        domain.dispatchOne();
        assertEquals(ProtosTask.State.SUSPENDED, task.state());
        assertEquals(2, first.get());
        dependency.complete(task, resumed);
        domain.dispatchOne();

        assertEquals(ProtosTask.State.COMPLETED, task.state());
        assertSame(resumed, task.result().orElseThrow());
        assertEquals(2, first.get(), "nested receiver/left operand effects must not replay");
        assertEquals(1, second.get());
    }

    @Test
    void cancellingSuspendedEvaluationUnwindsWithoutWaitingOrDoubleResume() {
        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        ControlledDependency dependency = new ControlledDependency();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        Harness harness = harness("first()\npause()\nsecond()", dependency, first, second);
        ProtosTask task = task(domain, harness);

        domain.dispatchOne();
        assertEquals(ProtosTask.State.SUSPENDED, task.state());
        assertTrue(task.requestCancellation());
        assertEquals(ProtosTask.State.RUNNABLE, task.state());
        assertTrue(task.waitDependency().isEmpty());
        assertEquals(1, dependency.waiterCleanup());

        domain.dispatchOne();
        assertEquals(ProtosTask.State.CANCELLED, task.state());
        assertEquals(1, first.get());
        assertEquals(0, second.get());
        assertFalse(task.resume(dependency), "dependency completion after cancellation must not resume again");
        assertTrue(task.waitDependency().isEmpty());
    }

    private static ProtosTask task(ProtosActorExecutionDomain domain, Harness harness) {
        AtomicReference<ProtosTask> ref = new AtomicReference<>();
        ProtosTask task = domain.createTask(null, current -> current.executeProtos(harness.target, harness.activation));
        ref.set(task);
        return task;
    }

    private static Harness harness(
            String source,
            ControlledDependency dependency,
            AtomicInteger first,
            AtomicInteger second) {
        ProtosObjectValue context = new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object pauseValue = new ProtosObjectValue(ProtosObjectValue.rootObject());
        context.createLocalSlot("pauseValue", pauseValue);
        context.createLocalSlot("first", ProtosClosureValue.nativeClosure((activation, supplied) -> {
            assertTrue(supplied.isEmpty()); first.incrementAndGet(); return pauseValue;
        }));
        context.createLocalSlot("second", ProtosClosureValue.nativeClosure((activation, supplied) -> {
            assertTrue(supplied.isEmpty()); second.incrementAndGet(); return pauseValue;
        }));
        context.createLocalSlot("pause", ProtosClosureValue.nativeClosure((activation, supplied) -> {
            assertTrue(supplied.isEmpty());
            ProtosEvaluatorBridge.await(activation, dependency);
            return dependency.value();
        }));
        ProtosActivation activation = ProtosTestPrelude.activation(context, List.of(), context);
        CallTarget target = new ProtosSourceCompiler().compile(source);
        return new Harness(target, activation, pauseValue);
    }

    private record Harness(CallTarget target, ProtosActivation activation, Object pauseValue) {}
}

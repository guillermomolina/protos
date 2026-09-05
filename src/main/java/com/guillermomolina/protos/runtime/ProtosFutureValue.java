/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import com.guillermomolina.protos.execution.ProtosEvaluatorBridge;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Actor-domain Future state machine. Terminal state and waiter registration share one monitor. */
public final class ProtosFutureValue extends ProtosObjectValue {
    public enum State { PENDING, RESOLVED, FAILED, CANCELLED }

    @FunctionalInterface
    public interface Observer { void terminal(ProtosFutureValue future); }

    /** Producer-side cancellation bridge for pending work that is not backed by a Task. */
    @FunctionalInterface
    public interface CancellationProducer { void cancellationRequested(); }

    private final ProtosActorExecutionDomain domain;
    private State state = State.PENDING;
    private Object value;
    private ProtosObjectValue error;
    private ProtosTask producerTask;
    private ProtosActivation producerActivation;
    private CancellationProducer cancellationProducer;
    private boolean detached;
    private ProtosFutureValue adoptedSource;
    private Observer adoptedObserver;
    private final List<Waiter> waiters = new ArrayList<>();
    private final List<Observer> observers = new ArrayList<>();

    public ProtosFutureValue(ProtosObjectValue futurePrototype, ProtosActorExecutionDomain domain) {
        super(Objects.requireNonNull(futurePrototype, "futurePrototype"));
        this.domain = Objects.requireNonNull(domain, "domain");
    }

    public ProtosActorExecutionDomain domain() { return domain; }
    public synchronized State state() { return state; }
    public synchronized boolean isPending() { return state == State.PENDING; }
    public synchronized Optional<Object> resolvedValue() { return state == State.RESOLVED ? Optional.of(value) : Optional.empty(); }
    public synchronized Optional<ProtosObjectValue> failedError() { return state == State.FAILED ? Optional.of(error) : Optional.empty(); }
    public synchronized Optional<ProtosTask> producerTask() { return Optional.ofNullable(producerTask); }
    public synchronized boolean detached() { return detached; }

    public synchronized void attachProducerTask(ProtosTask task, ProtosActivation activation) {
        Objects.requireNonNull(task, "task");
        if (task.owner() != domain) throw new IllegalArgumentException("Future producer belongs to another Actor domain");
        if (producerTask != null && producerTask != task) throw new IllegalStateException("Future already has a producer task");
        producerTask = task;
        producerActivation = Objects.requireNonNull(activation, "activation");
    }

    public synchronized void attachCancellationProducer(CancellationProducer producer) {
        Objects.requireNonNull(producer, "producer");
        if (producerTask != null) throw new IllegalStateException("Task-backed Future already owns cancellation");
        if (cancellationProducer != null && cancellationProducer != producer)
            throw new IllegalStateException("Future already has a cancellation producer");
        cancellationProducer = producer;
    }

    public synchronized ProtosActivation producerActivation() {
        if (producerActivation == null) throw new IllegalStateException("Future has no producer activation");
        return producerActivation;
    }

    /** First-terminal-wins ordinary-value resolution, including canonical semantic null. */
    public boolean resolve(Object result, ProtosActivation activation) {
        Objects.requireNonNull(result, "result");
        if (result instanceof ProtosFutureValue source) return adopt(source, activation);
        return transition(State.RESOLVED, result, null);
    }

    public boolean resolveWithCommit(Object result,ProtosActivation activation,Runnable commit) {
        Objects.requireNonNull(result);Objects.requireNonNull(activation);Objects.requireNonNull(commit);
        if(result instanceof ProtosFutureValue)throw new IllegalArgumentException("commit result cannot adopt");
        List<Waiter>wake;List<Observer>notify;
        synchronized(this){if(state!=State.PENDING)return false;commit.run();state=State.RESOLVED;value=result;error=null;wake=List.copyOf(waiters);waiters.clear();notify=List.copyOf(observers);observers.clear();}
        for(Waiter w:wake)w.ready();for(Observer o:notify)o.terminal(this);return true;
    }

    public boolean fail(ProtosObjectValue failure) {
        return transition(State.FAILED, null, Objects.requireNonNull(failure, "failure"));
    }

    public boolean cancelRequest() {
        ProtosTask producer;
        CancellationProducer cancellation;
        synchronized (this) {
            if (state != State.PENDING) return false;
            producer = producerTask;
            cancellation = cancellationProducer;
        }
        if (producer != null) {
            producer.requestCancellation();
            return true;
        }
        if (cancellation != null) {
            cancellation.cancellationRequested();
            return true;
        }
        return transition(State.CANCELLED, null, null);
    }

    public boolean cancelTerminal() { return transition(State.CANCELLED, null, null); }

    public ProtosFutureValue detach() {
        ProtosTask producer;
        synchronized (this) {
            if (detached || state != State.PENDING) return this;
            detached = true;
            producer = producerTask;
        }
        if (producer != null) producer.detachFromParent();
        return this;
    }

    public Object observeValue(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        requireDomain(activation);
        while (true) {
            State snapshot;
            Object resolved;
            ProtosObjectValue failed;
            synchronized (this) {
                snapshot = state;
                resolved = value;
                failed = error;
                if (snapshot == State.PENDING) {
                    ProtosTask task = activation.task().orElseThrow(
                            () -> new IllegalStateException("pending Future.value() requires an Actor-local task execution"));
                    Waiter waiter = new Waiter(this, task);
                    waiters.add(waiter);
                    ProtosEvaluatorBridge.await(activation, waiter);
                    // await either suspended (control unwind) or observed a terminal-ready waiter.
                    waiters.remove(waiter);
                    continue;
                }
            }
            return switch (snapshot) {
                case RESOLVED -> resolved;
                case FAILED -> throw ProtosCoreErrors.signal(activation, failed);
                case CANCELLED -> throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newOccurrence(activation, ProtosCoreErrors.StandardError.CANCELLED));
                case PENDING -> throw new AssertionError("pending handled above");
            };
        }
    }

    public void observe(Observer observer) {
        Objects.requireNonNull(observer, "observer");
        boolean callNow;
        synchronized (this) {
            callNow = state != State.PENDING;
            if (!callNow) observers.add(observer);
        }
        if (callNow) observer.terminal(this);
    }

    public synchronized void removeObserver(Observer observer) { observers.remove(observer); }

    public void requireDomain(ProtosActivation activation) {
        if (activation.executionDomain() != domain) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
    }

    private boolean adopt(ProtosFutureValue source, ProtosActivation activation) {
        Objects.requireNonNull(source, "source");
        requireDomain(activation);
        if (source.domain != domain) throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        if (source == this || source.adoptionReaches(this)) {
            return fail(ProtosCoreErrors.newOccurrence(activation, ProtosCoreErrors.StandardError.FUTURE_RESOLUTION_CYCLE));
        }
        synchronized (this) {
            if (state != State.PENDING) return false;
            adoptedSource = source;
        }
        Observer observer = ignored -> mirrorAdopted(source);
        synchronized (this) { if (state == State.PENDING) adoptedObserver = observer; }
        source.observe(observer);
        synchronized (this) {
            if (state != State.PENDING) source.removeObserver(observer);
        }
        return true;
    }

    private boolean adoptionReaches(ProtosFutureValue target) {
        Map<ProtosFutureValue, Boolean> seen = new IdentityHashMap<>();
        ProtosFutureValue current = this;
        while (current != null && seen.put(current, Boolean.TRUE) == null) {
            if (current == target) return true;
            synchronized (current) { current = current.adoptedSource; }
        }
        return false;
    }

    private void mirrorAdopted(ProtosFutureValue source) {
        State s; Object v; ProtosObjectValue e;
        synchronized (source) { s=source.state; v=source.value; e=source.error; }
        switch (s) {
            case RESOLVED -> transition(State.RESOLVED, v, null);
            case FAILED -> transition(State.FAILED, null, e);
            case CANCELLED -> transition(State.CANCELLED, null, null);
            case PENDING -> { }
        }
    }

    private boolean transition(State terminal, Object resolved, ProtosObjectValue failed) {
        List<Waiter> wake;
        List<Observer> notify;
        ProtosFutureValue adoptionSource;
        Observer adoptionObserver;
        synchronized (this) {
            if (state != State.PENDING) return false;
            state = terminal;
            value = resolved;
            error = failed;
            adoptionSource = adoptedSource;
            adoptionObserver = adoptedObserver;
            adoptedSource = null;
            adoptedObserver = null;
            wake = List.copyOf(waiters);
            waiters.clear();
            notify = List.copyOf(observers);
            observers.clear();
        }
        if (adoptionSource != null && adoptionObserver != null) adoptionSource.removeObserver(adoptionObserver);
        for (Waiter waiter : wake) waiter.ready();
        for (Observer observer : notify) observer.terminal(this);
        return true;
    }

    private static final class Waiter implements ProtosTask.WaitDependency {
        private final ProtosFutureValue future;
        private final ProtosTask task;
        private volatile boolean ready;
        private Waiter(ProtosFutureValue future, ProtosTask task) { this.future=future; this.task=task; }
        @Override public boolean isReady() { return ready; }
        void ready() { ready=true; task.resume(this); }
        @Override public void waitingTaskCancelled(ProtosTask cancelled) {
            synchronized (future) { future.waiters.remove(this); }
        }
    }
}

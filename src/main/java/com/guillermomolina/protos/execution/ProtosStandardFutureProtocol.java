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

import com.guillermomolina.protos.runtime.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Ordinary Core Future protocol plus the standard Object.future Closure behavior. */
public final class ProtosStandardFutureProtocol {
    private ProtosStandardFutureProtocol() {}

    public static void install(ProtosObjectValue futurePrototype) {
        Objects.requireNonNull(futurePrototype, "futurePrototype");
        installObjectFuture();
        suspensionSlot(
                futurePrototype,
                "value",
                (a,x)->{ arity(a,x,0); return future(a).observeValue(a); },
                (a,x)->{
                    arity(a,x,0);
                    ProtosFutureValue observed=future(a);
                    if (a.deferredCPrimeOperationForRuntime().isPresent()) {
                        return observeValueForIoOperationContinuation(
                                a,
                                observed,
                                a.deferredCPrimeOperationForRuntime().orElseThrow());
                    }
                    return observed.observeValueForContinuationForRuntime(
                            a,
                            (dependency,resumer) ->
                                    ProtosNativeSuspension.pending(
                                            dependency,
                                            resumer));
                });
        slot(futurePrototype, "cancel", (a,x)->{ arity(a,x,0); ProtosFutureValue f=future(a); f.cancelRequest(); return f; });
        slot(futurePrototype, "detach", (a,x)->{ arity(a,x,0); return future(a).detach(); });
        slot(futurePrototype, "then", (a,x)->then(a,x,futurePrototype));
        slot(futurePrototype, "all", (a,x)->all(a,x,futurePrototype));
    }

    static void installObjectFuture() {
        ProtosObjectValue object = ProtosObjectValue.rootObject();
        if (object.hasLocalSlot("future")) return;
        object.createLocalSlot("future", ProtosClosureValue.nativeClosure((activation,supplied)->{
            arity(activation,supplied,0);
            if (!(activation.receiver() instanceof ProtosClosureValue closure)) throw error(activation);
            ProtosActorExecutionDomain domain=activation.executionDomain();
            ProtosObjectValue futurePrototype=activation.prelude().orElseThrow().futurePrototype();
            ProtosFutureValue result=new ProtosFutureValue(futurePrototype,domain);
            ProtosTask parent=activation.task().orElse(null);
            ProtosTask task=domain.createTask(parent,result,current ->
                    ProtosClosureInvoker.executeInTaskForRuntime(
                            closure,List.of(),activation,current));
            result.attachProducerTask(task, activation);
            return result;
        }));
    }

    private static Object then(ProtosActivation activation,List<?> supplied,ProtosObjectValue futurePrototype) {
        arity(activation,supplied,1);
        ProtosFutureValue source=future(activation);
        Object transform=supplied.get(0);
        requireInvokable(transform,activation);
        ProtosFutureValue destination=new ProtosFutureValue(futurePrototype,activation.executionDomain());
        ProtosTask parent=activation.task().orElse(null);
        final ProtosFutureValue.Observer[] observation=new ProtosFutureValue.Observer[1];
        ProtosTask task=activation.executionDomain().createTask(parent,destination,current->{
            if(source.isPending()) {
                SourceDependency dep=new SourceDependency(source,current);
                observation[0]=dep;
                source.observe(dep);
                if(source.isPending()) {
                    /*
                     * The source may terminalize after the pending check/observer registration
                     * but before suspend(). SourceDependency then becomes ready while the Task
                     * is still RUNNING. ProtosTask.suspend() deliberately returns false for that
                     * ready-before-suspend race and leaves the Task RUNNING; only a true
                     * suspension may return from this continuation here.
                     */
                    if(current.suspend(dep)) return;
                    source.removeObserver(dep);
                    if(current.state()!=ProtosTask.State.RUNNING) return;
                } else {
                    source.removeObserver(dep);
                }
            }
            switch(source.state()) {
                case RESOLVED -> ProtosInvocation.executeInTaskForRuntime(
                        transform,List.of(source.resolvedValue().orElseThrow()),activation,current);
                case FAILED -> current.fail(source.failedError().orElseThrow());
                case CANCELLED -> { current.requestCancellation(); current.observeCancellation(); }
                case PENDING -> throw new IllegalStateException("continuation resumed before source terminal");
            }
        });
        destination.attachProducerTask(task, activation);
        return destination;
    }

    private static Object all(ProtosActivation activation,List<?> supplied,ProtosObjectValue futurePrototype) {
        if (activation.receiver() != futurePrototype) throw error(activation);
        ArrayList<ProtosFutureValue> sources=new ArrayList<>(supplied.size());
        for(Object value:supplied) {
            if(!(value instanceof ProtosFutureValue f) || f.domain()!=activation.executionDomain()) throw error(activation);
            sources.add(f);
        }
        ProtosFutureValue aggregate=new ProtosFutureValue(futurePrototype,activation.executionDomain());
        if(sources.isEmpty()) { aggregate.resolve(activation.prelude().orElseThrow().newArray(List.of()),activation); return aggregate; }
        AggregateObservation observation=new AggregateObservation(aggregate,sources,activation);
        observation.register();
        return aggregate;
    }


    private static Object observeValueForIoOperationContinuation(
            ProtosActivation activation,
            ProtosFutureValue observed,
            ProtosIoOperation operation) {
        return awaitFutureForIoOperationContinuationForRuntime(
                observed,
                operation,
                () -> observed.observeValue(activation));
    }

    static Object awaitFutureForIoOperationContinuationForRuntime(
            ProtosFutureValue observed,
            ProtosIoOperation operation,
            Supplier<Object> terminalProjection) {
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(terminalProjection, "terminalProjection");
        FutureOperationDependency dependency =
                new FutureOperationDependency(observed, operation);
        observed.observe(dependency);
        if (dependency.isReady()) {
            return Objects.requireNonNull(
                    terminalProjection.get(),
                    "operation-owned Future terminal projection returned null");
        }
        return ProtosIoOperationSuspension.pending(
                operation,
                dependency,
                () ->
                        Objects.requireNonNull(
                                terminalProjection.get(),
                                "operation-owned Future terminal projection returned null"));
    }

    private static final class FutureOperationDependency
            implements ProtosFutureValue.Observer, ProtosIoOperationSuspension.Dependency {
        private final ProtosFutureValue source;
        private final ProtosIoOperation operation;
        private boolean ready;
        private boolean retained;
        private boolean released;

        FutureOperationDependency(ProtosFutureValue source, ProtosIoOperation operation) {
            this.source = Objects.requireNonNull(source, "source");
            this.operation = Objects.requireNonNull(operation, "operation");
        }

        @Override
        public synchronized boolean isReady() {
            return ready;
        }

        @Override
        public void terminal(ProtosFutureValue terminalSource) {
            boolean schedule;
            synchronized (this) {
                if (terminalSource != source || released) {
                    return;
                }
                ready = true;
                schedule = retained;
            }
            if (schedule) {
                operation.requestDeferredCPrimeRunForRuntime();
            }
        }

        @Override
        public void waitingOperationRetained(ProtosIoOperation retainedOperation) {
            boolean schedule;
            synchronized (this) {
                requireOwner(retainedOperation);
                if (released) {
                    return;
                }
                retained = true;
                schedule = ready;
            }
            if (schedule) {
                operation.requestDeferredCPrimeRunForRuntime();
            }
        }

        @Override
        public void waitingOperationReleased(ProtosIoOperation releasedOperation) {
            synchronized (this) {
                requireOwner(releasedOperation);
                if (released) {
                    return;
                }
                released = true;
                retained = false;
            }
            source.removeObserver(this);
        }

        private void requireOwner(ProtosIoOperation candidate) {
            if (candidate != operation) {
                throw new IllegalArgumentException(
                        "Future.value operation waiter belongs to another I/O operation");
            }
        }
    }

    private static final class SourceDependency implements ProtosTask.WaitDependency, ProtosFutureValue.Observer {
        private final ProtosFutureValue source; private final ProtosTask task; private volatile boolean ready;
        SourceDependency(ProtosFutureValue source,ProtosTask task){this.source=source;this.task=task;}
        @Override public boolean isReady(){return ready;}
        @Override public void terminal(ProtosFutureValue ignored){ready=true;task.resume(this);}
        @Override public void waitingTaskCancelled(ProtosTask ignored){source.removeObserver(this);}
    }

    private static final class AggregateObservation implements ProtosFutureValue.Observer {
        private final ProtosFutureValue aggregate; private final List<ProtosFutureValue> sources; private final ProtosActivation activation;
        AggregateObservation(ProtosFutureValue aggregate,List<ProtosFutureValue> sources,ProtosActivation activation){this.aggregate=aggregate;this.sources=List.copyOf(sources);this.activation=activation;}
        void register(){ aggregate.observe(ignored -> cleanup()); for(ProtosFutureValue source:sources) source.observe(this); advance(); }
        @Override public void terminal(ProtosFutureValue ignored){ advance(); }
        private synchronized void advance(){
            if(!aggregate.isPending()){cleanup();return;}
            ArrayList<Object> values=new ArrayList<>(sources.size());
            for(ProtosFutureValue source:sources){
                switch(source.state()){
                    case PENDING -> { return; }
                    case RESOLVED -> values.add(source.resolvedValue().orElseThrow());
                    case FAILED -> { aggregate.fail(source.failedError().orElseThrow()); cleanup(); return; }
                    case CANCELLED -> { aggregate.cancelTerminal(); cleanup(); return; }
                }
            }
            aggregate.resolve(activation.prelude().orElseThrow().newArray(values),activation); cleanup();
        }
        private void cleanup(){for(ProtosFutureValue source:sources)source.removeObserver(this);}
    }

    private static ProtosFutureValue future(ProtosActivation activation){
        if(!(activation.receiver() instanceof ProtosFutureValue f)) throw error(activation);
        f.requireDomain(activation); return f;
    }
    private static void requireInvokable(Object candidate,ProtosActivation activation){
        try {
            var selected=ProtosValueLookup.lookup(candidate,"call",activation.prelude().orElseThrow()).orElseThrow(()->error(activation));
            if(!(selected.value() instanceof ProtosClosureValue)) throw error(activation);
        } catch(UnsupportedOperationException ex){throw error(activation);}
    }
    private static void arity(ProtosActivation a,List<?>x,int n){if(x.size()!=n)throw error(a);}
    private static ProtosSignalException error(ProtosActivation a){return new ProtosSignalException(ProtosCoreErrors.newError(a));}
    private static void suspensionSlot(
            ProtosObjectValue p,
            String name,
            ProtosNativeClosureBody ordinaryBody,
            ProtosNativeClosureBody continuationBody) {
        if(p.hasLocalSlot(name))throw new IllegalStateException("Core Future already defines "+name);
        p.createLocalSlot(
                name,
                ProtosClosureValue.suspensionCapableNativeClosure(
                        ordinaryBody,
                        continuationBody));
    }
    private static void slot(ProtosObjectValue p,String name,ProtosNativeClosureBody body){if(p.hasLocalSlot(name))throw new IllegalStateException("Core Future already defines "+name);p.createLocalSlot(name,ProtosClosureValue.nativeClosure(body));}
}

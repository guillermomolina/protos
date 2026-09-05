/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ordinary Core Future protocol plus the standard Object.future Closure behavior. */
public final class ProtosStandardFutureProtocol {
    private ProtosStandardFutureProtocol() {}

    public static void install(ProtosObjectValue futurePrototype) {
        Objects.requireNonNull(futurePrototype, "futurePrototype");
        installObjectFuture();
        slot(futurePrototype, "value", (a,x)->{ arity(a,x,0); return future(a).observeValue(a); });
        slot(futurePrototype, "cancel", (a,x)->{ arity(a,x,0); ProtosFutureValue f=future(a); f.cancelRequest(); return f; });
        slot(futurePrototype, "detach", (a,x)->{ arity(a,x,0); return future(a).detach(); });
        slot(futurePrototype, "then", (a,x)->then(a,x,futurePrototype));
        slot(futurePrototype, "all", (a,x)->all(a,x,futurePrototype));
    }

    private static void installObjectFuture() {
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
                    current.executeAction(() -> ProtosClosureInvoker.invokeInTask(closure,List.of(),activation,current)));
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
                if(source.isPending()) { current.suspend(dep); return; }
                source.removeObserver(dep);
            }
            switch(source.state()) {
                case RESOLVED -> current.executeAction(() -> ProtosInvocation.invoke(transform,List.of(source.resolvedValue().orElseThrow()),activation));
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
    private static void slot(ProtosObjectValue p,String name,ProtosNativeClosureBody body){if(p.hasLocalSlot(name))throw new IllegalStateException("Core Future already defines "+name);p.createLocalSlot(name,ProtosClosureValue.nativeClosure(body));}
}

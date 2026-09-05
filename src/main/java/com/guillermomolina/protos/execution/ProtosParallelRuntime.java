/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Process-local host machinery for I010 isolated P execution. */
public final class ProtosParallelRuntime {
    private static final int CARRIERS=Math.max(2,Integer.getInteger(
            "protos.parallel.carriers",Math.max(2,Runtime.getRuntime().availableProcessors())));
    private static final ThreadPoolExecutor EXECUTOR=new ThreadPoolExecutor(
            CARRIERS,CARRIERS,30L,TimeUnit.SECONDS,new LinkedBlockingQueue<>(),
            new ThreadFactory(){private final AtomicInteger n=new AtomicInteger();
                public Thread newThread(Runnable r){Thread t=new Thread(r,"protos-p-"+n.incrementAndGet());t.setDaemon(true);return t;}});
    private static final Set<ProtosActorExecutionDomain> P_DOMAINS=
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    static { EXECUTOR.allowCoreThreadTimeOut(true); }
    private ProtosParallelRuntime(){}

    public static int configuredCarrierLimit(){return CARRIERS;}
    public static int liveCarrierCountForTesting(){return EXECUTOR.getPoolSize();}

    public static void installObjectParallel(){
        ProtosObjectValue object=ProtosObjectValue.rootObject();
        if(object.hasLocalSlot("parallel"))return;
        object.createLocalSlot("parallel",ProtosClosureValue.nativeClosure((a,args)->{
            if(!(a.receiver() instanceof ProtosClosureValue closure))throw error(a);
            validateClosureArity(closure,args.size(),a);
            Snapshot snapshot=Snapshot.capture(closure,args,a);
            return ownedFuture(a,c->submit(()->run(snapshot,c)));
        }));
    }

    public static void installArrayParallel(ProtosObjectValue p){
        slot(p,"parallelMap",(a,x)->indexed(a,x,Kind.MAP));
        slot(p,"parallelFilter",(a,x)->indexed(a,x,Kind.FILTER));
        slot(p,"parallelFindIndex",(a,x)->indexed(a,x,Kind.FIND));
        slot(p,"parallelReduce",ProtosParallelRuntime::reduce);
        slot(p,"parallelSort",ProtosParallelRuntime::sort);
    }

    public static void installBytesParallel(ProtosObjectValue p){
        slot(p,"parallelRange",(a,x)->parallelRange(a.receiver(),a,x));
    }

    /*
     * Standard prelude objects remain owned by the caller runtime.  P transfer
     * never changes their mutation state.  Direct standard identities are only
     * physically shared when already frozen; otherwise ordinary graph copying
     * applies.  This keeps I010 isolation from globally freezing Core prototypes.
     */

    private enum Kind {MAP,FILTER,FIND}

    private static Object indexed(ProtosActivation a,List<?> supplied,Kind kind){
        ProtosArrayValue source=requireArray(a);
        if(supplied.isEmpty())throw error(a);
        Object callback=supplied.get(0);requireInvokable(callback,a);
        List<?> extra=supplied.subList(1,supplied.size());
        List<Object> sourceSnapshot=source.indexedSnapshot();
        if(sourceSnapshot.isEmpty())return resolved(a,
                kind==Kind.FIND?ProtosNullValue.INSTANCE:a.prelude().orElseThrow().newArray(List.of()));

        ArrayList<Snapshot> children=new ArrayList<>(sourceSnapshot.size());
        for(Object element:sourceSnapshot){
            ArrayList<Object> args=new ArrayList<>();args.add(element);args.addAll(extra);
            children.add(Snapshot.capture(callback,args,a));
        }
        Completion all=new Completion();
        ProtosFutureValue future=ownedFuture(a,c->all.forwardTo(c));
        Outcome[] outcomes=new Outcome[children.size()];
        AtomicInteger remaining=new AtomicInteger(children.size());
        AtomicBoolean abandoned=new AtomicBoolean();
        all.onCancel(()->abandoned.set(true));
        for(int i=0;i<children.size();i++){
            int index=i;Completion child=new Completion();
            child.onReady(o->{outcomes[index]=o;if(remaining.decrementAndGet()==0&&!abandoned.get())
                finishIndexed(kind,sourceSnapshot,outcomes,a,all);});
            submit(()->run(children.get(index),child));
        }
        return future;
    }

    private static void finishIndexed(Kind kind,List<Object> source,Outcome[] outcomes,
                                      ProtosActivation a,Completion target){
        for(int i=0;i<outcomes.length;i++){
            Outcome o=outcomes[i];
            if(o.error!=null){target.fail(o.error);return;}
            if(kind!=Kind.MAP&&o.value!=ProtosBooleanValue.TRUE&&o.value!=ProtosBooleanValue.FALSE){
                target.fail(occ(a,ProtosCoreErrors.StandardError.INVALID_PREDICATE_RESULT));return;
            }
            if(kind==Kind.FIND&&o.value==ProtosBooleanValue.TRUE){
                target.resolve(new ProtosIntegerValue(BigInteger.valueOf(i)));return;
            }
        }
        if(kind==Kind.FIND){target.resolve(ProtosNullValue.INSTANCE);return;}
        ArrayList<Object> result=new ArrayList<>();
        for(int i=0;i<outcomes.length;i++){
            if(kind==Kind.MAP)result.add(outcomes[i].value);
            else if(outcomes[i].value==ProtosBooleanValue.TRUE){
                try{result.add(Transfer.back(source.get(i),a));}
                catch(NonParallel e){target.fail(nonParallel(a));return;}
            }
        }
        target.resolve(a.prelude().orElseThrow().newArray(result));
    }

    private static Object reduce(ProtosActivation a,List<?> supplied){
        ProtosArrayValue source=requireArray(a);
        if(supplied.isEmpty())throw error(a);
        Object reducer=supplied.get(0);requireInvokable(reducer,a);
        List<?> extra=supplied.subList(1,supplied.size());
        List<Object> raw=source.indexedSnapshot();
        if(raw.isEmpty())return resolved(a,ProtosNullValue.INSTANCE);
        Staged staged=Staged.capture(raw,reducer,extra,a);
        if(raw.size()==1)return resolved(a,Transfer.back(staged.values.get(0),a));
        return ownedFuture(a,c->submit(()->{
            List<Object> round=new ArrayList<>(staged.values);
            while(round.size()>1){
                ArrayList<Object> next=new ArrayList<>();
                for(int i=0;i<round.size();i+=2){
                    if(i+1==round.size()){next.add(round.get(i));continue;}
                    ArrayList<Object> args=new ArrayList<>();
                    args.add(round.get(i));args.add(round.get(i+1));args.addAll(staged.extra);
                    Outcome o=runInline(Snapshot.capture(staged.callable,args,a));
                    if(o.error!=null){c.fail(o.error);return;}
                    next.add(o.value);
                }
                round=next;
            }
            try{c.resolve(Transfer.back(round.get(0),a));}
            catch(NonParallel e){c.fail(nonParallel(a));}
        }));
    }

    private static Object sort(ProtosActivation a,List<?> supplied){
        ProtosArrayValue source=requireArray(a);
        if(supplied.isEmpty())throw error(a);
        Object less=supplied.get(0);requireInvokable(less,a);
        List<?> extra=supplied.subList(1,supplied.size());
        List<Object> raw=source.indexedSnapshot();
        if(raw.isEmpty())return resolved(a,a.prelude().orElseThrow().newArray(List.of()));
        Staged staged=Staged.capture(raw,less,extra,a);
        if(raw.size()==1)return resolved(a,a.prelude().orElseThrow().newArray(
                List.of(Transfer.back(staged.values.get(0),a))));
        return ownedFuture(a,c->submit(()->{
            Outcome o=mergeSort(staged.values,staged.callable,staged.extra,a);
            if(o.error!=null){c.fail(o.error);return;}
            @SuppressWarnings("unchecked") List<Object> sorted=(List<Object>)o.value;
            try{
                ArrayList<Object> result=new ArrayList<>();
                for(Object v:sorted)result.add(Transfer.back(v,a));
                c.resolve(a.prelude().orElseThrow().newArray(result));
            }catch(NonParallel e){c.fail(nonParallel(a));}
        }));
    }

    private static Outcome mergeSort(List<Object> values,Object less,List<Object> extra,ProtosActivation a){
        if(values.size()<=1)return Outcome.ok(new ArrayList<>(values));
        int mid=values.size()/2;
        Outcome lo=mergeSort(values.subList(0,mid),less,extra,a);if(lo.error!=null)return lo;
        Outcome ro=mergeSort(values.subList(mid,values.size()),less,extra,a);if(ro.error!=null)return ro;
        @SuppressWarnings("unchecked") List<Object> l=(List<Object>)lo.value;
        @SuppressWarnings("unchecked") List<Object> r=(List<Object>)ro.value;
        ArrayList<Object> out=new ArrayList<>();int i=0,j=0;
        while(i<l.size()&&j<r.size()){
            Object lv=l.get(i),rv=r.get(j);
            Outcome lr=compare(less,lv,rv,extra,a);if(lr.error!=null)return lr;
            Outcome rl=compare(less,rv,lv,extra,a);if(rl.error!=null)return rl;
            if(!bool(lr.value)||!bool(rl.value))
                return Outcome.fail(occ(a,ProtosCoreErrors.StandardError.INVALID_COMPARATOR_RESULT));
            boolean ab=lr.value==ProtosBooleanValue.TRUE,ba=rl.value==ProtosBooleanValue.TRUE;
            if(ab&&ba)return Outcome.fail(occ(a,ProtosCoreErrors.StandardError.INVALID_COMPARATOR_ORDER));
            if(ab||!ba)out.add(l.get(i++));else out.add(r.get(j++));
        }
        while(i<l.size())out.add(l.get(i++));while(j<r.size())out.add(r.get(j++));
        return Outcome.ok(out);
    }

    private static Outcome compare(Object less,Object a,Object b,List<Object> extra,ProtosActivation caller){
        ArrayList<Object> args=new ArrayList<>();args.add(a);args.add(b);args.addAll(extra);
        return runInline(Snapshot.capture(less,args,caller));
    }
    private static boolean bool(Object v){return v==ProtosBooleanValue.TRUE||v==ProtosBooleanValue.FALSE;}

    private static Object parallelRange(Object receiver,ProtosActivation a,List<?> supplied){
        if(!P_DOMAINS.contains(a.executionDomain()))
            throw signal(a,ProtosCoreErrors.StandardError.PARALLEL_REGION_OUTSIDE_P);
        if(supplied.size()<3)throw error(a);
        BigInteger start=integer(supplied.get(0),a);if(start.signum()<0)throw error(a);
        BigInteger length=integer(supplied.get(1),a);if(length.signum()<0)throw error(a);
        BigInteger size=receiver instanceof ProtosBytesValue b?b.indexedSize():
                receiver instanceof ProtosByteRegionValue r?r.indexedSize():null;
        if(size==null||start.add(length).compareTo(size)>0)throw error(a);
        if(!(supplied.get(2) instanceof ProtosClosureValue worker))throw error(a);
        Object token=new Object();
        boolean reserved=receiver instanceof ProtosBytesValue b?b.tryReserve(start,length,token):
                ((ProtosByteRegionValue)receiver).tryReserve(start,length,token);
        if(!reserved)throw signal(a,ProtosCoreErrors.StandardError.PARALLEL_REGION_OVERLAP);
        List<Object> bytes=receiver instanceof ProtosBytesValue b?b.rangeSnapshot(start,length):
                ((ProtosByteRegionValue)receiver).rangeSnapshot(start,length);
        ProtosByteRegionValue region=new ProtosByteRegionValue(bytes);installRegion(region);
        ArrayList<Object> args=new ArrayList<>();args.add(region);args.addAll(supplied.subList(3,supplied.size()));
        Snapshot snapshot;
        try{snapshot=Snapshot.capture(worker,args,a);}
        catch(RuntimeException e){release(receiver,token);throw e;}
        return ownedFuture(a,c->{
            c.onCancel(()->release(receiver,token));
            c.onCommit(()->{
                if(receiver instanceof ProtosBytesValue b)b.commitReserved(start,region.indexedSnapshot(),token);
                else ((ProtosByteRegionValue)receiver).commitReserved(start,region.indexedSnapshot(),token);
            });
            submit(()->run(snapshot,c));
        });
    }

    private static void installRegion(ProtosByteRegionValue r){
        slot(r,"size",(a,x)->{if(a.receiver()!=r||!x.isEmpty())throw error(a);return new ProtosIntegerValue(r.indexedSize());});
        slot(r,"at",(a,x)->{
            if(a.receiver()!=r||x.size()!=1)throw error(a);BigInteger i=integer(x.get(0),a);
            if(i.signum()<0||i.compareTo(r.indexedSize())>=0)throw error(a);
            if(r.isIndexReserved(i))throw signal(a,ProtosCoreErrors.StandardError.PARALLEL_REGION_IN_USE);
            return r.indexedAt(i);});
        slot(r,"atPut",(a,x)->{
            if(a.receiver()!=r||x.size()!=2)throw error(a);BigInteger i=integer(x.get(0),a);
            if(i.signum()<0||i.compareTo(r.indexedSize())>=0)throw error(a);
            if(r.isIndexReserved(i))throw signal(a,ProtosCoreErrors.StandardError.PARALLEL_REGION_IN_USE);
            octet(x.get(1),a);return r.indexedPut(i,x.get(1));});
        slot(r,"parallelRange",(a,x)->parallelRange(r,a,x));
    }

    private static void release(Object receiver,Object token){
        if(receiver instanceof ProtosBytesValue b)b.releaseReservation(token);
        else ((ProtosByteRegionValue)receiver).releaseReservation(token);
    }

    private static ProtosFutureValue ownedFuture(ProtosActivation caller,
            java.util.function.Consumer<Completion> starter){
        ProtosFutureValue f=new ProtosFutureValue(caller.prelude().orElseThrow().futurePrototype(),caller.executionDomain());
        Completion completion=new Completion();
        ProtosTask parent=caller.task().orElse(null);
        ProtosTask producer=caller.executionDomain().createTask(parent,null,current->{
            if(current.cancellationRequested()){
                completion.cancel();f.cancelTerminal();current.observeCancellation();return;
            }
            if(!completion.isReady()){current.suspend(completion);return;}
            Outcome o=completion.outcome();
            if(o.error!=null){if(f.fail(o.error))current.fail(o.error);else current.complete(ProtosNullValue.INSTANCE);return;}
            Runnable commit=completion.commit();
            if(commit==null)f.resolve(o.value,caller);else f.resolveWithCommit(o.value,caller,commit);
            current.complete(ProtosNullValue.INSTANCE);
        });
        f.attachProducerTask(producer,caller);completion.bind(producer);starter.accept(completion);return f;
    }

    private static ProtosFutureValue resolved(ProtosActivation a,Object v){
        ProtosFutureValue f=new ProtosFutureValue(a.prelude().orElseThrow().futurePrototype(),a.executionDomain());
        f.resolve(v,a);return f;
    }

    private static final class Completion implements ProtosTask.WaitDependency {
        private volatile ProtosTask task;private volatile Outcome outcome;private volatile Runnable cancel,commit;
        private volatile java.util.function.Consumer<Outcome> ready;private final AtomicBoolean cancelled=new AtomicBoolean();
        void bind(ProtosTask t){task=t;if(outcome!=null)t.resume(this);}
        void forwardTo(Completion target){onReady(o->{if(o.error!=null)target.fail(o.error);else target.resolve(o.value);});onCancel(target::cancel);}
        void onReady(java.util.function.Consumer<Outcome> c){ready=c;if(outcome!=null)c.accept(outcome);}
        void onCancel(Runnable r){cancel=r;if(cancelled.get())r.run();}
        void onCommit(Runnable r){commit=r;}Runnable commit(){return commit;}
        public boolean isReady(){return outcome!=null;}Outcome outcome(){return outcome;}
        void resolve(Object v){complete(Outcome.ok(v));}void fail(ProtosObjectValue e){complete(Outcome.fail(e));}
        synchronized void complete(Outcome o){if(outcome!=null||cancelled.get())return;outcome=o;
            if(ready!=null)ready.accept(o);if(task!=null)task.resume(this);}
        void cancel(){if(cancelled.compareAndSet(false,true)&&cancel!=null)cancel.run();}
        public void waitingTaskCancelled(ProtosTask ignored){cancel();}
    }
    private static final class Outcome {
        final Object value;final ProtosObjectValue error;
        Outcome(Object v,ProtosObjectValue e){value=v;error=e;}
        static Outcome ok(Object v){return new Outcome(Objects.requireNonNull(v),null);}
        static Outcome fail(ProtosObjectValue e){return new Outcome(null,Objects.requireNonNull(e));}
    }

    private static void submit(Runnable r){EXECUTOR.execute(r);}
    private static void run(Snapshot s,Completion c){
        if(c.cancelled.get())return;Outcome o=runInline(s);if(o.error!=null)c.fail(o.error);else c.resolve(o.value);
    }

    private static Outcome runInline(Snapshot s){
        ProtosActorExecutionDomain d=new ProtosActorExecutionDomain();P_DOMAINS.add(d);
        try{
            ProtosActivation creator=s.caller.prelude().orElseThrow().newModuleActivation(
                    new ProtosActorModuleState(),null,s.caller.prelude().orElseThrow().newExecutionContext(),d);
            ProtosTask root=d.createTask(null,t->t.executeAction(()->ProtosInvocation.invoke(s.callable,s.args,creator)));
            d.dispatchUntilTerminal(root,()->{
                Runnable helper=EXECUTOR.getQueue().poll();if(helper==null)return false;helper.run();return true;
            });
            if(root.state()==ProtosTask.State.COMPLETED){
                try{return Outcome.ok(Transfer.back(root.result().orElse(ProtosNullValue.INSTANCE),s.caller));}
                catch(NonParallel e){return Outcome.fail(nonParallel(s.caller));}
            }
            if(root.state()==ProtosTask.State.FAILED&&root.failure().orElse(null) instanceof ProtosObjectValue e){
                try{return Outcome.fail((ProtosObjectValue)Transfer.back(e,s.caller));}
                catch(NonParallel x){return Outcome.fail(nonParallel(s.caller));}
            }
            return Outcome.fail(occ(s.caller,ProtosCoreErrors.StandardError.CANCELLED));
        }finally{P_DOMAINS.remove(d);}
    }

    private static final class Snapshot {
        final Object callable;final List<Object> args;final ProtosActivation caller;
        Snapshot(Object c,List<Object> a,ProtosActivation caller){callable=c;args=List.copyOf(a);this.caller=caller;}
        static Snapshot capture(Object callable,List<?> args,ProtosActivation caller){
            IdentityHashMap<Object,Object> memo=new IdentityHashMap<>();
            Object c=Transfer.copy(callable,caller,memo);
            ArrayList<Object> a=new ArrayList<>();for(Object v:args)a.add(Transfer.copy(v,caller,memo));
            return new Snapshot(c,a,caller);
        }
    }
    private static final class Staged {
        final List<Object> values;final Object callable;final List<Object> extra;
        Staged(List<Object> v,Object c,List<Object> e){values=v;callable=c;extra=e;}
        static Staged capture(List<Object> values,Object callable,List<?> extra,ProtosActivation caller){
            IdentityHashMap<Object,Object> memo=new IdentityHashMap<>();
            Object c=Transfer.copy(callable,caller,memo);ArrayList<Object> vs=new ArrayList<>(),es=new ArrayList<>();
            for(Object v:values)vs.add(Transfer.copy(v,caller,memo));for(Object v:extra)es.add(Transfer.copy(v,caller,memo));
            return new Staged(vs,c,es);
        }
    }

    private static final class NonParallel extends RuntimeException{NonParallel(){super(null,null,false,false);}}
    private static final class Transfer {
        static Object back(Object v,ProtosActivation a){return copy(v,a,new IdentityHashMap<>());}
        static Object copy(Object v,ProtosActivation a,IdentityHashMap<Object,Object> memo){
            if(v==ProtosNullValue.INSTANCE||v==ProtosBooleanValue.TRUE||v==ProtosBooleanValue.FALSE)return v;
            if(v instanceof ProtosIntegerValue x)return new ProtosIntegerValue(x.value());
            if(v instanceof ProtosFixedIntegerValue x)return new ProtosFixedIntegerValue(x.family(),x.value());
            if(v instanceof ProtosFloatValue x)return new ProtosFloatValue(x.value());
            if(v instanceof ProtosStringValue x)return new ProtosStringValue(x.value());
            if(v instanceof ProtosPathValue x)return new ProtosPathValue(a.prelude().orElseThrow().pathPrototype(),x.rooted(),x.components());
            if(v instanceof ProtosProcessArgumentsValue x){
                if(memo.containsKey(v))return memo.get(v);
                ProtosProcessArgumentsValue y=x.rematerializeForParallelTransfer();memo.put(v,y);return y;
            }
            if(v instanceof ProtosEnvironmentValue x){
                if(memo.containsKey(v))return memo.get(v);
                ProtosEnvironmentValue y=x.rematerializeForParallelTransfer();memo.put(v,y);return y;
            }
            if(v instanceof ProtosFutureValue||v instanceof ProtosByteRegionValue||v instanceof ProtosTask||v instanceof ProtosFileValue||v instanceof ProtosFilesystemValue||v instanceof ProtosProcessStandardStreamValue||v instanceof ProtosSendOperationControl||v==null)throw new NonParallel();
            if(memo.containsKey(v))return memo.get(v);
            ProtosPrelude p=a.prelude().orElseThrow();
            if(v==ProtosObjectValue.rootObject()||prelude(v,p))return v;
            if(v instanceof ProtosClosureValue x){
                ProtosClosureExecutionPlan plan=x.definition()==null?null:new CanonicalToTruffleLowerer().lowerClosurePlan(x.definition());
                ProtosClosureValue y=x.parallelProjection(List.of(p.newExecutionContext()),ProtosNullValue.INSTANCE,p,plan);
                memo.put(v,y);slots(x,y,a,memo);state(x,y);return y;
            }
            if(v instanceof ProtosArrayValue x){
                ArrayList<Object> es=new ArrayList<>();ProtosArrayValue shell=new ProtosArrayValue(p.arrayPrototype(),List.of());memo.put(v,shell);
                for(Object e:x.indexedSnapshot())es.add(copy(e,a,memo));ProtosArrayValue y=new ProtosArrayValue(p.arrayPrototype(),es);
                memo.put(v,y);slots(x,y,a,memo);state(x,y);return y;
            }
            if(v instanceof ProtosBytesValue x){
                Object parent=copy(x.parent().orElseThrow(),a,memo);ProtosBytesValue y=new ProtosBytesValue(parent);memo.put(v,y);
                for(Object e:x.indexedSnapshot())y.indexedAdd(copy(e,a,memo));slots(x,y,a,memo);state(x,y);return y;
            }
            if(v instanceof ProtosMapValue x){
                ProtosMapValue y=new ProtosMapValue(p.mapPrototype());memo.put(v,y);
                for(var e:x.keyedSnapshot())y.append(copy(e.key(),a,memo),e.recordedHash(),copy(e.value(),a,memo));
                slots(x,y,a,memo);state(x,y);return y;
            }
            if(v instanceof ProtosIdentityMapValue x){
                ProtosIdentityMapValue y=new ProtosIdentityMapValue(p.identityMapPrototype());memo.put(v,y);
                for(var e:x.keyedSnapshot())y.append(copy(e.key(),a,memo),e.recordedIdentityHash(),copy(e.value(),a,memo));
                slots(x,y,a,memo);state(x,y);return y;
            }
            if(v instanceof ProtosObjectValue x){
                if(x.parent().orElse(null)==p.contextPrototype())throw new NonParallel();
                Object parent=copy(x.parent().orElseThrow(),a,memo);ProtosObjectValue y=new ProtosObjectValue(parent);memo.put(v,y);
                slots(x,y,a,memo);state(x,y);return y;
            }
            throw new NonParallel();
        }
        static boolean prelude(Object v,ProtosPrelude p){
            if(!(v instanceof ProtosObjectValue o)||!o.isFrozen())return false;
            for(Object x:p.bindings().localSlotsSnapshot().values())if(x==v)return true;return false;
        }
        static void slots(ProtosObjectValue x,ProtosObjectValue y,ProtosActivation a,IdentityHashMap<Object,Object> memo){
            for(var e:x.localSlotsSnapshot().entrySet())if(!y.hasLocalSlot(e.getKey()))y.createLocalSlot(e.getKey(),copy(e.getValue(),a,memo));
        }
        static void state(ProtosObjectValue x,ProtosObjectValue y){if(x.isFrozen())y.freeze();else if(x.isClosed())y.close();}
    }

    private static void validateClosureArity(ProtosClosureValue c,int n,ProtosActivation a){
        if(c.definition()==null)return;int required=0;boolean rest=false;int total=c.definition().parameters().size();
        for(var p:c.definition().parameters()){if(p.rest())rest=true;else if(p.defaultValue().isEmpty())required++;}
        if(n<required||(!rest&&n>total))throw error(a);
    }
    private static void requireInvokable(Object v,ProtosActivation a){
        try{var s=ProtosValueLookup.lookup(v,"call",a.prelude().orElseThrow()).orElseThrow(()->error(a));
            if(!(s.value() instanceof ProtosClosureValue))throw error(a);}
        catch(UnsupportedOperationException e){throw error(a);}
    }
    private static ProtosArrayValue requireArray(ProtosActivation a){if(!(a.receiver() instanceof ProtosArrayValue x))throw error(a);return x;}
    private static BigInteger integer(Object v,ProtosActivation a){
        if(v instanceof ProtosIntegerValue x)return x.value();if(v instanceof ProtosFixedIntegerValue x)return x.value();throw error(a);}
    private static void octet(Object v,ProtosActivation a){BigInteger x=integer(v,a);if(x.signum()<0||x.compareTo(BigInteger.valueOf(255))>0)throw error(a);}
    private static ProtosObjectValue occ(ProtosActivation a,ProtosCoreErrors.StandardError e){return ProtosCoreErrors.newOccurrence(a,e);}
    private static ProtosObjectValue nonParallel(ProtosActivation a){return occ(a,ProtosCoreErrors.StandardError.NON_PARALLEL_VALUE);}
    private static ProtosSignalException signal(ProtosActivation a,ProtosCoreErrors.StandardError e){return new ProtosSignalException(occ(a,e));}
    private static ProtosSignalException error(ProtosActivation a){return new ProtosSignalException(ProtosCoreErrors.newError(a));}
    private static void slot(ProtosObjectValue p,String n,ProtosNativeClosureBody b){
        if(p.hasLocalSlot(n))throw new IllegalStateException("Core already defines "+n);p.createLocalSlot(n,ProtosClosureValue.nativeClosure(b));}
}

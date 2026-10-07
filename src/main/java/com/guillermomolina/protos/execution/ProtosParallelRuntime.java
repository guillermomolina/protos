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
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
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
    private record PPlacement(ProtosProcessExecutionHost host) {}
    private static final Map<ProtosActorExecutionDomain,PPlacement> P_DOMAINS=
            new ConcurrentHashMap<>();
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

    /*
     * Standard prelude objects remain owned by the caller runtime.  P transfer
     * never changes their mutation state.  Direct standard identities are only
     * physically shared when already frozen; otherwise ordinary graph copying
     * applies.  This keeps I010 isolation from globally freezing Core prototypes.
     */

    private static ProtosFutureValue ownedFuture(ProtosActivation caller,
            java.util.function.Consumer<Completion> starter){
        return ownedFuture(caller,starter,null);
    }

    /**
     * BUG015 deterministic test seam. The Completion is never started on a P carrier;
     * {@code afterPendingCheck} runs inside the producer Task after its pending observation and
     * before {@code suspend()}, and may resolve the Completion through the supplied sink.
     */
    static ProtosFutureValue ownedFutureForTesting(ProtosActivation caller,
            java.util.function.BiConsumer<ProtosTask,java.util.function.Consumer<Object>> afterPendingCheck){
        Completion[] started=new Completion[1];
        return ownedFuture(caller,c->started[0]=c,
                current->afterPendingCheck.accept(current,v->started[0].resolve(v)));
    }

    private static ProtosFutureValue ownedFuture(ProtosActivation caller,
            java.util.function.Consumer<Completion> starter,
            java.util.function.Consumer<ProtosTask> afterPendingCheck){
        ProtosFutureValue f=new ProtosFutureValue(caller.prelude().orElseThrow().futurePrototype(),caller.executionDomain());
        Completion completion=new Completion();
        ProtosTask parent=caller.task().orElse(null);
        ProtosTask producer=caller.executionDomain().createTask(parent,null,current->{
            if(current.cancellationRequested()){
                completion.cancel();f.cancelTerminal();current.observeCancellation();return;
            }
            if(!completion.isReady()){
                if(afterPendingCheck!=null)afterPendingCheck.accept(current);
                /*
                 * BUG015: the P outcome may become ready after the pending check but before
                 * suspend(). suspend() then returns false and leaves the Task RUNNING, so the
                 * outcome must be consumed now. A false return caused by cancellation leaves
                 * the Task RUNNABLE and re-enqueued; only a still-RUNNING Task may continue.
                 */
                if(current.suspend(completion))return;
                if(current.state()!=ProtosTask.State.RUNNING)return;
            }
            Outcome o=completion.outcome();
            if(o.error!=null){if(f.fail(o.error))current.fail(o.error);else current.complete(ProtosNullValue.INSTANCE);return;}
            f.resolve(o.value,caller);
            current.complete(ProtosNullValue.INSTANCE);
        });
        f.attachProducerTask(producer,caller);completion.bind(producer);starter.accept(completion);return f;
    }

    private static final class Completion implements ProtosTask.WaitDependency {
        private volatile ProtosTask task;private volatile Outcome outcome;
        private final AtomicBoolean cancelled=new AtomicBoolean();
        void bind(ProtosTask t){task=t;if(outcome!=null)t.resume(this);}
        public boolean isReady(){return outcome!=null;}Outcome outcome(){return outcome;}
        void resolve(Object v){complete(Outcome.ok(v));}void fail(ProtosObjectValue e){complete(Outcome.fail(e));}
        synchronized void complete(Outcome o){if(outcome!=null||cancelled.get())return;outcome=o;
            if(task!=null)task.resume(this);}
        void cancel(){cancelled.set(true);}
        public void waitingTaskCancelled(ProtosTask ignored){cancel();}
    }
    private static final class Outcome {
        final Object value;final ProtosObjectValue error;
        Outcome(Object v,ProtosObjectValue e){value=v;error=e;}
        static Outcome ok(Object v){return new Outcome(Objects.requireNonNull(v),null);}
        static Outcome fail(ProtosObjectValue e){return new Outcome(null,Objects.requireNonNull(e));}
    }

    private static void submit(Runnable r){EXECUTOR.execute(r);}
    private static void run(Snapshot s,Completion c){settle(c,s.caller,()->runInline(s));}

    /**
     * BUG017: P carrier execution boundary. Every admitted P job settles its Completion, so an
     * unexpected host failure cannot abandon the producer Task and leave its Future pending.
     * Ordinary host RuntimeExceptions (for example Truffle frame/interop failures) are contained
     * as a generic standard Error occurrence; the host Throwable never reaches the guest. JVM
     * Errors are not converted into guest failures: the Completion is still failed so the
     * Future terminates, and the Error is rethrown to the carrier (ThreadPoolExecutor replaces
     * it). Completion terminalization stays exactly-once and never overrides cancellation.
     */
    private static void settle(Completion c,ProtosActivation caller,java.util.function.Supplier<Outcome> execution){
        if(c.cancelled.get())return;
        Outcome o;
        try{o=execution.get();}
        catch(RuntimeException hostFailure){o=Outcome.fail(ProtosCoreErrors.newError(caller));}
        catch(Error fatal){
            try{c.fail(ProtosCoreErrors.newError(caller));}
            catch(RuntimeException|Error settleFailure){fatal.addSuppressed(settleFailure);}
            throw fatal;
        }
        if(o.error!=null)c.fail(o.error);else c.resolve(o.value);
    }

    /**
     * BUG017 deterministic test seam: submits one P job whose host execution throws
     * {@code hostFailure} through the real carrier {@link #settle} boundary.
     */
    static ProtosFutureValue hostFailingParallelForTesting(ProtosActivation caller,RuntimeException hostFailure){
        return ownedFuture(caller,c->submit(()->settle(c,caller,()->{throw hostFailure;})));
    }

    private static Outcome runInline(Snapshot s){
        if(s.executionHost==null)return runInlinePlaced(s);
        return s.executionHost.callForRuntime(()->runInlinePlaced(s));
    }

    private static Outcome runInlinePlaced(Snapshot s){
        ProtosActorExecutionDomain d=new ProtosActorExecutionDomain();
        P_DOMAINS.put(d,new PPlacement(s.executionHost));
        try{
            ProtosActivation creator=s.caller.prelude().orElseThrow().newModuleActivation(
                    new ProtosActorModuleState(),null,s.caller.prelude().orElseThrow().newExecutionContext(),d);
            ProtosTask root=d.createTask(null,t->{creator.attachTask(t);ProtosInvocation.executeInTaskForRuntime(s.callable,s.args,creator,t);});
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

    private static ProtosProcessExecutionHost executionHost(ProtosActivation caller){
        PPlacement placement=P_DOMAINS.get(caller.executionDomain());
        if(placement!=null)return placement.host();
        return caller.executionDomain().currentActorForRuntime()
                .flatMap(ProtosActor::processForRuntime)
                .flatMap(ProtosProcessRuntime::executionHostForRuntime)
                .orElse(null);
    }

    private static final class Snapshot {
        final Object callable;final List<Object> args;final ProtosActivation caller;
        final ProtosProcessExecutionHost executionHost;
        Snapshot(Object c,List<Object> a,ProtosActivation caller,ProtosProcessExecutionHost executionHost){
            callable=c;args=List.copyOf(a);this.caller=caller;this.executionHost=executionHost;
        }
        static Snapshot capture(Object callable,List<?> args,ProtosActivation caller){
            IdentityHashMap<Object,Object> memo=new IdentityHashMap<>();
            Object c=Transfer.copy(callable,caller,memo);
            ArrayList<Object> a=new ArrayList<>();for(Object v:args)a.add(Transfer.copy(v,caller,memo));
            return new Snapshot(c,a,caller,executionHost(caller));
        }
    }
    private static final class NonParallel extends RuntimeException{private static final long serialVersionUID = 1L; NonParallel(){super(null,null,false,false);}}
    private static final class Transfer {
        static Object back(Object v,ProtosActivation a){return copy(v,a,new IdentityHashMap<>());}
        static Object copy(Object v,ProtosActivation a,IdentityHashMap<Object,Object> memo){
            if(v==ProtosNullValue.INSTANCE||v==ProtosBooleanValue.TRUE||v==ProtosBooleanValue.FALSE)return v;
            if(v instanceof ProtosIntegerValue x)return new ProtosIntegerValue(x.value());
            if(v instanceof ProtosFloatValue x)return new ProtosFloatValue(x.value());
            if(v instanceof ProtosStringValue x)return x.copyForRuntime();
            if(v instanceof ProtosPathValue x)return new ProtosPathValue(a.prelude().orElseThrow().pathPrototype(),x.components());
            if(v instanceof ProtosEncodingValue x){
                memo.put(v,x);return x.transferForParallelRuntime();
            }
            if(v instanceof ProtosEnvironmentValue x){
                if(memo.containsKey(v))return memo.get(v);
                ProtosEnvironmentValue y=x.rematerializeForParallelTransfer();memo.put(v,y);return y;
            }
            if(v instanceof ProtosFutureValue||v instanceof ProtosTask||v instanceof ProtosFileValue||v instanceof ProtosFilesystemValue||v instanceof ProtosNetworkCapabilityValue||v instanceof ProtosTcpConnectionValue||v instanceof ProtosTcpListenerValue||v instanceof ProtosProcessStandardStreamValue||v instanceof ProtosSendOperationControl||v==null)throw new NonParallel();
            if(memo.containsKey(v))return memo.get(v);
            ProtosPrelude p=a.prelude().orElseThrow();
            if(v==ProtosObjectValue.rootObject()||prelude(v,p))return v;
            if(v instanceof ProtosSemanticTransferValue x){
                // PLAT051: rebuilt from its inert payload, never copied or projected.
                ProtosSemanticTransferValue y=ProtosSemanticTransferFamily.rematerializeForRuntime(x,p);
                if(y==null)throw new NonParallel();
                memo.put(v,y);return y;
            }
            if(v instanceof ProtosClosureValue x){
                java.util.function.Supplier<ProtosClosureExecutionPlan> rematerializer=null;
                CanonicalClosure definition=x.definition();
                if(definition!=null){
                    rematerializer=x.executionPlanRematerializerForParallelRuntime().orElseGet(
                            ()->x.executionPlan()
                                    .<java.util.function.Supplier<ProtosClosureExecutionPlan>>map(
                                            existing->()->{
                                                ProtosLanguageContext context=
                                                        ProtosLanguageContext.currentIfEnteredForRuntime();
                                                if(context==null
                                                        || !ProtosPolyglotExecutionContext
                                                                .hasEnteredContextForRuntime()){
                                                    throw new IllegalStateException(
                                                            "P source Closure Bytecode rematerialization "
                                                                    + "requires the retained Process Context");
                                                }
                                                return context.bytecodeExecutionPlanForDefinition(
                                                        definition,existing);
                                            })
                                    .orElseGet(
                                            ()->()->{
                                                throw new IllegalStateException(
                                                        "P source Closure rematerialization requires "
                                                                + "a prepared execution-plan template");
                                            }));
                }
                ProtosClosureValue y=x.parallelProjectionDeferred(
                        List.of(p.newExecutionContext()),ProtosNullValue.INSTANCE,p,rematerializer);
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
            if(p.isTcpConnectionPrototypeForRuntime(v))return true;
            if(p.isTcpListenerPrototypeForRuntime(v))return true;
            if(p.isStandardModuleMemberForRuntime(v))return true;
            ArrayList<String> names=new ArrayList<>();
            ArrayList<Object> values=new ArrayList<>();
            p.bindings().appendLocalBindingsTo(names,values);
            for(int index=0;index<values.size();index++)if(values.get(index)==v)return true;
            return false;
        }
        static void slots(ProtosObjectValue x,ProtosObjectValue y,ProtosActivation a,IdentityHashMap<Object,Object> memo){
            ArrayList<String> names=new ArrayList<>();
            ArrayList<Object> values=new ArrayList<>();
            x.appendLocalBindingsTo(names,values);
            for(int index=0;index<names.size();index++){
                String name=names.get(index);
                if(!y.hasLocalSlot(name))y.createLocalSlot(name,copy(values.get(index),a,memo));
            }
        }
        static void state(ProtosObjectValue x,ProtosObjectValue y){if(x.isFrozen())y.freeze();else if(x.isClosed())y.close();}
    }

    private static void validateClosureArity(ProtosClosureValue c,int n,ProtosActivation a){
        if(c.definition()==null)return;
        var parameters=c.definition().parameters();
        int required=0;boolean rest=false;int total=parameters.size();
        for(int index=0;index<total;index++){
            var p=parameters.get(index);
            if(p.rest())rest=true;
            else if(p.defaultValue().isEmpty())required++;
        }
        if(n<required||(!rest&&n>total))throw error(a);
    }
    private static ProtosObjectValue occ(ProtosActivation a,ProtosCoreErrors.StandardError e){return ProtosCoreErrors.newOccurrence(a,e);}
    private static ProtosObjectValue nonParallel(ProtosActivation a){return occ(a,ProtosCoreErrors.StandardError.NON_PARALLEL_VALUE);}
    private static ProtosSignalException error(ProtosActivation a){return new ProtosSignalException(ProtosCoreErrors.newError(a));}
}

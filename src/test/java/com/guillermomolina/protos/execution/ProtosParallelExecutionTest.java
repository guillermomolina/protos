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
import static org.junit.jupiter.api.Assertions.*;
import com.guillermomolina.protos.runtime.*;
import java.math.BigInteger;import java.nio.file.Path;import java.util.*;
import java.util.concurrent.*;import org.junit.jupiter.api.Test;
class ProtosParallelExecutionTest{
 private static ProtosPrelude core()throws Exception{return new ProtosCoreBootstrap().bootstrap(Path.of("protos","lib","core"));}
 private static Object eval(ProtosPrelude p,ProtosActivation a,String s){return new ProtosSourceCompiler().compile(s).call(a);}
 @Test void closedPublicSurface()throws Exception{var p=core();assertFalse(p.bindings().hasLocalSlot("P"));assertFalse(p.arrayPrototype().hasLocalSlot("parallelEach"));assertTrue(ProtosObjectValue.rootObject().hasLocalSlot("parallel"));for(String n:List.of("parallelMap","parallelFilter","parallelFindIndex","parallelReduce","parallelSort"))assertTrue(p.arrayPrototype().hasLocalSlot(n));}
 @Test void standardPublicationFreezesSharedPrototypesWithoutFreezingChildren()throws Exception{var p=core();assertTrue(ProtosObjectValue.rootObject().isFrozen());assertTrue(p.integerPrototype().isFrozen());ProtosObjectValue child=new ProtosObjectValue(p.integerPrototype());assertFalse(child.isFrozen());child.createLocalSlot("probe",ProtosNullValue.INSTANCE);assertTrue(child.hasLocalSlot("probe"));}
 @Test void reloweringCreatesFreshPlans()throws Exception{var p=core();var a=p.newModuleActivation();var c=(ProtosClosureValue)eval(p,a,"() => 1");var x=new CanonicalToTruffleLowerer().lowerClosurePlan(c.definition());var y=new CanonicalToTruffleLowerer().lowerClosurePlan(c.definition());assertNotSame(c.executionPlan().orElseThrow(),x);assertNotSame(x,y);}
 @Test void executorBounded()throws Exception{core();assertTrue(ProtosParallelRuntime.configuredCarrierLimit()>=2);assertTrue(ProtosParallelRuntime.liveCarrierCountForTesting()<=ProtosParallelRuntime.configuredCarrierLimit());}
 @Test void parallelReturnsCallerDomainFuture()throws Exception{var p=core();var d=new ProtosActorExecutionDomain();var a=p.newModuleActivation(new ProtosActorModuleState(),null,p.newExecutionContext(),d);var f=(ProtosFutureValue)eval(p,a,"((x) => x).parallel(42)");assertSame(d,f.domain());while(f.isPending()){d.dispatchUntilIdle();Thread.onSpinWait();}assertEquals(BigInteger.valueOf(42),((ProtosIntegerValue)f.resolvedValue().orElseThrow()).value());}
 @Test void highIndexCompletionCannotSelectFailure()throws Exception{var p=core();var d=new ProtosActorExecutionDomain();var a=p.newModuleActivation(new ProtosActorModuleState(),null,p.newExecutionContext(),d);CountDownLatch high=new CountDownLatch(1),lowRelease=new CountDownLatch(1);a.context().createLocalSlot("worker",ProtosClosureValue.nativeClosure((x,args)->{int n=((ProtosIntegerValue)args.get(0)).value().intValueExact();try{if(n==1){high.await();lowRelease.await();}else high.countDown();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}throw new ProtosSignalException(ProtosCoreErrors.newError(x));}));a.context().createLocalSlot("xs",p.newArray(List.of(new ProtosIntegerValue(BigInteger.ONE),new ProtosIntegerValue(BigInteger.TWO))));var f=(ProtosFutureValue)eval(p,a,"xs.parallelMap(worker)");assertTrue(high.await(5,TimeUnit.SECONDS));lowRelease.countDown();while(f.isPending()){d.dispatchUntilIdle();Thread.onSpinWait();}assertEquals(ProtosFutureValue.State.FAILED,f.state());}

 @Test void ipDataTransferConformanceSourceRoundTripsThroughP()throws Exception{
  var p=core();var d=new ProtosActorExecutionDomain();
  var a=p.newModuleActivation(new ProtosActorModuleState(),null,p.newExecutionContext(),d);
  var source=java.nio.file.Files.readString(
      Path.of("protos","tests","conformance","network","ip-data-parallel-transfer.protos"),
      java.nio.charset.StandardCharsets.UTF_8);
  var f=assertInstanceOf(ProtosFutureValue.class,eval(p,a,source));
  while(f.isPending()){d.dispatchUntilIdle();Thread.onSpinWait();}
  assertEquals(ProtosFutureValue.State.RESOLVED,f.state());
  assertSame(ProtosBooleanValue.TRUE,f.resolvedValue().orElseThrow());
  d.dispatchUntilIdle();
  assertEquals(0,d.liveTaskCount());
 }
}

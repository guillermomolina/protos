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
import java.nio.file.Path;import java.util.*;
import org.junit.jupiter.api.Test;
class ProtosParallelExecutionTest{
 private static ProtosPrelude core()throws Exception{return new ProtosCoreBootstrap().bootstrap(Path.of("protos","lib","core"));}
 private static Object evalHosted(ProtosHostedExecutionTestFixture h,String s){return h.evaluatePersistent("<parallel-test>",s);}
 private static void dispatchHosted(ProtosHostedExecutionTestFixture h,ProtosActorExecutionDomain d){h.callEntered(()->{d.dispatchUntilIdle();return null;});}
 @Test void closedPublicSurface()throws Exception{var p=core();assertFalse(p.bindings().hasLocalSlot("P"));assertFalse(p.arrayPrototype().hasLocalSlot("parallelEach"));assertTrue(ProtosObjectValue.rootObject().hasLocalSlot("parallel"));for(String n:List.of("parallelMap","parallelFilter","parallelFindIndex","parallelReduce","parallelSort"))assertFalse(p.arrayPrototype().hasLocalSlot(n),n+" is Standard Library policy (D160), not Core Array");}
 @Test void standardPublicationFreezesSharedPrototypesWithoutFreezingChildren()throws Exception{var p=core();assertTrue(ProtosObjectValue.rootObject().isFrozen());assertTrue(p.integerPrototype().isFrozen());ProtosObjectValue child=new ProtosObjectValue(p.integerPrototype());assertFalse(child.isFrozen());child.createLocalSlot("probe",ProtosNullValue.INSTANCE);assertTrue(child.hasLocalSlot("probe"));}
 @Test void executorBounded()throws Exception{core();assertTrue(ProtosParallelRuntime.configuredCarrierLimit()>=2);assertTrue(ProtosParallelRuntime.liveCarrierCountForTesting()<=ProtosParallelRuntime.configuredCarrierLimit());}
 @Test void parallelReturnsCallerDomainFuture()throws Exception{var p=core();try(var h=ProtosHostedExecutionTestFixture.open(p)){var a=h.activation();var d=a.executionDomain();var f=(ProtosFutureValue)evalHosted(h,"((x) => x).parallel(42)");assertSame(d,f.domain());while(f.isPending()){dispatchHosted(h,d);Thread.onSpinWait();}assertEquals(String.valueOf("42"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(f.resolvedValue().orElseThrow()));}}

 @Test void identityMapSnapshotCaptureIsIsolatedFromLaterSourceMutation()throws Exception{
  var p=core();
  try(var h=ProtosHostedExecutionTestFixture.open(p)){
   var d=h.activation().executionDomain();
   var f=assertInstanceOf(
       ProtosFutureValue.class,
       evalHosted(
           h,
           "m: IdentityMap()\n"
               + "m[1] = 10\n"
               + "((copy) => copy[1]).parallel(m)"));
   assertEquals(String.valueOf("20"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(evalHosted(h,"m[1] = 20")));
   while(f.isPending()){
    dispatchHosted(h,d);
    Thread.onSpinWait();
   }
   assertEquals(ProtosFutureValue.State.RESOLVED,f.state());
   assertEquals(String.valueOf("10"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(f.resolvedValue().orElseThrow()));
   dispatchHosted(h,d);
   assertEquals(0,d.liveTaskCount());
  }
 }


 @Test void ipDataTransferConformanceSourceRoundTripsThroughP()throws Exception{
  // D172: the fixture imports the canonical IP families from std:network.
  var p=new ProtosCoreBootstrap().bootstrap(
      Path.of("protos","lib","core"),
      new ProtosStandardLibraryModuleResolver(Path.of("protos","lib")));
  try(var h=ProtosHostedExecutionTestFixture.open(p)){
   var d=h.activation().executionDomain();
   var source=java.nio.file.Files.readString(
       Path.of("protos","tests","tooling","scheduling/parallel-execution-ip-data-transfer.protos"),
       java.nio.charset.StandardCharsets.UTF_8);
   var f=assertInstanceOf(ProtosFutureValue.class,evalHosted(h,source));
   while(f.isPending()){dispatchHosted(h,d);Thread.onSpinWait();}
   assertEquals(ProtosFutureValue.State.RESOLVED,f.state());
   assertSame(ProtosBooleanValue.TRUE,f.resolvedValue().orElseThrow());
   dispatchHosted(h,d);
   assertEquals(0,d.liveTaskCount());
  }
 }

 /*
  * BUG015: the P outcome becomes ready after the producer's pending check but before suspend().
  * suspend() returns false and the producer stays RUNNING; it must consume the outcome rather than
  * return as though it had suspended.
  */
 @Test void readyBeforeSuspendCompletionIsConsumedWithoutLostWakeup()throws Exception{
  var p=core();
  try(var h=ProtosHostedExecutionTestFixture.open(p)){
   var d=h.activation().executionDomain();
   int[] observedPending={0};
   var f=h.callEntered(()->ProtosParallelRuntime.ownedFutureForTesting(h.activation(),(current,resolve)->{
    observedPending[0]++;
    assertEquals(ProtosTask.State.RUNNING,current.state());
    resolve.accept(new ProtosIntegerValue(42));
    assertEquals(ProtosTask.State.RUNNING,current.state());
   }));
   var producer=f.producerTask().orElseThrow();
   dispatchHosted(h,d);
   assertEquals(1,observedPending[0]);
   assertEquals(ProtosFutureValue.State.RESOLVED,f.state());
   assertEquals(String.valueOf("42"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(f.resolvedValue().orElseThrow()));
   assertEquals(ProtosTask.State.COMPLETED,producer.state());
   assertEquals(0,d.liveTaskCount());
  }
 }

 /*
  * BUG015: when cancellation wins the same window, suspend() also returns false but the producer
  * is RUNNABLE; the ready outcome must not be consumed and the Future is cancelled.
  */
 @Test void readyBeforeSuspendCancellationWinsWithoutConsumingOutcome()throws Exception{
  var p=core();
  try(var h=ProtosHostedExecutionTestFixture.open(p)){
   var d=h.activation().executionDomain();
   int[] observedPending={0};
   var f=h.callEntered(()->ProtosParallelRuntime.ownedFutureForTesting(h.activation(),(current,resolve)->{
    observedPending[0]++;
    assertTrue(current.requestCancellation());
    resolve.accept(new ProtosIntegerValue(42));
   }));
   var producer=f.producerTask().orElseThrow();
   dispatchHosted(h,d);
   assertEquals(1,observedPending[0]);
   assertEquals(ProtosFutureValue.State.CANCELLED,f.state());
   assertTrue(f.resolvedValue().isEmpty());
   assertEquals(ProtosTask.State.CANCELLED,producer.state());
   assertEquals(0,d.liveTaskCount());
  }
 }

 /*
  * BUG017: an unexpected host failure escaping P execution on a carrier must still settle the
  * Completion. Before the fix the carrier exited without an outcome and the Future stayed pending
  * forever; the bounded wait turns that hang into a failure.
  */
 @Test void hostExecutionFailureSettlesFutureAsGenericError()throws Exception{
  var p=core();
  try(var h=ProtosHostedExecutionTestFixture.open(p)){
   var d=h.activation().executionDomain();
   var f=h.callEntered(()->ProtosParallelRuntime.hostFailingParallelForTesting(
       h.activation(),new IllegalStateException("BUG017 injected host failure")));
   var producer=f.producerTask().orElseThrow();
   long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
   while(f.isPending()&&System.nanoTime()<deadline){dispatchHosted(h,d);Thread.onSpinWait();}
   assertEquals(ProtosFutureValue.State.FAILED,f.state(),"P Future must not remain pending after a host failure");
   var error=f.failedError().orElseThrow();
   assertSame(p.errorPrototype(),error.parent().orElseThrow());
   assertEquals(ProtosTask.State.FAILED,producer.state());
   dispatchHosted(h,d);
   assertEquals(0,d.liveTaskCount());
  }
 }

 /*
  * BUG026: P Closure projections must see the frozen standard Prelude as their outer lexical scope
  * (§71.15) without capturing the caller's contexts; the full Closure.parallel -> worker -> Future
  * path is observed through real terminal Future states.
  */
 private static void awaitTerminal(ProtosHostedExecutionTestFixture h,ProtosActorExecutionDomain d,ProtosFutureValue f){
  long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
  while(f.isPending()&&System.nanoTime()<deadline){dispatchHosted(h,d);Thread.onSpinWait();}
  assertFalse(f.isPending(),"P Future must reach a terminal state");
 }
 private static Object resolvedP(ProtosHostedExecutionTestFixture h,String s){
  var d=h.activation().executionDomain();
  var f=assertInstanceOf(ProtosFutureValue.class,evalHosted(h,s));
  awaitTerminal(h,d,f);
  assertEquals(ProtosFutureValue.State.RESOLVED,f.state(),s);
  return f.resolvedValue().orElseThrow();
 }
 private static ProtosObjectValue failedP(ProtosHostedExecutionTestFixture h,String s){
  var d=h.activation().executionDomain();
  var f=assertInstanceOf(ProtosFutureValue.class,evalHosted(h,s));
  awaitTerminal(h,d,f);
  assertEquals(ProtosFutureValue.State.FAILED,f.state(),s);
  assertTrue(f.resolvedValue().isEmpty());
  return f.failedError().orElseThrow();
 }
 private static void assertErrorCategory(ProtosPrelude p,String category,ProtosObjectValue error){
  Object expected=p.bindings().readLocalSlot(category).orElseThrow();
  Object current=error;
  while(current instanceof ProtosObjectValue o){if(o==expected)return;current=o.parent().orElse(null);}
  fail("Error does not delegate to "+category);
 }
 private static String integerText(Object v){return ProtosNumericValueSupport.integerDecimalText(v);}

 @Test void parallelClosureSeesStandardPrelude()throws Exception{
  var p=core();
  try(var h=ProtosHostedExecutionTestFixture.open(p)){
   assertSame(ProtosBooleanValue.TRUE,resolvedP(h,"((n) => { Integer.recognizes(n) }).parallel(84)"));
   var pair=assertInstanceOf(ProtosArrayValue.class,resolvedP(h,"((n) => { Array(1, n) }).parallel(84)"));
   assertEquals(2,pair.indexedSize());
   assertEquals("1",integerText(pair.indexedAt(0)));
   assertEquals("84",integerText(pair.indexedAt(1)));
   var empty=assertInstanceOf(ProtosArrayValue.class,resolvedP(h,"(() => { [] }).parallel()"));
   assertEquals(0,empty.indexedSize());
   assertEquals("hello",assertInstanceOf(ProtosStringValue.class,resolvedP(h,"(() => { \"hello\" }).parallel()")).value());
   assertSame(ProtosBooleanValue.TRUE,resolvedP(h,"(() => { true }).parallel()"));
   dispatchHosted(h,h.activation().executionDomain());
   assertEquals(0,h.activation().executionDomain().liveTaskCount());
  }
 }

 @Test void parallelArrayResultGraphsTransferWithIdentity()throws Exception{
  var p=core();
  try(var h=ProtosHostedExecutionTestFixture.open(p)){
   var result=assertInstanceOf(ProtosArrayValue.class,resolvedP(h,
       "src: Array(1, 2)\n"
           + "((a) => {\n"
           + "    cell: Array(7)\n"
           + "    a[0] = 99\n"
           + "    holder: { items: Array(5, 6) }\n"
           + "    Array([], Array(1, 2), Array(Array(3), 4), Array(cell, cell), holder, a)\n"
           + "}).parallel(src)"));
   assertEquals(6,result.indexedSize());
   assertEquals(0,assertInstanceOf(ProtosArrayValue.class,result.indexedAt(0)).indexedSize());
   var numbers=assertInstanceOf(ProtosArrayValue.class,result.indexedAt(1));
   assertEquals(2,numbers.indexedSize());
   assertEquals("1",integerText(numbers.indexedAt(0)));
   assertEquals("2",integerText(numbers.indexedAt(1)));
   var nested=assertInstanceOf(ProtosArrayValue.class,result.indexedAt(2));
   assertEquals(2,nested.indexedSize());
   var inner=assertInstanceOf(ProtosArrayValue.class,nested.indexedAt(0));
   assertEquals(1,inner.indexedSize());
   assertEquals("3",integerText(inner.indexedAt(0)));
   assertEquals("4",integerText(nested.indexedAt(1)));
   var aliased=assertInstanceOf(ProtosArrayValue.class,result.indexedAt(3));
   assertEquals(2,aliased.indexedSize());
   assertSame(aliased.indexedAt(0),aliased.indexedAt(1),"internal alias must survive transfer");
   assertEquals("7",integerText(assertInstanceOf(ProtosArrayValue.class,aliased.indexedAt(0)).indexedAt(0)));
   var holder=assertInstanceOf(ProtosObjectValue.class,result.indexedAt(4));
   var items=assertInstanceOf(ProtosArrayValue.class,holder.readLocalSlot("items").orElseThrow());
   assertEquals(2,items.indexedSize());
   assertEquals("5",integerText(items.indexedAt(0)));
   assertEquals("6",integerText(items.indexedAt(1)));
   var source=assertInstanceOf(ProtosArrayValue.class,evalHosted(h,"src"));
   var returned=assertInstanceOf(ProtosArrayValue.class,result.indexedAt(5));
   assertNotSame(source,returned,"mutable argument must be copied, not shared");
   assertEquals("99",integerText(returned.indexedAt(0)));
   assertEquals("1",integerText(source.indexedAt(0)),"caller argument must not be modified by P");
   assertEquals("2",integerText(source.indexedAt(1)));

   var cyclic=assertInstanceOf(ProtosArrayValue.class,resolvedP(h,
       "(() => {\n    s: Array(0)\n    s[0] = s\n    s\n}).parallel()"));
   assertEquals(1,cyclic.indexedSize());
   assertSame(cyclic,cyclic.indexedAt(0),"self-reference must survive transfer");
  }
 }

 @Test void parallelClosureDoesNotCaptureCallerLocals()throws Exception{
  var p=core();
  try(var h=ProtosHostedExecutionTestFixture.open(p)){
   evalHosted(h,"bug026Secret: 41");
   assertErrorCategory(p,"SlotNotFound",failedP(h,"(() => { bug026Secret }).parallel()"));
   assertEquals("42",integerText(resolvedP(h,"((s) => { s + 1 }).parallel(bug026Secret)")));
  }
 }

 @Test void parallelFailuresAndNonTransferableResults()throws Exception{
  var p=core();
  try(var h=ProtosHostedExecutionTestFixture.open(p)){
   assertErrorCategory(p,"SlotNotFound",failedP(h,"(() => { SlotNotFound().signal() }).parallel()"));
   assertErrorCategory(p,"NonParallelValue",failedP(h,"(() => { (() => { 1 }).future() }).parallel()"));
   var ok=assertInstanceOf(ProtosArrayValue.class,resolvedP(h,"(() => { Array(1) }).parallel()"));
   assertEquals(1,ok.indexedSize());
   assertErrorCategory(p,"SlotNotFound",failedP(h,
       "(() => {\n    partial: Array(1, 2)\n    SlotNotFound().signal()\n    partial\n}).parallel()"));
   dispatchHosted(h,h.activation().executionDomain());
   assertEquals(0,h.activation().executionDomain().liveTaskCount());
  }
 }
}

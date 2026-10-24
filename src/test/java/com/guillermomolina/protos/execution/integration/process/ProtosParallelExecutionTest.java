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

 /*
  * LIB024: P transfer memoizes the definitive Vector/Matrix shell before copying its private
  * payload, so self-cycles, mutual cycles and shared paths resolve to the same destination object.
  */
 @Test void linearAlgebraTransferPublishesDefinitiveShellsAndCopiesPayload()throws Exception{
  var p=core();
  var source=p.newModuleActivation();
  var vectorPrototype=linearAlgebraMember(p,"Vector");
  var matrixPrototype=linearAlgebraMember(p,"Matrix");
  Object half=ProtosNumericValueSupport.divideIntegersExactly(
      new ProtosIntegerValue(1),new ProtosIntegerValue(2),p);
  Object complex=ProtosComplexNumericSupport.normalize(
      new ProtosIntegerValue(1),new ProtosFloatValue(-0.0d),p);
  Object hugeRows=ProtosNumericValueSupport.integerFromHost(java.math.BigInteger.ONE.shiftLeft(70),p);
  var vector=new ProtosVectorValue(vectorPrototype,List.of(new ProtosIntegerValue(1),half,complex));
  var matrix=new ProtosMatrixValue(matrixPrototype,new ProtosIntegerValue(1),new ProtosIntegerValue(2),
      List.of(complex,new ProtosFloatValue(0.5d)));
  var empty=new ProtosMatrixValue(matrixPrototype,hugeRows,new ProtosIntegerValue(0),List.of());
  vector.createLocalSlot("self",vector);
  vector.createLocalSlot("matrix",matrix);
  matrix.createLocalSlot("vector",vector);
  matrix.close();
  empty.freeze();

  boolean[] semanticRecords=new boolean[1];
  var captured=ProtosParallelRuntime.captureValuesForTesting(
      List.of(vector,matrix,vector,empty),source,semanticRecords);
  assertTrue(semanticRecords[0]);
  var delivered=ProtosParallelRuntime.materializeValuesForTesting(captured,p.newModuleActivation());

  var copiedVector=assertInstanceOf(ProtosVectorValue.class,delivered.get(0));
  var copiedMatrix=assertInstanceOf(ProtosMatrixValue.class,delivered.get(1));
  var copiedEmpty=assertInstanceOf(ProtosMatrixValue.class,delivered.get(3));
  assertNotSame(vector,copiedVector);
  assertNotSame(matrix,copiedMatrix);
  assertSame(copiedVector,delivered.get(2));
  assertSame(copiedVector,copiedVector.readLocalSlot("self").orElseThrow());
  assertSame(copiedMatrix,copiedVector.readLocalSlot("matrix").orElseThrow());
  assertSame(copiedVector,copiedMatrix.readLocalSlot("vector").orElseThrow());
  assertSame(vectorPrototype,copiedVector.parent().orElseThrow());
  assertSame(matrixPrototype,copiedMatrix.parent().orElseThrow());
  assertTrue(copiedVector.isOpen());
  assertTrue(copiedMatrix.isClosed());
  assertTrue(copiedEmpty.isFrozen());
  assertTrue(ProtosIdentity.identical(hugeRows,copiedEmpty.rowsValue()));
  assertTrue(ProtosIdentity.identical(new ProtosIntegerValue(0),copiedEmpty.columnsValue()));

  var elements=copiedVector.indexedSnapshot();
  assertTrue(ProtosNumericValueSupport.isFraction(elements.get(1)));
  assertTrue(ProtosComplexNumericSupport.isComplex(elements.get(2)));
  assertNotSame(half,elements.get(1));
  assertNotSame(complex,elements.get(2));
  assertTrue(ProtosIdentity.identical(half,elements.get(1)));
  assertTrue(ProtosIdentity.identical(complex,elements.get(2)));
  assertSame(elements.get(2),copiedMatrix.indexedAt(new ProtosIntegerValue(0),new ProtosIntegerValue(0)));
  // Public reads after materialization yield Numbers, never inert transfer descriptors.
  for(int index=0;index<copiedVector.indexedSize();index++){
   Object element=copiedVector.indexedAt(new ProtosIntegerValue(index));
   assertTrue(ProtosNumericValueSupport.isCurrentNumber(element));
   assertFalse(ProtosNumericValueSupport.isFractionTransferDescriptor(element));
   assertFalse(ProtosComplexNumericSupport.isTransferDescriptor(element));
  }
  for(Object element:copiedMatrix.indexedSnapshot())assertTrue(ProtosNumericValueSupport.isCurrentNumber(element));

  // No mutable aliasing with the origin in either direction.
  vector.indexedPut(new ProtosIntegerValue(0),new ProtosIntegerValue(9));
  copiedMatrix.indexedPut(new ProtosIntegerValue(0),new ProtosIntegerValue(1),new ProtosIntegerValue(8));
  assertTrue(ProtosIdentity.identical(new ProtosIntegerValue(1),copiedVector.indexedAt(new ProtosIntegerValue(0))));
  assertTrue(ProtosIdentity.identical(new ProtosFloatValue(0.5d),
      matrix.indexedAt(new ProtosIntegerValue(0),new ProtosIntegerValue(1))));
 }

 @Test void linearAlgebraResultsThroughPMatchOrdinaryExecution()throws Exception{
  var p=new ProtosCoreBootstrap().bootstrap(
      Path.of("protos","lib","core"),
      new ProtosStandardLibraryModuleResolver(Path.of("protos","lib")));
  try(var h=ProtosHostedExecutionTestFixture.open(p)){
   var d=h.activation().executionDomain();
   // Every P probe answers exactly one Number: Vector/Matrix results are reduced to scalars
   // inside the worker, so no collection crosses the P result boundary (BUG026 #958).
   String[] probes={
       "vec.dot(vec)",
       "vec.dot(extra)",
       "mat.matvec(other).at(0)",
       "mat.matvec(other).at(1)",
       "mat.matmul(mat).at(0, 0)",
       "mat.matmul(mat).at(1, 1)",
       "mat.transpose().at(0, 1)",
       "mat.transpose().at(1, 0)",
       "vec.scale(2).at(3)",
       "vec.scale(1 / 2).at(1)",
       "vec.add(extra).at(2)",
       "vec.add(extra).at(3)",
       "vec.copy().at(3)",
       "mat.copy().at(1, 1)",
       "vec.copy().size()",
       "mat.copy().rows()",
       "vec.size()",
       "mat.rows()",
       "mat.columns()"
   };
   StringBuilder probeSource=new StringBuilder("probes: [\n");
   for(int index=0;index<probes.length;index++){
    probeSource.append("    (vec, mat, other, extra) => ").append(probes[index])
        .append(index+1<probes.length?",\n":"\n");
   }
   probeSource.append("]\n");
   var pair=assertInstanceOf(ProtosArrayValue.class,evalHosted(h,
       "LA: import(\"std:math/LinearAlgebra\")\n"
           + "Vector: LA.Vector\n"
           + "Matrix: LA.Matrix\n"
           + "v: Vector(1, 1 / 2, 2.5, Complex(1, 2))\n"
           + "u: Vector(2, 1 / 3, -0.0, Complex(0, 1))\n"
           + "m: Matrix(2, 2, 1, 1 / 3, 0.1, Complex(0, 1))\n"
           + "w: Vector(3, -0.0)\n"
           + probeSource
           + "ordinary: Array()\n"
           + "futures: Array()\n"
           + "probes.each((probe) => {\n"
           + "    ordinary = Array(...ordinary, probe(v, m, w, u))\n"
           + "    futures = Array(...futures, probe.parallel(v, m, w, u))\n"
           + "})\n"
           + "[ordinary, futures]"));
   var expected=assertInstanceOf(ProtosArrayValue.class,pair.indexedAt(0)).indexedSnapshot();
   var futures=assertInstanceOf(ProtosArrayValue.class,pair.indexedAt(1)).indexedSnapshot();
   assertEquals(probes.length,expected.size());
   assertEquals(probes.length,futures.size());
   for(int index=0;index<probes.length;index++){
    String probe=probes[index];
    var f=assertInstanceOf(ProtosFutureValue.class,futures.get(index));
    while(f.isPending()){dispatchHosted(h,d);Thread.onSpinWait();}
    assertEquals(ProtosFutureValue.State.RESOLVED,f.state(),
        () -> probe+" failed in P with "+standardErrorName(h.activation(),f));
    Object actual=f.resolvedValue().orElseThrow();
    assertTrue(ProtosNumericValueSupport.isCurrentNumber(expected.get(index)),probes[index]);
    assertTrue(ProtosNumericValueSupport.isCurrentNumber(actual),probes[index]);
    assertTrue(ProtosIdentity.identical(expected.get(index),actual),probes[index]);
   }
   dispatchHosted(h,d);
   assertEquals(0,d.liveTaskCount());
  }
 }

 /*
  * LIB024 after BUG026: Vector and Matrix results return from the worker as complete values
  * delegating to the shared standard prototypes, equal element-wise to ordinary execution, with
  * internal aliasing preserved and no Number transfer descriptor observable.
  */
 @Test void linearAlgebraCollectionResultsReturnFromP()throws Exception{
  var p=new ProtosCoreBootstrap().bootstrap(
      Path.of("protos","lib","core"),
      new ProtosStandardLibraryModuleResolver(Path.of("protos","lib")));
  try(var h=ProtosHostedExecutionTestFixture.open(p)){
   var d=h.activation().executionDomain();
   var pair=assertInstanceOf(ProtosArrayValue.class,evalHosted(h,
       "LA: import(\"std:math/LinearAlgebra\")\n"
           + "Vector: LA.Vector\n"
           + "Matrix: LA.Matrix\n"
           + "v: Vector(1, 1 / 2, 2.5, Complex(1, 2))\n"
           + "m: Matrix(2, 2, 1, 1 / 3, 0.1, Complex(0, 1))\n"
           + "w: Vector(3, -0.0)\n"
           + "work: (vec, mat, other) => {\n"
           + "    sum: vec.add(vec)\n"
           + "    [sum, sum, vec.scale(2), mat.matvec(other), mat.matmul(mat), mat.transpose(), mat.copy()]\n"
           + "}\n"
           + "[work(v, m, w), work.parallel(v, m, w)]"));
   var expected=assertInstanceOf(ProtosArrayValue.class,pair.indexedAt(0)).indexedSnapshot();
   var f=assertInstanceOf(ProtosFutureValue.class,pair.indexedAt(1));
   while(f.isPending()){dispatchHosted(h,d);Thread.onSpinWait();}
   assertEquals(ProtosFutureValue.State.RESOLVED,f.state(),
       () -> "P failed with "+standardErrorName(h.activation(),f));
   var actual=assertInstanceOf(ProtosArrayValue.class,f.resolvedValue().orElseThrow()).indexedSnapshot();
   assertEquals(expected.size(),actual.size());
   // One worker-side identity returned twice stays one caller-side identity.
   assertSame(actual.get(0),actual.get(1));
   for(int index=0;index<expected.size();index++){
    Object want=expected.get(index);
    Object got=actual.get(index);
    assertNotSame(want,got);
    assertSame(((ProtosObjectValue)want).parent().orElseThrow(),((ProtosObjectValue)got).parent().orElseThrow());
    assertTrue(((ProtosObjectValue)got).isOpen());
    List<Object> wantElements;
    List<Object> gotElements;
    if(want instanceof ProtosVectorValue wantVector){
     var gotVector=assertInstanceOf(ProtosVectorValue.class,got,"result "+index);
     wantElements=wantVector.indexedSnapshot();
     gotElements=gotVector.indexedSnapshot();
    }else{
     var wantMatrix=assertInstanceOf(ProtosMatrixValue.class,want);
     var gotMatrix=assertInstanceOf(ProtosMatrixValue.class,got,"result "+index);
     assertTrue(ProtosIdentity.identical(wantMatrix.rowsValue(),gotMatrix.rowsValue()));
     assertTrue(ProtosIdentity.identical(wantMatrix.columnsValue(),gotMatrix.columnsValue()));
     wantElements=wantMatrix.indexedSnapshot();
     gotElements=gotMatrix.indexedSnapshot();
    }
    assertEquals(wantElements.size(),gotElements.size());
    for(int element=0;element<wantElements.size();element++){
     Object value=gotElements.get(element);
     assertTrue(ProtosNumericValueSupport.isCurrentNumber(value),"result "+index+" element "+element);
     assertTrue(ProtosIdentity.identical(wantElements.get(element),value),"result "+index+" element "+element);
    }
   }
   dispatchHosted(h,d);
   assertEquals(0,d.liveTaskCount());
  }
 }

 /* Names the standard Error family of a FAILED Future, for an assertion message only. */
 private static String standardErrorName(ProtosActivation a,ProtosFutureValue f){
  Object kind=f.failedError().map(e->e.parent().orElse(null)).orElse(null);
  for(var standard:ProtosCoreErrors.StandardError.values()){
   if(kind==ProtosCoreErrors.prototype(a,standard))return standard.prototypeName();
  }
  return "a non-standard Error";
 }

 private static ProtosObjectValue linearAlgebraMember(ProtosPrelude p,String name){
  ProtosObjectValue context=p.newExecutionContext();
  p.installStandardModuleMembersForRuntime(
      new com.guillermomolina.protos.runtime.modules.ProtosModuleKey("std:math/LinearAlgebra"),context);
  return (ProtosObjectValue)context.readLocalSlot(name).orElseThrow();
 }
}

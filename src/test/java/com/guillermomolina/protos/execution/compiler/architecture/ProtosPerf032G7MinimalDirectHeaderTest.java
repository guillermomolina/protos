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

import com.guillermomolina.protos.execution.ProtosDispatchTestSupport.DirectEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I102 (PLAT060-B5) uniform header for the former PERF032-G7 minimal shapes.
 *
 * <p>The four zero-argument direct Closure call shapes PERF032-G7 once
 * shortened (A: no Task, unobservable home; B: no Task, physical home; C:
 * explicit Task, unobservable home; D: explicit Task, physical home), the
 * zero-argument immediate method call, and the rich entry that already owns
 * its activation all use the single five-slot header
 * {@code [closure|activation, receiver|direct, methodHome|task, caller,
 * returnHome, supplied...]}. Publication replaces argument 0 only.
 */
final class ProtosPerf032G7MinimalDirectHeaderTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void variantAUsesB5WithTheUnobservableMarker() throws Exception {
        withCore(module -> {
            ProtosClosureValue literal = parsedClosure("() => { 1 }", "g7-a.protos", module);
            DirectEntry prepared = fastDirect(literal, module);
            Object[] arguments = prepared.targetArguments();

            assertEquals(ProtosFrameArguments.HEADER_LENGTH, arguments.length);
            assertSame(literal, arguments[0]);
            assertNull(arguments[2], "no explicit Task");
            assertSame(module, arguments[3]);
            assertSame(ProtosReturnHome.unobservable(), arguments[4]);
            assertSame(ProtosReturnHome.unobservable(),
                    ProtosFrameArguments.compactReturnHome(arguments));
            assertTrue(ProtosFrameArguments.compactOwnsReturnHome(arguments));
            assertSame(module, ProtosFrameArguments.compactCaller(arguments));
            assertNull(ProtosFrameArguments.compactTask(arguments));
            assertEquals(0, ProtosFrameArguments.compactSuppliedArgumentCount(arguments));
            assertSame(module, ProtosFrameArguments.compactInheritedProvenanceCaller(arguments));
            assertFalse(ProtosFrameArguments.hasPublishedActivation(arguments),
                    "no activation exists before the callee runs");

            assertEquals("1", integerValue(enter(prepared)));
            assertSame(literal, arguments[0], "execution creates no eager activation");
            assertTrue(ProtosFrameArguments.isUnmaterializedCompactCall(arguments));
            assertTrue(ProtosFrameArguments.isUnmaterializedCompactScalarLocalCall(arguments));

            ProtosActivation callee = ProtosFrameArguments.activation(arguments);
            assertSame(callee, arguments[0], "the activation is published into argument 0");
            assertSame(callee, ProtosFrameArguments.activation(arguments));
            assertEquals(ProtosFrameArguments.HEADER_LENGTH, arguments.length);
            assertSame(module, arguments[3], "publication rewrites argument 0 only");
            assertSame(ProtosReturnHome.unobservable(), callee.returnHome().orElseThrow());
            assertTrue(callee.task().isEmpty());
            assertEquals(0, callee.suppliedArgumentCountForRuntime());
            assertFalse(ProtosFrameArguments.isUnmaterializedCompactCall(arguments));
            assertFalse(ProtosFrameArguments.isUnmaterializedCompactScalarLocalCall(arguments));
            assertTrue(ProtosFrameArguments.hasActivation(arguments));
            assertEquals(0, ProtosFrameArguments.compactSuppliedArgumentCount(arguments));
            assertSame(module, ProtosFrameArguments.compactCaller(arguments));
        });
        System.out.println("PERF032_G7_VARIANT_A=PASS");
    }

    @Test
    void variantBKeepsTheExactPhysicalHome() throws Exception {
        withCore(module -> {
            ProtosClosureValue returning =
                    parsedClosure("() => { ^ 42 }", "g7-b.protos", module);
            DirectEntry prepared = fastDirect(returning, module);
            Object[] arguments = prepared.targetArguments();

            assertEquals(ProtosFrameArguments.HEADER_LENGTH, arguments.length);
            assertSame(returning, arguments[0]);
            assertNull(arguments[2], "no explicit Task");
            assertSame(module, arguments[3]);
            ProtosReturnHome home = assertInstanceOf(ProtosReturnHome.class, arguments[4]);
            assertNotSame(ProtosReturnHome.unobservable(), home);
            assertSame(home, ProtosFrameArguments.compactReturnHome(arguments));
            assertSame(home, ProtosFrameArguments.compactReturnHome(arguments),
                    "reconstruction never recomputes a home");
            assertTrue(home.isActive());

            assertEquals("42", integerValue(enter(prepared)));
            assertFalse(home.isActive());
            ProtosActivation callee = ProtosFrameArguments.activation(arguments);
            assertSame(home, callee.returnHome().orElseThrow());
            assertSame(callee, ProtosFrameArguments.activation(arguments));
        });
        System.out.println("PERF032_G7_VARIANT_B=PASS");
    }

    @Test
    void variantCKeepsTheExactTaskWithTheUnobservableMarker() throws Exception {
        withCore(module -> {
            ProtosClosureValue literal = parsedClosure("() => { 1 }", "g7-c.protos", module);
            AtomicReference<Object[]> argumentsRef = new AtomicReference<>();
            AtomicReference<ProtosTask> currentRef = new AtomicReference<>();
            ProtosTask task =
                    module.executionDomain().createTask(null, current -> {
                        // I115: the Task-owned entry is made by the shared dispatcher,
                        // which writes the B5 header; it is inspected after the call.
                        ProtosCallRequest prepared =
                                ProtosCallRequest.taskEntry(literal, current, List.of(), module);
                        argumentsRef.set(prepared.arguments());
                        currentRef.set(current);
                        assertSame(current, prepared.arguments()[2]);
                        ProtosBytecodeTaskExecution.executeRequest(current, prepared);
                    });
            assertTrue(module.executionDomain().dispatchOne());

            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertEquals("1", integerValue(task.result().orElseThrow()));
            Object[] arguments = argumentsRef.get();
            assertEquals(ProtosFrameArguments.HEADER_LENGTH, arguments.length);
            assertSame(task, arguments[2]);
            assertSame(module, arguments[3]);
            assertSame(ProtosReturnHome.unobservable(),
                    ProtosFrameArguments.compactReturnHome(arguments));
            assertSame(module, ProtosFrameArguments.compactCaller(arguments));
            ProtosActivation callee = ProtosFrameArguments.activation(arguments);
            assertSame(task, callee.task().orElseThrow());
            assertSame(task, ProtosFrameArguments.compactTask(arguments));
            assertSame(ProtosReturnHome.unobservable(), callee.returnHome().orElseThrow());
            assertTrue(module.task().isEmpty(), "the creator is never mutated");
        });
        System.out.println("PERF032_G7_VARIANT_C=PASS");
    }

    @Test
    void variantDKeepsTheExactTaskAndPhysicalHome() throws Exception {
        withCore(module -> {
            ProtosClosureValue returning =
                    parsedClosure("() => { ^ 42 }", "g7-d.protos", module);
            AtomicReference<Object[]> argumentsRef = new AtomicReference<>();
            AtomicReference<ProtosReturnHome> homeRef = new AtomicReference<>();
            ProtosTask task =
                    module.executionDomain().createTask(null, current -> {
                        // I115: Task-owned entry through the shared dispatcher.
                        ProtosCallRequest prepared =
                                ProtosCallRequest.taskEntry(returning, current, List.of(), module);
                        Object[] arguments = prepared.arguments();
                        argumentsRef.set(arguments);
                        assertSame(current, arguments[2]);
                        ProtosBytecodeTaskExecution.executeRequest(current, prepared);
                        assertEquals(ProtosFrameArguments.HEADER_LENGTH, arguments.length);
                        assertSame(module, arguments[3]);
                        ProtosReturnHome home =
                                assertInstanceOf(ProtosReturnHome.class, arguments[4]);
                        assertNotSame(ProtosReturnHome.unobservable(), home);
                        assertSame(home, ProtosFrameArguments.compactReturnHome(arguments));
                        homeRef.set(home);
                    });
            assertTrue(module.executionDomain().dispatchOne());

            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertEquals("42", integerValue(task.result().orElseThrow()));
            ProtosActivation callee = ProtosFrameArguments.activation(argumentsRef.get());
            assertSame(task, callee.task().orElseThrow());
            assertSame(homeRef.get(), callee.returnHome().orElseThrow());
            assertFalse(homeRef.get().isActive());
        });
        System.out.println("PERF032_G7_VARIANT_D=PASS");
    }

    @Test
    void variantAScopeObservationMaterializesOneActivation() throws Exception {
        withCore(module -> {
            ProtosClosureValue literal = parsedClosure("() => { 1 }", "g7-scope-a.protos", module);
            Object[] arguments =
                    ProtosFrameArguments.compactDirectClosureCall(
                            literal, module, null, new Object[0]);
            assertEquals(ProtosFrameArguments.HEADER_LENGTH, arguments.length);

            ProtosActivation observed = observeScopeTwice(literal, arguments);
            assertSame(module, ProtosFrameArguments.compactCaller(arguments));
            assertTrue(observed.task().isEmpty());
            assertSame(ProtosReturnHome.unobservable(), observed.returnHome().orElseThrow());
        });
        System.out.println("PERF032_G7_SCOPE_A=PASS");
    }

    @Test
    void variantDScopeObservationMaterializesOneActivation() throws Exception {
        withCore(module -> {
            ProtosClosureValue returning =
                    parsedClosure("() => { ^ 42 }", "g7-scope-d.protos", module);
            AtomicReference<Object[]> argumentsRef = new AtomicReference<>();
            ProtosTask task =
                    module.executionDomain().createTask(null, current -> {
                        Object[] arguments =
                                ProtosFrameArguments.compactDirectClosureCall(
                                        returning, module, current, new Object[0]);
                        assertEquals(ProtosFrameArguments.HEADER_LENGTH, arguments.length);
                        argumentsRef.set(arguments);
                    });
            assertTrue(module.executionDomain().dispatchOne());

            Object[] arguments = argumentsRef.get();
            ProtosReturnHome home = assertInstanceOf(ProtosReturnHome.class, arguments[4]);
            ProtosActivation observed = observeScopeTwice(returning, arguments);
            assertSame(module, ProtosFrameArguments.compactCaller(arguments));
            assertSame(task, observed.task().orElseThrow());
            assertSame(task, ProtosFrameArguments.compactTask(arguments));
            assertSame(home, observed.returnHome().orElseThrow());
        });
        System.out.println("PERF032_G7_SCOPE_D=PASS");
    }

    /**
     * Observes the NodeLibrary scope of a zero-argument B5 frame twice and
     * returns the one activation both observations published.
     */
    private static ProtosActivation observeScopeTwice(
            ProtosClosureValue closure, Object[] arguments) throws Exception {
        int length = arguments.length;
        VirtualFrame frame =
                Truffle.getRuntime()
                        .createVirtualFrame(arguments, FrameDescriptor.newBuilder().build());
        assertTrue(ProtosBytecodeTagTreeNodeExports.hasScope(null, frame));
        assertSame(closure, arguments[0], "hasScope does not materialize");

        ProtosBytecodeTagTreeNodeExports.getScope(null, frame, true);
        ProtosActivation observed = assertInstanceOf(ProtosActivation.class, arguments[0]);
        ProtosBytecodeTagTreeNodeExports.getScope(null, frame, true);
        assertSame(observed, arguments[0], "a second observation reuses the activation");
        assertSame(observed, ProtosFrameArguments.activation(frame));
        assertEquals(length, arguments.length);
        assertEquals(0, observed.suppliedArgumentCountForRuntime());
        assertTrue(observed.suppliedArgumentsForRuntime().isEmpty());
        assertEquals(0, ProtosFrameArguments.compactSuppliedArgumentCount(arguments));
        return observed;
    }

    @Test
    void capturedHomeOwnershipIsPreserved() throws Exception {
        withCore(module -> {
            ProtosClosureValue escaped =
                    assertInstanceOf(ProtosClosureValue.class,
                            evaluate("make: () => { () => { ^ 1 } }\nmake()\n",
                                    "g7-captured.protos", module));
            ProtosReturnHome captured = escaped.returnHome().orElseThrow();
            Object[] arguments =
                    ProtosFrameArguments.compactDirectClosureCall(
                            escaped, module, null, new Object[0]);
            assertEquals(ProtosFrameArguments.HEADER_LENGTH, arguments.length);
            assertSame(captured, arguments[4]);
            assertSame(captured, ProtosFrameArguments.compactReturnHome(arguments));
            assertFalse(ProtosFrameArguments.compactOwnsReturnHome(arguments));
            assertSame(captured,
                    ProtosFrameArguments.activation(arguments).returnHome().orElseThrow());
        });
    }

    @Test
    void normalNestedInvocationDoesNotCloseItsActiveCapturedHome() throws Exception {
        withCore(module -> {
            ProtosReturnHome home = new ProtosReturnHome();
            ProtosClosureValue plain = parsedClosure("(value) => { value }", "g7-active-capture.protos", module);
            ProtosClosureValue captured = new ProtosClosureValue(
                    plain.definition(), module.lexicalContextsForClosureCapture(),
                    module.receiver(), module.methodHome().orElse(null), home,
                    module.prelude().orElseThrow(), plain.executionPlan().orElseThrow());
            ProtosCallRequest request = ProtosCallRequest.call(captured, List.of(integer(23)), module);
            assertEquals("23", integerValue(request.callUncached()));
            Object[] arguments = request.arguments();
            assertEquals(ProtosFrameArguments.HEADER_LENGTH + 1, arguments.length);
            assertSame(home, arguments[4]);
            assertTrue(home.isActive());
            assertFalse(ProtosFrameArguments.compactOwnsReturnHome(arguments));
            ProtosActivation observed = ProtosFrameArguments.activation(arguments);
            assertSame(home, observed.returnHome().orElseThrow());
            assertSame(module, arguments[3]);
            assertTrue(home.isActive());
            home.closeOwned();
        });
    }

    @Test
    void suspendedB5KeepsItsCallerTaskAndOwnedHomeThroughCompletionOrCancellation() throws Exception {
        for (boolean cancel : new boolean[] {false, true}) {
            withCore(module -> {
                var domain = module.executionDomain();
                var prelude = module.prelude().orElseThrow();
                ProtosFutureValue dependency = new ProtosFutureValue(prelude.futurePrototype(), domain);
                module.context().createLocalSlot("dependency", dependency);
                ProtosClosureValue closure = parsedClosure(
                        "() => { dependency.value()\n ^17 }", "g7-pending-home.protos", module);
                AtomicReference<ProtosCallRequest> saved = new AtomicReference<>();
                ProtosTask task = domain.createTask(null, current -> {
                    ProtosCallRequest request = ProtosCallRequest.taskEntry(closure, current, List.of(), module);
                    saved.set(request);
                    assertFalse(ProtosFrameArguments.hasPublishedActivation(request.arguments()));
                    ProtosBytecodeTaskExecution.executeRequest(current, request);
                });
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state());
                Object[] arguments = saved.get().arguments();
                ProtosReturnHome home = ProtosFrameArguments.compactReturnHome(arguments);
                assertTrue(home.isMaterialized());
                assertTrue(home.isActive());
                assertSame(task, home.ownerTaskForRuntime());
                assertSame(task, ProtosFrameArguments.compactTask(arguments));
                assertSame(module, ProtosFrameArguments.compactCaller(arguments));
                if (cancel) assertTrue(task.requestCancellation());
                else assertTrue(dependency.resolve(ProtosNullValue.INSTANCE, module));
                assertTrue(domain.dispatchOne());
                assertEquals(cancel ? ProtosTask.State.CANCELLED : ProtosTask.State.COMPLETED, task.state());
                if (!cancel) assertEquals("17", integerValue(task.result().orElseThrow()));
                assertFalse(home.isActive());
                assertSame(arguments, saved.get().arguments());
                assertSame(home, ProtosFrameArguments.compactReturnHome(arguments));
                assertSame(module, ProtosFrameArguments.compactCaller(arguments));
                assertSame(task, ProtosFrameArguments.compactTask(arguments));
            });
        }
    }

    @Test
    void suppliedArgumentsAndImmediateMethodsUseTheSameHeader() throws Exception {
        withCore(module -> {
            ProtosClosureValue identity =
                    parsedClosure("(value) => { value }", "g7-full.protos", module);
            Long seven = integer(7);
            DirectEntry prepared =
                    fastDirect(identity, module, seven);
            Object[] direct = prepared.targetArguments();
            assertEquals(6, direct.length);
            assertSame(module, direct[3]);
            assertEquals(1, ProtosFrameArguments.compactSuppliedArgumentCount(direct));
            assertSame(seven, ProtosFrameArguments.compactSuppliedArgument(direct, 0));
            assertEquals("7", integerValue(enter(prepared)));

            ProtosClosureValue literal = parsedClosure("() => { 1 }", "g7-method.protos", module);
            ProtosObjectValue methodHome = new ProtosObjectValue(literal);
            Object[] method =
                    ProtosFrameArguments.compactImmediateMethodCall(
                            literal, literal, methodHome, module, new Object[0]);

            assertEquals(ProtosFrameArguments.HEADER_LENGTH, method.length);
            assertSame(literal, method[0]);
            assertSame(literal, method[1]);
            assertSame(methodHome, method[2]);
            assertSame(module, method[3]);
            assertSame(ProtosReturnHome.unobservable(), method[4]);
            assertSame(module, ProtosFrameArguments.compactCaller(method));
            assertSame(
                    ProtosReturnHome.unobservable(),
                    ProtosFrameArguments.compactReturnHome(method));
            assertEquals(0, ProtosFrameArguments.compactSuppliedArgumentCount(method));

            ProtosActivation published = ProtosFrameArguments.activation(method);
            assertSame(literal, published.receiver());
            assertSame(published, method[0]);
            assertSame(published, ProtosFrameArguments.activation(method));
            assertEquals(ProtosFrameArguments.HEADER_LENGTH, method.length);
            assertSame(literal, method[1]);
            assertSame(methodHome, method[2]);
            assertSame(module, ProtosFrameArguments.compactCaller(method));
            assertSame(
                    ProtosReturnHome.unobservable(),
                    ProtosFrameArguments.compactReturnHome(method));
            assertFalse(
                    ProtosFrameArguments.isUnmaterializedInheritingFullHeader(method));
        });
    }

    @Test
    void perf038HDirectCallZeroOnePreparedEntries() throws Exception {
        withCore(module -> {
            ProtosClosureValue literal =
                    parsedClosure("() => { 1 }", "perf038h-call01.protos", module);
            RootCallTarget target = fastDirect(literal, module).target();

            DirectEntry zero =
                    ProtosDispatchTestSupport.direct(literal, module);

            assertSame(target, zero.target());
            assertEquals(ProtosFrameArguments.HEADER_LENGTH, zero.targetArguments().length);
            assertSame(literal, zero.targetArguments()[0]);
            assertNull(zero.targetArguments()[2]);
            assertSame(module, zero.targetArguments()[3]);
            assertSame(
                    ProtosReturnHome.unobservable(),
                    ProtosFrameArguments.compactReturnHome(
                            zero.targetArguments()));

            Long value = integer(9);
            DirectEntry one =
                    ProtosDispatchTestSupport.direct(literal, module, value);

            // D203/I105: excess arguments enter the ordinary body root.
            assertSame(target, one.target());
            assertEquals(6, one.targetArguments().length);
            assertSame(literal, one.targetArguments()[0]);
            assertSame(module, one.targetArguments()[3]);
            assertSame(value, one.targetArguments()[5]);
            assertEquals(
                    1,
                    ProtosFrameArguments.compactSuppliedArgumentCount(
                            one.targetArguments()));

            ProtosClosureValue returning =
                    parsedClosure(
                            "() => { ^ 42 }",
                            "perf038h-call0-home.protos",
                            module);
            RootCallTarget returningTarget =
                    fastDirect(returning, module).target();
            DirectEntry owned =
                    ProtosDispatchTestSupport.direct(returning, module);

            assertEquals(ProtosFrameArguments.HEADER_LENGTH, owned.targetArguments().length);
            ProtosReturnHome physicalHome =
                    assertInstanceOf(
                            ProtosReturnHome.class,
                            owned.targetArguments()[4]);
            assertNotSame(ProtosReturnHome.unobservable(), physicalHome);
            // I115: the callee root owns and closes this materialized home.
            assertSame(
                    physicalHome,
                    ProtosFrameArguments.ownedReturnHomeOrNull(owned.targetArguments()));
        });
    }

    @Test
    void zeroParameterExcessArgumentsEnterTheBodyRoot() throws Exception {
        withCore(module -> {
            ProtosClosureValue literal = parsedClosure("() => { 1 }", "g7-arity.protos", module);
            RootCallTarget normal = fastDirect(literal, module).target();
            DirectEntry excess =
                    fastDirect(literal, module, integer(1));
            assertEquals(6, excess.targetArguments().length);
            // D203/I105: no arity-rejection root exists; the B5 header is unchanged.
            assertSame(normal, excess.target());
        });
    }

    @Test
    void richActivationArraysAreNotCompact() throws Exception {
        withCore(module -> {
            Object[] rich = {module};
            assertFalse(ProtosFrameArguments.isUnmaterializedCompactCall(rich));
            assertTrue(ProtosFrameArguments.hasActivation(rich));
            assertSame(module, ProtosFrameArguments.activation(rich));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.compactOwnsReturnHome(rich));
        });
    }

    /**
     * I102: an ordinary entry that already owns its activation uses the same
     * B5 header, published from the start: no synthesized caller, the exact
     * return home, and the supplied values at the fixed offset.
     */
    @Test
    void richOrdinaryEntryUsesThePublishedB5Header() throws Exception {
        withCore(module -> {
            ProtosClosureValue identity =
                    parsedClosure("(value) => { value }", "i102-rich.protos", module);
            Long seven = integer(7);
            DirectEntry compact =
                    fastDirect(identity, module, seven);
            ProtosActivation activation =
                    ProtosFrameArguments.activation(
                            ProtosFrameArguments.compactDirectClosureCall(
                                    identity, module, null, new Object[] {seven}));

            DirectEntry rich =
                    ProtosDispatchTestSupport.rich(compact.target(), activation);
            Object[] arguments = rich.targetArguments();

            assertEquals(ProtosFrameArguments.HEADER_LENGTH + 1, arguments.length);
            assertSame(activation, arguments[0]);
            assertTrue(ProtosFrameArguments.hasPublishedActivation(arguments));
            assertFalse(ProtosFrameArguments.isUnmaterializedCompactCall(arguments));
            assertNull(arguments[3], "no caller is synthesized for a published entry");
            assertSame(activation.returnHome().orElseThrow(), arguments[4]);
            assertEquals(1, ProtosFrameArguments.compactSuppliedArgumentCount(arguments));
            assertSame(seven, ProtosFrameArguments.compactSuppliedArgument(arguments, 0));
            if (activation.methodHome().isPresent()) {
                assertSame(activation.receiver(), arguments[1]);
                assertSame(activation.methodHome().orElseThrow(), arguments[2]);
            } else {
                assertNull(arguments[2], "no explicit Task is claimed");
            }
            assertSame(activation, ProtosFrameArguments.activation(arguments));
            assertNull(activation.materializedContextOrNullForRuntime(),
                    "building the header materializes no Context");

            // Published-state readers take the activation, never the null caller slot.
            assertSame(activation.preludeOrNullForRuntime(),
                    ProtosFrameArguments.preludeOrNull(arguments));
            assertSame(activation.task().orElse(null),
                    ProtosFrameArguments.compactTask(arguments));
            assertNull(ProtosFrameArguments.compactInheritedProvenanceCaller(arguments));
            assertFalse(ProtosFrameArguments.isUnmaterializedInheritingFullHeader(arguments));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.compactCaller(arguments),
                    "the absent caller is never treated as a caller");

            assertEquals("7", integerValue(enter(rich)));
            assertSame(activation, arguments[0], "the activation identity is preserved");
            assertSame(activation.returnHome().orElseThrow(), arguments[4],
                    "the invocation's exact return home is kept");

            // A module activation is a real activation but not a Closure invocation.
            IllegalStateException rejected =
                    assertThrows(IllegalStateException.class,
                            () -> ProtosFrameArguments.publishedInvocation(
                                    module,
                                    compact.target()));
            assertTrue(rejected.getMessage().contains("return home"), rejected.getMessage());
        });
    }

    @Test
    void perf038HLiteralOnlyCallHasProvenNonSuspendingEntry()
            throws Exception {
        withCore(module -> {
            ProtosClosureValue constant =
                    parsedClosure(
                            "() => { 1 }",
                            "perf038h-literal-proof.protos",
                            module);

            DirectEntry prepared =
                    assertInstanceOf(
                            DirectEntry.class,
                            fastDirect(constant, module));

            assertTrue(provablyNonSuspending(prepared));

            assertEquals("1", integerValue(enter(prepared)));
        });
    }

    @Test
    void perf038HDynamicCallsKeepGeneralAndSimpleParametersUseProof()
            throws Exception {
        withCore(module -> {
            ProtosClosureValue withSend =
                    parsedClosure(
                            "(x) => { x + 2 }",
                            "perf038h-dynamic-body.protos",
                            module);

            DirectEntry dynamic =
                    assertInstanceOf(
                            DirectEntry.class,
                            fastDirect(withSend, module, integer(1)));

            assertFalse(provablyNonSuspending(dynamic));
            assertEquals("3", integerValue(enter(dynamic)));

            ProtosClosureValue folded =
                    parsedClosure(
                            "() => { 1 + 2 }",
                            "perf038h-folded-body.protos",
                            module);
            DirectEntry foldedCall =
                    assertInstanceOf(
                            DirectEntry.class,
                            fastDirect(folded, module));
            assertTrue(
                    provablyNonSuspending(foldedCall),
                    "Constant-folded arithmetic admits the static proof");
            assertEquals("3", integerValue(enter(foldedCall)));

            ProtosClosureValue withParameter =
                    parsedClosure(
                            "(x) => { 1 }",
                            "perf038h-parameter-body.protos",
                            module);

            DirectEntry parameterCall =
                    assertInstanceOf(
                            DirectEntry.class,
                            fastDirect(withParameter, module, integer(9)));

            assertTrue(provablyNonSuspending(parameterCall));
            assertEquals("1", integerValue(enter(parameterCall)));

            ProtosClosureValue identity =
                    parsedClosure(
                            "(x) => { x }",
                            "perf038h-parameter-read.protos",
                            module);

            DirectEntry identityCall =
                    assertInstanceOf(
                            DirectEntry.class,
                            fastDirect(identity, module, integer(7)));

            assertTrue(provablyNonSuspending(identityCall));
            assertEquals("7", integerValue(enter(identityCall)));

            ProtosClosureValue twoParameters =
                    parsedClosure(
                            "(x, y) => { x }",
                            "perf038h-two-parameters.protos",
                            module);

            DirectEntry twoArgumentCall =
                    assertInstanceOf(
                            DirectEntry.class,
                            fastDirect(
                                    twoParameters, module,
                                    integer(8), integer(9)));

            assertTrue(provablyNonSuspending(twoArgumentCall));
            assertEquals("8", integerValue(enter(twoArgumentCall)));

            ProtosClosureValue dynamicParameter =
                    parsedClosure(
                            "(x) => { x + 1 }",
                            "perf038h-dynamic-parameter.protos",
                            module);

            DirectEntry dynamicParameterCall =
                    assertInstanceOf(
                            DirectEntry.class,
                            fastDirect(dynamicParameter, module, integer(2)));

            assertFalse(provablyNonSuspending(dynamicParameterCall));
            assertEquals("3", integerValue(enter(dynamicParameterCall)));
        });
    }

    @Test
    void perf038HStraightLineInvocationPreservesDirectResults()
            throws Exception {
        withCore(module -> {
            ProtosClosureValue literal =
                    parsedClosure(
                            "() => { 1 }",
                            "perf038h-straight-line-zero.protos",
                            module);

            DirectEntry zero =
                    assertInstanceOf(
                            DirectEntry.class,
                            fastDirect(literal, module));

            assertTrue(
                    admitsStraightLine(zero));
            assertEquals("1", integerValue(
                            enter(zero)));

            ProtosClosureValue identity =
                    parsedClosure(
                            "(x) => { x }",
                            "perf038h-straight-line-one.protos",
                            module);

            DirectEntry one =
                    assertInstanceOf(
                            DirectEntry.class,
                            fastDirect(identity, module, integer(7)));

            assertTrue(
                    admitsStraightLine(one));
            assertEquals("7", integerValue(
                            enter(one)));
        });
    }

    @Test
    void perf038HStraightLineRejectsDynamicAndReturnEntries()
            throws Exception {
        withCore(module -> {
            ProtosClosureValue dynamic =
                    parsedClosure(
                            "(x) => { x + 2 }",
                            "perf038h-straight-line-dynamic.protos",
                            module);

            assertFalse(
                    admitsStraightLine(
                                    assertInstanceOf(
        DirectEntry.class,
        fastDirect(dynamic, module, integer(1)))));

            ProtosClosureValue folded =
                    parsedClosure(
                            "() => { 1 + 2 }",
                            "perf038h-straight-line-folded.protos",
                            module);
            assertTrue(
                    admitsStraightLine(
                                    assertInstanceOf(
                                            DirectEntry.class,
                                            fastDirect(folded, module))),
                    "Constant-folded arithmetic admits straight-line execution");

            ProtosClosureValue returning =
                    parsedClosure(
                            "() => { ^ 42 }",
                            "perf038h-straight-line-return.protos",
                            module);

            assertFalse(
                    admitsStraightLine(
                                    assertInstanceOf(
        DirectEntry.class,
        fastDirect(returning, module))));

            ProtosClosureValue literal =
                    parsedClosure(
                            "() => { 1 }",
                            "perf038h-straight-line-arity.protos",
                            module);

            // D203/I105: an excess argument enters the same straight-line root.
            assertTrue(
                    admitsStraightLine(
                                    assertInstanceOf(
        DirectEntry.class,
        fastDirect(
                                            literal, module, integer(3)))));
        });
    }

    @Test
    void perf038HFusedSendOnePreservesRebindingAndFallback()
            throws Exception {
        withCore(module -> {
            Object result = evaluate(
                    "receiver: { identity: (value) => { value } }\n"
                            + "run: (value) => { receiver.identity(value) }\n"
                            + "first: run(1)\n"
                            + "second: run(2)\n"
                            + "receiver.identity = (value) => { value + 10 }\n"
                            + "third: run(3)\n"
                            + "first * 100 + second * 10 + third\n",
                    "perf038h-fused-send-one.protos",
                    module);

            assertEquals("133", integerValue(result));
        });
    }

    @Test
    void perf038HFusedCallZeroAndOnePreserveResults()
            throws Exception {
        withCore(module -> {
            Object result = evaluate(
                    "constant: () => { 1 }\n"
                            + "identity: (x) => { x }\n"
                            + "constant() * 100"
                            + " + identity(2) * 10"
                            + " + identity(3)\n",
                    "perf038h-fused-call-arity.protos",
                    module);

            assertEquals("123", integerValue(result));
        });
    }

    @Test
    void perf038HFusedCallsRespectArityAndDynamicBodies()
            throws Exception {
        withCore(module -> {
            Object result = evaluate(
                    "zero: () => { 4 }\n"
                            + "dynamic: (x) => { x + 1 }\n"
                            + "zero() * 10 + dynamic(2)\n",
                    "perf038h-fused-call-fallback.protos",
                    module);

            assertEquals("43", integerValue(result));
        });
    }

    @Test
    void perf038HFusedSendZeroPreservesSelectionAndRebinding()
            throws Exception {
        withCore(module -> {
            Object result = evaluate(
                    "receiver: { value: () => { 7 } }\n"
                            + "run: () => { receiver.value() }\n"
                            + "first: run()\n"
                            + "second: run()\n"
                            + "receiver.value = () => { 2 + 3 }\n"
                            + "third: run()\n"
                            + "receiver.value = () => { 9 }\n"
                            + "fourth: run()\n"
                            + "[first, second, third, fourth]\n",
                    "perf038h-fused-send-zero.protos",
                    module);

            java.util.List<Object> values =
                    assertInstanceOf(
                            ProtosArrayValue.class, result).indexedSnapshot();

            assertEquals(4, values.size());
            assertEquals("7", integerValue(values.get(0)));
            assertEquals("7", integerValue(values.get(1)));
            assertEquals("5", integerValue(values.get(2)));
            assertEquals("9", integerValue(values.get(3)));
        });
    }

    @Test
    void perf038HSendAdmissionIgnoresExcessArity() throws Exception {
        withCore(module -> {
            Object receiver = evaluate(
                    "receiver: {\n"
                            + "    zero: () => { 7 }\n"
                            + "    one: (x) => { x }\n"
                            + "}\n"
                            + "receiver\n",
                    "perf038h-send-admission.protos",
                    module);

            // I115: GetMethod selects a bound source method; the shared
            // dispatcher's function path admits it whatever the arity.
            ProtosBoundMethod zero =
                    assertInstanceOf(
                            ProtosBoundMethod.class,
                            ProtosExperimentalGetMethodNode.select(receiver, "zero", null, module));
            ProtosBoundMethod one =
                    assertInstanceOf(
                            ProtosBoundMethod.class,
                            ProtosExperimentalGetMethodNode.select(receiver, "one", null, module));

            assertTrue(
                    ProtosFunctionCallDispatchers.CallNode.isSourceFunction(zero.callable()),
                    "zero-argument source method must be admitted");
            assertEquals("7", integerValue(ProtosFunctionCallDispatchers.callUncached(
                            zero,
                            ProtosFrameArguments.functionCallArguments(new Object[] {integer(1)}),
                            module)),
                    "D203/I105: Send1 to a zero-argument method enters its ordinary root");
            assertTrue(
                    ProtosFunctionCallDispatchers.CallNode.isSourceFunction(one.callable()),
                    "one-argument source method must be admitted");
        });
    }

    private interface CoreTest {
        void run(ProtosActivation module) throws Exception;
    }

    private static void withCore(CoreTest test) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                test.run(prelude.newModuleActivation());
            } finally {
                context.leave();
            }
        }
    }

    /** I115: the callee root's recorded non-suspension proof. */
    private static boolean provablyNonSuspending(DirectEntry entry) {
        return ((ProtosBytecodeRootNode) entry.target().getRootNode()).provablyNonSuspendingBody();
    }

    /**
     * I115: the former straight-line admission as properties of the current
     * architecture: a provably non-suspending root whose invocation owns no
     * materialized ReturnHome.
     */
    private static boolean admitsStraightLine(DirectEntry entry) {
        return provablyNonSuspending(entry)
                && ProtosFrameArguments.ownedReturnHomeOrNull(entry.targetArguments()) == null;
    }

    private static DirectEntry fastDirect(
            ProtosClosureValue closure, ProtosActivation caller, Object... supplied) {
        // I115: the B5 header the shared dispatcher writes for a function call.
        return ProtosDispatchTestSupport.direct(closure, caller, supplied);
    }

    private static Object enter(DirectEntry prepared) {
        return ProtosDispatchTestSupport.enter(prepared);
    }

    private static Object evaluate(String characters, String name, ProtosActivation activation) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, name)
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        CallTarget target = ProtosLanguageContext.current().parsePublic(source);
        return target.call(activation);
    }

    private static ProtosClosureValue parsedClosure(
            String characters, String name, ProtosActivation activation) {
        return assertInstanceOf(
                ProtosClosureValue.class, evaluate(characters, name, activation));
    }

    private static Long integer(long value) {
        return Long.valueOf(value);
    }

    private static String integerValue(Object value) {
        return com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value);
    }
}

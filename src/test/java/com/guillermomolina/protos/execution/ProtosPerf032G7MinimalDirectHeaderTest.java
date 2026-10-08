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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosReturnHome;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.Source;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF032-G7 minimal direct Closure call headers.
 *
 * <p>A direct source Closure call with zero supplied arguments carries only
 * the slots it needs: {@code [closure, caller]} (A), {@code [closure, caller,
 * returnHome]} (B), {@code [closure, task, caller]} (C) or {@code [closure,
 * task, caller, returnHome]} (D). Calls with supplied arguments and immediate
 * method calls keep the full five-slot header.
 */
final class ProtosPerf032G7MinimalDirectHeaderTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void variantAUsesTwoSlotsWithTheUnobservableMarker() throws Exception {
        withCore(module -> {
            ProtosClosureValue literal = parsedClosure("() => { 1 }", "g7-a.protos", module);
            ProtosBytecodeRootNode.PreparedClosureCall prepared = fastDirect(literal, module);
            Object[] arguments = prepared.targetArguments();

            assertEquals(2, arguments.length);
            assertSame(literal, arguments[0]);
            assertSame(module, arguments[1]);
            assertSame(ProtosReturnHome.unobservable(),
                    ProtosFrameArguments.compactReturnHome(arguments));
            assertTrue(ProtosFrameArguments.compactOwnsReturnHome(arguments));
            assertSame(module, ProtosFrameArguments.compactCaller(arguments));
            assertNull(ProtosFrameArguments.compactTask(arguments));
            assertEquals(0, ProtosFrameArguments.compactSuppliedArgumentCount(arguments));
            assertSame(module, ProtosFrameArguments.compactInheritedProvenanceCaller(arguments));
            assertThrows(IllegalStateException.class, prepared::activation);

            assertEquals(BigInteger.ONE, integerValue(enter(prepared)));
            assertSame(literal, arguments[0], "execution creates no eager activation");
            assertTrue(ProtosFrameArguments.isUnmaterializedCompactCall(arguments));
            assertTrue(ProtosFrameArguments.isUnmaterializedCompactScalarLocalCall(arguments));

            ProtosActivation callee = ProtosFrameArguments.activation(arguments);
            assertSame(callee, arguments[0], "the activation is published into argument 0");
            assertSame(callee, ProtosFrameArguments.activation(arguments));
            assertEquals(2, arguments.length);
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
            ProtosBytecodeRootNode.PreparedClosureCall prepared = fastDirect(returning, module);
            Object[] arguments = prepared.targetArguments();

            assertEquals(3, arguments.length);
            assertSame(returning, arguments[0]);
            assertSame(module, arguments[1]);
            ProtosReturnHome home = assertInstanceOf(ProtosReturnHome.class, arguments[2]);
            assertNotSame(ProtosReturnHome.unobservable(), home);
            assertSame(home, ProtosFrameArguments.compactReturnHome(arguments));
            assertSame(home, ProtosFrameArguments.compactReturnHome(arguments),
                    "reconstruction never recomputes a home");
            assertTrue(home.isActive());

            assertEquals(BigInteger.valueOf(42), integerValue(enter(prepared)));
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
            ProtosTask task =
                    module.executionDomain().createTask(null, current -> {
                        ProtosBytecodeRootNode.PreparedClosureCall prepared =
                                ProtosBytecodeRootNode.prepareTaskOwnedDirectClosureIfBytecode(
                                        literal, List.of(), module, current);
                        Object[] arguments = prepared.targetArguments();
                        argumentsRef.set(arguments);
                        assertEquals(3, arguments.length);
                        assertSame(literal, arguments[0]);
                        assertSame(current, arguments[1]);
                        assertSame(module, arguments[2]);
                        assertSame(current, ProtosFrameArguments.compactTask(arguments));
                        assertSame(module, ProtosFrameArguments.compactCaller(arguments));
                        assertSame(ProtosReturnHome.unobservable(),
                                ProtosFrameArguments.compactReturnHome(arguments));
                        assertNull(
                                ProtosFrameArguments.compactInheritedProvenanceCaller(arguments),
                                "an explicit Task is never a no-Task invocation");
                        ProtosBytecodeTaskExecution.executePreparedClosure(current, prepared);
                    });
            assertTrue(module.executionDomain().dispatchOne());

            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertEquals(BigInteger.ONE, integerValue(task.result().orElseThrow()));
            Object[] arguments = argumentsRef.get();
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
                        ProtosBytecodeRootNode.PreparedClosureCall prepared =
                                ProtosBytecodeRootNode.prepareTaskOwnedDirectClosureIfBytecode(
                                        returning, List.of(), module, current);
                        Object[] arguments = prepared.targetArguments();
                        argumentsRef.set(arguments);
                        assertEquals(4, arguments.length);
                        assertSame(returning, arguments[0]);
                        assertSame(current, arguments[1]);
                        assertSame(module, arguments[2]);
                        ProtosReturnHome home =
                                assertInstanceOf(ProtosReturnHome.class, arguments[3]);
                        assertNotSame(ProtosReturnHome.unobservable(), home);
                        assertSame(home, ProtosFrameArguments.compactReturnHome(arguments));
                        assertTrue(home.isActive());
                        homeRef.set(home);
                        ProtosBytecodeTaskExecution.executePreparedClosure(current, prepared);
                    });
            assertTrue(module.executionDomain().dispatchOne());

            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertEquals(BigInteger.valueOf(42), integerValue(task.result().orElseThrow()));
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
            assertEquals(2, arguments.length);

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
                        assertEquals(4, arguments.length);
                        argumentsRef.set(arguments);
                    });
            assertTrue(module.executionDomain().dispatchOne());

            Object[] arguments = argumentsRef.get();
            ProtosReturnHome home = assertInstanceOf(ProtosReturnHome.class, arguments[3]);
            ProtosActivation observed = observeScopeTwice(returning, arguments);
            assertSame(module, ProtosFrameArguments.compactCaller(arguments));
            assertSame(task, observed.task().orElseThrow());
            assertSame(task, ProtosFrameArguments.compactTask(arguments));
            assertSame(home, observed.returnHome().orElseThrow());
        });
        System.out.println("PERF032_G7_SCOPE_D=PASS");
    }

    /**
     * Observes the NodeLibrary scope of a minimal-header frame twice and
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
            assertEquals(3, arguments.length);
            assertSame(captured, arguments[2]);
            assertSame(captured, ProtosFrameArguments.compactReturnHome(arguments));
            assertFalse(ProtosFrameArguments.compactOwnsReturnHome(arguments));
            assertSame(captured,
                    ProtosFrameArguments.activation(arguments).returnHome().orElseThrow());
        });
    }

    @Test
    void suppliedArgumentsAndImmediateMethodsKeepTheFullHeader() throws Exception {
        withCore(module -> {
            ProtosClosureValue identity =
                    parsedClosure("(value) => { value }", "g7-full.protos", module);
            ProtosIntegerValue seven = integer(7);
            ProtosBytecodeRootNode.PreparedClosureCall prepared =
                    fastDirect(identity, module, seven);
            Object[] direct = prepared.targetArguments();
            assertEquals(6, direct.length);
            assertSame(module, direct[3]);
            assertEquals(1, ProtosFrameArguments.compactSuppliedArgumentCount(direct));
            assertSame(seven, ProtosFrameArguments.compactSuppliedArgument(direct, 0));
            assertEquals(BigInteger.valueOf(7), integerValue(enter(prepared)));

            ProtosClosureValue literal = parsedClosure("() => { 1 }", "g7-method.protos", module);
            Object[] method =
                    ProtosFrameArguments.compactImmediateMethodCall(
                            literal, literal, new ProtosObjectValue(literal), module,
                            new Object[0]);
            assertEquals(5, method.length);
            assertSame(module, ProtosFrameArguments.compactCaller(method));
            assertEquals(0, ProtosFrameArguments.compactSuppliedArgumentCount(method));
            assertSame(literal, ProtosFrameArguments.activation(method).receiver());
        });
    }

    @Test
    void zeroParameterExcessArgumentsSelectTheRejectionRoot() throws Exception {
        withCore(module -> {
            ProtosClosureValue literal = parsedClosure("() => { 1 }", "g7-arity.protos", module);
            RootCallTarget normal = fastDirect(literal, module).bodyTarget();
            RootCallTarget rejection =
                    assertInstanceOf(ProtosSemanticBytecodeRootNode.class, normal.getRootNode())
                            .arityRejectionTarget();
            ProtosBytecodeRootNode.PreparedClosureCall excess =
                    fastDirect(literal, module, integer(1));
            assertEquals(6, excess.targetArguments().length);
            assertSame(rejection, excess.bodyTarget());
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
                    () -> ProtosFrameArguments.compactReturnHome(rich));
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

    private static ProtosBytecodeRootNode.PreparedClosureCall fastDirect(
            ProtosClosureValue closure, ProtosActivation caller, Object... supplied) {
        ProtosLanguageContext entered = ProtosLanguageContext.currentIfEnteredForRuntime();
        ProtosClosureValue selected =
                ProtosBytecodeRootNode.directClosureCallSelectionOrNull(closure, caller);
        assertSame(closure, selected, "canonical direct Closure-call selection");
        RootCallTarget target =
                ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(
                        closure, entered);
        return ProtosBytecodeRootNode.PrepareClosureCallArguments.fastDirect(
                closure,
                caller,
                supplied,
                selected,
                selected.definition(),
                entered,
                selected.definition(),
                entered,
                target);
    }

    private static Object enter(ProtosBytecodeRootNode.PreparedClosureCall prepared) {
        Object entered =
                ProtosBytecodeRootNode.EnterClosureCall.ordinaryIndirect(
                        assertInstanceOf(ProtosBytecodeRootNode.OrdinarySourceCall.class, prepared),
                        IndirectCallNode.create());
        return prepared.finish(entered);
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

    private static ProtosIntegerValue integer(long value) {
        return new ProtosIntegerValue(BigInteger.valueOf(value));
    }

    private static BigInteger integerValue(Object value) {
        return assertInstanceOf(ProtosIntegerValue.class, value).value();
    }
}

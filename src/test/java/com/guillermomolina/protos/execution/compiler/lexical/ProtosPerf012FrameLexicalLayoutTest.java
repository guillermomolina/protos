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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosExecutionContextValue;
import com.guillermomolina.protos.runtime.ProtosLexicalCell;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.LocalVariable;
import com.oracle.truffle.api.source.Source;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF012, reconciled with I115: the retired frame lexical layout and its
 * ordinal binding authority are replaced by native Bytecode DSL storage. A
 * Closure root allocates exactly one root-scoped local per declared binding
 * (the value of a LOCAL binding or the shared cell of a CELL binding); a
 * module root allocates none and keeps its bindings as NAME bindings of its
 * module context. These tests prove that storage shape, its reproduction on
 * reparse, and the runtime invariants the former authority guaranteed:
 * invocation isolation, present-null versus absent, and NAME
 * removal/recreation.
 */
final class ProtosPerf012FrameLexicalLayoutTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    /**
     * Former layout-order evidence: a Closure root owns one native local per
     * declared binding, parameters and body creations alike, in declaration
     * order, and none for an undeclared name.
     */
    @Test
    void closureRootAllocatesOneNativeLocalPerDeclaredBindingInDeclarationOrder()
            throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    ProtosClosureValue closure = closureOf(
                            run(language, module, "(a, b) => {\n    c: a\n    c\n}"));
                    List<String> names = declaredLocalNames(
                            rootOf(closure), List.of("a", "b", "c", "neverDeclared"));

                    assertEquals(List.of("a", "b", "c"), names);
                });
    }

    /**
     * BUG018-C, reconciled: the local offsets of a root are fixed by its
     * lowering, and a complete Bytecode reparse (source and instrumentation
     * materialization) reproduces exactly the same named locals at the same
     * offsets.
     */
    @Test
    void reparseReproducesTheSameNativeLocalsAtTheSameOffsets() throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    ProtosClosureValue closure = closureOf(
                            run(language, module,
                                    "(x, y) => {\n    z: x\n    () => z\n}"));
                    ProtosBytecodeRootNode root = rootOf(closure);
                    List<String> before = localShape(root);

                    root.getRootNodes().ensureComplete();

                    assertEquals(before, localShape(rootOf(closure)));
                    assertTrue(before.stream().anyMatch(entry -> entry.startsWith("z@")),
                            before::toString);
                });
    }

    /**
     * A module root keeps its declarations as NAME bindings: it allocates no
     * native local for them, and two invocations of the same root keep fully
     * independent module contexts.
     */
    @Test
    void multipleInvocationsOfTheSameRootKeepIndependentBindings() throws Exception {
        withEnteredLanguage(
                (language, ignoredModule) -> {
                    ProtosBytecodeRootNode root = lowerRoot(language, "x: 41\nx");
                    assertTrue(
                            declaredLocalNames(root, List.of("x")).isEmpty(),
                            "a module binding is NAME storage, not a native local");

                    ProtosActivation firstModule = moduleActivation();
                    ProtosActivation secondModule = moduleActivation();
                    root.getCallTarget().call(firstModule);
                    root.getCallTarget().call(secondModule);

                    ((ProtosExecutionContextValue) secondModule.context())
                            .assignLocalSlot("x", 999L);
                    assertEquals(
                            41L,
                            unwrapNumber(
                                    ((ProtosExecutionContextValue) firstModule.context())
                                            .readLocalSlot("x")
                                            .orElseThrow()));
                    assertEquals(
                            999L,
                            unwrapNumber(
                                    ((ProtosExecutionContextValue) secondModule.context())
                                            .readLocalSlot("x")
                                            .orElseThrow()));
                });
    }

    /**
     * Two invocations of one Closure root share its lowered locals but never
     * their storage: each invocation creates its own cell for a captured
     * binding, and each escaped Closure reads its own value.
     */
    @Test
    void multipleInvocationsOfTheSameClosureRootCreateIndependentCells() throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    ProtosClosureValue factory = closureOf(
                            run(language, module, "(v) => {\n    x: v\n    () => x\n}"));

                    ProtosClosureValue first = closureOf(call(factory, module, 41L));
                    ProtosClosureValue second = closureOf(call(factory, module, 999L));

                    assertSame(rootOf(first), rootOf(second));
                    ProtosLexicalCell firstCell = onlyCell(first);
                    ProtosLexicalCell secondCell = onlyCell(second);
                    assertNotSame(firstCell, secondCell);
                    assertEquals(41L, unwrapNumber(call(first, module)));
                    assertEquals(999L, unwrapNumber(call(second, module)));
                    assertEquals(41L, unwrapNumber(firstCell.valueOrNullForRuntime()));
                    assertEquals(999L, unwrapNumber(secondCell.valueOrNullForRuntime()));
                });
    }

    @Test
    void presentNullBindingRemainsDistinctFromAbsent() throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    run(language, module, "x: null");

                    ProtosExecutionContextValue context =
                            (ProtosExecutionContextValue) module.context();

                    assertTrue(context.hasLocalSlot("x"));
                    assertSame(
                            ProtosNullValue.INSTANCE,
                            context.readLocalSlot("x").orElseThrow());
                    assertFalse(context.hasLocalSlot("neverDeclared"));
                });
    }

    /**
     * CELL storage: a binding established with the guest null holds that
     * guest value; Java {@code null} is reserved for a cell whose binding
     * point has not executed.
     */
    @Test
    void presentNullCellRemainsDistinctFromUnestablished() throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    ProtosClosureValue factory = closureOf(
                            run(language, module, "() => {\n    x: null\n    () => x\n}"));
                    ProtosClosureValue reader = closureOf(call(factory, module));

                    assertSame(ProtosNullValue.INSTANCE, onlyCell(reader).valueOrNullForRuntime());
                    assertSame(ProtosNullValue.INSTANCE, call(reader, module));
                });
    }

    /**
     * NAME removal/recreation through the module context. The KNOWN I115
     * SEMANTIC DIVERGENCE (D179) that a LOCAL/CELL binding is never removed
     * or recreated through an observed context is not asserted here.
     */
    @Test
    void clearedThenRecreatedNameBindingIsObservedAgain() throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    run(
                            language,
                            module,
                            "removed: \"before\"\ncontext.removeSlot(\"removed\")\nnull");

                    ProtosExecutionContextValue context =
                            (ProtosExecutionContextValue) module.context();

                    assertFalse(context.hasLocalSlot("removed"));

                    context.createLocalSlot("removed", 7L);

                    assertTrue(context.hasLocalSlot("removed"));
                    assertEquals(
                            7L, unwrapNumber(context.readLocalSlot("removed").orElseThrow()));
                });
    }

    /* The declared names among the root's native locals, in local order. */
    private static List<String> declaredLocalNames(
            ProtosBytecodeRootNode root, List<String> candidates) {
        List<String> names = new ArrayList<>();
        for (LocalVariable local : root.getBytecodeNode().getLocals()) {
            Object name = local.getName();
            if (name instanceof String text && candidates.contains(text)) {
                names.add(text);
            }
        }
        return names;
    }

    /* Every named native local of the root as name@offset, in local order. */
    private static List<String> localShape(ProtosBytecodeRootNode root) {
        List<String> shape = new ArrayList<>();
        for (LocalVariable local : root.getBytecodeNode().getLocals()) {
            shape.add(local.getName() + "@" + local.getLocalOffset());
        }
        return shape;
    }

    private static ProtosLexicalCell onlyCell(ProtosClosureValue closure) {
        ProtosLexicalCell[] cells = closure.capturedCellsForRuntime();
        assertEquals(1, cells.length);
        return cells[0];
    }

    private static ProtosClosureValue closureOf(Object value) {
        return assertInstanceOf(ProtosClosureValue.class, value);
    }

    private static ProtosBytecodeRootNode rootOf(ProtosClosureValue closure) {
        return closure.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
    }

    private static Object call(
            ProtosClosureValue closure, ProtosActivation caller, Object... supplied) {
        return ProtosNumericValueSupport.guestValue(
                ProtosCallRequest.call(closure, List.of(supplied), caller).callUncached());
    }

    private interface ModuleTest {
        void run(ProtosLanguage language, ProtosActivation module) throws Exception;
    }

    private static void withEnteredLanguage(ModuleTest test) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                test.run(LANGUAGE_REF.get(null), moduleActivation());
            } finally {
                context.leave();
            }
        }
    }

    private static Object run(
            ProtosLanguage language, ProtosActivation module, String characters) {
        return lowerRoot(language, characters).getCallTarget().call(module);
    }

    private static ProtosBytecodeRootNode lowerRoot(
            ProtosLanguage language, String characters) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, "perf012-layout.protos")
                        .build();
        return new CanonicalToBytecodeLowerer(language, source)
                .lowerRoot(canonicalize(characters));
    }

    private static long unwrapNumber(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        // PRUIBA DE I115, en algún momento esto volverá a funcionar
        // if (value instanceof com.guillermomolina.protos.runtime.Long integerValue) {
            // return integerValue.longValue();
        // }
        throw new AssertionError("expected a Protos integer literal result, got: " + value);
    }

    private static CanonicalSequence canonicalize(String characters) {
        return (CanonicalSequence)
                new Canonicalizer().canonicalize(new ProtosParser(characters).parseProgram());
    }

    private static ProtosActivation moduleActivation() {
        ProtosStandardObjectProtocol.install();

        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings = new ProtosObjectValue(contextPrototype);
        ProtosObjectValue errorPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        bindings.createLocalSlot("Context", contextPrototype);
        bindings.createLocalSlot("Error", errorPrototype);
        bindings.createLocalSlot(
                "SlotNotFound", new ProtosObjectValue(errorPrototype));
        bindings.createLocalSlot(
                "Array", new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.freeze();

        return new ProtosPrelude(bindings, contextPrototype).newModuleActivation();
    }
}

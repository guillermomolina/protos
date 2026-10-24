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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF013 Slice B1 focal evidence: a proven {@code CapturedResolved} lexical
 * read whose owner root shares the current lowering group (PERF013 Slice
 * A/A2/A3) compiles to {@code ReadCapturedMaterializedLocal} — a compile-time
 * {@link com.oracle.truffle.api.bytecode.MaterializedLocalAccessor} identifying
 * the owner's {@link com.oracle.truffle.api.bytecode.BytecodeLocal} — instead
 * of the runtime {@code ProtosFrameLexicalBindingAuthority}/{@code
 * LocalRangeAccessor} path {@code ReadCapturedFrameLocal} still uses. This
 * slice touches captured reads only; captured writes remain unchanged and are
 * exercised by {@link ProtosI068Slice5CapturedMaterializedLexicalLoweringTest}.
 */
final class ProtosPerf013SliceB1MaterializedCapturedReadTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void ordinaryCapturedReadUsesMaterializedAccessorFastPath() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                String characters =
                        "outer: (x) => {\n"
                                + "  inner: () => x\n"
                                + "  inner()\n"
                                + "}\n"
                                + "outer(42)";
                Source source =
                        Source.newBuilder(ProtosLanguage.ID, characters, "perf013-b1-ordinary.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source).lowerRoot(sequence);

                Object result = root.getCallTarget().call(module);
                ProtosIntegerValue integer = assertInstanceOf(ProtosIntegerValue.class, result);
                assertEquals(String.valueOf("42"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(integer));
            } finally {
                context.leave();
            }
        }
        System.out.println("ORDINARY_CAPTURED_READ=PASS");
    }

    @Test
    void capturedReadObservesLaterMutationByReference() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                ProtosObjectValue first = new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosObjectValue second = new ProtosObjectValue(ProtosObjectValue.rootObject());
                module.context().createLocalSlot("seed", first);

                String characters = "x: seed\n" + "() => x";
                Source source =
                        Source.newBuilder(ProtosLanguage.ID, characters, "perf013-b1-mutation.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);
                CanonicalClosure definition =
                        assertInstanceOf(CanonicalClosure.class, sequence.expressions().get(1));
                CanonicalLookup capturedRead =
                        assertInstanceOf(CanonicalLookup.class, definition.body().expressions().get(0));

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source).lowerRoot(sequence);
                ProtosClosureValue closure =
                        assertInstanceOf(ProtosClosureValue.class, root.getCallTarget().call(module));
                ProtosClosureExecutionPlan plan = closure.executionPlan().orElseThrow();

                assertExpectedReadPathForOwner(plan, capturedRead);

                module.context().assignLocalSlot("x", second);

                Object result = plan.executeBytecodeActivationForTesting(invocationOf(module, closure));
                assertSame(second, result);
            } finally {
                context.leave();
            }
        }
        System.out.println("CAPTURE_BY_REFERENCE=PASS");
        System.out.println("LATER_MUTATION_VISIBLE=PASS");
    }

    @Test
    void escapedCaptureObservesOwnerAfterOwnerActivationReturned() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                String characters = "f: (x) => () => x\n" + "g: f(7)\n" + "g()";
                Source source =
                        Source.newBuilder(ProtosLanguage.ID, characters, "perf013-b1-escaped.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source).lowerRoot(sequence);

                /*
                 * By the time this call completes, f's own activation has
                 * already returned: g only escapes because f's own root
                 * install its retained/materialized frame-lexical-binding
                 * authority before returning.
                 */
                Object result = root.getCallTarget().call(module);
                ProtosIntegerValue integer = assertInstanceOf(ProtosIntegerValue.class, result);
                assertEquals(String.valueOf("7"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(integer));
            } finally {
                context.leave();
            }
        }
        System.out.println("ESCAPED_CAPTURE=PASS");
    }

    @Test
    void multiDepthCaptureUsesMaterializedAccessorAtEveryDepth() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                String characters =
                        "outer: (x) => { () => { () => x } }\n" + "outer(9)()()";
                Source source =
                        Source.newBuilder(ProtosLanguage.ID, characters, "perf013-b1-multidepth.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source).lowerRoot(sequence);

                Object result = root.getCallTarget().call(module);
                ProtosIntegerValue integer = assertInstanceOf(ProtosIntegerValue.class, result);
                assertEquals(String.valueOf("9"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(integer));
            } finally {
                context.leave();
            }
        }
        System.out.println("MULTI_DEPTH_CAPTURE=PASS");
    }

    @Test
    void presentNullCapturedBindingIsDistinctFromAbsent() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                String characters = "x: null\n" + "() => x";
                Source source =
                        Source.newBuilder(ProtosLanguage.ID, characters, "perf013-b1-present-null.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);
                CanonicalClosure definition =
                        assertInstanceOf(CanonicalClosure.class, sequence.expressions().get(1));
                CanonicalLookup capturedRead =
                        assertInstanceOf(CanonicalLookup.class, definition.body().expressions().get(0));

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source).lowerRoot(sequence);
                ProtosClosureValue closure =
                        assertInstanceOf(ProtosClosureValue.class, root.getCallTarget().call(module));
                ProtosClosureExecutionPlan plan = closure.executionPlan().orElseThrow();

                assertExpectedReadPathForOwner(plan, capturedRead);

                Object result = plan.executeBytecodeActivationForTesting(invocationOf(module, closure));
                assertSame(ProtosNullValue.INSTANCE, result);
            } finally {
                context.leave();
            }
        }
        System.out.println("PRESENT_NULL_DISTINCT_FROM_ABSENT=PASS");
    }

    @Test
    void ownerRemovalFallsBackToFartherLexicalBinding() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                ProtosObjectValue seed = new ProtosObjectValue(ProtosObjectValue.rootObject());
                module.context().createLocalSlot("seed", seed);

                String characters = "x: seed\n" + "() => x";
                Source source =
                        Source.newBuilder(ProtosLanguage.ID, characters, "perf013-b1-owner-remove.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);
                CanonicalClosure definition =
                        assertInstanceOf(CanonicalClosure.class, sequence.expressions().get(1));
                CanonicalLookup capturedRead =
                        assertInstanceOf(CanonicalLookup.class, definition.body().expressions().get(0));

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source).lowerRoot(sequence);
                ProtosClosureValue closure =
                        assertInstanceOf(ProtosClosureValue.class, root.getCallTarget().call(module));
                ProtosClosureExecutionPlan plan = closure.executionPlan().orElseThrow();

                assertExpectedReadPathForOwner(plan, capturedRead);

                module.context().removeLocalSlot("x");

                /*
                 * No farther lexical scope declares x and this closure was
                 * never bound to a receiver: the exact existing unqualified
                 * lookup failure must still surface.
                 */
                assertThrows(
                        ProtosSignalException.class,
                        () -> plan.executeBytecodeActivationForTesting(invocationOf(module, closure)));
            } finally {
                context.leave();
            }
        }
        System.out.println("OWNER_REMOVAL_FALLBACK=PASS");
    }

    @Test
    void ownerRemoveThenRecreateIsObservedByTheSameAccessor() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                ProtosObjectValue original = new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosObjectValue recreated = new ProtosObjectValue(ProtosObjectValue.rootObject());
                module.context().createLocalSlot("seed", original);

                String characters = "x: seed\n" + "() => x";
                Source source =
                        Source.newBuilder(ProtosLanguage.ID, characters, "perf013-b1-remove-recreate.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);
                CanonicalClosure definition =
                        assertInstanceOf(CanonicalClosure.class, sequence.expressions().get(1));
                CanonicalLookup capturedRead =
                        assertInstanceOf(CanonicalLookup.class, definition.body().expressions().get(0));

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source).lowerRoot(sequence);
                ProtosClosureValue closure =
                        assertInstanceOf(ProtosClosureValue.class, root.getCallTarget().call(module));
                ProtosClosureExecutionPlan plan = closure.executionPlan().orElseThrow();

                assertExpectedReadPathForOwner(plan, capturedRead);

                module.context().removeLocalSlot("x");
                module.context().createLocalSlot("x", recreated);

                Object result = plan.executeBytecodeActivationForTesting(invocationOf(module, closure));
                assertSame(recreated, result);
            } finally {
                context.leave();
            }
        }
        System.out.println("REMOVE_RECREATE=PASS");
    }

    @Test
    void nearerLateCreationDoesNotRetargetModuleGlobalRead() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                ProtosObjectValue outer = new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosObjectValue nearer = new ProtosObjectValue(ProtosObjectValue.rootObject());
                module.context().createLocalSlot("seed", outer);

                String characters = "x: seed\n" + "() => x";
                Source source =
                        Source.newBuilder(ProtosLanguage.ID, characters, "perf013-b1-late-nearer.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);
                CanonicalClosure definition =
                        assertInstanceOf(CanonicalClosure.class, sequence.expressions().get(1));
                CanonicalLookup capturedRead =
                        assertInstanceOf(CanonicalLookup.class, definition.body().expressions().get(0));

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source).lowerRoot(sequence);
                ProtosClosureValue closure =
                        assertInstanceOf(ProtosClosureValue.class, root.getCallTarget().call(module));
                ProtosClosureExecutionPlan plan = closure.executionPlan().orElseThrow();

                assertExpectedReadPathForOwner(plan, capturedRead);

                ProtosActivation invocation = invocationOf(module, closure);
                /*
                 * I115-R6 (experimental divergence from D179 C0): x is
                 * statically proven to the module declaration, so a binding
                 * that appears later in a nearer current context does not
                 * retarget this ROOT-bound read.
                 */
                invocation.context().createLocalSlot("x", nearer);

                Object result = plan.executeBytecodeActivationForTesting(invocation);
                assertSame(outer, result);
                assertSame(outer, module.context().readLocalSlot("x").orElseThrow());
            } finally {
                context.leave();
            }
        }
        System.out.println("I115_R6_NEARER_LATE_CREATION_IGNORED_BY_MODULE_GLOBAL=PASS");
    }

    @Test
    void parameterDefaultClosureUsesMaterializedAccessorForOwnerParameter() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                String characters = "f: (x, g: () => x) => g()\n" + "f(42)";
                Source source =
                        Source.newBuilder(ProtosLanguage.ID, characters, "perf013-b1-default-closure.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source).lowerRoot(sequence);

                Object result = root.getCallTarget().call(module);
                ProtosIntegerValue integer = assertInstanceOf(ProtosIntegerValue.class, result);
                assertEquals(String.valueOf("42"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(integer));
            } finally {
                context.leave();
            }
        }
        System.out.println("DEFAULT_PARAMETER_CLOSURE_MATERIALIZED_READ=PASS");
    }

    @Test
    void closureInsideObjectBodyCapturesOuterLexicalNotObjectSlot() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                ProtosObjectValue outerLexical = new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosObjectValue objectSlot = new ProtosObjectValue(ProtosObjectValue.rootObject());
                module.context().createLocalSlot("seed", outerLexical);
                module.context().createLocalSlot("objectValue", objectSlot);

                String characters =
                        "x: seed\n"
                                + "obj: {\n"
                                + "  x: objectValue\n"
                                + "  method: () => x\n"
                                + "}\n"
                                + "obj.method()";
                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID, characters, "perf013-b1-object-body.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source).lowerRoot(sequence);

                /*
                 * If method's captured read had incorrectly resolved to the
                 * object's own local slot, this would return objectSlot.
                 */
                assertSame(outerLexical, root.getCallTarget().call(module));
            } finally {
                context.leave();
            }
        }
        System.out.println("OBJECT_BODY_CLOSURE_MATERIALIZED_READ=PASS");
    }

    @Test
    void isolatedRebuildWithoutOwnerInGroupKeepsOldSafeFallback() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosActivation module = moduleActivation();

                ProtosObjectValue token = new ProtosObjectValue(ProtosObjectValue.rootObject());
                module.context().createLocalSlot("seed", token);

                String characters = "x: seed\n" + "() => x";
                Source source =
                        Source.newBuilder(ProtosLanguage.ID, characters, "perf013-b1-isolated-rebuild.protos")
                                .build();

                CanonicalSequence original = canonicalize(characters);
                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source).lowerRoot(original);
                ProtosClosureValue closure =
                        assertInstanceOf(ProtosClosureValue.class, root.getCallTarget().call(module));
                ProtosClosureExecutionPlan template = closure.executionPlan().orElseThrow();

                CanonicalSequence reparsed = canonicalize(characters);
                CanonicalClosure reparsedDefinition =
                        assertInstanceOf(CanonicalClosure.class, reparsed.expressions().get(1));

                ProtosClosureExecutionPlan rebuilt =
                        template.rebuildBytecodeForLanguage(reparsedDefinition, language);

                java.util.List<String> instructionNames =
                        rebuilt.bytecodeActivationRootForTesting()
                                .getBytecodeNode()
                                .getInstructionsAsList()
                                .stream()
                                .map(ProtosInstructionNames::of)
                                .toList();

                CanonicalLookup reparsedRead =
                        assertInstanceOf(
                                CanonicalLookup.class, reparsedDefinition.body().expressions().get(0));
                CanonicalBindingResolution.CapturedResolved resolution =
                        assertInstanceOf(
                                CanonicalBindingResolution.CapturedResolved.class,
                                rebuilt.bytecodeBindingAnalysisForTesting()
                                        .resolutionOf(reparsedRead)
                                        .orElseThrow());
                assertEquals(CanonicalLexicalScope.Kind.ROOT, resolution.identity().owner().kind());

                /*
                 * The rebuild lowers the Closure in isolation, in a brand new
                 * BytecodeRootNodes group that does not include the module
                 * root. I115-R6: the ROOT owner is still statically proven,
                 * so the read is one ReadGlobal, which needs no owner
                 * BytecodeLocal.
                 */
                assertEquals(
                        1,
                        instructionNames.stream().filter("c.ReadGlobal"::equals).count(),
                        () -> "isolated rebuild did not use one ReadGlobal: " + instructionNames);
                assertTrue(
                        instructionNames.stream()
                                .noneMatch(
                                        name ->
                                                name.contains("SelectCapturedMaterializedOwnerFrame")
                                                        || name.startsWith("load.local.mat")),
                        () ->
                                "isolated rebuild incorrectly used the materialized fast path "
                                        + "without its owner in the group: "
                                        + instructionNames);

                assertSame(
                        token,
                        rebuilt.executeBytecodeActivationForTesting(invocationOf(module, closure)));
            } finally {
                context.leave();
            }
        }
        System.out.println("OLD_SAFE_FALLBACK=PASS");
    }

    private static void assertExpectedReadPathForOwner(
            ProtosClosureExecutionPlan plan, CanonicalLookup capturedRead) {
        CanonicalBindingResolution.CapturedResolved captured =
                assertInstanceOf(
                        CanonicalBindingResolution.CapturedResolved.class,
                        plan.bytecodeBindingAnalysisForTesting().resolutionOf(capturedRead).orElseThrow());

        java.util.List<String> instructionNames =
                plan.bytecodeActivationRootForTesting()
                        .getBytecodeNode()
                        .getInstructionsAsList()
                        .stream()
                        .map(ProtosInstructionNames::of)
                        .toList();

        /*
         * I115-R6 (experimental): a read proven to a module (ROOT)
         * declaration is one direct ReadGlobal, never a captured owner-frame
         * selection or materialized load.
         */
        if (captured.identity().owner().kind() == CanonicalLexicalScope.Kind.ROOT) {
            assertEquals(
                    1,
                    instructionNames.stream().filter("c.ReadGlobal"::equals).count(),
                    () -> "module-owned read did not lower to one ReadGlobal: " + instructionNames);
            assertTrue(
                    instructionNames.stream()
                            .noneMatch(name ->
                                    name.contains("SelectCapturedMaterializedOwnerFrame")
                                            || name.startsWith("load.local.mat")
                                            || name.equals("c.ReadCapturedFrameLocalAtRoot")),
                    () -> "module-owned read kept a captured read path: " + instructionNames);
            return;
        }

        /*
         * I115-R3: immediate captured reads may use the compact
         * root operation. The semantic assertions in the calling
         * tests still execute after this structural check.
         */
        if (instructionNames.contains("c.ReadCapturedFrameLocalAtRoot")) {
            assertEquals(
                    1,
                    instructionNames.stream()
                            .filter("c.ReadCapturedFrameLocalAtRoot"::equals)
                            .count());
            assertTrue(
                    instructionNames.stream()
                            .noneMatch(name ->
                                    name.contains("SelectCapturedMaterializedOwnerFrame")
                                            || name.startsWith("load.local.mat")));
            return;
        }

        /*
         * BUG018-C: the fast path selects the PRESENT owner frame, then loads
         * the value with the builtin LoadLocalMaterialized of the owner local.
         */
        assertTrue(
                instructionNames.stream()
                        .anyMatch(name -> name.contains("SelectCapturedMaterializedOwnerFrame")),
                () -> "captured read did not select the materialized fast path: " + instructionNames);
        assertTrue(
                instructionNames.stream().anyMatch(name -> name.startsWith("load.local.mat")),
                () -> "captured read did not load through LoadLocalMaterialized: " + instructionNames);
        assertTrue(
                instructionNames.stream().noneMatch(name -> name.equals("ReadCapturedFrameLocal")),
                () ->
                        "captured read incorrectly emitted the runtime-authority fallback "
                                + "path even though its owner shares this lowering group: "
                                + instructionNames);
    }

    private static ProtosActivation invocationOf(ProtosActivation module, ProtosClosureValue closure) {
        return ProtosActivation.forClosureInvocation(
                closure,
                java.util.List.of(),
                module.prelude().orElseThrow(),
                module.actorModuleState(),
                module.currentModuleKey().orElse(null),
                module.executionDomain());
    }

    private static CanonicalSequence canonicalize(String characters) {
        return (CanonicalSequence)
                new Canonicalizer().canonicalize(new ProtosParser(characters).parseProgram());
    }

    private static ProtosActivation moduleActivation() {
        ProtosStandardObjectProtocol.install();

        ProtosObjectValue contextPrototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings = new ProtosObjectValue(contextPrototype);
        ProtosObjectValue errorPrototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        bindings.createLocalSlot("Context", contextPrototype);
        bindings.createLocalSlot("Error", errorPrototype);
        /*
         * newUnqualifiedLookupError/newSlotNotFound requires a standard
         * SlotNotFound prototype in the Error hierarchy: the owner-removal
         * fallback test actually reaches unqualified-lookup-error
         * construction, unlike the existing I068 Slice 5 fixture this helper
         * was copied from.
         */
        bindings.createLocalSlot("SlotNotFound", new ProtosObjectValue(errorPrototype));
        bindings.createLocalSlot("Array", new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.freeze();

        return new ProtosPrelude(bindings, contextPrototype).newModuleActivation();
    }
}

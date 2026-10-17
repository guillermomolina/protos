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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * I101-A (PLAT058-D) focal evidence: the immutable NORMAL/DEBUG profile of the owning Context
 * selects the physical lowering of every executable it owns, and NORMAL emits the proven minimal
 * representation of the primary constant Boolean-control witness.
 *
 * <p>The expected NORMAL activation root of {@code () => { true.ifTrue() { 1 } }} is a handwritten
 * {@link ProtosSemanticBytecodeRootNodeGen.Builder} program authored from the language root
 * contract (a parameterless Closure root with one terminal statement returning the literal's
 * Long carrier; D203/I105 lowers no arity-rejection root). It never consults the parser, the
 * canonicalizer, the lowerer or any lowered root. Real roots always come from the production
 * {@code ParsingRequest} path of an entered Context, so the profile is the Context's own.
 *
 * <p>Instruction signatures carry every instruction name, every argument kind, every constant
 * with its exact host type (Long is distinct from Integer and Double, Double compares by raw
 * bits), every local and bytecode index, and the tags of every tag node. Nothing is removed before
 * the oracle comparison. Profiles and caches carry no stable value and are compared by kind.
 *
 * <p>No test mutates the frozen global root. A source lowered before standard root publication
 * cannot be produced deterministically in this shared JVM (the first Core bootstrap publishes the
 * root for the whole run), so the publication snapshot is evidenced by reparse stability of
 * roots lowered after publication.
 */
final class ProtosI101ProfileSpecializedLoweringTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    private static final String DIRECT = "() => { 1 }";
    private static final String UNARY = "(value) => { value }";
    private static final String WITNESS = "() => { true.ifTrue() { 1 } }";

    @TempDir
    Path directory;

    /** DIRECT_LITERAL == FOLDED_BOOLEAN == HANDWRITTEN_ORACLE for complete activation roots. */
    @Test
    void foldedWitnessMatchesDirectLiteralAndHandwrittenOracle() throws Exception {
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL, null)) {
            normal.run(() -> {
                ProtosSemanticBytecodeRootNode oracle = handwrittenLiteralOneActivationRoot();
                ProtosSemanticBytecodeRootNode direct = activationRoot(compile(DIRECT, "direct"));
                ProtosSemanticBytecodeRootNode folded =
                        activationRoot(compile(WITNESS, "witness"));

                List<String> expected = completeSignature(oracle);
                List<String> directSignature = completeSignature(direct);
                List<String> foldedSignature = completeSignature(folded);
                System.out.println("I101_A_ORACLE_SIGNATURE=" + expected);
                System.out.println("I101_A_DIRECT_SIGNATURE=" + directSignature);
                System.out.println("I101_A_FOLDED_SIGNATURE=" + foldedSignature);

                assertEquals(expected, directSignature, "direct literal root");
                assertEquals(expected, foldedSignature, "folded Boolean-control root");
                assertTrue(
                        expected.stream().anyMatch(entry -> entry.contains("=Long(1)")),
                        expected::toString);
                assertNoForbiddenNormalOperation(foldedSignature);
                /*
                 * Capabilities are proved over the effective body (the fold's proven result),
                 * not inferred from equal instructions: the witness gets those of the literal.
                 */
                assertTrue(direct.provablyNonSuspendingBody());
                assertTrue(folded.provablyNonSuspendingBody());
                // An unfolded site (its callback body is a send) does not keep the capability.
                ProtosSemanticBytecodeRootNode unfolded =
                        activationRoot(compile("() => { true.ifTrue() { 1 + 0 } }", "unfolded"));
                assertTrue(!unfolded.provablyNonSuspendingBody());
                System.out.println("I101_A_THREE_WAY_SIGNATURE=PASS");
            });
        }
    }

    /**
     * I104-A: independent Builder oracles for the previous and fused unary
     * entries. Neither oracle invokes the production lowering machinery.
     */
    @Test
    void unaryNormalEntryMatchesIndependentBuilderOracle()
            throws Exception {
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL, null)) {
            normal.run(() -> {
                ProtosSemanticBytecodeRootNode module =
                        compile(UNARY, "i104-unary");
                ProtosSemanticBytecodeRootNode production =
                        activationRoot(module);

                List<String> previous =
                        completeSignature(handwrittenUnaryIdentityRoot(false));
                List<String> expected =
                        completeSignature(handwrittenUnaryIdentityRoot(true));
                List<String> actual =
                        completeSignature(production);

                System.out.println("I104_A_PREVIOUS_INSTRUCTIONS=" + previous);
                System.out.println("I104_A_FUSED_INSTRUCTIONS=" + actual);
                System.out.println("I104_A_PREVIOUS_COUNT=" + previous.size());
                System.out.println("I104_A_FUSED_COUNT=" + actual.size());

                assertEquals(
                        expected,
                        actual,
                        "the production root must equal the independent Builder");
                assertTrue(
                        expected.size() < previous.size(),
                        "the new entry must physically reduce instructions");
                assertTrue(
                        actual.stream().anyMatch(
                                item -> item.contains("BindUnaryFrameParameter")));
                assertTrue(
                        actual.stream().noneMatch(
                                item -> item.contains("BindClosureFrameParameter")));
                assertEquals(2, groupRoots(module).size());
                assertSame(production, groupRoots(module).get(1));

                var target = production.getCallTarget();
                var group = production.getRootNodes();
                group.ensureComplete();

                assertSame(production, groupRoots(module).get(1));
                assertSame(target, production.getCallTarget());
            });
        }

        try (Harness debug = new Harness(ProtosExecutionMode.DEBUG, null)) {
            debug.run(() -> {
                ProtosSemanticBytecodeRootNode production =
                        activationRoot(compile(UNARY, "i104-debug"));
                List<String> instructions = completeSignature(production);
                assertTrue(
                        instructions.stream().noneMatch(
                                item -> item.contains("BindUnaryFrameParameter")));
                assertTrue(
                        instructions.stream().anyMatch(
                                item -> item.contains("BindClosureFrameParameter")));
            });
        }
    }

    /** Root count/order and CallTargets of the NORMAL groups. */
    @Test
    void normalGroupTopologyHasNoCallbackRoot() throws Exception {
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL, null)) {
            normal.run(() -> {
                for (String characters : List.of(DIRECT, WITNESS)) {
                    ProtosSemanticBytecodeRootNode module =
                            compile(characters, "topology-" + characters.length());
                    List<ProtosSemanticBytecodeRootNode> roots = groupRoots(module);
                    /*
                     * Roots are numbered when opened: the module root and the Closure root it
                     * creates. The folded callback is never lowered, so it has no root.
                     */
                    assertEquals(2, roots.size(), characters);
                    assertSame(module, roots.get(0));

                    ProtosClosureValue closure = assertInstanceOf(
                            ProtosClosureValue.class,
                            module.getCallTarget().call(normal.moduleActivation()));
                    ProtosClosureExecutionPlan plan = closure.executionPlan().orElseThrow();
                    assertSame(roots.get(1), plan.bytecodeActivationRootForTesting());
                    assertSame(
                            roots.get(1).getCallTarget(),
                            plan.bytecodeActivationTargetForComposition());
                    assertEquals(ProtosExecutionMode.NORMAL, plan.bytecodeProfileForTesting());
                    assertInteger(1, callClosure(normal, closure));
                }
                System.out.println("I101_A_NORMAL_TOPOLOGY=PASS");
            });
        }
    }

    /** DEBUG keeps the ordinary send, the physical callback root and its source/RootTag. */
    @Test
    void debugWitnessKeepsTheOrdinarySendAndPhysicalCallback() throws Exception {
        try (Harness debug = new Harness(ProtosExecutionMode.DEBUG, null)) {
            debug.run(() -> {
                ProtosSemanticBytecodeRootNode module = compile(WITNESS, "debug-witness");
                List<ProtosSemanticBytecodeRootNode> roots = groupRoots(module);
                // Module, Closure and the callback root.
                assertEquals(3, roots.size());

                // Unfolded in DEBUG: the ordinary send keeps no literal-body capability.
                assertTrue(!roots.get(1).provablyNonSuspendingBody());
                List<String> activation = completeSignature(roots.get(1));
                assertNotEquals(
                        completeSignature(handwrittenLiteralOneActivationRoot()), activation);
                assertTrue(activation.stream().anyMatch(entry -> entry.contains("PrepareSendOne")),
                        activation::toString);
                assertTrue(
                        activation.stream()
                                .anyMatch(entry -> entry.contains("IsCompactCanonicalBooleanMiss")),
                        activation::toString);

                ProtosSemanticBytecodeRootNode callback = roots.get(2);
                assertNotNull(callback.getSourceSection());
                // A Closure root spans its body sequence, as the direct Closure root does.
                assertEquals("1", callback.getSourceSection().getCharacters().toString());
                assertTrue(hasRootTag(callback.getBytecodeNode().getTagTree()),
                        "the callback keeps its own RootTag");

                ProtosClosureValue closure = assertInstanceOf(
                        ProtosClosureValue.class,
                        module.getCallTarget().call(debug.moduleActivation()));
                assertEquals(
                        ProtosExecutionMode.DEBUG,
                        closure.executionPlan().orElseThrow().bytecodeProfileForTesting());
                assertInteger(1, callClosure(debug, closure));
                System.out.println("I101_A_DEBUG_PHYSICAL_CALLBACK=PASS");
            });
        }
    }

    /**
     * Reparse ({@code ensureComplete}) keeps the group, its root and CallTarget identities and
     * every semantic instruction, and only adds tag instrumentation.
     */
    @Test
    void reparseKeepsTopologyIdentitiesAndSemanticInstructions() throws Exception {
        for (ProtosExecutionMode mode : ProtosExecutionMode.values()) {
            try (Harness harness = new Harness(mode, null)) {
                harness.run(() -> {
                    ProtosSemanticBytecodeRootNode module =
                            compile(WITNESS, "reparse-" + mode);
                    BytecodeRootNodes<?> group = module.getRootNodes();
                    List<ProtosSemanticBytecodeRootNode> roots = groupRoots(module);
                    List<RootCallTarget> targets =
                            roots.stream().map(RootNode::getCallTarget).toList();
                    List<List<String>> semantic =
                            roots.stream().map(root -> semanticSignature(root)).toList();

                    group.ensureComplete();
                    List<List<String>> complete =
                            roots.stream().map(root -> signature(root)).toList();
                    for (ProtosSemanticBytecodeRootNode root : roots) {
                        assertNotNull(root.getSourceSection(), mode.toString());
                    }
                    group.ensureComplete();

                    assertSame(group, module.getRootNodes());
                    assertEquals(roots, groupRoots(module), mode.toString());
                    for (int index = 0; index < roots.size(); index++) {
                        ProtosSemanticBytecodeRootNode root =
                                (ProtosSemanticBytecodeRootNode) group.getNode(index);
                        assertSame(roots.get(index), root);
                        assertSame(targets.get(index), root.getCallTarget());
                        assertOnlyInstrumentationAdded(
                                semantic.get(index), semanticSignature(root),
                                mode + " root " + index);
                        assertEquals(complete.get(index), signature(root),
                                mode + " root " + index);
                    }

                    ProtosClosureValue closure = assertInstanceOf(
                            ProtosClosureValue.class,
                            module.getCallTarget().call(harness.moduleActivation()));
                    assertSame(roots.get(1),
                            closure.executionPlan().orElseThrow()
                                    .bytecodeActivationRootForTesting());
                    assertInteger(1, callClosure(harness, closure));
                    assertInteger(1, callClosure(harness, closure));
                });
            }
        }
        System.out.println("I101_A_REPARSE_STABLE=PASS");
    }

    /** NORMAL and DEBUG Contexts of one shared Engine each lower under their own profile. */
    @Test
    void mixedContextsOfOneEngineKeepTheirOwnProfiles() throws Exception {
        try (Engine engine = Engine.create();
                Harness normal = new Harness(ProtosExecutionMode.NORMAL, engine);
                Harness debug = new Harness(ProtosExecutionMode.DEBUG, engine)) {
            for (int round = 0; round < 2; round++) {
                String name = "mixed-" + round;
                List<String> normalSignature = normal.call(() -> {
                    List<String> expected =
                            completeSignature(handwrittenLiteralOneActivationRoot());
                    List<String> actual = completeSignature(activationRoot(compile(WITNESS, name)));
                    assertEquals(expected, actual);
                    return actual;
                });
                List<String> debugSignature = debug.call(() ->
                        completeSignature(activationRoot(compile(WITNESS, name))));
                assertNotEquals(normalSignature, debugSignature);
                assertTrue(
                        debugSignature.stream().anyMatch(entry -> entry.contains("PrepareSendOne")),
                        debugSignature::toString);
            }
        }
        System.out.println("I101_A_MIXED_CONTEXTS=PASS");
    }

    /**
     * A Closure projected into another Context is lowered under the destination profile, in both
     * directions; the source template keeps its own executable.
     */
    @Test
    void closureProjectionUsesTheDestinationContextProfile() throws Exception {
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL, null);
                Harness debug = new Harness(ProtosExecutionMode.DEBUG, null)) {
            ProtosClosureValue normalClosure = normal.call(() -> assertInstanceOf(
                    ProtosClosureValue.class,
                    compile(WITNESS, "projection-normal").getCallTarget()
                            .call(normal.moduleActivation())));
            ProtosClosureValue debugClosure = debug.call(() -> assertInstanceOf(
                    ProtosClosureValue.class,
                    compile(WITNESS, "projection-debug").getCallTarget()
                            .call(debug.moduleActivation())));
            ProtosClosureExecutionPlan normalTemplate = normalClosure.executionPlan().orElseThrow();
            ProtosClosureExecutionPlan debugTemplate = debugClosure.executionPlan().orElseThrow();

            debug.run(() -> {
                ProtosLanguageContext destination = ProtosLanguageContext.current();
                ProtosClosureExecutionPlan projected =
                        destination.bytecodeExecutionPlanForDefinition(
                                normalClosure.definition(), normalTemplate);
                assertEquals(ProtosExecutionMode.DEBUG, projected.bytecodeProfileForTesting());
                assertSame(projected, destination.bytecodeExecutionPlanForDefinition(
                        normalClosure.definition(), normalTemplate));
                List<String> signature =
                        completeSignature(projected.bytecodeActivationRootForTesting());
                assertTrue(signature.stream().anyMatch(entry -> entry.contains("PrepareSendOne")),
                        signature::toString);
            });
            normal.run(() -> {
                ProtosLanguageContext destination = ProtosLanguageContext.current();
                ProtosClosureExecutionPlan projected =
                        destination.bytecodeExecutionPlanForDefinition(
                                debugClosure.definition(), debugTemplate);
                assertEquals(ProtosExecutionMode.NORMAL, projected.bytecodeProfileForTesting());
                assertEquals(
                        completeSignature(handwrittenLiteralOneActivationRoot()),
                        completeSignature(projected.bytecodeActivationRootForTesting()));
            });
            assertEquals(ProtosExecutionMode.NORMAL, normalTemplate.bytecodeProfileForTesting());
            assertEquals(ProtosExecutionMode.DEBUG, debugTemplate.bytecodeProfileForTesting());
        }
        System.out.println("I101_A_PROJECTION_DESTINATION_PROFILE=PASS");
    }

    /** An imported module is compiled under its owning Context's profile. */
    @Test
    void importedModuleUsesTheOwningContextProfile() throws Exception {
        List<String> oracle;
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL, null)) {
            oracle = normal.call(() -> completeSignature(handwrittenLiteralOneActivationRoot()));
        }
        for (ProtosExecutionMode mode : ProtosExecutionMode.values()) {
            Path root = Files.createDirectories(directory.resolve(mode.toString()));
            Files.writeString(
                    root.resolve("helper.protos"),
                    "run: " + WITNESS + "\n",
                    StandardCharsets.UTF_8);
            Path main = root.resolve("main.protos");
            Files.writeString(
                    main,
                    """
                    Helper: import("./helper.protos")
                    run: Helper.run
                    0
                    """,
                    StandardCharsets.UTF_8);
            try (ProtosStandaloneHostedSession session =
                    ProtosStandaloneHostedSession.openForTesting(CORE, main, mode)) {
                assertEquals(ProtosExecutionOutcome.State.COMPLETED,
                        session.initialOutcome().state(), mode.toString());
                ProtosStandaloneHostedSession.PreparedTopLevel run =
                        session.prepareTopLevel("run");
                ProtosClosureExecutionPlan plan =
                        run.closureForTesting().executionPlan().orElseThrow();
                assertEquals(mode, plan.bytecodeProfileForTesting());
                List<String> signature =
                        completeSignature(plan.bytecodeActivationRootForTesting());
                if (mode == ProtosExecutionMode.NORMAL) {
                    assertEquals(oracle, signature);
                } else {
                    assertTrue(
                            signature.stream().anyMatch(entry -> entry.contains("PrepareSendOne")),
                            signature::toString);
                }
                for (int iteration = 0; iteration < 3; iteration++) {
                    assertEquals(1, run.executable().execute().asInt(), mode.toString());
                }
            }
        }
        System.out.println("I101_A_IMPORTED_MODULE_PROFILE=PASS");
    }

    /**
     * A NORMAL root omits the roots of the Boolean callbacks it inlines or never selects, so an
     * I096 inline copy of that root lowers them at its own site, where the callee's root is
     * already closed. Their captures of the callee's bindings must take the runtime-authority
     * path; both I096 sites keep their inline region and every capture keeps its by-reference
     * semantics.
     */
    @Test
    void inlineCopyLowersOmittedCapturingCallbacksAtItsSite() throws Exception {
        String program = """
                run: () => {
                    f: () => {
                        count: 0
                        value: true.ifTrueIfFalse(() => {
                            count = count + 1
                            count
                        }, () => {
                            count = 100
                            0
                        })
                        (value * 10) + count
                    }
                    f() * 1000 + (() => {
                        count: 0
                        value: false.ifTrueIfFalse(() => {
                            count = 100
                            0
                        }, () => {
                            count = count + 2
                            count
                        })
                        (value * 10) + count
                    })()
                }
                run()
                """;
        for (ProtosExecutionMode mode : ProtosExecutionMode.values()) {
            try (Harness harness = new Harness(mode, null)) {
                harness.run(() -> {
                    ProtosSemanticBytecodeRootNode module =
                            compile(program, "omitted-callbacks-" + mode);
                    List<String> run = completeSignature(groupRoots(module).get(1));
                    long inlineSites = run.stream()
                            .filter(entry -> entry.startsWith("c.PrepareInlineClosureLiteralCall"))
                            .count();
                    assertEquals(mode == ProtosExecutionMode.NORMAL ? 2L : 0L, inlineSites,
                            () -> mode + " " + run);
                    for (int iteration = 0; iteration < 3; iteration++) {
                        assertInteger(11022,
                                module.getCallTarget().call(harness.moduleActivation()));
                    }
                });
            }
        }
        System.out.println("I101_A_INLINE_COPY_OMITTED_CALLBACKS=PASS");
    }

    /**
     * Fold boundary: only an admitted literal receiver whose selected EXACT callback body is one
     * pure literal folds. Every other shape keeps ordinary semantics under both profiles.
     */
    @Test
    void foldBoundaryKeepsOrdinarySemantics() throws Exception {
        record Case(String closure, String folded, Object expected) {}
        List<Case> cases = List.of(
                // Proven folds of the same proof family.
                new Case("() => { false.ifFalse() { 1 } }", "() => { 1 }", 1L),
                new Case("() => { true.ifTrueIfFalse(() => { 1 }, () => { 2 }) }",
                        "() => { 1 }", 1L),
                new Case("() => { false.ifTrueIfFalse(() => { 1 }, () => { 2 }) }",
                        "() => { 2 }", 2L),
                new Case("() => { true.ifTrue() { null } }", "() => { null }",
                        ProtosNullValue.INSTANCE),
                // Not folded: an unselected callback, a send, a non-local return, a large
                // Integer, a receiver/canonical-Boolean result policy and a wrong arity.
                new Case("() => { false.ifTrue() { 1 } }", null, ProtosNullValue.INSTANCE),
                new Case("() => { true.ifTrue() { 1 + 2 } }", "() => { 3 }", 3L),
                new Case("() => {\n    true.ifTrue() { ^1 }\n    2\n}", "() => { 1 }", 1L),
                new Case("() => { true.ifTrue() { 99999999999999999999 } }",
                        "() => { 99999999999999999999 }",
                        new BigInteger("99999999999999999999")),
                new Case("() => { true.if() { 1 } }", "() => { true }", ProtosBooleanValue.TRUE),
                new Case("() => { true.and() { true } }", "() => { true }",
                        ProtosBooleanValue.TRUE),
                new Case("() => { Error.handle(() => { true.ifTrue() }, (caught) => { 7 }) }",
                        null, 7L));
        List<Integer> foldedCases = List.of(0, 1, 2, 3);
        for (ProtosExecutionMode mode : ProtosExecutionMode.values()) {
            try (Harness harness = new Harness(mode, null)) {
                harness.run(() -> {
                    for (int index = 0; index < cases.size(); index++) {
                        Case test = cases.get(index);
                        String name = "boundary-" + index;
                        ProtosSemanticBytecodeRootNode module = compile(test.closure(), name);
                        List<String> actual = completeSignature(activationRoot(module));
                        if (test.folded() != null) {
                            List<String> folded = completeSignature(activationRoot(
                                    compile(test.folded(), name + "-folded")));
                            boolean expectFold = mode == ProtosExecutionMode.NORMAL
                                    && foldedCases.contains(index);
                            assertEquals(expectFold, folded.equals(actual),
                                    mode + " " + test.closure() + "\n" + actual);
                        }
                        ProtosClosureValue closure = assertInstanceOf(
                                ProtosClosureValue.class,
                                module.getCallTarget().call(harness.moduleActivation()));
                        assertGuestValue(test.expected(), callClosure(harness, closure),
                                mode + " " + test.closure());
                    }
                });
            }
        }
        System.out.println("I101_A_FOLD_BOUNDARY=PASS");
    }

    /**
     * I101-C/D203 cross-profile acceptance. This is a semantic and root-topology
     * integration test, not a claim of a new physically minimal encoding.
     */
    @Test
    void i101CD203BindingsRetainRootTopologyAcrossProfiles() throws Exception {
        record Case(String definition, String arguments, Object expected) {}

        List<Case> cases = List.of(
                new Case(
                        "(first, second: first, third) => { second }",
                        "9", 9L),
                new Case(
                        "(first, second: 7, third) => { second }",
                        "9, null", ProtosNullValue.INSTANCE),
                new Case(
                        "(first: 7, second, third: second) => { second }",
                        "", ProtosNullValue.INSTANCE),
                new Case(
                        "(first, second: 7, third) => { third }",
                        "9, 8, 42, 100", 42L),
                new Case(
                        "(first: 7, ...tail) => { tail.size() }",
                        "9, 8, 42", 2L));

        for (ProtosExecutionMode mode : ProtosExecutionMode.values()) {
            try (Harness harness = new Harness(mode, null)) {
                harness.run(() -> {
                    for (int index = 0; index < cases.size(); index++) {
                        Case example = cases.get(index);
                        String label = "d203-" + mode + "-" + index;

                        ProtosSemanticBytecodeRootNode module =
                                compile(example.definition(), label);

                        BytecodeRootNodes<?> group = module.getRootNodes();
                        List<ProtosSemanticBytecodeRootNode> roots =
                                groupRoots(module);

                        assertEquals(2, roots.size(),
                                "D203 must not create an arity-rejection root");

                        ProtosSemanticBytecodeRootNode body = roots.get(1);
                        RootCallTarget target = body.getCallTarget();

                        group.ensureComplete();
                        List<String> complete = signature(body);
                        group.ensureComplete();

                        assertSame(group, module.getRootNodes());
                        assertEquals(roots, groupRoots(module));
                        assertSame(body, group.getNode(1));
                        assertSame(target, body.getCallTarget());
                        assertEquals(complete, signature(body));

                        assertNotNull(body.getSourceSection());
                        assertTrue(hasRootTag(
                                body.getBytecodeNode().getTagTree()));

                        ProtosClosureValue closure = assertInstanceOf(
                                ProtosClosureValue.class,
                                module.getCallTarget().call(
                                        harness.moduleActivation()));

                        ProtosClosureExecutionPlan plan =
                                closure.executionPlan().orElseThrow();

                        assertEquals(mode, plan.bytecodeProfileForTesting());
                        assertSame(target,
                                plan.bytecodeActivationTargetForComposition());

                        ProtosActivation caller = harness.moduleActivation();
                        caller.context().createLocalSlot("candidate", closure);

                        ProtosSemanticBytecodeRootNode invocation =
                                compile(
                                        "candidate(" + example.arguments() + ")",
                                        label + "-call");

                        for (int repetition = 0; repetition < 2; repetition++) {
                            assertGuestValue(
                                    example.expected(),
                                    invocation.getCallTarget().call(caller),
                                    mode + " " + example.definition());
                        }
                    }
                });
            }
        }
    }

    /**
     * I101-C integration of lexical authority and structured control with
     * NORMAL/DEBUG root identity, reparse and real hosted execution.
     */
    @Test
    void i101CCrossFamilyControlAndCaptureAcceptance() throws Exception {
        record Case(String name, String program, int expected) {}

        List<Case> cases = List.of(
                new Case("late-capture", """
                        run: () => {
                            outer: () => {
                                value: 40
                                middle: () => {
                                    reader: () => value
                                    first: reader()
                                    value: 2
                                    first + reader()
                                }
                                middle()
                            }
                            outer()
                        }
                        0
                        """, 42),
                new Case("nonlocal-ensure", """
                        cleaned: 0
                        run: () => {
                            (() => {
                                ^42
                            }).ensure(() => {
                                cleaned = 7
                            })
                            0
                        }
                        verifyCleanup: () => { cleaned }
                        0
                        """, 42),
                new Case("error-identity", """
                        run: () => {
                            original: Error()
                            cleaned: false
                            result: original.handle(() => {
                                (() => original.signal()).ensure(() => {
                                    cleaned = true
                                })
                            }, (caught) => {
                                (caught === original) && cleaned
                            })
                            result.ifTrueIfFalse(
                                () => { 1 },
                                () => { 0 })
                        }
                        0
                        """, 1),
                new Case("suspendible-ensure", """
                        run: () => {
                            work: () => {
                                count: 0
                                dependency: (() => 7).future()
                                (() => {
                                    count = count + 1
                                    dependency.value()
                                    count = count + 10
                                }).ensure(() => {
                                    count = count + 100
                                })
                                count
                            }
                            work.future().value()
                        }
                        0
                        """, 111));

        for (ProtosExecutionMode mode : ProtosExecutionMode.values()) {
            for (Case example : cases) {
                Path file = directory.resolve(
                        "i101-c-" + mode + "-" + example.name() + ".protos");

                Files.writeString(
                        file, example.program(), StandardCharsets.UTF_8);

                try (ProtosStandaloneHostedSession session =
                        ProtosStandaloneHostedSession.openForTesting(
                                CORE, file, mode)) {

                    assertEquals(
                            ProtosExecutionOutcome.State.COMPLETED,
                            session.initialOutcome().state());

                    ProtosStandaloneHostedSession.PreparedTopLevel run =
                            session.prepareTopLevel("run");

                    ProtosClosureExecutionPlan plan =
                            run.closureForTesting()
                                    .executionPlan().orElseThrow();

                    assertEquals(mode, plan.bytecodeProfileForTesting());

                    ProtosSemanticBytecodeRootNode root =
                            plan.bytecodeActivationRootForTesting();

                    BytecodeRootNodes<?> group = root.getRootNodes();
                    List<ProtosSemanticBytecodeRootNode> roots =
                            groupRoots(root);
                    RootCallTarget target = root.getCallTarget();

                    group.ensureComplete();

                    assertSame(group, root.getRootNodes());
                    assertEquals(roots, groupRoots(root));
                    assertSame(target, root.getCallTarget());
                    assertNotNull(root.getSourceSection());
                    assertTrue(hasRootTag(
                            root.getBytecodeNode().getTagTree()));

                    for (int repetition = 0; repetition < 2; repetition++) {
                        if ("suspendible-ensure".equals(example.name())) {
                            ProtosExecutionOutcome outcome = run.invoke();
                            assertEquals(
                                    ProtosExecutionOutcome.State.COMPLETED,
                                    outcome.state(),
                                    mode + " " + example.name());
                            assertEquals(
                                    String.valueOf(example.expected()),
                                    ProtosNumericValueSupport.integerDecimalText(
                                            outcome.value()),
                                    mode + " " + example.name());
                        } else {
                            assertEquals(
                                    example.expected(),
                                    run.executable().execute().asInt(),
                                    mode + " " + example.name());
                        }
                    }

                    if (example.name().equals("nonlocal-ensure")) {
                        assertEquals(
                                7,
                                session.prepareTopLevel("verifyCleanup")
                                        .executable().execute().asInt(),
                                mode + " NLR cleanup");
                    }
                }
            }
        }
    }


    /**
     * I101-C: guarded NORMAL specializations must retain their actual MISS
     * path. NORMAL/I095 has a specialized physical Boolean-control selection
     * with a fallback; DEBUG may retain the pre-existing I092/PERF041
     * canonical ifTrue classifier, so the absence of
     * {@code IsCanonicalBooleanFalseClass} is the appropriate negative
     * control for the Boolean witness. I096 keeps a NORMAL-only inline
     * Closure-call guard that DEBUG must not emit. Reparse may materialize
     * instrumentation but must not change the semantic instruction stream,
     * root identities, CallTargets or the selected guest behavior.
     *
     * This test checks the complete typed instruction signatures for reparse
     * stability. It does not claim a minimum-Builder equality for a guarded
     * root: the MISS path is semantically necessary.
     */
    @Test
    void i101CGuardedFallbackPreservesPhysicalPathsAndReparse()
            throws Exception {
        record Family(
                String name,
                String source,
                String guard,
                String fallback,
                long expected) {}

        List<Family> families = List.of(
                new Family(
                        "boolean",
                        """
                        select: (receiver) => {
                            receiver.ifTrue(() => { 1 })
                        }
                        run: () => {
                            custom: {
                                ifTrue: (callback) => { 7 }
                            }
                            select(true) * 10 + select(custom)
                        }
                        0
                        """,
                        "TryCompactCanonicalBooleanOne",
                        "PrepareSendOne",
                        17),
                new Family(
                        "closure-call",
                        """
                        run: () => {
                            f: (value) => { value }
                            first: f(2)
                            f.call: (value) => { value + 10 }
                            second: f(3)
                            first * 10 + second
                        }
                        0
                        """,
                        "AdmitsInlineClosureLiteralCall",
                        "LoadInlineLiteralFallbackCall",
                        33));

        for (ProtosExecutionMode mode : ProtosExecutionMode.values()) {
            for (Family family : families) {
                Path folder = Files.createDirectories(
                        directory.resolve(
                                "i101-guard-" + mode + "-" + family.name()));
                Path file = folder.resolve("fixture.protos");
                Files.writeString(
                        file, family.source(), StandardCharsets.UTF_8);

                try (ProtosStandaloneHostedSession session =
                        ProtosStandaloneHostedSession.openForTesting(
                                CORE, file, mode)) {
                    assertEquals(
                            ProtosExecutionOutcome.State.COMPLETED,
                            session.initialOutcome().state(),
                            mode + " " + family.name());

                    ProtosStandaloneHostedSession.PreparedTopLevel run =
                            session.prepareTopLevel("run");

                    ProtosClosureExecutionPlan plan =
                            run.closureForTesting()
                                    .executionPlan().orElseThrow();
                    assertEquals(mode, plan.bytecodeProfileForTesting());

                    ProtosSemanticBytecodeRootNode activation =
                            plan.bytecodeActivationRootForTesting();
                    BytecodeRootNodes<?> group = activation.getRootNodes();
                    List<ProtosSemanticBytecodeRootNode> roots =
                            groupRoots(activation);

                    List<RootCallTarget> targets = roots.stream()
                            .map(RootNode::getCallTarget)
                            .toList();

                    List<List<String>> before = roots.stream()
                            .map(ProtosI101ProfileSpecializedLoweringTest
                                    ::semanticSignature)
                            .toList();

                    group.ensureComplete();

                    List<List<String>> complete = roots.stream()
                            .map(ProtosI101ProfileSpecializedLoweringTest
                                    ::signature)
                            .toList();

                    group.ensureComplete();

                    assertSame(group, activation.getRootNodes());
                    assertEquals(roots, groupRoots(activation));

                    for (int index = 0; index < roots.size(); index++) {
                        ProtosSemanticBytecodeRootNode root =
                                roots.get(index);

                        assertSame(
                                root, group.getNode(index),
                                mode + " " + family.name());
                        assertSame(
                                targets.get(index), root.getCallTarget(),
                                mode + " " + family.name());

                        assertReparsePrefixAndCleanup(
                                before.get(index),
                                semanticSignature(root),
                                mode + " " + family.name()
                                        + " root " + index);

                        assertEquals(
                                complete.get(index),
                                signature(root),
                                mode + " " + family.name()
                                        + " root " + index);

                        assertNotNull(
                                root.getSourceSection(),
                                mode + " " + family.name());
                    }

                    List<ProtosSemanticBytecodeRootNode> guarded =
                            roots.stream()
                                    .filter(root -> signature(root).stream()
                                            .anyMatch(instruction ->
                                                    instruction.contains(
                                                            family.guard())))
                                    .toList();

                    if (mode == ProtosExecutionMode.NORMAL) {
                        assertEquals(
                                1, guarded.size(),
                                "one NORMAL guarded site: "
                                        + family.name());

                        List<String> instructions =
                                signature(guarded.get(0));

                        int guardIndex = -1;
                        int fallbackIndex = -1;

                        for (int index = 0;
                                index < instructions.size();
                                index++) {
                            String instruction =
                                    instructions.get(index);

                            if (instruction.contains(family.guard())
                                    && guardIndex < 0) {
                                guardIndex = index;
                            }

                            if (instruction.contains(family.fallback())
                                    && fallbackIndex < 0) {
                                fallbackIndex = index;
                            }
                        }

                        assertTrue(
                                instructions.stream().noneMatch(
                                        instruction -> instruction.contains(
                                                "IsNormalExecutionMode")),
                                "NORMAL must not contain a runtime mode branch");
                        assertTrue(
                                guardIndex >= 0,
                                "missing guard: " + instructions);
                        assertTrue(
                                fallbackIndex > guardIndex,
                                "missing or misplaced MISS fallback: "
                                        + instructions);
                    } else if ("boolean".equals(family.name())) {
                        assertTrue(
                                guarded.stream().anyMatch(root ->
                                        signature(root).stream().anyMatch(instruction ->
                                                instruction.contains(
                                                        "TryCompactCanonicalBooleanOne"))),
                                "DEBUG retains the pre-existing I092/PERF041 "
                                        + "canonical ifTrue classifier");

                        assertTrue(
                                roots.stream().noneMatch(root ->
                                        signature(root).stream().anyMatch(instruction ->
                                                instruction.contains(
                                                        "IsCanonicalBooleanFalseClass"))),
                                "DEBUG must not emit the NORMAL I095 "
                                        + "Boolean-control selection");

                        assertTrue(
                                roots.stream().anyMatch(root ->
                                        signature(root).stream().anyMatch(instruction ->
                                                instruction.contains("PrepareSendOne"))),
                                "DEBUG must retain the ordinary ifTrue send");
                    } else {
                        assertTrue(
                                guarded.isEmpty(),
                                "DEBUG must not emit the NORMAL I096 "
                                        + "inline Closure-call guard");
                    }

                    for (int repetition = 0; repetition < 2;
                            repetition++) {
                        assertEquals(
                                family.expected(),
                                run.executable().execute().asLong(),
                                mode + " " + family.name());
                    }
                }
            }
        }
    }

    /**
     * I101-C: a cancellation request reaching a real suspension must unwind
     * through ensure. This test explicitly checks that the pending
     * cancellation is observed at the cooperative {@code dependency.value()}
     * boundary, that code after {@code dependency.value()} never runs
     * ({@code afterAwait} stays 0), that ensure runs exactly once
     * ({@code cleaned} is 1), that the handler receives the identical
     * cleanup Error ({@code caught === cleanupError}), that the cleanup
     * Error takes precedence over the original cancellation transfer, and
     * that NORMAL and DEBUG produce the same result.
     *
     * The operation is entered through a cooperative RootTask only because
     * it contains a real suspension; ordinary synchronous calls elsewhere
     * remain task-free.
     */
    @Test
    void i101CCancellationEnsureAndTaskRetainProfileParity()
            throws Exception {
        String source = """
                run: () => {
                    cleaned: 0
                    afterAwait: 0
                    cleanupError: Error()

                    producer: (() => {
                        dependency: Future.all().then(
                            (values) => { 7 })

                        Error.handle(
                            () => {
                                (() => {
                                    producer.cancel()
                                    dependency.value()
                                    afterAwait = 1
                                    41
                                }).ensure(() => {
                                    cleaned = cleaned + true.ifTrue(() => { 1 })
                                    cleanupError.signal()
                                })
                            },
                            (caught) => {
                                (caught === cleanupError).ifTrueIfFalse(
                                    () => { 42 },
                                    () => { 90 }
                                )
                            }
                        )
                    }).future()

                    value: producer.value()
                    cleaned * 100 + value + afterAwait * 1000
                }
                0
                """;

        for (ProtosExecutionMode mode : ProtosExecutionMode.values()) {
            Path folder = Files.createDirectories(
                    directory.resolve("i101-cancellation-" + mode));
            Path file = folder.resolve("fixture.protos");
            Files.writeString(file, source, StandardCharsets.UTF_8);

            try (ProtosStandaloneHostedSession session =
                    ProtosStandaloneHostedSession.openForTesting(
                            CORE, file, mode)) {
                assertEquals(
                        ProtosExecutionOutcome.State.COMPLETED,
                        session.initialOutcome().state(),
                        mode.toString());

                ProtosStandaloneHostedSession.PreparedTopLevel run =
                        session.prepareTopLevel("run");

                ProtosClosureExecutionPlan plan =
                        run.closureForTesting()
                                .executionPlan().orElseThrow();

                assertEquals(mode, plan.bytecodeProfileForTesting());

                for (int repetition = 0; repetition < 2; repetition++) {
                    ProtosExecutionOutcome result = run.invoke();

                    assertEquals(
                            ProtosExecutionOutcome.State.COMPLETED,
                            result.state(),
                            mode + " cancellation / ensure");
                    assertInteger(142, result.value());
                }
            }
        }
    }


    /**
     * Reparse may append the root's unreachable local-cleanup epilogue.
     * The epilogue may be empty, contain only {@code return}, or be the
     * complete cleanup epilogue ({@code load.null}, zero or more
     * {@code clear.local}, {@code return}). The pre-existing semantic instruction prefix must remain identical.
     * Bytecode indices are normalized by semanticSignature.
     */
    private static void assertReparsePrefixAndCleanup(
            List<String> before, List<String> after, String message) {
        assertTrue(
                after.size() >= before.size(),
                () -> message + ": instructions disappeared");

        assertEquals(
                before,
                after.subList(0, before.size()),
                message + ": semantic prefix changed");

        List<String> epilogue = after.subList(before.size(), after.size());
        if (epilogue.isEmpty() || epilogue.equals(List.of("return"))) {
            return;
        }
        assertTrue(
                epilogue.size() >= 2,
                () -> message + ": incomplete reparse epilogue " + epilogue);
        assertEquals(
                "load.null", epilogue.get(0),
                message + ": reparse epilogue must start with load.null " + epilogue);
        assertEquals(
                "return", epilogue.get(epilogue.size() - 1),
                message + ": reparse epilogue must end with return " + epilogue);
        for (String instruction : epilogue.subList(1, epilogue.size() - 1)) {
            assertTrue(
                    instruction.startsWith("clear.local "),
                    () -> message
                            + ": unexpected reparse epilogue instruction "
                            + instruction + " in " + epilogue);
        }
    }


    /**
     * I101-C/D203: defaults, rest parameters and spread arguments are
     * intentionally outside the admitted I096 direct-binding inline shapes.
     * They must keep ordinary argument binding and invocation semantics,
     * including in NORMAL. DEBUG must not acquire a NORMAL-only guard.
     */
    @Test
    void i101CD203ExcludedCallsKeepOrdinaryTopology()
            throws Exception {
        String program = """
                run: () => {
                    withDefault: (a: 2) => { a + 3 }
                    withRest: (...items) => { items.size() }
                    withSpread: (a) => { a }
                    values: [9]

                    first: withDefault()
                    second: withRest(1, 2)
                    third: withSpread(...values)

                    first * 100 + second * 10 + third
                }
                0
                """;

        for (ProtosExecutionMode mode : ProtosExecutionMode.values()) {
            Path folder = Files.createDirectories(
                    directory.resolve("i101-d203-negative-" + mode));
            Path file = folder.resolve("fixture.protos");
            Files.writeString(file, program, StandardCharsets.UTF_8);

            try (ProtosStandaloneHostedSession session =
                    ProtosStandaloneHostedSession.openForTesting(
                            CORE, file, mode)) {
                assertEquals(
                        ProtosExecutionOutcome.State.COMPLETED,
                        session.initialOutcome().state(),
                        mode.toString());

                ProtosStandaloneHostedSession.PreparedTopLevel run =
                        session.prepareTopLevel("run");

                ProtosClosureExecutionPlan plan =
                        run.closureForTesting()
                                .executionPlan().orElseThrow();

                assertEquals(mode, plan.bytecodeProfileForTesting());

                ProtosSemanticBytecodeRootNode root =
                        plan.bytecodeActivationRootForTesting();

                List<String> instructions = completeSignature(root);

                assertTrue(
                        instructions.stream().noneMatch(
                                instruction -> instruction.contains(
                                        "AdmitsInlineClosureLiteralCall")),
                        mode + " must not inline D203-excluded calls");

                // I108 preserves ordinary physical invocation through
                // semantic operations. D203 exclusions must not become
                // inline-literal calls.
                List<String> operationNames = instructions.stream()
                        .map(instruction -> instruction.split(" ", 2)[0])
                        .toList();

                long callZero = operationNames.stream()
                        .filter(name -> name.startsWith("c.InvokeClosureZero"))
                        .count();
                long callN = operationNames.stream()
                        .filter(name -> name.startsWith("c.InvokeClosureArguments"))
                        .count();

                assertEquals(1L, callZero,
                        mode + ": expected one ordinary Call0");
                assertEquals(2L, callN,
                        mode + ": expected ordinary rest and spread calls");

                assertTrue(
                        instructions.stream().noneMatch(
                                instruction -> instruction.contains(
                                        "IsNormalExecutionMode")),
                        mode + " must not contain runtime profile dispatch");

                for (int repetition = 0; repetition < 2; repetition++) {
                    assertEquals(
                            529L,
                            run.executable().execute().asLong(),
                            mode + " D203 ordinary calls");
                }
            }
        }
    }

    /**
     * I104-A standalone physical oracle. The original and fused variants
     * intentionally differ only in the parameter-binding operations.
     * All other obligations, including source tags and numeric-carrier
     * materialization, are represented identically. D203/I105: no
     * excess-argument check follows the binding.
     */
    private static ProtosSemanticBytecodeRootNode handwrittenUnaryIdentityRoot(
            boolean fused) {
        String characters = UNARY;
        Source source = Source.newBuilder(
                        ProtosLanguage.ID,
                        characters,
                        "i104-unary-oracle.protos")
                .build();

        int bodyStart = characters.indexOf('{');
        int bodyLength = characters.lastIndexOf('}') + 1 - bodyStart;
        int readStart = characters.lastIndexOf("value");

        BytecodeRootNodes<ProtosSemanticBytecodeRootNode> nodes =
                ProtosSemanticBytecodeRootNodeGen.create(
                        LANGUAGE_REF.get(null),
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginSource(source);
                            builder.beginSourceSection(bodyStart, bodyLength);
                            builder.beginRoot();

                            BytecodeLocal local =
                                    builder.createLocal("value", null);
                            BytecodeLocal[] locals = {local};
                            ProtosFrameLexicalLayout layout =
                                    ProtosFrameLexicalLayout.of(
                                            new String[] {"value"},
                                            ProtosFrameLexicalLayout.localOffsetsOf(
                                                    locals));

                            if (fused) {
                                builder.emitBindUnaryFrameParameter(
                                        locals, layout, 0, "value");
                            } else {
                                builder.beginBindClosureFrameParameter(
                                        locals, layout, 0);
                                builder.emitLoadConstant("value");
                                builder.beginLoadFrameClosureArgument();
                                builder.emitLoadConstant(0);
                                builder.endLoadFrameClosureArgument();
                                builder.endBindClosureFrameParameter();
                            }

                            builder.beginSourceSection(
                                    readStart, "value".length());
                            builder.beginTag(
                                    StandardTags.StatementTag.class,
                                    StandardTags.ExpressionTag.class);
                            builder.beginBlock();

                            builder.beginReturn();
                            builder.beginMaterializeReturnCarrier();
                            builder.beginReadRootFrameLocal(
                                    local,
                                    layout.presentContinuityAt(0));
                            builder.emitLoadConstant("value");
                            builder.endReadRootFrameLocal();
                            builder.endMaterializeReturnCarrier();
                            builder.endReturn();

                            builder.endBlock();
                            builder.endTag(
                                    StandardTags.StatementTag.class,
                                    StandardTags.ExpressionTag.class);
                            builder.endSourceSection();

                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });

        return nodes.getNode(0);
    }


    /** I104-B: production lowering against an independently written Builder. */
    @Test
    void i104RequiredPrefixMatchesIndependentBuilderOracle() throws Exception {
        final String source = "(a, b, c) => { b }";

        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL, null)) {
            normal.run(() -> {
                ProtosSemanticBytecodeRootNode module =
                        compile(source, "i104-b-normal");
                ProtosSemanticBytecodeRootNode actual =
                        activationRoot(module);

                List<String> beforeReparse = semanticSignature(actual);
                var group = module.getRootNodes();
                var roots = groupRoots(module);
                var target = actual.getCallTarget();

                List<String> baseline =
                        completeSignature(handwrittenI104BRoot(false));
                List<String> handwritten =
                        completeSignature(handwrittenI104BRoot(true));
                List<String> compiled = completeSignature(actual);

                System.out.println(
                        "I104_B_BASELINE_INSTRUCTION_COUNT="
                                + baseline.size());
                System.out.println(
                        "I104_B_COMPACT_INSTRUCTION_COUNT="
                                + compiled.size());
                System.out.println(
                        "I104_B_COMPACT_SIGNATURE=" + compiled);

                assertEquals(
                        handwritten, compiled,
                        "the required-prefix root must match independent Builder");
                assertTrue(
                        baseline.size() > compiled.size(),
                        "the compact path must remove physical instructions");
                assertEquals(2, roots.size());
                assertSame(module, roots.get(0));
                assertSame(actual, roots.get(1));
                assertSame(target, actual.getCallTarget());

                group.ensureComplete();
                assertSame(actual, group.getNode(1));
                assertSame(target, actual.getCallTarget());
                assertEquals(compiled, completeSignature(actual));
                List<String> afterReparse = semanticSignature(actual);
                assertTrue(
                        afterReparse.size() >= beforeReparse.size(),
                        "I104-B reparse lost original instructions");
                assertEquals(
                        beforeReparse,
                        afterReparse.subList(0, beforeReparse.size()),
                        "I104-B reparse changed the original instruction prefix");
                assertEquals(
                        List.of(
                                "load.null",
                                "clear.local LOCAL_OFFSET=0",
                                "clear.local LOCAL_OFFSET=1",
                                "clear.local LOCAL_OFFSET=2",
                                "return"),
                        afterReparse.subList(
                                beforeReparse.size(), afterReparse.size()),
                        "I104-B reparse must append only the expected local cleanup");

                System.out.println("I104_B_BUILDER_ORACLE=PASS");
                System.out.println("I104_B_REPARSE=PASS");
            });
        }

        try (Harness debug = new Harness(ProtosExecutionMode.DEBUG, null)) {
            debug.run(() -> {
                var root = activationRoot(
                        compile(source, "i104-b-debug"));
                List<String> signature = completeSignature(root);
                assertTrue(
                        signature.stream().noneMatch(
                                entry -> entry.contains("BindRequiredFrameParameter")),
                        signature::toString);
                assertTrue(
                        signature.stream().anyMatch(
                                entry -> entry.contains("BindClosureFrameParameter")),
                        signature::toString);
                System.out.println("I104_B_DEBUG_NEGATIVE_CONTROL=PASS");
            });
        }
    }

    private static ProtosSemanticBytecodeRootNode handwrittenI104BRoot(
            boolean compact) {
        String characters = "(a, b, c) => { b }";
        Source source = Source.newBuilder(
                ProtosLanguage.ID,
                characters,
                compact ? "i104-b-builder.protos" : "i104-b-baseline.protos")
                .build();

        int bodyStart = characters.indexOf('{');
        int bodyLength = characters.lastIndexOf('}') + 1 - bodyStart;
        int lookupStart = characters.lastIndexOf('b');

        BytecodeRootNodes<ProtosSemanticBytecodeRootNode> nodes =
                ProtosSemanticBytecodeRootNodeGen.create(
                        LANGUAGE_REF.get(null),
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginSource(source);
                            builder.beginSourceSection(bodyStart, bodyLength);
                            builder.beginRoot();

                            BytecodeLocal[] locals = {
                                builder.createLocal("a", null),
                                builder.createLocal("b", null),
                                builder.createLocal("c", null)
                            };
                            String[] names = {"a", "b", "c"};
                            ProtosFrameLexicalLayout layout =
                                    ProtosFrameLexicalLayout.of(
                                            names,
                                            ProtosFrameLexicalLayout.localOffsetsOf(
                                                    locals));

                            if (compact) {
                                for (int index = 0; index < names.length; index++) {
                                    builder.beginBindRequiredFrameParameter(
                                            locals, layout, index);
                                    builder.emitLoadConstant(names[index]);
                                    builder.endBindRequiredFrameParameter();
                                }
                            } else {
                                for (int index = 0; index < names.length; index++) {
                                    builder.beginBindClosureFrameParameter(
                                            locals, layout, index);
                                    builder.emitLoadConstant(names[index]);
                                    builder.beginLoadFrameClosureArgument();
                                    builder.emitLoadConstant(index);
                                    builder.endLoadFrameClosureArgument();
                                    builder.endBindClosureFrameParameter();
                                }
                            }

                            builder.beginSourceSection(lookupStart, 1);
                            builder.beginTag(
                                    StandardTags.StatementTag.class,
                                    StandardTags.ExpressionTag.class);
                            builder.beginBlock();

                            builder.beginReturn();
                            builder.beginMaterializeReturnCarrier();
                            builder.beginReadRootFrameLocal(
                                    locals[1],
                                    layout.presentContinuityAt(1));
                            builder.emitLoadConstant("b");
                            builder.endReadRootFrameLocal();
                            builder.endMaterializeReturnCarrier();
                            builder.endReturn();

                            builder.endBlock();
                            builder.endTag(
                                    StandardTags.StatementTag.class,
                                    StandardTags.ExpressionTag.class);
                            builder.endSourceSection();

                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });
        return nodes.getNode(0);
    }

    /**
     * Handwritten expectation of the NORMAL activation root: a parameterless Closure root with a
     * direct return of the Long carrier of {@code 1}. Ordinary guest-value materialization is
     * handled by the call boundary; D203/I105 nests no arity-rejection root.
     */
    private static ProtosSemanticBytecodeRootNode handwrittenLiteralOneActivationRoot() {
        String characters = DIRECT;
        Source source = Source.newBuilder(ProtosLanguage.ID, characters, "i101-a-oracle.protos")
                .build();
        int bodyStart = characters.indexOf('{');
        int bodyLength = characters.lastIndexOf('}') + 1 - bodyStart;
        BytecodeRootNodes<ProtosSemanticBytecodeRootNode> nodes =
                ProtosSemanticBytecodeRootNodeGen.create(
                        LANGUAGE_REF.get(null),
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginSource(source);
                            builder.beginSourceSection(bodyStart, bodyLength);
                            builder.beginRoot();

                            builder.beginReturn();
                            builder.emitLoadConstant(1L);
                            builder.endReturn();

                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });
        return nodes.getNode(0);
    }

    /** The production ParsingRequest path of the entered Context. */
    private static ProtosSemanticBytecodeRootNode compile(String characters, String name) {
        Source source = Source.newBuilder(ProtosLanguage.ID, characters, "i101-a-" + name + ".protos")
                .build();
        RootCallTarget target = assertInstanceOf(
                RootCallTarget.class, ProtosLanguageContext.current().parsePublic(source));
        return assertInstanceOf(ProtosSemanticBytecodeRootNode.class, target.getRootNode());
    }

    /** The Closure root created by a module whose only statement is a Closure literal. */
    private static ProtosSemanticBytecodeRootNode activationRoot(
            ProtosSemanticBytecodeRootNode module) {
        return groupRoots(module).get(1);
    }

    /** The roots of {@code root}'s group in group order. */
    private static List<ProtosSemanticBytecodeRootNode> groupRoots(
            ProtosSemanticBytecodeRootNode root) {
        return root.getRootNodes().getNodes().stream()
                .map(ProtosSemanticBytecodeRootNode.class::cast)
                .toList();
    }

    private static void assertNoForbiddenNormalOperation(List<String> signature) {
        List<String> forbidden = List.of(
                "IsNormalExecutionMode",
                "CanonicalBoolean",
                "IsCanonical",
                "PrepareSend",
                "Capture",
                "PrepareClosureCall",
                "TryDirectCall",
                "PrepareInlineClosureLiteralCall",
                "AdmitsInline",
                "store.local",
                "load.local",
                "branch");
        for (String entry : signature) {
            String name = entry.split(" ", 2)[0];
            for (String operation : forbidden) {
                assertTrue(!name.contains(operation), () -> operation + " in " + signature);
            }
        }
    }

    private static List<String> completeSignature(ProtosSemanticBytecodeRootNode root) {
        root.getRootNodes().ensureComplete();
        return signature(root);
    }

    /** Every instruction with every argument; nothing is filtered. */
    private static List<String> signature(ProtosSemanticBytecodeRootNode root) {
        List<String> entries = new ArrayList<>();
        for (Instruction instruction : root.getBytecodeNode().getInstructionsAsList()) {
            StringBuilder entry = new StringBuilder(instruction.getName());
            for (Instruction.Argument argument : instruction.getArguments()) {
                entry.append(' ')
                        .append(argument.getName())
                        .append(':')
                        .append(argument.getKind())
                        .append('=')
                        .append(operand(argument));
            }
            entries.add(entry.toString());
        }
        return entries;
    }

    /**
     * Reparse comparison only: the instructions other than tag and trace instrumentation, whose
     * insertion is the expected metadata expansion and shifts bytecode indices.
     */
    private static List<String> semanticSignature(ProtosSemanticBytecodeRootNode root) {
        List<String> entries = new ArrayList<>();
        for (Instruction instruction : root.getBytecodeNode().getInstructionsAsList()) {
            if (instruction.getName().startsWith("tag.")
                    || instruction.getName().startsWith("trace.")) {
                continue;
            }
            StringBuilder entry = new StringBuilder(instruction.getName());
            for (Instruction.Argument argument : instruction.getArguments()) {
                entry.append(' ').append(argument.getKind()).append('=').append(
                        argument.getKind() == Instruction.Argument.Kind.BYTECODE_INDEX
                                ? "bci"
                                : operand(argument));
            }
            entries.add(entry.toString());
        }
        return entries;
    }

    /**
     * Every semantic instruction before reparse is still present, in order and with the same
     * operands; the only additions are the instrumented root epilogue that tag materialization
     * appends after the final return.
     */
    private static void assertOnlyInstrumentationAdded(
            List<String> before, List<String> after, String message) {
        int matched = 0;
        for (String entry : after) {
            if (matched < before.size() && entry.equals(before.get(matched))) {
                matched++;
            } else {
                assertTrue(entry.equals("load.null") || entry.equals("return"),
                        () -> message + ": unexpected " + entry + " in " + after);
            }
        }
        assertEquals(before.size(), matched, () -> message + ": " + before + " vs " + after);
    }

    private static String operand(Instruction.Argument argument) {
        return switch (argument.getKind()) {
            case CONSTANT -> constant(argument.asConstant());
            case BYTECODE_INDEX -> Integer.toString(argument.asBytecodeIndex());
            case INTEGER -> Integer.toString(argument.asInteger());
            case LOCAL_OFFSET -> Integer.toString(argument.asLocalOffset());
            case LOCAL_INDEX -> Integer.toString(argument.asLocalIndex());
            case TAG_NODE -> tags(argument.asTagNode());
            case NODE_PROFILE, BRANCH_PROFILE -> "profile";
        };
    }

    private static String constant(Object value) {
        if (value == null) {
            return "host-null";
        }
        if (value instanceof Double number) {
            return "Double(bits=" + Long.toHexString(Double.doubleToRawLongBits(number)) + ")";
        }
        if (value instanceof Float number) {
            return "Float(bits=" + Integer.toHexString(Float.floatToRawIntBits(number)) + ")";
        }
        if (value instanceof Long
                || value instanceof Integer
                || value instanceof Short
                || value instanceof Byte
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof String
                || value instanceof BigInteger) {
            return value.getClass().getSimpleName() + "(" + value + ")";
        }
        if (value == ProtosNullValue.INSTANCE) {
            return "Protos(null)";
        }
        if (value == ProtosBooleanValue.TRUE || value == ProtosBooleanValue.FALSE) {
            return "Protos(" + (value == ProtosBooleanValue.TRUE) + ")";
        }
        if (value instanceof Class<?> type) {
            return "Class(" + type.getName() + ")";
        }
        // Internal metadata (plans, layouts, definitions) has no stable value across roots.
        return "Metadata(" + value.getClass().getName() + ")";
    }

    private static String tags(TagTreeNode node) {
        if (node == null) {
            return "no-tag-node";
        }
        return node.getTags().stream().map(Class::getSimpleName).sorted().toList().toString();
    }

    private static boolean hasRootTag(TagTree tree) {
        if (tree == null) {
            return false;
        }
        if (tree.hasTag(StandardTags.RootTag.class)) {
            return true;
        }
        for (TagTree child : tree.getTreeChildren()) {
            if (hasRootTag(child)) {
                return true;
            }
        }
        return false;
    }

    /** Calls a Closure value through an ordinary guest call in a fresh module activation. */
    private static Object callClosure(Harness harness, ProtosClosureValue closure) throws Exception {
        ProtosActivation module = harness.moduleActivation();
        module.context().createLocalSlot("candidate", closure);
        return compile("candidate()", "call-" + System.identityHashCode(closure))
                .getCallTarget()
                .call(module);
    }

    private static void assertInteger(long expected, Object value) {
        assertEquals(
                Long.toString(expected), ProtosNumericValueSupport.integerDecimalText(value));
    }

    private static void assertGuestValue(Object expected, Object actual, String message) {
        if (expected instanceof Long || expected instanceof BigInteger) {
            assertEquals(
                    expected.toString(), ProtosNumericValueSupport.integerDecimalText(actual),
                    message);
        } else {
            assertSame(expected, actual, message);
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface Producer<T> {
        T get() throws Exception;
    }

    /** One raw Polyglot Context with a published Core, entered only while an action runs. */
    private static final class Harness implements AutoCloseable {
        private final ProtosExecutionMode mode;
        private final Context context;
        private ProtosPrelude prelude;

        Harness(ProtosExecutionMode mode, Engine engine) {
            this.mode = mode;
            Context.Builder builder = Context.newBuilder(ProtosLanguage.ID);
            if (engine != null) {
                builder.engine(engine);
            }
            if (mode == ProtosExecutionMode.DEBUG) {
                builder.allowExperimentalOptions(true).option("protos.Debug", "true");
            }
            this.context = builder.build();
            context.initialize(ProtosLanguage.ID);
        }

        void run(Action action) throws Exception {
            call(() -> {
                action.run();
                return null;
            });
        }

        <T> T call(Producer<T> action) throws Exception {
            context.enter();
            try {
                assertEquals(mode, ProtosLanguageContext.current().executionMode());
                if (prelude == null) {
                    // Publishes the standard root before any witness is lowered.
                    prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                }
                return action.get();
            } finally {
                context.leave();
            }
        }

        ProtosActivation moduleActivation() {
            return prelude.newModuleActivation();
        }

        @Override
        public void close() {
            context.close();
        }
    }
}

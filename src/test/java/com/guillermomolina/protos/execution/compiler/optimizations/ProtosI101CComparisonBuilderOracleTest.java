/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS
 * OF THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE.
 * A COPY OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT"
 * ACCOMPANYING THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT
 * ACCOMPANY THE CONTENTS OF THIS FILE, A COPY OF THE LICENSE MAY ALSO BE
 * OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the License.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I101-C1: strict pre-Graal Builder oracle for trivial numeric comparison.
 *
 * <p>The physical optimization target is identical executable bytecode for
 * {@code () => { 1 < 2 }} and {@code () => { true }}. The independent
 * Builder emits the canonical Boolean value directly.
 *
 * <p>This is a strict RED/PASS physical oracle, matching I101-B arithmetic.
 * The mathematical result does not authorize unconditional elimination
 * of an overridable D013 message. Production optimization must separately
 * preserve canonical-selection guards and semantic fallback.
 */
final class ProtosI101CComparisonBuilderOracleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE =
            LanguageReference.create(ProtosLanguage.class);

    private static final String LITERAL = "() => { true }";
    private static final String COMPARISON = "() => { 1 < 2 }";

    @Test
    void i101ConstantComparisonMatchesBooleanLiteralBytecodeExactly()
            throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                assertEquals(ProtosExecutionMode.NORMAL,
                        ProtosLanguageContext.current().executionMode());

                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);

                ProtosSemanticBytecodeRootNode literalModule =
                        parse(LITERAL, "literal-true");
                ProtosSemanticBytecodeRootNode comparisonModule =
                        parse(COMPARISON, "comparison");

                ProtosSemanticBytecodeRootNode literal =
                        verifiedClosureRoot(literalModule, LITERAL);
                ProtosSemanticBytecodeRootNode comparison =
                        verifiedClosureRoot(comparisonModule, COMPARISON);

                ProtosSemanticBytecodeRootNode oracle =
                        handwrittenBooleanOracle();

                assertEquals(1, roots(oracle).size());
                assertSame(oracle, roots(oracle).get(0));
                assertSource(oracle, COMPARISON);

                List<String> expected = completeSignature(oracle);
                List<String> direct = completeSignature(literal);
                List<String> actual = completeSignature(comparison);

                System.out.println(
                        "I101_C1_COMPARISON_ORACLE_COUNT=" + expected.size());
                System.out.println(
                        "I101_C1_COMPARISON_PROTOS_COUNT=" + actual.size());
                System.out.println(
                        "I101_C1_COMPARISON_ORACLE=" + expected);
                System.out.println(
                        "I101_C1_COMPARISON_LITERAL=" + direct);
                System.out.println(
                        "I101_C1_COMPARISON_ACTUAL=" + actual);
                System.out.println(
                        "I101_C1_COMPARISON_BYTECODE_EQUAL="
                                + expected.equals(actual));

                // Guest semantics are checked independently of the oracle.
                assertSame(
                        ProtosBooleanValue.TRUE,
                        invoke(prelude, literalModule, literal, "literal"),
                        "The Boolean literal must return canonical true");

                assertSame(
                        ProtosBooleanValue.TRUE,
                        invoke(prelude, comparisonModule, comparison, "comparison"),
                        "1 < 2 must return canonical true");

                System.out.println("I101_C1_COMPARISON_SEMANTICS=PASS");

                // A completed group must retain its roots and CallTargets
                // across repeated deterministic reparse/completion.
                assertStableReparse(literalModule);
                assertStableReparse(comparisonModule);

                assertTrue(
                        expected.stream().anyMatch(
                                instruction -> instruction.contains("Protos(true)")),
                        "The independent oracle must load canonical Protos true");

                assertEquals(
                        expected,
                        direct,
                        "The direct Boolean literal must match the Builder oracle");

                assertEquals(
                        expected,
                        actual,
                        "1 < 2 must have exactly the Boolean literal's "
                                + "physical Bytecode DSL before Graal");

                for (String instruction : actual) {
                    for (String forbidden : List.of(
                            "TryPrimitiveNumeric",
                            "PrepareSendOne",
                            "TryDirectSendOne",
                            "MaterializePrimitiveNumericCarrier",
                            "MaterializeReturnCarrier",
                            "CreateCurrentFrameLocal",
                            "ReadRootFrameLocal",
                            "InstallFrameLexicalAuthority",
                            "store.local",
                            "load.local")) {
                        assertTrue(
                                !instruction.contains(forbidden),
                                () -> "Unexpected operation "
                                        + forbidden + " in " + actual);
                    }
                }

                System.out.println("I101_C1_COMPARISON_ORACLE=PASS");
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void i101C1CompleteConstantComparisonFamily() throws Exception {
        record Case(String expression, boolean expected) {}

        List<Case> cases = List.of(
                new Case("1 < 2", true),
                new Case("2 < 1", false),
                new Case("2 <= 2", true),
                new Case("3 <= 2", false),
                new Case("3 > 2", true),
                new Case("1 > 2", false),
                new Case("2 >= 2", true),
                new Case("1 >= 2", false),
                new Case("2 == 2", true),
                new Case("1 == 2", false),
                new Case("1 != 2", true),
                new Case("2 != 2", false),
                new Case("2 === 2", true),
                new Case("1 === 2", false),
                new Case("1 !== 2", true),
                new Case("2 !== 2", false),
                new Case("1 == 1.0", true),
                new Case("1 === 1.0", false),
                new Case("1 !== 1.0", true),
                new Case("1.0 < 2.0", true),
                new Case("2.0 >= 3.0", false),
                new Case("9007199254740993 == 9007199254740992.0", false),
                new Case("9007199254740993 > 9007199254740992.0", true),
                new Case("9223372036854775808 > 9223372036854775807", true),
                new Case("(1 + 1) < (2 + 1)", true),
                new Case("(1 + 1) == (4 - 2)", true),
                new Case("(1 + 2) !== (2 + 1)", false),
                new Case("(1 < 2) === true", true)
        );

        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                assertEquals(ProtosExecutionMode.NORMAL,
                        ProtosLanguageContext.current().executionMode());
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);

                List<String> trueSignature = completeSignature(
                        verifiedClosureRoot(
                                parse("() => { true }", "family-true"),
                                "() => { true }"));
                List<String> falseSignature = completeSignature(
                        verifiedClosureRoot(
                                parse("() => { false }", "family-false"),
                                "() => { false }"));

                int index = 0;
                for (Case testCase : cases) {
                    String program = "() => { " + testCase.expression() + " }";
                    ProtosSemanticBytecodeRootNode module =
                            parse(program, "family-" + index);
                    ProtosSemanticBytecodeRootNode root =
                            verifiedClosureRoot(module, program);

                    Object value = invoke(
                            prelude, module, root, "family-invoke-" + index);

                    assertSame(
                            ProtosBooleanValue.of(testCase.expected()),
                            value,
                            testCase.expression());

                    assertEquals(
                            testCase.expected()
                                    ? trueSignature : falseSignature,
                            completeSignature(root),
                            "Comparison was not physically folded: "
                                    + testCase.expression());

                    index++;
                }
                System.out.println("I101_C1_COMPARISON_FAMILY=PASS");
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void i101C1PropagatesComparisonConstantsThroughLocals() throws Exception {
        record Case(String body, boolean expected) {}

        List<Case> cases = List.of(
                new Case("x: 1; x < 2", true),
                new Case("x: 2; x < 1", false),
                new Case("x: 1; y: 2; x < y", true),
                new Case("x: 1; y: x < 2; y", true),
                new Case("x: 3; y: x <= 2; y", false),
                new Case("x: 1; y: 2; z: x == y; z", false),
                new Case("x: 1; y: 2; z: x != y; z", true),
                new Case("x: 1; y: 1; z: x === y; z", true),
                new Case("x: 1; y: 2; z: x !== y; z", true),
                new Case("x: 1; y: x + 1; y >= 2", true),
                new Case("x: true; y: x === true; y", true)
        );

        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                int index = 0;

                for (Case testCase : cases) {
                    String program = "() => { " + testCase.body() + " }";
                    ProtosSemanticBytecodeRootNode module =
                            parse(program, "locals-" + index);
                    ProtosSemanticBytecodeRootNode root =
                            verifiedClosureRoot(module, program);

                    assertSame(
                            ProtosBooleanValue.of(testCase.expected()),
                            invoke(prelude, module, root, "locals-invoke-" + index),
                            testCase.body());

                    List<String> emitted = completeSignature(root);
                    for (String operation : emitted) {
                        assertTrue(
                                !operation.contains("TryPrimitiveNumeric")
                                        && !operation.contains("PrepareSendOne")
                                        && !operation.contains("TryDirectSendOne"),
                                () -> "Dispatch survived: " + testCase.body()
                                        + " -> " + emitted);
                    }
                    index++;
                }
                System.out.println("I101_C1_COMPARISON_LOCALS=PASS");
            } finally {
                context.leave();
            }
        }
    }

    /**
     * Independent handwritten reference. No Protos lowerer or canonical
     * optimizer is involved in constructing the expected instructions.
     */

    @Test
    void i101C1AllConstantComparisonOperators() throws Exception {
        record Case(String expression, boolean expected) {}

        List<Case> cases = List.of(
                new Case("1 < 2", true),
                new Case("2 < 1", false),
                new Case("2 < 2", false),
                new Case("1 <= 2", true),
                new Case("2 <= 2", true),
                new Case("3 <= 2", false),
                new Case("3 > 2", true),
                new Case("1 > 2", false),
                new Case("2 > 2", false),
                new Case("3 >= 2", true),
                new Case("2 >= 2", true),
                new Case("1 >= 2", false),
                new Case("2 == 2", true),
                new Case("1 == 2", false),
                new Case("1 != 2", true),
                new Case("2 != 2", false),
                new Case("2 === 2", true),
                new Case("1 === 2", false),
                new Case("1 !== 2", true),
                new Case("2 !== 2", false),
                new Case("1 == 1.0", true),
                new Case("1 != 1.0", false),
                new Case("1 === 1.0", false),
                new Case("1 !== 1.0", true),
                new Case("1.0 < 2.0", true),
                new Case("2.0 >= 3.0", false),
                new Case("1.0 == 1", true),
                new Case("true === true", true),
                new Case("true === false", false),
                new Case("true !== false", true),
                new Case("null === null", true),
                new Case(
                        "9007199254740993 == 9007199254740992.0",
                        false),
                new Case(
                        "9007199254740993 > 9007199254740992.0",
                        true),
                new Case(
                        "9223372036854775808 > 9223372036854775807",
                        true),
                new Case(
                        "9223372036854775808 == 9223372036854775808",
                        true),
                new Case("(1 + 1) < (2 + 1)", true),
                new Case("(1 + 1) == (4 - 2)", true),
                new Case("(1 + 2) !== (2 + 1)", false),
                new Case("(1 < 2) === true", true),
                new Case("(2 < 1) === false", true)
        );

        try (Context context =
                Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                assertEquals(
                        ProtosExecutionMode.NORMAL,
                        ProtosLanguageContext.current().executionMode());

                ProtosPrelude prelude =
                        new ProtosCoreBootstrap().bootstrap(CORE);

                String trueProgram = "() => { true }";
                String falseProgram = "() => { false }";

                List<String> trueSignature = completeSignature(
                        verifiedClosureRoot(
                                parse(trueProgram, "matrix-true"),
                                trueProgram));

                List<String> falseSignature = completeSignature(
                        verifiedClosureRoot(
                                parse(falseProgram, "matrix-false"),
                                falseProgram));

                int index = 0;

                for (Case testCase : cases) {
                    String program =
                            "() => { " + testCase.expression() + " }";

                    ProtosSemanticBytecodeRootNode module =
                            parse(program, "matrix-" + index);

                    ProtosSemanticBytecodeRootNode actual =
                            verifiedClosureRoot(module, program);

                    Object result = invoke(
                            prelude,
                            module,
                            actual,
                            "matrix-invoke-" + index);

                    assertSame(
                            ProtosBooleanValue.of(testCase.expected()),
                            result,
                            "Incorrect guest result: "
                                    + testCase.expression());

                    assertEquals(
                            testCase.expected()
                                    ? trueSignature : falseSignature,
                            completeSignature(actual),
                            "Nonminimal Bytecode DSL: "
                                    + testCase.expression());

                    index++;
                }

                System.out.println(
                        "I101_C1_COMPARISON_MATRIX_COUNT="
                                + cases.size());
                System.out.println(
                        "I101_C1_COMPARISON_MATRIX=PASS");
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void i101C1ComparisonPropagationThroughLocals() throws Exception {
        record Case(String body, boolean expected) {}

        List<Case> cases = List.of(
                new Case("x: 1; x < 2", true),
                new Case("x: 2; x < 1", false),
                new Case("x: 1; y: 2; x < y", true),
                new Case("x: 2; y: 1; x > y", true),
                new Case("x: 1; y: x < 2; y", true),
                new Case("x: 3; y: x <= 2; y", false),
                new Case("x: 1; y: 2; z: x == y; z", false),
                new Case("x: 1; y: 2; z: x != y; z", true),
                new Case("x: 1; y: 1; z: x === y; z", true),
                new Case("x: 1; y: 2; z: x !== y; z", true),
                new Case("x: 1; y: x + 1; y >= 2", true),
                new Case("x: true; y: x === true; y", true)
        );

        try (Context context =
                Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                assertEquals(
                        ProtosExecutionMode.NORMAL,
                        ProtosLanguageContext.current().executionMode());

                ProtosPrelude prelude =
                        new ProtosCoreBootstrap().bootstrap(CORE);

                int index = 0;

                for (Case testCase : cases) {
                    String program =
                            "() => { " + testCase.body() + " }";

                    ProtosSemanticBytecodeRootNode module =
                            parse(program, "locals-" + index);

                    ProtosSemanticBytecodeRootNode actual =
                            verifiedClosureRoot(module, program);

                    Object result = invoke(
                            prelude,
                            module,
                            actual,
                            "locals-invoke-" + index);

                    assertSame(
                            ProtosBooleanValue.of(testCase.expected()),
                            result,
                            "Incorrect guest result: "
                                    + testCase.body());

                    List<String> instructions =
                            completeSignature(actual);

                    for (String instruction : instructions) {
                        assertTrue(
                                !instruction.contains(
                                        "TryPrimitiveNumeric")
                                && !instruction.contains(
                                        "PrepareSendOne")
                                && !instruction.contains(
                                        "TryDirectSendOne"),
                                () -> "Comparison dispatch survived: "
                                        + testCase.body()
                                        + " -> " + instructions);
                    }

                    index++;
                }

                System.out.println(
                        "I101_C1_COMPARISON_LOCALS_COUNT="
                                + cases.size());
                System.out.println(
                        "I101_C1_COMPARISON_LOCALS=PASS");
            } finally {
                context.leave();
            }
        }
    }

    private static ProtosSemanticBytecodeRootNode handwrittenBooleanOracle() {
        Source source = source(COMPARISON, "comparison-oracle");

        int bodyStart = COMPARISON.indexOf('{');
        int bodyLength = COMPARISON.lastIndexOf('}') + 1 - bodyStart;

        BytecodeRootNodes<ProtosSemanticBytecodeRootNode> nodes =
                ProtosSemanticBytecodeRootNodeGen.create(
                        LANGUAGE.get(null),
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginSource(source);
                            builder.beginSourceSection(bodyStart, bodyLength);
                            builder.beginRoot();
                            builder.beginTag(StandardTags.RootTag.class);
                            builder.beginBlock();

                            builder.beginReturn();
                            builder.emitLoadConstant(ProtosBooleanValue.TRUE);
                            builder.endReturn();

                            builder.endBlock();
                            builder.endTag(StandardTags.RootTag.class);
                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });

        return nodes.getNode(0);
    }

    private static ProtosSemanticBytecodeRootNode verifiedClosureRoot(
            ProtosSemanticBytecodeRootNode module, String program) {
        List<ProtosSemanticBytecodeRootNode> group = roots(module);

        // D203/I105: no arity-rejection root accompanies the Closure root.
        assertEquals(2, group.size(),
                "Module and executable Closure");
        assertSame(module, group.get(0));

        ProtosSemanticBytecodeRootNode closure = group.get(1);

        assertSource(closure, program);

        return closure;
    }

    private static void assertSource(
            ProtosSemanticBytecodeRootNode root, String program) {
        root.getRootNodes().ensureComplete();
        // Source sections are optional with this Bytecode configuration.
        if (root.getSourceSection() != null) {
            assertEquals(
                    program,
                    root.getSourceSection().getSource().getCharacters().toString(),
                    "The root must retain its original source identity");
        }
    }

    private static void assertStableReparse(
            ProtosSemanticBytecodeRootNode module) {
        BytecodeRootNodes<?> group = module.getRootNodes();

        List<ProtosSemanticBytecodeRootNode> before = roots(module);
        List<RootCallTarget> targets = before.stream()
                .map(ProtosSemanticBytecodeRootNode::getCallTarget)
                .toList();

        List<List<String>> signatures = before.stream()
                .map(ProtosI101CComparisonBuilderOracleTest::completeSignature)
                .toList();

        group.ensureComplete();
        group.ensureComplete();

        List<ProtosSemanticBytecodeRootNode> after = roots(module);

        assertSame(group, module.getRootNodes());
        assertEquals(before.size(), after.size());

        for (int i = 0; i < before.size(); i++) {
            assertSame(before.get(i), after.get(i));
            assertSame(targets.get(i), after.get(i).getCallTarget());
            assertEquals(signatures.get(i), completeSignature(after.get(i)));
        }
    }

    private static Object invoke(
            ProtosPrelude prelude,
            ProtosSemanticBytecodeRootNode module,
            ProtosSemanticBytecodeRootNode expectedRoot,
            String label) {
        ProtosClosureValue closure = assertInstanceOf(
                ProtosClosureValue.class,
                module.getCallTarget().call(prelude.newModuleActivation()));

        assertSame(
                expectedRoot,
                closure.executionPlan().orElseThrow()
                        .bytecodeActivationRootForTesting());

        ProtosActivation invocation = prelude.newModuleActivation();
        invocation.context().createLocalSlot("candidate", closure);

        return parse("candidate()", label + "-invoke")
                .getCallTarget()
                .call(invocation);
    }


    @Test
    void i101C1DynamicOperandsDoNotFold() throws Exception {
        record Case(
                String expression,
                long firstValue,
                boolean firstResult,
                long secondValue,
                boolean secondResult) {}

        List<Case> cases = List.of(
                new Case("subject < 2", 1, true, 3, false),
                new Case("subject <= 2", 2, true, 3, false),
                new Case("subject > 2", 1, false, 3, true),
                new Case("subject >= 2", 1, false, 2, true),
                new Case("subject == 2", 2, true, 3, false),
                new Case("subject != 2", 2, false, 3, true),
                new Case("subject === 2", 2, true, 3, false),
                new Case("subject !== 2", 2, false, 3, true)
        );

        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);

                List<String> constantTrue = completeSignature(
                        roots(parse("() => { true }", "dynamic-true")).get(1));
                List<String> constantFalse = completeSignature(
                        roots(parse("() => { false }", "dynamic-false")).get(1));

                int index = 0;
                for (Case entry : cases) {
                    String program =
                            "() => { " + entry.expression() + " }";
                    ProtosSemanticBytecodeRootNode module =
                            parse(program, "dynamic-" + index);
                    ProtosSemanticBytecodeRootNode root =
                            roots(module).get(1);
                    List<String> instructions = completeSignature(root);

                    assertTrue(
                            !instructions.equals(constantTrue)
                                    && !instructions.equals(constantFalse),
                            "Dynamic operand was physically replaced by "
                                    + "a Boolean constant: " + entry.expression());

                    assertSame(
                            ProtosBooleanValue.of(entry.firstResult()),
                            i101C1InvokeCapturedSubject(
                                    prelude, module,
                                    ProtosNumericValueSupport.integer(
                                            entry.firstValue()),
                                    "dynamic-first-" + index),
                            entry.expression());

                    assertSame(
                            ProtosBooleanValue.of(entry.secondResult()),
                            i101C1InvokeCapturedSubject(
                                    prelude, module,
                                    ProtosNumericValueSupport.integer(
                                            entry.secondValue()),
                                    "dynamic-second-" + index),
                            entry.expression());

                    index++;
                }

                System.out.println("I101_C1_DYNAMIC_OPERANDS=PASS");
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void i101C1CustomOperatorsRetainOrdinaryDispatch() throws Exception {
        record Case(
                String expression,
                boolean expected,
                int expectedCalls) {}

        List<Case> cases = List.of(
                new Case("subject < 2", true, 1),
                new Case("subject <= 2", true, 1),
                new Case("subject > 2", true, 1),
                new Case("subject >= 2", true, 1),
                new Case("subject == 2", false, 1),
                new Case("subject != 2", true, 1),
                new Case("subject === 2", false, 0),
                new Case("subject !== 2", true, 0)
        );

        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);

                // Do not mutate Core's frozen prototypes. This ordinary
                // object owns its own customizable comparison behaviors.
                ProtosObjectValue receiver =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());

                int[] calls = {0};
                for (String selector : List.of("<", "<=", ">", ">=", "==")) {
                    receiver.createLocalSlot(
                            selector,
                            ProtosClosureValue.nativeClosure(
                                    (activation, supplied) -> {
                                        calls[0]++;
                                        return ProtosBooleanValue.of(
                                                !"==".equals(selector));
                                    }));
                }

                int index = 0;
                for (Case entry : cases) {
                    String program =
                            "() => { " + entry.expression() + " }";
                    ProtosSemanticBytecodeRootNode module =
                            parse(program, "override-" + index);

                    int beforeCalls = calls[0];
                    Object actual = i101C1InvokeCapturedSubject(
                            prelude, module, receiver,
                            "override-invoke-" + index);

                    assertSame(
                            ProtosBooleanValue.of(entry.expected()),
                            actual,
                            "Incorrect overridden comparison: "
                                    + entry.expression());

                    assertEquals(
                            entry.expectedCalls(),
                            calls[0] - beforeCalls,
                            "Wrong number of ordinary operator selections: "
                                    + entry.expression());

                    index++;
                }

                System.out.println("I101_C1_CUSTOM_DISPATCH=PASS");
            } finally {
                context.leave();
            }
        }
    }

    /**
     * Evaluate the same parsed Closure with an externally supplied lexical
     * value. Its identity is unknown when the Closure root is lowered.
     */
    private static Object i101C1InvokeCapturedSubject(
            ProtosPrelude prelude,
            ProtosSemanticBytecodeRootNode module,
            Object subject,
            String label) {
        ProtosActivation owner = prelude.newModuleActivation();
        owner.context().createLocalSlot("subject", subject);

        ProtosClosureValue closure = assertInstanceOf(
                ProtosClosureValue.class,
                module.getCallTarget().call(owner));

        ProtosActivation caller = prelude.newModuleActivation();
        caller.context().createLocalSlot("candidate", closure);

        return parse("candidate()", label)
                .getCallTarget().call(caller);
    }

    private static Source source(String code, String label) {
        return Source.newBuilder(
                ProtosLanguage.ID,
                code,
                "i101-c1-" + label + ".protos").build();
    }

    private static ProtosSemanticBytecodeRootNode parse(
            String code, String label) {
        RootCallTarget target = assertInstanceOf(
                RootCallTarget.class,
                ProtosLanguageContext.current().parsePublic(
                        source(code, label)));

        return assertInstanceOf(
                ProtosSemanticBytecodeRootNode.class,
                target.getRootNode());
    }

    private static List<ProtosSemanticBytecodeRootNode> roots(
            ProtosSemanticBytecodeRootNode root) {
        return root.getRootNodes().getNodes().stream()
                .map(ProtosSemanticBytecodeRootNode.class::cast)
                .toList();
    }

    private static List<String> completeSignature(
            ProtosSemanticBytecodeRootNode root) {
        root.getRootNodes().ensureComplete();

        List<String> result = new ArrayList<>();

        for (Instruction instruction :
                root.getBytecodeNode().getInstructionsAsList()) {
            StringBuilder signature = new StringBuilder(instruction.getName());

            for (Instruction.Argument argument : instruction.getArguments()) {
                signature.append(' ')
                        .append(argument.getName())
                        .append(':')
                        .append(argument.getKind())
                        .append('=')
                        .append(operand(argument));
            }

            result.add(signature.toString());
        }

        return result;
    }

    private static String operand(Instruction.Argument argument) {
        return switch (argument.getKind()) {
            case CONSTANT -> constant(argument.asConstant());
            case BYTECODE_INDEX ->
                    Integer.toString(argument.asBytecodeIndex());
            case INTEGER ->
                    Integer.toString(argument.asInteger());
            case LOCAL_OFFSET ->
                    Integer.toString(argument.asLocalOffset());
            case LOCAL_INDEX ->
                    Integer.toString(argument.asLocalIndex());
            case TAG_NODE ->
                    argument.asTagNode() == null
                            ? "no-tag-node"
                            : argument.asTagNode().getTags().stream()
                                    .map(Class::getSimpleName)
                                    .sorted()
                                    .toList()
                                    .toString();
            case NODE_PROFILE, BRANCH_PROFILE -> "profile";
        };
    }

    private static String constant(Object value) {
        if (value == null) {
            return "host-null";
        }
        if (value == ProtosNullValue.INSTANCE) {
            return "Protos(null)";
        }
        if (value == ProtosBooleanValue.TRUE) {
            return "Protos(true)";
        }
        if (value == ProtosBooleanValue.FALSE) {
            return "Protos(false)";
        }
        if (value instanceof Double number) {
            return "Double(bits="
                    + Long.toHexString(Double.doubleToRawLongBits(number))
                    + ")";
        }
        if (value instanceof Float number) {
            return "Float(bits="
                    + Integer.toHexString(Float.floatToRawIntBits(number))
                    + ")";
        }
        if (value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof String) {
            return value.getClass().getSimpleName() + "(" + value + ")";
        }
        if (value instanceof Class<?> type) {
            return "Class(" + type.getName() + ")";
        }
        return "Metadata(" + value.getClass().getName() + ")";
    }
}

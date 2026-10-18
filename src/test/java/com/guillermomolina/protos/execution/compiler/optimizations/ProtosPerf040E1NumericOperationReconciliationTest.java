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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.Source;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF040-E1 acceptance: the per-selector primitive numeric operations, their
 * Prelude-certified admission, and the guarded negative specialization that
 * never displaces a numeric lane, whatever order operand families arrive in.
 */
final class ProtosPerf040E1NumericOperationReconciliationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path EXECUTION_SOURCES =
            Path.of("src", "main", "java", "com", "guillermomolina", "protos", "execution");

    private static final Map<String, String> OPERATIONS =
            Map.of(
                    "+", "TryPrimitiveNumericAdd",
                    "-", "TryPrimitiveNumericSubtract",
                    "*", "TryPrimitiveNumericMultiply",
                    "/", "TryPrimitiveNumericDivide",
                    "<", "TryPrimitiveNumericLess",
                    "<=", "TryPrimitiveNumericLessEqual",
                    ">", "TryPrimitiveNumericGreater",
                    ">=", "TryPrimitiveNumericGreaterEqual");

    private static final List<Class<?>> NODES =
            List.of(
                    ProtosNumericAddNode.class,
                    ProtosNumericSubtractNode.class,
                    ProtosNumericMultiplyNode.class,
                    ProtosNumericDivideNode.class,
                    ProtosNumericLessNode.class,
                    ProtosNumericLessEqualNode.class,
                    ProtosNumericGreaterNode.class,
                    ProtosNumericGreaterEqualNode.class);

    /* Matrix 1-9, 14, 16: each sequence reuses one syntactic operation site. */
    private static final List<String> SAME_SITE_SEQUENCES =
            List.of(
                    // 1. Integer -> Integer
                    "add: (x, y) => x + y\n"
                            + "(add(1, 2) == 3) && (add(40, 2) == 42)",
                    // 2. Integer -> non-numeric -> Integer
                    "add: (x, y) => x + y\n"
                            + "(add(1, 2) == 3) && (add(\"a\", \"b\") == \"ab\")"
                            + " && (add(3, 4) == 7) && SmallInteger.recognizes(add(3, 4))",
                    // 3. non-numeric -> Integer
                    "add: (x, y) => x + y\n"
                            + "(add(\"a\", \"b\") == \"ab\") && (add(3, 4) == 7)"
                            + " && SmallInteger.recognizes(add(3, 4))",
                    // 4. Integer -> Float -> Integer
                    "add: (x, y) => x + y\n"
                            + "(add(1, 2) == 3) && (add(1, 2.5) == 3.5)"
                            + " && Float.recognizes(add(1, 2.5)) && (add(3, 4) == 7)"
                            + " && SmallInteger.recognizes(add(3, 4))",
                    // 5. Integer -> overflow -> Integer, for every exact operation
                    "add: (x, y) => x + y\n"
                            + "sub: (x, y) => x - y\n"
                            + "mul: (x, y) => x * y\n"
                            + "high: 9223372036854775807\n"
                            + "low: 0 - 9223372036854775807 - 1\n"
                            + "(add(1, 2) == 3) && BigInteger.recognizes(add(high, 1))"
                            + " && ((add(high, 1) - 1) == high) && (add(3, 4) == 7)"
                            + " && (sub(5, 3) == 2) && BigInteger.recognizes(sub(low, 1))"
                            + " && (sub(9, 3) == 6)"
                            + " && (mul(2, 3) == 6) && BigInteger.recognizes(mul(high, 2))"
                            + " && (mul(4, 5) == 20) && SmallInteger.recognizes(mul(4, 5))",
                    // 6. Integer -> BigInteger -> Integer
                    "add: (x, y) => x + y\n"
                            + "big: 9223372036854775807 + 1\n"
                            + "(add(1, 2) == 3) && BigInteger.recognizes(add(big, 1))"
                            + " && (add(big, 0 - 1) == 9223372036854775807)"
                            + " && BigInteger.recognizes(add(1, big))"
                            + " && (add(3, 4) == 7) && SmallInteger.recognizes(add(3, 4))",
                    // 7. Float -> Integer -> Float
                    "add: (x, y) => x + y\n"
                            + "(add(1.5, 1.0) == 2.5) && (add(1, 2) == 3)"
                            + " && (add(0.25, 0.5) == 0.75) && (add(0.5, 1) == 1.5)"
                            + " && Float.recognizes(add(0.5, 1))",
                    // D197 exact quotient on one site: integral, Fraction, zero divisor.
                    "div: (x, y) => x / y\n"
                            + "failed: false\n"
                            + "warm: div(6, 3)\n"
                            + "Error.handle(() => { div(1, 0) }, error => { failed = true })\n"
                            + "(warm == 2) && Fraction.recognizes(div(5, 2))"
                            + " && (div(6, 3) == 2) && (div(1.0, 4) == 0.25)"
                            + " && BigInteger.recognizes(div(0 - 9223372036854775807 - 1, 0 - 1))"
                            + " && (div(8, 2) == 4) && failed && (div(9, 3) == 3)",
                    // 8. each relation with a generic operand before and after a numeric one
                    orderingSequence("<", "lt(1, 2) && !lt(2, 1) && lt(1.5, 2) && !lt(2, 1.5)"),
                    orderingSequence("<=", "lt(2, 2) && !lt(3, 2) && lt(2, 2.0) && lt(1.5, 2)"),
                    orderingSequence(">", "lt(2, 1) && !lt(1, 2) && lt(2.5, 2) && !lt(2, 2.0)"),
                    orderingSequence(">=", "lt(2, 2) && !lt(1, 2) && lt(2.0, 2) && lt(3, 2.5)"),
                    // 9. NaN is unordered; IEEE signed zeros order equal, keep their sign
                    "lt: (x, y) => x < y\n"
                            + "le: (x, y) => x <= y\n"
                            + "gt: (x, y) => x > y\n"
                            + "ge: (x, y) => x >= y\n"
                            + "add: (x, y) => x + y\n"
                            + "mul: (x, y) => x * y\n"
                            + "n: 0.0 / 0.0\n"
                            + "z: 0.0 * (0.0 - 1.0)\n"
                            + "!lt(n, n) && !le(n, 1) && !gt(1, n) && !ge(n, n)"
                            + " && le(z, 0.0) && !lt(z, 0.0) && ge(0, z) && !lt(z, 0)"
                            + " && ((1.0 / add(z, 0.0)) > 0) && ((1.0 / mul(z, 1)) < 0)"
                            + " && ((1.0 / mul(1, z)) < 0)",
                    // E2-1..4: BigInteger with SmallInteger, both orders; back into signed-64
                    "add: (x, y) => x + y\n"
                            + "sub: (x, y) => x - y\n"
                            + "mul: (x, y) => x * y\n"
                            + "big: 9223372036854775807 + 1\n"
                            + "low: 0 - 9223372036854775807 - 1\n"
                            + "BigInteger.recognizes(add(big, 1))"
                            + " && BigInteger.recognizes(add(1, big))"
                            + " && (add(big, big) == big * 2) && (add(big, 0 - big) == 0)"
                            + " && SmallInteger.recognizes(add(big, 0 - big))"
                            + " && (sub(big, 1) == 9223372036854775807)"
                            + " && SmallInteger.recognizes(sub(big, 1))"
                            + " && BigInteger.recognizes(sub(low, 1))"
                            + " && SmallInteger.recognizes(add(sub(low, 1), 1))"
                            + " && (mul(big, 0) == 0) && SmallInteger.recognizes(mul(big, 0))"
                            + " && (mul(big, 1) == big) && (mul(2, big) == big * 2)",
                    // E2-5..7: exact division, Fraction, zero divisor Error
                    "div: (x, y) => x / y\n"
                            + "big: 9223372036854775807 + 1\n"
                            + "failed: false\n"
                            + "Error.handle(() => { div(big, 0) }, error => { failed = true })\n"
                            + "(div(big * 2, big) == 2)"
                            + " && SmallInteger.recognizes(div(big * 2, big))"
                            + " && (div(big * big, big) == big)"
                            + " && BigInteger.recognizes(div(big * big, big))"
                            + " && (div(big, big * 2) == (1 / 2))"
                            + " && Fraction.recognizes(div(big, big * 2))"
                            + " && Fraction.recognizes(div(1, big)) && failed",
                    // E2-8,9: Float mixes in both orders, ties to even, huge magnitudes
                    "add: (x, y) => x + y\n"
                            + "sub: (x, y) => x - y\n"
                            + "mul: (x, y) => x * y\n"
                            + "div: (x, y) => x / y\n"
                            + "big: 9223372036854775807 + 1\n"
                            + "huge: big * big\n"
                            + "vast: huge * huge * huge * huge * huge * huge * huge * huge * huge\n"
                            + "Float.recognizes(add(big, 0.5))"
                            + " && (add(big, 0.5) == 9223372036854775808.0)"
                            + " && (add(big + 1, 0.0) == 9223372036854775808.0)"
                            + " && (sub(1.0, big) == (0.0 - 9223372036854775808.0))"
                            + " && (sub(big, 1.0) == 9223372036854775808.0)"
                            + " && (add(huge, 0.0) == 85070591730234615865843651857942052864.0)"
                            + " && (div(1.0, big) == (1.0 / 9223372036854775808.0))"
                            + " && (div(big, 2.0) == 4611686018427387904.0)"
                            + " && (mul(1.0, vast) > 1.0e308)"
                            + " && (mul(vast, 0.0 - 1.0) < (0.0 - 1.0e308))"
                            + " && (add(9007199254740993 * big, 0.0)"
                            + " == (9007199254740992.0 * 9223372036854775808.0))",
                    // E2-10: exact ordering against Float, NaN and infinity
                    "lt: (x, y) => x < y\n"
                            + "le: (x, y) => x <= y\n"
                            + "gt: (x, y) => x > y\n"
                            + "ge: (x, y) => x >= y\n"
                            + "big: 9223372036854775807 + 1\n"
                            + "n: 0.0 / 0.0\n"
                            + "inf: 1.0 / 0.0\n"
                            + "lt(9223372036854775808.0, big + 1)"
                            + " && gt(big + 1, 9223372036854775808.0)"
                            + " && !lt(big, 9223372036854775808.0)"
                            + " && le(big, 9223372036854775808.0)"
                            + " && ge(9223372036854775808.0, big)"
                            + " && !lt(big, n) && !ge(n, big) && !le(big, n) && !gt(n, big)"
                            + " && lt(big, inf) && gt(big, 0.0 - inf) && gt(inf, big * big)"
                            + " && lt(1, big) && gt(big, 1) && lt(0 - big, 1) && lt(big, big + 1)"
                            + " && le(big, big) && !gt(big, big)",
                    // E2-11: Generic -> BigInteger -> SmallInteger -> Generic -> BigInteger
                    "add: (x, y) => x + y\n"
                            + "big: 9223372036854775807 + 1\n"
                            + "(add(\"a\", \"b\") == \"ab\") && BigInteger.recognizes(add(big, 1))"
                            + " && (add(1, 2) == 3) && (add(\"c\", \"d\") == \"cd\")"
                            + " && BigInteger.recognizes(add(big, 2)) && (add(3, 4) == 7)",
                    // 16. MISS keeps evaluation, materialization and Error semantics
                    "count: 0\n"
                            + "next: () => {\n"
                            + "    count = count + 1\n"
                            + "    count\n"
                            + "}\n"
                            + "add: (x, y) => x + y\n"
                            + "a: 1 + 2\n"
                            + "failed: false\n"
                            + "Error.handle(() => { add(a, \"x\") }, error => { failed = true })\n"
                            + "sum: next() + next()\n"
                            + "Error.handle(() => { next() + \"x\" }, error => { 0 })\n"
                            + "failed && (sum == 3) && (count == 3) && (add(a, 1) == 4)"
                            + " && (a.parent() === SmallInteger)");

    private static String orderingSequence(String selector, String numeric) {
        return "lt: (x, y) => x " + selector + " y\n"
                + "Error.handle(() => { lt(\"a\", \"b\") }, error => { false })\n"
                + "first: " + numeric + "\n"
                + "Error.handle(() => { lt(\"a\", 1) }, error => { false })\n"
                + "first && " + numeric;
    }

    /** Matrix 1-9, 13, 14, 16: NORMAL and DEBUG, in two independent Contexts each. */
    @Test
    void sameSiteSequencesHoldInNormalAndDebugAcrossContexts() {
        for (boolean debug : new boolean[] {false, true}) {
            try (Context first = context(debug);
                    Context second = context(debug)) {
                for (String sequence : SAME_SITE_SEQUENCES) {
                    assertTrue(eval(first, sequence), () -> mode(debug) + ":\n" + sequence);
                    assertTrue(eval(second, sequence), () -> mode(debug) + ":\n" + sequence);
                }
            }
        }
        System.out.println("PERF040_E1_SAME_SITE_SEQUENCES=PASS");
    }

    /** Matrix 13: certification is per Prelude, immutable, and never a guest slot. */
    @Test
    void canonicalCorePreludesAreCertifiedIndependently() throws Exception {
        ProtosPrelude first = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosPrelude second = new ProtosCoreBootstrap().bootstrap(CORE);

        assertNotSame(first.numericCoreAdmission(), second.numericCoreAdmission());
        assertSame(first.numericCoreAdmission(), first.numericCoreAdmission());
        for (int lane : allLanes()) {
            assertTrue(first.numericCoreAdmission().admits(lane), "lane " + lane);
            assertTrue(second.numericCoreAdmission().admits(lane), "lane " + lane);
        }
        for (String binding : List.of("Number", "Integer", "SmallInteger", "Float")) {
            ProtosObjectValue prototype =
                    (ProtosObjectValue) first.bindings().readLocalSlot(binding).orElseThrow();
            assertTrue(prototype.isFrozen(), binding);
            for (Object slot : prototype.localSlotsSnapshot().values()) {
                assertFalse(slot instanceof ProtosNumericCoreAdmission, binding);
            }
        }
        System.out.println("PERF040_E1_PER_PRELUDE_CERTIFICATE=PASS");
    }

    /** Matrix 11: a genuinely different selected method is not admitted for that selector. */
    @Test
    void alternativeCoreSelectionIsNotAdmitted() throws Exception {
        ProtosPrelude canonical = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosObjectValue number = canonical.numberPrototype();

        ProtosObjectValue integer = new ProtosObjectValue(number);
        for (String selector : List.of("+", "-", "*", "/")) {
            ProtosClosureValue standard = (ProtosClosureValue)
                    canonical.integerPrototype().readLocalSlot(selector).orElseThrow();
            // The same native body behind a different Closure is a different selection.
            integer.createLocalSlot(
                    selector,
                    ProtosClosureValue.nativeClosure(standard.nativeBody().orElseThrow()));
        }
        ProtosObjectValue smallInteger = new ProtosObjectValue(integer);
        ProtosObjectValue bigInteger = new ProtosObjectValue(integer);
        integer.freeze();
        smallInteger.freeze();
        bigInteger.freeze();

        ProtosPrelude alternative =
                syntheticPrelude(
                        canonical,
                        Map.of(
                                "Number", number,
                                "Integer", integer,
                                "SmallInteger", smallInteger,
                                "BigInteger", bigInteger,
                                "Float", canonical.floatPrototype()));
        ProtosNumericCoreAdmission admission = alternative.numericCoreAdmission();

        for (int lane : List.of(
                ProtosNumericCoreAdmission.INTEGER_ADD,
                ProtosNumericCoreAdmission.INTEGER_SUBTRACT,
                ProtosNumericCoreAdmission.INTEGER_MULTIPLY,
                ProtosNumericCoreAdmission.INTEGER_DIVIDE)) {
            assertFalse(admission.admits(lane), "alternative Integer lane " + lane);
        }
        // Certification is per selector: the untouched standard lanes remain admitted.
        assertTrue(admission.admits(ProtosNumericCoreAdmission.INTEGER_LESS));
        assertTrue(admission.admits(ProtosNumericCoreAdmission.FLOAT_ADD));
        assertTrue(admission.admits(ProtosNumericCoreAdmission.FLOAT_GREATER_EQUAL));

        assertFalse(
                ProtosPrimitiveNumericSendFastPathsBase.admittedCanonicalNumericLane(
                        1L, true, 2L, true, alternative,
                        ProtosNumericAddNode.INTEGER_LANE, ProtosNumericAddNode.FLOAT_LANE));
        assertTrue(
                ProtosPrimitiveNumericSendFastPathsBase.admittedCanonicalNumericLane(
                        1.5d, true, 2L, true, alternative,
                        ProtosNumericAddNode.INTEGER_LANE, ProtosNumericAddNode.FLOAT_LANE));
        System.out.println("PERF040_E1_ALTERNATIVE_CORE=PASS");
    }

    /** Matrix 12: a synthetic Prelude without canonical numeric structure admits nothing. */
    @Test
    void syntheticPreludeIsNotCertified() throws Exception {
        ProtosPrelude canonical = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosPrelude synthetic = syntheticPrelude(canonical, Map.of());

        for (int lane : allLanes()) {
            assertFalse(synthetic.numericCoreAdmission().admits(lane), "lane " + lane);
        }
        // Names alone admit nothing: unfrozen look-alike prototypes are rejected.
        ProtosObjectValue openNumber = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosPrelude lookAlike =
                syntheticPrelude(canonical, Map.of("Number", openNumber));
        for (int lane : allLanes()) {
            assertFalse(lookAlike.numericCoreAdmission().admits(lane), "lane " + lane);
        }
        assertFalse(ProtosPrimitiveNumericSendFastPathsBase.admits(null,
                ProtosNumericCoreAdmission.INTEGER_ADD));
        assertFalse(
                ProtosPrimitiveNumericSendFastPathsBase.admittedCanonicalNumericLane(
                        1L, true, 2L, true, synthetic,
                        ProtosNumericAddNode.INTEGER_LANE, ProtosNumericAddNode.FLOAT_LANE));
        System.out.println("PERF040_E1_SYNTHETIC_PRELUDE=PASS");
    }

    /** Matrix 10: ordinary objects, even delegating to Number, are never a positive domain. */
    @Test
    void ordinaryDelegatesAndUnauthorizedCarriersAreOutsideEveryLane() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        List<Object> outside =
                List.of(
                        new ProtosObjectValue(prelude.numberPrototype()),
                        new ProtosObjectValue(prelude.integerPrototype()),
                        new ProtosObjectValue(prelude.floatPrototype()),
                        new ProtosObjectValue(ProtosObjectValue.rootObject()));
        for (Object value : outside) {
            for (int[] lanes : lanePairs()) {
                assertFalse(
                        ProtosPrimitiveNumericSendFastPathsBase.admittedCanonicalNumericLane(
                                value, true, 1L, true, prelude, lanes[0], lanes[1]));
                assertFalse(
                        ProtosPrimitiveNumericSendFastPathsBase.admittedCanonicalNumericLane(
                                1L, true, value, true, prelude, lanes[0], lanes[1]));
            }
        }
        // A Java carrier is a member only where the lowerer authorized it.
        assertFalse(
                ProtosPrimitiveNumericSendFastPathsBase.admittedCanonicalNumericLane(
                        1L, false, 2L, true, prelude,
                        ProtosNumericAddNode.INTEGER_LANE, ProtosNumericAddNode.FLOAT_LANE));
        assertTrue(
                ProtosPrimitiveNumericSendFastPathsBase.admittedCanonicalNumericLane(
                        new ProtosIntegerValue(1L), false, new ProtosFloatValue(2.0d), false,
                        prelude,
                        ProtosNumericAddNode.INTEGER_LANE, ProtosNumericAddNode.FLOAT_LANE));
        System.out.println("PERF040_E1_ORDINARY_DELEGATES=PASS");
    }

    /**
     * Structural evidence over the declared DSL operations: positive lanes are
     * guarded by certification, the single negative specialization is guarded
     * by the negated lane union and replaces nothing, and the only rewriteOn /
     * replaces are the doLL -> doLLOvf overflow generalizations of '+', '-'
     * and '*': a long-returning primitive rewritten on ArithmeticException
     * and replaced by a specialization with exactly the same guards and
     * operand types. The generated cached nodes exist.
     */
    @Test
    void negativeSpecializationIsDisjointAndOnlyOverflowGeneralizes() throws Exception {
        for (Class<?> node : NODES) {
            List<Method> specializations = new ArrayList<>();
            for (Method method : node.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Specialization.class)) {
                    specializations.add(method);
                }
            }
            assertTrue(specializations.size() >= 3, node.getSimpleName());
            int negative = 0;
            for (Method method : specializations) {
                Specialization specialization = method.getAnnotation(Specialization.class);
                String where = node.getSimpleName() + "." + method.getName();
                assertOverflowGeneralizationOnly(node, method, specializations);
                List<String> guards = Arrays.asList(specialization.guards());
                assertFalse(guards.isEmpty(), where + " is an unguarded catch-all");
                if (method.getName().equals("ordinaryMiss")) {
                    negative++;
                    assertEquals(
                            List.of("!admittedCanonicalNumericLane(receiver, receiverCarrier, "
                                    + "argument, argumentCarrier, prelude, INTEGER_LANE, "
                                    + "FLOAT_LANE)"),
                            guards, where);
                } else {
                    assertTrue(
                            guards.contains("admits(prelude, INTEGER_LANE)")
                                    || guards.contains("admits(prelude, FLOAT_LANE)"),
                            where + " is not certified");
                }
            }
            assertEquals(1, negative, node.getSimpleName());
            assertTrue(Class.forName(node.getName() + "Gen").getSuperclass() == node);
        }
        System.out.println("PERF040_E1_DISJOINT_NEGATIVE=PASS");
    }

    /* The authorized primitive -> generalization pairs of '+', '-' and '*'. */
    private static final Map<String, String> OVERFLOW_GENERALIZATIONS =
            Map.of("doLLOvf", "doLL", "doIntegersOvf", "doIntegers");

    private static boolean overflowGeneralizes(Class<?> node) {
        return node == ProtosNumericAddNode.class
                || node == ProtosNumericSubtractNode.class
                || node == ProtosNumericMultiplyNode.class;
    }

    private static void assertOverflowGeneralizationOnly(
            Class<?> node, Method method, List<Method> specializations) {
        Specialization specialization = method.getAnnotation(Specialization.class);
        String where = node.getSimpleName() + "." + method.getName();
        boolean arithmetic = overflowGeneralizes(node);
        String replaced = OVERFLOW_GENERALIZATIONS.get(method.getName());

        if (arithmetic && OVERFLOW_GENERALIZATIONS.containsValue(method.getName())) {
            assertEquals(List.of(ArithmeticException.class),
                    Arrays.asList(specialization.rewriteOn()), where);
            assertEquals(0, specialization.replaces().length, where);
            assertSame(long.class, method.getReturnType(), where + " must return long");
            return;
        }
        assertEquals(0, specialization.rewriteOn().length, where);
        if (!arithmetic || replaced == null) {
            assertEquals(0, specialization.replaces().length, where);
            return;
        }
        assertEquals(List.of(replaced), Arrays.asList(specialization.replaces()), where);
        Method primitive = specializations.stream()
                .filter(candidate -> candidate.getName().equals(replaced))
                .findFirst()
                .orElseThrow();
        // The generalization accepts exactly the replaced domain.
        assertEquals(
                Arrays.asList(primitive.getAnnotation(Specialization.class).guards()),
                Arrays.asList(specialization.guards()), where);
        assertEquals(
                Arrays.asList(primitive.getParameterTypes()),
                Arrays.asList(method.getParameterTypes()), where);
        assertSame(Object.class, method.getReturnType(), where);
    }

    /*
     * @Fallback (CLASS retention) and @OperationProxy (SOURCE retention) are
     * invisible to reflection, so they are checked in the source itself.
     */
    @Test
    void sourcesDeclareNoFallbackAndRegisterEveryOperation() throws Exception {
        for (Class<?> node : NODES) {
            String source = Files.readString(
                    EXECUTION_SOURCES.resolve("bytecode/numeric").resolve(node.getSimpleName() + ".java"));
            assertFalse(source.contains("@Fallback"), node.getSimpleName());
            // Only the authorized overflow generalizations may replace anything.
            String remaining = source
                    .replace("replaces = \"doLL\"", "")
                    .replace("replaces = \"doIntegers\"", "");
            assertFalse(remaining.contains("replaces ="), node.getSimpleName());
            if (overflowGeneralizes(node)) {
                assertEquals(1, occurrences(source, "replaces = \"doLL\""), node.getSimpleName());
                assertEquals(1, occurrences(source, "replaces = \"doIntegers\""),
                        node.getSimpleName());
            } else {
                assertFalse(source.contains("rewriteOn"), node.getSimpleName());
                assertEquals(source, remaining, node.getSimpleName());
            }
            assertTrue(source.contains("@OperationProxy.Proxyable("), node.getSimpleName());
        }
        String root = Files.readString(
                EXECUTION_SOURCES.resolve("bytecode/activation/ProtosBytecodeRootNode.java"));
        assertFalse(root.contains("TryPrimitiveNumericSendOne"));
        for (int index = 0; index < NODES.size(); index++) {
            String node = NODES.get(index).getSimpleName();
            String operation = "TryPrimitiveNumeric"
                    + node.substring("ProtosNumeric".length(), node.length() - "Node".length());
            assertTrue(
                    root.contains("@OperationProxy(value = " + node + ".class, name = \""
                            + operation + "\")"),
                    operation);
            assertTrue(OPERATIONS.containsValue(operation), operation);
        }
        System.out.println("PERF040_E1_SOURCE_STRUCTURE=PASS");
    }

    /**
     * The generated cached operation keeps its numeric lanes reachable after a
     * generic operand in both arrival orders, and after overflow.
     */
    @Test
    void numericLanesStayReachableInEveryArrivalOrder() throws Exception {
        withCore(module -> {
            Object generic = new ProtosObjectValue(ProtosObjectValue.rootObject());

            // Generic -> Integer -> Integer
            ProtosNumericAddNode add = ProtosNumericAddNode.create();
            assertSame(ProtosPrimitiveNumericSendFastPathsBase.MISS,
                    add.execute(generic, module, 1L, true, true, false, false));
            assertEquals(3L, add.execute(1L, module, 2L, true, true, false, false));
            assertEquals(7L, add.execute(3L, module, 4L, true, true, false, false));

            // Integer -> Generic -> Integer, then overflow -> Integer
            ProtosNumericAddNode other = ProtosNumericAddNode.create();
            assertEquals(3L, other.execute(1L, module, 2L, true, true, false, false));
            assertSame(ProtosPrimitiveNumericSendFastPathsBase.MISS,
                    other.execute(1L, module, generic, true, true, false, false));
            assertEquals(7L, other.execute(3L, module, 4L, true, true, false, false));
            Object overflow =
                    other.execute(Long.MAX_VALUE, module, 1L, true, true, false, false);
            assertTrue(ProtosNumericValueSupport.isLargeInteger(overflow));
            assertEquals(11L, other.execute(5L, module, 6L, true, true, false, false));
            assertEquals(2.5d, other.execute(1L, module, 1.5d, true, true, false, false));
            assertEquals(9L, other.execute(4L, module, 5L, true, true, false, false));

            // Unauthorized carriers miss without disturbing the lane.
            assertSame(ProtosPrimitiveNumericSendFastPathsBase.MISS,
                    other.execute(1L, module, 2L, false, false, false, false));
            assertEquals(3L, other.execute(1L, module, 2L, true, true, false, false));

            // Divide: a Fraction result misses from inside the admitted lane.
            ProtosNumericDivideNode divide = ProtosNumericDivideNode.create();
            assertSame(ProtosPrimitiveNumericSendFastPathsBase.MISS,
                    divide.execute(generic, module, 2L, true, true, false, false));
            assertEquals(2L, divide.execute(6L, module, 3L, true, true, false, false));
            assertSame(ProtosPrimitiveNumericSendFastPathsBase.MISS,
                    divide.execute(5L, module, 2L, true, true, false, false));
            assertSame(ProtosPrimitiveNumericSendFastPathsBase.MISS,
                    divide.execute(5L, module, 0L, true, true, false, false));
            assertEquals(4L, divide.execute(8L, module, 2L, true, true, false, false));

            // Ordering: generic before and after, NaN unordered, signed zeros equal.
            ProtosNumericLessNode less = ProtosNumericLessNode.create();
            assertSame(ProtosPrimitiveNumericSendFastPathsBase.MISS,
                    less.execute(generic, module, 1L, true, true));
            assertSame(ProtosBooleanValue.TRUE, less.execute(1L, module, 2L, true, true));
            assertSame(ProtosPrimitiveNumericSendFastPathsBase.MISS,
                    less.execute(1L, module, generic, true, true));
            assertSame(ProtosBooleanValue.FALSE,
                    less.execute(Double.NaN, module, 1L, true, true));
            assertSame(ProtosBooleanValue.FALSE,
                    less.execute(-0.0d, module, 0.0d, true, true));
            assertSame(ProtosBooleanValue.TRUE, less.execute(1.5d, module, 2L, true, true));
            ProtosNumericLessEqualNode lessEqual = ProtosNumericLessEqualNode.create();
            assertSame(ProtosBooleanValue.TRUE,
                    lessEqual.execute(-0.0d, module, 0L, true, true));
            assertSame(ProtosBooleanValue.FALSE,
                    lessEqual.execute(Double.NaN, module, Double.NaN, true, true));
        });
        System.out.println("PERF040_E1_ARRIVAL_ORDER=PASS");
    }

    /**
     * GraalPy-style doLL -> doLLOvf for '+', '-' and '*': long results before
     * and after the first overflow, exact promotion on overflow, carrier and
     * authentic Integer operands in every combination, generic operands
     * around them, and PERF043 folds on both primitive paths. Only results are
     * asserted: after the rewrite the generalization, not doLL, is active.
     */
    @Test
    void integerArithmeticStaysPrimitiveAcrossOverflow() throws Exception {
        withCore(module -> {
            Object generic = new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosNumericAddNode add = ProtosNumericAddNode.create();
            ProtosNumericSubtractNode subtract = ProtosNumericSubtractNode.create();
            ProtosNumericMultiplyNode multiply = ProtosNumericMultiplyNode.create();

            // Initial primitive path, no overflow.
            assertEquals(5L, add.execute(2L, module, 3L, true, true, false, false));
            assertEquals(-1L, subtract.execute(2L, module, 3L, true, true, false, false));
            assertEquals(6L, multiply.execute(2L, module, 3L, true, true, false, false));

            // First overflow: exact, normalized promotion.
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    add.execute(Long.MAX_VALUE, module, 1L, true, true, false, false)));
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    subtract.execute(Long.MIN_VALUE, module, 1L, true, true, false, false)));
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    multiply.execute(Long.MAX_VALUE, module, 2L, true, true, false, false)));

            // The generalized site keeps returning Java longs for small results.
            for (long i = 0; i < 1000; i++) {
                assertEquals(i + 1, add.execute(i, module, 1L, true, true, false, false));
                assertEquals(i - 1, subtract.execute(i, module, 1L, true, true, false, false));
                assertEquals(i * 3, multiply.execute(i, module, 3L, true, true, false, false));
            }
            // Signed-64 edges that fit are not promoted.
            assertEquals(Long.MAX_VALUE,
                    add.execute(Long.MAX_VALUE - 1, module, 1L, true, true, false, false));
            assertEquals(Long.MIN_VALUE,
                    subtract.execute(Long.MIN_VALUE + 1, module, 1L, true, true, false, false));
            assertEquals(Long.MIN_VALUE,
                    multiply.execute(Long.MIN_VALUE, module, 1L, true, true, false, false));

            // Authentic Integers and mixed carrier / Integer pairs.
            ProtosNumericAddNode represented = ProtosNumericAddNode.create();
            assertEquals(7L, represented.execute(
                    integer(3), module, integer(4), false, false, false, false));
            assertEquals(7L, represented.execute(3L, module, integer(4), true, true, false, false));
            assertEquals(7L, represented.execute(integer(3), module, 4L, true, true, false, false));
            assertEquals(7L,
                    represented.execute(integer(3), module, 4L, false, true, false, false));
            assertSame(ProtosPrimitiveNumericSendFastPathsBase.MISS,
                    represented.execute(integer(3), module, 4L, true, false, false, false));
            assertSame(ProtosPrimitiveNumericSendFastPathsBase.MISS,
                    represented.execute(generic, module, integer(4), false, false, false, false));
            assertTrue(ProtosNumericValueSupport.isLargeInteger(represented.execute(
                    integer(Long.MAX_VALUE), module, integer(1), false, false, false, false)));
            assertEquals(9L, represented.execute(
                    integer(4), module, integer(5), false, false, false, false));
            assertEquals(9L, represented.execute(4L, module, 5L, true, true, false, false));
            assertSame(ProtosPrimitiveNumericSendFastPathsBase.MISS,
                    represented.execute(4L, module, generic, true, true, false, false));
            assertEquals(2.5d, represented.execute(1L, module, 1.5d, true, true, false, false));
            assertEquals(11L, represented.execute(
                    integer(5), module, integer(6), false, false, false, false));

            // PERF043 folds: applied only when operation and operands match.
            var fold = new ProtosPrimitiveNumericSendFastPathsBase.FoldedIntegerArithmetic(
                    ProtosStandardIntegerProtocol.CanonicalIntegerOperation.ADD, 1L, 2L, 3L);
            ProtosNumericAddNode folded = ProtosNumericAddNode.create();
            assertEquals(3L, folded.execute(1L, module, 2L, true, true, fold, 3L));
            assertEquals(4L, folded.execute(2L, module, 2L, true, true, fold, 3L));
            assertEquals(3L,
                    folded.execute(integer(1), module, integer(2), false, false, fold, 3L));
            assertEquals(-1L, ProtosNumericSubtractNode.create()
                    .execute(1L, module, 2L, true, true, fold, 3L));
        });
        System.out.println("PERF040_E1_DOLL_DOLLOVF=PASS");
    }

    /** Part A, 1-3 and 7: a fresh Prelude certifies on first use, exactly once. */
    @Test
    void certificationIsDeferredToFirstUseAndMemoized() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        // Bootstrap ran Core sources but no specialized operation of this Prelude.
        assertSame(null, certificateField(prelude));

        ProtosNumericCoreAdmission first = prelude.numericCoreAdmission();
        assertSame(first, certificateField(prelude));
        assertSame(first, prelude.numericCoreAdmission());
        assertTrue(first.admits(ProtosNumericCoreAdmission.INTEGER_ADD));

        // A specialized operation reaches the same memoized certificate.
        withCore(module -> {
            ProtosPrelude own = module.preludeOrNullForRuntime();
            assertSame(null, certificateField(own));
            assertEquals(3L, ProtosNumericAddNode.create()
                    .execute(1L, module, 2L, true, true, false, false));
            ProtosNumericCoreAdmission used = certificateField(own);
            assertTrue(used != null);
            assertEquals(7L, ProtosNumericAddNode.create()
                    .execute(3L, module, 4L, true, true, false, false));
            assertSame(used, certificateField(own));
        });
        System.out.println("PERF040_E1_DEFERRED_CERTIFICATE=PASS");
    }

    /** Part A, 4 and 5: negative certificates are memoized; Preludes share nothing. */
    @Test
    void negativeCertificationIsStableAndPerPrelude() throws Exception {
        ProtosPrelude canonical = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosPrelude synthetic = syntheticPrelude(canonical, Map.of());
        assertSame(null, certificateField(synthetic));
        ProtosNumericCoreAdmission negative = synthetic.numericCoreAdmission();
        assertSame(negative, synthetic.numericCoreAdmission());
        assertSame(negative, certificateField(synthetic));
        for (int lane : allLanes()) {
            assertFalse(negative.admits(lane));
        }

        ProtosPrelude other = new ProtosCoreBootstrap().bootstrap(CORE);
        assertNotSame(canonical.numericCoreAdmission(), other.numericCoreAdmission());
        System.out.println("PERF040_E1_NEGATIVE_CERTIFICATE=PASS");
    }

    /** Part A, 6: concurrent first use publishes one complete certificate. */
    @Test
    void concurrentFirstUsePublishesOneCertificate() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<ProtosNumericCoreAdmission>> results = new ArrayList<>();
            for (int index = 0; index < threads; index++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return prelude.numericCoreAdmission();
                }));
            }
            start.countDown();
            ProtosNumericCoreAdmission published = results.get(0).get();
            for (Future<ProtosNumericCoreAdmission> result : results) {
                assertSame(published, result.get());
            }
            for (int lane : allLanes()) {
                assertTrue(published.admits(lane));
            }
            assertSame(published, certificateField(prelude));
        } finally {
            pool.shutdownNow();
        }
        System.out.println("PERF040_E1_CONCURRENT_CERTIFICATE=PASS");
    }

    /**
     * Part B structural reachability: owned large Integers take a numeric
     * lane (never MISS) after generic operands, in every operation; foreign
     * or delegating look-alikes stay on ordinary dispatch.
     */
    @Test
    void largeIntegerLanesAreReachableAndOwned() throws Exception {
        withCore(module -> {
            ProtosPrelude prelude = module.preludeOrNullForRuntime();
            Object big = ProtosNumericValueSupport.addIntegers(
                    integer(Long.MAX_VALUE), integer(1L), prelude);
            assertTrue(ProtosNumericValueSupport.isLargeInteger(big));
            Object generic = new ProtosObjectValue(ProtosObjectValue.rootObject());
            Object miss = ProtosPrimitiveNumericSendFastPathsBase.MISS;

            ProtosNumericAddNode add = ProtosNumericAddNode.create();
            assertSame(miss, add.execute(generic, module, 1L, true, true, false, false));
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    add.execute(big, module, 1L, false, true, false, false)));
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    add.execute(1L, module, big, true, false, false, false)));
            assertEquals(3L, add.execute(1L, module, 2L, true, true, false, false));
            assertSame(miss, add.execute(big, module, generic, false, true, false, false));
            Object zero = add.execute(big, module,
                    ProtosNumericValueSupport.subtractIntegers(integer(0L), big, prelude),
                    false, false, false, false);
            assertEquals(0L, ((ProtosIntegerValue) zero).longValue());
            assertEquals(9.223372036854775808E18d,
                    add.execute(big, module, 0.5d, false, true, false, false));
            assertEquals(9.223372036854775808E18d,
                    add.execute(0.5d, module, big, true, false, false, false));

            ProtosNumericSubtractNode subtract = ProtosNumericSubtractNode.create();
            assertEquals(Long.MAX_VALUE, ((ProtosIntegerValue) subtract.execute(
                    big, module, 1L, false, true, false, false)).longValue());
            ProtosNumericMultiplyNode multiply = ProtosNumericMultiplyNode.create();
            assertEquals(0L, ((ProtosIntegerValue) multiply.execute(
                    big, module, 0L, false, true, false, false)).longValue());

            ProtosNumericDivideNode divide = ProtosNumericDivideNode.create();
            assertSame(miss, divide.execute(generic, module, big, true, false, false, false));
            assertEquals(2L, ((ProtosIntegerValue) divide.execute(
                    multiply.execute(big, module, 2L, false, true, false, false),
                    module, big, false, false, false, false)).longValue());
            assertTrue(ProtosNumericValueSupport.isFraction(
                    divide.execute(1L, module, big, true, false, false, false)));
            // Zero divisor: ordinary dispatch owns the Error.
            assertSame(miss, divide.execute(big, module, 0L, false, true, false, false));
            assertSame(miss, divide.execute(big, module, integer(0L), false, false, false, false));

            ProtosNumericLessNode less = ProtosNumericLessNode.create();
            assertSame(miss, less.execute(generic, module, big, true, false));
            assertSame(ProtosBooleanValue.TRUE, less.execute(1L, module, big, true, false));
            assertSame(ProtosBooleanValue.FALSE, less.execute(big, module, 1L, false, true));
            assertSame(ProtosBooleanValue.FALSE,
                    less.execute(big, module, 9.223372036854775808E18d, false, true));
            assertSame(ProtosBooleanValue.TRUE,
                    less.execute(9.223372036854775808E18d, module,
                            add.execute(big, module, 1L, false, true, false, false),
                            true, false));
            assertSame(ProtosBooleanValue.FALSE,
                    less.execute(big, module, Double.NaN, false, true));
            assertSame(ProtosBooleanValue.TRUE,
                    less.execute(big, module, Double.POSITIVE_INFINITY, false, true));
            assertSame(miss, less.execute(big, module, generic, false, true));
            assertSame(ProtosBooleanValue.TRUE, less.execute(1L, module, 2L, true, true));

            // A large Integer of another Prelude, or a delegating look-alike, is not owned.
            ProtosPrelude foreign = new ProtosCoreBootstrap().bootstrap(CORE);
            Object foreignBig = ProtosNumericValueSupport.addIntegers(
                    integer(Long.MAX_VALUE), integer(1L), foreign);
            Object lookAlike = new ProtosObjectValue(prelude.bigIntegerPrototype());
            for (Object outsider : List.of(foreignBig, lookAlike)) {
                assertSame(miss, add.execute(outsider, module, 1L, false, true, false, false));
                assertSame(miss, add.execute(1L, module, outsider, true, false, false, false));
                assertSame(miss, less.execute(outsider, module, 1L, false, true));
                assertFalse(ProtosPrimitiveNumericSendFastPathsBase.ownedLarge(outsider, prelude));
            }
            assertTrue(ProtosPrimitiveNumericSendFastPathsBase.ownedLarge(foreignBig, foreign));
        });
        System.out.println("PERF040_E2_LARGE_INTEGER_LANES=PASS");
    }

    /** Part B, 13: an alternative Core keeps BigInteger Integer lanes off. */
    @Test
    void alternativeCoreDoesNotAdmitLargeIntegerLanes() throws Exception {
        ProtosPrelude canonical = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosObjectValue number = canonical.numberPrototype();
        ProtosObjectValue integer = new ProtosObjectValue(number);
        ProtosClosureValue standard = (ProtosClosureValue)
                canonical.integerPrototype().readLocalSlot("+").orElseThrow();
        integer.createLocalSlot(
                "+", ProtosClosureValue.nativeClosure(standard.nativeBody().orElseThrow()));
        ProtosObjectValue smallInteger = new ProtosObjectValue(integer);
        ProtosObjectValue bigInteger = new ProtosObjectValue(integer);
        integer.freeze();
        smallInteger.freeze();
        bigInteger.freeze();
        ProtosPrelude alternative =
                syntheticPrelude(
                        canonical,
                        Map.of(
                                "Number", number,
                                "Integer", integer,
                                "SmallInteger", smallInteger,
                                "BigInteger", bigInteger,
                                "Float", canonical.floatPrototype()));
        Object big = ProtosNumericValueSupport.addIntegers(
                integer(Long.MAX_VALUE), integer(1L), alternative);

        assertTrue(ProtosPrimitiveNumericSendFastPathsBase.ownedLarge(big, alternative));
        assertFalse(
                ProtosPrimitiveNumericSendFastPathsBase.admittedCanonicalNumericLane(
                        big, false, 1L, true, alternative,
                        ProtosNumericAddNode.INTEGER_LANE, ProtosNumericAddNode.FLOAT_LANE));
        assertFalse(
                ProtosPrimitiveNumericSendFastPathsBase.admittedCanonicalNumericLane(
                        1L, true, big, false, alternative,
                        ProtosNumericAddNode.INTEGER_LANE, ProtosNumericAddNode.FLOAT_LANE));
        // The untouched standard Float '+' still admits Float + BigInteger.
        assertTrue(
                ProtosPrimitiveNumericSendFastPathsBase.admittedCanonicalNumericLane(
                        1.5d, true, big, false, alternative,
                        ProtosNumericAddNode.INTEGER_LANE, ProtosNumericAddNode.FLOAT_LANE));
        System.out.println("PERF040_E2_ALTERNATIVE_CORE=PASS");
    }

    private static ProtosNumericCoreAdmission certificateField(ProtosPrelude prelude)
            throws ReflectiveOperationException {
        Field field = ProtosPrelude.class.getDeclaredField("numericCoreAdmission");
        field.setAccessible(true);
        return (ProtosNumericCoreAdmission) field.get(prelude);
    }

    private static ProtosIntegerValue integer(long value) {
        return new ProtosIntegerValue(value);
    }

    /**
     * Matrix 14, 15: the lowerer emits exactly the operation of the site's
     * selector, in NORMAL and DEBUG, and it survives a complete reparse with
     * source and instrumentation.
     */
    @Test
    void selectorOwnsItsOperationAcrossProfilesAndReparse() throws Exception {
        for (boolean debug : new boolean[] {false, true}) {
            withCore(debug, module -> {
                for (Map.Entry<String, String> entry : OPERATIONS.entrySet()) {
                    ProtosClosureValue site =
                            closure("(x, y) => x " + entry.getKey() + " y", module);
                    assertOnlyOperation(site, entry.getValue());

                    Object before = normalized(call(site, module,
                            new ProtosIntegerValue(6L), new ProtosIntegerValue(3L)));
                    rootOf(site).getRootNodes().ensureComplete();
                    assertOnlyOperation(site, entry.getValue());
                    assertEquals(before, normalized(call(site, module,
                            new ProtosIntegerValue(6L), new ProtosIntegerValue(3L))),
                            entry.getKey());
                }
            });
        }
        System.out.println("PERF040_E1_SELECTOR_OPERATION_REPARSE=PASS");
    }

    /* Each instruction denotes the longest operation name it contains. */
    private static void assertOnlyOperation(ProtosClosureValue site, String expected) {
        List<String> names = instructionNames(site);
        List<String> denoted = new ArrayList<>();
        for (String name : names) {
            String longest = null;
            for (String operation : OPERATIONS.values()) {
                if (name.contains(operation)
                        && (longest == null || operation.length() > longest.length())) {
                    longest = operation;
                }
            }
            if (longest != null) {
                denoted.add(longest);
            }
        }
        assertFalse(denoted.isEmpty(), () -> "expected " + expected + " in " + names);
        assertTrue(denoted.stream().allMatch(expected::equals),
                () -> "only " + expected + " expected in " + names);
    }

    private static Object normalized(Object value) {
        return value instanceof ProtosIntegerValue integer ? integer.longValue() : value;
    }

    private static int occurrences(String text, String fragment) {
        int count = 0;
        for (int at = text.indexOf(fragment); at >= 0; at = text.indexOf(fragment, at + 1)) {
            count++;
        }
        return count;
    }

    private static ProtosPrelude syntheticPrelude(
            ProtosPrelude canonical, Map<String, ProtosObjectValue> numeric) {
        ProtosObjectValue bindings = new ProtosObjectValue(canonical.contextPrototype());
        bindings.createLocalSlot("Context", canonical.contextPrototype());
        bindings.createLocalSlot("Error", canonical.errorPrototype());
        numeric.forEach(bindings::createLocalSlot);
        bindings.freeze();
        return new ProtosPrelude(bindings, canonical.contextPrototype());
    }

    private static List<Integer> allLanes() {
        List<Integer> lanes = new ArrayList<>();
        for (int bit = 0; bit < 16; bit++) {
            lanes.add(1 << bit);
        }
        return lanes;
    }

    private static List<int[]> lanePairs() {
        return List.of(
                new int[] {ProtosNumericAddNode.INTEGER_LANE, ProtosNumericAddNode.FLOAT_LANE},
                new int[] {ProtosNumericDivideNode.INTEGER_LANE,
                    ProtosNumericDivideNode.FLOAT_LANE},
                new int[] {ProtosNumericLessNode.INTEGER_LANE, ProtosNumericLessNode.FLOAT_LANE},
                new int[] {ProtosNumericGreaterEqualNode.INTEGER_LANE,
                    ProtosNumericGreaterEqualNode.FLOAT_LANE});
    }

    private interface CoreTest {
        void run(ProtosActivation module) throws Exception;
    }

    private static void withCore(CoreTest test) throws Exception {
        withCore(false, test);
    }

    private static void withCore(boolean debug, CoreTest test) throws Exception {
        try (Context context = context(debug)) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                assertEquals(
                        debug ? ProtosExecutionMode.DEBUG : ProtosExecutionMode.NORMAL,
                        ProtosLanguageContext.current().executionMode());
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                test.run(prelude.newModuleActivation());
            } finally {
                context.leave();
            }
        }
    }

    private static Context context(boolean debug) {
        Context.Builder builder =
                Context.newBuilder(ProtosLanguage.ID)
                        .option("protos.CoreRoot", CORE.toAbsolutePath().toString());
        if (debug) {
            builder.allowExperimentalOptions(true).option("protos.Debug", "true");
        }
        return builder.build();
    }

    private static String mode(boolean debug) {
        return debug ? "DEBUG" : "NORMAL";
    }

    private static boolean eval(Context context, String source) {
        return context.eval(ProtosLanguage.ID, source).asBoolean();
    }

    private static ProtosClosureValue closure(String characters, ProtosActivation activation) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, "perf040-e1.protos")
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        CallTarget target = ProtosLanguageContext.current().parsePublic(source);
        return assertInstanceOf(ProtosClosureValue.class, target.call(activation));
    }

    private static Object call(
            ProtosClosureValue closure, ProtosActivation caller, Object... supplied) {
        ProtosLanguageContext entered = ProtosLanguageContext.currentIfEnteredForRuntime();
        ProtosClosureValue selected =
                ProtosClosureCallPreparation.directClosureCallSelectionOrNull(closure, caller);
        assertSame(closure, selected, "canonical direct Closure-call selection");
        RootCallTarget target =
                ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(
                        closure, entered);
        ProtosPreparedCalls.PreparedClosureCall prepared =
                ProtosBytecodeRootNode.PrepareClosureCallArguments.fastDirect(
                        false,
                        closure,
                        caller,
                        supplied,
                        selected,
                        selected.definition(),
                        entered,
                        selected.definition(),
                        entered,
                        target);
        Object result =
                ProtosBytecodeRootNode.EnterClosureCall.ordinaryIndirect(
                        assertInstanceOf(
                                ProtosPreparedCalls.OrdinarySourceCall.class, prepared),
                        IndirectCallNode.create());
        return ProtosNumericValueSupport.guestValue(prepared.finish(result));
    }

    private static ProtosBytecodeRootNode rootOf(ProtosClosureValue closure) {
        return closure.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
    }

    private static List<String> instructionNames(ProtosClosureValue closure) {
        return rootOf(closure)
                .getBytecodeNode()
                .getInstructionsAsList()
                .stream()
                .map(Instruction::getName)
                .toList();
    }
}

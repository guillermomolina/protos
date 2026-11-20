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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.oracle.truffle.api.debug.Debugger;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosBinary64Rounding;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.source.Source;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF040-E1 acceptance, reconciled with I115: the per-selector numeric
 * operations ({@code NumberAdd}, {@code NumberSubtract}, {@code
 * NumberMultiply}, {@code NumberDivide}, {@code RichCompare*}) keep every
 * numeric result exact whatever order operand families arrive in, and every
 * non-primitive pair takes the single in-node generic route.
 *
 * <p>Addition guards the selected behavior through the current prototype chain;
 * its standard primitive branch remains specialized. Exact promotion uses the
 * executing invocation's Core, including when one Context hosts several Cores.
 */
final class ProtosPerf040E1NumericOperationReconciliationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path EXECUTION_SOURCES =
            Path.of("src", "main", "java", "com", "guillermomolina", "protos", "execution");

    private static final Map<String, String> OPERATIONS =
            Map.of(
                    "+", "NumberAdd",
                    "-", "NumberSubtract",
                    "*", "NumberMultiply",
                    "/", "NumberDivide",
                    "<", "RichCompareLt",
                    "<=", "RichCompareLe",
                    ">", "RichCompareGt",
                    ">=", "RichCompareGe");

    /* The surviving node behind each canonical operation, in OPERATIONS order of meaning. */
    private static final Map<String, Class<?>> NODES =
            Map.of(
                    "NumberAdd", ProtosNumberAddNode.class,
                    "NumberSubtract", ProtosNumberSubtractNode.class,
                    "NumberMultiply", ProtosNumberMultiplyNode.class,
                    "NumberDivide", ProtosNumberDivideNode.class,
                    "RichCompareLt", ProtosRichCompareLtNode.class,
                    "RichCompareLe", ProtosRichCompareLeNode.class,
                    "RichCompareGt", ProtosRichCompareGtNode.class,
                    "RichCompareGe", ProtosRichCompareGeNode.class);

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
                    // 16. the generic route keeps evaluation, materialization and Error semantics
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

    /*
     * Former direct-node arrival-order matrices, now executed on one real
     * operation site each: generic operands before and after the numeric
     * lanes, overflow, Fraction and zero divisor from inside the integral
     * lane, and ordering with NaN and signed zeros.
     */
    private static final List<String> ARRIVAL_ORDER_SEQUENCES =
            List.of(
                    // Generic -> Integer -> Integer
                    "add: (x, y) => x + y\n"
                            + "(add(\"a\", \"b\") == \"ab\") && (add(1, 2) == 3) && (add(3, 4) == 7)",
                    // Integer -> Generic -> Integer -> overflow -> Integer -> Float -> Integer
                    "add: (x, y) => x + y\n"
                            + "(add(1, 2) == 3) && (add(\"a\", \"b\") == \"ab\") && (add(3, 4) == 7)"
                            + " && BigInteger.recognizes(add(9223372036854775807, 1))"
                            + " && (add(5, 6) == 11) && (add(1, 1.5) == 2.5)"
                            + " && Float.recognizes(add(1, 1.5)) && (add(4, 5) == 9)"
                            + " && SmallInteger.recognizes(add(4, 5))",
                    // Divide: Fraction and zero divisor leave the integral lane exactly
                    "div: (x, y) => x / y\n"
                            + "failed: false\n"
                            + "(div(6, 3) == 2) && Fraction.recognizes(div(5, 2))"
                            + " && (div(5, 2) == (10 / 4))"
                            + " && Error.handle(() => { div(5, 0) }, error => { failed = true; true })"
                            + " && failed && (div(8, 2) == 4) && SmallInteger.recognizes(div(8, 2))",
                    // Ordering: generic before and after, NaN unordered, signed zeros equal
                    "lt: (x, y) => x < y\n"
                            + "le: (x, y) => x <= y\n"
                            + "Error.handle(() => { lt(\"a\", 1) }, error => { false })\n"
                            + "n: 0.0 / 0.0\n"
                            + "z: 0.0 * (0.0 - 1.0)\n"
                            + "lt(1, 2) && !lt(n, 1) && !lt(z, 0.0) && lt(1.5, 2)"
                            + " && le(z, 0) && !le(n, n)"
                            + " && Error.handle(() => { lt(1, \"a\") }, error => { true })"
                            + " && lt(1, 2)");

    /*
     * One root reuses several operations of different result representations:
     * a Boolean comparison next to a Long, Double or BigInteger sum, in both
     * orders and nested. Every operation owns its temporaries.
     */
    private static final List<String> SHARED_ROOT_SEQUENCES =
            List.of(
                    // comparison followed by addition
                    "cmpAdd: (x, y) => {\n"
                            + "    less: x < y\n"
                            + "    sum: x + y\n"
                            + "    less.ifTrueIfFalse(() => { sum }, () => { 0 - sum })\n"
                            + "}\n"
                            + "(cmpAdd(3, 4) == 7) && (cmpAdd(4, 3) == (0 - 7)) && (cmpAdd(1, 2) == 3)",
                    // addition followed by comparison
                    "addCmp: (x, y) => {\n"
                            + "    sum: x + y\n"
                            + "    sum < 10\n"
                            + "}\n"
                            + "addCmp(3, 4) && !addCmp(6, 5) && (addCmp(1, 1) == true)",
                    // nested operations
                    "nest: (a, b, c) => ((a + b) * c) < (a + (b * c))\n"
                            + "sumNest: (a, b, c) => (a + b) + ((b + c) < 6).ifTrueIfFalse(() => { 1 }, () => { 2 })\n"
                            + "!nest(1, 2, 3) && nest(0 - 1, 2, 3) && (sumNest(1, 2, 3) == 4)"
                            + " && (sumNest(1, 2, 4) == 5) && nest(0 - 1, 2, 3)",
                    // Long -> Double -> Long through one root
                    "mixed: (x, y) => {\n"
                            + "    s: x + y\n"
                            + "    l: s < 10\n"
                            + "    l.ifTrueIfFalse(() => { s + 1 }, () => { s - 1 })\n"
                            + "}\n"
                            + "(mixed(1, 2) == 4) && SmallInteger.recognizes(mixed(1, 2))"
                            + " && (mixed(1, 2.5) == 4.5) && Float.recognizes(mixed(1, 2.5))"
                            + " && (mixed(3, 4) == 8) && SmallInteger.recognizes(mixed(3, 4))"
                            + " && (mixed(6, 6) == 11)",
                    // Long overflow promotes exactly next to a comparison
                    "step: (x) => {\n"
                            + "    next: x + 1\n"
                            + "    (next > x).ifTrueIfFalse(() => { next }, () => { 0 })\n"
                            + "}\n"
                            + "high: 9223372036854775807\n"
                            + "(step(1) == 2) && BigInteger.recognizes(step(high))"
                            + " && ((step(high) - 1) == high) && (step(5) == 6)"
                            + " && SmallInteger.recognizes(step(5))");

    private static String orderingSequence(String selector, String numeric) {
        return "lt: (x, y) => x " + selector + " y\n"
                + "Error.handle(() => { lt(\"a\", \"b\") }, error => { false })\n"
                + "first: " + numeric + "\n"
                + "Error.handle(() => { lt(\"a\", 1) }, error => { false })\n"
                + "first && " + numeric;
    }

    /**
     * Matrix 1-9, 13, 14, 16: without and with an attached debugger, in two
     * independent Contexts each.
     */
    @Test
    void sameSiteSequencesHoldWithAndWithoutDebuggerAcrossContexts() {
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

    @Test
    void sharedRootSequencesReuseOneRootAcrossRepresentations() {
        for (boolean debug : new boolean[] {false, true}) {
            try (Context context = context(debug)) {
                for (String sequence : SHARED_ROOT_SEQUENCES) {
                    assertTrue(eval(context, sequence), () -> mode(debug) + ":\n" + sequence);
                    assertTrue(eval(context, sequence), () -> mode(debug) + " again:\n" + sequence);
                }
            }
        }
        System.out.println("PERF040_E1_SHARED_ROOT_SEQUENCES=PASS");
    }

    /**
     * One root holding a comparison and an addition: standard selection,
     * then a redefined {@code +}, then the standard selection again.
     */
    @Test
    void sharedRootReturnsToStandardSelectionAfterRedefinition() throws Exception {
        withCanonicalPrelude(canonical -> {
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, standardPlus, false);
            ProtosActivation module = alternative.prelude().newModuleActivation();
            ProtosClosureValue site = closure(
                    "(x, y) => {\n    ordered: x < y\n    sum: x + y\n"
                            + "    ordered.ifTrueIfFalse(() => { sum }, () => { sum + 100 })\n}",
                    module);
            for (long round = 0; round < 3; round++) {
                alternative.integer().assignLocalSlot("+", standardPlus);
                assertEquals(3L, normalized(call(site, module, 1L, 2L)));
                assertEquals(103L, normalized(call(site, module, 2L, 1L)));
                assertTrue(ProtosNumericValueSupport.isLargeInteger(
                        call(site, module, 1L, Long.MAX_VALUE)));
                alternative.integer().assignLocalSlot("+", answering(5L));
                assertEquals(5L, normalized(call(site, module, 1L, 2L)));
                assertEquals(5L, normalized(call(site, module, 2L, 1L)));
            }
            alternative.integer().assignLocalSlot("+", standardPlus);
            assertEquals(3L, normalized(call(site, module, 1L, 2L)));
        });
        System.out.println("PERF040_E1_SHARED_ROOT_STANDARD_REDEFINED=PASS");
    }

    /**
     * A selected {@code +} that suspends inside a task resumes the operation
     * once: its operands are not evaluated again, the method runs once and the
     * following comparison consumes exactly one result.
     */
    @Test
    void suspendingSelectedAdditionResumesWithoutRepeatingOperands() throws Exception {
        withCanonicalPrelude(canonical -> {
            int[] operands = new int[1];
            int[] selected = new int[1];
            ProtosClosureValue next = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        operands[0]++;
                        return Long.valueOf(operands[0]);
                    });
            ProtosClosureValue mark = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        selected[0]++;
                        return Long.valueOf(selected[0]);
                    });
            /*
             * The outermost standard host entry of the embedded Process is
             * the suspendible Task-free entry: both modules share the
             * RootActor execution domain, so the pending Future.value()
             * yields its continuation and the host entry resumes it.
             */
            ProtosEmbeddedProcess embedding =
                    ProtosLanguageContext.current().embeddedProcessForHostEntry();
            ProtosActorExecutionDomain rootDomain = embedding.rootExecutionDomain();
            ProtosActivation canonicalModule = canonical.newModuleActivation(
                    new ProtosActorModuleState(), null,
                    canonical.newExecutionContext(), rootDomain);
            Object suspendingPlus = call(
                    closure("(mark) => (other) => {\n    mark()\n"
                            + "    (() => { 42 }).future().value()\n}", canonicalModule),
                    canonicalModule,
                    mark);
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, suspendingPlus, true);
            ProtosActivation module = alternative.prelude().newModuleActivation(
                    new ProtosActorModuleState(), null,
                    alternative.prelude().newExecutionContext(), rootDomain);
            ProtosClosureValue site = closure(
                    "(next) => {\n"
                            + "    (() => {\n"
                            + "        sum: next() + next()\n"
                            + "        (sum < 50).ifTrueIfFalse(() => { sum }, () => { 0 })\n"
                            + "    }).future().value()\n"
                            + "}",
                    module);
            ProtosHostExecutableClosure hostEntry =
                    ProtosHostExecutableClosure.prepareForEmbeddingRuntime(
                            site, module, embedding);
            /*
             * Read-only observation of a real host-entry suspension. It proves the outermost
             * entry held a suspended continuation; it does not by itself prove that the
             * redefined + suspended inside its Task (no read-only Task observation exists).
             */
            java.util.concurrent.atomic.AtomicInteger hostSuspensions =
                    new java.util.concurrent.atomic.AtomicInteger();
            java.util.function.Consumer<com.guillermomolina.protos.runtime.ProtosFutureValue>
                    previous = ProtosHostEntrySuspension.suspendedForTesting;
            ProtosHostEntrySuspension.suspendedForTesting =
                    future -> hostSuspensions.incrementAndGet();
            try {
                assertEquals(42L, normalized(
                        InteropLibrary.getUncached().execute(hostEntry, next)));
                assertEquals(2, operands[0], "each operand is evaluated once");
                assertEquals(1, selected[0], "the selected method runs once");
                assertTrue(hostSuspensions.get() >= 1,
                        "the host entry really suspended at least once");
            } finally {
                ProtosHostEntrySuspension.suspendedForTesting = previous;
            }
        });
        System.out.println("PERF040_E1_SUSPENDING_SELECTED_ADDITION=PASS");
    }

    /**
     * One site consumes an arithmetic result and a comparison result after
     * their operations, through the standard selection, a redefined
     * selection, a redefined selection that suspends inside a Task, and the
     * restored standard selection. Each operand is evaluated once per
     * execution (also across a resumption), each selected method runs once,
     * the comparison result is validated as a canonical Boolean after its
     * resumption, and the restored site answers exact standard results.
     */
    @Test
    void numericResultsConsumedAfterARealContinuationKeepTheSelectedValue() throws Exception {
        withCanonicalPrelude(canonical -> {
            int[] operands = new int[1];
            int[] selected = new int[1];
            ProtosClosureValue next = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        operands[0]++;
                        return Long.valueOf(operands[0] % 2 == 1 ? 1L : 2L);
                    });
            ProtosClosureValue mark = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        selected[0]++;
                        return Long.valueOf(selected[0]);
                    });
            ProtosEmbeddedProcess embedding =
                    ProtosLanguageContext.current().embeddedProcessForHostEntry();
            ProtosActorExecutionDomain rootDomain = embedding.rootExecutionDomain();
            ProtosActivation canonicalModule = canonical.newModuleActivation(
                    new ProtosActorModuleState(), null,
                    canonical.newExecutionContext(), rootDomain);
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            Object suspendingPlus = call(
                    closure("(mark) => (other) => {\n    mark()\n"
                            + "    (() => { 42 }).future().value()\n}", canonicalModule),
                    canonicalModule,
                    mark);
            Object suspendingLess = call(
                    closure("(mark) => (other) => {\n    mark()\n"
                            + "    (() => { false }).future().value()\n}", canonicalModule),
                    canonicalModule,
                    mark);
            Object suspendingNonBoolean = call(
                    closure("(mark) => (other) => {\n    mark()\n"
                            + "    (() => { 1 }).future().value()\n}", canonicalModule),
                    canonicalModule,
                    mark);
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, standardPlus, false);
            ProtosActivation module = alternative.prelude().newModuleActivation(
                    new ProtosActorModuleState(), null,
                    alternative.prelude().newExecutionContext(), rootDomain);
            // next() answers 1, 2, 1, 2: sum is 1 + 2 and ordered is 1 < 2.
            ProtosClosureValue site = closure(
                    "(next) => {\n"
                            + "    (() => {\n"
                            + "        sum: next() + next()\n"
                            + "        ordered: next() < next()\n"
                            + "        ordered.ifTrueIfFalse(() => { sum }, () => { sum + 1000 })\n"
                            + "    }).future().value()\n"
                            + "}",
                    module);
            ProtosHostExecutableClosure hostEntry =
                    ProtosHostExecutableClosure.prepareForEmbeddingRuntime(
                            site, module, embedding);
            java.util.function.Supplier<Object> run = () -> {
                try {
                    return InteropLibrary.getUncached().execute(hostEntry, next);
                } catch (com.oracle.truffle.api.interop.InteropException e) {
                    throw new AssertionError(e);
                }
            };
            java.util.concurrent.atomic.AtomicInteger hostSuspensions =
                    new java.util.concurrent.atomic.AtomicInteger();
            java.util.function.Consumer<com.guillermomolina.protos.runtime.ProtosFutureValue>
                    previous = ProtosHostEntrySuspension.suspendedForTesting;
            ProtosHostEntrySuspension.suspendedForTesting =
                    future -> hostSuspensions.incrementAndGet();
            try {
                for (int round = 0; round < 3; round++) {
                    operands[0] = 0;
                    selected[0] = 0;
                    assertEquals(3L, normalized(run.get()), "standard");
                    assertEquals(4, operands[0], "standard operands");

                    alternative.integer().assignLocalSlot("+", answering(5L));
                    assertEquals(5L, normalized(run.get()), "redefined +");
                    assertEquals(8, operands[0], "redefined + operands");

                    alternative.integer().assignLocalSlot("+", suspendingPlus);
                    assertEquals(42L, normalized(run.get()), "suspending +");
                    assertEquals(12, operands[0], "suspending + operands");
                    assertEquals(1, selected[0], "the suspending + runs once");

                    alternative.integer().assignLocalSlot("+", standardPlus);
                    alternative.integer().createLocalSlot("<", suspendingLess);
                    assertEquals(1003L, normalized(run.get()), "suspending <");
                    assertEquals(16, operands[0], "suspending < operands");
                    assertEquals(2, selected[0], "the suspending < runs once");

                    alternative.integer().removeLocalSlot("<");
                    assertEquals(3L, normalized(run.get()), "restored");
                    assertEquals(20, operands[0], "restored operands");
                    assertEquals(2, selected[0], "no selected method after restore");
                }
                assertTrue(hostSuspensions.get() >= 1,
                        "the host entry really suspended at least once");
                ProtosClosureValue add = closure("(x, y) => x + y", module);
                for (long i = 0; i < 20; i++) {
                    assertEquals(i + 1, normalized(call(add, module, i, 1L)));
                }
                assertTrue(ProtosNumericValueSupport.isLargeInteger(
                        call(add, module, Long.MAX_VALUE, 1L)),
                        "the restored standard + still promotes exactly");

                // Last: an escaping guest Error terminates the embedded Process.
                operands[0] = 0;
                selected[0] = 0;
                alternative.integer().createLocalSlot("<", suspendingNonBoolean);
                assertThrows(ProtosSignalException.class, run::get,
                        "a resumed non-Boolean ordering result signals");
                assertEquals(4, operands[0], "non-Boolean < operands");
                assertEquals(1, selected[0], "the non-Boolean < runs once");
                assertFalse(embedding.isLive(),
                        "an Error escaping the outermost entry terminates its Process");
            } finally {
                ProtosHostEntrySuspension.suspendedForTesting = previous;
            }
        });
        System.out.println("PERF040_E1_RESULT_AFTER_CONTINUATION=PASS");
    }

    /** Regression for the original I115-LONG-5 overflow in a standalone module root. */
    @Test
    void i115StandaloneModuleOverflowUsesContextNumericFactory() {
        String source = "big: 9223372036854775807 + 1\n"
                + "BigInteger.recognizes(big)\n";
        try (Context isolated = context(false)) {
            assertTrue(eval(isolated, source), "module-root overflow must promote exactly");
        }
    }

    /**
     * Matrix 13, reconciled: each Prelude owns its own frozen numeric
     * prototypes. The former per-Prelude admission certificate no longer
     * exists (I115 recorded divergence), so only the prototype structure it
     * certified is asserted.
     */
    @Test
    void canonicalCoreNumericPrototypesAreFrozenPerPrelude() throws Exception {
        ProtosPrelude first = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosPrelude second = new ProtosCoreBootstrap().bootstrap(CORE);

        assertNotSame(first.integerPrototype(), second.integerPrototype());
        assertNotSame(first.floatPrototype(), second.floatPrototype());
        for (String binding : List.of("Number", "Integer", "SmallInteger", "Float")) {
            ProtosObjectValue prototype =
                    (ProtosObjectValue) first.bindings().readLocalSlot(binding).orElseThrow();
            assertTrue(prototype.isFrozen(), binding);
        }
        System.out.println("PERF040_E1_PER_PRELUDE_PROTOTYPES=PASS");
    }

    /**
     * Matrix 11, execution level: in a noncanonical Prelude whose Integer
     * prototype selects a genuinely different {@code +}, an ordinary
     * {@code +} send on Integers runs that selected method, before and at
     * signed-64 overflow, at the same operation site. The standard arithmetic
     * fast path must not bypass this ordinary selected behavior.
     */
    @Test
    void alternativeIntegerPlusIsTheSelectedMethod() throws Exception {
        withAlternativeIntegerPlus(module -> {
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            assertEquals(ALTERNATIVE_PLUS_RESULT, normalized(call(add, module, 1L, 2L)));
            assertEquals(ALTERNATIVE_PLUS_RESULT,
                    normalized(call(add, module, Long.MAX_VALUE, 1L)),
                    "overflow does not bypass the selected method");
            assertEquals(ALTERNATIVE_PLUS_RESULT, normalized(call(add, module, 3L, 4L)));
        });
        System.out.println("PERF040_E1_ALTERNATIVE_CORE_SELECTION=PASS");
    }

    /**
     * Matrix 11, per selector: replacing Integer {@code +} leaves every other
     * selector and the untouched Float prototype on standard behavior, with
     * exact results, in that same noncanonical Prelude.
     */
    @Test
    void alternativeCoreKeepsUntouchedSelectorsStandard() throws Exception {
        withAlternativeIntegerPlus(module -> {
            ProtosClosureValue subtract = closure("(x, y) => x - y", module);
            ProtosClosureValue multiply = closure("(x, y) => x * y", module);
            ProtosClosureValue divide = closure("(x, y) => x / y", module);
            ProtosClosureValue less = closure("(x, y) => x < y", module);
            ProtosClosureValue add = closure("(x, y) => x + y", module);

            assertEquals(-1L, normalized(call(subtract, module, 2L, 3L)));
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    call(multiply, module, Long.MAX_VALUE, 2L)));
            assertEquals(2L, normalized(call(divide, module, 6L, 3L)));
            assertTrue(ProtosNumericValueSupport.isFraction(call(divide, module, 5L, 2L)));
            assertSame(Boolean.TRUE, call(less, module, 1L, 2L));
            assertEquals(2.5d, call(add, module, 1.5d, 1.0d), "Float + is untouched");
        });
        System.out.println("PERF040_E1_ALTERNATIVE_CORE_PER_SELECTOR=PASS");
    }

    /**
     * Matrix 10, execution level: an ordinary object delegating to a numeric
     * prototype is never a numeric value; its own selected method runs at
     * the numeric operation site, between and after genuine numeric calls.
     */
    @Test
    void delegatingLookAlikeUsesItsOwnSelection() throws Exception {
        withCore(module -> {
            ProtosPrelude prelude = module.preludeOrNullForRuntime();
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            ProtosClosureValue less = closure("(x, y) => x < y", module);
            for (ProtosObjectValue prototype : List.of(
                    prelude.numberPrototype(),
                    prelude.integerPrototype(),
                    prelude.floatPrototype())) {
                ProtosObjectValue lookAlike = new ProtosObjectValue(prototype);
                lookAlike.createLocalSlot("+", answering(17L));
                lookAlike.createLocalSlot("<", answeringFalse());

                assertEquals(3L, normalized(call(add, module, 1L, 2L)));
                assertEquals(17L, normalized(call(add, module, lookAlike, 1L)));
                assertEquals(3L, normalized(call(add, module, 1L, 2L)));
                assertSame(Boolean.TRUE, call(less, module, 1L, 2L));
                assertSame(Boolean.FALSE, call(less, module, lookAlike, 2L));
                assertSame(Boolean.TRUE, call(less, module, 1L, 2L));
            }
        });
        System.out.println("PERF040_E1_ORDINARY_DELEGATES=PASS");
    }

    /**
     * Replacement and invalidation at one operation site: a nonstandard
     * receiver's selected method is replaced between calls and the site
     * runs the new selection, while Integer receivers keep standard results
     * at the same site, for '+', '/' and '<'.
     */
    @Test
    void replacedReceiverMethodInvalidatesTheNumericSite() throws Exception {
        withCore(module -> {
            for (Map.Entry<String, Object[]> entry : Map.of(
                    "+", new Object[] {9L},
                    "-", new Object[] {3L},
                    "*", new Object[] {18L},
                    "/", new Object[] {2L},
                    "div", new Object[] {2L},
                    "mod", new Object[] {0L},
                    "<", new Object[] {Boolean.FALSE},
                    "<=", new Object[] {Boolean.FALSE},
                    ">", new Object[] {Boolean.TRUE},
                    ">=", new Object[] {Boolean.TRUE}).entrySet()) {
                String selector = entry.getKey();
                String expression = selector.equals("div") || selector.equals("mod")
                        ? "x." + selector + "(y)" : "x " + selector + " y";
                ProtosClosureValue site = closure("(x, y) => " + expression, module);
                ProtosObjectValue custom = new ProtosObjectValue(ProtosObjectValue.rootObject());
                boolean ordering = List.of("<", "<=", ">", ">=").contains(selector);
                Object first = ordering ? Boolean.FALSE : Long.valueOf(41L);
                Object second = ordering ? Boolean.TRUE : Long.valueOf(42L);
                custom.createLocalSlot(selector, ordering ? answeringFalse() : answering(41L));

                Object standard = normalized(call(site, module, 6L, 3L));
                assertEquals(entry.getValue()[0], standard, selector);
                assertEquals(first, normalized(call(site, module, custom, 1L)), selector);
                custom.assignLocalSlot(selector, ordering
                        ? ProtosClosureValue.nativeClosure((activation, supplied) -> Boolean.TRUE)
                        : answering(42L));
                assertEquals(second, normalized(call(site, module, custom, 1L)),
                        selector + ": the replaced selection is observed");
                assertEquals(standard, normalized(call(site, module, 6L, 3L)), selector);
                if (ordering) {
                    custom.assignLocalSlot(selector, answering(41L));
                    assertThrows(com.guillermomolina.protos.runtime.ProtosSignalException.class,
                            () -> call(site, module, custom, 1L), selector);
                }
            }
        });
        System.out.println("PERF040_E1_REPLACEMENT_INVALIDATION=PASS");
    }

    /**
     * A Closure that captured its Prelude selects the standard operators
     * through the single family of numeric entries, for {@code long} and
     * {@code double} receivers, with exact guest results past warm-up.
     */
    @Test
    void capturedPreludeSelectsStandardOperators() throws Exception {
        withCore(module -> {
            ProtosPrelude prelude = module.preludeOrNullForRuntime();
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            ProtosClosureValue subtract = closure("(x, y) => x - y", module);
            ProtosClosureValue multiply = closure("(x, y) => x * y", module);
            ProtosClosureValue less = closure("(x, y) => x < y", module);
            ProtosClosureValue greater = closure("(x, y) => x > y", module);
            for (ProtosClosureValue site : List.of(add, subtract, multiply, less, greater)) {
                assertSame(prelude, site.preludeOrNullForRuntime(), "the Closure captured its Prelude");
            }
            for (long round = 0; round < 20; round++) {
                assertEquals(round + 3L, normalized(call(add, module, round, 3L)));
                assertEquals(round - 3L, normalized(call(subtract, module, round, 3L)));
                assertEquals(round * 3L, normalized(call(multiply, module, round, 3L)));
                assertSame(round < 3L, call(less, module, round, 3L));
                assertSame(round > 3L, call(greater, module, round, 3L));
                double x = round + 0.5d;
                assertEquals(x + 1.25d, call(add, module, x, 1.25d));
                assertEquals(x - 1.25d, call(subtract, module, x, 1.25d));
                assertEquals(x * 1.25d, call(multiply, module, x, 1.25d));
                assertSame(x < 3.0d, call(less, module, x, 3.0d));
                assertSame(x > 3.0d, call(greater, module, x, 3.0d));
            }
        });
        System.out.println("PERF040_E1_CAPTURED_PRELUDE_STANDARD=PASS");
    }

    /**
     * A Closure without its own Prelude inherits the caller's: one site
     * alternates between callers of a canonical Prelude and of a Prelude
     * whose Integer {@code +} is redefined, and each invocation selects
     * under its own caller's Prelude without contaminating the other.
     */
    @Test
    void inheritedPreludeSelectsPerCaller() throws Exception {
        withCanonicalPrelude(canonical -> {
            AlternativeIntegers alternative = alternativeIntegers(
                    canonical, answering(ALTERNATIVE_PLUS_RESULT), true);
            ProtosActivation canonicalModule = canonical.newModuleActivation();
            ProtosActivation alternativeModule = alternative.prelude().newModuleActivation();
            assertSame(canonical, canonicalModule.preludeOrNullForRuntime());
            assertSame(alternative.prelude(), alternativeModule.preludeOrNullForRuntime());
            ProtosClosureValue inherited = withoutPrelude(
                    closure("(x, y) => x + y", canonicalModule));
            assertNull(inherited.preludeOrNullForRuntime(), "the Closure owns no Prelude");
            for (int round = 0; round < 12; round++) {
                assertEquals(3L, normalized(call(inherited, canonicalModule, 1L, 2L)),
                        "canonical caller, round " + round);
                assertEquals(ALTERNATIVE_PLUS_RESULT,
                        normalized(call(inherited, alternativeModule, 1L, 2L)),
                        "alternative caller, round " + round);
                assertEquals(2.5d, call(inherited, alternativeModule, 1.5d, 1.0d),
                        "the alternative Float + stays standard");
            }
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    call(inherited, canonicalModule, Long.MAX_VALUE, 1L)));
            assertEquals(ALTERNATIVE_PLUS_RESULT,
                    normalized(call(inherited, alternativeModule, Long.MAX_VALUE, 1L)));
        });
        System.out.println("PERF040_E1_INHERITED_PRELUDE=PASS");
    }

    /**
     * One compiled site alternates the standard selection, a redefined
     * {@code +}, the restored standard and a second redefinition. Each state
     * evaluates both operands exactly once per execution and runs only the
     * method actually selected.
     */
    @Test
    void sameSiteAlternatesStandardAndRedefinedSelections() throws Exception {
        withCanonicalPrelude(canonical -> {
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, standardPlus, false);
            ProtosActivation module = alternative.prelude().newModuleActivation();
            int[] operands = new int[1];
            int[] firstCalls = new int[1];
            int[] secondCalls = new int[1];
            ProtosClosureValue next = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        operands[0]++;
                        return Long.valueOf(operands[0] % 2 == 1 ? 1L : 2L);
                    });
            ProtosClosureValue first = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        firstCalls[0]++;
                        return Long.valueOf(11L);
                    });
            ProtosClosureValue second = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        secondCalls[0]++;
                        return Long.valueOf(22L);
                    });
            ProtosClosureValue site = closure("(next) => next() + next()", module);
            for (int round = 0; round < 4; round++) {
                operands[0] = 0;
                firstCalls[0] = 0;
                secondCalls[0] = 0;

                alternative.integer().assignLocalSlot("+", standardPlus);
                assertEquals(3L, normalized(call(site, module, next)), "standard");
                assertEquals(2, operands[0], "standard operands");

                alternative.integer().assignLocalSlot("+", first);
                assertSame(first, alternative.integer().readLocalSlot("+").orElseThrow());
                assertEquals(11L, normalized(call(site, module, next)), "first redefinition");
                assertEquals(4, operands[0], "first redefinition operands");
                assertEquals(1, firstCalls[0], "the first redefinition runs once");

                alternative.integer().assignLocalSlot("+", standardPlus);
                assertEquals(3L, normalized(call(site, module, next)), "restored");
                assertEquals(6, operands[0], "restored operands");
                assertEquals(1, firstCalls[0], "no redefinition after restore");

                alternative.integer().assignLocalSlot("+", second);
                assertSame(second, alternative.integer().readLocalSlot("+").orElseThrow());
                assertEquals(22L, normalized(call(site, module, next)), "second redefinition");
                assertEquals(8, operands[0], "second redefinition operands");
                assertEquals(1, firstCalls[0], "the first redefinition is not selected again");
                assertEquals(1, secondCalls[0], "the second redefinition runs once");
            }
            alternative.integer().assignLocalSlot("+", standardPlus);
            assertEquals(3L, normalized(call(site, module, next)));
        });
        System.out.println("PERF040_E1_SAME_SITE_ALTERNATION=PASS");
    }

    /**
     * Two Closures of one definition differ only in identity and captured
     * Prelude: one captures the canonical Prelude, the other a Prelude whose
     * SmallInteger {@code +} is redefined, restored and redefined again.
     * Alternating them at the same site with the same selector and operands
     * keeps each selection under its own Closure, and every execution
     * evaluates both operands exactly once.
     */
    @Test
    void capturedClosureIdentitySeparatesPreludes() throws Exception {
        withCanonicalPrelude(canonical -> {
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, standardPlus, false);
            ProtosActivation canonicalModule = canonical.newModuleActivation();
            ProtosActivation alternativeModule = alternative.prelude().newModuleActivation();
            ProtosClosureValue template =
                    closure("(next) => next() + next()", canonicalModule);
            ProtosClosureValue canonicalSite = capturing(template, canonical);
            ProtosClosureValue alternativeSite = capturing(template, alternative.prelude());
            assertNotSame(canonicalSite, alternativeSite);
            assertSame(canonicalSite.definition(), alternativeSite.definition());
            assertSame(canonical, canonicalSite.preludeOrNullForRuntime());
            assertSame(alternative.prelude(), alternativeSite.preludeOrNullForRuntime());
            int[] operands = new int[1];
            int[] firstCalls = new int[1];
            int[] secondCalls = new int[1];
            ProtosClosureValue next = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        operands[0]++;
                        return Long.valueOf(operands[0] % 2 == 1 ? 1L : 2L);
                    });
            ProtosClosureValue first = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        firstCalls[0]++;
                        return Long.valueOf(11L);
                    });
            ProtosClosureValue second = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        secondCalls[0]++;
                        return Long.valueOf(22L);
                    });
            for (int round = 0; round < 6; round++) {
                String label = " in round " + round;
                operands[0] = 0;
                firstCalls[0] = 0;
                secondCalls[0] = 0;

                alternative.integer().assignLocalSlot("+", first);
                assertEquals(3L, normalized(call(canonicalSite, canonicalModule, next)),
                        "canonical Closure" + label);
                assertEquals(2, operands[0], "canonical operands" + label);
                assertEquals(11L, normalized(call(alternativeSite, alternativeModule, next)),
                        "redefined Closure" + label);
                assertEquals(4, operands[0], "redefined operands" + label);
                assertEquals(1, firstCalls[0], "the redefinition runs once" + label);
                assertEquals(3L, normalized(call(canonicalSite, canonicalModule, next)),
                        "canonical Closure again" + label);
                assertEquals(6, operands[0], "canonical operands again" + label);
                assertEquals(1, firstCalls[0], "the canonical Closure runs no redefinition" + label);

                alternative.integer().assignLocalSlot("+", standardPlus);
                assertEquals(3L, normalized(call(alternativeSite, alternativeModule, next)),
                        "restored Closure" + label);
                assertEquals(8, operands[0], "restored operands" + label);
                assertEquals(3L, normalized(call(canonicalSite, canonicalModule, next)),
                        "canonical Closure after restore" + label);
                assertEquals(10, operands[0], "canonical operands after restore" + label);

                alternative.integer().assignLocalSlot("+", second);
                assertEquals(22L, normalized(call(alternativeSite, alternativeModule, next)),
                        "second redefinition" + label);
                assertEquals(12, operands[0], "second redefinition operands" + label);
                assertEquals(1, secondCalls[0], "the second redefinition runs once" + label);
                assertEquals(3L, normalized(call(canonicalSite, canonicalModule, next)),
                        "canonical Closure after the second redefinition" + label);
                assertEquals(14, operands[0], "final canonical operands" + label);
                assertEquals(1, firstCalls[0], "the first redefinition is not selected again" + label);
                assertEquals(1, secondCalls[0], "the canonical Closure runs no redefinition" + label);
            }
            alternative.integer().assignLocalSlot("+", standardPlus);
            assertEquals(3L, normalized(call(alternativeSite, alternativeModule, next)));
        });
        System.out.println("PERF040_E1_CAPTURED_CLOSURE_IDENTITY=PASS");
    }

    /**
     * The selection follows the invocation's Prelude across the change from
     * compact invocation to published activation: a Closure owning its
     * Prelude keeps it, and a Closure owning none keeps inheriting the
     * caller's, both before and after a nested capturing Closure publishes
     * the activation that runs the operation.
     */
    @Test
    void capturedClosureIdentitySurvivesPublication() throws Exception {
        withCanonicalPrelude(canonical -> {
            AlternativeIntegers alternative = alternativeIntegers(
                    canonical, answering(ALTERNATIVE_PLUS_RESULT), true);
            ProtosActivation canonicalModule = canonical.newModuleActivation();
            ProtosActivation alternativeModule = alternative.prelude().newModuleActivation();
            ProtosClosureValue compact = closure("(x, y) => x + y", alternativeModule);
            ProtosClosureValue published = closure(
                    "(x, y) => {\n"
                            + "    read: () => y\n"
                            + "    r: x + read()\n"
                            + "    r\n"
                            + "}", alternativeModule);
            ProtosClosureValue inheritedCompact = withoutPrelude(compact);
            ProtosClosureValue inheritedPublished = withoutPrelude(published);
            assertSame(alternative.prelude(), compact.preludeOrNullForRuntime());
            assertSame(alternative.prelude(), published.preludeOrNullForRuntime());
            assertNull(inheritedCompact.preludeOrNullForRuntime());
            assertNull(inheritedPublished.preludeOrNullForRuntime());
            for (int round = 0; round < 12; round++) {
                String label = " in round " + round;
                for (ProtosClosureValue owning : List.of(compact, published)) {
                    assertEquals(ALTERNATIVE_PLUS_RESULT,
                            normalized(call(owning, canonicalModule, 1L, 2L)),
                            "own Prelude under a canonical caller" + label);
                    assertEquals(ALTERNATIVE_PLUS_RESULT,
                            normalized(call(owning, alternativeModule, 1L, 2L)),
                            "own Prelude under its own module" + label);
                    assertEquals(2.5d, call(owning, canonicalModule, 1.5d, 1.0d),
                            "own Prelude keeps the standard Float +" + label);
                }
                for (ProtosClosureValue inherited : List.of(inheritedCompact, inheritedPublished)) {
                    assertEquals(3L, normalized(call(inherited, canonicalModule, 1L, 2L)),
                            "inherited canonical caller" + label);
                    assertEquals(ALTERNATIVE_PLUS_RESULT,
                            normalized(call(inherited, alternativeModule, 1L, 2L)),
                            "inherited alternative caller" + label);
                }
            }
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    call(inheritedPublished, canonicalModule, Long.MAX_VALUE, 1L)));
            assertEquals(ALTERNATIVE_PLUS_RESULT,
                    normalized(call(published, canonicalModule, Long.MAX_VALUE, 1L)));
        });
        System.out.println("PERF040_E1_CAPTURED_CLOSURE_PUBLICATION=PASS");
    }

    /**
     * Rewriting the selected slot of a mutable prototype keeps every Shape:
     * the structural selection stays valid while its final-value assumption
     * is invalidated, so the site observes the new behavior and recovers
     * the standard operation after the slot is restored.
     */
    @Test
    void shapePreservingRewriteIsObservedAndRestored() throws Exception {
        withCanonicalPrelude(canonical -> {
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, standardPlus, false);
            ProtosActivation module = alternative.prelude().newModuleActivation();
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            ProtosValueLookup.StructuralSelection selection =
                    ProtosValueLookup.selectStructural(alternative.smallInteger(), "+");
            assertSame(alternative.integer(), selection.home());
            assertFalse(selection.allOwnersFrozen(), "the chain is mutable");
            for (int warm = 0; warm < 10; warm++) {
                assertEquals(3L, normalized(call(add, module, 1L, 2L)));
            }

            alternative.integer().assignLocalSlot("+", answering(5L));
            assertTrue(selection.isValid(), "the rewrite keeps every Shape");
            assertFalse(selection.finalAssumption().isValid(),
                    "the rewrite invalidates the final-value assumption");
            assertEquals(5L, normalized(call(add, module, 1L, 2L)),
                    "Shape identity alone does not keep the old selection");

            alternative.integer().assignLocalSlot("+", standardPlus);
            assertTrue(selection.isValid(), "the restoration keeps every Shape");
            for (int round = 0; round < 10; round++) {
                assertEquals(3L, normalized(call(add, module, 1L, 2L)), "restored");
            }
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    call(add, module, Long.MAX_VALUE, 1L)), "the restored + promotes exactly");
        });
        System.out.println("PERF040_E1_SHAPE_PRESERVING_REWRITE=PASS");
    }

    /**
     * A chain FROZEN from installation is proven FROZEN; a chain observed
     * while mutable is never reused as FROZEN, its rewrites are observed,
     * and only a selection taken after freezing is FROZEN.
     */
    @Test
    void freezingTransitionNeedsANewSelection() throws Exception {
        withCanonicalPrelude(canonical -> {
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            AlternativeIntegers frozen = alternativeIntegers(canonical, standardPlus, true);
            ProtosActivation frozenModule = frozen.prelude().newModuleActivation();
            assertTrue(ProtosValueLookup.selectStructural(frozen.smallInteger(), "+")
                    .allOwnersFrozen(), "FROZEN from installation");
            ProtosClosureValue frozenAdd = closure("(x, y) => x + y", frozenModule);
            for (int round = 0; round < 10; round++) {
                assertEquals(3L, normalized(call(frozenAdd, frozenModule, 1L, 2L)));
            }

            AlternativeIntegers mutable = alternativeIntegers(canonical, standardPlus, false);
            ProtosActivation module = mutable.prelude().newModuleActivation();
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            ProtosValueLookup.StructuralSelection before =
                    ProtosValueLookup.selectStructural(mutable.smallInteger(), "+");
            assertFalse(before.allOwnersFrozen(), "observed while mutable");
            for (int round = 0; round < 10; round++) {
                assertEquals(3L, normalized(call(add, module, 1L, 2L)));
            }
            mutable.integer().assignLocalSlot("+", answering(5L));
            assertEquals(5L, normalized(call(add, module, 1L, 2L)),
                    "a mutable classification is not reused as FROZEN");
            mutable.integer().assignLocalSlot("+", standardPlus);
            assertEquals(3L, normalized(call(add, module, 1L, 2L)));

            mutable.integer().freeze();
            mutable.smallInteger().freeze();
            mutable.bigInteger().freeze();
            assertFalse(before.allOwnersFrozen(),
                    "freezing grants no retroactive FROZEN status");
            ProtosValueLookup.StructuralSelection after =
                    ProtosValueLookup.selectStructural(mutable.smallInteger(), "+");
            assertTrue(after.allOwnersFrozen(), "a selection taken after freezing is FROZEN");
            for (int round = 0; round < 10; round++) {
                assertEquals(3L, normalized(call(add, module, 1L, 2L)), "after freezing");
            }
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    call(add, module, Long.MAX_VALUE, 1L)));
        });
        System.out.println("PERF040_E1_FREEZING_TRANSITION=PASS");
    }

    /**
     * Signed-64 overflow promotes to the existing large Integer
     * representation, mixed {@code long}/{@code double} operands answer
     * Floats, and each site continues with exact SmallInteger results after
     * its specialization generalized.
     */
    @Test
    void overflowAndMixedOperandsKeepTheirRepresentation() throws Exception {
        withCore(module -> {
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            ProtosClosureValue subtract = closure("(x, y) => x - y", module);
            ProtosClosureValue multiply = closure("(x, y) => x * y", module);
            for (int round = 0; round < 3; round++) {
                assertEquals(3L, normalized(call(add, module, 1L, 2L)));
                assertTrue(ProtosNumericValueSupport.isLargeInteger(
                        call(add, module, Long.MAX_VALUE, 1L)), "MAX + 1");
                assertEquals(7L, normalized(call(add, module, 3L, 4L)));
                assertInstanceOf(Long.class, call(add, module, 3L, 4L));

                assertEquals(-1L, normalized(call(subtract, module, 2L, 3L)));
                assertTrue(ProtosNumericValueSupport.isLargeInteger(
                        call(subtract, module, Long.MIN_VALUE, 1L)), "MIN - 1");
                assertEquals(1L, normalized(call(subtract, module, 3L, 2L)));
                assertInstanceOf(Long.class, call(subtract, module, 3L, 2L));

                assertEquals(6L, normalized(call(multiply, module, 2L, 3L)));
                assertTrue(ProtosNumericValueSupport.isLargeInteger(
                        call(multiply, module, Long.MAX_VALUE, 2L)), "MAX * 2");
                assertTrue(ProtosNumericValueSupport.isLargeInteger(
                        call(multiply, module, Long.MIN_VALUE, -1L)), "MIN * -1");
                assertEquals(20L, normalized(call(multiply, module, 4L, 5L)));
                assertInstanceOf(Long.class, call(multiply, module, 4L, 5L));

                assertEquals(2.5d, call(add, module, 1L, 1.5d));
                assertEquals(2.5d, call(add, module, 1.5d, 1L));
                assertEquals(-0.5d, call(subtract, module, 1L, 1.5d));
                assertEquals(3.0d, call(multiply, module, 2L, 1.5d));
                assertEquals(3.0d, call(multiply, module, 1.5d, 2L));
            }
        });
        System.out.println("PERF040_E1_OVERFLOW_AND_MIXED=PASS");
    }

    /**
     * A dynamically selected {@code +} that suspends inside a Task resumes
     * once, with its operands evaluated once and its method run once, and
     * answers the final guest result; a site of a different Prelude in the
     * same Process keeps its standard selection meanwhile. Each embedding
     * stays live: no guest Error escapes.
     */
    @Test
    void suspendingRedefinedSelectionResumesUnderItsOwnPrelude() throws Exception {
        withCanonicalPrelude(canonical -> {
            int[] operands = new int[1];
            int[] selected = new int[1];
            ProtosClosureValue next = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        operands[0]++;
                        return Long.valueOf(operands[0] % 2 == 1 ? 1L : 2L);
                    });
            ProtosClosureValue mark = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        selected[0]++;
                        return Long.valueOf(selected[0]);
                    });
            ProtosEmbeddedProcess embedding =
                    ProtosLanguageContext.current().embeddedProcessForHostEntry();
            ProtosActorExecutionDomain rootDomain = embedding.rootExecutionDomain();
            ProtosActivation canonicalModule = canonical.newModuleActivation(
                    new ProtosActorModuleState(), null,
                    canonical.newExecutionContext(), rootDomain);
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            Object suspendingPlus = call(
                    closure("(mark) => (other) => {\n    mark()\n"
                            + "    (() => { 42 }).future().value()\n}", canonicalModule),
                    canonicalModule,
                    mark);
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, standardPlus, false);
            ProtosActivation module = alternative.prelude().newModuleActivation(
                    new ProtosActorModuleState(), null,
                    alternative.prelude().newExecutionContext(), rootDomain);
            String source = "(next) => {\n"
                    + "    (() => {\n"
                    + "        sum: next() + next()\n"
                    + "        sum * 1\n"
                    + "    }).future().value()\n"
                    + "}";
            ProtosHostExecutableClosure alternativeEntry =
                    ProtosHostExecutableClosure.prepareForEmbeddingRuntime(
                            closure(source, module), module, embedding);
            ProtosHostExecutableClosure canonicalEntry =
                    ProtosHostExecutableClosure.prepareForEmbeddingRuntime(
                            closure(source, canonicalModule), canonicalModule, embedding);
            java.util.function.Function<ProtosHostExecutableClosure, Object> run = entry -> {
                try {
                    return InteropLibrary.getUncached().execute(entry, next);
                } catch (com.oracle.truffle.api.interop.InteropException e) {
                    throw new AssertionError(e);
                }
            };
            java.util.concurrent.atomic.AtomicInteger hostSuspensions =
                    new java.util.concurrent.atomic.AtomicInteger();
            java.util.function.Consumer<com.guillermomolina.protos.runtime.ProtosFutureValue>
                    previous = ProtosHostEntrySuspension.suspendedForTesting;
            ProtosHostEntrySuspension.suspendedForTesting =
                    future -> hostSuspensions.incrementAndGet();
            try {
                for (int round = 0; round < 3; round++) {
                    operands[0] = 0;
                    selected[0] = 0;
                    assertEquals(3L, normalized(run.apply(alternativeEntry)), "standard");
                    assertEquals(2, operands[0], "standard operands");

                    alternative.integer().assignLocalSlot("+", suspendingPlus);
                    assertEquals(42L, normalized(run.apply(alternativeEntry)), "suspending +");
                    assertEquals(4, operands[0], "suspending + operands");
                    assertEquals(1, selected[0], "the suspending + runs once");

                    assertEquals(3L, normalized(run.apply(canonicalEntry)),
                            "the canonical Prelude keeps its standard +");
                    assertEquals(6, operands[0], "canonical operands");
                    assertEquals(1, selected[0], "the canonical site selects no redefinition");

                    alternative.integer().assignLocalSlot("+", standardPlus);
                    assertEquals(3L, normalized(run.apply(alternativeEntry)), "restored");
                    assertEquals(8, operands[0], "restored operands");
                    assertEquals(1, selected[0], "no selected method after restore");
                }
                assertTrue(hostSuspensions.get() >= 1,
                        "the host entry really suspended at least once");
                assertTrue(embedding.isLive(), "the Process stays live");
            } finally {
                ProtosHostEntrySuspension.suspendedForTesting = previous;
            }
        });
        System.out.println("PERF040_E1_SUSPENDING_REDEFINED_SELECTION=PASS");
    }

    /* The same compiled Closure, owning no Prelude: it inherits its caller's. */
    private static ProtosClosureValue withoutPrelude(ProtosClosureValue closure) {
        return ProtosClosureValue.experimentalNonCapturing(
                closure.definition(),
                closure.executionPlan().orElseThrow(),
                closure.definingModuleKeyOrNullForRuntime(),
                null);
    }

    /* The same compiled Closure as {@code closure}, owning {@code prelude}. */
    private static ProtosClosureValue capturing(
            ProtosClosureValue closure, ProtosPrelude prelude) {
        return ProtosClosureValue.experimentalNonCapturing(
                closure.definition(),
                closure.executionPlan().orElseThrow(),
                closure.definingModuleKeyOrNullForRuntime(),
                prelude);
    }

    /*
     * Retired with no surviving behavioral contract (reported):
     * certificationIsDeferredToFirstUseAndMemoized,
     * negativeCertificationIsStableAndPerPrelude,
     * concurrentFirstUsePublishesOneCertificate and
     * syntheticPreludeIsNotCertified tested the lifecycle, memoization and
     * publication of the ProtosNumericCoreAdmission object itself.
     */

    private static final long ALTERNATIVE_PLUS_RESULT = 99L;

    private static ProtosClosureValue answering(long value) {
        return ProtosClosureValue.nativeClosure((activation, supplied) -> Long.valueOf(value));
    }

    private static ProtosClosureValue answeringFalse() {
        return ProtosClosureValue.nativeClosure((activation, supplied) -> Boolean.FALSE);
    }

    /**
     * Runs {@code test} in a module of a noncanonical Prelude: every
     * canonical binding, except an Integer prototype (with fresh
     * SmallInteger and BigInteger below it) whose {@code +} is a different
     * selected method; its other selectors are the standard ones.
     */
    private static void withAlternativeIntegerPlus(CoreTest test) throws Exception {
        try (Context context = context(false)) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude canonical = new ProtosCoreBootstrap().bootstrap(CORE);
                AlternativeIntegers alternative = alternativeIntegers(
                        canonical, answering(ALTERNATIVE_PLUS_RESULT), true);
                assertNotSame(canonical.integerPrototype(), alternative.prelude().integerPrototype());
                test.run(alternative.prelude().newModuleActivation());
            } finally {
                context.leave();
            }
        }
    }

    /* A noncanonical Prelude and its replaced Integer prototypes. */
    private record AlternativeIntegers(
            ProtosPrelude prelude,
            ProtosObjectValue integer,
            ProtosObjectValue smallInteger,
            ProtosObjectValue bigInteger) {}

    /**
     * Every canonical binding of {@code canonical}, except an Integer
     * prototype (with fresh SmallInteger and BigInteger below it) whose
     * {@code +} is {@code plus}; its other selectors are the standard ones.
     * With {@code freeze} the three prototypes are FROZEN like the canonical
     * ones; otherwise they stay mutable.
     */
    private static AlternativeIntegers alternativeIntegers(
            ProtosPrelude canonical, Object plus, boolean freeze) {
        ProtosObjectValue integer = new ProtosObjectValue(canonical.numberPrototype());
        for (Map.Entry<String, Object> slot :
                canonical.integerPrototype().localSlotsSnapshot().entrySet()) {
            integer.createLocalSlot(
                    slot.getKey(),
                    slot.getKey().equals("+") ? plus : slot.getValue());
        }
        ProtosObjectValue smallInteger = new ProtosObjectValue(integer);
        ProtosObjectValue bigInteger = new ProtosObjectValue(integer);
        if (freeze) {
            integer.freeze();
            smallInteger.freeze();
            bigInteger.freeze();
        }

        ProtosObjectValue bindings = new ProtosObjectValue(canonical.contextPrototype());
        Map<String, ProtosObjectValue> replaced = Map.of(
                "Integer", integer,
                "SmallInteger", smallInteger,
                "BigInteger", bigInteger);
        for (Map.Entry<String, Object> binding :
                canonical.bindings().localSlotsSnapshot().entrySet()) {
            bindings.createLocalSlot(
                    binding.getKey(),
                    replaced.containsKey(binding.getKey())
                            ? replaced.get(binding.getKey())
                            : binding.getValue());
        }
        bindings.freeze();
        return new AlternativeIntegers(
                new ProtosPrelude(bindings, canonical.contextPrototype()),
                integer,
                smallInteger,
                bigInteger);
    }

    /* Runs {@code test} with a canonical Prelude in an entered Context. */
    private interface PreludeTest {
        void run(ProtosPrelude canonical) throws Exception;
    }

    private static void withCanonicalPrelude(PreludeTest test) throws Exception {
        try (Context context = context(false)) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                test.run(new ProtosCoreBootstrap().bootstrap(CORE));
            } finally {
                context.leave();
            }
        }
    }

    /**
     * Repeated standard {@code +} over two signed-64 Integers at one site
     * stays exact well past warm-up. The canonical selection of {@code +}
     * for a SmallInteger receiver examines only FROZEN prototypes, which is
     * what lets the site keep its classification without reading the
     * selected method again.
     */
    @Test
    void repeatedStandardLongPlusStaysExactAfterWarmup() throws Exception {
        withCore(module -> {
            ProtosPrelude prelude = module.preludeOrNullForRuntime();
            ProtosObjectValue prototype = (ProtosObjectValue)
                    ProtosValueLookup.delegationParent(1L, prelude).orElseThrow();
            ProtosValueLookup.StructuralSelection selection =
                    ProtosValueLookup.selectStructural(prototype, "+");
            assertTrue(selection != null && selection.isValid());
            assertTrue(selection.allOwnersFrozen(), "canonical + selection is FROZEN");

            ProtosClosureValue add = closure("(x, y) => x + y", module);
            for (long i = 0; i < 2000; i++) {
                assertEquals(2 * i + 1, normalized(call(add, module, i, i + 1)));
            }
            assertEquals(Long.MAX_VALUE, normalized(call(add, module, Long.MAX_VALUE - 7, 7L)));
            assertEquals(-3L, normalized(call(add, module, -1L, -2L)));
        });
        System.out.println("PERF040_E1_REPEATED_STANDARD_PLUS=PASS");
    }

    /**
     * The FROZEN classification of a structural selection rests on the
     * Shapes it captured: the canonical {@code +} selection from SmallInteger
     * is FROZEN and owned by the real Integer prototype; a mutable child of
     * SmallInteger inherits that selection yet is not FROZEN until it is
     * frozen and the selection is captured again, after which its slots can
     * no longer change.
     */
    @Test
    void frozenSelectionUsesCapturedShapes() throws Exception {
        withCanonicalPrelude(canonical -> {
            ProtosObjectValue smallInteger = canonical.smallIntegerPrototype();
            ProtosValueLookup.StructuralSelection canonicalSelection =
                    ProtosValueLookup.selectStructural(smallInteger, "+");
            assertTrue(canonicalSelection != null, "the canonical + is selected");
            assertTrue(canonicalSelection.isValid());
            assertTrue(canonicalSelection.allOwnersFrozen(), "the canonical chain is FROZEN");
            assertSame(canonical.integerPrototype(), canonicalSelection.home(),
                    "the real owner of the standard + is Integer");

            ProtosObjectValue child = new ProtosObjectValue(smallInteger);
            child.createLocalSlot("tag", 1L);
            ProtosValueLookup.StructuralSelection mutableSelection =
                    ProtosValueLookup.selectStructural(child, "+");
            assertTrue(mutableSelection != null && mutableSelection.isValid());
            assertSame(canonical.integerPrototype(), mutableSelection.home());
            assertFalse(mutableSelection.allOwnersFrozen(),
                    "a mutable origin is not FROZEN even with FROZEN ancestors");

            child.freeze();
            assertFalse(mutableSelection.allOwnersFrozen(),
                    "the Shape captured before freezing is not FROZEN");
            ProtosValueLookup.StructuralSelection frozenSelection =
                    ProtosValueLookup.selectStructural(child, "+");
            assertTrue(frozenSelection != null && frozenSelection.isValid());
            assertTrue(frozenSelection.allOwnersFrozen(), "the recaptured chain is FROZEN");
            assertSame(canonical.integerPrototype(), frozenSelection.home());

            assertThrows(IllegalStateException.class, () -> child.assignLocalSlot("tag", 2L));
            assertThrows(IllegalStateException.class, () -> child.createLocalSlot("+", 3L));
            assertEquals(1L, child.readLocalSlot("tag").orElseThrow());
            assertTrue(frozenSelection.isValid(), "a FROZEN chain keeps its selection");

            /*
             * The four selections side by side: the original FROZEN one, the
             * one inherited from the still-mutable object, the one recaptured
             * after freezing it, and the one captured before freezing it.
             * Freezing grants no retroactive FROZEN status to an older selection.
             */
            ProtosObjectValue other = new ProtosObjectValue(smallInteger);
            other.createLocalSlot("tag", 2L);
            ProtosValueLookup.StructuralSelection capturedBeforeFreezing =
                    ProtosValueLookup.selectStructural(other, "+");
            ProtosValueLookup.StructuralSelection inheritedFromMutable =
                    ProtosValueLookup.selectStructural(other, "+");
            assertFalse(inheritedFromMutable.allOwnersFrozen(), "inherited from a mutable object");
            other.freeze();
            ProtosValueLookup.StructuralSelection afterFreezing =
                    ProtosValueLookup.selectStructural(other, "+");
            assertTrue(canonicalSelection.allOwnersFrozen(), "the original FROZEN selection");
            assertFalse(inheritedFromMutable.allOwnersFrozen(),
                    "the inherited selection does not become FROZEN");
            assertTrue(afterFreezing != null && afterFreezing.isValid()
                    && afterFreezing.allOwnersFrozen(), "the selection after freezing");
            assertFalse(capturedBeforeFreezing.allOwnersFrozen(),
                    "a selection captured before freezing is never FROZEN retroactively");
            for (ProtosValueLookup.StructuralSelection selection : List.of(
                    canonicalSelection, inheritedFromMutable, afterFreezing, capturedBeforeFreezing)) {
                assertSame(canonical.integerPrototype(), selection.home(),
                        "every selection keeps the real Integer owner");
            }
            assertTrue(canonical.integerPrototype().isFrozen(), "no canonical prototype changes");
        });
        System.out.println("PERF040_E1_FROZEN_CAPTURED_SHAPES=PASS");
    }

    /**
     * A mutable Integer prototype keeps every selection observed: assigning
     * a new value to {@code +} keeps the structure valid, yet each call at
     * the same site runs the currently selected behavior, including a return
     * to the standard method and away from it again.
     */
    @Test
    void mutablePrototypeSelectionValueChangesAreObserved() throws Exception {
        withCanonicalPrelude(canonical -> {
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, answering(41L), false);
            ProtosActivation module = alternative.prelude().newModuleActivation();
            ProtosClosureValue add = closure("(x, y) => x + y", module);

            assertFalse(ProtosValueLookup.selectStructural(alternative.smallInteger(), "+")
                    .allOwnersFrozen());
            assertEquals(41L, normalized(call(add, module, 1L, 2L)));
            assertEquals(41L, normalized(call(add, module, 1L, 2L)));

            alternative.integer().assignLocalSlot("+", answering(42L));
            assertEquals(42L, normalized(call(add, module, 1L, 2L)),
                    "the reassigned behavior is selected");

            alternative.integer().assignLocalSlot("+", standardPlus);
            for (long i = 0; i < 200; i++) {
                assertEquals(i + 2, normalized(call(add, module, i, 2L)));
            }

            alternative.integer().assignLocalSlot("+", answering(43L));
            assertEquals(43L, normalized(call(add, module, 1L, 2L)),
                    "leaving the standard behavior is observed");
        });
        System.out.println("PERF040_E1_MUTABLE_SELECTION_VALUE=PASS");
    }

    /**
     * A structural change of a mutable prototype replaces the selection: a
     * nearer {@code +} created on SmallInteger shadows the Integer method at
     * the same site, and removing it restores the former selection.
     */
    @Test
    void structuralChangeOfMutablePrototypeReplacesTheSelection() throws Exception {
        withCanonicalPrelude(canonical -> {
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, standardPlus, false);
            ProtosActivation module = alternative.prelude().newModuleActivation();
            ProtosClosureValue add = closure("(x, y) => x + y", module);

            for (long i = 0; i < 200; i++) {
                assertEquals(i + 3, normalized(call(add, module, i, 3L)));
            }

            alternative.smallInteger().createLocalSlot("+", answering(7L));
            assertEquals(7L, normalized(call(add, module, 1L, 2L)),
                    "the nearer selection is observed");

            alternative.smallInteger().removeLocalSlot("+");
            assertEquals(3L, normalized(call(add, module, 1L, 2L)),
                    "the former selection is restored");
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    call(add, module, Long.MAX_VALUE, 1L)));
        });
        System.out.println("PERF040_E1_STRUCTURAL_REPLACEMENT=PASS");
    }

    /**
     * The Float path at a warmed site of a Prelude whose {@code Float}
     * binding is a mutable child of the canonical Float prototype, bound
     * before the bindings object is FROZEN: the inherited standard {@code +},
     * a shadowing {@code +}, an in-place reassignment that keeps the Shape,
     * and a removal restoring the inherited selection are each observed.
     */
    @Test
    void floatSelectionObservesPrototypeMutation() throws Exception {
        withCanonicalPrelude(canonical -> {
            ProtosObjectValue floatChild = new ProtosObjectValue(canonical.floatPrototype());
            ProtosObjectValue bindings = new ProtosObjectValue(canonical.contextPrototype());
            for (Map.Entry<String, Object> binding :
                    canonical.bindings().localSlotsSnapshot().entrySet()) {
                bindings.createLocalSlot(
                        binding.getKey(),
                        binding.getKey().equals("Float") ? floatChild : binding.getValue());
            }
            bindings.freeze();
            ProtosActivation module =
                    new ProtosPrelude(bindings, canonical.contextPrototype()).newModuleActivation();
            ProtosClosureValue add = closure("(x, y) => x + y", module);

            for (int i = 0; i < 200; i++) {
                assertEquals(i + 0.5d, call(add, module, (double) i, 0.5d),
                        "the inherited standard + adds");
            }

            floatChild.createLocalSlot("+", answering(5L));
            assertEquals(5L, normalized(call(add, module, 1.5d, 1.0d)),
                    "a shadowing + is selected");
            floatChild.assignLocalSlot("+", answering(6L));
            assertEquals(6L, normalized(call(add, module, 1.5d, 1.0d)),
                    "an in-place reassignment is selected");
            floatChild.removeLocalSlot("+");
            for (int i = 0; i < 200; i++) {
                assertEquals(i + 0.25d, call(add, module, (double) i, 0.25d),
                        "removal restores the inherited selection");
            }

            for (int round = 1; round <= 2; round++) {
                floatChild.createLocalSlot("+", answering(5L + 10L * round));
                assertEquals(5L + 10L * round, normalized(call(add, module, 1.5d, 1.0d)),
                        "a shadowing + is selected again in round " + round);
                floatChild.assignLocalSlot("+", answering(6L + 10L * round));
                assertEquals(6L + 10L * round, normalized(call(add, module, 1.5d, 1.0d)),
                        "an in-place reassignment is selected again in round " + round);
                floatChild.removeLocalSlot("+");
                for (int i = 0; i < 50; i++) {
                    assertEquals(i + 0.75d, call(add, module, (double) i, 0.75d),
                            "removal restores the inherited + again in round " + round);
                }
            }
            assertEquals(4L, normalized(call(add, module, 1L, 3L)), "Integer + is untouched");
        });
        System.out.println("PERF040_E1_FLOAT_PROTOTYPE_MUTATION=PASS");
    }

    /**
     * Two Preludes in one Context, each with its own {@code +}, interleaved
     * on sites with the same source: neither cached selection leaks into
     * the other Prelude.
     */
    @Test
    void distinctPreludesNeverShareASelection() throws Exception {
        withCanonicalPrelude(canonical -> {
            ProtosActivation standardModule = canonical.newModuleActivation();
            AlternativeIntegers alternative = alternativeIntegers(
                    canonical, answering(ALTERNATIVE_PLUS_RESULT), true);
            ProtosActivation alternativeModule = alternative.prelude().newModuleActivation();
            ProtosClosureValue standardAdd = closure("(x, y) => x + y", standardModule);
            ProtosClosureValue alternativeAdd = closure("(x, y) => x + y", alternativeModule);

            for (long i = 0; i < 100; i++) {
                assertEquals(i + 1, normalized(call(standardAdd, standardModule, i, 1L)));
                assertEquals(ALTERNATIVE_PLUS_RESULT,
                        normalized(call(alternativeAdd, alternativeModule, i, 1L)));
            }
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    call(standardAdd, standardModule, Long.MAX_VALUE, 1L)));
            assertEquals(ALTERNATIVE_PLUS_RESULT,
                    normalized(call(alternativeAdd, alternativeModule, Long.MAX_VALUE, 1L)));
            assertEquals(5L, normalized(call(standardAdd, standardModule, 2L, 3L)));

            for (int round = 0; round < 3; round++) {
                for (long i = 0; i < 50; i++) {
                    assertEquals(i + 2, normalized(call(standardAdd, standardModule, i, 2L)),
                            "canonical standard + in round " + round);
                }
                assertEquals(ALTERNATIVE_PLUS_RESULT,
                        normalized(call(alternativeAdd, alternativeModule, 4L, 5L)),
                        "alternative redefined + in round " + round);
                assertEquals(9L, normalized(call(standardAdd, standardModule, 4L, 5L)),
                        "return to the canonical + in round " + round);
                Object promoted = call(standardAdd, standardModule, Long.MAX_VALUE, 1L);
                assertTrue(ProtosNumericValueSupport.isLargeInteger(promoted),
                        "canonical overflow promotes in round " + round);
                assertEquals(ALTERNATIVE_PLUS_RESULT,
                        normalized(call(alternativeAdd, alternativeModule, Long.MAX_VALUE, 1L)),
                        "alternative overflow runs the redefined + in round " + round);
                assertEquals(-1L, normalized(call(standardAdd, standardModule, 2L, -3L)),
                        "canonical + after the alternative overflow in round " + round);
            }

            /*
             * Each Closure captures its own module's Prelude: a second Closure
             * instance of the same Prelude shares its selection, while a
             * Closure of the other Prelude never reuses it, for every operator.
             */
            ProtosClosureValue secondStandardAdd = closure("(x, y) => x + y", standardModule);
            assertNotSame(standardAdd, secondStandardAdd);
            Map<String, ProtosClosureValue> standardSites = new java.util.LinkedHashMap<>();
            Map<String, ProtosClosureValue> alternativeSites = new java.util.LinkedHashMap<>();
            for (String selector : List.of("-", "*", "/", "<")) {
                standardSites.put(selector, closure("(x, y) => x " + selector + " y", standardModule));
                alternativeSites.put(selector,
                        closure("(x, y) => x " + selector + " y", alternativeModule));
            }
            for (int round = 0; round < 3; round++) {
                String label = " in captured round " + round;
                assertEquals(7L, normalized(call(standardAdd, standardModule, 3L, 4L)),
                        "1. canonical standard +" + label);
                assertEquals(ALTERNATIVE_PLUS_RESULT,
                        normalized(call(alternativeAdd, alternativeModule, 3L, 4L)),
                        "2. alternative redefined +" + label);
                assertEquals(11L, normalized(call(standardAdd, standardModule, 5L, 6L)),
                        "3. return to the canonical +" + label);
                assertEquals(13L, normalized(call(secondStandardAdd, standardModule, 6L, 7L)),
                        "4. canonical + from another Closure instance" + label);
                assertTrue(ProtosNumericValueSupport.isLargeInteger(
                        call(secondStandardAdd, standardModule, Long.MAX_VALUE, 1L)),
                        "4. canonical overflow from another Closure instance" + label);
                assertEquals(ALTERNATIVE_PLUS_RESULT,
                        normalized(call(alternativeAdd, alternativeModule, 1L, 1L)),
                        "5. alternative redefined + again" + label);
                assertEquals(3L, normalized(call(standardAdd, standardModule, 1L, 2L)),
                        "6. canonical + again" + label);

                for (ProtosActivation module : List.of(standardModule, alternativeModule)) {
                    Map<String, ProtosClosureValue> sites =
                            module == standardModule ? standardSites : alternativeSites;
                    String where = (module == standardModule ? "canonical " : "alternative ") + label;
                    assertEquals(-1L, normalized(call(sites.get("-"), module, 2L, 3L)), "-" + where);
                    assertEquals(6L, normalized(call(sites.get("*"), module, 2L, 3L)), "*" + where);
                    assertEquals(2L, normalized(call(sites.get("/"), module, 6L, 3L)), "/" + where);
                    assertTrue(ProtosNumericValueSupport.isFraction(
                            call(sites.get("/"), module, 5L, 2L)), "/ Fraction" + where);
                    assertSame(Boolean.TRUE, call(sites.get("<"), module, 1L, 2L), "<" + where);
                    assertSame(Boolean.FALSE, call(sites.get("<"), module, 2L, 1L), "<" + where);
                }
            }
        });
        System.out.println("PERF040_E1_PRELUDE_ISOLATION=PASS");
    }

    /**
     * {@code Long.MAX_VALUE + 1} promotes exactly and the same site keeps
     * adding exactly afterwards, in both the signed-64 and the large domain.
     */
    @Test
    void overflowPromotesExactlyAndTheSiteContinues() throws Exception {
        withCore(module -> {
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            ProtosClosureValue subtract = closure("(x, y) => x - y", module);

            assertEquals(3L, normalized(call(add, module, 1L, 2L)));
            Object big = call(add, module, Long.MAX_VALUE, 1L);
            assertTrue(ProtosNumericValueSupport.isLargeInteger(big));
            assertEquals(Long.MAX_VALUE, normalized(call(subtract, module, big, 1L)),
                    "the promotion is exact");
            assertEquals(Long.MIN_VALUE,
                    normalized(call(subtract, module, call(subtract, module, 0L, big), 0L)));
            Object bigger = call(add, module, big, 1L);
            assertTrue(ProtosNumericValueSupport.isLargeInteger(bigger));
            assertEquals(1L, normalized(call(subtract, module, bigger, big)));
            for (long i = 0; i < 500; i++) {
                assertEquals(i + 5, normalized(call(add, module, i, 5L)));
            }
        });
        System.out.println("PERF040_E1_OVERFLOW_CONTINUITY=PASS");
    }

    /**
     * Alternating Integer, Float and large Integer operands at one site
     * never retires the signed-64 result: every Integer sum is still a
     * signed-64 Integer with its exact value.
     */
    @Test
    void alternatingDomainsKeepLongPlusReachable() throws Exception {
        withCore(module -> {
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            Object big = call(add, module, Long.MAX_VALUE, 1L);
            for (long i = 0; i < 200; i++) {
                Object small = normalized(call(add, module, i, 1L));
                assertInstanceOf(Long.class, small);
                assertEquals(i + 1, small);
                assertEquals(i + 0.5d, call(add, module, i, 0.5d));
                assertEquals(1.5d, call(add, module, 1.0d, 0.5d));
                assertTrue(ProtosNumericValueSupport.isLargeInteger(call(add, module, big, i)));
                Object back = normalized(call(add, module, 2L, i));
                assertInstanceOf(Long.class, back);
                assertEquals(i + 2, back);
            }
        });
        System.out.println("PERF040_E1_ALTERNATING_DOMAINS=PASS");
    }

    /**
     * A nonstandard {@code +} propagates its own Error unchanged through
     * the numeric site, between standard calls in the canonical Prelude,
     * and an Integer plus a non-numeric operand still signals.
     */
    @Test
    void nonstandardSelectionAndErrorsPropagateUnchanged() throws Exception {
        withCanonicalPrelude(canonical -> {
            ProtosObjectValue failure = new ProtosObjectValue(ProtosObjectValue.rootObject());
            AlternativeIntegers alternative = alternativeIntegers(
                    canonical,
                    ProtosClosureValue.nativeClosure((activation, supplied) -> {
                        throw new ProtosSignalException(failure);
                    }),
                    true);
            ProtosActivation alternativeModule = alternative.prelude().newModuleActivation();
            ProtosClosureValue alternativeAdd = closure("(x, y) => x + y", alternativeModule);
            for (int round = 0; round < 3; round++) {
                ProtosSignalException signal = assertThrows(ProtosSignalException.class,
                        () -> call(alternativeAdd, alternativeModule, 1L, 2L));
                assertSame(failure, signal.error());
            }

            ProtosActivation module = canonical.newModuleActivation();
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            assertEquals(3L, normalized(call(add, module, 1L, 2L)));
            assertThrows(ProtosSignalException.class,
                    () -> call(add, module, 1L, new ProtosStringValue("x")));
            assertEquals(3L, normalized(call(add, module, 1L, 2L)));
        });
        System.out.println("PERF040_E1_NONSTANDARD_ERRORS=PASS");
    }

    /**
     * A FROZEN noncanonical Prelude whose Integer {@code +} is not standard:
     * the frozen classification is cached, yet every call (warm, cold and at
     * signed-64 overflow) runs the selected method and never the standard
     * primitive addition.
     */
    @Test
    void frozenNonstandardSelectionNeverRunsThePrimitiveAddition() throws Exception {
        withAlternativeIntegerPlus(module -> {
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            for (long i = 0; i < 500; i++) {
                assertEquals(ALTERNATIVE_PLUS_RESULT, normalized(call(add, module, i, 1L)));
            }
            assertEquals(ALTERNATIVE_PLUS_RESULT,
                    normalized(call(add, module, Long.MAX_VALUE, 1L)),
                    "overflow after the redefinition runs the selected method");
            assertEquals(2.5d, call(add, module, 1.5d, 1.0d), "Float + is untouched");
        });
        System.out.println("PERF040_E1_FROZEN_NONSTANDARD=PASS");
    }

    /**
     * One site alternating between the standard and a redefined {@code +}
     * in a mutable Prelude: each call observes the selection current at
     * that call, and the standard primitive results stay exact.
     */
    @Test
    void oneSiteAlternatesBetweenStandardAndRedefinedSelection() throws Exception {
        withCanonicalPrelude(canonical -> {
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, standardPlus, false);
            ProtosActivation module = alternative.prelude().newModuleActivation();
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            for (long round = 0; round < 50; round++) {
                alternative.integer().assignLocalSlot("+", standardPlus);
                assertEquals(round + 1, normalized(call(add, module, round, 1L)));
                assertTrue(ProtosNumericValueSupport.isLargeInteger(
                        call(add, module, Long.MAX_VALUE, 1L)));
                alternative.integer().assignLocalSlot("+", answering(round));
                assertEquals(round, normalized(call(add, module, 1L, 2L)));
                assertEquals(round, normalized(call(add, module, Long.MAX_VALUE, 1L)),
                        "overflow does not bypass the redefined method");
            }
        });
        System.out.println("PERF040_E1_STANDARD_REDEFINED_ALTERNATION=PASS");
    }

    /**
     * Entering the fallback route does not disable the primitive lanes: on
     * one site per operation, a standard long, a standard double, a
     * redefined selector, the restored standard selector and a long again,
     * repeated so the site is warm, for every arithmetic and ordering
     * selector.
     */
    @Test
    void fallbackRouteKeepsPrimitiveLanesAfterRedefinitionAndRestore() throws Exception {
        withCanonicalPrelude(canonical -> {
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, standardPlus, false);
            ProtosActivation module = alternative.prelude().newModuleActivation();
            ProtosObjectValue integer = alternative.integer();
            Map<String, long[]> arithmetic = Map.of(
                    "+", new long[] {6L, 3L, 9L},
                    "-", new long[] {6L, 3L, 3L},
                    "*", new long[] {6L, 3L, 18L},
                    "/", new long[] {6L, 3L, 2L});
            Map<String, Double> doubles = Map.of("+", 4.5d, "-", 1.5d, "*", 4.5d, "/", 2.0d);
            for (Map.Entry<String, long[]> entry : arithmetic.entrySet()) {
                String selector = entry.getKey();
                long[] lane = entry.getValue();
                ProtosClosureValue site = closure("(x, y) => x " + selector + " y", module);
                Object standard = integer.readLocalSlot(selector).orElse(null);
                for (long round = 0; round < 20; round++) {
                    String where = selector + " round " + round;
                    assertEquals(lane[2], normalized(call(site, module, lane[0], lane[1])), where);
                    assertEquals(doubles.get(selector), call(site, module, 3.0d, 1.5d), where);
                    setOrCreate(integer, selector, answering(round + 1000L));
                    assertEquals(round + 1000L,
                            normalized(call(site, module, lane[0], lane[1])), where);
                    restore(integer, selector, standard);
                    assertEquals(lane[2], normalized(call(site, module, lane[0], lane[1])),
                            where + ": restored standard selection");
                    assertTrue(call(site, module, lane[0], lane[1]) instanceof Long, where);
                }
            }
            for (String selector : List.of("<", "<=", ">", ">=")) {
                ProtosClosureValue site = closure("(x, y) => x " + selector + " y", module);
                Object standard = integer.readLocalSlot(selector).orElse(null);
                boolean less = selector.startsWith("<");
                long left = less ? 1L : 2L;
                long right = less ? 2L : 1L;
                for (int round = 0; round < 20; round++) {
                    String where = selector + " round " + round;
                    assertSame(Boolean.TRUE, call(site, module, left, right), where);
                    assertSame(Boolean.TRUE, call(site, module, (double) left, (double) right),
                            where + " on Float");
                    setOrCreate(integer, selector, answeringFalse());
                    assertSame(Boolean.FALSE, call(site, module, left, right), where);
                    restore(integer, selector, standard);
                    assertSame(Boolean.TRUE, call(site, module, left, right),
                            where + ": restored standard selection");
                    assertSame(Boolean.FALSE, call(site, module, right, left), where);
                }
            }
        });
        System.out.println("PERF040_E1_FALLBACK_KEEPS_PRIMITIVE_LANES=PASS");
    }

    /**
     * One site per selector ({@code +}, {@code -}, {@code *}, {@code <},
     * {@code >}) alternates the standard behavior, a redefined behavior
     * returning an ordinary result, the restored standard behavior and, for
     * an ordering, a redefinition returning a non-Boolean: each call
     * evaluates the receiver and the argument exactly once, observes the
     * selection current at that call and, for an ordering, accepts only a
     * canonical Boolean.
     */
    @Test
    void everySelectorSiteAlternatesStandardRedefinedAndRestoredOperandsOnce()
            throws Exception {
        withCanonicalPrelude(canonical -> {
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, standardPlus, false);
            ProtosActivation module = alternative.prelude().newModuleActivation();
            ProtosObjectValue integer = alternative.integer();
            int[] counts = new int[2];
            ProtosClosureValue left = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        counts[0]++;
                        return Long.valueOf(6L);
                    });
            ProtosClosureValue right = ProtosClosureValue.nativeClosure(
                    (activation, supplied) -> {
                        counts[1]++;
                        return Long.valueOf(3L);
                    });
            Map<String, Object> standardResults = Map.of(
                    "+", 9L, "-", 3L, "*", 18L, "<", Boolean.FALSE, ">", Boolean.TRUE);
            for (String selector : List.of("+", "-", "*", "<", ">")) {
                boolean ordering = selector.equals("<") || selector.equals(">");
                Object expected = standardResults.get(selector);
                Object redefinedResult = ordering
                        ? Boolean.valueOf(!((Boolean) expected))
                        : Long.valueOf(1000L);
                ProtosClosureValue redefined = ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> redefinedResult);
                ProtosClosureValue site =
                        closure("(l, r) => l() " + selector + " r()", module);
                Object standard = integer.readLocalSlot(selector).orElse(null);
                for (int round = 0; round < 10; round++) {
                    String where = selector + " round " + round;
                    counts[0] = 0;
                    counts[1] = 0;
                    assertEquals(normalized(expected),
                            normalized(call(site, module, left, right)), where);
                    setOrCreate(integer, selector, redefined);
                    assertEquals(normalized(redefinedResult),
                            normalized(call(site, module, left, right)), where + " redefined");
                    restore(integer, selector, standard);
                    assertEquals(normalized(expected),
                            normalized(call(site, module, left, right)), where + " restored");
                    int calls = 3;
                    if (ordering) {
                        setOrCreate(integer, selector, answering(1L));
                        assertThrows(ProtosSignalException.class,
                                () -> call(site, module, left, right), where + " non-Boolean");
                        restore(integer, selector, standard);
                        calls = 4;
                    }
                    assertEquals(calls, counts[0], where + ": receiver once per call");
                    assertEquals(calls, counts[1], where + ": argument once per call");
                }
            }
        });
        System.out.println("PERF040_E1_EVERY_SELECTOR_OPERANDS_ONCE=PASS");
    }

    /**
     * Chained operations whose intermediate result is reused stay exact
     * through signed-64 overflow, follow a redefinition of {@code +} at
     * every {@code +} of the chain and return to the standard results once
     * it is restored.
     */
    @Test
    void chainedOperationsReuseIntermediatesAcrossRedefinitionAndOverflow()
            throws Exception {
        withCanonicalPrelude(canonical -> {
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, standardPlus, false);
            ProtosActivation module = alternative.prelude().newModuleActivation();
            ProtosClosureValue site = closure(
                    "(x, y) => {\n    s: x + y\n    (s * s) - (s + y)\n}", module);
            for (int round = 0; round < 10; round++) {
                alternative.integer().assignLocalSlot("+", standardPlus);
                assertEquals(17L, normalized(call(site, module, 2L, 3L)));
                assertTrue(ProtosNumericValueSupport.isLargeInteger(
                        call(site, module, Long.MAX_VALUE, 1L)));
                alternative.integer().assignLocalSlot("+", answering(10L));
                // s = 10; (10 * 10) - (10 + y) with the redefined + answering 10.
                assertEquals(90L, normalized(call(site, module, 2L, 3L)));
                assertEquals(90L, normalized(call(site, module, Long.MAX_VALUE, 1L)));
            }
            alternative.integer().assignLocalSlot("+", standardPlus);
            assertEquals(17L, normalized(call(site, module, 2L, 3L)));
        });
        System.out.println("PERF040_E1_CHAINED_INTERMEDIATES=PASS");
    }

    /**
     * An ordering used directly as an {@code if} condition and as the
     * conditions of nested {@code while} loops, with and without a debugger:
     * the standard comparison decides, a redefinition returning a canonical
     * Boolean decides through its own result, and a redefinition returning
     * any other value signals the standard Error.
     */
    @Test
    void orderingConditionsOfIfAndNestedWhileFollowTheSelection() throws Exception {
        String program = "(n) => {\n"
                + "    hits: 0\n"
                + "    i: 0\n"
                + "    j: 0\n"
                + "    while (i < n) {\n"
                + "        j = 0\n"
                + "        while (j < n) {\n"
                + "            if (j > i) {\n"
                + "                hits = hits + 1\n"
                + "            }\n"
                + "            j = j + 1\n"
                + "        }\n"
                + "        i = i + 1\n"
                + "    }\n"
                + "    hits\n"
                + "}";
        for (boolean debug : new boolean[] {false, true}) {
            try (Context context = context(debug)) {
                context.initialize(ProtosLanguage.ID);
                context.enter();
                try {
                    ProtosPrelude canonical = new ProtosCoreBootstrap().bootstrap(CORE);
                    Object standardPlus =
                            canonical.integerPrototype().readLocalSlot("+").orElseThrow();
                    AlternativeIntegers alternative =
                            alternativeIntegers(canonical, standardPlus, false);
                    ProtosActivation module = alternative.prelude().newModuleActivation();
                    ProtosObjectValue integer = alternative.integer();
                    ProtosClosureValue site = closure(program, module);
                    Object standardGreater = integer.readLocalSlot(">").orElse(null);
                    Object standardLess = integer.readLocalSlot("<").orElse(null);
                    ProtosClosureValue answeringTrue = ProtosClosureValue.nativeClosure(
                            (activation, supplied) -> Boolean.TRUE);
                    for (int round = 0; round < 5; round++) {
                        String where = mode(debug) + " round " + round;
                        assertEquals(6L, normalized(call(site, module, 4L)), where);
                        setOrCreate(integer, ">", answeringTrue);
                        assertEquals(16L, normalized(call(site, module, 4L)),
                                where + ": redefined > answering true");
                        setOrCreate(integer, ">", answering(1L));
                        assertThrows(ProtosSignalException.class,
                                () -> call(site, module, 4L),
                                where + ": redefined > answering a non-Boolean");
                        restore(integer, ">", standardGreater);
                        setOrCreate(integer, "<", answeringFalse());
                        assertEquals(0L, normalized(call(site, module, 4L)),
                                where + ": redefined < answering false");
                        setOrCreate(integer, "<", answering(1L));
                        assertThrows(ProtosSignalException.class,
                                () -> call(site, module, 4L),
                                where + ": redefined < answering a non-Boolean");
                        restore(integer, "<", standardLess);
                        assertEquals(6L, normalized(call(site, module, 4L)),
                                where + ": restored");
                    }
                } finally {
                    context.leave();
                }
            }
        }
        System.out.println("PERF040_E1_ORDERING_CONTROL_CONDITIONS=PASS");
    }

    private static void setOrCreate(ProtosObjectValue owner, String selector, Object value) {
        if (owner.readLocalSlot(selector).isPresent()) {
            owner.assignLocalSlot(selector, value);
        } else {
            owner.createLocalSlot(selector, value);
        }
    }

    /* Restores the local slot, or removes it when the selector was inherited. */
    private static void restore(ProtosObjectValue owner, String selector, Object standard) {
        if (standard == null) {
            owner.removeLocalSlot(selector);
        } else {
            owner.assignLocalSlot(selector, standard);
        }
    }

    /**
     * Selectors are independent: redefining {@code +} on a mutable Integer
     * prototype leaves {@code -}, {@code *} and {@code <} standard at their
     * own warmed sites, before and after the change.
     */
    @Test
    void redefiningPlusLeavesOtherSelectorsStandard() throws Exception {
        withCanonicalPrelude(canonical -> {
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, standardPlus, false);
            ProtosActivation module = alternative.prelude().newModuleActivation();
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            ProtosClosureValue subtract = closure("(x, y) => x - y", module);
            ProtosClosureValue multiply = closure("(x, y) => x * y", module);
            ProtosClosureValue less = closure("(x, y) => x < y", module);
            for (long i = 0; i < 100; i++) {
                assertEquals(i + 1, normalized(call(add, module, i, 1L)));
                assertEquals(i - 1, normalized(call(subtract, module, i, 1L)));
            }
            alternative.integer().assignLocalSlot("+", answering(5L));
            assertEquals(5L, normalized(call(add, module, 1L, 2L)));
            assertEquals(-1L, normalized(call(subtract, module, 1L, 2L)));
            assertEquals(6L, normalized(call(multiply, module, 2L, 3L)));
            assertSame(Boolean.TRUE, call(less, module, 1L, 2L));
        });
        System.out.println("PERF040_E1_SELECTOR_INDEPENDENCE=PASS");
    }

    /**
     * A redefined ordering selector runs at its site; a canonical Boolean
     * result is its value, any other result signals after the selected
     * method completed, and Float receivers keep the standard ordering.
     */
    @Test
    void redefinedComparisonRunsAndRequiresACanonicalBoolean() throws Exception {
        withCanonicalPrelude(canonical -> {
            Object standardPlus =
                    canonical.integerPrototype().readLocalSlot("+").orElseThrow();
            AlternativeIntegers alternative =
                    alternativeIntegers(canonical, standardPlus, false);
            ProtosActivation module = alternative.prelude().newModuleActivation();
            for (String selector : List.of("<", "<=", ">", ">=")) {
                ProtosClosureValue site = closure("(x, y) => x " + selector + " y", module);
                Object standard = call(site, module, 1L, 2L);
                // The ordering selectors are inherited from Number: the
                // override is a structural change of Integer.
                alternative.integer().createLocalSlot(selector, answeringFalse());
                assertSame(Boolean.FALSE, call(site, module, 1L, 2L), selector);
                alternative.integer().assignLocalSlot(selector, answering(1L));
                assertThrows(ProtosSignalException.class,
                        () -> call(site, module, 1L, 2L), selector);
                assertSame(Boolean.valueOf(selector.startsWith("<")),
                        call(site, module, 1.0d, 2.0d), selector + " on Float");
                alternative.integer().removeLocalSlot(selector);
                assertSame(standard, call(site, module, 1L, 2L),
                        selector + ": the inherited standard Number selection is observed");
                assertSame(Boolean.valueOf(!selector.startsWith("<")),
                        call(site, module, 2L, 1L), selector + " after removal");
            }
        });
        System.out.println("PERF040_E1_REDEFINED_COMPARISON=PASS");
    }

    /**
     * Structural evidence over the surviving DSL operations: the only
     * rewriteOn / replaces are the doLL -> doLLOvf overflow generalizations
     * of '+', '-' and '*': a long-returning primitive rewritten on
     * ArithmeticException and replaced by a specialization over exactly the
     * same operand types. Every other specialization neither rewrites nor
     * replaces. The generated nodes exist.
     */
    @Test
    void onlyOverflowGeneralizesAndGeneratedNodesExist() throws Exception {
        for (Map.Entry<String, Class<?>> entry : NODES.entrySet()) {
            Class<?> node = entry.getValue();
            List<Method> specializations = new ArrayList<>();
            for (Class<?> type = node; type != null && type != Object.class;
                    type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Specialization.class)) {
                        specializations.add(method);
                    }
                }
            }
            assertTrue(specializations.size() >= 2, node.getSimpleName());
            assertTrue(specializations.stream().anyMatch(m -> m.getName().equals("doLL")),
                    node.getSimpleName() + " has no long x long specialization");
            assertTrue(specializations.stream().anyMatch(m -> m.getName().equals("doDD")),
                    node.getSimpleName() + " has no double x double specialization");
            for (Method method : specializations) {
                assertOverflowGeneralizationOnly(node, method, specializations);
            }
            if (overflowGeneralizes(node)) {
                assertTrue(specializations.stream().anyMatch(m -> m.getName().equals("doLLOvf")
                                && m.getDeclaringClass() == node),
                        node.getSimpleName() + " declares no doLLOvf of its own");
                for (String name : List.of("doLL", "doLLOvf", "doDD", "doDL", "doLD")) {
                    Method method = specializations.stream()
                            .filter(m -> m.getName().equals(name))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError(
                                    node.getSimpleName() + " has no " + name));
                    assertStandardSelectionGuard(node, method);
                }
                assertOperands(node, specializations, "doDD", double.class, double.class);
                assertOperands(node, specializations, "doDL", double.class, long.class);
                assertOperands(node, specializations, "doLD", long.class, double.class);
            }
            assertTrue(Class.forName(node.getName() + "Gen").getSuperclass() == node,
                    node.getSimpleName());
        }
        System.out.println("PERF040_E1_OVERFLOW_ONLY_GENERALIZATION=PASS");
    }

    private static boolean overflowGeneralizes(Class<?> node) {
        return node == ProtosNumberAddNode.class
                || node == ProtosNumberSubtractNode.class
                || node == ProtosNumberMultiplyNode.class;
    }

    private static void assertOverflowGeneralizationOnly(
            Class<?> node, Method method, List<Method> specializations) {
        Specialization specialization = method.getAnnotation(Specialization.class);
        String where = node.getSimpleName() + "." + method.getName();
        boolean arithmetic = overflowGeneralizes(node);

        if (arithmetic && method.getName().equals("doLL")) {
            assertEquals(List.of(ArithmeticException.class),
                    Arrays.asList(specialization.rewriteOn()), where);
            assertEquals(0, specialization.replaces().length, where);
            assertSame(long.class, method.getReturnType(), where + " must return long");
            assertGuestLongOperands(method, where);
            return;
        }
        assertEquals(0, specialization.rewriteOn().length, where);
        if (!arithmetic || !method.getName().equals("doLLOvf")) {
            assertEquals(0, specialization.replaces().length, where);
            return;
        }
        assertEquals(List.of("doLL"), Arrays.asList(specialization.replaces()), where);
        Method primitive = specializations.stream()
                .filter(candidate -> candidate.getName().equals("doLL"))
                .findFirst()
                .orElseThrow();
        // The generalization accepts exactly the replaced signature (frame,
        // terminal constant, operands and the shared selection guard node);
        // it additionally binds its node to reach the caller's numeric factory.
        List<Class<?>> replaced = Arrays.asList(primitive.getParameterTypes());
        List<Class<?>> signature = new ArrayList<>(Arrays.asList(method.getParameterTypes()));
        assertTrue(signature.remove(com.oracle.truffle.api.nodes.Node.class), where);
        assertEquals(replaced, signature, where);
        assertSame(Object.class, method.getReturnType(), where);
        assertGuestLongOperands(method, where);
    }

    /* Operand layout shared by every fast path: (frame, terminal, left, right, ...). */
    private static final int LEFT_OPERAND = 2;

    private static void assertGuestLongOperands(Method method, String where) {
        Class<?>[] parameters = method.getParameterTypes();
        assertTrue(parameters.length > LEFT_OPERAND + 1, where);
        assertSame(long.class, parameters[LEFT_OPERAND], where + " left operand");
        assertSame(long.class, parameters[LEFT_OPERAND + 1], where + " right operand");
    }

    private static void assertOperands(Class<?> node, List<Method> specializations,
            String name, Class<?> left, Class<?> right) {
        Method method = specializations.stream()
                .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        String where = node.getSimpleName() + "." + name;
        Class<?>[] parameters = method.getParameterTypes();
        assertTrue(parameters.length > LEFT_OPERAND + 1, where);
        assertSame(left, parameters[LEFT_OPERAND], where + " left operand");
        assertSame(right, parameters[LEFT_OPERAND + 1], where + " right operand");
    }

    /* Every primitive fast path stays behind the standard numeric selection guard. */
    private static void assertStandardSelectionGuard(Class<?> node, Method method) {
        assertEquals(List.of("selection.execute(frame, SELECTOR, left)"),
                Arrays.asList(method.getAnnotation(Specialization.class).guards()),
                node.getSimpleName() + "." + method.getName() + " guard");
    }

    /*
     * @Fallback (CLASS retention) and @OperationProxy (SOURCE retention) are
     * invisible to reflection, so they are checked in the source itself:
     * every node owns exactly one in-node generic route (no MISS result),
     * declared as the DSL @Fallback after every primitive specialization, and
     * the root registers every canonical operation under its own name and no
     * retired TryPrimitiveNumeric* operation.
     */
    @Test
    void sourcesDeclareOneGenericRouteAndRegisterEveryOperation() throws Exception {
        for (Class<?> node : NODES.values()) {
            String activeSource = activeSource(
                    EXECUTION_SOURCES.resolve("bytecode/numeric")
                            .resolve(node.getSimpleName() + ".java"));
            // One generic route, the DSL @Fallback taken when no primitive
            // specialization applies, ordered last and never replacing the
            // primitive ones, re-checks the selection and separates the
            // selected-implementation dispatch.
            assertEquals(1, occurrences(activeSource, "doGeneric("), node.getSimpleName());
            assertEquals(1, occurrences(activeSource, "@Fallback"), node.getSimpleName());
            assertTrue(activeSource.contains(
                    "@Fallback\n    @StoreBytecodeIndex\n    @InliningCutoff\n"
                            + "    static Object doGeneric("),
                    node.getSimpleName());
            int fallback = activeSource.indexOf("@Fallback");
            assertTrue(activeSource.lastIndexOf("@Specialization") < fallback,
                    node.getSimpleName() + ": the fallback follows every specialization");
            assertTrue(activeSource.contains("@InliningCutoff"), node.getSimpleName());
            assertTrue(activeSource.contains("ProtosNumericOverloadNode overload"),
                    node.getSimpleName());
            assertTrue(activeSource.contains("selection.execute(frame, SELECTOR, left)"),
                    node.getSimpleName());
            assertFalse(activeSource.contains("MISS"), node.getSimpleName());
            String remaining = activeSource.replace("replaces = \"doLL\"", "");
            assertFalse(remaining.contains("replaces ="), node.getSimpleName());
            if (overflowGeneralizes(node)) {
                assertEquals(1, occurrences(activeSource, "replaces = \"doLL\""),
                        node.getSimpleName());
            } else {
                assertFalse(activeSource.contains("rewriteOn"), node.getSimpleName());
                assertEquals(activeSource, remaining, node.getSimpleName());
            }
            assertTrue(activeSource.contains("@OperationProxy.Proxyable("), node.getSimpleName());
        }
        String root = activeSource(
                EXECUTION_SOURCES.resolve("bytecode/activation/ProtosBytecodeRootNode.java"));
        assertFalse(root.contains("TryPrimitiveNumeric"));
        for (Map.Entry<String, Class<?>> entry : NODES.entrySet()) {
            String declaration = "@OperationProxy(value = " + entry.getValue().getSimpleName()
                    + ".class, name = \"" + entry.getKey() + "\")";
            assertEquals(1, occurrences(root, declaration), declaration);
        }
        System.out.println("PERF040_E1_SOURCE_STRUCTURE=PASS");
    }

    /**
     * The numeric lanes stay reachable after a generic operand in both
     * arrival orders, after overflow and after an exact non-integral
     * quotient, each on one real operation site, without and with an
     * attached debugger.
     */
    @Test
    void numericLanesStayReachableInEveryArrivalOrder() {
        for (boolean debug : new boolean[] {false, true}) {
            try (Context context = context(debug)) {
                for (String sequence : ARRIVAL_ORDER_SEQUENCES) {
                    assertTrue(eval(context, sequence), () -> mode(debug) + ":\n" + sequence);
                }
            }
        }
        System.out.println("PERF040_E1_ARRIVAL_ORDER=PASS");
    }

    /**
     * Overflow-checked long arithmetic (doLL -> doLLOvf) for '+', '-' and '*', executed on real
     * operation sites: long results before and after the first overflow,
     * exact promotion on overflow, and the signed-64 edges that fit. Only
     * results are asserted: after the rewrite the generalization, not doLL,
     * is active.
     */
    @Test
    void integerArithmeticStaysPrimitiveAcrossOverflow() throws Exception {
        withCore(module -> {
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            ProtosClosureValue subtract = closure("(x, y) => x - y", module);
            ProtosClosureValue multiply = closure("(x, y) => x * y", module);

            // Initial primitive path, no overflow.
            assertEquals(5L, normalized(call(add, module, 2L, 3L)));
            assertEquals(-1L, normalized(call(subtract, module, 2L, 3L)));
            assertEquals(6L, normalized(call(multiply, module, 2L, 3L)));

            // First overflow: exact, normalized promotion.
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    call(add, module, Long.MAX_VALUE, 1L)));
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    call(subtract, module, Long.MIN_VALUE, 1L)));
            assertTrue(ProtosNumericValueSupport.isLargeInteger(
                    call(multiply, module, Long.MAX_VALUE, 2L)));

            // The generalized site keeps returning signed-64 Integers for small results.
            for (long i = 0; i < 1000; i++) {
                assertEquals(i + 1, normalized(call(add, module, i, 1L)));
                assertEquals(i - 1, normalized(call(subtract, module, i, 1L)));
                assertEquals(i * 3, normalized(call(multiply, module, i, 3L)));
            }
            // Signed-64 edges that fit are not promoted.
            assertEquals(Long.MAX_VALUE,
                    normalized(call(add, module, Long.MAX_VALUE - 1, 1L)));
            assertEquals(Long.MIN_VALUE,
                    normalized(call(subtract, module, Long.MIN_VALUE + 1, 1L)));
            assertEquals(Long.MIN_VALUE,
                    normalized(call(multiply, module, Long.MIN_VALUE, 1L)));

            // Mixed Float operands after the overflow keep their exact lane.
            assertEquals(2.5d, call(add, module, 1L, 1.5d));
            assertEquals(11L, normalized(call(add, module, 5L, 6L)));
        });
        System.out.println("PERF040_E1_DOLL_DOLLOVF=PASS");
    }

    /**
     * Part B reachability: large Integers of the executing Context take an
     * exact numeric result in every operation, both operand orders, and the
     * small lanes stay reachable afterwards.
     */
    @Test
    void largeIntegerLanesAreReachable() throws Exception {
        withCore(module -> {
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            ProtosClosureValue subtract = closure("(x, y) => x - y", module);
            ProtosClosureValue multiply = closure("(x, y) => x * y", module);
            ProtosClosureValue divide = closure("(x, y) => x / y", module);
            ProtosClosureValue less = closure("(x, y) => x < y", module);

            Object big = call(add, module, Long.MAX_VALUE, 1L);
            assertTrue(ProtosNumericValueSupport.isLargeInteger(big));
            assertTrue(ProtosNumericValueSupport.isLargeInteger(call(add, module, big, 1L)));
            assertTrue(ProtosNumericValueSupport.isLargeInteger(call(add, module, 1L, big)));
            assertEquals(3L, normalized(call(add, module, 1L, 2L)));
            Object negated = call(subtract, module, 0L, big);
            assertEquals(0L, normalized(call(add, module, big, negated)));
            assertEquals(9.223372036854775808E18d, call(add, module, big, 0.5d));
            assertEquals(9.223372036854775808E18d, call(add, module, 0.5d, big));

            assertEquals(Long.MAX_VALUE, normalized(call(subtract, module, big, 1L)));
            assertEquals(0L, normalized(call(multiply, module, big, 0L)));

            assertEquals(2L, normalized(
                    call(divide, module, call(multiply, module, big, 2L), big)));
            assertTrue(ProtosNumericValueSupport.isFraction(call(divide, module, 1L, big)));

            assertSame(Boolean.TRUE, call(less, module, 1L, big));
            assertSame(Boolean.FALSE, call(less, module, big, 1L));
            assertSame(Boolean.FALSE,
                    call(less, module, big, 9.223372036854775808E18d));
            assertSame(Boolean.TRUE,
                    call(less, module, 9.223372036854775808E18d, call(add, module, big, 1L)));
            assertSame(Boolean.FALSE, call(less, module, big, Double.NaN));
            assertSame(Boolean.TRUE,
                    call(less, module, big, Double.POSITIVE_INFINITY));
            assertSame(Boolean.TRUE, call(less, module, 1L, 2L));
        });
        System.out.println("PERF040_E2_LARGE_INTEGER_LANES=PASS");
    }

    /**
     * Matrix 14, 15: the lowerer emits exactly the operation of the site's
     * selector, without and with an attached debugger, and it survives a
     * complete reparse with source and instrumentation.
     */
    @Test
    void selectorOwnsItsOperationWithAndWithoutDebuggerAndReparse() throws Exception {
        for (boolean debug : new boolean[] {false, true}) {
            withCore(debug, module -> {
                for (Map.Entry<String, String> entry : OPERATIONS.entrySet()) {
                    ProtosClosureValue site =
                            closure("(x, y) => x " + entry.getKey() + " y", module);
                    assertOnlyOperation(site, entry.getValue());

                    Object before = normalized(call(site, module,
                            Long.valueOf(6L), Long.valueOf(3L)));
                    rootOf(site).getRootNodes().ensureComplete();
                    assertOnlyOperation(site, entry.getValue());
                    assertEquals(before, normalized(call(site, module,
                            Long.valueOf(6L), Long.valueOf(3L))),
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

    /* The source without comments: only code declares routes. */
    private static String activeSource(Path path) throws Exception {
        String withoutBlocks = Files.readString(path).replaceAll("(?s)/\\*.*?\\*/", "");
        return String.join("\n", withoutBlocks.lines()
                .filter(line -> !line.stripLeading().startsWith("//"))
                .toList());
    }

    /**
     * Exact Integer to binary64 rounding keeps the primitive Long route and isolates the
     * arbitrary-precision route: nearest, ties to even, overflow to infinity and the
     * {@code Long.MIN_VALUE} extremes.
     */
    @Test
    void exactIntegerRoundingIsNearestTiesToEvenAcrossPrimitiveAndLargeIntegers()
            throws Exception {
        withCore(module -> {
            ProtosPrelude prelude = module.preludeOrNullForRuntime();
            java.math.BigInteger twoTo53 = java.math.BigInteger.ONE.shiftLeft(53);

            assertEquals(-0x1p63, ProtosBinary64Rounding.roundExactInteger(Long.MIN_VALUE));
            assertEquals(0x1p63, ProtosBinary64Rounding.roundExactInteger(Long.MAX_VALUE));
            assertEquals(0x1p53, ProtosBinary64Rounding.roundExactInteger((1L << 53) + 1L));

            Object large = ProtosNumericValueSupport.integerFromHost(
                    java.math.BigInteger.ONE.shiftLeft(64).add(java.math.BigInteger.ONE), prelude);
            assertFalse(large instanceof Long);
            assertEquals(0x1p64, ProtosBinary64Rounding.roundExactInteger(large));

            // Both ties round to the even significand: down for 2^53+1, up for 2^53+3.
            Object tie = ProtosNumericValueSupport.integerFromHost(
                    twoTo53.add(java.math.BigInteger.ONE).shiftLeft(20), prelude);
            assertEquals(0x1p73, ProtosBinary64Rounding.roundExactInteger(tie));
            Object upperTie = ProtosNumericValueSupport.integerFromHost(
                    twoTo53.add(java.math.BigInteger.valueOf(3)).shiftLeft(20), prelude);
            assertEquals(
                    (0x1p53 + 4.0) * 0x1p20,
                    ProtosBinary64Rounding.roundExactInteger(upperTie));

            Object huge = ProtosNumericValueSupport.integerFromHost(
                    java.math.BigInteger.ONE.shiftLeft(1024), prelude);
            Object negativeHuge = ProtosNumericValueSupport.integerFromHost(
                    java.math.BigInteger.ONE.shiftLeft(1024).negate(), prelude);
            assertEquals(
                    Double.POSITIVE_INFINITY, ProtosBinary64Rounding.roundExactInteger(huge));
            assertEquals(
                    Double.NEGATIVE_INFINITY,
                    ProtosBinary64Rounding.roundExactInteger(negativeHuge));
        });
    }

    @Test
    void exactIntegerQuotientRoundingKeepsSignedZeroAndLongMinValueExtremes()
            throws Exception {
        withCore(module -> {
            ProtosPrelude prelude = module.preludeOrNullForRuntime();

            assertEquals(0x1p63, ProtosBinary64Rounding.divideExactIntegers(Long.MIN_VALUE, -1L));
            assertEquals(-0x1p62, ProtosBinary64Rounding.divideExactIntegers(Long.MIN_VALUE, 2L));
            assertEquals(1.0 / 3.0, ProtosBinary64Rounding.divideExactIntegers(1L, 3L));
            assertThrows(
                    ArithmeticException.class,
                    () -> ProtosBinary64Rounding.divideExactIntegers(1L, 0L));

            Object twoTo64 = ProtosNumericValueSupport.integerFromHost(
                    java.math.BigInteger.ONE.shiftLeft(64), prelude);
            // Mixed primitive and large operands take the arbitrary-precision route.
            assertEquals(0x1p63, ProtosBinary64Rounding.divideExactIntegers(twoTo64, 2L));
            assertEquals(0x1p-64, ProtosBinary64Rounding.divideExactIntegers(1L, twoTo64));

            Object negativeTiny = ProtosNumericValueSupport.integerFromHost(
                    java.math.BigInteger.ONE.shiftLeft(1200).negate(), prelude);
            double underflow = ProtosBinary64Rounding.divideExactIntegers(1L, negativeTiny);
            assertEquals(
                    Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(underflow),
                    "a nonzero quotient that rounds to zero keeps its sign");
        });
    }

    /** Mixed Integer and Float addition stays exact where binary64 represents the result. */
    @Test
    void mixedIntegerAndFloatAdditionKeepsTheFloatFamily() throws Exception {
        withCore(module -> {
            ProtosClosureValue add = closure("(x, y) => x + y", module);
            assertEquals(1.5, call(add, module, 1L, 0.5));
            assertEquals(1.5, call(add, module, 0.5, 1L));
            assertEquals(3L, normalized(call(add, module, 1L, 2L)));
        });
    }

    /**
     * A runtime value without a standard representation reports its concrete host class from the
     * exceptional delegation path, which never yields a parent.
     */
    @Test
    void unsupportedRepresentationDelegationReportsTheConcreteReceiverClass() throws Exception {
        withCore(module -> {
            ProtosPrelude prelude = module.preludeOrNullForRuntime();
            final class UnrepresentedValue {}
            UnsupportedOperationException failure =
                    assertThrows(
                            UnsupportedOperationException.class,
                            () -> ProtosValueLookup.delegationParent(
                                    new UnrepresentedValue(), prelude));
            assertEquals(
                    "Standard delegation parent is not implemented for runtime value representation "
                            + UnrepresentedValue.class.getName(),
                    failure.getMessage());
            // The specialized branches still answer their parents.
            assertTrue(ProtosValueLookup.delegationParent(1L, prelude).isPresent());
            assertTrue(ProtosValueLookup.delegationParent(1.0, prelude).isPresent());
            assertSame(
                    ProtosObjectValue.rootObject(),
                    ProtosValueLookup.delegationParent(Boolean.TRUE, prelude).orElseThrow());
        });
    }

    private static Object normalized(Object value) {
        return value instanceof Long integer ? integer.longValue() : value;
    }

    private static int occurrences(String text, String fragment) {
        int count = 0;
        for (int at = text.indexOf(fragment); at >= 0; at = text.indexOf(fragment, at + 1)) {
            count++;
        }
        return count;
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
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                test.run(prelude.newModuleActivation());
            } finally {
                context.leave();
            }
        }
    }

    /**
     * A fresh Context; with {@code debug}, a Truffle debugger session is
     * attached to its Engine before any evaluation and lives until the
     * Engine closes (PLAT067-B: it observes, it never selects a lowering).
     */
    private static Context context(boolean debug) {
        Context context =
                Context.newBuilder(ProtosLanguage.ID)
                        .option("protos.CoreRoot", CORE.toAbsolutePath().toString())
                        .build();
        if (debug) {
            Debugger.find(context.getEngine()).startSession(event -> {});
        }
        return context;
    }

    private static String mode(boolean debug) {
        return debug ? "debugger attached" : "no debugger";
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
        // I115: a direct function call through the shared dispatcher.
        return ProtosNumericValueSupport.guestValue(
                ProtosCallRequest.call(closure, supplied, caller).callUncached());
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

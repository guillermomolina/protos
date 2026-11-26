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

import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosCurrentNumericRelationsTest {

    @Test
    void equalityUsesExactMathematicalValue() {
        Long beyond = Long.valueOf((1L << 53) + 1L);
        ProtosFloatValue rounded =
                new ProtosFloatValue((double) (1L << 53));

        assertFalse(ProtosStandardNumberEqualityProtocol.numericEquals(
                beyond, rounded));

        Long integer = Long.valueOf(42L);
        ProtosFloatValue floating = new ProtosFloatValue(42.0);

        assertTrue(ProtosStandardNumberEqualityProtocol.numericEquals(
                integer, floating));
        assertTrue(ProtosStandardNumberEqualityProtocol.numericEquals(
                floating, integer));

        assertEquals(
                hash(integer, null),
                hash(floating, null));
    }

    @Test
    void signedZeroNanAndHashPreserveCurrentSemantics() {
        ProtosFloatValue positive = new ProtosFloatValue(0.0);
        ProtosFloatValue negative = new ProtosFloatValue(-0.0);
        ProtosFloatValue nan = new ProtosFloatValue(Double.NaN);

        assertTrue(ProtosCurrentNumericRelations.numericEquals(
                positive, negative));
        assertFalse(ProtosCurrentNumericRelations.numericEquals(nan, nan));

        assertEquals(hash(positive, null), hash(negative, null));
        assertEquals("2146959360", hash(nan, null));

        assertEquals(
                ProtosStandardNumberOrderingProtocol.Comparison.UNORDERED,
                ProtosStandardNumberOrderingProtocol.compare(nan, positive));
    }

    @Test
    void orderingIsExactAcrossBinary64Boundaries() throws IOException {
        ProtosPrelude prelude = corePrelude();
        Long max =
                Long.valueOf(Long.MAX_VALUE);
        ProtosFloatValue rounded =
                new ProtosFloatValue((double) Long.MAX_VALUE);

        assertEquals(
                ProtosStandardNumberOrderingProtocol.Comparison.LESS,
                ProtosStandardNumberOrderingProtocol.compare(max, rounded));

        assertEquals(
                ProtosStandardNumberOrderingProtocol.Comparison.GREATER,
                ProtosStandardNumberOrderingProtocol.compare(rounded, max));

        Object huge = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(BigInteger.ONE.shiftLeft(130), prelude);

        assertEquals(
                ProtosStandardNumberOrderingProtocol.Comparison.GREATER,
                ProtosStandardNumberOrderingProtocol.compare(
                        new ProtosFloatValue(Double.POSITIVE_INFINITY),
                        huge));

        ProtosFloatValue exact =
                new ProtosFloatValue(Math.scalb(1.0, 130));

        assertTrue(ProtosCurrentNumericRelations.numericEquals(huge, exact));
        assertEquals(hash(huge, prelude), hash(exact, prelude));
        assertSame(huge, ProtosCurrentNumericRelations.normalHash(huge, prelude));
    }

    @Test
    void floatOrderingAgainstBigIntegersIsExactOnBothSidesOfTheLongRange() throws IOException {
        ProtosPrelude prelude = corePrelude();
        Object twoPow63 = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(BigInteger.ONE.shiftLeft(63), prelude);
        Object belowMinLong =
                com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(BigInteger.ONE.shiftLeft(63).negate().subtract(BigInteger.ONE), prelude);
        Object twoPow70Plus1 =
                com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(BigInteger.ONE.shiftLeft(70).add(BigInteger.ONE), prelude);

        assertTrue(ProtosCurrentNumericRelations.numericEquals(
                twoPow63, new ProtosFloatValue(0x1p63)));
        assertComparison(ProtosStandardNumberOrderingProtocol.Comparison.LESS,
                new ProtosFloatValue(0x1.fffffffffffffp62), twoPow63);
        assertComparison(ProtosStandardNumberOrderingProtocol.Comparison.GREATER,
                new ProtosFloatValue(-0x1p63), belowMinLong);
        assertComparison(ProtosStandardNumberOrderingProtocol.Comparison.GREATER,
                new ProtosFloatValue(-0.0), belowMinLong);
        assertComparison(ProtosStandardNumberOrderingProtocol.Comparison.LESS,
                new ProtosFloatValue(0x1p70), twoPow70Plus1);
        assertComparison(ProtosStandardNumberOrderingProtocol.Comparison.GREATER,
                new ProtosFloatValue(0x1.0000000000001p70), twoPow70Plus1);
        assertComparison(ProtosStandardNumberOrderingProtocol.Comparison.LESS,
                new ProtosFloatValue(-0x1p70), belowMinLong);
        assertComparison(ProtosStandardNumberOrderingProtocol.Comparison.UNORDERED,
                new ProtosFloatValue(Double.NaN), twoPow63);
    }

    @Test
    void signed64EdgesAndOneHundredThirtyBitIntegersOrderExactly() throws IOException {
        ProtosPrelude prelude = corePrelude();
        var less = ProtosStandardNumberOrderingProtocol.Comparison.LESS;
        var greater = ProtosStandardNumberOrderingProtocol.Comparison.GREATER;
        var equal = ProtosStandardNumberOrderingProtocol.Comparison.EQUAL;
        Object twoPow63 = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(BigInteger.ONE.shiftLeft(63), prelude);
        Long max = Long.valueOf(Long.MAX_VALUE);
        Long min = Long.valueOf(Long.MIN_VALUE);
        assertEquals(less, ProtosCurrentNumericRelations.compare(max, twoPow63));
        assertEquals(greater, ProtosCurrentNumericRelations.compare(twoPow63, max));
        assertFalse(ProtosCurrentNumericRelations.numericEquals(max, twoPow63));

        assertTrue(ProtosCurrentNumericRelations.numericEquals(min, new ProtosFloatValue(-0x1p63)));
        assertEquals(equal, ProtosCurrentNumericRelations.compare(min, new ProtosFloatValue(-0x1p63)));
        assertEquals(less, ProtosCurrentNumericRelations.compare(
                min, new ProtosFloatValue(-0x1.fffffffffffffp62)));
        assertEquals(greater, ProtosCurrentNumericRelations.compare(
                min, new ProtosFloatValue(-0x1.0000000000001p63)));

        BigInteger magnitude = BigInteger.ONE.shiftLeft(129).add(BigInteger.ONE);
        Object positive = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(magnitude, prelude);
        Object negative = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(magnitude.negate(), prelude);
        assertComparison(less, new ProtosFloatValue(0x1p129), positive);
        assertComparison(greater, new ProtosFloatValue(0x1.0000000000001p129), positive);
        assertComparison(greater, new ProtosFloatValue(-0x1p129), negative);
        assertComparison(less, new ProtosFloatValue(-0x1.0000000000001p129), negative);
        assertComparison(less, new ProtosFloatValue(Double.NEGATIVE_INFINITY), negative);
        assertComparison(greater, new ProtosFloatValue(Double.POSITIVE_INFINITY), negative);
        assertComparison(less, new ProtosFloatValue(Double.NEGATIVE_INFINITY), positive);
        assertComparison(
                ProtosStandardNumberOrderingProtocol.Comparison.UNORDERED,
                new ProtosFloatValue(Double.NaN),
                negative);
        assertFalse(ProtosCurrentNumericRelations.numericEquals(
                negative, new ProtosFloatValue(Double.NaN)));

        Object exactNegative = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(BigInteger.ONE.shiftLeft(129).negate(), prelude);
        assertTrue(ProtosCurrentNumericRelations.numericEquals(
                exactNegative, new ProtosFloatValue(-0x1p129)));
        assertTrue(ProtosCurrentNumericRelations.numericEquals(
                Long.valueOf(1L << 53), new ProtosFloatValue(0x1p53)));
        assertFalse(ProtosCurrentNumericRelations.numericEquals(
                Long.valueOf((1L << 53) + 1L), new ProtosFloatValue(0x1p53)));
    }

    private static void assertComparison(
            ProtosStandardNumberOrderingProtocol.Comparison expected,
            ProtosFloatValue floating,
            Object integer) {
        assertEquals(expected, ProtosStandardNumberOrderingProtocol.compare(floating, integer));
    }

    @Test
    void javaHostNumbersRemainOutsideGuestNumericDomain() {
        // I115: a signed-64 Long is the guest Integer itself, so it is compared.
        assertEquals(ProtosStandardNumberOrderingProtocol.Comparison.EQUAL,
                ProtosCurrentNumericRelations.compare(Long.valueOf(1), Long.valueOf(1L)));

        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosCurrentNumericRelations.normalHash(BigInteger.ONE, null));
    }

    @Test
    void overloadedEqualityStaysCorrectAcrossCachedSelections() throws IOException {
        ProtosPrelude prelude = corePrelude();

        Object result = ProtosTestExecutionSupport.evaluate(
                """
                calls: {
                    yes: 0
                    no: 0
                }
                yes: { equals: (other) => {
                    calls.yes = calls.yes + 1
                    true
                } }.alias("equals", "==")
                no: { equals: (other) => {
                    calls.no = calls.no + 1
                    false
                } }.alias("equals", "==")
                child: yes {}
                receivers: [yes, no, child, yes, no, child, 1, 1.0, "a", true, null]
                expected: [true, false, true, true, false, true, true, true, false, false, false]
                state: {
                    mismatches: 0
                    rounds: 0
                }
                rounds: [1, 2, 3]
                rounds.each((round) => {
                    state.rounds = state.rounds + 1
                    n: 0
                    receivers.each((r) => {
                        ((r == 1) === expected[n]).ifFalse(() => {
                            state.mismatches = state.mismatches + 1
                        })
                        n = n + 1
                    })
                })
                ok: (state.rounds == 3) && (state.mismatches == 0) && (calls.yes == 12) && (calls.no == 6)
                ok
                """,
                prelude.newModuleActivation());

        assertSame(Boolean.TRUE, result);
    }

    /*
     * Alternating receiver representations and selectors at shared operator
     * sites: custom + and == of ordinary receivers sharing a Shape, and the
     * exact numeric, String, Boolean and null laws, stay correct whichever
     * representation each cached selection last observed.
     */
    @Test
    void alternatingRepresentationsAndSelectorsKeepEachSelection() throws IOException {
        ProtosPrelude prelude = corePrelude();
        Object result = ProtosTestExecutionSupport.evaluate(
                """
                BoxBase: {
                    value: 0
                    plusOp: (other) => value + other + 1000
                    eqOp: (other) => other == value
                }
                Box: BoxBase.alias("plusOp", "+").alias("eqOp", "==")
                b1: Box { value: 1 }
                b2: Box { value: 2 }
                add: (l, r) => l + r
                eq: (l, r) => l == r
                state: { mismatches: 0 }
                check: (actual, expected) => {
                    (actual == expected).ifFalse(() => {
                        state.mismatches = state.mismatches + 1
                    })
                }
                rounds: [1, 2, 3]
                rounds.each((round) => {
                    check(add(b1, round), 1001 + round)
                    check(add(1, round), 1 + round)
                    check(add(b2, round), 1002 + round)
                    check(add(1.5, round), 1.5 + round)
                    check(add(b1, round), 1001 + round)
                    check(eq(b1, 1), true)
                    check(eq(b2, 1), false)
                    check(eq(round, round), true)
                    check(eq(round, round + 0.0), true)
                    check(eq(round + 0.5, round), false)
                    check(eq("a", "a"), true)
                    check(eq(true, true), true)
                    check(eq(null, null), true)
                    check(eq(b2, 2), true)
                })
                state.mismatches == 0
                """,
                prelude.newModuleActivation());
        assertSame(Boolean.TRUE, result);
    }

    @Test
    void primitiveFloatHashSharesTheRepresentedFloatHash() throws IOException {
        ProtosPrelude prelude = corePrelude();
        double[] values = {
            0.0, -0.0, 1.5, -2.25, 42.0, Double.NaN,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            0x1p52, 0x1p53, 0x1p53 + 2.0, -0x1p53, Math.nextDown(0x1p53),
            0x1p63, -0x1p63, Math.nextDown(0x1p63), 0x1p70, -0x1p129,
            Double.MIN_VALUE, Double.MAX_VALUE
        };
        for (double value : values) {
            String primitive = com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                    .integerDecimalText(ProtosCurrentNumericRelations.floatNormalHash(value, prelude));
            assertEquals(hash(new ProtosFloatValue(value), prelude), primitive, Double.toString(value));
            assertEquals(hash(Double.valueOf(value), prelude), primitive, Double.toString(value));
        }
    }

    @Test
    void integerHashIsTheIntegerItselfAcrossTheSigned64Range() throws IOException {
        ProtosPrelude prelude = corePrelude();
        for (long value : new long[] {
            Long.MIN_VALUE, Long.MIN_VALUE + 1, -1L, 0L, 1L,
            (1L << 53) - 1L, 1L << 53, (1L << 53) + 1L, Long.MAX_VALUE - 1L, Long.MAX_VALUE
        }) {
            Long integer = Long.valueOf(value);
            assertSame(integer, ProtosCurrentNumericRelations.normalHash(integer, prelude));
        }
    }

    @Test
    void equalNumbersHashEquallyAcrossRepresentations() throws IOException {
        ProtosPrelude prelude = corePrelude();
        assertEquals(hash(Long.valueOf(1L << 53), prelude),
                hash(new ProtosFloatValue(0x1p53), prelude));
        assertEquals(hash(Long.valueOf(Long.MIN_VALUE), prelude),
                hash(new ProtosFloatValue(-0x1p63), prelude));
        Object twoPow63 = com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                .integerFromHost(BigInteger.ONE.shiftLeft(63), prelude);
        assertEquals(hash(twoPow63, prelude), hash(Double.valueOf(0x1p63), prelude));
        assertEquals(hash(Long.valueOf(0L), prelude), hash(Double.valueOf(-0.0), prelude));
        assertFalse(ProtosCurrentNumericRelations.numericEquals(
                Long.valueOf((1L << 53) + 1L), Double.valueOf(0x1p53)));
    }

    @Test
    void fractionAndComplexKeysShareHashesWithEquivalentNumbers() throws IOException {
        ProtosPrelude prelude = corePrelude();
        Object result = ProtosTestExecutionSupport.evaluate(
                """
                m: Map()
                m[1/2] = "half"
                m[3] = "three"
                m[Complex(1/4, 0)] = "quarter"
                m[Complex(1, 2)] = "complex"
                (m[0.5] == "half") && (m[Complex(0.5, 0)] == "half") &&
                    (m[3.0] == "three") && (m[Complex(3, 0.0)] == "three") &&
                    (m[0.25] == "quarter") && (m[1/4] == "quarter") &&
                    (m[Complex(1.0, 2/1)] == "complex") && (m.size() == 4)
                """,
                prelude.newModuleActivation());
        assertSame(Boolean.TRUE, result);
    }

    private static String hash(Object number, ProtosPrelude prelude) {
        return com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(
                ProtosCurrentNumericRelations.normalHash(number, prelude));
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }
}

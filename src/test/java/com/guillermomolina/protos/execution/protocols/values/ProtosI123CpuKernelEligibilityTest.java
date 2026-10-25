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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosComplexNumericSupport;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosMatrixValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosVectorValue;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import com.guillermomolina.protos.execution.ProtosI123CpuKernelCandidateSupport.CandidateShape;
import com.guillermomolina.protos.execution.ProtosI123CpuKernelCandidateSupport.PathPlan;
import com.guillermomolina.protos.execution.ProtosI123CpuKernelCandidateSupport.Series;
import org.junit.jupiter.api.Test;

/**
 * I123 / PLAT068 eligibility guards: the actual D214 {@code Matrix.matmul} Float results equal,
 * bit for bit, both the strict scalar and the column-lane host cell orders of
 * {@code tools/diagnostics/Plat068CpuKernelProbe.java}, and non-Float Numbers never qualify for a
 * primitive Float kernel. General D214 semantics stay in {@link
 * ProtosStandardLinearAlgebraProtocolTest}. The host kernels here are test-only copies.
 *
 * <p>The test-local candidates A1 and T of {@link ProtosI123CpuKernelCandidateSupport} run over
 * real Protos Matrix carriers and are compared here with the public {@code Matrix.matmul}. They are diagnostics only: never installed into the
 * Standard Library prototypes and never a production path.
 */
final class ProtosI123CpuKernelEligibilityTest {
    private static final String MODULE = "std:math/LinearAlgebra";
    private static final long SEED = 680123L;
    private static final int[][] SHAPES = {{1, 1, 1}, {2, 3, 4}, {4, 7, 5}, {16, 32, 16}, {32, 64, 32}};
    private static final int WARMUP_BATCHES = 25;
    private static final int MEASURED_BATCHES = 31;
    private static final double MAX_RELATIVE_DISPERSION = 0.10d;
    private static final int[][] CANDIDATE_SHAPES = {
        {1, 1, 1}, {4, 7, 8}, {3, 1, 5}, {2, 3, 4}, {32, 64, 32}, {64, 65, 64}
    };
    private static final int[][] CANDIDATE_MEASURE_SHAPES = {
        {1, 1, 1}, {4, 7, 8}, {32, 64, 32}, {64, 65, 64}, {128, 256, 128}
    };

    private static volatile double sink;
    /* Every end-to-end timed result escapes here; a volatile store shared by all its variants. */
    private static volatile Object escapedResult;

    @Test
    void strictFloatMatmulMatchesIndependentHostCellOrders() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        for (int[] shape : SHAPES) {
            int rows = shape[0];
            int inner = shape[1];
            int columns = shape[2];
            double[][] in = inputs(rows, inner, columns);
            ProtosMatrixValue result =
                    matmul(matrix(prelude, a, rows, inner, in[0]),
                            matrix(prelude, a, inner, columns, in[1]), a);
            assertTrue(ProtosIdentity.identical(new ProtosIntegerValue(rows), result.rowsValue()));
            assertTrue(ProtosIdentity.identical(new ProtosIntegerValue(columns), result.columnsValue()));
            double[] scalar = strictScalarMatmul(in[0], in[1], rows, inner, columns);
            assertBits(scalar, ProtosI123CpuKernelCandidateSupport.columnLaneMatmul(in[0], in[1], rows, inner, columns));
            assertBits(scalar, floats(result.indexedSnapshot()));
        }
    }

    @Test
    void strictContractionWitnessesRemainExact() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        double e = Math.scalb(1.0d, -27);
        double inf = Double.POSITIVE_INFINITY;
        double[][][] witnesses = {
            {{1e16, -1e16, 1}, {1, 1, 1}, {1.0}},
            {{1e16, 1, -1e16}, {1, 1, 1}, {0.0}},
            {{-0.0}, {1.0}, {-0.0}},
            {{Double.MIN_NORMAL}, {0.5}, {Double.MIN_NORMAL * 0.5}},
            {{Double.MIN_VALUE}, {1.0}, {Double.MIN_VALUE}},
            {{1 + e, 1}, {1 - e, -1}, {0.0}},
            {{inf}, {0.0}, {Double.NaN}},
            {{inf, 1}, {1, 1}, {inf}},
        };
        for (double[][] w : witnesses) {
            int inner = w[0].length;
            ProtosMatrixValue product =
                    matmul(matrix(prelude, a, 1, inner, w[0]), matrix(prelude, a, inner, 1, w[1]), a);
            assertBits(w[2], floats(product.indexedSnapshot()));
            Object dot =
                    ProtosInvocation.invokeMessage(
                            vector(prelude, a, w[0]), "dot", List.of(vector(prelude, a, w[1])), a);
            assertBits(w[2], floats(List.of(dot)));
        }
    }

    @Test
    void emptyContractionsStaySemanticIntegerZero() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        ProtosMatrixValue result =
                matmul(matrix(prelude, a, 2, 0, new double[0]), matrix(prelude, a, 0, 3, new double[0]), a);
        assertTrue(ProtosIdentity.identical(new ProtosIntegerValue(2), result.rowsValue()));
        assertTrue(ProtosIdentity.identical(new ProtosIntegerValue(3), result.columnsValue()));
        List<Object> cells = result.indexedSnapshot();
        assertEquals(6, cells.size());
        for (Object cell : cells) {
            assertInstanceOf(ProtosIntegerValue.class, cell);
            assertTrue(ProtosIdentity.identical(new ProtosIntegerValue(0), cell));
        }
        Object dot =
                ProtosInvocation.invokeMessage(
                        vector(prelude, a, new double[0]), "dot",
                        List.of(vector(prelude, a, new double[0])), a);
        assertInstanceOf(ProtosIntegerValue.class, dot);
        assertTrue(ProtosIdentity.identical(new ProtosIntegerValue(0), dot));
    }

    @Test
    void nonFloatFamiliesNeverEnterPrimitiveProbe() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        Object half = fraction(1, 2, prelude);
        Object third = fraction(1, 3, prelude);
        Object big =
                ProtosNumericValueSupport.integerFromHost(
                        BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), prelude);
        Object complex =
                ProtosComplexNumericSupport.normalize(
                        new ProtosIntegerValue(0), new ProtosIntegerValue(1), prelude);

        ProtosMatrixValue fractions = generic(prelude, a, 1, 2, half, third);
        ProtosMatrixValue mixed = generic(prelude, a, 2, 1, new ProtosIntegerValue(1), new ProtosFloatValue(1.0d));
        ProtosMatrixValue exact = generic(prelude, a, 1, 2, big, new ProtosIntegerValue(1));
        ProtosMatrixValue ones = generic(prelude, a, 2, 1, new ProtosIntegerValue(1), new ProtosIntegerValue(1));
        ProtosMatrixValue imaginary = generic(prelude, a, 1, 1, complex);
        for (ProtosMatrixValue operand : List.of(fractions, mixed, exact, ones, imaginary)) {
            assertNull(packFloatOnly(operand.indexedSnapshot()));
        }
        assertNotNull(packFloatOnly(matrix(prelude, a, 1, 1, new double[] {2.0d}).indexedSnapshot()));

        assertTrue(ProtosIdentity.identical(fraction(5, 6, prelude), matmul(fractions, ones, a).indexedSnapshot().get(0)));
        assertTrue(
                ProtosIdentity.identical(
                        ProtosNumericValueSupport.integerFromHost(
                                BigInteger.ONE.shiftLeft(63).add(BigInteger.ONE), prelude),
                        matmul(exact, ones, a).indexedSnapshot().get(0)));
        assertTrue(ProtosIdentity.identical(new ProtosIntegerValue(-1), matmul(imaginary, imaginary, a).indexedSnapshot().get(0)));
    }

    @Test
    void optionalEndToEndMeasurement() throws Exception {
        if (!Boolean.getBoolean("protos.i123.measure")) {
            return;
        }
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        int rows = 32;
        int inner = 64;
        int columns = 32;
        int repetitions = 16;
        double[][] in = inputs(rows, inner, columns);
        ProtosMatrixValue left = matrix(prelude, a, rows, inner, in[0]);
        ProtosMatrixValue right = matrix(prelude, a, inner, columns, in[1]);
        double[] expected = strictScalarMatmul(in[0], in[1], rows, inner, columns);
        assertBits(expected, floats(matmul(left, right, a).indexedSnapshot()));
        assertBits(expected, ProtosI123CpuKernelCandidateSupport.columnLaneMatmul(in[0], in[1], rows, inner, columns));
        List<Object> snapshot = left.indexedSnapshot();

        String[] labels = {
            "I123_PROTOS_MATMUL_NS", "I123_R1_PACK_NS", "I123_SCALAR_KERNEL_NS",
            "I123_COLUMN_LANES_NS", "I123_RESULT_MATERIALIZE_NS"
        };
        long[][] samples = new long[labels.length][MEASURED_BATCHES];
        long[][] cpuSamples = new long[labels.length][MEASURED_BATCHES];
        long[][] gcSamples = new long[labels.length][MEASURED_BATCHES];
        for (int batch = 0; batch < WARMUP_BATCHES + MEASURED_BATCHES; batch++) {
            for (int step = 0; step < labels.length; step++) {
                int variant = batch % 2 == 0 ? step : labels.length - 1 - step;
                BatchMeasurement measurement =
                        timeVariant(variant, left, right, a, in, snapshot, expected, repetitions);
                if (batch >= WARMUP_BATCHES) {
                    samples[variant][batch - WARMUP_BATCHES] = measurement.wallNs();
                    cpuSamples[variant][batch - WARMUP_BATCHES] = measurement.threadCpuNs();
                    gcSamples[variant][batch - WARMUP_BATCHES] = measurement.gcCollections();
                }
            }
        }
        assertBits(expected, floats(matmul(left, right, a).indexedSnapshot()));
        System.out.println("I123_SHAPE=" + rows + "x" + inner + "x" + columns + " repetitions=" + repetitions
                + " warmup=" + WARMUP_BATCHES + " samples=" + MEASURED_BATCHES);
        for (int variant = 0; variant < labels.length; variant++) {
            report(labels[variant], samples[variant], repetitions);
            reportDiagnostics(labels[variant], cpuSamples[variant], gcSamples[variant], repetitions);
        }
        System.out.println("I123_END_TO_END_CLASS=DIAGNOSTIC_ONLY");
        System.out.println("I123_SIMD=NOT_PROVEN");
        System.out.println("I123_SINK=" + sink);
    }

    @Test
    void candidatesMatchPublicMatmulOnRectangularShapes() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        for (int[] shape : CANDIDATE_SHAPES) {
            double[][] in = inputs(shape[0], shape[1], shape[2]);
            ProtosMatrixValue left = matrix(prelude, a, shape[0], shape[1], in[0]);
            ProtosMatrixValue right = matrix(prelude, a, shape[1], shape[2], in[1]);
            List<Object> leftBefore = left.indexedSnapshot();
            List<Object> rightBefore = right.indexedSnapshot();
            ProtosMatrixValue expected = matmul(left, right, a);
            assertBits(strictScalarMatmul(in[0], in[1], shape[0], shape[1], shape[2]),
                    floats(expected.indexedSnapshot()));
            ProtosMatrixValue first = ProtosI123CpuKernelCandidateSupport.strictR1FloatMatmulCandidate(left, right);
            ProtosMatrixValue second = ProtosI123CpuKernelCandidateSupport.strictTemporaryPackedMatmulCandidate(left, right);
            assertCandidateMatches(expected, first, left, right);
            assertCandidateMatches(expected, second, left, right);
            // Repeated operations on the same input stay independent of each other.
            assertCandidateMatches(expected, ProtosI123CpuKernelCandidateSupport.strictR1FloatMatmulCandidate(left, right), first, second);
            assertCandidateMatches(expected, ProtosI123CpuKernelCandidateSupport.strictTemporaryPackedMatmulCandidate(left, right), first, second);
            assertSameReferences(leftBefore, left.indexedSnapshot());
            assertSameReferences(rightBefore, right.indexedSnapshot());
        }
    }

    @Test
    void candidatesPreserveStrictContractionWitnesses() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        double e = Math.scalb(1.0d, -27);
        double inf = Double.POSITIVE_INFINITY;
        double[][][] witnesses = {
            {{1e16, -1e16, 1}, {1, 1, 1}, {1.0}},
            {{1e16, 1, -1e16}, {1, 1, 1}, {0.0}},
            {{-0.0}, {1.0}, {-0.0}},
            {{-1.0}, {0.0}, {-0.0}},
            {{-1.0, -1.0}, {0.0, 0.0}, {-0.0}},
            {{-0.0, 0.0}, {1.0, 1.0}, {0.0}},
            {{Double.MIN_NORMAL}, {0.5}, {Double.MIN_NORMAL * 0.5}},
            {{Double.MIN_VALUE}, {1.0}, {Double.MIN_VALUE}},
            {{Double.MIN_VALUE, Double.MIN_VALUE}, {1.0, -0.5}, {Double.MIN_VALUE}},
            {{1 + e, 1}, {1 - e, -1}, {0.0}},
            {{-1, 1 + e}, {1, 1 - e}, {0.0}},
            {{inf}, {0.0}, {Double.NaN}},
            {{inf, 1}, {1, 1}, {inf}},
            {{-inf, 1}, {1, 1}, {-inf}},
            {{inf, inf}, {1, -1}, {Double.NaN}},
            {{Double.NaN, 1}, {1, 1}, {Double.NaN}},
            {{Double.MAX_VALUE, Double.MAX_VALUE}, {1, 1}, {inf}},
        };
        for (double[][] w : witnesses) {
            int inner = w[0].length;
            ProtosMatrixValue left = matrix(prelude, a, 1, inner, w[0]);
            ProtosMatrixValue right = matrix(prelude, a, inner, 1, w[1]);
            ProtosMatrixValue expected = matmul(left, right, a);
            assertBits(w[2], floats(expected.indexedSnapshot()));
            assertCandidateMatches(expected, ProtosI123CpuKernelCandidateSupport.strictR1FloatMatmulCandidate(left, right), left, right);
            assertCandidateMatches(expected, ProtosI123CpuKernelCandidateSupport.strictTemporaryPackedMatmulCandidate(left, right), left, right);
        }
        // Rectangular witness: two independent columns, each reduced in ascending k.
        ProtosMatrixValue left = matrix(prelude, a, 2, 3, new double[] {1e16, -1e16, 1, -1, -1, -1});
        ProtosMatrixValue right = matrix(prelude, a, 3, 2, new double[] {1, 0, 1, 0, 1, 0});
        ProtosMatrixValue expected = matmul(left, right, a);
        assertBits(new double[] {1.0, 0.0, -3.0, -0.0}, floats(expected.indexedSnapshot()));
        assertCandidateMatches(expected, ProtosI123CpuKernelCandidateSupport.strictR1FloatMatmulCandidate(left, right), left, right);
        assertCandidateMatches(expected, ProtosI123CpuKernelCandidateSupport.strictTemporaryPackedMatmulCandidate(left, right), left, right);
    }

    @Test
    void candidatesKeepEmptyContractionsExactIntegerZero() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        int[][] shapes = {{2, 0, 3}, {0, 4, 0}, {3, 0, 0}, {0, 0, 5}, {1, 0, 1}};
        for (int[] shape : shapes) {
            ProtosMatrixValue left = matrix(prelude, a, shape[0], shape[1], new double[shape[0] * shape[1]]);
            ProtosMatrixValue right = matrix(prelude, a, shape[1], shape[2], new double[shape[1] * shape[2]]);
            ProtosMatrixValue expected = matmul(left, right, a);
            for (ProtosMatrixValue candidate : List.of(
                    ProtosI123CpuKernelCandidateSupport.strictR1FloatMatmulCandidate(left, right),
                    ProtosI123CpuKernelCandidateSupport.strictTemporaryPackedMatmulCandidate(left, right))) {
                assertCandidateMatches(expected, candidate, left, right);
                assertEquals(shape[0] * shape[2], candidate.indexedSnapshot().size());
                for (Object cell : candidate.indexedSnapshot()) {
                    assertInstanceOf(ProtosIntegerValue.class, cell);
                    assertTrue(ProtosIdentity.identical(new ProtosIntegerValue(0), cell));
                }
            }
        }
    }

    @Test
    void candidatesDeclineIneligibleOperandsWhileCanonicalFallbackAnswers() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        Object half = fraction(1, 2, prelude);
        Object third = fraction(1, 3, prelude);
        Object big =
                ProtosNumericValueSupport.integerFromHost(
                        BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), prelude);
        double e = Math.scalb(1.0d, -27);
        Object leftComplex =
                ProtosComplexNumericSupport.normalize(
                        new ProtosFloatValue(1 + e), new ProtosFloatValue(1.0d), prelude);
        Object rightComplex =
                ProtosComplexNumericSupport.normalize(
                        new ProtosFloatValue(1 - e), new ProtosFloatValue(1.0d), prelude);
        assertTrue(ProtosComplexNumericSupport.isComplex(leftComplex));

        double[][] in = inputs(4, 7, 8);
        Object[] heterogeneousValues = new Object[in[0].length];
        for (int index = 0; index < heterogeneousValues.length; index++) {
            heterogeneousValues[index] = new ProtosFloatValue(in[0][index]);
        }
        heterogeneousValues[heterogeneousValues.length - 1] = new ProtosIntegerValue(1);
        ProtosMatrixValue heterogeneousLast = generic(prelude, a, 4, 7, heterogeneousValues);
        ProtosMatrixValue floats = matrix(prelude, a, 7, 8, in[1]);
        ProtosMatrixValue floatsLeft = matrix(prelude, a, 8, 4, inputs(8, 4, 1)[0]);
        ProtosMatrixValue fractions = generic(prelude, a, 1, 2, half, third);
        ProtosMatrixValue ones = generic(prelude, a, 2, 1, new ProtosIntegerValue(1), new ProtosIntegerValue(1));
        ProtosMatrixValue exact = generic(prelude, a, 1, 2, big, new ProtosIntegerValue(1));
        ProtosMatrixValue mixed = generic(prelude, a, 1, 2, new ProtosIntegerValue(1), new ProtosFloatValue(1.0d));
        ProtosMatrixValue floatColumn = matrix(prelude, a, 2, 1, new double[] {1.0d, 1.0d});
        ProtosMatrixValue complexLeft = generic(prelude, a, 1, 1, leftComplex);
        ProtosMatrixValue complexRight = generic(prelude, a, 1, 1, rightComplex);
        ProtosMatrixValue mismatched = matrix(prelude, a, 2, 3, new double[6]);

        ProtosMatrixValue[][] declined = {
            {heterogeneousLast, floats}, {floatsLeft, heterogeneousLast},
            {fractions, ones}, {exact, ones}, {mixed, floatColumn}, {floatColumn, mixed},
            {complexLeft, complexRight}, {mismatched, mismatched},
        };
        for (ProtosMatrixValue[] pair : declined) {
            List<Object> leftBefore = pair[0].indexedSnapshot();
            List<Object> rightBefore = pair[1].indexedSnapshot();
            assertNull(ProtosI123CpuKernelCandidateSupport.strictR1FloatMatmulCandidate(pair[0], pair[1]));
            assertNull(ProtosI123CpuKernelCandidateSupport.strictTemporaryPackedMatmulCandidate(pair[0], pair[1]));
            assertSameReferences(leftBefore, pair[0].indexedSnapshot());
            assertSameReferences(rightBefore, pair[1].indexedSnapshot());
        }

        // Canonical public protocol still answers every eligible-shape fallback.
        ProtosMatrixValue heterogeneousResult = matmul(heterogeneousLast, floats, a);
        assertTrue(ProtosIdentity.identical(new ProtosIntegerValue(4), heterogeneousResult.rowsValue()));
        assertTrue(ProtosIdentity.identical(new ProtosIntegerValue(8), heterogeneousResult.columnsValue()));
        assertInstanceOf(ProtosIntegerValue.class, heterogeneousLast.indexedSnapshot().get(27));
        assertTrue(ProtosIdentity.identical(fraction(5, 6, prelude), matmul(fractions, ones, a).indexedSnapshot().get(0)));
        assertTrue(
                ProtosIdentity.identical(
                        ProtosNumericValueSupport.integerFromHost(
                                BigInteger.ONE.shiftLeft(63).add(BigInteger.ONE), prelude),
                        matmul(exact, ones, a).indexedSnapshot().get(0)));
        assertNotNull(matmul(mixed, floatColumn, a).indexedSnapshot().get(0));

        // Bilinear Complex product: the real coordinate (1+e)(1-e) - 1 is rounded once to -2^-54,
        // which separately rounded primitive doubles would lose (they produce 0.0).
        Object product = matmul(complexLeft, complexRight, a).indexedSnapshot().get(0);
        assertBits(new double[] {-Math.scalb(1.0d, -54), 2.0d},
                floats(List.of(ProtosComplexNumericSupport.real(product),
                        ProtosComplexNumericSupport.imaginary(product))));
        // Hermitian dot conjugates the receiver: imaginary (1+e) - (1-e) = 2^-26, not 2.0.
        Object dot =
                ProtosInvocation.invokeMessage(
                        genericVector(prelude, a, leftComplex), "dot",
                        List.of(genericVector(prelude, a, rightComplex)), a);
        assertBits(new double[] {2.0d, Math.scalb(1.0d, -26)},
                floats(List.of(ProtosComplexNumericSupport.real(dot),
                        ProtosComplexNumericSupport.imaginary(dot))));
    }

    @Test
    void optionalCandidateMeasurement() throws Exception {
        if (!Boolean.getBoolean("protos.i123.candidates")) {
            return;
        }
        // Optional selectors: only the selected path and shape are warmed and measured.
        String pathSelector = System.getProperty("protos.i123.candidates.path");
        String shapeSelector = System.getProperty("protos.i123.candidates.shape");
        boolean telemetry = Boolean.getBoolean("protos.i123.candidates.telemetry");
        List<int[]> selected =
                ProtosI123CpuKernelCandidateSupport.selectCandidates(CANDIDATE_MEASURE_SHAPES, pathSelector, shapeSelector);
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        for (int[] shape : selected) {
            measureCandidateShape(prelude, a, shape[0], shape[1], shape[2], pathSelector, telemetry);
        }
        System.out.println("I123_GUEST_WORKLOAD=NOT_EXERCISED GUEST_TIER_NOT_PROVEN");
        System.out.println("I123_CANDIDATE_SPEEDUP=NOT_COMPUTED CANDIDATES_EXCLUDE_GUEST_DISPATCH=YES");
        System.out.println("I123_SIMD=NOT_PROVEN");
        System.out.println("I123_SINK=" + sink);
    }

    /* ---- Measurement helpers (no assertions inside timed loops) ---- */

    /**
     * One measured batch. {@code threadCpuNs} and {@code gcCollections} are {@code -1} when the
     * corresponding management information is unavailable. A nonzero GC count only identifies a
     * batch that overlapped a collection; it does not attribute elapsed time to GC.
     */
    private record BatchMeasurement(long wallNs, long threadCpuNs, long gcCollections) {
    }

    private static BatchMeasurement timeVariant(
            int variant, ProtosMatrixValue left, ProtosMatrixValue right, ProtosActivation a,
            double[][] in, List<Object> snapshot, double[] expected, int repetitions) {
        int rows = 32;
        int inner = 64;
        int columns = 32;
        double consumed = 0.0d;
        long gcBefore = gcCollectionCount();
        long cpuBefore = currentThreadCpuNs();
        long start = System.nanoTime();
        for (int repetition = 0; repetition < repetitions; repetition++) {
            switch (variant) {
                case 0 -> {
                    Object result = ProtosInvocation.invokeMessage(left, "matmul", List.of(right), a);
                    escapedResult = result;
                    consumed += ((ProtosMatrixValue) result).indexedSnapshot().size();
                }
                case 1 -> {
                    double[] packed = packFloatOnly(snapshot);
                    escapedResult = packed;
                    consumed += packed[packed.length - 1];
                }
                case 2 -> {
                    double[] out = strictScalarMatmul(in[0], in[1], rows, inner, columns);
                    escapedResult = out;
                    consumed += out[out.length - 1];
                }
                case 3 -> {
                    double[] out = ProtosI123CpuKernelCandidateSupport.columnLaneMatmul(in[0], in[1], rows, inner, columns);
                    escapedResult = out;
                    consumed += out[out.length - 1];
                }
                default -> {
                    Object[] cells = new Object[expected.length];
                    for (int index = 0; index < cells.length; index++) {
                        cells[index] = new ProtosFloatValue(expected[index]);
                    }
                    escapedResult = cells;
                    consumed += ((ProtosFloatValue) cells[cells.length - 1]).value();
                }
            }
        }
        long elapsed = System.nanoTime() - start;
        long cpuAfter = currentThreadCpuNs();
        long gcAfter = gcCollectionCount();
        sink = sink + consumed;
        long cpu = cpuBefore >= 0 && cpuAfter >= 0 ? cpuAfter - cpuBefore : -1L;
        long gc = gcBefore >= 0 && gcAfter >= 0 ? gcAfter - gcBefore : -1L;
        return new BatchMeasurement(elapsed, cpu, gc);
    }

    /** Current-thread CPU time, or {@code -1} when unsupported or disabled (never enabled here). */
    private static long currentThreadCpuNs() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        if (!threads.isCurrentThreadCpuTimeSupported() || !threads.isThreadCpuTimeEnabled()) {
            return -1L;
        }
        return threads.getCurrentThreadCpuTime();
    }

    /** Sum of nonnegative collection counts across all collectors, or {@code -1} when none report. */
    private static long gcCollectionCount() {
        long total = 0L;
        boolean available = false;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = collector.getCollectionCount();
            if (count >= 0) {
                total += count;
                available = true;
            }
        }
        return available ? total : -1L;
    }

    private static void report(String label, long[] samples, int repetitions) {
        double[] sorted = new double[samples.length];
        for (int index = 0; index < samples.length; index++) {
            sorted[index] = samples[index] / (double) repetitions;
        }
        Arrays.sort(sorted);
        double median = sorted[sorted.length / 2];
        double p10 = sorted[(int) Math.round(0.10d * (sorted.length - 1))];
        double p90 = sorted[(int) Math.round(0.90d * (sorted.length - 1))];
        double dispersion = median > 0.0d ? (p90 - p10) / median : Double.POSITIVE_INFINITY;
        String stability = dispersion <= MAX_RELATIVE_DISPERSION ? "WITHIN_THRESHOLD" : "NOT_STABLE";
        System.out.println(String.format(Locale.ROOT,
                "%s=median=%.1f p10=%.1f p90=%.1f relDispersion=%.4f stability=%s rawBatchNs=%s",
                label, median, p10, p90, dispersion, stability, Arrays.toString(samples)));
    }

    /**
     * Thread CPU time is reported separately from wall-clock time; their difference is not
     * attributed to GC or scheduling. Samples are reported untrimmed.
     */
    private static void reportDiagnostics(String label, long[] cpuSamples, long[] gcSamples, int repetitions) {
        int gcAffected = 0;
        boolean cpuAvailable = true;
        for (int index = 0; index < cpuSamples.length; index++) {
            if (gcSamples[index] > 0) {
                gcAffected++;
            }
            if (cpuSamples[index] < 0) {
                cpuAvailable = false;
            }
        }
        System.out.println("I123_VARIANT_DIAGNOSTICS[" + label + "]=gcAffectedBatches=" + gcAffected
                + " cpuTimeAvailable=" + cpuAvailable);
        if (!cpuAvailable) {
            return;
        }
        double[] sorted = new double[cpuSamples.length];
        for (int index = 0; index < cpuSamples.length; index++) {
            sorted[index] = cpuSamples[index] / (double) repetitions;
        }
        Arrays.sort(sorted);
        double median = sorted[sorted.length / 2];
        double p10 = sorted[(int) Math.round(0.10d * (sorted.length - 1))];
        double p90 = sorted[(int) Math.round(0.90d * (sorted.length - 1))];
        System.out.println(String.format(Locale.ROOT, "I123_VARIANT_CPU_NS[%s]=median=%.1f p10=%.1f p90=%.1f",
                label, median, p10, p90));
    }

    /* ---- Test-only host kernels, copies of the probe's loop orders ---- */

    private static double[] strictScalarMatmul(
            double[] left, double[] right, int rows, int inner, int columns) {
        double[] result = new double[rows * columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                double accumulator = left[row * inner] * right[column];
                for (int k = 1; k < inner; k++) {
                    double product = left[row * inner + k] * right[k * columns + column];
                    accumulator = accumulator + product;
                }
                result[row * columns + column] = accumulator;
            }
        }
        return result;
    }

    /* Same generator as the probe: a fresh SplittableRandom(680123), left then right. */
    private static double[][] inputs(int rows, int inner, int columns) {
        SplittableRandom random = new SplittableRandom(SEED);
        double[] left = new double[rows * inner];
        double[] right = new double[inner * columns];
        for (int index = 0; index < left.length; index++) {
            left[index] = random.nextDouble(-1.0d, 1.0d);
        }
        for (int index = 0; index < right.length; index++) {
            right[index] = random.nextDouble(-1.0d, 1.0d);
        }
        return new double[][] {left, right};
    }

    /** Experimental Float-only packing: null (declined, nothing converted) for any non-Float. */
    private static double[] packFloatOnly(List<Object> elements) {
        double[] packed = new double[elements.size()];
        for (int index = 0; index < packed.length; index++) {
            if (!(elements.get(index) instanceof ProtosFloatValue)) {
                return null;
            }
            packed[index] = ProtosNumericValueSupport.currentFloatValue(elements.get(index));
        }
        return packed;
    }

    /**
     * Same shape, parent and cell families as the public result; Float cells bit-equal (raw bits
     * for zeros) and fresh: no Float cell is the public result's or any {@code others} element.
     */
    private static void assertCandidateMatches(
            ProtosMatrixValue expected, ProtosMatrixValue actual, ProtosMatrixValue... others) {
        assertNotNull(actual);
        assertNotSame(expected, actual);
        assertTrue(ProtosIdentity.identical(expected.rowsValue(), actual.rowsValue()));
        assertTrue(ProtosIdentity.identical(expected.columnsValue(), actual.columnsValue()));
        assertSame(expected.parent().orElseThrow(), actual.parent().orElseThrow());
        Set<Object> foreign = Collections.newSetFromMap(new IdentityHashMap<>());
        foreign.addAll(expected.indexedSnapshot());
        for (ProtosMatrixValue other : others) {
            assertNotSame(other, actual);
            foreign.addAll(other.indexedSnapshot());
        }
        List<Object> want = expected.indexedSnapshot();
        List<Object> got = actual.indexedSnapshot();
        assertEquals(want.size(), got.size());
        for (int index = 0; index < want.size(); index++) {
            assertEquals(want.get(index).getClass(), got.get(index).getClass());
            if (got.get(index) instanceof ProtosFloatValue) {
                assertBits(floats(List.of(want.get(index))), floats(List.of(got.get(index))));
                assertTrue(!foreign.contains(got.get(index)), "Float cell " + index + " is not fresh");
            } else {
                assertTrue(ProtosIdentity.identical(want.get(index), got.get(index)));
            }
        }
    }

    private static void assertSameReferences(List<Object> expected, List<Object> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertTrue(ProtosIdentity.identical(expected.get(index), actual.get(index)), "element " + index);
        }
    }

    /* ---- Opt-in candidate timing (-Dprotos.i123.candidates=true); no timing assertions ---- */

    private static void measureCandidateShape(
            ProtosPrelude prelude, ProtosActivation a, int rows, int inner, int columns,
            String pathSelector, boolean telemetry) {
        double[][] in = inputs(rows, inner, columns);
        ProtosMatrixValue left = matrix(prelude, a, rows, inner, in[0]);
        ProtosMatrixValue right = matrix(prelude, a, inner, columns, in[1]);
        ProtosMatrixValue expected = matmul(left, right, a);
        assertCandidateMatches(
                expected, ProtosI123CpuKernelCandidateSupport.strictR1FloatMatmulCandidate(left, right), left, right);
        assertCandidateMatches(
                expected, ProtosI123CpuKernelCandidateSupport.strictTemporaryPackedMatmulCandidate(left, right), left, right);
        CandidateShape shape = ProtosI123CpuKernelCandidateSupport.candidateShape(left, right);
        List<Object> leftSnapshot = left.indexedSnapshot();
        List<Object> rightSnapshot = right.indexedSnapshot();
        double[] leftPacked = ProtosI123CpuKernelCandidateSupport.packFloats(leftSnapshot);
        double[] rightPacked = ProtosI123CpuKernelCandidateSupport.packFloats(rightSnapshot);
        double[] packedResult =
                ProtosI123CpuKernelCandidateSupport.columnLaneMatmul(leftPacked, rightPacked, rows, inner, columns);

        String na = "NOT_APPLICABLE";
        String inTotal = "INCLUDED_IN_TOTAL";
        Series snapshot = new Series("SNAPSHOT", () -> {
            ProtosI123CpuKernelCandidateSupport.consume(left.indexedSnapshot());
            return right.indexedSnapshot();
        });
        Series scan = new Series("SCAN",
                () -> ProtosI123CpuKernelCandidateSupport.homogeneousFloat(leftSnapshot)
                        && ProtosI123CpuKernelCandidateSupport.homogeneousFloat(rightSnapshot));
        List<PathPlan> plans = List.of(
                new PathPlan("A0_PUBLIC_MATMUL", "PROTOS_PROTOCOL", na, "REFERENCE", " GUEST_TIER_NOT_PROVEN",
                        Map.of("SNAPSHOT", inTotal, "SCAN", na, "PACK", na, "KERNEL", inTotal, "MATERIALIZE", inTotal),
                        List.of(new Series("TOTAL",
                                () -> ProtosInvocation.invokeMessage(left, "matmul", List.of(right), a)))),
                new PathPlan("A1_R1_FLOAT", "TEST_LOCAL_PROTO_VALUE_KERNEL", "ADMITTED", "EQUIVALENT", "",
                        Map.of("PACK", na, "MATERIALIZE", "INCLUDED_IN_KERNEL"),
                        List.of(new Series("TOTAL",
                                        () -> ProtosI123CpuKernelCandidateSupport.strictR1FloatMatmulCandidate(left, right)),
                                snapshot, scan,
                                new Series("KERNEL", () -> ProtosI123CpuKernelCandidateSupport.r1FloatCells(
                                        leftSnapshot, rightSnapshot, rows, inner, columns)))),
                new PathPlan("T_PACKED", "TEST_LOCAL_PROTO_VALUE_KERNEL", "ADMITTED", "EQUIVALENT", "", Map.of(),
                        List.of(new Series("TOTAL",
                                        () -> ProtosI123CpuKernelCandidateSupport.strictTemporaryPackedMatmulCandidate(left, right)),
                                snapshot, scan,
                                new Series("PACK", () -> {
                                    ProtosI123CpuKernelCandidateSupport.consume(
                                            ProtosI123CpuKernelCandidateSupport.packFloats(leftSnapshot));
                                    return ProtosI123CpuKernelCandidateSupport.packFloats(rightSnapshot);
                                }),
                                new Series("KERNEL", () -> ProtosI123CpuKernelCandidateSupport.columnLaneMatmul(
                                        leftPacked, rightPacked, rows, inner, columns)),
                                new Series("MATERIALIZE", () -> ProtosI123CpuKernelCandidateSupport.candidateResult(
                                        left, shape, ProtosI123CpuKernelCandidateSupport.materializeFloats(packedResult))))),
                new PathPlan("HOST_SCALAR_CELL_CONTROL", "JAVA_HOST_PROXY", na, "REFERENCE", "",
                        Map.of("SNAPSHOT", na, "SCAN", na, "PACK", na, "KERNEL", inTotal, "MATERIALIZE", na),
                        List.of(new Series("TOTAL", () -> strictScalarMatmul(in[0], in[1], rows, inner, columns)))));
        for (PathPlan plan : plans) {
            if (pathSelector == null || pathSelector.equals(plan.path())) {
                ProtosI123CpuKernelCandidateSupport.measurePath(
                        rows + "x" + inner + "x" + columns, plan, MEASURED_BATCHES, MAX_RELATIVE_DISPERSION, telemetry);
            }
        }
    }

    /* ---- Protos helpers ---- */

    private static ProtosPrelude prelude() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(
                        Path.of("protos", "lib", "core"),
                        new ProtosStandardLibraryModuleResolver(Path.of("protos", "lib")));
    }

    private static ProtosMatrixValue matrix(
            ProtosPrelude prelude, ProtosActivation a, int rows, int columns, double[] values) {
        Object[] elements = new Object[values.length];
        for (int index = 0; index < values.length; index++) {
            elements[index] = new ProtosFloatValue(values[index]);
        }
        return generic(prelude, a, rows, columns, elements);
    }

    private static ProtosMatrixValue generic(
            ProtosPrelude prelude, ProtosActivation a, int rows, int columns, Object... values) {
        ProtosObjectValue matrixPrototype =
                ProtosStandardModuleMemberTestSupport.member(prelude, MODULE, "Matrix");
        List<Object> arguments = new ArrayList<>();
        arguments.add(new ProtosIntegerValue(rows));
        arguments.add(new ProtosIntegerValue(columns));
        arguments.addAll(Arrays.asList(values));
        return assertInstanceOf(
                ProtosMatrixValue.class,
                ProtosInvocation.invokeMessage(matrixPrototype, "call", arguments, a));
    }

    private static ProtosVectorValue vector(ProtosPrelude prelude, ProtosActivation a, double[] values) {
        ProtosObjectValue vectorPrototype =
                ProtosStandardModuleMemberTestSupport.member(prelude, MODULE, "Vector");
        List<Object> arguments = new ArrayList<>();
        for (double value : values) {
            arguments.add(new ProtosFloatValue(value));
        }
        return assertInstanceOf(
                ProtosVectorValue.class,
                ProtosInvocation.invokeMessage(vectorPrototype, "call", arguments, a));
    }

    private static ProtosVectorValue genericVector(
            ProtosPrelude prelude, ProtosActivation a, Object... values) {
        ProtosObjectValue vectorPrototype =
                ProtosStandardModuleMemberTestSupport.member(prelude, MODULE, "Vector");
        return assertInstanceOf(
                ProtosVectorValue.class,
                ProtosInvocation.invokeMessage(vectorPrototype, "call", Arrays.asList(values), a));
    }

    private static ProtosMatrixValue matmul(
            ProtosMatrixValue left, ProtosMatrixValue right, ProtosActivation a) {
        return assertInstanceOf(
                ProtosMatrixValue.class,
                ProtosInvocation.invokeMessage(left, "matmul", List.of(right), a));
    }

    private static double[] floats(List<Object> values) {
        double[] result = new double[values.size()];
        for (int index = 0; index < result.length; index++) {
            assertInstanceOf(ProtosFloatValue.class, values.get(index));
            result[index] = ProtosNumericValueSupport.currentFloatValue(values.get(index));
        }
        return result;
    }

    /* doubleToLongBits: canonical NaN, distinct signed zeros; zeros also checked by raw bits. */
    private static void assertBits(double[] expected, double[] actual) {
        assertEquals(expected.length, actual.length);
        for (int index = 0; index < expected.length; index++) {
            int cell = index;
            assertEquals(
                    Double.doubleToLongBits(expected[index]),
                    Double.doubleToLongBits(actual[index]),
                    () -> "cell " + cell + " expected " + expected[cell] + " got " + actual[cell]);
            if (expected[index] == 0.0d) {
                assertEquals(
                        Double.doubleToRawLongBits(expected[index]),
                        Double.doubleToRawLongBits(actual[index]),
                        () -> "signed zero at cell " + cell);
            }
        }
    }

    private static Object fraction(long numerator, long denominator, ProtosPrelude prelude) {
        return ProtosNumericValueSupport.divideIntegersExactly(
                new ProtosIntegerValue(numerator), new ProtosIntegerValue(denominator), prelude);
    }
}

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * I123 / PLAT068 standalone host-kernel diagnostic for D214-F1 Float-only matmul.
 *
 * <p>Compares a strict scalar cell kernel with a column-lane kernel that may expose SIMD across
 * independent output columns while keeping the ascending contraction order of every cell. Both
 * kernels keep multiplication and addition as separate binary64 operations. This is host timing
 * only: it never measures Protos and never proves that SIMD instructions were generated.
 *
 * <p>{@code --candidates} adds host proxies of the I123 candidates A1 (direct R1 Float carrier
 * reads) and T (temporary packed {@code double[]}) over {@code Object[]} element storage. Their
 * boxes are Java host proxies, NOT the {@code ProtosFloatValue} layout, so every candidate line is
 * labelled {@code TIMING_DOMAIN=JAVA_HOST_PROXY}; the real carriers are measured by
 * {@code ProtosI123CpuKernelEligibilityTest}.
 *
 * <p>Run: {@code java tools/diagnostics/Plat068CpuKernelProbe.java --check|--measure|--candidates}.
 * Nothing is written to the source tree.
 */
public final class Plat068CpuKernelProbe {
    static final long SEED = 680123L;
    static final int[][] CHECK_SHAPES = {{1, 1, 1}, {2, 3, 4}, {4, 7, 5}, {16, 32, 16}, {32, 64, 32}};
    static final int[][] MEASURE_SHAPES = {
        {1, 1, 1}, {4, 8, 4}, {16, 32, 16}, {32, 64, 32}, {64, 128, 64}, {96, 256, 96}
    };
    static final int[][] CANDIDATE_SHAPES = {
        {1, 1, 1}, {4, 7, 8}, {32, 64, 32}, {64, 65, 64}, {128, 256, 128}
    };
    static final int CHAIN_SIZE = 32;
    static final int CHAIN_OPERATIONS = 8;
    static final int WARMUP_BATCHES = 25;
    static final int MEASURED_BATCHES = 31;
    static final long TARGET_MULTIPLY_ADDS_PER_BATCH = 1L << 22;
    static final double MAX_RELATIVE_DISPERSION = 0.10d;

    private static volatile double sink;
    /* Every timed result escapes here; the volatile store is a cost shared by both kernels. */
    private static volatile Object escapedResult;

    private Plat068CpuKernelProbe() {}

    /** D214-F1 strict order: row, column, then ascending k seeded by the first product. */
    static double[] strictScalarMatmul(
            double[] left, double[] right, int rows, int inner, int columns) {
        requireShape(left, right, rows, inner, columns);
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

    /** Row, ascending k, then independent columns; every cell keeps its own ascending k order. */
    static double[] columnLaneMatmul(
            double[] left, double[] right, int rows, int inner, int columns) {
        requireShape(left, right, rows, inner, columns);
        double[] result = new double[rows * columns];
        for (int row = 0; row < rows; row++) {
            for (int k = 0; k < inner; k++) {
                double leftValue = left[row * inner + k];
                for (int column = 0; column < columns; column++) {
                    double product = leftValue * right[k * columns + column];
                    if (k == 0) {
                        result[row * columns + column] = product;
                    } else {
                        result[row * columns + column] = result[row * columns + column] + product;
                    }
                }
            }
        }
        return result;
    }

    /* A zero contraction is exact Integer 0 in Protos, which no double kernel can produce. */
    private static void requireShape(
            double[] left, double[] right, int rows, int inner, int columns) {
        if (inner <= 0) {
            throw new IllegalArgumentException("Float-only host kernels reject inner == 0");
        }
        if (rows < 0 || columns < 0
                || left.length != rows * inner || right.length != inner * columns) {
            throw new IllegalArgumentException("operand lengths do not match the shape");
        }
    }

    /** Bitwise equality by doubleToLongBits (signed zeros differ, NaNs canonical); -1 when equal. */
    static int checkEquivalent(double[] expected, double[] actual) {
        if (expected.length != actual.length) {
            return Math.min(expected.length, actual.length);
        }
        for (int index = 0; index < expected.length; index++) {
            if (Double.doubleToLongBits(expected[index])
                    != Double.doubleToLongBits(actual[index])) {
                return index;
            }
        }
        return -1;
    }

    /** Deterministic finite inputs: a fresh SplittableRandom(680123), left then right. */
    static double[][] inputs(int rows, int inner, int columns) {
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

    private record Fixture(
            String name, double[] left, double[] right, int rows, int inner, int columns,
            double[] expected) {}

    static Fixture[] fixtures() {
        double e = Math.scalb(1.0d, -27);
        return new Fixture[] {
            new Fixture("A", d(1e16, -1e16, 1), d(1, 1, 1), 1, 3, 1, d(1.0)),
            new Fixture("B", d(1e16, 1, -1e16), d(1, 1, 1), 1, 3, 1, d(0.0)),
            new Fixture("C", d(-0.0), d(1.0), 1, 1, 1, d(-0.0)),
            new Fixture("D", d(Double.MIN_VALUE), d(1.0), 1, 1, 1, d(Double.MIN_VALUE)),
            new Fixture("E", d(1 + e, 1), d(1 - e, -1), 1, 2, 1, d(0.0)),
            new Fixture("F", d(Double.POSITIVE_INFINITY), d(0.0), 1, 1, 1, d(Double.NaN)),
            new Fixture("G", d(1e16, -1e16, 1), d(1, 1, 1, 1, 1, 1), 1, 3, 2, d(1.0, 1.0)),
        };
    }

    private static double[] d(double... values) {
        return values;
    }

    /** Answers true only when every fixture and random shape agrees bit for bit. */
    static boolean runCorrectnessCases() {
        for (Fixture f : fixtures()) {
            double[] scalar = strictScalarMatmul(f.left(), f.right(), f.rows(), f.inner(), f.columns());
            double[] lanes = columnLaneMatmul(f.left(), f.right(), f.rows(), f.inner(), f.columns());
            int vsExpected = checkEquivalent(f.expected(), scalar);
            int vsLanes = checkEquivalent(scalar, lanes);
            boolean rawZero = true;
            for (int i = 0; i < f.expected().length; i++) {
                if (f.expected()[i] == 0.0d) {
                    rawZero &= Double.doubleToRawLongBits(f.expected()[i])
                                    == Double.doubleToRawLongBits(scalar[i])
                            && Double.doubleToRawLongBits(scalar[i])
                                    == Double.doubleToRawLongBits(lanes[i]);
                }
            }
            boolean ok = vsExpected < 0 && vsLanes < 0 && rawZero;
            System.out.println("I123_FIXTURE[" + f.name() + "]=" + (ok ? "EQUIVALENT" : "MISMATCH")
                    + " scalar=" + Arrays.toString(scalar) + " lanes=" + Arrays.toString(lanes));
            if (!ok) {
                return false;
            }
        }
        for (int[] shape : CHECK_SHAPES) {
            if (!shapeEquivalent(shape)) {
                return false;
            }
        }
        return true;
    }

    private static boolean shapeEquivalent(int[] shape) {
        double[][] in = inputs(shape[0], shape[1], shape[2]);
        int mismatch = checkEquivalent(
                strictScalarMatmul(in[0], in[1], shape[0], shape[1], shape[2]),
                columnLaneMatmul(in[0], in[1], shape[0], shape[1], shape[2]));
        System.out.println("I123_SHAPE_CHECK[" + shapeName(shape) + "]="
                + (mismatch < 0 ? "EQUIVALENT" : "MISMATCH at " + mismatch));
        return mismatch < 0;
    }

    /** Times both kernels per shape with alternating order; never run after a discrepancy. */
    static void runMeasurements(int[][] shapes) {
        for (int[] shape : shapes) {
            int rows = shape[0];
            int inner = shape[1];
            int columns = shape[2];
            double[][] in = inputs(rows, inner, columns);
            long work = (long) rows * inner * columns;
            int repetitions = (int) Math.max(1L, TARGET_MULTIPLY_ADDS_PER_BATCH / work);
            for (int batch = 0; batch < WARMUP_BATCHES; batch++) {
                timeBatch(true, in, rows, inner, columns, repetitions);
                timeBatch(false, in, rows, inner, columns, repetitions);
            }
            long[] scalar = new long[MEASURED_BATCHES];
            long[] lanes = new long[MEASURED_BATCHES];
            for (int batch = 0; batch < MEASURED_BATCHES; batch++) {
                if (batch % 2 == 0) {
                    scalar[batch] = timeBatch(true, in, rows, inner, columns, repetitions);
                    lanes[batch] = timeBatch(false, in, rows, inner, columns, repetitions);
                } else {
                    lanes[batch] = timeBatch(false, in, rows, inner, columns, repetitions);
                    scalar[batch] = timeBatch(true, in, rows, inner, columns, repetitions);
                }
            }
            String name = shapeName(shape);
            System.out.println("I123_SHAPE=" + name + " repetitions=" + repetitions
                    + " warmup=" + WARMUP_BATCHES + " samples=" + MEASURED_BATCHES);
            report("I123_SCALAR_KERNEL_NS", name, scalar, repetitions);
            report("I123_COLUMN_LANES_NS", name, lanes, repetitions);
        }
    }

    private static long timeBatch(
            boolean scalar, double[][] in, int rows, int inner, int columns, int repetitions) {
        double[] last = null;
        long start = System.nanoTime();
        for (int repetition = 0; repetition < repetitions; repetition++) {
            last = scalar
                    ? strictScalarMatmul(in[0], in[1], rows, inner, columns)
                    : columnLaneMatmul(in[0], in[1], rows, inner, columns);
            escapedResult = last;
        }
        long elapsed = System.nanoTime() - start;
        sink = sink + last[last.length - 1];
        return elapsed;
    }

    /** Prints raw batch samples, median/p10/p90 per operation and a stability verdict. */
    static void report(String label, String shape, long[] samples, int repetitions) {
        double[] perOperation = new double[samples.length];
        for (int i = 0; i < samples.length; i++) {
            perOperation[i] = samples[i] / (double) repetitions;
        }
        double[] sorted = perOperation.clone();
        Arrays.sort(sorted);
        double median = percentile(sorted, 0.50d);
        double p10 = percentile(sorted, 0.10d);
        double p90 = percentile(sorted, 0.90d);
        double dispersion = median > 0.0d ? (p90 - p10) / median : Double.POSITIVE_INFINITY;
        String stability = dispersion <= MAX_RELATIVE_DISPERSION ? "WITHIN_THRESHOLD" : "NOT_STABLE";
        System.out.println(String.format(Locale.ROOT,
                "%s[%s]=median=%.1f p10=%.1f p90=%.1f relDispersion=%.4f stability=%s rawBatchNs=%s",
                label, shape, median, p10, p90, dispersion, stability, Arrays.toString(samples)));
    }

    private static double percentile(double[] sorted, double fraction) {
        return sorted[(int) Math.round(fraction * (sorted.length - 1))];
    }

    /* ---- I123 A1 / T host proxies: TIMING_DOMAIN=JAVA_HOST_PROXY ---- */

    /** Boxed host proxy of a Float carrier; Java layout only, NOT {@code ProtosFloatValue}. */
    static final class ProxyFloat {
        final double value;

        ProxyFloat(double value) {
            this.value = value;
        }
    }

    /** Boxed host proxy of a non-Float Number; it exists only to fail the eligibility guard. */
    static final class ProxyOtherNumber {
        final long value;

        ProxyOtherNumber(long value) {
            this.value = value;
        }
    }

    /** Mirrors the R1 indexed snapshot: an independent copy of the element references. */
    static Object[] proxySnapshot(Object[] elements) {
        return elements.clone();
    }

    /** Eligibility scan: every element must be a Float proxy; nothing is converted. */
    static boolean proxyEligible(Object[] elements) {
        for (Object element : elements) {
            if (!(element instanceof ProxyFloat)) {
                return false;
            }
        }
        return true;
    }

    /* Shape first, then every element of both operands; an empty contraction is declined. */
    private static boolean proxyAdmits(
            Object[] left, Object[] right, int rows, int inner, int columns) {
        return inner > 0 && rows >= 0 && columns >= 0
                && left.length == rows * inner && right.length == inner * columns
                && proxyEligible(left) && proxyEligible(right);
    }

    static double[] packProxy(Object[] elements) {
        double[] packed = new double[elements.length];
        for (int index = 0; index < packed.length; index++) {
            packed[index] = ((ProxyFloat) elements[index]).value;
        }
        return packed;
    }

    /** Fresh proxy boxes for every cell. */
    static Object[] boxProxy(double[] values) {
        Object[] boxed = new Object[values.length];
        for (int index = 0; index < boxed.length; index++) {
            boxed[index] = new ProxyFloat(values[index]);
        }
        return boxed;
    }

    /** A1 proxy: snapshot, guard, then the carrier-reading kernel; null when declined. */
    static Object[] a1ProxyMatmul(Object[] left, Object[] right, int rows, int inner, int columns) {
        Object[] l = proxySnapshot(left);
        Object[] r = proxySnapshot(right);
        if (!proxyAdmits(l, r, rows, inner, columns)) {
            return null;
        }
        return a1ProxyKernel(l, r, rows, inner, columns);
    }

    /** Strict D214-F1 cell order reading the boxes directly; boxing is inline per cell. */
    static Object[] a1ProxyKernel(Object[] left, Object[] right, int rows, int inner, int columns) {
        Object[] cells = new Object[rows * columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                double accumulator =
                        ((ProxyFloat) left[row * inner]).value * ((ProxyFloat) right[column]).value;
                for (int k = 1; k < inner; k++) {
                    double product = ((ProxyFloat) left[row * inner + k]).value
                            * ((ProxyFloat) right[k * columns + column]).value;
                    accumulator = accumulator + product;
                }
                cells[row * columns + column] = new ProxyFloat(accumulator);
            }
        }
        return cells;
    }

    /** T proxy: snapshot, guard, temporary packing, column-lane kernel, fresh boxes. */
    static Object[] tProxyMatmul(Object[] left, Object[] right, int rows, int inner, int columns) {
        Object[] l = proxySnapshot(left);
        Object[] r = proxySnapshot(right);
        if (!proxyAdmits(l, r, rows, inner, columns)) {
            return null;
        }
        return boxProxy(columnLaneMatmul(packProxy(l), packProxy(r), rows, inner, columns));
    }

    private static boolean sharesIdentity(Object[] first, Object[] second) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.addAll(Arrays.asList(first));
        for (Object element : second) {
            if (seen.contains(element)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameReferences(Object[] expected, Object[] actual) {
        if (expected.length != actual.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (expected[index] != actual[index]) {
                return false;
            }
        }
        return true;
    }

    /* EQUIVALENT only when bits match, inputs are untouched and no cell aliases an input box. */
    private static String candidateCorrectness(
            Object[] result, double[] expected, Object[] left, Object[] leftBefore,
            Object[] right, Object[] rightBefore) {
        if (result == null) {
            return "DECLINED_UNEXPECTEDLY";
        }
        boolean ok = checkEquivalent(expected, packProxy(result)) < 0
                && sameReferences(leftBefore, left) && sameReferences(rightBefore, right)
                && !sharesIdentity(result, left) && !sharesIdentity(result, right);
        return ok ? "EQUIVALENT" : "MISMATCH";
    }

    private static boolean printCandidateCheck(String caseName, String path, String correctness) {
        System.out.println("I123_CANDIDATE_CHECK CASE=" + caseName + " PATH=" + path
                + " ELIGIBILITY=" + (correctness.equals("FALLBACK_REQUIRED") ? "DECLINED" : "ADMITTED")
                + " CORRECTNESS=" + correctness + " TIMING_DOMAIN=JAVA_HOST_PROXY");
        return correctness.equals("EQUIVALENT") || correctness.equals("FALLBACK_REQUIRED");
    }

    /** Answers true only when both proxies agree with the strict scalar kernel everywhere. */
    static boolean runCandidateCases() {
        boolean ok = true;
        for (Fixture f : fixtures()) {
            ok &= checkCandidatesOn("fixture_" + f.name(), f.left(), f.right(), f.rows(), f.inner(), f.columns());
        }
        for (int[] shape : CANDIDATE_SHAPES) {
            double[][] in = inputs(shape[0], shape[1], shape[2]);
            ok &= checkCandidatesOn(shapeName(shape), in[0], in[1], shape[0], shape[1], shape[2]);
        }

        double[][] in = inputs(4, 7, 8);
        Object[] heterogeneous = boxProxy(in[0]);
        heterogeneous[heterogeneous.length - 1] = new ProxyOtherNumber(1L);
        Object[] heterogeneousBefore = heterogeneous.clone();
        Object[] right = boxProxy(in[1]);
        for (String path : List.of("A1_R1_FLOAT_PROXY", "T_PACKED_PROXY")) {
            Object[] declined = path.startsWith("A1")
                    ? a1ProxyMatmul(heterogeneous, right, 4, 7, 8)
                    : tProxyMatmul(heterogeneous, right, 4, 7, 8);
            boolean guard = declined == null && sameReferences(heterogeneousBefore, heterogeneous);
            ok &= printCandidateCheck("heterogeneous_last_element", path,
                    guard ? "FALLBACK_REQUIRED" : "MISMATCH");
        }

        Object[] left = boxProxy(in[0]);
        double[] expected = strictScalarMatmul(in[0], in[1], 4, 7, 8);
        for (String path : List.of("A1_R1_FLOAT_PROXY", "T_PACKED_PROXY")) {
            boolean a1 = path.startsWith("A1");
            Object[] first = a1 ? a1ProxyMatmul(left, right, 4, 7, 8) : tProxyMatmul(left, right, 4, 7, 8);
            Object[] second = a1 ? a1ProxyMatmul(left, right, 4, 7, 8) : tProxyMatmul(left, right, 4, 7, 8);
            boolean repeated = first != null && second != null && first != second
                    && checkEquivalent(expected, packProxy(first)) < 0
                    && checkEquivalent(expected, packProxy(second)) < 0
                    && !sharesIdentity(first, second);
            ok &= printCandidateCheck("repeated_same_input", path, repeated ? "EQUIVALENT" : "MISMATCH");
        }

        ok &= checkChain("A1_R1_FLOAT_PROXY");
        ok &= checkChain("T_PACKED_PROXY");
        System.out.println("I123_CANDIDATE_CHECK CASE=empty_contraction ELIGIBILITY=DECLINED "
                + "EMPTY_CONTRACTION=PROTOS_INTEGER_ZERO HOST_DOUBLE_KERNEL=NOT_APPLICABLE");
        return ok;
    }

    private static boolean checkCandidatesOn(
            String caseName, double[] leftValues, double[] rightValues, int rows, int inner, int columns) {
        double[] expected = strictScalarMatmul(leftValues, rightValues, rows, inner, columns);
        Object[] left = boxProxy(leftValues);
        Object[] right = boxProxy(rightValues);
        Object[] leftBefore = left.clone();
        Object[] rightBefore = right.clone();
        boolean ok = printCandidateCheck(caseName, "A1_R1_FLOAT_PROXY", candidateCorrectness(
                a1ProxyMatmul(left, right, rows, inner, columns), expected, left, leftBefore, right, rightBefore));
        ok &= printCandidateCheck(caseName, "T_PACKED_PROXY", candidateCorrectness(
                tProxyMatmul(left, right, rows, inner, columns), expected, left, leftBefore, right, rightBefore));
        return ok;
    }

    /* Independent chained operations: every step must materialize fresh boxes. */
    private static boolean checkChain(String path) {
        double[][] in = inputs(CHAIN_SIZE, CHAIN_SIZE, CHAIN_SIZE);
        double[] expected = in[0];
        Object[] current = boxProxy(in[0]);
        Object[] right = boxProxy(in[1]);
        boolean ok = true;
        for (int operation = 0; operation < CHAIN_OPERATIONS && ok; operation++) {
            expected = strictScalarMatmul(expected, in[1], CHAIN_SIZE, CHAIN_SIZE, CHAIN_SIZE);
            Object[] next = chainStep(path, current, right);
            ok = next != null && checkEquivalent(expected, packProxy(next)) < 0
                    && !sharesIdentity(next, current) && !sharesIdentity(next, right);
            current = next;
        }
        return printCandidateCheck("independent_fresh_chain", path, ok ? "EQUIVALENT" : "MISMATCH");
    }

    private static Object[] chainStep(String path, Object[] left, Object[] right) {
        return path.startsWith("A1")
                ? a1ProxyMatmul(left, right, CHAIN_SIZE, CHAIN_SIZE, CHAIN_SIZE)
                : tProxyMatmul(left, right, CHAIN_SIZE, CHAIN_SIZE, CHAIN_SIZE);
    }

    private record Variant(String name, Runnable operation) {}

    /** Batch samples of every variant, interleaved in alternating order after shared warmup. */
    static long[][] measureVariants(List<Variant> variants, int repetitions) {
        int count = variants.size();
        long[][] samples = new long[count][MEASURED_BATCHES];
        for (int batch = 0; batch < WARMUP_BATCHES + MEASURED_BATCHES; batch++) {
            for (int step = 0; step < count; step++) {
                int variant = batch % 2 == 0 ? step : count - 1 - step;
                Runnable operation = variants.get(variant).operation();
                long start = System.nanoTime();
                for (int repetition = 0; repetition < repetitions; repetition++) {
                    operation.run();
                }
                long elapsed = System.nanoTime() - start;
                if (batch >= WARMUP_BATCHES) {
                    samples[variant][batch - WARMUP_BATCHES] = elapsed;
                }
            }
        }
        return samples;
    }

    /** Per-operation median and the existing (p90 - p10) / median dispersion criterion. */
    private record Summary(double medianNs, double dispersion) {
        boolean stable() {
            return dispersion <= MAX_RELATIVE_DISPERSION;
        }
    }

    private static Summary summarize(long[] samples, int repetitions) {
        double[] sorted = new double[samples.length];
        for (int i = 0; i < samples.length; i++) {
            sorted[i] = samples[i] / (double) repetitions;
        }
        Arrays.sort(sorted);
        double median = percentile(sorted, 0.50d);
        double dispersion = median > 0.0d
                ? (percentile(sorted, 0.90d) - percentile(sorted, 0.10d)) / median
                : Double.POSITIVE_INFINITY;
        return new Summary(median, dispersion);
    }

    private static String ns(Summary summary) {
        return String.format(Locale.ROOT, "%.1f", summary.medianNs());
    }

    /* One machine-readable line; DISPERSION is the worst measured series of the path. */
    private static void printCandidateLine(
            String caseName, String path, String eligibility, String correctness, String snapshot,
            String scan, String pack, String kernel, String materialize, String total,
            int repetitions, Summary... measured) {
        double worst = 0.0d;
        for (Summary summary : measured) {
            worst = Math.max(worst, summary.dispersion());
        }
        System.out.println(String.format(Locale.ROOT,
                "I123_CANDIDATE CASE=%s PATH=%s ELIGIBILITY=%s CORRECTNESS=%s SNAPSHOT_NS=%s SCAN_NS=%s"
                        + " PACK_NS=%s KERNEL_NS=%s MATERIALIZE_NS=%s TOTAL_NS=%s DISPERSION=%.4f"
                        + " STABILITY=%s REPETITIONS=%d SAMPLES=%d TIMING_DOMAIN=JAVA_HOST_PROXY"
                        + " SIMD=NOT_PROVEN PROXY_LAYOUT=JAVA_BOX_NOT_PROTOS_FLOAT_VALUE",
                caseName, path, eligibility, correctness, snapshot, scan, pack, kernel, materialize,
                total, worst, worst <= MAX_RELATIVE_DISPERSION ? "WITHIN_THRESHOLD" : "NOT_STABLE",
                repetitions, MEASURED_BATCHES));
    }

    /** Cost decomposition of A1 and T per shape on identical deterministic inputs. */
    static void runCandidateMeasurements() {
        for (int[] shape : CANDIDATE_SHAPES) {
            measureCandidateShape(shape[0], shape[1], shape[2]);
        }
        measureDeclinedGuard();
        measureChains();
    }

    private static void measureCandidateShape(int rows, int inner, int columns) {
        double[][] in = inputs(rows, inner, columns);
        Object[] left = boxProxy(in[0]);
        Object[] right = boxProxy(in[1]);
        Object[] leftSnapshot = proxySnapshot(left);
        Object[] rightSnapshot = proxySnapshot(right);
        double[] leftPacked = packProxy(leftSnapshot);
        double[] rightPacked = packProxy(rightSnapshot);
        double[] packedResult = columnLaneMatmul(leftPacked, rightPacked, rows, inner, columns);
        double[] expected = strictScalarMatmul(in[0], in[1], rows, inner, columns);
        String name = rows + "x" + inner + "x" + columns + "_repeated_same_input";
        String a1Correctness = candidateCorrectness(a1ProxyMatmul(left, right, rows, inner, columns),
                expected, left, leftSnapshot, right, rightSnapshot);
        String tCorrectness = candidateCorrectness(tProxyMatmul(left, right, rows, inner, columns),
                expected, left, leftSnapshot, right, rightSnapshot);
        if (!a1Correctness.equals("EQUIVALENT") || !tCorrectness.equals("EQUIVALENT")) {
            System.out.println("I123_CANDIDATE CASE=" + name + " CORRECTNESS=MISMATCH MEASUREMENT=REJECTED");
            return;
        }
        long work = (long) rows * inner * columns;
        int repetitions = (int) Math.max(1L, TARGET_MULTIPLY_ADDS_PER_BATCH / work);
        List<Variant> variants = new ArrayList<>();
        variants.add(new Variant("snapshot", () -> {
            escapedResult = proxySnapshot(left);
            escapedResult = proxySnapshot(right);
        }));
        variants.add(new Variant("scan",
                () -> escapedResult = proxyEligible(leftSnapshot) && proxyEligible(rightSnapshot)));
        variants.add(new Variant("pack", () -> {
            escapedResult = packProxy(leftSnapshot);
            escapedResult = packProxy(rightSnapshot);
        }));
        variants.add(new Variant("tKernel",
                () -> escapedResult = columnLaneMatmul(leftPacked, rightPacked, rows, inner, columns)));
        variants.add(new Variant("tMaterialize", () -> escapedResult = boxProxy(packedResult)));
        variants.add(new Variant("tTotal",
                () -> escapedResult = tProxyMatmul(left, right, rows, inner, columns)));
        variants.add(new Variant("a1KernelBoxing",
                () -> escapedResult = a1ProxyKernel(leftSnapshot, rightSnapshot, rows, inner, columns)));
        variants.add(new Variant("a1Total",
                () -> escapedResult = a1ProxyMatmul(left, right, rows, inner, columns)));
        variants.add(new Variant("scalarControl",
                () -> escapedResult = strictScalarMatmul(in[0], in[1], rows, inner, columns)));
        long[][] samples = measureVariants(variants, repetitions);
        Summary[] s = new Summary[samples.length];
        for (int i = 0; i < samples.length; i++) {
            s[i] = summarize(samples[i], repetitions);
        }
        printCandidateLine(name, "A1_R1_FLOAT_PROXY", "ADMITTED", a1Correctness, ns(s[0]), ns(s[1]),
                "NOT_APPLICABLE", ns(s[6]), "INCLUDED_IN_KERNEL", ns(s[7]), repetitions,
                s[0], s[1], s[6], s[7]);
        printCandidateLine(name, "T_PACKED_PROXY", "ADMITTED", tCorrectness, ns(s[0]), ns(s[1]),
                ns(s[2]), ns(s[3]), ns(s[4]), ns(s[5]), repetitions, s[0], s[1], s[2], s[3], s[4], s[5]);
        printCandidateLine(name, "SCALAR_CELL_CONTROL", "NOT_APPLICABLE", "REFERENCE", "NOT_APPLICABLE",
                "NOT_APPLICABLE", "NOT_APPLICABLE", ns(s[8]), "NOT_APPLICABLE", ns(s[8]), repetitions, s[8]);
        printCandidateLine(name, "COLUMN_LANE_CONTROL", "NOT_APPLICABLE", "EQUIVALENT", "NOT_APPLICABLE",
                "NOT_APPLICABLE", "NOT_APPLICABLE", ns(s[3]), "NOT_APPLICABLE", ns(s[3]), repetitions, s[3]);
    }

    /* The guard scans every element before declining; the last element is the ineligible one. */
    private static void measureDeclinedGuard() {
        int rows = 32;
        int inner = 64;
        int columns = 32;
        double[][] in = inputs(rows, inner, columns);
        Object[] left = boxProxy(in[0]);
        left[left.length - 1] = new ProxyOtherNumber(1L);
        Object[] right = boxProxy(in[1]);
        boolean declined = a1ProxyMatmul(left, right, rows, inner, columns) == null
                && tProxyMatmul(left, right, rows, inner, columns) == null;
        String name = "heterogeneous_last_element_32x64x32";
        if (!declined) {
            System.out.println("I123_CANDIDATE CASE=" + name + " CORRECTNESS=MISMATCH MEASUREMENT=REJECTED");
            return;
        }
        int repetitions = 4096;
        long[][] samples = measureVariants(List.of(
                new Variant("a1Declined",
                        () -> escapedResult = a1ProxyMatmul(left, right, rows, inner, columns)),
                new Variant("tDeclined",
                        () -> escapedResult = tProxyMatmul(left, right, rows, inner, columns))),
                repetitions);
        Summary a1 = summarize(samples[0], repetitions);
        Summary t = summarize(samples[1], repetitions);
        printCandidateLine(name, "A1_R1_FLOAT_PROXY", "DECLINED", "FALLBACK_REQUIRED", "INCLUDED_IN_TOTAL",
                "INCLUDED_IN_TOTAL", "NOT_APPLICABLE", "NOT_APPLICABLE", "NOT_APPLICABLE", ns(a1),
                repetitions, a1);
        printCandidateLine(name, "T_PACKED_PROXY", "DECLINED", "FALLBACK_REQUIRED", "INCLUDED_IN_TOTAL",
                "INCLUDED_IN_TOTAL", "NOT_APPLICABLE", "NOT_APPLICABLE", "NOT_APPLICABLE", ns(t),
                repetitions, t);
    }

    /* Independent chained operations: T repacks and reboxes at every step, A1 reboxes. */
    private static void measureChains() {
        double[][] in = inputs(CHAIN_SIZE, CHAIN_SIZE, CHAIN_SIZE);
        Object[] start = boxProxy(in[0]);
        Object[] right = boxProxy(in[1]);
        String name = "independent_fresh_chain_" + CHAIN_SIZE + "x" + CHAIN_SIZE + "x" + CHAIN_SIZE
                + "_ops" + CHAIN_OPERATIONS;
        int repetitions = (int) Math.max(1L, TARGET_MULTIPLY_ADDS_PER_BATCH
                / ((long) CHAIN_SIZE * CHAIN_SIZE * CHAIN_SIZE * CHAIN_OPERATIONS));
        long[][] samples = measureVariants(List.of(
                new Variant("a1Chain", () -> escapedResult = runChain("A1_R1_FLOAT_PROXY", start, right)),
                new Variant("tChain", () -> escapedResult = runChain("T_PACKED_PROXY", start, right))),
                repetitions);
        Summary a1 = summarize(samples[0], repetitions);
        Summary t = summarize(samples[1], repetitions);
        String a1Check = checkChain("A1_R1_FLOAT_PROXY") ? "EQUIVALENT" : "MISMATCH";
        String tCheck = checkChain("T_PACKED_PROXY") ? "EQUIVALENT" : "MISMATCH";
        printCandidateLine(name, "A1_R1_FLOAT_PROXY", "ADMITTED", a1Check, "INCLUDED_IN_TOTAL",
                "INCLUDED_IN_TOTAL", "NOT_APPLICABLE", "INCLUDED_IN_TOTAL", "INCLUDED_IN_TOTAL", ns(a1),
                repetitions, a1);
        printCandidateLine(name, "T_PACKED_PROXY", "ADMITTED", tCheck, "INCLUDED_IN_TOTAL",
                "INCLUDED_IN_TOTAL", "INCLUDED_IN_TOTAL", "INCLUDED_IN_TOTAL", "INCLUDED_IN_TOTAL", ns(t),
                repetitions, t);
    }

    private static Object[] runChain(String path, Object[] start, Object[] right) {
        Object[] current = start;
        for (int operation = 0; operation < CHAIN_OPERATIONS; operation++) {
            current = chainStep(path, current, right);
        }
        return current;
    }

    private static String shapeName(int[] shape) {
        return shape[0] + "x" + shape[1] + "x" + shape[2];
    }

    public static void main(String[] args) {
        if (args.length != 1 || !(args[0].equals("--check") || args[0].equals("--measure")
                || args[0].equals("--candidates"))) {
            System.err.println("usage: Plat068CpuKernelProbe --check|--measure|--candidates");
            System.exit(2);
        }
        System.out.println("I123_JAVA=" + Runtime.version());
        System.out.println("I123_VM=" + System.getProperty("java.vm.name"));
        System.out.println("I123_OS=" + System.getProperty("os.name"));
        System.out.println("I123_ARCH=" + System.getProperty("os.arch"));
        System.out.println("I123_PROCESSORS=" + Runtime.getRuntime().availableProcessors());
        System.out.println("I123_VECTORIZE_LOOPS_FLAG="
                + System.getProperty("jdk.graal.VectorizeLoops", "UNSET"));
        System.out.println("I123_MEASUREMENT_CLASS=HOST_KERNEL_ONLY");
        System.out.println("I123_SIMD=NOT_PROVEN");
        boolean correct = runCorrectnessCases() & runCandidateCases();
        System.out.println("I123_CORRECTNESS=" + (correct ? "EQUIVALENT" : "MISMATCH"));
        if (!correct) {
            System.exit(1);
        }
        if (args[0].equals("--measure")) {
            for (int[] shape : MEASURE_SHAPES) {
                if (!shapeEquivalent(shape)) {
                    System.exit(1);
                }
            }
            runMeasurements(MEASURE_SHAPES);
            System.out.println("I123_SINK=" + sink);
        }
        if (args[0].equals("--candidates")) {
            runCandidateMeasurements();
            System.out.println("I123_CANDIDATE_RESULT_CLASS=JAVA_HOST_PROXY_DIAGNOSTIC_ONLY");
        }
    }
}

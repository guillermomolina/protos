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

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * I123 / PLAT068 standalone host-Java diagnostic comparing CPU numeric policies for dense
 * row-major {@code double[]} matrix products.
 *
 * <p>{@code strictCells} is the D214-F1 reference for non-empty Float matrices.
 * {@code strictColumnLanes} and {@code strictBlockedColumns} only reorganize loops and must stay
 * bit-identical to it. {@code fmaCells}, {@code fastrStyleBlocked} and
 * {@code dualAccumulatorCells} are deliberately nonconforming experiments whose divergence is the
 * information being collected. {@code fastrStyleBlocked} only models the block-partial
 * association observed in FastR; it is not FastR.
 *
 * <p>This is host computation only: it never executes the Protos runtime, never proves SIMD
 * generation, and its timings are never Protos speedups. Empty contractions are a Protos Integer
 * {@code 0} and are out of scope for {@code double[]} kernels.
 *
 * <p>Run: {@code java tools/diagnostics/Plat068NumericPolicyProbe.java [--witnesses|--measure]}.
 * Without arguments both sections run. Output is {@code KEY=VALUE} tokens.
 */
public final class Plat068NumericPolicyProbe {

    static final int COLUMN_BLOCK = 64;
    static final int K_BLOCK = 64;

    private static final int WARMUP_BATCHES = 5;
    private static final int MEASURED_BATCHES = 15;
    private static final long TARGET_BATCH_NS = 5_000_000L;
    private static final double STABLE_DISPERSION = 0.10;
    private static final int CHAIN_OPERATIONS = 8;
    private static final int[] CHAIN_SIZES = {32, 64, 128};
    private static final int[][] SHAPES = {{1, 1, 1}, {4, 7, 8}, {32, 64, 32}, {64, 65, 64}, {128, 256, 128}};

    /** Full published results; primitive and reference results are kept apart. */
    static volatile double[] primitiveSink;
    static volatile FloatBox[] referenceSink;

    private Plat068NumericPolicyProbe() {
    }

    public static void main(String[] args) {
        boolean witnesses = args.length == 0 || Arrays.asList(args).contains("--witnesses");
        boolean measure = args.length == 0 || Arrays.asList(args).contains("--measure");
        printMetadata();
        if (witnesses) {
            runNumericWitnesses();
        }
        if (measure) {
            runKernelMeasurements();
            runStorageMeasurements();
        }
    }

    // ---------------------------------------------------------------- kernels

    @FunctionalInterface
    interface KernelFn {
        double[] apply(double[] a, double[] b, int m, int k, int n);
    }

    record KernelSpec(String name, String conformance, boolean structural, KernelFn fn) {
    }

    static final List<KernelSpec> KERNELS = List.of(
                    new KernelSpec("strictCells", "D214_F1_REFERENCE", true, Plat068NumericPolicyProbe::strictCells),
                    new KernelSpec("strictColumnLanes", "STRICT_LOOP_REORGANIZATION", true, Plat068NumericPolicyProbe::strictColumnLanes),
                    new KernelSpec("strictBlockedColumns", "STRICT_LOOP_REORGANIZATION", true, Plat068NumericPolicyProbe::strictBlockedColumns),
                    new KernelSpec("fmaCells", "EXPERIMENTAL_FMA_NONCONFORMING", false, Plat068NumericPolicyProbe::fmaCells),
                    new KernelSpec("fastrStyleBlocked", "EXPERIMENTAL_BLOCK_REDUCTION_NONCONFORMING", false, Plat068NumericPolicyProbe::fastrStyleBlocked),
                    new KernelSpec("dualAccumulatorCells", "EXPERIMENTAL_REASSOCIATED_NONCONFORMING", false, Plat068NumericPolicyProbe::dualAccumulatorCells));

    private static void requireNonEmpty(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("EMPTY_CONTRACTION=PROTOS_INTEGER_ZERO HOST_DOUBLE_KERNEL=NOT_APPLICABLE");
        }
    }

    /** D214-F1 reference: row, column, contraction ascending; first product seeds the sum. */
    static double[] strictCells(double[] a, double[] b, int m, int k, int n) {
        requireNonEmpty(k);
        double[] c = new double[m * n];
        for (int i = 0; i < m; i++) {
            int row = i * k;
            for (int j = 0; j < n; j++) {
                double acc = a[row] * b[j];
                for (int p = 1; p < k; p++) {
                    acc = acc + a[row + p] * b[p * n + j];
                }
                c[i * n + j] = acc;
            }
        }
        return c;
    }

    /** Same per-cell operation sequence as strictCells, iterating independent columns innermost. */
    static double[] strictColumnLanes(double[] a, double[] b, int m, int k, int n) {
        requireNonEmpty(k);
        double[] c = new double[m * n];
        for (int i = 0; i < m; i++) {
            int out = i * n;
            double a0 = a[i * k];
            for (int j = 0; j < n; j++) {
                c[out + j] = a0 * b[j];
            }
            for (int p = 1; p < k; p++) {
                double aip = a[i * k + p];
                int bRow = p * n;
                for (int j = 0; j < n; j++) {
                    c[out + j] = c[out + j] + aip * b[bRow + j];
                }
            }
        }
        return c;
    }

    /** Column blocks of COLUMN_BLOCK; full ascending contraction inside each block. */
    static double[] strictBlockedColumns(double[] a, double[] b, int m, int k, int n) {
        requireNonEmpty(k);
        double[] c = new double[m * n];
        for (int i = 0; i < m; i++) {
            int out = i * n;
            for (int j0 = 0; j0 < n; j0 += COLUMN_BLOCK) {
                int jEnd = Math.min(n, j0 + COLUMN_BLOCK);
                double a0 = a[i * k];
                for (int j = j0; j < jEnd; j++) {
                    c[out + j] = a0 * b[j];
                }
                for (int p = 1; p < k; p++) {
                    double aip = a[i * k + p];
                    int bRow = p * n;
                    for (int j = j0; j < jEnd; j++) {
                        c[out + j] = c[out + j] + aip * b[bRow + j];
                    }
                }
            }
        }
        return c;
    }

    /** EXPERIMENTAL_FMA_NONCONFORMING: ascending k, fused multiply-add after the first product. */
    static double[] fmaCells(double[] a, double[] b, int m, int k, int n) {
        requireNonEmpty(k);
        double[] c = new double[m * n];
        for (int i = 0; i < m; i++) {
            int row = i * k;
            for (int j = 0; j < n; j++) {
                double acc = a[row] * b[j];
                for (int p = 1; p < k; p++) {
                    acc = Math.fma(a[row + p], b[p * n + j], acc);
                }
                c[i * n + j] = acc;
            }
        }
        return c;
    }

    /** EXPERIMENTAL_BLOCK_REDUCTION_NONCONFORMING: +0.0-seeded partial per K_BLOCK, then added. */
    static double[] fastrStyleBlocked(double[] a, double[] b, int m, int k, int n) {
        requireNonEmpty(k);
        double[] c = new double[m * n];
        for (int i = 0; i < m; i++) {
            int row = i * k;
            for (int j = 0; j < n; j++) {
                double result = 0.0;
                for (int p0 = 0; p0 < k; p0 += K_BLOCK) {
                    int pEnd = Math.min(k, p0 + K_BLOCK);
                    double partial = 0.0;
                    for (int p = p0; p < pEnd; p++) {
                        partial = partial + a[row + p] * b[p * n + j];
                    }
                    result = result + partial;
                }
                c[i * n + j] = result;
            }
        }
        return c;
    }

    /** EXPERIMENTAL_REASSOCIATED_NONCONFORMING: even/odd contraction indices, combined at the end. */
    static double[] dualAccumulatorCells(double[] a, double[] b, int m, int k, int n) {
        requireNonEmpty(k);
        double[] c = new double[m * n];
        for (int i = 0; i < m; i++) {
            int row = i * k;
            for (int j = 0; j < n; j++) {
                double even = a[row] * b[j];
                if (k == 1) {
                    c[i * n + j] = even;
                    continue;
                }
                double odd = a[row + 1] * b[n + j];
                for (int p = 2; p < k; p++) {
                    double product = a[row + p] * b[p * n + j];
                    if ((p & 1) == 0) {
                        even = even + product;
                    } else {
                        odd = odd + product;
                    }
                }
                c[i * n + j] = even + odd;
            }
        }
        return c;
    }

    // --------------------------------------------------- high-precision reference

    static boolean allFinite(double[] values) {
        for (double v : values) {
            if (!Double.isFinite(v)) {
                return false;
            }
        }
        return true;
    }

    /** Exact decimal products and sums, one rounding per cell; finite operands only, never timed. */
    static double[] highPrecisionReference(double[] a, double[] b, int m, int k, int n) {
        requireNonEmpty(k);
        if (!allFinite(a) || !allFinite(b)) {
            return null;
        }
        BigDecimal[] exactA = exact(a);
        BigDecimal[] exactB = exact(b);
        double[] c = new double[m * n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                BigDecimal sum = BigDecimal.ZERO;
                for (int p = 0; p < k; p++) {
                    sum = sum.add(exactA[i * k + p].multiply(exactB[p * n + j]));
                }
                c[i * n + j] = sum.doubleValue();
            }
        }
        return c;
    }

    private static BigDecimal[] exact(double[] values) {
        BigDecimal[] result = new BigDecimal[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = new BigDecimal(values[i]);
        }
        return result;
    }

    // ------------------------------------------------------------- comparison

    record Comparison(int differingCells, int firstIndex, long referenceBits, long resultBits, int signedZeroDifferences,
                    int classificationDifferences, double maxFiniteAbsoluteError, BigInteger maxFiniteUlpError) {

        boolean bitExact() {
            return differingCells == 0;
        }

        String tokens(String prefix) {
            StringBuilder sb = new StringBuilder();
            sb.append(prefix).append("BIT_EXACT=").append(bitExact() ? "YES" : "NO");
            sb.append(' ').append(prefix).append("DIFFERING_CELLS=").append(differingCells);
            if (firstIndex >= 0) {
                sb.append(' ').append(prefix).append("FIRST_DIFFERENCE_INDEX=").append(firstIndex);
                sb.append(' ').append(prefix).append("REFERENCE_BITS=").append(hex(referenceBits));
                sb.append(' ').append(prefix).append("REFERENCE_CLASS=").append(classify(Double.longBitsToDouble(referenceBits)));
                sb.append(' ').append(prefix).append("RESULT_BITS=").append(hex(resultBits));
                sb.append(' ').append(prefix).append("RESULT_CLASS=").append(classify(Double.longBitsToDouble(resultBits)));
            }
            sb.append(' ').append(prefix).append("SIGNED_ZERO_DIFFERENCES=").append(signedZeroDifferences);
            sb.append(' ').append(prefix).append("NONFINITE_CLASSIFICATION_DIFFERENCES=").append(classificationDifferences);
            sb.append(' ').append(prefix).append("MAX_FINITE_ABSOLUTE_ERROR=").append(fmt(maxFiniteAbsoluteError));
            sb.append(' ').append(prefix).append("MAX_FINITE_ULP_ERROR=").append(maxFiniteUlpError);
            return sb.toString();
        }
    }

    static String classify(double v) {
        if (Double.isNaN(v)) {
            return "NAN";
        }
        if (Double.isInfinite(v)) {
            return v > 0 ? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY";
        }
        if (v == 0.0) {
            return Double.doubleToRawLongBits(v) == 0L ? "POSITIVE_ZERO" : "NEGATIVE_ZERO";
        }
        return Math.abs(v) < Double.MIN_NORMAL ? "SUBNORMAL" : "NORMAL";
    }

    private static int category(double v) {
        return Double.isNaN(v) ? 2 : Double.isInfinite(v) ? 1 : 0;
    }

    private static long ordered(double v) {
        long bits = Double.doubleToRawLongBits(v);
        return bits < 0 ? Long.MIN_VALUE - bits : bits;
    }

    /** Exact raw-bit comparison; error magnitudes are informative and never imply acceptance. */
    static Comparison compareResults(double[] reference, double[] result) {
        int differing = 0;
        int first = -1;
        int signedZero = 0;
        int classification = 0;
        double maxAbs = 0.0;
        BigInteger maxUlp = BigInteger.ZERO;
        for (int i = 0; i < reference.length; i++) {
            long rb = Double.doubleToRawLongBits(reference[i]);
            long xb = Double.doubleToRawLongBits(result[i]);
            if (rb == xb) {
                continue;
            }
            differing++;
            if (first < 0) {
                first = i;
            }
            double r = reference[i];
            double x = result[i];
            if (r == 0.0 && x == 0.0) {
                signedZero++;
            }
            if (category(r) != category(x)) {
                classification++;
            }
            if (Double.isFinite(r) && Double.isFinite(x)) {
                maxAbs = Math.max(maxAbs, Math.abs(x - r));
                BigInteger ulp = BigInteger.valueOf(ordered(x)).subtract(BigInteger.valueOf(ordered(r))).abs();
                maxUlp = maxUlp.max(ulp);
            }
        }
        long firstRef = first < 0 ? 0L : Double.doubleToRawLongBits(reference[first]);
        long firstRes = first < 0 ? 0L : Double.doubleToRawLongBits(result[first]);
        return new Comparison(differing, first, firstRef, firstRes, signedZero, classification, maxAbs, maxUlp);
    }

    // -------------------------------------------------------- numeric witnesses

    record Witness(String name, double[] a, double[] b, int m, int k, int n) {
    }

    static void runNumericWitnesses() {
        for (Witness w : witnesses()) {
            reportWitness(w);
        }
        System.out.println("SECTION=WITNESS CASE=empty_contraction SHAPE=2x0x3 EMPTY_CONTRACTION=PROTOS_INTEGER_ZERO "
                        + "HOST_DOUBLE_KERNEL=NOT_APPLICABLE MEASUREMENT=EXCLUDED");
        runNumericFamilyWitnesses();
    }

    /**
     * Families no Float-only {@code double[]} kernel may serve. The coerced host values are
     * explanatory only: they show what a Float shortcut would change, never the Protos result,
     * which stays owned by canonical Number arithmetic on the public protocol.
     */
    static void runNumericFamilyWitnesses() {
        String prefix = "SECTION=FAMILY_WITNESS CASE=";
        String fallback = " HOST_DOUBLE_KERNEL=NOT_APPLICABLE PROTOS_PATH=CANONICAL_FALLBACK";

        BigInteger big = BigInteger.ONE.shiftLeft(63).add(BigInteger.ONE);
        double bigCoerced = big.doubleValue() * 1.0;
        System.out.println(prefix + "big_integer_product SHAPE=1x1x1 FAMILY=BIG_INTEGER EXACT_RESULT=" + big
                        + " COERCED_DOUBLE_BITS=" + hex(Double.doubleToRawLongBits(bigCoerced))
                        + " COERCION_CHANGES_VALUE=" + yesNo(new BigDecimal(bigCoerced).compareTo(new BigDecimal(big)) != 0)
                        + fallback);

        double tenth = 1.0 / 10.0;
        double fractionCoerced = tenth * 1.0;
        for (int p = 1; p < 10; p++) {
            fractionCoerced = fractionCoerced + tenth * 1.0;
        }
        System.out.println(prefix + "fraction_tenths SHAPE=1x10x1 FAMILY=FRACTION EXACT_RATIONAL_RESULT=1"
                        + " COERCED_DOUBLE_BITS=" + hex(Double.doubleToRawLongBits(fractionCoerced))
                        + " COERCION_CHANGES_VALUE=" + yesNo(fractionCoerced != 1.0) + fallback);

        BigInteger beyond = BigInteger.ONE.shiftLeft(53).add(BigInteger.ONE);
        System.out.println(prefix + "mixed_integer_float SHAPE=1x2x1 FAMILY=MIXED_INTEGER_FLOAT"
                        + " ELIGIBILITY=REJECTED_HETEROGENEOUS INTEGER_ELEMENT=" + beyond
                        + " INTEGER_ELEMENT_DOUBLE_EXACT=" + yesNo(new BigDecimal(beyond.doubleValue()).compareTo(new BigDecimal(beyond)) == 0)
                        + " PROTOS_RESULT=OWNED_BY_CANONICAL_NUMBER_ARITHMETIC" + fallback);

        // Bilinear product (1+e + i)(1-e + i): the real coordinate (1+e)(1-e) - 1 = -2^-54 exactly.
        double e = Math.scalb(1.0, -27);
        double a = 1.0 + e;
        double b = 1.0;
        double c = 1.0 - e;
        double d = 1.0;
        double onceRounded = new BigDecimal(a).multiply(new BigDecimal(c))
                        .subtract(new BigDecimal(b).multiply(new BigDecimal(d))).doubleValue();
        double separatelyRounded = a * c - b * d;
        System.out.println(prefix + "complex_finite_coordinate SHAPE=1x1x1 FAMILY=COMPLEX PRODUCT=BILINEAR"
                        + " ONCE_ROUNDED_REAL_BITS=" + hex(Double.doubleToRawLongBits(onceRounded))
                        + " SEPARATELY_ROUNDED_REAL_BITS=" + hex(Double.doubleToRawLongBits(separatelyRounded))
                        + " SEPARATE_ROUNDING_DIVERGES=" + yesNo(Double.doubleToRawLongBits(onceRounded) != Double.doubleToRawLongBits(separatelyRounded))
                        + " NORMATIVE_RULE=STANDARD_COMPLEX_ARITHMETIC_R DOT=HERMITIAN_CONJUGATES_LEFT"
                        + " PRIMITIVE_DOUBLE_KERNEL=NOT_APPLICABLE" + fallback);
    }

    private static String yesNo(boolean value) {
        return value ? "YES" : "NO";
    }

    private static List<Witness> witnesses() {
        double e = Math.scalb(1.0, -27);
        double inf = Double.POSITIVE_INFINITY;
        double nan = Double.NaN;
        List<Witness> list = new ArrayList<>();
        list.add(new Witness("negative_zero_single_product", new double[]{-1.0}, new double[]{0.0}, 1, 1, 1));
        list.add(new Witness("negative_zero_sum", new double[]{-1.0, -1.0}, new double[]{0.0, 0.0}, 1, 2, 1));
        list.add(new Witness("cancellation_1e16", new double[]{1e16, -1e16, 1.0}, new double[]{1.0, 1.0, 1.0}, 1, 3, 1));
        list.add(new Witness("fma_divergence", new double[]{-1.0, 1.0 + e}, new double[]{1.0, 1.0 - e}, 1, 2, 1));
        list.add(new Witness("min_value", new double[]{Double.MIN_VALUE, Double.MIN_VALUE}, new double[]{1.0, -0.5}, 1, 2, 1));
        list.add(new Witness("min_normal", new double[]{Double.MIN_NORMAL, Double.MIN_NORMAL}, new double[]{0.5, 0.25}, 1, 2, 1));
        list.add(new Witness("overflow_to_infinity", new double[]{Double.MAX_VALUE, Double.MAX_VALUE}, new double[]{1.0, 1.0}, 1, 2, 1));
        list.add(new Witness("zero_times_infinity", new double[]{0.0}, new double[]{inf}, 1, 1, 1));
        list.add(new Witness("nan_left_operand", new double[]{nan, 1.0}, new double[]{1.0, 1.0}, 1, 2, 1));
        list.add(new Witness("nan_right_operand", new double[]{1.0, 1.0}, new double[]{1.0, nan}, 1, 2, 1));
        list.add(new Witness("negative_infinity", new double[]{-inf, 1.0}, new double[]{1.0, 1.0}, 1, 2, 1));
        list.add(new Witness("opposite_infinities", new double[]{inf, inf}, new double[]{1.0, -1.0}, 1, 2, 1));
        for (int k : new int[]{63, 64, 65, 127, 128}) {
            list.add(contractionWitness(k));
        }
        return list;
    }

    /** Mixed-magnitude 2 x k x 3 operands so block and pairing boundaries can change rounding. */
    private static Witness contractionWitness(int k) {
        SplittableRandom random = new SplittableRandom(0x9068_0000L + k);
        double[] a = new double[2 * k];
        double[] b = new double[k * 3];
        for (int i = 0; i < a.length; i++) {
            a[i] = Math.scalb(random.nextDouble(-1.0, 1.0), random.nextInt(-20, 21));
        }
        for (int i = 0; i < b.length; i++) {
            b[i] = Math.scalb(random.nextDouble(-1.0, 1.0), random.nextInt(-20, 21));
        }
        return new Witness("contraction_length_" + k, a, b, 2, k, 3);
    }

    private static void reportWitness(Witness w) {
        double[] f1 = strictCells(w.a(), w.b(), w.m(), w.k(), w.n());
        double[] hp = highPrecisionReference(w.a(), w.b(), w.m(), w.k(), w.n());
        boolean structuralFailure = false;
        for (KernelSpec spec : KERNELS) {
            double[] result = spec.fn().apply(w.a(), w.b(), w.m(), w.k(), w.n());
            Comparison vsF1 = compareResults(f1, result);
            StringBuilder line = new StringBuilder("SECTION=WITNESS CASE=").append(w.name());
            line.append(" SHAPE=").append(shape(w.m(), w.k(), w.n()));
            line.append(" KERNEL=").append(spec.name()).append(" CONFORMANCE_CLASS=").append(spec.conformance());
            line.append(" CELL0_CLASS=").append(classify(result[0])).append(" CELL0_BITS=").append(hex(Double.doubleToRawLongBits(result[0])));
            line.append(' ').append(f1Tokens(vsF1));
            line.append(' ').append(highPrecisionTokens(hp, result));
            if (spec.structural() && !vsF1.bitExact()) {
                structuralFailure = true;
                line.append(" STRUCTURAL_CONFORMANCE=FAIL");
            } else if (spec.structural()) {
                line.append(" STRUCTURAL_CONFORMANCE=PASS");
            }
            System.out.println(line);
        }
        System.out.println("SECTION=WITNESS CASE=" + w.name() + " STRUCTURAL_CONFORMANCE=" + (structuralFailure ? "FAIL MEASUREMENT=REJECTED" : "PASS"));
    }

    private static String f1Tokens(Comparison c) {
        return c.tokens("F1_").replace("F1_DIFFERING_CELLS=", "DIFFERING_CELLS=")
                        .replace("F1_SIGNED_ZERO_DIFFERENCES=", "SIGNED_ZERO_DIFFERENCES=")
                        .replace("F1_NONFINITE_CLASSIFICATION_DIFFERENCES=", "NONFINITE_CLASSIFICATION_DIFFERENCES=")
                        .replace("F1_MAX_FINITE_ABSOLUTE_ERROR=", "MAX_FINITE_ABSOLUTE_ERROR=")
                        .replace("F1_MAX_FINITE_ULP_ERROR=", "MAX_FINITE_ULP_ERROR=");
    }

    private static String highPrecisionTokens(double[] hp, double[] result) {
        if (hp == null) {
            return "HIGH_PRECISION_REFERENCE=NOT_APPLICABLE";
        }
        return "HIGH_PRECISION_REFERENCE=APPLICABLE " + compareResults(hp, result).tokens("HP_");
    }

    // -------------------------------------------------------- measurement core

    record Measurement(long medianWallNs, long medianCpuNs, double relDispersion, int gcAffectedBatches, int repsPerBatch) {

        boolean stable() {
            return relDispersion <= STABLE_DISPERSION;
        }

        String tokens() {
            return "REPS_PER_BATCH=" + repsPerBatch + " BATCHES=" + MEASURED_BATCHES + " MEDIAN_WALL_NS=" + medianWallNs
                            + " MEDIAN_CPU_NS=" + (medianCpuNs < 0 ? "NOT_AVAILABLE" : Long.toString(medianCpuNs))
                            + " REL_DISPERSION=" + fmt(relDispersion) + " GC_AFFECTED_BATCHES=" + gcAffectedBatches
                            + " STABILITY=" + (stable() ? "STABLE" : "NOT_STABLE");
        }
    }

    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final boolean CPU_TIME = enableCpuTime();

    private static boolean enableCpuTime() {
        if (!THREADS.isCurrentThreadCpuTimeSupported()) {
            return false;
        }
        if (!THREADS.isThreadCpuTimeEnabled()) {
            THREADS.setThreadCpuTimeEnabled(true);
        }
        return true;
    }

    private static long gcCount() {
        long total = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0L, gc.getCollectionCount());
        }
        return total;
    }

    private static int calibrate(Runnable op) {
        int reps = 1;
        while (true) {
            long start = System.nanoTime();
            for (int r = 0; r < reps; r++) {
                op.run();
            }
            if (System.nanoTime() - start >= TARGET_BATCH_NS || reps >= (1 << 24)) {
                return reps;
            }
            reps *= 2;
        }
    }

    /** Per-operation medians over measured batches; dispersion is median absolute deviation / median. */
    static Measurement measure(Runnable op) {
        int reps = calibrate(op);
        for (int w = 0; w < WARMUP_BATCHES; w++) {
            for (int r = 0; r < reps; r++) {
                op.run();
            }
        }
        long[] wall = new long[MEASURED_BATCHES];
        long[] cpu = new long[MEASURED_BATCHES];
        int gcAffected = 0;
        for (int batch = 0; batch < MEASURED_BATCHES; batch++) {
            long gcBefore = gcCount();
            long cpuStart = CPU_TIME ? THREADS.getCurrentThreadCpuTime() : 0L;
            long start = System.nanoTime();
            for (int r = 0; r < reps; r++) {
                op.run();
            }
            wall[batch] = (System.nanoTime() - start) / reps;
            cpu[batch] = CPU_TIME ? (THREADS.getCurrentThreadCpuTime() - cpuStart) / reps : -1L;
            if (gcCount() != gcBefore) {
                gcAffected++;
            }
        }
        long medianWall = median(wall);
        long[] deviation = new long[MEASURED_BATCHES];
        for (int i = 0; i < MEASURED_BATCHES; i++) {
            deviation[i] = Math.abs(wall[i] - medianWall);
        }
        double dispersion = medianWall == 0 ? 0.0 : (double) median(deviation) / medianWall;
        return new Measurement(medianWall, CPU_TIME ? median(cpu) : -1L, dispersion, gcAffected, reps);
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    }

    // ---------------------------------------------------- kernel measurements

    static void runKernelMeasurements() {
        for (int[] s : SHAPES) {
            measureShape(s[0], s[1], s[2]);
        }
        System.out.println("SECTION=KERNEL_MEASUREMENT MEASUREMENT_DOMAIN=HOST_PRIMITIVE_KERNEL SHAPE=4x0x8 "
                        + "EMPTY_CONTRACTION=PROTOS_INTEGER_ZERO HOST_DOUBLE_KERNEL=NOT_APPLICABLE MEASUREMENT=EXCLUDED");
    }

    private static void measureShape(int m, int k, int n) {
        SplittableRandom random = new SplittableRandom(0x9068_1000L + m * 31L + k * 7L + n);
        double[] a = boundedMatrix(random, m * k);
        double[] b = boundedMatrix(random, k * n);
        double[] f1 = strictCells(a, b, m, k, n);
        double[] hp = highPrecisionReference(a, b, m, k, n);
        for (KernelSpec spec : KERNELS) {
            Comparison vsF1 = compareResults(f1, spec.fn().apply(a, b, m, k, n));
            StringBuilder line = new StringBuilder("SECTION=KERNEL_MEASUREMENT MEASUREMENT_DOMAIN=HOST_PRIMITIVE_KERNEL");
            line.append(" SHAPE=").append(shape(m, k, n)).append(" KERNEL=").append(spec.name());
            line.append(" CONFORMANCE_CLASS=").append(spec.conformance());
            line.append(' ').append(f1Tokens(vsF1)).append(' ').append(highPrecisionTokens(hp, spec.fn().apply(a, b, m, k, n)));
            if (spec.structural() && !vsF1.bitExact()) {
                line.append(" STRUCTURAL_CONFORMANCE=FAIL MEASUREMENT=REJECTED");
            } else {
                KernelFn fn = spec.fn();
                line.append(' ').append(measure(() -> primitiveSink = fn.apply(a, b, m, k, n)).tokens());
            }
            System.out.println(line);
        }
    }

    private static double[] boundedMatrix(SplittableRandom random, int length) {
        double[] values = new double[length];
        for (int i = 0; i < length; i++) {
            values[i] = random.nextDouble(-1.0, 1.0);
        }
        return values;
    }

    // ---------------------------------------------------- storage experiment

    /** Physical cost model of a boxed double only; this is NOT a Protos Number value. */
    private static final class FloatBox {
        final double value;

        FloatBox(double value) {
            this.value = value;
        }
    }

    static double[] pack(FloatBox[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i].value;
        }
        return result;
    }

    static FloatBox[] materialize(double[] values) {
        FloatBox[] result = new FloatBox[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = new FloatBox(values[i]);
        }
        return result;
    }

    /** Same operation order as strictCells, reading through references. */
    static FloatBox[] referenceCells(FloatBox[] a, FloatBox[] b, int m, int k, int n) {
        requireNonEmpty(k);
        FloatBox[] c = new FloatBox[m * n];
        for (int i = 0; i < m; i++) {
            int row = i * k;
            for (int j = 0; j < n; j++) {
                double acc = a[row].value * b[j].value;
                for (int p = 1; p < k; p++) {
                    acc = acc + a[row + p].value * b[p * n + j].value;
                }
                c[i * n + j] = new FloatBox(acc);
            }
        }
        return c;
    }

    static FloatBox[] transientPackedCells(FloatBox[] a, FloatBox[] b, int m, int k, int n) {
        return materialize(strictCells(pack(a), pack(b), m, k, n));
    }

    static FloatBox[] persistentPrimitiveChain(FloatBox[] a, FloatBox[] b, int size, int operations) {
        double[] x = pack(a);
        double[] pb = pack(b);
        for (int op = 0; op < operations; op++) {
            x = strictCells(x, pb, size, size, size);
        }
        return materialize(x);
    }

    static FloatBox[] referenceChain(FloatBox[] a, FloatBox[] b, int size, int operations) {
        FloatBox[] x = a;
        for (int op = 0; op < operations; op++) {
            x = referenceCells(x, b, size, size, size);
        }
        return x;
    }

    static FloatBox[] transientChain(FloatBox[] a, FloatBox[] b, int size, int operations) {
        FloatBox[] x = a;
        for (int op = 0; op < operations; op++) {
            x = transientPackedCells(x, b, size, size, size);
        }
        return x;
    }

    static void runStorageMeasurements() {
        for (int[] s : SHAPES) {
            measureSingleStorage(s[0], s[1], s[2]);
        }
        for (int size : CHAIN_SIZES) {
            measureChains(size);
        }
    }

    private static String storagePrefix(String shape, String work) {
        return "SECTION=STORAGE MEASUREMENT_DOMAIN=HOST_STORAGE_MODEL SHAPE=" + shape + " MEASURED_WORK=" + work;
    }

    private static void measureSingleStorage(int m, int k, int n) {
        SplittableRandom random = new SplittableRandom(0x9068_2000L + m * 31L + k * 7L + n);
        double[] pa = boundedMatrix(random, m * k);
        double[] pb = boundedMatrix(random, k * n);
        FloatBox[] ba = materialize(pa);
        FloatBox[] bb = materialize(pb);
        String shape = shape(m, k, n);
        boolean equivalent = Arrays.equals(pack(referenceCells(ba, bb, m, k, n)), pack(transientPackedCells(ba, bb, m, k, n)));
        String check = " STORAGE_BIT_EQUIVALENT=" + (equivalent ? "YES" : "NO");
        if (!equivalent) {
            System.out.println(storagePrefix(shape, "INTEGRAL_BUILD_COMPUTE_RETURN") + check + " MEASUREMENT=REJECTED");
            return;
        }
        System.out.println(storagePrefix(shape, "INTEGRAL_BUILD_COMPUTE_RETURN") + " VARIANT=referenceCells" + check + " "
                        + measure(() -> referenceSink = referenceCells(ba, bb, m, k, n)).tokens());
        System.out.println(storagePrefix(shape, "INTEGRAL_BUILD_COMPUTE_RETURN") + " VARIANT=transientPackedCells" + check + " "
                        + measure(() -> referenceSink = transientPackedCells(ba, bb, m, k, n)).tokens());
        System.out.println(storagePrefix(shape, "PREPARED_PRIMITIVE_KERNEL_ONLY") + " VARIANT=strictCells" + check + " "
                        + measure(() -> primitiveSink = strictCells(pa, pb, m, k, n)).tokens());
    }

    /** Bounded left operand and a near-identity right operand keep repeated products finite. */
    private static void measureChains(int size) {
        SplittableRandom random = new SplittableRandom(0x9068_3000L + size);
        FloatBox[] a = materialize(boundedMatrix(random, size * size));
        double[] near = new double[size * size];
        for (int i = 0; i < near.length; i++) {
            near[i] = random.nextDouble(-1.0, 1.0) * (1.0e-3 / size);
        }
        for (int d = 0; d < size; d++) {
            near[d * size + d] += 1.0;
        }
        FloatBox[] b = materialize(near);
        double[] persistent = pack(persistentPrimitiveChain(a, b, size, CHAIN_OPERATIONS));
        boolean equivalent = Arrays.equals(persistent, pack(referenceChain(a, b, size, CHAIN_OPERATIONS)))
                        && Arrays.equals(persistent, pack(transientChain(a, b, size, CHAIN_OPERATIONS)));
        String shape = shape(size, size, size);
        String common = " CHAIN_OPERATIONS=" + CHAIN_OPERATIONS + " CHAIN_BIT_EQUIVALENT=" + (equivalent ? "YES" : "NO")
                        + " CHAIN_RESULT_FINITE=" + (allFinite(persistent) ? "YES" : "NO");
        String work = "INTEGRAL_CHAIN_BUILD_COMPUTE_RETURN";
        if (!equivalent) {
            System.out.println(storagePrefix(shape, work) + common + " MEASUREMENT=REJECTED");
            return;
        }
        System.out.println(storagePrefix(shape, work) + " VARIANT=persistentPrimitiveChain" + common + " "
                        + measure(() -> referenceSink = persistentPrimitiveChain(a, b, size, CHAIN_OPERATIONS)).tokens());
        System.out.println(storagePrefix(shape, work) + " VARIANT=referenceChain" + common + " "
                        + measure(() -> referenceSink = referenceChain(a, b, size, CHAIN_OPERATIONS)).tokens());
        System.out.println(storagePrefix(shape, work) + " VARIANT=transientChain" + common + " "
                        + measure(() -> referenceSink = transientChain(a, b, size, CHAIN_OPERATIONS)).tokens());
    }

    // ------------------------------------------------------------- reporting

    static void printMetadata() {
        System.out.println("PLAT068_PROBE=NUMERIC_POLICY");
        System.out.println("MEASUREMENT_DOMAIN=HOST_JAVA_ONLY");
        System.out.println("JAVA_VERSION=" + token(System.getProperty("java.version")));
        System.out.println("JAVA_VM_NAME=" + token(System.getProperty("java.vm.name")));
        System.out.println("JAVA_VM_VERSION=" + token(System.getProperty("java.vm.version")));
        System.out.println("OS_NAME=" + token(System.getProperty("os.name")));
        System.out.println("OS_ARCH=" + token(System.getProperty("os.arch")));
        System.out.println("AVAILABLE_PROCESSORS=" + Runtime.getRuntime().availableProcessors());
        System.out.println("THREAD_CPU_TIME=" + (CPU_TIME ? "AVAILABLE" : "NOT_AVAILABLE"));
        System.out.println("COLUMN_BLOCK=" + COLUMN_BLOCK);
        System.out.println("K_BLOCK=" + K_BLOCK);
        System.out.println("STABILITY_THRESHOLD_REL_DISPERSION=" + fmt(STABLE_DISPERSION));
        System.out.println("SIMD=NOT_PROVEN");
        System.out.println("PROTOS_RUNTIME_EXECUTED=FALSE");
        System.out.println("PRODUCTION_ACCELERATION=NONE");
        System.out.println("D214_F1_CHANGED=NO");
        System.out.println("PLAT068_RATIFIED=NO");
        System.out.println("END_TO_END_SPEEDUP_ACCEPTED=NO");
    }

    private static String token(String value) {
        return value == null ? "UNKNOWN" : value.replace(' ', '_');
    }

    private static String shape(int m, int k, int n) {
        return m + "x" + k + "x" + n;
    }

    private static String hex(long bits) {
        return String.format(Locale.ROOT, "0x%016x", bits);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.6g", value);
    }
}

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

import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosMatrixValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/**
 * I123 / PLAT068 test-local CPU candidates over real Protos Matrix carriers, used by {@code
 * ProtosI123CpuKernelEligibilityTest}: A1 ({@link #strictR1FloatMatmulCandidate}) and T ({@link
 * #strictTemporaryPackedMatmulCandidate}), plus the opt-in timing harness of that test. These are
 * diagnostics only: never installed into the Standard Library prototypes and never a production
 * path. A null result means the candidate declined and the canonical public protocol owns the
 * operation.
 */
final class ProtosI123CpuKernelCandidateSupport {
    private ProtosI123CpuKernelCandidateSupport() {
    }

    /* ---- I123 test-local candidates over real Protos carriers (diagnostic, never installed) ---- */

    /** Exact semantic dimensions plus host ints; the ints are meaningful only for a positive volume. */
    record CandidateShape(
            Object rowsValue, Object columnsValue, int rows, int inner, int columns, int volume) {
    }

    /** Null when the shapes do not compose or exceed storage: the canonical protocol owns that. */
    static CandidateShape candidateShape(ProtosMatrixValue left, ProtosMatrixValue right) {
        Object contraction = left.columnsValue();
        if (!ProtosNumericValueSupport.sameInteger(contraction, right.rowsValue())) {
            return null;
        }
        Object rowsValue = left.rowsValue();
        Object columnsValue = right.columnsValue();
        int volume;
        try {
            volume = ProtosMatrixValue.physicalVolume(rowsValue, columnsValue);
        } catch (IllegalArgumentException tooLarge) {
            return null;
        }
        if (volume == 0) {
            return new CandidateShape(rowsValue, columnsValue, 0, 0, 0, 0);
        }
        int inner =
                ProtosNumericValueSupport.integerSignum(contraction) == 0
                        ? 0
                        : ProtosNumericValueSupport.exactInt(contraction);
        return new CandidateShape(
                rowsValue,
                columnsValue,
                ProtosNumericValueSupport.exactInt(rowsValue),
                inner,
                ProtosNumericValueSupport.exactInt(columnsValue),
                volume);
    }

    /** Eligibility scan: genuine Float carriers only; nothing is converted. */
    static boolean homogeneousFloat(List<Object> elements) {
        for (Object element : elements) {
            if (!(element instanceof ProtosFloatValue)) {
                return false;
            }
        }
        return true;
    }

    /* D214-F1: an empty contraction is exact Integer 0, never Float +0.0. */
    static Object[] emptyContractionCells(int volume) {
        Object[] cells = new Object[volume];
        for (int index = 0; index < volume; index++) {
            cells[index] = ProtosNumericValueSupport.integer(0);
        }
        return cells;
    }

    /** A fresh open Matrix delegating to the operand's prototype, with the exact result shape. */
    static ProtosMatrixValue candidateResult(
            ProtosMatrixValue left, CandidateShape shape, Object[] cells) {
        return new ProtosMatrixValue(
                left.parent().orElseThrow(), shape.rowsValue(), shape.columnsValue(), Arrays.asList(cells));
    }

    /**
     * A1: keeps the R1 {@code Object[]} carriers, unboxes each Float, accumulates strictly from the
     * first product in ascending k and boxes a fresh Float per cell. Null declines the input.
     */
    static ProtosMatrixValue strictR1FloatMatmulCandidate(
            ProtosMatrixValue left, ProtosMatrixValue right) {
        CandidateShape shape = candidateShape(left, right);
        if (shape == null) {
            return null;
        }
        List<Object> l = left.indexedSnapshot();
        List<Object> r = right.indexedSnapshot();
        if (!homogeneousFloat(l) || !homogeneousFloat(r)) {
            return null;
        }
        Object[] cells =
                shape.volume() == 0
                        ? new Object[0]
                        : shape.inner() == 0
                                ? emptyContractionCells(shape.volume())
                                : r1FloatCells(l, r, shape.rows(), shape.inner(), shape.columns());
        return candidateResult(left, shape, cells);
    }

    static Object[] r1FloatCells(
            List<Object> left, List<Object> right, int rows, int inner, int columns) {
        Object[] cells = new Object[rows * columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                double accumulator =
                        ((ProtosFloatValue) left.get(row * inner)).value()
                                * ((ProtosFloatValue) right.get(column)).value();
                for (int k = 1; k < inner; k++) {
                    double product =
                            ((ProtosFloatValue) left.get(row * inner + k)).value()
                                    * ((ProtosFloatValue) right.get(k * columns + column)).value();
                    accumulator = accumulator + product;
                }
                cells[row * columns + column] = new ProtosFloatValue(accumulator);
            }
        }
        return cells;
    }

    /**
     * T: the same guard as A1, then temporary {@code double[]} packing, the row/k/column kernel
     * with one ascending-k accumulator per output column, and fresh Floats. Null declines.
     */
    static ProtosMatrixValue strictTemporaryPackedMatmulCandidate(
            ProtosMatrixValue left, ProtosMatrixValue right) {
        CandidateShape shape = candidateShape(left, right);
        if (shape == null) {
            return null;
        }
        List<Object> l = left.indexedSnapshot();
        List<Object> r = right.indexedSnapshot();
        if (!homogeneousFloat(l) || !homogeneousFloat(r)) {
            return null;
        }
        Object[] cells =
                shape.volume() == 0
                        ? new Object[0]
                        : shape.inner() == 0
                                ? emptyContractionCells(shape.volume())
                                : materializeFloats(
                                        columnLaneMatmul(
                                                packFloats(l), packFloats(r),
                                                shape.rows(), shape.inner(), shape.columns()));
        return candidateResult(left, shape, cells);
    }

    /* Callers have already passed homogeneousFloat. */
    static double[] packFloats(List<Object> elements) {
        double[] packed = new double[elements.size()];
        for (int index = 0; index < packed.length; index++) {
            packed[index] = ((ProtosFloatValue) elements.get(index)).value();
        }
        return packed;
    }

    static Object[] materializeFloats(double[] values) {
        Object[] cells = new Object[values.length];
        for (int index = 0; index < cells.length; index++) {
            cells[index] = new ProtosFloatValue(values[index]);
        }
        return cells;
    }

    /** Row, ascending k, then independent columns; every cell keeps its own ascending k order. */
    static double[] columnLaneMatmul(
            double[] left, double[] right, int rows, int inner, int columns) {
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

    /* ---- Opt-in candidate timing harness; no timing assertions ---- */

    static final List<String> CANDIDATE_PATHS =
            List.of("A0_PUBLIC_MATMUL", "A1_R1_FLOAT", "T_PACKED", "HOST_SCALAR_CELL_CONTROL");
    static final long TARGET_BATCH_NS = 20_000_000L;
    static final long WARMUP_TARGET_NS = 200_000_000L;
    /* Explicit safe limit for calibration: the doubling stops here even below the target. */
    static final int MAX_REPETITIONS = 1 << 24;
    static final String RESULT_CONSUMPTION = "VOLATILE_REFERENCE_STORE_PER_INVOCATION";
    private static final List<String> PHASES = List.of("SNAPSHOT", "SCAN", "PACK", "KERNEL", "MATERIALIZE");

    /* Every timed invocation stores its own result here before the next invocation starts. */
    private static volatile Object consumedResult;

    static void consume(Object result) {
        consumedResult = result;
    }

    /** Rejects an unknown path selector; returns the shapes matching the shape selector (all when null). */
    static List<int[]> selectCandidates(int[][] shapes, String pathSelector, String shapeSelector) {
        if (pathSelector != null && !CANDIDATE_PATHS.contains(pathSelector)) {
            throw new IllegalArgumentException("unknown protos.i123.candidates.path: " + pathSelector);
        }
        List<int[]> selected = new ArrayList<>();
        for (int[] shape : shapes) {
            if (shapeSelector == null || shapeSelector.equals(shape[0] + "x" + shape[1] + "x" + shape[2])) {
                selected.add(shape);
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("unknown protos.i123.candidates.shape: " + shapeSelector);
        }
        return selected;
    }

    /** One independently warmed, calibrated and sampled operation; {@code phase} is TOTAL or a PHASES entry. */
    record Series(String phase, Supplier<Object> operation) {
    }

    /** A path's series plus the labels of the phases it does not measure (default NOT_MEASURED). */
    record PathPlan(
            String path, String domain, String eligibility, String correctness, String suffix,
            Map<String, String> phaseLabels, List<Series> series) {
    }

    record Summary(double medianNs, double dispersion, boolean stable) {
    }

    private record SeriesResult(
            Summary summary, long warmupNs, long calibrationNs, int repetitions, boolean limitReached,
            String telemetry, Telemetry before, Telemetry after, List<Observation> observations) {
    }

    /** One sampled batch: its wall time plus counters captured just outside its timed interval. */
    private record Observation(long wallNs, Telemetry start, Telemetry end) {
    }

    /**
     * Counters; {@code -1} marks a counter the JVM does not report. GC and compilation time are
     * global to the JVM; allocation and CPU time belong to the current thread.
     */
    private record Telemetry(
            long gcCollections, long gcTimeMs, long allocatedBytes, long threadCpuNs, long jitMs) {
        static Telemetry capture() {
            long collections = 0L;
            long timeMs = 0L;
            for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
                long count = collector.getCollectionCount();
                long time = collector.getCollectionTime();
                collections = collections < 0 || count < 0 ? -1L : Math.addExact(collections, count);
                timeMs = timeMs < 0 || time < 0 ? -1L : Math.addExact(timeMs, time);
            }
            long allocated = -1L;
            if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean threads
                    && threads.isThreadAllocatedMemorySupported()
                    && threads.isThreadAllocatedMemoryEnabled()) {
                allocated = threads.getCurrentThreadAllocatedBytes();
            }
            long cpu = -1L;
            if (ManagementFactory.getThreadMXBean().isCurrentThreadCpuTimeSupported()
                    && ManagementFactory.getThreadMXBean().isThreadCpuTimeEnabled()) {
                cpu = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
            }
            long jit = -1L;
            CompilationMXBean compiler = ManagementFactory.getCompilationMXBean();
            if (compiler != null && compiler.isCompilationTimeMonitoringSupported()) {
                jit = compiler.getTotalCompilationTime();
            }
            return new Telemetry(collections, timeMs, allocated, cpu, jit);
        }
    }

    /** Measures every series of one path in order, then prints its series lines and its path line. */
    static void measurePath(
            String caseName, PathPlan plan, int samples, double threshold, boolean telemetry) {
        Map<String, SeriesResult> results = new LinkedHashMap<>();
        for (Series series : plan.series()) {
            SeriesResult result = measureSeries(series.operation(), samples, threshold, telemetry);
            results.put(series.phase(), result);
            System.out.println(String.format(Locale.ROOT,
                    "I123_SERIES CASE=%s PATH=%s PHASE=%s MEDIAN_NS=%s DISPERSION=%.4f STABILITY=%s%s"
                            + " TIMING_DOMAIN=%s%s",
                    caseName, plan.path(), series.phase(), ns(result.summary()),
                    result.summary().dispersion(), stability(result.summary()), calibration(result, samples),
                    plan.domain(), result.telemetry()));
            printObservations(caseName, plan.path(), series.phase(), result);
        }
        SeriesResult total = results.get("TOTAL");
        StringBuilder phases = new StringBuilder();
        for (String phase : PHASES) {
            SeriesResult measured = results.get(phase);
            phases.append(' ').append(phase).append("_NS=").append(
                    measured != null ? ns(measured.summary()) : plan.phaseLabels().getOrDefault(phase, "NOT_MEASURED"));
        }
        System.out.println(String.format(Locale.ROOT,
                "I123_CANDIDATE CASE=%s PATH=%s ELIGIBILITY=%s CORRECTNESS=%s%s TOTAL_NS=%s"
                        + " DISPERSION=%.4f STABILITY=%s%s PHASE_STABILITY=SEE_I123_SERIES TIMING_DOMAIN=%s"
                        + " SIMD=NOT_PROVEN%s",
                caseName, plan.path(), plan.eligibility(), plan.correctness(), phases, ns(total.summary()),
                total.summary().dispersion(), stability(total.summary()), calibration(total, samples),
                plan.domain(), plan.suffix()));
    }

    /**
     * Separate warmup (not sampled), repetition doubling until one batch reaches TARGET_BATCH_NS or
     * MAX_REPETITIONS, then {@code samples} untrimmed batches. Telemetry, when enabled, brackets
     * only the sampled batches and, around each batch, captures that sample's counters; MXBeans are
     * never queried inside a batch, and observations are only stored here, never printed.
     */
    private static SeriesResult measureSeries(
            Supplier<Object> operation, int samples, double threshold, boolean telemetry) {
        long warmupStart = System.nanoTime();
        long warmupNs;
        do {
            consumedResult = operation.get();
            warmupNs = System.nanoTime() - warmupStart;
        } while (warmupNs < WARMUP_TARGET_NS);

        long calibrationStart = System.nanoTime();
        int repetitions = 1;
        boolean limitReached = false;
        while (runBatch(operation, repetitions) < TARGET_BATCH_NS) {
            if (repetitions >= MAX_REPETITIONS) {
                limitReached = true;
                break;
            }
            repetitions = (int) Math.min(MAX_REPETITIONS, 2L * repetitions);
        }
        long calibrationNs = System.nanoTime() - calibrationStart;

        Telemetry before = telemetry ? Telemetry.capture() : null;
        long[] batches = new long[samples];
        List<Observation> observations = new ArrayList<>(telemetry ? samples : 0);
        for (int sample = 0; sample < samples; sample++) {
            Telemetry start = telemetry ? Telemetry.capture() : null;
            batches[sample] = runBatch(operation, repetitions);
            if (telemetry) {
                observations.add(new Observation(batches[sample], start, Telemetry.capture()));
            }
        }
        Telemetry after = telemetry ? Telemetry.capture() : null;
        long operations = Math.multiplyExact((long) samples, (long) repetitions);
        return new SeriesResult(summarize(batches, repetitions, threshold), warmupNs, calibrationNs,
                repetitions, limitReached, telemetry ? telemetryFields(before, after, operations) : " TELEMETRY=DISABLED",
                before, after, observations);
    }

    private static long runBatch(Supplier<Object> operation, int repetitions) {
        long start = System.nanoTime();
        for (int repetition = 0; repetition < repetitions; repetition++) {
            consumedResult = operation.get();
        }
        return System.nanoTime() - start;
    }

    /** Per-operation median and (p90 - p10) / median; stable when within {@code threshold}. */
    static Summary summarize(long[] samples, int repetitions, double threshold) {
        double[] sorted = new double[samples.length];
        for (int index = 0; index < samples.length; index++) {
            sorted[index] = samples[index] / (double) repetitions;
        }
        Arrays.sort(sorted);
        double median = sorted[sorted.length / 2];
        double p10 = sorted[(int) Math.round(0.10d * (sorted.length - 1))];
        double p90 = sorted[(int) Math.round(0.90d * (sorted.length - 1))];
        double dispersion = median > 0.0d ? (p90 - p10) / median : Double.POSITIVE_INFINITY;
        return new Summary(median, dispersion, dispersion <= threshold);
    }

    static String ns(Summary summary) {
        return String.format(Locale.ROOT, "%.1f", summary.medianNs());
    }

    private static String stability(Summary summary) {
        return summary.stable() ? "WITHIN_THRESHOLD" : "NOT_STABLE";
    }

    private static String calibration(SeriesResult result, int samples) {
        return String.format(Locale.ROOT,
                " CALIBRATION=PER_PATH_PER_SHAPE TARGET_BATCH_NS=%d WARMUP_NS=%d CALIBRATION_NS=%d"
                        + " REPETITIONS=%d REPETITION_LIMIT=%s SAMPLES=%d RESULT_CONSUMPTION=%s",
                TARGET_BATCH_NS, result.warmupNs(), result.calibrationNs(), result.repetitions(),
                result.limitReached() ? "REACHED" : "NOT_REACHED", samples, RESULT_CONSUMPTION);
    }

    /* Aggregate deltas over the whole sampled series; no GC pause is attributed to one operation. */
    private static String telemetryFields(Telemetry before, Telemetry after, long operations) {
        String allocated = delta(before.allocatedBytes(), after.allocatedBytes());
        String perOperation =
                allocated.equals("UNAVAILABLE")
                        ? allocated
                        : String.format(Locale.ROOT, "%.1f", Long.parseLong(allocated) / (double) operations);
        return " GC_COLLECTIONS_DELTA=" + delta(before.gcCollections(), after.gcCollections())
                + " GC_TIME_MS_DELTA=" + delta(before.gcTimeMs(), after.gcTimeMs())
                + " THREAD_ALLOCATED_BYTES_DELTA=" + allocated
                + " ALLOCATED_BYTES_PER_OPERATION=" + perOperation
                + " TELEMETRY_SCOPE=SAMPLED_SERIES_AGGREGATE";
    }

    /**
     * After a series: one I123_SAMPLE line per stored observation, then one I123_COHERENCE line
     * comparing the per-sample sums with the aggregate window. The aggregate window also spans the
     * per-sample captures themselves, so the remainder is reported as UNATTRIBUTED_DELTA rather than
     * required to be zero. The GC/no-GC median split is DIAGNOSTIC_ONLY and implies no causation.
     */
    private static void printObservations(String caseName, String path, String phase, SeriesResult result) {
        List<Observation> observations = result.observations();
        if (observations.isEmpty()) {
            return;
        }
        for (int index = 0; index < observations.size(); index++) {
            Observation observation = observations.get(index);
            Telemetry start = observation.start();
            Telemetry end = observation.end();
            System.out.println(String.format(Locale.ROOT,
                    "I123_SAMPLE CASE=%s PATH=%s PHASE=%s SAMPLE_INDEX=%d REPETITIONS=%d WALL_BATCH_NS=%d"
                            + " NS_PER_OPERATION=%.1f GC_COLLECTIONS_DELTA=%s GC_TIME_MS_DELTA=%s"
                            + " THREAD_ALLOCATED_BYTES_DELTA=%s THREAD_CPU_NS_DELTA=%s JIT_COMPILATION_MS_DELTA=%s"
                            + " GC_SCOPE=GLOBAL_JVM JIT_SCOPE=GLOBAL_JVM",
                    caseName, path, phase, index, result.repetitions(), observation.wallNs(),
                    observation.wallNs() / (double) result.repetitions(),
                    delta(start.gcCollections(), end.gcCollections()), delta(start.gcTimeMs(), end.gcTimeMs()),
                    delta(start.allocatedBytes(), end.allocatedBytes()), delta(start.threadCpuNs(), end.threadCpuNs()),
                    delta(start.jitMs(), end.jitMs())));
        }
        List<Long> withoutGc = new ArrayList<>();
        int withGc = 0;
        boolean gcKnown = true;
        for (Observation observation : observations) {
            String collections = delta(observation.start().gcCollections(), observation.end().gcCollections());
            if (collections.equals("UNAVAILABLE")) {
                gcKnown = false;
            } else if (Long.parseLong(collections) > 0) {
                withGc++;
            } else {
                withoutGc.add(observation.wallNs());
            }
        }
        String noGcMedian = "UNAVAILABLE";
        if (gcKnown && !withoutGc.isEmpty()) {
            long[] batches = withoutGc.stream().mapToLong(Long::longValue).toArray();
            noGcMedian = ns(summarize(batches, result.repetitions(), 0.0d));
        }
        System.out.println(String.format(Locale.ROOT,
                "I123_COHERENCE CASE=%s PATH=%s PHASE=%s SAMPLES=%d%s%s%s%s%s SAMPLES_WITH_GC=%s"
                        + " NO_GC_MEDIAN_NS_DIAGNOSTIC_ONLY=%s GC_SPLIT=DIAGNOSTIC_ONLY_NOT_CAUSAL"
                        + " COHERENCE_SCOPE=AGGREGATE_WINDOW_VS_SUM_OF_SAMPLE_WINDOWS",
                caseName, path, phase, observations.size(),
                coherence("GC_COLLECTIONS", observations, result, Telemetry::gcCollections),
                coherence("GC_TIME_MS", observations, result, Telemetry::gcTimeMs),
                coherence("THREAD_ALLOCATED_BYTES", observations, result, Telemetry::allocatedBytes),
                coherence("THREAD_CPU_NS", observations, result, Telemetry::threadCpuNs),
                coherence("JIT_COMPILATION_MS", observations, result, Telemetry::jitMs),
                gcKnown ? Integer.toString(withGc) : "UNAVAILABLE", noGcMedian));
    }

    private static String coherence(
            String name, List<Observation> observations, SeriesResult result,
            ToLongFunction<Telemetry> counter) {
        String aggregate = delta(counter.applyAsLong(result.before()), counter.applyAsLong(result.after()));
        String sum = "UNAVAILABLE";
        if (!aggregate.equals("UNAVAILABLE")) {
            long total = 0L;
            for (Observation observation : observations) {
                String sample = delta(counter.applyAsLong(observation.start()), counter.applyAsLong(observation.end()));
                if (sample.equals("UNAVAILABLE")) {
                    total = -1L;
                    break;
                }
                total = Math.addExact(total, Long.parseLong(sample));
            }
            sum = total < 0 ? "UNAVAILABLE" : Long.toString(total);
        }
        String unattributed =
                sum.equals("UNAVAILABLE")
                        ? "UNAVAILABLE"
                        : Long.toString(Math.subtractExact(Long.parseLong(aggregate), Long.parseLong(sum)));
        return " " + name + "_AGGREGATE=" + aggregate + " " + name + "_SAMPLE_SUM=" + sum
                + " " + name + "_UNATTRIBUTED_DELTA=" + unattributed;
    }

    private static String delta(long before, long after) {
        return before < 0 || after < 0 ? "UNAVAILABLE" : Long.toString(Math.subtractExact(after, before));
    }
}

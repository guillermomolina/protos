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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Explicit, opt-in cost diagnostic of the private SQLite v4 transport. Not part of the general
 * regression: the class name matches no default Surefire include and the test is skipped unless
 * {@code -Dprotos.sqlite.codec.diagnostic=true} is given.
 *
 * <p>Every measured operation is real Protos code (std:text/base64url, std:json/json and the
 * production IsolatedProtocol.protos) running in one installed-CLI child process. This class only
 * timestamps the child's DIAG_BEGIN/DIAG_END lines, so JVM start-up and fixture data construction
 * fall outside every interval. Allocated bytes are not observable from here and are reported as
 * n/a; GC events and pause time come from the child's own unified GC log, mapped onto the
 * intervals through the child's start instant, and are therefore approximate.
 */
final class ProtosIsolatedSqliteCodecDiagnostic {
    private static final Pattern GC_LINE = Pattern.compile("^\\[(\\d+)ms\\].*GC\\(\\d+\\) Pause.* (\\d+(?:\\.\\d+)?)ms$");

    @TempDir Path work;

    private record Key(String operation, String kind, long size, long fragments) {}

    private record Sample(String phase, long beginNanos, long endNanos, long beginUptimeMs, long endUptimeMs) {
        long nanos() { return endNanos - beginNanos; }
    }

    private record Pause(long uptimeMs, double pauseMs) {}

    @Test void measureV4TransportCosts() throws Exception {
        assumeTrue(Boolean.getBoolean("protos.sqlite.codec.diagnostic"),
                "explicit diagnostic: set -Dprotos.sqlite.codec.diagnostic=true");
        String selected = System.getProperty("protos.sqlite.distribution");
        assumeTrue(selected != null, "set -Dprotos.sqlite.distribution to an extracted portable distribution");
        Path distribution = Path.of(selected).toRealPath();
        String sizes = System.getProperty("protos.sqlite.codec.sizes", "65536,262144,1048576,4194304");
        String warmup = System.getProperty("protos.sqlite.codec.warmup", "2");
        String measured = System.getProperty("protos.sqlite.codec.repetitions", "3");

        Files.copy(distribution.resolve("protos/lib/sqlite/internal/IsolatedProtocol.protos"),
                work.resolve("IsolatedProtocol.protos"));
        Path fixture = work.resolve("codec-diagnostic.protos");
        Files.copy(Path.of("protos/tests/foreign-provider/isolated/codec-diagnostic.protos"), fixture);
        Path gcLog = work.resolve("gc.log");

        List<String> args = new ArrayList<>(List.of(distribution.resolve("bin/protos").toString(),
                fixture.toString(), warmup, measured));
        args.addAll(List.of(sizes.split(",")));
        ProcessBuilder builder = new ProcessBuilder(args).directory(work.toFile())
                .redirectError(work.resolve("stderr.txt").toFile());
        builder.environment().remove("PROTOS_HOME");
        builder.environment().put("JAVA_TOOL_OPTIONS", "-Xlog:gc:file=" + gcLog + ":uptimemillis");
        Process child = builder.start();
        child.getOutputStream().close();
        Instant childStart = child.info().startInstant().orElse(null);

        Map<Key, List<Sample>> samples = new LinkedHashMap<>();
        Map<String, long[]> open = new LinkedHashMap<>();
        boolean complete = false;
        try (BufferedReader output = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = output.readLine()) != null) {
                long now = System.nanoTime();
                long uptime = childStart == null ? -1 : Instant.now().toEpochMilli() - childStart.toEpochMilli();
                if (line.equals("DIAG_COMPLETE")) { complete = true; continue; }
                String[] field = line.split(" ");
                if (field.length != 7) continue;
                String label = String.join(" ", List.of(field).subList(1, 7));
                if (field[0].equals("DIAG_BEGIN")) {
                    open.put(label, new long[] {now, uptime});
                } else if (field[0].equals("DIAG_END")) {
                    long[] begin = open.remove(label);
                    assertTrue(begin != null, "unmatched end: " + label);
                    // Live progress: a slow operation must not look like a hang.
                    System.err.println("PLAT074_CODEC " + label + " ms=" + (now - begin[0]) / 1_000_000);
                    System.err.flush();
                    samples.computeIfAbsent(new Key(field[1], field[2], Long.parseLong(field[3]), Long.parseLong(field[4])),
                            key -> new ArrayList<>()).add(new Sample(field[5], begin[0], now, begin[1], uptime));
                }
            }
        } finally {
            if (!child.waitFor(60, TimeUnit.MINUTES)) child.destroyForcibly();
        }
        String errors = Files.readString(work.resolve("stderr.txt"));
        assertEquals(0, child.exitValue(), "diagnostic program failed (integrity or execution):\n"
                + (errors.length() > 8192 ? errors.substring(errors.length() - 8192) : errors));
        assertTrue(complete, "diagnostic program did not complete");
        System.out.println(table(samples, pauses(gcLog)));
    }

    private static List<Pause> pauses(Path gcLog) throws Exception {
        List<Pause> pauses = new ArrayList<>();
        if (!Files.exists(gcLog)) return null;
        for (String line : Files.readAllLines(gcLog)) {
            Matcher match = GC_LINE.matcher(line);
            if (match.matches()) pauses.add(new Pause(Long.parseLong(match.group(1)), Double.parseDouble(match.group(2))));
        }
        return pauses;
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(null);
        return sorted.get(sorted.size() / 2);
    }

    private static String table(Map<Key, List<Sample>> samples, List<Pause> pauses) {
        StringBuilder out = new StringBuilder("PLAT074_CODEC_DIAGNOSTIC\n");
        out.append(String.format("%-14s %-5s %10s %6s %12s %12s %10s %9s %9s %10s %9s %s%n",
                "operation", "kind", "octets", "frags", "first_ms", "median_ms", "ns/octet", "MiB/s",
                "alloc", "gc_events", "gc_ms", "growth_index"));
        Map<String, long[]> previous = new LinkedHashMap<>();
        for (Map.Entry<Key, List<Sample>> entry : samples.entrySet()) {
            Key key = entry.getKey();
            List<Sample> all = entry.getValue();
            long first = all.getFirst().nanos();
            List<Long> measured = new ArrayList<>();
            List<Sample> measuredSamples = new ArrayList<>();
            for (Sample sample : all) {
                if (sample.phase().equals("measured")) { measured.add(sample.nanos()); measuredSamples.add(sample); }
            }
            if (measured.isEmpty()) continue;
            long median = median(measured);
            double nsPerOctet = (double) median / key.size();
            double mibPerSecond = (key.size() / 1048576.0) / (median / 1e9);
            String gcEvents = "n/a";
            String gcMs = "n/a";
            if (pauses != null && measuredSamples.getFirst().beginUptimeMs() >= 0) {
                long events = 0;
                double pause = 0;
                for (Sample sample : measuredSamples) {
                    for (Pause candidate : pauses) {
                        if (candidate.uptimeMs() >= sample.beginUptimeMs() && candidate.uptimeMs() <= sample.endUptimeMs()) {
                            events++;
                            pause += candidate.pauseMs();
                        }
                    }
                }
                gcEvents = String.format("%.1f", (double) events / measuredSamples.size());
                gcMs = String.format("%.1f", pause / measuredSamples.size());
            }
            String series = key.operation() + "/" + key.kind();
            String growth = "-";
            long[] before = previous.get(series);
            // Time ratio over size ratio: about 1 for linear growth; clearly above 1 suggests
            // superlinear cost (or GC/warm-up effects), which this table alone cannot separate.
            if (before != null) {
                growth = String.format("%.2f", ((double) median / before[1]) / ((double) key.size() / before[0]));
            }
            previous.put(series, new long[] {key.size(), median});
            out.append(String.format("%-14s %-5s %10d %6d %12.1f %12.1f %10.1f %9.2f %9s %10s %9s %s%n",
                    key.operation(), key.kind(), key.size(), key.fragments(), first / 1e6, median / 1e6,
                    nsPerOctet, mibPerSecond, "n/a", gcEvents, gcMs, growth));
        }
        out.append("first_ms: first execution (warm-up 0); median_ms: median of measured repetitions;")
                .append(" gc_*: mean per measured repetition, approximate (wall-clock mapping);")
                .append(" alloc: allocated bytes not observable from this harness;")
                .append(" growth_index: (time ratio) / (size ratio) against the previous size of the same operation.");
        return out.toString();
    }
}

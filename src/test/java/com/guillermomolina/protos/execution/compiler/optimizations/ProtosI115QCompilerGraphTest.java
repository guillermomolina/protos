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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.Instruction;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * I115-Q: opt-in physical measurement of compact NORMAL Closure roots.
 *
 * Captures complete Bytecode DSL instructions and the real compiler graph
 * for one selected workload and one requested compiler tier.
 *
 * Module and run costs remain separate. BGV files are retained for
 * independent StructuredGraph After TruffleTier node counting.
 *
 * The ordinary test suite skips graph acquisition.
 */
final class ProtosI115QCompilerGraphTest {

    private static final Path CORE =
            Path.of("protos", "lib", "core");

    private static final Path OUTPUT =
            Path.of("target", "i115-q-graphs");

    private record Workload(String source, int expected) {}

    private record Census(
            int moduleFull,
            int runFull,
            int rootCount,
            String instructions) {}

    private static Workload workload(String name) {
        return switch (name) {
            case "primitive-return-literal" -> new Workload("""
                    run: () => {
                        1
                    }
                    """, 1);

            case "primitive-if-true" -> new Workload("""
                    run: () => {
                        result: null
                        true.ifTrue() {
                            result = 1
                        }
                        result
                    }
                    """, 1);

            case "primitive-if-false" -> new Workload("""
                    run: () => {
                        false.ifTrue() { 99 }
                        1
                    }
                    """, 1);

            case "primitive-while-zero" -> new Workload("""
                    run: () => {
                        (() => false).whileTrue() { 99 }
                        1
                    }
                    """, 1);

            default -> throw new IllegalArgumentException(
                    "Unknown I115-Q workload: " + name);
        };
    }

    @Test
    void captureSelectedRunAtRequestedTier() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean("protos.i115q.graphCapture"),
                "Graph capture is opt-in");

        String caseName = System.getProperty(
                "protos.i115q.graphCase", "");
        String requested = System.getProperty(
                "protos.i115q.graphTier", "");

        assertTrue(
                requested.equals("1") || requested.equals("2"),
                "Compiler tier must be 1 or 2");

        int tier = Integer.parseInt(requested);
        Workload sample = workload(caseName);

        assertEquals(
                "false",
                System.getProperty(
                        "polyglot.engine.BackgroundCompilation"),
                "Synchronous compilation is required");

        assertEquals(
                "true",
                System.getProperty(
                        "protos.diagnostic.stableRootIdentity"),
                "Stable root identities are required");

        List<String> vmArguments = ManagementFactory
                .getRuntimeMXBean()
                .getInputArguments();

        assertTrue(vmArguments.stream().anyMatch(
                value -> value.startsWith(
                        "-Djdk.graal.Dump=Truffle:")),
                "Truffle graph dumping must be active at JVM startup");

        assertTrue(vmArguments.stream().anyMatch(
                value -> value.startsWith(
                        "-Djdk.graal.DumpPath=")),
                "A BGV dump path must be supplied");

        Path directory = OUTPUT
                .resolve(caseName)
                .resolve("tier" + tier);

        Files.createDirectories(directory);

        Path sourceFile = directory.resolve("source.protos");
        Files.writeString(
                sourceFile,
                sample.source(),
                StandardCharsets.UTF_8);

        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(
                        CORE, sourceFile)) {

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    session.initialOutcome().state(),
                    "Module initialization failed");

            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");

            Object target =
                    prepared.hostExecutableForTesting().target;

            ProtosBytecodeRootNode run = assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    prepared.hostExecutableForTesting()
                            .target.getRootNode());

            assertEquals(
                    target,
                    run.getCallTarget(),
                    "The real prepared Closure must be compiled");

            assertTrue(
                    isOptimizedCallTarget(target.getClass()),
                    "The optimizing JVM Truffle runtime is required");

            String rootName = run.toString();

            assertTrue(
                    rootName.startsWith("protos-root:")
                            && rootName.contains("[CLOSURE|")
                            && !rootName.contains("unavailable"),
                    "The selected root lacks stable identity: "
                            + rootName);

            Census census =
                    session.processContextForTesting()
                            .callForRuntime(() -> census(run));

            assertEquals(
                    2,
                    census.rootCount(),
                    "Compact lowering must avoid callback roots");

            Path bytecodeFile = directory.resolve("dsl-full.txt");

            Files.writeString(
                    bytecodeFile,
                    census.instructions(),
                    StandardCharsets.UTF_8);

            System.out.println(
                    "I115_Q_GRAPH_CASE=" + caseName);
            System.out.println(
                    "I115_Q_GRAPH_REQUESTED_TIER=" + tier);
            System.out.println(
                    "I115_Q_GRAPH_SELECTED_ROOT=" + rootName);
            System.out.println(
                    "I115_Q_GRAPH_ROOT_COUNT=" + census.rootCount());
            System.out.println(
                    "I115_Q_GRAPH_MODULE_DSL_FULL="
                            + census.moduleFull());
            System.out.println(
                    "I115_Q_GRAPH_RUN_DSL_FULL="
                            + census.runFull());
            System.out.println(
                    "I115_Q_GRAPH_TOTAL_DSL_FULL="
                            + (census.moduleFull() + census.runFull()));
            System.out.println(
                    "I115_Q_GRAPH_EPILOG_PROJECTS="
                            + run.epilogProjectsReturnCarrier());
            System.out.println(
                    "I115_Q_GRAPH_BYTECODE="
                            + bytecodeFile.toAbsolutePath());

            Value executable = prepared.executable();

            int calls = 0;

            while (calls < 128
                    && run.getBytecodeNode().getTier()
                            != BytecodeTier.CACHED) {

                assertEquals(
                        sample.expected(),
                        executable.execute().asInt());

                calls++;
            }

            assertEquals(
                    BytecodeTier.CACHED,
                    run.getBytecodeNode().getTier(),
                    "The run root must reach cached Bytecode");

            System.out.println(
                    "I115_Q_GRAPH_WARMUP_CALLS=" + calls);

            boolean compiled =
                    session.processContextForTesting()
                            .callForRuntime(() ->
                                    (Boolean) invoke(
                                            target,
                                            "compile",
                                            tier == 2));

            Object compiledTier =
                    invoke(target, "highestCompiledTier");

            System.out.println(
                    "I115_Q_GRAPH_COMPILE_RETURN=" + compiled);
            System.out.println(
                    "I115_Q_GRAPH_COMPILED_TIER=" + compiledTier);

            assertTrue(
                    compiled,
                    "Explicit compilation did not complete");

            assertEquals(
                    tier,
                    compiledTier,
                    "Unexpected compiler tier");

            assertTrue(
                    (Boolean) invoke(target, "isValid"),
                    "The compiled target is invalid");

            assertEquals(
                    sample.expected(),
                    executable.execute().asInt());

            Path dump = Path.of(
                    System.getProperty("jdk.graal.DumpPath"));

            String selector = rootName.split("\\[", 2)[0];

            List<Path> graphs;

            try (var files = Files.walk(dump)) {
                graphs = files
                        .filter(Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".bgv"))
                        .filter(file -> file.getFileName()
                                .toString().contains(selector))
                        .sorted()
                        .toList();
            }

            assertFalse(
                    graphs.isEmpty(),
                    "No BGV found for the selected run root");

            for (Path graph : graphs) {
                assertTrue(
                        Files.size(graph) > 0,
                        "Empty graph: " + graph);

                System.out.println(
                        "I115_Q_GRAPH_BGV="
                                + graph.toAbsolutePath());
            }

            System.out.println(
                    "I115_Q_GRAPH_BGV_COUNT=" + graphs.size());

            System.out.println("I115_Q_GRAPH_RESULT=PASS");
        }
    }

    private static Census census(ProtosBytecodeRootNode run) {
        // Measure DEFAULT Bytecode, preserving the compact lowering.
        // Do not request the fully instrumented representation.
        run.getRootNodes().ensureSourceInformation();

        List<ProtosBytecodeRootNode> roots =
                run.getRootNodes().getNodes().stream()
                        .map(ProtosBytecodeRootNode.class::cast)
                        .toList();

        int moduleFull = -1;
        int runFull = -1;

        StringBuilder report = new StringBuilder();

        for (int index = 0; index < roots.size(); index++) {
            ProtosBytecodeRootNode root = roots.get(index);

            List<Instruction> instructions =
                    root.getBytecodeNode().getInstructionsAsList();

            int count = instructions.size();

            report.append("ROOT_INDEX=")
                    .append(index)
                    .append(" ROOT=")
                    .append(root)
                    .append(" DSL_FULL=")
                    .append(count)
                    .append('\n');

            for (Instruction instruction : instructions) {
                report.append(instruction).append('\n');
            }

            if (root == run) {
                runFull = count;
            } else {
                assertTrue(
                        moduleFull < 0,
                        "Unexpected additional physical root");
                moduleFull = count;
            }
        }

        assertTrue(
                moduleFull >= 0 && runFull >= 0,
                "Expected one module root and one run root");

        return new Census(
                moduleFull,
                runFull,
                roots.size(),
                report.toString());
    }

    private static boolean isOptimizedCallTarget(Class<?> type) {
        for (Class<?> current = type;
                current != null;
                current = current.getSuperclass()) {

            if (current.getName().equals(
                    "com.oracle.truffle.runtime.OptimizedCallTarget")) {
                return true;
            }
        }
        return false;
    }

    private static Object invoke(
            Object target,
            String name,
            Object... args) {

        try {
            for (Class<?> current = target.getClass();
                    current != null;
                    current = current.getSuperclass()) {

                for (java.lang.reflect.Method method
                        : current.getDeclaredMethods()) {

                    if (method.getName().equals(name)
                            && method.getParameterCount()
                                    == args.length) {

                        method.setAccessible(true);
                        return method.invoke(target, args);
                    }
                }
            }

            throw new AssertionError(
                    "Missing OptimizedCallTarget method " + name);

        } catch (IllegalAccessException exception) {
            throw new AssertionError(exception);

        } catch (InvocationTargetException exception) {
            throw new AssertionError(exception.getCause());
        }
    }
}

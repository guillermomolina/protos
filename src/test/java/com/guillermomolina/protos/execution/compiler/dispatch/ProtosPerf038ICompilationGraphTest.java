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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.Instruction;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * PERF038-I: opt-in physical Graal compilation of the canonical terminal
 * method send. The measured unit is run, not the module or identity root.
 *
 * Each requested compiler tier must be acquired in a separate JVM.
 * Nothing in this test changes the NORMAL execution or compiler policy.
 */
final class ProtosPerf038ICompilationGraphTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final Path SOURCE = Path.of(
            "target", "truffle-compilation", "perf038-i",
            "primitive-method-call.protos");

    private static final String PROGRAM = """
            receiver: {
                identity: (value) => { value }
            }

            run: () => {
                receiver.identity(1)
            }
            run()
            """;

    @Test
    void captureTerminalMethodRunAtRequestedCompilerTier() throws IOException {
        Assumptions.assumeTrue(
                Boolean.getBoolean("protos.perf038i.graphCapture"),
                "PERF038-I graph acquisition is opt-in");

        String tierText =
                System.getProperty("protos.perf038i.graphTier", "");

        assertTrue(tierText.equals("1") || tierText.equals("2"),
                "graphTier must be 1 or 2");

        int requestedTier = Integer.parseInt(tierText);

        assertEquals(
                "true",
                System.getProperty("protos.diagnostic.stableRootIdentity"),
                "Stable root identity is required");

        assertEquals(
                "false",
                System.getProperty("polyglot.engine.BackgroundCompilation"),
                "Require synchronous compilation");

        List<String> vmArguments =
                ManagementFactory.getRuntimeMXBean().getInputArguments();

        assertTrue(vmArguments.stream().anyMatch(
                        value -> value.startsWith("-Djdk.graal.Dump=Truffle:")),
                "Missing JVM startup option jdk.graal.Dump");

        assertTrue(vmArguments.stream().anyMatch(
                        value -> value.startsWith("-Djdk.graal.DumpPath=")),
                "Missing JVM startup option jdk.graal.DumpPath");

        assertTrue(vmArguments.contains("-Djdk.graal.PrintGraph=File"),
                "Missing JVM startup option jdk.graal.PrintGraph=File");

        String dumpText = System.getProperty("jdk.graal.DumpPath", "");
        assertFalse(dumpText.isBlank(), "Missing Graal dump directory");

        Path dumpPath = Path.of(dumpText).toAbsolutePath();

        Files.createDirectories(SOURCE.getParent());
        Files.writeString(SOURCE, PROGRAM, StandardCharsets.UTF_8);

        try (ProtosStandaloneHostedSession session =
                     ProtosStandaloneHostedSession.open(CORE, SOURCE)) {

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    session.initialOutcome().state(),
                    "Canonical module initialization must complete");

            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");

            ProtosHostExecutableClosure host =
                    prepared.hostExecutableForTesting();

            ProtosBytecodeRootNode root = assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    host.target.getRootNode());

            root.getRootNodes().ensureSourceInformation();

            var roots = root.getRootNodes().getNodes();

            assertEquals(3, roots.size(),
                    "Expected module, identity and run roots");

            ProtosBytecodeRootNode module = assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    roots.get(0));

            assertTrue(module != root,
                    "Do not compile the module instead of run");

            assertTrue(roots.contains(root),
                    "Run must belong to the canonical source root group");

            ProtosBytecodeRootNode method = null;

            for (Object member : roots) {
                ProtosBytecodeRootNode candidate = assertInstanceOf(
                        ProtosBytecodeRootNode.class, member);

                if (candidate != module && candidate != root) {
                    assertTrue(method == null,
                            "Expected exactly one identity method root");
                    method = candidate;
                }
            }

            assertTrue(method != null, "Identity root is missing");

            List<String> moduleBytecode = instructionNames(module);
            List<String> methodBytecode = instructionNames(method);
            List<String> runBytecode = instructionNames(root);

            assertEquals(52, moduleBytecode.size(),
                    "Canonical module DSL FULL");
            assertEquals(5, methodBytecode.size(),
                    "Canonical identity DSL FULL");
            assertEquals(17, runBytecode.size(),
                    "Canonical run DSL FULL");

            assertEquals(
                    1,
                    runBytecode.stream()
                            .filter("c.InvokeMethodOneTail"::equals)
                            .count(),
                    "Run must contain one terminal method invocation");

            for (String forbidden : List.of(
                    "c.TryDirectSendOne",
                    "c.PrepareSendOne",
                    "c.IsPreparedClosureInvocation",
                    "c.EnterNestedStructuredDispatch",
                    "c.ResumeContinuation",
                    "yield")) {
                assertFalse(runBytecode.contains(forbidden),
                        () -> "Unexpected operation in run: " + forbidden);
            }

            Object target = host.target;

            assertTrue(isOptimizingTarget(target.getClass()),
                    "Requires GraalVM optimizing Truffle runtime");

            assertTrue(target == root.getCallTarget(),
                    "Compile the exact run call target");

            String targetName = root.toString();

            assertTrue(
                    targetName.matches(
                            "protos-root:[0-9a-f]{16}\\[CLOSURE\\|[^\\] ]+\\]"),
                    "Unattributable run target: " + targetName);

            String selector = targetName.split("\\[", 2)[0];

            System.out.println("PERF038_I_GRAPH_TIER_REQUESTED=" + requestedTier);
            System.out.println("PERF038_I_GRAPH_TARGET=" + targetName);
            System.out.println("PERF038_I_GRAPH_SELECTOR=" + selector);
            System.out.println("PERF038_I_GRAPH_SOURCE=" + SOURCE);
            System.out.println("PERF038_I_GRAPH_ROOT_COUNT=" + roots.size());
            System.out.println(
                    "PERF038_I_GRAPH_MODULE_DSL_FULL=" + moduleBytecode.size());
            System.out.println(
                    "PERF038_I_GRAPH_METHOD_DSL_FULL=" + methodBytecode.size());
            System.out.println(
                    "PERF038_I_GRAPH_RUN_DSL_FULL=" + runBytecode.size());
            System.out.println(
                    "PERF038_I_GRAPH_TOTAL_DSL_FULL="
                            + (moduleBytecode.size()
                                    + methodBytecode.size()
                                    + runBytecode.size()));
            System.out.println("PERF038_I_GRAPH_RUN_BYTECODE=" + runBytecode);

            Value executable = prepared.executable();

            int calls = 0;

            while (calls < 128
                    && root.getBytecodeNode().getTier() != BytecodeTier.CACHED) {
                assertEquals(1, executable.execute().asInt());
                calls++;
            }

            assertEquals(
                    BytecodeTier.CACHED,
                    root.getBytecodeNode().getTier(),
                    "Run did not reach the cached Bytecode DSL interpreter");

            System.out.println("PERF038_I_GRAPH_WARMUP_CALLS=" + calls);
            printCallerDiagnostics("WARM", root, runBytecode);
            System.out.println(
                    "PERF038_I_GRAPH_BYTECODE_TIER="
                            + root.getBytecodeNode().getTier());

            /*
             * Record the pre-compilation inventory. A previous BGV must
             * never count as evidence for this compilation request.
             */
            Set<Path> before = matchingGraphs(dumpPath, selector)
                    .stream().collect(Collectors.toSet());

            /*
             * compile(false) requests Tier 1; compile(true) requests Tier 2.
             * Do not use CompileImmediately, which changes tier behavior.
             */
            boolean compiled = session.processContextForTesting()
                    .callForRuntime(() -> (Boolean) compilerMethod(
                            target, "compile", requestedTier == 2));

            Object compiledTier =
                    compilerMethod(target, "highestCompiledTier");

            System.out.println("PERF038_I_GRAPH_COMPILE_RETURN=" + compiled);
            System.out.println("PERF038_I_GRAPH_COMPILED_TIER=" + compiledTier);

            assertTrue(compiled,
                    "Explicit compilation did not complete");
            assertEquals(requestedTier, compiledTier,
                    "Compiler selected a different tier");
            assertTrue(
                    (Boolean) compilerMethod(target, "isValid"),
                    "Compiled target is invalid");

            assertEquals(1, executable.execute().asInt(),
                    "Compiled run changed the guest result");
            printCallerDiagnostics("COMPILED", root, runBytecode);

            List<Path> captured = matchingGraphs(dumpPath, selector)
                    .stream()
                    .filter(path -> !before.contains(path))
                    .toList();

            System.out.println(
                    "PERF038_I_GRAPH_BGV_COUNT=" + captured.size());

            for (Path graph : captured) {
                System.out.println(
                        "PERF038_I_GRAPH_BGV=" + graph.toAbsolutePath());
            }

            assertFalse(captured.isEmpty(),
                    "No fresh BGV attributed to the compiled run target");

            System.out.println("PERF038_I_GRAPH_RESULT=1");
            System.out.println("PERF038_I_GRAPH_CAPTURE=READY");
        }
    }

    /*
     * I115 diagnostics only, attributed to the run root and printed per
     * requested tier; nothing here is asserted. INVOKE_METHOD_ONE_TAIL counts
     * the run root's terminal method sends, GUARDED_ENTRIES the installed
     * direct-tier entries, FALLBACK_INSTANTIATED whether the generic
     * fallback of that site was ever specialized, and
     * CALLER_MATERIALIZATION_NEED whether run registered its exact-activation
     * caller need (it publishes its activation for a caller resolution).
     */
    private static void printCallerDiagnostics(
            String phase, ProtosBytecodeRootNode run, List<String> runBytecode) {
        String prefix = "PERF038_I_GRAPH_" + phase + "_";
        System.out.println(prefix + "INVOKE_METHOD_ONE_TAIL="
                + runBytecode.stream().filter("c.InvokeMethodOneTail"::equals).count());
        int entries = 0;
        boolean fallback = false;
        try {
            Object bytecode = run.getBytecodeNode();
            Object[] cachedNodes = (Object[]) field(bytecode, "cachedNodes_");
            for (Object node : cachedNodes) {
                if (node == null || !node.getClass().getSimpleName()
                        .equals("InvokeMethodOneTail_Node")) {
                    continue;
                }
                for (Object entry = field(node, "guardedSource_cache");
                        entry != null;
                        entry = field(entry, "next_")) {
                    entries++;
                }
                for (java.lang.reflect.Field declared : node.getClass().getDeclaredFields()) {
                    if (declared.getName().startsWith("generic")) {
                        declared.setAccessible(true);
                        fallback |= declared.get(node) != null;
                    }
                }
            }
        } catch (ReflectiveOperationException unavailable) {
            System.out.println(prefix + "CACHE_INTROSPECTION=UNAVAILABLE " + unavailable);
        }
        System.out.println(prefix + "GUARDED_ENTRIES=" + entries);
        System.out.println(prefix + "FALLBACK_INSTANTIATED=" + fallback);
        System.out.println(prefix + "CALLER_MATERIALIZATION_NEED="
                + run.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION));
        // I115: whether run's callers must transport its inherited prelude.
        System.out.println(prefix + "CALLER_PRELUDE_NEED="
                + run.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE));
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException absent) {
                // Continue with the superclass.
            }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }

    private static List<String> instructionNames(
            ProtosBytecodeRootNode root) {
        List<String> names = new ArrayList<>();
        for (Instruction instruction :
                root.getBytecodeNode().getInstructionsAsList()) {
            names.add(instruction.getName());
        }
        return names;
    }

    private static List<Path> matchingGraphs(
            Path dumpPath, String selector) throws IOException {
        if (!Files.isDirectory(dumpPath)) {
            return List.of();
        }
        try (var paths = Files.walk(dumpPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".bgv"))
                    .filter(path -> path.getFileName().toString()
                            .contains(selector))
                    .filter(path -> {
                        try {
                            return Files.size(path) > 0;
                        } catch (IOException error) {
                            return false;
                        }
                    })
                    .sorted()
                    .toList();
        }
    }

    private static boolean isOptimizingTarget(Class<?> type) {
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

    private static Object compilerMethod(
            Object target, String name, Object... arguments) {
        try {
            for (Class<?> current = target.getClass();
                    current != null;
                    current = current.getSuperclass()) {
                for (java.lang.reflect.Method method :
                        current.getDeclaredMethods()) {
                    if (method.getName().equals(name)
                            && method.getParameterCount()
                                    == arguments.length) {
                        method.setAccessible(true);
                        return method.invoke(target, arguments);
                    }
                }
            }
            throw new AssertionError(
                    "Missing OptimizedCallTarget method: " + name);
        } catch (IllegalAccessException error) {
            throw new AssertionError(error);
        } catch (InvocationTargetException error) {
            throw new AssertionError(error.getCause());
        }
    }
}

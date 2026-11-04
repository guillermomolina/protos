/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READ THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THE
 * CONTENTS OF THIS FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE
 * FOLLOWING WEB SITE: https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.bytecode.BytecodeTier;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * I115 opt-in compiler regression: compile the actual run() of the
 * primitive-closure-call case, parse its own After TruffleTier BGV and assert
 * zero LoadFieldNode and IsNullNode. No pre-existing summary is consulted.
 *
 * <p>The BGV reader is Graal's own graphio parser, loaded reflectively so
 * ordinary test compilation has no dependency on compiler implementation
 * classes. Run with tools/i115_tier2_run_graph.sh on a GraalVM JDK.
 */
final class ProtosI115Tier2RunGraphTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path SOURCE = Path.of("target", "truffle-compilation", "i115-run", "primitive-closure-call.protos");
    private static final String PROGRAM = """
            run: () => {
                identity: (value) => { value }
                identity(1)
            }
            """;
    private static final String GRAPH_TYPE = "StructuredGraph";
    private static final String PHASE = "After TruffleTier";

    @Test
    void compiledRunContainsNoLoadFieldOrIsNullNodes() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("protos.i115.graphCapture"),
                "I115 compiler diagnostic is opt-in: use tools/i115_tier2_run_graph.sh");
        assertEquals("false", System.getProperty("polyglot.engine.BackgroundCompilation"),
                "Require synchronous compilation");
        assertEquals("true", System.getProperty("protos.diagnostic.stableRootIdentity"),
                "Require stable root IDs for BGV attribution");

        String dumpOption = System.getProperty("jdk.graal.DumpPath", "");
        assertTrue(!dumpOption.isBlank(), "A fresh Graal BGV dump directory is required");
        Path dumps = Path.of(dumpOption).toAbsolutePath();
        assertTrue(Files.isDirectory(dumps), "BGV dump directory missing: " + dumps);
        try (var existing = Files.walk(dumps)) {
            assertTrue(existing.noneMatch(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".bgv")),
                    "BGV dump directory must be empty to exclude stale measurements");
        }

        Files.createDirectories(SOURCE.getParent());
        Files.writeString(SOURCE, PROGRAM);

        String targetName;
        try (ProtosStandaloneHostedSession session = ProtosStandaloneHostedSession.open(CORE, SOURCE)) {
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, session.initialOutcome().state());
            ProtosStandaloneHostedSession.PreparedTopLevel prepared = session.prepareTopLevel("run");
            ProtosHostExecutableClosure host = prepared.hostExecutableForTesting();
            ProtosBytecodeRootNode root = assertInstanceOf(
                    ProtosBytecodeRootNode.class, host.target.getRootNode());
            Object target = host.target;
            assertTrue(isOptimizedCallTarget(target.getClass()),
                    "Requires an optimizing GraalVM Truffle runtime");
            targetName = root.toString();
            assertTrue(targetName.matches("protos-root:[0-9a-f]{16}\\[CLOSURE\\|[^\\] ]+\\]"),
                    "Expected stable run Closure root, got: " + targetName);
            assertEquals(target, root.getCallTarget(), "Compile the actual run CallTarget");

            Value run = prepared.executable();
            // Reproduce the cold-capture invocation count and natural tiering:
            // one cold call followed by 2,000 calls; do not compile the root
            // immediately after its interpreter first becomes CACHED.
            int calls = 0;
            while (calls < 2001) {
                assertEquals(1, run.execute().asInt(), "run must return identity(1)");
                calls++;
            }
            assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier(),
                    "run did not reach the cached Bytecode DSL interpreter");

            assertEquals(2, compilerMethod(target, "highestCompiledTier"),
                    "The selected run root did not reach natural Tier 2 compilation");
            assertEquals(true, compilerMethod(target, "isValid"),
                    "The compiled run target is invalid");
            System.out.println("I115_RUN_TARGET=" + targetName);
            System.out.println("I115_RUN_TIER=2");
            System.out.println("I115_RUN_WARMUP_CALLS=" + calls);
        }

        String rootId = targetName.substring(0, targetName.indexOf('['));
        List<Path> selectedBgvs;
        try (var files = Files.walk(dumps)) {
            selectedBgvs = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".bgv"))
                    .filter(path -> path.getFileName().toString().contains(rootId))
                    .sorted().toList();
        }
        assertTrue(!selectedBgvs.isEmpty(),
                "No newly compiled run BGV: " + selectedBgvs);

        // Natural multi-tier execution may dump both Tier 1 and Tier 2.
        // With synchronous compilation, later compilation IDs supersede
        // earlier ones. The target's highest compiled tier was checked above.
        Path bgv = selectedBgvs.stream()
                .max(Comparator.comparingLong(ProtosI115Tier2RunGraphTest::compilationId))
                .orElseThrow();
        System.out.println("I115_RUN_BGV_COUNT=" + selectedBgvs.size());
        System.out.println("I115_RUN_SELECTED_COMP_ID=" + compilationId(bgv));
        assertTrue(Files.size(bgv) > 0, "Empty run BGV: " + bgv);
        GraphCounts counts = countsFromGraalBgv(bgv);
        System.out.println("I115_RUN_BGV=" + bgv.toAbsolutePath());
        System.out.println("I115_RUN_TIER2_TOTAL_NODES=" + counts.total());
        System.out.println("I115_RUN_TIER2_LOAD_FIELD_NODE=" + counts.loadField());
        System.out.println("I115_RUN_TIER2_IS_NULL_NODE=" + counts.isNull());
        assertEquals(0, counts.loadField(), "Tier 2 run contains LoadFieldNode");
        assertEquals(0, counts.isNull(), "Tier 2 run contains IsNullNode");
    }

    private static long compilationId(Path path) {
        String name = path.getFileName().toString();
        String prefix = "TruffleHotSpotCompilation-";
        int opening = name.indexOf('[');
        assertTrue(name.startsWith(prefix) && opening > prefix.length(),
                "Unrecognized Graal compilation BGV name: " + name);
        return Long.parseLong(name.substring(prefix.length(), opening));
    }

    private record GraphCounts(int total, int loadField, int isNull) {}

    /**
     * Parse the BGV using the graphio parser in the GraalVM compiler module.
     * The run script exports only the two required parser packages to tests.
     */
    private static GraphCounts countsFromGraalBgv(Path bgv) throws Exception {
        Class<?> dataSourceType = Class.forName("jdk.graal.compiler.graphio.parsing.DataSource");
        Class<?> builderType = Class.forName("jdk.graal.compiler.graphio.parsing.Builder");
        Class<?> graphDocType = Class.forName("jdk.graal.compiler.graphio.parsing.model.GraphDocument");
        Class<?> parseMonitorType = Class.forName("jdk.graal.compiler.graphio.parsing.ParseMonitor");
        Class<?> modelBuilderType = Class.forName("jdk.graal.compiler.graphio.parsing.ModelBuilder");
        Class<?> inputGraphType = Class.forName("jdk.graal.compiler.graphio.parsing.model.InputGraph");
        Class<?> readerType = Class.forName("jdk.graal.compiler.graphio.parsing.BinaryReader");
        Class<?> sourceType = Class.forName("jdk.graal.compiler.graphio.parsing.BinarySource");

        List<Object> phases = new ArrayList<>();
        try (ReadableByteChannel channel = Files.newByteChannel(bgv)) {
            Object source = sourceType.getConstructor(Object.class, ReadableByteChannel.class)
                    .newInstance(bgv.toString(), channel);
            Object document = graphDocType.getConstructor().newInstance();
            Object builder = modelBuilderType.getConstructor(graphDocType, parseMonitorType)
                    .newInstance(document, null);
            Object reader = readerType.getConstructor(dataSourceType, builderType)
                    .newInstance(source, builder);
            Object parsed = readerType.getMethod("parse").invoke(reader);
            collectPhaseGraphs(parsed, inputGraphType, phases);
        }
        assertEquals(1, phases.size(),
                "Expected one StructuredGraph at After TruffleTier; found " + phases.size());

        Object graph = phases.get(0);
        Collection<?> nodes = assertInstanceOf(Collection.class,
                inputGraphType.getMethod("getNodes").invoke(graph));
        assertTrue(!nodes.isEmpty(), "Selected compiler graph has no nodes");

        Map<String, Integer> histogram = new HashMap<>();
        Map<Integer, Object> nodesById = new HashMap<>();
        List<Object> unwantedNodes = new ArrayList<>();
        for (Object node : nodes) {
            Object nodeClass = node.getClass().getMethod("getNodeClass").invoke(node);
            assertNotNull(nodeClass, "Unclassified node in compiler graph");
            Object fullName = nodeClass.getClass().getField("className").get(nodeClass);
            assertTrue(fullName instanceof String && !((String) fullName).isBlank(),
                    "Missing compiler node class");
            String name = ((String) fullName).replaceFirst("^.*\\.", "");
            histogram.merge(name, 1, Integer::sum);
            int id = (Integer) node.getClass().getMethod("getId").invoke(node);
            assertTrue(nodesById.put(id, node) == null, "Duplicate compiler node id " + id);
            if (name.equals("LoadFieldNode") || name.equals("IsNullNode")) {
                unwantedNodes.add(node);
            }
        }
        // Diagnostic ownership is the real selected Tier-2 BGV. Emit the
        // offending nodes' properties and their incoming data-flow edges,
        // so each remaining field load/null check can be tied to its origin.
        for (Object node : unwantedNodes) {
            printOffendingNode(node, nodesById);
        }
        return new GraphCounts(nodes.size(), histogram.getOrDefault("LoadFieldNode", 0),
                histogram.getOrDefault("IsNullNode", 0));
    }

    private static void printOffendingNode(Object node, Map<Integer, Object> nodesById)
            throws ReflectiveOperationException {
        int id = (Integer) node.getClass().getMethod("getId").invoke(node);
        System.out.println("I115_GRAPH_OFFENDER=" + id + " " + compilerNodeClass(node));
        printInputChain(node, nodesById, 0, new HashSet<>());
    }

    /** Print at most two edges upstream of each offender, without dumping the entire graph. */
    private static void printInputChain(Object node, Map<Integer, Object> nodesById,
            int depth, Set<Integer> visited) throws ReflectiveOperationException {
        int id = (Integer) node.getClass().getMethod("getId").invoke(node);
        if (!visited.add(id)) {
            return;
        }
        Object properties = node.getClass().getMethod("getProperties").invoke(node);
        String text = String.valueOf(properties).replace('\n', ' ').replace('\r', ' ');
        if (text.length() > 700) {
            text = text.substring(0, 700) + " ...";
        }
        System.out.println("I115_GRAPH_NODE depth=" + depth + " id=" + id
                + " class=" + compilerNodeClass(node) + " properties=" + text);
        if (depth == 2) {
            return;
        }
        Object raw = node.getClass().getMethod("getIdsMap").invoke(node);
        Map<?, ?> inputs = assertInstanceOf(Map.class, raw);
        for (var entry : inputs.entrySet()) {
            Object port = entry.getKey();
            Object name = port.getClass().getField("name").get(port);
            Collection<?> incomingIds = assertInstanceOf(Collection.class, entry.getValue());
            for (Object incomingId : incomingIds) {
                Object input = nodesById.get(incomingId);
                System.out.println("I115_GRAPH_EDGE depth=" + depth + " node=" + id
                        + " port=" + name + " input=" + incomingId
                        + " class=" + (input == null ? "MISSING" : compilerNodeClass(input)));
                if (input != null) {
                    printInputChain(input, nodesById, depth + 1, visited);
                }
            }
        }
    }

    private static String compilerNodeClass(Object node) throws ReflectiveOperationException {
        Object nodeClass = node.getClass().getMethod("getNodeClass").invoke(node);
        Object fullName = nodeClass.getClass().getField("className").get(nodeClass);
        return String.valueOf(fullName).replaceFirst("^.*\\.", "");
    }

    private static void collectPhaseGraphs(Object folder, Class<?> inputGraphType,
            List<Object> selected) throws ReflectiveOperationException {
        Object children = folder.getClass().getMethod("getElements").invoke(folder);
        for (Object child : assertInstanceOf(Collection.class, children)) {
            if (inputGraphType.isInstance(child)) {
                String name = (String) inputGraphType.getMethod("getName").invoke(child);
                if (name != null && name.replaceFirst("^\\d+\\s*:\\s*", "").trim().equals(PHASE)) {
                    Object type = inputGraphType.getMethod("getGraphType").invoke(child);
                    if (GRAPH_TYPE.equals(type)) {
                        selected.add(child);
                    }
                }
            } else if (child.getClass().getSimpleName().equals("Group")) {
                collectPhaseGraphs(child, inputGraphType, selected);
            }
        }
    }

    private static boolean isOptimizedCallTarget(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().equals("com.oracle.truffle.runtime.OptimizedCallTarget")) {
                return true;
            }
        }
        return false;
    }

    private static Object compilerMethod(Object target, String name, Object... args) {
        try {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                        method.setAccessible(true);
                        return method.invoke(target, args);
                    }
                }
            }
        } catch (IllegalAccessException error) {
            throw new AssertionError("Cannot access OptimizedCallTarget." + name, error);
        } catch (InvocationTargetException error) {
            throw new AssertionError("OptimizedCallTarget." + name + " failed", error.getCause());
        }
        throw new AssertionError("Missing OptimizedCallTarget." + name);
    }
}

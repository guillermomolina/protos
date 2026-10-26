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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.bytecode.Instruction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * I115-R: experimental factorial baseline.
 *
 * Records complete Bytecode DSL instructions for module, run,
 * factorial and any additional physical roots.
 *
 * The workload and its result remain unchanged by instrumentation.
 */
final class ProtosI115RFactorialCaptureTest {

    private static final Path CORE =
            Path.of("protos", "lib", "core");

    private static final Path OUTPUT =
            Path.of("target", "i115-r-factorial", "dsl");

    private static final long EXPECTED =
            2432902008176640000L;

    private static final String SOURCE = """
            factorial: (n) => {
                result: 1

                (n > 1).ifTrue() {
                    result = n * factorial(n - 1)
                }

                result
            }

            run: () => {
                factorial(20)
            }
            """;

    @Test
    void factorialDslAndCorrectness() throws Exception {
        Files.createDirectories(OUTPUT);

        Path sourceFile = OUTPUT.resolve("source.protos");
        Files.writeString(
                sourceFile, SOURCE, StandardCharsets.UTF_8);

        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(
                        CORE, sourceFile)) {

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    session.initialOutcome().state());

            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");

            ProtosStandaloneHostedSession.PreparedTopLevel factorialPrepared =
                    session.prepareTopLevel("factorial");

            ProtosBytecodeRootNode run = assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    prepared.hostExecutableForTesting()
                            .target.getRootNode());

            ProtosBytecodeRootNode factorial = assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    factorialPrepared.hostExecutableForTesting()
                            .target.getRootNode());

            assertTrue(run != factorial);
            assertTrue(run.getRootNodes() == factorial.getRootNodes());

            String report = session.processContextForTesting()
                    .callForRuntime(() -> census(run, factorial));

            Path output = OUTPUT.resolve("dsl-full.txt");
            Files.writeString(
                    output, report, StandardCharsets.UTF_8);

            Value result = prepared.executable().execute();

            assertTrue(result.fitsInLong());
            assertEquals(EXPECTED, result.asLong());

            System.out.println("I115_R_RESULT=" + result.asLong());
            System.out.println("I115_R_CORRECTNESS=PASS");
            System.out.println(
                    "I115_R_DSL_FILE=" + output.toAbsolutePath());
        }
    }


    @Test
    void captureFactorialCompilerGraph() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Boolean.getBoolean("protos.i115r.graphCapture"),
                "I115-R compiler capture is opt-in");

        String selectedRole =
                System.getProperty("protos.i115r.graphRoot", "");
        String requestedTier =
                System.getProperty("protos.i115r.graphTier", "");

        assertTrue(
                selectedRole.equals("run")
                        || selectedRole.equals("factorial"),
                "Select run or factorial");

        assertTrue(
                requestedTier.equals("1")
                        || requestedTier.equals("2"),
                "Select compiler Tier 1 or Tier 2");

        assertEquals("false", System.getProperty(
                "polyglot.engine.BackgroundCompilation"));

        assertEquals("true", System.getProperty(
                "protos.diagnostic.stableRootIdentity"));

        assertEquals("false", System.getProperty(
                "polyglot.engine.DynamicCompilationThresholds"));

        assertEquals("1000000", System.getProperty(
                "polyglot.engine.FirstTierCompilationThreshold"));

        assertEquals("1000000", System.getProperty(
                "polyglot.engine.LastTierCompilationThreshold"));

        var vmArguments = java.lang.management.ManagementFactory
                .getRuntimeMXBean().getInputArguments();

        assertTrue(vmArguments.stream().anyMatch(
                arg -> arg.startsWith("-Djdk.graal.Dump=Truffle:")));

        assertTrue(vmArguments.stream().anyMatch(
                arg -> arg.startsWith("-Djdk.graal.DumpPath=")));

        int tier = Integer.parseInt(requestedTier);

        Path directory = Path.of(
                "target", "i115-r-factorial", "graphs",
                selectedRole, "tier" + tier);

        Files.createDirectories(directory);

        Path sourceFile = directory.resolve("source.protos");
        Files.writeString(
                sourceFile, SOURCE, StandardCharsets.UTF_8);

        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(
                        CORE, sourceFile)) {

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    session.initialOutcome().state());

            var runPrepared = session.prepareTopLevel("run");
            var factorialPrepared =
                    session.prepareTopLevel("factorial");

            var runRoot = assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    runPrepared.hostExecutableForTesting()
                            .target.getRootNode());

            var factorialRoot = assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    factorialPrepared.hostExecutableForTesting()
                            .target.getRootNode());

            ProtosBytecodeRootNode selectedRoot =
                    selectedRole.equals("run")
                            ? runRoot : factorialRoot;

            Object selectedTarget =
                    selectedRole.equals("run")
                            ? runPrepared.hostExecutableForTesting().target
                            : factorialPrepared.hostExecutableForTesting().target;

            assertTrue(
                    selectedRoot.getCallTarget() == selectedTarget,
                    "Capture must compile the actual Closure target");

            assertTrue(
                    isOptimizingTarget(selectedTarget),
                    "Optimizing Truffle runtime required");

            String rootName = selectedRoot.toString();

            assertTrue(
                    rootName.startsWith("protos-root:")
                            && rootName.contains("[CLOSURE|"),
                    "Stable Closure root identity missing: " + rootName);

            String census = session.processContextForTesting()
                    .callForRuntime(
                            () -> census(runRoot, factorialRoot));

            Path dsl = directory.resolve("dsl-full.txt");
            Files.writeString(
                    dsl, census, StandardCharsets.UTF_8);

            graphMarker("CASE", "factorial");
            graphMarker("SELECTED_ROLE", selectedRole);
            graphMarker("REQUESTED_TIER", tier);
            graphMarker("SELECTED_ROOT", rootName);
            graphMarker("ROOT_COUNT",
                    runRoot.getRootNodes().getNodes().size());
            graphMarker("BYTECODE", dsl.toAbsolutePath());
            graphMarker("SOURCE", sourceFile.toAbsolutePath());

            var roots = runRoot.getRootNodes().getNodes();

            for (int i = 0; i < roots.size(); i++) {
                var root = (ProtosBytecodeRootNode) roots.get(i);

                graphMarker("ROOT_" + i + "_NAME", root);
                graphMarker("ROOT_" + i + "_SOURCE",
                        root.getSourceSection());
                graphMarker("ROOT_" + i + "_DSL_FULL",
                        root.getBytecodeNode()
                                .getInstructionsAsList().size());
            }

            Value executable = runPrepared.executable();

            for (int call = 0; call < 128; call++) {
                Value result = executable.execute();

                assertTrue(result.fitsInLong());
                assertEquals(EXPECTED, result.asLong());
            }

            graphMarker("WARMUP_CALLS", 128);

            graphMarker("RUN_BYTECODE_TIER",
                    runRoot.getBytecodeNode().getTier());

            graphMarker("FACTORIAL_BYTECODE_TIER",
                    factorialRoot.getBytecodeNode().getTier());

            assertEquals(
                    com.oracle.truffle.api.bytecode.BytecodeTier.CACHED,
                    selectedRoot.getBytecodeNode().getTier(),
                    "Selected root must use cached Bytecode");

            Object before = invokeCompiler(
                    selectedTarget, "highestCompiledTier");

            graphMarker("PRECOMPILE_TIER", before);

            assertTrue(
                    before instanceof Number
                            && ((Number) before).intValue() <= 0,
                    "Target compiled automatically before explicit request");

            boolean compiled = session.processContextForTesting()
                    .callForRuntime(() -> (Boolean) invokeCompiler(
                            selectedTarget, "compile", tier == 2));

            Object actualTier = invokeCompiler(
                    selectedTarget, "highestCompiledTier");

            graphMarker("COMPILE_RETURN", compiled);
            graphMarker("COMPILED_TIER", actualTier);

            assertTrue(compiled, "Explicit compilation failed");

            assertEquals(
                    tier, actualTier,
                    "Compiled tier differs from requested tier");

            assertTrue(
                    (Boolean) invokeCompiler(
                            selectedTarget, "isValid"),
                    "Selected compiled target is invalid");

            Path dump = Path.of(
                    System.getProperty("jdk.graal.DumpPath"));

            String selector = rootName.split("\\[", 2)[0];

            List<Path> graphs;

            try (var files = Files.walk(dump)) {
                graphs = files
                        .filter(Files::isRegularFile)
                        .filter(file -> file.toString()
                                .endsWith(".bgv"))
                        .filter(file -> file.getFileName()
                                .toString().contains(selector))
                        .sorted()
                        .toList();
            }

            assertTrue(
                    !graphs.isEmpty(),
                    "No BGV files for selected root: " + rootName);

            for (Path graph : graphs) {
                assertTrue(Files.size(graph) > 0);
                graphMarker("BGV", graph.toAbsolutePath());
            }

            graphMarker("BGV_COUNT", graphs.size());
            graphMarker("JVM_PID",
                    ProcessHandle.current().pid());
            graphMarker("RESULT", "PASS");
        }
    }

    private static boolean isOptimizingTarget(Object target) {
        for (Class<?> current = target.getClass();
                current != null;
                current = current.getSuperclass()) {
            if (current.getName().equals(
                    "com.oracle.truffle.runtime.OptimizedCallTarget")) {
                return true;
            }
        }
        return false;
    }

    private static Object invokeCompiler(
            Object target, String name, Object... arguments) {
        try {
            for (Class<?> current = target.getClass();
                    current != null;
                    current = current.getSuperclass()) {
                for (java.lang.reflect.Method method
                        : current.getDeclaredMethods()) {
                    if (method.getName().equals(name)
                            && method.getParameterCount()
                                    == arguments.length) {
                        method.setAccessible(true);
                        return method.invoke(target, arguments);
                    }
                }
            }
            throw new AssertionError(
                    "Missing compiler method: " + name);
        } catch (IllegalAccessException error) {
            throw new AssertionError(error);
        } catch (java.lang.reflect.InvocationTargetException error) {
            throw new AssertionError(error.getCause());
        }
    }

    private static void graphMarker(String name, Object value) {
        System.out.println("I115_R_GRAPH_" + name + "=" + value);
    }


    /*
     * I115-H1-FIX: '>' is the shared guarded numeric site; its HIT yields a
     * canonical Boolean that runs the callback inline, and any other result
     * receives ifTrue as an ordinary send with the callback as a guest
     * Closure, whose root the lowering registers. The unary parameter is
     * not assumed to be an Integer.
     */
    @Test
    void compactUnaryBooleanControlIsGuarded() throws Exception {
        Path directory = Path.of(
                "target", "i115-r-factorial", "unary");
        Files.createDirectories(directory);

        Path sourceFile = directory.resolve("source.protos");
        Files.writeString(sourceFile, """
                compute: (n) => {
                    result: 1

                    (n > 1).ifTrue() {
                        result = n * 2
                    }

                    result
                }

                runZero: () => { compute(0) }
                runThree: () => { compute(3) }
                """, StandardCharsets.UTF_8);

        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(
                        CORE, sourceFile)) {

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    session.initialOutcome().state());

            var compute = session.prepareTopLevel("compute");

            ProtosBytecodeRootNode root = assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    compute.hostExecutableForTesting()
                            .target.getRootNode());

            var instructions = root.getBytecodeNode()
                    .getInstructionsAsList();

            List<String> names = instructions.stream()
                    .map(ProtosInstructionNames::of)
                    .toList();

            System.out.println(
                    "I115_R1_UNARY_DSL_FULL=" + instructions.size());

            System.out.println(
                    "I115_R1_UNARY_INSTRUCTIONS=" + names);

            for (String required : List.of(
                    "BindUnaryFrameParameter",
                    "TryPrimitiveNumericGreater",
                    "TryPrimitiveNumericMultiply",
                    "IsCanonicalBooleanValue",
                    "ExperimentalCanonicalBooleanCondition",
                    "MaterializeCurrentClosure")) {
                assertTrue(
                        names.stream().anyMatch(
                                name -> name.contains(required)),
                        "Missing guarded compact operation: " + required);
            }

            for (String retired : List.of(
                    "ExperimentalInteger",
                    "InstallFrameLexicalAuthority",
                    "InvokeStaticClosureOneTail")) {
                assertTrue(
                        names.stream().noneMatch(
                                name -> name.contains(retired)),
                        "Unexpected retired operation: " + retired);
            }

            assertEquals(
                    1,
                    session.prepareTopLevel("runZero")
                            .executable().execute().asInt());

            assertEquals(
                    6,
                    session.prepareTopLevel("runThree")
                            .executable().execute().asInt());

            System.out.println("I115_R1_UNARY_CORRECTNESS=PASS");
            System.out.println("I115_R1_UNARY_GUARDED_COMPACT=PASS");
        }
    }


    private static final String I115_R2_SOURCE = """
            factorial: (n) => {
                result: 1

                (n > 1).ifTrue() {
                    result = n * factorial(n - 1)
                }

                result
            }

            run: () => {
                factorial(20)
            }
            """;

    /*
     * I115-H2: experimental structural contract. This selected factorial
     * workload uses the admitted compact recursive lane; its measurements
     * do not establish general Protos semantic equivalence.
     */
    @Test
    void compactRecursiveFactorialStructure() throws Exception {
        Path file = Path.of(
                "target", "i115-r-factorial", "r2-structure.protos");
        Files.createDirectories(file.getParent());
        Files.writeString(file, I115_R2_SOURCE, StandardCharsets.UTF_8);

        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(CORE, file)) {

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    session.initialOutcome().state());

            var prepared = session.prepareTopLevel("factorial");
            ProtosBytecodeRootNode factorial = assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    prepared.hostExecutableForTesting()
                            .target.getRootNode());

            factorial.getRootNodes().ensureSourceInformation();

            int rootCount = factorial.getRootNodes().getNodes().size();

            List<String> names = factorial.getBytecodeNode()
                    .getInstructionsAsList().stream()
                    .map(ProtosInstructionNames::of)
                    .toList();

            System.out.println("I115_R2_TEST_ROOT_COUNT=" + rootCount);
            System.out.println("I115_R2_TEST_DSL_FULL=" + names.size());

            assertEquals(3, rootCount,
                    "Unexpected callback or other physical root");

            // I115-H3: the direct parameter binding is two instructions.
            assertEquals(19, names.size(),
                    "I115-H3 experimental factorial FULL DSL budget");

            for (String required : List.of(
                    "ExperimentalLongArgument0",
                    "InvokeStaticClosureOneTail",
                    "ExperimentalIntegerMultiply",
                    "ExperimentalIntegerSubtract",
                    "ExperimentalIntegerGreaterBool")) {
                assertTrue(names.stream().anyMatch(
                        name -> name.contains(required)),
                        "Missing compact operation: " + required);
            }

            for (String forbidden : List.of(
                    "PrepareClosureCall",
                    "PrepareSendOne",
                    "EnterNestedStructuredDispatch",
                    "ResumeContinuation",
                    "CompleteClosureCall",
                    "InstallFrameLexicalAuthority",
                    "BindUnaryFrameParameter",
                    "TryPrimitiveNumeric")) {
                assertTrue(names.stream().noneMatch(
                        name -> name.contains(forbidden)),
                        "Unexpected generic operation: " + forbidden);
            }

            assertEquals(
                    2432902008176640000L,
                    session.prepareTopLevel("run")
                            .executable().execute().asLong());

            System.out.println("I115_R2_STRUCTURE=PASS");
        }
    }

    private static final String FACTORIAL_OVERFLOW_SOURCE = """
            factorial: (n) => {
                result: 1

                (n > 1).ifTrue() {
                    result = n * factorial(n - 1)
                }

                result
            }

            runOverflow: () => {
                factorial(21) == 51090942171709440000
            }
            """;

    /*
     * I115-H1: factorial(21) exceeds long, so the recursive product must
     * cross from the primitive carrier to a large Integer through the
     * ordinary invocation. Independent of compilation and graph capture.
     */
    @Test
    @Disabled("I115-H3: the experimental lane wraps on long overflow (owner-accepted divergence)")
    void recursiveFactorialOverflowsToLargeInteger() throws Exception {
        Path file = Path.of(
                "target", "i115-r-factorial", "h1-overflow.protos");
        Files.createDirectories(file.getParent());
        Files.writeString(
                file, FACTORIAL_OVERFLOW_SOURCE, StandardCharsets.UTF_8);

        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(CORE, file)) {

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    session.initialOutcome().state());

            Value result = session.prepareTopLevel("runOverflow")
                    .executable().execute();

            assertTrue(result.isBoolean());
            assertTrue(result.asBoolean());
        }
    }

    /** I115-R5 historical run() FULL DSL count, before the R6 module-global read. */
    private static final int I115_R5_RUN_DSL_FULL_HISTORICAL = 17;

    /** I115-R6 run() FULL DSL sequence (GraalPy run() has 6). */
    private static final List<String> I115_R6_RUN_DSL_FULL = List.of(
            "c.EnterRoot",
            "c.ReadGlobal",
            "load.constant",
            "c.InvokeExperimentalClosureOneTail",
            "c.EpilogForReturn",
            "return");

    /*
     * I115-R6 (experimental): run() reads the module-owned factorial with
     * one ReadGlobal. R5 used the generated materialized captured read
     * (owner-frame selection, load.local.mat and a conditional fallback)
     * at I115_R5_RUN_DSL_FULL_HISTORICAL instructions.
     */
    @Test
    void capturedModuleBindingUsesModuleGlobalRead()
            throws Exception {
        Path file = Path.of(
                "target", "i115-r-factorial",
                "r6-module-global.protos");
        Files.createDirectories(file.getParent());
        Files.writeString(
                file, I115_R2_SOURCE, StandardCharsets.UTF_8);

        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(CORE, file)) {
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    session.initialOutcome().state());

            var prepared = session.prepareTopLevel("run");
            ProtosBytecodeRootNode root = assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    prepared.hostExecutableForTesting()
                            .target.getRootNode());

            List<String> names = root.getBytecodeNode()
                    .getInstructionsAsList().stream()
                    .map(ProtosInstructionNames::of)
                    .toList();

            assertEquals(
                    I115_R6_RUN_DSL_FULL,
                    names,
                    "I115-R6 run() FULL DSL sequence");

            for (String retired : List.of(
                    "SelectCapturedMaterializedOwnerFrameAtRoot",
                    "IsCapturedOwnerFrameSelected",
                    "ReadCapturedFallbackAtRoot",
                    "ReadCapturedFrameLocalAtRoot",
                    "load.local.mat")) {
                assertTrue(
                        names.stream().noneMatch(
                                name -> name.contains(retired)),
                        "Module-owned read kept a captured path: " + retired);
            }

            assertEquals(
                    EXPECTED, prepared.executable().execute().asLong());

            System.out.println(
                    "I115_R5_RUN_DSL_FULL_HISTORICAL="
                            + I115_R5_RUN_DSL_FULL_HISTORICAL);
            System.out.println(
                    "I115_R6_RUN_DSL_FULL=" + names.size());
            System.out.println(
                    "I115_R6_MODULE_GLOBAL_READ=PASS");
        }
    }

    @Test
    void compactRecursiveFactorialBoundaryInputs() throws Exception {
        String source = I115_R2_SOURCE + """
                runZero: () => { factorial(0) }
                runOne: () => { factorial(1) }
                runTwo: () => { factorial(2) }
                runFive: () => { factorial(5) }
                """;

        Path file = Path.of(
                "target", "i115-r-factorial", "r2-boundaries.protos");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);

        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(CORE, file)) {

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    session.initialOutcome().state());

            assertEquals(
                    1L,
                    session.prepareTopLevel("runZero")
                            .executable().execute().asLong());

            assertEquals(
                    1L,
                    session.prepareTopLevel("runOne")
                            .executable().execute().asLong());

            assertEquals(
                    2L,
                    session.prepareTopLevel("runTwo")
                            .executable().execute().asLong());

            assertEquals(
                    120L,
                    session.prepareTopLevel("runFive")
                            .executable().execute().asLong());

            assertEquals(
                    2432902008176640000L,
                    session.prepareTopLevel("run")
                            .executable().execute().asLong());

            System.out.println("I115_R2_BOUNDARIES=PASS");
        }
    }

    private static String census(
            ProtosBytecodeRootNode run,
            ProtosBytecodeRootNode factorial) {

        run.getRootNodes().ensureSourceInformation();

        List<ProtosBytecodeRootNode> roots =
                run.getRootNodes().getNodes().stream()
                        .map(ProtosBytecodeRootNode.class::cast)
                        .toList();

        assertTrue(roots.size() >= 3);

        int moduleIndex = -1;

        for (int i = 0; i < roots.size(); i++) {
            if (roots.get(i).toString().contains("[TOP_LEVEL|")) {
                assertTrue(moduleIndex < 0, "Multiple module roots");
                moduleIndex = i;
            }
        }

        if (moduleIndex < 0) {
            for (int i = 0; i < roots.size(); i++) {
                if (roots.get(i) != run && roots.get(i) != factorial) {
                    moduleIndex = i;
                    break;
                }
            }
        }

        assertTrue(moduleIndex >= 0, "Module root missing");

        int moduleFull = 0;
        int runFull = 0;
        int factorialFull = 0;
        int additionalFull = 0;

        StringBuilder report = new StringBuilder();

        for (int i = 0; i < roots.size(); i++) {
            ProtosBytecodeRootNode root = roots.get(i);

            List<Instruction> instructions =
                    root.getBytecodeNode().getInstructionsAsList();

            int count = instructions.size();
            String role;

            if (root == run) {
                role = "run";
                runFull += count;
            } else if (root == factorial) {
                role = "factorial";
                factorialFull += count;
            } else if (i == moduleIndex) {
                role = "module";
                moduleFull += count;
            } else {
                role = "additional";
                additionalFull += count;
            }

            report.append("ROOT_INDEX=").append(i)
                    .append(" ROLE=").append(role)
                    .append(" ROOT=").append(root)
                    .append(" SOURCE=").append(root.getSourceSection())
                    .append(" DSL_FULL=").append(count)
                    .append('\n');

            for (int n = 0; n < count; n++) {
                Instruction instruction = instructions.get(n);

                report.append("INSTRUCTION[")
                        .append(i).append("][")
                        .append(n).append("]=")
                        .append(ProtosInstructionNames.of(instruction))
                        .append(" | ")
                        .append(instruction)
                        .append('\n');
            }
        }

        int total = moduleFull + runFull
                + factorialFull + additionalFull;

        // I115-H2: retain the verified R6 physical-bytecode baseline.
        assertEquals(3, roots.size(), "Experimental physical root count");
        assertEquals(17, moduleFull, "Experimental module DSL");
        // I115-H3: +1 for the direct parameter binding.
        assertEquals(19, factorialFull, "Experimental factorial DSL");
        assertEquals(6, runFull, "Experimental run DSL");
        assertEquals(0, additionalFull, "Unexpected callback root");
        assertEquals(42, total, "Experimental total DSL");

        System.out.println("I115_R_ROOT_COUNT=" + roots.size());
        System.out.println("I115_R_MODULE_DSL_FULL=" + moduleFull);
        System.out.println("I115_R_RUN_DSL_FULL=" + runFull);
        System.out.println("I115_R_FACTORIAL_DSL_FULL=" + factorialFull);
        System.out.println("I115_R_ADDITIONAL_DSL_FULL=" + additionalFull);
        System.out.println("I115_R_TOTAL_DSL_FULL=" + total);
        System.out.println("I115_R_RUN_ROOT=" + run);
        System.out.println("I115_R_FACTORIAL_ROOT=" + factorial);

        assertTrue(moduleFull > 0);
        assertTrue(runFull > 0);
        assertTrue(factorialFull > 0);

        return report.toString();
    }
}

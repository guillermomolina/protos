/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION
 * OF DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S
 * ACCEPTANCE OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT
 * READS THE TERMS OF THE LICENSE. A COPY OF THE LICENSE IS LOCATED IN THE
 * TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING THE CONTENTS OF THIS FILE.
 */

package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF041 / I115: full physical Bytecode DSL census of primitive-if-true.
 *
 * Uses the exact benchmark program body. The invocation is performed
 * separately so no additional source statement enters the measured
 * module or run root.
 *
 * This test is observational: no instruction budget is asserted.
 * No fallback, branch, instrumentation or auxiliary root is filtered.
 */
final class ProtosPerf041PrimitiveIfTrueCensusTest {

    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final String PROGRAM = """
            run: () => {
                result: null
                true.ifTrue() {
                    result = 1
                }
                result
            }
            """;

    @Test
    void primitiveIfTrueFullPhysicalCensus() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();

            try {
                ProtosPrelude prelude =
                        new ProtosCoreBootstrap().bootstrap(CORE);

                ProtosBytecodeRootNode module =
                        parse(PROGRAM, "perf041-primitive-if-true.protos");

                // Capture the physical DSL before executing the program.
                // ensureComplete() would activate additional instrumentation.
                module.getRootNodes().ensureSourceInformation();

                List<ProtosBytecodeRootNode> roots =
                        module.getRootNodes().getNodes().stream()
                                .map(ProtosBytecodeRootNode.class::cast)
                                .toList();

                assertTrue(roots.size() >= 2,
                        "Expected at least module and run roots");
                assertSame(module, roots.get(0));

                List<List<String>> signatures = roots.stream()
                        .map(ProtosPerf041PrimitiveIfTrueCensusTest::fullSignature)
                        .toList();

                // Execute the declaration, preserving the original source.
                ProtosClosureValue closure = assertInstanceOf(
                        ProtosClosureValue.class,
                        module.getCallTarget().call(
                                prelude.newModuleActivation()));

                ProtosClosureExecutionPlan plan =
                        closure.executionPlan().orElseThrow();

                ProtosBytecodeRootNode runRoot =
                        plan.bytecodeActivationRootForTesting();

                int runIndex = roots.indexOf(runRoot);
                assertTrue(runIndex > 0,
                        "run must be a separate physical source root");

                // Call run through ordinary Protos semantics.
                ProtosActivation invocation =
                        prelude.newModuleActivation();

                invocation.context().createLocalSlot(
                        "candidate", closure);

                Object result = parse(
                        "candidate()",
                        "perf041-invoke.protos")
                        .getCallTarget()
                        .call(invocation);

                assertEquals(
                        "1",
                        ProtosNumericValueSupport.integerDecimalText(result));

                // Publish observations only after correctness succeeds.
                System.out.println("PERF041_CASE=primitive-if-true");
                System.out.println("PERF041_CORRECTNESS=PASS");
                System.out.println("PERF041_RESULT=1");
                System.out.println(
                        "PERF041_CONFIG=BytecodeConfig.DEFAULT");
                System.out.println(
                        "PERF041_ROOT_COUNT=" + roots.size());
                System.out.println(
                        "PERF041_RUN_ROOT_INDEX=" + runIndex);

                int total = 0;

                for (int i = 0; i < roots.size(); i++) {
                    ProtosBytecodeRootNode root = roots.get(i);
                    List<String> signature = signatures.get(i);

                    String role = i == 0
                            ? "module"
                            : i == runIndex ? "run" : "auxiliary";

                    total += signature.size();

                    System.out.println(
                            "PERF041_ROOT_INDEX=" + i);
                    System.out.println(
                            "PERF041_ROOT_ROLE=" + role);
                    System.out.println(
                            "PERF041_ROOT_SOURCE="
                                    + root.getSourceSection());
                    System.out.println(
                            "PERF041_ROOT_TIER="
                                    + root.getBytecodeNode().getTier());
                    System.out.println(
                            "PERF041_ROOT_FULL_COUNT="
                                    + signature.size());

                    for (int j = 0; j < signature.size(); j++) {
                        System.out.println(
                                "PERF041_INSTRUCTION[" + i + "]["
                                        + j + "]=" + signature.get(j));
                    }
                }

                System.out.println(
                        "PERF041_RUN_FULL="
                                + signatures.get(runIndex).size());
                System.out.println(
                        "PERF041_MODULE_FULL="
                                + signatures.get(0).size());
                System.out.println(
                        "PERF041_TOTAL_FULL=" + total);
                System.out.println("PERF041_CENSUS=PASS");

            } finally {
                context.leave();
            }
        }
    }


    /**
     * Opt-in real Graal compilation of the exact PERF041 run Closure.
     * Each requested tier runs in an independent JVM.
     * No synthetic guest caller or modified benchmark body is introduced.
     */
    @Test
    void capturePrimitiveIfTrueAtRequestedTier() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Boolean.getBoolean("protos.perf041.graphCapture"),
                "PERF041 Graal capture is opt-in");

        String tierText = System.getProperty(
                "protos.perf041.graphTier", "");

        assertTrue(tierText.equals("1") || tierText.equals("2"),
                "Expected graphTier=1 or graphTier=2");

        int requestedTier = Integer.parseInt(tierText);

        assertEquals("true",
                System.getProperty("protos.diagnostic.stableRootIdentity"));

        assertEquals("false",
                System.getProperty("polyglot.engine.BackgroundCompilation"));

        String dumpText = System.getProperty("jdk.graal.DumpPath", "");
        assertTrue(!dumpText.isBlank(), "Missing Graal DumpPath");

        Path dumpPath = Path.of(dumpText);
        java.nio.file.Files.createDirectories(dumpPath);

        Path sourceFile = Path.of(
                "target", "truffle-compilation",
                "perf041-i115", "primitive-if-true.protos");

        java.nio.file.Files.createDirectories(sourceFile.getParent());
        java.nio.file.Files.writeString(sourceFile, PROGRAM);

        try (ProtosStandaloneHostedSession session =
                     ProtosStandaloneHostedSession.open(CORE, sourceFile)) {

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    session.initialOutcome().state());

            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");

            ProtosHostExecutableClosure host =
                    prepared.hostExecutableForTesting();

            ProtosBytecodeRootNode root = assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    host.target.getRootNode());

            Object target = host.target;

            boolean optimizing = false;
            for (Class<?> type = target.getClass();
                    type != null;
                    type = type.getSuperclass()) {
                if (type.getName().equals(
                        "com.oracle.truffle.runtime.OptimizedCallTarget")) {
                    optimizing = true;
                    break;
                }
            }

            assertTrue(optimizing, "Optimizing Truffle runtime required");
            assertEquals(2, root.getRootNodes().getNodes().size(),
                    "Expected module and run roots");

            String targetName = root.toString();
            assertTrue(targetName.startsWith("protos-root:"),
                    "Stable root identity required: " + targetName);

            System.out.println("PERF041_GRAPH_TIER_REQUESTED="
                    + requestedTier);
            System.out.println("PERF041_GRAPH_ROOT=" + targetName);
            System.out.println("PERF041_GRAPH_SOURCE=" + sourceFile);
            System.out.println("PERF041_GRAPH_DSL_FULL="
                    + root.getBytecodeNode()
                            .getInstructionsAsList().size());

            org.graalvm.polyglot.Value run = prepared.executable();

            int calls = 0;
            while (calls < 128
                    && root.getBytecodeNode().getTier()
                            != com.oracle.truffle.api.bytecode.BytecodeTier.CACHED) {
                assertEquals(1, run.execute().asInt());
                calls++;
            }

            assertEquals(
                    com.oracle.truffle.api.bytecode.BytecodeTier.CACHED,
                    root.getBytecodeNode().getTier(),
                    "run did not reach the cached interpreter");

            System.out.println("PERF041_GRAPH_WARMUP_CALLS=" + calls);

            boolean compiled =
                    session.processContextForTesting().callForRuntime(
                            () -> (Boolean) compilerMethod(
                                    target, "compile", requestedTier == 2));

            Object actualTier = compilerMethod(
                    target, "highestCompiledTier");

            System.out.println("PERF041_GRAPH_COMPILE_RETURN=" + compiled);
            System.out.println("PERF041_GRAPH_COMPILED_TIER=" + actualTier);

            assertTrue(compiled, "Compilation did not complete");
            assertEquals(requestedTier, actualTier);
            assertTrue((Boolean) compilerMethod(target, "isValid"));

            String selector = targetName;
            int bracket = selector.indexOf('[');
            if (bracket >= 0) {
                selector = selector.substring(0, bracket);
            }

            final String selected = selector;
            List<Path> graphs;

            try (var files = java.nio.file.Files.walk(dumpPath)) {
                graphs = files
                        .filter(java.nio.file.Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".bgv"))
                        .filter(file -> file.getFileName()
                                .toString().contains(selected))
                        .sorted()
                        .toList();
            }

            System.out.println("PERF041_GRAPH_BGV_COUNT=" + graphs.size());

            for (Path graph : graphs) {
                System.out.println("PERF041_GRAPH_BGV="
                        + graph.toAbsolutePath());
            }

            assertTrue(!graphs.isEmpty(),
                    "No BGV captured for selected run root");

            System.out.println("PERF041_GRAPH_CAPTURE=PASS");
        }
    }

    private static Object compilerMethod(
            Object target, String name, Object... arguments) {
        try {
            for (Class<?> type = target.getClass();
                    type != null;
                    type = type.getSuperclass()) {
                for (java.lang.reflect.Method method
                        : type.getDeclaredMethods()) {
                    if (method.getName().equals(name)
                            && method.getParameterCount()
                                    == arguments.length) {
                        method.setAccessible(true);
                        return method.invoke(target, arguments);
                    }
                }
            }
            throw new AssertionError(
                    "Missing OptimizedCallTarget method " + name);
        } catch (java.lang.reflect.InvocationTargetException error) {
            throw new AssertionError(error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static ProtosBytecodeRootNode parse(
            String program, String name) {

        Source source = Source.newBuilder(
                ProtosLanguage.ID, program, name).build();

        RootCallTarget target = assertInstanceOf(
                RootCallTarget.class,
                ProtosLanguageContext.current().parsePublic(source));

        return assertInstanceOf(
                ProtosBytecodeRootNode.class, target.getRootNode());
    }

    private static List<String> fullSignature(
            ProtosBytecodeRootNode root) {

        return root.getBytecodeNode().getInstructionsAsList()
                .stream()
                .map(instruction -> {
                    StringBuilder entry = new StringBuilder(
                            ProtosInstructionNames.of(instruction));

                    for (Instruction.Argument argument :
                            instruction.getArguments()) {

                        entry.append(' ')
                                .append(argument.getName())
                                .append(':')
                                .append(argument.getKind())
                                .append('=')
                                .append(operand(argument));
                    }

                    return entry.toString();
                })
                .toList();
    }

    private static String operand(Instruction.Argument argument) {
        return switch (argument.getKind()) {
            case CONSTANT -> constant(argument.asConstant());
            case BYTECODE_INDEX ->
                    Integer.toString(argument.asBytecodeIndex());
            case INTEGER ->
                    Integer.toString(argument.asInteger());
            case LOCAL_INDEX ->
                    Integer.toString(argument.asLocalIndex());
            case LOCAL_OFFSET ->
                    Integer.toString(argument.asLocalOffset());
            case TAG_NODE ->
                    argument.asTagNode() == null
                            ? "no-tag-node"
                            : argument.asTagNode().getTags().stream()
                                    .map(Class::getName)
                                    .sorted()
                                    .toList()
                                    .toString();
            case NODE_PROFILE, BRANCH_PROFILE -> "profile";
        };
    }

    private static String constant(Object value) {
        if (value == null) {
            return "host-null";
        }
        if (value instanceof Number
                || value instanceof String
                || value instanceof Boolean) {
            return value.getClass().getName() + "(" + value + ")";
        }
        if (value instanceof Class<?> type) {
            return "Class(" + type.getName() + ")";
        }
        return "Metadata(" + value.getClass().getName() + ")";
    }
}

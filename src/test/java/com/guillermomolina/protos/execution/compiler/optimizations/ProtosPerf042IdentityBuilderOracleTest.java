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

import static org.junit.jupiter.api.Assertions.assertTrue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import java.nio.file.Files;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * PERF042: handwritten Bytecode DSL oracle with parameter binding.
 *
 * This is a physical lower bound, not a replacement implementation
 * of the complete observable Closure protocol.
 */
final class ProtosPerf042IdentityBuilderOracleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE =
            LanguageReference.create(ProtosLanguage.class);

    private static final String IDENTITY = "(value) => { value }";

    @Test
    void handwrittenIdentityBytecodeOnly() throws Exception {
        withCore(ignored -> {
            ProtosSemanticBytecodeRootNode root =
                    handwrittenIdentity();

            assertEquals(1, root.getRootNodes().getNodes().size());

            printIdentityBytecode("MINIMAL_BUILDER", root);

            assertEquals(
                    List.of(
                            "load.argument",
                            "store.local",
                            "load.local",
                            "return"),
                    executionPrefixNames(root));
        });
    }

    @Test
    void protosIdentityBytecodeOnly() throws Exception {
        withCore(ignored -> {
            ProtosSemanticBytecodeRootNode module =
                    parse(IDENTITY, "identity-protos-bytecode");

            assertEquals(2, module.getRootNodes().getNodes().size());

            ProtosSemanticBytecodeRootNode root =
                    assertInstanceOf(
                            ProtosSemanticBytecodeRootNode.class,
                            module.getRootNodes().getNode(1));

            printIdentityBytecode("PROTOS_LOWERER", root);

            assertEquals(
                    List.of(
                            "c.BindUnaryFrameParameter",
                            "load.constant",
                            "c.ReadRootFrameLocal",
                            "c.MaterializeReturnCarrier",
                            "return"),
                    executionPrefixNames(root));
        });
    }

    /**
     * PERF042: oráculo con binding explícito del parámetro.
     *
     * Graal podrá eliminar el store/load si demuestra que no
     * son observables. El lowerer conserva el binding.
     */
    private static ProtosSemanticBytecodeRootNode handwrittenIdentity() {
        String code = IDENTITY;
        Source source = source(code, "identity-oracle");

        BytecodeRootNodes<ProtosSemanticBytecodeRootNode> roots =
                ProtosSemanticBytecodeRootNodeGen.create(
                        LANGUAGE.get(null),
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginSource(source);
                            builder.beginSourceSection(
                                    code.indexOf('{'),
                                    code.lastIndexOf('}') - code.indexOf('{') + 1);
                            builder.beginRoot();

                            BytecodeLocal value =
                                    builder.createLocal("value", null);

                            builder.beginStoreLocal(value);
                            builder.emitLoadArgument(
                                    ProtosFrameArguments.SUPPLIED_ARGUMENT_OFFSET);
                            builder.endStoreLocal();

                            builder.beginReturn();
                            builder.emitLoadLocal(value);
                            builder.endReturn();

                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });

        ProtosSemanticBytecodeRootNode root = roots.getNode(0);

        // Diagnostic-only: make the handwritten root selectable in Graal dumps.
        if (Boolean.getBoolean("protos.perf042.graphCapture")
                && ProtosDiagnosticRootIdentity.ENABLED) {
            int bodyStart = code.indexOf('{');
            int bodyLength = code.lastIndexOf('}') - bodyStart + 1;
            ProtosDiagnosticRootIdentity.record(
                    root,
                    new ProtosDiagnosticRootIdentity.Span(
                            source,
                            bodyStart,
                            bodyLength,
                            ProtosDiagnosticRootIdentity.CLOSURE));
        }

        return root;
    }


    /**
     * PERF042 opt-in Graal Tier 1 acquisition.
     *
     * Run once per JVM with side=manual or side=lowerer.
     * The root is the Closure activation itself, never an enclosing
     * run root or a synthetic Builder caller.
     *
     * Captures only; intentionally performs no graph comparison.
     */
    @Test
    void captureIdentityRootAtRequestedTier() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean("protos.perf042.graphCapture"),
                "PERF042 Tier 1 acquisition is opt-in");

        String side = System.getProperty(
                "protos.perf042.graphSide", "");

        assertTrue(
                side.equals("manual") || side.equals("lowerer"),
                "tier1Side must be manual or lowerer");

        assertEquals(
                "false",
                System.getProperty("polyglot.engine.BackgroundCompilation"),
                "Tier 1 capture requires synchronous compilation");

        assertEquals(
                "true",
                System.getProperty("protos.diagnostic.stableRootIdentity"),
                "Tier 1 capture requires stable root identities");

        String tierText = System.getProperty(
                "protos.perf042.graphTier", "1");

        assertTrue(
                tierText.equals("1") || tierText.equals("2"),
                "graphTier must be 1 or 2");

        int requestedTier = Integer.parseInt(tierText);

        withCore(module -> {
            // The source contains only the Closure expression.
            // Executing its top-level expression obtains the real Closure
            // and its B5 invocation metadata, not a guest caller.
            ProtosSemanticBytecodeRootNode sourceRoot =
                    parse(IDENTITY, "identity-tier1-source");

            ProtosClosureValue closure = assertInstanceOf(
                    ProtosClosureValue.class,
                    sourceRoot.getCallTarget().call(module));

            ProtosSemanticBytecodeRootNode root =
                    side.equals("manual")
                            ? handwrittenIdentity()
                            : closure.executionPlan().orElseThrow()
                                    .bytecodeActivationRootForTesting();

            root.getRootNodes().ensureComplete();

            Object target = root.getCallTarget();
            assertTrue(perf042OptimizedCallTarget(target.getClass()),
                    "Requires the JVM optimizing Truffle runtime");

            String targetName = root.toString();

            assertTrue(
                    targetName.startsWith("protos-root:")
                            && !targetName.contains("unavailable"),
                    "Root cannot be identified in Graal dumps: " + targetName);

            // Exactly the same supplied value and ABI in both JVM lanes.
            Object argument = ProtosNumericValueSupport.integer(1L);

            // Move the selected Bytecode DSL root naturally from UNCACHED
            // to CACHED before requesting compiler Tier 1.
            int calls = 0;
            while (calls < 128
                    && root.getBytecodeNode().getTier() != BytecodeTier.CACHED) {
                Object[] arguments =
                        ProtosFrameArguments.compactDirectClosureCallOne(
                                closure, module, null, argument);

                root.getCallTarget().call(arguments);
                calls++;
            }

            assertEquals(
                    BytecodeTier.CACHED,
                    root.getBytecodeNode().getTier(),
                    "Closure root did not enter the cached interpreter");

            System.out.println(
                    "PERF042_TIER" + requestedTier + "_SIDE=" + side);
            System.out.println(
                    "PERF042_TIER" + requestedTier + "_ROOT=" + targetName);
            System.out.println(
                    "PERF042_TIER" + requestedTier + "_WARMUP_CALLS=" + calls);
            System.out.println(
                    "PERF042_TIER" + requestedTier + "_BYTECODE_TIER="
                            + root.getBytecodeNode().getTier());

            // Same mechanism as ProtosI101CanonicalLocalGraphTest.
            // compile(false) requests Tier 1; compile(true) requests Tier 2.
            boolean compiled = (Boolean) perf042CompilerMethod(
                    target, "compile", requestedTier == 2);

            Object highestTier = perf042CompilerMethod(
                    target, "highestCompiledTier");

            System.out.println(
                    "PERF042_TIER" + requestedTier + "_COMPILE_RETURN=" + compiled);
            System.out.println(
                    "PERF042_TIER" + requestedTier + "_COMPILED_TIER=" + highestTier);

            assertTrue(compiled, "Tier 1 compilation did not complete");
            assertEquals(requestedTier, highestTier, "Unexpected compiler tier");

            assertTrue(
                    (Boolean) perf042CompilerMethod(target, "isValid"),
                    "Tier 1 compiled target is invalid");

            // Print the BGV files attributed to this exact root.
            // No interpretation or comparison of graph contents.
            String dumpProperty = System.getProperty(
                    "jdk.graal.DumpPath", "");

            assertTrue(
                    !dumpProperty.isBlank(),
                    "Specify jdk.graal.DumpPath to acquire graph files");

            Path dumpPath = Path.of(dumpProperty);
            String selector = targetName.split("\\[", 2)[0];

            List<Path> graphFiles;
            try (var files = Files.walk(dumpPath)) {
                graphFiles = files
                        .filter(Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".bgv"))
                        .filter(file ->
                                file.getFileName().toString()
                                        .contains(selector))
                        .sorted()
                        .toList();
            }

            System.out.println(
                    "PERF042_TIER" + requestedTier + "_BGV_COUNT=" + graphFiles.size());

            for (Path graph : graphFiles) {
                System.out.println(
                        "PERF042_TIER" + requestedTier + "_BGV="
                                + graph.toAbsolutePath());
            }

            System.out.println(
                    "PERF042_TIER" + requestedTier + "_CAPTURE="
                            + (graphFiles.isEmpty()
                                    ? "NO_ATTRIBUTED_BGV"
                                    : "READY"));
        });
    }


    /**
     * PERF042: full primitive-closure-call workload.
     *
     * Captures the lowerer's complete run root, with the nested identity
     * present in its source. Prints all emitted Bytecode DSL root signatures
     * and compiles run itself at the requested Graal tier.
     *
     * No handwritten run substitute and no graph equality assertions.
     */
    @Test
    void captureCompleteRunRootAtRequestedTier() throws Exception {
        if (Boolean.getBoolean("protos.perf042.builderNoFallback")) {
            captureBareIdentityNoFallbackTier1();
            return;
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Boolean.getBoolean("protos.perf042.graphCapture"),
                "PERF042 graph acquisition is opt-in");

        assertEquals(
                "false",
                System.getProperty("polyglot.engine.BackgroundCompilation"),
                "Require synchronous compilation");
        assertEquals(
                "true",
                System.getProperty("protos.diagnostic.stableRootIdentity"),
                "Require stable root identity");

        String tierText = System.getProperty(
                "protos.perf042.graphTier", "1");

        assertTrue(
                tierText.equals("1") || tierText.equals("2"),
                "graphTier must be 1 or 2");

        int requestedTier = Integer.parseInt(tierText);

        // Same function body as protos-benchmarks primitive-closure-call.
        // The final 'run' only retrieves the Closure for the harness.
        String sourceText = """
                run: () => {
                    identity: (value) => { value }
                    identity(1)
                }
                run
                """;

        withCore(module -> {
            assertEquals(ProtosExecutionMode.NORMAL,
                    ProtosLanguageContext.current().executionMode(),
                    "PERF042 must run in NORMAL mode");
            ProtosClosureValue run = assertInstanceOf(
                    ProtosClosureValue.class,
                    parse(sourceText, "complete-run")
                            .getCallTarget().call(module));

            ProtosSemanticBytecodeRootNode root =
                    Boolean.getBoolean("protos.perf042.builderNoFallback")
                            ? handwrittenCompleteRunWithoutFallback(module)
                            : run.executionPlan().orElseThrow()
                                    .bytecodeActivationRootForTesting();

            System.out.println(
                    "PERF042_RUN_BUILDER_MODE="
                    + (Boolean.getBoolean("protos.perf042.builderNoFallback")
                            ? "INLINE_NO_FALLBACK"
                            : "PRODUCTION"));


            Object target = root.getCallTarget();

            assertTrue(
                    perf042OptimizedCallTarget(target.getClass()),
                    "Requires optimizing Truffle runtime");

            assertTrue(
                    target == root.getCallTarget(),
                    "Must compile the real run call target");

            String targetName = root.toString();
            System.out.println("PERF042_RUN_TIER=" + requestedTier);
            System.out.println("PERF042_RUN_TARGET=" + targetName);

            assertTrue(
                    targetName.startsWith("protos-root:")
                            && !targetName.contains("unavailable"),
                    "Unattributable run root: " + targetName);

            // Execute run through its actual compact B5 Closure ABI.
            // identity(1) is executed inside run, as in the benchmark.
            int calls = 0;

            while (calls < 128
                    && root.getBytecodeNode().getTier()
                            != com.oracle.truffle.api.bytecode.BytecodeTier.CACHED) {

                Object[] arguments =
                        ProtosFrameArguments.compactDirectClosureCall(
                                run, module, null, new Object[0]);

                Object result = root.getCallTarget().call(arguments);

                assertEquals(
                        "1",
                        ProtosNumericValueSupport.integerDecimalText(result),
                        "Full run must return identity(1)");

                calls++;
            }

            assertEquals(
                    com.oracle.truffle.api.bytecode.BytecodeTier.CACHED,
                    root.getBytecodeNode().getTier(),
                    "run did not reach cached interpreter");

            System.out.println(
                    "PERF042_RUN_WARMUP_CALLS=" + calls);
            System.out.println(
                    "PERF042_RUN_BYTECODE_TIER="
                            + root.getBytecodeNode().getTier());

            // Explicit Tier 1 or Tier 2 compilation of run itself.
            boolean compiled = (Boolean) perf042CompilerMethod(
                    target, "compile", requestedTier == 2);

            Object actualTier = perf042CompilerMethod(
                    target, "highestCompiledTier");

            System.out.println(
                    "PERF042_RUN_COMPILE_RETURN=" + compiled);
            System.out.println(
                    "PERF042_RUN_COMPILED_TIER=" + actualTier);

            assertTrue(compiled, "run compilation was not completed");
            assertEquals(
                    requestedTier,
                    actualTier,
                    "Unexpected compiler tier");

            assertTrue(
                    (Boolean) perf042CompilerMethod(target, "isValid"),
                    "Compiled run target is invalid");

            // Retain the native Graal graphs, independently for each tier.
            String dumpText =
                    System.getProperty("jdk.graal.DumpPath", "");

            assertTrue(
                    !dumpText.isBlank(),
                    "Specify jdk.graal.DumpPath");

            Path dumpPath = Path.of(dumpText);

            assertTrue(
                    java.nio.file.Files.isDirectory(dumpPath),
                    "Graal dump directory does not exist");

            String selector = targetName.split("\\[", 2)[0];

            List<Path> graphs;

            try (var files = java.nio.file.Files.walk(dumpPath)) {
                graphs = files
                        .filter(java.nio.file.Files::isRegularFile)
                        .filter(file ->
                                file.getFileName().toString()
                                        .endsWith(".bgv"))
                        .filter(file ->
                                file.getFileName().toString()
                                        .contains(selector))
                        .sorted()
                        .toList();
            }

            System.out.println(
                    "PERF042_RUN_BGV_COUNT=" + graphs.size());

            for (Path graph : graphs) {
                System.out.println(
                        "PERF042_RUN_BGV=" + graph.toAbsolutePath());
            }

            System.out.println(
                    "PERF042_RUN_CAPTURE="
                            + (graphs.isEmpty()
                                    ? "NO_ATTRIBUTED_BGV"
                                    : "READY"));

            assertTrue(
                    !graphs.isEmpty(),
                    "No BGV attributed to the compiled run root");

            // Inspect DEFAULT only after Graal compilation and BGV capture.
            // Never request COMPLETE on this compiled root.
            List<String> defaultInstructions = root.getBytecodeNode()
                    .getInstructionsAsList().stream()
                    .map(Instruction::getName).toList();

            long defaultTrace = defaultInstructions.stream()
                    .filter(name -> name.startsWith("trace.")).count();
            long defaultTag = defaultInstructions.stream()
                    .filter(name -> name.startsWith("tag.")).count();

            System.out.println("PERF042_RUN_DEFAULT_INSTRUCTIONS="
                    + defaultInstructions.size());
            System.out.println("PERF042_RUN_DEFAULT_SEMANTIC="
                    + (defaultInstructions.size() - defaultTrace - defaultTag));
            System.out.println("PERF042_RUN_DEFAULT_TRACE=" + defaultTrace);
            System.out.println("PERF042_RUN_DEFAULT_TAG=" + defaultTag);
            System.out.println("PERF042_RUN_DEFAULT_BYTECODE="
                    + defaultInstructions);

            assertEquals(0L, defaultTrace,
                    "DEFAULT should not enable instruction tracing");
            assertEquals(0L, defaultTag,
                    "DEFAULT should not enable tag instrumentation");

        });

        // Independent Context: COMPLETE must not alter the compiled root.
        withCore(module -> {
            assertEquals(ProtosExecutionMode.NORMAL,
                    ProtosLanguageContext.current().executionMode(),
                    "COMPLETE inspection must also use NORMAL");

            ProtosClosureValue run = assertInstanceOf(
                    ProtosClosureValue.class,
                    parse(sourceText, "complete-run-inspection")
                            .getCallTarget().call(module));

            ProtosSemanticBytecodeRootNode root =
                    run.executionPlan().orElseThrow()
                            .bytecodeActivationRootForTesting();

            root.getRootNodes().ensureComplete();

            System.out.println(
                    "PERF042_RUN_INSPECTION_PROFILE=COMPLETE_ISOLATED");

            // Bytecode DSL emitted by the real lowerer for run.
            List<String> runBytecode = signature(root);

            long semanticInstructions = root.getBytecodeNode()
                    .getInstructionsAsList()
                    .stream()
                    .map(Instruction::getName)
                    .filter(name -> !name.startsWith("trace."))
                    .filter(name -> !name.startsWith("tag."))
                    .count();

            System.out.println("PERF042_RUN_COMPLETE_TARGET=" + root);
            System.out.println(
                    "PERF042_RUN_LOWERER_INSTRUCTIONS="
                            + runBytecode.size());
            System.out.println(
                    "PERF042_RUN_LOWERER_SEMANTIC_INSTRUCTIONS="
                            + semanticInstructions);
            System.out.println(
                    "PERF042_RUN_LOWERER_BYTECODE=" + runBytecode);

            // The source contains the module, run and nested identity.
            // Report every physical root separately. Do not sum their
            // instruction counts and call that the IR of run.
            var group = root.getRootNodes().getNodes();

            System.out.println(
                    "PERF042_RUN_GROUP_ROOTS=" + group.size());

            for (int index = 0; index < group.size(); index++) {
                ProtosSemanticBytecodeRootNode member =
                        assertInstanceOf(
                                ProtosSemanticBytecodeRootNode.class,
                                group.get(index));

                List<String> emitted = signature(member);
                String prefix = "PERF042_RUN_GROUP_" + index;

                System.out.println(
                        prefix + "_TARGET=" + member);
                System.out.println(
                        prefix + "_IS_RUN=" + (member == root));
                System.out.println(
                        prefix + "_INSTRUCTIONS=" + emitted.size());
                System.out.println(
                        prefix + "_BYTECODE=" + emitted);
            }

        });
    }


    /**
     * PERF042: independent handwritten run oracle for the admitted I096 C1
     * path. Creates a real identity Closure on every invocation, performs
     * canonical call preparation, binds the supplied value to a Bytecode
     * local and returns it.
     *
     * The admission miss is deliberately NOT implemented as a physical
     * fallback. This is a performance lower bound, not a replacement
     * lowering for dynamically overridable Protos calls.
     *
     * The identity body plan is taken from the separately parsed genuine
     * identity Closure; its creation and caller execution use real Protos
     * operations. No production code is modified.
     */
    /**
     * Bare two-statement TOP_LEVEL oracle.
     * Real Closure creation and direct Call1; no generic fallback.
     */
    private static ProtosSemanticBytecodeRootNode
            handwrittenCompleteRunWithoutFallback(ProtosActivation module) {

        ProtosClosureValue identity = assertInstanceOf(
                ProtosClosureValue.class,
                parse("(value) => { value }", "bare-identity-template")
                        .getCallTarget().call(module));

        var definition = java.util.Objects.requireNonNull(
                identity.definition(), "identity definition");

        ProtosClosureExecutionPlanCell cell =
                ProtosClosureExecutionPlanCell.pendingGroup();
        cell.freeze(identity.executionPlan().orElseThrow());

        String code = "identity: (value) => { value }\nidentity(1)\n";
        Source oracleSource = source(code, "bare-no-fallback");

        BytecodeRootNodes<ProtosSemanticBytecodeRootNode> roots =
                ProtosSemanticBytecodeRootNodeGen.create(
                        LANGUAGE.get(null),
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginSource(oracleSource);
                            builder.beginSourceSection(0, code.length());
                            builder.beginRoot();

                            BytecodeLocal identityLocal =
                                    builder.createLocal("identity", null);
                            BytecodeLocal creation =
                                    builder.createLocal("creation", null);

                            builder.beginStoreLocal(identityLocal);
                            builder.beginMaterializeClosure();
                            builder.emitCurrentActivation();
                            builder.emitLoadConstant(definition);
                            builder.emitLoadConstant(cell);
                            builder.endMaterializeClosure();
                            builder.endStoreLocal();

                            // Establish the actual top-level binding.
                            builder.beginStoreLocal(creation);
                            builder.beginCreateCurrentLocalSlot();
                            builder.emitCurrentActivation();
                            builder.emitLoadConstant("identity");
                            builder.emitLoadLocal(identityLocal);
                            builder.endCreateCurrentLocalSlot();
                            builder.endStoreLocal();

                            // Direct Call1 only. No general fallback.
                            builder.beginReturn();
                            builder.beginTryDirectCallOne();
                            builder.emitLoadLocal(identityLocal);
                            builder.emitCurrentCallerReference();
                            builder.emitLoadConstant(
                                    ProtosNumericValueSupport.integer(1L));
                            builder.endTryDirectCallOne();
                            builder.endReturn();

                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });

        ProtosSemanticBytecodeRootNode root = roots.getNode(0);

        if (ProtosDiagnosticRootIdentity.ENABLED) {
            ProtosDiagnosticRootIdentity.record(
                    root,
                    new ProtosDiagnosticRootIdentity.Span(
                            oracleSource,
                            0,
                            code.length(),
                            ProtosDiagnosticRootIdentity.TOP_LEVEL));
        }

        assertEquals(1, roots.getNodes().size());
        return root;
    }

    private static Object perf042BareInvoke(
            Object target, String name, Object... args)
            throws Exception {
        for (Class<?> type = target.getClass(); type != null;
                type = type.getSuperclass()) {
            for (java.lang.reflect.Method m : type.getDeclaredMethods()) {
                if (m.getName().equals(name)
                        && m.getParameterCount() == args.length) {
                    m.setAccessible(true);
                    try {
                        return m.invoke(target, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw new AssertionError(name + " failed", e.getCause());
                    }
                }
            }
        }
        throw new AssertionError("Missing compiler method: " + name);
    }

    private static void captureBareIdentityNoFallbackTier1()
            throws Exception {

        assertEquals("1", System.getProperty("protos.perf042.graphTier"),
                "Bare Builder witness is Tier 1 only");

        withCore(module -> {
            assertEquals(
                    ProtosExecutionMode.NORMAL,
                    ProtosLanguageContext.current().executionMode());

            ProtosSemanticBytecodeRootNode root =
                    handwrittenCompleteRunWithoutFallback(module);

            RootCallTarget target = root.getCallTarget();

            System.out.println(
                    "PERF042_RUN_BUILDER_MODE=BARE_TOP_LEVEL_NO_FALLBACK");
            System.out.println("PERF042_RUN_TIER=1");
            System.out.println("PERF042_RUN_TARGET=" + root);

            // TryDirectCallOne excludes fast specializations in UNCACHED.
            // Initial executions can return MISS by design.
            int uncachedCalls = 0;
            while (uncachedCalls < 32
                    && !"CACHED".equals(
                            root.getBytecodeNode().getTier().name())) {
                target.call(
                        module.prelude().orElseThrow()
                                .newModuleActivation());
                uncachedCalls++;
            }

            assertEquals(
                    "CACHED",
                    root.getBytecodeNode().getTier().name(),
                    "Builder did not transition to CACHED");

            // Now direct Call1 must be admitted: no fallback exists.
            int cachedCalls = 8;
            for (int i = 0; i < cachedCalls; i++) {
                Object value = target.call(
                        module.prelude().orElseThrow()
                                .newModuleActivation());

                assertTrue(
                        !ProtosSemanticBytecodeRootNode
                                .IsDirectSendMiss.perform(value),
                        "TryDirectCallOne returned MISS in CACHED");

                assertEquals(
                        "1",
                        ProtosNumericValueSupport.integerDecimalText(value),
                        "Cached direct Call1 must return identity(1)");
            }

            System.out.println(
                    "PERF042_RUN_UNCACHED_CALLS=" + uncachedCalls);
            System.out.println(
                    "PERF042_RUN_CACHED_CALLS=" + cachedCalls);
            System.out.println(
                    "PERF042_RUN_WARMUP_CALLS="
                            + (uncachedCalls + cachedCalls));

            assertEquals(
                    Boolean.TRUE, perf042BareInvoke(target, "compile", false));

            assertEquals(
                    1,
                    ((Number) perf042BareInvoke(
                            target, "highestCompiledTier")).intValue());

            assertEquals(
                    Boolean.TRUE, perf042BareInvoke(target, "isValid"));

            System.out.println("PERF042_RUN_COMPILED_TIER=1");

            List<String> instructions =
                    root.getBytecodeNode()
                            .getInstructionsAsList().stream()
                            .map(Instruction::getName)
                            .toList();

            long trace = instructions.stream()
                    .filter(n -> n.startsWith("trace.")).count();
            long tags = instructions.stream()
                    .filter(n -> n.startsWith("tag.")).count();

            assertEquals(0L, trace);
            assertEquals(0L, tags);
            assertTrue(instructions.contains("c.TryDirectCallOne"));

            assertTrue(instructions.stream().noneMatch(n ->
                    n.contains("Fallback")
                    || n.equals("yield")
                    || n.contains("ResumeContinuation")
                    || n.contains("EnterClosureCall")));

            System.out.println("PERF042_RUN_DEFAULT_INSTRUCTIONS="
                    + instructions.size());
            System.out.println("PERF042_RUN_DEFAULT_SEMANTIC="
                    + instructions.size());
            System.out.println("PERF042_RUN_DEFAULT_TRACE=" + trace);
            System.out.println("PERF042_RUN_DEFAULT_TAG=" + tags);
            System.out.println("PERF042_RUN_CAPTURE=READY");
            System.out.println("PERF042_BARE_PROGRAM_NO_FALLBACK=PASS");
        });
    }

    private static boolean perf042OptimizedCallTarget(Class<?> type) {
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

    private static Object perf042CompilerMethod(
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
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(
                    "Cannot invoke OptimizedCallTarget." + name,
                    error);
        }
    }

    private static void printIdentityBytecode(
            String label,
            ProtosSemanticBytecodeRootNode root) {

        // Same complete DSL instruction signature used by the I101 oracles.
        List<String> ir = signature(root);

        System.out.println(
                "PERF042_IDENTITY_" + label + "=" + ir);
        System.out.println(
                "PERF042_IDENTITY_" + label
                        + "_INSTRUCTIONS=" + ir.size());
    }

    /**
     * Physical execution prefix, excluding optional tracing/tagging.
     * Stop at the first return so ensureComplete()'s unreachable local
     * cleanup epilogue is not mistaken for executed body operations.
     */
    private static List<String> executionPrefixNames(
            ProtosSemanticBytecodeRootNode root) {
        root.getRootNodes().ensureComplete();

        List<String> names = new ArrayList<>();
        for (Instruction instruction :
                root.getBytecodeNode().getInstructionsAsList()) {
            String name = instruction.getName();

            if (name.startsWith("trace.")
                    || name.startsWith("tag.")) {
                continue;
            }

            names.add(name);
            if (name.equals("return")) {
                break;
            }
        }
        return names;
    }

    private static List<String> signature(
            ProtosSemanticBytecodeRootNode root) {
        root.getRootNodes().ensureComplete();

        List<String> result = new ArrayList<>();

        for (Instruction instruction :
                root.getBytecodeNode().getInstructionsAsList()) {
            StringBuilder entry =
                    new StringBuilder(instruction.getName());

            for (Instruction.Argument argument :
                    instruction.getArguments()) {
                entry.append(' ')
                        .append(argument.getName())
                        .append(':')
                        .append(argument.getKind())
                        .append('=')
                        .append(operand(argument));
            }
            result.add(entry.toString());
        }
        return result;
    }

    private static String operand(Instruction.Argument argument) {
        return switch (argument.getKind()) {
            case CONSTANT -> {
                Object value = argument.asConstant();
                if (value == null) {
                    yield "host-null";
                }
                if (value instanceof Number || value instanceof String
                        || value instanceof Boolean) {
                    yield value.getClass().getSimpleName()
                            + "(" + value + ")";
                }
                yield "Metadata(" + value.getClass().getName() + ")";
            }
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
                                    .map(Class::getSimpleName)
                                    .sorted().toList().toString();
            case NODE_PROFILE, BRANCH_PROFILE -> "profile";
        };
    }

    private static Source source(String code, String label) {
        return Source.newBuilder(
                ProtosLanguage.ID, code,
                "perf042-" + label + ".protos").build();
    }

    private static ProtosSemanticBytecodeRootNode parse(
            String code, String label) {
        RootCallTarget target = assertInstanceOf(
                RootCallTarget.class,
                ProtosLanguageContext.current().parsePublic(
                        source(code, label)));
        return assertInstanceOf(
                ProtosSemanticBytecodeRootNode.class,
                target.getRootNode());
    }

    @FunctionalInterface
    private interface CoreTest {
        void run(ProtosActivation module) throws Exception;
    }

    private static void withCore(CoreTest test) throws Exception {
        try (Context context =
                Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude =
                        new ProtosCoreBootstrap().bootstrap(CORE);
                test.run(prelude.newModuleActivation());
            } finally {
                context.leave();
            }
        }
    }

    /**
     * PERF042 minimal two-root experiment.
     *
     * The local function is statically resolved. No Closure materialization,
     * lexical authority, dynamic call selection, Protos ABI, or fallback.
     */
    @Test
    void captureMinimalKnownIdentityCallTier1() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Boolean.getBoolean("protos.perf042.graphCapture"));

        assertEquals("1", System.getProperty("protos.perf042.graphTier"));
        assertEquals("false",
                System.getProperty("polyglot.engine.BackgroundCompilation"));
        assertEquals("true",
                System.getProperty("protos.diagnostic.stableRootIdentity"));

        withCore(module -> {
            assertEquals(ProtosExecutionMode.NORMAL,
                    ProtosLanguageContext.current().executionMode());

            String calleeCode = "(value) => { value }";
            Source calleeSource =
                    source(calleeCode, "minimal-identity-body");

            BytecodeRootNodes<ProtosSemanticBytecodeRootNode> calleeRoots =
                    ProtosSemanticBytecodeRootNodeGen.create(
                            LANGUAGE.get(null),
                            BytecodeConfig.DEFAULT,
                            builder -> {
                                builder.beginSource(calleeSource);
                                builder.beginSourceSection(
                                        0, calleeCode.length());
                                builder.beginRoot();

                                builder.beginReturn();
                                builder.emitLoadArgument(0);
                                builder.endReturn();

                                builder.endRoot();
                                builder.endSourceSection();
                                builder.endSource();
                            });

            assertEquals(1, calleeRoots.getNodes().size());

            RootCallTarget identity =
                    calleeRoots.getNode(0).getCallTarget();

            String code =
                    "identity: (value) => { value }\\n"
                    + "identity(1)\\n";

            Source callerSource =
                    source(code, "minimal-known-call");

            BytecodeRootNodes<ProtosSemanticBytecodeRootNode> callerRoots =
                    ProtosSemanticBytecodeRootNodeGen.create(
                            LANGUAGE.get(null),
                            BytecodeConfig.DEFAULT,
                            builder -> {
                                builder.beginSource(callerSource);
                                builder.beginSourceSection(
                                        0, code.length());
                                builder.beginRoot();

                                builder.beginReturn();
                                builder.beginPerf042KnownIdentityCallOne(
                                        identity);
                                builder.emitLoadConstant(1L);
                                builder.endPerf042KnownIdentityCallOne();
                                builder.endReturn();

                                builder.endRoot();
                                builder.endSourceSection();
                                builder.endSource();
                            });

            assertEquals(1, callerRoots.getNodes().size());

            ProtosSemanticBytecodeRootNode root =
                    callerRoots.getNode(0);

            if (ProtosDiagnosticRootIdentity.ENABLED) {
                ProtosDiagnosticRootIdentity.record(
                        root,
                        new ProtosDiagnosticRootIdentity.Span(
                                callerSource,
                                0,
                                code.length(),
                                ProtosDiagnosticRootIdentity.TOP_LEVEL));
            }

            RootCallTarget target = root.getCallTarget();

            System.out.println(
                    "PERF042_MINIMAL_MODE=STATIC_DIRECT_CALL");
            System.out.println(
                    "PERF042_MINIMAL_TARGET=" + root);

            int warmup = 0;

            while (warmup < 32) {
                Object result = target.call(module);

                assertEquals(1L, assertInstanceOf(Long.class, result).longValue());

                warmup++;

                if ("CACHED".equals(
                        root.getBytecodeNode().getTier().name())) {
                    break;
                }
            }

            assertEquals(
                    "CACHED",
                    root.getBytecodeNode().getTier().name());

            for (int i = 0; i < 8; i++) {
                Object result = target.call(module);

                assertEquals(1L, assertInstanceOf(Long.class, result).longValue());
            }

            System.out.println(
                    "PERF042_MINIMAL_WARMUP_CALLS=" + warmup);

            assertEquals(
                    Boolean.TRUE,
                    perf042MinimalInvoke(target, "compile", false));

            assertEquals(
                    1,
                    ((Number) perf042MinimalInvoke(
                            target, "highestCompiledTier")).intValue());

            assertEquals(
                    Boolean.TRUE,
                    perf042MinimalInvoke(target, "isValid"));

            List<String> callerInstructions =
                    root.getBytecodeNode()
                            .getInstructionsAsList().stream()
                            .map(Instruction::getName)
                            .toList();

            List<String> calleeInstructions =
                    calleeRoots.getNode(0)
                            .getBytecodeNode()
                            .getInstructionsAsList().stream()
                            .map(Instruction::getName)
                            .toList();

            assertEquals(
                    1,
                    callerInstructions.stream()
                            .filter(n -> n.contains(
                                    "Perf042KnownIdentityCallOne"))
                            .count());

            assertTrue(callerInstructions.stream().noneMatch(n ->
                    n.contains("Fallback")
                    || n.contains("TryDirect")
                    || n.contains("Guard")
                    || n.contains("MaterializeClosure")
                    || n.equals("yield")
                    || n.startsWith("trace.")
                    || n.startsWith("tag.")));

            System.out.println(
                    "PERF042_MINIMAL_CALLER_INSTRUCTIONS="
                    + callerInstructions.size());
            System.out.println(
                    "PERF042_MINIMAL_CALLER_BYTECODE="
                    + callerInstructions);
            System.out.println(
                    "PERF042_MINIMAL_CALLEE_INSTRUCTIONS="
                    + calleeInstructions.size());
            System.out.println(
                    "PERF042_MINIMAL_CALLEE_BYTECODE="
                    + calleeInstructions);
            System.out.println(
                    "PERF042_MINIMAL_COMPILED_TIER=1");
            System.out.println(
                    "PERF042_MINIMAL_CAPTURE=READY");
        });
    }

    private static Object perf042MinimalInvoke(
            Object target, String name, Object... arguments)
            throws Exception {

        for (Class<?> type = target.getClass();
                type != null;
                type = type.getSuperclass()) {

            for (java.lang.reflect.Method method :
                    type.getDeclaredMethods()) {

                if (!method.getName().equals(name)
                        || method.getParameterCount()
                                != arguments.length) {
                    continue;
                }

                method.setAccessible(true);

                try {
                    return method.invoke(target, arguments);
                } catch (
                        java.lang.reflect.InvocationTargetException e) {
                    throw new AssertionError(
                            "Compiler method failed: " + name,
                            e.getCause());
                }
            }
        }

        throw new AssertionError(
                "Compiler method not found: " + name);
    }

}

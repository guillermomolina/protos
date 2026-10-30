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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
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

    private static final String IDENTITY =
            "identity: (value) => { value }\nidentity";

    @Test
    void identityBodyComparedWithBoundBuilder() throws Exception {
        withCore(module -> {
            ProtosClosureValue closure = assertInstanceOf(
                    ProtosClosureValue.class,
                    parse(IDENTITY, "identity-source")
                            .getCallTarget().call(module));

            ProtosBytecodeRootNode actual =
                    closure.executionPlan().orElseThrow()
                            .bytecodeActivationRootForTesting();

            ProtosBytecodeRootNode bound =
                    handwrittenIdentity();

            assertEquals(1, bound.getRootNodes().getNodes().size());

            // El oráculo usa el ABI B5 real y establece el binding.
            Object marker = new Object();
            assertSame(marker,
                    invokeOracle(bound, closure, module, marker));

            // Verificar también la semántica de la Closure real.
            ProtosActivation invocation = module;
            invocation.context().createLocalSlot("candidate", closure);

            assertSame(closure, parse(
                    "candidate(candidate)",
                    "identity-object").getCallTarget().call(invocation));

            Object result = parse(
                    "candidate(42)",
                    "identity-value").getCallTarget().call(invocation);

            assertEquals("42",
                    ProtosNumericValueSupport.integerDecimalText(result));

            // PERF042 remains an independent physical lower bound.
            // It does not implement D203 missing-argument, lexical-presence
            // or primitive-carrier semantics, so exact equality with the
            // production root is not a valid acceptance criterion.
            report("BOUND_ARGUMENT", bound, actual);

            // I112: the generated prologue and return epilogue belong to
            // every semantic root, the handwritten bound included.
            assertEquals(
                    List.of(
                            "c.EnterRoot",
                            "load.argument",
                            "store.local",
                            "load.local",
                            "c.EpilogForReturn",
                            "return"),
                    executionPrefixNames(bound),
                    "PERF042 handwritten lower-bound Builder");

            // I104-A has its own independent complete Builder oracle
            // in ProtosI101ProfileSpecializedLoweringTest. Here we
            // separately pin the physical unary entry operations.
            /*
             * I115-H7 EXPERIMENTAL: identity is an admitted non-dispatching
             * compact unary root. Its prologue is the GraalPy-shaped
             * CopyArguments and its body a plain LoadLocal (any quickened
             * variant), replacing the I104-A BindUnaryFrameParameter and
             * ReadRootFrameLocal shape. The return epilogue still owns the
             * carrier projection.
             */
            assertEquals(
                    List.of(
                            "c.EnterRoot",
                            "c.CopyArguments",
                            "load.local",
                            "c.EpilogForReturn",
                            "return"),
                    executionPrefixNames(actual).stream()
                            .map(name -> name.startsWith("load.local")
                                            && !name.contains(".mat")
                                    ? "load.local"
                                    : name)
                            .toList(),
                    "I115-H7 compact unary entry: CopyArguments and a direct "
                            + "local read; D203/I105 emits no excess-argument check");

            System.out.println("PERF042_BOUND_ARGUMENT_LOWER_BOUND=PASS");
            System.out.println("PERF042_I115_H7_BYTECODE_SHAPE=PASS");
            System.out.println("PERF042_IDENTITY_SEMANTICS=PASS");
        });
    }

    /**
     * PERF042: oráculo con binding explícito del parámetro.
     *
     * Graal podrá eliminar el store/load si demuestra que no
     * son observables. El lowerer conserva el binding.
     */
    private static ProtosBytecodeRootNode handwrittenIdentity() {
        String code = "(value) => { value }";
        Source source = source(code, "identity-oracle");

        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
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
                            builder.beginTag(StandardTags.RootTag.class);
                            builder.beginBlock();

                            builder.beginStoreLocal(value);
                            builder.emitLoadArgument(
                                    ProtosFrameArguments.SUPPLIED_ARGUMENT_OFFSET);
                            builder.endStoreLocal();

                            builder.beginReturn();
                            builder.emitLoadLocal(value);
                            builder.endReturn();

                            builder.endBlock();
                            builder.endTag(StandardTags.RootTag.class);
                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });

        return roots.getNode(0);
    }

    private static Object invokeOracle(
            ProtosBytecodeRootNode root,
            ProtosClosureValue closure,
            ProtosActivation caller,
            Object value) {
        // I102 / PLAT060-B5: use a real compact direct-call header,
        // not five null placeholders. The first positional value is [5].
        assertEquals(5, ProtosFrameArguments.HEADER_LENGTH);
        assertEquals(ProtosFrameArguments.HEADER_LENGTH,
                ProtosFrameArguments.SUPPLIED_ARGUMENT_OFFSET);

        Object[] arguments = ProtosFrameArguments.compactDirectClosureCallOne(
                closure, caller, null, value);

        assertEquals(ProtosFrameArguments.SUPPLIED_ARGUMENT_OFFSET + 1,
                arguments.length);
        assertSame(closure, arguments[0]);
        assertSame(caller, arguments[3]);
        assertNotNull(arguments[4]);
        assertSame(value,
                arguments[ProtosFrameArguments.SUPPLIED_ARGUMENT_OFFSET]);

        return root.getCallTarget().call(arguments);
    }

    private static boolean report(
            String name,
            ProtosBytecodeRootNode oracle,
            ProtosBytecodeRootNode actual) {

        List<String> expected = signature(oracle);
        List<String> emitted = signature(actual);

        System.out.println("PERF042_" + name
                + "_ORACLE_COUNT=" + expected.size());
        System.out.println("PERF042_" + name
                + "_PROTOS_COUNT=" + emitted.size());
        System.out.println("PERF042_" + name
                + "_ORACLE=" + expected);
        System.out.println("PERF042_" + name
                + "_PROTOS=" + emitted);
        boolean equal = expected.equals(emitted);
        System.out.println("PERF042_" + name
                + "_BYTECODE_EQUAL=" + equal);
        return equal;
    }

    /**
     * Physical execution prefix, excluding optional tracing/tagging.
     * Stop at the first return so ensureComplete()'s unreachable local
     * cleanup epilogue is not mistaken for executed body operations.
     */
    private static List<String> executionPrefixNames(
            ProtosBytecodeRootNode root) {
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
            ProtosBytecodeRootNode root) {
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

    private static ProtosBytecodeRootNode parse(
            String code, String label) {
        RootCallTarget target = assertInstanceOf(
                RootCallTarget.class,
                ProtosLanguageContext.current().parsePublic(
                        source(code, label)));
        return assertInstanceOf(
                ProtosBytecodeRootNode.class,
                target.getRootNode());
    }

    @FunctionalInterface
    private interface CoreTest {
        void run(ProtosActivation module) throws Exception;
    }

    /*
     * An opt-in graph capture (protos.perf042.graphCapture) runs on its own
     * Engine with synchronous compilation. The experimental option is set on
     * that builder rather than as a global polyglot.engine.* system property,
     * which would also reach the Contexts the Core bootstrap opens and be
     * rejected there. Ordinary runs keep the default Context unchanged.
     */
    private static void withCore(CoreTest test) throws Exception {
        if (!Boolean.getBoolean("protos.perf042.graphCapture")) {
            try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
                runCore(context, test);
            }
            return;
        }
        try (Engine engine = Engine.newBuilder(ProtosLanguage.ID)
                        .allowExperimentalOptions(true)
                        .option("engine.BackgroundCompilation", "false")
                        .option("engine.TraceCompilation", "true")
                        .build();
                Context context = Context.newBuilder(ProtosLanguage.ID)
                        .engine(engine)
                        .build()) {
            runCore(context, test);
        }
    }

    private static void runCore(Context context, CoreTest test) throws Exception {
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
    @Test
    void bareIdentityTopLevelWithoutRun() throws Exception {
        String sourceText = """
                identity: (value) => { value }
                identity(1)
                """;

        withCore(module -> {
            ProtosBytecodeRootNode root =
                    parse(sourceText, "bare-identity-smoke");

            assertEquals(2, root.getRootNodes().getNodes().size(),
                    "Expected top-level and nested identity roots");
            assertTrue(root == root.getRootNodes().getNode(0),
                    "The root under test must be top-level");
            assertEquals("1",
                    ProtosNumericValueSupport.integerDecimalText(
                            root.getCallTarget().call(module)));
        });
    }
    /**
     * I115 / PLAT066-C: the nested run program follows GraalPy's physical
     * sequence for a non-cell local. run keeps identity in an ordinary local,
     * creates it without capturing run's context, installs no frame
     * authority, and its compact invocation never publishes an activation.
     */
    @Test
    void nestedRunKeepsCompactFrameWithOrdinaryLocals() throws Exception {
        String sourceText = """
                run: () => {
                    identity: (value) => { value }
                    identity(1)
                }
                """;

        withCore(module -> {
            ProtosBytecodeRootNode moduleRoot =
                    parse(sourceText, "i115-nested-run-ordinary-locals");

            assertEquals(3, moduleRoot.getRootNodes().getNodes().size(),
                    "Expected module, run and identity roots");

            moduleRoot.getCallTarget().call(module);

            ProtosClosureValue run = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("run").orElseThrow());
            ProtosBytecodeRootNode root =
                    run.executionPlan().orElseThrow()
                            .bytecodeActivationRootForTesting();

            List<String> names = root.getBytecodeNode().getInstructionsAsList()
                    .stream()
                    .map(com.oracle.truffle.api.bytecode.Instruction::getName)
                    .toList();
            assertEquals(1, names.stream().filter("c.InvokeClosureOneTail"::equals).count(),
                    () -> "one terminal call in run: " + names);
            assertTrue(!names.contains("c.InstallFrameLexicalAuthority"),
                    () -> "run installs frame authority: " + names);
            assertTrue(!names.contains("c.CurrentActivation"),
                    () -> "run loads its activation: " + names);
            assertEquals(1, names.stream().filter(
                            name -> name.startsWith("store.local") && !name.contains(".mat")).count(),
                    () -> "one store.local in run: " + names);
            assertEquals(1, names.stream().filter(
                            name -> name.startsWith("load.local") && !name.contains(".mat")).count(),
                    () -> "one load.local in run: " + names);
            for (String absent : List.of(
                    "c.IsCompactLocalFrame",
                    "c.CreateCurrentFrameLocal",
                    "c.ReadRootFrameLocal")) {
                assertTrue(!names.contains(absent),
                        () -> absent + " in run: " + names);
            }
            assertEquals(1, names.stream().filter("c.MaterializeCurrentClosure"::equals).count(),
                    () -> "one Closure creation in run: " + names);

            /*
             * The uncached tier executes InvokeClosureOneTail.generic, whose
             * D013 fallback resolves the exact caller and therefore publishes
             * run's activation. Only the cached tier, where the direct
             * specializations pass the compact argument array as the caller,
             * is the GraalPy-equivalent physical path asserted here.
             */
            for (int i = 0; i < 128
                    && root.getBytecodeNode().getTier() != BytecodeTier.CACHED; i++) {
                assertEquals("1",
                        ProtosNumericValueSupport.integerDecimalText(
                                root.getCallTarget().call(
                                        ProtosFrameArguments.compactDirectClosureCall(
                                                run, module, null, new Object[0]))));
            }
            assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier(),
                    "run did not enter the cached interpreter");

            for (int i = 0; i < 4; i++) {
                Object[] arguments =
                        ProtosFrameArguments.compactDirectClosureCall(
                                run, module, null, new Object[0]);
                assertEquals("1",
                        ProtosNumericValueSupport.integerDecimalText(
                                root.getCallTarget().call(arguments)));
                assertTrue(!(arguments[0] instanceof ProtosActivation),
                        "cached run published its activation");
            }
        });
    }

    @Test
    void captureIdentityRootAtRequestedTier() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean("protos.perf042.graphCapture"),
                "PERF042 Tier 1 acquisition is opt-in");
        // Synchronous compilation is configured by withCore on the capture Engine.

        assertEquals(
                "true",
                System.getProperty("protos.diagnostic.stableRootIdentity"),
                "Graph capture requires stable root identities");

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
            ProtosBytecodeRootNode sourceRoot =
                    parse("(value) => { value }", "identity-tier1-source");

            ProtosClosureValue closure = assertInstanceOf(
                    ProtosClosureValue.class,
                    sourceRoot.getCallTarget().call(module));

            ProtosBytecodeRootNode root =
                    closure.executionPlan().orElseThrow()
                            .bytecodeActivationRootForTesting();

            root.getRootNodes().ensureSourceInformation();

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
                    "PERF042_TIER" + requestedTier + "_SIDE=lowerer");
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

            assertTrue(compiled, "Compilation did not complete");
            assertEquals(requestedTier, highestTier, "Unexpected compiler tier");

            assertTrue(
                    (Boolean) perf042CompilerMethod(target, "isValid"),
                    "Compiled target is invalid");

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
    @Test
    void captureCompleteRunRootAtRequestedTier() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Boolean.getBoolean("protos.perf042.graphCapture"),
                "PERF042 graph acquisition is opt-in");

        /*
         * Graal's graph dump must be configured at JVM startup.
         * A Maven -D property visible through System.getProperty()
         * does not prove that the JVM received the option at startup.
         */
        java.util.List<String> vmArguments =
                java.lang.management.ManagementFactory
                        .getRuntimeMXBean()
                        .getInputArguments();

        assertTrue(
                vmArguments.stream().anyMatch(
                        argument ->
                                argument.startsWith("-Djdk.graal.Dump=Truffle:")),
                "Missing JVM startup option jdk.graal.Dump=Truffle:1");

        assertTrue(
                vmArguments.stream().anyMatch(
                        argument ->
                                argument.startsWith("-Djdk.graal.DumpPath=")),
                "Missing JVM startup option jdk.graal.DumpPath");

        assertTrue(
                vmArguments.contains("-Djdk.graal.PrintGraph=File"),
                "Missing JVM startup option jdk.graal.PrintGraph=File");

        // Synchronous compilation is configured by withCore on the capture Engine.
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

        // Exact bare program: no synthetic run Closure or wrapper.
        String sourceText = """
                identity: (value) => { value }
                identity(1)
                """;

        withCore(module -> {
            ProtosBytecodeRootNode root =
                    parse(sourceText, "bare-identity-program");

            assertEquals(2, root.getRootNodes().getNodes().size(),
                    "Expected the top-level root and the identity Closure");
            assertTrue(root == root.getRootNodes().getNode(0),
                    "The selected root must be the lowerer's top-level root");

            // Preserve existing PERF042_RUN_* keys for graph-capture scripts.
            System.out.println("PERF042_RUN_BUILDER_MODE=PRODUCTION_TOP_LEVEL");
            System.out.println("PERF042_RUN_SOURCE=BARE_TWO_STATEMENTS");


            Object target = root.getCallTarget();

            assertTrue(
                    perf042OptimizedCallTarget(target.getClass()),
                    "Requires optimizing Truffle runtime");

            assertTrue(
                    target == root.getCallTarget(),
                    "Must compile the real top-level call target");

            String targetName = root.toString();
            System.out.println("PERF042_RUN_TIER=" + requestedTier);
            System.out.println("PERF042_RUN_TARGET=" + targetName);

            assertTrue(
                    targetName.startsWith("protos-root:")
                            && !targetName.contains("unavailable"),
                    "Unattributable top-level root: " + targetName);

            // Execute the real top-level program, not a Closure ABI call.
            // Use a fresh module activation for each independent evaluation.
            int calls = 0;

            while (calls < 128
                    && root.getBytecodeNode().getTier()
                            != com.oracle.truffle.api.bytecode.BytecodeTier.CACHED) {

                ProtosActivation invocation =
                        module.prelude().orElseThrow().newModuleActivation();
                Object result = root.getCallTarget().call(invocation);

                assertEquals(
                        "1",
                        ProtosNumericValueSupport.integerDecimalText(result),
                        "Bare identity program must return identity(1)");

                calls++;
            }

            assertEquals(
                    com.oracle.truffle.api.bytecode.BytecodeTier.CACHED,
                    root.getBytecodeNode().getTier(),
                    "Top-level program did not reach cached interpreter");

            System.out.println(
                    "PERF042_RUN_WARMUP_CALLS=" + calls);
            System.out.println(
                    "PERF042_RUN_BYTECODE_TIER="
                            + root.getBytecodeNode().getTier());

            // Compile the real top-level target at the requested tier.
            boolean compiled = (Boolean) perf042CompilerMethod(
                    target, "compile", requestedTier == 2);

            Object actualTier = perf042CompilerMethod(
                    target, "highestCompiledTier");

            System.out.println(
                    "PERF042_RUN_COMPILE_RETURN=" + compiled);
            System.out.println(
                    "PERF042_RUN_COMPILED_TIER=" + actualTier);

            assertTrue(compiled, "Top-level compilation was not completed");
            assertEquals(
                    requestedTier,
                    actualTier,
                    "Unexpected compiler tier");

            assertTrue(
                    (Boolean) perf042CompilerMethod(target, "isValid"),
                    "Compiled top-level target is invalid");

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

            if (graphs.isEmpty()) {
                try (var files = java.nio.file.Files.walk(dumpPath)) {
                    List<Path> allBgvs = files
                            .filter(java.nio.file.Files::isRegularFile)
                            .filter(file -> file.toString().endsWith(".bgv"))
                            .sorted()
                            .toList();

                    System.out.println(
                            "PERF042_RUN_BGV_TOTAL=" + allBgvs.size());

                    allBgvs.stream()
                            .limit(10)
                            .forEach(file -> System.out.println(
                                    "PERF042_RUN_UNMATCHED_BGV="
                                            + file.toAbsolutePath()));
                }
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
                    "No BGV attributed to the compiled top-level root");

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

            ProtosBytecodeRootNode root =
                    parse(sourceText, "bare-identity-inspection");

            root.getRootNodes().ensureComplete();

            System.out.println(
                    "PERF042_RUN_INSPECTION_PROFILE=COMPLETE_ISOLATED");

            // Bytecode DSL emitted for the real top-level program.
            List<String> programBytecode = signature(root);

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
                            + programBytecode.size());
            System.out.println(
                    "PERF042_RUN_LOWERER_SEMANTIC_INSTRUCTIONS="
                            + semanticInstructions);
            System.out.println(
                    "PERF042_RUN_LOWERER_BYTECODE=" + programBytecode);

            // The source has a top-level root and one nested identity.
            // Report each physical root separately; never sum their
            // instruction counts and call that the top-level IR.
            var group = root.getRootNodes().getNodes();

            System.out.println(
                    "PERF042_RUN_GROUP_ROOTS=" + group.size());

            for (int index = 0; index < group.size(); index++) {
                ProtosBytecodeRootNode member =
                        assertInstanceOf(
                                ProtosBytecodeRootNode.class,
                                group.get(index));

                List<String> emitted = signature(member);
                String prefix = "PERF042_RUN_GROUP_" + index;

                System.out.println(
                        prefix + "_TARGET=" + member);
                System.out.println(
                        prefix + "_IS_TOP_LEVEL=" + (member == root));
                System.out.println(
                        prefix + "_INSTRUCTIONS=" + emitted.size());
                System.out.println(
                        prefix + "_BYTECODE=" + emitted);
            }

        });
    }

    /**
     * PERF042: compile the actual run Closure of primitive-closure-call.
     * The nested identity is created and called inside run, not at module
     * top level. The module root and the run root are distinct targets.
     */
    @Test
    void captureNestedRunRootAtRequestedTier() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean("protos.perf042.graphCapture"),
                "PERF042 nested-run graph acquisition is opt-in");

        assertEquals(
                "true",
                System.getProperty("protos.diagnostic.stableRootIdentity"));

        String tierText =
                System.getProperty("protos.perf042.graphTier", "1");

        assertTrue(tierText.equals("1") || tierText.equals("2"));
        int requestedTier = Integer.parseInt(tierText);

        List<String> vmArguments =
                java.lang.management.ManagementFactory
                        .getRuntimeMXBean().getInputArguments();

        assertTrue(vmArguments.stream().anyMatch(
                value -> value.startsWith("-Djdk.graal.Dump=Truffle:")));
        assertTrue(vmArguments.stream().anyMatch(
                value -> value.startsWith("-Djdk.graal.DumpPath=")));
        assertTrue(vmArguments.contains("-Djdk.graal.PrintGraph=File"));

        String sourceText = """
                run: () => {
                    identity: (value) => { value }
                    identity(1)
                }
                """;

        withCore(module -> {
            ProtosBytecodeRootNode moduleRoot =
                    parse(sourceText, "nested-run-identity-program");

            assertEquals(3, moduleRoot.getRootNodes().getNodes().size(),
                    "Expected module, run and identity roots");

            moduleRoot.getCallTarget().call(module);

            ProtosClosureValue run = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("run").orElseThrow());

            ProtosBytecodeRootNode root =
                    run.executionPlan().orElseThrow()
                            .bytecodeActivationRootForTesting();

            root.getRootNodes().ensureSourceInformation();

            assertTrue(moduleRoot != root,
                    "Must compile run, not the module");
            assertTrue(moduleRoot.getRootNodes().getNodes().contains(root),
                    "Run must belong to the parsed source root group");

            Object target = root.getCallTarget();

            assertTrue(perf042OptimizedCallTarget(target.getClass()),
                    "Requires optimizing Truffle runtime");

            String targetName = root.toString();

            assertTrue(targetName.startsWith("protos-root:")
                            && !targetName.contains("unavailable"),
                    "Unattributable run root: " + targetName);

            System.out.println("PERF042_NESTED_RUN_SOURCE=RUN_IDENTITY");
            System.out.println("PERF042_NESTED_RUN_TIER=" + requestedTier);
            System.out.println("PERF042_NESTED_RUN_TARGET=" + targetName);
            System.out.println("PERF042_NESTED_RUN_ROOT_SOURCE="
                    + root.getSourceSection());

            // Re-enter the same run definition. Each call creates a fresh
            // nested identity Closure and executes identity(1).
            for (int i = 0; i < 128; i++) {
                Object[] arguments =
                        ProtosFrameArguments.compactDirectClosureCall(
                                run, module, null, new Object[0]);

                Object result = root.getCallTarget().call(arguments);

                assertEquals("1",
                        ProtosNumericValueSupport.integerDecimalText(result));
            }

            assertEquals(BytecodeTier.CACHED,
                    root.getBytecodeNode().getTier(),
                    "Run root did not enter the cached interpreter");

            System.out.println("PERF042_NESTED_RUN_WARMUP_CALLS=128");
            System.out.println("PERF042_NESTED_RUN_BYTECODE_TIER="
                    + root.getBytecodeNode().getTier());

            boolean compiled = (Boolean) perf042CompilerMethod(
                    target, "compile", requestedTier == 2);

            Object actualTier = perf042CompilerMethod(
                    target, "highestCompiledTier");

            System.out.println(
                    "PERF042_NESTED_RUN_COMPILE_RETURN=" + compiled);
            System.out.println(
                    "PERF042_NESTED_RUN_COMPILED_TIER=" + actualTier);

            assertTrue(compiled, "Run compilation did not complete");
            assertEquals(requestedTier, actualTier);
            assertTrue(
                    (Boolean) perf042CompilerMethod(target, "isValid"));

            Path dumpPath = Path.of(
                    System.getProperty("jdk.graal.DumpPath", ""));

            assertTrue(Files.isDirectory(dumpPath));

            String selector = targetName.split("\\[", 2)[0];

            List<Path> graphs;
            try (var files = Files.walk(dumpPath)) {
                graphs = files
                        .filter(Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".bgv"))
                        .filter(file ->
                                file.getFileName().toString()
                                        .contains(selector))
                        .sorted()
                        .toList();
            }

            System.out.println(
                    "PERF042_NESTED_RUN_BGV_COUNT=" + graphs.size());

            for (Path graph : graphs) {
                System.out.println("PERF042_NESTED_RUN_BGV="
                        + graph.toAbsolutePath());
            }

            assertTrue(!graphs.isEmpty(),
                    "No BGV attributed to the compiled run root");

            System.out.println("PERF042_NESTED_RUN_CAPTURE=READY");
        });

        // Independent Context: inspect physical DEFAULT instructions
        // without rematerializing instrumented bytecode in the compiled
        // Context.
        withCore(module -> {
            ProtosBytecodeRootNode root =
                    parse(sourceText, "nested-run-identity-census");

            root.getRootNodes().ensureSourceInformation();

            var group = root.getRootNodes().getNodes();

            assertEquals(3, group.size());

            int total = 0;

            for (int index = 0; index < group.size(); index++) {
                ProtosBytecodeRootNode member = assertInstanceOf(
                        ProtosBytecodeRootNode.class, group.get(index));

                List<String> instructions =
                        member.getBytecodeNode()
                                .getInstructionsAsList()
                                .stream()
                                .map(Instruction::getName)
                                .toList();

                String prefix = "PERF042_NESTED_RUN_ROOT_" + index;

                System.out.println(prefix + "_NAME=" + member);
                System.out.println(prefix + "_SOURCE="
                        + member.getSourceSection());
                System.out.println(prefix + "_FULL_COUNT="
                        + instructions.size());
                System.out.println(prefix + "_BYTECODE=" + instructions);

                total += instructions.size();
            }

            System.out.println(
                    "PERF042_NESTED_RUN_TOTAL_FULL=" + total);
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

}

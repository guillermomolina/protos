package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I109-A / PLAT063-C: actual execution of the common untagged
 * C-prime loop with non-inline, dynamically selected callbacks.
 *
 * <p>This is distinct from PERF026-D's literal-callback inline tests.
 */
final class ProtosI109ADynamicLocalEachTest {

    private static final LanguageReference<ProtosLanguage> LANGUAGE =
            LanguageReference.create(ProtosLanguage.class);

    private record EachCase(
            String name,
            String initialization,
            String emptyInitialization,
            String parameters,
            String arguments,
            String mutation,
            List<List<String>> expected) {}

    private static final List<EachCase> CASES = List.of(
            new EachCase(
                    "Array",
                    "r: Array(1, 2)",
                    "r: Array()",
                    "element",
                    "element",
                    "r.atPut(1, 9)",
                    List.of(List.of("1"), List.of("2"))),
            new EachCase(
                    "Bytes",
                    "r: Encoding.UTF8.encode(\"ab\")",
                    "r: Encoding.UTF8.encode(\"\")",
                    "octet",
                    "octet",
                    "r.atPut(1, 0)",
                    List.of(List.of("97"), List.of("98"))),
            new EachCase(
                    "Map",
                    "r: Map()\n    r[10] = 1\n    r[20] = 2",
                    "r: Map()",
                    "key, value",
                    "key, value",
                    "r[20] = 99",
                    List.of(
                            List.of("10", "1"),
                            List.of("20", "2"))),
            new EachCase(
                    "IdentityMap",
                    "r: IdentityMap()\n    r[10] = 1\n    r[20] = 2",
                    "r: IdentityMap()",
                    "key, value",
                    "key, value",
                    "r[20] = 99",
                    List.of(
                            List.of("10", "1"),
                            List.of("20", "2"))),
            new EachCase(
                    "Environment",
                    "",
                    "",
                    "name, value",
                    "name, value",
                    "",
                    List.of(
                            List.of("A", "one"),
                            List.of("B", "two"))));

    @Test
    void dynamicCallbacksSuspendAndResumeWithoutReplay() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();

            for (EachCase each : CASES) {
                ProtosActorExecutionDomain domain =
                        new ProtosActorExecutionDomain();

                ProtosActivation module = activation(prelude, domain);
                installEnvironmentIfNeeded(
                        each, module, prelude, false);

                ProtosFutureValue gate =
                        new ProtosFutureValue(
                                prelude.futurePrototype(), domain);

                module.context().createLocalSlot("gate", gate);

                List<List<String>> visits = new ArrayList<>();
                AtomicBoolean sawCPrime = new AtomicBoolean();

                installProbe(module, visits, sawCPrime);

                String program =
                        "run: () => {\n"
                        + "    " + each.initialization() + "\n"
                        + "    callback: (" + each.parameters() + ") => {\n"
                        + "        probe(" + each.arguments() + ")\n"
                        + (each.mutation().isEmpty()
                                ? ""
                                : "        " + each.mutation() + "\n")
                        + "        gate.value()\n"
                        + "    }\n"
                        + "    result: r.each(callback)\n"
                        + "    result === r\n"
                        + "}\n"
                        + "run()\n";

                ProtosBytecodeRootNode root =
                        lowerRoot(scope.language(), program,
                                "i109-a-suspend-" + each.name());

                ProtosTask task = execute(domain, module, root);

                assertTrue(
                        domain.dispatchOne(),
                        each.name() + ": first dispatch");

                assertEquals(
                        ProtosTask.State.SUSPENDED,
                        task.state(),
                        each.name());

                assertEquals(
                        List.of(each.expected().get(0)),
                        visits,
                        each.name() + ": first visit before suspension");

                assertTrue(
                        sawCPrime.get(),
                        each.name() + ": callback must traverse C-prime");

                assertTrue(
                        gate.resolve(ProtosNullValue.INSTANCE, module),
                        each.name() + ": resolving Future");

                assertTrue(
                        domain.dispatchOne(),
                        each.name() + ": resume dispatch");

                assertEquals(
                        ProtosTask.State.COMPLETED,
                        task.state(),
                        each.name());

                assertSame(
                        ProtosBooleanValue.TRUE,
                        task.result().orElseThrow(),
                        each.name() + ": receiver identity");

                assertEquals(
                        each.expected(),
                        visits,
                        each.name() + ": no replay and stable snapshot");
            }
        }
    }

    @Test
    void dynamicCallbacksStopOnErrorAndReturnHome() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();

            for (EachCase each : CASES) {
                for (boolean error : List.of(false, true)) {
                    ProtosActorExecutionDomain domain =
                            new ProtosActorExecutionDomain();

                    ProtosActivation module = activation(prelude, domain);
                    installEnvironmentIfNeeded(
                            each, module, prelude, false);

                    List<List<String>> visits = new ArrayList<>();
                    AtomicBoolean sawCPrime = new AtomicBoolean();

                    installProbe(module, visits, sawCPrime);

                    String callbackExit =
                            error ? "Error().signal()" : "^42";

                    String run =
                            "run: () => {\n"
                            + "    " + each.initialization() + "\n"
                            + "    callback: (" + each.parameters() + ") => {\n"
                            + "        probe(" + each.arguments() + ")\n"
                            + "        " + callbackExit + "\n"
                            + "    }\n"
                            + "    r.each(callback)\n"
                            + "    100\n"
                            + "}\n";

                    String program =
                            error
                            ? run
                                + "Error.handle("
                                + "() => { run() }, "
                                + "(caught) => { 42 })\n"
                            : run + "run()\n";

                    ProtosBytecodeRootNode root =
                            lowerRoot(
                                    scope.language(),
                                    program,
                                    "i109-a-exit-" + each.name()
                                            + (error ? "-error" : "-nlr"));

                    Object result = root.getCallTarget().call(module);

                    assertEquals(
                            "42",
                            ProtosNumericValueSupport
                                    .integerDecimalText(result),
                            each.name() + " abnormal exit");

                    assertEquals(
                            List.of(each.expected().get(0)),
                            visits,
                            each.name()
                                    + ": abnormal exit must stop visits");

                    assertTrue(
                            sawCPrime.get(),
                            each.name() + ": abnormal exit via C-prime");
                }
            }
        }
    }

    @Test
    void emptyReceiversDoNotInvokeDynamicCallbacks()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();

            for (EachCase each : CASES) {
                ProtosActorExecutionDomain domain =
                        new ProtosActorExecutionDomain();

                ProtosActivation module = activation(prelude, domain);
                installEnvironmentIfNeeded(
                        each, module, prelude, true);

                List<List<String>> visits = new ArrayList<>();
                AtomicBoolean sawCPrime = new AtomicBoolean();
                installProbe(module, visits, sawCPrime);

                String program =
                        "run: () => {\n"
                        + "    " + each.emptyInitialization() + "\n"
                        + "    callback: (" + each.parameters() + ") => {\n"
                        + "        probe(" + each.arguments() + ")\n"
                        + "        Error().signal()\n"
                        + "    }\n"
                        + "    result: r.each(callback)\n"
                        + "    result === r\n"
                        + "}\n"
                        + "run()\n";

                ProtosBytecodeRootNode root =
                        lowerRoot(
                                scope.language(),
                                program,
                                "i109-a-empty-" + each.name());

                Object result = root.getCallTarget().call(module);

                assertSame(
                        ProtosBooleanValue.TRUE,
                        result,
                        each.name() + ": empty receiver identity");

                assertTrue(
                        visits.isEmpty(),
                        each.name() + ": empty callback never invoked");
            }
        }
    }


    @Test
    void normalDebugAndRepeatedParsingPreserveDynamicCPrimeEach()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();

            for (EachCase each : CASES) {
                String program =
                        "run: () => {\n"
                        + "    " + each.initialization() + "\n"
                        + "    callback: (" + each.parameters() + ") => {\n"
                        + "        probe(" + each.arguments() + ")\n"
                        + "    }\n"
                        + "    result: r.each(callback)\n"
                        + "    result === r\n"
                        + "}\n"
                        + "run()\n";

                for (ProtosExecutionMode mode :
                        List.of(
                                ProtosExecutionMode.NORMAL,
                                ProtosExecutionMode.DEBUG)) {

                    for (int iteration = 0; iteration < 2; iteration++) {
                        ProtosActorExecutionDomain domain =
                                new ProtosActorExecutionDomain();

                        ProtosActivation module =
                                activation(prelude, domain);

                        installEnvironmentIfNeeded(
                                each, module, prelude, false);

                        List<List<String>> visits = new ArrayList<>();
                        AtomicBoolean sawCPrime =
                                new AtomicBoolean();

                        installProbe(module, visits, sawCPrime);

                        ProtosBytecodeRootNode root =
                                lowerRoot(
                                        scope.language(),
                                        program,
                                        "i109-a-profile-"
                                                + each.name()
                                                + "-" + mode
                                                + "-" + iteration,
                                        mode);

                        root.getRootNodes().ensureComplete();

                        Object result =
                                root.getCallTarget().call(module);

                        String diagnostic =
                                each.name()
                                        + " / " + mode
                                        + " / parse " + iteration;

                        assertSame(
                                ProtosBooleanValue.TRUE,
                                result,
                                diagnostic + ": receiver identity");

                        assertEquals(
                                each.expected(),
                                visits,
                                diagnostic + ": callback arguments and order");

                        assertTrue(
                                sawCPrime.get(),
                                diagnostic + ": C-prime dispatcher missing");
                    }
                }
            }
        }
    }


    /**
     * I109-A: cancellation of a suspended dynamic each must unwind
     * the existing C-prime continuation, execute ensure exactly once
     * and never replay or advance to the next collection element.
     */
    @Test
    void cancellationOfSuspendedDynamicEachRunsEnsureOnce()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();

            for (EachCase each : CASES) {
                ProtosActorExecutionDomain domain =
                        new ProtosActorExecutionDomain();
                ProtosActivation module = activation(prelude, domain);

                installEnvironmentIfNeeded(
                        each, module, prelude, false);

                ProtosFutureValue gate =
                        new ProtosFutureValue(
                                prelude.futurePrototype(), domain);
                module.context().createLocalSlot("gate", gate);

                List<List<String>> visits = new ArrayList<>();
                AtomicBoolean sawCPrime = new AtomicBoolean();
                AtomicInteger cleanups = new AtomicInteger();

                installProbe(module, visits, sawCPrime);

                module.context().createLocalSlot(
                        "cleanupProbe",
                        ProtosClosureValue.nativeClosure(
                                (activation, supplied) -> {
                                    cleanups.incrementAndGet();
                                    return ProtosNullValue.INSTANCE;
                                }));

                String program =
                        "run: () => {\n"
                        + "    " + each.initialization() + "\n"
                        + "    callback: (" + each.parameters()
                        + ") => {\n"
                        + "        probe(" + each.arguments() + ")\n"
                        + "        gate.value()\n"
                        + "    }\n"
                        + "    body: () => { r.each(callback) }\n"
                        + "    cleanup: () => { cleanupProbe() }\n"
                        + "    body.ensure(cleanup)\n"
                        + "}\n"
                        + "run()\n";

                ProtosBytecodeRootNode root =
                        lowerRoot(
                                scope.language(),
                                program,
                                "i109-a-cancel-" + each.name());

                ProtosFutureValue producer =
                        new ProtosFutureValue(
                                prelude.futurePrototype(), domain);

                ProtosTask task =
                        domain.createTask(
                                null,
                                producer,
                                current ->
                                        ProtosBytecodeTaskExecution.execute(
                                                current,
                                                root.getCallTarget(),
                                                module));

                producer.attachProducerTask(task, module);

                String diagnostic = each.name() + " cancellation";

                assertTrue(domain.dispatchOne(), diagnostic);
                assertEquals(
                        ProtosTask.State.SUSPENDED,
                        task.state(), diagnostic);

                assertEquals(
                        List.of(each.expected().get(0)),
                        visits,
                        diagnostic + ": first visit");

                assertTrue(
                        sawCPrime.get(),
                        diagnostic + ": C-prime frame");

                assertEquals(
                        0, cleanups.get(),
                        diagnostic + ": no premature cleanup");

                assertTrue(
                        producer.cancelRequest(),
                        diagnostic + ": cancellation request");

                assertEquals(
                        ProtosTask.State.RUNNABLE,
                        task.state(), diagnostic);

                assertTrue(domain.dispatchOne(), diagnostic);

                assertEquals(
                        ProtosTask.State.CANCELLED,
                        task.state(), diagnostic);

                assertEquals(
                        ProtosFutureValue.State.CANCELLED,
                        producer.state(), diagnostic);

                assertEquals(
                        ProtosTask.CancellationPhase.TERMINAL,
                        task.cancellationPhase(), diagnostic);

                assertEquals(
                        1, cleanups.get(),
                        diagnostic + ": cleanup exactly once");

                assertEquals(
                        List.of(each.expected().get(0)),
                        visits,
                        diagnostic + ": no replay or later visit");

                assertEquals(
                        ProtosFutureValue.State.PENDING,
                        gate.state(), diagnostic);

                assertTrue(
                        gate.resolve(ProtosNullValue.INSTANCE, module),
                        diagnostic);

                assertFalse(
                        domain.dispatchOne(),
                        diagnostic + ": detached cancelled waiter");

                assertEquals(
                        1, cleanups.get(),
                        diagnostic + ": no repeated cleanup");
            }
        }
    }

    /**
     * I109-A: a tooling reparse must keep the same semantic roots,
     * CallTargets and instructions for all five dynamic each families
     * under NORMAL and DEBUG.
     */
    @Test
    void reparsePreservesDynamicEachRootGroupAndCallTargets()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();

            for (EachCase each : CASES) {
                String program =
                        "run: () => {\n"
                        + "    " + each.initialization() + "\n"
                        + "    callback: (" + each.parameters()
                        + ") => {\n"
                        + "        probe(" + each.arguments() + ")\n"
                        + "    }\n"
                        + "    result: r.each(callback)\n"
                        + "    result === r\n"
                        + "}\n"
                        + "run()\n";

                for (ProtosExecutionMode mode :
                        List.of(
                                ProtosExecutionMode.NORMAL,
                                ProtosExecutionMode.DEBUG)) {

                    ProtosActorExecutionDomain domain =
                            new ProtosActorExecutionDomain();
                    ProtosActivation module = activation(prelude, domain);

                    installEnvironmentIfNeeded(
                            each, module, prelude, false);

                    List<List<String>> visits = new ArrayList<>();
                    AtomicBoolean sawCPrime = new AtomicBoolean();
                    installProbe(module, visits, sawCPrime);

                    String diagnostic =
                            each.name() + " / " + mode;

                    ProtosBytecodeRootNode root =
                            lowerRoot(
                                    scope.language(),
                                    program,
                                    "i109-a-reparse-"
                                            + each.name() + "-" + mode,
                                    mode);

                    var group = root.getRootNodes();

                    List<ProtosBytecodeRootNode> before =
                            group.getNodes().stream()
                                    .map(
                                            ProtosBytecodeRootNode.class
                                                    ::cast)
                                    .toList();

                    List<com.oracle.truffle.api.RootCallTarget> targets =
                            before.stream()
                                    .map(node -> node.getCallTarget())
                                    .toList();

                    List<List<String>> instructions =
                            before.stream()
                                    .map(
                                            ProtosI109ADynamicLocalEachTest
                                                    ::i109SemanticInstructions)
                                    .toList();

                    assertTrue(
                            before.size() >= 2,
                            diagnostic + ": semantic roots");

                    assertSame(root, before.get(0), diagnostic);

                    group.ensureComplete();

                    assertSame(
                            group, root.getRootNodes(),
                            diagnostic + ": group identity");

                    List<ProtosBytecodeRootNode> after =
                            group.getNodes().stream()
                                    .map(
                                            ProtosBytecodeRootNode.class
                                                    ::cast)
                                    .toList();

                    assertEquals(
                            before.size(),
                            after.size(),
                            diagnostic + ": root count");

                    for (int index = 0;
                            index < before.size();
                            index++) {

                        ProtosBytecodeRootNode node =
                                after.get(index);

                        String detail =
                                diagnostic + " / root " + index;

                        assertSame(
                                before.get(index),
                                node,
                                detail + ": root identity");

                        assertSame(
                                targets.get(index),
                                node.getCallTarget(),
                                detail + ": CallTarget identity");

                        i109AssertInstructionsRetained(
                                instructions.get(index),
                                i109SemanticInstructions(node),
                                detail);

                        assertNotNull(
                                node.getSourceSection(),
                                detail + ": source section");

                        assertTrue(
                                i109HasRootTag(
                                        node.getBytecodeNode().getTagTree()),
                                detail + ": semantic RootTag");
                    }

                    group.ensureComplete();

                    assertSame(
                            group,
                            root.getRootNodes(),
                            diagnostic + ": repeated reparse");

                    for (int index = 0;
                            index < after.size();
                            index++) {
                        assertSame(
                                after.get(index),
                                group.getNode(index),
                                diagnostic + ": repeated root identity");
                    }

                    Object result = root.getCallTarget().call(module);

                    assertSame(
                            ProtosBooleanValue.TRUE,
                            result,
                            diagnostic + ": receiver identity");

                    assertEquals(
                            each.expected(),
                            visits,
                            diagnostic + ": ordered arguments");

                    assertTrue(
                            sawCPrime.get(),
                            diagnostic + ": C-prime after reparse");
                }
            }
        }
    }

    private static List<String> i109SemanticInstructions(
            ProtosBytecodeRootNode root) {
        return root.getBytecodeNode()
                .getInstructionsAsList()
                .stream()
                .map(Instruction::getName)
                .filter(name ->
                        !name.startsWith("tag.")
                                && !name.startsWith("trace."))
                .toList();
    }

    /**
     * ensureComplete() puede añadir exclusivamente un epílogo inalcanzable
     * de limpieza de locales: limpiezas del Block del RootTag explícito
     * (I111-A), load.null, limpiezas de locales de raíz, a lo sumo un
     * c.EpilogForReturn (I112) y return, en ese orden. El prefijo
     * semántico anterior al reparse debe mantenerse exactamente igual y en
     * el mismo orden.
     */
    private static void i109AssertInstructionsRetained(
            List<String> before,
            List<String> after,
            String diagnostic) {

        assertTrue(
                after.size() >= before.size(),
                () -> diagnostic + ": desaparecieron instrucciones");

        assertEquals(
                before,
                after.subList(0, before.size()),
                diagnostic + ": prefijo semántico modificado");

        List<String> epilogue =
                after.subList(before.size(), after.size());

        if (epilogue.isEmpty()
                || epilogue.equals(List.of("return"))) {
            return;
        }

        int position = 0;
        // I111-A: unreachable exit cleanup of the explicit RootTag Block.
        while (position < epilogue.size()
                && epilogue.get(position).equals("clear.local")) {
            position++;
        }

        assertTrue(
                position < epilogue.size(),
                () -> diagnostic
                        + ": epílogo incompleto " + epilogue);

        assertEquals(
                "load.null",
                epilogue.get(position++),
                diagnostic + ": inicio inesperado del epílogo");

        // The implicit root return clears the root-scoped locals.
        while (position < epilogue.size()
                && epilogue.get(position).equals("clear.local")) {
            position++;
        }

        // I112: every root return runs the EpilogForReturn epilogue.
        if (position < epilogue.size()
                && epilogue.get(position).equals("c.EpilogForReturn")) {
            position++;
        }

        assertEquals(
                List.of("return"),
                epilogue.subList(position, epilogue.size()),
                diagnostic + ": final inesperado del epílogo");
    }

    private static boolean i109HasRootTag(TagTree tree) {
        if (tree == null) {
            return false;
        }

        if (tree.hasTag(StandardTags.RootTag.class)) {
            return true;
        }

        for (TagTree child : tree.getTreeChildren()) {
            if (i109HasRootTag(child)) {
                return true;
            }
        }

        return false;
    }

    private static void installProbe(
            ProtosActivation module,
            List<List<String>> visits,
            AtomicBoolean sawCPrime) {
        module.context().createLocalSlot(
                "probe",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            List<String> arguments = new ArrayList<>();

                            for (Object argument : supplied) {
                                if (argument instanceof ProtosStringValue text) {
                                    arguments.add(text.value());
                                } else {
                                    arguments.add(
                                            ProtosNumericValueSupport
                                                    .integerDecimalText(argument));
                                }
                            }

                            visits.add(List.copyOf(arguments));

                            Truffle.getRuntime().iterateFrames(frame -> {
                                if (frame.getCallTarget()
                                        instanceof RootCallTarget target
                                        && target.getRootNode()
                                                instanceof ProtosBytecodeRootNode root
                                        && !root.isSemanticActivationRoot()) {
                                    sawCPrime.set(true);
                                }
                                return null;
                            });

                            return ProtosNullValue.INSTANCE;
                        }));
    }

    private static void installEnvironmentIfNeeded(
            EachCase each,
            ProtosActivation module,
            ProtosPrelude prelude,
            boolean empty) {
        if (!each.name().equals("Environment")) {
            return;
        }

        List<ProtosEnvironmentValue.NativeEntry> entries =
                empty
                ? List.of()
                : List.of(
                        new ProtosEnvironmentValue.NativeEntry("B", "two"),
                        new ProtosEnvironmentValue.NativeEntry("A", "one"));

        ProtosProcessRuntime process =
                new ProtosProcessRuntime(
                        prelude.actorRefPrototypeForRuntime());

        ProtosObjectValueHolder holder =
                new ProtosObjectValueHolder();

        assertEquals(
                ProtosProcessRuntime.EnvironmentSnapshotState.AVAILABLE,
                process.establishEnvironmentForRuntime(
                        holder.prototype,
                        exactDomain(),
                        entries));

        module.context().createLocalSlot(
                "r",
                process.environmentSnapshotForRuntime().orElseThrow());
    }

    private static final class ProtosObjectValueHolder {
        final com.guillermomolina.protos.runtime.ProtosObjectValue prototype =
                ProtosStandardEnvironmentProtocol.createPrototype();
    }

    private static ProtosEnvironmentValue.NativeNameDomain exactDomain() {
        return new ProtosEnvironmentValue.NativeNameDomain() {
            @Override
            public boolean sameCapturedName(String left, String right) {
                return left.equals(right);
            }

            @Override
            public boolean isQueryRepresentable(String name) {
                return !name.contains("=") && name.indexOf('\0') < 0;
            }

            @Override
            public boolean matchesQuery(String captured, String query) {
                return captured.equals(query);
            }
        };
    }

    private static ProtosTask execute(
            ProtosActorExecutionDomain domain,
            ProtosActivation module,
            ProtosBytecodeRootNode root) {
        return domain.createTask(
                null,
                current -> ProtosBytecodeTaskExecution.execute(
                        current,
                        root.getCallTarget(),
                        module));
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(Path.of("protos", "lib", "core"));
    }

    private static ProtosActivation activation(
            ProtosPrelude prelude,
            ProtosActorExecutionDomain domain) {
        return prelude.newModuleActivation(
                new ProtosActorModuleState(),
                null,
                prelude.newExecutionContext(),
                domain);
    }

    private static ProtosBytecodeRootNode lowerRoot(
            ProtosLanguage language,
            String characters,
            String name)
            throws Exception {
        return lowerRoot(
                language,
                characters,
                name,
                ProtosExecutionMode.NORMAL);
    }

    private static ProtosBytecodeRootNode lowerRoot(
            ProtosLanguage language,
            String characters,
            String name,
            ProtosExecutionMode mode)
            throws Exception {
        Source source =
                Source.newBuilder(
                        ProtosLanguage.ID,
                        characters,
                        name + ".protos").build();

        CanonicalSequence canonical =
                (CanonicalSequence)
                        new Canonicalizer().canonicalize(
                                new ProtosParser(characters).parseProgram());

        return new CanonicalToBytecodeLowerer(
                language,
                source,
                mode)
                .lowerRoot(canonical);
    }

    private static LanguageScope languageScope() {
        Context context =
                Context.newBuilder(ProtosLanguage.ID).build();

        context.initialize(ProtosLanguage.ID);
        context.enter();

        return new LanguageScope(context, LANGUAGE.get(null));
    }

    private record LanguageScope(
            Context context,
            ProtosLanguage language)
            implements AutoCloseable {
        @Override
        public void close() {
            context.leave();
            context.close();
        }
    }
}

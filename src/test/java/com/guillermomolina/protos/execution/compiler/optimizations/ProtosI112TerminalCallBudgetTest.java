/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE.
 * A COPY OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT"
 * ACCOMPANYING THE CONTENTS OF THIS FILE.
 */

package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.Debugger;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I112 / PLAT062-T physical acceptance.
 *
 * Counts every materialized Truffle Bytecode DSL instruction, including
 * branches, returns, cleanup and instrumentation. No execution-prefix
 * filtering, operation-name filtering or argument removal is permitted.
 *
 * PLAT067-B: every Context uses the one proof-optimized lowering. The
 * census is taken without and with an attached Truffle debugger session,
 * before any instrumentation is activated, and both must be identical.
 *
 * The source-root group is enumerated independently of the invocation
 * result. An auxiliary root in that group is an acceptance failure.
 */
final class ProtosI112TerminalCallBudgetTest {

    private static final Path CORE =
            Path.of("protos", "lib", "core");

    private static final String IDENTITY = """
            identity: (value) => { value }
            identity(1)
            """;

    private static final String NESTED = """
            identity: (value) => { value }
            run: () => { identity(1) }
            run()
            """;

    /** PLAT062-T design target: 15 enumerated FULL instructions plus one of margin. */
    private static final int TARGET_IDENTITY_FULL = 16;

    /** I112 acceptance ceiling; exceeding it is unacceptable while tuning the target. */
    private static final int CEILING_IDENTITY_FULL = 17;

    /** Physical prepared-call protocol that a terminal root must not contain. */
    private static final List<String> PREPARED_PROTOCOL = List.of(
            "c.IsPreparedClosureInvocation",
            "c.EnterStraightLineSourceCall",
            "c.EnterClosureCall",
            "c.IsContinuationForCall",
            "c.ResumeContinuation",
            "c.FinishClosureCall",
            "c.RequiresStructuredDispatch",
            "yield");

    @Test
    void normalIdentityMeetsTheTerminalFullInstructionTarget()
            throws Exception {

        try (Harness harness =
                     new Harness(false)) {

            harness.run(() -> {
                Census census = census(
                        IDENTITY, "identity-normal", 2);

                assertEquals(
                        2,
                        census.roots().size(),
                        "Module and identity must be the only source roots");

                boolean ceilingExceeded =
                        census.total() > CEILING_IDENTITY_FULL;
                System.out.println(
                        "I112_TARGET_FULL=" + TARGET_IDENTITY_FULL
                                + " I112_CEILING_FULL=" + CEILING_IDENTITY_FULL
                                + " I112_CEILING_EXCEEDED=" + ceilingExceeded);

                assertTrue(
                        !ceilingExceeded,
                        () -> "I112 acceptance ceiling exceeded (unacceptable): "
                                + census.total()
                                + " > " + CEILING_IDENTITY_FULL
                                + "\n" + census.fullReport());

                assertTrue(
                        census.total() <= TARGET_IDENTITY_FULL,
                        () -> "I112 physical target missed: "
                                + census.total()
                                + " > " + TARGET_IDENTITY_FULL
                                + "\n" + census.fullReport());

                List<String> module = census.signatures().get(0);
                List<String> identity = census.signatures().get(1);
                for (String required : List.of(
                        "c.EnterRoot",
                        "c.MaterializeCurrentClosure",
                        "c.CreateCurrentFrameLocal",
                        "c.ReadRootFrameLocal",
                        "c.InvokeClosureOneTail",
                        "c.EpilogForReturn")) {
                    assertTrue(
                            names(module).contains(required),
                            () -> required + " missing in module\n" + census.fullReport());
                }
                for (String forbidden : List.of(
                        "c.InstallFrameLexicalAuthority",
                        "c.CreateCurrentLocalSlot",
                        "c.CurrentActivation",
                        "c.CurrentCallerReference",
                        "c.Lookup",
                        "c.MaterializePrimitiveNumericCarrier")) {
                    assertTrue(
                            !names(module).contains(forbidden),
                            () -> forbidden + " in module\n" + census.fullReport());
                }
                for (String required : List.of(
                        "c.EnterRoot",
                        "c.BindUnaryFrameParameter",
                        "c.ReadRootFrameLocal",
                        "c.EpilogForReturn")) {
                    assertTrue(
                            names(identity).contains(required),
                            () -> required + " missing in identity\n" + census.fullReport());
                }
                for (List<String> signature : census.signatures()) {
                    for (String entry : signature) {
                        String name = entry.split(" ", 2)[0];
                        assertTrue(
                                !PREPARED_PROTOCOL.contains(name),
                                () -> name + " in a terminal root\n"
                                        + census.fullReport());
                    }
                }

                assertInteger(
                        1,
                        census.module().getCallTarget().call(
                                harness.moduleActivation()));
            });
        }
    }

    @Test
    void attachedDebuggerIdentityHasTheSameCompleteCensus()
            throws Exception {

        List<Census> censuses = new ArrayList<>();
        for (boolean debugger : new boolean[] {false, true}) {
            try (Harness harness = new Harness(debugger)) {
                harness.run(() -> {
                    Census census = census(
                            IDENTITY, debugger ? "identity-debugger" : "identity-plain", 2);

                    assertEquals(2, census.roots().size());
                    assertInteger(
                            1,
                            census.module().getCallTarget().call(
                                    harness.moduleActivation()));
                    censuses.add(census);
                });
            }
        }

        // PLAT067-B: attaching a debugger selects no other lowering.
        assertEquals(censuses.get(0).signatures(), censuses.get(1).signatures());
        assertEquals(censuses.get(0).total(), censuses.get(1).total());
    }

    @Test
    void nestedInvocationHasIndependentCompleteRootCensus()
            throws Exception {

        try (Harness harness =
                     new Harness(false)) {

            harness.run(() -> {
                Census census = census(
                        NESTED, "nested-normal", 3);

                assertEquals(
                        3,
                        census.roots().size(),
                        "Expected module, identity and run roots");

                assertInteger(
                        1,
                        census.module().getCallTarget().call(
                                harness.moduleActivation()));
            });
        }
    }

    private static Census census(
            String program,
            String name,
            int expectedSourceRoots) {

        Source source = Source.newBuilder(
                        ProtosLanguage.ID,
                        program,
                        "i112-" + name + ".protos")
                .build();

        RootCallTarget target = assertInstanceOf(
                RootCallTarget.class,
                ProtosLanguageContext.current().parsePublic(source));

        ProtosBytecodeRootNode module =
                assertInstanceOf(
                        ProtosBytecodeRootNode.class,
                        target.getRootNode());

        /*
         * The physical census is BytecodeConfig.DEFAULT, the configuration
         * every Context executes. ensureComplete() would materialize instrumentation
         * (trace.instruction, tag.enter/leave), which is not part of that
         * physical program; only source information is requested here.
         */
        module.getRootNodes().ensureSourceInformation();

        List<ProtosBytecodeRootNode> roots =
                module.getRootNodes().getNodes().stream()
                        .map(ProtosBytecodeRootNode.class::cast)
                        .toList();

        List<List<String>> signatures = new ArrayList<>();
        int total = 0;

        StringBuilder report = new StringBuilder();

        report.append("I112_CASE=").append(name).append('\n');
        report.append("I112_CONFIG=")
                .append("BytecodeConfig.DEFAULT; single lowering (PLAT067-B)")
                .append('\n');
        report.append("I112_ROOT_COUNT=")
                .append(roots.size())
                .append('\n');

        for (int rootIndex = 0; rootIndex < roots.size(); rootIndex++) {
            ProtosBytecodeRootNode root = roots.get(rootIndex);

            List<String> signature = fullSignature(root);
            signatures.add(signature);
            total += signature.size();

            report.append("I112_ROOT_INDEX=")
                    .append(rootIndex)
                    .append('\n');

            report.append("I112_ROOT_NAME=")
                    .append(root)
                    .append('\n');

            report.append("I112_ROOT_SOURCE=")
                    .append(root.getSourceSection())
                    .append('\n');

            report.append("I112_ROOT_TIER=")
                    .append(root.getBytecodeNode().getTier())
                    .append('\n');

            report.append("I112_ROOT_FULL_COUNT=")
                    .append(signature.size())
                    .append('\n');

            for (int instructionIndex = 0;
                    instructionIndex < signature.size();
                    instructionIndex++) {

                report.append("I112_INSTRUCTION[")
                        .append(rootIndex)
                        .append("][")
                        .append(instructionIndex)
                        .append("]=")
                        .append(signature.get(instructionIndex))
                        .append('\n');
            }
        }

        report.append("I112_TOTAL_FULL=")
                .append(total)
                .append('\n');

        report.append("I112_EXPECTED_SOURCE_ROOTS=")
                .append(expectedSourceRoots)
                .append('\n');

        report.append("I112_EXTRA_GROUP_ROOTS=")
                .append(Math.max(0, roots.size() - expectedSourceRoots))
                .append('\n');

        System.out.print(report);

        assertEquals(
                expectedSourceRoots,
                roots.size(),
                "Unexpected root topology:\n" + report);

        assertSame(
                module,
                roots.get(0),
                "The first group root must be the module");

        return new Census(
                module,
                roots,
                signatures,
                total,
                report.toString());
    }

    private static List<String> fullSignature(
            ProtosBytecodeRootNode root) {

        List<String> entries = new ArrayList<>();

        for (Instruction instruction :
                root.getBytecodeNode().getInstructionsAsList()) {

            StringBuilder entry =
                    new StringBuilder(ProtosInstructionNames.of(instruction));

            for (Instruction.Argument argument :
                    instruction.getArguments()) {

                entry.append(' ')
                        .append(argument.getName())
                        .append(':')
                        .append(argument.getKind())
                        .append('=')
                        .append(operand(argument));
            }

            entries.add(entry.toString());
        }

        return List.copyOf(entries);
    }

    private static List<String> names(List<String> signature) {
        return signature.stream().map(entry -> entry.split(" ", 2)[0]).toList();
    }

    private static String operand(
            Instruction.Argument argument) {

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
                            : argument.asTagNode()
                                    .getTags()
                                    .stream()
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

            return value.getClass().getName()
                    + "(" + value + ")";
        }

        if (value instanceof Class<?> type) {
            return "Class(" + type.getName() + ")";
        }

        return "Metadata(" + value.getClass().getName() + ")";
    }

    private static void assertInteger(
            long expected,
            Object result) {

        assertEquals(
                Long.toString(expected),
                ProtosNumericValueSupport.integerDecimalText(result));
    }

    private record Census(
            ProtosBytecodeRootNode module,
            List<ProtosBytecodeRootNode> roots,
            List<List<String>> signatures,
            int total,
            String fullReport) {
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private static final class Harness implements AutoCloseable {

        private final Context context;
        private final DebuggerSession debugger;
        private ProtosPrelude prelude;

        /** With {@code debugger}, a Truffle debugger session is attached to the Engine first. */
        Harness(boolean debugger) {

            context = Context.newBuilder(ProtosLanguage.ID).build();
            this.debugger =
                    debugger
                            ? Debugger.find(context.getEngine()).startSession(event -> {})
                            : null;
            context.initialize(ProtosLanguage.ID);
        }

        void run(Action action) throws Exception {

            context.enter();

            try {
                if (prelude == null) {
                    prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                }

                action.run();

            } finally {
                context.leave();
            }
        }

        ProtosActivation moduleActivation() {
            return prelude.newModuleActivation();
        }

        @Override
        public void close() {
            if (debugger != null) {
                debugger.close();
            }
            context.close();
        }
    }
}

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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I107 (PLAT061-B, D204-A) physical evidence: the lowerer consumes its
 * pre-builder emission plan, so a nested constant Boolean-control site whose
 * selection is complete under the published standard root folds to its proven
 * literal and loses the ordinary send, its continuation composition ({@code
 * IsContinuationForCall}, {@code Yield}, {@code ResumeContinuation}) and its
 * callback roots; the published root capability is the consumed EFFECTIVE_ROOT
 * proof. Every dynamic selection keeps its generic fallback.
 *
 * <p>Signatures carry every instruction with every argument; nothing is
 * filtered before a strict comparison. The oracle is a handwritten {@link
 * ProtosBytecodeRootNodeGen.Builder} program that never consults the
 * parser, canonicalizer, lowerer or the analysis under test.
 */
final class ProtosI107ContinuationEmissionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    private static final String NESTED =
            "() => { true.ifTrue() { false.ifFalse() { true.ifTrueIfFalse(() => { 7 }, () => { x() }) } } }";
    /** Same nesting whose innermost value is a send: no fold is proven. */
    private static final String NESTED_UNPROVEN =
            "() => { true.ifTrue() { false.ifFalse() { 7 + 0 } } }";
    /** Exact Bytecode instruction names of the continuation composition. */
    private static final List<String> CONTINUATION =
            List.of("c.IsContinuationForCall", "yield", "c.ResumeContinuation");

    @Test
    void nestedConstantControlFoldsToTheIndependentOracle() throws Exception {
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL)) {
            normal.run(() -> {
                ProtosBytecodeRootNode module = compile(NESTED, "nested");
                ProtosBytecodeRootNode activation = activationRoot(module);
                List<String> expected = completeSignature(handwrittenLiteralRoot(NESTED, 7L));
                List<String> actual = completeSignature(activation);
                System.out.println("I107_ORACLE_SIGNATURE=" + expected);
                System.out.println("I107_NESTED_SIGNATURE=" + actual);

                assertEquals(expected, actual, "nested fold must equal the Builder oracle");
                assertNoContinuation(actual);
                assertTrue(actual.stream().anyMatch(entry -> entry.contains("=Long(7)")));
                // The group: module and Closure only; no callback root was lowered.
                assertEquals(2, groupRoots(module).size());
                assertSame(activation, groupRoots(module).get(1));
                assertTrue(activation.provablyNonSuspendingBody());
                assertEquals(bodyText(NESTED),
                        activation.getSourceSection().getCharacters().toString());

                // Second generation: a full reparse keeps the identical topology.
                RootCallTarget target = activation.getCallTarget();
                module.getRootNodes().ensureComplete();
                assertEquals(actual, signature(activation));
                assertSame(target, activation.getCallTarget());
                ProtosBytecodeRootNode equivalent =
                        activationRoot(compile(NESTED, "again"));
                assertEquals(expected, completeSignature(equivalent));
                assertTrue(activation.provablyNonSuspendingBody());
                assertTrue(equivalent.provablyNonSuspendingBody());

                assertInteger(7, callClosure(normal, closureOf(normal, module)));
            });
        }
    }

    /** The previous topology of the same shape: ordinary send plus continuation. */
    @Test
    void unprovenNestingKeepsTheGenericTopology() throws Exception {
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL)) {
            normal.run(() -> {
                ProtosBytecodeRootNode module = compile(NESTED_UNPROVEN, "unproven");
                List<String> actual = completeSignature(activationRoot(module));
                List<String> folded = completeSignature(activationRoot(compile(NESTED, "folded")));
                assertHasContinuation(actual);
                assertTrue(actual.stream().anyMatch(entry -> entry.startsWith("c.PrepareSend")
                        || entry.contains("PrepareSend")));
                assertTrue(folded.size() < actual.size(), folded + " vs " + actual);
                assertTrue(groupRoots(module).size() > 2);
                assertFalse(activationRoot(module).provablyNonSuspendingBody());
                assertInteger(7, callClosure(normal, closureOf(normal, module)));
            });
        }
    }

    @Test
    void debugKeepsTheOrdinarySendAndContinuation() throws Exception {
        try (Harness debug = new Harness(ProtosExecutionMode.DEBUG)) {
            debug.run(() -> {
                ProtosBytecodeRootNode module = compile(NESTED, "debug");
                ProtosBytecodeRootNode activation = activationRoot(module);
                List<String> actual = completeSignature(activation);
                assertHasContinuation(actual);
                assertTrue(groupRoots(module).size() > 2);
                assertFalse(activation.provablyNonSuspendingBody());
                assertInteger(7, callClosure(debug, closureOf(debug, module)));
            });
        }
    }

    /** Effective-root proofs that the previous literal/parameter predicate rejected. */
    @Test
    void generalSafeBodiesPublishTheEffectiveRootCapability() throws Exception {
        StringBuilder sequence = new StringBuilder("() => {\n");
        for (int index = 0; index < 200; index++) {
            sequence.append("v").append(index).append(": ")
                    .append(index == 0 ? "1" : "v" + (index - 1)).append('\n');
        }
        sequence.append("v199\n}");
        record Case(String source, Object expected) {}
        List<Case> cases = List.of(
                new Case("() => { a: 1\n b: a\n b }", 1L),
                new Case(sequence.toString(), 1L),
                new Case("() => { true.ifTrue() { 1 }\n false.ifFalse() { 2 }\n 3 }", 3L),
                new Case("(value: 7) => { value }", 7L),
                new Case("(a, b) => { a === b }", null));
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL)) {
            normal.run(() -> {
                for (Case test : cases) {
                    ProtosBytecodeRootNode module = compile(test.source(), "safe");
                    ProtosBytecodeRootNode activation = activationRoot(module);
                    List<String> instructions = completeSignature(activation);
                    assertNoContinuation(instructions);
                    assertTrue(activation.provablyNonSuspendingBody(), test.source());
                    if (test.expected() != null) {
                        assertInteger((Long) test.expected(),
                                callClosure(normal, closureOf(normal, module)));
                    }
                }
            });
        }
    }

    @Test
    void unknownSubjectsKeepTheCapabilityOff() throws Exception {
        List<String> sources = List.of(
                "(f) => { f.value() }",
                "(value: compute()) => { 1 }",
                "(first: 7, ...tail) => { first }",
                "() => { escape: () => { ^1 }\n escape()\n 0 }",
                "(f) => { f() }",
                "(b) => { b.ifTrue() { 1 } }",
                "() => { self: () => { self() }\n 1 }",
                "() => { Error.handle(() => { true.ifTrue() }, (caught) => { 7 }) }");
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL)) {
            normal.run(() -> {
                for (String source : sources) {
                    ProtosBytecodeRootNode activation =
                            activationRoot(compile(source, "unknown"));
                    completeSignature(activation);
                    if (!source.contains("self")) {
                        assertFalse(activation.provablyNonSuspendingBody(), source);
                    }
                }
            });
        }
    }

    /** Guarded sites keep the complete fallback even when the expected body is trivial. */
    @Test
    void dynamicSelectionsKeepTheirPhysicalFallback() throws Exception {
        List<String> sources = List.of(
                // Ordinary call of a parameter followed by guest work: D013
                // dynamic selection. (A final call of a parameter is an I112
                // terminal invocation; see terminalCallWithoutYieldIsNotANonSuspensionProof.)
                "(f) => { f()\n 1 }",
                // Binding candidate: the guard may miss after replacement/override.
                "() => { f: () => { 1 }\n f() }",
                "() => { f: () => { 1 }\n f = () => { 2 }\n f() }",
                // Literal receiver: inline region with its admission miss.
                "() => { (() => { 1 })() }",
                // Dynamic Boolean receiver and callback.
                "(b, g) => { b.ifTrue(g) }",
                // Spread argument.
                "(f, items) => { f(...items) }");
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL)) {
            normal.run(() -> {
                for (String source : sources) {
                    List<String> instructions =
                            completeSignature(activationRoot(compile(source, "fallback")));
                    assertHasContinuation(instructions);
                }
                ProtosBytecodeRootNode replaced =
                        compile("() => { f: () => { 1 }\n f = () => { 2 }\n f() }", "replace");
                assertInteger(2, callClosure(normal, closureOf(normal, replaced)));
            });
        }
    }

    /**
     * I112 / PLAT062-T: a terminal Closure invocation emits no Yield in its
     * root, because no guest work follows it, yet it is not a proof of
     * non-suspension: the invoked call may still suspend and the root then
     * returns its terminal transport. A non-terminal unknown call keeps the
     * physical C-prime protocol.
     */
    @Test
    void terminalCallWithoutYieldIsNotANonSuspensionProof() throws Exception {
        record Case(String source, String operation, String argument, long expected) {}
        List<Case> terminal = List.of(
                new Case("(f) => { f() }", "c.InvokeClosureZeroTail",
                        "() => { 5 }", 5),
                new Case("(f) => { f(1) }", "c.InvokeClosureOneTail",
                        "(value) => { value + 4 }", 5),
                new Case("(f) => { f(2, 3) }", "c.InvokeClosureArgumentsTail",
                        "(a, b) => { a + b }", 5));
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL)) {
            normal.run(() -> {
                for (Case test : terminal) {
                    ProtosBytecodeRootNode module = compile(test.source(), "terminal");
                    ProtosBytecodeRootNode activation = activationRoot(module);
                    List<String> instructions = completeSignature(activation);
                    assertTrue(
                            instructions.stream().anyMatch(
                                    entry -> entry.split(" ", 2)[0].equals(test.operation())),
                            () -> test.operation() + " missing in " + instructions);
                    assertNoContinuation(instructions);
                    for (String absent : List.of(
                            "c.IsPreparedClosureInvocation",
                            "c.EnterClosureCall",
                            "c.FinishClosureCall")) {
                        assertFalse(
                                instructions.stream().anyMatch(
                                        entry -> entry.split(" ", 2)[0].equals(absent)),
                                () -> absent + " in " + instructions);
                    }
                    // Terminal without an outer Yield is not non-suspending.
                    assertFalse(activation.provablyNonSuspendingBody(), test.source());

                    module.getRootNodes().ensureComplete();
                    assertEquals(instructions, signature(activation));
                    assertFalse(activation.provablyNonSuspendingBody(), test.source());

                    assertInteger(
                            test.expected(),
                            callClosureWith(
                                    normal, closureOf(normal, module), test.argument()));
                }

                ProtosBytecodeRootNode nonTerminal =
                        activationRoot(compile("(f) => { f()\n 1 }", "non-terminal"));
                List<String> composed = completeSignature(nonTerminal);
                assertHasContinuation(composed);
                assertFalse(composed.stream().anyMatch(entry -> entry.contains("Tail")),
                        composed::toString);
                assertFalse(nonTerminal.provablyNonSuspendingBody());
            });
        }
    }

    @Test
    void inlineFallbackContinuationIsAccountedFor() throws Exception {
        String source = """
                () => {
                    b: true
                    b.ifTrue() { 1 }
                }
                """;
        try (Harness normal = new Harness(ProtosExecutionMode.NORMAL)) {
            normal.run(() -> {
                ProtosBytecodeRootNode module =
                        compile(source, "inline-fallback");
                ProtosBytecodeRootNode activation = activationRoot(module);
                List<String> actual = completeSignature(activation);

                assertHasContinuation(actual);
                assertTrue(actual.stream().anyMatch(
                        item -> item.contains("InlineLiteralFallbackCall")),
                        actual::toString);
                assertFalse(activation.provablyNonSuspendingBody());
                assertInteger(1, callClosure(normal, closureOf(normal, module)));

                module.getRootNodes().ensureComplete();
                assertEquals(actual, signature(activation));
                assertFalse(activation.provablyNonSuspendingBody());

                ProtosBytecodeRootNode equivalent =
                        activationRoot(compile(source, "inline-fallback-reparse"));
                assertEquals(actual, completeSignature(equivalent));
                assertFalse(equivalent.provablyNonSuspendingBody());

                ProtosBytecodeRootNode safe =
                        activationRoot(compile("() => { 1 }", "independent-root"));
                completeSignature(safe);
                assertTrue(safe.provablyNonSuspendingBody());
            });
        }
    }

    /** Each Context owns its lowerer and facts; nothing is shared across Contexts. */
    @Test
    void contextsDoNotShareRootsOrFacts() throws Exception {
        List<String> first = new ArrayList<>();
        try (Harness one = new Harness(ProtosExecutionMode.NORMAL)) {
            one.run(() -> first.addAll(completeSignature(activationRoot(compile(NESTED, "one")))));
        }
        try (Harness two = new Harness(ProtosExecutionMode.NORMAL)) {
            two.run(() -> {
                ProtosBytecodeRootNode activation = activationRoot(compile(NESTED, "two"));
                assertEquals(first, completeSignature(activation));
                assertTrue(activation.provablyNonSuspendingBody());
            });
        }
    }

    /**
     * Handwritten NORMAL Closure root returning one Long literal carrier, with the source section
     * of {@code characters}' outermost body.
     */
    private static ProtosBytecodeRootNode handwrittenLiteralRoot(
            String characters, long value) {
        Source source = Source.newBuilder(ProtosLanguage.ID, characters, "i107-oracle.protos")
                .build();
        int bodyStart = characters.indexOf('{');
        int bodyLength = characters.lastIndexOf('}') + 1 - bodyStart;
        BytecodeRootNodes<ProtosBytecodeRootNode> nodes =
                ProtosBytecodeRootNodeGen.create(
                        LANGUAGE_REF.get(null),
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginSource(source);
                            builder.beginSourceSection(bodyStart, bodyLength);
                            builder.beginRoot();
                            builder.beginTag(StandardTags.RootTag.class);
                            builder.beginBlock();
                            builder.beginReturn();
                            builder.emitLoadConstant(value);
                            builder.endReturn();
                            builder.endBlock();
                            builder.endTag(StandardTags.RootTag.class);
                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });
        return nodes.getNode(0);
    }

    /** The Closure body's statements, which the root's source section covers. */
    private static String bodyText(String characters) {
        return characters.substring(characters.indexOf('{') + 1, characters.lastIndexOf('}'))
                .trim();
    }

    private static void assertNoContinuation(List<String> signature) {
        for (String entry : signature) {
            String name = entry.split(" ", 2)[0];
            for (String operation : CONTINUATION) {
                assertFalse(name.equals(operation), () -> operation + " in " + signature);
            }
            assertFalse(name.contains("PrepareSend"), () -> "PrepareSend in " + signature);
        }
    }

    private static void assertHasContinuation(List<String> signature) {
        for (String operation : CONTINUATION) {
            assertTrue(
                    signature.stream().anyMatch(entry -> entry.split(" ", 2)[0].equals(operation)),
                    () -> operation + " missing in " + signature);
        }
    }

    private static ProtosBytecodeRootNode compile(String characters, String name) {
        Source source = Source.newBuilder(
                        ProtosLanguage.ID,
                        characters,
                        "i107-" + name + "-" + System.nanoTime() + ".protos")
                .build();
        RootCallTarget target = assertInstanceOf(
                RootCallTarget.class, ProtosLanguageContext.current().parsePublic(source));
        return assertInstanceOf(ProtosBytecodeRootNode.class, target.getRootNode());
    }

    private static ProtosBytecodeRootNode activationRoot(
            ProtosBytecodeRootNode module) {
        return groupRoots(module).get(1);
    }

    private static List<ProtosBytecodeRootNode> groupRoots(
            ProtosBytecodeRootNode root) {
        return root.getRootNodes().getNodes().stream()
                .map(ProtosBytecodeRootNode.class::cast)
                .toList();
    }

    private static List<String> completeSignature(ProtosBytecodeRootNode root) {
        root.getRootNodes().ensureComplete();
        return signature(root);
    }

    private static List<String> signature(ProtosBytecodeRootNode root) {
        List<String> entries = new ArrayList<>();
        for (Instruction instruction : root.getBytecodeNode().getInstructionsAsList()) {
            StringBuilder entry = new StringBuilder(instruction.getName());
            for (Instruction.Argument argument : instruction.getArguments()) {
                entry.append(' ')
                        .append(argument.getName())
                        .append(':')
                        .append(argument.getKind())
                        .append('=')
                        .append(operand(argument));
            }
            entries.add(entry.toString());
        }
        return entries;
    }

    private static String operand(Instruction.Argument argument) {
        return switch (argument.getKind()) {
            case CONSTANT -> constant(argument.asConstant());
            case BYTECODE_INDEX -> Integer.toString(argument.asBytecodeIndex());
            case INTEGER -> Integer.toString(argument.asInteger());
            case LOCAL_OFFSET -> Integer.toString(argument.asLocalOffset());
            case LOCAL_INDEX -> Integer.toString(argument.asLocalIndex());
            case TAG_NODE -> tags(argument.asTagNode());
            case NODE_PROFILE, BRANCH_PROFILE -> "profile";
        };
    }

    private static String constant(Object value) {
        if (value == null) {
            return "host-null";
        }
        if (value instanceof Double number) {
            return "Double(bits=" + Long.toHexString(Double.doubleToRawLongBits(number)) + ")";
        }
        if (value instanceof Long
                || value instanceof Integer
                || value instanceof Boolean
                || value instanceof String
                || value instanceof BigInteger) {
            return value.getClass().getSimpleName() + "(" + value + ")";
        }
        if (value == ProtosNullValue.INSTANCE) {
            return "Protos(null)";
        }
        if (value == ProtosBooleanValue.TRUE || value == ProtosBooleanValue.FALSE) {
            return "Protos(" + (value == ProtosBooleanValue.TRUE) + ")";
        }
        if (value instanceof Class<?> type) {
            return "Class(" + type.getName() + ")";
        }
        return "Metadata(" + value.getClass().getName() + ")";
    }

    private static String tags(TagTreeNode node) {
        if (node == null) {
            return "no-tag-node";
        }
        return node.getTags().stream().map(Class::getSimpleName).sorted().toList().toString();
    }

    private static ProtosClosureValue closureOf(
            Harness harness, ProtosBytecodeRootNode module) {
        return assertInstanceOf(
                ProtosClosureValue.class,
                module.getCallTarget().call(harness.moduleActivation()));
    }

    private static Object callClosure(Harness harness, ProtosClosureValue closure) {
        ProtosActivation module = harness.moduleActivation();
        module.context().createLocalSlot("candidate", closure);
        return compile("candidate()", "call").getCallTarget().call(module);
    }

    private static Object callClosureWith(
            Harness harness, ProtosClosureValue closure, String argument) {
        ProtosActivation module = harness.moduleActivation();
        module.context().createLocalSlot("candidate", closure);
        return compile("candidate(" + argument + ")", "call-with").getCallTarget().call(module);
    }

    private static void assertInteger(long expected, Object value) {
        assertEquals(Long.toString(expected), ProtosNumericValueSupport.integerDecimalText(value));
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    /** One raw Polyglot Context with a published Core, entered only while an action runs. */
    private static final class Harness implements AutoCloseable {
        private final ProtosExecutionMode mode;
        private final Context context;
        private ProtosPrelude prelude;

        Harness(ProtosExecutionMode mode) {
            this.mode = mode;
            Context.Builder builder = Context.newBuilder(ProtosLanguage.ID);
            if (mode == ProtosExecutionMode.DEBUG) {
                builder.allowExperimentalOptions(true).option("protos.Debug", "true");
            }
            this.context = builder.build();
            context.initialize(ProtosLanguage.ID);
        }

        void run(Action action) throws Exception {
            context.enter();
            try {
                assertEquals(mode, ProtosLanguageContext.current().executionMode());
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
            context.close();
        }
    }
}

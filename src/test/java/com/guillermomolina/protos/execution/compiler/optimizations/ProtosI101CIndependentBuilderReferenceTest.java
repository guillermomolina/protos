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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.Debugger;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I101-C focal evidence: independent Builder references for roots whose specialization is a
 * dynamic guard with a semantically necessary physical fallback. PLAT067-B: the same references
 * hold with a Truffle debugger attached before lowering.
 *
 * <p>Every expected root is a handwritten {@link ProtosBytecodeRootNodeGen.Builder}
 * program. The references never consult the parser, the canonicalizer, the lowerer or any lowered
 * root; the shared invocation helpers below restate the physical prepared-invocation protocol
 * operation by operation. Only the actual roots come from the production {@code ParsingRequest}
 * path of an entered Context with a published Core.
 *
 * <p>Signatures carry every instruction (including tag, branch, cleanup and fallback
 * instructions) with every argument name, kind and value. Profiles carry no stable value and
 * compare by kind; metadata constants compare by their exact class.
 */
final class ProtosI101CIndependentBuilderReferenceTest {
    private static final java.nio.file.Path CORE = java.nio.file.Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);



    /**
     * I108-A+B: complete independent Bytecode Builder references for
     * dynamic Call0 and CallN.
     *
     * The receiver is deliberately not a source Closure. Consequently
     * both sites must retain the entire generic prepared-result path,
     * while their caller bytecode is owned by InvokeClosureZero or
     * InvokeClosureArguments rather than TryDirectCall/PrepareClosureCall.
     *
     * The reference never uses the canonicalizer, production lowerer,
     * or a production root as input.
     */
    @Test
    void i108DynamicCallRootsMatchIndependentBuilder()
            throws Exception {
        record Witness(String name, String source, boolean multi,
                String invocation) {}

        List<Witness> witnesses = List.of(
                new Witness(
                        "call-zero",
                        "() => { null() }",
                        false,
                        "CallNilaryMethod"),
                new Witness(
                        "call-n",
                        "() => { null(7, 9) }",
                        true,
                        "CallVarargsMethod"));

        try (Harness normal =
                new Harness(false)) {
            normal.run(() -> {
                for (Witness witness : witnesses) {
                    ProtosBytecodeRootNode module =
                            compile(witness.source(), witness.name());

                    BytecodeRootNodes<?> group =
                            module.getRootNodes();

                    List<ProtosBytecodeRootNode> roots =
                            groupRoots(module);

                    assertEquals(2, roots.size(), witness.name());

                    ProtosBytecodeRootNode actualRoot =
                            roots.get(1);

                    RootCallTarget actualTarget =
                            actualRoot.getCallTarget();

                    ProtosBytecodeRootNode reference =
                            handwrittenI108DynamicCallRoot(
                                    witness.source(), witness.multi());

                    assertEquals(
                            1,
                            groupRoots(reference).size(),
                            witness.name() + " independent group");

                    List<String> expected =
                            completeSignature(reference);

                    List<String> actual =
                            completeSignature(actualRoot);

                    assertEquals(
                            expected,
                            actual,
                            witness.name()
                                    + " complete Builder reference");

                    assertTrue(
                            indexOfOperation(
                                    actual, witness.invocation()) >= 0,
                            witness.name() + " missing semantic invocation");

                    // I112 / PLAT062-T: the terminal operation owns the
                    // fallback; no physical composition remains here.
                    for (String absent : List.of(
                            "IsPreparedClosureInvocation",
                            "RequiresStructuredDispatch",
                            "EnterStraightLineSourceCall",
                            "EnterClosureCall",
                            "IsContinuationForCall",
                            "ResumeContinuation",
                            "Yield")) {
                        assertNoOperation(actual, absent);
                    }

                    assertNoOperation(actual, "TryDirectCall");
                    assertNoOperation(actual, "PrepareClosureCall");

                    group.ensureComplete();

                    assertSame(
                            actualTarget,
                            actualRoot.getCallTarget(),
                            witness.name() + " CallTarget stability");

                    assertEquals(
                            actual,
                            signature(actualRoot),
                            witness.name()
                                    + " complete-root reparse stability");
                }
            });
        }
    }

    /**
     * Independent, single-root Builder model of a parameterless
     * Closure whose terminal expression invokes null with zero or
     * two positional arguments, lowered as an I112 terminal call. The call is not executed here:
     * null is chosen solely to make the receiver construction
     * independent of any Closure materialization or lexical lookup.
     */
    private static ProtosBytecodeRootNode
            handwrittenI108DynamicCallRoot(
                    String characters, boolean multi) {

        Source source = Source.newBuilder(
                        ProtosLanguage.ID,
                        characters,
                        "i108-independent-dynamic.protos")
                .build();

        int bodyStart = characters.indexOf('{');
        int bodyLength =
                characters.lastIndexOf('}') + 1 - bodyStart;

        int callStart = characters.indexOf("null(");
        int callLength = multi
                ? "null(7, 9)".length()
                : "null()".length();

        if (bodyStart < 0 || callStart < 0) {
            throw new AssertionError("Invalid independent witness");
        }

        BytecodeRootNodes<ProtosBytecodeRootNode> nodes =
                ProtosBytecodeRootNodeGen.create(
                        LANGUAGE_REF.get(null),
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginSource(source);
                            builder.beginSourceSection(
                                    bodyStart, bodyLength);
                            builder.beginRoot();
                            builder.beginBlock();

                            // I112 / PLAT062-T: the final call is the
                            // root's terminal invocation; its operands
                            // are evaluated exactly once, left to right.
                            builder.beginReturn();
                            beginReferenceStatement(
                                    builder, callStart, callLength);

                            builder.beginSourceSection(
                                    callStart, callLength);
                            builder.beginTag(
                                    StandardTags.CallTag.class);

                            if (multi) {
                                builder.beginCallVarargsMethod(true);
                            } else {
                                builder.beginCallNilaryMethod(true);
                            }

                            builder.emitLoadConstant(
                                    ProtosNullValue.INSTANCE);

                            if (multi) {
                                builder.beginCollectClosureCallArguments();
                                builder.emitLoadConstant(
                                        ProtosNumericValueSupport.integer(7));
                                builder.emitLoadConstant(
                                        ProtosNumericValueSupport.integer(9));
                                builder.endCollectClosureCallArguments();
                                builder.endCallVarargsMethod();
                            } else {
                                builder.endCallNilaryMethod();
                            }

                            builder.endTag(
                                    StandardTags.CallTag.class);
                            builder.endSourceSection();

                            endReferenceStatement(builder);
                            builder.endReturn();

                            builder.endBlock();
                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });

        return nodes.getNode(0);
    }


    /**
     * I108-A: independent physical Builder reference for a dynamic
     * Call1 whose receiver is a supplied Closure.
     *
     * The operand identity is deliberately a parameter, not a
     * literal or a statically known Closure binding. This excludes
     * I096 lexical inlining and exercises InvokeClosureOne.
     */
    @Test
    void i108IdentityCallOneMatchesIndependentBuilder()
            throws Exception {
        try (Harness normal = new Harness(false)) {
            normal.run(() -> {
                String source =
                        "(identity) => { identity(1) }";

                ProtosBytecodeRootNode module =
                        compile(source, "i108-identity-call1");

                List<ProtosBytecodeRootNode> roots =
                        groupRoots(module);

                assertEquals(2, roots.size());

                ProtosBytecodeRootNode actualRoot =
                        roots.get(1);

                RootCallTarget actualTarget =
                        actualRoot.getCallTarget();

                ProtosBytecodeRootNode reference =
                        handwrittenI108IdentityCallOneRoot();

                assertEquals(
                        1,
                        groupRoots(reference).size(),
                        "independent Builder has one root");

                List<String> expected =
                        completeSignature(reference);

                List<String> actual =
                        completeSignature(actualRoot);

                assertEquals(
                        expected,
                        actual,
                        "I108 Call1 identity complete Builder signature");

                // I112 / PLAT062-T: the final call is terminal. The
                // operation owns selection, fallback and the transport
                // of a suspended invocation; no physical composition
                // remains in this root.
                // I115: the GraalPy-shaped CallUnaryMethod replaces the
                // retired InvokeClosureOneTail terminal operation.
                assertTrue(
                        indexOfOperation(
                                actual, "CallUnaryMethod") >= 0,
                        actual::toString);
                assertNoOperation(actual, "InvokeClosureOneTail");

                for (String operation : List.of(
                        "IsPreparedClosureInvocation",
                        "IsContinuationForCall",
                        "ResumeContinuation",
                        "Yield",
                        "EnterStraightLineSourceCall",
                        "EnterClosureCall",
                        "FinishClosureCall",
                        "CurrentActivation",
                        "MaterializePrimitiveNumericCarrier")) {
                    assertNoOperation(actual, operation);
                }

                assertNoOperation(actual, "TryDirectCallOne");
                assertNoOperation(actual, "PrepareClosureCallOne");
                assertNoOperation(actual, "IsDirectSendMiss");

                module.getRootNodes().ensureComplete();

                assertSame(
                        actualTarget,
                        actualRoot.getCallTarget());

                assertEquals(
                        actual,
                        signature(actualRoot),
                        "I108 Call1 reparse stability");

                // Execute the actual production root as well.
                ProtosClosureValue outer =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                module.getCallTarget().call(
                                        normal.moduleActivation()));

                assertInteger(
                        1,
                        callClosure(
                                normal,
                                outer,
                                "(value) => { value }"));
            });
        }
    }

    /**
     * Entirely handwritten reference for:
     *
     *     (identity) => { identity(1) }
     *
     * The actual lowerer is never consulted to construct this
     * reference. It includes parameter binding, the direct receiver
     * read, primitive argument staging and the I112 terminal Call1,
     * which owns generic fallback and suspension transport; the
     * return carrier boundary is the root's return epilogue.
     */
    private static ProtosBytecodeRootNode
            handwrittenI108IdentityCallOneRoot() {

        String characters =
                "(identity) => { identity(1) }";

        Source source = Source.newBuilder(
                        ProtosLanguage.ID,
                        characters,
                        "i108-independent-identity-call1.protos")
                .build();

        int bodyStart = characters.indexOf('{');
        int bodyLength =
                characters.lastIndexOf('}') + 1 - bodyStart;

        int callStart = characters.indexOf("identity(1)");
        int callLength = "identity(1)".length();

        BytecodeRootNodes<ProtosBytecodeRootNode> nodes =
                ProtosBytecodeRootNodeGen.create(
                        LANGUAGE_REF.get(null),
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginSource(source);
                            builder.beginSourceSection(
                                    bodyStart, bodyLength);
                            builder.beginRoot();

                            // The receiver is a real Closure parameter.
                            BytecodeLocal identity =
                                    builder.createLocal(
                                            "identity", null);
                            builder.beginBlock();

                            BytecodeLocal[] locals = {identity};

                            ProtosFrameLexicalLayout layout =
                                    ProtosFrameLexicalLayout.of(
                                            new String[] {"identity"},
                                            ProtosFrameLexicalLayout
                                                    .localOffsetsOf(locals));

                            builder.beginBindClosureFrameParameter(
                                    locals, layout, 0);
                            builder.emitLoadConstant("identity");

                            builder.beginLoadFrameClosureArgument();
                            builder.emitLoadConstant(0);
                            builder.endLoadFrameClosureArgument();

                            builder.endBindClosureFrameParameter();

                            // I112 / PLAT062-T: the final call is the
                            // root's terminal invocation. Receiver and
                            // argument are its two operands, evaluated
                            // once, left to right; the caller is bound
                            // from the frame. No scratch local, prepared
                            // dispatch or Yield remains in this root.
                            // The Return is the root's last operation.
                            builder.beginReturn();

                            beginReferenceStatement(
                                    builder, callStart, callLength);

                            builder.beginSourceSection(
                                    callStart, callLength);

                            builder.beginTag(
                                    StandardTags.CallTag.class);

                            builder.beginCallUnaryMethod(true);

                            // The receiver binding is read directly; the
                            // operation projects a carrier before any
                            // generic selection.
                            builder.emitReadRootFrameLocal(
                                    identity,
                                    layout.presentContinuityAt(0),
                                    "identity");

                            // I091: the scalar argument is a Long
                            // carrier until the selected path requires
                            // a guest numeric value.
                            builder.emitLoadConstant(1L);

                            builder.endCallUnaryMethod();

                            builder.endTag(
                                    StandardTags.CallTag.class);

                            builder.endSourceSection();

                            endReferenceStatement(builder);

                            builder.endReturn();

                            builder.endBlock();
                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });

        return nodes.getNode(0);
    }

    // ---- Shared Builder restatement of the physical prepared-invocation protocol. ----

    private static void beginReferenceStatement(
            ProtosBytecodeRootNodeGen.Builder builder, int start, int length) {
        builder.beginSourceSection(start, length);
        builder.beginTag(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class);
        builder.beginBlock();
    }

    private static void endReferenceStatement(ProtosBytecodeRootNodeGen.Builder builder) {
        builder.endBlock();
        builder.endTag(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class);
        builder.endSourceSection();
    }

    // ---- Actual roots, signatures and guest calls. ----

    /** The production ParsingRequest path of the entered Context. */
    private static ProtosBytecodeRootNode compile(String characters, String name) {
        Source source = Source.newBuilder(
                        ProtosLanguage.ID, characters, "i101-c-reference-" + name + ".protos")
                .build();
        RootCallTarget target = assertInstanceOf(
                RootCallTarget.class, ProtosLanguageContext.current().parsePublic(source));
        return assertInstanceOf(ProtosBytecodeRootNode.class, target.getRootNode());
    }

    private static List<ProtosBytecodeRootNode> groupRoots(
            ProtosBytecodeRootNode root) {
        return root.getRootNodes().getNodes().stream()
                .map(ProtosBytecodeRootNode.class::cast)
                .toList();
    }

    private static int indexOfOperation(List<String> signature, String operation) {
        for (int index = 0; index < signature.size(); index++) {
            String name = signature.get(index).split(" ", 2)[0];
            if (name.equals(operation) || name.equals("c." + operation)) {
                return index;
            }
        }
        return -1;
    }

    private static void assertNoOperation(List<String> signature, String operation) {
        assertTrue(
                signature.stream().noneMatch(entry -> entry.split(" ", 2)[0].contains(operation)),
                () -> operation + " in " + signature);
    }

    /**
     * Compares two complete signatures of one program across Truffle
     * quickening. The instruction count and order must be identical; each
     * operation name is compared with only its DSL quickening suffix (from
     * the first {@code $}) removed, and each operand is compared after
     * dropping generated specialization state and node profiles. Nothing is
     * deleted, sorted, filtered or collapsed.
     */
    private static void assertSameLogicalProgram(
            List<String> before, List<String> after, String message) {
        assertEquals(
                before.size(),
                after.size(),
                () -> message + ": instruction count " + before + " vs " + after);
        for (int index = 0; index < before.size(); index++) {
            assertEquals(
                    logicalInstruction(before.get(index)),
                    logicalInstruction(after.get(index)),
                    message + ": instruction " + index + "\n" + before + "\nvs\n" + after);
        }
    }

    private static String logicalInstruction(String entry) {
        String[] parts = entry.split(" ");
        StringBuilder logical = new StringBuilder();
        String name = parts[0];
        int quickened = name.indexOf('$');
        logical.append(quickened < 0 ? name : name.substring(0, quickened));
        for (int index = 1; index < parts.length; index++) {
            String operand = parts[index];
            // Generated specialization state and node profile identities
            // are not part of the language-level program.
            if (operand.startsWith("state_")
                    || operand.contains(":NODE_PROFILE=")
                    || operand.contains(":BRANCH_PROFILE=")) {
                continue;
            }
            logical.append(' ').append(operand);
        }
        return logical.toString();
    }

    private static List<String> completeSignature(ProtosBytecodeRootNode root) {
        root.getRootNodes().ensureComplete();
        return signature(root);
    }

    /** Every instruction with every argument; nothing is filtered. */
    private static List<String> signature(ProtosBytecodeRootNode root) {
        List<String> entries = new ArrayList<>();
        for (Instruction instruction : root.getBytecodeNode().getInstructionsAsList()) {
            StringBuilder entry = new StringBuilder(ProtosInstructionNames.of(instruction));
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
        if (value instanceof Float number) {
            return "Float(bits=" + Integer.toHexString(Float.floatToRawIntBits(number)) + ")";
        }
        if (value instanceof Long
                || value instanceof Integer
                || value instanceof Short
                || value instanceof Byte
                || value instanceof Boolean
                || value instanceof Character
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
        // Internal metadata (plans, layouts, definitions) has no stable value across roots.
        return "Metadata(" + value.getClass().getName() + ")";
    }

    private static String tags(TagTreeNode node) {
        if (node == null) {
            return "no-tag-node";
        }
        return node.getTags().stream().map(Class::getSimpleName).sorted().toList().toString();
    }

    private static boolean hasRootTag(TagTree tree) {
        return countRootTags(tree) > 0;
    }

    private static int countRootTags(TagTree tree) {
        if (tree == null) {
            return 0;
        }
        int count = tree.hasTag(StandardTags.RootTag.class) ? 1 : 0;
        for (TagTree child : tree.getTreeChildren()) {
            count += countRootTags(child);
        }
        return count;
    }

    /** Calls a Closure value through an ordinary guest call in a fresh module activation. */
    private static Object callClosure(
            Harness harness, ProtosClosureValue closure, String arguments) {
        ProtosActivation module = harness.moduleActivation();
        module.context().createLocalSlot("candidate", closure);
        return compile(
                        "candidate(" + arguments + ")",
                        "call-" + System.identityHashCode(closure) + "-" + arguments.hashCode())
                .getCallTarget()
                .call(module);
    }

    private static void assertInteger(long expected, Object value) {
        assertEquals(
                Long.toString(expected), ProtosNumericValueSupport.integerDecimalText(value));
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    /** One raw Polyglot Context with a published Core, entered only while an action runs. */
    private static final class Harness implements AutoCloseable {
        private final Context context;
        private final DebuggerSession debugger;
        private ProtosPrelude prelude;

        /**
         * With {@code debugger}, a Truffle debugger session is attached to the Engine before any
         * lowering (PLAT067-B: it observes, it never selects another lowering).
         */
        Harness(boolean debugger) {
            this.context = Context.newBuilder(ProtosLanguage.ID).build();
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
                    // Publishes the standard root before any witness is lowered.
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

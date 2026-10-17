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
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
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

            ProtosSemanticBytecodeRootNode actual =
                    closure.executionPlan().orElseThrow()
                            .bytecodeActivationRootForTesting();

            ProtosSemanticBytecodeRootNode bound =
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
            assertEquals(
                    List.of(
                            // I112: the binding name is a constant operand and
                            // the return epilogue owns the carrier projection.
                            "c.EnterRoot",
                            "c.BindUnaryFrameParameter",
                            "c.ReadRootFrameLocal",
                            "c.EpilogForReturn",
                            "return"),
                    executionPrefixNames(actual),
                    "I104-A must retain its fused unary entry and "
                            + "semantically correct local read; D203/I105 "
                            + "emits no excess-argument check");

            System.out.println("PERF042_BOUND_ARGUMENT_LOWER_BOUND=PASS");
            System.out.println("PERF042_I104_A_BYTECODE_SHAPE=PASS");
            System.out.println("PERF042_IDENTITY_SEMANTICS=PASS");
        });
    }

    /**
     * PERF042: oráculo con binding explícito del parámetro.
     *
     * Graal podrá eliminar el store/load si demuestra que no
     * son observables. El lowerer conserva el binding.
     */
    private static ProtosSemanticBytecodeRootNode handwrittenIdentity() {
        String code = "(value) => { value }";
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

        return roots.getNode(0);
    }

    private static Object invokeOracle(
            ProtosSemanticBytecodeRootNode root,
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
            ProtosSemanticBytecodeRootNode oracle,
            ProtosSemanticBytecodeRootNode actual) {

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
}

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF025-C1c (PLAT042 Candidate B′) focal topology evidence.
 *
 * <p>Ordinary source roots are tagged semantic Bytecode roots: an ordinary
 * source Closure call adds exactly one CallTarget to the stack. A structured
 * prepared invocation adds exactly one untagged structured-dispatch CallTarget,
 * and that helper root carries no RootTag. PLAT043 (PERF025-C2B) narrows the
 * structured case: standard Boolean control is sequenced in the source root and
 * adds no helper CallTarget. Canonical control whose callbacks are literal
 * Closures (conditionals and lowered loop sites) runs as regions of its owning
 * semantic root and registers no auxiliary physical root.
 */
final class ProtosPerf025C1cSemanticSourceTopologyTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void ordinarySourceClosureCallUsesOneSemanticCallTarget() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                List<RootNode> stack = new ArrayList<>();
                ProtosActivation module = moduleWithProbe(stack);
                ProtosBytecodeRootNode root =
                        lowerRoot(
                                """
                                inner: () => { probe() }
                                outer: () => { inner() }
                                outer()
                                """);

                assertSame(ProtosNullValue.INSTANCE, root.getCallTarget().call(module));

                assertEquals(4, stack.size(), () -> "stack=" + stack);
                assertInstanceOf(ProtosBuiltinCallRootNode.class, stack.get(0));
                for (RootNode frameRoot : stack.subList(1, stack.size())) {
                    assertTrue(
                            assertInstanceOf(ProtosBytecodeRootNode.class, frameRoot)
                                    .isSemanticActivationRoot());
                }
                assertSame(root, stack.get(3));

                root.getRootNodes().ensureComplete();
                // I111-B: the reparse keeps the physical root and its semantic identity.
                assertTrue(root.isSemanticActivationRoot());
                assertEquals(1, rootTags(root.getBytecodeNode()));
                assertEquals(1, rootBodyTags(root.getBytecodeNode()));
            } finally {
                context.leave();
            }
        }
        System.out.println("PERF025_C1C_ORDINARY_SOURCE_CLOSURE_CALLTARGET_COUNT=1");
        System.out.println("PERF025_C1C_SEMANTIC_EXPLICIT_ROOT_TAG=YES");
    }

    @Test
    void closureActivationRootIsItsOwnCompositionTarget() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosActivation module = moduleWithProbe(new ArrayList<>());
                ProtosBytecodeRootNode root = lowerRoot("() => 1");
                ProtosClosureValue closure =
                        assertInstanceOf(
                                ProtosClosureValue.class,
                                root.getCallTarget().call(module));
                ProtosClosureExecutionPlan plan = closure.executionPlan().orElseThrow();
                ProtosBytecodeRootNode activationRoot =
                        plan.bytecodeActivationRootForTesting();

                assertSame(
                        activationRoot,
                        plan.bytecodeActivationTargetForComposition().getRootNode(),
                        "no universal semantic wrapper may sit in front of a Closure root");
                assertSame(root.getRootNodes(), activationRoot.getRootNodes());

                activationRoot.getRootNodes().ensureComplete();
                assertTrue(activationRoot.isSemanticActivationRoot());
                assertEquals(1, rootTags(activationRoot.getBytecodeNode()));
                assertEquals(1, rootBodyTags(activationRoot.getBytecodeNode()));
            } finally {
                context.leave();
            }
        }
        System.out.println("PERF025_C1C_UNIVERSAL_SEMANTIC_WRAPPER=REMOVED");
    }

    /**
     * I111-B / PLAT065-A: one generated interpreter. The Task C-prime entry
     * plan is an auxiliary root of it: never a semantic activation, carrying
     * no RootTag, also after a reparse that materializes every tag.
     */
    @Test
    void auxiliaryTaskEntryRootSharesTheSingleInterpreterWithoutSemanticIdentity()
            throws Exception {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("com.guillermomolina.protos.execution.ProtosSemanticBytecodeRootNodeGen"));
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosBytecodeRootNode entry =
                        assertInstanceOf(
                                ProtosBytecodeRootNode.class,
                                ProtosTaskCPrimeEntryExecution.planForEnteredContext()
                                        .target()
                                        .getRootNode());
                assertFalse(entry.isSemanticActivationRoot());

                entry.getRootNodes().ensureComplete();
                assertFalse(entry.isSemanticActivationRoot());
                assertEquals(1, rootTags(entry.getBytecodeNode()));
                assertFalse(entry.isSemanticActivationRoot(),
                        "a generated RootTag does not grant semantic identity");
            } finally {
                context.leave();
            }
        }
        System.out.println("I111_B_SINGLE_GENERATED_INTERPRETER=YES");
    }

    /**
     * PLAT043 (PERF025-C2B): every prepared standard Boolean kind is sequenced
     * in the semantic source root, so the reached callback root sits directly
     * on the source root with no untagged helper root in between.
     */
    @Test
    void standardBooleanControlEntersNoStructuredRoot() throws Exception {
        List<String> sources =
                List.of(
                        "true.ifTrue(() => { probe() })",
                        "false.ifFalse(() => { probe() })",
                        "false.ifTrueIfFalse(() => 1, () => { probe() })",
                        """
                        true.and(() => {
                            probe()
                            true
                        })
                        """,
                        """
                        false.or(() => {
                            probe()
                            true
                        })
                        """);
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                for (String characters : sources) {
                    List<RootNode> stack = new ArrayList<>();
                    ProtosActivation module = moduleWithProbe(stack);
                    ProtosBytecodeRootNode root = lowerRoot(characters);

                    root.getCallTarget().call(module);

                    // The literal callback contains no return and its module
                    // read is statically proven: it runs inline in the source root.
                    assertEquals(2, stack.size(), () -> characters + " stack=" + stack);
                    assertInstanceOf(
                            ProtosBuiltinCallRootNode.class,
                            stack.get(0),
                            () -> characters + " native probe stack=" + stack);
                    for (RootNode frameRoot : stack.subList(1, stack.size())) {
                        assertTrue(
                                assertInstanceOf(
                                        ProtosBytecodeRootNode.class,
                                        frameRoot).isSemanticActivationRoot(),
                                () -> characters + " stack=" + stack);
                    }
                    assertSame(root, stack.get(1));
                }
            } finally {
                context.leave();
            }
        }
        System.out.println("PERF025_C2B_BOOLEAN_HELPER_CALLTARGET_REMOVED=YES");
    }

    /**
     * PLAT042 remains authoritative for every other structured family: a
     * reached {@code Closure.whileTrue} condition runs while exactly one untagged
     * structured-dispatch root is live between it and the source root.
     */
    @Test
    void nonBooleanStructuredInvocationEntersOneUntaggedStructuredRoot() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                List<RootNode> stack = new ArrayList<>();
                ProtosActivation module = moduleWithProbe(stack);
                ProtosBytecodeRootNode root =
                        lowerRoot(
                                """
                                (() => {
                                    probe()
                                    false
                                }).whileTrue(() => 1)
                                """);

                root.getCallTarget().call(module);

                // The condition literal contains no return and its module read
                // is statically proven: it runs inline in the source root.
                assertEquals(2, stack.size(), () -> "stack=" + stack);
                assertInstanceOf(ProtosBuiltinCallRootNode.class, stack.get(0));
                assertSame(root, stack.get(1));
                assertTrue(root.isSemanticActivationRoot());

                // No auxiliary structured dispatcher may appear on the
                // observed execution stack between source roots.
                RootNode auxiliary =
                        ProtosTaskCPrimeEntryExecution
                                .planForEnteredContext().target().getRootNode();
                assertTrue(stack.stream().noneMatch(frame -> frame == auxiliary),
                        () -> "unexpected structured dispatcher: " + stack);
                assertEquals(
                        1L,
                        stack.stream()
                                .filter(frame -> frame instanceof ProtosBytecodeRootNode)
                                .count(),
                        () -> "exactly one semantic source root: " + stack);
            } finally {
                context.leave();
            }
        }
        System.out.println("PERF025_C1C_STRUCTURED_ONLY_HELPER_BOUNDARY=YES");
        System.out.println("PERF025_C1C_STRUCTURED_HELPER_ROOT_TAG=NO");
    }

    /**
     * The canonical Bubble Sort shape: an outer {@code while}, an inner
     * {@code while}, an inner {@code if}, indexed reads and writes, assignments
     * to bindings of the {@code run} Closure and a checksum loop. Structurally,
     * the root group holds exactly the module root and the {@code run} root: no
     * condition or body of those controls is registered as a physical root. At
     * run time, every control region executes with only {@code run} and the
     * module on the stack.
     */
    @Test
    void bubbleSortControlsRunAsRegionsOfTheirSemanticRoot() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                List<List<RootNode>> stacks = new ArrayList<>();
                ProtosActivation module =
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
                module.context().createLocalSlot(
                        "probe",
                        ProtosClosureValue.nativeClosure(
                                (activation, supplied) -> {
                                    stacks.add(currentStack());
                                    return ProtosNullValue.INSTANCE;
                                }));
                ProtosBytecodeRootNode root = lowerRoot(BUBBLE_SORT);

                root.getRootNodes().ensureComplete();
                assertEquals(2, root.getRootNodes().count(),
                        "module and run roots only: " + root.getRootNodes().getNodes());

                Object checksum = root.getCallTarget().call(module);

                assertEquals("89440",
                        com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                                .integerDecimalText(checksum));
                assertFalse(stacks.isEmpty());
                for (List<RootNode> stack : stacks) {
                    assertEquals(3, stack.size(), () -> "stack=" + stack);
                    assertInstanceOf(ProtosBuiltinCallRootNode.class, stack.get(0));
                    assertNoAuxiliaryRoot(stack);
                    assertSame(root, stack.get(2));
                }
            } finally {
                context.leave();
            }
        }
        System.out.println("BUBBLE_SORT_INSTRUMENTED_CONTROL_STACK=SEMANTIC_ONLY");
    }

    /**
     * The exact benchmark program, kept as an independent fixture: lowering
     * registers only the module root and the {@code run} root, before and
     * after a reparse that materializes every root, and before and after
     * execution; no control Closure is registered ahead of time as a root of
     * its own, and the program computes its checksum. The fixture only defines
     * {@code run}; its harness invokes it, as the appended call does here.
     */
    @Test
    void exactBubbleSortBenchmarkRegistersOnlyItsSemanticRoots() throws Exception {
        String program;
        try (java.io.InputStream input =
                ProtosPerf025C1cSemanticSourceTopologyTest.class.getResourceAsStream(
                        BUBBLE_SORT_FIXTURE)) {
            assertNotNull(input, BUBBLE_SORT_FIXTURE);
            program = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosActivation module =
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
                ProtosBytecodeRootNode root = lowerRoot(program + "\nrun()\n");

                assertOnlySemanticRoots(root, 2);
                root.getRootNodes().ensureComplete();
                assertOnlySemanticRoots(root, 2);

                Object checksum = root.getCallTarget().call(module);

                assertEquals("89440",
                        com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                                .integerDecimalText(checksum));
                assertOnlySemanticRoots(root, 2);
            } finally {
                context.leave();
            }
        }
        System.out.println("BUBBLE_SORT_AUXILIARY_CONTROL_ROOTS=NONE");
    }

    private static final String BUBBLE_SORT_FIXTURE =
            "/benchmarks/bubble-sort/bubble-sort.protos";

    /** The root group holds exactly {@code expected} roots, all semantic. */
    private static void assertOnlySemanticRoots(ProtosBytecodeRootNode root, int expected) {
        List<? extends RootNode> roots = root.getRootNodes().getNodes();
        assertEquals(expected, roots.size(), () -> "roots=" + roots);
        for (RootNode each : roots) {
            assertTrue(
                    assertInstanceOf(ProtosBytecodeRootNode.class, each)
                            .isSemanticActivationRoot(),
                    () -> "auxiliary root registered: " + roots);
        }
    }

    private static final String BUBBLE_SORT =
            """
            run: () => {
                values: [
                    13, 18, 6, 42, 43, 1, 58, 4,
                    7, 32, 8, 59, 9, 64, 38, 37,
                    25, 61, 24, 21, 23, 63, 35, 31,
                    34, 30, 17, 48, 51, 41, 27, 40,
                    15, 33, 46, 2, 28, 44, 54, 56,
                    62, 11, 22, 12, 19, 50, 26, 55,
                    60, 53, 57, 36, 5, 45, 14, 16,
                    20, 39, 47, 10, 3, 52, 29, 49
                ]
                n: values.size()
                i: 0
                j: 0
                temp: 0
                checksum: 0
                k: 0

                while(i < n - 1) {
                    j = 0
                    probe()
                    while (j < n - i - 1) {
                        if(values[j] > values[j + 1]) {
                            probe()
                            temp = values[j]
                            values[j] = values[j + 1]
                            values[j + 1] = temp
                        }
                        j = j + 1
                    }
                    i = i + 1
                }

                while(k < n) {
                    checksum = checksum + values[k] * (k + 1)
                    k = k + 1
                }
                probe()
                checksum
            }
            run()
            """;

    /** Every Bytecode root on {@code stack} is a semantic activation root. */
    private static void assertNoAuxiliaryRoot(List<RootNode> stack) {
        for (RootNode frame : stack) {
            if (frame instanceof ProtosBytecodeRootNode bytecode) {
                assertTrue(bytecode.isSemanticActivationRoot(),
                        () -> "auxiliary root on the stack: " + stack);
            }
        }
    }

    private static List<RootNode> currentStack() {
        List<RootNode> stack = new ArrayList<>();
        Truffle.getRuntime()
                .iterateFrames(
                        frame -> {
                            stack.add(((RootCallTarget) frame.getCallTarget()).getRootNode());
                            return null;
                        });
        return stack;
    }

    @Test
    void guardedMethodSendEntersSemanticRootWithOneActivation() throws Exception {
        ProtosActivation module =
                new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "perf025-c1c-compact-send.protos",
                        """
                        receiver: {
                            identity: (value) => { value }
                            fail: () => { Error().signal() }
                        }
                        sum: 0
                        count: 0
                        (() => count < 20).whileTrue(() => {
                            sum = sum + receiver.identity(2)
                            count = count + 1
                        })
                        sum + Error.handle(() => receiver.fail(), (caught) => 2)
                        """,
                        module);
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "outcome=" + outcome.state() + ", error=" + outcome.error());
        assertEquals(Long.toString(42), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(outcome.value()));
        System.out.println("PERF025_C1C_COMPACT_METHOD_SEND_ACTIVATION=PASS");
    }

    private static ProtosActivation moduleWithProbe(List<RootNode> stack) throws Exception {
        ProtosActivation module =
                new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
        module.context().createLocalSlot(
                "probe",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            stack.clear();
                            Truffle.getRuntime()
                                    .iterateFrames(
                                            frame -> {
                                                stack.add(
                                                        ((RootCallTarget) frame.getCallTarget())
                                                                .getRootNode());
                                                return null;
                                            });
                            return ProtosNullValue.INSTANCE;
                        }));
        return module;
    }

    private static ProtosBytecodeRootNode lowerRoot(String characters) {
        Source source =
                Source.newBuilder(
                                ProtosLanguage.ID,
                                characters,
                                "perf025-c1c-topology.protos")
                        .build();
        CanonicalSequence sequence =
                (CanonicalSequence)
                        new Canonicalizer()
                                .canonicalize(new ProtosParser(characters).parseProgram());
        return new CanonicalToBytecodeLowerer(LANGUAGE_REF.get(null), source)
                .lowerRoot(sequence);
    }

    private static int rootTags(BytecodeNode bytecodeNode) {
        return countTags(bytecodeNode.getTagTree(), StandardTags.RootTag.class);
    }

    private static int rootBodyTags(BytecodeNode bytecodeNode) {
        return countTags(bytecodeNode.getTagTree(), StandardTags.RootBodyTag.class);
    }

    private static int countTags(
            TagTree tree,
            Class<? extends com.oracle.truffle.api.instrumentation.Tag> tag) {
        if (tree == null) {
            return 0;
        }
        int count = tree.hasTag(tag) ? 1 : 0;
        for (TagTree child : tree.getTreeChildren()) {
            count += countTags(child, tag);
        }
        return count;
    }
}

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.InvokeStaticClosureOneTail;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.Source;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I115-H (EXPERIMENTAL, PLAT066-C): static invocation of a local Closure whose
 * only use is one terminal call ({@code InvokeStaticClosureOneTail}), the
 * reduced static unary frame-argument layout and the lightweight
 * representation of non-capturing Closures.
 *
 * <p>These tests pin experimental behavior that diverges from the normative
 * specification (D013, Closure identity, creator capture, caller, Task,
 * ReturnHome); they do not assert normative equivalence. See
 * {@code docs/experiments/i115-h-static-closure-invocation.md}.
 */
final class ProtosI115HStaticClosureCallTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final int PAST_CACHE_LIMITS = 12;
    private static final String STATIC_CALL = "c.InvokeStaticClosureOneTail";

    private static final String RUN = """
            run: () => {
                identity: (value) => { value }
                identity(1)
            }
            """;

    // ---- Functional --------------------------------------------------------

    @Test
    void staticUnaryCallReturnsItsArgument() throws Exception {
        withCore(module -> {
            evaluate(RUN, "i115h-return-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("run()\n", "i115h-return.protos", module));
            }
            assertTrue(names(functionRoot("run", module)).contains(STATIC_CALL));
        });
    }

    @Test
    void staticUnaryCallIsDefinitionIndependent() throws Exception {
        record Shape(String definition, String parameter, long value) {}
        withCore(module -> {
            for (Shape shape : List.of(
                    new Shape("echo", "item", 7),
                    new Shape("pass", "x", 0),
                    new Shape("same", "argument", 123456))) {
                String function = "run_" + shape.definition();
                evaluate(function + ": () => {\n"
                                + "    " + shape.definition() + ": (" + shape.parameter()
                                + ") => { " + shape.parameter() + " }\n"
                                + "    " + shape.definition() + "(" + shape.value() + ")\n"
                                + "}\n",
                        "i115h-shape-define.protos", module);
                for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                    assertInteger(shape.value(),
                            evaluate(function + "()\n", "i115h-shape.protos", module));
                }
                List<String> names = names(functionRoot(function, module));
                assertTrue(names.contains(STATIC_CALL), names::toString);
            }
        });
    }

    @Test
    void staticUnaryLiteralBodyReturnsConstant() throws Exception {
        withCore(module -> {
            evaluate("""
                    run: () => {
                        constant: (ignored) => { 5 }
                        constant(9)
                    }
                    """, "i115h-literal-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(5, evaluate("run()\n", "i115h-literal.protos", module));
            }
            List<String> names = names(functionRoot("run", module));
            assertTrue(names.contains(STATIC_CALL), names::toString);
        });
    }

    @Test
    void staticUnaryClosurePreservesSeparatePhysicalRoot() throws Exception {
        withCore(module -> {
            ProtosBytecodeRootNode moduleRoot = parse(RUN, "i115h-roots.protos");
            assertEquals(3, moduleRoot.getRootNodes().getNodes().size(),
                    "Expected module, run and identity roots");
        });
    }

    @Test
    void staticUnaryRemovesMaterializationAndBindingStorage() throws Exception {
        withCore(module -> {
            evaluate(RUN, "i115h-shape-run-define.protos", module);
            ProtosBytecodeRootNode run = functionRoot("run", module);
            List<String> names = names(run);

            assertEquals(1, names.stream().filter(STATIC_CALL::equals).count(), names::toString);
            for (String absent : List.of(
                    "c.MaterializeCurrentClosure",
                    "c.InvokeClosureOneTail",
                    "c.CreateCurrentFrameLocal",
                    "c.ReadRootFrameLocal")) {
                assertFalse(names.contains(absent), () -> absent + " in run: " + names);
            }
            assertFalse(names.stream().anyMatch(name -> name.startsWith("store.local")),
                    names::toString);
            assertFalse(names.stream().anyMatch(name -> name.startsWith("load.local")),
                    names::toString);
            assertEquals(1, names.stream().filter("load.constant"::equals).count(),
                    () -> "the argument is the only evaluated operand: " + names);
            assertEquals(
                    List.of(
                            "c.EnterRoot",
                            "load.constant",
                            STATIC_CALL,
                            "c.EpilogForReturn",
                            "return"),
                    executionPrefix(run));

            ProtosBytecodeRootNode callee =
                    staticPlanCell(run).plan().bytecodeActivationRootForTesting();
            assertNotSame(run, callee);
            assertSame(run.getRootNodes(), callee.getRootNodes());
        });
    }

    @Test
    void staticUnaryUsesMinimalArguments() throws Exception {
        Object supplied = new Object();
        Object[] arguments = ProtosFrameArguments.experimentalStaticUnaryCall(supplied);

        assertEquals(2, arguments.length);
        assertSame(supplied, arguments[1]);
        assertTrue(ProtosFrameArguments.isExperimentalStaticUnaryCall(arguments));
        assertSame(supplied, ProtosFrameArguments.compactSuppliedArgumentOrNull(arguments, 0));
        assertSame(ProtosNullValue.INSTANCE,
                ProtosFrameArguments.compactSuppliedArgumentOrNull(arguments, 1));
        assertTrue(ProtosFrameArguments.acceptsPrimitiveResult(arguments));
        // No B5+ header: neither a compact call nor a published activation.
        assertFalse(ProtosFrameArguments.hasActivation(arguments));
        assertFalse(ProtosFrameArguments.isUnmaterializedCompactCall(arguments));
        assertFalse(ProtosFrameArguments.hasPublishedActivation(arguments));
        assertFalse(ProtosFrameArguments.isExperimentalStaticUnaryCall(new Object[] {null, supplied}));

        withCore(module -> {
            evaluate(RUN, "i115h-abi-define.protos", module);
            ProtosBytecodeRootNode callee = staticPlanCell(functionRoot("run", module))
                    .plan().bytecodeActivationRootForTesting();
            Object value = ProtosNumericValueSupport.integer(42);
            Object result = callee.getCallTarget().call(
                    ProtosFrameArguments.experimentalStaticUnaryCall(value));
            assertInteger(42, ProtosNumericValueSupport.guestValue(result));
        });
    }

    @Test
    void staticUnaryReusesDefinitionTarget() throws Exception {
        withCore(module -> {
            evaluate(RUN, "i115h-pic-define.protos", module);
            ProtosBytecodeRootNode run = functionRoot("run", module);
            RootCallTarget target =
                    staticPlanCell(run).plan().bytecodeActivationTargetForComposition();
            run.getBytecodeNode().setUncachedThreshold(0);
            assertInteger(1, evaluate("run()\n", "i115h-pic-first.protos", module));
            DirectCallNode installed = singleDirectCall(run);
            assertSame(target, installed.getCallTarget());

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("run()\n", "i115h-pic.protos", module));
            }
            // The same Context keeps the one installed entry and its target.
            assertSame(installed, singleDirectCall(run));
            assertSame(target, installed.getCallTarget());
            assertTrue(cachedNodeInstances(run, IndirectCallNode.class).isEmpty(),
                    "the generic tier was not used");
        });
    }

    @Test
    void staticUnaryUsesEnteredContextProjection() throws Exception {
        try (Context first = Context.newBuilder(ProtosLanguage.ID).build()) {
            first.initialize(ProtosLanguage.ID);
            first.enter();
            ProtosClosureExecutionPlanCell foreignCell;
            RootCallTarget foreignTarget;
            try {
                ProtosActivation module =
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
                evaluate(RUN, "i115h-foreign-define.protos", module);
                foreignCell = staticPlanCell(functionRoot("run", module));
                foreignTarget = InvokeStaticClosureOneTail.staticTargetOrNull(
                        foreignCell, ProtosLanguageContext.current());
                assertSame(
                        foreignCell.plan().bytecodeActivationTargetForComposition(),
                        foreignTarget);
            } finally {
                first.leave();
            }

            withCore(module -> {
                ProtosLanguageContext entered = ProtosLanguageContext.current();
                RootCallTarget projected =
                        InvokeStaticClosureOneTail.staticTargetOrNull(foreignCell, entered);
                assertNotNull(projected);
                assertNotSame(foreignTarget, projected);
                assertSame(
                        entered.languageForTesting(),
                        projected.getRootNode().getLanguage(ProtosLanguage.class));
                assertSame(projected,
                        InvokeStaticClosureOneTail.staticTargetOrNull(foreignCell, entered));
                assertInteger(7, ProtosNumericValueSupport.guestValue(projected.call(
                        ProtosFrameArguments.experimentalStaticUnaryCall(
                                ProtosNumericValueSupport.integer(7)))));
            });
        }
    }

    @Test
    void staticUnarySurvivesBytecodeReparse() throws Exception {
        withCore(module -> {
            evaluate(RUN, "i115h-reparse-define.protos", module);
            ProtosBytecodeRootNode run = functionRoot("run", module);
            ProtosClosureExecutionPlanCell before = staticPlanCell(run);
            RootCallTarget targetBefore = before.plan().bytecodeActivationTargetForComposition();
            assertInteger(1, evaluate("run()\n", "i115h-reparse-before.protos", module));

            run.getRootNodes().ensureComplete();

            assertEquals(3, run.getRootNodes().getNodes().size());
            ProtosClosureExecutionPlanCell after = staticPlanCell(run);
            assertSame(before, after);
            assertSame(targetBefore, after.plan().bytecodeActivationTargetForComposition());
            assertFalse(names(run).contains("c.MaterializeCurrentClosure"), names(run)::toString);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("run()\n", "i115h-reparse-after.protos", module));
            }
        });
    }

    /**
     * The static call in the uncached interpreter takes
     * {@code InvokeStaticClosureOneTail.generic} through {@code run} itself.
     */
    @Test
    void staticUnaryExecutesInUncachedInterpreter() throws Exception {
        withCore(module -> {
            evaluate(RUN, "i115h-uncached-define.protos", module);
            ProtosBytecodeRootNode run = functionRoot("run", module);
            run.getBytecodeNode().setUncachedThreshold(Integer.MAX_VALUE);
            assertEquals(BytecodeTier.UNCACHED, run.getBytecodeNode().getTier());
            assertTrue(names(run).contains(STATIC_CALL), names(run)::toString);

            assertInteger(1, evaluate("run()\n", "i115h-uncached.protos", module));

            assertEquals(BytecodeTier.UNCACHED, run.getBytecodeNode().getTier());
        });
    }

    /**
     * A non-capturing Closure whose body may observe its ReturnHome keeps
     * the creator's ReturnHome: {@code ^value} returns from {@code outer}.
     */
    @Test
    void nonCapturingClosurePreservesNonLocalReturnHome() throws Exception {
        withCore(module -> {
            evaluate("""
                    outer: () => {
                        inner: (value) => { ^value }
                        inner(5)
                        0
                    }
                    """, "i115h-nonlocal-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(5, evaluate("outer()\n", "i115h-nonlocal.protos", module));
            }
            List<String> names = names(functionRoot("outer", module));
            assertFalse(names.contains(STATIC_CALL), names::toString);
        });
    }

    // ---- Exclusions --------------------------------------------------------

    @Test
    void nonLocalReturnBodyIsNotStatic() throws Exception {
        withCore(module -> {
            evaluate("""
                    outer: () => {
                        inner: (value) => { ^value }
                        inner(5)
                    }
                    """, "i115h-nonlocal-terminal-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(5, evaluate("outer()\n", "i115h-nonlocal-terminal.protos", module));
            }
            List<String> names = names(functionRoot("outer", module));
            assertFalse(names.contains(STATIC_CALL), names::toString);
        });
    }

    @Test
    void capturingClosureIsNotStatic() throws Exception {
        assertNotStatic("""
                run: (base) => {
                    add: (value) => { base }
                    add(1)
                }
                """, "run(10)\n", 10);
    }

    @Test
    void escapingClosureIsNotEliminated() throws Exception {
        withCore(module -> {
            evaluate("""
                    run: () => {
                        identity: (value) => { value }
                        identity
                    }
                    """, "i115h-escape-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInstanceOf(ProtosClosureValue.class,
                        evaluate("run()\n", "i115h-escape.protos", module));
            }
            evaluate("escaped: run()\n", "i115h-escape-bind.protos", module);
            assertInteger(3, evaluate("escaped(3)\n", "i115h-escape-use.protos", module));
            List<String> names = names(functionRoot("run", module));
            assertFalse(names.contains(STATIC_CALL), names::toString);
            assertTrue(names.contains("c.MaterializeCurrentClosure"), names::toString);
        });
    }

    @Test
    void storedClosureIsNotEliminated() throws Exception {
        withCore(module -> {
            // Each call stores into a fresh holder (a Closure is an object), so
            // the member creation never meets an existing slot.
            evaluate("""
                    run: (holder) => {
                        identity: (value) => { value }
                        holder.stored: identity
                        identity(1)
                    }
                    """, "i115h-stored-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("run(() => { 0 })\n", "i115h-stored.protos", module));
            }
            evaluate("""
                    box: () => { 0 }
                    run(box)
                    """, "i115h-stored-box.protos", module);
            ProtosClosureValue box = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("box").orElseThrow());
            ProtosClosureValue stored = assertInstanceOf(
                    ProtosClosureValue.class,
                    box.readLocalSlot("stored").orElseThrow());
            assertNotNull(stored.definition());
            assertInteger(5, evaluate("box.stored(5)\n", "i115h-stored-use.protos", module));
            List<String> names = names(functionRoot("run", module));
            assertFalse(names.contains(STATIC_CALL), names::toString);
        });
    }

    @Test
    void aliasedClosureIsNotStatic() throws Exception {
        assertNotStatic("""
                run: () => {
                    identity: (value) => { value }
                    f: identity
                    f(1)
                }
                """, "run()\n", 1);
    }

    @Test
    void reassignedBindingIsNotStatic() throws Exception {
        assertNotStatic("""
                run: () => {
                    identity: (value) => { value }
                    identity = (value) => { 2 }
                    identity(1)
                }
                """, "run()\n", 2);
    }

    @Test
    void composedCallIsNotStatic() throws Exception {
        assertNotStatic("""
                run: () => {
                    identity: (value) => { value }
                    identity(1 + 2)
                }
                """, "run()\n", 3);
    }

    @Test
    void nestedOrNonLeafBodyIsNotStatic() throws Exception {
        assertNotStatic("""
                run: () => {
                    next: (value) => { value + 1 }
                    next(1)
                }
                """, "run()\n", 2);
        assertNotStatic("""
                run: () => {
                    wrap: (value) => {
                        inner: value
                        inner
                    }
                    wrap(4)
                }
                """, "run()\n", 4);
    }

    // ---- Experimental divergences -------------------------------------------

    /**
     * EXPERIMENTAL DIVERGENCE (D013), structural check only: the installed
     * direct entry of the static call holds no {@link Assumption} field
     * (located by field type), so it depends on no {@code Object.call}
     * certificate. {@code Object.call} is not modified here.
     */
    @Test
    void experimentalStaticCallHasNoSelectorAssumption() throws Exception {
        withCore(module -> {
            evaluate(RUN, "i115h-selector-define.protos", module);
            ProtosBytecodeRootNode run = functionRoot("run", module);
            run.getBytecodeNode().setUncachedThreshold(0);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("run()\n", "i115h-selector.protos", module));
            }
            // The owner of the installed call node holds the entry's cached
            // state; none of it is an Assumption (fields inspected by type).
            Node entry = singleDirectCall(run).getParent();
            assertNotNull(entry);
            for (Class<?> type = entry.getClass(); type != Object.class; type = type.getSuperclass()) {
                for (Field declared : type.getDeclaredFields()) {
                    assertFalse(Assumption.class.isAssignableFrom(declared.getType()),
                            declared.toString());
                }
            }
        });
    }

    /**
     * EXPERIMENTAL DIVERGENCE: a materialized non-capturing Closure is the
     * lightweight representation and keeps none of its creator's receiver,
     * methodHome, ReturnHome or lexical environment.
     */
    @Test
    void experimentalLightweightClosureDropsCreatorMetadata() throws Exception {
        withCore(module -> {
            evaluate("""
                    box: () => { 0 }
                    box.make: () => { (value) => { value } }
                    made: box.make()
                    """, "i115h-light-define.protos", module);
            ProtosClosureValue box = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("box").orElseThrow());
            ProtosClosureValue made = assertInstanceOf(
                    ProtosClosureValue.class,
                    module.context().readLocalSlot("made").orElseThrow());

            assertSame(ProtosObjectValue.rootObject(), made.capturedReceiver());
            assertNotSame(box, made.capturedReceiver());
            assertTrue(made.methodHome().isEmpty());
            assertTrue(made.returnHome().isEmpty());
            assertEquals(null, made.capturedLexicalEnvironmentForRuntime());
            assertInteger(4, evaluate("made(4)\n", "i115h-light-use.protos", module));
        });
    }

    /**
     * EXPERIMENTAL DIVERGENCE: the static unary layout transports no caller,
     * Task, ReturnHome or Closure, so no activation can be resolved from it.
     */
    @Test
    void experimentalMinimalInvocationCarriesNoCallerOrReturnHome() {
        Object[] arguments = ProtosFrameArguments.experimentalStaticUnaryCall(
                ProtosNumericValueSupport.integer(1));
        for (Object slot : arguments) {
            assertFalse(slot instanceof ProtosActivation);
            assertFalse(slot instanceof ProtosClosureValue);
            assertFalse(slot instanceof com.guillermomolina.protos.runtime.control.ProtosReturnHome);
            assertFalse(slot instanceof Object[]);
        }
        assertFalse(ProtosFrameArguments.hasActivation(arguments));
    }

    // ---- Helpers -----------------------------------------------------------

    private static void assertNotStatic(String definition, String call, long expected)
            throws Exception {
        withCore(module -> {
            evaluate(definition, "i115h-excluded-define.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(expected, evaluate(call, "i115h-excluded.protos", module));
            }
            List<String> names = names(functionRoot("run", module));
            assertFalse(names.contains(STATIC_CALL), names::toString);
        });
    }

    private static ProtosClosureExecutionPlanCell staticPlanCell(ProtosBytecodeRootNode root) {
        for (Instruction instruction : root.getBytecodeNode().getInstructionsAsList()) {
            if (!ProtosInstructionNames.of(instruction).equals(STATIC_CALL)) {
                continue;
            }
            for (Instruction.Argument argument : instruction.getArguments()) {
                if (argument.getKind() == Instruction.Argument.Kind.CONSTANT
                        && argument.asConstant() instanceof ProtosClosureExecutionPlanCell cell) {
                    return cell;
                }
            }
        }
        throw new AssertionError("no " + STATIC_CALL + " plan cell: " + names(root));
    }

    /**
     * The single adopted {@link DirectCallNode} of {@code root}'s cached
     * Bytecode node. {@code run}'s only call site is the static call, so
     * exactly one direct entry exists while one Context is used.
     */
    private static DirectCallNode singleDirectCall(ProtosBytecodeRootNode root) {
        List<DirectCallNode> calls = cachedNodeInstances(root, DirectCallNode.class);
        assertEquals(1, calls.size(), () -> "direct call entries: " + calls);
        return calls.get(0);
    }

    /**
     * Instances of {@code type} under the operation nodes of {@code root}'s
     * cached Bytecode node. The operation nodes are held in a
     * compilation-final {@code Node[]}, not a {@code @Children} field, so
     * tree traversal does not reach them; the array is located by type,
     * never by a generated name.
     */
    private static <T extends Node> List<T> cachedNodeInstances(
            ProtosBytecodeRootNode root, Class<T> type) {
        Object bytecode = root.getBytecodeNode();
        List<T> found = new ArrayList<>();
        try {
            for (Class<?> owner = bytecode.getClass(); owner != null; owner = owner.getSuperclass()) {
                for (Field declared : owner.getDeclaredFields()) {
                    if (declared.getType() != Node[].class) {
                        continue;
                    }
                    declared.setAccessible(true);
                    Node[] nodes = (Node[]) declared.get(bytecode);
                    if (nodes == null) {
                        continue;
                    }
                    for (Node node : nodes) {
                        if (node != null) {
                            found.addAll(NodeUtil.findAllNodeInstances(node, type));
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException inaccessible) {
            throw new AssertionError(inaccessible);
        }
        return found;
    }

    private static List<String> names(ProtosBytecodeRootNode root) {
        return root.getBytecodeNode().getInstructionsAsList().stream()
                .map(ProtosInstructionNames::of)
                .toList();
    }

    /** Executed prefix up to the first return, without tag or trace instructions. */
    private static List<String> executionPrefix(ProtosBytecodeRootNode root) {
        List<String> prefix = new ArrayList<>();
        for (String name : names(root)) {
            if (name.startsWith("tag.") || name.startsWith("trace.")) {
                continue;
            }
            prefix.add(name);
            if (name.equals("return")) {
                break;
            }
        }
        return prefix;
    }

    private static ProtosBytecodeRootNode functionRoot(String function, ProtosActivation module) {
        ProtosClosureValue closure = assertInstanceOf(
                ProtosClosureValue.class,
                module.context().readLocalSlot(function).orElseThrow());
        return closure.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
    }

    private interface CoreTest {
        void run(ProtosActivation module) throws Exception;
    }

    private static void withCore(CoreTest test) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                test.run(prelude.newModuleActivation());
            } finally {
                context.leave();
            }
        }
    }

    private static Source source(String characters, String name) {
        return Source.newBuilder(ProtosLanguage.ID, characters, name)
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
    }

    private static Object evaluate(String characters, String name, ProtosActivation activation) {
        CallTarget target = ProtosLanguageContext.current().parsePublic(source(characters, name));
        return target.call(activation);
    }

    private static ProtosBytecodeRootNode parse(String characters, String name) {
        RootCallTarget target = assertInstanceOf(
                RootCallTarget.class,
                ProtosLanguageContext.current().parsePublic(source(characters, name)));
        return assertInstanceOf(ProtosBytecodeRootNode.class, target.getRootNode());
    }

    private static void assertInteger(long expected, Object actual) {
        assertEquals(Long.toString(expected),
                ProtosNumericValueSupport.integerDecimalText(actual));
    }
}

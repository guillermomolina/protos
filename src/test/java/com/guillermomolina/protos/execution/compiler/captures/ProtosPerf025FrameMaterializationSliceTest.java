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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosLexicalCell;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.Source;
import java.lang.reflect.Field;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF025 frame-materialization slice focal evidence, reconciled with I115:
 * declaring a local or a parameter never forces a Closure root to publish
 * its activation or materialize its guest Context. A Closure binding lives in
 * native Bytecode DSL storage (a LOCAL local, or a CELL held in a local and
 * shared with every capturer); a module binding lives in its module context
 * (NAME). No root installs a frame lexical authority any more.
 *
 * <p>KNOWN I115 SEMANTIC DIVERGENCE: a Closure activation's Context holds
 * only NAME bindings. Assertions that the specified semantics make about a
 * Closure's own bindings through its Context are kept as they were and are
 * marked where they depend on that divergence.
 */
final class ProtosPerf025FrameMaterializationSliceTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    /* Retired frame-authority operations; none may reappear. */
    private static final List<String> RETIRED =
            List.of(
                    "InstallFrameLexicalAuthority",
                    "EnsureLiveFrameLexicalAuthority",
                    "BindUnaryFrameParameter",
                    "BindClosureFrameParameter",
                    "ReadRootFrameLocal",
                    "AssignRootFrameLocal",
                    "CreateCurrentFrameLocal");

    @Test
    void parameterOnlyClosureRunsWithoutPublishingItsActivation() throws Exception {
        withFixture(
                fixture -> {
                    ClosureUnderTest plan = closurePlan(fixture, "(value) => value");
                    List<String> names = instructionNames(activationNode(plan));
                    assertNoRetired(names);
                    // The parameter is copied once into its frame local on entry.
                    assertContains(names, "CopySuppliedArguments");
                    assertContains(names, "load.local");
                    assertNone(names, "CreateCell");
                    assertNone(names, "CreateCurrentLocalSlot");

                    ProtosObjectValue argument = newObject();
                    ProtosActivation invocation = deferredInvocation(fixture, plan.closure, List.of(argument));
                    assertSame(argument, plan.plan.executeBytecodeActivationForTesting(invocation));
                    assertNull(privateField(invocation, "context"));
                    assertNull(privateField(invocation, "currentEnvironment"));
                });
        System.out.println("PARAMETER_ONLY_REQUIRES_MATERIALIZED_FRAME=NO");
    }

    /**
     * PLAT067-B: a proven scalar body keeps its bindings in Bytecode locals for every Context. An
     * activation observed before entry keeps its identity and the result, and no eliminated
     * binding is fabricated on it.
     */
    @Test
    void perf034ScalarBodyWithObservedActivationFabricatesNoEliminatedBinding()
            throws Exception {
        withFixture(fixture -> {
            ClosureUnderTest plan =
                    closurePlan(
                            fixture,
                            "() => { first: 7\nsecond: 9\nfirst }");

            // An unobserved rich carrier still materializes nothing.
            ProtosActivation unobserved =
                    deferredInvocation(
                            fixture, plan.closure, List.of());
            assertEquals(
                    7L,
                    unwrapNumber(
                            plan.plan.executeBytecodeActivationForTesting(
                                    unobserved)));
            assertNull(privateField(unobserved, "context"));
            assertNull(privateField(unobserved, "currentEnvironment"));

            // Observation before entry.
            ProtosActivation invocation =
                    deferredInvocation(
                            fixture, plan.closure, List.of());
            ProtosObjectValue observedContext = invocation.context();

            assertEquals(
                    7L,
                    unwrapNumber(
                            plan.plan.executeBytecodeActivationForTesting(
                                    invocation)));

            assertSame(observedContext, invocation.context());
            assertTrue(
                    invocation.context().readLocalSlot("first").isEmpty(),
                    "an eliminated binding is never fabricated");
            assertTrue(
                    invocation.context().readLocalSlot("second").isEmpty(),
                    "an eliminated binding is never fabricated");
        });
    }

    @Test
    void localOnlyClosureStaysInNativeLocals() throws Exception {
        withFixture(
                fixture -> {
                    ClosureUnderTest plan =
                            closurePlan(fixture, "(a) => { b: a\n" + "c: b\n" + "c = b\n" + "c }");
                    List<String> names = instructionNames(activationNode(plan));
                    assertNoRetired(names);
                    assertContains(names, "store.local");
                    assertNone(names, "CreateCell");
                    assertNone(names, "CreateCurrentLocalSlot");
                    assertNone(names, "AccessNamedLexical");

                    ProtosObjectValue argument = newObject();
                    ProtosActivation invocation = deferredInvocation(fixture, plan.closure, List.of(argument));
                    assertSame(argument, plan.plan.executeBytecodeActivationForTesting(invocation));
                    assertNull(privateField(invocation, "context"));
                    assertNull(privateField(invocation, "currentEnvironment"));
                });
        System.out.println("LOCAL_ONLY_REQUIRES_MATERIALIZED_FRAME=NO");
    }

    @Test
    void sequentialDefaultsSeeOnlyEarlierEstablishedParameters() throws Exception {
        withFixture(
                fixture -> {
                    ClosureUnderTest plan = closurePlan(fixture, "(a, b: a, c: b) => c");
                    assertNoRetired(instructionNames(activationNode(plan)));
                    ProtosObjectValue first = newObject();
                    ProtosObjectValue second = newObject();
                    assertSame(first, plan.plan.executeBytecodeActivationForTesting(
                            deferredInvocation(fixture, plan.closure, List.of(first))));
                    assertSame(second, plan.plan.executeBytecodeActivationForTesting(
                            deferredInvocation(fixture, plan.closure, List.of(first, second))));
                    // D203/I105: excess arguments are ignored.
                    assertSame(second, plan.plan.executeBytecodeActivationForTesting(
                            deferredInvocation(fixture, plan.closure, List.of(first, second, second, first))));
                });
        System.out.println("SEQUENTIAL_PARAMETERS_DEFAULTS=PASS");
    }

    @Test
    void duplicateFrameNativeCreationIsTheExactCreationError() throws Exception {
        withFixture(
                fixture -> {
                    ClosureUnderTest plan = closurePlan(fixture, "(a) => { b: a\n" + "b: a }");
                    assertNoRetired(instructionNames(activationNode(plan)));
                    ProtosSignalException signal =
                            assertThrows(
                                    ProtosSignalException.class,
                                    () -> plan.plan.executeBytecodeActivationForTesting(
                                            deferredInvocation(fixture, plan.closure, List.of(newObject()))));
                    assertSame(fixture.errorPrototype, signal.error().parent().orElseThrow());
                });
        System.out.println("DUPLICATE_CREATE_ERROR=PASS");
    }

    /**
     * An activation already observed before entry keeps its single Context
     * identity, and its bindings keep their values through the body. The
     * former in-frame authority transition no longer exists.
     */
    @Test
    void observedActivationKeepsItsContextIdentityAndPreservesBindings() throws Exception {
        withFixture(
                fixture -> {
                    ClosureUnderTest plan = closurePlan(fixture, "(value) => { other: value\n" + "other }");
                    ProtosObjectValue argument = newObject();
                    ProtosActivation invocation =
                            ProtosActivation.forClosureInvocation(
                                    plan.closure,
                                    List.of(argument),
                                    fixture.module.prelude().orElseThrow(),
                                    fixture.module.actorModuleState(),
                                    fixture.module.currentModuleKey().orElse(null),
                                    fixture.module.executionDomain());
                    ProtosObjectValue observed = invocation.context();
                    assertSame(argument, plan.plan.executeBytecodeActivationForTesting(invocation));
                    assertSame(observed, invocation.context());
                    assertNoRetired(instructionNames(activationNode(plan)));
                });
        System.out.println("OBSERVED_ACTIVATION_IDENTITY=PASS");
    }

    @Test
    void capturedEscapingClosureSharesItsCell() throws Exception {
        withFixture(
                fixture -> {
                    ClosureUnderTest plan = closurePlan(fixture, "(x) => { g: () => x\n" + "x = 5\n" + "g }");
                    List<String> names = instructionNames(activationNode(plan));
                    assertNoRetired(names);
                    assertContains(names, "CreateCell");
                    assertContains(names, "StoreCell");
                    assertContains(names, "CaptureClosureCells");

                    ProtosActivation invocation =
                            deferredInvocation(fixture, plan.closure, List.of(newObject()));
                    ProtosClosureValue escaped =
                            assertInstanceOf(
                                    ProtosClosureValue.class,
                                    plan.plan.executeBytecodeActivationForTesting(invocation));
                    ProtosLexicalCell[] cells = escaped.capturedCellsForRuntime();
                    assertEquals(1, cells.length);
                    assertEquals(5L, unwrapNumber(cells[0].valueOrNullForRuntime()),
                            "the assignment after the capture is seen through the shared cell");
                    assertNull(privateField(invocation, "context"),
                            "an escaping capture does not materialize the owner Context");

                    assertEquals(
                            6L,
                            unwrapNumber(run(fixture,
                                    "f: (x) => { set: (v) => { x = v }\n" + "get: () => x\n" + "set(6)\n" + "get }\n"
                                            + "f(1)()")));
                });
        System.out.println("CAPTURED_ESCAPE_SEMANTICS=PASS");
    }

    /**
     * Explicit {@code context} observation. The final guest assertion reads
     * a Closure's own LOCAL binding through its observed Context; it is the
     * specified behavior and depends on the KNOWN I115 SEMANTIC DIVERGENCE
     * that a Closure Context holds only NAME bindings.
     */
    @Test
    void explicitContextObservationPublishesOneContext() throws Exception {
        withFixture(
                fixture -> {
                    ClosureUnderTest plan = closurePlan(fixture, "(a) => context");
                    assertNoRetired(instructionNames(activationNode(plan)));
                    ProtosActivation invocation =
                            deferredInvocation(fixture, plan.closure, List.of(newObject()));
                    Object observed = plan.plan.executeBytecodeActivationForTesting(invocation);
                    assertSame(invocation.materializedContextOrNullForRuntime(), observed);
                    assertSame(observed, invocation.context());
                    assertEquals(
                            3L,
                            unwrapNumber(run(fixture,
                                    "f: (a) => { b: a\n" + "context }\n" + "c: f(3)\n" + "c.slotValue(\"b\")")));
                });
        System.out.println("CONTEXT_OBSERVATION=PASS");
    }

    /**
     * An own name read before its own binding point resolves through the
     * enclosing scope. The guest assertion is unchanged; see the report for
     * the I115 {@code UninitializedBinding} lowering of a proven-early LOCAL
     * read.
     */
    @Test
    void nonResolvedAccessToOwnNameFallsBackToTheEnclosingBinding() throws Exception {
        withFixture(
                fixture -> {
                    ClosureUnderTest plan =
                            closurePlan(fixture, "x: 7\n" + "(a) => { y: x\n" + "x: 2\n" + "y }");
                    assertNoRetired(instructionNames(activationNode(plan)));
                    assertEquals(7L, unwrapNumber(plan.plan.executeBytecodeActivationForTesting(
                            deferredInvocation(fixture, plan.closure, List.of(newObject())))));
                });
        System.out.println("CANDIDATE_DYNAMIC_FALLBACK=PASS");
    }

    /**
     * The observed Context of a Closure activation is its one execution
     * context identity, and dynamic NAME bindings created through it keep
     * exact establishment, removal and recreation order. The former frame
     * authority's compact/general bookkeeping no longer exists.
     */
    @Test
    void observedContextKeepsExactNameEstablishmentOrder() throws Exception {
        withFixture(
                fixture -> {
                    ClosureUnderTest plan =
                            closurePlan(
                                    fixture,
                                    "(value) => { other: value\n context }");
                    ProtosObjectValue argument = newObject();
                    ProtosActivation invocation =
                            ProtosActivation.forClosureInvocation(
                                    plan.closure,
                                    List.of(argument),
                                    fixture.module.prelude().orElseThrow(),
                                    fixture.module.actorModuleState(),
                                    fixture.module.currentModuleKey().orElse(null),
                                    fixture.module.executionDomain());

                    ProtosObjectValue observed =
                            assertInstanceOf(
                                    ProtosObjectValue.class,
                                    plan.plan.executeBytecodeActivationForTesting(
                                            invocation));
                    assertSame(invocation.context(), observed);

                    Object first = newObject();
                    Object second = newObject();
                    observed.createLocalSlot("dynamicFirst", first);
                    observed.createLocalSlot("dynamicSecond", second);
                    assertEquals(
                            List.of("dynamicFirst", "dynamicSecond"),
                            dynamicNames(observed));

                    assertSame(first, observed.removeLocalSlot("dynamicFirst"));
                    assertFalse(observed.hasLocalSlot("dynamicFirst"));
                    observed.createLocalSlot("dynamicFirst", first);
                    assertEquals(
                            List.of("dynamicSecond", "dynamicFirst"),
                            dynamicNames(observed),
                            "a recreated binding is ordered by its re-establishment");
                    assertSame(first, observed.readLocalSlot("dynamicFirst").orElseThrow());
                });
        System.out.println("OBSERVED_CONTEXT_ESTABLISHMENT_ORDER=PASS");
    }

    @Test
    void admittedModulePreservesCapturedBindingsAfterReturn()
            throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue moduleContext = fixture.module.context();

                    ProtosClosureValue value = assertInstanceOf(
                            ProtosClosureValue.class,
                            run(fixture,
                                    "reader: () => value\n"
                                            + "value: () => 7\n"
                                            + "reader()"));

                    ProtosClosureValue reader = assertInstanceOf(
                            ProtosClosureValue.class,
                            moduleContext.readLocalSlot("reader").orElseThrow());

                    assertSame(
                            value,
                            moduleContext.readLocalSlot("value").orElseThrow());
                    // PLAT066-C: the representation of the captured environment
                    // is not observable; the module bindings both Closures
                    // reach are, and keep their identity after the return.
                    assertSame(value, run(fixture, "reader()"));
                    assertSame(value, run(fixture, "reader()"));

                    moduleContext.removeLocalSlot("value");
                    assertThrows(
                            ProtosSignalException.class,
                            () -> run(fixture, "reader()"));

                    ProtosObjectValue replacement = newObject();
                    moduleContext.createLocalSlot("value", replacement);
                    assertSame(replacement, run(fixture, "reader()"));
                });
    }

    @Test
    void admittedModuleRetainsEarlierBindingsAcrossRootHandoff()
            throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue moduleContext = fixture.module.context();

                    assertEquals(
                            7L,
                            unwrapNumber(
                                    run(fixture,
                                            "reader: () => 7\n"
                                                    + "reader()")));

                    Object first = moduleContext
                            .readLocalSlot("reader")
                            .orElseThrow();

                    assertEquals(
                            11L,
                            unwrapNumber(
                                    run(fixture,
                                            "writer: () => 11\n"
                                                    + "writer()")));

                    assertSame(
                            first,
                            moduleContext
                                    .readLocalSlot("reader")
                                    .orElseThrow());
                    assertSame(moduleContext, fixture.module.context());

                    assertEquals(
                            7L,
                            unwrapNumber(run(fixture, "reader()")));
                });
    }

    @Test
    void admittedModulePreservesBindingsOnExceptionalExit()
            throws Exception {
        withFixture(
                fixture -> {
                    assertThrows(
                            ProtosSignalException.class,
                            () -> run(
                                    fixture,
                                    "reader: () => value\n"
                                            + "value: () => 7\n"
                                            + "missing()"));

                    ProtosObjectValue moduleContext = fixture.module.context();
                    Object value = moduleContext
                            .readLocalSlot("value").orElseThrow();
                    assertTrue(moduleContext.hasLocalSlot("reader"));
                    assertSame(value, run(fixture, "reader()"));
                });
    }

    @Test
    void noLexicalStorageOrToolingViewRetainsAVirtualFrame() {
        for (Class<?> owner :
                List.of(
                        ProtosLexicalCell.class,
                        ProtosLexicalEnvironment.class,
                        ProtosClosureValue.class,
                        ProtosBytecodeRootNode.class,
                        ProtosDebuggerScope.class,
                        ProtosActivation.class)) {
            for (Field field : owner.getDeclaredFields()) {
                // MaterializedFrame is itself a VirtualFrame subtype; only a
                // non-materialized frame type would be a raw VirtualFrame.
                assertFalse(
                        VirtualFrame.class.isAssignableFrom(field.getType())
                                && !MaterializedFrame.class.isAssignableFrom(field.getType()),
                        () -> owner.getSimpleName() + " retains a VirtualFrame: " + field.getName());
            }
        }
        System.out.println("RAW_VIRTUALFRAME_RETAINED_ACROSS_ROOT_LIFETIME=NO");
    }

    /**
     * I115 lazy capture: a capturing Closure root creates one cell per
     * captured binding and hands the same cell to every capturer, without
     * publishing its activation or materializing its Context; only an
     * explicit {@code context} observation materializes that Context, once.
     */
    @Test
    void captureSharesOneCellPerBindingWithoutMaterializingTheOwner() throws Exception {
        withFixture(
                fixture -> {
                    ClosureUnderTest lazy =
                            closurePlan(fixture,
                                    "(x) => { g: () => x\n" + "h: () => x\n" + "g }");
                    List<String> lazyNames = instructionNames(activationNode(lazy));
                    assertNoRetired(lazyNames);
                    assertContains(lazyNames, "CreateCell");
                    assertContains(lazyNames, "CaptureClosureCells");

                    ProtosObjectValue argument = newObject();
                    ProtosActivation unobserved =
                            deferredInvocation(fixture, lazy.closure, List.of(argument));
                    ProtosClosureValue g =
                            assertInstanceOf(
                                    ProtosClosureValue.class,
                                    lazy.plan.executeBytecodeActivationForTesting(unobserved));
                    assertSame(argument, g.capturedCellsForRuntime()[0].valueOrNullForRuntime());
                    assertNull(privateField(unobserved, "context"),
                            "capturing does not materialize the owner Context");

                    ClosureUnderTest observing =
                            closurePlan(fixture,
                                    "(x) => { g: () => x\n" + "h: () => x\n" + "y: x\n" + "context }");
                    assertNoRetired(instructionNames(activationNode(observing)));
                    ProtosActivation invocation =
                            deferredInvocation(fixture, observing.closure, List.of(argument));
                    ProtosObjectValue observed =
                            assertInstanceOf(
                                    ProtosObjectValue.class,
                                    observing.plan.executeBytecodeActivationForTesting(invocation));
                    assertSame(observed, invocation.context());
                    assertSame(observed, invocation.materializedContextOrNullForRuntime());
                });
        System.out.println("I115_LAZY_CAPTURE_SHARED_CELL=PASS");
    }

    private static List<String> dynamicNames(ProtosObjectValue context) {
        return context.localSlotsSnapshot().keySet().stream()
                .filter(name -> name.startsWith("dynamic"))
                .toList();
    }

    private static void assertNoRetired(List<String> names) {
        for (String retired : RETIRED) {
            assertNone(names, retired);
        }
    }

    private static void assertNone(List<String> names, String operation) {
        assertTrue(
                names.stream().noneMatch(name -> name.contains(operation)),
                () -> "unexpected " + operation + " in: " + names);
    }

    private static void assertContains(List<String> names, String operation) {
        assertTrue(
                names.stream().anyMatch(name -> name.contains(operation)),
                () -> "expected " + operation + " in: " + names);
    }

    private static List<String> instructionNames(BytecodeNode node) {
        return node.getInstructionsAsList().stream().map(Instruction::getName).toList();
    }

    private static BytecodeNode activationNode(ClosureUnderTest closure) {
        return closure.plan.bytecodeActivationRootForTesting().getBytecodeNode();
    }

    private static ProtosActivation deferredInvocation(
            Fixture fixture, ProtosClosureValue closure, List<?> supplied) {
        return ProtosActivation.forImmediateMethodInvocationWithReturnHomeForRuntime(
                closure,
                supplied,
                newObject(),
                newObject(),
                fixture.module.prelude().orElseThrow(),
                fixture.module.actorModuleState(),
                fixture.module.currentModuleKey().orElse(null),
                fixture.module.executionDomain(),
                new ProtosReturnHome());
    }

    private record ClosureUnderTest(ProtosClosureValue closure, ProtosClosureExecutionPlan plan) {}

    private record Fixture(
            ProtosLanguage language,
            ProtosActivation module,
            ProtosObjectValue errorPrototype) {}

    private interface FixtureTest {
        void run(Fixture fixture) throws Exception;
    }

    private static void withFixture(FixtureTest test) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosStandardObjectProtocol.install();

                ProtosObjectValue contextPrototype = newObject();
                ProtosObjectValue bindings = new ProtosObjectValue(contextPrototype);
                ProtosObjectValue errorPrototype = newObject();
                bindings.createLocalSlot("Context", contextPrototype);
                bindings.createLocalSlot("Error", errorPrototype);
                bindings.createLocalSlot("SlotNotFound", new ProtosObjectValue(errorPrototype));
                bindings.createLocalSlot("Array", newObject());
                bindings.freeze();

                test.run(
                        new Fixture(
                                LANGUAGE_REF.get(null),
                                new ProtosPrelude(bindings, contextPrototype).newModuleActivation(),
                                errorPrototype));
            } finally {
                context.leave();
            }
        }
    }

    /** Runs a module source whose final expression is the Closure under test. */
    private static ClosureUnderTest closurePlan(Fixture fixture, String characters) {
        ProtosClosureValue closure =
                assertInstanceOf(ProtosClosureValue.class, run(fixture, characters));
        return new ClosureUnderTest(closure, closure.executionPlan().orElseThrow());
    }

    private static Object run(Fixture fixture, String characters) {
        Source source = Source.newBuilder(ProtosLanguage.ID, characters, "perf025-frame.protos").build();
        CanonicalSequence canonical =
                (CanonicalSequence) new Canonicalizer().canonicalize(new ProtosParser(characters).parseProgram());
        return new CanonicalToBytecodeLowerer(
                        fixture.language,
                        source)
                .lowerRoot(canonical)
                .getCallTarget()
                .call(fixture.module);
    }

    private static ProtosObjectValue newObject() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    private static Object privateField(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static long unwrapNumber(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        throw new AssertionError("expected a Protos integer result, got: " + value);
    }
}

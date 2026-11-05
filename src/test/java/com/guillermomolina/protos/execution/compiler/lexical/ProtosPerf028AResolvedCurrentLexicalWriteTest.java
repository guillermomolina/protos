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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF028-A focal evidence, reconciled with I115: a bare assignment
 * statically {@code Resolved} to the running module's NAME binding compiles
 * to {@code SelectWriteGlobal} (the destination, selected before the RHS)
 * followed by {@code WriteGlobal}; one resolved to the running Closure's own
 * LOCAL binding compiles to a native local store. Neither uses the
 * String-keyed {@code ResolveWritableLexicalTarget}/
 * {@code AssignResolvedLexicalTarget} pair. Static resolution proves identity,
 * not presence (D179 C0), so a cleared current local selects the exact generic
 * fallback before RHS evaluation, and the selected destination is retained
 * through RHS effects, suspension, and resumption without re-resolution
 * ({@code EXECUTION_AND_CONTROL.md} §7).
 */
final class ProtosPerf028AResolvedCurrentLexicalWriteTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    private static final String SELECT_GLOBAL = "SelectWriteGlobal";
    private static final String WRITE_GLOBAL = "c.WriteGlobal";
    private static final String STORE_LOCAL = "store.local";
    private static final String LOAD_LOCAL = "load.local";
    private static final String RESOLVE_GENERIC = "ResolveWritableLexicalTarget";
    private static final String ASSIGN_GENERIC = "AssignResolvedLexicalTarget";

    @Test
    void presentCurrentWriteUsesConstantAccessorAndReturnsTheExactRhs() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue seed = newObject();
                    ProtosObjectValue replacement = newObject();
                    fixture.module.context().createLocalSlot("seed", seed);
                    fixture.module.context().createLocalSlot("replacement", replacement);

                    ProtosBytecodeRootNode root =
                            lower(
                                    fixture.language,
                                    "x: seed\n"
                                            + "r: (x = replacement)\n"
                                            + "context.slotValue(\"x\")");
                    assertModuleWritePath(root.getBytecodeNode());

                    // Guest reflection and host reflection observe the one
                    // authoritative binding the accessor wrote.
                    assertSame(replacement, root.getCallTarget().call(fixture.module));
                    assertSame(replacement, slot(fixture.module, "x"));
                    assertSame(replacement, slot(fixture.module, "r"));
                });
        System.out.println("CURRENT_RESOLVED_WRITE_USES_CONSTANT_LOCAL_ACCESSOR=YES");
        System.out.println("ASSIGNMENT_RESULT_IS_RHS=PASS");
    }

    @Test
    void currentAbsentAtSelectionWritesNearestOuterLexicalBinding() throws Exception {
        withFixture(
                fixture -> {
                    ClosureUnderTest plan =
                            closurePlan(
                                    fixture,
                                    "x: 1\n"
                                            + "() => { x: 2\n"
                                            + "context.removeSlot(\"x\")\n"
                                            + "x = 4 }");
                    assertLocalWritePath(activationNode(plan));

                    ProtosActivation invocation = fixture.invocationOf(plan.closure);
                    assertEquals(4L, unwrapNumber(plan.plan.executeBytecodeActivationForTesting(invocation)));

                    assertEquals(4L, unwrapNumber(slot(fixture.module, "x")));
                    assertFalse(invocation.context().hasLocalSlot("x"));
                });
        System.out.println("CURRENT_ABSENT_EXACT_FALLBACK=PASS");
    }

    @Test
    void currentAbsentAtSelectionWritesReceiverOwnSlot() throws Exception {
        withFixture(
                fixture -> {
                    Object result =
                            run(
                                    fixture,
                                    "obj: { x: 5\n"
                                            + "m: () => { x: 0\n"
                                            + "context.removeSlot(\"x\")\n"
                                            + "x = 7 } }\n"
                                            + "obj.m()\n"
                                            + "obj.x");
                    assertEquals(7L, unwrapNumber(result));
                    assertFalse(fixture.module.context().hasLocalSlot("x"));
                });
        System.out.println("CURRENT_ABSENT_RECEIVER_FALLBACK=PASS");
    }

    @Test
    void noDestinationSignalsSlotNotFoundBeforeRhsEvaluation() throws Exception {
        withFixture(
                fixture -> {
                    int[] rhsEvaluations = new int[1];
                    fixture.module.context().createLocalSlot(
                            "touch",
                            ProtosClosureValue.nativeClosure(
                                    (nativeActivation, supplied) -> {
                                        rhsEvaluations[0]++;
                                        return ProtosNullValue.INSTANCE;
                                    }));

                    ProtosBytecodeRootNode root =
                            lower(
                                    fixture.language,
                                    "x: 1\n" + "context.removeSlot(\"x\")\n" + "x = touch()");
                    assertModuleWritePath(root.getBytecodeNode());

                    ProtosSignalException signal =
                            assertThrows(
                                    ProtosSignalException.class,
                                    () -> root.getCallTarget().call(fixture.module));
                    assertSame(fixture.slotNotFoundPrototype, parentOf(signal));
                    assertEquals(0, rhsEvaluations[0]);
                    assertFalse(fixture.module.context().hasLocalSlot("x"));
                });
        System.out.println("DESTINATION_SELECTED_BEFORE_RHS=PASS");
    }

    @Test
    void currentSelectedBeforeRhsIsNotRetargetedBySameNameCreationElsewhere() throws Exception {
        withFixture(
                fixture -> {
                    ClosureUnderTest plan =
                            closurePlan(
                                    fixture,
                                    "outer: context\n"
                                            + "makeOuter: () => { outer.x: 5\n"
                                            + "6 }\n"
                                            + "() => { x: 2\n"
                                            + "x = makeOuter()\n"
                                            + "x }");
                    assertLocalWritePath(activationNode(plan));

                    ProtosActivation invocation = fixture.invocationOf(plan.closure);
                    assertEquals(6L, unwrapNumber(plan.plan.executeBytecodeActivationForTesting(invocation)));

                    assertEquals(5L, unwrapNumber(slot(fixture.module, "x")));
                    assertEquals(6L, unwrapNumber(invocation.context().readLocalSlot("x").orElseThrow()));
                });
        System.out.println("POST_RHS_DESTINATION_RE_RESOLUTION=NO");
    }

    @Test
    void rhsRemovalOfSelectedCurrentBindingIsMutationErrorNotRetarget() throws Exception {
        withFixture(
                fixture -> {
                    ProtosBytecodeRootNode root =
                            lower(
                                    fixture.language,
                                    "x: 1\n"
                                            + "ctx: context\n"
                                            + "drop: () => { ctx.removeSlot(\"x\")\n"
                                            + "2 }\n"
                                            + "x = drop()");
                    assertModuleWritePath(root.getBytecodeNode());

                    ProtosSignalException signal =
                            assertThrows(
                                    ProtosSignalException.class,
                                    () -> root.getCallTarget().call(fixture.module));
                    assertSame(fixture.errorPrototype, parentOf(signal));
                    assertFalse(fixture.module.context().hasLocalSlot("x"));
                });
        System.out.println("RHS_REMOVE_SELECTED_CURRENT=EXPECTED_MUTATION_ERROR");
    }

    @Test
    void rhsRemoveThenRecreateInSameContextReceivesTheWrite() throws Exception {
        withFixture(
                fixture -> {
                    ProtosBytecodeRootNode root =
                            lower(
                                    fixture.language,
                                    "x: 1\n"
                                            + "ctx: context\n"
                                            + "swap: () => { ctx.removeSlot(\"x\")\n"
                                            + "ctx.x: 2\n"
                                            + "3 }\n"
                                            + "x = swap()\n"
                                            + "x");
                    assertModuleWritePath(root.getBytecodeNode());

                    assertEquals(3L, unwrapNumber(root.getCallTarget().call(fixture.module)));
                    assertEquals(3L, unwrapNumber(slot(fixture.module, "x")));
                });
        System.out.println("RHS_REMOVE_RECREATE_SAME_OWNER=PASS");
    }

    @Test
    void fallbackSelectedWhileCurrentAbsentIsNotStolenByRhsRecreation() throws Exception {
        withFixture(
                fixture -> {
                    ClosureUnderTest plan =
                            closurePlan(
                                    fixture,
                                    "x: 1\n"
                                            + "() => { x: 2\n"
                                            + "ctx: context\n"
                                            + "context.removeSlot(\"x\")\n"
                                            + "recreate: () => { ctx.x: 3\n"
                                            + "4 }\n"
                                            + "x = recreate()\n"
                                            + "x }");
                    assertLocalWritePath(activationNode(plan));

                    ProtosActivation invocation = fixture.invocationOf(plan.closure);
                    Object result = plan.plan.executeBytecodeActivationForTesting(invocation);

                    // The outer binding selected before the RHS receives the
                    // write; the recreated current binding keeps its own value.
                    assertEquals(3L, unwrapNumber(result));
                    assertEquals(4L, unwrapNumber(slot(fixture.module, "x")));
                    assertEquals(3L, unwrapNumber(invocation.context().readLocalSlot("x").orElseThrow()));
                });
        System.out.println("ABSENT_THEN_RHS_RECREATE_NO_RETARGET=PASS");
    }

    @Test
    void closedCurrentContextRemainsWritable() throws Exception {
        withFixture(
                fixture -> {
                    ProtosBytecodeRootNode root =
                            lower(
                                    fixture.language,
                                    "x: 1\n"
                                            + "ctx: context\n"
                                            + "closer: () => { ctx.close()\n"
                                            + "2 }\n"
                                            + "x = closer()\n"
                                            + "x");
                    assertModuleWritePath(root.getBytecodeNode());

                    assertEquals(2L, unwrapNumber(root.getCallTarget().call(fixture.module)));
                    assertEquals(2L, unwrapNumber(slot(fixture.module, "x")));
                });
        System.out.println("CLOSED_WRITE_ALLOWED=PASS");
    }

    @Test
    void frozenCurrentContextRejectsWriteAndPreservesValue() throws Exception {
        withFixture(
                fixture -> {
                    ProtosBytecodeRootNode root =
                            lower(
                                    fixture.language,
                                    "x: 1\n"
                                            + "ctx: context\n"
                                            + "freezer: () => { ctx.freeze()\n"
                                            + "2 }\n"
                                            + "x = freezer()");
                    assertModuleWritePath(root.getBytecodeNode());

                    ProtosSignalException signal =
                            assertThrows(
                                    ProtosSignalException.class,
                                    () -> root.getCallTarget().call(fixture.module));
                    assertSame(fixture.errorPrototype, parentOf(signal));
                    assertEquals(1L, unwrapNumber(slot(fixture.module, "x")));
                });
        System.out.println("FROZEN_WRITE_REJECTED=PASS");
    }

    @Test
    void presentNullRemainsDistinctFromAbsent() throws Exception {
        withFixture(
                fixture -> {
                    ProtosBytecodeRootNode root =
                            lower(fixture.language, "x: 1\n" + "x = null\n" + "x = null");
                    assertModuleWritePath(root.getBytecodeNode());

                    // The second write succeeds only if the first left x PRESENT.
                    assertSame(ProtosNullValue.INSTANCE, root.getCallTarget().call(fixture.module));
                    assertTrue(fixture.module.context().hasLocalSlot("x"));
                    assertSame(ProtosNullValue.INSTANCE, slot(fixture.module, "x"));
                });
        System.out.println("PRESENT_NULL_DISTINCT_FROM_ABSENT=PASS");
    }

    @Test
    void rhsControlTransferAttemptsNoWrite() throws Exception {
        withFixture(
                fixture -> {
                    ProtosBytecodeRootNode root =
                            lower(
                                    fixture.language,
                                    "x: 1\n" + "boom: () => missingName\n" + "x = boom()");
                    assertModuleWritePath(root.getBytecodeNode());

                    assertThrows(
                            ProtosSignalException.class,
                            () -> root.getCallTarget().call(fixture.module));
                    assertEquals(1L, unwrapNumber(slot(fixture.module, "x")));
                });
        System.out.println("RHS_CONTROL_TRANSFER_NO_WRITE=PASS");
    }

    @Test
    void defaultExpressionAssignmentUsesTheSameCurrentAccessorPath() throws Exception {
        withFixture(
                fixture -> {
                    ClosureUnderTest plan =
                            closurePlan(fixture, "r: 0\n" + "f: (x, y: (x = 99)) => x\n" + "r = f(1)\n" + "f");
                    assertLocalWritePath(activationNode(plan));

                    assertEquals(99L, unwrapNumber(slot(fixture.module, "r")));
                });
        System.out.println("DEFAULT_ASSIGNMENT_PATH=PASS");
    }

    @Test
    void selectionRetainedAcrossSuspensionAndUncachedToCachedTransition() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue initial = newObject();
                    ProtosObjectValue recreated = newObject();
                    ProtosObjectValue rhs = newObject();
                    ProtosBytecodeRootNode root =
                            createSelectYieldAssignRoot(fixture.language, initial);
                    ProtosObjectValue executionContext = fixture.module.context();

                    root.getBytecodeNode().setUncachedThreshold(1);
                    ContinuationResult suspended =
                            assertInstanceOf(
                                    ContinuationResult.class,
                                    root.getCallTarget().call(fixture.module));
                    assertSame(initial, executionContext.readLocalSlot("x").orElseThrow());

                    // Remove/recreate in the same context while suspended.
                    executionContext.removeLocalSlot("x");
                    executionContext.createLocalSlot("x", recreated);

                    assertSame(rhs, suspended.continueWith(rhs));
                    assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
                    assertSame(rhs, executionContext.readLocalSlot("x").orElseThrow());
                });
        System.out.println("RHS_YIELD_RESUME_SELECTION_PRESERVED=PASS");
        System.out.println("CACHED_TRANSITION_ACCESSOR_COHERENT=PASS");
    }

    @Test
    void removalDuringSuspensionIsMutationErrorAfterResume() throws Exception {
        withFixture(
                fixture -> {
                    ProtosObjectValue initial = newObject();
                    ProtosBytecodeRootNode root =
                            createSelectYieldAssignRoot(fixture.language, initial);
                    ProtosObjectValue executionContext = fixture.module.context();

                    ContinuationResult suspended =
                            assertInstanceOf(
                                    ContinuationResult.class,
                                    root.getCallTarget().call(fixture.module));
                    executionContext.removeLocalSlot("x");

                    ProtosSignalException signal =
                            assertThrows(
                                    ProtosSignalException.class,
                                    () -> suspended.continueWith(newObject()));
                    assertSame(fixture.errorPrototype, parentOf(signal));
                    assertFalse(executionContext.hasLocalSlot("x"));
                });
        System.out.println("SUSPENDED_REMOVE_SELECTED_CURRENT=EXPECTED_MUTATION_ERROR");
    }

    @Test
    void ineligibleAssignmentsKeepTheirExistingPaths() throws Exception {
        withFixture(
                fixture -> {
                    // Candidate: the write precedes the current declaration.
                    ClosureUnderTest candidate =
                            closurePlan(fixture, "x: 0\n" + "() => { x = 5\n" + "x: 1\n" + "x }");
                    assertGenericLexicalWritePath(activationNode(candidate));
                    ProtosActivation invocation = fixture.invocationOf(candidate.closure);
                    assertEquals(
                            1L,
                            unwrapNumber(candidate.plan.executeBytecodeActivationForTesting(invocation)));
                    assertEquals(5L, unwrapNumber(slot(fixture.module, "x")));

                    // Dynamic: no static declaration anywhere.
                    assertGenericLexicalWritePath(
                            lower(fixture.language, "undeclared = 1").getBytecodeNode());

                    // Explicit member assignment.
                    ProtosBytecodeRootNode member =
                            lower(fixture.language, "o: { x: 1 }\n" + "o.x = 2\n" + "o.x");
                    List<String> memberNames = instructionNames(member.getBytecodeNode());
                    assertTrue(
                            memberNames.stream().anyMatch(name -> name.contains("AssignLocalSlot")),
                            () -> "explicit member assignment lost its path: " + memberNames);
                    assertTrue(
                            memberNames.stream()
                                    .noneMatch(name -> name.contains(SELECT_GLOBAL)),
                            () -> "explicit member assignment used the module write path: " + memberNames);
                    assertNoRetired(memberNames);
                    assertEquals(2L, unwrapNumber(member.getCallTarget().call(fixture.module)));

                    // Captured module binding: the captured-depth module write
                    // (SelectWriteGlobal before WriteGlobal), never a local store.
                    ClosureUnderTest captured =
                            closurePlan(fixture, "y: 0\n" + "() => { y = 3 }");
                    List<String> capturedNames = instructionNames(activationNode(captured));
                    assertModuleWritePath(activationNode(captured));
                    assertTrue(
                            capturedNames.stream().noneMatch(name -> name.contains(STORE_LOCAL)),
                            () -> "captured module write used a local store: " + capturedNames);
                    ProtosActivation capturedInvocation = fixture.invocationOf(captured.closure);
                    assertEquals(3L, unwrapNumber(
                            captured.plan.executeBytecodeActivationForTesting(capturedInvocation)));
                    assertEquals(3L, unwrapNumber(slot(fixture.module, "y")));
                });
        System.out.println("CANDIDATE_FALLBACK_PRESERVED=YES");
        System.out.println("DYNAMIC_FALLBACK_PRESERVED=YES");
        System.out.println("EXPLICIT_MEMBER_ASSIGNMENT_UNCHANGED=YES");
        System.out.println("CAPTURED_WRITE_PATH_PRESERVED=YES");
    }

    /**
     * Mirrors the lowered module shape: the module NAME binding {@code x} is
     * created, its destination selected by {@code SelectWriteGlobal} (module
     * depth 0) and retained in a native local, then the RHS suspends; the
     * resume value is written by {@code WriteGlobal} through that destination.
     */
    private static ProtosBytecodeRootNode createSelectYieldAssignRoot(
            ProtosLanguage language, ProtosObjectValue initial) {
        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();

                            BytecodeLocal target = builder.createLocal("target", null);

                            // The module NAME binding x.
                            builder.beginCreateCurrentLocalSlot();
                            builder.emitLoadArgument(0);
                            builder.emitLoadConstant("x");
                            builder.emitLoadConstant(initial);
                            builder.endCreateCurrentLocalSlot();

                            // Destination selected before the RHS and retained.
                            builder.beginStoreLocal(target);
                            builder.emitSelectWriteGlobal(
                                    TruffleString.fromJavaStringUncached(
                                            "x", TruffleString.Encoding.UTF_16),
                                    0);
                            builder.endStoreLocal();

                            builder.beginReturn();
                            builder.beginWriteGlobal();
                            builder.emitLoadLocal(target);
                            builder.beginYield();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endYield();
                            builder.endWriteGlobal();
                            builder.endReturn();

                            builder.endRoot();
                        });
        return roots.getNode(0);
    }

    /* Retired PERF028-A/PERF025 accessor operations; none may reappear. */
    private static final List<String> RETIRED =
            List.of(
                    "ResolveCurrentFrameLocalWriteTarget",
                    "AssignCurrentFrameLocal",
                    "ResolveRootFrameLocalWriteTarget",
                    "AssignRootFrameLocal",
                    "InstallFrameLexicalAuthority");

    /**
     * A write statically resolved to the running module's own NAME binding:
     * SelectWriteGlobal selects the destination, WriteGlobal later writes
     * through it, with no String-keyed generic resolution.
     */
    private static void assertModuleWritePath(BytecodeNode node) {
        List<String> names = instructionNames(node);
        int select = indexOf(names, SELECT_GLOBAL);
        int write = indexOf(names, WRITE_GLOBAL);
        assertTrue(select >= 0, () -> "module write did not select with SelectWriteGlobal: " + names);
        assertTrue(write > select, () -> "module write did not write after selecting: " + names);
        assertTrue(
                names.stream()
                        .noneMatch(name -> name.contains(RESOLVE_GENERIC) || name.contains(ASSIGN_GENERIC)),
                () -> "Resolved module write still emitted the generic String-keyed path: " + names);
        assertNoRetired(names);
    }

    /**
     * A write statically resolved to the running Closure's own LOCAL
     * binding: a native store into that binding's local, with no module or
     * String-keyed write.
     */
    private static void assertLocalWritePath(BytecodeNode node) {
        List<String> names = instructionNames(node);
        assertTrue(names.stream().anyMatch(name -> name.contains(STORE_LOCAL)),
                () -> "Resolved local write is not a native store: " + names);
        assertTrue(names.stream().anyMatch(name -> name.contains(LOAD_LOCAL)),
                () -> "Resolved local binding is not read natively: " + names);
        assertTrue(
                names.stream()
                        .noneMatch(name -> name.contains(RESOLVE_GENERIC) || name.contains(ASSIGN_GENERIC)),
                () -> "Resolved local write still emitted the generic String-keyed path: " + names);
        assertNoRetired(names);
    }

    private static void assertGenericLexicalWritePath(BytecodeNode node) {
        List<String> names = instructionNames(node);
        int resolve = indexOf(names, RESOLVE_GENERIC);
        assertTrue(resolve >= 0, () -> "ineligible write lost the generic resolve path: " + names);
        assertTrue(indexOf(names, ASSIGN_GENERIC) > resolve,
                () -> "ineligible write lost the generic assign path after resolution: " + names);
        assertTrue(names.stream().noneMatch(name -> name.contains(SELECT_GLOBAL)),
                () -> "ineligible write used the static module path: " + names);
        assertNoRetired(names);
    }

    private static void assertNoRetired(List<String> names) {
        for (String retired : RETIRED) {
            assertTrue(names.stream().noneMatch(name -> name.contains(retired)),
                    () -> "retired " + retired + " in " + names);
        }
    }

    private static int indexOf(List<String> names, String operation) {
        for (int index = 0; index < names.size(); index++) {
            if (names.get(index).contains(operation)) {
                return index;
            }
        }
        return -1;
    }

    private static List<String> instructionNames(BytecodeNode node) {
        return node.getInstructionsAsList().stream().map(ProtosInstructionNames::of).toList();
    }

    private static BytecodeNode activationNode(ClosureUnderTest closure) {
        return closure.plan.bytecodeActivationRootForTesting().getBytecodeNode();
    }

    private record ClosureUnderTest(ProtosClosureValue closure, ProtosClosureExecutionPlan plan) {}

    private record Fixture(
            ProtosLanguage language,
            ProtosActivation module,
            ProtosObjectValue errorPrototype,
            ProtosObjectValue slotNotFoundPrototype) {
        ProtosActivation invocationOf(ProtosClosureValue closure) {
            return ProtosActivation.forClosureInvocation(
                    closure,
                    List.of(),
                    module.prelude().orElseThrow(),
                    module.actorModuleState(),
                    module.currentModuleKey().orElse(null),
                    module.executionDomain());
        }
    }

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
                ProtosObjectValue slotNotFoundPrototype = new ProtosObjectValue(errorPrototype);
                bindings.createLocalSlot("Context", contextPrototype);
                bindings.createLocalSlot("Error", errorPrototype);
                bindings.createLocalSlot("SlotNotFound", slotNotFoundPrototype);
                bindings.createLocalSlot("Array", newObject());
                bindings.freeze();

                test.run(
                        new Fixture(
                                LANGUAGE_REF.get(null),
                                new ProtosPrelude(bindings, contextPrototype).newModuleActivation(),
                                errorPrototype,
                                slotNotFoundPrototype));
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
        return lower(fixture.language, characters).getCallTarget().call(fixture.module);
    }

    private static ProtosBytecodeRootNode lower(ProtosLanguage language, String characters) {
        Source source = Source.newBuilder(ProtosLanguage.ID, characters, "perf028-a.protos").build();
        return new CanonicalToBytecodeLowerer(language, source).lowerRoot(canonicalize(characters));
    }

    private static CanonicalSequence canonicalize(String characters) {
        return (CanonicalSequence)
                new Canonicalizer().canonicalize(new ProtosParser(characters).parseProgram());
    }

    private static Object slot(ProtosActivation module, String name) {
        return module.context().readLocalSlot(name).orElseThrow();
    }

    private static Object parentOf(ProtosSignalException signal) {
        return signal.error().parent().orElseThrow();
    }

    private static ProtosObjectValue newObject() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    private static long unwrapNumber(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Long integerValue) {
            return integerValue.longValue();
        }
        throw new AssertionError("expected a Protos integer result, got: " + value);
    }
}

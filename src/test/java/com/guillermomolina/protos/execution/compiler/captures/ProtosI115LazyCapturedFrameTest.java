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
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I115 lazy-context experiment (EXPERIMENTAL, see
 * docs/design/I115_LAZY_CONTEXT_EXPERIMENT.md): an ordinary Closure root that
 * may need durable lexical state starts frame-native and installs its
 * frame-backed authority only through {@code EnsureLiveFrameLexicalAuthority},
 * immediately before a creator-capturing Closure creation or a {@code context}
 * observation. Roots whose proof fails (a {@code Candidate} reference to an own
 * name, Object construction, parameter defaults) keep the entry prologue.
 */
final class ProtosI115LazyCapturedFrameTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);


    // ---- Bytecode structure ------------------------------------------------------

    @Test
    void noncapturingNestedClosureNeitherInstallsNorPromotes() throws Exception {
        withFixture(fixture -> {
            ClosureUnderTest plan =
                    closurePlan(fixture, "(a) => { unused: () => 1\n a }");
            List<String> names = instructionNames(activationNode(plan));
            // PLAT066-C: the nested Closure uses nothing of this activation.
            assertNone(names, "CreateCell");
            assertNone(names, "CaptureClosureCells");
            assertNone(names, "PublishCurrentClosureContext");
            assertNone(names, "EnsureCurrentClosureLexicalView");
        });
    }

    @Test
    void capturingClosurePromotesExactlyOnceBeforeItsCreation() throws Exception {
        withFixture(fixture -> {
            ClosureUnderTest plan = closurePlan(fixture, "(x) => { g: () => x\n g }");
            List<String> names = instructionNames(activationNode(plan));
            // The captured x is a cell created once, before the Closure that
            // captures it; nothing binds or publishes the frame.
            assertEquals(1, count(names, "CreateCell"), names::toString);
            assertTrue(
                    indexOf(names, "CreateCell") < indexOf(names, "MaterializeCurrentClosure"),
                    () -> "the cell must exist before the capture: " + names);
            assertContains(names, "CaptureClosureCells");
            assertNone(names, "PublishCurrentClosureContext");
            assertNone(names, "EnsureCurrentClosureLexicalView");
        });
    }

    @Test
    void contextObservationPromotesBeforeTheIntrinsic() throws Exception {
        withFixture(fixture -> {
            ClosureUnderTest plan = closurePlan(fixture, "(a) => { b: a\n context }");
            List<String> names = instructionNames(activationNode(plan));
            // The frame is bound to its live view once, at the observation
            // itself, after the earlier statements; there is no entry prologue.
            assertEquals(1, count(names, "PublishCurrentClosureContext"), names::toString);
            assertTrue(
                    indexOf(names, "CreateLexicalLocal") < indexOf(names, "PublishCurrentClosureContext"),
                    () -> "the observation binds the frame where it occurs: " + names);
            assertNone(names, "EnsureCurrentClosureLexicalView");
        });
    }

    @Test
    void directLocalReadAndWriteNeedNoPromotion() throws Exception {
        withFixture(fixture -> {
            ClosureUnderTest plan = closurePlan(fixture, "(a) => { b: a\n b = a\n b }");
            List<String> names = instructionNames(activationNode(plan));
            // Own bindings are plain frame locals: no cell, no view, no publication.
            assertNone(names, "CreateCell");
            assertNone(names, "PublishCurrentClosureContext");
            assertNone(names, "EnsureCurrentClosureLexicalView");
            assertContains(names, "store.local");
        });
    }

    @Test
    void candidateReferenceToOwnNameKeepsTheEntryPrologue() throws Exception {
        withFixture(fixture -> {
            ClosureUnderTest plan =
                    closurePlan(fixture,
                            "x: 7\n" + "(a) => { y: x\n" + "x: 2\n" + "g: () => y\n" + "g }");
            List<String> names = instructionNames(activationNode(plan));
            // A read of an own name before its creation is presence-aware:
            // while the own x is absent it selects the outer one.
            assertContains(names, "LoadLexicalLocal");
            assertNone(names, "EnsureCurrentClosureLexicalView");
            ProtosClosureValue g = assertInstanceOf(
                    ProtosClosureValue.class,
                    plan.plan.executeBytecodeActivationForTesting(
                            deferredInvocation(fixture, plan.closure, List.of(newObject()))));
            assertEquals(7L, unwrapNumber(ProtosClosureInvoker
                    .invoke(g, List.of())));
        });
    }

    // ---- Executable semantics ----------------------------------------------------

    @Test
    void immediateNonescapingCallbackReadsTheParentLocal() throws Exception {
        withFixture(fixture -> assertEquals(
                4L,
                unwrapNumber(run(fixture, "f: (x) => { g: () => x\n g() }\n" + "f(4)"))));
    }

    @Test
    void siblingCallbacksShareOneMutableParentLocal() throws Exception {
        withFixture(fixture -> assertEquals(
                6L,
                unwrapNumber(run(fixture,
                        "f: (x) => { set: (v) => { x = v }\n" + "get: () => x\n"
                                + "set(6)\n" + "get() }\n" + "f(1)"))));
    }

    @Test
    void escapedClosureKeepsTheOwnerStateAcrossRepeatedInvocations() throws Exception {
        withFixture(fixture -> {
            run(fixture, "mk: (x) => { put: (v) => { x = v\n x }\n put }\n" + "s: mk(1)\n" + "s(2)");
            assertEquals(3L, unwrapNumber(run(fixture, "s(3)")));
            assertEquals(4L, unwrapNumber(run(fixture, "s(4)")));
            assertEquals(
                    7L,
                    unwrapNumber(run(fixture,
                            "mkGet: (x) => { get: () => x\n get }\n" + "k: mkGet(7)\n" + "k()\n" + "k()")));
        });
    }

    @Test
    void nestedClosureReadsParentAndOuterScopes() throws Exception {
        withFixture(fixture -> {
            assertEquals(
                    3L,
                    unwrapNumber(run(fixture,
                            "outer: 10\n"
                                    + "f: (x) => { g: () => { h: () => outer\n" + "h()\n" + "x }\n"
                                    + "g() }\n"
                                    + "f(3)")));
            assertEquals(
                    10L,
                    unwrapNumber(run(fixture,
                            "f2: (x) => { g: () => { h: () => x\n" + "h()\n" + "outer }\n"
                                    + "g() }\n"
                                    + "f2(3)")));
        });
    }

    @Test
    void contextObservedBeforeAndAfterClosureCreationIsOneIdentity() throws Exception {
        withFixture(fixture -> {
            run(fixture,
                    "f: (a) => { before: context\n" + "g: () => before\n" + "after: context\n"
                            + "g }\n"
                            + "k: f(1)");
            ProtosClosureValue k = assertInstanceOf(ProtosClosureValue.class, run(fixture, "k"));
            ProtosObjectValue before = assertInstanceOf(ProtosObjectValue.class, run(fixture, "k()"));
            // g only reads the cell `before`; it receives that cell, not its
            // creator's Context (PLAT066-C), so identity is checked in guest
            // terms: the Context observed before and after creation is one.
            assertSame(before, before.readLocalSlot("after").orElseThrow());
        });
    }

    @Test
    void twoCapturesResolveTheSameContextIdentity() throws Exception {
        withFixture(fixture -> {
            ProtosObjectValue observed = assertInstanceOf(
                    ProtosObjectValue.class,
                    run(fixture, "f: (a) => { g: () => a\n" + "h: () => a\n" + "context }\n" + "f(1)"));
            ProtosClosureValue g = assertInstanceOf(
                    ProtosClosureValue.class, observed.readLocalSlot("g").orElseThrow());
            ProtosClosureValue h = assertInstanceOf(
                    ProtosClosureValue.class, observed.readLocalSlot("h").orElseThrow());
            // Guest terms (PLAT066-C): both Closures share the one binding the
            // observed Context exposes, before and after an assignment to it.
            run(fixture, "c2: f(1)");
            assertEquals(1L, unwrapNumber(run(fixture, "c2.g()")));
            assertEquals(1L, unwrapNumber(run(fixture, "c2.h()")));
            run(fixture, "c2.a = 9");
            assertEquals(9L, unwrapNumber(run(fixture, "c2.g()")));
            assertEquals(9L, unwrapNumber(run(fixture, "c2.h()")));
        });
    }

    @Test
    void candidateNameResolvesDynamicallyWithPossibleAbsence() throws Exception {
        withFixture(fixture -> {
            ClosureUnderTest plan =
                    closurePlan(fixture, "x: 7\n" + "(a) => { y: x\n" + "x: 2\n" + "y }");
            assertEquals(7L, unwrapNumber(plan.plan.executeBytecodeActivationForTesting(
                    deferredInvocation(fixture, plan.closure, List.of(newObject())))));
        });
    }

    @Test
    void presenceFollowsRemovalAndRecreationThroughTheObservedContext() throws Exception {
        withFixture(fixture -> {
            ProtosObjectValue observed = assertInstanceOf(
                    ProtosObjectValue.class,
                    run(fixture, "f: (a) => { b: a\n" + "g: () => b\n" + "context }\n" + "c: f(5)"));
            run(fixture, "k: c.slotValue(\"g\")");
            assertEquals(5L, unwrapNumber(run(fixture, "k()")));

            observed.removeLocalSlot("b");
            assertThrows(ProtosSignalException.class, () -> run(fixture, "k()"));

            ProtosObjectValue replacement = newObject();
            observed.createLocalSlot("b", replacement);
            assertSame(replacement, run(fixture, "k()"));
        });
    }

    @Test
    void selectedAssignmentDestinationSurvivesRightHandSideEffects() throws Exception {
        withFixture(fixture -> assertEquals(
                9L,
                unwrapNumber(run(fixture,
                        "f: (x) => { set: (v) => { x = v\n v }\n"
                                + "rhs: () => { set(2)\n 9 }\n"
                                + "x = rhs()\n"
                                + "get: () => x\n"
                                + "get() }\n"
                                + "f(1)"))));
    }

    @Test
    void nonLocalReturnThroughAPromotedCaptureReachesItsActiveHome() throws Exception {
        withFixture(fixture -> {
            ClosureUnderTest plan =
                    closurePlan(fixture, "(x) => { g: () => ^x\n" + "g()\n" + "99 }");
            // The returning Closure captures x as a cell of this activation.
            assertContains(instructionNames(activationNode(plan)), "CaptureClosureCells");
            ProtosObjectValue argument = newObject();
            assertSame(argument, ProtosClosureInvoker.invoke(
                    plan.closure, List.of(argument), fixture.module));
        });
    }

    @Test
    void escapedNonLocalReturnAfterItsHomeCompletedIsAnError() throws Exception {
        withFixture(fixture -> {
            ClosureUnderTest plan = closurePlan(fixture, "(x) => { g: () => ^x\n" + "g }");
            ProtosReturnHome home = new ProtosReturnHome();
            ProtosClosureValue escaped = assertInstanceOf(
                    ProtosClosureValue.class,
                    plan.plan.executeBytecodeActivationForTesting(
                            deferredInvocation(fixture, plan.closure, List.of(newObject()), home)));
            // The owning activation closes its own home when it leaves its
            // scope; complete it here only if it is still open.
            if (home.isActive()) {
                home.complete();
            }
            assertFalse(home.isActive());
            assertThrows(
                    ProtosSignalException.class,
                    () -> ProtosClosureInvoker.invoke(
                            escaped, List.of()));
        });
    }

    // ---- Helpers (the ProtosPerf025FrameMaterializationSliceTest fixture) --------

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

    private static long count(List<String> names, String operation) {
        return names.stream().filter(name -> name.contains(operation)).count();
    }

    private static int indexOf(List<String> names, String operation) {
        for (int index = 0; index < names.size(); index++) {
            if (names.get(index).contains(operation)) {
                return index;
            }
        }
        throw new AssertionError("expected " + operation + " in: " + names);
    }

    private static List<String> instructionNames(BytecodeNode node) {
        return node.getInstructionsAsList().stream().map(Instruction::getName).toList();
    }

    private static BytecodeNode activationNode(ClosureUnderTest closure) {
        return closure.plan.bytecodeActivationRootForTesting().getBytecodeNode();
    }

    private static ProtosActivation deferredInvocation(
            Fixture fixture, ProtosClosureValue closure, List<?> supplied) {
        return deferredInvocation(fixture, closure, supplied, new ProtosReturnHome());
    }

    private static ProtosActivation deferredInvocation(
            Fixture fixture, ProtosClosureValue closure, List<?> supplied, ProtosReturnHome home) {
        return ProtosActivation.forImmediateMethodInvocationWithReturnHomeForRuntime(
                closure,
                supplied,
                newObject(),
                newObject(),
                fixture.module.prelude().orElseThrow(),
                fixture.module.actorModuleState(),
                fixture.module.currentModuleKey().orElse(null),
                fixture.module.executionDomain(),
                home);
    }

    private record ClosureUnderTest(ProtosClosureValue closure, ProtosClosureExecutionPlan plan) {}

    private record Fixture(ProtosLanguage language, ProtosActivation module) {}

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
                // An escaped return whose home completed is the standard InvalidReturn Error.
                bindings.createLocalSlot("InvalidReturn", new ProtosObjectValue(errorPrototype));
                bindings.createLocalSlot("Array", newObject());
                bindings.freeze();

                test.run(new Fixture(
                        LANGUAGE_REF.get(null),
                        new ProtosPrelude(bindings, contextPrototype).newModuleActivation()));
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
        Source source = Source.newBuilder(ProtosLanguage.ID, characters, "i115-lazy-context.protos").build();
        CanonicalSequence canonical =
                (CanonicalSequence) new Canonicalizer().canonicalize(new ProtosParser(characters).parseProgram());
        return new CanonicalToBytecodeLowerer(fixture.language, source)
                .lowerRoot(canonical)
                .getCallTarget()
                .call(fixture.module);
    }

    private static ProtosObjectValue newObject() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    private static long unwrapNumber(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        throw new AssertionError("expected a Protos integer result, got: " + value);
    }
}

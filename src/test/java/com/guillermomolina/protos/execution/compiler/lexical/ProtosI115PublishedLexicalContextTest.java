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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosExecutionContextValue;
import com.guillermomolina.protos.runtime.ProtosLexicalBindingAuthority;
import com.guillermomolina.protos.runtime.ProtosLexicalCell;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.source.Source;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I115-S1c-L regression evidence: the observed Context of a Closure
 * activation is the live frame-backed view of its original frame, with one
 * physical value and one membership state per binding.
 */
final class ProtosI115PublishedLexicalContextTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void establishedLocalIsVisibleWithExactValueAndActivationIdentity() throws Exception {
        withFixture(f -> {
            Object value = newObject();
            ProtosClosureValue closure = closure(f, "((value) => { x: value\n context })");
            ProtosActivation activation = invocation(f, closure, value);
            Object result = closure.executionPlan().orElseThrow().executeBytecodeActivationForTesting(activation);
            assertSame(activation.context(), result);
            assertTrue(activation.context().hasLocalSlot("x"));
            assertSame(value, local(activation.context(), "x"));
            assertTrue(operations(activationNode(closure)).contains("PublishCurrentClosureContext"));
        });
    }

    @Test
    void unestablishedLocalIsAbsent() throws Exception {
        withFixture(f -> {
            ProtosClosureValue closure = closure(f,
                    "(() => { absentBeforeCreation: context.hasSlot(\"x\")\n"
                            + " x: 7\n presentAfterCreation: context.hasSlot(\"x\")\n"
                            + " absentBeforeCreation })");
            ProtosActivation activation = invocation(f, closure);
            Object result = closure.executionPlan().orElseThrow()
                    .executeBytecodeActivationForTesting(activation);
            assertSame(ProtosBooleanValue.FALSE, result);
            assertSame(ProtosBooleanValue.TRUE, local(activation.context(), "presentAfterCreation"));
            assertEquals(7L, local(activation.context(), "x"));
        });
    }

    @Test
    void canonicalNullLocalIsPresent() throws Exception {
        withFixture(f -> {
            ProtosObjectValue context = observed(f, "(() => { x: null\n context })");
            assertTrue(context.hasLocalSlot("x"));
            assertSame(ProtosNullValue.INSTANCE, local(context, "x"));
        });
    }

    @Test
    void primitiveIntegerAndFloatReflectAsGuestValues() throws Exception {
        withFixture(f -> {
            ProtosObjectValue context = observed(f, "(() => { i: 17\n d: 2.5\n context })");
            assertEquals(17L, assertInstanceOf(Long.class, local(context, "i")));
            assertEquals(2.5, assertInstanceOf(Double.class, local(context, "d")));
        });
    }

    @Test
    void repeatedObservationKeepsIdentity() throws Exception {
        withFixture(f -> {
            ProtosObjectValue context = observed(f, "(() => { first: context\n context })");
            assertSame(context, local(context, "first"));
        });
    }

    @Test
    void lexicalAssignmentAfterPublicationChangesObservedValue() throws Exception {
        withFixture(f -> {
            ProtosClosureValue closure = closure(f, "(() => { x: 1\n saved: context\n x = 7\n saved })");
            ProtosObjectValue context = assertInstanceOf(ProtosObjectValue.class, execute(f, closure));
            assertEquals(7L, local(context, "x"));
            assertTrue(operations(activationNode(closure)).contains("AssignLexicalLocal"));
            assertFalse(operations(activationNode(closure)).contains("StoreCell"));
        });
    }

    @Test
    void contextAssignmentChangesPhysicalLocalRead() throws Exception {
        withFixture(f -> {
            ProtosClosureValue closure =
                    closure(f, "((replacement) => { x: 1\n saved: context\n saved.x = replacement\n x })");
            Object replacement = newObject();
            assertSame(replacement, execute(f, closure, replacement));
            assertTrue(operations(activationNode(closure)).contains("LoadLexicalLocal"));
        });
    }

    @Test
    void ownedCellReflectsItsActualGuestValue() throws Exception {
        withFixture(f -> {
            ProtosObjectValue context = observed(f, "(() => { x: 3\n reader: (() => x)\n context })");
            ProtosClosureValue reader = callable(context, "reader");
            assertEquals(3L, local(context, "x"));
            assertSame(reader.capturedCellsForRuntime()[0].valueOrNullForRuntime(), local(context, "x"));
        });
    }

    @Test
    void contextAndCapturedClosureMutateEachOtherThroughOneCell() throws Exception {
        withFixture(f -> {
            ProtosObjectValue context = observed(f,
                    "(() => { x: 1\n setter: ((v) => x = v)\n getter: (() => x)\n context })");
            Object fromClosure = newObject();
            execute(f, callable(context, "setter"), fromClosure);
            assertSame(fromClosure, local(context, "x"));
            Object fromContext = newObject();
            context.assignLocalSlot("x", fromContext);
            assertSame(fromContext, execute(f, callable(context, "getter")));
        });
    }

    @Test
    void contextAssignmentUpdatesTwoSiblingCapturesWithoutReplacingCell() throws Exception {
        withFixture(f -> {
            ProtosObjectValue context = observed(f,
                    "(() => { x: 1\n first: (() => x)\n second: (() => x)\n context })");
            ProtosClosureValue first = callable(context, "first");
            ProtosClosureValue second = callable(context, "second");
            ProtosLexicalCell cell = first.capturedCellsForRuntime()[0];
            assertSame(cell, second.capturedCellsForRuntime()[0]);
            Object replacement = newObject();
            context.assignLocalSlot("x", replacement);
            assertSame(replacement, execute(f, first));
            assertSame(replacement, execute(f, second));
            assertSame(cell, first.capturedCellsForRuntime()[0]);
        });
    }

    @Test
    void closureAndObservedContextShareOneLexicalOwner() throws Exception {
        withFixture(f -> {
            ProtosObjectValue context = observed(f,
                    "(() => { x: {}\n reader: (() => late)\n context })");
            ProtosClosureValue reader = callable(context, "reader");
            assertSame(context, reader.capturedLexicalContexts().get(0));
            Object value = local(context, "x");
            context.createLocalSlot("late", value);
            assertSame(value, execute(f, reader));
        });
    }

    @Test
    void dynamicNameDoesNotOverwriteStaticLocalOrCell() throws Exception {
        withFixture(f -> {
            ProtosObjectValue context = observed(f,
                    "(() => { x: {}\n y: {}\n reader: (() => y)\n context })");
            Object x = local(context, "x");
            Object y = local(context, "y");
            Object extra = newObject();
            context.createLocalSlot("additional", extra);
            assertSame(x, local(context, "x"));
            assertSame(y, local(context, "y"));
            assertSame(y, execute(f, callable(context, "reader")));
            assertSame(extra, local(context, "additional"));
        });
    }

    @Test
    void moduleReadGlobalRetainsLiveModuleAuthority() throws Exception {
        withFixture(f -> {
            ProtosClosureValue reader = closure(f, "x: {}\n () => x");
            Object replacement = newObject();
            f.module.context().assignLocalSlot("x", replacement);
            assertSame(replacement, execute(f, reader));
            assertTrue(operations(activationNode(reader)).contains("ReadGlobal"));
            assertFalse(operations(activationNode(reader)).contains("EnsureCurrentClosureLexicalView"));
        });
    }

    @Test
    void publicationPreservesReturnHomeIdentityAndOwnerCompletion() throws Exception {
        withFixture(f -> {
            ProtosClosureValue maker = closure(f,
                    "(() => { x: 1\n saved: context\n escape: (() => { ^x })\n saved })");
            ProtosActivation activation = invocation(f, maker);
            var home = activation.returnHome().orElseThrow();
            assertTrue(home.isActive());
            ProtosObjectValue context = assertInstanceOf(ProtosObjectValue.class,
                    maker.executionPlan().orElseThrow().executeBytecodeActivationForTesting(activation));
            assertSame(home, activation.returnHome().orElseThrow());
            assertSame(home, callable(context, "escape").capturedReturnHomeOrNullForRuntime());
            assertFalse(home.isActive());
            assertEquals(1L, local(context, "x"));
        });
    }

    @Test
    void unobservedPlainLocalRootKeepsDirectInstructionsWithoutPublication() throws Exception {
        withFixture(f -> {
            ProtosClosureValue closure = closure(f, "(() => { x: 1\n x = 2\n x })");
            List<String> names = operations(activationNode(closure));
            assertTrue(names.stream().anyMatch(n -> n.startsWith("load.local")));
            assertTrue(names.stream().anyMatch(n -> n.startsWith("store.local")));
            assertFalse(names.contains("PublishCurrentClosureContext"));
            assertFalse(names.contains("EnsureCurrentClosureLexicalView"));
            assertFalse(names.contains("CurrentActivation"));
            assertEquals(2L, execute(f, closure));
        });
    }

    @Test
    void removalClearsOriginalLocalAndSharedCell() throws Exception {
        withFixture(f -> {
            ProtosObjectValue context = observed(f,
                    "(() => { x: {}\n y: {}\n reader: (() => y)\n context })");
            Object x = local(context, "x");
            Object y = local(context, "y");
            ProtosLexicalCell cell = callable(context, "reader").capturedCellsForRuntime()[0];
            assertSame(x, context.removeLocalSlot("x"));
            assertSame(y, context.removeLocalSlot("y"));
            assertFalse(context.hasLocalSlot("x"));
            assertFalse(context.hasLocalSlot("y"));
            assertSame(null, cell.valueOrNullForRuntime());
            context.createLocalSlot("y", x);
            assertSame(x, cell.valueOrNullForRuntime());
        });
    }

    @Test
    void snapshotsAndBoundedEnumerationObservePhysicalStorage() throws Exception {
        withFixture(f -> {
            ProtosExecutionContextValue context = assertInstanceOf(ProtosExecutionContextValue.class,
                    observed(f, "(() => { x: 1\n context })"));
            ProtosLexicalBindingAuthority authority = context.lexicalBindingAuthorityForRuntime();
            var snapshot = authority.bindingsSnapshot();
            var names = new java.util.ArrayList<String>();
            assertFalse(authority.appendBindingNamesToBounded(names, 0));
            assertTrue(names.isEmpty());
            assertTrue(authority.appendBindingNamesToBounded(names, 1));
            assertEquals(List.of("x"), names);
            context.assignLocalSlot("x", 2L);
            assertEquals(1L, snapshot.get("x"));
            assertThrows(UnsupportedOperationException.class, () -> snapshot.put("z", 3L));
            names.clear();
            var values = new java.util.ArrayList<Object>();
            authority.appendBindingsTo(names, values);
            assertEquals(List.of("x"), names);
            assertEquals(List.of(2L), values);
        });
    }

    @Test
    void authorityHandoffPreservesExistingContextAndDynamicBindings() throws Exception {
        withFixture(f -> {
            ProtosClosureValue closure = closure(f, "(() => { x: 1\n context })");
            ProtosActivation activation = invocation(f, closure);
            ProtosObjectValue before = activation.context();
            Object extra = newObject();
            before.createLocalSlot("additional", extra);
            Object result = closure.executionPlan().orElseThrow().executeBytecodeActivationForTesting(activation);
            assertSame(before, result);
            assertSame(extra, local(before, "additional"));
            assertEquals(1L, local(before, "x"));
            var authority = activation.publishedClosureLexicalAuthorityForRuntime();
            activation.installPublishedClosureLexicalAuthorityForRuntime(authority);
            assertSame(authority, activation.publishedClosureLexicalAuthorityForRuntime());
            assertThrows(IllegalStateException.class, () -> activation.installPublishedClosureLexicalAuthorityForRuntime(
                    ProtosLexicalBindingAuthority.newMapBackedForRuntime()));
        });
    }

    @Test
    void compactSourceInvocationPublishesItsOwnContextAndKeepsItAfterReturn() throws Exception {
        withFixture(f -> {
            ProtosObjectValue context = assertInstanceOf(ProtosObjectValue.class, run(f,
                    "make: (() => { x: {}\n original: context\n context })\n make()"));
            assertTrue(context.hasLocalSlot("x"));
            assertSame(context, local(context, "original"));
        });
    }

    @Test
    void closedAndFrozenContextRulesStillGuardPhysicalStorage() throws Exception {
        withFixture(f -> {
            ProtosObjectValue context = observed(f, "(() => { x: 1\n context })");
            context.close();
            context.assignLocalSlot("x", 2L);
            assertEquals(2L, local(context, "x"));
            assertThrows(IllegalStateException.class, () -> context.createLocalSlot("late", 3L));
            assertThrows(IllegalStateException.class, () -> context.removeLocalSlot("x"));
            context.freeze();
            assertThrows(IllegalStateException.class, () -> context.assignLocalSlot("x", 3L));
            assertEquals(2L, local(context, "x"));
        });
    }

    // ---- Guest-level D179 semantics -------------------------------------------

    @Test
    void removedBindingRevealsOuterLexicalBindingAndRecreationRestoresIt() throws Exception {
        withFixture(f -> assertEquals(213L, run(f,
                "make: () => {\n"
                        + " x: 1\n"
                        + " inner: () => {\n"
                        + "  x: 2\n"
                        + "  saved: context\n"
                        + "  read: () => x\n"
                        + "  before: read()\n"
                        + "  saved.removeSlot(\"x\")\n"
                        + "  after: read()\n"
                        + "  x: 3\n"
                        + "  again: read()\n"
                        + "  before * 100 + after * 10 + again\n"
                        + " }\n"
                        + " inner()\n"
                        + "}\n"
                        + "make()")));
    }

    @Test
    void removedBindingRevealsReceiverSlotAfterLexicalExhaustion() throws Exception {
        withFixture(f -> assertEquals(7L, run(f,
                "holder: {\n"
                        + " y: 7\n"
                        + " probe: () => {\n"
                        + "  y: 1\n"
                        + "  saved: context\n"
                        + "  saved.removeSlot(\"y\")\n"
                        + "  y\n"
                        + " }\n"
                        + "}\n"
                        + "holder.probe()")));
    }

    @Test
    void duplicateCreationIsRejected() throws Exception {
        withFixture(f -> {
            assertThrows(ProtosSignalException.class,
                    () -> execute(f, closure(f, "(() => { x: 1\n x: 2 })")));
            assertThrows(ProtosSignalException.class,
                    () -> execute(f, closure(f, "(() => { saved: context\n saved.x: 1\n x: 2 })")));
        });
    }

    @Test
    void presentGuestNullIsNotAbsence() throws Exception {
        withFixture(f -> {
            assertSame(ProtosNullValue.INSTANCE, run(f,
                    "make: () => { x: 5\n inner: () => { x: null\n saved: context\n x }\n inner() }\n"
                            + "make()"));
            assertEquals(5L, run(f,
                    "makeAfterRemoval: () => { x: 5\n inner: () => { x: null\n saved: context\n"
                            + " saved.removeSlot(\"x\")\n x }\n inner() }\n"
                            + "makeAfterRemoval()"));
        });
    }

    @Test
    void frozenAndClosedContextsRejectLexicalMutation() throws Exception {
        withFixture(f -> {
            assertThrows(ProtosSignalException.class, () -> execute(f, closure(f,
                    "(() => { x: 1\n saved: context\n saved.freeze()\n x = 2 })")));
            assertThrows(ProtosSignalException.class, () -> execute(f, closure(f,
                    "(() => { saved: context\n saved.close()\n y: 1 })")));
            assertEquals(2L, execute(f, closure(f,
                    "(() => { y: 1\n saved: context\n saved.close()\n y = 2\n y })")));
        });
    }

    @Test
    void separateInvocationsNeverShareLocalState() throws Exception {
        withFixture(f -> {
            ProtosClosureValue make = closure(f, "((v) => { x: v\n context })");
            ProtosObjectValue first = assertInstanceOf(ProtosObjectValue.class, execute(f, make, 1L));
            ProtosObjectValue second = assertInstanceOf(ProtosObjectValue.class, execute(f, make, 2L));
            assertNotSame(first, second);
            first.assignLocalSlot("x", 10L);
            assertEquals(10L, local(first, "x"));
            assertEquals(2L, local(second, "x"));
        });
    }

    @Test
    void retainedViewFollowsBytecodeTierTransitionAndReparse() throws Exception {
        withFixture(f -> {
            ProtosClosureValue make = closure(f, "((v) => { x: v\n context })");
            ProtosBytecodeRootNode root =
                    make.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
            root.getBytecodeNode().setUncachedThreshold(1);
            assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
            ProtosObjectValue early = assertInstanceOf(ProtosObjectValue.class, execute(f, make, 1L));
            execute(f, make, 2L);
            execute(f, make, 3L);
            assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
            assertEquals(1L, local(early, "x"));
            root.getRootNodes().ensureSourceInformation();
            early.assignLocalSlot("x", 4L);
            assertEquals(4L, local(early, "x"));
        });
    }

    /* Instruction names without the custom-operation prefix and quickening suffix. */
    private static List<String> operations(BytecodeNode node) {
        return node.getInstructionsAsList().stream()
                .map(ProtosInstructionNames::of)
                .map(name -> name.startsWith("c.") ? name.substring(2) : name)
                .map(name -> name.indexOf('$') < 0 ? name : name.substring(0, name.indexOf('$')))
                .toList();
    }

    private static BytecodeNode activationNode(ProtosClosureValue closure) {
        return closure.executionPlan()
                .orElseThrow()
                .bytecodeActivationRootForTesting()
                .getBytecodeNode();
    }

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
                bindings.createLocalSlot("Array", newObject());
                bindings.freeze();

                test.run(
                        new Fixture(
                                LANGUAGE_REF.get(null),
                                new ProtosPrelude(bindings, contextPrototype).newModuleActivation()));
            } finally {
                context.leave();
            }
        }
    }

    private static ProtosClosureValue closure(Fixture fixture, String characters) {
        return assertInstanceOf(ProtosClosureValue.class, run(fixture, characters));
    }

    private static ProtosActivation invocation(Fixture fixture, ProtosClosureValue closure, Object... supplied) {
        return ProtosActivation.forClosureInvocation(
                closure,
                List.of(supplied),
                fixture.module.prelude().orElseThrow(),
                fixture.module.actorModuleState(),
                fixture.module.currentModuleKey().orElse(null),
                fixture.module.executionDomain());
    }

    private static Object execute(Fixture fixture, ProtosClosureValue closure, Object... supplied) {
        return closure.executionPlan().orElseThrow().executeBytecodeActivationForTesting(
                invocation(fixture, closure, supplied));
    }

    private static ProtosObjectValue observed(Fixture fixture, String source) {
        return assertInstanceOf(ProtosObjectValue.class, execute(fixture, closure(fixture, source)));
    }

    private static Object local(ProtosObjectValue object, String name) {
        return object.readLocalSlot(name).orElseThrow();
    }

    private static ProtosClosureValue callable(ProtosObjectValue object, String name) {
        return assertInstanceOf(ProtosClosureValue.class, local(object, name));
    }

    private static Object run(Fixture fixture, String characters) {
        return lower(fixture.language, characters).getCallTarget().call(fixture.module);
    }

    private static ProtosBytecodeRootNode lower(ProtosLanguage language, String characters) {
        Source source = Source.newBuilder(ProtosLanguage.ID, characters, "i115-published-context.protos").build();
        return new CanonicalToBytecodeLowerer(language, source).lowerRoot(canonicalize(characters));
    }

    private static CanonicalSequence canonicalize(String characters) {
        return (CanonicalSequence)
                new Canonicalizer().canonicalize(new ProtosParser(characters).parseProgram());
    }

    private static ProtosObjectValue newObject() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }
}

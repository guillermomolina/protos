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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.source.Source;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF025-D179-A, reconciled with I115: binding membership stays exact under
 * D179 structural removal and recreation. The retired frame lexical layout
 * and its per-definition presence-continuity tokens no longer exist; the
 * same guarantees are proved through observable binding presence of NAME
 * bindings (the storage a module root and a dynamic name use), read by a
 * real suspended lookup in two activations of one root.
 *
 * <p>KNOWN I115 SEMANTIC DIVERGENCE (D179): a captured CELL binding is never
 * removed or recreated, so removal/recreation is not asserted for cells.
 */
final class ProtosPerf025D179LexicalMembershipStabilityReadTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void removalAffectsOnlyTheExactBindingAndRecreationIsObserved() {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosBytecodeRootNode root = yieldingLookupRoot(LANGUAGE_REF.get(null), "x");

                /*
                 * Two activations of the same root keep independent binding
                 * presence and values in their own execution contexts.
                 */
                ProtosActivation firstModule = moduleActivation();
                ProtosActivation secondModule = moduleActivation();

                ContinuationResult firstContinuation = suspend(root, firstModule);
                ContinuationResult secondContinuation = suspend(root, secondModule);

                ProtosObjectValue firstContext = firstModule.context();
                ProtosObjectValue secondContext = secondModule.context();

                ProtosObjectValue firstX =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosObjectValue secondX =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());

                firstContext.createLocalSlot("x", firstX);
                firstContext.createLocalSlot("y", ProtosNullValue.INSTANCE);
                secondContext.createLocalSlot("x", secondX);

                ProtosObjectValue secondAssignedX =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                secondContext.assignLocalSlot("x", secondAssignedX);

                // Removing x from one activation affects neither y nor the other activation.
                assertSame(firstX, firstContext.removeLocalSlot("x"));
                assertFalse(firstContext.hasLocalSlot("x"));
                assertTrue(firstContext.hasLocalSlot("y"));
                assertTrue(secondContext.hasLocalSlot("x"));

                // D179 C0 permits recreation while OPEN.
                ProtosObjectValue recreatedX =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                firstContext.createLocalSlot("x", recreatedX);

                // The second activation never lost x and reads its own assigned value.
                assertSame(
                        secondAssignedX,
                        secondContinuation.continueWith(ProtosNullValue.INSTANCE));

                // The first activation lost and recreated x: no stale presence or value.
                assertSame(
                        recreatedX,
                        firstContinuation.continueWith(ProtosNullValue.INSTANCE));

                // PRESENT(null) remains PRESENT until its own removal.
                assertSame(
                        ProtosNullValue.INSTANCE,
                        firstContext.readLocalSlot("y").orElseThrow());
                assertSame(
                        ProtosNullValue.INSTANCE,
                        firstContext.removeLocalSlot("y"));
                assertFalse(firstContext.hasLocalSlot("y"));
            } finally {
                context.leave();
            }
        }
    }

    /**
     * A lowered module root keeps its exact binding behavior across a
     * Bytecode reparse: a binding removed through the Context established
     * before the reparse is ABSENT, and executing the reparsed root
     * re-establishes it.
     */
    @Test
    void loweredRootKeepsExactMembershipAcrossBytecodeReparse() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                String characters = "x: null\ncontext\nx";
                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID,
                                        characters,
                                        "perf025-d179-reparse.protos")
                                .build();

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source)
                                .lowerRoot(canonicalize(characters));

                ProtosActivation module = moduleActivation();
                assertSame(ProtosNullValue.INSTANCE, root.getCallTarget().call(module));
                assertTrue(module.context().hasLocalSlot("x"));

                root.getRootNodes().ensureComplete();

                assertSame(
                        ProtosNullValue.INSTANCE,
                        module.context().removeLocalSlot("x"));
                assertFalse(module.context().hasLocalSlot("x"));

                assertSame(ProtosNullValue.INSTANCE, root.getCallTarget().call(module),
                        "the reparsed root re-establishes the removed binding");
                assertTrue(module.context().hasLocalSlot("x"));

                ProtosActivation fresh = moduleActivation();
                assertSame(ProtosNullValue.INSTANCE, root.getCallTarget().call(fresh));
                assertNotSame(module.context(), fresh.context());
            } finally {
                context.leave();
            }
        }
    }

    /**
     * Two independently lowered roots reading the same name never share
     * binding state: removal under one never changes what the other reads.
     */
    @Test
    void independentRootsNeverShareBindingPresence() {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosBytecodeRootNode first = yieldingLookupRoot(language, "x");
                ProtosBytecodeRootNode second = yieldingLookupRoot(language, "x");
                assertNotSame(first, second);

                ProtosActivation firstModule = moduleActivation();
                ProtosActivation secondModule = moduleActivation();
                ContinuationResult firstContinuation = suspend(first, firstModule);
                ContinuationResult secondContinuation = suspend(second, secondModule);

                ProtosObjectValue firstX = new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosObjectValue secondX = new ProtosObjectValue(ProtosObjectValue.rootObject());
                firstModule.context().createLocalSlot("x", firstX);
                secondModule.context().createLocalSlot("x", secondX);
                firstModule.context().removeLocalSlot("x");

                assertSame(secondX, secondContinuation.continueWith(ProtosNullValue.INSTANCE));
                ProtosSignalException absent =
                        assertThrows(
                                ProtosSignalException.class,
                                () -> firstContinuation.continueWith(ProtosNullValue.INSTANCE));
                assertSame(
                        slotNotFoundOf(firstModule),
                        absent.error().parent().orElseThrow(),
                        "an ABSENT binding is SlotNotFound");
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void suspendedLookupPreservesPresenceAcrossRemovalAndRecreation() {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosBytecodeRootNode root = yieldingLookupRoot(LANGUAGE_REF.get(null), "x");

                /* A: a PRESENT binding is read. */
                ProtosActivation presentModule = moduleActivation();
                ContinuationResult presentContinuation = suspend(root, presentModule);
                ProtosObjectValue presentX =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                presentModule.context().createLocalSlot("x", presentX);
                assertSame(
                        presentX,
                        presentContinuation.continueWith(ProtosNullValue.INSTANCE));

                /*
                 * D: an activation whose x is never removed stays suspended
                 * across a removal performed in another activation.
                 */
                ProtosActivation independentModule = moduleActivation();
                ContinuationResult independentContinuation = suspend(root, independentModule);
                ProtosObjectValue independentX =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                independentModule.context().createLocalSlot("x", independentX);

                /* B: after removal the read reports ABSENT. */
                ProtosActivation removedModule = moduleActivation();
                ContinuationResult removedContinuation = suspend(root, removedModule);
                ProtosObjectValue removedX =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                removedModule.context().createLocalSlot("x", removedX);
                assertSame(removedX, removedModule.context().removeLocalSlot("x"));
                ProtosSignalException failure =
                        assertThrows(
                                ProtosSignalException.class,
                                () -> removedContinuation.continueWith(ProtosNullValue.INSTANCE));
                assertSame(
                        slotNotFoundOf(removedModule),
                        failure.error().parent().orElseThrow(),
                        "the read must raise its activation's SlotNotFound");

                /* C: recreation after removal is observed exactly. */
                ProtosActivation recreatedModule = moduleActivation();
                ContinuationResult recreatedContinuation = suspend(root, recreatedModule);
                recreatedModule.context().createLocalSlot(
                        "x", new ProtosObjectValue(ProtosObjectValue.rootObject()));
                recreatedModule.context().removeLocalSlot("x");
                ProtosObjectValue recreatedX =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                recreatedModule.context().createLocalSlot("x", recreatedX);
                assertSame(
                        recreatedX,
                        recreatedContinuation.continueWith(ProtosNullValue.INSTANCE));

                /* D: the untouched activation still reads its own x. */
                assertSame(
                        independentX,
                        independentContinuation.continueWith(ProtosNullValue.INSTANCE));

                /* E: PRESENT(null) is presence, not absence. */
                ProtosActivation nullModule = moduleActivation();
                ContinuationResult nullContinuation = suspend(root, nullModule);
                nullModule.context().createLocalSlot("x", ProtosNullValue.INSTANCE);
                assertSame(
                        ProtosNullValue.INSTANCE,
                        nullContinuation.continueWith(ProtosNullValue.INSTANCE));
            } finally {
                context.leave();
            }
        }
    }

    private static ContinuationResult suspend(
            ProtosBytecodeRootNode root, ProtosActivation module) {
        return assertInstanceOf(
                ContinuationResult.class,
                root.getCallTarget().call(module));
    }

    private static ProtosObjectValue slotNotFoundOf(
            ProtosActivation activation) {
        return ProtosCoreErrors.prototype(
                activation,
                ProtosCoreErrors.StandardError.SLOT_NOT_FOUND);
    }

    /**
     * Suspends once, so the test can perform D179 mutations through the
     * genuine execution Context, then returns the dynamic NAME lookup of
     * {@code name} in the activation of argument 0.
     */
    private static ProtosBytecodeRootNode yieldingLookupRoot(
            ProtosLanguage language, String name) {
        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();

                            builder.beginYield();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endYield();

                            builder.beginReturn();
                            builder.beginAccessNamedLexical(ProtosNamedLexicalAccess.LOOKUP);
                            builder.emitLoadArgument(0);
                            builder.emitLoadConstant(name);
                            builder.endAccessNamedLexical();
                            builder.endReturn();

                            builder.endRoot();
                        });
        return roots.getNode(0);
    }

    private static CanonicalSequence canonicalize(String characters) {
        return (CanonicalSequence)
                new Canonicalizer()
                        .canonicalize(
                                new ProtosParser(characters)
                                        .parseProgram());
    }

    private static ProtosActivation moduleActivation() {
        ProtosStandardObjectProtocol.install();

        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings =
                new ProtosObjectValue(contextPrototype);

        bindings.createLocalSlot("Context", contextPrototype);
        ProtosObjectValue errorPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        bindings.createLocalSlot("Error", errorPrototype);
        bindings.createLocalSlot(
                "SlotNotFound", new ProtosObjectValue(errorPrototype));
        bindings.createLocalSlot(
                "Array",
                new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.freeze();

        return new ProtosPrelude(
                        bindings,
                        contextPrototype)
                .newModuleActivation();
    }
}

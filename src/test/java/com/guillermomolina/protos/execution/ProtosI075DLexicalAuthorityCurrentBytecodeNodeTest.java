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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I075-D: the frame-backed lexical authority must address the declaring root's
 * <em>current</em> {@code BytecodeNode}, not the node that was current when
 * the authority was installed. With boxing elimination the cached node owns
 * local-kind metadata, so a write performed through a stale uncached node
 * leaves the physical frame PRESENT while the cached node still records the
 * local as unwritten, and the next current-node read fails.
 *
 * <p>The scenario is built directly on the generated builder because it needs
 * a suspension between authority installation and binding creation, and a
 * deterministic uncached-to-cached transition at the resume boundary.
 */
final class ProtosI075DLexicalAuthorityCurrentBytecodeNodeTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void bindingCreatedAfterUncachedToCachedTransitionIsReadByTheCurrentNode() {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosSemanticBytecodeRootNode root = installThenTwiceYieldingRoot(LANGUAGE_REF.get(null));
                ProtosActivation module = moduleActivation();
                ProtosObjectValue executionContext = module.context();

                /*
                 * Threshold 1: the first entry consumes the count and runs
                 * uncached; the resume after the first Yield finds the count
                 * exhausted and transitions the root to the cached node.
                 */
                root.getBytecodeNode().setUncachedThreshold(1);
                assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());

                ContinuationResult beforeTransition =
                        assertInstanceOf(ContinuationResult.class, root.getCallTarget().call(module));
                assertEquals(
                        BytecodeTier.UNCACHED,
                        root.getBytecodeNode().getTier(),
                        "authority is installed while the root is still uncached");
                assertFalse(
                        executionContext.hasLocalSlot("x"),
                        "the statically allocated local is still ABSENT at the transition");

                ContinuationResult afterTransition =
                        assertInstanceOf(
                                ContinuationResult.class,
                                beforeTransition.continueWith(ProtosNullValue.INSTANCE));
                assertEquals(
                        BytecodeTier.CACHED,
                        root.getBytecodeNode().getTier(),
                        "the resume must have moved the root to the cached node");
                assertFalse(executionContext.hasLocalSlot("x"));

                // ABSENT -> PRESENT through the retained authority, after the transition.
                ProtosObjectValue first = new ProtosObjectValue(ProtosObjectValue.rootObject());
                executionContext.createLocalSlot("x", first);
                assertSame(first, executionContext.readLocalSlot("x").orElseThrow());

                // PRESENT -> ABSENT -> PRESENT keeps the same static physical binding.
                assertSame(first, executionContext.removeLocalSlot("x"));
                assertFalse(executionContext.hasLocalSlot("x"));
                ProtosObjectValue second = new ProtosObjectValue(ProtosObjectValue.rootObject());
                executionContext.createLocalSlot("x", second);

                // PRESENT -> PRESENT mutation while writable.
                ProtosObjectValue third = new ProtosObjectValue(ProtosObjectValue.rootObject());
                executionContext.assignLocalSlot("x", third);
                assertSame(third, executionContext.readLocalSlot("x").orElseThrow());

                // The direct current-node ReadFrameLocal must observe the same value.
                assertSame(third, afterTransition.continueWith(ProtosNullValue.INSTANCE));
            } finally {
                context.leave();
            }
        }
    }

    /**
     * PERF029: installing the persistent frame authority over a context that
     * already holds bindings migrates them through the name-keyed {@link
     * ProtosFrameLexicalBindingAuthority#putBinding}, whose frame ordinal is
     * a runtime value and so must stay outside partial evaluation. The handoff
     * itself must keep its exact semantics: a layout name becomes PRESENT in
     * its frame local, a dynamic name keeps its value and order, and a layout
     * name never established stays ABSENT until created.
     */
    @Test
    void installationOverExistingBindingsMigratesThemThroughTheGenericPath() throws Exception {
        assertTrue(
                ProtosFrameLexicalBindingAuthority.class
                        .getMethod("putBinding", String.class, Object.class)
                        .isAnnotationPresent(TruffleBoundary.class),
                "a runtime-name frame ordinal is never a partial-evaluation constant");

        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosSemanticBytecodeRootNode root = installThenReadRoot(LANGUAGE_REF.get(null));
                ProtosActivation module = moduleActivation();
                ProtosObjectValue executionContext = module.context();

                ProtosObjectValue dynamic = new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosObjectValue migrated = new ProtosObjectValue(ProtosObjectValue.rootObject());
                executionContext.createLocalSlot("dynamic", dynamic);
                executionContext.createLocalSlot("x", migrated);

                // The root's own direct frame read observes the migrated binding.
                assertSame(migrated, root.getCallTarget().call(module));

                assertEquals(
                        List.of("dynamic", "x"),
                        List.copyOf(executionContext.localSlotsSnapshot().keySet()),
                        "migration preserves establishment order");
                assertSame(dynamic, executionContext.readLocalSlot("dynamic").orElseThrow());
                assertFalse(executionContext.hasLocalSlot("y"), "layout allocation is not presence");

                // PRESENT(null) is distinct from ABSENT; duplicate creation is rejected.
                executionContext.createLocalSlot("y", ProtosNullValue.INSTANCE);
                assertTrue(executionContext.hasLocalSlot("y"));
                assertThrows(
                        IllegalStateException.class,
                        () -> executionContext.createLocalSlot("y", ProtosNullValue.INSTANCE));

                // Removal then recreation re-establishes the binding last.
                assertSame(migrated, executionContext.removeLocalSlot("x"));
                assertFalse(executionContext.hasLocalSlot("x"));
                executionContext.createLocalSlot("x", dynamic);
                assertEquals(
                        List.of("dynamic", "y", "x"),
                        List.copyOf(executionContext.localSlotsSnapshot().keySet()));
            } finally {
                context.leave();
            }
        }
    }

    /**
     * {@code x} and {@code y} are statically allocated frame-backed locals.
     * The root installs the frame lexical authority and returns the direct
     * {@code ReadFrameLocal} of {@code x}.
     */
    private static ProtosSemanticBytecodeRootNode installThenReadRoot(ProtosLanguage language) {
        BytecodeRootNodes<ProtosSemanticBytecodeRootNode> roots =
                ProtosSemanticBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();

                            BytecodeLocal x = builder.createLocal("x", null);
                            BytecodeLocal y = builder.createLocal("y", null);
                            BytecodeLocal[] locals = {x, y};
                            ProtosFrameLexicalLayout layout =
                                    ProtosFrameLexicalLayout.of(
                                            new String[] {"x", "y"},
                                            ProtosFrameLexicalLayout.localOffsetsOf(locals));
                            builder.beginInstallFrameLexicalAuthority(locals, layout);
                            builder.emitLoadArgument(0);
                            builder.endInstallFrameLexicalAuthority();

                            builder.beginReturn();
                            builder.beginReadFrameLocal(
                                    x,
                                    layout.presentContinuityAt(0));
                            builder.emitLoadArgument(0);
                            builder.emitLoadConstant("x");
                            builder.endReadFrameLocal();
                            builder.endReturn();

                            builder.endRoot();
                        });
        return roots.getNode(0);
    }

    /**
     * {@code x} is a statically allocated frame-backed local. The root installs
     * the frame lexical authority, suspends twice without ever writing {@code
     * x}, and finally returns the direct {@code ReadFrameLocal} of {@code x}.
     */
    private static ProtosSemanticBytecodeRootNode installThenTwiceYieldingRoot(ProtosLanguage language) {
        BytecodeRootNodes<ProtosSemanticBytecodeRootNode> roots =
                ProtosSemanticBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();

                            BytecodeLocal x = builder.createLocal("x", null);
                            BytecodeLocal[] locals = {x};
                            ProtosFrameLexicalLayout layout =
                                    ProtosFrameLexicalLayout.of(
                                            new String[] {"x"},
                                            ProtosFrameLexicalLayout.localOffsetsOf(locals));
                            builder.beginInstallFrameLexicalAuthority(locals, layout);
                            builder.emitLoadArgument(0);
                            builder.endInstallFrameLexicalAuthority();

                            builder.beginYield();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endYield();

                            builder.beginYield();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endYield();

                            builder.beginReturn();
                            builder.beginReadFrameLocal(
                                    x,
                                    layout.presentContinuityAt(0));
                            builder.emitLoadArgument(0);
                            builder.emitLoadConstant("x");
                            builder.endReadFrameLocal();
                            builder.endReturn();

                            builder.endRoot();
                        });
        return roots.getNode(0);
    }

    private static ProtosActivation moduleActivation() {
        ProtosStandardObjectProtocol.install();
        ProtosObjectValue contextPrototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings = new ProtosObjectValue(contextPrototype);
        bindings.createLocalSlot("Context", contextPrototype);
        bindings.createLocalSlot("Error", new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.createLocalSlot("Array", new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.freeze();
        return new ProtosPrelude(bindings, contextPrototype).newModuleActivation();
    }
}

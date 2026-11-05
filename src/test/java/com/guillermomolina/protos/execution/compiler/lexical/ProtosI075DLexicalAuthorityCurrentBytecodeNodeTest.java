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
 * I075-D, reconciled with I115: a binding read must address the declaring
 * root's <em>current</em> {@code BytecodeNode}. With boxing elimination the
 * cached node owns local-kind metadata, so a value written after an
 * uncached-to-cached transition must be read back exactly by the current
 * node. The retired frame lexical authority is replaced by the native
 * storage that now owns a Closure binding: a LOCAL binding's Bytecode DSL
 * local and a CELL binding's cell held in such a local.
 *
 * <p>The scenarios are built directly on the generated builder because they
 * need a suspension between storage allocation and binding establishment,
 * and a deterministic uncached-to-cached transition at the resume boundary.
 */
final class ProtosI075DLexicalAuthorityCurrentBytecodeNodeTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    /** LOCAL storage: a boxing-eliminated long written after the transition. */
    @Test
    void localWrittenAfterUncachedToCachedTransitionIsReadByTheCurrentNode() {
        withEnteredContext(language -> {
            ProtosBytecodeRootNode root = establishLocalAfterResumeRoot(language);
            assertBindingSurvivesTransition(root, 42L, 42L);
            // A second invocation runs entirely on the cached node.
            assertBindingSurvivesTransition(root, 7L, 7L);
        });
    }

    /** LOCAL storage: a reference value written after the transition. */
    @Test
    void localReferenceWrittenAfterTransitionIsReadByTheCurrentNode() {
        withEnteredContext(language -> {
            ProtosBytecodeRootNode root = establishLocalAfterResumeRoot(language);
            ProtosObjectValue value = new ProtosObjectValue(ProtosObjectValue.rootObject());
            assertBindingSurvivesTransition(root, value, value);
        });
    }

    /** CELL storage: the cell created before the transition receives the value after it. */
    @Test
    void cellWrittenAfterUncachedToCachedTransitionIsReadByTheCurrentNode() {
        withEnteredContext(language -> {
            ProtosBytecodeRootNode root = establishCellAfterResumeRoot(language);
            assertBindingSurvivesTransition(root, 42L, 42L);
            ProtosObjectValue value = new ProtosObjectValue(ProtosObjectValue.rootObject());
            assertBindingSurvivesTransition(root, value, value);
        });
    }

    /**
     * PERF029, reconciled: a dynamic NAME read observes bindings that already
     * exist in the execution context before the root runs, with their exact
     * presence semantics: establishment order, PRESENT(null) distinct from
     * ABSENT, duplicate creation rejected, removal then recreation
     * re-establishing the binding last.
     */
    @Test
    void nameReadObservesExistingContextBindingsWithExactPresenceSemantics() {
        withEnteredContext(language -> {
            ProtosBytecodeRootNode root = namedLookupRoot(language, "x");
            ProtosActivation module = moduleActivation();
            ProtosObjectValue executionContext = module.context();

            ProtosObjectValue dynamic = new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosObjectValue existing = new ProtosObjectValue(ProtosObjectValue.rootObject());
            executionContext.createLocalSlot("dynamic", dynamic);
            executionContext.createLocalSlot("x", existing);

            assertSame(existing, root.getCallTarget().call(module));

            assertEquals(
                    List.of("dynamic", "x"),
                    List.copyOf(executionContext.localSlotsSnapshot().keySet()),
                    "reading preserves establishment order");
            assertSame(dynamic, executionContext.readLocalSlot("dynamic").orElseThrow());
            assertFalse(executionContext.hasLocalSlot("y"));

            executionContext.createLocalSlot("y", ProtosNullValue.INSTANCE);
            assertTrue(executionContext.hasLocalSlot("y"));
            assertThrows(
                    IllegalStateException.class,
                    () -> executionContext.createLocalSlot("y", ProtosNullValue.INSTANCE));

            assertSame(existing, executionContext.removeLocalSlot("x"));
            assertFalse(executionContext.hasLocalSlot("x"));
            executionContext.createLocalSlot("x", dynamic);
            assertEquals(
                    List.of("dynamic", "y", "x"),
                    List.copyOf(executionContext.localSlotsSnapshot().keySet()));
            assertSame(dynamic, root.getCallTarget().call(module));
        });
    }

    /*
     * Threshold 1: the first entry consumes the count and runs uncached; the
     * resume after the first Yield finds it exhausted and transitions the
     * root to the cached node. The binding is established from the resumed
     * value, after the transition, and read on the final resume.
     */
    private static void assertBindingSurvivesTransition(
            ProtosBytecodeRootNode root, Object written, Object expected) {
        ProtosActivation module = moduleActivation();
        boolean firstRun = root.getBytecodeNode().getTier() == BytecodeTier.UNCACHED;
        if (firstRun) {
            root.getBytecodeNode().setUncachedThreshold(1);
        }

        ContinuationResult beforeTransition =
                assertInstanceOf(ContinuationResult.class, root.getCallTarget().call(module));
        if (firstRun) {
            assertEquals(
                    BytecodeTier.UNCACHED,
                    root.getBytecodeNode().getTier(),
                    "storage is allocated while the root is still uncached");
        }

        ContinuationResult afterTransition =
                assertInstanceOf(
                        ContinuationResult.class,
                        beforeTransition.continueWith(written));
        assertEquals(
                BytecodeTier.CACHED,
                root.getBytecodeNode().getTier(),
                "the resume must have moved the root to the cached node");

        Object read = afterTransition.continueWith(ProtosNullValue.INSTANCE);
        if (expected instanceof Long) {
            assertEquals(expected, read);
        } else {
            assertSame(expected, read);
        }
    }

    /**
     * {@code x} is a LOCAL binding's native local. The root suspends, stores
     * the resumed value into {@code x}, suspends again and returns {@code x}.
     */
    private static ProtosBytecodeRootNode establishLocalAfterResumeRoot(ProtosLanguage language) {
        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();
                            BytecodeLocal x = builder.createLocal("x", null);

                            builder.beginStoreLocal(x);
                            builder.beginYield();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endYield();
                            builder.endStoreLocal();

                            builder.beginYield();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endYield();

                            builder.beginReturn();
                            builder.emitLoadLocal(x);
                            builder.endReturn();

                            builder.endRoot();
                        });
        return roots.getNode(0);
    }

    /**
     * {@code x} is a CELL binding: its local holds the cell created at entry,
     * before any suspension. The root suspends, stores the resumed value into
     * the cell, suspends again and returns the cell's value.
     */
    private static ProtosBytecodeRootNode establishCellAfterResumeRoot(ProtosLanguage language) {
        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();
                            BytecodeLocal x = builder.createLocal("x", null);

                            builder.beginStoreLocal(x);
                            builder.emitCreateCell();
                            builder.endStoreLocal();

                            builder.beginStoreCell();
                            builder.emitLoadLocal(x);
                            builder.beginYield();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endYield();
                            builder.endStoreCell();

                            builder.beginYield();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endYield();

                            builder.beginReturn();
                            builder.beginLoadCell();
                            builder.emitLoadLocal(x);
                            builder.endLoadCell();
                            builder.endReturn();

                            builder.endRoot();
                        });
        return roots.getNode(0);
    }

    /** The dynamic NAME lookup of {@code name} in the activation of argument 0. */
    private static ProtosBytecodeRootNode namedLookupRoot(ProtosLanguage language, String name) {
        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();
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

    private interface LanguageTest {
        void run(ProtosLanguage language);
    }

    private static void withEnteredContext(LanguageTest test) {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                test.run(LANGUAGE_REF.get(null));
            } finally {
                context.leave();
            }
        }
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

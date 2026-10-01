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
                            builder.beginInstallFrameLexicalAuthority(
                                    new BytecodeLocal[] {x},
                                    ProtosFrameLexicalLayout.of(new String[] {"x"}));
                            builder.emitLoadArgument(0);
                            builder.endInstallFrameLexicalAuthority();

                            builder.beginYield();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endYield();

                            builder.beginYield();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endYield();

                            builder.beginReturn();
                            builder.beginReadFrameLocal(x);
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

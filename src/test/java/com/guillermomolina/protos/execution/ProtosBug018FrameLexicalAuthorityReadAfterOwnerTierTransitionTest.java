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
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.util.List;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * BUG018-C: a {@link ProtosFrameLexicalBindingAuthority} retained by an
 * escaped execution context must read a binding PRESENT in its retained frame
 * exactly, even after another activation of the same root moved the root from
 * the uncached to the cached tier without ever writing that local. The cached
 * node's local-kind metadata is then still unset while the retained frame's
 * physical tag is PRESENT.
 *
 * <p>Shape: activation A1 runs uncached, installs its authority, and
 * establishes {@code x} (and {@code y} as PRESENT(null)) in its frame through
 * that authority; A1's context retains the frame. Activation A2 of the same
 * root enters cached and establishes nothing. A1's authority is then read
 * through its context. The transition is made deterministic with {@code
 * setUncachedThreshold(1)}, as in {@link
 * ProtosI075DLexicalAuthorityCurrentBytecodeNodeTest}.
 */
final class ProtosBug018FrameLexicalAuthorityReadAfterOwnerTierTransitionTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void retainedAuthorityReadOfPresentLocalSurvivesOwnerTransitionInAnotherActivation() {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosSemanticBytecodeRootNode root = installingRoot(LANGUAGE_REF.get(null));
                root.getBytecodeNode().setUncachedThreshold(1);
                assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());

                // A1: uncached; its authority retains A1's materialized frame.
                ProtosActivation first = moduleActivation();
                ProtosObjectValue firstContext = first.context();
                root.getCallTarget().call(first);

                ProtosObjectValue x = new ProtosObjectValue(ProtosObjectValue.rootObject());
                firstContext.createLocalSlot("x", x);
                firstContext.createLocalSlot("y", ProtosNullValue.INSTANCE);
                assertEquals(
                        BytecodeTier.UNCACHED,
                        root.getBytecodeNode().getTier(),
                        "A1's bindings are established while the root is uncached");

                // A2: the same root enters cached and establishes nothing.
                ProtosActivation second = moduleActivation();
                root.getCallTarget().call(second);
                assertEquals(
                        BytecodeTier.CACHED,
                        root.getBytecodeNode().getTier(),
                        "A2 must have moved the root to the cached tier");
                assertFalse(second.context().hasLocalSlot("x"));

                // Exact retained values, including PRESENT(null).
                assertSame(x, firstContext.readLocalSlot("x").orElseThrow());
                assertSame(
                        ProtosNullValue.INSTANCE,
                        firstContext.readLocalSlot("y").orElseThrow());
                assertFalse(firstContext.hasLocalSlot("z"), "ABSENT remains ABSENT");

                Map<String, Object> snapshot = firstContext.localSlotsSnapshot();
                assertEquals(List.of("x", "y"), List.copyOf(snapshot.keySet()));
                assertSame(x, snapshot.get("x"));
                assertSame(ProtosNullValue.INSTANCE, snapshot.get("y"));

                // Removal returns the exact previous value; the binding becomes ABSENT.
                assertSame(x, firstContext.removeLocalSlot("x"));
                assertFalse(firstContext.hasLocalSlot("x"));
                assertTrue(firstContext.hasLocalSlot("y"));

                // Recreation after the transition is read back exactly.
                ProtosObjectValue recreated =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                firstContext.createLocalSlot("x", recreated);
                assertSame(recreated, firstContext.readLocalSlot("x").orElseThrow());
            } finally {
                context.leave();
            }
        }
    }

    /**
     * {@code x}, {@code y} and {@code z} are statically allocated frame-backed
     * locals. The root installs the frame lexical authority and returns.
     */
    private static ProtosSemanticBytecodeRootNode installingRoot(ProtosLanguage language) {
        BytecodeRootNodes<ProtosSemanticBytecodeRootNode> roots =
                ProtosSemanticBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();

                            BytecodeLocal[] locals = {
                                builder.createLocal("x", null),
                                builder.createLocal("y", null),
                                builder.createLocal("z", null)
                            };
                            builder.beginInstallFrameLexicalAuthority(
                                    locals,
                                    ProtosFrameLexicalLayout.of(
                                            new String[] {"x", "y", "z"},
                                            ProtosFrameLexicalLayout.localOffsetsOf(locals)));
                            builder.emitLoadArgument(0);
                            builder.endInstallFrameLexicalAuthority();

                            builder.beginReturn();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
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

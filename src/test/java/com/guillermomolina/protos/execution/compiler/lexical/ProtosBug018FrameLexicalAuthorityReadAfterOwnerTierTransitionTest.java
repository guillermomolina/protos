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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosLexicalCell;
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
 * BUG018-C, reconciled with I115: storage retained by an escaped activation
 * must still be read exactly after another activation of the same root moved
 * the root from the uncached to the cached tier without ever establishing
 * that binding. The cached node's local-kind metadata is then still unset
 * while the retained activation's frame holds the binding's storage.
 *
 * <p>The retired frame lexical authority retained through an escaped context
 * is replaced by the storage that now escapes: a CELL binding's cell, held in
 * a native local of its owning root and shared with every capturer.
 *
 * <p>Shape: activation A1 runs uncached, creates the cells of {@code x} and
 * {@code y}, establishes {@code x} and {@code y} as PRESENT(null), escapes
 * the cell of {@code x} and suspends. Activation A2 of the same root enters
 * cached, establishes nothing and suspends. A1 then resumes on the cached
 * node and reads its own retained local of {@code y}. The transition is made
 * deterministic with {@code setUncachedThreshold(1)}, as in {@link
 * ProtosI075DLexicalAuthorityCurrentBytecodeNodeTest}.
 */
final class ProtosBug018FrameLexicalAuthorityReadAfterOwnerTierTransitionTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void retainedCellReadOfPresentBindingSurvivesOwnerTransitionInAnotherActivation() {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosBytecodeRootNode root = cellOwningRoot(LANGUAGE_REF.get(null));
                root.getBytecodeNode().setUncachedThreshold(1);
                assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());

                // A1: uncached; establishes x and y and escapes the cell of x.
                ProtosObjectValue x = new ProtosObjectValue(ProtosObjectValue.rootObject());
                ContinuationResult first =
                        assertInstanceOf(
                                ContinuationResult.class,
                                root.getCallTarget().call(moduleActivation(), Boolean.TRUE, x));
                ProtosLexicalCell firstX =
                        assertInstanceOf(ProtosLexicalCell.class, first.getResult());
                assertSame(x, firstX.valueOrNullForRuntime());
                assertEquals(
                        BytecodeTier.UNCACHED,
                        root.getBytecodeNode().getTier(),
                        "A1's bindings are established while the root is uncached");

                // A2: the same root enters cached and establishes nothing.
                ContinuationResult second =
                        assertInstanceOf(
                                ContinuationResult.class,
                                root.getCallTarget().call(
                                        moduleActivation(),
                                        Boolean.FALSE,
                                        ProtosNullValue.INSTANCE));
                assertEquals(
                        BytecodeTier.CACHED,
                        root.getBytecodeNode().getTier(),
                        "A2 must have moved the root to the cached tier");
                ProtosLexicalCell secondX =
                        assertInstanceOf(ProtosLexicalCell.class, second.getResult());
                assertNotSame(firstX, secondX);
                assertNull(secondX.valueOrNullForRuntime(), "ABSENT remains ABSENT");

                // A1 resumes on the cached node: its retained local of y is read exactly.
                ProtosLexicalCell firstY =
                        assertInstanceOf(
                                ProtosLexicalCell.class,
                                first.continueWith(ProtosNullValue.INSTANCE));
                assertSame(
                        ProtosNullValue.INSTANCE,
                        firstY.valueOrNullForRuntime(),
                        "PRESENT(null) is read exactly, distinct from ABSENT");
                assertSame(x, firstX.valueOrNullForRuntime(), "A2 never touched A1's cell");

                ProtosLexicalCell secondY =
                        assertInstanceOf(
                                ProtosLexicalCell.class,
                                second.continueWith(ProtosNullValue.INSTANCE));
                assertNotSame(firstY, secondY);
                assertNull(secondY.valueOrNullForRuntime());

                // A write after the transition through the retained cell is read back exactly.
                ProtosObjectValue reassigned =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                firstX.setForRuntime(reassigned);
                assertSame(reassigned, firstX.valueOrNullForRuntime());
            } finally {
                context.leave();
            }
        }
    }

    /**
     * {@code x} and {@code y} are CELL bindings: each native local holds the
     * cell created at entry. When argument 1 is true the root establishes
     * {@code x} from argument 2 and {@code y} as PRESENT(null). It then
     * suspends yielding the cell of {@code x}, and on resume returns the cell
     * of {@code y} read from its own local.
     */
    private static ProtosBytecodeRootNode cellOwningRoot(ProtosLanguage language) {
        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();

                            BytecodeLocal x = builder.createLocal("x", null);
                            BytecodeLocal y = builder.createLocal("y", null);
                            for (BytecodeLocal cell : new BytecodeLocal[] {x, y}) {
                                builder.beginStoreLocal(cell);
                                builder.emitCreateCell();
                                builder.endStoreLocal();
                            }

                            builder.beginIfThen();
                            builder.emitLoadArgument(1);
                            builder.beginBlock();
                            builder.beginStoreCell();
                            builder.emitLoadLocal(x);
                            builder.emitLoadArgument(2);
                            builder.endStoreCell();
                            builder.beginStoreCell();
                            builder.emitLoadLocal(y);
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endStoreCell();
                            builder.endBlock();
                            builder.endIfThen();

                            builder.beginYield();
                            builder.emitLoadLocal(x);
                            builder.endYield();

                            builder.beginReturn();
                            builder.emitLoadLocal(y);
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

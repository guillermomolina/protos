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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.util.Objects;

/**
 * PLAT026 semantic-root Bytecode shell.
 *
 * <p>The existing {@link ProtosBytecodeRootNode} remains the untagged execution/helper
 * interpreter that owns all canonical operations. This root exists one-to-one with a
 * guest-semantic top-level/module/Closure activation and composes that helper root's
 * complete C-prime continuation. It therefore supplies truthful automatic RootTag
 * identity without tagging Object-construction, I/O-operation, lifecycle-release or
 * other implementation helper roots.
 *
 * <p>This shell does not own a second Protos execution model. Its only state is the
 * nested helper continuation returned by the existing Bytecode DSL interpreter.
 */
@GenerateBytecode(
        languageClass = ProtosLanguage.class,
        enableYield = true,
        enableTagInstrumentation = true,
        enableRootTagging = true,
        enableRootBodyTagging = false,
        tagTreeNodeLibrary = ProtosBytecodeTagTreeNodeExports.class)
abstract class ProtosSemanticBytecodeRootNode extends RootNode implements BytecodeRootNode {
    protected ProtosSemanticBytecodeRootNode(
            ProtosLanguage language,
            FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    public static final class InvokeSemanticHelper {
        @Specialization
        public static Object perform(
                CallTarget helperTarget,
                ProtosActivation activation) {
            return Objects.requireNonNull(
                    helperTarget.call(activation),
                    "semantic Bytecode helper root returned null");
        }
    }

    @Operation
    public static final class IsSemanticContinuation {
        @Specialization
        public static boolean perform(Object value) {
            return value instanceof ContinuationResult;
        }
    }

    @Operation
    public static final class ResumeSemanticContinuation {
        @Specialization
        public static Object perform(
                Object continuation,
                Object resumeValue) {
            if (!(continuation instanceof ContinuationResult child)) {
                throw new IllegalStateException(
                        "semantic Bytecode root resumed a non-continuation child");
            }
            return Objects.requireNonNull(
                    child.continueWith(resumeValue),
                    "semantic Bytecode helper continuation returned null");
        }
    }

    static RootCallTarget wrap(
            ProtosLanguage language,
            Source source,
            SourceSpan semanticSpan,
            CallTarget helperTarget) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(semanticSpan, "semanticSpan");
        Objects.requireNonNull(helperTarget, "helperTarget");

        BytecodeRootNodes<ProtosSemanticBytecodeRootNode> roots =
                ProtosSemanticBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginSource(source);
                            builder.beginSourceSection(
                                    semanticSpan.startOffset(),
                                    semanticSpan.length());
                            builder.beginRoot();

                            BytecodeLocal child =
                                    builder.createLocal(
                                            "semanticRootChild",
                                            null);
                            BytecodeLocal resume =
                                    builder.createLocal(
                                            "semanticRootResume",
                                            null);

                            builder.beginStoreLocal(child);
                            builder.beginInvokeSemanticHelper();
                            builder.emitLoadConstant(helperTarget);
                            builder.emitLoadArgument(0);
                            builder.endInvokeSemanticHelper();
                            builder.endStoreLocal();

                            builder.beginWhile();
                            builder.beginIsSemanticContinuation();
                            builder.emitLoadLocal(child);
                            builder.endIsSemanticContinuation();

                            builder.beginBlock();
                            builder.beginStoreLocal(resume);
                            builder.beginYield();
                            builder.emitLoadLocal(child);
                            builder.endYield();
                            builder.endStoreLocal();

                            builder.beginStoreLocal(child);
                            builder.beginResumeSemanticContinuation();
                            builder.emitLoadLocal(child);
                            builder.emitLoadLocal(resume);
                            builder.endResumeSemanticContinuation();
                            builder.endStoreLocal();
                            builder.endBlock();
                            builder.endWhile();

                            builder.beginReturn();
                            builder.emitLoadLocal(child);
                            builder.endReturn();

                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });

        return roots.getNode(0).getCallTarget();
    }
}

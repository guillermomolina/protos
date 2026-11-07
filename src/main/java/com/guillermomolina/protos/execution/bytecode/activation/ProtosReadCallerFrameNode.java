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
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * I115: GraalPy ReadFrameNode-style cold caller recovery.
 *
 * The ordinary dispatcher leaves the caller slot empty until the
 * effective callee requests it. On first demand, a Truffle stack walk
 * recovers the active caller and registers the callee's caller need.
 * Subsequent calls use the existing demand-driven B5 transport.
 *
 * This reader does not retain ancestral frames or add an allocation
 * to the ordinary direct-call path.
 */
final class ProtosReadCallerFrameNode {

    private record LocatedCaller(
            Frame frame,
            ProtosBytecodeRootNode calleeRoot) {}

    private ProtosReadCallerFrameNode() {}

    @TruffleBoundary
    static ProtosActivation resolve(Object[] calleeArguments) {
        LocatedCaller located = Truffle.getRuntime().iterateFrames(
                new FrameInstanceVisitor<LocatedCaller>() {
                    private boolean foundCallee;
                    private ProtosBytecodeRootNode calleeRoot;

                    @Override
                    public LocatedCaller visitFrame(
                            FrameInstance instance) {
                        Frame frame = instance.getFrame(
                                FrameInstance.FrameAccess.READ_ONLY);
                        RootNode root = ((RootCallTarget)
                                instance.getCallTarget()).getRootNode();

                        if (!foundCallee) {
                            if (frame.getArguments() == calleeArguments) {
                                foundCallee = true;
                                if (root instanceof ProtosBytecodeRootNode
                                        semantic
                                        && semantic.isSemanticActivationRoot()) {
                                    calleeRoot = semantic;
                                }
                            }
                            return null;
                        }

                        if (calleeRoot == null) {
                            return null;
                        }

                        boolean sourceRoot =
                                root instanceof ProtosBytecodeRootNode semantic
                                        && semantic.isSemanticActivationRoot();
                        boolean builtinRoot =
                                root instanceof ProtosBuiltinCallRootNode;

                        if ((sourceRoot || builtinRoot)
                                && ProtosFrameArguments.hasActivation(frame)) {
                            return new LocatedCaller(frame, calleeRoot);
                        }

                        return null;
                    }
                });

        if (located == null) {
            throw new IllegalStateException(
                    "I115: caller frame unavailable for stack recovery");
        }

        ProtosActivation caller =
                ProtosFrameArguments.activation(located.frame());

        /*
         * GraalPy PRootNode.updateCallerFlags / ReadFrameNode:
         * register the missing caller dependency and invalidate
         * incoming direct-call assumptions for subsequent calls.
         */
        located.calleeRoot().requestCallerNeed(
                ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION);

        return caller;
    }
}

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

import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * I115-H4-B EXPERIMENTAL (GraalPy CalleeContext): the root-entry and
 * root-exit work of a root marked by the lowerer as an experimental callee
 * context root, installed as a child of that root and run by its {@code
 * EnterRoot} prolog and its return and exceptional epilogues.
 *
 * <p>The per-invocation state is a {@link FrameInfo} stored in the
 * CURRENT_FRAME_INFO slot of the invocation's own GraalPy unary frame
 * arguments ({@link ProtosFrameArguments#isExperimentalGraalPyUnaryCall});
 * nothing per invocation is stored in this node or in its root, so recursion
 * never overwrites it. A root entered with B5 or the static unary layout has
 * no such slot and records no frame info.
 *
 * <p>No ProtosActivation is materialized, no Object.call, Task, Prelude,
 * ReturnHome or continuation is consulted or transported. See the I115-H4-B
 * block of {@link ProtosExperimentalCallUnaryNode} for the accepted
 * semantic divergences.
 */
final class ProtosExperimentalCalleeContext extends Node {

    /**
     * GraalPy PFrame.Reference counterpart: the identity of one experimental
     * invocation. Never a guest value.
     */
    static final class FrameInfo {
        private final ProtosBytecodeRootNode root;
        private final Object caller;
        private final ProtosLanguageContext context;
        private BytecodeNode exitBytecodeNode;

        FrameInfo(ProtosBytecodeRootNode root, Object caller, ProtosLanguageContext context) {
            this.root = root;
            this.caller = caller;
            this.context = context;
        }

        ProtosBytecodeRootNode root() {
            return root;
        }

        /** The caller reference transported by the call context, or null. */
        Object caller() {
            return caller;
        }

        ProtosLanguageContext context() {
            return context;
        }

        /** The Bytecode node the invocation exited from, or null while it runs. */
        BytecodeNode exitBytecodeNode() {
            return exitBytecodeNode;
        }
    }

    void enter(VirtualFrame frame, ProtosBytecodeRootNode root) {
        Object[] arguments = frame.getArguments();
        if (ProtosFrameArguments.isExperimentalGraalPyUnaryCall(arguments)) {
            ProtosFrameArguments.setExperimentalFrameInfo(
                    arguments,
                    new FrameInfo(
                            root,
                            ProtosFrameArguments.experimentalCaller(arguments),
                            ProtosLanguageContext.current(root)));
        }
    }

    void exit(VirtualFrame frame, ProtosBytecodeRootNode root, BytecodeNode bytecodeNode) {
        Object[] arguments = frame.getArguments();
        if (ProtosFrameArguments.isExperimentalGraalPyUnaryCall(arguments)
                && ProtosFrameArguments.experimentalFrameInfo(arguments) instanceof FrameInfo info) {
            assert info.root == root : "experimental frame info of another root";
            info.exitBytecodeNode = bytecodeNode;
            ProtosFrameArguments.setExperimentalFrameInfo(arguments, null);
        }
    }
}

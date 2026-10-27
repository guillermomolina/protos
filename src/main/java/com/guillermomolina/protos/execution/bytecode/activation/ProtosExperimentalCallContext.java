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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * I115-H4-B EXPERIMENTAL (GraalPy ExecutionContext.CallContext): the
 * caller-side preparation of the B5+ header staged by {@link
 * ProtosFrameArguments#admittedTerminalClosureCallOneToInstall} before the
 * direct call of {@link ProtosExperimentalCallDispatchers}.
 *
 * <p>{@link #prepareCall} installs the B5 caller slot exactly as the
 * ordinary admitted terminal Call1 passes it ({@link
 * ProtosBytecodeRootNode.InvokeClosureOneTail#callerArgument}): the
 * caller's published activation or its own still-compact B5 arguments, or,
 * when the callee root registered NEEDS_CALLER_PRELUDE, a
 * ProtosCallerFrameReference transporting the caller's prelude. Nothing is
 * published or materialized.
 *
 * <p>B5 has no caller-less admitted header: the caller identity is the
 * Protos caller of the invocation and has no thread-state substitute (GraalPy
 * reads its indirect caller from the thread state). The indirect entry
 * therefore rejects a call without a caller frame as an internal error;
 * the Bytecode DSL always executes this operation with its frame.
 */
final class ProtosExperimentalCallContext extends Node {

    @NeverDefault
    static ProtosExperimentalCallContext create() {
        return new ProtosExperimentalCallContext();
    }

    /*
     * {@code callerRoot} is the root executing the Call1, bound statically by
     * the Bytecode DSL at the operation; {@code target} is the callee's.
     */
    void prepareCall(
            VirtualFrame frame,
            ProtosBytecodeRootNode callerRoot,
            Object[] arguments,
            RootCallTarget target) {
        ProtosBytecodeRootNode callee = (ProtosBytecodeRootNode) target.getRootNode();
        Object caller =
                ProtosBytecodeRootNode.InvokeClosureOneTail.callerReference(frame);
        if (callee.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE)) {
            caller = ProtosBytecodeRootNode.InvokeClosureOneTail.callerArgument(
                    callerRoot,
                    callee,
                    ProtosFrameArguments.admittedInvokedClosure(arguments),
                    caller);
        }
        ProtosFrameArguments.installAdmittedCallerSlot(arguments, caller);
    }

    void enterIndirect(Object[] arguments) {
        throw CompilerDirectives.shouldNotReachHere(
                "experimental B5 Call1 without a caller frame");
    }

    void exitIndirect(Object[] arguments) {
        throw CompilerDirectives.shouldNotReachHere(
                "experimental B5 Call1 without a caller frame");
    }
}

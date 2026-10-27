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

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/**
 * I115-H4-B EXPERIMENTAL (GraalPy CallDispatchers): the inlined direct-call
 * helpers of {@link ProtosExperimentalCallUnaryNode}. They prepare the GraalPy
 * unary frame-argument layout and perform the direct call; the ordinary
 * Closure dispatch does not use them.
 */
final class ProtosExperimentalCallDispatchers {

    private ProtosExperimentalCallDispatchers() {}

    /**
     * GraalPy FunctionDirectInvokeNode: installs the invoked Closure and its
     * captured lexical environment in the experimental header, then performs
     * the simple direct call.
     */
    @GenerateInline
    @GenerateCached(false)
    abstract static class ExperimentalFunctionDirectInvokeNode extends Node {

        abstract Object execute(
                VirtualFrame frame,
                Node inliningTarget,
                DirectCallNode callNode,
                ProtosClosureValue callee,
                Object[] arguments);

        @Specialization
        static Object doDirect(
                VirtualFrame frame,
                Node inliningTarget,
                DirectCallNode callNode,
                ProtosClosureValue callee,
                Object[] arguments,
                @Cached ExperimentalSimpleDirectInvokeNode invoke) {
            assert callNode.getCallTarget()
                            == ProtosExperimentalCallUnaryNode.experimentalTargetOrNull(callee)
                    : "experimental direct call target mismatch";
            ProtosFrameArguments.setExperimentalGlobals(
                    arguments, callee.capturedLexicalEnvironmentForRuntime());
            ProtosFrameArguments.setExperimentalClosure(arguments, callee);
            return invoke.execute(frame, inliningTarget, callNode, arguments);
        }
    }

    /**
     * GraalPy SimpleDirectInvokeNode: prepares the call context of the
     * caller frame and calls the target; without a caller frame, the
     * indirect entry and exit of the call context bracket the call.
     */
    @GenerateInline
    @GenerateCached(false)
    abstract static class ExperimentalSimpleDirectInvokeNode extends Node {

        abstract Object execute(
                VirtualFrame frame,
                Node inliningTarget,
                DirectCallNode callNode,
                Object[] arguments);

        @Specialization
        static Object doCall(
                VirtualFrame frame,
                Node inliningTarget,
                DirectCallNode callNode,
                Object[] arguments,
                @Cached InlinedConditionProfile isNullFrameProfile,
                @Cached ProtosExperimentalCallContext callContext) {
            if (isNullFrameProfile.profile(inliningTarget, frame == null)) {
                callContext.enterIndirect(arguments);
                try {
                    return callNode.call(arguments);
                } finally {
                    callContext.exitIndirect(arguments);
                }
            }
            callContext.prepareCall(
                    frame, arguments, (RootCallTarget) callNode.getCallTarget());
            return callNode.call(arguments);
        }
    }
}

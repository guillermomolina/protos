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

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * I115-H4-B EXPERIMENTAL (GraalPy ExecutionContext.CallContext): the
 * caller-side preparation of a GraalPy unary frame-argument layout
 * ({@link ProtosFrameArguments#experimentalGraalPyUnaryCall}) before the
 * direct call of {@link ProtosExperimentalCallDispatchers}.
 *
 * <p>{@link #prepareCall} installs the caller reference (the caller's own
 * frame arguments) and the caller's experimental exceptional state. The
 * indirect pair, used when no caller frame exists, installs an absent caller
 * and exceptional state and clears the caller reference again after the
 * call. No ProtosActivation is built, no Object.call, ReturnHome, Task or
 * Prelude is consulted or prepared, no suspension route exists, and neither
 * transferred exceptions nor results are translated.
 */
final class ProtosExperimentalCallContext extends Node {

    @NeverDefault
    static ProtosExperimentalCallContext create() {
        return new ProtosExperimentalCallContext();
    }

    void prepareCall(VirtualFrame frame, Object[] arguments, RootCallTarget target) {
        assert target.getRootNode() instanceof ProtosBytecodeRootNode
                : "experimental call target is not a Protos Bytecode root";
        Object[] callerArguments = frame.getArguments();
        ProtosFrameArguments.setExperimentalCaller(arguments, callerArguments);
        ProtosFrameArguments.setExperimentalExceptionState(
                arguments,
                ProtosFrameArguments.isExperimentalGraalPyUnaryCall(callerArguments)
                        ? ProtosFrameArguments.experimentalExceptionState(callerArguments)
                        : null);
    }

    void enterIndirect(Object[] arguments) {
        ProtosFrameArguments.setExperimentalCaller(arguments, null);
        ProtosFrameArguments.setExperimentalExceptionState(arguments, null);
    }

    void exitIndirect(Object[] arguments) {
        ProtosFrameArguments.setExperimentalCaller(arguments, null);
    }
}

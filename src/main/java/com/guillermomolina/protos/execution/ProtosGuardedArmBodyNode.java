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
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.Objects;

/** D092 guard gate executed inside the already-bound synthetic arm Closure. */
final class ProtosGuardedArmBodyNode extends ProtosExpressionNode {
    @Child private ProtosExpressionNode guardNode;
    @Child private ProtosExpressionNode bodyNode;

    ProtosGuardedArmBodyNode(
            SourceSpan span,
            ProtosExpressionNode guardNode,
            ProtosExpressionNode bodyNode) {
        super(span);
        this.guardNode = Objects.requireNonNull(guardNode, "guardNode");
        this.bodyNode = Objects.requireNonNull(bodyNode, "bodyNode");
    }

    @Override
    protected Object executeDirect(VirtualFrame frame) {
        Object guardResult = guardNode.execute(frame);
        if (guardResult == ProtosBooleanValue.TRUE) {
            return bodyNode.execute(frame);
        }
        if (guardResult == ProtosBooleanValue.FALSE) {
            return ProtosMatchNode.guardedArmRejectedSentinel();
        }
        ProtosActivation activation = ProtosFrameArguments.activation(frame);
        throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}

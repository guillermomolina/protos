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
import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.List;
import java.util.Objects;

public final class ProtosCallNode extends ProtosExpressionNode {
    @Child private ProtosExpressionNode receiverNode;
    @Child private ProtosArgumentVectorNode argumentsNode;

    public ProtosCallNode(SourceSpan span, ProtosExpressionNode receiverNode, ProtosArgumentVectorNode argumentsNode) {
        super(span);
        this.receiverNode = Objects.requireNonNull(receiverNode, "receiverNode");
        this.argumentsNode = Objects.requireNonNull(argumentsNode, "argumentsNode");
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object receiver = receiverNode.execute(frame);
        @SuppressWarnings("unchecked")
        List<?> supplied = (List<?>) argumentsNode.execute(frame);
        ProtosActivation caller = ProtosFrameArguments.activation(frame);
        return ProtosInvocation.invoke(receiver, supplied, caller);
    }
}

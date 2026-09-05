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

import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import java.util.Objects;

public final class ProtosMemberCreateNode extends ProtosExpressionNode {
    private final String name;
    @Child private ProtosExpressionNode targetNode;
    @Child private ProtosExpressionNode valueNode;

    public ProtosMemberCreateNode(
            SourceSpan span,
            ProtosExpressionNode targetNode,
            String name,
            ProtosExpressionNode valueNode) {
        super(span);
        this.targetNode = Objects.requireNonNull(targetNode, "targetNode");
        this.name = Objects.requireNonNull(name, "name");
        this.valueNode = Objects.requireNonNull(valueNode, "valueNode");
    }

    @Override
    protected Object executeDirect(VirtualFrame frame) {
        Object targetValue = targetNode.execute(frame);
        if (!(targetValue instanceof ProtosObjectValue target)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(ProtosFrameArguments.activation(frame)));
        }

        Object value = valueNode.execute(frame);
        try {
            target.createLocalSlot(name, value);
        } catch (IllegalStateException invalidMutation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(ProtosFrameArguments.activation(frame)));
        }
        return value;
    }
}

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
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Executes D136 Map construction without introducing a guest-visible temporary binding. */
public final class ProtosMapConstructionNode extends ProtosExpressionNode {
    @Child private ProtosExpressionNode factoryNode;
    @Children private final ProtosExpressionNode[] keyNodes;
    @Children private final ProtosExpressionNode[] valueNodes;

    public ProtosMapConstructionNode(
            SourceSpan span,
            ProtosExpressionNode factoryNode,
            ProtosExpressionNode[] keyNodes,
            ProtosExpressionNode[] valueNodes) {
        super(span);
        this.factoryNode = Objects.requireNonNull(factoryNode, "factoryNode");
        Objects.requireNonNull(keyNodes, "keyNodes");
        Objects.requireNonNull(valueNodes, "valueNodes");
        if (keyNodes.length != valueNodes.length) {
            throw new IllegalArgumentException("Map construction key/value arity mismatch");
        }
        this.keyNodes = keyNodes.clone();
        this.valueNodes = valueNodes.clone();
        if (Arrays.stream(this.keyNodes).anyMatch(Objects::isNull)
                || Arrays.stream(this.valueNodes).anyMatch(Objects::isNull)) {
            throw new NullPointerException("Map construction entries contain null");
        }
    }

    @ExplodeLoop
    @Override
    protected Object executeDirect(VirtualFrame frame) {
        CompilerAsserts.compilationConstant(keyNodes.length);
        ProtosActivation caller = ProtosFrameArguments.activation(frame);

        Object factory = factoryNode.execute(frame);
        Object result = ProtosInvocation.invoke(factory, List.of(), caller);

        for (int index = 0; index < keyNodes.length; index++) {
            Object key = keyNodes[index].execute(frame);
            Object value = valueNodes[index].execute(frame);
            ProtosInvocation.invokeMessage(
                    result,
                    "atPut",
                    List.of(key, value),
                    caller);
        }
        return result;
    }
}

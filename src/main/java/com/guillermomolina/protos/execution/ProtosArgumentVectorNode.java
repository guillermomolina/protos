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

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Children;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProtosArgumentVectorNode extends ProtosExpressionNode {
    @Children private final ProtosExpressionNode[] expressions;
    private final boolean[] spreads;

    public ProtosArgumentVectorNode(
            SourceSpan span,
            List<ProtosArgumentItem> items) {
        super(span);
        Objects.requireNonNull(items, "items");
        expressions = new ProtosExpressionNode[items.size()];
        spreads = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ProtosArgumentItem item =
                    Objects.requireNonNull(items.get(i), "argument item");
            expressions[i] = item.expression();
            spreads[i] = item.spread();
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        ArrayList<Object> supplied = new ArrayList<>();
        for (int i = 0; i < expressions.length; i++) {
            Object value = expressions[i].execute(frame);
            if (!spreads[i]) {
                supplied.add(value);
                continue;
            }

            if (!(value instanceof ProtosArrayValue array)) {
                throw new ProtosSignalException(ProtosCoreErrors.newError());
            }
            supplied.addAll(array.indexedSnapshot());
        }
        return List.copyOf(supplied);
    }
}

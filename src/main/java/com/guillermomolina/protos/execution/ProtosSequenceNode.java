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

import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.Arrays;
import java.util.Objects;

public final class ProtosSequenceNode extends ProtosExpressionNode {
    @Children
    private final ProtosExpressionNode[] expressions;

    public ProtosSequenceNode(SourceSpan span, ProtosExpressionNode[] expressions) {
        super(span);
        Objects.requireNonNull(expressions, "expressions");
        if (expressions.length == 0) {
            throw new IllegalArgumentException("A ProtosSequenceNode requires at least one expression");
        }
        this.expressions = expressions.clone();
        if (Arrays.stream(this.expressions).anyMatch(Objects::isNull)) {
            throw new NullPointerException("expressions contains null");
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object result = null;
        for (ProtosExpressionNode expression : expressions) {
            result = expression.execute(frame);
        }
        return result;
    }
}

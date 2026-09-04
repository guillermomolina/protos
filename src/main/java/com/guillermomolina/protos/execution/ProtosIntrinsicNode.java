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

import com.guillermomolina.protos.runtime.ProtosExecutionContext;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.Objects;

public final class ProtosIntrinsicNode extends ProtosExpressionNode {
    private final CanonicalIntrinsic.Kind kind;

    public ProtosIntrinsicNode(SourceSpan span, CanonicalIntrinsic.Kind kind) {
        super(span);
        this.kind = Objects.requireNonNull(kind, "kind");
        if (kind == CanonicalIntrinsic.Kind.ARGS) {
            throw new IllegalArgumentException("args requires invocation-argument materialization");
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        ProtosExecutionContext executionContext =
                ProtosFrameArguments.executionContext(frame);
        return switch (kind) {
            case THIS -> executionContext.receiver();
            case CONTEXT -> executionContext.context();
            case ARGS -> throw new AssertionError("ARGS is rejected by the constructor");
        };
    }
}

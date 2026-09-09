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
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Objects;

public abstract class ProtosExpressionNode extends Node {
    private final SourceSpan span;

    protected ProtosExpressionNode(SourceSpan span) {
        this.span = Objects.requireNonNull(span, "span");
    }

    public final SourceSpan span() {
        return span;
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public final SourceSection getSourceSection() {
        RootNode rootNode = getRootNode();
        if (!(rootNode instanceof ProtosRootNode protosRootNode)) {
            return null;
        }
        return protosRootNode.sourceSectionFor(span);
    }

    public final Object execute(VirtualFrame frame) {
        if (frame == null) {
            return executeDirect(null);
        }

        Object[] arguments = frame.getArguments();
        if (arguments.length > 0
                && arguments[0] instanceof ProtosActivation activation) {
            ProtosTask task = activation.task().orElse(null);
            if (task != null && task.evaluatorContinuation().segmentActive()) {
                CompilerDirectives.transferToInterpreter();
                return ProtosEvaluatorBridge.execute(this, frame);
            }
        }

        return executeDirect(frame);
    }

    protected abstract Object executeDirect(VirtualFrame frame);
}

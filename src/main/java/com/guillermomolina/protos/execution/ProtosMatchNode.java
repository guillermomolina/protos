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
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import java.util.List;
import java.util.Objects;

/** Initial production execution node for the already-ratified matching protocol. */
public final class ProtosMatchNode extends ProtosExpressionNode {
    @Child private ProtosExpressionNode subjectNode;
    @Children private final ArmNode[] arms;

    public ProtosMatchNode(
            SourceSpan span,
            ProtosExpressionNode subjectNode,
            ArmNode[] arms) {
        super(span);
        this.subjectNode = Objects.requireNonNull(subjectNode, "subjectNode");
        Objects.requireNonNull(arms, "arms");
        if (arms.length == 0) {
            throw new IllegalArgumentException("match requires at least one arm");
        }
        this.arms = arms.clone();
        for (ArmNode arm : this.arms) {
            Objects.requireNonNull(arm, "arms contains null");
        }
    }

    @ExplodeLoop
    @Override
    protected Object executeDirect(VirtualFrame frame) {
        CompilerAsserts.compilationConstant(arms.length);
        ProtosActivation activation = ProtosFrameArguments.activation(frame);
        Object subject = subjectNode.execute(frame);

        for (ArmNode arm : arms) {
            List<Object> captures = arm.attempt(frame, subject, activation);
            if (captures == null) {
                continue;
            }

            Object body = arm.bodyClosure(frame);
            return ProtosInvocation.invoke(body, captures, activation);
        }

        throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }

    public static final class ArmNode extends Node {
        enum Kind {
            VALUE,
            BINDER,
            WILDCARD
        }

        private final Kind kind;
        @Child private ProtosExpressionNode matcherNode;
        @Child private ProtosExpressionNode bodyClosureNode;

        ArmNode(
                Kind kind,
                ProtosExpressionNode matcherNode,
                ProtosExpressionNode bodyClosureNode) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.matcherNode = matcherNode;
            this.bodyClosureNode =
                    Objects.requireNonNull(bodyClosureNode, "bodyClosureNode");

            if ((kind == Kind.VALUE) != (matcherNode != null)) {
                throw new IllegalArgumentException(
                        "only VALUE match arms own a matcher expression node");
            }
        }

        List<Object> attempt(
                VirtualFrame frame,
                Object subject,
                ProtosActivation activation) {
            return switch (kind) {
                case WILDCARD -> List.of();
                case BINDER -> List.of(subject);
                case VALUE -> consumeMatcherOutcome(
                        ProtosInvocation.invokeMessage(
                                matcherNode.execute(frame),
                                "match",
                                List.of(subject),
                                activation),
                        activation);
            };
        }

        Object bodyClosure(VirtualFrame frame) {
            return bodyClosureNode.execute(frame);
        }

        private static List<Object> consumeMatcherOutcome(
                Object outcome,
                ProtosActivation activation) {
            if (outcome == ProtosBooleanValue.FALSE) {
                return null;
            }
            if (outcome == ProtosBooleanValue.TRUE) {
                return List.of();
            }
            if (outcome instanceof ProtosArrayValue captures) {
                List<Object> snapshot = captures.indexedSnapshot();
                if (!snapshot.isEmpty()) {
                    return snapshot;
                }
            }

            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
    }
}

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Production AST execution node for the ratified matching protocol. */
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

    abstract static class PatternNode extends Node {
        abstract List<Object> attempt(
                VirtualFrame frame,
                Object subject,
                ProtosActivation activation);
    }

    static final class WildcardPatternNode extends PatternNode {
        @Override
        List<Object> attempt(
                VirtualFrame frame,
                Object subject,
                ProtosActivation activation) {
            return List.of();
        }
    }

    static final class BinderPatternNode extends PatternNode {
        @Override
        List<Object> attempt(
                VirtualFrame frame,
                Object subject,
                ProtosActivation activation) {
            return List.of(subject);
        }
    }

    static final class ValuePatternNode extends PatternNode {
        @Child private ProtosExpressionNode matcherNode;

        ValuePatternNode(ProtosExpressionNode matcherNode) {
            this.matcherNode = Objects.requireNonNull(matcherNode, "matcherNode");
        }

        @Override
        List<Object> attempt(
                VirtualFrame frame,
                Object subject,
                ProtosActivation activation) {
            return consumeMatcherOutcome(
                    ProtosInvocation.invokeMessage(
                            matcherNode.execute(frame),
                            "match",
                            List.of(subject),
                            activation),
                    activation);
        }
    }

    static final class AliasPatternNode extends PatternNode {
        @Child private PatternNode nested;

        AliasPatternNode(PatternNode nested) {
            this.nested = Objects.requireNonNull(nested, "nested");
        }

        @Override
        List<Object> attempt(
                VirtualFrame frame,
                Object subject,
                ProtosActivation activation) {
            List<Object> nestedCaptures = nested.attempt(frame, subject, activation);
            if (nestedCaptures == null) {
                return null;
            }

            ArrayList<Object> captures =
                    new ArrayList<>(nestedCaptures.size() + 1);
            captures.add(subject);
            captures.addAll(nestedCaptures);
            return List.copyOf(captures);
        }
    }

    static final class ArrayPatternNode extends PatternNode {
        @Children private final PatternNode[] prefix;
        private final boolean hasRemainder;
        @Child private PatternNode remainder;
        @Children private final PatternNode[] suffix;

        ArrayPatternNode(
                PatternNode[] prefix,
                boolean hasRemainder,
                PatternNode remainder,
                PatternNode[] suffix) {
            Objects.requireNonNull(prefix, "prefix");
            Objects.requireNonNull(suffix, "suffix");
            this.prefix = prefix.clone();
            this.hasRemainder = hasRemainder;
            this.remainder = remainder;
            this.suffix = suffix.clone();

            for (PatternNode item : this.prefix) {
                Objects.requireNonNull(item, "prefix contains null");
            }
            for (PatternNode item : this.suffix) {
                Objects.requireNonNull(item, "suffix contains null");
            }
            if (!hasRemainder && remainder != null) {
                throw new IllegalArgumentException(
                        "Array pattern without remainder cannot own a remainder child");
            }
        }

        @ExplodeLoop
        @Override
        List<Object> attempt(
                VirtualFrame frame,
                Object subject,
                ProtosActivation activation) {
            CompilerAsserts.compilationConstant(prefix.length);
            CompilerAsserts.compilationConstant(suffix.length);

            if (!(subject instanceof ProtosArrayValue array)) {
                return null;
            }

            List<Object> observed = array.indexedSnapshot();
            int fixedCount = prefix.length + suffix.length;
            if (hasRemainder) {
                if (observed.size() < fixedCount) {
                    return null;
                }
            } else if (observed.size() != fixedCount) {
                return null;
            }

            ArrayList<Object> captures = new ArrayList<>();

            for (int index = 0; index < prefix.length; index++) {
                List<Object> child =
                        prefix[index].attempt(
                                frame,
                                observed.get(index),
                                activation);
                if (child == null) {
                    return null;
                }
                captures.addAll(child);
            }

            if (hasRemainder && remainder != null) {
                int remainderEnd = observed.size() - suffix.length;
                ProtosArrayValue remainderValue =
                        activation.prelude()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "Array matching requires an owning Core prelude"))
                                .newFrozenArray(
                                        observed.subList(prefix.length, remainderEnd));
                List<Object> child =
                        remainder.attempt(frame, remainderValue, activation);
                if (child == null) {
                    return null;
                }
                captures.addAll(child);
            }

            int suffixStart = observed.size() - suffix.length;
            for (int index = 0; index < suffix.length; index++) {
                List<Object> child =
                        suffix[index].attempt(
                                frame,
                                observed.get(suffixStart + index),
                                activation);
                if (child == null) {
                    return null;
                }
                captures.addAll(child);
            }

            return List.copyOf(captures);
        }
    }

    public static final class ArmNode extends Node {
        @Child private PatternNode patternNode;
        @Child private ProtosExpressionNode bodyClosureNode;

        ArmNode(
                PatternNode patternNode,
                ProtosExpressionNode bodyClosureNode) {
            this.patternNode = Objects.requireNonNull(patternNode, "patternNode");
            this.bodyClosureNode =
                    Objects.requireNonNull(bodyClosureNode, "bodyClosureNode");
        }

        List<Object> attempt(
                VirtualFrame frame,
                Object subject,
                ProtosActivation activation) {
            return patternNode.attempt(frame, subject, activation);
        }

        Object bodyClosure(VirtualFrame frame) {
            return bodyClosureNode.execute(frame);
        }
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

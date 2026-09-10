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
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Objects;

@GenerateWrapper
@ExportLibrary(NodeLibrary.class)
public abstract class ProtosExpressionNode extends Node implements InstrumentableNode {
    private static final byte TAG_STATEMENT = 1;
    private static final byte TAG_CALL = 1 << 1;
    private static final int MAX_WRAPPER_DEPTH = 64;

    private final SourceSpan span;
    private byte instrumentationTags;

    protected ProtosExpressionNode(SourceSpan span) {
        this.span = Objects.requireNonNull(span, "span");
    }

    protected ProtosExpressionNode(ProtosExpressionNode source) {
        Objects.requireNonNull(source, "source");
        this.span = source.span;
        this.instrumentationTags = source.instrumentationTags;
    }

    public final SourceSpan span() {
        return span;
    }

    final ProtosExpressionNode markStatementTagForLowering() {
        requireUnadoptedTagMutation();
        instrumentationTags |= TAG_STATEMENT;
        return this;
    }

    final ProtosExpressionNode markCallTagForLowering() {
        requireUnadoptedTagMutation();
        instrumentationTags |= TAG_CALL;
        return this;
    }

    private void requireUnadoptedTagMutation() {
        if (getParent() != null) {
            throw new IllegalStateException("instrumentation tags are fixed before AST adoption");
        }
    }

    @Override
    public final boolean isInstrumentable() {
        return instrumentationTags != 0 && getSourceSection() != null;
    }

    @Override
    public final boolean hasTag(Class<? extends Tag> tag) {
        if (tag == StandardTags.StatementTag.class) {
            return (instrumentationTags & TAG_STATEMENT) != 0;
        }
        if (tag == StandardTags.CallTag.class) {
            return (instrumentationTags & TAG_CALL) != 0;
        }
        return false;
    }

    @Override
    public final WrapperNode createWrapper(ProbeNode probe) {
        return new ProtosExpressionNodeWrapper(this, this, probe);
    }

    @GenerateWrapper.OutgoingConverter
    final Object convertOutgoingForInstrumentation(Object ignored) {
        return null;
    }

    @GenerateWrapper.IncomingConverter
    final Object rejectIncomingInstrumentationValue(Object ignored) {
        throw new IllegalStateException(
                "instrumentation value injection is unavailable before I026-D interop ownership");
    }

    static ProtosExpressionNode replaySiteIdentity(ProtosExpressionNode node) {
        Objects.requireNonNull(node, "node");
        Node current = node;
        int depth = 0;
        while (current instanceof WrapperNode wrapper) {
            if (++depth > MAX_WRAPPER_DEPTH) {
                throw new IllegalStateException("instrumentation wrapper chain exceeds safe depth");
            }
            Node delegate = wrapper.getDelegateNode();
            if (delegate == current) {
                throw new IllegalStateException("instrumentation wrapper delegates to itself");
            }
            if (!(delegate instanceof ProtosExpressionNode expression)) {
                throw new IllegalStateException(
                        "instrumentation wrapper does not delegate to a Protos execution node");
            }
            current = expression;
        }
        return (ProtosExpressionNode) current;
    }

    @ExportMessage
    final boolean hasScope(Frame frame) {
        return ProtosFrameArguments.hasActivation(frame);
    }

    @ExportMessage
    final Object getScope(
            Frame frame,
            @SuppressWarnings("unused") boolean nodeEnter)
            throws UnsupportedMessageException {
        if (!hasScope(frame)) {
            throw UnsupportedMessageException.create();
        }
        return new ProtosDebuggerScope(ProtosFrameArguments.activation(frame));
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

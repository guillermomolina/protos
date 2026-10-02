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
 * the specific language governing rights and limitations under the LICENSE.
 */

package com.guillermomolina.protos.execution;


import com.guillermomolina.protos.runtime.ProtosActivation;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * PLAT015 NodeLibrary bridge for Bytecode DSL instrumentation locations.
 *
 * <p>The Bytecode interpreter's own locals are implementation state, not Protos
 * lexical bindings. Tooling therefore projects the exact activation from frame
 * argument zero through the same bounded {@link ProtosDebuggerScope} used by the
 * AST backend.</p>
 *
 * <p>PLAT044 B′: inside an inline literal callback region frame argument zero is
 * the enclosing activation, while the semantic current activation is the
 * callback's own, held in the region's {@link
 * CanonicalToBytecodeLowerer#INLINE_CALLBACK_ACTIVATION_LOCAL}. A location whose
 * bytecode index lies in such a region projects that activation instead; no
 * additional frame is fabricated.</p>
 */
@ExportLibrary(value = NodeLibrary.class, receiverType = TagTreeNode.class)
final class ProtosBytecodeTagTreeNodeExports {
    private ProtosBytecodeTagTreeNodeExports() {}

    @ExportMessage
    static boolean hasScope(
            TagTreeNode node,
            Frame frame) {
        if (frame == null) {
            return false;
        }
        return inlineCallbackActivation(node, frame) != null
                || ProtosFrameArguments.hasActivation(frame);
    }

    @ExportMessage
    static Object getScope(
            TagTreeNode node,
            Frame frame,
            @SuppressWarnings("unused") boolean nodeEnter)
            throws UnsupportedMessageException {
        if (!hasScope(node, frame)) {
            throw UnsupportedMessageException.create();
        }
        ProtosActivation inline = inlineCallbackActivation(node, frame);
        if (inline != null) {
            return debuggerScope(inline);
        }
        ProtosActivation activation = ProtosFrameArguments.activation(frame);
        projectFrameNativeBindings(node, frame, activation);
        return debuggerScope(activation);
    }

    /**
     * PERF025 frame-materialization slice: a root lowered without a persistent
     * frame authority keeps its current bindings only in its live frame while
     * its execution context is unobserved. Before projecting such an
     * activation, tooling installs on it, while the queried frame is live, the
     * same escape-safe materialized-frame authority the root would install on
     * an observed activation, so the scope reads the exact bindings through the
     * activation as for every other root.
     */
    private static void projectFrameNativeBindings(
            TagTreeNode node,
            Frame frame,
            ProtosActivation activation) {
        if (node == null || !activation.hasUnobservedFrameNativeExecutionContextForRuntime()) {
            return;
        }
        BytecodeNode bytecode = node.getBytecodeNode();
        if (bytecode.getBytecodeRootNode() instanceof ProtosSemanticBytecodeRootNode root) {
            root.installFrameNativeAuthorityForTooling(activation, bytecode, frame);
        }
    }

    /**
     * The activation of the innermost inline callback region live at {@code
     * node}'s location, or {@code null} outside every such region. Block
     * scoping makes the region local visible only inside its own region; the
     * last live match is the innermost one. Only reached from tooling scope
     * queries, never from guest execution.
     */
    private static ProtosActivation inlineCallbackActivation(TagTreeNode node, Frame frame) {
        if (node == null) {
            return null;
        }
        BytecodeNode bytecode = node.getBytecodeNode();
        int bytecodeIndex = node.getEnterBytecodeIndex();
        Object[] names = bytecode.getLocalNames(bytecodeIndex);
        for (int offset = names.length - 1; offset >= 0; offset--) {
            if (CanonicalToBytecodeLowerer.INLINE_CALLBACK_ACTIVATION_LOCAL.equals(names[offset])
                    && bytecode.getLocalValue(bytecodeIndex, frame, offset)
                            instanceof ProtosActivation activation) {
                return activation;
            }
        }
        return null;
    }

    @TruffleBoundary
    private static ProtosDebuggerScope debuggerScope(
            ProtosActivation activation) {
        return new ProtosDebuggerScope(activation);
    }
}

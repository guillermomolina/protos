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


import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PreparedInlineLiteralCall;
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
 * callback's own, carried by the region's {@link
 * CanonicalToBytecodeLowerer#INLINE_CALLBACK_CALL_LOCAL}. A location whose
 * bytecode index lies in such a region projects that activation instead,
 * materializing it through the carrier only when a scope is actually
 * requested, so the guest and every later observer share that one instance.
 * For a frame-native callback that first materialization moves the
 * callback's PRESENT block-local bindings to a durable authority on the
 * activation before it is projected; no additional frame is fabricated.</p>
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
        return inlineCallbackCall(node, frame) != null
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
        PreparedInlineLiteralCall inline = inlineCallbackCall(node, frame);
        if (inline != null) {
            java.util.Map<String, Object> frameBindings =
                    inlineCallbackFrameBindings(node, frame);
            ProtosActivation activation =
                    frameBindings == null
                            ? inline.activation()
                            : ProtosInlineCallbackFrameBindings.durableActivationForTooling(
                                    inline, frameBindings);
            return debuggerScope(activation);
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
     * PERF025 inline-callback lexical slice: returns the PRESENT block-local
     * bindings of a frame-native callback region, in layout order, or
     * {@code null} when this inline callback is using the unchanged
     * activation/context authority path. They seed the invocation's durable
     * authority when tooling is its first observer; once the bindings are
     * durable they are ignored.
     *
     * <p>The prefixed Bytecode locals exist only for the statically admitted
     * frame-native callback path. Their prefix is implementation metadata;
     * the guest/debugger name is the suffix. A cleared local is ABSENT and is
     * therefore omitted. Guest null is {@code ProtosNullValue}, never host
     * {@code null}, so host null remains an unambiguous cleared-local marker.
     *
     * <p>No Frame is retained after the scope object has been constructed.
     */
    private static java.util.Map<String, Object> inlineCallbackFrameBindings(
            TagTreeNode node,
            Frame frame) {
        if (node == null) {
            return null;
        }

        BytecodeNode bytecode = node.getBytecodeNode();
        int bytecodeIndex = node.getEnterBytecodeIndex();
        Object[] names = bytecode.getLocalNames(bytecodeIndex);

        java.util.LinkedHashMap<String, Object> bindings = null;
        boolean frameNativeRegion = false;

        for (int offset = 0; offset < names.length; offset++) {
            Object rawName = names[offset];
            if (!(rawName instanceof String localName)
                    || !localName.startsWith(
                            CanonicalToBytecodeLowerer
                                    .INLINE_CALLBACK_BINDING_LOCAL_PREFIX)) {
                continue;
            }

            frameNativeRegion = true;
            Object value =
                    bytecode.getLocalValue(
                            bytecodeIndex,
                            frame,
                            offset);
            if (value == null) {
                continue;
            }

            if (bindings == null) {
                bindings = new java.util.LinkedHashMap<>();
            }
            bindings.put(
                    localName.substring(
                            CanonicalToBytecodeLowerer
                                    .INLINE_CALLBACK_BINDING_LOCAL_PREFIX
                                    .length()),
                    value);
        }

        if (!frameNativeRegion) {
            return null;
        }
        if (bindings == null) {
            return java.util.Map.of();
        }
        return java.util.Collections.unmodifiableMap(bindings);
    }

    /**
     * The invocation carrier of the innermost inline callback region live at
     * {@code node}'s location, or {@code null} outside every such region.
     * Block scoping makes the region local visible only inside its own region;
     * the last live match is the innermost one. Only reached from tooling
     * scope queries, never from guest execution.
     */
    private static PreparedInlineLiteralCall inlineCallbackCall(TagTreeNode node, Frame frame) {
        if (node == null) {
            return null;
        }
        BytecodeNode bytecode = node.getBytecodeNode();
        int bytecodeIndex = node.getEnterBytecodeIndex();
        Object[] names = bytecode.getLocalNames(bytecodeIndex);
        for (int offset = names.length - 1; offset >= 0; offset--) {
            if (CanonicalToBytecodeLowerer.INLINE_CALLBACK_CALL_LOCAL.equals(names[offset])
                    && bytecode.getLocalValue(bytecodeIndex, frame, offset)
                            instanceof PreparedInlineLiteralCall call) {
                return call;
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

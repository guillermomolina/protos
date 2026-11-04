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


import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
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
 *
 * <p>I102 (PLAT060-B5): every ordinary Closure frame carries the same
 * argument header, so the frame layout says nothing about debugging. PLAT067-B:
 * every Context uses one proof-optimized lowering; scopes are projected only
 * for executable locations that survived it, and no frame is fabricated for an
 * execution a valid optimization eliminated.</p>
 */
@ExportLibrary(value = NodeLibrary.class, receiverType = TagTreeNode.class)
final class ProtosBytecodeTagTreeNodeExports {
    private ProtosBytecodeTagTreeNodeExports() {}

    @ExportMessage
    static boolean hasScope(
            TagTreeNode node,
            Frame frame) {
        if (frame == null || !inSemanticActivationRoot(node)) {
            return false;
        }
        return ProtosFrameArguments.hasActivation(frame);
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
        ProtosActivation activation = ProtosFrameArguments.activation(frame);
        java.util.Map<String, Object> ownInlineBindings =
                normalInlineCallbackBindings(node, frame.materialize());
        if (ownInlineBindings != null) {
            // I095-B6: virtual lexical scope; no carrier or guest activation.
            return new ProtosDebuggerScope(activation, ownInlineBindings);
        }
        java.util.Map<String, Object> rootBindings = toolingBindings(node, frame);
        if (rootBindings != null) {
            /*
             * PLAT067-B / I115: a Closure root's LOCAL and CELL bindings are
             * never part of its Context; the live established bindings
             * overlay that Context read-only.
             */
            return new ProtosDebuggerScope(activation, rootBindings);
        }
        return debuggerScope(activation);
    }

    private static java.util.Map<String, Object> toolingBindings(TagTreeNode node, Frame frame) {
        if (node == null) {
            return null;
        }
        BytecodeNode bytecode = node.getBytecodeNode();
        return bytecode.getBytecodeRootNode() instanceof ProtosBytecodeRootNode root
                ? root.toolingBindingsOrNull(
                        bytecode, node.getEnterBytecodeIndex(), frame.materialize())
                : null;
    }

    /*
     * I111-B: an auxiliary structured/C-prime root of the same generated
     * interpreter emits no tags, and even a tag location reached there must
     * never offer a guest scope. Tooling-only.
     */
    private static boolean inSemanticActivationRoot(TagTreeNode node) {
        return node == null
                || !(node.getBytecodeNode().getBytecodeRootNode()
                                instanceof ProtosBytecodeRootNode root)
                || root.isSemanticActivationRoot();
    }

    /**
     * I095-B6 tooling-only projection for physically inlined callbacks.
     * BytecodeLocal names are implementation details and are never visible to
     * the guest; the unprefixed source names identify the callback's bindings.
     * An uninitialized/cleared local remains ABSENT rather than host null.
     * No Frame is retained in the resulting debugger view.
     */
    @TruffleBoundary
    private static java.util.Map<String, Object> normalInlineCallbackBindings(
            TagTreeNode node, MaterializedFrame frame) {
        if (node == null) {
            return null;
        }
        BytecodeNode bytecode = node.getBytecodeNode();
        int index = node.getEnterBytecodeIndex();
        Object[] names = bytecode.getLocalNames(index);
        String prefix = CanonicalToBytecodeLowerer
                .NORMAL_INLINE_CALLBACK_BINDING_LOCAL_PREFIX;
        boolean inNormalInlineCallback = false;
        java.util.LinkedHashMap<String, Object> bindings =
                new java.util.LinkedHashMap<>();
        for (int offset = 0; offset < names.length; offset++) {
            if (!(names[offset] instanceof String name)
                    || !name.startsWith(prefix)) {
                continue;
            }
            inNormalInlineCallback = true;
            Object value = ProtosNumericValueSupport.guestValue(
                    bytecode.getLocalValue(index, frame, offset));
            if (value != null) {
                bindings.put(name.substring(prefix.length()), value);
            }
        }
        return inNormalInlineCallback
                ? java.util.Collections.unmodifiableMap(bindings)
                : null;
    }

    @TruffleBoundary
    private static ProtosDebuggerScope debuggerScope(
            ProtosActivation activation) {
        return new ProtosDebuggerScope(activation);
    }

}

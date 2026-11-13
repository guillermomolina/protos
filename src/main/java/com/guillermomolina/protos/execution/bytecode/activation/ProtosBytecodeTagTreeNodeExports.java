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


import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * NodeLibrary bridge for Bytecode DSL instrumentation locations.
 *
 * <p>A physical Closure scope reads the exact activation's original frame-backed
 * lexical authority. MODULE roots retain their module context. Inline callbacks
 * contribute only temporary tooling snapshots of the locals owned by semantic
 * RootTag regions active at the suspended bytecode position. No frame, activation
 * or variable store is fabricated for an eliminated physical call.</p>
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
            boolean nodeEnter)
            throws UnsupportedMessageException {
        if (!hasScope(node, frame)) {
            throw UnsupportedMessageException.create();
        }
        ProtosActivation activation = ProtosFrameArguments.activation(frame);
        MaterializedFrame materialized = frame.materialize();
        int bytecodeIndex = node == null ? -1
                : nodeEnter ? node.getEnterBytecodeIndex() : node.getReturnBytecodeIndex();
        if (node != null) {
            BytecodeNode bytecode = node.getBytecodeNode();
            if (bytecode.getBytecodeRootNode() instanceof ProtosBytecodeRootNode root) {
                // MODULE roots have no Closure layout and keep their module authority.
                root.ensureToolingLexicalFrameView(
                        materialized, bytecode, bytecodeIndex, activation);
            }
        }
        java.util.Map<String, Object> ownInlineBindings =
                normalInlineCallbackBindings(node, materialized, bytecodeIndex);
        return new ProtosDebuggerScope(
                activation, ownInlineBindings == null ? java.util.Map.of() : ownInlineBindings);
    }

    /**
     * Resolve a live lexical creator only through an identical owned cell whose
     * canonical binding metadata proves immediate lexical ownership. Stack order
     * and the dynamic caller are not evidence of a lexical relationship.
     */
    @TruffleBoundary
    static ProtosActivation lexicalCreatorForTooling(ProtosActivation activation) {
        var closure = activation.invokedClosureOrNullForRuntime();
        if (closure == null || activation.capturedCellsForRuntime().length == 0) {
            return null;
        }
        // Observation must never prepare or rematerialize executable code.
        var plan = closure.preparedExecutionPlanOrNullForRuntime();
        if (plan == null) {
            return null;
        }
        var indices = plan.creatorCellIndicesForTooling();
        if (indices.isEmpty()) {
            return null;
        }
        var captured = activation.capturedCellsForRuntime();
        return com.oracle.truffle.api.Truffle.getRuntime().iterateFrames(instance -> {
            if (!(instance.getCallTarget() instanceof com.oracle.truffle.api.RootCallTarget target)) {
                return null;
            }
            Object source = target.getRootNode();
            Frame original = instance.getFrame(
                    com.oracle.truffle.api.frame.FrameInstance.FrameAccess.READ_ONLY);
            boolean resumed = source instanceof com.oracle.truffle.api.bytecode.ContinuationRootNode;
            if (source instanceof com.oracle.truffle.api.bytecode.ContinuationRootNode continuation) {
                source = continuation.getSourceRootNode();
                if (original.getArguments().length == 0
                        || !(original.getArguments()[0] instanceof Frame saved)) {
                    return null;
                }
                original = saved;
            }
            if (!(source instanceof ProtosBytecodeRootNode root) || !root.isSemanticActivationRoot()) {
                return null;
            }
            var layout = root.lexicalFrameLayoutOrNull();
            if (layout == null) {
                return null;
            }
            BytecodeNode bytecode = root.getBytecodeNode();
            int bytecodeIndex = bytecode.getBytecodeIndex(original);
            for (int local = 0; local < layout.size(); local++) {
                if (!layout.isCell(local)) {
                    continue;
                }
                // The tooling API accepts Frame and returns null for a cleared local.
                Object ownedCell = bytecode.getLocalValue(bytecodeIndex, original, layout.offset(local));
                for (int index : indices) {
                    if (index < captured.length && ownedCell == captured[index]) {
                        MaterializedFrame materialized = resumed ? original.materialize()
                                : instance.getFrame(
                                        com.oracle.truffle.api.frame.FrameInstance.FrameAccess.READ_WRITE)
                                        .materialize();
                        ProtosActivation creator = ProtosFrameArguments.activation(materialized);
                        root.ensureToolingLexicalFrameView(materialized, bytecode,
                                bytecode.getBytecodeIndex(materialized), creator);
                        return creator;
                    }
                }
            }
            return null;
        });
    }

    /*
     * Auxiliary structured/C-prime roots receive automatic physical tags but
     * do not represent semantic activations and never offer guest scopes.
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
            TagTreeNode node, MaterializedFrame frame, int index) {
        if (node == null) {
            return null;
        }
        BytecodeNode bytecode = node.getBytecodeNode();
        Object[] names = bytecode.getLocalNames(index);
        String prefix = CanonicalToBytecodeLowerer
                .NORMAL_INLINE_CALLBACK_BINDING_LOCAL_PREFIX;
        java.util.List<TagTree> regions = new java.util.ArrayList<>();
        collectSemanticRoots(bytecode.getTagTree(), regions);
        java.util.LinkedHashMap<String, Object> bindings = new java.util.LinkedHashMap<>();
        boolean active = false;
        // The first RootTag is the physical root. Later roots are callback regions,
        // in the same preorder that assigned their BytecodeLocal info during lowering.
        for (int region = 1; region < regions.size(); region++) {
            TagTree owner = regions.get(region);
            if (owner.getEnterBytecodeIndex() == owner.getReturnBytecodeIndex()
                    || index < owner.getEnterBytecodeIndex() || index > owner.getReturnBytecodeIndex()) {
                continue;
            }
            active = true;
            for (int offset = 0; offset < names.length; offset++) {
                if (names[offset] instanceof String name && name.startsWith(prefix)
                        && bytecode.getLocalInfo(index, offset) instanceof Integer ownerRegion
                        && ownerRegion == region) {
                    Object value = ProtosNumericValueSupport.guestValue(
                            bytecode.getLocalValue(index, frame, offset));
                    if (value != null) {
                        bindings.put(name.substring(prefix.length()), value);
                    }
                }
            }
        }
        return active ? java.util.Collections.unmodifiableMap(bindings) : null;
    }

    private static void collectSemanticRoots(TagTree tree, java.util.List<TagTree> roots) {
        if (tree == null) {
            return;
        }
        if (tree.hasTag(StandardTags.RootTag.class)) {
            roots.add(tree);
        }
        for (TagTree child : tree.getTreeChildren()) {
            collectSemanticRoots(child, roots);
        }
    }
}

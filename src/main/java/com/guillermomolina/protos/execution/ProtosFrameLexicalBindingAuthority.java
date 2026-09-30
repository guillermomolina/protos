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

import com.guillermomolina.protos.runtime.ProtosLexicalBindingAuthority;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * PLAT036 Candidate D, Slice 3 {@link ProtosLexicalBindingAuthority} backed by
 * one genuine {@code ROOT}/{@code CLOSURE} Bytecode root's own frame/local
 * layout, installed once by {@link
 * com.guillermomolina.protos.runtime.ProtosExecutionContextValue#installFrameLexicalBindingAuthority}
 * before any binding exists on that context.
 *
 * <p>Every name statically admitted to the direct-local layout (see
 * {@code CanonicalToBytecodeLowerer}), including Closure parameters since
 * I068 Slice 4, is backed by its own {@link LocalAccessor}; genuinely dynamic
 * names fall through to an ordinary insertion-ordered overflow map, coordinated by
 * this single authority instance rather than a second competing store. This
 * is the {@code DYNAMIC_OVERFLOW} shape PLAT036 anticipates: one authority
 * object, hybrid physical storage.
 *
 * <p>Presence is derived directly from {@link LocalAccessor#isCleared}: the
 * physical local always exists once the owning root's frame is created (Slice
 * 3 "no eager visibility from layout allocation"), but a name is only
 * semantically PRESENT once {@link #putBinding} has actually written to it,
 * matching {@code STATIC_LOCAL_EXISTS != SEMANTIC_BINDING_PRESENT}. Because
 * the stored value is always a genuine, non-null Protos value (host {@code
 * null} never crosses this boundary), a cleared local and a local holding a
 * PRESENT value are always distinguishable, giving {@code PRESENT(null) !=
 * ABSENT}.
 *
 * <p>The frame reference held here is the owning root's own frame, retained
 * (materialized by the installer) so this context's frame-backed bindings
 * remain observable even after that root's own activation returns, for the
 * case where the execution context itself escapes. I068 Slice 5 reuses this
 * same retained materialized authority for proven captured access: the child
 * root never owns or copies the outer frame, and no Truffle frame object is
 * stored in the semantic Closure value.
 *
 * <p>I075-D: the authority retains its stable declaring {@link
 * BytecodeRootNode}, never a {@link BytecodeNode}, and resolves the root's
 * current node for every frame-backed access. With boxing elimination the
 * cached node owns local-kind metadata, so a stale (for example uncached)
 * node writing the physical frame would leave the current node's metadata
 * incoherent.
 *
 * <p>PERF012: the name/ordinal layout itself is {@link
 * ProtosFrameLexicalLayout}, precomputed once per root at lowering time and
 * shared, by reference, across every per-invocation authority instance; only
 * the runtime frame/dynamic-overflow state below is per-invocation.
 */
final class ProtosFrameLexicalBindingAuthority implements ProtosLexicalBindingAuthority {
    private final ProtosFrameLexicalLayout frameBackedLayout;
    private final LocalRangeAccessor frameBackedLocals;
    private final BytecodeRootNode declaringRoot;
    private VirtualFrame frame;
    private final LinkedHashMap<String, Object> dynamicOverflow = new LinkedHashMap<>();
    private final LinkedHashSet<String> establishmentOrder = new LinkedHashSet<>();

    ProtosFrameLexicalBindingAuthority(
            ProtosFrameLexicalLayout frameBackedLayout,
            LocalRangeAccessor frameBackedLocals,
            BytecodeNode bytecodeNode,
            VirtualFrame frame) {
        this.frameBackedLayout =
                Objects.requireNonNull(frameBackedLayout, "frameBackedLayout");
        this.frameBackedLocals =
                Objects.requireNonNull(frameBackedLocals, "frameBackedLocals");
        this.declaringRoot =
                Objects.requireNonNull(bytecodeNode, "bytecodeNode").getBytecodeRootNode();
        this.frame = Objects.requireNonNull(frame, "frame");

        if (frameBackedLayout.length() != frameBackedLocals.getLength()) {
            throw new IllegalArgumentException(
                    "frame-backed binding-name count must match local range length");
        }
    }

    /**
     * The declaring root's current {@link BytecodeNode}. Bytecode DSL replaces
     * a root's node while it runs (for example on the uncached-to-cached
     * transition), and the cached node owns local-kind metadata that must stay
     * coherent with every physical local access, so an installation-time node
     * must never be retained. Each logical operation below reads this once and
     * uses that single node throughout.
     */
    private BytecodeNode currentBytecodeNode() {
        return declaringRoot.getBytecodeNode();
    }

    /**
     * I068 Slice 5 frame-native captured-read seam. The ordinal comes from the
     * same CanonicalLexicalScope declaration order used to create this
     * authority's LocalRangeAccessor layout. The expected name is checked as a
     * defensive guard against stale or mismatched lowering metadata.
     */
    boolean hasFrameBackedBindingAt(String expectedName, int ordinal) {
        return hasFrameBackedBindingAt(
                currentBytecodeNode(), expectedName, ordinal);
    }

    private boolean hasFrameBackedBindingAt(
            BytecodeNode bytecodeNode, String expectedName, int ordinal) {
        Objects.requireNonNull(expectedName, "expectedName");
        if (ordinal < 0 || ordinal >= frameBackedLayout.length()) {
            return false;
        }
        if (!frameBackedLayout.nameAt(ordinal).equals(expectedName)) {
            return false;
        }
        return !frameBackedLocals.isCleared(bytecodeNode, frame, ordinal);
    }

    Object readFrameBackedBindingAt(String expectedName, int ordinal) {
        BytecodeNode bytecodeNode = currentBytecodeNode();
        requirePresentFrameBackedBinding(bytecodeNode, expectedName, ordinal);
        return frameBackedLocals.getObject(bytecodeNode, frame, ordinal);
    }

    void assignFrameBackedBindingAt(
            String expectedName,
            int ordinal,
            Object value) {
        Objects.requireNonNull(value, "value");
        BytecodeNode bytecodeNode = currentBytecodeNode();
        requirePresentFrameBackedBinding(bytecodeNode, expectedName, ordinal);
        frameBackedLocals.setObject(bytecodeNode, frame, ordinal, value);
    }

    private void requirePresentFrameBackedBinding(
            BytecodeNode bytecodeNode, String expectedName, int ordinal) {
        if (!hasFrameBackedBindingAt(bytecodeNode, expectedName, ordinal)) {
            throw new IllegalStateException(
                    "captured frame-backed binding is absent or layout metadata mismatched: "
                            + expectedName
                            + "@"
                            + ordinal);
        }
    }

    @Override
    public void prepareForContextObservation() {
        frame = frame.materialize();
    }

    /**
     * PERF013 Slice B1 seam: exposes this authority's own retained frame for a
     * compile-time-proven {@link com.oracle.truffle.api.bytecode.MaterializedLocalAccessor}
     * captured read. Returns {@code null}, rather than materializing eagerly,
     * when this context's frame is not yet materialized: {@link
     * #prepareForContextObservation} remains the sole materialization point
     * (triggered by the existing capture/observation boundary), and a caller
     * observing {@code null} here falls back to the exact generic captured
     * path instead of inventing a second materialization trigger.
     */
    MaterializedFrame retainedMaterializedFrameForCapturedAccess() {
        return frame instanceof MaterializedFrame materializedFrame ? materializedFrame : null;
    }

    @Override
    public boolean containsBinding(String name) {
        Objects.requireNonNull(name, "name");
        Integer offset = frameBackedLayout.offsetOf(name);
        if (offset != null) {
            return !frameBackedLocals.isCleared(
                    currentBytecodeNode(), frame, offset);
        }
        return dynamicOverflow.containsKey(name);
    }

    @Override
    public Optional<Object> readBinding(String name) {
        Objects.requireNonNull(name, "name");
        Integer offset = frameBackedLayout.offsetOf(name);
        if (offset != null) {
            BytecodeNode bytecodeNode = currentBytecodeNode();
            if (frameBackedLocals.isCleared(
                    bytecodeNode, frame, offset)) {
                return Optional.empty();
            }
            return Optional.of(
                    frameBackedLocals.getObject(
                            bytecodeNode, frame, offset));
        }
        return dynamicOverflow.containsKey(name)
                ? Optional.of(dynamicOverflow.get(name))
                : Optional.empty();
    }

    @Override
    public Map<String, Object> bindingsSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        BytecodeNode bytecodeNode = currentBytecodeNode();
        for (String name : establishmentOrder) {
            Integer offset = frameBackedLayout.offsetOf(name);
            if (offset != null) {
                if (!frameBackedLocals.isCleared(
                        bytecodeNode, frame, offset)) {
                    snapshot.put(
                            name,
                            frameBackedLocals.getObject(
                                    bytecodeNode, frame, offset));
                }
            } else if (dynamicOverflow.containsKey(name)) {
                snapshot.put(name, dynamicOverflow.get(name));
            }
        }
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public void appendBindingsTo(
            java.util.ArrayList<String> names,
            java.util.ArrayList<Object> values) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(values, "values");

        BytecodeNode bytecodeNode = currentBytecodeNode();
        for (String name : establishmentOrder) {
            Integer offset = frameBackedLayout.offsetOf(name);
            if (offset != null) {
                if (!frameBackedLocals.isCleared(
                        bytecodeNode, frame, offset)) {
                    names.add(name);
                    values.add(
                            frameBackedLocals.getObject(
                                    bytecodeNode, frame, offset));
                }
            } else if (dynamicOverflow.containsKey(name)) {
                names.add(name);
                values.add(dynamicOverflow.get(name));
            }
        }
    }

    @Override
    public void putBinding(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        establishmentOrder.add(name);

        Integer offset = frameBackedLayout.offsetOf(name);
        if (offset != null) {
            frameBackedLocals.setObject(
                    currentBytecodeNode(), frame, offset, value);
            return;
        }

        dynamicOverflow.put(name, value);
    }

    @Override
    public Object removeBinding(String name) {
        Objects.requireNonNull(name, "name");

        Integer offset = frameBackedLayout.offsetOf(name);
        if (offset != null) {
            BytecodeNode bytecodeNode = currentBytecodeNode();
            Object previous =
                    frameBackedLocals.isCleared(
                                    bytecodeNode, frame, offset)
                            ? null
                            : frameBackedLocals.getObject(
                                    bytecodeNode, frame, offset);
            frameBackedLocals.clear(
                    bytecodeNode, frame, offset);
            establishmentOrder.remove(name);
            return previous;
        }

        establishmentOrder.remove(name);
        return dynamicOverflow.remove(name);
    }
}

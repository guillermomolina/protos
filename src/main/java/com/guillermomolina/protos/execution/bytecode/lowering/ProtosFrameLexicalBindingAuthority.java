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
import com.guillermomolina.protos.runtime.ProtosLexicalBindingAuthority;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.frame.MaterializedFrame;
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
 * <p>The frame reference held here is the owning root's own frame, materialized
 * by the installer before this authority retains it, so this context's frame-backed bindings
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
 * incoherent. The {@link BytecodeLocation} retained since BUG018-C is never
 * used directly: it is translated with {@link BytecodeLocation#update()} to
 * the root's latest node before each value read.
 *
 * <p>PERF012: the name/ordinal layout itself is {@link
 * ProtosFrameLexicalLayout}, precomputed once per root at lowering time and
 * shared, by reference, across every per-invocation authority instance; only
 * the runtime frame/dynamic-overflow state below is per-invocation.
 *
 * <p>BUG018-C: presence still comes from {@link LocalRangeAccessor#isCleared},
 * but a PRESENT frame-backed value is never read through {@link
 * LocalRangeAccessor#getObject}. The retained frame may belong to an
 * activation that ran under another tier: another activation of the same root
 * can move it to a cached node whose local-kind metadata disagrees with this
 * frame's physical tag, which makes that accessor fail. Values are instead read
 * through the public {@link BytecodeNode#getLocalValue}, at the translated
 * index of a retained {@link BytecodeLocation} of the declaring root and the
 * binding's public local offset, always behind a {@link TruffleBoundary} so it
 * consults the physical frame tag. Writes are unchanged: the cached write path
 * reconciles its own local-kind metadata.
 */
final class ProtosFrameLexicalBindingAuthority implements ProtosLexicalBindingAuthority {
    private final ProtosFrameLexicalLayout frameBackedLayout;
    private final LocalRangeAccessor frameBackedLocals;
    private final BytecodeRootNode declaringRoot;
    private final BytecodeLocation declaringLocation;
    private final MaterializedFrame frame;

    /*
     * Both structures are optional. The common case uses only statically
     * admitted frame locals and therefore carries neither allocation.
     */
    private LinkedHashMap<String, Object> dynamicOverflow;
    private LinkedHashSet<String> establishmentOrder;

    /*
     * While establishmentOrder is null, current PRESENT frame-backed bindings
     * are known to have been established in ascending layout order. This
     * ordinal is the greatest currently PRESENT ordinal in that compact mode.
     */
    private int lastCompactFrameOrdinal = -1;

    ProtosFrameLexicalBindingAuthority(
            ProtosFrameLexicalLayout frameBackedLayout,
            LocalRangeAccessor frameBackedLocals,
            BytecodeLocation declaringLocation,
            MaterializedFrame frame) {
        this.frameBackedLayout =
                Objects.requireNonNull(frameBackedLayout, "frameBackedLayout");
        this.frameBackedLocals =
                Objects.requireNonNull(frameBackedLocals, "frameBackedLocals");
        this.declaringLocation =
                Objects.requireNonNull(declaringLocation, "declaringLocation");
        this.declaringRoot = declaringLocation.getBytecodeNode().getBytecodeRootNode();
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
     * BUG018-C: the value of the frame-backed binding at {@code ordinal}, which
     * the caller has already proven PRESENT through the separate {@code
     * isCleared} check; the returned value never encodes presence.
     *
     * <p>The retained location is translated to the root's latest {@link
     * BytecodeNode} for every read, because a bytecode index is only
     * meaningful together with the node it came from. The layout's locals are
     * root-scoped, so any location of the declaring root addresses them.
     */
    @TruffleBoundary
    private Object readPresentFrameBackedValueAt(int ordinal) {
        BytecodeLocation current = declaringLocation.update();
        // I091: a frame may retain a primitive carrier; observers see the guest value.
        return ProtosNumericValueSupport.guestValue(
                current.getBytecodeNode()
                        .getLocalValue(
                                current.getBytecodeIndex(),
                                frame,
                                frameBackedLayout.localOffsetAt(ordinal)));
    }

    private void ensureGeneralEstablishmentOrder(BytecodeNode bytecodeNode) {
        if (establishmentOrder != null) {
            return;
        }
        materializeGeneralEstablishmentOrder(bytecodeNode);
    }

    @TruffleBoundary
    private void materializeGeneralEstablishmentOrder(BytecodeNode bytecodeNode) {
        LinkedHashSet<String> materialized = new LinkedHashSet<>();
        for (int ordinal = 0; ordinal < frameBackedLayout.length(); ordinal++) {
            if (!frameBackedLocals.isCleared(
                    bytecodeNode, frame, ordinal)) {
                materialized.add(frameBackedLayout.nameAt(ordinal));
            }
        }

        /*
         * Compact mode never intentionally owns dynamic bindings; retaining
         * this defensive projection keeps the transition exact if that
         * invariant is changed by future code.
         */
        if (dynamicOverflow != null) {
            materialized.addAll(dynamicOverflow.keySet());
        }

        establishmentOrder = materialized;
    }

    private void recordFrameBackedEstablishment(
            BytecodeNode bytecodeNode,
            int ordinal,
            String name) {
        if (establishmentOrder != null) {
            establishmentOrder.add(name);
            return;
        }

        if (ordinal > lastCompactFrameOrdinal) {
            lastCompactFrameOrdinal = ordinal;
            return;
        }

        /*
         * The new binding would no longer be last in layout order. Materialize
         * the exact history before applying that establishment, then append it
         * in the same position a LinkedHashSet would have given it.
         */
        ensureGeneralEstablishmentOrder(bytecodeNode);
        establishmentOrder.add(name);
    }

    private void recomputeLastCompactFrameOrdinal(
            BytecodeNode bytecodeNode) {
        if (establishmentOrder != null) {
            return;
        }

        for (int ordinal = frameBackedLayout.length() - 1;
                ordinal >= 0;
                ordinal--) {
            if (!frameBackedLocals.isCleared(
                    bytecodeNode, frame, ordinal)) {
                lastCompactFrameOrdinal = ordinal;
                return;
            }
        }
        lastCompactFrameOrdinal = -1;
    }

    /**
     * I068 Slice 5 frame-native captured-read seam. The ordinal comes from the
     * same CanonicalLexicalScope declaration order used to create this
     * authority's LocalRangeAccessor layout. The expected name is checked as a
     * defensive guard against stale or mismatched lowering metadata.
     *
     * <p>TEST009-A: partially evaluated {@link LocalRangeAccessor} accesses
     * require the accessor and its {@link BytecodeNode} to be PE constants.
     * Here both come from this per-invocation authority (its field and its
     * declaring root's current node), never from an operation operand, so
     * this seam is a {@link TruffleBoundary}.
     */
    @TruffleBoundary
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

    @TruffleBoundary
    Object readFrameBackedBindingAt(String expectedName, int ordinal) {
        BytecodeNode bytecodeNode = currentBytecodeNode();
        requirePresentFrameBackedBinding(bytecodeNode, expectedName, ordinal);
        return readPresentFrameBackedValueAt(ordinal);
    }

    @TruffleBoundary
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

    /**
     * PERF013 Slice B1 seam: exposes this authority's retained materialized
     * frame for a compile-time-proven {@link
     * com.oracle.truffle.api.bytecode.MaterializedLocalAccessor} captured
     * read. The installer materializes the frame before constructing this
     * authority, so captured access reuses that escape-safe frame directly.
     */
    MaterializedFrame retainedMaterializedFrameForCapturedAccess() {
        return frame;
    }

    /*
     * PERF038-D: created only when a captured read first caches this
     * authority, so ordinary installations allocate nothing. Guarded by this.
     */
    private Assumption installed;
    private boolean retiredInstallation;

    /**
     * PERF038-D: valid while this authority remains installed where it was
     * installed; invalidated by {@link #retireInstallation}. Never valid once
     * retired.
     */
    @TruffleBoundary
    synchronized Assumption installedAssumption() {
        if (retiredInstallation) {
            return Assumption.NEVER_VALID;
        }
        if (installed == null) {
            installed = Assumption.create("ProtosFrameLexicalBindingAuthority installed");
        }
        return installed;
    }

    @Override
    public synchronized void retireInstallation() {
        retiredInstallation = true;
        if (installed != null) {
            installed.invalidate();
        }
    }

    /**
     * PERF025 frame-materialization slice: true when this authority is the
     * one installed over {@code candidate}, the materialized frame of the
     * live activation of the root owning {@code bytecodeNode}.
     */
    boolean isInstalledFor(BytecodeNode bytecodeNode, MaterializedFrame candidate) {
        return declaringRoot == bytecodeNode.getBytecodeRootNode() && frame == candidate;
    }

    /** PERF037-C: the layout this authority stores, shared by its root's invocations. */
    ProtosFrameLexicalLayout storedLayout() {
        return frameBackedLayout;
    }

    /** PERF025-H1: true when {@code layout} is the very layout this authority stores. */
    boolean storesLayout(ProtosFrameLexicalLayout layout) {
        return frameBackedLayout == layout;
    }

    /**
     * PERF025-H1: creates the frame-backed binding at {@code ordinal} of this
     * authority's layout, the exact effect of {@link #containsBinding} followed
     * by {@link #putBinding} for that layout name, without resolving the name
     * to the ordinal again. Callers pass an ordinal the lowerer proved from
     * this same layout instance (see {@link #storesLayout}) and must already
     * have applied the owning context's OPEN/CLOSED/FROZEN creation rule.
     * Static identity never implies presence (D179 C0): a PRESENT binding,
     * including PRESENT(null), is still rejected as a duplicate creation, and
     * a cleared (never established or removed) one is established at the same
     * stable ordinal. Establishment order is recorded exactly as before.
     * That ordinal is always an {@code int} constant operand of the calling
     * operation (PERF030-I), as the {@code LocalRangeAccessor} accesses
     * require.
     *
     * <p>TEST009-A: partially evaluated {@link LocalRangeAccessor} accesses
     * require the accessor and its {@link BytecodeNode} to be PE constants.
     * Here both come from this per-invocation authority (its field and its
     * declaring root's current node), never from an operation operand, so
     * this seam is a {@link TruffleBoundary}.
     */
    @TruffleBoundary
    void createFrameBackedBindingAt(int ordinal, Object value) {
        Objects.requireNonNull(value, "value");
        BytecodeNode bytecodeNode = currentBytecodeNode();
        String name = frameBackedLayout.nameAt(ordinal);
        if (!frameBackedLocals.isCleared(bytecodeNode, frame, ordinal)) {
            throw new IllegalStateException("local slot already exists: " + name);
        }
        recordFrameBackedEstablishment(
                bytecodeNode,
                ordinal,
                name);
        frameBackedLocals.setObject(bytecodeNode, frame, ordinal, value);
    }

    /**
     * PERF025 frame-materialization slice: records every frame-backed binding
     * already PRESENT in the retained frame as established, in layout
     * (declaration) order. Used only when this authority is created after its
     * root has already established bindings directly in its frame locals,
     * which a root does only while its code is straight-line within one
     * activation, so declaration order is establishment order.
     */
    void adoptPresentFrameBackedBindings() {
        BytecodeNode bytecodeNode = currentBytecodeNode();
        adoptPresentFrameBackedBindingsSlow(bytecodeNode);
    }

    @TruffleBoundary
    private void adoptPresentFrameBackedBindingsSlow(
            BytecodeNode bytecodeNode) {
        if (establishmentOrder != null) {
            for (int ordinal = 0;
                    ordinal < frameBackedLayout.length();
                    ordinal++) {
                if (!frameBackedLocals.isCleared(
                        bytecodeNode, frame, ordinal)) {
                    establishmentOrder.add(
                            frameBackedLayout.nameAt(ordinal));
                }
            }
            return;
        }

        /*
         * This transition is reached only after the owning root established
         * direct frame locals in its straight-line declaration order. PRESENT
         * state plus layout order therefore already is the exact history.
         */
        int lastPresentOrdinal = -1;
        for (int ordinal = 0;
                ordinal < frameBackedLayout.length();
                ordinal++) {
            if (!frameBackedLocals.isCleared(
                    bytecodeNode, frame, ordinal)) {
                lastPresentOrdinal = ordinal;
            }
        }
        lastCompactFrameOrdinal = lastPresentOrdinal;
    }

    @Override
    public boolean isEmpty() {
        if (dynamicOverflow != null && !dynamicOverflow.isEmpty()) {
            return false;
        }
        if (establishmentOrder != null) {
            return establishmentOrder.isEmpty();
        }
        return lastCompactFrameOrdinal < 0;
    }

    @TruffleBoundary
    @Override
    public boolean containsBinding(String name) {
        Objects.requireNonNull(name, "name");
        Integer offset = frameBackedLayout.offsetOf(name);
        if (offset != null) {
            return !frameBackedLocals.isCleared(
                    currentBytecodeNode(), frame, offset);
        }
        return dynamicOverflow != null
                && dynamicOverflow.containsKey(name);
    }

    @TruffleBoundary
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
            return Optional.of(readPresentFrameBackedValueAt(offset));
        }
        return dynamicOverflow != null
                        && dynamicOverflow.containsKey(name)
                ? Optional.of(dynamicOverflow.get(name))
                : Optional.empty();
    }

    @Override
    public Map<String, Object> bindingsSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        BytecodeNode bytecodeNode = currentBytecodeNode();

        if (establishmentOrder == null) {
            for (int ordinal = 0;
                    ordinal < frameBackedLayout.length();
                    ordinal++) {
                if (!frameBackedLocals.isCleared(
                        bytecodeNode, frame, ordinal)) {
                    snapshot.put(
                            frameBackedLayout.nameAt(ordinal),
                            readPresentFrameBackedValueAt(ordinal));
                }
            }
            return Collections.unmodifiableMap(snapshot);
        }

        for (String name : establishmentOrder) {
            Integer offset = frameBackedLayout.offsetOf(name);
            if (offset != null) {
                if (!frameBackedLocals.isCleared(
                        bytecodeNode, frame, offset)) {
                    snapshot.put(name, readPresentFrameBackedValueAt(offset));
                }
            } else if (dynamicOverflow != null
                    && dynamicOverflow.containsKey(name)) {
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
        appendBindingsToSlow(bytecodeNode, names, values);
    }

    @TruffleBoundary
    private void appendBindingsToSlow(
            BytecodeNode bytecodeNode,
            java.util.ArrayList<String> names,
            java.util.ArrayList<Object> values) {
        if (establishmentOrder == null) {
            for (int ordinal = 0;
                    ordinal < frameBackedLayout.length();
                    ordinal++) {
                if (!frameBackedLocals.isCleared(
                        bytecodeNode, frame, ordinal)) {
                    names.add(frameBackedLayout.nameAt(ordinal));
                    values.add(readPresentFrameBackedValueAt(ordinal));
                }
            }
            return;
        }

        for (String name : establishmentOrder) {
            Integer offset = frameBackedLayout.offsetOf(name);
            if (offset != null) {
                if (!frameBackedLocals.isCleared(
                        bytecodeNode, frame, offset)) {
                    names.add(name);
                    values.add(readPresentFrameBackedValueAt(offset));
                }
            } else if (dynamicOverflow != null
                    && dynamicOverflow.containsKey(name)) {
                names.add(name);
                values.add(dynamicOverflow.get(name));
            }
        }
    }

    /*
     * PERF029: the frame ordinal here is resolved from a runtime name (for
     * example while an authority handoff migrates the previous authority's
     * bindings), so it can never be the partial-evaluation constant that every
     * LocalRangeAccessor local operation requires. Statically known bindings
     * use the ordinal-based seams above instead.
     */
    @TruffleBoundary
    @Override
    public void putBinding(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");

        Integer offset = frameBackedLayout.offsetOf(name);
        if (offset != null) {
            BytecodeNode bytecodeNode = currentBytecodeNode();
            boolean present =
                    !frameBackedLocals.isCleared(
                            bytecodeNode, frame, offset);
            if (!present) {
                recordFrameBackedEstablishment(
                        bytecodeNode,
                        offset,
                        name);
            }
            frameBackedLocals.setObject(
                    bytecodeNode, frame, offset, value);
            return;
        }

        BytecodeNode bytecodeNode = currentBytecodeNode();
        ensureGeneralEstablishmentOrder(bytecodeNode);

        boolean present =
                dynamicOverflow != null
                        && dynamicOverflow.containsKey(name);
        if (!present) {
            // PERF037-C: invalidate nearer-scope absence proofs first.
            frameBackedLayout.recordDynamicBindingCreation();
        }
        if (dynamicOverflow == null) {
            dynamicOverflow = new LinkedHashMap<>();
        }
        if (!present) {
            establishmentOrder.add(name);
        }
        dynamicOverflow.put(name, value);
    }

    @Override
    public Object removeBinding(String name) {
        Objects.requireNonNull(name, "name");

        Integer offset = frameBackedLayout.offsetOf(name);
        if (offset != null) {
            BytecodeNode bytecodeNode = currentBytecodeNode();
            boolean present =
                    !frameBackedLocals.isCleared(
                            bytecodeNode, frame, offset);
            Object previous =
                    present
                            ? readPresentFrameBackedValueAt(offset)
                            : null;

            /*
             * PERF025-D179-A: invalidate before making the local ABSENT so no
             * compiled Resolved read may still rely on continuity while the
             * physical local is already cleared. The token is one-way and is
             * deliberately not renewed by a later legal recreation.
             */
            if (present) {
                frameBackedLayout.invalidatePresentContinuityAt(offset);
            }

            frameBackedLocals.clear(
                    bytecodeNode, frame, offset);

            if (establishmentOrder != null) {
                establishmentOrder.remove(name);
            } else if (present
                    && offset == lastCompactFrameOrdinal) {
                recomputeLastCompactFrameOrdinal(bytecodeNode);
            }
            return previous;
        }

        if (establishmentOrder != null) {
            establishmentOrder.remove(name);
        }
        if (dynamicOverflow == null) {
            return null;
        }

        Object previous = dynamicOverflow.remove(name);
        if (dynamicOverflow.isEmpty()) {
            dynamicOverflow = null;
        }
        return previous;
    }
}

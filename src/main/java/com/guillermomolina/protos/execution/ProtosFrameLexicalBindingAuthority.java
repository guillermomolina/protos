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
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
 * case where the execution context itself escapes. This is current-context
 * retention only: no other root's frame is ever referenced here (that is
 * Slice 5's {@code MaterializedLocalAccessor}-based captured/outer access).
 */
final class ProtosFrameLexicalBindingAuthority implements ProtosLexicalBindingAuthority {
    private final Map<String, Integer> frameBackedOffsets;
    private final LocalRangeAccessor frameBackedLocals;
    private final BytecodeNode bytecodeNode;
    private final VirtualFrame frame;
    private final Map<String, Object> dynamicOverflow = new LinkedHashMap<>();
    private final LinkedHashSet<String> establishmentOrder = new LinkedHashSet<>();

    ProtosFrameLexicalBindingAuthority(
            List<?> frameBackedNames,
            LocalRangeAccessor frameBackedLocals,
            BytecodeNode bytecodeNode,
            VirtualFrame frame) {
        Objects.requireNonNull(frameBackedNames, "frameBackedNames");
        this.frameBackedLocals =
                Objects.requireNonNull(frameBackedLocals, "frameBackedLocals");
        this.bytecodeNode = Objects.requireNonNull(bytecodeNode, "bytecodeNode");
        this.frame = Objects.requireNonNull(frame, "frame");

        if (frameBackedNames.size() != frameBackedLocals.getLength()) {
            throw new IllegalArgumentException(
                    "frame-backed binding-name count must match local range length");
        }

        Map<String, Integer> offsets = new LinkedHashMap<>();
        for (int index = 0; index < frameBackedNames.size(); index++) {
            Object candidate =
                    Objects.requireNonNull(
                            frameBackedNames.get(index),
                            "frameBackedNames[" + index + "]");
            if (!(candidate instanceof String name)) {
                throw new IllegalArgumentException(
                        "frame-backed binding name must be a String at index "
                                + index
                                + ": "
                                + candidate.getClass().getName());
            }
            if (offsets.put(name, index) != null) {
                throw new IllegalArgumentException(
                        "duplicate frame-backed binding name: " + name);
            }
        }
        this.frameBackedOffsets = Map.copyOf(offsets);
    }

    @Override
    public boolean containsBinding(String name) {
        Objects.requireNonNull(name, "name");
        Integer offset = frameBackedOffsets.get(name);
        if (offset != null) {
            return !frameBackedLocals.isCleared(
                    bytecodeNode, frame, offset);
        }
        return dynamicOverflow.containsKey(name);
    }

    @Override
    public Optional<Object> readBinding(String name) {
        Objects.requireNonNull(name, "name");
        Integer offset = frameBackedOffsets.get(name);
        if (offset != null) {
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
        for (String name : establishmentOrder) {
            Integer offset = frameBackedOffsets.get(name);
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
    public void putBinding(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        establishmentOrder.add(name);

        Integer offset = frameBackedOffsets.get(name);
        if (offset != null) {
            frameBackedLocals.setObject(
                    bytecodeNode, frame, offset, value);
            return;
        }

        dynamicOverflow.put(name, value);
    }

    @Override
    public Object removeBinding(String name) {
        Objects.requireNonNull(name, "name");

        Integer offset = frameBackedOffsets.get(name);
        if (offset != null) {
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

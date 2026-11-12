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
import com.guillermomolina.protos.runtime.ProtosLexicalBindingAuthority;
import com.guillermomolina.protos.runtime.ProtosLexicalCell;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.frame.MaterializedFrame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * I115-S1c-L: the single frame-backed {@link ProtosLexicalBindingAuthority}
 * of one escaped genuine Closure activation (GraalPy {@code
 * PFrameLocalsProxy}, TruffleRuby frame-backed {@code Binding}).
 *
 * <p>It is a live view of the original frame, never a mirror of it. A LOCAL
 * binding is read and written in the original Bytecode DSL local of the
 * retained {@link MaterializedFrame}; a CELL binding in the original {@link
 * ProtosLexicalCell}, which every capturing Closure shares. The cleared
 * state of a LOCAL local and the absent value of a cell are the binding's
 * only presence state, so removal through the Context and reads by the
 * Bytecode root observe the same physical membership. Only names outside the
 * root's compiled {@link ProtosBytecodeRootNode.LexicalFrameLayout} allocate
 * the lazy overflow store; no second value is ever stored for a declared
 * binding.
 *
 * <p>Java {@code null} is internal absence only; the guest {@code null} is
 * an ordinary PRESENT value. Values leave this view normalized to guest
 * values, never as primitive carriers.
 *
 * <p>The retained {@link BytecodeLocation} is updated before every frame
 * access, so a Bytecode tier change or a reparse (for example for source
 * information) never reads the frame through a stale Bytecode node.
 *
 * <p>Structural object rules (OPEN/CLOSED/FROZEN, duplicate creation,
 * absent assignment or removal) are enforced by the owning
 * {@code ProtosObjectValue} before it calls this authority; the direct
 * Bytecode creation and assignment operations apply the same rules. Removal
 * changes only this activation's own local or cell: the presence-aware
 * Bytecode operations test exactly that physical state, so no other
 * activation and no compiled specialization is invalidated.
 *
 * <p>All operations are cold reflective paths behind Truffle boundaries.
 * The view is confined to the Actor-local execution of its activation and
 * carries no synchronization.
 */
final class ProtosBytecodeLexicalFrameView implements ProtosLexicalBindingAuthority {
    private final MaterializedFrame frame;
    private ProtosBytecodeRootNode.LexicalFrameLayout layout;
    private BytecodeLocation location;
    private LinkedHashMap<String, Object> overflow;

    ProtosBytecodeLexicalFrameView(
            MaterializedFrame frame,
            BytecodeLocation location,
            ProtosBytecodeRootNode.LexicalFrameLayout layout) {
        this.frame = Objects.requireNonNull(frame, "frame");
        this.location = Objects.requireNonNull(location, "location");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /**
     * Attaches the owning activation to every cell of the layout, so direct
     * mutations of a cell by capturing Closures observe the owner's Context
     * state.
     */
    @TruffleBoundary
    void attachCellOwners(ProtosActivation owner) {
        BytecodeNode bytecode = currentBytecode();
        for (int index = 0; index < layout.size(); index++) {
            if (layout.isCell(index) && stored(index, bytecode) instanceof ProtosLexicalCell cell) {
                cell.attachOwnerForRuntime(owner);
            }
        }
    }

    /**
     * Stops viewing the frame: every PRESENT declared binding moves, with its
     * current value, into this view's own store, and the frame layout is no
     * longer consulted. Used when the frame locals are about to be reused by
     * another invocation, so the bindings stay those of this activation.
     */
    @TruffleBoundary
    void detach() {
        if (layout.size() == 0) {
            return;
        }
        BytecodeNode bytecode = currentBytecode();
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < layout.size(); index++) {
            Object value = valueOrNull(index, bytecode);
            if (value != null) {
                values.put(layout.name(index), value);
            }
        }
        if (overflow != null) {
            values.putAll(overflow);
        }
        overflow = values;
        layout = ProtosBytecodeRootNode.EMPTY_LEXICAL_FRAME_LAYOUT;
    }

    /** The current Bytecode node of the retained location; never a stale tier. */
    private BytecodeNode currentBytecode() {
        location = location.update();
        return location.getBytecodeNode();
    }

    private Object stored(int index, BytecodeNode bytecode) {
        if (layout.accessor(index).isCleared(bytecode, frame)) {
            return null;
        }
        return layout.accessor(index).getObject(bytecode, frame);
    }

    private ProtosLexicalCell cellOrNull(int index, BytecodeNode bytecode) {
        return stored(index, bytecode) instanceof ProtosLexicalCell cell ? cell : null;
    }

    /* The guest value of a declared binding, or null when it is ABSENT. */
    private Object valueOrNull(int index, BytecodeNode bytecode) {
        Object value;
        if (layout.isCell(index)) {
            ProtosLexicalCell cell = cellOrNull(index, bytecode);
            value = cell == null ? null : cell.valueOrNullForRuntime();
        } else {
            value = stored(index, bytecode);
        }
        return value == null ? null : ProtosNumericValueSupport.guestValue(value);
    }

    private boolean present(int index, BytecodeNode bytecode) {
        if (layout.isCell(index)) {
            ProtosLexicalCell cell = cellOrNull(index, bytecode);
            return cell != null && cell.isPresentForRuntime();
        }
        return stored(index, bytecode) != null;
    }

    @Override
    @TruffleBoundary
    public boolean containsBinding(String name) {
        int index = layout.indexOf(Objects.requireNonNull(name, "name"));
        if (index >= 0) {
            return present(index, currentBytecode());
        }
        return overflow != null && overflow.containsKey(name);
    }

    @Override
    @TruffleBoundary
    public Optional<Object> readBinding(String name) {
        int index = layout.indexOf(Objects.requireNonNull(name, "name"));
        if (index >= 0) {
            return Optional.ofNullable(valueOrNull(index, currentBytecode()));
        }
        return overflow == null ? Optional.empty() : Optional.ofNullable(overflow.get(name));
    }

    @Override
    @TruffleBoundary
    public boolean isEmpty() {
        BytecodeNode bytecode = currentBytecode();
        for (int index = 0; index < layout.size(); index++) {
            if (present(index, bytecode)) {
                return false;
            }
        }
        return overflow == null || overflow.isEmpty();
    }

    @Override
    @TruffleBoundary
    public Map<String, Object> bindingsSnapshot() {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        BytecodeNode bytecode = currentBytecode();
        for (int index = 0; index < layout.size(); index++) {
            Object value = valueOrNull(index, bytecode);
            if (value != null) {
                snapshot.put(layout.name(index), value);
            }
        }
        if (overflow != null) {
            snapshot.putAll(overflow);
        }
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    @TruffleBoundary
    public void appendBindingNamesTo(ArrayList<String> names) {
        appendBindingNamesToBounded(names, Integer.MAX_VALUE);
    }

    @Override
    @TruffleBoundary
    public boolean appendBindingNamesToBounded(ArrayList<String> names, int remainingBudget) {
        Objects.requireNonNull(names, "names");
        int budget = remainingBudget;
        BytecodeNode bytecode = currentBytecode();
        for (int index = 0; index < layout.size(); index++) {
            if (present(index, bytecode)) {
                if (budget-- <= 0) {
                    return false;
                }
                names.add(layout.name(index));
            }
        }
        if (overflow != null) {
            for (String name : overflow.keySet()) {
                if (budget-- <= 0) {
                    return false;
                }
                names.add(name);
            }
        }
        return true;
    }

    @Override
    @TruffleBoundary
    public void appendBindingsTo(ArrayList<String> names, ArrayList<Object> values) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(values, "values");
        BytecodeNode bytecode = currentBytecode();
        for (int index = 0; index < layout.size(); index++) {
            Object value = valueOrNull(index, bytecode);
            if (value != null) {
                names.add(layout.name(index));
                values.add(value);
            }
        }
        if (overflow != null) {
            for (Map.Entry<String, Object> entry : overflow.entrySet()) {
                names.add(entry.getKey());
                values.add(entry.getValue());
            }
        }
    }

    /**
     * Establishes or assigns a binding in its one physical owner: the
     * original local, the original cell, or the overflow store for a name
     * outside the compiled layout. A legal recreation of a removed declared
     * binding reuses the same local or cell.
     */
    @Override
    @TruffleBoundary
    public void putBinding(String name, Object value) {
        Objects.requireNonNull(value, "value");
        int index = layout.indexOf(Objects.requireNonNull(name, "name"));
        if (index < 0) {
            if (overflow == null) {
                overflow = new LinkedHashMap<>();
            }
            overflow.put(name, value);
            return;
        }
        BytecodeNode bytecode = currentBytecode();
        if (layout.isCell(index)) {
            ProtosLexicalCell cell = cellOrNull(index, bytecode);
            if (cell == null) {
                // The cell prologue precedes every escape of the frame.
                throw new IllegalStateException("a CELL binding has no physical cell: " + name);
            }
            cell.setForRuntime(value);
        } else {
            layout.accessor(index).setObject(bytecode, frame, value);
        }
    }

    /**
     * Makes a binding ABSENT in its physical owner and returns its previous
     * guest value, or {@code null} when it was already ABSENT. A cell keeps
     * its identity.
     */
    @Override
    @TruffleBoundary
    public Object removeBinding(String name) {
        int index = layout.indexOf(Objects.requireNonNull(name, "name"));
        if (index < 0) {
            return overflow == null ? null : overflow.remove(name);
        }
        BytecodeNode bytecode = currentBytecode();
        Object previous = valueOrNull(index, bytecode);
        if (previous == null) {
            return null;
        }
        if (layout.isCell(index)) {
            cellOrNull(index, bytecode).clearForRuntime();
        } else {
            layout.accessor(index).clear(bytecode, frame);
        }
        return previous;
    }
}

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

package com.guillermomolina.protos.runtime;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default {@link ProtosLexicalBindingAuthority}: an ordinary insertion-ordered
 * map owned exclusively by this authority instance. This is the same physical
 * storage every {@code ProtosObjectValue} local slot used before the PLAT036
 * authority seam existed; it is installed both for ordinary objects and, for
 * now, for execution contexts, so no observable behavior changes in this
 * slice.
 *
 * <p>PERF037-B stable locations: a binding a guarded member-read PIC has
 * selected may be promoted in place to a {@link SlotCell}, the binding's
 * physical value location. The cell is still the single authoritative store
 * of that binding (the map entry holds the cell instead of the value); it
 * is never exposed outside this authority's read surface, which always
 * unwraps it. Assignment writes through the cell, so the PIC observes the
 * current value without a nominal lookup; removal drops the cell together
 * with its map entry, and a recreated binding starts uncelled, so a stale
 * cell can be reached only through a selection that the owning object has
 * already invalidated. Bindings no PIC selects never pay for a cell.
 */
final class ProtosMapBackedLexicalBindingAuthority
        implements ProtosLexicalBindingAuthority {
    /** Stable physical location of one promoted binding. */
    static final class SlotCell {
        private Object value;

        /*
         * Allocated only if a guarded read selects a non-Closure value.
         * It is one-way: once a Closure is stored, the token is invalidated
         * before publication and never renewed for this cell.
         */
        private Assumption nonClosureContinuity;

        private SlotCell(Object value) {
            this.value = value;
        }

        Object value() {
            return value;
        }

        Assumption nonClosureContinuityForGuardedRead() {
            if (value instanceof ProtosClosureValue) {
                return null;
            }
            Assumption token = nonClosureContinuity;
            if (token == null) {
                token = Truffle.getRuntime()
                        .createAssumption("Protos selected slot stays non-Closure");
                nonClosureContinuity = token;
            }
            return token.isValid() ? token : null;
        }

        void replace(Object replacement) {
            Assumption token = nonClosureContinuity;
            if (token != null
                    && replacement instanceof ProtosClosureValue) {
                // Deoptimize compiled plain reads before publishing Closure.
                token.invalidate();
            }
            value = replacement;
        }
    }

    /*
     * Null is the physical representation of no bindings. LinkedHashMap
     * insertion order already carries the exact create/remove/recreate order
     * required by this backend-private authority, so no parallel order list is
     * needed. A value is either the binding's value or its SlotCell.
     */
    private LinkedHashMap<String, Object> bindings;

    private static Object unwrap(Object stored) {
        return stored instanceof SlotCell cell ? cell.value : stored;
    }

    /**
     * Returns the stable location of the existing binding {@code name},
     * promoting it in place on first request, or {@code null} when absent.
     * Promotion replaces the value of an existing key, so insertion order is
     * unchanged. Specialization-time only.
     */
    SlotCell slotCellFor(String name) {
        Objects.requireNonNull(name, "name");
        if (bindings == null || !bindings.containsKey(name)) {
            return null;
        }
        Object stored = bindings.get(name);
        if (stored instanceof SlotCell cell) {
            return cell;
        }
        SlotCell cell = new SlotCell(stored);
        bindings.put(name, cell);
        return cell;
    }

    @Override
    public boolean containsBinding(String name) {
        Objects.requireNonNull(name, "name");
        return bindings != null && bindings.containsKey(name);
    }

    @Override
    public Optional<Object> readBinding(String name) {
        Objects.requireNonNull(name, "name");
        if (bindings == null || !bindings.containsKey(name)) {
            return Optional.empty();
        }
        return Optional.of(unwrap(bindings.get(name)));
    }

    @Override
    public boolean isEmpty() {
        return bindings == null || bindings.isEmpty();
    }

    @Override
    public Map<String, Object> bindingsSnapshot() {
        if (bindings == null) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Object> binding : bindings.entrySet()) {
            snapshot.put(binding.getKey(), unwrap(binding.getValue()));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public void appendBindingsTo(
            java.util.ArrayList<String> names,
            java.util.ArrayList<Object> values) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(values, "values");
        if (bindings == null) {
            return;
        }
        for (Map.Entry<String, Object> binding : bindings.entrySet()) {
            names.add(binding.getKey());
            values.add(unwrap(binding.getValue()));
        }
    }

    @Override
    public void putBinding(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (bindings == null) {
            bindings = new LinkedHashMap<>();
        } else if (bindings.get(name) instanceof SlotCell cell) {
            cell.replace(value);
            return;
        }
        bindings.put(name, value);
    }

    @Override
    public Object removeBinding(String name) {
        Objects.requireNonNull(name, "name");
        if (bindings == null) {
            return null;
        }
        Object previous = bindings.remove(name);
        if (bindings.isEmpty()) {
            bindings = null;
        }
        return unwrap(previous);
    }
}

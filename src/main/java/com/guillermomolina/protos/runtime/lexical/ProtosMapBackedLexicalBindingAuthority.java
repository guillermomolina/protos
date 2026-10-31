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
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.PropertyGetter;
import com.oracle.truffle.api.object.Shape;
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
/**
 * I115 experimental Shape-backed authoritative local-slot storage.
 *
 * The ordered LinkedHashMap is ONLY an index of the names plus optional PIC
 * cells; no guest value is duplicated there. All values live in one
 * DynamicObject, with identical physical locations for generic read/write,
 * guarded read, and experimental GetAttribute/SetAttribute.
 */
final class ProtosMapBackedLexicalBindingAuthority
        implements ProtosLexicalBindingAuthority {
    private static final Shape INITIAL_SHAPE = Shape.newBuilder().build();

    static final class Store extends DynamicObject {
        Store() {
            super(INITIAL_SHAPE);
        }
    }

    /*
     * I115-H10-A: the physical store uses a Java long for every
     * ordinary signed-64 Protos Integer. A Java Integer carrier
     * from an older experimental operation is widened, never
     * truncated. Other guest values keep their object storage.
     */
    private static Object toStorageValue(Object value) {
        if (value instanceof ProtosIntegerValue integer) {
            return Long.valueOf(integer.longValue());
        }
        if (value instanceof Integer integer) {
            return Long.valueOf(integer.longValue());
        }
        return value;
    }

    /*
     * The guest-facing authority never exposes an internal long.
     * The stored location, not this projected guest value, is the
     * single authoritative source of the binding.
     */
    private static Object toGuestValue(Object value) {
        return value instanceof Long integer
                ? ProtosNumericValueSupport.integer(integer.longValue())
                : value;
    }

    static final class SlotCell {
        private final Store store;
        private final String key;
        private Assumption nonClosureContinuity;

        SlotCell(Store store, String key) {
            this.store = Objects.requireNonNull(store);
            this.key = Objects.requireNonNull(key);
        }

        Object value() {
            return toGuestValue(
                    DynamicObject.GetNode.getUncached()
                            .execute(store, key, null));
        }

        Object value(DynamicObject.GetNode getNode) {
            return toGuestValue(getNode.execute(store, key, null));
        }

        Store storage() {
            return store;
        }

        String key() {
            return key;
        }

        PropertyGetter getter() {
            return store.getShape().makePropertyGetter(key);
        }

        Assumption nonClosureContinuityForGuardedRead() {
            if (value() instanceof ProtosClosureValue) {
                return null;
            }
            Assumption token = nonClosureContinuity;
            if (token == null) {
                token = Truffle.getRuntime().createAssumption(
                        "Protos selected slot stays non-Closure");
                nonClosureContinuity = token;
            }
            return token.isValid() ? token : null;
        }

        void replace(Object replacement) {
            replace(replacement, DynamicObject.PutNode.getUncached());
        }

        void replace(Object replacement, DynamicObject.PutNode put) {
            Assumption token = nonClosureContinuity;
            if (token != null && replacement instanceof ProtosClosureValue) {
                token.invalidate();
            }
            put.execute(store, key, toStorageValue(replacement));
        }

    }

    /* A name index, NOT a second value store. Null = unselected PIC location. */
    private LinkedHashMap<String, SlotCell> bindings;
    /*
     * I115-H10-C: retain the same authoritative Store and shared
     * Shape tree. Truffle is allowed to preserve generalized
     * property locations across removal and recreation.
     */
    private final Store store = new Store();
    private boolean localCallPresent;

    SlotCell slotCellFor(String name) {
        Objects.requireNonNull(name, "name");
        if (bindings == null || !bindings.containsKey(name)) {
            return null;
        }
        SlotCell selected = bindings.get(name);
        if (selected == null) {
            selected = new SlotCell(store, name.intern());
            bindings.put(name, selected);
        }
        return selected;
    }

    boolean hasLocalCallBindingForRuntime() {
        return localCallPresent;
    }

    @Override
    public boolean containsBinding(String name) {
        Objects.requireNonNull(name, "name");
        return bindings != null && bindings.containsKey(name);
    }

    @Override
    public Optional<Object> readBinding(String name) {
        Objects.requireNonNull(name, "name");
        if (!containsBinding(name)) {
            return Optional.empty();
        }
        // DynamicObject property keys compare by identity. Canonicalize keys.
        return Optional.of(toGuestValue(
                DynamicObject.GetNode.getUncached()
                        .execute(store, name.intern(), null)));
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
        for (String name : bindings.keySet()) {
            snapshot.put(name, toGuestValue(
                    DynamicObject.GetNode.getUncached()
                            .execute(store, name.intern(), null)));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public void appendBindingsTo(
            java.util.ArrayList<String> names,
            java.util.ArrayList<Object> values) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(values, "values");
        if (bindings != null) {
            for (String name : bindings.keySet()) {
                names.add(name);
                values.add(toGuestValue(
                        DynamicObject.GetNode.getUncached()
                                .execute(store, name.intern(), null)));
            }
        }
    }

    @Override
    public void putBinding(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (bindings == null) {
            bindings = new LinkedHashMap<>();
        }
        if (!bindings.containsKey(name)) {
            bindings.put(name, null);
        }
        SlotCell cell = bindings.get(name);
        if (cell != null) {
            cell.replace(value);
        } else {
            DynamicObject.PutNode.getUncached().execute(
                    store, name.intern(), toStorageValue(value));
        }
        if ("call".equals(name)) {
            localCallPresent = true;
        }
    }

    @Override
    public Object removeBinding(String name) {
        Objects.requireNonNull(name, "name");
        if (!containsBinding(name)) {
            return null;
        }
        Object previous = toGuestValue(
                DynamicObject.GetNode.getUncached()
                        .execute(store, name.intern(), null));
        DynamicObject.RemoveKeyNode.getUncached().execute(store, name.intern());
        bindings.remove(name);
        if ("call".equals(name)) {
            localCallPresent = false;
        }
        if (bindings.isEmpty()) {
            bindings = null;
        }
        return previous;
    }
}

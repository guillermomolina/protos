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

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link ProtosLexicalBindingAuthority} adapter over the properties of one
 * Truffle {@link DynamicObject}: the property set of that object is the
 * single physical store of the bindings, and no value is copied elsewhere.
 *
 * <p>Two storage forms share the same operations:
 * <ul>
 *   <li>the object authority, whose storage is the owning
 *       {@link ProtosObjectValue} itself (the ordinary slot representation
 *       as a Truffle {@code DynamicObject}); and</li>
 *   <li>the independent durable authority, whose storage is a private
 *       {@link Store}, used only by owners whose bindings are not ordinary
 *       object properties (for example an execution context before or
 *       instead of frame-backed storage).</li>
 * </ul>
 *
 * <p>Property keys are canonical interned {@code String}s, because cached
 * {@code DynamicObject} nodes compare keys by identity.
 */
final class ProtosMapBackedLexicalBindingAuthority
        implements ProtosLexicalBindingAuthority {
    private static final Shape STORE_INITIAL_SHAPE =
            Shape.newBuilder()
                    .layout(Store.class, MethodHandles.lookup())
                    .build();

    /** Independent storage of a durable authority not backed by an object. */
    static final class Store extends DynamicObject {
        Store() {
            super(STORE_INITIAL_SHAPE);
        }
    }

    private final DynamicObject storage;

    /** The object authority: {@code owner} is its own physical storage. */
    ProtosMapBackedLexicalBindingAuthority(ProtosObjectValue owner) {
        this.storage = Objects.requireNonNull(owner, "owner");
    }

    /** An independent durable authority with its own private storage. */
    ProtosMapBackedLexicalBindingAuthority() {
        this.storage = new Store();
    }

    /** The single physical storage of this authority's bindings. */
    DynamicObject storage() {
        return storage;
    }

    /** Whether {@code candidate} is this authority's physical storage. */
    boolean isStoredIn(DynamicObject candidate) {
        return storage == candidate;
    }

    private static String key(String name) {
        return Objects.requireNonNull(name, "name").intern();
    }

    @Override
    public boolean containsBinding(String name) {
        return DynamicObject.ContainsKeyNode.getUncached()
                .execute(storage, key(name));
    }

    @Override
    public Optional<Object> readBinding(String name) {
        return Optional.ofNullable(DynamicObject.GetNode.getUncached()
                .execute(storage, key(name), null));
    }

    @Override
    public boolean isEmpty() {
        return storage.getShape().getPropertyCount() == 0;
    }

    @Override
    public Map<String, Object> bindingsSnapshot() {
        Object[] keys = DynamicObject.GetKeyArrayNode.getUncached().execute(storage);
        if (keys.length == 0) {
            return Collections.emptyMap();
        }
        DynamicObject.GetNode get = DynamicObject.GetNode.getUncached();
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        for (Object key : keys) {
            snapshot.put((String) key, get.execute(storage, key, null));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public void appendBindingNamesTo(java.util.ArrayList<String> names) {
        Objects.requireNonNull(names, "names");
        Object[] keys =
                DynamicObject.GetKeyArrayNode.getUncached()
                        .execute(storage);
        for (Object key : keys) {
            names.add((String) key);
        }
    }

    @Override
    public boolean appendBindingNamesToBounded(
            java.util.ArrayList<String> names,
            int remainingBudget) {
        Objects.requireNonNull(names, "names");
        Object[] keys =
                DynamicObject.GetKeyArrayNode.getUncached()
                        .execute(storage);
        int appended = 0;
        for (Object key : keys) {
            if (appended >= remainingBudget) {
                return false;
            }
            names.add((String) key);
            appended++;
        }
        return true;
    }

    @Override
    public void appendBindingsTo(
            java.util.ArrayList<String> names,
            java.util.ArrayList<Object> values) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(values, "values");
        DynamicObject.GetNode get = DynamicObject.GetNode.getUncached();
        for (Object key : DynamicObject.GetKeyArrayNode.getUncached().execute(storage)) {
            names.add((String) key);
            values.add(get.execute(storage, key, null));
        }
    }

    @Override
    public void putBinding(String name, Object value) {
        Objects.requireNonNull(value, "value");
        if (storage == ProtosObjectValue.rootObject() && "call".equals(name)) {
            synchronized (storage) {
                ProtosObjectValue.invalidateRootCallSelectionForRuntime();
                DynamicObject.PutNode.getUncached().execute(storage, key(name), value);
            }
            return;
        }
        DynamicObject.PutNode.getUncached().execute(storage, key(name), value);
    }

    @Override
    public Object removeBinding(String name) {
        String key = key(name);
        if (storage == ProtosObjectValue.rootObject() && "call".equals(name)) {
            synchronized (storage) {
                Object previous = DynamicObject.GetNode.getUncached()
                        .execute(storage, key, null);
                if (previous != null) {
                    ProtosObjectValue.invalidateRootCallSelectionForRuntime();
                    DynamicObject.RemoveKeyNode.getUncached().execute(storage, key);
                }
                return previous;
            }
        }
        Object previous = DynamicObject.GetNode.getUncached().execute(storage, key, null);
        if (previous != null) {
            DynamicObject.RemoveKeyNode.getUncached().execute(storage, key);
        }
        return previous;
    }
}

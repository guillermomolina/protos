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
 */
final class ProtosMapBackedLexicalBindingAuthority implements ProtosLexicalBindingAuthority {
    private final LinkedHashMap<String, Object> bindings = new LinkedHashMap<>();
    private final java.util.ArrayList<String> bindingOrder = new java.util.ArrayList<>();

    @Override
    public boolean containsBinding(String name) {
        Objects.requireNonNull(name, "name");
        return bindings.containsKey(name);
    }

    @Override
    public Optional<Object> readBinding(String name) {
        Objects.requireNonNull(name, "name");
        return bindings.containsKey(name) ? Optional.of(bindings.get(name)) : Optional.empty();
    }

    @Override
    public Map<String, Object> bindingsSnapshot() {
        LinkedHashMap<String, Object> snapshot =
                new LinkedHashMap<>(bindingOrder.size());
        for (int index = 0; index < bindingOrder.size(); index++) {
            String name = bindingOrder.get(index);
            snapshot.put(name, bindings.get(name));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public void appendBindingsTo(
            java.util.ArrayList<String> names,
            java.util.ArrayList<Object> values) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(values, "values");
        for (int index = 0; index < bindingOrder.size(); index++) {
            String name = bindingOrder.get(index);
            names.add(name);
            values.add(bindings.get(name));
        }
    }

    @Override
    public void putBinding(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (!bindings.containsKey(name)) {
            bindingOrder.add(name);
        }
        bindings.put(name, value);
    }

    @Override
    public Object removeBinding(String name) {
        Objects.requireNonNull(name, "name");
        Object previous = bindings.remove(name);
        if (previous != null) {
            for (int index = 0; index < bindingOrder.size(); index++) {
                if (bindingOrder.get(index).equals(name)) {
                    bindingOrder.remove(index);
                    break;
                }
            }
        }
        return previous;
    }
}

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
    private final Map<String, Object> bindings = new LinkedHashMap<>();

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
        return Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
    }

    @Override
    public void putBinding(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        bindings.put(name, value);
    }

    @Override
    public Object removeBinding(String name) {
        Objects.requireNonNull(name, "name");
        return bindings.remove(name);
    }
}

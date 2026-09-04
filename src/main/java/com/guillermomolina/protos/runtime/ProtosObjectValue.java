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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ProtosObjectValue {
    public enum MutationState {
        OPEN,
        CLOSED,
        FROZEN
    }

    private static final ProtosObjectValue ROOT = new ProtosObjectValue();

    private final Object parent;
    private final Map<String, Object> localSlots = new LinkedHashMap<>();
    private MutationState mutationState = MutationState.OPEN;

    private ProtosObjectValue() {
        this.parent = null;
    }

    public ProtosObjectValue(Object parent) {
        this.parent = Objects.requireNonNull(parent, "parent");
    }

    public static ProtosObjectValue rootObject() {
        return ROOT;
    }

    public boolean isRootObject() {
        return this == ROOT;
    }

    public Optional<Object> parent() {
        return Optional.ofNullable(parent);
    }

    public MutationState mutationState() {
        return mutationState;
    }

    public boolean isOpen() {
        return mutationState == MutationState.OPEN;
    }

    public boolean isClosed() {
        return mutationState == MutationState.CLOSED;
    }

    public boolean isFrozen() {
        return mutationState == MutationState.FROZEN;
    }

    public void close() {
        if (mutationState == MutationState.OPEN) {
            mutationState = MutationState.CLOSED;
        }
    }

    public void freeze() {
        mutationState = MutationState.FROZEN;
    }

    public boolean hasLocalSlot(String name) {
        Objects.requireNonNull(name, "name");
        return localSlots.containsKey(name);
    }

    public Optional<Object> readLocalSlot(String name) {
        Objects.requireNonNull(name, "name");
        return localSlots.containsKey(name)
                ? Optional.of(localSlots.get(name))
                : Optional.empty();
    }

    public Optional<ProtosSlotLookupResult> lookupSlot(String name) {
        Objects.requireNonNull(name, "name");

        ProtosObjectValue current = this;
        while (true) {
            if (current.localSlots.containsKey(name)) {
                return Optional.of(
                        new ProtosSlotLookupResult(current.localSlots.get(name), current));
            }

            if (current.parent == null) {
                return Optional.empty();
            }

            if (!(current.parent instanceof ProtosObjectValue parentObject)) {
                throw new UnsupportedOperationException(
                        "Delegated lookup through non-ordinary Protos values "
                                + "requires standard prototype bootstrap");
            }

            current = parentObject;
        }
    }

    public Optional<Object> readSlot(String name) {
        return lookupSlot(name).map(ProtosSlotLookupResult::value);
    }

    public void createLocalSlot(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");

        if (mutationState == MutationState.FROZEN) {
            throw new IllegalStateException("object is frozen");
        }
        if (mutationState == MutationState.CLOSED) {
            throw new IllegalStateException("object is closed");
        }
        if (localSlots.containsKey(name)) {
            throw new IllegalStateException("local slot already exists: " + name);
        }

        localSlots.put(name, value);
    }

    public void assignLocalSlot(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");

        if (mutationState == MutationState.FROZEN) {
            throw new IllegalStateException("object is frozen");
        }
        if (!localSlots.containsKey(name)) {
            throw new IllegalStateException("local slot does not exist: " + name);
        }

        localSlots.put(name, value);
    }

    public Object removeLocalSlot(String name) {
        Objects.requireNonNull(name, "name");

        if (mutationState == MutationState.FROZEN) {
            throw new IllegalStateException("object is frozen");
        }
        if (mutationState == MutationState.CLOSED) {
            throw new IllegalStateException("object is closed");
        }
        if (!localSlots.containsKey(name)) {
            throw new IllegalStateException("local slot does not exist: " + name);
        }

        return localSlots.remove(name);
    }
}

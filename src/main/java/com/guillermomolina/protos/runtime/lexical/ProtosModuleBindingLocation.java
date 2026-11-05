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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import java.util.Objects;

/**
 * I115 (GraalPy ReadGlobal/WriteGlobal over the module dict storage): the
 * stable physical location of one NAME binding in a module execution
 * context, resolved once when a ReadGlobal or WriteGlobal site specializes.
 *
 * <p>It is only an address: the module context, its single map-backed store
 * and the binding's interned key. No value is copied here, so every legal
 * assignment stays observable. The store instance is fixed for the lifetime
 * of the context (I115 installs no other authority), so the address stays
 * valid across removal and recreation of the binding: a removed binding reads
 * as absent ({@code null}), and the caller then takes its unspecialized NAME
 * access over the same storage.
 */
public final class ProtosModuleBindingLocation {
    /* Private absence marker of a store read; never a stored value. */
    private static final Object ABSENT = new Object();

    private final ProtosObjectValue context;
    private final DynamicObject store;
    private final String key;

    private ProtosModuleBindingLocation(
            ProtosObjectValue context, DynamicObject store, String key) {
        this.context = Objects.requireNonNull(context, "context");
        this.store = Objects.requireNonNull(store, "store");
        this.key = Objects.requireNonNull(key, "key");
    }

    /**
     * The location of the PRESENT binding {@code name} of the execution
     * context {@code context}, or {@code null} when it is not a map-backed
     * execution context holding {@code name}. Specialization time only.
     */
    @TruffleBoundary
    public static ProtosModuleBindingLocation ofOrNull(ProtosObjectValue context, String name) {
        if (!(context instanceof ProtosExecutionContextValue executionContext)
                || !(executionContext.lexicalBindingAuthorityForRuntime()
                        instanceof ProtosMapBackedLexicalBindingAuthority mapBacked)) {
            return null;
        }
        ProtosMapBackedLexicalBindingAuthority.SlotCell cell = mapBacked.slotCellFor(name);
        return cell == null
                ? null
                : new ProtosModuleBindingLocation(context, cell.storage(), cell.key());
    }

    /** The current guest value of the binding, or {@code null} when it is absent. */
    public Object readOrNull(DynamicObject.GetNode getNode) {
        Object stored = getNode.execute(store, key, null);
        return stored instanceof Long integer
                ? ProtosNumericValueSupport.integer(integer.longValue())
                : stored;
    }

    /** Whether the binding is currently present at this location. */
    public boolean isPresent(DynamicObject.GetNode getNode) {
        return getNode.execute(store, key, ABSENT) != ABSENT;
    }

    /**
     * Assigns {@code value} to the binding at this fixed location through
     * the context's authoritative assignment, so the stored representation
     * (an internal {@code long} for a signed-64 Integer), the frozen-context
     * rule, the slot-presence rule and the invalidation of value-lookup
     * dependencies are exactly those of every other assignment of the
     * binding. No other store or owner is consulted and the name is never
     * resolved again: when the binding was removed, or the context frozen,
     * after this location was selected (for example by the right-hand side
     * of the assignment), the assignment fails instead of being redirected or
     * creating a binding.
     *
     * @throws IllegalStateException when the context is frozen or the
     *     binding is absent, as {@link ProtosObjectValue#assignLocalSlot}
     */
    @TruffleBoundary
    public void assign(Object value) {
        context.assignLocalSlot(key, value);
    }
}

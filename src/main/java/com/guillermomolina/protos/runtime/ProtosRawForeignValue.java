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

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.util.Objects;
import java.util.Optional;

/**
 * Raw foreign reference: an admitted foreign value that is not converted to an existing Protos
 * value family (D188, {@code VALUES_AND_COLLECTIONS.md} "Foreign Values").
 *
 * <p>It is an identity-bearing value that forms no new value family and is deliberately not a
 * {@link ProtosObjectValue}: it has no local Protos slots, so ordinary slot creation and assignment
 * on it fail with their ordinary failure. Its delegation parent is the root Object, which supplies
 * the default {@code ==} (that is, {@code ===}) and {@code hash} ({@code identityHashOf}); its
 * member lookup is the D188 projection of {@link Handle}, which never supplies the default
 * construction of {@code Object.call}.
 *
 * <p>Identity: when the provider classified a stable foreign identity, two references denote the
 * same Protos identity exactly when they belong to the same session generation and their identity
 * keys are equal; otherwise each admission is its own identity and aliases preserve it. Neither
 * host reference identity nor wrapper identity is otherwise consulted.
 *
 * <p>Like every represented value it exports only a bounded opaque host display; it never exposes
 * the underlying foreign target through Truffle interop.
 */
@ExportLibrary(InteropLibrary.class)
public final class ProtosRawForeignValue
        implements ProtosRepresentedValue, ProtosForeignProjectedReceiver {
    private static final int IDENTITY_FAMILY = 36;

    /** Provider-side state of one raw reference; never guest-visible. */
    public interface Handle {
        /** The exact session binding of the admission; compared by identity only. */
        Object sessionGenerationForRuntime();

        /**
         * Provider-defined stable foreign identity key whose {@code equals}/{@code hashCode}
         * denote the foreign identity, or null when the provider has no stable identity.
         */
        Object stableIdentityKeyForRuntime();

        Optional<ProtosSlotLookupResult> lookupForRuntime(
                ProtosRawForeignValue receiver, String name, ProtosPrelude prelude);
    }

    private final Handle handle;
    private final int identityHash;

    public ProtosRawForeignValue(Handle handle) {
        this.handle = Objects.requireNonNull(handle, "handle");
        Object generation =
                Objects.requireNonNull(handle.sessionGenerationForRuntime(), "session generation");
        Object key = handle.stableIdentityKeyForRuntime();
        this.identityHash =
                key == null
                        ? System.identityHashCode(this)
                        : key.hashCode() * 31 + System.identityHashCode(generation);
    }

    public Handle handleForRuntime() {
        return handle;
    }

    boolean sameForeignIdentity(ProtosRawForeignValue other) {
        if (this == other) {
            return true;
        }
        Object key = handle.stableIdentityKeyForRuntime();
        return key != null
                && handle.sessionGenerationForRuntime()
                        == other.handle.sessionGenerationForRuntime()
                && key.equals(other.handle.stableIdentityKeyForRuntime());
    }

    long taggedIdentityHash() {
        return (((long) IDENTITY_FAMILY) << 32) ^ Integer.toUnsignedLong(identityHash);
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude prelude) {
        return ProtosObjectValue.rootObject();
    }

    @Override
    public Optional<ProtosSlotLookupResult> lookupForeignMemberForRuntime(
            String name, ProtosPrelude prelude) {
        return handle.lookupForRuntime(this, name, prelude);
    }

    @ExportMessage
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "Object";
    }

    @Override
    public String toString() {
        return "ProtosRawForeignValue";
    }
}

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

import java.util.Objects;
import java.util.Optional;

/**
 * Ordinary slot lookup across runtime representations whose semantic delegation
 * parent is supplied by the source-backed Core prelude.
 */
public final class ProtosValueLookup {
    private ProtosValueLookup() {}

    public static Optional<ProtosSlotLookupResult> lookup(
            Object receiver,
            String name,
            ProtosPrelude prelude) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(name, "name");

        Object current = receiver;
        while (true) {
            if (current instanceof ProtosObjectValue ordinary) {
                Optional<Object> local = ordinary.readLocalSlot(name);
                if (local.isPresent()) {
                    return Optional.of(new ProtosSlotLookupResult(local.orElseThrow(), ordinary));
                }
            }

            Optional<Object> parent = delegationParent(current, prelude);
            if (parent.isEmpty()) {
                return Optional.empty();
            }
            current = parent.orElseThrow();
        }
    }

    /**
     * Returns the semantic immediate delegation parent used by ordinary lookup.
     *
     * <p>The unique root Object has no parent. Represented semantic values delegate through the
     * parent supplied by their representation contract. The prelude may be {@code null} for a
     * representation whose contract does not require a Core prototype.
     */
    public static Optional<Object> delegationParent(
            Object receiver,
            ProtosPrelude prelude) {
        Objects.requireNonNull(receiver, "receiver");

        if (receiver instanceof ProtosObjectValue ordinary) {
            return ordinary.parent();
        }
        if (receiver instanceof ProtosRepresentedValue represented) {
            return Optional.of(
                    Objects.requireNonNull(
                            represented.representedDelegationParent(prelude),
                            "represented delegation parent"));
        }
        throw new UnsupportedOperationException(
                "Standard delegation parent is not implemented for runtime value representation "
                        + receiver.getClass().getName());
    }
}

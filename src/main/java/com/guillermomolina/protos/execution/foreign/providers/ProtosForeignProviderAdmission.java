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

import java.util.Objects;
import java.util.Optional;

/**
 * Single PLAT052 admission owner for foreign provider execution.
 *
 * <p>Every runtime path that would run provider-supplied code (import canonicalization, the
 * Actor-local facade, session acquisition, and compartment opening) passes this gate first; every
 * later D188/D189 provider operation is reachable only through a session binding acquired after
 * it. Admission is decided only from the immutable host-supplied descriptor, by type tests that run
 * no provider code, so a rejected provider has zero provider-side effect:
 *
 * <ul>
 *   <li>{@code UNAVAILABLE} is always rejected;
 *   <li>{@code RESTRICTED_IN_PROCESS} requires an {@link
 *       ProtosForeignProviderEnforcement.InProcessRestriction} mechanism;
 *   <li>{@code STRONGLY_ISOLATED} requires a {@link
 *       ProtosForeignProviderEnforcement.StrongIsolation} mechanism, never an in-process one;
 *   <li>{@code TRUSTED_IN_PROCESS} is the host's explicit choice and carries no mechanism.
 * </ul>
 *
 * A mechanism that claims both kinds, or one attached to a profile that does not require it, is a
 * contradictory configuration and is rejected. Nothing here is guest-visible or mutable.
 */
final class ProtosForeignProviderAdmission {
    private ProtosForeignProviderAdmission() {}

    /** The reason {@code descriptor} is not admitted, or empty when it is. */
    static Optional<String> rejection(ProtosForeignProviderDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        ProtosForeignProviderEnforcement mechanism = descriptor.enforcement().orElse(null);
        boolean restriction =
                mechanism instanceof ProtosForeignProviderEnforcement.InProcessRestriction;
        boolean isolation = mechanism instanceof ProtosForeignProviderEnforcement.StrongIsolation;
        String id = descriptor.id().value();
        if (restriction && isolation) {
            return Optional.of("foreign provider enforcement is ambiguous: " + id);
        }
        return switch (descriptor.profile()) {
            case UNAVAILABLE -> Optional.of("foreign provider is unavailable: " + id);
            case TRUSTED_IN_PROCESS ->
                    mechanism == null
                            ? Optional.empty()
                            : Optional.of("trusted foreign provider carries an enforcement: " + id);
            case RESTRICTED_IN_PROCESS ->
                    restriction
                            ? Optional.empty()
                            : Optional.of("restricted foreign provider lacks enforcement: " + id);
            case STRONGLY_ISOLATED ->
                    isolation
                            ? Optional.empty()
                            : Optional.of("isolated foreign provider lacks isolation: " + id);
        };
    }

    /** Fails closed before any provider hook unless {@code descriptor} is admitted. */
    static void require(ProtosForeignProviderDescriptor descriptor) {
        Optional<String> rejection = rejection(descriptor);
        if (rejection.isPresent()) {
            throw new IllegalStateException(rejection.get());
        }
    }

    /**
     * Opens the compartment of an admitted provider: through its enforcement mechanism, or
     * directly from the factory only for a host-trusted provider.
     */
    static ProtosForeignProviderCompartment openCompartment(
            ProtosForeignProviderDescriptor descriptor) {
        require(descriptor);
        ProtosForeignProviderCompartment compartment =
                descriptor.enforcement().isPresent()
                        ? descriptor
                                .enforcement()
                                .get()
                                .openConfinedCompartment(descriptor.factory())
                        : descriptor.factory().openCompartment();
        return Objects.requireNonNull(compartment, "foreign provider compartment");
    }
}

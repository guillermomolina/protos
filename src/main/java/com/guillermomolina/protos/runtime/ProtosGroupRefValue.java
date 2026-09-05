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
import java.util.UUID;

/**
 * Opaque identity-bearing GroupRef communication capability.
 *
 * <p>The semantic reference identity is independent of the target Group identity and of the
 * physical wrapper. Actor-boundary rematerialization therefore creates a fresh wrapper while
 * preserving the exact same semantic GroupRef identity, Group target identity, and effective
 * restriction descriptor. No mutable Group membership, controller, routing-cache, or Authority
 * state is represented by this value.
 *
 * <p>I011-12 deliberately provides only the identity/transfer substrate. Group acquisition,
 * membership, routing, send/request delivery, uncertainty, and distributed transport are layered
 * later and must not infer broader authority from possession of this capability.
 */
public final class ProtosGroupRefValue implements ProtosRepresentedValue {
    private final ProtosObjectValue prototype;
    private final Descriptor descriptor;

    private ProtosGroupRefValue(ProtosObjectValue prototype, Descriptor descriptor) {
        this.prototype = Objects.requireNonNull(prototype, "prototype");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    /**
     * Runtime acquisition boundary for one new semantic GroupRef.
     *
     * <p>Two calls are independent acquisitions even when they name the same Group and effective
     * restriction descriptor; they therefore receive distinct semantic GroupRef identities.
     */
    static ProtosGroupRefValue acquireForRuntime(
            ProtosObjectValue prototype, UUID groupIdentity, UUID restrictionIdentity) {
        return new ProtosGroupRefValue(
                prototype,
                new Descriptor(
                        UUID.randomUUID(),
                        Objects.requireNonNull(groupIdentity, "groupIdentity"),
                        Objects.requireNonNull(restrictionIdentity, "restrictionIdentity")));
    }

    boolean denotesSameReference(ProtosGroupRefValue other) {
        return other != null && descriptor.semanticIdentity.equals(other.descriptor.semanticIdentity);
    }

    UUID semanticIdentityForRuntime() {
        return descriptor.semanticIdentity;
    }

    UUID groupIdentityForRuntime() {
        return descriptor.groupIdentity;
    }

    UUID restrictionIdentityForRuntime() {
        return descriptor.restrictionIdentity;
    }

    /** Rematerializes the same capability without copying Group/control-plane mutable state. */
    ProtosGroupRefValue rematerializeForActorTransfer() {
        return new ProtosGroupRefValue(prototype, descriptor);
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude ignored) {
        return prototype;
    }

    /** Immutable runtime descriptor; it contains identities only, never mutable Group state. */
    private static final class Descriptor {
        private final UUID semanticIdentity;
        private final UUID groupIdentity;
        private final UUID restrictionIdentity;

        private Descriptor(
                UUID semanticIdentity, UUID groupIdentity, UUID restrictionIdentity) {
            this.semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            this.groupIdentity = Objects.requireNonNull(groupIdentity, "groupIdentity");
            this.restrictionIdentity =
                    Objects.requireNonNull(restrictionIdentity, "restrictionIdentity");
        }
    }
}

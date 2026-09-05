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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Opaque identity-bearing GroupRef communication capability.
 *
 * <p>The semantic reference identity is independent of the target Group identity and of the
 * physical wrapper. Actor-boundary rematerialization therefore creates a fresh wrapper while
 * preserving the exact same semantic GroupRef identity, Group target identity, and effective
 * restriction descriptor. No mutable Group membership, controller, routing-cache, or Authority
 * state is exposed as Protos-visible state; a runtime-bound descriptor may retain only an opaque
 * handle to the same target Group so later routing can consult its current state.
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

    /** Runtime acquisition bound to one concrete local ActorGroup target. */
    static ProtosGroupRefValue acquireForRuntime(
            ProtosObjectValue prototype,
            ProtosActorGroupRuntime group,
            UUID restrictionIdentity) {
        Objects.requireNonNull(group, "group");
        return new ProtosGroupRefValue(
                prototype,
                new Descriptor(
                        UUID.randomUUID(),
                        group.groupIdentityForRuntime(),
                        Objects.requireNonNull(restrictionIdentity, "restrictionIdentity"),
                        group));
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

    Optional<ProtosActorGroupRuntime> localGroupForRuntime() {
        return Optional.ofNullable(descriptor.localGroup);
    }

    UUID restrictionIdentityForRuntime() {
        return descriptor.restrictionIdentity;
    }

    /** Starts one local Group-routed send operation over an already-formed logical snapshot. */
    public ProtosGroupSendOperationValue beginSendForRuntime(
            ProtosObjectValue sendOperationPrototype,
            ProtosActorRefValue sender,
            String selector,
            List<Object> snapshot) {
        ProtosActorGroupRuntime group =
                localGroupForRuntime()
                        .orElseThrow(() -> new IllegalStateException("GroupRef has no local routing target"));
        return ProtosGroupSendOperationValue.begin(
                sendOperationPrototype, this, group, sender, selector, snapshot);
    }

    /** Starts one local Group-routed request/reply operation over the original snapshot. */
    public ProtosFutureValue beginRequestForRuntime(
            ProtosActorRefValue sender,
            String selector,
            List<Object> snapshot,
            ProtosActivation callerActivation) {
        ProtosActorGroupRuntime group =
                localGroupForRuntime()
                        .orElseThrow(() -> new IllegalStateException("GroupRef has no local routing target"));
        return ProtosGroupRequest.begin(this, group, sender, selector, snapshot, callerActivation);
    }

    /** Rematerializes the same capability without copying Group/control-plane mutable state. */
    ProtosGroupRefValue rematerializeForActorTransfer() {
        return new ProtosGroupRefValue(prototype, descriptor);
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude ignored) {
        return prototype;
    }

    /** Immutable capability descriptor; the optional Group handle is opaque runtime routing state. */
    private static final class Descriptor {
        private final UUID semanticIdentity;
        private final UUID groupIdentity;
        private final UUID restrictionIdentity;

        private final ProtosActorGroupRuntime localGroup;

        private Descriptor(
                UUID semanticIdentity, UUID groupIdentity, UUID restrictionIdentity) {
            this(semanticIdentity, groupIdentity, restrictionIdentity, null);
        }

        private Descriptor(
                UUID semanticIdentity,
                UUID groupIdentity,
                UUID restrictionIdentity,
                ProtosActorGroupRuntime localGroup) {
            this.semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            this.groupIdentity = Objects.requireNonNull(groupIdentity, "groupIdentity");
            this.restrictionIdentity =
                    Objects.requireNonNull(restrictionIdentity, "restrictionIdentity");
            if (localGroup != null
                    && !this.groupIdentity.equals(localGroup.groupIdentityForRuntime())) {
                throw new IllegalArgumentException("GroupRef target identity mismatch");
            }
            this.localGroup = localGroup;
        }
    }
}

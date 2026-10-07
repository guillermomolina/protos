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

/**
 * Internal detached transfer node for one semantic-transfer value in flight (PLAT051-A2).
 *
 * <p>A record is produced by the source stage and consumed by the destination stage of
 * {@link ProtosSemanticTransferFamily}. It is not a Protos value: it has no slots, delegation,
 * mutation state or callable surface, and it never reaches guest code. Isolation transfer replaces
 * every record with a materialized destination value before the transferred graph is observable.
 * The record is immutable and holds only the exact authorized family and an inert payload, so the
 * same record may be materialized independently by several destinations.
 *
 * <p>Holding a record confers no authority: the destination re-checks that its own Prelude
 * authorizes the exact family descriptor before materializing.
 */
public final class ProtosSemanticTransferRecord {
    private final ProtosSemanticTransferFamily family;
    private final ProtosSemanticTransferPayload payload;

    private ProtosSemanticTransferRecord(
            ProtosSemanticTransferFamily family, ProtosSemanticTransferPayload payload) {
        this.family = family;
        this.payload = payload;
    }

    /**
     * Source stage: validates and extracts {@code value} synchronously in the transferring domain.
     *
     * <p>Answers {@code null} when the value must be rejected as non-transferable: {@code source}
     * does not authorize this exact family, the value is not FROZEN, or extraction fails or yields
     * a payload the family does not accept. Nothing is materialized and no guest code runs.
     */
    public static ProtosSemanticTransferRecord prepareForRuntime(
            ProtosSemanticTransferValue value, ProtosPrelude source) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(source, "source");
        ProtosSemanticTransferFamily family = value.family();
        if (!source.authorizesSemanticTransferFamilyForRuntime(family) || !value.isFrozen()) {
            return null;
        }
        ProtosSemanticTransferPayload payload;
        try {
            payload = family.extract(value);
            if (payload == null || !family.acceptsPayload(payload)) {
                return null;
            }
        } catch (RuntimeException rejected) {
            return null;
        }
        return new ProtosSemanticTransferRecord(family, payload);
    }

    /**
     * Destination stage: materializes a fresh FROZEN value of the recorded family inside the
     * execution domain of {@code destination}, before any guest code observes it.
     *
     * <p>A source-validated record must materialize; any failure here is an internal
     * implementation inconsistency and is reported as {@link IllegalStateException}, never as a
     * guest-visible transfer error.
     */
    public ProtosSemanticTransferValue materializeForRuntime(ProtosActivation destination) {
        Objects.requireNonNull(destination, "destination");
        ProtosPrelude prelude =
                destination.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "semantic transfer materialization requires a Core prelude"));
        if (!prelude.authorizesSemanticTransferFamilyForRuntime(family)) {
            throw inconsistency("destination does not authorize the recorded family", null);
        }
        ProtosSemanticTransferValue materialized;
        try {
            materialized =
                    family.materialize(
                            payload, new ProtosSemanticTransferDestination(family, destination));
        } catch (RuntimeException failure) {
            throw inconsistency("materialization of an accepted payload failed", failure);
        }
        if (materialized == null || materialized.family() != family || !materialized.isFrozen()) {
            throw inconsistency("materialization did not produce a FROZEN value of its family", null);
        }
        return materialized;
    }

    private IllegalStateException inconsistency(String reason, RuntimeException cause) {
        return new IllegalStateException(
                "PLAT051 internal inconsistency for " + family.ownerModule().canonicalId() + ": " + reason,
                cause);
    }
}

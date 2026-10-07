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
 * Trusted descriptor of one Standard Library semantic-value family that may cross isolation
 * boundaries by rematerialization (PLAT051).
 *
 * <p>A family is the explicit semantic-portability contract of one selected Standard Library
 * value family: a source-local extractor reducing a value to an inert
 * {@link ProtosSemanticTransferPayload}, and a destination-local reconstructor building a fresh
 * value from that payload with the destination's own Standard Library implementation. A family is
 * effective only while the transferring {@link ProtosPrelude} authorizes this exact descriptor
 * identity for its owning standard module; that authorization is fixed by Core bootstrap when the
 * Prelude is constructed. Standard Library membership alone never makes a value portable.
 *
 * <p>Only values minted by a family through {@link #newValue} carry it. Guest code cannot mint
 * them: an ordinary object with the same names, slots, shape, delegation parent or tags remains an
 * ordinary object for transfer. Neither the source implementation graph nor any Closure, execution
 * state or authority crosses; the destination value is exactly what the reconstructor builds.
 *
 * <p>The payload is an acyclic inert tree, so a family whose own semantic content is cyclic cannot
 * use this mechanism without an explicit two-phase construction contract.
 */
public abstract class ProtosSemanticTransferFamily {
    private static final String STANDARD_LIBRARY_PREFIX = "std:";

    private final ProtosModuleKey ownerModule;

    protected ProtosSemanticTransferFamily(ProtosModuleKey ownerModule) {
        this.ownerModule = Objects.requireNonNull(ownerModule, "ownerModule");
        if (!ownerModule.canonicalId().startsWith(STANDARD_LIBRARY_PREFIX)) {
            throw new IllegalArgumentException(
                    "semantic transfer family must belong to a Standard Library module: "
                            + ownerModule.canonicalId());
        }
    }

    /** The canonical Standard Library module that owns this family's semantics. */
    public final ProtosModuleKey ownerModule() {
        return ownerModule;
    }

    /**
     * Mints a fresh OPEN value of this family. The family populates and freezes it before
     * publishing it; only FROZEN values are eligible for transfer.
     */
    protected final ProtosSemanticTransferValue newValue(Object parent) {
        return new ProtosSemanticTransferValue(parent, this);
    }

    /**
     * Source-local extraction of the complete semantic content of a FROZEN value of this family.
     * Must not execute guest code. Answers {@code null} when the value cannot be represented.
     */
    protected abstract ProtosSemanticTransferPayload extract(ProtosSemanticTransferValue value);

    /**
     * Destination-local reconstruction of a fresh FROZEN value of this family using only the
     * destination Prelude's own Standard Library authority. Answers {@code null} when the payload
     * is malformed.
     */
    protected abstract ProtosSemanticTransferValue reconstruct(
            ProtosSemanticTransferPayload payload, ProtosPrelude destination);

    /**
     * The single fail-closed transfer step shared by Actor and P transfer.
     *
     * <p>Answers a fresh destination value, or {@code null} when the value must be rejected as
     * non-transferable: the destination does not authorize this exact family, the value is not
     * FROZEN, extraction or reconstruction fails, or the reconstructed value is not a fresh FROZEN
     * value of the same family. There is no fallback to copying the source graph.
     */
    public static ProtosSemanticTransferValue rematerializeForRuntime(
            ProtosSemanticTransferValue value, ProtosPrelude destination) {
        ProtosSemanticTransferFamily family = value.family();
        if (!destination.authorizesSemanticTransferFamilyForRuntime(family) || !value.isFrozen()) {
            return null;
        }
        ProtosSemanticTransferValue rebuilt;
        try {
            ProtosSemanticTransferPayload payload = family.extract(value);
            if (payload == null) {
                return null;
            }
            rebuilt = family.reconstruct(payload, destination);
        } catch (RuntimeException rejected) {
            return null;
        }
        if (rebuilt == null
                || rebuilt == value
                || rebuilt.family() != family
                || !rebuilt.isFrozen()) {
            return null;
        }
        return rebuilt;
    }
}

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Run-owned immutable index of verified external package custody (PLAT012).
 *
 * <p>The index is keyed by the complete {@link ProtosExactExternalPackageIdentity}, never by
 * PackageId, alias, export, version, revision, ContentIdentity alone, locator or path. Distinct
 * logical identities stay distinct even when their content is identical. Workspace packages have no
 * entry.
 *
 * <p>Ownership: before {@link #reconcile} succeeds the caller owns every supplied custody, and a
 * failed reconciliation returns no scope and closes nothing. After success this scope owns exactly
 * the reconciled custodies and {@link #close()} releases each one exactly once. Planning remains a
 * borrower of the same custodies and must finish before they are handed to this scope. Where the
 * scope is created and closed inside the public-run lifecycle is not decided here.
 */
final class ProtosExternalPackageResourceScope implements AutoCloseable {
    private final Map<ProtosExactExternalPackageIdentity, ProtosCapturedFilesystemCustody>
            custodies;
    private boolean closed;

    private ProtosExternalPackageResourceScope(
            Map<ProtosExactExternalPackageIdentity, ProtosCapturedFilesystemCustody> custodies) {
        this.custodies = custodies;
    }

    /**
     * Reconciles the detached plan's external identities exactly 1:1 with verified custody.
     *
     * <p>The required set is derived only from
     * {@link ProtosPackageExecutionPlanV2.ExternalPackage#identity()}; the supplied set only from
     * {@link ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage#identity()}. Missing,
     * extra, duplicate or mismatched identities, and one custody supplied for several identities,
     * fail closed.
     */
    static ProtosExternalPackageResourceScope reconcile(
            ProtosPackageExecutionPlanV2 plan,
            List<ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage> verified)
            throws IOException {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(verified, "verified");

        Set<ProtosExactExternalPackageIdentity> required = new HashSet<>();
        for (ProtosPackageExecutionPlanV2.PackageNode node : plan.packages()) {
            if (node instanceof ProtosPackageExecutionPlanV2.ExternalPackage external
                    && !required.add(external.identity())) {
                throw new IOException("duplicate exact external identity in execution plan");
            }
        }

        Map<ProtosExactExternalPackageIdentity, ProtosCapturedFilesystemCustody> supplied =
                new HashMap<>();
        IdentityHashMap<ProtosCapturedFilesystemCustody, Boolean> seenCustodies =
                new IdentityHashMap<>();
        for (ProtosExternalPackagePlanningPreflight.VerifiedExternalPackage input : verified) {
            Objects.requireNonNull(input, "verified external package");
            ProtosExactExternalPackageIdentity identity = input.identity();
            if (supplied.putIfAbsent(identity, input.custody()) != null) {
                throw new IOException("duplicate verified external package identity");
            }
            if (seenCustodies.put(input.custody(), Boolean.TRUE) != null) {
                throw new IOException(
                        "one verified custody supplied for several external identities");
            }
            if (!required.contains(identity)) {
                throw new IOException(
                        "verified custody has no exact external node in execution plan");
            }
        }
        if (supplied.size() != required.size()) {
            throw new IOException("external execution plan node has no exact verified custody");
        }

        return new ProtosExternalPackageResourceScope(Collections.unmodifiableMap(supplied));
    }

    /** Whether this scope owns verified custody for exactly {@code identity}. */
    synchronized boolean contains(ProtosExactExternalPackageIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        requireOpen();
        return custodies.containsKey(identity);
    }

    /**
     * Reads one immutable package-relative regular resource from the custody of exactly
     * {@code identity}. The lookup is expected-O(1); the read itself does not hold the scope lock.
     *
     * @throws IllegalStateException when this scope is closed
     * @throws IllegalArgumentException when {@code identity} is not in this scope
     * @throws IOException when the name does not denote an exact captured regular resource
     */
    byte[] readResource(
            ProtosExactExternalPackageIdentity identity, ProtosPackageResourceName name)
            throws IOException {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(name, "name");
        ProtosCapturedFilesystemCustody custody;
        synchronized (this) {
            requireOpen();
            custody = custodies.get(identity);
        }
        if (custody == null) {
            throw new IllegalArgumentException("unknown exact external package identity");
        }
        return custody.readResource(name);
    }

    /** Closes every owned custody exactly once; later calls are no-ops. */
    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        RuntimeException failure = null;
        for (ProtosCapturedFilesystemCustody custody : new ArrayList<>(custodies.values())) {
            try {
                custody.close();
            } catch (RuntimeException closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("external package resource scope is closed");
        }
    }
}

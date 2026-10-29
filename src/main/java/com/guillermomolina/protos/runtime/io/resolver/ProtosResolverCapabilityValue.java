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

import com.guillermomolina.protos.runtime.io.ProtosResolverBackend;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.util.Objects;
import java.util.Optional;

/**
 * JVM/Truffle represented wrapper for one host-granted D223-A1 Resolver capability.
 *
 * <p>The standard {@code Resolver} prototype is ordinary frozen, source-backed Protos state and
 * carries no authority. This wrapper delegates to the exact canonical Resolver prototype of its
 * provisioning Prelude and retains one opaque host name-service backend outside the Protos slot
 * graph; nothing about the backend is guest-visible. It is not derived from, and grants no,
 * Network authority. Actor and P transfer reject it and every ordinary graph whose delegation
 * chain reaches it.
 *
 * <p>The whole-resource lifecycle is created lazily by the first lookup or close and is bound to
 * that caller's Actor execution domain. A Resolver is non-transferable, so every later use comes
 * from the same domain; a foreign-domain use is a runtime invariant violation.
 *
 * <p>Host custody is separate from that guest lifecycle. An explicit grant binds the capability to
 * exactly one Process incarnation before its initial module executes (§14.9: a granted Resolver
 * lives at most as long as that Process); it can never be granted again, not even after
 * retirement. {@link #retireForRuntime()} is the single host-side path that closes the backend:
 * it needs no guest Activation, works whether or not the lifecycle was ever created, settles no
 * guest Future, and is idempotent, so the backend is closed exactly once whichever of public close,
 * Process termination or host closure comes first.
 */
@ExportLibrary(InteropLibrary.class)
public final class ProtosResolverCapabilityValue implements ProtosRepresentedValue {
    private final ProtosObjectValue prototype;
    private final ProtosResolverBackend backend;
    private ProtosIoLifecycle lifecycle;
    private ProtosProcessRuntime owner;
    private boolean retired;

    public ProtosResolverCapabilityValue(ProtosPrelude prelude, ProtosResolverBackend backend) {
        this.prototype = Objects.requireNonNull(prelude, "prelude").resolverPrototypeForRuntime();
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    /** Internal opaque host backend; only the Resolver bridge and flow consult it. */
    public ProtosResolverBackend backendForRuntime() {
        return backend;
    }

    /**
     * Associates this capability with the Process incarnation it is granted to. Called once, by
     * that Process, before any guest code can observe the grant.
     *
     * @throws IllegalArgumentException when the capability is already owned or retired
     */
    synchronized void bindOwnerForRuntime(ProtosProcessRuntime process) {
        Objects.requireNonNull(process, "process");
        if (retired) {
            throw new IllegalArgumentException("Resolver capability is retired");
        }
        if (owner != null) {
            throw new IllegalArgumentException("Resolver capability is already granted to a Process");
        }
        owner = process;
    }

    /** The Process incarnation this capability is granted to, if any. */
    public synchronized Optional<ProtosProcessRuntime> ownerForRuntime() {
        return Optional.ofNullable(owner);
    }

    /**
     * Withdraws the host authority: no new name-service work can start, queued host requests are
     * dropped undelivered, and a lookup already running in the host name service is not waited
     * for (its late completion is discarded by the lookup flow). Closes no Network, connection,
     * HTTP, TLS or other Resolver resource. Idempotent.
     */
    public void retireForRuntime() {
        synchronized (this) {
            if (retired) {
                return;
            }
            retired = true;
        }
        backend.close();
    }

    public synchronized boolean retiredForRuntime() {
        return retired;
    }

    /** The lifecycle of this capability, created for the first using Actor domain. */
    public synchronized ProtosIoLifecycle lifecycleForRuntime(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        if (lifecycle == null) {
            lifecycle =
                    new ProtosIoLifecycle(
                            this,
                            activation.prelude().orElseThrow().futurePrototype(),
                            activation.executionDomain(),
                            completion -> {
                                retireForRuntime();
                                completion.succeeded();
                            });
        }
        return lifecycle;
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude ignored) {
        return prototype;
    }

    @ExportMessage
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "Object";
    }
}

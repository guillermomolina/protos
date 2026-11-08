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

import com.guillermomolina.protos.runtime.io.ProtosEntropyBackend;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.util.Objects;
import java.util.Optional;

/**
 * D243-C host-granted Entropy authority.
 *
 * <p>The delegation prototype contains no authority. The independent
 * backend is opaque and never appears in the Protos slot graph.
 * A grant belongs to at most one Process incarnation for its lifetime.
 */
@ExportLibrary(InteropLibrary.class)
public final class ProtosEntropyCapabilityValue
        implements ProtosRepresentedValue {

    private final ProtosObjectValue prototype;
    private final ProtosEntropyBackend backend;
    private ProtosIoLifecycle lifecycle;
    private ProtosProcessRuntime owner;
    private boolean retired;

    public ProtosEntropyCapabilityValue(
            ProtosPrelude prelude, ProtosEntropyBackend backend) {
        this.prototype = Objects.requireNonNull(
                Objects.requireNonNull(prelude, "prelude")
                        .entropyPrototypeForRuntime(),
                "Entropy prototype");
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public ProtosEntropyBackend backendForRuntime() {
        return backend;
    }

    /** One-time binding before RootActor initialization. */
    synchronized void bindOwnerForRuntime(ProtosProcessRuntime process) {
        Objects.requireNonNull(process, "process");
        if (retired) {
            throw new IllegalArgumentException(
                    "Entropy capability is retired");
        }
        if (owner != null) {
            throw new IllegalArgumentException(
                    "Entropy capability is already granted to a Process");
        }
        owner = process;
    }

    public synchronized Optional<ProtosProcessRuntime> ownerForRuntime() {
        return Optional.ofNullable(owner);
    }

    /**
     * Host-only authority retirement. Does not run guest code or
     * settle guest Futures. Exactly this backend is retired.
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

    /** Lazily binds the resource lifecycle to its owning Actor domain. */
    public synchronized ProtosIoLifecycle lifecycleForRuntime(
            ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        if (lifecycle == null) {
            lifecycle = new ProtosIoLifecycle(
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
    String toDisplayString(
            @SuppressWarnings("unused") boolean allowSideEffects) {
        return "Object";
    }
}

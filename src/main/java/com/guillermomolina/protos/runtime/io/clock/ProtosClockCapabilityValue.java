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

import com.guillermomolina.protos.runtime.io.ProtosClockBackend;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.util.Objects;
import java.util.Optional;

/** Opaque host authority, bound at most once and never revived after host retirement. */
@ExportLibrary(InteropLibrary.class)
public final class ProtosClockCapabilityValue implements ProtosRepresentedValue {
    private final ProtosObjectValue prototype;
    private final ProtosClockBackend backend;
    private volatile ProtosProcessRuntime owner;
    private volatile boolean retired;

    public ProtosClockCapabilityValue(ProtosPrelude prelude, ProtosClockBackend backend) {
        prototype = Objects.requireNonNull(prelude, "prelude").clockPrototypeForRuntime();
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    /** One-time host binding, before any initial guest expression. */
    synchronized void bindOwnerForRuntime(ProtosProcessRuntime process) {
        Objects.requireNonNull(process, "process");
        if (retired || owner != null) {
            throw new IllegalArgumentException("Clock capability is retired or already owned");
        }
        owner = process;
    }

    public Optional<ProtosProcessRuntime> ownerForRuntime() {
        return Optional.ofNullable(owner);
    }

    public ProtosClockBackend backendForRuntime() {
        return backend;
    }

    /** Shares the per-grant observation admission monitor; no provider starts after retirement. */
    public synchronized void retireForRuntime() {
        retired = true;
    }

    public boolean retiredForRuntime() {
        return retired;
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

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

import java.util.List;
import java.util.Objects;

/**
 * Provider-facing D189 capability of one Protos Closure handed to one synchronous foreign
 * operation.
 *
 * <p>It denotes the exact Protos value that was passed: invocation is ordinary Protos invocation
 * of that value, never a second Closure. The capability is live only during the dynamic extent of
 * the receiving operation ({@link ProtosForeignCallbackScope}); afterwards it is inert and roots
 * nothing. Its only operation is {@link #invoke}: provider code never receives the Closure, an
 * activation, a Task, an Actor, or a Process.
 */
final class ProtosForeignCallback {
    private final ProtosForeignCallbackScope scope;
    /** The exact passed value; cleared when the receiving operation leaves. */
    private Object callable;

    ProtosForeignCallback(ProtosForeignCallbackScope scope, Object callable) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.callable = Objects.requireNonNull(callable, "callable");
    }

    /**
     * Synchronously invokes the Protos value with provider values as positional arguments, each
     * admitted exactly like any other provider value, and returns the lossless outbound
     * projection of its normal result.
     *
     * <p>Throws {@link Rejection} before any Protos code runs when the capability expired, the
     * calling thread is not the one executing the receiving operation, or a governing session,
     * Task, or Actor lifetime ended; and after a normal result that has no lossless projection. A
     * Protos Error or control transfer leaving the invocation is thrown as an opaque carrier: if
     * the provider lets that exact carrier leave the receiving operation, Protos resumes the
     * original outcome; anything else the operation raises is a foreign failure.
     */
    ProtosForeignArgument invoke(List<?> foreignArguments) throws Exception {
        Objects.requireNonNull(foreignArguments, "foreignArguments");
        return scope.invoke(this, foreignArguments);
    }

    Object callableOrNullForRuntime() {
        return callable;
    }

    void expireForRuntime() {
        callable = null;
    }

    /** A callback invocation the runtime refused; reported only to the foreign caller. */
    static final class Rejection extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Rejection(String reason) {
            super(reason, null, false, false);
        }
    }
}

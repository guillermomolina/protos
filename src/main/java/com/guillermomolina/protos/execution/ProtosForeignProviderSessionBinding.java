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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime-owned binding of one provider session to its exact Actor/provider pair (PLAT053).
 *
 * <p>Object identity of this binding is the session generation: a later slice that projects raw
 * foreign values or admits callbacks records the binding and checks {@link #isOpenForRuntime()}
 * instead of rebinding to a replacement session. A closed binding never reopens. The binding is
 * internal machinery and is never guest-visible.
 */
final class ProtosForeignProviderSessionBinding {
    private final ProtosForeignProviderId providerId;
    private final ProtosForeignProviderSession session;
    private final AtomicBoolean open = new AtomicBoolean(true);

    ProtosForeignProviderSessionBinding(
            ProtosForeignProviderId providerId, ProtosForeignProviderSession session) {
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.session = Objects.requireNonNull(session, "session");
    }

    ProtosForeignProviderId providerId() {
        return providerId;
    }

    boolean isOpenForRuntime() {
        return open.get();
    }

    /** Returns the live provider session, failing closed once the binding was closed. */
    ProtosForeignProviderSession sessionForRuntime() {
        if (!open.get()) {
            throw new IllegalStateException(
                    "foreign provider session is closed: " + providerId.value());
        }
        return session;
    }

    /** Marks the binding closed before closing the provider session; repeated calls do nothing. */
    void closeForRuntime() {
        if (open.compareAndSet(true, false)) {
            session.close();
        }
    }
}

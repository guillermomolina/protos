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

import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.util.Objects;
import java.util.Optional;

/**
 * Actor-local Protos module facade over a provider-acquired foreign target (D188, MODULES.md
 * "Foreign module instances").
 *
 * <p>The facade is an ordinary identity-bearing Protos object and the module instance cached in
 * the Actor module cache. Its private attachment is never a guest slot; member projection belongs
 * to later slices. The attachment is set once, only by a successful initialization, and never
 * rebound: a facade whose initialization failed stays unattached and remains an ordinary object.
 * Like the Actor module cache that owns it, the facade is only mutated by its owning Actor.
 */
final class ProtosForeignModuleFacadeValue extends ProtosObjectValue {
    private Attachment attachment;

    ProtosForeignModuleFacadeValue() {
        super(ProtosObjectValue.rootObject());
    }

    void attachForRuntime(Attachment acquired) {
        Objects.requireNonNull(acquired, "acquired");
        if (attachment != null) {
            throw new IllegalStateException("foreign module facade is already attached");
        }
        attachment = acquired;
    }

    Optional<Attachment> attachmentForRuntime() {
        return Optional.ofNullable(attachment);
    }

    /**
     * Provider target privately bound to a facade. The session binding identity is the session
     * generation; its liveness is {@link ProtosForeignProviderSessionBinding#isOpenForRuntime()}.
     */
    record Attachment(
            ProtosForeignProviderSessionBinding session, String canonicalTarget, Object target) {
        Attachment {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(canonicalTarget, "canonicalTarget");
            Objects.requireNonNull(target, "target");
        }

        ProtosForeignProviderId providerId() {
            return session.providerId();
        }
    }
}

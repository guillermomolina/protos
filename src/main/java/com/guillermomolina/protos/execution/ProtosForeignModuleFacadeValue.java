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

import com.guillermomolina.protos.runtime.ProtosForeignProjectedReceiver;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSlotLookupResult;
import java.util.Objects;
import java.util.Optional;

/**
 * Actor-local Protos module facade over a provider-acquired foreign target (D188, MODULES.md
 * "Foreign module instances").
 *
 * <p>The facade is an ordinary identity-bearing Protos object and the module instance cached in
 * the Actor module cache. Its private attachment is never a guest slot. Member lookup is ordinary
 * first, over the facade's own slots and delegation chain; only after a miss does the shared D188
 * projection of the attached target apply ({@link ProtosForeignProjectedOperations}). The
 * attachment is set once, only by a successful initialization, and never rebound: a facade whose
 * initialization failed stays unattached and behaves as an ordinary object. Like the Actor module
 * cache that owns it, the facade is only mutated by its owning Actor, and it never crosses an Actor
 * or P boundary.
 */
final class ProtosForeignModuleFacadeValue extends ProtosObjectValue
        implements ProtosForeignProjectedReceiver {
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

    @Override
    public Optional<ProtosSlotLookupResult> lookupForeignMemberForRuntime(
            String name, ProtosPrelude prelude) {
        return ProtosForeignProjectedOperations.lookupFacade(
                this, attachment == null ? null : attachment.handle(), name, prelude);
    }

    /**
     * Provider target privately bound to a facade through the shared D188 handle. The session
     * binding identity is the session generation; its liveness is {@link
     * ProtosForeignProviderSessionBinding#isOpenForRuntime()}.
     */
    record Attachment(String canonicalTarget, ProtosForeignHandle handle) {
        Attachment {
            Objects.requireNonNull(canonicalTarget, "canonicalTarget");
            Objects.requireNonNull(handle, "handle");
        }

        ProtosForeignProviderSessionBinding session() {
            return handle.session();
        }

        Object target() {
            return handle.target();
        }

        ProtosForeignProviderId providerId() {
            return handle.session().providerId();
        }
    }
}

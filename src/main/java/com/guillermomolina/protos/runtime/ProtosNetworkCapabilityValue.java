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
 * JVM/Truffle represented wrapper for one host-provisioned Network authority.
 *
 * <p>The standard {@code Network} prototype is ordinary frozen Protos state and carries no
 * authority. This wrapper retains one opaque host-selected authority target outside the ordinary
 * Protos slot graph and delegates lookup to the exact canonical Network prototype belonging to the
 * provisioning prelude. Actor and P transfer deliberately reject this wrapper and any ordinary
 * graph whose delegation chain reaches it under the initial D047 contract.
 *
 * <p>The authority target has no Protos-visible representation or identity. I028-B2 intentionally
 * defines no TCP backend interface; later I028 slices may interpret the target behind the same
 * represented capability without changing Protos semantics.
 */
public final class ProtosNetworkCapabilityValue implements ProtosRepresentedValue {
    private final ProtosObjectValue prototype;
    private final Object authorityTarget;

    public ProtosNetworkCapabilityValue(ProtosPrelude prelude, Object authorityTarget) {
        ProtosPrelude owningPrelude = Objects.requireNonNull(prelude, "prelude");
        this.prototype = owningPrelude.networkPrototype();
        this.authorityTarget = Objects.requireNonNull(authorityTarget, "authorityTarget");
    }

    /** Internal opaque host authority retained for later Network protocol/backend slices. */
    Object authorityTargetForRuntime() {
        return authorityTarget;
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude ignored) {
        return prototype;
    }
}

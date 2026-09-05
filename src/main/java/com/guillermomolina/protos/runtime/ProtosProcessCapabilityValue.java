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
 * Actor-local represented proxy carrying authority into one existing logical Protos Process.
 *
 * <p>The proxy contains no Process-owned mutable Protos state. Actor delegation rematerializes a
 * fresh wrapper that points at the same internal Process authority, so the destination receives no
 * mutable alias to the source wrapper and no authority amplification. The standard Process
 * protocol and RootActor bootstrap provisioning are installed later by I017.
 */
public final class ProtosProcessCapabilityValue implements ProtosRepresentedValue {
    private final ProtosObjectValue prototype;
    private final ProtosProcessRuntime processRuntime;

    ProtosProcessCapabilityValue(
            ProtosObjectValue prototype, ProtosProcessRuntime processRuntime) {
        this.prototype = Objects.requireNonNull(prototype, "prototype");
        this.processRuntime = Objects.requireNonNull(processRuntime, "processRuntime");
    }

    /** Internal authority bridge for the future standard Process protocol. */
    public ProtosProcessRuntime processForRuntime() {
        return processRuntime;
    }

    /**
     * Forms one destination-side Actor proxy to the same logical Process authority.
     *
     * <p>Process proxies do not receive GroupRef-style preserved wrapper identity. The fresh proxy
     * is ordinary Actor-local capability state; only its authority target is preserved.
     */
    ProtosProcessCapabilityValue rematerializeForActorTransfer() {
        return new ProtosProcessCapabilityValue(prototype, processRuntime);
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude ignored) {
        return prototype;
    }
}

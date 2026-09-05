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

/** Actor-local proxy/view for one Process-local standard byte-stream binding. */
public final class ProtosProcessStandardStreamValue implements ProtosRepresentedValue {
    private final ProtosObjectValue prototype;
    private final ProtosProcessStandardStreamBinding binding;
    private final ProtosObjectValue lifecycleIdentity;

    ProtosProcessStandardStreamValue(
            ProtosObjectValue prototype,
            ProtosProcessStandardStreamBinding binding) {
        this.prototype = Objects.requireNonNull(prototype, "prototype");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.lifecycleIdentity =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }

    public ProtosProcessStandardStreamBinding.Direction directionForRuntime() {
        return binding.directionForRuntime();
    }

    public ProtosFutureValue readForRuntime(
            ProtosActivation activation, Object maxBytes) {
        return binding.readForRuntime(this, activation, maxBytes);
    }

    public ProtosFutureValue writeForRuntime(
            ProtosActivation activation, Object bytes) {
        return binding.writeForRuntime(this, activation, bytes);
    }

    ProtosProcessStandardStreamValue rematerializeForActorTransfer() {
        return new ProtosProcessStandardStreamValue(prototype, binding);
    }

    ProtosObjectValue asObjectForLifecycle() {
        return lifecycleIdentity;
    }

    boolean denotesSameBindingForTesting(ProtosProcessStandardStreamValue other) {
        return other != null && binding == other.binding;
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude ignored) {
        return prototype;
    }
}

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

package com.guillermomolina.protos.runtime.io;

import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.util.Objects;

/**
 * Runtime representation marker for one host-provisioned Filesystem authority capability.
 *
 * <p>Core has no required Filesystem prelude binding and ordinary Protos code has no public
 * constructor for this authority. The marker exists only so runtime isolation/transfer machinery
 * can reject accidental copying of the live capability or of descendants whose delegation chain
 * carries that authority.
 */
public final class ProtosFilesystemValue extends ProtosObjectValue {
    /*
     * D241-B: the open flow of this one capability. It is bound exactly once while the host
     * constructs the capability, before guest code can reach it, and is consulted only by the
     * private Core open bridge. It is never a Protos slot and never shared through a registry.
     */
    private ProtosFilesystemOpenFlow openFlow;

    public ProtosFilesystemValue() {
        super(ProtosObjectValue.rootObject());
    }

    /**
     * Runtime-only, one-time binding of this capability's open flow during capability
     * construction. A second binding is rejected: an existing capability's flow is never replaced.
     */
    public synchronized void bindOpenFlowForRuntime(ProtosFilesystemOpenFlow flow) {
        Objects.requireNonNull(flow, "flow");
        if (openFlow != null) {
            throw new IllegalStateException("Filesystem open flow is already bound");
        }
        openFlow = flow;
    }

    /** Runtime-only read of the bound open flow; {@code null} when none was installed. */
    public synchronized ProtosFilesystemOpenFlow openFlowForRuntime() {
        return openFlow;
    }
}

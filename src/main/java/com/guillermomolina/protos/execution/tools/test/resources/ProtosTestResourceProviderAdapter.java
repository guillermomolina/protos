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

import java.util.concurrent.CompletionStage;

/**
 * Private TOOL002/D107 provider mechanism boundary.
 *
 * <p>This Java interface is an in-process adapter shape for the current host. It is deliberately
 * not the persisted provider identity and does not require a provider implementation to remain
 * Java/in-process: a future adapter may bridge to Protos, native code, a local daemon, RPC, a
 * hardened worker or remote execution without changing D098 provider/profile identities.
 */
@FunctionalInterface
interface ProtosTestResourceProviderAdapter {

    /**
     * Starts provisioning for one already-reserved, single-provider attempt group.
     *
     * <p>The returned stage may already be complete for a cheap local provider or may complete
     * asynchronously. Transaction coordination, multi-provider rollback, guest bootstrap and
     * scheduler release are intentionally owned by later TOOL002-I slices.
     */
    CompletionStage<ProtosTestResourceProviderLease> provision(
            ProtosTestResourceProviderRequest request);
}

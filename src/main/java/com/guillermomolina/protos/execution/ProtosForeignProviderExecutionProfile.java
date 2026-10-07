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

/**
 * PLAT052/PLAT053 authority and execution profile of one concrete provider configuration.
 *
 * <p>The profile describes the real provider/library/feature configuration, not merely a language
 * name. Selecting {@link #TRUSTED_IN_PROCESS} is reserved to the host or embedder that supplies the
 * provider registry; it is never selected by an import or by foreign code. Each profile is an
 * enforcement state of {@link ProtosForeignProviderAdmission}, not a description.
 */
enum ProtosForeignProviderExecutionProfile {
    /**
     * In-process execution confined by a provider-specific {@link
     * ProtosForeignProviderEnforcement.InProcessRestriction}; without one, use fails closed.
     */
    RESTRICTED_IN_PROCESS,
    /** In-process execution explicitly trusted by the host or embedder. */
    TRUSTED_IN_PROCESS,
    /**
     * Execution under a provider-specific {@link ProtosForeignProviderEnforcement.StrongIsolation};
     * never silently degraded to in-process execution.
     */
    STRONGLY_ISOLATED,
    /** The provider is known but cannot execute; use fails closed. */
    UNAVAILABLE
}

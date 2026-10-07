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
 * Provider-specific PLAT052 enforcement mechanism through which a non-trusted provider compartment
 * is opened.
 *
 * <p>Foreign execution begins with zero ambient host authority and may hold at most the authority
 * explicitly provisioned to its compartment; I082-F provisions none. The generic runtime cannot
 * prove non-amplification for arbitrary host code, so it never trusts a profile name: a {@link
 * ProtosForeignProviderExecutionProfile#RESTRICTED_IN_PROCESS} provider is admitted only with an
 * {@link InProcessRestriction} and a {@link
 * ProtosForeignProviderExecutionProfile#STRONGLY_ISOLATED} provider only with a {@link
 * StrongIsolation}; anything else fails closed (see {@link
 * ProtosForeignProviderAdmission}). The admitted mechanism, not the runtime, invokes the provider
 * factory, so the compartment exists only inside the confinement the mechanism establishes. The
 * concrete confinement (a restricted Context configuration, an isolate, an external process, or a
 * deliberately inert zero-authority provider) and its proof obligation belong to the concrete
 * provider (PLAT053).
 *
 * <p>The mechanism is fixed with the host-supplied descriptor: no import, dependency, guest, or
 * foreign code can supply, replace, or bypass it. Provider code-loading authority the mechanism
 * needs is implementation-internal, scoped to the already-selected provider artifacts, and never
 * exported to foreign code.
 */
interface ProtosForeignProviderEnforcement {
    /**
     * Opens the Process compartment of {@code factory} inside this mechanism's confinement with no
     * provisioned authority. Called at most once per successful compartment, only after admission,
     * and never while a RuntimeHost is constructed or a Process is hosted.
     */
    ProtosForeignProviderCompartment openConfinedCompartment(ProtosForeignProviderFactory factory);

    /** Non-amplifying in-process confinement required by {@code RESTRICTED_IN_PROCESS}. */
    interface InProcessRestriction extends ProtosForeignProviderEnforcement {}

    /** Physical isolation mechanism required by {@code STRONGLY_ISOLATED}. */
    interface StrongIsolation extends ProtosForeignProviderEnforcement {}
}

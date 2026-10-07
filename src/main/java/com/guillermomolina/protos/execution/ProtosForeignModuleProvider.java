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
 * Provider-neutral foreign-module contract of one routed provider (PLAT053, D188).
 *
 * <p>This contract covers only canonical target resolution and target acquisition. Member access,
 * execution, construction, indexing, iteration, conversion, and callbacks are not part of it.
 */
interface ProtosForeignModuleProvider {
    /**
     * Canonicalizes the exact target spelling that follows the routed import scheme.
     *
     * <p>The result is the provider-defined canonical target identity and becomes part of the
     * canonical foreign {@code ModuleKey}. It must be stable semantic identity: it is computed
     * without a session and must never encode a Polyglot value, Context, session, Actor, provider
     * cache entry, Java object identity, or foreign runtime address. Two spellings of one target
     * must return equal strings.
     */
    String canonicalTarget(String exactTarget) throws Exception;

    /**
     * Acquires and initializes the canonical target inside the importing Actor's live provider
     * session. It runs only after the Actor-local facade was cached as {@code INITIALIZING}; a
     * failure evicts that exact cache record, so provider-private failure state must not make a
     * later retry impossible.
     *
     * @return the non-null provider-acquired target privately attached to the facade
     */
    Object acquireTarget(ProtosForeignProviderSession session, String canonicalTarget)
            throws Exception;

    /**
     * Whether this provider deliberately publishes the facade's {@code call} as the projected
     * execution of an executable target (D188 "Callability and construction"), whose documented
     * provider meaning may be construction. A facade is an ordinary object, so without this
     * publication it inherits the standard {@code Object.call}. Decided by the provider, never by
     * the import specifier.
     */
    default boolean publishesFacadeCall() {
        return false;
    }
}

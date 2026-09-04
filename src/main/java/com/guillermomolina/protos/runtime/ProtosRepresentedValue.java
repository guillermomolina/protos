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

/**
 * Implementation-only bridge for Protos semantic values represented by specialized
 * host objects rather than {@link ProtosObjectValue}.
 *
 * <p>The bridge supplies only the value's immediate Protos delegation parent for
 * ordinary lookup. Implementing this interface does not define semantic-family
 * membership, identity, mutability, receiver-domain compatibility, or any other
 * language-visible property.
 */
interface ProtosRepresentedValue {
    Object representedDelegationParent(ProtosPrelude prelude);

    static ProtosPrelude requirePrelude(ProtosPrelude prelude, String family) {
        if (prelude == null) {
            throw new UnsupportedOperationException(
                    "represented " + family + " lookup requires an owning Core prelude");
        }
        return prelude;
    }
}

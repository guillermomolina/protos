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
 * Internal representation of an ordinary Protos object minted by a trusted
 * {@link ProtosSemanticTransferFamily} (PLAT051).
 *
 * <p>It is not a new Protos value category: slots, delegation, mutation state, identity and every
 * other observable behavior are those of an ordinary object. The only difference is the family
 * reference, which isolation transfer uses to rematerialize the value instead of copying it. Only
 * values that opt in pay for that field; ordinary objects carry nothing extra.
 */
public final class ProtosSemanticTransferValue extends ProtosObjectValue {
    private final ProtosSemanticTransferFamily family;

    ProtosSemanticTransferValue(Object parent, ProtosSemanticTransferFamily family) {
        super(parent);
        this.family = Objects.requireNonNull(family, "family");
    }

    public ProtosSemanticTransferFamily family() {
        return family;
    }
}

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
 * I091 / PLAT056 Candidate C internal capability: the value identity of a rich numeric family
 * whose values are ordinary FROZEN Protos objects minted by a {@link ProtosSemanticTransferFamily}.
 *
 * <p>Such a value keeps ordinary delegation, slots and immutability, and crosses isolation by
 * PLAT051 rematerialization. This capability adds only what a numeric family needs beyond that:
 * non-overridable {@code ===} by family and value, and a coherent identity hash, read from the
 * family-private state without executing guest code.
 *
 * <p>No public family implements this yet. Its semantic families, normalization, cross-family
 * {@code ==} and normal hash belong to D197 and their implementation owner (I090); installing a
 * family is that owner's decision, not a consequence of this capability.
 */
public interface ProtosRichNumericFamily {
    /** Identity-hash family tag; must not collide with any other value-family tag. */
    int identityTag();

    /** Whether two family-private states denote the same value of this family. */
    boolean sameValue(Object leftState, Object rightState);

    /** A hash of {@code state} coherent with {@link #sameValue}. */
    int valueHash(Object state);
}

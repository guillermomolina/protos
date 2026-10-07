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

import java.util.Optional;

/**
 * Receiver whose ordinary member lookup is defined by the D188 foreign-value projection
 * ({@code VALUES_AND_COLLECTIONS.md}, "Foreign Values"): a raw foreign reference or an attached
 * foreign module facade.
 *
 * <p>{@link ProtosValueLookup} consults this hook only when the lookup receiver itself is such a
 * value; the result is never a D013 cached selection, because a faithful foreign-member fallback
 * is a foreign operation rather than a slot dependency. Implementations are runtime machinery and
 * never guest-visible. Values implementing this interface have no automatic Actor or P transfer.
 */
public interface ProtosForeignProjectedReceiver {
    /**
     * Performs the complete D188 member lookup of {@code name} with this value as receiver.
     *
     * @return the selection, or empty for the ordinary missing-member failure
     * @throws ProtosSignalException for an ordinary pre-entry failure or a fresh {@code
     *     ForeignError} after a foreign operation was entered
     */
    Optional<ProtosSlotLookupResult> lookupForeignMemberForRuntime(
            String name, ProtosPrelude prelude);
}

/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS.
 * "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY OF THE
 * LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING THE
 * CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS FILE, A
 * COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */

package com.guillermomolina.protos.analysis;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Editor-neutral acquisition boundary for one exact candidate project root.
 *
 * <p>A candidate path is not project identity. Implementations return an immutable binding only
 * after validating their canonical project authority at exactly that path. Absence, staleness or
 * invalid authority is represented by an empty result rather than project discovery or guessing.
 */
@FunctionalInterface
public interface ProtosProjectBindingProvider {
    Optional<ProtosProjectBinding> acquire(Path candidateRoot);
}

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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;
import java.util.Objects;
import java.util.Optional;

/** Immutable owner of the Truffle language/source identity used for roots from one compilation. */
final class ProtosRootFactory {
    private final ProtosLanguage language;
    private final Source source;

    private ProtosRootFactory(ProtosLanguage language, Source source) {
        this.language = language;
        this.source = source;
    }

    static ProtosRootFactory legacy() {
        return new ProtosRootFactory(null, null);
    }

    static ProtosRootFactory sourceBound(ProtosLanguage language, Source source) {
        return new ProtosRootFactory(
                Objects.requireNonNull(language, "language"),
                Objects.requireNonNull(source, "source"));
    }

    CallTarget createCallTarget(ProtosExpressionNode body) {
        Objects.requireNonNull(body, "body");
        if (language == null) {
            return ProtosExecution.createCallTarget(body);
        }
        return new ProtosRootNode(language, source, body).getCallTarget();
    }

    Optional<ProtosLanguage> language() {
        return Optional.ofNullable(language);
    }

    Optional<Source> source() {
        return Optional.ofNullable(source);
    }
}

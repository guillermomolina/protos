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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;
import java.util.Objects;

/**
 * The genuine Closure of a callback literal that a conditional site normally
 * executes inline, for the rare execution in which the receiver is not a
 * canonical Boolean and ordinary dispatch selects another method.
 *
 * <p>The inline execution keeps the callback's captures as plain locals of
 * the creating frame, so this Closure cannot capture them as cells. It is
 * instead lowered standalone, where every name the callback does not declare
 * is selected by name at run time, and it is created over the creating
 * activation after that activation's frame has been bound to its live
 * frame-backed view. Reads and writes therefore reach the same frame locals
 * as the inline execution, also after the creating activation returns.
 *
 * <p>The standalone root is lowered on first use only; a site whose receiver
 * is always a canonical Boolean never creates it. This holder is an
 * implementation-private constant of the creating root and is never
 * guest-visible.
 */
final class ProtosSelectedCallbackPlan {
    private final CanonicalClosure definition;
    private final ProtosLanguage language;
    private final Source source;
    private volatile ProtosClosureExecutionPlan plan;

    ProtosSelectedCallbackPlan(
            CanonicalClosure definition, ProtosLanguage language, Source source) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.language = Objects.requireNonNull(language, "language");
        this.source = Objects.requireNonNull(source, "source");
    }

    /** A new Closure of the callback created by {@code activation}. */
    @TruffleBoundary
    ProtosClosureValue materialize(ProtosActivation activation) {
        return new ProtosClosureValue(
                definition,
                activation.lexicalEnvironmentForClosureCapture(),
                activation.receiver(),
                activation.methodHome().orElse(null),
                activation.returnHome().orElse(null),
                activation.prelude().orElse(null),
                plan(),
                activation.currentModuleKey().orElse(null));
    }

    private ProtosClosureExecutionPlan plan() {
        ProtosClosureExecutionPlan current = plan;
        if (current == null) {
            synchronized (this) {
                current = plan;
                if (current == null) {
                    current = ProtosClosureExecutionPlan.bytecode(definition, language, source);
                    plan = current;
                }
            }
        }
        return current;
    }
}

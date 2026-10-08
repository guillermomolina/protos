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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.Objects;

/**
 * PLAT054 host entry for one parsed Source: the root a Polyglot {@code Context.eval} executes.
 *
 * <p>Parsing has already completed when this root exists, so a syntax error never reaches it and
 * never bootstraps a Process. Executing it places the compiled module body in the Context's
 * embedded Process ({@link ProtosEmbeddedProcess#evaluate}). Internal runtime parsing never
 * executes this root: {@link ProtosLanguageContext#parsePublic} unwraps it to the {@link
 * #bytecodeTarget} it carries, so module, Core, and driver execution keep the canonical Bytecode
 * root unchanged.
 */
final class ProtosHostEvalRootNode extends RootNode {
    private final RootCallTarget bytecodeTarget;

    ProtosHostEvalRootNode(ProtosLanguage language, RootCallTarget bytecodeTarget) {
        super(language);
        this.bytecodeTarget = Objects.requireNonNull(bytecodeTarget, "bytecodeTarget");
    }

    RootCallTarget bytecodeTarget() {
        return bytecodeTarget;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return evaluate(ProtosLanguageContext.current(this), bytecodeTarget);
    }

    @TruffleBoundary
    private static Object evaluate(ProtosLanguageContext context, RootCallTarget target) {
        return context.embeddedProcessForHostEntry().evaluate(target);
    }

    @Override
    public String getName() {
        return bytecodeTarget.getRootNode().getName();
    }

    @Override
    public boolean isInternal() {
        return true;
    }
}

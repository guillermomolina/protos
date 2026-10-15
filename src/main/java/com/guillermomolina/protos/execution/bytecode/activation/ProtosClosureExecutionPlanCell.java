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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import java.util.Objects;

/**
 * PERF013 Slice A backend-private constant-pool payload for a lowered
 * Closure literal.
 *
 * <p>A Closure lexically reached while an enclosing root's own {@code
 * beginRoot()}/{@code endRoot()} pair is still open has its root nested in
 * that same {@code create()} invocation (shared {@code BytecodeRootNodes}
 * group). {@link com.oracle.truffle.api.nodes.RootNode#getCallTarget()}
 * cannot be called on that root — and therefore no real {@link
 * ProtosClosureExecutionPlan} can be constructed — until the whole group's
 * {@code create()} call returns, but the {@code MaterializeClosure} bytecode
 * referencing this Closure must still be emitted at that exact point in the
 * enclosing root's own instruction stream.
 *
 * <p>This cell is the immutable-once-frozen indirection that resolves that
 * ordering conflict: {@code CanonicalToBytecodeLowerer} emits the cell itself
 * as the bytecode constant immediately, then freezes it with the real plan
 * once the owning group's {@code create()} call has returned (see {@code
 * lowerRoot}). The cell is a backend-private lowering artifact — it is never
 * guest-visible and is always frozen before any guest code belonging to this
 * lowering unit can execute, so it never behaves as a second, mutable
 * execution-plan authority.
 *
 * <p>PERF013 Slice A2: every Closure literal reached by the lowerer —
 * including a parameter default-value Closure — is nested this way; there is
 * no longer an independent, out-of-line construction path.
 */
final class ProtosClosureExecutionPlanCell {
    @CompilationFinal private ProtosClosureExecutionPlan plan;

    static ProtosClosureExecutionPlanCell pendingGroup() {
        return new ProtosClosureExecutionPlanCell();
    }

    void freeze(ProtosClosureExecutionPlan plan) {
        if (this.plan != null) {
            throw new IllegalStateException(
                    "closure execution plan cell already frozen");
        }
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    ProtosClosureExecutionPlan plan() {
        return Objects.requireNonNull(
                plan, "closure execution plan cell resolved before being frozen");
    }
}

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
import com.oracle.truffle.api.RootCallTarget;
import java.util.Objects;

/**
 * PERF013 Slice A3 backend-private constant-pool payload for a lowered
 * object-construction helper root.
 *
 * <p>An object-construction body reached while an enclosing root's own {@code
 * beginRoot()}/{@code endRoot()} pair is still open has its helper root
 * nested in that same {@code create()} invocation (shared {@code
 * BytecodeRootNodes} group), exactly like a nested Closure (see {@link
 * ProtosClosureExecutionPlanCell}). {@link
 * com.oracle.truffle.api.nodes.RootNode#getCallTarget()} cannot be called on
 * that helper root until the whole group's {@code create()} call returns, but
 * the {@code PrepareObjectConstruction} bytecode referencing it must still be
 * emitted at that exact point in the enclosing root's own instruction stream.
 *
 * <p>This cell resolves that ordering conflict the same way {@link
 * ProtosClosureExecutionPlanCell} does: {@code CanonicalToBytecodeLowerer}
 * emits the cell itself as the bytecode constant immediately, then freezes it
 * with the real {@link RootCallTarget} once the owning group's {@code
 * create()} call has returned. The cell is a backend-private lowering
 * artifact — it is never guest-visible and is always frozen before any guest
 * code belonging to this lowering unit can execute, so it never behaves as a
 * second, mutable execution authority.
 *
 * <p>Sharing this physical root group does not make the object-construction
 * helper root a genuine lexical execution context: it is lowered with
 * {@code genuineExecutionContextRoot=false}, exactly as before Slice A3.
 */
final class ProtosObjectBodyTargetCell {
    @CompilationFinal private RootCallTarget target;

    static ProtosObjectBodyTargetCell pendingGroup() {
        return new ProtosObjectBodyTargetCell();
    }

    void freeze(RootCallTarget target) {
        if (this.target != null) {
            throw new IllegalStateException(
                    "object body target cell already frozen");
        }
        this.target = Objects.requireNonNull(target, "target");
    }

    RootCallTarget target() {
        return Objects.requireNonNull(
                target, "object body target cell resolved before being frozen");
    }
}

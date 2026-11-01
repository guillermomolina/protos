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

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.DirectCallNode;

/**
 * I115: CallTarget services of source Closures for the shared dispatcher
 * ({@link ProtosFunctionCallDispatchers}): resolution of the effective
 * Bytecode CallTarget, projection into the entered Context and deferred plan
 * materialization. It prepares no call, activation or ReturnHome and does no
 * {@code call} lookup.
 */
final class ProtosClosureCallPreparation {
    private ProtosClosureCallPreparation() {}

    /**
     * I115 (GraalPy PFunction.getCallTarget): the effective CallTarget of a
     * source Closure in {@code enteredContext}, or null when there is none.
     * A deferred plan is materialized once; a projection is obtained only
     * through the entered Context's executable-plan cache. No activation,
     * prepared call, ReturnHome or {@code call} lookup is involved.
     */
    static RootCallTarget functionCallTarget(
            ProtosClosureValue closure,
            ProtosLanguageContext enteredContext) {
        if (closure == null
                || enteredContext == null
                || closure.nativeBody().isPresent()
                || closure.definition() == null) {
            return null;
        }
        ProtosClosureExecutionPlan plan =
                closure.preparedExecutionPlanOrNullForRuntime();
        if (plan == null) {
            plan = materializedExecutionPlan(closure);
        }
        if (!closure.requiresContextLocalExecutionProjectionForRuntime()
                && plan.language().orElseThrow() == enteredContext.languageForRuntime()) {
            return plan.bytecodeActivationTargetForComposition();
        }
        ProtosClosureExecutionPlan effective =
                enteredContext.bytecodeExecutionPlanForDefinition(
                        closure.definition(), plan);
        return effective == null
                ? null
                : effective.bytecodeActivationTargetForComposition();
    }

    /** The deferred plan's rematerializer is not part of partial evaluation. */
    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private static ProtosClosureExecutionPlan materializedExecutionPlan(
            ProtosClosureValue closure) {
        return closure.executionPlanForRuntimeInvocation();
    }

    static DirectCallNode createDirectCallNodeOrNull(RootCallTarget target) {
        return target == null ? null : DirectCallNode.create(target);
    }
}

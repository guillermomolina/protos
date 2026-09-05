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
import com.guillermomolina.protos.runtime.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosReturnHome;
import java.util.List;
import java.util.Objects;

public final class ProtosClosureInvoker {
    private ProtosClosureInvoker() {}

    public static Object invoke(
            ProtosClosureValue closure,
            List<?> supplied) {
        return invoke(closure, supplied, null);
    }

    public static Object invoke(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation caller) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(supplied, "supplied");
        com.guillermomolina.protos.runtime.ProtosPrelude fallbackPrelude =
                caller == null ? null : caller.prelude().orElse(null);
        java.util.function.Supplier<ProtosActivation> activationFactory =
                () -> caller == null
                        ? ProtosActivation.forClosureInvocation(closure, supplied, fallbackPrelude)
                        : ProtosActivation.forClosureInvocation(
                                closure, supplied, fallbackPrelude, caller.actorModuleState(),
                                caller.currentModuleKey().orElse(null));
        ProtosActivation activation;
        if (caller != null && caller.task().isPresent()) {
            com.guillermomolina.protos.runtime.ProtosTask task = caller.task().orElseThrow();
            activation = task.evaluatorContinuation().invocationActivation(activationFactory);
            activation.attachTask(task);
        } else {
            activation = activationFactory.get();
        }
        ProtosReturnHome returnHome =
                activation.returnHome()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Closure invocation requires a return home"));

        try {
            if (closure.nativeBody().isPresent()) {
                return closure.nativeBody().orElseThrow().execute(activation, supplied);
            }
            ProtosClosureExecutionPlan plan = closure.executionPlan().orElseThrow(
                    () -> new IllegalStateException("Closure invocation requires a prepared execution plan"));
            plan.bind(activation);
            return plan.executeBody(activation);
        } catch (ProtosNonLocalReturnException transfer) {
            if (activation.ownsReturnHome()
                    && transfer.target() == returnHome) {
                return transfer.value();
            }
            throw transfer;
        } catch (ProtosEvaluatorSuspension transfer) {
            throw transfer;
        } catch (ProtosTaskCancellationException transfer) {
            throw transfer;
        } finally {
            if (activation.ownsReturnHome()
                    && returnHome.isActive()
                    && !ProtosEvaluatorBridge.isControlUnwindInProgress(activation)) {
                returnHome.complete();
            }
        }
    }
}

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
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.oracle.truffle.api.CallTarget;
import java.util.Objects;

/**
 * Mechanical execution of one exact entry as a real RootActor-local cooperative task.
 *
 * <p>This class owns no CLI, test, reporting, timeout, worker or scheduling policy. It only drives
 * the supplied Actor execution domain until the supplied root task reaches a semantic terminal
 * state and then returns inert terminal data.
 */
public final class ProtosRootTaskExecution {
    private ProtosRootTaskExecution() {}

    public static ProtosExecutionOutcome execute(
            CallTarget target,
            ProtosActivation activation) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(activation, "activation");

        ProtosActorExecutionDomain domain = activation.executionDomain();
        ProtosTask rootTask =
                domain.createTask(
                        null,
                        null,
                        task -> task.executeProtos(target, activation));

        domain.dispatchUntilTerminal(rootTask, () -> false);

        return switch (rootTask.state()) {
            case COMPLETED ->
                    ProtosExecutionOutcome.completed(
                            rootTask
                                    .result()
                                    .orElseThrow(
                                            () ->
                                                    new IllegalStateException(
                                                            "completed root task has no result")));
            case FAILED -> {
                Object failure =
                        rootTask
                                .failure()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "failed root task has no error"));
                if (!(failure instanceof ProtosObjectValue error)) {
                    throw new IllegalStateException(
                            "root task failed with a non-Protos error value");
                }
                yield ProtosExecutionOutcome.failed(error);
            }
            case CANCELLED -> ProtosExecutionOutcome.cancelled();
            default ->
                    throw new IllegalStateException(
                            "root task returned before reaching a terminal state");
        };
    }
}

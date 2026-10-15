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
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared PERF019 caller-domain completion path for logical-Case facilities. */
final class ProtosLogicalCaseCallerCompletion {
    private ProtosLogicalCaseCallerCompletion() {}

    static void enqueue(
            ProtosActivation caller,
            ProtosFutureValue future,
            Exception hostFailure,
            Supplier<ProtosObjectValue> rematerializer,
            Runnable completionBegin,
            Consumer<Boolean> futureTerminal) {
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(future, "future");
        Objects.requireNonNull(rematerializer, "rematerializer");
        Objects.requireNonNull(completionBegin, "completionBegin");
        Objects.requireNonNull(futureTerminal, "futureTerminal");

        try {
            caller.executionDomain()
                    .enqueueTargetedFutureCompletionForRuntime(
                            future,
                            () -> {
                                completionBegin.run();

                                if (!future.isPending()) {
                                    return false;
                                }

                                boolean transitioned;
                                if (hostFailure != null) {
                                    transitioned =
                                            future.fail(ProtosCoreErrors.newError(caller));
                                } else {
                                    try {
                                        transitioned =
                                                future.resolve(
                                                        Objects.requireNonNull(
                                                                rematerializer.get(),
                                                                "logical Case rematerialization"),
                                                        caller);
                                    } catch (ProtosSignalException signal) {
                                        transitioned = future.fail(signal.error());
                                    }
                                }

                                futureTerminal.accept(transitioned);
                                return transitioned;
                            });
        } catch (IllegalStateException callerTerminated) {
            futureTerminal.accept(future.cancelTerminal());
        }
    }
}

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

import com.guillermomolina.protos.runtime.ProtosProcessExecutionHost;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

final class ProtosTestResourceAttemptTerminalizer {
    private ProtosTestResourceAttemptTerminalizer() {}

    static CompletionStage<ProtosTestResourceAttemptCompletion> finishStarted(
            ProtosProcessRuntime process,
            ProtosExecutionOutcome guestObservation,
            ProtosTestResourceProviderTransaction transaction) {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(guestObservation, "guestObservation");
        Objects.requireNonNull(transaction, "transaction");

        ArrayList<Throwable> infrastructureFailures = new ArrayList<>();

        try {
            process.requestTerminationForRuntime();
            process.awaitTerminationForRuntime();
        } catch (RuntimeException | Error failure) {
            infrastructureFailures.add(failure);
        }

        try {
            ProtosProcessExecutionHost host =
                    process.executionHostForRuntime().orElse(null);
            if (host != null) {
                host.awaitTerminalDispositionForRuntime();
            }
        } catch (RuntimeException | Error failure) {
            infrastructureFailures.add(failure);
        }

        final CompletionStage<Void> cleanupStage;
        try {
            cleanupStage =
                    Objects.requireNonNull(
                            transaction.cleanup(),
                            "provider transaction cleanup stage");
        } catch (RuntimeException | Error failure) {
            infrastructureFailures.add(failure);
            return CompletableFuture.completedFuture(
                    completion(guestObservation, infrastructureFailures));
        }

        return cleanupStage.handle(
                (ignored, cleanupFailure) -> {
                    if (cleanupFailure != null) {
                        infrastructureFailures.add(
                                unwrapCompletionFailure(cleanupFailure));
                    }
                    return completion(guestObservation, infrastructureFailures);
                });
    }

    private static ProtosTestResourceAttemptCompletion completion(
            ProtosExecutionOutcome guestObservation,
            ArrayList<Throwable> infrastructureFailures) {
        return new ProtosTestResourceAttemptCompletion(
                guestObservation,
                infrastructureFailures,
                infrastructureFailures.isEmpty()
                        ? ProtosTestResourceAttemptCompletion.CapacityDisposition.SAFE
                        : ProtosTestResourceAttemptCompletion.CapacityDisposition.UNSAFE);
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        Throwable current = Objects.requireNonNull(failure, "failure");
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}

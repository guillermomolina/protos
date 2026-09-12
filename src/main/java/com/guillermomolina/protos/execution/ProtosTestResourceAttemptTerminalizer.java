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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/** Private D108 terminal-lifecycle owner for already-provisioned resourceful attempts. */
final class ProtosTestResourceAttemptTerminalizer {
    private ProtosTestResourceAttemptTerminalizer() {}

    static CompletionStage<ProtosTestResourceAttemptCompletion> finishStarted(
            ProtosProcessRuntime process,
            ProtosCapturedProcessExecution.Result guestObservation,
            ProtosTestResourceProviderTransaction transaction) {
        return finishCreated(process, guestObservation, List.of(), transaction);
    }

    /**
     * Finishes an attempt for which a semantic Process exists.
     *
     * <p>Initial infrastructure evidence (for example an execution failure) does not by itself make
     * capacity unsafe. Reuse safety is decided by terminal Process/host disposition plus provider
     * cleanup. This preserves the D108 separation between infrastructure failure and capacity
     * disposition.
     */
    static CompletionStage<ProtosTestResourceAttemptCompletion> finishCreated(
            ProtosProcessRuntime process,
            ProtosCapturedProcessExecution.Result guestObservation,
            List<? extends Throwable> initialInfrastructureFailures,
            ProtosTestResourceProviderTransaction transaction) {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(initialInfrastructureFailures, "initialInfrastructureFailures");
        Objects.requireNonNull(transaction, "transaction");

        ArrayList<Throwable> failures =
                new ArrayList<>(List.copyOf(initialInfrastructureFailures));
        boolean[] unsafe = {false};

        try {
            process.requestTerminationForRuntime();
            process.awaitTerminationForRuntime();
        } catch (RuntimeException | Error failure) {
            failures.add(failure);
            unsafe[0] = true;
        }

        try {
            ProtosProcessExecutionHost host =
                    process.executionHostForRuntime().orElse(null);
            if (host != null) {
                host.awaitTerminalDispositionForRuntime();
            }
        } catch (RuntimeException | Error failure) {
            failures.add(failure);
            unsafe[0] = true;
        }

        return cleanupAndComplete(guestObservation, failures, unsafe[0], transaction);
    }

    /**
     * Cleans a successful provider transaction when no semantic child Process was created.
     *
     * <p>The triggering infrastructure failure is preserved, while capacity remains SAFE when
     * provider cleanup succeeds.
     */
    static CompletionStage<ProtosTestResourceAttemptCompletion> finishUnstarted(
            Throwable initialInfrastructureFailure,
            ProtosTestResourceProviderTransaction transaction) {
        Objects.requireNonNull(initialInfrastructureFailure, "initialInfrastructureFailure");
        Objects.requireNonNull(transaction, "transaction");
        return cleanupAndComplete(
                null,
                new ArrayList<>(List.of(initialInfrastructureFailure)),
                false,
                transaction);
    }

    private static CompletionStage<ProtosTestResourceAttemptCompletion> cleanupAndComplete(
            ProtosCapturedProcessExecution.Result guestObservation,
            ArrayList<Throwable> failures,
            boolean unsafeBeforeCleanup,
            ProtosTestResourceProviderTransaction transaction) {
        final CompletionStage<Void> cleanupStage;
        try {
            cleanupStage =
                    Objects.requireNonNull(
                            transaction.cleanup(),
                            "provider transaction cleanup stage");
        } catch (RuntimeException | Error failure) {
            failures.add(failure);
            return CompletableFuture.completedFuture(
                    completion(guestObservation, failures, true));
        }

        return cleanupStage.handle(
                (ignored, cleanupFailure) -> {
                    boolean unsafe = unsafeBeforeCleanup;
                    if (cleanupFailure != null) {
                        failures.add(unwrapCompletionFailure(cleanupFailure));
                        unsafe = true;
                    }
                    return completion(guestObservation, failures, unsafe);
                });
    }

    static ProtosTestResourceAttemptCompletion provisioningFailure(
            Throwable provisioningFailure) {
        Throwable primary = unwrapCompletionFailure(
                Objects.requireNonNull(provisioningFailure, "provisioningFailure"));
        ArrayList<Throwable> failures = new ArrayList<>();
        failures.add(primary);
        for (Throwable suppressed : primary.getSuppressed()) {
            failures.add(Objects.requireNonNull(suppressed, "suppressed cleanup failure"));
        }
        boolean unsafe = primary.getSuppressed().length != 0;
        return completion(null, failures, unsafe);
    }

    private static ProtosTestResourceAttemptCompletion completion(
            ProtosCapturedProcessExecution.Result guestObservation,
            ArrayList<Throwable> failures,
            boolean unsafe) {
        return new ProtosTestResourceAttemptCompletion(
                guestObservation,
                failures,
                unsafe
                        ? ProtosTestResourceAttemptCompletion.CapacityDisposition.UNSAFE
                        : ProtosTestResourceAttemptCompletion.CapacityDisposition.SAFE);
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        Throwable current = Objects.requireNonNull(failure, "failure");
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}

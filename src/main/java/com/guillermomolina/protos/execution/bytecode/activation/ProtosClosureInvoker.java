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
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain.HostEntryExtent;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import java.util.List;
import java.util.Objects;

/**
 * Host-side synchronous Closure invocation (standard protocols implemented
 * in Java, embedding and tooling). I115: every invocation enters the shared
 * dispatcher ({@link ProtosFunctionCallDispatchers}); this class owns no
 * CallTarget selection, activation preparation or ReturnHome: the callee
 * root captures its own non-local returns and closes its own home.
 *
 * <p>A synchronous invocation cannot drive a suspension: a pending outcome is
 * abandoned (closing the homes its frames own) and rejected.
 */
public final class ProtosClosureInvoker {
    private ProtosClosureInvoker() {}

    public static Object invoke(
            ProtosClosureValue closure,
            List<?> supplied) {
        return invoke(closure, supplied, null);
    }

    /** A Task-owned entry of {@code closure}, driven by the Task C-prime driver. */
    public static void executeInTaskForRuntime(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation creator,
            ProtosTask task) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(task, "task");
        ProtosBytecodeTaskExecution.executeRequest(
                task, ProtosCallRequest.taskEntry(closure, task, supplied, creator));
    }

    public static Object invoke(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation caller) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(supplied, "supplied");
        if (caller == null) {
            ProtosActivation activation =
                    ProtosActivation.forClosureInvocation(closure, supplied, null);
            return synchronous(activation, () -> callPublished(closure, activation));
        }
        requireOutsideTask(caller, "synchronous Closure invocation");
        return synchronous(
                caller,
                () -> callWithEnteredContext(
                        () -> ProtosFunctionCallDispatchers.callFunctionUncached(
                                closure, supplied.toArray(), caller)));
    }

    public static Object invokeImmediateMethod(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            List<?> supplied,
            ProtosActivation caller) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(methodHome, "methodHome");
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(caller, "caller");
        requireOutsideTask(caller, "synchronous method invocation");
        return synchronous(
                caller,
                () -> callWithEnteredContext(
                        () -> ProtosFunctionCallDispatchers.callMethodUncached(
                                closure, receiver, methodHome, supplied.toArray(), caller)));
    }

    /*
     * A published entry through the shared dispatcher. Outside an entered
     * Process Context, a bounded host is created solely for this synchronous
     * invocation (closed on every exit, including a non-local return); a
     * source Closure's plan then requires destination-Context projection.
     * The same activation, hence the same ReturnHome, is entered either way.
     */
    private static Object callPublished(ProtosClosureValue closure, ProtosActivation activation) {
        if (ProtosLanguageContext.currentIfEnteredForRuntime() != null) {
            return ProtosFunctionCallDispatchers.callPublishedUncached(closure, activation);
        }
        if (closure.nativeBody().isEmpty()) {
            closure.requireContextLocalExecutionProjectionForRuntime();
        }
        try (ProtosPolyglotExecutionContext temporaryHost =
                ProtosPolyglotExecutionContext.open(
                        java.io.InputStream.nullInputStream(),
                        java.io.OutputStream.nullOutputStream(),
                        java.io.OutputStream.nullOutputStream())) {
            return temporaryHost.callEntered(
                    () -> ProtosFunctionCallDispatchers.callPublishedUncached(closure, activation));
        }
    }

    /** Any callable value, invoked through the shared dispatcher by {@code caller}. */
    static Object invokeCallable(Object callable, List<?> supplied, ProtosActivation caller) {
        Objects.requireNonNull(callable, "callable");
        Objects.requireNonNull(caller, "caller");
        requireOutsideTask(caller, "synchronous invocation");
        return synchronous(
                caller,
                () -> callWithEnteredContext(
                        () -> ProtosFunctionCallDispatchers.callFunctionUncached(
                                callable, supplied.toArray(), caller)));
    }

    /**
     * Shared boundary for synchronous Java-to-guest invocations with a caller.
     * The dispatcher is always entered under a Truffle Context; ordinary
     * guest-to-guest calls already have one and incur no additional host.
     * This is the same bounded host entry used for a published source call.
     */
    private static Object callWithEnteredContext(java.util.function.Supplier<Object> action) {
        if (ProtosLanguageContext.currentIfEnteredForRuntime() != null) {
            return action.get();
        }
        try (ProtosPolyglotExecutionContext temporaryHost =
                ProtosPolyglotExecutionContext.open(
                        java.io.InputStream.nullInputStream(),
                        java.io.OutputStream.nullOutputStream(),
                        java.io.OutputStream.nullOutputStream())) {
            return temporaryHost.callEntered(action);
        }
    }

    private static void requireOutsideTask(ProtosActivation caller, String what) {
        if (caller.task().isPresent()) {
            throw new IllegalStateException(what + " cannot execute inside a Task; use C-prime");
        }
    }

    /*
     * A synchronous host invocation never suspends: a suspendible host-entry
     * extent is narrowed to NONE for its duration.
     */
    private static Object synchronous(
            ProtosActivation site, java.util.function.Supplier<Object> invocation) {
        ProtosActorExecutionDomain domain = site.executionDomain();
        if (domain == null || domain.hostEntryExtentForRuntime() != HostEntryExtent.SUSPENDIBLE) {
            return complete(site, invocation);
        }
        domain.swapHostEntryExtentForRuntime(HostEntryExtent.NONE);
        try {
            return complete(site, invocation);
        } finally {
            domain.swapHostEntryExtentForRuntime(extent);
        }
    }

    private static Object complete(
            ProtosActivation site, java.util.function.Supplier<Object> invocation) {
        Object outcome;
        try {
            outcome = invocation.get();
        } catch (ProtosSignalException transfer) {
            ProtosCoreErrors.selectHandlerIfNeeded(site, transfer);
            throw transfer;
        }
        if (ProtosTailCallContinuation.isSuspended(outcome)) {
            ProtosTailCallContinuation.abandon(outcome);
            throw new IllegalStateException(
                    "synchronous Closure invocation cannot drive a suspended continuation");
        }
        return ProtosNumericValueSupport.guestValue(outcome);
    }
}

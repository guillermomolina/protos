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
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosSlotLookupResult;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import java.util.List;
import java.util.Objects;

public final class ProtosInvocation {
    private ProtosInvocation() {}

    public static Object invoke(Object receiver, List<?> supplied, ProtosActivation caller) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(caller, "caller");
        com.guillermomolina.protos.runtime.ProtosPrelude prelude =
                caller.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "polymorphic invocation requires an owning Core prelude"));
        return invokeSelected(
                receiver,
                ProtosValueLookup.lookup(receiver, "call", prelude).orElseThrow(
                        () -> new ProtosSignalException(ProtosCoreErrors.newError(caller))),
                supplied,
                caller);
    }

    public static Object invokeMessage(
            Object receiver, String selector, List<?> supplied, ProtosActivation caller) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(caller, "caller");

        com.guillermomolina.protos.runtime.ProtosPrelude prelude =
                caller.prelude().orElse(null);
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(receiver, selector, prelude)
                            .orElseThrow(
                                    () ->
                                            new ProtosSignalException(
                                                    ProtosCoreErrors.newSlotNotFound(caller)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }
        if (ProtosForeignProjectedOperations.isForeignMemberSelection(selected)) {
            // D188: read then ordinary invocation of the read foreign member value.
            return invoke(selected.value(), supplied, caller);
        }
        return invokeSelected(receiver, selected, supplied, caller);
    }

    /**
     * D189: ordinary polymorphic invocation of {@code receiver} by a synchronous foreign callback,
     * as a nested activation of {@code caller}'s execution.
     *
     * <p>Outside a Task this is {@link #invoke}. Inside a Task it continues that exact Task: the
     * same prepared C-prime call shapes as Task entry are driven nested on the host stack, so no
     * Task, Future, Actor turn, or structured scope is created and no suspension can commit across
     * the foreign frames ({@link ProtosBytecodeTaskExecution#invokeNestedSynchronous}).
     */
    static Object invokeFromForeignCallbackForRuntime(
            Object receiver, List<?> supplied, ProtosActivation caller) {
        ProtosTask task = caller.task().orElse(null);
        if (task == null) {
            return invokeForeignCallbackOutsideTask(receiver, supplied, caller);
        }
        com.guillermomolina.protos.runtime.ProtosPrelude prelude =
                caller.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "polymorphic invocation requires an owning Core prelude"));
        ProtosSlotLookupResult selected =
                ProtosValueLookup.lookup(receiver, "call", prelude)
                        .orElseThrow(
                                () -> new ProtosSignalException(ProtosCoreErrors.newError(caller)));
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }
        ProtosBytecodeRootNode.PreparedClosureCall prepared =
                ProtosBytecodeRootNode.prepareTaskOwnedSelectedCallIfBytecode(
                        receiver, selected, supplied, caller, task);
        if (prepared != null) {
            return ProtosBytecodeTaskExecution.invokeNestedSynchronous(
                    task, prepared, false, caller);
        }
        ProtosClosureValue nativeTarget = closure;
        if (receiver instanceof ProtosClosureValue targetClosure
                && ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                        closure, selected.home())) {
            nativeTarget = targetClosure;
        }
        ProtosClosureInvoker.requireNativeTaskFallbackForRuntime(nativeTarget);
        return ProtosBytecodeTaskExecution.invokeNestedSynchronous(
                task,
                ProtosBytecodeRootNode.prepareTaskOwnedSelectedNativeForCPrime(
                        receiver, selected, supplied, caller, task),
                true,
                caller);
    }

    /**
     * PLAT054-3E2: a foreign callback reached inside a suspendible host entry runs in a
     * synchronous extent, where an actual suspension is rejected with a fresh {@code Error}
     * ({@code FUTURES_AND_TASKS.md}, Synchronous foreign callbacks and the current Task).
     */
    private static Object invokeForeignCallbackOutsideTask(
            Object receiver, List<?> supplied, ProtosActivation caller) {
        ProtosActorExecutionDomain domain = caller.executionDomain();
        if (domain == null
                || domain.hostEntryExtentForRuntime() == HostEntryExtent.NONE) {
            return invoke(receiver, supplied, caller);
        }
        HostEntryExtent previous =
                domain.swapHostEntryExtentForRuntime(HostEntryExtent.SYNCHRONOUS);
        try {
            return invoke(receiver, supplied, caller);
        } finally {
            domain.swapHostEntryExtentForRuntime(previous);
        }
    }

    public static void executeInTaskForRuntime(
            Object receiver,
            List<?> supplied,
            ProtosActivation caller,
            ProtosTask task) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(task, "task");

        com.guillermomolina.protos.runtime.ProtosPrelude prelude =
                caller.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "polymorphic invocation requires an owning Core prelude"));
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(receiver, "call", prelude)
                            .orElseThrow(
                                    () ->
                                            new ProtosSignalException(
                                                    ProtosCoreErrors.newError(caller)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            task.fail(ProtosCoreErrors.newError(caller));
            return;
        } catch (ProtosSignalException signalled) {
            task.fail(signalled.error(), ProtosDiagnosticTraceCapture.capture(signalled));
            return;
        }
        executeSelectedInTaskForRuntime(receiver, selected, supplied, caller, task);
    }

    public static void executeMessageInTaskForRuntime(
            Object receiver,
            String selector,
            List<?> supplied,
            ProtosActivation caller,
            ProtosTask task) {
        executeMessageInTaskForRuntime(
                receiver,
                selector,
                supplied,
                caller,
                task,
                () -> {});
    }

    /**
     * Executes one ordinary message in the owning Task and reports whether the selected guest
     * handler entered through the Task-owned C-prime backend.
     *
     * <p>The callback runs only after ordinary lookup/selection and successful C-prime
     * preparation, immediately before that C-prime computation starts. It is runtime hosting
     * machinery for PLAT027 installation, not a guest callback or another continuation layer.
     */
    public static boolean executeMessageInTaskForRuntime(
            Object receiver,
            String selector,
            List<?> supplied,
            ProtosActivation caller,
            ProtosTask task,
            Runnable beforeBytecodeExecution) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(beforeBytecodeExecution, "beforeBytecodeExecution");

        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(
                                    receiver,
                                    selector,
                                    caller.prelude().orElse(null))
                            .orElseThrow(
                                    () ->
                                            new ProtosSignalException(
                                                    ProtosCoreErrors.newSlotNotFound(caller)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            task.fail(ProtosCoreErrors.newError(caller));
            return false;
        } catch (ProtosSignalException signalled) {
            task.fail(signalled.error(), ProtosDiagnosticTraceCapture.capture(signalled));
            return false;
        }
        if (ProtosForeignProjectedOperations.isForeignMemberSelection(selected)) {
            // D188: read then ordinary invocation of the read foreign member value.
            receiver = selected.value();
            try {
                selected = ProtosForeignProjectedOperations.invocationSelection(receiver, caller);
            } catch (ProtosSignalException signalled) {
                task.fail(signalled.error(), ProtosDiagnosticTraceCapture.capture(signalled));
                return false;
            }
        }
        return executeSelectedInTaskForRuntime(
                receiver,
                selected,
                supplied,
                caller,
                task,
                beforeBytecodeExecution);
    }

    private static void executeSelectedInTaskForRuntime(
            Object receiver,
            ProtosSlotLookupResult selected,
            List<?> supplied,
            ProtosActivation caller,
            ProtosTask task) {
        executeSelectedInTaskForRuntime(
                receiver,
                selected,
                supplied,
                caller,
                task,
                () -> {});
    }

    private static boolean executeSelectedInTaskForRuntime(
            Object receiver,
            ProtosSlotLookupResult selected,
            List<?> supplied,
            ProtosActivation caller,
            ProtosTask task,
            Runnable beforeBytecodeExecution) {
        Objects.requireNonNull(beforeBytecodeExecution, "beforeBytecodeExecution");
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            task.fail(ProtosCoreErrors.newError(caller));
            return false;
        }

        ProtosBytecodeRootNode.PreparedClosureCall prepared =
                ProtosBytecodeRootNode.prepareTaskOwnedSelectedCallIfBytecode(
                        receiver,
                        selected,
                        supplied,
                        caller,
                        task);
        if (prepared != null) {
            beforeBytecodeExecution.run();
            ProtosBytecodeTaskExecution.executePreparedClosure(task, prepared);
            return true;
        }

        /*
         * B6B-E2 removes the final production native Task replay branch.
         * The preparer preserves PLAT017's exact canonical Object.call target,
         * ordinary method receiver/home, PLAT021/028 structured provenance and
         * PLAT025 canonical import lifecycle ownership.
         */
        ProtosClosureValue nativeTarget = closure;
        if (receiver instanceof ProtosClosureValue targetClosure
                && ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                        closure,
                        selected.home())) {
            nativeTarget = targetClosure;
        }
        ProtosClosureInvoker.requireNativeTaskFallbackForRuntime(nativeTarget);

        ProtosBytecodeRootNode.PreparedClosureCall nativePrepared;
        try {
            nativePrepared =
                    ProtosBytecodeRootNode.prepareTaskOwnedSelectedNativeForCPrime(
                            receiver,
                            selected,
                            supplied,
                            caller,
                            task);
        } catch (ProtosSignalException signalled) {
            task.fail(signalled.error(), ProtosDiagnosticTraceCapture.capture(signalled));
            return false;
        }

        beforeBytecodeExecution.run();
        ProtosTaskCPrimeEntryExecution.execute(task, nativePrepared);
        return true;
    }

    public static Object invokeSuperMessage(
            String selector, List<?> supplied, ProtosActivation caller) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(caller, "caller");

        ProtosObjectValue methodHome =
                caller.methodHome()
                        .orElseThrow(
                                () ->
                                        new ProtosSignalException(
                                                ProtosCoreErrors.newInvalidSuper(caller)));
        Object lookupOrigin =
                methodHome.parent()
                        .orElseThrow(
                                () ->
                                        new ProtosSignalException(
                                                ProtosCoreErrors.newSlotNotFound(caller)));

        com.guillermomolina.protos.runtime.ProtosPrelude prelude =
                caller.prelude().orElse(null);
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(lookupOrigin, selector, prelude)
                            .orElseThrow(
                                    () ->
                                            new ProtosSignalException(
                                                    ProtosCoreErrors.newSlotNotFound(caller)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }

        return invokeSelected(caller.receiver(), selected, supplied, caller);
    }

    private static Object invokeSelected(
            Object receiver, ProtosSlotLookupResult selected, List<?> supplied, ProtosActivation caller) {
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }
        return ProtosClosureInvoker.invokeImmediateMethod(
                closure, receiver, selected.home(), supplied, caller);
    }
}

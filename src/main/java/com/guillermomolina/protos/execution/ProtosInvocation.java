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
        return invokeSelected(receiver, selected, supplied, caller);
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
            task.fail(signalled.error());
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
            task.fail(signalled.error());
            return false;
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
         * B6A6A3 deliberately retains the historical AST/native Task branch until B6B. The
         * PLAT027 hook above is never installed for this compatibility path.
         */
        task.executeAction(
                () -> {
                    if (receiver instanceof ProtosClosureValue targetClosure
                            && ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                                    closure,
                                    selected.home())) {
                        return ProtosClosureInvoker.invokeInTask(
                                targetClosure,
                                supplied,
                                caller,
                                task);
                    }
                    return ProtosClosureInvoker.invokeImmediateMethodInTask(
                            closure,
                            receiver,
                            selected.home(),
                            supplied,
                            caller,
                            task);
                });
        return false;
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

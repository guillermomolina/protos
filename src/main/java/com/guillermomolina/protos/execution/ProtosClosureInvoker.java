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
import com.guillermomolina.protos.runtime.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosReturnHome;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.util.List;
import java.util.Objects;

public final class ProtosClosureInvoker {
    private ProtosClosureInvoker() {}

    static void requireNativeTaskFallbackForRuntime(ProtosClosureValue closure) {
        Objects.requireNonNull(closure, "closure");
        if (closure.nativeBody().isEmpty()) {
            throw new IllegalStateException(
                    "B6B-C retired Task-owned source Closure AST/replay fallback; "
                            + "source-backed Task execution must enter through C-prime");
        }
    }

    public static Object invoke(
            ProtosClosureValue closure,
            List<?> supplied) {
        return invoke(closure, supplied, null);
    }

    public static void executeInTaskForRuntime(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation creator,
            com.guillermomolina.protos.runtime.ProtosTask task) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(task, "task");

        ProtosBytecodeRootNode.PreparedClosureCall prepared =
                ProtosBytecodeRootNode.prepareTaskOwnedDirectClosureIfBytecode(
                        closure,
                        supplied,
                        creator,
                        task);
        if (prepared != null) {
            ProtosBytecodeTaskExecution.executePreparedClosure(task, prepared);
            return;
        }

        /*
         * B6B-E2: native Task execution is prepared with the same native/control
         * provenance used by composed Bytecode calls, then enters the shared
         * Context-cached C-prime root. No evaluator replay state is allocated.
         */
        requireNativeTaskFallbackForRuntime(closure);
        ProtosBytecodeRootNode.PreparedClosureCall nativePrepared =
                ProtosBytecodeRootNode.prepareTaskOwnedDirectNativeForCPrime(
                        closure,
                        supplied,
                        creator,
                        task);
        ProtosTaskCPrimeEntryExecution.execute(task, nativePrepared);
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
                () -> {
                    ProtosActivation created =
                            caller == null
                                    ? ProtosActivation.forClosureInvocation(
                                            closure, supplied, fallbackPrelude)
                                    : ProtosActivation.forClosureInvocation(
                                            closure,
                                            supplied,
                                            fallbackPrelude,
                                            caller.actorModuleState(),
                                            caller.currentModuleKey().orElse(null),
                                            caller.executionDomain());
                    if (caller != null && caller.task().isEmpty()) {
                        created.inheritDynamicControlState(caller);
                    }
                    return created;
                };
        if (caller != null && caller.task().isPresent()) {
            throw new IllegalStateException(
                    "synchronous Closure invocation cannot execute inside a Task; use C-prime");
        }
        return invokePrepared(closure, supplied, activationFactory.get());
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

        com.guillermomolina.protos.runtime.ProtosPrelude fallbackPrelude =
                caller.prelude().orElse(null);
        java.util.function.Supplier<ProtosActivation> activationFactory =
                () -> {
                    ProtosActivation created =
                            ProtosActivation.forImmediateMethodInvocation(
                                    closure,
                                    supplied,
                                    receiver,
                                    methodHome,
                                    fallbackPrelude,
                                    caller.actorModuleState(),
                                    caller.currentModuleKey().orElse(null),
                                    caller.executionDomain());
                    if (caller.task().isEmpty()) {
                        created.inheritDynamicControlState(caller);
                    }
                    return created;
                };

        if (caller.task().isPresent()) {
            throw new IllegalStateException(
                    "synchronous method invocation cannot execute inside a Task; use C-prime");
        }
        return invokePrepared(closure, supplied, activationFactory.get());
    }

    private static Object invokePrepared(ProtosClosureValue closure, List<?> supplied, ProtosActivation activation) {
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
            if (activation.task().isPresent()) {
                throw new IllegalStateException(
                        "B6B-C retired Task-owned source Closure AST/replay fallback; "
                                + "source-backed Task execution must enter through C-prime");
            }
            ProtosClosureExecutionPlan template =
                    closure.executionPlanForRuntimeInvocation();
            /*
             * This synchronous path is now non-Task only. Entered-Context projection remains
             * selective for the retained AST equivalence/oracle surface. A direct host invocation
             * outside an entered Context may still rebuild one fresh AST plan, but no Task may use
             * that path or acquire evaluator replay state through it.
             */
            ProtosLanguageContext enteredContext =
                    ProtosLanguageContext.currentIfEnteredForRuntime();
            boolean hostedEnteredContext =
                    enteredContext != null
                            && ProtosPolyglotExecutionContext.hasEnteredContextForRuntime();

            if (hostedEnteredContext) {
                if (template.source().isEmpty()) {
                    throw new UnsupportedOperationException(
                            "source-less legacy Closure cannot execute through "
                                    + "the Bytecode-only runtime");
                }
                return invokePreparedSourceBytecode(
                        closure,
                        supplied,
                        activation);
            }

            if (template.source().isEmpty()) {
                throw new UnsupportedOperationException(
                        "unhosted source-less legacy Closure cannot execute after "
                                + "AST backend retirement");
            }

            /*
             * Outside an entered Process Context, create a bounded host solely
             * for this synchronous source-backed invocation. Any Bytecode plan
             * observed here necessarily belongs to no currently entered host,
             * so require destination-Context projection before entry.
             */
            closure.requireContextLocalExecutionProjectionForRuntime();

            try (ProtosPolyglotExecutionContext temporaryHost =
                    ProtosPolyglotExecutionContext.open(
                            java.io.InputStream.nullInputStream(),
                            java.io.OutputStream.nullOutputStream(),
                            java.io.OutputStream.nullOutputStream())) {
                return temporaryHost.callEntered(
                        () ->
                                invokePreparedSourceBytecode(
                                        closure,
                                        supplied,
                                        activation));
            }
        } catch (ProtosSignalException transfer) {
            ProtosCoreErrors.selectHandlerIfNeeded(activation, transfer);
            throw transfer;
        } catch (ProtosNonLocalReturnException transfer) {
            if (activation.ownsReturnHome()
                    && transfer.target() == returnHome) {
                return transfer.value();
            }
            throw transfer;
        } catch (ProtosTaskCancellationException transfer) {
            throw transfer;
        } finally {
            if (activation.ownsReturnHome()
                    && returnHome.isActive()) {
                returnHome.complete();
            }
        }
    }

    private static Object invokePreparedSourceBytecode(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation activation) {
        ProtosBytecodeRootNode.PreparedClosureCall prepared =
                ProtosBytecodeRootNode.prepareSynchronousSourceClosureForRuntime(
                        closure,
                        supplied,
                        activation);

        Object entered =
                ProtosBytecodeRootNode.EnterClosureCall.indirect(
                        prepared,
                        IndirectCallNode.create());

        return ProtosBytecodeRootNode.FinishClosureCall.perform(
                prepared,
                entered);
    }
}

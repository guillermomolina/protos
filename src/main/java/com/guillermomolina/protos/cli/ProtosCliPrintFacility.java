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
package com.guillermomolina.protos.cli;

import com.guillermomolina.protos.execution.ProtosInvocation;
import com.guillermomolina.protos.execution.ProtosStandardFutureProtocol;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosProcessStandardStreamValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosSlotLookupResult;
import com.guillermomolina.protos.runtime.ProtosSuspensionCapableNativeClosureBody;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.util.List;
import java.util.Objects;

/**
 * Host-owned standalone-CLI print convenience.
 *
 * <p>The binding is installed only in the ordinary initial CLI module context. It is not a Core
 * binding, parser form, intrinsic lookup rule, or ambient host-stream escape. General value display
 * is CLI policy; the resulting text is emitted through a borrowing standard TextWriter built over
 * the Process stdout capability and its bootstrap Encoding association.
 */
final class ProtosCliPrintFacility {
    private ProtosCliPrintFacility() {}

    static void install(
            ProtosActivation activation,
            ProtosProcessRuntime process,
            ProtosValueRenderer renderer) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(renderer, "renderer");

        if (activation.context().hasLocalSlot("print")) {
            throw new IllegalStateException("initial CLI context already defines print");
        }

        Object writer = borrowingStdoutWriter(activation, process);
        activation.context()
                .createLocalSlot(
                        "print",
                        ProtosClosureValue.suspensionCapableNativeClosure(
                                (callActivation, supplied) ->
                                        printOrdinary(
                                                writer,
                                                renderer,
                                                callActivation,
                                                supplied),
                                (callActivation, supplied) ->
                                        printForCPrime(
                                                writer,
                                                renderer,
                                                callActivation,
                                                supplied)));
    }

    private static Object printOrdinary(
            Object writer,
            ProtosValueRenderer renderer,
            ProtosActivation callActivation,
            List<?> supplied) {
        ProtosStringValue text = renderText(renderer, callActivation, supplied);
        Object result =
                ProtosInvocation.invokeMessage(
                        writer,
                        "writeLine",
                        List.of(text),
                        callActivation);
        if (!(result instanceof ProtosFutureValue future)) {
            throw new IllegalStateException(
                    "TextWriter.writeLine did not return Future");
        }
        future.observeValue(callActivation);
        return ProtosNullValue.INSTANCE;
    }

    private static Object printForCPrime(
            Object writer,
            ProtosValueRenderer renderer,
            ProtosActivation callActivation,
            List<?> supplied) {
        ProtosStringValue text = renderText(renderer, callActivation, supplied);
        ProtosFutureValue future =
                invokePrivateWriteLineForCPrime(
                        writer,
                        text,
                        callActivation);
        return ProtosStandardFutureProtocol
                .awaitTaskFutureThenForContinuationForRuntime(
                        callActivation,
                        future,
                        () -> ProtosNullValue.INSTANCE);
    }

    private static ProtosStringValue renderText(
            ProtosValueRenderer renderer,
            ProtosActivation activation,
            List<?> supplied) {
        if (supplied.size() != 1) {
            throw ordinaryError(activation);
        }
        Object value = supplied.get(0);
        return value instanceof ProtosStringValue string
                ? string
                : new ProtosStringValue(renderer.render(value));
    }

    private static ProtosFutureValue invokePrivateWriteLineForCPrime(
            Object writer,
            ProtosStringValue text,
            ProtosActivation caller) {
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(
                                    writer,
                                    "writeLine",
                                    caller.prelude().orElseThrow())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "CLI stdout writer lost writeLine"));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new IllegalStateException(
                    "CLI stdout writer has unsupported writeLine representation",
                    unsupportedRepresentation);
        }
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new IllegalStateException(
                    "CLI stdout writer writeLine is not invokable");
        }
        Object nativeBody =
                closure.nativeBody()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "CLI stdout writer writeLine is not native"));
        if (!(nativeBody instanceof ProtosSuspensionCapableNativeClosureBody cPrimeBody)) {
            throw new IllegalStateException(
                    "CLI stdout writer writeLine lost C-prime suspension capability");
        }
        ProtosTask task =
                caller.task()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "C-prime CLI print requires an Actor-local Task"));
        List<?> arguments = List.of(text);
        ProtosActivation invocation =
                ProtosActivation.forImmediateMethodInvocation(
                        closure,
                        arguments,
                        writer,
                        selected.home(),
                        caller.prelude().orElse(null),
                        caller.actorModuleState(),
                        caller.currentModuleKey().orElse(null),
                        caller.executionDomain());
        invocation.attachTask(task);
        Object result =
                cPrimeBody.executeForBytecodeContinuation(
                        invocation,
                        arguments);
        if (!(result instanceof ProtosFutureValue future)) {
            throw new IllegalStateException(
                    "C-prime TextWriter.writeLine did not return Future");
        }
        return future;
    }

    private static Object borrowingStdoutWriter(
            ProtosActivation activation, ProtosProcessRuntime process) {
        ProtosProcessStandardStreamValue stdout;
        ProtosEncodingValue encoding;
        synchronized (process) {
            if (process.lifecycleState() != ProtosProcessRuntime.LifecycleState.RUNNING) {
                throw new IllegalStateException(
                        "cannot install CLI print after Process termination begins");
            }
            stdout =
                    process.stdoutForRuntime()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "standalone CLI stdout is unavailable"));
            if (process.stdoutEncodingStateForRuntime()
                    != ProtosProcessRuntime.StandardStreamEncodingState.AVAILABLE) {
                throw new IllegalStateException(
                        "standalone CLI stdout Encoding is unavailable");
            }
            encoding =
                    process.stdoutEncodingForRuntime()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "standalone CLI stdout Encoding is unavailable"));
        }

        Object factory =
                activation.prelude()
                        .orElseThrow()
                        .bindings()
                        .readLocalSlot("TextWriter")
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Core TextWriter binding is missing"));
        return ProtosInvocation.invoke(factory, List.of(stdout, encoding), activation);
    }

    private static ProtosSignalException ordinaryError(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}

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
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.List;

/**
 * Private asynchronous bridge of {@code std:http/Server} (D215, LIB025).
 *
 * <p>Every HTTP rule (request parsing, body framing, error classification, response validation
 * and serialization, emission policy, session state, limits) lives in the Protos sources {@code
 * lib/http/Server.protos} and {@code lib/http/internal/*.protos}. This frozen facility supplies
 * only the integration that the public Future protocol cannot express without creating Tasks:
 *
 * <ul>
 *   <li>{@code failed(error)} and {@code resolved(value)} answer fresh, already terminal, non-Task
 *       Futures of the caller's Actor. They give invalid admission its failed Future without a
 *       Task (HTTP.md §2.4) and give the private framed source behind {@code request.body} its
 *       per-read results; the public body itself is a borrowing standard {@code BufferedReader}
 *       over that source, whose operation-owned C-prime execution runs the Protos framer without
 *       a Task per read (§6, §8).
 *   <li>{@code provides(value, selector)}, {@code invokable(value)}, {@code closable(value)},
 *       {@code ordinaryObject(value)}
 *       and {@code isBytes(value)} are read-only representation probes used by admission and
 *       response validation; they never invoke guest code.
 *   <li>{@code startService(body)} creates the one service Task (§2.3) as a structured child of
 *       the invoker's Task, or of the Actor root scope, and answers its Future. The Task enters
 *       {@code body} even when cancelled before its first ordinary instruction.
 *   <li>{@code enterService()}, called by {@code body} as its first instruction inside its own
 *       {@code ensure} scope, turns such a pre-start cancellation into the ordinary cancellation
 *       unwind of that scope, so session retirement and the owning close complete before the
 *       service Future becomes terminal (§9.2, §9.5).
 * </ul>
 *
 * <p>Like {@code ProtosRuntimeMetadataFacility}, the facility is a deferred standard initial
 * member of exactly its modules: it is created only when an Actor-local instance of a module
 * initializes, is captured lexically by the module source, and its bootstrap slot is removed, so
 * it is never module surface or a Prelude binding. It retains no Actor, Task or session state.
 *
 * <p>I126 (D216, HTTP.md §15): the same facility, with no additional operation, is also a deferred
 * member of {@code std:http/Host}, whose construction validates options and limits with {@code
 * ordinaryObject} and whose lifecycle runs its completion Task with {@code startService} and
 * {@code enterService}, so that its cleanup also runs when cancelled before starting, and of
 * {@code std:http/internal/HostWorker}, whose Worker bootstrap applies {@code invokable} to the
 * resolved handler binding.
 *
 * <p>I127 (D218-B, HTTP.md §16): the same facility, with no additional operation, is also a
 * deferred member of {@code std:http/Client}. Its single-exchange session uses {@code failed},
 * {@code resolved}, the probes, {@code startService} and {@code enterService} exactly as the
 * server session does; every client rule lives in {@code lib/http/Client.protos} and {@code
 * lib/http/internal/Client*.protos}.
 */
public final class ProtosHttpAsyncFacility {
    public static final ProtosModuleKey MODULE_KEY = new ProtosModuleKey("std:http/Server");
    public static final ProtosModuleKey HOST_MODULE_KEY = new ProtosModuleKey("std:http/Host");
    public static final ProtosModuleKey HOST_WORKER_MODULE_KEY =
            new ProtosModuleKey("std:http/internal/HostWorker");
    public static final ProtosModuleKey CLIENT_MODULE_KEY =
            new ProtosModuleKey("std:http/Client");
    public static final String BOOTSTRAP_SLOT = "_httpAsyncFacility";

    private ProtosHttpAsyncFacility() {}

    public static ProtosObjectValue createFacility() {
        ProtosObjectValue facility = new ProtosObjectValue(ProtosObjectValue.rootObject());
        operation(facility, "failed", 1, ProtosHttpAsyncFacility::failed);
        operation(facility, "resolved", 1, ProtosHttpAsyncFacility::resolved);
        operation(facility, "provides", 2, ProtosHttpAsyncFacility::provides);
        operation(facility, "invokable", 1, ProtosHttpAsyncFacility::invokable);
        operation(facility, "closable", 1, ProtosHttpAsyncFacility::closable);
        operation(facility, "ordinaryObject", 1, ProtosHttpAsyncFacility::ordinaryObject);
        operation(facility, "isBytes", 1, ProtosHttpAsyncFacility::isBytes);
        operation(facility, "startService", 1, ProtosHttpAsyncFacility::startService);
        operation(facility, "enterService", 0, ProtosHttpAsyncFacility::enterService);
        return facility.freeze();
    }

    private static void operation(
            ProtosObjectValue facility, String name, int arity, ProtosNativeClosureBody body) {
        facility.createLocalSlot(
                name,
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            if (activation.receiver() != facility || supplied.size() != arity) {
                                throw invalid(activation);
                            }
                            return body.execute(activation, supplied);
                        }));
    }

    private static Object failed(ProtosActivation activation, List<?> supplied) {
        if (!(supplied.get(0) instanceof ProtosObjectValue error)) {
            throw invalid(activation);
        }
        ProtosFutureValue future = freshFuture(activation);
        future.fail(error);
        return future;
    }

    private static Object resolved(ProtosActivation activation, List<?> supplied) {
        Object value = supplied.get(0);
        if (value instanceof ProtosFutureValue) {
            // Adoption would make the result depend on another producer.
            throw invalid(activation);
        }
        ProtosFutureValue future = freshFuture(activation);
        future.resolve(value, activation);
        return future;
    }

    @TruffleBoundary
    private static Object provides(ProtosActivation activation, List<?> supplied) {
        if (!(supplied.get(1) instanceof ProtosStringValue selector)) {
            throw invalid(activation);
        }
        return bool(hasCallable(supplied.get(0), selector.value(), activation));
    }

    /** CALLABLES.md "Callability inspection": lookup of {@code call} selects a Closure. */
    private static Object invokable(ProtosActivation activation, List<?> supplied) {
        return bool(hasCallable(supplied.get(0), "call", activation));
    }

    /**
     * A resource {@code close}: the inherited structural {@code Object.close} is not the
     * {@code Closable} capability, as for the standard I/O wrappers.
     */
    @TruffleBoundary
    private static Object closable(ProtosActivation activation, List<?> supplied) {
        try {
            return bool(
                    ProtosValueLookup.lookup(
                                    supplied.get(0), "close", activation.prelude().orElseThrow())
                            .filter(result -> result.home() != ProtosObjectValue.rootObject())
                            .filter(result -> result.value() instanceof ProtosClosureValue)
                            .isPresent());
        } catch (UnsupportedOperationException unsupported) {
            return ProtosBooleanValue.FALSE;
        }
    }

    /** An ordinary object: no standard value family, collection, Closure, or resource. */
    private static Object ordinaryObject(ProtosActivation activation, List<?> supplied) {
        Object value = supplied.get(0);
        return bool(value != null && value.getClass() == ProtosObjectValue.class);
    }

    private static Object isBytes(ProtosActivation activation, List<?> supplied) {
        return bool(supplied.get(0) instanceof ProtosBytesValue);
    }

    private static Object startService(ProtosActivation activation, List<?> supplied) {
        if (!(supplied.get(0) instanceof ProtosClosureValue body)) {
            throw invalid(activation);
        }
        ProtosActorExecutionDomain domain = activation.executionDomain();
        ProtosFutureValue result = freshFuture(activation);
        ProtosTask parent = activation.task().orElse(null);
        ProtosTask task =
                domain.createTask(
                        parent,
                        result,
                        current ->
                                ProtosClosureInvoker.executeInTaskForRuntime(
                                        body, List.of(), activation, current));
        // The invoker still owns the Actor: the fresh Task cannot have been dispatched yet.
        task.enterBodyBeforePreStartCancellationForRuntime();
        result.attachProducerTask(task, activation);
        return result;
    }

    private static Object enterService(ProtosActivation activation, List<?> supplied) {
        ProtosTask task = activation.task().orElseThrow(() -> invalid(activation));
        if (task.cancellationRequested()) {
            if (!task.beginContinuationCancellationUnwindForRuntime()) {
                throw new IllegalStateException(
                        "pre-start service cancellation was not observable");
            }
            throw new ProtosTaskCancellationException();
        }
        return ProtosNullValue.INSTANCE;
    }

    private static ProtosFutureValue freshFuture(ProtosActivation activation) {
        ProtosPrelude prelude = activation.prelude().orElseThrow(() -> invalid(activation));
        return new ProtosFutureValue(prelude.futurePrototype(), activation.executionDomain());
    }

    private static boolean hasCallable(
            Object receiver, String selector, ProtosActivation activation) {
        try {
            return ProtosValueLookup.lookup(
                            receiver, selector, activation.prelude().orElseThrow())
                    .filter(result -> result.value() instanceof ProtosClosureValue)
                    .isPresent();
        } catch (UnsupportedOperationException unsupported) {
            return false;
        }
    }

    private static ProtosBooleanValue bool(boolean value) {
        return value ? ProtosBooleanValue.TRUE : ProtosBooleanValue.FALSE;
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}

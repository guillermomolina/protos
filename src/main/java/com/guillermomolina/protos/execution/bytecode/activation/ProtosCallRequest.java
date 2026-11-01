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
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.runtime.lookup.ProtosSlotLookupResult;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.List;
import java.util.Objects;

/**
 * I115: the operands of one call a structured protocol (loop, ensure,
 * Error handler, collection iteration, Map hashing/equality, Boolean
 * control, Object.call, Task and I/O entries) wants made on its behalf: the
 * evaluated callable, the B5 argument array the shared dispatcher will
 * complete, and the calling activation.
 *
 * <p>It is data, exactly the operands of {@code CallVarargsMethod}: it owns
 * no CallTarget, activation, ReturnHome, completion or failure protocol and
 * it is executed only by {@link ProtosFunctionCallDispatchers.CallNode}
 * (the {@code CallRequest} operation, or {@link #callUncached} on the host
 * side). Selection of a send is performed here exactly as by {@code
 * GetMethod}; nothing is invoked.
 */
final class ProtosCallRequest {
    private final Object callable;
    private final Object[] arguments;
    private final ProtosActivation caller;

    private ProtosCallRequest(Object callable, Object[] arguments, ProtosActivation caller) {
        this.callable = Objects.requireNonNull(callable, "callable");
        this.arguments = Objects.requireNonNull(arguments, "arguments");
        this.caller = Objects.requireNonNull(caller, "caller");
    }

    /** {@code callable(supplied...)} made by {@code caller}. */
    static ProtosCallRequest call(Object callable, List<?> supplied, ProtosActivation caller) {
        return new ProtosCallRequest(
                callable,
                ProtosFrameArguments.functionCallArguments(supplied.toArray()),
                caller);
    }

    /** {@code receiver.selector(supplied...)} made by {@code caller}, selected now. */
    @TruffleBoundary
    static ProtosCallRequest send(
            Object receiver, String selector, ProtosActivation caller, List<?> supplied) {
        Object selected =
                ProtosExperimentalGetMethodNode.select(receiver, selector, null, caller);
        return call(selected, supplied, caller);
    }

    /** {@code method} already selected on {@code receiver} at {@code selected}'s home. */
    static ProtosCallRequest method(
            ProtosSlotLookupResult selected,
            Object receiver,
            List<?> supplied,
            ProtosActivation caller) {
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }
        return call(new ProtosBoundMethod(closure, receiver, selected.home()), supplied, caller);
    }

    /**
     * A Task-owned entry: the callee activation owns {@code task}. A Closure
     * is entered as a function with the Task in its B5 header; any other
     * callable (a {@link ProtosBoundMethod} or an object whose {@code call}
     * is selected) is entered as a method whose caller is a provenance copy
     * of {@code creator} that belongs to {@code task}, so the materialized
     * callee activation inherits exactly that Task.
     */
    @TruffleBoundary
    static ProtosCallRequest taskEntry(
            Object callable, ProtosTask task, List<?> supplied, ProtosActivation creator) {
        if (callable instanceof ProtosClosureValue) {
            return new ProtosCallRequest(
                    callable,
                    ProtosFrameArguments.taskFunctionCallArguments(task, supplied.toArray()),
                    creator);
        }
        ProtosBoundMethod method;
        if (callable instanceof ProtosBoundMethod bound) {
            method = bound;
        } else {
            ProtosSlotLookupResult selected = selectCall(callable, creator);
            if (!(selected.value() instanceof ProtosClosureValue closure)) {
                throw new ProtosSignalException(ProtosCoreErrors.newError(creator));
            }
            method = new ProtosBoundMethod(closure, callable, selected.home());
        }
        ProtosActivation taskCaller =
                ProtosActivation.forImmediateMethodInvocation(
                        method.callable(),
                        List.of(),
                        method.receiver(),
                        method.methodHome(),
                        creator.prelude().orElse(null),
                        creator.actorModuleState(),
                        creator.currentModuleKey().orElse(null),
                        creator.executionDomain());
        taskCaller.attachTask(task);
        return new ProtosCallRequest(
                method.callable(),
                ProtosFrameArguments.methodCallArguments(
                        method.receiver(), method.methodHome(), supplied.toArray()),
                taskCaller);
    }

    /** Ordinary {@code call} selection of a non-Closure value (D013 lookup). */
    @TruffleBoundary
    static ProtosSlotLookupResult selectCall(Object receiver, ProtosActivation caller) {
        try {
            return ProtosValueLookup.lookup(receiver, "call", caller.preludeOrNullForRuntime())
                    .orElseThrow(() -> new ProtosSignalException(ProtosCoreErrors.newError(caller)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }
    }

    Object callable() {
        return callable;
    }

    Object[] arguments() {
        return arguments;
    }

    ProtosActivation caller() {
        return caller;
    }

    /** Host-side execution through the uncached shared dispatcher. */
    Object callUncached() {
        return ProtosFunctionCallDispatchers.callUncached(callable, arguments, caller);
    }
}

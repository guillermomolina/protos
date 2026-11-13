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
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosDynamicControlState;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import java.util.List;

/**
 * Prepared state of the structured control-flow invocations (match/case,
 * standard Object and import calls, ensure, Error.handle and Boolean callbacks)
 * driven by {@link ProtosStructuredDispatchLowerer} roots and inline literal regions.
 */
final class ProtosStructuredControlCalls {
    private ProtosStructuredControlCalls() {}

    static final class PreparedCaseOfCall {
        private final Object subject;
        private final List<java.util.Map.Entry<Object, Object>> cases;
        private final ProtosActivation activation;
        private int index;
        private Object selectedAction;
        private List<Object> selectedArguments;

        PreparedCaseOfCall(
                Object subject,
                List<?> supplied,
                ProtosActivation activation) {
            this.subject = java.util.Objects.requireNonNull(subject, "subject");
            this.activation =
                    java.util.Objects.requireNonNull(
                            activation,
                            "activation");

            if (supplied.size() != 1
                    || !(supplied.get(0) instanceof ProtosMapValue caseMap)) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            this.cases =
                    List.copyOf(
                            caseMap.associationSnapshot());
        }

        boolean needsMatcher() {
            return selectedAction == null && index < cases.size();
        }

        ProtosCallRequest prepareMatcher() {
            if (!needsMatcher()) {
                throw new IllegalStateException(
                        "Object.caseOf matcher requested after selection or exhaustion");
            }

            return ProtosCallRequest.send(
                    cases.get(index).getKey(),
                    "match",
                    activation,
                    List.of(subject));
        }

        void acceptMatcher(Object outcome) {
            if (!needsMatcher()) {
                throw new IllegalStateException(
                        "Object.caseOf matcher outcome accepted after selection or exhaustion");
            }

            if (outcome == Boolean.FALSE) {
                index++;
                return;
            }

            if (outcome == Boolean.TRUE) {
                selectedAction = cases.get(index).getValue();
                selectedArguments = List.of();
                return;
            }

            if (outcome instanceof ProtosArrayValue captures) {
                List<Object> snapshot = captures.indexedSnapshot();

                if (snapshot.isEmpty()) {
                    throw ProtosCoreErrors.signal(
                            activation,
                            ProtosCoreErrors.newError(activation));
                }

                selectedAction = cases.get(index).getValue();
                selectedArguments = List.copyOf(snapshot);
                return;
            }

            throw ProtosCoreErrors.signal(
                    activation,
                    ProtosCoreErrors.newError(activation));
        }

        ProtosCallRequest prepareSelectedAction() {
            if (selectedAction == null) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            return ProtosCallRequest.call(
                    selectedAction,
                    selectedArguments,
                    activation);
        }
    }

    /**
     * PLAT032 / D239-C1 standard Object.call: exactly one ordinary child
     * invocation, either the D013 Closure execution or the ordinary
     * {@code construct} send on the original receiver. Its finish answers the
     * child's exact normal result, so a customized construct may answer any
     * value, including {@code null}, a scalar or an existing identity.
     */
    static final class PreparedStandardObjectCall {
        private final ProtosCallRequest child;

        private PreparedStandardObjectCall(ProtosCallRequest child) {
            this.child = java.util.Objects.requireNonNull(child, "child");
        }

        static PreparedStandardObjectCall forward(
                ProtosCallRequest child) {
            return new PreparedStandardObjectCall(child);
        }

        ProtosCallRequest child() {
            return child;
        }

        Object finish(Object childResult) {
            return childResult;
        }
    }

    static final class PreparedStandardImportCall {
        private final ProtosPreparedCalls.ModuleInitializationCall child;

        PreparedStandardImportCall(
                ProtosPreparedCalls.ModuleInitializationCall child) {
            this.child = java.util.Objects.requireNonNull(child, "child");
        }

        ProtosPreparedCalls.ModuleInitializationCall child() {
            return child;
        }

        Object finish(Object childResult) {
            return child.finish(childResult);
        }
    }

    static final class PreparedEnsureCall {
        private final ProtosCallRequest body;
        private final ProtosCallRequest cleanup;

        PreparedEnsureCall(
                ProtosCallRequest body,
                ProtosCallRequest cleanup) {
            this.body = java.util.Objects.requireNonNull(body, "body");
            this.cleanup = java.util.Objects.requireNonNull(cleanup, "cleanup");
        }

        ProtosCallRequest body() { return body; }
        ProtosCallRequest cleanup() { return cleanup; }
    }

    static final class PreparedErrorHandlerCall {
        private final ProtosDynamicControlState state;
        private final ProtosDynamicControlState.Frame frame;
        private final ProtosClosureValue handler;
        private final ProtosActivation activation;
        private final ProtosCallRequest body;
        private boolean left;

        PreparedErrorHandlerCall(
                ProtosDynamicControlState state,
                ProtosDynamicControlState.Frame frame,
                ProtosClosureValue handler,
                ProtosActivation activation,
                ProtosCallRequest body) {
            this.state = java.util.Objects.requireNonNull(state, "state");
            this.frame = java.util.Objects.requireNonNull(frame, "frame");
            this.handler = java.util.Objects.requireNonNull(handler, "handler");
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            this.body = java.util.Objects.requireNonNull(body, "body");
        }

        ProtosCallRequest body() { return body; }

        ProtosCallRequest selectHandler(AbstractTruffleException exception) {
            if (exception instanceof ProtosSignalException transfer
                    && transfer.selectedHandlerFrame().orElse(null) == frame) {
                leave();
                return ProtosCallRequest.call(
                        handler,
                        List.of(transfer.error()),
                        activation);
            }
            leave();
            throw exception;
        }

        void leave() {
            if (left) {
                return;
            }
            state.leaveFrame(frame);
            left = true;
        }
    }


    static final class PreparedBooleanCall {
        private final ProtosStandardBooleanProtocol.StructuredCallbackKind kind;
        private final Object receiver;
        private final List<?> supplied;
        private final ProtosActivation activation;

        PreparedBooleanCall(
                ProtosStandardBooleanProtocol.StructuredCallbackKind kind,
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.kind = java.util.Objects.requireNonNull(kind, "kind");
            this.receiver = java.util.Objects.requireNonNull(receiver, "receiver");
            // TEST009-AL: supplied is NativeCall's already-immutable List.copyOf result (the sole
            // construction site); re-copying it only re-enters List.copyOf's erased fallback in PE.
            this.supplied = java.util.Objects.requireNonNull(supplied, "supplied");
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (receiver != Boolean.TRUE
                    && receiver != Boolean.FALSE) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            if (this.supplied.size() != kind.arity()) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
        }

        boolean hasCallback() {
            return switch (kind) {
                case AND, IF_TRUE -> receiver == Boolean.TRUE;
                case OR, IF_FALSE -> receiver == Boolean.FALSE;
                case IF_TRUE_IF_FALSE -> true;
            };
        }

        ProtosCallRequest prepareCallback() {
            if (!hasCallback()) {
                throw new IllegalStateException(
                        "short-circuited Boolean operation has no selected callback");
            }
            int selected = kind == ProtosStandardBooleanProtocol.StructuredCallbackKind.IF_TRUE_IF_FALSE
                    && receiver == Boolean.FALSE ? 1 : 0;
            return ProtosCallRequest.call(
                    supplied.get(selected),
                    List.of(),
                    activation);
        }

        Object immediateResult() {
            if (hasCallback()) {
                throw new IllegalStateException(
                        "selected Boolean callback has no immediate result");
            }
            return switch (kind) {
                case AND -> Boolean.FALSE;
                case OR -> Boolean.TRUE;
                case IF_TRUE, IF_FALSE -> ProtosNullValue.INSTANCE;
                case IF_TRUE_IF_FALSE -> throw new IllegalStateException(
                        "two-branch conditional always selects a callback");
            };
        }

        Object finishCallback(Object result) {
            // and/or require a Boolean; conditional messages return the exact value,
            // including an ordinary Future, without adopting or flattening it.
            return switch (kind) {
                case AND, OR -> ProtosStandardBooleanProtocol.requireBooleanResult(result, activation);
                case IF_TRUE, IF_FALSE, IF_TRUE_IF_FALSE -> result;
            };
        }
    }

    /**
     * A standard Closure loop reached by ordinary dispatch, driven as a
     * sequence of child activations so each one may suspend and resume. The
     * receiver, the argument count and the argument are validated, in that
     * order, before either Closure is activated. Each child is a
     * zero-argument activation of the condition or the body; a condition
     * result that is exactly the exit Boolean ends the loop, the other
     * canonical Boolean continues it and any other value signals a fresh
     * Error. The result is the last normal body result, or canonical null.
     */
    static final class PreparedLoopCall {
        private final ProtosClosureValue condition;
        private final ProtosClosureValue body;
        private final Boolean exitValue;
        private final ProtosActivation activation;
        private boolean conditionNext;
        private boolean done;
        private Object lastBodyResult = ProtosNullValue.INSTANCE;

        PreparedLoopCall(
                boolean postTest,
                Boolean exitValue,
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            this.exitValue = java.util.Objects.requireNonNull(exitValue, "exitValue");
            if (!(receiver instanceof ProtosClosureValue receiverClosure)
                    || supplied.size() != 1
                    || !(supplied.get(0) instanceof ProtosClosureValue argument)) {
                throw ProtosCoreErrors.signal(activation, ProtosCoreErrors.newError(activation));
            }
            this.condition = postTest ? argument : receiverClosure;
            this.body = postTest ? receiverClosure : argument;
            this.conditionNext = !postTest;
        }

        boolean hasNext() {
            return !done;
        }

        ProtosCallRequest prepareCurrent() {
            if (done) {
                throw new IllegalStateException("loop child requested after termination");
            }
            return ProtosCallRequest.call(conditionNext ? condition : body, List.of(), activation);
        }

        void acceptCurrent(Object outcome) {
            if (done) {
                throw new IllegalStateException("loop child outcome accepted after termination");
            }
            if (!conditionNext) {
                lastBodyResult = outcome;
                conditionNext = true;
                return;
            }
            if (outcome == exitValue) {
                done = true;
                return;
            }
            if (outcome != Boolean.TRUE && outcome != Boolean.FALSE) {
                throw ProtosCoreErrors.signal(activation, ProtosCoreErrors.newError(activation));
            }
            conditionNext = false;
        }

        Object finish() {
            return lastBodyResult;
        }
    }
}

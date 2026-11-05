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
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosDynamicControlState;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import java.util.List;

/**
 * Prepared state of the structured control-flow invocations (match/case,
 * standard Object and import calls, ensure, while, Error.handle and the and/or callbacks)
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

            if (outcome == ProtosBooleanValue.FALSE) {
                index++;
                return;
            }

            if (outcome == ProtosBooleanValue.TRUE) {
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

    static final class PreparedStandardObjectCall {
        private final ProtosCallRequest child;
        private final ProtosObjectValue constructedInstance;

        private PreparedStandardObjectCall(
                ProtosCallRequest child,
                ProtosObjectValue constructedInstance) {
            this.child = java.util.Objects.requireNonNull(child, "child");
            this.constructedInstance = constructedInstance;
        }

        static PreparedStandardObjectCall closure(
                ProtosCallRequest child) {
            return new PreparedStandardObjectCall(child, null);
        }

        static PreparedStandardObjectCall construction(
                ProtosCallRequest child,
                ProtosObjectValue constructedInstance) {
            return new PreparedStandardObjectCall(
                    child,
                    java.util.Objects.requireNonNull(
                            constructedInstance,
                            "constructedInstance"));
        }

        ProtosCallRequest child() {
            return child;
        }

        Object finish(Object childResult) {
            return constructedInstance == null
                    ? childResult
                    : constructedInstance;
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

    /**
     * Validated standard Closure loop (D212 {@code whileFalse},
     * {@code untilTrue}, {@code untilFalse}). The
     * immutable {@link ProtosStandardObjectProtocol.StructuredLoopKind} fixes
     * the phase order and exit polarity; {@code condition} and {@code body}
     * are already mapped from the original receiver/argument by phase.
     * {@link #result} yields the normal loop result once a condition
     * completed normally with the kind's canonical exit Boolean.
     */
    static final class PreparedWhileCall {
        private final ProtosStandardObjectProtocol.StructuredLoopKind kind;
        private final ProtosClosureValue condition;
        private final ProtosClosureValue body;
        private final ProtosActivation activation;

        PreparedWhileCall(
                ProtosStandardObjectProtocol.StructuredLoopKind kind,
                ProtosClosureValue condition,
                ProtosClosureValue body,
                ProtosActivation activation) {
            this.kind = java.util.Objects.requireNonNull(kind, "kind");
            this.condition = java.util.Objects.requireNonNull(condition, "condition");
            this.body = java.util.Objects.requireNonNull(body, "body");
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
        }

        /**
         * Whether the body activates before each condition (post-test). The
         * lowering selects the loop shape from this; the phase is never
         * inferred from Closure identity or a previous condition result.
         */
        boolean postTest() {
            return kind.postTest();
        }

        /**
         * Normal loop result. {@code lastBodyResult} is the exact result of the
         * last normally completed body activation, or canonical {@code null}
         * when none ran; it is returned unchanged (no copy, conversion, or
         * Future await/adoption).
         */
        Object result(Object lastBodyResult) {
            return lastBodyResult;
        }

        /**
         * Value kept in the loop's body-result local after a normal body
         * activation: the exact body result, unchanged.
         */
        Object retainedBodyResult(Object bodyResult) {
            return bodyResult;
        }

        ProtosCallRequest prepareCondition() {
            return ProtosCallRequest.call(condition, List.of(), activation);
        }

        ProtosCallRequest prepareBody() {
            return ProtosCallRequest.call(body, List.of(), activation);
        }

        /**
         * Whether the loop continues after a normally completed condition:
         * false exactly for the kind's canonical exit Boolean, true for the
         * other canonical Boolean, and a fresh standard Error for any other
         * value (no {@code not()}, truthiness or Future await/adoption).
         */
        boolean conditionResult(Object result) {
            if (result == ProtosBooleanValue.TRUE || result == ProtosBooleanValue.FALSE) {
                return result != kind.exitValue();
            }
            throw ProtosCoreErrors.signal(
                    activation,
                    ProtosCoreErrors.newError(activation));
        }
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
            if (receiver != ProtosBooleanValue.TRUE
                    && receiver != ProtosBooleanValue.FALSE) {
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
                case AND -> receiver == ProtosBooleanValue.TRUE;
                case OR -> receiver == ProtosBooleanValue.FALSE;
            };
        }

        ProtosCallRequest prepareCallback() {
            if (!hasCallback()) {
                throw new IllegalStateException(
                        "short-circuited Boolean operation has no selected callback");
            }
            return ProtosCallRequest.call(
                    supplied.get(0),
                    List.of(),
                    activation);
        }

        Object immediateResult() {
            if (hasCallback()) {
                throw new IllegalStateException(
                        "selected Boolean callback has no immediate result");
            }
            return switch (kind) {
                case AND -> ProtosBooleanValue.FALSE;
                case OR -> ProtosBooleanValue.TRUE;
            };
        }

        Object finishCallback(Object result) {
            return ProtosStandardBooleanProtocol.requireBooleanResult(
                    result,
                    activation);
        }
    }
}

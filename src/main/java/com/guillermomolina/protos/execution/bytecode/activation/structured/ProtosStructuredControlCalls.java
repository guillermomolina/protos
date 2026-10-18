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

import com.guillermomolina.protos.execution.ProtosInlineCallbackFrameBindings.PreparedInlineLiteralCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedClosureCall;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
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
 * standard Object and import calls, ensure, while, Error.handle and the Boolean callbacks)
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

        PreparedClosureCall prepareMatcher() {
            if (!needsMatcher()) {
                throw new IllegalStateException(
                        "Object.caseOf matcher requested after selection or exhaustion");
            }

            return ProtosSendPreparation.prepareSend(
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

        PreparedClosureCall prepareSelectedAction() {
            if (selectedAction == null) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            return ProtosClosureCallPreparation.prepareClosureCall(
                    selectedAction,
                    selectedArguments,
                    activation);
        }
    }

    static final class PreparedStandardObjectCall {
        private final PreparedClosureCall child;
        private final ProtosObjectValue constructedInstance;

        private PreparedStandardObjectCall(
                PreparedClosureCall child,
                ProtosObjectValue constructedInstance) {
            this.child = java.util.Objects.requireNonNull(child, "child");
            this.constructedInstance = constructedInstance;
        }

        static PreparedStandardObjectCall closure(
                PreparedClosureCall child) {
            return new PreparedStandardObjectCall(child, null);
        }

        static PreparedStandardObjectCall construction(
                PreparedClosureCall child,
                ProtosObjectValue constructedInstance) {
            return new PreparedStandardObjectCall(
                    child,
                    java.util.Objects.requireNonNull(
                            constructedInstance,
                            "constructedInstance"));
        }

        PreparedClosureCall child() {
            return child;
        }

        Object finish(Object childResult) {
            return constructedInstance == null
                    ? childResult
                    : constructedInstance;
        }
    }

    static final class PreparedStandardImportCall {
        private final PreparedClosureCall child;

        PreparedStandardImportCall(
                PreparedClosureCall child) {
            this.child = java.util.Objects.requireNonNull(child, "child");
        }

        PreparedClosureCall child() {
            return child;
        }

        Object finish(Object childResult) {
            return childResult;
        }
    }

    static final class PreparedEnsureCall {
        private final PreparedClosureCall body;
        private final PreparedClosureCall cleanup;

        PreparedEnsureCall(
                PreparedClosureCall body,
                PreparedClosureCall cleanup) {
            this.body = java.util.Objects.requireNonNull(body, "body");
            this.cleanup = java.util.Objects.requireNonNull(cleanup, "cleanup");
        }

        PreparedClosureCall body() { return body; }
        PreparedClosureCall cleanup() { return cleanup; }
    }

    /**
     * Validated standard pre-test loop shared by {@code whileTrue} and {@code
     * while} (D201). The loop algorithm is identical; {@link #result} applies
     * the selector's normal-result policy once the final condition completed
     * normally with canonical {@code false}.
     */
    static final class PreparedWhileCall {
        private final ProtosClosureValue condition;
        private final ProtosClosureValue body;
        private final ProtosActivation activation;
        private final boolean returnsLastBody;

        PreparedWhileCall(
                ProtosClosureValue condition,
                ProtosClosureValue body,
                ProtosActivation activation,
                boolean returnsLastBody) {
            this.condition = java.util.Objects.requireNonNull(condition, "condition");
            this.body = java.util.Objects.requireNonNull(body, "body");
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            this.returnsLastBody = returnsLastBody;
        }

        /**
         * Normal loop result. {@code lastBodyResult} is the exact result of the
         * last normally completed body activation, or canonical {@code null}
         * when none ran; it is returned unchanged (no copy, conversion, or
         * Future await/adoption) for {@code whileTrue}. {@code while} returns
         * its final canonical {@code false}.
         */
        Object result(Object lastBodyResult) {
            return returnsLastBody ? lastBodyResult : ProtosBooleanValue.FALSE;
        }

        /**
         * Value kept in the loop's body-result local after a normal body
         * activation: the exact result for {@code whileTrue}, canonical
         * {@code null} for {@code while}, so {@code while} never keeps a body
         * object reachable across its next (possibly suspending) condition.
         */
        Object retainedBodyResult(Object bodyResult) {
            return returnsLastBody ? bodyResult : ProtosNullValue.INSTANCE;
        }

        PreparedClosureCall prepareCondition() {
            return ProtosSendPreparation.prepareDirectClosureCall(condition, List.of(), activation);
        }

        PreparedClosureCall prepareBody() {
            return ProtosSendPreparation.prepareDirectClosureCall(body, List.of(), activation);
        }

        PreparedInlineLiteralCall prepareInlineCondition() {
            return ProtosClosureCallPreparation.prepareInlineLiteralCall(condition, List.of(), activation);
        }

        PreparedInlineLiteralCall prepareInlineBody() {
            return ProtosClosureCallPreparation.prepareInlineLiteralCall(body, List.of(), activation);
        }

        boolean conditionResult(Object result) {
            if (result == ProtosBooleanValue.TRUE) {
                return true;
            }
            if (result == ProtosBooleanValue.FALSE) {
                return false;
            }
            throw ProtosCoreErrors.signal(
                    activation,
                    ProtosCoreErrors.newError(activation));
        }

        /**
         * PLAT044 B′ (PERF026-C1) inline eligibility of the freshly prepared
         * condition invocation. The condition is exactly the staged literal
         * and the invocation has already performed authoritative ordinary
         * {@code call} selection, but has not materialized a rich activation
         * or physical prepared source call merely to prove inline execution.
         */
        boolean admitsInlineLiteralCondition(
                PreparedInlineLiteralCall child,
                Object literal,
                ProtosClosureExecutionPlanCell literalPlan) {
            return condition == literal
                    && child.admits(literalPlan);
        }

        /** As {@link #admitsInlineLiteralCondition}, for a fresh body invocation. */
        boolean admitsInlineLiteralBody(
                PreparedInlineLiteralCall child,
                Object literal,
                ProtosClosureExecutionPlanCell literalPlan) {
            return body == literal
                    && child.admits(literalPlan);
        }
    }

    static final class PreparedErrorHandlerCall {
        private final ProtosDynamicControlState state;
        private final ProtosDynamicControlState.Frame frame;
        private final ProtosClosureValue handler;
        private final ProtosActivation activation;
        private final PreparedClosureCall body;
        private boolean left;

        PreparedErrorHandlerCall(
                ProtosDynamicControlState state,
                ProtosDynamicControlState.Frame frame,
                ProtosClosureValue handler,
                ProtosActivation activation,
                PreparedClosureCall body) {
            this.state = java.util.Objects.requireNonNull(state, "state");
            this.frame = java.util.Objects.requireNonNull(frame, "frame");
            this.handler = java.util.Objects.requireNonNull(handler, "handler");
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            this.body = java.util.Objects.requireNonNull(body, "body");
        }

        PreparedClosureCall body() { return body; }

        PreparedClosureCall selectHandler(AbstractTruffleException exception) {
            if (exception instanceof ProtosSignalException transfer
                    && transfer.selectedHandlerFrame().orElse(null) == frame) {
                leave();
                return ProtosSendPreparation.prepareDirectClosureCall(
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
                case IF_TRUE -> receiver == ProtosBooleanValue.TRUE;
                case IF_FALSE -> receiver == ProtosBooleanValue.FALSE;
                case IF_TRUE_IF_FALSE -> true;
                case AND -> receiver == ProtosBooleanValue.TRUE;
                case OR -> receiver == ProtosBooleanValue.FALSE;
                case IF -> receiver == ProtosBooleanValue.TRUE;
                case ELSE -> receiver == ProtosBooleanValue.FALSE;
            };
        }

        PreparedClosureCall prepareCallback() {
            if (!hasCallback()) {
                throw new IllegalStateException(
                        "short-circuited Boolean operation has no selected callback");
            }
            return ProtosClosureCallPreparation.prepareClosureCall(
                    supplied.get(selectedIndex()),
                    List.of(),
                    activation);
        }

        PreparedInlineLiteralCall prepareInlineCallback() {
            if (!hasCallback()) {
                throw new IllegalStateException(
                        "short-circuited Boolean operation has no selected callback");
            }
            return ProtosClosureCallPreparation.prepareInlineLiteralCall(
                    supplied.get(selectedIndex()),
                    List.of(),
                    activation);
        }

        /**
         * Supplied position of the selected callback; meaningful only when
         * {@link #hasCallback} holds. Shared by {@link #prepareCallback} and
         * {@link #admitsInlineLiteralCallback} so inline admission can never
         * disagree with ordinary selection.
         */
        private int selectedIndex() {
            return switch (kind) {
                case IF_TRUE, IF_FALSE, AND, OR, IF, ELSE -> 0;
                case IF_TRUE_IF_FALSE ->
                        receiver == ProtosBooleanValue.TRUE ? 0 : 1;
            };
        }

        Object immediateResult() {
            if (hasCallback()) {
                throw new IllegalStateException(
                        "selected Boolean callback has no immediate result");
            }
            return switch (kind) {
                case IF_TRUE, IF_FALSE -> ProtosNullValue.INSTANCE;
                case AND -> ProtosBooleanValue.FALSE;
                case OR -> ProtosBooleanValue.TRUE;
                case IF, ELSE -> receiver;
                case IF_TRUE_IF_FALSE ->
                        throw new IllegalStateException(
                                "ifTrueIfFalse always selects one callback");
            };
        }

        Object finishCallback(Object result) {
            return switch (kind) {
                case AND, OR ->
                        ProtosStandardBooleanProtocol.requireBooleanResult(
                                result,
                                activation);
                case IF_TRUE, IF_FALSE, IF_TRUE_IF_FALSE -> result;
                // D200: the callback's normal result is ignored; the receiver is returned.
                case IF, ELSE -> receiver;
            };
        }

        /**
         * PLAT044 B′ (PERF026-B1/B2/B3) inline eligibility of an already
         * prepared selected callback {@code child}.
         *
         * <p>True only when this standard capability selected a callback, the
         * selected supplied position ({@link #selectedIndex}) is exactly the
         * send site's candidate {@code position}, the value supplied there is
         * exactly {@code literal} (the immediate Closure literal value staged
         * by the send site), and {@code child} is that literal's exact
         * inline-equivalent invocation ({@link #isInlineLiteralInvocation}).
         * For IF_TRUE_IF_FALSE both callbacks are eagerly evaluated and
         * staged, but only the selected position can be admitted, so the
         * unselected literal is never entered. Any other shape (including a
         * different selected value or position) keeps the exact physical
         * callback invocation. The inline result still completes through
         * {@link #finishCallback}, so AND/OR Boolean-result validation is
         * unchanged.
         */
        boolean admitsInlineLiteralCallback(
                PreparedInlineLiteralCall child,
                Object literal,
                ProtosClosureExecutionPlanCell literalPlan,
                int position) {
            return hasCallback()
                    && selectedIndex() == position
                    && supplied.get(position) == literal
                    && child.admits(literalPlan);
        }
    }
}

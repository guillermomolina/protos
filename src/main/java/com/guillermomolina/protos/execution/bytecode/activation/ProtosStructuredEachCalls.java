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
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import java.util.ArrayList;
import java.util.List;

/**
 * Prepared iteration state of the structured each/match invocations over
 * local collections (Array, Bytes, Environment, IdentityMap, Map) shared by the structured
 * dispatcher and inline literal callback regions.
 */
final class ProtosStructuredEachCalls {
    private ProtosStructuredEachCalls() {}

    /**
     * PLAT044 B′ common loop state of a standard each whose callback a send
     * site may sequence locally (PERF026-D1 indexed, PERF026-D2 association,
     * PERF026-D3 Environment): validated receiver and callback, one snapshot
     * established before the first callback, one fresh
     * callback invocation per snapshot position carrying exactly that
     * position's arguments, advance only after that invocation completes
     * normally, and the original receiver as result. It lets a send site
     * sequence any such loop without duplicating its inline callback region;
     * each implementation keeps its own validation and snapshot authority.
     */
    sealed interface PreparedLocalEachCall
            permits PreparedIndexedEachCall,
                    PreparedAssociationEachCall,
                    PreparedEnvironmentEachCall {
        boolean hasNext();

        PreparedClosureCall prepareCurrent();

        PreparedInlineLiteralCall prepareInlineCurrent();

        void advance();

        Object finish();

        /** The validated callback value supplied to this each invocation. */
        Object callback();

        /**
         * Inline eligibility of an already prepared per-position {@code
         * child}: the callback is exactly the staged {@code literal} and
         * {@code child} is its exact inline-equivalent invocation ({@link
         * #isInlineLiteralInvocation}), whose activation already carries the
         * snapshot arguments the inline region binds.
         */
        default boolean admitsInlineLiteralChild(
                PreparedInlineLiteralCall child,
                Object literal,
                ProtosClosureExecutionPlanCell literalPlan) {
            return callback() == literal
                    && child.admits(literalPlan);
        }
    }

    /**
     * PLAT044 B′ (PERF026-D1) standard Array.each and Bytes.each loops, whose
     * observable shapes coincide: an ascending indexed snapshot and one
     * one-argument callback invocation per snapshot element.
     */
    sealed interface PreparedIndexedEachCall extends PreparedLocalEachCall
            permits PreparedArrayEachCall, PreparedBytesEachCall {}

    /**
     * PLAT044 B′ (PERF026-D2) standard Map.each and IdentityMap.each loops,
     * whose observable shapes coincide: an insertion-ordered association
     * snapshot of representative keys and their values, and one
     * {@code block(key, value)} invocation per snapshot association. The two
     * receiver families stay distinct; only the loop view is shared.
     */
    sealed interface PreparedAssociationEachCall extends PreparedLocalEachCall
            permits PreparedMapEachCall, PreparedIdentityMapEachCall {}

    static final class PreparedArrayEachCall implements PreparedIndexedEachCall {
        private final ProtosArrayValue array;
        private final List<Object> snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedArrayEachCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosArrayValue value)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.array = value;
            this.block = supplied.get(0);
            ProtosStandardArrayProtocol.requireInvokableForStructured(
                    block,
                    activation);
            this.snapshot = value.indexedSnapshot();
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.size();
        }

        @Override
        public PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.each callback requested after snapshot exhaustion");
            }
            return ProtosClosureCallPreparation.prepareClosureCall(
                    block,
                    List.of(snapshot.get(index)),
                    activation);
        }

        @Override
        public PreparedInlineLiteralCall prepareInlineCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.each callback requested after snapshot exhaustion");
            }
            return ProtosClosureCallPreparation.prepareInlineLiteralCall(
                    block,
                    List.of(snapshot.get(index)),
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Array.each finished before snapshot exhaustion");
            }
            return array;
        }

        @Override
        public Object callback() {
            return block;
        }
    }


    static final class PreparedArrayMatchCall {
        private final ProtosActivation activation;
        private final List<Object> matcherSnapshot;
        private final List<Object> subjectSnapshot;
        private final List<Object> captures = new ArrayList<>();
        private int index;
        private boolean mismatch;

        PreparedArrayMatchCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation =
                    java.util.Objects.requireNonNull(
                            activation,
                            "activation");

            if (!(receiver instanceof ProtosArrayValue matcher)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            Object subjectValue = supplied.get(0);
            if (!(subjectValue instanceof ProtosArrayValue subject)) {
                matcherSnapshot = List.of();
                subjectSnapshot = List.of();
                mismatch = true;
                return;
            }

            matcherSnapshot = matcher.indexedSnapshot();
            subjectSnapshot = subject.indexedSnapshot();

            if (matcherSnapshot.size() != subjectSnapshot.size()) {
                mismatch = true;
            }
        }

        boolean hasNext() {
            return !mismatch && index < matcherSnapshot.size();
        }

        PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.match child requested after terminal outcome");
            }

            return ProtosSendPreparation.prepareSend(
                    matcherSnapshot.get(index),
                    "match",
                    activation,
                    List.of(subjectSnapshot.get(index)));
        }

        void acceptCurrent(Object outcome) {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Array.match child outcome accepted after terminal outcome");
            }

            if (outcome == ProtosBooleanValue.FALSE) {
                mismatch = true;
                return;
            }

            if (outcome == ProtosBooleanValue.TRUE) {
                index++;
                return;
            }

            if (outcome instanceof ProtosArrayValue childCaptures) {
                List<Object> observedCaptures =
                        childCaptures.indexedSnapshot();

                if (observedCaptures.isEmpty()) {
                    throw ProtosCoreErrors.signal(
                            activation,
                            ProtosCoreErrors.newError(activation));
                }

                for (int captureIndex = 0;
                        captureIndex < observedCaptures.size();
                        captureIndex++) {
                    captures.add(observedCaptures.get(captureIndex));
                }
                index++;
                return;
            }

            throw ProtosCoreErrors.signal(
                    activation,
                    ProtosCoreErrors.newError(activation));
        }

        Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Array.match finished before snapshot exhaustion");
            }

            if (mismatch) {
                return ProtosBooleanValue.FALSE;
            }

            if (captures.isEmpty()) {
                return ProtosBooleanValue.TRUE;
            }

            return activation
                    .prelude()
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "standard Array.match requires an owning Core prelude"))
                    .newArray(captures);
        }
    }

    static final class PreparedBytesEachCall implements PreparedIndexedEachCall {
        private final ProtosBytesValue bytes;
        private final List<Object> snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedBytesEachCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosBytesValue value)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.bytes = value;
            this.block = supplied.get(0);
            ProtosStandardBytesProtocol.requireInvokableForStructured(
                    block,
                    activation);
            this.snapshot = value.indexedSnapshot();
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.size();
        }

        @Override
        public PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Bytes.each callback requested after snapshot exhaustion");
            }
            return ProtosClosureCallPreparation.prepareClosureCall(
                    block,
                    List.of(snapshot.get(index)),
                    activation);
        }

        @Override
        public PreparedInlineLiteralCall prepareInlineCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Bytes.each callback requested after snapshot exhaustion");
            }
            return ProtosClosureCallPreparation.prepareInlineLiteralCall(
                    block,
                    List.of(snapshot.get(index)),
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Bytes.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Bytes.each finished before snapshot exhaustion");
            }
            return bytes;
        }

        @Override
        public Object callback() {
            return block;
        }
    }

    /**
     * Standard Environment.each loop state. It joins the PLAT044 B′
     * (PERF026-D3) local each view directly, not as an association each: its
     * snapshot is not a shallow association snapshot but the complete
     * portable (String, String) snapshot, converted, validated and
     * canonically ordered by the Environment protocol before any callback is
     * prepared.
     */
    static final class PreparedEnvironmentEachCall implements PreparedLocalEachCall {
        private final ProtosEnvironmentValue environment;
        private final List<ProtosEnvironmentValue.PortableEntry> snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedEnvironmentEachCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosEnvironmentValue value)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.environment = value;
            this.block = supplied.get(0);
            ProtosStandardEnvironmentProtocol.requireInvokableForStructured(
                    block,
                    activation);
            /*
             * PLAT028 Environment.each must preserve the historical eager
             * portable-representation cutover. This call validates/converts
             * the complete environment before callback #1 can be prepared.
             */
            this.snapshot =
                    List.copyOf(
                            ProtosStandardEnvironmentProtocol.portableEntriesForStructured(
                                    value,
                                    activation));
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.size();
        }

        @Override
        public PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Environment.each callback requested after snapshot exhaustion");
            }
            ProtosEnvironmentValue.PortableEntry entry = snapshot.get(index);
            return ProtosClosureCallPreparation.prepareClosureCall(
                    block,
                    List.of(entry.name(), entry.value()),
                    activation);
        }

        @Override
        public PreparedInlineLiteralCall prepareInlineCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Environment.each callback requested after snapshot exhaustion");
            }
            ProtosEnvironmentValue.PortableEntry entry = snapshot.get(index);
            return ProtosClosureCallPreparation.prepareInlineLiteralCall(
                    block,
                    List.of(entry.name(), entry.value()),
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Environment.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Environment.each finished before snapshot exhaustion");
            }
            return environment;
        }

        @Override
        public Object callback() {
            return block;
        }
    }


    static final class PreparedIdentityMapAtIfAbsentCall {
        private final ProtosIdentityMapValue.Entry match;
        private final Object fallback;
        private final ProtosActivation activation;
        private boolean fallbackPrepared;

        PreparedIdentityMapAtIfAbsentCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation =
                    java.util.Objects.requireNonNull(
                            activation,
                            "activation");

            if (!(receiver instanceof ProtosIdentityMapValue map)
                    || supplied.size() != 2) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            Object key = supplied.get(0);
            this.fallback = supplied.get(1);

            this.match =
                    ProtosStandardIdentityMapProtocol.findForStructured(
                            map,
                            key);
        }

        boolean needsFallback() {
            return match == null;
        }

        PreparedClosureCall prepareFallback() {
            if (!needsFallback() || fallbackPrepared) {
                throw new IllegalStateException(
                        "IdentityMap.atIfAbsent fallback requested in an invalid state");
            }

            fallbackPrepared = true;

            return ProtosClosureCallPreparation.prepareClosureCall(
                    fallback,
                    List.of(),
                    activation);
        }

        Object finishFallback(Object result) {
            if (!needsFallback() || !fallbackPrepared) {
                throw new IllegalStateException(
                        "IdentityMap.atIfAbsent fallback result accepted in an invalid state");
            }
            return result;
        }

        Object finishPresent() {
            if (needsFallback()) {
                throw new IllegalStateException(
                        "IdentityMap.atIfAbsent present path has no matching entry");
            }
            if (fallbackPrepared) {
                throw new IllegalStateException(
                        "IdentityMap.atIfAbsent present path prepared its fallback");
            }
            return match.value();
        }
    }


    static final class PreparedIdentityMapEachCall implements PreparedAssociationEachCall {
        private final ProtosIdentityMapValue identityMap;
        private final List<java.util.Map.Entry<Object, Object>> snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedIdentityMapEachCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosIdentityMapValue value)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.identityMap = value;
            this.block = supplied.get(0);
            ProtosStandardIdentityMapProtocol.requireInvokableForStructured(
                    block,
                    activation);
            this.snapshot = value.associationSnapshot();
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.size();
        }

        @Override
        public PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "IdentityMap.each callback requested after snapshot exhaustion");
            }
            java.util.Map.Entry<Object, Object> entry = snapshot.get(index);
            return ProtosClosureCallPreparation.prepareClosureCall(
                    block,
                    List.of(entry.getKey(), entry.getValue()),
                    activation);
        }

        @Override
        public PreparedInlineLiteralCall prepareInlineCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "IdentityMap.each callback requested after snapshot exhaustion");
            }
            java.util.Map.Entry<Object, Object> entry = snapshot.get(index);
            return ProtosClosureCallPreparation.prepareInlineLiteralCall(
                    block,
                    List.of(entry.getKey(), entry.getValue()),
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "IdentityMap.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "IdentityMap.each finished before snapshot exhaustion");
            }
            return identityMap;
        }

        @Override
        public Object callback() {
            return block;
        }
    }

    static final class PreparedMapEachCall implements PreparedAssociationEachCall {
        private final ProtosMapValue map;
        private final List<java.util.Map.Entry<Object, Object>> snapshot;
        private final Object block;
        private final ProtosActivation activation;
        private int index;

        PreparedMapEachCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosMapValue value)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.map = value;
            this.block = supplied.get(0);
            ProtosStandardMapProtocol.requireInvokableForStructured(
                    block,
                    activation);
            this.snapshot = List.copyOf(value.associationSnapshot());
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.size();
        }

        @Override
        public PreparedClosureCall prepareCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Map.each callback requested after snapshot exhaustion");
            }
            java.util.Map.Entry<Object, Object> entry = snapshot.get(index);
            return ProtosClosureCallPreparation.prepareClosureCall(
                    block,
                    List.of(entry.getKey(), entry.getValue()),
                    activation);
        }

        @Override
        public PreparedInlineLiteralCall prepareInlineCurrent() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Map.each callback requested after snapshot exhaustion");
            }
            java.util.Map.Entry<Object, Object> entry = snapshot.get(index);
            return ProtosClosureCallPreparation.prepareInlineLiteralCall(
                    block,
                    List.of(entry.getKey(), entry.getValue()),
                    activation);
        }

        @Override
        public void advance() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        "Map.each cursor advanced after snapshot exhaustion");
            }
            index++;
        }

        @Override
        public Object finish() {
            if (hasNext()) {
                throw new IllegalStateException(
                        "Map.each finished before snapshot exhaustion");
            }
            return map;
        }

        @Override
        public Object callback() {
            return block;
        }
    }
}

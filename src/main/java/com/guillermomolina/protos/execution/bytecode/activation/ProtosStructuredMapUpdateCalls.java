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

import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedClosureCall;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.numeric.ProtosNumericHashKey;
import java.util.List;

/**
 * Prepared state of the structured Map construction, at:put: and remove
 * invocations, which compare guest keys through C-prime hash and equality calls.
 */
final class ProtosStructuredMapUpdateCalls {
    private ProtosStructuredMapUpdateCalls() {}

    static final class PreparedMapInitialDefinition {
        private final ProtosMapValue map;
        private final Object key;
        private final Object value;
        private final ProtosActivation activation;
        private ProtosNumericHashKey queryHash;
        private List<ProtosMapValue.Entry> candidates;
        private int index;
        private ProtosMapValue.Entry match;
        private boolean hashAccepted;
        private boolean comparisonEntered;

        PreparedMapInitialDefinition(
                Object receiver,
                Object key,
                Object value,
                ProtosActivation activation) {
            this.activation =
                    java.util.Objects.requireNonNull(
                            activation,
                            "activation");

            if (!(receiver instanceof ProtosMapValue mapValue)
                    || !mapValue.isOpen()
                    || mapValue.comparisonActive()) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            this.map = mapValue;
            this.key = java.util.Objects.requireNonNull(key, "key");
            this.value = java.util.Objects.requireNonNull(value, "value");
        }

        void enterComparison() {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map initial definition attempted to nest its own comparison scope");
            }
            map.enterComparison();
            comparisonEntered = true;
        }

        void leaveComparison() {
            if (!comparisonEntered) {
                throw new IllegalStateException(
                        "Map initial-definition comparison scope left without entry");
            }
            map.leaveComparison();
            comparisonEntered = false;
        }

        PreparedClosureCall prepareHash() {
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map initial-definition hash callback prepared after hash acceptance");
            }
            return ProtosSendPreparation.prepareSend(
                    key,
                    "hash",
                    activation,
                    List.of());
        }

        void acceptHash(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map initial-definition hash accepted before comparison-scope exit");
            }
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map initial-definition hash accepted twice");
            }

            queryHash =
                    ProtosStandardMapProtocol.requireHashResultForStructured(
                            result,
                            activation);
            candidates = map.candidatesForRecordedHash(queryHash);
            hashAccepted = true;
        }

        boolean needsEquality() {
            requireHashAccepted();

            if (match != null) {
                return false;
            }

            return index < candidates.size();
        }

        PreparedClosureCall prepareEquality() {
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map initial-definition equality callback requested without a candidate");
            }

            return ProtosSendPreparation.prepareSend(
                    key,
                    "==",
                    activation,
                    List.of(candidates.get(index).key()));
        }

        void acceptEquality(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map initial-definition equality accepted before comparison-scope exit");
            }
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map initial-definition equality accepted without a candidate");
            }

            boolean equal =
                    ProtosStandardMapProtocol.requireEqualityResultForStructured(
                            result,
                            activation);

            if (equal) {
                match = candidates.get(index);
            } else {
                index++;
            }
        }

        void finish() {
            requireHashAccepted();

            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map initial definition finished with an active comparison scope");
            }
            if (needsEquality()) {
                throw new IllegalStateException(
                        "Map initial definition finished before candidate exhaustion");
            }

            if (match != null
                    || !map.isOpen()
                    || map.comparisonActive()) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            map.append(key, queryHash, value);
        }

        private void requireHashAccepted() {
            if (!hashAccepted) {
                throw new IllegalStateException(
                        "Map initial definition used before hash acceptance");
            }
        }
    }

    static final class PreparedMapAtPutCall {
        private final ProtosMapValue map;
        private final Object key;
        private final Object newValue;
        private final ProtosActivation activation;
        private ProtosNumericHashKey queryHash;
        private List<ProtosMapValue.Entry> candidates;
        private int index;
        private ProtosMapValue.Entry match;
        private boolean hashAccepted;
        private boolean comparisonEntered;

        PreparedMapAtPutCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosMapValue mapValue)
                    || supplied.size() != 2) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.map = mapValue;
            ProtosStandardMapProtocol.requireMutationEntryForStructured(map, activation);
            this.key = supplied.get(0);
            this.newValue = supplied.get(1);
        }

        void enterComparison() {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.atPut attempted to nest its own comparison scope");
            }
            map.enterComparison();
            comparisonEntered = true;
        }

        void leaveComparison() {
            if (!comparisonEntered) {
                throw new IllegalStateException(
                        "Map.atPut comparison scope left without entry");
            }
            map.leaveComparison();
            comparisonEntered = false;
        }

        PreparedClosureCall prepareHash() {
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map.atPut hash callback prepared after hash acceptance");
            }
            return ProtosSendPreparation.prepareSend(key, "hash", activation, List.of());
        }

        void acceptHash(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.atPut hash result accepted before comparison-scope exit");
            }
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map.atPut hash result accepted twice");
            }
            queryHash =
                    ProtosStandardMapProtocol.requireHashResultForStructured(
                            result,
                            activation);
            candidates = map.candidatesForRecordedHash(queryHash);
            hashAccepted = true;
        }

        boolean needsEquality() {
            requireHashAccepted();
            if (match != null) {
                return false;
            }
            return index < candidates.size();
        }

        PreparedClosureCall prepareEquality() {
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map.atPut equality callback requested without a candidate");
            }
            return ProtosSendPreparation.prepareSend(
                    key,
                    "==",
                    activation,
                    List.of(candidates.get(index).key()));
        }

        void acceptEquality(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.atPut equality result accepted before comparison-scope exit");
            }
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map.atPut equality result accepted without a candidate");
            }
            boolean equal =
                    ProtosStandardMapProtocol.requireEqualityResultForStructured(
                            result,
                            activation);
            if (equal) {
                match = candidates.get(index);
            } else {
                index++;
            }
        }

        Object finish() {
            requireHashAccepted();
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.atPut finished with an active comparison scope");
            }
            if (needsEquality()) {
                throw new IllegalStateException(
                        "Map.atPut finished before candidate exhaustion");
            }
            if (match != null) {
                if (map.isFrozen()) {
                    throw ProtosCoreErrors.signal(
                            activation,
                            ProtosCoreErrors.newError(activation));
                }
                map.replaceValue(match, newValue);
                return newValue;
            }
            if (!map.isOpen()) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            map.append(key, queryHash, newValue);
            return newValue;
        }

        private void requireHashAccepted() {
            if (!hashAccepted) {
                throw new IllegalStateException(
                        "Map.atPut used before hash acceptance");
            }
        }
    }

    static final class PreparedMapRemoveCall {
        private final ProtosMapValue map;
        private final Object key;
        private final ProtosActivation activation;
        private ProtosNumericHashKey queryHash;
        private List<ProtosMapValue.Entry> candidates;
        private int index;
        private ProtosMapValue.Entry match;
        private boolean hashAccepted;
        private boolean comparisonEntered;

        PreparedMapRemoveCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            if (!(receiver instanceof ProtosMapValue mapValue)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.map = mapValue;
            ProtosStandardMapProtocol.requireMutationEntryForStructured(map, activation);
            if (!map.isOpen()) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.key = supplied.get(0);
        }

        void enterComparison() {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.remove attempted to nest its own comparison scope");
            }
            map.enterComparison();
            comparisonEntered = true;
        }

        void leaveComparison() {
            if (!comparisonEntered) {
                throw new IllegalStateException(
                        "Map.remove comparison scope left without entry");
            }
            map.leaveComparison();
            comparisonEntered = false;
        }

        PreparedClosureCall prepareHash() {
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map.remove hash callback prepared after hash acceptance");
            }
            return ProtosSendPreparation.prepareSend(key, "hash", activation, List.of());
        }

        void acceptHash(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.remove hash result accepted before comparison-scope exit");
            }
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map.remove hash result accepted twice");
            }
            queryHash =
                    ProtosStandardMapProtocol.requireHashResultForStructured(
                            result,
                            activation);
            candidates = map.candidatesForRecordedHash(queryHash);
            hashAccepted = true;
        }

        boolean needsEquality() {
            requireHashAccepted();
            if (match != null) {
                return false;
            }
            return index < candidates.size();
        }

        PreparedClosureCall prepareEquality() {
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map.remove equality callback requested without a candidate");
            }
            return ProtosSendPreparation.prepareSend(
                    key,
                    "==",
                    activation,
                    List.of(candidates.get(index).key()));
        }

        void acceptEquality(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.remove equality result accepted before comparison-scope exit");
            }
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map.remove equality result accepted without a candidate");
            }
            boolean equal =
                    ProtosStandardMapProtocol.requireEqualityResultForStructured(
                            result,
                            activation);
            if (equal) {
                match = candidates.get(index);
            } else {
                index++;
            }
        }

        Object finish() {
            requireHashAccepted();
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.remove finished with an active comparison scope");
            }
            if (needsEquality()) {
                throw new IllegalStateException(
                        "Map.remove finished before candidate exhaustion");
            }
            if (match == null || !map.isOpen()) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            return map.remove(match);
        }

        private void requireHashAccepted() {
            if (!hashAccepted) {
                throw new IllegalStateException(
                        "Map.remove used before hash acceptance");
            }
        }
    }
}

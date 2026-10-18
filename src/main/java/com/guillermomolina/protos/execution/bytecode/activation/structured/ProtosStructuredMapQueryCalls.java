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
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.numeric.ProtosNumericHashKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Prepared state of the structured Map match and read-lookup invocations,
 * which compare guest keys through C-prime hash and equality calls.
 */
final class ProtosStructuredMapQueryCalls {
    private ProtosStructuredMapQueryCalls() {}

    static final class PreparedMapMatchCall {
        private final ProtosActivation activation;
        private final ProtosMapValue subject;
        private final List<ProtosStandardMapProtocol.StableAssociation> matcherSnapshot;
        private final List<ProtosStandardMapProtocol.StableAssociation> subjectSnapshot;
        private final List<Object> childMatchers = new ArrayList<>();
        private final List<Object> selectedValues = new ArrayList<>();
        private final List<Object> captures = new ArrayList<>();

        private int requirementIndex;
        private int candidateIndex;
        private int childIndex;

        private ProtosNumericHashKey queryHash;
        private ProtosStandardMapProtocol.StableAssociation selectedAssociation;

        private boolean hashAccepted;
        private boolean comparisonEntered;
        private boolean mismatch;

        PreparedMapMatchCall(
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.activation =
                    java.util.Objects.requireNonNull(
                            activation,
                            "activation");

            if (!(receiver instanceof ProtosMapValue matcher)
                    || supplied.size() != 1) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            Object subjectValue = supplied.get(0);
            if (!(subjectValue instanceof ProtosMapValue subjectMap)) {
                subject = null;
                matcherSnapshot = List.of();
                subjectSnapshot = List.of();
                mismatch = true;
                return;
            }

            subject = subjectMap;
            matcherSnapshot =
                    ProtosStandardMapProtocol.stableSnapshot(matcher);
            subjectSnapshot =
                    ProtosStandardMapProtocol.stableSnapshot(subjectMap);
        }

        boolean hasRequirement() {
            return !mismatch
                    && requirementIndex < matcherSnapshot.size();
        }

        void enterComparison() {
            if (!hasRequirement()) {
                throw new IllegalStateException(
                        "Map.match comparison entered without a current requirement");
            }
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.match attempted to nest its own comparison scope");
            }
            subject.enterComparison();
            comparisonEntered = true;
        }

        void leaveComparison() {
            if (!comparisonEntered) {
                throw new IllegalStateException(
                        "Map.match comparison scope left without entry");
            }
            subject.leaveComparison();
            comparisonEntered = false;
        }

        PreparedClosureCall prepareHash() {
            if (!hasRequirement() || hashAccepted) {
                throw new IllegalStateException(
                        "Map.match hash requested in an invalid state");
            }

            return ProtosSendPreparation.prepareSend(
                    matcherSnapshot.get(requirementIndex).key(),
                    "hash",
                    activation,
                    List.of());
        }

        void acceptHash(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.match hash accepted before comparison-scope exit");
            }
            if (!hasRequirement() || hashAccepted) {
                throw new IllegalStateException(
                        "Map.match hash accepted in an invalid state");
            }

            queryHash =
                    ProtosStandardMapProtocol.requireHashResultForStructured(
                            result,
                            activation);
            candidateIndex = 0;
            selectedAssociation = null;
            hashAccepted = true;
        }

        boolean needsEquality() {
            requireHashAccepted();

            if (selectedAssociation != null) {
                return false;
            }

            while (candidateIndex < subjectSnapshot.size()
                    && !subjectSnapshot.get(candidateIndex)
                            .recordedHash()
                            .equals(queryHash)) {
                candidateIndex++;
            }

            return candidateIndex < subjectSnapshot.size();
        }

        PreparedClosureCall prepareEquality() {
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map.match equality requested without a candidate");
            }

            return ProtosSendPreparation.prepareSend(
                    matcherSnapshot.get(requirementIndex).key(),
                    "==",
                    activation,
                    List.of(subjectSnapshot.get(candidateIndex).key()));
        }

        void acceptEquality(Object result) {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.match equality accepted before comparison-scope exit");
            }
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map.match equality accepted without a candidate");
            }

            boolean equal =
                    ProtosStandardMapProtocol.requireEqualityResultForStructured(
                            result,
                            activation);

            if (equal) {
                selectedAssociation =
                        subjectSnapshot.get(candidateIndex);
            } else {
                candidateIndex++;
            }
        }

        void finishRequirement() {
            requireHashAccepted();

            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.match requirement finished with an active comparison scope");
            }
            if (needsEquality()) {
                throw new IllegalStateException(
                        "Map.match requirement finished before candidate exhaustion");
            }

            if (selectedAssociation == null) {
                mismatch = true;
                return;
            }

            ProtosStandardMapProtocol.StableAssociation requirement =
                    matcherSnapshot.get(requirementIndex);

            childMatchers.add(requirement.value());
            selectedValues.add(selectedAssociation.value());

            requirementIndex++;
            candidateIndex = 0;
            queryHash = null;
            selectedAssociation = null;
            hashAccepted = false;
        }

        boolean hasChildMatcher() {
            return !mismatch && childIndex < childMatchers.size();
        }

        PreparedClosureCall prepareChildMatcher() {
            if (!hasChildMatcher()) {
                throw new IllegalStateException(
                        "Map.match child matcher requested after terminal outcome");
            }

            return ProtosSendPreparation.prepareSend(
                    childMatchers.get(childIndex),
                    "match",
                    activation,
                    List.of(selectedValues.get(childIndex)));
        }

        void acceptChildOutcome(Object outcome) {
            if (!hasChildMatcher()) {
                throw new IllegalStateException(
                        "Map.match child outcome accepted after terminal outcome");
            }

            if (outcome == ProtosBooleanValue.FALSE) {
                mismatch = true;
                return;
            }

            if (outcome == ProtosBooleanValue.TRUE) {
                childIndex++;
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
                childIndex++;
                return;
            }

            throw ProtosCoreErrors.signal(
                    activation,
                    ProtosCoreErrors.newError(activation));
        }

        Object finish() {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map.match finished with an active comparison scope");
            }
            if (hasRequirement()) {
                throw new IllegalStateException(
                        "Map.match finished before requirement resolution completed");
            }
            if (hasChildMatcher()) {
                throw new IllegalStateException(
                        "Map.match finished before child matcher exhaustion");
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
                                            "standard Map.match requires an owning Core prelude"))
                    .newArray(captures);
        }

        private void requireHashAccepted() {
            if (!hashAccepted) {
                throw new IllegalStateException(
                        "Map.match requirement used before hash acceptance");
            }
        }
    }

    static final class PreparedMapReadLookupCall {
        private final ProtosStandardMapProtocol.StructuredReadLookupKind kind;
        private final ProtosMapValue map;
        private final Object key;
        private final Object fallback;
        private final ProtosActivation activation;
        private ProtosNumericHashKey queryHash;
        private List<ProtosMapValue.Entry> candidates;
        private int index;
        private ProtosMapValue.Entry match;
        private boolean hashAccepted;
        private boolean comparisonEntered;
        private boolean fallbackPrepared;

        PreparedMapReadLookupCall(
                ProtosStandardMapProtocol.StructuredReadLookupKind kind,
                Object receiver,
                List<?> supplied,
                ProtosActivation activation) {
            this.kind = java.util.Objects.requireNonNull(kind, "kind");
            this.activation = java.util.Objects.requireNonNull(activation, "activation");
            int expectedArity =
                    kind == ProtosStandardMapProtocol.StructuredReadLookupKind.AT_IF_ABSENT
                            ? 2
                            : 1;
            if (!(receiver instanceof ProtosMapValue value)
                    || supplied.size() != expectedArity) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            this.map = value;
            this.key = supplied.get(0);
            this.fallback =
                    kind == ProtosStandardMapProtocol.StructuredReadLookupKind.AT_IF_ABSENT
                            ? supplied.get(1)
                            : null;
        }

        void enterComparison() {
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map read lookup attempted to nest its own comparison scope");
            }
            map.enterComparison();
            comparisonEntered = true;
        }

        void leaveComparison() {
            if (!comparisonEntered) {
                throw new IllegalStateException(
                        "Map read lookup comparison scope left without entry");
            }
            map.leaveComparison();
            comparisonEntered = false;
        }

        PreparedClosureCall prepareHash() {
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map read lookup hash callback prepared after hash acceptance");
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
                        "Map read lookup hash result accepted before comparison-scope exit");
            }
            if (hashAccepted) {
                throw new IllegalStateException(
                        "Map read lookup hash result accepted twice");
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
                        "Map read lookup equality callback requested without a candidate");
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
                        "Map read lookup equality result accepted before comparison-scope exit");
            }
            if (!needsEquality()) {
                throw new IllegalStateException(
                        "Map read lookup equality result accepted without a candidate");
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

        boolean needsFallback() {
            requireSearchComplete();
            return kind == ProtosStandardMapProtocol.StructuredReadLookupKind.AT_IF_ABSENT
                    && match == null;
        }

        PreparedClosureCall prepareFallback() {
            if (!needsFallback() || fallbackPrepared) {
                throw new IllegalStateException(
                        "Map.atIfAbsent fallback requested in an invalid state");
            }
            fallbackPrepared = true;
            return ProtosClosureCallPreparation.prepareClosureCall(
                    fallback,
                    List.of(),
                    activation);
        }

        Object finishFallback(Object result) {
            requireSearchComplete();
            if (kind != ProtosStandardMapProtocol.StructuredReadLookupKind.AT_IF_ABSENT
                    || match != null
                    || !fallbackPrepared) {
                throw new IllegalStateException(
                        "Map.atIfAbsent fallback result accepted in an invalid state");
            }
            return result;
        }

        Object finish() {
            requireSearchComplete();

            return switch (kind) {
                case AT -> {
                    if (match == null) {
                        throw ProtosCoreErrors.signal(
                                activation,
                                ProtosCoreErrors.newError(activation));
                    }
                    yield match.value();
                }
                case CONTAINS_KEY ->
                        match == null
                                ? ProtosBooleanValue.FALSE
                                : ProtosBooleanValue.TRUE;
                case AT_IF_ABSENT -> {
                    if (match == null) {
                        throw new IllegalStateException(
                                "Map.atIfAbsent absent path requires its fallback result");
                    }
                    yield match.value();
                }
            };
        }

        private void requireSearchComplete() {
            requireHashAccepted();
            if (comparisonEntered) {
                throw new IllegalStateException(
                        "Map read lookup finished with an active comparison scope");
            }
            if (needsEquality()) {
                throw new IllegalStateException(
                        "Map read lookup used before candidate exhaustion");
            }
        }

        private void requireHashAccepted() {
            if (!hashAccepted) {
                throw new IllegalStateException(
                        "Map read lookup used before hash acceptance");
            }
        }
    }
}

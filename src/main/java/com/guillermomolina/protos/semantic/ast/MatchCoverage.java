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

package com.guillermomolina.protos.semantic.ast;

import java.util.Objects;
import java.util.Optional;

/**
 * Compiler-only D096 coverage proof attached to one canonical match expression.
 *
 * <p>This is not guest-visible matcher metadata and is never consulted to change
 * D071-D095 observations.</p>
 */
public record MatchCoverage(
        Status status,
        Optional<Witness> witness,
        boolean budgetExhausted) {
    public MatchCoverage {
        Objects.requireNonNull(status, "status");
        witness = Objects.requireNonNull(witness, "witness");

        if (status != Status.PROVEN_NON_EXHAUSTIVE && witness.isPresent()) {
            throw new IllegalArgumentException(
                    "coverage witness requires PROVEN_NON_EXHAUSTIVE");
        }
        if (budgetExhausted && status != Status.UNKNOWN) {
            throw new IllegalArgumentException(
                    "budget exhaustion must fail coverage analysis to UNKNOWN");
        }
    }

    public static MatchCoverage provenExhaustive() {
        return new MatchCoverage(
                Status.PROVEN_EXHAUSTIVE,
                Optional.empty(),
                false);
    }

    public static MatchCoverage provenNonExhaustive(Witness witness) {
        return new MatchCoverage(
                Status.PROVEN_NON_EXHAUSTIVE,
                Optional.of(Objects.requireNonNull(witness, "witness")),
                false);
    }

    public static MatchCoverage unknown() {
        return new MatchCoverage(Status.UNKNOWN, Optional.empty(), false);
    }

    public static MatchCoverage budgetUnknown() {
        return new MatchCoverage(Status.UNKNOWN, Optional.empty(), true);
    }

    public enum Status {
        PROVEN_EXHAUSTIVE,
        PROVEN_NON_EXHAUSTIVE,
        UNKNOWN
    }

    public enum Witness {
        /** At least one ordinary value outside standard Array/Map eligibility remains uncovered. */
        NON_ARRAY_OR_MAP_VALUE
    }
}

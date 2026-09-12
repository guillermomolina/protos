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

package com.guillermomolina.protos.semantic;

import com.guillermomolina.protos.parser.ast.SurfaceMatch;
import com.guillermomolina.protos.parser.ast.SurfaceMatchPattern;
import com.guillermomolina.protos.semantic.ast.MatchCoverage;
import java.util.Objects;

/**
 * Sound resource-bounded D096 coverage analysis for the Core v0.1 match algebra.
 *
 * <p>The analyzer deliberately proves only facts owned by ratified source
 * semantics. Ordinary matcher/value patterns and explicit guards are opaque.
 * When the available facts are insufficient, or the budget is exhausted, the
 * result is UNKNOWN rather than an invented non-exhaustiveness claim.</p>
 */
public final class MatchCoverageAnalyzer {
    public static final int DEFAULT_NODE_BUDGET = 4096;

    private MatchCoverageAnalyzer() {}

    public static MatchCoverage analyze(SurfaceMatch match) {
        return analyze(match, DEFAULT_NODE_BUDGET);
    }

    public static MatchCoverage analyze(
            SurfaceMatch match,
            int nodeBudget) {
        Objects.requireNonNull(match, "match");
        if (nodeBudget < 1) {
            throw new IllegalArgumentException("nodeBudget must be >= 1");
        }

        Budget budget = new Budget(nodeBudget);
        boolean allArmsExcludeKnownWitness = true;

        try {
            for (SurfaceMatch.Arm arm : match.arms()) {
                budget.spend();
                PatternFacts facts = facts(arm.pattern(), budget);

                if (arm.guard().isEmpty()
                        && facts.syntacticallyUniversallyIrrefutable()) {
                    return MatchCoverage.provenExhaustive();
                }

                if (!facts.definitelyExcludesNonArrayOrMapWitness()) {
                    allArmsExcludeKnownWitness = false;
                }
            }
        } catch (BudgetExceeded ignored) {
            return MatchCoverage.budgetUnknown();
        }

        if (allArmsExcludeKnownWitness) {
            return MatchCoverage.provenNonExhaustive(
                    MatchCoverage.Witness.NON_ARRAY_OR_MAP_VALUE);
        }

        return MatchCoverage.unknown();
    }

    private static PatternFacts facts(
            SurfaceMatchPattern pattern,
            Budget budget) {
        budget.spend();

        if (pattern instanceof SurfaceMatchPattern.Binder
                || pattern instanceof SurfaceMatchPattern.Wildcard) {
            return new PatternFacts(true, false);
        }

        if (pattern instanceof SurfaceMatchPattern.Alias alias) {
            return facts(alias.pattern(), budget);
        }

        if (pattern instanceof SurfaceMatchPattern.Group group) {
            return facts(group.pattern(), budget);
        }

        if (pattern instanceof SurfaceMatchPattern.Or orPattern) {
            boolean irrefutable = false;
            boolean excludesWitness = true;
            for (SurfaceMatchPattern alternative : orPattern.alternatives()) {
                PatternFacts alternativeFacts = facts(alternative, budget);
                irrefutable |=
                        alternativeFacts.syntacticallyUniversallyIrrefutable();
                excludesWitness &=
                        alternativeFacts.definitelyExcludesNonArrayOrMapWitness();
            }
            return new PatternFacts(irrefutable, excludesWitness);
        }

        if (pattern instanceof SurfaceMatchPattern.ArrayPattern
                || pattern instanceof SurfaceMatchPattern.MapPattern) {
            /*
             * D084/D086 eligibility alone is sufficient for this witness:
             * a normal value that is neither a standard Array nor a normal
             * standard Map mismatches before nested/query behavior matters.
             */
            return new PatternFacts(false, true);
        }

        if (pattern instanceof SurfaceMatchPattern.Value) {
            /*
             * D073/D081 ordinary matcher behavior is coverage-opaque. It may
             * recognize the known witness, mismatch it, or propagate effects.
             */
            return new PatternFacts(false, false);
        }

        throw new IllegalStateException(
                "Unknown SurfaceMatchPattern: " + pattern.getClass().getName());
    }

    private record PatternFacts(
            boolean syntacticallyUniversallyIrrefutable,
            boolean definitelyExcludesNonArrayOrMapWitness) {}

    private static final class Budget {
        private int remaining;

        Budget(int remaining) {
            this.remaining = remaining;
        }

        void spend() {
            if (remaining == 0) {
                throw BudgetExceeded.INSTANCE;
            }
            remaining--;
        }
    }

    private static final class BudgetExceeded extends RuntimeException {
        private static final BudgetExceeded INSTANCE = new BudgetExceeded();

        private BudgetExceeded() {
            super(null, null, false, false);
        }
    }
}

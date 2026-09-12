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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceMatch;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.ast.MatchCoverage;
import org.junit.jupiter.api.Test;

class MatchCoverageAnalyzerTest {
    @Test
    void provesStableUnguardedIrrefutableFormsExhaustive() {
        assertStatus(
                "subject match {\n"
                        + "case _ => 1\n"
                        + "}",
                MatchCoverage.Status.PROVEN_EXHAUSTIVE);

        assertStatus(
                "subject match {\n"
                        + "case @whole: _ => whole\n"
                        + "}",
                MatchCoverage.Status.PROVEN_EXHAUSTIVE);

        assertStatus(
                "subject match {\n"
                        + "case opaque | _ => 1\n"
                        + "}",
                MatchCoverage.Status.PROVEN_EXHAUSTIVE);
    }

    @Test
    void guardsAndOrdinaryMatchersRemainCoverageOpaque() {
        assertStatus(
                "subject match {\n"
                        + "case _ when predicate() => 1\n"
                        + "}",
                MatchCoverage.Status.UNKNOWN);

        assertStatus(
                "subject match {\n"
                        + "case matcher => 1\n"
                        + "}",
                MatchCoverage.Status.UNKNOWN);

        assertStatus(
                "subject match {\n"
                        + "case [@x] => x\n"
                        + "case matcher => 1\n"
                        + "}",
                MatchCoverage.Status.UNKNOWN);
    }

    @Test
    void provesStructuralArrayMapOnlyMatchesNonExhaustiveWithSoundWitness() {
        MatchCoverage coverage =
                MatchCoverageAnalyzer.analyze(
                        parseMatch(
                                "subject match {\n"
                                        + "case [@x] => x\n"
                                        + "case %{} => 2\n"
                                        + "case exact %{} => 3\n"
                                        + "}"));

        assertEquals(
                MatchCoverage.Status.PROVEN_NON_EXHAUSTIVE,
                coverage.status());
        assertEquals(
                MatchCoverage.Witness.NON_ARRAY_OR_MAP_VALUE,
                coverage.witness().orElseThrow());
        assertFalse(coverage.budgetExhausted());
    }

    @Test
    void aliasesAndOrOfStructuralPatternsPreserveKnownWitnessProof() {
        MatchCoverage coverage =
                MatchCoverageAnalyzer.analyze(
                        parseMatch(
                                "subject match {\n"
                                        + "case @whole: ([...] | %{}) => whole\n"
                                        + "}"));

        assertEquals(
                MatchCoverage.Status.PROVEN_NON_EXHAUSTIVE,
                coverage.status());
        assertEquals(
                MatchCoverage.Witness.NON_ARRAY_OR_MAP_VALUE,
                coverage.witness().orElseThrow());
    }

    @Test
    void budgetExhaustionFailsToUnknown() {
        SurfaceMatch match =
                parseMatch(
                        "subject match {\n"
                                + "case @a: @b: @c: _ => a\n"
                                + "}");

        MatchCoverage coverage =
                MatchCoverageAnalyzer.analyze(match, 2);

        assertEquals(MatchCoverage.Status.UNKNOWN, coverage.status());
        assertTrue(coverage.budgetExhausted());
        assertTrue(coverage.witness().isEmpty());
    }

    private static void assertStatus(
            String source,
            MatchCoverage.Status expected) {
        MatchCoverage coverage =
                MatchCoverageAnalyzer.analyze(parseMatch(source));
        assertEquals(expected, coverage.status());
        assertFalse(coverage.budgetExhausted());
    }

    private static SurfaceMatch parseMatch(String source) {
        SurfaceSequence sequence = new ProtosParser(source).parseProgram();
        return (SurfaceMatch) sequence.expressions().get(0);
    }
}

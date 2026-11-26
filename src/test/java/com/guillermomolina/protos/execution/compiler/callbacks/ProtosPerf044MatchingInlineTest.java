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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PERF044: ordinary {@code caseOf} sends, whatever their source spelling,
 * run their standard recognition in the enclosing root as compiler-visible
 * alternatives, each selecting its matcher through its own ordinary
 * {@code GetMethod}/{@code CallUnaryMethod} site, with no case-table call
 * request; standard Array.match and Map.match children use the same send.
 */
final class ProtosPerf044MatchingInlineTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    private static final String CONTEXTUAL = """
            value: 2
            match(value) {
                0: 11
                1: 23
                2: 37
                Any: 53
            }
            """;

    private static final String EXPLICIT = """
            value: 2
            value.caseOf(%{
                0: () => 11
                1: () => 23
                2: () => 37
                Any: () => 53
            })
            """;

    @Test
    void contextualAndExplicitCaseOfConvergeOnTheSameAdmittedPath() {
        List<String> contextual = names(CONTEXTUAL);
        List<String> explicit = names(EXPLICIT);
        assertEquals(contextual, explicit,
                "both spellings are one canonical send and lower identically");
        assertEquals(1, count(contextual, "GetCaseOfSnapshot"));
        assertEquals(0, count(contextual, "PrepareStructuredCaseOf"));
    }

    @Test
    void fixedTableRunsEachActionInsideItsOwnAlternative() {
        List<String> names = names(EXPLICIT);
        // Four written alternatives plus the trailing snapshot loop.
        assertEquals(5, count(names, "StructuredMatcher"));
        // Only a written alternative commits in place and branches to the end.
        assertEquals(4, count(names, "CaseOfCommitted"));
        // One action site per written alternative, one after the loop.
        assertEquals(5, count(names, "CaseOfSelectedAction"));
        assertTrue(count(names, "CallNilaryMethod") >= 5,
                "each alternative invokes its own action through its own site");
        assertEquals(0, count(names, "PrepareStructuredCaseOf"),
                "no case matcher or action goes through the structured root");
        assertTrue(names.stream().anyMatch(name -> name.equals("branch")),
                "a completed alternative branches to the common end");
    }

    @Test
    void dynamicTableEntersTheSameRecognitionLoop() {
        List<String> names = names("""
                cases: %{ 0: () => 1; Any: () => 2 }
                5.caseOf(cases)
                """);
        assertEquals(1, count(names, "GetCaseOfSnapshot"));
        // The caseOf argument is a lookup: only the trailing loop is emitted.
        assertEquals(1, count(names, "StructuredMatcher"));
        assertEquals(0, count(names, "CaseOfCommitted"));
        assertEquals(1, count(names, "CaseOfSelectedAction"));
    }

    @Test
    void spreadPacketsKeepTheOrdinaryInvocation() {
        List<String> names = names("""
                packet: [%{ Any: () => 1 }]
                5.caseOf(...packet)
                """);
        assertEquals(0, count(names, "GetCaseOfSnapshot"));
    }

    @Test
    void admittedPathPreservesResultsOfEveryMatcherFamily() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        Object result = ProtosTestExecutionSupport.evaluate("""
                Custom: { match: (subject) => subject == "custom" }
                select: (value) => match(value) {
                    0: "zero"
                    1.5: "float"
                    true: "boolean"
                    "text": "string"
                    null: "null"
                    [Capture, 2]: (first) => first
                    %{ "k": Capture }: (mapped) => mapped
                    [[Capture], %{ "n": Capture }]: (a, b) => a + b
                    Custom: "custom matcher"
                    Any: "any"
                }
                (select(0) == "zero") && (select(1.5) == "float") &&
                    (select(true) == "boolean") && (select("text") == "string") &&
                    (select(null) == "null") && (select([9, 2]) == 9) &&
                    (select(%{ "k": 4; "extra": 0 }) == 4) &&
                    (select([[3], %{ "n": 4 }]) == 7) &&
                    (select("custom") == "custom matcher") && (select(99) == "any")
                """, prelude.newModuleActivation());
        assertSame(Boolean.TRUE, result);
    }

    @Test
    void standaloneMatchKeepsTheActualResult() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        Object result = ProtosTestExecutionSupport.evaluate("""
                Odd: { match: (subject) => 7 }
                single: Capture.match(5)
                pair: [Capture, Capture].match([1, 2])
                mapped: %{ "a": Capture }.match(%{ "a": 3 })
                (Odd.match(1) == 7) && (Any.match(1) == true) && (1.match(1) == true) &&
                    (single.size() == 1) && (single[0] == 5) &&
                    (pair.size() == 2) && (pair[0] == 1) && (pair[1] == 2) &&
                    (mapped.size() == 1) && (mapped[0] == 3)
                """, prelude.newModuleActivation());
        assertSame(Boolean.TRUE, result);
    }

    private static int count(List<String> names, String operation) {
        return (int) names.stream().filter(name -> name.endsWith(operation)).count();
    }

    private static List<String> names(String characters) {
        return ProtosTestExecutionSupport.callEntered(() -> lower(characters)
                .getBytecodeNode().getInstructionsAsList().stream()
                .map(Instruction::getName)
                .toList());
    }

    private static ProtosBytecodeRootNode lower(String characters) {
        Source source = Source.newBuilder(ProtosLanguage.ID, characters, "matching-inline.protos").build();
        CanonicalSequence sequence =
                (CanonicalSequence)
                        new Canonicalizer().canonicalize(new ProtosParser(characters).parseProgram());
        return new CanonicalToBytecodeLowerer(LANGUAGE_REF.get(null), source).lowerRoot(sequence);
    }
}

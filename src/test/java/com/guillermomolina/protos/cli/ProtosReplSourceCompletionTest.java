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
package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ProtosReplSourceCompletionTest {
    private static Optional<ProtosReplSourceCompletion.Result> complete(
            String pending, String line, int cursor,
            String... liveNames) {
        return ProtosReplSourceCompletion.complete(
                pending, line, cursor, List.of(liveNames));
    }

    private static List<String> labels(
            ProtosReplSourceCompletion.Result result) {
        return result.candidates().stream()
                .map(ProtosReplSourceCompletion.Candidate::label)
                .toList();
    }

    @Test
    void liveNamesAndStaticProofsRetainOrderedProvenance() {
        String line = "f: (first, second) => fir";
        var result = complete("", line, line.length(),
                "fire", "first").orElseThrow();

        assertEquals(line.lastIndexOf("fir"), result.replaceStart());
        assertEquals(line.length(), result.replaceEnd());
        assertEquals(List.of("fire", "first"), labels(result));

        assertEquals(
                ProtosReplSourceCompletion.Kind.LIVE_BINDING,
                result.candidates().get(0).kind());
        assertEquals(
                ProtosReplSourceCompletion.Kind.LIVE_BINDING,
                result.candidates().get(1).kind());
    }

    @Test
    void liveNamesOnlyExistWhenActuallySuppliedBySession() {
        assertTrue(
                complete("", "sess", 4).isEmpty(),
                "without a live binding or matching syntax, no candidate exists");

        var present = complete(
                "", "sess", 4, "sessionVariable").orElseThrow();
        assertEquals(List.of("sessionVariable"), labels(present));
    }

    @Test
    void usesActualLexerAndParserForMiddleOfIdentifier() {
        var result = complete(
                "", "sesion", 3, "session").orElseThrow();

        assertEquals(0, result.replaceStart());
        assertEquals(6, result.replaceEnd());
        assertEquals(List.of("session"), labels(result));

        assertTrue(complete(
                "", "sesson", 3, "session").isEmpty());
    }

    @Test
    void excludesMembersDeclarationsAndInvalidGuestNames() {
        assertTrue(complete(
                "", "object.member", 13, "member").isEmpty());
        assertTrue(complete(
                "", "created: 1", 7, "createdAgain").isEmpty());

        var result = complete("", "fi", 2,
                "first", "two words", "this", "fi")
                .orElseThrow();
        assertEquals(List.of("first", "fi"), labels(result));
    }

    @Test
    void grammarAcceptsReservedSyntaxButNotBareSuper() {
        var result = complete("", "th", 2).orElseThrow();
        assertEquals(
                List.of("this"),
                result.candidates().stream()
                        .filter(c -> c.kind()
                                == ProtosReplSourceCompletion.Kind.SYNTAX)
                        .map(ProtosReplSourceCompletion.Candidate::label)
                        .toList());

        assertFalse(complete("", "", 0).orElseThrow()
                .candidates().stream()
                .anyMatch(c -> c.label().equals("super")));
    }

    @Test
    void rejectsStringsCommentsAndMalformedSource() {
        assertTrue(complete(
                "", "\"session\"", 5, "session").isEmpty());
        assertTrue(complete(
                "", "// session", 10, "session").isEmpty());
        assertTrue(complete(
                "", "/* session */", 6, "session").isEmpty());
        assertTrue(complete(
                "", "f: (x) => {", 11, "session").isEmpty());
        assertTrue(complete(
                "", "'unterminated", 12, "session").isEmpty());
    }

    @Test
    void pendingMultilineSourceParticipatesInProof() {
        var result = complete(
                "f: (first) => {",
                "  first\n}",
                7,
                "first").orElseThrow();

        assertEquals(2, result.replaceStart());
        assertEquals(7, result.replaceEnd());
        assertEquals(List.of("first"), labels(result));
    }

    @Test
    void unicodeAndSourceBoundariesAreConservative() {
        var result = complete("", "caf", 3, "café")
                .orElseThrow();
        assertEquals(List.of("café"), labels(result));

        assertTrue(complete(
                "", "ca", 2, "ca\u0301").isEmpty());
        assertTrue(complete(
                "", "😀", 1, "emoji").isEmpty());
        assertTrue(complete(
                "", ":he", 3, "help").isEmpty());
        assertTrue(complete(
                "", "x", -1, "x").isEmpty());

        assertTrue(complete(
                "x".repeat(65536), "x", 1, "x").isEmpty());
    }
}

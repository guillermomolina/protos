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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jline.reader.Candidate;
import org.jline.reader.CompletingParsedLine;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.impl.CompletionMatcherImpl;
import org.jline.reader.impl.DefaultParser;
import org.junit.jupiter.api.Test;

final class ProtosReplJLineCompleterTest {
    private static ParsedLine parsed(String text, int cursor) {
        return new DefaultParser().parse(
                text, cursor, Parser.ParseContext.COMPLETE);
    }

    private static List<Candidate> candidates(String text, int cursor) {
        List<Candidate> result = new ArrayList<>();
        new ProtosReplJLineCompleter().complete(
                null, parsed(text, cursor), result);
        return result;
    }

    @Test
    void projectsExistingCommandsWithoutTrailingWhitespace() {
        List<Candidate> results = candidates(":", 1);

        assertEquals(
                List.of(":help", ":quit", ":exit"),
                results.stream().map(Candidate::value).toList());

        for (Candidate candidate : results) {
            assertFalse(candidate.complete());
        }
    }

    @Test
    void selectsExactCommandAndRejectsUnknownCommands() {
        assertEquals(
                List.of(":help"),
                candidates(":he", 3).stream()
                        .map(Candidate::value).toList());

        assertEquals(
                List.of(":exit"),
                candidates(":exit", 5).stream()
                        .map(Candidate::value).toList());

        assertTrue(candidates(":unknown", 8).isEmpty());
    }

    @Test
    void rejectsNonCommandWordsAndQuotedInput() {
        assertTrue(candidates("object.member", 13).isEmpty());
        assertTrue(candidates(" :he", 4).isEmpty());
        assertTrue(candidates(":he extra", 3).isEmpty());
        assertTrue(candidates(":h\\e", 4).isEmpty());
    }

    @Test
    void jlineMatcherSupportsCompletionInsideAWord() {
        ParsedLine line = parsed(":hlp", 2);

        List<Candidate> available = new ArrayList<>();
        new ProtosReplJLineCompleter().complete(
                null, line, available);

        assertEquals(
                List.of(":help"),
                available.stream().map(Candidate::value).toList());

        CompletionMatcherImpl matcher = new CompletionMatcherImpl();
        matcher.compile(
                Map.of(
                        LineReader.Option.COMPLETE_IN_WORD, true,
                        LineReader.Option.COMPLETE_MATCHER_TYPO, false),
                false,
                (CompletingParsedLine) line,
                false,
                0,
                "Others");

        assertEquals(
                List.of(":help"),
                matcher.matches(available).stream()
                        .map(Candidate::value).toList());
    }
}

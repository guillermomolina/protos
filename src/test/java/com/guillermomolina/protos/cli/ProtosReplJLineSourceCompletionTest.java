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
import java.util.concurrent.atomic.AtomicInteger;
import org.jline.reader.Candidate;
import org.jline.reader.CompletingParsedLine;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.impl.CompletionMatcherImpl;
import org.jline.reader.impl.DefaultParser;
import org.junit.jupiter.api.Test;

final class ProtosReplJLineSourceCompletionTest {
    private static ParsedLine parsed(String line, int cursor) {
        return new DefaultParser().parse(
                line, cursor, Parser.ParseContext.COMPLETE);
    }

    private static List<Candidate> candidates(
            String pending, String line, int cursor, String... names) {
        var result = new ArrayList<Candidate>();
        new ProtosReplJLineCompleter(
                () -> pending,
                () -> List.of(names))
                .complete(null, parsed(line, cursor), result);
        return result;
    }

    private static List<String> values(List<Candidate> candidates) {
        return candidates.stream().map(Candidate::value).toList();
    }

    private static List<String> matched(
            ParsedLine line, List<Candidate> candidates) {
        var matcher = new CompletionMatcherImpl();
        matcher.compile(
                Map.of(
                        LineReader.Option.COMPLETE_IN_WORD, true,
                        LineReader.Option.COMPLETE_MATCHER_TYPO, false),
                false,
                (CompletingParsedLine) line,
                false,
                0,
                "Others");
        return values(matcher.matches(candidates));
    }

    @Test
    void liveSessionNamesBecomeJLineCandidates() {
        assertEquals(
                List.of("sessionVariable"),
                values(candidates(
                        "", "sess", 4, "sessionVariable")));

        assertTrue(candidates("", "sess", 4).isEmpty());
    }

    @Test
    void middleOfIdentifierUsesJLineInWordMatcher() {
        ParsedLine line = parsed("sesion", 3);
        List<Candidate> candidates =
                candidates("", "sesion", 3, "session");

        assertEquals(List.of("session"), values(candidates));
        assertEquals(List.of("session"), matched(line, candidates));
        assertFalse(candidates.get(0).complete());
    }

    @Test
    void preservesSurroundingProtosExpression() {
        String source = "print(sess)";
        int cursor = "print(sess".length();

        List<Candidate> candidates =
                candidates("", source, cursor, "session");

        assertEquals(
                List.of("print(session)"),
                values(candidates));
        assertEquals(
                List.of("print(session)"),
                matched(parsed(source, cursor), candidates));
        assertEquals("session", candidates.get(0).displ());
    }

    @Test
    void supportsValidMultilinePendingSource() {
        String pending = "f: (first) => {";
        String line = "  fir\\n}";
        // A literal backslash-n is not a Protos source newline;
        // use the actual edited multiline input.
        line = "  fir\n}";
        int cursor = 5;

        assertEquals(
                List.of("first"),
                values(candidates(pending, line, cursor, "first")));
    }

    @Test
    void neverInspectsRuntimeForMetacommands() {
        AtomicInteger observations = new AtomicInteger();
        var completer = new ProtosReplJLineCompleter(
                () -> "",
                () -> {
                    observations.incrementAndGet();
                    return List.of("sessionVariable");
                });
        var results = new ArrayList<Candidate>();

        completer.complete(null, parsed(":he", 3), results);

        assertEquals(List.of(":help"), values(results));
        assertEquals(0, observations.get());
    }

    @Test
    void rejectsInvalidOrNonReadPositions() {
        assertTrue(candidates(
                "", "object.member", 13, "member").isEmpty());
        assertTrue(candidates(
                "", "\"session\"", 5, "session").isEmpty());
        assertTrue(candidates(
                "", "// session", 10, "session").isEmpty());
        assertTrue(candidates(
                "", "broken: (", 9, "session").isEmpty());
    }

    @Test
    void existingCommandOnlyConstructorKeepsItsScope() {
        var completer = new ProtosReplJLineCompleter();
        var results = new ArrayList<Candidate>();

        completer.complete(null, parsed(":ex", 3), results);
        assertEquals(List.of(":exit"), values(results));

        results.clear();
        completer.complete(null, parsed("sess", 4), results);
        assertTrue(results.isEmpty());
    }

    private static List<Candidate> selectorCandidates(
            String pending, String line, int cursor,
            ProtosReplSourceCompletion.MemberName... names) {
        var result = new ArrayList<Candidate>();
        new ProtosReplJLineCompleter(
                () -> pending,
                () -> List.of(),
                site -> List.of(names))
                .complete(null, parsed(line, cursor), result);
        return result;
    }

    private static ProtosReplSourceCompletion.MemberName own(String name) {
        return new ProtosReplSourceCompletion.MemberName(name, true);
    }

    private static ProtosReplSourceCompletion.MemberName delegated(String name) {
        return new ProtosReplSourceCompletion.MemberName(name, false);
    }

    @Test
    void selectorCompletesWithinTheJLineWord() {
        ParsedLine line = parsed("dog.met", 7);
        List<Candidate> candidates =
                selectorCandidates("", "dog.met", 7, delegated("method"));

        assertEquals(List.of("dog.method"), values(candidates));
        assertEquals(List.of("dog.method"), matched(line, candidates));
        assertEquals("method", candidates.get(0).displ());
        assertEquals("delegated slot", candidates.get(0).descr());
        assertFalse(candidates.get(0).complete());

        assertEquals(
                "own slot",
                selectorCandidates("", "dog.met", 7, own("method"))
                        .get(0).descr());
    }

    @Test
    void middleOfSelectorPreservesTheCorrectCharacters() {
        ParsedLine line = parsed("dog.methd", 8);
        List<Candidate> candidates =
                selectorCandidates("", "dog.methd", 8, own("method"));

        assertEquals(List.of("dog.method"), values(candidates));
        assertEquals(List.of("dog.method"), matched(line, candidates));
    }

    @Test
    void selectorInsideExpressionKeepsTheRest() {
        String source = "print(dog.met)";
        int cursor = "print(dog.met".length();
        List<Candidate> candidates =
                selectorCandidates("", source, cursor, own("method"));

        assertEquals(List.of("print(dog.method)"), values(candidates));
        assertEquals(
                List.of("print(dog.method)"),
                matched(parsed(source, cursor), candidates));
    }

    @Test
    void groupedReceiversAndMultilinePendingSource() {
        assertEquals(
                List.of("((dog)).speak"),
                values(selectorCandidates(
                        "", "((dog)).sp", 10, own("speak"))));

        assertEquals(
                List.of("dog.speak"),
                values(selectorCandidates(
                        "f: (dog) => {", "dog.sp\n}", 6, own("speak"))));
    }

    @Test
    void emptySelectorWithoutMatchingSlotHasNoCandidates() {
        assertTrue(selectorCandidates("", "object.", 7).isEmpty());
        assertEquals(
                List.of("object.slot"),
                values(selectorCandidates("", "object.", 7, own("slot"))));
    }

    @Test
    void rejectsSpansThatTheJLineWordCannotReplace() {
        // A JLine word "th" inside "dog.meth" cannot replace the whole
        // parser-proven selector "meth", so no candidate is offered.
        var narrow = new NarrowWordLine("dog.meth", 7, 6, 8);
        var results = new ArrayList<Candidate>();
        new ProtosReplJLineCompleter(
                () -> "",
                () -> List.of(),
                site -> List.of(own("method")))
                .complete(null, narrow, results);

        assertTrue(results.isEmpty());
    }

    private record NarrowWordLine(String line, int cursor, int wordStart, int wordEnd)
            implements CompletingParsedLine {
        @Override
        public String word() {
            return line.substring(wordStart, wordEnd);
        }

        @Override
        public int wordCursor() {
            return cursor - wordStart;
        }

        @Override
        public int wordIndex() {
            return 0;
        }

        @Override
        public List<String> words() {
            return List.of(word());
        }

        @Override
        public CharSequence escape(CharSequence candidate, boolean complete) {
            return candidate;
        }

        @Override
        public int rawWordCursor() {
            return wordCursor();
        }

        @Override
        public int rawWordLength() {
            return wordEnd - wordStart;
        }
    }

    @Test
    void ambiguousSelectorsDoNotRewriteTheBuffer() {
        ParsedLine line = parsed("dog.s", 5);
        List<Candidate> candidates = selectorCandidates(
                "", "dog.s", 5, own("sit"), delegated("speak"));

        assertEquals(List.of("dog.sit", "dog.speak"), values(candidates));
        // Protos owns the candidate order above; JLine's matcher applies its
        // own presentation order, so only the matched set is asserted here.
        assertEquals(
                java.util.Set.of("dog.sit", "dog.speak"),
                java.util.Set.copyOf(matched(line, candidates)));
        assertTrue(candidates.stream().noneMatch(Candidate::complete));
    }

    @Test
    void metacommandsAndBareNamesConsultOnlyTheirOwnProviders() {
        AtomicInteger lexical = new AtomicInteger();
        AtomicInteger selectors = new AtomicInteger();
        var completer = new ProtosReplJLineCompleter(
                () -> "",
                () -> {
                    lexical.incrementAndGet();
                    return List.of("sessionVariable");
                },
                site -> {
                    selectors.incrementAndGet();
                    return List.of(own("speak"));
                });
        var results = new ArrayList<Candidate>();

        completer.complete(null, parsed(":he", 3), results);
        assertEquals(List.of(":help"), values(results));
        assertEquals(0, lexical.get());
        assertEquals(0, selectors.get());

        results.clear();
        completer.complete(null, parsed("sess", 4), results);
        assertEquals(List.of("sessionVariable"), values(results));
        assertEquals(1, lexical.get());
        assertEquals(0, selectors.get());

        results.clear();
        completer.complete(null, parsed("dog.sp", 6), results);
        assertEquals(List.of("dog.speak"), values(results));
        assertEquals(1, lexical.get());
        assertEquals(1, selectors.get());
    }
}

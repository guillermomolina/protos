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

import com.guillermomolina.protos.analysis.ProtosStaticMemberCompletionSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosReplSourceCompletionTest {
    private static Optional<ProtosReplSourceCompletion.Result> complete(
            String pending, String line, int cursor,
            String... liveNames) {
        return ProtosReplSourceCompletion.complete(
                pending, line, cursor, List.of(liveNames));
    }

    private static ProtosReplSourceCompletion.MemberName own(String name) {
        return new ProtosReplSourceCompletion.MemberName(name, true);
    }

    private static ProtosReplSourceCompletion.MemberName delegated(String name) {
        return new ProtosReplSourceCompletion.MemberName(name, false);
    }

    private static Optional<ProtosReplSourceCompletion.Result> member(
            String pending, String line, int cursor,
            ProtosReplSourceCompletion.MemberName... names) {
        return ProtosReplSourceCompletion.complete(
                pending, line, cursor,
                () -> {
                    throw new AssertionError("lexical provider used for a selector");
                },
                site -> List.of(names));
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
        // Without an approved selector provider a member site stays empty.
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

    @Test
    void parserProvenSelectorOffersProviderNamesWithProvenance() {
        AtomicInteger observed = new AtomicInteger();
        List<ProtosStaticMemberCompletionSite> sites = new ArrayList<>();
        var result = ProtosReplSourceCompletion.complete(
                "", "object.mem", 10,
                () -> List.of("memberLexical"),
                site -> {
                    observed.incrementAndGet();
                    sites.add(site);
                    return List.of(own("member"), delegated("memo"),
                            delegated("other"));
                }).orElseThrow();

        assertEquals(1, observed.get());
        assertEquals("object", sites.get(0).receiverName());
        assertEquals(7, result.replaceStart());
        assertEquals(10, result.replaceEnd());
        assertEquals(List.of("member", "memo"), labels(result));
        assertEquals(
                ProtosReplSourceCompletion.Kind.MEMBER_OWN,
                result.candidates().get(0).kind());
        assertEquals(
                ProtosReplSourceCompletion.Kind.MEMBER_DELEGATED,
                result.candidates().get(1).kind());
    }

    @Test
    void ownAndDelegatedOrderAndShadowingFollowTheProvider() {
        var result = member("", "dog.", 4,
                own("name"), own("speak"),
                delegated("age"), delegated("speak"))
                .orElseThrow();

        assertEquals(List.of("name", "speak", "age"), labels(result));
        assertEquals(
                ProtosReplSourceCompletion.Kind.MEMBER_OWN,
                result.candidates().get(1).kind());
    }

    @Test
    void selectorFilteringUsesTextBeforeAndAfterCursor() {
        var middle = member("", "dog.mthod", 5,
                own("method"), own("mthod"), own("mod"))
                .orElseThrow();
        assertEquals(4, middle.replaceStart());
        assertEquals(9, middle.replaceEnd());
        assertEquals(List.of("method", "mthod"), labels(middle));

        assertTrue(member("", "dog.xy", 5, own("method")).isEmpty());
    }

    @Test
    void selectorCandidatesAreLimitedTo256AfterOrdering() {
        var names = new ProtosReplSourceCompletion.MemberName[300];
        for (int index = 0; index < names.length; index++) {
            names[index] = own(String.format("m%03d", index));
        }
        var result = member("", "dog.m", 5, names).orElseThrow();

        assertEquals(256, result.candidates().size());
        assertEquals("m000", result.candidates().get(0).label());
        assertEquals("m255", result.candidates().get(255).label());
    }

    @Test
    void reservedSelectorsAreValidMemberNamesButNotJunk() {
        var result = member("", "dog.", 4,
                own("this"), own("super"), own("null"),
                own("two words"), own("1x"))
                .orElseThrow();
        assertEquals(List.of("this", "super", "null"), labels(result));

        var reserved = member("", "dog.th", 6, own("this"), own("thing"))
                .orElseThrow();
        assertEquals(List.of("this", "thing"), labels(reserved));
    }

    @Test
    void multilinePendingSelectorUsesLineRelativeOffsets() {
        var result = member(
                "f: (dog) => {",
                "  dog.sp\n}",
                8,
                own("speak")).orElseThrow();

        assertEquals(6, result.replaceStart());
        assertEquals(8, result.replaceEnd());
        assertEquals(List.of("speak"), labels(result));
    }

    @Test
    void rejectedPositionsInvokeNoProvider() {
        AtomicInteger lexical = new AtomicInteger();
        AtomicInteger selectors = new AtomicInteger();
        for (String line : List.of(
                "makeDog().me", "dog.child.me", "super.me()",
                "context.me", "\"dog.me\"", "// dog.me",
                "dog.me = 1", "array[0].me")) {
            var result = ProtosReplSourceCompletion.complete(
                    "", line, line.endsWith(")") || line.endsWith("1")
                            ? line.indexOf("me") + 2
                            : line.length(),
                    () -> {
                        lexical.incrementAndGet();
                        return List.of("me", "meow");
                    },
                    site -> {
                        selectors.incrementAndGet();
                        return List.of(own("meow"));
                    });
            assertTrue(result.isEmpty(), line);
        }
        assertEquals(0, lexical.get());
        assertEquals(0, selectors.get());
    }

    @Test
    void lexicalPathIsUnchangedAndNeverAsksForSelectors() {
        AtomicInteger selectors = new AtomicInteger();
        var withProvider = ProtosReplSourceCompletion.complete(
                "", "f: (first, second) => fir", 25,
                () -> List.of("fire", "first"),
                site -> {
                    selectors.incrementAndGet();
                    return List.of();
                }).orElseThrow();
        var legacy = complete("", "f: (first, second) => fir", 25,
                "fire", "first").orElseThrow();

        assertEquals(legacy, withProvider);
        assertEquals(0, selectors.get());
    }
}

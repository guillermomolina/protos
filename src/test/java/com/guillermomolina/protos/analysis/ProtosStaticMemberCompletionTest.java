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
package com.guillermomolina.protos.analysis;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.source.SourceSpan;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ProtosStaticMemberCompletionTest {
    private static Optional<ProtosStaticMemberCompletionSite> site(String source, int offset) {
        return new ProtosStaticAnalysisCore().memberCompletionSite(
                new ProtosDocumentSnapshot("memory:/member-completion.protos", 0, source),
                offset);
    }

    private static Optional<ProtosStaticMemberCompletionSite> siteAtEnd(String source) {
        return site(source, source.length());
    }

    private static void assertLexical(
            ProtosStaticMemberCompletionSite site, String name, int start, int end) {
        assertEquals(ProtosStaticMemberCompletionSite.ReceiverKind.LEXICAL_NAME, site.receiverKind());
        assertEquals(name, site.receiverName());
        assertEquals(new SourceSpan(start, end), site.replacementSpan());
    }

    @Test
    void lexicalNameAndThisReceiversAreProven() {
        assertLexical(siteAtEnd("dog.me").orElseThrow(), "dog", 4, 6);
        assertLexical(siteAtEnd("(dog).me").orElseThrow(), "dog", 6, 8);
        assertLexical(siteAtEnd("((dog)).me").orElseThrow(), "dog", 8, 10);

        var self = siteAtEnd("this.me").orElseThrow();
        assertEquals(ProtosStaticMemberCompletionSite.ReceiverKind.THIS, self.receiverKind());
        assertNull(self.receiverName());
        assertEquals(new SourceSpan(5, 7), self.replacementSpan());

        assertEquals(
                ProtosStaticMemberCompletionSite.ReceiverKind.THIS,
                siteAtEnd("((this)).me").orElseThrow().receiverKind());
    }

    @Test
    void incompleteSelectorAfterDotHasEmptyReplacement() {
        assertLexical(siteAtEnd("dog.").orElseThrow(), "dog", 4, 4);
        assertLexical(site("dog. + 1", 4).orElseThrow(), "dog", 4, 4);
    }

    @Test
    void cursorInsideSelectorKeepsTheRealTokenBoundaries() {
        assertLexical(site("dog.meth", 6).orElseThrow(), "dog", 4, 8);
        assertLexical(site("dog.meth", 4).orElseThrow(), "dog", 4, 8);
        assertLexical(site("dog.meth", 8).orElseThrow(), "dog", 4, 8);
    }

    @Test
    void replacementIsTheSelectorNotTheWholeMemberSpan() {
        String source = "print(dog.met)";
        var proven = site(source, "print(dog.met".length()).orElseThrow();
        assertLexical(proven, "dog", "print(dog.".length(), "print(dog.met".length());
        assertNotEquals("print(".length(), proven.replacementSpan().startOffset());
    }

    @Test
    void followingTextAndSendsAreAccepted() {
        assertLexical(site("dog.met + 1", 7).orElseThrow(), "dog", 4, 7);
        assertLexical(site("dog.method()", 10).orElseThrow(), "dog", 4, 10);
        assertLexical(site("dog.()", 4).orElseThrow(), "dog", 4, 4);
    }

    @Test
    void reservedWordSelectorsAreStructuralMemberNames() {
        for (String word : new String[] {"this", "context", "super", "true", "false", "null"}) {
            String source = "dog." + word;
            assertLexical(siteAtEnd(source).orElseThrow(), "dog", 4, source.length());
        }
    }

    @Test
    void computedAndSpecialReceiversAreRejected() {
        assertTrue(siteAtEnd("super.method()").isEmpty());
        assertTrue(site("super.method()", "super.method".length()).isEmpty());
        assertTrue(siteAtEnd("context.method").isEmpty());
        assertTrue(siteAtEnd("dog.child.me").isEmpty());
        assertTrue(siteAtEnd("makeDog().me").isEmpty());
        assertTrue(siteAtEnd("import(\"x\").me").isEmpty());
        assertTrue(siteAtEnd("array[0].me").isEmpty());
        assertTrue(siteAtEnd("\"text\".me").isEmpty());
        assertTrue(siteAtEnd("1.me").isEmpty());
        assertTrue(siteAtEnd("true.me").isEmpty());
        assertTrue(siteAtEnd("null.me").isEmpty());
        assertTrue(siteAtEnd("Object { }.me").isEmpty());
        assertTrue(siteAtEnd("[1, 2].me").isEmpty());
        assertTrue(siteAtEnd("(a + b).me").isEmpty());

        // The inner selector of a chain is still a proven lexical site.
        assertLexical(site("dog.child.me", 9).orElseThrow(), "dog", 4, 9);
    }

    @Test
    void assignmentAndSlotCreationTargetsAreRejected() {
        assertTrue(site("dog.me = 1", 6).isEmpty());
        assertTrue(site("dog.me: 1", 6).isEmpty());
        assertTrue(site("x = dog.me", 10).isPresent());
        assertTrue(site("x: dog.me", 9).isPresent());
    }

    @Test
    void stringsCommentsAndBrokenSourceAreRejected() {
        assertTrue(site("\"dog.me\"", 7).isEmpty());
        assertTrue(site("// dog.me", 9).isEmpty());
        assertTrue(site("/* dog.me */", 9).isEmpty());
        assertTrue(site("dog.me)", 6).isEmpty());
        assertTrue(site("'unterminated dog.me", 20).isEmpty());
        assertTrue(site("dog.me", 2).isEmpty());
        assertTrue(site("dog.me ", 7).isEmpty());
        assertTrue(site("dog", 3).isEmpty());
        assertTrue(site("dog.me", -1).isEmpty());
        assertTrue(site("dog.me", 7).isEmpty());
    }

    @Test
    void parserDecidesNewlinesBetweenTokens() {
        String continued = "dog.\nme";
        assertLexical(siteAtEnd(continued).orElseThrow(), "dog", 5, 7);

        String leadingDot = "dog\n.me";
        assertLexical(siteAtEnd(leadingDot).orElseThrow(), "dog", 5, 7);
    }

    @Test
    void unicodeNamesAndSurrogatePairsAreConservative() {
        String nfc = "café.mé";
        assertLexical(siteAtEnd(nfc).orElseThrow(), "café", 5, 7);

        String supplementary = "dog.𝒜";
        var proven = siteAtEnd(supplementary);
        proven.ifPresent(found -> assertEquals(
                new SourceSpan(4, supplementary.length()), found.replacementSpan()));
        assertTrue(site(supplementary, 5).isEmpty());
    }

    private static String chain(String head, int links) {
        StringBuilder source = new StringBuilder(head);
        for (int index = 0; index < links; index++) {
            source.append(".a").append(index);
        }
        return source.toString();
    }

    @Test
    void longTrailingChainKeepsTheFirstSelectorWithinBudget() {
        String source = chain("dog.me", 1000);
        assertLexical(site(source, 6).orElseThrow(), "dog", 4, 6);
    }

    @Test
    void deepChainFinalSelectorHasNoAdmittedReceiver() {
        String source = chain("dog.me", 1000);
        assertTrue(siteAtEnd(source).isEmpty());
    }

    @Test
    void exhaustedNodeBudgetFailsClosedWithoutStackOverflow() {
        String source = chain("dog.me", ProtosStaticMemberCompletion.MAX_EXAMINED_NODES + 10);
        assertTrue(site(source, 6).isEmpty());
    }

    @Test
    void onlyTheDirectMutationTargetIsRejected() {
        assertTrue(site("dog.name = 1", 8).isEmpty());
        assertTrue(site("dog.name: 1", 8).isEmpty());

        // Inside the assigned value or within the target's receiver chain the
        // selector is not itself the destination.
        assertLexical(site("x = dog.name", 12).orElseThrow(), "dog", 8, 12);
        assertLexical(site("x: dog.name", 11).orElseThrow(), "dog", 7, 11);
        assertLexical(site("dog.owner.name = 1", 9).orElseThrow(), "dog", 4, 9);
        assertLexical(site("dog.owner.name: 1", 9).orElseThrow(), "dog", 4, 9);
    }

    @Test
    void syntheticNameAvoidsCollisionsAndFailsClosedWhenExhausted() {
        String colliding = "protosMemberCompletionSite: 1\nprotosMemberCompletionSite0: 2\ndog.";
        assertLexical(siteAtEnd(colliding).orElseThrow(), "dog",
                colliding.length(), colliding.length());

        StringBuilder exhausted = new StringBuilder("protosMemberCompletionSite: 0\n");
        for (int suffix = 0;
                suffix < ProtosStaticMemberCompletion.MAX_SYNTHETIC_ATTEMPTS - 1;
                suffix++) {
            exhausted.append("protosMemberCompletionSite").append(suffix).append(": 0\n");
        }
        exhausted.append("dog.");
        assertTrue(siteAtEnd(exhausted.toString()).isEmpty());
    }
}

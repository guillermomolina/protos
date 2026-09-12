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

package com.guillermomolina.protos.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.source.SourceSpan;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosWorkspaceSymbolSearchTest {

    @Test
    void emptyQueryReturnsNoResultsWithoutEnumeratingEverything() {
        assertTrue(ProtosWorkspaceSymbolSearch.search("", List.of(candidate("value", 0))).isEmpty());
    }

    @Test
    void usesNfcDefaultCaseFoldAndOrderedSubsequenceEligibility() {
        List<ProtosWorkspaceSymbolSearch.Candidate> candidates = List.of(
                candidate("Straße", 0),
                candidate("CAFÉ", 10),
                candidate("longMethodName", 20),
                candidate("unrelated", 30));

        assertEquals(
                List.of("Straße"),
                names(ProtosWorkspaceSymbolSearch.search("STRASSE", candidates)));
        assertEquals(
                List.of("CAFÉ"),
                names(ProtosWorkspaceSymbolSearch.search("cafe\u0301", candidates)));
        assertEquals(
                List.of("longMethodName"),
                names(ProtosWorkspaceSymbolSearch.search("lmn", candidates)));
    }

    @Test
    void ranksExactPrefixSubstringThenSubsequenceAndPrefersOriginalCaseRelation() {
        List<ProtosWorkspaceSymbolSearch.Candidate> candidates = List.of(
                candidate("counter", 40),
                candidate("xctrx", 30),
                candidate("ctrValue", 20),
                candidate("ctr", 10));

        assertEquals(
                List.of("ctr", "ctrValue", "xctrx", "counter"),
                names(ProtosWorkspaceSymbolSearch.search("ctr", candidates)));

        List<ProtosWorkspaceSymbolSearch.Candidate> sameTier = List.of(
                candidate("FooA", 2),
                candidate("fooZ", 1));
        assertEquals(
                List.of("fooZ", "FooA"),
                names(ProtosWorkspaceSymbolSearch.search("foo", sameTier)));
    }

    @Test
    void appliesOneDeterministicGlobalTopHundredCap() {
        ArrayList<ProtosWorkspaceSymbolSearch.Candidate> candidates = new ArrayList<>();
        for (int index = 129; index >= 0; index--) {
            candidates.add(candidate(String.format("a%03d", index), index));
        }

        List<ProtosWorkspaceSymbolSearch.Candidate> result =
                ProtosWorkspaceSymbolSearch.search("a", candidates);
        assertEquals(ProtosWorkspaceSymbolSearch.RESULT_LIMIT, result.size());
        assertEquals("a000", result.get(0).name());
        assertEquals("a099", result.get(99).name());
    }

    private static List<String> names(List<ProtosWorkspaceSymbolSearch.Candidate> candidates) {
        return candidates.stream().map(ProtosWorkspaceSymbolSearch.Candidate::name).toList();
    }

    private static ProtosWorkspaceSymbolSearch.Candidate candidate(String name, int offset) {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "protos-g3-search")
                .toAbsolutePath()
                .normalize();
        Path source = root.resolve("Main.protos").normalize();
        String characters = " ".repeat(offset) + name;
        return new ProtosWorkspaceSymbolSearch.Candidate(
                name,
                root,
                "root-package",
                "Main",
                source,
                source.toUri().toString(),
                characters,
                new SourceSpan(offset, offset + name.length()));
    }
}

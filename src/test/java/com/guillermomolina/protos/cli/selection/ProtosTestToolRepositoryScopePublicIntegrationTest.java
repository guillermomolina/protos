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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Repository scope integration, independently selectable by behavior. */
final class ProtosTestToolRepositoryScopePublicIntegrationTest
        extends ProtosTestToolCaseSelectionPublicIntegrationTestSupport {
    private static R completeLibraryListing;

    private static synchronized R libraryRepositoryListing() throws Exception {
        if (completeLibraryListing == null) {
            R result = runInScope("libraries", "test", "--list-cases");
            assertEquals(0, result.code(), result::err);
            assertEquals("", result.err());
            completeLibraryListing = result;
        }
        return completeLibraryListing;
    }

    @Test
    void repositoryScopesPartitionTheUnfilteredPlanWithoutChangingOrder() throws Exception {
        R fullResult = unfilteredRepositoryListing();
        R coreResult = runInScope("core", "test", "--list-cases");
        R libraryResult = libraryRepositoryListing();
        assertEquals(0, coreResult.code(), coreResult::err);
        List<ListedCase> full = completeListing(fullResult.out());
        List<ListedCase> core = completeListing(coreResult.out());
        List<ListedCase> libraries = completeListing(libraryResult.out());
        Set<ListedCase> combined = new HashSet<>(core);
        for (ListedCase entry : libraries) {
            assertTrue(combined.add(entry), "core/library overlap");
        }
        assertEquals(new HashSet<>(full), combined);
        assertEquals(core, full.stream().filter(new HashSet<>(core)::contains).toList());
        assertEquals(libraries, full.stream().filter(new HashSet<>(libraries)::contains).toList());
        // This existing harness also verifies every owner's complete source
        // partition using the real Protos ownership implementation.
        assertTrue(!registeredOwners().isEmpty());
    }

    @Test
    void markdownScopeFollowsRegisteredMembership() throws Exception {
        if (!registeredOwners().contains("markdown")) {
            assertFailsBeforeScheduling(runInScope("library:markdown", "test", "--list-cases"));
        } else {
            R markdown = runInScope("library:markdown", "test", "--list-cases");
            assertEquals(0, markdown.code(), markdown::err);
            List<ListedCase> selected = completeListing(markdown.out());
            List<ListedCase> libraries = completeListing(libraryRepositoryListing().out());
            assertEquals(selected, libraries.stream().filter(new HashSet<>(selected)::contains).toList());
        }
    }

    @Test
    void tomlScopeRetainsTheCompleteOrdinaryAndOfficialCorpus() throws Exception {
        assertCompleteLibrary("toml",
                Path.of("protos", "tests", "conformance", "library", "toml"));
    }

    @Test
    void hierarchicalLibraryScopeRetainsItsCompleteCorpus() throws Exception {
        assertCompleteLibrary("math/integer",
                Path.of("protos", "tests", "library", "math", "integer"));
    }

    private static void assertCompleteLibrary(String owner, Path directory) throws Exception {
        R selected = runInScope("library:" + owner, "test", "--list-cases");
        R authoritative = runInScope(null, "test", "--list-cases", "--directory", directory.toString());
        assertEquals(0, selected.code(), selected::err);
        assertEquals(0, authoritative.code(), authoritative::err);
        assertEquals(authoritative.out(), selected.out(), "complete corpus: " + owner);
        List<ListedCase> entries = completeListing(selected.out());
        List<ListedCase> libraries = completeListing(libraryRepositoryListing().out());
        assertEquals(entries, libraries.stream().filter(new HashSet<>(entries)::contains).toList());
    }

    @Test
    void repositoryScopeIntersectsFileSelection() throws Exception {
        R selected = runInScope("library:uri", "test", "--list-cases", "--file", PARSE_FILE);
        R unfiltered = runInScope(null, "test", "--list-cases", "--file", PARSE_FILE);
        assertEquals(0, selected.code(), selected::err);
        assertEquals(0, unfiltered.code(), unfiltered::err);
        assertEquals(unfiltered.out(), selected.out());
        assertFailsBeforeScheduling(runInScope("core", "test", "--list-cases", "--file", PARSE_FILE));
    }

    @Test
    void repositoryScopeIntersectsExactCaseSelection() throws Exception {
        R exact = runInScope("library:uri", "test", "--list-cases", "--case", parseRef(0));
        assertEquals(0, exact.code(), exact::err);
        assertEquals(document(PARSE_SELECTORS[0]), exact.out());
    }

    @Test
    void repositoryScopeIntersectsDirectorySelection() throws Exception {
        String directory = Path.of(PARSE_FILE).getParent().toString();
        R selected = runInScope("library:uri", "test", "--list-cases", "--directory", directory);
        R original = runInScope(null, "test", "--list-cases", "--directory", directory);
        assertEquals(0, selected.code(), selected::err);
        assertEquals(0, original.code(), original::err);
        assertEquals(original.out(), selected.out());
    }

    @Test
    void repositoryScopePreservesExecutionProgressAndExitStatus() throws Exception {
        R execution = runInScope("library:uri", "test", "--file", PARSE_FILE, "--jobs", "1");
        R original = runInScope(null, "test", "--file", PARSE_FILE, "--jobs", "1");
        assertEquals(original.code(), execution.code());
        assertEquals(original.out(), execution.out());
        assertEquals(original.err(), execution.err());
    }

    @Test
    void invalidRepositoryScopesFailBeforeScheduling() throws Exception {
        for (String scope : List.of("", "library:", "library:integer", "library:unknown", "core ")) {
            assertFailsBeforeScheduling(runInScope(scope, "test", "--list-cases"));
        }
    }

    private static List<String> registeredOwners() throws Exception {
        return com.guillermomolina.protos.execution
                .ProtosTestToolRepositoryCompletenessSupport.registeredLibraryOwners();
    }
}

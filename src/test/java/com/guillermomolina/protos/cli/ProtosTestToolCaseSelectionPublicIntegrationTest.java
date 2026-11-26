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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** TOOL009-G2 / D185 public `protos test --list-cases` surface. */
final class ProtosTestToolCaseSelectionPublicIntegrationTest
        extends ProtosTestToolCaseSelectionPublicIntegrationTestSupport {
    private static final String LOGGING_CORPUS = "protos/corpus/library/logging";
    private static final String COLORED_FORMATTER_SOURCE = "logging/colored-formatter.protos";
    private static final String COLORED_FORMATTER_FILE =
            Path.of("protos", "tests", "library", "logging", "colored-formatter.protos").toString();

    // The source's `tests` Array order, which is the authoritative Case plan order.
    private static final String[] COLORED_FORMATTER_SELECTORS = {
        "construction answers a fresh frozen formatter exposing only format",
        "the module publishes only its constructor",
        "every canonical mode with a Boolean constructs",
        "an invalid mode signals Error",
        "autoEnabled must be exactly a Boolean for every mode",
        "styling follows the ColorMode resolution matrix",
        "the fixed palette colors each level",
        "only the LEVEL token is styled",
        "disabled styling answers exactly the TextFormatter line",
        "enabled styling keeps the TextFormatter line outside generated sequences",
        "styled INFO generates no escape sequence",
        "escape characters in event text never reach the output",
        "TextFormatter failures are signalled unchanged",
        "a text sink writes one colored line per event to a borrowed writer",
        "a real TextWriter frames colored lines like plain ones",
        "a Logger contains failures through a colored text sink"
    };
    @Test
    void bug043FullListingExceedsSingleWriteAdmissionCapacity() {
        List<List<ListedCase>> shards = new ArrayList<>();
        Set<String> union = new HashSet<>();
        int minimum = Integer.MAX_VALUE;
        int maximum = 0;
        for (int run = 1; run <= 4; run++) {
            R result = run("test", "--list-cases", "--shard-count", "4",
                    "--shard-run", Integer.toString(run));
            assertEquals(0, result.code(), result::err);
            assertEquals("", result.err());
            List<ListedCase> records = completeListing(result.out());
            minimum = Math.min(minimum, records.size());
            maximum = Math.max(maximum, records.size());
            for (ListedCase record : records) {
                assertTrue(union.add(record.ref()), "duplicate CaseRef: " + record.ref());
            }
            shards.add(records);
        }
        assertTrue(maximum - minimum <= 1);
        for (int index = 1; index < shards.size(); index++) {
            assertTrue(shards.get(index - 1).size() >= shards.get(index).size());
        }
        List<ListedCase> ordered = new ArrayList<>();
        for (int row = 0; row < maximum; row++) {
            for (List<ListedCase> shard : shards) {
                if (row < shard.size()) {
                    ordered.add(shard.get(row));
                }
            }
        }
        assertEquals(union.size(), ordered.size());
        String expected = listingDocument(ordered);
        assertTrue(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1_048_576);
        R actual = run("test", "--list-cases");
        assertEquals(0, actual.code(), actual::err);
        assertEquals("", actual.err());
        assertEquals(ordered, completeListing(actual.out()));
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.out().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void listCasesProjectsAuthoritativeCasePlanWithoutProgress() {
        R result = run("test", "--list-cases", "--file", PARSE_FILE);

        assertEquals(0, result.code());
        assertEquals("", result.err());
        assertEquals(
                document(PARSE_SELECTORS[0], PARSE_SELECTORS[1], PARSE_SELECTORS[2], PARSE_SELECTORS[3]),
                result.out());
    }

    @Test
    void listCasesComposesWithExactSelectionInCasePlanOrder() {
        R result =
                run(
                        "test",
                        "--list-cases",
                        "--file",
                        PARSE_FILE,
                        "--case",
                        parseRef(3),
                        "--case",
                        parseRef(0));

        assertEquals(0, result.code());
        assertEquals("", result.err());
        assertEquals(document(PARSE_SELECTORS[0], PARSE_SELECTORS[3]), result.out());
    }

    /**
     * TEST010: the colored formatter source is selectable through the official logging corpus and
     * lists every one of its Logical Cases under that corpus and source, without progress or any
     * Test body output.
     */
    @Test
    void listCasesSelectsEveryColoredFormatterCaseThroughOfficialLoggingCorpus() {
        R result = run("test", "--list-cases", "--file", COLORED_FORMATTER_FILE);

        assertEquals(16, COLORED_FORMATTER_SELECTORS.length);
        assertEquals(0, result.code(), result::err);
        assertEquals("", result.err());
        assertEquals(
                caseDocument(LOGGING_CORPUS, COLORED_FORMATTER_SOURCE, COLORED_FORMATTER_SELECTORS),
                result.out());
    }

    @Test
    void listCasesWithRefOutsideActiveFileScopeFails() {
        assertFailsBeforeScheduling(
                run(
                        "test",
                        "--list-cases",
                        "--file",
                        PARSE_FILE,
                        "--case",
                        ref(CORPUS, "uri/format.protos", "format constructed")));
    }

    @Test
    void twoShardsHaveExactRepeatableListingsIndependentOfJobs() {
        for (int shard = 1; shard <= 2; shard++) {
            String expected = document(PARSE_SELECTORS[shard - 1], PARSE_SELECTORS[shard + 1]);
            for (String jobs : List.of("1", "4")) {
                R result = run("test", "--list-cases", "--file", PARSE_FILE,
                        "--shard-count", "2", "--shard-run", String.valueOf(shard), "--jobs", jobs);
                assertEquals(0, result.code(), result::err);
                assertEquals("", result.err());
                assertEquals(expected, result.out());
            }
            R repeated = run("test", "--list-cases", "--file", PARSE_FILE,
                    "--shard-count", "2", "--shard-run", String.valueOf(shard));
            assertEquals(0, repeated.code(), repeated::err);
            assertEquals("", repeated.err());
            assertEquals(expected, repeated.out());
        }
    }

    @Test
    void unitSingleCaseAndEmptyShardsHaveExactDocuments() {
        R unsharded = run("test", "--list-cases", "--file", PARSE_FILE);
        R unit = run("test", "--list-cases", "--file", PARSE_FILE,
                "--shard-count", "1", "--shard-run", "1");
        assertEquals(0, unsharded.code(), unsharded::err);
        assertEquals("", unsharded.err());
        assertEquals(0, unit.code(), unit::err);
        assertEquals("", unit.err());
        assertEquals(unsharded.out(), unit.out());
        for (int shard = 1; shard <= 4; shard++) {
            R result = run("test", "--list-cases", "--file", PARSE_FILE,
                    "--shard-count", "4", "--shard-run", String.valueOf(shard));
            assertEquals(0, result.code(), result::err);
            assertEquals("", result.err());
            assertEquals(document(PARSE_SELECTORS[shard - 1]), result.out());
        }
        for (String count : List.of("5", "9223372036854775809")) {
            R empty = run("test", "--list-cases", "--file", PARSE_FILE,
                    "--shard-count", count, "--shard-run", count);
            assertEquals(0, empty.code(), empty::err);
            assertEquals("", empty.err());
            assertEquals(document(), empty.out());
        }
    }

    @Test
    void reverseExactArgumentsAreShardedInSelectedPlanOrder() {
        for (int shard = 1; shard <= 2; shard++) {
            R result = run("test", "--list-cases", "--file", PARSE_FILE,
                    "--case", parseRef(3), "--case", parseRef(0),
                    "--shard-count", "2", "--shard-run", String.valueOf(shard));
            assertEquals(0, result.code(), result::err);
            assertEquals("", result.err());
            assertEquals(document(PARSE_SELECTORS[shard == 1 ? 0 : 3]), result.out());
        }
    }

    @Test
    void publicPartitionsCoverMultipleCorporaAndDirectorySelectionWithoutOverlap() {
        // Deliberately reverse source arguments: the unsharded authoritative plan owns order.
        assertPublicPartition(List.of("--file", COLORED_FORMATTER_FILE, "--file", PARSE_FILE), 3);
        assertPublicPartition(List.of("--directory",
                Path.of("protos", "tests", "library", "uri").toString()), 3);
    }

    private static void assertPublicPartition(List<String> scope, int count) {
        List<String> arguments = new ArrayList<>(List.of("test", "--list-cases"));
        arguments.addAll(scope);
        List<String> complete = listedRefs(run(arguments.toArray(String[]::new)));
        assertTrue(complete.size() > PARSE_SELECTORS.length);
        assertEquals(complete.size(), new HashSet<>(complete).size());
        Set<String> union = new HashSet<>();
        int minimum = complete.size();
        int maximum = 0;
        for (int shard = 1; shard <= count; shard++) {
            List<String> sharded = new ArrayList<>(arguments);
            sharded.addAll(List.of("--shard-count", String.valueOf(count),
                    "--shard-run", String.valueOf(shard)));
            List<String> actual = listedRefs(run(sharded.toArray(String[]::new)));
            List<String> expected = new ArrayList<>();
            for (int index = shard - 1; index < complete.size(); index += count) {
                expected.add(complete.get(index));
            }
            assertEquals(expected, actual);
            for (String ref : actual) {
                assertTrue(union.add(ref), () -> "Case assigned more than once: " + ref);
            }
            minimum = Math.min(minimum, actual.size());
            maximum = Math.max(maximum, actual.size());
        }
        assertEquals(new HashSet<>(complete), union);
        assertTrue(maximum - minimum <= 1);
    }

    private static List<String> listedRefs(R result) {
        assertEquals(0, result.code(), result::err);
        assertEquals("", result.err());
        Matcher matches = Pattern.compile("\"ref\":\"(v1\\.[0-9a-f]+)\"").matcher(result.out());
        List<String> refs = new ArrayList<>();
        while (matches.find()) {
            refs.add(matches.group(1));
        }
        return refs;
    }

    /** The exact `protos.test.cases/v1` document for selectors needing no JSON escaping. */
    private static String caseDocument(String corpus, String source, String... selectors) {
        StringBuilder cases = new StringBuilder();
        for (String selector : selectors) {
            if (cases.length() > 0) {
                cases.append(',');
            }
            cases.append("{\"ref\":\"")
                    .append(ref(corpus, source, selector))
                    .append("\",\"display\":\"")
                    .append(corpus)
                    .append(':')
                    .append(source)
                    .append("::")
                    .append(selector)
                    .append("\"}");
        }
        return "{\"schema\":\"protos.test.cases/v1\",\"cases\":[" + cases + "]}\n";
    }
}

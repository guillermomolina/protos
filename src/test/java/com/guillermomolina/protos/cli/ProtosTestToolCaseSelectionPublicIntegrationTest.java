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

import java.nio.file.Path;
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

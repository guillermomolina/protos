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
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** TOOL009-G2 / D185 public `protos test --list-cases` and `--case CASE_REF` surface. */
final class ProtosTestToolCaseSelectionPublicIntegrationTest {
    private static final String CORPUS = "protos/corpus/library/uri";
    private static final String PARSE_SOURCE = "uri/parse.protos";

    private static final String PARSE_FILE =
            Path.of("protos", "tests", "library", "uri", "parse.protos").toString();

    private static final String[] PARSE_SELECTORS = {
        "parse components", "parse invalid", "parse valid", "arity domain rejection"
    };

    private static final String EXPECTED_BOOTSTRAP_STDOUT =
            "Protos test tool bootstrap\n" + "test\n";

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

    @Test
    void exactCaseExecutesOnlyThatCase() {
        R result = run("test", "--file", PARSE_FILE, "--case", parseRef(2));

        assertEquals(0, result.code());
        assertEquals(EXPECTED_BOOTSTRAP_STDOUT, result.out());
        assertEquals(
                "[library/uri] 0/1\n" + "[library/uri] 1/1 passed\n" + "1 passed, 0 failed\n",
                result.err());
    }

    @Test
    void exactCasesInReverseArgumentOrderExecuteOnlyThoseCases() {
        R result =
                run("test", "--file", PARSE_FILE, "--case", parseRef(3), "--case", parseRef(1));

        assertEquals(0, result.code());
        assertEquals(EXPECTED_BOOTSTRAP_STDOUT, result.out());
        assertEquals(
                "[library/uri] 0/2\n"
                        + "[library/uri] 1/2\n"
                        + "[library/uri] 2/2 passed\n"
                        + "2 passed, 0 failed\n",
                result.err());
    }

    @Test
    void repeatedExactRefFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run("test", "--file", PARSE_FILE, "--case", parseRef(1), "--case", parseRef(1)));
    }

    @Test
    void wellFormedUnknownRefFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run("test", "--file", PARSE_FILE, "--case", ref(CORPUS, PARSE_SOURCE, "missing")));
    }

    @Test
    void malformedRefFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run("test", "--file", PARSE_FILE, "--case", "uri/parse.protos::parse valid"));
    }

    @Test
    void unsupportedRefVersionFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run(
                        "test",
                        "--file",
                        PARSE_FILE,
                        "--case",
                        "v2." + parseRef(2).substring("v1.".length())));
    }

    @Test
    void refOutsideActiveFileScopeFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run(
                        "test",
                        "--file",
                        PARSE_FILE,
                        "--case",
                        ref(CORPUS, "uri/format.protos", "format constructed")));
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

    private static void assertFailsBeforeScheduling(R result) {
        assertEquals(1, result.code());
        assertEquals("", result.out());
        assertFalse(result.err().contains("[library/uri]"), result::err);
    }

    private static String parseRef(int index) {
        return ref(CORPUS, PARSE_SOURCE, PARSE_SELECTORS[index]);
    }

    private static String document(String... selectors) {
        StringBuilder cases = new StringBuilder();
        for (String selector : selectors) {
            if (cases.length() > 0) {
                cases.append(',');
            }
            cases.append("{\"ref\":\"")
                    .append(ref(CORPUS, PARSE_SOURCE, selector))
                    .append("\",\"display\":\"")
                    .append(CORPUS)
                    .append(':')
                    .append(PARSE_SOURCE)
                    .append("::")
                    .append(selector)
                    .append("\"}");
        }
        return "{\"schema\":\"protos.test.cases/v1\",\"cases\":[" + cases + "]}\n";
    }

    /** Test-side D185 V1 reference for plain associations whose strings need no JSON escaping. */
    private static String ref(String corpus, String path, String selector) {
        String payload = "[[\"" + corpus + "\",\"" + path + "\"],\"" + selector + "\"]";
        return "v1." + HexFormat.of().formatHex(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static R run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int code =
                new ProtosCli()
                        .run(
                                args,
                                InputStream.nullInputStream(),
                                new PrintStream(out),
                                new PrintStream(err));

        return new R(
                code,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record R(int code, String out, String err) {}
}

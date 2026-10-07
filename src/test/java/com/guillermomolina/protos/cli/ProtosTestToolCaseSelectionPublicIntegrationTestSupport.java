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

/** Shared TOOL009-G2 / D185 public `protos test --list-cases` and `--case CASE_REF` support. */
abstract class ProtosTestToolCaseSelectionPublicIntegrationTestSupport {
    static final String CORPUS = "protos/corpus/library/uri";
    static final String PARSE_SOURCE = "uri/parse.protos";

    static final String PARSE_FILE =
            Path.of("protos", "tests", "library", "uri", "parse.protos").toString();

    static final String[] PARSE_SELECTORS = {
        "parse components", "parse invalid", "parse valid", "arity domain rejection"
    };

    static final String EXPECTED_BOOTSTRAP_STDOUT =
            "Protos test tool bootstrap\n" + "test\n";

    static void assertFailsBeforeScheduling(R result) {
        assertEquals(1, result.code());
        assertEquals("", result.out());
        assertFalse(result.err().contains("[library/uri]"), result::err);
    }

    static String parseRef(int index) {
        return ref(CORPUS, PARSE_SOURCE, PARSE_SELECTORS[index]);
    }

    static String document(String... selectors) {
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
    static String ref(String corpus, String path, String selector) {
        String payload = "[[\"" + corpus + "\",\"" + path + "\"],\"" + selector + "\"]";
        return "v1." + HexFormat.of().formatHex(payload.getBytes(StandardCharsets.UTF_8));
    }

    static R run(String... args) {
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

    record R(int code, String out, String err) {}
}

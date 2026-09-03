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

package com.guillermomolina.protos.lexer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Checks the Core v0.1 NFC predicate against the Unicode 17.0.0 normalization
 * conformance corpus.
 */
class UnicodeNfc17ConformanceTest {
    private static final String RESOURCE = "/unicode/17.0.0/NormalizationTest.txt";

    @Test
    void matchesUnicode17NormalizationTestForNfc() throws IOException {
        try (InputStream raw = UnicodeNfc17ConformanceTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(raw, "Missing Unicode 17.0.0 normalization conformance resource");
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8))) {
                String firstLine = reader.readLine();
                if (!"# NormalizationTest-17.0.0.txt".equals(firstLine)) {
                    throw new AssertionError(
                        "Unexpected Unicode normalization test version: " + firstLine
                    );
                }

                String line;
                int lineNumber = 1;
                int cases = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    String data = stripComment(line).trim();
                    if (data.isEmpty() || data.startsWith("@")) {
                        continue;
                    }

                    String[] columns = data.split(";", -1);
                    if (columns.length < 5) {
                        throw new AssertionError(
                            "Malformed NormalizationTest.txt line " + lineNumber + ": " + line
                        );
                    }

                    String c1 = decode(columns[0]);
                    String c2 = decode(columns[1]);
                    String c3 = decode(columns[2]);
                    String c4 = decode(columns[3]);
                    String c5 = decode(columns[4]);

                    check(lineNumber, 1, c1, c1.equals(c2));
                    check(lineNumber, 2, c2, true);
                    check(lineNumber, 3, c3, c3.equals(c2));
                    check(lineNumber, 4, c4, true);
                    check(lineNumber, 5, c5, c5.equals(c4));
                    cases++;
                }

                if (cases == 0) {
                    throw new AssertionError("Unicode normalization conformance resource is empty");
                }
            }
        }
    }

    private static void check(int lineNumber, int column, String value, boolean expected) {
        boolean actual = UnicodeXid.isNfc(value);
        if (actual != expected) {
            throw new AssertionError(
                "NFC mismatch at NormalizationTest.txt line "
                    + lineNumber
                    + ", column c"
                    + column
                    + ": expected "
                    + expected
                    + ", got "
                    + actual
                    + ", code points "
                    + codePoints(value)
            );
        }
    }

    private static String decode(String field) {
        String trimmed = field.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String hex : trimmed.split("\\s+")) {
            result.appendCodePoint(Integer.parseInt(hex, 16));
        }
        return result.toString();
    }

    private static String codePoints(String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(String.format("U+%04X", codePoint));
        });
        return result.toString();
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        return comment < 0 ? line : line.substring(0, comment);
    }
}

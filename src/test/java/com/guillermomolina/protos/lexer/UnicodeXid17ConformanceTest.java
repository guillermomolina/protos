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
import java.util.BitSet;
import org.junit.jupiter.api.Test;

/**
 * Checks Core v0.1 XID_Start and XID_Continue membership against the Unicode
 * 17.0.0 derived core properties.
 */
class UnicodeXid17ConformanceTest {
    private static final String RESOURCE = "/unicode/17.0.0/DerivedCoreProperties.txt";
    private static final int UNICODE_LIMIT = 0x110000;

    @Test
    void matchesUnicode17XidPropertiesForEveryCodePoint() throws IOException {
        BitSet xidStart = new BitSet(UNICODE_LIMIT);
        BitSet xidContinue = new BitSet(UNICODE_LIMIT);

        try (InputStream raw = UnicodeXid17ConformanceTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(raw, "Missing Unicode 17.0.0 derived-core-properties resource");
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8))) {
                String firstLine = reader.readLine();
                if (!"# DerivedCoreProperties-17.0.0.txt".equals(firstLine)) {
                    throw new AssertionError(
                        "Unexpected Unicode derived-core-properties version: " + firstLine
                    );
                }

                String line;
                int lineNumber = 1;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    String data = stripComment(line).trim();
                    if (data.isEmpty()) {
                        continue;
                    }

                    String[] fields = data.split(";", -1);
                    if (fields.length < 2) {
                        throw new AssertionError(
                            "Malformed DerivedCoreProperties.txt line "
                                + lineNumber
                                + ": "
                                + line
                        );
                    }

                    String property = fields[1].trim();
                    BitSet target;
                    if ("XID_Start".equals(property)) {
                        target = xidStart;
                    } else if ("XID_Continue".equals(property)) {
                        target = xidContinue;
                    } else {
                        continue;
                    }

                    addRange(target, fields[0].trim(), lineNumber);
                }
            }
        }

        if (xidStart.isEmpty() || xidContinue.isEmpty()) {
            throw new AssertionError("Unicode XID conformance data was not loaded");
        }

        for (int codePoint = 0; codePoint < UNICODE_LIMIT; codePoint++) {
            boolean expectedStart = xidStart.get(codePoint);
            boolean actualStart = UnicodeXid.isStart(codePoint);
            if (actualStart != expectedStart) {
                throw mismatch("XID_Start", codePoint, expectedStart, actualStart);
            }

            boolean expectedContinue = xidContinue.get(codePoint);
            boolean actualContinue = UnicodeXid.isContinue(codePoint);
            if (actualContinue != expectedContinue) {
                throw mismatch("XID_Continue", codePoint, expectedContinue, actualContinue);
            }
        }
    }

    private static void addRange(BitSet target, String field, int lineNumber) {
        String[] bounds = field.split("\\.\\.", -1);
        try {
            int start = Integer.parseInt(bounds[0], 16);
            int end = bounds.length == 1 ? start : Integer.parseInt(bounds[1], 16);
            if (bounds.length > 2 || start < 0 || end < start || end >= UNICODE_LIMIT) {
                throw new NumberFormatException();
            }
            target.set(start, end + 1);
        } catch (NumberFormatException exception) {
            throw new AssertionError(
                "Malformed code-point range at DerivedCoreProperties.txt line "
                    + lineNumber
                    + ": "
                    + field,
                exception
            );
        }
    }

    private static AssertionError mismatch(
        String property,
        int codePoint,
        boolean expected,
        boolean actual
    ) {
        return new AssertionError(
            property
                + " mismatch at "
                + String.format("U+%04X", codePoint)
                + ": expected "
                + expected
                + ", got "
                + actual
        );
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        return comment < 0 ? line : line.substring(0, comment);
    }
}

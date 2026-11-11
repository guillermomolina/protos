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
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/** I115: direct GetLen / BinarySubscript / SetItem over standard Bytes. */
final class ProtosI115BytesDirectOperationTest {
    private static final String SOURCE = "source: Encoding.UTF8.encode(\"jobs\")\n";

    @Test
    void bytesSizeIsDirectLength() {
        assertEquals(4L, evalLong(SOURCE + "source.size()"));
        assertTrue(evalBoolean(SOURCE + "source.size() == 4"));
    }

    @Test
    void bytesIndexedReadReturnsOctet() {
        assertEquals(106L, evalLong(SOURCE + "source[0]"));
        assertEquals(115L, evalLong(SOURCE + "source.at(3)"));
    }

    @Test
    void contextualIfObservesBytesSize() {
        assertEquals(1L, evalLong(SOURCE
                + "hit: 0\nif(source.size() == 4) { hit = 1 }\nhit"));
        assertEquals(0L, evalLong(SOURCE
                + "hit: 0\nif(source.size() == 0) { hit = 1 }\nhit"));
    }

    @Test
    void arrayLengthAndIndexingUnchanged() {
        assertEquals(3L, evalLong("a: [7, 8, 9]\na.size()"));
        assertEquals(8L, evalLong("a: [7, 8, 9]\na[1]"));
        assertTrue(signals("a: [7, 8, 9]\na[0 - 1]"));
        assertTrue(signals("a: [7, 8, 9]\na[3]"));
        assertTrue(signals("a: [7, 8, 9]\na[9223372036854775807]"));
    }

    @Test
    void invalidBytesIndexesSignalGuestError() {
        assertTrue(signals(SOURCE + "source[0 - 1]"));
        assertTrue(signals(SOURCE + "source[4]"));
        assertTrue(signals(SOURCE + "source[4294967296]"));
        assertTrue(signals(SOURCE + "source[9223372036854775807]"));
    }

    @Test
    void validBytesIndexedAssignment() {
        assertEquals(107L, evalLong(SOURCE
                + "source[0] = 107\nsource[0]"));
        assertEquals(4L, evalLong(SOURCE
                + "source[3] = 255\nsource.size()"));
    }

    @Test
    void invalidOctetsAndFrozenBytesSignalGuestError() {
        assertTrue(signals(SOURCE + "source[0] = 256"));
        assertTrue(signals(SOURCE + "source[0] = 0 - 1"));
        assertTrue(signals(SOURCE + "source[4] = 1"));
        assertTrue(signals(SOURCE + "source.freeze()\nsource[0] = 1"));
    }

    @Test
    void bytesOctetsCombineWithBooleanOperators() {
        String isAlpha = "isAlpha: (octet) => {\n"
                + "    ((octet >= 65) && (octet <= 90)) ||\n"
                + "        ((octet >= 97) && (octet <= 122))\n"
                + "}\n";
        assertTrue(evalBoolean(SOURCE + isAlpha + "isAlpha(source[0]) === true"));
        assertTrue(evalBoolean(SOURCE + isAlpha + "isAlpha(source[3]) === true"));
        assertTrue(evalBoolean(SOURCE + isAlpha + "isAlpha(48) === false"));
        assertEquals(1L, evalLong(SOURCE
                + "octet: source[0]\nhit: 0\n"
                + "if((octet >= 97) && (octet <= 122)) { hit = 1 }\nhit"));
    }

    @Test
    void isAlphaAcceptsEveryOctetOfJobsAndRejectsOthers() {
        String isAlpha = "isAlpha: (octet) => {\n"
                + "    ((octet >= 65) && (octet <= 90)) ||\n"
                + "        ((octet >= 97) && (octet <= 122))\n"
                + "}\n";
        assertEquals(4L, evalLong(SOURCE + isAlpha
                + "count: 0\n"
                + "source.each((octet) => {\n"
                + "    (isAlpha(octet) === true).ifTrue(() => { count = count + 1 })\n"
                + "})\ncount"));
        for (int index = 0; index < 4; index++) {
            assertTrue(evalBoolean(SOURCE + isAlpha
                    + "isAlpha(source[" + index + "]) === true"));
        }
        for (long octet : new long[] {0, 48, 57, 64, 91, 96, 123, 255}) {
            assertTrue(evalBoolean(isAlpha + "isAlpha(" + octet + ") === false"));
        }
        for (long octet : new long[] {65, 90, 97, 122}) {
            assertTrue(evalBoolean(isAlpha + "isAlpha(" + octet + ") === true"));
        }
    }

    private static boolean signals(String body) {
        return evalBoolean(
                "Error.handle(() => {\n" + body + "\nfalse\n}, (caught) => true)");
    }

    private static long evalLong(String source) {
        try (Context context = context()) {
            return context.eval(ProtosLanguage.ID, source).asLong();
        }
    }

    private static boolean evalBoolean(String source) {
        try (Context context = context()) {
            return context.eval(ProtosLanguage.ID, source).asBoolean();
        }
    }

    private static Context context() {
        String core = Path.of("protos", "lib", "core").toAbsolutePath().toString();
        return Context.newBuilder(ProtosLanguage.ID).option("protos.CoreRoot", core).build();
    }
}

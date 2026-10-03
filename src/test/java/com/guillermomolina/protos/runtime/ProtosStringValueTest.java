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

package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosStringValueTest {
    @Test
    void validatesAndCachesExactUnicodeScalarCountInOneSemanticValue() {
        assertScalarCount("", 0);
        assertScalarCount("abc", 3);
        assertScalarCount("e\u0301", 2);
        assertScalarCount("😀", 1);
        assertScalarCount("👨‍👩‍👧‍👦", 7);
    }

    @Test
    void acceptsExactUnicodeScalarSequencesIncludingSupplementaryScalars() {
        String value = "e\u0301😀";

        ProtosStringValue string = new ProtosStringValue(value);

        assertEquals(value, string.value());
        assertEquals(3, string.scalarCountForRuntime());
    }

    @Test
    void rejectsUnpairedHighSurrogate() {
        String value = Character.toString((char) 0xD800);

        assertThrows(IllegalArgumentException.class, () -> new ProtosStringValue(value));
    }

    @Test
    void rejectsUnpairedLowSurrogate() {
        String value = Character.toString((char) 0xDC00);

        assertThrows(IllegalArgumentException.class, () -> new ProtosStringValue(value));
    }

    @Test
    void derivedConcatenationComposesUnicodeProofAndScalarCountWithoutNormalization() {
        ProtosStringValue left = new ProtosStringValue("é😀");
        ProtosStringValue right = new ProtosStringValue("e\u0301");

        ProtosStringValue pair =
                ProtosStringValue.concatenateForRuntime(left, right);
        ProtosStringValue aggregate =
                ProtosStringValue.concatenateAllForRuntime(
                        left,
                        List.of(
                                new ProtosStringValue(""),
                                new ProtosStringValue("😀"),
                                right));

        assertEquals("é😀e\u0301", pair.value());
        assertEquals(4, pair.scalarCountForRuntime());

        assertEquals("é😀😀e\u0301", aggregate.value());
        assertEquals(5, aggregate.scalarCountForRuntime());
    }

    @Test
    void scalarExtractionAndRuntimeCopyPreserveEstablishedUnicodeProof() {
        ProtosStringValue source = new ProtosStringValue("Ae\u0301😀");
        ProtosStringValue combiningMark = source.scalarAtForRuntime(2);
        ProtosStringValue supplementary = source.scalarAtForRuntime(3);
        ProtosStringValue copied = source.copyForRuntime();

        assertEquals("\u0301", combiningMark.value());
        assertEquals(1, combiningMark.scalarCountForRuntime());
        assertEquals("😀", supplementary.value());
        assertEquals(1, supplementary.scalarCountForRuntime());
        assertNull(source.scalarAtForRuntime(4));

        assertNotSame(source, copied);
        assertEquals(source.value(), copied.value());
        assertEquals(source.scalarCountForRuntime(), copied.scalarCountForRuntime());
    }

    private static void assertScalarCount(String value, int expected) {
        assertEquals(expected, new ProtosStringValue(value).scalarCountForRuntime());
    }
}

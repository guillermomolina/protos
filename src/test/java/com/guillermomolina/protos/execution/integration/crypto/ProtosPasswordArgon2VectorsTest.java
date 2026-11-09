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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * I160: independently generated libargon2/argon2-cffi 25.1.0
 * Argon2id-v19 known-answer vectors.
 *
 * <p>The vectors use 65536 KiB, three iterations, four lanes,
 * sixteen salt octets and a thirty-two-octet tag.
 * They are constants, not computed by the provider under test.
 */
final class ProtosPasswordArgon2VectorsTest {

    private static final HexFormat HEX = HexFormat.of();

    private static final byte[] SALT =
            HEX.parseHex("000102030405060708090a0b0c0d0e0f");

    private static void vector(String password, String expectedHex) {
        byte[] normalized = null;
        byte[] tag = null;

        try {
            normalized = ProtosPasswordArgon2Facility.normalizePassword(
                    new ProtosStringValue(password));

            tag = ProtosPasswordArgon2Facility.derive(
                    normalized, SALT);

            assertArrayEquals(HEX.parseHex(expectedHex), tag);
            assertTrue(ProtosPasswordArgon2Facility.verify(
                    normalized, SALT, HEX.parseHex(expectedHex)));

            byte[] different = HEX.parseHex(expectedHex);
            different[0] ^= 1;

            assertFalse(ProtosPasswordArgon2Facility.verify(
                    normalized, SALT, different));
        } finally {
            if (normalized != null) {
                Arrays.fill(normalized, (byte) 0);
            }
            if (tag != null) {
                Arrays.fill(tag, (byte) 0);
            }
        }
    }

    @Test
    @Timeout(120)
    void libargon2IndependentVectors() {
        vector(
                "password",
                "bd4ace85c295fb38d844e16771a9e439"
                        + "25282ac982c8656be55a2ef6cf20fd58");

        vector(
                "café",
                "4caea402ab0c9e0590cdef1a1cb2c5b1"
                        + "533a95bbb5a407adb52df872f57d7a49");

        vector(
                "",
                "ad4a178e6b0786a48f9bb3443df83f76"
                        + "b822c6fdac15a80277483fd84551ac52");

        vector(
                "password\0with-nul",
                "2deff4bacc0e0e5b2a2543ce2511a2e"
                        + "0ac5cc635dabbe72ac0a37ae46a807e5f");
    }

    @Test
    @Timeout(120)
    void nfcCanonicalEquivalenceUsesTheSameIndependentVector() {
        String composed = "café";
        String decomposed = "cafe\u0301";

        assertFalse(composed.equals(decomposed));
        assertTrue(Normalizer.isNormalized(
                composed, Normalizer.Form.NFC));

        byte[] first = ProtosPasswordArgon2Facility.normalizePassword(
                new ProtosStringValue(composed));
        byte[] second = ProtosPasswordArgon2Facility.normalizePassword(
                new ProtosStringValue(decomposed));

        try {
            assertArrayEquals(first, second);
            assertArrayEquals(
                    "4caea402ab0c9e0590cdef1a1cb2c5b1"
                            .concat("533a95bbb5a407adb52df872f57d7a49")
                            .getBytes(StandardCharsets.US_ASCII),
                    HEX.formatHex(
                            ProtosPasswordArgon2Facility.derive(
                                    second, SALT))
                            .getBytes(StandardCharsets.US_ASCII));
        } finally {
            Arrays.fill(first, (byte) 0);
            Arrays.fill(second, (byte) 0);
        }
    }

    @Test
    void strictInputBoundariesAndSaltDomain() {
        // Protos String itself rejects invalid UTF-16 before the
        // password normalization boundary can receive that value.
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProtosStringValue("\uD800"));

        // The password facility independently rejects non-String
        // values rather than coercing them through guest protocols.
        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosPasswordArgon2Facility.normalizePassword(
                        new byte[] {112, 97, 115, 115}));

        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosPasswordArgon2Facility.normalizePassword(
                        new ProtosStringValue("x".repeat(65537))));

        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosPasswordArgon2Facility.derive(
                        new byte[0], new byte[15]));

        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosPasswordArgon2Facility.verify(
                        new byte[0], SALT, new byte[31]));
    }
}

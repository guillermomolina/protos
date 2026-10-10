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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

final class ProtosNumericHashKeyTest {
    @Test
    void signed64BoundariesRemainExact() {
        for (long value : new long[] {
                Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE
        }) {
            assertEquals(
                    ProtosNumericHashKey.ofLong(value),
                    ProtosNumericHashKey.fromCanonicalTwosComplement(
                            BigInteger.valueOf(value).toByteArray()));
        }
    }

    @Test
    void arbitraryPrecisionHashesDoNotTruncateOrCollideByLowWord() {
        BigInteger huge = BigInteger.ONE.shiftLeft(200).add(BigInteger.valueOf(7));
        BigInteger negative = huge.negate();

        ProtosNumericHashKey positive =
                ProtosNumericHashKey.fromSemanticInteger(
                        new ProtosIntegerValue(huge));
        ProtosNumericHashKey same =
                ProtosNumericHashKey.fromCanonicalTwosComplement(
                        huge.toByteArray());
        ProtosNumericHashKey opposite =
                ProtosNumericHashKey.fromCanonicalTwosComplement(
                        negative.toByteArray());

        assertEquals(positive, same);
        assertEquals(positive.hashCode(), same.hashCode());
        assertNotEquals(positive, opposite);
        assertNotEquals(positive, ProtosNumericHashKey.ofLong(7));
    }

    @Test
    void canonicalEncodingAndDefensiveCopyPreserveIdentity() {
        byte[] encoded = BigInteger.ONE.shiftLeft(160).toByteArray();
        ProtosNumericHashKey key =
                ProtosNumericHashKey.fromCanonicalTwosComplement(encoded);
        ProtosNumericHashKey equivalent =
                ProtosNumericHashKey.fromCanonicalTwosComplement(encoded);

        encoded[0] ^= 1;
        assertEquals(key, equivalent);

        assertEquals(
                ProtosNumericHashKey.ofLong(1),
                ProtosNumericHashKey.fromCanonicalTwosComplement(
                        new byte[] {0, 0, 1}));
        assertEquals(
                ProtosNumericHashKey.ofLong(-1),
                ProtosNumericHashKey.fromCanonicalTwosComplement(
                        new byte[] {-1, -1}));
    }
}

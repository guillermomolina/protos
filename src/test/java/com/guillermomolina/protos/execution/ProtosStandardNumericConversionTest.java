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

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ProtosStandardNumericConversionTest {
    // Deliberately Java-side: this tests the representation helper used by
    // numeric conversion/equality bridges, not observable source-level behavior.
    @Test
    void exactIntegralBinary64ExtractionUsesActualBinaryValue() {
        assertEquals(
                new BigInteger("99999999999999991611392"),
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(1e23));
        assertEquals(
                BigInteger.ZERO,
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(-0.0d));
        assertEquals(
                BigInteger.ONE,
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(1.0d));
        assertEquals(
                null,
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(1.5d));
        assertEquals(
                null,
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(
                        Double.POSITIVE_INFINITY));
        assertEquals(
                null,
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(Double.NaN));
    }
}

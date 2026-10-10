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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

final class ProtosI091UnsignedWidthTest {
    @Test
    void ipv4Boundary() {
        assertTrue(new ProtosIntegerValue(0L)
                .fitsUnsignedBitsForRuntime(32));
        assertTrue(new ProtosIntegerValue(4294967295L)
                .fitsUnsignedBitsForRuntime(32));
        assertFalse(new ProtosIntegerValue(4294967296L)
                .fitsUnsignedBitsForRuntime(32));
        assertFalse(new ProtosIntegerValue(-1L)
                .fitsUnsignedBitsForRuntime(32));
    }

    @Test
    void ipv6BoundaryPreservesArbitraryPrecision() {
        BigInteger upper = BigInteger.ONE.shiftLeft(128);
        assertTrue(ProtosNumericValueSupport.isUnsignedIntegerWithin(
                ProtosTestIntegers.integer(upper.subtract(BigInteger.ONE)), 128));
        assertFalse(ProtosNumericValueSupport.isUnsignedIntegerWithin(
                ProtosTestIntegers.integer(upper), 128));
        assertFalse(ProtosNumericValueSupport.isUnsignedIntegerWithin(
                ProtosTestIntegers.integer(upper.negate()), 128));
    }

    @Test
    void signed64AndZeroWidthBoundaries() {
        assertTrue(new ProtosIntegerValue(Long.MAX_VALUE)
                .fitsUnsignedBitsForRuntime(63));
        assertFalse(new ProtosIntegerValue(Long.MIN_VALUE)
                .fitsUnsignedBitsForRuntime(128));
        assertTrue(new ProtosIntegerValue(0L)
                .fitsUnsignedBitsForRuntime(0));
        assertFalse(new ProtosIntegerValue(1L)
                .fitsUnsignedBitsForRuntime(0));
        assertThrows(IllegalArgumentException.class,
                () -> new ProtosIntegerValue(1L)
                        .fitsUnsignedBitsForRuntime(-1));
    }
}

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

import com.oracle.truffle.api.interop.UnsupportedMessageException;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Exact host-width projection helpers for integral Protos interop values. */
final class ProtosIntegralInteropSupport {
    private static final BigInteger BYTE_MIN = BigInteger.valueOf(Byte.MIN_VALUE);
    private static final BigInteger BYTE_MAX = BigInteger.valueOf(Byte.MAX_VALUE);
    private static final BigInteger SHORT_MIN = BigInteger.valueOf(Short.MIN_VALUE);
    private static final BigInteger SHORT_MAX = BigInteger.valueOf(Short.MAX_VALUE);
    private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private ProtosIntegralInteropSupport() {}

    static boolean fitsInByte(BigInteger value) {
        return between(value, BYTE_MIN, BYTE_MAX);
    }

    static boolean fitsInShort(BigInteger value) {
        return between(value, SHORT_MIN, SHORT_MAX);
    }

    static boolean fitsInInt(BigInteger value) {
        return between(value, INT_MIN, INT_MAX);
    }

    static boolean fitsInLong(BigInteger value) {
        return between(value, LONG_MIN, LONG_MAX);
    }

    static boolean fitsInFloat(BigInteger value) {
        float converted = value.floatValue();
        if (!Float.isFinite(converted)) {
            return false;
        }
        try {
            return new BigDecimal((double) converted).toBigIntegerExact().equals(value);
        } catch (ArithmeticException notIntegral) {
            return false;
        }
    }

    static boolean fitsInDouble(BigInteger value) {
        double converted = value.doubleValue();
        if (!Double.isFinite(converted)) {
            return false;
        }
        try {
            return new BigDecimal(converted).toBigIntegerExact().equals(value);
        } catch (ArithmeticException notIntegral) {
            return false;
        }
    }

    static byte asByte(BigInteger value) throws UnsupportedMessageException {
        if (!fitsInByte(value)) {
            throw UnsupportedMessageException.create();
        }
        return value.byteValue();
    }

    static short asShort(BigInteger value) throws UnsupportedMessageException {
        if (!fitsInShort(value)) {
            throw UnsupportedMessageException.create();
        }
        return value.shortValue();
    }

    static int asInt(BigInteger value) throws UnsupportedMessageException {
        if (!fitsInInt(value)) {
            throw UnsupportedMessageException.create();
        }
        return value.intValue();
    }

    static long asLong(BigInteger value) throws UnsupportedMessageException {
        if (!fitsInLong(value)) {
            throw UnsupportedMessageException.create();
        }
        return value.longValue();
    }

    static float asFloat(BigInteger value) throws UnsupportedMessageException {
        if (!fitsInFloat(value)) {
            throw UnsupportedMessageException.create();
        }
        return value.floatValue();
    }

    static double asDouble(BigInteger value) throws UnsupportedMessageException {
        if (!fitsInDouble(value)) {
            throw UnsupportedMessageException.create();
        }
        return value.doubleValue();
    }

    private static boolean between(BigInteger value, BigInteger minimum, BigInteger maximum) {
        return value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }
}

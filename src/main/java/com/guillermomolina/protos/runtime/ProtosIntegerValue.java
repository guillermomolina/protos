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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import java.math.BigInteger;
import java.util.Objects;

@ExportLibrary(InteropLibrary.class)
public final class ProtosIntegerValue implements ProtosRepresentedValue {
    private static final int SIGNED_LONG_MAGNITUDE_BITS = Long.SIZE - 1;

    private final long smallValue;
    private final BigInteger bigValue;

    public ProtosIntegerValue(long value) {
        this.smallValue = value;
        this.bigValue = null;
    }

    public ProtosIntegerValue(BigInteger value) {
        BigInteger exact = Objects.requireNonNull(value, "value");
        if (fitsSignedLong(exact)) {
            this.smallValue = exact.longValue();
            this.bigValue = null;
        } else {
            this.smallValue = 0L;
            this.bigValue = exact;
        }
    }

    public boolean isSmallForRuntime() {
        return bigValue == null;
    }

    public long smallValueForRuntime() {
        if (bigValue != null) {
            throw new IllegalStateException("Integer is not represented as a signed long");
        }
        return smallValue;
    }

    public BigInteger value() {
        return bigValue != null ? bigValue : BigInteger.valueOf(smallValue);
    }

    public int signumForRuntime() {
        return bigValue != null ? bigValue.signum() : Long.compare(smallValue, 0L);
    }

    public boolean fitsInIntForRuntime() {
        return bigValue == null
                && smallValue >= Integer.MIN_VALUE
                && smallValue <= Integer.MAX_VALUE;
    }

    public int intValueExactForRuntime() {
        if (!fitsInIntForRuntime()) {
            throw new ArithmeticException("Integer does not fit in int");
        }
        return (int) smallValue;
    }

    public boolean sameIntegerForRuntime(ProtosIntegerValue other) {
        Objects.requireNonNull(other, "other");
        if (bigValue == null) {
            return other.bigValue == null && smallValue == other.smallValue;
        }
        return other.bigValue != null && bigValue.equals(other.bigValue);
    }

    public int compareToIntegerForRuntime(ProtosIntegerValue other) {
        Objects.requireNonNull(other, "other");
        if (bigValue == null && other.bigValue == null) {
            return Long.compare(smallValue, other.smallValue);
        }
        if (bigValue != null && other.bigValue != null) {
            return bigValue.compareTo(other.bigValue);
        }
        if (bigValue != null) {
            return bigValue.signum();
        }
        return -other.bigValue.signum();
    }

    public ProtosIntegerValue addForRuntime(ProtosIntegerValue other) {
        Objects.requireNonNull(other, "other");
        if (bigValue == null && other.bigValue == null) {
            try {
                return new ProtosIntegerValue(Math.addExact(smallValue, other.smallValue));
            } catch (ArithmeticException overflow) {
                return new ProtosIntegerValue(
                        BigInteger.valueOf(smallValue).add(BigInteger.valueOf(other.smallValue)));
            }
        }
        return new ProtosIntegerValue(value().add(other.value()));
    }

    public ProtosIntegerValue subtractForRuntime(ProtosIntegerValue other) {
        Objects.requireNonNull(other, "other");
        if (bigValue == null && other.bigValue == null) {
            try {
                return new ProtosIntegerValue(Math.subtractExact(smallValue, other.smallValue));
            } catch (ArithmeticException overflow) {
                return new ProtosIntegerValue(
                        BigInteger.valueOf(smallValue).subtract(BigInteger.valueOf(other.smallValue)));
            }
        }
        return new ProtosIntegerValue(value().subtract(other.value()));
    }

    public ProtosIntegerValue multiplyForRuntime(ProtosIntegerValue other) {
        Objects.requireNonNull(other, "other");
        if (bigValue == null && other.bigValue == null) {
            try {
                return new ProtosIntegerValue(Math.multiplyExact(smallValue, other.smallValue));
            } catch (ArithmeticException overflow) {
                return new ProtosIntegerValue(
                        BigInteger.valueOf(smallValue).multiply(BigInteger.valueOf(other.smallValue)));
            }
        }
        return new ProtosIntegerValue(value().multiply(other.value()));
    }

    public ProtosIntegerValue divideForRuntime(ProtosIntegerValue other) {
        Objects.requireNonNull(other, "other");
        if (bigValue == null && other.bigValue == null) {
            if (other.smallValue == 0L) {
                throw new ArithmeticException("BigInteger divide by zero");
            }
            if (smallValue == Long.MIN_VALUE && other.smallValue == -1L) {
                return new ProtosIntegerValue(BigInteger.valueOf(Long.MIN_VALUE).negate());
            }
            return new ProtosIntegerValue(smallValue / other.smallValue);
        }
        return new ProtosIntegerValue(value().divide(other.value()));
    }

    public ProtosIntegerValue remainderForRuntime(ProtosIntegerValue other) {
        Objects.requireNonNull(other, "other");
        if (bigValue == null && other.bigValue == null) {
            if (other.smallValue == 0L) {
                throw new ArithmeticException("BigInteger divide by zero");
            }
            return new ProtosIntegerValue(smallValue % other.smallValue);
        }
        return new ProtosIntegerValue(value().remainder(other.value()));
    }

    private static boolean fitsSignedLong(BigInteger value) {
        return value.bitLength() <= SIGNED_LONG_MAGNITUDE_BITS;
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude prelude) {
        return ProtosRepresentedValue.requirePrelude(prelude, "Integer").integerPrototype();
    }


    @ExportMessage
    boolean isNumber() {
        return true;
    }

    @ExportMessage
    boolean fitsInByte() {
        if (bigValue == null) {
            return smallValue >= Byte.MIN_VALUE && smallValue <= Byte.MAX_VALUE;
        }
        return fitsInByteBig(bigValue);
    }

    @ExportMessage
    boolean fitsInShort() {
        if (bigValue == null) {
            return smallValue >= Short.MIN_VALUE && smallValue <= Short.MAX_VALUE;
        }
        return fitsInShortBig(bigValue);
    }

    @ExportMessage
    boolean fitsInInt() {
        if (bigValue == null) {
            return smallValue >= Integer.MIN_VALUE && smallValue <= Integer.MAX_VALUE;
        }
        return fitsInIntBig(bigValue);
    }

    @ExportMessage
    boolean fitsInLong() {
        return bigValue == null || fitsInLongBig(bigValue);
    }

    @ExportMessage
    boolean fitsInBigInteger() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    boolean fitsInFloat() {
        return ProtosIntegralInteropSupport.fitsInFloat(value());
    }

    @ExportMessage
    @TruffleBoundary
    boolean fitsInDouble() {
        return ProtosIntegralInteropSupport.fitsInDouble(value());
    }

    @ExportMessage
    byte asByte() throws UnsupportedMessageException {
        if (bigValue == null) {
            if (smallValue < Byte.MIN_VALUE || smallValue > Byte.MAX_VALUE) {
                throw UnsupportedMessageException.create();
            }
            return (byte) smallValue;
        }
        return asByteBig(bigValue);
    }

    @ExportMessage
    short asShort() throws UnsupportedMessageException {
        if (bigValue == null) {
            if (smallValue < Short.MIN_VALUE || smallValue > Short.MAX_VALUE) {
                throw UnsupportedMessageException.create();
            }
            return (short) smallValue;
        }
        return asShortBig(bigValue);
    }

    @ExportMessage
    int asInt() throws UnsupportedMessageException {
        if (bigValue == null) {
            if (smallValue < Integer.MIN_VALUE || smallValue > Integer.MAX_VALUE) {
                throw UnsupportedMessageException.create();
            }
            return (int) smallValue;
        }
        return asIntBig(bigValue);
    }

    @ExportMessage
    long asLong() throws UnsupportedMessageException {
        if (bigValue == null) {
            return smallValue;
        }
        return asLongBig(bigValue);
    }

    @ExportMessage
    BigInteger asBigInteger() {
        return value();
    }

    @ExportMessage
    @TruffleBoundary
    float asFloat() throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asFloat(value());
    }

    @ExportMessage
    @TruffleBoundary
    double asDouble() throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asDouble(value());
    }

    @ExportMessage
    @TruffleBoundary
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return bigValue != null ? bigValue.toString() : Long.toString(smallValue);
    }

    @TruffleBoundary
    private static boolean fitsInByteBig(BigInteger value) {
        return ProtosIntegralInteropSupport.fitsInByte(value);
    }

    @TruffleBoundary
    private static boolean fitsInShortBig(BigInteger value) {
        return ProtosIntegralInteropSupport.fitsInShort(value);
    }

    @TruffleBoundary
    private static boolean fitsInIntBig(BigInteger value) {
        return ProtosIntegralInteropSupport.fitsInInt(value);
    }

    @TruffleBoundary
    private static boolean fitsInLongBig(BigInteger value) {
        return ProtosIntegralInteropSupport.fitsInLong(value);
    }

    @TruffleBoundary
    private static byte asByteBig(BigInteger value) throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asByte(value);
    }

    @TruffleBoundary
    private static short asShortBig(BigInteger value) throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asShort(value);
    }

    @TruffleBoundary
    private static int asIntBig(BigInteger value) throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asInt(value);
    }

    @TruffleBoundary
    private static long asLongBig(BigInteger value) throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asLong(value);
    }

}

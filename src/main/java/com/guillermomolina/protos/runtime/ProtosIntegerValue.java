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

    /**
     * Exact unsigned-width check for bounded host protocols.
     * Arbitrarily large values remain exact and are never narrowed.
     */
    public boolean fitsUnsignedBitsForRuntime(int bits) {
        if (bits < 0) {
            throw new IllegalArgumentException("negative unsigned width");
        }
        if (bigValue != null) {
            return bigValue.signum() >= 0 && bigValue.bitLength() <= bits;
        }
        if (smallValue < 0) {
            return false;
        }
        return bits >= Long.SIZE - 1 || (smallValue >>> bits) == 0L;
    }

    /**
     * Exact non-negative Integer denoted by an unsigned big-endian octet sequence, as used by
     * fixed-width host encodings such as IP address bits. Only sequences beyond the signed-long
     * range allocate arbitrary-precision state.
     */
    public static ProtosIntegerValue fromUnsignedBigEndianForRuntime(byte[] octets) {
        Objects.requireNonNull(octets, "octets");
        int first = 0;
        while (first < octets.length && octets[first] == 0) {
            first++;
        }
        int significant = octets.length - first;
        if (significant < Long.BYTES
                || (significant == Long.BYTES && octets[first] > 0)) {
            long value = 0L;
            for (int index = first; index < octets.length; index++) {
                value = (value << Byte.SIZE) | (octets[index] & 0xffL);
            }
            return new ProtosIntegerValue(value);
        }
        return fromUnsignedBigEndianBig(octets);
    }

    /**
     * Unsigned big-endian encoding of exactly {@code width} octets. The value must be
     * non-negative and fit {@code width * 8} bits; the encoding never truncates.
     */
    public byte[] toUnsignedBigEndianForRuntime(int width) {
        if (width < 0 || !fitsUnsignedBitsForRuntime(width * Byte.SIZE)) {
            throw new ArithmeticException("Integer does not fit the unsigned width");
        }
        byte[] result = new byte[width];
        if (bigValue != null) {
            copyUnsignedBig(bigValue, result);
            return result;
        }
        long remaining = smallValue;
        for (int index = width - 1; index >= 0 && remaining != 0L; index--) {
            result[index] = (byte) remaining;
            remaining >>>= Byte.SIZE;
        }
        return result;
    }

    @TruffleBoundary
    private static ProtosIntegerValue fromUnsignedBigEndianBig(byte[] octets) {
        return new ProtosIntegerValue(new BigInteger(1, octets));
    }

    @TruffleBoundary
    private static void copyUnsignedBig(BigInteger value, byte[] result) {
        byte[] raw = value.toByteArray();
        int offset = raw.length > 1 && raw[0] == 0 ? 1 : 0;
        int length = raw.length - offset;
        System.arraycopy(raw, offset, result, result.length - length, length);
    }

    /**
     * Hash of the exact value, identical to the canonical arbitrary-precision host hash so the
     * observable identity hash never depends on the internal representation. Small values are
     * hashed without allocating arbitrary-precision state.
     */
    public int exactHashCodeForRuntime() {
        if (bigValue != null) {
            return bigHashCode(bigValue);
        }
        if (smallValue == 0L) {
            return 0;
        }
        long magnitude = smallValue < 0L ? -smallValue : smallValue;
        int high = (int) (magnitude >>> Integer.SIZE);
        int low = (int) magnitude;
        int hash = high != 0 ? 31 * high + low : low;
        return smallValue < 0L ? -hash : hash;
    }

    @TruffleBoundary
    private static int bigHashCode(BigInteger value) {
        return value.hashCode();
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
                return addBig(this, other);
            }
        }
        return addBig(this, other);
    }

    public ProtosIntegerValue subtractForRuntime(ProtosIntegerValue other) {
        Objects.requireNonNull(other, "other");
        if (bigValue == null && other.bigValue == null) {
            try {
                return new ProtosIntegerValue(Math.subtractExact(smallValue, other.smallValue));
            } catch (ArithmeticException overflow) {
                return subtractBig(this, other);
            }
        }
        return subtractBig(this, other);
    }

    public ProtosIntegerValue multiplyForRuntime(ProtosIntegerValue other) {
        Objects.requireNonNull(other, "other");
        if (bigValue == null && other.bigValue == null) {
            try {
                return new ProtosIntegerValue(Math.multiplyExact(smallValue, other.smallValue));
            } catch (ArithmeticException overflow) {
                return multiplyBig(this, other);
            }
        }
        return multiplyBig(this, other);
    }

    public ProtosIntegerValue divideForRuntime(ProtosIntegerValue other) {
        Objects.requireNonNull(other, "other");
        if (bigValue == null && other.bigValue == null) {
            if (other.smallValue == 0L) {
                throw new ArithmeticException("BigInteger divide by zero");
            }
            if (smallValue == Long.MIN_VALUE && other.smallValue == -1L) {
                return divideBig(this, other);
            }
            return new ProtosIntegerValue(smallValue / other.smallValue);
        }
        return divideBig(this, other);
    }

    public ProtosIntegerValue remainderForRuntime(ProtosIntegerValue other) {
        Objects.requireNonNull(other, "other");
        if (bigValue == null && other.bigValue == null) {
            if (other.smallValue == 0L) {
                throw new ArithmeticException("BigInteger divide by zero");
            }
            return new ProtosIntegerValue(smallValue % other.smallValue);
        }
        return remainderBig(this, other);
    }

    /*
     * TEST009-E: the arbitrary-precision results (long overflow or a big operand)
     * are rare and BigInteger arithmetic has no partial-evaluation value; inlined,
     * they were expanded into every compiled Integer operation site.
     */
    @TruffleBoundary
    private static ProtosIntegerValue addBig(ProtosIntegerValue left, ProtosIntegerValue right) {
        return new ProtosIntegerValue(left.value().add(right.value()));
    }

    @TruffleBoundary
    private static ProtosIntegerValue subtractBig(ProtosIntegerValue left, ProtosIntegerValue right) {
        return new ProtosIntegerValue(left.value().subtract(right.value()));
    }

    @TruffleBoundary
    private static ProtosIntegerValue multiplyBig(ProtosIntegerValue left, ProtosIntegerValue right) {
        return new ProtosIntegerValue(left.value().multiply(right.value()));
    }

    @TruffleBoundary
    private static ProtosIntegerValue divideBig(ProtosIntegerValue left, ProtosIntegerValue right) {
        return new ProtosIntegerValue(left.value().divide(right.value()));
    }

    @TruffleBoundary
    private static ProtosIntegerValue remainderBig(ProtosIntegerValue left, ProtosIntegerValue right) {
        return new ProtosIntegerValue(left.value().remainder(right.value()));
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

    /*
     * The arbitrary-precision representation is used only outside the signed-long range, so it
     * never fits a fixed host integral width and only binary32/binary64 projection can apply.
     */
    @ExportMessage
    boolean fitsInByte() {
        return bigValue == null
                && smallValue >= Byte.MIN_VALUE
                && smallValue <= Byte.MAX_VALUE;
    }

    @ExportMessage
    boolean fitsInShort() {
        return bigValue == null
                && smallValue >= Short.MIN_VALUE
                && smallValue <= Short.MAX_VALUE;
    }

    @ExportMessage
    boolean fitsInInt() {
        return bigValue == null
                && smallValue >= Integer.MIN_VALUE
                && smallValue <= Integer.MAX_VALUE;
    }

    @ExportMessage
    boolean fitsInLong() {
        return bigValue == null;
    }

    @ExportMessage
    boolean fitsInBigInteger() {
        return true;
    }

    @ExportMessage
    boolean fitsInFloat() {
        if (bigValue == null) {
            return smallFitsInFloat(smallValue);
        }
        return fitsInFloatBig(bigValue);
    }

    @ExportMessage
    boolean fitsInDouble() {
        if (bigValue == null) {
            return smallFitsInDouble(smallValue);
        }
        return fitsInDoubleBig(bigValue);
    }

    @ExportMessage
    byte asByte() throws UnsupportedMessageException {
        if (!fitsInByte()) {
            throw UnsupportedMessageException.create();
        }
        return (byte) smallValue;
    }

    @ExportMessage
    short asShort() throws UnsupportedMessageException {
        if (!fitsInShort()) {
            throw UnsupportedMessageException.create();
        }
        return (short) smallValue;
    }

    @ExportMessage
    int asInt() throws UnsupportedMessageException {
        if (!fitsInInt()) {
            throw UnsupportedMessageException.create();
        }
        return (int) smallValue;
    }

    @ExportMessage
    long asLong() throws UnsupportedMessageException {
        if (bigValue != null) {
            throw UnsupportedMessageException.create();
        }
        return smallValue;
    }

    @ExportMessage
    BigInteger asBigInteger() {
        return value();
    }

    @ExportMessage
    float asFloat() throws UnsupportedMessageException {
        if (bigValue == null) {
            if (!smallFitsInFloat(smallValue)) {
                throw UnsupportedMessageException.create();
            }
            return (float) smallValue;
        }
        return asFloatBig(bigValue);
    }

    @ExportMessage
    double asDouble() throws UnsupportedMessageException {
        if (bigValue == null) {
            if (!smallFitsInDouble(smallValue)) {
                throw UnsupportedMessageException.create();
            }
            return (double) smallValue;
        }
        return asDoubleBig(bigValue);
    }

    @ExportMessage
    @TruffleBoundary
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return bigValue != null ? bigValue.toString() : Long.toString(smallValue);
    }

    /*
     * A rounded conversion below 2^63 is integral and converts back to long exactly, so the
     * round trip decides exact representability; 2^63 itself is beyond every long.
     */
    private static boolean smallFitsInFloat(long value) {
        float converted = (float) value;
        return converted != 0x1p63f && (long) converted == value;
    }

    private static boolean smallFitsInDouble(long value) {
        double converted = (double) value;
        return converted != 0x1p63 && (long) converted == value;
    }

    @TruffleBoundary
    private static boolean fitsInFloatBig(BigInteger value) {
        return ProtosIntegralInteropSupport.fitsInFloat(value);
    }

    @TruffleBoundary
    private static boolean fitsInDoubleBig(BigInteger value) {
        return ProtosIntegralInteropSupport.fitsInDouble(value);
    }

    @TruffleBoundary
    private static float asFloatBig(BigInteger value) throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asFloat(value);
    }

    @TruffleBoundary
    private static double asDoubleBig(BigInteger value) throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asDouble(value);
    }
}

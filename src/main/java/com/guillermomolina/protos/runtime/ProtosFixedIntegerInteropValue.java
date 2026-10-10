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
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Internal fixed-width carrier for host/foreign interop.
 *
 * <p>This is not a Protos semantic numeric value, has no Core prototype, and
 * does not participate in Protos equality, identity, hashing, transfer, or
 * arithmetic. It preserves width/range validation and exact Truffle integral
 * projection machinery for a future explicitly designed interop boundary.
 */
@ExportLibrary(InteropLibrary.class)
final class ProtosFixedIntegerInteropValue implements TruffleObject {
    /**
     * Width and signedness of a fixed host integral family. Range membership is decided
     * exactly from the bit length, never from overflowing primitive arithmetic.
     */
    public enum Kind {
        UINT8(8, false),
        INT8(8, true),
        UINT16(16, false),
        INT16(16, true),
        UINT32(32, false),
        INT32(32, true),
        UINT64(64, false),
        INT64(64, true);

        private final int width;
        private final boolean signed;

        Kind(int width, boolean signed) {
            this.width = width;
            this.signed = signed;
        }

        public int width() {
            return width;
        }

        public boolean signed() {
            return signed;
        }

        /** The family minimum; every family minimum lies within the signed-64 range. */
        public long minimum() {
            return signed ? -1L << (width - 1) : 0L;
        }

        /**
         * The two's-complement bits of the family maximum. They read as signed for every family
         * except UINT64, whose maximum 2^64 - 1 is the unsigned pattern of {@code -1L}.
         */
        public long maximumBits() {
            if (signed) {
                return ~minimum();
            }
            return width == Long.SIZE ? -1L : ~(-1L << width);
        }

        /** Whether the exact current Integer {@code integer}, of any magnitude, is in range. */
        public boolean containsInteger(Object integer) {
            if (ProtosNumericValueSupport.isIntegerInLongRange(integer)) {
                return contains(ProtosNumericValueSupport.exactLong(integer));
            }
            ProtosNumericValueSupport.requireCurrentInteger(integer);
            // Outside the signed-64 range only UINT64 holds values, those below 2^64.
            return this == UINT64 && ProtosNumericValueSupport.isUnsignedIntegerWithin(integer, width);
        }

        /** Whether the exact signed-64 {@code value} lies in this family's range. */
        public boolean contains(long value) {
            if (signed) {
                return width == Long.SIZE
                        || (value >= minimum() && value <= ~minimum());
            }
            return value >= 0L && (width == Long.SIZE || (value >>> width) == 0L);
        }
    }

    private final Kind kind;
    /** Two's-complement bits of the value; unsigned for UINT64 values at or above 2^63. */
    private final long bits;

    private ProtosFixedIntegerInteropValue(Kind kind, long bits) {
        this.kind = kind;
        this.bits = bits;
    }

    /** The value of {@code kind} denoting the exact signed-64 {@code value}. */
    public static ProtosFixedIntegerInteropValue ofLong(Kind kind, long value) {
        Objects.requireNonNull(kind, "kind");
        if (!kind.contains(value)) {
            throw new IllegalArgumentException(value + " is outside " + kind + " range");
        }
        return new ProtosFixedIntegerInteropValue(kind, value);
    }

    /** The UINT64 value whose unsigned 64-bit pattern is {@code bits}; every pattern is valid. */
    public static ProtosFixedIntegerInteropValue ofUnsignedBits(long bits) {
        return new ProtosFixedIntegerInteropValue(Kind.UINT64, bits);
    }

    /** The value of {@code kind} denoting the exact current Integer {@code integer}. */
    public static ProtosFixedIntegerInteropValue ofInteger(Kind kind, Object integer) {
        Objects.requireNonNull(kind, "kind");
        if (!kind.containsInteger(integer)) {
            throw new IllegalArgumentException("Integer is outside " + kind + " range");
        }
        if (ProtosNumericValueSupport.isIntegerInLongRange(integer)) {
            return new ProtosFixedIntegerInteropValue(
                    kind, ProtosNumericValueSupport.exactLong(integer));
        }
        byte[] octets = ProtosNumericValueSupport.unsignedBigEndianOrNull(integer, Long.BYTES);
        long unsignedBits = 0L;
        for (byte octet : octets) {
            unsignedBits = (unsignedBits << Byte.SIZE) | (octet & 0xffL);
        }
        return ofUnsignedBits(unsignedBits);
    }

    public Kind kind() {
        return kind;
    }

    /** Two's-complement bits of the value; unsigned for UINT64 values at or above 2^63. */
    public long bits() {
        return bits;
    }

    /**
     * The exact current Integer this value denotes. Only a UINT64 value at or above 2^63 needs
     * {@code prelude}, which mints the large Integer.
     */
    public Object integer(ProtosPrelude prelude) {
        if (!beyondSignedLong()) {
            return ProtosNumericValueSupport.integer(bits);
        }
        byte[] octets = new byte[Long.BYTES];
        long remaining = bits;
        for (int index = Long.BYTES - 1; index >= 0; index--) {
            octets[index] = (byte) remaining;
            remaining >>>= Byte.SIZE;
        }
        return ProtosNumericValueSupport.integerFromUnsignedBigEndian(octets, prelude);
    }

    private boolean beyondSignedLong() {
        return bits < 0L && !kind.signed;
    }

    @ExportMessage
    boolean isNumber() {
        return true;
    }

    @ExportMessage
    boolean fitsInByte() {
        return bits >= Byte.MIN_VALUE && bits <= Byte.MAX_VALUE && !beyondSignedLong();
    }

    @ExportMessage
    boolean fitsInShort() {
        return bits >= Short.MIN_VALUE && bits <= Short.MAX_VALUE && !beyondSignedLong();
    }

    @ExportMessage
    boolean fitsInInt() {
        return bits >= Integer.MIN_VALUE && bits <= Integer.MAX_VALUE && !beyondSignedLong();
    }

    @ExportMessage
    boolean fitsInLong() {
        return !beyondSignedLong();
    }

    @ExportMessage
    boolean fitsInBigInteger() {
        return true;
    }

    @ExportMessage
    boolean fitsInFloat() {
        return beyondSignedLong()
                ? ProtosIntegralInteropSupport.unsignedFitsInFloat(bits)
                : ProtosIntegralInteropSupport.fitsInFloat(bits);
    }

    @ExportMessage
    boolean fitsInDouble() {
        return beyondSignedLong()
                ? ProtosIntegralInteropSupport.unsignedFitsInDouble(bits)
                : ProtosIntegralInteropSupport.fitsInDouble(bits);
    }

    @ExportMessage
    byte asByte() throws UnsupportedMessageException {
        if (!fitsInByte()) {
            throw UnsupportedMessageException.create();
        }
        return (byte) bits;
    }

    @ExportMessage
    short asShort() throws UnsupportedMessageException {
        if (!fitsInShort()) {
            throw UnsupportedMessageException.create();
        }
        return (short) bits;
    }

    @ExportMessage
    int asInt() throws UnsupportedMessageException {
        if (!fitsInInt()) {
            throw UnsupportedMessageException.create();
        }
        return (int) bits;
    }

    @ExportMessage
    long asLong() throws UnsupportedMessageException {
        if (!fitsInLong()) {
            throw UnsupportedMessageException.create();
        }
        return bits;
    }

    /* Truffle interop contract: the only arbitrary-precision projection of a fixed value. */
    @ExportMessage
    @TruffleBoundary
    BigInteger asBigInteger() {
        BigInteger exact = BigInteger.valueOf(bits);
        return beyondSignedLong() ? exact.add(BigInteger.ONE.shiftLeft(Long.SIZE)) : exact;
    }

    @ExportMessage
    float asFloat() throws UnsupportedMessageException {
        if (!fitsInFloat()) {
            throw UnsupportedMessageException.create();
        }
        return beyondSignedLong()
                ? (float) ProtosIntegralInteropSupport.unsignedToDouble(bits)
                : (float) bits;
    }

    @ExportMessage
    double asDouble() throws UnsupportedMessageException {
        if (!fitsInDouble()) {
            throw UnsupportedMessageException.create();
        }
        return beyondSignedLong()
                ? ProtosIntegralInteropSupport.unsignedToDouble(bits)
                : (double) bits;
    }

    @ExportMessage
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return beyondSignedLong() ? Long.toUnsignedString(bits) : Long.toString(bits);
    }
}

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

        public BigInteger minimum() {
            return BigInteger.valueOf(signed ? -1L << (width - 1) : 0L);
        }

        public BigInteger maximum() {
            if (!signed && width == Long.SIZE) {
                return unsigned(-1L);
            }
            return BigInteger.valueOf(signed ? ~(-1L << (width - 1)) : ~(-1L << width));
        }

        /** A signed n-bit value has bit length at most n - 1; an unsigned one is non-negative. */
        public boolean contains(BigInteger value) {
            Objects.requireNonNull(value, "value");
            if (signed) {
                return value.bitLength() <= width - 1;
            }
            return value.signum() >= 0 && value.bitLength() <= width;
        }
    }

    private final Kind kind;
    /** Two's-complement bits of the value; unsigned for UINT64 values at or above 2^63. */
    private final long bits;

    public ProtosFixedIntegerInteropValue(Kind kind, BigInteger value) {
        this.kind = Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        if (!kind.contains(value)) {
            throw new IllegalArgumentException(value + " is outside " + kind + " range");
        }
        this.bits = value.longValue();
    }

    public Kind kind() {
        return kind;
    }

    public BigInteger value() {
        return beyondSignedLong() ? unsigned(bits) : BigInteger.valueOf(bits);
    }

    /** The exact unsigned 64-bit integer denoted by {@code bits}. */
    private static BigInteger unsigned(long bits) {
        BigInteger high = BigInteger.valueOf(bits >>> 1).shiftLeft(1);
        return (bits & 1L) == 0L ? high : high.setBit(0);
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

    @ExportMessage
    BigInteger asBigInteger() {
        return value();
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

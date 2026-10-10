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

import java.math.BigInteger;
import java.util.Objects;

/**
 * Internal boundary for the currently published Core numeric carriers.
 *
 * <p>A semantic Integer is a {@link ProtosIntegerValue} within the signed-64
 * range or a {@link ProtosLargeIntegerValue} outside it (I091 / PLAT056
 * Candidate C); every exact Integer result is normalized here, so the two never
 * denote the same value. A Float is a {@link ProtosFloatValue}. Host Long,
 * Double, BigInteger, and arbitrary frozen guest objects do not thereby become
 * semantic Protos numbers. The D197 family transition requires a separate
 * normative and implementation gate.
 *
 * <p>Ordinary copies are created only at explicit transfer boundaries.
 * This class installs no guest methods, global registry, or rich-value
 * materialization in ordinary arithmetic.
 */
public final class ProtosNumericValueSupport {
    private ProtosNumericValueSupport() {}

    public static boolean isCurrentInteger(Object value) {
        return value instanceof ProtosIntegerValue || value instanceof ProtosLargeIntegerValue;
    }

    /** Whether {@code value} is a semantic Integer outside the signed-64 range. */
    public static boolean isLargeInteger(Object value) {
        return value instanceof ProtosLargeIntegerValue;
    }

    public static boolean isCurrentFloat(Object value) {
        return value instanceof ProtosFloatValue;
    }

    public static boolean isCurrentNumber(Object value) {
        return isCurrentInteger(value) || isCurrentFloat(value);
    }

    /**
     * I091 internal primitive carriers: a raw {@code Long} (signed-64 Integer) or
     * {@code Double} (binary64 Float) produced by guarded lowered numeric execution and
     * retained in operand and compact frame storage.
     *
     * <p>Such a carrier is never a guest value. It is unambiguous inside guest frames because
     * no host scalar reaches them unconverted: host executable arguments are admitted as
     * Protos values, and every other foreign value is wrapped. Every frame observer that can
     * expose a binding outside the lowered carrier chain materializes through {@link
     * #guestValue}.
     */
    public static boolean isPrimitiveCarrier(Object value) {
        return value instanceof Long || value instanceof Double;
    }

    /** The guest value denoted by {@code value}; identity for every non-carrier. */
    public static Object guestValue(Object value) {
        if (value instanceof Long small) {
            return new ProtosIntegerValue(small.longValue());
        }
        if (value instanceof Double floating) {
            return new ProtosFloatValue(floating.doubleValue());
        }
        return value;
    }

    /*
     * I091 semantic Integer capabilities for generic clients (I/O, network,
     * collections), so they need not depend on the physical Integer class.
     */

    /*
     * Integer identity, equality and hashing are by value, so the octet
     * Integers that byte-oriented I/O produces per byte can be shared.
     */
    private static final ProtosIntegerValue[] OCTETS = new ProtosIntegerValue[256];

    static {
        for (int octet = 0; octet < OCTETS.length; octet++) {
            OCTETS[octet] = new ProtosIntegerValue(octet);
        }
    }

    /** The guest Integer denoting {@code value}. */
    public static Object integer(long value) {
        return value >= 0 && value < OCTETS.length
                ? OCTETS[(int) value]
                : new ProtosIntegerValue(value);
    }

    /** The guest Integer of an unsigned octet {@code 0..255}. */
    public static Object octet(int value) {
        if (value < 0 || value >= OCTETS.length) {
            throw new IllegalArgumentException("octet out of range: " + value);
        }
        return OCTETS[value];
    }

    /** Whether {@code value} is a current Integer within the Java {@code int} range. */
    public static boolean isIntegerInIntRange(Object value) {
        return value instanceof ProtosIntegerValue integer && integer.fitsInIntForRuntime();
    }

    /** The exact {@code int} of a value admitted by {@link #isIntegerInIntRange}. */
    public static int exactInt(Object value) {
        if (!isIntegerInIntRange(value)) {
            throw new IllegalArgumentException("value is not an Integer in the int range");
        }
        return ((ProtosIntegerValue) value).intValueExactForRuntime();
    }

    /** Whether {@code value} is a current Integer within the signed-64 range. */
    public static boolean isIntegerInLongRange(Object value) {
        return value instanceof ProtosIntegerValue;
    }

    /** The exact {@code long} of a value admitted by {@link #isIntegerInLongRange}. */
    public static long exactLong(Object value) {
        if (!(value instanceof ProtosIntegerValue integer)) {
            throw new IllegalArgumentException("value is not an Integer in the long range");
        }
        return integer.longValue();
    }

    /** Whether {@code value} is a non-negative current Integer of at most {@code bits} bits. */
    public static boolean isUnsignedIntegerWithin(Object value, int bits) {
        if (value instanceof ProtosIntegerValue integer) {
            return integer.fitsUnsignedBitsForRuntime(bits);
        }
        return value instanceof ProtosLargeIntegerValue large
                && bigFitsUnsignedBits(large.exactValue(), bits);
    }

    /**
     * The unsigned big-endian encoding of a non-negative current Integer in exactly
     * {@code width} octets, or {@code null} when {@code value} is not such an Integer.
     */
    public static byte[] unsignedBigEndianOrNull(Object value, int width) {
        if (width < 0 || !isUnsignedIntegerWithin(value, width * Byte.SIZE)) {
            return null;
        }
        if (value instanceof ProtosIntegerValue integer) {
            return integer.toUnsignedBigEndianForRuntime(width);
        }
        return bigToUnsignedBigEndian(((ProtosLargeIntegerValue) value).exactValue(), width);
    }

    /**
     * The guest Integer whose unsigned big-endian encoding is {@code octets}. Only encodings
     * beyond the signed-64 range need {@code prelude}, which mints the large Integer.
     */
    public static Object integerFromUnsignedBigEndian(byte[] octets, ProtosPrelude prelude) {
        return integerFromUnsignedBigEndian(
                octets, prelude == null ? null : prelude.integerPrototype());
    }

    /**
     * The guest Integer whose unsigned big-endian encoding is {@code octets}; only encodings
     * beyond the signed-64 range need {@code integerPrototype}.
     */
    public static Object integerFromUnsignedBigEndian(
            byte[] octets, ProtosObjectValue integerPrototype) {
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
            return integer(value);
        }
        return integerWithPrototype(unsignedBig(octets), integerPrototype);
    }

    /** The semantic Integer {@code value} admitted by {@link #isCurrentInteger}, unchanged. */
    public static Object requireCurrentInteger(Object value) {
        if (isCurrentInteger(value)) {
            return value;
        }
        throw new IllegalArgumentException(
                "value is not a current exact-integer family");
    }

    /*
     * I091 / PLAT056 Candidate C exact Integer service. Every exact Integer result is
     * normalized: a value within the signed-64 range is a ProtosIntegerValue, every other value
     * a ProtosLargeIntegerValue minted with the Integer prototype of the supplied Prelude.
     */

    /**
     * The normalized semantic Integer denoting {@code value}. {@code prelude} is consulted only
     * for a value outside the signed-64 range.
     */
    public static Object integer(BigInteger value, ProtosPrelude prelude) {
        Objects.requireNonNull(value, "value");
        if (value.bitLength() < Long.SIZE) {
            return integer(value.longValue());
        }
        Objects.requireNonNull(prelude, "a large Integer requires its owning Core prelude");
        return new ProtosLargeIntegerValue(prelude.integerPrototype(), value);
    }

    /**
     * The normalized semantic Integer denoting {@code value}, minting a large value with the
     * explicit Integer prototype {@code integerPrototype}. Used where the prototype, rather than a
     * whole Prelude, is the available owner (for example a destination prototype during
     * rematerialization).
     */
    public static Object integerWithPrototype(
            BigInteger value, ProtosObjectValue integerPrototype) {
        Objects.requireNonNull(value, "value");
        if (value.bitLength() < Long.SIZE) {
            return integer(value.longValue());
        }
        Objects.requireNonNull(
                integerPrototype, "a large Integer requires its owning Integer prototype");
        return new ProtosLargeIntegerValue(integerPrototype, value);
    }

    /** The exact value of a semantic Integer as a temporary host BigInteger. */
    public static BigInteger exactBigInteger(Object integer) {
        if (integer instanceof ProtosIntegerValue small) {
            return BigInteger.valueOf(small.longValue());
        }
        if (integer instanceof ProtosLargeIntegerValue large) {
            return large.exactValue();
        }
        throw new IllegalArgumentException("value is not a current exact-integer family");
    }

    public static int integerSignum(Object integer) {
        if (integer instanceof ProtosIntegerValue small) {
            return small.signumForRuntime();
        }
        return largeValue(integer).signum();
    }

    /** Canonical decimal spelling of a semantic Integer. */
    public static String integerDecimalText(Object integer) {
        if (integer instanceof ProtosIntegerValue small) {
            return small.decimalTextForRuntime();
        }
        return bigDecimalText(largeValue(integer));
    }

    /** Exact mathematical order of two semantic Integers. */
    public static int compareIntegers(Object left, Object right) {
        if (left instanceof ProtosIntegerValue a && right instanceof ProtosIntegerValue b) {
            return Long.compare(a.longValue(), b.longValue());
        }
        /* A large Integer lies beyond every signed-64 value, on the side of its sign. */
        if (left instanceof ProtosIntegerValue) {
            return -largeValue(right).signum();
        }
        if (right instanceof ProtosIntegerValue) {
            return largeValue(left).signum();
        }
        return bigCompare(largeValue(left), largeValue(right));
    }

    /** Whether two semantic Integers denote the same value. */
    public static boolean sameInteger(Object left, Object right) {
        if (left instanceof ProtosIntegerValue a && right instanceof ProtosIntegerValue b) {
            return a.longValue() == b.longValue();
        }
        if (left instanceof ProtosLargeIntegerValue a
                && right instanceof ProtosLargeIntegerValue b) {
            return bigEquals(a.exactValue(), b.exactValue());
        }
        return false;
    }

    /** Hash of the exact value, equal to the canonical arbitrary-precision host hash. */
    public static int integerHashCode(Object integer) {
        if (integer instanceof ProtosIntegerValue small) {
            return small.exactHashCodeForRuntime();
        }
        return bigHashCode(largeValue(integer));
    }

    public static Object addIntegers(Object left, Object right, ProtosPrelude prelude) {
        if (left instanceof ProtosIntegerValue a && right instanceof ProtosIntegerValue b) {
            long x = a.longValue();
            long y = b.longValue();
            long sum = x + y;
            if (((x ^ sum) & (y ^ sum)) >= 0L) {
                return integer(sum);
            }
        }
        return bigResult(Operation.ADD, left, right, prelude);
    }

    public static Object subtractIntegers(Object left, Object right, ProtosPrelude prelude) {
        if (left instanceof ProtosIntegerValue a && right instanceof ProtosIntegerValue b) {
            long x = a.longValue();
            long y = b.longValue();
            long difference = x - y;
            if (((x ^ y) & (x ^ difference)) >= 0L) {
                return integer(difference);
            }
        }
        return bigResult(Operation.SUBTRACT, left, right, prelude);
    }

    public static Object multiplyIntegers(Object left, Object right, ProtosPrelude prelude) {
        if (left instanceof ProtosIntegerValue a && right instanceof ProtosIntegerValue b) {
            long x = a.longValue();
            long y = b.longValue();
            long high = Math.multiplyHigh(x, y);
            long low = x * y;
            if ((high == 0L && low >= 0L) || (high == -1L && low < 0L)) {
                return integer(low);
            }
        }
        return bigResult(Operation.MULTIPLY, left, right, prelude);
    }

    /** Truncating quotient; the divisor must not be zero. */
    public static Object quotientIntegers(Object left, Object right, ProtosPrelude prelude) {
        if (left instanceof ProtosIntegerValue a && right instanceof ProtosIntegerValue b) {
            long x = a.longValue();
            long y = b.longValue();
            if (y == 0L) {
                throw new ArithmeticException("BigInteger divide by zero");
            }
            if (x != Long.MIN_VALUE || y != -1L) {
                return integer(x / y);
            }
        } else if (left instanceof ProtosIntegerValue a) {
            return integer(divisionByLargeIsMinusOne(a.longValue(), largeValue(right)) ? -1L : 0L);
        }
        return bigResult(Operation.QUOTIENT, left, right, prelude);
    }

    /** Truncating remainder, signed like the dividend; the divisor must not be zero. */
    public static Object remainderIntegers(Object left, Object right, ProtosPrelude prelude) {
        if (left instanceof ProtosIntegerValue a && right instanceof ProtosIntegerValue b) {
            long y = b.longValue();
            if (y == 0L) {
                throw new ArithmeticException("BigInteger divide by zero");
            }
            return integer(a.longValue() % y);
        }
        if (left instanceof ProtosIntegerValue a) {
            return divisionByLargeIsMinusOne(a.longValue(), largeValue(right)) ? integer(0L) : a;
        }
        return bigResult(Operation.REMAINDER, left, right, prelude);
    }

    /**
     * Whether a result of {@code operation} needs a Prelude to be represented: true exactly when
     * the exact result lies outside the signed-64 range. Primitive fast paths without a Prelude
     * use this to fall back to the ordinary path, which always has one.
     */
    public static boolean needsPreludeForResult(
            Operation operation, Object left, Object right) {
        if (left instanceof ProtosIntegerValue a && right instanceof ProtosIntegerValue b) {
            long x = a.longValue();
            long y = b.longValue();
            return switch (operation) {
                case ADD -> ((x ^ (x + y)) & (y ^ (x + y))) < 0L;
                case SUBTRACT -> ((x ^ y) & (x ^ (x - y))) < 0L;
                case MULTIPLY -> {
                    long high = Math.multiplyHigh(x, y);
                    long low = x * y;
                    yield !((high == 0L && low >= 0L) || (high == -1L && low < 0L));
                }
                case QUOTIENT -> x == Long.MIN_VALUE && y == -1L;
                case REMAINDER -> false;
            };
        }
        if (left instanceof ProtosIntegerValue
                && (operation == Operation.QUOTIENT || operation == Operation.REMAINDER)) {
            return false;
        }
        return true;
    }

    /** Exact Integer operations of {@link #needsPreludeForResult}. */
    public enum Operation {
        ADD,
        SUBTRACT,
        MULTIPLY,
        QUOTIENT,
        REMAINDER
    }

    /*
     * A large divisor lies outside the signed-64 range, so its magnitude is at least 2^63 and
     * every signed-64 dividend truncates to quotient 0 with itself as remainder. The single
     * exception is Long.MIN_VALUE divided by +2^63, whose quotient is -1 with remainder 0.
     */
    @TruffleBoundary
    private static boolean divisionByLargeIsMinusOne(long dividend, BigInteger divisor) {
        return dividend == Long.MIN_VALUE
                && divisor.signum() > 0
                && divisor.bitLength() == Long.SIZE
                && divisor.getLowestSetBit() == Long.SIZE - 1;
    }

    /*
     * TEST009-E: arbitrary-precision results (signed-64 overflow or a large operand) are rare
     * and BigInteger arithmetic has no partial-evaluation value.
     */
    @TruffleBoundary
    private static Object bigResult(
            Operation operation, Object left, Object right, ProtosPrelude prelude) {
        BigInteger x = exactBigInteger(left);
        BigInteger y = exactBigInteger(right);
        BigInteger result = switch (operation) {
            case ADD -> x.add(y);
            case SUBTRACT -> x.subtract(y);
            case MULTIPLY -> x.multiply(y);
            case QUOTIENT -> x.divide(y);
            case REMAINDER -> x.remainder(y);
        };
        return integer(result, prelude);
    }

    private static BigInteger largeValue(Object integer) {
        if (integer instanceof ProtosLargeIntegerValue large) {
            return large.exactValue();
        }
        throw new IllegalArgumentException("value is not a current exact-integer family");
    }

    @TruffleBoundary
    private static int bigCompare(BigInteger left, BigInteger right) {
        return left.compareTo(right);
    }

    @TruffleBoundary
    private static boolean bigEquals(BigInteger left, BigInteger right) {
        return left.equals(right);
    }

    @TruffleBoundary
    private static int bigHashCode(BigInteger value) {
        return value.hashCode();
    }

    @TruffleBoundary
    private static String bigDecimalText(BigInteger value) {
        return value.toString();
    }

    @TruffleBoundary
    private static boolean bigFitsUnsignedBits(BigInteger value, int bits) {
        if (bits < 0) {
            throw new IllegalArgumentException("negative unsigned width");
        }
        return value.signum() >= 0 && value.bitLength() <= bits;
    }

    @TruffleBoundary
    private static byte[] bigToUnsignedBigEndian(BigInteger value, int width) {
        byte[] raw = value.toByteArray();
        int offset = raw.length > 1 && raw[0] == 0 ? 1 : 0;
        int length = raw.length - offset;
        byte[] result = new byte[width];
        System.arraycopy(raw, offset, result, width - length, length);
        return result;
    }

    @TruffleBoundary
    private static BigInteger unsignedBig(byte[] octets) {
        return new BigInteger(1, octets);
    }

    public static double currentFloatValue(Object value) {
        if (value instanceof ProtosFloatValue floating) {
            return floating.value();
        }
        throw new IllegalArgumentException(
                "value is not a current Float family");
    }

    /**
     * I091 / PLAT056 Candidate C: whether {@code value} is a FROZEN ordinary object minted by a
     * rich numeric family. Membership comes only from the minting family, never from slots,
     * delegation or shape, so guest code cannot forge it.
     */
    public static boolean isRichNumericValue(Object value) {
        return value instanceof ProtosSemanticTransferValue minted
                && minted.family() instanceof ProtosRichNumericFamily
                && minted.isFrozen();
    }

    /**
     * Non-overridable value identity of two rich numeric values: the same minting family and the
     * same value of that family. Any other pair answers false.
     */
    public static boolean sameRichNumericIdentity(Object left, Object right) {
        if (!isRichNumericValue(left) || !isRichNumericValue(right)) {
            return false;
        }
        ProtosSemanticTransferValue a = (ProtosSemanticTransferValue) left;
        ProtosSemanticTransferValue b = (ProtosSemanticTransferValue) right;
        return a.family() == b.family()
                && ((ProtosRichNumericFamily) a.family())
                        .sameValue(a.familyState(), b.familyState());
    }

    /** Identity hash of a value admitted by {@link #isRichNumericValue}. */
    public static long richNumericIdentityHash(Object value) {
        if (!isRichNumericValue(value)) {
            throw new IllegalArgumentException("value is not a rich numeric value");
        }
        ProtosSemanticTransferValue minted = (ProtosSemanticTransferValue) value;
        ProtosRichNumericFamily family = (ProtosRichNumericFamily) minted.family();
        return tagged(family.identityTag(), family.valueHash(minted.familyState()));
    }

    /**
     * Current non-overridable numeric value identity, excluding the initial
     * reference-equality shortcut owned by ProtosIdentity.
     */
    public static boolean sameCurrentFamilyIdentity(Object left, Object right) {
        if (isCurrentInteger(left) && isCurrentInteger(right)) {
            return sameInteger(left, right);
        }
        if (left instanceof ProtosFloatValue a
                && right instanceof ProtosFloatValue b) {
            double av = a.value();
            double bv = b.value();
            if (Double.isNaN(av) && Double.isNaN(bv)) {
                return true;
            }
            return Double.doubleToRawLongBits(av)
                    == Double.doubleToRawLongBits(bv);
        }
        return false;
    }

    /**
     * Current numeric identity hash of a value admitted by {@link #isCurrentNumber};
     * callers retain their generic-object fallback for every other value.
     */
    public static long currentNumericIdentityHash(Object value) {
        if (isCurrentInteger(value)) {
            return tagged(1, integerHashCode(value));
        }
        if (value instanceof ProtosFloatValue floating) {
            double number = floating.value();
            long bits = Double.isNaN(number)
                    ? 0x7ff8000000000000L
                    : Double.doubleToRawLongBits(number);
            return tagged(30, Long.hashCode(bits));
        }
        throw new IllegalArgumentException("value is not a current numeric family");
    }

    /**
     * Explicit detached copy for today's numeric carriers only, owned by {@code destination}.
     *
     * <p>A signed-64 Integer or a Float is a context-free immutable value. A large Integer is an
     * ordinary guest object delegating to its minting Prelude's Integer prototype, so it is
     * rematerialized with the destination's prototype and never shared across isolated domains.
     * This method is not called on ordinary arithmetic, lookup, or numeric dispatch paths.
     */
    public static Object copyCurrentNumberOrNull(Object value, ProtosPrelude destination) {
        if (value instanceof ProtosIntegerValue integer) {
            return new ProtosIntegerValue(integer.longValue());
        }
        if (value instanceof ProtosLargeIntegerValue large) {
            return integer(large.exactValue(), destination);
        }
        if (value instanceof ProtosFloatValue floating) {
            return new ProtosFloatValue(floating.value());
        }
        return null;
    }

    private static long tagged(int family, int hash) {
        return (((long) family) << 32) ^ Integer.toUnsignedLong(hash);
    }
}

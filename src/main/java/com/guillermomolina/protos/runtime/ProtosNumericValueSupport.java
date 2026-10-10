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

import java.util.Objects;

/**
 * Internal boundary for the currently published Core numeric carriers.
 *
 * <p>Only ProtosIntegerValue and ProtosFloatValue are admitted. Host Long,
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
        return value instanceof ProtosIntegerValue;
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
        return value instanceof ProtosIntegerValue integer && integer.isSmallForRuntime();
    }

    /** The exact {@code long} of a value admitted by {@link #isIntegerInLongRange}. */
    public static long exactLong(Object value) {
        if (!isIntegerInLongRange(value)) {
            throw new IllegalArgumentException("value is not an Integer in the long range");
        }
        return ((ProtosIntegerValue) value).smallValueForRuntime();
    }

    /** Whether {@code value} is a non-negative current Integer of at most {@code bits} bits. */
    public static boolean isUnsignedIntegerWithin(Object value, int bits) {
        return value instanceof ProtosIntegerValue integer
                && integer.fitsUnsignedBitsForRuntime(bits);
    }

    /**
     * The unsigned big-endian encoding of a non-negative current Integer in exactly
     * {@code width} octets, or {@code null} when {@code value} is not such an Integer.
     */
    public static byte[] unsignedBigEndianOrNull(Object value, int width) {
        if (!(value instanceof ProtosIntegerValue integer)
                || !integer.fitsUnsignedBitsForRuntime(width * Byte.SIZE)) {
            return null;
        }
        return integer.toUnsignedBigEndianForRuntime(width);
    }

    /** The guest Integer whose unsigned big-endian encoding is {@code octets}. */
    public static Object integerFromUnsignedBigEndian(byte[] octets) {
        return ProtosIntegerValue.fromUnsignedBigEndianForRuntime(octets);
    }

    public static ProtosIntegerValue requireCurrentInteger(Object value) {
        if (value instanceof ProtosIntegerValue integer) {
            return integer;
        }
        throw new IllegalArgumentException(
                "value is not a current exact-integer family");
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
        if (left instanceof ProtosIntegerValue a
                && right instanceof ProtosIntegerValue b) {
            return a.sameIntegerForRuntime(b);
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
        if (value instanceof ProtosIntegerValue integer) {
            return tagged(1, integer.exactHashCodeForRuntime());
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
     * Explicit detached copy for today's numeric carriers only.
     *
     * <p>Small Integer copies preserve the primitive long backing without
     * needlessly constructing a host BigInteger. This method is not called
     * on ordinary arithmetic, lookup, or numeric dispatch paths.
     */
    public static Object copyCurrentNumberOrNull(Object value) {
        if (value instanceof ProtosIntegerValue integer) {
            return integer.isSmallForRuntime()
                    ? new ProtosIntegerValue(integer.smallValueForRuntime())
                    : new ProtosIntegerValue(integer.value());
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

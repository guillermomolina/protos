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

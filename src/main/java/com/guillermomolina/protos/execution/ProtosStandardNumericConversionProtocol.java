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

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFixedIntegerValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.math.BigInteger;
import java.util.Objects;

public final class ProtosStandardNumericConversionProtocol {
    private static final long FRACTION_MASK = 0x000fffffffffffffL;
    private static final long EXPONENT_MASK = 0x7ffL;
    private static final long HIDDEN_BIT = 1L << 52;

    private ProtosStandardNumericConversionProtocol() {}

    public static void install(
            ProtosObjectValue integerPrototype,
            ProtosObjectValue floatPrototype,
            ProtosObjectValue uInt8Prototype,
            ProtosObjectValue int8Prototype,
            ProtosObjectValue uInt16Prototype,
            ProtosObjectValue int16Prototype,
            ProtosObjectValue uInt32Prototype,
            ProtosObjectValue int32Prototype,
            ProtosObjectValue uInt64Prototype,
            ProtosObjectValue int64Prototype) {
        Objects.requireNonNull(integerPrototype, "integerPrototype");
        Objects.requireNonNull(floatPrototype, "floatPrototype");
        Objects.requireNonNull(uInt8Prototype, "uInt8Prototype");
        Objects.requireNonNull(int8Prototype, "int8Prototype");
        Objects.requireNonNull(uInt16Prototype, "uInt16Prototype");
        Objects.requireNonNull(int16Prototype, "int16Prototype");
        Objects.requireNonNull(uInt32Prototype, "uInt32Prototype");
        Objects.requireNonNull(int32Prototype, "int32Prototype");
        Objects.requireNonNull(uInt64Prototype, "uInt64Prototype");
        Objects.requireNonNull(int64Prototype, "int64Prototype");

        installIntegerFactory(integerPrototype);
        installFloatFactory(floatPrototype);
        installFixedFactory(uInt8Prototype, ProtosFixedIntegerValue.Family.UINT8);
        installFixedFactory(int8Prototype, ProtosFixedIntegerValue.Family.INT8);
        installFixedFactory(uInt16Prototype, ProtosFixedIntegerValue.Family.UINT16);
        installFixedFactory(int16Prototype, ProtosFixedIntegerValue.Family.INT16);
        installFixedFactory(uInt32Prototype, ProtosFixedIntegerValue.Family.UINT32);
        installFixedFactory(int32Prototype, ProtosFixedIntegerValue.Family.INT32);
        installFixedFactory(uInt64Prototype, ProtosFixedIntegerValue.Family.UINT64);
        installFixedFactory(int64Prototype, ProtosFixedIntegerValue.Family.INT64);
    }

    private static void installIntegerFactory(ProtosObjectValue integerPrototype) {
        installFactory(
                integerPrototype,
                supplied -> {
                    Object value = supplied.get(0);
                    if (value instanceof ProtosIntegerValue integer) {
                        return integer;
                    }
                    if (value instanceof ProtosFixedIntegerValue fixed) {
                        return new ProtosIntegerValue(fixed.value());
                    }
                    if (value instanceof ProtosFloatValue floating) {
                        BigInteger exact = exactIntegralBinary64(floating.value());
                        if (exact != null) {
                            return new ProtosIntegerValue(exact);
                        }
                    }
                    return null;
                });
    }

    private static void installFloatFactory(ProtosObjectValue floatPrototype) {
        installFactory(
                floatPrototype,
                supplied -> {
                    Object value = supplied.get(0);
                    if (value instanceof ProtosFloatValue floating) {
                        return floating;
                    }
                    if (value instanceof ProtosIntegerValue integer) {
                        return new ProtosFloatValue(
                                ProtosBinary64Rounding.divideExactIntegers(
                                        integer.value(), BigInteger.ONE));
                    }
                    if (value instanceof ProtosFixedIntegerValue fixed) {
                        return new ProtosFloatValue(
                                ProtosBinary64Rounding.divideExactIntegers(
                                        fixed.value(), BigInteger.ONE));
                    }
                    return null;
                });
    }

    private static void installFixedFactory(
            ProtosObjectValue prototype,
            ProtosFixedIntegerValue.Family family) {
        installFactory(
                prototype,
                supplied -> {
                    Object value = supplied.get(0);
                    BigInteger exact = null;
                    if (value instanceof ProtosIntegerValue integer) {
                        exact = integer.value();
                    } else if (value instanceof ProtosFixedIntegerValue fixed) {
                        exact = fixed.value();
                    } else if (value instanceof ProtosFloatValue floating) {
                        exact = exactIntegralBinary64(floating.value());
                    }
                    if (exact == null || !family.contains(exact)) {
                        return null;
                    }
                    if (value instanceof ProtosFixedIntegerValue fixed
                            && fixed.family() == family) {
                        return fixed;
                    }
                    return new ProtosFixedIntegerValue(family, exact);
                });
    }

    private static void installFactory(
            ProtosObjectValue prototype,
            java.util.function.Function<java.util.List<?>, Object> conversion) {
        if (prototype.hasLocalSlot("call")) {
            throw new IllegalStateException(
                    "Core numeric prototype already defines a local call slot");
        }
        prototype.createLocalSlot(
                "call",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            if (activation.receiver() != prototype || supplied.size() != 1) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            Object converted = conversion.apply(supplied);
                            if (converted == null) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            return converted;
                        }));
    }

    static BigInteger exactIntegralBinary64(double value) {
        if (!Double.isFinite(value)) {
            return null;
        }

        long rawBits = Double.doubleToRawLongBits(value);
        boolean negative = rawBits < 0;
        long magnitudeBits = rawBits & Long.MAX_VALUE;
        if (magnitudeBits == 0L) {
            return BigInteger.ZERO;
        }

        long exponentBits = (magnitudeBits >>> 52) & EXPONENT_MASK;
        long fractionBits = magnitudeBits & FRACTION_MASK;

        if (exponentBits == 0L) {
            return null;
        }

        int exponent = (int) exponentBits - 1023;
        BigInteger significand = BigInteger.valueOf(HIDDEN_BIT | fractionBits);
        int binaryShift = exponent - 52;

        BigInteger exactMagnitude;
        if (binaryShift >= 0) {
            exactMagnitude = significand.shiftLeft(binaryShift);
        } else {
            int discardedBits = -binaryShift;
            if (discardedBits > 52
                    || significand.getLowestSetBit() < discardedBits) {
                return null;
            }
            exactMagnitude = significand.shiftRight(discardedBits);
        }

        return negative ? exactMagnitude.negate() : exactMagnitude;
    }
}

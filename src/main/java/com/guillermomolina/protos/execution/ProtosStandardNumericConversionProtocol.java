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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
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
            ProtosObjectValue floatPrototype) {
        Objects.requireNonNull(integerPrototype, "integerPrototype");
        Objects.requireNonNull(floatPrototype, "floatPrototype");

        installIntegerFactory(integerPrototype);
        installFloatFactory(floatPrototype);
    }

    private static void installIntegerFactory(ProtosObjectValue integerPrototype) {
        installFactory(integerPrototype, FactoryKind.INTEGER);
    }

    private static void installFloatFactory(ProtosObjectValue floatPrototype) {
        installFactory(floatPrototype, FactoryKind.FLOAT);
    }

    private enum FactoryKind {
        INTEGER,
        FLOAT
    }

    private static void installFactory(
            ProtosObjectValue prototype,
            FactoryKind factoryKind) {
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
                            Object converted = convert(
                                    factoryKind,
                                    supplied.get(0),
                                    activation.prelude().orElse(null));
                            if (converted == null) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }
                            return converted;
                        }));
    }


    private static Object convert(
            FactoryKind factoryKind, Object value, ProtosPrelude prelude) {
        return switch (factoryKind) {
            case INTEGER -> {
                if (ProtosNumericValueSupport.isCurrentInteger(value)) {
                    yield value;
                }
                if (value instanceof ProtosFloatValue floating) {
                    yield exactIntegralBinary64(floating.value(), prelude);
                }
                yield null;
            }
            case FLOAT -> {
                if (value instanceof ProtosFloatValue floating) {
                    yield floating;
                }
                if (ProtosNumericValueSupport.isCurrentInteger(value)) {
                    yield new ProtosFloatValue(integerToBinary64(value));
                }
                yield null;
            }
        };
    }

    /**
     * D196 operand-first conversion shared by explicit Float(Integer)
     * and standard mixed arithmetic. Small Integers never require
     * BigInteger materialization for this conversion.
     */
    static double integerToBinary64(Object integer) {
        return ProtosBinary64Rounding.roundExactInteger(integer);
    }

    /**
     * The exact Integer denoted by an integral finite binary64 value, or null. Values below
     * 2^63 in magnitude are extracted as signed longs; only larger values need exact
     * arbitrary-precision scaling of the significand.
     */
    static Object exactIntegralBinary64(double value, ProtosPrelude prelude) {
        if (!Double.isFinite(value) || Math.rint(value) != value) {
            return null;
        }
        if (value >= -0x1p63 && value < 0x1p63) {
            return ProtosNumericValueSupport.integer((long) value);
        }
        return ProtosNumericValueSupport.integer(largeIntegralBinary64(value), prelude);
    }

    /**
     * The exact value of a finite binary64 at or beyond 2^63 in magnitude, which is always
     * integral; the result lies outside the signed-64 range.
     */
    @TruffleBoundary
    static BigInteger largeIntegralBinary64(double value) {
        long rawBits = Double.doubleToRawLongBits(value);
        int binaryShift = (int) ((rawBits >>> 52) & EXPONENT_MASK) - 1075;
        BigInteger magnitude =
                BigInteger.valueOf(HIDDEN_BIT | (rawBits & FRACTION_MASK)).shiftLeft(binaryShift);
        return rawBits < 0 ? magnitude.negate() : magnitude;
    }
}

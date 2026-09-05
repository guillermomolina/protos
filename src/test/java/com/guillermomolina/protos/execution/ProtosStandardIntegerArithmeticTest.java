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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosStandardIntegerArithmeticTest {
    @Test
    void additionSubtractionAndMultiplicationAreExactOrdinaryIntegers() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertInteger(prelude, "20 + 22", BigInteger.valueOf(42));
        assertInteger(prelude, "20 - 22", BigInteger.valueOf(-2));
        assertInteger(prelude, "20 * 22", BigInteger.valueOf(440));
    }

    @Test
    void arithmeticDoesNotExposeHostIntegerOverflow() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertInteger(
                prelude,
                "9223372036854775807 + 1",
                new BigInteger("9223372036854775808"));
        assertInteger(
                prelude,
                "999999999999999999999999 * 999999999999999999999999",
                new BigInteger("999999999999999999999998000000000000000000000001"));
    }

    @Test
    void integerDivisionReturnsCorrectlyRoundedBinary64() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertFloatBits(prelude, "1 / 2", 0x3fe0000000000000L);
        assertFloatBits(prelude, "1 / 3", 0x3fd5555555555555L);
        assertFloatBits(prelude, "-1 / 2", 0xbfe0000000000000L);
        assertFloatBits(prelude, "1 / (-2)", 0xbfe0000000000000L);
    }

    @Test
    void integerDivisionRoundsExactRationalRatherThanRoundedOperands() throws IOException {
        ProtosPrelude prelude = corePrelude();
        String huge = BigInteger.ONE.shiftLeft(2000).toString();

        assertFloatBits(prelude, huge + " / " + huge, 0x3ff0000000000000L);
        assertFloatBits(prelude, huge + " / 1", 0x7ff0000000000000L);
    }

    @Test
    void integerDivisionUsesRoundTiesToEven() throws IOException {
        ProtosPrelude prelude = corePrelude();
        BigInteger scale = BigInteger.ONE.shiftLeft(53);

        assertFloatBits(
                prelude,
                scale.add(BigInteger.ONE) + " / " + scale,
                0x3ff0000000000000L);
        assertFloatBits(
                prelude,
                scale.add(BigInteger.valueOf(3)) + " / " + scale,
                0x3ff0000000000002L);
    }

    @Test
    void integerDivisionPreservesSpecifiedSubnormalAndZeroSigns() throws IOException {
        ProtosPrelude prelude = corePrelude();
        String denominator = BigInteger.ONE.shiftLeft(1075).toString();

        assertFloatBits(prelude, "1 / " + denominator, 0x0000000000000000L);
        assertFloatBits(prelude, "-1 / " + denominator, 0x8000000000000000L);
        assertFloatBits(prelude, "3 / " + denominator, 0x0000000000000002L);
        assertFloatBits(prelude, "0 / (-3)", 0x0000000000000000L);
    }

    @Test
    void integerDivisionRejectsZeroAndDifferentNumericFamily() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1 / 0"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1 / 1.0"));
    }

    @Test
    void quotientAndRemainderUseTruncationTowardZero() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertInteger(prelude, "7.div(3)", BigInteger.valueOf(2));
        assertInteger(prelude, "(-7).div(3)", BigInteger.valueOf(-2));
        assertInteger(prelude, "7.div(-3)", BigInteger.valueOf(-2));
        assertInteger(prelude, "(-7).div(-3)", BigInteger.valueOf(2));

        assertInteger(prelude, "7.mod(3)", BigInteger.ONE);
        assertInteger(prelude, "(-7).mod(3)", BigInteger.valueOf(-1));
        assertInteger(prelude, "7.mod(-3)", BigInteger.ONE);
        assertInteger(prelude, "(-7).mod(-3)", BigInteger.valueOf(-1));

        assertInteger(prelude, "7 % 3", BigInteger.ONE);
        assertInteger(prelude, "-7 % 3", BigInteger.valueOf(-1));
    }

    @Test
    void quotientAndRemainderRemainArbitraryPrecision() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertInteger(
                prelude,
                "999999999999999999999999.div(3)",
                new BigInteger("333333333333333333333333"));
        assertInteger(
                prelude,
                "999999999999999999999999.mod(10)",
                BigInteger.valueOf(9));
    }

    @Test
    void quotientAndRemainderRejectZeroDivisor() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1.div(0)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1.mod(0)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1 % 0"));
    }

    @Test
    void quotientAndRemainderRejectDifferentNumericFamily() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1.div(1.0)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1.mod(1.0)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1 % 1.0"));
    }

    @Test
    void unaryMinusUsesStandardNegatedBehavior() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertInteger(prelude, "-123", BigInteger.valueOf(-123));
        assertInteger(prelude, "-(-123)", BigInteger.valueOf(123));
    }

    @Test
    void negatedIsSourceBackedAndPreservesIntegerReceiverDomain() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosClosureValue negated =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        prelude.integerPrototype().readLocalSlot("negated").orElseThrow());
        assertNotNull(negated.definition());
        assertTrue(negated.executionPlan().isPresent());
        assertTrue(negated.nativeBody().isEmpty());

        ProtosClosureValue subtraction =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        prelude.integerPrototype().readLocalSlot("-").orElseThrow());
        assertTrue(subtraction.nativeBody().isPresent());

        var activation = prelude.newModuleActivation();
        var incompatible =
                new com.guillermomolina.protos.runtime.ProtosObjectValue(
                        prelude.integerPrototype());
        activation.context().createLocalSlot("incompatibleIntegerChild", incompatible);
        assertThrows(
                ProtosSignalException.class,
                () ->
                        new ProtosSourceCompiler()
                                .compile("incompatibleIntegerChild.negated()")
                                .call(activation));
    }

    @Test
    void ordinaryIntegerArithmeticRejectsDifferentNumericFamily() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1 + 1.0"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1 - 1.0"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1 * 1.0"));
    }

    @Test
    void copiedStandardIntegerOperationRejectsOrdinaryObjectReceiver() throws IOException {
        ProtosPrelude prelude = corePrelude();
        Object plus = prelude.integerPrototype().readLocalSlot("+").orElseThrow();
        var activation = prelude.newModuleActivation();
        var object = new com.guillermomolina.protos.runtime.ProtosObjectValue(
                com.guillermomolina.protos.runtime.ProtosObjectValue.rootObject());
        object.createLocalSlot("+", plus);
        activation.context().createLocalSlot("o", object);

        assertThrows(
                ProtosSignalException.class,
                () -> new ProtosSourceCompiler().compile("o + 1").call(activation));
    }

    private static void assertFloatBits(ProtosPrelude prelude, String source, long expectedBits) {
        Object result = execute(prelude, source);
        assertEquals(
                expectedBits,
                Double.doubleToRawLongBits(((ProtosFloatValue) result).value()));
    }

    private static void assertInteger(ProtosPrelude prelude, String source, BigInteger expected) {
        Object result = execute(prelude, source);
        assertEquals(expected, ((ProtosIntegerValue) result).value());
    }

    private static Object execute(ProtosPrelude prelude, String source) {
        return new ProtosSourceCompiler()
                .compile(source)
                .call(prelude.newModuleActivation());
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }
}

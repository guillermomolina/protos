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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosStandardNumericConversionTest {
    @Test
    void integerFactoryAcceptsExactIntegralNumbersOnly() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertInteger(prelude, "Integer(42)", BigInteger.valueOf(42));
        assertInteger(prelude, "Integer(-0.0)", BigInteger.ZERO);
        assertInteger(
                prelude,
                "Integer(1e23)",
                new BigInteger("99999999999999991611392"));

        assertThrows(ProtosSignalException.class, () -> execute(prelude, "Integer(1.5)"));
        assertThrows(
                ProtosSignalException.class,
                () -> execute(prelude, "Integer(1.0 / 0.0)"));
        assertThrows(
                ProtosSignalException.class,
                () -> execute(prelude, "Integer(0.0 / 0.0)"));
    }

    @Test
    void integerFactoryDoesNotRoundOrTruncate() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertThrows(
                ProtosSignalException.class,
                () -> execute(prelude, "Integer(9007199254740991.5)"));
    }

    @Test
    void floatFactoryRoundsExactIntegerOnceToBinary64() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertFloatBits(prelude, "Float(1)", 0x3ff0000000000000L);
        assertFloatBits(prelude, "Float(0)", 0x0000000000000000L);
        assertFloatBits(
                prelude,
                "Float(9007199254740993)",
                0x4340000000000000L);

        String huge = BigInteger.ONE.shiftLeft(2000).toString();
        assertFloatBits(prelude, "Float(" + huge + ")", 0x7ff0000000000000L);
    }

    @Test
    void floatFactoryPreservesExistingFloatSemanticValue() throws IOException {
        ProtosPrelude prelude = corePrelude();

        Object negativeZero = execute(prelude, "-0.0");
        Object result =
                ProtosInvocation.invoke(
                        prelude.floatPrototype(),
                        List.of(negativeZero),
                        prelude.newModuleActivation());

        assertSame(negativeZero, result);
        assertEquals(
                0x8000000000000000L,
                Double.doubleToRawLongBits(((ProtosFloatValue) result).value()));

        Object nan = execute(prelude, "0.0 / 0.0");
        Object nanResult =
                ProtosInvocation.invoke(
                        prelude.floatPrototype(),
                        List.of(nan),
                        prelude.newModuleActivation());
        assertSame(nan, nanResult);
        assertTrue(Double.isNaN(((ProtosFloatValue) nanResult).value()));
    }

    @Test
    void conversionFactoriesRejectWrongArityAndNonNumber() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertThrows(ProtosSignalException.class, () -> execute(prelude, "Integer()"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "Integer(1, 2)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "Integer({})"));

        assertThrows(ProtosSignalException.class, () -> execute(prelude, "Float()"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "Float(1, 2)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "Float({})"));
    }

    @Test
    void inheritedOrCopiedFactoryDoesNotReclassifyOrdinaryReceiver() throws IOException {
        ProtosPrelude prelude = corePrelude();
        Object integerCall = prelude.integerPrototype().readLocalSlot("call").orElseThrow();

        ProtosObjectValue ordinary =
                new ProtosObjectValue(prelude.integerPrototype());
        ordinary.createLocalSlot("copiedCall", integerCall);

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invoke(
                                ordinary,
                                List.of(new ProtosIntegerValue(BigInteger.ONE)),
                                prelude.newModuleActivation()));

        ProtosObjectValue copied =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        copied.createLocalSlot("call", integerCall);
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invoke(
                                copied,
                                List.of(new ProtosIntegerValue(BigInteger.ONE)),
                                prelude.newModuleActivation()));
    }

    @Test
    void exactIntegralBinary64ExtractionUsesActualBinaryValue() {
        assertEquals(
                new BigInteger("99999999999999991611392"),
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(1e23));
        assertEquals(
                BigInteger.ZERO,
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(-0.0d));
        assertEquals(
                BigInteger.ONE,
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(1.0d));
        assertEquals(
                null,
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(1.5d));
        assertEquals(
                null,
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(
                        Double.POSITIVE_INFINITY));
        assertEquals(
                null,
                ProtosStandardNumericConversionProtocol.exactIntegralBinary64(Double.NaN));
    }

    private static void assertInteger(ProtosPrelude prelude, String source, BigInteger expected) {
        Object result = execute(prelude, source);
        assertEquals(expected, ((ProtosIntegerValue) result).value());
    }

    private static void assertFloatBits(ProtosPrelude prelude, String source, long expectedBits) {
        Object result = execute(prelude, source);
        assertEquals(
                expectedBits,
                Double.doubleToRawLongBits(((ProtosFloatValue) result).value()));
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

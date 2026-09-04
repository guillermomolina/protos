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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosFixedIntegerValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosStandardFixedWidthConversionTest {
    @Test
    void allFactoriesProduceTheirExactSemanticFamily() throws IOException {
        ProtosPrelude prelude = corePrelude();
        assertFixed(prelude, "UInt8(255)", ProtosFixedIntegerValue.Family.UINT8, "255");
        assertFixed(prelude, "Int8(-128)", ProtosFixedIntegerValue.Family.INT8, "-128");
        assertFixed(prelude, "UInt16(65535)", ProtosFixedIntegerValue.Family.UINT16, "65535");
        assertFixed(prelude, "Int16(-32768)", ProtosFixedIntegerValue.Family.INT16, "-32768");
        assertFixed(prelude, "UInt32(4294967295)", ProtosFixedIntegerValue.Family.UINT32, "4294967295");
        assertFixed(prelude, "Int32(-2147483648)", ProtosFixedIntegerValue.Family.INT32, "-2147483648");
        assertFixed(prelude, "UInt64(18446744073709551615)", ProtosFixedIntegerValue.Family.UINT64, "18446744073709551615");
        assertFixed(prelude, "Int64(-9223372036854775808)", ProtosFixedIntegerValue.Family.INT64, "-9223372036854775808");
    }

    @Test
    void factoriesRejectOutOfRangeExactIntegers() throws IOException {
        ProtosPrelude prelude = corePrelude();
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "UInt8(-1)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "UInt8(256)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "Int8(-129)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "Int8(128)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "UInt64(18446744073709551616)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "Int64(9223372036854775808)"));
    }

    @Test
    void factoriesAcceptIntegralFloatWithinRangeOnly() throws IOException {
        ProtosPrelude prelude = corePrelude();
        assertFixed(prelude, "UInt8(255.0)", ProtosFixedIntegerValue.Family.UINT8, "255");
        assertFixed(prelude, "Int8(-128.0)", ProtosFixedIntegerValue.Family.INT8, "-128");
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "UInt8(255.5)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "Int8(128.0)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "UInt8(0.0 / 0.0)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "Int8(1.0 / 0.0)"));
    }

    @Test
    void integerAndFloatFactoriesAcceptFixedWidthInputs() throws IOException {
        ProtosPrelude prelude = corePrelude();
        assertEquals(new BigInteger("255"),
                ((com.guillermomolina.protos.runtime.ProtosIntegerValue)
                        execute(prelude, "Integer(UInt8(255))")).value());
        assertEquals(0x406fe00000000000L,
                Double.doubleToRawLongBits(
                        ((com.guillermomolina.protos.runtime.ProtosFloatValue)
                                execute(prelude, "Float(UInt8(255))")).value()));
    }

    @Test
    void crossFamilyConversionChecksTargetRangeExactly() throws IOException {
        ProtosPrelude prelude = corePrelude();
        assertFixed(prelude, "Int16(UInt8(255))", ProtosFixedIntegerValue.Family.INT16, "255");
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "Int8(UInt8(255))"));
        assertFixed(prelude, "UInt16(Int8(-0))", ProtosFixedIntegerValue.Family.UINT16, "0");
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "UInt16(Int8(-1))"));
    }

    private static void assertFixed(
            ProtosPrelude prelude, String source, ProtosFixedIntegerValue.Family family, String expected) {
        ProtosFixedIntegerValue value = (ProtosFixedIntegerValue) execute(prelude, source);
        assertEquals(family, value.family());
        assertEquals(new BigInteger(expected), value.value());
    }

    private static Object execute(ProtosPrelude prelude, String source) {
        return new ProtosSourceCompiler().compile(source).call(prelude.newModuleActivation());
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }
}

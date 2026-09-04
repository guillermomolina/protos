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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosStandardNumberEqualityTest {
    @Test
    void equalityComparesMathematicalValueAcrossNumericFamilies() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertBoolean(prelude, "1 == 1.0", true);
        assertBoolean(prelude, "UInt8(1) == 1", true);
        assertBoolean(prelude, "Int32(1) == UInt32(1)", true);
        assertBoolean(prelude, "Int8(-1) == -1", true);
        assertBoolean(prelude, "UInt64(18446744073709551615) == 18446744073709551615", true);
    }

    @Test
    void equalityUsesExactFloatValueWithoutIntegerRounding() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertBoolean(prelude, "9007199254740992 == 9007199254740992.0", true);
        assertBoolean(prelude, "9007199254740993 == 9007199254740993.0", false);
        assertBoolean(
                prelude,
                "99999999999999991611392 == 1e23",
                true);
        assertBoolean(prelude, "1 == 1.5", false);
    }

    @Test
    void floatNanAndSignedZeroFollowNormativeEquality() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertBoolean(prelude, "(0.0 / 0.0) == (0.0 / 0.0)", false);
        assertBoolean(prelude, "(0.0 / 0.0) == 0", false);
        assertBoolean(prelude, "0.0 == -0.0", true);
        assertBoolean(prelude, "0 == -0.0", true);
        assertBoolean(prelude, "(1.0 / 0.0) == (1.0 / 0.0)", true);
        assertBoolean(prelude, "(1.0 / 0.0) == (-1.0 / 0.0)", false);
    }

    @Test
    void numericEqualityWithNonNumberReturnsFalse() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertBoolean(prelude, "1 == {}", false);
        assertBoolean(prelude, "1.0 == {}", false);
        assertBoolean(prelude, "UInt8(1) == {}", false);
        assertBoolean(prelude, "1 == null", false);
        assertBoolean(prelude, "1 == true", false);
    }

    @Test
    void standardEqualityRejectsNonNumericOriginalReceiver() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosObjectValue ordinary = new ProtosObjectValue(prelude.integerPrototype());

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                ordinary,
                                "==",
                                java.util.List.of(
                                        new com.guillermomolina.protos.runtime.ProtosIntegerValue(
                                                java.math.BigInteger.ONE)),
                                prelude.newModuleActivation()));
    }

    @Test
    void wrongAritySignalsError() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1.==(1, 2)"));
    }

    private static void assertBoolean(ProtosPrelude prelude, String source, boolean expected) {
        Object result = execute(prelude, source);
        assertSame(
                expected ? ProtosBooleanValue.TRUE : ProtosBooleanValue.FALSE,
                result);
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

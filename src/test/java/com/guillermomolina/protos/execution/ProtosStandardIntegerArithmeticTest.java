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
    void unaryMinusUsesStandardNegatedBehavior() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertInteger(prelude, "-123", BigInteger.valueOf(-123));
        assertInteger(prelude, "-(-123)", BigInteger.valueOf(123));
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

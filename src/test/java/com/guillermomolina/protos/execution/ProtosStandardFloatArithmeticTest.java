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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosStandardFloatArithmeticTest {
    @Test
    void basicArithmeticUsesBinary64Results() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertFloatBits(prelude, "1.5 + 2.25", 0x400e000000000000L);
        assertFloatBits(prelude, "1.5 - 2.25", 0xbfe8000000000000L);
        assertFloatBits(prelude, "1.5 * 2.0", 0x4008000000000000L);
        assertFloatBits(prelude, "1.0 / 2.0", 0x3fe0000000000000L);
    }

    @Test
    void unaryNegationPreservesBinary64SignSemantics() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertFloatBits(prelude, "-0.0", 0x8000000000000000L);
        assertFloatBits(prelude, "-(-0.0)", 0x0000000000000000L);
        assertFloatBits(prelude, "-1.5", 0xbff8000000000000L);
    }

    @Test
    void divisionByZeroProducesIeeeInfinityOrNan() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertFloatBits(prelude, "1.0 / 0.0", 0x7ff0000000000000L);
        assertFloatBits(prelude, "1.0 / -0.0", 0xfff0000000000000L);
        assertNaN(prelude, "0.0 / 0.0");
    }

    @Test
    void overflowAndUnderflowRemainBinary64Results() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertFloatBits(prelude, "1e308 * 1e308", 0x7ff0000000000000L);
        assertFloatBits(prelude, "5e-324 / 2.0", 0x0000000000000000L);
        assertFloatBits(prelude, "-5e-324 / 2.0", 0x8000000000000000L);
    }

    @Test
    void invalidIeeeOperationsProduceNanInsteadOfProtosError() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertNaN(prelude, "(1.0 / 0.0) - (1.0 / 0.0)");
        assertNaN(prelude, "0.0 * (1.0 / 0.0)");
    }

    @Test
    void floatArithmeticRejectsDistinctNumericFamily() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1.0 + 1"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1.0 - 1"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1.0 * 1"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1.0 / 1"));
    }

    @Test
    void copiedStandardFloatOperationRejectsOrdinaryObjectReceiver() throws IOException {
        ProtosPrelude prelude = corePrelude();
        Object plus = prelude.floatPrototype().readLocalSlot("+").orElseThrow();
        var activation = prelude.newModuleActivation();
        var object = new com.guillermomolina.protos.runtime.ProtosObjectValue(
                com.guillermomolina.protos.runtime.ProtosObjectValue.rootObject());
        object.createLocalSlot("+", plus);
        activation.context().createLocalSlot("o", object);

        assertThrows(
                ProtosSignalException.class,
                () -> new ProtosSourceCompiler().compile("o + 1.0").call(activation));
    }

    private static void assertFloatBits(ProtosPrelude prelude, String source, long expectedBits) {
        Object result = execute(prelude, source);
        assertEquals(
                expectedBits,
                Double.doubleToRawLongBits(((ProtosFloatValue) result).value()));
    }

    private static void assertNaN(ProtosPrelude prelude, String source) {
        Object result = execute(prelude, source);
        assertTrue(Double.isNaN(((ProtosFloatValue) result).value()));
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

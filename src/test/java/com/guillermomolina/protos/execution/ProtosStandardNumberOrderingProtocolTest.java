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
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosStandardNumberOrderingProtocolTest {
    @Test
    void ordinaryIntegersSupportAllFourOrderingSelectors() throws IOException {
        ProtosPrelude prelude = corePrelude();
        assertBoolean(prelude, "1 < 2", true);
        assertBoolean(prelude, "2 < 1", false);
        assertBoolean(prelude, "2 <= 2", true);
        assertBoolean(prelude, "3 > 2", true);
        assertBoolean(prelude, "3 >= 3", true);
    }

    @Test
    void orderingIsExactAndCrossFamilyWithoutNumericPromotion() throws IOException {
        ProtosPrelude prelude = corePrelude();
        assertBoolean(prelude, "1 < 1.5", true);
        assertBoolean(prelude, "1.5 > 1", true);
        assertBoolean(prelude, "UInt8(1) <= Int16(1)", true);
        assertBoolean(prelude, "Int16(-1) < UInt8(1)", true);
        assertBoolean(prelude, "9007199254740993 > 9007199254740992.0", true);
        assertBoolean(prelude, "9007199254740992.0 < 9007199254740993", true);
    }

    @Test
    void orderingHandlesSignedZeroInfinityAndNaNNormatively() throws IOException {
        ProtosPrelude prelude = corePrelude();
        assertBoolean(prelude, "(-0.0) < 0", false);
        assertBoolean(prelude, "(-0.0) <= 0", true);
        assertBoolean(prelude, "0 >= (-0.0)", true);
        assertBoolean(prelude, "(1.0 / 0.0) > 999999999999999999999999999999", true);
        assertBoolean(prelude, "(-1.0 / 0.0) < -999999999999999999999999999999", true);
        assertBoolean(prelude, "(0.0 / 0.0) < 1", false);
        assertBoolean(prelude, "(0.0 / 0.0) <= 1", false);
        assertBoolean(prelude, "1 > (0.0 / 0.0)", false);
        assertBoolean(prelude, "1 >= (0.0 / 0.0)", false);
    }

    @Test
    void standardOrderingRejectsNonNumberArgumentsAndReceivers() throws IOException {
        ProtosPrelude prelude = corePrelude();
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "1 < \"1\""));

        Object less = prelude.numberPrototype().readLocalSlot("<").orElseThrow();
        ProtosObjectValue ordinary = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ordinary.createLocalSlot("copiedLess", less);
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invokeMessage(
                        ordinary,
                        "copiedLess",
                        List.of(new ProtosIntegerValue(BigInteger.ONE)),
                        prelude.newModuleActivation()));
    }

    @Test
    void numberPrototypeOwnsOrderingAsOrdinaryClosureValuedProtocol() throws IOException {
        ProtosPrelude prelude = corePrelude();
        for (String selector : List.of("<", "<=", ">", ">=")) {
            Object behavior = prelude.numberPrototype().readLocalSlot(selector).orElseThrow();
            assertSame(ProtosClosureValue.class, behavior.getClass());
        }
    }

    private static void assertBoolean(ProtosPrelude prelude, String source, boolean expected) {
        assertSame(expected ? ProtosBooleanValue.TRUE : ProtosBooleanValue.FALSE, execute(prelude, source), source);
    }

    private static Object execute(ProtosPrelude prelude, String source) {
        return new ProtosSourceCompiler().compile(source).call(prelude.newModuleActivation());
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }
}

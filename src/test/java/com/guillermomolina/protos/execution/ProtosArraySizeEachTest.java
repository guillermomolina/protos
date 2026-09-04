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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosArraySizeEachTest {
    @Test
    void sizeReturnsSemanticIntegerForOpenClosedAndFrozenArrays() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosArrayValue array = prelude.newArray(java.util.List.of(
                new ProtosIntegerValue(BigInteger.ONE),
                new ProtosIntegerValue(BigInteger.TWO)));
        activation.context().createLocalSlot("xs", array);

        assertEquals(BigInteger.TWO, ((ProtosIntegerValue) execute(activation, "xs.size()")).value());
        array.close();
        assertEquals(BigInteger.TWO, ((ProtosIntegerValue) execute(activation, "xs.size()")).value());
        array.freeze();
        assertEquals(BigInteger.TWO, ((ProtosIntegerValue) execute(activation, "xs.size()")).value());
    }

    @Test
    void eachVisitsSnapshotInOrderAndReturnsOriginalReceiver() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        Object result = execute(
                activation,
                """
                xs: Array(10, 20)
                seen: Array(0, 0)
                i: 0
                callback: (value) => {
                    seen[i] = value
                    i = i + 1
                    value
                }
                xs.each(callback)
                """);

        ProtosArrayValue xs = (ProtosArrayValue) activation.context().readLocalSlot("xs").orElseThrow();
        ProtosArrayValue seen = (ProtosArrayValue) activation.context().readLocalSlot("seen").orElseThrow();
        assertSame(xs, result);
        assertEquals(BigInteger.TEN, ((ProtosIntegerValue) seen.indexedAt(BigInteger.ZERO)).value());
        assertEquals(BigInteger.valueOf(20), ((ProtosIntegerValue) seen.indexedAt(BigInteger.ONE)).value());
    }

    @Test
    void eachUsesShallowSnapshotWhenCallbackReplacesLaterElement() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        execute(
                activation,
                """
                xs: Array(1, 2)
                seen: Array(0, 0)
                i: 0
                callback: (value) => {
                    seen[i] = value
                    i = i + 1
                    xs[1] = 99
                    value
                }
                xs.each(callback)
                """);

        ProtosArrayValue seen = (ProtosArrayValue) activation.context().readLocalSlot("seen").orElseThrow();
        assertEquals(BigInteger.ONE, ((ProtosIntegerValue) seen.indexedAt(BigInteger.ZERO)).value());
        assertEquals(BigInteger.TWO, ((ProtosIntegerValue) seen.indexedAt(BigInteger.ONE)).value());
    }

    @Test
    void eachRejectsWrongArityAndNonInvokableBeforeIteration() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        assertThrows(ProtosSignalException.class, () -> execute(activation, "Array(1).each()"));
        assertThrows(ProtosSignalException.class, () -> execute(activation, "Array(1).each(null)"));
    }

    private static Object execute(ProtosActivation activation, String source) {
        return new ProtosSourceCompiler().compile(source).call(activation);
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }
}

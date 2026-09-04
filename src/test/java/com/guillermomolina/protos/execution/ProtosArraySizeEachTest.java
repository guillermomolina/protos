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
import com.guillermomolina.protos.runtime.ProtosClosureValue;
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
        ProtosArrayValue xs =
                prelude.newArray(
                        java.util.List.of(
                                new ProtosIntegerValue(BigInteger.TEN),
                                new ProtosIntegerValue(BigInteger.valueOf(20))));
        java.util.List<Object> seen = new java.util.ArrayList<>();
        ProtosClosureValue callback =
                ProtosClosureValue.nativeClosure(
                        (callbackActivation, supplied) -> {
                            seen.add(supplied.get(0));
                            return supplied.get(0);
                        });
        activation.context().createLocalSlot("xs", xs);
        activation.context().createLocalSlot("callback", callback);

        Object result = execute(activation, "xs.each(callback)");

        assertSame(xs, result);
        assertEquals(BigInteger.TEN, ((ProtosIntegerValue) seen.get(0)).value());
        assertEquals(BigInteger.valueOf(20), ((ProtosIntegerValue) seen.get(1)).value());
    }

    @Test
    void eachUsesShallowSnapshotWhenCallbackReplacesLaterElement() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosArrayValue xs =
                prelude.newArray(
                        java.util.List.of(
                                new ProtosIntegerValue(BigInteger.ONE),
                                new ProtosIntegerValue(BigInteger.TWO)));
        java.util.List<Object> seen = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger invocation =
                new java.util.concurrent.atomic.AtomicInteger();
        ProtosClosureValue callback =
                ProtosClosureValue.nativeClosure(
                        (callbackActivation, supplied) -> {
                            seen.add(supplied.get(0));
                            if (invocation.getAndIncrement() == 0) {
                                xs.indexedPut(
                                        BigInteger.ONE,
                                        new ProtosIntegerValue(BigInteger.valueOf(99)));
                            }
                            return supplied.get(0);
                        });
        activation.context().createLocalSlot("xs", xs);
        activation.context().createLocalSlot("callback", callback);

        execute(activation, "xs.each(callback)");

        assertEquals(BigInteger.ONE, ((ProtosIntegerValue) seen.get(0)).value());
        assertEquals(BigInteger.TWO, ((ProtosIntegerValue) seen.get(1)).value());
        assertEquals(
                BigInteger.valueOf(99),
                ((ProtosIntegerValue) xs.indexedAt(BigInteger.ONE)).value());
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

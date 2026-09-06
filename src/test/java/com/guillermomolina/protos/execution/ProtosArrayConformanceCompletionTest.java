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
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Java-side completion coverage that still requires host-established lifecycle
 * state or host observation of callback control transfer. Factory/indexing,
 * ordinary identity, and receiver-domain behavior are covered in .protos.
 */
class ProtosArrayConformanceCompletionTest {
    @Test
    void closedArrayAllowsReplacementButFrozenArrayRejectsWithoutMutation()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosArrayValue xs =
                prelude.newArray(List.of(new ProtosIntegerValue(BigInteger.ONE)));
        activation.context().createLocalSlot("xs", xs);

        xs.close();
        assertEquals(
                BigInteger.TWO,
                ((ProtosIntegerValue) execute(activation, "xs[0] = 2")).value());
        assertEquals(
                BigInteger.TWO,
                ((ProtosIntegerValue) xs.indexedAt(BigInteger.ZERO)).value());

        xs.freeze();
        assertThrows(
                ProtosSignalException.class,
                () -> execute(activation, "xs[0] = 3"));
        assertEquals(
                BigInteger.TWO,
                ((ProtosIntegerValue) xs.indexedAt(BigInteger.ZERO)).value());
    }

    @Test
    void sizeIsSemanticIntegerAcrossStatesAndRejectsWrongArity()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosArrayValue xs =
                prelude.newArray(
                        List.of(
                                new ProtosIntegerValue(BigInteger.ONE),
                                new ProtosIntegerValue(BigInteger.TWO)));
        activation.context().createLocalSlot("xs", xs);

        assertEquals(
                BigInteger.TWO,
                ((ProtosIntegerValue) execute(activation, "xs.size()")).value());
        xs.close();
        assertEquals(
                BigInteger.TWO,
                ((ProtosIntegerValue) execute(activation, "xs.size()")).value());
        xs.freeze();
        assertEquals(
                BigInteger.TWO,
                ((ProtosIntegerValue) execute(activation, "xs.size()")).value());
        assertThrows(
                ProtosSignalException.class,
                () -> execute(activation, "xs.size(1)"));
    }

    @Test
    void eachAcceptsOrdinaryInvokableObjectUsesSnapshotOrderAndReturnsReceiver()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosArrayValue xs =
                prelude.newArray(
                        List.of(
                                new ProtosIntegerValue(BigInteger.ONE),
                                new ProtosIntegerValue(BigInteger.TWO)));
        List<Object> seen = new ArrayList<>();
        ProtosObjectValue callback =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        callback.createLocalSlot(
                "call",
                ProtosClosureValue.nativeClosure(
                        (callbackActivation, supplied) -> {
                            seen.add(supplied.get(0));
                            if (seen.size() == 1) {
                                xs.indexedPut(
                                        BigInteger.ONE,
                                        new ProtosIntegerValue(BigInteger.valueOf(99)));
                            }
                            return supplied.get(0);
                        }));
        activation.context().createLocalSlot("xs", xs);
        activation.context().createLocalSlot("callback", callback);

        Object result = execute(activation, "xs.each(callback)");

        assertSame(xs, result);
        assertEquals(2, seen.size());
        assertEquals(BigInteger.ONE, ((ProtosIntegerValue) seen.get(0)).value());
        assertEquals(
                BigInteger.TWO,
                ((ProtosIntegerValue) seen.get(1)).value());
        assertEquals(
                BigInteger.valueOf(99),
                ((ProtosIntegerValue) xs.indexedAt(BigInteger.ONE)).value());
    }

    @Test
    void eachPropagatesCallbackFailureAndStopsAtFailingElement()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosArrayValue xs =
                prelude.newArray(
                        List.of(
                                new ProtosIntegerValue(BigInteger.ONE),
                                new ProtosIntegerValue(BigInteger.TWO)));
        List<Object> seen = new ArrayList<>();
        ProtosObjectValue error = ProtosCoreErrors.newError(activation);
        ProtosClosureValue callback =
                ProtosClosureValue.nativeClosure(
                        (callbackActivation, supplied) -> {
                            seen.add(supplied.get(0));
                            throw new ProtosSignalException(error);
                        });
        activation.context().createLocalSlot("xs", xs);
        activation.context().createLocalSlot("callback", callback);

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute(activation, "xs.each(callback)"));

        assertSame(error, signal.error());
        assertEquals(1, seen.size());
        assertEquals(
                BigInteger.ONE,
                ((ProtosIntegerValue) seen.get(0)).value());
    }

    private static Object execute(ProtosActivation activation, String source) {
        return new ProtosSourceCompiler().compile(source).call(activation);
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }
}

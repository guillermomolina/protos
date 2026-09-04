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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
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

/** Normative completion coverage for the complete standard Array surface. */
class ProtosArrayConformanceCompletionTest {
    @Test
    void emptyFactoryIsFreshOpenDenseArrayAndSingleIntegerIsAnElement() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosArrayValue first = (ProtosArrayValue) execute(activation, "Array()");
        ProtosArrayValue second = (ProtosArrayValue) execute(activation, "Array()");
        ProtosArrayValue singleton = (ProtosArrayValue) execute(activation, "Array(3)");

        assertNotSame(first, second);
        assertFalse(first.isClosed());
        assertFalse(first.isFrozen());
        assertSame(prelude.arrayPrototype(), first.parent().orElseThrow());
        assertEquals(BigInteger.ZERO, first.indexedSize());
        assertEquals(BigInteger.ONE, singleton.indexedSize());
        assertEquals(
                BigInteger.valueOf(3),
                ((ProtosIntegerValue) singleton.indexedAt(BigInteger.ZERO)).value());
    }

    @Test
    void indexedReadWriteCoversBothBoundariesExactRhsAndDenseBounds() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        Object marker = new ProtosObjectValue(ProtosObjectValue.rootObject());
        activation.context().createLocalSlot("marker", marker);

        ProtosArrayValue xs = (ProtosArrayValue) execute(activation, "Array(10, 20)");
        activation.context().createLocalSlot("xs", xs);

        assertEquals(
                BigInteger.TEN,
                ((ProtosIntegerValue) execute(activation, "xs[0]")).value());
        assertEquals(
                BigInteger.valueOf(20),
                ((ProtosIntegerValue) execute(activation, "xs[1]")).value());

        Object result = execute(activation, "xs[1] = marker");
        assertSame(marker, result);
        assertSame(marker, xs.indexedAt(BigInteger.ONE));
        assertEquals(BigInteger.valueOf(2), xs.indexedSize());

        assertThrows(ProtosSignalException.class, () -> execute(activation, "xs[-1]"));
        assertThrows(ProtosSignalException.class, () -> execute(activation, "xs[2]"));
        assertThrows(ProtosSignalException.class, () -> execute(activation, "xs[1.0]"));
        assertThrows(ProtosSignalException.class, () -> execute(activation, "xs[-1] = marker"));
        assertThrows(ProtosSignalException.class, () -> execute(activation, "xs[2] = marker"));
        assertThrows(ProtosSignalException.class, () -> execute(activation, "xs[1.0] = marker"));
        assertEquals(BigInteger.valueOf(2), xs.indexedSize());
    }

    @Test
    void closedArrayAllowsReplacementButFrozenArrayRejectsWithoutMutation() throws IOException {
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
        assertThrows(ProtosSignalException.class, () -> execute(activation, "xs[0] = 3"));
        assertEquals(
                BigInteger.TWO,
                ((ProtosIntegerValue) xs.indexedAt(BigInteger.ZERO)).value());
    }

    @Test
    void sizeIsSemanticIntegerAcrossStatesAndRejectsWrongArity() throws IOException {
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
        assertThrows(ProtosSignalException.class, () -> execute(activation, "xs.size(1)"));
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
        ProtosObjectValue callback = new ProtosObjectValue(ProtosObjectValue.rootObject());
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
        assertEquals(BigInteger.TWO, ((ProtosIntegerValue) seen.get(1)).value());
        assertEquals(
                BigInteger.valueOf(99),
                ((ProtosIntegerValue) xs.indexedAt(BigInteger.ONE)).value());
    }

    @Test
    void eachPropagatesCallbackFailureAndStopsAtFailingElement() throws IOException {
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
        assertEquals(BigInteger.ONE, ((ProtosIntegerValue) seen.get(0)).value());
    }

    @Test
    void arraysUseOrdinaryIdentityAndDefaultEquality() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosArrayValue a =
                prelude.newArray(List.of(new ProtosIntegerValue(BigInteger.ONE)));
        ProtosArrayValue b =
                prelude.newArray(List.of(new ProtosIntegerValue(BigInteger.ONE)));
        activation.context().createLocalSlot("a", a);
        activation.context().createLocalSlot("b", b);

        assertSame(ProtosBooleanValue.TRUE, execute(activation, "a == a"));
        assertSame(ProtosBooleanValue.TRUE, execute(activation, "a === a"));
        assertSame(ProtosBooleanValue.FALSE, execute(activation, "a == b"));
        assertSame(ProtosBooleanValue.FALSE, execute(activation, "a === b"));
    }

    @Test
    void delegatingToArrayDoesNotConferSemanticArrayMembership() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue fake = new ProtosObjectValue(prelude.arrayPrototype());

        ProtosSignalException at =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosInvocation.invokeMessage(
                                        fake,
                                        "at",
                                        List.of(new ProtosIntegerValue(BigInteger.ZERO)),
                                        activation));
        ProtosSignalException atPut =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosInvocation.invokeMessage(
                                        fake,
                                        "atPut",
                                        List.of(
                                                new ProtosIntegerValue(BigInteger.ZERO),
                                                ProtosBooleanValue.TRUE),
                                        activation));
        ProtosSignalException size =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosInvocation.invokeMessage(
                                        fake, "size", List.of(), activation));
        ProtosSignalException each =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosInvocation.invokeMessage(
                                        fake,
                                        "each",
                                        List.of(
                                                ProtosClosureValue.nativeClosure(
                                                        (callbackActivation, supplied) ->
                                                                ProtosBooleanValue.TRUE)),
                                        activation));

        assertSame(prelude.errorPrototype(), at.error().parent().orElseThrow());
        assertSame(prelude.errorPrototype(), atPut.error().parent().orElseThrow());
        assertSame(prelude.errorPrototype(), size.error().parent().orElseThrow());
        assertSame(prelude.errorPrototype(), each.error().parent().orElseThrow());
    }

    @Test
    void inheritedFactoryCreatesRealArrayButDoesNotGivePrototypeIndexedState() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosArrayValue result =
                (ProtosArrayValue)
                        execute(
                                activation,
                                """
                                MyArray: Array { label: 9 }
                                MyArray(10, 20)
                                """);
        Object myArray = activation.context().readLocalSlot("MyArray").orElseThrow();

        assertSame(myArray, result.parent().orElseThrow());
        assertEquals(BigInteger.valueOf(2), result.indexedSize());
        activation.context().createLocalSlot("myArrayPrototype", myArray);
        assertThrows(
                ProtosSignalException.class,
                () -> execute(activation, "myArrayPrototype.size()"));
    }

    private static Object execute(ProtosActivation activation, String source) {
        return new ProtosSourceCompiler().compile(source).call(activation);
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }
}

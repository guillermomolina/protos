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

package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosInvocation;
import com.guillermomolina.protos.execution.ProtosStandardProcessArgumentsProtocol;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosProcessArgumentsSnapshotTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void bootstrapCaptureIsCanonicalStableAndDetachedFromLaterHostMutation()
            throws Exception {
        ProtosProcessRuntime process = process();
        ProtosObjectValue prototype =
                ProtosStandardProcessArgumentsProtocol.createPrototype();
        ArrayList<String> host = new ArrayList<>(List.of("one", "two"));

        assertEquals(
                ProtosProcessRuntime.ArgumentsSnapshotState.AVAILABLE,
                process.establishArgumentsForRuntime(prototype, host));
        ProtosProcessArgumentsValue first =
                process.argumentsSnapshotForRuntime().orElseThrow();

        host.set(0, "changed");
        host.add("three");

        ProtosProcessArgumentsValue second =
                process.argumentsSnapshotForRuntime().orElseThrow();
        assertSame(first, second);
        assertTrue(ProtosIdentity.identical(first, second));
        assertEquals(BigInteger.valueOf(2), first.indexedSizeForRuntime());
        assertEquals("one", first.indexedAtForRuntime(BigInteger.ZERO).value());
        assertEquals("two", first.indexedAtForRuntime(BigInteger.ONE).value());

        assertThrows(
                IllegalStateException.class,
                () ->
                        process.establishArgumentsForRuntime(
                                prototype, List.of("replacement")));
    }

    @Test
    void completeUnrepresentableBootstrapOutcomeIsStableAndProducesNoSnapshot() {
        ProtosProcessRuntime process = process();
        ProtosObjectValue prototype =
                ProtosStandardProcessArgumentsProtocol.createPrototype();
        String invalid =
                new String(new char[] {'b', 'a', 'd', (char) 0xD800});

        assertEquals(
                ProtosProcessRuntime.ArgumentsSnapshotState.UNREPRESENTABLE,
                process.establishArgumentsForRuntime(
                        prototype, List.of("valid", invalid, "also-valid")));
        assertEquals(
                ProtosProcessRuntime.ArgumentsSnapshotState.UNREPRESENTABLE,
                process.argumentsSnapshotStateForRuntime());
        assertTrue(process.argumentsSnapshotForRuntime().isEmpty());

        assertThrows(
                IllegalStateException.class,
                () ->
                        process.establishArgumentsForRuntime(
                                prototype, List.of("now-valid")));
        assertTrue(process.argumentsSnapshotForRuntime().isEmpty());
    }

    @Test
    void sizeAtAndPolymorphicEachExposeOnlyTheImmutableSequentialContract()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosProcessRuntime process = process();
        ProtosObjectValue prototype =
                ProtosStandardProcessArgumentsProtocol.createPrototype();
        assertEquals(
                ProtosProcessRuntime.ArgumentsSnapshotState.AVAILABLE,
                process.establishArgumentsForRuntime(
                        prototype, List.of("alpha", "β", "😀")));
        ProtosProcessArgumentsValue arguments =
                process.argumentsSnapshotForRuntime().orElseThrow();

        assertEquals(
                BigInteger.valueOf(3),
                ((ProtosIntegerValue)
                                ProtosInvocation.invokeMessage(
                                        arguments,
                                        "size",
                                        List.of(),
                                        activation))
                        .value());
        assertEquals(
                "β",
                ((ProtosStringValue)
                                ProtosInvocation.invokeMessage(
                                        arguments,
                                        "at",
                                        List.of(
                                                new ProtosIntegerValue(
                                                        BigInteger.ONE)),
                                        activation))
                        .value());

        assertIndexedFailure(
                arguments,
                List.of(new ProtosIntegerValue(BigInteger.valueOf(-1))),
                activation,
                prelude);
        assertIndexedFailure(
                arguments,
                List.of(new ProtosIntegerValue(BigInteger.valueOf(3))),
                activation,
                prelude);
        assertIndexedFailure(
                arguments,
                List.of(new ProtosStringValue("1")),
                activation,
                prelude);

        ArrayList<String> seen = new ArrayList<>();
        ProtosObjectValue callable =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        callable.createLocalSlot(
                "call",
                ProtosClosureValue.nativeClosure(
                        (callbackActivation, supplied) -> {
                            assertEquals(1, supplied.size());
                            seen.add(
                                    ((ProtosStringValue) supplied.get(0))
                                            .value());
                            return ProtosNullValue.INSTANCE;
                        }));

        assertSame(
                arguments,
                ProtosInvocation.invokeMessage(
                        arguments, "each", List.of(callable), activation));
        assertEquals(List.of("alpha", "β", "😀"), seen);

        AtomicInteger calls = new AtomicInteger();
        ProtosObjectValue nonCallable =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        nonCallable.createLocalSlot(
                "notCall",
                ProtosClosureValue.nativeClosure(
                        (callbackActivation, supplied) -> {
                            calls.incrementAndGet();
                            return ProtosNullValue.INSTANCE;
                        }));
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                arguments,
                                "each",
                                List.of(nonCallable),
                                activation));
        assertEquals(0, calls.get());
    }

    @Test
    void canonicalIdentityIsPerProcessWhileActorTransferCreatesDestinationIdentity()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue prototype =
                ProtosStandardProcessArgumentsProtocol.createPrototype();

        ProtosProcessRuntime firstProcess = process();
        ProtosProcessRuntime secondProcess = process();
        firstProcess.establishArgumentsForRuntime(
                prototype, List.of("same"));
        secondProcess.establishArgumentsForRuntime(
                prototype, List.of("same"));

        ProtosProcessArgumentsValue canonical =
                firstProcess.argumentsSnapshotForRuntime().orElseThrow();
        ProtosProcessArgumentsValue sameProcessAgain =
                firstProcess.argumentsSnapshotForRuntime().orElseThrow();
        ProtosProcessArgumentsValue otherProcess =
                secondProcess.argumentsSnapshotForRuntime().orElseThrow();

        assertTrue(ProtosIdentity.identical(canonical, sameProcessAgain));
        assertFalse(ProtosIdentity.identical(canonical, otherProcess));

        ProtosProcessArgumentsValue copied =
                assertInstanceOf(
                        ProtosProcessArgumentsValue.class,
                        ProtosActorValueTransfer.snapshotValue(
                                canonical, activation));
        assertNotSame(canonical, copied);
        assertFalse(ProtosIdentity.identical(canonical, copied));
        assertEquals(
                canonical.valuesForRuntime().stream()
                        .map(ProtosStringValue::value)
                        .toList(),
                copied.valuesForRuntime().stream()
                        .map(ProtosStringValue::value)
                        .toList());

        List<Object> aliasCopy =
                ProtosActorValueTransfer.snapshotArguments(
                        List.of(canonical, canonical), activation);
        assertSame(aliasCopy.get(0), aliasCopy.get(1));
    }

    @Test
    void immutableArgumentSnapshotMayCrossPWithFreshDestinationIdentityAndAliasing()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosProcessRuntime process = process();
        process.establishArgumentsForRuntime(
                ProtosStandardProcessArgumentsProtocol.createPrototype(),
                List.of("p"));
        ProtosProcessArgumentsValue source =
                process.argumentsSnapshotForRuntime().orElseThrow();

        Class<?> transfer =
                Class.forName(
                        "com.guillermomolina.protos.execution.ProtosParallelRuntime$Transfer");
        Method copy =
                transfer.getDeclaredMethod(
                        "copy",
                        Object.class,
                        ProtosActivation.class,
                        IdentityHashMap.class);
        copy.setAccessible(true);

        IdentityHashMap<Object, Object> memo = new IdentityHashMap<>();
        ProtosProcessArgumentsValue first =
                assertInstanceOf(
                        ProtosProcessArgumentsValue.class,
                        copy.invoke(null, source, activation, memo));
        ProtosProcessArgumentsValue second =
                assertInstanceOf(
                        ProtosProcessArgumentsValue.class,
                        copy.invoke(null, source, activation, memo));

        assertNotSame(source, first);
        assertFalse(ProtosIdentity.identical(source, first));
        assertSame(first, second);
        assertEquals("p", first.indexedAtForRuntime(BigInteger.ZERO).value());
    }

    private static void assertIndexedFailure(
            ProtosProcessArgumentsValue arguments,
            List<?> supplied,
            ProtosActivation activation,
            ProtosPrelude prelude) {
        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosInvocation.invokeMessage(
                                        arguments, "at", supplied, activation));
        assertSame(
                prelude.errorPrototype(),
                signal.error().parent().orElseThrow());
    }

    private static ProtosProcessRuntime process() {
        return new ProtosProcessRuntime(
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze());
    }
}

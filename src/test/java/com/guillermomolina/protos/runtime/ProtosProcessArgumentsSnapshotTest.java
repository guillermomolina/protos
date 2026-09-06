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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosStandardProcessArgumentsProtocol;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
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

    // Source-level per-Process identity and size/at/each semantics live in the
    // Process snapshot conformance fixture. Keep only Actor transfer machinery here.
    @Test
    void actorTransferCreatesFreshDestinationIdentityAndPreservesAliases()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosProcessRuntime process = process();
        process.establishArgumentsForRuntime(
                ProtosStandardProcessArgumentsProtocol.createPrototype(),
                List.of("same"));
        ProtosProcessArgumentsValue source =
                process.argumentsSnapshotForRuntime().orElseThrow();

        List<Object> copied =
                ProtosActorValueTransfer.snapshotArguments(
                        List.of(source, source), activation);

        ProtosProcessArgumentsValue destination =
                assertInstanceOf(
                        ProtosProcessArgumentsValue.class, copied.get(0));
        assertNotSame(source, destination);
        assertFalse(ProtosIdentity.identical(source, destination));
        assertSame(destination, copied.get(1));
        assertEquals(
                source.valuesForRuntime().stream()
                        .map(ProtosStringValue::value)
                        .toList(),
                destination.valuesForRuntime().stream()
                        .map(ProtosStringValue::value)
                        .toList());
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
                        "com.guillermomolina.protos.execution."
                                + "ProtosParallelRuntime$Transfer");
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

    private static ProtosProcessRuntime process() {
        return new ProtosProcessRuntime(
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze());
    }
}

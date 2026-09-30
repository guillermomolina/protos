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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * D168: Process arguments are stable bootstrap content. The runtime retains only ordered Strings;
 * the public accessor materializes an ordinary frozen Array, so Actor and P transfer are the
 * ordinary Array isolation rules with no ProcessArguments-specific path.
 */
final class ProtosProcessArgumentsSnapshotTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void bootstrapCaptureIsStableAndDetachedFromLaterHostMutation() {
        ProtosProcessRuntime process = process();
        ArrayList<String> host = new ArrayList<>(List.of("one", "two"));

        assertEquals(
                ProtosProcessRuntime.ArgumentsSnapshotState.AVAILABLE,
                process.establishArgumentsForRuntime(host));

        host.set(0, "changed");
        host.add("three");

        assertEquals(List.of("one", "two"), strings(process));
        assertThrows(
                IllegalStateException.class,
                () -> process.establishArgumentsForRuntime(List.of("replacement")));
        assertEquals(List.of("one", "two"), strings(process));
    }

    @Test
    void emptyArgumentsAreAvailableAsEmptyContent() {
        ProtosProcessRuntime process = process();

        assertEquals(
                ProtosProcessRuntime.ArgumentsSnapshotState.AVAILABLE,
                process.establishArgumentsForRuntime(List.of()));
        assertTrue(strings(process).isEmpty());
    }

    @Test
    void completeUnrepresentableBootstrapOutcomeIsStableAndProducesNoSnapshot() {
        ProtosProcessRuntime process = process();
        String invalid = new String(new char[] {'b', 'a', 'd', (char) 0xD800});

        assertEquals(
                ProtosProcessRuntime.ArgumentsSnapshotState.UNREPRESENTABLE,
                process.establishArgumentsForRuntime(List.of("valid", invalid, "also-valid")));
        assertEquals(
                ProtosProcessRuntime.ArgumentsSnapshotState.UNREPRESENTABLE,
                process.argumentsSnapshotStateForRuntime());
        assertTrue(process.argumentsSnapshotForRuntime().isEmpty());

        assertThrows(
                IllegalStateException.class,
                () -> process.establishArgumentsForRuntime(List.of("now-valid")));
        assertTrue(process.argumentsSnapshotForRuntime().isEmpty());
    }

    // Source-level size/at/each semantics live in the Process conformance fixtures. Keep only
    // Actor/P transfer of the ordinary frozen Array here.
    @Test
    void actorTransferUsesOrdinaryFrozenArrayIsolationAndPreservesAliases() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosProcessRuntime process = process();
        process.establishArgumentsForRuntime(List.of("same"));
        ProtosArrayValue source =
                prelude.newFrozenArray(process.argumentsSnapshotForRuntime().orElseThrow());

        List<Object> copied =
                ProtosActorValueTransfer.snapshotArguments(List.of(source, source), activation);

        ProtosArrayValue destination = assertInstanceOf(ProtosArrayValue.class, copied.get(0));
        assertNotSame(source, destination);
        assertSame(destination, copied.get(1));
        assertTrue(destination.isFrozen());
        assertEquals(List.of("same"), strings(destination));
    }

    @Test
    void parallelTransferUsesOrdinaryFrozenArrayIsolationAndPreservesAliases() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosProcessRuntime process = process();
        process.establishArgumentsForRuntime(List.of("p"));
        ProtosArrayValue source =
                prelude.newFrozenArray(process.argumentsSnapshotForRuntime().orElseThrow());

        Class<?> transfer =
                Class.forName(
                        "com.guillermomolina.protos.execution.ProtosParallelRuntime$Transfer");
        Method copy =
                transfer.getDeclaredMethod(
                        "copy", Object.class, ProtosActivation.class, IdentityHashMap.class);
        copy.setAccessible(true);

        IdentityHashMap<Object, Object> memo = new IdentityHashMap<>();
        ProtosArrayValue first =
                assertInstanceOf(
                        ProtosArrayValue.class, copy.invoke(null, source, activation, memo));
        ProtosArrayValue second =
                assertInstanceOf(
                        ProtosArrayValue.class, copy.invoke(null, source, activation, memo));

        assertNotSame(source, first);
        assertSame(first, second);
        assertTrue(first.isFrozen());
        assertEquals(List.of("p"), strings(first));
    }

    private static List<String> strings(ProtosProcessRuntime process) {
        return process.argumentsSnapshotForRuntime().orElseThrow().stream()
                .map(ProtosStringValue::value)
                .toList();
    }

    private static List<String> strings(ProtosArrayValue array) {
        return array.indexedSnapshot().stream()
                .map(element -> ((ProtosStringValue) element).value())
                .toList();
    }

    private static ProtosProcessRuntime process() {
        return new ProtosProcessRuntime(
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze());
    }
}

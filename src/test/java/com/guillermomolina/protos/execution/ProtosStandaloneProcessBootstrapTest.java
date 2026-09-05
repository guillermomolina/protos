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

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosStandaloneProcessBootstrapTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void standaloneEntryGetsCompleteBootstrapStateBeforeSourceExecution()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosEncodingValue utf8 = encoding(prelude, "UTF8");

        ProtosStandaloneProcessBootstrap.Result result =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        List.of("alpha", "beta"),
                        exactDomain(),
                        List.of(
                                new ProtosEnvironmentValue.NativeEntry("A", "one"),
                                new ProtosEnvironmentValue.NativeEntry("B", "two")),
                        (maxBytes, completion) -> {
                            completion.eof();
                            return () -> {};
                        },
                        (bytes, completion) -> {
                            completion.succeeded();
                            return () -> {};
                        },
                        null,
                        utf8,
                        utf8,
                        null,
                        null);

        ProtosProcessRuntime process = result.process();
        ProtosProcessCapabilityValue capability =
                assertInstanceOf(
                        ProtosProcessCapabilityValue.class,
                        result.activation()
                                .context()
                                .readLocalSlot("process")
                                .orElseThrow());

        assertSame(process, capability.processForRuntime());
        assertSame(prelude.processPrototype(), capability.representedDelegationParent(prelude));
        assertFalse(result.activation().context().hasLocalSlot("filesystem"));

        assertEquals(
                List.of("alpha", "beta"),
                process.argumentsSnapshotForRuntime()
                        .orElseThrow()
                        .valuesForRuntime()
                        .stream()
                        .map(value -> value.value())
                        .toList());
        assertEquals(
                ProtosProcessRuntime.EnvironmentSnapshotState.AVAILABLE,
                process.environmentSnapshotStateForRuntime());
        assertTrue(process.stdinForRuntime().isPresent());
        assertTrue(process.stdoutForRuntime().isPresent());
        assertTrue(process.stderrForRuntime().isEmpty());
        assertSame(utf8, process.stdinEncodingForRuntime().orElseThrow());
        assertSame(utf8, process.stdoutEncodingForRuntime().orElseThrow());
        assertTrue(process.stderrEncodingForRuntime().isEmpty());

        assertSame(
                process.rootActorForRuntime().reference(),
                result.activation()
                        .executionDomain()
                        .currentActorReference()
                        .orElseThrow());
        assertTrue(
                prelude.actorRefPrototypeForRuntime().hasLocalSlot("send"),
                "RootActor reference must use the standard hidden ActorRef prototype");
    }

    @Test
    void optionalFilesystemGrantUsesExactCapabilityAndAbsenceIsNotNullSlot()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosFilesystemValue filesystem = new ProtosFilesystemValue();

        ProtosStandaloneProcessBootstrap.Result granted =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        List.of(),
                        exactDomain(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        filesystem);

        assertSame(
                filesystem,
                granted.activation()
                        .context()
                        .readLocalSlot("filesystem")
                        .orElseThrow());

        ProtosStandaloneProcessBootstrap.Result absent =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        List.of(),
                        exactDomain(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        assertFalse(absent.activation().context().hasLocalSlot("filesystem"));
    }

    @Test
    void invalidBootstrapDataRemainsStableForPublicProcessAccessors()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosEncodingValue utf8 = encoding(prelude, "UTF8");

        ProtosStandaloneProcessBootstrap.Result result =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        List.of("ok", String.valueOf((char) 0xD800)),
                        exactDomain(),
                        List.of(),
                        null,
                        null,
                        null,
                        utf8,
                        null,
                        null,
                        null);

        assertEquals(
                ProtosProcessRuntime.ArgumentsSnapshotState.UNREPRESENTABLE,
                result.process().argumentsSnapshotStateForRuntime());
        assertEquals(
                ProtosProcessRuntime.StandardStreamEncodingState.INVALID,
                result.process().stdinEncodingStateForRuntime());
    }

    private static ProtosEnvironmentValue.NativeNameDomain exactDomain() {
        return new ProtosEnvironmentValue.NativeNameDomain() {
            @Override
            public boolean sameCapturedName(String left, String right) {
                return left.equals(right);
            }

            @Override
            public boolean isQueryRepresentable(String name) {
                return !name.contains("=") && name.indexOf('\0') < 0;
            }

            @Override
            public boolean matchesQuery(String captured, String query) {
                return captured.equals(query);
            }
        };
    }

    private static ProtosEncodingValue encoding(ProtosPrelude prelude, String name) {
        return assertInstanceOf(
                ProtosEncodingValue.class,
                prelude.encodingPrototype().readLocalSlot(name).orElseThrow());
    }
}

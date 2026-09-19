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

import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosTestLogicalCaseAttemptBridgeTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY =
            Path.of("protos", "lib");
    private static final Path TOOL_ROOT =
            Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT =
            Path.of("protos", "tools", "shared");

    @Test
    void executesExactlySelectedCaseAfterExactRematerialization(
            @TempDir Path root) throws Exception {
        Path suite = root.resolve("suite.protos");

        String source =
                """
                TestValue: import("std:test/Test")
                Assertions: import("std:test/Assertions")

                Assertions.signals(Error, () => { process })
                Assertions.signals(Error, () => { filesystem })
                Assertions.signals(Error, () => { network })
                Assertions.signals(Error, () => { resources })

                authorityFree: true

                tests: Array(
                    TestValue("first", () => {
                        Error().signal()
                    }),
                    TestValue("second", () => {
                        Assertions.require(authorityFree)
                        22
                    })
                )
                """;

        Files.writeString(
                suite,
                source,
                StandardCharsets.UTF_8);

        ProtosBundledToolModuleResolver fallback =
                fallbackResolver();

        try (ProtosPolyglotRuntimeHost runtimeHost =
                ProtosPolyglotRuntimeHost.open()) {
            ProtosTestLogicalCaseAttemptBridge bridge =
                    new ProtosTestLogicalCaseAttemptBridge(
                            CORE,
                            fallback,
                            runtimeHost);

            ProtosTestLogicalCaseAttemptBridge.Result result =
                    bridge.execute(
                            new ProtosTestLogicalCaseAttemptBridge.Request(
                                    suite,
                                    source,
                                    List.of("first", "second"),
                                    "second"));

            assertEquals(
                    ProtosTestLogicalCaseAttemptBridge.Phase.CASE_EXECUTION,
                    result.phase());
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    result.outcome().state());

            ProtosIntegerValue value =
                    (ProtosIntegerValue) result.outcome().value();

            assertEquals(
                    BigInteger.valueOf(22),
                    value.value());

            assertEquals(0, result.stdout().length);
            assertEquals(0, result.stderr().length);

            assertEquals(
                    0,
                    runtimeHost.activeProcessContextCountForTesting());
        }
    }

    @Test
    void classifiesSignatureMismatchAsRematerializationErrorWithoutRunningCase(
            @TempDir Path root) throws Exception {
        Path suite = root.resolve("suite.protos");

        String source =
                """
                TestValue: import("std:test/Test")

                tests: Array(
                    TestValue("second", () => {
                        Error().signal()
                    }),
                    TestValue("first", () => {
                        Error().signal()
                    })
                )
                """;

        Files.writeString(
                suite,
                source,
                StandardCharsets.UTF_8);

        ProtosBundledToolModuleResolver fallback =
                fallbackResolver();

        try (ProtosPolyglotRuntimeHost runtimeHost =
                ProtosPolyglotRuntimeHost.open()) {
            ProtosTestLogicalCaseAttemptBridge bridge =
                    new ProtosTestLogicalCaseAttemptBridge(
                            CORE,
                            fallback,
                            runtimeHost);

            ProtosTestLogicalCaseAttemptBridge.Result result =
                    bridge.execute(
                            new ProtosTestLogicalCaseAttemptBridge.Request(
                                    suite,
                                    source,
                                    List.of("first", "second"),
                                    "second"));

            assertEquals(
                    ProtosTestLogicalCaseAttemptBridge.Phase.REMATERIALIZATION_ERROR,
                    result.phase());
            assertEquals(
                    ProtosExecutionOutcome.State.FAILED,
                    result.outcome().state());

            assertEquals(
                    0,
                    runtimeHost.activeProcessContextCountForTesting());
        }
    }

    @Test
    void classifiesUnknownSelectorAsRematerializationError(
            @TempDir Path root) throws Exception {
        Path suite = root.resolve("suite.protos");

        String source =
                """
                TestValue: import("std:test/Test")

                tests: Array(
                    TestValue("first", () => {
                        Error().signal()
                    })
                )
                """;

        Files.writeString(
                suite,
                source,
                StandardCharsets.UTF_8);

        ProtosBundledToolModuleResolver fallback =
                fallbackResolver();

        try (ProtosPolyglotRuntimeHost runtimeHost =
                ProtosPolyglotRuntimeHost.open()) {
            ProtosTestLogicalCaseAttemptBridge bridge =
                    new ProtosTestLogicalCaseAttemptBridge(
                            CORE,
                            fallback,
                            runtimeHost);

            ProtosTestLogicalCaseAttemptBridge.Result result =
                    bridge.execute(
                            new ProtosTestLogicalCaseAttemptBridge.Request(
                                    suite,
                                    source,
                                    List.of("first"),
                                    "missing"));

            assertEquals(
                    ProtosTestLogicalCaseAttemptBridge.Phase.REMATERIALIZATION_ERROR,
                    result.phase());
            assertEquals(
                    ProtosExecutionOutcome.State.FAILED,
                    result.outcome().state());

            assertEquals(
                    0,
                    runtimeHost.activeProcessContextCountForTesting());
        }
    }

    private static ProtosBundledToolModuleResolver fallbackResolver() {
        return new ProtosBundledToolModuleResolver(
                "test",
                TOOL_ROOT,
                SHARED_ROOT,
                new ProtosStandardLibraryModuleResolver(
                        STANDARD_LIBRARY));
    }
}

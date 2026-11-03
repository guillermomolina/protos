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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
    private static final Path PACKAGE_TOOL_ROOT =
            Path.of("protos", "tools", "package");
    private static final Path RESOLUTION_INPUT_LOCK_CORPUS =
            Path.of(
                    "protos",
                    "tests",
                    "package-tool",
                    "resolution-input-lock");

    @Test
    void executesExactlySelectedCaseAfterExactRematerialization(
            @TempDir Path root) throws Exception {
        Path suite = root.resolve("suite.protos");

        String source =
                """
                TestValue: import("std:test/test")
                Assertions: import("std:test/assertions")

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
                                    List.of("first", "second"),
                                    "second"));

            assertEquals(
                    ProtosTestLogicalCaseAttemptBridge.Phase.CASE_EXECUTION,
                    result.phase());
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    result.outcome().state());

            Long value =
                    (Long) result.outcome().value();

            assertEquals(String.valueOf("22"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value));

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
                TestValue: import("std:test/test")

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
                TestValue: import("std:test/test")

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

    // An inline minimal suite with no self: imports, isolating whether
    // projectTreeFilesystem is visible to a selected Test's call() when
    // project-tree provisioning is active, independent of any real fixture's
    // own module dependencies.
    @Test
    void minimalInlineSuiteSeesProjectTreeFilesystem(
            @TempDir Path root) throws Exception {
        Path casesRoot =
                RESOLUTION_INPUT_LOCK_CORPUS.resolve("cases");
        Path authorityRoot = casesRoot.resolve("fresh");

        try (ProtosNioReadOnlyTreeFilesystemBackend probe =
                new ProtosNioReadOnlyTreeFilesystemBackend(authorityRoot)) {
            assumeTrue(
                    probe.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");
        }

        Path fixture =
                root.resolve("inline.protos");

        String source =
                """
                Assertions: import("std:test/assertions")
                Test: import("std:test/test")

                tests: [
                    Test("fresh", () => {
                        Assertions.require(projectTreeFilesystem !== null)
                    })
                ]
                tests.freeze()
                """;

        Files.writeString(
                fixture,
                source,
                StandardCharsets.UTF_8);

        ProtosModuleResolver fallback = packageFallbackResolver();

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
                                    fixture,
                                    List.of("fresh"),
                                    "fresh",
                                    casesRoot,
                                    "fresh"));

            String diagnostic =
                    "phase="
                            + result.phase()
                            + " state="
                            + result.outcome().state()
                            + " stdout="
                            + new String(
                                    result.stdout(),
                                    StandardCharsets.UTF_8)
                            + " stderr="
                            + new String(
                                    result.stderr(),
                                    StandardCharsets.UTF_8)
                            + " errorSlots="
                            + (result.outcome().error() == null
                                    ? "null"
                                    : result.outcome()
                                            .error()
                                            .localSlotsSnapshot())
                            + "; trace="
                            + result.outcome().failureDiagnosticTrace();

            assertEquals(
                    ProtosTestLogicalCaseAttemptBridge.Phase.CASE_EXECUTION,
                    result.phase(),
                    diagnostic);
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    result.outcome().state(),
                    diagnostic);
        }
    }

    // TOOL009 Package Non-TOML Publication 2: proves the merged project-tree
    // adapter end to end against the real "fresh" project-tree authority and
    // the real (now suite-native) fresh.protos fixture, in one fresh Process.
    @Test
    void projectTreeAuthorityIsVisibleToSelectedTestInSameProcess()
            throws Exception {
        Path casesRoot =
                RESOLUTION_INPUT_LOCK_CORPUS.resolve("cases");
        Path authorityRoot = casesRoot.resolve("fresh");

        try (ProtosNioReadOnlyTreeFilesystemBackend probe =
                new ProtosNioReadOnlyTreeFilesystemBackend(authorityRoot)) {
            assumeTrue(
                    probe.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");
        }

        Path fixture =
                RESOLUTION_INPUT_LOCK_CORPUS
                        .resolve("fixtures")
                        .resolve("fresh.protos");

        String source =
                Files.readString(
                        fixture,
                        StandardCharsets.UTF_8);

        ProtosModuleResolver fallback = packageFallbackResolver();

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
                                    fixture,
                                    List.of("fresh"),
                                    "fresh",
                                    casesRoot,
                                    "fresh"));

            String diagnostic =
                    "phase="
                            + result.phase()
                            + " state="
                            + result.outcome().state()
                            + " stdout="
                            + new String(
                                    result.stdout(),
                                    StandardCharsets.UTF_8)
                            + " stderr="
                            + new String(
                                    result.stderr(),
                                    StandardCharsets.UTF_8)
                            + " errorSlots="
                            + (result.outcome().error() == null
                                    ? "null"
                                    : result.outcome()
                                            .error()
                                            .localSlotsSnapshot())
                            + "; trace="
                            + result.outcome().failureDiagnosticTrace();

            assertEquals(
                    ProtosTestLogicalCaseAttemptBridge.Phase.CASE_EXECUTION,
                    result.phase(),
                    diagnostic);
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    result.outcome().state(),
                    diagnostic);
        }
    }

    /**
     * PERF018: one bridge bootstraps Core once and serves every Case attempt from that Prelude,
     * while each attempt still gets a fresh Process/Context with its own module resolver. Two
     * suites share the same entry and local-import file names but different contents, so any
     * cross-Case resolver or module-cache sharing would surface as a wrong value.
     */
    @Test
    void reusesOneCorePreludeWhileEachCaseKeepsItsOwnLocalModuleResolver(
            @TempDir Path root) throws Exception {
        Path suiteA = writeSuiteWithHelper(root.resolve("a"), 11);
        Path suiteB = writeSuiteWithHelper(root.resolve("b"), 22);

        try (ProtosPolyglotRuntimeHost runtimeHost =
                ProtosPolyglotRuntimeHost.open()) {
            ProtosTestLogicalCaseAttemptBridge bridge =
                    new ProtosTestLogicalCaseAttemptBridge(
                            CORE,
                            fallbackResolver(),
                            runtimeHost);

            ProtosTestLogicalCaseAttemptBridge.Result first =
                    bridge.execute(
                            new ProtosTestLogicalCaseAttemptBridge.Request(
                                    suiteA, List.of("only"), "only"));
            ProtosTestLogicalCaseAttemptBridge.Result second =
                    bridge.execute(
                            new ProtosTestLogicalCaseAttemptBridge.Request(
                                    suiteB, List.of("only"), "only"));
            ProtosTestLogicalCaseAttemptBridge.Result firstAgain =
                    bridge.execute(
                            new ProtosTestLogicalCaseAttemptBridge.Request(
                                    suiteA, List.of("only"), "only"));

            assertEquals(String.valueOf("11"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(first.outcome().value()));
            assertEquals(String.valueOf("22"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(second.outcome().value()));
            assertEquals(String.valueOf("11"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(firstAgain.outcome().value()));

            assertSame(first.sourcePrelude(), second.sourcePrelude());
            assertSame(first.sourcePrelude(), firstAgain.sourcePrelude());
            assertEquals(
                    0,
                    runtimeHost.activeProcessContextCountForTesting());
        }
    }

    private static Path writeSuiteWithHelper(Path directory, int value)
            throws Exception {
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve("helper.protos"),
                "answer: " + value + "\n",
                StandardCharsets.UTF_8);
        Path suite = directory.resolve("suite.protos");
        Files.writeString(
                suite,
                """
                TestValue: import("std:test/test")
                Helper: import("./helper.protos")

                tests: Array(
                    TestValue("only", () => {
                        Helper.answer
                    })
                )
                """,
                StandardCharsets.UTF_8);
        return suite;
    }

    private static ProtosBundledToolModuleResolver fallbackResolver() {
        return new ProtosBundledToolModuleResolver(
                "test",
                TOOL_ROOT,
                SHARED_ROOT,
                new ProtosStandardLibraryModuleResolver(
                        STANDARD_LIBRARY));
    }

    /**
     * D242 test-only resolver for one externally selected logical Case.
     *
     * <p>The host admits exactly two Package Tool SOURCE entrances from the
     * Case. Every transitive import is resolved and loaded by the owning
     * bundled Package Tool resolver, which enforces its physical root and
     * exact SOURCE identity. Test Tool and Standard Library imports retain
     * their ordinary resolvers. No production overlay policy is changed.
     */
    private static ProtosModuleResolver packageFallbackResolver() {
        ProtosModuleResolver standard =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);

        ProtosBundledToolModuleResolver testResolver =
                new ProtosBundledToolModuleResolver(
                        "test", TOOL_ROOT, SHARED_ROOT, standard);

        ProtosBundledToolModuleResolver packageResolver =
                new ProtosBundledToolModuleResolver(
                        "package",
                        PACKAGE_TOOL_ROOT,
                        PACKAGE_TOOL_ROOT.resolveSibling("shared"),
                        standard);

        return new ProtosModuleResolver() {
            @Override
            public ProtosModuleKey resolve(
                    String specifier,
                    java.util.Optional<ProtosModuleKey> importer)
                    throws Exception {
                // I151_B49_OWNER_ROUTING: the host authorizes exactly two
                // entrances from its external Case SOURCE into Package Tool.
                boolean versionEntry =
                        specifier.equals(
                                "tool-source:package/ReleaseVersion.protos");
                boolean lockEntry =
                        specifier.equals(
                                "tool-source:package/LockFile.protos");

                if (versionEntry || lockEntry) {
                    // D242: these are two exact SOURCE entrances authorized
                    // by this Case-specific host resolver. A Test body may
                    // execute with a different currentModuleKey from its
                    // declaring SOURCE; that key is not the authority grant.
                    // No arbitrary Package Tool file is selectable here.
                    return ProtosBundledToolModuleResolver.toolSourceKey(
                            "package",
                            versionEntry ? "ReleaseVersion" : "LockFile");
                }

                // Once imported, the Package Tool owns all its transitive
                // relative SOURCE imports. Do not route them through an
                // exact-entry overlay or through the external Case root.
                if (importer.isPresent()) {
                    ProtosModuleKey origin = importer.orElseThrow();
                    if (!origin.isForeign()
                            && origin.canonicalId()
                                    .startsWith("bundled-tool:package/")) {
                        return packageResolver.resolve(specifier, importer);
                    }
                }

                return testResolver.resolve(specifier, importer);
            }

            @Override
            public ProtosModuleSource loadSource(ProtosModuleKey key)
                    throws Exception {
                if (!key.isForeign()
                        && key.canonicalId()
                                .startsWith("bundled-tool:package/")) {
                    return packageResolver.loadSource(key);
                }
                return testResolver.loadSource(key);
            }
        };
    }
}

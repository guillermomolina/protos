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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TOOL009 Package Tool infrastructure: focal coverage for the Package-flavored suite-native
 * logical Case execution route.
 *
 * <p>These tests exercise the ordinary {@link ProtosTestLogicalCaseExecutionFacility} /
 * {@link ProtosTestLogicalCaseAttemptBridge} machinery parameterized with a Package-specific
 * fallback resolver (the exact bundled Package Tool resolver construction {@code packagePrelude}
 * itself uses, overlaid with host-selected exact references to Package Tool's own
 * {@code RuntimeNames} module and to the finite Package TOML module graph
 * ({@code TomlSyntax}/{@code TomlDocument}/{@code ManifestSchemaV1} and the shared Toml10
 * modules they depend on) so a selected Test body can prove which bootstrap resolved it),
 * demonstrating that the suite-native route can resolve and execute Package-flavored suites
 * without a parallel Case authority and without touching the legacy {@code packageExecutionAsync}
 * facility or its {@code packagePrelude} bootstrap. The Package TOML module graph coverage below
 * invokes the real {@code protos/tools/package} and {@code protos/tools/shared/Toml10} module
 * files (not copies embedded in this test) so the resolver route is proven against the actual
 * corpus dependency graph. None of the 102 TOML corpus fixtures under
 * {@code protos/tests/package-tool/toml-syntax} are used here; every suite source is a dedicated
 * inline fixture for this infrastructure, exercising the real modules through ordinary imports.
 */
final class ProtosPackageTestLogicalCaseExecutionFacilityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT = Path.of("protos", "tools", "shared");
    private static final Path PACKAGE_TOOL_ROOT = Path.of("protos", "tools", "package");

    private static final String SOURCE_USES_PACKAGE_RUNTIME_NAMES =
            """
            TestValue: import("std:test/Test")
            RuntimeNames: import("package-runtime-names")

            tests: Array(
                TestValue("usesPackageBootstrap", () => {
                    RuntimeNames.isReserved("CON")
                })
            )
            """;

    private static final String SOURCE_TWO_TESTS =
            """
            TestValue: import("std:test/Test")

            tests: Array(
                TestValue("a", () => {
                    1
                }),
                TestValue("b", () => {
                    2
                })
            )
            """;

    private static final String SOURCE_SELECTED_TEST_FAILS =
            """
            TestValue: import("std:test/Test")

            tests: Array(
                TestValue("fails", () => {
                    Error().signal()
                })
            )
            """;

    private static final String SOURCE_USES_TOML_SYNTAX =
            """
            TestValue: import("std:test/Test")
            Toml: import("self:TomlSyntax")

            tests: Array(
                TestValue("resolvesTomlSyntax", () => {
                    parts: Toml.keyPath("dependencies . parser-core")
                    ok: parts.size() == 2
                    (parts[0] == "dependencies").ifFalse(() => { ok = false })
                    (parts[1] == "parser-core").ifFalse(() => { ok = false })
                    ok
                })
            )
            """;

    private static final String SOURCE_USES_TOML_DOCUMENT =
            """
            TestValue: import("std:test/Test")
            Document: import("self:TomlDocument")

            tests: Array(
                TestValue("resolvesTomlDocument", () => {
                    root: Document.table("manifest-version = 1\\n[package]\\nid = \\"pkg-1\\"\\n")
                    version: root.value["manifest-version"]
                    package: root.value["package"]
                    ok: root.kind == "table"
                    (version.kind == "integer").ifFalse(() => { ok = false })
                    (version.value == 1).ifFalse(() => { ok = false })
                    (package.kind == "table").ifFalse(() => { ok = false })
                    (package.value["id"].value == "pkg-1").ifFalse(() => { ok = false })
                    ok
                })
            )
            """;

    private static final String SOURCE_USES_MANIFEST_SCHEMA_V1 =
            """
            TestValue: import("std:test/Test")
            Schema: import("self:ManifestSchemaV1")

            tests: Array(
                TestValue("resolvesManifestSchemaAndItsLazyTomlDocumentImport", () => {
                    model: Schema.parseBase(
                        "manifest-version = 1\\n[package]\\nid = \\"pkg-id\\"\\nversion = \\"1.2.3\\"\\n")
                    ok: model.package.id == "pkg-id"
                    (model.package.version == "1.2.3").ifFalse(() => { ok = false })
                    ok
                })
            )
            """;

    @Test
    void resolvesPackageRuntimeNamesOverlayAndExecutesSelectedTestExactlyOnce(
            @TempDir Path root) throws Exception {
        writeSuite(root, SOURCE_USES_PACKAGE_RUNTIME_NAMES);

        ManualSubmission submission = new ManualSubmission();
        ProtosModuleResolver resolver = packageResolver();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestLogicalCaseExecutionFacility facility =
                        ProtosTestLogicalCaseExecutionFacility.install(
                                activation,
                                "packageLogicalCaseExecutionAsync",
                                CORE,
                                resolver,
                                List.of(
                                        new ProtosTestToolFileSelectionFacility.CorpusSourceRoot(
                                                "package-corpus", root)),
                                runtimeHost,
                                submission)) {

            ProtosFutureValue future =
                    invoke(
                            facility,
                            prelude,
                            activation,
                            SOURCE_USES_PACKAGE_RUNTIME_NAMES,
                            List.of("usesPackageBootstrap"),
                            "usesPackageBootstrap");

            // Exactly one host execution is enqueued for the one selected Test.
            assertEquals(1, submission.queuedCount());
            assertTrue(submission.runNext());
            assertFalse(submission.runNext(), "no second host execution for the same Case");

            assertTrue(activation.executionDomain().dispatchOne());

            // "package-runtime-names" only resolves under the Package-flavored fallback
            // resolver: it does not exist under the ordinary/Actor/Group fallback
            // resolvers. A successful "true" observation is reachable only if the
            // Package Tool bootstrap resolved RuntimeNames.protos and the selected
            // Test body actually executed exactly once.
            assertSame(
                    ProtosBooleanValue.TRUE,
                    assertCompletedObservationValue(future));
        }
    }

    @Test
    void resolvesRealTomlSyntaxModuleAndItsSharedToml10Dependency(@TempDir Path root)
            throws Exception {
        writeSuite(root, SOURCE_USES_TOML_SYNTAX);

        ManualSubmission submission = new ManualSubmission();
        ProtosModuleResolver resolver = packageResolver();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestLogicalCaseExecutionFacility facility =
                        ProtosTestLogicalCaseExecutionFacility.install(
                                activation,
                                "packageLogicalCaseExecutionAsync",
                                CORE,
                                resolver,
                                List.of(
                                        new ProtosTestToolFileSelectionFacility.CorpusSourceRoot(
                                                "package-corpus", root)),
                                runtimeHost,
                                submission)) {

            ProtosFutureValue future =
                    invoke(
                            facility,
                            prelude,
                            activation,
                            SOURCE_USES_TOML_SYNTAX,
                            List.of("resolvesTomlSyntax"),
                            "resolvesTomlSyntax");

            assertTrue(submission.runNext());
            assertTrue(activation.executionDomain().dispatchOne());

            // "self:TomlSyntax" resolves to the real protos/tools/package/TomlSyntax.protos,
            // which itself imports "tool-shared:Toml10/TomlSyntax". A "true" observation is
            // reachable only if both modules resolved and the real parser logic executed.
            assertSame(
                    ProtosBooleanValue.TRUE,
                    assertCompletedObservationValue(future));
        }
    }

    @Test
    void resolvesRealTomlDocumentModuleAndItsTransitiveTomlSyntaxDependency(@TempDir Path root)
            throws Exception {
        writeSuite(root, SOURCE_USES_TOML_DOCUMENT);

        ManualSubmission submission = new ManualSubmission();
        ProtosModuleResolver resolver = packageResolver();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestLogicalCaseExecutionFacility facility =
                        ProtosTestLogicalCaseExecutionFacility.install(
                                activation,
                                "packageLogicalCaseExecutionAsync",
                                CORE,
                                resolver,
                                List.of(
                                        new ProtosTestToolFileSelectionFacility.CorpusSourceRoot(
                                                "package-corpus", root)),
                                runtimeHost,
                                submission)) {

            ProtosFutureValue future =
                    invoke(
                            facility,
                            prelude,
                            activation,
                            SOURCE_USES_TOML_DOCUMENT,
                            List.of("resolvesTomlDocument"),
                            "resolvesTomlDocument");

            assertTrue(submission.runNext());
            assertTrue(activation.executionDomain().dispatchOne());

            // "self:TomlDocument" resolves to the real protos/tools/package/TomlDocument.protos,
            // which imports "tool-shared:Toml10/TomlDocument", which in turn imports
            // "tool-shared:Toml10/TomlSyntax". Parsing a real two-level TOML table and asserting
            // its structure is only reachable if the whole chain resolved and executed.
            assertSame(
                    ProtosBooleanValue.TRUE,
                    assertCompletedObservationValue(future));
        }
    }

    @Test
    void resolvesRealManifestSchemaV1AndItsLazyTomlDocumentImport(@TempDir Path root)
            throws Exception {
        writeSuite(root, SOURCE_USES_MANIFEST_SCHEMA_V1);

        ManualSubmission submission = new ManualSubmission();
        ProtosModuleResolver resolver = packageResolver();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestLogicalCaseExecutionFacility facility =
                        ProtosTestLogicalCaseExecutionFacility.install(
                                activation,
                                "packageLogicalCaseExecutionAsync",
                                CORE,
                                resolver,
                                List.of(
                                        new ProtosTestToolFileSelectionFacility.CorpusSourceRoot(
                                                "package-corpus", root)),
                                runtimeHost,
                                submission)) {

            ProtosFutureValue future =
                    invoke(
                            facility,
                            prelude,
                            activation,
                            SOURCE_USES_MANIFEST_SCHEMA_V1,
                            List.of("resolvesManifestSchemaAndItsLazyTomlDocumentImport"),
                            "resolvesManifestSchemaAndItsLazyTomlDocumentImport");

            assertTrue(submission.runNext());
            assertTrue(activation.executionDomain().dispatchOne());

            // "self:ManifestSchemaV1" resolves to the real
            // protos/tools/package/ManifestSchemaV1.protos. Its "self:TomlDocument" import is
            // lazy: it lives inside the "parseBase" callable body, not at module top level. The
            // selected Test body calls "Schema.parseBase(...)", which is the only thing that
            // forces that lazy import to actually resolve. A dormant, never-imported module
            // would not produce this "true" observation.
            assertSame(
                    ProtosBooleanValue.TRUE,
                    assertCompletedObservationValue(future));
        }
    }

    @Test
    void selectedTestBodyIsCaseAuthorityNotModuleCompletion(@TempDir Path root)
            throws Exception {
        writeSuite(root, SOURCE_SELECTED_TEST_FAILS);

        ManualSubmission submission = new ManualSubmission();
        ProtosModuleResolver resolver = packageResolver();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestLogicalCaseExecutionFacility facility =
                        ProtosTestLogicalCaseExecutionFacility.install(
                                activation,
                                "packageLogicalCaseExecutionAsync",
                                CORE,
                                resolver,
                                List.of(
                                        new ProtosTestToolFileSelectionFacility.CorpusSourceRoot(
                                                "package-corpus", root)),
                                runtimeHost,
                                submission)) {

            ProtosFutureValue future =
                    invoke(
                            facility,
                            prelude,
                            activation,
                            SOURCE_SELECTED_TEST_FAILS,
                            List.of("fails"),
                            "fails");

            assertTrue(submission.runNext());
            assertTrue(activation.executionDomain().dispatchOne());

            ProtosObjectValue completion =
                    assertInstanceOf(
                            ProtosObjectValue.class, future.resolvedValue().orElseThrow());
            ProtosStringValue phase =
                    assertInstanceOf(
                            ProtosStringValue.class,
                            completion.readLocalSlot("phase").orElseThrow());
            // The suite declaration itself (building the "tests" Array) completed
            // successfully; only the selected Test body signaled. If module
            // completion were the Case authority, this Case would be reported as
            // completed. It is not: the observation records the selected Test's own
            // failure.
            assertEquals("case-execution", phase.value());

            ProtosObjectValue observation =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            completion.readLocalSlot("observation").orElseThrow());
            ProtosStringValue state =
                    assertInstanceOf(
                            ProtosStringValue.class,
                            observation.readLocalSlot("state").orElseThrow());
            assertEquals("failed", state.value());
        }
    }

    @Test
    void multipleTestsSelectsExactlyTheRequestedOne(@TempDir Path root) throws Exception {
        writeSuite(root, SOURCE_TWO_TESTS);

        ManualSubmission submission = new ManualSubmission();
        ProtosModuleResolver resolver = packageResolver();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestLogicalCaseExecutionFacility facility =
                        ProtosTestLogicalCaseExecutionFacility.install(
                                activation,
                                "packageLogicalCaseExecutionAsync",
                                CORE,
                                resolver,
                                List.of(
                                        new ProtosTestToolFileSelectionFacility.CorpusSourceRoot(
                                                "package-corpus", root)),
                                runtimeHost,
                                submission)) {

            ProtosFutureValue future =
                    invoke(
                            facility,
                            prelude,
                            activation,
                            SOURCE_TWO_TESTS,
                            List.of("a", "b"),
                            "b");

            assertTrue(submission.runNext());
            assertTrue(activation.executionDomain().dispatchOne());

            ProtosIntegerValue value =
                    assertInstanceOf(
                            ProtosIntegerValue.class, assertCompletedObservationValue(future));
            assertEquals(BigInteger.valueOf(2), value.value());
        }
    }

    @Test
    void eachCaseIsIsolatedAndDoesNotShareMutableStateWithAPriorCase(@TempDir Path root)
            throws Exception {
        String source =
                """
                TestValue: import("std:test/Test")

                counter: { value: 0 }

                tests: Array(
                    TestValue("increments", () => {
                        counter.value = counter.value + 1
                        counter.value
                    })
                )
                """;

        writeSuite(root, source);

        ManualSubmission submission = new ManualSubmission();
        ProtosModuleResolver resolver = packageResolver();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestLogicalCaseExecutionFacility facility =
                        ProtosTestLogicalCaseExecutionFacility.install(
                                activation,
                                "packageLogicalCaseExecutionAsync",
                                CORE,
                                resolver,
                                List.of(
                                        new ProtosTestToolFileSelectionFacility.CorpusSourceRoot(
                                                "package-corpus", root)),
                                runtimeHost,
                                submission)) {

            ProtosFutureValue firstCase =
                    invoke(
                            facility, prelude, activation, source, List.of("increments"),
                            "increments");
            assertTrue(submission.runNext());
            assertTrue(activation.executionDomain().dispatchOne());
            assertEquals(
                    BigInteger.valueOf(1),
                    assertInstanceOf(
                                    ProtosIntegerValue.class,
                                    assertCompletedObservationValue(firstCase))
                            .value());

            ProtosFutureValue secondCase =
                    invoke(
                            facility, prelude, activation, source, List.of("increments"),
                            "increments");
            assertTrue(submission.runNext());
            assertTrue(activation.executionDomain().dispatchOne());

            // If the second Case reused the first Case's Process/module state, the
            // counter would already be at 1 and this request would observe 2.
            assertEquals(
                    BigInteger.valueOf(1),
                    assertInstanceOf(
                                    ProtosIntegerValue.class,
                                    assertCompletedObservationValue(secondCase))
                            .value());
        }
    }

    @Test
    void selectorMismatchIsRejectedWithoutExecutingTheBody(@TempDir Path root)
            throws Exception {
        writeSuite(root, SOURCE_TWO_TESTS);

        ManualSubmission submission = new ManualSubmission();
        ProtosModuleResolver resolver = packageResolver();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestLogicalCaseExecutionFacility facility =
                        ProtosTestLogicalCaseExecutionFacility.install(
                                activation,
                                "packageLogicalCaseExecutionAsync",
                                CORE,
                                resolver,
                                List.of(
                                        new ProtosTestToolFileSelectionFacility.CorpusSourceRoot(
                                                "package-corpus", root)),
                                runtimeHost,
                                submission)) {

            ProtosFutureValue future =
                    invoke(
                            facility,
                            prelude,
                            activation,
                            SOURCE_TWO_TESTS,
                            List.of("a", "b"),
                            "doesNotExist");

            assertTrue(submission.runNext());
            assertTrue(activation.executionDomain().dispatchOne());

            assertEquals("rematerialization-error", phaseOf(future));
        }
    }

    @Test
    void signatureMismatchIsRejectedWithoutExecutingTheBody(@TempDir Path root)
            throws Exception {
        writeSuite(root, SOURCE_TWO_TESTS);

        ManualSubmission submission = new ManualSubmission();
        ProtosModuleResolver resolver = packageResolver();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestLogicalCaseExecutionFacility facility =
                        ProtosTestLogicalCaseExecutionFacility.install(
                                activation,
                                "packageLogicalCaseExecutionAsync",
                                CORE,
                                resolver,
                                List.of(
                                        new ProtosTestToolFileSelectionFacility.CorpusSourceRoot(
                                                "package-corpus", root)),
                                runtimeHost,
                                submission)) {

            ProtosFutureValue future =
                    invoke(
                            facility,
                            prelude,
                            activation,
                            SOURCE_TWO_TESTS,
                            // declared order is a, b: this expects the opposite order.
                            List.of("b", "a"),
                            "a");

            assertTrue(submission.runNext());
            assertTrue(activation.executionDomain().dispatchOne());

            assertEquals("rematerialization-error", phaseOf(future));
        }
    }

    private static void writeSuite(Path root, String source) throws Exception {
        Files.writeString(root.resolve("suite.protos"), source, StandardCharsets.UTF_8);
    }

    private static String phaseOf(ProtosFutureValue future) {
        ProtosObjectValue completion =
                assertInstanceOf(ProtosObjectValue.class, future.resolvedValue().orElseThrow());
        return assertInstanceOf(
                        ProtosStringValue.class, completion.readLocalSlot("phase").orElseThrow())
                .value();
    }

    private static Object assertCompletedObservationValue(ProtosFutureValue future) {
        ProtosObjectValue completion =
                assertInstanceOf(ProtosObjectValue.class, future.resolvedValue().orElseThrow());
        ProtosStringValue phase =
                assertInstanceOf(
                        ProtosStringValue.class, completion.readLocalSlot("phase").orElseThrow());
        assertEquals("case-execution", phase.value());

        ProtosObjectValue observation =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        completion.readLocalSlot("observation").orElseThrow());
        ProtosStringValue state =
                assertInstanceOf(
                        ProtosStringValue.class, observation.readLocalSlot("state").orElseThrow());
        assertEquals("completed", state.value());

        return observation.readLocalSlot("value").orElseThrow();
    }

    private static ProtosFutureValue invoke(
            ProtosTestLogicalCaseExecutionFacility facility,
            ProtosPrelude prelude,
            ProtosActivation activation,
            String source,
            List<String> signature,
            String selector) {
        Object execution =
                activation
                        .context()
                        .readLocalSlot("packageLogicalCaseExecutionAsync")
                        .orElseThrow();

        ProtosArrayValue sourceAssociation =
                prelude.newFrozenArray(
                        List.of(
                                new ProtosStringValue("package-corpus"),
                                new ProtosStringValue("suite.protos")));

        ProtosArrayValue signatureValue =
                prelude.newFrozenArray(
                        signature.stream()
                                .map(ProtosStringValue::new)
                                .map(value -> (Object) value)
                                .toList());

        return assertInstanceOf(
                ProtosFutureValue.class,
                ProtosInvocation.invoke(
                        execution,
                        List.of(
                                sourceAssociation,
                                new ProtosStringValue(source),
                                signatureValue,
                                new ProtosStringValue(selector)),
                        activation));
    }

    private static ProtosModuleResolver packageResolver() {
        ProtosModuleResolver standardLibraryResolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        // Matches ProtosCli's packageLogicalCaseFallbackResolver construction: the
        // ordinary bundled Test Tool resolver is the base (its "self:Discovery"
        // selection machinery is what the suite-native bridge always needs), overlaid
        // with the exact Package Tool modules the real TOML corpus depends on: the
        // RuntimeNames proof-of-bootstrap module, the three Package-owned TOML modules
        // (TomlSyntax/TomlDocument/ManifestSchemaV1), and the shared Toml10 modules they
        // transitively import. The shared modules keep the same canonical
        // "bundled-tool-shared:" ModuleKey that ProtosBundledToolModuleResolver would
        // itself assign for the same specifier.
        ProtosModuleResolver ordinaryTestResolver =
                new ProtosBundledToolModuleResolver(
                        "test", TOOL_ROOT, SHARED_ROOT, standardLibraryResolver);
        return new ProtosExactModuleOverlayResolver(
                Map.of(
                        "package-runtime-names",
                        new ProtosExactModuleOverlayResolver.ExactModule(
                                new ProtosModuleKey("tool001-package:runtime-names"),
                                PACKAGE_TOOL_ROOT.resolve("RuntimeNames.protos")),
                        "self:TomlSyntax",
                        new ProtosExactModuleOverlayResolver.ExactModule(
                                new ProtosModuleKey("tool001-package:toml-syntax"),
                                PACKAGE_TOOL_ROOT.resolve("TomlSyntax.protos")),
                        "self:TomlDocument",
                        new ProtosExactModuleOverlayResolver.ExactModule(
                                new ProtosModuleKey("tool001-package:toml-document"),
                                PACKAGE_TOOL_ROOT.resolve("TomlDocument.protos")),
                        "self:ManifestSchemaV1",
                        new ProtosExactModuleOverlayResolver.ExactModule(
                                new ProtosModuleKey("tool001-package:manifest-schema-v1"),
                                PACKAGE_TOOL_ROOT.resolve("ManifestSchemaV1.protos")),
                        "tool-shared:Toml10/TomlSyntax",
                        new ProtosExactModuleOverlayResolver.ExactModule(
                                new ProtosModuleKey("bundled-tool-shared:Toml10/TomlSyntax"),
                                SHARED_ROOT.resolve("Toml10").resolve("TomlSyntax.protos")),
                        "tool-shared:Toml10/TomlDocument",
                        new ProtosExactModuleOverlayResolver.ExactModule(
                                new ProtosModuleKey("bundled-tool-shared:Toml10/TomlDocument"),
                                SHARED_ROOT.resolve("Toml10").resolve("TomlDocument.protos"))),
                ordinaryTestResolver);
    }

    private static final class ManualSubmission
            implements ProtosAsyncExactExecutionFacility.Submission {
        private final ArrayDeque<Job> jobs = new ArrayDeque<>();

        @Override
        public synchronized ProtosAsyncExactExecutionFacility.Submitted submit(Runnable work) {
            Job job = new Job(Objects.requireNonNull(work, "work"));
            jobs.addLast(job);
            return job;
        }

        synchronized int queuedCount() {
            return jobs.size();
        }

        boolean runNext() {
            Job job;

            synchronized (this) {
                job = jobs.pollFirst();
            }

            return job != null && job.runIfAccepted();
        }

        private static final class Job implements ProtosAsyncExactExecutionFacility.Submitted {
            private final Runnable work;
            private boolean started;
            private boolean cancelled;

            private Job(Runnable work) {
                this.work = work;
            }

            @Override
            public synchronized boolean cancelIfNotStarted() {
                if (started || cancelled) {
                    return false;
                }

                cancelled = true;
                return true;
            }

            synchronized boolean runIfAccepted() {
                if (started || cancelled) {
                    return false;
                }

                started = true;
                work.run();
                return true;
            }
        }
    }
}

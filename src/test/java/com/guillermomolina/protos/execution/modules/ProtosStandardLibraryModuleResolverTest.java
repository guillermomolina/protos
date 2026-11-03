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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtosStandardLibraryModuleResolverTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @TempDir Path libraryRoot;

    @Test
    void canonicalModuleIdentityIsIndependentOfImporterAndInstallationRoot()
            throws Exception {
        Path otherRoot = Files.createTempDirectory("protos-stdlib-other-");
        try {
            writeModule(libraryRoot, "collections/probe", "this.value: 1");
            writeModule(otherRoot, "collections/probe", "this.value: 2");

            ProtosStandardLibraryModuleResolver first =
                    new ProtosStandardLibraryModuleResolver(libraryRoot);
            ProtosStandardLibraryModuleResolver second =
                    new ProtosStandardLibraryModuleResolver(otherRoot);
            Optional<ProtosModuleKey> importer =
                    Optional.of(ProtosModuleKey.source("file:/application/main.protos"));

            ProtosModuleKey fromTopLevel =
                    first.resolve("std:collections/probe", Optional.empty());
            ProtosModuleKey fromImporter =
                    first.resolve("std:collections/probe", importer);
            ProtosModuleKey afterRelocation =
                    second.resolve("std:collections/probe", Optional.empty());

            assertEquals(ProtosModuleKey.module("std:collections/probe"), fromTopLevel);
            assertEquals(fromTopLevel, fromImporter);
            assertEquals(fromTopLevel, afterRelocation);
        } finally {
            deleteTree(otherRoot);
        }
    }

    @Test
    void moduleRouteLoadsExactlyItsDirectoryInitializerAsUtf8() throws Exception {
        writeModule(libraryRoot, "collections/text_probe", "this.value: \"olá\"");
        writeFile(libraryRoot, "collections/text_probe.protos", "neighbour: 1");
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(libraryRoot);

        ProtosModuleKey key =
                resolver.resolve("std:collections/text_probe", Optional.empty());
        assertEquals(ProtosModuleKey.Kind.MODULE, key.kind());
        assertEquals("this.value: \"olá\"", resolver.loadSource(key).characters());
    }

    @Test
    void directoriesWithoutInitializerAndLegacyFilesAreNotModules() throws Exception {
        writeFile(libraryRoot, "collections/Probe.protos", "value: 1");
        writeFile(libraryRoot, "collections/empty/Helper.protos", "value: 1");
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(libraryRoot);

        for (String specifier :
                List.of(
                        "std:collections/Probe",
                        "std:collections/probe",
                        "std:collections/empty",
                        "std:collections/empty/Helper",
                        "std:collections/missing")) {
            assertThrows(
                    IOException.class,
                    () -> resolver.resolve(specifier, Optional.empty()),
                    specifier);
        }
    }

    @Test
    void moduleRoutesRequireLowercaseExactSpelling() throws Exception {
        writeModule(libraryRoot, "collections/probe", "this.value: 1");
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(libraryRoot);

        assertEquals(
                ProtosModuleKey.module("std:collections/probe"),
                resolver.resolve("std:collections/probe", Optional.empty()));
        assertThrows(
                IOException.class,
                () -> resolver.resolve("std:collections/Probe", Optional.empty()));
        assertThrows(
                IOException.class,
                () -> resolver.resolve("std:Collections/probe", Optional.empty()));
    }

    @Test
    void relativeSourceImportsResolveExactFilesFromTheImporterLocation() throws Exception {
        writeModule(libraryRoot, "collections/array", "helper: import(\"../internal/Bulk.protos\")");
        writeFile(libraryRoot, "collections/internal/Bulk.protos", "value: 1");
        writeFile(libraryRoot, "collections/internal/Other.protos", "value: 2");
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(libraryRoot);
        ProtosModuleKey module = resolver.resolve("std:collections/array", Optional.empty());

        ProtosModuleKey source =
                resolver.resolve("../internal/Bulk.protos", Optional.of(module));
        assertEquals(ProtosModuleKey.source("std:collections/internal/Bulk.protos"), source);
        assertEquals("value: 1", resolver.loadSource(source).characters());
        assertEquals(
                ProtosModuleKey.source("std:collections/internal/Other.protos"),
                resolver.resolve("./Other.protos", Optional.of(source)));
        assertNotEquals(module, ProtosModuleKey.source("std:collections/array"));
    }

    @Test
    void sourceAliasesOfInitializersTraversalAndCaseVariantsAreRejected() throws Exception {
        writeModule(libraryRoot, "collections/array", "value: 1");
        writeFile(libraryRoot, "collections/internal/Bulk.protos", "value: 1");
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(libraryRoot);
        Optional<ProtosModuleKey> importer =
                Optional.of(resolver.resolve("std:collections/array", Optional.empty()));

        for (String specifier :
                List.of(
                        "./module.protos",
                        "../array/module.protos",
                        "../internal/bulk.protos",
                        "../internal/Bulk",
                        "../../../outside.protos",
                        "../../core/Object.protos")) {
            assertThrows(
                    IOException.class,
                    () -> resolver.resolve(specifier, importer),
                    specifier);
        }
        assertThrows(
                IOException.class,
                () -> resolver.resolve("./Bulk.protos", Optional.empty()),
                "relative SOURCE import requires an importer");
    }

    @Test
    void symlinkEscapingTheDistributionRootIsRejected() throws Exception {
        Path outside = Files.createTempDirectory("protos-stdlib-outside-");
        try {
            writeFile(outside, "Escape.protos", "value: 1");
            writeModule(libraryRoot, "collections/array", "value: 1");
            Path link = libraryRoot.resolve("collections/array/Escape.protos");
            try {
                Files.createSymbolicLink(link, outside.resolve("Escape.protos"));
            } catch (UnsupportedOperationException | IOException unsupported) {
                return; // This host filesystem cannot create the symlink.
            }
            ProtosStandardLibraryModuleResolver resolver =
                    new ProtosStandardLibraryModuleResolver(libraryRoot);
            Optional<ProtosModuleKey> importer =
                    Optional.of(resolver.resolve("std:collections/array", Optional.empty()));
            assertThrows(
                    IOException.class,
                    () -> resolver.resolve("./Escape.protos", importer));
        } finally {
            deleteTree(outside);
        }
    }

    @Test
    void caseFoldEquivalentSiblingNamesAreRejectedAsAmbiguous() throws Exception {
        writeModule(libraryRoot, "collections/probe", "value: 1");
        writeFile(libraryRoot, "collections/Probe/module.protos", "value: 2");
        Path collections = libraryRoot.resolve("collections");
        long foldedMatches;
        try (var children = Files.list(collections)) {
            foldedMatches =
                    children
                            .filter(child -> child.getFileName().toString().equalsIgnoreCase("probe"))
                            .count();
        }
        if (foldedMatches < 2) {
            return; // This host filesystem cannot materialize the invalid collision.
        }

        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(libraryRoot);
        assertThrows(
                IOException.class,
                () -> resolver.resolve("std:collections/probe", Optional.empty()));
    }

    @Test
    void reservedCoreAndWindowsDeviceSegmentsAreRejected() {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(libraryRoot);

        for (String specifier :
                List.of(
                        "std:core/object",
                        "std:Core/Object",
                        "std:CORE/Object",
                        "std:collections/NUL",
                        "std:collections/nul",
                        "std:collections/com1")) {
            IOException failure =
                    assertThrows(
                            IOException.class,
                            () -> resolver.resolve(specifier, Optional.empty()),
                            specifier);
            assertEquals("invalid standard-library module name", failure.getMessage());
        }
    }

    @Test
    void invalidOrNonStandardSpellingsNeverFallBackToSearchPaths() {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(libraryRoot);
        List<String> invalid =
                List.of(
                        "collections/set",
                        "std:",
                        "std:collections/set.protos",
                        "std:collections/../set",
                        "std:/collections/set",
                        "std:collections//set",
                        "std:collections/set-name",
                        "std:collections\\set");

        for (String specifier : invalid) {
            assertThrows(
                    IOException.class,
                    () -> resolver.resolve(specifier, Optional.empty()),
                    specifier);
        }
    }

    @Test
    void importAnswersThePublicObjectCachedPerActor() throws Exception {
        writeModule(libraryRoot, "collections/probe", "hidden: 3\nthis.value: 7");
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(libraryRoot);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation actorA = prelude.newModuleActivation();
        ProtosActivation actorB = prelude.newModuleActivation();

        ProtosObjectValue first =
                (ProtosObjectValue)
                        ProtosTestExecutionSupport.evaluate(
                                "import(\"std:collections/probe\")",
                                actorA);
        ProtosObjectValue repeated =
                (ProtosObjectValue)
                        ProtosTestExecutionSupport.evaluate(
                                "import(\"std:collections/probe\")",
                                actorA);
        ProtosObjectValue otherActor =
                (ProtosObjectValue)
                        ProtosTestExecutionSupport.evaluate(
                                "import(\"std:collections/probe\")",
                                actorB);

        assertSame(first, repeated);
        assertNotSame(first, otherActor);
        assertFalse(first.hasLocalSlot("hidden"));
        Long value =
                (Long) first.readLocalSlot("value").orElseThrow();
        assertEquals("7", com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value));
    }

    @Test
    void ffiIsAnOrdinaryModuleWithPrivateMetadataAndNoProviderRequirement() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosModuleKey key = resolver.resolve("std:ffi", Optional.empty());
        assertEquals(ProtosModuleKey.module("std:ffi"), key);
        assertEquals(
                STANDARD_LIBRARY.resolve("ffi/module.protos").toRealPath(),
                resolver.loadSource(key).physicalPath().orElseThrow().toRealPath());
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation actor = prelude.newModuleActivation();
        ProtosObjectValue module = (ProtosObjectValue)
                ProtosTestExecutionSupport.evaluate("import(\"std:ffi\")", actor);
        assertEquals(12, module.localSlotsSnapshot().size());
        for (String name : List.of("void", "i32", "i64", "f64", "sizeT", "utf8z",
                "opaque", "nullable", "owned", "out", "bind", "release")) {
            org.junit.jupiter.api.Assertions.assertTrue(module.hasLocalSlot(name), name);
        }
        for (String name : List.of("descriptors", "owners", "Interop", "Arrays", "nativeType")) {
            assertFalse(module.hasLocalSlot(name), name);
        }
        assertSame(module, ProtosTestExecutionSupport.evaluate("import(\"std:ffi\")", actor));
    }

    @Test
    void missingStandardModuleBecomesTheExistingCoreImportError() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(libraryRoot);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosTestExecutionSupport.evaluate(
                                "import(\"std:collections/missing\")",
                                prelude.newModuleActivation()));
    }

    @Test
    void distributedLibraryPublishesEveryMigratedRouteAsModuleDirectory() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        for (String route :
                List.of(
                        "std:collections/array", "std:collections/set",
                        "std:collections/identity_set", "std:collections/range",
                        "std:datetime/date", "std:datetime/local_date_time",
                        "std:datetime/iso8601", "std:math/linear_algebra",
                        "std:semver/sem_ver", "std:network/ip_addresses",
                        "std:cli/command_line", "std:tls/client", "std:http/server",
                        "std:http/url_client", "std:http/internal/host_worker",
                        "std:test/assertions", "std:test/test", "std:runtime",
                        "std:inspect", "std:interop", "std:ffi")) {
            ProtosModuleKey key = resolver.resolve(route, Optional.empty());
            assertEquals(ProtosModuleKey.module(route), key);
            assertNotNull(resolver.loadSource(key).physicalPath().orElseThrow());
        }
        for (String obsolete :
                List.of(
                        "std:collections/Array", "std:collections/Set",
                        "std:datetime/Date", "std:http/Server", "std:test/Assertions")) {
            assertThrows(
                    IOException.class,
                    () -> resolver.resolve(obsolete, Optional.empty()),
                    obsolete);
        }
    }

    @Test
    void d242AllDistributedPublicRoutesHaveExactModuleInitializers() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        java.util.List<Path> initializers;
        try (var walk = Files.walk(STANDARD_LIBRARY)) {
            initializers =
                    walk.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().equals("module.protos"))
                            .sorted()
                            .toList();
        }
        org.junit.jupiter.api.Assertions.assertTrue(
                initializers.size() >= 55,
                "the distributed D242 public modules must be present");
        for (Path initializer : initializers) {
            String logicalName =
                    STANDARD_LIBRARY.relativize(initializer.getParent())
                            .toString()
                            .replace(java.io.File.separatorChar, '/');
            String specifier = "std:" + logicalName;
            ProtosModuleKey key = resolver.resolve(specifier, Optional.empty());
            assertEquals(ProtosModuleKey.module(specifier), key, specifier);
            assertEquals(ProtosModuleKey.Kind.MODULE, key.kind(), specifier);
            assertEquals(
                    initializer.toRealPath(),
                    resolver.loadSource(key).physicalPath().orElseThrow().toRealPath(),
                    "a public MODULE must select exactly its module.protos initializer");
            assertThrows(
                    IOException.class,
                    () -> resolver.resolve("./module.protos", Optional.of(key)),
                    "a MODULE initializer must not be importable as SOURCE: " + specifier);
        }
    }

    @Test
    void distributedLibraryHasNoCaseFoldEquivalentSiblingNames() throws Exception {
        assertNoCaseFoldEquivalentSiblings(STANDARD_LIBRARY);
    }

    private static void assertNoCaseFoldEquivalentSiblings(Path directory) throws IOException {
        Map<String, Path> seen = new HashMap<>();
        try (var children = Files.list(directory)) {
            for (Path child : children.toList()) {
                String folded =
                        child.getFileName().toString().toLowerCase(Locale.ROOT);
                Path previous = seen.putIfAbsent(folded, child);
                if (previous != null) {
                    throw new AssertionError(
                            "case-fold-equivalent distribution siblings: "
                                    + previous
                                    + " and "
                                    + child);
                }
                assertNotNull(child.getFileName());
                if (Files.isDirectory(child)) {
                    assertNoCaseFoldEquivalentSiblings(child);
                }
            }
        }
    }

    private static void writeModule(Path root, String logicalName, String source)
            throws IOException {
        writeFile(root, logicalName + "/module.protos", source);
    }

    private static void writeFile(Path root, String relativePath, String source)
            throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted((left, right) -> right.compareTo(left))
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException failure) {
                                    throw new RuntimeException(failure);
                                }
                            });
        } catch (RuntimeException failure) {
            if (failure.getCause() instanceof IOException) {
                throw (IOException) failure.getCause();
            }
            throw failure;
        }
    }
}

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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtosStandardLibraryModuleResolverTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @TempDir Path libraryRoot;

    @Test
    void canonicalIdentityIsLogicalAndIndependentOfImporterAndInstallationRoot()
            throws Exception {
        Path otherRoot = Files.createTempDirectory("protos-stdlib-other-");
        try {
            writeModule(libraryRoot, "collections/probe", "value: 1");
            writeModule(otherRoot, "collections/probe", "value: 2");

            ProtosStandardLibraryModuleResolver first =
                    new ProtosStandardLibraryModuleResolver(libraryRoot);
            ProtosStandardLibraryModuleResolver second =
                    new ProtosStandardLibraryModuleResolver(otherRoot);
            Optional<ProtosModuleKey> importer =
                    Optional.of(new ProtosModuleKey("file:/application/main.protos"));

            ProtosModuleKey fromTopLevel = first.resolve("std:collections/probe", Optional.empty());
            ProtosModuleKey fromImporter = first.resolve("std:collections/probe", importer);
            ProtosModuleKey afterRelocation = second.resolve("std:collections/probe", Optional.empty());

            assertEquals(new ProtosModuleKey("std:collections/probe"), fromTopLevel);
            assertEquals(fromTopLevel, fromImporter);
            assertEquals(fromTopLevel, afterRelocation);
        } finally {
            deleteTree(otherRoot);
        }
    }

    @Test
    void sourceLookupUsesHiddenProtosExtensionAndUtf8() throws Exception {
        writeModule(libraryRoot, "collections/text_probe", "value: \"olá\"");
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(libraryRoot);

        ProtosModuleKey key = resolver.resolve("std:collections/text_probe", Optional.empty());
        assertEquals("value: \"olá\"", resolver.loadSource(key));
    }

    @Test
    void invalidOrNonStandardSpellingsNeverFallBackToSearchPaths() {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(libraryRoot);
        List<String> invalid =
                List.of(
                        "collections/set",
                        "std:",
                        "std:core",
                        "std:core/object",
                        "std:collections/set.protos",
                        "std:collections/../set",
                        "std:/collections/set",
                        "std:collections//set",
                        "std:Collections/set",
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
    void ordinaryImportCachingAndActorLocalInstancesUseTheCanonicalStandardKey()
            throws Exception {
        writeModule(libraryRoot, "collections/probe", "value: 7");
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(libraryRoot);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosSourceCompiler compiler = new ProtosSourceCompiler();
        ProtosActivation actorA = prelude.newModuleActivation();
        ProtosActivation actorB = prelude.newModuleActivation();

        ProtosObjectValue first =
                (ProtosObjectValue)
                        compiler.compile("import(\"std:collections/probe\")").call(actorA);
        ProtosObjectValue repeated =
                (ProtosObjectValue)
                        compiler.compile("import(\"std:collections/probe\")").call(actorA);
        ProtosObjectValue otherActor =
                (ProtosObjectValue)
                        compiler.compile("import(\"std:collections/probe\")").call(actorB);

        assertSame(first, repeated);
        assertNotSame(first, otherActor);
        ProtosIntegerValue value =
                (ProtosIntegerValue) first.readLocalSlot("value").orElseThrow();
        assertEquals(BigInteger.valueOf(7), value.value());
    }

    @Test
    void missingStandardModuleBecomesTheExistingCoreImportError() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(libraryRoot);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        assertThrows(
                ProtosSignalException.class,
                () ->
                        new ProtosSourceCompiler()
                                .compile("import(\"std:collections/missing\")")
                                .call(prelude.newModuleActivation()));
    }

    private static void writeModule(Path root, String logicalName, String source)
            throws IOException {
        Path file = root.resolve(logicalName + ".protos");
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

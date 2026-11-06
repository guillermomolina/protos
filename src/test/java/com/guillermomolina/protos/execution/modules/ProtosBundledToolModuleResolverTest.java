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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosBundledToolModuleResolverTest {
    @TempDir Path toolRoot;

    @Test
    void entriesAndHelpersAreExactSourcesReachedOnlyRelatively() throws Exception {
        writeModule("Main", "Helper: import(\"./Helper.protos\")");
        writeModule("Helper", "banner: \"hello\"");
        writeModule("nested/Deep", "Up: import(\"../Helper.protos\")");

        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "package", toolRoot, ProtosModuleResolver.rejecting());

        ProtosModuleKey entry = resolver.entryModule("Main");
        ProtosModuleKey helperFromEntry =
                resolver.resolve("./Helper.protos", Optional.of(entry));
        ProtosModuleKey deep =
                resolver.resolve("./nested/Deep.protos", Optional.of(entry));
        ProtosModuleKey helperFromDeep =
                resolver.resolve("../Helper.protos", Optional.of(deep));

        assertEquals(ProtosModuleKey.source("bundled-tool:package/Main.protos"), entry);
        assertEquals(ProtosModuleKey.source("bundled-tool:package/Helper.protos"), helperFromEntry);
        assertEquals(helperFromEntry, helperFromDeep);
        assertEquals("banner: \"hello\"", resolver.loadSource(helperFromEntry).characters());
        assertEquals(
                ProtosModuleKey.source("bundled-tool:package/Helper.protos"),
                ProtosBundledToolModuleResolver.toolSourceKey("package", "Helper"));

        // No ambient access: neither self: nor a relative import works without a tool importer.
        assertThrows(IOException.class, () -> resolver.resolve("self:Helper", Optional.empty()));
        assertThrows(IOException.class, () -> resolver.resolve("./Helper.protos", Optional.empty()));
        // The imported std: SOURCE route is delegated unchanged to the supplied std resolver.
        // The rejecting test double signals a generic Exception rather than IOException.
        Exception delegatedFailure =
                assertThrows(
                        Exception.class,
                        () -> resolver.resolve(
                                "./Helper.protos",
                                Optional.of(ProtosModuleKey.module("std:collections/array"))));
        assertEquals("no host module resolver configured", delegatedFailure.getMessage());
        // Helpers are SOURCEs, never MODULEs; self: names only published module directories.
        assertThrows(IOException.class, () -> resolver.resolve("self:Helper", Optional.of(entry)));
        assertThrows(IOException.class, () -> resolver.resolve("../Escape.protos", Optional.of(entry)));
        assertThrows(IOException.class, () -> resolver.resolve("./helper.protos", Optional.of(entry)));
        assertThrows(IOException.class, () -> resolver.resolve("./Helper", Optional.of(entry)));
        assertThrows(IOException.class, () -> resolver.resolve("dep:any/Helper", Optional.empty()));
        assertThrows(IOException.class, () -> resolver.resolve("tool:package/Helper", Optional.empty()));
        assertThrows(IOException.class, () -> resolver.resolve("self:../Helper", Optional.of(entry)));
    }

    @Test
    void publishedToolModuleIsAMandatoryInitializerDirectoryNeverAliasedAsSource()
            throws Exception {
        writeFile("main/module.protos", "this.toolId: \"T\"");
        writeModule("Verify", "formatter: import(\"self:main\")");

        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "formatter", toolRoot, ProtosModuleResolver.rejecting());
        ProtosModuleKey entry = resolver.entryModule("Verify");
        ProtosModuleKey module = resolver.resolve("self:main", Optional.of(entry));

        assertEquals(ProtosModuleKey.module("bundled-tool:formatter/main"), module);
        assertEquals("this.toolId: \"T\"", resolver.loadSource(module).characters());
        assertThrows(
                IOException.class,
                () -> resolver.resolve("./main/module.protos", Optional.of(entry)));
        assertThrows(IOException.class, () -> resolver.entryModule("main/module"));
        assertThrows(IOException.class, () -> resolver.resolve("self:missing", Optional.of(entry)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosBundledToolModuleResolver.toolSourceKey("formatter", "main/module"));
    }

    @Test
    void distributedFormatterPublishesItsMainModule() throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "formatter",
                        Path.of("protos", "tools", "formatter"),
                        ProtosModuleResolver.rejecting());
        for (String entryName : java.util.List.of("Verify", "AcceptSourceLayout", "FormatStructural")) {
            ProtosModuleKey entry = resolver.entryModule(entryName);
            ProtosModuleKey module = resolver.resolve("self:main", Optional.of(entry));
            assertEquals(ProtosModuleKey.module("bundled-tool:formatter/main"), module);
            String initializer = resolver.loadSource(module).characters();
            org.junit.jupiter.api.Assertions.assertTrue(initializer.contains("this.toolId:"));
            org.junit.jupiter.api.Assertions.assertTrue(initializer.contains("this.acceptSourceLayout:"));
            org.junit.jupiter.api.Assertions.assertTrue(initializer.contains("this.formatStructural:"));
            org.junit.jupiter.api.Assertions.assertTrue(
                    initializer.contains("Structural: import(\"../Structural.protos\")"));
        }
    }

    @Test
    void privateSharedBootstrapUsesStableIdentityAndPreservesSelfConfinement() throws Exception {
        Path packageRoot = toolRoot.resolve("package-root");
        Path testRoot = toolRoot.resolve("test-root");
        Path sharedRoot = toolRoot.resolve("shared-root");
        writeModuleAt(packageRoot, "Main", "value: 1");
        writeModuleAt(testRoot, "Main", "value: 2");
        writeModuleAt(sharedRoot, "Probe", "answer: 42");
        writeModuleAt(sharedRoot, "Helper", "name: \"shared\"");

        ProtosBundledToolModuleResolver packageResolver =
                new ProtosBundledToolModuleResolver(
                        "package", packageRoot, sharedRoot, ProtosModuleResolver.rejecting());
        ProtosBundledToolModuleResolver testResolver =
                new ProtosBundledToolModuleResolver(
                        "test", testRoot, sharedRoot, ProtosModuleResolver.rejecting());

        ProtosModuleKey packageEntry = packageResolver.entryModule("Main");
        ProtosModuleKey testEntry = testResolver.entryModule("Main");
        ProtosModuleKey packageProbe =
                packageResolver.resolve("tool-shared:Probe", Optional.of(packageEntry));
        ProtosModuleKey testProbe =
                testResolver.resolve("tool-shared:Probe", Optional.of(testEntry));

        assertEquals(ProtosModuleKey.source("bundled-tool-shared:Probe.protos"), packageProbe);
        assertEquals(packageProbe, testProbe);
        assertEquals("answer: 42", packageResolver.loadSource(packageProbe).characters());
        assertEquals("answer: 42", testResolver.loadSource(testProbe).characters());

        ProtosModuleKey helper =
                packageResolver.resolve("tool-shared:Helper", Optional.of(packageProbe));
        assertEquals(ProtosModuleKey.source("bundled-tool-shared:Helper.protos"), helper);

        assertThrows(
                IOException.class,
                () -> packageResolver.resolve("self:Helper", Optional.of(packageProbe)));
        assertThrows(
                IOException.class,
                () -> packageResolver.resolve("tool-shared:Probe", Optional.empty()));
        assertThrows(
                IOException.class,
                () ->
                        packageResolver.resolve(
                                "tool-shared:Probe",
                                Optional.of(ProtosModuleKey.module("std:collections/array"))));
        assertThrows(
                IOException.class,
                () -> packageResolver.resolve("tool-shared:probe", Optional.of(packageEntry)));

        ProtosBundledToolModuleResolver unconfigured =
                new ProtosBundledToolModuleResolver(
                        "package", packageRoot, ProtosModuleResolver.rejecting());
        assertThrows(
                IOException.class,
                () -> unconfigured.resolve("tool-shared:Probe", Optional.of(packageEntry)));
    }

    @Test
    void standardRequestsDelegateWithoutTurningStdIntoToolIdentity() throws Exception {
        ProtosModuleResolver standard =
                new ProtosModuleResolver() {
                    @Override
                    public ProtosModuleKey resolve(
                            String exactSpecifier, Optional<ProtosModuleKey> importingModule) {
                        assertEquals("std:probe/Module", exactSpecifier);
                        return ProtosModuleKey.module("std:probe/Module");
                    }

                    @Override
                    public ProtosModuleSource loadSource(ProtosModuleKey key) {
                        assertEquals(ProtosModuleKey.module("std:probe/Module"), key);
                        return ProtosModuleSource.fromCharacters(key, "value: 7");
                    }
                };
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver("package", toolRoot, standard);

        ProtosModuleKey key = resolver.resolve("std:probe/Module", Optional.empty());
        assertEquals(ProtosModuleKey.module("std:probe/Module"), key);
        assertEquals("value: 7", resolver.loadSource(key).characters());
    }

    private static void writeModuleAt(Path root, String logicalName, String source)
            throws IOException {
        Path path =
                root.resolve(
                        logicalName.replace('/', java.io.File.separatorChar) + ".protos");
        Files.createDirectories(path.getParent());
        Files.writeString(path, source, StandardCharsets.UTF_8);
    }

    private void writeFile(String relativePath, String source) throws IOException {
        Path path = toolRoot.resolve(relativePath.replace('/', java.io.File.separatorChar));
        Files.createDirectories(path.getParent());
        Files.writeString(path, source, StandardCharsets.UTF_8);
    }

    private void writeModule(String logicalName, String source) throws IOException {
        Path path =
                toolRoot.resolve(
                        logicalName.replace('/', java.io.File.separatorChar) + ".protos");
        Files.createDirectories(path.getParent());
        Files.writeString(path, source, StandardCharsets.UTF_8);
    }
}

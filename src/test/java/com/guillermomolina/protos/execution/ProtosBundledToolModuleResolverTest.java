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

import com.guillermomolina.protos.runtime.ProtosModuleKey;
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
    void selfClosureUsesStableInternalKeysAndRejectsAmbientSpecifiers() throws Exception {
        writeModule("Main", "Helper: import(\"self:Helper\")");
        writeModule("Helper", "banner: \"hello\"");

        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "package", toolRoot, ProtosModuleResolver.rejecting());

        ProtosModuleKey entry = resolver.entryModule("Main");
        ProtosModuleKey helperFromEntry =
                resolver.resolve("self:Helper", Optional.empty());
        ProtosModuleKey helperFromHelper =
                resolver.resolve("self:Helper", Optional.of(helperFromEntry));

        assertEquals(new ProtosModuleKey("bundled-tool:package/Main"), entry);
        assertEquals(new ProtosModuleKey("bundled-tool:package/Helper"), helperFromEntry);
        assertEquals(helperFromEntry, helperFromHelper);
        assertEquals("banner: \"hello\"", resolver.loadSource(helperFromEntry));

        assertThrows(
                IOException.class,
                () ->
                        resolver.resolve(
                                "self:Helper",
                                Optional.of(new ProtosModuleKey("std:collections/Array"))));
        assertThrows(
                IOException.class,
                () -> resolver.resolve("dep:any/Helper", Optional.empty()));
        assertThrows(
                IOException.class,
                () -> resolver.resolve("tool:package/Helper", Optional.empty()));
        assertThrows(
                IOException.class,
                () -> resolver.resolve("self:../Helper", Optional.empty()));
    }

    @Test
    void standardRequestsDelegateWithoutTurningStdIntoToolIdentity() throws Exception {
        ProtosModuleResolver standard =
                new ProtosModuleResolver() {
                    @Override
                    public ProtosModuleKey resolve(
                            String exactSpecifier, Optional<ProtosModuleKey> importingModule) {
                        assertEquals("std:probe/Module", exactSpecifier);
                        return new ProtosModuleKey("std:probe/Module");
                    }

                    @Override
                    public String loadSource(ProtosModuleKey key) {
                        assertEquals(new ProtosModuleKey("std:probe/Module"), key);
                        return "value: 7";
                    }
                };
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver("package", toolRoot, standard);

        ProtosModuleKey key = resolver.resolve("std:probe/Module", Optional.empty());
        assertEquals(new ProtosModuleKey("std:probe/Module"), key);
        assertEquals("value: 7", resolver.loadSource(key));
    }

    private void writeModule(String logicalName, String source) throws IOException {
        Path path =
                toolRoot.resolve(
                        logicalName.replace('/', java.io.File.separatorChar) + ".protos");
        Files.createDirectories(path.getParent());
        Files.writeString(path, source, StandardCharsets.UTF_8);
    }
}

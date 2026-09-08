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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.oracle.truffle.api.RootCallTarget;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtosModuleSourceIdentityTest {
    @TempDir Path root;

    @Test
    void fileBackedResolverRetainsCanonicalKeyExactCharactersAndFileUri() throws Exception {
        Path collections = Files.createDirectories(root.resolve("collections"));
        Path file = collections.resolve("Probe.protos");
        Files.writeString(file, "value: \"olá\"", StandardCharsets.UTF_8);

        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(root);
        ProtosModuleKey key = resolver.resolve("std:collections/Probe", Optional.empty());
        ProtosModuleSource moduleSource = resolver.loadSource(key);

        assertEquals(key, moduleSource.key());
        assertEquals("value: \"olá\"", moduleSource.characters());
        assertEquals(file.toAbsolutePath().normalize().toUri(), moduleSource.source().getURI());
        assertEquals(ProtosLanguage.ID, moduleSource.source().getLanguage());
    }

    @Test
    void sourceOnlyStagingCompilationRetainsTheExactModuleSourceOnTheRoot() {
        ProtosModuleKey key = new ProtosModuleKey("test:source-owned");
        com.oracle.truffle.api.source.Source source =
                com.oracle.truffle.api.source.Source
                        .newBuilder(ProtosLanguage.ID, "1", "source-owned.protos")
                        .uri(URI.create("memory:///source-owned.protos"))
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        ProtosModuleSource moduleSource = new ProtosModuleSource(key, source);

        RootCallTarget target =
                (RootCallTarget) new ProtosSourceCompiler().compile(moduleSource);
        ProtosRootNode rootNode = (ProtosRootNode) target.getRootNode();

        assertSame(source, rootNode.source().orElseThrow());
    }

    @Test
    void resolverSourceKeyMismatchFailsClosed() {
        ProtosModuleSource source =
                ProtosModuleSource.fromCharacters(new ProtosModuleKey("test:a"), "1");
        assertThrows(
                IllegalArgumentException.class,
                () -> source.requireKey(new ProtosModuleKey("test:b")));
    }
}

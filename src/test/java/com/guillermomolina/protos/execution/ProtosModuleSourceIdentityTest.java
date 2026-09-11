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

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtosModuleSourceIdentityTest {
    @TempDir Path root;

    @Test
    void fileBackedResolverRetainsCanonicalKeyExactCharactersAndSelectedPath() throws Exception {
        Path collections = Files.createDirectories(root.resolve("collections"));
        Path file = collections.resolve("Probe.protos");
        Files.writeString(file, "value: \"olá\"", StandardCharsets.UTF_8);

        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(root);
        ProtosModuleKey key = resolver.resolve("std:collections/Probe", Optional.empty());
        ProtosModuleSource moduleSource = resolver.loadSource(key);
        Path exact = file.toAbsolutePath().normalize();

        assertEquals(key, moduleSource.key());
        assertEquals("value: \"olá\"", moduleSource.characters());
        assertEquals(Optional.of(exact), moduleSource.physicalPath());
        assertEquals("Probe.protos", moduleSource.sourceName());
    }

    @Test
    void hostedMaterializationProducesPhysicalSourceWithExactSelectedPathAndCharacters()
            throws Exception {
        Path file = root.resolve("Hosted.protos");
        Files.writeString(file, "value: 42", StandardCharsets.UTF_8);
        ProtosModuleSource moduleSource =
                ProtosModuleSource.fromPath(new ProtosModuleKey("test:hosted"), file);
        Path exact = file.toAbsolutePath().normalize();

        Source source = materialize(moduleSource);

        assertEquals(exact.toString(), source.getPath());
        assertEquals(exact.toUri(), source.getURI());
        assertEquals("Hosted.protos", source.getName());
        assertEquals("value: 42", source.getCharacters().toString());
        assertEquals(ProtosLanguage.ID, source.getLanguage());
    }

    @Test
    void hostedMaterializationPreservesSelectedSymlinkPathWhenSupported() throws Exception {
        Path target = root.resolve("target.protos");
        Path selected = root.resolve("selected.protos");
        Files.writeString(target, "42", StandardCharsets.UTF_8);
        try {
            Files.createSymbolicLink(selected, target.getFileName());
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "symbolic links unavailable: " + unavailable);
            return;
        }

        ProtosModuleSource moduleSource =
                ProtosModuleSource.fromPath(new ProtosModuleKey("test:symlink"), selected);
        Path exactSelected = selected.toAbsolutePath().normalize();

        assertEquals(Optional.of(exactSelected), moduleSource.physicalPath());
        Source source = materialize(moduleSource);
        assertEquals(exactSelected.toString(), source.getPath());
        assertFalse(
                source.getPath().equals(target.toRealPath().toString()),
                "tooling presentation must not resolve the selected symlink solely for identity");
    }

    @Test
    void resolverPayloadDoesNotOwnContextSpecificTruffleObjects() {
        assertTrue(
                Arrays.stream(ProtosModuleSource.class.getRecordComponents())
                        .noneMatch(
                                component ->
                                        Source.class.isAssignableFrom(component.getType())
                                                || TruffleFile.class.isAssignableFrom(
                                                        component.getType())));
    }

    @Test
    void sourceOnlyStagingCompilationBuildsLiteralSourceFromNeutralFacts() {
        ProtosModuleSource moduleSource =
                ProtosModuleSource.fromCharacters(
                        new ProtosModuleKey("test:source-owned"),
                        "1");

        com.oracle.truffle.api.RootCallTarget target =
                (com.oracle.truffle.api.RootCallTarget)
                        new ProtosSourceCompiler().compile(moduleSource);
        ProtosRootNode rootNode = (ProtosRootNode) target.getRootNode();
        Source source = rootNode.source().orElseThrow();

        assertEquals("1", source.getCharacters().toString());
        assertEquals("test:source-owned", source.getName());
        assertTrue(moduleSource.physicalPath().isEmpty());
    }

    @Test
    void resolverSourceKeyMismatchFailsClosed() {
        ProtosModuleSource source =
                ProtosModuleSource.fromCharacters(new ProtosModuleKey("test:a"), "1");
        assertThrows(
                IllegalArgumentException.class,
                () -> source.requireKey(new ProtosModuleKey("test:b")));
    }

    private static Source materialize(ProtosModuleSource moduleSource) {
        try (ProtosPolyglotExecutionContext context =
                ProtosPolyglotExecutionContext.open(
                        java.io.InputStream.nullInputStream(),
                        java.io.OutputStream.nullOutputStream(),
                        java.io.OutputStream.nullOutputStream())) {
            return context.callEntered(
                    () -> ProtosLanguageContext.current().materializeModuleSource(moduleSource));
        }
    }
}

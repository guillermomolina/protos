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
package com.guillermomolina.protos.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosNioConfinedFilesystemBackend;
import com.guillermomolina.protos.execution.ProtosSourceFileLoader;
import com.guillermomolina.protos.execution.ProtosStandardFilesystemProtocol;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosFilesystemLanguageConformanceTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path CASE_ROOT =
            Path.of("protos", "tests", "filesystem");

    @TempDir Path authorityRoot;

    @Test
    void replaceIsVisibleThroughOrdinaryProtosAndResolvesToTheExactFilesystem() throws Exception {
        Path source = Files.writeString(
                authorityRoot.resolve("source"), "new", StandardCharsets.UTF_8);
        Path target = authorityRoot.resolve("target");

        try (Fixture fixture = fixture(Set.of("source", "target"))) {
            assertSame(
                    ProtosBooleanValue.TRUE,
                    execute("replace-resolves-exact-receiver.protos", fixture.activation()));

            assertTrue(Files.notExists(source, LinkOption.NOFOLLOW_LINKS));
            assertEquals("new", Files.readString(target, StandardCharsets.UTF_8));
        }
    }

    @Test
    void removeIsVisibleThroughOrdinaryProtosAndResolvesToTheExactFilesystem() throws Exception {
        Path entry = Files.writeString(
                authorityRoot.resolve("entry"), "x", StandardCharsets.UTF_8);

        try (Fixture fixture = fixture(Set.of("entry"))) {
            assertSame(
                    ProtosBooleanValue.TRUE,
                    execute("remove-resolves-exact-receiver.protos", fixture.activation()));

            assertTrue(Files.notExists(entry, LinkOption.NOFOLLOW_LINKS));
        }
    }

    @Test
    void distinctNamespaceInvocationsProduceFreshFutureIdentitiesFromProtos() throws Exception {
        Path first = Files.writeString(
                authorityRoot.resolve("first"), "1", StandardCharsets.UTF_8);
        Path second = Files.writeString(
                authorityRoot.resolve("second"), "2", StandardCharsets.UTF_8);

        try (Fixture fixture = fixture(Set.of("first", "second"))) {
            assertSame(
                    ProtosBooleanValue.TRUE,
                    execute("fresh-namespace-futures.protos", fixture.activation()));

            assertTrue(Files.notExists(first, LinkOption.NOFOLLOW_LINKS));
            assertTrue(Files.notExists(second, LinkOption.NOFOLLOW_LINKS));
        }
    }

    @Test
    void mutableAuthorityRejectionSurfacesAsStandardIOErrorWithoutEffect() throws Exception {
        Path forbidden = Files.writeString(
                authorityRoot.resolve("forbidden"), "keep", StandardCharsets.UTF_8);

        try (Fixture fixture = fixture(Set.of("allowed"))) {
            ProtosSignalException signal =
                    assertThrows(
                            ProtosSignalException.class,
                            () -> execute("forbidden-remove-io-error.protos", fixture.activation()));

            assertSame(
                    ProtosCoreErrors.prototype(
                            fixture.activation(), ProtosCoreErrors.StandardError.I_O_ERROR),
                    signal.error().parent().orElseThrow());
            assertEquals("keep", Files.readString(forbidden, StandardCharsets.UTF_8));
        }
    }

    @Test
    void hardLinkAliasesRemainANamespaceNoOpThroughTheProtosSurface() throws Exception {
        Path source = Files.writeString(
                authorityRoot.resolve("source"), "same", StandardCharsets.UTF_8);
        Path target = authorityRoot.resolve("target");
        try {
            Files.createLink(target, source);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            assumeTrue(false, "host cannot create hard-link alias fixture");
        }

        try (Fixture fixture = fixture(Set.of("source", "target"))) {
            assertSame(
                    ProtosBooleanValue.TRUE,
                    execute("hardlink-alias-noop.protos", fixture.activation()));

            assertTrue(Files.exists(source, LinkOption.NOFOLLOW_LINKS));
            assertTrue(Files.exists(target, LinkOption.NOFOLLOW_LINKS));
            assertTrue(Files.isSameFile(source, target));
        }
    }

    @Test
    void finalSymlinkSourceIsMovedAsItsEntryRatherThanFollowedThroughProtos() throws Exception {
        Path outside = Files.createTempFile("protos-i021c-outside-", ".txt");
        try {
            Files.writeString(outside, "outside", StandardCharsets.UTF_8);
            Path source = authorityRoot.resolve("source");
            Path target = authorityRoot.resolve("target");
            try {
                Files.createSymbolicLink(source, outside.toAbsolutePath());
            } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
                assumeTrue(false, "host cannot create symlink fixture");
            }

            try (Fixture fixture = fixture(Set.of("source", "target"))) {
                assertSame(
                        ProtosBooleanValue.TRUE,
                        execute("symlink-source-not-followed.protos", fixture.activation()));

                assertTrue(Files.notExists(source, LinkOption.NOFOLLOW_LINKS));
                assertTrue(Files.isSymbolicLink(target));
                assertEquals(outside.toAbsolutePath(), Files.readSymbolicLink(target));
                assertEquals("outside", Files.readString(outside, StandardCharsets.UTF_8));
            }
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void nonRecursiveRemovalFailureSurfacesAsIOErrorAndPreservesDescendants() throws Exception {
        Path directory = Files.createDirectory(authorityRoot.resolve("directory"));
        Path child =
                Files.writeString(directory.resolve("child"), "x", StandardCharsets.UTF_8);

        try (Fixture fixture = fixture(Set.of("directory"))) {
            ProtosSignalException signal =
                    assertThrows(
                            ProtosSignalException.class,
                            () -> execute("nonrecursive-remove-io-error.protos", fixture.activation()));

            assertSame(
                    ProtosCoreErrors.prototype(
                            fixture.activation(), ProtosCoreErrors.StandardError.I_O_ERROR),
                    signal.error().parent().orElseThrow());
            assertTrue(Files.exists(directory, LinkOption.NOFOLLOW_LINKS));
            assertEquals("x", Files.readString(child, StandardCharsets.UTF_8));
        }
    }

    private Fixture fixture(Set<String> mutableDirectChildren) throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosNioConfinedFilesystemBackend backend =
                new ProtosNioConfinedFilesystemBackend(
                        authorityRoot, Set.of(), mutableDirectChildren);
        if (!backend.secureNamespaceConfinementAvailable()) {
            backend.close();
            assumeTrue(false, "host provider has no SecureDirectoryStream");
        }

        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(), activation, backend);
        ProtosFilesystemValue filesystem =
                assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem);
        activation.context().createLocalSlot("filesystem", filesystem);
        return new Fixture(activation, backend);
    }

    private static Object execute(String file, ProtosActivation activation) throws IOException {
        return new ProtosSourceFileLoader().load(CASE_ROOT.resolve(file)).call(activation);
    }

    private record Fixture(
            ProtosActivation activation, ProtosNioConfinedFilesystemBackend backend)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            backend.close();
        }
    }
}

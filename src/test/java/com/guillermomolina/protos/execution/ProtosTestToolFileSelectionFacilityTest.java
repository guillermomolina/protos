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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosTestToolFileSelectionFacilityTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY =
            Path.of("protos", "lib");

    @TempDir Path tempDir;

    @Test
    void relativeFileResolvesAgainstCapturedWorkingDirectory()
            throws Exception {
        Path root =
                Files.createDirectories(
                        tempDir.resolve("tests").resolve("conformance"));
        Files.writeString(root.resolve("case.protos"), "true");

        Fixture fixture =
                fixture(
                        tempDir,
                        List.of(
                                sourceRoot(
                                        "protos/corpus/conformance",
                                        root)));

        ProtosArrayValue associations =
                resolve(
                        fixture,
                        "tests/conformance/case.protos");

        assertEquals(
                BigInteger.ONE,
                associations.indexedSize());
        assertAssociation(
                associations,
                0,
                "protos/corpus/conformance",
                "case.protos");
    }

    @Test
    void absoluteFileIsAcceptedWithoutRebasing() throws Exception {
        Path root =
                Files.createDirectories(tempDir.resolve("corpus"));
        Path source =
                Files.writeString(
                        root.resolve("absolute.protos"),
                        "true");

        Fixture fixture =
                fixture(
                        tempDir.resolve("unrelated-cwd"),
                        List.of(
                                sourceRoot(
                                        "protos/corpus/example",
                                        root)));

        ProtosArrayValue associations =
                resolve(fixture, source.toAbsolutePath().toString());

        assertEquals(
                BigInteger.ONE,
                associations.indexedSize());
        assertAssociation(
                associations,
                0,
                "protos/corpus/example",
                "absolute.protos");
    }

    @Test
    void sharedAndOverlappingRootsPreserveEveryCorpusAssociation()
            throws Exception {
        Path sharedRoot =
                Files.createDirectories(tempDir.resolve("library"));
        Path nestedRoot =
                Files.createDirectories(sharedRoot.resolve("uri"));
        Path source =
                Files.writeString(
                        nestedRoot.resolve("parse.protos"),
                        "true");

        Fixture fixture =
                fixture(
                        tempDir,
                        List.of(
                                sourceRoot(
                                        "protos/corpus/library/uri",
                                        sharedRoot),
                                sourceRoot(
                                        "protos/corpus/library/csv",
                                        sharedRoot),
                                sourceRoot(
                                        "protos/corpus/nested",
                                        nestedRoot)));

        ProtosArrayValue associations =
                resolve(fixture, source.toString());

        assertEquals(
                BigInteger.valueOf(3),
                associations.indexedSize());

        assertAssociation(
                associations,
                0,
                "protos/corpus/library/uri",
                "uri/parse.protos");
        assertAssociation(
                associations,
                1,
                "protos/corpus/library/csv",
                "uri/parse.protos");
        assertAssociation(
                associations,
                2,
                "protos/corpus/nested",
                "parse.protos");
    }

    @Test
    void outsideMissingAndDirectorySourcesProduceNoAssociations()
            throws Exception {
        Path root =
                Files.createDirectories(tempDir.resolve("corpus"));
        Files.createDirectories(root.resolve("directory"));

        Fixture fixture =
                fixture(
                        tempDir,
                        List.of(
                                sourceRoot(
                                        "protos/corpus/example",
                                        root)));

        assertEquals(
                BigInteger.ZERO,
                resolve(
                                fixture,
                                tempDir.resolve("outside.protos").toString())
                        .indexedSize());

        assertEquals(
                BigInteger.ZERO,
                resolve(
                                fixture,
                                root.resolve("missing.protos").toString())
                        .indexedSize());

        assertEquals(
                BigInteger.ZERO,
                resolve(
                                fixture,
                                root.resolve("directory").toString())
                        .indexedSize());
    }

    @Test
    void symbolicLinkAliasIsNotSelected() throws Exception {
        Path root =
                Files.createDirectories(tempDir.resolve("corpus"));
        Path target =
                Files.writeString(
                        root.resolve("target.protos"),
                        "true");
        Path link = root.resolve("alias.protos");

        try {
            Files.createSymbolicLink(
                    link,
                    target.getFileName());
        } catch (UnsupportedOperationException
                | IOException
                | SecurityException unsupported) {
            Assumptions.assumeTrue(
                    false,
                    "symbolic links unavailable: " + unsupported);
        }

        Fixture fixture =
                fixture(
                        tempDir,
                        List.of(
                                sourceRoot(
                                        "protos/corpus/example",
                                        root)));

        assertEquals(
                BigInteger.ZERO,
                resolve(fixture, link.toString()).indexedSize());

        assertEquals(
                BigInteger.ONE,
                resolve(fixture, target.toString()).indexedSize());
    }

    @Test
    void resultAndAssociationsAreFrozen() throws Exception {
        Path root =
                Files.createDirectories(tempDir.resolve("corpus"));
        Path source =
                Files.writeString(
                        root.resolve("case.protos"),
                        "true");

        Fixture fixture =
                fixture(
                        tempDir,
                        List.of(
                                sourceRoot(
                                        "protos/corpus/example",
                                        root)));

        ProtosArrayValue associations =
                resolve(fixture, source.toString());
        ProtosArrayValue association =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        associations.indexedAt(BigInteger.ZERO));

        assertTrue(associations.isFrozen());
        assertTrue(association.isFrozen());
    }

    @Test
    void capabilityIsBootstrapLocal() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation activation =
                prelude.newModuleActivation();

        ProtosTestToolFileSelectionFacility.install(
                activation,
                tempDir,
                List.of());

        assertTrue(
                activation
                        .context()
                        .hasLocalSlot(
                                ProtosTestToolFileSelectionFacility
                                        .BOOTSTRAP_SLOT));

        ProtosActivation independent =
                prelude.newModuleActivation();

        assertFalse(
                independent
                        .context()
                        .hasLocalSlot(
                                ProtosTestToolFileSelectionFacility
                                        .BOOTSTRAP_SLOT));
    }

    @Test
    void wrongArityAndValueFamilyFailClosed() throws Exception {
        Fixture fixture =
                fixture(tempDir, List.of());

        assertThrows(
                ProtosSignalException.class,
                () ->
                        fixture
                                .closure()
                                .nativeBody()
                                .orElseThrow()
                                .execute(
                                        fixture.activation(),
                                        List.of()));

        assertThrows(
                ProtosSignalException.class,
                () ->
                        fixture
                                .closure()
                                .nativeBody()
                                .orElseThrow()
                                .execute(
                                        fixture.activation(),
                                        List.of(
                                                new ProtosIntegerValue(
                                                        BigInteger.ONE))));
    }

    private static ProtosTestToolFileSelectionFacility.CorpusSourceRoot
            sourceRoot(String corpusId, Path root) {
        return new ProtosTestToolFileSelectionFacility.CorpusSourceRoot(
                corpusId,
                root);
    }

    private static Fixture fixture(
            Path cwd,
            List<ProtosTestToolFileSelectionFacility.CorpusSourceRoot>
                    roots)
            throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation activation =
                prelude.newModuleActivation();

        ProtosTestToolFileSelectionFacility.install(
                activation,
                cwd,
                roots);

        ProtosClosureValue closure =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        activation
                                .context()
                                .readLocalSlot(
                                        ProtosTestToolFileSelectionFacility
                                                .BOOTSTRAP_SLOT)
                                .orElseThrow());

        return new Fixture(activation, closure);
    }

    private static ProtosPrelude prelude() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(
                        CORE,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY));
    }

    private static ProtosArrayValue resolve(
            Fixture fixture,
            String selectedFile) {
        return assertInstanceOf(
                ProtosArrayValue.class,
                fixture
                        .closure()
                        .nativeBody()
                        .orElseThrow()
                        .execute(
                                fixture.activation(),
                                List.of(
                                        new ProtosStringValue(
                                                selectedFile))));
    }

    private static void assertAssociation(
            ProtosArrayValue associations,
            int index,
            String expectedCorpusId,
            String expectedRelativePath) {
        ProtosArrayValue association =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        associations.indexedAt(
                                BigInteger.valueOf(index)));

        assertEquals(
                expectedCorpusId,
                assertInstanceOf(
                                ProtosStringValue.class,
                                association.indexedAt(BigInteger.ZERO))
                        .value());

        assertEquals(
                expectedRelativePath,
                assertInstanceOf(
                                ProtosStringValue.class,
                                association.indexedAt(BigInteger.ONE))
                        .value());
    }

    private record Fixture(
            ProtosActivation activation,
            ProtosClosureValue closure) {}
}

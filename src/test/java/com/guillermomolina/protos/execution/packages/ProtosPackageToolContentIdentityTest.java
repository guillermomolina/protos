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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.oracle.truffle.api.source.Source;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Host/runtime integration tests for TOOL001 Package Tool mechanics.
 *
 * <p>Repository-owned Protos semantic corpora are executed by TOOL002 through {@code protos test}.
 */
final class ProtosPackageToolContentIdentityTest extends ProtosPackageToolProtosTestSupport {
    @Test
    void contentIdentityRejectsLegacyOnlyAndCoexistingManifests() throws Exception {
        Path oldOnly = contentIdentityRoot("old-only");
        writeContentIdentityFile(oldOnly, "protos.toml", new byte[0]);
        assertContentIdentityRejectsTree(oldOnly);

        Path both = contentIdentityRoot("both");
        writeContentIdentityFile(both, "package.toml", new byte[0]);
        writeContentIdentityFile(both, "protos.toml", new byte[0]);
        assertContentIdentityRejectsTree(both);
    }

    @Test
    void contentIdentityRejectsNonRegularManifestAndLegacyLink() throws Exception {
        Path directory = contentIdentityRoot("manifest-directory");
        Files.createDirectory(directory.resolve("package.toml"));
        assertContentIdentityRejectsTree(directory);

        Path manifestLink = contentIdentityRoot("manifest-link");
        writeContentIdentityFile(
                manifestLink, "actual.toml", new byte[0]);

        boolean rootLinkCreated = false;
        try {
            Files.createSymbolicLink(
                    manifestLink.resolve("package.toml"),
                    Path.of("actual.toml"));
            rootLinkCreated = true;
        } catch (UnsupportedOperationException | IOException
                | SecurityException unavailable) {
            // The host cannot construct the root-link vector.
        }

        if (rootLinkCreated) {
            assertContentIdentityRejectsTree(manifestLink);
        }

        Path legacyLink = contentIdentityRoot("legacy-link");
        writeContentIdentityFile(legacyLink, "package.toml", new byte[0]);
        try {
            Files.createSymbolicLink(
                    legacyLink.resolve("protos.toml"),
                    Path.of("package.toml"));
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            assumeTrue(false, "host cannot create the legacy symbolic-link vector");
        }
        assertContentIdentityRejectsTree(legacyLink);
    }

    @Test
    void contentIdentityProductionCanonicalizerRejectsInvalidLogicalTrees() throws Exception {
        Path missingManifest = contentIdentityRoot("missing-manifest");
        writeContentIdentityFile(missingManifest, "Main.protos", asciiBytes("x"));
        assertContentIdentityRejectsTree(missingManifest);

        Path invalidCharacter = contentIdentityRoot("invalid-character");
        writeContentIdentityFile(invalidCharacter, "package.toml", new byte[0]);
        writeContentIdentityFile(invalidCharacter, "bad name", asciiBytes("x"));
        assertContentIdentityRejectsTree(invalidCharacter);

        Path reservedName = contentIdentityRoot("reserved-name");
        writeContentIdentityFile(reservedName, "package.toml", new byte[0]);
        writeContentIdentityFile(reservedName, "CON.txt", asciiBytes("x"));
        assertContentIdentityRejectsTree(reservedName);
    }

    @Test
    void contentIdentityProductionCanonicalizerRejectsSiblingCaseFoldCollision()
            throws Exception {
        Path root = contentIdentityRoot("case-collision");
        writeContentIdentityFile(root, "package.toml", new byte[0]);
        Path upper = root.resolve("Parser.protos");
        Path lower = root.resolve("parser.protos");
        Files.write(upper, asciiBytes("a"));
        Files.write(lower, asciiBytes("b"));

        assumeTrue(
                !Files.isSameFile(upper, lower),
                "host filesystem cannot materialize the case-collision vector");
        assertContentIdentityRejectsTree(root);
    }

    @Test
    void contentIdentityProductionCanonicalizerRejectsCapturedLinkWithoutFollowingIt()
            throws Exception {
        Path root = contentIdentityRoot("link");
        writeContentIdentityFile(root, "package.toml", asciiBytes("x"));

        try {
            Files.createSymbolicLink(root.resolve("alias"), Path.of("package.toml"));
        } catch (UnsupportedOperationException | IOException | SecurityException unsupported) {
            assumeTrue(false, "host filesystem cannot materialize the symbolic-link vector");
        }

        assertContentIdentityRejectsTree(root);
    }

    @Test
    void contentIdentityVerifierRejectsUnsupportedOrMalformedRecordedIdentity()
            throws Exception {
        Path root = contentIdentityRoot("unsupported");
        writeContentIdentityFile(root, "package.toml", new byte[0]);

        String valid =
                "7b84ceb16a689651d3de513f3aa9552554736735971ef9720a7818daff93c764";

        assertContentIdentityRejectsExpected(root, "future-package-tree-v2", "sha256", valid);
        assertContentIdentityRejectsExpected(
                root,
                "protos-package-tree-v1",
                "sha256",
                "beb503347f64442d909b4848e69333a72f4d049d7f5b37c32f453f409641ef04");
        assertContentIdentityRejectsExpected(
                root,
                "protos-package-tree-v2",
                "sha256",
                "beb503347f64442d909b4848e69333a72f4d049d7f5b37c32f453f409641ef04");
        assertContentIdentityRejectsExpected(root, "protos-package-tree-v2", "sha512", valid);
        assertContentIdentityRejectsExpected(root, "protos-package-tree-v2", "sha256", "00");
        assertContentIdentityRejectsExpected(
                root,
                "protos-package-tree-v2",
                "sha256",
                valid.substring(0, 63) + "A");
    }

    @Test
    void contentIdentityRecordShapeSurvivesSuspendingContentRead() throws Exception {
        Path root = contentIdentityRoot("record-shape");
        writeContentIdentityFile(root, "package.toml", new byte[0]);

        ProtosExecutionOutcome outcome =
                executeContentIdentity(
                        root,
                        "record-shape.protos",
                        Map.of(),
                        true);
        assertExpected(
                outcome,
                "true",
                "content-identity/record-shape.protos");
    }

    @Test
    void contentIdentityFrozenVectorsDigestAndVerifyHostCapturedCustody() throws Exception {
        // D171: the v2 vector trees reach ContentIdentity only as host-captured read-only
        // Filesystem custody (PLAT012), never through a guest capture selector.
        Map<String, String> vectors =
                Map.of(
                        "minimal",
                        "7b84ceb16a689651d3de513f3aa9552554736735971ef9720a7818daff93c764",
                        "ordering",
                        "f401793adfe18d9ffb00747b47a6581c7184dddc62cd5665a24fb6d217a4a9cb",
                        "binary",
                        "2291f7d8a6bccceceec105ce20ebdf17f2d76a3b82ff869f5a82fbddd29049e0",
                        "varuint-boundaries",
                        "6a63f12d9befb69810f8b8e84a7ef878a06bc1b67b35447f149cc3946cb7a0a0",
                        "exact-case-upper",
                        "b56cbd69fb745176b1d76856445dc232c57f2693c8a4db9fc9c757ad63da2383",
                        "exact-case-lower",
                        "157416a3c715142ac09ee3d7238badba2ce2a5546ffb5819886a218a5697c719");
        for (Map.Entry<String, String> vector : vectors.entrySet()) {
            ProtosExecutionOutcome outcome =
                    executeContentIdentity(
                            TEST_ROOT.resolve("content-identity/cases").resolve(vector.getKey()),
                            "digest-verify.protos",
                            Map.of("expectedHex", vector.getValue()),
                            true);
            assertExpected(
                    outcome, "true", "content-identity vector " + vector.getKey());
        }
    }

    @Test
    void contentIdentityVaruintImplementationCoversFrozenArbitraryPrecisionBoundaries()
            throws Exception {
        try (ProtosHostedExecutionTestFixture hosted =
                ProtosHostedExecutionTestFixture.open(newPackagePrelude())) {
            ProtosExecutionOutcome outcome =
                    executeFile(
                            TEST_ROOT.resolve("content-identity/varuint.protos"),
                            hosted.activation());
            assertExpected(outcome, "true", "content-identity/varuint.protos");
        }
    }

}

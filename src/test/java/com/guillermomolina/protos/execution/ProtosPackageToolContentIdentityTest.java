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
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
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
    void contentIdentityProductionCanonicalizerRejectsInvalidLogicalTrees() throws Exception {
        Path missingManifest = contentIdentityRoot("missing-manifest");
        writeContentIdentityFile(missingManifest, "Main.protos", asciiBytes("x"));
        assertContentIdentityRejectsTree(missingManifest);

        Path invalidCharacter = contentIdentityRoot("invalid-character");
        writeContentIdentityFile(invalidCharacter, "protos.toml", new byte[0]);
        writeContentIdentityFile(invalidCharacter, "bad name", asciiBytes("x"));
        assertContentIdentityRejectsTree(invalidCharacter);

        Path reservedName = contentIdentityRoot("reserved-name");
        writeContentIdentityFile(reservedName, "protos.toml", new byte[0]);
        writeContentIdentityFile(reservedName, "CON.txt", asciiBytes("x"));
        assertContentIdentityRejectsTree(reservedName);
    }

    @Test
    void contentIdentityProductionCanonicalizerRejectsSiblingCaseFoldCollision()
            throws Exception {
        Path root = contentIdentityRoot("case-collision");
        writeContentIdentityFile(root, "protos.toml", new byte[0]);
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
        writeContentIdentityFile(root, "protos.toml", asciiBytes("x"));

        try {
            Files.createSymbolicLink(root.resolve("alias"), Path.of("protos.toml"));
        } catch (UnsupportedOperationException | IOException | SecurityException unsupported) {
            assumeTrue(false, "host filesystem cannot materialize the symbolic-link vector");
        }

        assertContentIdentityRejectsTree(root);
    }

    @Test
    void contentIdentityVerifierRejectsUnsupportedOrMalformedRecordedIdentity()
            throws Exception {
        Path root = contentIdentityRoot("unsupported");
        writeContentIdentityFile(root, "protos.toml", new byte[0]);

        String valid =
                "beb503347f64442d909b4848e69333a72f4d049d7f5b37c32f453f409641ef04";

        assertContentIdentityRejectsExpected(root, "future-package-tree-v2", "sha256", valid);
        assertContentIdentityRejectsExpected(root, "protos-package-tree-v1", "sha512", valid);
        assertContentIdentityRejectsExpected(root, "protos-package-tree-v1", "sha256", "00");
        assertContentIdentityRejectsExpected(
                root,
                "protos-package-tree-v1",
                "sha256",
                valid.substring(0, 63) + "A");
    }

    @Test
    void contentIdentityRecordShapeSurvivesSuspendingContentRead() throws Exception {
        Path root = contentIdentityRoot("record-shape");
        writeContentIdentityFile(root, "protos.toml", new byte[0]);

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

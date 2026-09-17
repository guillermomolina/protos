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
final class ProtosPackageToolMetadataPersistenceTest extends ProtosPackageToolProtosTestSupport {
    @Test
    void lockFileLoadsCanonicalContent() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), CANONICAL_LOCK, StandardCharsets.UTF_8);
        assertConfinedTrue("lock-file/load-canonical.protos");
    }

    @Test
    void lockFileRejectsNonCanonicalContent() throws Exception {
        Files.writeString(
                projectRoot.resolve("protos.lock"), NONCANONICAL_LOCK, StandardCharsets.UTF_8);
        assertConfinedFailed("lock-file/load-noncanonical-error.protos");
    }

    @Test
    void lockFileMissingUsesOrdinaryIoFailure() throws Exception {
        try (Fixture fixture = confinedFixture(projectRoot)) {
            assertIoErrorOutcome(
                    executeFile(
                            TEST_ROOT.resolve("lock-file/load-missing-error.protos"),
                            fixture.activation()),
                    fixture.activation(),
                    "lock-file/load-missing-error.protos");
        }
    }

    @Test
    void lockFilePublishesCanonicalBytes() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), "old\n", StandardCharsets.UTF_8);
        assertConfinedTrue("lock-file/publish-canonical.protos");
        assertEquals(CANONICAL_LOCK, Files.readString(projectRoot.resolve("protos.lock")));
        assertTrue(
                Files.notExists(
                        projectRoot.resolve(".protos.lock.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void lockFileInvalidModelCreatesNoStageAndPreservesTarget() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), "old\n", StandardCharsets.UTF_8);
        assertConfinedFailed("lock-file/publish-invalid-no-stage.protos");
        assertEquals("old\n", Files.readString(projectRoot.resolve("protos.lock")));
        assertFalse(
                Files.exists(
                        projectRoot.resolve(".protos.lock.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void lockFileStageCollisionPreservesExistingLockAndStage() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), "old\n", StandardCharsets.UTF_8);
        Files.writeString(
                projectRoot.resolve(".protos.lock.stage"), "stale\n", StandardCharsets.UTF_8);

        try (Fixture fixture = confinedFixture(projectRoot)) {
            assertIoErrorOutcome(
                    executeFile(
                            TEST_ROOT.resolve("lock-file/publish-stage-collision.protos"),
                            fixture.activation()),
                    fixture.activation(),
                    "lock-file/publish-stage-collision.protos");
        }

        assertEquals("old\n", Files.readString(projectRoot.resolve("protos.lock")));
        assertEquals("stale\n", Files.readString(projectRoot.resolve(".protos.lock.stage")));
    }


    @Test
    void metadataPublicationReplacesManifestTarget() throws Exception {
        Files.writeString(projectRoot.resolve("protos.toml"), "old\n", StandardCharsets.UTF_8);
        assertConfinedTrue("metadata-publication/publish-replaces-target.protos");

        assertEquals(
                "new metadata\n",
                Files.readString(projectRoot.resolve("protos.toml"), StandardCharsets.UTF_8));
        assertTrue(
                Files.notExists(
                        projectRoot.resolve(".protos.toml.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void metadataPublicationReplacesLockTarget() throws Exception {
        Files.writeString(projectRoot.resolve("protos.lock"), "old lock\n", StandardCharsets.UTF_8);
        assertConfinedTrue("metadata-publication/publish-lock-replaces-target.protos");

        assertEquals(
                "new lock metadata\n",
                Files.readString(projectRoot.resolve("protos.lock"), StandardCharsets.UTF_8));
        assertTrue(
                Files.notExists(
                        projectRoot.resolve(".protos.lock.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void metadataPublicationStageCollisionPreservesTarget() throws Exception {
        Files.writeString(projectRoot.resolve("protos.toml"), "old\n", StandardCharsets.UTF_8);
        Files.writeString(
                projectRoot.resolve(".protos.toml.stage"), "stale\n", StandardCharsets.UTF_8);

        try (Fixture fixture = confinedFixture(projectRoot)) {
            assertIoErrorOutcome(
                    executeFile(
                            TEST_ROOT.resolve(
                                    "metadata-publication/stage-collision-preserves-target.protos"),
                            fixture.activation()),
                    fixture.activation(),
                    "metadata-publication/stage-collision-preserves-target.protos");
        }

        assertEquals(
                "old\n",
                Files.readString(projectRoot.resolve("protos.toml"), StandardCharsets.UTF_8));
        assertEquals(
                "stale\n",
                Files.readString(
                        projectRoot.resolve(".protos.toml.stage"), StandardCharsets.UTF_8));
    }

    @Test
    void metadataPublicationExplicitDiscardRemovesStage() throws Exception {
        Files.writeString(
                projectRoot.resolve(".protos.toml.stage"),
                "abandoned\n",
                StandardCharsets.UTF_8);

        assertConfinedTrue("metadata-publication/discard-staging.protos");

        assertTrue(
                Files.notExists(
                        projectRoot.resolve(".protos.toml.stage"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void metadataPublicationInvalidContentCreatesNoStage() throws Exception {
        Files.writeString(projectRoot.resolve("protos.toml"), "old\n", StandardCharsets.UTF_8);

        assertConfinedFailed("metadata-publication/invalid-content-has-no-stage.protos");

        assertFalse(
                Files.exists(
                        projectRoot.resolve(".protos.toml.stage"), LinkOption.NOFOLLOW_LINKS));
        assertEquals(
                "old\n",
                Files.readString(projectRoot.resolve("protos.toml"), StandardCharsets.UTF_8));
    }

    @Test
    void metadataPublicationForbiddenTargetPreservesPreparedStageAndTarget() throws Exception {
        Files.writeString(projectRoot.resolve("secret.txt"), "secret\n", StandardCharsets.UTF_8);

        try (Fixture fixture = confinedFixture(projectRoot)) {
            assertIoErrorOutcome(
                    executeFile(
                            TEST_ROOT.resolve(
                                    "metadata-publication/forbidden-target-preserves-target.protos"),
                            fixture.activation()),
                    fixture.activation(),
                    "metadata-publication/forbidden-target-preserves-target.protos");
        }

        assertEquals(
                "secret\n",
                Files.readString(projectRoot.resolve("secret.txt"), StandardCharsets.UTF_8));
        assertEquals(
                "staged but forbidden\n",
                Files.readString(
                        projectRoot.resolve(".protos.toml.stage"), StandardCharsets.UTF_8));
    }






}

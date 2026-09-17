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
final class ProtosPackageToolManifestTest extends ProtosPackageToolProtosTestSupport {
    @Test
    void manifestCommandReadsValidManifest() throws Exception {
        Files.writeString(
                projectRoot.resolve("protos.toml"),
                "manifest-version = 1\n[package]\nid = \"pkg\"\nversion = \"1.0.0\"\n",
                StandardCharsets.UTF_8);
        assertConfinedTrue("manifest-command/valid-minimal.protos");
    }

    @Test
    void manifestCommandReportsInvalidSchema() throws Exception {
        Files.writeString(
                projectRoot.resolve("protos.toml"),
                "manifest-version = 1\n[package]\nid = \"pkg\"\n",
                StandardCharsets.UTF_8);
        assertConfinedTrue("manifest-command/invalid-schema-diagnostic.protos");
    }

    @Test
    void manifestCommandReportsMissingManifest() throws Exception {
        assertConfinedTrue("manifest-command/missing-manifest-diagnostic.protos");
    }

    @Test
    void manifestCommandReportsInvalidUtf8() throws Exception {
        Files.write(projectRoot.resolve("protos.toml"), new byte[] {(byte) 0xc3, 0x28});
        assertConfinedTrue("manifest-command/invalid-utf8-diagnostic.protos");
    }

    @Test
    void manifestCommandReadsAcrossMultipleTextReaderChunks() throws Exception {
        String source =
                "manifest-version = 1\n"
                        + "[package]\n"
                        + "id = \"large\"\n"
                        + "version = \"1.0.0\"\n"
                        + "# "
                        + "x".repeat(9000)
                        + "\n";

        assertTrue(source.getBytes(StandardCharsets.UTF_8).length > 8192);
        Files.writeString(
                projectRoot.resolve("protos.toml"),
                source,
                StandardCharsets.UTF_8);
        assertConfinedTrue("manifest-command/multichunk-read.protos");
    }
}

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
package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolFileSelectionWiringTest {
    private static final Path CLI =
            Path.of(
                    "src",
                    "main",
                    "java",
                    "com",
                    "guillermomolina",
                    "protos",
                    "cli",
                    "ProtosCli.java");

    @Test
    void publicTestToolPublishesEveryAuthoritativeCorpusSourceRoot()
            throws Exception {
        String source =
                Files.readString(CLI, StandardCharsets.UTF_8);

        String[] corpusIds = {
            "protos/corpus/conformance",
            "protos/corpus/process-snapshot",
            "protos/corpus/actor",
            "protos/corpus/group",
            "protos/corpus/package-toml",
            "protos/corpus/library/uri",
            "protos/corpus/library/csv",
            "protos/corpus/library/cli",
            "protos/corpus/library/math/integer",
            "protos/corpus/library/crypto/sha256",
            "protos/corpus/library/network/ip-addresses",
            "protos/corpus/library/network/ip-endpoints",
            "protos/corpus/package-tool/version",
            "protos/corpus/package-tool/lock",
            "protos/corpus/package-tool/resolution-input",
            "protos/corpus/package-tool/content-identity",
            "protos/corpus/package-tool/resolution-input-lock",
            "protos/corpus/package-tool/resolution-root",
            "protos/corpus/package-tool/execution-plan",
            "protos/corpus/package-tool/project-projection"
        };

        for (String corpusId : corpusIds) {
            assertTrue(
                    source.contains("\"" + corpusId + "\""),
                    () -> "missing file-selection corpus authority: " + corpusId);
        }

        assertEquals(
                20,
                occurrences(
                        source,
                        "new ProtosTestToolFileSelectionFacility.CorpusSourceRoot("));

        assertTrue(
                source.contains(
                        "ProtosTestToolFileSelectionFacility.install(\n"
                                + "                                    session.activation,\n"
                                + "                                    invocationWorkingDirectory,\n"
                                + "                                    fileSelectionSourceRoots);"));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;

        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }

        return count;
    }
}

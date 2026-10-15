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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosWorkspacePackageSourceInventoryTest {
    @TempDir Path temporary;

    @Test
    void inventoriesOnlyCanonicalSourcesInDeterministicImmutableOrder() throws Exception {
        Path alpha = writeSource(temporary, "Alpha.protos");
        Path tools = Files.createDirectories(temporary.resolve("tools"));
        Path parser = writeSource(tools, "Parser.protos");
        writeSource(temporary, "bad-name.protos");
        writeSource(temporary, "CON.protos");
        Files.writeString(temporary.resolve("README.txt"), "not a module\n");
        Files.createDirectory(temporary.resolve("Directory.protos"));

        List<ProtosWorkspacePackageSourceInventory.Source> sources =
                inventory(temporary, node("root", "")).snapshot();

        assertEquals(
                List.of(
                        new ProtosWorkspacePackageSourceInventory.Source(
                                "root", "Alpha", alpha.toRealPath()),
                        new ProtosWorkspacePackageSourceInventory.Source(
                                "root", "tools/Parser", parser.toRealPath())),
                sources);
        assertThrows(UnsupportedOperationException.class, () -> sources.add(sources.get(0)));
    }

    @Test
    void assignsEachSourceOnlyToItsMostSpecificAuthorizedPackage() throws Exception {
        Path rootMain = writeSource(temporary, "Main.protos");
        Path memberDirectory = Files.createDirectories(temporary.resolve("libs/member"));
        Path api = writeSource(memberDirectory, "Api.protos");

        List<ProtosWorkspacePackageSourceInventory.Source> sources =
                inventory(
                                temporary,
                                node("root", ""),
                                node("member-pkg", "libs/member"))
                        .snapshot();

        assertEquals(
                List.of(
                        new ProtosWorkspacePackageSourceInventory.Source(
                                "member-pkg", "Api", api.toRealPath()),
                        new ProtosWorkspacePackageSourceInventory.Source(
                                "root", "Main", rootMain.toRealPath())),
                sources);
    }

    @Test
    void excludesParentSymlinkAliasesIntoAChildPackageDomain() throws Exception {
        Path memberDirectory = Files.createDirectories(temporary.resolve("libs/member"));
        Path api = writeSource(memberDirectory, "Api.protos");
        Path alias = temporary.resolve("Alias.protos");
        try {
            Files.createSymbolicLink(alias, temporary.relativize(api));
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "symbolic links unavailable: " + unavailable);
        }

        List<ProtosWorkspacePackageSourceInventory.Source> sources =
                inventory(
                                temporary,
                                node("root", ""),
                                node("member-pkg", "libs/member"))
                        .snapshot();

        assertEquals(
                List.of(
                        new ProtosWorkspacePackageSourceInventory.Source(
                                "member-pkg", "Api", api.toRealPath())),
                sources);
    }

    @Test
    void failsClosedOnAsciiCaseFoldAmbiguity() throws Exception {
        writeSource(temporary, "Feature.protos");
        Path folded = temporary.resolve("feature.protos");
        Assumptions.assumeFalse(
                Files.exists(folded),
                "host filesystem does not preserve distinct case spellings");
        writeSource(temporary, "feature.protos");

        ProtosWorkspacePackageSourceInventory inventory =
                inventory(temporary, node("root", ""));

        assertThrows(IOException.class, inventory::snapshot);
    }

    @Test
    void excludesSymlinkSourcesThatEscapeTheAuthorizedPackageRoot() throws Exception {
        Path workspace = Files.createDirectory(temporary.resolve("workspace"));
        Path main = writeSource(workspace, "Main.protos");
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path external = writeSource(outside, "External.protos");
        Path escaping = workspace.resolve("Escape.protos");
        try {
            Files.createSymbolicLink(escaping, external);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "symbolic links unavailable: " + unavailable);
        }

        List<ProtosWorkspacePackageSourceInventory.Source> sources =
                inventory(workspace, node("root", "")).snapshot();

        assertEquals(
                List.of(
                        new ProtosWorkspacePackageSourceInventory.Source(
                                "root", "Main", main.toRealPath())),
                sources);
    }

    private static Path writeSource(Path directory, String name) throws IOException {
        Path source = directory.resolve(name);
        Files.writeString(source, "value: 1\n");
        return source;
    }

    private static ProtosWorkspacePackageSourceInventory inventory(
            Path projectRoot, ProtosPackageExecutionPlan.PackageNode... packages)
            throws Exception {
        ProtosPackageExecutionPlan plan =
                new ProtosPackageExecutionPlan(
                        1,
                        new ProtosPackageExecutionPlan.WorkspaceRef("root"),
                        List.of(packages),
                        List.of());
        ProtosWorkspacePackageProjectIndex projectIndex =
                ProtosWorkspacePackageProjectIndex.bind(projectRoot, plan);
        ProtosWorkspacePackageDirectoryIndex directoryIndex =
                ProtosWorkspacePackageDirectoryIndex.bind(projectIndex);
        return ProtosWorkspacePackageSourceInventory.bind(directoryIndex);
    }

    private static ProtosPackageExecutionPlan.PackageNode node(
            String packageId, String location) {
        return new ProtosPackageExecutionPlan.PackageNode(
                new ProtosPackageExecutionPlan.WorkspaceRef(packageId),
                location,
                Map.of());
    }
}

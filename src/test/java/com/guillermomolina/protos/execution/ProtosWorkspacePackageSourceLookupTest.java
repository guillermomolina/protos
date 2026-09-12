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

final class ProtosWorkspacePackageSourceLookupTest {
    @TempDir Path temporary;

    @Test
    void resolvesExactRootAndMemberSourcesIncludingNestedAndCoreNames() throws Exception {
        Path rootMain = writeSource(temporary, "Main.protos");
        Path nestedDirectory = Files.createDirectories(temporary.resolve("tools"));
        Path nested = writeSource(nestedDirectory, "Parser.protos");
        Path core = writeSource(temporary, "core.protos");
        Path memberDirectory = Files.createDirectories(temporary.resolve("libs/member"));
        Path memberApi = writeSource(memberDirectory, "Api.protos");

        ProtosWorkspacePackageSourceLookup lookup =
                lookup(temporary, node("root", ""), node("member-pkg", "libs/member"));

        assertEquals(rootMain.toRealPath(), lookup.requireSource("root", "Main"));
        assertEquals(nested.toRealPath(), lookup.requireSource("root", "tools/Parser"));
        assertEquals(core.toRealPath(), lookup.requireSource("root", "core"));
        assertEquals(memberApi.toRealPath(), lookup.requireSource("member-pkg", "Api"));
    }

    @Test
    void parentLookupStopsAtAnAuthorizedDescendantPackageRoot() throws Exception {
        Path memberDirectory = Files.createDirectories(temporary.resolve("libs/member"));
        Path memberApi = writeSource(memberDirectory, "Api.protos");

        ProtosWorkspacePackageSourceLookup lookup =
                lookup(temporary, node("root", ""), node("member-pkg", "libs/member"));

        assertEquals(memberApi.toRealPath(), lookup.requireSource("member-pkg", "Api"));
        assertThrows(
                IOException.class,
                () -> lookup.requireSource("root", "libs/member/Api"));
    }

    @Test
    void parentSymlinkAliasCannotReenterAChildOwnedSource() throws Exception {
        Path memberDirectory = Files.createDirectories(temporary.resolve("libs/member"));
        Path memberApi = writeSource(memberDirectory, "Api.protos");
        Path alias = temporary.resolve("Alias.protos");
        try {
            Files.createSymbolicLink(alias, temporary.relativize(memberApi));
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "symbolic links unavailable: " + unavailable);
        }

        ProtosWorkspacePackageSourceLookup lookup =
                lookup(temporary, node("root", ""), node("member-pkg", "libs/member"));

        assertThrows(IOException.class, () -> lookup.requireSource("root", "Alias"));
        assertEquals(memberApi.toRealPath(), lookup.requireSource("member-pkg", "Api"));
    }

    @Test
    void requiresExactSpellingAndRejectsAsciiCaseFoldAmbiguity() throws Exception {
        writeSource(temporary, "Feature.protos");
        ProtosWorkspacePackageSourceLookup lookup = lookup(temporary, node("root", ""));

        assertThrows(IOException.class, () -> lookup.requireSource("root", "feature"));

        Path folded = temporary.resolve("feature.protos");
        Assumptions.assumeFalse(
                Files.exists(folded),
                "host filesystem does not preserve distinct case spellings");
        writeSource(temporary, "feature.protos");

        assertThrows(IOException.class, () -> lookup.requireSource("root", "Feature"));
        assertThrows(IOException.class, () -> lookup.requireSource("root", "feature"));
    }

    @Test
    void rejectsMissingNonRegularAndInvalidLogicalNames() throws Exception {
        Files.createDirectory(temporary.resolve("Directory.protos"));
        ProtosWorkspacePackageSourceLookup lookup = lookup(temporary, node("root", ""));

        assertThrows(IOException.class, () -> lookup.requireSource("root", "Missing"));
        assertThrows(IOException.class, () -> lookup.requireSource("root", "Directory"));
        assertThrows(IOException.class, () -> lookup.requireSource("root", ""));
        assertThrows(IOException.class, () -> lookup.requireSource("root", "tools//Parser"));
        assertThrows(IOException.class, () -> lookup.requireSource("root", "../Parser"));
        assertThrows(IOException.class, () -> lookup.requireSource("root", "bad-name"));
        assertThrows(IOException.class, () -> lookup.requireSource("root", "CON"));
        assertThrows(IOException.class, () -> lookup.requireSource("missing-pkg", "Main"));
    }

    @Test
    void permitsOnlySymlinkTargetsConfinedToTheSelectedPackageRoot() throws Exception {
        Path workspace = Files.createDirectory(temporary.resolve("workspace"));
        Path realDirectory = Files.createDirectory(workspace.resolve("real"));
        Path realSource = writeSource(realDirectory, "Module.protos");
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path outsideSource = writeSource(outside, "External.protos");

        Path inRootLink = workspace.resolve("Alias.protos");
        try {
            Files.createSymbolicLink(inRootLink, workspace.relativize(realSource));
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "symbolic links unavailable: " + unavailable);
        }

        ProtosWorkspacePackageSourceLookup lookup = lookup(workspace, node("root", ""));
        assertEquals(realSource.toRealPath(), lookup.requireSource("root", "Alias"));

        Path escapingLink = workspace.resolve("Escape.protos");
        Files.createSymbolicLink(escapingLink, outsideSource);
        assertThrows(IOException.class, () -> lookup.requireSource("root", "Escape"));
    }

    @Test
    void rejectsARealSourceOutsideAMemberPackageEvenWhenItRemainsInsideWorkspace() throws Exception {
        Path first = Files.createDirectories(temporary.resolve("libs/first"));
        Path second = Files.createDirectories(temporary.resolve("libs/second"));
        Path siblingSource = writeSource(second, "Sibling.protos");
        Path link = first.resolve("Escape.protos");
        try {
            Files.createSymbolicLink(link, first.relativize(siblingSource));
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "symbolic links unavailable: " + unavailable);
        }

        ProtosWorkspacePackageSourceLookup lookup =
                lookup(
                        temporary,
                        node("root", ""),
                        node("first-pkg", "libs/first"),
                        node("second-pkg", "libs/second"));

        assertThrows(IOException.class, () -> lookup.requireSource("first-pkg", "Escape"));
        assertEquals(siblingSource.toRealPath(), lookup.requireSource("second-pkg", "Sibling"));
    }

    private static Path writeSource(Path directory, String name) throws IOException {
        Path source = directory.resolve(name);
        Files.writeString(source, "value: 1\n");
        return source;
    }

    private static ProtosWorkspacePackageSourceLookup lookup(
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
        return ProtosWorkspacePackageSourceLookup.bind(directoryIndex);
    }

    private static ProtosPackageExecutionPlan.PackageNode node(
            String packageId, String location) {
        return new ProtosPackageExecutionPlan.PackageNode(
                new ProtosPackageExecutionPlan.WorkspaceRef(packageId),
                location,
                Map.of());
    }
}

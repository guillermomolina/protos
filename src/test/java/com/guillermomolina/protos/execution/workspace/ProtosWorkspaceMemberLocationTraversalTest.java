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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosWorkspaceMemberLocationTraversalTest {
    @TempDir Path temporary;

    @Test
    void traversesEveryCanonicalComponentExactlyAndReturnsTheRealDirectory()
            throws Exception {
        Path project = Files.createDirectory(temporary.resolve("project"));
        Path member =
                Files.createDirectories(project.resolve("Libs").resolve("Member_A").resolve("Nested"));

        assertEquals(
                member.toRealPath(),
                ProtosWorkspaceMemberLocationTraversal.requireConfinedMemberDirectory(
                        project.toRealPath(), "Libs/Member_A/Nested"));
        assertThrows(
                IOException.class,
                () ->
                        ProtosWorkspaceMemberLocationTraversal.requireConfinedMemberDirectory(
                                project.toRealPath(), "libs/Member_A/Nested"));
    }

    @Test
    void allowsInRootDirectorySymlinkButCanonicalizesBeforeContinuing() throws Exception {
        Path project = Files.createDirectory(temporary.resolve("project"));
        Path target = Files.createDirectories(project.resolve("storage").resolve("member"));
        Path nested = Files.createDirectory(target.resolve("nested"));
        Path alias = project.resolve("alias");
        createSymbolicLinkOrSkip(alias, project.relativize(target));

        assertEquals(
                nested.toRealPath(),
                ProtosWorkspaceMemberLocationTraversal.requireConfinedMemberDirectory(
                        project.toRealPath(), "alias/nested"));
    }

    @Test
    void rejectsDirectorySymlinkThatEscapesTheRealProjectRoot() throws Exception {
        Path project = Files.createDirectory(temporary.resolve("project"));
        Path outside = Files.createDirectories(temporary.resolve("outside").resolve("member"));
        Path escape = project.resolve("escape");
        createSymbolicLinkOrSkip(escape, outside);

        assertThrows(
                IOException.class,
                () ->
                        ProtosWorkspaceMemberLocationTraversal.requireConfinedMemberDirectory(
                                project.toRealPath(), "escape"));
    }

    @Test
    void rejectsNonCanonicalMemberLocationsBeforeHostTraversal() throws Exception {
        Path project = Files.createDirectory(temporary.resolve("project"));
        Files.createDirectories(project.resolve("a").resolve("b"));
        Path root = project.toRealPath();

        for (String invalid :
                new String[] {"", "/a", "a/", "a//b", "a/./b", "a/../b", "a\\b", "a\0b"}) {
            assertThrows(
                    IOException.class,
                    () ->
                            ProtosWorkspaceMemberLocationTraversal.requireConfinedMemberDirectory(
                                    root, invalid),
                    invalid);
        }
    }

    @Test
    void rejectsNonDirectoryOrMissingProjectRoot() throws Exception {
        Path file = temporary.resolve("file");
        Files.writeString(file, "not a directory");
        assertThrows(
                IOException.class,
                () ->
                        ProtosWorkspaceMemberLocationTraversal.requireConfinedMemberDirectory(
                                file, "member"));
        assertThrows(
                IOException.class,
                () ->
                        ProtosWorkspaceMemberLocationTraversal.requireConfinedMemberDirectory(
                                temporary.resolve("missing"), "member"));
    }

    private static void createSymbolicLinkOrSkip(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "symbolic links unavailable: " + unavailable);
        }
    }
}

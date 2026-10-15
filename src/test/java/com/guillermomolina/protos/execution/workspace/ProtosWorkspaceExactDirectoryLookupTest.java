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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosWorkspaceExactDirectoryLookupTest {
    @TempDir Path temporary;

    @Test
    void returnsOnlyTheExactStoredDirectChildDirectorySpelling() throws Exception {
        Path parent = Files.createDirectory(temporary.resolve("parent"));
        Path member = Files.createDirectory(parent.resolve("Member_A"));

        assertEquals(member,
                ProtosWorkspaceExactDirectoryLookup.requireExactChildDirectory(parent, "Member_A"));
        assertThrows(IOException.class,
                () -> ProtosWorkspaceExactDirectoryLookup.requireExactChildDirectory(parent, "member_a"));
        assertThrows(IOException.class,
                () -> ProtosWorkspaceExactDirectoryLookup.requireExactChildDirectory(parent, "missing"));
    }

    @Test
    void rejectsNonDirectoryTargetsAndInvalidSingleComponentSpellings() throws Exception {
        Path parent = Files.createDirectory(temporary.resolve("parent"));
        Files.writeString(parent.resolve("member"), "not a directory");

        assertThrows(IOException.class,
                () -> ProtosWorkspaceExactDirectoryLookup.requireExactChildDirectory(parent, "member"));
        assertThrows(IOException.class,
                () -> ProtosWorkspaceExactDirectoryLookup.requireExactChildDirectory(parent, ""));
        assertThrows(IOException.class,
                () -> ProtosWorkspaceExactDirectoryLookup.requireExactChildDirectory(parent, "."));
        assertThrows(IOException.class,
                () -> ProtosWorkspaceExactDirectoryLookup.requireExactChildDirectory(parent, ".."));
        assertThrows(IOException.class,
                () -> ProtosWorkspaceExactDirectoryLookup.requireExactChildDirectory(parent, "nested/member"));
    }

    @Test
    void rejectsNonDirectoryParent() throws Exception {
        Path parentFile = temporary.resolve("parent-file");
        Files.writeString(parentFile, "not a directory");
        assertThrows(IOException.class,
                () -> ProtosWorkspaceExactDirectoryLookup.requireExactChildDirectory(parentFile, "member"));
    }
}

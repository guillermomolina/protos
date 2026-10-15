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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Exact confined host traversal for one canonical non-root workspace member location. */
final class ProtosWorkspaceMemberLocationTraversal {
    private ProtosWorkspaceMemberLocationTraversal() {}

    static Path requireConfinedMemberDirectory(Path realProjectRoot, String location)
            throws IOException {
        Objects.requireNonNull(realProjectRoot, "realProjectRoot");
        Objects.requireNonNull(location, "location");

        Path root = realProjectRoot.toRealPath();
        if (!Files.isDirectory(root)) {
            throw new IOException("workspace project root is not a directory");
        }
        if (location.isEmpty()
                || location.startsWith("/")
                || location.contains("\\")
                || location.indexOf('\0') >= 0) {
            throw new IOException("invalid workspace member location");
        }

        Path current = root;
        for (String component : location.split("/", -1)) {
            if (component.isEmpty() || component.equals(".") || component.equals("..")) {
                throw new IOException("invalid workspace member location");
            }

            Path child =
                    ProtosWorkspaceExactDirectoryLookup.requireExactChildDirectory(
                            current, component);
            Path realChild = child.toRealPath();
            if (!Files.isDirectory(realChild) || !realChild.startsWith(root)) {
                throw new IOException("workspace member location escaped project root");
            }
            current = realChild;
        }
        return current;
    }
}

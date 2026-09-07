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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Exact host lookup for one already-separated workspace member-location component. */
final class ProtosWorkspaceExactDirectoryLookup {
    private ProtosWorkspaceExactDirectoryLookup() {}

    static Path requireExactChildDirectory(Path directory, String expectedName)
            throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(expectedName, "expectedName");

        if (expectedName.isEmpty()
                || expectedName.equals(".")
                || expectedName.equals("..")
                || expectedName.indexOf('/') >= 0) {
            throw new IOException("invalid workspace member-location component");
        }
        if (!Files.isDirectory(directory)) {
            throw new IOException("workspace member parent is not a directory");
        }

        Path exact = null;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                Path fileName = entry.getFileName();
                if (fileName != null && fileName.toString().equals(expectedName)) {
                    exact = entry;
                    break;
                }
            }
        }

        if (exact == null || !Files.isDirectory(exact)) {
            throw new IOException("workspace member directory not found");
        }
        return exact;
    }
}

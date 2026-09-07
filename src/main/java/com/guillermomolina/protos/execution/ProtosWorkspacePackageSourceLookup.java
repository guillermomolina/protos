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

/** Exact confined source-file lookup for already-bound workspace packages. */
final class ProtosWorkspacePackageSourceLookup {
    private final ProtosWorkspacePackageDirectoryIndex directoryIndex;

    private ProtosWorkspacePackageSourceLookup(
            ProtosWorkspacePackageDirectoryIndex directoryIndex) {
        this.directoryIndex = directoryIndex;
    }

    static ProtosWorkspacePackageSourceLookup bind(
            ProtosWorkspacePackageDirectoryIndex directoryIndex) {
        return new ProtosWorkspacePackageSourceLookup(
                Objects.requireNonNull(directoryIndex, "directoryIndex"));
    }

    ProtosWorkspacePackageDirectoryIndex directoryIndex() {
        return directoryIndex;
    }

    Path requireSource(String packageId, String logicalModule) throws IOException {
        ProtosWorkspacePackageDirectoryIndex.PackageDirectory packageDirectory =
                directoryIndex.requirePackage(packageId);
        String checkedModule = ProtosPackageRuntimeNames.requireLogicalName(logicalModule);

        Path packageRoot = packageDirectory.directory().toRealPath();
        if (!Files.isDirectory(packageRoot)) {
            throw new IOException("workspace package source root is not a directory");
        }

        String[] segments = checkedModule.split("/", -1);
        Path current = packageRoot;
        for (int index = 0; index < segments.length - 1; index++) {
            Path selected = requireExactChild(current, segments[index], true);
            Path realSelected = selected.toRealPath();
            if (!Files.isDirectory(realSelected) || !realSelected.startsWith(packageRoot)) {
                throw new IOException("workspace package module escaped its package root");
            }
            current = realSelected;
        }

        String sourceName = segments[segments.length - 1] + ".protos";
        Path selectedSource = requireExactChild(current, sourceName, false);
        Path realSource = selectedSource.toRealPath();
        if (!Files.isRegularFile(realSource) || !realSource.startsWith(packageRoot)) {
            throw new IOException("workspace package module escaped its package root");
        }
        return realSource;
    }

    private static Path requireExactChild(
            Path directory, String expectedName, boolean requireDirectory)
            throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("workspace package module not found");
        }

        Path exact = null;
        int caseFoldMatches = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                Path fileName = entry.getFileName();
                if (fileName == null) {
                    continue;
                }
                String actualName = fileName.toString();
                if (equalsAsciiIgnoreCase(actualName, expectedName)) {
                    caseFoldMatches++;
                    if (actualName.equals(expectedName)) {
                        exact = entry;
                    }
                }
            }
        }

        if (caseFoldMatches > 1) {
            throw new IOException("ambiguous workspace package module path spelling");
        }
        if (exact == null) {
            throw new IOException("workspace package module not found");
        }
        if (requireDirectory ? !Files.isDirectory(exact) : !Files.isRegularFile(exact)) {
            throw new IOException("workspace package module not found");
        }
        return exact;
    }

    private static boolean equalsAsciiIgnoreCase(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        for (int index = 0; index < left.length(); index++) {
            if (asciiLower(left.charAt(index)) != asciiLower(right.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static char asciiLower(char value) {
        return value >= 'A' && value <= 'Z'
                ? (char) (value + ('a' - 'A'))
                : value;
    }
}

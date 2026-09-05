// THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
// ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
// DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
// DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
// OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
// THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
// OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
// THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
// FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
// https://github.com/guillermomolina/protos
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
// the specific language governing rights and limitations under the License.
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Host resolver for modules shipped in the Protos standard distribution. */
public final class ProtosStandardLibraryModuleResolver implements ProtosModuleResolver {
    private static final String PREFIX = "std:";
    private static final Set<String> WINDOWS_RESERVED_SEGMENTS =
            Set.of(
                    "CON",
                    "PRN",
                    "AUX",
                    "NUL",
                    "COM1",
                    "COM2",
                    "COM3",
                    "COM4",
                    "COM5",
                    "COM6",
                    "COM7",
                    "COM8",
                    "COM9",
                    "LPT1",
                    "LPT2",
                    "LPT3",
                    "LPT4",
                    "LPT5",
                    "LPT6",
                    "LPT7",
                    "LPT8",
                    "LPT9");

    private final Path libraryRoot;

    public ProtosStandardLibraryModuleResolver(Path libraryRoot) {
        this.libraryRoot =
                Objects.requireNonNull(libraryRoot, "libraryRoot")
                        .toAbsolutePath()
                        .normalize();
    }

    @Override
    public ProtosModuleKey resolve(
            String exactSpecifier, Optional<ProtosModuleKey> importingModule)
            throws IOException {
        Objects.requireNonNull(exactSpecifier, "exactSpecifier");
        Objects.requireNonNull(importingModule, "importingModule");

        String logicalName = requireStandardLogicalName(exactSpecifier);
        sourcePath(logicalName);
        return new ProtosModuleKey(PREFIX + logicalName);
    }

    @Override
    public String loadSource(ProtosModuleKey key) throws IOException {
        Objects.requireNonNull(key, "key");
        String logicalName = requireStandardLogicalName(key.canonicalId());
        return Files.readString(sourcePath(logicalName), StandardCharsets.UTF_8);
    }

    private Path sourcePath(String logicalName) throws IOException {
        String[] segments = logicalName.split("/", -1);
        Path current = libraryRoot;
        for (int i = 0; i < segments.length - 1; i++) {
            current = requireExactChild(current, segments[i], true);
        }

        Path source =
                requireExactChild(
                        current, segments[segments.length - 1] + ".protos", false);
        Path realRoot = libraryRoot.toRealPath();
        Path realSource = source.toRealPath();
        if (!realSource.startsWith(realRoot)) {
            throw new IOException("standard-library module escaped its distribution root");
        }
        return source;
    }

    private static Path requireExactChild(
            Path directory, String expectedName, boolean requireDirectory)
            throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("standard-library module not found");
        }

        Path exact = null;
        int caseFoldMatches = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                String actualName = entry.getFileName().toString();
                if (equalsAsciiIgnoreCase(actualName, expectedName)) {
                    caseFoldMatches++;
                    if (actualName.equals(expectedName)) {
                        exact = entry;
                    }
                }
            }
        }

        if (caseFoldMatches > 1) {
            throw new IOException("ambiguous standard-library path spelling");
        }
        if (exact == null) {
            throw new IOException("standard-library module not found");
        }
        if (requireDirectory ? !Files.isDirectory(exact) : !Files.isRegularFile(exact)) {
            throw new IOException("standard-library module not found");
        }
        return exact;
    }

    private static String requireStandardLogicalName(String identifier) throws IOException {
        if (!identifier.startsWith(PREFIX)) {
            throw new IOException("unsupported module specifier");
        }

        String logicalName = identifier.substring(PREFIX.length());
        if (logicalName.isEmpty()) {
            throw new IOException("invalid standard-library module name");
        }

        String[] segments = logicalName.split("/", -1);
        if (segments[0].equalsIgnoreCase("core")) {
            throw new IOException("invalid standard-library module name");
        }
        for (String segment : segments) {
            if (!isPortableSegment(segment) || isWindowsReservedSegment(segment)) {
                throw new IOException("invalid standard-library module name");
            }
        }
        return logicalName;
    }

    private static boolean isPortableSegment(String segment) {
        if (segment.isEmpty() || !isAsciiLetter(segment.charAt(0))) {
            return false;
        }
        for (int i = 1; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (!isAsciiLetter(c) && !isAsciiDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    private static boolean isWindowsReservedSegment(String segment) {
        return WINDOWS_RESERVED_SEGMENTS.contains(segment.toUpperCase(Locale.ROOT));
    }

    private static boolean equalsAsciiIgnoreCase(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        for (int i = 0; i < left.length(); i++) {
            if (asciiLower(left.charAt(i)) != asciiLower(right.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static char asciiLower(char c) {
        return c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }
}

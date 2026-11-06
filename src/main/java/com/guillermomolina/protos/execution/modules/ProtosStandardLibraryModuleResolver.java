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

import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Host resolver for modules shipped in the Protos standard distribution.
 *
 * <p>D242: {@code std:<logical/path>} resolves only to the MODULE whose initializer is exactly
 * {@code <logical/path>/module.protos}. MODULE path segments use the lowercase/underscore
 * spelling; no {@code .protos} suffix is added and no neighbouring file is consulted. An explicitly
 * relative {@code ./X.protos} or {@code ../X.protos} specifier from a Standard Library MODULE
 * initializer or SOURCE resolves to that exact SOURCE file inside the distribution root. A
 * {@code module.protos} file is never importable as SOURCE.
 */
public final class ProtosStandardLibraryModuleResolver implements ProtosModuleResolver {
    private static final String PREFIX = "std:";
    static final String MODULE_INITIALIZER = "module.protos";
    private static final String SOURCE_SUFFIX = ".protos";
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

    /** Canonical MODULE identity of {@code std:<logicalName>}; performs no file access. */
    public static ProtosModuleKey moduleKey(String logicalName) {
        return ProtosModuleKey.module(PREFIX + logicalName);
    }

    @Override
    public ProtosModuleKey resolve(
            String exactSpecifier, Optional<ProtosModuleKey> importingModule)
            throws IOException {
        Objects.requireNonNull(exactSpecifier, "exactSpecifier");
        Objects.requireNonNull(importingModule, "importingModule");

        if (exactSpecifier.startsWith("./") || exactSpecifier.startsWith("../")) {
            ProtosModuleKey importer =
                    importingModule.orElseThrow(
                            () -> new IOException("relative SOURCE import requires an importer"));
            String sourcePath =
                    resolveRelativeSourcePath(owningDirectory(importer), exactSpecifier);
            sourceFile(sourcePath);
            return ProtosModuleKey.source(PREFIX + sourcePath);
        }
        String logicalName = requireModuleLogicalName(exactSpecifier);
        moduleInitializer(logicalName);
        return moduleKey(logicalName);
    }

    @Override
    public ProtosModuleSource loadSource(ProtosModuleKey key) throws IOException {
        Objects.requireNonNull(key, "key");
        if (key.isModule()) {
            return ProtosModuleSource.fromPath(
                    key, moduleInitializer(requireModuleLogicalName(key.canonicalId())));
        }
        if (key.isSource()) {
            return ProtosModuleSource.fromPath(key, sourceFile(requireSourcePath(key)));
        }
        throw new IOException("standard-library resolver owns only MODULE and SOURCE keys");
    }

    /** True when {@code key} is a Standard Library MODULE or SOURCE identity. */
    public static boolean owns(ProtosModuleKey key) {
        return key != null && !key.isForeign() && key.canonicalId().startsWith(PREFIX);
    }

    private static String owningDirectory(ProtosModuleKey importer) throws IOException {
        if (importer.isModule()) {
            return requireModuleLogicalName(importer.canonicalId());
        }
        if (importer.isSource()) {
            String path = requireSourcePath(importer);
            int slash = path.lastIndexOf('/');
            return slash < 0 ? "" : path.substring(0, slash);
        }
        throw new IOException("relative SOURCE import requires a Standard Library importer");
    }

    private static String requireSourcePath(ProtosModuleKey key) throws IOException {
        String identifier = key.canonicalId();
        if (!key.isSource() || !identifier.startsWith(PREFIX)) {
            throw new IOException("invalid standard-library SOURCE identity");
        }
        String path = identifier.substring(PREFIX.length());
        String checked = resolveRelativeSourcePath("", "./" + path);
        if (!checked.equals(path)) {
            throw new IOException("non-canonical standard-library SOURCE identity");
        }
        return checked;
    }

    private static String resolveRelativeSourcePath(String owningDirectory, String specifier)
            throws IOException {
        if (!specifier.endsWith(SOURCE_SUFFIX)) {
            throw new IOException("relative SOURCE import must name an exact .protos file");
        }
        java.util.ArrayList<String> segments = new java.util.ArrayList<>();
        if (!owningDirectory.isEmpty()) {
            segments.addAll(java.util.List.of(owningDirectory.split("/", -1)));
        }
        String[] parts = specifier.split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            boolean last = i == parts.length - 1;
            if (part.equals(".") && i == 0) {
                continue;
            }
            if (part.equals("..")) {
                if (segments.isEmpty()) {
                    throw new IOException("relative SOURCE import escapes the distribution root");
                }
                segments.remove(segments.size() - 1);
                continue;
            }
            String segment =
                    last ? part.substring(0, part.length() - SOURCE_SUFFIX.length()) : part;
            if (!isPortableSegment(segment) || isWindowsReservedSegment(segment)) {
                throw new IOException("invalid standard-library SOURCE path");
            }
            segments.add(segment);
        }
        if (segments.isEmpty()) {
            throw new IOException("invalid standard-library SOURCE path");
        }
        if (segments.get(segments.size() - 1).equalsIgnoreCase("module")) {
            throw new IOException("module initializer cannot be imported as SOURCE");
        }
        if (segments.get(0).equalsIgnoreCase("core")) {
            throw new IOException("Core bootstrap sources are not importable");
        }
        return String.join("/", segments) + SOURCE_SUFFIX;
    }

    private Path moduleInitializer(String logicalName) throws IOException {
        String[] segments = logicalName.split("/", -1);
        Path current = libraryRoot;
        for (String segment : segments) {
            current = requireExactChild(current, segment, true);
        }
        return confined(requireExactChild(current, MODULE_INITIALIZER, false));
    }

    private Path sourceFile(String sourcePath) throws IOException {
        String[] segments = sourcePath.split("/", -1);
        Path current = libraryRoot;
        for (int i = 0; i < segments.length - 1; i++) {
            current = requireExactChild(current, segments[i], true);
        }
        return confined(requireExactChild(current, segments[segments.length - 1], false));
    }

    private Path confined(Path source) throws IOException {
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

    private static String requireModuleLogicalName(String identifier) throws IOException {
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
            if (!isPortableSegment(segment)
                    || isWindowsReservedSegment(segment)
                    || !isLowercaseSegment(segment)) {
                throw new IOException("invalid standard-library module name");
            }
        }
        return logicalName;
    }

    /** D242 public MODULE routes use lowercase/underscore spelling only. */
    private static boolean isLowercaseSegment(String segment) {
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                return false;
            }
        }
        return true;
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

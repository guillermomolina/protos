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

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Host resolver for one exact Protos tool bundled with the selected toolchain.
 *
 * <p>The resolver accepts {@code self:} only for tool-local modules, an explicitly configured
 * private shared-bootstrap namespace only inside the bundled-tool/toolchain closure, and
 * delegates {@code std:} to the selected Standard Library resolver. It never searches project
 * manifests, lockfiles, package stores, working directories, or ambient module paths.
 *
 * <p>{@code bundled-tool:...} is an internal ModuleKey namespace, not a user-visible import
 * specifier.
 */
public final class ProtosBundledToolModuleResolver implements ProtosModuleResolver {
    private static final String SELF_PREFIX = "self:";
    private static final String SHARED_PREFIX = "tool-shared:";
    private static final String TOOL_KEY_PREFIX = "bundled-tool:";
    private static final String SHARED_KEY_PREFIX = "bundled-tool-shared:";
    private static final Set<String> WINDOWS_RESERVED_SEGMENTS =
            Set.of(
                    "CON", "PRN", "AUX", "NUL",
                    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
                    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private final Path toolRoot;
    private final Optional<Path> sharedRoot;
    private final String canonicalPrefix;
    private final ProtosModuleResolver standardLibraryResolver;

    public ProtosBundledToolModuleResolver(
            String toolName, Path toolRoot, ProtosModuleResolver standardLibraryResolver) {
        this(toolName, toolRoot, null, standardLibraryResolver);
    }

    public ProtosBundledToolModuleResolver(
            String toolName,
            Path toolRoot,
            Path sharedRoot,
            ProtosModuleResolver standardLibraryResolver) {
        Objects.requireNonNull(toolName, "toolName");
        if (!isPortableSegment(toolName) || isWindowsReservedSegment(toolName)) {
            throw new IllegalArgumentException("invalid bundled-tool name");
        }
        this.toolRoot = Objects.requireNonNull(toolRoot, "toolRoot").toAbsolutePath().normalize();
        this.sharedRoot =
                Optional.ofNullable(sharedRoot).map(path -> path.toAbsolutePath().normalize());
        this.canonicalPrefix = TOOL_KEY_PREFIX + toolName + "/";
        this.standardLibraryResolver =
                Objects.requireNonNull(standardLibraryResolver, "standardLibraryResolver");
    }

    public ProtosModuleKey entryModule(String logicalName) throws IOException {
        String exactLogicalName = requireLogicalName(logicalName);
        sourcePath(exactLogicalName);
        return new ProtosModuleKey(canonicalPrefix + exactLogicalName);
    }

    @Override
    public ProtosModuleKey resolve(
            String exactSpecifier, Optional<ProtosModuleKey> importingModule)
            throws Exception {
        Objects.requireNonNull(exactSpecifier, "exactSpecifier");
        Objects.requireNonNull(importingModule, "importingModule");

        if (exactSpecifier.startsWith("std:")) {
            return standardLibraryResolver.resolve(exactSpecifier, importingModule);
        }
        if (exactSpecifier.startsWith(SHARED_PREFIX)) {
            Path selectedSharedRoot =
                    sharedRoot.orElseThrow(
                            () -> new IOException("shared bundled-tool modules unavailable"));
            if (importingModule.isEmpty()) {
                throw new IOException("shared bundled-tool import requires importer");
            }
            String importerId = importingModule.orElseThrow().canonicalId();
            if (!importerId.startsWith(canonicalPrefix)
                    && !importerId.startsWith(SHARED_KEY_PREFIX)) {
                throw new IOException("shared bundled-tool import escaped toolchain closure");
            }
            String logicalName =
                    requireLogicalName(exactSpecifier.substring(SHARED_PREFIX.length()));
            sourcePath(selectedSharedRoot, logicalName);
            return new ProtosModuleKey(SHARED_KEY_PREFIX + logicalName);
        }
        if (!exactSpecifier.startsWith(SELF_PREFIX)) {
            throw new IOException("unsupported bundled-tool module specifier");
        }
        if (importingModule.isPresent()
                && !importingModule.orElseThrow().canonicalId().startsWith(canonicalPrefix)) {
            throw new IOException("self import escaped bundled-tool closure");
        }

        String logicalName =
                requireLogicalName(exactSpecifier.substring(SELF_PREFIX.length()));
        sourcePath(logicalName);
        return new ProtosModuleKey(canonicalPrefix + logicalName);
    }

    @Override
    public ProtosModuleSource loadSource(ProtosModuleKey key) throws Exception {
        Objects.requireNonNull(key, "key");
        String canonicalId = key.canonicalId();
        if (canonicalId.startsWith("std:")) {
            return standardLibraryResolver.loadSource(key);
        }
        if (canonicalId.startsWith(SHARED_KEY_PREFIX)) {
            Path selectedSharedRoot =
                    sharedRoot.orElseThrow(
                            () -> new IOException("shared bundled-tool modules unavailable"));
            String logicalName =
                    requireLogicalName(canonicalId.substring(SHARED_KEY_PREFIX.length()));
            return ProtosModuleSource.fromPath(key, sourcePath(selectedSharedRoot, logicalName));
        }
        if (!canonicalId.startsWith(canonicalPrefix)) {
            throw new IOException("module is outside bundled-tool closure");
        }

        String logicalName =
                requireLogicalName(canonicalId.substring(canonicalPrefix.length()));
        Path source = sourcePath(toolRoot, logicalName);
        return ProtosModuleSource.fromPath(key, source);
    }

    private Path sourcePath(String logicalName) throws IOException {
        return sourcePath(toolRoot, logicalName);
    }

    private static Path sourcePath(Path root, String logicalName) throws IOException {
        String[] segments = logicalName.split("/", -1);
        Path current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            current = requireExactChild(current, segments[i], true);
        }

        Path source =
                requireExactChild(
                        current, segments[segments.length - 1] + ".protos", false);
        Path realRoot = root.toRealPath();
        Path realSource = source.toRealPath();
        if (!realSource.startsWith(realRoot)) {
            throw new IOException("bundled-tool module escaped its distribution root");
        }
        return source;
    }

    private static Path requireExactChild(
            Path directory, String expectedName, boolean requireDirectory)
            throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("bundled-tool module not found");
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
            throw new IOException("ambiguous bundled-tool path spelling");
        }
        if (exact == null) {
            throw new IOException("bundled-tool module not found");
        }
        if (requireDirectory ? !Files.isDirectory(exact) : !Files.isRegularFile(exact)) {
            throw new IOException("bundled-tool module not found");
        }
        return exact;
    }

    private static String requireLogicalName(String logicalName) throws IOException {
        if (logicalName.isEmpty()) {
            throw new IOException("invalid bundled-tool module name");
        }
        for (String segment : logicalName.split("/", -1)) {
            if (!isPortableSegment(segment) || isWindowsReservedSegment(segment)) {
                throw new IOException("invalid bundled-tool module name");
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

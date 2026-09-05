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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Host resolver for modules shipped in the Protos standard distribution. */
public final class ProtosStandardLibraryModuleResolver implements ProtosModuleResolver {
    private static final String PREFIX = "std:";

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
        Path source = sourcePath(logicalName);
        if (!Files.isRegularFile(source)) {
            throw new IOException("standard-library module not found");
        }
        return new ProtosModuleKey(PREFIX + logicalName);
    }

    @Override
    public String loadSource(ProtosModuleKey key) throws IOException {
        Objects.requireNonNull(key, "key");
        String logicalName = requireStandardLogicalName(key.canonicalId());
        Path source = sourcePath(logicalName);
        if (!Files.isRegularFile(source)) {
            throw new IOException("standard-library module not found");
        }
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    private Path sourcePath(String logicalName) {
        Path source = libraryRoot.resolve(logicalName + ".protos").normalize();
        if (!source.startsWith(libraryRoot)) {
            throw new IllegalStateException("validated standard-library path escaped its root");
        }
        return source;
    }

    private static String requireStandardLogicalName(String identifier) throws IOException {
        if (!identifier.startsWith(PREFIX)) {
            throw new IOException("unsupported module specifier");
        }

        String logicalName = identifier.substring(PREFIX.length());
        if (logicalName.isEmpty()
                || logicalName.equals("core")
                || logicalName.startsWith("core/")) {
            throw new IOException("invalid standard-library module name");
        }

        String[] segments = logicalName.split("/", -1);
        for (String segment : segments) {
            if (!isPortableSegment(segment)) {
                throw new IOException("invalid standard-library module name");
            }
        }
        return logicalName;
    }

    private static boolean isPortableSegment(String segment) {
        if (segment.isEmpty() || !isAsciiLower(segment.charAt(0))) {
            return false;
        }
        for (int i = 1; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (!isAsciiLower(c) && !isAsciiDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiLower(char c) {
        return c >= 'a' && c <= 'z';
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }
}

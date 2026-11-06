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

import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Defensive host validation of the already-frozen F2D1 package runtime-name ABI. */
final class ProtosPackageRuntimeNames {
    private static final Set<String> WINDOWS_RESERVED =
            Set.of(
                    "CON", "PRN", "AUX", "NUL",
                    "COM1", "COM2", "COM3", "COM4", "COM5",
                    "COM6", "COM7", "COM8", "COM9",
                    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5",
                    "LPT6", "LPT7", "LPT8", "LPT9");

    private ProtosPackageRuntimeNames() {}

    static String requireAlias(String alias) throws IOException {
        requireSegment(alias);
        return alias;
    }

    static String requireLogicalName(String logicalName) throws IOException {
        if (logicalName == null || logicalName.isEmpty()) {
            throw new IOException("invalid package logical module name");
        }
        for (String segment : logicalName.split("/", -1)) {
            requireSegment(segment);
        }
        return logicalName;
    }

    /** D242 file name of every logical module initializer. */
    static final String MODULE_INITIALIZER = "module.protos";

    static final String SOURCE_SUFFIX = ".protos";

    /**
     * D242 target-name validation. A MODULE name is a logical module directory. A SOURCE name is
     * the exact package-relative file path without its {@code .protos} suffix; its final segment
     * may never be {@code module}, so a SOURCE identity cannot alias a MODULE initializer.
     */
    static String requireTargetName(ProtosModuleKey.Kind kind, String logicalName)
            throws IOException {
        requireLogicalName(logicalName);
        if (kind == ProtosModuleKey.Kind.SOURCE) {
            String last = logicalName.substring(logicalName.lastIndexOf('/') + 1);
            if (last.equals("module")) {
                throw new IOException("module initializer cannot be imported as SOURCE");
            }
        } else if (kind != ProtosModuleKey.Kind.MODULE) {
            throw new IOException("package targets are MODULE or SOURCE");
        }
        return logicalName;
    }

    static String kindTag(ProtosModuleKey.Kind kind) {
        return switch (kind) {
            case MODULE -> "module";
            case SOURCE -> "source";
            case FOREIGN -> "foreign";
        };
    }

    /** Exact package-relative resource path for one already-validated target name. */
    static String resourcePath(ProtosModuleKey.Kind kind, String logicalName) {
        return kind == ProtosModuleKey.Kind.MODULE
                ? logicalName + "/" + MODULE_INITIALIZER
                : logicalName + SOURCE_SUFFIX;
    }

    /** Package-relative directory that owns relative SOURCE imports from the given target. */
    static String owningDirectory(ProtosModuleKey.Kind kind, String logicalName) {
        if (kind == ProtosModuleKey.Kind.MODULE) {
            return logicalName;
        }
        int slash = logicalName.lastIndexOf('/');
        return slash < 0 ? "" : logicalName.substring(0, slash);
    }

    static boolean isRelativeSourceSpecifier(String specifier) {
        return specifier != null
                && (specifier.startsWith("./") || specifier.startsWith("../"))
                && specifier.endsWith(SOURCE_SUFFIX);
    }

    /**
     * Resolves one explicitly relative SOURCE specifier against its owning package directory and
     * returns the SOURCE target name. Traversal above the package root is rejected; the
     * {@code module.protos} initializer is never resolvable as SOURCE.
     */
    static String resolveRelativeSource(String owningDirectory, String specifier)
            throws IOException {
        if (!isRelativeSourceSpecifier(specifier)) {
            throw new IOException("relative SOURCE import must be ./ or ../ and end in .protos");
        }
        List<String> segments = new ArrayList<>();
        if (!owningDirectory.isEmpty()) {
            segments.addAll(List.of(owningDirectory.split("/", -1)));
        }
        String path = specifier.substring(0, specifier.length() - SOURCE_SUFFIX.length());
        for (String segment : path.split("/", -1)) {
            switch (segment) {
                case "." -> { }
                case ".." -> {
                    if (segments.isEmpty()) {
                        throw new IOException("relative SOURCE import escapes package root");
                    }
                    segments.remove(segments.size() - 1);
                }
                default -> segments.add(segment);
            }
        }
        if (segments.isEmpty()) {
            throw new IOException("empty relative SOURCE import");
        }
        return requireTargetName(ProtosModuleKey.Kind.SOURCE, String.join("/", segments));
    }

    private static void requireSegment(String segment) throws IOException {
        if (segment == null || segment.isEmpty() || !isAsciiLetter(segment.charAt(0))) {
            throw new IOException("invalid package runtime-name segment");
        }
        for (int index = 1; index < segment.length(); index++) {
            char c = segment.charAt(index);
            if (!isAsciiLetter(c) && !isAsciiDigit(c) && c != '_') {
                throw new IOException("invalid package runtime-name segment");
            }
        }
        if (WINDOWS_RESERVED.contains(segment.toUpperCase(Locale.ROOT))) {
            throw new IOException("reserved package runtime-name segment");
        }
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }
}

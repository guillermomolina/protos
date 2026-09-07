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

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

/**
 * Mechanical lookup of the installed toolchain roots shared by tool adapters.
 *
 * <p>The base is {@code PROTOS_HOME} when configured, otherwise the current
 * working directory; this class owns no layout policy beyond that existing
 * convention.</p>
 */
public final class ProtosToolchainRoots {
    private ProtosToolchainRoots() {}

    public static Path core() throws IOException {
        String home = System.getenv("PROTOS_HOME");
        Path base =
                home == null || home.isBlank()
                        ? Path.of("").toAbsolutePath()
                        : Path.of(home);
        Path core = base.resolve("protos/lib/core");
        if (!Files.isDirectory(core)) {
            throw new IOException(
                    "cannot locate protos/lib/core; run via bin/protos or set PROTOS_HOME");
        }
        return core;
    }

    public static Path formatterTool(Path core) {
        return core.getParent().getParent().resolve("tools").resolve("formatter");
    }
}

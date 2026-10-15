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

import java.util.List;
import java.util.Objects;

/**
 * Host-internal abstract package-relative resource name for immutable custody reads (PLAT012).
 *
 * <p>The name is a non-empty sequence of exact child names separated by {@code /}. Absolute
 * names, empty components, {@code .}, {@code ..} and NUL are rejected, so a name can only
 * descend from the package root of a captured tree. It is neither a guest Path nor a host
 * {@link java.nio.file.Path} and carries no physical backing representation.
 */
record ProtosPackageResourceName(List<String> components) {
    ProtosPackageResourceName {
        components = List.copyOf(Objects.requireNonNull(components, "components"));
        if (components.isEmpty()) {
            throw new IllegalArgumentException("empty package resource name");
        }
        for (String component : components) {
            if (component.isEmpty()
                    || component.equals(".")
                    || component.equals("..")
                    || component.indexOf('/') >= 0
                    || component.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("malformed package resource name component");
            }
        }
    }

    /** Parses a {@code /}-separated relative name such as {@code src/Main.protos}. */
    static ProtosPackageResourceName parse(String relativeName) {
        Objects.requireNonNull(relativeName, "relativeName");
        return new ProtosPackageResourceName(List.of(relativeName.split("/", -1)));
    }
}

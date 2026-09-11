/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THIS LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
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

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;

/**
 * PLAT022 Context-local readability authority for already-selected physical Protos Sources.
 *
 * <p>This object is not a resolver. It grants a read-only host-filesystem delegate only for exact
 * D065 absolute lexically-normalized paths admitted by the owning Process Context. Every unrelated
 * path falls through to deny-I/O. The mutable allow-set is private to one Context and is never
 * semantic module identity or public guest filesystem authority.
 */
final class ProtosSourceReadabilityAuthority {
    private final Set<Path> admittedPaths = ConcurrentHashMap.newKeySet();
    private final FileSystem fileSystem;
    private final IOAccess ioAccess;

    ProtosSourceReadabilityAuthority() {
        FileSystem readOnlyHost =
                FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem());
        this.fileSystem =
                FileSystem.newCompositeFileSystem(
                        FileSystem.newDenyIOFileSystem(),
                        FileSystem.Selector.of(readOnlyHost, this::isAdmitted));
        this.ioAccess =
                IOAccess.newBuilder()
                        .fileSystem(fileSystem)
                        .allowHostSocketAccess(false)
                        .build();
    }

    void admit(Path path) {
        admittedPaths.add(exact(path));
    }

    IOAccess ioAccess() {
        return ioAccess;
    }

    FileSystem fileSystemForTesting() {
        return fileSystem;
    }

    boolean isAdmittedForTesting(Path path) {
        return isAdmitted(path);
    }

    int admittedPathCountForTesting() {
        return admittedPaths.size();
    }

    private boolean isAdmitted(Path path) {
        return admittedPaths.contains(exact(path));
    }

    private static Path exact(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }
}

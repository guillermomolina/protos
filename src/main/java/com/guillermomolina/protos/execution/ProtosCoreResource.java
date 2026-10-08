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

import com.oracle.truffle.api.InternalResource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * PLAT054 packaged Core: the distributed {@code protos/lib} tree carried inside the Protos JAR as a
 * Truffle internal resource, the last origin of the standard embedding's Core resolution.
 *
 * <p>The build (Maven {@code package-core-internal-resource}) copies {@code protos/lib} below
 * {@link #RESOURCE_ROOT}, writes the Truffle file list, and records a SHA-256 over the tree. Truffle
 * unpacks the files once per content hash into its own versioned internal-resource cache (in a
 * native image, next to the image at build time), so unpacking is per Core content, never per
 * Context or per call, and nothing Context-scoped survives the Context. The unpacked tree keeps
 * the distribution layout {@code lib/core} and {@code lib/<std modules>}, so Core bootstrap, the
 * {@code std:} resolver, and {@code ModuleKey} identities are exactly those of a distribution
 * directory.
 *
 * <p>The unpacked location is an implementation resource read by the host side of the runtime. It
 * is never handed to the guest and confers no filesystem authority ({@code PROCESS_IO.md} Standard
 * Polyglot embedding bootstrap and authority).
 */
@InternalResource.Id(ProtosCoreResource.ID)
final class ProtosCoreResource implements InternalResource {
    static final String ID = "core";

    /** Library tree below the unpacked resource root, matching {@code protos/lib}. */
    static final String LIBRARY_DIRECTORY = "lib";

    private static final Path RESOURCE_ROOT = Path.of("META-INF", "resources", "protos", ID);

    @Override
    public void unpackFiles(Env env, Path targetDirectory) throws IOException {
        env.unpackResourceFiles(RESOURCE_ROOT.resolve("files"), targetDirectory, RESOURCE_ROOT);
    }

    @Override
    public String versionHash(Env env) throws IOException {
        List<String> lines = env.readResourceLines(RESOURCE_ROOT.resolve("sha256"));
        if (lines.isEmpty() || lines.get(0).isBlank()) {
            throw new IOException("packaged Protos Core has no content hash");
        }
        return lines.get(0).trim();
    }
}

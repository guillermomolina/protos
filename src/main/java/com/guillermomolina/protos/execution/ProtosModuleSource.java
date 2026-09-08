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
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Canonical source payload returned by the host module-resolution boundary.
 *
 * <p>The {@link ProtosModuleKey} remains the semantic module identity used by Core caching. The
 * Truffle {@link Source} is implementation/tooling identity for the exact source unit resolved for
 * that key; it does not redefine Protos module identity.
 */
public record ProtosModuleSource(ProtosModuleKey key, Source source) {
    public ProtosModuleSource {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");
        if (!ProtosLanguage.ID.equals(source.getLanguage())) {
            throw new IllegalArgumentException("module Source belongs to another language");
        }
        if (!source.hasCharacters()) {
            throw new IllegalArgumentException("Protos module Source must provide characters");
        }
    }

    public static ProtosModuleSource fromCharacters(
            ProtosModuleKey key, CharSequence characters) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(characters, "characters");
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, key.canonicalId())
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        return new ProtosModuleSource(key, source);
    }

    public static ProtosModuleSource fromPath(ProtosModuleKey key, Path path)
            throws IOException {
        Objects.requireNonNull(key, "key");
        Path exact = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        String characters = Files.readString(exact, StandardCharsets.UTF_8);
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, exact.getFileName().toString())
                        .uri(exact.toUri())
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        return new ProtosModuleSource(key, source);
    }

    /** Fail closed if a resolver returns source for a different canonical module identity. */
    public ProtosModuleSource requireKey(ProtosModuleKey expected) {
        Objects.requireNonNull(expected, "expected");
        if (!key.equals(expected)) {
            throw new IllegalArgumentException("module resolver returned source for a different ModuleKey");
        }
        return this;
    }

    public String characters() {
        return source.getCharacters().toString();
    }
}

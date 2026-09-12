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

package com.guillermomolina.protos.lsp;

import com.guillermomolina.protos.analysis.ProtosDocumentSnapshot;
import com.guillermomolina.protos.analysis.ProtosDocumentSymbol;
import com.guillermomolina.protos.analysis.ProtosDocumentSymbols;
import com.guillermomolina.protos.analysis.ProtosProjectBinding;
import com.guillermomolina.protos.analysis.ProtosStaticAnalysisCore;
import com.guillermomolina.protos.analysis.ProtosStaticParseResult;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Session-local incremental D082 index for one exact canonical ProjectBinding root. */
final class ProtosWorkspaceSymbolIndex {
    private final Path projectRoot;
    private final ProtosStaticAnalysisCore analysis = new ProtosStaticAnalysisCore();
    private final Map<SourceKey, CachedSource> cache = new HashMap<>();

    ProtosWorkspaceSymbolIndex(Path projectRoot) {
        this.projectRoot = requireAbsoluteNormalized(projectRoot, "projectRoot");
    }

    synchronized List<ProtosWorkspaceSymbolSearch.Candidate> snapshot(
            ProtosProjectBinding binding,
            Function<String, Optional<ProtosDocumentSnapshot>> overlayByUri) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(overlayByUri, "overlayByUri");
        if (!projectRoot.equals(binding.projection().canonicalProjectRoot())) {
            throw new IllegalArgumentException("ProjectBinding root does not match index domain");
        }

        ArrayList<ProtosWorkspaceSymbolSearch.Candidate> candidates = new ArrayList<>();
        Set<SourceKey> currentSources = new HashSet<>();
        for (ProtosProjectBinding.Source source : binding.sources()) {
            SourceKey key = new SourceKey(source.packageId(), source.logicalModule(), source.source());
            currentSources.add(key);
            String sourceUri = source.source().toUri().toString();
            Optional<ProtosDocumentSnapshot> overlay = overlayByUri.apply(sourceUri);
            CachedSource current = overlay.isPresent()
                    ? overlay(key, sourceUri, overlay.get())
                    : disk(key, sourceUri);
            if (current != null) {
                candidates.addAll(current.candidates());
            }
        }
        cache.keySet().removeIf(key -> !currentSources.contains(key));
        return List.copyOf(candidates);
    }

    private CachedSource overlay(
            SourceKey key,
            String sourceUri,
            ProtosDocumentSnapshot snapshot) {
        SourceState state = new OverlayState(snapshot);
        CachedSource existing = cache.get(key);
        if (existing != null && existing.state().equals(state)) {
            return existing;
        }
        CachedSource parsed = parse(key, sourceUri, snapshot.characters(), state, snapshot.version());
        cache.put(key, parsed);
        return parsed;
    }

    private CachedSource disk(SourceKey key, String sourceUri) {
        StableSource stable;
        try {
            stable = readStableSource(key.source());
        } catch (IOException rejected) {
            cache.remove(key);
            return null;
        }

        SourceState state = new DiskState(stable.digest());
        CachedSource existing = cache.get(key);
        if (existing != null && existing.state().equals(state)) {
            return existing;
        }
        CachedSource parsed = parse(key, sourceUri, stable.characters(), state, 0L);
        cache.put(key, parsed);
        return parsed;
    }

    private CachedSource parse(
            SourceKey key,
            String sourceUri,
            String characters,
            SourceState state,
            long version) {
        ProtosDocumentSnapshot snapshot =
                new ProtosDocumentSnapshot(sourceUri, version, characters);
        ProtosStaticParseResult result = analysis.parse(snapshot);
        if (!(result instanceof ProtosStaticParseResult.Parsed parsed)) {
            return new CachedSource(state, List.of());
        }

        ArrayList<ProtosWorkspaceSymbolSearch.Candidate> candidates = new ArrayList<>();
        for (ProtosDocumentSymbol symbol : ProtosDocumentSymbols.from(parsed.program())) {
            flatten(key, sourceUri, characters, symbol, candidates);
        }
        return new CachedSource(state, List.copyOf(candidates));
    }

    private void flatten(
            SourceKey key,
            String sourceUri,
            String characters,
            ProtosDocumentSymbol symbol,
            List<ProtosWorkspaceSymbolSearch.Candidate> destination) {
        destination.add(new ProtosWorkspaceSymbolSearch.Candidate(
                symbol.name(),
                projectRoot,
                key.packageId(),
                key.logicalModule(),
                key.source(),
                sourceUri,
                characters,
                symbol.selectionRange()));
        for (ProtosDocumentSymbol child : symbol.children()) {
            flatten(key, sourceUri, characters, child, destination);
        }
    }

    private static StableSource readStableSource(Path source) throws IOException {
        BasicFileAttributes before = Files.readAttributes(
                source,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile()) {
            throw new IOException("workspace source is not a regular file");
        }
        byte[] bytes = Files.readAllBytes(source);
        BasicFileAttributes after = Files.readAttributes(
                source,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!after.isRegularFile()
                || before.size() != after.size()
                || after.size() != bytes.length
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())) {
            throw new IOException("workspace source changed while reading");
        }
        return new StableSource(strictUtf8(bytes), sha256(bytes));
    }

    private static String strictUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private record SourceKey(String packageId, String logicalModule, Path source) {
        SourceKey {
            packageId = requireText(packageId, "packageId");
            logicalModule = requireText(logicalModule, "logicalModule");
            source = requireAbsoluteNormalized(source, "source");
        }
    }

    private sealed interface SourceState permits DiskState, OverlayState {}

    private record DiskState(String digest) implements SourceState {
        DiskState {
            digest = requireText(digest, "digest");
        }
    }

    private record OverlayState(ProtosDocumentSnapshot snapshot) implements SourceState {
        OverlayState {
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    private record CachedSource(
            SourceState state,
            List<ProtosWorkspaceSymbolSearch.Candidate> candidates) {
        CachedSource {
            Objects.requireNonNull(state, "state");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }
    }

    private record StableSource(String characters, String digest) {
        StableSource {
            Objects.requireNonNull(characters, "characters");
            digest = requireText(digest, "digest");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }

    private static Path requireAbsoluteNormalized(Path value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.isAbsolute() || !value.equals(value.normalize())) {
            throw new IllegalArgumentException(name + " must be absolute and normalized");
        }
        return value;
    }
}

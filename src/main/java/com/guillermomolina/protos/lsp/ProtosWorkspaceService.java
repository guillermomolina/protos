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

import com.guillermomolina.protos.analysis.ProtosProjectBinding;
import com.guillermomolina.protos.analysis.ProtosProjectBindingProvider;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * D082/D106 workspace-symbol protocol edge over exact canonical ProjectBinding domains.
 *
 * <p>Editor workspace roots are only exact acquisition candidates. They never become
 * Protos project identity, trigger parent/child discovery or authorize loose files.</p>
 */
final class ProtosWorkspaceService implements WorkspaceService {
    private final ProtosTextDocumentService documents;
    private final ProtosProjectBindingProvider bindingProvider;
    private final ConcurrentMap<Path, ProtosWorkspaceSymbolIndex> indexes =
            new ConcurrentHashMap<>();
    private volatile List<Path> candidateRoots = List.of();

    ProtosWorkspaceService(
            ProtosTextDocumentService documents,
            ProtosProjectBindingProvider bindingProvider) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.bindingProvider = Objects.requireNonNull(bindingProvider, "bindingProvider");
    }

    void configure(InitializeParams params) {
        Objects.requireNonNull(params, "params");
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        List<WorkspaceFolder> folders = params.getWorkspaceFolders();
        if (folders != null) {
            for (WorkspaceFolder folder : folders) {
                candidatePath(folder.getUri()).ifPresent(candidates::add);
            }
        } else {
            candidatePath(params.getRootUri()).ifPresent(candidates::add);
        }
        candidateRoots = List.copyOf(candidates);
        indexes.clear();
    }

    @Override
    public CompletableFuture<
                    Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>>
            symbol(WorkspaceSymbolParams params) {
        Objects.requireNonNull(params, "params");
        String query = Objects.requireNonNull(params.getQuery(), "query");
        if (query.isEmpty()) {
            return CompletableFuture.completedFuture(Either.forLeft(List.of()));
        }

        List<ProtosWorkspaceSymbolSearch.Candidate> candidates = currentCandidates();
        List<SymbolInformation> result = ProtosWorkspaceSymbolSearch.search(query, candidates)
                .stream()
                .map(ProtosWorkspaceService::toSymbolInformation)
                .toList();
        List<? extends SymbolInformation> left = result;
        Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>> response =
                Either.forLeft(left);
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        Objects.requireNonNull(params, "params");
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        Objects.requireNonNull(params, "params");
        // G3 validates binding/source state on the next query. No editor watch registration
        // becomes source/project authority, and no background index work is introduced.
    }


    /**
     * Returns whether the supplied URI is exactly one current canonical
     * ProjectBinding source identity.
     *
     * <p>No URI normalization, parent/child discovery, workspace-symbol lookup,
     * or filesystem guessing is performed.</p>
     */
    boolean ownsCanonicalSourceUri(String sourceUri) {
        Objects.requireNonNull(sourceUri, "sourceUri");
        int matches = 0;
        for (ProtosProjectBinding binding : currentBindings()) {
            for (ProtosProjectBinding.Source source : binding.sources()) {
                if (source.source().toUri().toString().equals(sourceUri)) {
                    matches++;
                    if (matches > 1) {
                        return false;
                    }
                }
            }
        }
        return matches == 1;
    }

    private List<ProtosWorkspaceSymbolSearch.Candidate> currentCandidates() {
        List<ProtosProjectBinding> bindings = currentBindings();
        Set<Path> activeRoots = bindings.stream()
                .map(binding -> binding.projection().canonicalProjectRoot())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        indexes.keySet().removeIf(root -> !activeRoots.contains(root));

        ArrayList<ProtosWorkspaceSymbolSearch.Candidate> candidates = new ArrayList<>();
        for (ProtosProjectBinding binding : bindings) {
            Path root = binding.projection().canonicalProjectRoot();
            ProtosWorkspaceSymbolIndex index =
                    indexes.computeIfAbsent(root, ProtosWorkspaceSymbolIndex::new);
            try {
                candidates.addAll(index.snapshot(binding, documents::currentSnapshot));
            } catch (RuntimeException rejected) {
                indexes.remove(root, index);
            }
        }
        return List.copyOf(candidates);
    }

    private List<ProtosProjectBinding> currentBindings() {
        Map<Path, ProtosProjectBinding> byCanonicalRoot = new HashMap<>();
        Set<Path> conflictingRoots = new HashSet<>();
        for (Path candidateRoot : candidateRoots) {
            Optional<ProtosProjectBinding> acquired;
            try {
                acquired = bindingProvider.acquire(candidateRoot);
            } catch (RuntimeException rejected) {
                continue;
            }
            if (acquired.isEmpty()) {
                continue;
            }
            ProtosProjectBinding binding = acquired.get();
            Path canonicalRoot = binding.projection().canonicalProjectRoot();
            ProtosProjectBinding previous =
                    byCanonicalRoot.putIfAbsent(canonicalRoot, binding);
            if (previous != null && !previous.equals(binding)) {
                conflictingRoots.add(canonicalRoot);
            }
        }
        conflictingRoots.forEach(byCanonicalRoot::remove);
        return List.copyOf(byCanonicalRoot.values());
    }

    private static SymbolInformation toSymbolInformation(
            ProtosWorkspaceSymbolSearch.Candidate candidate) {
        Location location = new Location();
        location.setUri(candidate.sourceUri());
        location.setRange(ProtosLspSourcePositions.range(
                candidate.sourceCharacters(), candidate.selectionRange()));

        SymbolInformation result = new SymbolInformation();
        result.setName(candidate.name());
        result.setKind(SymbolKind.Property);
        result.setLocation(location);
        return result;
    }

    private static Optional<Path> candidatePath(String uriText) {
        if (uriText == null || uriText.isEmpty()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(uriText);
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                return Optional.empty();
            }
            return Optional.of(Path.of(uri).toAbsolutePath().normalize());
        } catch (IllegalArgumentException rejected) {
            return Optional.empty();
        }
    }
}

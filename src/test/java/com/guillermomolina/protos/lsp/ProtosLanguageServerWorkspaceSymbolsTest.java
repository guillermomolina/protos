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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.analysis.ProtosProjectBinding;
import com.guillermomolina.protos.analysis.ProtosProjectBindingProjection;
import com.guillermomolina.protos.analysis.ProtosProjectBindingProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtosLanguageServerWorkspaceSymbolsTest {

    @TempDir Path temporary;

    @Test
    void aggregatesExactIndependentProjectBindingsAndNestedD079Symbols() throws Exception {
        ProjectFixture left = project("left", "alphaCounter: 1\ncontainer: { nestedCounter: 2 }\n");
        ProjectFixture right = project("right", "bravoCounter: 1\n");
        ProtosLanguageServer server = initialized(List.of(left, right));

        assertEquals(
                List.of("alphaCounter", "bravoCounter", "nestedCounter"),
                names(symbols(server, "counter")));
    }

    @Test
    void exactOpenDocumentOverlayReplacesDiskAndLooseDocumentsRemainExcluded() throws Exception {
        ProjectFixture project = project("overlay", "diskName: 1\n");
        ProtosLanguageServer server = initialized(List.of(project));
        ProtosTextDocumentService documents = server.textDocuments();
        String uri = project.source().toUri().toString();

        documents.didOpen(open(uri, 1, "liveName: 1\n"));
        assertEquals(List.of("liveName"), names(symbols(server, "name")));

        Path loose = temporary.resolve("loose.protos");
        Files.writeString(loose, "looseName: 1\n");
        documents.didOpen(open(loose.toUri().toString(), 1, "looseName: 1\n"));
        assertTrue(symbols(server, "loose").isEmpty());

        documents.didClose(close(uri));
        assertEquals(List.of("diskName"), names(symbols(server, "name")));
    }

    @Test
    void explicitEmptyWorkspaceFolderListDoesNotFallBackToLegacyRootUri() throws Exception {
        ProjectFixture project = project("empty-folders", "rootName: 1\n");
        ProtosLanguageServer server = new ProtosLanguageServer(
                code -> {}, provider(List.of(project)));
        InitializeParams params = new InitializeParams();
        params.setCapabilities(new ClientCapabilities());
        params.setRootUri(project.root().toUri().toString());
        params.setWorkspaceFolders(List.of());
        server.initialize(params).join();

        assertTrue(symbols(server, "root").isEmpty());
    }

    @Test
    void diskSourceChangesRefreshOnlyTheCanonicalIndexedSource() throws Exception {
        ProjectFixture project = project("disk-refresh", "oldCounter: 1\n");
        ProtosLanguageServer server = initialized(List.of(project));

        assertEquals(List.of("oldCounter"), names(symbols(server, "counter")));
        Files.writeString(project.source(), "newCounter: 1\n");
        assertEquals(List.of("newCounter"), names(symbols(server, "counter")));
    }

    @Test
    void invalidOverlaySuppressesStaleDiskSymbolsAndCandidateRootsAreNotDiscovered() throws Exception {
        ProjectFixture project = project("exact", "diskName: 1\n");
        ProtosLanguageServer server = initialized(List.of(project));
        String uri = project.source().toUri().toString();
        server.textDocuments().didOpen(open(uri, 1, "broken:"));
        assertTrue(symbols(server, "disk").isEmpty());

        Path child = Files.createDirectory(project.root().resolve("child"));
        ProtosLanguageServer childCandidate = initializedCandidates(
                List.of(child), provider(List.of(project)));
        assertTrue(symbols(childCandidate, "disk").isEmpty());
    }

    private ProtosLanguageServer initialized(List<ProjectFixture> projects) {
        List<Path> roots = projects.stream().map(ProjectFixture::root).toList();
        return initializedCandidates(roots, provider(projects));
    }

    private static ProtosLanguageServer initializedCandidates(
            List<Path> roots,
            ProtosProjectBindingProvider provider) {
        ProtosLanguageServer server = new ProtosLanguageServer(code -> {}, provider);
        InitializeParams params = new InitializeParams();
        params.setCapabilities(new ClientCapabilities());
        ArrayList<WorkspaceFolder> folders = new ArrayList<>();
        for (Path root : roots) {
            WorkspaceFolder folder = new WorkspaceFolder();
            folder.setUri(root.toUri().toString());
            folder.setName(root.getFileName().toString());
            folders.add(folder);
        }
        params.setWorkspaceFolders(folders);
        server.initialize(params).join();
        return server;
    }

    private static ProtosProjectBindingProvider provider(List<ProjectFixture> projects) {
        Map<Path, ProtosProjectBinding> exact = new LinkedHashMap<>();
        for (ProjectFixture project : projects) {
            exact.put(project.root(), project.binding());
        }
        return candidate -> Optional.ofNullable(exact.get(candidate.toAbsolutePath().normalize()));
    }

    private ProjectFixture project(String directoryName, String sourceText) throws Exception {
        Path root = Files.createDirectory(temporary.resolve(directoryName)).toRealPath();
        Path source = root.resolve("Main.protos");
        Files.writeString(source, sourceText);
        source = source.toRealPath();

        ProtosProjectBindingProjection projection = new ProtosProjectBindingProjection(
                ProtosProjectBindingProjection.CURRENT_GENERATION,
                root,
                directoryName + "-package",
                List.of(new ProtosProjectBindingProjection.PackageRef(
                        directoryName + "-package", "")),
                "test-freshness");
        ProtosProjectBinding binding = new ProtosProjectBinding(
                projection,
                List.of(new ProtosProjectBinding.PackageRoot(
                        directoryName + "-package", "", root)),
                List.of(new ProtosProjectBinding.Source(
                        directoryName + "-package", "Main", source)));
        return new ProjectFixture(root, source, binding);
    }

    private static List<SymbolInformation> symbols(ProtosLanguageServer server, String query) {
        WorkspaceSymbolParams params = new WorkspaceSymbolParams();
        params.setQuery(query);
        Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>> response =
                server.getWorkspaceService().symbol(params).join();
        assertTrue(response.isLeft());
        return new ArrayList<>(response.getLeft());
    }

    private static List<String> names(List<SymbolInformation> symbols) {
        return symbols.stream().map(SymbolInformation::getName).toList();
    }

    private static DidOpenTextDocumentParams open(String uri, int version, String text) {
        TextDocumentItem item = new TextDocumentItem();
        item.setUri(uri);
        item.setLanguageId("protos");
        item.setVersion(version);
        item.setText(text);
        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
        params.setTextDocument(item);
        return params;
    }

    private static DidCloseTextDocumentParams close(String uri) {
        TextDocumentIdentifier identifier = new TextDocumentIdentifier();
        identifier.setUri(uri);
        DidCloseTextDocumentParams params = new DidCloseTextDocumentParams();
        params.setTextDocument(identifier);
        return params;
    }

    private record ProjectFixture(Path root, Path source, ProtosProjectBinding binding) {}
}

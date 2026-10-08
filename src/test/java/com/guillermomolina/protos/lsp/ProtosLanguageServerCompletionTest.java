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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.analysis.ProtosProjectBinding;
import com.guillermomolina.protos.analysis.ProtosProjectBindingProjection;
import com.guillermomolina.protos.analysis.ProtosProjectBindingProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtosLanguageServerCompletionTest {

    private static final List<String> RESERVED =
            List.of("this", "context", "true", "false", "null");

    @TempDir Path temporary;

    @Test
    void advertisesCompletionAndPublishesProvenAndSyntaxItems() throws Exception {
        ProjectFixture project = project("completion");
        AtomicReference<Optional<ProtosProjectBinding>> current =
                new AtomicReference<>(Optional.of(project.binding()));
        ProtosLanguageServer server = initialized(project, candidate ->
                candidate.equals(project.root()) ? current.get() : Optional.empty());

        CompletionOptions options = server.initialize(initializeParams(project.root()))
                .join()
                .getCapabilities()
                .getCompletionProvider();
        assertNotNull(options);
        assertEquals(Boolean.FALSE, options.getResolveProvider());
        assertEquals(List.of(), options.getTriggerCharacters());

        String uri = project.source().toUri().toString();
        server.textDocuments().didOpen(open(uri, 1, "f: (first, second) => fir"));

        CompletionList list = completion(server, uri, 0, 25);
        assertFalse(list.isIncomplete());
        assertEquals(
                concat(List.of("first", "second"), RESERVED),
                list.getItems().stream().map(CompletionItem::getLabel).toList());

        CompletionItem first = list.getItems().get(0);
        assertEquals(CompletionItemKind.Variable, first.getKind());
        assertEquals("Proven binding: Closure parameter", first.getDetail());
        assertEquals("first", first.getFilterText());
        assertEquals(new TextEdit(range(0, 22, 0, 25), "first"), first.getTextEdit().getLeft());

        CompletionItem keyword = list.getItems().get(2);
        assertEquals("this", keyword.getLabel());
        assertEquals(CompletionItemKind.Keyword, keyword.getKind());
        assertEquals("Syntax: reserved word", keyword.getDetail());
        assertEquals(new TextEdit(range(0, 22, 0, 25), "this"), keyword.getTextEdit().getLeft());

        current.set(Optional.empty());
        assertEmptyComplete(completion(server, uri, 0, 25));
    }

    @Test
    void emptyPositionAtDocumentEndInsertsWithoutConsumingText() throws Exception {
        ProjectFixture project = project("end");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String uri = project.source().toUri().toString();

        server.textDocuments().didOpen(open(uri, 1, "f: (first) => "));
        CompletionList atEnd = completion(server, uri, 0, 14);
        assertEquals(
                new TextEdit(range(0, 14, 0, 14), "first"),
                atEnd.getItems().get(0).getTextEdit().getLeft());

        server.textDocuments().didChange(change(uri, 2, "f: (first) => g(fi, 2)"));
        CompletionList beforeSuffix = completion(server, uri, 0, 18);
        assertEquals(
                new TextEdit(range(0, 16, 0, 18), "first"),
                beforeSuffix.getItems().get(0).getTextEdit().getLeft());
    }

    @Test
    void looseOrNonCanonicalOpenDocumentNeverGainsCompletionAuthority() throws Exception {
        ProjectFixture project = project("authority");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String source = "f: (first) => fi";

        Path loose = temporary.resolve("Loose.protos");
        Files.writeString(loose, source);
        String looseUri = loose.toUri().toString();
        server.textDocuments().didOpen(open(looseUri, 1, source));
        assertEmptyComplete(completion(server, looseUri, 0, 16));

        String nonCanonicalUri =
                project.source().toUri().toString().replace("/Main.protos", "/./Main.protos");
        server.textDocuments().didOpen(open(nonCanonicalUri, 1, source));
        assertEmptyComplete(completion(server, nonCanonicalUri, 0, 16));

        // Canonical but not open: disk content is never consulted.
        assertEmptyComplete(completion(server, project.source().toUri().toString(), 0, 0));
    }

    @Test
    void invalidPositionsAndUnclassifiableSitesAreCompleteEmptyLists() throws Exception {
        ProjectFixture project = project("no-result");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String uri = project.source().toUri().toString();

        server.textDocuments().didOpen(open(uri, 1, "f: (first) => {\n  first.se\n  'fi'\n}\n"));
        assertEmptyComplete(completion(server, uri, 99, 0));
        assertEmptyComplete(completion(server, uri, 0, 99));
        assertEmptyComplete(completion(server, uri, 1, 10));
        assertEmptyComplete(completion(server, uri, 2, 5));
        assertEmptyComplete(completion(server, uri, 1, 4));

        server.textDocuments().didChange(change(uri, 2, "g(fir"));
        assertEmptyComplete(completion(server, uri, 0, 5));
    }

    @Test
    void unsavedChangesReplaceSnapshotAndCloseRemovesCompletion() throws Exception {
        ProjectFixture project = project("snapshot");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String uri = project.source().toUri().toString();

        server.textDocuments().didOpen(open(uri, 1, "f: (first) => fi"));
        assertEquals("first", completion(server, uri, 0, 16).getItems().get(0).getLabel());

        server.textDocuments().didChange(change(uri, 2, "f: (other) => fi"));
        assertEquals("other", completion(server, uri, 0, 16).getItems().get(0).getLabel());

        DidCloseTextDocumentParams close = new DidCloseTextDocumentParams();
        close.setTextDocument(new TextDocumentIdentifier(uri));
        server.textDocuments().didClose(close);
        assertEmptyComplete(completion(server, uri, 0, 16));
    }

    @Test
    void mapsUtf16NonBmpAndCrlfPositions() throws Exception {
        ProjectFixture project = project("utf16");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String uri = project.source().toUri().toString();

        // U+1F600 occupies two UTF-16 code units.
        String source = "f: (first) => {\r\n  \"😀\" + fi\r\n}\r\n";
        server.textDocuments().didOpen(open(uri, 1, source));

        CompletionList list = completion(server, uri, 1, 11);
        assertEquals("first", list.getItems().get(0).getLabel());
        assertEquals(
                new TextEdit(range(1, 9, 1, 11), "first"),
                list.getItems().get(0).getTextEdit().getLeft());

        // Between the two UTF-16 code units of the emoji.
        assertEmptyComplete(completion(server, uri, 1, 4));
    }

    private static void assertEmptyComplete(CompletionList list) {
        assertFalse(list.isIncomplete());
        assertTrue(list.getItems().isEmpty());
    }

    private static List<String> concat(List<String> left, List<String> right) {
        return java.util.stream.Stream.concat(left.stream(), right.stream()).toList();
    }

    private ProjectFixture project(String name) throws Exception {
        Path projectRoot = Files.createDirectory(temporary.resolve(name)).toRealPath();
        Path source = projectRoot.resolve("Main.protos");
        Files.writeString(source, "diskOnly: 1\n");
        source = source.toRealPath();

        String packageId = name + "-package";
        ProtosProjectBindingProjection projection =
                new ProtosProjectBindingProjection(
                        ProtosProjectBindingProjection.CURRENT_GENERATION,
                        projectRoot,
                        packageId,
                        List.of(new ProtosProjectBindingProjection.PackageRef(packageId, "")),
                        "completion-test");
        ProtosProjectBinding binding =
                new ProtosProjectBinding(
                        projection,
                        List.of(new ProtosProjectBinding.PackageRoot(packageId, "", projectRoot)),
                        List.of(new ProtosProjectBinding.Source(packageId, "Main", source)));
        return new ProjectFixture(projectRoot, source, binding);
    }

    private static ProtosProjectBindingProvider canonical(ProjectFixture project) {
        return candidate -> candidate.equals(project.root())
                ? Optional.of(project.binding())
                : Optional.empty();
    }

    private static ProtosLanguageServer initialized(
            ProjectFixture project,
            ProtosProjectBindingProvider provider) {
        ProtosLanguageServer server = new ProtosLanguageServer(code -> {}, provider);
        server.initialize(initializeParams(project.root())).join();
        return server;
    }

    private static InitializeParams initializeParams(Path root) {
        InitializeParams params = new InitializeParams();
        params.setCapabilities(new ClientCapabilities());
        WorkspaceFolder folder = new WorkspaceFolder();
        folder.setUri(root.toUri().toString());
        folder.setName(root.getFileName().toString());
        params.setWorkspaceFolders(List.of(folder));
        return params;
    }

    private static CompletionList completion(
            ProtosLanguageServer server,
            String uri,
            int line,
            int character) {
        CompletionParams params = new CompletionParams(
                new TextDocumentIdentifier(uri),
                new Position(line, character));
        return server.getTextDocumentService().completion(params).join().getRight();
    }

    private static Range range(int startLine, int startCharacter, int endLine, int endCharacter) {
        return new Range(new Position(startLine, startCharacter), new Position(endLine, endCharacter));
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

    private static DidChangeTextDocumentParams change(String uri, int version, String text) {
        VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier(uri, version);
        TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent();
        event.setText(text);
        DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();
        params.setTextDocument(identifier);
        params.setContentChanges(List.of(event));
        return params;
    }

    private record ProjectFixture(Path root, Path source, ProtosProjectBinding binding) {
    }
}

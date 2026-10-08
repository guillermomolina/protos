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
import static org.junit.jupiter.api.Assertions.assertNull;

import com.guillermomolina.protos.analysis.ProtosProjectBinding;
import com.guillermomolina.protos.analysis.ProtosProjectBindingProjection;
import com.guillermomolina.protos.analysis.ProtosProjectBindingProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtosLanguageServerHoverTest {

    @TempDir Path temporary;

    @Test
    void advertisesHoverAndPublishesLabeledProvenAndSyntaxFacts() throws Exception {
        ProjectFixture project = project("hover");
        AtomicReference<Optional<ProtosProjectBinding>> current =
                new AtomicReference<>(Optional.of(project.binding()));
        ProtosLanguageServer server = initialized(project, candidate ->
                candidate.equals(project.root()) ? current.get() : Optional.empty());

        assertEquals(
                Boolean.TRUE,
                server.initialize(initializeParams(project.root()))
                        .join()
                        .getCapabilities()
                        .getHoverProvider()
                        .getLeft());

        String source = "f: (value, limit = 3) => {\n  value\n}\n";
        String uri = project.source().toUri().toString();
        server.textDocuments().didOpen(open(uri, 1, source));

        Hover proven = hover(server, uri, 1, 3);
        assertEquals(MarkupKind.PLAINTEXT, proven.getContents().getRight().getKind());
        assertEquals(
                "Proven binding\nClosure parameter: value",
                proven.getContents().getRight().getValue());
        assertEquals(range(1, 2, 1, 7), proven.getRange());

        Hover declaration = hover(server, uri, 0, 11);
        assertEquals(
                "Syntax\nClosure parameter declaration: limit\nDefault value present\n"
                        + "Closure parameters: (value, limit = <default>)",
                declaration.getContents().getRight().getValue());
        assertEquals(range(0, 11, 0, 16), declaration.getRange());

        Hover literal = hover(server, uri, 0, 19);
        assertEquals("Syntax\nNumber literal", literal.getContents().getRight().getValue());

        current.set(Optional.empty());
        assertNull(hover(server, uri, 1, 3));
    }

    @Test
    void looseOrNonCanonicalOpenDocumentNeverGainsHoverAuthority() throws Exception {
        ProjectFixture project = project("authority");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String source = "f: (value) => value\n";

        Path loose = temporary.resolve("Loose.protos");
        Files.writeString(loose, source);
        String looseUri = loose.toUri().toString();
        server.textDocuments().didOpen(open(looseUri, 1, source));
        assertNull(hover(server, looseUri, 0, 14));
        assertNull(hover(server, looseUri, 0, 3));

        String nonCanonicalUri =
                project.source().toUri().toString().replace("/Main.protos", "/./Main.protos");
        server.textDocuments().didOpen(open(nonCanonicalUri, 1, source));
        assertNull(hover(server, nonCanonicalUri, 0, 14));

        // Canonical but not open: disk content is never consulted.
        assertNull(hover(server, project.source().toUri().toString(), 0, 10));
    }

    @Test
    void invalidDocumentPositionsAndUnprovenNamesReturnNull() throws Exception {
        ProjectFixture project = project("no-result");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String uri = project.source().toUri().toString();

        server.textDocuments().didOpen(open(uri, 1, "f: (value) => {\n  sink(value)\n  value.name\n}\n"));
        assertNull(hover(server, uri, 99, 0));
        assertNull(hover(server, uri, 0, 99));
        assertNull(hover(server, uri, 2, 3));
        assertNull(hover(server, uri, 2, 9));
        assertNull(hover(server, uri, 1, 3));

        server.textDocuments().didChange(change(uri, 2, "f: (value) => {\n  1\n"));
        assertNull(hover(server, uri, 1, 2));
    }

    @Test
    void unsavedChangesReplaceSnapshotAndCloseRemovesHover() throws Exception {
        ProjectFixture project = project("snapshot");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String uri = project.source().toUri().toString();

        server.textDocuments().didOpen(open(uri, 1, "x: 1\n"));
        assertEquals("Syntax\nNumber literal", hover(server, uri, 0, 3).getContents().getRight().getValue());

        server.textDocuments().didChange(change(uri, 2, "x: 'one'\n"));
        Hover changed = hover(server, uri, 0, 3);
        assertEquals("Syntax\nString literal", changed.getContents().getRight().getValue());
        assertEquals(range(0, 3, 0, 8), changed.getRange());

        DidCloseTextDocumentParams close = new DidCloseTextDocumentParams();
        close.setTextDocument(new TextDocumentIdentifier(uri));
        server.textDocuments().didClose(close);
        assertNull(hover(server, uri, 0, 3));
    }

    @Test
    void mapsUtf16NonBmpAndCrlfPositions() throws Exception {
        ProjectFixture project = project("utf16");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String uri = project.source().toUri().toString();

        // U+1F600 occupies two UTF-16 code units.
        // Required parameters precede defaulted ones, so the declaration after
        // the non-BMP default is itself defaulted.
        String source = "f: (value, a = \"😀\", b = 0) => {\r\n  \"😀\"\r\n  value\r\n}\r\n";
        server.textDocuments().didOpen(open(uri, 1, source));

        Hover declaration = hover(server, uri, 0, 21);
        assertEquals(range(0, 21, 0, 22), declaration.getRange());

        Hover string = hover(server, uri, 1, 4);
        assertEquals("Syntax\nString literal", string.getContents().getRight().getValue());
        assertEquals(range(1, 2, 1, 6), string.getRange());

        Hover proven = hover(server, uri, 2, 6);
        assertEquals(
                "Proven binding\nClosure parameter: value",
                proven.getContents().getRight().getValue());
        assertEquals(range(2, 2, 2, 7), proven.getRange());
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
                        "hover-test");
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

    private static Hover hover(ProtosLanguageServer server, String uri, int line, int character) {
        HoverParams params = new HoverParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        params.setPosition(new Position(line, character));
        return server.getTextDocumentService().hover(params).join();
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

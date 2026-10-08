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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtosLanguageServerSignatureHelpTest {

    @TempDir Path temporary;

    @Test
    void advertisesTriggersAndPublishesOneLiteralClosureSignature() throws Exception {
        ProjectFixture project = project("signature");
        AtomicReference<Optional<ProtosProjectBinding>> current =
                new AtomicReference<>(Optional.of(project.binding()));
        ProtosLanguageServer server = initialized(project, candidate ->
                candidate.equals(project.root()) ? current.get() : Optional.empty());

        SignatureHelpOptions options = server.initialize(initializeParams(project.root()))
                .join()
                .getCapabilities()
                .getSignatureHelpProvider();
        assertNotNull(options);
        assertEquals(List.of("(", ","), options.getTriggerCharacters());

        String uri = project.source().toUri().toString();
        String source = "((first, second = 2, ...rest) => first)(1, ";
        server.textDocuments().didOpen(open(uri, 1, source));

        SignatureHelp help = signatureHelp(server, uri, 0, source.length());
        assertNotNull(help);
        assertEquals(1, help.getSignatures().size());
        assertEquals(0, help.getActiveSignature());
        assertEquals(1, help.getActiveParameter());
        SignatureInformation signature = help.getSignatures().get(0);
        assertEquals("(first, second = 2, ...rest)", signature.getLabel());
        assertNull(signature.getDocumentation());
        assertEquals(
                List.of("first", "second = 2", "...rest"),
                signature.getParameters().stream()
                        .map(ParameterInformation::getLabel)
                        .map(label -> label.getLeft())
                        .toList());

        assertNull(signatureHelp(server, uri, 0, 0));

        current.set(Optional.empty());
        assertNull(signatureHelp(server, uri, 0, source.length()));
    }

    @Test
    void parameterlessClosureShowsAnEmptySignatureWithoutHighlight() throws Exception {
        ProjectFixture project = project("empty");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String uri = project.source().toUri().toString();

        server.textDocuments().didOpen(open(uri, 1, "(() => 1)()"));
        SignatureHelp help = signatureHelp(server, uri, 0, 10);
        assertNotNull(help);
        assertEquals("()", help.getSignatures().get(0).getLabel());
        assertTrue(help.getSignatures().get(0).getParameters().isEmpty());
        assertNull(help.getActiveParameter());
    }

    @Test
    void unprovableHighlightAndDynamicCallsPublishNothing() throws Exception {
        ProjectFixture project = project("no-result");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String uri = project.source().toUri().toString();

        String spread = "((a, b) => a)(...values, ";
        server.textDocuments().didOpen(open(uri, 1, spread));
        assertNull(signatureHelp(server, uri, 0, spread.length()));

        String excess = "((a) => a)(1, ";
        server.textDocuments().didChange(change(uri, 2, excess));
        assertNull(signatureHelp(server, uri, 0, excess.length()));

        // "b" first occurs inside "ab": a String label would highlight the wrong text.
        String ambiguous = "((ab, b) => ab)(1, ";
        server.textDocuments().didChange(change(uri, 3, ambiguous));
        assertNull(signatureHelp(server, uri, 0, ambiguous.length()));

        server.textDocuments().didChange(change(uri, 4, "f: (a, b) => a\nf(1, 2)"));
        assertNull(signatureHelp(server, uri, 1, 5));

        server.textDocuments().didChange(change(uri, 5, "((a) => a)(1)"));
        assertNull(signatureHelp(server, uri, 99, 0));
        assertNull(signatureHelp(server, uri, 0, 99));
        assertNull(signatureHelp(server, uri, 0, 13));

        server.textDocuments().didChange(change(uri, 6, "((a) => a)(\"open"));
        assertNull(signatureHelp(server, uri, 0, 16));
    }

    @Test
    void looseOrNonCanonicalOpenDocumentNeverGainsSignatureAuthority() throws Exception {
        ProjectFixture project = project("authority");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String source = "((a) => a)(1)";

        Path loose = temporary.resolve("Loose.protos");
        Files.writeString(loose, source);
        String looseUri = loose.toUri().toString();
        server.textDocuments().didOpen(open(looseUri, 1, source));
        assertNull(signatureHelp(server, looseUri, 0, 12));

        String nonCanonicalUri =
                project.source().toUri().toString().replace("/Main.protos", "/./Main.protos");
        server.textDocuments().didOpen(open(nonCanonicalUri, 1, source));
        assertNull(signatureHelp(server, nonCanonicalUri, 0, 12));

        // Canonical but not open: disk content is never consulted.
        Files.writeString(project.source(), source);
        assertNull(signatureHelp(server, project.source().toUri().toString(), 0, 12));
    }

    @Test
    void unsavedChangesReplaceSnapshotAndCloseRemovesSignatureHelp() throws Exception {
        ProjectFixture project = project("snapshot");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String uri = project.source().toUri().toString();

        server.textDocuments().didOpen(open(uri, 1, "((a) => a)("));
        assertEquals("(a)", signatureHelp(server, uri, 0, 11).getSignatures().get(0).getLabel());

        server.textDocuments().didChange(change(uri, 2, "((other) => other)("));
        assertEquals(
                "(other)",
                signatureHelp(server, uri, 0, 19).getSignatures().get(0).getLabel());

        DidCloseTextDocumentParams close = new DidCloseTextDocumentParams();
        close.setTextDocument(new TextDocumentIdentifier(uri));
        server.textDocuments().didClose(close);
        assertNull(signatureHelp(server, uri, 0, 19));
    }

    @Test
    void mapsUtf16NonBmpAndCrlfPositions() throws Exception {
        ProjectFixture project = project("utf16");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String uri = project.source().toUri().toString();

        // U+1F600 occupies two UTF-16 code units at characters 15 and 16.
        String source = "((a, b) => a)(\"😀\",\r\n  ";
        server.textDocuments().didOpen(open(uri, 1, source));

        assertEquals(1, signatureHelp(server, uri, 1, 2).getActiveParameter());
        assertNull(signatureHelp(server, uri, 0, 16));
    }

    @Test
    void hoverAndCompletionAreUnchangedBySignatureHelp() throws Exception {
        ProjectFixture project = project("regression");
        ProtosLanguageServer server = initialized(project, canonical(project));
        String uri = project.source().toUri().toString();

        server.textDocuments().didOpen(open(uri, 1, "f: (first) => first\n((a, b) => a)(1, 2)"));

        assertEquals(1, signatureHelp(server, uri, 1, 17).getActiveParameter());
        assertNotNull(server.getTextDocumentService()
                .hover(new HoverParams(new TextDocumentIdentifier(uri), new Position(0, 16)))
                .join());
        List<CompletionItem> items = server.getTextDocumentService()
                .completion(new CompletionParams(
                        new TextDocumentIdentifier(uri),
                        new Position(0, 19)))
                .join()
                .getRight()
                .getItems();
        assertEquals("first", items.get(0).getLabel());
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
                        "signature-help-test");
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

    private static SignatureHelp signatureHelp(
            ProtosLanguageServer server,
            String uri,
            int line,
            int character) {
        SignatureHelpParams params = new SignatureHelpParams(
                new TextDocumentIdentifier(uri),
                new Position(line, character));
        return server.getTextDocumentService().signatureHelp(params).join();
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

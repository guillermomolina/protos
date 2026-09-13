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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtosLanguageServerReferencesTest {

    @TempDir Path temporary;

    @Test
    void advertisesReferencesAndHonorsIncludeDeclarationOnCanonicalOverlay()
            throws Exception {
        ProjectFixture project = project("references");
        AtomicReference<Optional<ProtosProjectBinding>> current =
                new AtomicReference<>(Optional.of(project.binding()));
        ProtosLanguageServer server = initialized(project, candidate -> {
            if (!candidate.equals(project.root())) {
                return Optional.empty();
            }
            return current.get();
        });

        assertEquals(
                Boolean.TRUE,
                server.initialize(initializeParams(project.root()))
                        .join()
                        .getCapabilities()
                        .getReferencesProvider()
                        .getLeft());

        String source =
                "f: (value) => {\n"
                        + "  value\n"
                        + "  value\n"
                        + "}\n";
        String uri = project.source().toUri().toString();
        server.textDocuments().didOpen(open(uri, 7, source));

        List<Location> withoutDeclaration =
                references(server, uri, 1, 2, false);
        assertEquals(2, withoutDeclaration.size());
        assertEquals(new Position(1, 2), withoutDeclaration.get(0).getRange().getStart());
        assertEquals(new Position(2, 2), withoutDeclaration.get(1).getRange().getStart());

        List<Location> withDeclaration =
                references(server, uri, 1, 2, true);
        assertEquals(3, withDeclaration.size());
        assertEquals(new Position(0, 4), withDeclaration.get(0).getRange().getStart());
        assertEquals(new Position(1, 2), withDeclaration.get(1).getRange().getStart());
        assertEquals(new Position(2, 2), withDeclaration.get(2).getRange().getStart());

        List<Location> fromDeclaration =
                references(server, uri, 0, 5, false);
        assertEquals(withoutDeclaration, fromDeclaration);

        current.set(Optional.empty());
        assertTrue(references(server, uri, 1, 2, true).isEmpty());
    }

    @Test
    void looseOrNonCanonicalOpenDocumentNeverGainsReferenceAuthority()
            throws Exception {
        ProjectFixture project = project("authority");
        ProtosLanguageServer server = initialized(
                project,
                candidate -> candidate.equals(project.root())
                        ? Optional.of(project.binding())
                        : Optional.empty());

        String source = "f: (value) => value\n";

        Path loose = temporary.resolve("Loose.protos");
        Files.writeString(loose, source);
        String looseUri = loose.toUri().toString();
        server.textDocuments().didOpen(open(looseUri, 1, source));
        assertTrue(references(server, looseUri, 0, 14, true).isEmpty());

        String canonicalUri = project.source().toUri().toString();
        String nonCanonicalUri = canonicalUri.replace(
                "/Main.protos",
                "/./Main.protos");
        server.textDocuments().didOpen(open(nonCanonicalUri, 1, source));
        assertTrue(references(server, nonCanonicalUri, 0, 14, true).isEmpty());
    }

    @Test
    void invalidOrUnprovenPositionReturnsOrdinaryEmptyReferences()
            throws Exception {
        ProjectFixture project = project("no-result");
        ProtosLanguageServer server = initialized(
                project,
                candidate -> candidate.equals(project.root())
                        ? Optional.of(project.binding())
                        : Optional.empty());

        String source =
                "f: (value) => {\n"
                        + "  sink(value)\n"
                        + "  value\n"
                        + "}\n";
        String uri = project.source().toUri().toString();
        server.textDocuments().didOpen(open(uri, 1, source));

        assertTrue(references(server, uri, 99, 0, false).isEmpty());
        assertTrue(references(server, uri, 2, 2, false).isEmpty());
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
                        List.of(new ProtosProjectBindingProjection.PackageRef(
                                packageId,
                                "")),
                        "references-test");
        ProtosProjectBinding binding =
                new ProtosProjectBinding(
                        projection,
                        List.of(new ProtosProjectBinding.PackageRoot(
                                packageId,
                                "",
                                projectRoot)),
                        List.of(new ProtosProjectBinding.Source(
                                packageId,
                                "Main",
                                source)));
        return new ProjectFixture(projectRoot, source, binding);
    }

    private static ProtosLanguageServer initialized(
            ProjectFixture project,
            ProtosProjectBindingProvider provider) {
        ProtosLanguageServer server =
                new ProtosLanguageServer(code -> {}, provider);
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

    private static List<Location> references(
            ProtosLanguageServer server,
            String uri,
            int line,
            int character,
            boolean includeDeclaration) {
        ReferenceContext context = new ReferenceContext();
        context.setIncludeDeclaration(includeDeclaration);

        ReferenceParams params = new ReferenceParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        params.setPosition(new Position(line, character));
        params.setContext(context);

        return List.copyOf(
                server.getTextDocumentService().references(params).join());
    }

    private static DidOpenTextDocumentParams open(
            String uri,
            int version,
            String text) {
        TextDocumentItem item = new TextDocumentItem();
        item.setUri(uri);
        item.setLanguageId("protos");
        item.setVersion(version);
        item.setText(text);
        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
        params.setTextDocument(item);
        return params;
    }

    private record ProjectFixture(
            Path root,
            Path source,
            ProtosProjectBinding binding) {
    }
}

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
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtosLanguageServerDefinitionTest {

    @TempDir Path temporary;

    @Test
    void canonicalOverlayLocationSurvivesLossOfProjectBinding() throws Exception {
        ProjectFixture project = project("definition");
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
                        .getDefinitionProvider()
                        .getLeft());

        String source = "f: (value) => {\n  value\n}\n";
        String uri = project.source().toUri().toString();
        server.textDocuments().didOpen(open(uri, 7, source));

        List<Location> locations = definitions(server, uri, 1, 2);
        assertEquals(1, locations.size());
        assertEquals(uri, locations.get(0).getUri());
        assertEquals(new Position(0, 4), locations.get(0).getRange().getStart());
        assertEquals(new Position(0, 9), locations.get(0).getRange().getEnd());

        // The proof belongs entirely to the open snapshot, so losing canonical
        // project authority does not erase it.
        current.set(Optional.empty());
        assertParameterLocation(definitions(server, uri, 1, 2), uri);
    }

    @Test
    void looseOrNonCanonicalOpenDocumentAnswersSameDocumentDefinition() throws Exception {
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
        assertParameterLocation(definitions(server, looseUri, 0, 14), looseUri);

        String canonicalUri = project.source().toUri().toString();
        String nonCanonicalUri = canonicalUri.replace(
                "/Main.protos",
                "/./Main.protos");
        server.textDocuments().didOpen(open(nonCanonicalUri, 1, source));
        // The textual alias is kept verbatim; it is never rewritten to the
        // canonical project URI.
        assertParameterLocation(
                definitions(server, nonCanonicalUri, 0, 14),
                nonCanonicalUri);
    }

    @Test
    void workspaceWithoutProjectMetadataAnswersSameDocumentDefinition() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("no-metadata")).toRealPath();
        Path source = root.resolve("Main.protos");
        Files.writeString(source, "diskOnly: 1\n");
        assertTrue(Files.notExists(root.resolve("package.toml")));
        assertTrue(Files.notExists(root.resolve("protos.lock")));
        assertTrue(Files.notExists(root.resolve("protos.project")));

        ProtosLanguageServer server =
                new ProtosLanguageServer(code -> {}, candidate -> Optional.empty());
        server.initialize(initializeParams(root)).join();

        String uri = source.toUri().toString();
        server.textDocuments().didOpen(open(uri, 1, "f: (value) => {\n  value\n}\n"));

        assertParameterLocation(definitions(server, uri, 1, 2), uri);
        assertTrue(definitions(server, uri, 99, 0).isEmpty());
        assertTrue(definitions(server, uri, 1, 99).isEmpty());
    }

    @Test
    void unprovenSitesWithoutProjectBindingReturnOrdinaryNoResult() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("unproven")).toRealPath();
        ProtosLanguageServer server =
                new ProtosLanguageServer(code -> {}, candidate -> Optional.empty());
        server.initialize(initializeParams(root)).join();

        // The parameter declaration itself is not a read.
        String declaration = root.resolve("Declaration.protos").toUri().toString();
        server.textDocuments().didOpen(open(declaration, 1, "f: (value) => value\n"));
        assertTrue(definitions(server, declaration, 0, 5).isEmpty());
        assertParameterLocation(definitions(server, declaration, 0, 14), declaration);

        // A member name never resolves by textual coincidence with a binding;
        // only the receiver read is proven.
        String member = root.resolve("Member.protos").toUri().toString();
        server.textDocuments().didOpen(open(member, 1, "f: (value) => value.value\n"));
        assertTrue(definitions(server, member, 0, 21).isEmpty());
        assertParameterLocation(definitions(server, member, 0, 15), member);

        // A completed slot creation is the proven origin of a later read; the
        // creation target itself is not a read.
        String slot = root.resolve("Slot.protos").toUri().toString();
        server.textDocuments().didOpen(open(slot, 1, "count: 1\ncount\n"));
        assertLocation(definitions(server, slot, 1, 1), slot, 0, 0, 0, 5);
        assertTrue(definitions(server, slot, 0, 1).isEmpty());

        // A read after an opaque invocation stays unresolved.
        String opaque = root.resolve("Opaque.protos").toUri().toString();
        server.textDocuments().didOpen(
                open(opaque, 1, "f: (value) => {\n  sink(value)\n  value\n}\n"));
        assertTrue(definitions(server, opaque, 2, 2).isEmpty());

        // A named-argument label is not a read.
        String label = root.resolve("Label.protos").toUri().toString();
        server.textDocuments().didOpen(open(
                label,
                1,
                "f: (value) => {\n  ((other) => other)(value: value)\n}\n"));
        assertTrue(definitions(server, label, 1, 23).isEmpty());

        // A source that does not parse yields no locations.
        String broken = root.resolve("Broken.protos").toUri().toString();
        server.textDocuments().didOpen(open(broken, 1, "f: (value) => {\n  value\n"));
        assertTrue(definitions(server, broken, 1, 2).isEmpty());

        // Invalid positions and unopened documents yield the ordinary empty result.
        assertTrue(definitions(server, declaration, 5, 0).isEmpty());
        assertTrue(definitions(server, declaration, 0, 200).isEmpty());
        assertTrue(definitions(
                server,
                root.resolve("Unopened.protos").toUri().toString(),
                0,
                0).isEmpty());
    }

    @Test
    void slotAndObjectMemberOriginsWithoutProjectBindingStayInTheDocument() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("slots")).toRealPath();
        assertTrue(Files.notExists(root.resolve("package.toml")));
        assertTrue(Files.notExists(root.resolve("protos.lock")));
        assertTrue(Files.notExists(root.resolve("protos.project")));
        ProtosLanguageServer server =
                new ProtosLanguageServer(code -> {}, candidate -> Optional.empty());
        server.initialize(initializeParams(root)).join();

        String closure = root.resolve("Closure.protos").toUri().toString();
        server.textDocuments().didOpen(open(
                closure,
                1,
                "saludar: (nombre) => {\n  print(nombre)\n}\nsaludar(\"Hola\")\n"));
        assertLocation(definitions(server, closure, 3, 2), closure, 0, 0, 0, 7);
        assertLocation(definitions(server, closure, 1, 9), closure, 0, 10, 0, 16);

        String object = root.resolve("Object.protos").toUri().toString();
        server.textDocuments().didOpen(open(
                object,
                1,
                "obj: {\n  dato: 1\n  saludar: () => { 42 }\n}\nobj.dato\nobj.saludar()\n"));
        // The selector range is exactly the member token.
        assertTrue(definitions(server, object, 4, 3).isEmpty());
        assertLocation(definitions(server, object, 4, 4), object, 1, 2, 1, 6);
        assertLocation(definitions(server, object, 4, 7), object, 1, 2, 1, 6);
        assertTrue(definitions(server, object, 4, 8).isEmpty());
        assertLocation(definitions(server, object, 4, 1), object, 0, 0, 0, 3);
        assertLocation(definitions(server, object, 5, 6), object, 2, 2, 2, 9);

        // A textual URI alias is answered under exactly the requested URI.
        String alias = root.toUri().toString() + "./Alias.protos";
        server.textDocuments().didOpen(open(alias, 1, "mensaje: \"Hola\"\nmensaje\n"));
        assertLocation(definitions(server, alias, 1, 3), alias, 0, 0, 0, 7);

        // Opaque effects remove slot and member proofs.
        String opaque = root.resolve("Opaque.protos").toUri().toString();
        server.textDocuments().didOpen(open(
                opaque, 1, "v: 1\nobj: {\n  dato: 1\n}\nsink()\nv\nobj.dato\n"));
        assertTrue(definitions(server, opaque, 5, 0).isEmpty());
        assertTrue(definitions(server, opaque, 6, 5).isEmpty());
    }

    @Test
    void invalidPositionAndUnprovenReferenceReturnOrdinaryNoResult() throws Exception {
        ProjectFixture project = project("no-result");
        ProtosLanguageServer server = initialized(
                project,
                candidate -> candidate.equals(project.root())
                        ? Optional.of(project.binding())
                        : Optional.empty());

        String source = "f: (value) => {\n  sink(value)\n  value\n}\n";
        String uri = project.source().toUri().toString();
        server.textDocuments().didOpen(open(uri, 1, source));

        assertTrue(definitions(server, uri, 99, 0).isEmpty());
        assertTrue(definitions(server, uri, 2, 2).isEmpty());
    }

    @Test
    void contextualForBodyReadDefinesToTheHeaderBinder() throws Exception {
        ProjectFixture project = project("contextual-for");
        ProtosLanguageServer server = initialized(
                project,
                candidate -> candidate.equals(project.root())
                        ? Optional.of(project.binding())
                        : Optional.empty());

        String source = "for(item in items) {\n  item\n}\n";
        String uri = project.source().toUri().toString();
        server.textDocuments().didOpen(open(uri, 1, source));

        List<Location> locations = definitions(server, uri, 1, 2);
        assertEquals(1, locations.size());
        assertEquals(uri, locations.get(0).getUri());
        assertEquals(new Position(0, 4), locations.get(0).getRange().getStart());
        assertEquals(new Position(0, 8), locations.get(0).getRange().getEnd());

        // The contextual keyword is not a lexical read.
        assertTrue(definitions(server, uri, 0, 0).isEmpty());
    }

    @Test
    void namedArgumentLabelIsNotALexicalRead() throws Exception {
        ProjectFixture project = project("named-label");
        ProtosLanguageServer server = initialized(
                project,
                candidate -> candidate.equals(project.root())
                        ? Optional.of(project.binding())
                        : Optional.empty());

        // The label "value" at characters 21-25 of line 1 spells the
        // parameter "value"; the argument's value expression at 28 reads it.
        String source = "f: (value) => {\n  ((other) => other)(value: value)\n}\n";
        String uri = project.source().toUri().toString();
        server.textDocuments().didOpen(open(uri, 1, source));

        assertTrue(definitions(server, uri, 1, 21).isEmpty());
        assertTrue(definitions(server, uri, 1, 23).isEmpty());

        List<Location> locations = definitions(server, uri, 1, 29);
        assertEquals(1, locations.size());
        assertEquals(new Position(0, 4), locations.get(0).getRange().getStart());
        assertEquals(new Position(0, 9), locations.get(0).getRange().getEnd());
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
                        "definition-test");
        ProtosProjectBinding binding =
                new ProtosProjectBinding(
                        projection,
                        List.of(new ProtosProjectBinding.PackageRoot(
                                packageId,
                                "",
                                projectRoot)),
                        List.of(new ProtosProjectBinding.Source(ProtosProjectBinding.Source.Kind.SOURCE,
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

    private static List<Location> definitions(
            ProtosLanguageServer server,
            String uri,
            int line,
            int character) {
        DefinitionParams params = new DefinitionParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        params.setPosition(new Position(line, character));

        Either<List<? extends Location>, List<? extends LocationLink>> response =
                server.getTextDocumentService().definition(params).join();
        assertTrue(response.isLeft());
        return List.copyOf(response.getLeft());
    }

    private static void assertParameterLocation(List<Location> locations, String uri) {
        assertEquals(1, locations.size());
        assertEquals(uri, locations.get(0).getUri());
        assertEquals(new Position(0, 4), locations.get(0).getRange().getStart());
        assertEquals(new Position(0, 9), locations.get(0).getRange().getEnd());
    }

    private static void assertLocation(
            List<Location> locations,
            String uri,
            int startLine,
            int startCharacter,
            int endLine,
            int endCharacter) {
        assertEquals(1, locations.size());
        assertEquals(uri, locations.get(0).getUri());
        assertEquals(new Position(startLine, startCharacter), locations.get(0).getRange().getStart());
        assertEquals(new Position(endLine, endCharacter), locations.get(0).getRange().getEnd());
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

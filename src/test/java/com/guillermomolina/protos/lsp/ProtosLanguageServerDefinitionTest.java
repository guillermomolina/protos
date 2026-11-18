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
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
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

        // A read after an opaque invocation loses its exact origin proof but
        // keeps its lexical declaration, the enclosing parameter.
        String opaque = root.resolve("Opaque.protos").toUri().toString();
        server.textDocuments().didOpen(
                open(opaque, 1, "f: (value) => {\n  sink(value)\n  value\n}\n"));
        assertParameterLocation(definitions(server, opaque, 2, 2), opaque);

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
        // The selector range is exactly the member token. A cursor on the dot
        // sits just after the receiver read, and a cursor just after the
        // selector's last character still names the selector.
        assertLocation(definitions(server, object, 4, 3), object, 0, 0, 0, 3);
        assertLocation(definitions(server, object, 4, 4), object, 1, 2, 1, 6);
        assertLocation(definitions(server, object, 4, 7), object, 1, 2, 1, 6);
        assertLocation(definitions(server, object, 4, 8), object, 1, 2, 1, 6);
        assertLocation(definitions(server, object, 4, 1), object, 0, 0, 0, 3);
        assertLocation(definitions(server, object, 5, 6), object, 2, 2, 2, 9);

        // A textual URI alias is answered under exactly the requested URI.
        String alias = root.toUri().toString() + "./Alias.protos";
        server.textDocuments().didOpen(open(alias, 1, "mensaje: \"Hola\"\nmensaje\n"));
        assertLocation(definitions(server, alias, 1, 3), alias, 0, 0, 0, 7);

        // Opaque effects remove slot and member proofs. The slot read keeps its
        // lexical declaration; a member selector has no lexical declaration.
        String opaque = root.resolve("Opaque.protos").toUri().toString();
        server.textDocuments().didOpen(open(
                opaque, 1, "v: 1\nobj: {\n  dato: 1\n}\nsink()\nv\nobj.dato\n"));
        assertLocation(definitions(server, opaque, 5, 0), opaque, 0, 0, 0, 1);
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
        // The read after the opaque call has no exact origin, but its lexical
        // declaration is the parameter.
        assertParameterLocation(definitions(server, uri, 2, 2), uri);
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

    @Test
    void cursorJustAfterAProvenSlotReadNavigatesToTheSameCreation() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("selection-slot")).toRealPath();
        ProtosLanguageServer server =
                new ProtosLanguageServer(code -> {}, candidate -> Optional.empty());
        server.initialize(initializeParams(root)).join();

        String uri = root.resolve("Slots.protos").toUri().toString();
        server.textDocuments().didOpen(open(uri, 1, "x: 10\nx = 20\nprint(x)\n"));

        // Assignment target "x" spans line 1 characters 0-1.
        assertLocation(definitions(server, uri, 1, 0), uri, 0, 0, 0, 1);
        assertLocation(definitions(server, uri, 1, 1), uri, 0, 0, 0, 1);
        // Read "x" spans line 2 characters 6-7.
        assertLocation(definitions(server, uri, 2, 6), uri, 0, 0, 0, 1);
        assertLocation(definitions(server, uri, 2, 7), uri, 0, 0, 0, 1);
    }

    @Test
    void cursorJustAfterAProvenParameterReadNavigatesToTheSameParameter() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("selection-parameter")).toRealPath();
        ProtosLanguageServer server =
                new ProtosLanguageServer(code -> {}, candidate -> Optional.empty());
        server.initialize(initializeParams(root)).join();

        String uri = root.resolve("Parameter.protos").toUri().toString();
        server.textDocuments().didOpen(open(uri, 1, "f: (value) => {\n    value\n}\n"));

        // Read "value" spans line 1 characters 4-9.
        for (int character = 4; character <= 9; character++) {
            assertParameterLocation(definitions(server, uri, 1, character), uri);
        }
        assertTrue(definitions(server, uri, 1, 3).isEmpty());
    }

    @Test
    void cursorToleranceAcceptsOnlyTheExactEndOfAProvenReference() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("selection-bounds")).toRealPath();
        ProtosLanguageServer server =
                new ProtosLanguageServer(code -> {}, candidate -> Optional.empty());
        server.initialize(initializeParams(root)).join();

        String uri = root.resolve("Bounds.protos").toUri().toString();
        server.textDocuments().didOpen(open(uri, 1, "x: 10\nx = 20\nprint(x)\n"));

        // Exact start and exact end of the read "x" at line 2 characters 6-7.
        assertLocation(definitions(server, uri, 2, 6), uri, 0, 0, 0, 1);
        assertLocation(definitions(server, uri, 2, 7), uri, 0, 0, 0, 1);
        // One character after the end is the closing parenthesis boundary;
        // the tolerance never steps back more than one character.
        assertTrue(definitions(server, uri, 2, 8).isEmpty());
        // Before the start lies the opening parenthesis, just after the
        // unproven "print" read.
        assertTrue(definitions(server, uri, 2, 5).isEmpty());
        // Just after the assignment target, the following space and the
        // operator do not reach back to the target.
        assertTrue(definitions(server, uri, 1, 2).isEmpty());
        assertTrue(definitions(server, uri, 1, 3).isEmpty());
        // Document start is the creation target, which is not a read, and
        // there is no preceding character.
        assertTrue(definitions(server, uri, 0, 0).isEmpty());
        assertTrue(definitions(server, uri, 0, 1).isEmpty());
        // The empty last line at document end follows only a newline.
        assertTrue(definitions(server, uri, 3, 0).isEmpty());

        // A document that ends with a proven read accepts the end-of-document
        // cursor just after it.
        String end = root.resolve("End.protos").toUri().toString();
        server.textDocuments().didOpen(open(end, 1, "x: 10\nx"));
        assertLocation(definitions(server, end, 1, 0), end, 0, 0, 0, 1);
        assertLocation(definitions(server, end, 1, 1), end, 0, 0, 0, 1);

        // A member selector is answered at its last character and just after it.
        String member = root.resolve("Member.protos").toUri().toString();
        server.textDocuments().didOpen(open(member, 1, "obj: {\n  dato: 1\n}\nobj.dato\n"));
        assertLocation(definitions(server, member, 3, 7), member, 1, 2, 1, 6);
        assertLocation(definitions(server, member, 3, 8), member, 1, 2, 1, 6);

        // A read without an exact origin navigates to its lexical declaration
        // inside it and just after it, with the same tolerance.
        String unproven = root.resolve("Unproven.protos").toUri().toString();
        server.textDocuments().didOpen(
                open(unproven, 1, "f: (value) => {\n  sink(value)\n  value\n}\n"));
        assertParameterLocation(definitions(server, unproven, 2, 2), unproven);
        assertParameterLocation(definitions(server, unproven, 2, 4), unproven);
        assertParameterLocation(definitions(server, unproven, 2, 7), unproven);
        assertTrue(definitions(server, unproven, 2, 1).isEmpty());

        // Invalid syntax yields no proof at either cursor.
        String broken = root.resolve("Broken.protos").toUri().toString();
        server.textDocuments().didOpen(open(broken, 1, "x: 10\nprint(x\n"));
        assertTrue(definitions(server, broken, 1, 6).isEmpty());
        assertTrue(definitions(server, broken, 1, 7).isEmpty());

        // A URI that was never opened yields no proof.
        String unopened = root.resolve("Unopened.protos").toUri().toString();
        assertTrue(definitions(server, unopened, 2, 6).isEmpty());
        assertTrue(definitions(server, unopened, 2, 7).isEmpty());

        // A closed document yields no proof at either cursor.
        server.textDocuments().didClose(
                new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
        assertTrue(definitions(server, uri, 2, 6).isEmpty());
        assertTrue(definitions(server, uri, 2, 7).isEmpty());
    }

    @Test
    void immediatelyInvokedCaptureNavigatesWithoutAProjectBinding() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("immediate-capture")).toRealPath();
        ProtosLanguageServer server =
                new ProtosLanguageServer(code -> {}, candidate -> Optional.empty());
        server.initialize(initializeParams(root)).join();

        String uri = root.resolve("Capture.protos").toUri().toString();
        server.textDocuments().didOpen(open(uri, 1, "x: 1\n(() => x)()\n"));

        // The captured read "x" spans line 1 characters 7-8.
        assertLocation(definitions(server, uri, 1, 7), uri, 0, 0, 0, 1);
        assertLocation(definitions(server, uri, 1, 8), uri, 0, 0, 0, 1);

        // The edit moves the declaration; only the current snapshot answers.
        server.textDocuments().didChange(change(uri, 2, "valor: 0\nx: 1\n(() => x)()\n"));
        assertLocation(definitions(server, uri, 2, 7), uri, 1, 0, 1, 1);
        assertLocation(definitions(server, uri, 2, 8), uri, 1, 0, 1, 1);
        assertTrue(definitions(server, uri, 1, 7).isEmpty());

        server.textDocuments().didClose(
                new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
        assertTrue(definitions(server, uri, 2, 7).isEmpty());
        assertTrue(definitions(server, uri, 2, 8).isEmpty());
    }

    @Test
    void immediatelyInvokedCaptureNavigatesInADocumentOpenedWithoutWorkspace() {
        ProtosLanguageServer server =
                new ProtosLanguageServer(code -> {}, candidate -> Optional.empty());
        InitializeParams params = new InitializeParams();
        params.setCapabilities(new ClientCapabilities());
        server.initialize(params).join();

        String uri = temporary.resolve("Loose.protos").toUri().toString();
        server.textDocuments().didOpen(open(uri, 1, "x: 1\n(() => x)()\n"));

        assertLocation(definitions(server, uri, 1, 7), uri, 0, 0, 0, 1);
        assertLocation(definitions(server, uri, 1, 8), uri, 0, 0, 0, 1);
    }

    @Test
    void deferredCaptureAfterAnUnknownCallNavigatesToItsLexicalDeclaration() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("deferred-capture")).toRealPath();
        ProtosLanguageServer server =
                new ProtosLanguageServer(code -> {}, candidate -> Optional.empty());
        server.initialize(initializeParams(root)).join();

        String uri = root.resolve("Deferred.protos").toUri().toString();
        server.textDocuments().didOpen(
                open(uri, 1, "x: 1\nf: () => x\ndesconocido()\nf()\n"));

        // The captured read "x" spans line 1 characters 9-10. Its exact origin
        // is unproven, but its lexical declaration is the SOURCE creation.
        assertLocation(definitions(server, uri, 1, 9), uri, 0, 0, 0, 1);
        assertLocation(definitions(server, uri, 1, 10), uri, 0, 0, 0, 1);
    }

    @Test
    void objectBodyReadAndObjectBodyClosureNavigateToDifferentContexts() {
        ProtosLanguageServer server =
                new ProtosLanguageServer(code -> {}, candidate -> Optional.empty());
        InitializeParams params = new InitializeParams();
        params.setCapabilities(new ClientCapabilities());
        server.initialize(params).join();

        String uri = temporary.resolve("ObjectBody.protos").toUri().toString();
        server.textDocuments().didOpen(open(
                uri,
                1,
                "dato: 9\nobj: {\n  dato: 1\n  copia: dato\n  ejecutar: () => dato\n}\n"));

        // The direct read "dato" spans line 3 characters 9-13 and selects the
        // earlier slot of the object under construction (line 2 characters 2-6).
        assertLocation(definitions(server, uri, 3, 10), uri, 2, 2, 2, 6);
        assertLocation(definitions(server, uri, 3, 13), uri, 2, 2, 2, 6);

        // The Closure read "dato" spans line 4 characters 18-22; the object is
        // not its lexical context, so it selects the SOURCE creation.
        assertLocation(definitions(server, uri, 4, 19), uri, 0, 0, 0, 4);
        assertLocation(definitions(server, uri, 4, 22), uri, 0, 0, 0, 4);
    }

    @Test
    void deferredAndArgumentClosuresNavigateToTheirLexicalOrigins() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("lexical-callbacks")).toRealPath();
        ProtosLanguageServer server =
                new ProtosLanguageServer(code -> {}, candidate -> Optional.empty());
        server.initialize(initializeParams(root)).join();

        // Line 0 "valor: 1"; line 1 "guardada: (valor) => {"; line 2
        // "  valor"; line 4 "procesar((x) => valor)".
        String uri = root.resolve("Callbacks.protos").toUri().toString();
        server.textDocuments().didOpen(open(
                uri,
                1,
                "valor: 1\nguardada: (valor) => {\n  valor\n}\nprocesar((x) => valor)\n"
                        + "otra: () => {\n  valor: 2\n  procesar(() => valor)\n}\n"));

        // The parameter declaration is not a read; inside the deferred
        // Closure its parameter shadows the SOURCE slot.
        assertTrue(definitions(server, uri, 1, 12).isEmpty());
        assertLocation(definitions(server, uri, 2, 2), uri, 1, 11, 1, 16);
        assertLocation(definitions(server, uri, 2, 7), uri, 1, 11, 1, 16);
        // The Closure passed as an argument reaches the SOURCE slot.
        assertLocation(definitions(server, uri, 4, 16), uri, 0, 0, 0, 5);
        assertLocation(definitions(server, uri, 4, 21), uri, 0, 0, 0, 5);
        // A same-named slot of another activation is the nearer context.
        assertLocation(definitions(server, uri, 7, 17), uri, 6, 2, 6, 7);

        // Captured rest and defaulted parameters target only their identifiers.
        String parameters = root.resolve("Parameters.protos").toUri().toString();
        server.textDocuments().didOpen(open(
                parameters,
                1,
                "f: (a: 3, ...resto) => {\n  procesar(() => a + resto.size)\n}\n"));
        assertLocation(definitions(server, parameters, 1, 17), parameters, 0, 4, 0, 5);
        assertLocation(definitions(server, parameters, 1, 21), parameters, 0, 13, 0, 18);
    }

    @Test
    void defaultedParameterTargetIsOnlyItsIdentifier() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("defaulted-parameter")).toRealPath();
        ProtosLanguageServer server =
                new ProtosLanguageServer(code -> {}, candidate -> Optional.empty());
        server.initialize(initializeParams(root)).join();

        String uri = root.resolve("Defaulted.protos").toUri().toString();
        server.textDocuments().didOpen(open(uri, 1, "f: (a: 3) => a\n"));

        // The read "a" spans line 0 characters 13-14; the target excludes ": 3".
        assertLocation(definitions(server, uri, 0, 13), uri, 0, 4, 0, 5);
        assertLocation(definitions(server, uri, 0, 14), uri, 0, 4, 0, 5);
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

    private static DidChangeTextDocumentParams change(String uri, int version, String text) {
        VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier();
        identifier.setUri(uri);
        identifier.setVersion(version);
        TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent();
        event.setText(text);
        DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();
        params.setTextDocument(identifier);
        params.setContentChanges(List.of(event));
        return params;
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

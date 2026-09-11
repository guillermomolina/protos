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

import java.util.List;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

class ProtosLanguageServerDocumentSymbolsTest {

    @Test
    void projectsUniformPropertySymbolsWithOnlyValueSubtreeHierarchy() {
        ProtosLanguageServer server = initializedServer();
        ProtosTextDocumentService documents = server.textDocuments();
        String uri = "file:///workspace/symbols.protos";
        String source = "factory: (arg) => {\n"
                + "    local: 1\n"
                + "}\n"
                + "receiver.member: () => {\n"
                + "    nested: 2\n"
                + "}\n"
                + "duplicate: 1\n"
                + "duplicate: 2\n"
                + "existing = 3\n";

        documents.didOpen(open(uri, 1, source));
        List<DocumentSymbol> symbols = symbols(documents, uri);

        assertEquals(List.of("factory", "member", "duplicate", "duplicate"),
                symbols.stream().map(DocumentSymbol::getName).toList());
        assertTrue(symbols.stream().allMatch(symbol -> symbol.getKind() == SymbolKind.Property));

        DocumentSymbol factory = symbols.get(0);
        assertEquals(List.of("local"), childNames(factory));
        assertEquals(SymbolKind.Property, factory.getChildren().get(0).getKind());
        assertEquals(new Position(0, 0), factory.getSelectionRange().getStart());
        assertEquals(new Position(0, 7), factory.getSelectionRange().getEnd());

        DocumentSymbol member = symbols.get(1);
        assertEquals(List.of("nested"), childNames(member));
        assertEquals(new Position(3, 9), member.getSelectionRange().getStart());
        assertEquals(new Position(3, 15), member.getSelectionRange().getEnd());
    }

    @Test
    void preservesUtf16ColumnsAndReturnsNoTreeForCurrentParseFailure() {
        ProtosLanguageServer server = initializedServer();
        ProtosTextDocumentService documents = server.textDocuments();
        String uri = "file:///workspace/utf16.protos";

        documents.didOpen(open(uri, 1, "\"😀\".value: 1"));
        List<DocumentSymbol> valid = symbols(documents, uri);
        assertEquals(1, valid.size());
        assertEquals("value", valid.get(0).getName());
        assertEquals(new Position(0, 5), valid.get(0).getSelectionRange().getStart());
        assertEquals(new Position(0, 10), valid.get(0).getSelectionRange().getEnd());

        documents.didChange(change(uri, 2, "value:"));
        assertTrue(symbols(documents, uri).isEmpty());
    }

    @Test
    void closedOrUnknownDocumentHasNoSymbols() {
        ProtosLanguageServer server = initializedServer();
        assertTrue(symbols(server.textDocuments(), "file:///workspace/missing.protos").isEmpty());
    }

    private static ProtosLanguageServer initializedServer() {
        ProtosLanguageServer server = new ProtosLanguageServer(code -> {});
        InitializeParams params = new InitializeParams();
        ClientCapabilities client = new ClientCapabilities();
        TextDocumentClientCapabilities textDocument = new TextDocumentClientCapabilities();
        DocumentSymbolCapabilities documentSymbol = new DocumentSymbolCapabilities();
        documentSymbol.setHierarchicalDocumentSymbolSupport(Boolean.TRUE);
        textDocument.setDocumentSymbol(documentSymbol);
        client.setTextDocument(textDocument);
        params.setCapabilities(client);
        server.initialize(params).join();
        return server;
    }

    private static List<DocumentSymbol> symbols(
            ProtosTextDocumentService documents,
            String uri) {
        DocumentSymbolParams params = new DocumentSymbolParams();
        TextDocumentIdentifier identifier = new TextDocumentIdentifier();
        identifier.setUri(uri);
        params.setTextDocument(identifier);
        return documents.documentSymbol(params).join().stream()
                .map(ProtosLanguageServerDocumentSymbolsTest::rightSymbol)
                .toList();
    }

    private static DocumentSymbol rightSymbol(Either<SymbolInformation, DocumentSymbol> value) {
        assertTrue(value.isRight(), "D079 G2 must use the hierarchical DocumentSymbol projection");
        return value.getRight();
    }

    private static List<String> childNames(DocumentSymbol symbol) {
        List<DocumentSymbol> children = symbol.getChildren();
        return children == null
                ? List.of()
                : children.stream().map(DocumentSymbol::getName).toList();
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
}

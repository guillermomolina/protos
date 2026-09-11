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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.source.SourceSpan;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;

class ProtosLanguageServerDiagnosticsTest {

    @Test
    void invalidOpenPublishesExactParserDiagnosticAndFixClearsIt() {
        List<PublishDiagnosticsParams> published = new ArrayList<>();
        ProtosLanguageServer server = new ProtosLanguageServer(code -> {});
        server.connect(recordingClient(published));
        ProtosTextDocumentService documents = server.textDocuments();
        String uri = "file:///workspace/example.protos";

        documents.didOpen(open(uri, 7, "name\n)"));

        assertEquals(1, published.size());
        PublishDiagnosticsParams failure = published.get(0);
        assertEquals(uri, failure.getUri());
        assertEquals(Integer.valueOf(7), failure.getVersion());
        assertEquals(1, failure.getDiagnostics().size());

        Diagnostic diagnostic = failure.getDiagnostics().get(0);
        assertEquals(DiagnosticSeverity.Error, diagnostic.getSeverity());
        assertEquals("protos", diagnostic.getSource());
        assertEquals(new Position(1, 0), diagnostic.getRange().getStart());
        assertEquals(new Position(1, 1), diagnostic.getRange().getEnd());
        assertTrue(diagnostic.getMessage().isLeft());
        assertTrue(diagnostic.getMessage().getLeft().contains("RPAREN"));

        documents.didChange(change(uri, 8, "name\nother"));

        assertEquals(2, published.size());
        PublishDiagnosticsParams repaired = published.get(1);
        assertEquals(Integer.valueOf(8), repaired.getVersion());
        assertTrue(repaired.getDiagnostics().isEmpty());
    }

    @Test
    void closeClearsDiagnosticsWithoutInventingAnotherDocumentVersion() {
        List<PublishDiagnosticsParams> published = new ArrayList<>();
        ProtosLanguageServer server = new ProtosLanguageServer(code -> {});
        server.connect(recordingClient(published));
        ProtosTextDocumentService documents = server.textDocuments();
        String uri = "file:///workspace/example.protos";

        documents.didOpen(open(uri, 1, "name\n)"));
        documents.didClose(close(uri));

        assertEquals(2, published.size());
        PublishDiagnosticsParams cleared = published.get(1);
        assertEquals(uri, cleared.getUri());
        assertNull(cleared.getVersion());
        assertTrue(cleared.getDiagnostics().isEmpty());
        assertTrue(documents.currentSnapshot(uri).isEmpty());
    }

    @Test
    void sourcePositionsPreserveUtf16ColumnsAndLogicalCrLfLines() {
        Range utf16 = ProtosLspSourcePositions.range(
                "\"😀\" )",
                new SourceSpan(5, 6));
        assertEquals(new Position(0, 5), utf16.getStart());
        assertEquals(new Position(0, 6), utf16.getEnd());

        Range crlf = ProtosLspSourcePositions.range(
                "alpha\r\n)",
                new SourceSpan(7, 8));
        assertEquals(new Position(1, 0), crlf.getStart());
        assertEquals(new Position(1, 1), crlf.getEnd());
    }

    private static LanguageClient recordingClient(List<PublishDiagnosticsParams> published) {
        return (LanguageClient) Proxy.newProxyInstance(
                LanguageClient.class.getClassLoader(),
                new Class<?>[] {LanguageClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("publishDiagnostics")) {
                        published.add((PublishDiagnosticsParams) args[0]);
                        return null;
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "recording-language-client";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    return null;
                });
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

    private static DidCloseTextDocumentParams close(String uri) {
        TextDocumentIdentifier identifier = new TextDocumentIdentifier();
        identifier.setUri(uri);

        DidCloseTextDocumentParams params = new DidCloseTextDocumentParams();
        params.setTextDocument(identifier);
        return params;
    }
}

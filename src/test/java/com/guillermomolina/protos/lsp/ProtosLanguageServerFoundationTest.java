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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.analysis.ProtosDocumentSnapshot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ProtosLanguageServerFoundationTest {

    @Test
    void initializeWithoutHierarchySupportAdvertisesFullSyncAndWorkspaceSymbols() {
        ProtosLanguageServer server = new ProtosLanguageServer(code -> {});
        InitializeResult result = server.initialize(new InitializeParams()).join();

        TextDocumentSyncOptions sync =
                result.getCapabilities().getTextDocumentSync().getRight();
        assertNotNull(sync);
        assertEquals(Boolean.TRUE, sync.getOpenClose());
        assertEquals(TextDocumentSyncKind.Full, sync.getChange());

        assertNull(result.getCapabilities().getDefinitionProvider());
        assertNull(result.getCapabilities().getReferencesProvider());
        assertNull(result.getCapabilities().getHoverProvider());
        assertNull(result.getCapabilities().getCompletionProvider());
        assertNull(result.getCapabilities().getDocumentSymbolProvider());
        assertEquals(
                Boolean.TRUE,
                result.getCapabilities().getWorkspaceSymbolProvider().getLeft());
    }

    @Test
    void initializeAdvertisesDocumentSymbolsOnlyForHierarchicalClients() {
        ProtosLanguageServer server = new ProtosLanguageServer(code -> {});
        InitializeParams params = new InitializeParams();
        ClientCapabilities client = new ClientCapabilities();
        TextDocumentClientCapabilities textDocument = new TextDocumentClientCapabilities();
        DocumentSymbolCapabilities documentSymbol = new DocumentSymbolCapabilities();
        documentSymbol.setHierarchicalDocumentSymbolSupport(Boolean.TRUE);
        textDocument.setDocumentSymbol(documentSymbol);
        client.setTextDocument(textDocument);
        params.setCapabilities(client);

        InitializeResult result = server.initialize(params).join();
        assertEquals(Boolean.TRUE, result.getCapabilities().getDocumentSymbolProvider().getLeft());
        assertEquals(
                Boolean.TRUE,
                result.getCapabilities().getWorkspaceSymbolProvider().getLeft());
        assertNull(result.getCapabilities().getDefinitionProvider());
        assertNull(result.getCapabilities().getReferencesProvider());
    }

    @Test
    void fullSyncNotificationsOwnExactOpenDocumentSnapshots() {
        ProtosLanguageServer server = new ProtosLanguageServer(code -> {});
        ProtosTextDocumentService documents = server.textDocuments();

        String uri = "file:///workspace/example.protos";
        documents.didOpen(open(uri, 7, "value: 7"));

        assertEquals(
                new ProtosDocumentSnapshot(uri, 7, "value: 7"),
                documents.currentSnapshot(uri).orElseThrow());

        documents.didChange(change(uri, 3, "value: 3"));

        assertEquals(
                new ProtosDocumentSnapshot(uri, 3, "value: 3"),
                documents.currentSnapshot(uri).orElseThrow());

        documents.didClose(close(uri));
        assertTrue(documents.currentSnapshot(uri).isEmpty());
    }

    @Test
    void fullSyncAdapterRejectsIncrementalOrUnopenedChanges() {
        ProtosLanguageServer server = new ProtosLanguageServer(code -> {});
        ProtosTextDocumentService documents = server.textDocuments();
        String uri = "file:///workspace/example.protos";

        assertThrows(
                IllegalStateException.class,
                () -> documents.didChange(change(uri, 2, "new text")));

        documents.didOpen(open(uri, 1, "old text"));

        DidChangeTextDocumentParams incremental = change(uri, 2, "new text");
        incremental.getContentChanges().get(0).setRange(
                new org.eclipse.lsp4j.Range(
                        new org.eclipse.lsp4j.Position(0, 0),
                        new org.eclipse.lsp4j.Position(0, 1)));

        assertThrows(
                IllegalArgumentException.class,
                () -> documents.didChange(incremental));

        assertEquals(
                new ProtosDocumentSnapshot(uri, 1, "old text"),
                documents.currentSnapshot(uri).orElseThrow());
    }

    @Test
    void shutdownThenExitUsesSuccessAndPrematureExitUsesFailure() {
        AtomicInteger cleanExit = new AtomicInteger(-1);
        ProtosLanguageServer cleanServer = new ProtosLanguageServer(cleanExit::set);
        cleanServer.shutdown().join();
        cleanServer.exit();
        assertEquals(0, cleanExit.get());

        AtomicInteger prematureExit = new AtomicInteger(-1);
        ProtosLanguageServer prematureServer = new ProtosLanguageServer(prematureExit::set);
        prematureServer.exit();
        assertEquals(1, prematureExit.get());
    }

    @Test
    @Timeout(10)
    void stdioBindingProcessesRealLspContentLengthFraming() throws Exception {
        String initialize =
                "{\"jsonrpc\":\"2.0\",\"id\":1,"
                        + "\"method\":\"initialize\","
                        + "\"params\":{\"processId\":null,"
                        + "\"rootUri\":null,\"capabilities\":{}}}";
        byte[] inputBytes = frame(initialize);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProtosLanguageServer server = new ProtosLanguageServer(code -> {});
        Future<Void> listening =
                ProtosLanguageServerStdio.start(
                        server,
                        new ByteArrayInputStream(inputBytes),
                        output);

        listening.get(5, TimeUnit.SECONDS);
        awaitContains(output, "\"id\":1");
        String response = output.toString(StandardCharsets.UTF_8);

        assertTrue(response.contains("Content-Length:"));
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(response.contains("\"capabilities\""));
        assertTrue(response.contains("\"textDocumentSync\""));
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

    private static DidChangeTextDocumentParams change(
            String uri,
            int version,
            String text) {
        VersionedTextDocumentIdentifier identifier =
                new VersionedTextDocumentIdentifier();
        identifier.setUri(uri);
        identifier.setVersion(version);

        TextDocumentContentChangeEvent event =
                new TextDocumentContentChangeEvent();
        event.setText(text);

        DidChangeTextDocumentParams params =
                new DidChangeTextDocumentParams();
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

    private static byte[] frame(String payload) throws Exception {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(
                ("Content-Length: " + body.length + "\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
        bytes.write(body);
        return bytes.toByteArray();
    }

    private static void awaitContains(
            ByteArrayOutputStream output,
            String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (output.toString(StandardCharsets.UTF_8).contains(expected)) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(
                output.toString(StandardCharsets.UTF_8).contains(expected),
                "Timed out waiting for LSP response marker " + expected);
    }
}

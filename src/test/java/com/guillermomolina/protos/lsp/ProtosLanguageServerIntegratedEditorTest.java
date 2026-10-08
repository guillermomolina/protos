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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.analysis.ProtosProjectBinding;
import com.guillermomolina.protos.analysis.ProtosProjectBindingProjection;
import com.guillermomolina.protos.analysis.ProtosProjectBindingProvider;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpContext;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureHelpTriggerKind;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * LM010-D integrated acceptance over the real JSON-RPC transport.
 *
 * <p>Unlike the per-feature tests, which call {@link ProtosTextDocumentService}
 * directly, every request here is serialized by a standard LSP4J client,
 * framed through {@link ProtosLanguageServerStdio}, and deserialized back, so
 * the Hover, Completion and Signature Help projections are proven to survive
 * the wire alongside synchronization, diagnostics and navigation. Fixtures are
 * the ones already accepted by the per-feature tests.
 */
class ProtosLanguageServerIntegratedEditorTest {

    private static final long WAIT_SECONDS = 5;

    @TempDir Path temporary;

    private Connection connection;

    @AfterEach
    void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    @Timeout(10)
    void initializeAdvertisesExactlyTheLm010SurfaceOverTheWire() throws Exception {
        ProjectFixture project = project("capabilities");
        connection = connect(project);

        ServerCapabilities capabilities = connection.capabilities();
        assertEquals(Boolean.TRUE, capabilities.getHoverProvider().getLeft());
        assertEquals(Boolean.FALSE, capabilities.getCompletionProvider().getResolveProvider());
        assertEquals(List.of(), capabilities.getCompletionProvider().getTriggerCharacters());
        assertEquals(
                List.of("(", ","),
                capabilities.getSignatureHelpProvider().getTriggerCharacters());
        assertNull(capabilities.getSignatureHelpProvider().getRetriggerCharacters());

        assertNull(capabilities.getRenameProvider());
        assertNull(capabilities.getCodeActionProvider());
        assertNull(capabilities.getTypeDefinitionProvider());
        assertNull(capabilities.getInlayHintProvider());
        assertNull(capabilities.getSemanticTokensProvider());
        assertNull(capabilities.getDocumentHighlightProvider());
    }

    @Test
    @Timeout(10)
    void hoverAndCompletionProjectOnlyProvenFactsThroughTheTransport() throws Exception {
        ProjectFixture project = project("hover-completion");
        connection = connect(project);
        String uri = project.source().toUri().toString();

        connection.open(uri, 1, "f: (value, limit = 3) => {\n  value\n}\n");
        Hover proven = connection.hover(uri, 1, 3);
        assertEquals(
                "Proven binding\nClosure parameter: value",
                proven.getContents().getRight().getValue());
        assertEquals(range(1, 2, 1, 7), proven.getRange());

        connection.change(uri, 2, "f: (value) => {\n  sink(value)\n  value.name\n}\n");
        assertNull(connection.hover(uri, 1, 3));
        assertNull(connection.hover(uri, 2, 9));
        assertNull(connection.hover(uri, 99, 0));

        connection.change(uri, 3, "f: (first, second) => fir");
        CompletionList list = connection.completion(uri, 0, 25);
        assertFalse(list.isIncomplete());
        assertEquals(
                List.of("first", "second", "this", "context", "true", "false", "null"),
                list.getItems().stream().map(CompletionItem::getLabel).toList());
        assertEquals(
                new TextEdit(range(0, 22, 0, 25), "first"),
                list.getItems().get(0).getTextEdit().getLeft());

        connection.change(uri, 4, "f: (first) => {\n  first.se\n}\n");
        assertEmptyComplete(connection.completion(uri, 1, 10));
        connection.change(uri, 5, "g(fir");
        assertEmptyComplete(connection.completion(uri, 0, 5));
        assertEmptyComplete(connection.completion(uri, 0, 99));
    }

    @Test
    @Timeout(10)
    void signatureHelpTracksLiteralClosureArgumentsAndNeverGuesses() throws Exception {
        ProjectFixture project = project("signature");
        connection = connect(project);
        String uri = project.source().toUri().toString();

        String opened = "((a) => a)(";
        connection.open(uri, 1, opened);
        SignatureHelp onParen = connection.signatureHelp(uri, 0, opened.length(), "(");
        assertEquals("(a)", onParen.getSignatures().get(0).getLabel());

        String representative = "((first, second = 2, ...rest) => first)(1, ";
        connection.change(uri, 2, representative);
        SignatureHelp onComma = connection.signatureHelp(uri, 0, representative.length(), ",");
        assertEquals(1, onComma.getSignatures().size());
        assertEquals(0, onComma.getActiveSignature());
        assertEquals(1, onComma.getActiveParameter());
        assertEquals("(first, second = 2, ...rest)", onComma.getSignatures().get(0).getLabel());
        assertEquals(
                List.of("first", "second = 2", "...rest"),
                onComma.getSignatures().get(0).getParameters().stream()
                        .map(ParameterInformation::getLabel)
                        .map(label -> label.getLeft())
                        .toList());

        connection.change(uri, 3, "f: (a, b) => a\nf(1, 2)");
        assertNull(connection.signatureHelp(uri, 1, 5, ","));

        String spread = "((a, b) => a)(...values, ";
        connection.change(uri, 4, spread);
        assertNull(connection.signatureHelp(uri, 0, spread.length(), ","));

        String excess = "((a) => a)(1, ";
        connection.change(uri, 5, excess);
        assertNull(connection.signatureHelp(uri, 0, excess.length(), ","));

        // U+1F600 occupies two UTF-16 code units at characters 15 and 16.
        connection.change(uri, 6, "((a, b) => a)(\"😀\",\r\n  ");
        assertEquals(1, connection.signatureHelp(uri, 1, 2, null).getActiveParameter());
        assertNull(connection.signatureHelp(uri, 0, 16, null));
        assertNull(connection.signatureHelp(uri, 99, 0, null));
    }

    @Test
    @Timeout(10)
    void unsavedSnapshotsAndDocumentAuthorityGovernEveryLm010Answer() throws Exception {
        ProjectFixture project = project("authority");
        connection = connect(project);
        String uri = project.source().toUri().toString();

        connection.open(uri, 1, "((a) => a)(");
        connection.change(uri, 2, "((other) => other)(");
        assertEquals(
                "(other)",
                connection.signatureHelp(uri, 0, 19, null).getSignatures().get(0).getLabel());

        connection.change(uri, 3, "f: (other) => {\n  other\n}\n");
        assertEquals(
                "Proven binding\nClosure parameter: other",
                connection.hover(uri, 1, 3).getContents().getRight().getValue());

        connection.close(uri);
        assertNull(connection.signatureHelp(uri, 0, 19, null));
        assertNull(connection.hover(uri, 1, 3));
        assertEmptyComplete(connection.completion(uri, 1, 3));

        String source = "f: (value) => value\n((a) => a)(1)";
        String nonCanonicalUri = uri.replace("/Main.protos", "/./Main.protos");
        connection.open(nonCanonicalUri, 1, source);
        assertNull(connection.hover(nonCanonicalUri, 0, 14));
        assertNull(connection.signatureHelp(nonCanonicalUri, 1, 12, null));

        Path loose = temporary.resolve("Loose.protos");
        Files.writeString(loose, source);
        String looseUri = loose.toUri().toString();
        connection.open(looseUri, 1, source);
        assertNull(connection.hover(looseUri, 0, 14));
        assertNull(connection.signatureHelp(looseUri, 1, 12, null));
        assertEmptyComplete(connection.completion(looseUri, 0, 19));
    }

    @Test
    @Timeout(10)
    void diagnosticsNavigationAndLifecycleCoexistWithLm010() throws Exception {
        ProjectFixture project = project("coexistence");
        connection = connect(project);
        String uri = project.source().toUri().toString();

        connection.open(uri, 7, "name\n)");
        PublishDiagnosticsParams failure = connection.nextDiagnostics();
        assertEquals(uri, failure.getUri());
        assertEquals(Integer.valueOf(7), failure.getVersion());
        assertEquals(1, failure.getDiagnostics().size());
        assertEquals(DiagnosticSeverity.Error, failure.getDiagnostics().get(0).getSeverity());
        assertEquals(range(1, 0, 1, 1), failure.getDiagnostics().get(0).getRange());

        connection.change(uri, 8, "f: (value) => {\n  value\n}\n");
        PublishDiagnosticsParams repaired = connection.nextDiagnostics();
        assertEquals(Integer.valueOf(8), repaired.getVersion());
        assertTrue(repaired.getDiagnostics().isEmpty());

        List<? extends Location> definitions = connection.definition(uri, 1, 2);
        assertEquals(1, definitions.size());
        assertEquals(uri, definitions.get(0).getUri());
        assertEquals(range(0, 4, 0, 9), definitions.get(0).getRange());
        assertNotNull(connection.hover(uri, 1, 3));

        assertNull(connection.server().shutdown().get(WAIT_SECONDS, TimeUnit.SECONDS));
        connection.server().exit();
        assertEquals(0, connection.awaitExitCode());
    }

    private Connection connect(ProjectFixture project) throws Exception {
        Pipe toServer = Pipe.open();
        Pipe toClient = Pipe.open();
        AtomicInteger exitCode = new AtomicInteger(-1);
        ProtosLanguageServer server = new ProtosLanguageServer(exitCode::set, canonical(project));
        Future<Void> serverListening = ProtosLanguageServerStdio.start(
                server,
                Channels.newInputStream(toServer.source()),
                Channels.newOutputStream(toClient.sink()));

        RecordingClient client = new RecordingClient();
        ExecutorService clientExecutor = Executors.newCachedThreadPool();
        Launcher<LanguageServer> launcher = new LSPLauncher.Builder<LanguageServer>()
                .setLocalService(client)
                .setRemoteInterface(LanguageServer.class)
                .setInput(Channels.newInputStream(toClient.source()))
                .setOutput(Channels.newOutputStream(toServer.sink()))
                .setExecutorService(clientExecutor)
                .create();
        Future<Void> clientListening = launcher.startListening();

        LanguageServer remote = launcher.getRemoteProxy();
        return new Connection(
                remote,
                initialize(remote, project.root()),
                client,
                exitCode,
                toServer,
                toClient,
                serverListening,
                clientListening,
                clientExecutor);
    }

    private static ServerCapabilities initialize(LanguageServer server, Path root)
            throws Exception {
        InitializeParams params = new InitializeParams();
        params.setCapabilities(new ClientCapabilities());
        WorkspaceFolder folder = new WorkspaceFolder();
        folder.setUri(root.toUri().toString());
        folder.setName(root.getFileName().toString());
        params.setWorkspaceFolders(List.of(folder));
        ServerCapabilities capabilities = server.initialize(params)
                .get(WAIT_SECONDS, TimeUnit.SECONDS)
                .getCapabilities();
        server.initialized(new InitializedParams());
        return capabilities;
    }

    private static void assertEmptyComplete(CompletionList list) {
        assertFalse(list.isIncomplete());
        assertTrue(list.getItems().isEmpty());
    }

    private static Range range(int startLine, int startCharacter, int endLine, int endCharacter) {
        return new Range(
                new Position(startLine, startCharacter),
                new Position(endLine, endCharacter));
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
                        "integrated-editor-test");
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

    private record ProjectFixture(Path root, Path source, ProtosProjectBinding binding) {
    }

    /** Minimal client endpoint: records diagnostics and ignores other notifications. */
    private static final class RecordingClient implements LanguageClient {
        private final BlockingQueue<PublishDiagnosticsParams> diagnostics =
                new LinkedBlockingQueue<>();

        @Override
        public void telemetryEvent(Object object) {
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams params) {
            diagnostics.add(params);
        }

        @Override
        public void showMessage(MessageParams params) {
        }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(
                ShowMessageRequestParams params) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams params) {
        }
    }

    /**
     * One client/server session over two in-process byte pipes. Closing both
     * sinks delivers end-of-stream to each listener, which then terminates.
     */
    private record Connection(
            LanguageServer server,
            ServerCapabilities capabilities,
            RecordingClient client,
            AtomicInteger exitCode,
            Pipe toServer,
            Pipe toClient,
            Future<Void> serverListening,
            Future<Void> clientListening,
            ExecutorService clientExecutor) implements AutoCloseable {

        private void open(String uri, int version, String text) {
            TextDocumentItem item = new TextDocumentItem();
            item.setUri(uri);
            item.setLanguageId("protos");
            item.setVersion(version);
            item.setText(text);
            server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(item));
        }

        private void change(String uri, int version, String text) {
            TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent();
            event.setText(text);
            server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(
                    new VersionedTextDocumentIdentifier(uri, version),
                    List.of(event)));
        }

        private void close(String uri) {
            server.getTextDocumentService().didClose(
                    new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
        }

        private Hover hover(String uri, int line, int character) throws Exception {
            return server.getTextDocumentService()
                    .hover(new HoverParams(
                            new TextDocumentIdentifier(uri),
                            new Position(line, character)))
                    .get(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        private CompletionList completion(String uri, int line, int character) throws Exception {
            return server.getTextDocumentService()
                    .completion(new CompletionParams(
                            new TextDocumentIdentifier(uri),
                            new Position(line, character)))
                    .get(WAIT_SECONDS, TimeUnit.SECONDS)
                    .getRight();
        }

        private SignatureHelp signatureHelp(
                String uri,
                int line,
                int character,
                String triggerCharacter) throws Exception {
            SignatureHelpParams params = new SignatureHelpParams(
                    new TextDocumentIdentifier(uri),
                    new Position(line, character));
            if (triggerCharacter != null) {
                SignatureHelpContext context = new SignatureHelpContext(
                        SignatureHelpTriggerKind.TriggerCharacter,
                        false);
                context.setTriggerCharacter(triggerCharacter);
                params.setContext(context);
            }
            return server.getTextDocumentService()
                    .signatureHelp(params)
                    .get(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        private List<? extends Location> definition(String uri, int line, int character)
                throws Exception {
            return server.getTextDocumentService()
                    .definition(new DefinitionParams(
                            new TextDocumentIdentifier(uri),
                            new Position(line, character)))
                    .get(WAIT_SECONDS, TimeUnit.SECONDS)
                    .getLeft();
        }

        private PublishDiagnosticsParams nextDiagnostics() throws Exception {
            PublishDiagnosticsParams params =
                    client.diagnostics.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertNotNull(params, "Timed out waiting for publishDiagnostics");
            return params;
        }

        private int awaitExitCode() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (exitCode.get() < 0 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            return exitCode.get();
        }

        @Override
        public void close() throws Exception {
            closeQuietly(toServer.sink());
            closeQuietly(toClient.sink());
            try {
                serverListening.get(WAIT_SECONDS, TimeUnit.SECONDS);
                clientListening.get(WAIT_SECONDS, TimeUnit.SECONDS);
            } finally {
                closeQuietly(toServer.source());
                closeQuietly(toClient.source());
                clientExecutor.shutdownNow();
                clientExecutor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
            }
        }

        private static void closeQuietly(java.nio.channels.Channel channel) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // Teardown only; the listener futures report real transport failures.
            }
        }
    }
}

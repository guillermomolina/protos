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

import com.guillermomolina.protos.analysis.ProtosProjectBindingProvider;
import com.guillermomolina.protos.analysis.ProtosStaticAnalysisSession;
import com.guillermomolina.protos.execution.ProtosProjectFileBindingProvider;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * Thin LSP lifecycle/protocol edge over the editor-neutral static-analysis core.
 *
 * <p>This class retains the LM009-F full document-synchronization boundary.
 * LM009-G1 adds parser-derived push diagnostics, G2 adds D079-ratified
 * hierarchical document symbols, G3 adds D082/D106 workspace symbols, and G4
 * adds D110 exact fail-closed go-to-definition. Later H slices own completion,
 * hover, signature help and references.</p>
 */
public final class ProtosLanguageServer implements LanguageServer, LanguageClientAware {
    private final ProtosStaticAnalysisSession analysisSession;
    private final ProtosTextDocumentService textDocumentService;
    private final ProtosWorkspaceService workspaceService;
    private final IntConsumer exitHandler;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean();

    ProtosLanguageServer(IntConsumer exitHandler) {
        this(exitHandler, new ProtosProjectFileBindingProvider());
    }

    ProtosLanguageServer(
            IntConsumer exitHandler,
            ProtosProjectBindingProvider projectBindingProvider) {
        this.analysisSession = new ProtosStaticAnalysisSession();
        this.textDocumentService = new ProtosTextDocumentService(analysisSession);
        this.workspaceService = new ProtosWorkspaceService(
                textDocumentService,
                Objects.requireNonNull(projectBindingProvider, "projectBindingProvider"));
        this.textDocumentService.setDefinitionSourceAuthority(
                workspaceService::ownsCanonicalSourceUri);
        this.exitHandler = Objects.requireNonNull(exitHandler, "exitHandler");
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        Objects.requireNonNull(params, "params");
        workspaceService.configure(params);

        TextDocumentSyncOptions sync = new TextDocumentSyncOptions();
        sync.setOpenClose(Boolean.TRUE);
        sync.setChange(TextDocumentSyncKind.Full);

        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(sync);

        boolean hierarchicalDocumentSymbols = supportsHierarchicalDocumentSymbols(params);
        textDocumentService.setHierarchicalDocumentSymbolsEnabled(hierarchicalDocumentSymbols);
        if (hierarchicalDocumentSymbols) {
            capabilities.setDocumentSymbolProvider(Boolean.TRUE);
        }
        capabilities.setWorkspaceSymbolProvider(Boolean.TRUE);
        capabilities.setDefinitionProvider(Boolean.TRUE);

        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    private static boolean supportsHierarchicalDocumentSymbols(InitializeParams params) {
        if (params.getCapabilities() == null
                || params.getCapabilities().getTextDocument() == null
                || params.getCapabilities().getTextDocument().getDocumentSymbol() == null) {
            return false;
        }
        return Boolean.TRUE.equals(params.getCapabilities()
                .getTextDocument()
                .getDocumentSymbol()
                .getHierarchicalDocumentSymbolSupport());
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        shutdownRequested.set(true);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        exitHandler.accept(shutdownRequested.get() ? 0 : 1);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        textDocumentService.connect(Objects.requireNonNull(client, "client"));
    }

    ProtosTextDocumentService textDocuments() {
        return textDocumentService;
    }

    ProtosStaticAnalysisSession analysisSession() {
        return analysisSession;
    }
}

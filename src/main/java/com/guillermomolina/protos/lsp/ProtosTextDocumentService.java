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

import com.guillermomolina.protos.analysis.ProtosDocumentSnapshot;
import com.guillermomolina.protos.analysis.ProtosDocumentSymbol;
import com.guillermomolina.protos.analysis.ProtosDocumentSymbols;
import com.guillermomolina.protos.analysis.ProtosStaticAnalysisSession;
import com.guillermomolina.protos.analysis.ProtosStaticDefinitionResult;
import com.guillermomolina.protos.analysis.ProtosStaticParseResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * LSP document-synchronization adapter.
 *
 * <p>The adapter keeps all currently open editor buffers in one server-local
 * custody domain. G1 parses only each exact immutable buffer snapshot and emits
 * parser-derived diagnostics. G2 derives D079 document symbols from the same
 * current parser-authoritative snapshot. G4 definition queries consume only
 * D110 proof results after the workspace edge confirms exact canonical
 * ProjectBinding source ownership; the open-document domain itself never
 * becomes project/package/module authority.</p>
 */
final class ProtosTextDocumentService implements TextDocumentService {
    static final String OPEN_DOCUMENTS_DOMAIN = "lsp:open-documents";

    private final ProtosStaticAnalysisSession session;
    private volatile LanguageClient client;
    private volatile boolean hierarchicalDocumentSymbolsEnabled;
    private volatile Predicate<String> definitionSourceAuthority = ignored -> false;

    ProtosTextDocumentService(ProtosStaticAnalysisSession session) {
        this.session = Objects.requireNonNull(session, "session");
        this.session.openWorkspace(OPEN_DOCUMENTS_DOMAIN);
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        Objects.requireNonNull(params, "params");
        TextDocumentItem document =
                Objects.requireNonNull(params.getTextDocument(), "textDocument");
        String uri = Objects.requireNonNull(document.getUri(), "textDocument.uri");
        Integer version =
                Objects.requireNonNull(document.getVersion(), "textDocument.version");
        String text = Objects.requireNonNull(document.getText(), "textDocument.text");

        session.putDocument(
                OPEN_DOCUMENTS_DOMAIN,
                new ProtosDocumentSnapshot(uri, version.longValue(), text));
        publishCurrentDiagnostics(uri);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        Objects.requireNonNull(params, "params");
        VersionedTextDocumentIdentifier document =
                Objects.requireNonNull(params.getTextDocument(), "textDocument");
        String uri = Objects.requireNonNull(document.getUri(), "textDocument.uri");
        Integer version =
                Objects.requireNonNull(document.getVersion(), "textDocument.version");

        if (session.currentSnapshot(OPEN_DOCUMENTS_DOMAIN, uri).isEmpty()) {
            throw new IllegalStateException(
                    "LSP change received for document that is not open: " + uri);
        }

        List<TextDocumentContentChangeEvent> changes =
                Objects.requireNonNull(params.getContentChanges(), "contentChanges");
        if (changes.size() != 1 || changes.get(0).getRange() != null) {
            throw new IllegalArgumentException(
                    "Protos F3 advertises full document synchronization only");
        }

        String text = Objects.requireNonNull(changes.get(0).getText(), "contentChanges[0].text");
        session.putDocument(
                OPEN_DOCUMENTS_DOMAIN,
                new ProtosDocumentSnapshot(uri, version.longValue(), text));
        publishCurrentDiagnostics(uri);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        Objects.requireNonNull(params, "params");
        String uri = Objects.requireNonNull(
                Objects.requireNonNull(params.getTextDocument(), "textDocument").getUri(),
                "textDocument.uri");
        session.closeDocument(OPEN_DOCUMENTS_DOMAIN, uri);
        publishDiagnostics(uri, null, List.of());
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        Objects.requireNonNull(params, "params");
        String uri = Objects.requireNonNull(
                Objects.requireNonNull(params.getTextDocument(), "textDocument").getUri(),
                "textDocument.uri");

        if (!hierarchicalDocumentSymbolsEnabled) {
            return CompletableFuture.completedFuture(List.of());
        }

        Optional<ProtosStaticParseResult> parsed =
                session.parseCurrent(OPEN_DOCUMENTS_DOMAIN, uri);
        if (parsed.isEmpty() || !session.isCurrent(OPEN_DOCUMENTS_DOMAIN, parsed.get())) {
            return CompletableFuture.completedFuture(List.of());
        }
        if (!(parsed.get() instanceof ProtosStaticParseResult.Parsed success)) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<ProtosDocumentSymbol> symbols = ProtosDocumentSymbols.from(success.program());
        if (!session.isCurrent(OPEN_DOCUMENTS_DOMAIN, success)) {
            return CompletableFuture.completedFuture(List.of());
        }

        String source = success.snapshot().characters();
        return CompletableFuture.completedFuture(symbols.stream()
                .map(symbol -> Either.<SymbolInformation, DocumentSymbol>forRight(
                        toLspDocumentSymbol(source, symbol)))
                .toList());
    }


    @Override
    public CompletableFuture<
                    Either<List<? extends Location>, List<? extends LocationLink>>>
            definition(DefinitionParams params) {
        Objects.requireNonNull(params, "params");
        String uri = Objects.requireNonNull(
                Objects.requireNonNull(params.getTextDocument(), "textDocument").getUri(),
                "textDocument.uri");

        Predicate<String> authority = definitionSourceAuthority;
        if (!authority.test(uri)) {
            return noDefinition();
        }

        Optional<ProtosDocumentSnapshot> current =
                session.currentSnapshot(OPEN_DOCUMENTS_DOMAIN, uri);
        if (current.isEmpty()) {
            return noDefinition();
        }
        OptionalInt sourceOffset =
                ProtosLspSourcePositions.offset(
                        current.get().characters(),
                        Objects.requireNonNull(params.getPosition(), "position"));
        if (sourceOffset.isEmpty()) {
            return noDefinition();
        }

        Optional<ProtosStaticDefinitionResult> definition =
                session.definitionCurrent(
                        OPEN_DOCUMENTS_DOMAIN,
                        uri,
                        sourceOffset.getAsInt());
        if (definition.isEmpty()) {
            return noDefinition();
        }

        ProtosStaticDefinitionResult proven = definition.get();
        if (!session.isCurrent(OPEN_DOCUMENTS_DOMAIN, proven)
                || !authority.test(uri)
                || !proven.referenceSnapshot().documentId().equals(uri)) {
            return noDefinition();
        }

        for (ProtosStaticDefinitionResult.Target target : proven.targets()) {
            if (!target.snapshot().equals(proven.referenceSnapshot())
                    || !target.snapshot().documentId().equals(uri)) {
                return noDefinition();
            }
        }

        List<Location> locations = proven.targets().stream()
                .map(target -> new Location(
                        target.snapshot().documentId(),
                        ProtosLspSourcePositions.range(
                                target.snapshot().characters(),
                                target.span())))
                .toList();
        List<? extends Location> left = locations;
        return CompletableFuture.completedFuture(Either.forLeft(left));
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        Objects.requireNonNull(params, "params");
        // Save notifications are not advertised by F3 and carry no additional
        // foundation behavior if a client sends one defensively.
    }

    void connect(LanguageClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    void setHierarchicalDocumentSymbolsEnabled(boolean enabled) {
        hierarchicalDocumentSymbolsEnabled = enabled;
    }


    void setDefinitionSourceAuthority(Predicate<String> authority) {
        definitionSourceAuthority =
                Objects.requireNonNull(authority, "authority");
    }

    private static CompletableFuture<
                    Either<List<? extends Location>, List<? extends LocationLink>>>
            noDefinition() {
        List<? extends Location> empty = List.of();
        return CompletableFuture.completedFuture(Either.forLeft(empty));
    }

    private static DocumentSymbol toLspDocumentSymbol(
            String source,
            ProtosDocumentSymbol model) {
        DocumentSymbol symbol = new DocumentSymbol();
        symbol.setName(model.name());
        symbol.setKind(SymbolKind.Property);
        symbol.setRange(ProtosLspSourcePositions.range(source, model.range()));
        symbol.setSelectionRange(ProtosLspSourcePositions.range(source, model.selectionRange()));
        if (!model.children().isEmpty()) {
            symbol.setChildren(model.children().stream()
                    .map(child -> toLspDocumentSymbol(source, child))
                    .toList());
        }
        return symbol;
    }

    private void publishCurrentDiagnostics(String documentUri) {
        LanguageClient currentClient = client;
        if (currentClient == null) {
            return;
        }

        Optional<ProtosStaticParseResult> parsed =
                session.parseCurrent(OPEN_DOCUMENTS_DOMAIN, documentUri);
        if (parsed.isEmpty() || !session.isCurrent(OPEN_DOCUMENTS_DOMAIN, parsed.get())) {
            return;
        }

        ProtosStaticParseResult result = parsed.get();
        List<Diagnostic> diagnostics;
        if (result instanceof ProtosStaticParseResult.Failed failure) {
            Diagnostic diagnostic = new Diagnostic();
            diagnostic.setRange(ProtosLspSourcePositions.range(
                    result.snapshot().characters(),
                    failure.span()));
            diagnostic.setSeverity(DiagnosticSeverity.Error);
            diagnostic.setSource("protos");
            diagnostic.setMessage(failure.message());
            diagnostics = List.of(diagnostic);
        } else {
            diagnostics = List.of();
        }

        publishDiagnostics(
                documentUri,
                Math.toIntExact(result.snapshot().version()),
                diagnostics,
                currentClient);
    }

    private void publishDiagnostics(
            String documentUri,
            Integer version,
            List<Diagnostic> diagnostics) {
        LanguageClient currentClient = client;
        if (currentClient == null) {
            return;
        }
        publishDiagnostics(documentUri, version, diagnostics, currentClient);
    }

    private static void publishDiagnostics(
            String documentUri,
            Integer version,
            List<Diagnostic> diagnostics,
            LanguageClient currentClient) {
        PublishDiagnosticsParams params = new PublishDiagnosticsParams();
        params.setUri(documentUri);
        params.setVersion(version);
        params.setDiagnostics(diagnostics);
        currentClient.publishDiagnostics(params);
    }

    Optional<ProtosDocumentSnapshot> currentSnapshot(String documentUri) {
        return session.currentSnapshot(OPEN_DOCUMENTS_DOMAIN, documentUri);
    }
}

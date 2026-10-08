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
import com.guillermomolina.protos.analysis.ProtosStaticCompletionResult;
import com.guillermomolina.protos.analysis.ProtosStaticDefinitionResult;
import com.guillermomolina.protos.analysis.ProtosStaticHoverResult;
import com.guillermomolina.protos.analysis.ProtosStaticLint;
import com.guillermomolina.protos.analysis.ProtosStaticLintDiagnostic;
import com.guillermomolina.protos.analysis.ProtosStaticParseResult;
import com.guillermomolina.protos.analysis.ProtosStaticReferenceResult;
import com.guillermomolina.protos.analysis.ProtosStaticSignatureHelpResult;
import com.guillermomolina.protos.execution.ProtosWholeDocumentFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
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
 * ProjectBinding source ownership. H1 adds D124 references by inverting only
 * those D110-proven identities; the open-document domain itself never becomes
 * project/package/module authority. LM011-D1 formats the exact current open
 * snapshot through the TOOL010 whole-document authority and returns at most
 * one full-document edit; editor formatting options never select style.
 * LM010-A hover publishes only D110-proven bindings and explicitly labeled
 * parser-derived syntax facts, under the same canonical-source authority and
 * snapshot-freshness checks as definition. LM012-B1 adds the D194 warning
 * lint findings for a successfully parsed snapshot to the same single
 * publication; a stale snapshot publishes nothing. LM010-B completion
 * publishes only D110-proven Closure parameters and parser-accepted
 * reserved-word syntax at a proven read site, under the same authority and
 * freshness checks; every response is a complete list
 * ({@code isIncomplete=false}). LM010-C signature help publishes the one
 * signature of a call whose callee is a literal Closure, under the same
 * authority and freshness checks, and publishes nothing rather than a false
 * parameter highlight.</p>
 */
final class ProtosTextDocumentService implements TextDocumentService {
    static final String OPEN_DOCUMENTS_DOMAIN = "lsp:open-documents";

    private final ProtosStaticAnalysisSession session;
    private final ProtosLspDocumentFormatter documentFormatter;
    private volatile LanguageClient client;
    private volatile boolean hierarchicalDocumentSymbolsEnabled;
    private volatile Predicate<String> navigationSourceAuthority = ignored -> false;

    ProtosTextDocumentService(ProtosStaticAnalysisSession session) {
        this(session, ProtosLspDocumentFormatter.toolchain());
    }

    ProtosTextDocumentService(
            ProtosStaticAnalysisSession session,
            ProtosLspDocumentFormatter documentFormatter) {
        this.session = Objects.requireNonNull(session, "session");
        this.documentFormatter =
                Objects.requireNonNull(documentFormatter, "documentFormatter");
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
    public CompletableFuture<List<? extends TextEdit>> formatting(
            DocumentFormattingParams params) {
        Objects.requireNonNull(params, "params");
        String uri = Objects.requireNonNull(
                Objects.requireNonNull(params.getTextDocument(), "textDocument").getUri(),
                "textDocument.uri");

        // D183: params.getOptions() is accepted but never style authority.
        Optional<ProtosDocumentSnapshot> captured =
                session.currentSnapshot(OPEN_DOCUMENTS_DOMAIN, uri);
        if (captured.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        ProtosWholeDocumentFormatter.Result result;
        try {
            result = documentFormatter.format(captured.get());
        } catch (Exception failure) {
            return CompletableFuture.failedFuture(failure);
        }

        String original = captured.get().characters();
        if (result.status() != ProtosWholeDocumentFormatter.Status.SUCCESS
                || result.source().equals(original)
                || !captured.equals(session.currentSnapshot(OPEN_DOCUMENTS_DOMAIN, uri))) {
            return CompletableFuture.completedFuture(List.of());
        }

        Range wholeDocument = new Range(
                new Position(0, 0),
                ProtosLspSourcePositions.position(original, original.length()));
        return CompletableFuture.completedFuture(
                List.of(new TextEdit(wholeDocument, result.source())));
    }

    @Override
    public CompletableFuture<
                    Either<List<? extends Location>, List<? extends LocationLink>>>
            definition(DefinitionParams params) {
        Objects.requireNonNull(params, "params");
        String uri = Objects.requireNonNull(
                Objects.requireNonNull(params.getTextDocument(), "textDocument").getUri(),
                "textDocument.uri");

        Predicate<String> authority = navigationSourceAuthority;
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
    public CompletableFuture<List<? extends Location>> references(
            ReferenceParams params) {
        Objects.requireNonNull(params, "params");
        String uri = Objects.requireNonNull(
                Objects.requireNonNull(params.getTextDocument(), "textDocument").getUri(),
                "textDocument.uri");

        Predicate<String> authority = navigationSourceAuthority;
        if (!authority.test(uri)) {
            return noReferences();
        }

        Optional<ProtosDocumentSnapshot> current =
                session.currentSnapshot(OPEN_DOCUMENTS_DOMAIN, uri);
        if (current.isEmpty()) {
            return noReferences();
        }
        OptionalInt sourceOffset =
                ProtosLspSourcePositions.offset(
                        current.get().characters(),
                        Objects.requireNonNull(params.getPosition(), "position"));
        if (sourceOffset.isEmpty()) {
            return noReferences();
        }

        Optional<ProtosStaticReferenceResult> references =
                session.referencesCurrent(
                        OPEN_DOCUMENTS_DOMAIN,
                        uri,
                        sourceOffset.getAsInt());
        if (references.isEmpty()) {
            return noReferences();
        }

        ProtosStaticReferenceResult proven = references.get();
        if (!session.isCurrent(OPEN_DOCUMENTS_DOMAIN, proven)
                || !authority.test(uri)
                || !proven.seedSnapshot().documentId().equals(uri)) {
            return noReferences();
        }

        ProtosStaticDefinitionResult.Target target = proven.target();
        if (!target.snapshot().equals(proven.seedSnapshot())
                || !target.snapshot().documentId().equals(uri)) {
            return noReferences();
        }
        for (ProtosStaticReferenceResult.Occurrence occurrence : proven.occurrences()) {
            if (!occurrence.snapshot().equals(proven.seedSnapshot())
                    || !occurrence.snapshot().documentId().equals(uri)) {
                return noReferences();
            }
        }

        ArrayList<ProtosStaticReferenceResult.Occurrence> selected =
                new ArrayList<>(proven.occurrences());
        if (Objects.requireNonNull(params.getContext(), "context")
                .isIncludeDeclaration()) {
            selected.add(new ProtosStaticReferenceResult.Occurrence(
                    target.snapshot(),
                    target.span()));
        }
        selected.sort(Comparator
                .comparing((ProtosStaticReferenceResult.Occurrence occurrence) ->
                        occurrence.snapshot().documentId())
                .thenComparingInt(occurrence -> occurrence.span().startOffset())
                .thenComparingInt(occurrence -> occurrence.span().endOffset()));

        List<Location> locations = selected.stream()
                .distinct()
                .map(occurrence -> new Location(
                        occurrence.snapshot().documentId(),
                        ProtosLspSourcePositions.range(
                                occurrence.snapshot().characters(),
                                occurrence.span())))
                .toList();
        List<? extends Location> response = locations;
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        Objects.requireNonNull(params, "params");
        String uri = Objects.requireNonNull(
                Objects.requireNonNull(params.getTextDocument(), "textDocument").getUri(),
                "textDocument.uri");

        Predicate<String> authority = navigationSourceAuthority;
        if (!authority.test(uri)) {
            return noHover();
        }

        Optional<ProtosDocumentSnapshot> current =
                session.currentSnapshot(OPEN_DOCUMENTS_DOMAIN, uri);
        if (current.isEmpty()) {
            return noHover();
        }
        OptionalInt sourceOffset =
                ProtosLspSourcePositions.offset(
                        current.get().characters(),
                        Objects.requireNonNull(params.getPosition(), "position"));
        if (sourceOffset.isEmpty()) {
            return noHover();
        }

        Optional<ProtosStaticHoverResult> hover =
                session.hoverCurrent(
                        OPEN_DOCUMENTS_DOMAIN,
                        uri,
                        sourceOffset.getAsInt());
        if (hover.isEmpty()) {
            return noHover();
        }

        ProtosStaticHoverResult projected = hover.get();
        if (!session.isCurrent(OPEN_DOCUMENTS_DOMAIN, projected)
                || !authority.test(uri)
                || !projected.snapshot().documentId().equals(uri)) {
            return noHover();
        }

        List<String> sections = new ArrayList<>();
        for (ProtosStaticHoverResult.Section section : projected.sections()) {
            String label = switch (section.kind()) {
                case PROVEN_BINDING -> "Proven binding";
                case SYNTAX -> "Syntax";
            };
            sections.add(label + "\n" + String.join("\n", section.facts()));
        }
        return CompletableFuture.completedFuture(new Hover(
                new MarkupContent(MarkupKind.PLAINTEXT, String.join("\n\n", sections)),
                ProtosLspSourcePositions.range(
                        projected.snapshot().characters(),
                        projected.span())));
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
            CompletionParams params) {
        Objects.requireNonNull(params, "params");
        String uri = Objects.requireNonNull(
                Objects.requireNonNull(params.getTextDocument(), "textDocument").getUri(),
                "textDocument.uri");

        Predicate<String> authority = navigationSourceAuthority;
        if (!authority.test(uri)) {
            return noCompletion();
        }

        Optional<ProtosDocumentSnapshot> current =
                session.currentSnapshot(OPEN_DOCUMENTS_DOMAIN, uri);
        if (current.isEmpty()) {
            return noCompletion();
        }
        OptionalInt sourceOffset =
                ProtosLspSourcePositions.offset(
                        current.get().characters(),
                        Objects.requireNonNull(params.getPosition(), "position"));
        if (sourceOffset.isEmpty()) {
            return noCompletion();
        }

        Optional<ProtosStaticCompletionResult> completion =
                session.completionCurrent(
                        OPEN_DOCUMENTS_DOMAIN,
                        uri,
                        sourceOffset.getAsInt());
        if (completion.isEmpty()) {
            return noCompletion();
        }

        ProtosStaticCompletionResult projected = completion.get();
        if (!session.isCurrent(OPEN_DOCUMENTS_DOMAIN, projected)
                || !authority.test(uri)
                || !projected.snapshot().documentId().equals(uri)) {
            return noCompletion();
        }

        Range replacement = ProtosLspSourcePositions.range(
                projected.snapshot().characters(),
                projected.replacementSpan());
        List<CompletionItem> items = projected.candidates().stream()
                .map(candidate -> toLspCompletionItem(candidate, replacement))
                .toList();
        return CompletableFuture.completedFuture(
                Either.forRight(new CompletionList(false, items)));
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
        Objects.requireNonNull(params, "params");
        String uri = Objects.requireNonNull(
                Objects.requireNonNull(params.getTextDocument(), "textDocument").getUri(),
                "textDocument.uri");

        Predicate<String> authority = navigationSourceAuthority;
        if (!authority.test(uri)) {
            return noSignatureHelp();
        }

        Optional<ProtosDocumentSnapshot> current =
                session.currentSnapshot(OPEN_DOCUMENTS_DOMAIN, uri);
        if (current.isEmpty()) {
            return noSignatureHelp();
        }
        OptionalInt sourceOffset =
                ProtosLspSourcePositions.offset(
                        current.get().characters(),
                        Objects.requireNonNull(params.getPosition(), "position"));
        if (sourceOffset.isEmpty()) {
            return noSignatureHelp();
        }

        Optional<ProtosStaticSignatureHelpResult> signatureHelp =
                session.signatureHelpCurrent(
                        OPEN_DOCUMENTS_DOMAIN,
                        uri,
                        sourceOffset.getAsInt());
        if (signatureHelp.isEmpty()) {
            return noSignatureHelp();
        }

        ProtosStaticSignatureHelpResult projected = signatureHelp.get();
        if (!session.isCurrent(OPEN_DOCUMENTS_DOMAIN, projected)
                || !authority.test(uri)
                || !projected.snapshot().documentId().equals(uri)) {
            return noSignatureHelp();
        }
        return CompletableFuture.completedFuture(toLspSignatureHelp(projected));
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


    void setNavigationSourceAuthority(Predicate<String> authority) {
        navigationSourceAuthority =
                Objects.requireNonNull(authority, "authority");
    }

    private static CompletableFuture<
                    Either<List<? extends Location>, List<? extends LocationLink>>>
            noDefinition() {
        List<? extends Location> empty = List.of();
        return CompletableFuture.completedFuture(Either.forLeft(empty));
    }

    /**
     * An absent or unprovable result is a definitive, complete empty list;
     * {@code isIncomplete} never signals analysis failure.
     */
    private static CompletableFuture<Either<List<CompletionItem>, CompletionList>> noCompletion() {
        return CompletableFuture.completedFuture(
                Either.forRight(new CompletionList(false, List.of())));
    }

    private static CompletionItem toLspCompletionItem(
            ProtosStaticCompletionResult.Candidate candidate,
            Range replacement) {
        CompletionItem item = new CompletionItem(candidate.label());
        switch (candidate.kind()) {
            case PROVEN_BINDING -> {
                item.setKind(CompletionItemKind.Variable);
                item.setDetail("Proven binding: Closure parameter");
            }
            case SYNTAX -> {
                item.setKind(CompletionItemKind.Keyword);
                item.setDetail("Syntax: reserved word");
            }
        }
        item.setFilterText(candidate.label());
        item.setTextEdit(Either.forLeft(new TextEdit(replacement, candidate.label())));
        return item;
    }

    /**
     * Projects one signature with String parameter labels. A client treats
     * an omitted {@code activeParameter} as 0 when the signature has
     * parameters, and locates a String label by its first occurrence in the
     * signature label; when either would produce a false highlight, nothing
     * is published.
     */
    private static SignatureHelp toLspSignatureHelp(ProtosStaticSignatureHelpResult projected) {
        List<ProtosStaticSignatureHelpResult.Parameter> parameters = projected.parameters();
        if (!parameters.isEmpty() && projected.activeParameter().isEmpty()) {
            return null;
        }
        String label = projected.label();
        List<ParameterInformation> information = new ArrayList<>();
        int position = 1;
        for (ProtosStaticSignatureHelpResult.Parameter parameter : parameters) {
            if (label.indexOf(parameter.label()) != position) {
                return null;
            }
            information.add(new ParameterInformation(parameter.label()));
            position += parameter.label().length() + 2;
        }
        SignatureInformation signature = new SignatureInformation(label);
        signature.setParameters(information);
        Integer activeParameter = projected.activeParameter().isPresent()
                ? projected.activeParameter().getAsInt()
                : null;
        return new SignatureHelp(List.of(signature), 0, activeParameter);
    }

    private static CompletableFuture<SignatureHelp> noSignatureHelp() {
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<Hover> noHover() {
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<List<? extends Location>> noReferences() {
        List<? extends Location> empty = List.of();
        return CompletableFuture.completedFuture(empty);
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
        } else if (result instanceof ProtosStaticParseResult.Parsed success) {
            List<ProtosStaticLintDiagnostic> findings = ProtosStaticLint.check(success);
            if (!session.isCurrent(OPEN_DOCUMENTS_DOMAIN, result)) {
                return;
            }
            diagnostics = findings.stream()
                    .map(ProtosTextDocumentService::toLspDiagnostic)
                    .toList();
        } else {
            return;
        }

        publishDiagnostics(
                documentUri,
                Math.toIntExact(result.snapshot().version()),
                diagnostics,
                currentClient);
    }

    private static Diagnostic toLspDiagnostic(ProtosStaticLintDiagnostic finding) {
        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setRange(ProtosLspSourcePositions.range(
                finding.snapshot().characters(),
                finding.span()));
        diagnostic.setSeverity(switch (finding.severity()) {
            case WARNING -> DiagnosticSeverity.Warning;
        });
        diagnostic.setCode(finding.ruleId());
        diagnostic.setSource("protos");
        diagnostic.setMessage(finding.message());
        return diagnostic;
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

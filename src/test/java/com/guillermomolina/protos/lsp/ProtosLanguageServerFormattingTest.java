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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.analysis.ProtosDocumentSnapshot;
import com.guillermomolina.protos.execution.ProtosProjectFileBindingProvider;
import com.guillermomolina.protos.execution.ProtosWholeDocumentFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.jupiter.api.Test;

class ProtosLanguageServerFormattingTest {
    private static final String URI = "file:///workspace/missing-on-disk/format.protos";

    @Test
    void initializeAdvertisesWholeDocumentFormattingOnly() {
        InitializeResult result =
                new ProtosLanguageServer(code -> {}).initialize(new InitializeParams()).join();
        ServerCapabilities capabilities = result.getCapabilities();

        assertEquals(Boolean.TRUE, capabilities.getDocumentFormattingProvider().getLeft());
        assertNull(capabilities.getDocumentRangeFormattingProvider());
        assertNull(capabilities.getDocumentOnTypeFormattingProvider());
        assertEquals(Boolean.TRUE, capabilities.getDefinitionProvider().getLeft());
        assertEquals(Boolean.TRUE, capabilities.getReferencesProvider().getLeft());
        assertEquals(Boolean.TRUE, capabilities.getWorkspaceSymbolProvider().getLeft());
    }

    @Test
    void changedSourceYieldsOneFullDocumentEditOverTheOpenSnapshot() {
        List<ProtosDocumentSnapshot> seen = new ArrayList<>();
        ProtosLanguageServer server = server(snapshot -> {
            seen.add(snapshot);
            return success("value: 1\n");
        });
        server.textDocuments().didOpen(open(URI, 4, "value:1"));

        List<? extends TextEdit> edits = format(server, URI, options(4, true));

        assertEquals(List.of(new ProtosDocumentSnapshot(URI, 4, "value:1")), seen);
        assertEquals(1, edits.size());
        assertEquals(new Range(new Position(0, 0), new Position(0, 7)), edits.get(0).getRange());
        assertEquals("value: 1\n", edits.get(0).getNewText());
    }

    @Test
    void fullDocumentRangeEndsAtExactUtf16PositionOfMultilineSource() {
        String source = "a:1\r\nb: \"\u00e9\uD83D\uDE00\"\nc:3";
        ProtosLanguageServer server = server(snapshot -> success("formatted\n"));
        server.textDocuments().didOpen(open(URI, 1, source));

        List<? extends TextEdit> edits = format(server, URI, options(4, true));

        assertEquals(1, edits.size());
        assertEquals(new Position(0, 0), edits.get(0).getRange().getStart());
        assertEquals(
                ProtosLspSourcePositions.position(source, source.length()),
                edits.get(0).getRange().getEnd());
        assertEquals(new Position(2, 3), edits.get(0).getRange().getEnd());
    }

    @Test
    void unchangedFailedAndUnopenedDocumentsYieldNoEdits() {
        ProtosLanguageServer unchanged = server(snapshot -> success(snapshot.characters()));
        unchanged.textDocuments().didOpen(open(URI, 1, "value: 1\n"));
        assertEquals(List.of(), format(unchanged, URI, options(4, true)));

        ProtosLanguageServer failed = server(snapshot -> new ProtosWholeDocumentFormatter.Result(
                ProtosWholeDocumentFormatter.Status.FAILURE,
                snapshot.characters(),
                java.util.Optional.of("parse failure")));
        failed.textDocuments().didOpen(open(URI, 1, "foo("));
        assertEquals(List.of(), format(failed, URI, options(4, true)));

        ProtosLanguageServer unopened = server(snapshot -> {
            throw new AssertionError("unopened documents must not be formatted");
        });
        assertEquals(List.of(), format(unopened, URI, options(4, true)));
    }

    @Test
    void staleSnapshotResultIsNotReturned() {
        ProtosLanguageServer[] holder = new ProtosLanguageServer[1];
        holder[0] = server(snapshot -> {
            // The open document advances while the formatter is in flight.
            holder[0].textDocuments().didChange(change(URI, 2, "other:2"));
            return success("value: 1\n");
        });
        holder[0].textDocuments().didOpen(open(URI, 1, "value:1"));

        assertEquals(List.of(), format(holder[0], URI, options(4, true)));
        assertEquals(
                new ProtosDocumentSnapshot(URI, 2, "other:2"),
                holder[0].textDocuments().currentSnapshot(URI).orElseThrow());
    }

    @Test
    void internalFormatterFailureCompletesRequestExceptionally() {
        IllegalStateException failure = new IllegalStateException("formatter bootstrap failed");
        ProtosLanguageServer server = server(snapshot -> {
            throw failure;
        });
        server.textDocuments().didOpen(open(URI, 1, "value:1"));

        CompletionException thrown = assertThrows(
                CompletionException.class,
                () -> server.getTextDocumentService()
                        .formatting(params(URI, options(4, true)))
                        .join());
        assertSame(failure, thrown.getCause());
    }

    @Test
    void realToolchainFormatterFormatsOpenBufferAndIgnoresEditorOptions() {
        ProtosLanguageServer server = new ProtosLanguageServer(code -> {});
        server.textDocuments().didOpen(open(URI, 1, "value:1"));

        for (FormattingOptions options : List.of(options(4, true), options(8, false))) {
            List<? extends TextEdit> edits = format(server, URI, options);
            assertEquals(1, edits.size());
            assertEquals(
                    new Range(new Position(0, 0), new Position(0, 7)),
                    edits.get(0).getRange());
            assertEquals("value: 1\n", edits.get(0).getNewText());
        }

        server.textDocuments().didChange(change(URI, 2, "value: 1\n"));
        assertEquals(List.of(), format(server, URI, options(8, false)));

        server.textDocuments().didChange(change(URI, 3, "foo("));
        assertEquals(List.of(), format(server, URI, options(4, true)));
        assertEquals(
                "foo(",
                server.textDocuments().currentSnapshot(URI).orElseThrow().characters());
    }

    private static ProtosLanguageServer server(ProtosLspDocumentFormatter formatter) {
        return new ProtosLanguageServer(
                code -> {}, new ProtosProjectFileBindingProvider(), formatter);
    }

    private static ProtosWholeDocumentFormatter.Result success(String source) {
        return new ProtosWholeDocumentFormatter.Result(
                ProtosWholeDocumentFormatter.Status.SUCCESS,
                source,
                java.util.Optional.empty());
    }

    private static List<? extends TextEdit> format(
            ProtosLanguageServer server, String uri, FormattingOptions options) {
        List<? extends TextEdit> edits =
                server.getTextDocumentService().formatting(params(uri, options)).join();
        assertTrue(edits.size() <= 1);
        return edits;
    }

    private static DocumentFormattingParams params(String uri, FormattingOptions options) {
        return new DocumentFormattingParams(new TextDocumentIdentifier(uri), options);
    }

    private static FormattingOptions options(int tabSize, boolean insertSpaces) {
        return new FormattingOptions(tabSize, insertSpaces);
    }

    private static DidOpenTextDocumentParams open(String uri, int version, String text) {
        return new DidOpenTextDocumentParams(new TextDocumentItem(uri, "protos", version, text));
    }

    private static DidChangeTextDocumentParams change(String uri, int version, String text) {
        return new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(uri, version),
                List.of(new TextDocumentContentChangeEvent(text)));
    }
}

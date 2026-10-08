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

package com.guillermomolina.protos.analysis;

import com.guillermomolina.protos.lexer.ProtosLexer;
import com.guillermomolina.protos.parser.ParseError;
import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Editor-neutral static source-analysis entry point.
 *
 * <p>The initial LM009-F1 surface deliberately owns no workspace state, LSP
 * serialization, Truffle Context, guest execution, or module-resolution policy.
 * It reuses the real Protos parser directly.</p>
 */
public final class ProtosStaticAnalysisCore {

    public ProtosStaticParseResult parse(ProtosDocumentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        try {
            return new ProtosStaticParseResult.Parsed(
                    snapshot,
                    new ProtosParser(snapshot.characters()).parseProgram());
        } catch (ParseError error) {
            return new ProtosStaticParseResult.Failed(
                    snapshot,
                    error.getMessage(),
                    error.span(),
                    error.isUnexpectedEndOfSource());
        } catch (ProtosLexer.LexicalError error) {
            // An ordinary lexical error in edited source is a document failure,
            // not a server fault. The lexer reports one exact offset and no
            // extent, so the span is that empty point; an error without a
            // reported offset is not projected onto an invented position.
            OptionalInt offset = error.offset();
            if (offset.isEmpty() || offset.getAsInt() > snapshot.characters().length()) {
                throw error;
            }
            return new ProtosStaticParseResult.Failed(
                    snapshot,
                    error.getMessage(),
                    new SourceSpan(offset.getAsInt(), offset.getAsInt()),
                    false);
        }
    }

    /**
     * Builds source-layout metadata only on explicit tooling demand.
     *
     * <p>The ordinary static parse path remains unchanged and retains no
     * trivia or parser source facts.</p>
     */
    public ProtosSourceLayoutView sourceLayout(
            ProtosDocumentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return ProtosSourceLayoutView.create(snapshot);
    }

    /**
     * Resolves the complete generation-1 D110 definition proof for one source
     * offset in the supplied immutable snapshot.
     *
     * <p>Parse failure, a non-reference offset, or an unproven definition is an
     * ordinary empty result.</p>
     */
    public Optional<ProtosStaticDefinitionResult> definition(
            ProtosDocumentSnapshot snapshot,
            int sourceOffset) {
        Objects.requireNonNull(snapshot, "snapshot");

        ProtosStaticParseResult parsed = parse(snapshot);
        if (!(parsed instanceof ProtosStaticParseResult.Parsed successful)) {
            return Optional.empty();
        }
        return ProtosStaticDefinitions.resolve(successful, sourceOffset);
    }

    /**
     * Projects the LM010-A static hover for one source offset in the supplied
     * immutable snapshot.
     *
     * <p>Proven-binding facts reuse the D110 generation-1 definition proof;
     * syntax facts come only from the parsed surface AST. Parse failure or an
     * offset without verifiable information is an ordinary empty result.</p>
     */
    public Optional<ProtosStaticHoverResult> hover(
            ProtosDocumentSnapshot snapshot,
            int sourceOffset) {
        Objects.requireNonNull(snapshot, "snapshot");

        ProtosStaticParseResult parsed = parse(snapshot);
        if (!(parsed instanceof ProtosStaticParseResult.Parsed successful)) {
            return Optional.empty();
        }
        return ProtosStaticHover.resolve(successful, sourceOffset);
    }

    /**
     * Projects the LM010-B static completion for one cursor offset in the
     * supplied immutable snapshot.
     *
     * <p>{@code sourceOffset} may equal the snapshot length. Proven candidates
     * reuse the D110 generation-1 facts at the read site; reserved-word
     * candidates are accepted only by the real parser at that site. A position
     * that cannot be classified with certainty is an ordinary empty result.</p>
     */
    public Optional<ProtosStaticCompletionResult> completion(
            ProtosDocumentSnapshot snapshot,
            int sourceOffset) {
        Objects.requireNonNull(snapshot, "snapshot");
        return ProtosStaticCompletion.complete(snapshot, sourceOffset);
    }

    /**
     * Resolves the D124 generation-1 references relation for one exact seed
     * position in the supplied immutable snapshot.
     *
     * <p>The result is sound but deliberately not a global-completeness claim.
     * Parse failure, an ambiguous/unproven seed, or an unsupported source form
     * is an ordinary empty result.</p>
     */
    public Optional<ProtosStaticReferenceResult> references(
            ProtosDocumentSnapshot snapshot,
            int sourceOffset) {
        Objects.requireNonNull(snapshot, "snapshot");

        ProtosStaticParseResult parsed = parse(snapshot);
        if (!(parsed instanceof ProtosStaticParseResult.Parsed successful)) {
            return Optional.empty();
        }
        return ProtosStaticReferences.resolve(successful, sourceOffset);
    }

    /**
     * Runs the D194 initial correctness lint rules over the supplied immutable
     * snapshot.
     *
     * <p>Parse failure fails closed with no lint findings; parser diagnostics
     * remain the only report for an unparseable snapshot.</p>
     */
    public List<ProtosStaticLintDiagnostic> lint(ProtosDocumentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        ProtosStaticParseResult parsed = parse(snapshot);
        if (!(parsed instanceof ProtosStaticParseResult.Parsed successful)) {
            return List.of();
        }
        return ProtosStaticLint.check(successful);
    }
}

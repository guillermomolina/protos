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
package com.guillermomolina.protos.cli;

import com.guillermomolina.protos.analysis.ProtosDocumentSnapshot;
import com.guillermomolina.protos.analysis.ProtosStaticAnalysisCore;
import com.guillermomolina.protos.analysis.ProtosStaticCompletionResult;
import com.guillermomolina.protos.analysis.ProtosStaticMemberCompletionSite;
import com.guillermomolina.protos.lexer.ProtosLexer;
import com.guillermomolina.protos.lexer.TokenOccurrence;
import com.guillermomolina.protos.lexer.TokenType;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * D227-A1/D228-A1: pure REPL source-candidate composition.
 *
 * <p>Only an existing LM010 parser-proven bare read accepts live lexical
 * names, and only a D228-A1 parser-proven selector site accepts member
 * names. This class never reads the session, evaluates guest source,
 * guesses names or launches a language server. Each runtime provider is
 * invoked lazily, at most once, and only after its own site is proven.</p>
 */
final class ProtosReplSourceCompletion {
    private static final int MAX_SOURCE_CHARS = 65536;
    private static final int MAX_CANDIDATES = 256;

    enum Kind {
        LIVE_BINDING,
        PROVEN_BINDING,
        SYNTAX,
        MEMBER_OWN,
        MEMBER_DELEGATED
    }

    /** One inert selector name observed for a proven member site. */
    record MemberName(String name, boolean own) {
        MemberName {
            Objects.requireNonNull(name, "name");
        }
    }

    record Candidate(String label, Kind kind) {
        Candidate {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(kind, "kind");
        }
    }

    record Result(int replaceStart, int replaceEnd,
                  List<Candidate> candidates) {
        Result {
            candidates = List.copyOf(candidates);
            if (replaceStart < 0 || replaceEnd < replaceStart
                    || candidates.isEmpty()) {
                throw new IllegalArgumentException(
                        "invalid source completion result");
            }
        }
    }

    private ProtosReplSourceCompletion() {}

    /** D227-A1 composition without an approved selector provider. */
    static Optional<Result> complete(
            String pending,
            String line,
            int cursor,
            List<String> liveNames) {
        Objects.requireNonNull(liveNames, "liveNames");
        return complete(pending, line, cursor, () -> liveNames, null);
    }

    /**
     * @param memberNames D228-A1 selector provider, or {@code null} when no
     *     selector provider is approved; a proven member site then yields no
     *     candidates
     */
    static Optional<Result> complete(
            String pending,
            String line,
            int cursor,
            Supplier<List<String>> liveNames,
            Function<ProtosStaticMemberCompletionSite, List<MemberName>>
                    memberNames) {
        Objects.requireNonNull(pending, "pending");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(liveNames, "liveNames");

        if (cursor < 0 || cursor > line.length()
                || line.startsWith(":")) {
            return Optional.empty();
        }

        int prefixLength = pending.isEmpty() ? 0 : pending.length() + 1;
        if ((long) prefixLength + line.length() > MAX_SOURCE_CHARS) {
            return Optional.empty();
        }

        String source = pending.isEmpty() ? line : pending + "\n" + line;
        int offset = prefixLength + cursor;
        if (offset > 0 && offset < source.length()
                && Character.isHighSurrogate(source.charAt(offset - 1))
                && Character.isLowSurrogate(source.charAt(offset))) {
            return Optional.empty();
        }

        var snapshot = new ProtosDocumentSnapshot(
                "memory:/repl-completion.protos", 0, source);
        var analysis = new ProtosStaticAnalysisCore();

        Optional<ProtosStaticMemberCompletionSite> memberSite =
                analysis.memberCompletionSite(snapshot, offset);
        if (memberSite.isPresent()) {
            return memberNames == null
                    ? Optional.empty()
                    : completeMember(
                            source, prefixLength, offset,
                            memberSite.orElseThrow(), memberNames);
        }

        Optional<ProtosStaticCompletionResult> proven =
                analysis.completion(snapshot, offset);

        // LM010 intentionally rejects a cursor strictly inside a token.
        // To support in-word editing, locate the real identifier with the
        // lexer, then ask LM010 to prove the read at its exact token end.
        if (proven.isEmpty()) {
            try {
                for (TokenOccurrence token :
                        new ProtosLexer(source).tokenizeOccurrences()) {
                    SourceSpan span = token.span();
                    if (token.token().type() == TokenType.IDENTIFIER
                            && span.startOffset() < offset
                            && offset < span.endOffset()
                            && span.startOffset() >= prefixLength) {
                        var atEnd = analysis.completion(
                                snapshot, span.endOffset());
                        if (atEnd.isPresent()
                                && atEnd.orElseThrow()
                                        .replacementSpan().equals(span)) {
                            proven = atEnd;
                        }
                        break;
                    }
                }
            } catch (ProtosLexer.LexicalError invalid) {
                return Optional.empty();
            }
        }

        if (proven.isEmpty()) {
            return Optional.empty();
        }

        ProtosStaticCompletionResult fact = proven.orElseThrow();
        SourceSpan span = fact.replacementSpan();

        if (span.startOffset() < prefixLength
                || span.startOffset() > offset
                || span.endOffset() < offset
                || span.endOffset() > source.length()) {
            return Optional.empty();
        }

        String before = source.substring(span.startOffset(), offset);
        String after = source.substring(offset, span.endOffset());

        LinkedHashMap<String, Candidate> unique = new LinkedHashMap<>();

        for (String name : liveNames.get()) {
            if (unique.size() >= MAX_CANDIDATES) {
                break;
            }
            if (matches(name, before, after)
                    && isIdentifier(name)) {
                unique.putIfAbsent(
                        name, new Candidate(name, Kind.LIVE_BINDING));
            }
        }

        for (var candidate : fact.candidates()) {
            if (unique.size() >= MAX_CANDIDATES) {
                break;
            }
            String label = candidate.label();
            if (!matches(label, before, after)) {
                continue;
            }
            Kind kind = switch (candidate.kind()) {
                case PROVEN_BINDING -> Kind.PROVEN_BINDING;
                case SYNTAX -> Kind.SYNTAX;
            };
            unique.putIfAbsent(label, new Candidate(label, kind));
        }

        if (unique.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new Result(
                span.startOffset() - prefixLength,
                span.endOffset() - prefixLength,
                new ArrayList<>(unique.values())));
    }

    private static Optional<Result> completeMember(
            String source,
            int prefixLength,
            int offset,
            ProtosStaticMemberCompletionSite site,
            Function<ProtosStaticMemberCompletionSite, List<MemberName>>
                    memberNames) {
        SourceSpan span = site.replacementSpan();
        if (span.startOffset() < prefixLength
                || span.startOffset() > offset
                || span.endOffset() < offset
                || span.endOffset() > source.length()) {
            return Optional.empty();
        }

        String before = source.substring(span.startOffset(), offset);
        String after = source.substring(offset, span.endOffset());

        // The provider's order (nearest level first, scalar order within a
        // level) is kept; the presentation limit applies only afterwards.
        LinkedHashMap<String, Candidate> unique = new LinkedHashMap<>();
        for (MemberName member : memberNames.apply(site)) {
            if (unique.size() >= MAX_CANDIDATES) {
                break;
            }
            String name = member.name();
            if (matches(name, before, after) && isMemberName(name)) {
                unique.putIfAbsent(name, new Candidate(
                        name,
                        member.own() ? Kind.MEMBER_OWN : Kind.MEMBER_DELEGATED));
            }
        }

        if (unique.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Result(
                span.startOffset() - prefixLength,
                span.endOffset() - prefixLength,
                new ArrayList<>(unique.values())));
    }

    private static boolean matches(
            String name, String before, String after) {
        return name != null
                && name.length() >= before.length() + after.length()
                && name.startsWith(before)
                && name.endsWith(after);
    }

    private static boolean isIdentifier(String name) {
        return isSingleToken(name, false);
    }

    /** A structural member name: an identifier or a reserved word valid after {@code .}. */
    private static boolean isMemberName(String name) {
        return isSingleToken(name, true);
    }

    private static boolean isSingleToken(String name, boolean memberName) {
        try {
            List<TokenOccurrence> tokens =
                    new ProtosLexer(name).tokenizeOccurrences();
            if (tokens.size() != 2) {
                return false;
            }
            TokenType type = tokens.get(0).token().type();
            boolean admitted = memberName
                    ? switch (type) {
                        case IDENTIFIER, THIS, CONTEXT, SUPER,
                                TRUE, FALSE, NULL -> true;
                        default -> false;
                    }
                    : type == TokenType.IDENTIFIER;
            return admitted
                    && tokens.get(0).span().startOffset() == 0
                    && tokens.get(0).span().endOffset() == name.length()
                    && tokens.get(1).token().type() == TokenType.EOF;
        } catch (ProtosLexer.LexicalError invalid) {
            return false;
        }
    }
}

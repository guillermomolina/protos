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

import com.guillermomolina.protos.analysis.ProtosStaticMemberCompletionSite;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.Objects;
import org.jline.reader.Candidate;
import org.jline.reader.CompletingParsedLine;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

/**
 * JLine projection of REPL completion.
 *
 * <p>Meta-commands stay a separate context. For source, every syntactic
 * decision belongs to {@link ProtosReplSourceCompletion}; this adapter only
 * checks that JLine can replace the proven span within its word, transports
 * candidates and never evaluates guest code. The D227 lexical-name provider and
 * the D228-A1 selector provider are separate and lazy.</p>
 */
final class ProtosReplJLineCompleter implements Completer {
    private final Supplier<String> pendingSource;
    private final Supplier<List<String>> liveBindingNames;
    private final Function<ProtosStaticMemberCompletionSite,
            List<ProtosReplSourceCompletion.MemberName>> selectorNames;

    // Retains the batch-3 commands-only adapter contract.
    ProtosReplJLineCompleter() {
        pendingSource = null;
        liveBindingNames = null;
        selectorNames = null;
    }

    // Retains the D227-A1 contract: no approved selector provider.
    ProtosReplJLineCompleter(
            Supplier<String> pendingSource,
            Supplier<List<String>> liveBindingNames) {
        this.pendingSource =
                Objects.requireNonNull(pendingSource, "pendingSource");
        this.liveBindingNames =
                Objects.requireNonNull(liveBindingNames, "liveBindingNames");
        this.selectorNames = null;
    }

    ProtosReplJLineCompleter(
            Supplier<String> pendingSource,
            Supplier<List<String>> liveBindingNames,
            Function<ProtosStaticMemberCompletionSite,
                    List<ProtosReplSourceCompletion.MemberName>> selectorNames) {
        this.pendingSource =
                Objects.requireNonNull(pendingSource, "pendingSource");
        this.liveBindingNames =
                Objects.requireNonNull(liveBindingNames, "liveBindingNames");
        this.selectorNames =
                Objects.requireNonNull(selectorNames, "selectorNames");
    }

    @Override
    public void complete(
            LineReader reader,
            ParsedLine line,
            List<Candidate> candidates) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(candidates, "candidates");

        if (line.line().startsWith(":")) {
            // Preserve the exact metacommand behavior of batches 1-3.
            if (!line.line().equals(line.word())) {
                return;
            }
            for (String command : ProtosReplCompletion.commandCandidates(
                    line.line(), line.cursor())) {
                candidates.add(new Candidate(
                        command, command, null, null, null, null, false));
            }
            return;
        }

        if (pendingSource == null
                || !(line instanceof CompletingParsedLine parsed)) {
            return;
        }

        // JLine owns the edited word, while LM010 owns the Protos source
        // replacement span. Never assume both boundaries coincide.
        int cursor = line.cursor();
        int wordStart = cursor - parsed.rawWordCursor();
        int wordEnd = wordStart + parsed.rawWordLength();

        if (wordStart < 0 || wordEnd > line.line().length()
                || wordStart > cursor || cursor > wordEnd
                || !line.line().substring(wordStart, wordEnd)
                        .equals(line.word())) {
            return;
        }

        try {
            var completion = ProtosReplSourceCompletion.complete(
                    pendingSource.get(),
                    line.line(),
                    cursor,
                    liveBindingNames,
                    selectorNames);
            if (completion.isEmpty()) {
                return;
            }

            var result = completion.orElseThrow();
            if (result.replaceStart() < wordStart
                    || result.replaceEnd() > wordEnd) {
                // A JLine word cannot safely replace this Protos span.
                return;
            }

            String leading = line.line().substring(
                    wordStart, result.replaceStart());
            String trailing = line.line().substring(
                    result.replaceEnd(), wordEnd);

            for (var candidate : result.candidates()) {
                String value = leading + candidate.label() + trailing;
                String description = switch (candidate.kind()) {
                    case LIVE_BINDING -> "session binding";
                    case PROVEN_BINDING -> "proven local";
                    case SYNTAX -> "Protos syntax";
                    case MEMBER_OWN -> "own slot";
                    case MEMBER_DELEGATED -> "delegated slot";
                };

                // complete=false: do not append whitespace after TAB.
                candidates.add(new Candidate(
                        value,
                        candidate.label(),
                        null,
                        description,
                        null,
                        null,
                        false));
            }
        } catch (RuntimeException unavailable) {
            // Failed lexical observation or unavailable source authority:
            // no candidates, no fallback to dynamic guest inspection.
        }
    }
}

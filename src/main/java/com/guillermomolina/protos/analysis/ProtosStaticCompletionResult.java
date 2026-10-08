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

import com.guillermomolina.protos.source.SourceSpan;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Protocol-neutral LM010-B completion projection for one exact snapshot.
 *
 * <p>{@code replacementSpan} covers exactly the identifier prefix already
 * typed before the cursor, or is empty at the cursor. Candidates are in a
 * deterministic order with unique labels. {@link Kind#PROVEN_BINDING}
 * candidates come only from D110 generation-1 proofs at the completion read
 * site; {@link Kind#SYNTAX} candidates are reserved-word expressions the real
 * parser accepts at that site and never claim a dynamic resolution. No types,
 * values, ranking, or member identities are represented.</p>
 */
public record ProtosStaticCompletionResult(
        ProtosDocumentSnapshot snapshot,
        SourceSpan replacementSpan,
        List<Candidate> candidates) {
    public ProtosStaticCompletionResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(replacementSpan, "replacementSpan");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("completion result must contain at least one candidate");
        }
        Set<String> labels = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (!labels.add(candidate.label())) {
                throw new IllegalArgumentException("duplicate completion candidate: " + candidate.label());
            }
        }
    }

    public enum Kind {
        PROVEN_BINDING,
        SYNTAX
    }

    public record Candidate(String label, Kind kind) {
        public Candidate {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(kind, "kind");
        }
    }
}

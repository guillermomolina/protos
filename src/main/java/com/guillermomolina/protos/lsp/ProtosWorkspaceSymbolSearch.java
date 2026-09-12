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

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Normalizer2;
import com.guillermomolina.protos.source.SourceSpan;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/** D106 C-prime matching, ranking, deterministic ordering and global result cap. */
final class ProtosWorkspaceSymbolSearch {
    static final int RESULT_LIMIT = 100;

    private static final Normalizer2 NFC = Normalizer2.getNFCInstance();
    private static final Comparator<Match> MATCH_ORDER = ProtosWorkspaceSymbolSearch::compare;

    private ProtosWorkspaceSymbolSearch() {}

    static List<Candidate> search(String query, Collection<Candidate> candidates) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(candidates, "candidates");
        if (query.isEmpty()) {
            return List.of();
        }

        String queryNfc = NFC.normalize(query);
        String queryFolded = fold(queryNfc);
        int[] queryFoldedCodePoints = queryFolded.codePoints().toArray();
        if (queryFoldedCodePoints.length == 0) {
            return List.of();
        }

        PriorityQueue<Match> top =
                new PriorityQueue<>(RESULT_LIMIT, MATCH_ORDER.reversed());
        for (Candidate candidate : candidates) {
            Match match = match(queryNfc, queryFolded, queryFoldedCodePoints, candidate);
            if (match == null) {
                continue;
            }
            if (top.size() < RESULT_LIMIT) {
                top.add(match);
            } else if (MATCH_ORDER.compare(match, top.peek()) < 0) {
                top.poll();
                top.add(match);
            }
        }

        ArrayList<Match> ordered = new ArrayList<>(top);
        ordered.sort(MATCH_ORDER);
        return ordered.stream().map(Match::candidate).toList();
    }

    private static Match match(
            String queryNfc,
            String queryFolded,
            int[] queryFoldedCodePoints,
            Candidate candidate) {
        String candidateNfc = NFC.normalize(candidate.name());
        String candidateFolded = fold(candidateNfc);
        int[] candidateFoldedCodePoints = candidateFolded.codePoints().toArray();
        if (!isSubsequence(queryFoldedCodePoints, candidateFoldedCodePoints)) {
            return null;
        }

        Rank rank = rank(queryFolded, candidateFolded);
        boolean originalRelation = relation(rank, queryNfc, candidateNfc);
        return new Match(
                candidate,
                rank,
                originalRelation,
                candidate.name().codePointCount(0, candidate.name().length()),
                candidate.name().getBytes(StandardCharsets.UTF_8),
                candidate.projectRoot().toUri().toASCIIString().getBytes(StandardCharsets.UTF_8),
                candidate.packageId().getBytes(StandardCharsets.UTF_8),
                candidate.logicalModule().getBytes(StandardCharsets.UTF_8));
    }

    private static String fold(String nfc) {
        return NFC.normalize(UCharacter.foldCase(nfc, true));
    }

    private static Rank rank(String query, String candidate) {
        if (candidate.equals(query)) {
            return Rank.EXACT;
        }
        if (candidate.startsWith(query)) {
            return Rank.PREFIX;
        }
        if (candidate.contains(query)) {
            return Rank.SUBSTRING;
        }
        return Rank.SUBSEQUENCE;
    }

    private static boolean relation(Rank rank, String query, String candidate) {
        return switch (rank) {
            case EXACT -> candidate.equals(query);
            case PREFIX -> candidate.startsWith(query);
            case SUBSTRING -> candidate.contains(query);
            case SUBSEQUENCE ->
                    isSubsequence(query.codePoints().toArray(), candidate.codePoints().toArray());
        };
    }

    private static boolean isSubsequence(int[] query, int[] candidate) {
        int queryIndex = 0;
        for (int codePoint : candidate) {
            if (queryIndex < query.length && query[queryIndex] == codePoint) {
                queryIndex++;
            }
        }
        return queryIndex == query.length;
    }

    private static int compare(Match left, Match right) {
        int compared = Integer.compare(left.rank().ordinal(), right.rank().ordinal());
        if (compared != 0) {
            return compared;
        }
        compared = Boolean.compare(right.originalRelation(), left.originalRelation());
        if (compared != 0) {
            return compared;
        }
        compared = Integer.compare(left.nameCodePoints(), right.nameCodePoints());
        if (compared != 0) {
            return compared;
        }
        compared = compareUnsignedUtf8(left.nameKey(), right.nameKey());
        if (compared != 0) {
            return compared;
        }
        compared = compareUnsignedUtf8(left.projectKey(), right.projectKey());
        if (compared != 0) {
            return compared;
        }
        compared = compareUnsignedUtf8(left.packageKey(), right.packageKey());
        if (compared != 0) {
            return compared;
        }
        compared = compareUnsignedUtf8(left.moduleKey(), right.moduleKey());
        if (compared != 0) {
            return compared;
        }
        compared = Integer.compare(
                left.candidate().selectionRange().startOffset(),
                right.candidate().selectionRange().startOffset());
        if (compared != 0) {
            return compared;
        }
        return Integer.compare(
                left.candidate().selectionRange().endOffset(),
                right.candidate().selectionRange().endOffset());
    }

    private static int compareUnsignedUtf8(byte[] left, byte[] right) {
        int length = Math.min(left.length, right.length);
        for (int index = 0; index < length; index++) {
            int compared = Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    record Candidate(
            String name,
            Path projectRoot,
            String packageId,
            String logicalModule,
            Path source,
            String sourceUri,
            String sourceCharacters,
            SourceSpan selectionRange) {
        Candidate {
            name = requireText(name, "name");
            projectRoot = requireAbsoluteNormalized(projectRoot, "projectRoot");
            packageId = requireText(packageId, "packageId");
            logicalModule = requireText(logicalModule, "logicalModule");
            source = requireAbsoluteNormalized(source, "source");
            sourceUri = requireText(sourceUri, "sourceUri");
            sourceCharacters = Objects.requireNonNull(sourceCharacters, "sourceCharacters");
            selectionRange = Objects.requireNonNull(selectionRange, "selectionRange");
            if (selectionRange.endOffset() > sourceCharacters.length()) {
                throw new IllegalArgumentException("selectionRange exceeds sourceCharacters");
            }
        }
    }

    private record Match(
            Candidate candidate,
            Rank rank,
            boolean originalRelation,
            int nameCodePoints,
            byte[] nameKey,
            byte[] projectKey,
            byte[] packageKey,
            byte[] moduleKey) {}

    private enum Rank {
        EXACT,
        PREFIX,
        SUBSTRING,
        SUBSEQUENCE
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }

    private static Path requireAbsoluteNormalized(Path value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.isAbsolute() || !value.equals(value.normalize())) {
            throw new IllegalArgumentException(name + " must be absolute and normalized");
        }
        return value;
    }
}

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

import com.guillermomolina.protos.parser.ast.SurfaceArgument;
import com.guillermomolina.protos.parser.ast.SurfaceAssignment;
import com.guillermomolina.protos.parser.ast.SurfaceArrayConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceMapConstruction;
import com.guillermomolina.protos.parser.ast.SurfaceBinary;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceIndex;
import com.guillermomolina.protos.parser.ast.SurfaceIntrinsic;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceNonLocalReturn;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.parser.ast.SurfaceObjectItem;
import com.guillermomolina.protos.parser.ast.SurfaceParameter;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceMultipleSlotCreation;
import com.guillermomolina.protos.parser.ast.SurfaceSlotCreation;
import com.guillermomolina.protos.parser.ast.SurfaceSuperSend;
import com.guillermomolina.protos.parser.ast.SurfaceUnary;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * D124 generation-1 inverse reference query over the existing D110 proof authority.
 *
 * <p>This class does not reproduce D110 lexical/effect rules. It collects only
 * structural source candidates, then asks {@link ProtosStaticDefinitions} to prove
 * each candidate. Same-name filtering is therefore only a cost filter and never
 * semantic authority.</p>
 */
final class ProtosStaticReferences {
    private ProtosStaticReferences() {
    }

    static Optional<ProtosStaticReferenceResult> resolve(
            ProtosStaticParseResult.Parsed parsed,
            int sourceOffset) {
        Objects.requireNonNull(parsed, "parsed");
        ProtosDocumentSnapshot snapshot = parsed.snapshot();
        if (sourceOffset < 0 || sourceOffset >= snapshot.characters().length()) {
            return Optional.empty();
        }

        CandidateCollector candidates = new CandidateCollector(snapshot);
        candidates.collect(parsed.program());

        Optional<Seed> seed = candidates.seedAt(parsed, sourceOffset);
        if (seed.isEmpty()) {
            return Optional.empty();
        }

        Seed exactSeed = seed.get();
        ArrayList<ProtosStaticReferenceResult.Occurrence> references = new ArrayList<>();
        for (OccurrenceCandidate candidate : candidates.occurrences()) {
            if (!candidate.name().equals(exactSeed.name())) {
                continue;
            }
            Optional<ProtosStaticDefinitionResult> proof =
                    ProtosStaticDefinitions.resolve(parsed, candidate.span().startOffset());
            if (proof.isEmpty()
                    || !proof.get().referenceSpan().equals(candidate.span())
                    || proof.get().targets().stream().noneMatch(exactSeed.target()::equals)) {
                continue;
            }
            references.add(new ProtosStaticReferenceResult.Occurrence(
                    snapshot,
                    candidate.span()));
        }

        references.sort(Comparator
                .comparing((ProtosStaticReferenceResult.Occurrence occurrence) ->
                        occurrence.snapshot().documentId())
                .thenComparingInt(occurrence -> occurrence.span().startOffset())
                .thenComparingInt(occurrence -> occurrence.span().endOffset()));

        return Optional.of(new ProtosStaticReferenceResult(
                snapshot,
                exactSeed.target(),
                references));
    }

    private static boolean contains(SourceSpan span, int offset) {
        return span.startOffset() <= offset && offset < span.endOffset();
    }

    private static final class CandidateCollector {
        private final ProtosDocumentSnapshot snapshot;
        private final String source;
        private final ArrayList<DeclarationCandidate> declarations = new ArrayList<>();
        private final ArrayList<OccurrenceCandidate> occurrences = new ArrayList<>();

        CandidateCollector(ProtosDocumentSnapshot snapshot) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            source = snapshot.characters();
        }

        List<OccurrenceCandidate> occurrences() {
            return List.copyOf(occurrences);
        }

        Optional<Seed> seedAt(
                ProtosStaticParseResult.Parsed parsed,
                int sourceOffset) {
            List<DeclarationCandidate> declarationMatches = declarations.stream()
                    .filter(candidate -> contains(candidate.selectionSpan(), sourceOffset))
                    .toList();
            if (declarationMatches.size() == 1) {
                DeclarationCandidate declaration = declarationMatches.get(0);
                return Optional.of(new Seed(
                        declaration.name(),
                        new ProtosStaticDefinitionResult.Target(
                                snapshot,
                                declaration.originSpan())));
            }
            if (!declarationMatches.isEmpty()) {
                return Optional.empty();
            }

            List<OccurrenceCandidate> occurrenceMatches = occurrences.stream()
                    .filter(candidate -> contains(candidate.span(), sourceOffset))
                    .toList();
            if (occurrenceMatches.size() != 1) {
                return Optional.empty();
            }

            OccurrenceCandidate occurrence = occurrenceMatches.get(0);
            Optional<ProtosStaticDefinitionResult> proof =
                    ProtosStaticDefinitions.resolve(parsed, sourceOffset);
            if (proof.isEmpty()
                    || !proof.get().referenceSpan().equals(occurrence.span())
                    || proof.get().targets().size() != 1) {
                return Optional.empty();
            }

            ProtosStaticDefinitionResult.Target target = proof.get().targets().get(0);
            if (!target.snapshot().equals(snapshot)) {
                return Optional.empty();
            }
            return Optional.of(new Seed(occurrence.name(), target));
        }

        void collect(SurfaceExpression expression) {
            switch (expression) {
                case SurfaceLiteral ignored -> {
                }
                case SurfaceName name ->
                        occurrences.add(new OccurrenceCandidate(name.name(), name.span()));
                case SurfaceIntrinsic ignored -> {
                }
                case SurfaceSequence sequence ->
                        sequence.expressions().forEach(this::collect);
                case SurfaceGroup group ->
                        collect(group.expression());
                case SurfaceMember member ->
                        collect(member.receiver());
                case SurfaceCall call -> {
                    collect(call.receiver());
                    collectArguments(call.arguments());
                }
                case SurfaceArrayConstruction array ->
                        collectArguments(array.arguments());
                case SurfaceMapConstruction map -> {
                    for (SurfaceMapConstruction.Entry entry : map.entries()) {
                        collect(entry.key());
                        collect(entry.value());
                    }
                }
                case SurfaceIndex index -> {
                    collect(index.receiver());
                    collect(index.index());
                }
                case SurfaceUnary unary ->
                        collect(unary.operand());
                case SurfaceBinary binary -> {
                    collect(binary.left());
                    collect(binary.right());
                }
                case SurfaceNonLocalReturn nonLocalReturn ->
                        collect(nonLocalReturn.expression());
                case SurfaceSlotCreation creation -> {
                    collect(creation.target());
                    collect(creation.value());
                }
                case SurfaceMultipleSlotCreation creation -> {
                    creation.targets().forEach(this::collect);
                    collect(creation.value());
                }
                case SurfaceAssignment assignment -> {
                    collect(assignment.target());
                    collect(assignment.value());
                }
                case SurfaceSuperSend superSend ->
                        collectArguments(superSend.arguments());
                case SurfaceObject object ->
                        collectObject(object);
                case SurfaceClosure closure ->
                        collectClosure(closure);
            }
        }

        private void collectArguments(List<SurfaceArgument> arguments) {
            for (SurfaceArgument argument : arguments) {
                collect(argument.expression());
            }
        }

        private void collectObject(SurfaceObject object) {
            object.parent().ifPresent(this::collect);
            for (SurfaceObjectItem item : object.items()) {
                collect(item.expression());
            }
        }

        private void collectClosure(SurfaceClosure closure) {
            for (SurfaceParameter parameter : closure.parameters()) {
                addDeclaration(parameter.name(), parameter.span());
                parameter.defaultValue().ifPresent(this::collect);
            }
            collect(closure.body());
        }

        private void addDeclaration(String name, SourceSpan originSpan) {
            selectionSpan(name, originSpan).ifPresent(selection ->
                    declarations.add(new DeclarationCandidate(name, originSpan, selection)));
        }

        private Optional<SourceSpan> selectionSpan(String name, SourceSpan originSpan) {
            if (originSpan.startOffset() < 0
                    || originSpan.endOffset() > source.length()
                    || originSpan.startOffset() >= originSpan.endOffset()) {
                return Optional.empty();
            }

            String fragment = source.substring(
                    originSpan.startOffset(),
                    originSpan.endOffset());
            int relative = fragment.indexOf(name);
            if (relative < 0) {
                return Optional.empty();
            }
            int start = originSpan.startOffset() + relative;
            return Optional.of(new SourceSpan(start, start + name.length()));
        }
    }

    private record DeclarationCandidate(
            String name,
            SourceSpan originSpan,
            SourceSpan selectionSpan) {
        DeclarationCandidate {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(originSpan, "originSpan");
            Objects.requireNonNull(selectionSpan, "selectionSpan");
        }
    }

    private record OccurrenceCandidate(String name, SourceSpan span) {
        OccurrenceCandidate {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(span, "span");
        }
    }

    private record Seed(
            String name,
            ProtosStaticDefinitionResult.Target target) {
        Seed {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(target, "target");
        }
    }
}

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
import com.guillermomolina.protos.parser.ast.SurfaceBinary;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceClosure;
import com.guillermomolina.protos.parser.ast.SurfaceExpression;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceIndex;
import com.guillermomolina.protos.parser.ast.SurfaceIntrinsic;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceMatch;
import com.guillermomolina.protos.parser.ast.SurfaceMatchPattern;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceNonLocalReturn;
import com.guillermomolina.protos.parser.ast.SurfaceObject;
import com.guillermomolina.protos.parser.ast.SurfaceObjectItem;
import com.guillermomolina.protos.parser.ast.SurfaceParameter;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.parser.ast.SurfaceSlotCreation;
import com.guillermomolina.protos.parser.ast.SurfaceSuperSend;
import com.guillermomolina.protos.parser.ast.SurfaceUnary;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * D110 generation-1 exact definition proof over one parser-authoritative snapshot.
 *
 * <p>This resolver deliberately knows only source origins that can be proved from
 * the current activation: Closure parameters and singleton-proven match Binder/Alias
 * bindings. It never falls back to same-name, workspace-symbol, receiver, member,
 * module, or runtime lookup. Guest invocation invalidates current exact-origin facts
 * because an invoked Closure can mutate or remove slots of a captured execution
 * context.</p>
 */
final class ProtosStaticDefinitions {
    private ProtosStaticDefinitions() {
    }

    static Optional<ProtosStaticDefinitionResult> resolve(
            ProtosStaticParseResult.Parsed parsed,
            int sourceOffset) {
        Objects.requireNonNull(parsed, "parsed");
        if (sourceOffset < 0 || sourceOffset >= parsed.snapshot().characters().length()) {
            return Optional.empty();
        }
        return new Resolver(parsed.snapshot(), sourceOffset).resolve(parsed.program());
    }

    private static final class Resolver {
        private final ProtosDocumentSnapshot snapshot;
        private final int sourceOffset;
        private ProtosStaticDefinitionResult result;

        Resolver(ProtosDocumentSnapshot snapshot, int sourceOffset) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            this.sourceOffset = sourceOffset;
        }

        Optional<ProtosStaticDefinitionResult> resolve(SurfaceSequence program) {
            analyze(program, new Facts());
            return Optional.ofNullable(result);
        }

        private void analyze(SurfaceExpression expression, Facts facts) {
            if (result != null) {
                return;
            }

            switch (expression) {
                case SurfaceLiteral ignored -> {
                }
                case SurfaceName name -> analyzeName(name, facts);
                case SurfaceIntrinsic ignored -> {
                }
                case SurfaceSequence sequence -> analyzeSequence(sequence, facts);
                case SurfaceGroup group -> analyze(group.expression(), facts);
                case SurfaceMember member -> analyze(member.receiver(), facts);
                case SurfaceCall call -> {
                    analyze(call.receiver(), facts);
                    analyzeArguments(call.arguments(), facts);
                    opaqueInvocationBarrier(facts);
                }
                case SurfaceIndex index -> {
                    analyze(index.receiver(), facts);
                    analyze(index.index(), facts);
                    opaqueInvocationBarrier(facts);
                }
                case SurfaceUnary unary -> {
                    analyze(unary.operand(), facts);
                    opaqueInvocationBarrier(facts);
                }
                case SurfaceBinary binary -> {
                    analyze(binary.left(), facts);
                    if (isLazyBoolean(binary)) {
                        // && / || lower the right operand to a parameterless Closure.
                        // Its bare names are captured outer bindings, which G4-A1
                        // deliberately does not claim.
                        analyze(binary.right(), new Facts());
                    } else {
                        analyze(binary.right(), facts);
                    }
                    opaqueInvocationBarrier(facts);
                }
                case SurfaceNonLocalReturn nonLocalReturn -> {
                    analyze(nonLocalReturn.expression(), facts);
                    facts.clear();
                }
                case SurfaceMatch match -> analyzeMatch(match, facts);
                case SurfaceSlotCreation creation -> {
                    analyzeMutationTarget(creation.target(), facts);
                    analyze(creation.value(), facts);
                }
                case SurfaceAssignment assignment -> {
                    analyzeMutationTarget(assignment.target(), facts);
                    analyze(assignment.value(), facts);
                }
                case SurfaceSuperSend superSend -> {
                    analyzeArguments(superSend.arguments(), facts);
                    opaqueInvocationBarrier(facts);
                }
                case SurfaceObject object -> analyzeObject(object, facts);
                case SurfaceClosure closure -> analyzeClosure(closure);
            }
        }

        private void analyzeName(SurfaceName name, Facts facts) {
            if (!contains(name.span(), sourceOffset)) {
                return;
            }
            SourceSpan origin = facts.origin(name.name());
            if (origin != null) {
                result = ProtosStaticDefinitionResult.singleton(snapshot, name.span(), origin);
            }
        }

        private void analyzeSequence(SurfaceSequence sequence, Facts facts) {
            for (SurfaceExpression expression : sequence.expressions()) {
                analyze(expression, facts);
                if (result != null) {
                    return;
                }
            }
        }

        private void analyzeArguments(List<SurfaceArgument> arguments, Facts facts) {
            for (SurfaceArgument argument : arguments) {
                analyze(argument.expression(), facts);
                if (result != null) {
                    return;
                }
            }
        }

        private void analyzeMutationTarget(SurfaceExpression target, Facts facts) {
            if (target instanceof SurfaceName) {
                return;
            }
            if (target instanceof SurfaceMember member) {
                analyze(member.receiver(), facts);
                return;
            }
            if (target instanceof SurfaceIndex index) {
                analyze(index.receiver(), facts);
                analyze(index.index(), facts);
                return;
            }

            // Parser-authoritative source currently permits only the cases above.
            // If a future surface form reaches this implementation before G4 is
            // widened, inspect only nested activations and never use current facts.
            analyze(target, new Facts());
        }

        private void analyzeObject(SurfaceObject object, Facts facts) {
            object.parent().ifPresent(parent -> analyze(parent, facts));
            if (result != null) {
                return;
            }

            // Object construction is not the current Closure activation. Direct
            // object-body reads therefore cannot consume the caller's local-origin
            // facts in G4-A1. Nested Closures/match arms are still discoverable.
            Facts constructionFacts = new Facts();
            for (SurfaceObjectItem item : object.items()) {
                analyze(item.expression(), constructionFacts);
                if (result != null) {
                    return;
                }
            }

            // The object body is ordinary guest execution and may invoke behavior
            // that mutates a captured caller context.
            facts.clear();
        }

        private void analyzeClosure(SurfaceClosure closure) {
            Facts activation = new Facts();

            for (SurfaceParameter parameter : closure.parameters()) {
                if (parameter.defaultValue().isPresent()) {
                    Facts suppliedPath = activation.copy();
                    Facts defaultPath = activation.copy();
                    analyze(parameter.defaultValue().orElseThrow(), defaultPath);
                    if (result != null) {
                        return;
                    }
                    activation.replaceWithIntersection(suppliedPath, defaultPath);
                }

                // On every successful path the parameter slot exists only after
                // its supplied/default value has completed normally.
                activation.put(parameter.name(), parameter.span());
            }

            analyze(closure.body(), activation);
        }

        private void analyzeMatch(SurfaceMatch match, Facts enclosingFacts) {
            analyze(match.subject(), enclosingFacts);
            if (result != null) {
                return;
            }

            for (SurfaceMatch.Arm arm : match.arms()) {
                scanPatternExpressions(arm.pattern());
                if (result != null) {
                    return;
                }

                Facts armFacts = exactArmFacts(arm.pattern());
                arm.guard().ifPresent(guard -> analyze(guard, armFacts));
                if (result != null) {
                    return;
                }
                analyze(arm.body(), armFacts);
                if (result != null) {
                    return;
                }
            }

            // Matching may invoke ordinary matcher/guard/body Closures. Without a
            // complete interprocedural effect proof, enclosing local origins are
            // no longer exact after the matching expression.
            enclosingFacts.clear();
        }

        private void scanPatternExpressions(SurfaceMatchPattern pattern) {
            switch (pattern) {
                case SurfaceMatchPattern.Binder ignored -> {
                }
                case SurfaceMatchPattern.Wildcard ignored -> {
                }
                case SurfaceMatchPattern.Alias alias -> scanPatternExpressions(alias.pattern());
                case SurfaceMatchPattern.Group group -> scanPatternExpressions(group.pattern());
                case SurfaceMatchPattern.Or orPattern -> {
                    for (SurfaceMatchPattern alternative : orPattern.alternatives()) {
                        scanPatternExpressions(alternative);
                        if (result != null) {
                            return;
                        }
                    }
                }
                case SurfaceMatchPattern.Value value ->
                        analyze(value.matcher(), new Facts());
                case SurfaceMatchPattern.ArrayPattern array -> {
                    for (SurfaceMatchPattern item : array.prefix()) {
                        scanPatternExpressions(item);
                    }
                    array.remainder()
                            .flatMap(SurfaceMatchPattern.Remainder::pattern)
                            .ifPresent(this::scanPatternExpressions);
                    for (SurfaceMatchPattern item : array.suffix()) {
                        scanPatternExpressions(item);
                    }
                }
                case SurfaceMatchPattern.MapPattern map -> {
                    for (SurfaceMatchPattern.MapEntry entry : map.entries()) {
                        analyze(entry.key(), new Facts());
                        scanPatternExpressions(entry.valuePattern());
                    }
                    map.remainder()
                            .flatMap(SurfaceMatchPattern.Remainder::pattern)
                            .ifPresent(this::scanPatternExpressions);
                }
            }
        }

        private Facts exactArmFacts(SurfaceMatchPattern pattern) {
            Facts facts = new Facts();
            for (Map.Entry<String, Provenance> entry : provenance(pattern).entrySet()) {
                SourceSpan singleton = entry.getValue().completeSingleton();
                if (singleton != null) {
                    facts.put(entry.getKey(), singleton);
                }
            }
            return facts;
        }

        private Map<String, Provenance> provenance(SurfaceMatchPattern pattern) {
            if (pattern instanceof SurfaceMatchPattern.Binder binder) {
                return singletonBinding(binder.name(), binder.span());
            }
            if (pattern instanceof SurfaceMatchPattern.Wildcard) {
                return Map.of();
            }
            if (pattern instanceof SurfaceMatchPattern.Value value) {
                LinkedHashMap<String, Provenance> bindings = new LinkedHashMap<>();
                value.captureInterface().ifPresent(capture -> {
                    for (String name : capture.requiredNames()) {
                        bindings.put(name, Provenance.incomplete());
                    }
                    capture.restName().ifPresent(name ->
                            bindings.put(name, Provenance.incomplete()));
                });
                return bindings;
            }
            if (pattern instanceof SurfaceMatchPattern.Group group) {
                return provenance(group.pattern());
            }
            if (pattern instanceof SurfaceMatchPattern.Alias alias) {
                LinkedHashMap<String, Provenance> bindings =
                        new LinkedHashMap<>(provenance(alias.pattern()));
                mergeSequential(
                        bindings,
                        alias.name(),
                        Provenance.singleton(alias.span()));
                return bindings;
            }
            if (pattern instanceof SurfaceMatchPattern.Or orPattern) {
                return alternativeProvenance(orPattern.alternatives());
            }
            if (pattern instanceof SurfaceMatchPattern.ArrayPattern array) {
                LinkedHashMap<String, Provenance> bindings = new LinkedHashMap<>();
                for (SurfaceMatchPattern item : array.prefix()) {
                    mergeSequential(bindings, provenance(item));
                }
                array.remainder()
                        .flatMap(SurfaceMatchPattern.Remainder::pattern)
                        .ifPresent(item -> mergeSequential(bindings, provenance(item)));
                for (SurfaceMatchPattern item : array.suffix()) {
                    mergeSequential(bindings, provenance(item));
                }
                return bindings;
            }
            if (pattern instanceof SurfaceMatchPattern.MapPattern map) {
                LinkedHashMap<String, Provenance> bindings = new LinkedHashMap<>();
                for (SurfaceMatchPattern.MapEntry entry : map.entries()) {
                    mergeSequential(bindings, provenance(entry.valuePattern()));
                }
                map.remainder()
                        .flatMap(SurfaceMatchPattern.Remainder::pattern)
                        .ifPresent(item -> mergeSequential(bindings, provenance(item)));
                return bindings;
            }
            throw new AssertionError("unknown surface match pattern: " + pattern.getClass().getName());
        }

        private Map<String, Provenance> alternativeProvenance(
                List<SurfaceMatchPattern> alternatives) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            List<Map<String, Provenance>> branches = alternatives.stream()
                    .map(this::provenance)
                    .toList();
            for (Map<String, Provenance> branch : branches) {
                names.addAll(branch.keySet());
            }

            LinkedHashMap<String, Provenance> result = new LinkedHashMap<>();
            for (String name : names) {
                LinkedHashSet<SourceSpan> origins = new LinkedHashSet<>();
                boolean complete = true;
                for (Map<String, Provenance> branch : branches) {
                    Provenance candidate = branch.get(name);
                    if (candidate == null) {
                        complete = false;
                        continue;
                    }
                    complete &= candidate.complete();
                    origins.addAll(candidate.origins());
                }
                result.put(name, new Provenance(origins, complete));
            }
            return result;
        }

        private Map<String, Provenance> singletonBinding(String name, SourceSpan span) {
            LinkedHashMap<String, Provenance> result = new LinkedHashMap<>();
            result.put(name, Provenance.singleton(span));
            return result;
        }

        private void mergeSequential(
                LinkedHashMap<String, Provenance> destination,
                Map<String, Provenance> source) {
            for (Map.Entry<String, Provenance> entry : source.entrySet()) {
                mergeSequential(destination, entry.getKey(), entry.getValue());
            }
        }

        private void mergeSequential(
                LinkedHashMap<String, Provenance> destination,
                String name,
                Provenance provenance) {
            Provenance previous = destination.putIfAbsent(name, provenance);
            if (previous != null) {
                destination.put(name, previous.conflictedWith(provenance));
            }
        }

        private boolean isLazyBoolean(SurfaceBinary binary) {
            return "&&".equals(binary.operator()) || "||".equals(binary.operator());
        }

        private void opaqueInvocationBarrier(Facts facts) {
            facts.clear();
        }
    }

    private static boolean contains(SourceSpan span, int offset) {
        return span.startOffset() <= offset && offset < span.endOffset();
    }

    private static final class Facts {
        private final LinkedHashMap<String, SourceSpan> origins;

        Facts() {
            this.origins = new LinkedHashMap<>();
        }

        private Facts(LinkedHashMap<String, SourceSpan> origins) {
            this.origins = origins;
        }

        Facts copy() {
            return new Facts(new LinkedHashMap<>(origins));
        }

        SourceSpan origin(String name) {
            return origins.get(name);
        }

        void put(String name, SourceSpan origin) {
            origins.put(name, origin);
        }

        void replaceWithIntersection(Facts left, Facts right) {
            origins.clear();
            for (Map.Entry<String, SourceSpan> entry : left.origins.entrySet()) {
                if (entry.getValue().equals(right.origins.get(entry.getKey()))) {
                    origins.put(entry.getKey(), entry.getValue());
                }
            }
        }

        void clear() {
            origins.clear();
        }
    }

    private record Provenance(Set<SourceSpan> origins, boolean complete) {
        Provenance {
            origins = Set.copyOf(Objects.requireNonNull(origins, "origins"));
        }

        static Provenance singleton(SourceSpan origin) {
            return new Provenance(Set.of(origin), true);
        }

        static Provenance incomplete() {
            return new Provenance(Set.of(), false);
        }

        Provenance conflictedWith(Provenance other) {
            LinkedHashSet<SourceSpan> combined = new LinkedHashSet<>(origins);
            combined.addAll(other.origins);
            return new Provenance(combined, false);
        }

        SourceSpan completeSingleton() {
            if (!complete || origins.size() != 1) {
                return null;
            }
            return origins.iterator().next();
        }
    }
}

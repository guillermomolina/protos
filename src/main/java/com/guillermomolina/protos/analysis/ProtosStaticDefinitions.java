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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
                case SurfaceArrayConstruction array -> {
                    analyzeArguments(array.arguments(), facts);
                    opaqueInvocationBarrier(facts);
                }
                case SurfaceMapConstruction map -> {
                    // D136 performs ordinary Map() invocation before the first
                    // source entry, so generation-1 exact-origin facts cannot
                    // survive into entry evaluation without an effect proof.
                    opaqueInvocationBarrier(facts);
                    for (SurfaceMapConstruction.Entry entry : map.entries()) {
                        analyze(entry.key(), facts);
                        analyze(entry.value(), facts);
                        opaqueInvocationBarrier(facts);
                    }
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
                case SurfaceSlotCreation creation -> {
                    analyzeMutationTarget(creation.target(), facts);
                    analyze(creation.value(), facts);
                }
                case SurfaceMultipleSlotCreation creation ->
                        analyze(creation.value(), facts);
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
            // facts in G4-A1. Nested Closures are still discoverable.
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

}

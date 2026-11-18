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
import com.guillermomolina.protos.parser.ast.SurfaceContextualFor;
import com.guillermomolina.protos.parser.ast.SurfaceContextualIf;
import com.guillermomolina.protos.parser.ast.SurfaceContextualUntil;
import com.guillermomolina.protos.parser.ast.SurfaceContextualWhile;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * D110 exact definition proof over one parser-authoritative snapshot.
 *
 * <p>This resolver knows only source origins that can be proved from the current
 * activation in evaluation order: Closure parameters, bare-name slot creations
 * ({@code x: value} and grouped creations) that completed before the read, the
 * proven target of a bare-name assignment, and the own members of a bare object
 * literal that is still the proven value of a local slot. A slot origin is
 * recorded only after its value has been evaluated, because creation evaluates
 * the value before creating the slot in the current context. Object provenance
 * is limited to literals whose construction runs no guest code; inherited
 * members are never attributed.</p>
 *
 * <p>Guest invocation, and any other effect whose impact cannot be proved,
 * invalidates current exact-origin and object facts because invoked code can
 * mutate or remove slots of a captured execution context or of a reachable
 * object. A local object also loses its member facts once its value escapes as
 * an ordinary read or its slot is overwritten with an unknown value. The
 * resolver never executes Protos code and never falls back to same-name,
 * workspace-symbol, module, or runtime lookup.</p>
 *
 * <p>One {@link ProofWalker} owns the fact propagation. Definition asks it for
 * the proven origin under an offset; LM010-B completion asks it for the proven
 * Closure-parameter names at one exact read site, which ordinary slot facts do
 * not extend. Facts are query-local; no index outlives a call.</p>
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
        ProtosDocumentSnapshot snapshot = parsed.snapshot();
        ProofWalker<ProtosStaticDefinitionResult> walker = new ProofWalker<>() {
            private ProtosStaticDefinitionResult result;

            @Override
            boolean visitRead(SurfaceName name, Facts facts) {
                if (!contains(name.span(), sourceOffset)) {
                    return false;
                }
                SourceSpan origin = facts.origin(name.name());
                if (origin == null) {
                    return false;
                }
                result = ProtosStaticDefinitionResult.singleton(snapshot, name.span(), origin);
                return true;
            }

            @Override
            boolean visitAssignmentTarget(SurfaceName name, Facts facts) {
                return visitRead(name, facts);
            }

            @Override
            boolean visitMember(SurfaceMember member, Facts facts) {
                SourceSpan selector = selectorSpan(snapshot, member);
                if (selector == null || !contains(selector, sourceOffset)) {
                    return false;
                }
                if (!(member.receiver() instanceof SurfaceName receiver)) {
                    return false;
                }
                SourceSpan origin = facts.memberOrigin(receiver.name(), member.name());
                if (origin == null) {
                    return false;
                }
                result = ProtosStaticDefinitionResult.singleton(snapshot, selector, origin);
                return true;
            }

            @Override
            Optional<ProtosStaticDefinitionResult> result() {
                return Optional.ofNullable(result);
            }
        };
        walker.walk(parsed.program());
        return walker.result();
    }

    /**
     * Returns the names whose exact Closure-parameter origin is proven at one
     * read site, in fact order.
     *
     * <p>The site must be a {@link SurfaceName} read whose span equals
     * {@code readSpan} exactly; otherwise (a declaration, a mutation target, a
     * member name, or no node at all) the result is empty. A present but empty
     * list means the site is a proven read with no surviving facts.</p>
     */
    static Optional<List<String>> provenNamesAtRead(
            SurfaceSequence program,
            SourceSpan readSpan) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(readSpan, "readSpan");
        ProofWalker<List<String>> walker = new ProofWalker<>() {
            private List<String> names;

            @Override
            boolean visitRead(SurfaceName name, Facts facts) {
                if (!name.span().equals(readSpan)) {
                    return false;
                }
                names = facts.parameterNames();
                return true;
            }

            @Override
            Optional<List<String>> result() {
                return Optional.ofNullable(names);
            }
        };
        walker.walk(program);
        return walker.result();
    }

    /**
     * D110 fact propagation in evaluation order.
     *
     * <p>{@link #visitRead} observes every {@link SurfaceName} read with the
     * facts proven at that point; {@link #visitAssignmentTarget} observes a
     * bare-name assignment target at its selection point, before the assigned
     * value is evaluated; {@link #visitMember} observes a member access after
     * its receiver has been analyzed. Observers must not retain or mutate the
     * facts. Returning {@code true} stops the walk.</p>
     */
    private abstract static class ProofWalker<R> {
        private boolean done;

        abstract boolean visitRead(SurfaceName name, Facts facts);

        boolean visitAssignmentTarget(SurfaceName name, Facts facts) {
            return false;
        }

        boolean visitMember(SurfaceMember member, Facts facts) {
            return false;
        }

        abstract Optional<R> result();

        final void walk(SurfaceSequence program) {
            analyze(program, new Facts());
        }

        private void analyze(SurfaceExpression expression, Facts facts) {
            if (done) {
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
                case SurfaceMember member -> analyzeMember(member, facts);
                case SurfaceCall call -> {
                    analyze(call.receiver(), facts);
                    analyzeArguments(call.arguments(), facts);
                    opaqueInvocationBarrier(facts);
                }
                case SurfaceContextualWhile loop -> {
                    // D210-Q2 lowers the condition to a parameterless Closure and
                    // the body is a Closure literal; neither runs in the current
                    // activation, so neither may consume its facts.
                    analyze(loop.condition(), new Facts());
                    if (!done) {
                        analyzeClosure(loop.body());
                    }
                    opaqueInvocationBarrier(facts);
                }
                case SurfaceContextualIf conditional -> {
                    // D211 evaluates the condition eagerly in this activation, as
                    // the ordinary ifTrue/ifTrueIfFalse send receiver; the branches
                    // are Closure literals constructed but not run here, and the
                    // send itself is an opaque invocation.
                    analyze(conditional.condition(), facts);
                    if (!done) {
                        analyzeClosure(conditional.trueBranch());
                    }
                    if (!done && conditional.falseBranch() != null) {
                        analyzeClosure(conditional.falseBranch());
                    }
                    opaqueInvocationBarrier(facts);
                }
                case SurfaceContextualUntil loop -> {
                    // D212 lowers the braced body and the condition to two independent
                    // parameterless Closures; neither runs in the current activation, so
                    // neither may consume its facts, and the untilTrue send itself is an
                    // opaque invocation.
                    analyzeClosure(loop.body());
                    if (!done) {
                        analyze(loop.condition(), new Facts());
                    }
                    opaqueInvocationBarrier(facts);
                }
                case SurfaceContextualFor loop -> {
                    // D231-F2-PURE lowers to an ordinary each/eachStep send. Header
                    // expressions run in this activation; the numeric form first
                    // invokes the ordinary Range(start, stop) constructor before step.
                    // The binder is only the body Closure's parameter, and the send
                    // itself is an opaque invocation.
                    if (loop.kind() == SurfaceContextualFor.Kind.GENERIC) {
                        analyze(loop.iterable(), facts);
                    } else {
                        analyze(loop.start(), facts);
                        if (!done) {
                            analyze(loop.stop(), facts);
                        }
                        opaqueInvocationBarrier(facts);
                        if (!done && loop.step() != null) {
                            analyze(loop.step(), facts);
                        }
                    }
                    if (!done) {
                        analyzeClosure(loop.body());
                    }
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
                case SurfaceSlotCreation creation -> analyzeSlotCreation(creation, facts);
                case SurfaceMultipleSlotCreation creation -> {
                    // D143 evaluates the source once and observes Array state
                    // without user-visible sends; every target exists, left to
                    // right, only after that evaluation completes normally.
                    analyze(creation.value(), facts);
                    if (!done) {
                        for (SurfaceName target : creation.targets()) {
                            facts.putSlot(target.name(), target.span(), null);
                        }
                    }
                }
                case SurfaceAssignment assignment -> analyzeAssignment(assignment, facts);
                case SurfaceSuperSend superSend -> {
                    analyzeArguments(superSend.arguments(), facts);
                    opaqueInvocationBarrier(facts);
                }
                case SurfaceObject object -> analyzeObject(object, facts);
                case SurfaceClosure closure -> analyzeClosure(closure);
            }
        }

        private void analyzeName(SurfaceName name, Facts facts) {
            done = visitRead(name, facts);
            // The value escapes: an alias may later change the object's slots
            // without naming this slot.
            facts.forgetObject(name.name());
        }

        private void analyzeReceiver(SurfaceExpression receiver, Facts facts) {
            if (receiver instanceof SurfaceName name) {
                // A direct member receiver is read without exposing the value.
                done = visitRead(name, facts);
                return;
            }
            analyze(receiver, facts);
        }

        private void analyzeMember(SurfaceMember member, Facts facts) {
            analyzeReceiver(member.receiver(), facts);
            if (!done) {
                done = visitMember(member, facts);
            }
        }

        private void analyzeSlotCreation(SurfaceSlotCreation creation, Facts facts) {
            analyzeMutationTarget(creation.target(), facts);
            if (done) {
                return;
            }
            // Creation evaluates the value first and only then creates the slot
            // in the current context.
            analyze(creation.value(), facts);
            if (done) {
                return;
            }
            if (creation.target() instanceof SurfaceName name) {
                facts.putSlot(name.name(), name.span(), literalMembers(creation.value()));
            } else if (creation.target() instanceof SurfaceMember member) {
                if (member.receiver() instanceof SurfaceName receiver
                        && facts.knowsObject(receiver.name())) {
                    // A new own slot of a proven literal object changes only
                    // that object's member set.
                    facts.forgetObject(receiver.name());
                } else {
                    // The receiver may be the current context or an alias of
                    // it, so any slot or object fact may have changed.
                    facts.clear();
                }
            }
        }

        private void analyzeAssignment(SurfaceAssignment assignment, Facts facts) {
            if (assignment.target() instanceof SurfaceName name) {
                // The target slot is selected before the value is evaluated.
                done = visitAssignmentTarget(name, facts);
                if (done) {
                    return;
                }
                SourceSpan selected = facts.origin(name.name());
                analyze(assignment.value(), facts);
                if (done) {
                    return;
                }
                // The write changes the selected slot's value, not its presence
                // or origin; object provenance follows only a proven new value.
                if (selected != null && selected.equals(facts.origin(name.name()))) {
                    facts.replaceObject(name.name(), literalMembers(assignment.value()));
                } else {
                    facts.forgetObject(name.name());
                }
                return;
            }

            analyzeMutationTarget(assignment.target(), facts);
            if (done) {
                return;
            }
            analyze(assignment.value(), facts);
            if (done) {
                return;
            }
            if (assignment.target() instanceof SurfaceMember member) {
                if (!(member.receiver() instanceof SurfaceName receiver
                        && facts.memberOrigin(receiver.name(), member.name()) != null)) {
                    // Only a write to a known own member of a proven literal
                    // object keeps its facts. Any other receiver, including the
                    // current context, this, or an unproven value, may replace
                    // slots whose facts are held here.
                    facts.clear();
                }
            } else if (assignment.target() instanceof SurfaceIndex) {
                // Indexed writes are ordinary sends to the receiver.
                opaqueInvocationBarrier(facts);
            }
        }

        private void analyzeSequence(SurfaceSequence sequence, Facts facts) {
            for (SurfaceExpression expression : sequence.expressions()) {
                analyze(expression, facts);
                if (done) {
                    return;
                }
            }
        }

        private void analyzeArguments(List<SurfaceArgument> arguments, Facts facts) {
            for (SurfaceArgument argument : arguments) {
                analyze(argument.expression(), facts);
                if (done) {
                    return;
                }
            }
        }

        private void analyzeMutationTarget(SurfaceExpression target, Facts facts) {
            if (target instanceof SurfaceName) {
                return;
            }
            if (target instanceof SurfaceMember member) {
                analyzeReceiver(member.receiver(), facts);
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
            if (done) {
                return;
            }

            // Object construction is not the current Closure activation. Direct
            // object-body reads therefore cannot consume the caller's local-origin
            // facts, and object-body creations make object slots, never lexical
            // slots of any activation. Nested Closures are still discoverable.
            Facts constructionFacts = Facts.objectBody();
            for (SurfaceObjectItem item : object.items()) {
                analyze(item.expression(), constructionFacts);
                if (done) {
                    return;
                }
            }

            // A general object body is ordinary guest execution and may invoke
            // behavior that mutates a captured caller context. A bare literal
            // body whose items construct only literals runs no guest code.
            if (literalMembers(object) == null) {
                facts.clear();
            }
        }

        /**
         * Returns the own member declarations of a bare object literal whose
         * construction runs no guest code, or {@code null} when the value is not
         * such a literal. The literal must have no parent expression and no
         * composition, and every item must be a distinct bare-name creation
         * whose value is a literal, a Closure literal, or such an object literal.
         */
        private static Map<String, SourceSpan> literalMembers(SurfaceExpression value) {
            SurfaceExpression unwrapped = value;
            while (unwrapped instanceof SurfaceGroup group) {
                unwrapped = group.expression();
            }
            if (!(unwrapped instanceof SurfaceObject object) || object.parent().isPresent()) {
                return null;
            }
            LinkedHashMap<String, SourceSpan> members = new LinkedHashMap<>();
            for (SurfaceObjectItem item : object.items()) {
                if (item.composition()
                        || !(item.expression() instanceof SurfaceSlotCreation creation)
                        || !(creation.target() instanceof SurfaceName name)
                        || !constructsWithoutGuestCode(creation.value())
                        || members.putIfAbsent(name.name(), name.span()) != null) {
                    return null;
                }
            }
            return members;
        }

        private static boolean constructsWithoutGuestCode(SurfaceExpression value) {
            if (value instanceof SurfaceLiteral || value instanceof SurfaceClosure) {
                return true;
            }
            if (value instanceof SurfaceGroup group) {
                return constructsWithoutGuestCode(group.expression());
            }
            return value instanceof SurfaceObject && literalMembers(value) != null;
        }

        private void analyzeClosure(SurfaceClosure closure) {
            Facts activation = new Facts();

            for (SurfaceParameter parameter : closure.parameters()) {
                if (parameter.defaultValue().isPresent()) {
                    Facts suppliedPath = activation.copy();
                    Facts defaultPath = activation.copy();
                    analyze(parameter.defaultValue().orElseThrow(), defaultPath);
                    if (done) {
                        return;
                    }
                    activation.replaceWithIntersection(suppliedPath, defaultPath);
                }

                // On every successful path the parameter slot exists only after
                // its supplied/default value has completed normally.
                activation.putParameter(parameter.name(), parameter.span());
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

    /**
     * Returns the selector token of a member access, derived from the end of
     * the member span as document symbols do, or {@code null} when the span
     * does not end with exactly the member name in this snapshot.
     */
    private static SourceSpan selectorSpan(ProtosDocumentSnapshot snapshot, SurfaceMember member) {
        String text = snapshot.characters();
        String name = member.name();
        int end = member.span().endOffset();
        int start = end - name.length();
        if (name.isEmpty()
                || start < member.span().startOffset()
                || start < 0
                || end > text.length()
                || !text.regionMatches(start, name, 0, name.length())) {
            return null;
        }
        return new SourceSpan(start, end);
    }

    /**
     * Query-local proven facts of one activation at the current walk point.
     *
     * <p>An origin entry means that a read of the name selects exactly that
     * declaration here; it does not mean the value is immutable. Parameter
     * provenance is kept separately so completion keeps its contract of
     * proven Closure-parameter names. Object entries map a slot whose current
     * value is a proven bare object literal to that literal's own member
     * declarations; they exist only while the slot origin is proven.</p>
     */
    private static final class Facts {
        private final boolean recordsSlots;
        private final LinkedHashMap<String, SourceSpan> origins;
        private final Set<String> parameters;
        private final Map<String, Map<String, SourceSpan>> objects;

        Facts() {
            this(true, new LinkedHashMap<>(), new HashSet<>(), new HashMap<>());
        }

        private Facts(
                boolean recordsSlots,
                LinkedHashMap<String, SourceSpan> origins,
                Set<String> parameters,
                Map<String, Map<String, SourceSpan>> objects) {
            this.recordsSlots = recordsSlots;
            this.origins = origins;
            this.parameters = parameters;
            this.objects = objects;
        }

        /** Facts for an object body, whose creations are object slots. */
        static Facts objectBody() {
            return new Facts(false, new LinkedHashMap<>(), new HashSet<>(), new HashMap<>());
        }

        Facts copy() {
            return new Facts(
                    recordsSlots,
                    new LinkedHashMap<>(origins),
                    new HashSet<>(parameters),
                    new HashMap<>(objects));
        }

        SourceSpan origin(String name) {
            return origins.get(name);
        }

        List<String> parameterNames() {
            return origins.keySet().stream().filter(parameters::contains).toList();
        }

        SourceSpan memberOrigin(String receiver, String member) {
            Map<String, SourceSpan> members = objects.get(receiver);
            return members == null ? null : members.get(member);
        }

        void putParameter(String name, SourceSpan origin) {
            origins.put(name, origin);
            parameters.add(name);
            objects.remove(name);
        }

        void putSlot(String name, SourceSpan origin, Map<String, SourceSpan> members) {
            if (!recordsSlots) {
                return;
            }
            origins.put(name, origin);
            parameters.remove(name);
            replaceObject(name, members);
        }

        void replaceObject(String name, Map<String, SourceSpan> members) {
            if (members == null || !origins.containsKey(name)) {
                objects.remove(name);
            } else {
                objects.put(name, Map.copyOf(members));
            }
        }

        boolean knowsObject(String name) {
            return objects.containsKey(name);
        }

        void forgetObject(String name) {
            objects.remove(name);
        }

        void replaceWithIntersection(Facts left, Facts right) {
            origins.clear();
            parameters.clear();
            objects.clear();
            for (Map.Entry<String, SourceSpan> entry : left.origins.entrySet()) {
                String name = entry.getKey();
                if (!entry.getValue().equals(right.origins.get(name))) {
                    continue;
                }
                origins.put(name, entry.getValue());
                if (left.parameters.contains(name) && right.parameters.contains(name)) {
                    parameters.add(name);
                }
                Map<String, SourceSpan> members = left.objects.get(name);
                if (members != null && members.equals(right.objects.get(name))) {
                    objects.put(name, members);
                }
            }
        }

        void clear() {
            origins.clear();
            parameters.clear();
            objects.clear();
        }
    }

}

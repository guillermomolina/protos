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
import com.guillermomolina.protos.parser.ast.SurfaceContextualMatch;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
 * <p>A Closure body is analyzed against captured facts only when the Closure
 * literal is invoked immediately with literal positional arguments; its fresh
 * activation then consults its own slots first and only afterwards the facts of
 * the lexical parent proven at the invocation point. A Closure whose invocation
 * time is not proven never sees captured facts.</p>
 *
 * <p>Declaration navigation ({@link #resolveDeclaration}) additionally uses
 * the lexical identity of each read: the chain of source contexts built from
 * the AST, which does not depend on when a Closure is invoked. A lexical
 * declaration is a navigation candidate, not an exact origin proof; the strict
 * queries never consult it.</p>
 *
 * <p>One {@link ProofWalker} owns the fact propagation. Definition asks it for
 * the proven origin under an offset; LM010-B completion asks it for the proven
 * Closure-parameter names at one exact read site, which ordinary slot facts do
 * not extend. Facts are query-local; no index outlives a call.</p>
 */
final class ProtosStaticDefinitions {
    private ProtosStaticDefinitions() {
    }

    /**
     * Resolves the exact origin proof under one offset. Only an origin
     * proven at the read site under the dynamic presence, removal, re-creation
     * and effect rules is returned; References and Hover rely on this mode.
     */
    static Optional<ProtosStaticDefinitionResult> resolve(
            ProtosStaticParseResult.Parsed parsed,
            int sourceOffset) {
        return resolve(parsed, sourceOffset, false);
    }

    /**
     * Resolves declaration navigation under one offset.
     *
     * <p>An exact origin proof is preferred. When none survives, a bare-name
     * read or assignment target falls back to its lexical declarations: the
     * source declarations that the read's chain of lexical contexts, built from
     * the AST alone, can select. This holds regardless of when, by whom, or
     * whether a Closure on that chain is invoked. A lexical declaration is a
     * navigation candidate, not a proof that the slot is present when the read
     * runs. Several declarations of one name in one context are all returned;
     * a read whose only possible origin is the dynamic receiver has none.</p>
     */
    static Optional<ProtosStaticDefinitionResult> resolveDeclaration(
            ProtosStaticParseResult.Parsed parsed,
            int sourceOffset) {
        return resolve(parsed, sourceOffset, true);
    }

    private static Optional<ProtosStaticDefinitionResult> resolve(
            ProtosStaticParseResult.Parsed parsed,
            int sourceOffset,
            boolean lexicalNavigation) {
        Objects.requireNonNull(parsed, "parsed");
        if (sourceOffset < 0 || sourceOffset >= parsed.snapshot().characters().length()) {
            return Optional.empty();
        }
        ProtosDocumentSnapshot snapshot = parsed.snapshot();
        ProofWalker<ProtosStaticDefinitionResult> walker =
                new ProofWalker<>(lexicalNavigation) {
            private ProtosStaticDefinitionResult result;

            @Override
            boolean visitRead(SurfaceName name, Facts facts) {
                if (!contains(name.span(), sourceOffset)) {
                    return false;
                }
                SourceSpan origin = facts.origin(name.name());
                if (origin != null) {
                    result = ProtosStaticDefinitionResult.singleton(snapshot, name.span(), origin);
                    return true;
                }
                if (!lexicalNavigation) {
                    return false;
                }
                List<SourceSpan> declarations = lexicalDeclarations(name);
                if (declarations.isEmpty()) {
                    return false;
                }
                result = new ProtosStaticDefinitionResult(
                        snapshot,
                        name.span(),
                        declarations.stream()
                                .map(span -> new ProtosStaticDefinitionResult.Target(snapshot, span))
                                .toList());
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
        ProofWalker<List<String>> walker = new ProofWalker<>(false) {
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
     *
     * <p>When constructed with lexical scopes, the walk first runs once in a
     * collecting pass that observes nothing and records, by AST node identity,
     * every lexical context of the source: its lexical parent, the point where
     * it is constructed, and the parameters and bare-name creations it
     * declares. The observing pass then tracks the current {@link Scope}, so an
     * observer may ask for {@link #lexicalDeclarations}. Scopes are independent
     * of {@link Facts}: an effect that clears facts never changes where a
     * declaration was written.</p>
     */
    private abstract static class ProofWalker<R> {
        private final IdentityHashMap<Object, Scope> scopes;
        private boolean collecting;
        private Scope scope;
        private boolean done;

        ProofWalker(boolean lexicalScopes) {
            scopes = lexicalScopes ? new IdentityHashMap<>() : null;
        }

        abstract boolean visitRead(SurfaceName name, Facts facts);

        boolean visitAssignmentTarget(SurfaceName name, Facts facts) {
            return false;
        }

        boolean visitMember(SurfaceMember member, Facts facts) {
            return false;
        }

        abstract Optional<R> result();

        final void walk(SurfaceSequence program) {
            if (scopes != null) {
                Scope root = new Scope(null, false, false, program.span().startOffset());
                collecting = true;
                scope = root;
                analyze(program, new Facts());
                collecting = false;
                done = false;
                scope = root;
            }
            analyze(program, new Facts());
        }

        /**
         * Returns the lexical declarations a bare-name read or assignment target
         * at the current walk point can select, nearest context first, or an
         * empty list when only the dynamic receiver can supply the name.
         *
         * <p>In the read's own context only declarations completed before the
         * read are present. Each enclosing context is entered at the point where
         * its child context was constructed. While every context crossed so far
         * runs eagerly at construction (an object body), the enclosing context is
         * observed exactly at that point. Once a Closure activation has been
         * crossed, its invocation time is unknown: a declaration completed before
         * construction is present and ends the search, while a later one is
         * only possibly present and the search continues outward. An object
         * under construction is the current context of a direct read in its
         * body, so its slots created before the read are selected first. It is
         * never a lexical context of the Closures or nested objects it
         * constructs: their lexical parent is the genuine context of the
         * construction.</p>
         */
        final List<SourceSpan> lexicalDeclarations(SurfaceName name) {
            ArrayList<SourceSpan> found = new ArrayList<>();
            Scope current = scope;
            int point = name.span().startOffset();
            boolean deferred = false;
            while (current != null) {
                boolean present = false;
                for (Declaration declaration : current.declarations(name.name())) {
                    if (declaration.completedAt() <= point) {
                        present = true;
                        found.add(declaration.origin());
                    } else if (deferred) {
                        found.add(declaration.origin());
                    }
                }
                if (present) {
                    break;
                }
                deferred |= current.deferred;
                point = current.constructedAt;
                current = current.parent;
            }
            return List.copyOf(found);
        }

        private boolean observeRead(SurfaceName name, Facts facts) {
            return !collecting && visitRead(name, facts);
        }

        private void inScope(
                Object node,
                boolean objectBody,
                boolean deferred,
                int constructedAt,
                Runnable action) {
            if (scopes == null) {
                action.run();
                return;
            }
            Scope saved = scope;
            Scope entered = collecting ? null : scopes.get(node);
            if (entered == null) {
                // An object under construction is the current context only of
                // its own direct reads; a context created in its body links to
                // the genuine lexical context of the construction.
                Scope lexicalParent = saved;
                while (lexicalParent != null && lexicalParent.objectBody) {
                    lexicalParent = lexicalParent.parent;
                }
                entered = new Scope(lexicalParent, objectBody, deferred, constructedAt);
                scopes.put(node, entered);
            }
            scope = entered;
            try {
                action.run();
            } finally {
                scope = saved;
            }
        }

        private void inDeferredScope(SurfaceExpression node, Runnable action) {
            inScope(node, false, true, node.span().startOffset(), action);
        }

        private void declare(SurfaceName name, SourceSpan origin, int completedAt) {
            if (collecting) {
                scope.declare(name.name(), new Declaration(origin, completedAt));
            }
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
                    SurfaceClosure invoked = immediatelyInvokedClosure(call);
                    if (invoked != null) {
                        analyzeImmediateInvocation(invoked, call.arguments(), facts);
                    } else {
                        analyze(call.receiver(), facts);
                        analyzeArguments(call.arguments(), facts);
                    }
                    opaqueInvocationBarrier(facts);
                }
                case SurfaceContextualWhile loop -> {
                    // D210-Q2 lowers the condition to a parameterless Closure and
                    // the body is a Closure literal; neither runs in the current
                    // activation, so neither may consume its facts.
                    inDeferredScope(loop.condition(),
                            () -> analyze(loop.condition(), new Facts()));
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
                        inDeferredScope(loop.condition(),
                                () -> analyze(loop.condition(), new Facts()));
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
                case SurfaceContextualMatch match -> {
                    // Contextual match lowers to the ordinary
                    // subject.caseOf(%{ ... }) send: the subject is the send
                    // receiver, the table is an ordinary Map construction, and
                    // every action is a Closure constructed but not run here. An
                    // implicit action is a parameterless Closure whose body is
                    // the written expression, analyzed in its own deferred scope
                    // keyed by the stable table entry, so that a scope owned by
                    // the expression itself (an object body) keeps its own key.
                    analyzeReceiver(match.subject(), facts);
                    if (!done) {
                        opaqueInvocationBarrier(facts);
                    }
                    for (SurfaceMapConstruction.Entry entry : match.table().entries()) {
                        if (done) {
                            break;
                        }
                        analyze(entry.key(), facts);
                        if (done) {
                            break;
                        }
                        SurfaceExpression action = entry.value();
                        if (SurfaceContextualMatch.isImplicitAction(action)) {
                            inScope(
                                    entry,
                                    false,
                                    true,
                                    action.span().startOffset(),
                                    () -> analyze(action, new Facts()));
                        } else {
                            analyze(action, facts);
                        }
                        opaqueInvocationBarrier(facts);
                    }
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
                        inDeferredScope(binary.right(),
                                () -> analyze(binary.right(), new Facts()));
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
                            declare(target, target.span(), creation.span().endOffset());
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
            done = observeRead(name, facts);
            // The value escapes: an alias may later change the object's slots
            // without naming this slot.
            facts.forgetObject(name.name());
        }

        private void analyzeReceiver(SurfaceExpression receiver, Facts facts) {
            if (receiver instanceof SurfaceName name) {
                // A direct member receiver is read without exposing the value.
                done = observeRead(name, facts);
                return;
            }
            analyze(receiver, facts);
        }

        private void analyzeMember(SurfaceMember member, Facts facts) {
            analyzeReceiver(member.receiver(), facts);
            if (!done && !collecting) {
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
                declare(name, name.span(), creation.span().endOffset());
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
                done = !collecting && visitAssignmentTarget(name, facts);
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
            inScope(object, true, false, object.span().startOffset(), () -> {
                for (SurfaceObjectItem item : object.items()) {
                    analyze(item.expression(), constructionFacts);
                    if (done) {
                        return;
                    }
                }
            });
            if (done) {
                return;
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

        /**
         * Analyzes a Closure whose invocation time is not proven. Its fresh
         * activation sees no captured facts, but its lexical scope keeps the
         * context where the literal is constructed as its lexical parent.
         */
        private void analyzeClosure(SurfaceClosure closure) {
            inDeferredScope(closure, () -> analyzeActivation(closure));
        }

        private void analyzeActivation(SurfaceClosure closure) {
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
                declareParameter(parameter);
                activation.putParameter(parameter);
            }

            analyze(closure.body(), activation);
        }

        /**
         * Returns the Closure literal of a call whose activation is proven to
         * run immediately with an exact, effect-free argument association, or
         * {@code null} for every other call.
         *
         * <p>The receiver must be a Closure literal, optionally grouped, so the
         * value is freshly constructed and cannot escape before invocation. A
         * fresh Closure has no own slots and the standard {@code Object} is
         * frozen, so the ordinary {@code call} lookup can only select the
         * standard {@code Object.call}, whose Closure branch directly activates
         * that literal. Every argument must be positional, non-spread and a
         * literal; every parameter must be non-rest, and any declared default
         * must be a literal, so no guest code runs between capture and body
         * entry and each parameter binding is known exactly.</p>
         */
        private static SurfaceClosure immediatelyInvokedClosure(SurfaceCall call) {
            SurfaceExpression receiver = call.receiver();
            while (receiver instanceof SurfaceGroup group) {
                receiver = group.expression();
            }
            if (!(receiver instanceof SurfaceClosure closure)) {
                return null;
            }
            List<SurfaceParameter> parameters = closure.parameters();
            List<SurfaceArgument> arguments = call.arguments();
            if (arguments.size() > parameters.size()) {
                return null;
            }
            for (SurfaceArgument argument : arguments) {
                if (argument.spread()
                        || argument.name().isPresent()
                        || !(argument.expression() instanceof SurfaceLiteral)) {
                    return null;
                }
            }
            for (int index = 0; index < parameters.size(); index++) {
                SurfaceParameter parameter = parameters.get(index);
                if (parameter.rest()) {
                    return null;
                }
                Optional<SurfaceExpression> defaultValue = parameter.defaultValue();
                if (defaultValue.isPresent()
                        ? !(defaultValue.get() instanceof SurfaceLiteral)
                        : index >= arguments.size()) {
                    return null;
                }
            }
            return closure;
        }

        /**
         * Analyzes a Closure literal whose immediate invocation was proven by
         * {@link #immediatelyInvokedClosure}. The literal is both the call
         * receiver and the invoked body, so its construction is analyzed once,
         * here; constructing it and evaluating literal arguments touch no
         * facts. The fresh activation holds exactly its parameters and is
         * lexically linked to an independent copy of the caller facts proven
         * at the invocation point. The link proves which context a captured
         * read consults, not that its slots survive: every barrier inside the
         * body drops the link together with the local facts. The caller's own
         * post-invocation barrier remains the caller's responsibility.
         */
        private void analyzeImmediateInvocation(
                SurfaceClosure closure,
                List<SurfaceArgument> arguments,
                Facts caller) {
            analyzeArguments(arguments, caller);
            if (done) {
                return;
            }
            inDeferredScope(closure, () -> {
                Facts activation = Facts.capturing(caller.copy());
                for (SurfaceParameter parameter : closure.parameters()) {
                    declareParameter(parameter);
                    activation.putParameter(parameter);
                }
                analyze(closure.body(), activation);
            });
        }

        private void declareParameter(SurfaceParameter parameter) {
            if (collecting) {
                scope.declare(parameter.name(), new Declaration(
                        parameterIdentifier(parameter),
                        parameter.span().endOffset()));
            }
        }

        private boolean isLazyBoolean(SurfaceBinary binary) {
            return "&&".equals(binary.operator()) || "||".equals(binary.operator());
        }

        private void opaqueInvocationBarrier(Facts facts) {
            facts.clear();
        }
    }

    /**
     * Returns the parameter's identifier. The syntactic parameter span also
     * covers a default value or the rest prefix; the identifier starts the span,
     * or ends it for a rest parameter.
     */
    private static SourceSpan parameterIdentifier(SurfaceParameter parameter) {
        String name = parameter.name();
        int start = parameter.rest()
                ? parameter.span().endOffset() - name.length()
                : parameter.span().startOffset();
        return new SourceSpan(start, start + name.length());
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

    /** A source declaration and the offset after which it has completed. */
    private record Declaration(SourceSpan origin, int completedAt) {
    }

    /**
     * Query-local lexical identity of one source context: a Closure activation,
     * a lowered parameterless Closure ({@code while}/{@code until} conditions,
     * the right operand of {@code &&}/{@code ||}), an object body, or the SOURCE
     * root. It records the lexical parent, the parent offset where this context
     * is constructed, whether its execution may be deferred past construction,
     * and the declarations it makes, keyed by name in source order. Assignments
     * never declare. Object-body creations are slots of the object under
     * construction, visible only to direct reads in that body.
     */
    private static final class Scope {
        private final Scope parent;
        private final boolean objectBody;
        private final boolean deferred;
        private final int constructedAt;
        private final Map<String, List<Declaration>> declarations = new HashMap<>();

        Scope(Scope parent, boolean objectBody, boolean deferred, int constructedAt) {
            this.parent = parent;
            this.objectBody = objectBody;
            this.deferred = deferred;
            this.constructedAt = constructedAt;
        }

        void declare(String name, Declaration declaration) {
            declarations.computeIfAbsent(name, ignored -> new ArrayList<>()).add(declaration);
        }

        List<Declaration> declarations(String name) {
            return declarations.getOrDefault(name, List.of());
        }
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
     *
     * <p>{@code outer} links the facts of the proven lexical parent context of
     * an immediately invoked activation; it is {@code null} for the SOURCE
     * root and for every activation whose invocation time is not proven.
     * {@code complete} means the local origins name every slot of a fresh
     * activation, so a missing local entry proves local absence and lexical
     * lookup may continue to {@code outer}. Any invalidation drops both.
     * {@code activation} identifies the lexical context the facts belong to;
     * copies share it, so intersections combine only facts of one context.</p>
     */
    private static final class Facts {
        private final boolean recordsSlots;
        private final LinkedHashMap<String, SourceSpan> origins;
        private final Set<String> parameters;
        private final Map<String, Map<String, SourceSpan>> objects;
        private final Object activation;
        private Facts outer;
        private boolean complete;

        Facts() {
            this(true, new LinkedHashMap<>(), new HashSet<>(), new HashMap<>(),
                    new Object(), null, true);
        }

        private Facts(
                boolean recordsSlots,
                LinkedHashMap<String, SourceSpan> origins,
                Set<String> parameters,
                Map<String, Map<String, SourceSpan>> objects,
                Object activation,
                Facts outer,
                boolean complete) {
            this.recordsSlots = recordsSlots;
            this.origins = origins;
            this.parameters = parameters;
            this.objects = objects;
            this.activation = activation;
            this.outer = outer;
            this.complete = complete;
        }

        /** Facts for an object body, whose creations are object slots. */
        static Facts objectBody() {
            return new Facts(false, new LinkedHashMap<>(), new HashSet<>(), new HashMap<>(),
                    new Object(), null, false);
        }

        /**
         * Facts for a fresh, immediately invoked activation whose lexical
         * parent context holds the given facts, owned by the new activation.
         */
        static Facts capturing(Facts lexicalParent) {
            return new Facts(true, new LinkedHashMap<>(), new HashSet<>(), new HashMap<>(),
                    new Object(), lexicalParent, true);
        }

        Facts copy() {
            return new Facts(
                    recordsSlots,
                    new LinkedHashMap<>(origins),
                    new HashSet<>(parameters),
                    new HashMap<>(objects),
                    activation,
                    outer == null ? null : outer.copy(),
                    complete);
        }

        private Facts lexicalParent(String name) {
            return complete && !origins.containsKey(name) ? outer : null;
        }

        SourceSpan origin(String name) {
            SourceSpan local = origins.get(name);
            if (local != null) {
                return local;
            }
            Facts parent = lexicalParent(name);
            return parent == null ? null : parent.origin(name);
        }

        List<String> parameterNames() {
            return origins.keySet().stream().filter(parameters::contains).toList();
        }

        SourceSpan memberOrigin(String receiver, String member) {
            if (!origins.containsKey(receiver)) {
                // A local binding of the receiver name shadows outer objects.
                Facts parent = lexicalParent(receiver);
                return parent == null ? null : parent.memberOrigin(receiver, member);
            }
            Map<String, SourceSpan> members = objects.get(receiver);
            return members == null ? null : members.get(member);
        }

        /**
         * Records the parameter's identifier as its origin.
         */
        void putParameter(SurfaceParameter parameter) {
            String name = parameter.name();
            origins.put(name, parameterIdentifier(parameter));
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
            if (!origins.containsKey(name)) {
                // The written slot may belong to a lexical parent; its object
                // facts no longer describe the value.
                forgetObject(name);
            } else if (members == null) {
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
            if (outer != null) {
                outer.forgetObject(name);
            }
        }

        void replaceWithIntersection(Facts left, Facts right) {
            origins.clear();
            parameters.clear();
            objects.clear();
            complete = left.complete && right.complete;
            if (left.outer != null && right.outer != null
                    && left.outer.activation == right.outer.activation) {
                Facts parent = left.outer.copy();
                parent.replaceWithIntersection(left.outer, right.outer);
                outer = parent;
            } else {
                outer = null;
            }
            if (left.activation != right.activation) {
                complete = false;
                outer = null;
                return;
            }
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
            // The link proves only which context is consulted; an effect that
            // may mutate this activation may equally mutate captured contexts.
            outer = null;
            complete = false;
        }
    }

}

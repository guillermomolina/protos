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

package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.semantic.ast.CanonicalAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalCompose;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalDerivedInequality;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalIndexedAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalMapConstruction;
import com.guillermomolina.protos.semantic.ast.CanonicalMember;
import com.guillermomolina.protos.semantic.ast.CanonicalMultipleCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalNamedArgument;
import com.guillermomolina.protos.semantic.ast.CanonicalNotIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalObject;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.semantic.ast.CanonicalReturn;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalSpread;
import com.guillermomolina.protos.semantic.ast.CanonicalSuperSend;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The physical storage plan of one CLOSURE root after inlining.
 *
 * <p>The lexical classification of {@link CanonicalBindingAnalyzer} is kept
 * unchanged: a captured binding is still {@code Storage.CELL} in the
 * language model. This plan only decides which of the root's own CELL
 * identities need no physical cell, because every Closure literal capturing
 * them is emitted inline in this same physical root. Such an identity is
 * represented by its ordinary root {@code BytecodeLocal}.
 *
 * <p>A capture by any Closure that is materialized (stored, returned,
 * passed as a value, not admitted inline, or nested in a materialized
 * Closure, a parameter default or an inline object body) keeps the cell.
 * The decision is keyed by {@link CanonicalBindingIdentity}, never by name,
 * and is derived deterministically from the canonical tree, so it is the
 * same on every Bytecode DSL reparse.
 */
final class CanonicalInlineStoragePlan {

    /** The lowerer's inline decisions, consulted exactly as at emission. */
    interface InlinePolicy {
        /** Whether a Closure literal at an inline site is admitted inline. */
        boolean admitsInline(CanonicalClosure closure);

        /** Whether operand {@code position} (0: receiver) of a send is an inline callback site. */
        boolean inlineCallbackPosition(CanonicalSend send, int position);
    }

    private final CanonicalBindingAnalysis analysis;
    private final InlinePolicy policy;
    private final Set<CanonicalBindingIdentity> blocked = new LinkedHashSet<>();

    private CanonicalInlineStoragePlan(CanonicalBindingAnalysis analysis, InlinePolicy policy) {
        this.analysis = analysis;
        this.policy = policy;
    }

    /**
     * The immutable set of {@code rootScope}'s own CELL identities that are
     * physically represented as LOCAL, in declaration order.
     */
    static Set<CanonicalBindingIdentity> promotedCells(
            CanonicalSequence body,
            CanonicalClosure definition,
            CanonicalLexicalScope rootScope,
            CanonicalBindingAnalysis analysis,
            InlinePolicy policy) {
        if (rootScope == null
                || analysis == null
                || rootScope.kind() != CanonicalLexicalScope.Kind.CLOSURE
                || rootScope.cellNamesInDeclarationOrder().isEmpty()) {
            return Set.of();
        }
        CanonicalInlineStoragePlan plan = new CanonicalInlineStoragePlan(analysis, policy);
        if (definition != null) {
            for (CanonicalParameter parameter : definition.parameters()) {
                parameter.defaultValue().ifPresent(value -> plan.walk(value, true));
            }
        }
        plan.walk(body, false);
        Set<CanonicalBindingIdentity> promoted = new LinkedHashSet<>();
        for (String name : rootScope.cellNamesInDeclarationOrder()) {
            CanonicalBindingIdentity identity = rootScope.identity(name);
            if (!plan.blocked.contains(identity)) {
                promoted.add(identity);
            }
        }
        return promoted.isEmpty() ? Set.of() : Collections.unmodifiableSet(promoted);
    }

    /*
     * materialized: the expression is not emitted in the physical root's
     * inline region, so every Closure literal reached is a genuine Closure.
     */
    private void walk(CanonicalExpression expression, boolean materialized) {
        switch (expression) {
            case CanonicalClosure closure -> materialize(closure);
            case CanonicalSequence sequence -> walkAll(sequence.expressions(), materialized);
            case CanonicalAssign assign -> {
                assign.target().ifPresent(target -> walk(target, materialized));
                walk(assign.value(), materialized);
            }
            case CanonicalCreate create -> {
                create.target().ifPresent(target -> walk(target, materialized));
                walk(create.value(), materialized);
            }
            case CanonicalMultipleCreate create -> walk(create.value(), materialized);
            case CanonicalCall call -> {
                if (!materialized
                        && call.arguments().isEmpty()
                        && call.receiver() instanceof CanonicalClosure closure
                        && policy.admitsInline(closure)) {
                    walkInline(closure);
                } else {
                    walk(call.receiver(), materialized);
                }
                walkAll(call.arguments(), materialized);
            }
            case CanonicalSend send -> {
                walkOperand(send, 0, send.receiver(), materialized);
                List<CanonicalExpression> arguments = send.arguments();
                for (int index = 0; index < arguments.size(); index++) {
                    walkOperand(send, index + 1, arguments.get(index), materialized);
                }
            }
            case CanonicalSuperSend send -> walkAll(send.arguments(), materialized);
            case CanonicalCompose compose -> walk(compose.object(), materialized);
            case CanonicalDerivedInequality inequality -> {
                walk(inequality.left(), materialized);
                walk(inequality.right(), materialized);
            }
            case CanonicalIdentity identity -> {
                walk(identity.left(), materialized);
                walk(identity.right(), materialized);
            }
            case CanonicalNotIdentity identity -> {
                walk(identity.left(), materialized);
                walk(identity.right(), materialized);
            }
            case CanonicalIndexedAssign assign -> {
                walk(assign.receiver(), materialized);
                walk(assign.index(), materialized);
                walk(assign.value(), materialized);
            }
            case CanonicalMapConstruction map -> {
                walk(map.factory(), materialized);
                map.entries().forEach(entry -> {
                    walk(entry.key(), materialized);
                    walk(entry.value(), materialized);
                });
            }
            case CanonicalMember member -> walk(member.receiver(), materialized);
            case CanonicalObject object -> {
                object.parent().ifPresent(parent -> walk(parent, materialized));
                // An inline object body has its own activation: never inline there.
                walk(object.body(), true);
            }
            case CanonicalReturn result -> walk(result.value(), materialized);
            case CanonicalSpread spread -> walk(spread.expression(), materialized);
            case CanonicalNamedArgument named ->
                    walk(named.expression(), materialized);
            case CanonicalIntrinsic ignored -> { }
            case CanonicalLiteral ignored -> { }
            case CanonicalLookup ignored -> { }
        }
    }

    private void walkAll(List<CanonicalExpression> expressions, boolean materialized) {
        for (CanonicalExpression expression : expressions) {
            walk(expression, materialized);
        }
    }

    private void walkOperand(
            CanonicalSend send, int position, CanonicalExpression operand, boolean materialized) {
        if (!materialized
                && operand instanceof CanonicalClosure closure
                && policy.inlineCallbackPosition(send, position)
                && policy.admitsInline(closure)) {
            walkInline(closure);
        } else {
            walk(operand, materialized);
        }
    }

    /* An inline Closure runs in the physical root: its captures need no cell. */
    private void walkInline(CanonicalClosure closure) {
        walk(closure.body(), false);
    }

    /*
     * A genuine Closure transports every cell it or a nested Closure
     * references (its free cells are transitive), so none is promoted.
     */
    private void materialize(CanonicalClosure closure) {
        analysis.scopeOf(closure).ifPresent(scope -> blocked.addAll(scope.freeCells()));
        for (CanonicalParameter parameter : closure.parameters()) {
            parameter.defaultValue().ifPresent(value -> walk(value, true));
        }
        walk(closure.body(), true);
    }
}

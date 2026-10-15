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
import com.guillermomolina.protos.semantic.ast.CanonicalNotIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalObject;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.semantic.ast.CanonicalReturn;
import com.guillermomolina.protos.semantic.ast.CanonicalSend;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalSpread;
import com.guillermomolina.protos.semantic.ast.CanonicalSuperSend;
import java.util.List;

/**
 * PERF025 static return-home observability proof for a source Closure.
 *
 * <p>The return home an invocation of a Closure owns is reachable only by a
 * {@link CanonicalReturn} evaluated under that invocation or under an
 * activation sharing it: a lexically nested Closure literal (which captures
 * the creating activation's home), a parameter default, or an inline Object
 * body (whose construction activation propagates the enclosing home). A
 * dynamically obtained callee keeps its own home provenance and never
 * observes the caller's home, so no call-graph reasoning is needed.
 *
 * <p>The analysis therefore walks the whole lexical subtree, including
 * nested Closures at any depth, and reports the home observable whenever any
 * {@link CanonicalReturn} occurs. The exhaustive switch over the sealed
 * canonical expression family fails closed at compile time when a new
 * expression form is introduced. The result selects only the physical
 * representation of the home; it never changes semantics.
 */
final class CanonicalReturnHomeAnalysis {
    private CanonicalReturnHomeAnalysis() {}

    /**
     * True when an owning invocation of {@code definition} may have its return
     * home targeted by a non-local return; false only when that is proven
     * impossible.
     */
    static boolean mayObserveReturnHome(CanonicalClosure definition) {
        for (CanonicalParameter parameter : definition.parameters()) {
            if (parameter.defaultValue().map(CanonicalReturnHomeAnalysis::containsReturn).orElse(false)) {
                return true;
            }
        }
        return containsReturn(definition.body());
    }

    private static boolean containsReturn(CanonicalExpression expression) {
        return switch (expression) {
            case CanonicalReturn returnExpression -> true;
            case CanonicalClosure closure -> mayObserveReturnHome(closure);
            case CanonicalSequence sequence -> anyContainsReturn(sequence.expressions());
            case CanonicalAssign assign ->
                    assign.target().map(CanonicalReturnHomeAnalysis::containsReturn).orElse(false)
                            || containsReturn(assign.value());
            case CanonicalCreate create ->
                    create.target().map(CanonicalReturnHomeAnalysis::containsReturn).orElse(false)
                            || containsReturn(create.value());
            case CanonicalMultipleCreate create -> containsReturn(create.value());
            case CanonicalCall call ->
                    containsReturn(call.receiver()) || anyContainsReturn(call.arguments());
            case CanonicalSend send ->
                    containsReturn(send.receiver()) || anyContainsReturn(send.arguments());
            case CanonicalSuperSend send -> anyContainsReturn(send.arguments());
            case CanonicalCompose compose -> containsReturn(compose.object());
            case CanonicalDerivedInequality inequality ->
                    containsReturn(inequality.left()) || containsReturn(inequality.right());
            case CanonicalIdentity identity ->
                    containsReturn(identity.left()) || containsReturn(identity.right());
            case CanonicalNotIdentity identity ->
                    containsReturn(identity.left()) || containsReturn(identity.right());
            case CanonicalIndexedAssign assign ->
                    containsReturn(assign.receiver())
                            || containsReturn(assign.index())
                            || containsReturn(assign.value());
            case CanonicalMapConstruction map ->
                    containsReturn(map.factory())
                            || map.entries().stream()
                                    .anyMatch(
                                            entry ->
                                                    containsReturn(entry.key())
                                                            || containsReturn(entry.value()));
            case CanonicalMember member -> containsReturn(member.receiver());
            case CanonicalObject object ->
                    object.parent().map(CanonicalReturnHomeAnalysis::containsReturn).orElse(false)
                            || containsReturn(object.body());
            case CanonicalSpread spread -> containsReturn(spread.expression());
            case CanonicalIntrinsic intrinsic -> false;
            case CanonicalLiteral literal -> false;
            case CanonicalLookup lookup -> false;
        };
    }

    private static boolean anyContainsReturn(List<CanonicalExpression> expressions) {
        for (CanonicalExpression expression : expressions) {
            if (containsReturn(expression)) {
                return true;
            }
        }
        return false;
    }
}

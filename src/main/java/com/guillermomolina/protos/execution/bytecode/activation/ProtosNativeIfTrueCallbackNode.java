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

import com.guillermomolina.protos.execution.ProtosInlineCallbackFrameBindings.PreparedInlineLiteralCall;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.util.List;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

/**
 * Specialized preparation for the selected canonical Boolean
 * callback. Ordinary callable selection remains authoritative
 * whenever compact B-prime admission is unavailable.
 */
@GenerateInline(false)
@GenerateUncached
abstract class ProtosNativeIfTrueCallbackNode extends Node {

    abstract PreparedInlineLiteralCall execute(
            Object caller,
            Object supplied,
            Object literalPlan,
            ProtosLanguageContext context);

    @NeverDefault
    public static ProtosNativeIfTrueCallbackNode create() {
        return ProtosNativeIfTrueCallbackNodeGen.create();
    }

    @Specialization
    static PreparedInlineLiteralCall withLiteralPlan(
            Object caller,
            Object supplied,
            ProtosClosureExecutionPlanCell literalPlan,
            ProtosLanguageContext context) {
        PreparedInlineLiteralCall direct =
                compactCanonicalIfTrue(
                        caller, supplied, literalPlan, context);

        return direct != null
                ? direct
                : prepareNativeIfTrueCallbackFallback(
                            caller, supplied);
    }

    @Fallback
    @InliningCutoff
    static PreparedInlineLiteralCall ordinaryCallback(
            Object caller,
            Object supplied,
            Object literalPlan,
            ProtosLanguageContext context) {
        return prepareNativeIfTrueCallbackFallback(
                        caller, supplied);
    }

    /**
     * Cold callback-invocation preparation when the compact B-prime
     * admission is not valid.
     *
     * <p>The selected callback still uses the complete ordinary call protocol.
     * Only its preparation is isolated; invocation, suspension, completion
     * and control transfer remain in the existing Bytecode DSL machinery.
     */
    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    static PreparedInlineLiteralCall prepareNativeIfTrueCallbackFallback(
            Object caller,
            Object supplied) {
        return ProtosClosureCallPreparation.prepareInlineLiteralCall(
                supplied,
                List.of(),
                ProtosBytecodeRootNode.PrepareSendArguments.exactCaller(caller));
    }

    /**
     * B-prime admission of an immediate literal true callback, or null.
     *
     * <p>A Closure delegating directly to the published frozen root Object
     * (D049) without a local call slot selects the canonical Object.call by
     * D013, so no call lookup is needed. Neither an outer NativeCall nor a rich
     * activation is constructed. The child's direct compact arguments borrow
     * provenance only when the compact caller is proven equivalent to its own
     * caller.
     */
    static PreparedInlineLiteralCall compactCanonicalIfTrue(
            Object caller,
            Object supplied,
            ProtosClosureExecutionPlanCell literalPlan,
            ProtosLanguageContext enteredContext) {
        if (!(supplied instanceof ProtosClosureValue closure)
                || closure.nativeBody().isPresent()
                || closure.returnHome().isEmpty()
                || closure.requiresContextLocalExecutionProjectionForRuntime()
                || closure.parent().orElse(null) != ProtosObjectValue.rootObject()
                || closure.hasLocalSlot("call")) {
            return null;
        }

        ProtosActivation provenance;
        if (caller instanceof ProtosActivation activation) {
            provenance = activation;
        } else if (caller instanceof Object[] compact
                && ProtosFrameArguments.isUnmaterializedCompactCall(compact)) {
            provenance =
                    ProtosFrameArguments.unmaterializedInheritedProvenanceCaller(
                            compact);
        } else {
            return null;
        }

        if (provenance == null) {
            return null;
        }

        /*
         * PERF041-E1: this cell is frozen before guest execution.
         * A Closure using exactly its template plan requires no
         * Context-owned projection when the entered language agrees.
         *
         * A distinct template or foreign language still takes the
         * authoritative taskOwnedBytecodePlan resolution below.
         */
        ProtosClosureExecutionPlan literalTemplate = literalPlan.plan();
        boolean localLanguage =
                enteredContext == null
                        || literalTemplate.language().orElseThrow()
                                == enteredContext.languageForRuntime();

        ProtosClosureExecutionPlan plan;
        if (localLanguage
                && closure.executionPlanForRuntimeInvocation()
                        == literalTemplate) {
            plan = literalTemplate;
        } else {
            plan = ProtosClosureCallPreparation.taskOwnedBytecodePlan(closure, enteredContext);
        }

        if (plan == null
                || (plan != literalTemplate
                        && plan.bytecodeActivationTargetForComposition()
                                != literalTemplate
                                        .bytecodeActivationTargetForComposition())) {
            return null;
        }

        /*
         * The preceding guards already proved:
         *  - the exact callback target;
         *  - a non-owning ReturnHome (closure.returnHome is present);
         *  - canonical Closure.call selection.
         *
         * PreparedInlineLiteralCall.direct therefore satisfies
         * admits(literalPlan) without a second admission check.
         */
        return PreparedInlineLiteralCall.direct(
                literalTemplate.bytecodeActivationTargetForComposition(),
                ProtosFrameArguments.compactDirectClosureCallZeroPrepared(
                        closure,
                        provenance,
                        closure.returnHome().orElseThrow()));
    }
}

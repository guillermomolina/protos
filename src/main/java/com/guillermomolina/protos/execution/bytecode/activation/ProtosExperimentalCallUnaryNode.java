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

import com.guillermomolina.protos.execution.ProtosClosureCallPreparation.DirectClosureExecutableFacts;
import com.guillermomolina.protos.execution.ProtosExperimentalCallDispatchers.ExperimentalFunctionDirectInvokeNode;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;

/**
 * I115-H4-B EXPERIMENTAL (GraalPy CallUnaryMethodNode.callPFunction): the
 * terminal Call1 of the lowering-admitted root of the focal {@code run}
 * shape, cached by the {@link
 * ProtosBytecodeRootNode.InvokeExperimentalClosureOneTail} operation, which
 * supplies the caller root, BytecodeNode and bytecode index.
 *
 * <p>Both tiers use the single B5 frame-argument contract. The direct tier
 * stages the B5+ header of an admitted terminal Call1 ({@link
 * ProtosFrameArguments#admittedTerminalClosureCallOneToInstall}, GraalPy
 * {@code PArguments.create(1)}) and calls the cached {@link DirectCallNode}
 * through {@link ExperimentalFunctionDirectInvokeNode}, which installs the
 * Closure, and the call context, which installs the caller slot. Its
 * admission is the per-receiver admission of the identity tier of {@link
 * ProtosBytecodeRootNode.InvokeClosureOneTail} (canonical {@code call}
 * selection, no local {@code call}, stable Context projection, unobservable
 * ReturnHome, entered Context and executable facts), plus the Closure's
 * code-stable Assumption; it does not depend on the callee root's shape or
 * on the callee's name, and no lowering fact certifies the callee. Its
 * outcome handling is that of the ordinary admitted entry: control
 * transfers cross, a null result is rejected, and a target without the
 * non-suspension proof transports a suspended callee. Every receiver the
 * direct tier does not admit reaches the generic tier, the ordinary D013
 * terminal Call1 fallback of {@code InvokeClosureOneTail}.
 *
 * <p>Remaining experimental adaptations: Python globals/builtins are
 * represented through Protos lexical authority (ReadGlobal); Python
 * function/method categories have no literal equivalents, so only
 * ProtosClosureValue receivers reach the direct tier; the generic tier
 * replaces the direct one instead of re-admitting a temporarily rejected
 * instance.
 */
@GenerateUncached
@GenerateInline(false)
abstract class ProtosExperimentalCallUnaryNode extends Node {

    /* GraalPy PythonOptions.CallSiteInlineCacheMaxDepth counterpart. */
    private static final int CALL_SITE_INLINE_CACHE_MAX_DEPTH = 3;

    abstract Object execute(
            VirtualFrame frame,
            ProtosBytecodeRootNode root,
            BytecodeNode bytecodeNode,
            int bytecodeIndex,
            Object receiver,
            Object supplied0);

    static int getCallSiteInlineCacheMaxDepth() {
        return CALL_SITE_INLINE_CACHE_MAX_DEPTH;
    }

    @Specialization(
            excludeForUncached = true,
            guards = {
                "receiver == cachedReceiver",
                "currentContext(inliningTarget) == cachedContext",
                "cachedContext != null",
                "cachedAdmitted",
                "cachedLocalCallAbsence != null",
                "cachedProjectionStability != null",
                "cachedFacts != null",
                "cachedHomeProven",
                "admittedTailTarget(cachedFacts)"
            },
            assumptions = {
                "cachedReceiver.codeStableAssumptionForRuntime()",
                "cachedFacts.rootCallStability()",
                "cachedLocalCallAbsence",
                "cachedProjectionStability"
            },
            limit = "getCallSiteInlineCacheMaxDepth()")
    static Object direct(
            VirtualFrame frame,
            ProtosBytecodeRootNode root,
            BytecodeNode bytecodeNode,
            int bytecodeIndex,
            ProtosClosureValue receiver,
            Object supplied0,
            @Bind Node inliningTarget,
            @Cached(value = "receiver", weak = true) ProtosClosureValue cachedReceiver,
            @Cached("currentContext(inliningTarget)") ProtosLanguageContext cachedContext,
            @Cached("isAdmittedInstance(cachedReceiver)") boolean cachedAdmitted,
            @Cached("localCallAbsenceOrNull(cachedReceiver)") Assumption cachedLocalCallAbsence,
            @Cached("projectionStabilityOrNull(cachedReceiver)") Assumption cachedProjectionStability,
            @Cached("establishFacts(cachedReceiver, cachedAdmitted, cachedContext)")
                    DirectClosureExecutableFacts cachedFacts,
            @Cached("stableUnobservableReturnHome(cachedReceiver, cachedFacts)")
                    boolean cachedHomeProven,
            @Cached("provenNonSuspending(cachedFacts)") boolean cachedProven,
            @Cached("create(cachedFacts.target())") DirectCallNode callNode,
            @Cached ExperimentalFunctionDirectInvokeNode invokeNode) {
        Object[] args = ProtosFrameArguments.admittedTerminalClosureCallOneToInstall(supplied0);
        Object outcome;
        try {
            outcome = invokeNode.execute(
                    frame, inliningTarget, root, callNode, cachedReceiver, args);
        } catch (ProtosBytecodeControlTransferException bridged) {
            throw bridged.transfer();
        }
        java.util.Objects.requireNonNull(outcome, "terminal Closure invocation returned null");
        if (cachedProven) {
            // The caller root's return epilogue projects a numeric carrier.
            return outcome;
        }
        if (ProtosTailCallContinuation.isSuspended(outcome)) {
            return ProtosTailCallContinuation.deferDirect(outcome, bytecodeNode, bytecodeIndex);
        }
        return ProtosNumericValueSupport.guestValue(outcome);
    }

    @Megamorphic
    @InliningCutoff
    @Specialization(replaces = "direct")
    static Object generic(
            VirtualFrame frame,
            ProtosBytecodeRootNode root,
            BytecodeNode bytecodeNode,
            int bytecodeIndex,
            Object receiver,
            Object supplied0,
            @Cached IndirectCallNode indirectCall) {
        return ProtosBytecodeRootNode.InvokeClosureOneTail.prepareAndEnter(
                receiver,
                root,
                ProtosBytecodeRootNode.InvokeClosureOneTail.callerReference(frame),
                supplied0,
                indirectCall,
                bytecodeNode,
                bytecodeIndex);
    }

    @NonIdempotent
    static ProtosLanguageContext currentContext(Node node) {
        return ProtosLanguageContext.current(node);
    }

    /*
     * Installation only: the admission predicates of the identity tier of
     * InvokeClosureOneTail, applied to the cached receiver. The admission is
     * cached as a boolean so that the weak receiver cache stays the only
     * reference to the Closure.
     */
    static boolean isAdmittedInstance(ProtosClosureValue receiver) {
        return ProtosBytecodeRootNode.InvokeClosureOneTail
                        .admittedInstanceUnderGuardedRootCallOrNull(receiver)
                == receiver;
    }

    static Assumption localCallAbsenceOrNull(ProtosClosureValue closure) {
        return ProtosBytecodeRootNode.InvokeClosureOneTail.localCallAbsenceOrNull(closure);
    }

    static Assumption projectionStabilityOrNull(ProtosClosureValue closure) {
        return ProtosBytecodeRootNode.InvokeClosureOneTail.projectionStabilityOrNull(closure);
    }

    static DirectClosureExecutableFacts establishFacts(
            ProtosClosureValue closure, boolean admitted, ProtosLanguageContext context) {
        return !admitted || context == null
                ? null
                : ProtosBytecodeRootNode.InvokeClosureOneTail.establishFacts(closure, context);
    }

    static boolean stableUnobservableReturnHome(
            ProtosClosureValue closure, DirectClosureExecutableFacts facts) {
        return ProtosBytecodeRootNode.InvokeClosureOneTail
                .stableUnobservableReturnHome(closure, facts);
    }

    static boolean admittedTailTarget(DirectClosureExecutableFacts facts) {
        return ProtosBytecodeRootNode.InvokeClosureOneTail.admittedTailTarget(facts.target());
    }

    static boolean provenNonSuspending(DirectClosureExecutableFacts facts) {
        return facts != null
                && ProtosBytecodeRootNode.InvokeClosureOneTail.provenNonSuspending(
                        facts.target());
    }
}

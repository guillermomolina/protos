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

import com.guillermomolina.protos.execution.ProtosExperimentalCallDispatchers.ExperimentalFunctionDirectInvokeNode;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.ForceQuickening;
import com.oracle.truffle.api.bytecode.OperationProxy;
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
 * shape, proxied as the {@code InvokeExperimentalClosureOneTail} operation.
 *
 * <p>The direct tier compares the cached Closure identity, the simple arity
 * and the installed Context identity, depends on the Closure's code-stable
 * Assumption, and calls the cached {@link DirectCallNode} through {@link
 * ExperimentalFunctionDirectInvokeNode} with the GraalPy unary layout
 * ({@link ProtosFrameArguments#experimentalGraalPyUnaryCall}). The generic
 * tier is the ordinary D013 terminal Call1 fallback of {@code
 * InvokeClosureOneTail}.
 *
 * <p>I115-H4-B EXPERIMENTAL. Accepted semantic divergences, all KNOWN /
 * ACCEPTED / NOT FIXED:
 * <ol>
 *   <li>Dynamic Object.call changes are ignored.</li>
 *   <li>Cross-Context Closure projection is ignored.</li>
 *   <li>The cached executable target may differ from a later dynamic
 *       resolution.</li>
 *   <li>Caller, Task, Prelude and ReturnHome transport is omitted or reduced
 *       (the caller is the raw caller frame arguments).</li>
 *   <li>ReturnHome observability is not checked.</li>
 *   <li>Suspension eligibility is not checked.</li>
 *   <li>Transferred control exceptions are not translated.</li>
 *   <li>Null results are not validated.</li>
 *   <li>No ProtosActivation is materialized on the direct path.</li>
 *   <li>The result is returned directly to the root epilogue, which does not
 *       project a primitive carrier.</li>
 *   <li>The experimental ABI differs from B5: a callee whose root was not
 *       lowered to read it (any root but an H3 recursive Integer root)
 *       misreads its frame arguments.</li>
 *   <li>Python globals/builtins are represented through Protos lexical
 *       authority (ReadGlobal).</li>
 *   <li>Python function/method categories have no literal equivalents; only
 *       ProtosClosureValue receivers reach the direct tier.</li>
 *   <li>Python FrameInfo and exception state have experimental Protos
 *       representations (ProtosExperimentalCalleeContext.FrameInfo and
 *       header slot 5).</li>
 *   <li>Automatic RootTag/RootBodyTag tagging may differ from the previous
 *       explicit tagging, and auxiliary roots now carry automatic tags.</li>
 *   <li>The primitive boxing-elimination configuration ({@code int} only)
 *       differs from the previous Protos configuration.</li>
 *   <li>The experimental root does not guarantee full semantic
 *       equivalence.</li>
 *   <li>Additional: the admission no longer requires the callee to be a
 *       certified H3 recursive binding, so any Closure bound to the focal
 *       name is entered with the experimental ABI; ReadGlobal's prelude fast
 *       path keys on the module authority identity, which does not change
 *       when the name is later defined in the module; the arity guard reads
 *       only the declared parameter count.</li>
 * </ol>
 */
@GenerateUncached
@GenerateInline(false)
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = true)
abstract class ProtosExperimentalCallUnaryNode extends Node {

    /* GraalPy PythonOptions.CallSiteInlineCacheMaxDepth counterpart. */
    private static final int CALL_SITE_INLINE_CACHE_MAX_DEPTH = 3;

    abstract Object execute(VirtualFrame frame, Object receiver, Object supplied0);

    static int getCallSiteInlineCacheMaxDepth() {
        return CALL_SITE_INLINE_CACHE_MAX_DEPTH;
    }

    @ForceQuickening
    @Specialization(
            excludeForUncached = true,
            guards = {
                "receiver == cachedReceiver",
                "isSimpleUnary(cachedReceiver)",
                "currentContext(inliningTarget) == cachedContext",
                "cachedTarget != null"
            },
            assumptions = "cachedReceiver.codeStableAssumptionForRuntime()",
            limit = "getCallSiteInlineCacheMaxDepth()")
    static Object direct(
            VirtualFrame frame,
            ProtosClosureValue receiver,
            Object supplied0,
            @Bind Node inliningTarget,
            @Cached(value = "receiver", weak = true) ProtosClosureValue cachedReceiver,
            @Cached("currentContext(inliningTarget)") ProtosLanguageContext cachedContext,
            @Cached("experimentalTargetOrNull(cachedReceiver)") RootCallTarget cachedTarget,
            @Cached("create(cachedTarget)") DirectCallNode callNode,
            @Cached ExperimentalFunctionDirectInvokeNode invokeNode) {
        Object[] args = ProtosFrameArguments.experimentalGraalPyUnaryCall();
        ProtosFrameArguments.setExperimentalUnaryArgument(args, 0, supplied0);
        return invokeNode.execute(frame, inliningTarget, callNode, cachedReceiver, args);
    }

    @Megamorphic
    @InliningCutoff
    @Specialization(replaces = "direct")
    static Object generic(
            VirtualFrame frame,
            Object receiver,
            Object supplied0,
            @Bind Node node,
            @Cached IndirectCallNode indirectCall) {
        BytecodeNode bytecodeNode = BytecodeNode.get(node);
        return ProtosBytecodeRootNode.InvokeClosureOneTail.prepareAndEnter(
                receiver,
                (ProtosBytecodeRootNode) bytecodeNode.getBytecodeRootNode(),
                ProtosBytecodeRootNode.InvokeClosureOneTail.callerReference(frame),
                supplied0,
                indirectCall,
                bytecodeNode,
                bytecodeNode.getBytecodeIndex(frame));
    }

    /* Installation only: whether the cached Closure declares exactly one parameter. */
    static boolean isSimpleUnary(ProtosClosureValue closure) {
        return closure.definition() != null
                && closure.definition().parameters().size() == 1;
    }

    @NonIdempotent
    static ProtosLanguageContext currentContext(Node node) {
        return ProtosLanguageContext.current(node);
    }

    /* Installation only: the prepared plan's Bytecode target, or null. */
    static RootCallTarget experimentalTargetOrNull(ProtosClosureValue closure) {
        ProtosClosureExecutionPlan plan = closure.preparedExecutionPlanOrNullForRuntime();
        if (plan == null) {
            return null;
        }
        return plan.bytecodeActivationTargetForComposition();
    }
}

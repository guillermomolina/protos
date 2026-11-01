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

import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedClosureCall;
import com.guillermomolina.protos.execution.ProtosClosureCallPreparation.DirectClosureExecutableFacts;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ForceQuickening;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;

/**
 * Fixed-arity unary callable invocation.
 *
 * A single direct specialization owns the source-function call site. Its
 * PIC is keyed by Closure identity (weakly cached, limit 3) under the
 * existing root-call, code and Context-projection assumptions. Saturation
 * replaces it with the generic specialization, which reports the
 * megamorphic transition to the runtime once per cached installation.
 * There is no definition-keyed direct specialization or lowering-time
 * recursion-specific call operation.
 */
@GenerateUncached
@GenerateInline(false)
@ConstantOperand(type = boolean.class, name = "terminal")
@OperationProxy.Proxyable(
        allowUncached = true,
        storeBytecodeIndex = true)
abstract class ProtosCallUnaryMethodNode extends Node {

    abstract Object execute(
            VirtualFrame frame, boolean terminal,
            Object callable, Object argument);

    /*
     * I115 (GraalPy CallUnaryMethodNode.callPFunction): the entry is keyed
     * by the identity of the concrete Closure object, held weakly. Under the
     * canonical root Object.call assumption only the instance check runs on
     * a hit, after the argument was evaluated, so a local call slot created
     * meanwhile is still observed.
     */
    @NonIdempotent
    static boolean directInstance(ProtosClosureValue callable) {
        return ProtosClosureCallPreparation
                .directClosureInstanceUnderGuardedRootCallOrNull(callable)
                == callable;
    }

    @ForceQuickening
    @Specialization(
            guards = {
                "terminal",
                "callable == cachedCallable",
                "enteredContext == cachedContext",
                "directInstance(callable)",
                "cachedUnobservableHome",
                "cachedFacts != null",
                "cachedTarget != null"
            },
            assumptions = {
                "rootCallStabilityOrNull(cachedFacts)",
                "cachedCodeStable",
                "cachedProjectionStable"
            },
            excludeForUncached = true,
            limit = "3")
    static Object callPFunction(
            VirtualFrame frame,
            boolean terminal,
            ProtosClosureValue callable,
            Object argument,
            @Bind("bytecodeOf($node)") BytecodeNode bytecodeNode,
            @Bind("currentContext($node)")
                    ProtosLanguageContext enteredContext,
            @Cached(value = "callable", weak = true)
                    ProtosClosureValue cachedCallable,
            @Cached("enteredContext")
                    ProtosLanguageContext cachedContext,
            @Cached("callable.hasUnobservableInvocationReturnHomeForRuntime()")
                    boolean cachedUnobservableHome,
            @Cached("callable.codeStableAssumptionForRuntime()")
                    Assumption cachedCodeStable,
            @Cached("callable.contextProjectionStabilityForRuntime()")
                    Assumption cachedProjectionStable,
            @Cached("factsFor(callable, enteredContext)")
                    DirectClosureExecutableFacts cachedFacts,
            @Cached("targetOrNull(cachedFacts)")
                    RootCallTarget cachedTarget,
            @Cached("createDirectCallNodeOrNull(cachedTarget)")
                    DirectCallNode call,
            @Cached(value = "calleeRootOrNull(cachedTarget)", adopt = false)
                    ProtosBytecodeRootNode cachedCallee,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {

        Object caller =
                ProtosBytecodeRootNode.InvokeClosureOneTail.callerReference(frame);

        /*
         * I115 (GraalPy CallContext.prepareCall): the deferred caller as is,
         * unless the callee root registered NEEDS_CALLER_PRELUDE, in which
         * case the caller's Prelude travels in a ProtosCallerFrameReference.
         */
        Object[] args =
                ProtosFrameArguments.compactAdmittedTerminalClosureCallOneNoHome(
                        callable,
                        cachedCallee == null
                                ? caller
                                : ProtosBytecodeRootNode.InvokeClosureOneTail.callerArgument(
                                        root, cachedCallee, callable, caller),
                        argument);

        Object outcome;
        try {
            outcome = call.call(args);
        } catch (ProtosBytecodeControlTransferException bridged) {
            throw bridged.transfer();
        }

        if (ProtosTailCallContinuation.isSuspended(outcome)) {
            return ProtosTailCallContinuation.deferDirect(
                    outcome, bytecodeNode, bytecodeNode.getBytecodeIndex(frame));
        }

        // The root epilogue owns guest-boundary projection.
        // Preserve primitive carriers for accepting callers.
        return outcome;
    }

    /*
     * replaces retires the identity PIC on saturation. GraalPy declares
     * @Megamorphic here; for this operation the generated polymorphism check
     * reads a state field (state_0_) the generated node does not have, so the
     * same runtime notification is sent through the explicit Truffle API:
     * reportMegamorphicInstallation runs once, as the initializer of this
     * specialization's instance in the cached interpreter.
     */
    @Specialization(replaces = "callPFunction")
    @InliningCutoff
    static Object call(
            VirtualFrame frame,
            boolean terminal,
            Object callable,
            Object argument,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Bind("bytecodeOf($node)") BytecodeNode bytecodeNode,
            @Cached IndirectCallNode indirectCall,
            @Cached(value = "reportMegamorphicInstallation($node)",
                    uncached = "false")
                    boolean megamorphicReported) {

        Object caller =
                ProtosBytecodeRootNode.InvokeClosureOneTail.callerReference(frame);

        PreparedClosureCall prepared =
                ProtosClosureCallPreparation.prepareClosureCallOneFallback(
                        ProtosNumericValueSupport.guestValue(callable),
                        ProtosBytecodeRootNode.InvokeClosureOneTail.exactCaller(
                                root, caller),
                        ProtosNumericValueSupport.guestValue(argument));

        if (!terminal) {
            return prepared;
        }

        return ProtosTailCallContinuation.executeTerminalPreparedCall(
                prepared,
                indirectCall,
                bytecodeNode,
                bytecodeNode.getBytecodeIndex(frame));
    }


    /*
     * Installation only. ProtosClosureCallPreparation owns
     * executable target selection and Context projection.
     */
    static DirectClosureExecutableFacts factsFor(
            ProtosClosureValue closure,
            ProtosLanguageContext context) {
        return ProtosClosureCallPreparation
                .establishDirectClosureExecutableFactsOrNull(
                        closure, context);
    }

    static RootCallTarget targetOrNull(
            DirectClosureExecutableFacts facts) {
        return facts == null ? null : facts.target();
    }

    static DirectCallNode createDirectCallNodeOrNull(
            RootCallTarget target) {
        return target == null ? null : DirectCallNode.create(target);
    }

    /*
     * Installation only. A rejected entry has no facts: the null assumption
     * is invalid for the DSL (Assumption.isValidAssumption), so the direct
     * entry is never installed and the call takes the generic fallback.
     */
    static Assumption rootCallStabilityOrNull(
            DirectClosureExecutableFacts facts) {
        return facts == null ? null : facts.rootCallStability();
    }

    /* Installation only: the target's root, whose caller needs are read. */
    static ProtosBytecodeRootNode calleeRootOrNull(RootCallTarget target) {
        return target != null
                        && target.getRootNode() instanceof ProtosBytecodeRootNode callee
                ? callee
                : null;
    }

    /*
     * Installation only. The uncached interpreter uses the constant uncached
     * value instead and has no specialization to split; the tier check keeps
     * any non-cached evaluation from reporting.
     */
    static boolean reportMegamorphicInstallation(Node node) {
        if (BytecodeNode.get(node).getTier() != BytecodeTier.CACHED) {
            return false;
        }
        node.reportPolymorphicSpecialize();
        return true;
    }

    static BytecodeNode bytecodeOf(Node node) {
        return BytecodeNode.get(node);
    }

    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode)
                BytecodeNode.get(node).getBytecodeRootNode();
    }

    @NonIdempotent
    static ProtosLanguageContext currentContext(Node node) {
        return ProtosLanguageContext.current(node);
    }
}

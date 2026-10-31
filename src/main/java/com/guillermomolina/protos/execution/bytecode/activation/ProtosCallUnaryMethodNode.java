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
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeNode;
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
 * A single direct specialization owns the source-function call site.
 * There is no separate definition-keyed direct specialization, terminal
 * PIC or lowering-time recursion-specific call operation.
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

        @NonIdempotent
        static boolean canonicalCallSelection(
                ProtosClosureValue callable) {
            return ProtosClosureCallPreparation
                    .directClosureCallSelectionWithoutPreludeOrNull(callable)
                    == callable;
        }

    @ForceQuickening
    @Specialization(
            guards = {
                "terminal",
                "canonicalCallSelection(callable)",
                "callable.hasUnobservableInvocationReturnHomeForRuntime()",
                "definition != null",
                "definition == cachedDefinition",
                "sourcePlan != null",
                "sourcePlan == cachedPlan",
                "enteredContext != null",
                "enteredContext == cachedContext",
                "projectionRequired == cachedProjection",
                "cachedFacts != null",
                "cachedTarget != null"
            },
            assumptions = "cachedFacts.rootCallStability()",
            excludeForUncached = true,
            limit = "3")
    static Object callPFunction(
            VirtualFrame frame,
            boolean terminal,
            ProtosClosureValue callable,
            Object argument,
            @Bind("bytecodeOf($node)") BytecodeNode bytecodeNode,
            @Bind("callable.definition()")
                    CanonicalClosure definition,
            @Bind("callable.preparedExecutionPlanOrNullForRuntime()")
                    ProtosClosureExecutionPlan sourcePlan,
            @Bind("currentContext($node)")
                    ProtosLanguageContext enteredContext,
            @Bind("callable.requiresContextLocalExecutionProjectionForRuntime()")
                    boolean projectionRequired,
            @Cached("definition")
                    CanonicalClosure cachedDefinition,
            @Cached("sourcePlan")
                    ProtosClosureExecutionPlan cachedPlan,
            @Cached("enteredContext")
                    ProtosLanguageContext cachedContext,
            @Cached("projectionRequired")
                    boolean cachedProjection,
            @Cached("factsFor(callable, enteredContext)")
                    DirectClosureExecutableFacts cachedFacts,
            @Cached("cachedFacts.target()")
                    RootCallTarget cachedTarget,
            @Cached("createDirectCallNodeOrNull(cachedTarget)")
                    DirectCallNode call) {

        Object caller =
                ProtosBytecodeRootNode.InvokeClosureOneTail.callerReference(frame);

        Object[] args =
                ProtosFrameArguments.compactAdmittedTerminalClosureCallOneNoHome(
                        callable, caller, argument);

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

    @Specialization
    @InliningCutoff
    static Object call(
            VirtualFrame frame,
            boolean terminal,
            Object callable,
            Object argument,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Bind("bytecodeOf($node)") BytecodeNode bytecodeNode,
            @Cached IndirectCallNode indirectCall) {

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

    static DirectCallNode createDirectCallNodeOrNull(
            RootCallTarget target) {
        return target == null ? null : DirectCallNode.create(target);
    }

    static RootCallTarget targetFor(
            ProtosClosureValue function,
            ProtosLanguageContext context) {
        if (function == null || function.nativeBody().isPresent()) {
            return null;
        }
        return ProtosGuardedSendSelection.fastOrdinarySendTarget(
                function, context);
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

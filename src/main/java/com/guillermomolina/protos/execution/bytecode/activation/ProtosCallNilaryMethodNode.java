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

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Protos Bytecode DSL: the {@code CallNilaryMethod} operation, a call
 * of an evaluated callable with 0 positional arguments. The callable is a source Closure, a
 * native Closure, a {@link ProtosBoundMethod} produced by {@code GetMethod}
 * (its receiver is not counted among the arguments) or any other object.
 *
 * <p>The B5 frame-argument array is built once here and becomes the callee
 * frame's arguments. A source function goes straight to the shared
 * function PIC; a bound source method writes its receiver and methodHome
 * and uses the same PIC; every other callable takes {@link
 * ProtosFunctionCallDispatchers.CallNode}. No call is prepared.
 */
@GenerateUncached
@GenerateInline(false)
@ConstantOperand(type = boolean.class, name = "terminal")
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = true)
abstract class ProtosCallNilaryMethodNode extends Node {

    static ProtosStandardObjectProtocol.CanonicalRootCallSelection
            rootCallSelection() {
        return ProtosStandardObjectProtocol
                .canonicalRootCallSelectionOrNullForRuntime();
    }

    abstract Object execute(VirtualFrame frame, boolean terminal, Object callable);

    @Specialization(guards = {
            "isSourceFunction(callable)",
            "canonicalCallSelected(callable, rootSelection)"
        })
    static Object callFunction(
            VirtualFrame frame,
            boolean terminal,
            ProtosClosureValue callable,
            @Bind("$node") Node inliningTarget,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached(value = "rootCallSelection()", allowUncached = true)
                    ProtosStandardObjectProtocol.CanonicalRootCallSelection rootSelection,
            @Cached ProtosFunctionCallDispatchers.FunctionCachedCallNode call) {
        return call.execute(
                frame, inliningTarget, terminal, callable,
                ProtosFrameArguments.dispatchedCall0(
                null, ProtosFrameArguments.functionCallMarker(), null, null, null),
                null, root,
                ProtosFunctionCallDispatchers.currentContext(inliningTarget));
    }

    @Specialization(guards = "isSourceFunction(method.callable())")
    static Object callBoundMethod(
            VirtualFrame frame,
            boolean terminal,
            ProtosBoundMethod method,
            @Bind("$node") Node inliningTarget,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached ProtosFunctionCallDispatchers.FunctionCachedCallNode call) {
        Object[] arguments = ProtosFrameArguments.dispatchedCall0(
                null, ProtosFrameArguments.functionCallMarker(), null, null, null);
        ProtosFrameArguments.bindMethod(arguments, method.receiver(), method.methodHome());
        return call.execute(
                frame, inliningTarget, terminal, method.callable(), arguments,
                null, root,
                ProtosFunctionCallDispatchers.currentContext(inliningTarget));
    }

    /* Builtins, bound builtins and generic callables: the shared CallNode. */
    /*
     * Source functions retain the specialized call path while their
     * selected call behavior is canonical. Bound methods are already
     * selected and must not perform another callable lookup.
     */
    static boolean requiresCallableSelection(Object callable) {
        return !isSourceCallable(callable)
                || (callable instanceof ProtosClosureValue closure
                        && !canonicalCallSelected(
                                closure, rootCallSelection()));
    }

    @Specialization(guards = "requiresCallableSelection(callable)")
    @InliningCutoff
    static Object call(
            VirtualFrame frame,
            boolean terminal,
            Object callable,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached ProtosFunctionCallDispatchers.CallNode call) {
        return call.execute(
                frame, terminal, callable,
                ProtosFrameArguments.dispatchedCall0(
                null, ProtosFrameArguments.functionCallMarker(), null, null, null),
                null, root);
    }

    static boolean canonicalCallSelected(
            ProtosClosureValue callable,
            ProtosStandardObjectProtocol.CanonicalRootCallSelection selection) {
        return ProtosFunctionCallDispatchers.hasCanonicalCallSelection(
                callable, selection);
    }

    static boolean isSourceFunction(ProtosClosureValue callable) {
        return ProtosFunctionCallDispatchers.CallNode.isSourceFunction(callable);
    }

    static boolean isSourceCallable(Object callable) {
        return callable instanceof ProtosClosureValue closure
                        ? isSourceFunction(closure)
                        : callable instanceof ProtosBoundMethod method
                                && isSourceFunction(method.callable());
    }

    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
    }
}

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

import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.StoreBytecodeIndex;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * The exact division operation {@code /}. Primitive specializations execute
 * only while the standard {@code /} is the behavior selected for the
 * original receiver under the caller's Prelude. The generic specialization
 * re-checks the selection: a standard selection completes with the existing
 * numeric kernels; any other selection invokes the selected implementation
 * through {@link ProtosNumericOverloadNode}, whose result (including a
 * suspended invocation) is the exact result of this operation.
 *
 * <p>The operation stores its result in the {@code outcome} local and
 * answers whether that result must be composed as a continuation.
 */
@GenerateUncached
@GenerateInline(false)
@ConstantOperand(type = boolean.class, name = "terminal")
@ConstantOperand(type = LocalAccessor.class, name = "outcome")
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = false)
abstract class ProtosNumberDivideNode extends Node {
    static final String SELECTOR = "/";

    abstract boolean execute(VirtualFrame frame, boolean terminal, LocalAccessor outcome,
            Object left, Object right);

    @Specialization(guards = "selection.execute(frame, SELECTOR, left)")
    static boolean doLL(VirtualFrame frame, boolean terminal, LocalAccessor outcome,
            long left, long right,
            @Bind Node node,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Shared("selection") @Cached ProtosNumericSelectionNode selection,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode) {
        if (right != 0L && (left != Long.MIN_VALUE || right != -1L)
                && left % right == 0L) {
            outcome.setLong(bytecode, frame, left / right);
            return false;
        }
        // No truncation: Fraction, MIN/-1 and zero take the exact factory path.
        return ProtosBytecodeRootNode.StoreAndTestContinuation.storeResult(
                outcome, divideIntegers(root, frame, node, left, right), frame, bytecode);
    }

    @Specialization(guards = "selection.execute(frame, SELECTOR, left)")
    static boolean doDD(VirtualFrame frame, boolean terminal, LocalAccessor outcome,
            double left, double right,
            @Shared("selection") @Cached ProtosNumericSelectionNode selection,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode) {
        outcome.setDouble(bytecode, frame, left / right);
        return false;
    }

    @Specialization(guards = "selection.execute(frame, SELECTOR, left)")
    static boolean doDL(VirtualFrame frame, boolean terminal, LocalAccessor outcome,
            double left, long right,
            @Shared("selection") @Cached ProtosNumericSelectionNode selection,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode) {
        outcome.setDouble(bytecode, frame, left / right);
        return false;
    }

    @Specialization(guards = "selection.execute(frame, SELECTOR, left)")
    static boolean doLD(VirtualFrame frame, boolean terminal, LocalAccessor outcome,
            long left, double right,
            @Shared("selection") @Cached ProtosNumericSelectionNode selection,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode) {
        outcome.setDouble(bytecode, frame, left / right);
        return false;
    }

    @Fallback
    @StoreBytecodeIndex
    @InliningCutoff
    static boolean doGeneric(VirtualFrame frame, boolean terminal, LocalAccessor outcome,
            Object left, Object right,
            @Bind Node node,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Shared("selection") @Cached ProtosNumericSelectionNode selection,
            @Cached ProtosNumericOverloadNode overload,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode) {
        if (!selection.execute(frame, SELECTOR, left)) {
            return ProtosBytecodeRootNode.StoreAndTestContinuation.storeResult(
                    outcome, overload.execute(frame, terminal, SELECTOR, left, right,
                    root, bytecode, bytecode.getBytecodeIndex(frame)), frame, bytecode);
        }
        Object receiver = ProtosNumericValueSupport.guestValue(left);
        Object argument = ProtosNumericValueSupport.guestValue(right);
        if (ProtosNumericValueSupport.isCurrentInteger(receiver)
                && ProtosNumericValueSupport.isCurrentInteger(argument)) {
            return ProtosBytecodeRootNode.StoreAndTestContinuation.storeResult(
                    outcome, divideIntegers(root, frame, node, receiver, argument),
                    frame, bytecode);
        }
        if (isFloatOperand(receiver) && isFloatOperand(argument)
                && (ProtosNumericValueSupport.isCurrentFloat(receiver)
                        || ProtosNumericValueSupport.isCurrentFloat(argument))) {
            return ProtosBytecodeRootNode.StoreAndTestContinuation.storeResult(
                    outcome, floatValue(receiver) / floatValue(argument), frame, bytecode);
        }
        return ProtosBytecodeRootNode.StoreAndTestContinuation.storeResult(
                outcome, ProtosNumericOperationSupport.arithmetic(root, frame, left, SELECTOR, right),
                frame, bytecode);
    }

    @InliningCutoff
    static Object divideIntegers(ProtosBytecodeRootNode root, VirtualFrame frame,
            Node node, Object left, Object right) {
        try {
            return ProtosNumberFactory.divide(ProtosNumberFactory.prelude(node, frame), left, right);
        } catch (ArithmeticException zeroDivisor) {
            throw ProtosNumericOperationSupport.unsupported(root, frame);
        }
    }

    private static boolean isFloatOperand(Object value) {
        return ProtosNumericValueSupport.isCurrentFloat(value)
                || ProtosNumericValueSupport.isIntegerInLongRange(value);
    }

    private static double floatValue(Object value) {
        return ProtosNumericValueSupport.isCurrentFloat(value)
                ? ProtosNumericValueSupport.currentFloatValue(value)
                : (double) ProtosNumericValueSupport.exactLong(value);
    }

    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
    }

    /* The bytecode node executing this operation, resolved from the proxied node. */
    static BytecodeNode bytecodeOf(Node node) {
        return BytecodeNode.get(node);
    }
}

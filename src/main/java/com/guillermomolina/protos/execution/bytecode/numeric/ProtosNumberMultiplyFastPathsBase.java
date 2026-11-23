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
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.StoreBytecodeIndex;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Primitive long and double multiplication. Every specialization executes only
 * while the behavior selected for {@code *} on the original
 * receiver under the caller's Prelude is the standard operator; any other
 * selection falls to the generic specialization of the proxied subclass.
 * Signed-64 overflow promotes exactly.
 */
@GenerateCached(false)
abstract class ProtosNumberMultiplyFastPathsBase extends Node {

    static final String SELECTOR = "*";

    abstract boolean execute(VirtualFrame frame, boolean terminal, LocalAccessor outcome,
            LocalAccessor commitCell, boolean commitToCell,
            Object left, Object right);

    @Specialization(guards = "selection.execute(frame, SELECTOR, left)",
            rewriteOn = ArithmeticException.class)
    @StoreBytecodeIndex
    static boolean doLL(VirtualFrame frame, boolean terminal, LocalAccessor outcome,
            LocalAccessor commitCell, boolean commitToCell,
            long left, long right,
            @Shared("selection") @Cached ProtosNumericSelectionNode selection,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode) {
        outcome.setLong(bytecode, frame, Math.multiplyExact(left, right));
        if (commitToCell) {
            ProtosBytecodeRootNode.StoreAndTestContinuation.commitCompletedCell(
                    commitCell, outcome, frame, bytecode);
        }
        return false;
    }

    @Specialization(guards = "selection.execute(frame, SELECTOR, left)")
    @StoreBytecodeIndex
    static boolean doDD(VirtualFrame frame, boolean terminal, LocalAccessor outcome,
            LocalAccessor commitCell, boolean commitToCell,
            double left, double right,
            @Shared("selection") @Cached ProtosNumericSelectionNode selection,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode) {
        outcome.setDouble(bytecode, frame, left * right);
        if (commitToCell) {
            ProtosBytecodeRootNode.StoreAndTestContinuation.commitCompletedCell(
                    commitCell, outcome, frame, bytecode);
        }
        return false;
    }

    @Specialization(guards = "selection.execute(frame, SELECTOR, left)")
    @StoreBytecodeIndex
    static boolean doDL(VirtualFrame frame, boolean terminal, LocalAccessor outcome,
            LocalAccessor commitCell, boolean commitToCell,
            double left, long right,
            @Shared("selection") @Cached ProtosNumericSelectionNode selection,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode) {
        outcome.setDouble(bytecode, frame, left * right);
        if (commitToCell) {
            ProtosBytecodeRootNode.StoreAndTestContinuation.commitCompletedCell(
                    commitCell, outcome, frame, bytecode);
        }
        return false;
    }

    @Specialization(guards = "selection.execute(frame, SELECTOR, left)")
    @StoreBytecodeIndex
    static boolean doLD(VirtualFrame frame, boolean terminal, LocalAccessor outcome,
            LocalAccessor commitCell, boolean commitToCell,
            long left, double right,
            @Shared("selection") @Cached ProtosNumericSelectionNode selection,
            @Bind("bytecodeOf($node)") BytecodeNode bytecode) {
        outcome.setDouble(bytecode, frame, left * right);
        if (commitToCell) {
            ProtosBytecodeRootNode.StoreAndTestContinuation.commitCompletedCell(
                    commitCell, outcome, frame, bytecode);
        }
        return false;
    }

    static Object multiplyOrPromote(VirtualFrame frame, Node node, long left, long right) {
        long result = left * right;
        if (((Math.abs(left) | Math.abs(right)) >>> 31) != 0 && ((right != 0L && result / right != left) || (left == Long.MIN_VALUE && right == -1L))) {
            return ProtosNumberFactory.multiply(ProtosNumberFactory.prelude(node, frame), left, right);
        }
        return result;
    }

    /**
     * The admitted standard operand domains not covered by a primitive
     * specialization: two semantic Integers of any representation use the
     * exact factory of the caller's Prelude; other domains use the existing
     * standard numeric kernels. Only a standard selection reaches here.
     */
    static Object genericMultiply(ProtosBytecodeRootNode root, VirtualFrame frame,
            Node node, Object left, Object right) {
        Object receiver = ProtosNumericValueSupport.guestValue(left);
        Object argument = ProtosNumericValueSupport.guestValue(right);
        if (ProtosNumericValueSupport.isCurrentInteger(receiver)
                && ProtosNumericValueSupport.isCurrentInteger(argument)) {
            return ProtosNumberFactory.multiply(ProtosNumberFactory.prelude(node, frame), receiver, argument);
        }
        return ProtosNumericOperationSupport.arithmetic(root, frame, left, SELECTOR, right);
    }

    /*
     * The Protos root executing this operation. In a proxied node the
     * default root bind is typed Node, so it is resolved from the enclosing
     * BytecodeNode.
     */
    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
    }

    /* The bytecode node executing this operation, resolved from the proxied node. */
    static BytecodeNode bytecodeOf(Node node) {
        return BytecodeNode.get(node);
    }
}

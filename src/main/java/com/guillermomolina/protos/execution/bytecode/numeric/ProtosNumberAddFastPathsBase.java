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
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Primitive long and double addition. Signed-64 overflow promotes exactly;
 * selection is guarded by the lowering before this operation executes.
 */
@GenerateCached(false)
abstract class ProtosNumberAddFastPathsBase extends Node {

    abstract Object execute(VirtualFrame frame, Object left, Object right);

    @Specialization(rewriteOn = ArithmeticException.class)
    static long doLL(long left, long right) {
        return Math.addExact(left, right);
    }

    @Specialization
    static double doDD(double left, double right) {
        return left + right;
    }

    @Specialization
    static double doDL(double left, long right) {
        return left + right;
    }

    @Specialization
    static double doLD(long left, double right) {
        return left + right;
    }

    /*
     * doLLOvf (declared by each proxied subclass, which can bind its root):
     * the long sum, or on signed-64 overflow the exact large Integer of the
     * caller's Prelude.
     */
    static Object addOrPromote(VirtualFrame frame, Node node, long left, long right) {
        long sum = left + right;
        if (((left ^ sum) & (right ^ sum)) >= 0L) {
            return sum;
        }
        return ProtosNumberFactory.add(ProtosNumberFactory.prelude(node, frame), left, right);
    }

    /* Signed-64 overflow: the exact large Integer of the caller's Prelude. */

    /**
     * The single generic route of both operations: two semantic Integers
     * (any representation, long carriers included) are added exactly
     * without promotion loss. Other admitted standard operand domains use
     * the existing numeric kernels without sending a message.
     *
     * <p>NumberAdd is emitted only behind the {@code IsStandardNumericSelection}
     * admission; a receiver with any other selected {@code +} is dispatched
     * by the lowering.
     */
    static Object genericAdd(ProtosBytecodeRootNode root, VirtualFrame frame,
            Node node, Object left, Object right) {
        Object receiver = ProtosNumericValueSupport.guestValue(left);
        Object argument = ProtosNumericValueSupport.guestValue(right);
        if (ProtosNumericValueSupport.isCurrentInteger(receiver)
                && ProtosNumericValueSupport.isCurrentInteger(argument)) {
            return ProtosNumberFactory.add(ProtosNumberFactory.prelude(node, frame), receiver, argument);
        }
        return ProtosNumericOperationSupport.arithmetic(root, frame, left, "+", right);
    }


    /*
     * the Protos root executing this operation. In a proxied
     * node the default root bind is typed Node, so it is resolved from the
     * enclosing BytecodeNode.
     */
    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
    }
}

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
 * Primitive long and double subtraction, with exact promotion on signed-64
 * overflow. Only an admitted standard selection reaches these specializations.
 */
@GenerateCached(false)
abstract class ProtosNumberSubtractFastPathsBase extends Node {

    abstract Object execute(VirtualFrame frame, Object left, Object right);

    @Specialization(rewriteOn = ArithmeticException.class)
    static long doLL(long left, long right) {
        return Math.subtractExact(left, right);
    }

    @Specialization
    static double doDD(double left, double right) {
        return left - right;
    }

    @Specialization
    static double doDL(double left, long right) {
        return left - right;
    }

    @Specialization
    static double doLD(long left, double right) {
        return left - right;
    }

    static Object subtractOrPromote(VirtualFrame frame, Node node, long left, long right) {
        long result = left - right;
        if (((left ^ right) & (left ^ result)) < 0L) {
            return ProtosNumberFactory.subtract(ProtosNumberFactory.prelude(node, frame), left, right);
        }
        return result;
    }

    /** Complete the admitted standard operand domains using exact numeric kernels. */
    static Object genericSubtract(ProtosBytecodeRootNode root, VirtualFrame frame,
            Node node, Object left, Object right) {
        Object receiver = ProtosNumericValueSupport.guestValue(left);
        Object argument = ProtosNumericValueSupport.guestValue(right);
        if (ProtosNumericValueSupport.isCurrentInteger(receiver)
                && ProtosNumericValueSupport.isCurrentInteger(argument)) {
            return ProtosNumberFactory.subtract(ProtosNumberFactory.prelude(node, frame), receiver, argument);
        }
        return ProtosNumericOperationSupport.arithmetic(root, frame, left, "-", right);
    }


    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
    }
}

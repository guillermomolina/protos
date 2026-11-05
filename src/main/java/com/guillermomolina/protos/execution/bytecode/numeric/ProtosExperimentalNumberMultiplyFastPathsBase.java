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
 * I115-LONG-4: GraalPy PyNumberMultiplyFastPathsBase form without a 32-bit Integer
 * layer. There are no Protos numeric admission certificates or carrier flags.
 * On overflow the result is promoted to the exact large Integer, cold.
 */
@GenerateCached(false)
abstract class ProtosExperimentalNumberMultiplyFastPathsBase extends Node {

    abstract Object execute(VirtualFrame frame, Object left, Object right);

    @Specialization(rewriteOn = ArithmeticException.class)
    static long doLL(long left, long right) {
        return Math.multiplyExact(left, right);
    }

    @Specialization
    static double doDD(double left, double right) {
        return left * right;
    }

    @Specialization
    static double doDL(double left, long right) {
        return left * right;
    }

    @Specialization
    static double doLD(long left, double right) {
        return left * right;
    }

    static Object multiplyOrPromote(Node node, long left, long right) {
        long result = left * right;
        if (((Math.abs(left) | Math.abs(right)) >>> 31) != 0 && ((right != 0L && result / right != left) || (left == Long.MIN_VALUE && right == -1L))) {
            return ProtosNumberFactory.multiply(node, left, right);
        }
        return result;
    }

    /** The generic result: exact Integer arithmetic, else the ordinary '*' send. */
    static Object genericMultiply(ProtosBytecodeRootNode root, VirtualFrame frame,
            Node node, Object left, Object right) {
        Object receiver = ProtosNumericValueSupport.guestValue(left);
        Object argument = ProtosNumericValueSupport.guestValue(right);
        if (ProtosNumericValueSupport.isCurrentInteger(receiver)
                && ProtosNumericValueSupport.isCurrentInteger(argument)) {
            return ProtosNumberFactory.multiply(node, receiver, argument);
        }
        return ProtosFunctionCallDispatchers.sendOne(
                root, ProtosFunctionCallDispatchers.callerReference(frame),
                false, receiver, "*", argument);
    }


    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
    }
}

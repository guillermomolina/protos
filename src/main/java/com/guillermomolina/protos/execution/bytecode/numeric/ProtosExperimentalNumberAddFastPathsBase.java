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

// PRUIBA DE I115, en algún momento esto volverá a funcionar
// Signed-64 semantic Integers use Long directly, not ProtosIntegerValue.

// PRUIBA DE I115, en algún momento esto volverá a funcionar
// import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * I115-H7 EXPERIMENTAL (GraalPy {@code PyNumberAddFastPathsBase}): the
 * primitive fast paths shared by {@code NumberAdd} and {@code
 * NumberInPlaceAdd}. Each subclass declares its own generic fallback.
 *
 * <ul>
 *   <li>{@code doLL}: {@code long + long} with {@link Math#addExact};
 *       rewritten on overflow into {@code doLLOvf} (declared by each
 *       subclass), which promotes to the exact large Integer.</li>
 *   <li>{@code double} specializations.</li>
 * </ul>
 *
 * <p>DIFFERENCE FROM GRAALPY: the signed-64 Integer representation is a
 * Java {@code long} and the Float representation a Java {@code double};
 * there is no 32-bit primitive integer level, hence no {@code doII}/{@code
 * doIIOvf} and no implicit {@code int -> long} type-system cast.
 *
 * <p>RECORDED DIVERGENCE: no Prelude numeric-protocol admission certificate
 * is consulted; the standard Integer/Float {@code +} is assumed.
 */
@GenerateCached(false)
abstract class ProtosExperimentalNumberAddFastPathsBase extends Node {

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
     * without promotion loss; any other pair performs the ordinary
     * {@code +} send, which selects the standard behavior.
     *
     * <p>NumberAdd is emitted only behind the {@code IsStandardAddSelection}
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
        return ProtosI115DirectNumericFallback.send(root, frame, left, "+", right);
    }


    /*
     * I115-H7-R1: the Protos root executing this operation. In a proxied
     * node the default root bind is typed Node, so it is resolved from the
     * enclosing BytecodeNode.
     */
    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
    }
}

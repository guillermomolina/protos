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

import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
 *   <li>{@code doII}: {@code int + int} with {@link Math#addExact}, an
 *       {@code int} result; the first overflow rewrites it into {@code
 *       doIIOvf}, whose {@code long} result cannot overflow.</li>
 *   <li>{@code doLL}: {@code long + long} with {@link Math#addExact};
 *       rewritten on overflow into {@code doLLOvf} (declared by each
 *       subclass), which promotes to the exact large Integer.</li>
 *   <li>{@code double} specializations.</li>
 * </ul>
 *
 * <p>DIFFERENCE FROM GRAALPY: Protos has no implicit {@code int -> long}
 * cast in this interpreter's type system, so {@code doLL} does not replace
 * {@code doII} and mixed int/long pairs reach the generic fallback.
 *
 * <p>RECORDED DIVERGENCE: no Prelude numeric-protocol admission certificate
 * is consulted; the standard Integer/Float {@code +} is assumed.
 */
@GenerateCached(false)
abstract class ProtosExperimentalNumberAddFastPathsBase extends Node {

    abstract Object execute(VirtualFrame frame, Object left, Object right);

    @Specialization(rewriteOn = ArithmeticException.class)
    static int doII(int left, int right) {
        return Math.addExact(left, right);
    }

    @Specialization(replaces = "doII")
    static long doIIOvf(int left, int right) {
        return (long) left + (long) right;
    }

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
     * The caller prelude of the running root, read as every send selection
     * of a compact root reads it (registering NEEDS_CALLER_PRELUDE once when
     * an inherited prelude was not transported).
     */
    static ProtosPrelude callerPrelude(ProtosBytecodeRootNode root, VirtualFrame frame) {
        return ProtosBytecodeRootNode.InvokeClosureOneTail.admittedCallerPrelude(
                root, ProtosExperimentalCallBinaryMethodNode.callerReference(frame));
    }

    /*
     * doLLOvf (declared by each proxied subclass, which can bind its root):
     * the long sum, or on signed-64 overflow the exact large Integer of the
     * caller's Prelude.
     */
    static Object addOrPromote(
            ProtosBytecodeRootNode root, VirtualFrame frame, long left, long right) {
        long sum = left + right;
        if (((left ^ sum) & (right ^ sum)) >= 0L) {
            return sum;
        }
        return promote(callerPrelude(root, frame), left, right);
    }

    /* Signed-64 overflow: the exact large Integer of the caller's Prelude. */
    @TruffleBoundary
    static Object promote(ProtosPrelude prelude, long left, long right) {
        return ProtosNumericValueSupport.addIntegers(
                new ProtosIntegerValue(left), new ProtosIntegerValue(right), prelude);
    }

    /**
     * The single generic route of both operations: two semantic Integers
     * (any representation, int/long carriers included) are added exactly
     * without promotion loss; any other pair receives the ordinary send of
     * {@code +} (so a non-numeric receiver keeps its own {@code +}).
     */
    static Object genericAdd(
            ProtosBytecodeRootNode root, VirtualFrame frame, Object left, Object right) {
        Object receiver = ProtosNumericValueSupport.guestValue(left);
        Object argument = ProtosNumericValueSupport.guestValue(right);
        if (ProtosNumericValueSupport.isCurrentInteger(receiver)
                && ProtosNumericValueSupport.isCurrentInteger(argument)) {
            return addIntegers(receiver, argument, callerPrelude(root, frame));
        }
        return ProtosExperimentalCallBinaryMethodNode.sendOne(
                root,
                ProtosExperimentalCallBinaryMethodNode.callerReference(frame),
                false,
                receiver,
                "+",
                argument);
    }

    @TruffleBoundary
    private static Object addIntegers(Object left, Object right, ProtosPrelude prelude) {
        return ProtosNumericValueSupport.addIntegers(left, right, prelude);
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

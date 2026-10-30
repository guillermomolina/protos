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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * I115-H7 EXPERIMENTAL, proxied as the {@code Lt} operation (GraalPy
 * {@code Lt}): a compact {@code <} consumed as a Java {@code boolean} by a
 * Bytecode {@code While} condition. Primitive pairs compare directly and
 * produce no {@link ProtosBooleanValue}; every other pair takes the single
 * generic route inside this node: exact Integer order, otherwise the
 * ordinary send of {@code <}, whose result must be canonical {@code true} or
 * {@code false} (the standard whileTrue condition Error otherwise).
 *
 * <p>RECORDED DIVERGENCE: no Prelude numeric-protocol admission certificate
 * is consulted for primitive pairs.
 */
@GenerateUncached
@GenerateInline(false)
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = false)
abstract class ProtosExperimentalLessNode extends Node {

    abstract boolean execute(VirtualFrame frame, Object left, Object right);

    @Specialization
    static boolean doII(int left, int right) {
        return left < right;
    }

    @Specialization
    static boolean doLL(long left, long right) {
        return left < right;
    }

    @Specialization
    static boolean doIL(int left, long right) {
        return left < right;
    }

    @Specialization
    static boolean doLI(long left, int right) {
        return left < right;
    }

    @Specialization
    static boolean doDD(double left, double right) {
        return left < right;
    }

    @Fallback
    @InliningCutoff
    static boolean doGeneric(
            VirtualFrame frame,
            Object left,
            Object right,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        Object receiver = ProtosNumericValueSupport.guestValue(left);
        Object argument = ProtosNumericValueSupport.guestValue(right);
        if (bothIntegers(receiver, argument)) {
            return compareIntegers(receiver, argument) < 0;
        }
        Object caller = ProtosExperimentalCallBinaryMethodNode.callerReference(frame);
        Object result = ProtosExperimentalCallBinaryMethodNode.sendOne(
                root, caller, false, receiver, "<", argument);
        return canonicalCondition(root, caller, result);
    }

    private static boolean bothIntegers(Object receiver, Object argument) {
        return ProtosNumericValueSupport.isCurrentInteger(receiver)
                && ProtosNumericValueSupport.isCurrentInteger(argument);
    }

    @TruffleBoundary
    private static int compareIntegers(Object receiver, Object argument) {
        return ProtosNumericValueSupport.compareIntegers(receiver, argument);
    }

    /**
     * The whileTrue condition rule (PreparedWhileCall.conditionResult, as
     * ExperimentalCanonicalBooleanCondition): exactly canonical {@code true}
     * or {@code false}, no truthiness; any other value signals the fresh
     * standard {@code Error} at the exact caller activation (registering this
     * root's NEEDS_EXACT_ACTIVATION).
     */
    static boolean canonicalCondition(
            ProtosBytecodeRootNode root, Object caller, Object result) {
        if (result instanceof ProtosBooleanValue condition) {
            return condition.value();
        }
        ProtosActivation activation =
                ProtosBytecodeRootNode.InvokeClosureOneTail.exactCaller(root, caller);
        throw ProtosCoreErrors.signal(activation, ProtosCoreErrors.newError(activation));
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

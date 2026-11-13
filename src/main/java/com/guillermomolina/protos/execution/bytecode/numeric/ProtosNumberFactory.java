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

import java.math.BigInteger;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.bytecode.BytecodeNode;

/**
 * Exact numeric construction for the Core of the executing invocation.
 * Primitive non-overflow arithmetic never enters this cold factory. The frame
 * header supplies ownership, independently of which Core first entered a Context.
 */
final class ProtosNumberFactory {
    private ProtosNumberFactory() {}

    static ProtosPrelude prelude(Node node, VirtualFrame frame) {
        ProtosBytecodeRootNode root =
                (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
        root.requestCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE);
        ProtosPrelude prelude = ProtosFrameArguments.admittedPreludeOrNull(frame.getArguments());
        if (prelude == null) {
            throw new IllegalStateException("numeric construction requires the executing Core");
        }
        return prelude;
    }

    @TruffleBoundary
    static Object add(ProtosPrelude prelude, long left, long right) {
        return ProtosNumericValueSupport.integerFromHost(
                BigInteger.valueOf(left).add(BigInteger.valueOf(right)), prelude);
    }

    @TruffleBoundary
    static Object subtract(ProtosPrelude prelude, long left, long right) {
        return ProtosNumericValueSupport.integerFromHost(
                BigInteger.valueOf(left).subtract(BigInteger.valueOf(right)), prelude);
    }

    @TruffleBoundary
    static Object multiply(ProtosPrelude prelude, long left, long right) {
        return ProtosNumericValueSupport.integerFromHost(
                BigInteger.valueOf(left).multiply(BigInteger.valueOf(right)), prelude);
    }

    @TruffleBoundary
    static Object add(ProtosPrelude prelude, Object left, Object right) {
        return ProtosNumericValueSupport.addIntegers(left, right, prelude);
    }

    @TruffleBoundary
    static Object subtract(ProtosPrelude prelude, Object left, Object right) {
        return ProtosNumericValueSupport.subtractIntegers(left, right, prelude);
    }

    @TruffleBoundary
    static Object multiply(ProtosPrelude prelude, Object left, Object right) {
        return ProtosNumericValueSupport.multiplyIntegers(left, right, prelude);
    }

    /**
     * I115: the exact D197-C quotient of two semantic Integers (Integer or
     * Fraction). Throws {@link ArithmeticException} for a zero divisor; the
     * caller signals the guest Error.
     */
    @TruffleBoundary
    static Object divide(ProtosPrelude prelude, Object left, Object right) {
        return ProtosNumericValueSupport.divideIntegersExactly(left, right, prelude);
    }
}

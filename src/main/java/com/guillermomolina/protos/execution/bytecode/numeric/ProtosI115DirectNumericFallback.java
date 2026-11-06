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

import com.guillermomolina.protos.execution.ProtosStandardNumberOrderingProtocol.Relation;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * I115: the synchronous cold paths shared by the direct numeric operations
 * ({@code RichCompare*}, {@code NumberDivide}, {@code NumberAdd},
 * {@code NumberSubtract}, {@code NumberMultiply}).
 *
 * <p>KNOWN I115 TEMPORARY SEMANTIC DIVERGENCE: these intrinsic operations no
 * longer select a program-redefined {@code <}, {@code <=}, {@code >},
 * {@code >=}, {@code +}, {@code -}, {@code *} or {@code /} method. Numeric
 * operands use the trusted standard Number relations and arithmetic; any
 * other operand pair signals a guest Error instead of sending the selector.
 */
final class ProtosI115DirectNumericFallback {
    private ProtosI115DirectNumericFallback() {}

    /** The canonical Boolean of {@code relation} for any two operands, cold. */
    static Object compare(ProtosBytecodeRootNode root, VirtualFrame frame,
            Object left, Object right, Relation relation) {
        return compareCold(
                ProtosNumericValueSupport.guestValue(left),
                ProtosNumericValueSupport.guestValue(right),
                relation,
                root,
                ProtosFunctionCallDispatchers.callerReference(frame));
    }

    @TruffleBoundary
    private static Object compareCold(Object left, Object right, Relation relation,
            ProtosBytecodeRootNode root, Object caller) {
        if (!ProtosNumericValueSupport.isCurrentRealNumber(left)
                || !ProtosNumericValueSupport.isCurrentRealNumber(right)) {
            throw unsupported(root, caller);
        }
        return ProtosBooleanValue.of(
                relation.holds(ProtosStandardNumberOrderingProtocol.compare(left, right)));
    }

    /** Signals the guest Error of an unsupported numeric operand pair. */
    static ProtosSignalException unsupported(ProtosBytecodeRootNode root, VirtualFrame frame) {
        return unsupported(root, ProtosFunctionCallDispatchers.callerReference(frame));
    }

    @TruffleBoundary
    static ProtosSignalException unsupported(ProtosBytecodeRootNode root, Object caller) {
        ProtosActivation activation = ProtosFunctionCallDispatchers.exactCaller(root, caller);
        throw ProtosCoreErrors.signal(activation, ProtosCoreErrors.newError(activation));
    }
}

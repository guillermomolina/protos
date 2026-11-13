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
 * The synchronous cold paths shared by the direct numeric operations
 * ({@code RichCompare*}, {@code NumberDivide}, {@code NumberAdd},
 * {@code NumberSubtract}, {@code NumberMultiply}).
 *
 * <p>An operand pair that no specialization handles performs the ordinary
 * send of the operator selector (D013): a receiver that selects a redefined
 * operator receives that method, and a standard receiver receives the
 * standard method, whose behavior covers every remaining operand pair. The
 * operation is synchronous, so a suspension of the selected method is
 * rejected with the standard Error.
 */
final class ProtosI115DirectNumericFallback {
    private ProtosI115DirectNumericFallback() {}

    /**
     * The result of {@code relation} for any two operands, cold: the canonical
     * Boolean of two real Numbers, otherwise the ordinary send of the
     * relation's selector.
     */
    static Object compare(ProtosBytecodeRootNode root, VirtualFrame frame,
            Object left, Object right, Relation relation) {
        Object result = compareCold(
                ProtosNumericValueSupport.guestValue(left),
                ProtosNumericValueSupport.guestValue(right),
                relation);
        if (result == null) {
            return send(root, frame, left, selector(relation), right);
        }
        return result;
    }

    private static String selector(Relation relation) {
        return switch (relation) {
            case LESS -> "<";
            case LESS_EQUAL -> "<=";
            case GREATER -> ">";
            case GREATER_EQUAL -> ">=";
        };
    }

    /** The ordinary synchronous send {@code left.selector(right)} made by this frame. */
    static Object send(ProtosBytecodeRootNode root, VirtualFrame frame,
            Object left, String selector, Object right) {
        root.requestCallerNeed(
                ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION);
        return ProtosFunctionCallDispatchers.sendOne(
                root, ProtosFrameArguments.activation(frame), false, left, selector, right);
    }

    @TruffleBoundary
    private static Object compareCold(Object left, Object right, Relation relation) {
        if (!ProtosNumericValueSupport.isCurrentRealNumber(left)
                || !ProtosNumericValueSupport.isCurrentRealNumber(right)) {
            return null;
        }
        return ProtosBooleanValue.of(
                relation.holds(ProtosStandardNumberOrderingProtocol.compare(left, right)));
    }

    /** Signals the guest Error of an unsupported numeric operand pair. */
    static ProtosSignalException unsupported(ProtosBytecodeRootNode root, VirtualFrame frame) {
        root.requestCallerNeed(
                ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION);
        return unsupported(root, ProtosFrameArguments.activation(frame));
    }

    @TruffleBoundary
    static ProtosSignalException unsupported(ProtosBytecodeRootNode root, Object caller) {
        ProtosActivation activation = ProtosFunctionCallDispatchers.exactCaller(root, caller);
        throw ProtosCoreErrors.signal(activation, ProtosCoreErrors.newError(activation));
    }
}

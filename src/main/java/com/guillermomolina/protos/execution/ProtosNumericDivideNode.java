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

import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;

/**
 * PERF040-E1: the primitive numeric operation of a '/' send site.
 *
 * <p>The positive specializations are the certified Integer and Float
 * receiver lanes: an integral signed-64 Integer quotient stays primitive,
 * while a Fraction, a zero divisor or the signed-64 promotion returns {@code
 * MISS} from the Integer lane. {@link #ordinaryMiss} is guarded by exactly
 * their complement and returns {@code MISS}, so the site keeps its unchanged
 * ordinary send; it replaces nothing, and no arrival order of operand
 * families can retire a numeric lane.
 */
@GenerateInline(false)
@GenerateUncached
@OperationProxy.Proxyable(
        allowUncached = true,
        storeBytecodeIndex = false)
abstract class ProtosNumericDivideNode extends ProtosPrimitiveNumericSendFastPathsBase {
    static final int INTEGER_LANE = ProtosNumericCoreAdmission.INTEGER_DIVIDE;
    static final int FLOAT_LANE = ProtosNumericCoreAdmission.FLOAT_DIVIDE;

    abstract Object execute(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult);

    @NeverDefault
    public static ProtosNumericDivideNode create() {
        return ProtosNumericDivideNodeGen.create();
    }

    @Specialization(
            guards = {
                "smallInteger(receiver, receiverCarrier)",
                "smallInteger(argument, argumentCarrier)",
                "admits(prelude, INTEGER_LANE)"})
    static Object integers(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return integerQuotient(
                receiver, argument, foldedArithmetic, staticFoldResult);
    }

    @Specialization(
            guards = {
                "smallInteger(receiver, receiverCarrier)",
                "smallFloat(argument, argumentCarrier)",
                "admits(prelude, INTEGER_LANE)"})
    static double integerFloat(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return floatArithmetic(
                ProtosStandardFloatProtocol.CanonicalFloatOperation.DIVIDE,
                (double) smallValue(receiver), floatValue(argument));
    }

    @Specialization(
            guards = {
                "smallFloat(receiver, receiverCarrier)",
                "smallFloat(argument, argumentCarrier)",
                "admits(prelude, FLOAT_LANE)"})
    static double floats(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return floatArithmetic(
                ProtosStandardFloatProtocol.CanonicalFloatOperation.DIVIDE,
                floatValue(receiver), floatValue(argument));
    }

    @Specialization(
            guards = {
                "smallFloat(receiver, receiverCarrier)",
                "smallInteger(argument, argumentCarrier)",
                "admits(prelude, FLOAT_LANE)"})
    static double floatInteger(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return floatArithmetic(
                ProtosStandardFloatProtocol.CanonicalFloatOperation.DIVIDE,
                floatValue(receiver), (double) smallValue(argument));
    }

    /*
     * PERF040-E2: Integer pair with an owned large Integer; D197 exact
     * quotient. A zero divisor returns MISS before any work: ordinary dispatch
     * signals the standard Error.
     */
    @Specialization(
            guards = {
                "largeIntegerPair(receiver, receiverCarrier, argument, argumentCarrier, prelude)",
                "admits(prelude, INTEGER_LANE)"})
    static Object largeIntegers(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return nonZeroInteger(argument)
                ? largeIntegerArithmetic(
                        ProtosStandardIntegerProtocol.CanonicalIntegerOperation.EXACT_DIVIDE,
                        receiver, argument, prelude)
                : MISS;
    }

    /* PERF040-E2: owned large Integer receiver, Float argument (D196). */
    @Specialization(
            guards = {
                "ownedLarge(receiver, prelude)",
                "smallFloat(argument, argumentCarrier)",
                "admits(prelude, INTEGER_LANE)"})
    static double largeFloat(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return floatArithmetic(
                ProtosStandardFloatProtocol.CanonicalFloatOperation.DIVIDE,
                largeBinary64(receiver), floatValue(argument));
    }

    /* PERF040-E2: Float receiver, owned large Integer argument (D196). */
    @Specialization(
            guards = {
                "smallFloat(receiver, receiverCarrier)",
                "ownedLarge(argument, prelude)",
                "admits(prelude, FLOAT_LANE)"})
    static double floatLarge(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return floatArithmetic(
                ProtosStandardFloatProtocol.CanonicalFloatOperation.DIVIDE,
                floatValue(receiver), largeBinary64(argument));
    }

    @Specialization(
            guards = "!admittedCanonicalNumericLane(receiver, receiverCarrier, "
                    + "argument, argumentCarrier, prelude, INTEGER_LANE, FLOAT_LANE)")
    static Object ordinaryMiss(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return MISS;
    }
}

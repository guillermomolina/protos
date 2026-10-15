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
 * PERF040-E1: the primitive numeric operation of a '<=' send site.
 *
 * <p>The positive specializations are the certified Integer and Float
 * receiver lanes of the standard Number relation; Complex is never ordered
 * here. {@link #ordinaryMiss} is guarded by exactly their complement and
 * returns {@code MISS}, so the site keeps its unchanged ordinary send; it
 * replaces nothing, and no arrival order of operand families can retire a
 * numeric lane.
 */
@GenerateInline(false)
@GenerateUncached
@OperationProxy.Proxyable(
        allowUncached = true,
        storeBytecodeIndex = false)
abstract class ProtosNumericLessEqualNode extends ProtosPrimitiveNumericSendFastPathsBase {
    static final int INTEGER_LANE = ProtosNumericCoreAdmission.INTEGER_LESS_EQUAL;
    static final int FLOAT_LANE = ProtosNumericCoreAdmission.FLOAT_LESS_EQUAL;

    abstract Object execute(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier);

    @NeverDefault
    public static ProtosNumericLessEqualNode create() {
        return ProtosNumericLessEqualNodeGen.create();
    }

    @Specialization(
            guards = {
                "smallInteger(receiver, receiverCarrier)",
                "smallNumeric(argument, argumentCarrier)",
                "admits(prelude, INTEGER_LANE)"})
    static Object integerReceiver(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return integerOrdering(
                ProtosStandardNumberOrderingProtocol.Relation.LESS_EQUAL,
                receiver, argument, argumentCarrier);
    }

    @Specialization(
            guards = {
                "smallFloat(receiver, receiverCarrier)",
                "smallNumeric(argument, argumentCarrier)",
                "admits(prelude, FLOAT_LANE)"})
    static Object floatReceiver(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return floatOrdering(
                ProtosStandardNumberOrderingProtocol.Relation.LESS_EQUAL,
                receiver, argument, argumentCarrier);
    }

    /* PERF040-E2: Integer pair with an owned large Integer; exact. */
    @Specialization(
            guards = {
                "largeIntegerPair(receiver, receiverCarrier, argument, argumentCarrier, prelude)",
                "admits(prelude, INTEGER_LANE)"})
    static Object largeIntegers(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return exactOrdering(
                ProtosStandardNumberOrderingProtocol.Relation.LESS_EQUAL,
                receiver, argument);
    }

    /* PERF040-E2: owned large Integer receiver, Float argument; exact, never rounded. */
    @Specialization(
            guards = {
                "ownedLarge(receiver, prelude)",
                "smallFloat(argument, argumentCarrier)",
                "admits(prelude, INTEGER_LANE)"})
    static Object largeFloat(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return exactOrdering(
                ProtosStandardNumberOrderingProtocol.Relation.LESS_EQUAL,
                receiver, argument);
    }

    /* PERF040-E2: Float receiver, owned large Integer argument; exact, never rounded. */
    @Specialization(
            guards = {
                "smallFloat(receiver, receiverCarrier)",
                "ownedLarge(argument, prelude)",
                "admits(prelude, FLOAT_LANE)"})
    static Object floatLarge(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return exactOrdering(
                ProtosStandardNumberOrderingProtocol.Relation.LESS_EQUAL,
                receiver, argument);
    }

    @Specialization(
            guards = "!admittedCanonicalNumericLane(receiver, receiverCarrier, "
                    + "argument, argumentCarrier, prelude, INTEGER_LANE, FLOAT_LANE)")
    static Object ordinaryMiss(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return MISS;
    }
}

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
 * PERF040-E1: the primitive numeric operation of a '*' send site.
 *
 * <p>The positive specializations are the certified Integer and Float
 * receiver lanes; the Integer pair lanes are GraalPy-style doLL / doLLOvf
 * pairs ({@code rewriteOn} then {@code replaces} of the same domain). {@link
 * #ordinaryMiss} is guarded by exactly their complement and returns {@code
 * MISS}, so the site keeps its unchanged ordinary send; it replaces nothing,
 * and no arrival order of operand families can retire a numeric lane.
 */
@GenerateInline(false)
@GenerateUncached
@OperationProxy.Proxyable(
        allowUncached = true,
        storeBytecodeIndex = false)
abstract class ProtosNumericMultiplyNode extends ProtosPrimitiveNumericSendFastPathsBase {
    static final int INTEGER_LANE = ProtosNumericCoreAdmission.INTEGER_MULTIPLY;
    static final int FLOAT_LANE = ProtosNumericCoreAdmission.FLOAT_MULTIPLY;

    abstract Object execute(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult);

    @NeverDefault
    public static ProtosNumericMultiplyNode create() {
        return ProtosNumericMultiplyNodeGen.create();
    }

    /*
     * PERF040-E1, GraalPy doLL: two lowerer-authorized Long carriers, primitive
     * in and out. The first ArithmeticException rewrites this specialization
     * into doLLOvf, which accepts exactly the same inputs.
     */
    @Specialization(
            guards = {
                "receiverCarrier",
                "argumentCarrier",
                "admits(prelude, INTEGER_LANE)"},
            rewriteOn = ArithmeticException.class)
    static long doLL(
            long receiver, Object caller, long argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        if (foldApplies(ProtosStandardIntegerProtocol.CanonicalIntegerOperation.MULTIPLY,
                receiver, argument, foldedArithmetic, staticFoldResult)) {
            return foldedValue(staticFoldResult);
        }
        return Math.multiplyExact(receiver, argument);
    }

    /* GraalPy doLLOvf: same domain; a result that fits is still a Java long. */
    @Specialization(
            guards = {
                "receiverCarrier",
                "argumentCarrier",
                "admits(prelude, INTEGER_LANE)"},
            replaces = "doLL")
    static Object doLLOvf(
            long receiver, Object caller, long argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        if (foldApplies(ProtosStandardIntegerProtocol.CanonicalIntegerOperation.MULTIPLY,
                receiver, argument, foldedArithmetic, staticFoldResult)) {
            return foldedValue(staticFoldResult);
        }
        return multiplyOrPromote(receiver, argument, prelude);
    }

    /*
     * Every other signed-64 Integer pair: at least one authentic
     * ProtosIntegerValue, any Long being an authorized carrier. Same exact
     * primitive arithmetic and long result; the pair of Long carriers stays
     * with doLL / doLLOvf.
     */
    @Specialization(
            guards = {
                "representedIntegerPair(receiver, receiverCarrier, argument, argumentCarrier)",
                "admits(prelude, INTEGER_LANE)"},
            rewriteOn = ArithmeticException.class)
    static long doIntegers(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        long left = smallValue(receiver);
        long right = smallValue(argument);
        if (foldApplies(ProtosStandardIntegerProtocol.CanonicalIntegerOperation.MULTIPLY,
                left, right, foldedArithmetic, staticFoldResult)) {
            return foldedValue(staticFoldResult);
        }
        return Math.multiplyExact(left, right);
    }

    /* The exact generalization of doIntegers, over the same domain. */
    @Specialization(
            guards = {
                "representedIntegerPair(receiver, receiverCarrier, argument, argumentCarrier)",
                "admits(prelude, INTEGER_LANE)"},
            replaces = "doIntegers")
    static Object doIntegersOvf(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        long left = smallValue(receiver);
        long right = smallValue(argument);
        if (foldApplies(ProtosStandardIntegerProtocol.CanonicalIntegerOperation.MULTIPLY,
                left, right, foldedArithmetic, staticFoldResult)) {
            return foldedValue(staticFoldResult);
        }
        return multiplyOrPromote(left, right, prelude);
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
                ProtosStandardFloatProtocol.CanonicalFloatOperation.MULTIPLY,
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
                ProtosStandardFloatProtocol.CanonicalFloatOperation.MULTIPLY,
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
                ProtosStandardFloatProtocol.CanonicalFloatOperation.MULTIPLY,
                floatValue(receiver), (double) smallValue(argument));
    }

    /* PERF040-E2: Integer pair with an owned large Integer; exact and normalized. */
    @Specialization(
            guards = {
                "largeIntegerPair(receiver, receiverCarrier, argument, argumentCarrier, prelude)",
                "admits(prelude, INTEGER_LANE)"})
    static Object largeIntegers(
            Object receiver, Object caller, Object argument,
            boolean receiverCarrier, boolean argumentCarrier,
            Object foldedArithmetic, Object staticFoldResult,
            @Bind("callerPrelude(caller)") ProtosPrelude prelude) {
        return largeIntegerArithmetic(
                ProtosStandardIntegerProtocol.CanonicalIntegerOperation.MULTIPLY,
                receiver, argument, prelude);
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
                ProtosStandardFloatProtocol.CanonicalFloatOperation.MULTIPLY,
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
                ProtosStandardFloatProtocol.CanonicalFloatOperation.MULTIPLY,
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

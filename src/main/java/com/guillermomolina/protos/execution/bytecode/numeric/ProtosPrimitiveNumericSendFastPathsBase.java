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

import com.guillermomolina.protos.runtime.ProtosBinary64Rounding;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosLargeIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

/**
 * PERF040-A: arithmetic is distinct from canonical guest-message admission.
 *
 * An admitted SmallInteger '+' executes on primitive longs. As in GraalPy's
 * doLL / doLLOvf pair, the first overflow rewrites only that operation's
 * primitive specialization into its exact generalization; later results that
 * fit still stay Java longs, and genuine overflow enters exact Prelude-owned
 * Integer normalization. No BigInteger is created on the successful long path.
 *
 * <p>PERF040-E1: this is the shared support of the eight per-selector
 * primitive numeric operations ({@code ProtosNumeric*Node}). Each operation
 * knows its selector when the bytecode is emitted, so it declares only its own
 * positive lanes and one guarded negative specialization; the domain guards
 * and all numeric computation live here, once. Admission is the Prelude's
 * {@link ProtosNumericCoreAdmission} certificate, never a per-site D013
 * lookup, and the negative guard is exactly the complement of the positive
 * lanes, so a generic operand never displaces a numeric specialization.
 */
abstract class ProtosPrimitiveNumericSendFastPathsBase extends Node {

    /** The operation result that sends the site down its unchanged ordinary path. */
    static final Object MISS = new Object();

    /**
     * PERF043-A: a lowering-time candidate for one Integer arithmetic site. It
     * never authorizes dispatch: it is used only by an admitted lane whose
     * current operands equal the candidate's.
     */
    public record FoldedIntegerArithmetic(
            ProtosStandardIntegerProtocol.CanonicalIntegerOperation operation,
            long left, long right, long result) {}

    /*
     * PERF040-E1: exact signed-64 arithmetic for the doLLOvf generalizations
     * of '+', '-' and '*'. A result that fits stays a Java long; a genuine
     * overflow enters exact Prelude-owned Integer normalization. The
     * overflow predicates are GraalPy's doLLOvf / doLongWithOverflow ones.
     */
    static Object addOrPromote(long left, long right, ProtosPrelude prelude) {
        long result = left + right;
        if (((left ^ result) & (right ^ result)) < 0) {
            return addLarge(left, right, prelude);
        }
        return result;
    }

    static Object subtractOrPromote(long left, long right, ProtosPrelude prelude) {
        long result = left - right;
        if (((left ^ right) & (left ^ result)) < 0) {
            return subtractLarge(left, right, prelude);
        }
        return result;
    }

    static Object multiplyOrPromote(long left, long right, ProtosPrelude prelude) {
        long result = left * right;
        long ax = Math.abs(left);
        long ay = Math.abs(right);
        if (((ax | ay) >>> 31) != 0) {
            if ((right != 0 && result / right != left)
                    || (left == Long.MIN_VALUE && right == -1L)) {
                return multiplyLarge(left, right, prelude);
            }
        }
        return result;
    }

    @TruffleBoundary
    static Object addLarge(long left, long right, ProtosPrelude prelude) {
        return ProtosNumericValueSupport.addIntegers(
                new ProtosIntegerValue(left), new ProtosIntegerValue(right), prelude);
    }

    @TruffleBoundary
    static Object subtractLarge(long left, long right, ProtosPrelude prelude) {
        return ProtosNumericValueSupport.subtractIntegers(
                new ProtosIntegerValue(left), new ProtosIntegerValue(right), prelude);
    }

    @TruffleBoundary
    static Object multiplyLarge(long left, long right, ProtosPrelude prelude) {
        return ProtosNumericValueSupport.multiplyIntegers(
                new ProtosIntegerValue(left), new ProtosIntegerValue(right), prelude);
    }

    /*
     * Positive domains. A carrier is a Java Long or Double admitted only where
     * the lowerer authorized it; otherwise only the authentic final represented
     * value classes qualify. No ordinary object, even one delegating to
     * Integer, Float or Number, is ever a member.
     */
    static boolean smallInteger(Object value, boolean carrierPermitted) {
        return (carrierPermitted && value instanceof Long)
                || value instanceof ProtosIntegerValue;
    }

    static boolean smallFloat(Object value, boolean carrierPermitted) {
        return (carrierPermitted && value instanceof Double)
                || value instanceof ProtosFloatValue;
    }

    static boolean smallNumeric(Object value, boolean carrierPermitted) {
        return smallInteger(value, carrierPermitted)
                || smallFloat(value, carrierPermitted);
    }

    static long smallValue(Object value) {
        if (value instanceof Long carrier) {
            return carrier.longValue();
        }
        return ((ProtosIntegerValue) value).longValue();
    }

    static double floatValue(Object value) {
        if (value instanceof Double carrier) {
            return carrier.doubleValue();
        }
        return ((ProtosFloatValue) value).value();
    }

    static ProtosPrelude callerPrelude(Object caller) {
        return ProtosBytecodeRootNode.PrepareSendOne.callerPrelude(caller);
    }

    /** Whether the caller's Prelude certified {@code lane}. */
    static boolean admits(ProtosPrelude prelude, int lane) {
        return prelude != null && prelude.numericCoreAdmission().admits(lane);
    }

    /**
     * PERF040-E2: an authentic large Integer of the caller's certified Prelude.
     * The cheap representation test runs first, so small and generic operands
     * never reach the certificate here.
     */
    static boolean ownedLarge(Object value, ProtosPrelude prelude) {
        return value instanceof ProtosLargeIntegerValue
                && prelude != null
                && prelude.numericCoreAdmission().ownsLargeInteger(value);
    }

    /** Any admitted semantic Integer: signed-64 (carrier or value) or owned large. */
    static boolean integerOperand(Object value, boolean carrierPermitted, ProtosPrelude prelude) {
        return smallInteger(value, carrierPermitted) || ownedLarge(value, prelude);
    }

    /**
     * PERF040-E2: an Integer x Integer pair with at least one owned large
     * Integer. Disjoint from the carrier pair of doLL and from
     * {@link #representedIntegerPair}, which are both signed-64 only.
     */
    static boolean largeIntegerPair(
            Object receiver, boolean receiverCarrier,
            Object argument, boolean argumentCarrier,
            ProtosPrelude prelude) {
        return integerOperand(receiver, receiverCarrier, prelude)
                && integerOperand(argument, argumentCarrier, prelude)
                && (ownedLarge(receiver, prelude) || ownedLarge(argument, prelude));
    }

    /**
     * The exact union of an operation's positive lanes: an Integer receiver
     * (signed-64 or owned large) under the certified Integer lane, or a Float
     * receiver under the certified Float lane, with an Integer or Float
     * argument. Every operation's negative specialization is guarded by the
     * negation of this predicate, so the two are disjoint. It performs no
     * lookup and allocates nothing.
     */
    static boolean admittedCanonicalNumericLane(
            Object receiver, boolean receiverCarrier,
            Object argument, boolean argumentCarrier,
            ProtosPrelude prelude, int integerLane, int floatLane) {
        return (integerOperand(argument, argumentCarrier, prelude)
                        || smallFloat(argument, argumentCarrier))
                && ((integerOperand(receiver, receiverCarrier, prelude)
                                && admits(prelude, integerLane))
                        || (smallFloat(receiver, receiverCarrier)
                                && admits(prelude, floatLane)));
    }

    /**
     * PERF040-E2: exact Integer arithmetic with an owned large operand, by the
     * same normalizing service the standard native body uses. Carriers become
     * guest Integers only here, on the large path. EXACT_DIVIDE requires a
     * nonzero divisor (see {@link #nonZeroInteger}); the result may be a
     * SmallInteger, a BigInteger or a reduced Fraction.
     */
    @TruffleBoundary
    static Object largeIntegerArithmetic(
            ProtosStandardIntegerProtocol.CanonicalIntegerOperation operation,
            Object receiver, Object argument, ProtosPrelude prelude) {
        Object left = ProtosNumericValueSupport.guestValue(receiver);
        Object right = ProtosNumericValueSupport.guestValue(argument);
        return switch (operation) {
            case ADD -> ProtosNumericValueSupport.addIntegers(left, right, prelude);
            case SUBTRACT -> ProtosNumericValueSupport.subtractIntegers(left, right, prelude);
            case MULTIPLY -> ProtosNumericValueSupport.multiplyIntegers(left, right, prelude);
            case EXACT_DIVIDE ->
                    ProtosNumericValueSupport.divideIntegersExactly(left, right, prelude);
            default -> throw new AssertionError("not a primitive Integer operation");
        };
    }

    /*
     * A zero divisor leaves the site to ordinary dispatch, whose native body
     * signals the standard Error with its own activation. A large Integer is
     * never zero.
     */
    static boolean nonZeroInteger(Object value) {
        return value instanceof ProtosLargeIntegerValue || smallValue(value) != 0L;
    }

    /** D196: the binary64 nearest to an owned large Integer, ties to even. */
    static double largeBinary64(Object large) {
        return ProtosBinary64Rounding.roundExactInteger(large);
    }

    /**
     * PERF040-E2: exact standard Number ordering when an owned large Integer
     * participates; no operand is rounded and NaN stays unordered.
     */
    @TruffleBoundary
    static Object exactOrdering(
            ProtosStandardNumberOrderingProtocol.Relation relation,
            Object receiver, Object argument) {
        return ProtosBooleanValue.of(relation.holds(ProtosCurrentNumericRelations.compare(
                ProtosNumericValueSupport.guestValue(receiver),
                ProtosNumericValueSupport.guestValue(argument))));
    }

    /**
     * The two disjoint Integer x Integer domains of '+', '-' and '*'. The
     * carrier pair (two lowerer-authorized Java longs) is matched by the
     * primitive {@code long} parameters of doLL; this predicate covers every
     * other signed-64 pair, which involves at least one authentic
     * {@link ProtosIntegerValue}. A Java Long is a member only where the
     * lowerer authorized its carrier position; an unauthorized Long is no
     * Integer here and the site returns MISS. Together they are exactly the
     * signed-64 Integer x Integer part of {@link #admittedCanonicalNumericLane};
     * pairs with a large Integer belong to {@link #largeIntegerPair}.
     */
    static boolean representedIntegerPair(
            Object receiver, boolean receiverCarrier,
            Object argument, boolean argumentCarrier) {
        return smallInteger(receiver, receiverCarrier)
                && smallInteger(argument, argumentCarrier)
                && !(receiverCarrier
                        && argumentCarrier
                        && receiver instanceof Long
                        && argument instanceof Long);
    }

    /**
     * D197-C: Integer / Integer is the canonical exact quotient. Only an
     * integral signed-64 quotient stays primitive; a zero divisor, the
     * Long.MIN_VALUE / -1 promotion and every Fraction return MISS, keeping
     * the selected native body authoritative.
     */
    static Object integerQuotient(
            Object receiver,
            Object argument,
            Object foldedArithmetic,
            Object staticFoldResult) {
        long left = smallValue(receiver);
        long right = smallValue(argument);
        if (foldApplies(
                ProtosStandardIntegerProtocol.CanonicalIntegerOperation.EXACT_DIVIDE,
                left, right, foldedArithmetic, staticFoldResult)) {
            return foldedValue(staticFoldResult);
        }
        return right != 0L
                        && (left != Long.MIN_VALUE || right != -1L)
                        && left % right == 0L
                ? (Object) (left / right)
                : MISS;
    }

    /*
     * PERF043: whether the site's lowering-time candidate is this operation on
     * exactly the current operands. The lane is already admitted; the
     * operands are checked because guest locals are mutable. Every signed-64
     * value is a valid result, so the answer is a boolean, never a sentinel.
     */
    static boolean foldApplies(
            ProtosStandardIntegerProtocol.CanonicalIntegerOperation operation,
            long left, long right,
            Object foldedArithmetic, Object staticFoldResult) {
        return foldedArithmetic instanceof FoldedIntegerArithmetic fold
                && operation == fold.operation()
                && left == fold.left()
                && right == fold.right()
                && staticFoldResult instanceof Long constant
                && constant.longValue() == fold.result();
    }

    /** The known result of an applying fold (see {@link #foldApplies}). */
    static long foldedValue(Object staticFoldResult) {
        return ((Long) staticFoldResult).longValue();
    }

    /**
     * Standard binary64 arithmetic. A mixed operand was already converted to
     * binary64 (D196: the exact Integer becomes the nearest Float, Integer zero
     * becomes +0.0) and the operands keep their receiver/argument order.
     */
    static double floatArithmetic(
            ProtosStandardFloatProtocol.CanonicalFloatOperation operation,
            double left, double right) {
        return switch (operation) {
            case ADD -> left + right;
            case SUBTRACT -> left - right;
            case MULTIPLY -> left * right;
            case DIVIDE -> left / right;
        };
    }

    /** Standard Number ordering from an Integer receiver; no operand is rounded. */
    static Object integerOrdering(
            ProtosStandardNumberOrderingProtocol.Relation relation,
            Object receiver, Object argument, boolean argumentCarrier) {
        long left = smallValue(receiver);
        ProtosStandardNumberOrderingProtocol.Comparison comparison =
                smallFloat(argument, argumentCarrier)
                        ? ProtosCurrentNumericRelations.reverse(
                                ProtosCurrentNumericRelations.compareFloatToLong(
                                        floatValue(argument), left))
                        : ProtosCurrentNumericRelations.compareLongs(
                                left, smallValue(argument));
        return ProtosBooleanValue.of(relation.holds(comparison));
    }

    /** Standard Number ordering from a Float receiver; NaN is unordered. */
    static Object floatOrdering(
            ProtosStandardNumberOrderingProtocol.Relation relation,
            Object receiver, Object argument, boolean argumentCarrier) {
        double left = floatValue(receiver);
        ProtosStandardNumberOrderingProtocol.Comparison comparison =
                smallFloat(argument, argumentCarrier)
                        ? ProtosCurrentNumericRelations.compareFloats(
                                left, floatValue(argument))
                        : ProtosCurrentNumericRelations.compareFloatToLong(
                                left, smallValue(argument));
        return ProtosBooleanValue.of(relation.holds(comparison));
    }
}

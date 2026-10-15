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

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosLargeIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.util.Objects;

/**
 * PERF040-E1: the immutable certificate of the canonical numeric selections of
 * one {@link ProtosPrelude}.
 *
 * <p>Core bootstrap freezes the standard numeric graph before it constructs
 * its Prelude. A FROZEN exact ordinary object registers no D013 dependency
 * and can never gain, lose or replace a slot, so an ordinary selection that
 * starts at a frozen represented-family prototype and visits only frozen
 * exact ordinary objects is fixed for the lifetime of that Prelude. This
 * certificate proves such a selection once per Prelude, instead
 * of re-proving it with a guarded D013 lookup at every primitive send site.
 *
 * <p>Each lane is certified separately, per selector and per receiver family,
 * by identity: the exact Number, Integer, SmallInteger, BigInteger and Float
 * bindings of this Prelude, their exact delegation chain, their frozen state,
 * the absence of a nearer local slot for the selector, and the exact selected
 * standard Closure and home recognized by the owning standard protocol. A
 * known prototype name or a frozen object alone admits nothing; a Prelude
 * without the complete canonical structure (for example a synthetic test
 * Prelude, or an alternative Core whose selected method differs) simply
 * receives no admission for the affected lanes and keeps ordinary dispatch.
 *
 * <p>The certificate is computed lazily by the Prelude's first specialized
 * numeric operation (pay-as-you-grow), never by Prelude construction.
 *
 * <p>Certification reads local slots only. It sends no guest message, runs no
 * Closure and has no observable effect. The certificate is internal runtime
 * state: it is never a guest slot and cannot be reached or changed by Protos
 * code.
 */
public final class ProtosNumericCoreAdmission {
    /* Arithmetic selected through a signed-64 Integer receiver. */
    static final int INTEGER_ADD = 1;
    static final int INTEGER_SUBTRACT = 1 << 1;
    static final int INTEGER_MULTIPLY = 1 << 2;
    static final int INTEGER_DIVIDE = 1 << 3;

    /* Arithmetic selected through a Float receiver. */
    static final int FLOAT_ADD = 1 << 4;
    static final int FLOAT_SUBTRACT = 1 << 5;
    static final int FLOAT_MULTIPLY = 1 << 6;
    static final int FLOAT_DIVIDE = 1 << 7;

    /* Standard Number ordering reached from an Integer receiver. */
    static final int INTEGER_LESS = 1 << 8;
    static final int INTEGER_LESS_EQUAL = 1 << 9;
    static final int INTEGER_GREATER = 1 << 10;
    static final int INTEGER_GREATER_EQUAL = 1 << 11;

    /* Standard Number ordering reached from a Float receiver. */
    static final int FLOAT_LESS = 1 << 12;
    static final int FLOAT_LESS_EQUAL = 1 << 13;
    static final int FLOAT_GREATER = 1 << 14;
    static final int FLOAT_GREATER_EQUAL = 1 << 15;

    private static final ProtosNumericCoreAdmission NONE =
            new ProtosNumericCoreAdmission(0, null);

    private final int admittedLanes;
    /* This Prelude's certified BigInteger prototype, or null. */
    private final ProtosObjectValue bigIntegerPrototype;

    private ProtosNumericCoreAdmission(
            int admittedLanes, ProtosObjectValue bigIntegerPrototype) {
        this.admittedLanes = admittedLanes;
        this.bigIntegerPrototype = bigIntegerPrototype;
    }

    /** Whether every bit of {@code lane} was certified for this Prelude. */
    boolean admits(int lane) {
        return (admittedLanes & lane) == lane;
    }

    /**
     * PERF040-E2: whether {@code value} is an authentic large Integer minted by
     * this Prelude. The Integer lanes certify that BigInteger is a frozen
     * child of the certified Integer with no local numeric slot, so its
     * selections are exactly those of SmallInteger. Membership is by the exact
     * final representation and its parent, never by delegation alone: an
     * ordinary object delegating to BigInteger, or a large Integer of another
     * Prelude, is not a member.
     */
    boolean ownsLargeInteger(Object value) {
        return value instanceof ProtosLargeIntegerValue large
                && bigIntegerPrototype != null
                && large.parent().orElse(null) == bigIntegerPrototype;
    }

    /**
     * Certifies the canonical numeric lanes of a Prelude whose bindings have
     * already been initialized. Called once by the Prelude constructor.
     */
    public static ProtosNumericCoreAdmission certify(ProtosPrelude prelude) {
        Objects.requireNonNull(prelude, "prelude");
        ProtosObjectValue bindings = prelude.bindings();
        ProtosObjectValue number = frozenBinding(bindings, "Number");
        if (number == null
                || number.parent().orElse(null) != ProtosObjectValue.rootObject()) {
            return NONE;
        }

        int lanes = 0;
        ProtosObjectValue certifiedBigInteger = null;
        ProtosObjectValue integer = frozenBinding(bindings, "Integer");
        ProtosObjectValue smallInteger = frozenBinding(bindings, "SmallInteger");
        ProtosObjectValue bigInteger = frozenBinding(bindings, "BigInteger");
        if (integer != null
                && smallInteger != null
                && bigInteger != null
                && integer.parent().orElse(null) == number
                && smallInteger.parent().orElse(null) == integer
                && bigInteger.parent().orElse(null) == integer) {
            certifiedBigInteger = bigInteger;
            lanes |= integerArithmetic(
                    prelude, integer, smallInteger, bigInteger,
                    ProtosStandardIntegerProtocol.CanonicalIntegerOperation.ADD,
                    INTEGER_ADD);
            lanes |= integerArithmetic(
                    prelude, integer, smallInteger, bigInteger,
                    ProtosStandardIntegerProtocol.CanonicalIntegerOperation.SUBTRACT,
                    INTEGER_SUBTRACT);
            lanes |= integerArithmetic(
                    prelude, integer, smallInteger, bigInteger,
                    ProtosStandardIntegerProtocol.CanonicalIntegerOperation.MULTIPLY,
                    INTEGER_MULTIPLY);
            lanes |= integerArithmetic(
                    prelude, integer, smallInteger, bigInteger,
                    ProtosStandardIntegerProtocol.CanonicalIntegerOperation.EXACT_DIVIDE,
                    INTEGER_DIVIDE);
            lanes |= ordering(
                    prelude, number, ProtosStandardNumberOrderingProtocol.Relation.LESS,
                    "<", INTEGER_LESS, smallInteger, bigInteger, integer);
            lanes |= ordering(
                    prelude, number, ProtosStandardNumberOrderingProtocol.Relation.LESS_EQUAL,
                    "<=", INTEGER_LESS_EQUAL, smallInteger, bigInteger, integer);
            lanes |= ordering(
                    prelude, number, ProtosStandardNumberOrderingProtocol.Relation.GREATER,
                    ">", INTEGER_GREATER, smallInteger, bigInteger, integer);
            lanes |= ordering(
                    prelude, number, ProtosStandardNumberOrderingProtocol.Relation.GREATER_EQUAL,
                    ">=", INTEGER_GREATER_EQUAL, smallInteger, bigInteger, integer);
        }

        ProtosObjectValue floating = frozenBinding(bindings, "Float");
        if (floating != null && floating.parent().orElse(null) == number) {
            lanes |= floatArithmetic(
                    prelude, floating,
                    ProtosStandardFloatProtocol.CanonicalFloatOperation.ADD, "+", FLOAT_ADD);
            lanes |= floatArithmetic(
                    prelude, floating,
                    ProtosStandardFloatProtocol.CanonicalFloatOperation.SUBTRACT, "-",
                    FLOAT_SUBTRACT);
            lanes |= floatArithmetic(
                    prelude, floating,
                    ProtosStandardFloatProtocol.CanonicalFloatOperation.MULTIPLY, "*",
                    FLOAT_MULTIPLY);
            lanes |= floatArithmetic(
                    prelude, floating,
                    ProtosStandardFloatProtocol.CanonicalFloatOperation.DIVIDE, "/",
                    FLOAT_DIVIDE);
            lanes |= ordering(
                    prelude, number, ProtosStandardNumberOrderingProtocol.Relation.LESS,
                    "<", FLOAT_LESS, floating);
            lanes |= ordering(
                    prelude, number, ProtosStandardNumberOrderingProtocol.Relation.LESS_EQUAL,
                    "<=", FLOAT_LESS_EQUAL, floating);
            lanes |= ordering(
                    prelude, number, ProtosStandardNumberOrderingProtocol.Relation.GREATER,
                    ">", FLOAT_GREATER, floating);
            lanes |= ordering(
                    prelude, number, ProtosStandardNumberOrderingProtocol.Relation.GREATER_EQUAL,
                    ">=", FLOAT_GREATER_EQUAL, floating);
        }
        return lanes == 0
                ? NONE
                : new ProtosNumericCoreAdmission(lanes, certifiedBigInteger);
    }

    /*
     * A signed-64 receiver starts at SmallInteger. Both concrete families must
     * leave the selector to the common Integer prototype, exactly the family
     * independence lookupGuardedInteger requires.
     */
    private static int integerArithmetic(
            ProtosPrelude prelude,
            ProtosObjectValue integer,
            ProtosObjectValue smallInteger,
            ProtosObjectValue bigInteger,
            ProtosStandardIntegerProtocol.CanonicalIntegerOperation operation,
            int lane) {
        String selector = operation.selector();
        if (smallInteger.hasLocalSlot(selector) || bigInteger.hasLocalSlot(selector)) {
            return 0;
        }
        ProtosClosureValue selected = selectedClosure(integer, selector);
        return selected != null
                        && ProtosStandardIntegerProtocol.canonicalOperationForSelection(
                                        selected, integer, selector, prelude)
                                == operation
                ? lane
                : 0;
    }

    private static int floatArithmetic(
            ProtosPrelude prelude,
            ProtosObjectValue floating,
            ProtosStandardFloatProtocol.CanonicalFloatOperation operation,
            String selector,
            int lane) {
        ProtosClosureValue selected = selectedClosure(floating, selector);
        return selected != null
                        && ProtosStandardFloatProtocol.canonicalOperationForSelection(
                                        selected, floating, selector, prelude)
                                == operation
                ? lane
                : 0;
    }

    /*
     * Standard ordering is inherited from Number. Every prototype the receiver
     * family visits before Number must leave the selector absent.
     */
    private static int ordering(
            ProtosPrelude prelude,
            ProtosObjectValue number,
            ProtosStandardNumberOrderingProtocol.Relation relation,
            String selector,
            int lane,
            ProtosObjectValue... nearer) {
        for (ProtosObjectValue prototype : nearer) {
            if (prototype.hasLocalSlot(selector)) {
                return 0;
            }
        }
        ProtosClosureValue selected = selectedClosure(number, selector);
        return selected != null
                        && ProtosStandardNumberOrderingProtocol.canonicalRelationForSelection(
                                        selected, number, selector, prelude)
                                == relation
                ? lane
                : 0;
    }

    /*
     * The Closure stored in the home's own slot. A stored standard native
     * Closure carries no method home of its own, or exactly this home.
     */
    private static ProtosClosureValue selectedClosure(
            ProtosObjectValue home, String selector) {
        Object slot = home.readLocalSlot(selector).orElse(null);
        if (!(slot instanceof ProtosClosureValue closure)
                || closure.methodHome().map(methodHome -> methodHome != home).orElse(false)) {
            return null;
        }
        return closure;
    }

    /*
     * Only an exact FROZEN ordinary object has a permanently fixed slot set:
     * subclasses may back slots differently (see trackLookupDependency).
     */
    private static ProtosObjectValue frozenBinding(
            ProtosObjectValue bindings, String name) {
        Object binding = bindings.readLocalSlot(name).orElse(null);
        if (binding == null
                || binding.getClass() != ProtosObjectValue.class
                || !((ProtosObjectValue) binding).isFrozen()) {
            return null;
        }
        return (ProtosObjectValue) binding;
    }
}

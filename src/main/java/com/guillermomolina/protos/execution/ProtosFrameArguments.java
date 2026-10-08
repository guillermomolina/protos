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
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosReturnHome;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import java.util.List;
import java.util.Objects;

final class ProtosFrameArguments {
    /*
     * PLAT040 / I072-B compact ordinary source-call ABI.
     *
     * Captured lexical state and executable identity remain owned by the
     * Closure. Caller-owned module/domain/control provenance is referenced,
     * rather than copied into another rich per-call aggregate. User arguments
     * follow the fixed runtime header directly, as in mature Truffle frame
     * argument conventions.
     *
     * Two invocation kinds share the header layout:
     *
     *   immediate method call: closure, receiver, methodHome, caller, returnHome
     *   direct Closure call:   closure, DIRECT_CLOSURE_CALL, task|null, caller, returnHome
     *
     * A direct Closure call (PERF025) takes its receiver and method home from
     * the Closure's captures, exactly as ProtosActivation.forClosureInvocation
     * does. Its task slot carries an explicit owning Task for a Task-owned
     * entry (whose creator is not the Task's own activation); null means the
     * callee inherits the caller's Task or dynamic-control state.
     *
     * The returnHome slot always holds a ProtosReturnHome; for an owning call
     * of a plan proven return-home-unobservable it is the non-materialized
     * ProtosReturnHome.unobservable() marker (PERF025).
     *
     * PERF032-G7: a direct Closure call with zero supplied arguments uses a
     * minimal header instead, distinguished by length and slot types:
     *
     *   A: closure, caller                    (no Task, unobservable home)
     *   B: closure, caller, returnHome        (no Task, physical home)
     *   C: closure, task, caller              (explicit Task, unobservable home)
     *   D: closure, task, caller, returnHome  (explicit Task, physical home)
     *
     * The omitted unobservable home is the identity-stable shared marker, so
     * it is reconstructed rather than recomputed. Argument 0 is replaced by
     * the published activation exactly as for the full header; every other
     * slot and the array length stay unchanged after publication.
     */
    private static final int CLOSURE_INDEX = 0;
    private static final int RECEIVER_INDEX = 1;
    private static final int METHOD_HOME_INDEX = 2;
    private static final int TASK_INDEX = 2;
    private static final int CALLER_INDEX = 3;
    private static final int RETURN_HOME_INDEX = 4;
    private static final int USER_ARGUMENT_OFFSET = 5;

    /* PERF032-G7 minimal direct-header layouts; 0 is "not minimal". */
    private static final int MINIMAL_NONE = 0;
    private static final int MINIMAL_A = 1;
    private static final int MINIMAL_B = 2;
    private static final int MINIMAL_C = 3;
    private static final int MINIMAL_D = 4;

    /** Private kind marker; never a guest value, so never a method receiver. */
    private static final Object DIRECT_CLOSURE_CALL = new Object();

    private ProtosFrameArguments() {}

    static Object[] compactImmediateMethodCall(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            ProtosActivation caller,
            Object[] supplied) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(methodHome, "methodHome");
        return compactCall(closure, receiver, methodHome, caller, supplied);
    }

    /**
     * PERF025 compact direct source-backed Closure call. {@code task} is the
     * exact owning Task of a Task-owned entry, or {@code null} for a guest call
     * whose callee inherits the caller's Task/dynamic-control state.
     */
    static Object[] compactDirectClosureCall(
            ProtosClosureValue closure,
            ProtosActivation caller,
            ProtosTask task,
            Object[] supplied) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(supplied, "supplied");
        if (supplied.length != 0) {
            return compactCall(closure, DIRECT_CLOSURE_CALL, task, caller, supplied);
        }
        ProtosReturnHome returnHome = closure.invocationReturnHomeForRuntime();
        boolean unobservable = returnHome == ProtosReturnHome.unobservable();
        if (task == null) {
            return unobservable
                    ? new Object[] {closure, caller}
                    : new Object[] {closure, caller, returnHome};
        }
        return unobservable
                ? new Object[] {closure, task, caller}
                : new Object[] {closure, task, caller, returnHome};
    }

    private static Object[] compactCall(
            ProtosClosureValue closure,
            Object kindOrReceiver,
            Object methodHomeOrTask,
            ProtosActivation caller,
            Object[] supplied) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(supplied, "supplied");

        ProtosReturnHome returnHome = closure.invocationReturnHomeForRuntime();
        Object[] arguments =
                new Object[USER_ARGUMENT_OFFSET + supplied.length];
        arguments[CLOSURE_INDEX] = closure;
        arguments[RECEIVER_INDEX] = kindOrReceiver;
        arguments[METHOD_HOME_INDEX] = methodHomeOrTask;
        arguments[CALLER_INDEX] = caller;
        arguments[RETURN_HOME_INDEX] = returnHome;
        System.arraycopy(
                supplied,
                0,
                arguments,
                USER_ARGUMENT_OFFSET,
                supplied.length);
        return arguments;
    }

    static boolean hasActivation(Frame frame) {
        if (frame == null) {
            return false;
        }
        return hasActivation(frame.getArguments());
    }

    static boolean hasActivation(Object[] arguments) {
        return arguments.length > 0
                && (arguments[0] instanceof ProtosActivation
                        || isCompactCall(arguments));
    }

    static ProtosActivation activation(Frame frame) {
        if (frame == null) {
            throw new IllegalStateException(
                    "Execution node requires Protos frame arguments");
        }
        return activation(frame.getArguments());
    }

    /**
     * Returns the exact activation of a target-argument array, materializing
     * and publishing it into argument 0 when it is still in compact form.
     */
    static ProtosActivation activation(Object[] arguments) {
        if (arguments.length > 0
                && arguments[0] instanceof ProtosActivation activation) {
            return activation;
        }
        return materializeCompactActivation(arguments);
    }

    /*
     * TEST009-E: materializing a compact call's activation is the rare path of
     * activation(Object[]) and is host-side object construction with no
     * partial-evaluation value; inlined, it was expanded into every operation
     * that may consult the current activation (several hundred nodes per site).
     */
    @TruffleBoundary
    private static ProtosActivation materializeCompactActivation(Object[] arguments) {
        if (!isCompactCall(arguments)) {
            throw new IllegalStateException(
                    "Execution node requires a Protos activation or compact source-call ABI");
        }

        ProtosClosureValue closure =
                (ProtosClosureValue) arguments[CLOSURE_INDEX];
        int minimal = minimalLayout(arguments);
        if (minimal != MINIMAL_NONE) {
            return materializeMinimalDirectActivation(arguments, closure, minimal);
        }
        ProtosActivation caller =
                (ProtosActivation) arguments[CALLER_INDEX];
        ProtosReturnHome returnHome =
                (ProtosReturnHome) arguments[RETURN_HOME_INDEX];
        /*
         * The supplied values stay backed by this frame-argument array: the
         * user-argument range is written once by compactCall and never
         * mutated, so the activation adopts it as a read-only view rather
         * than copying it.
         */
        List<?> supplied =
                ProtosActivation.frameBackedSuppliedArgumentsForRuntime(
                        arguments, USER_ARGUMENT_OFFSET);

        ProtosActivation materialized;
        ProtosTask explicitTask = null;
        if (isDirectClosureCall(arguments)) {
            explicitTask = (ProtosTask) arguments[TASK_INDEX];
            materialized =
                    ProtosActivation.forDirectClosureInvocationWithReturnHomeForRuntime(
                            closure,
                            supplied,
                            caller.prelude().orElse(null),
                            caller.actorModuleState(),
                            caller.currentModuleKey().orElse(null),
                            caller.executionDomain(),
                            returnHome);
        } else {
            materialized =
                    ProtosActivation.forImmediateMethodInvocationWithReturnHomeForRuntime(
                            closure,
                            supplied,
                            arguments[RECEIVER_INDEX],
                            (ProtosObjectValue) arguments[METHOD_HOME_INDEX],
                            caller.prelude().orElse(null),
                            caller.actorModuleState(),
                            caller.currentModuleKey().orElse(null),
                            caller.executionDomain(),
                            returnHome);
        }

        if (explicitTask != null) {
            materialized.attachTask(explicitTask);
        } else if (caller.task().isPresent()) {
            materialized.attachTask(caller.task().orElseThrow());
        } else {
            materialized.inheritDynamicControlState(caller);
        }

        /*
         * Debugger/NodeLibrary observation may request the activation before
         * the semantic helper operation does. Publish the exact materialized
         * activation back into this frame so every observer and the helper
         * share one fresh guest execution-context identity.
         */
        arguments[CLOSURE_INDEX] = materialized;
        return materialized;
    }

    /** PERF032-G7 materialization of a minimal direct header (zero supplied). */
    private static ProtosActivation materializeMinimalDirectActivation(
            Object[] arguments, ProtosClosureValue closure, int minimal) {
        ProtosActivation caller = minimalCaller(arguments, minimal);
        ProtosReturnHome returnHome = minimalReturnHome(arguments, minimal);
        ProtosTask explicitTask = minimalTask(arguments, minimal);
        List<?> supplied =
                ProtosActivation.frameBackedSuppliedArgumentsForRuntime(
                        arguments, arguments.length);
        ProtosActivation materialized =
                ProtosActivation.forDirectClosureInvocationWithReturnHomeForRuntime(
                        closure,
                        supplied,
                        caller.prelude().orElse(null),
                        caller.actorModuleState(),
                        caller.currentModuleKey().orElse(null),
                        caller.executionDomain(),
                        returnHome);
        if (explicitTask != null) {
            materialized.attachTask(explicitTask);
        } else if (caller.task().isPresent()) {
            materialized.attachTask(caller.task().orElseThrow());
        } else {
            materialized.inheritDynamicControlState(caller);
        }
        arguments[CLOSURE_INDEX] = materialized;
        return materialized;
    }

    /**
     * PERF025-H1: true while {@code arguments} are still in compact
     * source-call form, i.e. no rich activation has been materialized and
     * published for this invocation yet. The invocation's execution context
     * is then necessarily unobserved and has no lexical-binding authority
     * installed, so the root's frame locals are the only store of its
     * frame-native bindings.
     *
     * <p>PERF038-B: this is the compact/materialized discriminator of an
     * already admitted Closure-root (or inline-callback carrier) argument
     * array, not an ABI validator. Every such array is either a rich
     * {@code {activation}} array or a compact array built by
     * {@link #compactImmediateMethodCall} or {@link #compactDirectClosureCall}
     * and validated by its {@code OrdinarySourceCall} or
     * {@code PreparedInlineLiteralCall} carrier before entry; the only later
     * write of argument 0 is {@link #materializeCompactActivation}, which
     * replaces the Closure with the published activation. Argument 0 alone
     * therefore distinguishes the two states, and the full header
     * re-validation is not repeated at every root-level frame operation.
     * The materialization slow path still runs the full validation.
     */
    static boolean isUnmaterializedCompactCall(Object[] arguments) {
        return arguments.length > CLOSURE_INDEX
                && arguments[CLOSURE_INDEX] instanceof ProtosClosureValue;
    }

    /**
     * PERF034-D: the compact/materialized discriminator of a scalar-local
     * lane. It is not an ABI validator: an arbitrary array whose argument 0
     * is a {@link ProtosClosureValue} (for example {@code {closure}}) yields
     * {@code true}.
     *
     * <p>Precondition: {@code arguments} are the frame arguments of a Closure
     * root admitted by the PERF034-C scalar-local lowering, entered either
     * with a rich array whose argument 0 is a {@link ProtosActivation} or
     * with a compact array built by {@link #compactImmediateMethodCall} or
     * {@link #compactDirectClosureCall}. Every compact source entry is
     * carried by {@code OrdinarySourceCall} or
     * {@code PreparedInlineLiteralCall}, whose construction already runs the
     * full {@link #requireCompactCall} validation (through
     * {@link #compactOwnsReturnHome}) outside the callee. The only later
     * write of argument 0 is {@link #materializeCompactActivation}, which
     * replaces the Closure with the published activation, so argument 0
     * alone distinguishes the two states. It is re-read at every use so a
     * materialization between two instructions is observed. The length test
     * subsumes the array bounds check of the read. Since PERF038-B this is
     * the same discriminator as {@link #isUnmaterializedCompactCall}, under
     * the same precondition.
     */
    static boolean isUnmaterializedCompactScalarLocalCall(Object[] arguments) {
        return isUnmaterializedCompactCall(arguments);
    }

    /**
     * Supplied positional argument count of a compact source call.
     *
     * <p>PERF038-B: every minimal direct header (PERF032-G7) is shorter than
     * the full header and every full header is at least
     * {@code USER_ARGUMENT_OFFSET} long; the length never changes on
     * publication. The length alone therefore selects the layout of a
     * compact array, before or after materialization, without re-deriving
     * the minimal variant.
     */
    static int compactSuppliedArgumentCount(Object[] arguments) {
        if (arguments.length < USER_ARGUMENT_OFFSET) {
            return 0;
        }
        return arguments.length - USER_ARGUMENT_OFFSET;
    }

    /** Supplied positional argument {@code index} of a compact source call. */
    static Object compactSuppliedArgument(Object[] arguments, int index) {
        if (arguments.length < USER_ARGUMENT_OFFSET) {
            /* Never expose a header slot as a guest argument. */
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return arguments[USER_ARGUMENT_OFFSET + index];
    }

    /** The callee Closure of a compact source call that is still unmaterialized. */
    static ProtosClosureValue compactClosure(Object[] arguments) {
        requireCompactCall(arguments);
        return (ProtosClosureValue) arguments[CLOSURE_INDEX];
    }

    /**
     * PERF025 slice 3: the compact caller of a still-unmaterialized direct
     * Closure call whose callee would inherit every provenance field from it,
     * or {@code null}. {@link #activation(Object[])} materializes such a call
     * with exactly this caller's prelude (the Closure has none of its own, or
     * the same one), actor module state, current module key, execution domain
     * and Task or dynamic-control state (no explicit Task). A child invocation
     * prepared with this caller therefore materializes exactly as it would
     * with the not-yet-materialized callee activation as its caller.
     */
    static ProtosActivation compactInheritedProvenanceCaller(Object[] arguments) {
        if (!isDirectClosureCall(arguments) || explicitDirectTask(arguments) != null) {
            return null;
        }
        ProtosClosureValue closure = (ProtosClosureValue) arguments[CLOSURE_INDEX];
        ProtosActivation caller = compactCaller(arguments);
        Object ownPrelude = closure.prelude().orElse(null);
        return ownPrelude == null || ownPrelude == caller.preludeOrNullForRuntime()
                ? caller
                : null;
    }

    static ProtosReturnHome compactReturnHome(Object[] arguments) {
        requireCompactCall(arguments);
        int minimal = minimalLayout(arguments);
        if (minimal != MINIMAL_NONE) {
            return minimalReturnHome(arguments, minimal);
        }
        return (ProtosReturnHome) arguments[RETURN_HOME_INDEX];
    }

    static boolean compactOwnsReturnHome(Object[] arguments) {
        requireCompactCall(arguments);
        return ((ProtosClosureValue) arguments[CLOSURE_INDEX])
                .returnHome()
                .isEmpty();
    }

    static ProtosActivation compactCaller(Object[] arguments) {
        int minimal = minimalLayout(arguments);
        if (minimal != MINIMAL_NONE) {
            return minimalCaller(arguments, minimal);
        }
        if (arguments == null
                || arguments.length < USER_ARGUMENT_OFFSET
                || !(arguments[CALLER_INDEX] instanceof ProtosActivation caller)) {
            throw new IllegalStateException(
                    "Invalid compact source-call caller frame argument");
        }
        return caller;
    }

    /**
     * The Task the callee activation of a compact call belongs to (or
     * {@code null}), without materializing that activation.
     */
    static ProtosTask compactTask(Object[] arguments) {
        if (arguments.length > 0
                && arguments[CLOSURE_INDEX] instanceof ProtosActivation materialized) {
            return materialized.task().orElse(null);
        }
        ProtosTask explicit = explicitDirectTask(arguments);
        if (explicit != null) {
            return explicit;
        }
        return compactCaller(arguments).task().orElse(null);
    }

    /** The explicit owning Task of a direct Closure call, or {@code null}. */
    private static ProtosTask explicitDirectTask(Object[] arguments) {
        int minimal = minimalLayout(arguments);
        if (minimal != MINIMAL_NONE) {
            return minimalTask(arguments, minimal);
        }
        if (isDirectClosureCall(arguments)
                && arguments[TASK_INDEX] instanceof ProtosTask task) {
            return task;
        }
        return null;
    }

    /**
     * PERF032-G7 structural discriminator of the four minimal direct headers.
     * Argument 0 is the Closure before publication and the published
     * activation afterwards; the remaining slots and the length never change,
     * so the layout stays recognizable in both states. Rich arrays carry a
     * single activation and are never minimal.
     */
    private static int minimalLayout(Object[] arguments) {
        if (arguments == null
                || arguments.length < 2
                || arguments.length > 4
                || !(arguments[CLOSURE_INDEX] instanceof ProtosClosureValue
                        || arguments[CLOSURE_INDEX] instanceof ProtosActivation)) {
            return MINIMAL_NONE;
        }
        Object first = arguments[1];
        switch (arguments.length) {
            case 2:
                return first instanceof ProtosActivation ? MINIMAL_A : MINIMAL_NONE;
            case 3:
                if (first instanceof ProtosActivation
                        && arguments[2] instanceof ProtosReturnHome) {
                    return MINIMAL_B;
                }
                return first instanceof ProtosTask
                                && arguments[2] instanceof ProtosActivation
                        ? MINIMAL_C
                        : MINIMAL_NONE;
            default:
                return first instanceof ProtosTask
                                && arguments[2] instanceof ProtosActivation
                                && arguments[3] instanceof ProtosReturnHome
                        ? MINIMAL_D
                        : MINIMAL_NONE;
        }
    }

    private static ProtosActivation minimalCaller(Object[] arguments, int minimal) {
        return (ProtosActivation) arguments[minimal <= MINIMAL_B ? 1 : 2];
    }

    private static ProtosTask minimalTask(Object[] arguments, int minimal) {
        return minimal <= MINIMAL_B ? null : (ProtosTask) arguments[1];
    }

    private static ProtosReturnHome minimalReturnHome(Object[] arguments, int minimal) {
        switch (minimal) {
            case MINIMAL_B:
                return (ProtosReturnHome) arguments[2];
            case MINIMAL_D:
                return (ProtosReturnHome) arguments[3];
            default:
                return ProtosReturnHome.unobservable();
        }
    }

    private static boolean isCompactCall(Object[] arguments) {
        return isCompactImmediateMethodCall(arguments)
                || isDirectClosureCall(arguments);
    }

    private static boolean isCompactImmediateMethodCall(Object[] arguments) {
        return hasCompactHeader(arguments)
                && arguments[RECEIVER_INDEX] != null
                && arguments[RECEIVER_INDEX] != DIRECT_CLOSURE_CALL
                && arguments[METHOD_HOME_INDEX] instanceof ProtosObjectValue;
    }

    private static boolean isDirectClosureCall(Object[] arguments) {
        if (arguments != null
                && arguments.length > 0
                && arguments[CLOSURE_INDEX] instanceof ProtosClosureValue
                && minimalLayout(arguments) != MINIMAL_NONE) {
            return true;
        }
        return hasCompactHeader(arguments)
                && arguments[RECEIVER_INDEX] == DIRECT_CLOSURE_CALL
                && (arguments[TASK_INDEX] == null
                        || arguments[TASK_INDEX] instanceof ProtosTask);
    }

    private static boolean hasCompactHeader(Object[] arguments) {
        return arguments != null
                && arguments.length >= USER_ARGUMENT_OFFSET
                && arguments[CLOSURE_INDEX] instanceof ProtosClosureValue
                && arguments[CALLER_INDEX] instanceof ProtosActivation
                && arguments[RETURN_HOME_INDEX] instanceof ProtosReturnHome;
    }

    private static void requireCompactCall(Object[] arguments) {
        if (!isCompactCall(arguments)) {
            throw new IllegalStateException(
                    "Invalid compact ordinary source-call frame arguments");
        }
    }
}

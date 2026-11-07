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
import com.guillermomolina.protos.runtime.ProtosLexicalCell;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import java.util.List;
import java.util.Objects;

final class ProtosFrameArguments {
    /* I115 single B5 ordinary-call ABI:
     * [0] invoked Closure or published activation
     * [1] receiver or direct-call marker
     * [2] method home or explicit Task
     * [3] null, caller Prelude or caller Activation, by callee need
     * [4] return home
     * [5...] supplied positional arguments
     * No B5 argument array or wrapper is retained by another invocation.
     * Publication of this invocation replaces only its own slot 0.
     */
    private static final int CLOSURE_INDEX = 0;
    private static final int RECEIVER_INDEX = 1;
    private static final int METHOD_HOME_INDEX = 2;
    private static final int TASK_INDEX = 2;
    private static final int CALLER_INDEX = 3;
    private static final int RETURN_HOME_INDEX = 4;
    static final int HEADER_LENGTH = 5;
    static final int SUPPLIED_ARGUMENT_OFFSET = HEADER_LENGTH;

    /** Private kind marker; never a guest value, so never a method receiver. */
    private static final Object DIRECT_CLOSURE_CALL = new Object();

    private static final java.util.function.Function<Object[], ProtosActivation>
            COMPACT_CAPTURE_OWNER = ProtosFrameArguments::activation;

    private ProtosFrameArguments() {}


    static Object[] compactImmediateMethodCall(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            ProtosActivation caller,
            Object[] supplied) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(methodHome, "methodHome");
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(supplied, "supplied");
        return compactImmediateMethodCall(
                closure,
                receiver,
                methodHome,
                caller,
                closure.invocationReturnHomeForRuntime(),
                supplied);
    }

    /**
     * PERF038-D: {@link #compactImmediateMethodCall} with the invocation
     * return home already obtained from {@code closure} by the caller
     * (exactly {@link ProtosClosureValue#invocationReturnHomeForRuntime}).
     *
     * <p>PERF038-E: the only caller is a guarded ordinary send whose
     * specialization already established every header value: the Closure and
     * method home are its cached selection (non-null by construction), the
     * receiver is its identity-guarded cached receiver, and the caller is an
     * activation the DSL type check or provenance guard produced. The header is
     * therefore written without repeating the null validation of the general
     * form.
     */
    static Object[] compactImmediateMethodCall(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            ProtosActivation caller,
            ProtosReturnHome returnHome,
            Object[] supplied) {
        bindOwnedReturnHome(closure, returnHome, caller.taskForRuntime());
        if (supplied.length == 0) {
            return new Object[] {closure, receiver, methodHome, caller, returnHome};
        }
        if (supplied.length == 1) {
            return new Object[] {
                closure, receiver, methodHome, caller, returnHome, supplied[0]
            };
        }
        return headerWithSupplied(closure, receiver, methodHome, caller, returnHome, supplied);
    }

    /** PERF038-H: guarded Send0, without supplied argument vector. */
    static Object[] compactImmediateMethodCallZero(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            ProtosActivation caller,
            ProtosReturnHome returnHome) {
        bindOwnedReturnHome(closure, returnHome, caller.taskForRuntime());
        return new Object[] {closure, receiver, methodHome, caller, returnHome};
    }

    /**
     * PERF038-H: guarded Send1, with the supplied value placed
     * directly into the final frame array.
     */
    static Object[] compactImmediateMethodCallOne(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            ProtosActivation caller,
            ProtosReturnHome returnHome,
            Object supplied0) {
        bindOwnedReturnHome(closure, returnHome, caller.taskForRuntime());
        return new Object[] {
            closure, receiver, methodHome, caller, returnHome, supplied0
        };
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
        ProtosReturnHome returnHome = closure.invocationReturnHomeForRuntime();
        bindOwnedReturnHome(
                closure, returnHome, task != null ? task : caller.taskForRuntime());
        if (supplied.length == 0) {
            return new Object[] {closure, DIRECT_CLOSURE_CALL, task, caller, returnHome};
        }
        if (supplied.length == 1) {
            return new Object[] {
                closure, DIRECT_CLOSURE_CALL, task, caller, returnHome, supplied[0]
            };
        }
        return headerWithSupplied(
                closure, DIRECT_CLOSURE_CALL, task, caller, returnHome, supplied);
    }

    /**
     * PERF038-H: admitted direct Call0 with an already-selected return home.
     * There is no explicit Task on an ordinary guest call.
     */
    static Object[] compactDirectClosureCallZeroPrepared(
            ProtosClosureValue closure,
            ProtosActivation caller,
            ProtosReturnHome returnHome) {
        bindOwnedReturnHome(closure, returnHome, caller.taskForRuntime());
        return new Object[] {closure, DIRECT_CLOSURE_CALL, null, caller, returnHome};
    }

    /**
     * PERF038-H: admitted direct Call1 with an already-selected home.
     * The supplied value is placed directly in the final frame.
     */
    static Object[] compactDirectClosureCallOnePrepared(
            ProtosClosureValue closure,
            ProtosActivation caller,
            ProtosReturnHome returnHome,
            Object supplied0) {
        bindOwnedReturnHome(closure, returnHome, caller.taskForRuntime());
        return new Object[] {
            closure, DIRECT_CLOSURE_CALL, null, caller, returnHome, supplied0
        };
    }

    /**
     * PERF038-H: one-argument direct Closure invocation.
     * No intermediate supplied-argument array is constructed.
     */
    static Object[] compactDirectClosureCallOne(
            ProtosClosureValue closure,
            ProtosActivation caller,
            ProtosTask task,
            Object supplied0) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(caller, "caller");
        ProtosReturnHome returnHome = closure.invocationReturnHomeForRuntime();
        bindOwnedReturnHome(
                closure, returnHome, task != null ? task : caller.taskForRuntime());
        return new Object[] {
            closure,
            DIRECT_CLOSURE_CALL,
            task,
            caller,
            returnHome,
            supplied0
        };
    }

    /*
     * PLAT070: a compact invocation whose Closure captured no home owns the materialized home in
     * its header; that home is bound to the invocation's known Task (the explicit Task of a
     * Task-owned entry, otherwise the exact caller's Task) before the callee body can create a
     * Closure capturing it. A captured home, a shared unobservable marker or an unknown Task
     * (Task-free invocation) is never bound. Ordinary-call cost: one field read and, for a fresh
     * home, one write; no activation, Context or caller chain is materialized.
     */
    private static void bindOwnedReturnHome(
            ProtosClosureValue closure, ProtosReturnHome returnHome, ProtosTask task) {
        if (task != null && closure.ownsInvocationReturnHomeForRuntime()) {
            returnHome.bindOwnerTaskForRuntime(task);
        }
    }

    /**
     * PLAT070: binds the home owned by the compact or published invocation in {@code arguments}
     * to {@code task} before a Task-owned entry runs it, without materializing an activation.
     */
    static void bindOwnedReturnHomeForTaskEntry(Object[] arguments, ProtosTask task) {
        Object first = arguments[CLOSURE_INDEX];
        if (first instanceof ProtosActivation published) {
            published.attachTask(task);
            return;
        }
        if (first instanceof ProtosClosureValue closure
                && arguments.length > RETURN_HOME_INDEX
                && arguments[RETURN_HOME_INDEX] instanceof ProtosReturnHome home) {
            bindOwnedReturnHome(closure, home, task);
        }
    }

    /**
     * PERF038-H: direct Call0 frame after a guarded proof that this
     * invocation has no observable ReturnHome; the home slot holds the
     * identity-stable unobservable marker.
     */
    static Object[] compactDirectClosureCallZeroNoHome(
            ProtosClosureValue closure,
            ProtosActivation caller) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(caller, "caller");
        return new Object[] {
            closure, DIRECT_CLOSURE_CALL, null, caller, ProtosReturnHome.unobservable()
        };
    }

    /**
     * PERF038-H: complete direct Call1 ABI without allocating an
     * intermediate supplied-argument array.
     */
    static Object[] compactDirectClosureCallOneNoHome(
            ProtosClosureValue closure,
            ProtosActivation caller,
            Object supplied0) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(caller, "caller");
        return new Object[] {
            closure,
            DIRECT_CLOSURE_CALL,
            null,
            caller,
            ProtosReturnHome.unobservableAcceptingPrimitiveResultForRuntime(),
            supplied0
        };
    }

    /**
     * I115-B: {@link #compactDirectClosureCallOneNoHome(ProtosClosureValue,
     * ProtosActivation, Object)} with the caller passed as the terminal
     * operation's caller reference: the caller's published activation, or
     * the caller's own still-compact B5 frame arguments. The reference is
     * stored as is; nothing is resolved, materialized or copied here.
     *
     * <p>Only the admitted terminal direct Call1 uses this form, for a
     * PROVEN or an UNKNOWN target alike, after its guards proved the
     * canonical {@code call} selection and the unobservable ReturnHome of
     * {@code closure}. The
     * deferred caller is resolved to the caller invocation's single
     * activation only when a reader needs it ({@link #resolveExactCaller}).
     */
    static Object[] compactDirectClosureCallOneNoHome(
            ProtosClosureValue closure,
            Object callerReference,
            Object supplied0) {
        Objects.requireNonNull(closure, "closure");
        if (callerReference != null
                && !(callerReference instanceof ProtosActivation)
                && !(callerReference instanceof ProtosPrelude)) {
            throw new IllegalArgumentException(
                    "Invalid direct Closure-call caller payload");
        }
        return compactAdmittedTerminalClosureCallOneNoHome(
                closure, callerReference, supplied0);
    }


    /**
     * I115-D: trusted B5+ construction for the already admitted
     * direct terminal specialization.
     *
     * The receiver and executable have passed the direct-call
     * guards. callerReference(frame) supplies either the exact
     * published activation or the caller's own B5 argument array.
     * No validation, caller publication or array copy occurs here.
     *
     * The general Object-caller constructor retains its validation.
     */
    static Object[] compactAdmittedTerminalClosureCallOneNoHome(
            ProtosClosureValue closure,
            Object callerReference,
            Object supplied0) {
        return new Object[] {
            closure,
            DIRECT_CLOSURE_CALL,
            null,
            callerReference,
            ProtosReturnHome.unobservableAcceptingPrimitiveResultForRuntime(),
            supplied0
        };
    }

    /**
     * B5 direct source-call header with an exact invocation ReturnHome.
     *
     * The caller is transported without publication. The invocation
     * home is selected once by the caller and retained by identity.
     */
    static Object[] compactDispatchedClosureCallOne(
            ProtosClosureValue closure,
            Object callerReference,
            ProtosReturnHome returnHome,
            Object supplied0) {
        return new Object[] {
            closure,
            DIRECT_CLOSURE_CALL,
            null,
            callerReference,
            returnHome,
            supplied0
        };
    }

    /*
     * I115 (GraalPy PArguments.create): the single B5 construction of every
     * call entered through ProtosFunctionCallDispatchers. A function call
     * writes the direct-call marker and no Task/methodHome; a selected method
     * writes its receiver and methodHome. The caller is transported as is
     * (exact or deferred) and the ReturnHome is the exact object selected by
     * the dispatcher: captured, freshly owned, or the unobservable marker.
     */
    static Object[] dispatchedCall0(
            ProtosClosureValue closure, Object receiverSlot, Object methodHomeSlot,
            Object callerReference, ProtosReturnHome returnHome) {
        return new Object[] {closure, receiverSlot, methodHomeSlot, callerReference, returnHome};
    }

    static Object[] dispatchedCall1(
            ProtosClosureValue closure, Object receiverSlot, Object methodHomeSlot,
            Object callerReference, ProtosReturnHome returnHome, Object a0) {
        return new Object[] {closure, receiverSlot, methodHomeSlot, callerReference, returnHome, a0};
    }

    static Object[] dispatchedCall2(
            ProtosClosureValue closure, Object receiverSlot, Object methodHomeSlot,
            Object callerReference, ProtosReturnHome returnHome, Object a0, Object a1) {
        return new Object[] {
            closure, receiverSlot, methodHomeSlot, callerReference, returnHome, a0, a1
        };
    }

    static Object[] dispatchedCall3(
            ProtosClosureValue closure, Object receiverSlot, Object methodHomeSlot,
            Object callerReference, ProtosReturnHome returnHome,
            Object a0, Object a1, Object a2) {
        return new Object[] {
            closure, receiverSlot, methodHomeSlot, callerReference, returnHome, a0, a1, a2
        };
    }

    static Object[] dispatchedCall4(
            ProtosClosureValue closure, Object receiverSlot, Object methodHomeSlot,
            Object callerReference, ProtosReturnHome returnHome,
            Object a0, Object a1, Object a2, Object a3) {
        return new Object[] {
            closure, receiverSlot, methodHomeSlot, callerReference, returnHome, a0, a1, a2, a3
        };
    }

    static Object[] dispatchedCallN(
            ProtosClosureValue closure, Object receiverSlot, Object methodHomeSlot,
            Object callerReference, ProtosReturnHome returnHome, Object[] supplied) {
        Object[] arguments = new Object[HEADER_LENGTH + supplied.length];
        arguments[CLOSURE_INDEX] = closure;
        arguments[RECEIVER_INDEX] = receiverSlot;
        arguments[METHOD_HOME_INDEX] = methodHomeSlot;
        arguments[CALLER_INDEX] = callerReference;
        arguments[RETURN_HOME_INDEX] = returnHome;
        System.arraycopy(supplied, 0, arguments, SUPPLIED_ARGUMENT_OFFSET, supplied.length);
        return arguments;
    }

    /** A B5 function-call array with the dispatcher-owned slots still empty. */
    static Object[] functionCallArguments(Object[] supplied) {
        return dispatchedCallN(null, DIRECT_CLOSURE_CALL, null, null, null, supplied);
    }

    /** A B5 method-call array with the dispatcher-owned slots still empty. */
    static Object[] methodCallArguments(
            Object receiver, ProtosObjectValue methodHome, Object[] supplied) {
        return dispatchedCallN(null, receiver, methodHome, null, null, supplied);
    }

    /**
     * I115: a Task-owned function call (GraalPy task entry): slot 2 carries
     * the exact owning Task.
     */
    static Object[] taskFunctionCallArguments(ProtosTask task, Object[] supplied) {
        return dispatchedCallN(null, DIRECT_CLOSURE_CALL, task, null, null, supplied);
    }

    /** Whether a B5 array built for the dispatcher is an unbound function call. */
    static boolean isFunctionCall(Object[] arguments) {
        return arguments[RECEIVER_INDEX] == DIRECT_CLOSURE_CALL;
    }

    /** Writes the receiver and methodHome of a selected method (GraalPy PMethod). */
    static void bindMethod(Object[] arguments, Object receiver, ProtosObjectValue methodHome) {
        arguments[RECEIVER_INDEX] = receiver;
        arguments[METHOD_HOME_INDEX] = methodHome;
    }

    /** Writes the dispatcher-owned header slots. */
    static void completeDispatchedHeader(
            Object[] arguments,
            ProtosClosureValue closure,
            Object callerReference,
            ProtosReturnHome returnHome) {
        arguments[CLOSURE_INDEX] = closure;
        arguments[CALLER_INDEX] = callerReference;
        arguments[RETURN_HOME_INDEX] = returnHome;
    }

    /** The receiver slot value of an unbound function call. */
    static Object functionCallMarker() {
        return DIRECT_CLOSURE_CALL;
    }

    /**
     * I115 (TruffleRuby RubyMethodRootNode): the materialized ReturnHome this
     * activation owns, or {@code null} when its home is captured from an
     * outer activation, is the unobservable marker, or the arguments are not
     * a B5 Closure header. Read from the header, so the identity is exactly
     * the one the dispatcher wrote.
     */
    static ProtosReturnHome ownedReturnHomeOrNull(Object[] arguments) {
        if (arguments.length < HEADER_LENGTH) {
            return null;
        }
        Object owner = arguments[CLOSURE_INDEX];
        ProtosReturnHome home;
        if (owner instanceof ProtosActivation activation) {
            if (!activation.ownsReturnHome()) {
                return null;
            }
            home = activation.returnHomeOrNullForRuntime();
        } else if (owner instanceof ProtosClosureValue closure
                && arguments[RETURN_HOME_INDEX] instanceof ProtosReturnHome slot) {
            if (closure.returnHome().isPresent()) {
                return null;
            }
            home = slot;
        } else {
            return null;
        }
        return home != null && home.isMaterialized() ? home : null;
    }

    /**
     * PERF038-I: trusted B5+ construction for the admitted direct tier of
     * InvokeMethodOneTail: an immediate method call (slots 1 and 2 hold the
     * receiver and the selected method home) whose caller slot carries the
     * I115-B deferred caller reference. The guarded selection already proved
     * a source Closure with an unobservable ReturnHome; the primitive-result
     * accepting marker lets the callee return a numeric carrier, which the
     * caller root's return epilogue projects. No validation, caller
     * publication or intermediate array occurs here.
     */
    static Object[] compactAdmittedTerminalMethodCallOneNoHome(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            Object callerReference,
            Object supplied0) {
        return new Object[] {
            closure,
            receiver,
            methodHome,
            callerReference,
            ProtosReturnHome.unobservableAcceptingPrimitiveResultForRuntime(),
            supplied0
        };
    }

    /**
     * I115-B: the exact caller activation a caller slot or caller reference
     * denotes. A {@link ProtosActivation} is returned unchanged. A deferred
     * caller (the caller invocation's own B5 frame arguments) yields the
     * activation already published in its argument 0, or materializes and
     * publishes exactly that invocation's activation, as {@link
     * #activation(Object[])} does when the caller root observes itself. Any
     * other representation is rejected; no substitute activation is created.
     *
     * <p>Temporal equivalence. Resolving later than the call (during the
     * callee, after its suspension, or after it returned) selects exactly
     * what materializing the caller at the call selected, because every
     * input of {@link #materializeCompactActivation} is fixed before the
     * caller can call anything:
     * <ul>
     *   <li>prelude, actor module state, module key and execution domain
     *       come from the immutable B5 slots, the Closure, and the caller's
     *       own caller, whose fields are final;</li>
     *   <li>an explicit Task is the immutable slot 2;</li>
     *   <li>an inherited Task, deferred C-prime operation or release, and
     *       direct dynamic-control state of a published activation are each
     *       written once, at creation, before that activation runs guest
     *       code ({@code attachTask} and the C-prime attachments are only
     *       applied to activations being created or entered, and {@code
     *       ProtosActivation#dynamicControlState} creates a direct state only
     *       for the {@code handle}/{@code ensure} invocation's own activation
     *       before it invokes its body). An activation that is the caller of
     *       a call therefore never changes which authority it hands down;
     *       only the contents of a shared state evolve, by reference, as
     *       before;</li>
     *   <li>a deferred caller of a deferred caller follows by induction,
     *       since each is published at most once, into its own argument
     *       0.</li>
     * </ul>
     */
    static ProtosActivation resolveExactCaller(Object callerReference) {
        if (callerReference instanceof ProtosActivation activation) {
            return activation;
        }
        throw new IllegalStateException(
                "I115: exact caller activation was not transported");
    }



    static ProtosActivation resolveAdmittedCaller(Object callerReference) {
        return resolveExactCaller(callerReference);
    }





    /**
     * I115: a caller reference transporting the effective {@code prelude} of
     * the running root whose own admitted call header is {@code
     * callerArguments}. Stored in its callee's slot 3 only when the callee
     * root requested the caller prelude.
     */


    /*
     * I115-B: the caller slot of {@code arguments}, resolved. A deferred
     * caller that is this same array (directly or through a reference)
     * would be a self-reference and is rejected.
     */
    private static ProtosActivation resolveCallerSlot(Object[] arguments) {
        Object callerReference = arguments[CALLER_INDEX];
        if (callerReference == null) {
            return ProtosReadCallerFrameNode.resolve(arguments);
        }
        return resolveExactCaller(callerReference);
    }







    /*
     * I115-B: publishes the activation of a still-compact deferred caller. The
     * whole unpublished chain is collected and validated first, with an
     * identity set rejecting any cycle, so a malformed chain throws before
     * anything is published. It is then published deepest first, iteratively,
     * so each materialization finds its own caller already published and no
     * recursion proportional to the chain depth is needed. Each invocation is
     * materialized at most once: its argument 0 is replaced by the published
     * activation, which every later resolution returns.
     */





    /**
     * PERF038-H: final zero-argument method frame for a guarded
     * source call with a proven unobservable ReturnHome.
     */
    /**
     * PERF038-H / I115-H7-R2: admitted synchronous Send0 with the B5 caller
     * slot holding either the published activation or a deferred caller.
     * The supplied caller reference has already been selected by the
     * producer; constructing this header never publishes it.
     */
    static Object[] compactImmediateMethodCallZeroNoHome(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            Object callerReference) {
        return new Object[] {
            closure,
            receiver,
            methodHome,
            callerReference,
            ProtosReturnHome.unobservableAcceptingPrimitiveResultForRuntime()
        };
    }

    /**
     * I102: the B5 frame arguments of an ordinary Closure entry whose exact
     * activation already exists (the generic, rich invocation path). The
     * activation is published in argument 0 from the start, so every reader
     * takes its published path and that activation remains the invocation's
     * single identity.
     *
     * <p>No caller, receiver or return home is synthesized. Slots 1 and 2
     * carry the activation's own receiver and method home when it has a method
     * home, otherwise the direct-call tag and no explicit Task (the activation
     * owns its Task state). The caller slot is {@code null}: the activation
     * already holds every provenance field, so no caller is ever consulted in
     * published state. The return-home slot is the activation's exact home,
     * and the supplied values follow at {@link #SUPPLIED_ARGUMENT_OFFSET}.
     *
     * <p>Published-state contract: the caller slot is read only behind an
     * "argument 0 is still the Closure" guard ({@link
     * #isUnmaterializedCompactCall} or the full {@link #hasCompactHeader}
     * validation), so {@code null} there is never taken for a caller nor
     * dereferenced; prelude, Task, receiver, method home and arguments of a
     * published invocation are read from its activation. Copying the supplied
     * values reads the activation's deferred value list (or its existing
     * guest Array) and never creates the guest Array or the Context.
     */
    static Object[] publishedInvocation(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        /*
         * Every Closure invocation activation receives its home from
         * ProtosClosureValue#invocationReturnHomeForRuntime (never null) or
         * from a validated compact header; only non-invocation activations
         * (module, bootstrap, top-level) have none, and they never enter
         * through this ABI.
         */
        ProtosReturnHome returnHome =
                activation.returnHome()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "ordinary Closure entry requires the invocation's"
                                                        + " return home; this activation is not a"
                                                        + " Closure invocation"));
        List<?> supplied = activation.suppliedArgumentsForRuntime();
        Object[] arguments = new Object[SUPPLIED_ARGUMENT_OFFSET + supplied.size()];
        arguments[CLOSURE_INDEX] = activation;
        ProtosObjectValue methodHome = activation.methodHome().orElse(null);
        if (methodHome != null) {
            arguments[RECEIVER_INDEX] = activation.receiver();
            arguments[METHOD_HOME_INDEX] = methodHome;
        } else {
            arguments[RECEIVER_INDEX] = DIRECT_CLOSURE_CALL;
        }
        arguments[RETURN_HOME_INDEX] = returnHome;
        for (int index = 0; index < supplied.size(); index++) {
            arguments[SUPPLIED_ARGUMENT_OFFSET + index] = supplied.get(index);
        }
        return arguments;
    }

    private static Object[] headerWithSupplied(
            ProtosClosureValue closure,
            Object kindOrReceiver,
            Object methodHomeOrTask,
            ProtosActivation caller,
            ProtosReturnHome returnHome,
            Object[] supplied) {
        Object[] arguments = new Object[SUPPLIED_ARGUMENT_OFFSET + supplied.length];
        arguments[CLOSURE_INDEX] = closure;
        arguments[RECEIVER_INDEX] = kindOrReceiver;
        arguments[METHOD_HOME_INDEX] = methodHomeOrTask;
        arguments[CALLER_INDEX] = caller;
        arguments[RETURN_HOME_INDEX] = returnHome;
        System.arraycopy(supplied, 0, arguments, SUPPLIED_ARGUMENT_OFFSET, supplied.length);
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
     * PERF038-C: true when argument 0 already holds the exact activation of
     * this invocation (a published B5 entry, or a compact call whose
     * activation was published by {@link #materializeCompactActivation}).
     * Exactly the first branch of {@link #activation(Object[])}, so a caller
     * that guards on it may read {@link #publishedActivation} without the
     * materialization path.
     */
    static boolean hasPublishedActivation(Object[] arguments) {
        return arguments.length > CLOSURE_INDEX
                && arguments[CLOSURE_INDEX] instanceof ProtosActivation;
    }

    /** The activation already published in argument 0; see {@link #hasPublishedActivation}. */
    static ProtosActivation publishedActivation(Object[] arguments) {
        return (ProtosActivation) arguments[CLOSURE_INDEX];
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

        /*
         * I115-B: a deferred caller is resolved here, so materializing this
         * invocation publishes its caller's single activation first.
         */
        ProtosActivation caller = resolveCallerSlot(arguments);
        ProtosReturnHome returnHome = normalizedReturnHome(arguments[RETURN_HOME_INDEX]);
        /*
         * The supplied values stay backed by this frame-argument array: the
         * user-argument range is written once by its constructor and never
         * mutated, so the activation adopts it as a read-only view rather
         * than copying it.
         */
        List<?> supplied =
                ProtosActivation.frameBackedSuppliedArgumentsForRuntime(
                        arguments, SUPPLIED_ARGUMENT_OFFSET);

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

    /**
     * I092: capture one ordinary Closure without materializing a compact
     * root's activation. The lowerer admits this only in a genuine root
     * that declares no frame-owned lexical bindings; every other root
     * retains MaterializeClosure's exact activation-based path.
     *
     * <p>I115 / PLAT066-C: when {@code capturesCreatorContext} is false the
     * lowering classified {@code definition} as not capturing its creator's
     * execution context, and the new Closure receives the creator's own
     * captured environment (the invoked Closure's, B5 argument 0) unchanged:
     * no deferred owner of this invocation is created, so nothing retains the
     * frame or its arguments. A recorded divergence from the unconditional
     * capture of {@code CALLABLES.md}, pending an owner decision.
     *
     * <p>I115-H (EXPERIMENTAL): when the definition also cannot observe its
     * ReturnHome ({@code mayObserveReturnHome == false}), such a Closure is the
     * lightweight {@link ProtosClosureValue#experimentalNonCapturing}
     * representation; the creator's Closure, environment, receiver,
     * methodHome, ReturnHome and Prelude are not consulted. A definition that
     * may observe its ReturnHome (for example {@code (value) => { ^value }})
     * keeps the I115-G construction with the creator's ReturnHome.
     */
    static ProtosClosureValue materializeCompactClosure(
            Object[] arguments,
            CanonicalClosure definition,
            ProtosClosureExecutionPlanCell executionPlanCell,
            boolean capturesCreatorContext,
            boolean mayObserveReturnHome) {
        if (!capturesCreatorContext && !mayObserveReturnHome) {
            return ProtosClosureValue.experimentalNonCapturing(
                    definition,
                    executionPlanCell.plan());
        }
        ProtosClosureValue currentClosure = compactClosure(arguments);
        boolean direct = isDirectClosureCall(arguments);
        Object receiver = direct
                ? currentClosure.capturedReceiver()
                : arguments[RECEIVER_INDEX];
        ProtosObjectValue methodHome = direct
                ? currentClosure.methodHome().orElse(null)
                : (ProtosObjectValue) arguments[METHOD_HOME_INDEX];
        ProtosLexicalEnvironment environment = capturesCreatorContext
                ? ProtosLexicalEnvironment.deferredCompactOwner(
                        arguments,
                        COMPACT_CAPTURE_OWNER,
                        currentClosure.capturedLexicalEnvironmentForRuntime())
                : currentClosure.capturedLexicalEnvironmentForRuntime();
        return new ProtosClosureValue(
                definition,
                environment,
                receiver,
                methodHome,
                compactReturnHome(arguments),
                preludeOrNull(arguments),
                executionPlanCell.plan(),
                currentClosure.definingModuleKeyOrNullForRuntime());
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
     * array, not an ABI validator. Every such array is a B5 array whose
     * argument 0 is either the invoked Closure or the published activation,
     * built by one of this class's constructors and validated by its
     * call carrier
     * before entry; the only later write of argument 0 is {@link
     * #materializeCompactActivation}, which replaces the Closure with the
     * published activation. Argument 0 alone therefore distinguishes the two
     * states, and the full header re-validation is not repeated at every
     * root-level frame operation. The materialization slow path still runs
     * the full validation.
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
     * <p>Precondition: {@code arguments} are the B5 frame arguments of a
     * Closure root admitted by the PERF034-C scalar-local lowering. It is
     * re-read at every use so a materialization between two instructions is
     * observed. Since PERF038-B this is the same discriminator as {@link
     * #isUnmaterializedCompactCall}, under the same precondition.
     */
    static boolean isUnmaterializedCompactScalarLocalCall(Object[] arguments) {
        return isUnmaterializedCompactCall(arguments);
    }

    /**
     * PERF037-B: the captured lexical chain the activation of a
     * still-unmaterialized compact call has (or would have once
     * materialized): the invoked Closure's own, for both invocation kinds.
     * Precondition: {@link #isUnmaterializedCompactCall}.
     */
    static ProtosLexicalEnvironment compactCapturedLexicalEnvironment(Object[] arguments) {
        return ((ProtosClosureValue) arguments[CLOSURE_INDEX])
                .capturedLexicalEnvironmentForRuntime();
    }


    static ProtosPrelude preludeOrNull(Object[] arguments) {
        if (hasPublishedActivation(arguments)) {
            return publishedActivation(arguments).preludeOrNullForRuntime();
        }
        ProtosPrelude own = compactClosure(arguments).preludeOrNullForRuntime();
        return own != null ? own : callerPreludeOrNull(arguments);
    }


    /**
     * I115: {@link #preludeOrNull} of an admitted call header (the running
     * root's own frame arguments), with the same precedence (published
     * activation, the Closure's own prelude, the caller's prelude) but
     * without re-validating the header the root was entered with. Any other
     * shape takes the general {@link #preludeOrNull}.
     */
    static ProtosPrelude admittedPreludeOrNull(Object[] arguments) {
        if (arguments.length >= HEADER_LENGTH) {
            Object first = arguments[CLOSURE_INDEX];
            if (first instanceof ProtosActivation activation) {
                return activation.preludeOrNullForRuntime();
            }
            if (first instanceof ProtosClosureValue closure) {
                ProtosPrelude own = closure.preludeOrNullForRuntime();
                return own != null ? own : callerPreludeOrNull(arguments);
            }
        }
        CompilerDirectives.transferToInterpreter();
        return preludeOrNull(arguments);
    }


    static boolean inheritsUntransportedCallerPrelude(Object[] arguments) {
        return arguments.length >= HEADER_LENGTH
                && arguments[CLOSURE_INDEX] instanceof ProtosClosureValue closure
                && closure.preludeOrNullForRuntime() == null
                && arguments[CALLER_INDEX] == null;
    }



    private static ProtosPrelude callerPreludeOrNull(Object[] arguments) {
        Object caller = arguments[CALLER_INDEX];
        if (caller instanceof ProtosPrelude prelude) {
            return prelude;
        }
        if (caller instanceof ProtosActivation activation) {
            return activation.preludeOrNullForRuntime();
        }
        return null;
    }


    /*
     * I115: recovery of an inherited prelude that was not transported (the
     * first invocations before the need was registered, or a producer that
     * cannot consult its callee's needs). The general walker validates every
     * link it follows, rejects cycles of any length and publishes nothing.
     * Its @TruffleBoundary is the physical separation; it makes no explicit
     * transfer to the interpreter, so a producer that never transports does
     * not invalidate compiled code repeatedly.
     */





    /**
     * Supplied positional argument count of a B5 call, before or after
     * publication: the length never changes and the supplied values always
     * start at {@link #SUPPLIED_ARGUMENT_OFFSET}.
     */
    static int compactSuppliedArgumentCount(Object[] arguments) {
        return arguments.length - SUPPLIED_ARGUMENT_OFFSET;
    }

    /**
     * PERF038-E: {@link #compactSuppliedArgument} for an {@code index} already
     * proven below {@link #compactSuppliedArgumentCount} of the same array by
     * the caller's guard.
     */
    static Object compactSuppliedArgumentWithinCount(Object[] arguments, int index) {
        return arguments[SUPPLIED_ARGUMENT_OFFSET + index];
    }

    /**
     * PERF038-F: exactly {@code index < compactSuppliedArgumentCount(arguments)}
     * for a non-negative {@code index}.
     */
    static boolean compactHasSuppliedArgument(Object[] arguments, int index) {
        return arguments.length > SUPPLIED_ARGUMENT_OFFSET + index;
    }

    /**
     * PERF038-F: exactly {@code compactSuppliedArgumentCount(arguments) <=
     * maximum} for a non-negative {@code maximum}.
     */
    static boolean compactSuppliedArgumentCountAtMost(Object[] arguments, int maximum) {
        return arguments.length <= SUPPLIED_ARGUMENT_OFFSET + maximum;
    }

    /**
     * D203: supplied positional argument {@code index} of a B5 call, or
     * canonical null when the call supplied fewer arguments. The B5 header is
     * unchanged; only the supplied suffix length is consulted.
     */
    static Object compactSuppliedArgumentOrNull(Object[] arguments, int index) {
        return arguments.length > SUPPLIED_ARGUMENT_OFFSET + index
                ? arguments[SUPPLIED_ARGUMENT_OFFSET + index]
                : ProtosNullValue.INSTANCE;
    }

    /**
     * I115: the number of supplied positional arguments of a B5 call, in
     * either state (publication never changes the supplied suffix).
     */
    static int suppliedArgumentCount(Object[] arguments) {
        return arguments.length - SUPPLIED_ARGUMENT_OFFSET;
    }

    /**
     * I115: the value of a rest parameter, read from the B5 supplied suffix:
     * the frozen Array of the supplied arguments after the first {@code
     * positionalParametersBeforeRest}, as guest values.
     */
    @TruffleBoundary
    static Object restArgumentArray(Object[] arguments, int positionalParametersBeforeRest) {
        int count = suppliedArgumentCount(arguments);
        java.util.ArrayList<Object> rest = new java.util.ArrayList<>();
        for (int index = positionalParametersBeforeRest; index < count; index++) {
            rest.add(ProtosNumericValueSupport.guestValue(
                    arguments[SUPPLIED_ARGUMENT_OFFSET + index]));
        }
        ProtosPrelude prelude = preludeOrNull(arguments);
        if (prelude == null) {
            throw new IllegalStateException(
                    "parameter binding requires an owning Core prelude");
        }
        return prelude.newFrozenArray(rest);
    }

    /** Supplied positional argument {@code index} of a B5 call. */
    static Object compactSuppliedArgument(Object[] arguments, int index) {
        if (index < 0) {
            /* Never expose a header slot as a guest argument. */
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return arguments[SUPPLIED_ARGUMENT_OFFSET + index];
    }

    /** The callee Closure of a compact source call that is still unmaterialized. */
    /**
     * I115: the captured cells of the invoked Closure, read once at root
     * entry from argument 0: the invoked Closure of a compact call, or the
     * published activation, which
     * refers to the same array. Called only by
     * roots whose scope declares free cells; each such root copies the cells
     * into its own Bytecode locals, so no later access reads this header.
     */
    static ProtosLexicalCell[] capturedCells(Object[] arguments) {
        Object invocation = arguments[CLOSURE_INDEX];
        if (invocation instanceof ProtosClosureValue closure) {
            return closure.capturedCellsForRuntime();
        }
        if (invocation instanceof ProtosActivation activation) {
            return activation.capturedCellsForRuntime();
        }
        throw CompilerDirectives.shouldNotReachHere(
                "I115 cell-capturing root entered without its Closure");
    }

    static ProtosClosureValue compactClosure(Object[] arguments) {
        requireCompactCall(arguments);
        return (ProtosClosureValue) arguments[CLOSURE_INDEX];
    }

    /**
     * PERF025 slice 3: the compact caller of a still-unmaterialized direct
     * Closure call (or, since PERF038-D, compact immediate method call) whose
     * callee would inherit every provenance field from it, or {@code null}.
     * {@link #activation(Object[])} materializes such a call with exactly this
     * caller's prelude (the Closure has none of its own, or the same one),
     * actor module state, current module key, execution domain and Task or
     * dynamic-control state (no explicit Task). A child invocation prepared
     * with this caller therefore materializes exactly as it would with the
     * not-yet-materialized callee activation as its caller.
     *
     * <p>A compact immediate method call never carries an explicit Task, and
     * its materialization ({@link
     * ProtosActivation#forImmediateMethodInvocationWithReturnHomeForRuntime})
     * takes the same caller-derived provenance as a direct Closure call's;
     * only its receiver and method home differ, and those are not provenance.
     */
    static ProtosActivation compactInheritedProvenanceCaller(Object[] arguments) {
        boolean inheritsAll =
                isDirectClosureCall(arguments)
                        ? explicitDirectTask(arguments) == null
                        : isCompactImmediateMethodCall(arguments);
        if (!inheritsAll) {
            return null;
        }
        return callerIfPreludeInherited(arguments);
    }

    /**
     * The exact caller when the activation of the invoked Closure has the
     * caller's prelude (the Closure has none of its own, or the same one),
     * else {@code null}. PERF038-E: reads the nullable prelude directly, so
     * no {@code Optional} is built. I115-B: the prelude relation is decided
     * without resolving a deferred caller; the caller is resolved only when
     * it is returned. I115: {@code admitted} selects the admitted caller
     * prelude walk for an admitted Closure root's own header; otherwise the
     * general, validating walk decides.
     */
    private static ProtosActivation callerIfPreludeInherited(
            Object[] arguments) {
        Object caller = arguments[CALLER_INDEX];
        if (!(caller instanceof ProtosActivation activation)) {
            return null;
        }
        ProtosPrelude own =
                ((ProtosClosureValue) arguments[CLOSURE_INDEX])
                        .preludeOrNullForRuntime();
        return own == null || own == activation.preludeOrNullForRuntime()
                ? activation
                : null;
    }


    /**
     * PERF038-D: exactly {@link #compactInheritedProvenanceCaller} for the
     * still-unmaterialized frame arguments of an admitted Closure root (the
     * {@link #isUnmaterializedCompactCall} precondition), decided by the
     * fixed B5 slots instead of re-validating the ABI: the receiver slot is
     * the direct-call marker exactly for a direct Closure call, whose task
     * slot is then its explicit Task or {@code null}.
     */
    static ProtosActivation unmaterializedInheritedProvenanceCaller(Object[] arguments) {
        if (arguments[RECEIVER_INDEX] == DIRECT_CLOSURE_CALL
                && arguments[TASK_INDEX] != null) {
            return null;
        }
        return callerIfPreludeInherited(arguments);
    }

    /**
     * PERF038-F: true when the frame arguments of an admitted Closure root
     * are still unmaterialized and their callee inherits its Task or
     * dynamic-control state from the caller: an immediate method call, or a
     * direct Closure call without an explicit Task. Together with {@link
     * #unmaterializedFullHeaderOwnPrelude} {@code ==} the caller's prelude,
     * this is exactly the non-null case of {@link
     * #unmaterializedInheritedProvenanceCaller}, decided without re-deriving
     * the ABI.
     */
    static boolean isUnmaterializedInheritingFullHeader(Object[] arguments) {
        return isUnmaterializedCompactCall(arguments)
                && (arguments[RECEIVER_INDEX] != DIRECT_CLOSURE_CALL
                        || arguments[TASK_INDEX] == null);
    }

    /**
     * PERF038-F: the caller slot of a B5 header. Precondition: {@link
     * #isUnmaterializedInheritingFullHeader}; every such header was built
     * with a non-null caller, and that slot is never rewritten. I115-B: a
     * deferred caller is resolved to the caller invocation's exact
     * activation; an already published one is returned without a boundary.
     */
    static ProtosActivation unmaterializedFullHeaderCaller(Object[] arguments) {
        Object caller = arguments[CALLER_INDEX];
        if (caller instanceof ProtosActivation activation) {
            return activation;
        }
        return resolveCallerSlot(arguments);
    }

    /**
     * PERF038-F: the invoked Closure's own (final) prelude, or {@code null}.
     * Precondition: {@link #isUnmaterializedInheritingFullHeader}.
     */
    static ProtosPrelude unmaterializedFullHeaderOwnPrelude(Object[] arguments) {
        return ((ProtosClosureValue) arguments[CLOSURE_INDEX]).preludeOrNullForRuntime();
    }

    /**
     * The invocation's exact return home, read from the fixed home slot. The
     * slot survives publication, so this is valid before and after it.
     */
    static ProtosReturnHome compactReturnHome(Object[] arguments) {
        return normalizedReturnHome(arguments[RETURN_HOME_INDEX]);
    }

    /*
     * A no-home fast call only needs a semantic home if its activation escapes
     * into the slow materialization path. Do not create a marker on entry.
     */
    private static ProtosReturnHome normalizedReturnHome(Object home) {
        return home == null
                || home == ProtosReturnHome.unobservableAcceptingPrimitiveResultForRuntime()
                ? ProtosReturnHome.unobservable()
                : (ProtosReturnHome) home;
    }

    /**
     * I091: whether the caller of this invocation accepts a primitive numeric
     * result. Only a fused direct source call or send writes the private
     * marker into the home slot; argument 0 publication keeps it. The length
     * test admits the frame arguments of non-Closure roots, whose entry
     * contracts are not B5.
     */
    static boolean acceptsPrimitiveResult(Object[] arguments) {
        return arguments.length > RETURN_HOME_INDEX
                && arguments[RETURN_HOME_INDEX]
                        == ProtosReturnHome.unobservableAcceptingPrimitiveResultForRuntime();
    }

    static boolean compactOwnsReturnHome(Object[] arguments) {
        requireCompactCall(arguments);
        return ((ProtosClosureValue) arguments[CLOSURE_INDEX])
                .returnHome()
                .isEmpty();
    }

    /**
     * The exact caller of a compact call. I115-B: a deferred caller is
     * resolved (and thereby published) by {@link #resolveExactCaller}.
     */
    static ProtosActivation compactCaller(Object[] arguments) {
        return resolveCallerSlot(arguments);
    }

    /**
     * The Task the callee activation of a compact call belongs to (or
     * {@code null}), without materializing that activation. I115-B: nor its
     * deferred caller's: a chain of deferred callers is followed
     * iteratively, each answering exactly as its materialized activation
     * would (its explicit Task, else its own caller's Task). Every deferred
     * link is validated; this is the contract for arbitrary representations.
     */
    static ProtosTask compactTask(Object[] arguments) {
        if (hasPublishedActivation(arguments)) {
            return publishedActivation(arguments).task().orElse(null);
        }
        requireCompactCall(arguments);
        ProtosTask explicit = explicitDirectTask(arguments);
        if (explicit != null) {
            return explicit;
        }
        Object caller = arguments[CALLER_INDEX];
        return caller instanceof ProtosActivation activation
                ? activation.task().orElse(null)
                : null;
    }



    static ProtosTask admittedCompactTask(Object[] arguments) {
        if (arguments.length >= HEADER_LENGTH) {
            Object first = arguments[CLOSURE_INDEX];
            if (first instanceof ProtosActivation activation) {
                return activation.task().orElse(null);
            }
            if (first instanceof ProtosClosureValue) {
                if (arguments[RECEIVER_INDEX] == DIRECT_CLOSURE_CALL
                        && arguments[TASK_INDEX] instanceof ProtosTask explicit) {
                    return explicit;
                }
                Object caller = arguments[CALLER_INDEX];
                return caller instanceof ProtosActivation activation
                        ? activation.task().orElse(null)
                        : null;
            }
        }
        CompilerDirectives.transferToInterpreter();
        return compactTask(arguments);
    }


    /* I115: a link the admitted Task walk does not admit; see admittedCompactTask. */


    /** The explicit owning Task of a direct Closure call, or {@code null}. */
    private static ProtosTask explicitDirectTask(Object[] arguments) {
        if (isDirectClosureCall(arguments)
                && arguments[TASK_INDEX] instanceof ProtosTask task) {
            return task;
        }
        return null;
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
        return hasCompactHeader(arguments)
                && arguments[RECEIVER_INDEX] == DIRECT_CLOSURE_CALL
                && (arguments[TASK_INDEX] == null
                        || arguments[TASK_INDEX] instanceof ProtosTask);
    }

    /**
     * Full validation of an unpublished B5 header (slow and carrier paths
     * only). I115-B: the caller slot is an activation, or a deferred caller
     * that structurally is another invocation's B5 frame arguments; a
     * deferred caller is not resolved here.
     */
    private static boolean hasCompactHeader(Object[] arguments) {
        return arguments != null
                && arguments.length >= HEADER_LENGTH
                && arguments[CLOSURE_INDEX] instanceof ProtosClosureValue
                && (arguments[CALLER_INDEX] == null
                        || arguments[CALLER_INDEX] instanceof ProtosPrelude
                        || arguments[CALLER_INDEX] instanceof ProtosActivation)
                && (arguments[RETURN_HOME_INDEX] == null
                        || arguments[RETURN_HOME_INDEX] instanceof ProtosReturnHome);
    }




    private static void requireCompactCall(Object[] arguments) {
        if (!isCompactCall(arguments)) {
            throw new IllegalStateException(
                    "Invalid compact ordinary source-call frame arguments");
        }
    }
}

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

import com.guillermomolina.protos.execution.ProtosInlineCallbackFrameBindings.PreparedInlineLiteralCall;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedClosureCall;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.lookup.ProtosSlotLookupResult;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.RootCallTarget;
import java.util.List;

/**
 * Closure-call selection and preparation: guarded direct Closure targets,
 * Task-owned and host-entry preparation, inline literal callbacks and the direct-call
 * completions that bypass the general prepared-call protocol.
 */
final class ProtosClosureCallPreparation {
    private ProtosClosureCallPreparation() {}

    static PreparedClosureCall prepareTaskOwnedDirectClosureIfBytecode(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation creator,
            ProtosTask task) {
        java.util.Objects.requireNonNull(closure, "closure");
        java.util.Objects.requireNonNull(supplied, "supplied");
        java.util.Objects.requireNonNull(creator, "creator");
        java.util.Objects.requireNonNull(task, "task");
        if (closure.nativeBody().isPresent()) {
            return null;
        }
        ProtosClosureExecutionPlan plan = taskOwnedBytecodePlan(closure);
        if (plan == null) {
            return null;
        }
        /*
         * PERF025 / PLAT040 F': the callee materializes its exact activation,
         * attached to this exact Task, after target entry; its guest Context
         * and supplied guest Array stay deferred.
         */
        return PreparedClosureCall.ordinaryCompact(
                plan.bytecodeActivationTargetForComposition(),
                ProtosFrameArguments.compactDirectClosureCall(
                        closure,
                        creator,
                        task,
                        supplied.toArray()));
    }


    static PreparedClosureCall prepareTaskOwnedDirectNativeForCPrime(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation creator,
            ProtosTask task) {
        java.util.Objects.requireNonNull(closure, "closure");
        java.util.Objects.requireNonNull(supplied, "supplied");
        java.util.Objects.requireNonNull(creator, "creator");
        java.util.Objects.requireNonNull(task, "task");
        if (closure.nativeBody().isEmpty()) {
            throw new IllegalArgumentException(
                    "Task native C-prime entry requires a native Closure");
        }

        /*
         * Direct Closure execution has already selected the exact Closure
         * identity. Preserve implementation provenance for extracted/bound
         * standard callback/control Closures exactly as PLAT019/PLAT021/PLAT028
         * require; no selector/home inference is introduced here.
         */
        ProtosSendPreparation.rejectComposedInvocationProjection(closure);
        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(
                        closure,
                        supplied,
                        creator.prelude().orElse(null),
                        creator.actorModuleState(),
                        creator.currentModuleKey().orElse(null),
                        creator.executionDomain());
        activation.attachTask(task);
        return ProtosSendPreparation.finishPreparingComposedCallByImplementation(
                closure,
                supplied,
                activation);
    }

    /**
     * PLAT054-3E2: Task-free counterpart of {@link #prepareTaskOwnedSelectedNativeForCPrime} for an
     * outermost standard-embedding host entry whose Closure has no compact target. {@code selected}
     * is the ordinary D013 {@code call} selection on {@code receiver}; only the exact canonical
     * selection executes the receiver Closure itself (PLAT017). The callee inherits the caller's
     * dynamic-control state exactly as a Task-free composed send does, and the returned call runs
     * through the shared C-prime entry root, so structured control and suspension behave as on the
     * compact path.
     */
    static PreparedClosureCall prepareHostEntrySelectedCall(
            Object receiver,
            ProtosSlotLookupResult selected,
            List<?> supplied,
            ProtosActivation caller) {
        java.util.Objects.requireNonNull(receiver, "receiver");
        java.util.Objects.requireNonNull(selected, "selected");
        java.util.Objects.requireNonNull(supplied, "supplied");
        java.util.Objects.requireNonNull(caller, "caller");
        if (caller.task().isPresent()) {
            throw new IllegalArgumentException("host entry provenance must not own a Task");
        }
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }
        ProtosActivation activation;
        ProtosClosureValue effective;
        if (receiver instanceof ProtosClosureValue targetClosure
                && ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                        closure,
                        selected.home())) {
            effective = targetClosure;
            activation =
                    ProtosActivation.forClosureInvocation(
                            targetClosure,
                            supplied,
                            caller.prelude().orElse(null),
                            caller.actorModuleState(),
                            caller.currentModuleKey().orElse(null),
                            caller.executionDomain());
        } else {
            effective = closure;
            activation =
                    ProtosActivation.forImmediateMethodInvocation(
                            closure,
                            supplied,
                            receiver,
                            selected.home(),
                            caller.prelude().orElse(null),
                            caller.actorModuleState(),
                            caller.currentModuleKey().orElse(null),
                            caller.executionDomain());
        }
        ProtosSendPreparation.rejectComposedInvocationProjection(effective);
        activation.inheritDynamicControlState(caller);
        return ProtosSendPreparation.finishPreparingComposedCallByImplementation(effective, supplied, activation);
    }

    static PreparedClosureCall prepareTaskOwnedSelectedCallIfBytecode(
            Object receiver,
            ProtosSlotLookupResult selected,
            List<?> supplied,
            ProtosActivation creator,
            ProtosTask task) {
        java.util.Objects.requireNonNull(receiver, "receiver");
        java.util.Objects.requireNonNull(selected, "selected");
        java.util.Objects.requireNonNull(supplied, "supplied");
        java.util.Objects.requireNonNull(creator, "creator");
        java.util.Objects.requireNonNull(task, "task");

        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(creator));
        }

        /*
         * Preserve PLAT017 exactly: ordinary D013 lookup has already selected
         * Object.call. Only the exact canonical selection may invoke the Closure
         * receiver intrinsically; aliases/overrides remain normal methods.
         */
        if (receiver instanceof ProtosClosureValue targetClosure
                && ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                        closure,
                        selected.home())) {
            return prepareTaskOwnedDirectClosureIfBytecode(
                    targetClosure,
                    supplied,
                    creator,
                    task);
        }

        if (closure.nativeBody().isPresent()) {
            return null;
        }
        ProtosClosureExecutionPlan plan = taskOwnedBytecodePlan(closure);
        if (plan == null) {
            return null;
        }
        ProtosActivation activation =
                ProtosActivation.forImmediateMethodInvocation(
                        closure,
                        supplied,
                        receiver,
                        selected.home(),
                        creator.prelude().orElse(null),
                        creator.actorModuleState(),
                        creator.currentModuleKey().orElse(null),
                        creator.executionDomain());
        activation.attachTask(task);
        return PreparedClosureCall.ordinary(
                plan.bytecodeActivationTargetForComposition(),
                activation);
    }


    static PreparedClosureCall prepareTaskOwnedSelectedNativeForCPrime(
            Object receiver,
            ProtosSlotLookupResult selected,
            List<?> supplied,
            ProtosActivation creator,
            ProtosTask task) {
        java.util.Objects.requireNonNull(receiver, "receiver");
        java.util.Objects.requireNonNull(selected, "selected");
        java.util.Objects.requireNonNull(supplied, "supplied");
        java.util.Objects.requireNonNull(creator, "creator");
        java.util.Objects.requireNonNull(task, "task");

        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(creator));
        }

        /*
         * PLAT017 remains post-lookup authority. Only the exact canonical
         * Object.call selection may elide that native bridge and execute the
         * Closure receiver itself.
         */
        if (receiver instanceof ProtosClosureValue targetClosure
                && ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                        closure,
                        selected.home())) {
            return prepareTaskOwnedDirectNativeForCPrime(
                    targetClosure,
                    supplied,
                    creator,
                    task);
        }

        if (closure.nativeBody().isEmpty()) {
            throw new IllegalArgumentException(
                    "Task selected-native C-prime entry requires a native effective target");
        }

        /*
         * Preserve PLAT025 for the exact canonical import facility. The caller
         * remains the original semantic caller for resolution/error attribution;
         * the explicit Task is supplied separately so no shared creator
         * activation is mutated merely to host module initialization.
         */
        ProtosModuleRuntime standardImportRuntime =
                ProtosStandardImportProtocol.selectedRuntimeForBytecodeIntrinsic(
                        receiver,
                        closure,
                        selected.home(),
                        creator.prelude().orElse(null));
        if (standardImportRuntime != null) {
            return PreparedClosureCall.moduleInitialization(
                    standardImportRuntime.prepareBytecodeImportForTask(
                            supplied,
                            creator,
                            task));
        }

        ProtosSendPreparation.rejectComposedInvocationProjection(closure);
        ProtosActivation activation =
                ProtosActivation.forImmediateMethodInvocation(
                        closure,
                        supplied,
                        receiver,
                        selected.home(),
                        creator.prelude().orElse(null),
                        creator.actorModuleState(),
                        creator.currentModuleKey().orElse(null),
                        creator.executionDomain());
        activation.attachTask(task);
        return ProtosSendPreparation.finishPreparingComposedCallByImplementation(
                closure,
                supplied,
                activation);
    }

    static ProtosClosureExecutionPlan taskOwnedBytecodePlan(
            ProtosClosureValue closure) {
        return taskOwnedBytecodePlan(
                closure,
                ProtosLanguageContext.currentIfEnteredForRuntime());
    }

    static ProtosClosureExecutionPlan taskOwnedBytecodePlan(
            ProtosClosureValue closure,
            ProtosLanguageContext enteredContext) {
        ProtosClosureExecutionPlan template =
                closure.executionPlanForRuntimeInvocation();

        if (enteredContext != null) {
            boolean foreignContextPlan =
                    template.language().orElseThrow()
                            != enteredContext.languageForRuntime();
            if (closure.requiresContextLocalExecutionProjectionForRuntime()
                    || foreignContextPlan) {
                if (template.source().isEmpty()) {
                    return null;
                }
                return enteredContext.bytecodeExecutionPlanForEnteredClosure(
                        closure,
                        template);
            }
        } else if (closure.requiresContextLocalExecutionProjectionForRuntime()) {
            return null;
        }

        return template;
    }

    /**
     * PERF014: the stable executable-identity key and Context-owned activation
     * target admitted by direct Closure-call invocation ({@code operation()}).
     *
     * <p>Admission requires exactly the canonical {@code Object.call} selection
     * (see {@link ProtosStandardObjectProtocol#isCanonicalStandardCallSelection})
     * on a non-native, ordinary source-backed Closure receiver whose Context-
     * owned Bytecode target can be derived. Any override, shadowing, native
     * body, or projection failure misses here and remains on the exact generic
     * {@link #prepareClosureCall} path.
     */
    record GuardedDirectClosureCallTarget(
            ProtosClosureValue closure,
            RootCallTarget target,
            Assumption stability) {}

    /**
     * Resolves and classifies only while establishing a specialization for one
     * direct Closure-call site. Reuses {@link ProtosValueLookup#lookupGuarded}
     * exactly as {@link PrepareSendArguments#createGuardedSend} does for
     * ordinary sends, and reuses
     * {@link PrepareSendArguments#fastOrdinarySendTarget} for the
     * Context-owned target derivation (including foreign-Context projection),
     * so admission stays exactly as discriminating as the institutions I072-A
     * already established. The valid hit below never repeats this lookup or
     * the generic {@code finishPreparingComposedCallByImplementation}
     * structured-protocol classification.
     */
    static GuardedDirectClosureCallTarget createGuardedDirectClosureCall(
            Object receiver,
            ProtosActivation caller,
            ProtosLanguageContext enteredContext) {
        return createGuardedDirectClosureCallForPrelude(
                receiver, caller.preludeOrNullForRuntime(), enteredContext);
    }

    static GuardedDirectClosureCallTarget createGuardedDirectClosureCallForPrelude(
            Object receiver,
            ProtosPrelude prelude,
            ProtosLanguageContext enteredContext) {
        if (enteredContext == null || !(receiver instanceof ProtosClosureValue targetClosure)) {
            return null;
        }
        ProtosValueLookup.GuardedLookup lookup;
        try {
            lookup = ProtosValueLookup.lookupGuarded(
                    receiver, "call", prelude);
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            return null;
        }
        if (lookup == null) {
            return null;
        }
        ProtosSlotLookupResult selected = lookup.selected();
        if (targetClosure.nativeBody().isPresent()
                || !ProtosStandardObjectProtocol.isCanonicalStandardCallSelection(
                        selected.value(), selected.home())) {
            lookup.stability().invalidate();
            return null;
        }
        RootCallTarget target =
                ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(targetClosure, enteredContext);
        if (target == null || !lookup.stability().isValid()) {
            lookup.stability().invalidate();
            return null;
        }
        return new GuardedDirectClosureCallTarget(targetClosure, target, lookup.stability());
    }

    /**
     * Builds the ordinary prepared Closure invocation for an admitted direct
     * Closure-call hit (either cache tier below), without repeating
     * structured-protocol classification. PERF025 / PLAT040 F': the hit enters
     * the source target through the compact direct-Closure frame ABI; the
     * callee materializes exactly the activation the generic
     * {@link #prepareDirectClosureCall} would build (same captures, return
     * home and Task/dynamic-control inheritance), with its guest Context and
     * supplied guest Array deferred.
     */
    /**
     * PERF038-H: fixed Call1 path. The final frame is constructed
     * directly; zero and N arguments retain their existing paths.
     */
    static PreparedClosureCall finishDirectClosureCallOne(
            ProtosClosureValue closure,
            RootCallTarget target,
            Object supplied0,
            ProtosActivation caller) {
        ProtosReturnHome returnHome =
                closure.invocationReturnHomeForRuntime();
        return PreparedClosureCall.ordinaryCompactSelected(
                target,
                ProtosFrameArguments.compactDirectClosureCallOnePrepared(
                        closure, caller, returnHome, supplied0),
                returnHome,
                closure.returnHome().isEmpty());
    }

    /** PERF038-H: admitted direct Call0 needs no compact-header revalidation. */
    static PreparedClosureCall finishDirectClosureCallZero(
            ProtosClosureValue closure,
            RootCallTarget target,
            ProtosActivation caller) {
        ProtosReturnHome returnHome =
                closure.invocationReturnHomeForRuntime();
        return PreparedClosureCall.ordinaryCompactSelected(
                target,
                ProtosFrameArguments.compactDirectClosureCallZeroPrepared(
                        closure, caller, returnHome),
                returnHome,
                closure.returnHome().isEmpty());
    }

    static PreparedClosureCall finishDirectClosureCall(
            ProtosClosureValue closure,
            RootCallTarget target,
            Object[] supplied,
            ProtosActivation caller) {
        return PreparedClosureCall.ordinaryCompact(
                target,
                ProtosFrameArguments.compactDirectClosureCall(
                        closure, caller, null, supplied));
    }

    /**
     * PERF014 second tier: admits a stable direct Closure-call target keyed by
     * the closure's stable {@link CanonicalClosure} definition identity rather
     * than by receiver instance identity, mirroring
     * {@link PrepareSendArguments#fastOrdinarySend}. A distinct
     * {@code ProtosClosureValue} materialization of the same definition (for
     * example a fresh closure rebuilt on every loop iteration, or every
     * recursive call) reuses the same Context-owned target without
     * re-deriving it, while the authoritative D013 {@code call} selection is
     * still decided fresh on every hit (I114: a local-slot presence check plus
     * the invalidable root certificate), so a local override on
     * one particular instance is observed exactly as before. This is the
     * institution the identity-keyed first tier
     * ({@link #createGuardedDirectClosureCall}) alone omitted, which left
     * receiver-identity churn paying repeated specialization-establishment
     * cost instead of converging.
     */
    static ProtosClosureValue directClosureCallSelectionOrNull(
            Object receiver, ProtosActivation caller) {
        return directClosureCallSelectionForPreludeOrNull(
                receiver, caller.preludeOrNullForRuntime());
    }

    /**
     * I114: a Closure's delegation parent is fixed to the root Object, so its
     * D013 {@code call} selection is canonical {@code Object.call} exactly
     * when the Closure owns no local {@code call} and the root's selection is
     * certified canonical. Neither check allocates or traverses a chain; the
     * prelude does not participate for the Closure family and is kept only for
     * the existing consumers' signature.
     */
    static ProtosClosureValue directClosureCallSelectionForPreludeOrNull(
            Object receiver, ProtosPrelude prelude) {
        if (!(receiver instanceof ProtosClosureValue targetClosure)
                || targetClosure.nativeBody().isPresent()
                || targetClosure.hasLocalCallSlotForRuntime()
                || !ProtosStandardObjectProtocol
                        .isCanonicalRootCallSelectionForRuntime()) {
            return null;
        }
        return targetClosure;
    }

    static CanonicalClosure directClosureCallDefinitionOrNull(ProtosClosureValue closure) {
        return closure == null ? null : closure.definition();
    }

    /**
     * PERF038-H: generic direct Call1, including noncanonical call
     * selection, native bodies and user overrides.
     */
    static PreparedClosureCall prepareClosureCallOneFallback(
            Object receiver,
            ProtosActivation caller,
            Object supplied0) {
        return prepareClosureCall(
                receiver, List.of(supplied0), caller);
    }

    static PreparedClosureCall prepareClosureCall(
            Object receiver,
            List<?> supplied,
            ProtosActivation caller) {
        return prepareSelectedClosureCall(
                receiver,
                supplied,
                caller,
                selectClosureCall(receiver, caller));
    }

    /**
     * PERF025 / PLAT044 B′ preparation seam for an inline-literal candidate.
     * Ordinary D013 {@code call} selection happens exactly once. The canonical
     * source-backed Closure selection keeps only its effective target and
     * compact frame arguments; every other selection is prepared through the
     * unchanged ordinary fallback immediately.
     */
    static PreparedInlineLiteralCall prepareInlineLiteralCall(
            Object receiver,
            List<?> supplied,
            ProtosActivation caller) {
        ProtosSlotLookupResult selected = selectClosureCall(receiver, caller);
        ProtosClosureValue callBehavior =
                (ProtosClosureValue) selected.value();

        if (receiver instanceof ProtosClosureValue targetClosure
                && targetClosure.nativeBody().isEmpty()
                && ProtosStandardObjectProtocol
                        .isCanonicalStandardCallSelection(
                                callBehavior,
                                selected.home())) {
            ProtosClosureExecutionPlan plan =
                    taskOwnedBytecodePlan(targetClosure);
            if (plan != null) {
                return PreparedInlineLiteralCall.direct(
                        plan.bytecodeActivationTargetForComposition(),
                        ProtosFrameArguments.compactDirectClosureCall(
                                targetClosure,
                                caller,
                                null,
                                supplied.toArray()));
            }
        }

        return PreparedInlineLiteralCall.fallback(
                prepareSelectedClosureCall(
                        receiver,
                        supplied,
                        caller,
                        selected));
    }

    /**
     * I096-A (PLAT057-B): the same preparation seam for an ordinary
     * {@code receiver(...)} call whose staged receiver is the call site's own
     * Closure literal (I096-A) or the value read from a binding with a
     * candidate body (I096-B1). Selection is exactly {@link
     * #prepareInlineLiteralCall}:
     * one ordinary D013 {@code call} lookup, and any selection other than the
     * canonical standard {@code Object.call} of a source-backed Closure keeps
     * the ordinary prepared fallback.
     */
    static PreparedInlineLiteralCall prepareInlineClosureLiteralCall(
            Object receiver,
            ProtosActivation caller,
            Object[] supplied) {
        return prepareInlineLiteralCall(
                receiver, java.util.Arrays.asList(supplied), caller);
    }

    private static ProtosSlotLookupResult selectClosureCall(
            Object receiver,
            ProtosActivation caller) {
        ProtosPrelude prelude =
                caller.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "polymorphic invocation requires an owning Core prelude"));
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(
                                    receiver,
                                    "call",
                                    prelude)
                            .orElseThrow(
                                    () ->
                                            new ProtosSignalException(
                                                    ProtosCoreErrors.newError(caller)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(caller));
        }

        if (!(selected.value() instanceof ProtosClosureValue)) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(caller));
        }
        return selected;
    }

    static PreparedClosureCall prepareSelectedClosureCall(
            Object receiver,
            List<?> supplied,
            ProtosActivation caller,
            ProtosSlotLookupResult selected) {
        ProtosClosureValue callBehavior =
                (ProtosClosureValue) selected.value();

        /*
         * PLAT017-A: D013 lookup has already selected one ordinary `call`
         * behavior. Only the exact canonical root Object.call implementation
         * may elide its native bridge for a Closure target. A nearer override,
         * including an alias of the standard behavior at another home, remains
         * an ordinary method invocation.
         */
        if (receiver instanceof ProtosClosureValue targetClosure
                && ProtosStandardObjectProtocol
                        .isCanonicalStandardCallSelection(
                                callBehavior,
                                selected.home())) {
            return prepareStandardClosureCallIntrinsic(
                    targetClosure,
                    supplied,
                    caller);
        }

        return ProtosSendPreparation.prepareImmediateMethodCall(
                callBehavior,
                receiver,
                selected.home(),
                supplied,
                caller);
    }

    private static PreparedClosureCall prepareStandardClosureCallIntrinsic(
            ProtosClosureValue targetClosure,
            List<?> supplied,
            ProtosActivation caller) {
        ProtosSendPreparation.rejectComposedInvocationProjection(
                targetClosure);
        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(
                        targetClosure,
                        supplied,
                        caller.prelude().orElse(null),
                        caller.actorModuleState(),
                        caller.currentModuleKey().orElse(null),
                        caller.executionDomain());
        ProtosSendPreparation.attachTaskOrInheritDynamicControlState(
                activation,
                caller);
        return ProtosSendPreparation.finishPreparingComposedCallByImplementation(
                targetClosure,
                supplied,
                activation);
    }

    static PreparedClosureCall prepareSynchronousSourceClosureForRuntime(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation activation) {
        java.util.Objects.requireNonNull(closure, "closure");
        java.util.Objects.requireNonNull(supplied, "supplied");
        java.util.Objects.requireNonNull(activation, "activation");

        if (closure.nativeBody().isPresent()) {
            throw new IllegalArgumentException(
                    "synchronous source Closure preparation requires a non-native Closure");
        }
        if (activation.task().isPresent()) {
            throw new IllegalArgumentException(
                    "synchronous source Closure preparation cannot own a Task");
        }
        if (!ProtosPolyglotExecutionContext.hasEnteredContextForRuntime()
                || ProtosLanguageContext.currentIfEnteredForRuntime() == null) {
            throw new IllegalStateException(
                    "synchronous source Closure preparation requires an entered host Context");
        }

        ProtosClosureExecutionPlan template =
                closure.executionPlanForRuntimeInvocation();
        if (template.source().isEmpty()) {
            throw new UnsupportedOperationException(
                    "synchronous Bytecode Closure preparation requires exact source");
        }

        return ProtosSendPreparation.finishPreparingComposedCallByImplementation(
                closure,
                supplied,
                activation);
    }
}

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

import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PrepareSendArguments.GuardedIntegerSend;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PrepareSendArguments.GuardedSendTarget;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PrepareSendArguments.GuardedStructuredKind;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.PrepareSendArguments.GuardedStructuredSend;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.lookup.ProtosSlotLookupResult;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.oracle.truffle.api.RootCallTarget;

/**
 * I113: selection and admission of the targets of PIC-guarded sends.
 *
 * <p>Every entry point runs only while a {@code PrepareSend*} specialization
 * is being established; none is called on a cached hit. The DSL guard and
 * {@code @Cached} expressions reach these methods through the forwarders
 * declared on {@link ProtosBytecodeRootNode.PrepareSendArguments}, because
 * the Bytecode DSL resolves them only against members of the operation.
 */
final class ProtosGuardedSendSelection {
    private ProtosGuardedSendSelection() {}

    /**
     * Resolves only while establishing a specialization. Admits a selected
     * native Closure only; a source-backed selection (for example a user
     * override) stays on the existing ordinary/generic paths.
     */
    static GuardedIntegerSend createGuardedIntegerSend(
            Object receiver, String selector, ProtosPrelude prelude) {
        return createGuardedIntegerSend(
                receiver,
                selector,
                prelude,
                ProtosLanguageContext.currentIfEnteredForRuntime());
    }

    static GuardedIntegerSend createGuardedIntegerSend(
            Object receiver,
            String selector,
            ProtosPrelude prelude,
            ProtosLanguageContext enteredContext) {
        if (enteredContext == null) {
            return null;
        }
        ProtosValueLookup.GuardedLookup lookup;
        try {
            lookup = ProtosValueLookup.lookupGuardedInteger(
                    receiver, selector, prelude);
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            return null;
        }
        if (lookup == null) {
            return null;
        }
        ProtosSlotLookupResult selected = lookup.selected();
        if (!(selected.value() instanceof ProtosClosureValue closure)
                || closure.nativeBody().isEmpty()
                || !lookup.stability().isValid()) {
            lookup.stability().invalidate();
            return null;
        }
        ProtosStandardIntegerProtocol.CanonicalIntegerOperation operation =
                ProtosStandardIntegerProtocol.canonicalOperationForSelection(
                        closure,
                        selected.home(),
                        selector,
                        prelude);
        return new GuardedIntegerSend(
                closure,
                selected.home(),
                operation,
                lookup.stability());
    }

    /**
     * Resolves and classifies only while establishing a specialization.
     * The entered Context owns the target; neither Closure definition
     * identity alone nor an unguarded method home authorizes this hit.
     */
    static GuardedSendTarget createGuardedSend(
            Object receiver,
            String selector,
            ProtosActivation caller,
            ProtosLanguageContext enteredContext) {
        return createGuardedSendForPrelude(
                receiver, selector, caller.preludeOrNullForRuntime(), enteredContext);
    }

    /**
     * Resolves and classifies only while establishing a specialization.
     * Reuses {@link ProtosValueLookup#lookupGuarded} exactly as
     * {@link #createGuardedSend} does, then admits only a selected native
     * Closure whose behavior/home satisfies one canonical-selection helper.
     * A copied/aliased/overridden selection (same native body at a
     * noncanonical home, or a different selected behavior entirely) misses
     * here and invalidates the assumption immediately, leaving the exact
     * generic classification as the sole authority for that selector.
     */
    static GuardedStructuredSend createGuardedStructuredSend(
            Object receiver, String selector, ProtosActivation caller) {
        boolean canonicalBoolean = ProtosValueLookup.isCanonicalBoolean(receiver);
        ProtosValueLookup.GuardedLookup lookup;
        try {
            lookup = canonicalBoolean
                    ? ProtosValueLookup.lookupGuardedCanonicalBoolean(
                            receiver, selector, caller.preludeOrNullForRuntime())
                    : ProtosValueLookup.lookupGuarded(
                            receiver, selector, caller.preludeOrNullForRuntime());
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            return null;
        }
        if (lookup == null) {
            return null;
        }
        ProtosSlotLookupResult selected = lookup.selected();
        if (!(selected.value() instanceof ProtosClosureValue closure)
                || closure.nativeBody().isEmpty()) {
            lookup.stability().invalidate();
            return null;
        }
        ProtosObjectValue home = selected.home();
        if (canonicalBoolean) {
            // PERF015: a represented Boolean is admitted only for the
            // canonical Boolean callbacks; every other selection stays generic.
            ProtosStandardBooleanProtocol.StructuredCallbackKind canonicalKind =
                    ProtosStandardBooleanProtocol
                            .structuredCallbackKindForCanonicalSelection(closure, home);
            if (canonicalKind == null || !lookup.stability().isValid()) {
                lookup.stability().invalidate();
                return null;
            }
            return new GuardedStructuredSend(
                    closure, home, GuardedStructuredKind.BOOLEAN, canonicalKind,
                    null, lookup.stability());
        }
        GuardedStructuredKind kind;
        ProtosStandardBooleanProtocol.StructuredCallbackKind booleanKind = null;
        ProtosStandardMapProtocol.StructuredReadLookupKind mapReadLookupKind = null;
        if (ProtosStandardObjectProtocol.isCanonicalStandardEnsureSelection(
                closure, home)) {
            kind = GuardedStructuredKind.ENSURE;
        } else if (ProtosStandardErrorProtocol.isCanonicalStandardHandleSelection(
                closure, home, caller)) {
            kind = GuardedStructuredKind.ERROR_HANDLE;
        } else if (ProtosStandardObjectProtocol.isCanonicalStandardPreTestLoopSelection(
                closure, home)) {
            kind = GuardedStructuredKind.WHILE;
        } else if ((booleanKind =
                ProtosStandardBooleanProtocol
                        .structuredCallbackKindForCanonicalSelection(
                                closure, home))
                != null) {
            kind = GuardedStructuredKind.BOOLEAN;
        } else if (ProtosStandardArrayProtocol.isCanonicalStandardEachSelection(
                closure, home, caller)) {
            kind = GuardedStructuredKind.ARRAY_EACH;
        } else if (ProtosStandardBytesProtocol.isCanonicalStandardEachSelection(
                closure, home, caller)) {
            kind = GuardedStructuredKind.BYTES_EACH;
        } else if (ProtosStandardEnvironmentProtocol.isCanonicalStandardEachSelection(
                closure, receiver, home)) {
            kind = GuardedStructuredKind.ENVIRONMENT_EACH;
        } else if (ProtosStandardIdentityMapProtocol.isCanonicalStandardEachSelection(
                closure, home, caller)) {
            kind = GuardedStructuredKind.IDENTITY_MAP_EACH;
        } else if (ProtosStandardMapProtocol.isCanonicalStandardEachSelection(
                closure, home, caller)) {
            kind = GuardedStructuredKind.MAP_EACH;
        } else if ((mapReadLookupKind =
                ProtosStandardMapProtocol
                        .structuredReadLookupKindForCanonicalSelection(
                                closure, home, caller))
                != null) {
            kind = GuardedStructuredKind.MAP_READ_LOOKUP;
        } else if (ProtosStandardMapProtocol.isCanonicalStandardAtPutSelection(
                closure, home, caller)) {
            kind = GuardedStructuredKind.MAP_AT_PUT;
        } else if (ProtosStandardMapProtocol.isCanonicalStandardRemoveSelection(
                closure, home, caller)) {
            kind = GuardedStructuredKind.MAP_REMOVE;
        } else if (ProtosStandardObjectProtocol.isCanonicalStandardCaseOfSelection(
                closure, home)) {
            kind = GuardedStructuredKind.CASE_OF;
        } else if (ProtosStandardArrayProtocol.isCanonicalStandardMatchSelection(
                closure, home, caller)) {
            kind = GuardedStructuredKind.ARRAY_MATCH;
        } else if (ProtosStandardMapProtocol.isCanonicalStandardMatchSelection(
                closure, home, caller)) {
            kind = GuardedStructuredKind.MAP_MATCH;
        } else if (ProtosStandardIdentityMapProtocol.isCanonicalStandardAtIfAbsentSelection(
                closure, home, caller)) {
            kind = GuardedStructuredKind.IDENTITY_MAP_AT_IF_ABSENT;
        } else {
            lookup.stability().invalidate();
            return null;
        }
        if (!lookup.stability().isValid()) {
            return null;
        }
        return new GuardedStructuredSend(
                closure, home, kind, booleanKind, mapReadLookupKind, lookup.stability());
    }

    /**
     * Returns the non-native ordinary Closure selected by {@code selected},
     * or {@code null} when the selection is not an ordinary source-backed
     * Closure. Native Closures (including the canonical standard-import
     * intrinsic) always miss here and remain on the exact generic
     * {@code perform} path.
     */
    static ProtosClosureValue ordinarySendClosureOrNull(ProtosSlotLookupResult selected) {
        if (selected.value() instanceof ProtosClosureValue closure
                && closure.nativeBody().isEmpty()) {
            return closure;
        }
        return null;
    }

    /**
     * Returns the stable executable-identity key for {@code closure}, or
     * {@code null} when there is no selected Closure. Same-definition
     * Closures always rematerialize this exact {@link CanonicalClosure}
     * instance (see the PERF010-A note on {@link ProtosBytecodeRootNode}), so this key stays
     * stable across fresh {@code ProtosClosureValue}/{@code methodHome}
     * materializations of the same executable behavior.
     */
    static CanonicalClosure ordinarySendClosureDefinitionOrNull(ProtosClosureValue closure) {
        return closure == null ? null : closure.definition();
    }

    /**
     * Materializes the effective Context-owned Bytecode activation target
     * for one cached fast-hit specialization instance.
     *
     * <p>This runs once, at cache-population time, and may use the
     * existing Context-owned-plan machinery
     * ({@link ProtosClosureCallPreparation#taskOwnedBytecodePlan}); it is not
     * called again on the resulting hot hit. Returns {@code null} when no
     * safe target can be produced for the current entered Context, which
     * keeps the fast specialization from being instantiated for this call
     * and leaves the exact generic path as the fallback.
     */
    static RootCallTarget fastOrdinarySendTarget(
            ProtosClosureValue closure, ProtosLanguageContext enteredContext) {
        if (enteredContext == null) {
            return null;
        }
        ProtosClosureExecutionPlan plan =
                ProtosClosureCallPreparation.taskOwnedBytecodePlan(
                        closure,
                        enteredContext);
        if (plan == null) {
            return null;
        }
        return plan.bytecodeActivationTargetForComposition();
    }

    /** PERF038-D: see {@link GuardedSendTarget#sharedInvocationHome}. */
    private static ProtosReturnHome sharedInvocationHomeOrNull(ProtosClosureValue closure) {
        if (closure.returnHome().isPresent()) {
            return closure.returnHome().orElseThrow();
        }
        ProtosReturnHome home = closure.invocationReturnHomeForRuntime();
        return home == ProtosReturnHome.unobservable() ? home : null;
    }

    static GuardedSendTarget createGuardedSendForPrelude(
            Object receiver,
            String selector,
            ProtosPrelude prelude,
            ProtosLanguageContext enteredContext) {
        if (enteredContext == null) {
            return null;
        }
        ProtosValueLookup.GuardedLookup lookup;
        try {
            lookup = ProtosValueLookup.lookupGuarded(
                    receiver, selector, prelude);
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            return null;
        }
        if (lookup == null) {
            return null;
        }
        ProtosClosureValue closure =
                ordinarySendClosureOrNull(lookup.selected());
        if (closure == null) {
            lookup.stability().invalidate();
            return null;
        }
        RootCallTarget target =
                fastOrdinarySendTarget(closure, enteredContext);
        if (target == null || !lookup.stability().isValid()) {
            lookup.stability().invalidate();
            return null;
        }
        ProtosReturnHome sharedHome = sharedInvocationHomeOrNull(closure);
        return new GuardedSendTarget(
                closure,
                lookup.selected().home(),
                target,
                lookup.stability(),
                sharedHome,
                sharedHome == null && closure.executionPlan().isPresent(),
                closure.returnHome().isEmpty());
    }
}

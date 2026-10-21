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
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedClosureCall;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.lookup.ProtosSlotLookupResult;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.oracle.truffle.api.RootCallTarget;
import java.util.List;

/**
 * I113: invocation and result preparation for a send whose target was
 * already selected by a {@code PrepareSend*} specialization.
 *
 * <p>Send0 and Send1 keep their fixed-arity entry points so that no
 * supplied-argument array is built on those paths.
 */
final class ProtosGuardedSendExecution {
    private ProtosGuardedSendExecution() {}

    /**
     * PERF038-H: guarded source Send0.
     * PIC guards and selection stability were checked by the DSL.
     */
    static PreparedClosureCall guardedOrdinarySendZero(
            Object receiver,
            ProtosActivation caller,
            GuardedSendTarget cachedSend) {
        ProtosReturnHome returnHome = cachedSend.invocationHome();
        Object[] arguments =
                ProtosFrameArguments.compactImmediateMethodCallZero(
                        cachedSend.closure(),
                        receiver,
                        cachedSend.methodHome(),
                        caller,
                        returnHome);
        return PreparedClosureCall.ordinaryCompactSelected(
                cachedSend.target(),
                arguments,
                returnHome,
                cachedSend.ownsReturnHome());
    }

    /**
     * PERF038-H: guarded source Send1.
     */
    static PreparedClosureCall guardedOrdinarySendOne(
            Object receiver,
            ProtosActivation caller,
            Object supplied0,
            GuardedSendTarget cachedSend) {
        ProtosReturnHome returnHome = cachedSend.invocationHome();
        Object[] arguments =
                ProtosFrameArguments.compactImmediateMethodCallOne(
                        cachedSend.closure(),
                        receiver,
                        cachedSend.methodHome(),
                        caller,
                        returnHome,
                        supplied0);
        return PreparedClosureCall.ordinaryCompactSelected(
                cachedSend.target(),
                arguments,
                returnHome,
                cachedSend.ownsReturnHome());
    }

    public static PreparedClosureCall guardedIntegerSendForActivation(
            Object receiver,
            String selector,
            ProtosActivation caller,
            Object[] supplied,
            ProtosLanguageContext enteredContext,
            ProtosPrelude prelude,
            String cachedSelector,
            ProtosLanguageContext cachedContext,
            ProtosPrelude cachedPrelude,
            GuardedIntegerSend cachedInteger) {
        PreparedClosureCall canonical =
                canonicalIntegerResultOrNull(cachedInteger, receiver, supplied, prelude);
        if (canonical != null) {
            return canonical;
        }

        /*
         * Non-canonical guarded Integer selections, inherited Number
         * operations, wrong-domain operands and Error-producing cases keep
         * the exact PERF027-A path. That preserves the selected Closure,
         * methodHome and rich semantic invocation whenever it is actually
         * observable.
         */
        return ProtosSendPreparation.prepareDeferredImmediateNativeMethodCall(
                cachedInteger.closure(),
                receiver,
                cachedInteger.methodHome(),
                List.of(supplied),
                caller);
    }

    /**
     * PERF038-H: Send0 has no successful binary canonical Integer
     * operation. Preserve its native fallback and Error semantics.
     */
    static PreparedClosureCall guardedIntegerSendZero(
            Object receiver,
            Object caller,
            GuardedIntegerSend cachedInteger) {
        ProtosActivation exactCaller =
                caller instanceof ProtosActivation activation
                        ? activation
                        : ProtosFrameArguments.activation((Object[]) caller);

        return ProtosSendPreparation.prepareDeferredImmediateNativeMethodCall(
                cachedInteger.closure(),
                receiver,
                cachedInteger.methodHome(),
                List.of(),
                exactCaller);
    }

    /**
     * PERF038-H: canonical Send1 needs neither an argument vector
     * nor the caller's activation. Materialize the caller only
     * when the native/Error fallback is actually required.
     */
    static PreparedClosureCall guardedIntegerSendOne(
            Object receiver,
            Object caller,
            Object supplied0,
            GuardedIntegerSend cachedInteger) {
        if (cachedInteger.operation() != null) {
            Object directResult =
                    ProtosStandardIntegerProtocol
                            .tryExecuteCanonicalOperationOne(
                                    cachedInteger.operation(),
                                    receiver,
                                    supplied0,
                                    // I091: owner of a result beyond the signed-64 range.
                                    caller instanceof ProtosActivation entered
                                            ? entered.preludeOrNullForRuntime()
                                            : ProtosFrameArguments.preludeOrNull(
                                                    (Object[]) caller));
            if (directResult != null) {
                return PreparedClosureCall.immediateResult(directResult);
            }
        }

        ProtosActivation exactCaller =
                caller instanceof ProtosActivation activation
                        ? activation
                        : ProtosFrameArguments.activation((Object[]) caller);

        return ProtosSendPreparation.prepareDeferredImmediateNativeMethodCall(
                cachedInteger.closure(),
                receiver,
                cachedInteger.methodHome(),
                List.of(supplied0),
                exactCaller);
    }

    /**
     * The immediate result of a canonical guarded Integer operation, or
     * {@code null} for every case that must take the exact selected native
     * invocation (non-canonical selection, wrong-domain operand, Error).
     * Observes neither the caller activation nor its Context.
     */
    static PreparedClosureCall canonicalIntegerResultOrNull(
            GuardedIntegerSend cachedInteger,
            Object receiver,
            Object[] supplied,
            ProtosPrelude prelude) {
        if (cachedInteger.operation() == null) {
            return null;
        }
        Object directResult =
                ProtosStandardIntegerProtocol.tryExecuteCanonicalOperation(
                        cachedInteger.operation(),
                        receiver,
                        supplied,
                        // I091: owner of a result beyond the signed-64 range.
                        prelude);
        return directResult == null
                ? null
                : PreparedClosureCall.immediateResult(directResult);
    }

    /**
     * PERF038-H: Send0 with dynamically selected source Closure.
     * Preserve the current selected Closure/home, but avoid the
     * supplied-argument vector and compact-header validation.
     */
    static PreparedClosureCall fastOrdinarySendZero(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            ProtosActivation caller,
            RootCallTarget target) {
        ProtosReturnHome returnHome =
                closure.invocationReturnHomeForRuntime();

        return PreparedClosureCall.ordinaryCompactSelected(
                target,
                ProtosFrameArguments.compactImmediateMethodCallZero(
                        closure, receiver, methodHome, caller, returnHome),
                returnHome,
                closure.returnHome().isEmpty());
    }

    /**
     * PERF038-H: Send1 with dynamically selected source Closure.
     * The exact supplied value enters the final frame directly.
     */
    static PreparedClosureCall fastOrdinarySendOne(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            ProtosActivation caller,
            RootCallTarget target,
            Object supplied0) {
        ProtosReturnHome returnHome =
                closure.invocationReturnHomeForRuntime();

        return PreparedClosureCall.ordinaryCompactSelected(
                target,
                ProtosFrameArguments.compactImmediateMethodCallOne(
                        closure, receiver, methodHome, caller,
                        returnHome, supplied0),
                returnHome,
                closure.returnHome().isEmpty());
    }

    public static PreparedClosureCall fastOrdinarySendForActivation(
            Object receiver,
            String selector,
            ProtosActivation caller,
            Object[] supplied,
            ProtosSlotLookupResult selected,
            ProtosClosureValue closure,
            ProtosObjectValue methodHome,
            CanonicalClosure closureDefinition,
            ProtosLanguageContext enteredContext,
            String cachedSelector,
            CanonicalClosure cachedClosureDefinition,
            ProtosLanguageContext cachedContext,
            RootCallTarget cachedTarget) {
        Object[] frameArguments =
                ProtosFrameArguments.compactImmediateMethodCall(
                        closure,
                        receiver,
                        methodHome,
                        caller,
                        supplied);
        return PreparedClosureCall.ordinaryCompact(
                cachedTarget,
                frameArguments);
    }

    /*
     * I072 Phase E: a stable canonical structured native send (Object.ensure,
     * Error.handle, Object.whileTrue, Boolean callbacks) reuses the exact Phase A
     * lookupGuarded/Assumption contract that guardedOrdinarySend already
     * establishes for source-backed Closures. createGuardedStructuredSend
     * runs the authoritative D013 lookup and classifies the selected value
     * against the canonical-selection helpers (identity + home/provenance,
     * not native-body identity alone) exactly once, at specialization
     * establishment. The valid hit below never repeats that lookup or the
     * generic finishPreparingComposedCallByImplementation classifier scan;
     * it feeds the single cached kind directly into the unchanged
     * finishPreparingComposedCall/PreparedClosureCall.nativeCall path, so
     * ensure/handle/while/Boolean execution, suspension, cleanup and error
     * semantics remain byte-identical to the generic path. A selector whose
     * selection is not exactly one canonical standard implementation misses
     * here and falls through to the exact generic {@code perform} path,
     * which preserves override/alias/noncanonical-home behavior unchanged.
     */
    public static PreparedClosureCall guardedStructuredSendForActivation(
            Object receiver,
            String selector,
            ProtosActivation caller,
            Object[] supplied,
            ProtosLanguageContext enteredContext,
            Object cachedReceiver,
            String cachedSelector,
            ProtosLanguageContext cachedContext,
            GuardedStructuredSend cachedStructured) {
        return guardedStructuredSendPrepared(
                receiver, caller, List.of(supplied), cachedStructured);
    }

    /**
     * PERF038-H: Send0, preserving the selected structured kind.
     */
    static PreparedClosureCall guardedStructuredSendZero(
            Object receiver,
            ProtosActivation caller,
            GuardedStructuredSend cachedStructured) {
        return guardedStructuredSendPrepared(
                receiver, caller, List.of(), cachedStructured);
    }

    /**
     * PERF038-H: Send1 without an intermediate Object[].
     */
    static PreparedClosureCall guardedStructuredSendOne(
            Object receiver,
            ProtosActivation caller,
            Object supplied0,
            GuardedStructuredSend cachedStructured) {
        return guardedStructuredSendPrepared(
                receiver, caller, List.of(supplied0), cachedStructured);
    }

    /**
     * Exact existing structured invocation body, shared by the
     * variadic and fixed-arity entry points.
     */
    private static PreparedClosureCall guardedStructuredSendPrepared(
            Object receiver,
            ProtosActivation caller,
            List<?> suppliedList,
            GuardedStructuredSend cachedStructured) {
        ProtosSendPreparation.rejectComposedInvocationProjection(cachedStructured.closure());
        ProtosActivation activation =
                ProtosActivation.forImmediateMethodInvocation(
                        cachedStructured.closure(),
                        suppliedList,
                        receiver,
                        cachedStructured.methodHome(),
                        caller.prelude().orElse(null),
                        caller.actorModuleState(),
                        caller.currentModuleKey().orElse(null),
                        caller.executionDomain());
        ProtosSendPreparation.attachTaskOrInheritDynamicControlState(activation, caller);
        return ProtosSendPreparation.finishPreparingComposedCall(
                cachedStructured.closure(),
                suppliedList,
                activation,
                cachedStructured.kind() == GuardedStructuredKind.ENSURE,
                cachedStructured.kind() == GuardedStructuredKind.ERROR_HANDLE,
                cachedStructured.kind() == GuardedStructuredKind.WHILE
                        ? ProtosStandardObjectProtocol.structuredLoopKindForImplementation(
                                cachedStructured.closure())
                        : null,
                cachedStructured.booleanKind(),
                cachedStructured.kind() == GuardedStructuredKind.ARRAY_EACH,
                cachedStructured.kind() == GuardedStructuredKind.BYTES_EACH,
                cachedStructured.kind() == GuardedStructuredKind.ENVIRONMENT_EACH,
                cachedStructured.kind() == GuardedStructuredKind.IDENTITY_MAP_EACH,
                cachedStructured.kind() == GuardedStructuredKind.MAP_EACH,
                cachedStructured.mapReadLookupKind(),
                cachedStructured.kind() == GuardedStructuredKind.MAP_AT_PUT,
                cachedStructured.kind() == GuardedStructuredKind.MAP_REMOVE,
                false,
                false,
                null);
    }

    public static PreparedClosureCall performForActivation(
            Object receiver,
            String selector,
            ProtosActivation caller,
            Object[] supplied) {
        return ProtosSendPreparation.prepareSend(
                receiver,
                selector,
                caller,
                List.of(supplied));
    }
}

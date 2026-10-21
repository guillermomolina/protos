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

import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedClosureCall;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.lookup.ProtosSlotLookupResult;
import java.util.List;

/**
 * Ordinary, super and immediate-method send preparation: selector lookup,
 * composed-call projection and the prepared call each send site enters.
 */
final class ProtosSendPreparation {
    private ProtosSendPreparation() {}

    /**
     * PERF038-H: generic Send0 without a supplied-argument array.
     */
    static PreparedClosureCall prepareSendZeroFallback(
            Object receiver,
            String selector,
            ProtosActivation caller) {
        return prepareSend(receiver, selector, caller, List.of());
    }

    /**
     * PERF038-H: generic Send1 without a supplied-argument array.
     */
    static PreparedClosureCall prepareSendOneFallback(
            Object receiver,
            String selector,
            ProtosActivation caller,
            Object supplied0) {
        return prepareSend(
                receiver, selector, caller, List.of(supplied0));
    }

    static PreparedClosureCall prepareSend(
            Object receiver,
            String selector,
            ProtosActivation caller,
            List<?> supplied) {
        ProtosSlotLookupResult selected =
                performOrdinarySendLookup(receiver, selector, caller);
        if (ProtosForeignProjectedOperations.isForeignMemberSelection(selected)) {
            // D188: read then ordinary invocation of the read foreign member value.
            receiver = selected.value();
            selected = ProtosForeignProjectedOperations.invocationSelection(receiver, caller);
        }
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(caller));
        }
        return prepareImmediateMethodCall(
                closure,
                receiver,
                selected.home(),
                supplied,
                caller);
    }

    /**
     * Performs the authoritative D013 ordinary-send lookup shared by the
     * generic {@link #prepareSend} path and the {@code PrepareSendArguments}
     * fast-hit specialization, preserving the exact existing lookup-failure
     * and unsupported-representation error semantics.
     */
    static ProtosSlotLookupResult performOrdinarySendLookup(
            Object receiver,
            String selector,
            ProtosActivation caller) {
        ProtosPrelude prelude =
                caller.preludeOrNullForRuntime();
        try {
            var selected =
                    ProtosValueLookup.lookup(
                            receiver,
                            selector,
                            prelude);
            if (selected.isEmpty()) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newSlotNotFound(caller));
            }
            return selected.orElseThrow();
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(caller));
        }
    }

    static PreparedClosureCall prepareSuperSend(
            String selector,
            ProtosActivation caller,
            List<?> supplied) {
        ProtosObjectValue methodHome =
                caller.methodHome()
                        .orElseThrow(
                                () ->
                                        new ProtosSignalException(
                                                ProtosCoreErrors.newInvalidSuper(caller)));
        Object lookupOrigin =
                methodHome.parent()
                        .orElseThrow(
                                () ->
                                        new ProtosSignalException(
                                                ProtosCoreErrors.newSlotNotFound(caller)));

        ProtosPrelude prelude = caller.prelude().orElse(null);
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(lookupOrigin, selector, prelude)
                            .orElseThrow(
                                    () ->
                                            new ProtosSignalException(
                                                    ProtosCoreErrors.newSlotNotFound(caller)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
        }

        return prepareImmediateMethodCall(
                closure,
                caller.receiver(),
                selected.home(),
                supplied,
                caller);
    }

    static PreparedClosureCall prepareImmediateMethodCall(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            List<?> supplied,
            ProtosActivation caller) {
        return prepareImmediateMethodCall(
                closure, receiver, methodHome, supplied, caller, false);
    }

    /**
     * PERF027-A: the guarded Integer native send prepares the same selected
     * method invocation through the PLAT040 deferred activation. The fresh
     * invocation return home is established here exactly as the eager factory
     * would; the guest execution Context and supplied guest Array materialize
     * only if the native body, an Error path, or another observer requests them.
     */
    static PreparedClosureCall prepareDeferredImmediateNativeMethodCall(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            List<?> supplied,
            ProtosActivation caller) {
        return prepareImmediateMethodCall(
                closure, receiver, methodHome, supplied, caller, true);
    }

    static PreparedClosureCall prepareImmediateMethodCall(
            ProtosClosureValue closure,
            Object receiver,
            ProtosObjectValue methodHome,
            List<?> supplied,
            ProtosActivation caller,
            boolean deferGuestInvocationState) {
        ProtosModuleRuntime standardImportRuntime =
                ProtosStandardImportProtocol.selectedRuntimeForBytecodeIntrinsic(
                        receiver,
                        closure,
                        methodHome,
                        caller.prelude().orElse(null));
        if (standardImportRuntime != null) {
            return PreparedClosureCall.moduleInitialization(
                    standardImportRuntime.prepareBytecodeImport(
                            supplied,
                            caller));
        }

        rejectComposedInvocationProjection(closure);
        ProtosActivation activation = deferGuestInvocationState
                ? ProtosActivation.forImmediateMethodInvocationWithReturnHomeForRuntime(
                        closure, supplied, receiver, methodHome, caller.prelude().orElse(null),
                        caller.actorModuleState(), caller.currentModuleKey().orElse(null),
                        caller.executionDomain(),
                        closure.invocationReturnHomeForRuntime())
                : ProtosActivation.forImmediateMethodInvocation(
                        closure, supplied, receiver, methodHome, caller.prelude().orElse(null),
                        caller.actorModuleState(), caller.currentModuleKey().orElse(null),
                        caller.executionDomain());
        attachTaskOrInheritDynamicControlState(
                activation,
                caller);
        return finishPreparingComposedCallByImplementation(
                closure,
                supplied,
                activation);
    }

    static PreparedClosureCall prepareDirectClosureCall(
            ProtosClosureValue closure,
            ProtosActivation caller) {
        return prepareDirectClosureCall(closure, List.of(), caller);
    }

    static PreparedClosureCall prepareDirectClosureCall(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation caller) {
        rejectComposedInvocationProjection(closure);
        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(
                        closure,
                        supplied,
                        caller.prelude().orElse(null),
                        caller.actorModuleState(),
                        caller.currentModuleKey().orElse(null),
                        caller.executionDomain());
        attachTaskOrInheritDynamicControlState(
                activation,
                caller);
        return finishPreparingComposedCallByImplementation(
                closure,
                supplied,
                activation);
    }

    static void attachTaskOrInheritDynamicControlState(
            ProtosActivation activation,
            ProtosActivation caller) {
        if (caller.task().isPresent()) {
            activation.attachTask(
                    caller.task().orElseThrow());
        } else {
            activation.inheritDynamicControlState(caller);
        }
    }

    static void rejectComposedInvocationProjection(
            ProtosClosureValue closure) {
        if (closure.requiresContextLocalExecutionProjectionForRuntime()
                && ProtosLanguageContext.currentIfEnteredForRuntime() == null) {
            com.oracle.truffle.api.CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException(
                    "Context-local Closure projection requires an entered Protos Context");
        }
    }

    static PreparedClosureCall finishPreparingComposedCallByImplementation(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation activation) {
        return finishPreparingComposedCall(
                closure,
                supplied,
                activation,
                ProtosStandardObjectProtocol.isStandardEnsureImplementation(closure),
                ProtosStandardErrorProtocol.isStandardHandleImplementation(closure),
                ProtosStandardObjectProtocol.structuredLoopKindForImplementation(closure),
                ProtosStandardBooleanProtocol.structuredCallbackKindForImplementation(closure),
                ProtosStandardArrayProtocol.isStandardEachImplementation(closure),
                ProtosStandardBytesProtocol.isStandardEachImplementation(closure),
                ProtosStandardEnvironmentProtocol.isStandardEachImplementation(closure),
                ProtosStandardIdentityMapProtocol.isStandardEachImplementation(closure),
                ProtosStandardMapProtocol.isStandardEachImplementation(closure),
                ProtosStandardMapProtocol.structuredReadLookupKindForImplementation(closure),
                ProtosStandardMapProtocol.isStandardAtPutImplementation(closure),
                ProtosStandardMapProtocol.isStandardRemoveImplementation(closure),
                ProtosStandardErrorProtocol.isStandardSignalImplementation(closure),
                ProtosStandardObjectProtocol.isStandardCallImplementation(closure),
                ProtosStandardImportProtocol.runtimeForImplementation(closure));
    }

    static PreparedClosureCall finishPreparingComposedCall(
            ProtosClosureValue closure,
            List<?> supplied,
            ProtosActivation activation,
            boolean structuredEnsure,
            boolean structuredErrorHandler,
            ProtosStandardObjectProtocol.StructuredLoopKind structuredWhile,
            ProtosStandardBooleanProtocol.StructuredCallbackKind structuredBoolean,
            boolean structuredArrayEach,
            boolean structuredBytesEach,
            boolean structuredEnvironmentEach,
            boolean structuredIdentityMapEach,
            boolean structuredMapEach,
            ProtosStandardMapProtocol.StructuredReadLookupKind structuredMapReadLookup,
            boolean structuredMapAtPut,
            boolean structuredMapRemove,
            boolean directControlNative,
            boolean structuredObjectCall,
            ProtosModuleRuntime structuredImportRuntime) {
        if (closure.nativeBody().isPresent()) {
            ProtosNativeClosureBody nativeBody =
                    closure.nativeBody().orElseThrow();
            /*
             * PLAT019 keeps the ordinary non-suspending native path direct and
             * pay-only-when-used. Only bodies carrying the explicit suspension
             * capability enter executeForBytecodeContinuation(); ordinary native
             * bodies execute synchronously through PreparedClosureCall.enterNative().
             * PLAT021 structured-control provenance remains independently guarded.
             */
            return PreparedClosureCall.nativeCall(
                    nativeBody,
                    supplied,
                    activation,
                    structuredEnsure,
                    structuredErrorHandler,
                    structuredWhile,
                    structuredBoolean,
                    structuredArrayEach,
                    structuredBytesEach,
                    structuredEnvironmentEach,
                    structuredIdentityMapEach,
                    structuredMapEach,
                    structuredMapReadLookup,
                    structuredMapAtPut,
                    structuredMapRemove,
                    directControlNative,
                    structuredObjectCall,
                    structuredImportRuntime);
        }
        if (structuredEnsure
                || structuredErrorHandler
                || structuredWhile != null
                || structuredBoolean != null
                || structuredArrayEach
                || structuredBytesEach
                || structuredEnvironmentEach
                || structuredIdentityMapEach
                || structuredMapEach
                || structuredMapReadLookup != null
                || structuredMapAtPut
                || structuredMapRemove
                || directControlNative
                || structuredObjectCall
                || structuredImportRuntime != null) {
            throw new IllegalStateException(
                    "structured control capability requires the canonical native implementation");
        }
        ProtosClosureExecutionPlan plan = closure.executionPlanForRuntimeInvocation();
        ProtosLanguageContext enteredContext =
                ProtosLanguageContext.currentIfEnteredForRuntime();
        if (enteredContext != null) {
            boolean foreignContextPlan =
                    plan.language().orElseThrow()
                            != enteredContext.languageForRuntime();
            if (closure.requiresContextLocalExecutionProjectionForRuntime()
                    || foreignContextPlan) {
                if (plan.source().isEmpty()) {
                    throw new UnsupportedOperationException(
                            "Bytecode composition cannot project a source-less Closure plan");
                }
                plan =
                        enteredContext.bytecodeExecutionPlanForEnteredClosure(
                                closure,
                                plan);
            }
        }
        return PreparedClosureCall.ordinary(plan.bytecodeActivationTargetForComposition(), activation);
    }
}

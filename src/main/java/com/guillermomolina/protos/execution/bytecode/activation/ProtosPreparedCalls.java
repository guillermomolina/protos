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

import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedBooleanCall;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedCaseOfCall;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedEnsureCall;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedErrorHandlerCall;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedStandardImportCall;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedStandardObjectCall;
import com.guillermomolina.protos.execution.ProtosStructuredControlCalls.PreparedWhileCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedArrayEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedArrayMatchCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedBytesEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedEnvironmentEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedIdentityMapAtIfAbsentCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedIdentityMapEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredEachCalls.PreparedMapEachCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapQueryCalls.PreparedMapMatchCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapQueryCalls.PreparedMapReadLookupCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapUpdateCalls.PreparedMapAtPutCall;
import com.guillermomolina.protos.execution.ProtosStructuredMapUpdateCalls.PreparedMapRemoveCall;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosDynamicControlState;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.callables.ProtosSuspensionCapableNativeClosureBody;
import com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.ControlFlowException;
import java.util.ArrayList;
import java.util.List;

/**
 * The prepared Closure-call protocol: the {@link PreparedClosureCall} carrier
 * contract and its ordinary-source, native, immediate-result and module-initialization
 * representations, with the supplied-argument vector they are prepared from.
 *
 * <p>I111-B: formerly nested in the second generated interpreter; these representations
 * share one private ReturnHome-owning base and therefore stay together.</p>
 */
final class ProtosPreparedCalls {
    private ProtosPreparedCalls() {}

    /**
     * Optional structured-control capability carried by a {@link NativeCall}.
     *
     * <p>Ordinary source-backed and plain-native calls never allocate this holder
     * (PLAT040 Candidate F′: optional semantic capability must not force unrelated
     * ordinary calls to carry its full physical machinery). It exists only when a
     * call actually owns one of the mutually exclusive structured-control,
     * collection-callback, or import capabilities below.
     */
    private static final class StructuredCallCapabilities {
        final boolean ensure;
        final boolean errorHandler;
        final ProtosStandardObjectProtocol.StructuredLoopKind whileLoop;
        final ProtosStandardBooleanProtocol.StructuredCallbackKind booleanKind;
        final boolean arrayEach;
        final boolean bytesEach;
        final boolean environmentEach;
        final boolean identityMapEach;
        final boolean mapEach;
        final ProtosStandardMapProtocol.StructuredReadLookupKind mapReadLookup;
        final boolean mapAtPut;
        final boolean mapRemove;
        final boolean directControlNative;
        final boolean objectCall;
        final ProtosModuleRuntime importRuntime;

        private StructuredCallCapabilities(
                boolean ensure,
                boolean errorHandler,
                ProtosStandardObjectProtocol.StructuredLoopKind whileLoop,
                ProtosStandardBooleanProtocol.StructuredCallbackKind booleanKind,
                boolean arrayEach,
                boolean bytesEach,
                boolean environmentEach,
                boolean identityMapEach,
                boolean mapEach,
                ProtosStandardMapProtocol.StructuredReadLookupKind mapReadLookup,
                boolean mapAtPut,
                boolean mapRemove,
                boolean directControlNative,
                boolean objectCall,
                ProtosModuleRuntime importRuntime) {
            this.ensure = ensure;
            this.errorHandler = errorHandler;
            this.whileLoop = whileLoop;
            this.booleanKind = booleanKind;
            this.arrayEach = arrayEach;
            this.bytesEach = bytesEach;
            this.environmentEach = environmentEach;
            this.identityMapEach = identityMapEach;
            this.mapEach = mapEach;
            this.mapReadLookup = mapReadLookup;
            this.mapAtPut = mapAtPut;
            this.mapRemove = mapRemove;
            this.directControlNative = directControlNative;
            this.objectCall = objectCall;
            this.importRuntime = importRuntime;
            int controlCapabilities =
                    (ensure ? 1 : 0)
                            + (errorHandler ? 1 : 0)
                            + (whileLoop != null ? 1 : 0)
                            + (booleanKind != null ? 1 : 0)
                            + (arrayEach ? 1 : 0)
                            + (bytesEach ? 1 : 0)
                            + (environmentEach ? 1 : 0)
                            + (identityMapEach ? 1 : 0)
                            + (mapEach ? 1 : 0)
                            + (mapReadLookup != null ? 1 : 0)
                            + (mapAtPut ? 1 : 0)
                            + (mapRemove ? 1 : 0)
                            + (directControlNative ? 1 : 0)
                            + (objectCall ? 1 : 0)
                            + (importRuntime != null ? 1 : 0);
            if (controlCapabilities > 1) {
                throw new IllegalArgumentException(
                        "prepared Closure call cannot own multiple structured-control capabilities");
            }
        }

        /** Returns {@code null} when every capability is at its default/unset value. */
        static StructuredCallCapabilities of(
                boolean ensure,
                boolean errorHandler,
                ProtosStandardObjectProtocol.StructuredLoopKind whileLoop,
                ProtosStandardBooleanProtocol.StructuredCallbackKind booleanKind,
                boolean arrayEach,
                boolean bytesEach,
                boolean environmentEach,
                boolean identityMapEach,
                boolean mapEach,
                ProtosStandardMapProtocol.StructuredReadLookupKind mapReadLookup,
                boolean mapAtPut,
                boolean mapRemove,
                boolean directControlNative,
                boolean objectCall,
                ProtosModuleRuntime importRuntime) {
            boolean any =
                    ensure
                            || errorHandler
                            || whileLoop != null
                            || booleanKind != null
                            || arrayEach
                            || bytesEach
                            || environmentEach
                            || identityMapEach
                            || mapEach
                            || mapReadLookup != null
                            || mapAtPut
                            || mapRemove
                            || directControlNative
                            || objectCall
                            || importRuntime != null;
            if (!any) {
                return null;
            }
            return new StructuredCallCapabilities(
                    ensure,
                    errorHandler,
                    whileLoop,
                    booleanKind,
                    arrayEach,
                    bytesEach,
                    environmentEach,
                    identityMapEach,
                    mapEach,
                    mapReadLookup,
                    mapAtPut,
                    mapRemove,
                    directControlNative,
                    objectCall,
                    importRuntime);
        }
    }

    /**
     * A prepared, about-to-execute Closure invocation.
     *
     * <p>I072 Phase D (PLAT040 Candidate F′): this is a small dispatch surface
     * shared by every call shape, not a universal physical carrier. Exactly one
     * of four leaf shapes implements it per call — {@link OrdinarySourceCall},
     * {@link NativeCall}, {@link ImmediateResultCall}, or {@link
     * ModuleInitializationCall} — and each leaf physically carries only the
     * state its own shape actually uses. An
     * ordinary source-backed call never allocates native-body, structured-
     * control-capability, or module-initialization state; those remain
     * exclusive to the two special shapes that actually need them. Interface
     * default methods below only supply the "this capability is absent"
     * answer for shapes that never own it; they add no per-instance state.
     */
    interface PreparedClosureCall {
        RootCallTarget bodyTarget();

        ProtosActivation activation();

        Object[] targetArguments();

        ProtosTask taskForRuntime();

        default boolean isNative() { return false; }

        default boolean isImmediate() { return false; }

        default Object enterImmediate() {
            throw new IllegalStateException("prepared call has no immediate result");
        }

        default Object enterNative() {
            throw new IllegalStateException("prepared Closure call is not native");
        }

        default boolean requiresStructuredDispatch() { return false; }

        default boolean isStructuredObjectCall() { return false; }
        default boolean isStructuredCaseOf() { return false; }
        default boolean isStructuredMapMatch() { return false; }
        default boolean isStructuredImportCall() { return false; }
        default boolean isStructuredEnsure() { return false; }
        default boolean isStructuredErrorHandler() { return false; }
        default boolean isStructuredWhile() { return false; }
        default boolean isStructuredBoolean() { return false; }
        default boolean isStructuredArrayEach() { return false; }
        default boolean isStructuredArrayMatch() { return false; }
        default boolean isStructuredBytesEach() { return false; }
        default boolean isStructuredEnvironmentEach() { return false; }
        default boolean isStructuredIdentityMapAtIfAbsent() { return false; }
        default boolean isStructuredIdentityMapEach() { return false; }
        default boolean isStructuredMapEach() { return false; }
        default boolean isStructuredMapReadLookup() { return false; }
        default boolean isStructuredMapAtPut() { return false; }
        default boolean isStructuredMapRemove() { return false; }
        default boolean isStructuredForeignEach() { return false; }

        /**
         * PLAT044 B′ (PERF026-C1) whole-pair admission of a selected standard
         * whileTrue; see {@link NativeCall#admitsInlineLiteralWhile}.
         */
        default boolean admitsInlineLiteralWhile(Object condition, Object body) {
            return false;
        }

        /**
         * PLAT044 B′ (PERF026-D1) whole-call admission of a selected standard
         * Array.each or Bytes.each; see {@link
         * NativeCall#admitsInlineLiteralIndexedEach}.
         */
        default boolean admitsInlineLiteralIndexedEach(Object callback) {
            return false;
        }

        /**
         * PLAT044 B′ (PERF026-D2) whole-call admission of a selected standard
         * Map.each or IdentityMap.each; see {@link
         * NativeCall#admitsInlineLiteralAssociationEach}.
         */
        default boolean admitsInlineLiteralAssociationEach(Object callback) {
            return false;
        }

        /**
         * PLAT044 B′ (PERF026-D3) whole-call admission of a selected standard
         * Environment.each; see {@link
         * NativeCall#admitsInlineLiteralEnvironmentEach}.
         */
        default boolean admitsInlineLiteralEnvironmentEach(Object callback) {
            return false;
        }

        default PreparedMapMatchCall prepareStructuredMapMatch() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Map.match capability");
        }

        default PreparedCaseOfCall prepareStructuredCaseOf() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Object.caseOf capability");
        }

        default PreparedStandardObjectCall prepareStructuredObjectCall() {
            throw new IllegalStateException(
                    "prepared Closure call has no PLAT032 Object.call capability");
        }

        default PreparedStandardImportCall prepareStructuredImportCall() {
            throw new IllegalStateException(
                    "prepared Closure call has no PLAT032 import capability");
        }

        default PreparedEnsureCall prepareStructuredEnsure() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured ensure capability");
        }

        default PreparedWhileCall prepareStructuredWhile() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured while capability");
        }

        default PreparedErrorHandlerCall prepareStructuredErrorHandler() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Error.handle capability");
        }

        default PreparedBooleanCall prepareStructuredBoolean() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Boolean callback capability");
        }

        default PreparedArrayEachCall prepareStructuredArrayEach() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Array.each capability");
        }

        default PreparedArrayMatchCall prepareStructuredArrayMatch() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Array.match capability");
        }

        default PreparedBytesEachCall prepareStructuredBytesEach() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Bytes.each capability");
        }

        default PreparedEnvironmentEachCall prepareStructuredEnvironmentEach() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Environment.each capability");
        }

        default PreparedIdentityMapAtIfAbsentCall prepareStructuredIdentityMapAtIfAbsent() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured IdentityMap.atIfAbsent capability");
        }

        default PreparedIdentityMapEachCall prepareStructuredIdentityMapEach() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured IdentityMap.each capability");
        }

        default PreparedMapEachCall prepareStructuredMapEach() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Map.each capability");
        }

        default PreparedMapReadLookupCall prepareStructuredMapReadLookup() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Map read-lookup capability");
        }

        default PreparedMapAtPutCall prepareStructuredMapAtPut() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Map.atPut capability");
        }

        default PreparedMapRemoveCall prepareStructuredMapRemove() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured Map.remove capability");
        }

        default ProtosForeignEachCall prepareStructuredForeignEach() {
            throw new IllegalStateException(
                    "prepared Closure call has no structured foreign each capability");
        }

        Object handleControlTransfer(ControlFlowException transfer);

        void failIfModuleInitialization();

        RuntimeException mapRuntimeFailure(RuntimeException failure);

        void complete();

        Object finish(Object result);

        static PreparedClosureCall ordinary(RootCallTarget bodyTarget, ProtosActivation activation) {
            return new OrdinarySourceCall(bodyTarget, activation);
        }

        static PreparedClosureCall ordinaryCompact(
                RootCallTarget bodyTarget, Object[] compactTargetArguments) {
            return new OrdinarySourceCall(bodyTarget, compactTargetArguments);
        }

        /**
         * PERF038-D: {@link #ordinaryCompact} for frame arguments the caller
         * has just built with {@link ProtosFrameArguments#compactImmediateMethodCall}
         * from {@code returnHome} and {@code suppliedArgumentCount} supplied
         * values; {@code ownsReturnHome} is exactly {@link
         * ProtosFrameArguments#compactOwnsReturnHome} of that array.
         */
        static PreparedClosureCall ordinaryCompactSelected(
                RootCallTarget selectedEntryTarget,
                Object[] compactTargetArguments,
                ProtosReturnHome returnHome,
                boolean ownsReturnHome) {
            return new OrdinarySourceCall(
                    selectedEntryTarget,
                    compactTargetArguments,
                    returnHome,
                    ownsReturnHome);
        }

        static PreparedClosureCall ordinaryCompactPrepared(
                RootCallTarget bodyTarget,
                Object[] compactTargetArguments,
                ProtosReturnHome returnHome,
                boolean ownsReturnHome,
                int suppliedArgumentCount) {
            return new OrdinarySourceCall(
                    bodyTarget,
                    compactTargetArguments,
                    returnHome,
                    ownsReturnHome,
                    suppliedArgumentCount);
        }

        static PreparedClosureCall nativeCall(
                ProtosNativeClosureBody nativeBody,
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
            return new NativeCall(
                    java.util.Objects.requireNonNull(nativeBody, "nativeBody"),
                    supplied,
                    activation,
                    StructuredCallCapabilities.of(
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
                            structuredImportRuntime));
        }

        static PreparedClosureCall immediateResult(Object result) {
            return new ImmediateResultCall(result);
        }

        static PreparedClosureCall moduleInitialization(
                ProtosModuleRuntime.PreparedModuleInitialization moduleInitialization) {
            return new ModuleInitializationCall(moduleInitialization);
        }
    }

    /**
     * Shared return-home ownership/completion behavior for the two prepared-call
     * shapes that actually own a {@link ProtosReturnHome}: {@link
     * OrdinarySourceCall} and {@link NativeCall}. {@link ModuleInitializationCall}
     * has no return-home concept and implements {@link PreparedClosureCall}
     * directly instead of extending this class.
     */
    private abstract static class ReturnHomeOwningCall implements PreparedClosureCall {
        private final ProtosReturnHome returnHome;
        /**
         * PERF025: an owned but non-materialized home has no lifecycle and can
         * never be a non-local-return target, so it is neither matched nor
         * completed.
         */
        private final boolean ownsMaterializedReturnHome;

        ReturnHomeOwningCall(ProtosReturnHome returnHome, boolean ownsReturnHome) {
            /*
             * PERF038-H: ordinary calls that cannot own a physical return
             * home have no return-home lifecycle or target to retain.
             * Captured homes still travel in the callee frame arguments,
             * where their provenance remains observable when required.
             */
            boolean hasLifecycle = ownsReturnHome && returnHome.isMaterialized();
            this.returnHome = hasLifecycle ? returnHome : null;
            this.ownsMaterializedReturnHome = hasLifecycle;
        }

        /**
         * PERF038-E: false when this call owns no materialized home, so
         * {@link #complete} and {@link #finish} have no effect beyond answering
         * the result and {@link #handleControlTransfer} rethrows every transfer.
         * Final for the carrier.
         */
        final boolean ownsMaterializedReturnHome() {
            return ownsMaterializedReturnHome;
        }

        @Override
        public Object handleControlTransfer(ControlFlowException transfer) {
            if (transfer instanceof ProtosNonLocalReturnException nonLocalReturn
                    && ownsMaterializedReturnHome
                    && returnHome.isActive()
                    && nonLocalReturn.target() == returnHome) {
                return nonLocalReturn.value();
            }
            throw transfer;
        }

        @Override
        public void failIfModuleInitialization() {
            // Neither shape extending this class owns module-initialization state.
        }

        @Override
        public RuntimeException mapRuntimeFailure(RuntimeException failure) {
            return failure;
        }

        @Override
        public void complete() {
            if (ownsMaterializedReturnHome && returnHome.isActive()) {
                returnHome.complete();
            }
        }

        @Override
        public Object finish(Object result) {
            complete();
            return result;
        }
    }

    /**
     * The lean, pay-only-when-used representation of an ordinary source-backed
     * Closure call. It carries exactly a selected target, the B5 frame
     * arguments (for the pre-target rich-activation shape, with that
     * activation already published in argument 0), and the
     * return-home/ownership state that non-local return
     * completion actually requires. It never carries native-body, structured-
     * control-capability, or module-initialization state.
     */
    static final class OrdinarySourceCall extends ReturnHomeOwningCall {
        private final RootCallTarget bodyTarget;
        private final ProtosActivation activation;
        private final Object[] targetArguments;

        OrdinarySourceCall(RootCallTarget bodyTarget, ProtosActivation activation) {
            super(
                    java.util.Objects.requireNonNull(activation, "activation")
                            .returnHome()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Closure invocation requires a return home")),
                    activation.ownsReturnHome());
            this.bodyTarget = java.util.Objects.requireNonNull(bodyTarget, "bodyTarget");
            this.activation = activation;
            // I102: a rich entry enters through the same B5 header, already published.
            this.targetArguments = ProtosFrameArguments.publishedInvocation(activation);
        }

        OrdinarySourceCall(RootCallTarget bodyTarget, Object[] compactTargetArguments) {
            super(
                    ProtosFrameArguments.compactReturnHome(
                            java.util.Objects.requireNonNull(
                                    compactTargetArguments, "compactTargetArguments")),
                    ProtosFrameArguments.compactOwnsReturnHome(compactTargetArguments));
            this.bodyTarget = java.util.Objects.requireNonNull(bodyTarget, "bodyTarget");
            this.activation = null;
            this.targetArguments = compactTargetArguments;
        }

        /** PERF038-H: the guarded PIC has already selected the exact source entry target. */
        OrdinarySourceCall(
                RootCallTarget selectedEntryTarget,
                Object[] compactTargetArguments,
                ProtosReturnHome returnHome,
                boolean ownsReturnHome) {
            super(returnHome, ownsReturnHome);
            this.bodyTarget = selectedEntryTarget;
            this.activation = null;
            this.targetArguments = compactTargetArguments;
        }

        /** PERF038-D: see {@link PreparedClosureCall#ordinaryCompactPrepared}. */
        OrdinarySourceCall(
                RootCallTarget bodyTarget,
                Object[] compactTargetArguments,
                ProtosReturnHome returnHome,
                boolean ownsReturnHome,
                int suppliedArgumentCount) {
            super(returnHome, ownsReturnHome);
            this.bodyTarget = bodyTarget;
            this.activation = null;
            this.targetArguments = compactTargetArguments;
        }

        /**
         * PERF038-E: the {@code FinishClosureCall} guard; declared here because
         * the DSL-generated node cannot see members of the private base class.
         */
        boolean hasReturnHomeLifecycle() {
            return ownsMaterializedReturnHome();
        }

        /**
         * PERF038-H: read the capability of the actual selected
         * source entry. Unknown targets
         * retain the general continuation protocol.
         */


        /** PERF038-H: capability of the selected source entry. */
        boolean provablyNonSuspending() {
            return bodyTarget.getRootNode()
                    instanceof ProtosBytecodeRootNode root
                    && root.provablyNonSuspendingBody();
        }

        @Override
        public RootCallTarget bodyTarget() { return bodyTarget; }

        @Override
        public ProtosActivation activation() {
            if (activation == null) {
                throw new IllegalStateException(
                        "compact source call has no pre-target rich activation");
            }
            return activation;
        }

        @Override
        public Object[] targetArguments() { return targetArguments; }

        /*
         * I101-B: a minimal source root may return a primitive carrier.
         * Do not expose it through an ordinary guest call. Fused callers
         * that explicitly accept primitive results retain that fast path.
         */
        @Override
        public Object finish(Object result) {
            // I101-B: project only this NORMAL local-root carrier at a guest boundary.
            if (bodyTarget.getRootNode() instanceof ProtosBytecodeRootNode root
                    && root.directScalarLocalCarrierBody()
                    && !ProtosFrameArguments.acceptsPrimitiveResult(targetArguments)) {
                result = ProtosNumericValueSupport.guestValue(result);
            }
            return super.finish(
                    ProtosFrameArguments.acceptsPrimitiveResult(targetArguments)
                            ? result
                            : ProtosNumericValueSupport.guestValue(result));
        }

        @Override
        public ProtosTask taskForRuntime() {
            if (activation != null) {
                return activation.task().orElse(null);
            }
            return ProtosFrameArguments.compactTask(targetArguments);
        }
    }

    /**
     * The special-call representation for a native Closure body, optionally
     * carrying exactly one mutually exclusive {@link StructuredCallCapabilities}.
     * Ordinary source calls never instantiate this class.
     */
    static final class NativeCall extends ReturnHomeOwningCall {
        private final ProtosNativeClosureBody nativeBody;
        private final List<?> supplied;
        private final ProtosActivation activation;
        private final StructuredCallCapabilities structured;

        NativeCall(
                ProtosNativeClosureBody nativeBody,
                List<?> supplied,
                ProtosActivation activation,
                StructuredCallCapabilities structured) {
            super(
                    java.util.Objects.requireNonNull(activation, "activation")
                            .returnHome()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Closure invocation requires a return home")),
                    activation.ownsReturnHome());
            this.nativeBody = java.util.Objects.requireNonNull(nativeBody, "nativeBody");
            this.supplied = List.copyOf(supplied);
            this.activation = activation;
            this.structured = structured;
        }

        @Override
        public RootCallTarget bodyTarget() { return null; }

        @Override
        public ProtosActivation activation() { return activation; }

        @Override
        public Object[] targetArguments() {
            throw new IllegalStateException("prepared call has no source target arguments");
        }

        @Override
        public ProtosTask taskForRuntime() { return activation.task().orElse(null); }

        @Override
        public boolean isNative() { return true; }

        @Override
        public boolean isStructuredObjectCall() { return structured != null && structured.objectCall; }

        @Override
        public boolean isStructuredCaseOf() {
            return ProtosStandardObjectProtocol.isStandardCaseOfImplementation(nativeBody);
        }

        @Override
        public boolean isStructuredMapMatch() {
            return ProtosStandardMapProtocol.isStandardMatchImplementation(nativeBody);
        }

        @Override
        public boolean isStructuredImportCall() {
            return structured != null && structured.importRuntime != null;
        }

        @Override
        public boolean isStructuredEnsure() { return structured != null && structured.ensure; }

        @Override
        public boolean isStructuredErrorHandler() {
            return structured != null && structured.errorHandler;
        }

        @Override
        public boolean isStructuredWhile() { return structured != null && structured.whileLoop != null; }

        /**
         * PLAT044 B′ (PERF026-C1): true only when ordinary selection prepared
         * the canonical standard whileTrue capability and its receiver and
         * sole supplied value are exactly the send site's staged {@code
         * condition} and {@code body} literal values, and neither literal
         * would fail the per-child B′ proof for a reason already known now:
         * an owned return home (no captured one) or a required Context-local
         * plan projection. A pure read of the already prepared call and the
         * literal values: no activation is prepared and nothing is validated,
         * so condition/body preparation timing is unchanged, and any other
         * shape keeps the structured-dispatch while. A per-child proof that
         * still fails later (for example a foreign-Context plan) keeps that
         * child's exact physical invocation.
         */
        @Override
        public boolean admitsInlineLiteralWhile(Object condition, Object body) {
            return isStructuredWhile()
                    && !structured.whileLoop.postTest()
                    && activation.receiver() == condition
                    && supplied.size() == 1
                    && supplied.get(0) == body
                    && isInlineLiteralCallbackValue(condition)
                    && isInlineLiteralCallbackValue(body);
        }

        /**
         * PLAT044 B′ (PERF026-D1): true only when ordinary selection prepared
         * the canonical standard Array.each or Bytes.each capability, its sole
         * supplied value is exactly the send site's staged one-parameter
         * {@code callback} literal value, and that literal would not fail the
         * per-element B′ proof for a reason already known now (as in {@link
         * #admitsInlineLiteralWhile}). A pure read: the receiver and callback
         * are not validated here and no snapshot or activation is created, so
         * {@code PreparedArrayEachCall}/{@code PreparedBytesEachCall} keep sole
         * authority over validation order, the snapshot and every per-element
         * activation.
         */
        @Override
        public boolean admitsInlineLiteralIndexedEach(Object callback) {
            return (isStructuredArrayEach() || isStructuredBytesEach())
                    && supplied.size() == 1
                    && supplied.get(0) == callback
                    && isInlineLiteralCallbackValue(callback);
        }

        /**
         * PLAT044 B′ (PERF026-D2): the {@link #admitsInlineLiteralIndexedEach}
         * proof for the canonical standard Map.each or IdentityMap.each
         * capability and the send site's staged two-parameter {@code
         * callback} literal value. Equally a pure read: {@code
         * PreparedMapEachCall}/{@code PreparedIdentityMapEachCall} keep sole
         * authority over receiver and callability validation, the association
         * snapshot and every per-entry activation, and callback arity is not
         * examined here.
         */
        @Override
        public boolean admitsInlineLiteralAssociationEach(Object callback) {
            return (isStructuredMapEach() || isStructuredIdentityMapEach())
                    && supplied.size() == 1
                    && supplied.get(0) == callback
                    && isInlineLiteralCallbackValue(callback);
        }

        /**
         * PLAT044 B′ (PERF026-D3): the {@link #admitsInlineLiteralIndexedEach}
         * proof for the canonical standard Environment.each capability and the
         * send site's staged two-parameter {@code callback} literal value.
         * Equally a pure read: no callability check, portable conversion,
         * ordering or activation happens here, so {@code
         * PreparedEnvironmentEachCall} keeps sole authority over receiver and
         * callability validation, the complete portable canonically ordered
         * snapshot established before callback #1 and every per-entry
         * activation, and callback arity is not examined here.
         *
         * <p>The structured capability may have been classified by the
         * standard each implementation alone (the generic preparation path),
         * which a copy of that Closure at another home shares; admission
         * therefore re-proves the canonical home from this call's own
         * receiver and method home, so a copied each keeps its ordinary path.
         */
        @Override
        public boolean admitsInlineLiteralEnvironmentEach(Object callback) {
            return isStructuredEnvironmentEach()
                    && activation.methodHome()
                            .map(home ->
                                    ProtosStandardEnvironmentProtocol.isCanonicalStandardEachHome(
                                            activation.receiver(), home))
                            .orElse(false)
                    && supplied.size() == 1
                    && supplied.get(0) == callback
                    && isInlineLiteralCallbackValue(callback);
        }

        private static boolean isInlineLiteralCallbackValue(Object value) {
            return value instanceof ProtosClosureValue closure
                    && closure.returnHome().isPresent()
                    && !closure.requiresContextLocalExecutionProjectionForRuntime();
        }

        @Override
        public boolean isStructuredBoolean() {
            return structured != null && structured.booleanKind != null;
        }

        @Override
        public boolean isStructuredArrayEach() { return structured != null && structured.arrayEach; }

        @Override
        public boolean isStructuredArrayMatch() {
            return ProtosStandardArrayProtocol.isStandardMatchImplementation(nativeBody);
        }

        @Override
        public boolean isStructuredBytesEach() { return structured != null && structured.bytesEach; }

        @Override
        public boolean isStructuredEnvironmentEach() {
            return structured != null && structured.environmentEach;
        }

        @Override
        public boolean isStructuredIdentityMapAtIfAbsent() {
            return ProtosStandardIdentityMapProtocol.isStandardAtIfAbsentImplementation(nativeBody);
        }

        @Override
        public boolean isStructuredIdentityMapEach() {
            return structured != null && structured.identityMapEach;
        }

        @Override
        public boolean isStructuredMapEach() { return structured != null && structured.mapEach; }

        @Override
        public boolean isStructuredMapReadLookup() {
            return structured != null && structured.mapReadLookup != null;
        }

        @Override
        public boolean isStructuredMapAtPut() { return structured != null && structured.mapAtPut; }

        @Override
        public boolean isStructuredMapRemove() { return structured != null && structured.mapRemove; }

        /**
         * D188 projected foreign {@code each}, recognized by its private projection body like
         * Array.match: the body is reachable only through the foreign projection, and the cursor
         * itself re-validates the receiver.
         */
        @Override
        public boolean isStructuredForeignEach() {
            return ProtosForeignProjectedOperations.isEachImplementation(nativeBody);
        }

        @Override
        public boolean requiresStructuredDispatch() {
            if (structured != null
                    && (structured.objectCall
                            || structured.importRuntime != null
                            || structured.ensure
                            || structured.errorHandler
                            || structured.whileLoop != null
                            || structured.booleanKind != null
                            || structured.arrayEach
                            || structured.bytesEach
                            || structured.environmentEach
                            || structured.identityMapEach
                            || structured.mapEach
                            || structured.mapReadLookup != null
                            || structured.mapAtPut
                            || structured.mapRemove)) {
                return true;
            }
            return isStructuredCaseOf()
                    || isStructuredMapMatch()
                    || isStructuredArrayMatch()
                    || isStructuredIdentityMapAtIfAbsent()
                    || isStructuredForeignEach();
        }

        @Override
        public PreparedMapMatchCall prepareStructuredMapMatch() {
            if (!isStructuredMapMatch()) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Map.match capability");
            }
            return new PreparedMapMatchCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedCaseOfCall prepareStructuredCaseOf() {
            if (!isStructuredCaseOf()) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Object.caseOf capability");
            }
            return new PreparedCaseOfCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedStandardObjectCall prepareStructuredObjectCall() {
            if (structured == null || !structured.objectCall) {
                throw new IllegalStateException(
                        "prepared Closure call has no PLAT032 Object.call capability");
            }

            Object receiver = activation.receiver();
            if (receiver instanceof ProtosClosureValue targetClosure) {
                return PreparedStandardObjectCall.closure(
                        ProtosSendPreparation.prepareDirectClosureCall(
                                targetClosure,
                                supplied,
                                activation));
            }
            if (receiver instanceof ProtosObjectValue prototype
                    && !ProtosNumericValueSupport.isCurrentNumber(receiver)) {
                ProtosObjectValue instance = new ProtosObjectValue(prototype);
                return PreparedStandardObjectCall.construction(
                        ProtosSendPreparation.prepareSend(
                                instance,
                                "init",
                                activation,
                                supplied),
                        instance);
            }
            throw ProtosCoreErrors.signal(
                    activation,
                    ProtosCoreErrors.newError(activation));
        }

        @Override
        public PreparedStandardImportCall prepareStructuredImportCall() {
            if (structured == null || structured.importRuntime == null) {
                throw new IllegalStateException(
                        "prepared Closure call has no PLAT032 import capability");
            }
            return new PreparedStandardImportCall(
                    PreparedClosureCall.moduleInitialization(
                            structured.importRuntime.prepareBytecodeImport(
                                    supplied,
                                    activation)));
        }

        @Override
        public PreparedEnsureCall prepareStructuredEnsure() {
            if (structured == null || !structured.ensure) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured ensure capability");
            }
            Object receiver = activation.receiver();
            if (!(receiver instanceof ProtosClosureValue body)
                    || supplied.size() != 1
                    || !(supplied.get(0) instanceof ProtosClosureValue cleanup)) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            return new PreparedEnsureCall(
                    ProtosSendPreparation.prepareDirectClosureCall(body, List.of(), activation),
                    ProtosSendPreparation.prepareDirectClosureCall(cleanup, List.of(), activation));
        }

        @Override
        public PreparedWhileCall prepareStructuredWhile() {
            if (structured == null || structured.whileLoop == null) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured while capability");
            }
            if (!(activation.receiver() instanceof ProtosClosureValue receiver)
                    || supplied.size() != 1
                    || !(supplied.get(0) instanceof ProtosClosureValue argument)) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }
            ProtosStandardObjectProtocol.StructuredLoopKind kind = structured.whileLoop;
            return kind.postTest()
                    ? new PreparedWhileCall(kind, argument, receiver, activation)
                    : new PreparedWhileCall(kind, receiver, argument, activation);
        }

        @Override
        public PreparedErrorHandlerCall prepareStructuredErrorHandler() {
            if (structured == null || !structured.errorHandler) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Error.handle capability");
            }
            Object receiver = activation.receiver();
            if (!(receiver instanceof ProtosObjectValue matchPrototype)
                    || !ProtosCoreErrors.isError(activation, matchPrototype)
                    || supplied.size() != 2
                    || !(supplied.get(0) instanceof ProtosClosureValue body)
                    || !(supplied.get(1) instanceof ProtosClosureValue handler)) {
                throw ProtosCoreErrors.signal(
                        activation,
                        ProtosCoreErrors.newError(activation));
            }

            ProtosDynamicControlState state = activation.dynamicControlState();
            ProtosDynamicControlState.Frame frame =
                    state.enterHandlerFrame(activation, matchPrototype);
            try {
                return new PreparedErrorHandlerCall(
                        state,
                        frame,
                        handler,
                        activation,
                        ProtosSendPreparation.prepareDirectClosureCall(body, List.of(), activation));
            } catch (RuntimeException | Error preparationFailure) {
                state.leaveFrame(frame);
                throw preparationFailure;
            }
        }

        @Override
        public PreparedBooleanCall prepareStructuredBoolean() {
            if (structured == null || structured.booleanKind == null) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Boolean callback capability");
            }
            return new PreparedBooleanCall(
                    structured.booleanKind,
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedArrayEachCall prepareStructuredArrayEach() {
            if (structured == null || !structured.arrayEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Array.each capability");
            }
            return new PreparedArrayEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedArrayMatchCall prepareStructuredArrayMatch() {
            if (!isStructuredArrayMatch()) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Array.match capability");
            }
            return new PreparedArrayMatchCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedBytesEachCall prepareStructuredBytesEach() {
            if (structured == null || !structured.bytesEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Bytes.each capability");
            }
            return new PreparedBytesEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedEnvironmentEachCall prepareStructuredEnvironmentEach() {
            if (structured == null || !structured.environmentEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Environment.each capability");
            }
            return new PreparedEnvironmentEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public ProtosForeignEachCall prepareStructuredForeignEach() {
            if (!isStructuredForeignEach()) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured foreign each capability");
            }
            return new ProtosForeignEachCall(activation.receiver(), supplied, activation);
        }

        @Override
        public PreparedIdentityMapAtIfAbsentCall prepareStructuredIdentityMapAtIfAbsent() {
            if (!isStructuredIdentityMapAtIfAbsent()) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured IdentityMap.atIfAbsent capability");
            }
            return new PreparedIdentityMapAtIfAbsentCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedIdentityMapEachCall prepareStructuredIdentityMapEach() {
            if (structured == null || !structured.identityMapEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured IdentityMap.each capability");
            }
            return new PreparedIdentityMapEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedMapEachCall prepareStructuredMapEach() {
            if (structured == null || !structured.mapEach) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Map.each capability");
            }
            return new PreparedMapEachCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedMapReadLookupCall prepareStructuredMapReadLookup() {
            if (structured == null || structured.mapReadLookup == null) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Map read-lookup capability");
            }
            return new PreparedMapReadLookupCall(
                    structured.mapReadLookup,
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedMapAtPutCall prepareStructuredMapAtPut() {
            if (structured == null || !structured.mapAtPut) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Map.atPut capability");
            }
            return new PreparedMapAtPutCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public PreparedMapRemoveCall prepareStructuredMapRemove() {
            if (structured == null || !structured.mapRemove) {
                throw new IllegalStateException(
                        "prepared Closure call has no structured Map.remove capability");
            }
            return new PreparedMapRemoveCall(
                    activation.receiver(),
                    supplied,
                    activation);
        }

        @Override
        public Object enterNative() {
            if (requiresStructuredDispatch()) {
                throw new IllegalStateException(
                        "structured control native must execute through Bytecode control operations");
            }
            if ((activation.task().isPresent()
                            || activation.deferredCPrimeOperationForRuntime().isPresent()
                            || activation.deferredCPrimeReleaseForRuntime().isPresent()
                            || ProtosHostEntrySuspension.suspendible(activation))
                    && nativeBody
                            instanceof ProtosSuspensionCapableNativeClosureBody
                                    suspensionCapable) {
                return suspensionCapable
                        .executeForBytecodeContinuation(
                                activation,
                                supplied);
            }
            return nativeBody.execute(
                    activation,
                    supplied);
        }
    }

    /**
     * PERF027: a result already produced by an exact guarded standard operation.
     * This shape deliberately owns no semantic method Activation, ReturnHome,
     * native body, supplied List or source target. Argument evaluation and D013
     * selection have already happened before it is created; only the physically
     * specialized implementation of that proven selection has executed.
     */
    static final class ImmediateResultCall implements PreparedClosureCall {
        private final Object result;

        ImmediateResultCall(Object result) {
            this.result = java.util.Objects.requireNonNull(result, "result");
        }

        @Override
        public RootCallTarget bodyTarget() { return null; }

        @Override
        public ProtosActivation activation() {
            throw new IllegalStateException(
                    "immediate guarded operation has no rich Activation");
        }

        @Override
        public Object[] targetArguments() {
            throw new IllegalStateException(
                    "immediate guarded operation has no source target arguments");
        }

        @Override
        public ProtosTask taskForRuntime() { return null; }

        @Override
        public boolean isImmediate() { return true; }

        @Override
        public Object enterImmediate() { return result; }

        @Override
        public Object handleControlTransfer(ControlFlowException transfer) {
            throw transfer;
        }

        @Override
        public void failIfModuleInitialization() {
            // No module lifecycle belongs to this shape.
        }

        @Override
        public RuntimeException mapRuntimeFailure(RuntimeException failure) {
            return failure;
        }

        @Override
        public void complete() {
            // No ReturnHome or lifecycle state exists.
        }

        @Override
        public Object finish(Object result) { return result; }
    }

    /**
     * The special-call representation for a standard-import module
     * initialization. It carries only the {@link
     * ProtosModuleRuntime.PreparedModuleInitialization} lifecycle handle;
     * ordinary source calls never instantiate this class and it has no
     * return-home concept of its own.
     */
    static final class ModuleInitializationCall implements PreparedClosureCall {
        private final ProtosModuleRuntime.PreparedModuleInitialization moduleInitialization;
        private final RootCallTarget bodyTarget;
        private final ProtosActivation activation;
        private final Object[] targetArguments;

        ModuleInitializationCall(
                ProtosModuleRuntime.PreparedModuleInitialization moduleInitialization) {
            this.moduleInitialization =
                    java.util.Objects.requireNonNull(
                            moduleInitialization,
                            "moduleInitialization");
            this.bodyTarget = moduleInitialization.bodyTarget();
            this.activation = moduleInitialization.activation();
            this.targetArguments =
                    bodyTarget == null
                            ? null
                            : new Object[] {this.activation};
        }

        @Override
        public RootCallTarget bodyTarget() { return bodyTarget; }

        @Override
        public ProtosActivation activation() { return activation; }

        @Override
        public Object[] targetArguments() {
            if (targetArguments == null) {
                throw new IllegalStateException(
                        "prepared call has no source target arguments");
            }
            return targetArguments;
        }

        @Override
        public ProtosTask taskForRuntime() { return activation.task().orElse(null); }

        @Override
        public boolean isImmediate() { return moduleInitialization.isImmediate(); }

        @Override
        public Object enterImmediate() {
            if (!isImmediate()) {
                throw new IllegalStateException("prepared call is not an immediate module hit");
            }
            return moduleInitialization.immediateResult();
        }

        @Override
        public Object handleControlTransfer(ControlFlowException transfer) {
            moduleInitialization.fail();
            throw transfer;
        }

        @Override
        public void failIfModuleInitialization() { moduleInitialization.fail(); }

        @Override
        public RuntimeException mapRuntimeFailure(RuntimeException failure) {
            return moduleInitialization.mapUnexpectedHostFailure(failure);
        }

        @Override
        public void complete() {
            // Module-initialization lifecycle is finalized exclusively through finish()/fail().
        }

        @Override
        public Object finish(Object result) { return moduleInitialization.finish(result); }
    }

    static final class PreparedArgumentVector {
        private final ArrayList<Object> values = new ArrayList<>();

        void append(Object value) {
            values.add(
                    java.util.Objects.requireNonNull(
                            value,
                            "supplied argument"));
        }

        void appendSpread(Object value, ProtosActivation caller) {
            if (!appendSpreadArrayOrFalse(value)) {
                throw new ProtosSignalException(ProtosCoreErrors.newError(caller));
            }
        }

        /**
         * Expands a valid Array without requiring caller activation.
         * Invalid spread is reported by the caller with its exact authority.
         */
        boolean appendSpreadArrayOrFalse(Object value) {
            if (!(value instanceof ProtosArrayValue array)) {
                return false;
            }

            // Snapshot at this argument's own evaluation position.
            List<Object> spread = array.indexedSnapshot();
            for (int index = 0; index < spread.size(); index++) {
                values.add(spread.get(index));
            }
            return true;
        }

        Object soleValueOrNull() {
            return values.size() == 1 ? values.get(0) : null;
        }

        List<Object> snapshot() {
            return List.copyOf(values);
        }
    }
}

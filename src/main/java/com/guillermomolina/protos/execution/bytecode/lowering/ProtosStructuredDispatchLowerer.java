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

import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.oracle.truffle.api.bytecode.BytecodeLocal;

/**
 * PLAT042 Candidate B′ structured prepared-invocation lowering.
 *
 * <p>It owns the complete structured dispatcher (while, each, ensure,
 * Error.handle, match/case, import, structured collection callbacks and the
 * other structured prepared calls) used by the Task C-prime entry root, the
 * I/O C-prime roots and nested structured dispatch. I111-B: it emits into
 * auxiliary roots of the single {@link ProtosBytecodeRootNode}
 * interpreter; those roots are never marked as semantic activations and
 * carry no tag. Ordinary canonical source lowering emits genuine semantic
 * roots instead and enters this dispatcher through exactly one helper
 * CallTarget per structured invocation (see {@code
 * ProtosBytecodeRootNode.EnterNestedStructuredDispatch}).
 */
final class ProtosStructuredDispatchLowerer {
    private ProtosStructuredDispatchLowerer() {}

    /**
     * Entry point for infrastructure (C-prime) roots, whose current activation
     * is always frame argument 0.
     */
    static void emitPreparedInvocationForRuntime(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        emitPreparedInvocation(
                builder,
                activationBuilder -> activationBuilder.emitLoadArgument(0),
                result,
                preparedCall,
                childResult,
                resumeValue);
    }

    private static void emitPreparedInvocation(
            ProtosBytecodeRootNodeGen.Builder builder,
            java.util.function.Consumer<ProtosBytecodeRootNodeGen.Builder> emitActivation,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        CanonicalToBytecodeLowerer.requireDefaultScratch(
                result,
                preparedCall,
                childResult,
                resumeValue);

        BytecodeLocal structuredMapMatch =
                builder.createLocal("structuredMapMatchCall", null);
        BytecodeLocal structuredMapMatchChild =
                builder.createLocal("structuredMapMatchChildCall", null);
        BytecodeLocal structuredCaseOf =
                builder.createLocal("structuredCaseOfCall", null);
        BytecodeLocal structuredCaseOfMatcher =
                builder.createLocal("structuredCaseOfMatcherCall", null);
        BytecodeLocal structuredCaseOfAction =
                builder.createLocal("structuredCaseOfActionCall", null);
        BytecodeLocal structuredObjectCall =
                builder.createLocal("structuredObjectCall", null);
        BytecodeLocal structuredObjectCallChild =
                builder.createLocal("structuredObjectCallChild", null);
        BytecodeLocal structuredImportCall =
                builder.createLocal("structuredImportCall", null);
        BytecodeLocal structuredImportCallChild =
                builder.createLocal("structuredImportCallChild", null);
        BytecodeLocal structuredEnsure =
                builder.createLocal("structuredEnsureCall", null);
        BytecodeLocal structuredEnsureCancellationWasUnwinding =
                builder.createLocal("structuredEnsureCancellationWasUnwinding", null);
        BytecodeLocal structuredChild =
                builder.createLocal("structuredEnsureChildCall", null);
        BytecodeLocal structuredHandler =
                builder.createLocal("structuredErrorHandlerCall", null);
        BytecodeLocal structuredHandlerChild =
                builder.createLocal("structuredErrorHandlerChildCall", null);
        BytecodeLocal structuredWhile =
                builder.createLocal("structuredWhileCall", null);
        BytecodeLocal structuredWhileChild =
                builder.createLocal("structuredWhileChildCall", null);
        BytecodeLocal structuredWhileConditionResult =
                builder.createLocal("structuredWhileConditionResult", null);
        BytecodeLocal structuredWhileBodyResult =
                builder.createLocal("structuredWhileBodyResult", null);
        BytecodeLocal structuredBoolean =
                builder.createLocal("structuredBooleanCall", null);
        BytecodeLocal structuredBooleanChild =
                builder.createLocal("structuredBooleanChildCall", null);
        BytecodeLocal structuredLocalEach =
                builder.createLocal("structuredLocalEachCall", null);
        BytecodeLocal structuredLocalEachChild =
                builder.createLocal("structuredLocalEachChildCall", null);
        BytecodeLocal structuredArrayMatch =
                builder.createLocal("structuredArrayMatchCall", null);
        BytecodeLocal structuredArrayMatchChild =
                builder.createLocal("structuredArrayMatchChildCall", null);
        BytecodeLocal structuredForeignEach =
                builder.createLocal("structuredForeignEachCall", null);
        BytecodeLocal structuredForeignEachChild =
                builder.createLocal("structuredForeignEachChildCall", null);
        BytecodeLocal structuredIdentityMapAtIfAbsent =
                builder.createLocal("structuredIdentityMapAtIfAbsentCall", null);
        BytecodeLocal structuredIdentityMapAtIfAbsentChild =
                builder.createLocal("structuredIdentityMapAtIfAbsentChildCall", null);
        BytecodeLocal structuredMapReadLookup =
                builder.createLocal("structuredMapReadLookupCall", null);
        BytecodeLocal structuredMapReadLookupChild =
                builder.createLocal("structuredMapReadLookupChildCall", null);
        BytecodeLocal structuredMapAtPut =
                builder.createLocal("structuredMapAtPutCall", null);
        BytecodeLocal structuredMapAtPutChild =
                builder.createLocal("structuredMapAtPutChildCall", null);
        BytecodeLocal structuredMapRemove =
                builder.createLocal("structuredMapRemoveCall", null);
        BytecodeLocal structuredMapRemoveChild =
                builder.createLocal("structuredMapRemoveChildCall", null);

        builder.beginIfThenElse();

        builder.beginIsStructuredCall(ProtosStructuredCallKind.MAP_MATCH);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredMapMatch);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.MAP_MATCH);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        /*
         * Phase 1: resolve and freeze every required subject association.
         * No nested value matcher is executed before this loop completes.
         */
        builder.beginWhile();
        builder.beginStructuredHasNext(ProtosStructuredPhase.STEP);
        builder.emitLoadLocal(structuredMapMatch);
        builder.endStructuredHasNext();

        builder.beginBlock();

        /* Hash callback under the subject comparison scope. */
        builder.beginStructuredAdvance(ProtosStructuredPhase.ENTER_COMPARISON);
        builder.emitLoadLocal(structuredMapMatch);
        builder.endStructuredAdvance();

        builder.beginTryFinally(
                () -> {
                    builder.beginStructuredAdvance(ProtosStructuredPhase.LEAVE_COMPARISON);
                    builder.emitLoadLocal(structuredMapMatch);
                    builder.endStructuredAdvance();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredMapMatchChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.HASH);
        builder.emitLoadLocal(structuredMapMatch);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();

        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredMapMatchChild,
                childResult,
                resumeValue);

        builder.endBlock();
        builder.endTryFinally();

        builder.beginStructuredAcceptChildOutcome(ProtosStructuredPhase.HASH);
        builder.emitLoadLocal(structuredMapMatch);
        builder.emitLoadLocal(childResult);
        builder.endStructuredAcceptChildOutcome();

        /* Same-hash candidates are tested in subject insertion order. */
        builder.beginWhile();
        builder.beginStructuredHasNext(ProtosStructuredPhase.EQUALITY);
        builder.emitLoadLocal(structuredMapMatch);
        builder.endStructuredHasNext();

        builder.beginBlock();

        builder.beginStructuredAdvance(ProtosStructuredPhase.ENTER_COMPARISON);
        builder.emitLoadLocal(structuredMapMatch);
        builder.endStructuredAdvance();

        builder.beginTryFinally(
                () -> {
                    builder.beginStructuredAdvance(ProtosStructuredPhase.LEAVE_COMPARISON);
                    builder.emitLoadLocal(structuredMapMatch);
                    builder.endStructuredAdvance();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredMapMatchChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.EQUALITY);
        builder.emitLoadLocal(structuredMapMatch);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();

        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredMapMatchChild,
                childResult,
                resumeValue);

        builder.endBlock();
        builder.endTryFinally();

        builder.beginStructuredAcceptChildOutcome(ProtosStructuredPhase.EQUALITY);
        builder.emitLoadLocal(structuredMapMatch);
        builder.emitLoadLocal(childResult);
        builder.endStructuredAcceptChildOutcome();

        builder.endBlock();
        builder.endWhile();

        builder.beginStructuredAdvance(ProtosStructuredPhase.STEP);
        builder.emitLoadLocal(structuredMapMatch);
        builder.endStructuredAdvance();

        builder.endBlock();
        builder.endWhile();

        /*
         * Phase 2: only after all associations are fixed do value matchers run.
         */
        builder.beginWhile();
        builder.beginStructuredHasNext(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredMapMatch);
        builder.endStructuredHasNext();

        builder.beginBlock();

        builder.beginStoreLocal(structuredMapMatchChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredMapMatch);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();

        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredMapMatchChild,
                childResult,
                resumeValue);

        builder.beginStructuredAcceptChildOutcome(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredMapMatch);
        builder.emitLoadLocal(childResult);
        builder.endStructuredAcceptChildOutcome();

        builder.endBlock();
        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginStructuredFinish();
        builder.emitLoadLocal(structuredMapMatch);
        builder.endStructuredFinish();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();

        builder.beginIfThenElse();

        builder.beginIsStructuredCall(ProtosStructuredCallKind.CASE_OF);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredCaseOf);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.CASE_OF);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        builder.beginWhile();
        builder.beginStructuredHasNext(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredCaseOf);
        builder.endStructuredHasNext();

        builder.beginBlock();

        builder.beginStoreLocal(structuredCaseOfMatcher);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredCaseOf);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();

        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredCaseOfMatcher,
                childResult,
                resumeValue);

        builder.beginStructuredAcceptChildOutcome(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredCaseOf);
        builder.emitLoadLocal(childResult);
        builder.endStructuredAcceptChildOutcome();

        builder.endBlock();
        builder.endWhile();

        builder.beginStoreLocal(structuredCaseOfAction);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.SECONDARY_CHILD);
        builder.emitLoadLocal(structuredCaseOf);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();

        emitScopedPreparedInvocation(
                builder,
                result,
                structuredCaseOfAction,
                childResult,
                resumeValue);

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();

        builder.beginIfThenElse();

        builder.beginIsStructuredCall(ProtosStructuredCallKind.ARRAY_MATCH);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredArrayMatch);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.ARRAY_MATCH);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        builder.beginWhile();
        builder.beginStructuredHasNext(ProtosStructuredPhase.STEP);
        builder.emitLoadLocal(structuredArrayMatch);
        builder.endStructuredHasNext();

        builder.beginBlock();

        builder.beginStoreLocal(structuredArrayMatchChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredArrayMatch);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();

        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredArrayMatchChild,
                childResult,
                resumeValue);

        builder.beginStructuredAcceptChildOutcome(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredArrayMatch);
        builder.emitLoadLocal(childResult);
        builder.endStructuredAcceptChildOutcome();

        builder.endBlock();
        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginStructuredFinish();
        builder.emitLoadLocal(structuredArrayMatch);
        builder.endStructuredFinish();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();

        builder.beginIfThenElse();

        builder.beginIsStructuredCall(ProtosStructuredCallKind.OBJECT);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredObjectCall);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.OBJECT);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        builder.beginStoreLocal(structuredObjectCallChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredObjectCall);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();

        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredObjectCallChild,
                childResult,
                resumeValue);

        builder.beginStoreLocal(result);
        builder.beginStructuredFinishWithChildResult();
        builder.emitLoadLocal(structuredObjectCall);
        builder.emitLoadLocal(childResult);
        builder.endStructuredFinishWithChildResult();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredCall(ProtosStructuredCallKind.IMPORT);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredImportCall);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.IMPORT);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        builder.beginStoreLocal(structuredImportCallChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredImportCall);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();

        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredImportCallChild,
                childResult,
                resumeValue);

        builder.beginStoreLocal(result);
        builder.beginStructuredFinishWithChildResult();
        builder.emitLoadLocal(structuredImportCall);
        builder.emitLoadLocal(childResult);
        builder.endStructuredFinishWithChildResult();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredCall(ProtosStructuredCallKind.ENSURE);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        /* Validation precedes the protected semantic extent. */
        builder.beginStoreLocal(structuredEnsure);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.ENSURE);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        builder.beginTryFinally(
                () -> {
                    /*
                     * Snapshot cancellation ownership before cleanup runs.
                     * A cancellation initiated by cleanup is itself the later
                     * transfer and must supersede the body's pending
                     * error/return/normal outcome. Only cancellation that was
                     * already UNWINDING on entry to cleanup may be superseded
                     * by a still-later cleanup transfer.
                     */
                    builder.beginStoreLocal(
                            structuredEnsureCancellationWasUnwinding);
                    builder.beginBeginEnsureCleanup();
                    emitActivation.accept(builder);
                    builder.endBeginEnsureCleanup();
                    builder.endStoreLocal();

                    /*
                     * D121: a cleanup run by that cancellation was published as
                     * its Task's termination-cleanup extent; it is left here,
                     * normally or by a later transfer.
                     */
                    builder.beginTryFinally(
                            () -> {
                                builder.beginExitCancellationCleanupExtent();
                                emitActivation.accept(builder);
                                builder.emitLoadLocal(
                                        structuredEnsureCancellationWasUnwinding);
                                builder.endExitCancellationCleanupExtent();
                            });

                    /*
                     * TryCatch is deliberately inside the generated finally.
                     * It sees only a later transfer escaping cleanup; the
                     * original pending transfer is rethrown by the outer
                     * TryFinally after this generator returns.
                     */
                    builder.beginTryCatch();

                    builder.beginBlock();
                    builder.beginStoreLocal(structuredChild);
                    builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.SECONDARY_CHILD);
                    builder.emitLoadLocal(structuredEnsure);
                    builder.endStructuredPrepareChildCall();
                    builder.endStoreLocal();
                    emitScopedPreparedInvocation(
                            builder,
                            childResult,
                            structuredChild,
                            childResult,
                            resumeValue);
                    builder.endBlock();

                    builder.beginBlock();
                    builder.beginSupersedeCancellationUnwindIfActive();
                    emitActivation.accept(builder);
                    builder.emitLoadLocal(
                            structuredEnsureCancellationWasUnwinding);
                    builder.endSupersedeCancellationUnwindIfActive();
                    builder.beginRethrowTruffleException();
                    builder.emitLoadException();
                    builder.endRethrowTruffleException();
                    builder.endBlock();

                    builder.endTryCatch();
                    builder.endTryFinally();
                });
        builder.beginBlock();
        builder.beginStoreLocal(structuredChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredEnsure);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();
        emitScopedPreparedInvocation(
                builder,
                result,
                structuredChild,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTryFinally();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredCall(ProtosStructuredCallKind.ERROR_HANDLER);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        /*
         * Receiver/arity/body/handler validation happens before frame
         * installation. The returned descriptor owns exactly one Task/direct
         * dynamic handler token for the protected extent.
         */
        builder.beginStoreLocal(structuredHandler);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.ERROR_HANDLER);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        builder.beginTryFinally(
                () -> {
                    builder.beginStructuredAdvance(ProtosStructuredPhase.STEP);
                    builder.emitLoadLocal(structuredHandler);
                    builder.endStructuredAdvance();
                });
        builder.beginBlock();

        /*
         * Bytecode DSL TryCatch is a void operation. Each branch writes the
         * semantic Error.handle result directly into the shared result local;
         * the TryCatch itself must not be used as a value-producing child.
         */
        builder.beginTryCatch();

        builder.beginBlock();
        builder.beginStoreLocal(structuredHandlerChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredHandler);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();
        emitScopedPreparedInvocation(
                builder,
                result,
                structuredHandlerChild,
                childResult,
                resumeValue);
        builder.endBlock();

        builder.beginBlock();
        builder.beginStoreLocal(structuredHandlerChild);
        builder.beginPrepareSelectedStructuredErrorHandlerCall();
        builder.emitLoadLocal(structuredHandler);
        builder.emitLoadException();
        builder.endPrepareSelectedStructuredErrorHandlerCall();
        builder.endStoreLocal();
        emitScopedPreparedInvocation(
                builder,
                result,
                structuredHandlerChild,
                childResult,
                resumeValue);
        builder.endBlock();

        builder.endTryCatch();

        builder.endBlock();
        builder.endTryFinally();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredCall(ProtosStructuredCallKind.WHILE);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        /*
         * Validate the standard loop receiver/argument before the first
         * condition or body activation.
         */
        builder.beginStoreLocal(structuredWhile);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.WHILE);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        /*
         * D210/D212: the last normal body result of the standard loop. It
         * starts as canonical null (zero pre-test iterations) and is
         * overwritten only by a body invocation that completes normally; an
         * abnormal condition/body exit propagates without reaching the
         * result store below.
         */
        builder.beginStoreLocal(structuredWhileBodyResult);
        builder.emitLoadConstant(ProtosNullValue.INSTANCE);
        builder.endStoreLocal();

        /*
         * PLAT021: loop phase lives in Bytecode control state. Every logical
         * condition/body activation is prepared fresh so each invocation owns
         * its own activation/ReturnHome; suspension resumes at the exact loop
         * PC without a replay checkpoint or callback compaction cursor.
         *
         * D212: the prepared loop's immutable kind selects one of two shapes.
         * A post-test loop runs body-then-condition inside the While
         * condition with an empty While body, so each iteration activates
         * the body before its condition and a body that exits abnormally
         * never reaches the condition. A pre-test loop keeps the D210
         * condition-then-body shape.
         */
        builder.beginIfThenElse();

        builder.beginStructuredWhilePostTest();
        builder.emitLoadLocal(structuredWhile);
        builder.endStructuredWhilePostTest();

        builder.beginWhile();
        builder.beginBlock();
        emitStructuredLoopBody(
                builder,
                structuredWhile,
                structuredWhileChild,
                structuredWhileBodyResult,
                childResult,
                resumeValue);
        emitStructuredLoopCondition(
                builder,
                structuredWhile,
                structuredWhileChild,
                structuredWhileConditionResult,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.beginBlock();
        builder.endBlock();
        builder.endWhile();

        builder.beginWhile();
        builder.beginBlock();
        emitStructuredLoopCondition(
                builder,
                structuredWhile,
                structuredWhileChild,
                structuredWhileConditionResult,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.beginBlock();
        emitStructuredLoopBody(
                builder,
                structuredWhile,
                structuredWhileChild,
                structuredWhileBodyResult,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endWhile();

        builder.endIfThenElse();

        builder.beginStoreLocal(result);
        builder.beginStructuredFinishWithChildResult();
        builder.emitLoadLocal(structuredWhile);
        builder.emitLoadLocal(structuredWhileBodyResult);
        builder.endStructuredFinishWithChildResult();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredCall(ProtosStructuredCallKind.BOOLEAN);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredBoolean);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.BOOLEAN);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        builder.beginIfThenElse();
        builder.beginStructuredHasNext(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredBoolean);
        builder.endStructuredHasNext();

        builder.beginBlock();
        builder.beginStoreLocal(structuredBooleanChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredBoolean);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();
        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredBooleanChild,
                childResult,
                resumeValue);
        builder.beginStoreLocal(result);
        builder.beginStructuredFinishWithChildResult();
        builder.emitLoadLocal(structuredBoolean);
        builder.emitLoadLocal(childResult);
        builder.endStructuredFinishWithChildResult();
        builder.endStoreLocal();
        builder.endBlock();

        builder.beginBlock();
        builder.beginStoreLocal(result);
        builder.beginStructuredFinish();
        builder.emitLoadLocal(structuredBoolean);
        builder.endStructuredFinish();
        builder.endStoreLocal();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredCall(ProtosStructuredCallKind.LOCAL_EACH);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredLocalEach);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.LOCAL_EACH);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        builder.beginWhile();
        builder.beginStructuredHasNext(ProtosStructuredPhase.STEP);
        builder.emitLoadLocal(structuredLocalEach);
        builder.endStructuredHasNext();

        builder.beginBlock();
        builder.beginStoreLocal(structuredLocalEachChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredLocalEach);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();
        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredLocalEachChild,
                childResult,
                resumeValue);
        builder.beginStructuredAdvance(ProtosStructuredPhase.STEP);
        builder.emitLoadLocal(structuredLocalEach);
        builder.endStructuredAdvance();
        builder.endBlock();

        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginStructuredFinish();
        builder.emitLoadLocal(structuredLocalEach);
        builder.endStructuredFinish();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();




        builder.beginIfThenElse();

        /*
         * D188 projected foreign each: a Protos-side pull loop. Prevalidation happens in the
         * prepare step; each iteration asks the provider for has-next, pulls and admits one
         * element, and invokes the block as an ordinary scoped child, so suspension resumes the
         * same visit and an Error or non-local exit leaves no later pull.
         */
        builder.beginIsStructuredCall(ProtosStructuredCallKind.FOREIGN_EACH);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredForeignEach);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.FOREIGN_EACH);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        builder.beginWhile();
        builder.beginStructuredHasNext(ProtosStructuredPhase.STEP);
        builder.emitLoadLocal(structuredForeignEach);
        builder.endStructuredHasNext();

        builder.beginBlock();
        builder.beginStoreLocal(structuredForeignEachChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredForeignEach);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();
        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredForeignEachChild,
                childResult,
                resumeValue);
        builder.beginStructuredAdvance(ProtosStructuredPhase.STEP);
        builder.emitLoadLocal(structuredForeignEach);
        builder.endStructuredAdvance();
        builder.endBlock();

        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginStructuredFinish();
        builder.emitLoadLocal(structuredForeignEach);
        builder.endStructuredFinish();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredCall(ProtosStructuredCallKind.IDENTITY_MAP_AT_IF_ABSENT);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredIdentityMapAtIfAbsent);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.IDENTITY_MAP_AT_IF_ABSENT);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        builder.beginIfThenElse();

        builder.beginStructuredHasNext(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredIdentityMapAtIfAbsent);
        builder.endStructuredHasNext();

        builder.beginBlock();

        builder.beginStoreLocal(structuredIdentityMapAtIfAbsentChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredIdentityMapAtIfAbsent);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();

        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredIdentityMapAtIfAbsentChild,
                childResult,
                resumeValue);

        builder.beginStoreLocal(result);
        builder.beginStructuredFinishWithChildResult();
        builder.emitLoadLocal(structuredIdentityMapAtIfAbsent);
        builder.emitLoadLocal(childResult);
        builder.endStructuredFinishWithChildResult();
        builder.endStoreLocal();

        builder.endBlock();

        builder.beginBlock();

        builder.beginStoreLocal(result);
        builder.beginStructuredFinish();
        builder.emitLoadLocal(structuredIdentityMapAtIfAbsent);
        builder.endStructuredFinish();
        builder.endStoreLocal();

        builder.endBlock();

        builder.endIfThenElse();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();




        builder.beginIfThenElse();

        builder.beginIsStructuredCall(ProtosStructuredCallKind.MAP_READ_LOOKUP);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredMapReadLookup);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.MAP_READ_LOOKUP);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        /* Hash callback: comparison scope spans suspension but not result validation. */
        builder.beginStructuredAdvance(ProtosStructuredPhase.ENTER_COMPARISON);
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.endStructuredAdvance();
        builder.beginTryFinally(
                () -> {
                    builder.beginStructuredAdvance(ProtosStructuredPhase.LEAVE_COMPARISON);
                    builder.emitLoadLocal(structuredMapReadLookup);
                    builder.endStructuredAdvance();
                });
        builder.beginBlock();
        builder.beginStoreLocal(structuredMapReadLookupChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.HASH);
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();
        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredMapReadLookupChild,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTryFinally();

        builder.beginStructuredAcceptChildOutcome(ProtosStructuredPhase.HASH);
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.emitLoadLocal(childResult);
        builder.endStructuredAcceptChildOutcome();

        /* Candidate equality callbacks repeat in insertion order over the hash-filtered snapshot. */
        builder.beginWhile();
        builder.beginStructuredHasNext(ProtosStructuredPhase.EQUALITY);
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.endStructuredHasNext();

        builder.beginBlock();
        builder.beginStructuredAdvance(ProtosStructuredPhase.ENTER_COMPARISON);
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.endStructuredAdvance();
        builder.beginTryFinally(
                () -> {
                    builder.beginStructuredAdvance(ProtosStructuredPhase.LEAVE_COMPARISON);
                    builder.emitLoadLocal(structuredMapReadLookup);
                    builder.endStructuredAdvance();
                });
        builder.beginBlock();
        builder.beginStoreLocal(structuredMapReadLookupChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.EQUALITY);
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();
        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredMapReadLookupChild,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTryFinally();

        builder.beginStructuredAcceptChildOutcome(ProtosStructuredPhase.EQUALITY);
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.emitLoadLocal(childResult);
        builder.endStructuredAcceptChildOutcome();
        builder.endBlock();

        builder.endWhile();

        builder.beginIfThenElse();

        builder.beginStructuredHasNext(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.endStructuredHasNext();

        builder.beginBlock();

        builder.beginStoreLocal(structuredMapReadLookupChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();

        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredMapReadLookupChild,
                childResult,
                resumeValue);

        builder.beginStoreLocal(result);
        builder.beginStructuredFinishWithChildResult();
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.emitLoadLocal(childResult);
        builder.endStructuredFinishWithChildResult();
        builder.endStoreLocal();

        builder.endBlock();

        builder.beginBlock();

        builder.beginStoreLocal(result);
        builder.beginStructuredFinish();
        builder.emitLoadLocal(structuredMapReadLookup);
        builder.endStructuredFinish();
        builder.endStoreLocal();

        builder.endBlock();

        builder.endIfThenElse();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredCall(ProtosStructuredCallKind.MAP_AT_PUT);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredMapAtPut);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.MAP_AT_PUT);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        builder.beginStructuredAdvance(ProtosStructuredPhase.ENTER_COMPARISON);
        builder.emitLoadLocal(structuredMapAtPut);
        builder.endStructuredAdvance();
        builder.beginTryFinally(
                () -> {
                    builder.beginStructuredAdvance(ProtosStructuredPhase.LEAVE_COMPARISON);
                    builder.emitLoadLocal(structuredMapAtPut);
                    builder.endStructuredAdvance();
                });
        builder.beginBlock();
        builder.beginStoreLocal(structuredMapAtPutChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.HASH);
        builder.emitLoadLocal(structuredMapAtPut);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();
        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredMapAtPutChild,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTryFinally();

        builder.beginStructuredAcceptChildOutcome(ProtosStructuredPhase.HASH);
        builder.emitLoadLocal(structuredMapAtPut);
        builder.emitLoadLocal(childResult);
        builder.endStructuredAcceptChildOutcome();

        builder.beginWhile();
        builder.beginStructuredHasNext(ProtosStructuredPhase.EQUALITY);
        builder.emitLoadLocal(structuredMapAtPut);
        builder.endStructuredHasNext();

        builder.beginBlock();
        builder.beginStructuredAdvance(ProtosStructuredPhase.ENTER_COMPARISON);
        builder.emitLoadLocal(structuredMapAtPut);
        builder.endStructuredAdvance();
        builder.beginTryFinally(
                () -> {
                    builder.beginStructuredAdvance(ProtosStructuredPhase.LEAVE_COMPARISON);
                    builder.emitLoadLocal(structuredMapAtPut);
                    builder.endStructuredAdvance();
                });
        builder.beginBlock();
        builder.beginStoreLocal(structuredMapAtPutChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.EQUALITY);
        builder.emitLoadLocal(structuredMapAtPut);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();
        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredMapAtPutChild,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTryFinally();

        builder.beginStructuredAcceptChildOutcome(ProtosStructuredPhase.EQUALITY);
        builder.emitLoadLocal(structuredMapAtPut);
        builder.emitLoadLocal(childResult);
        builder.endStructuredAcceptChildOutcome();
        builder.endBlock();

        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginStructuredFinish();
        builder.emitLoadLocal(structuredMapAtPut);
        builder.endStructuredFinish();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        builder.beginIfThenElse();

        builder.beginIsStructuredCall(ProtosStructuredCallKind.MAP_REMOVE);
        builder.emitLoadLocal(preparedCall);
        builder.endIsStructuredCall();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();

        builder.beginStoreLocal(structuredMapRemove);
        builder.beginPrepareStructuredCall(ProtosStructuredCallKind.MAP_REMOVE);
        builder.emitLoadLocal(preparedCall);
        builder.endPrepareStructuredCall();
        builder.endStoreLocal();

        builder.beginStructuredAdvance(ProtosStructuredPhase.ENTER_COMPARISON);
        builder.emitLoadLocal(structuredMapRemove);
        builder.endStructuredAdvance();
        builder.beginTryFinally(
                () -> {
                    builder.beginStructuredAdvance(ProtosStructuredPhase.LEAVE_COMPARISON);
                    builder.emitLoadLocal(structuredMapRemove);
                    builder.endStructuredAdvance();
                });
        builder.beginBlock();
        builder.beginStoreLocal(structuredMapRemoveChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.HASH);
        builder.emitLoadLocal(structuredMapRemove);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();
        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredMapRemoveChild,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTryFinally();

        builder.beginStructuredAcceptChildOutcome(ProtosStructuredPhase.HASH);
        builder.emitLoadLocal(structuredMapRemove);
        builder.emitLoadLocal(childResult);
        builder.endStructuredAcceptChildOutcome();

        builder.beginWhile();
        builder.beginStructuredHasNext(ProtosStructuredPhase.EQUALITY);
        builder.emitLoadLocal(structuredMapRemove);
        builder.endStructuredHasNext();

        builder.beginBlock();
        builder.beginStructuredAdvance(ProtosStructuredPhase.ENTER_COMPARISON);
        builder.emitLoadLocal(structuredMapRemove);
        builder.endStructuredAdvance();
        builder.beginTryFinally(
                () -> {
                    builder.beginStructuredAdvance(ProtosStructuredPhase.LEAVE_COMPARISON);
                    builder.emitLoadLocal(structuredMapRemove);
                    builder.endStructuredAdvance();
                });
        builder.beginBlock();
        builder.beginStoreLocal(structuredMapRemoveChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.EQUALITY);
        builder.emitLoadLocal(structuredMapRemove);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();
        emitScopedPreparedInvocation(
                builder,
                childResult,
                structuredMapRemoveChild,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTryFinally();

        builder.beginStructuredAcceptChildOutcome(ProtosStructuredPhase.EQUALITY);
        builder.emitLoadLocal(structuredMapRemove);
        builder.emitLoadLocal(childResult);
        builder.endStructuredAcceptChildOutcome();
        builder.endBlock();

        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginStructuredFinish();
        builder.emitLoadLocal(structuredMapRemove);
        builder.endStructuredFinish();
        builder.endStoreLocal();

        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.beginBlock();
        emitOrdinaryPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();


        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();


        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
        builder.endBlock();

        builder.endIfThenElse();
    }

    /**
     * Emits one fresh standard-loop condition activation followed by its
     * strict, polarity-aware continuation test; the emitted value is the
     * boolean "continue looping".
     */
    private static void emitStructuredLoopCondition(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal structuredWhile,
            BytecodeLocal structuredWhileChild,
            BytecodeLocal structuredWhileConditionResult,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginStoreLocal(structuredWhileChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.CHILD);
        builder.emitLoadLocal(structuredWhile);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();
        emitScopedPreparedInvocation(
                builder,
                structuredWhileConditionResult,
                structuredWhileChild,
                childResult,
                resumeValue);
        builder.beginStructuredWhileCondition();
        builder.emitLoadLocal(structuredWhile);
        builder.emitLoadLocal(structuredWhileConditionResult);
        builder.endStructuredWhileCondition();
    }

    /**
     * Emits one fresh standard-loop body activation and retains its exact
     * normal result in {@code structuredWhileBodyResult}.
     */
    private static void emitStructuredLoopBody(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal structuredWhile,
            BytecodeLocal structuredWhileChild,
            BytecodeLocal structuredWhileBodyResult,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginStoreLocal(structuredWhileChild);
        builder.beginStructuredPrepareChildCall(ProtosStructuredPhase.SECONDARY_CHILD);
        builder.emitLoadLocal(structuredWhile);
        builder.endStructuredPrepareChildCall();
        builder.endStoreLocal();
        emitScopedPreparedInvocation(
                builder,
                structuredWhileBodyResult,
                structuredWhileChild,
                childResult,
                resumeValue);
        builder.beginStoreLocal(structuredWhileBodyResult);
        builder.beginStructuredWhileRetainBodyResult();
        builder.emitLoadLocal(structuredWhile);
        builder.emitLoadLocal(structuredWhileBodyResult);
        builder.endStructuredWhileRetainBodyResult();
        builder.endStoreLocal();
    }

    private static void emitScopedPreparedInvocation(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginIfThenElse();

        builder.beginRequiresStructuredDispatch();
        builder.emitLoadLocal(preparedCall);
        builder.endRequiresStructuredDispatch();

        builder.beginBlock();
        builder.beginStoreLocal(childResult);
        builder.beginEnterNestedStructuredDispatch();
        builder.emitLoadLocal(preparedCall);
        builder.endEnterNestedStructuredDispatch();
        builder.endStoreLocal();

        builder.beginWhile();
        builder.beginIsResultKind(ProtosResultKind.CONTINUATION);
        builder.emitLoadLocal(childResult);
        builder.endIsResultKind();

        builder.beginBlock();
        builder.beginStoreLocal(resumeValue);
        builder.beginYield();
        builder.emitLoadLocal(childResult);
        builder.endYield();
        builder.endStoreLocal();

        builder.beginStoreLocal(childResult);
        builder.beginResumeContinuation();
        builder.emitLoadLocal(preparedCall);
        builder.emitLoadLocal(childResult);
        builder.emitLoadLocal(resumeValue);
        builder.endResumeContinuation();
        builder.endStoreLocal();
        builder.endBlock();

        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.emitLoadLocal(childResult);
        builder.endStoreLocal();
        builder.endBlock();

        builder.beginBlock();
        builder.beginTryFinally(
                () -> {
                    builder.beginCompleteClosureCall();
                    builder.emitLoadLocal(preparedCall);
                    builder.endCompleteClosureCall();
                });
        builder.beginBlock();
        emitOrdinaryPreparedInvocation(
                builder,
                result,
                preparedCall,
                childResult,
                resumeValue);
        builder.endBlock();
        builder.endTryFinally();
        builder.endBlock();

        builder.endIfThenElse();
    }

    private static void emitOrdinaryPreparedInvocation(
            ProtosBytecodeRootNodeGen.Builder builder,
            BytecodeLocal result,
            BytecodeLocal preparedCall,
            BytecodeLocal childResult,
            BytecodeLocal resumeValue) {
        builder.beginStoreLocal(childResult);
        builder.beginEnterClosureCall();
        builder.emitLoadLocal(preparedCall);
        builder.endEnterClosureCall();
        builder.endStoreLocal();

        builder.beginWhile();
        builder.beginIsResultKind(ProtosResultKind.CONTINUATION);
        builder.emitLoadLocal(childResult);
        builder.endIsResultKind();
        builder.beginBlock();
        builder.beginStoreLocal(resumeValue);
        builder.beginYield();
        builder.emitLoadLocal(childResult);
        builder.endYield();
        builder.endStoreLocal();
        builder.beginStoreLocal(childResult);
        builder.beginResumeContinuation();
        builder.emitLoadLocal(preparedCall);
        builder.emitLoadLocal(childResult);
        builder.emitLoadLocal(resumeValue);
        builder.endResumeContinuation();
        builder.endStoreLocal();
        builder.endBlock();
        builder.endWhile();

        builder.beginStoreLocal(result);
        builder.beginFinishClosureCall(true);
        builder.emitLoadLocal(preparedCall);
        builder.emitLoadLocal(childResult);
        builder.endFinishClosureCall();
        builder.endStoreLocal();
    }
}

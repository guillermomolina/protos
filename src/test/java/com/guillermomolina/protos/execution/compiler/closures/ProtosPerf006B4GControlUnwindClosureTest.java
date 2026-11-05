/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS.
 * "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY OF THE
 * LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING THE
 * CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS FILE,
 * A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the LICENSE.
 */

package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Retained PERF006-B4 cross-product closure evidence. */
final class ProtosPerf006B4GControlUnwindClosureTest {

    @Test
    void retainedCrossProductEvidencePassesTogether() throws Exception {
        new ProtosPerf006B4BNonLocalReturnTest()
                .canonicalReturnAfterFutureSuspensionResumesToOwningPreparedCallWithoutReplay();

        ProtosPerf006B4CStructuredEnsureTest ensure =
                new ProtosPerf006B4CStructuredEnsureTest();
        ensure.nonLocalReturnRunsCleanupThenPropagatesExactOriginalTransfer();
        ensure.cleanupNonLocalReturnSupersedesPendingBodyNonLocalReturn();
        ensure.directUnwindEnsureCrossProductKeepsTheSurvivingTransfer();

        ProtosPerf006B4DErrorHandlersTest handlers =
                new ProtosPerf006B4DErrorHandlersTest();
        handlers.selectedInnerHandlerIsInactiveBeforeCrossedEnsureCleanup();
        handlers.selectedHandlerRemainsRemovedWhileItsHandlerClosureSuspends();
        handlers.directUnwindNonLocalReturnNeverEntersErrorHandle();
        handlers.directUnwindCancellationNeverEntersErrorHandle();

        ProtosPerf006B4ECancellationUnwindTest cancellation =
                new ProtosPerf006B4ECancellationUnwindTest();
        cancellation.cancellationCleanupMaySuspendWithoutRedeliveryOrReplay();
        cancellation.cleanupErrorSupersedesCancellationBeforeOuterHandlerRuns();
        cancellation.cleanupNonLocalReturnEscapingSupersedesCancellation();
        cancellation.cleanupNonLocalReturnHandledInsideCleanupKeepsCancellation();

        ProtosPerf006B4FStructuredWhileTest loop =
                new ProtosPerf006B4FStructuredWhileTest();
        loop.conditionAndBodySuspensionResumeWithoutReplay();
        loop.cancellationThroughSuspendedConditionCrossesOuterEnsureExactlyOnce();
        loop.bodyErrorStopsLoopBeforeAnotherConditionActivation();
        loop.directUnwindReturnLeavesStructuredWhileThroughEnsureExactlyOnce();

        System.out.println("PERF006_B4G_NLR_ENSURE_CPRIME_CROSS_PRODUCT=PASS");
        System.out.println("PERF006_B4G_ERROR_HANDLER_ENSURE_CROSS_PRODUCT=PASS");
        System.out.println("PERF006_B4G_CANCELLATION_ENSURE_HANDLER_CROSS_PRODUCT=PASS");
        System.out.println("PERF006_B4G_WHILE_SUSPENSION_CANCELLATION_ERROR_CROSS_PRODUCT=PASS");
    }

    @Test
    void bytecodeControlPathAndSynchronousOracleContainNoReplayBackend()
            throws Exception {
        String lowerer = read(
                "src/main/java/com/guillermomolina/protos/execution/bytecode/lowering/CanonicalToBytecodeLowerer.java");
        String bytecodeRoot = read(
                "src/main/java/com/guillermomolina/protos/execution/bytecode/activation/ProtosBytecodeRootNode.java")
                + read(
                        "src/main/java/com/guillermomolina/protos/execution/bytecode/activation/ProtosPreparedCalls.java")
                + read(
                        "src/main/java/com/guillermomolina/protos/execution/bytecode/activation/structured/ProtosStructuredControlCalls.java");
        String standardObject = read(
                "src/main/java/com/guillermomolina/protos/execution/protocols/values/ProtosStandardObjectProtocol.java");
        String task = read(
                "src/main/java/com/guillermomolina/protos/runtime/ProtosTask.java");

        assertFalse(lowerer.contains("bindWhileCallbackCheckpoint("));
        assertFalse(lowerer.contains("skipInvocationReplayTo("));
        assertFalse(bytecodeRoot.contains("bindWhileCallbackCheckpoint("));
        assertFalse(bytecodeRoot.contains("skipInvocationReplayTo("));
        assertFalse(standardObject.contains("bindWhileCallbackCheckpoint("));
        assertFalse(standardObject.contains("skipInvocationReplayTo("));
        assertFalse(standardObject.contains("resumeEnsureCleanupReplay("));
        assertFalse(task.contains("executeProtos("));
        assertFalse(task.contains("evaluatorContinuation("));

        System.out.println("PERF006_B4G_BYTECODE_REPLAY_CURSOR=NO");
        System.out.println("PERF006_B4G_LEGACY_REPLAY_BACKEND_RETIRED=YES");
    }

    @Test
    void controlTransfersUseDirectUnwindWithoutABridgeOrGuestCatchSupersession()
            throws Exception {
        String production =
                read("src/main/java/com/guillermomolina/protos/execution/bytecode/activation/ProtosBytecodeRootNode.java")
                        + read("src/main/java/com/guillermomolina/protos/execution/bytecode/activation/ProtosTailCallContinuation.java")
                        + read("src/main/java/com/guillermomolina/protos/execution/bytecode/activation/ProtosBytecodeTaskExecution.java")
                        + read("src/main/java/com/guillermomolina/protos/execution/host/embedding/ProtosHostEntrySuspension.java")
                        + read("src/main/java/com/guillermomolina/protos/execution/host/tooling/ProtosDiagnosticTraceCapture.java");
        String structured = read(
                "src/main/java/com/guillermomolina/protos/execution/bytecode/lowering/ProtosStructuredDispatchLowerer.java");

        assertFalse(production.contains("ProtosBytecodeControlTransferException"));
        assertFalse(production.contains("interceptControlFlowException"));
        assertTrue(production.contains("unwindExceptions"));
        assertTrue(structured.contains("structuredEnsureCleanupCompletedNormally"));
        assertFalse(
                structured.contains("RethrowTruffleException"),
                "ensure supersession must not depend on a guest catch of the escaping transfer");

        System.out.println("PLAT070_CONTROL_TRANSFER_BRIDGE=REMOVED");
        System.out.println("PLAT070_CANCELLATION_SUPERSESSION=TRY_FINALLY_BASED");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

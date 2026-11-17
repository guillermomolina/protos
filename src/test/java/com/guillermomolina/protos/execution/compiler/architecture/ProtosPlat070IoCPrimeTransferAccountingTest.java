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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.ProtosIoLifecycle;
import com.guillermomolina.protos.runtime.ProtosIoReleaseExecution;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.source.Source;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PLAT070: internal control transfers unwind directly through I/O C-prime roots and never enter
 * their guest {@code TryCatch}. Every root accounts such a transfer through a Bytecode {@code
 * TryFinally} with its protocol's failed-invocation bookkeeping, while the transfer itself keeps
 * its identity and continues propagating. These cases drive the lifecycle-release C-prime through
 * the real lifecycle release path.
 */
final class ProtosPlat070IoCPrimeTransferAccountingTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    /*
     * IO_CORE: even when the wrapper's own finalization fails, the owned target's close() is
     * invoked. A transfer escaping write/flush therefore keeps pending mandatory finalizers, which
     * run through the release C-prime while the transfer stays pending.
     */
    @Test
    void nonLocalReturnDuringFlushStillClosesTheTargetExactlyOnce() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            ProtosReturnHome foreignHome = new ProtosReturnHome();
            ProtosNonLocalReturnException original =
                    new ProtosNonLocalReturnException(foreignHome, ProtosNullValue.INSTANCE);
            StepProbe flush = harness.probeThrowing(original);
            StepProbe close = harness.probeAnswering(harness.resolvedFuture());
            ProtosObjectValue target = harness.target("flush", flush, "close", close);

            harness.close(
                    List.of(
                            harness.ordinary(target, "flush", flush),
                            harness.mandatory(target, "close", close)));

            assertSame(original, harness.drain(), "the original transfer survives by identity");
            assertTrue(foreignHome.isActive());
            flush.assertFailedOnce();
            close.assertInvokedAndResolvedOnce();
            harness.assertReleaseFailed();
        }

        System.out.println("PLAT070_IO_RELEASE_NLR_FLUSH_CLOSE_ONCE=PASS");
    }

    @Test
    void nonLocalReturnDuringWriteClosesTheTargetOnceAcrossCloseSuspension() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            ProtosReturnHome foreignHome = new ProtosReturnHome();
            ProtosFutureValue closing = harness.pendingFuture();
            StepProbe write = harness.probeReturningTo(foreignHome);
            StepProbe flush = harness.probe();
            StepProbe close = harness.probeAnswering(closing);
            ProtosObjectValue target =
                    harness.target("write", write, "flush", flush, "close", close);

            harness.close(
                    List.of(
                            harness.ordinary(target, "write", write),
                            harness.ordinary(target, "flush", flush),
                            harness.mandatory(target, "close", close)));

            assertNull(harness.drain(), "close suspends while the transfer stays pending");
            assertEquals(1, close.invocations());
            assertEquals(0, flush.invocations(), "a later ordinary step is suppressed");
            assertFalse(harness.release().terminal(), "no release completion before close commits");

            assertTrue(closing.resolve(ProtosNullValue.INSTANCE, harness.module()));
            ControlFlowException escaped = harness.drain();
            assertNotNull(escaped, "the pending transfer resumes unwinding after close");
            assertSame(foreignHome, ((ProtosNonLocalReturnException) escaped).target());

            write.assertFailedOnce();
            assertEquals(0, flush.failedCount() + flush.resolvedCount());
            close.assertInvokedAndResolvedOnce();
            harness.assertReleaseFailed();
        }

        System.out.println("PLAT070_IO_RELEASE_NLR_WRITE_CLOSE_SUSPENDS_ONCE=PASS");
    }

    @Test
    void nonLocalReturnAfterAReleaseSuspensionAccountsWithoutReplay() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            ProtosReturnHome foreignHome = new ProtosReturnHome();
            ProtosFutureValue lower = harness.pendingFuture();
            StepProbe write = harness.probeAnswering(lower);
            StepProbe flush = harness.probeReturningTo(foreignHome);
            StepProbe close = harness.probeAnswering(harness.resolvedFuture());
            ProtosObjectValue target =
                    harness.target("write", write, "flush", flush, "close", close);

            harness.close(
                    List.of(
                            harness.ordinary(target, "write", write),
                            harness.ordinary(target, "flush", flush),
                            harness.mandatory(target, "close", close)));

            assertNull(harness.drain(), "the release suspends on its lower Future");
            assertEquals(1, write.invocations());
            assertEquals(0, write.resolvedCount());

            assertTrue(lower.resolve(ProtosNullValue.INSTANCE, harness.module()));
            ControlFlowException escaped = harness.drain();
            assertNotNull(escaped, "transfer must propagate");
            assertSame(foreignHome, ((ProtosNonLocalReturnException) escaped).target());

            assertEquals(1, write.invocations(), "the completed step is never replayed");
            assertEquals(1, write.resolvedCount());
            flush.assertFailedOnce();
            close.assertInvokedAndResolvedOnce();
            harness.assertReleaseFailed();
        }

        System.out.println("PLAT070_IO_RELEASE_NLR_AFTER_SUSPENSION=PASS");
    }

    @Test
    void cancellationDuringFlushKeepsIdentityAndStillClosesTheTarget() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            ProtosTaskCancellationException cancellation = new ProtosTaskCancellationException();
            StepProbe flush = harness.probeThrowing(cancellation);
            StepProbe close = harness.probeAnswering(harness.resolvedFuture());
            ProtosObjectValue target = harness.target("flush", flush, "close", close);

            harness.close(
                    List.of(
                            harness.ordinary(target, "flush", flush),
                            harness.mandatory(target, "close", close)));

            assertSame(cancellation, harness.drain(), "the original cancellation propagates");
            flush.assertFailedOnce();
            close.assertInvokedAndResolvedOnce();
            harness.assertReleaseFailed();
        }

        System.out.println("PLAT070_IO_RELEASE_CANCELLATION_CLOSE_ONCE=PASS");
    }

    @Test
    void laterTransferEscapingCloseSupersedesThePendingTransfer() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            ProtosNonLocalReturnException original =
                    new ProtosNonLocalReturnException(
                            new ProtosReturnHome(), ProtosNullValue.INSTANCE);
            ProtosTaskCancellationException later = new ProtosTaskCancellationException();
            StepProbe flush = harness.probeThrowing(original);
            StepProbe close = harness.probeThrowing(later);
            ProtosObjectValue target = harness.target("flush", flush, "close", close);

            harness.close(
                    List.of(
                            harness.ordinary(target, "flush", flush),
                            harness.mandatory(target, "close", close)));

            assertSame(later, harness.drain(), "a later escaping transfer supersedes, as in ensure");
            flush.assertFailedOnce();
            assertEquals(1, close.invocations());
            close.assertFailedOnce();
            harness.assertReleaseFailed();
        }

        System.out.println("PLAT070_IO_RELEASE_LATER_TRANSFER_SUPERSEDES=PASS");
    }

    /*
     * Two owned targets: a transfer escaping the first mandatory finalizer must not prevent the
     * second one from being invoked. The latest escaping transfer survives by identity.
     */
    @Test
    void laterTransferEscapingTheFirstFinalizerStillRunsTheSecond() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            ProtosNonLocalReturnException original =
                    new ProtosNonLocalReturnException(
                            new ProtosReturnHome(), ProtosNullValue.INSTANCE);
            ProtosTaskCancellationException later = new ProtosTaskCancellationException();
            StepProbe flush = harness.probeThrowing(original);
            StepProbe closeFirst = harness.probeThrowing(later);
            StepProbe closeSecond = harness.probeAnswering(harness.resolvedFuture());
            ProtosObjectValue wrapper = harness.target("flush", flush, "close", closeFirst);
            ProtosObjectValue owned = harness.target("close", closeSecond);

            harness.close(
                    List.of(
                            harness.ordinary(wrapper, "flush", flush),
                            harness.mandatory(wrapper, "close", closeFirst),
                            harness.mandatory(owned, "close", closeSecond)));

            assertSame(later, harness.drain(), "the later escaping transfer survives by identity");
            flush.assertFailedOnce();
            assertEquals(1, closeFirst.invocations());
            closeFirst.assertFailedOnce();
            closeSecond.assertInvokedAndResolvedOnce();
            harness.assertReleaseFailed();
        }

        System.out.println("PLAT070_IO_RELEASE_SECOND_FINALIZER_AFTER_TRANSFER=RUN");
    }

    @Test
    void secondFinalizerSuspendsAndResumesAfterTheFirstEscapes() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            ProtosReturnHome firstHome = new ProtosReturnHome();
            ProtosReturnHome laterHome = new ProtosReturnHome();
            ProtosFutureValue closing = harness.pendingFuture();
            StepProbe write = harness.probeReturningTo(firstHome);
            StepProbe closeFirst = harness.probeReturningTo(laterHome);
            StepProbe closeSecond = harness.probeAnswering(closing);
            ProtosObjectValue wrapper = harness.target("write", write, "close", closeFirst);
            ProtosObjectValue owned = harness.target("close", closeSecond);

            harness.close(
                    List.of(
                            harness.ordinary(wrapper, "write", write),
                            harness.mandatory(wrapper, "close", closeFirst),
                            harness.mandatory(owned, "close", closeSecond)));

            assertNull(harness.drain(), "the second close suspends while transfers stay pending");
            assertEquals(1, closeSecond.invocations());
            assertFalse(harness.release().terminal());

            assertTrue(closing.resolve(ProtosNullValue.INSTANCE, harness.module()));
            ControlFlowException escaped = harness.drain();
            assertNotNull(escaped);
            assertSame(
                    laterHome,
                    ((ProtosNonLocalReturnException) escaped).target(),
                    "the transfer escaping the first close supersedes the original");

            write.assertFailedOnce();
            closeFirst.assertFailedOnce();
            closeSecond.assertInvokedAndResolvedOnce();
            harness.assertReleaseFailed();
        }

        System.out.println("PLAT070_IO_RELEASE_SECOND_FINALIZER_SUSPENDS_ONCE=PASS");
    }

    @Test
    void transferEscapingAFinalizerInTheReleaseBodyStillRunsTheNextFinalizer() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            ProtosNonLocalReturnException original =
                    new ProtosNonLocalReturnException(
                            new ProtosReturnHome(), ProtosNullValue.INSTANCE);
            StepProbe flush = harness.probeAnswering(harness.resolvedFuture());
            StepProbe closeFirst = harness.probeThrowing(original);
            StepProbe closeSecond = harness.probeAnswering(harness.resolvedFuture());
            ProtosObjectValue wrapper = harness.target("flush", flush, "close", closeFirst);
            ProtosObjectValue owned = harness.target("close", closeSecond);

            harness.close(
                    List.of(
                            harness.ordinary(wrapper, "flush", flush),
                            harness.mandatory(wrapper, "close", closeFirst),
                            harness.mandatory(owned, "close", closeSecond)));

            assertSame(original, harness.drain());
            flush.assertInvokedAndResolvedOnce();
            closeFirst.assertFailedOnce();
            closeSecond.assertInvokedAndResolvedOnce();
            harness.assertReleaseFailed();
        }

        System.out.println("PLAT070_IO_RELEASE_BODY_FINALIZER_TRANSFER_NEXT_FINALIZER=RUN");
    }

    /*
     * Prepared positional argument arrays reach the target selectors unchanged, in step order,
     * and each step is invoked and committed exactly once by the release C-prime.
     */
    @Test
    void preparedArgumentStepsDeliverExactArgumentsAndCommitEachStepOnce() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            List<String> order = new ArrayList<>();
            List<List<?>> seen = new ArrayList<>();
            Object payload = new ProtosObjectValue(ProtosObjectValue.rootObject());
            StepProbe write =
                    harness.probeRecording("write", order, seen, harness.resolvedFuture());
            StepProbe flush =
                    harness.probeRecording("flush", order, seen, harness.resolvedFuture());
            StepProbe close =
                    harness.probeRecording("close", order, seen, harness.resolvedFuture());
            ProtosObjectValue target =
                    harness.target("write", write, "flush", flush, "close", close);

            harness.close(
                    List.of(
                            ProtosIoReleaseCPrimeExecution.Step.ordinary(
                                    target,
                                    "write",
                                    new Object[] {payload},
                                    write::resolved,
                                    write::failed),
                            ProtosIoReleaseCPrimeExecution.Step.ordinary(
                                    target, "flush", new Object[0], flush::resolved, flush::failed),
                            ProtosIoReleaseCPrimeExecution.Step.mandatoryFinalizer(
                                    target, "close", new Object[0], close::resolved, close::failed)));

            assertNull(harness.drain(), "no transfer escapes an ordinary release");
            assertEquals(List.of("write", "flush", "close"), order);
            assertEquals(1, seen.get(0).size());
            assertSame(payload, seen.get(0).get(0));
            assertEquals(List.of(), seen.get(1));
            assertEquals(List.of(), seen.get(2));
            write.assertInvokedAndResolvedOnce();
            flush.assertInvokedAndResolvedOnce();
            close.assertInvokedAndResolvedOnce();
            assertTrue(harness.release().terminal(), "the release is terminalized exactly once");
        }
    }

    @Test
    void preparedArgumentStepKeepsItsArgumentsWhenTheSourceArrayChangesLater() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            List<String> order = new ArrayList<>();
            List<List<?>> seen = new ArrayList<>();
            Object original = new ProtosObjectValue(ProtosObjectValue.rootObject());
            Object replacement = new ProtosObjectValue(ProtosObjectValue.rootObject());
            StepProbe write =
                    harness.probeRecording("write", order, seen, harness.resolvedFuture());
            StepProbe close =
                    harness.probeRecording("close", order, seen, harness.resolvedFuture());
            ProtosObjectValue target = harness.target("write", write, "close", close);
            Object[] source = {original};

            ProtosIoReleaseCPrimeExecution.Step step =
                    ProtosIoReleaseCPrimeExecution.Step.ordinary(
                            target, "write", source, write::resolved, write::failed);
            source[0] = replacement;
            harness.close(
                    List.of(
                            step,
                            ProtosIoReleaseCPrimeExecution.Step.mandatoryFinalizer(
                                    target, "close", new Object[0], close::resolved, close::failed)));

            assertNull(harness.drain());
            assertEquals(List.of("write", "close"), order);
            assertEquals(1, seen.get(0).size());
            assertSame(original, seen.get(0).get(0), "the step received the prepared value");
            write.assertInvokedAndResolvedOnce();
            close.assertInvokedAndResolvedOnce();
        }
    }

    @Test
    void preparedArgumentsStayStableAcrossReleaseSuspensionAndResumption() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            List<String> order = new ArrayList<>();
            List<List<?>> seen = new ArrayList<>();
            ProtosFutureValue flushing = harness.pendingFuture();
            Object flushArgument = new ProtosObjectValue(ProtosObjectValue.rootObject());
            Object closeArgument = new ProtosObjectValue(ProtosObjectValue.rootObject());
            Object replacement = new ProtosObjectValue(ProtosObjectValue.rootObject());
            StepProbe flush = harness.probeRecording("flush", order, seen, flushing);
            StepProbe close =
                    harness.probeRecording("close", order, seen, harness.resolvedFuture());
            ProtosObjectValue target = harness.target("flush", flush, "close", close);
            Object[] flushSource = {flushArgument};
            Object[] closeSource = {closeArgument};

            harness.close(
                    List.of(
                            ProtosIoReleaseCPrimeExecution.Step.ordinary(
                                    target, "flush", flushSource, flush::resolved, flush::failed),
                            ProtosIoReleaseCPrimeExecution.Step.mandatoryFinalizer(
                                    target,
                                    "close",
                                    closeSource,
                                    close::resolved,
                                    close::failed)));

            assertNull(harness.drain(), "the flush suspends on its pending Future");
            assertEquals(List.of("flush"), order);
            assertFalse(harness.release().terminal());

            // Writes to the caller's arrays during the suspension must not reach prepared steps.
            flushSource[0] = replacement;
            closeSource[0] = replacement;

            assertTrue(flushing.resolve(ProtosNullValue.INSTANCE, harness.module()));
            assertNull(harness.drain());
            assertEquals(List.of("flush", "close"), order);
            assertSame(flushArgument, seen.get(0).get(0));
            assertSame(closeArgument, seen.get(1).get(0));
            flush.assertInvokedAndResolvedOnce();
            close.assertInvokedAndResolvedOnce();
            assertTrue(harness.release().terminal());
        }
    }

    @Test
    void preparedSequenceKeepsItsStepsWhenTheSourceListChangesLater() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            List<String> order = new ArrayList<>();
            List<List<?>> seen = new ArrayList<>();
            ProtosFutureValue flushing = harness.pendingFuture();
            Object closeArgument = new ProtosObjectValue(ProtosObjectValue.rootObject());
            StepProbe flush = harness.probeRecording("flush", order, seen, flushing);
            StepProbe write =
                    harness.probeRecording("write", order, seen, harness.resolvedFuture());
            StepProbe close =
                    harness.probeRecording("close", order, seen, harness.resolvedFuture());
            ProtosObjectValue target =
                    harness.target("flush", flush, "write", write, "close", close);
            ProtosIoReleaseCPrimeExecution.Step closeStep =
                    ProtosIoReleaseCPrimeExecution.Step.mandatoryFinalizer(
                            target,
                            "close",
                            new Object[] {closeArgument},
                            close::resolved,
                            close::failed);
            List<ProtosIoReleaseCPrimeExecution.Step> source = new ArrayList<>();
            source.add(
                    ProtosIoReleaseCPrimeExecution.Step.ordinary(
                            target, "flush", new Object[0], flush::resolved, flush::failed));
            source.add(closeStep);

            harness.close(source);

            assertNull(harness.drain(), "the flush suspends on its pending Future");
            assertEquals(List.of("flush"), order);

            // Changes to the caller's list after preparation must not reach the sequence.
            source.remove(closeStep);
            source.add(
                    ProtosIoReleaseCPrimeExecution.Step.ordinary(
                            target, "write", new Object[0], write::resolved, write::failed));

            assertTrue(flushing.resolve(ProtosNullValue.INSTANCE, harness.module()));
            assertNull(harness.drain());
            assertEquals(List.of("flush", "close"), order);
            assertEquals(1, seen.get(1).size());
            assertSame(closeArgument, seen.get(1).get(0));
            flush.assertInvokedAndResolvedOnce();
            close.assertInvokedAndResolvedOnce();
            assertTrue(harness.release().terminal());
        }
    }

    @Test
    void preparedArgumentStepsRejectNullArgumentsBeforeAnyInvocation() {
        ProtosObjectValue target = new ProtosObjectValue(ProtosObjectValue.rootObject());
        assertThrows(
                NullPointerException.class,
                () -> ProtosIoReleaseCPrimeExecution.Step.ordinary(
                        target, "write", new Object[] {null}, () -> {}, ignored -> {}));
        assertThrows(
                NullPointerException.class,
                () -> ProtosIoReleaseCPrimeExecution.Step.mandatoryFinalizer(
                        target, "close", (Object[]) null, () -> {}, ignored -> {}));
    }

    @Test
    void cancellationOfAPreparedArgumentStepStillRunsTheMandatoryCloseOnce() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            ProtosTaskCancellationException cancellation = new ProtosTaskCancellationException();
            StepProbe write = harness.probeThrowing(cancellation);
            StepProbe close = harness.probeAnswering(harness.resolvedFuture());
            ProtosObjectValue target = harness.target("write", write, "close", close);

            harness.close(
                    List.of(
                            ProtosIoReleaseCPrimeExecution.Step.ordinary(
                                    target,
                                    "write",
                                    new Object[] {ProtosNullValue.INSTANCE},
                                    write::resolved,
                                    write::failed),
                            ProtosIoReleaseCPrimeExecution.Step.mandatoryFinalizer(
                                    target, "close", new Object[0], close::resolved, close::failed)));

            assertSame(cancellation, harness.drain(), "the original cancellation propagates");
            write.assertFailedOnce();
            close.assertInvokedAndResolvedOnce();
            harness.assertReleaseFailed();
        }
    }

    @Test
    void guestErrorInAReleaseStepKeepsItsExistingFailedInvocationTreatment() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            StepProbe flush = harness.probeSignalling();
            StepProbe close = harness.probeAnswering(harness.resolvedFuture());
            ProtosObjectValue target = harness.target("flush", flush, "close", close);

            harness.close(
                    List.of(
                            harness.ordinary(target, "flush", flush),
                            harness.mandatory(target, "close", close)));

            assertNull(harness.drain(), "a guest Error is not an escaping transfer");
            flush.assertFailedOnce();
            close.assertInvokedAndResolvedOnce();
            harness.assertReleaseFailed();
        }

        System.out.println("PLAT070_IO_RELEASE_GUEST_ERROR_TREATMENT=UNCHANGED");
    }

    @Test
    void resumedFinalizerTransferStillRunsTheNextSuspendingFinalizer() throws Exception {
        try (LanguageScope scope = languageScope()) {
            Harness harness = new Harness(scope.language());
            ProtosNonLocalReturnException original = new ProtosNonLocalReturnException(
                    new ProtosReturnHome(), ProtosNullValue.INSTANCE);
            ProtosTaskCancellationException later = new ProtosTaskCancellationException();
            ProtosFutureValue callbackWait = harness.pendingFuture();
            ProtosFutureValue closeWait = harness.pendingFuture();
            StepProbe flush = harness.probeThrowing(original);
            StepProbe firstClose = harness.probeSuspendingThenThrowing(callbackWait, later);
            StepProbe secondClose = harness.probeAnswering(closeWait);
            ProtosObjectValue target = harness.target(
                    "flush", flush, "firstClose", firstClose, "secondClose", secondClose);
            harness.close(List.of(
                    harness.ordinary(target, "flush", flush),
                    harness.mandatory(target, "firstClose", firstClose),
                    harness.mandatory(target, "secondClose", secondClose)));

            assertNull(harness.drain(), "the finalizer callback suspends with the original transfer held");
            assertEquals(1, firstClose.invocations());
            assertEquals(0, secondClose.invocations());
            assertFalse(harness.release().terminal());

            assertTrue(callbackWait.resolve(ProtosNullValue.INSTANCE, harness.module()));
            assertNull(harness.drain(), "the next finalizer suspends with the later transfer held");
            firstClose.assertFailedOnce();
            assertEquals(1, firstClose.invocations(), "the callback prefix must not replay");
            assertEquals(1, secondClose.invocations());
            assertFalse(harness.release().terminal());

            assertTrue(closeWait.resolve(ProtosNullValue.INSTANCE, harness.module()));
            assertSame(later, harness.drain(), "the resumed transfer keeps its exact identity");
            flush.assertFailedOnce();
            firstClose.assertFailedOnce();
            secondClose.assertInvokedAndResolvedOnce();
            harness.assertReleaseFailed();
        }
    }

    @Test
    void everyIoCPrimeRootAccountsEscapingTransfersThroughTryFinally() throws Exception {
        for (String protocol :
                List.of(
                        "ProtosBufferedByteReaderCPrimeExecution",
                        "ProtosBufferedByteWriterCPrimeExecution",
                        "ProtosTextReaderCPrimeExecution",
                        "ProtosTextWriterCPrimeExecution")) {
            String source = read(protocol);
            assertTrue(
                    source.contains("ProtosIoCPrimeTransferAccounting.begin(builder)"),
                    protocol);
            assertTrue(source.contains("ProtosIoCPrimeTransferAccounting.end("), protocol);
            assertTrue(source.contains("void transferEscaped()"), protocol);
        }
        String release = read("ProtosIoReleaseCPrimeExecution");
        assertTrue(release.contains("emitTransferCleanup(builder, locals, completed"));
        assertTrue(release.contains("boolean beginTransferCleanup("));

        System.out.println("PLAT070_IO_CPRIME_TRANSFER_ACCOUNTING=TRY_FINALLY_BASED");
    }

    @Test
    void hostIoAndSchedulerEntriesAreBoundariesWhileGuestControlStaysCompilable()
            throws Exception {
        String main = "src/main/java/com/guillermomolina/protos/";
        assertBoundary(main + "runtime/io/ProtosBufferedByteIo.java",
                "public boolean observeReadLowerForCPrimeRuntime(",
                "public void consumeReadLowerForCPrimeRuntime(");
        assertBoundary(main + "runtime/io/ProtosIoOperation.java",
                "public void onDeferredCPrimeTerminalIdleForRuntime(",
                "public boolean requestDeferredCPrimeRunForRuntime(");
        assertBoundary(main + "runtime/ProtosActorExecutionDomain.java",
                "void enqueueActorIoOperationForRuntime(",
                "public void enqueueActorIoReleaseForRuntime(");
        assertBoundary(main + "runtime/io/ProtosTextReader.java",
                "public boolean observeLowerForCPrimeRuntime(",
                "public boolean consumeLowerForCPrimeRuntime(",
                "public boolean invalidLowerForCPrimeRuntime(",
                "public boolean invocationFailedForCPrimeRuntime(");
        assertBoundary(main + "execution/modules/ProtosModuleSource.java",
                "String sourceName(",
                "Source literalSource(");

        String futureProtocol = Files.readString(
                Path.of(main + "execution/protocols/actors/ProtosStandardFutureProtocol.java"),
                StandardCharsets.UTF_8);
        assertNoBoundary(futureProtocol,
                "static Object awaitFutureForIoOperationContinuationForRuntime(");
        assertNoBoundary(read("ProtosBufferedByteReaderCPrimeExecution"),
                "Object awaitTargetFuture(");
    }

    @Test
    void terminalHostHandlersAreBoundariesWhileTerminalStateAndGuestSegmentsStayCompilable()
            throws Exception {
        String main = "src/main/java/com/guillermomolina/protos/";
        String operationFile = main + "runtime/io/ProtosIoOperation.java";
        assertBoundary(operationFile,
                "private static void runTerminalIdleHandler(",
                "private static void notifyTerminalizedExecution(",
                "private static void runCancellationHandler(");
        assertBoundary(main + "runtime/ProtosActorExecutionDomain.java",
                "void registerActorIoOperation(",
                "void terminalActorIoOperation(");
        assertBoundary(main + "runtime/io/ProtosIoLifecycle.java",
                "private boolean retireOperationRegistration(");

        String operation = Files.readString(Path.of(operationFile), StandardCharsets.UTF_8);
        assertNoBoundary(operation, "private void finishTerminal(");
        assertFalse(operation.contains("terminalIdle.run()"));
        assertFalse(operation.contains("deliver.run()"));
        String lifecycle = Files.readString(
                Path.of(main + "runtime/io/ProtosIoLifecycle.java"), StandardCharsets.UTF_8);
        assertNoBoundary(lifecycle, "private void startRelease(");
        String tasks = main + "execution/bytecode/activation/tasks/";
        assertNoBoundary(Files.readString(
                        Path.of(tasks + "ProtosBytecodeIoOperationExecution.java"),
                        StandardCharsets.UTF_8),
                "public void runSegment(ProtosIoOperation scheduledOperation)");
        assertNoBoundary(Files.readString(
                        Path.of(tasks + "ProtosBytecodeIoReleaseExecution.java"),
                        StandardCharsets.UTF_8),
                "public void runSegment(ProtosIoReleaseExecution scheduledRelease)");
        for (String protocol :
                List.of(
                        "ProtosBufferedByteReaderCPrimeExecution",
                        "ProtosBufferedByteWriterCPrimeExecution",
                        "ProtosTextReaderCPrimeExecution",
                        "ProtosTextWriterCPrimeExecution")) {
            assertTrue(read(protocol).contains("ProtosIoCPrimeTransferAccounting.begin(builder)"),
                    protocol);
        }
    }

    @Test
    void hostCancellationAndSchedulingCallbacksAreNarrowBoundaries() throws Exception {
        String main = "src/main/java/com/guillermomolina/protos/";
        String facilities = main + "execution/facilities/";
        assertBoundary(facilities + "ProtosPasswordArgon2Facility.java",
                "private static void cancelHostTicket(");
        assertBoundary(facilities + "ProtosPasswordKdfCustody.java",
                "public PasswordKdfAdmission submitForRuntime(");
        assertBoundary(facilities + "ProtosPasswordKdfExecutor.java",
                "Ticket submit(");
        assertBoundary(main + "runtime/actors/ProtosActorDeliveryAttempt.java",
                "public boolean cancelBeforeAcceptance(");
        assertBoundary(main + "runtime/actors/ProtosActorScheduler.java",
                "private void submitWorker(");
        assertBoundary(main + "runtime/io/ProtosTextReader.java",
                "private FoldResult resolvePendingLf(",
                "private ReadResult scanText(",
                "private LineResult scanLine(",
                "private static void appendLineText(");
        assertBoundary(main + "runtime/ProtosDynamicControlState.java",
                "public boolean hasActiveEnsureFrames(",
                "public List<Frame> framesNewestFirst(");

        String scheduler = Files.readString(
                Path.of(main + "runtime/actors/ProtosActorScheduler.java"), StandardCharsets.UTF_8);
        for (String guestEntry : List.of(
                "private void signal(",
                "private void workerLoop(",
                "private void runOneHostedSegment(",
                "private void runOneSegment(")) {
            assertNoBoundary(scheduler, guestEntry);
        }

        String future = Files.readString(
                Path.of(main + "runtime/actors/ProtosFutureValue.java"), StandardCharsets.UTF_8);
        assertNoBoundary(future, "public boolean cancelRequest(");
        assertNoBoundary(future, "public boolean cancelTerminal(");

        String textReader = Files.readString(
                Path.of(main + "runtime/io/ProtosTextReader.java"), StandardCharsets.UTF_8);
        for (String sequencing : List.of(
                "private boolean advanceUntilInputOrTerminal(",
                "private void completeText(",
                "private void completeEof(",
                "private void completeLine(",
                "private void failEncoding(",
                "private void startLowerRead(")) {
            assertNoBoundary(textReader, sequencing);
        }

        String control = Files.readString(
                Path.of(main + "runtime/ProtosDynamicControlState.java"), StandardCharsets.UTF_8);
        for (String transition : List.of(
                "public void beginEnsureCleanup(",
                "public void completeWhileCondition(",
                "public void completeWhileBody(",
                "public void leaveFrame(")) {
            assertNoBoundary(control, transition);
        }
    }

    private static void assertNoBoundary(String source, String declaration) {
        int at = source.indexOf(declaration);
        assertTrue(at > 0, declaration);
        assertFalse(source.substring(0, at).stripTrailing().endsWith("@TruffleBoundary"),
                declaration);
    }

    private static void assertBoundary(String file, String... declarations) throws Exception {
        String source = Files.readString(Path.of(file), StandardCharsets.UTF_8);
        for (String declaration : declarations) {
            int at = source.indexOf(declaration);
            assertTrue(at > 0, file + ": " + declaration);
            String before = source.substring(0, at).stripTrailing();
            assertTrue(before.endsWith("@TruffleBoundary"), file + ": " + declaration);
        }
    }

    private static String read(String protocol) throws Exception {
        return Files.readString(
                Path.of(
                        "src/main/java/com/guillermomolina/protos/execution/host/resources",
                        protocol + ".java"),
                StandardCharsets.UTF_8);
    }

    /** One step's guest target and the lifecycle actions recorded for it. */
    private static final class StepProbe {
        private final ProtosClosureValue target;
        private final AtomicInteger invocations;
        private final AtomicInteger resolved = new AtomicInteger();
        private final List<ProtosObjectValue> failures = new ArrayList<>();

        StepProbe(ProtosClosureValue target, AtomicInteger invocations) {
            this.target = target;
            this.invocations = invocations;
        }

        ProtosClosureValue target() {
            return target;
        }

        void resolved() {
            resolved.incrementAndGet();
        }

        void failed(ProtosObjectValue error) {
            failures.add(error);
        }

        int invocations() {
            return invocations.get();
        }

        int resolvedCount() {
            return resolved.get();
        }

        int failedCount() {
            return failures.size();
        }

        void assertFailedOnce() {
            assertEquals(1, failures.size(), "the step is accounted exactly once");
            assertNotNull(failures.get(0));
            assertEquals(0, resolved.get());
        }

        void assertInvokedAndResolvedOnce() {
            assertEquals(1, invocations.get(), "the step is invoked exactly once");
            assertEquals(1, resolved.get(), "the step is committed exactly once");
            assertEquals(0, failures.size());
        }
    }

    private static final class Harness {
        private final ProtosLanguage language;
        private final ProtosPrelude prelude;
        private final ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
        private final ProtosActivation module;
        private ProtosIoReleaseExecution release;
        private ProtosFutureValue closed;

        Harness(ProtosLanguage language) throws Exception {
            this.language = language;
            this.prelude = new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
            this.module =
                    prelude.newModuleActivation(
                            new ProtosActorModuleState(),
                            null,
                            prelude.newExecutionContext(),
                            domain);
        }

        ProtosActivation module() {
            return module;
        }

        ProtosIoReleaseExecution release() {
            return release;
        }

        ProtosFutureValue pendingFuture() {
            return new ProtosFutureValue(prelude.futurePrototype(), domain);
        }

        ProtosFutureValue resolvedFuture() {
            ProtosFutureValue future = pendingFuture();
            assertTrue(future.resolve(ProtosNullValue.INSTANCE, module));
            return future;
        }

        /** A guest object whose selectors answer the given probes' closures. */
        ProtosObjectValue target(Object... selectorsAndProbes) {
            ProtosObjectValue target = new ProtosObjectValue(ProtosObjectValue.rootObject());
            for (int index = 0; index < selectorsAndProbes.length; index += 2) {
                target.createLocalSlot(
                        (String) selectorsAndProbes[index],
                        ((StepProbe) selectorsAndProbes[index + 1]).target());
            }
            return target;
        }

        ProtosIoReleaseCPrimeExecution.Step ordinary(
                ProtosObjectValue target, String selector, StepProbe probe) {
            return ProtosIoReleaseCPrimeExecution.Step.ordinary(
                    target, selector, List.of(), probe::resolved, probe::failed);
        }

        ProtosIoReleaseCPrimeExecution.Step mandatory(
                ProtosObjectValue target, String selector, StepProbe probe) {
            return ProtosIoReleaseCPrimeExecution.Step.mandatoryFinalizer(
                    target, selector, List.of(), probe::resolved, probe::failed);
        }

        /** A probe whose guest source body returns non-locally to {@code home}. */
        StepProbe probeReturningTo(ProtosReturnHome home) throws Exception {
            AtomicInteger invocations = new AtomicInteger();
            ProtosClosureValue returning = returningClosure(home);
            return new StepProbe(
                    nativeClosure(
                            (activation, supplied) -> {
                                invocations.incrementAndGet();
                                return ProtosClosureInvoker.invoke(returning, List.of());
                            }),
                    invocations);
        }

        StepProbe probe() {
            return probeAnswering(ProtosNullValue.INSTANCE);
        }

        StepProbe probeAnswering(Object result) {
            AtomicInteger invocations = new AtomicInteger();
            return new StepProbe(
                    nativeClosure(
                            (activation, supplied) -> {
                                invocations.incrementAndGet();
                                return result;
                            }),
                    invocations);
        }

        /** A probe recording its selector and exact supplied arguments before answering. */
        StepProbe probeRecording(
                String selector, List<String> order, List<List<?>> seen, Object result) {
            AtomicInteger invocations = new AtomicInteger();
            return new StepProbe(
                    nativeClosure(
                            (activation, supplied) -> {
                                invocations.incrementAndGet();
                                order.add(selector);
                                seen.add(List.copyOf(supplied));
                                return result;
                            }),
                    invocations);
        }

        StepProbe probeThrowing(ControlFlowException transfer) {
            AtomicInteger invocations = new AtomicInteger();
            return new StepProbe(
                    nativeClosure(
                            (activation, supplied) -> {
                                invocations.incrementAndGet();
                                throw transfer;
                            }),
                    invocations);
        }

        StepProbe probeSuspendingThenThrowing(
                ProtosFutureValue lower, ControlFlowException transfer) throws Exception {
            AtomicInteger invocations = new AtomicInteger();
            module.context().createLocalSlot("markFinalizer", nativeClosure(
                    (activation, supplied) -> {
                        invocations.incrementAndGet();
                        return ProtosNullValue.INSTANCE;
                    }));
            module.context().createLocalSlot("finalizerWait", lower);
            module.context().createLocalSlot("raiseFinalizerTransfer", nativeClosure(
                    (activation, supplied) -> { throw transfer; }));
            return new StepProbe(sourceClosure(
                    "() => { markFinalizer()\nfinalizerWait.value()\nraiseFinalizerTransfer() }",
                    null), invocations);
        }

        StepProbe probeSignalling() {
            AtomicInteger invocations = new AtomicInteger();
            return new StepProbe(
                    nativeClosure(
                            (activation, supplied) -> {
                                invocations.incrementAndGet();
                                throw new com.guillermomolina.protos.runtime.ProtosSignalException(
                                        com.guillermomolina.protos.runtime.ProtosCoreErrors
                                                .newError(activation));
                            }),
                    invocations);
        }

        /** A source Closure whose body returns non-locally to {@code home}. */
        ProtosClosureValue returningClosure(ProtosReturnHome home) throws Exception {
            return sourceClosure("() => ^1", home);
        }

        private ProtosClosureValue sourceClosure(String characters, ProtosReturnHome home)
                throws Exception {
            CanonicalClosure definition =
                    (CanonicalClosure) canonicalize(characters).expressions().get(0);
            ProtosClosureExecutionPlan plan =
                    ProtosClosureExecutionPlan.bytecode(
                            definition,
                            language,
                            Source.newBuilder(
                                            ProtosLanguage.ID,
                                            characters,
                                            "plat070-io-release-step.protos")
                                    .build());
            return new ProtosClosureValue(
                    definition,
                    module.lexicalContextsForClosureCapture(),
                    module.receiver(),
                    module.methodHome().orElse(null),
                    home,
                    prelude,
                    plan);
        }

        /** Closes a lifecycle whose release runs {@code steps} through the release C-prime. */
        void close(List<ProtosIoReleaseCPrimeExecution.Step> steps) {
            ProtosIoLifecycle[] lifecycle = new ProtosIoLifecycle[1];
            lifecycle[0] =
                    new ProtosIoLifecycle(
                            new ProtosObjectValue(ProtosObjectValue.rootObject()),
                            prelude.futurePrototype(),
                            domain,
                            completion -> {
                                release =
                                        lifecycle[0].beginReleaseExecutionForRuntime(
                                                module, completion);
                                ProtosIoReleaseCPrimeExecution.schedule(
                                        ProtosIoReleaseCPrimeExecution.planForEnteredContext(),
                                        release,
                                        ProtosIoReleaseCPrimeExecution.sequence(
                                                release, null, steps));
                            });
            closed = lifecycle[0].close(module);
            assertNotNull(release, "the release must have started");
        }

        /**
         * Dispatches until the domain is idle. Returns the internal transfer that propagated out
         * of a dispatch, or {@code null}.
         */
        ControlFlowException drain() {
            ControlFlowException escaped = null;
            for (int dispatches = 0; dispatches < 16; dispatches++) {
                try {
                    if (!domain.dispatchOne()) {
                        return escaped;
                    }
                } catch (ControlFlowException transfer) {
                    escaped = transfer;
                }
            }
            throw new AssertionError("release C-prime did not become idle");
        }

        void assertReleaseFailed() {
            assertTrue(release.terminal(), "the release is terminalized exactly once");
            assertNotEquals(ProtosFutureValue.State.PENDING, closed.state());
            assertNotEquals(ProtosFutureValue.State.RESOLVED, closed.state());
        }
    }

    private static ProtosClosureValue nativeClosure(ProtosNativeClosureBody body) {
        return ProtosClosureValue.suspensionCapableNativeClosure(body, body);
    }

    private static CanonicalSequence canonicalize(String characters) {
        return (CanonicalSequence)
                new Canonicalizer().canonicalize(new ProtosParser(characters).parseProgram());
    }

    private static LanguageScope languageScope() {
        Context context = Context.newBuilder(ProtosLanguage.ID).build();
        context.initialize(ProtosLanguage.ID);
        context.enter();
        return new LanguageScope(context, LANGUAGE_REF.get(null));
    }

    private record LanguageScope(Context context, ProtosLanguage language)
            implements AutoCloseable {
        @Override
        public void close() {
            context.leave();
            context.close();
        }
    }
}

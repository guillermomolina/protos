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

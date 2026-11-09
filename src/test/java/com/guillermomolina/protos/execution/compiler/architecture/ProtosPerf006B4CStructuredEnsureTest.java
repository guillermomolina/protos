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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

final class ProtosPerf006B4CStructuredEnsureTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void normalCompletionPreservesExactBodyResultAndRunsCleanupOnce()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosObjectValue token = new ProtosObjectValue(ProtosObjectValue.rootObject());
            AtomicInteger cleanups = new AtomicInteger();

            module.context().createLocalSlot(
                    "body",
                    nativeClosure((activation, supplied) -> token));
            module.context().createLocalSlot(
                    "cleanup",
                    nativeClosure(
                            (activation, supplied) -> {
                                cleanups.incrementAndGet();
                                return Long.valueOf(999);
                            }));

            Object result =
                    lowerRoot(
                                    scope.language(),
                                    "body.ensure(cleanup)",
                                    "perf006-b4c-normal.protos")
                            .getCallTarget()
                            .call(module);

            assertSame(token, result);
            assertEquals(1, cleanups.get());
        }

        System.out.println("PERF006_B4C_NORMAL_EXACT_RESULT=PASS");
        System.out.println("PERF006_B4C_NORMAL_CLEANUP_EXACTLY_ONCE=PASS");
    }

    @Test
    void bodySuspensionDoesNotRunCleanupAndCompletedPrefixDoesNotReplay()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue future = new ProtosFutureValue(prelude.futurePrototype(), domain);
            AtomicInteger bodyPrefix = new AtomicInteger();
            java.util.concurrent.atomic.AtomicReference<ProtosReturnHome> ownedHome =
                    new java.util.concurrent.atomic.AtomicReference<>();
            AtomicInteger cleanups = new AtomicInteger();
            ProtosObjectValue resolved = new ProtosObjectValue(ProtosObjectValue.rootObject());

            module.context().createLocalSlot("f", future);
            module.context().createLocalSlot(
                    "bodyProbe",
                    nativeClosure(
                            (activation, supplied) -> {
                                bodyPrefix.incrementAndGet();
                                ownedHome.set(((ProtosClosureValue) supplied.get(0))
                                        .returnHome().orElseThrow());
                                return ProtosNullValue.INSTANCE;
                            }));
            module.context().createLocalSlot("resumeProbe", nativeClosure((activation, supplied) -> {
                ProtosReturnHome resumed = ((ProtosClosureValue) supplied.get(0)).returnHome().orElseThrow();
                assertSame(ownedHome.get(), resumed);
                assertTrue(resumed.isActive());
                assertSame(activation.taskForRuntime(), resumed.ownerTaskForRuntime());
                return ProtosNullValue.INSTANCE;
            }));
            module.context().createLocalSlot(
                    "cleanup",
                    nativeClosure(
                            (activation, supplied) -> {
                                cleanups.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            }));
            module.context().createLocalSlot(
                    "body",
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => { bodyProbe(() => ^0)\nresult: f.value()\nresumeProbe(() => ^0)\n^result }",
                            "perf006-b4c-body-suspend-body.protos"));

            ProtosTask task =
                    execute(
                            domain,
                            module,
                            lowerRoot(
                                    scope.language(),
                                    "entryB: () => { body.ensure(cleanup) }\nentryA: () => { entryB() }\nentryA()",
                                    "perf006-b4c-body-suspend-top.protos"));

            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertEquals(1, bodyPrefix.get());
            assertEquals(0, cleanups.get(), "suspension is not ensure scope exit");
            assertTrue(ownedHome.get().isMaterialized());
            assertTrue(ownedHome.get().isActive());
            assertSame(task, ownedHome.get().ownerTaskForRuntime());

            assertTrue(future.resolve(resolved, module));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertSame(resolved, task.result().orElseThrow());
            org.junit.jupiter.api.Assertions.assertFalse(ownedHome.get().isActive());
            org.junit.jupiter.api.Assertions.assertNull(ownedHome.get().ownerTaskForRuntime());
            assertEquals(1, bodyPrefix.get(), "completed body prefix must not replay");
            assertEquals(1, cleanups.get());
        }

        System.out.println("PERF006_B4C_BODY_SUSPENSION_NOT_UNWIND=PASS");
        System.out.println("PERF006_B4C_BODY_PREFIX_REPLAY=NO");
    }

    @Test
    void pendingBodyErrorSurvivesCleanupSuspensionWithExactIdentity()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue failed = new ProtosFutureValue(prelude.futurePrototype(), domain);
            ProtosFutureValue cleanupWait = new ProtosFutureValue(prelude.futurePrototype(), domain);
            ProtosObjectValue error = ProtosCoreErrors.newError(module);
            AtomicInteger cleanupPrefix = new AtomicInteger();

            assertTrue(failed.fail(error));
            module.context().createLocalSlot("failed", failed);
            module.context().createLocalSlot("cleanupWait", cleanupWait);
            module.context().createLocalSlot(
                    "cleanupProbe",
                    nativeClosure(
                            (activation, supplied) -> {
                                cleanupPrefix.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            }));
            module.context().createLocalSlot(
                    "body",
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => failed.value()",
                            "perf006-b4c-error-body.protos"));
            module.context().createLocalSlot(
                    "cleanup",
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => { cleanupProbe()\ncleanupWait.value() }",
                            "perf006-b4c-error-cleanup.protos"));

            ProtosTask task =
                    execute(
                            domain,
                            module,
                            lowerRoot(
                                    scope.language(),
                                    "body.ensure(cleanup)",
                                    "perf006-b4c-error-suspend-top.protos"));

            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertEquals(1, cleanupPrefix.get());

            assertTrue(cleanupWait.resolve(ProtosNullValue.INSTANCE, module));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.FAILED, task.state());
            assertSame(error, task.failure().orElseThrow());
            assertEquals(
                    1,
                    cleanupPrefix.get(),
                    "completed cleanup prefix must not replay after its suspension");
        }

        System.out.println("PERF006_B4C_PENDING_ERROR_ACROSS_CLEANUP_SUSPENSION=PASS");
        System.out.println("PERF006_B4C_CLEANUP_PREFIX_REPLAY=NO");
    }

    @Test
    void cleanupErrorSupersedesPendingBodyError()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue bodyFuture = new ProtosFutureValue(prelude.futurePrototype(), domain);
            ProtosFutureValue cleanupFuture = new ProtosFutureValue(prelude.futurePrototype(), domain);
            ProtosObjectValue bodyError = ProtosCoreErrors.newError(module);
            ProtosObjectValue cleanupError = ProtosCoreErrors.newError(module);

            assertTrue(bodyFuture.fail(bodyError));
            assertTrue(cleanupFuture.fail(cleanupError));
            module.context().createLocalSlot("bodyFuture", bodyFuture);
            module.context().createLocalSlot("cleanupFuture", cleanupFuture);
            module.context().createLocalSlot(
                    "body",
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => bodyFuture.value()",
                            "perf006-b4c-supersede-body.protos"));
            module.context().createLocalSlot(
                    "cleanup",
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => cleanupFuture.value()",
                            "perf006-b4c-supersede-cleanup.protos"));

            ProtosTask task =
                    execute(
                            domain,
                            module,
                            lowerRoot(
                                    scope.language(),
                                    "body.ensure(cleanup)",
                                    "perf006-b4c-supersede-top.protos"));

            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.FAILED, task.state());
            assertSame(cleanupError, task.failure().orElseThrow());
        }

        System.out.println("PERF006_B4C_CLEANUP_ERROR_SUPERSEDES_BODY_ERROR=PASS");
    }

    @Test
    void nonLocalReturnRunsCleanupThenPropagatesExactOriginalTransfer()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosObjectValue payload = new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosReturnHome externalHome = new ProtosReturnHome();
            AtomicInteger cleanups = new AtomicInteger();

            module.context().createLocalSlot("payload", payload);
            module.context().createLocalSlot(
                    "body",
                    sourceClosureWithHome(
                            scope.language(),
                            module,
                            "() => ^payload",
                            "perf006-b4c-nlr-body.protos",
                            externalHome));
            module.context().createLocalSlot(
                    "cleanup",
                    nativeClosure(
                            (activation, supplied) -> {
                                cleanups.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            }));

            ProtosClosureValue runner =
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => body.ensure(cleanup)",
                            "perf006-b4c-nlr-runner.protos");
            ProtosActivation invocation = invocation(runner, prelude, module, domain);

            ProtosNonLocalReturnException transfer =
                    assertThrows(
                            ProtosNonLocalReturnException.class,
                            () -> runner.executionPlanForRuntimeInvocation().executeBody(invocation));

            assertSame(externalHome, transfer.target());
            assertSame(payload, transfer.value());
            assertEquals(1, cleanups.get());
        }

        System.out.println("PERF006_B4C_NLR_CROSSES_CLEANUP=PASS");
        System.out.println("PERF006_B4C_NLR_EXACT_IDENTITY_AFTER_CLEANUP=PASS");
    }

    @Test
    void cleanupNonLocalReturnSupersedesPendingBodyNonLocalReturn()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosObjectValue first = new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosObjectValue second = new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosReturnHome firstHome = new ProtosReturnHome();
            ProtosReturnHome secondHome = new ProtosReturnHome();

            module.context().createLocalSlot("first", first);
            module.context().createLocalSlot("second", second);
            module.context().createLocalSlot(
                    "body",
                    sourceClosureWithHome(
                            scope.language(),
                            module,
                            "() => ^first",
                            "perf006-b4c-first-nlr.protos",
                            firstHome));
            module.context().createLocalSlot(
                    "cleanup",
                    sourceClosureWithHome(
                            scope.language(),
                            module,
                            "() => ^second",
                            "perf006-b4c-second-nlr.protos",
                            secondHome));

            ProtosClosureValue runner =
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => body.ensure(cleanup)",
                            "perf006-b4c-nlr-supersede-runner.protos");
            ProtosActivation invocation = invocation(runner, prelude, module, domain);

            ProtosNonLocalReturnException transfer =
                    assertThrows(
                            ProtosNonLocalReturnException.class,
                            () -> runner.executionPlanForRuntimeInvocation().executeBody(invocation));

            assertSame(secondHome, transfer.target());
            assertSame(second, transfer.value());
            assertTrue(firstHome.isActive());
            assertTrue(secondHome.isActive());
        }

        System.out.println("PERF006_B4C_CLEANUP_NLR_SUPERSEDES_BODY_NLR=PASS");
    }

    @Test
    void nestedStructuredEnsuresRunCleanupInLifoOrder()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosObjectValue token = new ProtosObjectValue(ProtosObjectValue.rootObject());
            List<Integer> order = new ArrayList<>();

            module.context().createLocalSlot("token", token);
            module.context().createLocalSlot(
                    "cleanup1",
                    nativeClosure(
                            (activation, supplied) -> {
                                order.add(1);
                                return ProtosNullValue.INSTANCE;
                            }));
            module.context().createLocalSlot(
                    "cleanup2",
                    nativeClosure(
                            (activation, supplied) -> {
                                order.add(2);
                                return ProtosNullValue.INSTANCE;
                            }));
            module.context().createLocalSlot(
                    "inner",
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => token",
                            "perf006-b4c-nested-inner.protos"));
            module.context().createLocalSlot(
                    "outerBody",
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => inner.ensure(cleanup1)",
                            "perf006-b4c-nested-outer-body.protos"));

            Object result =
                    lowerRoot(
                                    scope.language(),
                                    "outerBody.ensure(cleanup2)",
                                    "perf006-b4c-nested-top.protos")
                            .getCallTarget()
                            .call(module);

            assertSame(token, result);
            assertEquals(List.of(1, 2), order);
        }

        System.out.println("PERF006_B4C_NESTED_ENSURE_LIFO=PASS");
    }

    @Test
    void extractedEnsureKeepsCapabilityButNearerOverrideIsNotSelectorPet()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosObjectValue token = new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosObjectValue overrideResult = new ProtosObjectValue(ProtosObjectValue.rootObject());
            AtomicInteger extractedCleanups = new AtomicInteger();
            AtomicInteger overrideCalls = new AtomicInteger();
            AtomicInteger overrideCleanupCalls = new AtomicInteger();

            ProtosClosureValue body =
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => token",
                            "perf006-b4c-provenance-body.protos");
            module.context().createLocalSlot("token", token);

            ProtosClosureValue extracted =
                    assertInstanceOf(
                            ProtosClosureValue.class,
                            ProtosValueLookup.readMember(body, "ensure", prelude).orElseThrow());
            module.context().createLocalSlot("ensurer", extracted);
            module.context().createLocalSlot(
                    "cleanup",
                    nativeClosure(
                            (activation, supplied) -> {
                                extractedCleanups.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            }));

            Object extractedResult =
                    lowerRoot(
                                    scope.language(),
                                    "ensurer(cleanup)",
                                    "perf006-b4c-extracted.protos")
                            .getCallTarget()
                            .call(module);
            assertSame(token, extractedResult);
            assertEquals(1, extractedCleanups.get());

            ProtosClosureValue overriddenBody =
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => token",
                            "perf006-b4c-override-body.protos");
            overriddenBody.createLocalSlot(
                    "ensure",
                    nativeClosure(
                            (activation, supplied) -> {
                                overrideCalls.incrementAndGet();
                                return overrideResult;
                            }));
            module.context().createLocalSlot("overriddenBody", overriddenBody);
            module.context().createLocalSlot(
                    "overrideCleanup",
                    nativeClosure(
                            (activation, supplied) -> {
                                overrideCleanupCalls.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            }));

            Object overriddenResult =
                    lowerRoot(
                                    scope.language(),
                                    "overriddenBody.ensure(overrideCleanup)",
                                    "perf006-b4c-override-top.protos")
                            .getCallTarget()
                            .call(module);
            assertSame(overrideResult, overriddenResult);
            assertEquals(1, overrideCalls.get());
            assertEquals(
                    0,
                    overrideCleanupCalls.get(),
                    "selector spelling alone must not activate structured standard ensure");
        }

        System.out.println("PERF006_B4C_EXTRACTED_ENSURE_PROVENANCE=PASS");
        System.out.println("PERF006_B4C_ENSURE_SELECTOR_PET=NO");
    }

    @Test
    void validationFailureOccursBeforeProtectedBodyEntry()
            throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            AtomicInteger bodyCalls = new AtomicInteger();

            module.context().createLocalSlot(
                    "body",
                    nativeClosure(
                            (activation, supplied) -> {
                                bodyCalls.incrementAndGet();
                                return ProtosNullValue.INSTANCE;
                            }));
            module.context().createLocalSlot(
                    "notCleanup",
                    new ProtosObjectValue(ProtosObjectValue.rootObject()));

            assertThrows(
                    ProtosSignalException.class,
                    () ->
                            lowerRoot(
                                            scope.language(),
                                            "body.ensure(notCleanup)",
                                            "perf006-b4c-validation.protos")
                                    .getCallTarget()
                                    .call(module));
            assertEquals(0, bodyCalls.get());
        }

        System.out.println("PERF006_B4C_VALIDATION_BEFORE_EXTENT=PASS");
    }

    private static ProtosClosureValue nativeClosure(
            com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody body) {
        return ProtosClosureValue.suspensionCapableNativeClosure(body, body);
    }

    private static ProtosClosureValue sourceClosure(
            ProtosLanguage language,
            ProtosActivation creator,
            String characters,
            String sourceName)
            throws Exception {
        CanonicalClosure definition = closureDefinition(characters);
        ProtosClosureExecutionPlan plan =
                ProtosClosureExecutionPlan.bytecode(
                        definition,
                        language,
                        source(characters, sourceName));
        return new ProtosClosureValue(
                definition,
                creator.lexicalContextsForClosureCapture(),
                creator.receiver(),
                creator.methodHome().orElse(null),
                creator.returnHome().orElse(null),
                creator.prelude().orElseThrow(),
                plan);
    }

    /*
     * PLAT070 cross-product of the original pending outcome of an ensure body and the outcome of
     * its cleanup. Selected transfers unwind directly, so cleanup supersession must not depend on
     * guest catch interception. Marks: 1 body entered, 2 cleanup entered, 9 handler, 3 after.
     */
    private static final String ENSURE_MATRIX_TEMPLATE =
            """
            handledLocally: () => {
                (() => { ^0 })()
                1
            }
            m: () => {
                r: Error.handle(() => {
                    (() => {
                        mark(1)
                        BODY
                    }).ensure(() => {
                        mark(2)
                        CLEANUP
                    })
                }, (caught) => {
                    mark(9)
                    caught
                })
                mark(3)
                r
            }
            m()
            """;

    private enum BodyOutcome {
        NORMAL("10"),
        RETURN("^20"),
        ERROR("bodyError.signal()");

        final String source;

        BodyOutcome(String source) {
            this.source = source;
        }
    }

    private enum CleanupOutcome {
        NORMAL("30"),
        RETURN_ESCAPES("^40"),
        ERROR_ESCAPES("cleanupError.signal()"),
        RETURN_HANDLED_LOCALLY("handledLocally()");

        final String source;

        CleanupOutcome(String source) {
            this.source = source;
        }
    }

    @Test
    void directUnwindEnsureCrossProductKeepsTheSurvivingTransfer() throws Exception {
        ProtosPrelude prelude = core();
        for (BodyOutcome body : BodyOutcome.values()) {
            for (CleanupOutcome cleanup : CleanupOutcome.values()) {
                assertEnsureCase(prelude, body, cleanup);
            }
        }

        System.out.println("PLAT070_ENSURE_CROSS_PRODUCT=PASS");
        System.out.println("PLAT070_LATER_ESCAPING_TRANSFER_SUPERSEDES=PASS");
        System.out.println("PLAT070_CLEANUP_HANDLED_TRANSFER_SUPERSEDES=NO");
    }

    private static void assertEnsureCase(
            ProtosPrelude prelude, BodyOutcome body, CleanupOutcome cleanup) {
        ProtosActivation module = prelude.newModuleActivation();
        ProtosObjectValue bodyError = ProtosCoreErrors.newError(module);
        ProtosObjectValue cleanupError = ProtosCoreErrors.newError(module);
        List<Long> marks = new ArrayList<>();
        module.context().createLocalSlot("bodyError", bodyError);
        module.context().createLocalSlot("cleanupError", cleanupError);
        module.context().createLocalSlot("mark", marker(marks));
        String name = "plat070-ensure-" + body + "-" + cleanup + ".protos";
        String program =
                ENSURE_MATRIX_TEMPLATE
                        .replace("BODY", body.source)
                        .replace("CLEANUP", cleanup.source);

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(name, program, module);

        String label = body + " x " + cleanup;
        switch (cleanup) {
            case RETURN_ESCAPES -> {
                // The later escaping return supersedes every original outcome.
                assertCompleted(outcome, label);
                assertEquals(40L, integer(outcome.value()), label);
                assertEquals(List.of(1L, 2L), marks, label);
            }
            case ERROR_ESCAPES -> {
                if (body == BodyOutcome.ERROR) {
                    // The handler selected for the body Error is consumed; the replacement escapes.
                    assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state(), label);
                    assertSame(cleanupError, outcome.error(), label);
                    assertEquals(List.of(1L, 2L), marks, label);
                } else {
                    assertCompleted(outcome, label);
                    assertSame(cleanupError, outcome.value(), label);
                    assertEquals(List.of(1L, 2L, 9L, 3L), marks, label);
                }
            }
            case NORMAL, RETURN_HANDLED_LOCALLY -> {
                // A cleanup that completes normally keeps the original pending outcome.
                assertCompleted(outcome, label);
                switch (body) {
                    case NORMAL -> {
                        assertEquals(10L, integer(outcome.value()), label);
                        assertEquals(List.of(1L, 2L, 3L), marks, label);
                    }
                    case RETURN -> {
                        assertEquals(20L, integer(outcome.value()), label);
                        assertEquals(List.of(1L, 2L), marks, label);
                    }
                    case ERROR -> {
                        assertSame(bodyError, outcome.value(), label);
                        assertEquals(List.of(1L, 2L, 9L, 3L), marks, label);
                    }
                }
            }
        }
    }

    @Test
    void cleanupSuspensionResumesWithoutReplayUnderEveryPendingOutcome() throws Exception {
        ProtosPrelude prelude = core();
        String template =
                """
                gate: (() => { 1 }).future()
                m: () => {
                    r: Error.handle(() => {
                        (() => {
                            mark(1)
                            BODY
                        }).ensure(() => {
                            mark(2)
                            gate.value()
                            mark(4)
                            CLEANUP
                        })
                    }, (caught) => {
                        mark(9)
                        caught
                    })
                    mark(3)
                    r
                }
                m()
                """;
        for (BodyOutcome body : BodyOutcome.values()) {
            for (CleanupOutcome cleanup :
                    List.of(CleanupOutcome.NORMAL, CleanupOutcome.RETURN_ESCAPES)) {
                ProtosActivation module = prelude.newModuleActivation();
                ProtosObjectValue bodyError = ProtosCoreErrors.newError(module);
                List<Long> marks = new ArrayList<>();
                module.context().createLocalSlot("bodyError", bodyError);
                module.context().createLocalSlot("mark", marker(marks));
                String label = "suspending cleanup " + body + " x " + cleanup;

                ProtosExecutionOutcome outcome =
                        ProtosTestExecutionSupport.execute(
                                "plat070-ensure-suspend-" + body + "-" + cleanup + ".protos",
                                template.replace("BODY", body.source)
                                        .replace("CLEANUP", cleanup.source),
                                module);

                assertCompleted(outcome, label);
                if (cleanup == CleanupOutcome.RETURN_ESCAPES) {
                    assertEquals(40L, integer(outcome.value()), label);
                    assertEquals(List.of(1L, 2L, 4L), marks, label);
                } else if (body == BodyOutcome.NORMAL) {
                    assertEquals(10L, integer(outcome.value()), label);
                    assertEquals(List.of(1L, 2L, 4L, 3L), marks, label);
                } else if (body == BodyOutcome.RETURN) {
                    assertEquals(20L, integer(outcome.value()), label);
                    assertEquals(List.of(1L, 2L, 4L), marks, label);
                } else {
                    assertSame(bodyError, outcome.value(), label);
                    assertEquals(List.of(1L, 2L, 4L, 9L, 3L), marks, label);
                }
            }
        }

        System.out.println("PLAT070_ENSURE_CLEANUP_SUSPENSION=PASS");
        System.out.println("PLAT070_ENSURE_CLEANUP_PREFIX_REPLAY=NO");
    }

    @Test
    void nestedEnsuresRunLifoOnceForADirectUnwindReturn() throws Exception {
        ProtosActivation module = core().newModuleActivation();
        List<Long> marks = new ArrayList<>();
        module.context().createLocalSlot("mark", marker(marks));

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "plat070-ensure-lifo.protos",
                        """
                        m: () => {
                            (() => {
                                (() => {
                                    (() => { ^7 }).ensure(() => { mark(1) })
                                }).ensure(() => { mark(2) })
                            }).ensure(() => { mark(3) })
                            0
                        }
                        m()
                        """,
                        module);

        assertCompleted(outcome, "nested ensure");
        assertEquals(7L, integer(outcome.value()));
        assertEquals(List.of(1L, 2L, 3L), marks);

        System.out.println("PLAT070_ENSURE_LIFO_EXACTLY_ONCE=PASS");
    }

    /**
     * I115: after each resumption the original caller frames have returned. The
     * resumed body and both nested cleanups then call a source helper whose
     * nested Closure and native call require the current activation; their
     * caller provenance was sealed at the suspension boundary, never recovered
     * from the resumer's stack. Every suspension uses a fresh pending Future.
     * Marks: 1 body, 5/6 helper, 2/4 inner cleanup, 7 outer cleanup, 9 handler, 3 after.
     */
    @Test
    void resumedBodyAndCleanupsCallSourceClosuresWithSealedCallers() throws Exception {
        ProtosPrelude prelude = core();
        String template =
                """
                pause: () => { (() => { 1 }).future().value() }
                helper: () => {
                    inner: () => { mark(5) }
                    inner()
                    Error.handle(() => { mark(6) }, (caught) => { caught })
                }
                m: () => {
                    r: Error.handle(() => {
                        (() => {
                            (() => {
                                mark(1)
                                pause()
                                helper()
                                BODY
                            }).ensure(() => {
                                mark(2)
                                pause()
                                helper()
                                mark(4)
                            })
                        }).ensure(() => {
                            pause()
                            helper()
                            mark(7)
                        })
                    }, (caught) => {
                        mark(9)
                        caught
                    })
                    mark(3)
                    r
                }
                m()
                """;
        List<Long> prefix = List.of(1L, 5L, 6L, 2L, 5L, 6L, 4L, 5L, 6L, 7L);
        for (BodyOutcome body : BodyOutcome.values()) {
            ProtosActivation module = prelude.newModuleActivation();
            ProtosObjectValue bodyError = ProtosCoreErrors.newError(module);
            List<Long> marks = new ArrayList<>();
            module.context().createLocalSlot("bodyError", bodyError);
            module.context().createLocalSlot("mark", marker(marks));
            String label = "resumed sealed callers " + body;

            ProtosExecutionOutcome outcome =
                    ProtosTestExecutionSupport.execute(
                            "i115-ensure-sealed-caller-" + body + ".protos",
                            template.replace("BODY", body.source),
                            module);

            assertCompleted(outcome, label);
            List<Long> expected = new ArrayList<>(prefix);
            switch (body) {
                case NORMAL -> {
                    assertEquals(10L, integer(outcome.value()), label);
                    expected.add(3L);
                }
                case RETURN -> assertEquals(20L, integer(outcome.value()), label);
                case ERROR -> {
                    assertSame(bodyError, outcome.value(), label);
                    expected.add(9L);
                    expected.add(3L);
                }
            }
            assertEquals(expected, marks, label);
        }

        System.out.println("I115_ENSURE_RESUMED_SEALED_CALLER=PASS");
        System.out.println("I115_ENSURE_RESUMED_STACK_RECOVERY=NO");
    }

    @Test
    void abandonmentClosesTheOwnerOfARealSuspendedFrame() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
            module.context().createLocalSlot("wait", wait);
            ProtosClosureValue source = sourceClosure(scope.language(), module,
                    "() => { unused: () => ^42\nwait.value() }", "i115-abandon-owner.protos");
            java.util.concurrent.atomic.AtomicReference<Object> pending =
                    new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<ProtosReturnHome> home =
                    new java.util.concurrent.atomic.AtomicReference<>();
            ProtosTask task = domain.createTask(null, current -> {
                ProtosActivation caller = activation(prelude, domain);
                caller.attachTask(current);
                var entry = ProtosDispatchTestSupport.direct(source, caller);
                home.set(ProtosFrameArguments.compactReturnHome(entry.targetArguments()));
                // Execute the real Future.value suspension and retain its owning frame.
                pending.set(ProtosDispatchTestSupport.enterRaw(entry));
                assertTrue(ProtosTailCallContinuation.isSuspended(pending.get()));
                assertTrue(current.suspend(
                        ProtosTailCallContinuation.nativeSuspensionLeaf(pending.get()).dependency()));
            });
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertTrue(home.get().isActive());
            ProtosTailCallContinuation.abandon(pending.get());
            org.junit.jupiter.api.Assertions.assertFalse(home.get().isActive());
            org.junit.jupiter.api.Assertions.assertNull(home.get().ownerTaskForRuntime());
            ProtosTailCallContinuation.abandon(pending.get());
        }
    }

    private static void assertCompleted(ProtosExecutionOutcome outcome, String label) {
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> label + ": error=" + outcome.error());
    }

    private static ProtosClosureValue marker(List<Long> marks) {
        com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody body =
                (activation, supplied) -> {
                    marks.add(integer(supplied.get(0)));
                    return ProtosNullValue.INSTANCE;
                };
        return ProtosClosureValue.suspensionCapableNativeClosure(body, body);
    }

    private static long integer(Object value) {
        return assertInstanceOf(Long.class, value);
    }

    private static ProtosClosureValue sourceClosureWithHome(
            ProtosLanguage language,
            ProtosActivation creator,
            String characters,
            String sourceName,
            ProtosReturnHome returnHome)
            throws Exception {
        CanonicalClosure definition = closureDefinition(characters);
        ProtosClosureExecutionPlan plan =
                ProtosClosureExecutionPlan.bytecode(
                        definition,
                        language,
                        source(characters, sourceName));
        return new ProtosClosureValue(
                definition,
                creator.lexicalContextsForClosureCapture(),
                creator.receiver(),
                creator.methodHome().orElse(null),
                returnHome,
                creator.prelude().orElseThrow(),
                plan);
    }

    private static ProtosActivation invocation(
            ProtosClosureValue closure,
            ProtosPrelude prelude,
            ProtosActivation creator,
            ProtosActorExecutionDomain domain) {
        return ProtosActivation.forClosureInvocation(
                closure,
                List.of(),
                prelude,
                creator.actorModuleState(),
                creator.currentModuleKey().orElse(null),
                domain);
    }

    private static ProtosTask execute(
            ProtosActorExecutionDomain domain,
            ProtosActivation activation,
            ProtosBytecodeRootNode root) {
        return domain.createTask(
                null,
                current ->
                        ProtosBytecodeTaskExecution.execute(
                                current,
                                root.getCallTarget(),
                                activation));
    }

    private static LanguageScope languageScope() {
        Context context = Context.newBuilder(ProtosLanguage.ID).build();
        context.initialize(ProtosLanguage.ID);
        context.enter();
        return new LanguageScope(context, LANGUAGE_REF.get(null));
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    private static ProtosActivation activation(
            ProtosPrelude prelude,
            ProtosActorExecutionDomain domain) {
        return prelude.newModuleActivation(
                new ProtosActorModuleState(),
                null,
                prelude.newExecutionContext(),
                domain);
    }

    private static CanonicalClosure closureDefinition(String characters) {
        CanonicalSequence sequence = canonicalize(characters);
        assertEquals(1, sequence.expressions().size());
        return assertInstanceOf(
                CanonicalClosure.class,
                sequence.expressions().get(0));
    }

    private static CanonicalSequence canonicalize(String characters) {
        return (CanonicalSequence)
                new Canonicalizer()
                        .canonicalize(new ProtosParser(characters).parseProgram());
    }

    private static Source source(String characters, String name) throws Exception {
        return Source.newBuilder(ProtosLanguage.ID, characters, name).build();
    }

    private static ProtosBytecodeRootNode lowerRoot(
            ProtosLanguage language,
            String characters,
            String sourceName)
            throws Exception {
        Source source = source(characters, sourceName);
        return new CanonicalToBytecodeLowerer(language, source)
                .lowerRoot(canonicalize(characters));
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

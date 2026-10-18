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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.InvokeClosureZero;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

/**
 * I112 / PLAT062-T adversarial executable evidence for terminal Closure
 * invocation and its suspension transport.
 *
 * <p>Every suspension below is real: a Task-owned C-prime chain reaches a
 * pending {@code Future.value()} leaf, the Task becomes {@code SUSPENDED}, and
 * only a later dispatch resumes it. Exactly-once completion is observed
 * through effects (probes before and after the suspension point), {@code
 * ensure} cleanup counts, ReturnHome outcomes and Task terminal states, never
 * through the final value alone.
 */
final class ProtosI112TailContinuationTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    private static final String IDENTITY = "identity: (value) => { value }\nidentity(1)";

    // ---- 1. Direct identity(1) ---------------------------------------------------------

    @Test
    void identityTerminalCallReturnsTheExactGuestValueWithoutSuspension() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosBytecodeRootNode root =
                    lowerRoot(scope.language(), IDENTITY, "i112-identity.protos");
            assertTerminal(root, "c.InvokeClosureOneTail");

            for (int run = 0; run < 3; run++) {
                // Each run creates the binding afresh in its own module activation.
                assertInteger(1, root.getCallTarget().call(activation(prelude, domain)));
            }
        }
    }

    // ---- 2. Site transitions generic -> direct -> generic -> direct --------------------

    @Test
    void oneSiteTransitionsBetweenGenericAndDirectWithoutLosingSemantics() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosBytecodeRootNode root =
                    lowerRoot(scope.language(), "target(1)", "i112-transitions.protos");
            assertTerminal(root, "c.InvokeClosureOneTail");
            root.getBytecodeNode().setUncachedThreshold(0);

            ProtosActivation creator = activation(prelude, domain);
            ProtosClosureValue direct =
                    sourceClosureWithHome(
                            scope.language(),
                            creator,
                            "(value) => { value + 1 }",
                            "i112-transitions-direct.protos",
                            ProtosReturnHome.unobservable());
            ProtosClosureValue generic =
                    nativeClosure((activation, supplied) -> new ProtosIntegerValue(100));

            List<ProtosClosureValue> sequence = List.of(generic, direct, generic, direct, direct);
            List<Long> expected = List.of(100L, 2L, 100L, 2L, 2L);
            for (int index = 0; index < sequence.size(); index++) {
                ProtosActivation module = activation(prelude, domain);
                module.context().createLocalSlot("target", sequence.get(index));
                assertInteger(expected.get(index), root.getCallTarget().call(module));
            }
        }
    }

    // ---- 3. Override of call after creation ---------------------------------------------

    @Test
    void callOverrideInstalledAfterCreationIsSelected() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosBytecodeRootNode root =
                    lowerRoot(scope.language(), "target(1)", "i112-override.protos");
            root.getBytecodeNode().setUncachedThreshold(0);
            ProtosClosureValue target =
                    sourceClosureWithHome(
                            scope.language(),
                            activation(prelude, domain),
                            "(value) => { value }",
                            "i112-override-target.protos",
                            ProtosReturnHome.unobservable());

            for (int run = 0; run < 3; run++) {
                ProtosActivation module = activation(prelude, domain);
                module.context().createLocalSlot("target", target);
                assertInteger(1, root.getCallTarget().call(module));
            }

            target.createLocalSlot(
                    "call", nativeClosure((activation, supplied) -> new ProtosIntegerValue(77)));
            ProtosActivation module = activation(prelude, domain);
            module.context().createLocalSlot("target", target);
            assertInteger(77, root.getCallTarget().call(module));
        }
    }

    // ---- 4. Selection after argument evaluation -----------------------------------------

    @Test
    void callSelectionObservesAnOverrideInstalledByArgumentEvaluation() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            AtomicInteger argumentEvaluations = new AtomicInteger();
            ProtosClosureValue target =
                    sourceClosureWithHome(
                            scope.language(),
                            module,
                            "(value) => { value }",
                            "i112-selection-target.protos",
                            ProtosReturnHome.unobservable());
            module.context().createLocalSlot("target", target);
            module.context().createLocalSlot(
                    "mutate",
                    nativeClosure(
                            (activation, supplied) -> {
                                argumentEvaluations.incrementAndGet();
                                target.createLocalSlot(
                                        "call",
                                        nativeClosure(
                                                (inner, values) -> new ProtosIntegerValue(55)));
                                return new ProtosIntegerValue(1);
                            }));

            // A composed argument is not a terminal operand: the established
            // composition keeps D013 post-argument selection.
            ProtosBytecodeRootNode root =
                    lowerRoot(scope.language(), "target(mutate())", "i112-selection.protos");
            assertFalse(signature(root).stream().anyMatch(entry -> entry.contains("Tail")));
            assertInteger(55, root.getCallTarget().call(module));
            assertEquals(1, argumentEvaluations.get(), "the argument is evaluated once");

            // The terminal operation selects after both of its operands.
            ProtosActivation second = activation(prelude, domain);
            ProtosClosureValue fresh =
                    sourceClosureWithHome(
                            scope.language(),
                            second,
                            "(value) => { value }",
                            "i112-selection-fresh.protos",
                            ProtosReturnHome.unobservable());
            second.context().createLocalSlot("target", fresh);
            second.context().createLocalSlot("argument", new ProtosIntegerValue(3));
            ProtosBytecodeRootNode terminal =
                    lowerRoot(scope.language(), "target(argument)", "i112-selection-terminal.protos");
            assertTerminal(terminal, "c.InvokeClosureOneTail");
            assertInteger(3, terminal.getCallTarget().call(second));
            fresh.createLocalSlot(
                    "call", nativeClosure((activation, supplied) -> new ProtosIntegerValue(56)));
            assertInteger(56, terminal.getCallTarget().call(second));
        }
    }

    // ---- 5. PIC overflow and invalidation -----------------------------------------------

    @Test
    void polymorphicSiteOverflowAndInvalidationKeepExactResults() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosBytecodeRootNode root =
                    lowerRoot(scope.language(), "target(1)", "i112-pic.protos");
            root.getBytecodeNode().setUncachedThreshold(0);
            List<ProtosClosureValue> targets = new ArrayList<>();
            ProtosActivation creator = activation(prelude, domain);
            for (int index = 0; index < 5; index++) {
                targets.add(
                        sourceClosureWithHome(
                                scope.language(),
                                creator,
                                "(value) => { value + " + index + " }",
                                "i112-pic-" + index + ".protos",
                                ProtosReturnHome.unobservable()));
            }
            for (int round = 0; round < 2; round++) {
                for (int index = 0; index < targets.size(); index++) {
                    ProtosActivation module = activation(prelude, domain);
                    module.context().createLocalSlot("target", targets.get(index));
                    assertInteger(1 + index, root.getCallTarget().call(module));
                }
            }
            targets.get(0).createLocalSlot(
                    "call", nativeClosure((activation, supplied) -> new ProtosIntegerValue(-1)));
            ProtosActivation module = activation(prelude, domain);
            module.context().createLocalSlot("target", targets.get(0));
            assertInteger(-1, root.getCallTarget().call(module));
        }
    }

    // ---- 6, 11, 16. Suspendible source Closure in terminal position ---------------------

    @Test
    void suspendibleSourceClosureInTerminalPositionResumesItsOwnState() throws Exception {
        for (boolean directEntry : List.of(false, true)) {
            try (LanguageScope scope = languageScope()) {
                ProtosPrelude prelude = core();
                ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
                ProtosActivation module = activation(prelude, domain);
                ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
                ProtosObjectValue resolved = new ProtosObjectValue(ProtosObjectValue.rootObject());
                AtomicInteger before = new AtomicInteger();
                AtomicInteger after = new AtomicInteger();
                module.context().createLocalSlot("wait", wait);
                module.context().createLocalSlot("before", counter(before));
                module.context().createLocalSlot("after", counter(after));
                String body = "() => { before()\nresult: wait.value()\nafter()\nresult }";
                module.context().createLocalSlot(
                        "waiter",
                        directEntry
                                ? sourceClosureWithHome(
                                        scope.language(), module, body,
                                        "i112-waiter-direct.protos",
                                        ProtosReturnHome.unobservable())
                                : sourceClosure(
                                        scope.language(), module, body,
                                        "i112-waiter.protos"));

                ProtosBytecodeRootNode root =
                        lowerRoot(scope.language(), "waiter()", "i112-waiter-top.protos");
                assertTerminal(root, "c.InvokeClosureZeroTail");
                if (directEntry) {
                    root.getBytecodeNode().setUncachedThreshold(0);
                }

                ProtosTask task = execute(domain, module, root);
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state(), "really suspended");
                assertEquals(1, before.get());
                assertEquals(0, after.get());

                assertTrue(wait.resolve(resolved, module));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state());
                assertSame(resolved, task.result().orElseThrow(), "exact resumed result");
                assertEquals(1, before.get(), "the suspended prefix never replays");
                assertEquals(1, after.get(), "the suffix runs exactly once");
            }
        }
    }

    // ---- 7. A suspending call override --------------------------------------------------

    @Test
    void suspendingCallOverrideInTerminalPosition() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
            ProtosObjectValue resolved = new ProtosObjectValue(ProtosObjectValue.rootObject());
            AtomicInteger overrideRuns = new AtomicInteger();
            module.context().createLocalSlot("wait", wait);
            module.context().createLocalSlot("probe", counter(overrideRuns));
            ProtosClosureValue target =
                    sourceClosure(scope.language(), module, "() => { 0 }", "i112-ov-target.protos");
            target.createLocalSlot(
                    "call",
                    sourceClosure(
                            scope.language(),
                            module,
                            "() => { probe()\nwait.value() }",
                            "i112-ov-call.protos"));
            module.context().createLocalSlot("target", target);

            ProtosTask task =
                    execute(domain, module,
                            lowerRoot(scope.language(), "target()", "i112-ov-top.protos"));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertTrue(wait.resolve(resolved, module));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertSame(resolved, task.result().orElseThrow());
            assertEquals(1, overrideRuns.get());
        }
    }

    // ---- 8. Nested suspensions: native leaf, structured ensure, Boolean callback --------

    @Test
    void terminalNativeLeafSuspensionIsRetainedAndResumed() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
            ProtosObjectValue resolved = new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosClosureValue value =
                    assertInstanceOf(
                            ProtosClosureValue.class,
                            ProtosValueLookup.readMember(wait, "value", prelude).orElseThrow());
            module.context().createLocalSlot("waitValue", value);

            ProtosTask task =
                    execute(domain, module,
                            lowerRoot(scope.language(), "waitValue()", "i112-native-top.protos"));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertTrue(wait.resolve(resolved, module));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertSame(resolved, task.result().orElseThrow());
        }
    }

    @Test
    void terminalStructuredEnsureKeepsItsDispatcherOwnershipAcrossSuspension() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
            ProtosObjectValue resolved = new ProtosObjectValue(ProtosObjectValue.rootObject());
            AtomicInteger before = new AtomicInteger();
            AtomicInteger cleanups = new AtomicInteger();
            module.context().createLocalSlot("wait", wait);
            module.context().createLocalSlot("before", counter(before));
            module.context().createLocalSlot("cleanup", counter(cleanups));
            ProtosClosureValue body =
                    sourceClosure(
                            scope.language(), module,
                            "() => { before()\nwait.value() }", "i112-ensure-body.protos");
            module.context().createLocalSlot(
                    "ensurer",
                    assertInstanceOf(
                            ProtosClosureValue.class,
                            ProtosValueLookup.readMember(body, "ensure", prelude).orElseThrow()));

            ProtosBytecodeRootNode root =
                    lowerRoot(scope.language(), "ensurer(cleanup)", "i112-ensure-top.protos");
            assertTerminal(root, "c.InvokeClosureOneTail");
            ProtosTask task = execute(domain, module, root);
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertEquals(0, cleanups.get(), "suspension is not an ensure scope exit");

            assertTrue(wait.resolve(resolved, module));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertSame(resolved, task.result().orElseThrow());
            assertEquals(1, before.get());
            assertEquals(1, cleanups.get(), "cleanup runs exactly once");
        }
    }

    @Test
    void terminalCallInsideABooleanCallbackSuspendsThroughTheStructuredRoot() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
            AtomicInteger after = new AtomicInteger();
            module.context().createLocalSlot("wait", wait);
            module.context().createLocalSlot("after", counter(after));
            module.context().createLocalSlot("flag", ProtosBooleanValue.TRUE);
            module.context().createLocalSlot(
                    "waiter",
                    sourceClosure(scope.language(), module,
                            "() => { wait.value() }", "i112-bool-waiter.protos"));
            ProtosClosureValue callback =
                    sourceClosure(scope.language(), module,
                            "() => { waiter() }", "i112-bool-callback.protos");
            module.context().createLocalSlot("callback", callback);

            ProtosTask task =
                    execute(domain, module,
                            lowerRoot(scope.language(),
                                    "flag.ifTrue(callback)\nafter()",
                                    "i112-bool-top.protos"));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertEquals(0, after.get());
            assertTrue(wait.resolve(ProtosNullValue.INSTANCE, module));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertEquals(1, after.get());
        }
    }

    // ---- 9, 10. Terminal inside terminal; terminal inside a continuing caller -----------

    @Test
    void nestedTerminalTransportsCompleteInnermostFirst() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
            ProtosObjectValue resolved = new ProtosObjectValue(ProtosObjectValue.rootObject());
            List<String> order = new ArrayList<>();
            module.context().createLocalSlot("wait", wait);
            module.context().createLocalSlot(
                    "innerCleanup", recorder(order, "inner"));
            module.context().createLocalSlot(
                    "outerCleanup", recorder(order, "outer"));
            ProtosClosureValue innerBody =
                    sourceClosure(scope.language(), module,
                            "() => { wait.value() }", "i112-nested-inner-body.protos");
            module.context().createLocalSlot(
                    "innerEnsure",
                    ProtosValueLookup.readMember(innerBody, "ensure", prelude).orElseThrow());
            module.context().createLocalSlot(
                    "inner",
                    sourceClosure(scope.language(), module,
                            "() => { innerEnsure(innerCleanup) }", "i112-nested-inner.protos"));
            ProtosClosureValue outerBody =
                    sourceClosure(scope.language(), module,
                            "() => { inner() }", "i112-nested-outer-body.protos");
            module.context().createLocalSlot(
                    "outerEnsure",
                    ProtosValueLookup.readMember(outerBody, "ensure", prelude).orElseThrow());
            module.context().createLocalSlot(
                    "outer",
                    sourceClosure(scope.language(), module,
                            "() => { outerEnsure(outerCleanup) }", "i112-nested-outer.protos"));

            ProtosTask task =
                    execute(domain, module,
                            lowerRoot(scope.language(), "outer()", "i112-nested-top.protos"));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertEquals(List.of(), order);

            assertTrue(wait.resolve(resolved, module));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertSame(resolved, task.result().orElseThrow());
            assertEquals(List.of("inner", "outer"), order, "innermost completion first, once each");
        }
    }

    @Test
    void terminalSuspensionInsideACallerThatStillRunsCode() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
            ProtosObjectValue resolved = new ProtosObjectValue(ProtosObjectValue.rootObject());
            AtomicInteger after = new AtomicInteger();
            module.context().createLocalSlot("wait", wait);
            module.context().createLocalSlot("after", counter(after));
            module.context().createLocalSlot(
                    "waiter",
                    sourceClosure(scope.language(), module,
                            "() => { wait.value() }", "i112-continuing-waiter.protos"));
            module.context().createLocalSlot(
                    "terminal",
                    sourceClosure(scope.language(), module,
                            "() => { waiter() }", "i112-continuing-terminal.protos"));
            module.context().createLocalSlot(
                    "caller",
                    sourceClosure(scope.language(), module,
                            "() => { x: terminal()\nafter()\nx }", "i112-continuing-caller.protos"));

            ProtosTask task =
                    execute(domain, module,
                            lowerRoot(scope.language(), "caller()", "i112-continuing-top.protos"));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertEquals(0, after.get());
            assertTrue(wait.resolve(resolved, module));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.COMPLETED, task.state());
            assertSame(resolved, task.result().orElseThrow());
            assertEquals(1, after.get(), "the caller's remaining work runs once after resumption");
        }
    }

    // ---- 12. Error raised during resumption ---------------------------------------------

    @Test
    void errorRaisedDuringResumptionFailsTheTaskWithItsExactIdentity() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
            ProtosObjectValue error = ProtosCoreErrors.newError(module);
            AtomicInteger after = new AtomicInteger();
            AtomicInteger cleanups = new AtomicInteger();
            module.context().createLocalSlot("wait", wait);
            module.context().createLocalSlot("after", counter(after));
            module.context().createLocalSlot("cleanup", counter(cleanups));
            ProtosClosureValue body =
                    sourceClosure(scope.language(), module,
                            "() => { wait.value()\nafter() }", "i112-error-body.protos");
            module.context().createLocalSlot(
                    "ensurer", ProtosValueLookup.readMember(body, "ensure", prelude).orElseThrow());

            ProtosTask task =
                    execute(domain, module,
                            lowerRoot(scope.language(), "ensurer(cleanup)", "i112-error-top.protos"));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertTrue(wait.fail(error));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.FAILED, task.state());
            assertSame(error, task.failure().orElseThrow());
            assertEquals(0, after.get());
            assertEquals(1, cleanups.get(), "cleanup runs once while the Error unwinds");
        }
    }

    // ---- 13. Non-local ReturnHome and InvalidReturn ------------------------------------

    @Test
    void nonLocalReturnCrossesATerminalCallWithItsExactTargetAndValue() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosObjectValue payload = new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosReturnHome externalHome = new ProtosReturnHome();
            module.context().createLocalSlot("payload", payload);
            module.context().createLocalSlot(
                    "body",
                    sourceClosureWithHome(scope.language(), module,
                            "() => ^payload", "i112-nlr-body.protos", externalHome));
            ProtosClosureValue runner =
                    sourceClosure(scope.language(), module, "() => { body() }",
                            "i112-nlr-runner.protos");
            ProtosActivation invocation =
                    ProtosActivation.forClosureInvocation(
                            runner, List.of(), prelude, module.actorModuleState(),
                            module.currentModuleKey().orElse(null), domain);

            ProtosNonLocalReturnException transfer =
                    assertThrows(
                            ProtosNonLocalReturnException.class,
                            () -> runner.executionPlanForRuntimeInvocation().executeBody(invocation));
            assertSame(externalHome, transfer.target());
            assertSame(payload, transfer.value());
            assertTrue(externalHome.isActive(), "a foreign home is not completed by the transfer");
        }
    }

    @Test
    void nonLocalReturnAfterResumptionToAForeignHomeSignalsInvalidReturn() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
            ProtosObjectValue payload = new ProtosObjectValue(ProtosObjectValue.rootObject());
            module.context().createLocalSlot("wait", wait);
            module.context().createLocalSlot("payload", payload);
            module.context().createLocalSlot(
                    "body",
                    sourceClosureWithHome(scope.language(), module,
                            "() => { wait.value()\n^payload }", "i112-invalid-body.protos",
                            new ProtosReturnHome()));

            ProtosTask task =
                    execute(domain, module,
                            lowerRoot(scope.language(), "body()", "i112-invalid-top.protos"));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, task.state());
            assertTrue(wait.resolve(ProtosNullValue.INSTANCE, module));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.FAILED, task.state());
            assertSame(
                    prelude.invalidReturnPrototype(),
                    assertInstanceOf(ProtosObjectValue.class, task.failure().orElseThrow())
                            .parent()
                            .orElseThrow(),
                    "D177: the unreachable home is an InvalidReturn Task failure");
        }
    }

    // ---- 14, 15. Cancellation before and after publication; ensure during unwinding -----

    @Test
    void cancellationAfterPublicationUnwindsTheTerminalTransportOnce() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
            AtomicInteger after = new AtomicInteger();
            AtomicInteger cleanups = new AtomicInteger();
            module.context().createLocalSlot("wait", wait);
            module.context().createLocalSlot("after", counter(after));
            module.context().createLocalSlot("cleanup", counter(cleanups));
            ProtosClosureValue body =
                    sourceClosure(scope.language(), module,
                            "() => { wait.value()\nafter() }", "i112-cancel-body.protos");
            module.context().createLocalSlot(
                    "ensurer", ProtosValueLookup.readMember(body, "ensure", prelude).orElseThrow());
            module.context().createLocalSlot(
                    "guarded",
                    sourceClosure(scope.language(), module,
                            "() => { ensurer(cleanup) }", "i112-cancel-guarded.protos"));

            Execution execution =
                    executeWithFuture(domain, prelude, module,
                            lowerRoot(scope.language(), "guarded()", "i112-cancel-top.protos"));
            assertTrue(domain.dispatchOne());
            assertEquals(ProtosTask.State.SUSPENDED, execution.task().state());

            assertTrue(execution.future().cancelRequest());
            drainUntil(domain, execution.task(), ProtosTask.State.CANCELLED);
            assertEquals(ProtosFutureValue.State.CANCELLED, execution.future().state());
            assertEquals(0, after.get());
            assertEquals(1, cleanups.get(), "cleanup runs exactly once during cancellation");
        }
    }

    @Test
    void cancellationBeforePublicationIsObservedWithoutResumingTheSuffix() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosActivation module = activation(prelude, domain);
            ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
            AtomicInteger after = new AtomicInteger();
            module.context().createLocalSlot("wait", wait);
            module.context().createLocalSlot("after", counter(after));
            module.context().createLocalSlot(
                    "requestCancel",
                    nativeClosure(
                            (activation, supplied) -> {
                                assertTrue(activation.task().orElseThrow().requestCancellation());
                                return ProtosNullValue.INSTANCE;
                            }));
            module.context().createLocalSlot(
                    "waiter",
                    sourceClosure(scope.language(), module,
                            "() => { requestCancel()\nwait.value()\nafter() }",
                            "i112-precancel-waiter.protos"));

            Execution execution =
                    executeWithFuture(domain, prelude, module,
                            lowerRoot(scope.language(), "waiter()", "i112-precancel-top.protos"));
            drainUntil(domain, execution.task(), ProtosTask.State.CANCELLED);
            assertEquals(0, after.get());
        }
    }

    // ---- 17. Context ownership and projection -------------------------------------------

    @Test
    void eachContextOwnsItsRootsAndTerminalCaches() throws Exception {
        List<ProtosBytecodeRootNode> roots = new ArrayList<>();
        for (int round = 0; round < 2; round++) {
            try (Harness harness = new Harness(ProtosExecutionMode.NORMAL)) {
                harness.run(() -> {
                    ProtosBytecodeRootNode module = compile(IDENTITY, "contexts");
                    assertInteger(1, module.getCallTarget().call(harness.moduleActivation()));
                    assertInteger(1, module.getCallTarget().call(harness.moduleActivation()));
                    roots.add(module);
                });
            }
        }
        assertNotSame(roots.get(0), roots.get(1));
    }

    /**
     * Cross-Context terminal invocation within the infrastructure's limits: a
     * CallTarget is never shared across Contexts (the polyglot sharing layer
     * rejects it), so each Context lowers the same terminal site source, and
     * the second Context invokes a Closure carried over from the first one,
     * rebound and marked for Context-local projection exactly as the existing
     * foreign-template contract requires. The terminal site of the second
     * Context must not reuse the first Context's target or authority.
     */
    @Test
    void oneTerminalSiteAcrossContextsNeverReusesAnotherContextsTarget() throws Exception {
        try (Engine engine = Engine.create(ProtosLanguage.ID);
                ProtosPolyglotExecutionContext first = openContext(engine);
                ProtosPolyglotExecutionContext second = openContext(engine)) {
            ProtosBytecodeRootNode[] site = new ProtosBytecodeRootNode[1];
            ProtosClosureValue[] carried = new ProtosClosureValue[1];
            RootCallTarget[] firstTarget = new RootCallTarget[1];

            first.callEntered(
                    () -> {
                        ProtosPrelude prelude = uncheckedCore();
                        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
                        ProtosActivation creator = activation(prelude, domain);
                        ProtosClosureValue closure = uncheckedSourceClosure(
                                LANGUAGE_REF.get(null), creator, "(value) => { value + 1 }",
                                "i112-cross-context-target.protos");
                        site[0] = uncheckedLowerRoot(
                                LANGUAGE_REF.get(null), "target(1)", "i112-cross-context-site.protos");
                        assertTerminal(site[0], "c.InvokeClosureOneTail");
                        site[0].getBytecodeNode().setUncachedThreshold(0);
                        for (int run = 0; run < 3; run++) {
                            ProtosActivation module = activation(prelude, domain);
                            module.context().createLocalSlot("target", closure);
                            assertInteger(2, site[0].getCallTarget().call(module));
                        }
                        firstTarget[0] = InvokeClosureZero.definitionTarget(
                                closure, creator, ProtosLanguageContext.current());
                        carried[0] = closure;
                        return null;
                    });

            second.callEntered(
                    () -> {
                        ProtosPrelude prelude = uncheckedCore();
                        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
                        ProtosActivation module = activation(prelude, domain);
                        ProtosClosureValue source = carried[0];
                        ProtosClosureValue rebound = new ProtosClosureValue(
                                source.definition(),
                                module.lexicalContextsForClosureCapture(),
                                module.receiver(),
                                module.methodHome().orElse(null),
                                ProtosReturnHome.unobservable(),
                                prelude,
                                source.executionPlan().orElseThrow());
                        rebound.requireContextLocalExecutionProjectionForRuntime();
                        module.context().createLocalSlot("target", rebound);

                        ProtosBytecodeRootNode secondSite = uncheckedLowerRoot(
                                LANGUAGE_REF.get(null), "target(1)", "i112-cross-context-site.protos");
                        assertTerminal(secondSite, "c.InvokeClosureOneTail");
                        assertNotSame(site[0], secondSite);
                        secondSite.getBytecodeNode().setUncachedThreshold(0);
                        assertInteger(2, secondSite.getCallTarget().call(module));
                        assertEquals(
                                1,
                                ProtosLanguageContext.current()
                                        .projectedBytecodeExecutionPlanCountForTesting(),
                                "the site projects one Context-local plan instead of reusing a target");
                        RootCallTarget secondTarget = InvokeClosureZero.definitionTarget(
                                rebound, module, ProtosLanguageContext.current());
                        if (secondTarget != null) {
                            assertNotSame(firstTarget[0], secondTarget,
                                    "a Context-local target is never the other Context's target");
                        }
                        assertInteger(2, secondSite.getCallTarget().call(module));
                        return null;
                    });

            first.callEntered(
                    () -> {
                        ProtosPrelude prelude = uncheckedCore();
                        ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
                        ProtosActivation module = activation(prelude, domain);
                        module.context().createLocalSlot("target", carried[0]);
                        assertInteger(2, site[0].getCallTarget().call(module));
                        return null;
                    });
        }
    }

    // ---- Module authority installed by EnterRoot -------------------------------------------

    /**
     * The admitted module installs its persistent frame authority from the
     * prologue: its bindings are visible to Context observation and to a
     * later lexical access from a nested Closure, without an
     * InstallFrameLexicalAuthority body operation.
     */
    @Test
    void prologueInstalledModuleAuthorityKeepsBindingsVisible() throws Exception {
        try (Harness harness = new Harness(ProtosExecutionMode.NORMAL)) {
            harness.run(() -> {
                ProtosBytecodeRootNode root = compile(
                        "identity: (value) => { value }\nlater: () => { identity }\nobserve(later)",
                        "prolog-authority");
                List<String> names = signature(root).stream()
                        .map(entry -> entry.split(" ", 2)[0]).toList();
                assertFalse(names.contains("c.InstallFrameLexicalAuthority"), names::toString);
                assertTrue(names.contains("c.CreateCurrentFrameLocal"), names::toString);

                ProtosActivation module = harness.moduleActivation();
                Object[] observed = new Object[2];
                module.context().createLocalSlot(
                        "observe",
                        nativeClosure(
                                (activation, supplied) -> {
                                    observed[0] = module.context()
                                            .readLocalSlot("identity").orElseThrow();
                                    observed[1] = ProtosClosureInvoker.invoke(
                                            (ProtosClosureValue) supplied.get(0),
                                            List.of(),
                                            module);
                                    return new ProtosIntegerValue(5);
                                }));
                assertInteger(5, root.getCallTarget().call(module));
                assertInstanceOf(ProtosClosureValue.class, observed[0]);
                assertSame(observed[0], observed[1], "later lexical access sees the same binding");
            });
        }
    }

    /** D179: the compact module read never ignores a removal or a recreation. */
    @Test
    void prologueInstalledModuleReadObservesRemovalAndRecreation() throws Exception {
        try (Harness harness = new Harness(ProtosExecutionMode.NORMAL)) {
            harness.run(() -> {
                ProtosBytecodeRootNode root = compile(
                        "identity: (value) => { value }\nchange()\nidentity(1)", "d179");
                assertFalse(signature(root).stream()
                        .anyMatch(entry -> entry.startsWith("c.InstallFrameLexicalAuthority")));

                ProtosActivation removed = harness.moduleActivation();
                removed.context().createLocalSlot(
                        "change",
                        nativeClosure(
                                (activation, supplied) -> {
                                    removed.context().removeLocalSlot("identity");
                                    return ProtosNullValue.INSTANCE;
                                }));
                assertThrows(
                        ProtosSignalException.class,
                        () -> root.getCallTarget().call(removed),
                        "a removed binding is ABSENT for the compact read");

                ProtosActivation recreated = harness.moduleActivation();
                recreated.context().createLocalSlot(
                        "change",
                        nativeClosure(
                                (activation, supplied) -> {
                                    recreated.context().removeLocalSlot("identity");
                                    recreated.context().createLocalSlot(
                                            "identity",
                                            nativeClosure(
                                                    (inner, values) -> new ProtosIntegerValue(99)));
                                    return ProtosNullValue.INSTANCE;
                                }));
                assertInteger(99, root.getCallTarget().call(recreated));
            });
        }
    }

    /** Modules outside the conservative admission keep the original body installation. */
    @Test
    void nonAdmittedModulesKeepTheOriginalPersistentAuthority() throws Exception {
        try (Harness harness = new Harness(ProtosExecutionMode.NORMAL)) {
            harness.run(() -> {
                for (String program : List.of(
                        "a: 1\na",
                        "identity: (value) => { value }\nresult: identity(1)\nresult",
                        "identity: (value) => { value }\nidentity(identity(1))")) {
                    ProtosBytecodeRootNode root = compile(program, "not-admitted");
                    assertTrue(
                            signature(root).stream().anyMatch(
                                    entry -> entry.startsWith("c.InstallFrameLexicalAuthority")),
                            program);
                    assertInteger(1, root.getCallTarget().call(harness.moduleActivation()));
                }
            });
        }
    }

    // ---- 18. Reparse stability --------------------------------------------------------------

    @Test
    void reparseKeepsTheSamePhysicalDecisionsWithoutDuplicatingRoots() throws Exception {
        try (Harness harness = new Harness(ProtosExecutionMode.NORMAL)) {
            harness.run(() -> {
                ProtosBytecodeRootNode module = compile(IDENTITY, "reparse");
                // The first completion adds instrumentation; a second one adds nothing.
                module.getRootNodes().ensureComplete();
                List<List<String>> before = new ArrayList<>();
                for (ProtosBytecodeRootNode root : groupRoots(module)) {
                    before.add(signature(root));
                }
                RootCallTarget target = module.getCallTarget();
                module.getRootNodes().ensureComplete();
                List<ProtosBytecodeRootNode> after = groupRoots(module);
                assertEquals(2, after.size(), "no root is duplicated by a reparse");
                for (int index = 0; index < after.size(); index++) {
                    assertEquals(before.get(index), signature(after.get(index)));
                }
                assertSame(target, module.getCallTarget());
                assertInteger(1, module.getCallTarget().call(harness.moduleActivation()));
            });
        }
    }

    // ---- 19. NORMAL versus DEBUG: tags, spans and scopes ------------------------------------

    @Test
    void normalAndDebugKeepEquivalentSemanticsTagsAndSpans() throws Exception {
        for (ProtosExecutionMode mode : List.of(ProtosExecutionMode.NORMAL, ProtosExecutionMode.DEBUG)) {
            try (Harness harness = new Harness(mode)) {
                harness.run(() -> {
                    ProtosBytecodeRootNode module = compile(IDENTITY, "tags-" + mode);
                    module.getRootNodes().ensureComplete();
                    List<String> instructions = signature(module);
                    boolean terminal = instructions.stream()
                            .anyMatch(entry -> entry.startsWith("c.InvokeClosureOneTail"));
                    assertEquals(mode == ProtosExecutionMode.NORMAL, terminal, instructions::toString);

                    TagTree tree = module.getBytecodeNode().getTagTree();
                    assertNotNull(tree);
                    assertTrue(
                            hasTagOver(tree, StandardTags.CallTag.class, "identity(1)"),
                            () -> mode + ": CallTag over the exact call span");
                    assertTrue(
                            hasTagOver(tree, StandardTags.StatementTag.class, "identity(1)"),
                            () -> mode + ": StatementTag over the terminal statement");

                    if (terminal) {
                        Instruction call = instructions(module).stream()
                                .filter(instruction -> instruction.getName()
                                        .equals("c.InvokeClosureOneTail"))
                                .findFirst()
                                .orElseThrow();
                        SourceSection section = call.getSourceSection();
                        assertNotNull(section);
                        assertEquals("identity(1)", section.getCharacters().toString());
                    }
                    assertInteger(1, module.getCallTarget().call(harness.moduleActivation()));
                });
            }
        }
    }

    // ---- 20. Uncached execution and cache transitions ---------------------------------------

    @Test
    void uncachedExecutionAndCacheTransitionKeepSemantics() throws Exception {
        try (LanguageScope scope = languageScope()) {
            ProtosPrelude prelude = core();
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosBytecodeRootNode root =
                    lowerRoot(scope.language(), "target(1)", "i112-uncached.protos");
            root.getBytecodeNode().setUncachedThreshold(4);
            ProtosActivation creator = activation(prelude, domain);
            ProtosClosureValue source =
                    sourceClosureWithHome(scope.language(), creator, "(value) => { value + 1 }",
                            "i112-uncached-source.protos", ProtosReturnHome.unobservable());
            ProtosClosureValue nativeTarget =
                    nativeClosure((activation, supplied) -> new ProtosIntegerValue(9));
            for (int run = 0; run < 12; run++) {
                boolean useSource = run % 3 != 2;
                ProtosActivation module = activation(prelude, domain);
                module.context().createLocalSlot("target", useSource ? source : nativeTarget);
                assertInteger(useSource ? 2 : 9, root.getCallTarget().call(module));
            }
            System.out.println("I112_UNCACHED_FINAL_TIER=" + root.getBytecodeNode().getTier());
        }
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private static ProtosPolyglotExecutionContext openContext(Engine engine) {
        return ProtosPolyglotExecutionContext.open(
                engine,
                java.io.InputStream.nullInputStream(),
                java.io.OutputStream.nullOutputStream(),
                java.io.OutputStream.nullOutputStream(),
                () -> {});
    }

    private static ProtosPrelude uncheckedCore() {
        try {
            return core();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static ProtosClosureValue uncheckedSourceClosure(
            ProtosLanguage language, ProtosActivation creator, String characters, String name) {
        try {
            return sourceClosureWithHome(
                    language, creator, characters, name, ProtosReturnHome.unobservable());
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static ProtosBytecodeRootNode uncheckedLowerRoot(
            ProtosLanguage language, String characters, String name) {
        try {
            return lowerRoot(language, characters, name);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void assertTerminal(ProtosBytecodeRootNode root, String operation) {
        List<String> instructions = signature(root);
        assertTrue(
                instructions.stream().anyMatch(entry -> entry.split(" ", 2)[0].equals(operation)),
                () -> operation + " missing in " + instructions);
        for (String absent : List.of("yield", "c.IsPreparedClosureInvocation", "c.EnterClosureCall")) {
            assertFalse(
                    instructions.stream().anyMatch(entry -> entry.split(" ", 2)[0].equals(absent)),
                    () -> absent + " in terminal root " + instructions);
        }
    }

    private static boolean hasTagOver(
            TagTree tree, Class<? extends Tag> tag, String characters) {
        SourceSection section = tree.getSourceSection();
        if (tree.hasTag(tag)
                && section != null
                && section.getCharacters().toString().equals(characters)) {
            return true;
        }
        for (TagTree child : tree.getTreeChildren()) {
            if (hasTagOver(child, tag, characters)) {
                return true;
            }
        }
        return false;
    }

    private static void drainUntil(
            ProtosActorExecutionDomain domain, ProtosTask task, ProtosTask.State state) {
        int dispatches = 0;
        while (task.state() != state) {
            assertTrue(domain.dispatchOne(), () -> "Task stalled in " + task.state());
            assertTrue(++dispatches <= 4, () -> "Task did not reach " + state);
        }
    }

    private static ProtosClosureValue counter(AtomicInteger count) {
        return nativeClosure(
                (activation, supplied) -> {
                    count.incrementAndGet();
                    return ProtosNullValue.INSTANCE;
                });
    }

    private static ProtosClosureValue recorder(List<String> order, String name) {
        return nativeClosure(
                (activation, supplied) -> {
                    order.add(name);
                    return ProtosNullValue.INSTANCE;
                });
    }

    private static List<Instruction> instructions(ProtosBytecodeRootNode root) {
        return root.getBytecodeNode().getInstructionsAsList();
    }

    private static List<String> signature(ProtosBytecodeRootNode root) {
        List<String> entries = new ArrayList<>();
        for (Instruction instruction : instructions(root)) {
            StringBuilder entry = new StringBuilder(instruction.getName());
            for (Instruction.Argument argument : instruction.getArguments()) {
                entry.append(' ').append(argument.getName()).append(':').append(argument.getKind());
            }
            entries.add(entry.toString());
        }
        return entries;
    }

    private static List<ProtosBytecodeRootNode> groupRoots(
            ProtosBytecodeRootNode root) {
        return root.getRootNodes().getNodes().stream()
                .map(ProtosBytecodeRootNode.class::cast)
                .toList();
    }

    private static void assertInteger(long expected, Object value) {
        assertEquals(Long.toString(expected), ProtosNumericValueSupport.integerDecimalText(value));
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
        return sourceClosureWithHome(
                language, creator, characters, sourceName, creator.returnHome().orElse(null));
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
                        source(characters, sourceName), ProtosExecutionMode.NORMAL);
        return new ProtosClosureValue(
                definition,
                creator.lexicalContextsForClosureCapture(),
                creator.receiver(),
                creator.methodHome().orElse(null),
                returnHome,
                creator.prelude().orElseThrow(),
                plan);
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

    private static Execution executeWithFuture(
            ProtosActorExecutionDomain domain,
            ProtosPrelude prelude,
            ProtosActivation activation,
            ProtosBytecodeRootNode root) {
        ProtosFutureValue future = new ProtosFutureValue(prelude.futurePrototype(), domain);
        ProtosTask task =
                domain.createTask(
                        null,
                        future,
                        current ->
                                ProtosBytecodeTaskExecution.execute(
                                        current,
                                        root.getCallTarget(),
                                        activation));
        future.attachProducerTask(task, activation);
        return new Execution(task, future);
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
        return assertInstanceOf(CanonicalClosure.class, sequence.expressions().get(0));
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
        return new CanonicalToBytecodeLowerer(language, source, ProtosExecutionMode.NORMAL)
                .lowerRoot(canonicalize(characters));
    }

    /** The production ParsingRequest path of the entered Context. */
    private static ProtosBytecodeRootNode compile(String characters, String name) {
        Source source = Source.newBuilder(
                        ProtosLanguage.ID,
                        characters,
                        "i112-tail-" + name + "-" + System.nanoTime() + ".protos")
                .build();
        RootCallTarget target = assertInstanceOf(
                RootCallTarget.class, ProtosLanguageContext.current().parsePublic(source));
        return assertInstanceOf(ProtosBytecodeRootNode.class, target.getRootNode());
    }

    private record Execution(ProtosTask task, ProtosFutureValue future) {}

    private record LanguageScope(Context context, ProtosLanguage language)
            implements AutoCloseable {
        @Override
        public void close() {
            context.leave();
            context.close();
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    /** One raw Polyglot Context with a published Core, entered only while an action runs. */
    private static final class Harness implements AutoCloseable {
        private final ProtosExecutionMode mode;
        private final Context context;
        private ProtosPrelude prelude;

        Harness(ProtosExecutionMode mode) {
            this.mode = mode;
            Context.Builder builder = Context.newBuilder(ProtosLanguage.ID);
            if (mode == ProtosExecutionMode.DEBUG) {
                builder.allowExperimentalOptions(true).option("protos.Debug", "true");
            }
            this.context = builder.build();
            context.initialize(ProtosLanguage.ID);
        }

        void run(Action action) throws Exception {
            context.enter();
            try {
                assertEquals(mode, ProtosLanguageContext.current().executionMode());
                if (prelude == null) {
                    prelude = core();
                }
                action.run();
            } finally {
                context.leave();
            }
        }

        ProtosActivation moduleActivation() {
            return prelude.newModuleActivation();
        }

        @Override
        public void close() {
            context.close();
        }
    }
}

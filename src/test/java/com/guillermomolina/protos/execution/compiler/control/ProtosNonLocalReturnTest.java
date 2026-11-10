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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosNonLocalReturnTest {
    /**
     * Deliberately Java-side: this is an implementation-control test. It
     * manufactures a Closure with a specific host ReturnHome and verifies that
     * nested invocation rethrows the exact transfer to that still-active home.
     * Executable Protos conformance independently covers the observable result
     * of a nested non-local return.
     */
    @Test
    void nestedInvocationWithCapturedActiveHomeRethrowsTransferToOwner()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosClosureValue source =
                closure(prelude, "() => ^42");
        ProtosReturnHome capturedHome = new ProtosReturnHome();
        ProtosClosureValue nested =
                new ProtosClosureValue(
                        source.definition(),
                        source.capturedLexicalContexts(),
                        source.capturedReceiver(),
                        source.methodHome().orElse(null),
                        capturedHome,
                        source.prelude().orElseThrow(),
                        source.executionPlan().orElseThrow());

        ProtosNonLocalReturnException transfer =
                assertThrows(
                        ProtosNonLocalReturnException.class,
                        () -> ProtosClosureInvoker.invoke(nested, List.of()));

        assertSame(capturedHome, transfer.target());
        assertEquals("42", com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                .integerDecimalText(transfer.value()));
        assertTrue(capturedHome.isActive());
    }

    @Test
    void directUnwindReturnsToTheActiveHomeWithExactPayloadIdentity() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation module = prelude.newModuleActivation();
        ProtosObjectValue token = new ProtosObjectValue(ProtosObjectValue.rootObject());
        module.context().createLocalSlot("token", token);

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "plat070-active-home.protos",
                        """
                        m: () => {
                            (() => { ^token })()
                            0
                        }
                        m()
                        """,
                        module);

        assertSame(token, completed(outcome));
        System.out.println("PLAT070_NLR_ACTIVE_HOME_EXACT_PAYLOAD=PASS");
    }

    @Test
    void directUnwindTargetsTheNearestOwnedHomeOfNestedInvocations() throws IOException {
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "plat070-nested-home.protos",
                        """
                        inner: () => {
                            (() => { ^1 })()
                            2
                        }
                        outer: () => {
                            v: inner()
                            v + 10
                        }
                        outer()
                        """,
                        corePrelude().newModuleActivation());

        assertEquals(11L, integer(completed(outcome)));
        System.out.println("PLAT070_NLR_NESTED_HOME=PASS");
    }

    @Test
    void nonMatchingTransferCrossesSeveralOwningBytecodeRootsUnchanged() throws IOException {
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "plat070-crossing-roots.protos",
                        """
                        k: (f) => {
                            f()
                            0
                        }
                        j: (f) => {
                            k(f)
                            0
                        }
                        m: () => {
                            j(() => { ^4 })
                            1
                        }
                        m()
                        """,
                        corePrelude().newModuleActivation());

        assertEquals(4L, integer(completed(outcome)));
        System.out.println("PLAT070_NLR_CROSSES_NONMATCHING_HOMES=PASS");
    }

    @Test
    void escapedHomeSignalsCatchableInvalidReturn() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "plat070-escaped-home.protos",
                        """
                        m: () => {
                            () => { ^1 }
                        }
                        escaped: m()
                        Error.handle(() => { escaped() }, (caught) => { caught })
                        """,
                        prelude.newModuleActivation());

        ProtosObjectValue invalidReturn =
                assertInstanceOf(ProtosObjectValue.class, completed(outcome));
        assertSame(prelude.invalidReturnPrototype(), invalidReturn.parent().orElseThrow());
        System.out.println("PLAT070_NLR_ESCAPED_HOME_INVALID_RETURN=PASS");
    }

    @Test
    void crossTaskTransferFailsOnlyTheProducerTaskWithInvalidReturn() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "plat070-cross-task.protos",
                        """
                        m: () => {
                            escape: () => { ^1 }
                            child: (() => {
                                escape()
                            }).future()
                            Error.handle(() => { child.value() }, (caught) => { 0 })
                            child
                        }
                        m()
                        """,
                        prelude.newModuleActivation());

        ProtosFutureValue child = assertInstanceOf(ProtosFutureValue.class, completed(outcome));
        ProtosObjectValue invalidReturn = child.failedError().orElseThrow();
        assertSame(prelude.invalidReturnPrototype(), invalidReturn.parent().orElseThrow());
        assertSame(
                invalidReturn,
                child.producerTask().orElseThrow().failure().orElseThrow());
        System.out.println("PLAT070_NLR_CROSS_TASK_INVALID_RETURN=PASS");
    }

    @Test
    void validReturnsInsideAChildTaskReachTheirOwnTasksHomes() throws IOException {
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "plat070-child-task-homes.protos",
                        """
                        inner: () => {
                            (() => { ^1 })()
                            2
                        }
                        outer: () => {
                            (() => { ^(inner() + 10) })()
                            0
                        }
                        (() => { outer() }).future().value()
                        """,
                        corePrelude().newModuleActivation());

        assertEquals(11L, integer(completed(outcome)));
        System.out.println("PLAT070_CHILD_TASK_VALID_RETURNS=PASS");
    }

    /**
     * PLAT070: a ^ executed in another Task than the one owning its active home signals
     * InvalidReturn at the ^ itself, as an ordinary guest Error: a handler inside the executing
     * child Task catches it, which a failure fabricated only at the Task boundary could not be.
     */
    @Test
    void crossTaskReturnSignalsInvalidReturnAtTheCaretInsideTheChildTask() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "plat070-cross-task-at-caret.protos",
                        """
                        m: () => {
                            escape: () => { ^1 }
                            child: (() => {
                                Error.handle(() => { escape() }, (caught) => { caught })
                            }).future()
                            child.value()
                        }
                        m()
                        """,
                        prelude.newModuleActivation());

        ProtosObjectValue invalidReturn =
                assertInstanceOf(ProtosObjectValue.class, completed(outcome));
        assertSame(prelude.invalidReturnPrototype(), invalidReturn.parent().orElseThrow());
        System.out.println("PLAT070_INVALID_CROSS_TASK_RETURN=SOURCE_SITE_GUEST_ERROR");
    }

    @Test
    void capturedHomeUsedInAnotherTaskAfterItsOwnerCompletedSignalsInvalidReturn()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "plat070-completed-home-other-task.protos",
                        """
                        m: () => {
                            () => { ^1 }
                        }
                        escaped: m()
                        (() => {
                            Error.handle(() => { escaped() }, (caught) => { caught })
                        }).future().value()
                        """,
                        prelude.newModuleActivation());

        ProtosObjectValue invalidReturn =
                assertInstanceOf(ProtosObjectValue.class, completed(outcome));
        assertSame(prelude.invalidReturnPrototype(), invalidReturn.parent().orElseThrow());
        System.out.println("PLAT070_COMPLETED_HOME_OTHER_TASK=INVALID_RETURN");
    }

    @Test
    void taskFreeNestedReturnKeepsSynchronousSemantics() throws IOException {
        Object value =
                ProtosTestExecutionSupport.evaluate(
                        "plat070-task-free-nested.protos",
                        """
                        m: () => {
                            (() => { ^5 })()
                            0
                        }
                        m()
                        """,
                        corePrelude().newModuleActivation());

        assertEquals(5L, integer(value));
        System.out.println("PLAT070_TASK_FREE_NESTED_RETURN=PASS");
    }

    /** PLAT070: a completed materialized home does not retain its owning Task. */
    @Test
    void completedReturnHomeReleasesItsOwnerTask() {
        com.guillermomolina.protos.runtime.ProtosActorExecutionDomain domain =
                new com.guillermomolina.protos.runtime.ProtosActorExecutionDomain();
        com.guillermomolina.protos.runtime.ProtosTask task =
                domain.createTask(null, current -> {});
        ProtosReturnHome home = new ProtosReturnHome();

        home.bindOwnerTaskForRuntime(task);
        home.bindOwnerTaskForRuntime(task);
        assertSame(task, home.ownerTaskForRuntime(), "binding is idempotent for the same Task");
        assertThrows(
                IllegalStateException.class,
                () -> home.bindOwnerTaskForRuntime(domain.createTask(null, current -> {})),
                "an established owner is never changed");

        home.complete();
        assertFalse(home.isActive());
        assertNull(home.ownerTaskForRuntime(), "a completed home releases its Task");
        home.bindOwnerTaskForRuntime(task);
        assertNull(home.ownerTaskForRuntime(), "a completed home is never rebound");

        ProtosReturnHome marker = ProtosReturnHome.unobservable();
        marker.bindOwnerTaskForRuntime(task);
        assertNull(marker.ownerTaskForRuntime(), "the shared marker is untouched");
        System.out.println("PLAT070_COMPLETED_HOME_OWNER_RELEASED=PASS");
    }

    /**
     * I115 / PLAT070: closing an owned home must release the exact
     * Task reference, even when the closure was entered through
     * the compact B5 call path. A second close is harmless.
     */
    @Test
    void closeOwnedReturnHomeReleasesTaskAndIsIdempotent() {
        com.guillermomolina.protos.runtime.ProtosActorExecutionDomain domain =
                new com.guillermomolina.protos.runtime.ProtosActorExecutionDomain();

        com.guillermomolina.protos.runtime.ProtosTask task =
                domain.createTask(null, current -> {});

        ProtosReturnHome home = new ProtosReturnHome();

        home.bindOwnerTaskForRuntime(task);
        assertSame(task, home.ownerTaskForRuntime());
        assertTrue(home.isActive());

        home.closeOwned();

        assertFalse(home.isActive());
        assertNull(
                home.ownerTaskForRuntime(),
                "closing an owned home must release its Task");

        home.closeOwned();

        assertFalse(home.isActive());
        assertNull(home.ownerTaskForRuntime());

        home.bindOwnerTaskForRuntime(task);

        assertNull(
                home.ownerTaskForRuntime(),
                "a completed home must never be rebound");
    }

    /**
     * PLAT070: the home of a completed Task-owned invocation released its Task, and a Closure that
     * escaped with it still signals InvalidReturn rather than passing as a Task-free home.
     */
    @Test
    void escapedClosureOfACompletedTaskOwnedHomeStillSignalsInvalidReturn() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation module = prelude.newModuleActivation();
        ProtosClosureValue escaped =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        completed(
                                ProtosTestExecutionSupport.execute(
                                        "plat070-escaped-lifecycle.protos",
                                        """
                                        m: () => {
                                            () => { ^1 }
                                        }
                                        escaped: m()
                                        escaped
                                        """,
                                        module)));
        ProtosReturnHome home = escaped.returnHome().orElseThrow();
        assertFalse(home.isActive());
        assertNull(home.ownerTaskForRuntime(), "the completed home no longer retains its Task");

        ProtosObjectValue invalidReturn =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        completed(
                                ProtosTestExecutionSupport.execute(
                                        "plat070-escaped-lifecycle-call.protos",
                                        "Error.handle(() => { escaped() }, (caught) => { caught })",
                                        sibling(module))));
        assertSame(prelude.invalidReturnPrototype(), invalidReturn.parent().orElseThrow());

        Object taskFree =
                ProtosTestExecutionSupport.evaluate(
                        "plat070-escaped-lifecycle-task-free.protos",
                        "Error.handle(() => { escaped() }, (caught) => { caught })",
                        sibling(module));
        assertSame(
                prelude.invalidReturnPrototype(),
                assertInstanceOf(ProtosObjectValue.class, taskFree).parent().orElseThrow(),
                "a released owner never makes a completed home pass as a Task-free one");
        System.out.println("PLAT070_ESCAPED_RELEASED_HOME_INVALID_RETURN=PASS");
    }

    @Test
    void recursiveOwnersAndIndirectReturnsPreserveOperandIdentity() throws IOException {
        ProtosActivation module = corePrelude().newModuleActivation();
        ProtosObjectValue token = new ProtosObjectValue(ProtosObjectValue.rootObject());
        module.context().createLocalSlot("token", token);
        assertSame(token, completed(ProtosTestExecutionSupport.execute(
                "i115-recursive-return.protos", """
                invoke: (f) => { f() }
                recurse: (depth) => {
                    (depth == 0).ifTrue(() => { ^token })
                    inner: recurse(depth - 1)
                    invoke(() => { ^inner })
                    null
                }
                recurse(4)
                """, module)));
    }

    @Test
    void defaultAndGuestErrorDepartureCloseTheCapturedOwner() throws IOException {
        ProtosPrelude prelude = corePrelude();
        for (String body : List.of(
                "(arg: failure.signal()) => { bodyReached = true }",
                "() => { failure.signal() }",
                "() => { consume: (arg) => arg\nconsume(failure.signal()) }")) {
            ProtosActivation module = prelude.newModuleActivation();
            ProtosObjectValue failure = new ProtosObjectValue(prelude.errorPrototype());
            module.context().createLocalSlot("failure", failure);
            Object result = completed(ProtosTestExecutionSupport.execute(
                    "i115-failed-entry.protos", """
                    escaped: null
                    bodyReached: false
                    owner: () => {
                        escaped = () => { ^42 }
                        fail: BODY
                        fail()
                        bodyReached = true
                    }
                    caught: Error.handle(() => { owner() }, (error) => { error })
                    escaped
                    """.replace("BODY", body), module));
            ProtosClosureValue escaped = assertInstanceOf(ProtosClosureValue.class, result);
            assertFalse(escaped.returnHome().orElseThrow().isActive());
            assertSame(failure, module.context().readLocalSlot("caught").orElseThrow());
            assertSame(com.guillermomolina.protos.runtime.ProtosBooleanValue.FALSE,
                    module.context().readLocalSlot("bodyReached").orElseThrow());
            Object invalid = ProtosTestExecutionSupport.evaluate(
                    "i115-failed-entry-escaped.protos",
                    "Error.handle(() => { escaped() }, (error) => { error })", sibling(module));
            assertSame(prelude.invalidReturnPrototype(),
                    assertInstanceOf(ProtosObjectValue.class, invalid).parent().orElseThrow());
        }
    }

    /**
     * I115-A: a return issued through a warm site that dispatches a captured
     * Closure via a callee proven not to own a home crosses both boundaries,
     * runs the crossed ensure cleanup first, and is consumed only by its owner
     * with the payload computed before cleanup. It never becomes local.
     */
    @Test
    void ensureCleanupRunsBeforeTheOwnerConsumesAReturnAcrossSpecializedRoutes()
            throws IOException {
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "i115a-ensure-before-owner.protos",
                        """
                        cleaned: 0
                        invoke: (f) => { f() }
                        owner: (v) => {
                            (() => {
                                invoke(() => { ^(v + cleaned) })
                            }).ensure(() => { cleaned = cleaned + 1 })
                            0
                        }
                        a: owner(10)
                        b: owner(20)
                        c: owner(30)
                        a + b + c + (cleaned * 1000)
                        """,
                        corePrelude().newModuleActivation());

        // 10 + 21 + 32 payloads, three cleanups.
        assertEquals(3063L, integer(completed(outcome)));
    }

    /**
     * EXECUTION_AND_CONTROL "Pending transfer and cleanup precedence": a valid
     * return initiated by cleanup supersedes the pending one, whether it
     * targets the same owner or an outer owner that the inner owner must let
     * pass unchanged.
     */
    @Test
    void cleanupReturnSupersedesThePendingTransfer() throws IOException {
        ProtosExecutionOutcome sameOwner =
                ProtosTestExecutionSupport.execute(
                        "i115a-cleanup-same-owner.protos",
                        """
                        owner: () => {
                            (() => { ^1 }).ensure(() => { ^2 })
                            0
                        }
                        owner() + owner()
                        """,
                        corePrelude().newModuleActivation());
        assertEquals(4L, integer(completed(sameOwner)));

        ProtosExecutionOutcome outerOwner =
                ProtosTestExecutionSupport.execute(
                        "i115a-cleanup-outer-owner.protos",
                        """
                        outer: () => {
                            escape: () => { ^3 }
                            inner: () => {
                                (() => { ^1 }).ensure(() => { escape() })
                                0
                            }
                            inner() + 100
                        }
                        outer() + outer()
                        """,
                        corePrelude().newModuleActivation());
        assertEquals(6L, integer(completed(outerOwner)));
    }

    /** A fresh module activation sharing {@code module}'s context, domain and module state. */
    private static ProtosActivation sibling(ProtosActivation module) {
        return module.prelude().orElseThrow().newModuleActivation(
                module.actorModuleState(),
                module.currentModuleKey().orElse(null),
                module.context(),
                module.executionDomain());
    }

    private static Object completed(ProtosExecutionOutcome outcome) {
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "error=" + outcome.error());
        return outcome.value();
    }

    private static long integer(Object value) {
        return assertInstanceOf(Long.class, value);
    }

    private static ProtosClosureValue closure(
            ProtosPrelude prelude,
            String source) {
        return assertInstanceOf(
                ProtosClosureValue.class,
                ProtosTestExecutionSupport.evaluate(
                        "non-local-return-test.protos",
                        source,
                        prelude.newModuleActivation()));
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap()
                .bootstrap(Path.of("protos", "lib", "core"));
    }
}

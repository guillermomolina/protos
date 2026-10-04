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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosDiagnosticTrace;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosTask;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * CLI008-C1 focal coverage of the occurrence-carried bounded guest diagnostic trace (PLAT049
 * Candidate C). Lines are asserted exactly; columns are only required to be valid one-based
 * positions, because the exact column is the Bytecode source-section choice, not this slice.
 */
final class ProtosCli008C1DiagnosticTraceTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void nestedSourceCallsProjectOnlySemanticGuestFramesInnermostFirst() throws Exception {
        String name = "cli008c1-nested.protos";
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        name,
                        """
                        a: () => {
                            b()
                        }
                        b: () => {
                            Error().signal()
                        }
                        a()
                        """,
                        module());

        ProtosDiagnosticTrace trace = failedTrace(outcome, name);
        assertEquals(List.of(5, 2, 7), lines(trace));
        assertFalse(trace.truncated());
        System.out.println("CLI008_C1_NESTED_SOURCE_CALL_ORDER=PASS");
    }

    @Test
    void handledErrorLeavesNoTerminalOccurrence() throws Exception {
        ProtosExecutionOutcome handled =
                ProtosTestExecutionSupport.execute(
                        "cli008c1-handled.protos",
                        """
                        Error.handle(() => {
                            Error().signal()
                        }, (caught) => { 0 })
                        """,
                        module());
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, handled.state());
        assertTrue(handled.failureDiagnosticTrace().isEmpty());

        String name = "cli008c1-handled-then-failed.protos";
        ProtosExecutionOutcome failed =
                ProtosTestExecutionSupport.execute(
                        name,
                        """
                        Error.handle(() => {
                            Error().signal()
                        }, (caught) => { 0 })
                        fail: () => {
                            Error().signal()
                        }
                        fail()
                        """,
                        module());
        assertEquals(List.of(5, 7), lines(failedTrace(failed, name)));
        System.out.println("CLI008_C1_HANDLED_ERROR_TERMINAL_TRACE=NONE");
    }

    @Test
    void sameErrorSignalledTwiceKeepsIdentityWithDistinctOccurrences() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation first = prelude.newModuleActivation();
        ProtosActivation second = prelude.newModuleActivation();
        ProtosObjectValue shared = ProtosCoreErrors.newError(first);
        first.context().createLocalSlot("shared", shared);
        second.context().createLocalSlot("shared", shared);
        Map<String, Object> slotsBefore = shared.localSlotsSnapshot();

        String firstName = "cli008c1-same-first.protos";
        ProtosExecutionOutcome firstOutcome =
                ProtosTestExecutionSupport.execute(
                        firstName,
                        """
                        first: () => {
                            shared.signal()
                        }
                        first()
                        """,
                        first);
        String secondName = "cli008c1-same-second.protos";
        ProtosExecutionOutcome secondOutcome =
                ProtosTestExecutionSupport.execute(
                        secondName,
                        """
                        second: () => {
                            third()
                        }
                        third: () => {
                            shared.signal()
                        }
                        second()
                        """,
                        second);

        assertSame(shared, firstOutcome.error());
        assertSame(shared, secondOutcome.error());
        assertEquals(List.of(2, 4), lines(failedTrace(firstOutcome, firstName)));
        assertEquals(List.of(5, 2, 7), lines(failedTrace(secondOutcome, secondName)));
        assertEquals(slotsBefore, shared.localSlotsSnapshot(), "the Error must not be mutated");
        System.out.println("CLI008_C1_SAME_ERROR_DISTINCT_OCCURRENCES=PASS");
        System.out.println("CLI008_C1_ERROR_GUEST_VISIBLE_MUTATION=NO");
    }

    @Test
    void ensureCleanupErrorSupersedesAndOwnsTheTerminalTrace() throws Exception {
        ProtosActivation module = module();
        ProtosObjectValue bodyError = ProtosCoreErrors.newError(module);
        ProtosObjectValue cleanupError = ProtosCoreErrors.newError(module);
        module.context().createLocalSlot("bodyError", bodyError);
        module.context().createLocalSlot("cleanupError", cleanupError);

        String name = "cli008c1-ensure.protos";
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        name,
                        """
                        body: () => {
                            bodyError.signal()
                        }
                        cleanup: () => {
                            cleanupError.signal()
                        }
                        run: () => {
                            body.ensure(cleanup)
                        }
                        run()
                        """,
                        module);

        assertSame(cleanupError, outcome.error());
        List<Integer> lines = lines(failedTrace(outcome, name));
        assertEquals(List.of(5, 8, 10), lines);
        assertFalse(lines.contains(2), "the superseded body occurrence must not leak");
        System.out.println("CLI008_C1_ENSURE_SUPERSESSION_TRACE=PASS");
    }

    /**
     * D177: a non-local return whose home is active in another (suspended) Task reaches the
     * child Task boundary as a bridged control transfer, and the Task fails directly with a
     * fabricated InvalidReturn; no ProtosSignalException exists on that route.
     */
    @Test
    void directTaskFailureFromBridgedTransferProjectsOnlyItsOwnTask() throws Exception {
        String name = "cli008c1-direct-invalid-return.protos";
        ProtosPrelude prelude = prelude();
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        name,
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

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "error=" + outcome.error());
        ProtosFutureValue child = assertInstanceOf(ProtosFutureValue.class, outcome.value());
        ProtosObjectValue invalidReturn = child.failedError().orElseThrow();
        assertSame(prelude.invalidReturnPrototype(), invalidReturn.parent().orElseThrow());

        ProtosTask producer = child.producerTask().orElseThrow();
        assertSame(invalidReturn, producer.failure().orElseThrow());
        ProtosDiagnosticTrace trace = producer.failureDiagnosticTraceForRuntime().orElseThrow();
        assertGuestOnly(trace, name);
        List<Integer> lines = lines(trace);
        assertFalse(lines.contains(6), "consumer frames must not enter the producer trace");
        assertFalse(lines.contains(9), "consumer frames must not enter the producer trace");
        if (!lines.isEmpty()) {
            assertEquals(4, lines.get(0));
        }
        System.out.println("CLI008_C1_DIRECT_TASK_FAILURE_TRACE=PASS");
    }

    @Test
    void failedFutureValueTraceIsConsumerLocalAfterSuspendAndResume() throws Exception {
        ProtosActivation module = module();
        ProtosObjectValue producerError = ProtosCoreErrors.newError(module);
        module.context().createLocalSlot("producerError", producerError);

        String name = "cli008c1-future.protos";
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        name,
                        """
                        producer: (() => {
                            producerError.signal()
                        }).future()
                        consume: () => {
                            producer.value()
                        }
                        consume()
                        """,
                        module);

        assertSame(producerError, outcome.error());
        List<Integer> lines = lines(failedTrace(outcome, name));
        assertEquals(List.of(5, 7), lines);
        assertFalse(lines.contains(2), "producer frames must not reach the consumer trace");
        System.out.println("CLI008_C1_FAILED_FUTURE_TRACE=CONSUMER_LOCAL");
    }

    @Test
    void composedContinuationResumeKeepsSemanticOrderWithoutDuplicates() throws Exception {
        String name = "cli008c1-continuation.protos";
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        name,
                        """
                        gate: (() => { 1 }).future()
                        inner: () => {
                            gate.value()
                            Error().signal()
                        }
                        outer: () => {
                            inner()
                        }
                        outer()
                        """,
                        module());
        assertEquals(List.of(4, 7, 9), lines(failedTrace(outcome, name)));

        String inlineName = "cli008c1-continuation-inline.protos";
        ProtosExecutionOutcome inline =
                ProtosTestExecutionSupport.execute(
                        inlineName,
                        """
                        gate: (() => { 1 }).future()
                        run: () => {
                            true.ifTrue(() => {
                                gate.value()
                                Error().signal()
                            })
                        }
                        run()
                        """,
                        module());
        assertEquals(List.of(5, 3, 8), lines(failedTrace(inline, inlineName)));
        System.out.println("CLI008_C1_CONTINUATION_RESUME_ORDER=CORRECT");
    }

    @Test
    void inlineCallbackContributesExactlyOneSemanticFrame() throws Exception {
        String name = "cli008c1-inline.protos";
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        name,
                        """
                        run: () => {
                            true.ifTrue(() => {
                                Error().signal()
                            })
                        }
                        run()
                        """,
                        module());
        assertEquals(List.of(3, 2, 6), lines(failedTrace(outcome, name)));

        String callerName = "cli008c1-inline-caller.protos";
        ProtosExecutionOutcome caller =
                ProtosTestExecutionSupport.execute(
                        callerName,
                        """
                        boom: () => {
                            Error().signal()
                        }
                        run: () => {
                            true.ifTrue(() => {
                                boom()
                            })
                        }
                        run()
                        """,
                        module());
        assertEquals(List.of(2, 6, 5, 9), lines(failedTrace(caller, callerName)));
        System.out.println("CLI008_C1_PLAT044_INLINE_FRAME=YES");
        System.out.println("CLI008_C1_PLAT044_INLINE_FRAME_DUPLICATED=NO");
    }

    @Test
    void physicalCallbackFallbackContributesExactlyOneFrame() throws Exception {
        String name = "cli008c1-physical-callback.protos";
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        name,
                        """
                        run: () => {
                            callback: () => {
                                Error().signal()
                            }
                            true.ifTrue(callback)
                        }
                        run()
                        """,
                        module());
        assertEquals(List.of(3, 5, 7), lines(failedTrace(outcome, name)));
        System.out.println("CLI008_C1_PHYSICAL_CALLBACK_FRAME=YES");
    }

    @Test
    void deepStackRetainsTheInnermostBoundedFrames() throws Exception {
        String name = "cli008c1-truncation.protos";
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        name,
                        """
                        down: (n) => {
                            (n == 0).ifTrue(() => {
                                Error().signal()
                            })
                            down(n - 1)
                        }
                        down(100)
                        """,
                        module());

        ProtosDiagnosticTrace trace = failedTrace(outcome, name);
        assertEquals(ProtosDiagnosticTrace.MAX_FRAMES, trace.frames().size());
        assertTrue(trace.truncated());
        List<Integer> lines = lines(trace);
        assertEquals(3, lines.get(0), "innermost inline callback frame retained");
        assertEquals(2, lines.get(1), "innermost recursive activation retained");
        for (int index = 2; index < lines.size(); index++) {
            assertEquals(5, lines.get(index), "recursive caller frames retained innermost first");
        }
        System.out.println("CLI008_C1_TRACE_BOUND=64");
        System.out.println("CLI008_C1_TRUNCATION=PASS");
    }

    @Test
    void concurrentTasksDoNotShareTraces() throws Exception {
        ProtosActivation module = module();
        ProtosObjectValue leftError = ProtosCoreErrors.newError(module);
        ProtosObjectValue rightError = ProtosCoreErrors.newError(module);
        module.context().createLocalSlot("leftError", leftError);
        module.context().createLocalSlot("rightError", rightError);

        String name = "cli008c1-tasks.protos";
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        name,
                        """
                        left: (() => {
                            leftError.signal()
                        }).future()
                        right: (() => {
                            rightError.signal()
                        }).future()
                        Error.handle(() => { left.value() }, (caught) => { 0 })
                        right.value()
                        """,
                        module);
        assertSame(rightError, outcome.error());
        assertEquals(List.of(8), lines(failedTrace(outcome, name)));
        System.out.println("CLI008_C1_TASK_TRACE_ISOLATION=PASS");
    }

    @Test
    void independentExecutionsDoNotShareTraces() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<List<Integer>>> results = new ArrayList<>();
            for (int iteration = 0; iteration < 8; iteration++) {
                results.add(executor.submit(execution(
                        "cli008c1-isolated-a.protos",
                        """
                        a: () => {
                            Error().signal()
                        }
                        a()
                        """)));
                results.add(executor.submit(execution(
                        "cli008c1-isolated-b.protos",
                        """
                        b: () => {
                            c()
                        }
                        c: () => {
                            Error().signal()
                        }
                        b()
                        """)));
            }
            for (int index = 0; index < results.size(); index++) {
                List<Integer> expected = index % 2 == 0 ? List.of(2, 4) : List.of(5, 2, 7);
                assertEquals(expected, results.get(index).get(60, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }
        System.out.println("CLI008_C1_CONTEXT_TRACE_ISOLATION=PASS");
    }

    private static Callable<List<Integer>> execution(String name, String source) {
        return () -> lines(failedTrace(
                ProtosTestExecutionSupport.execute(name, source, module()), name));
    }

    private static ProtosDiagnosticTrace failedTrace(ProtosExecutionOutcome outcome, String name) {
        assertEquals(
                ProtosExecutionOutcome.State.FAILED,
                outcome.state(),
                () -> "value=" + outcome.value());
        ProtosDiagnosticTrace trace = outcome.failureDiagnosticTrace().orElseThrow();
        assertGuestOnly(trace, name);
        return trace;
    }

    /** No Java, Truffle, scheduler, C-prime or continuation helper frame may be projected. */
    private static void assertGuestOnly(ProtosDiagnosticTrace trace, String name) {
        for (ProtosDiagnosticTrace.Frame frame : trace.frames()) {
            assertEquals(name, frame.sourceName(), () -> "foreign frame " + frame);
            assertNull(frame.label(), () -> "anonymous Closure acquired a label: " + frame);
            assertTrue(frame.knownDisplayPath().isEmpty(), () -> "literal source path " + frame);
            assertTrue(frame.line() >= 1 && frame.column() >= 1, frame::toString);
        }
    }

    private static List<Integer> lines(ProtosDiagnosticTrace trace) {
        return trace.frames().stream().map(ProtosDiagnosticTrace.Frame::line).toList();
    }

    private static ProtosPrelude prelude() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(CORE);
    }

    private static ProtosActivation module() throws Exception {
        return prelude().newModuleActivation();
    }
}

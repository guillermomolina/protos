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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * TOOL009-E focal tests for the Process-snapshot Logical Case protocol adapter.
 *
 * <p>These tests demonstrate that {@code logicalCaseExecutionAsync} on the process-snapshot lane
 * actually reaches {@link ProtosProcessSnapshotExecution}: they do not merely check that the
 * bootstrap slot exists.
 */
final class ProtosProcessSnapshotLogicalCaseExecutionFacilityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path SNAPSHOT_IDENTITY =
            Path.of("protos", "tests", "conformance", "process", "snapshot-identity.protos");

    @Test
    void completionReachesProcessSnapshotExecutionWithD135ProcessBootstrap() throws Exception {
        ManualSubmission submission = new ManualSubmission();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        String source = Files.readString(SNAPSHOT_IDENTITY, StandardCharsets.UTF_8);

        try (ProtosProcessSnapshotLogicalCaseExecutionFacility facility =
                ProtosProcessSnapshotLogicalCaseExecutionFacility.install(
                        activation, prelude, submission)) {
            ProtosFutureValue future =
                    invoke(prelude, activation, source, List.of("snapshot-identity"),
                            "snapshot-identity");

            assertEquals(ProtosFutureValue.State.PENDING, future.state());
            assertEquals(1, submission.queuedCount());

            assertTrue(submission.runNext());
            assertEquals(
                    ProtosFutureValue.State.PENDING,
                    future.state(),
                    "host completion must remain inert until caller-domain dispatch");

            assertTrue(activation.executionDomain().dispatchOne());
            assertEquals(ProtosFutureValue.State.RESOLVED, future.state());

            ProtosObjectValue completion =
                    assertInstanceOf(
                            ProtosObjectValue.class, future.resolvedValue().orElseThrow());

            ProtosStringValue phase =
                    assertInstanceOf(
                            ProtosStringValue.class,
                            completion.readLocalSlot("phase").orElseThrow());
            assertEquals("case-execution", phase.value());

            ProtosObjectValue observation =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            completion.readLocalSlot("observation").orElseThrow());

            ProtosStringValue state =
                    assertInstanceOf(
                            ProtosStringValue.class,
                            observation.readLocalSlot("state").orElseThrow());
            assertEquals("completed", state.value());

            // snapshot-identity.protos only passes when process.args()/process.environment()
            // observe the fixture snapshots established by
            // establishArgumentsForRuntime/establishEnvironmentForRuntime, so this demonstrates
            // that the D135 Process bootstrap ran, not a test-harness-injected value.
            assertSame(
                    ProtosBooleanValue.TRUE, observation.readLocalSlot("value").orElseThrow());
        }
    }

    @Test
    void rejectsSignatureSelectorMismatchBeforeCreatingAFuture() throws Exception {
        ManualSubmission submission = new ManualSubmission();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        String source = Files.readString(SNAPSHOT_IDENTITY, StandardCharsets.UTF_8);

        try (ProtosProcessSnapshotLogicalCaseExecutionFacility facility =
                ProtosProcessSnapshotLogicalCaseExecutionFacility.install(
                        activation, prelude, submission)) {
            assertThrows(
                    ProtosSignalException.class,
                    () ->
                            invoke(
                                    prelude,
                                    activation,
                                    source,
                                    List.of("other-case"),
                                    "snapshot-identity"));

            assertEquals(
                    0,
                    submission.queuedCount(),
                    "a protocol-shape mismatch must fail closed before host submission");
        }
    }

    @Test
    void independentLogicalCasesRematerializeIndependentProcesses() throws Exception {
        ManualSubmission submission = new ManualSubmission();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        String source = Files.readString(SNAPSHOT_IDENTITY, StandardCharsets.UTF_8);

        try (ProtosProcessSnapshotLogicalCaseExecutionFacility facility =
                ProtosProcessSnapshotLogicalCaseExecutionFacility.install(
                        activation, prelude, submission)) {
            ProtosFutureValue first =
                    invoke(prelude, activation, source, List.of("snapshot-identity"),
                            "snapshot-identity");
            ProtosFutureValue second =
                    invoke(prelude, activation, source, List.of("snapshot-identity"),
                            "snapshot-identity");

            assertEquals(2, submission.queuedCount());

            assertTrue(submission.runNext());
            assertTrue(submission.runNext());
            assertTrue(activation.executionDomain().dispatchOne());
            assertTrue(activation.executionDomain().dispatchOne());

            for (ProtosFutureValue future : List.of(first, second)) {
                assertEquals(ProtosFutureValue.State.RESOLVED, future.state());

                ProtosObjectValue completion =
                        assertInstanceOf(
                                ProtosObjectValue.class,
                                future.resolvedValue().orElseThrow());
                ProtosObjectValue observation =
                        assertInstanceOf(
                                ProtosObjectValue.class,
                                completion.readLocalSlot("observation").orElseThrow());
                ProtosStringValue state =
                        assertInstanceOf(
                                ProtosStringValue.class,
                                observation.readLocalSlot("state").orElseThrow());

                // Each Logical Case independently satisfies snapshot-identity.protos's own
                // same-Process/different-Process assertions, so neither invocation observed
                // the other's rematerialized Process.
                assertEquals("completed", state.value());
                assertSame(
                        ProtosBooleanValue.TRUE,
                        observation.readLocalSlot("value").orElseThrow());
            }
        }
    }

    private static ProtosFutureValue invoke(
            ProtosPrelude prelude,
            ProtosActivation activation,
            String source,
            List<String> signature,
            String selector) {
        Object execution =
                activation
                        .context()
                        .readLocalSlot(
                                ProtosProcessSnapshotLogicalCaseExecutionFacility.BOOTSTRAP_SLOT)
                        .orElseThrow();

        ProtosArrayValue sourceAssociation =
                prelude.newFrozenArray(
                        List.of(
                                new ProtosStringValue("test-corpus"),
                                new ProtosStringValue("snapshot-identity.protos")));

        ProtosArrayValue signatureValue =
                prelude.newFrozenArray(
                        signature.stream()
                                .map(ProtosStringValue::new)
                                .map(value -> (Object) value)
                                .toList());

        return assertInstanceOf(
                ProtosFutureValue.class,
                ProtosInvocation.invoke(
                        execution,
                        List.of(
                                sourceAssociation,
                                new ProtosStringValue(source),
                                signatureValue,
                                new ProtosStringValue(selector)),
                        activation));
    }

    private static final class ManualSubmission
            implements ProtosAsyncExactExecutionFacility.Submission {
        private final ArrayDeque<Job> jobs = new ArrayDeque<>();

        @Override
        public synchronized ProtosAsyncExactExecutionFacility.Submitted submit(Runnable work) {
            Job job = new Job(Objects.requireNonNull(work, "work"));
            jobs.addLast(job);
            return job;
        }

        synchronized int queuedCount() {
            return jobs.size();
        }

        boolean runNext() {
            Job job;
            synchronized (this) {
                job = jobs.pollFirst();
            }
            return job != null && job.runIfAccepted();
        }

        private static final class Job implements ProtosAsyncExactExecutionFacility.Submitted {
            private final Runnable work;
            private boolean started;
            private boolean cancelled;

            private Job(Runnable work) {
                this.work = work;
            }

            @Override
            public synchronized boolean cancelIfNotStarted() {
                if (started || cancelled) {
                    return false;
                }
                cancelled = true;
                return true;
            }

            synchronized boolean runIfAccepted() {
                if (started || cancelled) {
                    return false;
                }
                started = true;
                work.run();
                return true;
            }
        }
    }
}

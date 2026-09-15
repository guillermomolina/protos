/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

final class ProtosAsyncProcessSnapshotExecutionFacilityTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");
    private static final Path SNAPSHOT_IDENTITY =
            Path.of(
                    "protos",
                    "tests",
                    "conformance",
                    "process",
                    "snapshot-identity.protos");

    @Test
    void completionReturnsThroughCallerDomainWithD135Bootstrap() throws Exception {
        ManualSubmission submission = new ManualSubmission();

        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation =
                prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost =
                        ProtosPolyglotRuntimeHost.open();
                ProtosAsyncExactExecutionFacility facility =
                        ProtosAsyncProcessSnapshotExecutionFacility.install(
                                activation,
                                prelude,
                                runtimeHost,
                                submission)) {

            Object execution =
                    activation
                            .context()
                            .readLocalSlot(
                                    ProtosAsyncProcessSnapshotExecutionFacility.BOOTSTRAP_SLOT)
                            .orElseThrow();

            String source =
                    Files.readString(
                            SNAPSHOT_IDENTITY,
                            StandardCharsets.UTF_8);

            ProtosFutureValue future =
                    assertInstanceOf(
                            ProtosFutureValue.class,
                            ProtosInvocation.invoke(
                                    execution,
                                    List.of(new ProtosStringValue(source)),
                                    activation));

            assertEquals(
                    ProtosFutureValue.State.PENDING,
                    future.state());

            assertTrue(submission.runNext());

            assertEquals(
                    ProtosFutureValue.State.PENDING,
                    future.state(),
                    "host completion must remain inert until caller-domain dispatch");

            assertTrue(activation.executionDomain().dispatchOne());

            assertEquals(
                    ProtosFutureValue.State.RESOLVED,
                    future.state());

            ProtosObjectValue observation =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            future.resolvedValue().orElseThrow());

            ProtosStringValue state =
                    assertInstanceOf(
                            ProtosStringValue.class,
                            observation.readLocalSlot("state").orElseThrow());

            assertEquals("completed", state.value());
            assertSame(
                    ProtosBooleanValue.TRUE,
                    observation.readLocalSlot("value").orElseThrow());
        }
    }

    private static final class ManualSubmission
            implements ProtosAsyncExactExecutionFacility.Submission {
        private final ArrayDeque<Job> jobs = new ArrayDeque<>();

        @Override
        public synchronized ProtosAsyncExactExecutionFacility.Submitted submit(
                Runnable work) {
            Job job = new Job(Objects.requireNonNull(work, "work"));
            jobs.addLast(job);
            return job;
        }

        boolean runNext() {
            Job job;
            synchronized (this) {
                job = jobs.pollFirst();
            }
            return job != null && job.runIfAccepted();
        }

        private static final class Job
                implements ProtosAsyncExactExecutionFacility.Submitted {
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

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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosTestLogicalCaseExecutionFacilityTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY =
            Path.of("protos", "lib");
    private static final Path TOOL_ROOT =
            Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT =
            Path.of("protos", "tools", "shared");

    @Test
    void completionRemainsInertUntilCallerDispatch(
            @TempDir Path root) throws Exception {
        Path suite = root.resolve("suite.protos");

        String source =
                """
                TestValue: import("std:test/Test")

                tests: Array(
                    TestValue("first", () => {
                        Error().signal()
                    }),
                    TestValue("second", () => {
                        22
                    })
                )
                """;

        Files.writeString(
                suite,
                source,
                StandardCharsets.UTF_8);

        ManualSubmission submission =
                new ManualSubmission();

        ProtosBundledToolModuleResolver resolver =
                resolver();

        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(CORE, resolver);

        ProtosActivation activation =
                prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost =
                        ProtosPolyglotRuntimeHost.open();
                ProtosTestLogicalCaseExecutionFacility facility =
                        ProtosTestLogicalCaseExecutionFacility.install(
                                activation,
                                CORE,
                                resolver,
                                runtimeHost,
                                submission)) {

            ProtosFutureValue future =
                    invoke(
                            prelude,
                            activation,
                            suite,
                            source,
                            List.of("first", "second"),
                            "second");

            assertEquals(
                    ProtosFutureValue.State.PENDING,
                    future.state());
            assertEquals(1, submission.queuedCount());

            assertTrue(submission.runNext());

            assertEquals(
                    ProtosFutureValue.State.PENDING,
                    future.state());
            assertEquals(
                    1,
                    activation.executionDomain().runnableCount());

            assertTrue(
                    activation.executionDomain().dispatchOne());

            ProtosObjectValue completion =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            future.resolvedValue().orElseThrow());

            ProtosStringValue phase =
                    assertInstanceOf(
                            ProtosStringValue.class,
                            completion
                                    .readLocalSlot("phase")
                                    .orElseThrow());

            assertEquals(
                    "case-execution",
                    phase.value());

            ProtosObjectValue observation =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            completion
                                    .readLocalSlot("observation")
                                    .orElseThrow());

            ProtosStringValue state =
                    assertInstanceOf(
                            ProtosStringValue.class,
                            observation
                                    .readLocalSlot("state")
                                    .orElseThrow());

            assertEquals("completed", state.value());

            ProtosIntegerValue value =
                    assertInstanceOf(
                            ProtosIntegerValue.class,
                            observation
                                    .readLocalSlot("value")
                                    .orElseThrow());

            assertEquals(
                    BigInteger.valueOf(22),
                    value.value());
        }
    }

    @Test
    void preservesRematerializationErrorAsPhaseNotFutureFailure(
            @TempDir Path root) throws Exception {
        Path suite = root.resolve("suite.protos");

        String source =
                """
                TestValue: import("std:test/Test")

                tests: Array(
                    TestValue("first", () => {
                        Error().signal()
                    }),
                    TestValue("second", () => {
                        Error().signal()
                    })
                )
                """;

        Files.writeString(
                suite,
                source,
                StandardCharsets.UTF_8);

        ManualSubmission submission =
                new ManualSubmission();

        ProtosBundledToolModuleResolver resolver =
                resolver();

        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(CORE, resolver);

        ProtosActivation activation =
                prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost =
                        ProtosPolyglotRuntimeHost.open();
                ProtosTestLogicalCaseExecutionFacility facility =
                        ProtosTestLogicalCaseExecutionFacility.install(
                                activation,
                                CORE,
                                resolver,
                                runtimeHost,
                                submission)) {

            ProtosFutureValue future =
                    invoke(
                            prelude,
                            activation,
                            suite,
                            source,
                            List.of("second", "first"),
                            "second");

            assertTrue(submission.runNext());
            assertTrue(
                    activation.executionDomain().dispatchOne());

            assertEquals(
                    ProtosFutureValue.State.RESOLVED,
                    future.state());

            ProtosObjectValue completion =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            future.resolvedValue().orElseThrow());

            ProtosStringValue phase =
                    assertInstanceOf(
                            ProtosStringValue.class,
                            completion
                                    .readLocalSlot("phase")
                                    .orElseThrow());

            assertEquals(
                    "rematerialization-error",
                    phase.value());

            ProtosObjectValue observation =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            completion
                                    .readLocalSlot("observation")
                                    .orElseThrow());

            ProtosStringValue state =
                    assertInstanceOf(
                            ProtosStringValue.class,
                            observation
                                    .readLocalSlot("state")
                                    .orElseThrow());

            assertEquals("failed", state.value());
        }
    }

    private static ProtosFutureValue invoke(
            ProtosPrelude prelude,
            ProtosActivation activation,
            Path sourcePath,
            String source,
            List<String> signature,
            String selector) {
        Object execution =
                activation
                        .context()
                        .readLocalSlot(
                                ProtosTestLogicalCaseExecutionFacility.BOOTSTRAP_SLOT)
                        .orElseThrow();

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
                                new ProtosStringValue(
                                        sourcePath.toString()),
                                new ProtosStringValue(source),
                                signatureValue,
                                new ProtosStringValue(selector)),
                        activation));
    }

    private static ProtosBundledToolModuleResolver resolver() {
        return new ProtosBundledToolModuleResolver(
                "test",
                TOOL_ROOT,
                SHARED_ROOT,
                new ProtosStandardLibraryModuleResolver(
                        STANDARD_LIBRARY));
    }

    private static final class ManualSubmission
            implements ProtosAsyncExactExecutionFacility.Submission {
        private final ArrayDeque<Job> jobs =
                new ArrayDeque<>();

        @Override
        public synchronized ProtosAsyncExactExecutionFacility.Submitted submit(
                Runnable work) {
            Job job =
                    new Job(
                            Objects.requireNonNull(
                                    work,
                                    "work"));
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

            return job != null
                    && job.runIfAccepted();
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

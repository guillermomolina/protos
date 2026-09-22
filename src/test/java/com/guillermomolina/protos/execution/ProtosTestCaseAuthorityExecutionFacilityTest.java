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
 * the specific language governing rights and limitations under the LICENSE.
 */

package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

final class ProtosTestCaseAuthorityExecutionFacilityTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");

    private static final Path STANDARD_LIBRARY =
            Path.of("protos", "lib");

    private static final Path PACKAGE_TOOL_ROOT =
            Path.of("protos", "tools", "package");

    private static final Path CORPUS_ROOT =
            Path.of(
                    "protos",
                    "tests",
                    "package-tool",
                    "resolution-root");

    private static final String SLOT =
            "caseAuthorityExecutionAsync";

    // D133/D134 infrastructure fixture: this facility test exercises the
    // legacy whole-source CaseAuthority execution path directly and only
    // needs any boolean-completing script that reads the provisioned
    // projectTreeFilesystem against the real "root-only" physical authority
    // below. It is deliberately inline rather than read from
    // protos/tests/package-tool/resolution-root/fixtures/root-only.protos, so
    // this infrastructure test stays independent of that corpus's own
    // suite-native Test Tool content and lifecycle.
    private static final String ROOT_ONLY_SOURCE =
            """
            Root: import("self:ResolutionRoot")
            model: Root.assemble(projectTreeFilesystem)

            ok: model.languageCompatibility == "0.1"
            (model.root.packageId == "root").ifFalse(() => { ok = false })
            (model.root.version.text == "1.2.3").ifFalse(() => { ok = false })
            (model.root.compatibility === null).ifFalse(() => { ok = false })
            (model.root.dependencies.size() == 0).ifFalse(() => { ok = false })
            (model.members.size() == 0).ifFalse(() => { ok = false })
            ok
            """;

    @Test
    void asyncProjectTreeAuthorityRematerializesD134EnvelopeInCallerDomain()
            throws Exception {
        Path casesRoot = CORPUS_ROOT.resolve("cases");
        Path authorityRoot = casesRoot.resolve("root-only");

        try (ProtosNioReadOnlyTreeFilesystemBackend probe =
                new ProtosNioReadOnlyTreeFilesystemBackend(authorityRoot)) {
            assumeTrue(
                    probe.secureConfinementAvailable(),
                    "host provider has no SecureDirectoryStream");
        }

        ProtosPrelude packagePrelude = newPackagePrelude();
        ProtosActivation activation =
                packagePrelude.newModuleActivation();
        ManualSubmission submission =
                new ManualSubmission();

        try (ProtosPolyglotRuntimeHost runtimeHost =
                        ProtosPolyglotRuntimeHost.open();
                ProtosTestCaseAuthorityExecutionFacility facility =
                        ProtosTestCaseAuthorityExecutionFacility.install(
                                activation,
                                SLOT,
                                packagePrelude,
                                casesRoot,
                                runtimeHost,
                                submission)) {

            Object execution =
                    activation
                            .context()
                            .readLocalSlot(SLOT)
                            .orElseThrow();

            ProtosFutureValue future =
                    assertInstanceOf(
                            ProtosFutureValue.class,
                            ProtosInvocation.invoke(
                                    execution,
                                    List.of(
                                            new ProtosStringValue(ROOT_ONLY_SOURCE),
                                            projectTreeDescriptor(
                                                    packagePrelude,
                                                    "root-only")),
                                    activation));

            assertEquals(
                    ProtosFutureValue.State.PENDING,
                    future.state());

            assertEquals(1, submission.queuedCount());

            assertTrue(submission.runNext());

            assertEquals(
                    ProtosFutureValue.State.PENDING,
                    future.state(),
                    "host completion must remain inert until caller-domain dispatch");

            assertEquals(
                    1,
                    activation.executionDomain().runnableCount());

            assertTrue(
                    activation.executionDomain().dispatchOne());

            assertEquals(
                    ProtosFutureValue.State.RESOLVED,
                    future.state());

            ProtosObjectValue completion =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            future.resolvedValue().orElseThrow());

            assertSame(
                    ProtosBooleanValue.FALSE,
                    completion
                            .readLocalSlot("infrastructureFailed")
                            .orElseThrow());

            ProtosArrayValue failures =
                    assertInstanceOf(
                            ProtosArrayValue.class,
                            completion
                                    .readLocalSlot("infrastructureFailures")
                                    .orElseThrow());

            assertEquals(
                    BigInteger.ZERO,
                    failures.indexedSize());

            ProtosObjectValue guest =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            completion
                                    .readLocalSlot("guestObservation")
                                    .orElseThrow());

            ProtosStringValue state =
                    assertInstanceOf(
                            ProtosStringValue.class,
                            guest
                                    .readLocalSlot("state")
                                    .orElseThrow());

            assertEquals(
                    "completed",
                    state.value());

            assertSame(
                    ProtosBooleanValue.TRUE,
                    guest
                            .readLocalSlot("value")
                            .orElseThrow());
        }
    }

    private static ProtosArrayValue projectTreeDescriptor(
            ProtosPrelude prelude,
            String fixtureIdentity) {
        ProtosArrayValue descriptor =
                prelude.newArray(
                        List.of(
                                new ProtosStringValue("project-tree"),
                                new ProtosStringValue(fixtureIdentity),
                                new ProtosStringValue("case"),
                                new ProtosStringValue("case")));
        descriptor.freeze();
        return descriptor;
    }

    private static ProtosPrelude newPackagePrelude()
            throws Exception {
        ProtosStandardLibraryModuleResolver standard =
                new ProtosStandardLibraryModuleResolver(
                        STANDARD_LIBRARY);

        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "package",
                        PACKAGE_TOOL_ROOT,
                        PACKAGE_TOOL_ROOT.resolveSibling("shared"),
                        standard);

        return new ProtosCoreBootstrap()
                .bootstrap(
                        CORE,
                        resolver);
    }

    private static final class ManualSubmission
            implements ProtosAsyncExactExecutionFacility.Submission {
        private Job queued;

        @Override
        public synchronized ProtosAsyncExactExecutionFacility.Submitted submit(
                Runnable work) {
            if (queued != null) {
                throw new IllegalStateException(
                        "manual submission already has queued work");
            }

            Job job =
                    new Job(
                            Objects.requireNonNull(
                                    work,
                                    "work"));

            queued = job;
            return job;
        }

        synchronized int queuedCount() {
            return queued == null ? 0 : 1;
        }

        boolean runNext() {
            Job job;

            synchronized (this) {
                job = queued;
                queued = null;
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

            private boolean runIfAccepted() {
                synchronized (this) {
                    if (cancelled || started) {
                        return false;
                    }

                    started = true;
                }

                work.run();
                return true;
            }
        }
    }
}

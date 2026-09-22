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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestCaseAuthorityAttemptBridgeTest {
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

    // D133/D134 infrastructure fixture: this bridge test exercises the legacy
    // whole-source CaseAuthority execution path directly and only needs any
    // boolean-completing script that reads the provisioned
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
    void d133PhysicalProjectTreeAuthorityExecutesOneRealCase()
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

        ProtosCapturedProcessExecution.Request execution =
                ProtosExactExecutionFacility.executionRequest(
                        new ProtosStringValue(ROOT_ONLY_SOURCE),
                        packagePrelude);

        try (ProtosPolyglotRuntimeHost runtimeHost =
                ProtosPolyglotRuntimeHost.open()) {
            ProtosTestCaseAuthorityAttemptBridge bridge =
                    new ProtosTestCaseAuthorityAttemptBridge(
                            runtimeHost);

            ProtosTestCaseAuthorityAttemptCompletion completion =
                    bridge.execute(
                            new ProtosTestCaseAuthorityAttemptBridge.Request(
                                    execution,
                                    casesRoot,
                                    "root-only"));

            assertFalse(completion.infrastructureFailed());

            ProtosCapturedProcessExecution.Result guest =
                    completion.guestObservation().orElseThrow();

            assertSame(
                    ProtosExecutionOutcome.State.COMPLETED,
                    guest.outcome().state());

            assertSame(
                    ProtosBooleanValue.TRUE,
                    guest.outcome().value());
        }
    }

    @Test
    void d133EachExecutionMaterializesFreshPhysicalAuthority()
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

        ProtosCapturedProcessExecution.Request execution =
                ProtosExactExecutionFacility.executionRequest(
                        new ProtosStringValue(ROOT_ONLY_SOURCE),
                        packagePrelude);

        try (ProtosPolyglotRuntimeHost runtimeHost =
                ProtosPolyglotRuntimeHost.open()) {
            ProtosTestCaseAuthorityAttemptBridge bridge =
                    new ProtosTestCaseAuthorityAttemptBridge(
                            runtimeHost);

            ProtosTestCaseAuthorityAttemptCompletion first =
                    bridge.execute(
                            new ProtosTestCaseAuthorityAttemptBridge.Request(
                                    execution,
                                    casesRoot,
                                    "root-only"));

            ProtosTestCaseAuthorityAttemptCompletion second =
                    bridge.execute(
                            new ProtosTestCaseAuthorityAttemptBridge.Request(
                                    execution,
                                    casesRoot,
                                    "root-only"));

            assertFalse(first.infrastructureFailed());
            assertFalse(second.infrastructureFailed());

            assertTrue(first.guestObservation().isPresent());
            assertTrue(second.guestObservation().isPresent());

            assertSame(
                    ProtosBooleanValue.TRUE,
                    first.guestObservation()
                            .orElseThrow()
                            .outcome()
                            .value());

            assertSame(
                    ProtosBooleanValue.TRUE,
                    second.guestObservation()
                            .orElseThrow()
                            .outcome()
                            .value());
        }
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
}

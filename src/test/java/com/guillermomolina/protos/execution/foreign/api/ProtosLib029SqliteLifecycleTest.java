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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Hosted execution of Protos-owned deterministic lifecycle failure assertions. */
class ProtosLib029SqliteLifecycleTest {
    @Test
    void acquisitionAndReleaseFailuresPreserveCustodyAndFirstError() throws Exception {
        runModel("Resources.protos", "sqlite-failure-model.protos");
    }

    @Test
    void borrowedColumnsCopyInOrderAndRejectUncertainNullAndOversizedResults() throws Exception {
        runModel("Borrowed.protos", "sqlite-borrow-model.protos");
    }

    private static void runModel(String production, String assertions) throws Exception {
        try (var fixture = new ProtosExternalForeignProviderTest.Fixture(
                ProtosPolyglotRuntimeHost.openWithForeignProviders(
                        ProtosForeignProviderConfiguration.trustedInProcess(List.of())), true)) {
            // Use the existing initialized hosted execution boundary. Only generic lifecycle
            // support is provisioned; the ABI double and every behavioral assertion are Protos.
            ProtosModuleKey key = ProtosModuleKey.source("lib029-lifecycle");
            ProtosActivation activation = fixture.prelude.newModuleActivation(
                    fixture.root.actorModuleState(), key, fixture.prelude.newExecutionContext(),
                    fixture.root.executionDomain());
            activation.createCurrentLocalSlotForRuntime(
                    "terminal", ProtosHttpAsyncFacility.createTerminalFacility());
            String source = Files.readString(Path.of(
                    "protos/lib/sqlite/internal", production)) + "\n"
                    + Files.readString(Path.of(
                            "protos/tests/foreign-provider", assertions));
            ProtosExecutionOutcome outcome = fixture.context.executeModuleSource(
                    ProtosModuleSource.fromCharacters(key, source), activation);
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(),
                    () -> "SQLite " + assertions + " failed: " + outcome.error()
                            + "\nGuest trace: " + outcome.failureDiagnosticTrace()
                                    .map(Object::toString).orElse("unavailable"));
        }
    }
}

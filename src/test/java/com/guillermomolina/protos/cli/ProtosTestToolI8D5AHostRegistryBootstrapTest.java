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

package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosBundledToolModuleResolver;
import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosExecutionOutcome;
import com.guillermomolina.protos.execution.ProtosPolyglotRuntimeHost;
import com.guillermomolina.protos.execution.ProtosRootTaskExecution;
import com.guillermomolina.protos.execution.ProtosSourceCompiler;
import com.guillermomolina.protos.execution.ProtosStandardLibraryModuleResolver;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolI8D5AHostRegistryBootstrapTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");

    @Test
    void productionHostScopeInstallsC1C2WithExplicitEmptyRegistryAndUnknownProviderFailsClosed()
            throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();

        try (ProtosPolyglotRuntimeHost runtimeHost = ProtosPolyglotRuntimeHost.open();
                ProtosTestToolAsyncExecutionScope scope =
                        ProtosTestToolAsyncExecutionScope.install(
                                activation,
                                runtimeHost,
                                prelude,
                                prelude,
                                prelude)) {
            assertTrue(activation.context().hasLocalSlot("resourceExecutionAsync"));
            assertTrue(activation.context().hasLocalSlot("resourceExecutionInspectAsync"));

            String source =
                    """
                    bindings: Array(
                        Array(
                            "gpu",
                            "exclusive",
                            null,
                            "placement",
                            "device/gpu",
                            null
                        )
                    )

                    ordinary: resourceExecutionAsync(
                        "1",
                        bindings
                    ).value()

                    inspected: resourceExecutionInspectAsync(
                        "1",
                        "(subject) => { subject }",
                        bindings
                    ).value()

                    passed: ordinary.infrastructureFailed
                    passed = passed && ordinary.capacitySafe
                    passed = passed && (ordinary.guestObservation === null)
                    passed = passed && (ordinary.infrastructureFailures.size() == 1)

                    passed = passed && inspected.infrastructureFailed
                    passed = passed && inspected.capacitySafe
                    passed = passed && (inspected.guestObservation === null)
                    passed = passed && (inspected.infrastructureFailures.size() == 1)

                    passed
                    """;

            ProtosExecutionOutcome outcome =
                    ProtosRootTaskExecution.execute(
                            new ProtosSourceCompiler().compile(source),
                            activation);

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () ->
                            "I8D5A root outcome="
                                    + outcome.state()
                                    + ", error="
                                    + outcome.error());
            assertSame(ProtosBooleanValue.TRUE, outcome.value());
        }
    }
}

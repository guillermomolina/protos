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
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolFloatBitsExpectationsTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");

    @Test
    void exactFloatBitsPolicyAndNormalMismatchesAreOwnedByProtos()
            throws Exception {
        ProtosExecutionOutcome outcome =
                execute("tool002-d3c2c-float-bits.protos", false);
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void detachedFreshProcessPreservesExactFloatBitsIncludingSignedZero()
            throws Exception {
        ProtosExecutionOutcome outcome =
                execute("tool002-d3c2c-detached-float-bits.protos", true);
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void rawNanPatternFailsClosedBecauseNanUsesFloatNanPolicy()
            throws Exception {
        assertEquals(
                ProtosExecutionOutcome.State.FAILED,
                execute("tool002-d3c2c-nan-raw-rejected.protos", false)
                        .state());
    }

    @Test
    void sequentialRunnerSelectsFloatBitsButStillSkipsFuturePolicy()
            throws Exception {
        ProtosExecutionOutcome outcome =
                execute("tool002-d3c2c-runner-selection.protos", false);
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    private static ProtosExecutionOutcome execute(
            String file,
            boolean withExecution)
            throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(
                                STANDARD_LIBRARY));
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        if (withExecution) {
            ProtosExactExecutionFacility.install(activation);
        }

        Path fixture = Path.of("protos", "tests", "tooling", file);
        return ProtosRootTaskExecution.execute(
                new ProtosSourceCompiler()
                        .compile(
                                Files.readString(
                                        fixture,
                                        StandardCharsets.UTF_8)),
                activation);
    }
}

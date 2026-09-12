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

final class ProtosTestToolSimpleExpectationsTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path POSITIVE_FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-d3a2-simple-expectations.protos");
    private static final Path UNSUPPORTED_FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-d3a2-unsupported-expectation.protos");
    private static final Path MALFORMED_FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-d3a2-malformed-expected.protos");

    @Test
    void simpleExpectationPolicyAndNormalMismatchesAreOwnedByProtos()
            throws Exception {
        Fixture fixture = fixture();

        ProtosExecutionOutcome outcome =
                executeFixture(POSITIVE_FIXTURE, fixture.activation());

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void unsupportedExpectationKindFailsClosedAsToolPolicyError()
            throws Exception {
        Fixture fixture = fixture();

        ProtosExecutionOutcome outcome =
                executeFixture(UNSUPPORTED_FIXTURE, fixture.activation());

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
    }

    @Test
    void malformedExpectedIntegerFailsClosedAsToolPolicyError()
            throws Exception {
        Fixture fixture = fixture();

        ProtosExecutionOutcome outcome =
                executeFixture(MALFORMED_FIXTURE, fixture.activation());

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
    }

    private static ProtosExecutionOutcome executeFixture(
            Path fixture,
            ProtosActivation activation)
            throws Exception {
        return ProtosRootTaskExecution.execute(
                new ProtosSourceCompiler()
                        .compile(
                                Files.readString(
                                        fixture,
                                        StandardCharsets.UTF_8)),
                activation);
    }

    private static Fixture fixture() throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        TOOL_ROOT.resolveSibling("shared"),
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosExactExecutionFacility.install(activation);
        return new Fixture(activation);
    }

    private record Fixture(ProtosActivation activation) {}
}

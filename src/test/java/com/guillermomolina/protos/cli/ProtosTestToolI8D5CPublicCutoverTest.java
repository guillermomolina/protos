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
import com.guillermomolina.protos.execution.ProtosRootTaskExecution;
import com.guillermomolina.protos.execution.ProtosSourceCompiler;
import com.guillermomolina.protos.execution.ProtosStandardLibraryModuleResolver;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosTestToolI8D5CPublicCutoverTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT = Path.of("protos", "tools", "shared");
    private static final Path FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-i8d5c-public-cutover.protos");
    private static final Path MAIN = TOOL_ROOT.resolve("Main.protos");

    @Test
    void bundledProtosOwnsNPlanAggregationAndD114AbortProjection() throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        SHARED_ROOT,
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        ProtosExecutionOutcome outcome =
                ProtosRootTaskExecution.execute(
                        new ProtosSourceCompiler()
                                .compile(Files.readString(FIXTURE, StandardCharsets.UTF_8)),
                        prelude.newModuleActivation());

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () ->
                        "I8D5C fixture outcome="
                                + outcome.state()
                                + ", error="
                                + outcome.error());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void publicMainUsesD108ForAllFourOwnedPlansAndReturnsOneFinalOutcome() throws Exception {
        String main = Files.readString(MAIN, StandardCharsets.UTF_8);

        assertEquals(4, occurrences(main, "Runner.runD108WithResources("));
        assertEquals(0, occurrences(main, "Runner.runBounded("));
        assertTrue(main.contains("Runner.testRunOutcomeCompletedAcrossRuns("));
        assertTrue(main.contains("Runner.testRunOutcomeInfrastructureAbortedAcrossInvocation("));
        assertTrue(main.contains("resourceExecutionAsync"));
        assertTrue(main.contains("actorResourceExecutionAsync"));
        assertTrue(main.contains("groupResourceExecutionAsync"));
        assertTrue(main.contains("packageResourceExecutionAsync"));
        assertTrue(main.trim().endsWith("finalOutcome"));
    }

    @Test
    void javaConsumesOnlyBundledProtosSelectedExitClassification() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        ProtosArrayValue completed0 =
                array(
                        prelude,
                        new ProtosStringValue("completed"),
                        ProtosNullValue.INSTANCE,
                        ProtosNullValue.INSTANCE,
                        new ProtosIntegerValue(BigInteger.ZERO));
        ProtosArrayValue completed1 =
                array(
                        prelude,
                        new ProtosStringValue("completed"),
                        ProtosNullValue.INSTANCE,
                        ProtosNullValue.INSTANCE,
                        new ProtosIntegerValue(BigInteger.ONE));

        ProtosArrayValue attempts =
                array(prelude, new ProtosStringValue("infra-a"));
        ProtosArrayValue cutover =
                array(
                        prelude,
                        new ProtosStringValue("later-a"),
                        new ProtosStringValue("later-b"));
        ProtosArrayValue abortPayload =
                array(
                        prelude,
                        array(prelude),
                        new ProtosIntegerValue(BigInteger.ZERO),
                        attempts,
                        cutover,
                        new ProtosIntegerValue(BigInteger.ONE));
        ProtosArrayValue aborted =
                array(
                        prelude,
                        new ProtosStringValue("infrastructure-aborted"),
                        ProtosNullValue.INSTANCE,
                        abortPayload,
                        new ProtosIntegerValue(BigInteger.valueOf(3)));

        assertEquals(0, ProtosCli.testToolExitCodeForRuntime(completed0, err));
        assertEquals(1, ProtosCli.testToolExitCodeForRuntime(completed1, err));
        assertEquals(3, ProtosCli.testToolExitCodeForRuntime(aborted, err));

        String report = errBytes.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("Test infrastructure aborted"));
        assertTrue(report.contains("infrastructure attempts: 1"));
        assertTrue(report.contains("cutover not admitted: 2"));
        assertTrue(report.contains("retained unsafe reservations: 1"));
    }

    private static ProtosArrayValue array(ProtosPrelude prelude, Object... values) {
        ProtosArrayValue array = prelude.newArray(List.of(values));
        array.freeze();
        return array;
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}

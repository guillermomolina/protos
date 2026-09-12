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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolJClosureReconciliationTest {
    @Test
    void ciValidatesJavaBeforeRunningThePublicProtosTestToolDirectly() throws Exception {
        String workflow =
                Files.readString(
                        Path.of(".github", "workflows", "tests.yml"),
                        StandardCharsets.UTF_8);

        int javaFirst = workflow.indexOf("- name: Run impact-aware tests");
        int checkoutBuild = workflow.indexOf("- name: Build checkout CLI for Protos Test Tool");
        int protosSecond = workflow.indexOf("- name: Run Protos Test Tool directly");

        assertTrue(javaFirst >= 0, "Java/runtime validation stage must remain explicit");
        assertTrue(
                checkoutBuild > javaFirst,
                "the checkout CLI build for Test Tool must run after Java/runtime validation");
        assertTrue(
                protosSecond > checkoutBuild,
                "the public Protos Test Tool must run directly after its checkout CLI build");
        assertTrue(
                workflow.contains("python3 scripts/publication_validation.py"),
                "the Java-first stage must keep the repository publication validator");
        assertTrue(
                workflow.contains("mvn -DskipTests package"),
                "the second-stage path must build the checkout CLI without rerunning JUnit");
        assertTrue(
                workflow.contains("bin/protos test --jobs 2"),
                "CI must execute the public Test Tool directly through the checkout launcher");
        assertFalse(
                workflow.contains("-Dprotos.testToolCheckpoint=true"),
                "CI must not route the complete Test Tool corpus through a JUnit property");
        assertFalse(
                workflow.contains(
                        "-Dtest=ProtosCliTest#testSubcommandRunsBundledProtosToolThroughCommonBootstrap"),
                "CI must not route the complete Test Tool corpus through ProtosCliTest");
    }

    @Test
    void bundledProtosRetainsInvocationPolicyAndJavaOnlyConsumesItsClassification()
            throws Exception {
        String main =
                Files.readString(
                        Path.of("protos", "tools", "test", "Main.protos"),
                        StandardCharsets.UTF_8);
        String cli =
                Files.readString(
                        Path.of(
                                "src",
                                "main",
                                "java",
                                "com",
                                "guillermomolina",
                                "protos",
                                "cli",
                                "ProtosCli.java"),
                        StandardCharsets.UTF_8);

        assertEquals(4, occurrences(main, "Runner.runD108WithResources("));
        assertTrue(main.contains("Runner.testRunOutcomeCompletedAcrossRuns("));
        assertTrue(main.contains("Runner.testRunOutcomeInfrastructureAbortedAcrossInvocation("));
        assertTrue(main.trim().endsWith("finalOutcome"));

        assertTrue(cli.contains("return testToolExitCodeForRuntime(toolResult, err);"));
        assertTrue(cli.contains("if (status.equals(\"completed\"))"));
        assertTrue(cli.contains("if (!status.equals(\"infrastructure-aborted\") || exitCode != 3)"));
        assertFalse(cli.contains("Runner.testRunOutcomeCompletedAcrossRuns("));
        assertFalse(cli.contains("Runner.testRunOutcomeInfrastructureAbortedAcrossInvocation("));
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

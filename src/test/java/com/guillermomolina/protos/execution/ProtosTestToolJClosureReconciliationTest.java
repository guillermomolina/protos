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
    void ciWorkflowMatchesExplicitSuspensionOrActiveClosureContract() throws Exception {
        String workflow =
                Files.readString(
                        Path.of(".github", "workflows", "tests.yml"),
                        StandardCharsets.UTF_8);
        String makefile =
                Files.readString(
                        Path.of("Makefile"),
                        StandardCharsets.UTF_8);

        boolean suspended =
                workflow.contains("name: CI (suspended)")
                        || workflow.contains("GITHUB017 / #492");

        if (suspended) {
            assertTrue(
                    workflow.contains("name: CI (suspended)"),
                    "GITHUB017 suspension must be explicit in the workflow name");
            assertTrue(
                    workflow.contains("workflow_dispatch:"),
                    "the suspended workflow must remain manually inspectable");
            assertFalse(
                    workflow.contains("\n  push:\n"),
                    "GITHUB017 suspension must not retain an automatic push trigger");
            assertFalse(
                    workflow.contains("\n  pull_request:\n"),
                    "GITHUB017 suspension must not retain an automatic pull-request trigger");
            assertTrue(
                    workflow.contains("AUTOMATIC_TEST_CI=SUSPENDED"),
                    "the explanatory stub must publish the suspension state");
            assertTrue(
                    workflow.contains("BLOCKED_BY=TOOL005/#468"),
                    "the explanatory stub must retain the TOOL005 blocker authority");
            assertTrue(
                    workflow.contains("NO_TESTS_EXECUTED=YES"),
                    "the explanatory stub must not pretend to execute tests");
            assertFalse(
                    workflow.contains("- name: Run impact-aware tests"),
                    "suspended CI must not execute Java/runtime validation");
            assertFalse(
                    workflow.contains("- name: Build checkout CLI for Protos Test Tool"),
                    "suspended CI must not build the Test Tool checkout path");
            assertFalse(
                    workflow.contains("- name: Run Protos Test Tool directly"),
                    "suspended CI must not execute the public Test Tool");
            assertFalse(
                    workflow.contains("python3 scripts/publication_validation.py"),
                    "the suspended workflow must not invoke repository test validation");
            assertFalse(
                    workflow.contains("bin/protos test --jobs 2"),
                    "the suspended workflow must not run the public Test Tool");
            return;
        }

        int repositoryTests = workflow.indexOf("- name: Run repository tests");

        assertTrue(
                repositoryTests >= 0,
                "active CI must expose the repository test stage explicitly");
        assertTrue(
                workflow.contains("make test-budget"),
                "active CI must enforce the approved complete-validation budget");
        assertTrue(
                makefile.contains("test-budget:"),
                "the Makefile must expose the complete-validation budget guard");
        assertTrue(
                makefile.contains("--budget-seconds 180"),
                "the retained complete-validation budget must match PERF009-C owner approval");
        assertTrue(
                makefile.contains("--environment development-container-reference"),
                "the budget must identify its reference execution environment");
        assertTrue(
                makefile.contains("$(MAKE) test JAVA_TEST_JOBS=4 PROTOS_TEST_JOBS=4"),
                "the budget guard must delegate to the canonical complete repository command");
        assertTrue(
                makefile.contains("test: test-java test-protos"),
                "the canonical Makefile test target must retain Java then Protos test ownership");
        assertTrue(
                makefile.contains("bin/protos test --jobs $(PROTOS_TEST_JOBS)"),
                "the Makefile must execute the public Protos Test Tool directly");
        assertFalse(
                workflow.contains("- name: Run impact-aware tests"),
                "active CI must not retain the superseded publication-validator test topology");
        assertFalse(
                workflow.contains("- name: Build checkout CLI for Protos Test Tool"),
                "active CI must not duplicate the Makefile checkout-build policy");
        assertFalse(
                workflow.contains("- name: Run Protos Test Tool directly"),
                "active CI must not duplicate Protos Test Tool execution outside the Makefile");
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

        assertEquals(1, occurrences(main, "Runner.runD108WithResources("));
        assertTrue(main.contains("SuiteGraph.flattenLeaves(RepositorySuite.root)"));
        assertTrue(main.contains("testExecutionRequirementBindings.slotValue(requirement)"));
        assertFalse(main.contains("actorExecutionAsync"));
        assertFalse(main.contains("groupExecutionAsync"));
        assertFalse(main.contains("packageExecutionAsync"));
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

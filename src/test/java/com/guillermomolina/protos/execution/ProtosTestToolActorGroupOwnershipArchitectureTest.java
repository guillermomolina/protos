/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolActorGroupOwnershipArchitectureTest {
    private static final Path LEGACY_ACTOR =
            Path.of(
                    "src",
                    "test",
                    "java",
                    "com",
                    "guillermomolina",
                    "protos",
                    "conformance",
                    "ProtosActorLanguageConformanceTest.java");
    private static final Path LEGACY_GROUP =
            Path.of(
                    "src",
                    "test",
                    "java",
                    "com",
                    "guillermomolina",
                    "protos",
                    "conformance",
                    "ProtosGroupLanguageConformanceTest.java");
    private static final Path MAIN = Path.of("protos", "tools", "test", "Main.protos");
    private static final Path RUNNER = Path.of("protos", "tools", "test", "Runner.protos");
    private static final Path REPOSITORY_SUITE =
            Path.of("protos", "tools", "test", "RepositorySuite.protos");
    private static final Path REQUIREMENT_REGISTRY =
            Path.of(
                    "src",
                    "main",
                    "java",
                    "com",
                    "guillermomolina",
                    "protos",
                    "cli",
                    "ProtosTestExecutionRequirementRegistry.java");
    private static final Path G2_JAVA =
            Path.of(
                    "src",
                    "test",
                    "java",
                    "com",
                    "guillermomolina",
                    "protos",
                    "execution",
                    "ProtosTestToolActorFullCorpusTest.java");
    private static final Path G3_JAVA =
            Path.of(
                    "src",
                    "test",
                    "java",
                    "com",
                    "guillermomolina",
                    "protos",
                    "execution",
                    "ProtosTestToolGroupFullCorpusTest.java");
    private static final Path G2_FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-g2-actor-full-corpus.protos");
    private static final Path G3_FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-g3-group-full-corpus.protos");

    @Test
    void duplicateJavaActorAndGroupCorpusPolicyOwnersStayRetired() {
        assertFalse(Files.exists(LEGACY_ACTOR));
        assertFalse(Files.exists(LEGACY_GROUP));
    }

    @Test
    void bundledProtosRetainsCompleteActorAndGroupCorpusOwnershipEvidence()
            throws Exception {
        String main = Files.readString(MAIN, StandardCharsets.UTF_8);
        String runner = Files.readString(RUNNER, StandardCharsets.UTF_8);
        String repositorySuite =
                Files.readString(REPOSITORY_SUITE, StandardCharsets.UTF_8);
        String requirementRegistry =
                Files.readString(REQUIREMENT_REGISTRY, StandardCharsets.UTF_8);
        String g2Java = Files.readString(G2_JAVA, StandardCharsets.UTF_8);
        String g3Java = Files.readString(G3_JAVA, StandardCharsets.UTF_8);
        String g2Fixture = Files.readString(G2_FIXTURE, StandardCharsets.UTF_8);
        String g3Fixture = Files.readString(G3_FIXTURE, StandardCharsets.UTF_8);

        assertTrue(main.contains("SuiteGraph.flattenLeaves(RepositorySuite.root)"));
        assertTrue(main.contains("testExecutionRequirementBindings.slotValue(requirement)"));
        assertTrue(main.contains("binding.resourceExecutionAsync"));
        assertFalse(main.contains("actorExecutionAsync"));
        assertFalse(main.contains("groupExecutionAsync"));

        assertTrue(repositorySuite.contains("\"protos/actor\""));
        assertTrue(repositorySuite.contains("\"protos/test/actor\""));
        assertTrue(repositorySuite.contains("\"protos/group\""));
        assertTrue(repositorySuite.contains("\"protos/test/group\""));

        assertTrue(requirementRegistry.contains("\"protos/test/actor\""));
        assertTrue(requirementRegistry.contains("\"actorFilesystem\""));
        assertTrue(requirementRegistry.contains("\"actorExecutionAsync\""));
        assertTrue(requirementRegistry.contains("\"actorResourceExecutionAsync\""));
        assertTrue(requirementRegistry.contains("\"protos/test/group\""));
        assertTrue(requirementRegistry.contains("\"groupFilesystem\""));
        assertTrue(requirementRegistry.contains("\"groupExecutionAsync\""));
        assertTrue(requirementRegistry.contains("\"groupResourceExecutionAsync\""));

        assertTrue(runner.contains("(kind === \"future-integer-one-of\").ifTrue"));

        assertTrue(
                g2Java.contains(
                        "bundledRunnerOwnsCompleteRetainedActorCorpusWithoutTestScheduler"));
        assertTrue(
                g3Java.contains(
                        "bundledRunnerOwnsCompleteRetainedGroupCorpusWithoutSelectingRoutingMember"));
        assertFalse(g2Java.contains("ManualExecutor"));
        assertFalse(g3Java.contains("ManualExecutor"));
        assertFalse(g2Java.contains("new ProtosStandardActorProtocol"));
        assertFalse(g3Java.contains("new ProtosStandardActorProtocol"));

        for (String fixture : new String[] {g2Fixture, g3Fixture}) {
            assertTrue(fixture.contains("Runner.runSelectedCount(run) == cases.size()"));
            assertTrue(fixture.contains("Runner.runPassedCount(run) == cases.size()"));
            assertTrue(fixture.contains("Runner.runSkippedCount(run) == 0"));
            assertTrue(fixture.contains("Runner.runAllPassed(run)"));
        }
    }
}

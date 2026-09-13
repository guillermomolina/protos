/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolTool004CProgressPresentationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT = Path.of("protos", "tools", "shared");
    private static final Path FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool004-c-progress-presentation.protos");
    private static final Path MAIN = TOOL_ROOT.resolve("Main.protos");
    private static final Path PROGRESS = TOOL_ROOT.resolve("Progress.protos");

    @Test
    void d120CandidateERendersBoundedMilestonesFailuresAndPhaseSummaries()
            throws Exception {
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
                        "TOOL004-C root outcome="
                                + outcome.state()
                                + ", error="
                                + outcome.error());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());

        String progress = Files.readString(PROGRESS, StandardCharsets.UTF_8);
        assertTrue(progress.contains("beginPlan: (phaseName, plan, emitLine) => {"));
        assertTrue(progress.contains("observer: (state) => {"));
        assertTrue(progress.contains("finishPhase: (state, infrastructureAborted) => {"));
        assertTrue(progress.contains("finishInvocation: (states, status, emitLine) => {"));
        assertTrue(progress.contains("\"FAIL\""));
        assertTrue(progress.contains("\"INFRA\""));

        String main = Files.readString(MAIN, StandardCharsets.UTF_8);
        assertTrue(main.contains("Progress: import(\"self:Progress\")"));
        assertTrue(
                main.contains(
                        "TextWriter(process.stderr(), process.stderrEncoding())"));
        assertEquals(4, occurrences(main, "startProgress("));
        assertEquals(4, occurrences(main, "Progress.observer("));
        assertEquals(4, occurrences(main, "Progress.finishPhase("));
        assertTrue(main.contains("Progress.finishInvocation("));
        assertEquals(4, occurrences(main, "Runner.runD108WithResources("));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}

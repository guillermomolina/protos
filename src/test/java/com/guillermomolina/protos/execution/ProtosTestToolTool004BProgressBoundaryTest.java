/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolTool004BProgressBoundaryTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT = Path.of("protos", "tools", "shared");
    private static final Path FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool004-b-progress-boundary.protos");
    private static final Path RUNNER = TOOL_ROOT.resolve("Runner.protos");
    private static final Path MAIN = TOOL_ROOT.resolve("Main.protos");

    @Test
    void d120TerminalObserverSeesOnlyTerminalAttemptsWithoutChangingDefaultScheduling()
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
                        "TOOL004-B root outcome="
                                + outcome.state()
                                + ", error="
                                + outcome.error());
        assertSame(ProtosBooleanValue.TRUE, outcome.value());

        String runner = Files.readString(RUNNER, StandardCharsets.UTF_8);
        assertTrue(runner.contains("terminalObserver = null"));
        assertTrue(runner.contains("terminalObserver("));
        assertTrue(runner.contains("infrastructureFailed"));

        // TOOL004-B is boundary-only. Main does not render progress until TOOL004-C.
        String main = Files.readString(MAIN, StandardCharsets.UTF_8);
        assertFalse(main.contains("terminalObserver"));
        assertFalse(main.contains("process.stderr()"));
    }
}

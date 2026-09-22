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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosProcessSnapshotExecutionTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");

    // Legacy-route source fixture kept as an inline literal rather than read from
    // protos/tests/conformance/process/snapshot-identity.protos: TOOL009-C migrated that
    // file to a suite-native Test Tool corpus member whose raw module completion is no
    // longer a boolean, so this whole-source legacy execution route needs its own copy of
    // the original behavior it exercises.
    private static final String SNAPSHOT_IDENTITY_SOURCE =
            "args1: process.args()\n"
                    + "args2: process.args()\n"
                    + "otherArgs: otherProcess.args()\n"
                    + "\n"
                    + "environment1: process.environment()\n"
                    + "environment2: process.environment()\n"
                    + "otherEnvironment: otherProcess.environment()\n"
                    + "\n"
                    + "(args1 === args2) &&\n"
                    + "    (args1 !== otherArgs) &&\n"
                    + "    (environment1 === environment2) &&\n"
                    + "    (environment1 !== otherEnvironment)";

    @Test
    void createsFreshEquivalentBootstrapForEveryExecution() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);

        ProtosExecutionOutcome firstArguments =
                ProtosProcessSnapshotExecution.execute(
                        "process.args()",
                        prelude);
        ProtosExecutionOutcome secondArguments =
                ProtosProcessSnapshotExecution.execute(
                        "process.args()",
                        prelude);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                firstArguments.state());
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                secondArguments.state());
        assertNotSame(firstArguments.value(), secondArguments.value());

        ProtosExecutionOutcome identity =
                ProtosProcessSnapshotExecution.execute(
                        SNAPSHOT_IDENTITY_SOURCE,
                        prelude);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                identity.state());
        assertSame(ProtosBooleanValue.TRUE, identity.value());
    }
}

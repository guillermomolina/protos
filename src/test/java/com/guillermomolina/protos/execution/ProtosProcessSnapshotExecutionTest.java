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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosProcessSnapshotExecutionTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");
    private static final Path SNAPSHOT_IDENTITY =
            Path.of(
                    "protos",
                    "tests",
                    "conformance",
                    "process",
                    "snapshot-identity.protos");

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

        String identityCase =
                Files.readString(
                        SNAPSHOT_IDENTITY,
                        StandardCharsets.UTF_8);
        ProtosExecutionOutcome identity =
                ProtosProcessSnapshotExecution.execute(
                        identityCase,
                        prelude);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                identity.state());
        assertSame(ProtosBooleanValue.TRUE, identity.value());
    }
}

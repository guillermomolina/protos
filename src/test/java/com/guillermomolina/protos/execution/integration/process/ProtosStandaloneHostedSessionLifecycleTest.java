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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosWorkspacePackageApplicationExecution.NetworkGrant;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Observable cleanup of {@link ProtosStandaloneHostedSession} through the package test hooks. */
final class ProtosStandaloneHostedSessionLifecycleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @TempDir Path directory;

    @Test
    void sessionStaysLiveUntilCloseThenReachesCleanTerminalDisposition() throws Exception {
        Path file = directory.resolve("workload.protos");
        Files.writeString(file, "run: () => {\n    1\n}\nrun()\n", StandardCharsets.UTF_8);

        ProtosStandaloneHostedSession session = ProtosStandaloneHostedSession.open(CORE, file);
        ProtosProcessRuntime process = session.processForTesting();
        ProtosPolyglotRuntimeHost runtimeHost = session.runtimeHostForTesting();
        ProtosPolyglotProcessContext processContext = session.processContextForTesting();

        session.invokeTopLevel("run");
        assertEquals(ProtosProcessRuntime.LifecycleState.RUNNING, process.lifecycleState());
        assertFalse(processContext.isClosedForTesting());
        assertEquals(1, runtimeHost.activeProcessContextCountForTesting());

        session.close();

        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.lifecycleState());
        assertTrue(processContext.isClosedForTesting());
        assertEquals(0, runtimeHost.activeProcessContextCountForTesting());
    }

    @Test
    void defaultSessionLeavesNetworkAbsentWithoutInitializingNetworkHost() throws Exception {
        Path file = directory.resolve("network.protos");
        Files.writeString(file, "network\n", StandardCharsets.UTF_8);

        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(CORE, file)) {
            assertEquals(ProtosExecutionOutcome.State.FAILED, session.initialOutcome().state());
            assertFalse(session.runtimeHostForTesting().networkHostInitializedForTesting());
        }
    }

    @Test
    void explicitGrantProvisionsNetworkFromSessionPreludeOnSessionRuntimeHost() throws Exception {
        Path file = directory.resolve("network.protos");
        Files.writeString(
                file,
                "probe: () => {\n    [network, Network]\n}\n[network, Network]\n",
                StandardCharsets.UTF_8);

        ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(
                        CORE,
                        file,
                        List.of(),
                        InputStream.nullInputStream(),
                        OutputStream.nullOutputStream(),
                        OutputStream.nullOutputStream(),
                        NetworkGrant.HOST_NETWORK);
        ProtosProcessRuntime process = session.processForTesting();
        ProtosPolyglotRuntimeHost runtimeHost = session.runtimeHostForTesting();
        ProtosPolyglotProcessContext processContext = session.processContextForTesting();
        try {
            ProtosExecutionOutcome initial = session.initialOutcome();
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, initial.state());
            ProtosArrayValue result = assertInstanceOf(ProtosArrayValue.class, initial.value());
            ProtosNetworkCapabilityValue network =
                    assertInstanceOf(
                            ProtosNetworkCapabilityValue.class, result.indexedAt(0));
            assertSame(result.indexedAt(1), network.representedDelegationParent(null));

            // The granted capability is the module's binding, observed again by a later call.
            ProtosArrayValue again =
                    assertInstanceOf(
                            ProtosArrayValue.class, session.invokeTopLevel("probe").value());
            assertSame(network, again.indexedAt(0));

            assertTrue(runtimeHost.networkHostInitializedForTesting());
            assertSame(processContext, process.executionHostForRuntime().orElseThrow());
            assertEquals(1, runtimeHost.activeProcessContextCountForTesting());
            assertEquals(ProtosProcessRuntime.LifecycleState.RUNNING, process.lifecycleState());
        } finally {
            session.close();
        }

        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, process.lifecycleState());
        assertTrue(processContext.isClosedForTesting());
        assertEquals(0, runtimeHost.activeProcessContextCountForTesting());
        // The session's RuntimeHost, which owns the provisioned Network plane, is closed too.
        assertThrows(IllegalStateException.class, runtimeHost::actorSchedulerForRuntime);
    }
}

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

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosRootActorServiceBootstrapIntegrationTest.ServiceOutput;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ClockGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.EntropyGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ResolverGrant;
import com.guillermomolina.protos.execution.ProtosWorkspacePackageApplicationExecution.NetworkGrant;
import com.guillermomolina.protos.runtime.ProtosClockCapabilityValue;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** I163: RootActor Clock sample broker across ordinary ActorRef messages. */
@Timeout(value = 15, unit = TimeUnit.SECONDS)
final class ProtosRootActorServiceClockIntegrationTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");

    private static final String GRANTED_SERVICE =
            """
            ProcessStreams: import("std:io/process_streams")
            out: ProcessStreams.stdoutWriter(process)
            rootHadClock: context.hasSlot("clock")
            service: () => {
                self: Actor.current()
                client: Actor.spawn("./clock-client.protos", "start")
                client.send("run", self)
                {
                    sample: () => {
                        clock.epochNanoseconds()
                    }
                    attemptGrantTransfer: (target) => {
                        NonTransferableValue.handle(
                            () => {
                                target.send("leak", clock)
                                false
                            },
                            (error) => { true }
                        )
                    }
                    rootHasClock: () => {
                        rootHadClock
                    }
                    report: (line) => {
                        out.writeLine(line).value()
                        null
                    }
                }
            }
            """;

    private static final String DENIED_SERVICE =
            """
            ProcessStreams: import("std:io/process_streams")
            out: ProcessStreams.stdoutWriter(process)
            rootHadClock: context.hasSlot("clock")
            service: () => {
                self: Actor.current()
                client: Actor.spawn("./clock-client.protos", "start")
                client.send("probe", self)
                {
                    rootHasClock: () => {
                        rootHadClock
                    }
                    report: (line) => {
                        out.writeLine(line).value()
                        null
                    }
                }
            }
            """;

    private static final String CLIENT =
            """
            ambientClock: context.hasSlot("clock")
            ambientNetwork: context.hasSlot("network")
            start: () => {
                {
                    run: (service) => {
                        sample: service.request("sample").value()

                        // The root may send completed Integer samples, but attempting to
                        // send the grant itself must fail before delivery.
                        denied: service.request(
                            "attemptGrantTransfer",
                            Actor.current()
                        ).value()

                        correct: !ambientClock &&
                            !ambientNetwork &&
                            service.request("rootHasClock").value() &&
                            denied &&
                            Integer.recognizes(sample)

                        result: "failed"
                        if(correct) {
                            result = "ok"
                        }
                        service.send("report", "clock-broker " + result)
                        null
                    }
                    probe: (service) => {
                        isolated: !ambientClock &&
                            !service.request("rootHasClock").value()
                        result: "failed"
                        if(isolated) {
                            result = "ok"
                        }
                        service.send("report", "clock-denied " + result)
                        null
                    }
                    leak: (grant) => {
                        Error().signal()
                    }
                }
            }
            """;

    @TempDir Path directory;

    @Test
    void authorizedRootBrokersAnIntegerSampleWithoutTransferringGrant()
            throws Exception {
        Path entry = write("service.protos", GRANTED_SERVICE);
        write("clock-client.protos", CLIENT);
        ServiceOutput output = new ServiceOutput();
        ProtosClockCapabilityValue grant;

        try (ProtosRootActorServiceSession session =
                open(entry, output, NetworkGrant.NONE,
                        ClockGrant.HOST_CLOCK)) {
            grant = session.processForTesting()
                    .rootClockForRuntime().orElseThrow();

            assertFalse(grant.retiredForRuntime());
            assertTrue(session.processForTesting()
                    .rootNetworkForRuntime().isEmpty());

            assertEquals("clock-broker ok",
                    output.awaitLine("clock-broker "));
            assertFalse(session.isTerminated());
        }

        assertTrue(grant.retiredForRuntime());
    }

    @Test
    void serviceModeDoesNotImplyClock() throws Exception {
        Path entry = write("service.protos", DENIED_SERVICE);
        write("clock-client.protos", CLIENT);
        ServiceOutput output = new ServiceOutput();

        try (ProtosRootActorServiceSession session =
                open(entry, output, NetworkGrant.NONE,
                        ClockGrant.NONE)) {
            assertTrue(session.processForTesting()
                    .rootClockForRuntime().isEmpty());
            assertEquals("clock-denied ok",
                    output.awaitLine("clock-denied "));
        }
    }

    @Test
    void networkAndClockGrantsAreSelectedIndependently()
            throws Exception {
        Path entry = write("service.protos", DENIED_SERVICE);
        write("clock-client.protos", CLIENT);
        ServiceOutput output = new ServiceOutput();

        try (ProtosRootActorServiceSession session =
                open(entry, output, NetworkGrant.HOST_NETWORK,
                        ClockGrant.NONE)) {
            assertTrue(session.processForTesting()
                    .rootNetworkForRuntime().isPresent());
            assertTrue(session.processForTesting()
                    .rootClockForRuntime().isEmpty());
            assertEquals("clock-denied ok",
                    output.awaitLine("clock-denied "));
        }
    }

    private ProtosRootActorServiceSession open(
            Path entry,
            OutputStream output,
            NetworkGrant network,
            ClockGrant clock) throws Exception {
        return ProtosRootActorServiceSession.open(
                CORE,
                entry,
                "service",
                List.of(),
                InputStream.nullInputStream(),
                output,
                OutputStream.nullOutputStream(),
                network,
                ResolverGrant.NONE,
                EntropyGrant.NONE,
                clock);
    }

    private Path write(String name, String contents) throws Exception {
        Path file = directory.resolve(name);
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }
}

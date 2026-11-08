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
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.EntropyGrant;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ResolverGrant;
import com.guillermomolina.protos.execution.ProtosWorkspacePackageApplicationExecution.NetworkGrant;
import com.guillermomolina.protos.runtime.ProtosEntropyCapabilityValue;
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

/** I157: RootActor Entropy broker across ordinary ActorRef messages. */
@Timeout(value = 15, unit = TimeUnit.SECONDS)
final class ProtosRootActorServiceEntropyIntegrationTest {
    private static final Path CORE =
            Path.of("protos", "lib", "core");

    private static final String GRANTED_SERVICE =
            """
            ProcessStreams: import("std:io/process_streams")
            out: ProcessStreams.stdoutWriter(process)
            rootHadEntropy: context.hasSlot("entropy")
            service: () => {
                self: Actor.group(Actor.current())
                client: Actor.spawn("./entropy-client.protos", "start")
                client.send("run", self)
                {
                    bytes: (count) => {
                        // An ordinary service message turn can suspend.
                        entropy.bytes(count).value()
                    }
                    attemptGrantTransfer: (target) => {
                        NonTransferableValue.handle(
                            () => {
                                target.send("leak", entropy)
                                false
                            },
                            (error) => { true }
                        )
                    }
                    rootHasEntropy: () => {
                        rootHadEntropy
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
            rootHadEntropy: context.hasSlot("entropy")
            service: () => {
                self: Actor.group(Actor.current())
                client: Actor.spawn("./entropy-client.protos", "start")
                client.send("probe", self)
                {
                    rootHasEntropy: () => {
                        rootHadEntropy
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
            ambientEntropy: context.hasSlot("entropy")
            ambientNetwork: context.hasSlot("network")
            start: () => {
                {
                    run: (service) => {
                        sixteen: service.request("bytes", 16).value()
                        thirtyTwo: service.request("bytes", 32).value()

                        // The root may send completed Bytes, but attempting to
                        // send the grant itself must fail before delivery.
                        denied: service.request(
                            "attemptGrantTransfer",
                            Actor.current()
                        ).value()

                        correct: !ambientEntropy &&
                            !ambientNetwork &&
                            service.request("rootHasEntropy").value() &&
                            denied &&
                            (sixteen.size() == 16) &&
                            (thirtyTwo.size() == 32)

                        result: "failed"
                        if(correct) {
                            result = "ok"
                        }
                        service.send("report", "entropy-broker " + result)
                        null
                    }
                    probe: (service) => {
                        isolated: !ambientEntropy &&
                            !service.request("rootHasEntropy").value()
                        result: "failed"
                        if(isolated) {
                            result = "ok"
                        }
                        service.send("report", "entropy-denied " + result)
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
    void authorizedRootBrokersBytesWithoutTransferringGrant()
            throws Exception {
        Path entry = write("service.protos", GRANTED_SERVICE);
        write("entropy-client.protos", CLIENT);
        ServiceOutput output = new ServiceOutput();
        ProtosEntropyCapabilityValue grant;

        try (ProtosRootActorServiceSession session =
                open(entry, output, NetworkGrant.NONE,
                        EntropyGrant.HOST_ENTROPY)) {
            grant = session.processForTesting()
                    .rootEntropyForRuntime().orElseThrow();

            assertFalse(grant.retiredForRuntime());
            assertTrue(session.processForTesting()
                    .rootNetworkForRuntime().isEmpty());

            assertEquals("entropy-broker ok",
                    output.awaitLine("entropy-broker "));
            assertFalse(session.isTerminated());
        }

        assertTrue(grant.retiredForRuntime());
    }

    @Test
    void serviceModeDoesNotImplyEntropy() throws Exception {
        Path entry = write("service.protos", DENIED_SERVICE);
        write("entropy-client.protos", CLIENT);
        ServiceOutput output = new ServiceOutput();

        try (ProtosRootActorServiceSession session =
                open(entry, output, NetworkGrant.NONE,
                        EntropyGrant.NONE)) {
            assertTrue(session.processForTesting()
                    .rootEntropyForRuntime().isEmpty());
            assertEquals("entropy-denied ok",
                    output.awaitLine("entropy-denied "));
        }
    }

    @Test
    void networkAndEntropyGrantsAreSelectedIndependently()
            throws Exception {
        Path entry = write("service.protos", DENIED_SERVICE);
        write("entropy-client.protos", CLIENT);
        ServiceOutput output = new ServiceOutput();

        try (ProtosRootActorServiceSession session =
                open(entry, output, NetworkGrant.HOST_NETWORK,
                        EntropyGrant.NONE)) {
            assertTrue(session.processForTesting()
                    .rootNetworkForRuntime().isPresent());
            assertTrue(session.processForTesting()
                    .rootEntropyForRuntime().isEmpty());
            assertEquals("entropy-denied ok",
                    output.awaitLine("entropy-denied "));
        }
    }

    private ProtosRootActorServiceSession open(
            Path entry,
            OutputStream output,
            NetworkGrant network,
            EntropyGrant entropy) throws Exception {
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
                entropy);
    }

    private Path write(String name, String contents) throws Exception {
        Path file = directory.resolve(name);
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }
}

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosRootActorServiceBootstrapIntegrationTest.ServiceOutput;
import com.guillermomolina.protos.execution.ProtosStandaloneHostedExecution.ResolverGrant;
import com.guillermomolina.protos.execution.ProtosWorkspacePackageApplicationExecution.NetworkGrant;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosResolverCapabilityValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * D225-B1 authority (PROCESS_IO.md §22): the RootActor service entry receives exactly the
 * independently granted bootstrap-local capabilities, which never reach imports, child Actors or
 * an ordinary Actor transfer, and which close and Process termination retire. No test performs a
 * name lookup or opens a socket, so neither DNS nor any network is a prerequisite.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
final class ProtosRootActorServiceAuthorityIntegrationTest {
    private static final Path CORE = ProtosRootActorServiceBootstrapIntegrationTest.CORE;

    private static final String SERVICE =
            """
            ProcessStreams: import("std:io/ProcessStreams")
            probe: import("./probe.protos")
            out: ProcessStreams.stdoutWriter(process)
            rootContext: context
            flag: (condition) => {
                text: "0"
                if(condition) {
                    text = "1"
                }
                text
            }
            // Attempts an ordinary Actor transfer of a granted capability.
            transfer: (target, name) => {
                result: "absent"
                if(rootContext.hasSlot(name)) {
                    result = NonTransferableValue.handle(
                        () => {
                            target.send("accept", rootContext.slotValue(name))
                            "transferred"
                        },
                        (error) => { "refused" }
                    )
                }
                result
            }
            worker: null
            service: () => {
                self: Actor.group(Actor.current())
                worker = Actor.spawn("./worker.protos", "start")
                worker.send("run", self)
                behavior: {
                    summary: () => {
                        "network=" + flag(rootContext.hasSlot("network")) + " resolver=" + flag(rootContext.hasSlot("resolver")) + " import=" + flag(probe.ambient) + " sendNetwork=" + transfer(worker, "network") + " sendResolver=" + transfer(worker, "resolver")
                    }
                    // With any application argument the report is followed by an
                    // unhandled Error: a fatal RootActor failure.
                    report: (line) => {
                        out.writeLine(line).value()
                        if(process.args().size() > 0) {
                            Error().signal()
                        }
                        null
                    }
                }
                behavior
            }
            """;

    private static final String PROBE =
            """
            ambient: context.hasSlot("process") || context.hasSlot("filesystem") || context.hasSlot("network") || context.hasSlot("resolver")
            """;

    private static final String WORKER =
            """
            ambient: context.hasSlot("process") || context.hasSlot("filesystem") || context.hasSlot("network") || context.hasSlot("resolver")
            start: () => {
                behavior: {
                    run: (service) => {
                        child: "1"
                        if(ambient) {
                            child = "0"
                        }
                        service.send("report", "authority " + service.request("summary").value() + " childIsolated=" + child)
                        null
                    }
                    accept: (value) => {
                        null
                    }
                }
                behavior
            }
            """;

    @TempDir Path directory;

    @Test
    void neitherGrant() throws Exception {
        assertGrants(NetworkGrant.NONE, ResolverGrant.NONE);
    }

    @Test
    void networkOnly() throws Exception {
        assertGrants(NetworkGrant.HOST_NETWORK, ResolverGrant.NONE);
    }

    @Test
    void resolverOnly() throws Exception {
        assertGrants(NetworkGrant.NONE, ResolverGrant.HOST_RESOLVER);
    }

    @Test
    void bothGrants() throws Exception {
        assertGrants(NetworkGrant.HOST_NETWORK, ResolverGrant.HOST_RESOLVER);
    }

    @Test
    void catalogSourceGrantsNoAuthorityToImportsOrWorkers() throws Exception {
        // The same service, with its imported probe and its worker supplied as app: source.
        Path entry =
                write(
                        "catalog-service.protos",
                        SERVICE.replace("\"./probe.protos\"", "\"app:probe\"")
                                .replace("\"./worker.protos\"", "\"app:worker\""));
        ServiceOutput out = new ServiceOutput();

        try (ProtosRootActorServiceSession session =
                ProtosRootActorServiceSession.open(
                        CORE,
                        entry,
                        "service",
                        List.of(),
                        InputStream.nullInputStream(),
                        out,
                        OutputStream.nullOutputStream(),
                        NetworkGrant.HOST_NETWORK,
                        ResolverGrant.HOST_RESOLVER,
                        Map.of("app:probe", PROBE, "app:worker", WORKER))) {
            // The RootActor holds both grants; the catalog modules and the Worker hold none,
            // and neither grant crosses to the Worker.
            assertEquals(
                    "authority network=1 resolver=1 import=0 sendNetwork=refused"
                            + " sendResolver=refused childIsolated=1",
                    out.awaitLine("authority "));
        }
    }

    @Test
    void processTerminationRetiresTheGrantsBeforeClose() throws Exception {
        Path entry = writeSources();
        ServiceOutput out = new ServiceOutput();
        ProtosRootActorServiceSession session =
                open(
                        entry,
                        List.of("fail-after-report"),
                        out,
                        NetworkGrant.HOST_NETWORK,
                        ResolverGrant.HOST_RESOLVER);
        try {
            ProtosProcessRuntime process = session.processForTesting();
            ProtosResolverCapabilityValue resolver =
                    process.rootResolverForRuntime().orElseThrow();
            out.awaitLine("authority ");

            // The RootActor fails right after reporting; nothing here requests termination.
            session.awaitTermination();

            assertTrue(session.isTerminated());
            assertTrue(resolver.retiredForRuntime(), "Process termination retires its Resolver");
        } finally {
            session.close();
        }
        assertEquals(0, session.runtimeHostForTesting().activeProcessContextCountForTesting());
    }

    private void assertGrants(NetworkGrant network, ResolverGrant resolver) throws Exception {
        Path entry = writeSources();
        ServiceOutput out = new ServiceOutput();
        boolean networkGranted = network == NetworkGrant.HOST_NETWORK;
        boolean resolverGranted = resolver == ResolverGrant.HOST_RESOLVER;

        ProtosRootActorServiceSession session = open(entry, List.of(), out, network, resolver);
        ProtosPolyglotRuntimeHost runtimeHost = session.runtimeHostForTesting();
        ProtosResolverCapabilityValue resolverCapability;
        try {
            ProtosProcessRuntime process = session.processForTesting();
            assertEquals(networkGranted, process.rootNetworkForRuntime().isPresent());
            assertEquals(resolverGranted, process.rootResolverForRuntime().isPresent());
            assertEquals(networkGranted, runtimeHost.networkHostInitializedForTesting());
            assertEquals(resolverGranted ? 1 : 0, runtimeHost.provisionedResolverCountForTesting());
            resolverCapability = process.rootResolverForRuntime().orElse(null);

            assertEquals(
                    "authority network="
                            + (networkGranted ? "1" : "0")
                            + " resolver="
                            + (resolverGranted ? "1" : "0")
                            + " import=0 sendNetwork="
                            + (networkGranted ? "refused" : "absent")
                            + " sendResolver="
                            + (resolverGranted ? "refused" : "absent")
                            + " childIsolated=1",
                    out.awaitLine("authority "));
        } finally {
            session.close();
        }

        assertTrue(session.isTerminated());
        assertEquals(0, runtimeHost.activeProcessContextCountForTesting());
        assertThrows(IllegalStateException.class, runtimeHost::actorSchedulerForRuntime);
        if (resolverCapability != null) {
            assertTrue(resolverCapability.retiredForRuntime(), "close retires the Resolver");
        }
    }

    private Path writeSources() throws IOException {
        write("probe.protos", PROBE);
        write("worker.protos", WORKER);
        return write("service.protos", SERVICE);
    }

    private ProtosRootActorServiceSession open(
            Path entry,
            List<String> arguments,
            OutputStream out,
            NetworkGrant network,
            ResolverGrant resolver)
            throws IOException {
        return ProtosRootActorServiceSession.open(
                CORE,
                entry,
                "service",
                arguments,
                InputStream.nullInputStream(),
                out,
                OutputStream.nullOutputStream(),
                network,
                resolver);
    }

    private Path write(String name, String source) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, source, StandardCharsets.UTF_8);
        return file;
    }
}

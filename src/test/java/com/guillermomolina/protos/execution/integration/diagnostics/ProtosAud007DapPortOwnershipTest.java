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

import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AUD007-B2 F2: DAP port ownership must never leave the operating system.
 *
 * <p>The first case is the retained deterministic proof of the defect the DAP tests used to
 * contain (reserve a port, capture its number, release it, later bind the DAP to that number):
 * ownership is transferred explicitly to a foreign listener between release and bind, so the
 * real DAP cannot own the captured port. The remaining cases pin the repair.
 */
final class ProtosAud007DapPortOwnershipTest {
    private static final int MAX_FOREIGN_CLAIM_ATTEMPTS = 16;
    private static final Pattern DAP_ADDRESS_OPTION =
            Pattern.compile("\\.option\\(\\s*\"dap\"\\s*,\\s*([^)]*?)\\s*\\)");
    private static final String PROOF_FILE = "ProtosAud007DapPortOwnershipTest.java";

    @Test
    void reserveCloseRebindLosesTheCapturedPortToAForeignOwner() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (ServerSocket foreignOwner = claimReleasedReservation(loopback)) {
            int captured = foreignOwner.getLocalPort();

            ProtosGraalDapReadinessAdapter readiness = ProtosDapTestSupport.newReadiness();
            boolean dapOwnsCapturedPort;
            Engine engine = null;
            try {
                engine =
                        Engine.newBuilder(ProtosLanguage.ID)
                                .option("dap", "127.0.0.1:" + captured)
                                .option("dap.Suspend", "false")
                                .option("dap.WaitAttached", "false")
                                .out(readiness)
                                .err(OutputStream.nullOutputStream())
                                .build();
                dapOwnsCapturedPort = publishedPort(readiness) == captured;
            } catch (RuntimeException bindFailure) {
                dapOwnsCapturedPort = false;
            } finally {
                if (engine != null) {
                    engine.close();
                }
            }

            assertFalse(dapOwnsCapturedPort, "the released port number was not owned by the DAP");
            try (Socket client = new Socket(loopback, captured);
                    Socket accepted = foreignOwner.accept()) {
                assertNotNull(accepted, "the foreign owner still holds the captured port");
            }
        }
    }

    @Test
    void ephemeralLoopbackDapPublishesTheOsAssignedPortItOwns() throws Exception {
        ProtosGraalDapReadinessAdapter readiness = ProtosDapTestSupport.newReadiness();
        try (Engine engine =
                Engine.newBuilder(ProtosLanguage.ID)
                        .option("dap", ProtosDapTestSupport.EPHEMERAL_LOOPBACK)
                        .option("dap.Suspend", "false")
                        .option("dap.WaitAttached", "false")
                        .out(readiness)
                        .build()) {
            ProtosPolyglotRuntimeHost.DebugEndpoint endpoint = readiness.requireEndpoint();
            assertTrue(InetAddress.getByName(endpoint.host()).isLoopbackAddress());
            assertTrue(endpoint.port() > 0);
            try (ServerSocket competitor = new ServerSocket()) {
                assertBindRefused(competitor, endpoint.port());
            }
        }
    }

    @Test
    void noTestConfiguresADapAddressOtherThanTheEphemeralLoopback() throws IOException {
        List<String> violations = new ArrayList<>();
        Path root = Path.of("src", "test", "java");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                if (file.getFileName().toString().equals(PROOF_FILE)) {
                    continue;
                }
                Matcher matcher =
                        DAP_ADDRESS_OPTION.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String value = matcher.group(1);
                    if (!value.equals("ProtosDapTestSupport.EPHEMERAL_LOOPBACK")
                            && !value.equals("EPHEMERAL_LOOPBACK")) {
                        violations.add(file + ": dap=" + value);
                    }
                }
            }
        }
        assertEquals(List.of(), violations, "DAP tests must let the OS own port allocation");
    }

    /**
     * Reproduces the old pattern up to the release, then hands the exact released port to a
     * foreign listener. Retrying only covers a third party winning that same race.
     */
    private static ServerSocket claimReleasedReservation(InetAddress loopback) throws IOException {
        for (int attempt = 0; attempt < MAX_FOREIGN_CLAIM_ATTEMPTS; attempt++) {
            int captured;
            try (ServerSocket reservation = new ServerSocket(0, 1, loopback)) {
                captured = reservation.getLocalPort();
            }
            ServerSocket foreign = new ServerSocket();
            try {
                foreign.bind(new InetSocketAddress(loopback, captured), 1);
                return foreign;
            } catch (BindException lostToThirdParty) {
                foreign.close();
            }
        }
        throw new IOException("could not hand a released reservation to a foreign owner");
    }

    private static int publishedPort(ProtosGraalDapReadinessAdapter readiness) {
        try {
            return readiness.requireEndpoint().port();
        } catch (IllegalStateException notPublished) {
            return -1;
        }
    }

    private static void assertBindRefused(ServerSocket competitor, int port) {
        try {
            competitor.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1);
        } catch (IOException refused) {
            return;
        }
        throw new AssertionError("DAP did not own its published port " + port);
    }
}

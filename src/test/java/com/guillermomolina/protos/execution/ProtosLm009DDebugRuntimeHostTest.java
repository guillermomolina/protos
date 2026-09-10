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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;

final class ProtosLm009DDebugRuntimeHostTest {
    @Test
    void realDebugRuntimeHostPublishesOsAllocatedLoopbackEndpoint() throws Exception {
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();

        ProtosPolyglotRuntimeHost.DebugEndpoint endpoint;
        try (ProtosPolyglotRuntimeHost host =
                ProtosPolyglotRuntimeHost.openDebug(diagnostics)) {
            endpoint = host.debugEndpoint();
            assertTrue(InetAddress.getByName(endpoint.host()).isLoopbackAddress());
            assertTrue(endpoint.port() > 0 && endpoint.port() <= 65535);
        }

        assertFalse(
                diagnostics.toString(StandardCharsets.UTF_8)
                        .contains("[Graal DAP] Starting server and listening on"),
                "raw Graal readiness must stay behind the PLAT018 adapter");
    }

    @Test
    void ordinaryRuntimeHostDoesNotBecomeDebugEnabled() {
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            assertThrows(IllegalStateException.class, host::debugEndpoint);
        }
    }

    @Test
    void readinessAdapterHandlesFragmentedIpv4StartupLine() throws Exception {
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        ProtosGraalDapReadinessAdapter adapter =
                new ProtosGraalDapReadinessAdapter(diagnostics);
        byte[] line =
                ("[Graal DAP] Starting server and listening on "
                                + "/127.0.0.1:54321\r\n")
                        .getBytes(StandardCharsets.UTF_8);

        adapter.write(line, 0, 17);
        adapter.write(line, 17, line.length - 17);

        assertEquals(
                new ProtosPolyglotRuntimeHost.DebugEndpoint("127.0.0.1", 54321),
                adapter.requireEndpoint());
        assertEquals("", diagnostics.toString(StandardCharsets.UTF_8));
    }

    @Test
    void readinessAdapterAcceptsBracketedNumericIpv6Loopback() throws Exception {
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        ProtosGraalDapReadinessAdapter adapter =
                new ProtosGraalDapReadinessAdapter(diagnostics);

        adapter.write(
                ("[Graal DAP] Starting server and listening on "
                                + "localhost/[0:0:0:0:0:0:0:1]:54322\n")
                        .getBytes(StandardCharsets.UTF_8));

        ProtosPolyglotRuntimeHost.DebugEndpoint endpoint = adapter.requireEndpoint();
        assertTrue(InetAddress.getByName(endpoint.host()).isLoopbackAddress());
        assertEquals(54322, endpoint.port());
    }

    @Test
    void readinessAdapterRejectsNonLoopbackEndpoint() throws Exception {
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        ProtosGraalDapReadinessAdapter adapter =
                new ProtosGraalDapReadinessAdapter(diagnostics);

        adapter.write(
                ("[Graal DAP] Starting server and listening on "
                                + "/192.0.2.1:54323\n")
                        .getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class, adapter::requireEndpoint);
    }

    @Test
    void nonReadinessGraalInfoIsDiagnosticNotControlOutput() throws Exception {
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        ProtosGraalDapReadinessAdapter adapter =
                new ProtosGraalDapReadinessAdapter(diagnostics);

        adapter.write(
                "[Graal DAP] Client connected on /127.0.0.1:50000\n"
                        .getBytes(StandardCharsets.UTF_8));

        assertTrue(
                diagnostics.toString(StandardCharsets.UTF_8)
                        .contains("[Graal DAP] Client connected on"));
    }
}

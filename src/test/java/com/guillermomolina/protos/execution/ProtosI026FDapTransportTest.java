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

import com.guillermomolina.protos.execution.ProtosDapTestSupport.DapClient;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtosI026FDapTransportTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final String DAP_CLIENT_CONNECTION_THREAD_NAME =
            "DAP client connection thread";

    @Test
    void realGraalVmDapInstrumentCompletesTcpHandshake() throws Exception {
        ProtosGraalDapReadinessAdapter readiness = ProtosDapTestSupport.newReadiness();
        Set<Thread> dapClientConnectionThreadsBefore = dapClientConnectionThreads();
        Context context = null;
        DapClient client = null;
        Thread dapClientConnectionThread = null;

        try {
            context =
                    Context.newBuilder(ProtosLanguage.ID)
                            .option("dap", ProtosDapTestSupport.EPHEMERAL_LOOPBACK)
                            .option("dap.Suspend", "false")
                            .option("dap.WaitAttached", "false")
                            .out(readiness)
                            .build();
            context.initialize(ProtosLanguage.ID);

            client = DapClient.connect(readiness, TIMEOUT);

            int initialize =
                    client.request(
                            "initialize",
                            "{\"adapterID\":\"protos\","
                                    + "\"clientID\":\"protos-i026-f1\","
                                    + "\"clientName\":\"Protos I026-F1\","
                                    + "\"linesStartAt1\":true,"
                                    + "\"columnsStartAt1\":true,"
                                    + "\"pathFormat\":\"path\"}");
            String initializeResponse = client.awaitResponse(initialize, TIMEOUT);
            assertSuccessfulResponse(initializeResponse, initialize, "initialize");
            assertTrue(
                    booleanField(initializeResponse, "supportsConfigurationDoneRequest"),
                    "real GraalVM DAP must advertise configurationDone");

            String initialized = client.awaitEvent("initialized", TIMEOUT);
            assertJsonStringField(initialized, "event", "initialized");

            int attach = client.request("attach", "{}");
            assertSuccessfulResponse(
                    client.awaitResponse(attach, TIMEOUT), attach, "attach");

            dapClientConnectionThread =
                    newDapClientConnectionThread(dapClientConnectionThreadsBefore);

            int configurationDone = client.request("configurationDone", "{}");
            assertSuccessfulResponse(
                    client.awaitResponse(configurationDone, TIMEOUT),
                    configurationDone,
                    "configurationDone");

            int disconnect =
                    client.request(
                            "disconnect",
                            "{\"restart\":false,\"terminateDebuggee\":false}");
            assertSuccessfulResponse(
                    client.awaitResponse(disconnect, TIMEOUT),
                    disconnect,
                    "disconnect");
        } finally {
            if (client != null) {
                client.close();
            }
            if (dapClientConnectionThread != null) {
                awaitDapClientConnectionThreadTermination(dapClientConnectionThread);
            }
            if (context != null) {
                context.close();
            }
        }
    }


    private static Set<Thread> dapClientConnectionThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(thread -> DAP_CLIENT_CONNECTION_THREAD_NAME.equals(thread.getName()))
                .collect(Collectors.toSet());
    }

    private static Thread newDapClientConnectionThread(Set<Thread> threadsBefore) {
        List<Thread> candidates =
                dapClientConnectionThreads().stream()
                        .filter(thread -> !threadsBefore.contains(thread))
                        .collect(Collectors.toList());
        assertEquals(
                1,
                candidates.size(),
                "exactly one new GraalVM DAP client connection thread must belong to this test");
        return candidates.get(0);
    }

    private static void awaitDapClientConnectionThreadTermination(Thread thread)
            throws InterruptedException {
        thread.join(TIMEOUT.toMillis());
        assertFalse(
                thread.isAlive(),
                "GraalVM DAP client connection thread must terminate before Context.close()");
    }

    private static void assertSuccessfulResponse(
            String message, int requestSequence, String command) {
        assertJsonStringField(message, "type", "response");
        assertEquals(
                requestSequence,
                integerField(message, "request_seq"),
                "DAP response must correlate to request");
        assertJsonStringField(message, "command", command);
        assertTrue(
                booleanField(message, "success"),
                () -> "DAP " + command + " failed: " + message);
    }

    private static void assertJsonStringField(
            String message, String name, String expected) {
        String value = stringField(message, name);
        assertEquals(expected, value, () -> "Unexpected DAP message: " + message);
    }

    private static String stringField(String json, String name) {
        Pattern pattern =
                Pattern.compile(
                        "\\\"" + Pattern.quote(name)
                                + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
        Matcher matcher = pattern.matcher(json);
        assertTrue(matcher.find(), () -> "Missing JSON string field " + name + ": " + json);
        return matcher.group(1);
    }

    private static int integerField(String json, String name) {
        Pattern pattern =
                Pattern.compile(
                        "\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(-?[0-9]+)");
        Matcher matcher = pattern.matcher(json);
        assertTrue(matcher.find(), () -> "Missing JSON integer field " + name + ": " + json);
        return Integer.parseInt(matcher.group(1));
    }

    private static boolean booleanField(String json, String name) {
        Pattern pattern =
                Pattern.compile(
                        "\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(json);
        assertTrue(matcher.find(), () -> "Missing JSON boolean field " + name + ": " + json);
        return Boolean.parseBoolean(matcher.group(1));
    }
}

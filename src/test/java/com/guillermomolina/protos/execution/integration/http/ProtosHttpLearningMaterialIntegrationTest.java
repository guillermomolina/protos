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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * DOC012: the shipped HTTP learning material that needs live TCP.
 *
 * <p>The standalone CLI grants no {@code network}, so these programs cannot join the print-based
 * CLI learning-material regression. Each one runs unchanged as the initial module of a standard
 * Polyglot Context that allows host socket access, which provisions the bootstrap-local {@code
 * network}. Thread creation is granted only to programs whose {@code std:http/Host} starts Worker
 * Actors. Handler modules are supplied through the {@code app:} catalog, from which each Worker
 * Actor resolves its own instance. Listeners use {@code port: null}, so no port is chosen by the
 * test; no verdict depends on a sleep; the class timeout bounds every wait, and closing the Context
 * on every path releases any connection or listener a failing program left open.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
final class ProtosHttpLearningMaterialIntegrationTest {
    private static final String CORE = Path.of("protos", "lib", "core").toAbsolutePath().toString();
    private static final Path TUTORIALS = Path.of("protos", "tutorials", "13-http");
    private static final Path EXAMPLES = Path.of("protos", "examples", "io");

    @Test
    void hostAndClientTutorialServesLoopbackRequestsAndDrains() throws IOException {
        assertRuns(
                TUTORIALS.resolve("03-host-and-client-over-tcp.protos"),
                Map.of("app:hello", TUTORIALS.resolve("hello-handler.protos")),
                true);
    }

    @Test
    void getExampleUsesAnApplicationEstablishedConnection() throws IOException {
        assertRuns(EXAMPLES.resolve("http-get.protos"), Map.of(), false);
    }

    @Test
    void boundedHostingExampleServesConcurrentClientsAndDrains() throws IOException {
        assertRuns(
                EXAMPLES.resolve("http-loopback-hosting.protos"),
                Map.of("app:echo", EXAMPLES.resolve("http-echo-handler.protos")),
                true);
    }

    private static void assertRuns(
            Path program, Map<String, Path> handlerModules, boolean spawnsWorkers)
            throws IOException {
        Map<String, String> catalog = new LinkedHashMap<>();
        for (Map.Entry<String, Path> module : handlerModules.entrySet()) {
            catalog.put(module.getKey(), Files.readString(module.getValue(), StandardCharsets.UTF_8));
        }
        Context context =
                Context.newBuilder(ProtosLanguage.ID)
                        .option("protos.CoreRoot", CORE)
                        .allowIO(IOAccess.newBuilder().allowHostSocketAccess(true).build())
                        .allowCreateThread(spawnsWorkers)
                        .build();
        try {
            if (!catalog.isEmpty()) {
                ProtosEmbeddedModules.install(context, catalog);
            }
            String source = Files.readString(program, StandardCharsets.UTF_8);
            assertTrue(
                    context.eval(ProtosLanguage.ID, source).asBoolean(),
                    program + " answered false");
        } finally {
            context.close(true);
        }
    }
}

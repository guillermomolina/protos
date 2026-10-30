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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * I133 (D225-A, HTTP.md §15.13): {@code Host.owningWithFactory} over real Actors and loopback TCP
 * in a standard Polyglot Context. Every Worker builds its handler with its own instance of {@code
 * app:factory} from its own copy of the initialization; {@code app:service} is an application
 * service Actor created by the program, never by a host. Every wait is bounded; no test depends on
 * a sleep for its verdict, on DNS, or on any non-loopback network.
 *
 * <p>A Resolver cannot be provisioned in this embedding; its rejection is the same ordinary Actor
 * transfer rule exercised here for Network, Closures, Futures, listeners and TcpAcceptors.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
final class ProtosHttpHostFactoryIntegrationTest {
    private static final String CORE = Path.of("protos", "lib", "core").toAbsolutePath().toString();
    private static final int SAFETY_MILLIS = (int) TimeUnit.SECONDS.toMillis(5);

    // ------------------------------------------------------------ tests

    @Test
    void aFactoryWorkerAnswersTheHostProtocolOverRealActors() throws Exception {
        try (Fixture x = new Fixture()) {
            // Isolates the Worker side of startup from HostLifecycle; on failure the answer
            // names the stage reached and the Error observed there.
            assertEquals("ok", x.execute("probeFactoryWorker", "make").asString());
        }
    }

    @Test
    void readyFollowsEntryIntoAcceptanceAfterASuspendedConstruction() throws Exception {
        try (Fixture x = new Fixture()) {
            // "registering" suspends inside the factory, so construction completes in a later
            // turn. The Worker's "ready" answers true only when its acceptance service is
            // entered, so a "ready" resuming before "serve" entered would answer false here.
            assertEquals("ok", x.execute("probeFactoryWorker", "registering").asString());
        }
    }

    @Test
    void eachWorkerBuildsItsHandlerOnceFromItsOwnCopy() throws Exception {
        try (Fixture x = new Fixture()) {
            // Two Workers with one connection unit each: two held sessions run in distinct
            // Workers.
            assertTrue(x.call("start", "make", 2, 2), "multi-Worker readiness");
            try (Socket held = x.connect(); Socket other = x.connect()) {
                send(held, post("/payload"));
                send(other, post("/payload"));
                send(other, "x");
                send(held, "x");
                // Each copy is intact, unaffected by the caller's later mutations, and its
                // mutable state is the Worker's own: each Worker counts its first hit.
                assertEquals(body("11"), readAll(other));
                assertEquals(body("11"), readAll(held));
            }
            // Both Workers now hold one hit; either answers the second one.
            assertEquals(body("12"), x.get(x.port, "/payload"));
            for (int index = 0; index < 4; index++) {
                // Factory ran exactly once, in the Worker that runs the handler, not in the
                // creator, with the Worker's own module instance and no ambient authority.
                assertEquals(body("11111"), x.get(x.port, "/factory"), "request " + index);
            }
            assertTrue(x.call("drain"));
            assertTrue(x.call("listenerClosed"));
        }
    }

    @Test
    void theConnectionQuotaStillBoundsAdmission() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", "make", 1, 1));
            try (Socket held = x.connect(); Socket waiting = x.connect()) {
                send(held, post("/"));
                send(waiting, "GET / HTTP/1.1\r\nHost: a\r\n\r\n");
                send(held, "x");
                // Admitted concurrently, either session would have observed a peak of two.
                assertEquals(body("1"), readAll(held));
                assertEquals(body("1"), readAll(waiting));
            }
            assertTrue(x.call("drain"));
        }
    }

    @Test
    void twoHostsShareOneServiceAndStayIndependentlyControllable() throws Exception {
        try (Fixture x = new Fixture()) {
            int otherPort = x.execute("openOther").asInt();
            assertTrue(x.call("start", "make", 1, 4));
            assertTrue(x.call("startOther"));
            assertEquals(body("protos,website"), x.get(x.port, "/projects"));
            assertEquals(body("protos,website"), x.get(otherPort, "/projects"));
            // The ActorRef kept its destination identity through the initialization copy.
            assertEquals(body("1"), x.get(otherPort, "/self"));
            assertTrue(x.call("abort"));
            assertTrue(x.call("listenerClosed"));
            // Aborting one host stopped neither the other host nor the shared service.
            assertEquals(body("protos,website"), x.get(otherPort, "/projects"));
            assertTrue(x.call("drainOther"));
            assertEquals(3, x.execute("servedCount").asInt());
        }
    }

    @Test
    void aTerminatedServiceDoesNotTerminateAHealthyHost() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", "make", 1, 4));
            assertTrue(x.call("stopService"));
            // The failed service request is a handler failure retired by its session.
            assertEquals(
                    "HTTP/1.1 500 \r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                    x.get(x.port, "/projects"));
            assertEquals(body("11111"), x.get(x.port, "/factory"));
            assertTrue(x.call("drain"), "the host still drains successfully");
        }
    }

    @Test
    void abortCancelsASessionWaitingOnTheServiceAndALateReplyChangesNothing() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", "make", 1, 4));
            try (Socket waiting = x.connect()) {
                send(waiting, "GET /slow HTTP/1.1\r\nHost: a\r\n\r\n");
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SAFETY_MILLIS);
                while (!x.call("slowWaiting")) {
                    if (System.nanoTime() > deadline) {
                        fail("the session never reached the service");
                    }
                }
                assertTrue(x.call("abort"), "abort completes without the service reply");
                assertEquals("", readAll(waiting), "the cancelled session was closed unanswered");
            }
            // The late reply reaches a terminated Worker; the host result is unchanged and
            // the service is still alive.
            assertTrue(x.call("release"));
            assertTrue(x.call("terminated"));
            assertTrue(x.call("listenerClosed"));
        }
    }

    @Test
    void drainLetsAnAdmittedSessionOfTheBuiltHandlerFinish() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", "make", 1, 4));
            try (Socket held = x.connect()) {
                send(held, post("/"));
                x.awaitAdmitted();
                assertTrue(x.call("beginDrain"));
                Entry drain = new Entry(() -> x.call("finishDrain"));
                assertFalse(drain.outcome.isDone(), "drain waits for the admitted session");
                send(held, "x");
                assertEquals(body("2"), readAll(held));
                assertEquals(Boolean.TRUE, drain.await());
            }
            assertTrue(x.call("listenerClosed"));
        }
    }

    @Test
    void factoryAndResultFailuresAreStartupFailuresWithFullCleanup() throws Exception {
        try (Fixture x = new Fixture()) {
            String[][] cases = {
                {"app:factory", "failing"},
                {"app:factory", "failingAfterSuspension"},
                {"app:factory", "notCallableResult"},
                {"app:factory", "notCallable"},
                {"app:factory", "missing"},
                {"app:factory", "Encoding"},
                {"app:missing", "make"}
            };
            for (String[] failing : cases) {
                assertTrue(x.call("startFailing", failing[0], failing[1]), failing[1]);
            }
        }
    }

    @Test
    void aSecondServeNeitherRebuildsTheHandlerNorStartsAnotherAcceptance() throws Exception {
        try (Fixture x = new Fixture()) {
            // The factory suspends on the service and registers its Worker there; readiness
            // follows that construction.
            assertTrue(x.call("start", "registering", 1, 4));
            // A second acceptance service would not answer before shutdown: the immediate
            // null reply is the guard.
            assertTrue(x.call("reserve"));
            assertEquals(body("11111"), x.get(x.port, "/factory"), "the factory still ran once");
            assertTrue(x.call("drain"));
            assertTrue(x.call("listenerClosed"));
        }
    }

    @Test
    void abortWhileAFactoryIsSuspendedCancelsItAndTerminates() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("startSuspended", "suspended"));
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SAFETY_MILLIS);
            while (!x.call("slowWaiting")) {
                if (System.nanoTime() > deadline) {
                    fail("the factory never reached the service");
                }
            }
            // The service has not replied: abort completes only because the construction
            // is cancelled, and no acceptance starts after the cutover.
            assertTrue(x.call("abortWhileConstructing"));
            assertTrue(x.call("listenerClosed"));
            // The late reply reaches a terminated Worker and changes nothing.
            assertTrue(x.call("release"));
            assertTrue(x.call("terminated"));
        }
    }

    @Test
    void anInitializationThatCannotCrossAnActorBoundaryFailsStartup() throws Exception {
        try (Fixture x = new Fixture()) {
            // "acceptor": a caller-supplied TcpAcceptor never uses the spawn-only delegation
            // that the host keeps for the acceptors it derives itself.
            for (String kind : new String[] {"closure", "future", "network", "listener", "acceptor"}) {
                assertTrue(x.call("rejectedInitialization", kind), kind);
            }
        }
    }

    @Test
    void aFutureResultIsNeitherAwaitedNorAdopted() throws Exception {
        try (Fixture x = new Fixture()) {
            // A Future passes the structural callability check; invoking it as a handler is
            // default construction, which fails the session, never the Future's eventual value.
            assertTrue(x.call("start", "futureResult", 1, 4));
            assertEquals(
                    "HTTP/1.1 500 \r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                    x.get(x.port, "/"));
            assertTrue(x.call("abort"));
        }
    }

    // ------------------------------------------------------------ fixture

    private static String body(String value) {
        return "HTTP/1.1 200 \r\nContent-Length: "
                + value.length()
                + "\r\nConnection: close\r\n\r\n"
                + value;
    }

    private static String post(String target) {
        return "POST " + target + " HTTP/1.1\r\nHost: a\r\nContent-Length: 1\r\n\r\n";
    }

    private static void send(Socket socket, String text) throws IOException {
        socket.getOutputStream().write(text.getBytes(StandardCharsets.ISO_8859_1));
        socket.getOutputStream().flush();
    }

    /** Reads to EOF: every hosted session closes its connection. */
    private static String readAll(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        try {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (java.net.SocketException reset) {
            // A connection closed while unread request octets remain may be reset.
        }
        return out.toString(StandardCharsets.ISO_8859_1);
    }

    private static final class Fixture implements AutoCloseable {
        final Context context;
        final int port;

        Fixture() {
            context =
                    Context.newBuilder(ProtosLanguage.ID)
                            .option("protos.CoreRoot", CORE)
                            .allowIO(IOAccess.newBuilder().allowHostSocketAccess(true).build())
                            .allowCreateThread(true)
                            .build();
            ProtosEmbeddedModules.install(
                    context, Map.of(
                            "app:factory",
                            ProtosHttpHostFactoryFixtureSources.FACTORY,
                            "app:service",
                            ProtosHttpHostFactoryFixtureSources.SERVICE));
            context.eval(ProtosLanguage.ID, ProtosHttpHostFactoryFixtureSources.PROGRAM);
            port = execute("openListener").asInt();
        }

        Value member(String name) {
            return context.getBindings(ProtosLanguage.ID).getMember(name);
        }

        boolean call(String name, Object... arguments) {
            return execute(name, arguments).asBoolean();
        }

        /**
         * Executes a program entry point. An escaped guest Error carries no message, so it is
         * reported as a test failure naming the entry point, the hosting phase it reached, the
         * Error's prototype and kind, and the guest stack, with the exception kept as cause.
         */
        Value execute(String name, Object... arguments) {
            Object[] entry = new Object[arguments.length + 1];
            entry[0] = name;
            System.arraycopy(arguments, 0, entry, 1, arguments.length);
            Value result;
            try {
                result = member("diagnose").execute(entry);
            } catch (PolyglotException failure) {
                throw new AssertionError(describe(name, failure), failure);
            }
            if (result.isString() && result.asString().startsWith("guest-error:")) {
                throw new AssertionError(name + " failed: " + result.asString());
            }
            return result;
        }

        /** An exception that escaped even the guest-side diagnosis, such as a cancellation. */
        private static String describe(String name, PolyglotException failure) {
            StringBuilder text = new StringBuilder(name).append(" failed outside diagnosis");
            text.append(": guest=").append(failure.isGuestException());
            text.append(" cancelled=").append(failure.isCancelled());
            text.append(" message=").append(failure.getMessage());
            if (failure.getSourceLocation() != null) {
                text.append(" at ").append(failure.getSourceLocation());
            }
            for (PolyglotException.StackFrame frame : failure.getPolyglotStackTrace()) {
                if (frame.isGuestFrame()) {
                    text.append("\n    guest frame ").append(frame);
                }
            }
            return text.toString();
        }

        Socket connect() throws IOException {
            return connect(port);
        }

        Socket connect(int target) throws IOException {
            Socket socket = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), target);
            socket.setSoTimeout(SAFETY_MILLIS);
            return socket;
        }

        String get(int target, String path) throws IOException {
            try (Socket socket = connect(target)) {
                send(socket, "GET " + path + " HTTP/1.1\r\nHost: a\r\n\r\n");
                return readAll(socket);
            }
        }

        /**
         * Waits until a held session is inside its handler in the single Worker: a probe then
         * observes a peak of two. The peak never decreases, so the wait is bounded and exact.
         */
        void awaitAdmitted() throws IOException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SAFETY_MILLIS);
            while (!get(port, "/").equals(body("2"))) {
                if (System.nanoTime() > deadline) {
                    fail("the held session was not admitted");
                }
            }
        }

        @Override
        public void close() {
            context.close(true);
        }
    }

    /** One host entry on its own thread, so the test thread can act while it waits. */
    private static final class Entry {
        final CompletableFuture<Object> outcome = new CompletableFuture<>();

        Entry(BooleanSupplier entry) {
            Thread thread =
                    new Thread(
                            () -> {
                                try {
                                    outcome.complete(entry.getAsBoolean());
                                } catch (Throwable failure) {
                                    outcome.complete(failure);
                                }
                            },
                            "host-factory-entry");
            thread.setDaemon(true);
            thread.start();
        }

        Object await() throws Exception {
            return outcome.get(SAFETY_MILLIS, TimeUnit.MILLISECONDS);
        }
    }
}

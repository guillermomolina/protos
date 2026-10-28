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
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * I126 (D216, HTTP.md §15): {@code std:http/Host} over real loopback NIO in a standard Polyglot
 * Context. Workers are real Actors of the embedded Process; HTTP clients are Java sockets owned by
 * the test. Every wait is bounded; no test depends on a sleep for its verdict.
 *
 * <p>The handler module {@code app:site} is resolved by each Worker in its own Actor. Each module
 * instance counts the handler invocations active in its Worker and answers the peak as its
 * one-octet body, which makes per-Worker admission bounds observable from the wire.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
final class ProtosHttpHostIntegrationTest {
    private static final String CORE = Path.of("protos", "lib", "core").toAbsolutePath().toString();
    private static final int SAFETY_MILLIS = (int) TimeUnit.SECONDS.toMillis(5);

    private static final String SITE =
            """
            active: 0
            peak: 0
            // Module-instance state: the constructing Actor's own instance
            // sets it; each Worker instance keeps its initial value.
            marker: "worker"
            mark: (value) => {
                marker = value
            }
            digit: (count) => {
                bytes: Encoding.Latin1.encode("")
                bytes.add(48 + count)
                bytes
            }
            handle: (request) => {
                active = active + 1
                if(active > peak) {
                    peak = active
                }
                if(Encoding.Latin1.decode(request.target) == "/boom") {
                    active = active - 1
                    Error().signal()
                }
                if(Encoding.Latin1.decode(request.target) == "/crash") {
                    // The Worker behavior has no "crash": that later turn ends
                    // with an unhandled Error, fatal to the Worker (ACTORS.md
                    // §24C), and its sessions unwind by cancellation.
                    Actor.current().send("crash")
                }
                if(Encoding.Latin1.decode(request.target) == "/stop") {
                    // An ordinary graceful self-stop of the Worker (ACTORS.md
                    // §24A): its other sessions are cancelled cooperatively.
                    Actor.current().stop()
                }
                if(request.method == "POST") {
                    request.body.read(1).value()
                }
                active = active - 1
                response: {
                    status: 200
                    headers: []
                    body: digit(peak)
                }
                response
            }
            // CALLABLES.md "Callability inspection": every object inherits the
            // Closure-valued Object.call, so only a nearer non-Closure call
            // makes a binding non-invokable.
            showMarker: (request) => {
                response: {
                    status: 200
                    headers: []
                    body: Encoding.Latin1.encode(marker)
                }
                response
            }
            notCallable: {
                call: 42
            }
            """;

    private static final String PROGRAM =
            """
            http: import("std:http/Host")
            IpAddress: import("std:network/IpAddresses").IpAddress
            loopback: IpAddress(4, 2130706433)
            listener: null
            host: null
            openListener: () => {
                request: {
                    ipVersion: 4
                    address: loopback
                    port: null
                }
                listener = network.listenTcp(request).value()
                listener.localPort()
            }
            // The options object is mutated after construction: the host keeps
            // the configuration it captured.
            start: (workerCount, connectionLimit) => {
                options: {
                    workers: workerCount
                    maxConnections: connectionLimit
                }
                host = http.Host.owning(listener, "app:site", "handle", options)
                options.workers = 3
                options.maxConnections = 64
                host.ready().value() === host
            }
            drain: () => {
                host.shutdown("drain").value() === host
            }
            // Test-only split of drain: beginDrain returns once the shutdown
            // request has been made, so the caller probes after the cutover.
            pendingDrain: null
            beginDrain: () => {
                pendingDrain = host.shutdown("drain")
                pendingDrain !== null
            }
            finishDrain: () => {
                pendingDrain.value() === host
            }
            abort: () => {
                host.shutdown("abort").value() === host
            }
            escalate: () => {
                draining: host.shutdown("drain")
                aborting: host.shutdown("abort")
                again: host.shutdown("drain")
                (aborting.value() === host) && (draining.value() === host) && (again.value() === host)
            }
            observers: () => {
                first: host.ready()
                second: host.ready()
                cancelled: host.termination()
                cancelled.cancel()
                (first !== second) && (first.value() === host) && (second.value() === host)
            }
            afterTermination: () => {
                terminated: host.termination()
                drained: host.shutdown("drain")
                aborted: host.shutdown("abort")
                ready: host.ready()
                distinct: (terminated !== drained) && (drained !== aborted)
                distinct && (terminated.value() === host) && (drained.value() === host) && (aborted.value() === host) && (ready.value() === host)
            }
            invalidMode: () => {
                InvalidIOArgument.handle(
                    () => {
                        host.shutdown("graceful")
                        false
                    },
                    (error) => { true }
                )
            }
            listenerClosed: () => {
                IOLifecycleError.handle(
                    () => {
                        listener.accept().value()
                        false
                    },
                    (error) => { true }
                )
            }
            // Readiness is observed first: a shutdown requested while STARTING
            // would itself preclude readiness without any hosting failure.
            hostFailureKinds: () => {
                kinds: []
                errors: []
                record: (future) => {
                    http.HostError.handle(
                        () => {
                            future.value()
                        },
                        (error) => {
                            kinds = Array(...kinds, error.kind)
                            errors = Array(...errors, error)
                            error
                        }
                    )
                }
                record(host.ready())
                // An unexpectedly ready host is aborted so that the remaining
                // observers settle and the verdict fails instead of hanging.
                if(errors.size() == 0) {
                    host.shutdown("abort")
                }
                record(host.termination())
                record(host.shutdown("drain"))
                fresh: (errors.size() == 3) && (errors[0] !== errors[1]) && (errors[1] !== errors[2])
                fresh && (kinds[0] == "startup") && (kinds[1] == "startup") && (kinds[2] == "startup")
            }
            startFailing: (binding) => {
                host = http.Host.owning(listener, "app:site", binding, null)
                hostFailureKinds()
            }
            rejected: (options) => {
                InvalidIOArgument.handle(
                    () => {
                        http.Host.owning(listener, "app:site", "handle", options)
                        false
                    },
                    (error) => { error.parent() === InvalidIOArgument }
                )
            }
            invalidWithLiveListener: () => {
                pending: {
                    workers: 2
                    maxPendingAccepts: 1
                }
                unknown: {
                    workers: 1
                    deadline: 5
                }
                rejected(pending) && rejected(unknown) && rejected([])
            }
            inheritedOptionsIgnored: () => {
                base: {
                    workers: 0
                    maxConnections: -1
                }
                derived: base {}
                host = http.Host.owning(listener, "app:site", "handle", derived)
                host.ready().value() === host
            }
            startPending: (workerCount, connectionLimit, pendingLimit) => {
                options: {
                    workers: workerCount
                    maxConnections: connectionLimit
                    maxPendingAccepts: pendingLimit
                }
                host = http.Host.owning(listener, "app:site", "handle", options)
                host.ready().value() === host
            }
            // HTTP.md §15.7: a shutdown while STARTING precludes RUNNING, so
            // readiness fails, while the requested shutdown itself completes.
            shutdownWhileStarting: (mode) => {
                host = http.Host.owning(listener, "app:site", "handle", { workers: 2 })
                requested: host.shutdown(mode)
                repeated: host.shutdown("drain")
                notReady: http.HostError.handle(
                    () => {
                        host.ready().value()
                        false
                    },
                    (error) => { error.kind == "startup" }
                )
                notReady && (requested.value() === host) && (repeated.value() === host) && (host.termination().value() === host)
            }
            repeatedAbort: () => {
                first: host.shutdown("abort")
                second: host.shutdown("abort")
                third: host.shutdown("drain")
                (first !== second) && (first.value() === host) && (second.value() === host) && (third.value() === host)
            }
            // Fails every observer with a fresh HostError of kind; answers
            // false when any observer resolves instead.
            terminatedWith: (kind) => {
                kinds: []
                errors: []
                record: (future) => {
                    http.HostError.handle(
                        () => {
                            future.value()
                        },
                        (error) => {
                            kinds = Array(...kinds, error.kind)
                            errors = Array(...errors, error)
                            error
                        }
                    )
                }
                record(host.termination())
                record(host.shutdown("drain"))
                record(host.termination())
                fresh: (errors.size() == 3) && (errors[0] !== errors[1]) && (errors[1] !== errors[2])
                fresh && (kinds[0] == kind) && (kinds[1] == kind) && (kinds[2] == kind)
            }
            startModule: (moduleSpecifier, binding) => {
                host = http.Host.owning(listener, moduleSpecifier, binding, null)
                hostFailureKinds()
            }
            // The constructing Actor's own app:site instance is not the
            // Workers' instance, and no Closure is accepted in place of the
            // module and binding Strings.
            isolatedModule: () => {
                site: import("app:site")
                site.mark("creator")
                closureRejected: InvalidIOArgument.handle(
                    () => {
                        http.Host.owning(listener, site.handle, "handle", null)
                        false
                    },
                    (error) => { true }
                )
                host = http.Host.owning(listener, "app:site", "showMarker", { workers: 2 })
                closureRejected && (host.ready().value() === host)
            }
            // FUTURES_AND_TASKS.md §24: the hosting Tasks belong to the Task
            // that constructed the host. Cancelling an observer leaves the host
            // running; cancelling that owning Task aborts and releases it.
            owner: null
            startOwned: () => {
                owner = (() => {
                    host = http.Host.owning(listener, "app:site", "handle", null)
                    host.termination().value()
                }).future()
                0
            }
            ownedReady: () => {
                // Suspends once so that the owning Task can run first.
                (() => { null }).future().value()
                (host !== null) && (host.ready().value() === host)
            }
            cancelObserver: () => {
                observer: host.termination()
                observer.cancel()
                Cancelled.handle(
                    () => {
                        observer.value()
                        false
                    },
                    (error) => { true }
                )
            }
            cancelOwner: () => {
                owner.cancel()
                Cancelled.handle(
                    () => {
                        owner.value()
                        false
                    },
                    (error) => { true }
                )
            }
            closedListenerRejected: () => {
                listener.close().value()
                rejected(null)
            }
            0
            """;

    // ------------------------------------------------------------ tests

    @Test
    void oneWorkerServesARealRequestThroughServeOneOwningAndDrains() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", 1, 64), "ready() resolves with the identical host");
            assertEquals(response('1'), x.get("/"));
            // The exact single-request Server framing, including its rejection path.
            assertEquals(
                    "HTTP/1.1 400 \r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                    x.exchange("BAD\r\n\r\n"));
            assertTrue(x.call("observers"), "fresh observers, same host; cancelling one is local");
            assertEquals(response('1'), x.get("/"), "the cancelled observer left the host running");
            assertTrue(x.call("drain"));
            assertTrue(x.call("afterTermination"));
            assertTrue(x.call("listenerClosed"), "the host closed its listener");
            x.assertRefused();
        }
    }

    @Test
    void handlerAndClientFailuresStayInsideTheirSessions() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", 1, 4));
            assertEquals(
                    "HTTP/1.1 500 \r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                    x.get("/boom"));
            try (Socket vanishing = x.connect()) {
                send(vanishing, "GET / HTTP/1.1\r\nHo");
            }
            assertEquals(response('1'), x.get("/"));
            assertTrue(x.call("invalidMode"), "an unknown mode signals and changes nothing");
            assertEquals(response('1'), x.get("/"));
            assertTrue(x.call("drain"));
        }
    }

    @Test
    void severalWorkersAcceptIndependently() throws Exception {
        try (Fixture x = new Fixture()) {
            // maxConnections 2 over 2 Workers: one connection quota unit each.
            assertTrue(x.call("start", 2, 2));
            try (Socket held = x.connect(); Socket other = x.connect()) {
                send(held, post());
                send(other, post());
                // Released first while the other is still held: each Worker peaked at one
                // session, so the two sessions ran in different Workers.
                send(other, "x");
                assertEquals(response('1'), readAll(other));
                send(held, "x");
                assertEquals(response('1'), readAll(held));
            }
            assertTrue(x.call("drain"));
        }
    }

    @Test
    void theConnectionQuotaBoundsAdmissionAndTheCapturedConfigurationIsFixed() throws Exception {
        try (Fixture x = new Fixture()) {
            // One Worker, maxConnections 1; the options were raised to 64 after construction.
            assertTrue(x.call("start", 1, 1));
            try (Socket held = x.connect(); Socket waiting = x.connect()) {
                send(held, post());
                send(waiting, "GET / HTTP/1.1\r\nHost: a\r\n\r\n");
                send(held, "x");
                // Admitted concurrently, either session would have observed a peak of two.
                assertEquals(response('1'), readAll(held));
                assertEquals(response('1'), readAll(waiting));
            }
            assertTrue(x.call("drain"));
        }
    }

    @Test
    void drainClosesTheListenerAndWaitsForTheAdmittedSession() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", 1, 4));
            try (Socket held = x.connect()) {
                send(held, post());
                x.awaitAdmitted();
                // The shutdown request is made synchronously, so every probe below starts
                // after the drain cutover.
                assertTrue(x.call("beginDrain"));
                x.assertRefused();
                Entry drain = new Entry(x.member("finishDrain"));
                assertFalse(drain.outcome.isDone(), "drain waits for the admitted session");
                send(held, "x");
                assertEquals(response('2'), readAll(held));
                assertEquals(Boolean.TRUE, drain.await());
            }
            assertTrue(x.call("afterTermination"));
        }
    }

    @Test
    void abortCancelsAdmittedSessionsAndCleansUp() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", 1, 4));
            try (Socket held = x.connect()) {
                send(held, post());
                x.awaitAdmitted();
                assertTrue(x.call("abort"), "abort completes without the held client");
                assertEquals("", readAll(held), "the cancelled session was closed unanswered");
            }
            assertTrue(x.call("afterTermination"));
            assertTrue(x.call("listenerClosed"));
        }
    }

    @Test
    void abortEscalatesAPendingDrainIrreversibly() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", 1, 4));
            try (Socket held = x.connect()) {
                send(held, post());
                x.awaitAdmitted();
                assertTrue(x.call("escalate"));
                assertEquals("", readAll(held));
            }
        }
    }

    @Test
    void startupFailureFailsEveryObserverAndReleasesTheListener() throws Exception {
        for (String binding : new String[] {"missing", "notCallable", "Encoding"}) {
            try (Fixture x = new Fixture()) {
                assertTrue(x.call("startFailing", binding), binding);
                assertTrue(x.call("listenerClosed"), binding);
            }
        }
    }

    @Test
    void invalidConstructionTakesNoCustodyOfALiveListener() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("invalidWithLiveListener"));
            assertTrue(x.call("inheritedOptionsIgnored"), "inherited option slots are not read");
            assertEquals(response('1'), x.get("/"));
            assertTrue(x.call("abort"));
        }
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("closedListenerRejected"));
        }
    }

    @Test
    void concurrentSessionsInOneWorkerInterleaveWhileOneStaysBlocked() throws Exception {
        try (Fixture x = new Fixture()) {
            // One Worker, two connection units and one pending accept: lanes follow demand
            // beyond the single pending accept.
            assertTrue(x.call("startPending", 1, 2, 1));
            try (Socket blocked = x.connect(); Socket other = x.connect()) {
                send(blocked, post());
                send(other, post());
                // Both units are taken, so both sessions are admitted; the blocked one does
                // not stop the other from completing.
                send(other, "x");
                assertEquals(response('2'), readAll(other), "two sessions ran concurrently");
                // The retired unit admits a later client while the first is still blocked.
                assertEquals(response('2'), x.get("/"));
                send(blocked, "x");
                assertEquals(response('2'), readAll(blocked));
            }
            assertTrue(x.call("drain"));
        }
    }

    @Test
    void successiveConnectionsAreServedOneAfterAnother() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", 2, 4));
            for (int index = 0; index < 8; index++) {
                assertEquals(response('1'), x.get("/"), "connection " + index);
            }
            assertTrue(x.call("drain"));
            x.assertRefused();
        }
    }

    @Test
    void shutdownWhileStartingPrecludesReadinessAndReleasesTheListener() throws Exception {
        for (String mode : new String[] {"drain", "abort"}) {
            try (Fixture x = new Fixture()) {
                assertTrue(x.call("shutdownWhileStarting", mode), mode);
                assertTrue(x.call("listenerClosed"), mode);
            }
        }
    }

    @Test
    void repeatedShutdownRequestsObserveOneLifecycle() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", 1, 4));
            try (Socket held = x.connect()) {
                send(held, post());
                x.awaitAdmitted();
                assertTrue(x.call("repeatedAbort"));
                assertEquals("", readAll(held));
            }
            assertTrue(x.call("afterTermination"));
            assertTrue(x.call("listenerClosed"));
        }
    }

    @Test
    void anUnexpectedWorkerTerminationAbortsTheHostWithWorkerKind() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", 2, 4));
            try (Socket crashing = x.connect()) {
                send(crashing, "GET /crash HTTP/1.1\r\nHost: a\r\n\r\n");
                assertTrue(
                        x.call("terminatedWith", "worker"), "fresh HostError \"worker\" per observer");
                assertTrue(x.call("listenerClosed"), "the remaining Worker was aborted");
                x.assertRefused();
                // Host termination waited for the failed Worker, whose termination waited for
                // the session's owning cleanup: the connection was released, so the client sees
                // EOF (HTTP.md §15.6). The socket timeout only bounds the wait; a timeout fails.
                readAll(crashing);
            }
        }
    }

    @Test
    void aWorkerStopReleasesTheConnectionOfASessionSuspendedInIo() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", 1, 4));
            try (Socket parked = x.connect()) {
                // The session is suspended reading its one-octet body.
                send(parked, post());
                x.awaitAdmitted();
                try (Socket stopping = x.connect()) {
                    send(stopping, "GET /stop HTTP/1.1\r\nHost: a\r\n\r\n");
                    assertTrue(
                            x.call("terminatedWith", "worker"),
                            "fresh HostError \"worker\" per observer");
                    assertTrue(x.call("listenerClosed"));
                    x.assertRefused();
                    // The Worker terminated only after the cancellation cleanup of each session
                    // closed its connection (D121): both clients see EOF. A timeout fails.
                    assertEquals("", readAll(parked), "the parked session was closed unanswered");
                    readAll(stopping);
                }
            }
        }
    }

    @Test
    void aWorkerStopFromAReadySessionReleasesItsConnection() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", 1, 4));
            try (Socket stopping = x.connect()) {
                // The handler stops its Worker and then continues without suspending: the
                // termination cancellation is still only requested when the session reaches
                // its owning close, which must still run as D121 cancellation cleanup.
                send(stopping, "GET /stop HTTP/1.1\r\nHost: a\r\n\r\n");
                assertTrue(
                        x.call("terminatedWith", "worker"),
                        "fresh HostError \"worker\" per observer");
                assertTrue(x.call("listenerClosed"));
                x.assertRefused();
                // The Worker terminated after the owning close: the client sees EOF, so no
                // session connection outlives the termination. A timeout fails.
                readAll(stopping);
            }
        }
    }

    @Test
    void aWorkerStopEncounteredOnAnAttemptedBodyReadReleasesItsConnection() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("start", 1, 4));
            try (Socket stopping = x.connect()) {
                // The declared one-octet body is never sent. The handler stops its Worker and
                // only then attempts the body read, so the already-requested termination
                // cancellation is encountered at that read and unwinds the session. A service
                // already suspended in I/O when cancellation is requested is covered by
                // aWorkerStopReleasesTheConnectionOfASessionSuspendedInIo.
                send(stopping, "POST /stop HTTP/1.1\r\nHost: a\r\nContent-Length: 1\r\n\r\n");
                assertTrue(
                        x.call("terminatedWith", "worker"),
                        "fresh HostError \"worker\" per observer");
                assertTrue(x.call("listenerClosed"));
                x.assertRefused();
                assertEquals("", readAll(stopping), "the stopping session was closed unanswered");
            }
        }
    }

    @Test
    void handlerModulesAreResolvedPerWorkerUnderTheExistingModuleRules() throws Exception {
        try (Fixture x = new Fixture()) {
            assertTrue(x.call("isolatedModule"));
            assertEquals(marker("worker"), x.get("/"), "a Worker never sees the creator's instance");
            assertEquals(marker("worker"), x.get("/"));
            assertTrue(x.call("abort"));
        }
        // MODULES.md: the specifier is resolved relative to the importing module, here
        // std:http/internal/HostWorker, never rebased onto the caller. The resolver rejects
        // it there, which is an asynchronous startup failure.
        for (String specifier : new String[] {"./site", "app:missing"}) {
            try (Fixture x = new Fixture()) {
                assertTrue(x.call("startModule", specifier, "handle"), specifier);
                assertTrue(x.call("listenerClosed"), specifier);
            }
        }
    }

    @Test
    void observerCancellationIsLocalButOwnerCancellationReleasesTheHost() throws Exception {
        try (Fixture x = new Fixture()) {
            x.member("startOwned").execute();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SAFETY_MILLIS);
            while (!x.call("ownedReady")) {
                if (System.nanoTime() > deadline) {
                    fail("the owned host did not become ready");
                }
            }
            assertTrue(x.call("cancelObserver"));
            assertEquals(response('1'), x.get("/"), "a cancelled observer leaves the host running");
            assertTrue(x.call("cancelOwner"), "the owning Task ends cancelled after cleanup");
            assertTrue(x.call("listenerClosed"), "its cancellation still released the listener");
            x.assertRefused();
        }
    }

    // ------------------------------------------------------------ fixture

    private static String marker(String value) {
        return "HTTP/1.1 200 \r\nContent-Length: "
                + value.length()
                + "\r\nConnection: close\r\n\r\n"
                + value;
    }

    private static String response(char peak) {
        return "HTTP/1.1 200 \r\nContent-Length: 1\r\nConnection: close\r\n\r\n" + peak;
    }

    private static String post() {
        return "POST / HTTP/1.1\r\nHost: a\r\nContent-Length: 1\r\n\r\n";
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
            ProtosEmbeddedModules.install(context, Map.of("app:site", SITE));
            context.eval(ProtosLanguage.ID, PROGRAM);
            port = member("openListener").execute().asInt();
        }

        Value member(String name) {
            return context.getBindings(ProtosLanguage.ID).getMember(name);
        }

        boolean call(String name, Object... arguments) {
            return member(name).execute(arguments).asBoolean();
        }

        Socket connect() throws IOException {
            Socket socket = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port);
            socket.setSoTimeout(SAFETY_MILLIS);
            return socket;
        }

        String exchange(String request) throws IOException {
            try (Socket socket = connect()) {
                send(socket, request);
                return readAll(socket);
            }
        }

        String get(String target) throws IOException {
            return exchange("GET " + target + " HTTP/1.1\r\nHost: a\r\n\r\n");
        }

        /**
         * Waits until a held session is inside its handler in the single Worker: a probe then
         * observes a peak of two. The peak never decreases, so the wait is bounded and exact.
         */
        void awaitAdmitted() throws IOException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SAFETY_MILLIS);
            while (!get("/").equals(response('2'))) {
                if (System.nanoTime() > deadline) {
                    fail("the held session was not admitted");
                }
            }
        }

        /**
         * The listener close is observable as refusal once its release completed. Probes start
         * after the shutdown cutover. A reset of an overlapping connect, or a connection that was
         * accepted but closed or reset unserved, is inconclusive and probed again; an HTTP
         * response proves a session admitted after the cutover and fails, as does any other I/O
         * failure or a connection left open until its socket timeout.
         */
        void assertRefused() {
            InetSocketAddress address;
            try {
                address =
                        new InetSocketAddress(
                                InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port);
            } catch (IOException unexpected) {
                throw new AssertionError(unexpected);
            }
            int connectResets = 0;
            int unservedConnections = 0;
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SAFETY_MILLIS);
            while (System.nanoTime() < deadline) {
                Socket probe = new Socket();
                try {
                    try {
                        probe.connect(address, SAFETY_MILLIS);
                    } catch (ConnectException refused) {
                        return;
                    } catch (java.net.SocketException connectFailure) {
                        if (!isReset(connectFailure)) {
                            fail("unexpected I/O failure while connecting a probe", connectFailure);
                        }
                        connectResets++;
                        continue;
                    } catch (IOException unexpected) {
                        fail("unexpected I/O failure while connecting a probe", unexpected);
                    }
                    probe.setSoTimeout(SAFETY_MILLIS);
                    String answered = probeUnserved(probe);
                    if (!answered.isEmpty()) {
                        fail(
                                "an HTTP session was admitted after the shutdown cutover: "
                                        + answered);
                    }
                    unservedConnections++;
                } catch (java.net.SocketTimeoutException open) {
                    fail("a probe connection stayed open until its socket timeout", open);
                } catch (IOException unexpected) {
                    fail("unexpected I/O failure on a probe connection", unexpected);
                } finally {
                    try {
                        probe.close();
                    } catch (IOException unexpected) {
                        fail("unexpected I/O failure closing a probe", unexpected);
                    }
                }
                Thread.onSpinWait();
            }
            fail(
                    "the listener was not released: "
                            + connectResets
                            + " transient connect resets and "
                            + unservedConnections
                            + " unserved connections never reached refusal");
        }

        /**
         * Sends a complete valid request on a connected probe and reads to its end. Answers the
         * octets received, empty when the connection was closed or reset unserved.
         */
        private String probeUnserved(Socket probe) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                send(probe, "GET / HTTP/1.1\r\nHost: a\r\n\r\n");
                InputStream in = probe.getInputStream();
                byte[] buffer = new byte[256];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } catch (java.net.SocketTimeoutException open) {
                throw open;
            } catch (java.net.SocketException closed) {
                if (!isReset(closed) && !isBrokenPipe(closed)) {
                    throw closed;
                }
            }
            return out.toString(StandardCharsets.ISO_8859_1);
        }

        private static boolean isReset(java.net.SocketException failure) {
            String message = failure.getMessage();
            return message != null && message.startsWith("Connection reset");
        }

        private static boolean isBrokenPipe(java.net.SocketException failure) {
            String message = failure.getMessage();
            return message != null && message.startsWith("Broken pipe");
        }

        @Override
        public void close() {
            context.close(true);
        }
    }

    /** One host entry on its own thread, so the test thread can act while it waits. */
    private static final class Entry {
        final CompletableFuture<Object> outcome = new CompletableFuture<>();

        Entry(Value executable) {
            Thread thread =
                    new Thread(
                            () -> {
                                try {
                                    outcome.complete(executable.execute().asBoolean());
                                } catch (Throwable failure) {
                                    outcome.complete(failure);
                                }
                            },
                            "host-entry");
            thread.setDaemon(true);
            thread.start();
        }

        Object await() throws Exception {
            return outcome.get(SAFETY_MILLIS, TimeUnit.MILLISECONDS);
        }
    }
}

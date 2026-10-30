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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * LIB026 (D222-C, HTTP.md §18): the public {@code std:http/UrlClient} module.
 *
 * <p>The first group runs guest snippets in a bootstrapped Prelude. The connection the guest
 * {@code network} fixture answers is a native in-memory transport whose far end is a {@link
 * ProtosTlsTestPeer} JSSE server presenting the fixture chain under an explicit {@code
 * Trust.anchors}; the server answers one canned HTTP response once a request head arrived. No
 * Internet, public DNS or operating-system trust is involved: the Resolver is a guest fixture that
 * answers loopback.
 *
 * <p>The second group uses a standard Polyglot Context with real loopback TCP and its embedded
 * {@code network} grant. Java {@link ServerSocket} fixtures play the origin server for numeric
 * {@code http} URLs. The embedding grants no Resolver, so host names are exercised only for
 * admission. All URL, HTTP, TLS and custody behavior is the Protos libraries'; Java contributes
 * only fixtures and the existing TLS engine. Every wait is bounded and no verdict depends on a
 * sleep.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
final class ProtosHttpUrlClientIntegrationTest {
    private static final Path CORE_PATH = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final String CORE = CORE_PATH.toAbsolutePath().toString();
    private static final int SAFETY_MILLIS = (int) TimeUnit.SECONDS.toMillis(5);
    private static final String TLS13 = "TLSv1.3";
    private static final String SECURE_RESPONSE =
            "HTTP/1.1 200 OK\r\nContent-Length: 6\r\n\r\nsecure";

    // ------------------------------------------------------------ in-memory TLS harness

    private static final String HARNESS_PRELUDE =
            """
            http: import("std:http/UrlClient")
            tls: import("std:tls/Client")
            IpAddresses: import("std:network/IpAddresses")
            IpEndpoints: import("std:network/IpEndpoints")
            Assertions: import("std:test/Assertions")
            ascii: (text) => {
                Encoding.Latin1.encode(text)
            }
            text: (bytes) => {
                Encoding.Latin1.decode(bytes)
            }
            failureOf: (future) => {
                caught: null
                Error.handle(
                    () => {
                        future.value()
                    },
                    (error) => {
                        caught = error
                        error
                    }
                )
                caught
            }
            attempts: []
            names: []
            network: {
                connectTcp: (endpoint) => {
                    attempts = Array(...attempts, IpEndpoints.format(endpoint))
                    (() => { dx }).future()
                }
            }
            resolver: {
                lookupAddresses: (name) => {
                    names = Array(...names, name)
                    (() => { [IpAddresses.parse("127.0.0.1")].freeze() }).future()
                }
            }
            authorities: {
                network: network
                resolver: resolver
                trust: tls.Trust.anchors([rootCa])
            }
            get: () => {
                request: {
                    method: "GET"
                    headers: []
                    body: null
                }
                request
            }
            requestOne: (url) => {
                http.UrlClient.requestOne(authorities, url, get(), null)
            }
            """;

    private static ProtosPrelude prelude;

    @BeforeAll
    static void bootstrap() throws IOException {
        prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE_PATH, new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
    }

    @Test
    void httpsVerifiesTheUrlHostNameAndSendsItAsSniAndHost() {
        Run run =
                run(
                        SECURE_RESPONSE,
                        """
                        response: requestOne("https://service.protos.test/x?y=1&y=2").value()
                        Assertions.require(response.status == 200)
                        Assertions.require(text(response.body) == "secure")
                        Assertions.require(names.size() == 1)
                        Assertions.require(names[0] == "service.protos.test")
                        Assertions.require(attempts.size() == 1)
                        Assertions.require(attempts[0] == "127.0.0.1:443")
                        Assertions.require(dx.closeCount() == 1)
                        true
                        """);
        run.assertCompleted();
        assertEquals(
                List.of(new SNIHostName("service.protos.test")), run.duplex.sni());
        assertEquals(
                "GET /x?y=1&y=2 HTTP/1.1\r\nHost: service.protos.test\r\n"
                        + "Content-Length: 0\r\nConnection: close\r\n\r\n",
                run.duplex.serverText());
        assertEquals(1, run.duplex.closes);
    }

    @Test
    void httpsToANumericHostVerifiesTheIpAddressWithoutLookupOrSni() {
        Run run =
                run(
                        SECURE_RESPONSE,
                        """
                        response: requestOne("https://127.0.0.1:8443/").value()
                        Assertions.require(response.status == 200)
                        Assertions.require(names.size() == 0)
                        Assertions.require(attempts[0] == "127.0.0.1:8443")
                        true
                        """);
        run.assertCompleted();
        assertTrue(run.duplex.sni().isEmpty());
        assertTrue(run.duplex.serverText().startsWith("GET / HTTP/1.1\r\nHost: 127.0.0.1:8443\r\n"));
        assertEquals(1, run.duplex.closes);
    }

    @Test
    void aMismatchedReferenceIdentityFailsBeforeAnyHttpAndClosesOnce() {
        Run run =
                run(
                        SECURE_RESPONSE,
                        """
                        failure: failureOf(requestOne("https://other.protos.test/"))
                        Assertions.require(failure.parent() === tls.TlsError)
                        Assertions.require(failure.kind == "identityMismatch")
                        Assertions.require(attempts.size() == 1)
                        true
                        """);
        run.assertCompleted();
        assertEquals("", run.duplex.serverText());
        assertEquals(1, run.duplex.closes);
    }

    @Test
    void anUnrelatedAnchorIsNeverReplacedByAmbientTrust() {
        Run run =
                run(
                        SECURE_RESPONSE,
                        """
                        untrusted: {
                            network: network
                            resolver: resolver
                            trust: tls.Trust.anchors([untrustedCa])
                        }
                        failure: failureOf(http.UrlClient.requestOne(untrusted, "https://service.protos.test/", get(), null))
                        Assertions.require(failure.kind == "untrustedPeer")
                        true
                        """);
        run.assertCompleted();
        assertEquals("", run.duplex.serverText());
        assertEquals(1, run.duplex.closes);
    }

    @Test
    void admissionCreatesExactlyOneServiceTaskAndInvalidCallsNone() {
        Run run =
                run(
                        SECURE_RESPONSE,
                        """
                        before: liveTasks()
                        invalid: http.UrlClient.requestOne(authorities, "https://u@service.protos.test/", get(), null)
                        Assertions.require(liveTasks() == before)
                        Assertions.require(failureOf(invalid).parent() === InvalidIOArgument)
                        service: requestOne("https://service.protos.test/")
                        Assertions.require(liveTasks() == before + 1)
                        Assertions.require(service.value().status == 200)
                        Assertions.require(liveTasks() == before)
                        true
                        """);
        run.assertCompleted();
        assertEquals(1, run.duplex.closes);
    }

    @Test
    void cancellationBeforeStartLooksUpAndConnectsNothing() {
        Run run =
                run(
                        SECURE_RESPONSE,
                        """
                        service: requestOne("https://service.protos.test/")
                        service.cancel()
                        cancelled: Cancelled.handle(
                            () => {
                                service.value()
                                false
                            },
                            (cancellation) => { true }
                        )
                        Assertions.require(cancelled)
                        Assertions.require(names.size() == 0)
                        Assertions.require(attempts.size() == 0)
                        true
                        """);
        run.assertCompleted();
        assertEquals(0, run.duplex.closes);
    }

    @Test
    void cancellationWhileWaitingClosesAnAcquiredConnectionOnce() {
        Run run =
                run(
                        null,
                        """
                        service: requestOne("https://service.protos.test/")
                        canceller: (() => {
                            service.cancel()
                            null
                        }).future()
                        cancelled: Cancelled.handle(
                            () => {
                                service.value()
                                false
                            },
                            (cancellation) => { true }
                        )
                        canceller.value()
                        Assertions.require(cancelled)
                        // Closed exactly once when a connection was acquired, never otherwise.
                        Assertions.require(dx.closeCount() == attempts.size())
                        true
                        """);
        run.assertCompleted();
        assertTrue(run.duplex.closes <= 1);
    }

    private static Run run(String response, String body) {
        Duplex duplex = new Duplex(ProtosTlsTestPeer.server("service-leaf", TLS13), response);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue context = activation.context();
        context.createLocalSlot("dx", duplex.object);
        context.createLocalSlot("rootCa", bytes(ProtosTlsTestPeer.der("root-ca.pem")));
        context.createLocalSlot("untrustedCa", bytes(ProtosTlsTestPeer.der("untrusted-ca.pem")));
        context.createLocalSlot(
                "liveTasks", fn((a, x) -> integer(a.executionDomain().liveTaskCount())));
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(HARNESS_PRELUDE + body, activation);
        return new Run(duplex, outcome);
    }

    private record Run(Duplex duplex, ProtosExecutionOutcome outcome) {
        void assertCompleted() {
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () -> "snippet failed: " + describe(outcome.error()));
            assertSame(ProtosBooleanValue.TRUE, outcome.value());
        }
    }

    /** Reports the standard Error prototype and own kind for failed guest snippets. */
    private static String describe(Object error) {
        if (!(error instanceof ProtosObjectValue object)) {
            return String.valueOf(error);
        }
        StringBuilder result = new StringBuilder();
        for (ProtosCoreErrors.StandardError standard : ProtosCoreErrors.StandardError.values()) {
            Object prototype;
            try {
                prototype = ProtosCoreErrors.newOccurrence(prelude, standard).parent().orElse(null);
            } catch (RuntimeException unavailable) {
                continue;
            }
            Object current = object;
            while (current instanceof ProtosObjectValue value) {
                if (value == prototype) {
                    result.append(standard.prototypeName()).append(' ');
                }
                current = value.parent().orElse(null);
            }
        }
        object.readLocalSlot("kind").ifPresent(kind -> result.append("kind=").append(kind));
        if (result.length() == 0) {
            result.append("unclassified ")
                    .append(object.localSlotsSnapshot().keySet());
        }
        return result.toString().trim();
    }

    /**
     * In-memory transport to a JSSE server. A write hands its ciphertext to the server at once;
     * once the decrypted request head is complete the server writes {@code response} (when not
     * null) exactly once. A read answers pending server ciphertext or a pending Future resolved
     * when the server next produces output.
     */
    private static final class Duplex {
        final ProtosObjectValue object = new ProtosObjectValue(ProtosObjectValue.rootObject());
        final ProtosTlsTestPeer peer;
        final ByteArrayOutputStream serverReceived = new ByteArrayOutputStream();
        String response;
        ProtosFutureValue pendingRead;
        int pendingMax;
        int closes;

        Duplex(ProtosTlsTestPeer peer, String response) {
            this.peer = peer;
            this.response = response;
            slot("read", (a, x) -> read(a, ProtosNumericValueSupport.exactInt(x.get(0))));
            slot(
                    "write",
                    (a, x) -> {
                        peer.receive(((ProtosBytesValue) x.get(0)).octetSnapshot());
                        serverReceived.writeBytes(peer.takeReceived());
                        if (this.response != null && serverText().contains("\r\n\r\n")) {
                            peer.write(this.response.getBytes(StandardCharsets.ISO_8859_1));
                            peer.send(0);
                            this.response = null;
                        }
                        deliver(a);
                        return resolved(a, object);
                    });
            slot(
                    "close",
                    (a, x) -> {
                        closes++;
                        return resolved(a, ProtosNullValue.INSTANCE);
                    });
            slot("closeCount", (a, x) -> integer(closes));
            slot("serverText", (a, x) -> new ProtosStringValue(serverText()));
        }

        String serverText() {
            return serverReceived.toString(StandardCharsets.ISO_8859_1);
        }

        List<javax.net.ssl.SNIServerName> sni() {
            return ((ExtendedSSLSession) peer.server.getSession()).getRequestedServerNames();
        }

        private void slot(String name, ProtosNativeClosureBody body) {
            object.createLocalSlot(name, fn(body));
        }

        private Object read(ProtosActivation activation, int maxBytes) {
            byte[] available = peer.send(maxBytes);
            if (available.length > 0) {
                return resolved(activation, bytes(available));
            }
            pendingRead = future(activation);
            pendingMax = maxBytes;
            return pendingRead;
        }

        private void deliver(ProtosActivation activation) {
            if (pendingRead == null) {
                return;
            }
            byte[] available = peer.send(pendingMax);
            if (available.length == 0) {
                return;
            }
            ProtosFutureValue waiting = pendingRead;
            pendingRead = null;
            waiting.resolve(bytes(available), activation);
        }
    }

    private static ProtosBytesValue bytes(byte[] octets) {
        ProtosBytesValue result = new ProtosBytesValue(prelude.bytesPrototypeForRuntime());
        for (byte octet : octets) {
            result.indexedAdd(ProtosNumericValueSupport.octet(octet & 255));
        }
        return result;
    }

    private static ProtosFutureValue future(ProtosActivation activation) {
        return new ProtosFutureValue(
                activation.prelude().orElseThrow().futurePrototype(), activation.executionDomain());
    }

    private static ProtosFutureValue resolved(ProtosActivation activation, Object value) {
        ProtosFutureValue future = future(activation);
        assertFalse(value instanceof ProtosFutureValue);
        future.resolve(value, activation);
        return future;
    }

    private static Object integer(long value) {
        return ProtosNumericValueSupport.integer(value);
    }

    private static ProtosClosureValue fn(ProtosNativeClosureBody body) {
        return ProtosClosureValue.nativeClosure(body);
    }

    // ------------------------------------------------------------ real loopback HTTP

    /**
     * Guest entries over the embedded {@code network} grant. {@code call} answers a summary {@code
     * status|name:value;...|body}, or {@code error:<kind>} for a failure with a kind.
     */
    private static final String PROGRAM =
            """
            http: import("std:http/UrlClient")
            ascii: (text) => {
                Encoding.Latin1.encode(text)
            }
            text: (bytes) => {
                Encoding.Latin1.decode(bytes)
            }
            decimal: (value) => {
                digits: ascii("")
                append: null
                append = (number) => {
                    quotient: number.div(10)
                    if(quotient > 0) {
                        append(quotient)
                    }
                    digits.add(48 + number.mod(10))
                }
                append(value)
                text(digits)
            }
            authorities: {
                network: network
                resolver: null
                trust: null
            }
            requestOf: (method) => {
                request: {
                    method: method
                    headers: [["Accept", ascii("*/*")]]
                    body: null
                }
                request
            }
            summary: (response) => {
                fields: ""
                response.headers.each((pair) => {
                    fields = fields + pair[0] + ":" + text(pair[1]) + ";"
                })
                decimal(response.status) + "|" + fields + "|" + text(response.body)
            }
            outcomeOf: (future) => {
                answer: null
                Error.handle(
                    () => {
                        answer = summary(future.value())
                    },
                    (error) => {
                        answer = "error:" + error.kind
                        error
                    }
                )
                answer
            }
            call: (url, method) => {
                outcomeOf(http.UrlClient.requestOne(authorities, url, requestOf(method), null))
            }
            callLimited: (url, maxBody) => {
                limits: {
                    maxResponseBodyBytes: maxBody
                }
                outcomeOf(http.UrlClient.requestOne(authorities, url, requestOf("GET"), limits))
            }
            admissionOf: (url) => {
                future: http.UrlClient.requestOne(authorities, url, requestOf("GET"), null)
                answer: "admitted"
                Error.handle(
                    () => {
                        future.value()
                    },
                    (error) => {
                        if(error.parent() === InvalidIOArgument) {
                            answer = "invalid"
                        }
                        error
                    }
                )
                answer
            }
            cancelWhileWaiting: (url) => {
                service: http.UrlClient.requestOne(authorities, url, requestOf("GET"), null)
                canceller: (() => {
                    service.cancel()
                    null
                }).future()
                cancelled: Cancelled.handle(
                    () => {
                        service.value()
                        false
                    },
                    (cancellation) => { true }
                )
                canceller.value()
                cancelled
            }
            0
            """;

    @Test
    void aNumericUrlSendsTheExactHostAndOriginFormTarget() throws Exception {
        try (Peer peer = new Peer()) {
            Origin origin = peer.serve("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello");
            String url = "http://127.0.0.1:" + origin.port() + "/index?x=1&x=2";
            assertEquals("200|Content-Length:5;|hello", peer.call("call", url, "GET"));
            assertEquals(
                    "GET /index?x=1&x=2 HTTP/1.1\r\nHost: 127.0.0.1:"
                            + origin.port()
                            + "\r\nAccept: */*\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                    origin.request());
        }
    }

    @Test
    void notFoundAndRedirectsAreReturnedUnmodified() throws Exception {
        try (Peer peer = new Peer()) {
            Origin missing = peer.serve("HTTP/1.1 404 Not Found\r\nContent-Length: 2\r\n\r\nno");
            assertEquals("404|Content-Length:2;|no", peer.call("call", url(missing), "GET"));
            Origin redirect =
                    peer.serve(
                            "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:1/x\r\n"
                                    + "Content-Length: 0\r\n\r\n");
            assertEquals(
                    "302|Location:http://127.0.0.1:1/x;Content-Length:0;|",
                    peer.call("call", url(redirect), "GET"));
        }
    }

    @Test
    void chunkedBodiesAndDuplicateFieldsAreBufferedInOrder() throws Exception {
        try (Peer peer = new Peer()) {
            Origin origin =
                    peer.serve(
                            "HTTP/1.1 200 OK\r\nSet-Cookie: a\r\nSet-Cookie: b\r\n"
                                    + "Transfer-Encoding: chunked\r\n\r\n"
                                    + "3\r\nabc\r\n2\r\nde\r\n0\r\n\r\n");
            assertEquals(
                    "200|Set-Cookie:a;Set-Cookie:b;Transfer-Encoding:chunked;|abcde",
                    peer.call("call", url(origin), "GET"));
        }
    }

    @Test
    void aHeadResponseHasNoBody() throws Exception {
        try (Peer peer = new Peer()) {
            Origin origin = peer.serve("HTTP/1.1 200 OK\r\nContent-Length: 9\r\n\r\n");
            assertEquals("200|Content-Length:9;|", peer.call("call", url(origin), "HEAD"));
            assertTrue(origin.request().startsWith("HEAD / HTTP/1.1\r\n"));
        }
    }

    @Test
    void malformedAndOversizedBodiesFailWithTheClientError() throws Exception {
        try (Peer peer = new Peer()) {
            Origin truncated = peer.serve("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nabc");
            assertEquals("error:malformedResponse", peer.call("call", url(truncated), "GET"));
            Origin oversized = peer.serve("HTTP/1.1 200 OK\r\n\r\n0123456789");
            assertEquals("error:responseLimit", peer.call("callLimited", url(oversized), 4));
        }
    }

    @Test
    void theConnectionIsClosedAfterTheBufferedResponse() throws Exception {
        try (Peer peer = new Peer()) {
            Origin origin = peer.serveThenCollect("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
            assertEquals("200|Content-Length:2;|ok", peer.call("call", url(origin), "GET"));
            assertEquals("", origin.afterResponse(), "the owning exchange closed the connection");
        }
    }

    @Test
    void cancellationWhileWaitingClosesTheConnection() throws Exception {
        try (Peer peer = new Peer()) {
            Origin waiting = peer.serveThenCollect(null);
            assertTrue(peer.callBoolean("cancelWhileWaiting", url(waiting)));
            // The peer observes EOF: the owning cleanup closed the connection, if one was made.
            waiting.closeListener();
        }
    }

    @Test
    void aHostNameWithoutAnExplicitResolverIsInadmissible() throws Exception {
        try (Peer peer = new Peer()) {
            assertEquals("invalid", peer.call("admissionOf", "http://localhost/"));
            assertEquals("invalid", peer.call("admissionOf", "http://127.0.0.1/#f"));
        }
    }

    @Test
    void noJavaHttpOrUrlEngineParticipates() {
        for (String name :
                new String[] {
                    "ProtosHttpUrlClient", "ProtosUrlClientFacility", "ProtosHttpRouter"
                }) {
            assertThrows(
                    ClassNotFoundException.class,
                    () -> Class.forName("com.guillermomolina.protos.execution." + name));
        }
    }

    private static String url(Origin origin) {
        return "http://127.0.0.1:" + origin.port() + "/";
    }

    /** The Protos side: one Polyglot Context with the embedded network grant. */
    private static final class Peer implements AutoCloseable {
        final Context context;

        Peer() {
            context =
                    Context.newBuilder(ProtosLanguage.ID)
                            .option("protos.CoreRoot", CORE)
                            .allowIO(IOAccess.newBuilder().allowHostSocketAccess(true).build())
                            .allowCreateThread(true)
                            .build();
            context.eval(ProtosLanguage.ID, PROGRAM);
        }

        String call(String name, Object... arguments) {
            return execute(name, arguments).asString();
        }

        boolean callBoolean(String name, Object... arguments) {
            return execute(name, arguments).asBoolean();
        }

        Value execute(String name, Object... arguments) {
            try {
                return context.getBindings(ProtosLanguage.ID).getMember(name).execute(arguments);
            } catch (PolyglotException failure) {
                throw new AssertionError(name + " failed: " + failure.getMessage(), failure);
            }
        }

        /** Answers one connection with response, then closes it. */
        Origin serve(String response) throws IOException {
            return new Origin(response, false);
        }

        /**
         * Writes response (when not null) and then collects every later octet until the
         * application closes its side.
         */
        Origin serveThenCollect(String response) throws IOException {
            return new Origin(response, true);
        }

        @Override
        public void close() {
            context.close(true);
        }
    }

    /** A Java origin-server fixture serving at most one connection on its own thread. */
    private static final class Origin {
        final ServerSocket listener;
        final CompletableFuture<String> request = new CompletableFuture<>();
        final CompletableFuture<String> after = new CompletableFuture<>();

        Origin(String response, boolean collect) throws IOException {
            listener = new ServerSocket();
            listener.bind(
                    new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0));
            listener.setSoTimeout(SAFETY_MILLIS);
            Thread thread =
                    new Thread(
                            () -> {
                                try (ServerSocket owned = listener;
                                        Socket socket = owned.accept()) {
                                    socket.setSoTimeout(SAFETY_MILLIS);
                                    InputStream in = socket.getInputStream();
                                    OutputStream out = socket.getOutputStream();
                                    if (response == null) {
                                        request.complete(readToEnd(in));
                                        return;
                                    }
                                    request.complete(readRequest(in));
                                    out.write(response.getBytes(StandardCharsets.ISO_8859_1));
                                    out.flush();
                                    if (collect) {
                                        socket.shutdownOutput();
                                        after.complete(readToEnd(in));
                                    }
                                } catch (Throwable failure) {
                                    request.completeExceptionally(failure);
                                    after.completeExceptionally(failure);
                                }
                            },
                            "url-origin");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return listener.getLocalPort();
        }

        String request() throws Exception {
            return request.get(SAFETY_MILLIS, TimeUnit.MILLISECONDS);
        }

        String afterResponse() throws Exception {
            return after.get(SAFETY_MILLIS, TimeUnit.MILLISECONDS);
        }

        /**
         * Waits until the fixture finished: either the connection reached EOF, or no connection
         * was accepted before the cancellation and the listener timed out.
         */
        void closeListener() throws Exception {
            try {
                request.get(SAFETY_MILLIS * 2L, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.ExecutionException noConnection) {
                // A cancellation before the connect completed leaves nothing to close.
            }
        }

        private static String readRequest(InputStream in) throws IOException {
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            while (!head.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n")) {
                int octet = in.read();
                if (octet < 0) {
                    throw new IOException("incomplete request head");
                }
                head.write(octet);
            }
            return head.toString(StandardCharsets.ISO_8859_1);
        }

        private static String readToEnd(InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                in.transferTo(out);
            } catch (java.net.SocketException reset) {
                // A closed peer may reset the connection.
            }
            return out.toString(StandardCharsets.ISO_8859_1);
        }
    }
}

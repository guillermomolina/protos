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
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosTask;
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
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * I127 (D218-B, HTTP.md §16): the public {@code std:http/Client} module.
 *
 * <p>The first group runs over a native in-memory duplex in a bootstrapped Prelude, so every live
 * Task is attributable: the root Task, the one service Task, and any Task the snippet creates.
 *
 * <p>The second group uses a standard Polyglot Context with real loopback TCP. Java {@link
 * ServerSocket} fixtures play the origin server; the Protos application acquires each connection
 * explicitly with {@code network.connectTcp} and hands only the resulting duplex to the Client,
 * which never acquires a Network itself. All HTTP behavior is the Protos library's: no Java HTTP
 * engine participates. Every wait is bounded and no verdict depends on a sleep.
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
final class ProtosHttpClientIntegrationTest {
    private static final Path CORE_PATH = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final String CORE = CORE_PATH.toAbsolutePath().toString();
    private static final int SAFETY_MILLIS = (int) TimeUnit.SECONDS.toMillis(5);
    private static final String GET_BYTES =
            "GET / HTTP/1.1\r\nHost: example.org\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";

    // ------------------------------------------------------------ in-memory harness

    private static final String HARNESS_PRELUDE =
            """
            http: import("std:http/Client")
            Assertions: import("std:test/Assertions")
            ascii: (text) => {
                Encoding.Latin1.encode(text)
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
            get: () => {
                request: {
                    method: "GET"
                    target: ascii("/")
                    headers: [["Host", ascii("example.org")]]
                    body: null
                }
                request
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
    void invalidAdmissionCreatesNoTaskSessionOrIo() {
        Run run =
                run(
                        """
                        before: liveTasks()
                        invoked: false
                        callback: (response) => {
                            invoked = true
                            null
                        }
                        unknownLimit: {
                            maxHeadrFields: 1
                        }
                        framed: get()
                        framed.headers = [["Host", ascii("a")], ["Content-Length", ascii("0")]]
                        first: http.Client.exchangeOne(dx, get(), callback, unknownLimit)
                        second: http.Client.exchangeOne.owning(dx, framed, callback, null)
                        Assertions.require(liveTasks() == before)
                        firstFailure: failureOf(first)
                        secondFailure: failureOf(second)
                        Assertions.require(firstFailure.parent() === InvalidIOArgument)
                        Assertions.require(secondFailure.parent() === InvalidIOArgument)
                        Assertions.require(firstFailure !== secondFailure)
                        Assertions.require(liveTasks() == before)
                        Assertions.require(!invoked)
                        true
                        """);
        run.assertCompleted();
        assertEquals(0, run.duplex.reads);
        assertEquals(0, run.duplex.writes);
        assertEquals(0, run.duplex.closes);
    }

    @Test
    void admittedExchangeRunsInExactlyOneServiceTask() {
        Run run =
                run(
                        """
                        dx.feed(ascii("HTTP/1.1 200 OK\\r\\nContent-Length: 0\\r\\n\\r\\n"))
                        dx.end()
                        before: liveTasks()
                        inCallback: 0
                        service: http.Client.exchangeOne(dx, get(), (response) => {
                            inCallback = liveTasks()
                            7
                        }, null)
                        Assertions.require(liveTasks() == before + 1)
                        Assertions.require(service.value() == 7)
                        Assertions.require(inCallback == before + 1)
                        Assertions.require(liveTasks() == before)
                        true
                        """);
        run.assertCompleted();
        assertEquals(GET_BYTES, run.duplex.output());
        assertEquals(0, run.duplex.closes);
    }

    @Test
    void responseBodyReadsCreateNoTask() {
        Run run =
                run(
                        """
                        dx.feed(ascii("HTTP/1.1 200 OK\\r\\nTransfer-Encoding: chunked\\r\\n\\r\\n"))
                        dx.feed(ascii("3\\r\\nabc\\r\\n"))
                        dx.feed(ascii("4\\r\\ndefg\\r\\n0\\r\\n\\r\\n"))
                        dx.end()
                        before: liveTasks()
                        counts: []
                        received: ascii("")
                        service: http.Client.exchangeOne(dx, get(), (response) => {
                            done: false
                            while(!done) {
                                chunk: response.body.read(2).value()
                                counts = Array(...counts, liveTasks())
                                if(chunk === null) {
                                    done = true
                                } else {
                                    chunk.each((octet) => {
                                        received.add(octet)
                                    })
                                }
                            }
                            null
                        }, null)
                        service.value()
                        Assertions.require(Encoding.Latin1.decode(received) == "abcdefg")
                        Assertions.require(counts.size() >= 5)
                        counts.each((count) => {
                            Assertions.require(count == before + 1)
                        })
                        Assertions.require(liveTasks() == before)
                        true
                        """);
        run.assertCompleted();
    }

    @Test
    void pendingBodyReadSuspendsAndResumesTheSameServiceTask() {
        Run run =
                run(
                        """
                        dx.feed(ascii("HTTP/1.1 200 OK\\r\\nContent-Length: 5\\r\\n\\r\\n"))
                        sameTask: false
                        content: null
                        service: http.Client.exchangeOne(dx, get(), (response) => {
                            first: taskId()
                            content = Encoding.Latin1.decode(response.body.read(10).value())
                            sameTask = first == taskId()
                            content
                        }, null)
                        releaser: (() => {
                            dx.deliver(ascii("hello"))
                        }).future()
                        Assertions.require(service.value() == "hello")
                        releaser.value()
                        Assertions.require(sameTask)
                        true
                        """);
        run.assertCompleted();
    }

    @Test
    void owningCancellationBeforeStartClosesBeforeTheFutureBecomesTerminal() {
        Run run =
                run(
                        """
                        dx.holdClose()
                        dx.baseline()
                        invoked: false
                        service: http.Client.exchangeOne.owning(dx, get(), (response) => {
                            invoked = true
                            null
                        }, null)
                        dx.track(service)
                        service.cancel()
                        releaser: (() => {
                            dx.finishClose()
                        }).future()
                        cancelled: false
                        Cancelled.handle(
                            () => {
                                service.value()
                            },
                            (cancellation) => {
                                cancelled = true
                                cancellation
                            }
                        )
                        releaser.value()
                        Assertions.require(cancelled)
                        Assertions.require(!invoked)
                        Assertions.require(liveTasks() == 1)
                        true
                        """);
        run.assertCompleted();
        assertEquals(1, run.duplex.closes);
        assertEquals(0, run.duplex.reads);
        assertEquals(0, run.duplex.writes);
        assertEquals(ProtosFutureValue.State.PENDING, run.duplex.serviceStateAtClose);
        assertEquals(ProtosFutureValue.State.PENDING, run.duplex.serviceStateAtCloseCompletion);
        assertEquals(ProtosFutureValue.State.CANCELLED, run.duplex.tracked.state());
        // Root, service and releaser Tasks only: the cleanup runs in the service Task.
        assertEquals(run.duplex.baselineTasks + 2, run.duplex.tasksAtClose);
    }

    @Test
    void theFacilityIsModulePrivate() {
        Run run =
                run(
                        """
                        Assertions.require(!http.hasSlot("_httpAsyncFacility"))
                        Assertions.require(!http.Client.hasSlot("_httpAsyncFacility"))
                        Assertions.signals(SlotNotFound, () => { _httpAsyncFacility })
                        Assertions.signals(SlotNotFound, () => { Client })
                        true
                        """);
        run.assertCompleted();
    }

    @Test
    void noJavaHttpClientEngineParticipates() {
        // The Client is Protos source over the shared private bridge; no parallel Java facility,
        // parser or session exists for it.
        for (String name :
                new String[] {
                    "ProtosHttpClientFacility", "ProtosHttpClient", "ProtosHttpClientSession"
                }) {
            assertThrows(
                    ClassNotFoundException.class,
                    () -> Class.forName("com.guillermomolina.protos.execution." + name));
        }
    }

    private static Run run(String body) {
        ProtosActivation activation = prelude.newModuleActivation();
        Duplex duplex = new Duplex();
        ProtosObjectValue context = activation.context();
        context.createLocalSlot("dx", duplex.object);
        Map<ProtosTask, Integer> taskIds = new IdentityHashMap<>();
        context.createLocalSlot(
                "liveTasks", fn((a, x) -> integer(a.executionDomain().liveTaskCount())));
        context.createLocalSlot(
                "taskId",
                fn(
                        (a, x) -> {
                            ProtosTask task = a.task().orElseThrow();
                            return integer(
                                    taskIds.computeIfAbsent(task, ignored -> taskIds.size()));
                        }));
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(HARNESS_PRELUDE + body, activation);
        return new Run(duplex, outcome);
    }

    private record Run(Duplex duplex, ProtosExecutionOutcome outcome) {
        void assertCompleted() {
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () -> "snippet failed: " + outcome.error());
            assertSame(ProtosBooleanValue.TRUE, outcome.value());
        }
    }

    /**
     * In-memory duplex: reads answer queued input, null after {@code end()}, or a pending Future
     * until {@code deliver(bytes)}; writes are captured and counted.
     */
    private static final class Duplex {
        final ProtosObjectValue object = new ProtosObjectValue(ProtosObjectValue.rootObject());
        final ArrayDeque<ProtosBytesValue> input = new ArrayDeque<>();
        final ByteArrayOutputStream written = new ByteArrayOutputStream();
        boolean ended;
        ProtosFutureValue pendingRead;
        boolean holdClose;
        ProtosFutureValue pendingClose;
        ProtosFutureValue tracked;
        ProtosFutureValue.State serviceStateAtClose;
        ProtosFutureValue.State serviceStateAtCloseCompletion;
        int reads;
        int writes;
        int closes;
        int baselineTasks;
        int tasksAtClose;

        Duplex() {
            slot("read", (a, x) -> read(a));
            slot(
                    "write",
                    (a, x) -> {
                        writes++;
                        written.writeBytes(((ProtosBytesValue) x.get(0)).octetSnapshot());
                        return resolved(a, object);
                    });
            slot("close", (a, x) -> close(a));
            slot(
                    "baseline",
                    (a, x) -> {
                        baselineTasks = a.executionDomain().liveTaskCount();
                        return ProtosNullValue.INSTANCE;
                    });
            slot(
                    "feed",
                    (a, x) -> {
                        input.addLast((ProtosBytesValue) x.get(0));
                        return ProtosNullValue.INSTANCE;
                    });
            slot(
                    "end",
                    (a, x) -> {
                        ended = true;
                        return ProtosNullValue.INSTANCE;
                    });
            slot(
                    "deliver",
                    (a, x) -> {
                        ProtosBytesValue bytes = (ProtosBytesValue) x.get(0);
                        if (pendingRead != null) {
                            ProtosFutureValue waiting = pendingRead;
                            pendingRead = null;
                            waiting.resolve(bytes, a);
                        } else {
                            input.addLast(bytes);
                        }
                        return ProtosNullValue.INSTANCE;
                    });
            slot(
                    "holdClose",
                    (a, x) -> {
                        holdClose = true;
                        return ProtosNullValue.INSTANCE;
                    });
            slot(
                    "finishClose",
                    (a, x) -> {
                        serviceStateAtCloseCompletion = tracked.state();
                        pendingClose.resolve(ProtosNullValue.INSTANCE, a);
                        return ProtosNullValue.INSTANCE;
                    });
            slot(
                    "track",
                    (a, x) -> {
                        tracked = (ProtosFutureValue) x.get(0);
                        return ProtosNullValue.INSTANCE;
                    });
        }

        private void slot(String name, ProtosNativeClosureBody body) {
            object.createLocalSlot(name, fn(body));
        }

        private Object read(ProtosActivation activation) {
            reads++;
            if (!input.isEmpty()) {
                return resolved(activation, input.removeFirst());
            }
            if (ended) {
                return resolved(activation, ProtosNullValue.INSTANCE);
            }
            pendingRead = future(activation);
            return pendingRead;
        }

        private Object close(ProtosActivation activation) {
            closes++;
            tasksAtClose = activation.executionDomain().liveTaskCount();
            if (tracked != null) {
                serviceStateAtClose = tracked.state();
            }
            if (holdClose) {
                pendingClose = future(activation);
                return pendingClose;
            }
            return resolved(activation, ProtosNullValue.INSTANCE);
        }

        String output() {
            return written.toString(StandardCharsets.ISO_8859_1);
        }
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

    // ------------------------------------------------------------ real loopback TCP

    /**
     * The initial module. Each entry acquires its own TCP connection explicitly and answers a
     * String summary: {@code status|body}, or {@code error:<kind>} for a ClientError.
     */
    private static final String PROGRAM =
            """
            http: import("std:http/Client")
            IpAddress: import("std:network/IpAddresses").IpAddress
            IpEndpoint: import("std:network/IpEndpoints").IpEndpoint
            loopback: IpAddress(4, 2130706433)
            ascii: (text) => {
                Encoding.Latin1.encode(text)
            }
            text: (bytes) => {
                Encoding.Latin1.decode(bytes)
            }
            connect: (port) => {
                network.connectTcp(IpEndpoint(loopback, port)).value()
            }
            requestOf: (method, target, body) => {
                request: {
                    method: method
                    target: ascii(target)
                    headers: [["Host", ascii("127.0.0.1")], ["Accept", ascii("*/*")]]
                    body: body
                }
                request
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
            summary: (response) => {
                collected: ""
                done: false
                while(!done) {
                    chunk: response.body.read(7).value()
                    if(chunk === null) {
                        done = true
                    } else {
                        collected = collected + text(chunk)
                    }
                }
                decimal(response.status) + "|" + collected
            }
            outcomeOf: (future) => {
                http.ClientError.handle(
                    () => {
                        future.value()
                    },
                    (error) => {
                        "error:" + error.kind
                    }
                )
            }
            exchange: (port, method, target, body) => {
                connection: connect(port)
                bodyBytes: null
                if(body !== null) {
                    bodyBytes = ascii(body)
                }
                answer: outcomeOf(http.Client.exchangeOne(connection, requestOf(method, target, bodyBytes), summary, null))
                connection.close().value()
                answer
            }
            get: (port) => {
                exchange(port, "GET", "/index?x=1", null)
            }
            post: (port, body) => {
                exchange(port, "POST", "/submit", body)
            }
            // Borrowed: the connection stays usable by the application afterwards.
            // The peer half-closed only its output and keeps reading, so this
            // write must succeed rather than observe any remote-closure failure.
            borrowedThenWrite: (port) => {
                connection: connect(port)
                answer: outcomeOf(http.Client.exchangeOne(connection, requestOf("GET", "/", null), summary, null))
                connection.write(ascii("after")).value()
                connection.close().value()
                answer
            }
            // Owning: the Client closed the connection exactly once. The later
            // write targets a locally closed resource, so IOLifecycleError is the
            // lifecycle contract here; the peer is still open and reading, so no
            // remote EOF, reset, or transport failure is involved.
            owningThenWrite: (port) => {
                connection: connect(port)
                answer: outcomeOf(http.Client.exchangeOne.owning(connection, requestOf("GET", "/", null), summary, null))
                closed: IOLifecycleError.handle(
                    () => {
                        connection.write(ascii("late")).value()
                        false
                    },
                    (error) => { true }
                )
                if(closed) {
                    answer = answer + "|closed"
                }
                answer
            }
            cancelOwningBeforeStart: (port) => {
                connection: connect(port)
                service: http.Client.exchangeOne.owning(connection, requestOf("GET", "/", null), summary, null)
                service.cancel()
                Cancelled.handle(
                    () => {
                        service.value()
                        false
                    },
                    (cancellation) => { true }
                )
            }
            // The cancellation reaches the service while it waits for the
            // response the Java peer withholds.
            cancelOwningWhileWaiting: (port) => {
                diagnosed(() => {
                    cancelWhileWaiting(port)
                })
            }
            cancelWhileWaiting: (port) => {
                connection: connect(port)
                service: http.Client.exchangeOne.owning(connection, requestOf("GET", "/", null), summary, null)
                // cancel() answers the Future itself; the canceller answers null so
                // that its own Future does not adopt the cancelled service.
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
            // Test diagnostics: names the standard prototype an escaped guest
            // Error delegates from, plus its kind and message when present.
            describeError: (error) => {
                names: ["ClientError", "IOLifecycleError", "InvalidIOArgument", "IOError", "SlotNotFound", "Cancelled", "Error"]
                prototypes: [http.ClientError, IOLifecycleError, InvalidIOArgument, IOError, SlotNotFound, Cancelled, Error]
                found: "unknown"
                index: 0
                while((found == "unknown") && (index < names.size())) {
                    // Prototype matching uses ordinary handler selection.
                    prototype: prototypes[index]
                    name: names[index]
                    // A non-matching prototype lets the Error reach the outer
                    // handler, which only discards it.
                    Error.handle(
                        () => {
                            prototype.handle(
                                () => {
                                    error.signal()
                                },
                                (matched) => {
                                    found = name
                                    matched
                                }
                            )
                        },
                        (unmatched) => {
                            unmatched
                        }
                    )
                    index = index + 1
                }
                if(error.hasSlot("kind")) {
                    found = found + " kind=" + error.kind
                }
                if(error.hasSlot("message")) {
                    found = found + " message=" + error.message
                }
                found
            }
            // Runs body; an escaped Error is answered as a "guest-error:" String
            // naming it, which Peer turns into a descriptive test failure.
            diagnosed: (body) => {
                result: null
                Error.handle(
                    () => {
                        result = body()
                    },
                    (error) => {
                        result = "guest-error: " + describeError(error)
                        error
                    }
                )
                result
            }
            // One exchange inside a spawned Actor over that Actor's own module
            // instance and in-memory duplex.
            inActor: () => {
                stage: "spawn"
                answer: null
                Error.handle(
                    () => {
                        worker: Actor.spawn("app:worker", "start")
                        stage = "request"
                        answer = worker.request("exchange").value()
                    },
                    (error) => {
                        answer = "guest-error: " + describeError(error) + " at root stage " + stage
                        error
                    }
                )
                answer
            }
            0
            """;

    private static final String WORKER =
            """
            http: import("std:http/Client")
            // Performs one in-memory exchange; mark(name) records the stage reached.
            runExchange: (mark) => {
                response: Encoding.Latin1.encode("HTTP/1.1 200 OK\\r\\nContent-Length: 2\\r\\n\\r\\nok")
                delivered: false
                output: Encoding.Latin1.encode("")
                duplex: {
                    read: (maxBytes) => {
                        chunk: null
                        if(!delivered) {
                            delivered = true
                            chunk = response
                        }
                        (() => { chunk }).future()
                    }
                    write: (bytes) => {
                        bytes.each((octet) => {
                            output.add(octet)
                        })
                        (() => { null }).future()
                    }
                }
                request: {
                    method: "GET"
                    target: Encoding.Latin1.encode("/")
                    headers: [["Host", Encoding.Latin1.encode("worker")]]
                    body: null
                }
                mark("admission")
                service: http.Client.exchangeOne(duplex, request, (answer) => {
                    Encoding.Latin1.decode(answer.body.read(10).value())
                }, null)
                mark("exchange")
                result: service.value()
                mark("result")
                sameInstance: "false"
                if(import("std:http/Client") === http) {
                    sameInstance = "true"
                }
                written: "partial"
                if(Encoding.Latin1.decode(output) == "GET / HTTP/1.1\\r\\nHost: worker\\r\\nContent-Length: 0\\r\\nConnection: close\\r\\n\\r\\n") {
                    written = "exact"
                }
                result + "|" + written + "|" + sameInstance
            }
            start: () => {
                {
                    exchange: () => {
                        stage: "setup"
                        outcome: null
                        Error.handle(
                            () => {
                                outcome = runExchange((name) => {
                                    stage = name
                                })
                            },
                            (error) => {
                                outcome = "guest-error: worker failed at stage " + stage
                                error
                            }
                        )
                        outcome
                    }
                }
            }
            """;

    @Test
    void realGetAndPostWriteExactRequestBytes() throws Exception {
        try (Peer peer = new Peer()) {
            Origin get = peer.serve("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello");
            assertEquals("200|hello", peer.call("get", get.port()));
            assertEquals(
                    "GET /index?x=1 HTTP/1.1\r\nHost: 127.0.0.1\r\nAccept: */*\r\n"
                            + "Content-Length: 0\r\nConnection: close\r\n\r\n",
                    get.request());
            Origin post = peer.serve("HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n");
            assertEquals("201|", peer.call("post", post.port(), "a=1&b=2"));
            assertEquals(
                    "POST /submit HTTP/1.1\r\nHost: 127.0.0.1\r\nAccept: */*\r\n"
                            + "Content-Length: 7\r\nConnection: close\r\n\r\na=1&b=2",
                    post.request());
        }
    }

    @Test
    void notFoundAndServerErrorAreOrdinaryResponses() throws Exception {
        try (Peer peer = new Peer()) {
            assertEquals(
                    "404|missing",
                    peer.call(
                            "get",
                            peer.serve("HTTP/1.1 404 Not Found\r\nContent-Length: 7\r\n\r\nmissing")
                                    .port()));
            assertEquals(
                    "500|",
                    peer.call(
                            "get",
                            peer.serve("HTTP/1.1 500 Internal\r\nContent-Length: 0\r\n\r\n")
                                    .port()));
        }
    }

    @Test
    void informationalResponsesPrecedeTheFinalResponse() throws Exception {
        try (Peer peer = new Peer()) {
            Origin origin =
                    peer.serve(
                            "HTTP/1.1 100 Continue\r\n\r\n"
                                    + "HTTP/1.1 103 Early Hints\r\nLink: </a.css>\r\n\r\n"
                                    + "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
            assertEquals("200|ok", peer.call("get", origin.port()));
        }
    }

    @Test
    void chunkedAndCloseDelimitedBodiesAreDecoded() throws Exception {
        try (Peer peer = new Peer()) {
            Origin chunked =
                    peer.serve(
                            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
                                    + "5;ext=1\r\nhello\r\n6\r\n world\r\n0\r\nX-T: t\r\n\r\n");
            assertEquals("200|hello world", peer.call("get", chunked.port()));
            Origin closeDelimited = peer.serve("HTTP/1.1 200 OK\r\n\r\nuntil the peer closes");
            assertEquals("200|until the peer closes", peer.call("get", closeDelimited.port()));
        }
    }

    @Test
    void arbitraryTcpSegmentationIsParsedIdentically() throws Exception {
        String response =
                "HTTP/1.1 103 Early Hints\r\nLink: </a>\r\n\r\n"
                        + "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
                        + "3\r\nabc\r\n2\r\nde\r\n0\r\n\r\n";
        try (Peer peer = new Peer()) {
            for (int segment : new int[] {1, 2, 3, 5, 7, 13}) {
                Origin origin = peer.serveSegmented(response, segment);
                assertEquals("200|abcde", peer.call("get", origin.port()), "segment " + segment);
            }
        }
    }

    /**
     * A remote close is an ordinary ByteReadable EOF ({@code null}), never an IOLifecycleError: the
     * Client classifies EOF inside an incomplete message as a {@code malformedResponse} framing
     * failure.
     */
    @Test
    void aPeerClosingDuringAnIncompleteBodyIsMalformed() throws Exception {
        try (Peer peer = new Peer()) {
            assertEquals(
                    "error:malformedResponse",
                    peer.call(
                            "get",
                            peer.serve("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nabc").port()));
            assertEquals(
                    "error:malformedResponse",
                    peer.call(
                            "get",
                            peer.serve("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nab")
                                    .port()));
            assertEquals(
                    "error:malformedResponse",
                    peer.call("get", peer.serve("HTTP/1.1 200 OK\r\nContent-Le").port()));
        }
    }

    @Test
    void aHeldProducerBlocksOnlyTheBodyUntilReleased() throws Exception {
        try (Peer peer = new Peer()) {
            CountDownLatch release = new CountDownLatch(1);
            Origin origin =
                    peer.serveHeld(
                            "HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nfirst",
                            release,
                            "-last");
            Entry entry = new Entry(() -> peer.call("get", origin.port()));
            origin.awaitRequest();
            assertFalse(entry.outcome.isDone(), "the body waits for the held producer");
            release.countDown();
            assertEquals("200|first-last", entry.await());
        }
    }

    @Test
    void borrowedConnectionsStayOpenAndOwnedConnectionsAreClosed() throws Exception {
        try (Peer peer = new Peer()) {
            Origin borrowed =
                    peer.serveThenCollect("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
            assertEquals("200|ok", peer.call("borrowedThenWrite", borrowed.port()));
            assertEquals("after", borrowed.afterResponse());

            Origin owned = peer.serveThenCollect("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
            assertEquals("200|ok|closed", peer.call("owningThenWrite", owned.port()));
            assertEquals("", owned.afterResponse(), "the owning exchange closed the connection");
        }
    }

    @Test
    void cancellationClosesAnOwnedConnection() throws Exception {
        try (Peer peer = new Peer()) {
            Origin beforeStart = peer.serveThenCollect(null);
            assertTrue(peer.callBoolean("cancelOwningBeforeStart", beforeStart.port()));
            assertEquals("", beforeStart.received(), "nothing was written before the cancellation");

            Origin waiting = peer.serveThenCollect(null);
            assertTrue(peer.callBoolean("cancelOwningWhileWaiting", waiting.port()));
            // The peer observes EOF: the owning cleanup closed the connection.
            waiting.received();
        }
    }

    @Test
    void anExchangeInsideASpawnedActorUsesThatActorsModuleInstance() throws Exception {
        try (Peer peer = new Peer()) {
            assertEquals(
                    "ok|exact|true",
                    peer.call("inActor"));
        }
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
            ProtosEmbeddedModules.install(context, Map.of("app:worker", WORKER));
            context.eval(ProtosLanguage.ID, PROGRAM);
        }

        String call(String name, Object... arguments) {
            return execute(name, arguments).asString();
        }

        boolean callBoolean(String name, Object... arguments) {
            return execute(name, arguments).asBoolean();
        }

        /** Executes a member and reports the escaped guest Error, which carries no message. */
        Value execute(String name, Object... arguments) {
            try {
                Value result = member(name).execute(arguments);
                if (result.isString() && result.asString().startsWith("guest-error:")) {
                    throw new AssertionError(name + " failed: " + result.asString());
                }
                return result;
            } catch (PolyglotException failure) {
                String guest = "no guest object";
                if (failure.getGuestObject() != null) {
                    try {
                        guest = member("describeError").execute(failure.getGuestObject()).asString();
                    } catch (RuntimeException undescribable) {
                        guest = "undescribable: " + failure.getGuestObject();
                    }
                }
                throw new AssertionError(
                        name
                                + " failed: "
                                + guest
                                + " (cancelled="
                                + failure.isCancelled()
                                + ", message="
                                + failure.getMessage()
                                + ")",
                        failure);
            }
        }

        Value member(String name) {
            return context.getBindings(ProtosLanguage.ID).getMember(name);
        }

        /** Answers one connection with response, then closes it. */
        Origin serve(String response) throws IOException {
            return new Origin(response, Integer.MAX_VALUE, null, null, false);
        }

        /** Answers with response written in segments of size octets, each flushed. */
        Origin serveSegmented(String response, int size) throws IOException {
            return new Origin(response, size, null, null, false);
        }

        /** Writes response, then waits for release before writing rest. */
        Origin serveHeld(String response, CountDownLatch release, String rest)
                throws IOException {
            return new Origin(response, Integer.MAX_VALUE, release, rest, false);
        }

        /**
         * Writes response (when not null) and then collects every later octet until the
         * application closes its side.
         */
        Origin serveThenCollect(String response) throws IOException {
            return new Origin(response, Integer.MAX_VALUE, null, null, true);
        }

        @Override
        public void close() {
            context.close(true);
        }
    }

    /** A Java origin-server fixture serving exactly one connection on its own thread. */
    private static final class Origin {
        final ServerSocket listener;
        final CompletableFuture<String> request = new CompletableFuture<>();
        final CompletableFuture<String> after = new CompletableFuture<>();

        Origin(
                String response,
                int segment,
                CountDownLatch release,
                String rest,
                boolean collect)
                throws IOException {
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
                                    socket.setTcpNoDelay(true);
                                    InputStream in = socket.getInputStream();
                                    OutputStream out = socket.getOutputStream();
                                    if (response == null) {
                                        request.complete(readToEnd(in));
                                        return;
                                    }
                                    request.complete(readRequest(in));
                                    writeSegmented(out, response, segment);
                                    if (release != null) {
                                        if (!release.await(SAFETY_MILLIS, TimeUnit.MILLISECONDS)) {
                                            throw new IOException("the producer was not released");
                                        }
                                        writeSegmented(out, rest, Integer.MAX_VALUE);
                                    }
                                    if (collect) {
                                        socket.shutdownOutput();
                                        after.complete(readToEnd(in));
                                    }
                                } catch (Throwable failure) {
                                    request.completeExceptionally(failure);
                                    after.completeExceptionally(failure);
                                }
                            },
                            "http-origin");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return listener.getLocalPort();
        }

        String request() throws Exception {
            return request.get(SAFETY_MILLIS, TimeUnit.MILLISECONDS);
        }

        /** Everything the client sent, for a fixture that never responds. */
        String received() throws Exception {
            return request();
        }

        void awaitRequest() throws Exception {
            request();
        }

        String afterResponse() throws Exception {
            return after.get(SAFETY_MILLIS, TimeUnit.MILLISECONDS);
        }

        /** Reads one request head and its Content-Length body. */
        private static String readRequest(InputStream in) throws IOException {
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            while (!head.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n")) {
                int octet = in.read();
                if (octet < 0) {
                    throw new IOException("incomplete request head");
                }
                head.write(octet);
            }
            String text = head.toString(StandardCharsets.ISO_8859_1);
            int length = 0;
            for (String line : text.split("\r\n")) {
                if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                    length = Integer.parseInt(line.substring(15).trim());
                }
            }
            byte[] body = in.readNBytes(length);
            return text + new String(body, StandardCharsets.ISO_8859_1);
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

        private static void writeSegmented(OutputStream out, String text, int segment)
                throws IOException {
            byte[] octets = text.getBytes(StandardCharsets.ISO_8859_1);
            for (int offset = 0; offset < octets.length; offset += segment) {
                out.write(octets, offset, Math.min(segment, octets.length - offset));
                out.flush();
            }
        }
    }

    /** One Protos entry on its own thread, so the test thread can act while it waits. */
    private static final class Entry {
        final CompletableFuture<Object> outcome = new CompletableFuture<>();

        Entry(java.util.function.Supplier<String> body) {
            Thread thread =
                    new Thread(
                            () -> {
                                try {
                                    outcome.complete(body.get());
                                } catch (Throwable failure) {
                                    outcome.complete(failure);
                                }
                            },
                            "client-entry");
            thread.setDaemon(true);
            thread.start();
        }

        Object await() throws Exception {
            return outcome.get(SAFETY_MILLIS, TimeUnit.MILLISECONDS);
        }
    }
}

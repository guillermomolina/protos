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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * LIB025 (D215, HTTP.md): Task topology, Future identity, cancellation and lifecycle of the
 * public {@code std:http/Server} module over a deterministic in-memory duplex.
 *
 * <p>The duplex, its Futures and the probes are native fixtures so that every live Task is
 * attributable: the root Task, the one service Task, and any Task the snippet itself creates.
 * All HTTP behavior exercised here is the Protos library's; no TCP, filesystem or timer is used.
 */
final class ProtosHttpServerIntegrationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    private static final String PRELUDE =
            """
            http: import("std:http/Server")
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
            ok: (status) => {
                response: {
                    status: status
                    headers: []
                    body: null
                }
                response
            }
            """;

    private static ProtosPrelude prelude;

    @BeforeAll
    static void bootstrap() throws IOException {
        prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(CORE, new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
    }

    @Test
    void invalidArgumentsFailWithoutServiceTaskSessionOrIo() {
        Run run =
                run(
                        """
                        before: liveTasks()
                        invoked: false
                        handler: (request) => {
                            invoked = true
                            ok(200)
                        }
                        unknownLimit: {
                            maxHeadrFields: 1
                        }
                        zeroLimit: {
                            maxHeaderFields: 0
                        }
                        first: http.Server.serveOne(dx, handler, unknownLimit)
                        second: http.Server.serveOne.owning(dx, handler, zeroLimit)
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
        assertEquals(0, run.duplex.closes);
        assertEquals("", run.duplex.output());
    }

    @Test
    void admittedServiceCreatesExactlyOneServiceTask() {
        Run run =
                run(
                        """
                        dx.feed(ascii("GET / HTTP/1.1\\r\\nHost: a\\r\\n\\r\\n"))
                        dx.end()
                        before: liveTasks()
                        inHandler: 0
                        service: http.Server.serveOne(dx, (request) => {
                            inHandler = liveTasks()
                            ok(200)
                        }, null)
                        Assertions.require(liveTasks() == before + 1)
                        Assertions.require(service.value() === null)
                        Assertions.require(inHandler == before + 1)
                        Assertions.require(liveTasks() == before)
                        true
                        """);
        run.assertCompleted();
        assertEquals(
                "HTTP/1.1 200 \r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                run.duplex.output());
    }

    @Test
    void requestBodyReadsCreateNoTask() {
        Run run =
                run(
                        """
                        dx.feed(ascii("POST / HTTP/1.1\\r\\nHost: a\\r\\nTransfer-Encoding: chunked\\r\\n\\r\\n"))
                        dx.feed(ascii("3\\r\\nabc\\r\\n"))
                        dx.feed(ascii("4\\r\\ndefg\\r\\n0\\r\\n\\r\\n"))
                        dx.end()
                        before: liveTasks()
                        counts: []
                        received: ascii("")
                        service: http.Server.serveOne(dx, (request) => {
                            done: false
                            while(!done) {
                                chunk: request.body.read(2).value()
                                counts = Array(...counts, liveTasks())
                                if(chunk === null) {
                                    done = true
                                } else {
                                    chunk.each((octet) => {
                                        received.add(octet)
                                    })
                                }
                            }
                            ok(200)
                        }, null)
                        service.value()
                        Assertions.require(Encoding.Latin1.decode(received) == "abcdefg")
                        Assertions.require(counts.size() >= 5)
                        counts.each((count) => {
                            Assertions.require(count == before + 1)
                        })
                        true
                        """);
        run.assertCompleted();
    }

    @Test
    void pendingHandlerReadSuspendsAndResumesTheSameServiceTask() {
        Run run =
                run(
                        """
                        dx.feed(ascii("POST / HTTP/1.1\\r\\nHost: a\\r\\nContent-Length: 5\\r\\n\\r\\n"))
                        sameTask: false
                        content: null
                        service: http.Server.serveOne(dx, (request) => {
                            first: taskId()
                            content = Encoding.Latin1.decode(request.body.read(10).value())
                            sameTask = first == taskId()
                            ok(200)
                        }, null)
                        releaser: (() => {
                            dx.deliver(ascii("hello"))
                        }).future()
                        service.value()
                        releaser.value()
                        Assertions.require(sameTask)
                        Assertions.require(content == "hello")
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
                        service: http.Server.serveOne.owning(dx, (request) => {
                            invoked = true
                            ok(200)
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
                        true
                        """);
        run.assertCompleted();
        assertEquals(1, run.duplex.closes);
        assertEquals(0, run.duplex.reads);
        assertEquals("", run.duplex.output());
        assertEquals(ProtosFutureValue.State.PENDING, run.duplex.serviceStateAtClose);
        assertEquals(ProtosFutureValue.State.PENDING, run.duplex.serviceStateAtCloseCompletion);
        assertEquals(ProtosFutureValue.State.CANCELLED, run.duplex.tracked.state());
        // Root, service and releaser Tasks only: the cleanup runs in the service Task.
        assertEquals(run.duplex.baselineTasks + 2, run.duplex.tasksAtClose);
    }

    @Test
    void borrowedCancellationDuringHandlerSuspensionNeverClosesTheDuplex() {
        Run run =
                run(
                        """
                        dx.feed(ascii("POST / HTTP/1.1\\r\\nHost: a\\r\\nContent-Length: 5\\r\\n\\r\\n"))
                        resumed: false
                        service: null
                        service = http.Server.serveOne(dx, (request) => {
                            service.cancel()
                            request.body.read(5).value()
                            resumed = true
                            ok(200)
                        }, null)
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
                        Assertions.require(cancelled)
                        Assertions.require(!resumed)
                        true
                        """);
        run.assertCompleted();
        assertEquals(0, run.duplex.closes);
        assertEquals("", run.duplex.output());
    }

    @Test
    void lateFramingFailureSurvivesHandlingAndLaterReadsFailFreshly() {
        Run run =
                run(
                        """
                        dx.feed(ascii("POST / HTTP/1.1\\r\\nHost: a\\r\\nTransfer-Encoding: chunked\\r\\n\\r\\nzz\\r\\n"))
                        dx.end()
                        first: null
                        second: null
                        third: null
                        service: http.Server.serveOne(dx, (request) => {
                            first = failureOf(request.body.read(10))
                            second = failureOf(request.body.read(10))
                            third = failureOf(request.body.read(10))
                            ok(200)
                        }, null)
                        failure: failureOf(service)
                        Assertions.require(failure === first)
                        Assertions.require(first.parent() === http.HttpError)
                        Assertions.require(first.kind == "malformedRequest")
                        Assertions.require(first.status == 400)
                        Assertions.require(second.parent() === IOLifecycleError)
                        Assertions.require(third.parent() === IOLifecycleError)
                        Assertions.require(second !== third)
                        true
                        """);
        run.assertCompleted();
        assertEquals(
                "HTTP/1.1 400 \r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                run.duplex.output());
    }

    @Test
    void responseStreamingAlternatesReadsAndWritesWithoutTasks() {
        Run run =
                run(
                        """
                        dx.feed(ascii("GET / HTTP/1.1\\r\\nHost: a\\r\\n\\r\\n"))
                        dx.end()
                        before: liveTasks()
                        chunks: [ascii("ab"), ascii("cde"), null]
                        index: 0
                        counts: []
                        stream: {
                            read: (maxBytes) => {
                                dx.mark()
                                counts = Array(...counts, liveTasks())
                                chunk: chunks[index]
                                index = index + 1
                                dx.resolved(chunk)
                            }
                        }
                        service: http.Server.serveOne(dx, (request) => {
                            response: {
                                status: 200
                                headers: []
                                body: stream
                            }
                            response
                        }, null)
                        service.value()
                        counts.each((count) => {
                            Assertions.require(count == before + 1)
                        })
                        true
                        """);
        run.assertCompleted();
        assertEquals(
                List.of("write", "read", "write", "read", "write", "read", "write"),
                run.duplex.events);
        assertEquals(
                "HTTP/1.1 200 \r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
                        + "2\r\nab\r\n3\r\ncde\r\n0\r\n\r\n",
                run.duplex.output());
    }

    @Test
    void failureAfterCommitmentEmitsNoSecondFinalResponse() {
        Run run =
                run(
                        """
                        dx.feed(ascii("GET / HTTP/1.1\\r\\nHost: a\\r\\n\\r\\n"))
                        dx.end()
                        streamError: Error {}
                        reads: 0
                        stream: {
                            read: (maxBytes) => {
                                reads = reads + 1
                                answer: null
                                if(reads == 1) {
                                    answer = dx.resolved(ascii("abc"))
                                } else {
                                    answer = dx.failed(streamError)
                                }
                                answer
                            }
                        }
                        service: http.Server.serveOne(dx, (request) => {
                            response: {
                                status: 200
                                headers: []
                                body: stream
                            }
                            response
                        }, null)
                        Assertions.require(failureOf(service) === streamError)
                        true
                        """);
        run.assertCompleted();
        assertEquals(
                "HTTP/1.1 200 \r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
                        + "3\r\nabc\r\n",
                run.duplex.output());
    }

    @Test
    void ownedCloseFailureSupersedesTheEarlierFailure() {
        Run run =
                run(
                        """
                        dx.feed(ascii("GET / HTTP/1.0\\r\\nHost: a\\r\\n\\r\\n"))
                        dx.end()
                        closeError: Error {}
                        dx.failCloseWith(closeError)
                        service: http.Server.serveOne.owning(dx, (request) => {
                            ok(200)
                        }, null)
                        Assertions.require(failureOf(service) === closeError)
                        true
                        """);
        run.assertCompleted();
        assertEquals(1, run.duplex.closes);
        assertEquals(
                "HTTP/1.1 505 \r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                run.duplex.output());
    }

    // ---- harness ----

    private static Run run(String body) {
        ProtosActivation activation = prelude.newModuleActivation();
        Duplex duplex = new Duplex();
        ProtosObjectValue context = activation.context();
        context.createLocalSlot("dx", duplex.object);
        Map<ProtosTask, Integer> taskIds = new IdentityHashMap<>();
        context.createLocalSlot(
                "liveTasks",
                fn((a, x) -> integer(a.executionDomain().liveTaskCount())));
        context.createLocalSlot(
                "taskId",
                fn(
                        (a, x) -> {
                            ProtosTask task = a.task().orElseThrow();
                            return integer(taskIds.computeIfAbsent(task, ignored -> taskIds.size()));
                        }));
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(PRELUDE + body, activation);
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
     * until {@code deliver(bytes)}; writes, closes and stream marks are logged in order.
     */
    private static final class Duplex {
        final ProtosObjectValue object = new ProtosObjectValue(ProtosObjectValue.rootObject());
        final ArrayDeque<ProtosBytesValue> input = new ArrayDeque<>();
        final ByteArrayOutputStream written = new ByteArrayOutputStream();
        final List<String> events = new ArrayList<>();
        boolean ended;
        ProtosFutureValue pendingRead;
        boolean holdClose;
        ProtosFutureValue pendingClose;
        ProtosObjectValue closeError;
        ProtosFutureValue tracked;
        ProtosFutureValue.State serviceStateAtClose;
        ProtosFutureValue.State serviceStateAtCloseCompletion;
        int reads;
        int closes;
        int baselineTasks;
        int tasksAtClose;

        Duplex() {
            slot("read", (a, x) -> read(a));
            slot(
                    "write",
                    (a, x) -> {
                        events.add("write");
                        byte[] octets = ((ProtosBytesValue) x.get(0)).octetSnapshot();
                        written.writeBytes(octets);
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
                    "failCloseWith",
                    (a, x) -> {
                        closeError = (ProtosObjectValue) x.get(0);
                        return ProtosNullValue.INSTANCE;
                    });
            slot(
                    "track",
                    (a, x) -> {
                        tracked = (ProtosFutureValue) x.get(0);
                        return ProtosNullValue.INSTANCE;
                    });
            slot(
                    "mark",
                    (a, x) -> {
                        events.add("read");
                        return ProtosNullValue.INSTANCE;
                    });
            slot("resolved", (a, x) -> resolved(a, x.get(0)));
            slot(
                    "failed",
                    (a, x) -> {
                        ProtosFutureValue future = future(a);
                        future.fail((ProtosObjectValue) x.get(0));
                        return future;
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
            if (closeError != null) {
                ProtosFutureValue failed = future(activation);
                failed.fail(closeError);
                return failed;
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
}

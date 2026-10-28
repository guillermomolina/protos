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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * I126 (D121, ACTORS.md §24A, FUTURES_AND_TASKS.md §§23–24): a {@code close()} first committed
 * after an Actor's termination cutover is admitted exactly inside the cancellation cleanup of that
 * Actor's own Task, including the cleanup of a Bytecode-lowered {@code ensure}, and nowhere else.
 * The Actor owns a real loopback {@code TcpConnection} accepted through a delegated {@code
 * TcpAcceptor}; the client is a Java socket, so a released connection is observed as EOF.
 * {@code ActorRef.termination()} is awaited first: termination cannot precede that cleanup, so EOF
 * must already be available, and a socket timeout is a failure.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
final class ProtosTerminationCleanupCloseTest {
    private static final String CORE = Path.of("protos", "lib", "core").toAbsolutePath().toString();
    private static final int SAFETY_MILLIS = (int) TimeUnit.SECONDS.toMillis(5);
    private static final int OPEN_PROBE_MILLIS = 200;

    private static final String HOLDER =
            """
            start: (acceptor) => {
                connection: null
                other: null
                witness: null
                behavior: {
                    useWitness: (reference) => {
                        witness = reference
                        true
                    }
                    accept: () => {
                        connection = acceptor.accept().value()
                        true
                    }
                    acceptOther: () => {
                        other = acceptor.accept().value()
                        true
                    }
                    // A: before any cutover an ordinary close keeps working.
                    closeFirst: () => {
                        connection.close().value()
                        "closed"
                    }
                    // B: the turn stops its own Actor and continues without
                    // suspending, so the termination cancellation is only
                    // requested. Entering the nested owning-cleanup ensure
                    // awaits a not yet run Task, which observes that pending
                    // cancellation; the close then commits from the
                    // cancellation cleanup extent, and completes.
                    pending: () => {
                        later: (() => { null }).future()
                        Actor.current().stop()
                        (() => {
                            later.value()
                        }).ensure(() => {
                            connection.close().value()
                        })
                        null
                    }
                    // G: a Task created inside the cancellation cleanup cannot
                    // close the unrelated other connection. Two safe outcomes
                    // establish different facts: "rejected" means the Task
                    // attempted close() and received IOLifecycleError;
                    // "cancelled" means the Actor-termination cancellation
                    // reached the Task before it attempted close(), which does
                    // not by itself demonstrate a rejection. The witness
                    // records which; the cleanup's own close is admitted.
                    delegated: () => {
                        (() => {
                            connection.read(1).value()
                        }).ensure(() => {
                            outcome: "unset"
                            attempted: false
                            Error.handle(
                                () => {
                                    IOLifecycleError.handle(
                                        () => {
                                            (() => {
                                                attempted = true
                                                other.close().value()
                                            }).future().value()
                                            outcome = "closed"
                                        },
                                        (error) => {
                                            outcome = "rejected"
                                            error
                                        }
                                    )
                                },
                                (error) => {
                                    // Anything but "cancelled" is classified
                                    // only for diagnosis.
                                    outcome = "error"
                                    if(error.parent() === IOError) {
                                        outcome = "io"
                                    }
                                    if(error.parent() === Cancelled) {
                                        outcome = "cancelled"
                                        if(attempted) {
                                            outcome = "cancelledAfterAttempt"
                                        }
                                    }
                                    error
                                }
                            )
                            witness.send("record", outcome)
                            connection.close().value()
                        })
                    }
                    // C: the turn is suspended reading the connection when a
                    // later "halt" turn stops the Actor; the cancellation
                    // wakes it, and the cleanup of its ensure commits the close
                    // after the cutover, and it completes.
                    cleanup: () => {
                        (() => {
                            connection.read(1).value()
                        }).ensure(() => {
                            connection.close().value()
                        })
                    }
                    // D: after the cutover, an ordinary close outside any
                    // cancellation cleanup is still refused.
                    ordinary: () => {
                        Actor.current().stop()
                        IOLifecycleError.handle(
                            () => {
                                connection.close().value()
                                "closed"
                            },
                            (error) => { "rejected" }
                        )
                    }
                    // E: a nested ensure inside the cancellation cleanup still
                    // runs within that cleanup extent, so its close is admitted
                    // and completes; the Error escaping the cleanup then
                    // supersedes the cancellation (ERRORS.md, ensure).
                    superseded: () => {
                        (() => {
                            connection.read(1).value()
                        }).ensure(() => {
                            (() => {
                                Error().signal()
                            }).ensure(() => {
                                connection.close().value()
                            })
                        })
                    }
                    halt: () => {
                        Actor.current().stop()
                        null
                    }
                }
                behavior
            }
            """;

    private static final String WITNESS =
            """
            start: () => {
                recorded: "none"
                behavior: {
                    record: (outcome) => {
                        recorded = outcome
                        null
                    }
                    recorded: () => {
                        recorded
                    }
                }
                behavior
            }
            """;

    private static final String PROGRAM =
            """
            IpAddress: import("std:network/IpAddresses").IpAddress
            listener: null
            holder: null
            open: () => {
                request: {
                    ipVersion: 4
                    address: IpAddress(4, 2130706433)
                    port: null
                }
                listener = network.listenTcp(request).value()
                holder = Actor.spawn("app:holder", "start", listener.acceptCapability())
                listener.localPort()
            }
            accept: () => {
                holder.request("accept").value()
            }
            acceptOther: () => {
                holder.request("acceptOther").value()
            }
            witness: null
            useWitness: () => {
                witness = Actor.spawn("app:witness", "start")
                holder.request("useWitness", witness).value()
            }
            // The witness outlives the holder; its record was sent before
            // the holder terminated.
            delegatedOutcome: () => {
                witness.request("recorded").value()
            }
            halt: () => {
                holder.send("halt")
                true
            }
            // The request outcome is not the subject: a cancelled turn has no
            // normal reply.
            run: (mode) => {
                outcome: "failed"
                Error.handle(
                    () => {
                        outcome = holder.request(mode).value()
                    },
                    (error) => { error }
                )
                outcome
            }
            // Same-sender FIFO: the parked turn runs, and suspends in its
            // read, before the "halt" turn establishes the cutover.
            park: (mode) => {
                holder.request(mode)
                holder.send("halt")
                true
            }
            // F: resolves only after the Actor's Task cleanup completed.
            terminated: () => {
                holder.termination().value()
                listener.close().value()
                true
            }
            0
            """;

    @Test
    void cancellationCleanupClosesAfterTheTerminationCutover() throws Exception {
        try (Fixture x = new Fixture(); Socket client = x.connectAndAccept()) {
            x.park("cleanup");
            assertTrue(x.terminated());
            assertEquals("", readToEof(client), "the cleanup close released the connection");
        }
    }

    @Test
    void anOrdinaryCloseAfterTheCutoverIsStillRefused() throws Exception {
        try (Fixture x = new Fixture(); Socket client = x.connectAndAccept()) {
            assertEquals("rejected", x.run("ordinary").asString());
            assertTrue(x.terminated());
        }
    }

    @Test
    void aNestedCleanupInsideTheCancellationCleanupClosesBeforeASupersedingError()
            throws Exception {
        try (Fixture x = new Fixture(); Socket client = x.connectAndAccept()) {
            x.park("superseded");
            assertTrue(x.terminated());
            assertEquals("", readToEof(client));
        }
    }

    @Test
    void anOrdinaryCloseBeforeTerminationStillWorks() throws Exception {
        try (Fixture x = new Fixture(); Socket client = x.connectAndAccept()) {
            assertEquals("closed", x.run("closeFirst").asString());
            assertEquals("", readToEof(client));
            assertTrue(x.member("halt").execute().asBoolean());
            assertTrue(x.terminated());
        }
    }

    @Test
    void aPendingTerminationCancellationObservedByANestedEnsureAuthorizesItsClose()
            throws Exception {
        try (Fixture x = new Fixture(); Socket client = x.connectAndAccept()) {
            // The turn has no normal reply: its cancellation unwinds it.
            x.run("pending");
            // Termination waits for the cleanup close, so EOF is already available.
            assertTrue(x.terminated());
            assertEquals("", readToEof(client), "the cleanup close released the connection");
        }
    }

    @Test
    void aTaskCreatedInsideCancellationCleanupCannotCloseAnUnrelatedConnection()
            throws Exception {
        try (Fixture x = new Fixture();
                Socket client = x.connectAndAccept();
                Socket other = x.connectAndAcceptOther()) {
            assertTrue(x.member("useWitness").execute().asBoolean());
            x.park("delegated");
            assertTrue(x.terminated());
            String outcome = x.member("delegatedOutcome").execute().asString();
            assertTrue(
                    outcome.equals("rejected") || outcome.equals("cancelled"),
                    "the delegated Task must either attempt close() and receive "
                            + "IOLifecycleError (\"rejected\") or be cancelled before attempting "
                            + "close() (\"cancelled\"), but the outcome was: "
                            + outcome);
            assertEquals("", readToEof(client), "the cleanup's own close was admitted");
            // Additional evidence: the unauthorized close left the other connection open. The
            // client sockets are closed by try-with-resources and the server-owned ones by
            // the context close, also when an assertion fails.
            other.setSoTimeout(OPEN_PROBE_MILLIS);
            try {
                int read = other.getInputStream().read();
                throw new AssertionError("the delegated close was admitted: read " + read);
            } catch (SocketTimeoutException stillOpen) {
                // Expected: the Actor does not implicitly close the connection either.
            }
        }
    }

    /** Reads to EOF; a timeout means the connection was left open and fails the test. */
    private static String readToEof(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        try {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (SocketTimeoutException open) {
            throw new AssertionError("the connection was not released", open);
        } catch (java.net.SocketException reset) {
            // A reset also proves the release.
        }
        return out.toString(java.nio.charset.StandardCharsets.ISO_8859_1);
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
            ProtosEmbeddedModules.install(context, Map.of("app:holder", HOLDER, "app:witness", WITNESS));
            context.eval(ProtosLanguage.ID, PROGRAM);
            port = member("open").execute().asInt();
        }

        Value member(String name) {
            return context.getBindings(ProtosLanguage.ID).getMember(name);
        }

        Socket connectAndAccept() throws IOException {
            Socket socket = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port);
            socket.setSoTimeout(SAFETY_MILLIS);
            assertTrue(member("accept").execute().asBoolean(), "the holder owns the connection");
            return socket;
        }

        Socket connectAndAcceptOther() throws IOException {
            Socket socket = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port);
            socket.setSoTimeout(SAFETY_MILLIS);
            assertTrue(
                    member("acceptOther").execute().asBoolean(),
                    "the holder owns the other connection");
            return socket;
        }

        void park(String mode) {
            assertTrue(member("park").execute(mode).asBoolean());
        }

        Value run(String mode) {
            return member("run").execute(mode);
        }

        boolean terminated() {
            return member("terminated").execute().asBoolean();
        }

        @Override
        public void close() {
            context.close(true);
        }
    }
}

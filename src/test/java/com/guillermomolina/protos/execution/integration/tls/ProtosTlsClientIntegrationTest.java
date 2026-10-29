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
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * I130-B (D220–S12, TLS.md): the public {@code std:tls/Client} module against a real JSSE server.
 *
 * <p>Each snippet is guest Protos code in a bootstrapped Prelude, so every live Task is
 * attributable. The duplex {@code dx} is a native in-memory transport whose far end is a {@link
 * ProtosTlsTestPeer} JSSE server presenting the fixture chain: client writes reach the server at
 * once, and a pending client read resolves as soon as the server has ciphertext. Every TLS policy
 * exercised here (trust, identity, SNI, ALPN acceptance, Futures, retention, custody) is the
 * Protos library's; Java contributes only the engine and raw Unicode facts. PKIX uses the real
 * clock: the fixtures are valid from 2026-10-28T09:04:19Z through 2126; the I130-C3 IDNA fixtures
 * from their generation through 2126.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
final class ProtosTlsClientIntegrationTest {
    private static final Path CORE_PATH = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final String TLS13 = "TLSv1.3";
    private static final String TLS12 = "TLSv1.2";

    private static final String HARNESS_PRELUDE =
            """
            tls: import("std:tls/Client")
            http: import("std:http/Client")
            IpAddresses: import("std:network/IpAddresses")
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
            cancelledOf: (future) => {
                Cancelled.handle(
                    () => {
                        future.value()
                        false
                    },
                    (cancellation) => {
                        true
                    }
                )
            }
            kindOf: (future) => {
                failure: failureOf(future)
                Assertions.require(failure.parent() === tls.TlsError)
                failure.kind
            }
            trust: () => {
                tls.Trust.anchors([rootCa])
            }
            idnaTrust: () => {
                tls.Trust.anchors([idnaRootCa])
            }
            connect: () => {
                tls.Client.handshake(dx, "service.protos.test", trust(), {}).value()
            }
            readAll: (secure) => {
                collected: ascii("")
                done: false
                while(!done) {
                    chunk: secure.read(4096).value()
                    if(chunk === null) {
                        done = true
                    } else {
                        chunk.each((octet) => {
                            collected.add(octet)
                        })
                    }
                }
                text(collected)
            }
            """;

    private static ProtosPrelude prelude;
    private static Object kept;

    @BeforeAll
    static void bootstrap() throws IOException {
        prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE_PATH, new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
    }

    // ------------------------------------------------------------ handshake and data

    @Test
    void tls13HandshakeRunsInOneTaskAndDeliversAVerifiedDuplex() {
        Run run =
                run(
                        server(TLS13, TLS12),
                        """
                        before: liveTasks()
                        handshake: tls.Client.handshake(dx, "service.protos.test", trust(), {})
                        Assertions.require(liveTasks() == before + 1)
                        secure: handshake.value()
                        Assertions.require(liveTasks() == before)
                        Assertions.require(secure.hasSlot("read"))
                        Assertions.require(secure.hasSlot("write"))
                        Assertions.require(secure.hasSlot("close"))
                        Assertions.require(secure.write(ascii("ping")).value() === secure)
                        Assertions.require(dx.serverText() == "ping")
                        dx.serverSend(ascii("pong"))
                        reply: secure.read(100).value()
                        Assertions.require(text(reply) == "pong")
                        // Reads and writes create no Task.
                        Assertions.require(liveTasks() == before)
                        Assertions.require(secure.close().value() === secure)
                        Assertions.require(liveTasks() == before)
                        true
                        """);
        run.assertCompleted();
        assertEquals(TLS13, run.duplex.peer.server.getSession().getProtocol());
        assertEquals("http/1.1", run.duplex.peer.server.getApplicationProtocol());
        assertEquals(List.of(new SNIHostName("service.protos.test")), run.duplex.sni());
        // Borrowed: close_notify was sent, the transport was not closed.
        assertTrue(run.duplex.peer.server.isInboundDone());
        assertEquals(0, run.duplex.closes);
    }

    @Test
    void secureTls12IsPermittedAndTls13OnlyRefusesIt() {
        Run permitted =
                run(
                        server(TLS12),
                        """
                        secure: connect()
                        secure.write(ascii("v12")).value()
                        Assertions.require(dx.serverText() == "v12")
                        true
                        """);
        permitted.assertCompleted();
        assertEquals(TLS12, permitted.duplex.peer.server.getSession().getProtocol());
        assertTrue(
                permitted.duplex.peer.server.getSession().getCipherSuite().contains("_GCM_"));

        Run refused =
                run(
                        server(TLS12),
                        """
                        options: {
                            minimumVersion: "1.3"
                        }
                        // A refusal without conclusive version evidence is general.
                        kind: kindOf(tls.Client.handshake(dx, "service.protos.test", trust(), options))
                        Assertions.require(kind == "handshakeFailure")
                        true
                        """);
        refused.assertCompleted();
    }

    @Test
    void fragmentedRecordsInBothDirectionsAreReassembled() {
        Duplex duplex = server(TLS13);
        // Smaller than every handshake message and every data record.
        duplex.fragment = 97;
        Run run =
                run(
                        duplex,
                        """
                        secure: connect()
                        // 20480 octets: more than one maximal plaintext record.
                        payload: "0123456789"
                        while(payload.size() < 20000) {
                            payload = payload + payload
                        }
                        secure.write(ascii(payload)).value()
                        Assertions.require(dx.serverText() == payload)
                        dx.serverSend(ascii(payload))
                        dx.serverClose()
                        Assertions.require(readAll(secure) == payload)
                        true
                        """);
        run.assertCompleted();
    }

    // ------------------------------------------------------------ trust and identity

    @Test
    void anUnrelatedAnchorIsAnUntrustedPeer() {
        Run run =
                run(
                        server(TLS13),
                        """
                        other: tls.Trust.anchors([untrustedCa])
                        kind: kindOf(tls.Client.handshake(dx, "service.protos.test", other, {}))
                        Assertions.require(kind == "untrustedPeer")
                        true
                        """);
        run.assertCompleted();
        assertEquals(0, run.duplex.closes);
    }

    @Test
    void identityIsSubjectAltNameOnlyAndNeverTheCommonName() {
        // The service leaf's subject CN is cn-only.protos.test; it is not a SAN.
        assertIdentity("service-leaf", "\"cn-only.protos.test\"", false);
        // A leaf whose only name is CN=service.protos.test presents no DNS-ID.
        assertIdentity("cn-only-leaf", "\"service.protos.test\"", false);
        assertIdentity("service-leaf", "\"SERVICE.protos.test.\"", true);
        assertIdentity("service-leaf", "\"other.protos.test\"", false);
    }

    @Test
    void aWildcardMatchesExactlyOneLeftmostLabel() {
        assertIdentity("service-leaf", "\"api.wild.protos.test\"", true);
        assertIdentity("service-leaf", "\"a.b.wild.protos.test\"", false);
        assertIdentity("service-leaf", "\"wild.protos.test\"", false);
    }

    @Test
    void ipIdentitiesMatchExactAddressOctetsAndSendNoSni() {
        Duplex v4 = assertIdentity("service-leaf", "IpAddresses.v4(127, 0, 0, 1)", true);
        assertTrue(v4.sni().isEmpty());
        assertTrue(assertIdentity("service-leaf", "IpAddresses.parse(\"::1\")", true).sni().isEmpty());
        assertIdentity("service-leaf", "IpAddresses.v4(127, 0, 0, 2)", false);
        // An IPv4-mapped IPv6 address is not the IPv4 SAN.
        assertIdentity("service-leaf", "IpAddresses.parse(\"::ffff:127.0.0.1\")", false);
    }

    @Test
    void anALabelSubjectAltNameVerifiesAndSendsTheCanonicalLowercaseSni() {
        Run run =
                run(
                        idnaServer("idna-leaf"),
                        """
                        secure: tls.Client.handshake(dx, "XN--BCHER-KVA.Protos.Test.", idnaTrust(), {}).value()
                        secure.write(ascii("idna")).value()
                        Assertions.require(dx.serverText() == "idna")
                        true
                        """);
        run.assertCompleted();
        assertEquals(List.of(new SNIHostName("xn--bcher-kva.protos.test")), run.duplex.sni());
    }

    @Test
    void malformedALabelSubjectAltNamesAreIneligibleAfterChainVerification() {
        // The chain verifies; only the NR-LDH SAN is eligible.
        Run eligible =
                run(
                        idnaServer("idna-malformed-leaf"),
                        """
                        tls.Client.handshake(dx, "fallback.protos.test", idnaTrust(), {}).value()
                        true
                        """);
        eligible.assertCompleted();
        // A valid reference never matches the ineligible SANs: identity, not trust, fails.
        Run mismatch =
                run(
                        idnaServer("idna-malformed-leaf"),
                        """
                        Assertions.require(kindOf(tls.Client.handshake(dx, "xn--bcher-kva.protos.test", idnaTrust(), {})) == "identityMismatch")
                        true
                        """);
        mismatch.assertCompleted();
    }

    @Test
    void aWildcardOverAnALabelMatchesOneLabelAndSharpSIsNotSs() {
        assertIdnaIdentity("\"api.xn--fa-hia.protos.test\"", true);
        assertIdnaIdentity("\"xn--fa-hia.protos.test\"", false);
        assertIdnaIdentity("\"a.b.xn--fa-hia.protos.test\"", false);
        // IDNA2008: faß (xn--fa-hia) and fass are different names.
        assertIdnaIdentity("\"api.fass.protos.test\"", false);
        assertIdnaIdentity("\"bucher.protos.test\"", false);
    }

    @Test
    void presentedDnsIdsMustPassStrictIdnaValidation() {
        Run run =
                run(
                        server(TLS13),
                        """
                        Identity: import("std:tls/internal/Identity")
                        Assertions.require(Identity.dnsName(unicode, "*.xn--bcher-kva.protos.test", true) !== null)
                        Assertions.require(Identity.dnsName(unicode, "xn--4dbrk0ce.example", true) !== null)
                        invalid: [
                            "xn--zz.protos.test",
                            "*.xn--a.protos.test",
                            "xn--a-0hc.protos.test",
                            "xn--bucher-xyd.protos.test",
                            "xn--ab-j1t.protos.test",
                            "1a.xn--4dbrk0ce",
                            "*.ab--cd.protos.test",
                            "xn--*.protos.test",
                            "a.*.protos.test",
                            "bücher.protos.test"
                        ]
                        invalid.each((name) => {
                            Assertions.require(Identity.dnsName(unicode, name, true) === null)
                        })
                        reference: Identity.reference(unicode, "xn--fa-hia.protos.test")
                        Assertions.require(!Identity.matches(unicode, reference, invalid, []))
                        Assertions.require(Identity.matches(unicode, reference, Array(...invalid, "XN--FA-HIA.protos.test"), []))
                        Assertions.require(!Identity.matches(unicode, reference, ["fass.protos.test"], []))
                        fass: Identity.reference(unicode, "fass.protos.test")
                        Assertions.require(!Identity.matches(unicode, fass, ["xn--fa-hia.protos.test"], []))
                        true
                        """);
        run.assertCompleted();
    }

    @Test
    void invalidDnsReferencesAreRejectedAtAdmissionWithoutEffects() {
        Run run =
                run(
                        server(TLS13),
                        """
                        before: liveTasks()
                        ["xn--zz.test", "ab--cd.test", "xn--a-0hc.test", "xn--bucher-xyd.test", "bücher.test"].each((name) => {
                            failure: failureOf(tls.Client.handshake.owning(dx, name, trust(), {}))
                            Assertions.require(failure.parent() === InvalidIOArgument)
                            Assertions.require(liveTasks() == before)
                        })
                        true
                        """);
        run.assertCompleted();
        assertEquals(0, run.duplex.reads);
        assertEquals(0, run.duplex.writes);
        assertEquals(0, run.duplex.closes);
    }

    @Test
    void identityMismatchUnderOwningCustodyClosesTheTransportOnce() {
        Run run =
                run(
                        server(TLS13),
                        """
                        kind: kindOf(tls.Client.handshake.owning(dx, "other.protos.test", trust(), {}))
                        Assertions.require(kind == "identityMismatch")
                        true
                        """);
        run.assertCompleted();
        assertEquals(1, run.duplex.closes);
    }

    // ------------------------------------------------------------ ALPN

    @Test
    void alpnOffersOnlyHttp11AndAcceptsNoSelection() {
        Run none =
                run(
                        server((engine, offered) -> "", TLS13),
                        """
                        secure: connect()
                        true
                        """);
        none.assertCompleted();

        Run disabled =
                run(
                        server(TLS13),
                        """
                        options: {
                            alpn: null
                        }
                        secure: tls.Client.handshake(dx, "service.protos.test", trust(), options).value()
                        true
                        """);
        disabled.assertCompleted();
        assertEquals("", disabled.duplex.peer.server.getApplicationProtocol());
    }

    @Test
    void aPeerAbortingOverAlpnIsAGeneralHandshakeFailure() {
        Run run =
                run(
                        server((engine, offered) -> "h2", TLS13),
                        """
                        kind: kindOf(tls.Client.handshake(dx, "service.protos.test", trust(), {}))
                        Assertions.require(kind == "handshakeFailure")
                        true
                        """);
        run.assertCompleted();
    }

    // ------------------------------------------------------------ reads

    @Test
    void retainedPlaintextIsReturnedInOrderAcrossReads() {
        Run run =
                run(
                        server(TLS13),
                        """
                        secure: connect()
                        dx.serverSend(ascii("hello world"))
                        first: secure.read(5).value()
                        Assertions.require(text(first) == "hello")
                        // Fresh mutable Bytes: mutation does not reach later reads.
                        first.add(33)
                        Assertions.require(text(secure.read(100).value()) == " world")
                        true
                        """);
        run.assertCompleted();
    }

    @Test
    void aCancelledReadLosesNoCiphertextOrPlaintext() {
        Run run =
                run(
                        server(TLS13),
                        """
                        secure: connect()
                        before: liveTasks()
                        pending: secure.read(10)
                        // Only a request: a read whose source is already waiting on the
                        // transport may still complete (BYTE_IO.md §5.1).
                        pending.cancel()
                        dx.serverSend(ascii("kept"))
                        dx.serverClose()
                        received: ""
                        if(!cancelledOf(pending)) {
                            received = text(pending.value())
                        }
                        // Either outcome: every decrypted octet arrives exactly once.
                        received = received + readAll(secure)
                        Assertions.require(received == "kept")
                        Assertions.require(liveTasks() == before)
                        true
                        """);
        run.assertCompleted();
    }

    @Test
    void closeNotifyIsAuthenticatedEndAndRawEndIsTruncated() {
        Run clean =
                run(
                        server(TLS13),
                        """
                        secure: connect()
                        dx.serverSend(ascii("bye"))
                        dx.serverClose()
                        Assertions.require(readAll(secure) == "bye")
                        Assertions.require(secure.read(1).value() === null)
                        true
                        """);
        clean.assertCompleted();

        Run truncated =
                run(
                        server(TLS13),
                        """
                        secure: connect()
                        dx.serverSend(ascii("partial"))
                        dx.endTransport()
                        // Plaintext authenticated before the raw end is still returned.
                        Assertions.require(text(secure.read(100).value()) == "partial")
                        failure: failureOf(secure.read(100))
                        Assertions.require(failure.parent() === tls.TlsError)
                        Assertions.require(failure.kind == "truncated")
                        true
                        """);
        truncated.assertCompleted();
    }

    @Test
    void aTransportReadErrorKeepsItsIdentity() {
        Run run =
                run(
                        server(TLS13),
                        """
                        secure: connect()
                        transport: IOError {}
                        dx.failReadsWith(transport)
                        Assertions.require(failureOf(secure.read(10)) === transport)
                        true
                        """);
        run.assertCompleted();
    }

    // ------------------------------------------------------------ writes

    @Test
    void writesSnapshotTheirBytesAtInvocation() {
        Run run =
                run(
                        server(TLS13),
                        """
                        secure: connect()
                        bytes: ascii("abc")
                        written: secure.write(bytes)
                        bytes.add(100)
                        bytes[0] = 120
                        written.value()
                        Assertions.require(dx.serverText() == "abc")
                        Assertions.require(secure.write(ascii("")).value() === secure)
                        Assertions.require(failureOf(secure.write("text")).parent() === InvalidIOArgument)
                        true
                        """);
        run.assertCompleted();
    }

    @Test
    void writeCapacityIsBoundedWithoutPoisoningTheStream() {
        Run run =
                run(
                        server(TLS13),
                        """
                        options: {
                            maxQueuedWriteBytes: 10
                        }
                        secure: tls.Client.handshake(dx, "service.protos.test", trust(), options).value()
                        dx.holdWrites()
                        first: secure.write(ascii("12345678"))
                        rejected: secure.write(ascii("abcd"))
                        Assertions.require(failureOf(rejected).parent() === IOCapacityExhausted)
                        // Backpressure: the admitted write stays pending.
                        last: secure.write(ascii("90"))
                        dx.releaseWrites()
                        Assertions.require(first.value() === secure)
                        Assertions.require(last.value() === secure)
                        Assertions.require(dx.serverText() == "1234567890")
                        // Released retention admits new writes again.
                        secure.write(ascii("abcd")).value()
                        Assertions.require(dx.serverText() == "1234567890abcd")
                        true
                        """);
        run.assertCompleted();
    }

    @Test
    void aFailedLowerWriteFailsOutputClosedWithoutFurtherRecords() {
        // ByteWritable does not reveal the committed prefix of a failed write, so even a failure
        // that in fact had zero effect cannot be proven harmless to the record stream.
        Run run =
                run(
                        server(TLS13),
                        """
                        secure: tls.Client.handshake.owning(dx, "service.protos.test", trust(), {}).value()
                        transport: IOError {}
                        dx.failWritesWith(transport)
                        Assertions.require(failureOf(secure.write(ascii("lost"))) === transport)
                        dx.failWritesWith(null)
                        // The recorded Error is re-observed; nothing new is sealed or written.
                        writes: dx.writeCount()
                        Assertions.require(failureOf(secure.write(ascii("after"))) === transport)
                        Assertions.require(dx.writeCount() == writes)
                        // Close emits no close_notify but still closes the owned transport once.
                        Assertions.require(secure.close().value() === secure)
                        Assertions.require(dx.writeCount() == writes)
                        Assertions.require(dx.closeCount() == 1)
                        Assertions.require(dx.serverText() == "")
                        true
                        """);
        run.assertCompleted();
        assertFalse(run.duplex.peer.server.isInboundDone());
    }

    @Test
    void aCapacityRejectionLeavesTheStreamUsable() {
        Run run =
                run(
                        server(TLS13),
                        """
                        options: {
                            maxQueuedWriteBytes: 3
                        }
                        secure: tls.Client.handshake(dx, "service.protos.test", trust(), options).value()
                        writes: dx.writeCount()
                        Assertions.require(failureOf(secure.write(ascii("toolong"))).parent() === IOCapacityExhausted)
                        Assertions.require(dx.writeCount() == writes)
                        secure.write(ascii("ok")).value()
                        Assertions.require(dx.serverText() == "ok")
                        secure.close().value()
                        true
                        """);
        run.assertCompleted();
        assertTrue(run.duplex.peer.server.isInboundDone());
    }

    @Test
    void aSecureDuplexServesOnlyItsOwnActor() {
        Duplex duplex = server(TLS13);
        Run owner =
                run(
                        duplex,
                        """
                        keep(connect())
                        true
                        """);
        owner.assertCompleted();
        // Every newModuleActivation() is a fresh Actor execution domain.
        ProtosActivation other = prelude.newModuleActivation();
        other.context().createLocalSlot("secure", kept);
        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        "secure.write(Encoding.Latin1.encode(\"x\"))\ntrue\n", other);
        assertFalse(
                outcome.state() == ProtosExecutionOutcome.State.COMPLETED
                        && outcome.value() == ProtosBooleanValue.TRUE,
                "a foreign Actor drove the TLS engine");
        assertEquals("", duplex.serverReceived.toString(StandardCharsets.ISO_8859_1));
    }

    // ------------------------------------------------------------ closure and custody

    @Test
    void owningCloseClosesTheTransportExactlyOnceAndIsIdempotent() {
        Run run =
                run(
                        server(TLS13),
                        """
                        secure: tls.Client.handshake.owning(dx, "service.protos.test", trust(), {}).value()
                        Assertions.require(dx.closeCount() == 0)
                        first: secure.close()
                        second: secure.close()
                        Assertions.require(first !== second)
                        Assertions.require(first.value() === secure)
                        Assertions.require(second.value() === secure)
                        Assertions.require(secure.close().value() === secure)
                        Assertions.require(dx.closeCount() == 1)
                        Assertions.require(failureOf(secure.read(1)).parent() === IOLifecycleError)
                        Assertions.require(failureOf(secure.write(ascii("x"))).parent() === IOLifecycleError)
                        true
                        """);
        run.assertCompleted();
        assertEquals(1, run.duplex.closes);
        assertTrue(run.duplex.peer.server.isInboundDone());
    }

    @Test
    void secureCloseFollowsWrapperClosePrecedence() {
        // IO_CORE.md §9: a close_notify emission failure prevails over the owned close failure.
        Run emission =
                run(
                        server(TLS13),
                        """
                        secure: tls.Client.handshake.owning(dx, "service.protos.test", trust(), {}).value()
                        emitError: IOError {}
                        dx.failWritesWith(emitError)
                        dx.failCloseWith(IOError {})
                        Assertions.require(failureOf(secure.close()) === emitError)
                        Assertions.require(dx.closeCount() == 1)
                        true
                        """);
        emission.assertCompleted();

        // After a clean close_notify, the owned close failure is the close failure, and later
        // closes re-observe that exact Error.
        Run release =
                run(
                        server(TLS13),
                        """
                        secure: tls.Client.handshake.owning(dx, "service.protos.test", trust(), {}).value()
                        closeError: IOError {}
                        dx.failCloseWith(closeError)
                        Assertions.require(failureOf(secure.close()) === closeError)
                        Assertions.require(failureOf(secure.close()) === closeError)
                        Assertions.require(dx.closeCount() == 1)
                        true
                        """);
        release.assertCompleted();
        assertTrue(release.duplex.peer.server.isInboundDone());
    }

    // ------------------------------------------------------------ HTTP composition

    @Test
    void theUnchangedHttpClientRunsOverABorrowedSecureDuplex() {
        Run run =
                run(
                        server(TLS13),
                        """
                        secure: connect()
                        dx.serverSend(ascii("HTTP/1.1 200 OK\\r\\nContent-Length: 2\\r\\n\\r\\nhi"))
                        request: {
                            method: "GET"
                            target: ascii("/")
                            headers: [["Host", ascii("service.protos.test")]]
                            body: null
                        }
                        status: http.Client.exchangeOne(secure, request, (response) => {
                            Assertions.require(readAll(response.body) == "hi")
                            response.status
                        }, null).value()
                        Assertions.require(status == 200)
                        Assertions.require(dx.serverTextStartsWith("GET / HTTP/1.1\\r\\n"))
                        secure.close().value()
                        true
                        """);
        run.assertCompleted();
        assertEquals(0, run.duplex.closes);
    }

    @Test
    void owningHttpOverOwningTlsClosesTheTransportOnce() {
        Run run =
                run(
                        server(TLS13),
                        """
                        secure: tls.Client.handshake.owning(dx, "service.protos.test", trust(), {}).value()
                        dx.serverSend(ascii("HTTP/1.1 204 No Content\\r\\n\\r\\n"))
                        request: {
                            method: "GET"
                            target: ascii("/")
                            headers: [["Host", ascii("service.protos.test")]]
                            body: null
                        }
                        status: http.Client.exchangeOne.owning(secure, request, (response) => {
                            response.status
                        }, null).value()
                        Assertions.require(status == 204)
                        // HTTP closed the secure duplex, which closed the transport.
                        Assertions.require(dx.closeCount() == 1)
                        Assertions.require(failureOf(secure.read(1)).parent() === IOLifecycleError)
                        true
                        """);
        run.assertCompleted();
        assertEquals(1, run.duplex.closes);
    }

    @Test
    void noSecondJavaTlsEngineOrSessionParticipates() {
        for (String name :
                new String[] {"ProtosTlsClientSession", "ProtosTlsSecureDuplex", "ProtosTlsIdentity"}) {
            assertThrows(
                    ClassNotFoundException.class,
                    () -> Class.forName("com.guillermomolina.protos.execution." + name));
        }
    }

    // ------------------------------------------------------------ harness

    /** Runs one handshake with serverIdentity and asserts success or identityMismatch. */
    private Duplex assertIdentity(String leaf, String serverIdentity, boolean matches) {
        Run run =
                run(
                        serverWith(leaf, null, TLS13),
                        "future: tls.Client.handshake(dx, "
                                + serverIdentity
                                + ", trust(), {})\n"
                                + (matches
                                        ? "future.value()\n"
                                        : "Assertions.require(kindOf(future) == \"identityMismatch\")\n")
                                + "true\n");
        run.assertCompleted();
        return run.duplex;
    }

    /** Runs one handshake against the IDNA leaf and asserts success or identityMismatch. */
    private void assertIdnaIdentity(String serverIdentity, boolean matches) {
        Run run =
                run(
                        idnaServer("idna-leaf"),
                        "future: tls.Client.handshake(dx, "
                                + serverIdentity
                                + ", idnaTrust(), {})\n"
                                + (matches
                                        ? "future.value()\n"
                                        : "Assertions.require(kindOf(future) == \"identityMismatch\")\n")
                                + "true\n");
        run.assertCompleted();
    }

    /** A TLS 1.3 server presenting only {@code leaf}, issued directly by {@code idna-root-ca.pem}. */
    private static Duplex idnaServer(String leaf) {
        return new Duplex(ProtosTlsTestPeer.serverWithIssuers(leaf, List.of(), null, TLS13));
    }

    private static Duplex server(String... protocols) {
        return serverWith("service-leaf", null, protocols);
    }

    private static Duplex server(
            BiFunction<SSLEngine, List<String>, String> alpnSelector, String... protocols) {
        return serverWith("service-leaf", alpnSelector, protocols);
    }

    private static Duplex serverWith(
            String leaf,
            BiFunction<SSLEngine, List<String>, String> alpnSelector,
            String... protocols) {
        return new Duplex(ProtosTlsTestPeer.server(leaf, alpnSelector, protocols));
    }

    private static Run run(Duplex duplex, String body) {
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue context = activation.context();
        context.createLocalSlot("dx", duplex.object);
        context.createLocalSlot("rootCa", bytes(ProtosTlsTestPeer.der("root-ca.pem")));
        context.createLocalSlot("untrustedCa", bytes(ProtosTlsTestPeer.der("untrusted-ca.pem")));
        context.createLocalSlot("idnaRootCa", bytes(ProtosTlsTestPeer.der("idna-root-ca.pem")));
        // The private backend facility, for direct Identity checks with real Unicode facts.
        context.createLocalSlot("unicode", ProtosTlsBackendFacility.createFacility());
        context.createLocalSlot(
                "keep",
                fn(
                        (a, x) -> {
                            kept = x.get(0);
                            return ProtosNullValue.INSTANCE;
                        }));
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

    /** The standard prototype and own {@code kind} of a failure, for readable assertions. */
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
        return result.length() == 0 ? object.toString() : result.toString().trim();
    }

    /**
     * In-memory transport to a JSSE server. A write hands its ciphertext to the server at once; a
     * read answers the server's pending ciphertext (at most {@link #fragment} octets), null after
     * {@code endTransport()}, or a pending Future resolved when the server next produces output.
     */
    private static final class Duplex {
        final ProtosObjectValue object = new ProtosObjectValue(ProtosObjectValue.rootObject());
        final ProtosTlsTestPeer peer;
        final ByteArrayOutputStream serverReceived = new ByteArrayOutputStream();
        final List<ProtosFutureValue> heldWrites = new ArrayList<>();
        int fragment = Integer.MAX_VALUE;
        boolean ended;
        boolean holdWrites;
        ProtosObjectValue readFailure;
        ProtosObjectValue writeFailure;
        ProtosObjectValue closeFailure;
        int writes;
        int reads;
        ProtosFutureValue pendingRead;
        int pendingMax;
        int closes;

        Duplex(ProtosTlsTestPeer peer) {
            this.peer = peer;
            slot("read", (a, x) -> read(a, ProtosNumericValueSupport.exactInt(x.get(0))));
            slot(
                    "write",
                    (a, x) -> {
                        writes++;
                        if (writeFailure != null) {
                            // Nothing reaches the server; TLS cannot know that.
                            ProtosFutureValue failed = future(a);
                            failed.fail(writeFailure);
                            return failed;
                        }
                        peer.receive(((ProtosBytesValue) x.get(0)).octetSnapshot());
                        serverReceived.writeBytes(peer.takeReceived());
                        deliver(a);
                        ProtosFutureValue result = future(a);
                        if (holdWrites) {
                            heldWrites.add(result);
                        } else {
                            result.resolve(object, a);
                        }
                        return result;
                    });
            slot(
                    "close",
                    (a, x) -> {
                        closes++;
                        if (closeFailure != null) {
                            ProtosFutureValue failed = future(a);
                            failed.fail(closeFailure);
                            return failed;
                        }
                        return resolved(a, ProtosNullValue.INSTANCE);
                    });
            slot(
                    "failCloseWith",
                    (a, x) -> {
                        closeFailure = (ProtosObjectValue) x.get(0);
                        return ProtosNullValue.INSTANCE;
                    });
            slot(
                    "closeCount",
                    (a, x) -> integer(closes));
            slot(
                    "serverText",
                    (a, x) -> new ProtosStringValue(serverReceived.toString(StandardCharsets.ISO_8859_1)));
            slot(
                    "serverTextStartsWith",
                    (a, x) ->
                            ProtosBooleanValue.of(
                                    serverReceived
                                            .toString(StandardCharsets.ISO_8859_1)
                                            .startsWith(((ProtosStringValue) x.get(0)).value())));
            slot(
                    "serverSend",
                    (a, x) -> {
                        peer.write(((ProtosBytesValue) x.get(0)).octetSnapshot());
                        // Seal the data now: a later serverClose must not overtake it.
                        peer.send(0);
                        deliver(a);
                        return ProtosNullValue.INSTANCE;
                    });
            slot(
                    "serverClose",
                    (a, x) -> {
                        peer.closeOutbound();
                        deliver(a);
                        return ProtosNullValue.INSTANCE;
                    });
            slot(
                    "endTransport",
                    (a, x) -> {
                        ended = true;
                        deliver(a);
                        return ProtosNullValue.INSTANCE;
                    });
            slot(
                    "failReadsWith",
                    (a, x) -> {
                        readFailure = (ProtosObjectValue) x.get(0);
                        return ProtosNullValue.INSTANCE;
                    });
            slot(
                    "failWritesWith",
                    (a, x) -> {
                        writeFailure =
                                x.get(0) == ProtosNullValue.INSTANCE
                                        ? null
                                        : (ProtosObjectValue) x.get(0);
                        return ProtosNullValue.INSTANCE;
                    });
            slot("writeCount", (a, x) -> integer(writes));
            slot(
                    "holdWrites",
                    (a, x) -> {
                        holdWrites = true;
                        return ProtosNullValue.INSTANCE;
                    });
            slot(
                    "releaseWrites",
                    (a, x) -> {
                        holdWrites = false;
                        for (ProtosFutureValue held : heldWrites) {
                            held.resolve(object, a);
                        }
                        heldWrites.clear();
                        return ProtosNullValue.INSTANCE;
                    });
        }

        private void slot(String name, ProtosNativeClosureBody body) {
            object.createLocalSlot(name, fn(body));
        }

        private Object read(ProtosActivation activation, int maxBytes) {
            reads++;
            if (readFailure != null) {
                ProtosFutureValue failed = future(activation);
                failed.fail(readFailure);
                return failed;
            }
            byte[] available = peer.send(Math.min(maxBytes, fragment));
            if (available.length > 0) {
                return resolved(activation, bytes(available));
            }
            if (ended) {
                return resolved(activation, ProtosNullValue.INSTANCE);
            }
            pendingRead = future(activation);
            pendingMax = maxBytes;
            return pendingRead;
        }

        /** Completes a waiting read once the server has produced output or input ended. */
        private void deliver(ProtosActivation activation) {
            if (pendingRead == null) {
                return;
            }
            byte[] available = peer.send(Math.min(pendingMax, fragment));
            if (available.length == 0 && !ended) {
                return;
            }
            ProtosFutureValue waiting = pendingRead;
            pendingRead = null;
            waiting.resolve(
                    available.length > 0 ? bytes(available) : ProtosNullValue.INSTANCE,
                    activation);
        }

        List<SNIServerName> sni() {
            return ((ExtendedSSLSession) peer.server.getSession()).getRequestedServerNames();
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
}

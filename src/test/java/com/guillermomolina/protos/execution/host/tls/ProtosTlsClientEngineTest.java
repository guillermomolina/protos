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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.TrustAnchor;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import org.junit.jupiter.api.Test;

/**
 * I130-A: the private JSSE record engine driven in memory against a JSSE server peer: engine
 * progress, the pre-negotiation security profile, chain verification, SNI and ALPN offers,
 * conservative failure facts and authenticated closure. PKIX runs at a fixed instant inside the
 * fixtures' validity window, so the result never depends on the machine clock.
 */
final class ProtosTlsClientEngineTest {
    private static final String TLS13 = "TLSv1.3";
    private static final String TLS12 = "TLSv1.2";
    private static final Date VALIDATION_DATE = Date.from(Instant.parse("2030-01-01T00:00:00Z"));

    @Test
    void defaultProfilePrefersTls13SendsSniAndExchangesData() {
        ProtosTlsTestPeer peer = ProtosTlsTestPeer.server("service-leaf", TLS13, TLS12);
        ProtosTlsClientEngine client = client(ProtosTlsTestPeer.SERVICE, false, true);
        peer.exchange(client);
        assertTrue(client.established());
        assertEquals(TLS13, client.negotiatedProtocol());
        assertEquals("http/1.1", peer.server.getApplicationProtocol());
        ExtendedSSLSession session = (ExtendedSSLSession) peer.server.getSession();
        assertEquals(
                List.of(new SNIHostName(ProtosTlsTestPeer.SERVICE)),
                session.getRequestedServerNames());

        byte[] ping = "ping".getBytes(StandardCharsets.US_ASCII);
        assertEquals(4, client.wrapPlaintext(ping, 0, ping.length));
        peer.write("pong".getBytes(StandardCharsets.US_ASCII));
        peer.exchange(client);
        assertArrayEquals(ping, peer.takeReceived());
        assertEquals(4, client.plaintextAvailable());
        assertArrayEquals("po".getBytes(StandardCharsets.US_ASCII), client.takePlaintext(2));
        assertArrayEquals("ng".getBytes(StandardCharsets.US_ASCII), client.takePlaintext(10));
        assertEquals(0, client.plaintextAvailable());
    }

    @Test
    void peerIdentitiesAreExposedOnlyAfterVerification() {
        ProtosTlsTestPeer peer = ProtosTlsTestPeer.server("service-leaf", TLS13);
        ProtosTlsClientEngine client = client(ProtosTlsTestPeer.SERVICE, false, true);
        assertThrows(IllegalStateException.class, client::peerDnsNames);
        peer.exchange(client);
        assertEquals(
                List.of("service.protos.test", "*.wild.protos.test"), client.peerDnsNames());
        assertEquals(2, client.peerIpAddresses().size());
    }

    @Test
    void secureTls12IsPermittedByDefault() {
        ProtosTlsTestPeer peer = ProtosTlsTestPeer.server("service-leaf", TLS12);
        ProtosTlsClientEngine client = client(ProtosTlsTestPeer.SERVICE, false, true);
        peer.exchange(client);
        assertTrue(client.established());
        assertEquals(TLS12, client.negotiatedProtocol());
        String suite = peer.server.getSession().getCipherSuite();
        assertTrue(suite.startsWith("TLS_ECDHE_ECDSA_WITH_AES_"), suite);
    }

    @Test
    void tls13OnlyProfileIsEnforcedBeforeNegotiation() {
        // The engine never offers TLS 1.2, so the peer cannot negotiate it; without conclusive
        // version evidence the fact is the general handshake failure.
        ProtosTlsTestPeer peer = ProtosTlsTestPeer.server("service-leaf", TLS12);
        ProtosTlsClientEngine client = client(ProtosTlsTestPeer.SERVICE, true, true);
        assertFact(ProtosTlsFailure.Fact.HANDSHAKE_FAILED, peer, client);
        assertFalse(client.established());
    }

    @Test
    void chainOutsideTheSelectedAnchorsIsUntrusted() {
        ProtosTlsTestPeer peer = ProtosTlsTestPeer.server("service-leaf", TLS13);
        ProtosTlsClientEngine client =
                ProtosTlsClientEngine.start(
                        anchors("untrusted-ca.pem"),
                        ProtosTlsTestPeer.SERVICE,
                        false,
                        true,
                        VALIDATION_DATE);
        assertFact(ProtosTlsFailure.Fact.UNTRUSTED_CHAIN, peer, client);
        assertFalse(client.established());
    }

    @Test
    void chainIsCheckedAtTheValidationInstant() {
        ProtosTlsTestPeer peer = ProtosTlsTestPeer.server("service-leaf", TLS13);
        ProtosTlsClientEngine client =
                ProtosTlsClientEngine.start(
                        anchors("root-ca.pem"),
                        ProtosTlsTestPeer.SERVICE,
                        false,
                        true,
                        Date.from(Instant.parse("2200-01-01T00:00:00Z")));
        assertFact(ProtosTlsFailure.Fact.UNTRUSTED_CHAIN, peer, client);
    }

    @Test
    void noSniIsSentWithoutADnsReference() {
        ProtosTlsTestPeer peer = ProtosTlsTestPeer.server("service-leaf", TLS13);
        ProtosTlsClientEngine client = client(null, false, true);
        peer.exchange(client);
        assertTrue(client.established());
        ExtendedSSLSession session = (ExtendedSSLSession) peer.server.getSession();
        assertTrue(session.getRequestedServerNames().isEmpty());
    }

    @Test
    void peerRejectingTheAlpnOfferIsAGeneralHandshakeFailure() {
        // The peer aborts; no protocol was negotiated, so there is no reliable ALPN evidence.
        ProtosTlsTestPeer peer =
                ProtosTlsTestPeer.server("service-leaf", (engine, offered) -> "h2", TLS13);
        ProtosTlsClientEngine client = client(ProtosTlsTestPeer.SERVICE, false, true);
        assertFact(ProtosTlsFailure.Fact.HANDSHAKE_FAILED, peer, client);
    }

    @Test
    void noAlpnSelectionIsAccepted() {
        ProtosTlsTestPeer peer =
                ProtosTlsTestPeer.server("service-leaf", (engine, offered) -> "", TLS13);
        ProtosTlsClientEngine client = client(ProtosTlsTestPeer.SERVICE, false, true);
        peer.exchange(client);
        assertTrue(client.established());
    }

    @Test
    void noAlpnOfferIsSentWhenDisabled() {
        ProtosTlsTestPeer peer = ProtosTlsTestPeer.server("service-leaf", TLS13);
        ProtosTlsClientEngine client = client(ProtosTlsTestPeer.SERVICE, false, false);
        peer.exchange(client);
        assertTrue(client.established());
        assertEquals("", peer.server.getApplicationProtocol());
    }

    @Test
    void peerCloseNotifyIsAuthenticatedEndOfInput() {
        ProtosTlsTestPeer peer = ProtosTlsTestPeer.server("service-leaf", TLS13);
        ProtosTlsClientEngine client = client(ProtosTlsTestPeer.SERVICE, false, true);
        peer.exchange(client);
        peer.write(new byte[] {7});
        peer.exchange(client);
        peer.closeOutbound();
        peer.exchange(client);
        client.transportEndOfInput();
        client.advance();
        assertTrue(client.peerClosed());
        assertArrayEquals(new byte[] {7}, client.takePlaintext(10));
    }

    @Test
    void physicalEndOfInputWithoutCloseNotifyIsTruncated() {
        ProtosTlsTestPeer peer = ProtosTlsTestPeer.server("service-leaf", TLS13);
        ProtosTlsClientEngine client = client(ProtosTlsTestPeer.SERVICE, false, true);
        peer.exchange(client);
        peer.write(new byte[] {9});
        peer.exchange(client);
        client.transportEndOfInput();
        ProtosTlsFailure failure = assertThrows(ProtosTlsFailure.class, client::advance);
        assertEquals(ProtosTlsFailure.Fact.TRUNCATED, failure.fact());
        assertFalse(client.peerClosed());
        // Plaintext unwrapped before the failure stays takeable; it is never dropped.
        assertArrayEquals(new byte[] {9}, client.takePlaintext(10));
        assertSame(failure, assertThrows(ProtosTlsFailure.class, client::advance));
    }

    @Test
    void endOfInputDuringTheHandshakeIsTruncated() {
        ProtosTlsTestPeer peer = ProtosTlsTestPeer.server("service-leaf", TLS13);
        peer.goSilent();
        ProtosTlsClientEngine client = client(ProtosTlsTestPeer.SERVICE, false, true);
        peer.exchange(client);
        client.transportEndOfInput();
        assertEquals(
                ProtosTlsFailure.Fact.TRUNCATED,
                assertThrows(ProtosTlsFailure.class, client::advance).fact());
    }

    @Test
    void closeOutboundProducesCloseNotifyWithoutWaiting() {
        ProtosTlsTestPeer peer = ProtosTlsTestPeer.server("service-leaf", TLS13);
        ProtosTlsClientEngine client = client(ProtosTlsTestPeer.SERVICE, false, true);
        peer.exchange(client);
        assertEquals(3, client.wrapPlaintext(new byte[] {1, 2, 3}, 0, 3));
        client.closeOutbound();
        peer.exchange(client);
        assertTrue(client.closeNotifyProduced());
        assertArrayEquals(new byte[] {1, 2, 3}, peer.takeReceived());
        assertTrue(peer.server.isInboundDone());
        assertEquals(0, client.wrapPlaintext(new byte[] {4}, 0, 1));
    }

    @Test
    void wrapConsumesAtMostOneRecordAndNothingBeforeEstablishment() {
        ProtosTlsTestPeer peer = ProtosTlsTestPeer.server("service-leaf", TLS13);
        ProtosTlsClientEngine client = client(ProtosTlsTestPeer.SERVICE, false, true);
        byte[] payload = new byte[40_000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        assertEquals(0, client.wrapPlaintext(payload, 0, payload.length));
        peer.exchange(client);
        ByteArrayOutputStream delivered = new ByteArrayOutputStream();
        int offset = 0;
        for (int round = 0; round < 20 && offset < payload.length; round++) {
            int consumed = client.wrapPlaintext(payload, offset, payload.length - offset);
            assertTrue(consumed <= ProtosTlsClientEngine.MAX_PLAINTEXT_RECORD);
            offset += consumed;
            peer.exchange(client);
            delivered.writeBytes(peer.takeReceived());
        }
        assertArrayEquals(payload, delivered.toByteArray());
    }

    @Test
    void failureIsSticky() {
        ProtosTlsTestPeer peer = ProtosTlsTestPeer.server("service-leaf", TLS13);
        ProtosTlsClientEngine client =
                ProtosTlsClientEngine.start(
                        anchors("untrusted-ca.pem"), null, false, true, VALIDATION_DATE);
        ProtosTlsFailure first = assertThrows(ProtosTlsFailure.class, () -> peer.exchange(client));
        assertSame(first, assertThrows(ProtosTlsFailure.class, client::advance));
        assertSame(
                first,
                assertThrows(
                        ProtosTlsFailure.class, () -> client.wrapPlaintext(new byte[1], 0, 1)));
    }

    private static void assertFact(
            ProtosTlsFailure.Fact expected, ProtosTlsTestPeer peer, ProtosTlsClientEngine client) {
        ProtosTlsFailure failure =
                assertThrows(ProtosTlsFailure.class, () -> peer.exchange(client));
        assertEquals(expected, failure.fact(), failure::toString);
    }

    private static ProtosTlsClientEngine client(
            String sniHostName, boolean tls13Only, boolean offerHttp11) {
        return ProtosTlsClientEngine.start(
                anchors("root-ca.pem"), sniHostName, tls13Only, offerHttp11, VALIDATION_DATE);
    }

    private static Set<TrustAnchor> anchors(String certificate) {
        return ProtosTlsTrust.anchors(List.of(ProtosTlsTestPeer.certificate(certificate)));
    }
}

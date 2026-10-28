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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * I130-A: host certificate-material primitives. Trust policy (bounds, snapshots, selection, no
 * fallback) and identity matching are Protos and are tested with the I130-B frontend.
 */
final class ProtosTlsTrustTest {
    private static final byte[] ROOT = ProtosTlsTestPeer.der("root-ca.pem");

    @Test
    void exactDerDecodesOneCertificate() {
        assertEquals(
                ProtosTlsTestPeer.certificate("root-ca.pem"),
                ProtosTlsTrust.exactDer(ROOT).orElseThrow());
    }

    @Test
    void exactDerRejectsPemTrailingOctetsAndGarbage() {
        byte[] pem =
                ("-----BEGIN CERTIFICATE-----\n"
                                + Base64.getMimeEncoder().encodeToString(ROOT)
                                + "\n-----END CERTIFICATE-----\n")
                        .getBytes(StandardCharsets.US_ASCII);
        assertTrue(ProtosTlsTrust.exactDer(pem).isEmpty());
        assertTrue(ProtosTlsTrust.exactDer(Arrays.copyOf(ROOT, ROOT.length + 1)).isEmpty());
        assertTrue(ProtosTlsTrust.exactDer(new byte[] {1, 2, 3}).isEmpty());
        assertTrue(ProtosTlsTrust.exactDer(new byte[0]).isEmpty());
    }

    @Test
    void anchorsWrapExactlyTheGivenCertificates() {
        X509Certificate root = ProtosTlsTestPeer.certificate("root-ca.pem");
        X509Certificate other = ProtosTlsTestPeer.certificate("untrusted-ca.pem");
        assertEquals(2, ProtosTlsTrust.anchors(List.of(root, other)).size());
    }

    @Test
    void javaCacertsReadsOnlyTheRuntimeStore() {
        List<X509Certificate> certificates = ProtosTlsTrust.javaCacerts();
        assertFalse(certificates.isEmpty());
        assertFalse(certificates.contains(ProtosTlsTestPeer.certificate("root-ca.pem")));
    }

    @Test
    void subjectAltNamesAreDecodedRawAndExact() {
        X509Certificate service = ProtosTlsTestPeer.certificate("service-leaf.pem");
        assertEquals(
                List.of("service.protos.test", "*.wild.protos.test"),
                ProtosTlsTrust.dnsNames(service));
        List<byte[]> addresses = ProtosTlsTrust.ipAddresses(service);
        assertEquals(2, addresses.size());
        assertArrayEquals(new byte[] {127, 0, 0, 1}, addresses.get(0));
        byte[] loopback6 = new byte[16];
        loopback6[15] = 1;
        assertArrayEquals(loopback6, addresses.get(1));
    }

    @Test
    void commonNameIsNotReportedAsAnIdentity() {
        X509Certificate cnOnly = ProtosTlsTestPeer.certificate("cn-only-leaf.pem");
        assertTrue(ProtosTlsTrust.dnsNames(cnOnly).isEmpty());
        assertTrue(ProtosTlsTrust.ipAddresses(cnOnly).isEmpty());
    }
}

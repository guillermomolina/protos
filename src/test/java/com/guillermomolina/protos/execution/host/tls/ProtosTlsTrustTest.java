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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * I130-A: host certificate-material primitives. Trust policy (bounds, snapshots, selection, no
 * fallback) and identity matching are Protos and are tested with the I130-B frontend.
 */
final class ProtosTlsTrustTest {
    private static final byte[] ROOT = ProtosTlsTestPeer.der("root-ca.pem");
    private static final String JAVA_HOME = "java.home";
    private static final String TRUST_STORE = "javax.net.ssl.trustStore";

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

    /**
     * I130-C2: the controlled java.home makes the result independent of the host JDK, and Native
     * Image, which does not define java.home itself, can read an explicitly configured one.
     */
    @Test
    void javaCacertsReadsOnlyTheRuntimeStore(@TempDir Path javaHome) throws Exception {
        X509Certificate root = ProtosTlsTestPeer.certificate("root-ca.pem");
        writeCacerts(cacertsOf(javaHome), root);
        String originalJavaHome = System.getProperty(JAVA_HOME);
        try {
            System.setProperty(JAVA_HOME, javaHome.toString());
            assertEquals(List.of(root), ProtosTlsTrust.javaCacerts());
        } finally {
            restoreProperty(JAVA_HOME, originalJavaHome);
        }
    }

    /**
     * I130-C2: the explicit Java cacerts source is java.home/lib/security/cacerts, never the
     * ambient JSSE default trust store. Says nothing about which certificates the JSSE default
     * trust store of a Native Image captured at build time.
     */
    @Test
    void javaCacertsIgnoresAmbientTrustStoreProperty(@TempDir Path javaHome) throws Exception {
        X509Certificate root = ProtosTlsTestPeer.certificate("root-ca.pem");
        writeCacerts(cacertsOf(javaHome), root);
        String originalJavaHome = System.getProperty(JAVA_HOME);
        String originalTrustStore = System.getProperty(TRUST_STORE);
        try {
            System.setProperty(JAVA_HOME, javaHome.toString());
            List<X509Certificate> reference = ProtosTlsTrust.javaCacerts();
            assertEquals(List.of(root), reference);
            System.setProperty(TRUST_STORE, "/nonexistent/protos-i130-c2-default-truststore");
            assertEquals(reference, ProtosTlsTrust.javaCacerts());
        } finally {
            restoreProperty(TRUST_STORE, originalTrustStore);
            restoreProperty(JAVA_HOME, originalJavaHome);
        }
    }

    /** I130-C2: an undefined java.home is TRUST_MATERIAL_UNAVAILABLE, with no fallback source. */
    @Test
    void javaCacertsWithoutJavaHomeIsUnavailable() {
        String originalJavaHome = System.getProperty(JAVA_HOME);
        try {
            System.clearProperty(JAVA_HOME);
            ProtosTlsFailure failure =
                    assertThrows(ProtosTlsFailure.class, ProtosTlsTrust::javaCacerts);
            assertEquals(ProtosTlsFailure.Fact.TRUST_MATERIAL_UNAVAILABLE, failure.fact());
        } finally {
            restoreProperty(JAVA_HOME, originalJavaHome);
        }
    }

    /**
     * I130-C2: the explicit cacerts file is read afresh at each call, with no cache in {@link
     * ProtosTlsTrust}, and its absence is TRUST_MATERIAL_UNAVAILABLE. Independent of the host's
     * cacerts; says nothing about whether a Native distribution's java.home holds a usable file.
     */
    @Test
    void javaCacertsReadsChangedFileAtRuntime(@TempDir Path directory) throws Exception {
        X509Certificate root = ProtosTlsTestPeer.certificate("root-ca.pem");
        X509Certificate other = ProtosTlsTestPeer.certificate("untrusted-ca.pem");
        Path file = directory.resolve("cacerts");

        writeCacerts(file, root);
        assertEquals(List.of(root), ProtosTlsTrust.readCacerts(file));

        writeCacerts(file, other);
        List<X509Certificate> changed = ProtosTlsTrust.readCacerts(file);
        assertEquals(List.of(other), changed);
        assertFalse(changed.contains(root));

        Files.delete(file);
        ProtosTlsFailure failure =
                assertThrows(ProtosTlsFailure.class, () -> ProtosTlsTrust.readCacerts(file));
        assertEquals(ProtosTlsFailure.Fact.TRUST_MATERIAL_UNAVAILABLE, failure.fact());
    }

    /** A password-less store of the type readCacerts loads, holding exactly {@code certificate}. */
    private static void writeCacerts(Path file, X509Certificate certificate) throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        store.setCertificateEntry("anchor", certificate);
        try (OutputStream output = Files.newOutputStream(file)) {
            store.store(output, null);
        }
    }

    private static Path cacertsOf(Path javaHome) throws Exception {
        Path file = javaHome.resolve("lib").resolve("security").resolve("cacerts");
        Files.createDirectories(file.getParent());
        return file;
    }

    private static void restoreProperty(String key, String original) {
        if (original == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, original);
        }
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

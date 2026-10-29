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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Host certificate-material primitives of the {@code std:tls/Client} backend (D220–S12).
 *
 * <p>This class owns no trust policy. Descriptor construction, the count and size bounds,
 * snapshotting, explicit source selection, the fail-closed {@code Trust.system()} and the absence
 * of any fallback are Protos policy (I130-B {@code lib/tls/internal/TrustSource.protos}). Java
 * provides only what Protos cannot: exact DER X.509 decoding, reading the explicitly selected Java
 * {@code cacerts} file, and decoding subjectAltName entries for the Protos identity matcher.
 */
public final class ProtosTlsTrust {
    private static final String SUBJECT_ALT_NAME_OID = "2.5.29.17";
    private static final int DNS_NAME_TAG = 0x82;
    private static final int IP_ADDRESS_TAG = 0x87;

    private ProtosTlsTrust() {}

    /**
     * Decodes exactly one DER X.509 certificate; empty for PEM, trailing octets, or any input the
     * provider would re-encode differently.
     */
    public static Optional<X509Certificate> exactDer(byte[] der) {
        try {
            Certificate certificate =
                    x509Factory().generateCertificate(new ByteArrayInputStream(der));
            if (certificate instanceof X509Certificate x509
                    && Arrays.equals(x509.getEncoded(), der)) {
                return Optional.of(x509);
            }
        } catch (CertificateException malformed) {
            // Reported as empty.
        }
        return Optional.empty();
    }

    /** PKIX anchors for already decoded certificates, in no particular order. */
    public static Set<TrustAnchor> anchors(List<X509Certificate> certificates) {
        Set<TrustAnchor> result = new HashSet<>();
        for (X509Certificate certificate : certificates) {
            result.add(new TrustAnchor(certificate, null));
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Reads the certificate entries of exactly {@code ${java.home}/lib/security/cacerts}, at each
     * call. {@code javax.net.ssl.trustStore}, {@code jssecacerts} and {@code
     * SSLContext.getDefault()} are deliberately ignored: they are ambient host policy. Under Native
     * Image the file may be absent or its content fixed at image-build time. An empty answer is
     * reported, not interpreted; the Protos caller fails closed.
     *
     * @throws ProtosTlsFailure {@link ProtosTlsFailure.Fact#TRUST_MATERIAL_UNAVAILABLE}
     */
    public static List<X509Certificate> javaCacerts() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null) {
            throw new ProtosTlsFailure(
                    ProtosTlsFailure.Fact.TRUST_MATERIAL_UNAVAILABLE, "java.home is not defined");
        }
        return readCacerts(Path.of(javaHome, "lib", "security", "cacerts"));
    }

    /**
     * Reads the certificate entries of {@code file} afresh, without caching. Internal separation of
     * {@link #javaCacerts()}; Protos cannot select the path.
     *
     * @throws ProtosTlsFailure {@link ProtosTlsFailure.Fact#TRUST_MATERIAL_UNAVAILABLE}
     */
    static List<X509Certificate> readCacerts(Path file) {
        try (var input = Files.newInputStream(file)) {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(input, null);
            List<X509Certificate> result = new ArrayList<>();
            for (String alias : Collections.list(store.aliases())) {
                if (store.isCertificateEntry(alias)
                        && store.getCertificate(alias) instanceof X509Certificate certificate) {
                    result.add(certificate);
                }
            }
            return List.copyOf(result);
        } catch (Exception unavailable) {
            throw new ProtosTlsFailure(
                    ProtosTlsFailure.Fact.TRUST_MATERIAL_UNAVAILABLE,
                    "Java cacerts cannot be read",
                    unavailable);
        }
    }

    /**
     * The raw subjectAltName dNSName entries, decoded octet-per-character (ISO-8859-1) so that the
     * Protos matcher sees and rejects any non-ASCII octet; no normalization is applied here.
     */
    public static List<String> dnsNames(X509Certificate certificate) {
        List<String> result = new ArrayList<>();
        for (byte[] entry : generalNames(certificate, DNS_NAME_TAG)) {
            result.add(new String(entry, StandardCharsets.ISO_8859_1));
        }
        return result;
    }

    /** The exact octets of every subjectAltName iPAddress entry. */
    public static List<byte[]> ipAddresses(X509Certificate certificate) {
        return generalNames(certificate, IP_ADDRESS_TAG);
    }

    /**
     * Decodes the DER {@code OCTET STRING { GeneralNames }} extension value. The host API {@link
     * X509Certificate#getSubjectAlternativeNames()} is not used because it renders iPAddress
     * entries as text, losing the exact octets. A malformed extension answers no entries.
     */
    private static List<byte[]> generalNames(X509Certificate certificate, int wanted) {
        byte[] extension = certificate.getExtensionValue(SUBJECT_ALT_NAME_OID);
        List<byte[]> result = new ArrayList<>();
        if (extension == null) {
            return result;
        }
        try {
            Der sequence = new Der(extension, 0, extension.length).next(0x04).next(0x30);
            while (sequence.hasMore()) {
                int tag = sequence.peekTag();
                Der entry = sequence.next(tag);
                if (tag == wanted) {
                    result.add(entry.contents());
                }
            }
            return result;
        } catch (IllegalArgumentException malformed) {
            return new ArrayList<>();
        }
    }

    static CertificateFactory x509Factory() {
        try {
            return CertificateFactory.getInstance("X.509");
        } catch (CertificateException unavailable) {
            throw new ProtosTlsFailure(
                    ProtosTlsFailure.Fact.BACKEND_UNAVAILABLE, "X.509 is unavailable", unavailable);
        }
    }

    /** A minimal bounded DER TLV reader sufficient for GeneralNames. */
    private static final class Der {
        private final byte[] bytes;
        private final int start;
        private final int end;
        private int position;

        Der(byte[] bytes, int start, int end) {
            this.bytes = bytes;
            this.start = start;
            this.end = end;
            this.position = start;
        }

        boolean hasMore() {
            return position < end;
        }

        int peekTag() {
            require(position < end);
            return bytes[position] & 0xff;
        }

        Der next(int tag) {
            require(peekTag() == tag);
            position++;
            require(position < end);
            int length = bytes[position++] & 0xff;
            if (length >= 0x80) {
                int count = length & 0x7f;
                require(count >= 1 && count <= 3 && position + count <= end);
                length = 0;
                for (int i = 0; i < count; i++) {
                    length = (length << 8) | (bytes[position++] & 0xff);
                }
            }
            require(length <= end - position);
            Der contents = new Der(bytes, position, position + length);
            position += length;
            return contents;
        }

        byte[] contents() {
            return Arrays.copyOfRange(bytes, start, end);
        }

        private static void require(boolean condition) {
            if (!condition) {
                throw new IllegalArgumentException("malformed subjectAltName");
            }
        }
    }
}

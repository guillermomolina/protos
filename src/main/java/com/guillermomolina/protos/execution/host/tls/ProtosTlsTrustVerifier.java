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

import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * The PKIX chain verifier of one client handshake (D220–S12).
 *
 * <p>Only chain trust against the anchors the Protos layer selected, and TLS server-usage, are
 * checked here; they must run inside the JSSE callback so an untrusted peer never completes the
 * handshake. Reference-identity matching is Protos policy (I130-B {@code
 * lib/tls/internal/Identity.protos}) over the SAN entries {@link ProtosTlsTrust} decodes from
 * {@link #verifiedLeaf()}; the secure duplex is released only after both succeed.
 *
 * <p>The chain is validated with a {@link CertPathValidator} over leaf-first prefixes of the
 * presented chain, never a {@code CertPathBuilder}, so verification never fetches issuers (AIA);
 * revocation checking is disabled so no OCSP/CRL endpoint is contacted. JSSE's HTTPS endpoint
 * identification is not used.
 */
final class ProtosTlsTrustVerifier extends X509ExtendedTrustManager {
    private static final String SERVER_AUTH = "1.3.6.1.5.5.7.3.1";
    private static final String ANY_EXTENDED_KEY_USAGE = "2.5.29.37.0";
    private static final String EXTENDED_KEY_USAGE_OID = "2.5.29.37";

    private final Set<TrustAnchor> anchors;
    private final Date validationDate;
    private volatile X509Certificate verifiedLeaf;

    /**
     * @param validationDate the PKIX validity instant, or {@code null} for the current time; only
     *     tests fix it, so fixtures do not depend on the machine clock
     */
    ProtosTlsTrustVerifier(Set<TrustAnchor> anchors, Date validationDate) {
        this.anchors = anchors;
        this.validationDate = validationDate;
    }

    /** The end-entity certificate of a chain that passed verification, or {@code null}. */
    X509Certificate verifiedLeaf() {
        return verifiedLeaf;
    }

    void verify(X509Certificate[] chain) throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw untrusted("the peer presented no certificate");
        }
        if (!trusted(chain)) {
            throw untrusted("the peer chain does not verify to a selected anchor");
        }
        if (!permitsServerAuth(chain[0])) {
            throw untrusted("the peer certificate is not valid for TLS server authentication");
        }
        verifiedLeaf = chain[0];
    }

    private boolean trusted(X509Certificate[] chain) {
        CertPathValidator validator;
        PKIXParameters parameters;
        try {
            validator = CertPathValidator.getInstance("PKIX");
            parameters = new PKIXParameters(anchors);
        } catch (GeneralSecurityException unavailable) {
            throw new ProtosTlsFailure(
                    ProtosTlsFailure.Fact.BACKEND_UNAVAILABLE, "PKIX is unavailable", unavailable);
        }
        parameters.setRevocationEnabled(false);
        parameters.setDate(validationDate);
        parameters.addCertPathChecker(new ExtendedKeyUsageMarker());
        for (int length = 1; length <= chain.length; length++) {
            try {
                CertPath path =
                        ProtosTlsTrust.x509Factory()
                                .generateCertPath(
                                        List.<Certificate>of(Arrays.copyOf(chain, length)));
                validator.validate(path, parameters);
                return true;
            } catch (GeneralSecurityException notThisPrefix) {
                // A longer prefix may reach a selected anchor.
            }
        }
        return false;
    }

    private static boolean permitsServerAuth(X509Certificate leaf) {
        try {
            List<String> usages = leaf.getExtendedKeyUsage();
            return usages == null
                    || usages.contains(SERVER_AUTH)
                    || usages.contains(ANY_EXTENDED_KEY_USAGE);
        } catch (CertificateParsingException malformed) {
            return false;
        }
    }

    private static CertificateException untrusted(String detail) {
        return new CertificateException(
                detail, new ProtosTlsFailure(ProtosTlsFailure.Fact.UNTRUSTED_CHAIN, detail));
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        verify(chain);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        verify(chain);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        verify(chain);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        throw new CertificateException("std:tls/Client authenticates no TLS client");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        throw new CertificateException("std:tls/Client authenticates no TLS client");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        throw new CertificateException("std:tls/Client authenticates no TLS client");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    /**
     * Marks a critical extendedKeyUsage extension as processed, so PKIX validation does not reject
     * it; {@link #permitsServerAuth} checks its content for the end-entity certificate.
     */
    private static final class ExtendedKeyUsageMarker extends PKIXCertPathChecker {
        @Override
        public void init(boolean forward) {}

        @Override
        public boolean isForwardCheckingSupported() {
            return true;
        }

        @Override
        public Set<String> getSupportedExtensions() {
            return Set.of(EXTENDED_KEY_USAGE_OID);
        }

        @Override
        public void check(Certificate certificate, Collection<String> unresolved) {
            unresolved.remove(EXTENDED_KEY_USAGE_OID);
        }
    }
}

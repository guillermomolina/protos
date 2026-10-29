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

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;

/**
 * The private TLS record engine of {@code std:tls/Client} (D220–S12): a transport-neutral,
 * non-blocking driver of one client JSSE {@link SSLEngine}.
 *
 * <p>Ownership boundary. Everything portable is Protos (I130-B {@code lib/tls/}): argument and
 * options admission, trust selection, reference-identity validation and matching, the handshake
 * Task, moving octets to and from the transport, handshake-byte accounting, the logical write
 * queue and its admission, read Futures and cancellation, close/custody and the mapping of {@link
 * ProtosTlsFailure.Fact} to {@code TlsError.kind}. This class keeps only state JSSE needs: the
 * engine, its delegated tasks, and the buffers {@code wrap}/{@code unwrap} require.
 *
 * <p>Security profile. Versions, suites, SNI and the ALPN offer are fixed on the engine before
 * {@code beginHandshake}; the suites are intersected with a fixed compliant set so a caller
 * cannot widen them. After negotiation the session is re-checked defensively before it is
 * reported established; only these conclusive checks produce the version/ALPN facts.
 *
 * <p>Ownership and confinement. One instance serves one handshake of one Actor and is used only
 * on the thread running that Actor's turn. Each instance creates its own {@link SSLContext}, so no
 * session cache or ticket is shared and no resumption occurs. Delegated tasks are CPU-bound and
 * run inline; {@link #advance} never waits on a transport.
 *
 * <p>Buffers. Received ciphertext and unwrapped plaintext are each bounded by {@value
 * #ENGINE_BUFFER_BYTES} octets; {@link #wrapPlaintext} wraps at most {@value
 * #MAX_PLAINTEXT_RECORD} octets per call. Unwrapped plaintext leaves only through {@link
 * #takePlaintext}, so it is never lost. After the first failure every state-advancing call
 * rethrows that failure; already unwrapped plaintext remains takeable.
 */
public final class ProtosTlsClientEngine {
    public static final int MAX_PLAINTEXT_RECORD = 16_384;
    public static final int ENGINE_BUFFER_BYTES = 65_536;

    /** The TLS record header: content type, legacy version and fragment length. */
    private static final int RECORD_HEADER_BYTES = 5;
    /** The approved bound on an advertised encrypted fragment, header excluded. */
    private static final int MAX_ENCRYPTED_FRAGMENT = 18_432;

    private static final String HTTP_1_1 = "http/1.1";
    private static final String TLS_1_3 = "TLSv1.3";
    private static final String TLS_1_2 = "TLSv1.2";
    private static final List<String> TLS_1_3_SUITES =
            List.of(
                    "TLS_AES_128_GCM_SHA256",
                    "TLS_AES_256_GCM_SHA384",
                    "TLS_CHACHA20_POLY1305_SHA256");
    private static final List<String> TLS_1_2_SUITES =
            List.of(
                    "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                    "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384");
    private static final ByteBuffer NOTHING = ByteBuffer.allocate(0);

    private final SSLEngine engine;
    private final ProtosTlsTrustVerifier verifier;
    private final Set<String> enabledProtocols;
    private final Set<String> enabledSuites;
    private final boolean offerHttp11;
    private final ByteBuffer received = ByteBuffer.allocate(ENGINE_BUFFER_BYTES);
    private final ByteBuffer plaintext = ByteBuffer.allocate(ENGINE_BUFFER_BYTES);
    private final ByteBuffer outbound;
    private boolean established;
    private boolean endOfInput;
    private boolean peerClosed;
    private ProtosTlsFailure failure;

    private ProtosTlsClientEngine(
            SSLEngine engine,
            ProtosTlsTrustVerifier verifier,
            Map<String, List<String>> profile,
            boolean offerHttp11) {
        this.engine = engine;
        this.verifier = verifier;
        this.enabledProtocols = Set.copyOf(profile.keySet());
        this.enabledSuites =
                Set.copyOf(profile.values().stream().flatMap(List::stream).toList());
        this.offerHttp11 = offerHttp11;
        // Room for at least two maximal records keeps wrap from overflowing between drains.
        this.outbound = ByteBuffer.allocate(Math.max(ENGINE_BUFFER_BYTES, packetSize() * 2));
    }

    /**
     * Configures a fresh engine with the fixed security profile and begins the handshake. No TLS
     * octet exists until the first {@link #advance}.
     *
     * @param anchors the anchors the Protos layer selected; never empty
     * @param sniHostName the normalized DNS reference to send as SNI, or {@code null} to send none
     * @param tls13Only whether TLS 1.2 is excluded
     * @param offerHttp11 whether ALPN offers {@code http/1.1}; otherwise ALPN is not offered
     * @throws ProtosTlsFailure {@link ProtosTlsFailure.Fact#BACKEND_UNAVAILABLE}
     */
    public static ProtosTlsClientEngine start(
            Set<TrustAnchor> anchors, String sniHostName, boolean tls13Only, boolean offerHttp11) {
        return start(anchors, sniHostName, tls13Only, offerHttp11, null);
    }

    /** As {@link #start(Set, String, boolean, boolean)} with a fixed PKIX instant (tests). */
    static ProtosTlsClientEngine start(
            Set<TrustAnchor> anchors,
            String sniHostName,
            boolean tls13Only,
            boolean offerHttp11,
            Date validationDate) {
        if (anchors.isEmpty()) {
            throw new IllegalArgumentException("the Protos layer selects at least one anchor");
        }
        ProtosTlsTrustVerifier verifier = new ProtosTlsTrustVerifier(anchors, validationDate);
        SSLEngine engine;
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {verifier}, null);
            engine = context.createSSLEngine();
        } catch (GeneralSecurityException | IllegalStateException unavailable) {
            throw new ProtosTlsFailure(
                    ProtosTlsFailure.Fact.BACKEND_UNAVAILABLE, "no JSSE TLS context", unavailable);
        }
        engine.setUseClientMode(true);
        List<String> supportedSuites = Arrays.asList(engine.getSupportedCipherSuites());
        List<String> supportedProtocols = Arrays.asList(engine.getSupportedProtocols());
        Map<String, List<String>> profile = new LinkedHashMap<>();
        addProtocol(TLS_1_3, TLS_1_3_SUITES, supportedProtocols, supportedSuites, profile);
        if (!tls13Only) {
            addProtocol(TLS_1_2, TLS_1_2_SUITES, supportedProtocols, supportedSuites, profile);
        }
        if (profile.isEmpty()) {
            throw new ProtosTlsFailure(
                    ProtosTlsFailure.Fact.BACKEND_UNAVAILABLE,
                    "the provider supports no compliant TLS configuration");
        }
        SSLParameters parameters = engine.getSSLParameters();
        parameters.setProtocols(profile.keySet().toArray(String[]::new));
        parameters.setCipherSuites(
                profile.values().stream().flatMap(List::stream).toArray(String[]::new));
        List<SNIServerName> serverNames = new ArrayList<>();
        if (sniHostName != null) {
            serverNames.add(new SNIHostName(sniHostName));
        }
        parameters.setServerNames(serverNames);
        // Identity is matched by the Protos layer, not by JSSE's HTTPS rule.
        parameters.setEndpointIdentificationAlgorithm(null);
        parameters.setApplicationProtocols(
                offerHttp11 ? new String[] {HTTP_1_1} : new String[0]);
        engine.setSSLParameters(parameters);
        ProtosTlsClientEngine result =
                new ProtosTlsClientEngine(engine, verifier, profile, offerHttp11);
        try {
            engine.beginHandshake();
        } catch (SSLException unavailable) {
            throw new ProtosTlsFailure(
                    ProtosTlsFailure.Fact.BACKEND_UNAVAILABLE,
                    "the handshake cannot begin",
                    unavailable);
        }
        return result;
    }

    private static void addProtocol(
            String protocol,
            List<String> candidates,
            List<String> supportedProtocols,
            List<String> supportedSuites,
            Map<String, List<String>> profile) {
        List<String> available = candidates.stream().filter(supportedSuites::contains).toList();
        if (supportedProtocols.contains(protocol) && !available.isEmpty()) {
            profile.put(protocol, available);
        }
    }

    /** Free room for received ciphertext; zero means the transport must not be read now. */
    public int receiveCapacity() {
        return received.remaining();
    }

    /** Copies up to {@link #receiveCapacity()} received octets and answers how many were taken. */
    public int offerCiphertext(byte[] source, int offset, int length) {
        int taken = Math.min(length, received.remaining());
        received.put(source, offset, taken);
        return taken;
    }

    /** Records physical end of transport input; it is never authenticated closure by itself. */
    public void transportEndOfInput() {
        endOfInput = true;
    }

    /**
     * Performs every engine step possible without transport I/O.
     *
     * @throws ProtosTlsFailure the first failure fact, and the same failure thereafter
     */
    public void advance() {
        checkFailure();
        try {
            boolean progress = true;
            while (progress) {
                progress =
                        switch (engine.getHandshakeStatus()) {
                            case NEED_TASK -> runDelegatedTasks();
                            case NEED_WRAP -> wrap(NOTHING);
                            case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> unwrap();
                            case FINISHED, NOT_HANDSHAKING -> {
                                if (!established && !engine.isOutboundDone()) {
                                    establish();
                                }
                                yield unwrap();
                            }
                        };
            }
            // Ciphertext held back only because unwrapped plaintext is unread is not truncation.
            boolean blockedByReader =
                    received.position() > 0 && plaintext.remaining() < applicationSize();
            if (endOfInput && !peerClosed && !blockedByReader) {
                throw truncated();
            }
        } catch (SSLException jsse) {
            throw fail(classify(jsse));
        } catch (ProtosTlsFailure fact) {
            throw fail(fact);
        }
    }

    /** The defensive post-negotiation re-check of the profile fixed before the handshake. */
    private void establish() {
        if (verifier.verifiedLeaf() == null) {
            throw new ProtosTlsFailure(
                    ProtosTlsFailure.Fact.HANDSHAKE_FAILED, "the peer chain was not verified");
        }
        if (!enabledProtocols.contains(engine.getSession().getProtocol())) {
            throw new ProtosTlsFailure(
                    ProtosTlsFailure.Fact.NEGOTIATED_VERSION_OUTSIDE_POLICY,
                    engine.getSession().getProtocol());
        }
        if (!enabledSuites.contains(engine.getSession().getCipherSuite())) {
            throw new ProtosTlsFailure(
                    ProtosTlsFailure.Fact.HANDSHAKE_FAILED,
                    "negotiated a suite outside the profile");
        }
        String selected = engine.getApplicationProtocol();
        boolean noSelection = selected == null || selected.isEmpty();
        if (!noSelection && !(offerHttp11 && HTTP_1_1.equals(selected))) {
            throw new ProtosTlsFailure(
                    ProtosTlsFailure.Fact.NEGOTIATED_ALPN_OUTSIDE_POLICY, selected);
        }
        established = true;
    }

    private boolean runDelegatedTasks() {
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            task.run();
        }
        return true;
    }

    private boolean wrap(ByteBuffer source) throws SSLException {
        if (outbound.remaining() < packetSize()) {
            return false;
        }
        SSLEngineResult result = engine.wrap(source, outbound);
        return switch (result.getStatus()) {
            case OK, CLOSED -> afterResult(result);
            case BUFFER_OVERFLOW, BUFFER_UNDERFLOW -> false;
        };
    }

    private boolean unwrap() throws SSLException {
        received.flip();
        SSLEngineResult result;
        try {
            // Every engine unwrap is preceded by the check of the record it may consume next.
            checkInboundRecordSize();
            if (!received.hasRemaining() || plaintext.remaining() < applicationSize()) {
                return false;
            }
            result = engine.unwrap(received, plaintext);
        } finally {
            received.compact();
        }
        return switch (result.getStatus()) {
            case OK -> afterResult(result);
            case CLOSED -> {
                peerClosed |= engine.isInboundDone();
                yield afterResult(result);
            }
            case BUFFER_UNDERFLOW -> false;
            case BUFFER_OVERFLOW -> {
                if (plaintext.position() == 0) {
                    throw new ProtosTlsFailure(
                            ProtosTlsFailure.Fact.PROTOCOL_FAILED,
                            "a record exceeds the unwrap buffer");
                }
                yield false;
            }
        };
    }

    /**
     * Rejects the first unconsumed record as soon as its header advertises an encrypted fragment
     * over {@value #MAX_ENCRYPTED_FRAGMENT} octets. {@link #received} is in read mode; the header
     * is read with absolute indices from its current position, and neither position nor limit
     * changes. Unrecognizable headers and version-specific limits are left to JSSE.
     */
    private void checkInboundRecordSize() {
        int start = received.position();
        if (received.limit() - start < RECORD_HEADER_BYTES) {
            return;
        }
        int contentType = received.get(start) & 0xFF;
        int major = received.get(start + 1) & 0xFF;
        int minor = received.get(start + 2) & 0xFF;
        boolean plausible =
                contentType >= 20 && contentType <= 23 && major == 3 && minor >= 1 && minor <= 3;
        if (!plausible) {
            return;
        }
        int fragmentLength =
                ((received.get(start + 3) & 0xFF) << 8) | (received.get(start + 4) & 0xFF);
        if (fragmentLength > MAX_ENCRYPTED_FRAGMENT) {
            throw new ProtosTlsFailure(
                    ProtosTlsFailure.Fact.RECORD_LIMIT_EXCEEDED,
                    "a record advertises " + fragmentLength + " encrypted octets");
        }
    }

    private boolean afterResult(SSLEngineResult result) {
        SSLEngineResult.HandshakeStatus status = result.getHandshakeStatus();
        if (status == SSLEngineResult.HandshakeStatus.FINISHED
                && !established
                && !engine.isOutboundDone()) {
            establish();
        }
        return result.bytesConsumed() > 0
                || result.bytesProduced() > 0
                || status == SSLEngineResult.HandshakeStatus.NEED_TASK
                || status == SSLEngineResult.HandshakeStatus.FINISHED;
    }

    /**
     * Only a fact recorded by this backend is specific; any other JSSE failure is reported as the
     * general fact for the current phase. Exception wording and peer alerts are not evidence.
     */
    private ProtosTlsFailure classify(SSLException jsse) {
        for (Throwable cause = jsse; cause != null; cause = cause.getCause()) {
            if (cause instanceof ProtosTlsFailure fact) {
                return fact;
            }
        }
        return new ProtosTlsFailure(
                established
                        ? ProtosTlsFailure.Fact.PROTOCOL_FAILED
                        : ProtosTlsFailure.Fact.HANDSHAKE_FAILED,
                String.valueOf(jsse.getMessage()),
                jsse);
    }

    private ProtosTlsFailure truncated() {
        try {
            engine.closeInbound();
        } catch (SSLException expected) {
            return new ProtosTlsFailure(
                    ProtosTlsFailure.Fact.TRUNCATED,
                    "transport input ended without close_notify",
                    expected);
        }
        return new ProtosTlsFailure(
                ProtosTlsFailure.Fact.TRUNCATED, "transport input ended without close_notify");
    }

    private ProtosTlsFailure fail(ProtosTlsFailure fact) {
        if (failure == null) {
            failure = fact;
            // Queue the fatal alert JSSE prepared, if any, for best-effort delivery.
            try {
                while (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP
                        && outbound.remaining() >= packetSize()
                        && engine.wrap(NOTHING, outbound).bytesProduced() > 0) {
                    // Keep draining prepared alert records.
                }
            } catch (SSLException | RuntimeException ignored) {
                // The primary failure stands; the alert is advisory.
            }
        }
        return failure;
    }

    private void checkFailure() {
        if (failure != null) {
            throw failure;
        }
    }

    /** Whether negotiation, chain verification and the defensive profile re-check succeeded. */
    public boolean established() {
        return established;
    }

    public boolean hasCiphertext() {
        return outbound.position() > 0;
    }

    /** Takes up to {@code maxBytes} pending ciphertext octets in order. */
    public byte[] takeCiphertext(int maxBytes) {
        outbound.flip();
        byte[] result = new byte[Math.min(maxBytes, outbound.remaining())];
        outbound.get(result);
        outbound.compact();
        return result;
    }

    /** Unwrapped application octets not yet taken. */
    public int plaintextAvailable() {
        return plaintext.position();
    }

    /** Takes up to {@code maxBytes} unwrapped octets in order; possibly none. */
    public byte[] takePlaintext(int maxBytes) {
        plaintext.flip();
        byte[] result = new byte[Math.min(maxBytes, plaintext.remaining())];
        plaintext.get(result);
        plaintext.compact();
        return result;
    }

    /** Whether an authenticated close_notify has been received. */
    public boolean peerClosed() {
        return peerClosed;
    }

    /**
     * Wraps up to {@value #MAX_PLAINTEXT_RECORD} octets of {@code source} into one record and
     * answers how many were consumed: zero when not established or when ciphertext output must be
     * drained first. The logical write queue and its admission are Protos policy.
     */
    public int wrapPlaintext(byte[] source, int offset, int length) {
        checkFailure();
        if (!established || engine.isOutboundDone()) {
            return 0;
        }
        ByteBuffer record =
                ByteBuffer.wrap(source, offset, Math.min(length, MAX_PLAINTEXT_RECORD));
        try {
            wrap(record);
        } catch (SSLException jsse) {
            throw fail(classify(jsse));
        } catch (ProtosTlsFailure fact) {
            throw fail(fact);
        }
        return record.position() - offset;
    }

    /** Begins local closure; the next {@link #advance} produces close_notify. Never waits. */
    public void closeOutbound() {
        engine.closeOutbound();
    }

    public boolean closeNotifyProduced() {
        return engine.isOutboundDone();
    }

    /** The negotiated protocol version name; meaningful once {@link #established()}. */
    public String negotiatedProtocol() {
        return engine.getSession().getProtocol();
    }

    /** subjectAltName dNSName entries of the verified peer, for the Protos identity matcher. */
    public List<String> peerDnsNames() {
        return ProtosTlsTrust.dnsNames(verifiedLeaf());
    }

    /** subjectAltName iPAddress octets of the verified peer, for the Protos identity matcher. */
    public List<byte[]> peerIpAddresses() {
        return ProtosTlsTrust.ipAddresses(verifiedLeaf());
    }

    private X509Certificate verifiedLeaf() {
        X509Certificate leaf = verifier.verifiedLeaf();
        if (!established || leaf == null) {
            throw new IllegalStateException("peer identities are available once established");
        }
        return leaf;
    }

    private int packetSize() {
        return engine.getSession().getPacketBufferSize();
    }

    private int applicationSize() {
        return engine.getSession().getApplicationBufferSize();
    }
}

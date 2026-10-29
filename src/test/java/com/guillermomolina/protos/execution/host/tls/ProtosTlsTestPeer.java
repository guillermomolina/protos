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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.BiFunction;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

/**
 * In-memory JSSE server peer and certificate fixtures for the I130-A TLS backend tests.
 *
 * <p>The fixtures under {@code src/test/resources/tls/} are generated test material (OpenSSL 3,
 * P-256 ECDSA, valid 2026-10-28 through 2126): {@code root-ca.pem} issues {@code
 * intermediate-ca.pem}, which issues {@code service-leaf.pem} (subject CN {@code
 * cn-only.protos.test}; SAN DNS {@code service.protos.test}, DNS {@code *.wild.protos.test}, IP
 * {@code 127.0.0.1}, IP {@code ::1}; serverAuth) and {@code cn-only-leaf.pem} (subject CN {@code
 * service.protos.test}, no SAN). {@code untrusted-ca.pem} is an unrelated self-signed CA. For
 * I130-C3, {@code idna-root-ca.pem} directly issues {@code idna-leaf.pem} (SAN DNS only the valid
 * A-label names {@code xn--bcher-kva.protos.test} and {@code *.xn--fa-hia.protos.test};
 * serverAuth) and {@code idna-malformed-leaf.pem} (SAN DNS {@code xn--zz.protos.test}, {@code
 * xn--a-0hc.protos.test}, {@code *.xn--a.protos.test}, {@code xn--bucher-xyd.protos.test} and the
 * NR-LDH {@code fallback.protos.test}; serverAuth). Every SAN of both leaves is an ASCII LDH
 * string, so certificate parsing and PKIX path validation accept them; identity is decided only
 * by the Protos layer. The IDNA root key is not retained. The keys
 * are unencrypted PKCS#8 and exist only for these tests. The fixtures were produced by the
 * implementation agent, not by the build. Client verification runs at a fixed PKIX instant inside
 * the validity window, so no test depends on the machine clock relative to {@code notBefore}.
 *
 * <p>No socket is involved: {@link #exchange} moves ciphertext between the engine under test and
 * the server peer until neither makes progress.
 */
final class ProtosTlsTestPeer {
    static final String SERVICE = "service.protos.test";

    final SSLEngine server;
    private final ByteBuffer fromClient = ByteBuffer.allocate(1 << 18);
    private final ByteBuffer toClient = ByteBuffer.allocate(1 << 18);
    private final ByteBuffer received = ByteBuffer.allocate(1 << 18);
    private final List<ByteBuffer> writes = new ArrayList<>();
    private SSLException failure;
    private boolean silent;

    private ProtosTlsTestPeer(SSLEngine server) {
        this.server = server;
    }

    static ProtosTlsTestPeer server(String leaf, String... protocols) {
        return server(leaf, null, protocols);
    }

    /**
     * A server presenting {@code leaf} plus the intermediate, enabling {@code protocols} and
     * offering ALPN {@code http/1.1}, or using {@code alpnSelector} when it is not {@code null}.
     */
    static ProtosTlsTestPeer server(
            String leaf,
            BiFunction<SSLEngine, List<String>, String> alpnSelector,
            String... protocols) {
        return serverWithIssuers(leaf, List.of("intermediate-ca.pem"), alpnSelector, protocols);
    }

    /**
     * A server presenting {@code leaf} followed by exactly the {@code issuers} fixtures, in order;
     * otherwise as {@link #server(String, BiFunction, String...)}.
     */
    static ProtosTlsTestPeer serverWithIssuers(
            String leaf,
            List<String> issuers,
            BiFunction<SSLEngine, List<String>, String> alpnSelector,
            String... protocols) {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            char[] password = "test".toCharArray();
            List<Certificate> chain = new ArrayList<>();
            chain.add(certificate(leaf + ".pem"));
            for (String issuer : issuers) {
                chain.add(certificate(issuer));
            }
            store.setKeyEntry(
                    "server",
                    privateKey(leaf + "-key.pem"),
                    password,
                    chain.toArray(new Certificate[0]));
            KeyManagerFactory keys =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, password);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keys.getKeyManagers(), null, null);
            SSLEngine engine = context.createSSLEngine();
            engine.setUseClientMode(false);
            SSLParameters parameters = engine.getSSLParameters();
            parameters.setProtocols(protocols);
            parameters.setApplicationProtocols(new String[] {"http/1.1"});
            engine.setSSLParameters(parameters);
            if (alpnSelector != null) {
                engine.setHandshakeApplicationProtocolSelector(alpnSelector);
            }
            return new ProtosTlsTestPeer(engine);
        } catch (Exception broken) {
            throw new IllegalStateException(broken);
        }
    }

    static X509Certificate certificate(String name) {
        try {
            return (X509Certificate)
                    CertificateFactory.getInstance("X.509")
                            .generateCertificate(new ByteArrayInputStream(pem(name)));
        } catch (Exception broken) {
            throw new IllegalStateException(broken);
        }
    }

    static byte[] der(String name) {
        try {
            return certificate(name).getEncoded();
        } catch (Exception broken) {
            throw new IllegalStateException(broken);
        }
    }

    private static PrivateKey privateKey(String name) throws Exception {
        String text = new String(pem(name), StandardCharsets.US_ASCII);
        String body = text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
    }

    private static byte[] pem(String name) {
        try (InputStream input = ProtosTlsTestPeer.class.getResourceAsStream("/tls/" + name)) {
            if (input == null) {
                throw new IllegalStateException("missing fixture " + name);
            }
            return input.readAllBytes();
        } catch (IOException broken) {
            throw new UncheckedIOException(broken);
        }
    }

    /** Stops sending anything, so the client can observe a physical end of input. */
    void goSilent() {
        silent = true;
    }

    void write(byte[] bytes) {
        writes.add(ByteBuffer.wrap(bytes));
    }

    void closeOutbound() {
        server.closeOutbound();
    }

    byte[] takeReceived() {
        received.flip();
        byte[] result = new byte[received.remaining()];
        received.get(result);
        received.clear();
        return result;
    }

    SSLException failure() {
        return failure;
    }

    /** Accepts ciphertext a client wrote through a transport and runs the server (I130-B). */
    void receive(byte[] ciphertext) {
        fromClient.put(ciphertext);
        step();
    }

    /** Runs the server and takes up to {@code maxBytes} ciphertext octets for the client. */
    byte[] send(int maxBytes) {
        step();
        toClient.flip();
        byte[] result = new byte[Math.min(maxBytes, toClient.remaining())];
        toClient.get(result);
        toClient.compact();
        return result;
    }

    /** Moves ciphertext both ways until quiescent; client failures propagate. */
    void exchange(ProtosTlsClientEngine client) {
        for (int round = 0; round < 1_000; round++) {
            client.advance();
            boolean moved = false;
            while (client.hasCiphertext()) {
                fromClient.put(client.takeCiphertext(fromClient.remaining()));
                moved = true;
            }
            moved |= step();
            if (toClient.position() > 0 && !silent) {
                toClient.flip();
                int taken =
                        client.offerCiphertext(
                                toClient.array(), toClient.position(), toClient.remaining());
                toClient.position(toClient.position() + taken);
                toClient.compact();
                moved |= taken > 0;
            }
            if (!moved) {
                client.advance();
                return;
            }
        }
        throw new IllegalStateException("the TLS exchange did not become quiescent");
    }

    private boolean step() {
        if (failure != null) {
            return false;
        }
        boolean progress = false;
        try {
            for (int guard = 0; guard < 1_000; guard++) {
                boolean moved =
                        switch (server.getHandshakeStatus()) {
                            case NEED_TASK -> runTasks();
                            case NEED_WRAP -> wrap(ByteBuffer.allocate(0));
                            case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> unwrap();
                            case FINISHED, NOT_HANDSHAKING -> applicationStep();
                        };
                if (!moved) {
                    return progress;
                }
                progress = true;
            }
            return progress;
        } catch (SSLException fatal) {
            failure = fatal;
            try {
                while (server.wrap(ByteBuffer.allocate(0), toClient).bytesProduced() > 0) {
                    // Flush the prepared alert to the client.
                }
            } catch (SSLException ignored) {
                // The alert is best effort.
            }
            return true;
        }
    }

    private boolean applicationStep() throws SSLException {
        boolean moved = unwrap();
        while (!writes.isEmpty()) {
            ByteBuffer head = writes.get(0);
            if (!wrap(head)) {
                break;
            }
            if (!head.hasRemaining()) {
                writes.remove(0);
            }
            moved = true;
        }
        return moved;
    }

    private boolean runTasks() {
        Runnable task;
        while ((task = server.getDelegatedTask()) != null) {
            task.run();
        }
        return true;
    }

    private boolean wrap(ByteBuffer source) throws SSLException {
        SSLEngineResult result = server.wrap(source, toClient);
        return result.bytesProduced() > 0 || result.bytesConsumed() > 0;
    }

    private boolean unwrap() throws SSLException {
        if (fromClient.position() == 0) {
            return false;
        }
        fromClient.flip();
        SSLEngineResult result;
        try {
            result = server.unwrap(fromClient, received);
        } finally {
            fromClient.compact();
        }
        return result.bytesConsumed() > 0 || result.bytesProduced() > 0;
    }
}

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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.io.ByteArrayOutputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Private host bridge of {@code std:tls/Client} (D220–S12, I130-B).
 *
 * <p>Every portable rule lives in Protos ({@code lib/tls/Client.protos} and {@code
 * lib/tls/internal/*.protos}): admission, options, trust descriptors and their bounds, trust
 * selection and the fail-closed sources, CA eligibility, reference-identity validation and
 * subjectAltName matching, SNI policy, the handshake Task, octet pumping and handshake accounting,
 * write admission and its bound, read retention, close and custody, and the mapping of backend
 * facts to {@code TlsError.kind}. This frozen facility supplies only what Protos cannot express:
 *
 * <ul>
 *   <li>the eight operations of {@link ProtosHttpAsyncFacility} ({@code failed}, {@code
 *       resolved}, {@code provides}, {@code closable}, {@code ordinaryObject}, {@code isBytes},
 *       {@code startService}, {@code enterService}), unchanged;
 *   <li>{@code trust}, a private sealed family ({@link ProtosSealedFamilyFacility}) so that only
 *       {@code Trust} can mint descriptors and their snapshots stay out of guest-visible slots;
 *   <li>{@code certificate(der)}: exact DER X.509 decoding and the raw CA facts the Protos
 *       eligibility rule reads (basicConstraints cA, keyUsage keyCertSign), with a fresh frozen
 *       octet snapshot;
 *   <li>{@code javaCacerts()}: one fresh read of exactly the Java {@code cacerts} file;
 *   <li>{@code startEngine(...)}: a fresh {@link ProtosTlsClientEngine} behind a frozen
 *       Actor-confined handle; backend failures are answered as the {@link ProtosTlsFailure.Fact}
 *       name, never as exception text;
 *   <li>{@code settle(outcomes, awaited, value)}, {@code pending(future)} and {@code
 *       failure(future)}: Future plumbing for the non-Task write and close Futures of the secure
 *       duplex; the probes are read-only and never wait;
 *   <li>{@code codePoint(scalar)} and {@code isNfc(scalars)}: raw ICU Unicode facts of one scalar
 *       and the NFC test, read by the Protos IDNA2008 A-label rules (I130-C3). They perform no
 *       mapping, conversion or IDNA processing and decide no admission; they are the shared
 *       {@link ProtosIdnaUnicodeFactsFacility} operations.
 * </ul>
 *
 * <p>Like {@link ProtosHttpAsyncFacility}, the facility is a deferred standard initial member of
 * exactly {@code std:tls/Client}: it is created only when an Actor-local instance of that module
 * initializes, captured lexically by the module source, and its bootstrap slot removed. It retains
 * no Actor, Task, engine or trust state; each engine handle belongs to the Actor execution domain
 * that created it and rejects every other domain.
 */
public final class ProtosTlsBackendFacility {
    public static final ProtosModuleKey MODULE_KEY = new ProtosModuleKey("std:tls/Client");
    public static final String BOOTSTRAP_SLOT = "_tlsBackendFacility";

    private ProtosTlsBackendFacility() {}

    public static ProtosObjectValue createFacility() {
        ProtosObjectValue facility = new ProtosObjectValue(ProtosObjectValue.rootObject());
        operation(facility, "failed", 1, ProtosHttpAsyncFacility::failed);
        operation(facility, "resolved", 1, ProtosHttpAsyncFacility::resolved);
        operation(facility, "provides", 2, ProtosHttpAsyncFacility::provides);
        operation(facility, "closable", 1, ProtosHttpAsyncFacility::closable);
        operation(facility, "ordinaryObject", 1, ProtosHttpAsyncFacility::ordinaryObject);
        operation(facility, "isBytes", 1, ProtosHttpAsyncFacility::isBytes);
        operation(facility, "startService", 1, ProtosHttpAsyncFacility::startService);
        operation(facility, "enterService", 0, ProtosHttpAsyncFacility::enterService);
        facility.createLocalSlot("trust", ProtosSealedFamilyFacility.createFacility());
        operation(facility, "certificate", 1, ProtosTlsBackendFacility::certificate);
        operation(facility, "javaCacerts", 0, ProtosTlsBackendFacility::javaCacerts);
        operation(facility, "startEngine", 4, ProtosTlsBackendFacility::startEngine);
        operation(facility, "settle", 3, ProtosTlsBackendFacility::settle);
        operation(facility, "pending", 1, ProtosTlsBackendFacility::pending);
        operation(facility, "failure", 1, ProtosTlsBackendFacility::failure);
        operation(facility, "codePoint", 1, ProtosIdnaUnicodeFactsFacility::codePoint);
        operation(facility, "isNfc", 1, ProtosIdnaUnicodeFactsFacility::isNfc);
        return facility.freeze();
    }

    private static void operation(
            ProtosObjectValue receiver, String name, int arity, ProtosNativeClosureBody body) {
        receiver.createLocalSlot(
                name,
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            if (activation.receiver() != receiver || supplied.size() != arity) {
                                throw invalid(activation);
                            }
                            return body.execute(activation, supplied);
                        }));
    }

    // ------------------------------------------------------------ certificate material

    /**
     * Answers {@code null} unless the argument is Bytes holding exactly one DER X.509 certificate;
     * otherwise a frozen object with {@code der} (a fresh frozen copy of the octets), {@code ca}
     * (basicConstraints cA) and {@code keyCertSign} ({@code null} when keyUsage is absent).
     */
    @TruffleBoundary
    private static Object certificate(ProtosActivation activation, List<?> supplied) {
        if (!(supplied.get(0) instanceof ProtosBytesValue bytes)) {
            throw invalid(activation);
        }
        byte[] der = bytes.octetSnapshot();
        Optional<X509Certificate> decoded = ProtosTlsTrust.exactDer(der);
        if (decoded.isEmpty()) {
            return ProtosNullValue.INSTANCE;
        }
        X509Certificate certificate = decoded.get();
        boolean[] keyUsage = certificate.getKeyUsage();
        ProtosObjectValue facts = new ProtosObjectValue(ProtosObjectValue.rootObject());
        facts.createLocalSlot("der", frozenBytes(activation, der));
        facts.createLocalSlot("ca", ProtosBooleanValue.of(certificate.getBasicConstraints() >= 0));
        facts.createLocalSlot(
                "keyCertSign",
                keyUsage == null
                        ? ProtosNullValue.INSTANCE
                        : ProtosBooleanValue.of(keyUsage.length > 5 && keyUsage[5]));
        return facts.freeze();
    }

    /**
     * Answers a frozen Array of fresh frozen DER Bytes, one per certificate entry of the Java
     * {@code cacerts} file read now, or {@code null} when that file is missing or unreadable. No
     * policy is applied: emptiness and eligibility are decided by the Protos caller.
     */
    @TruffleBoundary
    private static Object javaCacerts(ProtosActivation activation, List<?> supplied) {
        List<X509Certificate> certificates;
        try {
            certificates = ProtosTlsTrust.javaCacerts();
        } catch (ProtosTlsFailure unavailable) {
            return ProtosNullValue.INSTANCE;
        }
        List<Object> encoded = new ArrayList<>();
        for (X509Certificate certificate : certificates) {
            try {
                encoded.add(frozenBytes(activation, certificate.getEncoded()));
            } catch (CertificateEncodingException unreadable) {
                return ProtosNullValue.INSTANCE;
            }
        }
        return prelude(activation).newFrozenArray(encoded);
    }

    // ------------------------------------------------------------ engine

    /**
     * {@code startEngine(anchors, sniHostName, tls13Only, offerHttp11)}: {@code anchors} is the
     * nonempty Array of DER Bytes the Protos layer selected and already decoded; {@code
     * sniHostName} is a normalized DNS reference or {@code null}. Answers an engine handle, or the
     * name of the backend fact that prevented a compliant start.
     */
    @TruffleBoundary
    private static Object startEngine(ProtosActivation activation, List<?> supplied) {
        if (!(supplied.get(0) instanceof ProtosArrayValue anchorArray)
                || anchorArray.indexedSize() == 0
                || !(supplied.get(1) instanceof ProtosStringValue
                        || supplied.get(1) == ProtosNullValue.INSTANCE)
                || !(supplied.get(2) instanceof ProtosBooleanValue tls13Only)
                || !(supplied.get(3) instanceof ProtosBooleanValue offerHttp11)) {
            throw invalid(activation);
        }
        List<X509Certificate> certificates = new ArrayList<>();
        for (Object element : anchorArray.indexedSnapshot()) {
            if (!(element instanceof ProtosBytesValue der)) {
                throw invalid(activation);
            }
            certificates.add(
                    ProtosTlsTrust.exactDer(der.octetSnapshot())
                            .orElseThrow(() -> invalid(activation)));
        }
        String sni =
                supplied.get(1) instanceof ProtosStringValue name ? name.value() : null;
        ProtosTlsClientEngine engine;
        try {
            engine =
                    ProtosTlsClientEngine.start(
                            ProtosTlsTrust.anchors(certificates),
                            sni,
                            tls13Only == ProtosBooleanValue.TRUE,
                            offerHttp11 == ProtosBooleanValue.TRUE);
        } catch (ProtosTlsFailure unavailable) {
            return fact(unavailable);
        } catch (IllegalArgumentException rejectedServerName) {
            // The provider refused a name the Protos layer admitted: no compliant configuration.
            return new ProtosStringValue(ProtosTlsFailure.Fact.BACKEND_UNAVAILABLE.name());
        }
        return engineHandle(engine, activation.executionDomain());
    }

    /**
     * A frozen handle over one engine. Every operation checks that it runs in the creating Actor
     * execution domain, so a live engine never serves another Actor. Operations that can fail
     * answer the fact name instead of signalling; Bytes answers are fresh.
     */
    private static ProtosObjectValue engineHandle(
            ProtosTlsClientEngine engine, ProtosActorExecutionDomain owner) {
        ProtosObjectValue handle = new ProtosObjectValue(ProtosObjectValue.rootObject());
        confined(handle, owner, "advance", 0, (a, x) -> advance(engine));
        confined(handle, owner, "hasCiphertext", 0, (a, x) -> bool(engine.hasCiphertext()));
        confined(
                handle,
                owner,
                "takeCiphertext",
                0,
                (a, x) -> bytes(a, engine.takeCiphertext(Integer.MAX_VALUE)));
        confined(
                handle,
                owner,
                "receiveCapacity",
                0,
                (a, x) -> ProtosNumericValueSupport.integer(engine.receiveCapacity()));
        confined(handle, owner, "offer", 1, (a, x) -> offer(engine, a, x.get(0)));
        confined(
                handle,
                owner,
                "endOfInput",
                0,
                (a, x) -> {
                    engine.transportEndOfInput();
                    return ProtosNullValue.INSTANCE;
                });
        confined(handle, owner, "established", 0, (a, x) -> bool(engine.established()));
        confined(
                handle,
                owner,
                "plaintextAvailable",
                0,
                (a, x) -> ProtosNumericValueSupport.integer(engine.plaintextAvailable()));
        confined(
                handle,
                owner,
                "takePlaintext",
                1,
                (a, x) -> bytes(a, engine.takePlaintext(positiveInt(a, x.get(0)))));
        confined(handle, owner, "peerClosed", 0, (a, x) -> bool(engine.peerClosed()));
        confined(handle, owner, "seal", 1, (a, x) -> seal(engine, a, x.get(0)));
        confined(
                handle,
                owner,
                "closeOutbound",
                0,
                (a, x) -> {
                    engine.closeOutbound();
                    return ProtosNullValue.INSTANCE;
                });
        confined(handle, owner, "peerDnsNames", 0, (a, x) -> peerDnsNames(engine, a));
        confined(handle, owner, "peerIpAddresses", 0, (a, x) -> peerIpAddresses(engine, a));
        return handle.freeze();
    }

    private static void confined(
            ProtosObjectValue handle,
            ProtosActorExecutionDomain owner,
            String name,
            int arity,
            ProtosNativeClosureBody body) {
        operation(
                handle,
                name,
                arity,
                (activation, supplied) -> {
                    if (activation.executionDomain() != owner) {
                        throw invalid(activation);
                    }
                    return body.execute(activation, supplied);
                });
    }

    @TruffleBoundary
    private static Object advance(ProtosTlsClientEngine engine) {
        try {
            engine.advance();
            return ProtosNullValue.INSTANCE;
        } catch (ProtosTlsFailure failure) {
            return fact(failure);
        }
    }

    /** Offers received ciphertext; answers how many octets the bounded receive buffer took. */
    @TruffleBoundary
    private static Object offer(ProtosTlsClientEngine engine, ProtosActivation a, Object value) {
        if (!(value instanceof ProtosBytesValue received)) {
            throw invalid(a);
        }
        byte[] octets = received.octetSnapshot();
        return ProtosNumericValueSupport.integer(engine.offerCiphertext(octets, 0, octets.length));
    }

    /**
     * Wraps the complete octet snapshot of {@code value} into records and answers all ciphertext
     * produced since the last take, in order: Bytes; {@code null} when the engine cannot carry
     * application data (not established, or local closure began); or the fact name of a failure.
     * Ciphertext already pending in the engine precedes the new records, so record order follows
     * production order.
     */
    @TruffleBoundary
    private static Object seal(ProtosTlsClientEngine engine, ProtosActivation a, Object value) {
        if (!(value instanceof ProtosBytesValue plaintext)) {
            throw invalid(a);
        }
        byte[] source = plaintext.octetSnapshot();
        ByteArrayOutputStream ciphertext = new ByteArrayOutputStream();
        try {
            int offset = 0;
            while (offset < source.length) {
                int consumed = engine.wrapPlaintext(source, offset, source.length - offset);
                if (consumed == 0) {
                    if (!engine.hasCiphertext()) {
                        return ProtosNullValue.INSTANCE;
                    }
                    ciphertext.writeBytes(engine.takeCiphertext(Integer.MAX_VALUE));
                }
                offset += consumed;
            }
        } catch (ProtosTlsFailure failure) {
            return fact(failure);
        }
        while (engine.hasCiphertext()) {
            ciphertext.writeBytes(engine.takeCiphertext(Integer.MAX_VALUE));
        }
        return bytes(a, ciphertext.toByteArray());
    }

    @TruffleBoundary
    private static Object peerDnsNames(ProtosTlsClientEngine engine, ProtosActivation a) {
        List<Object> names = new ArrayList<>();
        for (String name : engine.peerDnsNames()) {
            names.add(new ProtosStringValue(name));
        }
        return prelude(a).newFrozenArray(names);
    }

    @TruffleBoundary
    private static Object peerIpAddresses(ProtosTlsClientEngine engine, ProtosActivation a) {
        List<Object> addresses = new ArrayList<>();
        for (byte[] address : engine.peerIpAddresses()) {
            addresses.add(frozenBytes(a, address));
        }
        return prelude(a).newFrozenArray(addresses);
    }

    // ------------------------------------------------------------ Future plumbing

    /**
     * {@code settle(outcomes, awaited, value)} answers a fresh non-Task Future that becomes
     * terminal only after every Future of both Arrays is terminal. It then fails with the exact
     * Error of the first failed member of {@code outcomes} in Array order (a cancelled member
     * yields a fresh {@code IOError}); otherwise it resolves with {@code value}. Members of {@code
     * awaited} are waited for, never reported. The operations it represents are already
     * committed, so a cancellation request is recorded without effect. No guest code runs when a
     * member becomes terminal.
     */
    @TruffleBoundary
    private static Object settle(ProtosActivation activation, List<?> supplied) {
        List<ProtosFutureValue> outcomes = futures(activation, supplied.get(0));
        List<ProtosFutureValue> awaited = futures(activation, supplied.get(1));
        Object value = supplied.get(2);
        if (value instanceof ProtosFutureValue) {
            throw invalid(activation);
        }
        ProtosPrelude prelude = prelude(activation);
        ProtosFutureValue result = ProtosHttpAsyncFacility.freshFuture(activation);
        result.attachCancellationProducer(() -> {});
        List<ProtosFutureValue> all = new ArrayList<>(outcomes);
        all.addAll(awaited);
        AtomicInteger remaining = new AtomicInteger(all.size() + 1);
        Runnable finish =
                () -> {
                    if (remaining.decrementAndGet() != 0) {
                        return;
                    }
                    for (ProtosFutureValue outcome : outcomes) {
                        switch (outcome.state()) {
                            case FAILED -> {
                                result.fail(outcome.failedError().orElseThrow());
                                return;
                            }
                            case CANCELLED -> {
                                result.fail(
                                        ProtosCoreErrors.newOccurrence(
                                                prelude, ProtosCoreErrors.StandardError.I_O_ERROR));
                                return;
                            }
                            default -> {}
                        }
                    }
                    result.resolve(value, activation);
                };
        for (ProtosFutureValue member : all) {
            member.observe(terminal -> finish.run());
        }
        // Releases the guard count only after every observer is registered.
        finish.run();
        return result;
    }

    private static Object pending(ProtosActivation activation, List<?> supplied) {
        if (!(supplied.get(0) instanceof ProtosFutureValue future)
                || future.domain() != activation.executionDomain()) {
            throw invalid(activation);
        }
        return bool(future.isPending());
    }

    /** The exact Error a terminal failed Future holds, or {@code null}; never waits. */
    private static Object failure(ProtosActivation activation, List<?> supplied) {
        if (!(supplied.get(0) instanceof ProtosFutureValue future)
                || future.domain() != activation.executionDomain()) {
            throw invalid(activation);
        }
        return future.failedError().<Object>map(error -> error).orElse(ProtosNullValue.INSTANCE);
    }

    private static List<ProtosFutureValue> futures(ProtosActivation activation, Object value) {
        if (!(value instanceof ProtosArrayValue array)) {
            throw invalid(activation);
        }
        List<ProtosFutureValue> result = new ArrayList<>();
        for (Object element : array.indexedSnapshot()) {
            if (!(element instanceof ProtosFutureValue future)
                    || future.domain() != activation.executionDomain()) {
                throw invalid(activation);
            }
            result.add(future);
        }
        return result;
    }

    // ------------------------------------------------------------ values

    private static ProtosStringValue fact(ProtosTlsFailure failure) {
        return new ProtosStringValue(failure.fact().name());
    }

    private static int positiveInt(ProtosActivation activation, Object value) {
        if (!ProtosNumericValueSupport.isIntegerInIntRange(value)
                || ProtosNumericValueSupport.exactInt(value) <= 0) {
            throw invalid(activation);
        }
        return ProtosNumericValueSupport.exactInt(value);
    }

    private static ProtosBytesValue bytes(ProtosActivation activation, byte[] octets) {
        ProtosBytesValue result = new ProtosBytesValue(prelude(activation).bytesPrototypeForRuntime());
        for (byte octet : octets) {
            result.indexedAdd(ProtosNumericValueSupport.octet(octet & 255));
        }
        return result;
    }

    private static ProtosBytesValue frozenBytes(ProtosActivation activation, byte[] octets) {
        ProtosBytesValue result = bytes(activation, octets);
        result.freeze();
        return result;
    }

    private static ProtosPrelude prelude(ProtosActivation activation) {
        return activation.prelude().orElseThrow(() -> invalid(activation));
    }

    private static ProtosBooleanValue bool(boolean value) {
        return ProtosBooleanValue.of(value);
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}

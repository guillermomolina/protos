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
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosEntropyCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.process.ProtosProcessExecutionHost;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.ibm.icu.text.Normalizer2;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Private Argon2id primitive for std:crypto/password (I160, D245-C).
 *
 * <p>This class owns only the fixed numerical derivation profile.
 * Protos owns normalization, record syntax, semantic admission,
 * Future publication, Actor ownership, and error classification.
 *
 * <p>Callers must not run this memory-hard primitive on an Actor turn.
 * The async host backend will provide bounded off-turn execution.
 *
 * <p>Input arrays are borrowed. Independently owned temporary
 * copies are cleared on a best-effort basis. Neither Java GC
 * memory nor provider-managed memory is claimed to be erased.
 */
public final class ProtosPasswordArgon2Facility {

    public static final ProtosModuleKey MODULE_KEY =
            ProtosModuleKey.module("std:crypto/password");

    public static final String BOOTSTRAP_SLOT =
            "_passwordArgon2Facility";

    public static final int MEMORY_KIB = 65536;
    public static final int ITERATIONS = 3;
    public static final int PARALLELISM = 4;
    public static final int SALT_BYTES = 16;
    public static final int TAG_BYTES = 32;
    public static final int MAX_PASSWORD_BYTES = 65536;

    private ProtosPasswordArgon2Facility() {}

    /**
     * Derive the exact D245-C Argon2id v1 tag.
     *
     * <p>Inputs must already have passed the semantic String,
     * NFC and strict UTF-8 checks at the private admission boundary.
     *
     * @return an independently owned 32-octet tag
     */

    private static final Normalizer2 NFC = Normalizer2.getNFCInstance();

    /**
     * Validate an actual standard Protos String and encode its NFC
     * representation using strict UTF-8.
     *
     * <p>No guest conversion, selector, or overridden getter is called.
     */
    static byte[] normalizePassword(Object supplied) {
        if (!(supplied instanceof ProtosStringValue password)) {
            throw new IllegalArgumentException("Invalid password argument");
        }

        String original = password.value();

        // Bound pre-normalization work independently of the final UTF-8
        // limit. Canonical composition cannot justify an unbounded input.
        if (original.length() > MAX_PASSWORD_BYTES * 2) {
            throw new IllegalArgumentException("Password exceeds limit");
        }

        String normalized = NFC.normalize(original);

        try {
            ByteBuffer encoded = StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(normalized));

            if (encoded.remaining() > MAX_PASSWORD_BYTES) {
                throw new IllegalArgumentException("Password exceeds limit");
            }

            byte[] result = new byte[encoded.remaining()];
            encoded.get(result);
            return result;
        } catch (CharacterCodingException invalidUnicode) {
            throw new IllegalArgumentException("Invalid password argument");
        }
    }

    /**
     * Capture an independently owned exact-octet salt snapshot.
     *
     * <p>The original Bytes may be mutated after admission without
     * modifying the captured input.
     */
    static byte[] captureSalt(Object supplied) {
        if (!(supplied instanceof ProtosBytesValue bytes)) {
            throw new IllegalArgumentException("Invalid salt argument");
        }

        byte[] snapshot = bytes.octetSnapshot();

        if (snapshot.length != SALT_BYTES) {
            Arrays.fill(snapshot, (byte) 0);
            throw new IllegalArgumentException("Invalid salt argument");
        }

        return snapshot;
    }

    /**
     * Capture the expected complete tag without retaining a Bytes alias.
     */
    static byte[] captureTag(Object supplied) {
        if (!(supplied instanceof ProtosBytesValue bytes)) {
            throw new IllegalArgumentException("Invalid tag argument");
        }

        byte[] snapshot = bytes.octetSnapshot();

        if (snapshot.length != TAG_BYTES) {
            Arrays.fill(snapshot, (byte) 0);
            throw new IllegalArgumentException("Invalid tag argument");
        }

        return snapshot;
    }


    /**
     * Deferred, exact-module facility. Only the ordinary password
     * module initializer can receive this private bootstrap member.
     */
    public static ProtosObjectValue createFacility() {
        ProtosObjectValue facility =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        operation(facility, "validPassword", 1,
                ProtosPasswordArgon2Facility::validPassword);
        operation(facility, "authenticEntropy", 1,
                ProtosPasswordArgon2Facility::authenticEntropy);
        operation(facility, "failed", 1,
                ProtosPasswordArgon2Facility::failed);
        operation(facility, "hashWithSalt", 3,
                ProtosPasswordArgon2Facility::hashWithSalt);
        operation(facility, "verify", 4,
                ProtosPasswordArgon2Facility::verifyPassword);

        return facility.freeze();
    }

    private static void operation(
            ProtosObjectValue facility,
            String name,
            int arity,
            ProtosNativeClosureBody body) {

        facility.createLocalSlot(
                name,
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            if (activation.receiver() != facility
                                    || supplied.size() != arity) {
                                throw new ProtosSignalException(
                                        ProtosCoreErrors.newError(activation));
                            }

                            return body.execute(activation, supplied);
                        }));
    }

    private static ProtosBooleanValue bool(boolean value) {
        return value
                ? ProtosBooleanValue.TRUE
                : ProtosBooleanValue.FALSE;
    }

    /**
     * Checks the actual standard String representation and the
     * complete post-NFC UTF-8 limit without guest conversion.
     */
    private static Object validPassword(
            ProtosActivation activation, List<?> supplied) {

        byte[] normalized = null;

        try {
            normalized = normalizePassword(supplied.get(0));
            return ProtosBooleanValue.TRUE;
        } catch (IllegalArgumentException invalid) {
            return ProtosBooleanValue.FALSE;
        } finally {
            wipe(normalized);
        }
    }

    /**
     * Authentic D243-C grant, bound to the exact current Process
     * and available only to its original RootActor.
     */
    private static Object authenticEntropy(
            ProtosActivation activation, List<?> supplied) {

        if (!(supplied.get(0) instanceof ProtosEntropyCapabilityValue entropy)
                || entropy.retiredForRuntime()) {
            return ProtosBooleanValue.FALSE;
        }

        Optional<ProtosActor> actor =
                activation.executionDomain().currentActorForRuntime();

        if (actor.isEmpty()) {
            return ProtosBooleanValue.FALSE;
        }

        Optional<ProtosProcessRuntime> process =
                actor.orElseThrow().processForRuntime();

        if (process.isEmpty()) {
            return ProtosBooleanValue.FALSE;
        }

        ProtosProcessRuntime owner = process.orElseThrow();

        return bool(
                actor.orElseThrow() == owner.rootActorForRuntime()
                        && entropy.ownerForRuntime().orElse(null) == owner
                        && !entropy.retiredForRuntime());
    }

    private static ProtosFutureValue freshFuture(
            ProtosActivation activation) {

        ProtosPrelude prelude = activation.prelude().orElseThrow();

        return new ProtosFutureValue(
                prelude.futurePrototype(),
                activation.executionDomain());
    }

    /**
     * Invalid dispatch arguments and unsupported host placement
     * are represented by ordinary failed Futures.
     */
    private static Object failed(
            ProtosActivation activation, List<?> supplied) {

        if (!(supplied.get(0) instanceof ProtosObjectValue error)) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }

        ProtosFutureValue future = freshFuture(activation);
        future.fail(error);
        return future;
    }

    private static ProtosObjectValue error(
            ProtosObjectValue prototype, String kind) {

        ProtosObjectValue occurrence = new ProtosObjectValue(prototype);
        occurrence.createLocalSlot("kind", new ProtosStringValue(kind));
        return occurrence;
    }

    private static void wipe(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    /**
     * Complete an argument failure without submitting a KDF job.
     */
    private static ProtosFutureValue failedKind(
            ProtosActivation activation,
            ProtosObjectValue prototype,
            String kind) {

        ProtosFutureValue future = freshFuture(activation);
        future.fail(error(prototype, kind));
        return future;
    }

    private static Object hashWithSalt(
            ProtosActivation activation, List<?> supplied) {

        if (!(supplied.get(2) instanceof ProtosObjectValue errors)) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }

        byte[] password = null;
        byte[] salt = null;

        try {
            password = normalizePassword(supplied.get(0));
            salt = captureSalt(supplied.get(1));

            return submit(
                    activation, password, salt, null, errors);
        } catch (IllegalArgumentException invalid) {
            return failedKind(
                    activation, errors, "invalidArgument");
        } finally {
            wipe(password);
            wipe(salt);
        }
    }

    private static Object verifyPassword(
            ProtosActivation activation, List<?> supplied) {

        if (!(supplied.get(3) instanceof ProtosObjectValue errors)) {
            throw new ProtosSignalException(
                    ProtosCoreErrors.newError(activation));
        }

        byte[] password = null;
        byte[] salt = null;
        byte[] expected = null;

        try {
            password = normalizePassword(supplied.get(0));
            salt = captureSalt(supplied.get(1));
            expected = captureTag(supplied.get(2));

            return submit(
                    activation, password, salt, expected, errors);
        } catch (IllegalArgumentException invalid) {
            return failedKind(
                    activation, errors, "invalidArgument");
        } finally {
            wipe(password);
            wipe(salt);
            wipe(expected);
        }
    }

    /**
     * Nonblocking submission of independently captured inputs.
     *
     * <p>Every admitted operation has one owning Actor Future.
     * The host executor receives independent byte-array copies.
     * Its completion callback only enqueues Actor-domain work.
     */
    private static ProtosFutureValue submit(
            ProtosActivation activation,
            byte[] password,
            byte[] salt,
            byte[] expected,
            ProtosObjectValue errors) {

        ProtosActorExecutionDomain domain =
                activation.executionDomain();

        ProtosActor actor =
                domain.currentActorForRuntime().orElse(null);

        if (actor == null) {
            return failedKind(activation, errors, "unavailable");
        }

        ProtosProcessRuntime process =
                actor.processForRuntime().orElse(null);

        if (process == null) {
            return failedKind(activation, errors, "unavailable");
        }

        ProtosProcessExecutionHost host =
                process.executionHostForRuntime().orElse(null);

        if (host == null) {
            return failedKind(activation, errors, "unavailable");
        }

        ProtosFutureValue future = freshFuture(activation);

        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<ProtosProcessExecutionHost.PasswordKdfTicket>
                ticket = new AtomicReference<>();

        future.attachCancellationProducer(() -> {
            cancelled.set(true);
            future.cancelTerminal();

            ProtosProcessExecutionHost.PasswordKdfTicket active =
                    ticket.get();

            if (active != null) {
                active.cancel();
            }
        });

        domain.registerActorNonTaskFutureForRuntime(future);

        if (!future.isPending()) {
            return future;
        }

        ProtosProcessExecutionHost.PasswordKdfAdmission admitted;

        try {
            admitted = host.submitPasswordKdfForRuntime(
                    actor,
                    password,
                    salt,
                    expected,
                    result -> {
                        // Host callback: no guest allocation or execution.
                        // Ownership of result transfers to the queued
                        // Actor-domain completion.
                        try {
                            domain.enqueueTargetedFutureCompletionForRuntime(
                                    future,
                                    () -> publish(
                                            activation,
                                            future,
                                            result,
                                            errors),
                                    result::close);
                        } catch (IllegalStateException terminating) {
                            result.close();
                        }
                    });
        } catch (RuntimeException unavailable) {
            future.fail(error(errors, "unavailable"));
            return future;
        }

        if (!admitted.isAdmitted()) {
            String kind = switch (admitted.rejection()) {
                case CAPACITY -> "capacity";
                case UNAVAILABLE -> "unavailable";
            };
            future.fail(error(errors, kind));
            return future;
        }

        ProtosProcessExecutionHost.PasswordKdfTicket active =
                admitted.ticket();

        ticket.set(active);

        if (cancelled.get() || !future.isPending()) {
            active.cancel();
        }

        return future;
    }

    /**
     * Actor-domain-only full-result publication.
     *
     * <p>The result is committed in one Future transition.
     * There is no guest-visible partial record or partial tag.
     */
    private static boolean publish(
            ProtosActivation activation,
            ProtosFutureValue future,
            ProtosProcessExecutionHost.PasswordKdfResult result,
            ProtosObjectValue errors) {

        try (result) {
            if (!future.isPending()) {
                return false;
            }

            if (result.unavailable()) {
                return future.fail(error(errors, "unavailable"));
            }

            if (result.verification()) {
                return future.resolve(bool(result.matches()), activation);
            }

            byte[] salt = null;
            byte[] tag = null;

            try {
                salt = result.saltSnapshot();
                tag = result.tagSnapshot();

                // Both complete Bytes are created inside the Actor.
                ProtosBytesValue saltValue = bytes(activation, salt);
                ProtosBytesValue tagValue = bytes(activation, tag);

                ProtosObjectValue pair =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());

                pair.createLocalSlot("salt", saltValue);
                pair.createLocalSlot("tag", tagValue);

                return future.resolve(pair, activation);
            } catch (RuntimeException constructionFailure) {
                return future.fail(error(errors, "unavailable"));
            } finally {
                wipe(salt);
                wipe(tag);
            }
        }
    }

    private static ProtosBytesValue bytes(
            ProtosActivation activation, byte[] contents) {

        ProtosBytesValue value =
                new ProtosBytesValue(
                        activation.prelude()
                                .orElseThrow()
                                .bytesPrototypeForRuntime());

        for (byte octet : contents) {
            value.indexedAdd(
                    ProtosNumericValueSupport.octet(octet & 255));
        }

        return value;
    }

    static byte[] derive(byte[] normalizedPasswordUtf8, byte[] salt) {
        if (normalizedPasswordUtf8 == null
                || normalizedPasswordUtf8.length > MAX_PASSWORD_BYTES
                || salt == null
                || salt.length != SALT_BYTES) {
            throw new IllegalArgumentException("Invalid Argon2id input");
        }

        byte[] passwordCopy =
                Arrays.copyOf(normalizedPasswordUtf8, normalizedPasswordUtf8.length);
        byte[] saltCopy = Arrays.copyOf(salt, salt.length);
        byte[] tag = new byte[TAG_BYTES];
        boolean completed = false;

        try {
            Argon2Parameters parameters =
                    new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                            .withMemoryAsKB(MEMORY_KIB)
                            .withIterations(ITERATIONS)
                            .withParallelism(PARALLELISM)
                            .withSalt(saltCopy)
                            .build();

            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(parameters);
            generator.generateBytes(passwordCopy, tag);

            completed = true;
            return tag;
        } finally {
            Arrays.fill(passwordCopy, (byte) 0);
            Arrays.fill(saltCopy, (byte) 0);

            if (!completed) {
                Arrays.fill(tag, (byte) 0);
            }
        }
    }

    /**
     * Verify an exact supported v1 tag without an early-exit
     * comparison of secret tag contents.
     *
     * <p>The caller validates record syntax and profile before
     * invoking this expensive primitive.
     */
    static boolean verify(
            byte[] normalizedPasswordUtf8,
            byte[] salt,
            byte[] expectedTag) {

        if (expectedTag == null || expectedTag.length != TAG_BYTES) {
            throw new IllegalArgumentException("Invalid Argon2id tag");
        }

        byte[] actualTag = derive(normalizedPasswordUtf8, salt);

        try {
            return MessageDigest.isEqual(actualTag, expectedTag);
        } finally {
            Arrays.fill(actualTag, (byte) 0);
        }
    }
}

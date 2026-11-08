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
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.ProviderException;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Private HMAC-SHA256 backend of the Actor-local standard crypto module.
 *
 * <p>D244-C / I159. No public guest type, permission, global, shared Mac
 * or persistent cryptographic state is introduced.
 *
 * <p>Each operation snapshots canonical Bytes and uses a fresh JCA Mac.
 * Temporary owned arrays are wiped, without claiming provider-internal
 * or complete JVM secret erasure.
 */
public final class ProtosHmacSha256Facility {
    public static final ProtosModuleKey MODULE_KEY =
            ProtosModuleKey.module("std:crypto/hmac_sha256");

    public static final String BOOTSTRAP_SLOT = "_hmacSha256Facility";

    private static final int MAX_KEY_LENGTH = 4096;
    private static final int MAX_MESSAGE_LENGTH = 16777216;
    private static final int TAG_LENGTH = 32;

    private ProtosHmacSha256Facility() {}

    public static ProtosObjectValue createFacility() {
        return createFacility(() -> Mac.getInstance("HmacSHA256"));
    }

    /**
     * Package-private per-facility creation seam for failure and isolation tests.
     * Production resolves a fresh provider Mac inside each operation.
     */
    @FunctionalInterface
    interface MacFactory {
        Mac create() throws GeneralSecurityException;
    }

    static ProtosObjectValue createFacility(MacFactory factory) {
        ProtosObjectValue facility =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        operation(
                facility,
                "digest",
                2,
                (activation, supplied) -> digest(activation, supplied, factory));
        operation(
                facility,
                "verify",
                3,
                (activation, supplied) -> verify(activation, supplied, factory));

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
                                throw invalid(activation);
                            }
                            return body.execute(activation, supplied);
                        }));
    }

    private static Object digest(
            ProtosActivation activation, List<?> supplied, MacFactory factory) {
        ProtosBytesValue key =
                requireBytes(activation, supplied.get(0), MAX_KEY_LENGTH);
        ProtosBytesValue message =
                requireBytes(activation, supplied.get(1), MAX_MESSAGE_LENGTH);

        byte[] keyCopy = null;
        byte[] messageCopy = null;
        byte[] calculated = null;

        try {
            keyCopy = key.octetSnapshot();
            messageCopy = message.octetSnapshot();

            calculated = calculate(keyCopy, messageCopy, factory);

            if (calculated.length != TAG_LENGTH) {
                throw invalid(activation);
            }

            ProtosBytesValue result =
                    new ProtosBytesValue(
                            activation.prelude()
                                    .orElseThrow(() -> invalid(activation))
                                    .bytesPrototypeForRuntime());

            for (byte octet : calculated) {
                result.indexedAdd(
                        ProtosNumericValueSupport.octet(octet & 255));
            }

            return result;
        } catch (GeneralSecurityException | IllegalArgumentException | IllegalStateException | ProviderException failure) {
            throw invalid(activation);
        } finally {
            wipe(keyCopy);
            wipe(messageCopy);
            wipe(calculated);
        }
    }

    private static Object verify(
            ProtosActivation activation, List<?> supplied, MacFactory factory) {
        ProtosBytesValue key =
                requireBytes(activation, supplied.get(0), MAX_KEY_LENGTH);
        ProtosBytesValue message =
                requireBytes(activation, supplied.get(1), MAX_MESSAGE_LENGTH);
        ProtosBytesValue tag =
                requireBytes(activation, supplied.get(2), Integer.MAX_VALUE);

        if (tag.indexedSize() != TAG_LENGTH) {
            return ProtosBooleanValue.FALSE;
        }

        byte[] keyCopy = null;
        byte[] messageCopy = null;
        byte[] tagCopy = null;
        byte[] calculated = null;

        try {
            keyCopy = key.octetSnapshot();
            messageCopy = message.octetSnapshot();
            tagCopy = tag.octetSnapshot();

            calculated = calculate(keyCopy, messageCopy, factory);

            if (calculated.length != TAG_LENGTH) {
                throw invalid(activation);
            }

            return ProtosBooleanValue.of(
                    MessageDigest.isEqual(calculated, tagCopy));
        } catch (GeneralSecurityException | IllegalArgumentException | IllegalStateException | ProviderException failure) {
            throw invalid(activation);
        } finally {
            wipe(keyCopy);
            wipe(messageCopy);
            wipe(tagCopy);
            wipe(calculated);
        }
    }

    private static ProtosBytesValue requireBytes(
            ProtosActivation activation,
            Object value,
            int maximumLength) {
        if (!(value instanceof ProtosBytesValue bytes)
                || bytes.indexedSize() > maximumLength) {
            throw invalid(activation);
        }

        return bytes;
    }

    /**
     * JCA backend boundary. Creates a new Mac for each operation.
     * Empty HMAC keys are represented by one zero octet for JCA,
     * which is equivalent under the RFC HMAC padding rules.
     */
    @TruffleBoundary
    private static byte[] calculate(
            byte[] key, byte[] message, MacFactory factory)
            throws GeneralSecurityException {
        byte[] emptyReplacement = null;

        try {
            byte[] effectiveKey = key;

            if (key.length == 0) {
                emptyReplacement = new byte[] {0};
                effectiveKey = emptyReplacement;
            }

            Mac mac = factory.create();
            mac.init(new SecretKeySpec(effectiveKey, "HmacSHA256"));

            return mac.doFinal(message);
        } catch (RuntimeException providerFailure) {
            // A provider implementation may signal unchecked failures.
            // They are never verification=false, diagnostic guest data,
            // or an opportunity for an insecure fallback.
            throw new GeneralSecurityException(
                    "HMAC-SHA256 provider operation failed");
        } finally {
            wipe(emptyReplacement);
        }
    }

    private static void wipe(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    private static ProtosSignalException invalid(
            ProtosActivation activation) {
        return new ProtosSignalException(
                ProtosCoreErrors.newError(activation));
    }
}

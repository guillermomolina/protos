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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * I159 / D244-C: integration through the actual Actor-local Protos
 * std:crypto/hmac_sha256 module, never through a direct JCA facade.
 *
 * <p>Large inputs are constructed directly as canonical runtime Bytes.
 * Source-level loops are deliberately excluded from the 16 MiB cases.
 */
final class ProtosHmacSha256IntegrationTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path LIBRARY = Path.of("protos", "lib");

    private static final int MAX_KEY = 4096;
    private static final int MAX_MESSAGE = 16777216;

    private static final String IMPORT =
            "HMAC: import(\"std:crypto/hmac_sha256\")\n";

    private static ProtosPrelude prelude;

    @BeforeAll
    static void bootstrap() throws IOException {
        prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosStandardLibraryModuleResolver(LIBRARY));
    }

    @Test
    void importsHaveActorLocalCanonicalIdentityAndExactPublicSurface() {
        ProtosActivation firstActor = prelude.newModuleActivation();
        ProtosActivation secondActor = prelude.newModuleActivation();

        Object first = completed(
                firstActor, "import(\"std:crypto/hmac_sha256\")");
        Object same = completed(
                firstActor, "import(\"std:crypto/hmac_sha256\")");
        Object other = completed(
                secondActor, "import(\"std:crypto/hmac_sha256\")");

        assertSame(first, same);
        assertNotSame(first, other);

        ProtosArrayValue names =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(firstActor, IMPORT + "HMAC.slotNames()"));

        List<String> slots = new ArrayList<>();
        for (Object value : names.indexedSnapshot()) {
            slots.add(assertInstanceOf(ProtosStringValue.class, value).value());
        }

        assertEquals(2, slots.size());
        assertEquals(Set.of("digest", "verify"), Set.copyOf(slots));

        assertTrue(
                firstActor.context()
                        .readLocalSlot(
                                ProtosHmacSha256Facility.BOOTSTRAP_SLOT)
                        .isEmpty());

        assertTrue(
                secondActor.context()
                        .readLocalSlot(
                                ProtosHmacSha256Facility.BOOTSTRAP_SLOT)
                        .isEmpty());
    }

    @Test
    void rfc4231CasesOneThroughFourSixAndSevenUsePublicProtosApi() {
        assertVector(
                repeated(0x0b, 20),
                ascii("Hi There"),
                "b0344c61d8db38535ca8afceaf0bf12b8"
                        + "81dc200c9833da726e9376c2e32cff7");

        assertVector(
                ascii("Jefe"),
                ascii("what do ya want for nothing?"),
                "5bdcc146bf60754e6a042426089575c7"
                        + "5a003f089d2739839dec58b964ec3843");

        assertVector(
                repeated(0xaa, 20),
                repeated(0xdd, 50),
                "773ea91e36800e46854db8ebd09181a7"
                        + "2959098b3ef8c122d9635514ced565fe");

        byte[] sequentialKey = new byte[25];
        for (int index = 0; index < sequentialKey.length; index++) {
            sequentialKey[index] = (byte) (index + 1);
        }

        assertVector(
                sequentialKey,
                repeated(0xcd, 50),
                "82558a389a443c0ea4cc819899f2083a"
                        + "85f0faa3e578f8077a2e3ff46729665b");

        assertVector(
                repeated(0xaa, 131),
                ascii("Test Using Larger Than Block-Size Key - Hash Key First"),
                "60e431591ee0b67f0d8a26aacbf5b77f"
                        + "8e0bc6213728c5140546040f0ee37f54");

        assertVector(
                repeated(0xaa, 131),
                ascii(
                        "This is a test using a larger than block-size key "
                                + "and a larger than block-size data. "
                                + "The key needs to be hashed before being "
                                + "used by the HMAC algorithm."),
                "9b09ffa71b942fcb27635fbcd5b0e944"
                        + "bfdc63644f0713938a7f51535c3a35e2");
    }

    @Test
    void emptyKeyAndMessageFollowRfcHmacNormalization() {
        assertVector(
                new byte[0],
                new byte[0],
                "b613679a0814d9ec772f95d778c35fc"
                        + "5ff1697c493715653c6c712144292c5ad");
    }

    @Test
    void wrongFirstMiddleLastAndWrongSizedTagsAlwaysAnswerFalse() {
        byte[] key = ascii("Jefe");
        byte[] message = ascii("what do ya want for nothing?");

        byte[] correct = HexFormat.of().parseHex(
                "5bdcc146bf60754e6a042426089575c7"
                        + "5a003f089d2739839dec58b964ec3843");

        for (int position : new int[] {0, 16, 31}) {
            byte[] altered = correct.clone();
            altered[position] ^= 1;
            assertVerification(key, message, altered, false);
        }

        for (int size : new int[] {0, 1, 16, 31, 33}) {
            byte[] candidate = new byte[size];
            assertVerification(key, message, candidate, false);
        }

        assertVerification(key, message, correct, true);
    }

    @Test
    void sourceOperandsAndReturnedBytesHaveIndependentIdentities() {
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosBytesValue key = bytes(ascii("Jefe"));
        ProtosBytesValue message =
                bytes(ascii("what do ya want for nothing?"));

        byte[] originalKey = key.octetSnapshot();
        byte[] originalMessage = message.octetSnapshot();

        activation.context().createLocalSlot("key", key);
        activation.context().createLocalSlot("message", message);

        ProtosBytesValue first =
                assertInstanceOf(
                        ProtosBytesValue.class,
                        completed(activation, IMPORT + "HMAC.digest(key, message)"));

        ProtosBytesValue second =
                assertInstanceOf(
                        ProtosBytesValue.class,
                        completed(activation, IMPORT + "HMAC.digest(key, message)"));

        assertNotSame(first, second);
        assertTrue(first.isOpen());
        assertTrue(second.isOpen());

        assertSame(
                prelude.bytesPrototypeForRuntime(),
                first.parent().orElseThrow());

        byte[] expected = first.octetSnapshot();

        assertArrayEquals(expected, second.octetSnapshot());
        assertArrayEquals(originalKey, key.octetSnapshot());
        assertArrayEquals(originalMessage, message.octetSnapshot());

        key.indexedPut(0, ProtosNumericValueSupport.octet(0));
        message.indexedPut(0, ProtosNumericValueSupport.octet(0));

        assertArrayEquals(expected, first.octetSnapshot());
        assertArrayEquals(expected, second.octetSnapshot());

        ProtosBytesValue changed =
                assertInstanceOf(
                        ProtosBytesValue.class,
                        completed(activation, IMPORT + "HMAC.digest(key, message)"));

        assertFalse(Arrays.equals(expected, changed.octetSnapshot()));

        first.indexedPut(0, ProtosNumericValueSupport.octet(0));
        first.indexedAdd(ProtosNumericValueSupport.octet(255));

        assertEquals(33, first.indexedSize());
        assertEquals(32, second.indexedSize());
        assertEquals(32, changed.indexedSize());

        assertArrayEquals(expected, second.octetSnapshot());
        assertArrayEquals(originalKey, bytes(originalKey).octetSnapshot());
        assertArrayEquals(
                originalMessage, bytes(originalMessage).octetSnapshot());
    }

    @Test
    void invalidDomainsDelegatedFakeBytesAndAritySignalErrors() {
        ProtosActivation activation = prelude.newModuleActivation();

        activation.context().createLocalSlot("key", bytes(new byte[] {1}));
        activation.context().createLocalSlot("message", bytes(new byte[] {2}));
        activation.context().createLocalSlot("tag", bytes(new byte[32]));

        // This object delegates to Bytes but is not an actual Bytes value.
        ProtosObjectValue fake =
                new ProtosObjectValue(prelude.bytesPrototypeForRuntime());
        activation.context().createLocalSlot("fake", fake);

        Object result = completed(
                activation,
                IMPORT
                        + "Assertions: import(\"std:test/assertions\")\n"
                        + "Assertions.signals(Error, () => { HMAC.digest(Array(), message) })\n"
                        + "Assertions.signals(Error, () => { HMAC.digest(key, \"text\") })\n"
                        + "Assertions.signals(Error, () => { HMAC.digest(fake, message) })\n"
                        + "Assertions.signals(Error, () => { HMAC.digest(key, fake) })\n"
                        + "Assertions.signals(Error, () => { HMAC.verify(key, message, fake) })\n"
                        + "Assertions.signals(Error, () => { HMAC.verify(key, message, null) })\n"
                        + "Assertions.signals(Error, () => { HMAC.digest(key) })\n"
                        + "Assertions.require(HMAC.digest(key, message, tag).size() == 32)\n"
                        + "Assertions.signals(Error, () => { HMAC.verify(key, message) })\n"
                        + "true\n");

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void maximum4096ByteKeyIsAdmittedAnd4097IsRejected() {
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosBytesValue key = filledBytes(MAX_KEY, 0xaa);
        ProtosBytesValue message = bytes(ascii("boundary"));

        activation.context().createLocalSlot("key", key);
        activation.context().createLocalSlot("message", message);

        ProtosBytesValue tag =
                assertInstanceOf(
                        ProtosBytesValue.class,
                        completed(activation, IMPORT + "HMAC.digest(key, message)"));

        assertEquals(32, tag.indexedSize());

        key.indexedAdd(ProtosNumericValueSupport.octet(0));

        Object result = completed(
                activation,
                IMPORT
                        + "Assertions: import(\"std:test/assertions\")\n"
                        + "Assertions.signals(Error, () => { HMAC.digest(key, message) })\n"
                        + "Assertions.signals(Error, () => { HMAC.verify(key, message, tag) })\n"
                        + "true\n",
                "tag",
                tag);

        assertSame(ProtosBooleanValue.TRUE, result);
        assertEquals(MAX_KEY + 1, key.indexedSize());
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void messageLimitsAreExactAtSixteenMiBAndOneOctetMore() {
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosBytesValue key = bytes(new byte[] {1, 2, 3, 4});
        ProtosBytesValue message = filledBytes(MAX_MESSAGE, 0x5a);

        activation.context().createLocalSlot("key", key);
        activation.context().createLocalSlot("message", message);

        ProtosBytesValue tag =
                assertInstanceOf(
                        ProtosBytesValue.class,
                        completed(activation, IMPORT + "HMAC.digest(key, message)"));

        assertEquals(32, tag.indexedSize());
        assertTrue(tag.isOpen());

        message.indexedAdd(ProtosNumericValueSupport.octet(0x5a));

        Object result = completed(
                activation,
                IMPORT
                        + "Assertions: import(\"std:test/assertions\")\n"
                        + "Assertions.signals(Error, () => { HMAC.digest(key, message) })\n"
                        + "Assertions.signals(Error, () => { HMAC.verify(key, message, tag) })\n"
                        + "true\n",
                "tag",
                tag);

        assertSame(ProtosBooleanValue.TRUE, result);
        assertEquals(MAX_MESSAGE + 1, message.indexedSize());
        assertEquals(32, tag.indexedSize());
    }

    private static void assertVector(
            byte[] keyBytes, byte[] messageBytes, String expectedHex) {
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosBytesValue key = bytes(keyBytes);
        ProtosBytesValue message = bytes(messageBytes);

        activation.context().createLocalSlot("key", key);
        activation.context().createLocalSlot("message", message);

        ProtosArrayValue values =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        completed(
                                activation,
                                IMPORT
                                        + "tag: HMAC.digest(key, message)\n"
                                        + "[tag, HMAC.verify(key, message, tag)]\n"));

        assertEquals(2, values.indexedSize());

        ProtosBytesValue result =
                assertInstanceOf(
                        ProtosBytesValue.class, values.indexedAt(0));

        assertSame(ProtosBooleanValue.TRUE, values.indexedAt(1));
        assertTrue(result.isOpen());
        assertEquals(32, result.indexedSize());
        assertSame(
                prelude.bytesPrototypeForRuntime(),
                result.parent().orElseThrow());

        assertArrayEquals(
                HexFormat.of().parseHex(expectedHex),
                result.octetSnapshot());

        assertArrayEquals(keyBytes, key.octetSnapshot());
        assertArrayEquals(messageBytes, message.octetSnapshot());
    }

    private static void assertVerification(
            byte[] key, byte[] message, byte[] tag, boolean expected) {
        ProtosActivation activation = prelude.newModuleActivation();

        activation.context().createLocalSlot("key", bytes(key));
        activation.context().createLocalSlot("message", bytes(message));
        activation.context().createLocalSlot("tag", bytes(tag));

        Object result =
                completed(
                        activation,
                        IMPORT + "HMAC.verify(key, message, tag)");

        assertSame(ProtosBooleanValue.of(expected), result);
    }

    private static Object completed(
            ProtosActivation activation, String source) {
        // Independent source snippets must not redeclare their local
        // HMAC binding in the same lexical context. Preserve the exact
        // Actor module cache and execution domain across snippets.
        ProtosObjectValue snippetContext = prelude.newExecutionContext();
        activation.context()
                .localSlotsSnapshot()
                .forEach(snippetContext::createLocalSlot);

        ProtosActivation snippetActivation =
                prelude.newModuleActivation(
                        activation.actorModuleState(),
                        activation.currentModuleKey().orElse(null),
                        snippetContext,
                        activation.executionDomain());

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(source, snippetActivation);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () ->
                        "Protos snippet failed: "
                                + outcome.error()
                                + "; trace="
                                + outcome.failureDiagnosticTrace());

        return outcome.value();
    }

    private static Object completed(
            ProtosActivation activation,
            String source,
            String slot,
            Object value) {
        activation.context().createLocalSlot(slot, value);
        return completed(activation, source);
    }

    private static ProtosBytesValue bytes(byte[] octets) {
        ProtosBytesValue result =
                new ProtosBytesValue(prelude.bytesPrototypeForRuntime());

        for (byte octet : octets) {
            result.indexedAdd(
                    ProtosNumericValueSupport.octet(octet & 255));
        }

        return result;
    }

    private static ProtosBytesValue filledBytes(int length, int octet) {
        ProtosBytesValue result =
                new ProtosBytesValue(prelude.bytesPrototypeForRuntime());

        Object value = ProtosNumericValueSupport.octet(octet);

        for (int index = 0; index < length; index++) {
            result.indexedAdd(value);
        }

        return result;
    }

    private static byte[] repeated(int value, int length) {
        byte[] octets = new byte[length];
        Arrays.fill(octets, (byte) value);
        return octets;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}

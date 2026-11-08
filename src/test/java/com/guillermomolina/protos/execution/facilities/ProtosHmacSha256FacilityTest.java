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
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * I159: private HMAC-SHA256 facility invariants, independent of any
 * global JCA provider mutation or provider ordering.
 */
final class ProtosHmacSha256FacilityTest {
    private static ProtosPrelude prelude;

    @BeforeAll
    static void bootstrap() throws IOException {
        prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                Path.of("protos", "lib", "core"),
                                new ProtosStandardLibraryModuleResolver(
                                        Path.of("protos", "lib")));
    }

    @Test
    void creationIsLazyAndEachOperationObtainsANewMac() {
        AtomicInteger creations = new AtomicInteger();

        ProtosObjectValue facility =
                ProtosHmacSha256Facility.createFacility(
                        () -> {
                            creations.incrementAndGet();
                            return Mac.getInstance("HmacSHA256");
                        });

        assertTrue(facility.isFrozen());
        assertEquals(0, creations.get());

        ProtosBytesValue key = bytes("Jefe".getBytes(StandardCharsets.US_ASCII));
        ProtosBytesValue message =
                bytes("what do ya want for nothing?"
                        .getBytes(StandardCharsets.US_ASCII));

        Object first = evaluate(
                facility,
                key,
                message,
                "bridge.digest(key, message)");

        assertEquals(1, creations.get());

        Object second = evaluate(
                facility,
                key,
                message,
                "bridge.digest(key, message)");

        assertEquals(2, creations.get());
        assertNotSame(first, second);

        byte[] expected = HexFormat.of().parseHex(
                "5bdcc146bf60754e6a042426089575c7"
                + "5a003f089d2739839dec58b964ec3843");

        ProtosBytesValue firstTag =
                assertInstanceOf(ProtosBytesValue.class, first);
        ProtosBytesValue secondTag =
                assertInstanceOf(ProtosBytesValue.class, second);

        assertArrayEquals(expected, firstTag.octetSnapshot());
        assertArrayEquals(expected, secondTag.octetSnapshot());
        assertTrue(firstTag.isOpen());
        assertTrue(secondTag.isOpen());

        assertSame(
                ProtosBooleanValue.TRUE,
                evaluate(
                        facility,
                        key,
                        message,
                        "bridge.verify(key, message, tag)",
                        secondTag));

        assertEquals(3, creations.get());
    }

    @Test
    void emptyKeyIsEquivalentToRfcZeroPadding() {
        ProtosObjectValue facility =
                ProtosHmacSha256Facility.createFacility();

        ProtosBytesValue empty = bytes(new byte[0]);

        ProtosBytesValue result =
                assertInstanceOf(
                        ProtosBytesValue.class,
                        evaluate(
                                facility,
                                empty,
                                empty,
                                "bridge.digest(key, message)"));

        assertArrayEquals(
                HexFormat.of().parseHex(
                        "b613679a0814d9ec772f95d778c35fc"
                        + "5ff1697c493715653c6c712144292c5ad"),
                result.octetSnapshot());
    }

    @Test
    void providerFailureSignalsFreshOrdinaryErrorWithoutFallback() {
        AtomicInteger attempts = new AtomicInteger();

        ProtosObjectValue facility =
                ProtosHmacSha256Facility.createFacility(
                        () -> {
                            attempts.incrementAndGet();
                            throw new NoSuchAlgorithmException(
                                    "provider-secret-marker");
                        });

        ProtosBytesValue key = bytes(new byte[] {1, 2, 3});
        ProtosBytesValue message = bytes(new byte[] {4, 5, 6});

        ProtosActivation activation = prelude.newModuleActivation();
        activation.context().createLocalSlot("bridge", facility);
        activation.context().createLocalSlot("key", key);
        activation.context().createLocalSlot("message", message);

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        """
                        first: null
                        second: null
                        Error.handle(
                            () => {
                                bridge.digest(key, message)
                            },
                            (error) => {
                                first = error
                                error
                            }
                        )
                        Error.handle(
                            () => {
                                bridge.digest(key, message)
                            },
                            (error) => {
                                second = error
                                error
                            }
                        )
                        [first, second]
                        """,
                        activation);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> String.valueOf(outcome.error()));

        com.guillermomolina.protos.runtime.ProtosArrayValue errors =
                assertInstanceOf(
                        com.guillermomolina.protos.runtime.ProtosArrayValue.class,
                        outcome.value());

        ProtosObjectValue first =
                assertInstanceOf(ProtosObjectValue.class, errors.indexedAt(0));
        ProtosObjectValue second =
                assertInstanceOf(ProtosObjectValue.class, errors.indexedAt(1));

        assertNotSame(first, second);
        assertSame(prelude.errorPrototype(), first.parent().orElseThrow());
        assertSame(prelude.errorPrototype(), second.parent().orElseThrow());

        assertFalse(first.toString().contains("provider-secret-marker"));
        assertFalse(second.toString().contains("provider-secret-marker"));
        assertEquals(2, attempts.get());
    }

    @Test
    void uncheckedProviderFailureIsAnOrdinaryGuestError() {
        AtomicInteger attempts = new AtomicInteger();

        ProtosObjectValue facility =
                ProtosHmacSha256Facility.createFacility(
                        () -> {
                            attempts.incrementAndGet();
                            throw new ProviderException("private-provider-detail");
                        });

        ProtosActivation activation = prelude.newModuleActivation();
        activation.context().createLocalSlot("bridge", facility);
        activation.context().createLocalSlot("key", bytes(new byte[] {1}));
        activation.context().createLocalSlot("message", bytes(new byte[] {2}));
        activation.context().createLocalSlot("tag", bytes(new byte[32]));

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        """
                        Assertions: import("std:test/assertions")
                        Assertions.signals(Error, () => {
                            bridge.digest(key, message)
                        })
                        Assertions.signals(Error, () => {
                            bridge.verify(key, message, tag)
                        })
                        true
                        """,
                        activation);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> String.valueOf(outcome.error()));
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
        assertEquals(2, attempts.get());
    }

    @Test
    void wrongReceiverAndAritySignalOrdinaryErrors() {
        ProtosObjectValue facility =
                ProtosHmacSha256Facility.createFacility();

        ProtosActivation activation = prelude.newModuleActivation();
        activation.context().createLocalSlot("bridge", facility);
        activation.context().createLocalSlot("key", bytes(new byte[] {1}));
        activation.context().createLocalSlot("message", bytes(new byte[] {2}));
        activation.context().createLocalSlot("tag", bytes(new byte[32]));

        ProtosObjectValue unrelated =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        unrelated.createLocalSlot(
                "digest",
                facility.readLocalSlot("digest").orElseThrow());
        activation.context().createLocalSlot("unrelated", unrelated);

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        """
                        Assertions: import("std:test/assertions")
                        // An extracted method keeps the original receiver.
                        stolen: bridge.digest
                        Assertions.require(stolen(key, message).size() == 32)

                        // Installing the raw Closure on another receiver
                        // does not grant the facility's authority.
                        Assertions.signals(Error, () => {
                            unrelated.digest(key, message)
                        })

                        // Native facility methods require exact arity.
                        Assertions.signals(Error, () => {
                            bridge.digest(key)
                        })
                        Assertions.signals(Error, () => {
                            bridge.digest(key, message, tag)
                        })
                        Assertions.signals(Error, () => {
                            bridge.verify(key, message)
                        })
                        Assertions.signals(Error, () => {
                            bridge.verify(key, message, tag, key)
                        })
                        true
                        """,
                        activation);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> String.valueOf(outcome.error()));

        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    private static Object evaluate(
            ProtosObjectValue facility,
            ProtosBytesValue key,
            ProtosBytesValue message,
            String expression) {
        return evaluate(facility, key, message, expression, null);
    }

    private static Object evaluate(
            ProtosObjectValue facility,
            ProtosBytesValue key,
            ProtosBytesValue message,
            String expression,
            ProtosBytesValue tag) {
        ProtosActivation activation = prelude.newModuleActivation();
        activation.context().createLocalSlot("bridge", facility);
        activation.context().createLocalSlot("key", key);
        activation.context().createLocalSlot("message", message);

        if (tag != null) {
            activation.context().createLocalSlot("tag", tag);
        }

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(expression, activation);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> String.valueOf(outcome.error()));

        return outcome.value();
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
}

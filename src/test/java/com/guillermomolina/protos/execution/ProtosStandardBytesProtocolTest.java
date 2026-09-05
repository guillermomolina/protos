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

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.*;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosStandardBytesProtocolTest {
    @Test
    void factoryCreatesFreshOpenEmptyBytesAndUsesActualInvocationReceiver()
            throws IOException {
        Fixture f = fixture();
        ProtosBytesValue a = f.create();
        ProtosBytesValue b = f.create();

        assertNotSame(a, b);
        assertTrue(a.isOpen());
        assertSame(f.factory, a.parent().orElseThrow());
        assertEquals(BigInteger.ZERO, a.indexedSize());

        ProtosObjectValue derived = new ProtosObjectValue(f.factory);
        ProtosBytesValue child =
                (ProtosBytesValue) ProtosInvocation.invoke(derived, List.of(), f.activation);
        assertSame(derived, child.parent().orElseThrow());

        assertError(f, () -> ProtosInvocation.invoke(f.factory, List.of(integer(1)), f.activation));
    }

    @Test
    void sizeAtAndBracketReadAreDenseZeroBasedAndAcceptIntegerFamilies()
            throws IOException {
        Fixture f = fixture();
        ProtosBytesValue bytes = f.create();
        ProtosIntegerValue zero = integer(0);
        ProtosFixedIntegerValue high =
                new ProtosFixedIntegerValue(
                        ProtosFixedIntegerValue.Family.UINT8, BigInteger.valueOf(255));
        f.add(bytes, zero);
        f.add(bytes, high);

        assertEquals(BigInteger.valueOf(2), intValue(f.send(bytes, "size")));
        assertSame(zero, f.send(bytes, "at", integer(0)));
        assertSame(high, f.send(bytes, "at", integer(1)));

        f.activation.context().createLocalSlot("bytesForBracket", bytes);
        assertSame(
                high,
                new ProtosSourceCompiler()
                        .compile("bytesForBracket[1]")
                        .call(f.activation));

        assertError(f, () -> f.send(bytes, "at", integer(-1)));
        assertError(f, () -> f.send(bytes, "at", integer(2)));
        assertError(f, () -> f.send(bytes, "at", new ProtosFloatValue(0.0)));
        assertError(f, () -> f.send(bytes, "at", new ProtosStringValue("0")));
        assertError(f, () -> f.send(f.create(), "at", integer(0)));
    }

    @Test
    void atPutReplacesOnlyExistingOctetAndReturnsExactSuppliedObject()
            throws IOException {
        Fixture f = fixture();
        ProtosBytesValue bytes = f.create();
        f.add(bytes, integer(1));
        f.add(bytes, integer(2));

        ProtosFixedIntegerValue replacement =
                new ProtosFixedIntegerValue(
                        ProtosFixedIntegerValue.Family.UINT16, BigInteger.valueOf(255));
        assertSame(replacement, f.send(bytes, "atPut", integer(0), replacement));
        assertSame(replacement, f.send(bytes, "at", integer(0)));
        assertEquals(BigInteger.valueOf(2), bytes.indexedSize());

        f.activation.context().createLocalSlot("bytesForPut", bytes);
        Object assignment =
                new ProtosSourceCompiler()
                        .compile("bytesForPut[1] = 7")
                        .call(f.activation);
        assertEquals(BigInteger.valueOf(7), intValue(assignment));
        assertEquals(BigInteger.valueOf(7), intValue(f.send(bytes, "at", integer(1))));

        assertError(f, () -> f.send(bytes, "atPut", integer(2), integer(9)));
        assertError(f, () -> f.send(bytes, "atPut", integer(0), integer(-1)));
        assertError(f, () -> f.send(bytes, "atPut", integer(0), integer(256)));
        assertError(f, () -> f.send(bytes, "atPut", integer(0), new ProtosFloatValue(7.0)));
        assertError(f, () -> f.send(bytes, "atPut", integer(0), new ProtosStringValue("7")));
        assertEquals(BigInteger.valueOf(2), bytes.indexedSize());
    }

    @Test
    void addRemoveAtCoverOctetExtremesAndFailWithoutPartialMutation()
            throws IOException {
        Fixture f = fixture();
        ProtosBytesValue bytes = f.create();
        ProtosIntegerValue low = integer(0);
        ProtosIntegerValue mid = integer(127);
        ProtosFixedIntegerValue high =
                new ProtosFixedIntegerValue(
                        ProtosFixedIntegerValue.Family.UINT8, BigInteger.valueOf(255));

        assertSame(low, f.add(bytes, low));
        assertSame(mid, f.add(bytes, mid));
        assertSame(high, f.add(bytes, high));
        assertSame(mid, f.send(bytes, "removeAt", integer(1)));
        assertEquals(BigInteger.valueOf(2), bytes.indexedSize());
        assertSame(low, f.send(bytes, "at", integer(0)));
        assertSame(high, f.send(bytes, "at", integer(1)));

        BigInteger before = bytes.indexedSize();
        assertError(f, () -> f.send(bytes, "add", integer(-1)));
        assertError(f, () -> f.send(bytes, "add", integer(256)));
        assertError(f, () -> f.send(bytes, "add", new ProtosFloatValue(1.0)));
        assertError(f, () -> f.send(bytes, "removeAt", integer(-1)));
        assertError(f, () -> f.send(bytes, "removeAt", integer(2)));
        assertEquals(before, bytes.indexedSize());
    }

    @Test
    void stateRulesAllowClosedReplacementButRequireOpenForResizeAndRejectFrozenMutation()
            throws IOException {
        Fixture f = fixture();
        ProtosBytesValue closed = f.create();
        f.add(closed, integer(1));
        closed.close();
        ProtosIntegerValue two = integer(2);
        assertSame(two, f.send(closed, "atPut", integer(0), two));
        assertError(f, () -> f.send(closed, "add", integer(3)));
        assertError(f, () -> f.send(closed, "removeAt", integer(0)));
        assertEquals(BigInteger.ONE, intValue(f.send(closed, "size")));
        assertSame(two, f.send(closed, "at", integer(0)));

        ProtosBytesValue frozen = f.create();
        ProtosIntegerValue four = integer(4);
        f.add(frozen, four);
        frozen.freeze();
        assertError(f, () -> f.send(frozen, "atPut", integer(0), integer(5)));
        assertError(f, () -> f.send(frozen, "add", integer(5)));
        assertError(f, () -> f.send(frozen, "removeAt", integer(0)));
        assertSame(four, f.send(frozen, "at", integer(0)));
        assertEquals(BigInteger.ONE, intValue(f.send(frozen, "size")));
    }

    @Test
    void eachUsesOrdinaryPolymorphicCallabilityAndAscendingSnapshot()
            throws IOException {
        Fixture f = fixture();
        ProtosBytesValue bytes = f.create();
        ProtosIntegerValue one = integer(1);
        ProtosIntegerValue two = integer(2);
        ProtosIntegerValue three = integer(3);
        f.add(bytes, one);
        f.add(bytes, two);
        f.add(bytes, three);

        List<Object> seen = new ArrayList<>();
        ProtosObjectValue callable = new ProtosObjectValue(ProtosObjectValue.rootObject());
        callable.createLocalSlot(
                "call",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            seen.add(supplied.get(0));
                            if (seen.size() == 1) {
                                f.send(bytes, "atPut", integer(1), integer(99));
                                f.send(bytes, "removeAt", integer(2));
                                f.send(bytes, "add", integer(4));
                            }
                            return ProtosNullValue.INSTANCE;
                        }));

        assertSame(bytes, f.send(bytes, "each", callable));
        assertEquals(List.of(one, two, three), seen);
        assertEquals(BigInteger.valueOf(99), intValue(f.send(bytes, "at", integer(1))));
        assertEquals(BigInteger.valueOf(4), intValue(f.send(bytes, "at", integer(2))));
        assertError(f, () -> f.send(bytes, "each", integer(1)));
    }

    @Test
    void delegationAndCopiedMethodsDoNotConferBytesMembership() throws IOException {
        Fixture f = fixture();
        ProtosBytesValue bytes = f.create();
        f.add(bytes, integer(9));

        ProtosObjectValue delegated = new ProtosObjectValue(bytes);
        assertError(f, () -> f.send(delegated, "size"));
        assertError(f, () -> f.send(delegated, "at", integer(0)));

        ProtosObjectValue copied = new ProtosObjectValue(ProtosObjectValue.rootObject());
        copied.createLocalSlot("size", f.factory.readLocalSlot("size").orElseThrow());
        copied.createLocalSlot("add", f.factory.readLocalSlot("add").orElseThrow());
        assertError(f, () -> f.send(copied, "size"));
        assertError(f, () -> f.send(copied, "add", integer(1)));
    }

    @Test
    void equalContentsStillUseOrdinaryObjectEqualityIdentityAndHash() throws IOException {
        Fixture f = fixture();
        ProtosBytesValue a = f.create();
        ProtosBytesValue b = f.create();
        for (int v : new int[] {0, 127, 255}) {
            f.add(a, integer(v));
            f.add(b, integer(v));
        }

        assertSame(ProtosBooleanValue.TRUE, f.send(a, "==", a));
        assertSame(ProtosBooleanValue.FALSE, f.send(a, "==", b));
        assertTrue(ProtosIdentity.identical(a, a));
        assertFalse(ProtosIdentity.identical(a, b));

        BigInteger identityHash = ProtosIdentity.identityHash(a);
        assertEquals(identityHash, ProtosIdentity.identityHash(a));
        assertEquals(identityHash, intValue(f.send(a, "identityHash")));
        assertEquals(intValue(f.send(a, "hash")), intValue(f.send(a, "hash")));
    }

    @Test
    void frozenAtPutChecksStateBeforeBadIndexAndValueAndLeavesContentsUntouched()
            throws IOException {
        Fixture f = fixture();
        ProtosBytesValue bytes = f.create();
        ProtosIntegerValue original = integer(10);
        f.add(bytes, original);
        bytes.freeze();

        assertError(
                f,
                () ->
                        f.send(
                                bytes,
                                "atPut",
                                new ProtosStringValue("bad-index"),
                                new ProtosStringValue("bad-value")));
        assertSame(original, f.send(bytes, "at", integer(0)));
    }

    private static ProtosIntegerValue integer(long value) {
        return new ProtosIntegerValue(BigInteger.valueOf(value));
    }

    private static BigInteger intValue(Object value) {
        return ((ProtosIntegerValue) value).value();
    }

    private static Fixture fixture() throws IOException {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue factory = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStandardBytesProtocol.install(factory);
        return new Fixture(prelude, activation, factory);
    }

    private static void assertError(
            Fixture f, org.junit.jupiter.api.function.Executable executable) {
        ProtosSignalException signal = assertThrows(ProtosSignalException.class, executable);
        assertSame(f.prelude.errorPrototype(), signal.error().parent().orElseThrow());
    }

    private record Fixture(
            ProtosPrelude prelude, ProtosActivation activation, ProtosObjectValue factory) {
        ProtosBytesValue create() {
            return (ProtosBytesValue) ProtosInvocation.invoke(factory, List.of(), activation);
        }

        Object add(ProtosBytesValue bytes, Object value) {
            return send(bytes, "add", value);
        }

        Object send(Object receiver, String selector, Object... arguments) {
            return ProtosInvocation.invokeMessage(
                    receiver, selector, List.of(arguments), activation);
        }
    }
}

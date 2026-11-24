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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosStandardBytesProtocolTest {
    // Deliberately Java-side: the construction-only Bytes prototype is omitted
    // from public prelude bindings. Its exact call/receiver contract remains a
    // runtime/bootstrap boundary.
    @Test
    void hiddenFactoryContractAndRepresentedConstructionRemainJavaSide()
            throws IOException {
        Fixture fixture = fixture();

        ProtosBytesValue a = fixture.create();
        ProtosBytesValue b = fixture.create();
        assertNotSame(a, b);
        assertTrue(a.isOpen());
        assertSame(fixture.factory, a.parent().orElseThrow());
        assertEquals(0, a.indexedSize());

        ProtosObjectValue derived = new ProtosObjectValue(fixture.factory);
        ProtosBytesValue child =
                assertInstanceOf(
                        ProtosBytesValue.class,
                        ProtosInvocation.invoke(derived, List.of(), fixture.activation));
        assertTrue(child.isOpen());
        assertSame(derived, child.parent().orElseThrow());

        assertDirectError(
                fixture,
                () ->
                        ProtosInvocation.invoke(
                                fixture.factory, List.of(integer(1)), fixture.activation));
    }

    // Source conformance owns the observable invalid-operation behavior. Java
    // recovery is retained only to inspect the SAME represented Bytes afterward
    // and prove that no partial mutation occurred.
    @Test
    void invalidOpenMutationFailuresLeaveSameBytesUntouched() throws IOException {
        Fixture fixture = fixture();
        ProtosBytesValue bytes = fixture.create();
        Long ten = integer(10);
        Long twenty = integer(20);
        fixture.add(bytes, ten);
        fixture.add(bytes, twenty);

        assertDirectError(fixture, () -> fixture.send(bytes, "atPut", integer(2), integer(9)));
        assertDirectError(fixture, () -> fixture.send(bytes, "atPut", integer(0), integer(-1)));
        assertDirectError(fixture, () -> fixture.send(bytes, "atPut", integer(0), integer(256)));
        assertDirectError(
                fixture,
                () -> fixture.send(bytes, "atPut", integer(0), new ProtosFloatValue(7.0)));
        assertDirectError(
                fixture,
                () -> fixture.send(bytes, "atPut", integer(0), new ProtosStringValue("7")));
        assertDirectError(fixture, () -> fixture.send(bytes, "add", integer(-1)));
        assertDirectError(fixture, () -> fixture.send(bytes, "add", integer(256)));
        assertDirectError(
                fixture, () -> fixture.send(bytes, "add", new ProtosFloatValue(1.0)));
        assertDirectError(fixture, () -> fixture.send(bytes, "removeAt", integer(-1)));
        assertDirectError(fixture, () -> fixture.send(bytes, "removeAt", integer(2)));

        assertEquals(2, bytes.indexedSize());
        assertSameInteger(ten, fixture.send(bytes, "at", integer(0)));
        assertSameInteger(twenty, fixture.send(bytes, "at", integer(1)));
    }

    // Preserve the already-closed I012 lifecycle contract without pretending
    // that ordinary source-level close()/freeze() dispatch on represented Bytes
    // currently reaches the same represented-value lifecycle path.
    @Test
    void representedLifecycleContractRemainsJavaSide() throws IOException {
        Fixture fixture = fixture();

        ProtosBytesValue closed = fixture.create();
        fixture.add(closed, integer(1));
        closed.close();
        Long two = integer(2);
        assertSame(two, fixture.send(closed, "atPut", integer(0), two));
        assertDirectError(fixture, () -> fixture.send(closed, "add", integer(3)));
        assertDirectError(fixture, () -> fixture.send(closed, "removeAt", integer(0)));
        assertEquals("1", intValue(fixture.send(closed, "size")));
        assertSameInteger(two, fixture.send(closed, "at", integer(0)));

        ProtosBytesValue frozen = fixture.create();
        Long four = integer(4);
        fixture.add(frozen, four);
        frozen.freeze();
        assertDirectError(fixture, () -> fixture.send(frozen, "atPut", integer(0), integer(5)));
        assertDirectError(
                fixture,
                () ->
                        fixture.send(
                                frozen,
                                "atPut",
                                new ProtosStringValue("bad-index"),
                                new ProtosStringValue("bad-value")));
        assertDirectError(fixture, () -> fixture.send(frozen, "add", integer(5)));
        assertDirectError(fixture, () -> fixture.send(frozen, "removeAt", integer(0)));
        assertSameInteger(four, fixture.send(frozen, "at", integer(0)));
        assertEquals("1", intValue(fixture.send(frozen, "size")));
    }

    // Hidden-factory Closure provenance and the exact Error category for
    // receiver-domain / callback failures remain Java-side supplements.
    @Test
    void hiddenMethodsAndReceiverErrorsRemainDirectStandardError() throws IOException {
        Fixture fixture = fixture();

        ProtosBytesValue bytes = fixture.create();
        fixture.add(bytes, integer(9));

        ProtosObjectValue delegated = new ProtosObjectValue(bytes);
        assertDirectError(fixture, () -> fixture.send(delegated, "size"));
        assertDirectError(fixture, () -> fixture.send(delegated, "at", integer(0)));

        ProtosObjectValue copied = new ProtosObjectValue(ProtosObjectValue.rootObject());
        copied.createLocalSlot("size", fixture.factory.readLocalSlot("size").orElseThrow());
        copied.createLocalSlot("add", fixture.factory.readLocalSlot("add").orElseThrow());
        assertDirectError(fixture, () -> fixture.send(copied, "size"));
        assertDirectError(fixture, () -> fixture.send(copied, "add", integer(1)));

        assertDirectError(fixture, () -> fixture.send(bytes, "each", integer(1)));
    }

    @Test
    void indicesAndOctetsAreRangeCheckedBeforeAnyNarrowing() throws IOException {
        Fixture fixture = fixture();
        ProtosBytesValue bytes = fixture.create();
        Long zero = integer(0);
        Long max = integer(255);
        assertSame(zero, fixture.add(bytes, zero));
        assertSame(max, fixture.add(bytes, max));
        Object twoPow63 = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(BigInteger.ONE.shiftLeft(63), fixture.prelude);
        Object largeNegative =
                com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(BigInteger.ONE.shiftLeft(64).negate(), fixture.prelude);

        for (Object index : List.of(
                integer(Integer.MAX_VALUE), integer(Integer.MIN_VALUE),
                integer(Long.MAX_VALUE), integer(-1), integer(2),
                twoPow63, largeNegative, new ProtosFloatValue(0.0))) {
            assertDirectError(fixture, () -> fixture.send(bytes, "at", index));
            assertDirectError(fixture, () -> fixture.send(bytes, "atPut", index, integer(1)));
            assertDirectError(fixture, () -> fixture.send(bytes, "removeAt", index));
        }
        for (Object octet : List.of(
                integer(-1), integer(256), integer(Long.MIN_VALUE), twoPow63, largeNegative,
                new ProtosFloatValue(1.0), new ProtosFloatValue(0.0))) {
            assertDirectError(fixture, () -> fixture.send(bytes, "add", octet));
            assertDirectError(fixture, () -> fixture.send(bytes, "atPut", integer(0), octet));
        }

        // No failed operation changed the content, which still holds the same Integers.
        assertEquals(2, bytes.indexedSize());
        assertSameInteger(zero, fixture.send(bytes, "at", integer(0)));
        assertSameInteger(max, fixture.send(bytes, "at", integer(1)));
        assertEquals(List.of((byte) 0, (byte) 0xff),
                List.of(bytes.octetSnapshot()[0], bytes.octetSnapshot()[1]));

        Long one = integer(1);
        assertSame(one, fixture.send(bytes, "atPut", integer(1), one));
        assertSameInteger(one, fixture.send(bytes, "at", integer(1)));
    }

    @Test
    void eachObservesTheStoredOctetIntegersThroughTheCallback() throws IOException {
        Fixture fixture = fixture();
        ProtosBytesValue bytes = fixture.create();
        List<Object> stored = List.of(integer(0), integer(128), integer(255));
        for (Object octet : stored) {
            fixture.add(bytes, octet);
        }
        java.util.ArrayList<Object> observed = new java.util.ArrayList<>();
        Object result = fixture.send(bytes, "each", ProtosClosureValue.nativeClosure(
                (activation, supplied) -> {
                    observed.add(supplied.get(0));
                    return supplied.get(0);
                }));
        assertSame(bytes, result);
        assertEquals(stored.size(), observed.size());
        for (int index = 0; index < stored.size(); index++) {
            assertSameInteger(stored.get(index), observed.get(index));
        }
    }

    @Test
    void runtimeSnapshotIsAnIndependentArrayOfTheStoredOctetIntegers()
            throws IOException {
        Fixture fixture = fixture();
        ProtosBytesValue bytes = fixture.create();
        Long zero = integer(0);
        Long middle = integer(128);
        Long max = integer(255);
        fixture.add(bytes, zero);
        fixture.add(bytes, middle);
        fixture.add(bytes, max);

        Object[] first = bytes.indexedSnapshotForRuntime();
        Object[] second = bytes.indexedSnapshotForRuntime();
        assertNotSame(first, second);
        assertEquals(3, first.length);
        assertSameInteger(zero, first[0]);
        assertSameInteger(middle, first[1]);
        assertSameInteger(max, first[2]);

        // Writing the array does not reach the Bytes receiver.
        second[0] = integer(7);
        assertSameInteger(zero, bytes.indexedAt(0));

        // Later receiver mutation does not reach an earlier snapshot.
        Long replacement = integer(9);
        bytes.indexedPut(1, replacement);
        bytes.indexedRemoveAt(0);
        bytes.indexedAdd(integer(42));
        assertEquals(3, first.length);
        assertSameInteger(zero, first[0]);
        assertSameInteger(middle, first[1]);
        assertSameInteger(max, first[2]);
        assertSameInteger(replacement, bytes.indexedAt(0));

        // The public list snapshot keeps its previous immutable copy contract.
        List<Object> listed = bytes.indexedSnapshot();
        assertEquals(3, listed.size());
        assertSameInteger(replacement, listed.get(0));
        assertSameInteger(max, listed.get(1));
        assertThrows(UnsupportedOperationException.class, () -> listed.set(0, zero));
        bytes.indexedAdd(integer(1));
        assertEquals(3, listed.size());
    }

    @Test
    void emptyBytesHasNoOctets() throws IOException {
        Fixture fixture = fixture();
        ProtosBytesValue bytes = fixture.create();
        assertEquals(0, bytes.indexedSize());
        assertEquals(0, bytes.octetSnapshot().length);
        assertEquals(0, bytes.indexedSnapshotForRuntime().length);
        assertTrue(bytes.indexedSnapshot().isEmpty());
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.indexedAt(0));
    }

    @Test
    void highOctetsReadAsUnsignedIntegers() throws IOException {
        Fixture fixture = fixture();
        ProtosBytesValue bytes = fixture.create();
        long[] octets = {0, 127, 128, 255};
        for (long octet : octets) {
            fixture.add(bytes, integer(octet));
        }
        for (int index = 0; index < octets.length; index++) {
            assertSameInteger(integer(octets[index]), fixture.send(bytes, "at", integer(index)));
            assertSameInteger(integer(octets[index]), bytes.indexedAt(index));
            assertSameInteger(integer(octets[index]), bytes.indexedSnapshot().get(index));
            assertSameInteger(integer(octets[index]), bytes.indexedSnapshotForRuntime()[index]);
        }
        assertArrayEquals(new byte[] {0, 0x7f, (byte) 0x80, (byte) 0xff}, bytes.octetSnapshot());
        ProtosBytesValue copied =
                new ProtosBytesValue(fixture.factory, new byte[] {(byte) 0x80, (byte) 0xff});
        assertSameInteger(integer(128), copied.indexedAt(0));
        assertSameInteger(integer(255), copied.indexedAt(1));
    }

    @Test
    void growthReplacementAndRemovalKeepTheLogicalOctets() throws IOException {
        Fixture fixture = fixture();
        ProtosBytesValue bytes = fixture.create();
        int count = 1000;
        for (int index = 0; index < count; index++) {
            fixture.add(bytes, integer(index & 0xff));
        }
        assertEquals(count, bytes.indexedSize());
        for (int index = 0; index < count; index++) {
            assertSameInteger(integer(index & 0xff), bytes.indexedAt(index));
        }
        Long first = integer(0xff);
        Long last = integer(0x80);
        assertSame(first, fixture.send(bytes, "atPut", integer(0), first));
        assertSame(last, fixture.send(bytes, "atPut", integer(count - 1), last));
        assertSameInteger(first, bytes.indexedAt(0));
        assertSameInteger(last, bytes.indexedAt(count - 1));

        assertSameInteger(integer(0xff), fixture.send(bytes, "removeAt", integer(0)));
        assertEquals(count - 1, bytes.indexedSize());
        assertSameInteger(integer(1), bytes.indexedAt(0));
        assertSameInteger(integer(0x80), bytes.indexedAt(count - 2));
        assertSameInteger(integer(0x80), fixture.send(bytes, "removeAt", integer(count - 2)));
        assertEquals(count - 2, bytes.indexedSize());
        assertSameInteger(integer((count - 2) & 0xff), bytes.indexedAt(count - 3));
        assertSameInteger(integer(0x80), bytes.indexedRemoveAt(127));
        assertSameInteger(integer(0x81), bytes.indexedAt(127));
        byte[] snapshot = bytes.octetSnapshot();
        assertEquals(count - 3, snapshot.length);
        assertEquals((byte) 0xff, snapshot[254 - 1]);
    }

    @Test
    void directJavaInsertionRejectsNonOctetsBeforeMutation() throws IOException {
        Fixture fixture = fixture();
        ProtosBytesValue bytes = fixture.create();
        bytes.indexedAdd(integer(7));
        Object big =
                ProtosNumericValueSupport.integerFromHost(BigInteger.ONE.shiftLeft(70), fixture.prelude);
        java.util.ArrayList<Object> invalid = new java.util.ArrayList<>();
        invalid.add(null);
        invalid.addAll(
                List.of(
                        new Object(), Boolean.TRUE, new ProtosFloatValue(1.0), 1.0d,
                        new ProtosStringValue("1"), ProtosNullValue.INSTANCE, integer(-1),
                        integer(256), big, (byte) 1, 1));
        for (Object value : invalid) {
            assertThrows(RuntimeException.class, () -> bytes.indexedAdd(value));
            assertThrows(RuntimeException.class, () -> bytes.indexedPut(0, value));
        }
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.indexedPut(1, integer(1)));
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.indexedPut(-1, integer(1)));
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.indexedRemoveAt(1));
        assertEquals(1, bytes.indexedSize());
        assertArrayEquals(new byte[] {7}, bytes.octetSnapshot());
    }

    @Test
    void closedAndFrozenJavaMutationsFailInOrderWithoutMutation() throws IOException {
        Fixture fixture = fixture();
        ProtosBytesValue closed = fixture.create();
        closed.indexedAdd(integer(1));
        closed.close();
        assertThrows(IllegalStateException.class, () -> closed.indexedAdd(integer(2)));
        assertThrows(IllegalStateException.class, () -> closed.indexedAdd(integer(256)));
        assertThrows(IllegalStateException.class, () -> closed.indexedRemoveAt(5));
        assertThrows(
                IllegalStateException.class, () -> closed.appendOctetsForRuntime(new byte[] {1}));
        Long two = integer(2);
        assertSame(two, closed.indexedPut(0, two));
        assertArrayEquals(new byte[] {2}, closed.octetSnapshot());

        ProtosBytesValue frozen = fixture.create();
        frozen.indexedAdd(integer(3));
        frozen.freeze();
        // Frozen is checked before the index and the value.
        assertThrows(IllegalStateException.class, () -> frozen.indexedPut(9, "bad"));
        assertThrows(IllegalStateException.class, () -> frozen.indexedPut(0, integer(4)));
        assertThrows(IllegalStateException.class, () -> frozen.indexedAdd(integer(4)));
        assertArrayEquals(new byte[] {3}, frozen.octetSnapshot());
    }

    @Test
    void eachIsStableAgainstLaterReceiverMutation() throws IOException {
        Fixture fixture = fixture();
        ProtosBytesValue bytes = fixture.create();
        for (long octet : new long[] {0x80, 0xff, 1}) {
            fixture.add(bytes, integer(octet));
        }
        fixture.send(bytes, "atPut", integer(2), integer(0xff));
        fixture.send(bytes, "removeAt", integer(0));
        fixture.add(bytes, integer(0x80));
        java.util.ArrayList<Object> observed = new java.util.ArrayList<>();
        Object result =
                fixture.send(
                        bytes,
                        "each",
                        ProtosClosureValue.nativeClosure(
                                (activation, supplied) -> {
                                    observed.add(supplied.get(0));
                                    if (observed.size() == 1) {
                                        bytes.indexedPut(1, integer(9));
                                        bytes.indexedAdd(integer(10));
                                    }
                                    return supplied.get(0);
                                }));
        assertSame(bytes, result);
        assertEquals(3, observed.size());
        assertSameInteger(integer(0xff), observed.get(0));
        assertSameInteger(integer(0xff), observed.get(1));
        assertSameInteger(integer(0x80), observed.get(2));
        assertArrayEquals(new byte[] {(byte) 0xff, 9, (byte) 0x80, 10}, bytes.octetSnapshot());
    }

    @Test
    void snapshotsAndHostSourcesAreIndependentOfTheStorage() throws IOException {
        Fixture fixture = fixture();
        byte[] source = {1, (byte) 0x80, (byte) 0xff};
        ProtosBytesValue bytes = new ProtosBytesValue(fixture.factory, source);
        source[0] = 42;
        assertSameInteger(integer(1), bytes.indexedAt(0));

        byte[] octets = bytes.octetSnapshot();
        Object[] runtime = bytes.indexedSnapshotForRuntime();
        List<Object> listed = bytes.indexedSnapshot();
        octets[1] = 0;
        runtime[1] = integer(0);
        assertSameInteger(integer(0x80), bytes.indexedAt(1));
        assertNotSame(octets, bytes.octetSnapshot());

        byte[] appended = {5, 6};
        bytes.appendOctetsForRuntime(appended);
        appended[0] = 0;
        assertArrayEquals(new byte[] {1, (byte) 0x80, (byte) 0xff, 5, 6}, bytes.octetSnapshot());

        bytes.indexedPut(2, integer(7));
        assertEquals(3, listed.size());
        assertSameInteger(integer(0xff), listed.get(2));
        assertThrows(UnsupportedOperationException.class, () -> listed.set(0, integer(1)));
        assertFalse(listed.contains(integer(5)));
    }

    private static void assertSameInteger(Object expected, Object actual) {
        assertTrue(ProtosIdentity.identical(expected, actual), expected + " vs " + actual);
    }

    private static Long integer(long value) {
        return Long.valueOf(value);
    }

    private static String intValue(Object value) {
        return com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value);
    }

    private static Fixture fixture() throws IOException {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosObjectValue factory =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStandardBytesProtocol.install(factory);
        return new Fixture(prelude, activation, factory);
    }

    private static void assertDirectError(
            Fixture fixture, org.junit.jupiter.api.function.Executable executable) {
        ProtosSignalException signal =
                assertThrows(ProtosSignalException.class, executable);
        assertSame(
                fixture.prelude.errorPrototype(),
                signal.error().parent().orElseThrow());
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation,
            ProtosObjectValue factory) {
        ProtosBytesValue create() {
            return (ProtosBytesValue)
                    ProtosInvocation.invoke(factory, List.of(), activation);
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

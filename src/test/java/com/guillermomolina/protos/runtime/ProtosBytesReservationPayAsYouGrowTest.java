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

package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosBytesReservationPayAsYouGrowTest {
    @Test
    void ordinaryBytesKeepReservationStateAbsentAndIndexedFastPathUnsynchronized()
            throws Exception {
        ProtosBytesValue bytes = new ProtosBytesValue(ProtosObjectValue.rootObject());
        ProtosIntegerValue one = octet(1);
        ProtosIntegerValue two = octet(2);

        assertNull(reservations(bytes));
        bytes.indexedAdd(one);
        assertEquals(BigInteger.ONE, bytes.indexedSize());
        assertSame(one, bytes.indexedAt(BigInteger.ZERO));
        bytes.indexedPut(BigInteger.ZERO, two);
        assertSame(two, bytes.indexedSnapshot().get(0));
        assertSame(two, bytes.indexedRemoveAt(BigInteger.ZERO));
        assertNull(reservations(bytes));

        assertUnsynchronized(
                ProtosBytesValue.class,
                "indexedSize",
                "indexedAt",
                "indexedPut",
                "indexedAdd",
                "indexedRemoveAt",
                "indexedSnapshot",
                "rangeSnapshot",
                "hasReservation",
                "isIndexReserved");
        assertSynchronized(
                ProtosBytesValue.class,
                "tryReserve",
                "releaseReservation",
                "commitReserved");
    }

    @Test
    void zeroLengthReservationDoesNotMaterializeBytesReservationState()
            throws Exception {
        ProtosBytesValue bytes = bytes(1, 2, 3);
        Object token = new Object();

        assertTrue(bytes.tryReserve(BigInteger.ONE, BigInteger.ZERO, token));

        assertNull(reservations(bytes));
        assertFalse(bytes.hasReservation());
        assertFalse(bytes.isIndexReserved(BigInteger.ONE));
        assertSame(bytes.indexedAt(BigInteger.ONE), bytes.rangeSnapshot(BigInteger.ONE, BigInteger.ONE).get(0));
    }

    @Test
    void bytesReservationStateGrowsOnlyForLiveReservationsAndDisappearsAfterLastRelease()
            throws Exception {
        ProtosBytesValue bytes = bytes(1, 2, 3, 4);
        Object first = new Object();
        Object second = new Object();

        assertTrue(bytes.tryReserve(BigInteger.ZERO, BigInteger.valueOf(2), first));
        assertTrue(bytes.hasReservation());
        assertTrue(bytes.isIndexReserved(BigInteger.ZERO));
        assertTrue(bytes.isIndexReserved(BigInteger.ONE));
        assertFalse(bytes.isIndexReserved(BigInteger.valueOf(2)));
        assertEquals(1, reservations(bytes).size());

        assertTrue(bytes.tryReserve(BigInteger.valueOf(2), BigInteger.valueOf(2), second));
        assertEquals(2, reservations(bytes).size());

        assertFalse(
                bytes.tryReserve(
                        BigInteger.ONE, BigInteger.valueOf(2), new Object()));
        assertEquals(2, reservations(bytes).size());

        bytes.releaseReservation(first);
        assertTrue(bytes.hasReservation());
        assertFalse(bytes.isIndexReserved(BigInteger.ZERO));
        assertTrue(bytes.isIndexReserved(BigInteger.valueOf(2)));
        assertEquals(1, reservations(bytes).size());

        bytes.releaseReservation(second);
        assertFalse(bytes.hasReservation());
        assertNull(reservations(bytes));
    }

    @Test
    void bytesCommitPublishesReservedSliceThenReturnsToOrdinaryState()
            throws Exception {
        ProtosBytesValue bytes = bytes(10, 20, 30, 40);
        Object token = new Object();

        assertTrue(bytes.tryReserve(BigInteger.ONE, BigInteger.valueOf(2), token));

        ProtosIntegerValue replacementOne = octet(21);
        ProtosIntegerValue replacementTwo = octet(31);
        bytes.commitReserved(
                BigInteger.ONE, List.of(replacementOne, replacementTwo), token);

        List<Object> snapshot = bytes.indexedSnapshot();
        assertEquals(BigInteger.valueOf(10), ((ProtosIntegerValue) snapshot.get(0)).value());
        assertSame(replacementOne, snapshot.get(1));
        assertSame(replacementTwo, snapshot.get(2));
        assertEquals(BigInteger.valueOf(40), ((ProtosIntegerValue) snapshot.get(3)).value());
        assertFalse(bytes.hasReservation());
        assertNull(reservations(bytes));
    }

    @Test
    void byteRegionUsesTheSameLazyReservationLifetime() throws Exception {
        ProtosByteRegionValue region =
                new ProtosByteRegionValue(List.of(octet(1), octet(2), octet(3)));
        Object first = new Object();
        Object second = new Object();

        assertNull(reservations(region));
        assertTrue(region.tryReserve(BigInteger.ZERO, BigInteger.ZERO, first));
        assertNull(reservations(region));

        assertTrue(region.tryReserve(BigInteger.ZERO, BigInteger.ONE, first));
        assertTrue(region.tryReserve(BigInteger.ONE, BigInteger.valueOf(2), second));
        assertEquals(2, reservations(region).size());
        assertTrue(region.isIndexReserved(BigInteger.ZERO));
        assertTrue(region.isIndexReserved(BigInteger.valueOf(2)));

        assertFalse(
                region.tryReserve(
                        BigInteger.valueOf(2), BigInteger.ONE, new Object()));

        region.releaseReservation(first);
        assertEquals(1, reservations(region).size());

        ProtosIntegerValue replacement = octet(9);
        region.commitReserved(
                BigInteger.ONE, List.of(replacement, replacement), second);
        assertSame(replacement, region.indexedAt(BigInteger.ONE));
        assertSame(replacement, region.indexedAt(BigInteger.valueOf(2)));
        assertNull(reservations(region));

        assertUnsynchronized(
                ProtosByteRegionValue.class,
                "indexedSize",
                "indexedAt",
                "indexedPut",
                "indexedSnapshot",
                "rangeSnapshot",
                "isIndexReserved");
        assertSynchronized(
                ProtosByteRegionValue.class,
                "tryReserve",
                "releaseReservation",
                "commitReserved");
    }

    private static ProtosBytesValue bytes(int... values) {
        ProtosBytesValue bytes = new ProtosBytesValue(ProtosObjectValue.rootObject());
        for (int value : values) {
            bytes.indexedAdd(octet(value));
        }
        return bytes;
    }

    private static ProtosIntegerValue octet(int value) {
        return new ProtosIntegerValue(BigInteger.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static List<?> reservations(Object value) throws Exception {
        Field field = value.getClass().getDeclaredField("reservations");
        field.setAccessible(true);
        return (List<?>) field.get(value);
    }

    private static void assertUnsynchronized(
            Class<?> type, String... methodNames) throws Exception {
        for (String methodName : methodNames) {
            Method method = uniqueMethod(type, methodName);
            assertFalse(
                    Modifier.isSynchronized(method.getModifiers()),
                    () -> type.getSimpleName() + "." + methodName + " must stay off the monitor fast path");
        }
    }

    private static void assertSynchronized(
            Class<?> type, String... methodNames) throws Exception {
        for (String methodName : methodNames) {
            Method method = uniqueMethod(type, methodName);
            assertTrue(
                    Modifier.isSynchronized(method.getModifiers()),
                    () -> type.getSimpleName() + "." + methodName + " must coordinate active reservations");
        }
    }

    private static Method uniqueMethod(Class<?> type, String name) {
        Method selected = null;
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }
            if (selected != null) {
                throw new AssertionError("method name is overloaded: " + type.getName() + "." + name);
            }
            selected = method;
        }
        if (selected == null) {
            throw new AssertionError("method not found: " + type.getName() + "." + name);
        }
        return selected;
    }
}

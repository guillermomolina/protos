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

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtosAdditionalIndexedInteropTest {
    private final InteropLibrary interop = InteropLibrary.getUncached();

    private static ProtosFixedIntegerValue octet(int value) {
        return new ProtosFixedIntegerValue(
                ProtosFixedIntegerValue.Family.UINT8,
                BigInteger.valueOf(value));
    }

    @Test
    void bytesProjectsCurrentSynchronizedIndexedStateReadOnly() throws Exception {
        ProtosBytesValue bytes =
                new ProtosBytesValue(ProtosObjectValue.rootObject());
        ProtosFixedIntegerValue first = octet(1);
        ProtosFixedIntegerValue replacement = octet(2);
        bytes.indexedAdd(first);

        assertTrue(interop.hasArrayElements(bytes));
        assertEquals(1L, interop.getArraySize(bytes));
        assertSame(first, interop.readArrayElement(bytes, 0));
        assertEquals(BigInteger.ONE, interop.asBigInteger(
                interop.readArrayElement(bytes, 0)));

        bytes.indexedPut(BigInteger.ZERO, replacement);
        assertSame(replacement, interop.readArrayElement(bytes, 0));

        assertFalse(interop.isArrayElementWritable(bytes, 0));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.writeArrayElement(bytes, 0, octet(3)));
        assertFalse(interop.hasIterator(bytes));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.getIterator(bytes));
        assertFalse(interop.hasMembers(bytes));
        assertEquals("Bytes", interop.toDisplayString(bytes, false));
    }

    @Test
    void bytesHostOnlyElementFailsClosed() throws Exception {
        ProtosBytesValue bytes =
                new ProtosBytesValue(ProtosObjectValue.rootObject());
        Object hostOnly = new Object();
        bytes.indexedAdd(hostOnly);

        assertEquals(1L, interop.getArraySize(bytes));
        assertFalse(interop.isArrayElementReadable(bytes, 0));
        assertThrows(
                InvalidArrayIndexException.class,
                () -> interop.readArrayElement(bytes, 0));
        assertSame(hostOnly, bytes.indexedAt(BigInteger.ZERO));
    }

    @Test
    void byteRegionProjectsExactLiveIndexedReferencesWithoutMutationInterop()
            throws Exception {
        ProtosFixedIntegerValue first = octet(10);
        ProtosFixedIntegerValue replacement = octet(11);
        ProtosByteRegionValue region =
                new ProtosByteRegionValue(List.of(first));

        assertTrue(interop.hasArrayElements(region));
        assertEquals(1L, interop.getArraySize(region));
        assertSame(first, interop.readArrayElement(region, 0));

        region.indexedPut(BigInteger.ZERO, replacement);
        assertSame(replacement, interop.readArrayElement(region, 0));

        assertFalse(interop.isArrayElementWritable(region, 0));
        assertFalse(interop.isArrayElementInsertable(region, 1));
        assertFalse(interop.isArrayElementRemovable(region, 0));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.writeArrayElement(region, 0, octet(12)));
        assertFalse(interop.hasIterator(region));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.getIterator(region));
        assertFalse(interop.hasMembers(region));
        assertEquals("ByteRegion", interop.toDisplayString(region, false));
    }

    @Test
    void processArgumentsProjectsItsImmutableIndexedSnapshotDirectly()
            throws Exception {
        ProtosObjectValue prototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosProcessArgumentsValue arguments =
                ProtosProcessArgumentsValue.captureForRuntime(
                        prototype, List.of("one", "two"));

        assertTrue(interop.hasArrayElements(arguments));
        assertEquals(2L, interop.getArraySize(arguments));
        assertTrue(interop.isArrayElementReadable(arguments, 0));
        assertTrue(interop.isArrayElementReadable(arguments, 1));
        assertFalse(interop.isArrayElementReadable(arguments, -1));
        assertFalse(interop.isArrayElementReadable(arguments, 2));

        Object first = interop.readArrayElement(arguments, 0);
        Object second = interop.readArrayElement(arguments, 1);
        assertSame(arguments.indexedAtForRuntime(BigInteger.ZERO), first);
        assertSame(arguments.indexedAtForRuntime(BigInteger.ONE), second);
        assertEquals("one", interop.asString(first));
        assertEquals("two", interop.asString(second));

        assertThrows(
                InvalidArrayIndexException.class,
                () -> interop.readArrayElement(arguments, 2));
        assertFalse(interop.isArrayElementWritable(arguments, 0));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.writeArrayElement(
                        arguments, 0, new ProtosStringValue("replacement")));
        assertFalse(interop.hasIterator(arguments));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.getIterator(arguments));
        assertFalse(interop.hasMembers(arguments));
        assertEquals(
                "ProcessArguments",
                interop.toDisplayString(arguments, false));
    }

    @Test
    void indexedFacetsDoNotAcquireOtherInteropCapabilities() {
        ProtosBytesValue bytes =
                new ProtosBytesValue(ProtosObjectValue.rootObject());
        ProtosByteRegionValue region =
                new ProtosByteRegionValue(List.of(octet(1)));
        ProtosProcessArgumentsValue arguments =
                ProtosProcessArgumentsValue.captureForRuntime(
                        new ProtosObjectValue(ProtosObjectValue.rootObject()),
                        List.of("x"));

        for (Object value : List.of(bytes, region, arguments)) {
            assertFalse(interop.isExecutable(value));
            assertFalse(interop.isString(value));
            assertFalse(interop.isBoolean(value));
            assertFalse(interop.isNumber(value));
            assertFalse(interop.isNull(value));
            String display = String.valueOf(
                    interop.toDisplayString(value, false));
            assertFalse(display.contains("@"));
            assertFalse(display.contains("com.guillermomolina"));
        }
    }
}

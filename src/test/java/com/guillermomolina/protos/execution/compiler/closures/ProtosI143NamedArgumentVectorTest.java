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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosNamedArgumentAssociation.NamedValue;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedArgumentVector;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** I143: evaluated argument vector before named dispatch integration. */
class ProtosI143NamedArgumentVectorTest {

    @Test
    void positionalOnlyVectorPreservesExistingBehavior() {
        PreparedArgumentVector vector = new PreparedArgumentVector();

        assertFalse(vector.hasNamedArguments());
        assertTrue(vector.namedSnapshot().isEmpty());

        vector.append(10);
        vector.append(20);

        assertEquals(List.of(10, 20), vector.snapshot());
        assertEquals(List.of(10, 20), vector.positionalSnapshot());
    }

    @Test
    void namesDoNotConsumePositionalOrdinals() {
        PreparedArgumentVector vector = new PreparedArgumentVector();

        vector.append(1);
        ProtosBytecodeRootNode.AppendNamedSuppliedArgument.perform(
                "c", vector, 30);

        vector.append(2);
        ProtosBytecodeRootNode.AppendNamedSuppliedArgument.perform(
                "a", vector, 10);

        assertTrue(vector.hasNamedArguments());
        assertEquals(List.of(1, 2), vector.positionalSnapshot());

        List<NamedValue> named = vector.namedSnapshot();
        assertEquals(2, named.size());
        assertEquals("c", named.get(0).name());
        assertEquals(30, named.get(0).value());
        assertEquals("a", named.get(1).name());
        assertEquals(10, named.get(1).value());
    }

    @Test
    void positionalOnlyAccessorsCannotDiscardNames() {
        PreparedArgumentVector vector = new PreparedArgumentVector();

        vector.append(1);
        vector.appendNamed("b", 20);

        assertThrows(
                IllegalStateException.class,
                vector::snapshot);

        assertThrows(
                IllegalStateException.class,
                vector::soleValueOrNull);
    }

    @Test
    void namedSnapshotsAreIndependentAndImmutable() {
        PreparedArgumentVector vector = new PreparedArgumentVector();

        vector.appendNamed("a", 10);
        List<NamedValue> before = vector.namedSnapshot();

        vector.appendNamed("b", 20);

        assertEquals(
                List.of(new NamedValue("a", 10)),
                before);
        assertEquals(2, vector.namedSnapshot().size());

        assertThrows(
                UnsupportedOperationException.class,
                () -> before.clear());
    }

    @Test
    void explicitNullRetainsItsIdentity() {
        PreparedArgumentVector vector = new PreparedArgumentVector();

        vector.appendNamed("a", ProtosNullValue.INSTANCE);

        assertSame(
                ProtosNullValue.INSTANCE,
                vector.namedSnapshot().get(0).value());
        assertTrue(vector.positionalSnapshot().isEmpty());
    }

    @Test
    void invalidEntriesDoNotMutateTheVector() {
        PreparedArgumentVector vector = new PreparedArgumentVector();

        assertThrows(
                IllegalArgumentException.class,
                () -> vector.appendNamed("", 1));

        assertThrows(
                NullPointerException.class,
                () -> vector.appendNamed("a", null));

        assertFalse(vector.hasNamedArguments());
        assertTrue(vector.namedSnapshot().isEmpty());
    }

    @Test
    void positionalSnapshotsAreImmutable() {
        PreparedArgumentVector vector = new PreparedArgumentVector();

        vector.append(1);
        List<Object> before = vector.positionalSnapshot();

        vector.append(2);
        vector.appendNamed("c", 3);

        assertEquals(List.of(1), before);
        assertEquals(List.of(1, 2), vector.positionalSnapshot());

        assertThrows(
                UnsupportedOperationException.class,
                () -> before.clear());
    }

    private static ProtosArrayValue array(List<?> elements) {
        return new ProtosArrayValue(ProtosObjectValue.rootObject(), elements);
    }

    private static List<Object> distinct(int count) {
        List<Object> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(new Object());
        }
        return values;
    }

    @Test
    void emptySpreadAddsNothing() {
        PreparedArgumentVector vector = new PreparedArgumentVector();

        assertTrue(vector.appendSpreadArrayOrFalse(array(List.of())));

        assertEquals(0, vector.positionalArray().length);
        assertTrue(vector.snapshot().isEmpty());
        assertEquals(null, vector.soleValueOrNull());
    }

    @Test
    void singleElementSpreadKeepsIdentity() {
        Object element = new Object();
        PreparedArgumentVector vector = new PreparedArgumentVector();

        assertTrue(vector.appendSpreadArrayOrFalse(array(List.of(element))));

        assertSame(element, vector.soleValueOrNull());
        assertSame(element, vector.positionalArray()[0]);
    }

    @Test
    void largeSpreadGrowsStorageAndPreservesOrderAndIdentity() {
        List<Object> elements = distinct(1000);
        PreparedArgumentVector vector = new PreparedArgumentVector();

        assertTrue(vector.appendSpreadArrayOrFalse(array(elements)));

        Object[] positional = vector.positionalArray();
        assertEquals(1000, positional.length);
        for (int index = 0; index < positional.length; index++) {
            assertSame(elements.get(index), positional[index]);
        }
    }

    @Test
    void longStorageSpreadPreservesValuesAndOrder() {
        PreparedArgumentVector vector = new PreparedArgumentVector();

        assertTrue(vector.appendSpreadArrayOrFalse(
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        new long[] {3L, 1L, 2L})));

        assertEquals(List.of(3L, 1L, 2L), vector.snapshot());
    }

    @Test
    void multipleSpreadsInterleavedWithOrdinaryArguments() {
        Object a = new Object();
        Object b = new Object();
        Object c = new Object();
        Object d = new Object();
        Object e = new Object();
        PreparedArgumentVector vector = new PreparedArgumentVector();

        vector.append(a);
        assertTrue(vector.appendSpreadArrayOrFalse(array(List.of(b, c))));
        vector.append(d);
        assertTrue(vector.appendSpreadArrayOrFalse(array(List.of())));
        assertTrue(vector.appendSpreadArrayOrFalse(array(List.of(e))));

        Object[] positional = vector.positionalArray();
        assertEquals(5, positional.length);
        assertSame(a, positional[0]);
        assertSame(b, positional[1]);
        assertSame(c, positional[2]);
        assertSame(d, positional[3]);
        assertSame(e, positional[4]);
    }

    @Test
    void spreadFollowedByNamedArgumentsStaysSeparate() {
        Object a = new Object();
        Object b = new Object();
        PreparedArgumentVector vector = new PreparedArgumentVector();

        assertTrue(vector.appendSpreadArrayOrFalse(array(List.of(a, b))));
        vector.appendNamed("k", 7L);

        ProtosPreparedCalls.NamedArgumentPacket packet = vector.namedPacket();
        assertEquals(2, packet.positional().size());
        assertSame(a, packet.positional().get(0));
        assertSame(b, packet.positional().get(1));
        assertEquals(List.of(new NamedValue("k", 7L)), packet.named());
    }

    @Test
    void laterMutationOfSourceArrayDoesNotAffectSpreadArguments() {
        Object original = new Object();
        Object replacement = new Object();
        ProtosArrayValue source = array(List.of(original, 2L));
        PreparedArgumentVector vector = new PreparedArgumentVector();

        assertTrue(vector.appendSpreadArrayOrFalse(source));
        source.indexedPut(0, replacement);

        assertSame(original, vector.positionalArray()[0]);
        assertSame(replacement, source.indexedAt(0));
    }

    @Test
    void independentVectorsDoNotShareStorage() {
        PreparedArgumentVector small = new PreparedArgumentVector();
        PreparedArgumentVector large = new PreparedArgumentVector();

        assertTrue(small.appendSpreadArrayOrFalse(array(List.of(1L))));
        assertTrue(large.appendSpreadArrayOrFalse(array(distinct(40))));

        assertEquals(1, small.positionalArray().length);
        assertEquals(40, large.positionalArray().length);
        assertEquals(List.of(1L), small.snapshot());
    }

    @Test
    void invalidSpreadReturnsFalseWithoutPartialMutation() {
        PreparedArgumentVector vector = new PreparedArgumentVector();
        vector.append(1L);

        assertFalse(vector.appendSpreadArrayOrFalse(5L));
        assertFalse(vector.appendSpreadArrayOrFalse(ProtosNullValue.INSTANCE));

        assertEquals(List.of(1L), vector.snapshot());
    }

    @Test
    void invalidNamesDoNotAlterPublishedNamedEntries() {
        PreparedArgumentVector vector = new PreparedArgumentVector();
        vector.appendNamed("a", 1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> vector.appendNamed("", 2L));
        assertThrows(
                NullPointerException.class,
                () -> vector.appendNamed("b", null));

        assertEquals(List.of(new NamedValue("a", 1L)), vector.namedSnapshot());
    }

    @Test
    void manyNamedArgumentsGrowTheirStorage() {
        PreparedArgumentVector vector = new PreparedArgumentVector();
        for (int index = 0; index < 20; index++) {
            vector.appendNamed("n" + index, (long) index);
        }

        List<NamedValue> named = vector.namedSnapshot();
        assertEquals(20, named.size());
        for (int index = 0; index < 20; index++) {
            assertEquals("n" + index, named.get(index).name());
            assertEquals((long) index, named.get(index).value());
        }
    }

    @Test
    void snapshotIsImmutableAndIndependentOfTheBuilder() {
        PreparedArgumentVector vector = new PreparedArgumentVector();
        vector.append(1L);
        List<Object> before = vector.snapshot();

        assertTrue(vector.appendSpreadArrayOrFalse(array(List.of(2L, 3L))));

        assertEquals(List.of(1L), before);
        assertThrows(UnsupportedOperationException.class, () -> before.add(9L));
    }

    @Test
    void positionalArrayIsAFreshExactCopy() {
        PreparedArgumentVector vector = new PreparedArgumentVector();
        vector.append(1L);
        vector.append(2L);

        Object[] first = vector.positionalArray();
        Object[] second = vector.positionalArray();

        assertEquals(2, first.length);
        assertNotSame(first, second);
        first[0] = 99L;
        assertArrayEquals(new Object[] {1L, 2L}, vector.positionalArray());
    }

    @Test
    void snapshotClosureCallArgumentsUsesExactPositionalArray() {
        PreparedArgumentVector vector = new PreparedArgumentVector();
        vector.append(1L);
        assertTrue(vector.appendSpreadArrayOrFalse(array(List.of(2L, 3L))));

        assertArrayEquals(
                new Object[] {1L, 2L, 3L},
                ProtosBytecodeRootNode.SnapshotClosureCallArguments.perform(vector));
    }

    @Test
    void positionalArrayEntersTheB5HeaderUnchanged() {
        PreparedArgumentVector vector = new PreparedArgumentVector();
        vector.append(1L);
        assertTrue(vector.appendSpreadArrayOrFalse(array(List.of(2L, 3L))));
        Object receiver = new Object();
        Object home = new Object();
        Object caller = new Object();

        Object[] frame = ProtosFrameArguments.dispatchedCallN(
                null, receiver, home, caller, null, vector.positionalArray());

        assertEquals(ProtosFrameArguments.HEADER_LENGTH + 3, frame.length);
        assertSame(receiver, frame[1]);
        assertSame(home, frame[2]);
        assertSame(caller, frame[3]);
        assertEquals(1L, frame[ProtosFrameArguments.SUPPLIED_ARGUMENT_OFFSET]);
        assertEquals(2L, frame[ProtosFrameArguments.SUPPLIED_ARGUMENT_OFFSET + 1]);
        assertEquals(3L, frame[ProtosFrameArguments.SUPPLIED_ARGUMENT_OFFSET + 2]);
    }
}

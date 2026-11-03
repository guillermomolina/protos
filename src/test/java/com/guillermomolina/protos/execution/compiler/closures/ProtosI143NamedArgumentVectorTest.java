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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosNamedArgumentAssociation.NamedValue;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedArgumentVector;
import com.guillermomolina.protos.runtime.ProtosNullValue;
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
}

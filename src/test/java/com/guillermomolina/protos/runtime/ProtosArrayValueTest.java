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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosArrayValueTest {
    @Test
    void arrayOwnsDenseIndexedStateWithExactElementReferences() {
        ProtosObjectValue parent =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object first = new Object();
        Object second = new Object();

        ProtosArrayValue array =
                new ProtosArrayValue(parent, List.of(first, second));

        assertSame(parent, array.parent().orElseThrow());
        assertEquals(BigInteger.valueOf(2), array.indexedSize());
        assertSame(first, array.indexedAt(BigInteger.ZERO));
        assertSame(second, array.indexedAt(BigInteger.ONE));
    }

    @Test
    void indexedUpdatePreservesLengthAndReturnsExactValue() {
        ProtosArrayValue array =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(ProtosBooleanValue.TRUE));
        Object replacement = new Object();

        assertSame(
                replacement,
                array.indexedPut(BigInteger.ZERO, replacement));
        assertSame(replacement, array.indexedAt(BigInteger.ZERO));
        assertEquals(BigInteger.ONE, array.indexedSize());
    }

    @Test
    void closedArrayAllowsReplacementButFrozenArrayRejectsBeforeIndexValidation() {
        ProtosArrayValue closed =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(ProtosBooleanValue.TRUE));
        closed.close();
        closed.indexedPut(BigInteger.ZERO, ProtosBooleanValue.FALSE);
        assertSame(
                ProtosBooleanValue.FALSE,
                closed.indexedAt(BigInteger.ZERO));

        ProtosArrayValue frozen =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(ProtosBooleanValue.TRUE));
        frozen.freeze();

        assertThrows(
                IllegalStateException.class,
                () ->
                        frozen.indexedPut(
                                BigInteger.valueOf(99),
                                ProtosBooleanValue.FALSE));
        assertSame(
                ProtosBooleanValue.TRUE,
                frozen.indexedAt(BigInteger.ZERO));
    }

    @Test
    void indexedBoundsAreDenseAndSnapshotIsShallowDetachedAndReadOnly() {
        Object first = new Object();
        ProtosArrayValue array =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(first));

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> array.indexedAt(BigInteger.valueOf(-1)));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> array.indexedAt(BigInteger.ONE));

        List<Object> snapshot = array.indexedSnapshot();
        assertSame(first, snapshot.get(0));
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.add(new Object()));

        Object replacement = new Object();
        array.indexedPut(BigInteger.ZERO, replacement);
        assertSame(first, snapshot.get(0));
        assertSame(replacement, array.indexedAt(BigInteger.ZERO));
    }
}

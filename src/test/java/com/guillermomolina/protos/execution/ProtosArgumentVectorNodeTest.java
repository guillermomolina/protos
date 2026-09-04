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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosArgumentVectorNodeTest {
    @Test
    void evaluatesItemsLeftToRightAndFlattensSpreadSnapshotInPlace() {
        List<String> order = new ArrayList<>();
        Object first = new Object();
        Object spreadA = new Object();
        Object spreadB = new Object();
        Object last = new Object();
        ProtosArrayValue spread =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(spreadA, spreadB));

        ProtosArgumentVectorNode node =
                new ProtosArgumentVectorNode(
                        SourceSpan.unknown(),
                        List.of(
                                new ProtosArgumentItem(
                                        recording(order, "first", first), false),
                                new ProtosArgumentItem(
                                        recording(order, "spread", spread), true),
                                new ProtosArgumentItem(
                                        recording(order, "last", last), false)));

        @SuppressWarnings("unchecked")
        List<Object> supplied =
                (List<Object>) execute(node);

        assertEquals(List.of("first", "spread", "last"), order);
        assertEquals(4, supplied.size());
        assertSame(first, supplied.get(0));
        assertSame(spreadA, supplied.get(1));
        assertSame(spreadB, supplied.get(2));
        assertSame(last, supplied.get(3));
    }

    @Test
    void spreadUsesShallowSnapshotTakenAtItsEvaluationPosition() {
        Object before = new Object();
        Object after = new Object();
        ProtosArrayValue array =
                new ProtosArrayValue(
                        ProtosObjectValue.rootObject(),
                        List.of(before));

        ProtosArgumentVectorNode node =
                new ProtosArgumentVectorNode(
                        SourceSpan.unknown(),
                        List.of(
                                new ProtosArgumentItem(
                                        new ProtosConstantNode(
                                                SourceSpan.unknown(), array),
                                        true),
                                new ProtosArgumentItem(
                                        new ProtosExpressionNode(
                                                SourceSpan.unknown()) {
                                            @Override
                                            public Object execute(
                                                    VirtualFrame frame) {
                                                array.indexedPut(
                                                        java.math.BigInteger.ZERO,
                                                        after);
                                                return after;
                                            }
                                        },
                                        false)));

        @SuppressWarnings("unchecked")
        List<Object> supplied =
                (List<Object>) execute(node);

        assertSame(before, supplied.get(0));
        assertSame(after, supplied.get(1));
        assertSame(after, array.indexedAt(java.math.BigInteger.ZERO));
    }

    @Test
    void invalidSpreadSourceSignalsCoreErrorAndStopsLaterEvaluation() {
        List<String> order = new ArrayList<>();
        ProtosArgumentVectorNode node =
                new ProtosArgumentVectorNode(
                        SourceSpan.unknown(),
                        List.of(
                                new ProtosArgumentItem(
                                        recording(
                                                order,
                                                "invalid",
                                                new Object()),
                                        true),
                                new ProtosArgumentItem(
                                        recording(
                                                order,
                                                "later",
                                                new Object()),
                                        false)));

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute(node));

        assertSame(
                ProtosCoreErrors.errorPrototype(),
                signal.error().parent().orElseThrow());
        assertEquals(List.of("invalid"), order);
    }

    @Test
    void suppliedVectorIsDetachedAndReadOnly() {
        Object value = new Object();
        ProtosArgumentVectorNode node =
                new ProtosArgumentVectorNode(
                        SourceSpan.unknown(),
                        List.of(
                                new ProtosArgumentItem(
                                        new ProtosConstantNode(
                                                SourceSpan.unknown(), value),
                                        false)));

        @SuppressWarnings("unchecked")
        List<Object> supplied =
                (List<Object>) execute(node);

        assertSame(value, supplied.get(0));
        assertThrows(
                UnsupportedOperationException.class,
                () -> supplied.add(new Object()));
    }

    private static ProtosExpressionNode recording(
            List<String> order,
            String label,
            Object result) {
        return new ProtosExpressionNode(SourceSpan.unknown()) {
            @Override
            public Object execute(VirtualFrame frame) {
                order.add(label);
                return result;
            }
        };
    }

    private static Object execute(ProtosExpressionNode node) {
        ProtosObjectValue context =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosActivation activation =
                new ProtosActivation(context, List.of(), context);
        return ProtosExecution.createCallTarget(node).call(activation);
    }
}

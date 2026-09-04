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

import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosSequenceNodeTest {
    @Test
    void executesChildrenLeftToRightAndReturnsFinalResult() {
        List<String> order = new ArrayList<>();
        ProtosSequenceNode sequence =
                new ProtosSequenceNode(
                        new SourceSpan(0, 5),
                        new ProtosExpressionNode[] {
                            new RecordingNode(new SourceSpan(0, 1), "first", 1, order),
                            new RecordingNode(new SourceSpan(2, 3), "second", 2, order),
                            new RecordingNode(new SourceSpan(4, 5), "third", 3, order)
                        });

        assertEquals(3, sequence.execute(null));
        assertEquals(List.of("first", "second", "third"), order);
    }

    @Test
    void emptyExecutionSequenceReturnsCanonicalNull() {
        ProtosSequenceNode sequence =
                new ProtosSequenceNode(
                        new SourceSpan(0, 0),
                        new ProtosExpressionNode[0]);

        assertSame(ProtosNullValue.INSTANCE, sequence.execute(null));
    }

    private static final class RecordingNode extends ProtosExpressionNode {
        private final String label;
        private final Object result;
        private final List<String> order;

        private RecordingNode(
                SourceSpan span,
                String label,
                Object result,
                List<String> order) {
            super(span);
            this.label = label;
            this.result = result;
            this.order = order;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            order.add(label);
            return result;
        }
    }
}

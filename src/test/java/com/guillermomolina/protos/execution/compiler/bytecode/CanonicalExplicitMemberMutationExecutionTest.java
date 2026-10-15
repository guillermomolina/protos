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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosTestPrelude;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalExplicitMemberMutationExecutionTest {
    @Test
    void explicitCreationCreatesLocalSlotAndReturnsRhs() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        ProtosActivation activation =
                ProtosTestPrelude.activation(new ProtosObjectValue(root), List.of(), receiver);

        Object result = execute(
                "this.x: \"created\"",
                activation);

        assertSame(result, receiver.readLocalSlot("x").orElseThrow());
    }

    @Test
    void explicitAssignmentNeverModifiesInheritedSlot() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue prototype = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(prototype);
        prototype.createLocalSlot("alive", ProtosBooleanValue.TRUE);
        ProtosActivation activation =
                ProtosTestPrelude.activation(new ProtosObjectValue(root), List.of(), receiver);

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute(
                                "this.alive = \"nope\"",
                                activation));

        assertSame(ProtosTestPrelude.errorPrototype(), signal.error().parent().orElseThrow());
        assertSame(ProtosBooleanValue.TRUE, prototype.readLocalSlot("alive").orElseThrow());
    }

    @Test
    void explicitAssignmentUpdatesLocalSlotAndReturnsRhs() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        receiver.createLocalSlot("name", ProtosBooleanValue.TRUE);
        ProtosActivation activation =
                ProtosTestPrelude.activation(new ProtosObjectValue(root), List.of(), receiver);

        Object result = execute(
                "this.name = \"updated\"",
                activation);

        assertSame(result, receiver.readLocalSlot("name").orElseThrow());
    }

    @Test
    void explicitMutationSignalsCoreErrorForNonOrdinaryRuntimeTargetInThisSlice() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue context = new ProtosObjectValue(root);
        context.createLocalSlot("value", ProtosBooleanValue.TRUE);
        ProtosActivation activation =
                ProtosTestPrelude.activation(context, List.of(), new ProtosObjectValue(root));

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute(
                                "value.x: \"created\"",
                                activation));

        assertSame(ProtosTestPrelude.errorPrototype(), signal.error().parent().orElseThrow());
    }

    private Object execute(
            String source,
            ProtosActivation activation) {
        return ProtosTestExecutionSupport.evaluate(
                "explicit-member-mutation-bytecode-test.protos",
                source,
                activation);
    }
}

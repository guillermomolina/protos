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
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalMemberReadExecutionTest {
    @Test
    void memberReadReturnsExactLocalValue() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        Object value = new ProtosStringValue("Rex");
        receiver.createLocalSlot("name", value);

        assertSame(value, execute("this.name", activation(receiver)));
    }

    @Test
    void memberReadDelegatesToNearestParent() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue animal = new ProtosObjectValue(root);
        ProtosObjectValue dog = new ProtosObjectValue(animal);
        Object inherited = new ProtosStringValue("animal");
        animal.createLocalSlot("kind", inherited);

        assertSame(inherited, execute("this.kind", activation(dog)));
    }

    @Test
    void memberReadSignalsCoreErrorWhenSlotIsMissing() {
        ProtosObjectValue receiver = new ProtosObjectValue(ProtosObjectValue.rootObject());

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute("this.missing", activation(receiver)));

        assertSame(
                ProtosTestPrelude.slotNotFoundPrototype(),
                signal.error().parent().orElseThrow());
    }

    @Test
    void memberReadSignalsSlotNotFoundForRepresentedValueWithoutMember() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue context = new ProtosObjectValue(root);
        context.createLocalSlot("value", ProtosBooleanValue.TRUE);
        ProtosActivation activation =
                ProtosTestPrelude.activation(context, List.of(), new ProtosObjectValue(root));

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute("value.name", activation));

        assertSame(
                ProtosTestPrelude.slotNotFoundPrototype(),
                signal.error().parent().orElseThrow());
    }

    private ProtosActivation activation(ProtosObjectValue receiver) {
        return ProtosTestPrelude.activation(
                new ProtosObjectValue(ProtosObjectValue.rootObject()),
                List.of(),
                receiver);
    }

    private Object execute(
            String source,
            ProtosActivation activation) {
        return ProtosTestExecutionSupport.evaluate(
                "member-read-bytecode-test.protos",
                source,
                activation);
    }
}

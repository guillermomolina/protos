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
import com.guillermomolina.protos.runtime.ProtosLexicalFallback;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalBareSlotMutationExecutionTest {
    @Test
    void bareCreationCreatesOnlyInCurrentContextAndReturnsValue() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue current = new ProtosObjectValue(root);
        ProtosObjectValue captured = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        ProtosActivation activation =
                ProtosTestPrelude.activation(current, List.of(captured), receiver);

        Object result = execute(
                "x: \"created\"",
                activation);

        assertSame(result, current.readLocalSlot("x").orElseThrow());
        assertSame(result, ProtosLexicalFallback.readByName(activation, "x").orElseThrow());
    }

    @Test
    void duplicateBareCreationSignalsCoreError() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue current = new ProtosObjectValue(root);
        current.createLocalSlot("x", ProtosBooleanValue.TRUE);
        ProtosActivation activation =
                ProtosTestPrelude.activation(current, List.of(), new ProtosObjectValue(root));

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute(
                                "x: \"duplicate\"",
                                activation));

        assertSame(ProtosTestPrelude.errorPrototype(), signal.error().parent().orElseThrow());
        assertSame(ProtosBooleanValue.TRUE, current.readLocalSlot("x").orElseThrow());
    }

    @Test
    void bareAssignmentPrefersCurrentThenCapturedThenReceiverLocal() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue current = new ProtosObjectValue(root);
        ProtosObjectValue captured = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(root);

        current.createLocalSlot("current", ProtosBooleanValue.TRUE);
        captured.createLocalSlot("captured", ProtosBooleanValue.TRUE);
        receiver.createLocalSlot("receiver", ProtosBooleanValue.TRUE);

        ProtosActivation activation =
                ProtosTestPrelude.activation(current, List.of(captured), receiver);

        Object currentValue = execute("current = \"c\"", activation);
        Object capturedValue = execute("captured = \"l\"", activation);
        Object receiverValue = execute("receiver = \"r\"", activation);

        assertSame(currentValue, current.readLocalSlot("current").orElseThrow());
        assertSame(capturedValue, captured.readLocalSlot("captured").orElseThrow());
        assertSame(receiverValue, receiver.readLocalSlot("receiver").orElseThrow());
    }

    @Test
    void bareAssignmentToFrozenDestinationSignalsCoreErrorWithoutMutation() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue current = new ProtosObjectValue(root);
        current.createLocalSlot("x", ProtosBooleanValue.TRUE);
        current.freeze();

        ProtosActivation activation =
                ProtosTestPrelude.activation(current, List.of(), new ProtosObjectValue(root));

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute("x = \"nope\"", activation));

        assertSame(ProtosTestPrelude.errorPrototype(), signal.error().parent().orElseThrow());
        assertSame(ProtosBooleanValue.TRUE, current.readLocalSlot("x").orElseThrow());
    }

    @Test
    void bareAssignmentNeverWritesDelegationParent() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue prototype = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(prototype);
        ProtosObjectValue current = new ProtosObjectValue(root);

        prototype.createLocalSlot("inherited", ProtosBooleanValue.TRUE);

        ProtosActivation activation =
                ProtosTestPrelude.activation(current, List.of(), receiver);

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute("inherited = \"nope\"", activation));

        assertSame(
                ProtosTestPrelude.slotNotFoundPrototype(),
                signal.error().parent().orElseThrow());
        assertSame(ProtosBooleanValue.TRUE, prototype.readLocalSlot("inherited").orElseThrow());
    }

    private Object execute(
            String source,
            ProtosActivation activation) {
        return ProtosTestExecutionSupport.evaluate(
                "bare-slot-mutation-bytecode-test.protos",
                source,
                activation);
    }
}

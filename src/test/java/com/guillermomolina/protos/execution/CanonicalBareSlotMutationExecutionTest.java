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
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosExecutionContext;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.semantic.ast.CanonicalAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CanonicalBareSlotMutationExecutionTest {
    private final CanonicalToTruffleLowerer lowerer = new CanonicalToTruffleLowerer();

    @Test
    void bareCreationCreatesOnlyInCurrentContextAndReturnsValue() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue current = new ProtosObjectValue(root);
        ProtosObjectValue captured = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        ProtosExecutionContext activation =
                new ProtosExecutionContext(current, List.of(captured), receiver);

        Object result = execute(
                new CanonicalCreate(
                        Optional.empty(),
                        "x",
                        literal("created"),
                        new SourceSpan(0, 1)),
                activation);

        assertSame(result, current.readLocalSlot("x").orElseThrow());
        assertSame(result, activation.lookup("x").orElseThrow());
    }

    @Test
    void duplicateBareCreationSignalsCoreError() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue current = new ProtosObjectValue(root);
        current.createLocalSlot("x", ProtosBooleanValue.TRUE);
        ProtosExecutionContext activation =
                new ProtosExecutionContext(current, List.of(), new ProtosObjectValue(root));

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute(
                                new CanonicalCreate(
                                        Optional.empty(),
                                        "x",
                                        literal("duplicate"),
                                        new SourceSpan(0, 1)),
                                activation));

        assertSame(ProtosCoreErrors.errorPrototype(), signal.error().parent().orElseThrow());
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

        ProtosExecutionContext activation =
                new ProtosExecutionContext(current, List.of(captured), receiver);

        Object currentValue = execute(assign("current", "c"), activation);
        Object capturedValue = execute(assign("captured", "l"), activation);
        Object receiverValue = execute(assign("receiver", "r"), activation);

        assertSame(currentValue, current.readLocalSlot("current").orElseThrow());
        assertSame(capturedValue, captured.readLocalSlot("captured").orElseThrow());
        assertSame(receiverValue, receiver.readLocalSlot("receiver").orElseThrow());
    }

    @Test
    void bareAssignmentNeverWritesDelegationParent() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue prototype = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(prototype);
        ProtosObjectValue current = new ProtosObjectValue(root);

        prototype.createLocalSlot("inherited", ProtosBooleanValue.TRUE);

        ProtosExecutionContext activation =
                new ProtosExecutionContext(current, List.of(), receiver);

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute(assign("inherited", "nope"), activation));

        assertSame(ProtosCoreErrors.errorPrototype(), signal.error().parent().orElseThrow());
        assertSame(ProtosBooleanValue.TRUE, prototype.readLocalSlot("inherited").orElseThrow());
    }

    private Object execute(
            com.guillermomolina.protos.semantic.ast.CanonicalExpression expression,
            ProtosExecutionContext activation) {
        return ProtosExecution.createCallTarget(lowerer.lower(expression)).call(activation);
    }

    private CanonicalAssign assign(String name, String value) {
        return new CanonicalAssign(
                Optional.empty(),
                name,
                literal(value),
                new SourceSpan(0, 1));
    }

    private CanonicalLiteral literal(String value) {
        return new CanonicalLiteral(
                CanonicalLiteral.Kind.STRING,
                value,
                new SourceSpan(0, value.length()));
    }
}

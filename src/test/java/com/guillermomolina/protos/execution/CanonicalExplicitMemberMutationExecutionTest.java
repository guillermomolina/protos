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
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.ast.CanonicalAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CanonicalExplicitMemberMutationExecutionTest {
    private final CanonicalToTruffleLowerer lowerer = new CanonicalToTruffleLowerer();

    @Test
    void explicitCreationCreatesLocalSlotAndReturnsRhs() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        ProtosActivation activation =
                new ProtosActivation(new ProtosObjectValue(root), List.of(), receiver);

        Object result = execute(
                new CanonicalCreate(
                        Optional.of(new CanonicalIntrinsic(
                                CanonicalIntrinsic.Kind.THIS,
                                new SourceSpan(0, 4))),
                        "x",
                        literal("created"),
                        new SourceSpan(0, 10)),
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
                new ProtosActivation(new ProtosObjectValue(root), List.of(), receiver);

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute(
                                new CanonicalAssign(
                                        Optional.of(new CanonicalIntrinsic(
                                                CanonicalIntrinsic.Kind.THIS,
                                                new SourceSpan(0, 4))),
                                        "alive",
                                        literal("nope"),
                                        new SourceSpan(0, 10)),
                                activation));

        assertSame(ProtosCoreErrors.errorPrototype(), signal.error().parent().orElseThrow());
        assertSame(ProtosBooleanValue.TRUE, prototype.readLocalSlot("alive").orElseThrow());
    }

    @Test
    void explicitAssignmentUpdatesLocalSlotAndReturnsRhs() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        receiver.createLocalSlot("name", ProtosBooleanValue.TRUE);
        ProtosActivation activation =
                new ProtosActivation(new ProtosObjectValue(root), List.of(), receiver);

        Object result = execute(
                new CanonicalAssign(
                        Optional.of(new CanonicalIntrinsic(
                                CanonicalIntrinsic.Kind.THIS,
                                new SourceSpan(0, 4))),
                        "name",
                        literal("updated"),
                        new SourceSpan(0, 12)),
                activation);

        assertSame(result, receiver.readLocalSlot("name").orElseThrow());
    }

    @Test
    void explicitMutationSignalsCoreErrorForNonOrdinaryRuntimeTargetInThisSlice() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue context = new ProtosObjectValue(root);
        context.createLocalSlot("value", ProtosBooleanValue.TRUE);
        ProtosActivation activation =
                new ProtosActivation(context, List.of(), new ProtosObjectValue(root));

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute(
                                new CanonicalCreate(
                                        Optional.of(new com.guillermomolina.protos.semantic.ast.CanonicalLookup(
                                                "value",
                                                new SourceSpan(0, 5))),
                                        "x",
                                        literal("created"),
                                        new SourceSpan(0, 10)),
                                activation));

        assertSame(ProtosCoreErrors.errorPrototype(), signal.error().parent().orElseThrow());
    }

    private Object execute(
            com.guillermomolina.protos.semantic.ast.CanonicalExpression expression,
            ProtosActivation activation) {
        return ProtosExecution.createCallTarget(lowerer.lower(expression)).call(activation);
    }

    private CanonicalLiteral literal(String value) {
        return new CanonicalLiteral(
                CanonicalLiteral.Kind.STRING,
                value,
                new SourceSpan(0, value.length()));
    }
}

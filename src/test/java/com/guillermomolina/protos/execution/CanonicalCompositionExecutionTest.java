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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosTestPrelude;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalCompositionExecutionTest {
    private final Canonicalizer canonicalizer = new Canonicalizer();
    private final CanonicalToTruffleLowerer lowerer =
            new CanonicalToTruffleLowerer();

    @Test
    void compositionCopiesLocalBindingsAndMakesThemVisibleToLaterItems() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue source = new ProtosObjectValue(root);
        source.createLocalSlot("inherited", ProtosBooleanValue.TRUE);
        ProtosObjectValue context = new ProtosObjectValue(root);
        context.createLocalSlot("source", source);
        ProtosActivation activation =
                ProtosTestPrelude.activation(
                        context,
                        List.of(),
                        new ProtosObjectValue(root));

        ProtosObjectValue object =
                (ProtosObjectValue)
                        execute(
                                "{ ...source\n"
                                        + "copied: inherited }",
                                activation);

        assertSame(
                ProtosBooleanValue.TRUE,
                object.readLocalSlot("inherited").orElseThrow());
        assertSame(
                ProtosBooleanValue.TRUE,
                object.readLocalSlot("copied").orElseThrow());
    }

    @Test
    void directLocalDeclarationReservesNameRegardlessOfTextualPosition() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue source = new ProtosObjectValue(root);
        source.createLocalSlot("reserved", ProtosBooleanValue.TRUE);
        source.createLocalSlot("kept", ProtosBooleanValue.TRUE);
        ProtosObjectValue context = new ProtosObjectValue(root);
        context.createLocalSlot("source", source);
        ProtosActivation activation =
                ProtosTestPrelude.activation(
                        context,
                        List.of(),
                        new ProtosObjectValue(root));

        ProtosObjectValue object =
                (ProtosObjectValue)
                        execute(
                                "{ ...source\n"
                                        + "reserved: false }",
                                activation);

        assertSame(
                ProtosBooleanValue.FALSE,
                object.readLocalSlot("reserved").orElseThrow());
        assertSame(
                ProtosBooleanValue.TRUE,
                object.readLocalSlot("kept").orElseThrow());
    }

    @Test
    void compositionConflictSignalsCoreError() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue first = new ProtosObjectValue(root);
        first.createLocalSlot("same", ProtosBooleanValue.TRUE);
        ProtosObjectValue second = new ProtosObjectValue(root);
        second.createLocalSlot("same", ProtosBooleanValue.FALSE);
        ProtosObjectValue context = new ProtosObjectValue(root);
        context.createLocalSlot("first", first);
        context.createLocalSlot("second", second);
        ProtosActivation activation =
                ProtosTestPrelude.activation(
                        context,
                        List.of(),
                        new ProtosObjectValue(root));

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                execute(
                                        "{ ...first\n"
                                                + "...second }",
                                        activation));

        assertSame(
                ProtosTestPrelude.errorPrototype(),
                signal.error().parent().orElseThrow());
    }

    @Test
    void nonOrdinaryCompositionSourceSignalsCoreError() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosActivation activation =
                ProtosTestPrelude.activation(
                        new ProtosObjectValue(root),
                        List.of(),
                        new ProtosObjectValue(root));

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute("{ ...true }", activation));

        assertSame(
                ProtosTestPrelude.errorPrototype(),
                signal.error().parent().orElseThrow());
    }

    private Object execute(
            String source,
            ProtosActivation activation) {
        CanonicalExpression expression =
                canonicalizer.canonicalize(
                        new ProtosParser(source)
                                .parseProgram()
                                .expressions()
                                .get(0));
        return ProtosExecution.createCallTarget(lowerer.lower(expression))
                .call(activation);
    }
}

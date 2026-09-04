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
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalLookupExecutionTest {
    private final CanonicalToTruffleLowerer lowerer = new CanonicalToTruffleLowerer();

    @Test
    void canonicalLookupReadsCurrentContext() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue current = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        Object value = new ProtosStringValue("local");
        current.createLocalSlot("name", value);

        assertSame(value, execute(lookup("name"), activation(current, List.of(), receiver)));
    }

    @Test
    void canonicalLookupUsesCapturedLexicalContextsBeforeReceiver() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue current = new ProtosObjectValue(root);
        ProtosObjectValue captured = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        Object lexicalValue = new ProtosStringValue("lexical");

        captured.createLocalSlot("name", lexicalValue);
        receiver.createLocalSlot("name", ProtosBooleanValue.TRUE);

        assertSame(
                lexicalValue,
                execute(lookup("name"), activation(current, List.of(captured), receiver)));
    }

    @Test
    void canonicalLookupFallsThroughToReceiverDelegation() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue prototype = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(prototype);
        ProtosObjectValue current = new ProtosObjectValue(root);
        Object inherited = new ProtosStringValue("inherited");
        prototype.createLocalSlot("name", inherited);

        assertSame(inherited, execute(lookup("name"), activation(current, List.of(), receiver)));
    }

    @Test
    void missingLookupSignalsCoreErrorWithoutInventingLookupErrorPrototype() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosActivation activation =
                activation(new ProtosObjectValue(root), List.of(), new ProtosObjectValue(root));

        ProtosSignalException signal =
                assertThrows(ProtosSignalException.class, () -> execute(lookup("missing"), activation));

        assertSame(
                ProtosTestPrelude.errorPrototype(),
                signal.error().parent().orElseThrow());
    }

    private Object execute(CanonicalLookup expression, ProtosActivation activation) {
        return ProtosExecution.createCallTarget(lowerer.lower(expression)).call(activation);
    }

    private CanonicalLookup lookup(String name) {
        return new CanonicalLookup(name, new SourceSpan(0, name.length()));
    }

    private ProtosActivation activation(
            ProtosObjectValue current,
            List<ProtosObjectValue> captured,
            ProtosObjectValue receiver) {
        return ProtosTestPrelude.activation(current, captured, receiver);
    }
}

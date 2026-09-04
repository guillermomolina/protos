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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.guillermomolina.protos.semantic.ast.CanonicalMember;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractedClosureBindingExecutionTest {
    private final CanonicalToTruffleLowerer lowerer = new CanonicalToTruffleLowerer();

    @Test
    void inheritedClosureReadBindsDynamicReceiverAndLookupHome() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue animal = new ProtosObjectValue(root);
        ProtosObjectValue dog = new ProtosObjectValue(animal);
        ProtosObjectValue lexical = new ProtosObjectValue(root);
        ProtosObjectValue definitionReceiver = new ProtosObjectValue(root);

        ProtosClosureValue stored =
                new ProtosClosureValue(closure(), List.of(lexical), definitionReceiver);
        animal.createLocalSlot("speak", stored);

        ProtosClosureValue extracted =
                (ProtosClosureValue) execute(member("speak"), activation(dog));

        assertSame(dog, extracted.capturedReceiver());
        assertSame(animal, extracted.methodHome().orElseThrow());
        assertSame(lexical, extracted.capturedLexicalContexts().get(0));
        assertSame(stored.definition(), extracted.definition());
    }

    @Test
    void extractingClosureDoesNotMutateStoredClosureBinding() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue home = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(home);
        ProtosClosureValue stored =
                new ProtosClosureValue(closure(), List.of(), home);
        home.createLocalSlot("f", stored);

        ProtosClosureValue extracted =
                (ProtosClosureValue) execute(member("f"), activation(receiver));

        assertSame(home, stored.capturedReceiver());
        assertTrue(stored.methodHome().isEmpty());
        assertSame(receiver, extracted.capturedReceiver());
        assertSame(home, extracted.methodHome().orElseThrow());
    }

    private CanonicalMember member(String name) {
        return new CanonicalMember(
                new CanonicalIntrinsic(CanonicalIntrinsic.Kind.THIS, new SourceSpan(0, 4)),
                name,
                new SourceSpan(0, name.length() + 5));
    }

    private CanonicalClosure closure() {
        SourceSpan span = new SourceSpan(0, 2);
        return new CanonicalClosure(
                List.of(),
                new CanonicalSequence(List.of(), span),
                span);
    }

    private ProtosActivation activation(ProtosObjectValue receiver) {
        return new ProtosActivation(
                new ProtosObjectValue(ProtosObjectValue.rootObject()),
                List.of(),
                receiver);
    }

    private Object execute(
            CanonicalMember expression,
            ProtosActivation activation) {
        return ProtosExecution.createCallTarget(lowerer.lower(expression)).call(activation);
    }
}

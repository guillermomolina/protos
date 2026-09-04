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

package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosClosureInvocationActivationTest {
    private static final SourceSpan SPAN = new SourceSpan(0, 0);

    private static CanonicalClosure closureDefinition() {
        return new CanonicalClosure(List.of(), new CanonicalSequence(List.of(), SPAN), SPAN);
    }

    @Test
    void topLevelClosureInvocationEstablishesCompleteFreshActivationState() {
        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue errorPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue arrayPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings = new ProtosObjectValue(contextPrototype);
        bindings.createLocalSlot("Context", contextPrototype);
        bindings.createLocalSlot("Error", errorPrototype);
        bindings.createLocalSlot("Array", arrayPrototype);
        bindings.freeze();
        ProtosPrelude prelude = new ProtosPrelude(bindings, contextPrototype);

        ProtosObjectValue lexical =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object suppliedValue = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosClosureValue closure =
                new ProtosClosureValue(
                        closureDefinition(),
                        List.of(lexical),
                        receiver,
                        null,
                        null,
                        prelude);

        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(closure, List.of(suppliedValue));

        assertSame(contextPrototype, activation.context().parent().orElseThrow());
        assertSame(lexical, activation.capturedLexicalContexts().get(0));
        assertSame(receiver, activation.receiver());
        assertSame(prelude, activation.prelude().orElseThrow());
        assertTrue(activation.ownsReturnHome());
        assertTrue(activation.returnHome().orElseThrow().isActive());
        ProtosArrayValue args = activation.arguments().orElseThrow();
        assertSame(arrayPrototype, args.parent().orElseThrow());
        assertSame(ProtosObjectValue.MutationState.FROZEN, args.mutationState());
        assertSame(suppliedValue, args.indexedAt(java.math.BigInteger.ZERO));
    }

    @Test
    void nestedClosureInvocationReusesCapturedReturnHome() {
        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings = new ProtosObjectValue(contextPrototype);
        bindings.createLocalSlot("Context", contextPrototype);
        bindings.createLocalSlot(
                "Error", new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.createLocalSlot(
                "Array", new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.freeze();
        ProtosPrelude prelude = new ProtosPrelude(bindings, contextPrototype);
        ProtosReturnHome capturedHome = new ProtosReturnHome();

        ProtosClosureValue closure =
                new ProtosClosureValue(
                        closureDefinition(),
                        List.of(),
                        new ProtosObjectValue(ProtosObjectValue.rootObject()),
                        null,
                        capturedHome,
                        prelude);

        ProtosActivation activation =
                ProtosActivation.forClosureInvocation(closure, List.of());

        assertSame(capturedHome, activation.returnHome().orElseThrow());
        assertFalse(activation.ownsReturnHome());
    }
}

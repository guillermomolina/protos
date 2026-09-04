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

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ProtosPreludeTest {
    @Test
    void createsFreshExecutionContextsFromFrozenPreludeBindings() {
        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings = new ProtosObjectValue(contextPrototype);
        bindings.createLocalSlot("Context", contextPrototype);
        bindings.freeze();
        ProtosPrelude prelude =
                new ProtosPrelude(bindings, contextPrototype);

        ProtosObjectValue first = prelude.newExecutionContext();
        ProtosObjectValue second = prelude.newExecutionContext();

        assertSame(bindings, prelude.bindings());
        assertSame(contextPrototype, prelude.contextPrototype());
        assertNotSame(first, second);
        assertSame(contextPrototype, first.parent().orElseThrow());
        assertSame(contextPrototype, second.parent().orElseThrow());
    }

    @Test
    void moduleActivationCapturesFrozenPreludeForOrdinaryLexicalLookup() {
        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings = new ProtosObjectValue(contextPrototype);
        bindings.createLocalSlot("Context", contextPrototype);
        bindings.freeze();
        ProtosPrelude prelude =
                new ProtosPrelude(bindings, contextPrototype);

        ProtosActivation activation = prelude.newModuleActivation();

        assertSame(
                contextPrototype,
                activation.lookup("Context").orElseThrow());
        assertSame(
                bindings,
                activation.capturedLexicalContexts().get(0));
        assertSame(
                contextPrototype,
                activation.context().parent().orElseThrow());
    }
}

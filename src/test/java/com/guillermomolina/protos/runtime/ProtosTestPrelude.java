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

import java.util.List;

public final class ProtosTestPrelude {
    private static final ProtosPrelude PRELUDE = createPrelude();

    private ProtosTestPrelude() {}

    private static ProtosPrelude createPrelude() {
        ProtosObjectValue context =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue error =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings = new ProtosObjectValue(context);
        bindings.createLocalSlot("Context", context);
        bindings.createLocalSlot("Error", error);
        bindings.createLocalSlot(
                "SlotNotFound",
                new ProtosObjectValue(error));
        bindings.createLocalSlot(
                "Array",
                new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.freeze();
        return new ProtosPrelude(bindings, context);
    }

    public static ProtosActivation activation(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object receiver) {
        return ProtosActivation.withPrelude(
                context, capturedLexicalContexts, receiver, PRELUDE);
    }

    public static ProtosObjectValue errorPrototype() {
        return PRELUDE.errorPrototype();
    }

    public static ProtosObjectValue slotNotFoundPrototype() {
        Object binding = PRELUDE.bindings().readLocalSlot("SlotNotFound").orElseThrow();
        if (!(binding instanceof ProtosObjectValue prototype)) {
            throw new IllegalStateException("test SlotNotFound binding is not an ordinary object");
        }
        return prototype;
    }
}

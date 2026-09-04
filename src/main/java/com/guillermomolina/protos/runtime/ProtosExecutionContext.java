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
import java.util.Objects;
import java.util.Optional;

public final class ProtosExecutionContext {
    private final ProtosObjectValue context;
    private final List<ProtosObjectValue> capturedLexicalContexts;
    private final ProtosObjectValue receiver;

    public ProtosExecutionContext(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            ProtosObjectValue receiver) {
        this.context = Objects.requireNonNull(context, "context");
        this.capturedLexicalContexts =
                List.copyOf(Objects.requireNonNull(
                        capturedLexicalContexts, "capturedLexicalContexts"));
        this.receiver = Objects.requireNonNull(receiver, "receiver");
    }

    public ProtosObjectValue context() {
        return context;
    }

    public List<ProtosObjectValue> capturedLexicalContexts() {
        return capturedLexicalContexts;
    }

    public ProtosObjectValue receiver() {
        return receiver;
    }

    public Optional<Object> lookup(String name) {
        Objects.requireNonNull(name, "name");

        Optional<Object> current = context.readLocalSlot(name);
        if (current.isPresent()) {
            return current;
        }

        for (ProtosObjectValue lexicalContext : capturedLexicalContexts) {
            Optional<Object> captured = lexicalContext.readLocalSlot(name);
            if (captured.isPresent()) {
                return captured;
            }
        }

        return receiver.readSlot(name);
    }

    public Optional<ProtosObjectValue> writableLexicalContext(String name) {
        Objects.requireNonNull(name, "name");

        if (context.hasLocalSlot(name)) {
            return Optional.of(context);
        }

        for (ProtosObjectValue lexicalContext : capturedLexicalContexts) {
            if (lexicalContext.hasLocalSlot(name)) {
                return Optional.of(lexicalContext);
            }
        }

        if (receiver.hasLocalSlot(name)) {
            return Optional.of(receiver);
        }

        return Optional.empty();
    }
}

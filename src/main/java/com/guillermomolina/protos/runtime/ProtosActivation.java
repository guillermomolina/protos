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

public final class ProtosActivation {
    private final ProtosObjectValue context;
    private final List<ProtosObjectValue> capturedLexicalContexts;
    private final Object receiver;
    private final ProtosPrelude prelude;
    private final boolean construction;

    public ProtosActivation(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object receiver) {
        this(context, capturedLexicalContexts, receiver, null, false);
    }

    static ProtosActivation withPrelude(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object receiver,
            ProtosPrelude prelude) {
        return new ProtosActivation(
                context,
                capturedLexicalContexts,
                receiver,
                Objects.requireNonNull(prelude, "prelude"),
                false);
    }

    private ProtosActivation(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object receiver,
            ProtosPrelude prelude,
            boolean construction) {
        this.context = Objects.requireNonNull(context, "context");
        this.capturedLexicalContexts =
                List.copyOf(Objects.requireNonNull(
                        capturedLexicalContexts, "capturedLexicalContexts"));
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.prelude = prelude;
        this.construction = construction;
    }

    public static ProtosActivation forObjectConstruction(
            ProtosObjectValue object,
            ProtosActivation enclosing) {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(enclosing, "enclosing");
        return new ProtosActivation(
                object,
                enclosing.lexicalContextsForClosureCapture(),
                object,
                enclosing.prelude,
                true);
    }

    public ProtosObjectValue context() {
        return context;
    }

    public List<ProtosObjectValue> capturedLexicalContexts() {
        return capturedLexicalContexts;
    }

    public Object receiver() {
        return receiver;
    }

    public Optional<ProtosPrelude> prelude() {
        return Optional.ofNullable(prelude);
    }

    public List<ProtosObjectValue> lexicalContextsForClosureCapture() {
        if (construction) {
            return capturedLexicalContexts;
        }

        java.util.ArrayList<ProtosObjectValue> contexts =
                new java.util.ArrayList<>(1 + capturedLexicalContexts.size());
        contexts.add(context);
        contexts.addAll(capturedLexicalContexts);
        return List.copyOf(contexts);
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

        if (receiver instanceof ProtosObjectValue ordinaryReceiver) {
            return ordinaryReceiver.readSlot(name);
        }

        throw new UnsupportedOperationException(
                "Lookup on non-ordinary Protos receivers requires standard prototype bootstrap");
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

        if (receiver instanceof ProtosObjectValue ordinaryReceiver
                && ordinaryReceiver.hasLocalSlot(name)) {
            return Optional.of(ordinaryReceiver);
        }

        return Optional.empty();
    }
}

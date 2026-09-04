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
    private final ProtosArrayValue arguments;
    private final ProtosReturnHome returnHome;
    private final ProtosObjectValue methodHome;
    private final boolean ownsReturnHome;
    private final boolean construction;

    public ProtosActivation(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object receiver) {
        this(context, capturedLexicalContexts, receiver, null, null, null, null, false, false);
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
                null,
                null,
                null,
                false,
                false);
    }

    public static ProtosActivation withReturnHome(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object receiver,
            ProtosReturnHome returnHome) {
        return new ProtosActivation(
                context,
                capturedLexicalContexts,
                receiver,
                null,
                null,
                Objects.requireNonNull(returnHome, "returnHome"),
                null,
                false,
                false);
    }

    public static ProtosActivation withMethodHome(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object receiver,
            ProtosObjectValue methodHome) {
        return new ProtosActivation(
                context,
                capturedLexicalContexts,
                receiver,
                null,
                null,
                null,
                Objects.requireNonNull(methodHome, "methodHome"),
                false,
                false);
    }

    public static ProtosActivation forClosureInvocation(
            ProtosClosureValue closure,
            java.util.List<?> supplied) {
        return forClosureInvocation(closure, supplied, null);
    }

    public static ProtosActivation forClosureInvocation(
            ProtosClosureValue closure,
            java.util.List<?> supplied,
            ProtosPrelude fallbackPrelude) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(supplied, "supplied");

        ProtosPrelude prelude = closure.prelude().orElse(fallbackPrelude);
        if (prelude == null) {
            throw new IllegalStateException("Closure invocation requires an owning Core prelude");
        }
        ProtosReturnHome capturedHome = closure.returnHome().orElse(null);
        boolean ownsReturnHome = capturedHome == null;
        ProtosReturnHome invocationHome =
                ownsReturnHome ? new ProtosReturnHome() : capturedHome;

        return new ProtosActivation(
                prelude.newExecutionContext(),
                closure.capturedLexicalContexts(),
                closure.capturedReceiver(),
                prelude,
                prelude.newFrozenArray(supplied),
                invocationHome,
                closure.methodHome().orElse(null),
                ownsReturnHome,
                false);
    }

    private ProtosActivation(
            ProtosObjectValue context,
            List<ProtosObjectValue> capturedLexicalContexts,
            Object receiver,
            ProtosPrelude prelude,
            ProtosArrayValue arguments,
            ProtosReturnHome returnHome,
            ProtosObjectValue methodHome,
            boolean ownsReturnHome,
            boolean construction) {
        this.context = Objects.requireNonNull(context, "context");
        this.capturedLexicalContexts =
                List.copyOf(Objects.requireNonNull(
                        capturedLexicalContexts, "capturedLexicalContexts"));
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.prelude = prelude;
        this.arguments = arguments;
        this.returnHome = returnHome;
        this.methodHome = methodHome;
        this.ownsReturnHome = ownsReturnHome;
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
                enclosing.arguments,
                enclosing.returnHome,
                enclosing.methodHome,
                false,
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

    public Optional<ProtosArrayValue> arguments() {
        return Optional.ofNullable(arguments);
    }

    public Optional<ProtosReturnHome> returnHome() {
        return Optional.ofNullable(returnHome);
    }

    public Optional<ProtosObjectValue> methodHome() {
        return Optional.ofNullable(methodHome);
    }

    public boolean ownsReturnHome() {
        return ownsReturnHome;
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

        return ProtosValueLookup.lookup(receiver, name, prelude)
                .map(ProtosSlotLookupResult::value);
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

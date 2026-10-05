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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Objects;
import java.util.Optional;

/**
 * Exact name-based lexical fallback used when static frame-backed binding
 * identity and presence are not sufficient to select the authoritative path.
 *
 * <p>This helper owns no lexical values and introduces no additional binding
 * authority. It traverses the semantic activation topology already retained by
 * {@link ProtosActivation}: current context, captured genuine lexical contexts
 * (through {@link ProtosLexicalEnvironment}, without materializing deferred
 * contexts), and finally the receiver according to the applicable read/write
 * rule.
 *
 * <p>Statically proven current and captured bindings are expected to bypass
 * this fallback through the Bytecode DSL frame-backed paths established by
 * PLAT036 / I068.
 */
public final class ProtosLexicalFallback {
    private ProtosLexicalFallback() {}

    /**
     * Exact residual bare-name read: lexical local slots first, then ordinary
     * receiver lookup.
     *
     * <p>TEST009-E: a host boundary. This String-keyed walk is the residual
     * path that statically proven reads bypass; inlined, its loops and the
     * receiver delegation lookup were expanded into every compiled read site.
     */
    @TruffleBoundary
    public static Optional<Object> readByName(
            ProtosActivation activation,
            String name) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(name, "name");

        Optional<Object> current =
                activation.readCurrentLocalSlotForRuntime(name);
        if (current.isPresent()) {
            return current;
        }

        for (ProtosLexicalEnvironment lexical =
                        activation.capturedLexicalEnvironmentForRuntime();
                lexical != null;
                lexical = lexical.outer()) {
            Optional<Object> captured =
                    lexical.readLocalSlotForRuntime(name);
            if (captured.isPresent()) {
                return captured;
            }
        }

        return ProtosValueLookup.readMember(
                activation.receiver(),
                name,
                activation.prelude().orElse(null));
    }

    /**
     * Exact residual bare-assignment destination resolution. Reads only local
     * membership and never follows receiver delegation.
     *
     * <p>TEST009-K: a host boundary, like {@link #readByName}. Callers reach
     * this String-keyed walk only after frame-backed selection did not apply.
     */
    @TruffleBoundary
    public static Optional<ProtosObjectValue> writableContextByName(
            ProtosActivation activation,
            String name) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(name, "name");

        if (activation.currentContextHasLocalSlotForRuntime(name)) {
            return Optional.of(activation.context());
        }

        for (ProtosLexicalEnvironment lexical =
                        activation.capturedLexicalEnvironmentForRuntime();
                lexical != null;
                lexical = lexical.outer()) {
            if (lexical.hasLocalSlotForRuntime(name)) {
                // The selected write destination is an observed context.
                return Optional.of(lexical.context());
            }
        }

        if (activation.receiver() instanceof ProtosObjectValue ordinaryReceiver
                && ordinaryReceiver.hasLocalSlot(name)) {
            return Optional.of(ordinaryReceiver);
        }

        return Optional.empty();
    }
}

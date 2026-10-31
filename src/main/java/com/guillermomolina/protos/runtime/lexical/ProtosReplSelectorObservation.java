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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * D228-A1: names-only observation of the selectors visible from one REPL receiver.
 *
 * <p>This is the only runtime component that discovers selector names for REPL TAB completion.
 * The caller must already hold the Process/RootActor admission established by the host; this
 * class never acquires authority on its own.</p>
 *
 * <p>The receiver is identified by exactly one transient read: the nearest PRESENT lexical binding
 * of an admitted name, or the activation receiver for {@code this}. No guest expression, getter,
 * Closure, import or method is executed, and the values of enumerated slots are never read.
 * Names come from each node's own local-slot authority, following ordinary delegation through
 * {@link ProtosValueLookup#delegationParent} up to and including the root Object. The result holds
 * only inert Java data.</p>
 */
public final class ProtosReplSelectorObservation {
    public static final int MAX_DELEGATION_NODES = 64;
    public static final int MAX_EXAMINED_NAMES = 1024;

    /** Syntactic receiver identity already proven by the static analyzer. */
    public enum ReceiverKind {
        LEXICAL_NAME,
        THIS
    }

    /** Whether the nearest definition of a name is on the receiver itself or on an ancestor. */
    public enum Provenance {
        OWN,
        DELEGATED
    }

    public record Receiver(ReceiverKind kind, String lexicalName) {
        public Receiver {
            Objects.requireNonNull(kind, "kind");
            if ((kind == ReceiverKind.LEXICAL_NAME) != (lexicalName != null)) {
                throw new IllegalArgumentException(
                        "a lexical receiver requires exactly one name; this has none");
            }
        }

        public static Receiver lexicalName(String name) {
            return new Receiver(ReceiverKind.LEXICAL_NAME, Objects.requireNonNull(name, "name"));
        }

        public static Receiver thisReceiver() {
            return new Receiver(ReceiverKind.THIS, null);
        }
    }

    public record Candidate(String name, Provenance provenance) {
        public Candidate {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(provenance, "provenance");
        }
    }

    private ProtosReplSelectorObservation() {}

    /**
     * Observes the selector names of {@code receiver} in {@code activation}.
     *
     * <p>Within each delegation level names are ordered by Unicode scalar value; a name keeps only
     * its nearest level. Any absent binding, unsupported or foreign representation, cycle, or
     * exceeded budget yields an empty list: a partial result is never returned.</p>
     */
    @TruffleBoundary
    public static List<Candidate> observe(ProtosActivation activation, Receiver receiver) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(receiver, "receiver");

        Optional<Object> resolved = switch (receiver.kind()) {
            case THIS -> Optional.ofNullable(activation.receiver());
            case LEXICAL_NAME -> readNearestLexicalBinding(activation, receiver.lexicalName());
        };
        if (resolved.isEmpty() || !admissible(resolved.orElseThrow())) {
            return List.of();
        }
        return enumerate(resolved.orElseThrow(), activation.preludeOrNullForRuntime());
    }

    /*
     * Reads exactly one PRESENT binding: the current scope first, then the authentic captured
     * lexical chain. No implicit fallback to the receiver, and no guest Context is materialized.
     */
    private static Optional<Object> readNearestLexicalBinding(
            ProtosActivation activation, String name) {
        if (activation.currentContextHasLocalSlotForRuntime(name)) {
            return activation.readCurrentLocalSlotForRuntime(name);
        }
        for (ProtosLexicalEnvironment scope = activation.capturedLexicalEnvironmentForRuntime();
                scope != null;
                scope = scope.outer()) {
            if (scope.hasLocalSlotForRuntime(name)) {
                return scope.readLocalSlotForRuntime(name);
            }
        }
        return Optional.empty();
    }

    /* Only ordinary Protos objects and specialized Protos representations; never foreign. */
    private static boolean admissible(Object node) {
        return !(node instanceof ProtosForeignProjectedReceiver)
                && (node instanceof ProtosObjectValue || node instanceof ProtosRepresentedValue);
    }

    private static List<Candidate> enumerate(Object origin, ProtosPrelude prelude) {
        // Identity is used only to detect delegation cycles, never as Protos equality.
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        HashSet<String> seen = new HashSet<>();
        ArrayList<Candidate> candidates = new ArrayList<>();
        ArrayList<String> level = new ArrayList<>();
        int examined = 0;

        Object current = origin;
        while (true) {
            if (!admissible(current)
                    || visited.size() >= MAX_DELEGATION_NODES
                    || visited.put(current, Boolean.TRUE) != null) {
                return List.of();
            }
            if (current instanceof ProtosObjectValue ordinary) {
                level.clear();
                if (!ordinary.appendLocalBindingNamesToBounded(
                        level, MAX_EXAMINED_NAMES - examined)) {
                    return List.of();
                }
                examined += level.size();
                level.sort(ProtosReplSelectorObservation::compareScalarValues);
                Provenance provenance = current == origin ? Provenance.OWN : Provenance.DELEGATED;
                for (String name : level) {
                    if (seen.add(name)) {
                        candidates.add(new Candidate(name, provenance));
                    }
                }
            }
            // A specialized representation without an ordinary local table contributes no names
            // of its own; it only delegates to its semantic parent.

            Optional<Object> parent = ProtosValueLookup.delegationParent(current, prelude);
            if (parent.isEmpty()) {
                return Collections.unmodifiableList(candidates);
            }
            current = parent.orElseThrow();
        }
    }

    /** Orders by Unicode scalar value, independent of locale and of UTF-16 surrogate order. */
    static int compareScalarValues(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            int leftCodePoint = left.codePointAt(leftIndex);
            int rightCodePoint = right.codePointAt(rightIndex);
            if (leftCodePoint != rightCodePoint) {
                return Integer.compare(leftCodePoint, rightCodePoint);
            }
            leftIndex += Character.charCount(leftCodePoint);
            rightIndex += Character.charCount(rightCodePoint);
        }
        return Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
    }
}

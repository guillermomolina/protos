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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * PERF025 internal representation of one captured genuine lexical execution
 * context together with its outer lexical chain.
 *
 * <p>{@code CALLABLES.md} requires a Closure to capture its genuine lexical
 * execution contexts by reference; it does not require the guest
 * {@code Context} objects to exist when the Closure is created. A node is
 * therefore either already bound to its materialized context object, or still
 * owned by the activation whose context remains deferred (PLAT040). In the
 * deferred state every membership/read query is answered through that
 * activation's context, so no value is ever copied here and no second
 * binding store exists.
 *
 * <p>I115: an environment node carries only NAME bindings (the module
 * context and other materialized contexts). A Closure's LOCAL bindings never
 * enter it, and its captured CELL bindings are carried by the Closure value
 * itself as a constant-indexed array ({@link
 * ProtosClosureValue#capturedCellsForRuntime}), never looked up here by name.
 * The cells are not attached to environment nodes because a Closure that
 * does not capture its creator's context reuses the creator's own chain, so a
 * node's position does not identify the lexical owner of a cell.
 *
 * <p>Each activation creates at most one node for its own current context and
 * shares it, by reference, with every Closure it materializes and with the
 * Closure activations derived from them. Materialization is delegated to the
 * owning activation's {@link ProtosActivation#context()}, so every observer
 * (the {@code context} intrinsic, an escaped Closure, reflection, the
 * debugger, a generic write) sees the same single context identity.
 *
 * <p>Nodes are immutable apart from the one-way deferred-to-materialized
 * transition. Like {@link ProtosActivation} itself, the transition is
 * confined to the owning Actor-local execution.
 */
public final class ProtosLexicalEnvironment {
    private ProtosActivation deferredOwner;
    private ProtosObjectValue context;
    private final ProtosLexicalEnvironment outer;

    private ProtosLexicalEnvironment(
            ProtosActivation deferredOwner,
            ProtosObjectValue context,
            ProtosLexicalEnvironment outer) {
        this.deferredOwner = deferredOwner;
        this.context = context;
        this.outer = outer;
    }

    /**
     * Resolve the exact compact owner while its invocation is still live.
     * The activation-owned node is shared by all captures and leaves the guest
     * Context deferred. Retaining only arguments plus a future stack resolver
     * would lose caller provenance as soon as the creator returns.
     */
    public static ProtosLexicalEnvironment deferredCompactOwner(
            Object[] arguments,
            Function<Object[], ProtosActivation> resolver) {
        ProtosActivation owner = Objects.requireNonNull(
                Objects.requireNonNull(resolver, "resolver")
                        .apply(Objects.requireNonNull(arguments, "arguments")),
                "resolved owner");
        return owner.lexicalEnvironmentForClosureCapture();
    }

    private ProtosActivation resolveDeferredOwner() {
        return Objects.requireNonNull(deferredOwner, "deferred owner");
    }

    /** A node whose context object already exists. */
    static ProtosLexicalEnvironment materialized(
            ProtosObjectValue context,
            ProtosLexicalEnvironment outer) {
        return new ProtosLexicalEnvironment(
                null, Objects.requireNonNull(context, "context"), outer);
    }

    /** A node for an activation whose context object is still deferred. */
    static ProtosLexicalEnvironment deferred(
            ProtosActivation owner,
            ProtosLexicalEnvironment outer) {
        return new ProtosLexicalEnvironment(
                Objects.requireNonNull(owner, "owner"), null, outer);
    }

    /**
     * Cold compatibility conversion from an explicit innermost-first context
     * list. Returns {@code null} for the empty chain.
     */
    public static ProtosLexicalEnvironment ofContexts(
            List<ProtosObjectValue> contexts) {
        Objects.requireNonNull(contexts, "contexts");
        ProtosLexicalEnvironment chain = null;
        for (int index = contexts.size() - 1; index >= 0; index--) {
            chain = materialized(contexts.get(index), chain);
        }
        return chain;
    }

    /**
     * Cold compatibility projection of a chain into its innermost-first
     * context objects. Materializes every node; never used on the ordinary
     * Closure creation or captured-access paths.
     */
    public static List<ProtosObjectValue> contextsOf(
            ProtosLexicalEnvironment chain) {
        if (chain == null) {
            return List.of();
        }
        ArrayList<ProtosObjectValue> contexts = new ArrayList<>();
        for (ProtosLexicalEnvironment node = chain; node != null; node = node.outer) {
            contexts.add(node.context());
        }
        return Collections.unmodifiableList(contexts);
    }

    /** Returns the node {@code distance} steps outward, or {@code null}. */
    public static ProtosLexicalEnvironment at(
            ProtosLexicalEnvironment chain,
            int distance) {
        ProtosLexicalEnvironment node = chain;
        for (int step = 0; node != null && step < distance; step++) {
            node = node.outer;
        }
        return node;
    }

    public ProtosLexicalEnvironment outer() {
        return outer;
    }

    /** Materializes (once) and returns this scope's semantic context object. */
    public ProtosObjectValue context() {
        ProtosObjectValue existing = context;
        if (existing != null) {
            return existing;
        }
        ProtosObjectValue materialized = resolveDeferredOwner().context();
        context = materialized;
        deferredOwner = null;
        return materialized;
    }

    /**
     * PERF037-D: this scope's guest context when it already exists, else
     * {@code null}; never materializes it.
     */
    public ProtosObjectValue materializedContextOrNullForRuntime() {
        return context;
    }

    /**
     * PERF037-D: the activation owning this still-deferred scope.
     * Precondition: {@link #materializedContextOrNullForRuntime} returned
     * {@code null}.
     */
    public ProtosActivation deferredOwnerForRuntime() {
        return resolveDeferredOwner();
    }

    /** True while the guest context object has not been created yet. */
    public boolean isDeferredForRuntime() {
        return context == null;
    }

    /**
     * I115-S1c-L: whether this scope can hold any binding visible to name
     * selection: its Context exists, or its deferred owner has a Context or a
     * frame-backed authority. A scope that cannot hold bindings needs no
     * membership query. Reads only fields; never materializes anything.
     */
    public boolean mayHoldBindingsForRuntime() {
        if (context != null) {
            return true;
        }
        ProtosActivation owner = deferredOwner;
        return owner != null
                && (owner.materializedContextOrNullForRuntime() != null
                        || owner.publishedClosureLexicalAuthorityForRuntime() != null);
    }

    public boolean hasLocalSlotForRuntime(String name) {
        ProtosObjectValue existing = context;
        if (existing != null) {
            return existing.hasLocalSlot(name);
        }
        return resolveDeferredOwner().currentContextHasLocalSlotForRuntime(name);
    }

    public Optional<Object> readLocalSlotForRuntime(String name) {
        ProtosObjectValue existing = context;
        if (existing != null) {
            return existing.readLocalSlot(name);
        }
        return resolveDeferredOwner().readCurrentLocalSlotForRuntime(name);
    }

    /**
     * Assigns an existing binding of this scope without materializing a
     * deferred guest context: a materialized context applies its ordinary
     * object rules; a deferred one is answered by its owning activation.
     *
     * @throws IllegalStateException when the binding is absent or the
     *     context rejects the mutation
     */
    public void assignLocalSlotForRuntime(String name, Object value) {
        ProtosObjectValue existing = context;
        if (existing != null) {
            existing.assignLocalSlot(name, value);
            return;
        }
        resolveDeferredOwner().assignCurrentLocalSlotForRuntime(name, value);
    }

    /** An unmaterialized execution context cannot have been frozen by guest code. */
    public boolean isFrozenForRuntime() {
        ProtosObjectValue existing = context;
        return existing != null && existing.isFrozen();
    }
}

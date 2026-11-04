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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * PLAT036 Candidate D, Slice 1 backend-private compile-time model of one
 * owning lexical scope: the module root, one Closure activation, or one
 * Object-construction body.
 *
 * <p>{@code OBJECT_BODY} scopes are tracked distinctly from {@code ROOT}/
 * {@code CLOSURE} scopes because, per {@code EXECUTION_AND_CONTROL.md}
 * ("Object Construction Is Not a Lexical Capture Scope"), an object literal's
 * own locally created slots are visible to bare references made directly in
 * its own body, but are never captured by a Closure literal nested inside
 * that body: such a Closure's own outward search skips over the intervening
 * {@code OBJECT_BODY} scope(s) and lands on the nearest enclosing genuine
 * execution context, matching {@link
 * com.guillermomolina.protos.runtime.ProtosActivation#lexicalEnvironmentForClosureCapture()}.
 *
 * <p>Binding declarations ({@link #declare(String)}) are order-independent
 * (a scope's complete static name set), while establishment ({@link
 * #markEstablished(String)}) is progressive and must be driven by a
 * left-to-right, evaluation-order walk of exactly this scope's own body, so
 * that {@link CanonicalBindingAnalyzer} can tell "declared somewhere in this
 * scope" (a legal future/late creation candidate) apart from "already
 * PRESENT at this exact program point". Under D179 C0, that compile-time fact
 * identifies the stable binding/layout but does not imply permanent runtime
 * presence: a later removeSlot may clear the binding again.
 *
 * <p>I115: the scope also carries the compile-time storage classification
 * of its own bindings ({@link Storage}) and, for a {@code CLOSURE} scope,
 * the ordered list of outer cell bindings its Closure value must carry
 * ({@link #freeCells()}). {@link CanonicalBindingAnalyzer} completes both
 * before any Bytecode is emitted; lowering only reads them.
 */
final class CanonicalLexicalScope {
    enum Kind {
        ROOT,
        CLOSURE,
        OBJECT_BODY
    }

    /**
     * I115: the single storage of one declared binding, fixed at lowering
     * time. {@code NAME} is the module context, {@code MEMBER} the object
     * under construction, {@code LOCAL} a Bytecode DSL local and {@code CELL}
     * a shared {@code ProtosLexicalCell} held in a Bytecode DSL local.
     */
    enum Storage {
        NAME,
        MEMBER,
        LOCAL,
        CELL
    }

    private final Kind kind;
    private final CanonicalLexicalScope lexicalParent;
    private final Map<String, CanonicalBindingIdentity> declared = new LinkedHashMap<>();
    private final Set<String> establishedSoFar = new HashSet<>();
    private final Set<String> cellNames = new HashSet<>();
    private final Set<CanonicalBindingIdentity> freeCells = new LinkedHashSet<>();

    CanonicalLexicalScope(Kind kind, CanonicalLexicalScope lexicalParent) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.lexicalParent = lexicalParent;
    }

    Kind kind() {
        return kind;
    }

    /**
     * The nearest enclosing scope reachable when a reference inside this
     * scope does not resolve locally, skipping non-authoritative {@code
     * OBJECT_BODY} scopes. Returns {@code null} when no further static
     * enclosing scope is known (the module root, or a scope analyzed in
     * isolation via {@link CanonicalBindingAnalyzer#analyzeClosure}).
     */
    CanonicalLexicalScope outwardScope() {
        CanonicalLexicalScope candidate = lexicalParent;
        while (candidate != null && candidate.kind == Kind.OBJECT_BODY) {
            candidate = candidate.lexicalParent;
        }
        return candidate;
    }

    CanonicalBindingIdentity declare(String name) {
        Objects.requireNonNull(name, "name");
        return declared.computeIfAbsent(name, n -> new CanonicalBindingIdentity(n, this));
    }

    boolean declaresName(String name) {
        return declared.containsKey(name);
    }

    /**
     * PLAT036 Candidate D: every name this scope ever declares,
     * order-independent (matching {@link #declare(String)}). Used by the
     * lowerer to size a genuine execution-context's Bytecode-local layout.
     * Since I068 Slice 4 this includes Closure parameters; semantic presence
     * still begins only at each parameter's sequential binding point.
     */
    Set<String> declaredNames() {
        return Collections.unmodifiableSet(declared.keySet());
    }

    CanonicalBindingIdentity identity(String name) {
        CanonicalBindingIdentity identity = declared.get(name);
        if (identity == null) {
            throw new IllegalStateException(
                    "binding identity requested for a name this scope never declares: " + name);
        }
        return identity;
    }

    void markEstablished(String name) {
        if (!declared.containsKey(name)) {
            throw new IllegalStateException("cannot establish a name this scope never declares: " + name);
        }
        establishedSoFar.add(name);
    }

    boolean isEstablished(String name) {
        return establishedSoFar.contains(name);
    }

    /**
     * I115: marks a binding of this {@code CLOSURE} scope as referenced from
     * a nested Closure, so its storage is a shared cell. Module and object
     * body bindings never become cells.
     */
    void markCell(String name) {
        if (!declared.containsKey(name)) {
            throw new IllegalStateException("cannot mark an undeclared name as a cell: " + name);
        }
        if (kind != Kind.CLOSURE) {
            throw new IllegalStateException("only Closure bindings can be cells: " + name);
        }
        cellNames.add(name);
    }

    boolean needsCell(String name) {
        return cellNames.contains(name);
    }

    Storage storageOf(String name) {
        if (!declared.containsKey(name)) {
            throw new IllegalStateException("storage requested for an undeclared name: " + name);
        }
        return switch (kind) {
            case ROOT -> Storage.NAME;
            case OBJECT_BODY -> Storage.MEMBER;
            case CLOSURE -> cellNames.contains(name) ? Storage.CELL : Storage.LOCAL;
        };
    }

    /** I115: this scope's own cell bindings, in declaration order. */
    List<String> cellNamesInDeclarationOrder() {
        List<String> names = new ArrayList<>(cellNames.size());
        for (String name : declared.keySet()) {
            if (cellNames.contains(name)) {
                names.add(name);
            }
        }
        return Collections.unmodifiableList(names);
    }

    /**
     * I115: records an outer cell binding that this {@code CLOSURE} scope,
     * or a Closure nested in it, references. The insertion order is the
     * constant index of the cell in the Closure's captured-cell array.
     */
    void addFreeCell(CanonicalBindingIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        if (kind != Kind.CLOSURE || identity.owner() == this) {
            throw new IllegalStateException("invalid free cell: " + identity.name());
        }
        freeCells.add(identity);
    }

    /** I115 rematerialization: replaces the free cells with an equivalent remapped list. */
    void replaceFreeCells(List<CanonicalBindingIdentity> identities) {
        freeCells.clear();
        for (CanonicalBindingIdentity identity : identities) {
            addFreeCell(identity);
        }
    }

    List<CanonicalBindingIdentity> freeCells() {
        return List.copyOf(freeCells);
    }

    /** The constant captured-cell index of {@code identity}, or {@code -1}. */
    int freeCellIndex(CanonicalBindingIdentity identity) {
        int index = 0;
        for (CanonicalBindingIdentity candidate : freeCells) {
            if (candidate.equals(identity)) {
                return index;
            }
            index++;
        }
        return -1;
    }
}

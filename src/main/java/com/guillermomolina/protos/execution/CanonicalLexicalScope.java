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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

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
 * com.guillermomolina.protos.runtime.ProtosActivation#lexicalContextsForClosureCapture()}.
 *
 * <p>Binding declarations ({@link #declare(String)}) are order-independent
 * (a scope's complete static name set), while establishment ({@link
 * #markEstablished(String)}) is progressive and must be driven by a
 * left-to-right, evaluation-order walk of exactly this scope's own body, so
 * that {@link CanonicalBindingAnalyzer} can tell "declared somewhere in this
 * scope" (a legal future/late creation candidate) apart from "already
 * PRESENT at this exact program point" (D179 candidate C3 monotonic
 * membership already reached this point in this activation).
 */
final class CanonicalLexicalScope {
    enum Kind {
        ROOT,
        CLOSURE,
        OBJECT_BODY
    }

    private final Kind kind;
    private final CanonicalLexicalScope lexicalParent;
    private final Map<String, CanonicalBindingIdentity> declared = new LinkedHashMap<>();
    private final Set<String> establishedSoFar = new HashSet<>();

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
}

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

import java.util.Objects;

/**
 * PLAT036 Candidate D, Slice 1 static classification for one bare lexical
 * reference site (a {@code CanonicalLookup}, or the destination of a
 * target-empty {@code CanonicalAssign}).
 *
 * <p>Preserves {@code STATIC_BINDING_IDENTITY != SEMANTIC_PRESENCE}: knowing
 * which declaration a name would refer to is never conflated with knowing
 * that the binding is PRESENT at runtime. {@link Resolved} identifies
 * a binding already established at this program point, but under D179 C0 its
 * stable identity/layout does not guarantee that runtime presence survives a
 * later removeSlot. {@link Candidate} deliberately carries no presence
 * guarantee even though it carries the same kind of identity. {@link
 * CapturedResolved} identifies a statically established binding owned by an outer
 * genuine lexical scope; runtime lowering must still preserve nearer-context
 * retargeting before taking its direct captured path. {@link Dynamic} means
 * no scope in the static chain declares the name anywhere, so
 * the existing exact String-keyed runtime lookup/receiver-fallback path
 * remains the only correct resolution, unchanged by this slice.
 */
sealed interface CanonicalBindingResolution {
    /** The current scope already established this binding at this exact program point. */
    record Resolved(CanonicalBindingIdentity identity) implements CanonicalBindingResolution {
        public Resolved {
            Objects.requireNonNull(identity, "identity");
        }
    }

    /**
     * An already-PRESENT binding owned by an outer genuine lexical scope.
     *
     * <p>{@code lexicalDepth} counts genuine execution-context hops; object
     * construction scopes are skipped by {@link CanonicalLexicalScope#outwardScope()}.
     * The owner is stable, but a nearer execution context may still acquire the
     * same name later, so captured lowering must retain an exact nearer-presence
     * guard before taking a direct frame-backed path.
     */
    record CapturedResolved(CanonicalBindingIdentity identity, int lexicalDepth)
            implements CanonicalBindingResolution {
        public CapturedResolved {
            Objects.requireNonNull(identity, "identity");
            if (lexicalDepth <= 0) {
                throw new IllegalArgumentException("captured lexicalDepth must be positive");
            }
        }
    }

    /**
     * The nearest scope (by {@code lexicalDepth} hops from the reference)
     * that statically declares this name anywhere, without a presence
     * guarantee at this program point. A runtime presence/topology check or
     * exact dynamic fallback remains required.
     */
    record Candidate(CanonicalBindingIdentity identity, int lexicalDepth) implements CanonicalBindingResolution {
        public Candidate {
            Objects.requireNonNull(identity, "identity");
            if (lexicalDepth < 0) {
                throw new IllegalArgumentException("lexicalDepth must not be negative");
            }
        }
    }

    /** No scope in the static chain declares this name anywhere; exact dynamic fallback remains required. */
    record Dynamic() implements CanonicalBindingResolution {}
}

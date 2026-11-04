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
 * <p>Keeps {@code STATIC_BINDING_IDENTITY != SEMANTIC_PRESENCE} in the
 * analysis: knowing which declaration a name would refer to is never
 * conflated with knowing that the binding is PRESENT at runtime. {@link
 * Resolved} identifies a binding of the reference's own scope already
 * established at this program point; {@link CapturedResolved} one owned by
 * an outer genuine lexical scope; {@link Candidate} the nearest declaring
 * scope without a presence guarantee; {@link Dynamic} means no scope in the
 * static chain declares the name.
 *
 * <p>I115: Bytecode lowering consumes the result as the static storage
 * classification of the site. The owner's {@link CanonicalLexicalScope.Storage}
 * selects exactly one access: a Bytecode DSL local, a shared cell, a module
 * context name operation, or an object member operation; {@link Dynamic}
 * selects one name operation. No classified site is lowered with a runtime
 * presence guard or a fallback to another storage.
 *
 * <p>KNOWN I115 SEMANTIC DIVERGENCE: because presence is no longer
 * re-checked at a classified site, a {@code LOCAL}/{@code CELL} binding does
 * not observe D179 removal/recreation, and a {@link Candidate} read before
 * its establishment does not reach an outer binding of the same name.
 */
sealed interface CanonicalBindingResolution {
    /** The current scope already established this binding at this exact program point. */
    record Resolved(CanonicalBindingIdentity identity) implements CanonicalBindingResolution {
        public Resolved {
            Objects.requireNonNull(identity, "identity");
        }
    }

    /**
     * An already-established binding owned by an outer genuine lexical scope.
     *
     * <p>{@code lexicalDepth} counts genuine execution-context hops; object
     * construction scopes are skipped by {@link CanonicalLexicalScope#outwardScope()}.
     * I115 lowers it directly to the owner's storage: the owner's cell, or
     * its module context name.
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
     * guarantee at this program point. I115 lowers it to that owner's
     * storage; an access before establishment signals the lookup Error.
     */
    record Candidate(CanonicalBindingIdentity identity, int lexicalDepth) implements CanonicalBindingResolution {
        public Candidate {
            Objects.requireNonNull(identity, "identity");
            if (lexicalDepth < 0) {
                throw new IllegalArgumentException("lexicalDepth must not be negative");
            }
        }
    }

    /** No scope in the static chain declares this name anywhere; I115 lowers one name operation. */
    record Dynamic() implements CanonicalBindingResolution {}
}

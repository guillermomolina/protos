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
 * <p>Three facts are kept strictly apart:
 * <ul>
 *   <li><b>static owner identity</b>: which declaration (scope and name) the
 *       reference statically refers to, {@link #staticIdentityOrNull()};</li>
 *   <li><b>compile-time establishment</b>: whether that owner has already
 *       executed a binding point for the name at this exact program point of
 *       the analysis walk, {@link #establishedAtSite()};</li>
 *   <li><b>runtime membership</b>: whether the binding is PRESENT when the
 *       site executes. It is never represented here. Under D179 C0 an
 *       established binding may later be removed and recreated, and a
 *       not-yet-established one may be absent while an outer binding of the
 *       same name is PRESENT.</li>
 * </ul>
 * {@link Resolved} identifies a binding of the reference's own scope already
 * established at this program point; {@link CapturedResolved} one owned by an
 * outer genuine lexical scope, established when the capturing Closure was
 * created; {@link Candidate} the nearest scope that declares the name
 * without an establishment guarantee; {@link Dynamic} means no scope in the
 * static chain declares the name.
 *
 * <p>I115 / I115-S1c-L: Bytecode lowering takes the owner's {@link
 * CanonicalLexicalScope.Storage} as the physical storage of the binding (a
 * Bytecode DSL local, a shared cell, a module context name, or an object
 * member). Whether a site may access that storage directly, must validate the
 * binding's live membership first, or must select its binding by live lexical
 * selection is a separate run-time access decision ({@link
 * CanonicalBindingAnalysis.LexicalAccess}), made by the analysis from the
 * resolution together with the final scope facts; a resolution alone never
 * proves runtime presence.
 */
sealed interface CanonicalBindingResolution {
    /** The current scope already established this binding at this exact program point. */
    record Resolved(CanonicalBindingIdentity identity) implements CanonicalBindingResolution {
        public Resolved {
            Objects.requireNonNull(identity, "identity");
        }
    }

    /**
     * A binding owned by an outer genuine lexical scope and established there
     * before the capturing Closure literal is evaluated.
     *
     * <p>{@code lexicalDepth} counts genuine execution-context hops; object
     * construction scopes are skipped by {@link CanonicalLexicalScope#outwardScope()}.
     * The physical storage is the owner's cell, or its module context name;
     * the binding may still be removed at run time when the owner's Context
     * may be observed.
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
     * that statically declares this name anywhere, without an establishment
     * guarantee at this program point. It is a possible owner only: when its
     * binding is ABSENT at run time the reference continues ordinary lookup
     * and may select an outer binding of the same name or the receiver; it is
     * never a promise that no other binding may be selected.
     */
    record Candidate(CanonicalBindingIdentity identity, int lexicalDepth) implements CanonicalBindingResolution {
        public Candidate {
            Objects.requireNonNull(identity, "identity");
            if (lexicalDepth < 0) {
                throw new IllegalArgumentException("lexicalDepth must not be negative");
            }
        }
    }

    /**
     * No scope in the static chain declares this name anywhere; the
     * reference is always selected by live lexical selection (a dynamically
     * created binding, the Prelude, or the receiver).
     */
    record Dynamic() implements CanonicalBindingResolution {}

    /** The static owner identity, or {@code null} for a {@link Dynamic} reference. */
    default CanonicalBindingIdentity staticIdentityOrNull() {
        if (this instanceof Resolved resolved) {
            return resolved.identity();
        }
        if (this instanceof CapturedResolved captured) {
            return captured.identity();
        }
        if (this instanceof Candidate candidate) {
            return candidate.identity();
        }
        return null;
    }

    /**
     * Whether the owner established the binding at this program point (a
     * compile-time fact). It never proves runtime presence by itself.
     */
    default boolean establishedAtSite() {
        return this instanceof Resolved || this instanceof CapturedResolved;
    }
}

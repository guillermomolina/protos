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

/**
 * The runtime representation of a genuine Protos execution context: the object
 * bound to a Closure invocation's fresh activation and to a module's
 * {@code moduleContext}, as defined by {@code EXECUTION_AND_CONTROL.md} §4.
 *
 * <p>Execution contexts remain ordinary Protos objects for structural mutation,
 * delegation through {@code Context -> Object}, reflection, capture, and
 * escape. Under ratified D179 candidate C0, an OPEN execution context may
 * remove a PRESENT local slot, making it semantically ABSENT; later lookup may
 * therefore continue through the ordinary outer-lexical and receiver fallback
 * path. CLOSED/FROZEN structural rules remain those of ordinary objects.
 *
 * <p>An ordinary object that merely delegates through {@code Context} (for
 * example because guest code wrote {@code foo: Context {}}) is not made from
 * this class and is therefore unaffected: it is not actually used as an
 * activation's lexical scope object, so it never acquires execution-context
 * membership semantics. The object created while an object literal's body
 * executes is likewise an ordinary object, not an instance of this class, per
 * {@code EXECUTION_AND_CONTROL.md} §4 ("Object Construction Is Not a Lexical
 * Capture Scope").
 *
 * <p>PLAT036 Candidate D requires exactly one authoritative store for a
 * statically admitted lexical binding. Each execution context therefore
 * installs its own {@link ProtosLexicalBindingAuthority} once, at
 * construction, through the {@code ProtosObjectValue} authority-attachment
 * constructor, rather than relying on the base class's inherited default. In
 * this slice the installed authority is still the same map-backed storage
 * ordinary objects use, so no runtime lexical behavior changes; a later slice
 * may install a Truffle Bytecode DSL frame-backed authority here instead
 * without any caller of the local-slot operations below changing.
 */
public final class ProtosExecutionContextValue extends ProtosObjectValue {
    public ProtosExecutionContextValue(Object parent) {
        super(parent, new ProtosMapBackedLexicalBindingAuthority());
    }

    /**
     * PLAT036 Candidate D, Slice 3 backend-private hook: a genuine {@code
     * ROOT}/{@code CLOSURE} Bytecode lowering unit calls this exactly once, as
     * its own first executed operation, to replace the context's default
     * map-backed authority with one backed by that unit's own Truffle Bytecode
     * DSL frame/local layout. Bindings established before root execution are
     * migrated during the authority handoff, preserving their insertion order
     * and values without exposing two simultaneous authorities.
     */
    public void installFrameLexicalBindingAuthority(ProtosLexicalBindingAuthority frameBackedAuthority) {
        replaceLexicalBindingAuthorityPreservingBindings(frameBackedAuthority);
    }

    /**
     * I068 Slice 5 execution-backend hook. The returned interface remains
     * backend-neutral; callers in the Bytecode backend may recognize their
     * own concrete authority without storing Truffle objects in semantic
     * Closure values.
     */
    public ProtosLexicalBindingAuthority lexicalBindingAuthorityForRuntime() {
        return lexicalBindingAuthorityForSubclass();
    }
}

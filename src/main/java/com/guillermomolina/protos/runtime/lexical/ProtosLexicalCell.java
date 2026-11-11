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
 * I115 internal runtime storage of one Closure binding captured by reference
 * by a nested Closure (GraalPy {@code PCell}).
 *
 * <p>The owning activation creates exactly one cell per captured binding and
 * shares the same instance with every Closure that captures it, so the cell
 * is the single authority of that binding's value and of its presence: there
 * is no second copy in a frame slot or a context. The value is a guest value;
 * Java {@code null} only marks an ABSENT binding (its binding point has not
 * executed yet, or the binding was removed through the observed Context),
 * which is distinct from the PRESENT guest {@code null}.
 *
 * <p>Allocating a cell never establishes its binding. Removal clears the
 * value without replacing the cell, so every capturing Closure observes the
 * removal, and a later legal recreation establishes the value in this same
 * cell. A read of an ABSENT cell is not an error by itself: the reader
 * continues ordinary lexical selection.
 *
 * <p>Presence proofs are exactly as narrow as the physical binding: a
 * presence-aware read tests this cell's own state on every execution (one
 * null check, GraalPy's checked cell load), so removal of this binding never
 * invalidates any other binding, activation or compiled specialization, and
 * a later recreation needs no re-validation. A change of value alone never
 * affects presence.
 *
 * <p>When the owner's Context is observed, the frame-backed view attaches the
 * owning activation, so a direct mutation from a capturing Closure applies the
 * same structural rules as a mutation through that Context: a FROZEN Context
 * rejects assignment.
 *
 * <p>A cell is confined to the owning Actor-local execution, like the
 * activation that creates it, so it carries no synchronization.
 */
public final class ProtosLexicalCell {
    private Object value;
    private ProtosActivation owner;

    public Object valueOrNullForRuntime() {
        return value;
    }

    /** Whether the binding is PRESENT; reads no guest value. */
    public boolean isPresentForRuntime() {
        return value != null;
    }

    public void setForRuntime(Object newValue) {
        if (newValue == null) {
            throw new IllegalArgumentException("a cell holds guest values only");
        }
        value = newValue;
    }

    /**
     * Makes the binding ABSENT while preserving this cell, which every
     * capturing Closure keeps sharing.
     */
    public void clearForRuntime() {
        value = null;
    }

    /**
     * Attaches the activation that owns this cell once its Context may be
     * observed. Idempotent for the same owner; a cell never changes owner.
     */
    public void attachOwnerForRuntime(ProtosActivation activation) {
        if (activation == null) {
            throw new IllegalArgumentException("owner");
        }
        if (owner != null && owner != activation) {
            throw new IllegalStateException("a cell belongs to exactly one activation");
        }
        owner = activation;
    }

    /** Whether the owner's observed Context is FROZEN; never materializes it. */
    public boolean ownerContextFrozenForRuntime() {
        ProtosActivation current = owner;
        return current != null && current.currentContextIsFrozenForRuntime();
    }

    /**
     * Whether the owner's observed Context rejects the creation of a binding
     * (it is CLOSED or FROZEN); never materializes it.
     */
    public boolean ownerContextRejectsCreationForRuntime() {
        ProtosActivation current = owner;
        if (current == null) {
            return false;
        }
        ProtosObjectValue context = current.materializedContextOrNullForRuntime();
        return context != null && !context.isOpen();
    }
}

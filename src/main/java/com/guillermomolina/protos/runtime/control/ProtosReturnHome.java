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

package com.guillermomolina.protos.runtime.control;

import com.guillermomolina.protos.runtime.ProtosTask;

/**
 * The Smalltalk-style return home an owning invocation establishes.
 *
 * <p>PERF025: a home that is statically proven unobservable (no non-local
 * return can ever target it; see {@code CanonicalReturnHomeAnalysis}) is
 * represented by the shared {@link #unobservable()} marker instead of a
 * fresh instance. The marker still carries home provenance (a Closure created
 * under it shares its owner's home rather than owning one), but it has no
 * lifecycle: owners never complete it, and a non-local return can never
 * target it. It is never a guest value, so its identity is not observable.
 */
public final class ProtosReturnHome {
    private static final ProtosReturnHome UNOBSERVABLE = new ProtosReturnHome(false);
    /*
     * I091: frame-ABI-only variant of UNOBSERVABLE written by a fused direct
     * source call or send whose caller accepts a primitive numeric result. The frame
     * argument accessors normalize it to UNOBSERVABLE, so no activation,
     * non-local return or guest observer ever sees it.
     */
    private static final ProtosReturnHome UNOBSERVABLE_PRIMITIVE_RESULT = new ProtosReturnHome(false);

    private boolean active;
    /*
     * PLAT070: the exact Task whose invocation owns this materialized home, or null for a
     * Task-free owner. Written at most once, when that invocation is established, and read by
     * RaiseNonLocalReturn with a plain identity comparison. Volatile so a captured Closure running
     * on another carrier observes it. Cleared by complete() after the home becomes inactive, so a
     * completed home never retains its Task; the volatile clear publishes the inactive state to a
     * reader that observes the cleared owner. Never a guest-visible property.
     */
    private volatile ProtosTask ownerTask;

    public ProtosReturnHome() {
        this(true);
    }

    private ProtosReturnHome(boolean active) {
        this.active = active;
    }

    /** The shared non-materialized representation of a proven-unobservable home. */
    public static ProtosReturnHome unobservable() {
        return UNOBSERVABLE;
    }

    /** I091 frame-ABI marker; see {@link #unobservable()} for its semantics. */
    public static ProtosReturnHome unobservableAcceptingPrimitiveResultForRuntime() {
        return UNOBSERVABLE_PRIMITIVE_RESULT;
    }

    /** False only for the {@link #unobservable()} marker, which has no lifecycle. */
    public boolean isMaterialized() {
        return this != UNOBSERVABLE && this != UNOBSERVABLE_PRIMITIVE_RESULT;
    }

    /**
     * PLAT070: binds the exact Task whose invocation owns this home. Idempotent for the same
     * Task; rejects a different Task. The shared unobservable markers have no owner and are left
     * untouched. Only the owning invocation may call this; a Closure capturing the home never
     * does.
     */
    public void bindOwnerTaskForRuntime(ProtosTask task) {
        if (task == null || !isMaterialized() || !active) {
            return;
        }
        ProtosTask current = ownerTask;
        if (current == task) {
            return;
        }
        if (current != null) {
            throw new IllegalStateException("return home already belongs to another Task");
        }
        ownerTask = task;
    }

    /** PLAT070: the owning Task, or {@code null} for a Task-free owner. No allocation. */
    public ProtosTask ownerTaskForRuntime() {
        return ownerTask;
    }

    public boolean isActive() {
        return active;
    }

    public void complete() {
        if (!active) {
            throw new IllegalStateException("return home is already completed");
        }
        active = false;
        // PLAT070: release the owner after deactivation; see ownerTask.
        ownerTask = null;
    }
}

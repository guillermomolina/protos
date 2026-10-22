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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.util.Objects;

/**
 * I115 (GraalPy {@code PFrame.Reference}): a lightweight reference to a
 * still-compact caller invocation, stored in the B5 caller slot (slot 3) of
 * its callee together with the caller information the callee's root
 * requested through its caller needs.
 *
 * <p>The four states the slot distinguishes, none of them by {@code null}:
 * <ul>
 *   <li>information not transported because it was not requested: the slot
 *       holds the caller's raw B5 header (no reference is built);</li>
 *   <li>information transported: the slot holds a reference, and {@link
 *       #transportedPrelude} is the caller's effective prelude, which may
 *       legitimately be {@code null} (the caller has no prelude);</li>
 *   <li>exact activation not yet published: argument 0 of {@link
 *       #callerArguments} is still the invoked Closure;</li>
 *   <li>exact activation published: argument 0 of {@link #callerArguments}
 *       is that invocation's single activation ({@link
 *       #publishedActivationOrNull}).</li>
 * </ul>
 *
 * <p>The reference never holds an activation of its own and never builds
 * one: the exact caller is resolved, and published at most once, through
 * the referenced header by {@link ProtosFrameArguments#resolveExactCaller}.
 * Every reference to one invocation therefore resolves to the same
 * identity. The transported prelude is fixed before the caller can call
 * anything (see {@link ProtosFrameArguments#resolveExactCaller}, temporal
 * equivalence), so it stays exact across suspension and publication.
 *
 * <p>Only {@link ProtosFrameArguments} constructs references, from the
 * running root's own admitted call header. Only the admitted read uses the
 * transported value; the general accessors ignore it and follow, validate
 * and cycle-check the referenced header's real provenance.
 */
final class ProtosCallerFrameReference {
    private final Object[] callerArguments;
    private final ProtosPrelude prelude;

    private ProtosCallerFrameReference(Object[] callerArguments, ProtosPrelude prelude) {
        this.callerArguments = callerArguments;
        this.prelude = prelude;
    }

    /**
     * A reference to the invocation whose B5 frame arguments are {@code
     * callerArguments}, transporting its effective {@code prelude} (possibly
     * {@code null}). Nothing is resolved, validated or published here.
     */
    static ProtosCallerFrameReference withPrelude(
            Object[] callerArguments, ProtosPrelude prelude) {
        return new ProtosCallerFrameReference(
                Objects.requireNonNull(callerArguments, "callerArguments"), prelude);
    }

    /** The referenced caller invocation's own B5 frame arguments. */
    Object[] callerArguments() {
        return callerArguments;
    }

    /** The caller's effective prelude, as transported; {@code null} is a valid value. */
    ProtosPrelude transportedPrelude() {
        return prelude;
    }

    /** The caller's exact activation if already published, else {@code null}. */
    ProtosActivation publishedActivationOrNull() {
        return callerArguments.length > 0
                        && callerArguments[0] instanceof ProtosActivation published
                ? published
                : null;
    }
}

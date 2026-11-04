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
 * is the single authority of that binding's value: there is no second copy
 * in a frame slot or a context. The value is a guest value; Java
 * {@code null} only marks a cell whose binding point has not executed yet,
 * which is distinct from the guest {@code null}. Translating an
 * uninitialized read into the guest lookup Error is the caller's boundary.
 *
 * <p>A cell is confined to the owning Actor-local execution, like the
 * activation that creates it, so it carries no synchronization.
 *
 * <p>KNOWN I115 SEMANTIC DIVERGENCE (D179): a cell is never removed or
 * recreated. A dynamic removeSlot/recreation of the binding through an
 * observed context is not reflected by the cell.
 */
public final class ProtosLexicalCell {
    private Object value;

    public Object valueOrNullForRuntime() {
        return value;
    }

    public void setForRuntime(Object newValue) {
        if (newValue == null) {
            throw new IllegalArgumentException("a cell holds guest values only");
        }
        value = newValue;
    }
}

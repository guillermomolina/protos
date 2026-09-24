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

import java.util.Objects;

/**
 * The runtime representation of a genuine Protos execution context: the object
 * bound to a Closure invocation's fresh activation and to a module's
 * {@code moduleContext}, as defined by {@code EXECUTION_AND_CONTROL.md} §4.
 *
 * <p>Execution contexts remain ordinary Protos objects for every other purpose
 * (delegation through {@code Context -> Object}, reflection, capture, escape).
 * The only distinguishing runtime rule, ratified by D179 candidate C3
 * ("monotonic context membership"), is that a local slot which has become
 * PRESENT on an execution context can never become ABSENT again: structural
 * removal is unconditionally rejected once the named local slot exists,
 * regardless of open/closed/frozen state. Growth while OPEN and value mutation
 * while writable are otherwise unaffected.
 *
 * <p>An ordinary object that merely delegates through {@code Context} (for
 * example because guest code wrote {@code foo: Context {}}) is not made from
 * this class and is therefore unaffected: it is not actually used as an
 * activation's lexical scope object, so it never acquires execution-context
 * membership semantics. The object created while an object literal's body
 * executes is likewise an ordinary object, not an instance of this class, per
 * {@code EXECUTION_AND_CONTROL.md} §4 ("Object Construction Is Not a Lexical
 * Capture Scope").
 */
public final class ProtosExecutionContextValue extends ProtosObjectValue {
    public ProtosExecutionContextValue(Object parent) {
        super(parent);
    }

    @Override
    public Object removeLocalSlot(String name) {
        Objects.requireNonNull(name, "name");
        if (hasLocalSlot(name)) {
            throw new IllegalStateException(
                    "execution-context local slot membership is monotonic once present: " + name);
        }
        return super.removeLocalSlot(name);
    }
}

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

/**
 * I113 / I115: the name-keyed lexical accesses of an
 * {@code AccessNamedLexical} instruction: a dynamic name, a site whose
 * actual nearest binding is not proven by its physical binding identity, or
 * an access inside an inline object-construction body, whose lookup also
 * observes the object under construction.
 *
 * <p>I115-S1c-L: both accesses are performed by {@link
 * ProtosLexicalSelectionNode}, the single runtime implementation of live
 * lexical selection; the constant only chooses read or write-target
 * selection, so the choice folds under partial evaluation.
 */
public enum ProtosNamedLexicalAccess {
    /** The unqualified lookup; signals the lookup Error on absence. */
    LOOKUP(false),

    /** The write-target selection; its assignment is a separate instruction. */
    RESOLVE_WRITE(true);

    private final boolean write;

    ProtosNamedLexicalAccess(boolean write) {
        this.write = write;
    }

    /** Whether this access selects a write destination instead of a value. */
    boolean write() {
        return write;
    }
}

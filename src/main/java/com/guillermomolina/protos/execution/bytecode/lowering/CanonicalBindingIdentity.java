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
 * PLAT036 Candidate D, Slice 1 backend-private identity for one statically
 * declared lexical binding (an ordinary/sequential/default/rest closure
 * parameter, or a bare {@code :} local creation) discovered by
 * {@link CanonicalBindingAnalyzer}.
 *
 * <p>Identity is deliberately Java reference identity, not name equality: two
 * bindings that share a name but belong to different {@link
 * CanonicalLexicalScope} owners (for example an outer and a nearer same-name
 * local) are distinct identities, while every reference to the same
 * declaration within one canonical compilation resolves to the same identity
 * instance. This is preparatory metadata only; it does not yet participate in
 * runtime lexical value authority (PLAT036 Slice 1 keeps
 * {@code RUNTIME_AUTHORITY_CUTOVER=NO}).
 */
final class CanonicalBindingIdentity {
    private final String name;
    private final CanonicalLexicalScope owner;

    CanonicalBindingIdentity(String name, CanonicalLexicalScope owner) {
        this.name = Objects.requireNonNull(name, "name");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    String name() {
        return name;
    }

    CanonicalLexicalScope owner() {
        return owner;
    }

    @Override
    public String toString() {
        return "CanonicalBindingIdentity[" + name + "@" + Integer.toHexString(System.identityHashCode(owner)) + "]";
    }
}

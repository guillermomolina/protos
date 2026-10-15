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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * TEST009-M bounded choke points for the String-keyed, interface-dispatched
 * residual of {@link ProtosLexicalBindingAuthority}.
 *
 * <p>Runtime callers such as {@link ProtosObjectValue} and
 * {@link ProtosActivation} hold the authority statically as the interface, so
 * partial evaluation would otherwise expand every implementation (including
 * map-backed {@code LinkedHashMap} machinery) at every call site. These helpers
 * only delegate: the authority remains the single semantic store, and
 * PRESENT/ABSENT, FROZEN/OPEN, and error order stay owned by the callers.
 * Statically proven frame-native accesses never come through here; they use
 * the ordinal-based Bytecode seams with PE-constant operands.
 */
final class ProtosLexicalBindingAuthorityCalls {
    private ProtosLexicalBindingAuthorityCalls() {}

    @TruffleBoundary
    static boolean contains(ProtosLexicalBindingAuthority authority, String name) {
        return authority.containsBinding(name);
    }

    @TruffleBoundary
    static Optional<Object> read(ProtosLexicalBindingAuthority authority, String name) {
        return authority.readBinding(name);
    }

    @TruffleBoundary
    static Map<String, Object> snapshot(ProtosLexicalBindingAuthority authority) {
        return authority.bindingsSnapshot();
    }

    @TruffleBoundary
    static void appendTo(
            ProtosLexicalBindingAuthority authority,
            ArrayList<String> names,
            ArrayList<Object> values) {
        authority.appendBindingsTo(names, values);
    }

    @TruffleBoundary
    static void put(ProtosLexicalBindingAuthority authority, String name, Object value) {
        authority.putBinding(name, value);
    }

    @TruffleBoundary
    static Object remove(ProtosLexicalBindingAuthority authority, String name) {
        return authority.removeBinding(name);
    }

    /**
     * Copies every current binding of {@code source}, in its observable order,
     * into {@code target}. Used by the one-time authority handoffs, where the
     * source stays the only visible authority until the caller switches.
     */
    @TruffleBoundary
    static void transferAll(
            ProtosLexicalBindingAuthority source,
            ProtosLexicalBindingAuthority target) {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<Object> values = new ArrayList<>();
        source.appendBindingsTo(names, values);
        for (int index = 0; index < names.size(); index++) {
            target.putBinding(names.get(index), values.get(index));
        }
    }
}

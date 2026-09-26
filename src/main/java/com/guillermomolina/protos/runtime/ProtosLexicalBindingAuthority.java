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

import java.util.Map;
import java.util.Optional;

/**
 * Backend-private single authority for the physical storage of one
 * {@link ProtosObjectValue}'s lexical local-slot bindings.
 *
 * <p>PLAT036 Candidate D requires that a statically admitted lexical binding
 * have exactly one authoritative value store. Every {@code ProtosObjectValue}
 * (including {@link ProtosExecutionContextValue}) holds exactly one instance
 * of this authority, installed once at construction, through which all of its
 * local-slot operations are routed. This lets {@code ProtosExecutionContextValue}
 * later install an authority whose statically admitted bindings are backed by
 * Truffle Bytecode DSL frame/local state, without any caller of
 * {@code ProtosObjectValue}'s local-slot operations changing and without ever
 * creating a second authoritative copy of the same binding value.
 *
 * <p>This is an internal implementation seam, not a Protos language concept:
 * it must never be exposed to guest code, and it must not leak Truffle types
 * across the boundary.
 */
public interface ProtosLexicalBindingAuthority {
    boolean containsBinding(String name);

    Optional<Object> readBinding(String name);

    /**
     * Returns an immutable, insertion-ordered snapshot of the current bindings.
     */
    Map<String, Object> bindingsSnapshot();

    void appendBindingsTo(
            java.util.ArrayList<String> names,
            java.util.ArrayList<Object> values);

    void putBinding(String name, Object value);

    Object removeBinding(String name);

    /**
     * Backend-private hook invoked immediately before a genuine execution
     * context becomes guest-observable. Authorities whose storage depends on
     * ephemeral execution state may make that state escape-safe here.
     */
    default void prepareForContextObservation() {}
}

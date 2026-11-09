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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosActivationLexicalNamesTest {
    private static ProtosObjectValue object() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    @Test
    void lexicalScopesAreNearestFirstAndTheirLocalNamesReflectMutation() {
        ProtosObjectValue prelude = object();
        prelude.createLocalSlot("shared", ProtosNullValue.INSTANCE);
        prelude.createLocalSlot("fromPrelude", ProtosNullValue.INSTANCE);

        ProtosObjectValue outer = object();
        outer.createLocalSlot("shared", ProtosNullValue.INSTANCE);
        outer.createLocalSlot("fromOuter", ProtosNullValue.INSTANCE);

        ProtosObjectValue current = object();
        current.createLocalSlot("shared", ProtosNullValue.INSTANCE);
        current.createLocalSlot("local", ProtosNullValue.INSTANCE);

        ProtosActivation activation = new ProtosActivation(
                current, List.of(outer, prelude), object());

        assertEquals(
                List.of(List.of("shared", "local"), List.of("shared", "fromOuter"),
                        List.of("shared", "fromPrelude")),
                scopeNames(activation));

        assertEquals(List.of(current, outer, prelude),
                ProtosLexicalEnvironment.contextsOf(activation.lexicalEnvironmentForClosureCapture()));
        current.removeLocalSlot("shared");

        assertEquals(
                List.of(List.of("local"), List.of("shared", "fromOuter"),
                        List.of("shared", "fromPrelude")),
                scopeNames(activation));

        outer.removeLocalSlot("shared");

        assertEquals(
                List.of(List.of("local"), List.of("fromOuter"),
                        List.of("shared", "fromPrelude")),
                scopeNames(activation));

        outer.createLocalSlot("newBinding", ProtosNullValue.INSTANCE);

        assertEquals(
                List.of(List.of("local"), List.of("fromOuter", "newBinding"),
                        List.of("shared", "fromPrelude")),
                scopeNames(activation));
    }

    @Test
    void receiverAndContextDelegationNeverBecomeLexicalNames() {
        ProtosObjectValue delegated = object();
        delegated.createLocalSlot(
                "delegatedOnly", ProtosNullValue.INSTANCE);

        ProtosObjectValue current = new ProtosObjectValue(delegated);
        current.createLocalSlot("local", ProtosNullValue.INSTANCE);

        ProtosObjectValue receiver = object();
        receiver.createLocalSlot(
                "receiverOnly", ProtosNullValue.INSTANCE);

        ProtosActivation activation = new ProtosActivation(
                current, List.of(), receiver);

        assertEquals(
                List.of(List.of("local")),
                scopeNames(activation));

        assertFalse(
                scopeNames(activation).stream().flatMap(List::stream).toList()
                        .contains("receiverOnly"));
        assertFalse(
                scopeNames(activation).stream().flatMap(List::stream).toList()
                        .contains("delegatedOnly"));
    }

    @Test
    void onlyActuallyCapturedScopesContributeNames() {
        ProtosObjectValue current = object();
        ProtosObjectValue uncaptured = object();
        uncaptured.createLocalSlot(
                "notInLexicalChain", ProtosNullValue.INSTANCE);

        ProtosActivation activation = new ProtosActivation(
                current, List.of(), object());

        assertEquals(
                List.of(List.of()),
                scopeNames(activation));

        current.createLocalSlot("present", ProtosNullValue.INSTANCE);

        assertEquals(
                List.of(List.of("present")),
                scopeNames(activation));

        assertFalse(
                scopeNames(activation).stream().flatMap(List::stream).toList()
                        .contains("notInLexicalChain"));
    }

    /** Projects each actual scope separately; no synthetic merged-name authority. */
    private static List<List<String>> scopeNames(ProtosActivation activation) {
        return ProtosLexicalEnvironment.contextsOf(activation.lexicalEnvironmentForClosureCapture())
                .stream()
                .map(context -> List.copyOf(context.localSlotsSnapshot().keySet()))
                .toList();
    }

}

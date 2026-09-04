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

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosCoreBootstrapTest {
    @Test
    void bootstrapsContextPrototypeFromDistributableCoreSource()
            throws IOException {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(Path.of("protos", "lib", "core"));

        ProtosObjectValue contextPrototype = prelude.contextPrototype();
        ProtosObjectValue bindings = prelude.bindings();
        ProtosObjectValue first = prelude.newExecutionContext();
        ProtosObjectValue second = prelude.newExecutionContext();

        assertSame(
                ProtosObjectValue.rootObject(),
                contextPrototype.parent().orElseThrow());
        assertSame(contextPrototype, bindings.parent().orElseThrow());
        assertSame(
                contextPrototype,
                bindings.readLocalSlot("Context").orElseThrow());
        ProtosObjectValue errorPrototype = prelude.errorPrototype();
        assertSame(
                errorPrototype,
                bindings.readLocalSlot("Error").orElseThrow());
        assertSame(
                ProtosObjectValue.rootObject(),
                errorPrototype.parent().orElseThrow());
        ProtosObjectValue invalidReturnPrototype =
                prelude.invalidReturnPrototype();
        assertSame(
                invalidReturnPrototype,
                bindings.readLocalSlot("InvalidReturn").orElseThrow());
        assertSame(
                errorPrototype,
                invalidReturnPrototype.parent().orElseThrow());
        assertNotSame(
                prelude.newInvalidReturn(),
                prelude.newInvalidReturn());
        assertSame(
                invalidReturnPrototype,
                prelude.newInvalidReturn().parent().orElseThrow());

        ProtosObjectValue arrayPrototype = prelude.arrayPrototype();
        assertSame(
                arrayPrototype,
                bindings.readLocalSlot("Array").orElseThrow());
        assertSame(
                ProtosObjectValue.rootObject(),
                arrayPrototype.parent().orElseThrow());
        assertSame(
                arrayPrototype,
                prelude.newArray(java.util.List.of()).parent().orElseThrow());
        assertSame(
                ProtosObjectValue.MutationState.FROZEN,
                prelude.newFrozenArray(java.util.List.of()).mutationState());
        assertSame(
                ProtosObjectValue.MutationState.FROZEN,
                bindings.mutationState());
        assertNotSame(first, second);
        assertSame(contextPrototype, first.parent().orElseThrow());
        assertSame(contextPrototype, second.parent().orElseThrow());
    }
}

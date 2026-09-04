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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosStandardBooleanProtocolTest {
    @Test
    void selectedIfBranchesInvokeCallbackAndReturnExactResult() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertInteger(prelude, "true.ifTrue(() => { 42 })", 42);
        assertInteger(prelude, "false.ifFalse(() => { 43 })", 43);
    }

    @Test
    void unselectedIfBranchesReturnCanonicalNullWithoutValidatingCallback() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertSame(ProtosNullValue.INSTANCE, execute(prelude, "false.ifTrue(123)"));
        assertSame(ProtosNullValue.INSTANCE, execute(prelude, "true.ifFalse(123)"));
    }

    @Test
    void andOrShortCircuitWithoutValidatingUnselectedCallback() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertSame(ProtosBooleanValue.FALSE, execute(prelude, "false.and(123)"));
        assertSame(ProtosBooleanValue.TRUE, execute(prelude, "true.or(123)"));
    }

    @Test
    void selectedAndOrCallbacksMustReturnCanonicalBoolean() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertSame(
                ProtosBooleanValue.FALSE,
                execute(prelude, "true.and(() => { false })"));
        assertSame(
                ProtosBooleanValue.TRUE,
                execute(prelude, "false.or(() => { true })"));

        assertThrows(
                ProtosSignalException.class,
                () -> execute(prelude, "true.and(() => { 1 })"));
        assertThrows(
                ProtosSignalException.class,
                () -> execute(prelude, "false.or(() => { null })"));
    }

    @Test
    void selectedBranchesUseOrdinaryPolymorphicInvocation() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertThrows(
                ProtosSignalException.class,
                () -> execute(prelude, "true.ifTrue(123)"));
        assertThrows(
                ProtosSignalException.class,
                () -> execute(prelude, "false.ifFalse(123)"));
    }

    @Test
    void standardBooleanBehaviorRejectsNonBooleanOriginalReceiver() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosObjectValue ordinary =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        prelude.newModuleActivation().context();

        Object ifTrue =
                ProtosObjectValue.rootObject().readLocalSlot("ifTrue").orElseThrow();
        ordinary.createLocalSlot("copiedIfTrue", ifTrue);

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                ordinary,
                                "ifTrue",
                                java.util.List.of(new ProtosIntegerValue(BigInteger.ONE)),
                                prelude.newModuleActivation()));
    }

    @Test
    void rootObjectOwnsBooleanSelectorsWithoutBooleanPrototype() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosObjectValue object = ProtosObjectValue.rootObject();

        assertEquals(true, object.hasLocalSlot("ifTrue"));
        assertEquals(true, object.hasLocalSlot("ifFalse"));
        assertEquals(true, object.hasLocalSlot("and"));
        assertEquals(true, object.hasLocalSlot("or"));
        assertEquals(false, prelude.bindings().hasLocalSlot("Boolean"));
    }

    private static void assertInteger(ProtosPrelude prelude, String source, long expected) {
        Object result = execute(prelude, source);
        assertEquals(BigInteger.valueOf(expected), ((ProtosIntegerValue) result).value());
    }

    private static Object execute(ProtosPrelude prelude, String source) {
        return new ProtosSourceCompiler()
                .compile(source)
                .call(prelude.newModuleActivation());
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }
}

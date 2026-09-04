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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosPolymorphicInvocationTest {
    @Test
    void plainClosureCallUsesInheritedObjectCall() throws IOException {
        Object result = execute(corePrelude(), "f: (x) => x\nf(42)");
        assertEquals(BigInteger.valueOf(42), assertInstanceOf(ProtosIntegerValue.class, result).value());
    }

    @Test
    void ordinaryLocalCallSlotOverridesInheritedObjectCall() throws IOException {
        Object result = execute(corePrelude(), "callable: { call: (x) => x }\ncallable(7)");
        assertEquals(BigInteger.valueOf(7), assertInstanceOf(ProtosIntegerValue.class, result).value());
    }

    @Test
    void inheritedCallKeepsOriginalReceiver() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        Object result = new ProtosSourceCompiler().compile("parent: { call: () => this }\nchild: parent {}\nchild()").call(activation);
        assertSame(activation.context().readLocalSlot("child").orElseThrow(), result);
    }

    @Test
    void nonClosureShadowingCallStopsLookupAndSignalsError() throws IOException {
        assertThrows(ProtosSignalException.class, () -> execute(corePrelude(), "blocked: { call: 42 }\nblocked()"));
    }

    @Test
    void inheritedObjectCallConstructsAndRunsOverriddenInit() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        Object result = new ProtosSourceCompiler().compile(
                "Thing: { init: (x) => { this.value: x; null } }\ninstance: Thing(42)\ninstance").call(activation);
        ProtosObjectValue thing = assertInstanceOf(ProtosObjectValue.class, activation.context().readLocalSlot("Thing").orElseThrow());
        ProtosObjectValue instance = assertInstanceOf(ProtosObjectValue.class, result);
        assertSame(thing, instance.parent().orElseThrow());
        assertEquals(BigInteger.valueOf(42), assertInstanceOf(ProtosIntegerValue.class, instance.readLocalSlot("value").orElseThrow()).value());
    }

    @Test
    void standardObjectInitReturnsReceiverAndRejectsArguments() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation caller = prelude.newModuleActivation();
        ProtosObjectValue receiver = new ProtosObjectValue(ProtosObjectValue.rootObject());
        assertSame(receiver, ProtosInvocation.invokeMessage(receiver, "init", List.of(), caller));
        assertThrows(ProtosSignalException.class, () -> ProtosInvocation.invokeMessage(receiver, "init", List.of(new ProtosObjectValue(ProtosObjectValue.rootObject())), caller));
    }

    @Test
    void callSpreadFlattensBeforeClosureActivation() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        Object first = new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object second = new ProtosObjectValue(ProtosObjectValue.rootObject());
        activation.context().createLocalSlot("xs", prelude.newArray(List.of(first, second)));
        Object result = new ProtosSourceCompiler().compile("f: (...items) => items\nf(...xs)").call(activation);
        ProtosArrayValue rest = assertInstanceOf(ProtosArrayValue.class, result);
        assertEquals(BigInteger.valueOf(2), rest.indexedSize());
        assertSame(first, rest.indexedAt(BigInteger.ZERO));
        assertSame(second, rest.indexedAt(BigInteger.ONE));
        assertTrue(rest.isFrozen());
    }

    @Test
    void nestedCallInsideClosureUsesCallableLowering() throws IOException {
        Object result = execute(corePrelude(), "identity: (x) => x\nouter: () => identity(99)\nouter()");
        assertEquals(BigInteger.valueOf(99), assertInstanceOf(ProtosIntegerValue.class, result).value());
    }

    @Test
    void nonLocalReturnCrossesNestedOrdinaryCall() throws IOException {
        Object result = execute(corePrelude(), "outer: () => { inner: () => ^42; inner(); 0 }\nouter()");
        assertEquals(BigInteger.valueOf(42), assertInstanceOf(ProtosIntegerValue.class, result).value());
    }

    private static Object execute(ProtosPrelude prelude, String source) {
        return new ProtosSourceCompiler().compile(source).call(prelude.newModuleActivation());
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }
}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosInvocation;
import com.guillermomolina.protos.execution.ProtosSourceCompiler;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosRepresentedValueLookupTest {
    @Test
    void ordinaryObjectLookupRemainsOrdinaryObjectLookup() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosObjectValue parent = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue child = new ProtosObjectValue(parent);
        Object marker = new ProtosObjectValue(ProtosObjectValue.rootObject());
        parent.createLocalSlot("marker", marker);

        ProtosSlotLookupResult selected =
                ProtosValueLookup.lookup(child, "marker", prelude).orElseThrow();

        assertSame(marker, selected.value());
        assertSame(parent, selected.home());
    }

    @Test
    void numericRepresentationsUseTheirNormativePrototypeParents() throws IOException {
        ProtosPrelude prelude = corePrelude();
        Object integerMarker = new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object floatMarker = new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object fixedMarker = new ProtosObjectValue(ProtosObjectValue.rootObject());

        prelude.integerPrototype().createLocalSlot("integerMarker", integerMarker);
        prelude.floatPrototype().createLocalSlot("floatMarker", floatMarker);
        prelude.fixedIntegerPrototype(ProtosFixedIntegerValue.Family.UINT8)
                .createLocalSlot("fixedMarker", fixedMarker);

        assertSame(
                integerMarker,
                ProtosValueLookup.lookup(
                                new ProtosIntegerValue(BigInteger.valueOf(42)),
                                "integerMarker",
                                prelude)
                        .orElseThrow()
                        .value());
        assertSame(
                floatMarker,
                ProtosValueLookup.lookup(new ProtosFloatValue(2.5d), "floatMarker", prelude)
                        .orElseThrow()
                        .value());
        assertSame(
                fixedMarker,
                ProtosValueLookup.lookup(
                                new ProtosFixedIntegerValue(
                                        ProtosFixedIntegerValue.Family.UINT8,
                                        BigInteger.valueOf(7)),
                                "fixedMarker",
                                prelude)
                        .orElseThrow()
                        .value());
    }

    @Test
    void canonicalBooleanAndNullBridgeDirectlyToObject() throws IOException {
        ProtosPrelude prelude = corePrelude();
        Object inheritedCall =
                ProtosObjectValue.rootObject().readLocalSlot("call").orElseThrow();

        for (Object value :
                List.of(
                        ProtosBooleanValue.TRUE,
                        ProtosBooleanValue.FALSE,
                        ProtosNullValue.INSTANCE)) {
            ProtosSlotLookupResult selected =
                    ProtosValueLookup.lookup(value, "call", prelude).orElseThrow();
            assertSame(inheritedCall, selected.value());
            assertSame(ProtosObjectValue.rootObject(), selected.home());
        }

        assertFalse(prelude.bindings().hasLocalSlot("Boolean"));
    }

    @Test
    void representedLookupExtensionDoesNotRequireCentralDispatcherCases() {
        ProtosObjectValue parent = new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object marker = new ProtosObjectValue(ProtosObjectValue.rootObject());
        parent.createLocalSlot("marker", marker);
        TestRepresentedValue value = new TestRepresentedValue(parent);

        ProtosSlotLookupResult selected =
                ProtosValueLookup.lookup(value, "marker", null).orElseThrow();

        assertSame(marker, selected.value());
        assertSame(parent, selected.home());
    }

    @Test
    void inheritedMethodReceivesOriginalRepresentedReceiver() throws IOException {
        ProtosPrelude prelude = corePrelude();

        Object result =
                new ProtosSourceCompiler()
                        .compile(
                                """
                                Integer.echoReceiver: () => this
                                ((23).echoReceiver)()
                                """)
                        .call(prelude.newModuleActivation());

        assertEquals(
                BigInteger.valueOf(23),
                ((ProtosIntegerValue) result).value());
    }

    @Test
    void delegatingToNumericValueDoesNotConferNumericMembership() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation caller = prelude.newModuleActivation();
        ProtosObjectValue child =
                new ProtosObjectValue(new ProtosIntegerValue(BigInteger.valueOf(7)));

        ProtosSignalException failure =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosInvocation.invokeMessage(
                                        child,
                                        "==",
                                        List.of(new ProtosIntegerValue(BigInteger.valueOf(7))),
                                        caller));

        assertSame(prelude.errorPrototype(), failure.error().parent().orElseThrow());
    }

    @Test
    void polymorphicInvocationUsesRepresentedLookupWithoutHostLookupFailure()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation caller = prelude.newModuleActivation();

        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invoke(ProtosBooleanValue.TRUE, List.of(), caller));
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosInvocation.invoke(ProtosNullValue.INSTANCE, List.of(), caller));
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invoke(
                                new ProtosIntegerValue(BigInteger.ONE), List.of(), caller));
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    private record TestRepresentedValue(Object parent) implements ProtosRepresentedValue {
        @Override
        public Object representedDelegationParent(ProtosPrelude prelude) {
            return parent;
        }
    }
}

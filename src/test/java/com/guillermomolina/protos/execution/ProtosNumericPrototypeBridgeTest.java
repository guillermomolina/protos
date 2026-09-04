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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosNumericPrototypeBridgeTest {
    @Test
    void sourceBackedNumericPrototypeHierarchyIsNormativeHierarchy() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertSame(prelude.numberPrototype(), prelude.integerPrototype().parent().orElseThrow());
        assertSame(prelude.numberPrototype(), prelude.floatPrototype().parent().orElseThrow());
        assertSame(
                ProtosObjectValue.rootObject(),
                prelude.numberPrototype().parent().orElseThrow());
    }

    @Test
    void integerMemberLookupStartsAtIntegerPrototype() throws IOException {
        ProtosPrelude prelude = corePrelude();
        Object marker = new ProtosObjectValue(ProtosObjectValue.rootObject());
        prelude.integerPrototype().createLocalSlot("marker", marker);

        Object result = execute(prelude, "(42).marker");

        assertSame(marker, result);
    }

    @Test
    void integerLookupDelegatesThroughNumberPrototype() throws IOException {
        ProtosPrelude prelude = corePrelude();
        Object marker = new ProtosObjectValue(ProtosObjectValue.rootObject());
        prelude.numberPrototype().createLocalSlot("numericMarker", marker);

        Object result = execute(prelude, "(42).numericMarker");

        assertSame(marker, result);
    }

    @Test
    void floatMemberLookupStartsAtFloatThenNumber() throws IOException {
        ProtosPrelude prelude = corePrelude();
        Object marker = new ProtosObjectValue(ProtosObjectValue.rootObject());
        prelude.numberPrototype().createLocalSlot("numericMarker", marker);

        Object result = execute(prelude, "(1.5).numericMarker");

        assertSame(marker, result);
    }

    @Test
    void childWhoseParentIsNumericValueContinuesThroughThatExactValuesFamilyChain()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        Object marker = new ProtosObjectValue(ProtosObjectValue.rootObject());
        prelude.integerPrototype().createLocalSlot("integerMarker", marker);

        ProtosObjectValue child =
                new ProtosObjectValue(
                        new ProtosIntegerValue(BigInteger.valueOf(7)));
        ProtosActivation activation = prelude.newModuleActivation();
        activation.context().createLocalSlot("child", child);

        Object result =
                new ProtosSourceCompiler()
                        .compile("child.integerMarker")
                        .call(activation);

        assertSame(marker, result);
    }

    @Test
    void inheritedNumericMethodBindsOriginalNumericReceiverAndPhysicalHome()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        Object result =
                new ProtosSourceCompiler()
                        .compile(
                                """
                                Integer.echoReceiver: () => this
                                ((23).echoReceiver)()
                                """)
                        .call(activation);

        assertEquals(
                BigInteger.valueOf(23),
                ((ProtosIntegerValue) result).value());
    }

    @Test
    void floatRepresentationRemainsExactReceiverForInheritedMethod()
            throws IOException {
        ProtosPrelude prelude = corePrelude();

        Object result =
                execute(
                        prelude,
                        """
                        Number.echoReceiver: () => this
                        ((2.5).echoReceiver)()
                        """);

        assertEquals(2.5d, ((ProtosFloatValue) result).value());
    }

    private static Object execute(ProtosPrelude prelude, String source) {
        return new ProtosSourceCompiler()
                .compile(source)
                .call(prelude.newModuleActivation());
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap()
                .bootstrap(Path.of("protos", "lib", "core"));
    }
}

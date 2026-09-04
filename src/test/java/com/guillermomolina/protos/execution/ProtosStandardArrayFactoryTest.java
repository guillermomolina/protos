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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

class ProtosStandardArrayFactoryTest {
    @Test
    void arrayCallCreatesFreshOpenArrayWithExactSuppliedElements() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        Object marker = new ProtosObjectValue(ProtosObjectValue.rootObject());
        activation.context().createLocalSlot("marker", marker);

        ProtosArrayValue first =
                (ProtosArrayValue)
                        new ProtosSourceCompiler()
                                .compile("Array(1, marker)")
                                .call(activation);
        ProtosArrayValue second =
                (ProtosArrayValue)
                        new ProtosSourceCompiler()
                                .compile("Array(1, marker)")
                                .call(activation);

        assertNotSame(first, second);
        assertFalse(first.isClosed());
        assertFalse(first.isFrozen());
        assertSame(prelude.arrayPrototype(), first.parent().orElseThrow());
        assertEquals(BigInteger.valueOf(2), first.indexedSize());
        assertEquals(
                BigInteger.ONE,
                ((ProtosIntegerValue) first.indexedAt(BigInteger.ZERO)).value());
        assertSame(marker, first.indexedAt(BigInteger.ONE));
    }

    @Test
    void singleIntegerArgumentIsOneExactElementNotLength() throws IOException {
        ProtosPrelude prelude = corePrelude();

        ProtosArrayValue result =
                (ProtosArrayValue)
                        new ProtosSourceCompiler()
                                .compile("Array(3)")
                                .call(prelude.newModuleActivation());

        assertEquals(BigInteger.ONE, result.indexedSize());
        assertEquals(
                BigInteger.valueOf(3),
                ((ProtosIntegerValue) result.indexedAt(BigInteger.ZERO)).value());
    }

    @Test
    void inheritedArrayFactoryUsesInvocationReceiverAsNewArrayParent() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosArrayValue result =
                (ProtosArrayValue)
                        new ProtosSourceCompiler()
                                .compile(
                                        """
                                        MyArray: Array {
                                            label: 9
                                        }
                                        MyArray(10, 20)
                                        """)
                                .call(activation);

        Object myArray = activation.context().readLocalSlot("MyArray").orElseThrow();
        assertSame(myArray, result.parent().orElseThrow());
        assertEquals(BigInteger.valueOf(2), result.indexedSize());
    }

    @Test
    void copiedStandardArrayFactoryRejectsUnrelatedReceiver() throws IOException {
        ProtosPrelude prelude = corePrelude();
        Object standardCall =
                prelude.arrayPrototype().readLocalSlot("call").orElseThrow();
        ProtosObjectValue unrelated =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        unrelated.createLocalSlot("call", standardCall);
        ProtosActivation activation = prelude.newModuleActivation();
        activation.context().createLocalSlot("unrelated", unrelated);

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                new ProtosSourceCompiler()
                                        .compile("unrelated()")
                                        .call(activation));

        assertSame(prelude.errorPrototype(), signal.error().parent().orElseThrow());
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap()
                .bootstrap(Path.of("protos", "lib", "core"));
    }
}

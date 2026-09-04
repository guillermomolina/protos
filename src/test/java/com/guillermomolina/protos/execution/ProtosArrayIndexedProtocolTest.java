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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosArrayIndexedProtocolTest {
    @Test
    void bracketReadUsesStandardArrayAt() throws IOException {
        ProtosPrelude prelude = corePrelude();

        Object result =
                execute(
                        prelude,
                        """
                        xs: Array(10, 20)
                        xs[1]
                        """);

        assertEquals(
                BigInteger.valueOf(20),
                ((ProtosIntegerValue) result).value());
    }

    @Test
    void indexedAssignmentMutatesExistingElementAndReturnsExactRhs() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        Object marker = new com.guillermomolina.protos.runtime.ProtosObjectValue(
                com.guillermomolina.protos.runtime.ProtosObjectValue.rootObject());
        activation.context().createLocalSlot("marker", marker);

        Object result =
                new ProtosSourceCompiler()
                        .compile(
                                """
                                xs: Array(10, 20)
                                xs[0] = marker
                                """)
                        .call(activation);

        ProtosArrayValue xs =
                (ProtosArrayValue)
                        activation.context().readLocalSlot("xs").orElseThrow();
        assertSame(marker, result);
        assertSame(marker, xs.indexedAt(BigInteger.ZERO));
        assertEquals(BigInteger.valueOf(2), xs.indexedSize());
    }

    @Test
    void customAtPutReturnValueDoesNotReplaceIndexedAssignmentResult() throws IOException {
        ProtosPrelude prelude = corePrelude();

        Object result =
                execute(
                        prelude,
                        """
                        receiver: {
                            atPut: (index, value) => 999
                        }
                        receiver[0] = 7
                        """);

        assertEquals(
                BigInteger.valueOf(7),
                ((ProtosIntegerValue) result).value());
    }

    @Test
    void closedArrayMayReplaceExistingElement() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosArrayValue xs = prelude.newArray(
                java.util.List.of(
                        new ProtosIntegerValue(BigInteger.ONE)));
        xs.close();
        activation.context().createLocalSlot("xs", xs);

        Object result =
                new ProtosSourceCompiler()
                        .compile("xs[0] = 2")
                        .call(activation);

        assertEquals(
                BigInteger.valueOf(2),
                ((ProtosIntegerValue) result).value());
        assertEquals(
                BigInteger.valueOf(2),
                ((ProtosIntegerValue) xs.indexedAt(BigInteger.ZERO)).value());
    }

    @Test
    void frozenArrayAtPutSignalsBeforeMutation() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosArrayValue xs = prelude.newArray(
                java.util.List.of(
                        new ProtosIntegerValue(BigInteger.ONE)));
        xs.freeze();
        activation.context().createLocalSlot("xs", xs);

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                new ProtosSourceCompiler()
                                        .compile("xs[0] = 2")
                                        .call(activation));

        assertSame(prelude.errorPrototype(), signal.error().parent().orElseThrow());
        assertEquals(
                BigInteger.ONE,
                ((ProtosIntegerValue) xs.indexedAt(BigInteger.ZERO)).value());
    }

    @Test
    void standardArrayAtRejectsNonIntegerAndOutOfBounds() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertThrows(
                ProtosSignalException.class,
                () -> execute(prelude, "Array(1).at(1.0)"));
        assertThrows(
                ProtosSignalException.class,
                () -> execute(prelude, "Array(1).at(1)"));
        assertThrows(
                ProtosSignalException.class,
                () -> execute(prelude, "Array(1).at(-1)"));
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

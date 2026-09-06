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

import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosCoreErrors.StandardError;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosSuperSendExecutionTest {
    @Test
    void superContinuesAfterPhysicalMethodHomeAndKeepsDynamicReceiver()
            throws IOException {
        ProtosPrelude prelude = corePrelude();

        Object result =
                execute(
                        prelude,
                        """
                        base: {
                            value: 1
                            read: () => { value }
                        }
                        middle: base {
                            read: () => { super.read() + 10 }
                        }
                        leaf: middle {
                            value: 7
                        }
                        leaf.read()
                        """);

        assertEquals(BigInteger.valueOf(17), ((ProtosIntegerValue) result).value());
    }

    @Test
    void nestedClosureRetainsMethodHomeForSuper() throws IOException {
        ProtosPrelude prelude = corePrelude();

        Object result =
                execute(
                        prelude,
                        """
                        base: {
                            value: 1
                            read: () => { value }
                        }
                        middle: base {
                            read: () => {
                                nested: () => { super.read() }
                                nested()
                            }
                        }
                        leaf: middle {
                            value: 9
                        }
                        leaf.read()
                        """);

        assertEquals(BigInteger.valueOf(9), ((ProtosIntegerValue) result).value());
    }

    @Test
    void superArgumentsUseOrdinarySpreadVectorSemantics() throws IOException {
        ProtosPrelude prelude = corePrelude();

        Object result =
                execute(
                        prelude,
                        """
                        base: {
                            sum: (a, b) => { a + b }
                        }
                        child: base {
                            sum: (a, b) => { super.sum(...args) }
                        }
                        child.sum(2, 3)
                        """);

        assertEquals(BigInteger.valueOf(5), ((ProtosIntegerValue) result).value());
    }

    @Test
    void superLookupExhaustionSignalsSlotNotFound() throws IOException {
        ProtosPrelude prelude = corePrelude();

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                execute(
                                        prelude,
                                        """
                                        base: {
                                            missingFromParent: () => {
                                                super.noSuchSelector()
                                            }
                                        }
                                        base.missingFromParent()
                                        """));

        assertSame(
                ProtosCoreErrors.prototype(
                        prelude.newModuleActivation(),
                        StandardError.SLOT_NOT_FOUND),
                signal.error().parent().orElseThrow());
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

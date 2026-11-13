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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * I143 / D237: named calls from real Protos source through parser,
 * canonicalization, Bytecode emission, selection and invocation.
 *
 * This is deliberately independent of Java-created argument vectors.
 */
class ProtosI143NamedSourceExecutionTest {

    @Test
    void directCallUsesDefaultForAbsentFormal() throws Exception {
        Object result = execute(
                """
                f: (a: false, b: true, c: false) => b
                f(c: false)
                """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void explicitGuestNullSuppressesDefault() throws Exception {
        Object result = execute(
                """
                f: (a: true) => a
                f(a: null)
                """);

        assertSame(ProtosNullValue.INSTANCE, result);
    }

    @Test
    void mixedArgumentsKeepPositionalOrdinals() throws Exception {
        Object result = execute(
                """
                f: (a, b: true, c: true) => c
                f(false, c: false)
                """);

        assertSame(Boolean.FALSE, result);
    }

    @Test
    void spreadAndNamedArgumentsShareOneCall() throws Exception {
        Object result = execute(
                """
                f: (a, b, c) => c
                xs: [true, false]
                f(...xs, c: true)
                """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void namedOrdinarySendExecutesSelectedSourceMethod()
            throws Exception {
        Object result = execute(
                """
                receiver: {
                    execute: (value: true) => value
                }
                receiver.execute(value: false)
                """);

        assertSame(Boolean.FALSE, result);
    }

    @Test
    void customCallUsesItsOwnDeclaredName() throws Exception {
        Object result = execute(
                """
                receiver: {
                    call: (value) => value
                }
                receiver(value: true)
                """);

        assertSame(Boolean.TRUE, result);
    }

    @Test
    void standardObjectCallReturnsConstructedInstance()
            throws Exception {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation module = prelude.newModuleActivation();

        Object result = ProtosTestExecutionSupport.evaluate(
                """
                Thing: {
                    init: (value: false) => value
                }
                Thing(value: true)
                """,
                module);

        ProtosObjectValue prototype = assertInstanceOf(
                ProtosObjectValue.class,
                module.context()
                        .readLocalSlot("Thing").orElseThrow());

        ProtosObjectValue instance = assertInstanceOf(
                ProtosObjectValue.class, result);

        assertSame(prototype, instance.parent().orElseThrow());
    }

    @Test
    void unknownNamedFormalRaisesGuestError() throws Exception {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation module = prelude.newModuleActivation();

        ProtosSignalException failure = assertThrows(
                ProtosSignalException.class,
                () -> ProtosTestExecutionSupport.evaluate(
                        """
                        f: (value) => value
                        f(unknown: true)
                        """,
                        module));

        assertSame(
                ProtosCoreErrors.prototype(
                        module,
                        ProtosCoreErrors.StandardError.ERROR),
                failure.error().parent().orElseThrow());
    }

    @Test
    void missingMethodWinsBeforeNamedAssociation()
            throws Exception {
        ProtosPrelude prelude = corePrelude();
        ProtosActivation module = prelude.newModuleActivation();

        ProtosSignalException failure = assertThrows(
                ProtosSignalException.class,
                () -> ProtosTestExecutionSupport.evaluate(
                        """
                        receiver: {}
                        receiver.missing(unknown: true)
                        """,
                        module));

        assertSame(
                ProtosCoreErrors.prototype(
                        module,
                        ProtosCoreErrors.StandardError.SLOT_NOT_FOUND),
                failure.error().parent().orElseThrow());
    }

    private static Object execute(String source) throws Exception {
        return ProtosTestExecutionSupport.evaluate(
                source, corePrelude().newModuleActivation());
    }

    private static ProtosPrelude corePrelude() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(
                Path.of("protos", "lib", "core"));
    }
}

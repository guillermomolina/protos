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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * I115 / D237 / D239-C1: observable named-call semantics
 * through the current Bytecode DSL and shared dispatcher.
 * No historical prepared-call implementation is recreated.
 */
class ProtosI143SelectedSourcePreparationTest {

    @Test
    void directSourcePreservesAbsentDefaults() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                f: (a: 1, b: 2, c: 3) => a * 100 + b * 10 + c
                f(c: 7) == 127
                """));
    }

    @Test
    void selectedMethodPreservesActualReceiverAndHome() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                base: {
                    read: (a, b: 20, c: 30) => this.marker + a + c
                }
                receiver: base { marker: 7 }
                receiver.read(1, c: 2) == 10
                """));
    }

    @Test
    void explicitGuestNullIsPresent() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                f: (a: true, b: false) => a
                f(a: null) === null
                """));
    }

    @Test
    void collisionSignalsGuestErrorBeforeActivation() throws Exception {
        assertGuestError(
                """
                f: (a, b) => a
                f(1, a: 2)
                """,
                ProtosCoreErrors.StandardError.ERROR);
    }

    @Test
    void unknownNameSignalsGuestError() throws Exception {
        assertGuestError(
                """
                f: (a) => a
                f(missing: 1)
                """,
                ProtosCoreErrors.StandardError.ERROR);
    }

    @Test
    void customSourceCallDoesNotForwardUndeclaredNames() throws Exception {
        assertGuestError(
                """
                receiver: { call: (arg) => arg }
                receiver(other: 12)
                """,
                ProtosCoreErrors.StandardError.ERROR);
    }

    @Test
    void namedFormalCanLeaveRestEmpty() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                f: (a, b, c, ...rest) => rest.size()
                f(1, 2, c: 3) == 0
                """));
    }

    @Test
    void selectedImplementationDeterminesItsFormalNames() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                receiver: { call: (oldName) => oldName }
                receiver.call = (newName) => newName
                receiver(newName: 5) == 5
                """));
    }

    @Test
    void selectedSourceCanEnterPreparedBytecodeWithNamedValue() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                f: (a: 10, b: 20, c: 30) => c
                value: {}
                f(c: value) === value
                """));
    }


    private static Object evaluate(String source) throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(
                        Path.of("protos", "lib", "core"));
        return ProtosTestExecutionSupport.evaluate(
                source, prelude.newModuleActivation());
    }

    private static void assertGuestError(
            String source,
            ProtosCoreErrors.StandardError expected)
            throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(
                        Path.of("protos", "lib", "core"));
        ProtosActivation module = prelude.newModuleActivation();

        ProtosSignalException failure = assertThrows(
                ProtosSignalException.class,
                () -> ProtosTestExecutionSupport.evaluate(
                        source, module));

        assertSame(
                ProtosCoreErrors.prototype(module, expected),
                failure.error().parent().orElseThrow());
    }
}

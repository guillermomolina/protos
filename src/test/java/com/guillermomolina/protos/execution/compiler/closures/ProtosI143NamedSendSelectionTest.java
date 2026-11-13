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
class ProtosI143NamedSendSelectionTest {

    @Test
    void namedSendUsesActualReceiverAndMethodHome() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                base: {
                    read: (value) => this.marker + value
                }
                receiver: base { marker: 7 }
                receiver.read(value: 3) == 10
                """));
    }

    @Test
    void sourceDefaultsRunOnlyForAbsentFormals() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                receiver: {
                    read: (a: 10, b: 20) => a * 10 + b
                }
                receiver.read(b: 4) == 104
                """));
    }

    @Test
    void superUsesTheInheritedMethodNotTheCurrentOverride() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                base: {
                    read: (value) => this.marker + value
                }
                middle: base {
                    read: () => super.read(value: 2)
                }
                leaf: middle { marker: 40 }
                leaf.read() == 42
                """));
    }

    @Test
    void selectedCustomCallBindsItsOwnNames() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                receiver: { call: (arg) => arg + 1 }
                receiver(arg: 41) == 42
                """));
    }

    @Test
    void unknownNameFailsAgainstSelectedMethod() throws Exception {
        assertGuestError(
                """
                receiver: { execute: (valid) => valid }
                receiver.execute(invalid: 2)
                """,
                ProtosCoreErrors.StandardError.ERROR);
    }

    @Test
    void missingSelectedMethodFailsBeforeNameAssociation() throws Exception {
        assertGuestError(
                """
                receiver: {}
                receiver.noSuchMethod(unknown: 2)
                """,
                ProtosCoreErrors.StandardError.SLOT_NOT_FOUND);
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

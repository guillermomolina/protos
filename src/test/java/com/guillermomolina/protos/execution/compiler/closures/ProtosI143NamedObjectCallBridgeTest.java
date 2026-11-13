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
class ProtosI143NamedObjectCallBridgeTest {

    @Test
    void copiedStandardCallForwardsNamesToClosureDefaults() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                target: (a: true, b: false) => b
                target.call: Object.call
                target(b: true) === true
                """));
    }

    @Test
    void namedInitEvaluatesArgumentExactlyOnce() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                trace: { count: 0 }
                once: () => {
                    trace.count = trace.count + 1
                    trace.count
                }
                Thing: {
                    init: (value) => {
                        this.saved: value
                    }
                }
                made: Thing(value: once())
                trace.count == 1
                """));
    }

    @Test
    void customCallBindsOwnFormalsWithoutForwarding() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                receiver: { call: (value) => value }
                receiver(value: true) === true
                """));
    }

    @Test
    void customCallRejectsUndeclaredName() throws Exception {
        assertGuestError(
                """
                receiver: { call: (value) => value }
                receiver(unknown: true)
                """,
                ProtosCoreErrors.StandardError.ERROR);
    }

    @Test
    void copiedStandardCallForwardsToSelectedInit() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                Thing: {
                    init: (x: false, y: false) => {
                        this.saved: y
                    }
                }
                Thing.call: Object.call
                Thing.construct: Object.construct
                made: Thing(y: 12)
                made.saved == 12
                """));
    }

    @Test
    void copiedStandardCallWorksThroughDifferentSelector() throws Exception {
        assertSame(
                Boolean.TRUE,
                evaluate("""
                Thing: {
                    init: (value) => {
                        this.saved: value
                    }
                }
                Thing.factory: Object.call
                Thing.factory(value: 13).saved == 13
                """));
    }

    @Test
    void selectedInitRejectsUnknownNameBeforeItsBody() throws Exception {
        assertGuestError(
                """
                Thing: {
                    init: (expected) => expected
                }
                Thing(unknown: true)
                """,
                ProtosCoreErrors.StandardError.ERROR);
    }

    @Test
    void unrelatedNativeCallDoesNotAcquireNamedSignature() throws Exception {
        assertGuestError(
                """
                Array(element: 1)
                """,
                ProtosCoreErrors.StandardError.ERROR);
    }


    @Test
    void namedReturnAndCapturedInitializerLeaveOuterHomeLive() throws Exception {
        assertSame(Boolean.TRUE, evaluate("""
                named: (value: false) => { ^value }
                owner: () => {
                    Thing: {
                        init: (value: false) => { this.saved: value }
                    }
                    made: Thing(value: named(value: true))
                    escape: () => { ^made.saved }
                    escape()
                    false
                }
                owner() === true
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

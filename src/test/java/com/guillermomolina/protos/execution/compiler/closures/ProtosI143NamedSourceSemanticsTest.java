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
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * I143 / D237-MIX-C: source-level execution order, defaults,
 * dynamic lookup, nested super and non-local return.
 *
 * Each test starts with an independently bootstrapped Core Prelude.
 * No PreparedArgumentVector is constructed by these tests.
 */
class ProtosI143NamedSourceSemanticsTest {

    @Test
    void evaluatesNamedValuesInSourceOrderNotFormalOrder() throws Exception {
        assertTrueResult("""
                trace: { order: 0 }
                note: (digit) => {
                    trace.order = trace.order * 10 + digit
                    digit
                }
                f: (a, b, c) => trace.order
                f(c: note(3), a: note(1), b: note(2)) == 312
                """);
    }

    @Test
    void expandsSpreadAtItsSourcePositionAfterNamedValue() throws Exception {
        assertTrueResult("""
                trace: { order: 0 }
                note: (digit) => {
                    trace.order = trace.order * 10 + digit
                    digit
                }
                f: (a, b, c) => trace.order
                f(c: note(3), ...[note(1), note(2)]) == 312
                """);
    }

    @Test
    void evaluatesCalleeOnceBeforeItsNamedArguments() throws Exception {
        assertTrueResult("""
                trace: { order: 0 }
                f: (value) => value
                choose: () => {
                    trace.order = trace.order * 10 + 1
                    f
                }
                note: () => {
                    trace.order = trace.order * 10 + 2
                    true
                }
                (choose()(value: note()) === true) && (trace.order == 12)
                """);
    }

    @Test
    void missingDefaultsRunOnceInFormalOrderAfterSuppliedValues()
            throws Exception {
        assertTrueResult("""
                trace: { order: 0 }
                note: (digit) => {
                    trace.order = trace.order * 10 + digit
                    digit
                }
                f: (a: note(1), b: note(2), c: note(3)) => trace.order
                f(c: note(9)) == 912
                """);
    }

    @Test
    void namedCallInsideDefaultExpressionRunsNormally() throws Exception {
        assertTrueResult("""
                inner: (x, y: true) => x
                outer: (a: inner(y: true, x: false)) => a
                outer() === false
                """);
    }

    @Test
    void namedSuperSendRetainsDynamicReceiver() throws Exception {
        assertTrueResult("""
                base: {
                    read: (label) => this.mark
                }
                middle: base {
                    read: () => super.read(label: false)
                }
                leaf: middle { mark: true }
                leaf.read() === true
                """);
    }

    @Test
    void nestedClosureRetainsSuperLookupForNamedArguments()
            throws Exception {
        assertTrueResult("""
                base: {
                    read: (label) => this.mark
                }
                middle: base {
                    read: () => {
                        nested: () => super.read(label: false)
                        nested()
                    }
                }
                leaf: middle { mark: true }
                leaf.read() === true
                """);
    }

    @Test
    void namedNestedCallPreservesNonLocalReturnHome() throws Exception {
        assertTrueResult("""
                outer: (unused: true) => {
                    inner: (value: true) => { ^value }
                    inner(value: false)
                    true
                }
                outer(unused: true) === false
                """);
    }

    @Test
    void unknownNameFailsAfterAllSuppliedEffectsBeforeDefaults()
            throws Exception {
        ProtosActivation module = corePrelude().newModuleActivation();
        ProtosSignalException failure = assertThrows(
                ProtosSignalException.class,
                () -> ProtosTestExecutionSupport.evaluate("""
                        trace: { count: 0 }
                        note: () => {
                            trace.count = trace.count + 1
                            true
                        }
                        f: (a: note(), b: note()) => true
                        f(unknown: note(), b: note())
                        """, module));
        assertSame(module.prelude().orElseThrow().errorPrototype(),
                failure.error().parent().orElseThrow());
        assertTraceCount(module, "2");
    }

    @Test
    void positionalNameCollisionFailsAfterBothArgumentEffects()
            throws Exception {
        ProtosActivation module = corePrelude().newModuleActivation();
        ProtosSignalException failure = assertThrows(
                ProtosSignalException.class,
                () -> ProtosTestExecutionSupport.evaluate("""
                        trace: { count: 0 }
                        note: () => {
                            trace.count = trace.count + 1
                            true
                        }
                        f: (a: false, b: false) => a
                        f(note(), a: note())
                        """, module));
        assertSame(module.prelude().orElseThrow().errorPrototype(),
                failure.error().parent().orElseThrow());
        assertTraceCount(module, "2");
    }

    private static void assertTrueResult(String source) throws Exception {
        Object result = ProtosTestExecutionSupport.evaluate(
                source, corePrelude().newModuleActivation());
        assertSame(ProtosBooleanValue.TRUE, result);
    }

    private static void assertTraceCount(
            ProtosActivation module, String expected) {
        ProtosObjectValue trace = (ProtosObjectValue)
                module.context().readLocalSlot("trace").orElseThrow();
        Object value = trace.readLocalSlot("count").orElseThrow();
        assertEquals(expected,
                ProtosNumericValueSupport.integerDecimalText(value));
    }

    private static ProtosPrelude corePrelude() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(
                Path.of("protos", "lib", "core"));
    }
}

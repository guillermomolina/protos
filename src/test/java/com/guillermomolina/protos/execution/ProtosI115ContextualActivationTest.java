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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I115: contextual control retains the per-activation lexical semantics
 * of standard Closure whileTrue/ifTrue while using native Bytecode DSL.
 */
final class ProtosI115ContextualActivationTest {

    @Test
    void bracketReadInsideWhileCreatesFreshBindingEveryIteration() {
        assertEquals(3L, evalLong("""
                leaves: Array({ corpus: 1 }, { corpus: 2 })
                resolveIndex: 0
                total: 0
                while(resolveIndex < leaves.size()) {
                    leaf: leaves[resolveIndex]
                    total = total + leaf.corpus
                    resolveIndex = resolveIndex + 1
                }
                total
                """));
    }

    @Test
    void explicitAtInsideWhileCreatesFreshBindingEveryIteration() {
        assertEquals(3L, evalLong("""
                leaves: Array({ corpus: 1 }, { corpus: 2 })
                resolveIndex: 0
                total: 0
                while(resolveIndex < leaves.size()) {
                    leaf: leaves.at(resolveIndex)
                    total = total + leaf.corpus
                    resolveIndex = resolveIndex + 1
                }
                total
                """));
    }

    @Test
    void conditionCallbackCreatesFreshBindingEveryActivation() {
        assertEquals(3L, evalLong("""
                i: 0
                (() => {
                    visited: i
                    i < 3
                }).whileTrue(() => {
                    i = i + 1
                })
                i
                """));
    }

    @Test
    void nestedLoopsDoNotReuseInnerBindings() {
        assertEquals(4L, evalLong("""
                outer: 0
                count: 0
                while(outer < 2) {
                    inner: 0
                    while(inner < 2) {
                        item: count
                        count = count + 1
                        inner = inner + 1
                    }
                    outer = outer + 1
                }
                count
                """));
    }

    @Test
    void observableBodyContextsAreDistinctPerIteration() {
        assertTrue(evalBoolean("""
                first: null
                second: null
                i: 0
                while(i < 2) {
                    own: i
                    bodyContext: context
                    if(i == 0) {
                        first = bodyContext
                    } else {
                        second = bodyContext
                    }
                    i = i + 1
                }
                (first !== second) &&
                    (first.own == 0) &&
                    (second.own == 1)
                """));
    }

    @Test
    void capturedBindingsBelongToTheirOriginalIteration() {
        assertEquals(1L, evalLong("""
                first: null
                second: null
                i: 0
                while(i < 2) {
                    own: i
                    reader: () => own
                    if(i == 0) {
                        first = reader
                    } else {
                        second = reader
                    }
                    i = i + 1
                }
                first() * 10 + second()
                """));
    }

    @Test
    void contextualIfInsideLoopRecreatesItsOwnBindings() {
        assertEquals(3L, evalLong("""
                i: 0
                total: 0
                while(i < 2) {
                    if(true) {
                        item: i
                        total = total + item + 1
                    }
                    i = i + 1
                }
                total
                """));
    }

    @Test
    void suspendedBodyResumesWithoutRepeatingItsCreation() {
        assertEquals(2L, evalLong("""
                work: () => {
                    i: 0
                    while(i < 2) {
                        snapshot: i
                        pending: (() => { snapshot + 1 }).future()
                        pending.value()
                        i = i + 1
                    }
                    i
                }
                work.future().value()
                """));
    }


    @Test
    void whileFalseRecreatesItsBodyBindings() {
        assertEquals(3L, evalLong("""
                i: 0
                (() => i >= 3).whileFalse(() => {
                    visited: i
                    i = i + 1
                })
                i
                """));
    }

    @Test
    void untilTrueRecreatesItsPostTestBodyBindings() {
        assertEquals(3L, evalLong("""
                i: 0
                (() => {
                    visited: i
                    i = i + 1
                }).untilTrue(() => i >= 3)
                i
                """));
    }

    @Test
    void zeroIterationDoesNotExecuteBodyCreation() {
        assertEquals(9L, evalLong("""
                original: 9
                while(false) {
                    original: 1
                }
                original
                """));
    }

    @Test
    void immediatelyInvokedInlineClosureHasFreshLocals() {
        assertEquals(1L, evalLong("""
                i: 0
                sum: 0
                while(i < 2) {
                    value: (() => {
                        number: i
                        number
                    })()
                    sum = sum + value
                    i = i + 1
                }
                sum
                """));
    }

    @Test
    void repeatedCreationInsideOneActivationStillFails() {
        AssertionError failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        AssertionError.class,
                () -> evalLong("""
                        i: 0
                        while(i < 1) {
                            item: 1
                            item: 2
                            i = i + 1
                        }
                        i
                        """));
        assertTrue(
                failure.getCause()
                        instanceof org.graalvm.polyglot.PolyglotException);
    }

    private static long evalLong(String source) {
        try (Context context = context()) {
            return context.eval(ProtosLanguage.ID, source).asLong();
        } catch (org.graalvm.polyglot.PolyglotException failure) {
            throw diagnosticFailure(source, failure);
        }
    }

    private static boolean evalBoolean(String source) {
        try (Context context = context()) {
            return context.eval(ProtosLanguage.ID, source).asBoolean();
        } catch (org.graalvm.polyglot.PolyglotException failure) {
            throw diagnosticFailure(source, failure);
        }
    }

    private static AssertionError diagnosticFailure(
            String source,
            org.graalvm.polyglot.PolyglotException failure) {
        StringBuilder details = new StringBuilder();
        details.append("I115_GUEST_FAILURE: ")
                .append(failure.getMessage())
                .append("\n")
                .append("guestException=")
                .append(failure.isGuestException())
                .append("\n");

        for (var frame : failure.getPolyglotStackTrace()) {
            if (!frame.isGuestFrame()) {
                continue;
            }
            details.append("guestFrame=")
                    .append(frame.getRootName());

            var location = frame.getSourceLocation();
            if (location != null) {
                details.append(" at ")
                        .append(location.getSource().getName())
                        .append(":")
                        .append(location.getStartLine())
                        .append(":")
                        .append(location.getStartColumn());
            }
            details.append("\n");
        }

        details.append("source:\n").append(source);
        return new AssertionError(details.toString(), failure);
    }

    private static Context context() {
        String core =
                Path.of("protos", "lib", "core")
                        .toAbsolutePath()
                        .toString();
        return Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", core)
                .build();
    }
}

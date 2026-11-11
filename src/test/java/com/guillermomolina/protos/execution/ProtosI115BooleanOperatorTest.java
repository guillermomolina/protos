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
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

/**
 * I115: native short-circuit {@code !}, {@code &&} and {@code ||}
 * (VALUES_AND_COLLECTIONS §16, PROTOS_GRAMMAR §23) over canonical and
 * primitive-comparison Booleans, with ordinary dispatch kept for custom
 * receivers.
 */
final class ProtosI115BooleanOperatorTest {

    private static final String BUMP =
            "n: 0\nbump: () => {\n    n = n + 1\n    true\n}\n";

    @Test
    void literalOperands() {
        assertTrue(evalBoolean("(true && false) === false"));
        assertTrue(evalBoolean("(false || true) === true"));
        assertTrue(evalBoolean("!(true) === false"));
        assertTrue(evalBoolean("!(1 < 2) === false"));
    }

    @Test
    void primitiveComparisonsFeedOperators() {
        String vars = "a: 3\nb: 5\n";
        assertTrue(evalBoolean(vars + "((a < b) && (b > a)) === true"));
        assertTrue(evalBoolean(vars + "((a > b) && (b > a)) === false"));
        assertTrue(evalBoolean(vars + "((a > b) || (a <= 3)) === true"));
        assertTrue(evalBoolean(vars + "((a > b) || (a >= 4)) === false"));
        assertTrue(evalBoolean(vars + "!(a > b) === true"));
        assertTrue(evalBoolean(vars + "!(a < b) === false"));
        assertTrue(evalBoolean(vars + "(true && (a < b)) === true"));
        assertTrue(evalBoolean(vars + "(false || (a > b)) === false"));
    }

    @Test
    void nestedCombinationsAndPrecedence() {
        assertTrue(evalBoolean("(true || false && false) === true"));
        assertTrue(evalBoolean("(!false && false) === false"));
        assertTrue(evalBoolean("((false || true) && !(2 < 1)) === true"));
        assertTrue(evalBoolean("!(!(1 < 2) || (3 < 2)) === true"));
        assertTrue(evalBoolean("x: 7\n((x > 0) && (x < 5) || (x > 6) && !(x > 8)) === true"));
    }

    @Test
    void unselectedRhsDoesNotExecute() {
        assertEquals(0L, evalLong(BUMP + "r: false && bump()\nn"));
        assertEquals(0L, evalLong(BUMP + "r: true || bump()\nn"));
        assertEquals(0L, evalLong(BUMP + "r: (2 < 1) && bump()\nn"));
        assertEquals(0L, evalLong(BUMP + "r: (1 < 2) || bump()\nn"));
        assertEquals(0L, evalLong(BUMP + "false && bump()\nn"));
    }

    @Test
    void selectedRhsExecutesExactlyOnce() {
        assertEquals(1L, evalLong(BUMP + "r: true && bump()\nn"));
        assertEquals(1L, evalLong(BUMP + "r: false || bump()\nn"));
        assertEquals(1L, evalLong(BUMP + "r: (1 < 2) && bump()\nn"));
        assertEquals(1L, evalLong(BUMP + "true && bump()\nn"));
        assertEquals(11L, evalLong(BUMP
                + "r: (1 < 2) && bump()\n(r === true).ifTrueIfFalse(() => { 10 }, () => { 0 }) + n"));
    }

    @Test
    void nonBooleanSelectedRhsSignalsError() {
        assertTrue(signals("true && 1"));
        assertTrue(signals("false || null"));
        assertTrue(signals("(1 < 2) && 3"));
        assertTrue(signals("(2 < 1) || \"x\""));
        // Effects completed before the invalid result are not rolled back.
        assertEquals(1L, evalLong(BUMP
                + "Error.handle(() => { true && (bump() && 5) }, (caught) => null)\nn"));
    }

    @Test
    void nonBooleanReceiverWithoutBehaviorSignalsError() {
        assertTrue(signals("1 && true"));
        assertTrue(signals("null || true"));
        assertTrue(signals("!(3)"));
    }

    @Test
    void booleanResultsAreCanonicalGuestValues() {
        assertTrue(evalBoolean("x: (1 < 2) && (2 < 3)\nx === true"));
        assertTrue(evalBoolean("x: (1 < 2) && (3 < 2)\nf: () => { x }\nf() === false"));
        assertTrue(evalBoolean("g: (v) => { (v > 3) || false }\n(g(4) === true) && (g(1) === false)"));
        assertTrue(evalBoolean("h: () => { !(1 < 2) }\nh() === false"));
        assertTrue(evalBoolean("a: [(1 < 2) && true, !(1 < 2)]\n(a[0] === true) && (a[1] === false)"));
        try (Context context = context()) {
            Value value = context.eval(ProtosLanguage.ID, "(1 < 2) && (2 < 3)");
            assertTrue(value.isBoolean());
            assertTrue(value.asBoolean());
        }
    }

    @Test
    void customReceiversKeepOrdinaryMethodBehavior() {
        String custom = "custom: {\n"
                + "    and: (rhs) => { 42 }\n"
                + "    or: (rhs) => { rhs() + 1 }\n"
                + "    not: () => { 7 }\n"
                + "}\n";
        assertEquals(42L, evalLong(custom + "custom && false"));
        assertEquals(6L, evalLong(custom + "custom || 5"));
        assertEquals(7L, evalLong(custom + "!custom"));
        // One site sees canonical and custom receivers.
        assertEquals(42L, evalLong(custom
                + "op: (v) => { v && true }\n"
                + "(op(true) === true).ifTrue(() => {\n"
                + "    (op(false) === false).ifTrue(() => { op(custom) })\n"
                + "})"));
    }

    @Test
    void customReceiverObservesGeneratedRhsClosure() {
        String observer = "observer: { and: (rhs) => { rhs } }\n";
        assertEquals(4L, evalLong(observer + "k: 3\nc: observer && (k + 1)\nc()"));
        // Lexical capture by reference: the RHS sees a later assignment.
        assertEquals(9L, evalLong(observer + "k: 3\nc: observer && k\nk = 9\nc()"));
        // The RHS body was not evaluated when the Closure was created.
        assertEquals(0L, evalLong(BUMP + observer + "c: observer && bump()\nn"));
        assertEquals(1L, evalLong(BUMP + observer + "c: observer && bump()\nc()\nn"));
    }

    @Test
    void rhsLexicalContextAndCapture() {
        assertTrue(evalBoolean("f: (lo, hi) => {\n"
                + "    probe: (v) => { (v >= lo) && (v <= hi) }\n"
                + "    probe(5)\n"
                + "}\n"
                + "(f(1, 9) === true) && (f(6, 9) === false)"));
        assertEquals(3L, evalLong(BUMP
                + "[1, 5, 7, 9].each((v) => {\n"
                + "    big: (v > 2) && bump()\n"
                + "    (big === true).ifTrue(() => { null })\n"
                + "})\nn"));
    }

    @Test
    void rhsNonLocalReturnAndErrorPropagate() {
        assertEquals(5L, evalLong("f: (b) => {\n"
                + "    r: b && (() => { ^ 5 })()\n"
                + "    9\n"
                + "}\nf(true)"));
        assertEquals(9L, evalLong("f: (b) => {\n"
                + "    r: b && (() => { ^ 5 })()\n"
                + "    9\n"
                + "}\nf(false)"));
        assertTrue(signals("true && ([1][5] == 1)"));
        assertTrue(evalBoolean("(false && ([1][5] == 1)) === false"));
    }

    @Test
    void rhsMayWaitForFuture() {
        assertTrue(evalBoolean("pending: (() => { 41 }).future()\n"
                + "(true && (pending.value() == 41)) === true"));
        assertTrue(evalBoolean("pending: (() => { 41 }).future()\n"
                + "(false || (pending.value() < 41)) === false"));
    }

    @Test
    void contextualControlsStayNativeAndCorrect() {
        assertEquals(4L, evalLong("i: 0\nn: 0\n"
                + "while(i < 10) {\n"
                + "    if((i >= 3) && (i < 7)) { n = n + 1 }\n"
                + "    i = i + 1\n"
                + "}\nn"));
        assertEquals(2L, evalLong("x: 5\n"
                + "if(!(x < 3) || (x > 100)) { 2 } else { 1 }"));
        assertEquals(4L, evalLong("i: 0\n"
                + "while((i < 10) && !(i == 4)) { i = i + 1 }\ni"));
        assertEquals(3L, evalLong("n: 0\n"
                + "(() => { n = n + 1 }).untilTrue(() => { (n >= 3) || (n < 0) })\nn"));
        assertEquals(0L, evalLong("n: 0\n"
                + "(() => { (n > 0) && (n < 0) }).whileTrue(() => { n = n + 1 })\nn"));
    }

    @Test
    void comparisonReturnedDirectlyFromClosureIsCanonical() {
        assertTrue(evalBoolean("f: (v) => { v > 3 }\nf(4) === true"));
        assertTrue(evalBoolean("f: (v) => { v > 3 }\nf(1) === false"));
        assertTrue(evalBoolean("f: () => { 1 < 2 }\nf() === true"));
        try (Context context = context()) {
            Value value = context.eval(ProtosLanguage.ID, "f: (v) => { v >= 65 }\nf(70)");
            assertTrue(value.isBoolean());
            assertTrue(value.asBoolean());
        }
        try (Context context = context()) {
            Value value = context.eval(ProtosLanguage.ID, "1 < 2");
            assertTrue(value.isBoolean());
            assertTrue(value.asBoolean());
        }
    }

    @Test
    void primitiveComparisonIdentityWithCanonicalBooleans() {
        assertTrue(evalBoolean("(1 < 2) === true"));
        assertTrue(evalBoolean("(2 < 1) === false"));
        assertTrue(evalBoolean("true === (1 < 2)"));
        assertTrue(evalBoolean("((1 < 2) === false) === false"));
        assertTrue(evalBoolean("((1 < 2) !== false) === true"));
        assertTrue(evalBoolean("x: 5\n((x > 3) === (x > 4)) === true"));
    }

    @Test
    void composedBooleansAsIdentityOperands() {
        String vars = "a: 3\nb: 5\n";
        assertTrue(evalBoolean(vars + "(((a < b) && (b > a)) === true) === true"));
        assertTrue(evalBoolean(vars + "(true === ((a < b) || (b < a))) === true"));
        assertTrue(evalBoolean(vars + "(((a > b) || (a > b)) === false) === true"));
        assertTrue(evalBoolean(vars + "(!(a > b) === ((a < b) && true)) === true"));
    }

    @Test
    void nestedAndInsideOrAndReverse() {
        String isAlpha = "isAlpha: (octet) => {\n"
                + "    ((octet >= 65) && (octet <= 90)) ||\n"
                + "        ((octet >= 97) && (octet <= 122))\n"
                + "}\n";
        assertTrue(evalBoolean(isAlpha + "isAlpha(106) === true"));
        assertTrue(evalBoolean(isAlpha + "isAlpha(70) === true"));
        assertTrue(evalBoolean(isAlpha + "isAlpha(48) === false"));
        assertTrue(evalBoolean(isAlpha + "isAlpha(123) === false"));
        String inRange = "f: (v) => {\n"
                + "    ((v < 0) || (v > 9)) && ((v < 20) || (v > 29))\n"
                + "}\n";
        assertTrue(evalBoolean(inRange + "f(15) === true"));
        assertTrue(evalBoolean(inRange + "f(5) === false"));
        assertTrue(evalBoolean(inRange + "f(25) === false"));
        assertTrue(evalBoolean("x: 4\n"
                + "(((x > 1) && ((x < 3) || ((x > 3) && ((x < 5) || false))))) === true"));
    }

    @Test
    void nonInlineRhsSelectedAndUnselected() {
        // A RHS that may observe its ReturnHome is not inline-admissible
        // and runs as a materialized Closure on the native path.
        String andSite = BUMP + "f: (b) => {\n"
                + "    r: b && (() => {\n"
                + "        bump()\n"
                + "        ^ 7\n"
                + "    })()\n"
                + "    9\n"
                + "}\n";
        String orSite = andSite.replace("b && (", "b || (");
        assertEquals(7L, evalLong(andSite + "f(true)"));
        assertEquals(1L, evalLong(andSite + "r: f(true)\nn"));
        assertEquals(9L, evalLong(andSite + "f(false)"));
        assertEquals(0L, evalLong(andSite + "r: f(false)\nn"));
        assertEquals(7L, evalLong(orSite + "f(false)"));
        assertEquals(1L, evalLong(orSite + "r: f(false)\nn"));
        assertEquals(9L, evalLong(orSite + "f(true)"));
        assertEquals(0L, evalLong(orSite + "r: f(true)\nn"));
        assertTrue(evalBoolean("f: (b) => { b && (() => { ^ true })() }\nf(false) === false"));
        assertTrue(evalBoolean("f: (b) => { b || (() => { ^ false })() }\nf(true) === true"));
        assertEquals(5L, evalLong("f: () => { true && (() => { ^ 5 })() }\nf()"));
    }

    @Test
    void nonInlineRhsCapturesLexicalContext() {
        assertEquals(2L, evalLong("f: (lo) => {\n"
                + "    hits: 0\n"
                + "    check: (v) => {\n"
                + "        (v > lo) && (() => {\n"
                + "            hits = hits + 1\n"
                + "            true\n"
                + "        })()\n"
                + "    }\n"
                + "    check(5)\n"
                + "    check(0)\n"
                + "    check(7)\n"
                + "    hits\n"
                + "}\nf(1)"));
    }

    @Test
    void nonInlineSelectedRhsMaySuspend() {
        assertTrue(evalBoolean("pending: (() => { 41 }).future()\n"
                + "f: (b) => { b && (() => { pending.value() == 41 })() }\n"
                + "f(true) === true"));
        assertTrue(evalBoolean("pending: (() => { 41 }).future()\n"
                + "f: (b) => { b || (() => { ^ pending.value() < 41 })() }\n"
                + "f(false) === false"));
    }

    @Test
    void customReceiverOverridesWithNonInlineRhs() {
        String custom = "custom: {\n"
                + "    and: (rhs) => { rhs() + 100 }\n"
                + "    or: (rhs) => { 3 }\n"
                + "}\n";
        // The custom and invokes the RHS, whose ^ returns from f.
        assertEquals(5L, evalLong(custom
                + "f: (r) => { r && (() => { ^ 5 })() }\nf(custom)"));
        assertEquals(3L, evalLong(custom + "custom || (() => { 1 })()"));
        assertEquals(0L, evalLong(BUMP + custom + "r: custom || bump()\nn"));
    }

    private static boolean signals(String body) {
        return evalBoolean(
                "Error.handle(() => {\n" + body + "\nfalse\n}, (caught) => true)");
    }

    private static long evalLong(String source) {
        try (Context context = context()) {
            return context.eval(ProtosLanguage.ID, source).asLong();
        }
    }

    private static boolean evalBoolean(String source) {
        try (Context context = context()) {
            return context.eval(ProtosLanguage.ID, source).asBoolean();
        }
    }

    private static Context context() {
        String core = Path.of("protos", "lib", "core").toAbsolutePath().toString();
        return Context.newBuilder(ProtosLanguage.ID).option("protos.CoreRoot", core).build();
    }
}

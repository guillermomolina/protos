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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import java.math.BigInteger;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I091: primitive carriers retained by frame storage and direct source-call
 * arguments are observed only as ordinary guest Integer/Float values.
 */
final class ProtosI091PrimitiveCarrierPropagationTest {
    @Test
    void definitionsAndAssignmentsRetainCarriers() {
        try (Context context = context()) {
            assertEquals(30, eval(context, "a: 1 + 2\na * 10").asInt());
            assertEquals(2, eval(context, "a: 0\na = a + 1\na = a + 1\na").asInt());
            assertEquals(7, eval(context, "a: 3 + 4\nb: a\nb").asInt());
            assertEquals(4.0d, eval(context, "a: 1.5 + 2.5\na").asDouble());
        }
    }

    @Test
    void overflowFromRetainedCarrierStaysExact() {
        try (Context context = context()) {
            assertEquals(new BigInteger("9223372036854775808"),
                    eval(context, "a: 9223372036854775806 + 1\nb: a + 1\nb").asBigInteger());
        }
    }

    @Test
    void loopCounterCapturedByCallbacks() {
        try (Context context = context()) {
            assertEquals(4950, eval(context,
                    "total: 0\ni: 0\n"
                            + "(() => i < 100).whileTrue(() => {\n"
                            + "    total = total + i\n"
                            + "    i = i + 1\n"
                            + "})\n"
                            + "total").asInt());
        }
    }

    @Test
    void escapingClosureObservesGuestValue() {
        try (Context context = context()) {
            assertEquals(5, eval(context, "a: 2 + 3\nf: () => a\nf()").asInt());
            assertEquals(6, eval(context, "a: 2 + 3\nf: () => a + 1\nf()").asInt());
        }
    }

    @Test
    void directSourceArgumentsAndReturns() {
        try (Context context = context()) {
            assertEquals(42, eval(context,
                    "o: { twice: x => x + x }\nn: 20 + 1\no.twice(n)").asInt());
            assertEquals(2.5d, eval(context,
                    "o: { half: x => x / 2 }\no.half(3 + 2)").asDouble());
            assertEquals(12, eval(context,
                    "o: { keep: x => {\n y: x\n y } }\no.keep(5 + 7)").asInt());
        }
    }

    @Test
    void directSourceReturnsReachTheCallerAndTheHost() {
        try (Context context = context()) {
            // The callee's final arithmetic result returns to an accepting caller operand.
            assertEquals(7, eval(context, "o: { inc: x => x + 1 }\no.inc(5) + 1").asInt());
            // A returned value stored and fed back through a loop.
            assertEquals(10, eval(context,
                    "o: { inc: x => x + 1 }\nn: 0\ni: 0\n"
                            + "(() => i < 10).whileTrue(() => {\n"
                            + "    n = o.inc(n)\n"
                            + "    i = i + 1\n"
                            + "})\n"
                            + "n").asInt());
            // A final retained read returns as a guest value to a non-accepting caller.
            assertEquals(42, eval(context, "o: { id: x => {\n y: x + 0\n y } }\no.id(42)").asInt());
            assertEquals(1.5d, eval(context, "o: { half: x => x / 2.0 }\no.half(3)").asDouble());
            // Overflow inside the callee still returns the exact result.
            assertEquals(new BigInteger("9223372036854775808"), eval(context,
                    "o: { inc: x => x + 1 }\no.inc(9223372036854775807)").asBigInteger());
        }
    }

    @Test
    void retainedCarriersKeepValueIdentityAndEquality() {
        try (Context context = context()) {
            assertTrue(eval(context, "a: 1 + 2\nb: 3\na === b").asBoolean());
            assertTrue(eval(context, "a: 1.5 + 1.5\na == 3").asBoolean());
            assertFalse(eval(context, "a: 0.0 * (0.0 - 1.0)\nb: 0.0\na === b").asBoolean());
        }
    }

    @Test
    void carrierMaterializationIsTheOnlyRecognition() {
        Object integer = ProtosNumericValueSupport.guestValue(41L);
        Object floating = ProtosNumericValueSupport.guestValue(0.5d);
        assertEquals(41L, ((ProtosIntegerValue) integer).smallValueForRuntime());
        assertEquals(0.5d, ((ProtosFloatValue) floating).value());
        Object other = new Object();
        assertSame(other, ProtosNumericValueSupport.guestValue(other));
        // A host Long is never itself a current Protos number.
        assertFalse(ProtosNumericValueSupport.isCurrentNumber(41L));
    }

    private static org.graalvm.polyglot.Value eval(Context context, String source) {
        return context.eval(ProtosLanguage.ID, source);
    }

    private static Context context() {
        String core = Path.of("protos", "lib", "core").toAbsolutePath().toString();
        return Context.newBuilder(ProtosLanguage.ID).option("protos.CoreRoot", core).build();
    }
}

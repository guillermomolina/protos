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

import java.math.BigInteger;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/** I091-C2: numeric carriers remain internal to guarded arithmetic. */
final class ProtosI091PrimitiveIntegerSendTest {
    @Test
    void chainedArithmeticPreservesGuestResult() {
        try (Context context = context()) {
            assertEquals(5, context.eval(ProtosLanguage.ID, "((1 + 2) * 3) - 4").asInt());
        }
    }

    @Test
    void localsRemainOrdinaryGuestOperands() {
        try (Context context = context()) {
            assertEquals(11, context.eval(ProtosLanguage.ID, "a: 6\nb: 5\na + b").asInt());
        }
    }

    @Test
    void overflowPreservesExactArithmetic() {
        try (Context context = context()) {
            assertEquals(new BigInteger("9223372036854775808"),
                    context.eval(ProtosLanguage.ID, "9223372036854775807 + 1")
                            .asBigInteger());
        }
    }

    @Test
    void mixedFloatAndBooleanControlKeepOriginalSemantics() {
        try (Context context = context()) {
            assertEquals(3.5d, context.eval(ProtosLanguage.ID, "1 + 2.5").asDouble());
            assertEquals(3, context.eval(ProtosLanguage.ID, "true.ifTrue() { 1 + 2 }")
                    .asInt());
        }
    }

    private static Context context() {
        String core = Path.of("protos", "lib", "core").toAbsolutePath().toString();
        return Context.newBuilder(ProtosLanguage.ID).option("protos.CoreRoot", core).build();
    }
}

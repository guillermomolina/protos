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

import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

final class ProtosPrimitiveSlotWriteRegressionTest {
    @Test
    void repeatedWriteThenReadReturnsCurrentPrimitive() {
        String core =
                Path.of("protos", "lib", "core").toAbsolutePath().toString();
        try (Context context = Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", core)
                .build()) {
            int result = context.eval(ProtosLanguage.ID, """
                    repeat: (count, operation) => {
                        (count > 0).ifTrue() {
                            operation()
                            repeat(count - 1, operation)
                        }
                    }

                    holder: { value: 1 }

                    run: () => {
                        holder.value = 2
                        holder.value
                    }

                    repeat(200, () => {
                        holder.value = 1
                        run()
                    })
                    run()
                    """).asInt();
            assertEquals(2, result);
        }
    }
}

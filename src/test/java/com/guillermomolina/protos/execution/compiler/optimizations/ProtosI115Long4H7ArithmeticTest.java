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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** I115-LONG-4: no TryPrimitiveNumeric/MISS in compact - and * roots. */
final class ProtosI115Long4H7ArithmeticTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path OUTPUT = Path.of("target", "i115-long4");

    @Test
    void subtractUsesDirectNumberSubtract() throws Exception {
        check("subtract", "run: () => { 9 - 4 }\n", "c.NumberSubtract", 5L);
    }

    @Test
    void multiplyUsesDirectNumberMultiply() throws Exception {
        check("multiply", "run: () => { 9 * 4 }\n", "c.NumberMultiply", 36L);
    }

    private static void check(String name, String source, String opcode, long expected)
            throws Exception {
        Files.createDirectories(OUTPUT);
        Path sourceFile = OUTPUT.resolve(name + ".protos");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(CORE, sourceFile)) {
            assertEquals(ProtosExecutionOutcome.State.COMPLETED,
                    session.initialOutcome().state(), name);
            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");
            ProtosBytecodeRootNode run = assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    prepared.hostExecutableForTesting().target.getRootNode());
            List<String> operations = run.getBytecodeNode().getInstructionsAsList()
                    .stream().map(ProtosInstructionNames::of)
                    .map(op -> {
                        int quickened = op.indexOf('$');
                        return quickened < 0 ? op : op.substring(0, quickened);
                    }).toList();
            assertTrue(operations.contains(opcode), operations.toString());
            assertFalse(operations.stream().anyMatch(op ->
                    op.contains("TryPrimitiveNumeric")
                            || op.contains("PrimitiveNumericSendMiss")
                            || op.contains("MaterializePrimitiveNumericCarrier")),
                    operations.toString());
            assertEquals(expected, prepared.executable().execute().asLong());
        }
    }
}

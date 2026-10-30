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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

final class ProtosPrimitiveSlotWriteRegressionTest {

    /**
     * I115-H8: the primitive-object-slot-write workload must use
     * the compact SetAttribute path, not generic slot mutation.
     */
    @Test
    void h8ObjectSlotWriteUsesDirectCompactBytecode() throws Exception {
        Path directory = Path.of("target", "i115-h8-slot-write");
        Files.createDirectories(directory);

        Path source = directory.resolve("primitive-object-slot-write.protos");
        Files.writeString(source, """
                holder: {
                    value: 1
                }

                run: () => {
                    holder.value = 2
                    holder.value
                }
                """, StandardCharsets.UTF_8);

        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(
                        Path.of("protos", "lib", "core"), source)) {

            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    session.initialOutcome().state());

            ProtosStandaloneHostedSession.PreparedTopLevel prepared =
                    session.prepareTopLevel("run");

            ProtosBytecodeRootNode root = assertInstanceOf(
                    ProtosBytecodeRootNode.class,
                    prepared.hostExecutableForTesting()
                            .target.getRootNode());

            List<String> instructions =
                    root.getBytecodeNode()
                            .getInstructionsAsList()
                            .stream()
                            .map(ProtosInstructionNames::of)
                            .map(name -> {
                                int index = name.indexOf('$');
                                return index < 0
                                        ? name
                                        : name.substring(0, index);
                            })
                            .toList();

            System.out.println(
                    "I115_H8_RUN_INSTRUCTIONS=" + instructions);

            assertEquals(
                    1L,
                    instructions.stream()
                            .filter("c.ExperimentalSetAttribute"::equals)
                            .count(),
                    "Exactly one direct SetAttribute is required");

            assertEquals(
                    2L,
                    instructions.stream()
                            .filter("c.ReadGlobal"::equals)
                            .count(),
                    "Write and read must use direct global access");

            assertEquals(
                    1L,
                    instructions.stream()
                            .filter("c.ReadMemberAtRoot"::equals)
                            .count(),
                    "Read must use the existing compact member PIC");

            for (String forbidden : List.of(
                    "MutateLocalSlot",
                    "RequireObjectMutationTarget",
                    "PrepareSend",
                    "AccessNamedLexical",
                    "PromoteFrameLexicalAuthority")) {
                assertTrue(
                        instructions.stream()
                                .noneMatch(name -> name.contains(forbidden)),
                        "Generic operation remains: " + forbidden
                                + " in " + instructions);
            }

            assertTrue(
                    root.isExperimentalH7RawIntegerReturn(),
                    "H8 must reuse R5 raw Integer return transport");

            for (int iteration = 0; iteration < 48; iteration++) {
                assertEquals(
                        2L,
                        prepared.executable().execute().asLong());
            }

            ProtosClosureValue closure =
                    prepared.closureForTesting();

            session.processContextForTesting().callForRuntime(() -> {
                ProtosLexicalEnvironment module =
                        closure.capturedLexicalEnvironmentForRuntime();

                ProtosObjectValue holder = assertInstanceOf(
                        ProtosObjectValue.class,
                        module.readLocalSlotForRuntime("holder")
                                .orElseThrow());

                Object stored =
                        holder.readLocalSlot("value").orElseThrow();

                assertEquals(
                        2,
                        assertInstanceOf(Integer.class, stored).intValue(),
                        "Direct store must retain its raw Integer carrier");

                return null;
            });

            System.out.println("I115_H8_COMPACT_WRITE=PASS");
            System.out.println("I115_H8_EXPECTED_RESULT=2");
        }
    }

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

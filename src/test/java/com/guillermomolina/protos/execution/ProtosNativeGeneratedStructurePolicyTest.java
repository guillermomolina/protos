/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS.
 * "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY OF THE
 * LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING THE
 * CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS FILE, A
 * COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */

package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosNativeGeneratedStructurePolicyTest {
    private static final String PACKAGE =
            "com.guillermomolina.protos.execution.";

    private static final String RUNTIME_ROOT =
            "ProtosBytecodeRootNodeGen";

    private static final String SEMANTIC_ROOT =
            "ProtosSemanticBytecodeRootNodeGen";

    @TempDir Path temporary;

    @Test
    void generatedBytecodeShapeAndNativeInitPolicyRemainAligned()
            throws Exception {
        Path repository =
                Path.of(System.getProperty("user.dir"))
                        .toAbsolutePath()
                        .normalize();

        Path classes = repository.resolve("target/classes");
        Path execution =
                classes.resolve(
                        "com/guillermomolina/protos/execution");

        /*
         * PLAT042 B′: the semantic source interpreter and the untagged
         * structured/C-prime interpreter share the same tier configuration
         * (uncached interpreter, tail-call handlers, materialized locals), so
         * both generate the same structural roles.
         */
        Set<String> interpreterRoles =
                Set.of(
                        "AbstractBytecodeNode",
                        "CachedBytecodeNode",
                        "CachedBytecodeNodeTailCall",
                        "TagNode",
                        "UncachedBytecodeNode",
                        "UncachedBytecodeNodeTailCall",
                        "VirtualState");

        assertEquals(
                interpreterRoles,
                sensitiveDirectRoles(execution, RUNTIME_ROOT));

        assertEquals(
                interpreterRoles,
                sensitiveDirectRoles(execution, SEMANTIC_ROOT));

        Path output = temporary.resolve("native-image-init.args");
        Path generator =
                repository.resolve("build/native/generate-init-args.sh");

        Process process =
                new ProcessBuilder(
                                "bash",
                                generator.toString(),
                                classes.toString(),
                                output.toString())
                        .directory(repository.toFile())
                        .redirectErrorStream(true)
                        .start();

        String generatorLog =
                new String(
                        process.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);

        assertEquals(0, process.waitFor(), generatorLog);

        List<String> lines =
                Files.readAllLines(output, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty());

        String prefix = "--initialize-at-build-time=";
        assertTrue(lines.get(0).startsWith(prefix));

        Set<String> initialized =
                new HashSet<>(
                        List.of(
                                lines.get(0)
                                        .substring(prefix.length())
                                        .split(",")));

        String runtime = PACKAGE + RUNTIME_ROOT;
        String semantic = PACKAGE + SEMANTIC_ROOT;

        Set<String> required =
                Set.of(
                        runtime,
                        runtime + "$1",
                        runtime + "$Bytecode",
                        runtime + "$AbstractBytecodeNode",
                        runtime + "$UncachedBytecodeNode",
                        runtime + "$UncachedBytecodeNodeTailCall",
                        runtime + "$TagNode",
                        runtime + "$VirtualState",
                        semantic,
                        semantic + "$1",
                        semantic + "$Bytecode",
                        semantic + "$AbstractBytecodeNode",
                        semantic + "$UncachedBytecodeNode",
                        semantic + "$UncachedBytecodeNodeTailCall",
                        semantic + "$TagNode",
                        semantic + "$VirtualState");

        assertTrue(
                initialized.containsAll(required),
                "missing Native generated-structure coverage: "
                        + difference(required, initialized)
                        + "\ngenerator output:\n"
                        + generatorLog);

        assertFalse(
                initialized.contains(
                        runtime + "$UninitializedBytecodeNode"));

        assertFalse(
                initialized.contains(
                        semantic + "$UninitializedBytecodeNode"));
    }

    private static Set<String> sensitiveDirectRoles(
            Path execution,
            String root)
            throws Exception {
        TreeSet<String> roles = new TreeSet<>();
        String prefix = root + "$";

        try (DirectoryStream<Path> stream =
                Files.newDirectoryStream(
                        execution,
                        root + "$*.class")) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                String suffix =
                        name.substring(
                                prefix.length(),
                                name.length() - ".class".length());

                if (suffix.contains("$")) {
                    continue;
                }

                if (suffix.matches(
                                "[A-Za-z0-9_]*BytecodeNode(?:TailCall)?")
                        || suffix.equals("TagNode")
                        || suffix.equals("VirtualState")) {
                    roles.add(suffix);
                }
            }
        }

        return roles;
    }

    private static Set<String> difference(
            Set<String> required,
            Set<String> actual) {
        TreeSet<String> missing = new TreeSet<>(required);
        missing.removeAll(actual);
        return missing;
    }
}

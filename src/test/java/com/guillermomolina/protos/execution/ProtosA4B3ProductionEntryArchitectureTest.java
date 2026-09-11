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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosA4B3ProductionEntryArchitectureTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");

    @Test
    void everyProductionProcessCreatorBindsItsProcessBeforeGuestEntry() throws Exception {
        List<Path> creators = new ArrayList<>();
        try (var paths = Files.walk(MAIN_JAVA)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(
                            path -> {
                                try {
                                    String source = Files.readString(path);
                                    if (source.contains("ProtosStandaloneProcessBootstrap.create(")) {
                                        creators.add(path);
                                        assertTrue(
                                                source.contains("hostProcess("),
                                                () ->
                                                        "production Process creator does not bind a Process Context: "
                                                                + path);
                                        assertFalse(
                                                source.contains("new ProtosSourceCompiler().compile("),
                                                () ->
                                                        "production Process creator retains direct compiler entry: "
                                                                + path);
                                    }
                                } catch (IOException failure) {
                                    throw new RuntimeException(failure);
                                }
                            });
        }
        assertFalse(creators.isEmpty(), "expected production Process creators");
    }

    @Test
    void moduleAndInitialModuleProductionRoutesRemainPublicParseHosted() throws Exception {
        String moduleRuntime =
                Files.readString(
                        MAIN_JAVA.resolve(
                                "com/guillermomolina/protos/execution/ProtosModuleRuntime.java"));
        String initialModule =
                Files.readString(
                        MAIN_JAVA.resolve(
                                "com/guillermomolina/protos/execution/ProtosCanonicalInitialModuleExecution.java"));

        assertTrue(moduleRuntime.contains("process.callInExecutionHostForRuntime("));
        assertTrue(moduleRuntime.contains("materializeModuleSource(source)"));
        assertFalse(moduleRuntime.contains(".parsePublic(source.source())"));
        assertTrue(moduleRuntime.contains("is not a production Process"));
        assertTrue(initialModule.contains("process.callInExecutionHostForRuntime("));
        assertTrue(initialModule.contains("materializeModuleSource(source)"));
        assertFalse(initialModule.contains("parsePublic(source.source())"));
        assertTrue(initialModule.contains("not reachable from a production driver"));
    }
}

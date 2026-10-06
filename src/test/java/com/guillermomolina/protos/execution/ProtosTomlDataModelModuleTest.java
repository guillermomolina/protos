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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProtosTomlDataModelModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void importedModuleExportsExactlyLib010ASemanticConstructors() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        Object imported =
                ProtosTestExecutionSupport.evaluate(
                        "import(\"std:toml/TOML\")",
                        prelude.newModuleActivation());
        ProtosObjectValue module = assertInstanceOf(ProtosObjectValue.class, imported);

        assertEquals(
                Set.of(
                        "string",
                        "integer",
                        "float",
                        "boolean",
                        "offsetDateTime",
                        "localDateTime",
                        "localDate",
                        "localTime",
                        "array",
                        "table",
                        "parse",
                        "encode"),
                module.localSlotsSnapshot().keySet());
    }

    @Test
    void semanticDataTransfersAcrossActorsWhileModuleRemainsActorLocal() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        ProtosActivation actorA = prelude.newModuleActivation();
        ProtosActivation actorB = prelude.newModuleActivation();

        ProtosObjectValue moduleA =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosTestExecutionSupport.evaluate(
                                "import(\"std:toml/TOML\")",
                                actorA));
        ProtosObjectValue moduleB =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosTestExecutionSupport.evaluate(
                                "import(\"std:toml/TOML\")",
                                actorB));

        assertNotSame(moduleA, moduleB);
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosActorValueTransfer.snapshotValue(moduleA, actorA));

        ProtosObjectValue source =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosTestExecutionSupport.evaluate(
                                """
                                TOML: import("std:toml/TOML")
                                TOML.table(
                                    "when",
                                    TOML.offsetDateTime(
                                        2026, 9, 12, 7, 32, 0, 0, 0, 120
                                    ),
                                    "items",
                                    TOML.array(
                                        TOML.string("a"),
                                        TOML.integer(12345678901234567890)
                                    )
                                )
                                """,
                                actorA));

        ProtosObjectValue copied =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosActorValueTransfer.snapshotValue(source, actorA));

        assertNotSame(source, copied);
        assertEquals("table", stringSlot(copied, "kind"));
    }

    private static String stringSlot(ProtosObjectValue value, String slot) {
        return assertInstanceOf(
                        com.guillermomolina.protos.runtime.ProtosStringValue.class,
                        value.readLocalSlot(slot).orElseThrow())
                .value();
    }
}

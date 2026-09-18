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
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolFileOptionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path SHARED_ROOT = Path.of("protos", "tools", "shared");

    @Test
    void absenceSelectsNoFileLocator() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        "Options: import(\"self:Options\")\n"
                                + "Options.filePath(Array(\"test\"))\n");

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertSame(ProtosNullValue.INSTANCE, outcome.value());
    }

    @Test
    void exactSeparateTokenReturnsLiteralFileLocator() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        "Options: import(\"self:Options\")\n"
                                + "Options.filePath("
                                + "Array(\"test\", \"--file\", "
                                + "\"protos/tests/library/uri/parse-components.protos\"))\n");

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertEquals(
                "protos/tests/library/uri/parse-components.protos",
                assertInstanceOf(ProtosStringValue.class, outcome.value()).value());
    }

    @Test
    void selectedFileValueIsLiteralEvenWhenItLooksLikeAnotherOption()
            throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        "Options: import(\"self:Options\")\n"
                                + "Options.filePath("
                                + "Array(\"test\", \"--file\", \"--jobs\"))\n");

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        assertEquals(
                "--jobs",
                assertInstanceOf(ProtosStringValue.class, outcome.value()).value());
    }

    @Test
    void missingAndRepeatedOccurrencesFailClosed() throws Exception {
        String[] expressions = {
            "Array(\"test\", \"--file\")",
            "Array(\"test\", \"--file\", \"a.protos\", "
                    + "\"--file\", \"b.protos\")"
        };

        for (String expression : expressions) {
            ProtosExecutionOutcome outcome =
                    execute(
                            "Options: import(\"self:Options\")\n"
                                    + "Options.filePath("
                                    + expression
                                    + ")\n");

            assertEquals(
                    ProtosExecutionOutcome.State.FAILED,
                    outcome.state(),
                    () -> "expected fail-closed file option: " + expression);
        }
    }

    @Test
    void positionalAndAttachedFormsRemainIgnored() throws Exception {
        String[] expressions = {
            "Array(\"test\", \"case.protos\")",
            "Array(\"test\", \"--file=case.protos\")"
        };

        for (String expression : expressions) {
            ProtosExecutionOutcome outcome =
                    execute(
                            "Options: import(\"self:Options\")\n"
                                    + "Options.filePath("
                                    + expression
                                    + ")\n");

            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
            assertSame(ProtosNullValue.INSTANCE, outcome.value());
        }
    }

    @Test
    void fileJobsAndResourceCatalogRemainOrthogonal() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        "Options: import(\"self:Options\")\n"
                                + "arguments: Array("
                                + "\"test\", "
                                + "\"--jobs\", \"3\", "
                                + "\"--file\", \"case.protos\", "
                                + "\"--resource-catalog\", \"catalog.toml\")\n"
                                + "Array("
                                + "Options.jobs(arguments), "
                                + "Options.resourceCatalogPath(arguments), "
                                + "Options.filePath(arguments))\n");

        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());

        ProtosArrayValue observed =
                assertInstanceOf(ProtosArrayValue.class, outcome.value());

        assertEquals(
                BigInteger.valueOf(3),
                assertInstanceOf(
                                ProtosIntegerValue.class,
                                observed.indexedAt(BigInteger.ZERO))
                        .value());

        assertEquals(
                "catalog.toml",
                assertInstanceOf(
                                ProtosStringValue.class,
                                observed.indexedAt(BigInteger.ONE))
                        .value());

        assertEquals(
                "case.protos",
                assertInstanceOf(
                                ProtosStringValue.class,
                                observed.indexedAt(BigInteger.valueOf(2)))
                        .value());
    }

    private static ProtosExecutionOutcome execute(String source) throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        SHARED_ROOT,
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));

        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        return ProtosTestExecutionSupport.execute(
                source,
                prelude.newModuleActivation());
    }
}

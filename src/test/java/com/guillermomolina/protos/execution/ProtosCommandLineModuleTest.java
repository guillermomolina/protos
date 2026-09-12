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

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Protos-language semantic harness for the LIB011 command-line model. */
final class ProtosCommandLineModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path CASE_ROOT =
            Path.of("protos", "tests", "library", "cli");

    @Test
    void importedModuleExportsCurrentCommandLineSurface() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        Object imported =
                new ProtosSourceCompiler()
                        .compile("import(\"std:cli/CommandLine\")")
                        .call(prelude.newModuleActivation());
        ProtosObjectValue module = assertInstanceOf(ProtosObjectValue.class, imported);

        assertEquals(
                Set.of("option", "positional", "command", "parse"),
                module.localSlotsSnapshot().keySet());
    }

    @Test
    void specificationConstructionConformsInProtos() throws Exception {
        assertFixture("specification-valid.protos");
    }

    @Test
    void invalidSpecificationFamiliesFailClosedInProtos() throws Exception {
        assertFixture("specification-invalid.protos");
    }

    @Test
    void specificationSnapshotsAndFreezeConformInProtos() throws Exception {
        assertFixture("specification-snapshot.protos");
    }

    @Test
    void recursiveLosslessResultShapeConformsInProtos() throws Exception {
        assertFixture("result-model.protos");
    }

    @Test
    void exactResultProvenanceShapeConformsInProtos() throws Exception {
        assertFixture("result-provenance.protos");
    }

    @Test
    void remainingApprovedAdversarialFamiliesFailClosedInProtos() throws Exception {
        assertFixture("adversarial.protos");
    }

    @Test
    void deterministicOptionParsingConformsInProtos() throws Exception {
        assertFixture("parse-options.protos");
    }

    @Test
    void d111PositionalAllocationConformsInProtos() throws Exception {
        assertFixture("parse-positionals-d111.protos");
    }

    @Test
    void delimiterAndLiteralValueProvenanceConformInProtos() throws Exception {
        assertFixture("parse-delimiter.protos");
    }

    @Test
    void parserFailureFamiliesFailClosedInProtos() throws Exception {
        assertFixture("parse-failures.protos");
    }

    @Test
    void d115SubcommandBoundaryConformsInProtos() throws Exception {
        assertFixture("parse-subcommands-d115.protos");
    }

    @Test
    void currentScopeDelimiterDisablesChildRecognitionInProtos() throws Exception {
        assertFixture("parse-subcommand-delimiter.protos");
    }

    @Test
    void optionOwnershipAndValuePrecedenceConformAcrossScopesInProtos() throws Exception {
        assertFixture("parse-subcommand-options.protos");
    }

    @Test
    void deepRecursiveCommandResultsAndFreezeConformInProtos() throws Exception {
        assertFixture("parse-subcommand-deep.protos");
    }

    private static void assertFixture(String fixture) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        Object result =
                new ProtosSourceCompiler()
                        .compile(
                                Files.readString(
                                        CASE_ROOT.resolve(fixture),
                                        StandardCharsets.UTF_8))
                        .call(prelude.newModuleActivation());

        assertSame(ProtosBooleanValue.TRUE, result, fixture);
    }
}

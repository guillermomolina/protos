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
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Real-std closure harness for LIB009-A/B/C/D/E CSV codec, streaming, Text I/O and isolation. */
final class ProtosCsvModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path CASE_ROOT = Path.of("protos", "tests", "library", "csv");

    @Test
    void importedModuleExportsExactlyClosedDefaultProfileSurface() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        Object imported =
                new ProtosSourceCompiler()
                        .compile("import(\"std:csv/CSV\")")
                        .call(prelude.newModuleActivation());
        ProtosObjectValue module = assertInstanceOf(ProtosObjectValue.class, imported);

        assertEquals(
                Set.of("parse", "encode", "rowParser", "readRows", "writeRows"),
                module.localSlotsSnapshot().keySet());
    }

    @Test
    void defaultProfileRowsAndFieldsConform() throws Exception {
        assertFixture("parse-basic.protos");
    }

    @Test
    void recordBoundariesBlankRecordsAndEmbeddedTerminatorsConform() throws Exception {
        assertFixture("parse-record-boundaries.protos");
    }

    @Test
    void malformedCsvFailsClosed() throws Exception {
        assertFixture("parse-invalid.protos");
    }

    @Test
    void arityAndStringDomainFailClosed() throws Exception {
        assertFixture("arity-domain-rejection.protos");
    }

    @Test
    void encodeCanonicalWriterPolicyConforms() throws Exception {
        assertFixture("encode-canonical.protos");
    }

    @Test
    void encodeParseStructuralRoundTripConforms() throws Exception {
        assertFixture("encode-roundtrip.protos");
    }

    @Test
    void encodeRejectsInvalidTableRowAndFieldDomains() throws Exception {
        assertFixture("encode-invalid.protos");
    }

    @Test
    void incrementalRowParserConforms() throws Exception {
        assertFixture("row-parser-basic.protos");
        assertFixture("row-parser-boundaries.protos");
        assertFixture("row-parser-lifecycle.protos");
        assertFixture("row-parser-independence.protos");
    }

    @Test
    void textReaderAndWriterAdaptersConform() throws Exception {
        assertFixture("text-adapter-reader.protos");
        assertFixture("text-adapter-writer.protos");
        assertFixture("text-adapter-overlap.protos");
        assertFixture("text-adapter-terminal-errors.protos");
    }

    @Test
    void integratedClosureAndScalabilityEvidenceConform() throws Exception {
        assertFixture("integrated-closure.protos");
        assertFixture("streaming-scale.protos");
    }

    @Test
    void standardLibraryModuleAndParserInstancesAreActorLocalAndIndependent()
            throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosSourceCompiler compiler = new ProtosSourceCompiler();
        ProtosActivation actorA = prelude.newModuleActivation();
        ProtosActivation actorB = prelude.newModuleActivation();

        ProtosObjectValue moduleA1 =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        compiler.compile("import(\"std:csv/CSV\")").call(actorA));
        ProtosObjectValue moduleA2 =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        compiler.compile("import(\"std:csv/CSV\")").call(actorA));
        ProtosObjectValue moduleB =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        compiler.compile("import(\"std:csv/CSV\")").call(actorB));

        assertSame(moduleA1, moduleA2, "one Actor must reuse its Actor-local CSV module instance");
        assertNotSame(
                moduleA1,
                moduleB,
                "different Actors must not share a mutable Standard Library module instance");

        Object parserA1 =
                compiler.compile(
                                "CSV_A1: import(\"std:csv/CSV\")\n"
                                        + "CSV_A1.rowParser((row) => { null })")
                        .call(actorA);
        Object parserA2 =
                compiler.compile(
                                "CSV_A2: import(\"std:csv/CSV\")\n"
                                        + "CSV_A2.rowParser((row) => { null })")
                        .call(actorA);
        Object parserB =
                compiler.compile(
                                "CSV_B: import(\"std:csv/CSV\")\n"
                                        + "CSV_B.rowParser((row) => { null })")
                        .call(actorB);

        assertInstanceOf(ProtosObjectValue.class, parserA1);
        assertInstanceOf(ProtosObjectValue.class, parserA2);
        assertInstanceOf(ProtosObjectValue.class, parserB);
        assertNotSame(parserA1, parserA2, "rowParser() must return fresh local parser state");
        assertNotSame(parserA1, parserB, "parser state must not be shared across Actors");
        assertNotSame(parserA2, parserB, "parser state must remain independently allocated");
    }

    private static void assertFixture(String fixture) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        ProtosActivation activation = prelude.newModuleActivation();
        Object result =
                new ProtosSourceCompiler()
                        .compile(
                                Files.readString(
                                        CASE_ROOT.resolve(fixture),
                                        StandardCharsets.UTF_8))
                        .call(activation);

        if (result instanceof ProtosFutureValue future) {
            awaitTerminal(future, activation, fixture);
            assertEquals(ProtosFutureValue.State.RESOLVED, future.state(), fixture);
            result = future.resolvedValue().orElseThrow();
        }

        assertSame(ProtosBooleanValue.TRUE, result, fixture);
    }

    private static void awaitTerminal(
            ProtosFutureValue future, ProtosActivation activation, String fixture) {
        int dispatches = 0;
        while (future.state() == ProtosFutureValue.State.PENDING) {
            if (!activation.executionDomain().dispatchOne()) {
                throw new AssertionError(fixture + ": pending Future with no runnable work");
            }
            dispatches++;
            if (dispatches > 100000) {
                throw new AssertionError(fixture + ": Future exceeded bounded terminal progress");
            }
        }
    }
}

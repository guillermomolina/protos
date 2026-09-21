/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
 * See LICENSE.TXT for the complete terms.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolRepositoryCorpusPlansTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");

    @Test
    void ordinaryLibraryPlansHaveExactExplicitMembership() throws Exception {
        assertPlan("protos/corpus/library/uri", 11, "uri/parse-components.protos", "uri/resolve-invalid.protos");
        assertPlan("protos/corpus/library/csv", 17, "csv/parse-basic.protos", "csv/streaming-scale.protos");
        assertPlan("protos/corpus/library/cli", 19, "cli/specification-valid.protos", "cli/closure-isolation-concurrency.protos");
        assertPlan("protos/corpus/library/math/integer", 11, "math/integer/gcd-lcm.protos", "math/integer/integrated-closure.protos");
        assertPlan("protos/corpus/library/crypto/sha256", 10, "crypto/sha256/empty.protos", "crypto/sha256/invalid-non-bytes.protos");
        assertPlan("protos/corpus/library/network/ip-addresses", 8, "network/ip-addresses/surface-ipv4.protos", "network/ip-addresses/ipv6-invalid.protos");
        assertPlan("protos/corpus/library/network/ip-endpoints", 2, "network/ip-endpoints/parse-format.protos", "network/ip-endpoints/invalid.protos");
    }

    @Test
    void csvFutureAndShaCasesRemainOrderedAndAreSuiteNative() throws Exception {
        assertEquals("csv/text-adapter-reader.protos", path("protos/corpus/library/csv", 11));
        assertEquals("csv/text-adapter-writer.protos", path("protos/corpus/library/csv", 12));
        assertEquals("csv/text-adapter-overlap.protos", path("protos/corpus/library/csv", 13));
        assertEquals("csv/integrated-closure.protos", path("protos/corpus/library/csv", 15));
        assertEquals(
                "crypto/sha256/invalid-non-bytes.protos",
                path("protos/corpus/library/crypto/sha256", 9));

        assertEquals("suite-native", expectation("protos/corpus/library/csv", 11));
        assertEquals("suite-native", expectation("protos/corpus/library/csv", 12));
        assertEquals("suite-native", expectation("protos/corpus/library/csv", 13));
        assertEquals("suite-native", expectation("protos/corpus/library/csv", 15));
        assertEquals("suite-native", expectation("protos/corpus/library/crypto/sha256", 9));
    }

    @Test
    void unknownCorpusFailsClosed() throws Exception {
        ProtosExecutionOutcome outcome =
                execute(
                        "Plans: import(\"self:RepositoryCorpusPlans\")\n"
                                + "Plans.load(\"protos/corpus/library/unknown\")");
        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
    }

    private static void assertPlan(
            String corpusId, int count, String firstPath, String lastPath) throws Exception {
        String prefix =
                "Manifest: import(\"self:Manifest\")\n"
                        + "Plans: import(\"self:RepositoryCorpusPlans\")\n"
                        + "cases: Manifest.planCases(Plans.load(\""
                        + corpusId
                        + "\"))\n";
        assertEquals(count, completedInteger(prefix + "cases.size()"));
        assertEquals(firstPath, completedString(prefix + "Manifest.casePath(cases[0])"));
        assertEquals(
                lastPath,
                completedString(
                        prefix + "Manifest.casePath(cases[cases.size() - 1])"));
    }

    private static String expectation(String corpusId, int index) throws Exception {
        return completedString(
                "Manifest: import(\"self:Manifest\")\n"
                        + "Plans: import(\"self:RepositoryCorpusPlans\")\n"
                        + "cases: Manifest.planCases(Plans.load(\""
                        + corpusId
                        + "\"))\n"
                        + "Manifest.caseExpectation(cases["
                        + index
                        + "])");
    }

    private static String path(String corpusId, int index) throws Exception {
        return completedString(
                "Manifest: import(\"self:Manifest\")\n"
                        + "Plans: import(\"self:RepositoryCorpusPlans\")\n"
                        + "cases: Manifest.planCases(Plans.load(\""
                        + corpusId
                        + "\"))\n"
                        + "Manifest.casePath(cases["
                        + index
                        + "])");
    }

    private static int completedInteger(String source) throws Exception {
        ProtosExecutionOutcome outcome = execute(source);
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        return assertInstanceOf(ProtosIntegerValue.class, outcome.value())
                .value()
                .intValueExact();
    }

    private static String completedString(String source) throws Exception {
        ProtosExecutionOutcome outcome = execute(source);
        assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
        return assertInstanceOf(ProtosStringValue.class, outcome.value()).value();
    }

    private static ProtosExecutionOutcome execute(String source) throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        TOOL_ROOT.resolveSibling("shared"),
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        return ProtosTestExecutionSupport.execute(source, prelude.newModuleActivation());
    }
}

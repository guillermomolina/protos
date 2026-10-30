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

import static com.guillermomolina.protos.execution.ProtosTestToolRepositoryCompletenessSupport.AUXILIARY_MODULES;
import static com.guillermomolina.protos.execution.ProtosTestToolRepositoryCompletenessSupport.COLORED_FORMATTER;
import static com.guillermomolina.protos.execution.ProtosTestToolRepositoryCompletenessSupport.SPECIALIZED_TREES;
import static com.guillermomolina.protos.execution.ProtosTestToolRepositoryCompletenessSupport.TESTS_ROOT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.cli.ProtosCli;
import com.guillermomolina.protos.execution.ProtosTestToolRepositoryCompletenessSupport.Exclusion;
import com.guillermomolina.protos.execution.ProtosTestToolRepositoryCompletenessSupport.Inventory;
import com.guillermomolina.protos.execution.ProtosTestToolRepositoryCompletenessSupport.RepositoryEvidence;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * TEST010 repository test-registration integrity guard.
 *
 * <p>The repository tests audit the real physical tree against the registrations of the real
 * {@code RepositorySuite.root} TestPlans; the synthetic tests drive {@code RepositoryCorpusAudit}
 * with small inventories to prove that each defect fails closed. Every comparison is made by the
 * Protos audit, in given and in reversed input order.
 */
final class ProtosTestToolRepositoryCompletenessTest {
    private static final String LOGGING_SUITE = "protos/library/logging";
    private static final String LOGGING_CORPUS = "protos/corpus/library/logging";
    private static final String RESOLUTION_ROOT_SUITE = "protos/package-tool/resolution-root";
    private static final String RESOLUTION_ROOT_CORPUS =
            "protos/corpus/package-tool/resolution-root";

    private static final List<String> REGISTRATION_CODES =
            List.of(
                    "MISSING_REGISTRATION",
                    "DANGLING_REGISTRATION",
                    "DUPLICATE_REGISTRATION",
                    "AUTHORITY_MISMATCH");
    private static final List<String> OWNERSHIP_CODES =
            List.of("UNCLASSIFIED_SOURCE", "INVALID_EXCLUSION");

    /** Classified as source text only; never imported or executed. */
    private static final String SUITE_NATIVE_SOURCE =
            """
            Test: import("std:test/Test")

            tests: [
                Test("first", () => {
                    true
                }),
                Test("second", () => {
                    true
                })
            ]
            tests.freeze()
            """;

    private record AuditOutcome(
            String report, int registrationCount, int registeredSourceCount, int exclusionCount) {}

    @Test
    void repositoryProductionSourcesAreRegisteredExactlyOnce() throws Exception {
        RepositoryEvidence evidence = ProtosTestToolRepositoryCompletenessSupport.repositoryEvidence();

        assertTrue(evidence.registrationCount() > 0);
        assertFalse(evidence.inventory().intended().isEmpty());
        assertEquals("", diagnostics(evidence.report(), REGISTRATION_CODES));
    }

    @Test
    void repositorySourceOwnershipIsComplete() throws Exception {
        RepositoryEvidence evidence = ProtosTestToolRepositoryCompletenessSupport.repositoryEvidence();
        Inventory inventory = evidence.inventory();
        Map<String, String> excluded = new LinkedHashMap<>();
        inventory.exclusions().forEach(exclusion -> excluded.put(exclusion.path(), exclusion.reason()));

        for (String tree : SPECIALIZED_TREES) {
            for (String root : ProtosTestToolRepositoryCompletenessSupport.corpusRoots()) {
                assertFalse(
                        (root + "/").startsWith(tree),
                        () -> "corpus root " + root + " lies inside the specialized tree " + tree);
            }
        }
        for (String module : AUXILIARY_MODULES.keySet()) {
            assertTrue(excluded.containsKey(module), () -> module + " is not auxiliary");
        }
        for (String directory :
                ProtosTestToolRepositoryCompletenessSupport.hostConfiguredJUnitDirectories()) {
            for (String path : inventory.physical()) {
                if (path.startsWith(directory)) {
                    assertTrue(excluded.containsKey(path), () -> path + " left JUnit ownership");
                }
            }
        }

        assertEquals("", diagnostics(evidence.report(), OWNERSHIP_CODES));
        assertEquals(
                inventory.physical().size(),
                evidence.registeredSourceCount() + inventory.exclusions().size());
    }

    @Test
    void missingRegistrationFailsClosed() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("library/logging/levels.protos", SUITE_NATIVE_SOURCE);
        sources.put("library/logging/new-suite.protos", SUITE_NATIVE_SOURCE);
        Inventory inventory = ProtosTestToolRepositoryCompletenessSupport.classify(sources);

        assertEquals(List.copyOf(sources.keySet()), inventory.intended());
        assertEquals(
                "MISSING_REGISTRATION library/logging/new-suite.protos\n",
                audit(inventory, logging("levels.protos")).report());
    }

    @Test
    void staleRegistrationFailsClosed() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("library/logging/levels.protos", SUITE_NATIVE_SOURCE);

        assertEquals(
                "DANGLING_REGISTRATION library/logging/removed.protos"
                        + loggingIdentity("removed.protos")
                        + "\n",
                audit(
                                ProtosTestToolRepositoryCompletenessSupport.classify(sources),
                                logging("levels.protos"),
                                logging("removed.protos"))
                        .report());
    }

    @Test
    void duplicateRegistrationFailsClosed() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("library/logging/levels.protos", SUITE_NATIVE_SOURCE);

        assertEquals(
                "DUPLICATE_REGISTRATION library/logging/levels.protos"
                        + loggingIdentity("levels.protos")
                        + "\n",
                audit(
                                ProtosTestToolRepositoryCompletenessSupport.classify(sources),
                                logging("levels.protos"),
                                logging("levels.protos"))
                        .report());
    }

    @Test
    void excludedFixtureIsNotProduction() throws Exception {
        String fixture = "tooling/logical-cases/test010-suite-native-fixture.protos";
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("library/logging/levels.protos", SUITE_NATIVE_SOURCE);
        sources.put(fixture, SUITE_NATIVE_SOURCE);
        Inventory inventory = ProtosTestToolRepositoryCompletenessSupport.classify(sources);

        assertEquals(List.of("library/logging/levels.protos"), inventory.intended());
        assertEquals(
                List.of(fixture), inventory.exclusions().stream().map(Exclusion::path).toList());

        AuditOutcome outcome = audit(inventory, logging("levels.protos"));
        assertEquals("", outcome.report());
        assertEquals(1, outcome.registeredSourceCount());
        assertEquals(1, outcome.exclusionCount());

        // A production source cannot also be excluded.
        Inventory contradictory =
                new Inventory(
                        inventory.physical(),
                        inventory.intended(),
                        List.of(
                                inventory.exclusions().get(0),
                                new Exclusion("library/logging/levels.protos", "JUnit-owned")));
        assertEquals(
                "INVALID_EXCLUSION library/logging/levels.protos\n",
                audit(contradictory, logging("levels.protos")).report());
    }

    @Test
    void distinctProjectAuthoritiesMayReuseSource() throws Exception {
        String source = "package-tool/resolution-root/fixtures/workspace.protos";
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(source, SUITE_NATIVE_SOURCE);
        Inventory inventory = ProtosTestToolRepositoryCompletenessSupport.classify(sources);

        AuditOutcome outcome =
                audit(
                        inventory,
                        resolutionRoot(source, "workspace", "workspace"),
                        resolutionRoot(source, "nested-workspace", "nested-workspace"));
        assertEquals("", outcome.report());
        assertEquals(2, outcome.registrationCount());
        assertEquals(1, outcome.registeredSourceCount());

        // Without distinct D133 project authorities the same source is claimed twice.
        assertEquals(
                "AUTHORITY_MISMATCH "
                        + source
                        + resolutionRootIdentity("nested-workspace")
                        + "\nAUTHORITY_MISMATCH "
                        + source
                        + resolutionRootIdentity("workspace")
                        + "\n",
                audit(
                                inventory,
                                resolutionRoot(source, "workspace", null),
                                resolutionRoot(source, "nested-workspace", null))
                        .report());
    }

    @Test
    void compositeRegistrationKeysKeepSeparatorsAndBackslashesDistinct() throws Exception {
        String first = "library/logging/with|pipe.protos";
        String second = "library/logging/with\\|pipe.protos";
        String third = "library/logging/third.protos";
        String fourth = "library/logging/fourth.protos";

        Map<String, String> sources = new LinkedHashMap<>();
        for (String path : List.of(first, second, third, fourth)) {
            sources.put(path, SUITE_NATIVE_SOURCE);
        }

        Inventory inventory = ProtosTestToolRepositoryCompletenessSupport.classify(sources);

        AuditOutcome outcome =
                audit(
                        inventory,
                        registration(first, "suite|part", "corpus", "case", null),
                        registration(second, "suite", "part|corpus", "case", null),
                        registration(third, "suite", "corpus", "part|case", null),
                        registration(fourth, "suite", "corpus", "part\\|case", null));

        assertEquals("", outcome.report());
        assertEquals(4, outcome.registrationCount());
        assertEquals(4, outcome.registeredSourceCount());
    }

    @Test
    void suiteNativeSourceInsideJUnitOwnedTreeStillNeedsRegistration() throws Exception {
        String path = "conformance/library/io/test010-new-suite-native.protos";

        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(path, SUITE_NATIVE_SOURCE);

        Inventory inventory = ProtosTestToolRepositoryCompletenessSupport.classify(sources);

        assertEquals(List.of(path), inventory.intended());
        assertTrue(inventory.exclusions().isEmpty());
        assertEquals("MISSING_REGISTRATION " + path + "\n", audit(inventory).report());
    }

    @Test
    void sourceAndCaseIdentitiesRemainSeparate() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(COLORED_FORMATTER, SUITE_NATIVE_SOURCE);
        AuditOutcome outcome =
                audit(
                        ProtosTestToolRepositoryCompletenessSupport.classify(sources),
                        logging("colored-formatter.protos"));
        assertEquals("", outcome.report());
        assertEquals(1, outcome.registrationCount());
        assertEquals(1, outcome.registeredSourceCount());

        // The real plans register the source once, under its source CaseSpec identity.
        assertEquals(
                List.of(List.of(LOGGING_SUITE, LOGGING_CORPUS, "logging/colored-formatter.protos")),
                ProtosTestToolRepositoryCompletenessSupport.repositoryEvidence()
                        .coloredFormatterIdentities());

        // Discovery answers several Logical Cases for that one registered source.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code =
                new ProtosCli()
                        .run(
                                new String[] {
                                    "test",
                                    "--list-cases",
                                    "--file",
                                    TESTS_ROOT.resolve(COLORED_FORMATTER).toString()
                                },
                                InputStream.nullInputStream(),
                                new PrintStream(out),
                                new PrintStream(err));
        assertEquals(0, code, () -> err.toString(StandardCharsets.UTF_8));
        String listing = out.toString(StandardCharsets.UTF_8);
        assertTrue(listing.split(Pattern.quote("{\"ref\":"), -1).length - 1 > 1, listing);
    }

    private static String diagnostics(String report, List<String> codes) {
        return report.lines()
                .filter(line -> codes.stream().anyMatch(code -> line.startsWith(code + " ")))
                .map(line -> line + "\n")
                .collect(Collectors.joining());
    }

    /**
     * Audits {@code inventory} against registration expressions, and again with every input
     * reversed; both reports must be identical.
     */
    private static AuditOutcome audit(Inventory inventory, String... registrations) {
        ProtosArrayValue observed =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        ProtosTestToolRepositoryCompletenessSupport.completed(
                                """
                                Audit: import("self:RepositoryCorpusAudit")
                                Arrays: import("std:collections/Array")
                                %sregistrations: %s
                                result: Audit.audit(
                                    physicalSources,
                                    intendedSources,
                                    exclusions,
                                    registrations
                                )
                                reordered: Audit.audit(
                                    Arrays.reverse(physicalSources),
                                    Arrays.reverse(intendedSources),
                                    Arrays.reverse(exclusions),
                                    Arrays.reverse(registrations)
                                )
                                Array(
                                    Audit.report(result),
                                    Audit.report(reordered),
                                    result.registrationCount,
                                    result.registeredSourceCount,
                                    result.exclusionCount
                                )
                                """
                                        .formatted(
                                                ProtosTestToolRepositoryCompletenessSupport
                                                        .inventoryBindings(inventory),
                                                ProtosTestToolRepositoryCompletenessSupport.array(
                                                        List.of(registrations))),
                                ProtosTestToolRepositoryCompletenessSupport.newActivation()));
        String report = ProtosTestToolRepositoryCompletenessSupport.stringAt(observed, 0);
        assertEquals(report, ProtosTestToolRepositoryCompletenessSupport.stringAt(observed, 1));
        return new AuditOutcome(
                report,
                ProtosTestToolRepositoryCompletenessSupport.integerAt(observed, 2),
                ProtosTestToolRepositoryCompletenessSupport.integerAt(observed, 3),
                ProtosTestToolRepositoryCompletenessSupport.integerAt(observed, 4));
    }

    private static String logging(String name) {
        return registration(
                "library/logging/" + name, LOGGING_SUITE, LOGGING_CORPUS, "logging/" + name, null);
    }

    private static String loggingIdentity(String name) {
        return " suite=" + LOGGING_SUITE + " corpus=" + LOGGING_CORPUS + " case=logging/" + name;
    }

    private static String resolutionRoot(String source, String project, String authority) {
        return registration(
                source,
                RESOLUTION_ROOT_SUITE,
                RESOLUTION_ROOT_CORPUS,
                RESOLUTION_ROOT_SUITE + "/" + project + "/workspace.protos",
                authority);
    }

    private static String resolutionRootIdentity(String project) {
        return " suite="
                + RESOLUTION_ROOT_SUITE
                + " corpus="
                + RESOLUTION_ROOT_CORPUS
                + " case="
                + RESOLUTION_ROOT_SUITE
                + "/"
                + project
                + "/workspace.protos";
    }

    private static String registration(
            String path, String suite, String corpus, String caseId, String authority) {
        return "Audit.registration("
                + String.join(
                        ", ",
                        ProtosTestToolRepositoryCompletenessSupport.text(path),
                        ProtosTestToolRepositoryCompletenessSupport.text(suite),
                        ProtosTestToolRepositoryCompletenessSupport.text(corpus),
                        ProtosTestToolRepositoryCompletenessSupport.text(caseId),
                        authority == null
                                ? "null"
                                : ProtosTestToolRepositoryCompletenessSupport.text(authority))
                + ")";
    }
}

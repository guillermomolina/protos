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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TEST010 repository side of {@link ProtosTestToolRepositoryCompletenessTest}.
 *
 * <p>It enumerates the {@code .protos} sources under {@code protos/tests/}, supplies the read-only
 * corpus authorities, classifies each source by the existing ownership boundaries, and runs the
 * real {@code RepositorySuite.root} TestPlans to collect registrations. Every comparison is left
 * to {@code RepositoryCorpusAudit}; no module under {@code protos/tests/} is imported and no Test
 * body runs.
 */
final class ProtosTestToolRepositoryCompletenessSupport {
    static final Path TESTS_ROOT = Path.of("protos", "tests");
    static final String COLORED_FORMATTER = "library/logging/colored-formatter.protos";

    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path TEST_SOURCES =
            Path.of("src", "test", "java", "com", "guillermomolina", "protos");
    private static final Path PROCESS_CONFORMANCE_OWNER =
            TEST_SOURCES.resolve(
                    "execution/integration/process/ProtosProcessIntegratedConformanceTest.java");

    /**
     * Read authority for one D126 CorpusBinding as {@code ProtosCli} installs it through
     * {@code ProtosTestCorpusRegistry}: its plan loader, its source root relative to
     * {@code protos/tests/}, and its D132/D133 case namespace ({@code null} when it has none).
     * It names no test and no source. The audit program fails when a suite leaf has no binding
     * here or a binding serves no leaf.
     */
    private record CorpusBinding(String corpus, String loader, String root, String namespace) {}

    private static final List<CorpusBinding> CORPUS_BINDINGS =
            List.of(
                    new CorpusBinding("protos/corpus/conformance", "manifest", "conformance", null),
                    new CorpusBinding(
                            "protos/corpus/process-snapshot", "manifest", "conformance/process", null),
                    new CorpusBinding("protos/corpus/actor", "manifest", "conformance/actor", null),
                    new CorpusBinding("protos/corpus/group", "manifest", "conformance/group", null),
                    new CorpusBinding(
                            "protos/corpus/package-toml",
                            "package-toml",
                            "package-tool/toml-syntax",
                            null),
                    library("uri"),
                    library("csv"),
                    library("cli"),
                    library("math/integer"),
                    library("math/linear-algebra"),
                    library("crypto/sha256"),
                    library("network/ip-addresses"),
                    library("network/ip-endpoints"),
                    library("network/resolvers"),
                    library("semver"),
                    library("datetime"),
                    library("regex"),
                    library("logging"),
                    library("http"),
                    library("tls"),
                    packageTool("version", "case-outcomes"),
                    packageTool("lock", "case-outcomes"),
                    packageTool("resolution-input", "case-outcomes"),
                    packageTool("content-identity", "project-tree"),
                    packageTool("resolution-input-lock", "project-tree"),
                    packageTool("resolution-root", "project-tree"),
                    packageTool("execution-plan", "project-tree"),
                    packageTool("project-projection", "project-tree"));

    /** Trees whose sources declaring the suite-native surface are production-owned. */
    private static final List<String> PRODUCTION_TREES =
            List.of("library/", "conformance/", "package-tool/");

    /**
     * Trees outside every corpus root, whose execution belongs to specialized JUnit tests or to
     * the foreign-provider mechanism. No TestPlan can register a source here.
     */
    static final List<String> SPECIALIZED_TREES =
            List.of("tooling/", "parser/", "filesystem/", "cli/", "foreign-provider/");

    /** Auxiliary modules imported by suite-native sources; they declare no Tests of their own. */
    static final Map<String, String> AUXILIARY_MODULES =
            Map.of(
                    "conformance/actor/modules/workers.protos",
                    "auxiliary module bound as tool002-actor:workers",
                    "conformance/group/modules/workers.protos",
                    "auxiliary module bound as tool002-group:workers",
                    "conformance/library/toml/official/suite/Harness.protos",
                    "auxiliary harness of the toml-official suite sources");

    /**
     * A JUnit-owned boundary: a source under {@code directory} that declares no suite-native
     * Tests is JUnit-owned when one of {@code owners} (a test source, or a directory of them)
     * names it.
     */
    private record JUnitBoundary(String directory, List<Path> owners) {}

    private static final List<JUnitBoundary> JUNIT_BOUNDARIES =
            List.of(
                    new JUnitBoundary(
                            "conformance/library/io/",
                            List.of(
                                    TEST_SOURCES.resolve(
                                            "conformance/ProtosFilesystemLibraryConformanceTest.java"),
                                    PROCESS_CONFORMANCE_OWNER)),
                    new JUnitBoundary(
                            "conformance/maturity/process/", List.of(PROCESS_CONFORMANCE_OWNER)),
                    new JUnitBoundary(
                            "conformance/maturity/resource/",
                            List.of(
                                    TEST_SOURCES.resolve(
                                            "conformance/"
                                                    + "ProtosResourceLifetimeMaturityConformanceTest.java"))),
                    new JUnitBoundary(
                            "conformance/maturity/system/",
                            List.of(
                                    TEST_SOURCES.resolve(
                                            "conformance/"
                                                    + "ProtosSystemResourceEndToEndConformanceTest.java"))),
                    new JUnitBoundary(
                            "package-tool/", List.of(TEST_SOURCES.resolve("execution/packages"))));

    private static final Pattern TEST_IMPORT =
            Pattern.compile(
                    "(?m)^[A-Za-z_][A-Za-z0-9_]*[ \\t]*:[ \\t]*import\\(\"std:test/Test\"\\)");
    private static final Pattern TESTS_BINDING = Pattern.compile("(?m)^tests[ \\t]*:");

    private static final Map<Path, String> OWNER_TEXT = new ConcurrentHashMap<>();
    private static RepositoryEvidence cachedRepositoryEvidence;

    record Exclusion(String path, String reason) {}

    record Inventory(List<String> physical, List<String> intended, List<Exclusion> exclusions) {}

    record RepositoryEvidence(
            Inventory inventory,
            String report,
            int registrationCount,
            int registeredSourceCount,
            List<List<String>> coloredFormatterIdentities) {}

    private ProtosTestToolRepositoryCompletenessSupport() {}

    private static CorpusBinding library(String name) {
        return new CorpusBinding(
                "protos/corpus/library/" + name, "repository-explicit", "library", null);
    }

    private static CorpusBinding packageTool(String name, String loader) {
        return new CorpusBinding(
                "protos/corpus/package-tool/" + name,
                loader,
                "package-tool/" + name,
                "protos/package-tool/" + name);
    }

    /** The source roots, relative to {@code protos/tests/}, of every corpus binding. */
    static Set<String> corpusRoots() {
        return CORPUS_BINDINGS.stream()
                .map(CorpusBinding::root)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** The JUnit-owned directories whose sources need host-specific configuration. */
    static List<String> hostConfiguredJUnitDirectories() {
        return JUNIT_BOUNDARIES.stream()
                .map(JUnitBoundary::directory)
                .filter(directory -> directory.startsWith("conformance/"))
                .toList();
    }

    // ---- classification by the existing ownership boundaries ----

    /**
     * Classifies sources keyed by their path relative to {@code protos/tests/}. A source is
     * intended production when it declares the suite-native surface (an {@code std:test/Test}
     * import and a top-level {@code tests} Array) inside a production tree and outside D133
     * project data. Exclusions follow ownership boundaries only and are never derived from
     * registrations.
     */
    static Inventory classify(Map<String, String> sources) throws IOException {
        List<String> intended = new ArrayList<>();
        List<Exclusion> exclusions = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            String path = source.getKey();
            boolean declares = declaresSuiteNativeTests(source.getValue());
            if (declares
                    && PRODUCTION_TREES.stream().anyMatch(path::startsWith)
                    && projectDataCorpus(path) == null) {
                intended.add(path);
            }
            String reason = exclusionReason(path, declares);
            if (reason != null) {
                exclusions.add(new Exclusion(path, reason));
            }
        }
        return new Inventory(List.copyOf(sources.keySet()), intended, exclusions);
    }

    private static Map<String, String> physicalSources() throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(TESTS_ROOT)) {
            for (Path file :
                    paths.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".protos"))
                            .toList()) {
                Path relative = TESTS_ROOT.relativize(file);
                sources.put(
                        relative.toString().replace(relative.getFileSystem().getSeparator(), "/"),
                        new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            }
        }
        return sources;
    }

    private static boolean declaresSuiteNativeTests(String content) {
        return TEST_IMPORT.matcher(content).find() && TESTS_BINDING.matcher(content).find();
    }

    private static String projectDataCorpus(String path) {
        for (CorpusBinding binding : CORPUS_BINDINGS) {
            if (binding.loader().equals("project-tree")
                    && path.startsWith(binding.root() + "/cases/")) {
                return binding.corpus();
            }
        }
        return null;
    }

    private static String exclusionReason(String path, boolean declares) throws IOException {
        String projectCorpus = projectDataCorpus(path);
        if (projectCorpus != null) {
            return "D133 project-tree data of " + projectCorpus;
        }
        for (String tree : SPECIALIZED_TREES) {
            if (path.startsWith(tree)) {
                return "outside every corpus root; owned by specialized JUnit tests or the"
                        + " foreign-provider mechanism";
            }
        }
        if (declares) {
            return null;
        }
        if (AUXILIARY_MODULES.containsKey(path)) {
            return AUXILIARY_MODULES.get(path);
        }
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        for (JUnitBoundary boundary : JUNIT_BOUNDARIES) {
            if (path.startsWith(boundary.directory())) {
                String owner = namingOwner(boundary, fileName);
                if (owner != null) {
                    return "JUnit-owned by " + owner;
                }
            }
        }
        return null;
    }

    private static String namingOwner(JUnitBoundary boundary, String fileName)
            throws IOException {
        for (Path owner : ownerSources(boundary)) {
            String text =
                    OWNER_TEXT.computeIfAbsent(
                            owner, ProtosTestToolRepositoryCompletenessSupport::read);
            if (text.contains("\"" + fileName + "\"") || text.contains("/" + fileName + "\"")) {
                String name = owner.getFileName().toString();
                return name.substring(0, name.length() - ".java".length());
            }
        }
        return null;
    }

    private static List<Path> ownerSources(JUnitBoundary boundary) throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path owner : boundary.owners()) {
            if (Files.isDirectory(owner)) {
                try (Stream<Path> listing = Files.list(owner)) {
                    listing.filter(path -> path.getFileName().toString().endsWith(".java"))
                            .sorted()
                            .forEach(sources::add);
                }
            } else if (Files.isRegularFile(owner)) {
                sources.add(owner);
            }
        }
        return sources;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // ---- registrations from the authorized TestPlans ----

    static synchronized RepositoryEvidence repositoryEvidence() throws Exception {
        if (cachedRepositoryEvidence == null) {
            cachedRepositoryEvidence = loadRepositoryEvidence();
        }
        return cachedRepositoryEvidence;
    }

    private static RepositoryEvidence loadRepositoryEvidence() throws Exception {
        Inventory inventory = classify(physicalSources());
        List<String> roots = List.copyOf(corpusRoots());
        ProtosPrelude prelude = SharedPrelude.INSTANCE;
        ProtosActivation activation = prelude.newModuleActivation();
        List<ProtosNioReadOnlyTreeFilesystemBackend> backends = new ArrayList<>();
        try {
            for (int index = 0; index < roots.size(); index++) {
                ProtosNioReadOnlyTreeFilesystemBackend backend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(
                                TESTS_ROOT.resolve(roots.get(index)));
                backends.add(backend);
                assumeTrue(
                        backend.secureConfinementAvailable(),
                        "host provider has no SecureDirectoryStream");
                installFilesystem(prelude, activation, "corpusFilesystem" + index, backend);
            }

            ProtosArrayValue observed =
                    assertInstanceOf(
                            ProtosArrayValue.class,
                            completed(repositoryAuditProgram(inventory, roots), activation));
            ProtosArrayValue colored =
                    assertInstanceOf(ProtosArrayValue.class, observed.indexedAt(3));
            List<List<String>> coloredIdentities = new ArrayList<>();
            for (int index = 0; index < colored.indexedSize(); index++) {
                ProtosArrayValue identity =
                        assertInstanceOf(ProtosArrayValue.class, colored.indexedAt(index));
                coloredIdentities.add(
                        List.of(
                                stringAt(identity, 0),
                                stringAt(identity, 1),
                                stringAt(identity, 2)));
            }
            return new RepositoryEvidence(
                    inventory,
                    stringAt(observed, 0),
                    integerAt(observed, 1),
                    integerAt(observed, 2),
                    coloredIdentities);
        } finally {
            for (ProtosNioReadOnlyTreeFilesystemBackend backend : backends) {
                backend.close();
            }
        }
    }

    /**
     * The registrations are exactly the CaseSpecs the authorized TestPlans materialize for every
     * {@code RepositorySuite.root} leaf, each mapped to its physical path under the binding root
     * and keeping its SuiteId, CorpusId, CaseSpec identity and D133 project authority.
     */
    private static String repositoryAuditProgram(Inventory inventory, List<String> roots) {
        String bindings =
                CORPUS_BINDINGS.stream()
                        .map(
                                binding ->
                                        "    Array("
                                                + text(binding.corpus())
                                                + ", "
                                                + text(binding.loader())
                                                + ", "
                                                + text(binding.root())
                                                + ", "
                                                + (binding.namespace() == null
                                                        ? "null"
                                                        : text(binding.namespace()))
                                                + ", corpusFilesystem"
                                                + roots.indexOf(binding.root())
                                                + ")")
                        .collect(Collectors.joining(",\n"));

        return """
                SuiteGraph: import("self:SuiteGraph")
                RepositorySuite: import("self:RepositorySuite")
                Manifest: import("self:Manifest")
                Plans: import("self:RepositoryCorpusPlans")
                Audit: import("self:RepositoryCorpusAudit")
                ArrayAccumulator: import("self:ArrayAccumulator")
                Arrays: import("std:collections/Array")

                bindings: Array(
                %s
                )
                accumulator: ArrayAccumulator.create()
                usedCorpora: Map()
                SuiteGraph.flattenLeaves(RepositorySuite.root).each((leaf) => {
                    matches: Arrays.filter(bindings, (candidate) => candidate[0] == leaf.corpus)
                    (matches.size() == 1).ifFalse(() => {
                        fail()
                    })
                    (corpus, loader, root, namespace, filesystem): matches[0]
                    usedCorpora[corpus] = true
                    plan: null
                    if(loader == "manifest") {
                        plan = Manifest.load(filesystem)
                    }
                    if(loader == "package-toml") {
                        plan = Manifest.loadPackageToml(filesystem)
                    }
                    if(loader == "repository-explicit") {
                        plan = Plans.load(corpus)
                    }
                    if(loader == "case-outcomes") {
                        plan = Manifest.loadCaseOutcomes(filesystem, namespace)
                    }
                    if(loader == "project-tree") {
                        plan = Manifest.loadProjectTreeCases(filesystem, namespace)
                    }
                    if(plan === null) {
                        fail()
                    }
                    Manifest.planCases(plan).each((spec) => {
                        descriptor: Manifest.caseAuthorityDescriptor(spec)
                        authority: null
                        (descriptor === null).ifFalse(() => {
                            authority = Manifest.caseAuthorityFixtureIdentity(descriptor)
                        })
                        ArrayAccumulator.append(
                            accumulator,
                            Audit.registration(
                                root + "/" + Manifest.casePath(spec),
                                leaf.id,
                                corpus,
                                Manifest.caseId(spec),
                                authority
                            )
                        )
                    })
                })
                (usedCorpora.size() == bindings.size()).ifFalse(() => {
                    fail()
                })
                registrations: ArrayAccumulator.finish(accumulator)
                %s\
                result: Audit.audit(physicalSources, intendedSources, exclusions, registrations)
                colored: Arrays.filter(registrations, (record) => record.path == %s)
                Array(
                    Audit.report(result),
                    result.registrationCount,
                    result.registeredSourceCount,
                    Arrays.map(colored, (record) => Array(
                        record.identity.suite,
                        record.identity.corpus,
                        record.identity.caseId
                    ))
                )
                """
                .formatted(bindings, inventoryBindings(inventory), text(COLORED_FORMATTER));
    }

    /** Binds {@code physicalSources}, {@code intendedSources} and {@code exclusions}. */
    static String inventoryBindings(Inventory inventory) {
        return "physicalSources: "
                + array(inventory.physical().stream().map(
                        ProtosTestToolRepositoryCompletenessSupport::text).toList())
                + "\nintendedSources: "
                + array(inventory.intended().stream().map(
                        ProtosTestToolRepositoryCompletenessSupport::text).toList())
                + "\nexclusions: "
                + array(
                        inventory.exclusions().stream()
                                .map(
                                        exclusion ->
                                                array(
                                                        List.of(
                                                                text(exclusion.path()),
                                                                text(exclusion.reason()))))
                                .toList())
                + "\n";
    }

    static String array(List<String> elements) {
        return "Array(" + String.join(", ", elements) + ")";
    }

    /** A Protos String literal denoting {@code value}. */
    static String text(String value) {
        StringBuilder literal = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> literal.append("\\\\");
                case '"' -> literal.append("\\\"");
                case '\n' -> literal.append("\\n");
                case '\r' -> literal.append("\\r");
                case '\t' -> literal.append("\\t");
                default -> literal.append(character);
            }
        }
        return literal.append('"').toString();
    }

    // ---- execution support ----

    private static final class SharedPrelude {
        private static final ProtosPrelude INSTANCE = create();

        private static ProtosPrelude create() {
            try {
                ProtosBundledToolModuleResolver resolver =
                        new ProtosBundledToolModuleResolver(
                                "test",
                                TOOL_ROOT,
                                TOOL_ROOT.resolveSibling("shared"),
                                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
                return new ProtosCoreBootstrap().bootstrap(CORE, resolver);
            } catch (Exception exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }

    static ProtosActivation newActivation() {
        return SharedPrelude.INSTANCE.newModuleActivation();
    }

    private static void installFilesystem(
            ProtosPrelude prelude,
            ProtosActivation activation,
            String slotName,
            ProtosStandardFilesystemProtocol.Backend backend) {
        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        prelude.bytesPrototypeForRuntime(), activation, backend);
        activation
                .context()
                .createLocalSlot(
                        slotName, assertInstanceOf(ProtosFilesystemValue.class, rawFilesystem));
    }

    static Object completed(String source, ProtosActivation activation) {
        ProtosExecutionOutcome outcome = ProtosTestExecutionSupport.execute(source, activation);
        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "Protos execution did not complete; error=" + outcome.error());
        return outcome.value();
    }

    static String stringAt(ProtosArrayValue array, int index) {
        return assertInstanceOf(ProtosStringValue.class, array.indexedAt(index)).value();
    }

    static int integerAt(ProtosArrayValue array, int index) {
        return Integer.parseInt(
                ProtosNumericValueSupport.integerDecimalText(array.indexedAt(index)));
    }
}

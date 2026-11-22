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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosNativeGeneratedStructurePolicyTest {
    private static final String PACKAGE =
            "com.guillermomolina.protos.execution.";

    /* I111-B: the former semantic-only generated interpreter name, which must no longer exist. */
    private static final String FORMER_RUNTIME_ROOT =
            "ProtosSemanticBytecodeRootNodeGen";

    private static final String SEMANTIC_ROOT =
            "ProtosBytecodeRootNodeGen";

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
         * I111-B: one generated interpreter serves semantic and auxiliary
         * structured/C-prime roots, with the tier configuration (uncached
         * interpreter, tail-call handlers, materialized locals) both used.
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

        assertFalse(
                Files.exists(execution.resolve(FORMER_RUNTIME_ROOT + ".class")),
                "the unified interpreter must not also be generated under its former name");

        assertEquals(
                interpreterRoles,
                sensitiveDirectRoles(execution, SEMANTIC_ROOT));

        Path output = temporary.resolve("native-image-init.args");
        Path generator =
                repository.resolve("scripts/native-generate-init-args.sh");

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

        List<String> initialized =
                List.of(
                        lines.get(0)
                                .substring(prefix.length())
                                .split(","));

        // Package-prefix initialization of exactly the two implementation packages.
        assertEquals(
                List.of(
                        "com.guillermomolina.protos.execution",
                        "com.guillermomolina.protos.runtime"),
                initialized,
                "generator output:\n" + generatorLog);

        // No enumerated generated classes, %%D methods or per-class entries.
        assertFalse(
                lines.get(0).contains("$"),
                "the policy must not enumerate generated classes");
        assertFalse(lines.get(0).contains("%%"));
        assertFalse(
                initialized.stream()
                        .anyMatch(name -> name.startsWith(PACKAGE + FORMER_RUNTIME_ROOT)),
                "native initialization must not name the former interpreter");
        for (String excluded : List.of(
                "com.guillermomolina.protos.cli",
                "com.guillermomolina.protos.lsp",
                "org.eclipse.lsp4j")) {
            assertFalse(
                    initialized.stream().anyMatch(name -> name.startsWith(excluded)),
                    excluded + " must not be initialized at build time");
        }

        assertTrue(lines.contains("-H:MaxRuntimeCompileMethods=20000"));

        /*
         * Implementation metadata is initialized at build time by package;
         * the run-time exceptions are exactly the classes whose static
         * initializers link native symbols through Java FFM (I166 POSIX process
         * handler, I156 confined Filesystem bridge) and the class whose static
         * initializer computes Unicode case tables through ICU4J.
         */
        assertEquals(
                "--initialize-at-run-time="
                        + "com.guillermomolina.protos.execution.ProtosPosixProcessHandler,"
                        + "com.guillermomolina.protos.execution.ProtosLinuxConfinedFilesystemNative$Bridge,"
                        + "com.guillermomolina.protos.execution.ProtosRegexUnicodeFacility$CaseOrbits",
                lines.get(1));
        assertEquals(1L,
                lines.stream().filter(line -> line.startsWith("--initialize-at-run-time=")).count(),
                "no further run-time initialization lists");

        // No ICU4J package or class is initialized at image build time.
        assertFalse(
                lines.stream().anyMatch(line -> line.contains("com.ibm.icu")
                        && !line.startsWith("--initialize-at-run-time=")),
                "ICU4J must not be initialized at build time");
        assertFalse(lines.get(1).contains("com.ibm.icu"));

        // No fallback runtime selection in the generated arguments.
        assertFalse(lines.stream().anyMatch(line -> line.contains("Fallback")));

        // LSP preservation and native access are unchanged.
        assertEquals(
                List.of(
                        "-H:+UnlockExperimentalVMOptions",
                        "-H:Preserve=package=org.eclipse.lsp4j.*",
                        "-H:-UnlockExperimentalVMOptions",
                        "--enable-native-access=org.graalvm.truffle"),
                lines.subList(3, lines.size()));
    }

    @Test
    void optimizedNativeProfilesKeepTheTruffleRuntime() throws Exception {
        Path repository =
                Path.of(System.getProperty("user.dir"))
                        .toAbsolutePath()
                        .normalize();
        String pom = Files.readString(repository.resolve("pom.xml"), StandardCharsets.UTF_8);

        assertFalse(pom.contains("UseFallbackRuntime"),
                "native profiles must not select the fallback Truffle runtime");
        assertFalse(pom.contains("EnableFallbackCompilation"));
        assertFalse(pom.contains("WarnInterpreterOnly"));
        assertTrue(pom.contains("<buildArg>-march=compatibility</buildArg>"));
        assertTrue(pom.contains(
                "<buildArg>@${project.build.directory}/native-image-init.args</buildArg>"));
        assertTrue(pom.contains("<buildArg>--enable-native-access=ALL-UNNAMED</buildArg>"));
        assertTrue(pom.contains("<artifactId>truffle-runtime</artifactId>"));
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

    private static final String MAIN = "src/main/java/com/guillermomolina/protos/";

    private static String mainSource(String relative) throws Exception {
        return Files.readString(Path.of(MAIN + relative), StandardCharsets.UTF_8);
    }

    private static boolean boundaryPrecedes(String source, String declaration) {
        int at = source.indexOf(declaration);
        assertTrue(at > 0, declaration);
        return source.substring(0, at).stripTrailing().endsWith("@TruffleBoundary");
    }

    private static final String[] STRUCTURED_PROTOCOLS = {
        "Boolean", "CaseOf", "ObjectCall", "ImportCall", "Ensure", "LocalEach",
        "ChildOutcomeSequence", "ForeignEach", "IdentityMapAtIfAbsent", "MapMatch",
        "MapReadLookup", "MapAtPut", "MapRemove", "ErrorHandler"
    };

    @Test
    void structuredDispatchUsesConcreteBytecodeOperations() throws Exception {
        String root = mainSource("execution/bytecode/activation/ProtosBytecodeRootNode.java");
        String lowerer = mainSource(
                "execution/bytecode/lowering/ProtosStructuredDispatchLowerer.java");

        assertFalse(root.contains("public static final class IsStructuredCall {"));
        assertFalse(root.contains("public static final class PrepareStructuredCall {"));
        assertFalse(root.contains("ProtosStructuredCallKind"));
        assertFalse(lowerer.contains("ProtosStructuredCallKind"));
        assertFalse(lowerer.contains("beginIsStructuredCall("));
        assertFalse(lowerer.contains("beginPrepareStructuredCall("));
        assertFalse(Files.exists(Path.of(MAIN
                + "execution/bytecode/activation/structured/ProtosStructuredCallKind.java")));

        for (String protocol : STRUCTURED_PROTOCOLS) {
            assertTrue(root.contains("public static final class IsStructured" + protocol + " {"),
                    protocol);
            assertTrue(root.contains("public static final class PrepareStructured" + protocol + " {"),
                    protocol);
            assertTrue(lowerer.contains("builder.beginIsStructured" + protocol + "();"), protocol);
            assertTrue(lowerer.contains("builder.endIsStructured" + protocol + "();"), protocol);
            assertTrue(lowerer.contains("builder.beginPrepareStructured" + protocol + "();"),
                    protocol);
            assertTrue(lowerer.contains("builder.endPrepareStructured" + protocol + "();"),
                    protocol);
        }

        String phase = mainSource(
                "execution/bytecode/activation/structured/ProtosStructuredPhase.java");
        assertTrue(phase.contains("public enum ProtosStructuredPhase {"));
        assertFalse(phase.contains("abstract "));
        assertFalse(phase.contains("@Override"));
        assertFalse(phase.contains("ordinal()"));

        assertTrue(root.contains(
                "boxingEliminationTypes = {long.class, double.class}"));
        assertFalse(root.contains("int.class}"));
        for (String relation : List.of("Lt", "Le", "Gt", "Ge")) {
            String comparison = mainSource(
                    "execution/bytecode/numeric/ProtosRichCompare"
                            + relation + "Node.java");
            assertTrue(comparison.contains("static boolean doLL("), relation);
            assertTrue(comparison.contains("static boolean doDD("), relation);
        }
    }

    @Test
    void hostCallbacksAreIsolatedWithoutBoundingGuestExecution() throws Exception {
        String facility = mainSource("execution/facilities/ProtosPasswordArgon2Facility.java");
        assertTrue(boundaryPrecedes(facility, "private static void cancelHostTicket("));
        assertFalse(facility.contains("active.cancel();\n        });"));
        assertFalse(boundaryPrecedes(
                mainSource("runtime/actors/ProtosFutureValue.java"), "public boolean cancelRequest("));

        String scheduler = mainSource("runtime/actors/ProtosActorScheduler.java");
        assertTrue(boundaryPrecedes(scheduler, "private void submitWorker("));
        assertFalse(boundaryPrecedes(scheduler, "private void signal("));
        assertFalse(boundaryPrecedes(scheduler, "private void workerLoop("));
        assertFalse(boundaryPrecedes(scheduler, "private void runOneHostedSegment("));
        assertFalse(boundaryPrecedes(scheduler, "private void runOneSegment("));
        assertEquals(1, scheduler.split("carrierExecutor\\.execute\\(", -1).length - 1);

        String reader = mainSource("runtime/io/ProtosTextReader.java");
        assertTrue(boundaryPrecedes(reader, "private LineResult scanLine("));
        assertTrue(boundaryPrecedes(reader, "private ReadResult scanText("));
        assertTrue(boundaryPrecedes(reader, "private FoldResult resolvePendingLf("));
        assertFalse(boundaryPrecedes(reader, "private boolean advanceUntilInputOrTerminal("));
        assertFalse(boundaryPrecedes(reader, "private void startLowerRead("));
    }

    /*
     * Source-level structure of the production KDF cancellation paths. This
     * checks the call sites only; it is not evidence about runtime races.
     */
    @Test
    void passwordKdfCancellationCallsTheHostTicketHelperExactlyTwice() throws Exception {
        String facility = mainSource("execution/facilities/ProtosPasswordArgon2Facility.java");

        String declaration = "private static void cancelHostTicket(";
        assertEquals(1, facility.split("void cancelHostTicket\\(", -1).length - 1);
        assertTrue(boundaryPrecedes(facility, declaration));
        String helper = methodBody(facility, declaration);
        assertEquals("ProtosProcessExecutionHost.PasswordKdfTicket active",
                helper.substring(declaration.length(), helper.indexOf(')')).strip());
        assertEquals("active.cancel();",
                helper.substring(helper.indexOf('{') + 1, helper.lastIndexOf('}')).strip());

        String submit = methodBody(facility, "private static ProtosFutureValue submit(");
        String call = "cancelHostTicket(active);";
        assertEquals(2, submit.split("cancelHostTicket\\(", -1).length - 1);
        assertEquals(2, submit.split("cancelHostTicket\\(active\\);", -1).length - 1);
        // Every call site of the helper lives in the production submit method.
        assertEquals(3, facility.split("cancelHostTicket\\(", -1).length - 1);

        String callbackStart = "future.attachCancellationProducer(() -> {";
        int callbackAt = submit.indexOf(callbackStart);
        assertTrue(callbackAt >= 0, callbackStart);
        int callbackEnd = submit.indexOf("});", callbackAt);
        assertTrue(callbackEnd > callbackAt);
        String callback = submit.substring(callbackAt, callbackEnd);
        assertTrue(callback.contains("future.cancelTerminal();"));
        assertEquals(1, callback.split("cancelHostTicket\\(active\\);", -1).length - 1);

        int installAt = submit.indexOf("ticket.set(active);");
        assertTrue(installAt > callbackEnd, "ticket installation");
        String late = submit.substring(installAt).replaceAll("\\s+", " ");
        assertTrue(late.contains(
                "if (cancelled.get() || !future.isPending()) { " + call + " }"), late);

        String future = mainSource("runtime/actors/ProtosFutureValue.java");
        assertFalse(boundaryPrecedes(future, "public boolean cancelRequest("));
        assertFalse(boundaryPrecedes(future, "public boolean cancelTerminal("));
    }

    /* The declaration through its closing four-space-indented brace. */
    private static String methodBody(String source, String declaration) {
        int at = source.indexOf(declaration);
        assertTrue(at > 0, declaration);
        int end = source.indexOf("\n    }\n", at);
        assertTrue(end > at, declaration);
        return source.substring(at, end + "\n    }".length());
    }

    @Test
    void identityMapKeyLookupUsesItsBucketIndex() throws Exception {
        String protocol = mainSource(
                "execution/protocols/values/ProtosStandardIdentityMapProtocol.java");
        assertTrue(protocol.contains("m.findIdentical(k,h)"));
        assertFalse(protocol.contains("candidatesForRecordedIdentityHash"));

        String value = mainSource("runtime/values/ProtosIdentityMapValue.java");
        assertTrue(value.contains("public Entry findIdentical(Object key, ProtosNumericHashKey hash)"));
        assertTrue(value.contains("Bucket bucket = lookupBucket(hash);"));
    }

    @Test
    void actorIoCleanupCompletionAndBufferedArgumentFactoriesAreHostBoundaries() throws Exception {
        String domain = mainSource("runtime/ProtosActorExecutionDomain.java");
        assertTrue(boundaryPrecedes(domain,
                "void terminalActorIoLifecycleCleanupForRuntime(ProtosIoLifecycle lifecycle)"));
        assertTrue(boundaryPrecedes(domain,
                "public void terminalActorIoReleaseForRuntime(ProtosIoReleaseExecution release)"));

        String buffered = mainSource("runtime/io/ProtosBufferedByteIo.java");
        // The argument factories allocate exact-size arrays, which partial
        // evaluation handles directly; they need no host boundary.
        assertFalse(boundaryPrecedes(buffered, "private static Object[] noArguments()"));
        assertFalse(boundaryPrecedes(buffered,
                "private static Object[] singletonArguments(Object value)"));
        assertFalse(boundaryPrecedes(buffered, "private void pump("));
        assertFalse(boundaryPrecedes(buffered, "private void doFlush("));
    }

    @Test
    void noHistoricalInterpreterOrAlternateEngineIsReintroduced() throws Exception {
        for (String retired : List.of(
                "execution/bytecode/activation/structured/ProtosStructuredCallKind.java",
                "execution/ProtosResultKind.java",
                "execution/ProtosSemanticBytecodeRootNode.java")) {
            assertFalse(Files.exists(Path.of(MAIN + retired)), retired);
        }
        try (var sources = Files.walk(Path.of(MAIN))) {
            for (Path file : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                assertFalse(name.equals("ProtosStructuredCallKind.java"), file.toString());
                assertFalse(name.equals("ProtosResultKind.java"), file.toString());
                assertFalse(name.startsWith("ProtosSemanticBytecodeRootNode"), file.toString());
            }
        }
    }
}

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * I144 ABI fixture host and PLAT074 real-provider confinement experiments.
 *
 * <p>The CLI has no provider-option channel, so this host builds the test-only registration
 * {@code ffi-test:} ({@code plugins/ffm-fixture}, the generic FFM backend under a fixture-only
 * target policy) against the SPI-only class path, compiles {@code native/ffi_test.c}, and selects
 * that library exclusively through {@code ProtosForeignProviderConfiguration.withProviderOptions}.
 * Nothing in the JAR authorizes a library. Skipped (not passed) off Linux x86-64 or without a C
 * compiler.
 */
@Execution(ExecutionMode.SAME_THREAD)
class ProtosI144FfmFixtureAbiTest {
    private static final Path CASES = Path.of("protos", "tests", "foreign-provider");
    private static final String PROVIDER_ID = "protos-ffm-fixture";
    private static final String IMPORT = "ffi: import(\"ffi-test:ffi_test\")\n";

    @TempDir static Path work;
    static Path jar;
    static Path library;
    static Path sqliteJar;

    @BeforeAll
    static void buildFixture() throws Exception {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        assumeTrue(
                System.getProperty("os.name", "").equalsIgnoreCase("linux")
                        && (arch.equals("amd64") || arch.equals("x86_64")),
                "the FFM provider supports Linux x86-64 only");
        Path compiler = findCompiler();
        assumeTrue(compiler != null, "no C compiler (cc, gcc, or clang) on PATH");

        library = work.resolve("libffi_test.so");
        Process cc =
                new ProcessBuilder(
                                compiler.toString(), "-shared", "-fPIC", "-O2", "-std=c11",
                                "-Wall", "-Werror", "-o", library.toString(),
                                CASES.resolve("native").resolve("ffi_test.c").toString())
                        .redirectErrorStream(true)
                        .start();
        String ccOutput = new String(cc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(true, cc.waitFor(60, TimeUnit.SECONDS), "cc timed out");
        assertEquals(0, cc.exitValue(), ccOutput);

        Path classes = Files.createDirectories(work.resolve("classes"));
        List<String> arguments =
                new ArrayList<>(
                        List.of("--release", "25", "-proc:none", "-classpath",
                                ProtosExternalForeignProviderTest.publicClassPath(work), "-d",
                                classes.toString()));
        for (Path root :
                List.of(
                        Path.of("providers", "ffm", "src", "main", "java"),
                        CASES.resolve("plugins").resolve("ffm-fixture").resolve("src"))) {
            try (Stream<Path> sources = Files.walk(root)) {
                sources.filter(path -> path.toString().endsWith(".java"))
                        .map(Path::toString)
                        .forEach(arguments::add);
            }
        }
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        assertEquals(
                0,
                ToolProvider.getSystemJavaCompiler()
                        .run(null, diagnostics, diagnostics, arguments.toArray(String[]::new)),
                diagnostics::toString);
        jar =
                ProtosExternalForeignProviderTest.jar(
                        work,
                        classes,
                        "com.guillermomolina.protos.ffm",
                        "com.guillermomolina.protos.ffm.FfmFixtureProvider");
        sqliteJar = ProtosExternalForeignProviderTest.jar(
                Files.createDirectories(work.resolve("sqlite-provider")), classes,
                "com.guillermomolina.protos.ffm",
                "com.guillermomolina.protos.ffm.FfmForeignProvider");
    }

    @BeforeAll
    static void prepareRestrictedErrorClasses() throws Exception {
        // A first load under Landlock can fail and poison symbolic resolution for this JVM.
        ClassLoader loader = ProtosI144FfmFixtureAbiTest.class.getClassLoader();
        Class.forName("com.guillermomolina.protos.runtime.ProtosCoreErrors$StandardError", true, loader);
        Class.forName("com.guillermomolina.protos.runtime.ProtosIoOperation", true, loader);
        Class.forName("com.guillermomolina.protos.execution.ProtosDiagnosticTraceCapture", true, loader);
        Class.forName("com.guillermomolina.protos.execution.ProtosBytecodeRootNode$EpilogForException",
                true, loader);
        Class.forName("com.guillermomolina.protos.runtime.ProtosActorTerminationObservation", true, loader);
    }

    @Test
    void abiProgramRunsUnderTheTestHost() throws Exception {
        // The CLI success marker is not part of the checks: this embedded host has no print.
        String marker = "\nprint(\"I144-FFM-ABI-PASS\")";
        String program = Files.readString(CASES.resolve("ffm-abi.protos"));
        assertTrue(program.contains(marker), "ffm-abi.protos marker");
        String source = program.replace(marker, "\n");
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized())) {
            ProtosActivation observer = fixture.newActorActivation();
            int liveBaseline = nativeCount(fixture, observer, "ffi_test_live_count");
            ProtosExecutionOutcome outcome = fixture.run(source);
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () -> "ffm-abi.protos failed with " + describe(fixture, outcome.error()));

            // Still owned by the root session: the counter the program kept on purpose and the
            // owned out adopted by the call whose conversion failed (never visible to Protos).
            assertEquals(liveBaseline + 2, nativeCount(fixture, observer, "ffi_test_live_count"));
            int destroyed = destroyedCount(fixture, observer);
            ProtosForeignModuleFacadeValue facade =
                    (ProtosForeignModuleFacadeValue) fixture.eval(IMPORT + "ffi");
            ProtosForeignProviderSessionBinding session =
                    facade.attachmentForRuntime().orElseThrow().session();
            session.closeForRuntime();
            session.closeForRuntime();

            // Close destroyed exactly those two, once each: no leak and no double destructor.
            assertEquals(destroyed + 2, destroyedCount(fixture, observer));
            assertEquals(liveBaseline, nativeCount(fixture, observer, "ffi_test_live_count"));
        }
    }

    @Test
    void publicFfiProgramKeepsCustodyAndReleasesPendingResultsExactlyOnce() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized())) {
            ProtosActivation observer = fixture.newActorActivation();
            int liveBaseline = nativeCount(fixture, observer, "ffi_test_live_count");
            int destroyedBaseline = destroyedCount(fixture, observer);
            ProtosExecutionOutcome outcome =
                    fixture.run(Files.readString(CASES.resolve("ffm-public-api.protos")));
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(),
                    () -> "ffm-public-api.protos failed with " + describe(fixture, outcome.error()));
            assertEquals(liveBaseline + 2, nativeCount(fixture, observer, "ffi_test_live_count"));
            int destroyed = destroyedCount(fixture, observer);
            assertEquals(destroyedBaseline + 3, destroyed);
            ProtosObjectValue published = (ProtosObjectValue) outcome.value();
            Object retained = published.readLocalSlot("retained").orElseThrow();
            org.junit.jupiter.api.Assertions.assertInstanceOf(
                    com.guillermomolina.protos.runtime.ProtosClosureValue.class,
                    published.readLocalSlot("increment").orElseThrow());
            Object otherIncrement = fixture.eval(observer,
                    "FFI: import(\"std:ffi\")\n"
                            + "native: import(\"ffi-test:ffi_test\")\n"
                            + "counter: FFI.opaque(native, \"ffi_test_counter\")\n"
                            + "FFI.bind(native, \"ffi_test_counter_increment\", FFI.i64, Array(counter))");
            assertOrdinary(fixture, invoke(fixture, otherIncrement, retained));
            Object otherRelease = fixture.eval(observer, "import(\"std:ffi\").release");
            assertOrdinary(fixture, invoke(fixture, otherRelease, retained));

            // A library from another Actor/session cannot be used
            // by this MODULE's binding or release dispatch metadata.
            Object otherLibrary = fixture.eval(observer, IMPORT + "ffi");
            Object bind = fixture.eval("import(\"std:ffi\").bind");
            Object descriptor = fixture.eval(
                    "FFI: import(\"std:ffi\")\n"
                            + "FFI.opaque(import(\"ffi-test:ffi_test\"), \"ffi_test_counter\")");
            // Exercise session mismatch with a retained root descriptor.
            Object symbol = fixture.eval("\"ffi_test_counter_new\"");
            Object parameters = fixture.eval("Array(import(\"std:ffi\").i64)");
            assertOrdinary(fixture, invoke(fixture, bind, otherLibrary, symbol, descriptor, parameters));

            ProtosForeignModuleFacadeValue facade =
                    (ProtosForeignModuleFacadeValue) fixture.eval(IMPORT + "ffi");
            ProtosForeignProviderSessionBinding session =
                    facade.attachmentForRuntime().orElseThrow().session();
            session.closeForRuntime();
            session.closeForRuntime();
            assertEquals(destroyed + 2, destroyedCount(fixture, observer));
            assertEquals(liveBaseline, nativeCount(fixture, observer, "ffi_test_live_count"));
        }
    }

    @Test
    void privateLengthCarryingInputsCopyExactOctetsOnlyWithinTheCall() throws Exception {
        // LIB030 provider-private inputs: exact lengths, interior NUL, non-NULL empty regions,
        // the copy-destructor sentinel, remainder conversion and pre-entry refusals. Every
        // assertion is in Protos; this host only authorizes the fixture library.
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized())) {
            ProtosActivation observer = fixture.newActorActivation();
            int liveBaseline = nativeCount(fixture, observer, "ffi_test_live_count");
            ProtosExecutionOutcome outcome =
                    fixture.run(Files.readString(CASES.resolve("ffm-inputs.protos")));
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(),
                    () -> "ffm-inputs.protos failed with " + describe(fixture, outcome.error()));
            // Inputs never create provider custody: nothing is owned or retained.
            assertEquals(liveBaseline, nativeCount(fixture, observer, "ffi_test_live_count"));
            // Another Actor's session observes the same C copy but holds no input region.
            assertEquals(120, nativeCount(fixture, observer, "ffi_test_input_byte_zero"));
        }
    }

    @Test
    void publicDeclarationsDoNotOpenAConfiguredProviderSession() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized())) {
            assertEquals(ProtosExecutionOutcome.State.COMPLETED,
                    fixture.run("FFI: import(\"std:ffi\")\nFFI.out(FFI.i32)").state());
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());
        }
    }

    @Test
    void withoutTheHostOptionTheFixtureNeverLoads() throws Exception {
        for (Map<String, String> options :
                List.of(
                        Map.<String, String>of(),
                        Map.of("ffi_test.path", "libffi_test.so"),
                        Map.of("sqlite3.path", library.toString()))) {
            try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(options)) {
                assertEquals(
                        ProtosExecutionOutcome.State.FAILED,
                        fixture.run(IMPORT).state(),
                        options::toString);
            }
        }
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized())) {
            for (String rejected :
                    List.of(
                            "import(\"ffi-test:sqlite3\")",
                            "import(\"ffi-test:" + library + "\")",
                            "import(\"ffi:ffi_test\")")) {
                assertEquals(
                        ProtosExecutionOutcome.State.FAILED,
                        fixture.run(rejected).state(),
                        rejected);
            }
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());
        }
    }

    @Test
    void sessionCloseRevokesEverythingAndReleasesOwnedResourcesOnce() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized())) {
            ProtosActivation observer = fixture.newActorActivation();
            String sig = "sig: (r, p) => { ffi.param(ffi.signature(r), p) }\n";
            ProtosForeignModuleFacadeValue facade =
                    (ProtosForeignModuleFacadeValue) fixture.eval(IMPORT + "ffi");
            // Held provider values of the root Actor's session, invoked directly afterwards.
            Object identity =
                    fixture.eval(IMPORT + sig
                            + "ffi.bind(\"ffi_test_i32_identity\", sig(ffi.i32, ffi.i32))");
            Object copy = fixture.eval(IMPORT + "ffi.copy");
            Object release = fixture.eval(IMPORT + "ffi.release");
            Object isLive = fixture.eval(IMPORT + "ffi.isLive");
            Object region =
                    fixture.eval(IMPORT + sig
                            + "ffi.bind(\"ffi_test_bytes\", sig(ffi.borrowed, ffi.i32))(4)");
            Object counter =
                    fixture.eval(IMPORT + sig
                            + "owned: ffi.owned(ffi.opaque(\"ffi_test_counter\"),"
                            + " \"ffi_test_counter_free\", ffi.i32)\n"
                            + "ffi.bind(\"ffi_test_counter_new\", sig(owned, ffi.i64))(5)");
            int destroyedBefore = destroyedCount(fixture, observer);
            ProtosForeignProviderSessionBinding session =
                    facade.attachmentForRuntime().orElseThrow().session();

            session.closeForRuntime();
            session.closeForRuntime();

            // Close released the still-owned counter exactly once.
            assertEquals(destroyedBefore + 1, destroyedCount(fixture, observer));
            assertOrdinary(fixture, invoke(fixture, identity, 1));
            assertOrdinary(fixture, invoke(fixture, copy, region, 4));
            assertOrdinary(fixture, invoke(fixture, release, counter));
            assertOrdinary(fixture, invoke(fixture, isLive, counter));
            assertEquals(destroyedBefore + 1, destroyedCount(fixture, observer));
        }
    }

    // B2 is a falsification experiment: a failed confinement assertion is not an R1 defect.
    @Test
    void b2T1NativeTidMatchesThePhysicalCallerAndActorCarrier(TestReporter reporter)
            throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized())) {
            Probe probe = probe(fixture, fixture.root);
            long caller = kernelTid();
            assertEquals(caller, number(probe.call("threadId")));
            AtomicReference<Long> nativeTid = new AtomicReference<>();
            PlatformCall other = startPlatform(null,
                    () -> nativeTid.set(number(probe.call("threadId"))));
            other.finish();
            reporter.publishEntry("B2-T1", "JUnit caller=" + caller + ", Actor carrier="
                    + other.tid + ", C downcall=" + nativeTid.get());
            assertNotEquals(caller, other.tid);
            assertEquals(other.tid, nativeTid.get().longValue());
            // This source-backed root task has no suspension; its first segment runs directly.
            // T6 additionally witnesses the physical carrier's FfmBinding.invoke stack.
        }
    }

    @Test
    void b2T2RealProviderDowncallMustRemainInTheLandlockDomain(TestReporter reporter)
            throws Exception {
        Path root = Files.createTempDirectory(work, "b2-grant-").toRealPath();
        Path outside = Files.createTempDirectory(work, "b2-outside-").toRealPath();
        Path insideFile = Files.writeString(root.resolve("existing"), "inside");
        Path outsideFile = Files.writeString(outside.resolve("existing"), "outside");
        Path outsideCreate = outside.resolve("create");
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized());
                ProtosExternalForeignProviderTest.Fixture watching = fixture(authorized())) {
            Probe probe = probe(fixture, fixture.root);
            Probe observer = probe(watching, watching.newActorActivation());
            assertOpen(probe.open(outsideFile, 0), 0, 0);
            assertOpen(probe.open(outsideCreate, 1), 0, 0);
            Files.delete(outsideCreate); // Same exact path is absent for both create attempts.
            assertOpen(probe.open(root.resolve("missing"), 0), -1, 2); // ENOENT control.
            assertOpen(probe.open(insideFile, 0), 0, 0); // Warm the actual public invocation.
            observer.open(insideFile, 5);
            List<long[]> observations = new ArrayList<>();
            PlatformCall confined = startPlatform(root, () -> {
                observations.add(probe.open(insideFile, 0));
                observations.add(probe.open(root.resolve("created"), 1));
                observations.add(probe.open(outsideFile, 0));
                observations.add(probe.open(outsideCreate, 1));
                observations.add(probe.open(root.resolve("missing"), 0));
                observations.add(probe.open(insideFile, 2));
            });
            try {
                awaitGate(observer, insideFile, confined);
                assertOnStack(confined.thread, "FfmBinding", "invoke");
            } finally {
                observer.open(insideFile, 4);
                confined.finish();
            }
            reporter.publishEntry("B2-T2", "restricted TID=" + confined.tid + ", "
                    + observations.stream().map(java.util.Arrays::toString).toList());
            assertEquals(6, observations.size());
            for (long[] observation : observations) assertEquals(confined.tid, observation[2],
                    "FAIL B hypothesis: FFM escaped the restricted physical thread");
            assertOpen(observations.get(0), 0, 0);
            assertOpen(observations.get(1), 0, 0);
            assertOpen(observations.get(2), -1, 13); // EACCES after exact positive control.
            assertOpen(observations.get(3), -1, 13);
            assertOpen(observations.get(4), -1, 2);
            assertOpen(observations.get(5), 0, 0);
            assertFalse(Files.exists(outsideCreate));
            assertEquals("outside", Files.readString(outsideFile));
            assertTrue(Files.isRegularFile(root.resolve("created")));
            assertOpen(probe.open(outsideFile, 0), 0, 0);
            assertOpen(probe.open(outsideCreate, 1), 0, 0);
            Files.delete(outsideCreate);
        }
    }

    @Test
    void b2T3ColdAcquisitionAndPreparedBindingHaveSeparateAuthority(TestReporter reporter)
            throws Exception {
        Path root = Files.createTempDirectory(work, "b2-load-grant-").toRealPath();
        // A unique pathname/inode prevents an already-loaded fixture from satisfying dlopen.
        Path cold = Files.copy(library, work.resolve("libffi_test_b2_cold.so")).toRealPath();
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(
                Map.of("ffi_test.path", cold.toString()))) {
            Probe phases = phases(fixture, fixture.root); // No native acquisition or binding.
            ProtosForeignProviderSessionBinding binding = fixture.context.foreignSessionForRuntime(
                    fixture.actorOf(fixture.root), new ProtosForeignProviderId(PROVIDER_ID));
            assertRealSession(binding);
            AtomicReference<ProtosExecutionOutcome> acquired = new AtomicReference<>();
            PlatformCall restricted = startPlatform(root,
                    () -> acquired.set(phases.outcome("acquireLibrary")));
            restricted.finish();
            ProtosExecutionOutcome result = acquired.get();
            reporter.publishEntry("B2-T3 cold acquireModule/load", result.state() + " / "
                    + describe(fixture, result.error()) + "; library outside grant=" + cold);
            assertEquals(ProtosExecutionOutcome.State.FAILED, result.state(),
                    "unexpected cold exterior load: investigate loader authority before claiming B");
            assertSame(fixture.standardError("ForeignError"), result.error().parent().orElseThrow());
            assertEquals("library-unavailable", ((ProtosStringValue) result.error()
                    .readLocalSlot("foreignCategory").orElseThrow()).value(),
                    "a class-loading/admission failure is not evidence of native loader denial");
            // Retrying explicitly OUTSIDE the domain is a distinct control, not a fallback.
            Object rootLibrary = phases.call("acquireLibrary");
            // Resolve an as-yet-unbound symbol only after installing Landlock. Parsing/classes
            // may themselves need preparation; any such failure remains visible, not a PASS.
            Probe warming = phases(fixture, fixture.newActorActivation());
            Object warmLibrary = warming.call("acquireLibrary");
            warming.call("invokeBound", warming.call("bindThreadId", warmLibrary));
            // The root session still has never bound this symbol.
            AtomicReference<Object> bound = new AtomicReference<>();
            AtomicReference<Object> linked = new AtomicReference<>();
            PlatformCall prepared = startPlatform(root, () -> {
                linked.set(phases.call("bindThreadId", rootLibrary));
                bound.set(phases.call("invokeBound", linked.get()));
            });
            prepared.finish();
            reporter.publishEntry("B2-T3 prepared load / first bind / invoke",
                    "binding completed=" + (linked.get() instanceof ProtosClosureValue)
                    + ", restricted TID=" + prepared.tid + ", C TID=" + number(bound.get()));
            assertTrue(linked.get() instanceof ProtosClosureValue);
            assertEquals(prepared.tid, number(bound.get()));
        }
    }

    @Test
    void b2T3GrantLocalLibraryLoadAndBindingRunAfterRestriction(TestReporter reporter)
            throws Exception {
        Path root = Files.createTempDirectory(work, "b2-local-load-").toRealPath();
        Path localLibrary = Files.copy(library, root.resolve("libffi_test_local.so"));
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(
                Map.of("ffi_test.path", localLibrary.toString()))) {
            Probe phases = phases(fixture, fixture.root);
            ProtosForeignProviderSessionBinding session = fixture.context.foreignSessionForRuntime(
                    fixture.actorOf(fixture.root), new ProtosForeignProviderId(PROVIDER_ID));
            assertRealSession(session);
            AtomicReference<Object> result = new AtomicReference<>();
            AtomicReference<Object> acquired = new AtomicReference<>();
            AtomicReference<Object> linked = new AtomicReference<>();
            PlatformCall restricted = startPlatform(root, () -> {
                acquired.set(phases.call("acquireLibrary"));
                linked.set(phases.call("bindThreadId", acquired.get()));
                result.set(phases.call("invokeBound", linked.get()));
            });
            restricted.finish();
            reporter.publishEntry("B2-T3 grant-local load/bind/invoke", "acquisition completed="
                    + (acquired.get() instanceof ProtosForeignModuleFacadeValue)
                    + ", binding completed=" + (linked.get() instanceof ProtosClosureValue)
                    + ", restricted TID=" + restricted.tid + ", C TID=" + number(result.get())
                    + "; dependencies already resident in this JVM are not cold-load evidence");
            assertTrue(acquired.get() instanceof ProtosForeignModuleFacadeValue);
            assertTrue(linked.get() instanceof ProtosClosureValue);
            assertEquals(restricted.tid, number(result.get()));
        }
    }

    @Test
    void b2T3HostInitializationInsideRestrictionIsAnIndependentExperiment(TestReporter reporter)
            throws Exception {
        Path root = Files.createTempDirectory(work, "b2-host-grant-").toRealPath();
        AtomicReference<ProtosExternalForeignProviderTest.Fixture> created = new AtomicReference<>();
        AtomicReference<Throwable> initializationFailure = new AtomicReference<>();
        PlatformCall restricted = startPlatform(root, () -> {
            try {
                created.set(fixture(authorized()));
                created.get().eval("import(\"std:ffi\")");
            } catch (Throwable failure) {
                initializationFailure.set(failure);
            }
        });
        restricted.finish();
        try {
            reporter.publishEntry("B2-T3 host initialization",
                    initializationFailure.get() == null ? "OBSERVED: completed inside restriction"
                            : "REQUIRES PREPARATION / UNPROVEN: " + initializationFailure.get());
            // This is phase evidence, not an assertion of native confinement. A class-loading
            // refusal cannot be reclassified as a successful confined downcall.
            if (created.get() != null) {
                assertEquals(0, created.get().context.foreignProviderSessionCountForTesting());
            }
            assumeTrue(initializationFailure.get() == null,
                    "UNPROVEN: host initialization requires preparation: " + initializationFailure.get());
        } finally {
            if (created.get() != null) created.get().close();
        }
    }

    @Test
    void b2T4ExplicitReleaseMustDestroyInsideTheRestrictedDomain(TestReporter reporter)
            throws Exception {
        Path root = Files.createTempDirectory(work, "b2-release-grant-").toRealPath();
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized())) {
            Probe probe = probe(fixture, fixture.root);
            Probe observer = probe(fixture, fixture.newActorActivation());
            probe.call("releaseHere"); // Warm owned acquisition, invocation and release.
            long live = number(observer.call("liveCount"));
            long destroyed = number(observer.call("destroyedCount"));
            AtomicReference<long[]> result = new AtomicReference<>();
            PlatformCall restricted = startPlatform(root,
                    () -> result.set(numbers(probe.call("releaseHere"), 5)));
            restricted.finish();
            reporter.publishEntry("B2-T4", "restricted TID=" + restricted.tid + ", release="
                    + java.util.Arrays.toString(result.get()));
            assertEquals(41, result.get()[0]);
            assertEquals(0, result.get()[1]);
            assertEquals(restricted.tid, result.get()[2], "FAIL B: explicit destructor escaped");
            assertEquals(live, number(observer.call("liveCount")));
            assertEquals(destroyed + 1, number(observer.call("destroyedCount")));
            probe.session.closeForRuntime();
            probe.session.closeForRuntime();
            assertEquals(destroyed + 1, number(observer.call("destroyedCount")));
        }
    }

    @Test
    void b2T5OtherHostThreadCloseMustNotMoveTheDestructorOutsideTheDomain(TestReporter reporter)
            throws Exception {
        Path root = Files.createTempDirectory(work, "b2-close-grant-").toRealPath();
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized())) {
            Probe probe = probe(fixture, fixture.root);
            Probe observer = probe(fixture, fixture.newActorActivation());
            probe.call("releaseHere");
            long live = number(observer.call("liveCount"));
            long destroyed = number(observer.call("destroyedCount"));
            PlatformCall restricted = startPlatform(root, () -> probe.call("keepForClose"));
            restricted.finish();
            Object retained = probe.call("retained");
            assertEquals(live + 1, number(observer.call("liveCount")));
            PlatformCall closer = startPlatform(null, probe.session::closeForRuntime);
            closer.finish();
            long destructor = number(observer.call("lastDestroyThreadId"));
            probe.session.closeForRuntime();
            assertFalse(probe.session.isOpenForRuntime());
            probe.refuses("incrementOwned", retained);
            probe.refuses("releaseOwned", retained);
            assertEquals(live, number(observer.call("liveCount")));
            assertEquals(destroyed + 1, number(observer.call("destroyedCount")));
            reporter.publishEntry("B2-T5", "restricted TID=" + restricted.tid + ", closer TID="
                    + closer.tid + ", destructor TID=" + destructor + ", confinement="
                    + (destructor == restricted.tid ? "SATISFIED" : "FAIL B PROPERTY"));
            // Deliberately a failing hypothesis on the current trusted provider if close runs C
            // on the unrestricted closer. Do not change FfmSession.close to make this green.
            assertEquals(restricted.tid, destructor,
                    "FAIL B architecture: session-close destructor outside grant; valid R1 behavior");
        }
    }

    @Test
    void b2T6CloseWaitsForARealAdmittedDowncallWithoutProvingDestructorConfinement(
            TestReporter reporter) throws Exception {
        Path file = Files.writeString(work.resolve("b2-pending"), "pending").toRealPath();
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized());
                ProtosExternalForeignProviderTest.Fixture watching = fixture(authorized())) {
            Probe probe = probe(fixture, fixture.root);
            Probe observer = probe(watching, watching.newActorActivation());
            long live = number(observer.call("liveCount"));
            long destroyed = number(observer.call("destroyedCount"));
            probe.call("keepForClose");
            observer.open(file, 5);
            AtomicReference<ProtosExecutionOutcome> terminal = new AtomicReference<>();
            AtomicReference<Long> nativeTid = new AtomicReference<>();
            PlatformCall active = startPlatform(null, () -> {
                nativeTid.set(number(probe.call("threadId")));
                terminal.set(probe.outcome("open", file.toString(), 2));
            });
            PlatformCall closer = null;
            try {
                awaitGate(observer, file, active);
                assertOnStack(active.thread, "FfmBinding", "invoke");
                closer = startPlatform(null, probe.session::closeForRuntime);
                PlatformCall closing = closer;
                awaitCondition(() -> closing.thread.getState() == Thread.State.WAITING
                                && hasFrame(closing.thread, "FfmSession", "close"),
                        "close never reached the actual FFM session");
                assertTrue(closer.thread.isAlive());
                assertFalse(probe.session.isOpenForRuntime());
                assertEquals(live + 1, number(observer.call("liveCount")));
                assertEquals(destroyed, number(observer.call("destroyedCount")),
                        "premature destruction while native call remains admitted");
            } finally {
                observer.open(file, 4);
                active.finish();
                if (closer != null) closer.finish();
            }
            assertEquals(active.tid, nativeTid.get().longValue());
            // closeForRuntime revokes the facade generation before waiting. Reading the
            // private indexed output after C returns may therefore be an ordinary Error.
            // Record publication separately; do not change the adapter or weaken the C gate.
            ProtosExecutionOutcome publication = terminal.get();
            if (publication.state() == ProtosExecutionOutcome.State.COMPLETED) {
                long[] result = numbers(publication.value(), 3);
                assertOpen(result, 0, 0);
                assertEquals(active.tid, result[2]);
            } else {
                assertEquals(ProtosExecutionOutcome.State.FAILED, publication.state());
                assertOrdinary(fixture, publication.error());
            }
            assertEquals(live, number(observer.call("liveCount")));
            assertEquals(destroyed + 1, number(observer.call("destroyedCount")));
            long destructor = number(observer.call("lastDestroyThreadId"));
            reporter.publishEntry("B2-T6", "wait guarantee satisfied; active TID=" + active.tid
                    + ", close TID=" + closer.tid + ", destructor TID=" + destructor
                    + ", public output=" + publication.state()
                    + "; this case installs no Landlock and proves no confinement");
            probe.session.closeForRuntime();
            assertEquals(destroyed + 1, number(observer.call("destroyedCount")));
        }
    }

    @Test
    void b2T7ActorSessionsRejectHandlesButDoNotConfineUnrestrictedNativeEntry(
            TestReporter reporter) throws Exception {
        Path root = Files.createTempDirectory(work, "b2-actor-grant-").toRealPath();
        Path outside = Files.writeString(work.resolve("b2-actor-outside"), "outside").toRealPath();
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized())) {
            Probe first = probe(fixture, fixture.newActorActivation());
            Probe second = probe(fixture, fixture.newActorActivation());
            assertNotSame(first.session, second.session);
            assertNotSame(first.session.sessionForRuntime(), second.session.sessionForRuntime());
            Object handle = first.call("createOwned");
            second.refuses("incrementOwned", handle);
            second.refuses("releaseOwned", handle);
            // An owner must still be able to use its handle after a foreign-session refusal.
            assertEquals(41, number(first.call("incrementOwned", handle)));
            ProtosSignalException transfer = assertThrows(ProtosSignalException.class,
                    () -> ProtosActorValueTransfer.snapshotValue(handle, first.actor));
            assertSame(fixture.standardError("NonTransferableValue"),
                    transfer.error().parent().orElseThrow());
            first.call("releaseOwned", handle);
            first.refuses("incrementOwned", handle);
            first.refuses("releaseOwned", handle);
            Object next = first.call("createOwned");
            Object ownSecond = second.call("createOwned");
            assertNotSame(handle, next, "native address reuse must not revive a retired handle");
            second.refuses("incrementOwned", next);
            first.refuses("incrementOwned", ownSecond);
            assertEquals(41, number(second.call("incrementOwned", ownSecond)));
            first.call("releaseOwned", next);
            second.call("releaseOwned", ownSecond);
            AtomicReference<Long> confinedTid = new AtomicReference<>();
            CountDownLatch confinedReady = new CountDownLatch(1);
            CountDownLatch finishDomain = new CountDownLatch(1);
            PlatformCall confined = startPlatform(root, () -> {
                confinedTid.set(number(first.call("threadId")));
                confinedReady.countDown();
                assertTrue(finishDomain.await(20, TimeUnit.SECONDS));
            });
            AtomicReference<long[]> exterior = new AtomicReference<>();
            AtomicReference<long[]> migrated = new AtomicReference<>();
            PlatformCall unrestricted;
            try {
                assertTrue(confinedReady.await(5, TimeUnit.SECONDS));
                unrestricted = startPlatform(null, () -> {
                    exterior.set(second.open(outside, 0));
                    migrated.set(first.open(outside, 0)); // Same first session, new carrier.
                });
                unrestricted.finish();
            } finally {
                finishDomain.countDown();
                confined.finish();
            }
            assertEquals(confined.tid, confinedTid.get().longValue());
            assertEquals(unrestricted.tid, exterior.get()[2]);
            assertNotEquals(confined.tid, unrestricted.tid);
            assertOpen(exterior.get(), 0, 0);
            assertOpen(migrated.get(), 0, 0);
            assertEquals(unrestricted.tid, migrated.get()[2]);
            reporter.publishEntry("B2-T7", "distinct Actor sessions and TIDs; exterior acquisition"
                    + " succeeds on unrestricted carrier, including the first session after"
                    + " carrier migration: FAIL B confinement property, valid trusted R1 access");
        }
    }

    @Test
    void b2T8AuthorizedFfiIsANativeRouteWithoutImportingSqlite(TestReporter reporter)
            throws Exception {
        Path outside = Files.writeString(work.resolve("b2-alternative"), "native").toRealPath();
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized())) {
            fixture.eval("import(\"std:ffi\")");
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());
            Probe probe = probe(fixture, fixture.root);
            assertRealSession(probe.session);
            ProtosForeignProviderDescriptor descriptor = fixture.context
                    .foreignProviderRegistryForRuntime()
                    .lookup(new ProtosForeignProviderId(PROVIDER_ID)).orElseThrow();
            assertEquals(ProtosForeignProviderExecutionProfile.TRUSTED_IN_PROCESS,
                    descriptor.profile());
            assertTrue(descriptor.enforcement().isEmpty());
            assertOpen(probe.open(outside, 0), 0, 0);
            assertEquals(1, fixture.context.foreignProviderSessionCountForTesting());
            reporter.publishEntry("B2-T8", "TRUSTED_IN_PROCESS native access through std:ffi alone;"
                    + " no std:sqlite import or durable grant; future policy remains UNPROVEN");
        }
        // Preserve the existing authorization refusals rather than banning R1 access.
        withoutTheHostOptionTheFixtureNeverLoads();
    }

    @Test
    void b2T9ExistingAbiAdmissionBorrowCustodyAndIrreversibleCloseRemainIntact()
            throws Exception {
        // Reuse the complete established assertions: ordering/out slots, pre-entry admission,
        // borrowed epochs, adoption before publication, cross-session rejection and closed use.
        abiProgramRunsUnderTheTestHost();
        publicFfiProgramKeepsCustodyAndReleasesPendingResultsExactlyOnce();
        sessionCloseRevokesEverythingAndReleasesOwnedResourcesOnce();
    }

    @Test
    void b3T1ExternallyRequestedCloseMustDestroyOnRestrictedOwnerThread(TestReporter reporter)
            throws Exception {
        Path root = Files.createTempDirectory(work, "b3-close-grant-").toRealPath();
        Path outside = Files.writeString(work.resolve("b3-close-outside"), "outside").toRealPath();
        ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized());
        PlatformCall restricted = null;
        Throwable primary = null;
        Probe probe = null;
        CountDownLatch created = new CountDownLatch(1);
        CountDownLatch closeRequested = new CountDownLatch(1);
        CountDownLatch closeFinished = new CountDownLatch(1);
        AtomicReference<Object> retained = new AtomicReference<>();
        AtomicReference<Long> physicalTid = new AtomicReference<>();
        AtomicReference<Long> nativeTid = new AtomicReference<>();
        AtomicReference<String> requestResult = new AtomicReference<>("NOT REQUESTED");
        long live = -1;
        long destroyed = -1;
        long finalLive = -1;
        long finalDestroyed = -1;
        long destructor = -1;
        try (ProtosExternalForeignProviderTest.Fixture watching = fixture(authorized())) {
            probe = probe(fixture, fixture.root);
            Probe owner = probe;
            Probe observer = probe(watching, watching.newActorActivation());
            // Resolve provider classes without creating/releasing an owned resource on JUnit.
            ProtosForeignPluginProvider.Session bridge =
                    (ProtosForeignPluginProvider.Session) owner.session.sessionForRuntime();
            ClassLoader loader = bridge.plugin.getClass().getClassLoader();
            try (java.util.jar.JarFile prepared = new java.util.jar.JarFile(jar.toFile())) {
                for (java.util.jar.JarEntry entry : java.util.Collections.list(prepared.entries())) {
                    if (entry.getName().endsWith(".class")) {
                        Class.forName(entry.getName().replace('/', '.')
                                .replaceFirst("\\.class$", ""), true, loader);
                    }
                }
            }
            owner.call("threadId");
            observer.call("lastDestroyThreadId");
            live = number(observer.call("liveCount"));
            destroyed = number(observer.call("destroyedCount"));
            assertOpen(owner.open(outside, 0), 0, 0);
            assertOpen(observer.open(outside, 0), 0, 0);
            restricted = startPlatform(root, () -> {
                Throwable failure = null;
                try {
                    physicalTid.set(kernelTid());
                    nativeTid.set(number(owner.call("threadId")));
                    assertEquals(physicalTid.get(), nativeTid.get());
                    long[] denied = owner.open(outside, 0);
                    assertOpen(denied, -1, 13);
                    assertEquals(physicalTid.get().longValue(), denied[2]);
                    assertEquals(41, number(owner.call("keepForClose")));
                    retained.set(owner.call("retained"));
                    created.countDown();
                    assertTrue(closeRequested.await(20, TimeUnit.SECONDS),
                            "external close request timed out");
                    assertEquals("REQUESTED", requestResult.get(),
                            "emergency barrier release is not a successful request");
                    owner.session.closeForRuntime();
                    assertFalse(owner.session.isOpenForRuntime());
                    // The idempotence check also stays on the restricted owner thread.
                    owner.session.closeForRuntime();
                    requestResult.set("CLOSE COMPLETED");
                } catch (Throwable problem) {
                    failure = problem;
                    requestResult.set("FAILED: " + problem);
                } finally {
                    // Never retry a failed close/destructor. An early failure can only drain
                    // custody here, before this same restricted thread is allowed to exit.
                    if (owner.session.isOpenForRuntime()) {
                        try { owner.session.closeForRuntime(); }
                        catch (Throwable cleanup) {
                            if (failure == null) failure = cleanup;
                            else failure.addSuppressed(cleanup);
                        }
                    }
                    created.countDown();
                    closeFinished.countDown();
                }
                if (failure != null) throw new AssertionError("restricted close failed", failure);
            });
            assertTrue(created.await(20, TimeUnit.SECONDS), "owned creation timed out");
            assertTrue(restricted.thread.isAlive(), "owner exited before the close request");
            assertTrue(restricted.installed, "Landlock was not installed");
            assertTrue(probe.session.isOpenForRuntime());
            assertEquals(1, closeFinished.getCount(), "close completed before its request");
            assertEquals(live + 1, number(observer.call("liveCount")));
            assertEquals(destroyed, number(observer.call("destroyedCount")));
            assertOpen(observer.open(outside, 0), 0, 0);
            requestResult.set("REQUESTED");
            closeRequested.countDown(); // JUnit only signals; it never closes the session.
            assertTrue(closeFinished.await(20, TimeUnit.SECONDS), "restricted close timed out");
            finalLive = number(observer.call("liveCount"));
            finalDestroyed = number(observer.call("destroyedCount"));
            destructor = number(observer.call("lastDestroyThreadId"));
            restricted.finish();
            assertEquals("CLOSE COMPLETED", requestResult.get());
            assertFalse(probe.session.isOpenForRuntime());
            assertEquals(live, finalLive);
            assertEquals(destroyed + 1, finalDestroyed);
            assertEquals(restricted.tid, physicalTid.get().longValue());
            assertEquals(restricted.tid, destructor, "session-close destructor escaped Landlock");
            probe.refuses("incrementOwned", retained.get());
            probe.refuses("releaseOwned", retained.get());
            assertEquals(destroyed + 1, number(observer.call("destroyedCount")));
            assertOpen(observer.open(outside, 0), 0, 0);
        } catch (Exception | Error problem) {
            primary = problem;
            throw problem;
        } finally {
            closeRequested.countDown();
            Throwable cleanupFailure = null;
            if (restricted != null) {
                try { restricted.finish(); }
                catch (Throwable cleanup) { cleanupFailure = cleanup; }
            }
            // A pending owner must never race Fixture.close on an unrestricted thread.
            if (restricted == null || (!restricted.thread.isAlive()
                    && probe != null && !probe.session.isOpenForRuntime())) {
                try { fixture.close(); }
                catch (Throwable cleanup) {
                    if (cleanupFailure == null) cleanupFailure = cleanup;
                    else cleanupFailure.addSuppressed(cleanup);
                }
            } else {
                AssertionError pending = new AssertionError(
                        "owner/session still pending; unrestricted Fixture.close deliberately withheld");
                if (cleanupFailure == null) cleanupFailure = pending;
                else cleanupFailure.addSuppressed(pending);
            }
            reporter.publishEntry("B3-T1", "restricted TID=" + physicalTid.get()
                    + ", C TID=" + nativeTid.get() + ", destructor TID=" + destructor
                    + ", Landlock=" + (restricted != null && restricted.installed)
                    + ", live=" + live + " -> " + finalLive
                    + ", destroyed=" + destroyed + " -> " + finalDestroyed
                    + ", request=" + requestResult.get()
                    + ", owner alive=" + (restricted != null && restricted.thread.isAlive())
                    + ", owner failure=" + (restricted == null ? null : restricted.failure)
                    + ", cleanup failure=" + cleanupFailure);
            if (cleanupFailure != null) {
                if (primary != null) primary.addSuppressed(cleanupFailure);
                else throw new AssertionError("B3-T1 cleanup failed", cleanupFailure);
            }
        }
    }

    @Test
    void b3T2MultipleOwnedResourcesReleaseAndCloseOnRestrictedThread(TestReporter reporter)
            throws Exception {
        Path root = Files.createTempDirectory(work, "b3-many-").toRealPath();
        ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized());
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[1];
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch request = new CountDownLatch(1);
        Object[] retained = new Object[2];
        Throwable primary = null;
        ProtosExternalForeignProviderTest.Fixture watching = null;
        try {
            watching = fixture(authorized());
            Probe owner = owners[0] = probe(fixture, fixture.root);
            Probe observer = probe(watching, watching.newActorActivation());
            prepareB3(owner);
            long[] before = b3Counts(observer);
            reportB3Counts(reporter, "B3-T2 initial", before, observer);
            calls[0] = startB3Owner(root, owner, () -> {
                assertEquals(kernelTid(), number(owner.call("threadId")));
                long[] values = numbers(owner.call("keepManyForClose"), 3);
                assertEquals(41, values[0]);
                assertEquals(61, values[1]);
                assertEquals(0, values[2]);
                assertEquals(before[1] + 1, number(owner.call("destroyedCount")));
                retained[0] = owner.call("retained");
                retained[1] = owner.call("retainedThird");
                ready.countDown();
                awaitB3(request, "multiple-resource close request");
                closeB3Owner(owner);
            });
            awaitB3(ready, "multiple-resource creation");
            assertTrue(calls[0].thread.isAlive());
            assertTrue(calls[0].installed);
            assertB3Counts(observer, before[0] + 2, before[1] + 1, before[2]);
            request.countDown();
            calls[0].finish();
            reportB3Counts(reporter, "B3-T2", before, observer);
            assertB3Counts(observer, before[0], before[1] + 3, before[2]);
            assertEquals(calls[0].tid, number(observer.call("lastDestroyThreadId")));
            assertFalse(owner.session.isOpenForRuntime());
            for (Object handle : retained) {
                owner.refuses("incrementOwned", handle);
                owner.refuses("releaseOwned", handle);
            }
            assertB3Counts(observer, before[0], before[1] + 3, before[2]);
        } catch (Exception | Error problem) {
            primary = problem;
            throw problem;
        } finally {
            request.countDown();
            finishB3(primary, reporter, "B3-T2", fixture, owners, calls, watching);
        }
    }

    @Test
    void b3T3CloseRequestDuringNativeCallWaitsUntilOwnerCanClose(TestReporter reporter)
            throws Exception {
        Path root = Files.createTempDirectory(work, "b3-active-").toRealPath();
        Path file = Files.writeString(root.resolve("allowed"), "allowed").toRealPath();
        ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized());
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[1];
        CountDownLatch request = new CountDownLatch(1);
        AtomicReference<long[]> result = new AtomicReference<>();
        Throwable primary = null;
        // Keep the observer alive until the native barrier has been released and joined.
        ProtosExternalForeignProviderTest.Fixture watching = null;
        Probe observer = null;
        try {
            watching = fixture(authorized());
            Probe owner = owners[0] = probe(fixture, fixture.root);
            observer = probe(watching, watching.newActorActivation());
            prepareB3(owner);
            assertOpen(owner.open(file, 0), 0, 0);
            long[] before = b3Counts(observer);
            reportB3Counts(reporter, "B3-T3 initial", before, observer);
            assertOpen(observer.open(file, 5), 0, 0);
            calls[0] = startB3Owner(root, owner, () -> {
                assertEquals(kernelTid(), number(owner.call("threadId")));
                assertEquals(41, number(owner.call("keepForClose")));
                long[] completed = owner.open(file, 2);
                result.set(completed);
                assertOpen(completed, 0, 0);
                assertEquals(kernelTid(), completed[2]);
                awaitB3(request, "active-downcall close request");
                closeB3Owner(owner);
            });
            awaitGate(observer, file, calls[0]);
            assertOnStack(calls[0].thread, "FfmBinding", "invoke");
            assertTrue(calls[0].installed);
            assertOpen(observer.open(file, 3), 1, 0);
            request.countDown();
            assertEquals(0, request.getCount(), "request must precede native barrier release");
            assertTrue(owner.session.isOpenForRuntime());
            assertB3Counts(observer, before[0] + 1, before[1], before[2]);
            reporter.publishEntry("B3-T3 request", "recorded while real C barrier is active;"
                    + " owner TID=" + calls[0].tid + "; no session close on JUnit");
            assertOpen(observer.open(file, 4), 0, 0);
            calls[0].finish();
            reporter.publishEntry("B3-T3 native result", "[status, errno, Linux TID]="
                    + java.util.Arrays.toString(result.get()));
            assertOpen(result.get(), 0, 0);
            assertEquals(calls[0].tid, result.get()[2]);
            assertEquals(calls[0].tid, number(observer.call("lastDestroyThreadId")));
            assertFalse(owner.session.isOpenForRuntime());
            reportB3Counts(reporter, "B3-T3", before, observer);
            assertB3Counts(observer, before[0], before[1] + 1, before[2]);
        } catch (Exception | Error problem) {
            primary = problem;
            throw problem;
        } finally {
            request.countDown();
            Throwable cleanupFailure = null;
            if (observer != null) {
                try { assertOpen(observer.open(file, 4), 0, 0); }
                catch (Throwable cleanup) { cleanupFailure = cleanup; }
            }
            try { finishB3(primary, reporter, "B3-T3", fixture, owners, calls); }
            catch (Throwable cleanup) {
                if (cleanupFailure == null) cleanupFailure = cleanup;
                else cleanupFailure.addSuppressed(cleanup);
            }
            if (watching != null) {
                try { closeB3Fixture(watching); }
                catch (Throwable cleanup) {
                    if (cleanupFailure == null) cleanupFailure = cleanup;
                    else cleanupFailure.addSuppressed(cleanup);
                }
            }
            if (cleanupFailure != null) {
                if (primary != null) primary.addSuppressed(cleanupFailure);
                else throw new AssertionError("B3-T3 cleanup failed", cleanupFailure);
            }
        }
    }

    @Test
    void b3T4IndependentActorsAndGrantsCloseOnTheirOwnRestrictedThreads(TestReporter reporter)
            throws Exception {
        Path[] roots = {Files.createTempDirectory(work, "b3-first-").toRealPath(),
                Files.createTempDirectory(work, "b3-second-").toRealPath()};
        Path[] files = {Files.writeString(roots[0].resolve("first"), "first").toRealPath(),
                Files.writeString(roots[1].resolve("second"), "second").toRealPath()};
        ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized());
        Probe[] owners = new Probe[2];
        PlatformCall[] calls = new PlatformCall[2];
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch membershipChecked = new CountDownLatch(2);
        CountDownLatch[] requests = {new CountDownLatch(1), new CountDownLatch(1)};
        Object[] handles = new Object[2];
        Throwable primary = null;
        ProtosExternalForeignProviderTest.Fixture watching = null;
        try {
            watching = fixture(authorized());
            owners[0] = probe(fixture, fixture.newActorActivation());
            owners[1] = probe(fixture, fixture.newActorActivation());
            assertNotSame(owners[0].session, owners[1].session);
            assertNotSame(owners[0].session.sessionForRuntime(), owners[1].session.sessionForRuntime());
            Probe observer = probe(watching, watching.newActorActivation());
            long[] before = b3Counts(observer);
            reportB3Counts(reporter, "B3-T4 initial", before, observer);
            for (int i = 0; i < 2; i++) {
                prepareB3(owners[i]);
                assertOpen(owners[i].open(files[i], 0), 0, 0);
                assertOpen(owners[i].open(files[1 - i], 0), 0, 0);
                final int index = i;
                Probe owner = owners[i];
                calls[i] = startB3Owner(roots[i], owner, () -> {
                    long tid = kernelTid();
                    assertEquals(tid, number(owner.call("threadId")));
                    long[] admitted = owner.open(files[index], 0);
                    assertOpen(admitted, 0, 0);
                    assertEquals(tid, admitted[2]);
                    long[] denied = owner.open(files[1 - index], 0);
                    assertOpen(denied, -1, 13);
                    assertEquals(tid, denied[2]);
                    assertEquals(41, number(owner.call("keepForClose")));
                    handles[index] = owner.call("retained");
                    ready.countDown();
                    awaitB3(requests[index], "independent Actor close request");
                    // Check actual session membership on each owning carrier, not thread identity.
                    owner.refuses("incrementOwned", handles[1 - index]);
                    owner.refuses("releaseOwned", handles[1 - index]);
                    assertEquals(42, number(owner.call("incrementOwned", handles[index])));
                    membershipChecked.countDown();
                    awaitB3(membershipChecked, "both live-session membership checks");
                    closeB3Owner(owner);
                });
            }
            awaitB3(ready, "both restricted Actors");
            assertNotEquals(calls[0].tid, calls[1].tid);
            for (int i = 0; i < 2; i++) {
                assertTrue(calls[i].thread.isAlive());
                assertTrue(calls[i].installed);
                final int index = i;
                ProtosSignalException transfer = assertThrows(ProtosSignalException.class,
                        () -> ProtosActorValueTransfer.snapshotValue(handles[index], owners[index].actor));
                assertSame(fixture.standardError("NonTransferableValue"),
                        transfer.error().parent().orElseThrow());
                assertOpen(observer.open(files[i], 0), 0, 0);
            }
            assertB3Counts(observer, before[0] + 2, before[1], before[2]);
            for (CountDownLatch request : requests) request.countDown();
            for (PlatformCall call : calls) call.finish();
            reportB3Counts(reporter, "B3-T4", before, observer);
            assertB3Counts(observer, before[0], before[1] + 2, before[2]);
            for (int i = 0; i < 2; i++) {
                assertFalse(owners[i].session.isOpenForRuntime());
                owners[i].refuses("incrementOwned", handles[i]);
                owners[i].refuses("releaseOwned", handles[i]);
                assertOpen(observer.open(files[i], 0), 0, 0);
            }
            assertB3Counts(observer, before[0], before[1] + 2, before[2]);
        } catch (Exception | Error problem) {
            primary = problem;
            throw problem;
        } finally {
            for (CountDownLatch request : requests) request.countDown();
            while (membershipChecked.getCount() > 0) membershipChecked.countDown();
            finishB3(primary, reporter, "B3-T4", fixture, owners, calls, watching);
        }
    }

    @Test
    void b3T5AliveRestrictedOwnerDoesNotConstrainExternalCloser(TestReporter reporter)
            throws Exception {
        Path root = Files.createTempDirectory(work, "b3-negative-").toRealPath();
        ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized());
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[2];
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch exit = new CountDownLatch(1);
        Throwable primary = null;
        ProtosExternalForeignProviderTest.Fixture watching = null;
        try {
            watching = fixture(authorized());
            Probe owner = owners[0] = probe(fixture, fixture.root);
            Probe observer = probe(watching, watching.newActorActivation());
            prepareB3(owner);
            long[] before = b3Counts(observer);
            reportB3Counts(reporter, "B3-T5 initial", before, observer);
            calls[0] = startB3Owner(root, owner, () -> {
                assertEquals(kernelTid(), number(owner.call("threadId")));
                assertEquals(41, number(owner.call("keepForClose")));
                ready.countDown();
                awaitB3(exit, "negative-control owner exit");
                assertFalse(owner.session.isOpenForRuntime());
                // The external closer already finished; do not perform another close here.
            });
            awaitB3(ready, "negative-control creation");
            assertTrue(calls[0].thread.isAlive());
            assertTrue(calls[0].installed);
            assertB3Counts(observer, before[0] + 1, before[1], before[2]);
            calls[1] = startPlatform(null, owner.session::closeForRuntime);
            calls[1].finish();
            assertTrue(calls[0].thread.isAlive(), "restricted owner must still be alive");
            assertFalse(owner.session.isOpenForRuntime());
            long destructor = number(observer.call("lastDestroyThreadId"));
            assertEquals(calls[1].tid, destructor);
            assertNotEquals(calls[0].tid, destructor);
            reportB3Counts(reporter, "B3-T5", before, observer);
            assertB3Counts(observer, before[0], before[1] + 1, before[2] + 1);
            reporter.publishEntry("B3-T5 negative control", "PASS observation of FAIL B property:"
                    + " existing session does not automatically dispatch to its still-alive owner;"
                    + " owner TID=" + calls[0].tid + ", external/destructor TID=" + destructor);
            exit.countDown();
            calls[0].finish();
            assertB3Counts(observer, before[0], before[1] + 1, before[2] + 1);
        } catch (Exception | Error problem) {
            primary = problem;
            throw problem;
        } finally {
            exit.countDown();
            finishB3(primary, reporter, "B3-T5", fixture, owners, calls, watching);
        }
    }

    @Test
    void b3T6RejectedGuestCallsDoNotPreventRestrictedSessionCleanup(TestReporter reporter)
            throws Exception {
        Path root = Files.createTempDirectory(work, "b3-rejected-").toRealPath();
        ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized());
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[1];
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch request = new CountDownLatch(1);
        AtomicReference<Object> handle = new AtomicReference<>();
        Throwable primary = null;
        ProtosExternalForeignProviderTest.Fixture watching = null;
        try {
            watching = fixture(authorized());
            Probe owner = owners[0] = probe(fixture, fixture.root);
            Probe observer = probe(watching, watching.newActorActivation());
            prepareB3(owner);
            long[] before = b3Counts(observer);
            reportB3Counts(reporter, "B3-T6 initial", before, observer);
            calls[0] = startB3Owner(root, owner, () -> {
                assertEquals(kernelTid(), number(owner.call("threadId")));
                handle.set(owner.call("createOwned"));
                owner.refuses("incrementOwned", "invalid");
                owner.refuses("releaseOwned", "invalid");
                assertEquals(before[0] + 1, number(owner.call("liveCount")));
                assertEquals(before[1], number(owner.call("destroyedCount")));
                assertTrue(owner.session.isOpenForRuntime());
                assertEquals(41, number(owner.call("incrementOwned", handle.get())));
                ready.countDown();
                awaitB3(request, "post-rejection close request");
                closeB3Owner(owner);
            });
            awaitB3(ready, "rejected calls and retained custody");
            assertTrue(calls[0].thread.isAlive());
            assertTrue(calls[0].installed);
            assertB3Counts(observer, before[0] + 1, before[1], before[2]);
            request.countDown();
            calls[0].finish();
            reportB3Counts(reporter, "B3-T6", before, observer);
            assertB3Counts(observer, before[0], before[1] + 1, before[2]);
            assertEquals(calls[0].tid, number(observer.call("lastDestroyThreadId")));
            assertFalse(owner.session.isOpenForRuntime());
            owner.refuses("incrementOwned", handle.get());
            owner.refuses("releaseOwned", handle.get());
            assertB3Counts(observer, before[0], before[1] + 1, before[2]);
        } catch (Exception | Error problem) {
            primary = problem;
            throw problem;
        } finally {
            request.countDown();
            finishB3(primary, reporter, "B3-T6", fixture, owners, calls, watching);
        }
    }

    // Shared only by B3-T2..T6; all signatures and native operations remain in Protos.
    private static void prepareB3(Probe owner) throws Exception {
        preloadProviderClasses(owner);
        owner.call("threadId");
        // Exercise the ordinary Error path before restricting the carrier. No owned acquisition.
        owner.refuses("incrementOwned", "invalid");
        owner.refuses("releaseOwned", "invalid");
    }

    private static void awaitB3(CountDownLatch latch, String message) throws Exception {
        assertTrue(latch.await(20, TimeUnit.SECONDS), message + " timed out");
    }

    private static void closeB3Owner(Probe owner) {
        owner.session.closeForRuntime();
        assertFalse(owner.session.isOpenForRuntime());
        owner.session.closeForRuntime();
    }

    private static PlatformCall startB3Owner(Path root, Probe owner, Experiment experiment)
            throws Exception {
        return startPlatform(root, () -> {
            Throwable primary = null;
            try { experiment.run(); }
            catch (Exception | Error problem) {
                primary = problem;
                throw problem;
            } finally {
                if (owner.session.isOpenForRuntime()) {
                    try { owner.session.closeForRuntime(); }
                    catch (Exception | Error cleanup) {
                        if (primary != null) primary.addSuppressed(cleanup);
                        else throw cleanup;
                    }
                }
            }
        });
    }

    /** [live, destroyed, cross-thread destroyed, last destructor Linux TID]. */
    private static long[] b3Counts(Probe observer) {
        return new long[] {number(observer.call("liveCount")),
                number(observer.call("destroyedCount")),
                number(observer.call("crossThreadDestroyCount")),
                number(observer.call("lastDestroyThreadId"))};
    }

    private static void assertB3Counts(Probe observer, long live, long destroyed, long crossThread) {
        long[] counts = b3Counts(observer);
        assertEquals(live, counts[0], "live resources");
        assertEquals(destroyed, counts[1], "exactly-once destruction");
        assertEquals(crossThread, counts[2], "creator/destructor thread mismatches");
    }

    private static void reportB3Counts(TestReporter reporter, String name, long[] before, Probe observer) {
        reporter.publishEntry(name, "[live, destroyed, cross-thread destroyed, last destructor TID]: "
                + java.util.Arrays.toString(before) + " -> " + java.util.Arrays.toString(b3Counts(observer)));
    }

    private static void finishB3(Throwable primary, TestReporter reporter, String name,
            ProtosExternalForeignProviderTest.Fixture fixture, Probe[] owners, PlatformCall[] calls,
            ProtosExternalForeignProviderTest.Fixture... observers) {
        Throwable failure = null;
        boolean stopped = true;
        boolean started = false;
        for (PlatformCall call : calls) {
            if (call == null) continue;
            started = true;
            try { call.finish(); }
            catch (Throwable cleanup) {
                if (failure == null) failure = cleanup;
                else failure.addSuppressed(cleanup);
            }
            stopped &= !call.thread.isAlive();
            reporter.publishEntry(name + " lifecycle TID " + call.tid,
                    "Landlock=" + call.installed + ", alive=" + call.thread.isAlive()
                            + ", failure=" + call.failure);
        }
        boolean closed = true;
        for (int i = 0; i < owners.length; i++) {
            Probe owner = owners[i];
            if (owner != null) {
                boolean open = owner.session.isOpenForRuntime();
                closed &= !open;
                reporter.publishEntry(name + " session " + i, "open=" + open);
            }
        }
        if (!started || (stopped && closed)) {
            try { closeB3Fixture(fixture); }
            catch (Throwable cleanup) {
                if (failure == null) failure = cleanup;
                else failure.addSuppressed(cleanup);
            }
        } else {
            AssertionError pending = new AssertionError(
                    "pending owner/session: unrestricted fixture teardown withheld");
            if (failure == null) failure = pending;
            else failure.addSuppressed(pending);
        }
        for (ProtosExternalForeignProviderTest.Fixture observer : observers) {
            if (observer == null) continue;
            try { closeB3Fixture(observer); }
            catch (Throwable cleanup) {
                if (failure == null) failure = cleanup;
                else failure.addSuppressed(cleanup);
            }
        }
        if (failure != null) {
            if (primary != null) primary.addSuppressed(failure);
            else throw new AssertionError(name + " cleanup failed", failure);
        }
    }

    private static void closeB3Fixture(ProtosExternalForeignProviderTest.Fixture fixture)
            throws Exception {
        // Called only after restricted custody has been retired (or for an authority-only observer).
        PlatformCall termination = startPlatform(null,
                () -> fixture.bootstrap.process().requestTerminationForRuntime());
        termination.finish();
        awaitCondition(() -> fixture.host.activeProcessContextCountForTesting() == 0,
                "Process Context did not complete terminal cleanup; host close withheld");
        // Host.close also exposes any recorded Context-close failure. No retry after failure.
        fixture.close();
    }

    // ---- PLAT074-B4: Java coordinates; every SQLite operation is guest Protos.

    @Test
    void b4T1RealSqlitePersistsThroughConfinedFfmSession(TestReporter reporter) throws Exception {
        Path root = Files.createTempDirectory(work, "b4-persist-").toRealPath();
        Path outside = Files.createTempDirectory(work, "b4-outside-").toRealPath();
        Path db = root.resolve("persistent.db");
        Path exterior = outside.resolve("exterior.db");
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[1];
        Throwable primary = null;
        try {
            Probe observer = sqliteProbe(fixture, fixture.root, reporter);
            seedSqlite(observer, exterior, 7);
            Probe owner = owners[0] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            assertFalse(Files.exists(db), "persistent database must not be opened during warmup");
            calls[0] = startB4Owner(root, owner, reporter, () -> {
                assertTrue(kernelTid() > 0);
                assertEquals(0, number(owner.call("openFile", db.toString())));
                owner.call("execute", "CREATE TABLE t(value INTEGER)");
                owner.call("insertInteger", ProtosNumericValueSupport.integer(9223372036854775806L));
                assertEquals(9223372036854775806L,
                        number(owner.call("queryInteger", "SELECT value FROM t")));
                owner.call("beginImmediate");
                owner.call("insertInteger", 42);
                owner.call("rollback");
                assertEquals(1, number(owner.call("queryInteger", "SELECT count(*) FROM t")));
                owner.call("beginImmediate");
                owner.call("insertInteger", 43);
                owner.call("commit");
                assertEquals(1, number(owner.call("autocommit")));
                assertEquals(0, number(owner.call("close")));
                assertEquals(0, number(owner.call("openFile", db.toString())));
                assertEquals(2, number(owner.call("queryInteger", "SELECT count(*) FROM t")));
                assertEquals(43, number(owner.call("queryInteger", "SELECT min(value) FROM t")));
                owner.call("close");
                rejectSqliteOpen(owner, exterior, reporter);
                reporter.publishEntry("B4-T1 transactions", "i64 copied; ROLLBACK absent; COMMIT persisted after reopen");
            });
            calls[0].finish();
            assertEquals(0, number(observer.call("openReadOnly", exterior.toString())));
            assertEquals(7, number(observer.call("queryInteger", "SELECT value FROM t")));
            observer.call("close");
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally { finishB3(primary, reporter, "B4-T1", fixture, owners, calls); }
    }

    @Test
    void b4T2RealSqliteWalAndShmRemainInsideGrant(TestReporter reporter) throws Exception {
        Path root = Files.createTempDirectory(work, "b4-wal-").toRealPath();
        Path db = root.resolve("wal.db");
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[2];
        PlatformCall[] calls = new PlatformCall[2];
        CountDownLatch created = new CountDownLatch(1), readers = new CountDownLatch(1);
        CountDownLatch committed = new CountDownLatch(1), observed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Throwable primary = null;
        try {
            Probe first = owners[0] = sqliteProbe(fixture, fixture.root, reporter);
            Probe second = owners[1] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            assertNotSame(first.session, second.session);
            calls[0] = startB4Owner(root, first, reporter, () -> {
                assertEquals(0, number(first.call("openFile", db.toString())));
                first.call("execute", "PRAGMA journal_mode=WAL");
                assertWal(first, reporter);
                first.call("execute", "CREATE TABLE t(value INTEGER)");
                first.call("insertInteger", 1);
                created.countDown();
                awaitB3(readers, "second WAL connection");
                first.call("beginImmediate");
                first.call("insertInteger", 2);
                first.call("commit");
                committed.countDown();
                awaitB3(release, "WAL observation completed");
                first.call("close");
            });
            calls[1] = startB4Owner(root, second, reporter, () -> {
                awaitB3(created, "WAL schema");
                assertEquals(0, number(second.call("openFile", db.toString())));
                assertWal(second, reporter);
                assertEquals(1, number(second.call("queryInteger", "SELECT count(*) FROM t")));
                readers.countDown();
                awaitB3(committed, "WAL COMMIT");
                assertEquals(2, number(second.call("queryInteger", "SELECT count(*) FROM t")));
                observed.countDown();
                awaitB3(release, "live WAL sidecars observed");
                second.call("close");
            });
            awaitB3(observed, "WAL visibility");
            assertTrue(Files.exists(Path.of(db + "-wal")));
            assertTrue(Files.exists(Path.of(db + "-shm")));
            assertNotEquals(calls[0].tid, calls[1].tid);
            reporter.publishEntry("B4-T2", "WAL and SHM present with two live connections; COMMIT visible");
            release.countDown();
            calls[0].finish();
            calls[1].finish();
            Probe reopened = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            PlatformCall verification = startB4Owner(root, reopened, reporter, () -> {
                assertEquals(0, number(reopened.call("openFile", db.toString())));
                assertEquals(2, number(reopened.call("queryInteger", "SELECT count(*) FROM t")));
                reopened.call("close");
            });
            // Keep this owner in the same teardown accounting, including a failed join.
            owners[0] = reopened;
            calls[0] = verification;
            verification.finish();
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally {
            release.countDown();
            finishB3(primary, reporter, "B4-T2", fixture, owners, calls);
        }
    }

    @Test
    void b4T3RealSqliteAttachAndVacuumCannotEscapeLandlock(TestReporter reporter) throws Exception {
        Path root = Files.createTempDirectory(work, "b4-attach-").toRealPath();
        Path outside = Files.createTempDirectory(work, "b4-attach-outside-'").toRealPath();
        Path exterior = outside.resolve("source's.db"), output = outside.resolve("vacuum's.db");
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[1];
        Throwable primary = null;
        try {
            Probe observer = sqliteProbe(fixture, fixture.root, reporter);
            seedSqlite(observer, exterior, 71);
            // Positive controls exercise exactly these SQL routes without Landlock.
            assertEquals(0, number(observer.call("openMemory")));
            observer.call("execute", "ATTACH DATABASE " + sqlPath(exterior) + " AS outside_db");
            observer.call("execute", "INSERT INTO outside_db.t VALUES (72)");
            observer.call("execute", "DETACH DATABASE outside_db");
            observer.call("execute", "VACUUM INTO " + sqlPath(output));
            observer.call("close");
            assertTrue(Files.size(output) > 0);
            Files.delete(output);
            byte[] before = Files.readAllBytes(exterior);
            List<String> names = directoryNames(outside);
            Probe owner = owners[0] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            calls[0] = startB4Owner(root, owner, reporter, () -> {
                assertEquals(0, number(owner.call("openFile", root.resolve("inside.db").toString())));
                owner.call("execute", "CREATE TABLE t(value INTEGER)");
                long attach = number(owner.call("executeStatus",
                        "ATTACH DATABASE " + sqlPath(exterior) + " AS outside_db"));
                assertEquals(14, attach, "SQLITE_CANTOPEN");
                assertEquals(0, number(owner.call("queryInteger",
                        "SELECT count(*) FROM pragma_database_list WHERE name='outside_db'")));
                long vacuum = number(owner.call("executeStatus", "VACUUM INTO " + sqlPath(output)));
                assertEquals(14, vacuum, "SQLITE_CANTOPEN");
                owner.call("insertInteger", 73);
                assertEquals(73, number(owner.call("queryInteger", "SELECT value FROM t")));
                owner.call("close");
                reporter.publishEntry("B4-T3 native codes", "ATTACH=" + attach + ", VACUUM INTO=" + vacuum);
            });
            calls[0].finish();
            assertFalse(Files.exists(output));
            assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(exterior)));
            assertEquals(names, directoryNames(outside));
            reporter.publishEntry("B4-T3 observation", "exterior final bytes/names unchanged; no syscall trace or transient-effect exclusion");
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally { finishB3(primary, reporter, "B4-T3", fixture, owners, calls); }
    }

    @Test
    void b4T4RealSqliteBusyAndTransactionRecoveryUnderConfinement(TestReporter reporter) throws Exception {
        Path root = Files.createTempDirectory(work, "b4-busy-").toRealPath();
        Path db = root.resolve("busy.db");
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[2];
        PlatformCall[] calls = new PlatformCall[2];
        CountDownLatch locked = new CountDownLatch(1), busy = new CountDownLatch(1);
        CountDownLatch committed = new CountDownLatch(1);
        Throwable primary = null;
        try {
            Probe first = owners[0] = sqliteProbe(fixture, fixture.root, reporter);
            Probe second = owners[1] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            calls[0] = startB4Owner(root, first, reporter, () -> {
                assertEquals(0, number(first.call("openFile", db.toString())));
                first.call("execute", "CREATE TABLE t(value INTEGER)");
                first.call("beginImmediate");
                first.call("insertInteger", 1);
                assertEquals(0, number(first.call("autocommit")));
                locked.countDown();
                awaitB3(busy, "second writer SQLITE_BUSY");
                first.call("commit");
                committed.countDown();
                first.call("close");
            });
            calls[1] = startB4Owner(root, second, reporter, () -> {
                awaitB3(locked, "first writer transaction");
                assertEquals(0, number(second.call("openFile", db.toString())));
                assertEquals(0, number(second.call("setBusyTimeout", 100)));
                long status = number(second.call("executeStatus", "INSERT INTO t VALUES (2)"));
                assertEquals(5, status, "SQLITE_BUSY must remain native data");
                assertEquals(1, number(second.call("autocommit")));
                reporter.publishEntry("B4-T4 native result", "SQLITE_BUSY=" + status + ", autocommit=1, busy_timeout=100ms");
                busy.countDown();
                awaitB3(committed, "first COMMIT");
                assertEquals(1, number(second.call("queryInteger", "SELECT value FROM t")));
                second.call("insertInteger", 2);
                assertEquals(2, number(second.call("queryInteger", "SELECT count(*) FROM t")));
                second.call("close");
            });
            calls[0].finish();
            calls[1].finish();
            assertNotSame(first.session, second.session);
            assertNotEquals(calls[0].tid, calls[1].tid);
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally { finishB3(primary, reporter, "B4-T4", fixture, owners, calls); }
    }

    @Test
    void b4T5RealSqliteIndependentGrantsRejectCrossAccess(TestReporter reporter) throws Exception {
        Path[] roots = {Files.createTempDirectory(work, "b4-grant-a-").toRealPath(),
                Files.createTempDirectory(work, "b4-grant-b-").toRealPath()};
        Path[] db = {roots[0].resolve("a.db"), roots[1].resolve("b.db")};
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[2];
        PlatformCall[] calls = new PlatformCall[2];
        CountDownLatch ready = new CountDownLatch(2), release = new CountDownLatch(1);
        Throwable primary = null;
        try {
            Probe observer = sqliteProbe(fixture, fixture.root, reporter);
            seedSqlite(observer, db[0], 10);
            seedSqlite(observer, db[1], 20);
            for (int i = 0; i < 2; i++) {
                int index = i;
                Probe owner = owners[i] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
                calls[i] = startB4Owner(roots[i], owner, reporter, () -> {
                    assertEquals(0, number(owner.call("openFile", db[index].toString())));
                    owner.call("insertInteger", index + 30);
                    assertEquals(2, number(owner.call("queryInteger", "SELECT count(*) FROM t")));
                    owner.call("close");
                    rejectSqliteOpen(owner, db[1 - index], reporter);
                    ready.countDown();
                    awaitB3(release, "simultaneous independent grants");
                });
            }
            awaitB3(ready, "both grants exercised");
            assertNotEquals(calls[0].tid, calls[1].tid);
            assertNotSame(owners[0].session, owners[1].session);
            assertNotSame(owners[0].declarations, owners[1].declarations);
            release.countDown();
            calls[0].finish();
            calls[1].finish();
            for (int i = 0; i < 2; i++) {
                assertEquals(0, number(observer.call("openFile", db[i].toString())));
                assertEquals(40 + 11 * i,
                        number(observer.call("queryInteger", "SELECT sum(value) FROM t")));
                observer.call("close");
            }
            reporter.publishEntry("B4-T5", "distinct Actor sessions/TIDs; no native handle export; cross RO/RW=SQLITE_CANTOPEN");
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally {
            release.countDown();
            finishB3(primary, reporter, "B4-T5", fixture, owners, calls);
        }
    }

    @Test
    void b4T6RealSqliteCloseRequestPreservesInFlightNativeCall(TestReporter reporter) throws Exception {
        Path root = Files.createTempDirectory(work, "b4-close-active-").toRealPath();
        Path db = root.resolve("active.db");
        int downcallLine = sqliteDowncallLine();
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[2];
        PlatformCall[] calls = new PlatformCall[2];
        CountDownLatch locked = new CountDownLatch(1), stepping = new CountDownLatch(1);
        CountDownLatch request = new CountDownLatch(1), completed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicLong requestedAt = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicLong returnedAt = new java.util.concurrent.atomic.AtomicLong();
        Throwable primary = null;
        try {
            Probe blocker = owners[0] = sqliteProbe(fixture, fixture.root, reporter);
            Probe owner = owners[1] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            calls[0] = startB4Owner(root, blocker, reporter, () -> {
                assertEquals(0, number(blocker.call("openFile", db.toString())));
                blocker.call("execute", "CREATE TABLE t(value INTEGER)");
                blocker.call("beginImmediate");
                blocker.call("insertInteger", 1);
                locked.countDown();
                awaitB3(release, "busy call returned and owner closed");
                blocker.call("commit");
                blocker.call("close");
                assertEquals(0, number(blocker.call("openFile", db.toString())));
                assertEquals(1, number(blocker.call("queryInteger", "SELECT count(*) FROM t")));
                blocker.call("close");
            });
            calls[1] = startB4Owner(root, owner, reporter, () -> {
                awaitB3(locked, "SQLite blocking transaction");
                assertEquals(0, number(owner.call("openFile", db.toString())));
                assertEquals(0, number(owner.call("setBusyTimeout", 2000)));
                owner.call("prepareContendedInsert");
                stepping.countDown();
                long status = number(owner.call("stepPreparedStatus"));
                returnedAt.set(System.nanoTime());
                assertEquals(5, status, "real finite native busy timeout must return SQLITE_BUSY");
                assertEquals(0, request.getCount(), "request must arrive before downcall returns");
                assertTrue(requestedAt.get() > 0 && requestedAt.get() < returnedAt.get());
                assertEquals(2, sqliteCustody(owner, "ownedCountForDiagnostics"),
                        "connection and completed statement are still owned");
                owner.call("finalizePending"); // BUSY may also be the finalize result: never retry.
                assertEquals(1, number(owner.call("autocommit")));
                assertEquals(1, sqliteCustody(owner, "ownedCountForDiagnostics"),
                        "only connection may remain after finalization");
                reporter.publishEntry("B4-T6 native completion", "SQLITE_BUSY=" + status
                        + ", request=" + requestedAt.get() + ", return=" + returnedAt.get()
                        + ", autocommit=1; statement finalized before owner session close");
                // startB4Owner retires guest state and closes custody on this thread.
                completed.countDown();
            });
            awaitB3(stepping, "SQLite step about to enter");
            awaitCondition(() -> sqliteDowncallActive(calls[1].thread, downcallLine),
                    "no active SQLite downcall observed; request overlap NOT demonstrated");
            assertOnStack(calls[1].thread, "FfmBinding", "invoke");
            requestedAt.set(System.nanoTime());
            request.countDown();
            // Reobserve native entry after recording the request: a prior frame alone is insufficient.
            assertTrue(sqliteDowncallActive(calls[1].thread, downcallLine),
                    "request overlap with native SQLite step was not demonstrated");
            assertEquals(0, returnedAt.get(), "request must precede completed native call");
            assertTrue(owner.session.isOpenForRuntime());
            reporter.publishEntry("B4-T6 request", "recorded at FfmBinding.invoke downcall line "
                    + downcallLine + "; request=" + requestedAt.get()
                    + "; stack=" + java.util.Arrays.toString(calls[1].thread.getStackTrace())
                    + "; no exterior close or interrupt");
            awaitB3(completed, "SQLite normal return");
            calls[1].finish();
            assertFalse(owner.session.isOpenForRuntime());
            release.countDown();
            calls[0].finish();
            assertNotEquals(calls[0].tid, calls[1].tid);
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally {
            release.countDown();
            finishB3(primary, reporter, "B4-T6", fixture, owners, calls);
        }
    }

    @Test
    void b4T7PublicStdSqliteMemoryOperationsRemainCompatibleWithLandlock(TestReporter reporter)
            throws Exception {
        Path root = Files.createTempDirectory(work, "b4-public-memory-").toRealPath();
        Path standardDirectory = Path.of("protos", "lib", "sqlite").toRealPath();
        assertTrue(Files.isDirectory(standardDirectory));
        try (var entries = Files.newDirectoryStream(standardDirectory)) {
            assertTrue(entries.iterator().hasNext(), "standard directory accessible before restriction");
        }
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[1];
        Throwable primary = null;
        try {
            Probe owner = owners[0] = sqliteProbe(fixture, fixture.root, reporter);
            calls[0] = startB4Owner(root, owner, reporter, () -> {
                assertThrows(java.nio.file.AccessDeniedException.class, () -> {
                    try (var entries = Files.newDirectoryStream(standardDirectory)) {
                        entries.iterator().hasNext();
                    }
                });
                for (int attempt = 0; attempt < 3; attempt++) {
                    long[] copied = numbers(owner.call("publicMemory"), 3);
                    assertEquals(9223372036854775806L, copied[0]);
                    assertEquals(41, copied[1]);
                    assertEquals(9223372036854775806L, copied[2]);
                    assertEquals(0, sqliteCustody(owner, "ownedCountForDiagnostics"));
                    assertEquals(0, sqliteCustody(owner, "uncertainCountForDiagnostics"));
                }
                reporter.publishEntry("B4-T7", "std:sqlite openMemory/create/bind/step/copy/reset/terminal Future close completed");
            });
            calls[0].finish();
            assertEquals(List.of(), directoryNames(root));
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally { finishB3(primary, reporter, "B4-T7", fixture, owners, calls); }
    }

    @Test
    void b4T8AuthorizedSqliteNativeAccessIsNotImplicitlyConfined(TestReporter reporter) throws Exception {
        var unauthorized = new ProtosExternalForeignProviderTest.Fixture(ProtosPolyglotRuntimeHost.open(), true);
        try {
            unauthorized.eval("import(\"std:ffi\")\nimport(\"std:sqlite\")");
            assertSame(null, unauthorized.host.foreignProviderLoaderForTesting());
            assertEquals(ProtosExecutionOutcome.State.FAILED,
                    unauthorized.run("import(\"ffi:sqlite3\")").state());
            assertEquals(ProtosExecutionOutcome.State.FAILED,
                    unauthorized.run("import(\"std:sqlite\").openMemory()").state());
        } finally { closeB3Fixture(unauthorized); }
        Path root = Files.createTempDirectory(work, "b4-authority-").toRealPath();
        Path outside = Files.createTempDirectory(work, "b4-authority-outside-").toRealPath();
        Path db = outside.resolve("exterior.db");
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[2];
        PlatformCall[] calls = new PlatformCall[2];
        CountDownLatch denied = new CountDownLatch(1);
        Throwable primary = null;
        try {
            Probe seed = sqliteProbe(fixture, fixture.root, reporter);
            seedSqlite(seed, db, 81);
            Probe restricted = owners[0] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            Probe unrestricted = owners[1] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            calls[0] = startB4Owner(root, restricted, reporter, () -> {
                rejectSqliteOpen(restricted, db, reporter);
                denied.countDown();
            });
            calls[1] = startB4Owner(null, unrestricted, reporter, () -> {
                awaitB3(denied, "restricted SQLite denial");
                assertEquals(0, number(unrestricted.call("openFile", db.toString())));
                unrestricted.call("insertInteger", 82);
                assertEquals(163, number(unrestricted.call("queryInteger", "SELECT sum(value) FROM t")));
                unrestricted.call("close");
            });
            calls[0].finish();
            calls[1].finish();
            assertNotEquals(calls[0].tid, calls[1].tid);
            reporter.publishEntry("B4-T8 NEGATIVE CONTROL", "authorized unrestricted Actor accessed exterior DB; provider does NOT impose Filesystem confinement");
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally { finishB3(primary, reporter, "B4-T8", fixture, owners, calls); }
    }

    private static ProtosExternalForeignProviderTest.Fixture sqliteFixture() throws Exception {
        return new ProtosExternalForeignProviderTest.Fixture(
                ProtosPolyglotRuntimeHost.openWithForeignProviders(
                        ProtosForeignProviderConfiguration.trustedInProcess(List.of(jar, sqliteJar))
                                .withProviderOptions(PROVIDER_ID, authorized())), true);
    }

    @Test
    void b4T9PublicSqliteAbiPreparationRemainsActorLocal(TestReporter reporter) throws Exception {
        Path firstRoot = Files.createTempDirectory(work, "b4-public-first-").toRealPath();
        Path secondRoot = Files.createTempDirectory(work, "b4-public-second-").toRealPath();
        Path standardDirectory = Path.of("protos", "lib", "sqlite").toRealPath();
        try (var entries = Files.newDirectoryStream(standardDirectory)) {
            assertTrue(entries.iterator().hasNext());
        }
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[2];
        PlatformCall[] calls = new PlatformCall[2];
        Throwable primary = null;
        try {
            Probe first = owners[0] = sqliteProbe(fixture, fixture.root, reporter);
            Probe second = owners[1] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            Object firstModule = first.call("publicModule");
            Object secondModule = second.call("publicModule");
            assertSame(firstModule, first.call("publicModule"));
            assertSame(secondModule, second.call("publicModule"));
            assertNotSame(firstModule, secondModule);
            assertNotSame(first.session, second.session);
            assertNotSame(first.session.sessionForRuntime(), second.session.sessionForRuntime());
            Path[] roots = {firstRoot, secondRoot};
            for (int i = 0; i < owners.length; i++) {
                Probe owner = owners[i];
                calls[i] = startB4Owner(roots[i], owner, reporter, () -> {
                    assertThrows(java.nio.file.AccessDeniedException.class, () -> {
                        try (var entries = Files.newDirectoryStream(standardDirectory)) {
                            entries.iterator().hasNext();
                        }
                    });
                    long[] copied = numbers(owner.call("publicMemory"), 3);
                    assertEquals(9223372036854775806L, copied[0]);
                    assertEquals(41, copied[1]);
                    assertEquals(9223372036854775806L, copied[2]);
                    assertEquals(0, sqliteCustody(owner, "ownedCountForDiagnostics"));
                    assertEquals(0, sqliteCustody(owner, "uncertainCountForDiagnostics"));
                });
            }
            for (PlatformCall call : calls) call.finish();
            assertTrue(calls[0].installed);
            assertTrue(calls[1].installed);
            assertNotEquals(calls[0].tid, calls[1].tid);
            assertEquals(List.of(), directoryNames(firstRoot));
            assertEquals(List.of(), directoryNames(secondRoot));
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally { finishB3(primary, reporter, "B4-T9", fixture, owners, calls); }
    }

    @Test
    void b4T10CachedSqliteAbiCannotReviveClosedProviderSession(TestReporter reporter) throws Exception {
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[1];
        Throwable primary = null;
        try {
            Probe owner = owners[0] = sqliteProbe(fixture, fixture.root, reporter);
            // sqliteProbe has already completed publicMemory, including every terminal close.
            Object backend = ((ProtosForeignPluginProvider.Session)
                    owner.session.sessionForRuntime()).plugin;
            int sessionCount = fixture.context.foreignProviderSessionCountForTesting();
            int compartmentCount = fixture.context.foreignProviderCompartmentCountForTesting();
            calls[0] = startB3Owner(null, owner, () -> {
                assertEquals(0, sqliteCustody(backend, "ownedCountForDiagnostics"));
                assertEquals(0, sqliteCustody(backend, "uncertainCountForDiagnostics"));
                owner.session.closeForRuntime();
                assertFalse(owner.session.isOpenForRuntime());
                owner.refuses("publicMemory");
                assertFalse(owner.session.isOpenForRuntime());
                assertThrows(IllegalStateException.class, () -> fixture.context.foreignSessionForRuntime(
                        fixture.actorOf(owner.actor), new ProtosForeignProviderId("protos-ffm")));
                assertEquals(sessionCount, fixture.context.foreignProviderSessionCountForTesting());
                assertEquals(compartmentCount, fixture.context.foreignProviderCompartmentCountForTesting());
                assertEquals(0, sqliteCustody(backend, "ownedCountForDiagnostics"));
                assertEquals(0, sqliteCustody(backend, "uncertainCountForDiagnostics"));
                owner.session.closeForRuntime();
            });
            calls[0].finish();
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally { finishB3(primary, reporter, "B4-T10", fixture, owners, calls); }
    }

    // ---- PLAT074-B5: independent JVMs; SQL and owned resources stay in Protos.

    @Test
    void b5T1TwoIndependentOsProcessesReopenCommittedSqlite() throws Exception {
        runB5(1);
    }

    @Test
    void b5T2WalFullCommittedTransactionSurvivesSigkill() throws Exception {
        runB5(2);
    }

    @Test
    void b5T3UncommittedTransactionRollsBackAfterSigkill() throws Exception {
        runB5(3);
    }

    @Test
    void b5T4IndependentOsProcessesRespectSqliteWriteLocks() throws Exception {
        runB5(4);
    }

    @Test
    void b5T5WalRecoveryWithLiveReaderAndInterruptedWriter() throws Exception {
        runB5(5);
    }

    @Test
    void b5T6RollbackJournalRecoversAfterKilledWriter() throws Exception {
        runB5(6);
    }

    @Test
    void b5T7ProcessBoundaryDoesNotImplicitlyExtendLandlockGrant() throws Exception {
        runB5(7);
    }

    private static void b5Event(String event) {
        System.out.println(event);
        System.out.flush();
        assertFalse(System.out.checkError(), "subprocess event pipe failed");
    }

    /** Test launcher only. No fixture compilation, SQL bridge or native call is added here. */
    public static void main(String[] args) throws Exception {
        try {
            assertEquals(8, args.length, "role, grant, database, two provider JARs, C fixture, exterior, parent PID");
            assertTrue(List.of("confined", "observer").contains(args[0]), "unknown child role");
            Path root = Path.of(args[1]);
            Path db = Path.of(args[2]);
            jar = Path.of(args[3]);
            sqliteJar = Path.of(args[4]);
            library = Path.of(args[5]);
            Path exterior = Path.of(args[6]);
            for (Path path : List.of(root, db, jar, sqliteJar, library, exterior)) {
                assertTrue(path.isAbsolute(), "child paths must be absolute: " + path);
            }
            assertNotEquals(Long.parseLong(args[7]), ProcessHandle.current().pid());
            System.err.println("B5 child PID=" + ProcessHandle.current().pid() + ", role=" + args[0]);
            for (Path path : List.of(jar, sqliteJar, library)) {
                assertTrue(Files.isRegularFile(path), "missing compiled fixture dependency: " + path);
            }
            prepareRestrictedErrorClasses();
            TestReporter reporter = entries -> System.err.println(entries);
            var fixture = sqliteFixture();
            try {
                Probe owner = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
                // Warm B5-specific scalar, checkpoint and transfer/error paths using memory only.
                assertEquals(0, number(owner.call("openMemory")));
                owner.call("execute", "CREATE TABLE t(value INTEGER)");
                b5Insert(owner, 10);
                b5Rows(owner, 1, 10, 10);
                owner.call("integrityOk");
                owner.call("quickCheckOk");
                owner.call("checkpoint");
                Object warmHandle = owner.call("ownedConnectionForTransferTest");
                assertThrows(ProtosSignalException.class,
                        () -> ProtosActorValueTransfer.snapshotValue(warmHandle, owner.actor));
                fixture.standardError("NonTransferableValue");
                owner.call("close");
                // Resolve command transport and synchronization before restricting the owner.
                var commands = new java.util.concurrent.ArrayBlockingQueue<String>(16);
                commands.offer("warmup");
                assertEquals("warmup", commands.poll(1, TimeUnit.SECONDS));
                var input = new java.io.BufferedReader(
                        new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8));
                Thread.ofPlatform().daemon().name("b5-stdin").start(() -> {
                    try {
                        String line;
                        while ((line = input.readLine()) != null) {
                            if (line.length() > 64 || !commands.offer(line, 5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("invalid or overflowing command pipe");
                            }
                        }
                        commands.offer("EOF");
                    } catch (Exception failure) {
                        System.err.println("B5 stdin: " + failure);
                        commands.offer("EOF");
                    }
                });
                boolean confined = args[0].equals("confined");
                b5Event("READY");
                PlatformCall call = startB4Owner(confined ? root : null, owner, reporter, () -> {
                    long tid = kernelTid();
                    // startPlatform invokes this body only after restrictCurrentThreadBeneath.
                    if (confined) b5Event("CONFINED");
                    assertSame(false, owner.call("isOpen"));
                    if (confined) rejectSqliteOpen(owner, exterior, reporter);
                    assertEquals(0, number(owner.call("openFile", db.toString())));
                    System.err.println("B5 persistent open after restriction=" + confined + ", TID=" + tid);
                    b5Event("READY");
                    while (true) {
                        String command = commands.poll(40, TimeUnit.SECONDS);
                        assertTrue(command != null, "child command timeout");
                        switch (command) {
                            case "INIT_WAL", "INIT_DELETE" -> {
                                long[] settings = numbers(owner.call(command.equals("INIT_WAL")
                                        ? "setWalFull" : "setRollbackJournal"), 4);
                                assertEquals(1, settings[1]);
                                assertEquals(2, settings[3]);
                                owner.call("execute", "CREATE TABLE t(value INTEGER)");
                                b5Insert(owner, 10);
                                b5Event("COMMITTED");
                            }
                            case "COMMIT" -> {
                                b5Insert(owner, 20);
                                b5Event("COMMITTED");
                            }
                            case "LOCK", "UNCOMMITTED", "SPILL" -> {
                                if (command.equals("SPILL")) {
                                    owner.call("setRollbackJournal");
                                    owner.call("execute", "PRAGMA cache_size=5");
                                } else owner.call("setWalFull");
                                owner.call("beginImmediate");
                                owner.call("insertInteger", 20);
                                if (command.equals("SPILL")) {
                                    // Exceed the small page cache so recovery exercises a hot journal.
                                    owner.call("execute", "UPDATE t SET value=99");
                                    owner.call("execute", "WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL "
                                            + "SELECT x+1 FROM n WHERE x<12000) INSERT INTO t SELECT x FROM n");
                                }
                                assertEquals(0, number(owner.call("autocommit")));
                                b5Event(command.equals("LOCK") ? "TRANSACTION_STARTED" : "UNCOMMITTED");
                            }
                            case "RELEASE" -> {
                                assertEquals(0, number(owner.call("autocommit")));
                                owner.call("commit");
                                assertEquals(1, number(owner.call("autocommit")));
                                b5Event("RELEASED");
                            }
                            case "BUSY" -> {
                                assertEquals(0, number(owner.call("setBusyTimeout", 100)));
                                assertEquals(5, number(owner.call("executeStatus", "INSERT INTO t VALUES (30)")));
                                assertEquals(1, number(owner.call("autocommit")));
                                b5Rows(owner, 1, 10, 10);
                                b5Event("BUSY");
                            }
                            case "RETRY" -> {
                                b5Insert(owner, 30);
                                b5Event("COMMITTED");
                            }
                            case "READ" -> {
                                owner.call("execute", "BEGIN");
                                b5Rows(owner, 1, 10, 10);
                                assertEquals(0, number(owner.call("autocommit")));
                                b5Event("TRANSACTION_STARTED");
                            }
                            case "SNAPSHOT" -> {
                                b5Rows(owner, 1, 10, 10);
                                assertEquals(0, number(owner.call("autocommit")));
                                b5Event("READY");
                            }
                            case "VERIFY_INITIAL", "RECOVER_WRITE" -> {
                                b5Rows(owner, 1, 10, 10);
                                assertEquals(1, number(owner.call("integrityOk")));
                                if (command.equals("RECOVER_WRITE")) b5Insert(owner, 30);
                                b5Event("READY");
                            }
                            case "VERIFY_TWO", "VERIFY_THREE", "VERIFY_RECOVERED", "CHECKPOINT" -> {
                                if (command.equals("VERIFY_THREE")) b5Rows(owner, 3, 30, 60);
                                else if (command.equals("VERIFY_RECOVERED")) b5Rows(owner, 2, 30, 40);
                                else b5Rows(owner, 2, 20, 30);
                                assertEquals(1, number(owner.call("integrityOk")));
                                if (command.equals("CHECKPOINT")) {
                                    assertEquals(1, number(owner.call("quickCheckOk")));
                                    long[] checkpoint = numbers(owner.call("checkpoint"), 3);
                                    assertTrue(checkpoint[0] == 0 || checkpoint[0] == 1,
                                            "unexpected checkpoint status " + checkpoint[0]);
                                    System.err.println("B5 checkpoint [busy, log, checkpointed]="
                                            + java.util.Arrays.toString(checkpoint));
                                }
                                b5Event("READY");
                            }
                            case "TRANSFER" -> {
                                // Guest Actor transfer rejects this live SQLite owned reference before
                                // it could be serialized to a different Process; no address IPC exists.
                                Object handle = owner.call("ownedConnectionForTransferTest");
                                ProtosSignalException failure = assertThrows(ProtosSignalException.class,
                                        () -> ProtosActorValueTransfer.snapshotValue(handle, owner.actor));
                                assertSame(fixture.standardError("NonTransferableValue"),
                                        failure.error().parent().orElseThrow());
                                b5Event("READY");
                            }
                            case "CLOSE" -> {
                                if (number(owner.call("autocommit")) == 0) owner.call("rollback");
                                assertEquals(tid, kernelTid(), "close left physical owner thread");
                                owner.call("close");
                                assertEquals(0, sqliteCustody(owner, "ownedCountForDiagnostics"));
                                assertEquals(0, sqliteCustody(owner, "uncertainCountForDiagnostics"));
                                return;
                            }
                            default -> throw new AssertionError("unknown/closed command pipe: " + command);
                        }
                    }
                });
                call.thread.join(TimeUnit.SECONDS.toMillis(90));
                assertFalse(call.thread.isAlive(), "child owner timeout");
                if (call.failure != null) throw new AssertionError("child owner failed", call.failure);
                assertEquals(confined, call.installed);
                assertFalse(owner.session.isOpenForRuntime());
            } finally {
                closeB3Fixture(fixture);
            }
            b5Event("CLOSED");
        } catch (Throwable failure) {
            b5Event("FAILED");
            failure.printStackTrace(System.err);
            throw new AssertionError("B5 subprocess failed", failure);
        }
    }

    private static void b5Insert(Probe owner, int value) {
        owner.call("beginImmediate");
        owner.call("insertInteger", value);
        assertEquals(101, number(owner.call("commit")));
        assertEquals(1, number(owner.call("autocommit")));
    }

    private static void b5Rows(Probe owner, long count, long maximum, long sum) {
        assertEquals(count, number(owner.call("committedCount")));
        assertEquals(maximum, number(owner.call("committedValue")));
        assertEquals(sum, number(owner.call("queryInteger", "SELECT sum(value) FROM t")));
        assertEquals(1, number(owner.call("queryInteger", "SELECT count(*) FROM t WHERE value=10")));
        assertEquals(maximum == 20 || count == 3 ? 1 : 0,
                number(owner.call("queryInteger", "SELECT count(*) FROM t WHERE value=20")));
        assertEquals(maximum == 30 ? 1 : 0,
                number(owner.call("queryInteger", "SELECT count(*) FROM t WHERE value=30")));
    }

    private static Process b5Start(Path root, Path db, Path exterior, boolean confined,
            Map<Process, java.util.concurrent.BlockingQueue<String>> events,
            Map<Process, StringBuilder> diagnostics) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        assertTrue(classpath != null && !classpath.isBlank(), "missing test subprocess classpath");
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        assertTrue(Files.isExecutable(java), "missing child JVM: " + java);
        List<String> invocation = new ArrayList<>(List.of(java.toString(),
                "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"));
        String cache = System.getProperty("polyglot.engine.userResourceCache");
        if (cache != null) invocation.add("-Dpolyglot.engine.userResourceCache=" + cache);
        invocation.addAll(List.of("-cp", classpath, ProtosI144FfmFixtureAbiTest.class.getName(),
                confined ? "confined" : "observer", root.toString(), db.toString(),
                jar.toAbsolutePath().toString(), sqliteJar.toAbsolutePath().toString(),
                library.toAbsolutePath().toString(), exterior.toString(),
                Long.toString(ProcessHandle.current().pid())));
        Process child = new ProcessBuilder(invocation).redirectErrorStream(true).start();
        var queue = new java.util.concurrent.ArrayBlockingQueue<String>(64);
        StringBuilder log = new StringBuilder();
        events.put(child, queue);
        diagnostics.put(child, log);
        // One bounded drainer consumes both pipes. Diagnostic truncation never blocks a child.
        Thread.ofPlatform().daemon().name("b5-output-" + child.pid()).start(() -> {
            try (var output = new java.io.InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8)) {
                StringBuilder line = new StringBuilder();
                int next;
                boolean oversized = false;
                while ((next = output.read()) >= 0) {
                    if (next == '\n') {
                        String complete = line.toString();
                        synchronized (log) {
                            if (log.length() > 32768) log.delete(0, 16384);
                            log.append(complete).append('\n');
                        }
                        if (!oversized && List.of("READY", "CONFINED", "TRANSACTION_STARTED", "COMMITTED",
                                "UNCOMMITTED", "BUSY", "RELEASED", "CLOSED", "FAILED").contains(complete)) {
                            if (!queue.offer(complete)) queue.offer("FAILED");
                        }
                        line.setLength(0);
                        oversized = false;
                    } else if (line.length() < 4096) line.append((char) next);
                    else oversized = true;
                }
                queue.offer("EOF");
            } catch (Exception failure) {
                synchronized (log) { log.append(failure); }
                queue.offer("FAILED");
            }
        });
        assertTrue(child.pid() > 0);
        assertNotEquals(ProcessHandle.current().pid(), child.pid());
        assertEquals(events.size(), events.keySet().stream().map(Process::pid).distinct().count(),
                "each role requires a distinct OS process PID");
        b5Await(child, "READY", events, diagnostics);
        if (confined) b5Await(child, "CONFINED", events, diagnostics);
        b5Await(child, "READY", events, diagnostics);
        return child;
    }

    private static void b5Await(Process child, String expected,
            Map<Process, java.util.concurrent.BlockingQueue<String>> events,
            Map<Process, StringBuilder> diagnostics) throws Exception {
        String actual = events.get(child).poll(45, TimeUnit.SECONDS);
        String diagnostic;
        synchronized (diagnostics.get(child)) { diagnostic = diagnostics.get(child).toString(); }
        assertEquals(expected, actual, "PID=" + child.pid() + ", alive=" + child.isAlive() + "\n" + diagnostic);
    }

    private static void b5Command(Process child, String command) throws Exception {
        child.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8));
        child.getOutputStream().flush();
    }

    private static void b5NormalClose(Process child,
            Map<Process, java.util.concurrent.BlockingQueue<String>> events,
            Map<Process, StringBuilder> diagnostics) throws Exception {
        b5Command(child, "CLOSE");
        b5Await(child, "CLOSED", events, diagnostics);
        assertTrue(child.waitFor(15, TimeUnit.SECONDS), "normal child termination timeout");
        assertEquals(0, child.exitValue(), "PID=" + child.pid() + "\n" + diagnostics.get(child));
        b5Await(child, "EOF", events, diagnostics);
    }

    private static void b5Kill(Process child,
            Map<Process, java.util.concurrent.BlockingQueue<String>> events,
            Map<Process, StringBuilder> diagnostics) throws Exception {
        assertTrue(child.isAlive(), "writer died before SIGKILL");
        assertTrue(child.supportsNormalTermination(), "Linux process termination contract required");
        child.destroyForcibly();
        assertTrue(child.waitFor(15, TimeUnit.SECONDS), "SIGKILL termination timeout");
        assertEquals(137, child.exitValue(), "Linux SIGKILL must not be a normal SQLite close");
        assertFalse(child.isAlive());
        // Drain through EOF: FAILED or CLOSED before the kill must never be hidden by exit 137.
        b5Await(child, "EOF", events, diagnostics);
    }

    private static void runB5(int scenario) throws Exception {
        // Only the established platform precondition can skip. Child failures are assertions.
        ProtosLinuxConfinedFilesystemTestSupport.assumeConfinementPrimitives();
        Path root = Files.createTempDirectory(work, "b5-grant-").toRealPath();
        Path outside = Files.createTempDirectory(work, "b5-exterior-").toRealPath();
        Path exterior = outside.resolve("outside.db");
        Path db = root.resolve("persistent.db");
        Map<Process, java.util.concurrent.BlockingQueue<String>> events = new java.util.LinkedHashMap<>();
        Map<Process, StringBuilder> diagnostics = new java.util.LinkedHashMap<>();
        Throwable primary = null;
        try {
            // A separate, confined fixture control seeds the exterior with its own grant.
            Process control = b5Start(outside, exterior, db, true, events, diagnostics);
            b5Command(control, "INIT_DELETE");
            b5Await(control, "COMMITTED", events, diagnostics);
            b5NormalClose(control, events, diagnostics);
            Process a = b5Start(root, db, exterior, true, events, diagnostics);
            b5Command(a, scenario == 6 ? "INIT_DELETE" : "INIT_WAL");
            b5Await(a, "COMMITTED", events, diagnostics);
            switch (scenario) {
                case 1 -> {
                    b5NormalClose(a, events, diagnostics);
                    Process b = b5Start(root, db, exterior, true, events, diagnostics);
                    b5Command(b, "VERIFY_INITIAL");
                    b5Await(b, "READY", events, diagnostics);
                    b5NormalClose(b, events, diagnostics);
                }
                case 2 -> {
                    b5Command(a, "COMMIT");
                    b5Await(a, "COMMITTED", events, diagnostics);
                    assertTrue(Files.size(Path.of(db + "-wal")) > 0, "committed WAL must remain live");
                    assertTrue(Files.isRegularFile(Path.of(db + "-shm")), "live WAL index missing");
                    b5Kill(a, events, diagnostics);
                    Process b = b5Start(root, db, exterior, true, events, diagnostics);
                    b5Command(b, "VERIFY_TWO");
                    b5Await(b, "READY", events, diagnostics);
                    b5NormalClose(b, events, diagnostics);
                }
                case 3, 6 -> {
                    // Initial committed state is in a different, normally closed JVM.
                    b5NormalClose(a, events, diagnostics);
                    Process writer = b5Start(root, db, exterior, true, events, diagnostics);
                    b5Command(writer, scenario == 6 ? "SPILL" : "UNCOMMITTED");
                    b5Await(writer, "UNCOMMITTED", events, diagnostics);
                    if (scenario == 6) assertTrue(Files.size(Path.of(db + "-journal")) > 512,
                            "rollback recovery requires a populated journal before the kill");
                    b5Kill(writer, events, diagnostics);
                    Process b = b5Start(root, db, exterior, true, events, diagnostics);
                    b5Command(b, "RECOVER_WRITE");
                    b5Await(b, "READY", events, diagnostics);
                    b5NormalClose(b, events, diagnostics);
                    Process c = b5Start(root, db, exterior, true, events, diagnostics);
                    b5Command(c, "VERIFY_RECOVERED");
                    b5Await(c, "READY", events, diagnostics);
                    b5NormalClose(c, events, diagnostics);
                }
                case 4 -> {
                    b5Command(a, "LOCK");
                    b5Await(a, "TRANSACTION_STARTED", events, diagnostics);
                    Process b = b5Start(root, db, exterior, true, events, diagnostics);
                    b5Command(b, "BUSY");
                    b5Await(b, "BUSY", events, diagnostics);
                    b5Command(a, "RELEASE");
                    b5Await(a, "RELEASED", events, diagnostics);
                    b5Command(b, "RETRY");
                    b5Await(b, "COMMITTED", events, diagnostics);
                    b5NormalClose(a, events, diagnostics);
                    b5NormalClose(b, events, diagnostics);
                    Process c = b5Start(root, db, exterior, true, events, diagnostics);
                    b5Command(c, "VERIFY_THREE");
                    b5Await(c, "READY", events, diagnostics);
                    b5NormalClose(c, events, diagnostics);
                }
                case 5 -> {
                    Process b = b5Start(root, db, exterior, true, events, diagnostics);
                    b5Command(b, "READ");
                    b5Await(b, "TRANSACTION_STARTED", events, diagnostics);
                    b5Command(a, "COMMIT");
                    b5Await(a, "COMMITTED", events, diagnostics);
                    b5Command(b, "SNAPSHOT");
                    b5Await(b, "READY", events, diagnostics);
                    b5Kill(a, events, diagnostics);
                    b5NormalClose(b, events, diagnostics);
                    Process c = b5Start(root, db, exterior, true, events, diagnostics);
                    b5Command(c, "CHECKPOINT");
                    b5Await(c, "READY", events, diagnostics);
                    b5NormalClose(c, events, diagnostics);
                }
                case 7 -> {
                    Process b = b5Start(outside, exterior, exterior, false, events, diagnostics);
                    for (Process child : List.of(a, b)) {
                        b5Command(child, "TRANSFER");
                        b5Await(child, "READY", events, diagnostics);
                    }
                    b5Command(b, "VERIFY_INITIAL");
                    b5Await(b, "READY", events, diagnostics);
                    b5NormalClose(a, events, diagnostics);
                    b5NormalClose(b, events, diagnostics);
                }
                default -> throw new AssertionError("unknown B5 scenario " + scenario);
            }
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            // Kill every survivor before waiting for any one of them. Preserve the first failure.
            for (Process child : events.keySet()) if (child.isAlive()) child.destroyForcibly();
            AssertionError cleanup = null;
            boolean interrupted = Thread.interrupted();
            for (Process child : events.keySet()) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                boolean terminated = false;
                while (!terminated && System.nanoTime() < deadline) {
                    try {
                        terminated = child.waitFor(Math.max(1, deadline - System.nanoTime()),
                                TimeUnit.NANOSECONDS);
                    } catch (InterruptedException failure) {
                        // Finish reaping every child before restoring the caller's interrupt.
                        interrupted = true;
                    }
                }
                if (!terminated) {
                    if (cleanup == null) cleanup = new AssertionError("B5 child retained after cleanup");
                    cleanup.addSuppressed(new AssertionError("PID=" + child.pid()));
                }
                synchronized (diagnostics.get(child)) {
                    System.err.println("B5 PID=" + child.pid() + " diagnostics:\n" + diagnostics.get(child));
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            if (cleanup != null) {
                if (primary != null) primary.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }

    private static Probe sqliteProbe(ProtosExternalForeignProviderTest.Fixture fixture,
            ProtosActivation actor, TestReporter reporter) throws Exception {
        ProtosObjectValue module = (ProtosObjectValue) fixture.eval(actor,
                Files.readString(CASES.resolve("sqlite-landlock-provider.protos")));
        Probe preparation = new Probe(fixture, actor, module, null);
        ProtosObjectValue operations = (ProtosObjectValue) preparation.call("prepare");
        ProtosForeignProviderSessionBinding session = fixture.context.foreignSessionForRuntime(
                fixture.actorOf(actor), new ProtosForeignProviderId("protos-ffm"));
        assertEquals("protos-ffm", session.providerId().value());
        Probe owner = new Probe(fixture, actor, operations, session);
        preloadProviderClasses(owner);
        long version = number(owner.call("version")), threadsafe = number(owner.call("threadsafe"));
        assertTrue(version >= 3016000, "WAL/table-valued PRAGMA require a modern SQLite");
        assertTrue(threadsafe > 0, "two Actor sessions require thread-safe SQLite build");
        reporter.publishEntry("B4 SQLite preparation", "version=" + version + ", threadsafe=" + threadsafe);
        assertEquals(0, number(owner.call("openMemory")));
        owner.call("execute", "CREATE TABLE t(value INTEGER)");
        owner.call("insertInteger", 41);
        assertEquals(41, number(owner.call("queryInteger", "SELECT value FROM t")));
        owner.call("beginImmediate");
        owner.call("rollback");
        owner.call("beginImmediate");
        owner.call("commit");
        owner.call("setBusyTimeout", 0);
        owner.call("autocommit");
        owner.call("errorCode");
        owner.call("close");
        // A successful memory run never exercises exception interception/epilogues.
        // Exercise the diagnostic wrapper and exceptional ensure before restriction too.
        assertSame(true, preparation.call("warmPublicErrorPath"));
        // Cross the real root-task failure boundary too: caught guest errors do
        // not invoke terminal diagnostic trace projection (Collector/Origin).
        ProtosExecutionOutcome terminalError = preparation.outcome("warmPublicTerminalError");
        assertEquals(ProtosExecutionOutcome.State.FAILED, terminalError.state());
        assertOrdinary(fixture, terminalError.error());
        owner.call("publicMemory"); // Warm the exact exported closure used by T7.
        assertEquals(0, sqliteCustody(owner, "ownedCountForDiagnostics"));
        assertEquals(0, sqliteCustody(owner, "uncertainCountForDiagnostics"));
        return owner;
    }

    private static PlatformCall startB4Owner(Path root, Probe owner, TestReporter reporter,
            Experiment experiment) throws Exception {
        return startB3Owner(root, owner, () -> {
            Throwable primary = null;
            try {
                reporter.publishEntry("B4 owner", "physical Linux TID=" + kernelTid()
                        + ", restricted=" + (root != null));
                experiment.run();
            } catch (Exception | Error problem) {
                primary = problem;
                throw problem;
            } finally {
                try {
                    owner.call("closeForSession");
                    assertSame(false, owner.call("isOpen"));
                    Object backend = ((ProtosForeignPluginProvider.Session)
                            owner.session.sessionForRuntime()).plugin;
                    owner.session.closeForRuntime();
                    assertFalse(owner.session.isOpenForRuntime());
                    assertEquals(0, sqliteCustody(backend, "ownedCountForDiagnostics"));
                    assertEquals(0, sqliteCustody(backend, "uncertainCountForDiagnostics"));
                    reporter.publishEntry("B4 owner close", "TID=" + kernelTid()
                            + ", session closed; owned=0, uncertain=0");
                } catch (Exception | Error cleanup) {
                    // Match B3: cleanup must not replace the original experiment failure,
                    // including a fatal guest entry that already closed its Context.
                    if (primary != null) primary.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
        });
    }

    /** Read-only observation of the backend's existing diagnostic counters. */
    private static int sqliteCustody(Probe owner, String name) throws Exception {
        ProtosForeignPluginProvider.Session bridge =
                (ProtosForeignPluginProvider.Session) owner.session.sessionForRuntime();
        return sqliteCustody(bridge.plugin, name);
    }

    private static int sqliteCustody(Object backend, String name) throws Exception {
        var method = backend.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (int) method.invoke(backend);
    }

    private static int sqliteDowncallLine() throws Exception {
        // buildFixture compiles this very source. Observe its actual downcall site,
        // without requiring hidden JDK stub frames to appear in Thread.getStackTrace.
        List<String> lines = Files.readAllLines(Path.of("providers", "ffm", "src", "main",
                "java", "com", "guillermomolina", "protos", "ffm", "FfmBinding.java"));
        String marker = "Object raw = (Object) spread.invokeExact(carriers);";
        int line = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).strip().equals(marker)) {
                assertEquals(-1, line, "ambiguous provider downcall site");
                line = i + 1;
            }
        }
        assertTrue(line > 0, "provider downcall site not found; overlap cannot be witnessed");
        return line;
    }

    private static boolean sqliteDowncallActive(Thread thread, int downcallLine) {
        for (StackTraceElement frame : thread.getStackTrace()) {
            if (frame.getClassName().equals("com.guillermomolina.protos.ffm.FfmBinding")
                    && frame.getMethodName().equals("invoke")
                    && frame.getLineNumber() == downcallLine) return true;
        }
        return false;
    }

    private static void seedSqlite(Probe observer, Path db, int value) {
        assertEquals(0, number(observer.call("openFile", db.toString())));
        observer.call("execute", "CREATE TABLE t(value INTEGER)");
        observer.call("insertInteger", value);
        observer.call("close");
    }

    private static void rejectSqliteOpen(Probe owner, Path exterior, TestReporter reporter) {
        for (String mode : List.of("openReadOnly", "openFile")) {
            long status = number(owner.call(mode, exterior.toString()));
            assertEquals(14, status, "SQLITE_CANTOPEN expected for " + mode);
            assertSame(false, owner.call("isOpen"));
            reporter.publishEntry("B4 exterior denial", mode + " native status=" + status);
        }
    }

    private static void assertWal(Probe owner, TestReporter reporter) {
        assertEquals(1, number(owner.call("queryInteger",
                "SELECT count(*) FROM pragma_journal_mode WHERE journal_mode='wal'")));
        reporter.publishEntry("B4 journal mode", "wal (queried, not inferred from PRAGMA success)");
    }

    private static String sqlPath(Path path) {
        // Only harness-created paths reach SQL; this is not a public SQL executor.
        return "'" + path.toString().replace("'", "''") + "'";
    }

    private static List<String> directoryNames(Path directory) throws Exception {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static void preloadProviderClasses(Probe owner) throws Exception {
        ProtosForeignPluginProvider.Session bridge =
                (ProtosForeignPluginProvider.Session) owner.session.sessionForRuntime();
        ClassLoader loader = bridge.plugin.getClass().getClassLoader();
        try (java.util.jar.JarFile prepared = new java.util.jar.JarFile(jar.toFile())) {
            for (java.util.jar.JarEntry entry : java.util.Collections.list(prepared.entries())) {
                if (entry.getName().endsWith(".class")) {
                    Class.forName(entry.getName().replace('/', '.').replaceFirst("\\.class$", ""),
                            true, loader);
                }
            }
        }
    }

    // ---- B2 test-only host observations; all fixture ABI declarations stay in Protos.

    private static Probe probe(
            ProtosExternalForeignProviderTest.Fixture fixture, ProtosActivation actor)
            throws Exception {
        ProtosObjectValue declarations = (ProtosObjectValue) phases(fixture, actor).call("prepare");
        ProtosForeignModuleFacadeValue libraryFacade = (ProtosForeignModuleFacadeValue)
                declarations.readLocalSlot("library").orElseThrow();
        ProtosForeignProviderSessionBinding session =
                libraryFacade.attachmentForRuntime().orElseThrow().session();
        assertRealSession(session);
        return new Probe(fixture, actor, declarations, session);
    }

    /** Authority-free source preparation; phase calls deliberately retain no cached session. */
    private static Probe phases(
            ProtosExternalForeignProviderTest.Fixture fixture, ProtosActivation actor)
            throws Exception {
        ProtosObjectValue declarations = (ProtosObjectValue) fixture.eval(actor,
                Files.readString(CASES.resolve("ffm-confinement-probe.protos")));
        return new Probe(fixture, actor, declarations, null);
    }

    private record Probe(
            ProtosExternalForeignProviderTest.Fixture fixture,
            ProtosActivation actor,
            ProtosObjectValue declarations,
            ProtosForeignProviderSessionBinding session) {
        private ProtosExecutionOutcome outcome(String name, Object... supplied) {
            ProtosClosureValue closure = (ProtosClosureValue)
                    declarations.readLocalSlot(name).orElseThrow();
            List<Object> arguments = new ArrayList<>();
            for (Object value : supplied) {
                arguments.add(value instanceof String text ? new ProtosStringValue(text)
                        : value instanceof Integer integer ? ProtosNumericValueSupport.integer(integer)
                        : value);
            }
            // Reuse the existing entered Context and canonical source-backed root-task entry.
            // No raw CallTarget, replacement invocation engine or production hook is added.
            return fixture.context.callForRuntime(
                    () -> ProtosRootTaskExecution.executeClosure(closure, arguments, actor));
        }

        Object call(String name, Object... arguments) {
            ProtosExecutionOutcome result = outcome(name, arguments);
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, result.state(),
                    () -> name + " failed: " + describe(fixture, result.error()));
            return result.value();
        }

        void refuses(String name, Object... arguments) {
            ProtosExecutionOutcome result = outcome(name, arguments);
            assertEquals(ProtosExecutionOutcome.State.FAILED, result.state(), name);
            assertOrdinary(fixture, result.error());
        }

        long[] open(Path path, int mode) {
            return numbers(call("open", path.toString(), mode), 3);
        }
    }

    private static void assertRealSession(ProtosForeignProviderSessionBinding binding) {
        assertEquals(PROVIDER_ID, binding.providerId().value());
        ProtosForeignPluginProvider.Session bridge =
                (ProtosForeignPluginProvider.Session) binding.sessionForRuntime();
        assertEquals("com.guillermomolina.protos.ffm.FfmSession",
                bridge.plugin.getClass().getName(), "must exercise the real dynamically loaded backend");
    }

    private static long number(Object value) {
        return Long.parseLong(ProtosNumericValueSupport.integerDecimalText(value));
    }

    private static long[] numbers(Object value, int count) {
        ProtosArrayValue array = (ProtosArrayValue) value;
        assertEquals(count, array.indexedSize());
        long[] result = new long[count];
        for (int i = 0; i < count; i++) result[i] = number(array.indexedAtNormalized(i));
        return result;
    }

    private static void assertOpen(long[] observation, long status, long errno) {
        assertEquals(status, observation[0], java.util.Arrays.toString(observation));
        assertEquals(errno, observation[1], java.util.Arrays.toString(observation));
        assertTrue(observation[2] > 0, "C must report a real Linux TID");
    }

    /** Independent host diagnostic only; no fixture operation uses this direct JDK downcall. */
    private static final class KernelTid {
        static final MethodHandle GETTID = Linker.nativeLinker().downcallHandle(
                Linker.nativeLinker().defaultLookup().find("gettid").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
    }

    private static long kernelTid() {
        try { return (int) KernelTid.GETTID.invokeExact(); }
        catch (Throwable failure) { throw new AssertionError("host Linux gettid diagnostic failed", failure); }
    }

    @FunctionalInterface
    private interface Experiment { void run() throws Exception; }

    private static PlatformCall startPlatform(Path root, Experiment experiment) throws Exception {
        kernelTid(); // Resolve the independent diagnostic before restricting any thread.
        int rootFd;
        if (root == null) rootFd = -1;
        else {
            ProtosLinuxConfinedFilesystemTestSupport.assumeConfinementPrimitives();
            ProtosLinuxConfinedFilesystemNative.preloadForRestrictedThread();
            rootFd = ProtosLinuxConfinedFilesystemNative.openRootDirectory(root.toString());
        }
        PlatformCall call = new PlatformCall(root != null);
        try {
            call.thread = Thread.ofPlatform().daemon().name("b2-ffm-observation").start(() -> {
                try {
                    call.tid = kernelTid();
                    if (rootFd >= 0) {
                        ProtosLinuxConfinedFilesystemLandlock.restrictCurrentThreadBeneath(rootFd);
                        call.installed = true;
                    }
                    experiment.run();
                } catch (Throwable failure) { call.failure = failure; }
                finally {
                    if (rootFd >= 0) {
                        try { ProtosLinuxConfinedFilesystemNative.close(rootFd); }
                        catch (Throwable cleanup) {
                            if (call.failure == null) call.failure = cleanup;
                            else call.failure.addSuppressed(cleanup);
                        }
                    }
                }
            });
        } catch (Throwable failure) {
            if (rootFd >= 0) ProtosLinuxConfinedFilesystemNative.close(rootFd);
            throw failure;
        }
        return call;
    }

    private static final class PlatformCall {
        final boolean restricted;
        Thread thread;
        volatile long tid;
        volatile boolean installed;
        volatile Throwable failure;

        PlatformCall(boolean restricted) { this.restricted = restricted; }

        void finish() throws Exception {
            thread.join(TimeUnit.SECONDS.toMillis(20));
            assertFalse(thread.isAlive(), "retained native thread; never forcibly interrupt C");
            if (restricted && !installed
                    && failure instanceof ProtosLinuxConfinedFilesystemNative.NativeException) {
                assumeTrue(false, "UNSUPPORTED: Landlock installation refused: " + failure);
            }
            if (failure != null) throw new AssertionError("physical FFM experiment failed", failure);
            if (restricted) assertTrue(installed);
        }
    }

    private static void awaitGate(Probe observer, Path path, PlatformCall active) {
        awaitCondition(() -> {
            if (!active.thread.isAlive()) throw new AssertionError(
                    "downcall ended before native barrier", active.failure);
            return observer.open(path, 3)[0] == 1;
        }, "real native barrier not reached (no simulated admission)");
    }

    private static boolean hasFrame(Thread thread, String type, String method) {
        for (StackTraceElement frame : thread.getStackTrace()) {
            if (frame.getClassName().equals("com.guillermomolina.protos.ffm." + type)
                    && frame.getMethodName().equals(method)) return true;
        }
        return false;
    }

    private static void assertOnStack(Thread thread, String type, String method) {
        assertTrue(hasFrame(thread, type, method), "missing real provider frame " + type + "." + method);
    }

    private static void awaitCondition(java.util.function.BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) throw new AssertionError(message);
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    // ---- existing ABI helpers

    private static Map<String, String> authorized() {
        return Map.of("ffi_test.path", library.toAbsolutePath().toString());
    }

    private static ProtosExternalForeignProviderTest.Fixture fixture(Map<String, String> options)
            throws Exception {
        return new ProtosExternalForeignProviderTest.Fixture(
                ProtosPolyglotRuntimeHost.openWithForeignProviders(
                        ProtosForeignProviderConfiguration.trustedInProcess(List.of(jar))
                                .withProviderOptions(PROVIDER_ID, options)),
                true);
    }

    /** Reads the fixture's destructor counter through another Actor's own session. */
    private static int destroyedCount(
            ProtosExternalForeignProviderTest.Fixture fixture, ProtosActivation observer) {
        return nativeCount(fixture, observer, "ffi_test_destroyed_count");
    }

    /** Reads one {@code int32_t f(void)} fixture counter through the observer Actor's session. */
    private static int nativeCount(
            ProtosExternalForeignProviderTest.Fixture fixture,
            ProtosActivation observer,
            String symbol) {
        Object count =
                fixture.eval(
                        observer,
                        IMPORT
                                + "ffi.bind(\"" + symbol + "\","
                                + " ffi.signature(ffi.i32))()");
        return Integer.parseInt(
                com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(
                        count));
    }

    /** Invokes a held value directly (receiver = the value) and answers its signalled Error. */
    private static ProtosObjectValue invoke(
            ProtosExternalForeignProviderTest.Fixture fixture, Object callable, Object... args) {
        List<Object> arguments = new ArrayList<>();
        for (Object argument : args) {
            arguments.add(
                    argument instanceof Integer number
                            ? com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integer(
                                    number)
                            : argument);
        }
        com.guillermomolina.protos.runtime.ProtosSignalException failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.guillermomolina.protos.runtime.ProtosSignalException.class,
                        () -> ProtosInvocation.invoke(callable, arguments, fixture.root));
        return failure.error();
    }

    /** The ordinary pre-entry Error: exactly Error, never ForeignError. */
    private static void assertOrdinary(
            ProtosExternalForeignProviderTest.Fixture fixture, ProtosObjectValue error) {
        assertSame(fixture.standardError("Error"), error.parent().orElseThrow(),
                () -> describe(fixture, error));
    }

    /** Readable description of a failure: its standard parent and its local slots. */
    private static String describe(
            ProtosExternalForeignProviderTest.Fixture fixture, ProtosObjectValue error) {
        if (error == null) {
            return "null";
        }
        Object parent = error.parent().orElse(null);
        String kind = "other";
        for (String name :
                List.of("Error", "ForeignError", "SlotNotFound", "TypeError", "ArgumentError")) {
            if (fixture.prelude.bindings().readLocalSlot(name).orElse(null) == parent) {
                kind = name;
            }
        }
        StringBuilder text = new StringBuilder(kind).append(" {");
        for (Map.Entry<String, Object> slot : error.localSlotsSnapshot().entrySet()) {
            Object value = slot.getValue();
            String shown;
            if (value instanceof com.guillermomolina.protos.runtime.ProtosStringValue string) {
                shown = '"' + string.value() + '"';
            } else {
                try {
                    shown = com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                            .integerDecimalText(value);
                } catch (RuntimeException notInteger) {
                    shown = value == null ? "null" : value.getClass().getSimpleName();
                }
            }
            text.append(' ').append(slot.getKey()).append('=').append(shown);
        }
        return text.append(" }").toString();
    }

    private static Path findCompiler() {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String name : List.of("cc", "gcc", "clang")) {
            for (String directory : path.split(File.pathSeparator)) {
                Path candidate = Path.of(directory, name);
                if (Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }
}

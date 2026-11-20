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

    // ---- PLAT074-B6: authority boundaries; negative controls do not prove confinement.

    @Test
    void b6T1PreopenedSqliteConnectionRetainsExteriorReadAccess(TestReporter reporter) throws Exception {
        Path root = Files.createTempDirectory(work, "b6-t1-").toRealPath();
        Path granted = root.resolve("granted.db");
        Path exterior = work.resolve("b6-t1-exterior.db").toAbsolutePath();
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[1];
        boolean[] installed = new boolean[1];
        Throwable primary = null;
        try {
            Probe observer = sqliteProbe(fixture, fixture.root, reporter);
            seedSqlite(observer, granted, 71);
            seedSqlite(observer, exterior, 71);
            ProtosLinuxConfinedFilesystemTestSupport.assumeConfinementPrimitives();
            ProtosLinuxConfinedFilesystemNative.preloadForRestrictedThread();
            byte[] before = Files.readAllBytes(exterior);
            Probe owner = owners[0] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            prepareB6(owner);
            calls[0] = startB4Owner(null, owner, reporter, () -> {
                // The SAME physical owner acquires before restriction and closes after it.
                int rootFd = ProtosLinuxConfinedFilesystemNative.openRootDirectory(root.toString());
                Throwable nativeFailure = null;
                try {
                    long beforeTid = kernelTid();
                    assertEquals(0, number(owner.call("openReadOnly", exterior.toString())));
                    ProtosLinuxConfinedFilesystemLandlock.restrictCurrentThreadBeneath(rootFd);
                    installed[0] = true;
                    assertEquals(beforeTid, kernelTid());
                    reporter.publishEntry("B6-T1 installed grant", root + "; restricted TID=" + kernelTid());
                    assertEquals(71, number(owner.call("nativeReadMarker")));
                    owner.call("close");
                    assertEquals(14, number(owner.call("openReadOnly", exterior.toString())));
                    assertSame(false, owner.call("isOpen"));
                    assertEquals(0, sqliteCustody(owner, "ownedCountForDiagnostics"));
                    assertEquals(0, sqliteCustody(owner, "uncertainCountForDiagnostics"));
                    reporter.publishEntry("B6-T1 NEGATIVE CONTROL",
                            "preopened connection retained exterior read authority; new open denied");
                } catch (Exception | Error problem) { nativeFailure = problem; throw problem; }
                finally {
                    try { ProtosLinuxConfinedFilesystemNative.close(rootFd); }
                    catch (Exception | Error cleanup) {
                        if (nativeFailure != null) nativeFailure.addSuppressed(cleanup);
                        else throw cleanup;
                    }
                }
            });
            calls[0].finish();
            assertTrue(installed[0], "Landlock installed after acquisition on the owner");
            assertTrue(calls[0].tid > 0);
            assertFalse(owner.session.isOpenForRuntime());
            assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(exterior)), "exterior bytes unchanged");
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally {
            finishB3(primary, reporter, "B6-T1", fixture, owners, calls);
        }
    }

    @Test
    void b6T2UnrestrictedHostEntryCanBypassRestrictedCarrier(TestReporter reporter) throws Exception {
        Path root = Files.createTempDirectory(work, "b6-t2-").toRealPath();
        Path granted = root.resolve("granted.db");
        Path exterior = work.resolve("b6-t2-exterior.db").toAbsolutePath();
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[2];
        CountDownLatch ready = new CountDownLatch(1), release = new CountDownLatch(1);
        var segments = new java.util.concurrent.atomic.AtomicInteger();
        Throwable primary = null;
        try {
            Probe observer = sqliteProbe(fixture, fixture.root, reporter);
            seedSqlite(observer, granted, 72);
            seedSqlite(observer, exterior, 72);
            byte[] before = Files.readAllBytes(exterior);
            Probe owner = owners[0] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            prepareB6(owner);
            calls[0] = startPlatform(root, () -> {
                Throwable ownerFailure = null;
                try {
                    assertTrue(segments.compareAndSet(0, 1), "only one guest segment");
                    try {
                        assertEquals(0, number(owner.call("openReadOnly", granted.toString())));
                        assertEquals(72, number(owner.call("readOnlyProbe")));
                        owner.call("close");
                    } finally { segments.decrementAndGet(); }
                    ready.countDown();
                    // No guest entry while the unrestricted host owns the sequential segment.
                    awaitB3(release, "unrestricted same-Actor host segment completed");
                    assertEquals(0, segments.get(), "host segment ended before owner teardown");
                } catch (Exception | Error problem) { ownerFailure = problem; throw problem; }
                finally {
                    try {
                        // Seal admission before cleanup, including a timeout. If a host
                        // segment is still active, leave custody intact and fail; never
                        // race its guest work with teardown on either carrier.
                        assertTrue(segments.compareAndSet(0, -1), "active host entry: owner cleanup withheld");
                        owner.call("closeForSession");
                        Object backend = ((ProtosForeignPluginProvider.Session)
                                owner.session.sessionForRuntime()).plugin;
                        owner.session.closeForRuntime();
                        assertFalse(owner.session.isOpenForRuntime());
                        assertEquals(0, sqliteCustody(backend, "ownedCountForDiagnostics"));
                        assertEquals(0, sqliteCustody(backend, "uncertainCountForDiagnostics"));
                        reporter.publishEntry("B6-T2 owner close", "restricted TID=" + kernelTid()
                                + "; session closed; owned=0, uncertain=0");
                    } catch (Exception | Error cleanup) {
                        if (ownerFailure != null) ownerFailure.addSuppressed(cleanup);
                        else throw cleanup;
                    }
                }
            });
            awaitB3(ready, "restricted owner parked outside guest code");
            assertTrue(calls[0].installed);
            assertTrue(calls[0].thread.isAlive());
            calls[1] = startPlatform(null, () -> {
                assertNotEquals(calls[0].tid, kernelTid());
                assertTrue(segments.compareAndSet(0, 1), "no overlapping guest segment or retired owner");
                try {
                    assertTrue(owner.session.isOpenForRuntime());
                    assertEquals(0, number(owner.call("openReadOnly", exterior.toString())));
                    assertEquals(72, number(owner.call("nativeReadMarker")));
                    owner.call("close");
                    reporter.publishEntry("B6-T2 NEGATIVE CONTROL", "same Actor/session; unrestricted host TID="
                            + kernelTid() + "; restricted owner TID=" + calls[0].tid
                            + "; explicit sequential host entry, not scheduler migration");
                } finally { segments.decrementAndGet(); }
            });
            calls[1].finish();
            assertFalse(calls[1].restricted);
            assertFalse(calls[1].installed);
            assertEquals(0, segments.get());
            release.countDown();
            calls[0].finish();
            assertTrue(calls[0].installed);
            assertTrue(calls[0].tid > 0);
            assertFalse(owner.session.isOpenForRuntime());
            assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(exterior)), "exterior bytes unchanged");
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally {
            release.countDown();
            finishB3(primary, reporter, "B6-T2", fixture, owners, calls);
        }
    }

    @Test
    void b6T3SymlinkOutsideGrantCannotBeOpenedBySqlite(TestReporter reporter) throws Exception {
        Path root = Files.createTempDirectory(work, "b6-t3-").toRealPath();
        Path granted = root.resolve("granted.db");
        Path exterior = work.resolve("b6-t3-exterior.db").toAbsolutePath();
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[1];
        Throwable primary = null;
        try {
            Probe observer = sqliteProbe(fixture, fixture.root, reporter);
            seedSqlite(observer, granted, 73);
            seedSqlite(observer, exterior, 73);
            Path link = root.resolve("outside-link.db");
            Files.createSymbolicLink(link, exterior);
            assertTrue(Files.isSymbolicLink(link));
            assertEquals(exterior, Files.readSymbolicLink(link));
            assertEquals(0, number(observer.call("openReadOnly", link.toString())));
            assertEquals(73, number(observer.call("nativeReadMarker")));
            observer.call("close");
            byte[] before = Files.readAllBytes(exterior);
            Probe owner = owners[0] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            prepareB6(owner);
            calls[0] = startB4Owner(root, owner, reporter, () -> {
                assertEquals(0, number(owner.call("openReadOnly", granted.toString())));
                assertEquals(73, number(owner.call("readOnlyProbe")));
                owner.call("close");
                assertEquals(14, number(owner.call("openReadOnly", link.toString())));
                assertSame(false, owner.call("isOpen"));
            });
            calls[0].finish();
            assertTrue(Files.isSymbolicLink(link));
            assertEquals(exterior, Files.readSymbolicLink(link));
            assertTrue(calls[0].installed);
            assertTrue(calls[0].tid > 0);
            assertFalse(owner.session.isOpenForRuntime());
            assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(exterior)), "exterior bytes unchanged");
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally {
            finishB3(primary, reporter, "B6-T3", fixture, owners, calls);
        }
    }

    @Test
    void b6T4PreexistingHardlinkDemonstratesPathNotDatabaseIdentity(TestReporter reporter) throws Exception {
        Path root = Files.createTempDirectory(work, "b6-t4-").toRealPath();
        Path granted = root.resolve("granted.db");
        Path exterior = work.resolve("b6-t4-exterior.db").toAbsolutePath();
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[1];
        Throwable primary = null;
        try {
            Probe observer = sqliteProbe(fixture, fixture.root, reporter);
            seedSqlite(observer, granted, 74);
            seedSqlite(observer, exterior, 74);
            assertEquals(0, number(observer.call("openFile", exterior.toString())));
            observer.call("setRollbackJournal");
            observer.call("close");
            Path link = root.resolve("hardlink.db");
            Files.createLink(link, exterior);
            assertTrue(Files.isSameFile(link, exterior));
            byte[] before = Files.readAllBytes(exterior);
            Probe owner = owners[0] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            prepareB6(owner);
            calls[0] = startB4Owner(root, owner, reporter, () -> {
                assertEquals(14, number(owner.call("openReadOnly", exterior.toString())));
                assertSame(false, owner.call("isOpen"));
                assertEquals(0, number(owner.call("openReadOnly", link.toString())));
                assertEquals(74, number(owner.call("nativeReadMarker")));
                owner.call("close");
                reporter.publishEntry("B6-T4 AUTHORITY SCOPE CONTROL",
                        "preexisting in-grant hardlink reads the same exterior inode; directory grant is not exclusive DB identity");
            });
            calls[0].finish();
            assertTrue(Files.isSameFile(link, exterior));
            assertTrue(calls[0].installed);
            assertTrue(calls[0].tid > 0);
            assertFalse(owner.session.isOpenForRuntime());
            assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(exterior)), "exterior bytes unchanged");
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally {
            finishB3(primary, reporter, "B6-T4", fixture, owners, calls);
        }
    }

    @Test
    void b6T5ExplicitUriModeStillObeysRestrictedFilesystemGrant(TestReporter reporter) throws Exception {
        Path root = Files.createTempDirectory(work, "b6-t5-").toRealPath();
        Path granted = root.resolve("granted.db");
        Path exterior = work.resolve("b6-t5-exterior.db").toAbsolutePath();
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[1];
        Throwable primary = null;
        try {
            Probe observer = sqliteProbe(fixture, fixture.root, reporter);
            seedSqlite(observer, granted, 75);
            seedSqlite(observer, exterior, 75);
            String grantedUri = granted.toUri().toASCIIString();
            String exteriorUri = exterior.toUri().toASCIIString();
            assertEquals(0, number(observer.call("openUriReadOnly", exteriorUri)));
            assertEquals(75, number(observer.call("nativeReadMarker")));
            observer.call("close");
            byte[] before = Files.readAllBytes(exterior);
            Probe owner = owners[0] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            prepareB6(owner);
            calls[0] = startB4Owner(root, owner, reporter, () -> {
                assertEquals(0, number(owner.call("openUriReadOnly", grantedUri)));
                assertEquals(75, number(owner.call("readOnlyProbe")));
                owner.call("close");
                for (String mode : List.of("openUriReadOnly", "openUriFile")) {
                    assertEquals(14, number(owner.call(mode, exteriorUri)), mode);
                    assertSame(false, owner.call("isOpen"));
                    assertEquals(0, sqliteCustody(owner, "ownedCountForDiagnostics"));
                    assertEquals(0, sqliteCustody(owner, "uncertainCountForDiagnostics"));
                }
            });
            calls[0].finish();
            assertTrue(calls[0].installed);
            assertTrue(calls[0].tid > 0);
            assertFalse(owner.session.isOpenForRuntime());
            assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(exterior)), "exterior bytes unchanged");
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally {
            finishB3(primary, reporter, "B6-T5", fixture, owners, calls);
        }
    }

    @Test
    void b6T6UriAttachCannotEscapeRestrictedDirectory(TestReporter reporter) throws Exception {
        Path root = Files.createTempDirectory(work, "b6-t6-").toRealPath();
        Path granted = root.resolve("granted.db");
        Path exterior = work.resolve("b6-t6-exterior.db").toAbsolutePath();
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[1];
        Throwable primary = null;
        try {
            Probe observer = sqliteProbe(fixture, fixture.root, reporter);
            seedSqlite(observer, granted, 76);
            seedSqlite(observer, exterior, 76);
            Path secondary = root.resolve("secondary 'quoted.db");
            seedSqlite(observer, secondary, 176);
            String grantedUri = granted.toUri().toASCIIString();
            String secondarySql = "'" + secondary.toUri().toASCIIString().replace("'", "''") + "'";
            String exteriorSql = "'" + exterior.toUri().toASCIIString().replace("'", "''") + "'";
            byte[] before = Files.readAllBytes(exterior);
            Probe owner = owners[0] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            prepareB6(owner);
            calls[0] = startB4Owner(root, owner, reporter, () -> {
                assertEquals(0, number(owner.call("openUriFile", grantedUri)));
                assertEquals(101, number(owner.call("executeStatus", "ATTACH DATABASE " + secondarySql + " AS permitted")));
                assertEquals(1, number(owner.call("queryInteger",
                        "SELECT count(*) FROM pragma_database_list WHERE name='permitted'")));
                assertEquals(176, number(owner.call("queryInteger", "SELECT value FROM permitted.t LIMIT 1")));
                assertEquals(101, number(owner.call("executeStatus", "DETACH DATABASE permitted")));
                assertEquals(0, number(owner.call("queryInteger",
                        "SELECT count(*) FROM pragma_database_list WHERE name='permitted'")));
                assertEquals(14, number(owner.call("executeStatus", "ATTACH DATABASE " + exteriorSql + " AS exterior")),
                        "SQLITE_CANTOPEN from denied ATTACH acquisition");
                assertEquals(0, number(owner.call("queryInteger",
                        "SELECT count(*) FROM pragma_database_list WHERE name='exterior'")));
                assertEquals(76, number(owner.call("readOnlyProbe")));
                owner.call("insertInteger", 276);
                assertEquals(2, number(owner.call("queryInteger", "SELECT count(*) FROM t")));
                owner.call("close");
            });
            calls[0].finish();
            assertTrue(calls[0].installed);
            assertTrue(calls[0].tid > 0);
            assertFalse(owner.session.isOpenForRuntime());
            assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(exterior)), "exterior bytes unchanged");
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally {
            finishB3(primary, reporter, "B6-T6", fixture, owners, calls);
        }
    }

    @Test
    void b6T7SymlinkRetargetAfterRestrictionCannotExpandGrant(TestReporter reporter) throws Exception {
        Path root = Files.createTempDirectory(work, "b6-t7-").toRealPath();
        Path granted = root.resolve("granted.db");
        Path exterior = work.resolve("b6-t7-exterior.db").toAbsolutePath();
        var fixture = sqliteFixture();
        Probe[] owners = new Probe[1];
        PlatformCall[] calls = new PlatformCall[1];
        CountDownLatch ready = new CountDownLatch(1), release = new CountDownLatch(1);
        Throwable primary = null;
        try {
            Probe observer = sqliteProbe(fixture, fixture.root, reporter);
            seedSqlite(observer, granted, 77);
            seedSqlite(observer, exterior, 177);
            Path link = root.resolve("retarget.db");
            Files.createSymbolicLink(link, granted);
            assertEquals(granted, Files.readSymbolicLink(link));
            byte[] before = Files.readAllBytes(exterior);
            Probe owner = owners[0] = sqliteProbe(fixture, fixture.newActorActivation(), reporter);
            prepareB6(owner);
            calls[0] = startB4Owner(root, owner, reporter, () -> {
                assertEquals(0, number(owner.call("openReadOnly", link.toString())));
                assertEquals(77, number(owner.call("nativeReadMarker")));
                owner.call("close");
                ready.countDown();
                awaitB3(release, "deterministic symlink replacement");
                assertEquals(14, number(owner.call("openReadOnly", link.toString())));
                assertSame(false, owner.call("isOpen"));
                assertEquals(0, number(owner.call("openReadOnly", granted.toString())));
                assertEquals(77, number(owner.call("readOnlyProbe")));
                owner.call("close");
            });
            awaitB3(ready, "initial symlink read and close");
            assertTrue(calls[0].installed);
            Files.delete(link);
            Files.createSymbolicLink(link, exterior);
            assertEquals(exterior, Files.readSymbolicLink(link));
            release.countDown();
            calls[0].finish();
            assertTrue(calls[0].installed);
            assertTrue(calls[0].tid > 0);
            assertFalse(owner.session.isOpenForRuntime());
            assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(exterior)), "exterior bytes unchanged");
        } catch (Exception | Error problem) { primary = problem; throw problem; }
        finally {
            release.countDown();
            finishB3(primary, reporter, "B6-T7", fixture, owners, calls);
        }
    }

    private static void prepareB6(Probe owner) {
        // Warm new guest closure/copy paths before Landlock, using no persistent file.
        assertEquals(0, number(owner.call("openUriFile", "file::memory:")));
        owner.call("execute", "CREATE TABLE t(value INTEGER)");
        owner.call("insertInteger", 1);
        assertEquals(1, number(owner.call("nativeReadMarker")));
        assertEquals(1, number(owner.call("readOnlyProbe")));
        owner.call("close");
        assertEquals(0, number(owner.call("openUriReadOnly", "file::memory:")));
        owner.call("close");
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

    // ---- PLAT074-D0: Protos-first SQLite helper in an independent OS process.
    // The helper is the real Protos CLI running isolated/helper.protos; protocol,
    // validation and SQLite custody are Protos. JUnit only launches, relays opaque
    // lines between the Protos client driver and the helper, kills and observes.

    private static final Path D0 = CASES.resolve("isolated");
    private static final String D0_EOF = "\u0000EOF";
    private static final Map<Process, java.util.concurrent.BlockingQueue<String>> d0Stdout =
            new java.util.LinkedHashMap<>();
    private static final Map<Process, java.util.concurrent.BlockingQueue<String>> d0Events =
            new java.util.LinkedHashMap<>();
    private static final Map<Process, StringBuilder> d0Logs = new java.util.LinkedHashMap<>();

    @Test
    void d0T1ProtocolRoundTripsExactProtosValues() throws Exception {
        runD0(() -> {
            Process client = d0Client();
            // Valid requests and exact signed 64-bit extremes round-trip through Protos.
            assertEquals("ADMITTED 1 PING null", d0Client(client, "DECODE_REQUEST",
                    d0RequestText(client, 1, "PING", null)));
            assertEquals("ADMITTED 9223372036854775807 INSERT -9223372036854775808",
                    d0Client(client, "DECODE_REQUEST", d0RequestText(client,
                            Long.MAX_VALUE, "INSERT", Long.toString(Long.MIN_VALUE))));
            // Unknown operations are syntactically admitted; the helper allowlist rejects them (T4).
            assertEquals("ADMITTED 2 OPEN null", d0Client(client, "DECODE_REQUEST",
                    d0RequestText(client, 2, "OPEN", null)));
            // The encoder refuses ids and values outside the exact domain.
            assertEquals("REJECTED", d0Client(client, "REQUEST", "0", "PING", "null"));
            assertEquals("REJECTED", d0Client(client, "REQUEST", "-3", "PING", "null"));
            assertEquals("REJECTED", d0Client(client, "REQUEST", "9223372036854775808", "PING", "null"));
            assertEquals("REJECTED", d0Client(client, "REQUEST", "3", "INSERT", "9223372036854775808"));
            // Admission failures: malformed JSON, wrong types, fractions, extra or nested fields.
            String[][] refused = {
                {"{\"version\":1,", "REFUSED null MALFORMED"},
                {"[1,2]", "REFUSED null MALFORMED"},
                {"{\"version\":1,\"id\":7,\"op\":\"PING\",\"value\":null,\"extra\":1}", "REFUSED null MALFORMED"},
                {"{\"version\":1,\"id\":7,\"op\":\"PING\"}", "REFUSED null MALFORMED"},
                {"{\"version\":2,\"id\":4,\"op\":\"PING\",\"value\":null}", "REFUSED null INVALID_VERSION"},
                {"{\"version\":\"1\",\"id\":4,\"op\":\"PING\",\"value\":null}", "REFUSED null INVALID_VERSION"},
                {"{\"version\":1,\"id\":\"5\",\"op\":\"PING\",\"value\":null}", "REFUSED null INVALID_ID"},
                {"{\"version\":1,\"id\":-1,\"op\":\"PING\",\"value\":null}", "REFUSED null INVALID_ID"},
                {"{\"version\":1,\"id\":0,\"op\":\"PING\",\"value\":null}", "REFUSED null INVALID_ID"},
                {"{\"version\":1,\"id\":1.0,\"op\":\"PING\",\"value\":null}", "REFUSED null INVALID_ID"},
                {"{\"version\":1,\"id\":9223372036854775808,\"op\":\"PING\",\"value\":null}", "REFUSED null INVALID_ID"},
                {"{\"version\":1,\"id\":6,\"op\":7,\"value\":null}", "REFUSED 6 INVALID_OPERATION"},
                {"{\"version\":1,\"id\":6,\"op\":\"\",\"value\":null}", "REFUSED 6 INVALID_OPERATION"},
                {"{\"version\":1,\"id\":8,\"op\":\"INSERT\",\"value\":1.5}", "REFUSED 8 INVALID_VALUE"},
                {"{\"version\":1,\"id\":8,\"op\":\"INSERT\",\"value\":\"1\"}", "REFUSED 8 INVALID_VALUE"},
                {"{\"version\":1,\"id\":8,\"op\":\"INSERT\",\"value\":true}", "REFUSED 8 INVALID_VALUE"},
                {"{\"version\":1,\"id\":8,\"op\":\"INSERT\",\"value\":[1]}", "REFUSED 8 INVALID_VALUE"},
                {"{\"version\":1,\"id\":8,\"op\":\"INSERT\",\"value\":{\"v\":1}}", "REFUSED 8 INVALID_VALUE"},
                {"{\"version\":1,\"id\":8,\"op\":\"INSERT\",\"value\":9223372036854775808}", "REFUSED 8 INVALID_VALUE"},
                {d0Padded("{\"version\":1,\"id\":9,\"op\":\"PING\",\"value\":null", 4097), "REFUSED null OVERSIZED"},
            };
            for (String[] vector : refused) {
                assertEquals(vector[1], d0Client(client, "DECODE_REQUEST", vector[0]), vector[0]);
            }
            assertEquals("ADMITTED 9 PING null", d0Client(client, "DECODE_REQUEST",
                    d0Padded("{\"version\":1,\"id\":9,\"op\":\"PING\",\"value\":null", 4096)));
            // Responses: correlation, status, result type and structured error are validated.
            String ok = "{\"version\":1,\"id\":1,\"status\":\"OK\",\"value\":0,\"error\":null}";
            assertEquals("ACCEPTED OK 0 null", d0Client(client, "MATCH", "1", ok));
            assertEquals("REJECTED", d0Client(client, "MATCH", "2", ok));
            assertEquals("ACCEPTED OK true null", d0Client(client, "MATCH", "3",
                    "{\"version\":1,\"id\":3,\"status\":\"OK\",\"value\":true,\"error\":null}"));
            assertEquals("ACCEPTED ERROR null CLOSED", d0Client(client, "MATCH", "4",
                    "{\"version\":1,\"id\":4,\"status\":\"ERROR\",\"value\":null,\"error\":{\"reason\":\"CLOSED\"}}"));
            String[] rejectedResponses = {
                "{\"version\":1,\"id\":1,\"status\":\"DONE\",\"value\":0,\"error\":null}",
                "{\"version\":2,\"id\":1,\"status\":\"OK\",\"value\":0,\"error\":null}",
                "{\"version\":1,\"id\":1,\"status\":\"OK\",\"value\":0.5,\"error\":null}",
                "{\"version\":1,\"id\":1,\"status\":\"OK\",\"value\":\"0\",\"error\":null}",
                "{\"version\":1,\"id\":1,\"status\":\"OK\",\"value\":0,\"error\":{\"reason\":\"CLOSED\"}}",
                "{\"version\":1,\"id\":1,\"status\":\"ERROR\",\"value\":null,\"error\":null}",
                "{\"version\":1,\"id\":1,\"status\":\"ERROR\",\"value\":1,\"error\":{\"reason\":\"CLOSED\"}}",
                "{\"version\":1,\"id\":1,\"status\":\"ERROR\",\"value\":null,\"error\":{\"reason\":\"0x7f00 /tmp/x\"}}",
                "{\"version\":1,\"id\":1,\"status\":\"ERROR\",\"value\":null,\"error\":{\"reason\":\"CLOSED\",\"trace\":\"x\"}}",
                "{\"version\":1,\"id\":1,\"status\":\"OK\",\"value\":0,\"error\":null,\"extra\":0}",
                "{\"version\":1,\"id\":null,\"status\":\"ERROR\",\"value\":null,\"error\":{\"reason\":\"MALFORMED\"}}",
                "not json",
            };
            for (String response : rejectedResponses) {
                assertEquals("REJECTED", d0Client(client, "MATCH", "1", response), response);
            }
            assertEquals("ACCEPTED null ERROR null MALFORMED", d0Client(client, "RESPONSE",
                    "{\"version\":1,\"id\":null,\"status\":\"ERROR\",\"value\":null,\"error\":{\"reason\":\"MALFORMED\"}}"));
            assertEquals("ACCEPTED", d0Client(client, "READY", "{\"version\":1,\"event\":\"READY\"}"));
            assertEquals("REJECTED", d0Client(client, "READY", "{\"version\":1,\"event\":\"GO\"}"));
            assertEquals("ACCEPTED UNCERTAIN null CONNECTION_LOST", d0Client(client, "LOST", "5"));
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d0T2ProtosHelperRunsInIndependentOsProcess() throws Exception {
        Path db = Files.createTempDirectory(work, "d0-t2-").toRealPath().resolve("d0.db");
        runD0(() -> {
            Process client = d0Client();
            Process helper = d0Helper(db);
            long self = ProcessHandle.current().pid();
            assertNotEquals(self, helper.pid());
            assertNotEquals(client.pid(), helper.pid());
            assertEquals(self, helper.toHandle().parent().map(ProcessHandle::pid).orElse(-1L),
                    "the helper must be a separate child OS process of the JUnit JVM");
            // READY was produced and PING answered by guest code, validated by the Protos client.
            d0Ok(client, helper, 1, "PING", null, "0");
            d0Ok(client, helper, 2, "CLOSE", null, "null");
            d0Ok(client, helper, 3, "STOP", null, "null");
            d0Exit(helper);
            d0CloseClient(client);
            assertTrue(Files.isRegularFile(db), "the helper opened the bootstrap database path");
            return null;
        });
    }

    @Test
    void d0T3ProtosSqliteCommitsAndReopensAcrossHelpers() throws Exception {
        Path db = Files.createTempDirectory(work, "d0-t3-").toRealPath().resolve("d0.db");
        runD0(() -> {
            Process client = d0Client();
            Process a = d0Helper(db);
            d0Ok(client, a, 1, "INIT", null, "null");
            d0Ok(client, a, 2, "BEGIN", null, "null");
            d0Ok(client, a, 3, "INSERT", "10", "null");
            d0Ok(client, a, 4, "INSERT", "20", "null");
            d0Ok(client, a, 5, "COMMIT", null, "null");
            d0Ok(client, a, 6, "COUNT", null, "2");
            d0Ok(client, a, 7, "SUM", null, "30");
            d0Ok(client, a, 8, "CLOSE", null, "null");
            d0Ok(client, a, 9, "STOP", null, "null");
            d0Exit(a);
            Process b = d0Helper(db);
            assertNotEquals(a.pid(), b.pid());
            d0Ok(client, b, 1, "COUNT", null, "2");
            d0Ok(client, b, 2, "SUM", null, "30");
            d0Ok(client, b, 3, "INTEGRITY", null, "true");
            d0Ok(client, b, 4, "STOP", null, "null");
            d0Exit(b);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d0T4MalformedAndUnauthorizedRequestsNeverReachSqlite() throws Exception {
        Path root = Files.createTempDirectory(work, "d0-t4-").toRealPath();
        Path db = root.resolve("d0.db");
        Path exterior = Files.createTempDirectory(work, "d0-t4-exterior-").toRealPath().resolve("outside.db");
        runD0(() -> {
            Process client = d0Client();
            Process a = d0Helper(db);
            d0Ok(client, a, 1, "INIT", null, "null");
            d0Ok(client, a, 2, "BEGIN", null, "null");
            d0Ok(client, a, 3, "INSERT", "5", "null");
            d0Ok(client, a, 4, "COMMIT", null, "null");
            // Uncorrelated admission failures carry no id.
            d0Raw(client, a, "{\"version\":1,", "ACCEPTED null ERROR null MALFORMED");
            d0Raw(client, a, "{\"version\":1,\"id\":\"6\",\"op\":\"INSERT\",\"value\":7}",
                    "ACCEPTED null ERROR null INVALID_ID");
            d0Raw(client, a, "{\"version\":1,\"id\":6,\"op\":\"INSERT\",\"value\":\"7\"}",
                    "ACCEPTED 6 ERROR null INVALID_VALUE");
            // Duplicate and decreasing ids are rejected before dispatch.
            d0Expect(client, a, 4, "INSERT", "9", "ACCEPTED ERROR null NON_INCREASING_ID");
            d0Expect(client, a, 2, "INSERT", "9", "ACCEPTED ERROR null NON_INCREASING_ID");
            // Paths, SQL and symbols cannot be carried; unknown operations never dispatch.
            d0Raw(client, a, "{\"version\":1,\"id\":7,\"op\":\"OPEN\",\"value\":\"" + exterior + "\"}",
                    "ACCEPTED 7 ERROR null INVALID_VALUE");
            d0Raw(client, a, "{\"version\":1,\"id\":8,\"op\":\"EXEC\",\"value\":\"INSERT INTO entries VALUES (99)\"}",
                    "ACCEPTED 8 ERROR null INVALID_VALUE");
            long id = 9;
            for (String operation : List.of("OPEN", "EXEC", "ATTACH", "VACUUM", "EVAL", "LOAD", "IMPORT",
                    "CONTINUE_ACK", "sqlite3_exec")) {
                d0Expect(client, a, id++, operation, null, "ACCEPTED ERROR null UNSUPPORTED_OPERATION");
            }
            d0Expect(client, a, id++, "INSERT", null, "ACCEPTED ERROR null INVALID_VALUE");
            d0Expect(client, a, id++, "COUNT", "1", "ACCEPTED ERROR null INVALID_VALUE");
            d0Ok(client, a, id++, "COUNT", null, "1");
            d0Ok(client, a, id++, "SUM", null, "5");
            // Exactly 4096 octets before the terminator is admitted (a read-only COUNT).
            String limit = d0Padded("{\"version\":1,\"id\":" + id + ",\"op\":\"COUNT\",\"value\":null", 4096);
            d0Raw(client, a, limit, "ACCEPTED " + id + " OK 1 null");
            id++;
            // 4097 octets fails the framing: no reply, no INSERT, fail-closed process exit.
            d0Send(a, d0Padded("{\"version\":1,\"id\":" + id + ",\"op\":\"INSERT\",\"value\":7", 4097));
            d0AwaitEvent(a, "FRAMING_REJECTED");
            assertEquals(D0_EOF, d0Next(a), "an oversized line must never receive a reply");
            assertTrue(a.waitFor(30, TimeUnit.SECONDS), "fail-closed helper termination timeout");
            assertNotEquals(0, a.exitValue(), "framing failure must not be a clean protocol exit");
            assertFalse(Files.exists(exterior), "no request may create or open an exterior path");
            Process b = d0Helper(db);
            d0Ok(client, b, 1, "COUNT", null, "1");
            d0Ok(client, b, 2, "SUM", null, "5");
            d0Ok(client, b, 3, "INTEGRITY", null, "true");
            d0Ok(client, b, 4, "STOP", null, "null");
            d0Exit(b);
            assertFalse(Files.exists(exterior));
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d0T5ExplicitRollbackKeepsCommittedState() throws Exception {
        Path db = Files.createTempDirectory(work, "d0-t5-").toRealPath().resolve("d0.db");
        runD0(() -> {
            Process client = d0Client();
            Process a = d0Helper(db);
            d0Ok(client, a, 1, "INIT", null, "null");
            d0Ok(client, a, 2, "BEGIN", null, "null");
            d0Ok(client, a, 3, "INSERT", "10", "null");
            d0Ok(client, a, 4, "COMMIT", null, "null");
            d0Ok(client, a, 5, "COUNT", null, "1");
            d0Ok(client, a, 6, "SUM", null, "10");
            d0Ok(client, a, 7, "BEGIN", null, "null");
            d0Ok(client, a, 8, "INSERT", "99", "null");
            d0Ok(client, a, 9, "ROLLBACK", null, "null");
            d0Ok(client, a, 10, "COUNT", null, "1");
            d0Ok(client, a, 11, "SUM", null, "10");
            d0Ok(client, a, 12, "STOP", null, "null");
            d0Exit(a);
            Process b = d0Helper(db);
            d0Ok(client, b, 1, "COUNT", null, "1");
            d0Ok(client, b, 2, "SUM", null, "10");
            d0Ok(client, b, 3, "INTEGRITY", null, "true");
            d0Ok(client, b, 4, "STOP", null, "null");
            d0Exit(b);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d0T6KilledHelperRollsBackUncommittedTransaction() throws Exception {
        Path db = Files.createTempDirectory(work, "d0-t6-").toRealPath().resolve("d0.db");
        runD0(() -> {
            Process client = d0Client();
            Process a = d0Helper(db);
            d0Ok(client, a, 1, "INIT", null, "null");
            d0Ok(client, a, 2, "BEGIN", null, "null");
            d0Ok(client, a, 3, "INSERT", "10", "null");
            d0Ok(client, a, 4, "COMMIT", null, "null");
            d0Ok(client, a, 5, "STOP", null, "null");
            d0Exit(a);
            Process b = d0Helper(db);
            d0Ok(client, b, 1, "BEGIN", null, "null");
            d0Ok(client, b, 2, "INSERT", "77", "null");
            d0Kill(b);
            Process c = d0Helper(db);
            d0Ok(client, c, 1, "COUNT", null, "1");
            d0Ok(client, c, 2, "SUM", null, "10");
            d0Ok(client, c, 3, "INTEGRITY", null, "true");
            d0Ok(client, c, 4, "STOP", null, "null");
            d0Exit(c);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d0T7CommittedButUnacknowledgedOperationIsUncertain() throws Exception {
        Path db = Files.createTempDirectory(work, "d0-t7-").toRealPath().resolve("d0.db");
        runD0(() -> {
            Process client = d0Client();
            Process a = d0Helper(db);
            d0Ok(client, a, 1, "INIT", null, "null");
            d0Ok(client, a, 2, "BEGIN", null, "null");
            d0Ok(client, a, 3, "INSERT", "10", "null");
            d0Ok(client, a, 4, "COMMIT", null, "null");
            d0Ok(client, a, 5, "STOP", null, "null");
            d0Exit(a);
            Process b = d0Helper(db);
            d0Ok(client, b, 1, "BEGIN", null, "null");
            d0Ok(client, b, 2, "INSERT", "40", "null");
            d0Send(b, d0RequestText(client, 3, "COMMIT_HOLD_ACK", null));
            d0AwaitEvent(b, "COMMITTED_BEFORE_ACK");
            // The helper writes the acknowledgement only after a continuation that is never sent.
            assertEquals(null, d0Stdout.get(b).poll(), "no acknowledgement may precede the continuation");
            d0Kill(b);
            // The lost reply is UNCERTAIN for the client: neither success nor rollback.
            assertEquals("ACCEPTED UNCERTAIN null CONNECTION_LOST", d0Client(client, "LOST", "3"));
            // No automatic retry: the durable state is inspected, not re-written.
            Process c = d0Helper(db);
            d0Ok(client, c, 1, "COUNT", null, "2");
            d0Ok(client, c, 2, "SUM", null, "50");
            d0Ok(client, c, 3, "INTEGRITY", null, "true");
            d0Ok(client, c, 4, "STOP", null, "null");
            d0Exit(c);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d0T8ClosedHelperRejectsFurtherDatabaseOperations() throws Exception {
        Path db = Files.createTempDirectory(work, "d0-t8-").toRealPath().resolve("d0.db");
        runD0(() -> {
            Process client = d0Client();
            Process a = d0Helper(db);
            d0Ok(client, a, 1, "INIT", null, "null");
            d0Ok(client, a, 2, "CLOSE", null, "null");
            d0Expect(client, a, 3, "COUNT", null, "ACCEPTED ERROR null CLOSED");
            d0Expect(client, a, 4, "INSERT", "1", "ACCEPTED ERROR null CLOSED");
            d0Expect(client, a, 5, "BEGIN", null, "ACCEPTED ERROR null CLOSED");
            d0Expect(client, a, 6, "INIT", null, "ACCEPTED ERROR null CLOSED");
            // CLOSE is idempotent and never reopens; STOP still terminates cleanly.
            d0Ok(client, a, 7, "CLOSE", null, "null");
            d0Expect(client, a, 8, "COUNT", null, "ACCEPTED ERROR null CLOSED");
            d0Ok(client, a, 9, "STOP", null, "null");
            d0Exit(a);
            assertFalse(a.isAlive());
            assertFalse(ProcessHandle.of(a.pid()).map(ProcessHandle::isAlive).orElse(false),
                    "no surviving helper process");
            d0CloseClient(client);
            return null;
        });
    }

    /** Runs one D0 scenario; every launched process is killed and reaped even on failure. */
    private static void runD0(java.util.concurrent.Callable<?> scenario) throws Exception {
        d0Stdout.clear();
        d0Events.clear();
        d0Logs.clear();
        Throwable primary = null;
        try {
            scenario.call();
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            for (Process child : d0Stdout.keySet()) if (child.isAlive()) child.destroyForcibly();
            AssertionError cleanup = null;
            boolean interrupted = Thread.interrupted();
            for (Process child : d0Stdout.keySet()) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                boolean terminated = false;
                while (!terminated && System.nanoTime() < deadline) {
                    try {
                        terminated = child.waitFor(Math.max(1, deadline - System.nanoTime()),
                                TimeUnit.NANOSECONDS);
                    } catch (InterruptedException failure) {
                        interrupted = true;
                    }
                }
                if (!terminated) {
                    if (cleanup == null) cleanup = new AssertionError("D0 child retained after cleanup");
                    cleanup.addSuppressed(new AssertionError("PID=" + child.pid()));
                }
                synchronized (d0Logs.get(child)) {
                    System.err.println("D0 PID=" + child.pid() + " stderr:\n" + d0Logs.get(child));
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            if (cleanup != null) {
                if (primary != null) primary.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }

    /** Launches the real Protos CLI on one D0 entry; stdout and stderr stay separate pipes. */
    private static Process d0Launch(Path entry, boolean provider, String... arguments) throws Exception {
        return d0Start(null, null, entry, provider, arguments);
    }

    /**
     * D0 launch, optionally behind the PLAT074-D1 pre-exec launcher. With {@code sandbox == null}
     * the invocation is exactly D0's. Otherwise {@code sandbox} (launcher, policy, environment and
     * the {@code --} separator) precedes the same canonical JVM, the extra {@code jvmOptions}
     * relocate JVM temporary state into the private grant, and nothing is inherited from the
     * harness environment: the launcher passes only its explicit {@code --env} entries.
     */
    private static Process d0Start(List<String> sandbox, List<String> jvmOptions, Path entry,
            boolean provider, String... arguments) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        assertTrue(classpath != null && !classpath.isBlank(), "missing test subprocess classpath");
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        assertTrue(Files.isExecutable(java), "missing child JVM: " + java);
        Path home = Path.of("").toAbsolutePath();
        List<String> invocation = new ArrayList<>();
        if (sandbox != null) {
            invocation.addAll(sandbox);
            java = java.toRealPath();
        }
        invocation.addAll(List.of(java.toString(),
                "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"));
        if (sandbox == null) {
            String cache = System.getProperty("polyglot.engine.userResourceCache");
            if (cache != null) invocation.add("-Dpolyglot.engine.userResourceCache=" + cache);
        } else {
            invocation.addAll(jvmOptions);
        }
        invocation.addAll(List.of("-cp", classpath, "com.guillermomolina.protos.cli.ProtosCli"));
        // The only native authority: the CLI's explicit foreign-provider authorization.
        if (provider) invocation.addAll(List.of("--foreign-provider-path", sqliteJar.toAbsolutePath().toString()));
        invocation.add(entry.toAbsolutePath().toString());
        invocation.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(invocation).directory(home.toFile());
        if (sandbox == null) builder.environment().put("PROTOS_HOME", home.toString());
        else builder.environment().clear();
        Process child = builder.start();
        var lines = new java.util.concurrent.LinkedBlockingQueue<String>();
        var events = new java.util.concurrent.LinkedBlockingQueue<String>();
        StringBuilder log = new StringBuilder();
        d0Stdout.put(child, lines);
        d0Events.put(child, events);
        d0Logs.put(child, log);
        Thread.ofPlatform().daemon().name("d0-stdout-" + child.pid()).start(() -> {
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null; ) lines.offer(line);
            } catch (Exception failure) {
                synchronized (log) { log.append("stdout drain: ").append(failure).append('\n'); }
            } finally {
                lines.offer(D0_EOF);
            }
        });
        Thread.ofPlatform().daemon().name("d0-stderr-" + child.pid()).start(() -> {
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(child.getErrorStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    synchronized (log) {
                        if (log.length() > 32768) log.delete(0, 16384);
                        log.append(line).append('\n');
                    }
                    if (List.of("COMMITTED_BEFORE_ACK", "FRAMING_REJECTED", "COMMIT_ACK_WITHHELD",
                            "ADMISSION_FAILED_CLOSED", "LANDLOCK_APPLIED", "DURABLE_PRECOMMIT",
                            "DURABLE_COMMITTED_BEFORE_ACK", "DURABLE_ACK_WITHHELD",
                            "DURABLE_RECONCILE_REQUIRED", "DURABLE_SESSION_BROKEN").contains(line)
                            || line.startsWith("SANDBOX_REFUSED ")) events.offer(line);
                }
            } catch (Exception failure) {
                synchronized (log) { log.append("stderr drain: ").append(failure).append('\n'); }
            } finally {
                events.offer(D0_EOF);
            }
        });
        assertTrue(child.pid() > 0);
        assertNotEquals(ProcessHandle.current().pid(), child.pid());
        return child;
    }

    /** The Protos client driver: builds requests and validates replies; no provider. */
    private static Process d0Client() throws Exception {
        return d0Launch(D0.resolve("client-driver.protos"), false);
    }

    /** The Protos helper; the absolute database path is its only bootstrap argument. */
    private static Process d0Helper(Path db) throws Exception {
        assertTrue(db.isAbsolute(), "D0 database path must be absolute");
        Process helper = d0Launch(D0.resolve("helper.protos"), true, db.toString());
        Process client = d0Stdout.keySet().iterator().next();
        assertEquals("ACCEPTED", d0Client(client, "READY", d0Next(helper)),
                "the helper must publish the Protos READY event first");
        return helper;
    }

    private static String d0Next(Process child) throws Exception {
        String line = d0Stdout.get(child).poll(60, TimeUnit.SECONDS);
        String log;
        synchronized (d0Logs.get(child)) { log = d0Logs.get(child).toString(); }
        assertTrue(line != null, "PID=" + child.pid() + " stdout timeout, alive=" + child.isAlive() + "\n" + log);
        return line;
    }

    private static void d0AwaitEvent(Process child, String expected) throws Exception {
        String event = d0Events.get(child).poll(60, TimeUnit.SECONDS);
        String log;
        synchronized (d0Logs.get(child)) { log = d0Logs.get(child).toString(); }
        assertEquals(expected, event, "PID=" + child.pid() + "\n" + log);
    }

    private static void d0Send(Process child, String line) throws Exception {
        child.getOutputStream().write((line + "\n").getBytes(StandardCharsets.UTF_8));
        child.getOutputStream().flush();
    }

    /** One driver command: each field is one line; the driver answers with one line. */
    private static String d0Client(Process client, String... fields) throws Exception {
        for (String field : fields) d0Send(client, field);
        String reply = d0Next(client);
        assertNotEquals(D0_EOF, reply, "Protos client driver terminated");
        return reply;
    }

    private static String d0RequestText(Process client, long id, String operation, String value)
            throws Exception {
        String reply = d0Client(client, "REQUEST", Long.toString(id), operation,
                value == null ? "null" : value);
        assertTrue(reply.startsWith("TEXT "), reply);
        return reply.substring("TEXT ".length());
    }

    /** Protos builds the request; the reply must be correlated with it by the Protos client. */
    private static void d0Expect(Process client, Process helper, long id, String operation, String value,
            String expected) throws Exception {
        d0Send(helper, d0RequestText(client, id, operation, value));
        String reply = d0Next(helper);
        assertNotEquals(D0_EOF, reply, "helper closed stdout before replying to " + operation);
        assertEquals(expected, d0Client(client, "MATCH", Long.toString(id), reply), reply);
    }

    private static void d0Ok(Process client, Process helper, long id, String operation, String value,
            String result) throws Exception {
        d0Expect(client, helper, id, operation, value, "ACCEPTED OK " + result + " null");
    }

    /** Sends an adversarial raw line; the reply is decoded by the Protos client. */
    private static void d0Raw(Process client, Process helper, String line, String expected) throws Exception {
        d0Send(helper, line);
        String reply = d0Next(helper);
        assertNotEquals(D0_EOF, reply, "helper closed stdout before rejecting " + line);
        assertEquals(expected, d0Client(client, "RESPONSE", reply), reply);
    }

    /** A JSON object padded with insignificant spaces to exactly {@code octets} ASCII octets. */
    private static String d0Padded(String unterminatedObject, int octets) {
        int padding = octets - unterminatedObject.length() - 1;
        assertTrue(padding >= 0, "padding target too small");
        return unterminatedObject + " ".repeat(padding) + "}";
    }

    private static void d0Exit(Process child) throws Exception {
        assertEquals(D0_EOF, d0Next(child), "stdout carries no line after the final reply");
        assertTrue(child.waitFor(30, TimeUnit.SECONDS), "normal helper termination timeout");
        assertEquals(0, child.exitValue(), "PID=" + child.pid() + "\n" + d0Logs.get(child));
    }

    private static void d0Kill(Process child) throws Exception {
        assertTrue(child.isAlive(), "helper died before SIGKILL");
        child.destroyForcibly();
        assertTrue(child.waitFor(30, TimeUnit.SECONDS), "SIGKILL termination timeout");
        assertEquals(137, child.exitValue(), "Linux SIGKILL must not be a normal SQLite close");
        assertEquals(D0_EOF, d0Next(child), "a killed helper must not publish a later reply");
    }

    private static void d0CloseClient(Process client) throws Exception {
        client.getOutputStream().close();
        d0Exit(client);
    }

    // ---- PLAT074-D1: pre-exec Landlock confinement of the D0 helper JVM.
    // isolated/native/sandbox-exec.c (test fixture only) installs a Landlock domain in a
    // single-threaded process and then execve()s the same D0 JVM, so every JVM and GraalVM
    // thread inherits it. The Protos client stays unrestricted. A missing Landlock profile is
    // UNSUPPORTED (JUnit assumption, never a pass); no scenario ever falls back to running
    // unconfined. LANDLOCK_APPLIED is a diagnostic only: every claim rests on allowed and
    // denied real SQLite opens compared with unrestricted controls.

    private static final String D1_AUTHORIZED = "AUTHORIZED_OPENED true";
    private static final String D1_EXTERIOR_OPENED = "EXTERIOR_OPENED true";
    private static final String D1_EXTERIOR_DENIED = "EXTERIOR_DENIED";
    private static Path d1Launcher;
    private static String d1Unsupported;
    private static boolean d1Checked;
    private static long d1Abi;

    @Test
    void d1T1LandlockIsInstalledBeforeProtosBoot() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-t1-");
        runD0(() -> {
            Process client = d0Client();
            // d1Helper requires LANDLOCK_APPLIED from the launcher first, then the Protos READY.
            Process helper = d1Helper(root, root.resolve("d1.db"));
            assertNotEquals(ProcessHandle.current().pid(), helper.pid());
            assertNotEquals(client.pid(), helper.pid());
            // execve replaced the launcher image in place: the confined PID now runs the JVM.
            assertEquals(Path.of(System.getProperty("java.home"), "bin", "java").toRealPath().toString(),
                    helper.info().command().orElse(""), "the launcher must exec the JVM, not fork it");
            d0Ok(client, helper, 1, "PING", null, "0");
            d0Ok(client, helper, 2, "CLOSE", null, "null");
            d0Ok(client, helper, 3, "STOP", null, "null");
            d0Exit(helper);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d1T2ConfinedSqliteCreatesAndReopensDurableDatabase() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-t2-");
        Path db = root.resolve("d1.db");
        runD0(() -> {
            Process client = d0Client();
            Process a = d1Helper(root, db);
            d0Ok(client, a, 1, "INIT", null, "null");
            d0Ok(client, a, 2, "BEGIN", null, "null");
            d0Ok(client, a, 3, "INSERT", "10", "null");
            d0Ok(client, a, 4, "INSERT", "20", "null");
            d0Ok(client, a, 5, "COMMIT", null, "null");
            d0Ok(client, a, 6, "COUNT", null, "2");
            d0Ok(client, a, 7, "SUM", null, "30");
            d0Ok(client, a, 8, "INTEGRITY", null, "true");
            d0Ok(client, a, 9, "CLOSE", null, "null");
            d0Ok(client, a, 10, "STOP", null, "null");
            d0Exit(a);
            Process b = d1Helper(root, db);
            assertNotEquals(a.pid(), b.pid());
            d0Ok(client, b, 1, "COUNT", null, "2");
            d0Ok(client, b, 2, "SUM", null, "30");
            d0Ok(client, b, 3, "INTEGRITY", null, "true");
            d0Ok(client, b, 4, "STOP", null, "null");
            d0Exit(b);
            d0CloseClient(client);
            assertEquals(List.of("d1.db"), directoryNames(root), "no journal survives a clean close");
            return null;
        });
    }

    @Test
    void d1T3NativeSqliteCannotOpenExteriorDatabase() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-t3-grant-");
        Path control = d1Dir("d1-t3-control-");
        Path outside = d1Dir("d1-t3-exterior-");
        Path exterior = outside.resolve("exterior.db");
        runD0(() -> {
            Process client = d0Client();
            d1Seed(client, exterior, 7);
            Map<String, String> before = d1Snapshot(outside);
            // Positive control: the same probe, module and FFM provider, unrestricted.
            assertEquals(List.of(D1_AUTHORIZED, D1_EXTERIOR_OPENED),
                    d1Probe(null, control.resolve("control.db"), exterior));
            assertEquals(before, d1Snapshot(outside), "the control probe must not modify the exterior");
            // Restricted: the authorized open succeeds in the same JVM, the exterior is denied.
            assertEquals(List.of(D1_AUTHORIZED, D1_EXTERIOR_DENIED),
                    d1Probe(root, root.resolve("authorized.db"), exterior));
            assertTrue(Files.isRegularFile(root.resolve("authorized.db")));
            assertEquals(before, d1Snapshot(outside), "a denied open must leave the exterior untouched");
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d1T4SeparateHelpersCannotWidenEachOthersGrants() throws Exception {
        d1RequireProfile();
        Path a = d1Dir("d1-t4-a-");
        Path b = d1Dir("d1-t4-b-");
        Path dbA = a.resolve("a.db");
        Path dbB = b.resolve("b.db");
        runD0(() -> {
            Process client = d0Client();
            Process helperA = d1Helper(a, dbA);
            Process helperB = d1Helper(b, dbB);
            for (Object[] step : new Object[][] {{helperA, "1"}, {helperB, "2"}}) {
                Process helper = (Process) step[0];
                d0Ok(client, helper, 1, "INIT", null, "null");
                d0Ok(client, helper, 2, "BEGIN", null, "null");
                d0Ok(client, helper, 3, "INSERT", (String) step[1], "null");
                d0Ok(client, helper, 4, "COMMIT", null, "null");
            }
            Map<String, String> beforeA = d1Snapshot(a);
            Map<String, String> beforeB = d1Snapshot(b);
            // Control: both databases are openable without Landlock.
            assertEquals(List.of(D1_AUTHORIZED, D1_EXTERIOR_OPENED), d1Probe(null, dbA, dbB));
            assertEquals(List.of(D1_AUTHORIZED, D1_EXTERIOR_OPENED), d1Probe(null, dbB, dbA));
            // Each grant opens its own database and is denied the other's.
            assertEquals(List.of(D1_AUTHORIZED, D1_EXTERIOR_DENIED), d1Probe(a, dbA, dbB));
            assertEquals(List.of(D1_AUTHORIZED, D1_EXTERIOR_DENIED), d1Probe(b, dbB, dbA));
            // A helper confined to A cannot even boot on B's database: no READY, failed exit.
            Process cross = d1Launch(a, List.of(), D0.resolve("helper.protos"), dbB.toString());
            d0AwaitEvent(cross, "LANDLOCK_APPLIED");
            assertEquals(D0_EOF, d0Next(cross), "a helper confined to A must not open B");
            assertTrue(cross.waitFor(60, TimeUnit.SECONDS), "cross-grant helper termination timeout");
            assertNotEquals(0, cross.exitValue());
            assertEquals(beforeA, d1Snapshot(a));
            assertEquals(beforeB, d1Snapshot(b));
            // Both helpers keep serving their own authorized operations.
            d0Ok(client, helperA, 5, "COUNT", null, "1");
            d0Ok(client, helperA, 6, "SUM", null, "1");
            d0Ok(client, helperB, 5, "COUNT", null, "1");
            d0Ok(client, helperB, 6, "SUM", null, "2");
            d0Ok(client, helperA, 7, "STOP", null, "null");
            d0Ok(client, helperB, 7, "STOP", null, "null");
            d0Exit(helperA);
            d0Exit(helperB);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d1T5UnrestrictedClientDoesNotWidenHelperAuthority() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-t5-grant-");
        Path clientSide = d1Dir("d1-t5-client-");
        Path outside = d1Dir("d1-t5-exterior-");
        Path db = root.resolve("d1.db");
        Path control = outside.resolve("control.db");
        runD0(() -> {
            Process client = d0Client();
            d1Seed(client, control, 3);
            Map<String, String> before = d1Snapshot(outside);
            // The client-side authority (unrestricted Protos CLI, the client's launch profile)
            // opens the exterior control database with the real provider.
            assertEquals(List.of(D1_AUTHORIZED, D1_EXTERIOR_OPENED),
                    d1Probe(null, clientSide.resolve("client.db"), control));
            Process helper = d1Helper(root, db);
            // The client cannot hand its authority to the helper through the protocol.
            d0Raw(client, helper, "{\"version\":1,\"id\":1,\"op\":\"OPEN\",\"value\":\"" + control + "\"}",
                    "ACCEPTED 1 ERROR null INVALID_VALUE");
            d0Expect(client, helper, 2, "OPEN", null, "ACCEPTED ERROR null UNSUPPORTED_OPERATION");
            d0Expect(client, helper, 3, "ATTACH", null, "ACCEPTED ERROR null UNSUPPORTED_OPERATION");
            d0Ok(client, helper, 4, "INIT", null, "null");
            d0Ok(client, helper, 5, "COUNT", null, "0");
            d0Ok(client, helper, 6, "STOP", null, "null");
            d0Exit(helper);
            // The helper's own grant, through the same module and provider, is denied the control.
            assertEquals(List.of(D1_AUTHORIZED, D1_EXTERIOR_DENIED), d1Probe(root, db, control));
            assertEquals(before, d1Snapshot(outside));
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d1T6UnauthorizedSqlOperationsRemainRejectedUnderConfinement() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-t6-");
        Path db = root.resolve("d1.db");
        Path outside = d1Dir("d1-t6-exterior-");
        Path existing = outside.resolve("existing.db");
        Path missing = outside.resolve("outside.db");
        runD0(() -> {
            Process client = d0Client();
            d1Seed(client, existing, 1);
            Map<String, String> before = d1Snapshot(outside);
            Process a = d1Helper(root, db);
            d0Ok(client, a, 1, "INIT", null, "null");
            d0Ok(client, a, 2, "BEGIN", null, "null");
            d0Ok(client, a, 3, "INSERT", "5", "null");
            d0Ok(client, a, 4, "COMMIT", null, "null");
            d0Raw(client, a, "{\"version\":1,\"id\":5,\"op\":\"OPEN\",\"value\":\"" + missing + "\"}",
                    "ACCEPTED 5 ERROR null INVALID_VALUE");
            d0Raw(client, a, "{\"version\":1,\"id\":6,\"op\":\"EXEC\",\"value\":\"ATTACH DATABASE '"
                    + existing + "' AS x\"}", "ACCEPTED 6 ERROR null INVALID_VALUE");
            long id = 7;
            for (String operation : List.of("OPEN", "EXEC", "ATTACH", "VACUUM", "LOAD", "IMPORT")) {
                d0Expect(client, a, id++, operation, null, "ACCEPTED ERROR null UNSUPPORTED_OPERATION");
            }
            d0Ok(client, a, id++, "COUNT", null, "1");
            d0Ok(client, a, id++, "SUM", null, "5");
            String limit = d0Padded("{\"version\":1,\"id\":" + id + ",\"op\":\"COUNT\",\"value\":null", 4096);
            d0Raw(client, a, limit, "ACCEPTED " + id + " OK 1 null");
            id++;
            d0Send(a, d0Padded("{\"version\":1,\"id\":" + id + ",\"op\":\"INSERT\",\"value\":7", 4097));
            d0AwaitEvent(a, "FRAMING_REJECTED");
            assertEquals(D0_EOF, d0Next(a), "an oversized line must never receive a reply");
            assertTrue(a.waitFor(30, TimeUnit.SECONDS), "fail-closed helper termination timeout");
            assertNotEquals(0, a.exitValue(), "framing failure must not be a clean protocol exit");
            assertFalse(Files.exists(missing), "no request may create an exterior path");
            assertEquals(before, d1Snapshot(outside), "no request may modify an exterior database");
            Process b = d1Helper(root, db);
            d0Ok(client, b, 1, "COUNT", null, "1");
            d0Ok(client, b, 2, "SUM", null, "5");
            d0Ok(client, b, 3, "STOP", null, "null");
            d0Exit(b);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d1T7UncommittedTransactionRollsBackAfterSandboxedHelperKill() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-t7-");
        Path db = root.resolve("d1.db");
        runD0(() -> {
            Process client = d0Client();
            Process a = d1Helper(root, db);
            d0Ok(client, a, 1, "INIT", null, "null");
            d0Ok(client, a, 2, "BEGIN", null, "null");
            d0Ok(client, a, 3, "INSERT", "10", "null");
            d0Ok(client, a, 4, "COMMIT", null, "null");
            d0Ok(client, a, 5, "STOP", null, "null");
            d0Exit(a);
            Process b = d1Helper(root, db);
            d0Ok(client, b, 1, "BEGIN", null, "null");
            d0Ok(client, b, 2, "INSERT", "77", "null");
            d0Kill(b);
            Process c = d1Helper(root, db);
            d0Ok(client, c, 1, "COUNT", null, "1");
            d0Ok(client, c, 2, "SUM", null, "10");
            d0Ok(client, c, 3, "INTEGRITY", null, "true");
            d0Ok(client, c, 4, "STOP", null, "null");
            d0Exit(c);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d1T8CommittedButUnacknowledgedTransactionRemainsUncertain() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-t8-");
        Path db = root.resolve("d1.db");
        runD0(() -> {
            Process client = d0Client();
            Process a = d1Helper(root, db);
            d0Ok(client, a, 1, "INIT", null, "null");
            d0Ok(client, a, 2, "BEGIN", null, "null");
            d0Ok(client, a, 3, "INSERT", "10", "null");
            d0Ok(client, a, 4, "COMMIT", null, "null");
            d0Ok(client, a, 5, "STOP", null, "null");
            d0Exit(a);
            Process b = d1Helper(root, db);
            d0Ok(client, b, 1, "BEGIN", null, "null");
            d0Ok(client, b, 2, "INSERT", "40", "null");
            d0Send(b, d0RequestText(client, 3, "COMMIT_HOLD_ACK", null));
            d0AwaitEvent(b, "COMMITTED_BEFORE_ACK");
            assertEquals(null, d0Stdout.get(b).poll(), "no acknowledgement may precede the continuation");
            d0Kill(b);
            assertEquals("ACCEPTED UNCERTAIN null CONNECTION_LOST", d0Client(client, "LOST", "3"));
            // No automatic retry: the durable state is inspected by another confined helper.
            Process c = d1Helper(root, db);
            d0Ok(client, c, 1, "COUNT", null, "2");
            d0Ok(client, c, 2, "SUM", null, "50");
            d0Ok(client, c, 3, "INTEGRITY", null, "true");
            d0Ok(client, c, 4, "STOP", null, "null");
            d0Exit(c);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d1T9UnsupportedLandlockProfileRefusesExecution() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-t9-");
        runD0(() -> {
            // An impossible minimum ABI: the launcher must refuse before any JVM exists.
            Process refused = d1Launch(root, List.of("--require-abi", "1000000"),
                    D0.resolve("helper.protos"), root.resolve("d1.db").toString());
            String event = d0Events.get(refused).poll(60, TimeUnit.SECONDS);
            assertTrue(event != null && event.startsWith("SANDBOX_REFUSED LANDLOCK_ABI_INSUFFICIENT "),
                    "PID=" + refused.pid() + " event=" + event + "\n" + d0Logs.get(refused));
            assertEquals(D0_EOF, d0Events.get(refused).poll(60, TimeUnit.SECONDS),
                    "no LANDLOCK_APPLIED after a refusal");
            assertEquals(D0_EOF, d0Next(refused), "a refused launch must never publish READY");
            assertTrue(refused.waitFor(30, TimeUnit.SECONDS), "refused launcher termination timeout");
            assertEquals(69, refused.exitValue(), "an unsupported profile is a non-zero refusal");
            assertEquals(List.of(), directoryNames(root), "no SQLite operation may have run");
            assertEquals(1, d0Stdout.size(), "the harness must not retry without restriction");
            return null;
        });
    }

    @Test
    void d1T10HardlinkDoesNotProvideExclusiveDatabaseIdentity(TestReporter reporter) throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-t10-grant-");
        Path outside = d1Dir("d1-t10-exterior-");
        Path exterior = outside.resolve("exterior.db");
        Path alias = root.resolve("alias.db");
        runD0(() -> {
            Process client = d0Client();
            d1Seed(client, exterior, 9);
            // Pre-existing alias created by the unrestricted harness, as in B6-T4.
            Files.createLink(alias, exterior);
            assertTrue(Files.isSameFile(alias, exterior), "the alias must be the same physical inode");
            Map<String, String> before = d1Snapshot(outside);
            assertEquals(List.of(D1_AUTHORIZED, D1_EXTERIOR_OPENED), d1Probe(null, alias, exterior));
            List<String> restricted = d1Probe(root, alias, exterior);
            assertEquals(2, restricted.size(), restricted.toString());
            assertEquals(D1_EXTERIOR_DENIED, restricted.get(1), "the direct exterior path must be denied");
            String through = restricted.get(0);
            assertTrue(through.equals(D1_AUTHORIZED) || through.equals("AUTHORIZED_FAILED"), through);
            if (through.equals(D1_AUTHORIZED)) {
                reporter.publishEntry("D1-T10", "KNOWN LIMITATION of the per-directory authorization "
                        + "unit: a pre-existing hardlink inside the grant opened the exterior inode; "
                        + "Landlock authorizes paths, not database identity");
            } else {
                reporter.publishEntry("D1-T10", "hardlink alias denied on this kernel: " + through);
            }
            assertEquals(before, d1Snapshot(outside), "the read-only probe must not modify the inode");
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d1T11NoResourceOrProcessSurvivesNormalClose() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-t11-");
        Path db = root.resolve("d1.db");
        runD0(() -> {
            Process client = d0Client();
            Process a = d1Helper(root, db);
            d0Ok(client, a, 1, "INIT", null, "null");
            d0Ok(client, a, 2, "BEGIN", null, "null");
            d0Ok(client, a, 3, "INSERT", "4", "null");
            d0Ok(client, a, 4, "COMMIT", null, "null");
            assertEquals(0, a.toHandle().descendants().count(), "the confined helper spawns no process");
            d0Ok(client, a, 5, "CLOSE", null, "null");
            d0Ok(client, a, 6, "STOP", null, "null");
            d0Exit(a);
            assertFalse(ProcessHandle.of(a.pid()).map(ProcessHandle::isAlive).orElse(false),
                    "no surviving helper process");
            assertEquals(List.of("d1.db"), directoryNames(root), "no journal survives a clean close");
            Process b = d1Helper(root, db);
            d0Ok(client, b, 1, "COUNT", null, "1");
            d0Ok(client, b, 2, "SUM", null, "4");
            d0Ok(client, b, 3, "INTEGRITY", null, "true");
            d0Ok(client, b, 4, "STOP", null, "null");
            d0Exit(b);
            for (Process child : d0Stdout.keySet()) {
                if (child != client) assertFalse(child.isAlive(), "PID=" + child.pid() + " survived");
            }
            // Only scalar protocol values ever reached the client: each reply above was decoded
            // and correlated by the Protos client as an exact OK/ERROR value.
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d1T12ConfinedHelperPreservesD0ProtocolAndActorSemantics() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-t12-");
        runD0(() -> {
            Process client = d0Client();
            Process a = d1Helper(root, root.resolve("d1.db"));
            d0Ok(client, a, 1, "PING", null, "0");
            d0Raw(client, a, "{\"version\":1,", "ACCEPTED null ERROR null MALFORMED");
            d0Ok(client, a, 2, "INIT", null, "null");
            d0Ok(client, a, 3, "BEGIN", null, "null");
            d0Ok(client, a, 4, "INSERT", "11", "null");
            d0Expect(client, a, 4, "INSERT", "9", "ACCEPTED ERROR null NON_INCREASING_ID");
            d0Expect(client, a, 3, "COUNT", null, "ACCEPTED ERROR null NON_INCREASING_ID");
            d0Ok(client, a, 5, "INSERT", "31", "null");
            d0Ok(client, a, 6, "COMMIT", null, "null");
            d0Ok(client, a, 7, "COUNT", null, "2");
            d0Ok(client, a, 8, "SUM", null, "42");
            d0Expect(client, a, 9, "EVAL", null, "ACCEPTED ERROR null UNSUPPORTED_OPERATION");
            d0Expect(client, a, 10, "INSERT", null, "ACCEPTED ERROR null INVALID_VALUE");
            d0Ok(client, a, 11, "CLOSE", null, "null");
            d0Expect(client, a, 12, "COUNT", null, "ACCEPTED ERROR null CLOSED");
            d0Expect(client, a, 13, "INSERT", "1", "ACCEPTED ERROR null CLOSED");
            d0Ok(client, a, 14, "CLOSE", null, "null");
            d0Ok(client, a, 15, "STOP", null, "null");
            d0Exit(a);
            d0CloseClient(client);
            return null;
        });
    }

    // ---- PLAT074-D1-R1: extended Landlock profile (ABI 10 contract) and its falsification.
    // isolated/native/authority-probe.c performs one OS operation per mode. Every restricted
    // claim is paired with an unrestricted control of the same operation that succeeds first;
    // a restricted result counts only as an authorization errno (EACCES/EPERM) after the
    // launcher printed the full profile. A missing listener, ECONNREFUSED, ESRCH or a lost
    // datagram is never accepted as isolation. The Protos SQLite helper is unchanged.

    private static final String D1R1_PROFILE =
            "LANDLOCK_PROFILE abi=10 handled_fs=0x1ffff handled_net=0xf scoped=0x3";
    private static final String D1R1_FD = "9";
    private static final String D1R1_MARKER = "D1R1-FD-MARKER";
    private static Path d1r1Probe;

    @Test
    void d1R1T1FullSupportedLandlockProfileIsApplied() throws Exception {
        d1RequireProfile();
        assertTrue(d1Abi >= 10, "the D1-R1 profile requires Landlock ABI 10, found " + d1Abi);
        Path root = d1Dir("d1-r1-t1-grant-");
        Path outside = d1Dir("d1-r1-t1-exterior-");
        Path exterior = outside.resolve("exterior.db");
        runD0(() -> {
            Process client = d0Client();
            d1Seed(client, exterior, 5);
            Process helper = d1Helper(root, root.resolve("d1.db"));
            // Diagnostic: all FS rights through ABI 10, TCP+UDP, abstract-UNIX and signal scopes.
            String log;
            synchronized (d0Logs.get(helper)) { log = d0Logs.get(helper).toString(); }
            assertTrue(log.contains(D1R1_PROFILE + "\n"), log);
            assertTrue(log.indexOf(D1R1_PROFILE) < log.indexOf("LANDLOCK_APPLIED"), log);
            // Real allowed operations in the JVM booted after the restriction.
            d0Ok(client, helper, 1, "INIT", null, "null");
            d0Ok(client, helper, 2, "BEGIN", null, "null");
            d0Ok(client, helper, 3, "INSERT", "3", "null");
            d0Ok(client, helper, 4, "COMMIT", null, "null");
            d0Ok(client, helper, 5, "COUNT", null, "1");
            d0Ok(client, helper, 6, "STOP", null, "null");
            d0Exit(helper);
            // Real allowed and denied native SQLite opens under the same extended profile.
            assertEquals(List.of(D1_AUTHORIZED, D1_EXTERIOR_DENIED),
                    d1Probe(root, root.resolve("authorized.db"), exterior));
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d1R1T2TcpConnectOutsideDomainIsDenied() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-r1-t2-");
        try (var server = java.nio.channels.ServerSocketChannel.open()) {
            server.bind(new java.net.InetSocketAddress(java.net.InetAddress.getByName("127.0.0.1"), 0));
            server.configureBlocking(false);
            String port = Integer.toString(((java.net.InetSocketAddress) server.getLocalAddress()).getPort());
            d1r1Expect(d1r1Run(d1r1Control("tcp-connect", port)), "TCP_ALLOWED");
            // connect() completed the loopback handshake: the connection is already queued.
            try (var accepted = server.accept()) {
                assertTrue(accepted != null, "the control connection must reach the listener");
            }
            d1r1Denied(d1r1Run(d1r1Confined(root, List.of(), "tcp-connect", port)), "TCP");
            assertEquals(null, server.accept(), "no connection may arrive from the restricted probe");
        }
    }

    @Test
    void d1R1T3UdpSendOutsideDomainIsDenied() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-r1-t3-");
        try (var receiver = java.nio.channels.DatagramChannel.open(java.net.StandardProtocolFamily.INET)) {
            receiver.bind(new java.net.InetSocketAddress(java.net.InetAddress.getByName("127.0.0.1"), 0));
            receiver.configureBlocking(false);
            String port = Integer.toString(((java.net.InetSocketAddress) receiver.getLocalAddress()).getPort());
            d1r1Expect(d1r1Run(d1r1Control("udp-send", port)), "UDP_SENT");
            // Loopback delivery is complete when sendto returns: the datagram is queued.
            var buffer = java.nio.ByteBuffer.allocate(64);
            assertTrue(receiver.receive(buffer) != null, "the control datagram must be received");
            buffer.flip();
            assertEquals("PROBE", StandardCharsets.US_ASCII.decode(buffer).toString());
            // The restricted claim is the authorization errno, not the absence of a datagram.
            d1r1Denied(d1r1Run(d1r1Confined(root, List.of(), "udp-send", port)), "UDP");
            buffer.clear();
            assertEquals(null, receiver.receive(buffer), "no datagram may arrive from the restricted probe");
        }
    }

    @Test
    void d1R1T4PathnameUnixSocketOutsideDomainIsDenied() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-r1-t4-grant-");
        Path sockets = d1Dir("d1-r1-t4-socket-");
        Path socket = sockets.resolve("s.sock");
        try (var server = java.nio.channels.ServerSocketChannel.open(java.net.StandardProtocolFamily.UNIX)) {
            server.bind(java.net.UnixDomainSocketAddress.of(socket));
            server.configureBlocking(false);
            d1r1Expect(d1r1Run(d1r1Control("unix-path-connect", socket.toString())), "UNIX_PATH_CONNECTED");
            try (var accepted = server.accept()) {
                assertTrue(accepted != null, "the control connection must reach the socket");
            }
            // The socket directory is deliberately readable (--ro) and the probe stat()s the
            // socket before connecting: only RESOLVE_UNIX can produce the denial.
            d1r1Denied(d1r1Run(d1r1Confined(root, List.of(sockets.toString()), "unix-path-connect",
                    socket.toString())), "UNIX_PATH");
            assertEquals(null, server.accept(), "no connection may arrive from the restricted probe");
            assertTrue(Files.exists(socket), "the socket must still exist");
        } finally {
            Files.deleteIfExists(socket);
        }
    }

    @Test
    void d1R1T5AbstractUnixSocketOutsideDomainIsDenied() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-r1-t5-");
        String name = "protos-d1r1-" + ProcessHandle.current().pid() + "-" + System.nanoTime();
        ProcessBuilder builder = new ProcessBuilder(d1r1Control("unix-abstract-listen", name))
                .directory(work.toFile()).redirectErrorStream(true);
        builder.environment().clear();
        Process listener = builder.start();
        var lines = new java.util.concurrent.LinkedBlockingQueue<String>();
        Thread.ofPlatform().daemon().name("d1r1-listener-" + listener.pid()).start(() -> {
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(listener.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null; ) lines.offer(line);
            } catch (Exception failure) {
                lines.offer("DRAIN_FAILED " + failure);
            } finally {
                lines.offer(D0_EOF);
            }
        });
        try {
            assertEquals("READY", lines.poll(30, TimeUnit.SECONDS), "listener READY");
            d1r1Expect(d1r1Run(d1r1Control("unix-abstract-connect", name)), "UNIX_ABSTRACT_CONNECTED");
            assertEquals("ACCEPTED", lines.poll(30, TimeUnit.SECONDS), "control connection accepted");
            d1r1Denied(d1r1Run(d1r1Confined(root, List.of(), "unix-abstract-connect", name)), "UNIX_ABSTRACT");
            // Closing stdin makes the listener drain pending connections and report the total.
            listener.getOutputStream().close();
            assertEquals("CLOSED total=1", lines.poll(30, TimeUnit.SECONDS),
                    "the restricted probe must not have reached the listener");
            assertTrue(listener.waitFor(30, TimeUnit.SECONDS), "listener termination timeout");
            assertEquals(0, listener.exitValue());
        } finally {
            if (listener.isAlive()) listener.destroyForcibly();
            listener.waitFor(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void d1R1T6SignalsCannotEscapeTheHelperDomain() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-r1-t6-");
        // A live same-user process outside the domain: this JUnit JVM. Signal 0 only checks
        // permission; nothing is delivered and the target is not modified.
        String target = Long.toString(ProcessHandle.current().pid());
        d1r1Expect(d1r1Run(d1r1Control("signal-check", target)), "SIGNAL_ALLOWED");
        // SIGNAL_NO_TARGET (ESRCH) would fail d1r1Denied: it is never taken for a scope denial.
        d1r1Denied(d1r1Run(d1r1Confined(root, List.of(), "signal-check", target)), "SIGNAL");
        assertTrue(ProcessHandle.current().isAlive());
    }

    @Test
    void d1R1T7AuthorizedProtosSqliteSurvivesExpandedRestrictions() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-r1-t7-");
        Path db = root.resolve("d1.db");
        runD0(() -> {
            Process client = d0Client();
            Process a = d1Helper(root, db);
            String log;
            synchronized (d0Logs.get(a)) { log = d0Logs.get(a).toString(); }
            assertTrue(log.contains(D1R1_PROFILE + "\n"), log);
            d0Ok(client, a, 1, "INIT", null, "null");
            d0Ok(client, a, 2, "BEGIN", null, "null");
            d0Ok(client, a, 3, "INSERT", "12", "null");
            d0Ok(client, a, 4, "INSERT", "30", "null");
            d0Ok(client, a, 5, "COMMIT", null, "null");
            d0Ok(client, a, 6, "COUNT", null, "2");
            d0Ok(client, a, 7, "SUM", null, "42");
            d0Ok(client, a, 8, "INTEGRITY", null, "true");
            d0Ok(client, a, 9, "CLOSE", null, "null");
            d0Ok(client, a, 10, "STOP", null, "null");
            d0Exit(a);
            Process b = d1Helper(root, db);
            assertNotEquals(a.pid(), b.pid());
            d0Ok(client, b, 1, "COUNT", null, "2");
            d0Ok(client, b, 2, "SUM", null, "42");
            d0Ok(client, b, 3, "INTEGRITY", null, "true");
            d0Ok(client, b, 4, "STOP", null, "null");
            d0Exit(b);
            d0CloseClient(client);
            assertEquals(List.of("d1.db"), directoryNames(root), "no journal survives a clean close");
            return null;
        });
    }

    @Test
    void d1R1T8InheritedExteriorFileDescriptorIsClosedBeforeExec() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-r1-t8-grant-");
        Path outside = d1Dir("d1-r1-t8-exterior-");
        Path authority = outside.resolve("authority.txt");
        Files.writeString(authority, D1R1_MARKER, StandardCharsets.US_ASCII);
        List<String> preopen = List.of(d1r1Probe().toString(), "preopen-exec", authority.toString(), D1R1_FD, "--");
        // Control without the launcher: the fixture's descriptor survives execve (no CLOEXEC).
        List<String> control = new ArrayList<>(preopen);
        control.addAll(d1r1Control("fd-check", D1R1_FD));
        String[] open = d1r1Run(control);
        d1r1Expect(open, "FD_OPEN_READ " + D1R1_MARKER);
        assertTrue(open[2].contains("PREOPENED fd=" + D1R1_FD), open[2]);
        // Through the launcher: the same inheritable descriptor reaches sandbox-exec, which
        // closes it with close_range before exec'ing the probe.
        List<String> confined = new ArrayList<>(preopen);
        confined.addAll(d1r1Confined(root, List.of(), "fd-check", D1R1_FD));
        String[] closed = d1r1Run(confined);
        assertEquals("0", closed[0], String.join("\n", closed));
        int preopened = closed[2].indexOf("PREOPENED fd=" + D1R1_FD);
        int applied = closed[2].indexOf("LANDLOCK_APPLIED");
        assertTrue(preopened >= 0 && applied > preopened, closed[2]);
        // fd-check ran in the exec'd image: the descriptor no longer exists (EBADF).
        assertEquals("FD_CLOSED", closed[1], String.join("\n", closed));
        assertEquals(D1R1_MARKER, Files.readString(authority, StandardCharsets.US_ASCII));
    }

    @Test
    void d1R1T9UnsafeEnvironmentVariablesAreRejected() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-r1-t9-");
        Path db = root.resolve("d1.db");
        runD0(() -> {
            Process client = d0Client();
            // Appended to the exact harness environment; the JUnit environment is never touched.
            String[][] appended = {
                {"JAVA_TOOL_OPTIONS=-Xmx1m", "ENV_FORBIDDEN JAVA_TOOL_OPTIONS"},
                {"_JAVA_OPTIONS=-Xmx1m", "ENV_FORBIDDEN _JAVA_OPTIONS"},
                {"JDK_JAVA_OPTIONS=-Xmx1m", "ENV_FORBIDDEN JDK_JAVA_OPTIONS"},
                {"LD_PRELOAD=/nonexistent/preload.so", "ENV_FORBIDDEN LD_PRELOAD"},
                {"LD_LIBRARY_PATH=/nonexistent", "ENV_FORBIDDEN LD_LIBRARY_PATH"},
                {"LD_AUDIT=/nonexistent/audit.so", "ENV_FORBIDDEN LD_AUDIT"},
                {"GCONV_PATH=/nonexistent", "ENV_FORBIDDEN GCONV_PATH"},
                {"PROTOS_EXTRA=1", "ENV_UNKNOWN PROTOS_EXTRA"},
                {"LANG=C", "ENV_DUPLICATE LANG"},
                {"LANG", "ENV_MISSING_SEPARATOR LANG"},
                {"=value", "ENV_EMPTY_NAME =value"},
            };
            for (String[] entry : appended) {
                Path tmp = d1Dir("d1-tmp-");
                List<String> environment = new ArrayList<>(d1Environment(tmp));
                environment.add(entry[0]);
                d1r1Refused(d1LaunchWith(root, tmp, List.of(), environment, D0.resolve("helper.protos"),
                        db.toString()), "SANDBOX_REFUSED " + entry[1], 64);
            }
            // Replaced entries: an invalid value and a HOME outside the private temporary root.
            String[][] replaced = {
                {"LANG=", "ENV_INVALID_VALUE LANG"},
                {"HOME=" + root, "ENV_NOT_PRIVATE_TMP HOME"},
            };
            for (String[] entry : replaced) {
                Path tmp = d1Dir("d1-tmp-");
                String name = entry[0].substring(0, entry[0].indexOf('=') + 1);
                List<String> environment = new ArrayList<>();
                for (String variable : d1Environment(tmp)) {
                    environment.add(variable.startsWith(name) ? entry[0] : variable);
                }
                d1r1Refused(d1LaunchWith(root, tmp, List.of(), environment, D0.resolve("helper.protos"),
                        db.toString()), "SANDBOX_REFUSED " + entry[1], 64);
            }
            assertEquals(List.of(), directoryNames(root), "no refused launch may reach SQLite");
            // The exact authorized environment still boots the helper normally.
            Process helper = d1Helper(root, db);
            d0Ok(client, helper, 1, "PING", null, "0");
            d0Ok(client, helper, 2, "STOP", null, "null");
            d0Exit(helper);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d1R1T10HardlinkOutcomeIsAssertedAndRecorded(TestReporter reporter) throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-r1-t10-grant-");
        Path outside = d1Dir("d1-r1-t10-exterior-");
        Path exterior = outside.resolve("exterior.db");
        Path alias = root.resolve("alias.db");
        runD0(() -> {
            Process client = d0Client();
            d1Seed(client, exterior, 9);
            Files.createLink(alias, exterior);
            assertTrue(Files.isSameFile(alias, exterior), "the alias must be the same physical inode");
            Map<String, String> before = d1Snapshot(outside);
            assertEquals(List.of(D1_AUTHORIZED, D1_EXTERIOR_OPENED), d1Probe(null, alias, exterior));
            // Landlock evaluates the path walked, not the inode: the interior name is beneath the
            // grant and is allowed; the exterior name of the same inode is denied.
            assertEquals(List.of(D1_AUTHORIZED, D1_EXTERIOR_DENIED), d1Probe(root, alias, exterior));
            reporter.publishEntry("D1-R1-T10", "hardlink alias inside the grant OPENED the exterior inode; "
                    + "the per-directory grant does not establish exclusive database identity");
            assertEquals(before, d1Snapshot(outside), "the read-only probe must not modify the inode");
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d1R1T11UnsupportedSecurityProfileRefusesWithoutFallback() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-r1-t11-");
        Path db = root.resolve("d1.db");
        runD0(() -> {
            // A requirement below the profile can never select a weaker (filesystem-only) profile.
            d1r1Refused(d1Launch(root, List.of("--require-abi", "9"), D0.resolve("helper.protos"),
                    db.toString()), "SANDBOX_REFUSED ABI_BELOW_PROFILE_FLOOR 9", 64);
            // A requirement beyond the reviewed ABI 10 contract claims unimplemented guarantees.
            long beyond = Math.max(d1Abi, 11);
            String expected = d1Abi >= 11 ? "SANDBOX_REFUSED PROFILE_DOES_NOT_COVER_ABI "
                    : "SANDBOX_REFUSED LANDLOCK_ABI_INSUFFICIENT ";
            d1r1Refused(d1Launch(root, List.of("--require-abi", Long.toString(beyond)),
                    D0.resolve("helper.protos"), db.toString()), expected, 69);
            assertEquals(List.of(), directoryNames(root), "no SQLite operation may have run");
            assertEquals(2, d0Stdout.size(), "the harness must not retry with a weaker profile");
            return null;
        });
    }

    @Test
    void d1R1T12ClientDeathDoesNotYetProvideHelperRevocation(TestReporter reporter) throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-r1-t12-");
        runD0(() -> {
            Process client = d0Client();
            Process helper = d1Helper(root, root.resolve("d1.db"));
            d0Ok(client, helper, 1, "INIT", null, "null");
            client.destroyForcibly();
            assertTrue(client.waitFor(30, TimeUnit.SECONDS), "client SIGKILL timeout");
            assertEquals(137, client.exitValue());
            // Negative architecture control, not a guarantee: the JUnit harness, not the Protos
            // client, owns the helper process, so the client's death revokes nothing.
            assertTrue(helper.isAlive(), "expected the helper to outlive its client (D-10 unresolved)");
            assertEquals(null, d0Stdout.get(helper).poll(), "no unsolicited helper output");
            reporter.publishEntry("D1-R1-T12", "D-10 UNRESOLVED: confined helper PID=" + helper.pid()
                    + " survived the forced death of client PID=" + client.pid()
                    + "; it was terminated by the JUnit harness, not by client-death revocation");
            d0Kill(helper);
            return null;
        });
    }

    @Test
    void d1R1T13ParentCanTerminateConfinedHelper() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d1-r1-t13-");
        Path db = root.resolve("d1.db");
        runD0(() -> {
            Process client = d0Client();
            Process a = d1Helper(root, db);
            d0Ok(client, a, 1, "INIT", null, "null");
            d0Ok(client, a, 2, "BEGIN", null, "null");
            d0Ok(client, a, 3, "INSERT", "21", "null");
            d0Ok(client, a, 4, "COMMIT", null, "null");
            // The signal scope restricts signals sent by the domain, not to it: the
            // unrestricted harness still kills the helper (no guest cleanup is implied).
            d0Kill(a);
            assertFalse(ProcessHandle.of(a.pid()).map(ProcessHandle::isAlive).orElse(false),
                    "the killed helper must not survive");
            Process b = d1Helper(root, db);
            d0Ok(client, b, 1, "COUNT", null, "1");
            d0Ok(client, b, 2, "SUM", null, "21");
            d0Ok(client, b, 3, "INTEGRITY", null, "true");
            d0Ok(client, b, 4, "STOP", null, "null");
            d0Exit(b);
            d0CloseClient(client);
            return null;
        });
    }

    /** Compiles the D1-R1 OS-authority probe next to the launcher, once. */
    private static Path d1r1Probe() throws Exception {
        if (d1r1Probe == null) d1r1Probe = d1Compile("authority-probe");
        return d1r1Probe;
    }

    /** Unrestricted control: the probe exec'd directly (d1r1Run clears the environment). */
    private static List<String> d1r1Control(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of(d1r1Probe().toString()));
        command.addAll(List.of(arguments));
        return command;
    }

    /** Restricted probe: the shared D1 policy; the probe directory is the only extra --rx. */
    private static List<String> d1r1Confined(Path grant, List<String> extraRo, String... arguments)
            throws Exception {
        Path tmp = d1Dir("d1-tmp-");
        List<String> command = d1Policy(grant, tmp, List.of(), List.of(d1r1Probe().getParent().toString()),
                extraRo, d1Environment(tmp));
        command.add(d1r1Probe().toString());
        command.addAll(List.of(arguments));
        return command;
    }

    /**
     * Runs one native command to completion with an empty environment and closed stdin; stdout and
     * stderr are drained concurrently. Returns {exit status, stripped stdout, stderr}.
     */
    private static String[] d1r1Run(List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).directory(work.toFile());
        builder.environment().clear();
        Process process = builder.start();
        try {
            process.getOutputStream().close();
            var stdout = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> d1r1Drain(process.getInputStream()));
            var stderr = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> d1r1Drain(process.getErrorStream()));
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "native command timeout: " + command);
            return new String[] {Integer.toString(process.exitValue()),
                    stdout.get(30, TimeUnit.SECONDS).strip(), stderr.get(30, TimeUnit.SECONDS)};
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            process.waitFor(15, TimeUnit.SECONDS);
        }
    }

    private static String d1r1Drain(java.io.InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            return "DRAIN_FAILED " + failure;
        }
    }

    /** An unrestricted control: exit 0 and exactly {@code expected}. */
    private static void d1r1Expect(String[] result, String expected) {
        assertEquals("0", result[0], String.join("\n", result));
        assertEquals(expected, result[1], String.join("\n", result));
    }

    /** A restricted probe: full profile installed, then an authorization errno for the operation. */
    private static void d1r1Denied(String[] result, String prefix) {
        String all = String.join("\n", result);
        assertEquals("0", result[0], all);
        assertTrue(result[2].contains(D1R1_PROFILE + "\n") && result[2].contains("LANDLOCK_APPLIED\n"), all);
        assertTrue(result[1].startsWith(prefix + "_DENIED errno="), all);
    }

    /** A refused launch: one SANDBOX_REFUSED event, no LANDLOCK_APPLIED, no READY, given status. */
    private static void d1r1Refused(Process refused, String expectedPrefix, int status) throws Exception {
        String event = d0Events.get(refused).poll(60, TimeUnit.SECONDS);
        assertTrue(event != null && event.startsWith(expectedPrefix),
                "PID=" + refused.pid() + " event=" + event + "\n" + d0Logs.get(refused));
        assertEquals(D0_EOF, d0Events.get(refused).poll(60, TimeUnit.SECONDS),
                "no LANDLOCK_APPLIED after a refusal");
        assertEquals(D0_EOF, d0Next(refused), "a refused launch must never publish READY");
        assertTrue(refused.waitFor(30, TimeUnit.SECONDS), "refused launcher termination timeout");
        assertEquals(status, refused.exitValue());
    }

    /** UNSUPPORTED (assumption, never PASS) when the platform cannot install the D1 profile. */
    private static void d1RequireProfile() throws Exception {
        Path launcher = d1Launcher();
        if (!d1Checked) {
            Process check = new ProcessBuilder(launcher.toString(), "--abi-only")
                    .redirectErrorStream(true).start();
            String output = new String(check.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            assertTrue(check.waitFor(30, TimeUnit.SECONDS), "launcher ABI check timed out");
            if (check.exitValue() == 69) {
                d1Unsupported = output;
            } else {
                assertEquals(0, check.exitValue(), output);
                assertTrue(output.startsWith("LANDLOCK_ABI "), output);
                d1Abi = Long.parseLong(output.substring("LANDLOCK_ABI ".length()).strip());
            }
            d1Checked = true;
        }
        assumeTrue(d1Unsupported == null, "UNSUPPORTED: " + d1Unsupported);
    }

    /** Compiles the experimental launcher into the test temporary directory, once. */
    private static Path d1Launcher() throws Exception {
        if (d1Launcher == null) d1Launcher = d1Compile("sandbox-exec");
        return d1Launcher;
    }

    /** Compiles one isolated/native fixture into the shared native directory. */
    private static Path d1Compile(String name) throws Exception {
        Path compiler = findCompiler();
        assumeTrue(compiler != null, "UNSUPPORTED: no C compiler for the D1 native fixtures");
        Path output = Files.createDirectories(work.resolve("d1-native")).resolve(name);
        Process cc = new ProcessBuilder(compiler.toString(), "-O2", "-std=c11", "-Wall", "-Werror",
                        "-o", output.toString(), D0.resolve("native").resolve(name + ".c").toString())
                .redirectErrorStream(true).start();
        String ccOutput = new String(cc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(cc.waitFor(60, TimeUnit.SECONDS), "cc timed out");
        assertEquals(0, cc.exitValue(), ccOutput);
        return output.toRealPath();
    }

    private static Path d1Dir(String prefix) throws Exception {
        return Files.createTempDirectory(work, prefix).toRealPath();
    }

    /**
     * Launches {@code entry} behind the launcher. {@code grant} is the only SQLite directory; a fresh
     * private temporary directory is created per process. Every read path is listed explicitly by
     * this host; none comes from helper IPC.
     */
    private static Process d1Launch(Path grant, List<String> launcherOptions, Path entry,
            String... arguments) throws Exception {
        Path tmp = d1Dir("d1-tmp-");
        return d1LaunchWith(grant, tmp, launcherOptions, d1Environment(tmp), entry, arguments);
    }

    /** {@link #d1Launch} with an explicit launcher environment (D1-R1-T9 adversarial entries). */
    private static Process d1LaunchWith(Path grant, Path tmp, List<String> launcherOptions,
            List<String> environment, Path entry, String... arguments) throws Exception {
        List<String> sandbox = d1Policy(grant, tmp, launcherOptions, List.of(), List.of(), environment);
        List<String> jvm = List.of("-XX:-UsePerfData", "-Djava.io.tmpdir=" + tmp, "-Duser.home=" + tmp,
                "-Dpolyglot.engine.userResourceCache=" + tmp.resolve("polyglot-cache"));
        return d0Start(sandbox, jvm, entry, true, arguments);
    }

    /** The exact harness environment: the five allowlisted names, HOME/TMPDIR = private tmp. */
    private static List<String> d1Environment(Path tmp) throws Exception {
        Path home = Path.of("").toAbsolutePath().toRealPath();
        return List.of("PROTOS_HOME=" + home, "HOME=" + tmp, "TMPDIR=" + tmp, "LANG=C.UTF-8",
                "PATH=/usr/bin:/bin");
    }

    /**
     * The launcher prefix shared by the Protos JVM and the D1-R1 native probe: one policy builder,
     * so both run under identical grants. {@code extraRx}/{@code extraRo} only add explicit host
     * paths (the probe directory, a socket directory); the result ends with {@code --}.
     */
    private static List<String> d1Policy(Path grant, Path tmp, List<String> launcherOptions,
            List<String> extraRx, List<String> extraRo, List<String> environment) throws Exception {
        Path home = Path.of("").toAbsolutePath().toRealPath();
        List<String> sandbox = new ArrayList<>(List.of(d1Launcher().toString()));
        sandbox.addAll(launcherOptions);
        sandbox.addAll(List.of("--db", grant.toRealPath().toString(), "--tmp", tmp.toString()));
        sandbox.addAll(List.of("--rx", Path.of(System.getProperty("java.home")).toRealPath().toString()));
        for (String path : extraRx) sandbox.addAll(List.of("--rx", path));
        Path interpreter = Path.of("/lib64/ld-linux-x86-64.so.2");
        if (Files.exists(interpreter)) sandbox.addAll(List.of("--rx", interpreter.toRealPath().toString()));
        java.util.Set<String> readable = new java.util.LinkedHashSet<>();
        for (String library : List.of("/usr/lib64", "/usr/lib")) {
            if (Files.isDirectory(Path.of(library))) readable.add(Path.of(library).toRealPath().toString());
        }
        String classpath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        for (String element : classpath.split(File.pathSeparator)) {
            if (!element.isBlank() && Files.exists(Path.of(element))) {
                readable.add(Path.of(element).toRealPath().toString());
            }
        }
        readable.add(sqliteJar.toRealPath().toString());
        readable.add(home.resolve("protos").resolve("lib").toRealPath().toString());
        readable.add(D0.toAbsolutePath().toRealPath().toString());
        for (String system : List.of("/etc/ld.so.cache", "/etc/localtime", "/etc/nsswitch.conf",
                "/etc/passwd", "/etc/group", "/proc", "/sys", "/dev/urandom", "/dev/random")) {
            if (Files.exists(Path.of(system))) readable.add(Path.of(system).toRealPath().toString());
        }
        readable.addAll(extraRo);
        for (String path : readable) sandbox.addAll(List.of("--ro", path));
        sandbox.addAll(List.of("--dev", "/dev/null"));
        for (String variable : environment) sandbox.addAll(List.of("--env", variable));
        sandbox.add("--");
        return sandbox;
    }

    /** A confined helper: LANDLOCK_APPLIED must precede the Protos READY event. */
    private static Process d1Helper(Path grant, Path db) throws Exception {
        assertTrue(db.isAbsolute(), "D1 database path must be absolute");
        Process helper = d1Launch(grant, List.of(), D0.resolve("helper.protos"), db.toString());
        d0AwaitEvent(helper, "LANDLOCK_APPLIED");
        Process client = d0Stdout.keySet().iterator().next();
        assertEquals("ACCEPTED", d0Client(client, "READY", d0Next(helper)),
                "the confined helper must publish the Protos READY event first");
        return helper;
    }

    /** Runs isolation-probe.protos, confined to {@code grant} or unrestricted when null. */
    private static List<String> d1Probe(Path grant, Path authorized, Path exterior) throws Exception {
        Path entry = D0.resolve("isolation-probe.protos");
        Process probe = grant == null
                ? d0Launch(entry, true, authorized.toString(), exterior.toString())
                : d1Launch(grant, List.of(), entry, authorized.toString(), exterior.toString());
        if (grant != null) d0AwaitEvent(probe, "LANDLOCK_APPLIED");
        List<String> lines = new ArrayList<>();
        for (String line = d0Next(probe); !line.equals(D0_EOF); line = d0Next(probe)) lines.add(line);
        assertTrue(probe.waitFor(60, TimeUnit.SECONDS), "probe termination timeout");
        assertEquals(0, probe.exitValue(), "PID=" + probe.pid() + "\n" + d0Logs.get(probe));
        return lines;
    }

    /** Creates a valid committed database through an unrestricted D0 helper. */
    private static void d1Seed(Process client, Path db, long value) throws Exception {
        Process seeder = d0Helper(db);
        d0Ok(client, seeder, 1, "INIT", null, "null");
        d0Ok(client, seeder, 2, "BEGIN", null, "null");
        d0Ok(client, seeder, 3, "INSERT", Long.toString(value), "null");
        d0Ok(client, seeder, 4, "COMMIT", null, "null");
        d0Ok(client, seeder, 5, "STOP", null, "null");
        d0Exit(seeder);
    }

    /** Names, sizes, modification times and SHA-256 digests of a directory's entries. */
    private static Map<String, String> d1Snapshot(Path directory) throws Exception {
        Map<String, String> snapshot = new java.util.TreeMap<>();
        for (String name : directoryNames(directory)) {
            Path path = directory.resolve(name);
            byte[] content = Files.readAllBytes(path);
            snapshot.put(name, content.length + ":" + Files.getLastModifiedTime(path).toMillis() + ":"
                    + java.util.HexFormat.of().formatHex(
                            java.security.MessageDigest.getInstance("SHA-256").digest(content)));
        }
        return snapshot;
    }

    // ---- PLAT074-D2: durable operation identity, atomic receipts and uncertain-COMMIT
    // reconciliation. isolated/durable-helper.protos runs under the D1-R1 Landlock launcher;
    // the receipt table, the single-transaction apply, the v2 protocol and the client-side
    // UNCERTAIN/reconciliation logic are Protos. JUnit only launches, relays opaque lines,
    // kills and observes; raw JSON appears here only as adversarial vectors. Transport ids
    // restart with every helper; the durable key is (clientId, operationId).

    @Test
    void d2T1DurableProtocolRejectsInvalidIdentityAndPayload() throws Exception {
        runD0(() -> {
            Process client = d2Client();
            assertEquals("ADMITTED 5 101 7001 APPLY_ONCE 42", d0Client(client, "DECODE_REQUEST",
                    d2Text(client, 5, "101", "7001", "APPLY_ONCE", "42")));
            assertEquals("ADMITTED 1 null null PING null", d0Client(client, "DECODE_REQUEST",
                    d2Text(client, 1, "null", "null", "PING", "null")));
            assertEquals("ADMITTED 3 9223372036854775807 9223372036854775807 LOOKUP null",
                    d0Client(client, "DECODE_REQUEST", d2Text(client, 3, Long.toString(Long.MAX_VALUE),
                            Long.toString(Long.MAX_VALUE), "LOOKUP", "null")));
            assertEquals("ADMITTED 4 1 1 APPLY_ONCE -9223372036854775808", d0Client(client,
                    "DECODE_REQUEST", d2Text(client, 4, "1", "1", "APPLY_ONCE", Long.toString(Long.MIN_VALUE))));
            // The Protos encoder refuses every request outside the v2 contract.
            String[][] unencodable = {
                {"0", "101", "7001", "APPLY_ONCE", "42"},
                {"1", "0", "7001", "APPLY_ONCE", "42"},
                {"1", "-101", "7001", "APPLY_ONCE", "42"},
                {"1", "101", "null", "APPLY_ONCE", "42"},
                {"1", "101", "7001", "APPLY_ONCE", "null"},
                {"1", "101", "9223372036854775808", "APPLY_ONCE", "42"},
                {"1", "101", "7001", "APPLY_ONCE", "9223372036854775808"},
                {"1", "101", "7001", "PING", "null"},
                {"1", "null", "null", "LOOKUP", "null"},
                {"1", "101", "7001", "LOOKUP", "3"},
                {"1", "null", "null", "EXEC", "null"},
            };
            for (String[] vector : unencodable) {
                assertEquals("REJECTED", d0Client(client, "REQUEST", vector[0], vector[1], vector[2],
                        vector[3], vector[4]), String.join(",", vector));
            }
            String[][] refused = {
                {"{\"version\":2,", "REFUSED null MALFORMED"},
                {d2Json("5", "101", "7001", "\"APPLY_ONCE\"", "42").replace("}", ",\"extra\":1}"),
                    "REFUSED null MALFORMED"},
                {"{\"version\":2,\"id\":5,\"operationId\":7001,\"op\":\"APPLY_ONCE\",\"value\":42}",
                    "REFUSED null MALFORMED"},
                {d2Json("5", "101", "7001", "\"APPLY_ONCE\"", "42").replace("\"version\":2", "\"version\":1"),
                    "REFUSED null INVALID_VERSION"},
                {d2Json("1.0", "101", "7001", "\"APPLY_ONCE\"", "42"), "REFUSED null INVALID_ID"},
                {d2Json("-5", "101", "7001", "\"APPLY_ONCE\"", "42"), "REFUSED null INVALID_ID"},
                {d2Json("9223372036854775808", "101", "7001", "\"APPLY_ONCE\"", "42"), "REFUSED null INVALID_ID"},
                {d2Json("5", "101", "7001", "\"EXEC\"", "42"), "REFUSED 5 UNSUPPORTED_OPERATION"},
                {d2Json("5", "101", "7001", "7", "42"), "REFUSED 5 UNSUPPORTED_OPERATION"},
                {d2Json("5", "-101", "7001", "\"APPLY_ONCE\"", "42"), "REFUSED 5 INVALID_IDENTITY"},
                {d2Json("5", "0", "7001", "\"APPLY_ONCE\"", "42"), "REFUSED 5 INVALID_IDENTITY"},
                {d2Json("5", "\"101\"", "7001", "\"APPLY_ONCE\"", "42"), "REFUSED 5 INVALID_IDENTITY"},
                {d2Json("5", "101.5", "7001", "\"APPLY_ONCE\"", "42"), "REFUSED 5 INVALID_IDENTITY"},
                {d2Json("5", "101", "9223372036854775808", "\"APPLY_ONCE\"", "42"), "REFUSED 5 INVALID_IDENTITY"},
                {d2Json("5", "null", "7001", "\"APPLY_ONCE\"", "42"), "REFUSED 5 INVALID_IDENTITY"},
                {d2Json("5", "101", "null", "\"LOOKUP\"", "null"), "REFUSED 5 INVALID_IDENTITY"},
                {d2Json("5", "101", "7001", "\"PING\"", "null"), "REFUSED 5 INVALID_IDENTITY"},
                {d2Json("5", "101", "7001", "\"APPLY_ONCE\"", "1.5"), "REFUSED 5 INVALID_VALUE"},
                {d2Json("5", "101", "7001", "\"APPLY_ONCE\"", "\"42\""), "REFUSED 5 INVALID_VALUE"},
                {d2Json("5", "101", "7001", "\"APPLY_ONCE\"", "true"), "REFUSED 5 INVALID_VALUE"},
                {d2Json("5", "101", "7001", "\"APPLY_ONCE\"", "null"), "REFUSED 5 INVALID_VALUE"},
                {d2Json("5", "101", "7001", "\"APPLY_ONCE\"", "9223372036854775808"), "REFUSED 5 INVALID_VALUE"},
                {d2Json("5", "101", "7001", "\"LOOKUP\"", "1"), "REFUSED 5 INVALID_VALUE"},
                {d0Padded(d2Json("9", "null", "null", "\"PING\"", "null").replace("}", ""), 4097),
                    "REFUSED null OVERSIZED"},
            };
            for (String[] vector : refused) {
                assertEquals(vector[1], d0Client(client, "DECODE_REQUEST", vector[0]), vector[0]);
            }
            assertEquals("ADMITTED 9 null null PING null", d0Client(client, "DECODE_REQUEST",
                    d0Padded(d2Json("9", "null", "null", "\"PING\"", "null").replace("}", ""), 4096)));
            // Responses: exact correlation, status-specific value and fixed reasons.
            String applied = d2Reply("5", "APPLIED", "42", "null");
            assertEquals("ACCEPTED APPLIED 42 null", d0Client(client, "MATCH", "5", applied));
            assertEquals("REJECTED", d0Client(client, "MATCH", "6", applied), "uncorrelated reply");
            assertEquals("ACCEPTED ABSENT null null", d0Client(client, "MATCH", "5",
                    d2Reply("5", "ABSENT", "null", "null")));
            assertEquals("ACCEPTED FOUND 0 null", d0Client(client, "MATCH", "5", d2Reply("5", "FOUND", "0", "null")));
            assertEquals("ACCEPTED ERROR null BUSY", d0Client(client, "MATCH", "5",
                    d2Reply("5", "ERROR", "null", "\"BUSY\"")));
            String[] rejectedResponses = {
                d2Reply("5", "UNCERTAIN", "null", "null"),
                d2Reply("5", "ABSENT", "0", "null"),
                d2Reply("5", "FOUND", "null", "null"),
                d2Reply("5", "APPLIED", "1.5", "null"),
                d2Reply("5", "APPLIED", "42", "\"BUSY\""),
                d2Reply("5", "ERROR", "null", "null"),
                d2Reply("5", "ERROR", "1", "\"BUSY\""),
                d2Reply("5", "ERROR", "null", "\"java.lang.IllegalStateException at 0x7f00\""),
                d2Reply("5", "OK", "\"x\"", "null"),
                d2Reply("null", "APPLIED", "42", "null"),
                d2Reply("5", "APPLIED", "42", "null").replace("\"version\":2", "\"version\":1"),
                d2Reply("5", "APPLIED", "42", "null").replace("}", ",\"extra\":0}"),
                "",
                "not json",
            };
            for (String response : rejectedResponses) {
                assertEquals("REJECTED", d0Client(client, "MATCH", "5", response), response);
            }
            assertEquals("ACCEPTED", d0Client(client, "READY", "{\"version\":2,\"event\":\"READY\"}"));
            assertEquals("REJECTED", d0Client(client, "READY", "{\"version\":1,\"event\":\"READY\"}"));
            // Lost replies are client-local UNCERTAIN, tracked per durable key.
            assertEquals("UNCERTAIN 101 7001 CONNECTION_LOST", d0Client(client, "LOST", "5", "101", "7001", "42"));
            assertEquals("STATE UNCERTAIN", d0Client(client, "STATE", "101", "7001"));
            assertEquals("STATE NONE", d0Client(client, "STATE", "101", "7002"));
            assertEquals("REJECTED", d0Client(client, "RECONCILE", "1", "101", "7002"),
                    "only an uncertain key is reconciled");
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T2ApplyOncePersistsEntryAndReceiptAtomically() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t2-");
        Path db = root.resolve("d2.db");
        runD0(() -> {
            Process client = d2Client();
            Process a = d2Helper(client, root, db);
            d2Ok(client, a, 1, null, null, "INIT", null, "null");
            d2Expect(client, a, 2, 101L, 7001L, "APPLY_ONCE", 42L, "APPLIED 42");
            d2State(client, a, 3, 1, 1, 42);
            d2Expect(client, a, 6, 101L, 7001L, "LOOKUP", null, "FOUND 42");
            d2Ok(client, a, 7, null, null, "INTEGRITY", null, "true");
            d2Ok(client, a, 8, null, null, "STOP", null, "null");
            d0Exit(a);
            Process b = d2Helper(client, root, db);
            assertNotEquals(a.pid(), b.pid());
            d2State(client, b, 1, 1, 1, 42);
            d2Expect(client, b, 4, 101L, 7001L, "LOOKUP", null, "FOUND 42");
            d2Ok(client, b, 5, null, null, "INTEGRITY", null, "true");
            d2Ok(client, b, 6, null, null, "STOP", null, "null");
            d0Exit(b);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T3SameKeyReplayAfterRestartDoesNotDuplicate() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t3-");
        Path db = root.resolve("d2.db");
        runD0(() -> {
            Process client = d2Client();
            Process a = d2Helper(client, root, db);
            d2Ok(client, a, 1, null, null, "INIT", null, "null");
            d2Expect(client, a, 2, 101L, 7001L, "APPLY_ONCE", 42L, "APPLIED 42");
            d2Ok(client, a, 3, null, null, "STOP", null, "null");
            d0Exit(a);
            for (int restart = 0; restart < 2; restart++) {
                Process b = d2Helper(client, root, db);
                d2Expect(client, b, 1, 101L, 7001L, "APPLY_ONCE", 42L, "REPLAYED 42");
                d2State(client, b, 2, 1, 1, 42);
                d2Ok(client, b, 5, null, null, "STOP", null, "null");
                d0Exit(b);
            }
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T4ConflictingReplayIsRejectedWithoutWrite() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t4-");
        Path db = root.resolve("d2.db");
        runD0(() -> {
            Process client = d2Client();
            Process a = d2Helper(client, root, db);
            d2Ok(client, a, 1, null, null, "INIT", null, "null");
            d2Expect(client, a, 2, 101L, 7001L, "APPLY_ONCE", 42L, "APPLIED 42");
            d2Expect(client, a, 3, 101L, 7001L, "APPLY_ONCE", 43L, "CONFLICT 42");
            d2Expect(client, a, 4, 101L, 7001L, "LOOKUP", null, "FOUND 42");
            d2State(client, a, 5, 1, 1, 42);
            d2Ok(client, a, 8, null, null, "STOP", null, "null");
            d0Exit(a);
            Process b = d2Helper(client, root, db);
            d2Expect(client, b, 1, 101L, 7001L, "APPLY_ONCE", 43L, "CONFLICT 42");
            d2Expect(client, b, 2, 101L, 7001L, "LOOKUP", null, "FOUND 42");
            d2State(client, b, 3, 1, 1, 42);
            d2Ok(client, b, 6, null, null, "INTEGRITY", null, "true");
            d2Ok(client, b, 7, null, null, "STOP", null, "null");
            d0Exit(b);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T5DifferentClientsMayUseSameOperationNumber() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t5-");
        Path db = root.resolve("d2.db");
        runD0(() -> {
            Process client = d2Client();
            Process a = d2Helper(client, root, db);
            d2Ok(client, a, 1, null, null, "INIT", null, "null");
            d2Expect(client, a, 2, 101L, 7001L, "APPLY_ONCE", 10L, "APPLIED 10");
            d2Expect(client, a, 3, 202L, 7001L, "APPLY_ONCE", 20L, "APPLIED 20");
            d2State(client, a, 4, 2, 2, 30);
            d2Ok(client, a, 7, null, null, "STOP", null, "null");
            d0Exit(a);
            Process b = d2Helper(client, root, db);
            d2Expect(client, b, 1, 101L, 7001L, "LOOKUP", null, "FOUND 10");
            d2Expect(client, b, 2, 202L, 7001L, "LOOKUP", null, "FOUND 20");
            d2State(client, b, 3, 2, 2, 30);
            d2Ok(client, b, 6, null, null, "STOP", null, "null");
            d0Exit(b);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T6SameClientDistinctOperationIdsRemainIndependent() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t6-");
        Path db = root.resolve("d2.db");
        runD0(() -> {
            Process client = d2Client();
            Process a = d2Helper(client, root, db);
            d2Ok(client, a, 1, null, null, "INIT", null, "null");
            d2Expect(client, a, 2, 101L, 1L, "APPLY_ONCE", 1L, "APPLIED 1");
            d2Expect(client, a, 3, 101L, 2L, "APPLY_ONCE", 2L, "APPLIED 2");
            d2Expect(client, a, 4, 101L, 3L, "APPLY_ONCE", 4L, "APPLIED 4");
            d2Expect(client, a, 5, 101L, 2L, "APPLY_ONCE", 2L, "REPLAYED 2");
            d2Expect(client, a, 6, 101L, 4L, "APPLY_ONCE", 8L, "APPLIED 8");
            d2State(client, a, 7, 4, 4, 15);
            d2Ok(client, a, 10, null, null, "STOP", null, "null");
            d0Exit(a);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T7KilledBeforeCommitLeavesNoEntryOrReceipt() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t7-");
        Path db = root.resolve("d2.db");
        runD0(() -> {
            Process client = d2Client();
            d2Initialize(client, root, db);
            Process b = d2Helper(client, root, db);
            d0Send(b, d2Text(client, 1, "101", "7001", "HOLD_BEFORE_COMMIT", "42"));
            d0AwaitEvent(b, "DURABLE_PRECOMMIT");
            assertEquals(null, d0Stdout.get(b).poll(), "no reply may precede COMMIT");
            d0Kill(b);
            assertEquals("UNCERTAIN 101 7001 CONNECTION_LOST", d0Client(client, "LOST", "1", "101", "7001", "42"));
            // D3: d0Kill observed the source's exit 137; ABSENT is final only with that evidence.
            assertEquals("TERMINATED 101 7001", d0Client(client, "SOURCE_TERMINATED", "101", "7001"));
            Process c = d2Helper(client, root, db);
            assertEquals("RESOLVED NOT_APPLIED null", d2Reconcile(client, c, 1, 101, 7001));
            d2State(client, c, 2, 0, 0, 0);
            d2Expect(client, c, 5, 101L, 7001L, "APPLY_ONCE", 42L, "APPLIED 42");
            d2Expect(client, c, 6, 101L, 7001L, "APPLY_ONCE", 42L, "REPLAYED 42");
            d2State(client, c, 7, 1, 1, 42);
            d2Ok(client, c, 10, null, null, "INTEGRITY", null, "true");
            d2Ok(client, c, 11, null, null, "STOP", null, "null");
            d0Exit(c);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T8KilledAfterCommitBeforeAckReconcilesWithoutDuplicate() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t8-");
        Path db = root.resolve("d2.db");
        runD0(() -> {
            Process client = d2Client();
            d2Initialize(client, root, db);
            Process b = d2Helper(client, root, db);
            d0Send(b, d2Text(client, 1, "101", "7001", "HOLD_AFTER_COMMIT", "42"));
            d0AwaitEvent(b, "DURABLE_COMMITTED_BEFORE_ACK");
            assertEquals(null, d0Stdout.get(b).poll(), "no acknowledgement may precede the continuation");
            d0Kill(b);
            assertEquals("UNCERTAIN 101 7001 CONNECTION_LOST", d0Client(client, "LOST", "1", "101", "7001", "42"));
            assertEquals("STATE UNCERTAIN", d0Client(client, "STATE", "101", "7001"));
            Process c = d2Helper(client, root, db);
            assertEquals("RESOLVED COMMITTED 42", d2Reconcile(client, c, 1, 101, 7001));
            assertEquals("STATE COMMITTED", d0Client(client, "STATE", "101", "7001"));
            d2Expect(client, c, 2, 101L, 7001L, "APPLY_ONCE", 42L, "REPLAYED 42");
            d2State(client, c, 3, 1, 1, 42);
            d2Ok(client, c, 6, null, null, "INTEGRITY", null, "true");
            d2Ok(client, c, 7, null, null, "STOP", null, "null");
            d0Exit(c);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T9MissingReceiptCannotBeMistakenForZeroValue() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t9-");
        Path db = root.resolve("d2.db");
        runD0(() -> {
            Process client = d2Client();
            Process a = d2Helper(client, root, db);
            d2Ok(client, a, 1, null, null, "INIT", null, "null");
            d2Expect(client, a, 2, 101L, 9001L, "LOOKUP", null, "ABSENT null");
            d2Expect(client, a, 3, 101L, 9001L, "APPLY_ONCE", 0L, "APPLIED 0");
            d2Expect(client, a, 4, 101L, 9001L, "LOOKUP", null, "FOUND 0");
            d2Expect(client, a, 5, 101L, 9002L, "LOOKUP", null, "ABSENT null");
            d2Ok(client, a, 6, null, null, "STOP", null, "null");
            d0Exit(a);
            Process b = d2Helper(client, root, db);
            d2Expect(client, b, 1, 101L, 9001L, "LOOKUP", null, "FOUND 0");
            d2Expect(client, b, 2, 101L, 9002L, "LOOKUP", null, "ABSENT null");
            d2State(client, b, 3, 1, 1, 0);
            d2Ok(client, b, 6, null, null, "STOP", null, "null");
            d0Exit(b);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T10TwoConfinedHelpersCannotApplyTheSameKeyTwice() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t10-");
        Path db = root.resolve("d2.db");
        runD0(() -> {
            Process client = d2Client();
            Process a = d2Helper(client, root, db);
            Process b = d2Helper(client, root, db);
            d2Ok(client, a, 1, null, null, "INIT", null, "null");
            d2Ok(client, b, 1, null, null, "INIT", null, "null");
            // A holds the RESERVED write lock inside its open transaction (observable event).
            d0Send(a, d2Text(client, 2, "101", "7001", "HOLD_BEFORE_COMMIT", "42"));
            d0AwaitEvent(a, "DURABLE_PRECOMMIT");
            // B's BEGIN IMMEDIATE meets SQLITE_BUSY after its bounded native wait: no effect.
            d2Expect(client, b, 2, 101L, 7001L, "APPLY_ONCE", 42L, "ERROR null BUSY");
            assertEquals(null, d0Stdout.get(a).poll(), "A has not committed yet");
            d0Send(a, d2Text(client, 3, "null", "null", "CONTINUE", "null"));
            String reply = d0Next(a);
            assertEquals("ACCEPTED APPLIED 42 null", d0Client(client, "MATCH", "2", reply), reply);
            // Reconciliation by B: the key is already applied; no second effect.
            d2Expect(client, b, 3, 101L, 7001L, "LOOKUP", null, "FOUND 42");
            d2Expect(client, b, 4, 101L, 7001L, "APPLY_ONCE", 42L, "REPLAYED 42");
            d2State(client, b, 5, 1, 1, 42);
            d2State(client, a, 4, 1, 1, 42);
            d2Ok(client, a, 7, null, null, "STOP", null, "null");
            d2Ok(client, b, 8, null, null, "INTEGRITY", null, "true");
            d2Ok(client, b, 9, null, null, "STOP", null, "null");
            d0Exit(a);
            d0Exit(b);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T11UnauthorizedProtocolCannotReachSqlite() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t11-");
        Path db = root.resolve("d2.db");
        Path exterior = d1Dir("d2-t11-exterior-").resolve("outside.db");
        runD0(() -> {
            Process client = d2Client();
            Process a = d2Helper(client, root, db);
            d2Ok(client, a, 1, null, null, "INIT", null, "null");
            d2Expect(client, a, 2, 101L, 1L, "APPLY_ONCE", 5L, "APPLIED 5");
            long id = 3;
            for (String operation : List.of("OPEN", "EXEC", "ATTACH", "LOAD", "VACUUM", "EVAL", "IMPORT",
                    "BEGIN", "INSERT", "COMMIT", "COMMIT_HOLD_ACK", "CONTINUE_ACK", "sqlite3_exec",
                    "sqlite3_load_extension", "libsqlite3.so", exterior.toString())) {
                d0Raw(client, a, d2Json(Long.toString(id), "null", "null", "\"" + operation + "\"", "null"),
                        "ACCEPTED " + id + " ERROR null UNSUPPORTED_OPERATION");
                id++;
            }
            String[][] carried = {
                {d2Json("ID", "101", "2", "\"APPLY_ONCE\"", "\"INSERT INTO entries VALUES (99)\""), "INVALID_VALUE"},
                {d2Json("ID", "101", "2", "\"APPLY_ONCE\"", "\"ATTACH '" + exterior + "' AS x\""), "INVALID_VALUE"},
                {d2Json("ID", "101", "2", "\"APPLY_ONCE\"", "\"Error().signal()\""), "INVALID_VALUE"},
                {d2Json("ID", "\"" + exterior + "\"", "2", "\"APPLY_ONCE\"", "7"), "INVALID_IDENTITY"},
                {d2Json("ID", "\"libsqlite3.so\"", "2", "\"LOOKUP\"", "null"), "INVALID_IDENTITY"},
            };
            for (String[] vector : carried) {
                d0Raw(client, a, vector[0].replace("ID", Long.toString(id)),
                        "ACCEPTED " + id + " ERROR null " + vector[1]);
                id++;
            }
            d0Raw(client, a, d2Json(Long.toString(id), "101", "2", "\"APPLY_ONCE\"", "7")
                    .replace("}", ",\"sql\":\"DROP TABLE entries\"}"), "ACCEPTED null ERROR null MALFORMED");
            d0Raw(client, a, "{\"version\":1,\"id\":" + id + ",\"op\":\"INSERT\",\"value\":7}",
                    "ACCEPTED null ERROR null MALFORMED");
            d2Expect(client, a, id++, null, null, "CONTINUE", null, "ERROR null UNEXPECTED_CONTINUE");
            d2State(client, a, id, 1, 1, 5);
            id += 3;
            String limit = d0Padded(d2Json(Long.toString(id), "null", "null", "\"COUNT\"", "null")
                    .replace("}", ""), 4096);
            d0Raw(client, a, limit, "ACCEPTED " + id + " OK 1 null");
            id++;
            d0Send(a, d0Padded(d2Json(Long.toString(id), "101", "3", "\"APPLY_ONCE\"", "7").replace("}", ""), 4097));
            d0AwaitEvent(a, "FRAMING_REJECTED");
            assertEquals(D0_EOF, d0Next(a), "an oversized line must never receive a reply");
            assertTrue(a.waitFor(30, TimeUnit.SECONDS), "fail-closed helper termination timeout");
            assertNotEquals(0, a.exitValue(), "framing failure must not be a clean protocol exit");
            assertFalse(Files.exists(exterior), "no request may create or open an exterior path");
            Process b = d2Helper(client, root, db);
            d2State(client, b, 1, 1, 1, 5);
            d2Expect(client, b, 4, 101L, 2L, "LOOKUP", null, "ABSENT null");
            d2Expect(client, b, 5, 101L, 3L, "LOOKUP", null, "ABSENT null");
            d2Ok(client, b, 6, null, null, "INTEGRITY", null, "true");
            d2Ok(client, b, 7, null, null, "STOP", null, "null");
            d0Exit(b);
            assertFalse(Files.exists(exterior));
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T12FailedOrInterruptedWriteNeverCreatesOrphanReceipt() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t12-");
        Path db = root.resolve("d2.db");
        runD0(() -> {
            Process client = d2Client();
            Process a = d2Helper(client, root, db);
            d2Ok(client, a, 1, null, null, "INIT", null, "null");
            d2Expect(client, a, 2, 101L, 1L, "APPLY_ONCE", 7L, "APPLIED 7");
            d2Ok(client, a, 3, null, null, "STOP", null, "null");
            d0Exit(a);
            // Interruption 1: SIGKILL while the entry and receipt are written but uncommitted.
            Process b = d2Helper(client, root, db);
            d0Send(b, d2Text(client, 1, "101", "2", "HOLD_BEFORE_COMMIT", "9"));
            d0AwaitEvent(b, "DURABLE_PRECOMMIT");
            d0Kill(b);
            // Interruption 2: an invalid continuation; the helper rolls back, withholds any
            // reply and fails closed.
            Process c = d2Helper(client, root, db);
            d0Send(c, d2Text(client, 1, "101", "3", "HOLD_BEFORE_COMMIT", "11"));
            d0AwaitEvent(c, "DURABLE_PRECOMMIT");
            d0Send(c, d2Text(client, 2, "null", "null", "PING", "null"));
            d0AwaitEvent(c, "DURABLE_ACK_WITHHELD");
            assertEquals(D0_EOF, d0Next(c), "an interrupted write must never be acknowledged");
            assertTrue(c.waitFor(30, TimeUnit.SECONDS), "fail-closed helper termination timeout");
            assertNotEquals(0, c.exitValue());
            // Entries and receipts agree exactly: neither an orphan receipt nor an unkeyed entry.
            Process d = d2Helper(client, root, db);
            d2Expect(client, d, 1, 101L, 1L, "LOOKUP", null, "FOUND 7");
            d2Expect(client, d, 2, 101L, 2L, "LOOKUP", null, "ABSENT null");
            d2Expect(client, d, 3, 101L, 3L, "LOOKUP", null, "ABSENT null");
            d2State(client, d, 4, 1, 1, 7);
            d2Ok(client, d, 7, null, null, "INTEGRITY", null, "true");
            d2Ok(client, d, 8, null, null, "STOP", null, "null");
            d0Exit(d);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T13TransportIdsAreNotDurableOperationIds() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t13-");
        Path db = root.resolve("d2.db");
        runD0(() -> {
            Process client = d2Client();
            Process a = d2Helper(client, root, db);
            d2Ok(client, a, 1, null, null, "INIT", null, "null");
            d2Expect(client, a, 2, 101L, 7001L, "APPLY_ONCE", 42L, "APPLIED 42");
            d2Expect(client, a, 3, 101L, 7001L, "APPLY_ONCE", 42L, "REPLAYED 42");
            d2Expect(client, a, 7, 101L, 7001L, "APPLY_ONCE", 42L, "REPLAYED 42");
            d2Expect(client, a, 7001, 101L, 7001L, "APPLY_ONCE", 42L, "REPLAYED 42");
            // A repeated transport id is a transport error, never a durable decision.
            d2Expect(client, a, 7001, 101L, 7002L, "APPLY_ONCE", 1L, "ERROR null NON_INCREASING_ID");
            d2State(client, a, 7002, 1, 1, 42);
            d2Ok(client, a, 7005, null, null, "STOP", null, "null");
            d0Exit(a);
            // The transport counter restarts at 1; the durable identity is unchanged.
            Process b = d2Helper(client, root, db);
            d2Expect(client, b, 1, 101L, 7001L, "APPLY_ONCE", 42L, "REPLAYED 42");
            d2Expect(client, b, 2, 101L, 2L, "LOOKUP", null, "ABSENT null");
            d2State(client, b, 3, 1, 1, 42);
            d2Ok(client, b, 6, null, null, "STOP", null, "null");
            d0Exit(b);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T14DurableCloseRevokesConnectionCustody() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t14-");
        Path db = root.resolve("d2.db");
        runD0(() -> {
            Process client = d2Client();
            Process a = d2Helper(client, root, db);
            d2Ok(client, a, 1, null, null, "INIT", null, "null");
            d2Expect(client, a, 2, 101L, 1L, "APPLY_ONCE", 5L, "APPLIED 5");
            assertEquals(0, a.toHandle().descendants().count(), "the confined helper spawns no process");
            d2Ok(client, a, 3, null, null, "CLOSE", null, "null");
            d2Expect(client, a, 4, 101L, 2L, "APPLY_ONCE", 6L, "ERROR null CLOSED");
            d2Expect(client, a, 5, 101L, 1L, "LOOKUP", null, "ERROR null CLOSED");
            d2Expect(client, a, 6, null, null, "INIT", null, "ERROR null CLOSED");
            d2Expect(client, a, 7, null, null, "COUNT", null, "ERROR null CLOSED");
            d2Expect(client, a, 8, 101L, 3L, "HOLD_AFTER_COMMIT", 1L, "ERROR null CLOSED");
            d2Ok(client, a, 9, null, null, "CLOSE", null, "null");
            d2Expect(client, a, 10, null, null, "RECEIPTS", null, "ERROR null CLOSED");
            d2Ok(client, a, 11, null, null, "STOP", null, "null");
            d0Exit(a);
            assertFalse(ProcessHandle.of(a.pid()).map(ProcessHandle::isAlive).orElse(false),
                    "no surviving helper process");
            assertEquals(List.of("d2.db"), directoryNames(root), "no journal survives a clean close");
            Process b = d2Helper(client, root, db);
            d2Expect(client, b, 1, 101L, 1L, "LOOKUP", null, "FOUND 5");
            d2Expect(client, b, 2, 101L, 2L, "LOOKUP", null, "ABSENT null");
            d2State(client, b, 3, 1, 1, 5);
            d2Ok(client, b, 6, null, null, "STOP", null, "null");
            d0Exit(b);
            for (Process child : d0Stdout.keySet()) {
                if (child != client) assertFalse(child.isAlive(), "PID=" + child.pid() + " survived");
            }
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T15UncertainReplyNeverMeansConfirmedRollback() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t15-");
        Path db = root.resolve("d2.db");
        runD0(() -> {
            Process client = d2Client();
            d2Initialize(client, root, db);
            Process b = d2Helper(client, root, db);
            d0Send(b, d2Text(client, 1, "101", "7001", "HOLD_AFTER_COMMIT", "42"));
            d0AwaitEvent(b, "DURABLE_COMMITTED_BEFORE_ACK");
            // The channel is lost after COMMIT: EOF instead of a continuation.
            b.getOutputStream().close();
            d0AwaitEvent(b, "DURABLE_ACK_WITHHELD");
            assertEquals(D0_EOF, d0Next(b), "a withheld acknowledgement is never sent");
            assertTrue(b.waitFor(30, TimeUnit.SECONDS), "fail-closed helper termination timeout");
            assertNotEquals(0, b.exitValue());
            // EOF is neither a decodable ERROR nor a rollback: the key stays UNCERTAIN.
            assertEquals("REJECTED", d0Client(client, "MATCH", "1", ""));
            assertEquals("UNCERTAIN 101 7001 CONNECTION_LOST", d0Client(client, "LOST", "1", "101", "7001", "42"));
            assertEquals("STATE UNCERTAIN", d0Client(client, "STATE", "101", "7001"));
            Process c = d2Helper(client, root, db);
            // A non-LOOKUP reply cannot resolve it either.
            assertEquals("TEXT ", d0Client(client, "RECONCILE", "1", "101", "7001").substring(0, 5));
            assertEquals("REJECTED", d0Client(client, "RESOLVE", "1", d2Reply("1", "ERROR", "null", "\"BUSY\"")));
            assertEquals("STATE UNCERTAIN", d0Client(client, "STATE", "101", "7001"));
            assertEquals("RESOLVED COMMITTED 42", d2Reconcile(client, c, 2, 101, 7001));
            d2State(client, c, 3, 1, 1, 42);
            d2Ok(client, c, 6, null, null, "STOP", null, "null");
            d0Exit(c);
            d0CloseClient(client);
            return null;
        });
    }

    @Test
    void d2T16ConflictAfterUncertainCommitCannotOverwriteReceipt() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d2-t16-");
        Path db = root.resolve("d2.db");
        runD0(() -> {
            Process client = d2Client();
            d2Initialize(client, root, db);
            Process b = d2Helper(client, root, db);
            d0Send(b, d2Text(client, 1, "101", "7001", "HOLD_AFTER_COMMIT", "42"));
            d0AwaitEvent(b, "DURABLE_COMMITTED_BEFORE_ACK");
            d0Kill(b);
            assertEquals("UNCERTAIN 101 7001 CONNECTION_LOST", d0Client(client, "LOST", "1", "101", "7001", "42"));
            for (int restart = 0; restart < 2; restart++) {
                Process c = d2Helper(client, root, db);
                d2Expect(client, c, 1, 101L, 7001L, "APPLY_ONCE", 99L, "CONFLICT 42");
                d2Expect(client, c, 2, 101L, 7001L, "LOOKUP", null, "FOUND 42");
                d2State(client, c, 3, 1, 1, 42);
                d2Ok(client, c, 6, null, null, "INTEGRITY", null, "true");
                d2Ok(client, c, 7, null, null, "STOP", null, "null");
                d0Exit(c);
            }
            d0CloseClient(client);
            return null;
        });
    }

    /** The Protos durable client driver: builds v2 requests and validates replies; no provider. */
    private static Process d2Client() throws Exception {
        return d0Launch(D0.resolve("durable-client-driver.protos"), false);
    }

    /**
     * A durable helper behind the D1-R1 launcher: the full ABI 10 profile and LANDLOCK_APPLIED must
     * precede the Protos v2 READY event, which the Protos client validates.
     */
    private static Process d2Helper(Process client, Path grant, Path db) throws Exception {
        assertTrue(db.isAbsolute(), "D2 database path must be absolute");
        Process helper = d1Launch(grant, List.of(), D0.resolve("durable-helper.protos"), db.toString());
        d0AwaitEvent(helper, "LANDLOCK_APPLIED");
        String log;
        synchronized (d0Logs.get(helper)) { log = d0Logs.get(helper).toString(); }
        assertTrue(log.contains(D1R1_PROFILE + "\n"), log);
        assertEquals("ACCEPTED", d0Client(client, "READY", d0Next(helper)),
                "the confined durable helper must publish the Protos v2 READY event first");
        return helper;
    }

    /** Creates the durable schema in one confined helper and stops it cleanly. */
    private static void d2Initialize(Process client, Path grant, Path db) throws Exception {
        Process helper = d2Helper(client, grant, db);
        d2Ok(client, helper, 1, null, null, "INIT", null, "null");
        d2Ok(client, helper, 2, null, null, "STOP", null, "null");
        d0Exit(helper);
    }

    private static String d2Scalar(Long value) {
        return value == null ? "null" : Long.toString(value);
    }

    /** One v2 request line, built by the Protos driver. */
    private static String d2Text(Process client, long id, String clientId, String operationId,
            String operation, String value) throws Exception {
        String reply = d0Client(client, "REQUEST", Long.toString(id), clientId, operationId, operation, value);
        assertTrue(reply.startsWith("TEXT "), reply);
        return reply.substring("TEXT ".length());
    }

    /** Protos builds the request; the reply is correlated and decoded by the Protos client. */
    private static void d2Expect(Process client, Process helper, long id, Long clientId, Long operationId,
            String operation, Long value, String expected) throws Exception {
        d0Send(helper, d2Text(client, id, d2Scalar(clientId), d2Scalar(operationId), operation, d2Scalar(value)));
        String reply = d0Next(helper);
        assertNotEquals(D0_EOF, reply, "helper closed stdout before replying to " + operation);
        String[] parts = expected.split(" ");
        String full = parts.length == 2 ? "ACCEPTED " + expected + " null" : "ACCEPTED " + expected;
        assertEquals(full, d0Client(client, "MATCH", Long.toString(id), reply), reply);
    }

    private static void d2Ok(Process client, Process helper, long id, Long clientId, Long operationId,
            String operation, Long value, String result) throws Exception {
        d2Expect(client, helper, id, clientId, operationId, operation, value, "OK " + result);
    }

    /** COUNT, RECEIPTS and SUM with transport ids {@code id}, {@code id + 1}, {@code id + 2}. */
    private static void d2State(Process client, Process helper, long id, long entries, long receipts, long sum)
            throws Exception {
        d2Ok(client, helper, id, null, null, "COUNT", null, Long.toString(entries));
        d2Ok(client, helper, id + 1, null, null, "RECEIPTS", null, Long.toString(receipts));
        d2Ok(client, helper, id + 2, null, null, "SUM", null, Long.toString(sum));
    }

    /** The Protos client prepares the LOOKUP for an uncertain key and resolves its reply. */
    private static String d2Reconcile(Process client, Process helper, long id, long clientId, long operationId)
            throws Exception {
        String request = d0Client(client, "RECONCILE", Long.toString(id), Long.toString(clientId),
                Long.toString(operationId));
        assertTrue(request.startsWith("TEXT "), request);
        d0Send(helper, request.substring("TEXT ".length()));
        String reply = d0Next(helper);
        assertNotEquals(D0_EOF, reply, "helper closed stdout before answering the reconciliation");
        return d0Client(client, "RESOLVE", Long.toString(id), reply);
    }

    /** A raw adversarial v2 request object; every argument is inserted verbatim. */
    private static String d2Json(String id, String clientId, String operationId, String operation, String value) {
        return "{\"version\":2,\"id\":" + id + ",\"clientId\":" + clientId + ",\"operationId\":" + operationId
                + ",\"op\":" + operation + ",\"value\":" + value + "}";
    }

    /** A raw v2 response object for client-side validation vectors. */
    private static String d2Reply(String id, String status, String value, String reason) {
        return "{\"version\":2,\"id\":" + id + ",\"status\":\"" + status + "\",\"value\":" + value
                + ",\"reason\":" + reason + "}";
    }

    // ---- PLAT074-D3: OS parent custody of the helper and verified reconciliation.
    // isolated/native/owner-exec.c (test fixture only) forks the D1-R1 launcher with
    // --expected-parent-pid and then execve()s the Protos client JVM in the same process, so the
    // client is the helper's OS parent, and stdin/stdout link the two Protos JVMs directly. The
    // launcher sets PR_SET_PDEATHSIG(SIGKILL) before Landlock and before the helper JVM exists.
    // JUnit only sees stderr: it builds no protocol line for the session and relays nothing.
    // Process liveness is read from /proc (state and start time), so a zombie or a reused PID is
    // never mistaken for a helper that still executes code. All D3 claims are experimental.

    private static final String D3_SESSION = "durable-session-client.protos";
    private static final int D3_SIGKILL = 9;
    private static final int D3_SIGSTOP = 19;
    private static Path d3OwnerExec;
    private static final List<D3Pid> d3Helpers = new ArrayList<>();

    /** One observed process: its PID and its /proc start time, which detects PID reuse. */
    private record D3Pid(long pid, String startTime) {}

    /** A session: the owner/client process launched by JUnit and its forked helper. */
    private record D3Session(Process client, D3Pid helper) {}

    @Test
    void d3T1ParentOwnershipIsEstablishedBeforeJvmStartup() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t1-");
        Path db = root.resolve("d3.db");
        runD3(() -> {
            Process driver = d3Driver(11, 12);
            d3Provision(driver, root, db, 11, 12);
            D3Session session = d3Session(root, db, 11, 12, "HOLD_AFTER_READY", 11, 12);
            Process client = session.client();
            D3Pid helper = session.helper();
            d3Await(client, "CLIENT_READY");
            assertNotEquals(client.pid(), helper.pid(), "client and helper are distinct OS processes");
            assertEquals(client.pid(), d3Parent(helper), "the Protos client JVM is the helper's OS parent");
            assertTrue(client.toHandle().children().anyMatch(child -> child.pid() == helper.pid()));
            assertEquals(ProcessHandle.current().pid(),
                    client.toHandle().parent().map(ProcessHandle::pid).orElse(-1L));
            // owner-exec and sandbox-exec were replaced by execve: both are Protos CLI JVMs now.
            String clientCommand = d3Command(client.pid());
            String helperCommand = d3Command(helper.pid());
            assertTrue(clientCommand.contains("com.guillermomolina.protos.cli.ProtosCli")
                    && clientCommand.contains(D3_SESSION), clientCommand);
            assertTrue(helperCommand.contains("com.guillermomolina.protos.cli.ProtosCli")
                    && helperCommand.contains("durable-helper.protos"), helperCommand);
            assertFalse(helperCommand.contains("sandbox-exec") || helperCommand.contains("owner-exec"),
                    helperCommand);
            // The launcher verified this very parent and SIGKILL before the profile and the JVM.
            String log = d3Log(client);
            String verified = "PARENT_OWNERSHIP_VERIFIED parent=" + client.pid() + " signal=SIGKILL\n";
            assertTrue(log.contains(verified), log);
            assertTrue(log.indexOf(verified) < log.indexOf(D1R1_PROFILE + "\n"), log);
            assertTrue(log.indexOf(D1R1_PROFILE + "\n") < log.indexOf("LANDLOCK_APPLIED\n"), log);
            assertTrue(log.indexOf("LANDLOCK_APPLIED\n") < log.indexOf("CLIENT_READY\n"), log);
            d3KillClient(session);
            return null;
        });
    }

    @Test
    void d3T2DirectProtosToProtosCommunication() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t2-");
        Path db = root.resolve("d3.db");
        runD3(() -> {
            Process driver = d3Driver(13, 14);
            d3Provision(driver, root, db, 13, 14);
            D3Session session = d3Session(root, db, 13, 14, "NORMAL", 13, 14);
            Process client = session.client();
            d3Await(client, "CLIENT_READY");
            d3Await(client, "CLIENT_APPLY APPLIED");
            d3Await(client, "CLIENT_LOOKUP FOUND");
            d3Await(client, "CLIENT_STOPPED");
            assertTrue(client.waitFor(60, TimeUnit.SECONDS), "client termination timeout");
            assertEquals(0, client.exitValue(), d3Log(client));
            // JUnit never had a data channel: its stdout pipe was replaced in both processes.
            assertEquals(D0_EOF, d0Next(client), "no protocol line may reach the harness");
            assertTrue(d3AwaitGone(session.helper(), 30), "the stopped helper must terminate");
            // INIT, APPLY_ONCE and STOP were executed: verified by an independent restricted helper.
            Process check = d3Helper(driver, root, db, 13, 14, false);
            d2Expect(driver, check, 1, 301L, 9001L, "LOOKUP", null, "FOUND 77");
            d2State(driver, check, 2, 1, 1, 77);
            d2Ok(driver, check, 5, null, null, "INTEGRITY", null, "true");
            d2Ok(driver, check, 6, null, null, "STOP", null, "null");
            d0Exit(check);
            d0CloseClient(driver);
            return null;
        });
    }

    @Test
    void d3T3ParentDeathSignalSurvivesExecve() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t3-");
        Path db = root.resolve("d3.db");
        runD3(() -> {
            Process driver = d3Driver(15, 16);
            d3Provision(driver, root, db, 15, 16);
            D3Session session = d3Session(root, db, 15, 16, "HOLD_AFTER_READY", 15, 16);
            Process client = session.client();
            D3Pid helper = session.helper();
            d3Await(client, "CLIENT_READY");
            String log = d3Log(client);
            assertTrue(log.indexOf("PARENT_OWNERSHIP_VERIFIED parent=" + client.pid())
                    < log.indexOf("LANDLOCK_APPLIED\n"), "SIGKILL custody must precede the exec: " + log);
            assertTrue(d3Command(helper.pid()).contains("durable-helper.protos"),
                    "the helper image is the exec'd JVM");
            // A stopped helper cannot execute code, so it can never observe EOF: only a signal can
            // end it. SIGKILL also terminates stopped tasks.
            d3Signal(helper, D3_SIGSTOP);
            d3AwaitState(helper, "T");
            client.destroyForcibly();
            assertTrue(client.waitFor(30, TimeUnit.SECONDS), "client SIGKILL timeout");
            assertEquals(137, client.exitValue());
            assertTrue(d3AwaitGone(helper, 30),
                    "a stopped helper must be ended by the parent-death SIGKILL, state=" + d3State(helper));
            return null;
        });
    }

    @Test
    void d3T4ClientSigkillTerminatesIdleHelper() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t4-");
        Path db = root.resolve("d3.db");
        runD3(() -> {
            Process driver = d3Driver(17, 18);
            d3Provision(driver, root, db, 17, 18);
            D3Session session = d3Session(root, db, 17, 18, "HOLD_AFTER_READY", 17, 18);
            d3Await(session.client(), "CLIENT_READY");
            assertTrue(d3Running(session.helper()), "the idle helper must be running before the kill");
            d3KillClient(session);
            return null;
        });
    }

    @Test
    void d3T5ClientSigkillBeforeCommitLeavesNothing() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t5-");
        Path db = root.resolve("d3.db");
        runD3(() -> {
            Process driver = d3Driver(19, 20);
            d3Provision(driver, root, db, 19, 20);
            D3Session session = d3Session(root, db, 19, 20, "HOLD_BEFORE_COMMIT", 19, 20);
            d3Await(session.client(), "CLIENT_READY");
            d3Await(session.client(), "DURABLE_PRECOMMIT");
            assertTrue(d3Running(session.helper()));
            d3KillClient(session);
            Process check = d3Helper(driver, root, db, 19, 20, false);
            d2Expect(driver, check, 1, 301L, 9001L, "LOOKUP", null, "ABSENT null");
            d2State(driver, check, 2, 0, 0, 0);
            d2Ok(driver, check, 5, null, null, "INTEGRITY", null, "true");
            d2Ok(driver, check, 6, null, null, "STOP", null, "null");
            d0Exit(check);
            d0CloseClient(driver);
            return null;
        });
    }

    @Test
    void d3T6ClientSigkillAfterCommitBeforeAckReplays() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t6-");
        Path db = root.resolve("d3.db");
        runD3(() -> {
            Process driver = d3Driver(21, 22);
            d3Provision(driver, root, db, 21, 22);
            D3Session session = d3Session(root, db, 21, 22, "HOLD_AFTER_COMMIT", 21, 22);
            d3Await(session.client(), "CLIENT_READY");
            d3Await(session.client(), "DURABLE_COMMITTED_BEFORE_ACK");
            d3KillClient(session);
            Process check = d3Helper(driver, root, db, 21, 22, false);
            d2Expect(driver, check, 1, 301L, 9001L, "LOOKUP", null, "FOUND 77");
            d2Expect(driver, check, 2, 301L, 9001L, "APPLY_ONCE", 77L, "REPLAYED 77");
            d2State(driver, check, 3, 1, 1, 77);
            d2Ok(driver, check, 6, null, null, "INTEGRITY", null, "true");
            d2Ok(driver, check, 7, null, null, "STOP", null, "null");
            d0Exit(check);
            d0CloseClient(driver);
            return null;
        });
    }

    @Test
    void d3T7NormalClientExitDoesNotLeaveHelperRunning() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t7-");
        Path db = root.resolve("d3.db");
        runD3(() -> {
            Process driver = d3Driver(23, 24);
            d3Provision(driver, root, db, 23, 24);
            for (String scenario : List.of("EXIT_AFTER_READY", "EXIT_AFTER_COMMIT")) {
                D3Session session = d3Session(root, db, 23, 24, scenario, 23, 24);
                Process client = session.client();
                d3Await(client, "CLIENT_READY");
                if (scenario.equals("EXIT_AFTER_COMMIT")) d3Await(client, "CLIENT_APPLIED");
                d3Await(client, "CLIENT_EXIT_WITHOUT_STOP");
                assertTrue(client.waitFor(60, TimeUnit.SECONDS), scenario + " client termination timeout");
                assertEquals(0, client.exitValue(), d3Log(client));
                // Not running: absent, or a zombie that executes no code, never a live helper.
                assertTrue(d3AwaitGone(session.helper(), 30),
                        scenario + " left helper state=" + d3State(session.helper()));
            }
            Process check = d3Helper(driver, root, db, 23, 24, false);
            d2Expect(driver, check, 1, 301L, 9001L, "LOOKUP", null, "FOUND 77");
            d2State(driver, check, 2, 1, 1, 77);
            d2Ok(driver, check, 5, null, null, "STOP", null, "null");
            d0Exit(check);
            d0CloseClient(driver);
            return null;
        });
    }

    @Test
    void d3T8WrongDatabaseIdentityRefusesReconciliation() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t8-");
        Path a = root.resolve("a.db");
        Path b = root.resolve("b.db");
        Path legacy = root.resolve("legacy.db");
        runD3(() -> {
            Process driverA = d3Driver(31, 32);
            Process driverB = d3Driver(41, 42);
            d3Provision(driverA, root, a, 31, 32);
            d3Provision(driverB, root, b, 41, 42);
            // A populated database without an identity (the D2 helper) is never adopted.
            Process seeder = d2Helper(driverA, root, legacy);
            d2Ok(driverA, seeder, 1, null, null, "INIT", null, "null");
            d2Expect(driverA, seeder, 2, 101L, 1L, "APPLY_ONCE", 5L, "APPLIED 5");
            d2Ok(driverA, seeder, 3, null, null, "STOP", null, "null");
            d0Exit(seeder);
            Map<String, String> before = d1Snapshot(root);
            // Database A started with B's identity, A re-provisioned, legacy provisioned: refused
            // before READY, so no LOOKUP or APPLY_ONCE can ever be sent.
            d3Refused(root, a, "41", "42", null);
            d3Refused(root, a, "31", "32", "PROVISION");
            d3Refused(root, legacy, "51", "52", "PROVISION");
            d3Refused(root, legacy, "51", "52", null);
            assertEquals(before, d1Snapshot(root), "a refused identity must leave every database unchanged");
            // The real client: the helper verified A correctly, but the client's bootstrap names B.
            D3Session session = d3Session(root, a, 31, 32, "NORMAL", 41, 42);
            d3Await(session.client(), "CLIENT_DATABASE_IDENTITY_MISMATCH");
            assertTrue(session.client().waitFor(60, TimeUnit.SECONDS));
            assertNotEquals(0, session.client().exitValue());
            assertFalse(d3Log(session.client()).contains("CLIENT_READY\n"), d3Log(session.client()));
            assertTrue(d3AwaitGone(session.helper(), 30), "the helper must not outlive the refused client");
            Process check = d3Helper(driverA, root, a, 31, 32, false);
            d2State(driverA, check, 1, 0, 0, 0);
            d2Ok(driverA, check, 4, null, null, "STOP", null, "null");
            d0Exit(check);
            d0CloseClient(driverA);
            d0CloseClient(driverB);
            return null;
        });
    }

    @Test
    void d3T9SameDatabaseIdentitySurvivesRestart() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t9-");
        Path db = root.resolve("d3.db");
        runD3(() -> {
            Process driver = d3Driver(-9223372036854775808L, 9223372036854775807L);
            d3Provision(driver, root, db, Long.MIN_VALUE, Long.MAX_VALUE);
            D3Session first = d3Session(root, db, Long.MIN_VALUE, Long.MAX_VALUE, "NORMAL",
                    Long.MIN_VALUE, Long.MAX_VALUE);
            d3Await(first.client(), "CLIENT_APPLY APPLIED");
            d3Await(first.client(), "CLIENT_STOPPED");
            assertTrue(first.client().waitFor(60, TimeUnit.SECONDS));
            assertEquals(0, first.client().exitValue(), d3Log(first.client()));
            assertTrue(d3AwaitGone(first.helper(), 30));
            // Another restricted JVM: READY, then the same DATABASE_IDENTITY (VERIFIED by Protos).
            Process check = d3Helper(driver, root, db, Long.MIN_VALUE, Long.MAX_VALUE, false);
            d2Expect(driver, check, 1, 301L, 9001L, "LOOKUP", null, "FOUND 77");
            d2Ok(driver, check, 2, null, null, "STOP", null, "null");
            d0Exit(check);
            D3Session second = d3Session(root, db, Long.MIN_VALUE, Long.MAX_VALUE, "NORMAL",
                    Long.MIN_VALUE, Long.MAX_VALUE);
            d3Await(second.client(), "CLIENT_APPLY REPLAYED");
            d3Await(second.client(), "CLIENT_STOPPED");
            assertTrue(second.client().waitFor(60, TimeUnit.SECONDS));
            assertEquals(0, second.client().exitValue(), d3Log(second.client()));
            assertTrue(d3AwaitGone(second.helper(), 30));
            Process last = d3Helper(driver, root, db, Long.MIN_VALUE, Long.MAX_VALUE, false);
            d2State(driver, last, 1, 1, 1, 77);
            d2Ok(driver, last, 4, null, null, "STOP", null, "null");
            d0Exit(last);
            d0CloseClient(driver);
            return null;
        });
    }

    @Test
    void d3T10LiveSourcePlusAbsentDoesNotEstablishRollback() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t10-");
        Path db = root.resolve("d3.db");
        runD3(() -> {
            Process driver = d3Driver(51, 52);
            d3Provision(driver, root, db, 51, 52);
            D3Session session = d3Session(root, db, 51, 52, "HOLD_BEFORE_COMMIT", 51, 52);
            d3Await(session.client(), "CLIENT_READY");
            d3Await(session.client(), "DURABLE_PRECOMMIT");
            assertEquals("UNCERTAIN 301 9001 CONNECTION_LOST",
                    d0Client(driver, "LOST", "1", "301", "9001", "77"));
            Process other = d3Helper(driver, root, db, 51, 52, false);
            for (long id = 1; id <= 2; id++) {
                String resolved = d2Reconcile(driver, other, id, 301, 9001);
                // ABSENT while the source can still COMMIT is an observation, never a rollback.
                assertTrue(resolved.equals("RESOLVED ABSENT_OBSERVED null") || resolved.equals("REJECTED"),
                        resolved);
                String state = d0Client(driver, "STATE", "301", "9001");
                assertTrue(state.equals("STATE ABSENT_OBSERVED") || state.equals("STATE UNCERTAIN"), state);
                assertTrue(d3Running(session.helper()), "the source helper is still alive and holding");
            }
            d2Ok(driver, other, 3, null, null, "STOP", null, "null");
            d0Exit(other);
            d3KillClient(session);
            d0CloseClient(driver);
            return null;
        });
    }

    @Test
    void d3T11TerminatedSourcePlusAbsentPermitsSafeRetry() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t11-");
        Path db = root.resolve("d3.db");
        runD3(() -> {
            Process driver = d3Driver(61, 62);
            d3Provision(driver, root, db, 61, 62);
            D3Session session = d3Session(root, db, 61, 62, "HOLD_BEFORE_COMMIT", 61, 62);
            d3Await(session.client(), "CLIENT_READY");
            d3Await(session.client(), "DURABLE_PRECOMMIT");
            assertEquals("UNCERTAIN 301 9001 CONNECTION_LOST",
                    d0Client(driver, "LOST", "1", "301", "9001", "77"));
            // Kill the source helper itself; the host then observes its effective termination.
            d3Signal(session.helper(), D3_SIGKILL);
            assertTrue(d3AwaitGone(session.helper(), 30), "the source helper must terminate");
            d3Await(session.client(), "CLIENT_CONNECTION_LOST UNCERTAIN");
            assertTrue(session.client().waitFor(60, TimeUnit.SECONDS));
            assertNotEquals(0, session.client().exitValue());
            assertEquals("TERMINATED 301 9001", d0Client(driver, "SOURCE_TERMINATED", "301", "9001"));
            Process other = d3Helper(driver, root, db, 61, 62, false);
            assertEquals("RESOLVED NOT_APPLIED null", d2Reconcile(driver, other, 1, 301, 9001));
            assertEquals("STATE NOT_APPLIED", d0Client(driver, "STATE", "301", "9001"));
            d2Expect(driver, other, 2, 301L, 9001L, "APPLY_ONCE", 77L, "APPLIED 77");
            d2Expect(driver, other, 3, 301L, 9001L, "APPLY_ONCE", 77L, "REPLAYED 77");
            d2State(driver, other, 4, 1, 1, 77);
            d2Ok(driver, other, 7, null, null, "STOP", null, "null");
            d0Exit(other);
            d0CloseClient(driver);
            return null;
        });
    }

    @Test
    void d3T12WrongIdentityCannotProduceFalseNotApplied() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t12-");
        Path a = root.resolve("a.db");
        Path b = root.resolve("b.db");
        runD3(() -> {
            Process driver = d3Driver(71, 72);
            Process driverB = d3Driver(81, 82);
            d3Provision(driver, root, a, 71, 72);
            d3Provision(driverB, root, b, 81, 82);
            D3Session session = d3Session(root, a, 71, 72, "HOLD_BEFORE_COMMIT", 71, 72);
            d3Await(session.client(), "CLIENT_READY");
            d3Await(session.client(), "DURABLE_PRECOMMIT");
            assertEquals("UNCERTAIN 301 9001 CONNECTION_LOST",
                    d0Client(driver, "LOST", "1", "301", "9001", "77"));
            d3KillClient(session);
            assertEquals("TERMINATED 301 9001", d0Client(driver, "SOURCE_TERMINATED", "301", "9001"));
            // The LOOKUP is prepared against a verified helper of A ...
            Process first = d3Helper(driver, root, a, 71, 72, false);
            String request = d0Client(driver, "RECONCILE", "1", "301", "9001");
            assertTrue(request.startsWith("TEXT "), request);
            d2Ok(driver, first, 2, null, null, "STOP", null, "null");
            d0Exit(first);
            // ... but the reconnection reaches B, a correctly provisioned database without the receipt.
            Process wrong = d1Launch(root, List.of(), D0.resolve("durable-helper.protos"), b.toString(), "81", "82");
            d0AwaitEvent(wrong, "LANDLOCK_APPLIED");
            assertEquals("ACCEPTED", d0Client(driver, "READY", d0Next(wrong)));
            assertEquals("MISMATCH", d0Client(driver, "HELPER_IDENTITY", d0Next(wrong)));
            d0Send(wrong, request.substring("TEXT ".length()));
            String reply = d0Next(wrong);
            assertEquals("ACCEPTED 1 ABSENT null null", d0Client(driver, "RESPONSE", reply));
            assertEquals("REJECTED", d0Client(driver, "RESOLVE", "1", reply),
                    "an ABSENT from another database must never resolve this key");
            assertEquals("REJECTED", d0Client(driver, "RECONCILE", "2", "301", "9001"));
            assertEquals("STATE UNCERTAIN", d0Client(driver, "STATE", "301", "9001"));
            d2Ok(driver, wrong, 3, null, null, "STOP", null, "null");
            d0Exit(wrong);
            // The verified original database then gives the final answer.
            Process right = d3Helper(driver, root, a, 71, 72, false);
            assertEquals("RESOLVED NOT_APPLIED null", d2Reconcile(driver, right, 1, 301, 9001));
            d2Ok(driver, right, 2, null, null, "STOP", null, "null");
            d0Exit(right);
            d0CloseClient(driver);
            d0CloseClient(driverB);
            return null;
        });
    }

    @Test
    void d3T13ParentMismatchRefusesStartup() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t13-");
        Path db = root.resolve("d3.db");
        runD0(() -> {
            Process driver = d3Driver(91, 92);
            // PID 1 is never this launcher's parent (the JUnit JVM is); the rest are invalid PIDs.
            for (String parent : List.of("1", "0", "-7", "abc", Long.toString(Integer.MAX_VALUE + 1L))) {
                Process refused = d1Launch(root, List.of("--expected-parent-pid", parent),
                        D0.resolve("durable-helper.protos"), db.toString(), "91", "92", "PROVISION");
                d1r1Refused(refused, "SANDBOX_REFUSED PARENT_OWNERSHIP_FAILED", 72);
            }
            assertFalse(Files.exists(db), "a refused launch must never open SQLite");
            // Positive control: the true parent passes the same check and the profile follows.
            String self = Long.toString(ProcessHandle.current().pid());
            Process helper = d1Launch(root, List.of("--expected-parent-pid", self),
                    D0.resolve("durable-helper.protos"), db.toString(), "91", "92", "PROVISION");
            d0AwaitEvent(helper, "LANDLOCK_APPLIED");
            String log;
            synchronized (d0Logs.get(helper)) { log = d0Logs.get(helper).toString(); }
            assertTrue(log.contains("PARENT_OWNERSHIP_VERIFIED parent=" + self + " signal=SIGKILL\n"), log);
            assertEquals("ACCEPTED", d0Client(driver, "READY", d0Next(helper)));
            assertEquals("VERIFIED", d0Client(driver, "HELPER_IDENTITY", d0Next(helper)));
            d2Ok(driver, helper, 1, null, null, "STOP", null, "null");
            d0Exit(helper);
            d0CloseClient(driver);
            return null;
        });
    }

    @Test
    void d3T14LossOfClientDoesNotWidenLandlockAuthority() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t14-");
        Path db = root.resolve("d3.db");
        Path outside = d1Dir("d3-t14-exterior-");
        Path exterior = outside.resolve("exterior.db");
        runD3(() -> {
            Process seedClient = d0Client();
            d1Seed(seedClient, exterior, 7);
            Map<String, String> before = d1Snapshot(outside);
            Process driver = d3Driver(101, 102);
            d3Provision(driver, root, db, 101, 102);
            D3Session session = d3Session(root, db, 101, 102, "HOLD_AFTER_READY", 101, 102);
            d3Await(session.client(), "CLIENT_READY");
            d3KillClient(session);
            // A fresh restricted helper JVM after the crash: same grant, same denials.
            assertEquals(List.of(D1_AUTHORIZED, D1_EXTERIOR_DENIED),
                    d1Probe(root, root.resolve("authorized.db"), exterior));
            // The custody option adds no authority: the native probe stays denied with it.
            try (var server = java.nio.channels.ServerSocketChannel.open()) {
                server.bind(new java.net.InetSocketAddress(java.net.InetAddress.getByName("127.0.0.1"), 0));
                server.configureBlocking(false);
                String port = Integer.toString(((java.net.InetSocketAddress) server.getLocalAddress()).getPort());
                d1r1Expect(d1r1Run(d1r1Control("tcp-connect", port)), "TCP_ALLOWED");
                try (var accepted = server.accept()) {
                    assertTrue(accepted != null, "the control connection must reach the listener");
                }
                Path tmp = d1Dir("d1-tmp-");
                List<String> command = d1Policy(root, tmp,
                        List.of("--expected-parent-pid", Long.toString(ProcessHandle.current().pid())),
                        List.of(d1r1Probe().getParent().toString()), List.of(), d1Environment(tmp));
                command.add(d1r1Probe().toString());
                command.addAll(List.of("tcp-connect", port));
                String[] result = d1r1Run(command);
                d1r1Denied(result, "TCP");
                assertTrue(result[2].contains("PARENT_OWNERSHIP_VERIFIED"), String.join("\n", result));
                assertEquals(null, server.accept(), "no connection may arrive from the restricted probe");
            }
            Process check = d3Helper(driver, root, db, 101, 102, false);
            d2Ok(driver, check, 1, null, null, "STOP", null, "null");
            d0Exit(check);
            assertEquals(before, d1Snapshot(outside), "no recovery path may touch the exterior");
            d0CloseClient(driver);
            d0CloseClient(seedClient);
            return null;
        });
    }

    @Test
    void d3T15NoNativeHandleCrossesDirectProtosIpc() throws Exception {
        d1RequireProfile();
        Path root = d1Dir("d3-t15-");
        Path db = root.resolve("d3.db");
        runD3(() -> {
            Process driver = d3Driver(111, 112);
            String event = "{\"version\":2,\"event\":\"DATABASE_IDENTITY\",\"identityHi\":HI,\"identityLo\":LO}";
            assertEquals("VERIFIED", d0Client(driver, "HELPER_IDENTITY", event.replace("HI", "111").replace("LO", "112")));
            assertEquals("MISMATCH", d0Client(driver, "HELPER_IDENTITY", event.replace("HI", "111").replace("LO", "113")));
            for (String vector : List.of(
                    event.replace("HI", "111").replace("LO", "112").replace("}", ",\"handle\":1}"),
                    event.replace("HI", "111").replace("LO", "112").replace("}", ",\"path\":\"/tmp/d3.db\"}"),
                    event.replace("HI", "\"111\"").replace("LO", "112"),
                    event.replace("HI", "{\"address\":1}").replace("LO", "112"),
                    event.replace("HI", "[111]").replace("LO", "112"),
                    event.replace("HI", "1.5").replace("LO", "112"),
                    event.replace("HI", "1E2").replace("LO", "112"),
                    event.replace("HI", "9223372036854775808").replace("LO", "112"),
                    event.replace("HI", "111").replace("LO", "null"),
                    event.replace("\"version\":2", "\"version\":1").replace("HI", "111").replace("LO", "112"),
                    event.replace("DATABASE_IDENTITY", "READY").replace("HI", "111").replace("LO", "112"),
                    "{\"version\":2,\"event\":\"DATABASE_IDENTITY\",\"identityHi\":111}")) {
                assertEquals("REJECTED", d0Client(driver, "HELPER_IDENTITY", vector), vector);
            }
            for (String value : List.of("{\"handle\":1}", "[1]", "\"0x7f00\"")) {
                assertEquals("REJECTED", d0Client(driver, "RESPONSE", d2Reply("1", "FOUND", value, "null")), value);
            }
            for (String source : List.of(D3_SESSION, "DurableProtocol.protos", "durable-client-driver.protos")) {
                String text = Files.readString(D0.resolve(source));
                assertFalse(text.contains("import(\"std:ffi\")") || text.contains("import(\"ffi:"), source);
            }
            d3Provision(driver, root, db, 111, 112);
            Process helper = d3Helper(driver, root, db, 111, 112, false);
            d2Ok(driver, helper, 1, null, null, "INIT", null, "null");
            d0Raw(driver, helper, d2Json("2", "301", "1", "\"APPLY_ONCE\"", "{\"handle\":1}"),
                    "ACCEPTED 2 ERROR null INVALID_VALUE");
            d0Raw(driver, helper, d2Json("3", "301", "1", "\"APPLY_ONCE\"", "[1]"),
                    "ACCEPTED 3 ERROR null INVALID_VALUE");
            d0Raw(driver, helper, d2Json("4", "{\"handle\":1}", "1", "\"LOOKUP\"", "null"),
                    "ACCEPTED 4 ERROR null INVALID_IDENTITY");
            // A real reply is one flat JSON object of copied scalars.
            d0Send(helper, d2Text(driver, 5, "301", "1", "APPLY_ONCE", "7"));
            String reply = d0Next(helper);
            assertEquals("ACCEPTED APPLIED 7 null", d0Client(driver, "MATCH", "5", reply));
            assertEquals(1, reply.chars().filter(c -> c == '{').count(), reply);
            assertFalse(reply.contains("["), reply);
            d2State(driver, helper, 6, 1, 1, 7);
            d2Ok(driver, helper, 9, null, null, "STOP", null, "null");
            d0Exit(helper);
            d0CloseClient(driver);
            return null;
        });
    }

    @Test
    void d3T16D2ReconciliationRequiresExplicitSourceTermination() throws Exception {
        d1RequireProfile();
        Path before = d1Dir("d3-t16-before-");
        Path after = d1Dir("d3-t16-after-");
        Path eof = d1Dir("d3-t16-eof-");
        runD0(() -> {
            Process client = d2Client();
            assertEquals("REJECTED", d0Client(client, "SOURCE_TERMINATED", "101", "7001"),
                    "termination evidence needs a recorded uncertain key");
            // D2-T7: killed before COMMIT. ABSENT alone is only an observation.
            Path db = before.resolve("d2.db");
            d2Initialize(client, before, db);
            Process b = d2Helper(client, before, db);
            d0Send(b, d2Text(client, 1, "101", "7001", "HOLD_BEFORE_COMMIT", "42"));
            d0AwaitEvent(b, "DURABLE_PRECOMMIT");
            d0Kill(b);
            assertEquals("UNCERTAIN 101 7001 CONNECTION_LOST", d0Client(client, "LOST", "1", "101", "7001", "42"));
            Process c = d2Helper(client, before, db);
            assertEquals("RESOLVED ABSENT_OBSERVED null", d2Reconcile(client, c, 1, 101, 7001));
            assertEquals("STATE ABSENT_OBSERVED", d0Client(client, "STATE", "101", "7001"));
            // d0Kill observed exit 137: the host records the source's effective termination.
            assertEquals("TERMINATED 101 7001", d0Client(client, "SOURCE_TERMINATED", "101", "7001"));
            assertEquals("STATE ABSENT_OBSERVED", d0Client(client, "STATE", "101", "7001"),
                    "evidence alone changes no state");
            assertEquals("RESOLVED NOT_APPLIED null", d2Reconcile(client, c, 2, 101, 7001));
            assertEquals("REJECTED", d0Client(client, "SOURCE_TERMINATED", "101", "7001"));
            d2Expect(client, c, 3, 101L, 7001L, "APPLY_ONCE", 42L, "APPLIED 42");
            d2Expect(client, c, 4, 101L, 7001L, "APPLY_ONCE", 42L, "REPLAYED 42");
            d2State(client, c, 5, 1, 1, 42);
            d2Ok(client, c, 8, null, null, "STOP", null, "null");
            d0Exit(c);
            // D2-T8: killed after COMMIT before ACK, with termination evidence: COMMITTED.
            Path db2 = after.resolve("d2.db");
            d2Initialize(client, after, db2);
            Process d = d2Helper(client, after, db2);
            d0Send(d, d2Text(client, 1, "102", "7002", "HOLD_AFTER_COMMIT", "43"));
            d0AwaitEvent(d, "DURABLE_COMMITTED_BEFORE_ACK");
            d0Kill(d);
            assertEquals("UNCERTAIN 102 7002 CONNECTION_LOST", d0Client(client, "LOST", "1", "102", "7002", "43"));
            assertEquals("TERMINATED 102 7002", d0Client(client, "SOURCE_TERMINATED", "102", "7002"));
            Process e = d2Helper(client, after, db2);
            assertEquals("RESOLVED COMMITTED 43", d2Reconcile(client, e, 1, 102, 7002));
            d2Expect(client, e, 2, 102L, 7002L, "APPLY_ONCE", 43L, "REPLAYED 43");
            d2State(client, e, 3, 1, 1, 43);
            d2Ok(client, e, 6, null, null, "STOP", null, "null");
            d0Exit(e);
            // D2-T15: lost acknowledgement through EOF; the fail-closed exit is observed first.
            Path db3 = eof.resolve("d2.db");
            d2Initialize(client, eof, db3);
            Process f = d2Helper(client, eof, db3);
            d0Send(f, d2Text(client, 1, "103", "7003", "HOLD_AFTER_COMMIT", "44"));
            d0AwaitEvent(f, "DURABLE_COMMITTED_BEFORE_ACK");
            f.getOutputStream().close();
            d0AwaitEvent(f, "DURABLE_ACK_WITHHELD");
            assertEquals(D0_EOF, d0Next(f), "a withheld acknowledgement is never sent");
            assertTrue(f.waitFor(30, TimeUnit.SECONDS), "fail-closed helper termination timeout");
            assertNotEquals(0, f.exitValue());
            assertEquals("UNCERTAIN 103 7003 CONNECTION_LOST", d0Client(client, "LOST", "1", "103", "7003", "44"));
            assertEquals("TERMINATED 103 7003", d0Client(client, "SOURCE_TERMINATED", "103", "7003"));
            Process g = d2Helper(client, eof, db3);
            assertEquals("RESOLVED COMMITTED 44", d2Reconcile(client, g, 1, 103, 7003));
            d2State(client, g, 2, 1, 1, 44);
            d2Ok(client, g, 5, null, null, "STOP", null, "null");
            d0Exit(g);
            d0CloseClient(client);
            return null;
        });
    }

    /** Runs one D3 scenario; D0 cleanup kills the JUnit children, then no tracked helper may run. */
    private static void runD3(java.util.concurrent.Callable<?> scenario) throws Exception {
        d3Helpers.clear();
        Throwable primary = null;
        try {
            runD0(scenario);
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            AssertionError retained = null;
            for (D3Pid helper : d3Helpers) {
                if (d3Running(helper)) {
                    ProcessHandle.of(helper.pid()).ifPresent(ProcessHandle::destroyForcibly);
                }
                if (!d3AwaitGone(helper, 15)) {
                    if (retained == null) retained = new AssertionError("D3 helper retained after cleanup");
                    retained.addSuppressed(new AssertionError("PID=" + helper.pid()));
                }
            }
            if (retained != null) {
                if (primary != null) primary.addSuppressed(retained);
                else throw retained;
            }
        }
    }

    /** Compiles the D3 process-tree fixture next to the D1 launcher, once. */
    private static Path d3Owner() throws Exception {
        if (d3OwnerExec == null) d3OwnerExec = d1Compile("owner-exec");
        return d3OwnerExec;
    }

    /** A durable client driver bound to the trusted bootstrap database identity. */
    private static Process d3Driver(long identityHi, long identityLo) throws Exception {
        Process driver = d2Client();
        assertEquals("BOUND " + identityHi + " " + identityLo,
                d0Client(driver, "BIND_DATABASE", Long.toString(identityHi), Long.toString(identityLo)));
        return driver;
    }

    /**
     * A D3 helper launched by JUnit behind the D1-R1 launcher (no parent custody: used for
     * provisioning and independent reconciliation). READY and a DATABASE_IDENTITY VERIFIED by the
     * Protos driver must precede any request.
     */
    private static Process d3Helper(Process driver, Path grant, Path db, long identityHi, long identityLo,
            boolean provision) throws Exception {
        List<String> arguments = new ArrayList<>(List.of(db.toString(), Long.toString(identityHi),
                Long.toString(identityLo)));
        if (provision) arguments.add("PROVISION");
        Process helper = d1Launch(grant, List.of(), D0.resolve("durable-helper.protos"),
                arguments.toArray(String[]::new));
        d0AwaitEvent(helper, "LANDLOCK_APPLIED");
        String log;
        synchronized (d0Logs.get(helper)) { log = d0Logs.get(helper).toString(); }
        assertTrue(log.contains(D1R1_PROFILE + "\n"), log);
        assertEquals("ACCEPTED", d0Client(driver, "READY", d0Next(helper)));
        assertEquals("VERIFIED", d0Client(driver, "HELPER_IDENTITY", d0Next(helper)));
        return helper;
    }

    /** Provisions a brand-new database with its identity and the durable schema, then stops. */
    private static void d3Provision(Process driver, Path grant, Path db, long identityHi, long identityLo)
            throws Exception {
        Process helper = d3Helper(driver, grant, db, identityHi, identityLo, true);
        d2Ok(driver, helper, 1, null, null, "INIT", null, "null");
        d2Ok(driver, helper, 2, null, null, "STOP", null, "null");
        d0Exit(helper);
    }

    /** A D3 helper whose identity bootstrap must be refused before READY. */
    private static void d3Refused(Path grant, Path db, String identityHi, String identityLo, String policy)
            throws Exception {
        List<String> arguments = new ArrayList<>(List.of(db.toString(), identityHi, identityLo));
        if (policy != null) arguments.add(policy);
        Process helper = d1Launch(grant, List.of(), D0.resolve("durable-helper.protos"),
                arguments.toArray(String[]::new));
        d0AwaitEvent(helper, "LANDLOCK_APPLIED");
        assertEquals(D0_EOF, d0Next(helper), "a refused identity must never publish READY");
        d0AwaitEvent(helper, "ADMISSION_FAILED_CLOSED");
        assertTrue(helper.waitFor(30, TimeUnit.SECONDS), "refused helper termination timeout");
        assertNotEquals(0, helper.exitValue());
        String log;
        synchronized (d0Logs.get(helper)) { log = d0Logs.get(helper).toString(); }
        assertTrue(log.contains("DURABLE_DATABASE_IDENTITY_REFUSED\n"), log);
    }

    /** The Protos CLI JVM command line shared by the D3 client and helper. */
    private static List<String> d3Java(List<String> jvmOptions, boolean provider, Path entry,
            String... arguments) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        assertTrue(classpath != null && !classpath.isBlank(), "missing test subprocess classpath");
        Path java = Path.of(System.getProperty("java.home"), "bin", "java").toRealPath();
        List<String> command = new ArrayList<>(List.of(java.toString(),
                "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"));
        command.addAll(jvmOptions);
        command.addAll(List.of("-cp", classpath, "com.guillermomolina.protos.cli.ProtosCli"));
        if (provider) command.addAll(List.of("--foreign-provider-path", sqliteJar.toAbsolutePath().toString()));
        command.add(entry.toAbsolutePath().toString());
        command.addAll(List.of(arguments));
        return command;
    }

    /**
     * Launches owner-exec: the confined helper (identity {@code helperHi/helperLo}) and the Protos
     * session client (scenario and bootstrap identity {@code clientHi/clientLo}) as parent and child.
     * Every stderr line of both processes is queued; stdout must reach EOF without a line.
     */
    private static D3Session d3Session(Path grant, Path db, long helperHi, long helperLo, String scenario,
            long clientHi, long clientLo) throws Exception {
        Path tmp = d1Dir("d3-tmp-");
        List<String> command = new ArrayList<>(List.of(d3Owner().toString(), "--owner-helper"));
        command.addAll(d1Policy(grant, tmp, List.of(), List.of(), List.of(), d1Environment(tmp)));
        command.addAll(d3Java(List.of("-XX:-UsePerfData", "-Djava.io.tmpdir=" + tmp, "-Duser.home=" + tmp,
                        "-Dpolyglot.engine.userResourceCache=" + tmp.resolve("polyglot-cache")), true,
                D0.resolve("durable-helper.protos"), db.toString(), Long.toString(helperHi),
                Long.toString(helperLo)));
        command.add("--owner-client");
        List<String> clientOptions = new ArrayList<>();
        String cache = System.getProperty("polyglot.engine.userResourceCache");
        if (cache != null) clientOptions.add("-Dpolyglot.engine.userResourceCache=" + cache);
        command.addAll(d3Java(clientOptions, false, D0.resolve(D3_SESSION), scenario,
                Long.toString(clientHi), Long.toString(clientLo)));
        Path home = Path.of("").toAbsolutePath();
        ProcessBuilder builder = new ProcessBuilder(command).directory(home.toFile());
        builder.environment().put("PROTOS_HOME", home.toString());
        Process client = builder.start();
        var lines = new java.util.concurrent.LinkedBlockingQueue<String>();
        var events = new java.util.concurrent.LinkedBlockingQueue<String>();
        StringBuilder log = new StringBuilder();
        d0Stdout.put(client, lines);
        d0Events.put(client, events);
        d0Logs.put(client, log);
        Thread.ofPlatform().daemon().name("d3-stdout-" + client.pid()).start(() -> {
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null; ) lines.offer(line);
            } catch (Exception failure) {
                synchronized (log) { log.append("stdout drain: ").append(failure).append('\n'); }
            } finally {
                lines.offer(D0_EOF);
            }
        });
        Thread.ofPlatform().daemon().name("d3-stderr-" + client.pid()).start(() -> {
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(client.getErrorStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    synchronized (log) { log.append(line).append('\n'); }
                    events.offer(line);
                }
            } catch (Exception failure) {
                synchronized (log) { log.append("stderr drain: ").append(failure).append('\n'); }
            } finally {
                events.offer(D0_EOF);
            }
        });
        String owned = d3AwaitPrefix(client, "OWNER_HELPER_PID ");
        D3Pid helper = d3Track(Long.parseLong(owned.substring("OWNER_HELPER_PID ".length()).strip()));
        return new D3Session(client, helper);
    }

    /** Skips stderr lines until exactly {@code expected}; EOF or the deadline fails. */
    private static void d3Await(Process client, String expected) throws Exception {
        d3AwaitMatching(client, expected::equals, expected);
    }

    private static String d3AwaitPrefix(Process client, String prefix) throws Exception {
        return d3AwaitMatching(client, line -> line.startsWith(prefix), prefix);
    }

    private static String d3AwaitMatching(Process client, java.util.function.Predicate<String> match,
            String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (true) {
            long remaining = deadline - System.nanoTime();
            String line = remaining > 0 ? d0Events.get(client).poll(remaining, TimeUnit.NANOSECONDS) : null;
            if (line == null || line.equals(D0_EOF)) {
                throw new AssertionError("PID=" + client.pid() + " missing " + description + "\n" + d3Log(client));
            }
            if (match.test(line)) return line;
        }
    }

    private static String d3Log(Process client) {
        StringBuilder log = d0Logs.get(client);
        synchronized (log) { return log.toString(); }
    }

    /** /proc/PID/stat fields after the command name: [0] state, [1] ppid, [19] start time. */
    private static String[] d3Stat(long pid) {
        try {
            String stat = Files.readString(Path.of("/proc", Long.toString(pid), "stat"));
            return stat.substring(stat.lastIndexOf(')') + 2).strip().split(" ");
        } catch (java.io.IOException failure) {
            return null;
        }
    }

    private static D3Pid d3Track(long pid) {
        String[] stat = d3Stat(pid);
        assertTrue(stat != null, "helper PID=" + pid + " not observable");
        D3Pid helper = new D3Pid(pid, stat[19]);
        d3Helpers.add(helper);
        return helper;
    }

    /** "GONE" when absent or when the PID now names another process. */
    private static String d3State(D3Pid helper) {
        String[] stat = d3Stat(helper.pid());
        if (stat == null || !stat[19].equals(helper.startTime())) return "GONE";
        return stat[0];
    }

    /** Running means able to execute code: present, same process, neither zombie nor dead. */
    private static boolean d3Running(D3Pid helper) {
        String state = d3State(helper);
        return !List.of("GONE", "Z", "X", "x").contains(state);
    }

    private static long d3Parent(D3Pid helper) {
        String[] stat = d3Stat(helper.pid());
        assertTrue(stat != null && stat[19].equals(helper.startTime()), "helper not observable");
        return Long.parseLong(stat[1]);
    }

    /** Observes termination of a non-child process within a bounded deadline. */
    private static boolean d3AwaitGone(D3Pid helper, long seconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (d3Running(helper)) {
            if (System.nanoTime() >= deadline) return false;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        }
        return true;
    }

    private static void d3AwaitState(D3Pid helper, String expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!d3State(helper).equals(expected)) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("helper state " + d3State(helper) + ", expected " + expected);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        }
    }

    private static String d3Command(long pid) throws Exception {
        return new String(Files.readAllBytes(Path.of("/proc", Long.toString(pid), "cmdline")),
                StandardCharsets.UTF_8).replace('\u0000', ' ');
    }

    /** Sends one signal from the unrestricted harness to a tracked, still running helper. */
    private static void d3Signal(D3Pid helper, int signal) {
        assertTrue(d3Running(helper), "helper not running before signal " + signal);
        try {
            MethodHandle kill = Linker.nativeLinker().downcallHandle(
                    Linker.nativeLinker().defaultLookup().find("kill").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            assertEquals(0, (int) kill.invokeExact((int) helper.pid(), signal), "kill failed");
        } catch (Throwable failure) {
            throw new AssertionError("host kill diagnostic failed", failure);
        }
    }

    /** SIGKILL of the client JVM; its helper must then stop running. */
    private static void d3KillClient(D3Session session) throws Exception {
        Process client = session.client();
        assertTrue(client.isAlive(), "client died before SIGKILL");
        client.destroyForcibly();
        assertTrue(client.waitFor(30, TimeUnit.SECONDS), "client SIGKILL timeout");
        assertEquals(137, client.exitValue());
        assertTrue(d3AwaitGone(session.helper(), 30),
                "helper still running after client death, state=" + d3State(session.helper()));
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

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
 * I144 Java test host of the SQLite-independent ABI fixture.
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

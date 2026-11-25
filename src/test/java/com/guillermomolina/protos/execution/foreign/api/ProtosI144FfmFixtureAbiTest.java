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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * I144 Java test host of the SQLite-independent ABI fixture.
 *
 * <p>This host builds the test-only registration
 * {@code ffi-test:} ({@code plugins/ffm-fixture}, the generic FFM backend under a fixture-only
 * target policy) against the SPI-only class path, compiles {@code native/ffi_test.c}, and selects
 * that library exclusively through {@code ProtosForeignProviderConfiguration.withProviderOptions}.
 * Nothing in the JAR authorizes a library. The same classes are also packaged under the
 * distributable {@code protos-ffm} registration to cover its {@code library.<alias>} host
 * authorization table. Skipped (not passed) off Linux x86-64 or without a C
 * compiler.
 */
class ProtosI144FfmFixtureAbiTest {
    private static final Path CASES = Path.of("protos", "tests", "foreign-provider");
    private static final String PROVIDER_ID = "protos-ffm-fixture";
    private static final String IMPORT = "ffi: import(\"ffi-test:ffi_test\")\n";

    @TempDir static Path work;
    static Path jar;
    static Path distributable;
    static Path library;
    static Path secondLibrary;
    static Path decoy;

    @BeforeAll
    static void buildFixture() throws Exception {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        assumeTrue(
                System.getProperty("os.name", "").equalsIgnoreCase("linux")
                        && (arch.equals("amd64") || arch.equals("x86_64")),
                "the FFM provider supports Linux x86-64 only");
        Path compiler = findCompiler();
        assumeTrue(compiler != null, "no C compiler (cc, gcc, or clang) on PATH");

        library = compileFixture(compiler, "ffi_test");
        secondLibrary = compileFixture(compiler, "ffi_second");

        Path classes = Files.createDirectories(work.resolve("classes"));
        List<String> arguments =
                new ArrayList<>(
                        List.of("--release", "25", "-proc:none", "-classpath",
                                ProtosExternalForeignProviderTest.publicClassPath(work)
                                        + File.pathSeparator
                                        + ProtosExternalForeignProviderTest.imageInfoJar(),
                                "-d", classes.toString()));
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
        distributable =
                ProtosExternalForeignProviderTest.jar(
                        Files.createDirectories(work.resolve("distributable")),
                        classes,
                        "com.guillermomolina.protos.ffm",
                        "com.guillermomolina.protos.ffm.FfmForeignProvider");

        // A test-only provider whose MODULE facade exposes a counted assumeStaticExtent.
        Path decoySources = Files.createDirectories(work.resolve("decoy-src").resolve("i174d"));
        Path decoySource = decoySources.resolve("Decoy.java");
        Files.writeString(decoySource, DECOY_SOURCE);
        Path decoyClasses = Files.createDirectories(work.resolve("decoy-classes"));
        ByteArrayOutputStream decoyDiagnostics = new ByteArrayOutputStream();
        assertEquals(
                0,
                ToolProvider.getSystemJavaCompiler()
                        .run(null, decoyDiagnostics, decoyDiagnostics, "--release", "25",
                                "-proc:none", "-classpath",
                                ProtosExternalForeignProviderTest.publicClassPath(work), "-d",
                                decoyClasses.toString(), decoySource.toString()),
                decoyDiagnostics::toString);
        decoy =
                ProtosExternalForeignProviderTest.jar(
                        Files.createDirectories(work.resolve("decoy")),
                        decoyClasses,
                        "i174d",
                        DECOY);
    }

    private static Path compileFixture(Path compiler, String name) throws Exception {
        Path target = work.resolve("lib" + name + ".so");
        Process cc =
                new ProcessBuilder(
                                compiler.toString(), "-shared", "-fPIC", "-O2", "-std=c11",
                                "-Wall", "-Werror", "-o", target.toString(),
                                CASES.resolve("native").resolve(name + ".c").toString())
                        .redirectErrorStream(true)
                        .start();
        String ccOutput = new String(cc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(true, cc.waitFor(60, TimeUnit.SECONDS), "cc timed out");
        assertEquals(0, cc.exitValue(), ccOutput);
        return target;
    }

    /** The program without its CLI success marker: this embedded host has no print. */
    private static String withoutMarker(String file, String marker) throws Exception {
        String line = "\nprint(\"" + marker + "\")";
        String program = Files.readString(CASES.resolve(file));
        assertTrue(program.contains(line), file + " marker");
        return program.replace(line, "\n");
    }

    /**
     * Declarative layouts are pure Protos: the program runs with no authorized library at all
     * and opens no foreign provider session. Every semantic assertion is in the program.
     */
    @Test
    void layoutProgramRunsWithoutAnyLibraryOrProviderSession() throws Exception {
        String source = withoutMarker("ffm-layouts.protos", "FFM-LAYOUTS-PASS");
        try (ProtosExternalForeignProviderTest.Fixture fixture = distributable(Map.of())) {
            ProtosExecutionOutcome outcome = fixture.run(source);
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () -> "ffm-layouts.protos failed with " + describe(fixture, outcome.error()));
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());

            // Public surface: the association operations are published; the private
            // recognition facility and its operation are not, nor are they in the Prelude
            // or on the canonical Object prototype.
            ProtosObjectValue module = (ProtosObjectValue) fixture.eval("import(\"std:ffi\")");
            java.util.Set<String> surface = module.localSlotsSnapshot().keySet();
            for (String name : List.of("prototype", "layoutOf", "struct", "bindPreserving")) {
                assertTrue(surface.contains(name), name);
            }
            for (String name :
                    List.of("_ffiObjectRecognitionFacility", "recognition", "isOrdinaryObject",
                            "ordinaryObject", "associations")) {
                assertTrue(!surface.contains(name), name);
                assertTrue(fixture.prelude.bindings().readLocalSlot(name).isEmpty(), name);
            }
            ProtosObjectValue object =
                    (ProtosObjectValue) fixture.prelude.bindings().readLocalSlot("Object")
                            .orElseThrow();
            assertTrue(!object.localSlotsSnapshot().containsKey("ordinaryObject"));
            assertTrue(!object.localSlotsSnapshot().containsKey("_ffiObjectRecognitionFacility"));
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());
        }
    }

    /**
     * Prototype associations are Actor-local MODULE state: each Actor declares and registers
     * independently, without native authority, and one Actor's registration is absent from the
     * other Actor's MODULE instance.
     */
    @Test
    void prototypeAssociationsAreActorLocalAndNeedNoNativeAuthority() throws Exception {
        String register =
                "FFI: import(\"std:ffi\")\n"
                        + "layout: FFI.struct(Array(FFI.field(\"x\", FFI.i32)))\n"
                        + "P: { x: 0 }\n"
                        + "FFI.prototype(layout, P)\n";
        try (ProtosExternalForeignProviderTest.Fixture fixture = distributable(Map.of())) {
            ProtosActivation observer = fixture.newActorActivation();
            Object rootPrototype = fixture.eval(register);
            Object observerPrototype = fixture.eval(observer, register);
            assertEquals(
                    Boolean.TRUE,
                    fixture.eval(observer, "FFI: import(\"std:ffi\")\n"
                            + "P: { x: 0 }\n"
                            + "layout: FFI.array(FFI.u8, 4)\n"
                            + "FFI.layoutOf(FFI.prototype(layout, P)) === layout"));
            // The root registration is not visible through the observer's MODULE instance,
            // and the observer's registration is not visible through the root's.
            Object observerLayoutOf = fixture.eval(observer, "import(\"std:ffi\").layoutOf");
            assertOrdinary(fixture, invoke(fixture, observerLayoutOf, rootPrototype));
            Object rootLayoutOf = fixture.eval("import(\"std:ffi\").layoutOf");
            assertOrdinary(fixture, invoke(fixture, rootLayoutOf, observerPrototype));
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());
        }
    }

    /**
     * Fixed-width scalar downcalls, outputs, preserving binding and C-measured layouts against
     * the compiled C fixtures, under two independently authorized libraries.
     */
    @Test
    void scalarAndLayoutAbiProgramRunsAgainstCompiledC() throws Exception {
        String source = withoutMarker("ffm-scalar-layout-abi.protos", "FFM-SCALAR-LAYOUT-ABI-PASS");
        try (ProtosExternalForeignProviderTest.Fixture fixture =
                distributable(
                        Map.of(
                                "library.first", library.toAbsolutePath().toString(),
                                "library.second", secondLibrary.toAbsolutePath().toString()))) {
            ProtosExecutionOutcome outcome = fixture.run(source);
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () -> "ffm-scalar-layout-abi.protos failed with "
                            + describe(fixture, outcome.error()));
        }
    }

    /**
     * Typed native pointer capabilities against the compiled C fixtures: plain, nullable and
     * borrowed results, pointer parameters and outs, the borrow epoch, cross-library passage and
     * original-owner provenance. Every semantic assertion is in the program.
     */
    @Test
    void pointerProgramRunsAgainstCompiledC() throws Exception {
        String source = withoutMarker("ffm-pointers.protos", "FFM-POINTERS-PASS");
        try (ProtosExternalForeignProviderTest.Fixture fixture =
                distributable(
                        Map.of(
                                "library.first", library.toAbsolutePath().toString(),
                                "library.second", secondLibrary.toAbsolutePath().toString()))) {
            ProtosExecutionOutcome outcome = fixture.run(source);
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () -> "ffm-pointers.protos failed with " + describe(fixture, outcome.error()));
        }
    }

    /**
     * Bounded native memory against the compiled C fixtures: allocation before any library
     * import, typed regions, slices, views, extent assertions and revocation. Every semantic
     * assertion is in the program; allocating before the import opens no second session.
     */
    @Test
    void nativeMemoryProgramRunsAgainstCompiledC() throws Exception {
        String source = withoutMarker("ffm-native-memory.protos", "FFM-NATIVE-MEMORY-PASS");
        try (ProtosExternalForeignProviderTest.Fixture fixture =
                distributable(
                        Map.of(
                                "library.first", library.toAbsolutePath().toString(),
                                "library.second", secondLibrary.toAbsolutePath().toString()))) {
            ProtosExecutionOutcome outcome = fixture.run(source);
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () -> "ffm-native-memory.protos failed with "
                            + describe(fixture, outcome.error()));
            assertEquals(1, fixture.context.foreignProviderCompartmentCountForTesting());
            assertEquals(1, fixture.context.foreignProviderSessionCountForTesting());
        }
    }

    /**
     * Importing std:ffi stays inert, and the private native-context facility is neither part of
     * the MODULE surface nor of the Prelude or the canonical Object prototype.
     */
    @Test
    void stdFfiImportIsInertAndTheNativeContextFacilityIsPrivate() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = distributable(Map.of())) {
            ProtosObjectValue module = (ProtosObjectValue) fixture.eval("import(\"std:ffi\")");
            assertEquals(0, fixture.context.foreignProviderCompartmentCountForTesting());
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());
            java.util.Set<String> surface = module.localSlotsSnapshot().keySet();
            for (String name :
                    List.of("cType", "allocate", "release", "addressOf", "slice", "view",
                            "assumeExtent")) {
                assertTrue(surface.contains(name), name);
            }
            for (String name :
                    List.of("_ffiNativeContextFacility", "_ffiObjectRecognitionFacility",
                            "nativeContextFacility", "nativeContext", "providerContext",
                            "storages", "physical", "cTypes", "acquire", "recognition")) {
                assertTrue(!surface.contains(name), name);
                assertTrue(fixture.prelude.bindings().readLocalSlot(name).isEmpty(), name);
            }
            ProtosObjectValue object =
                    (ProtosObjectValue) fixture.prelude.bindings().readLocalSlot("Object")
                            .orElseThrow();
            assertTrue(!object.localSlotsSnapshot().containsKey("acquire"));
            assertTrue(!object.localSlotsSnapshot().containsKey("_ffiNativeContextFacility"));
            // Layout declarations and queries still need no native context.
            assertEquals(
                    "16",
                    integer(fixture.eval("FFI: import(\"std:ffi\")\n"
                            + "FFI.sizeOf(FFI.struct(Array(FFI.field(\"a\", FFI.i32),"
                            + " FFI.field(\"b\", FFI.f64))))")));
            // A rejected C type spelling is refused before any provider use.
            assertOrdinary(fixture, fixture.run("import(\"std:ffi\").cType(\"float\")").error());
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());
        }
    }

    /**
     * The distributable provider configured without any library grant allocates: the session
     * context loads no library, creates no library arena, and confers no library authority.
     * Each allocation root enters the existing custody and leaves it once on release.
     */
    @Test
    void allocationNeedsNoLibraryGrantAndPerformsNoLibraryLookup() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = distributable(Map.of())) {
            Object region =
                    fixture.eval("FFI: import(\"std:ffi\")\n"
                            + "FFI.allocate(FFI.struct(Array(FFI.field(\"a\", FFI.i64))))");
            assertEquals(1, fixture.context.foreignProviderSessionCountForTesting());
            Object session = pluginSession(region);
            assertEquals(0, ((Map<?, ?>) field(session, "modules")).size());
            assertEquals(null, field(session, "arena"));
            assertEquals(1, diagnostic(session, "ownedCountForDiagnostics"));
            assertEquals(
                    "5",
                    integer(fixture.eval("FFI: import(\"std:ffi\")\n"
                            + "cell: FFI.allocate(FFI.i64)\ncell[0] = 5\ncell[0]")));
            assertEquals(2, diagnostic(session, "ownedCountForDiagnostics"));
            Object release = fixture.eval("import(\"std:ffi\").release");
            assertSame(
                    com.guillermomolina.protos.runtime.ProtosNullValue.INSTANCE,
                    ProtosInvocation.invoke(release, List.of(region), fixture.root));
            assertEquals(1, diagnostic(session, "ownedCountForDiagnostics"));
            assertOrdinary(fixture, invoke(fixture, release, region));
            assertEquals(1, diagnostic(session, "ownedCountForDiagnostics"));
            assertEquals(0, ((Map<?, ?>) field(session, "modules")).size());
            assertEquals(null, field(session, "arena"));
            assertEquals(1, fixture.context.foreignProviderSessionCountForTesting());
            // The session the allocation opened authorizes no library.
            assertForeign(fixture, fixture.run("import(\"ffi:first\")"), "unauthorized-library");
            assertEquals(0, diagnostic(session, "uncertainCountForDiagnostics"));
        }
    }

    /** The native context and later authorized imports share the RootActor's one session. */
    @Test
    void nativeContextSharesTheSessionOfLaterAuthorizedImports() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture =
                distributable(Map.of("library.first", library.toAbsolutePath().toString()))) {
            Object region = fixture.eval("import(\"std:ffi\").allocate(import(\"std:ffi\").i32)");
            ProtosForeignProviderSessionBinding binding = handle(region).session();
            ProtosForeignModuleFacadeValue facade =
                    (ProtosForeignModuleFacadeValue) fixture.eval("import(\"ffi:first\")");
            assertSame(binding, facade.attachmentForRuntime().orElseThrow().session());
            assertEquals(1, fixture.context.foreignProviderSessionCountForTesting());
            assertEquals(1, ((Map<?, ?>) field(pluginSession(region), "modules")).size());
        }
    }

    /**
     * Session close releases pending allocation roots and C-owned resources once; held regions,
     * views and pointers are then rejected before entry, and nothing reopens the session.
     */
    @Test
    void sessionCloseReleasesAllocationRootsAndNeverReopens() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture =
                distributable(Map.of("library.first", library.toAbsolutePath().toString()))) {
            ProtosObjectValue state =
                    (ProtosObjectValue)
                            fixture.eval("FFI: import(\"std:ffi\")\n"
                                    + "first: import(\"ffi:first\")\n"
                                    + "Inner: FFI.struct(Array(FFI.field(\"a\", FFI.i32),"
                                    + " FFI.field(\"b\", FFI.f64)))\n"
                                    + "kept: FFI.allocate(Inner)\n"
                                    + "dropped: FFI.allocate(Inner)\n"
                                    + "kept[\"a\"] = 7\n"
                                    + "counter: FFI.bind(first, \"ffi_test_counter_new\","
                                    + " FFI.owned(FFI.opaque(first, \"ffi_test_counter\"),"
                                    + " \"ffi_test_counter_free\", FFI.i32), Array(FFI.i64))(3)\n"
                                    + "{\n kept: kept\n dropped: dropped\n"
                                    + " bytes: FFI.slice(kept, 0, 4)\n"
                                    + " pointer: FFI.addressOf(kept)\n counter: counter\n"
                                    + " read: (region) => region[\"a\"]\n"
                                    + " readByte: (bytes) => bytes[0]\n"
                                    + " release: (value) => FFI.release(value)\n"
                                    + " view: (pointer) => FFI.view(pointer, Inner)\n"
                                    + " allocate: () => FFI.allocate(Inner)\n}");
            Object kept = slot(state, "kept");
            Object session = pluginSession(kept);
            assertEquals(3, diagnostic(session, "ownedCountForDiagnostics"));
            assertSame(
                    com.guillermomolina.protos.runtime.ProtosNullValue.INSTANCE,
                    ProtosInvocation.invoke(
                            slot(state, "release"), List.of(slot(state, "dropped")), fixture.root));
            assertEquals(2, diagnostic(session, "ownedCountForDiagnostics"));
            assertOrdinary(
                    fixture, invoke(fixture, slot(state, "release"), slot(state, "dropped")));
            assertEquals(
                    "7",
                    integer(ProtosInvocation.invoke(
                            slot(state, "read"), List.of(kept), fixture.root)));
            int sessions = fixture.context.foreignProviderSessionCountForTesting();

            ProtosForeignProviderSessionBinding binding = handle(kept).session();
            binding.closeForRuntime();
            binding.closeForRuntime();

            assertEquals(0, diagnostic(session, "ownedCountForDiagnostics"));
            assertEquals(0, diagnostic(session, "uncertainCountForDiagnostics"));
            assertOrdinary(fixture, invoke(fixture, slot(state, "read"), kept));
            assertOrdinary(fixture, invoke(fixture, slot(state, "readByte"), slot(state, "bytes")));
            assertOrdinary(fixture, invoke(fixture, slot(state, "view"), slot(state, "pointer")));
            assertOrdinary(fixture, invoke(fixture, slot(state, "release"), kept));
            assertOrdinary(
                    fixture, invoke(fixture, slot(state, "release"), slot(state, "counter")));
            assertOrdinary(fixture, invoke(fixture, slot(state, "allocate")));
            assertEquals(sessions, fixture.context.foreignProviderSessionCountForTesting());
            assertTrue(!binding.isOpenForRuntime());
        }
    }

    /**
     * Native memory needs the RootActor: a secondary Actor's allocation or C type query is the
     * ordinary pre-entry Error and opens no compartment or session, before and after the
     * RootActor's own session exists.
     */
    @Test
    void nativeMemoryRequiresTheRootActorBeforeAnySession() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture =
                distributable(Map.of("library.first", library.toAbsolutePath().toString()))) {
            ProtosActivation observer = fixture.newActorActivation();
            List<String> refused =
                    List.of(
                            "FFI: import(\"std:ffi\")\nFFI.allocate(FFI.i32)",
                            "FFI: import(\"std:ffi\")\nFFI.cType(\"int\")",
                            "FFI: import(\"std:ffi\")\nFFI.view(null, FFI.i32)",
                            "FFI: import(\"std:ffi\")\nFFI.addressOf(1)");
            for (String program : refused) {
                ProtosExecutionOutcome outcome = fixture.run(observer, program);
                assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state(), program);
                assertOrdinary(fixture, outcome.error());
                assertEquals(0, fixture.context.foreignProviderCompartmentCountForTesting());
                assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());
            }
            assertEquals(Boolean.TRUE, fixture.eval(observer, PURE_DECLARATIONS));
            fixture.eval("import(\"std:ffi\").allocate(import(\"std:ffi\").i32)");
            assertEquals(1, fixture.context.foreignProviderSessionCountForTesting());
            for (String program : refused) {
                assertOrdinary(fixture, fixture.run(observer, program).error());
            }
            assertEquals(1, fixture.context.foreignProviderSessionCountForTesting());
        }
    }

    /**
     * Regions and pointers never cross to another provider session: a region of the
     * distributable session is rejected by the fixture registration's session, and a pointer of
     * the fixture session is rejected by native-memory operations, all before entry.
     */
    @Test
    void wrongSessionRegionsAndPointersCannotCrossTheSpi() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture =
                new ProtosExternalForeignProviderTest.Fixture(
                        ProtosPolyglotRuntimeHost.openWithForeignProviders(
                                ProtosForeignProviderConfiguration.trustedInProcess(
                                                List.of(jar, distributable))
                                        .withProviderOptions(PROVIDER_ID, authorized())
                                        .withProviderOptions("protos-ffm", Map.of())),
                        true)) {
            Object region =
                    fixture.eval("FFI: import(\"std:ffi\")\n"
                            + "FFI.allocate(FFI.struct(Array(FFI.field(\"a\", FFI.i32))))");
            Object isLive = fixture.eval(IMPORT + "ffi.isLive");
            assertOrdinary(fixture, invoke(fixture, isLive, region));
            Object foreignPointer =
                    fixture.eval(IMPORT + "ffi.bind(\"ffi_test_pointer_octets\","
                            + " ffi.param(ffi.signature(ffi.pointerType(false)), ffi.i32))(1)");
            Object view = fixture.eval("FFI: import(\"std:ffi\")\n(p) => FFI.view(p, FFI.u8)");
            assertOrdinary(fixture, invoke(fixture, view, foreignPointer));
            Object assume =
                    fixture.eval("FFI: import(\"std:ffi\")\n"
                            + "(p) => FFI.assumeExtent(p, 1, p, \"read\")");
            assertOrdinary(fixture, invoke(fixture, assume, foreignPointer));
            assertEquals(2, fixture.context.foreignProviderSessionCountForTesting());
        }
    }

    /**
     * The library-static anchor admits only an actually attached FFM library facade of the
     * pointer's own session. A foreign MODULE facade of another provider exposing a matching
     * member, an ordinary object with a matching slot, a raw foreign reference and the FFM
     * facade of another provider session are each refused with the ordinary Error before any
     * member of theirs is read or executed, without advancing the borrow epoch or changing
     * native bytes; the authorized FFM facade itself stays admitted.
     */
    @Test
    void libraryAnchorRecognitionEntersNoForeignCode() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture =
                new ProtosExternalForeignProviderTest.Fixture(
                        ProtosPolyglotRuntimeHost.openWithForeignProviders(
                                ProtosForeignProviderConfiguration.trustedInProcess(
                                                List.of(jar, distributable, decoy))
                                        .withProviderOptions(PROVIDER_ID, authorized())
                                        .withProviderOptions(
                                                "protos-ffm",
                                                Map.of(
                                                        "library.first",
                                                        library.toAbsolutePath().toString()))),
                        true)) {
            Object outcome =
                    fixture.eval("FFI: import(\"std:ffi\")\n"
                            + "first: import(\"ffi:first\")\n"
                            + "decoy: import(\"decoy:demo\")\n"
                            + "other: import(\"ffi-test:ffi_test\")\n"
                            + "Inner: FFI.struct(Array(FFI.field(\"a\", FFI.i32),"
                            + " FFI.field(\"b\", FFI.f64)))\n"
                            + "statics: FFI.bind(first, \"ffi_test_memory_static\","
                            + " FFI.nullable(FFI.pointer(Inner)), Array(FFI.i32))\n"
                            + "p: statics(1)\n"
                            + "rawOther: statics(1)\n"
                            + "lent: FFI.bind(first, \"ffi_test_pointer_octets\","
                            + " FFI.borrowedPointer(FFI.pointer(FFI.u8)), Array(FFI.i32))(1)\n"
                            + "refused: 0\n"
                            + "imitation: {\n assumeStaticExtent: (a, b, c) => 1\n}\n"
                            + "for(anchor in Array(decoy, imitation, rawOther, other)) {\n"
                            + "    Error.handle(() => FFI.assumeExtent(p, 16, anchor, \"read\"),"
                            + " (e) => {\n"
                            + "        if(e.parent() === Error) { refused = refused + 1 }\n"
                            + "        e\n"
                            + "    })\n"
                            + "}\n"
                            + "(refused == 4) && (first.isLive(lent) == true)"
                            + " && (FFI.view(FFI.assumeExtent(p, 16, first, \"read\"),"
                            + " Inner)[\"a\"] == 7)"
                            + " && (first.isLive(lent) == true)");
            assertEquals(Boolean.TRUE, outcome);
            assertEquals(0, ProtosExternalForeignProviderTest.counter(fixture, DECOY, "READS"));
            assertEquals(
                    0, ProtosExternalForeignProviderTest.counter(fixture, DECOY, "EXECUTES"));
        }
    }

    private static final String DECOY = "i174d.Decoy";

    /** A module whose faithful {@code assumeStaticExtent} member counts reads and executions. */
    private static final String DECOY_SOURCE =
            """
            package i174d;

            import com.guillermomolina.protos.spi.foreign.ProtosForeignArgumentValue;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignPluginEnvironment;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignPluginSession;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignProviderPlugin;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignValueClass;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignValueOperations;
            import java.util.List;
            import java.util.Set;
            import java.util.concurrent.atomic.AtomicInteger;

            public final class Decoy implements ProtosForeignProviderPlugin {
                public static final AtomicInteger READS = new AtomicInteger();
                public static final AtomicInteger EXECUTES = new AtomicInteger();

                public static final class Demo {}

                record Op() {}

                public String providerId() { return "i174-decoy"; }

                public String scheme() { return "decoy"; }

                public String canonicalTarget(String target) { return target; }

                public ProtosForeignPluginSession openSession(
                        ProtosForeignPluginEnvironment environment) {
                    return new ProtosForeignPluginSession() {
                        public Object acquireModule(String target) { return new Demo(); }

                        public void close() {}
                    };
                }

                public ProtosForeignValueOperations valueOperations() {
                    return new ProtosForeignValueOperations() {
                        public String language() { return "i174-decoy"; }

                        public boolean supportsLongIntegerArguments() { return true; }

                        public ProtosForeignValueClass classify(
                                ProtosForeignPluginSession session, Object handle) {
                            if (handle instanceof Long number) {
                                return ProtosForeignValueClass.integral(number);
                            }
                            if (handle instanceof Op) {
                                return ProtosForeignValueClass.handle(null,
                                        Set.of(ProtosForeignValueClass.Capability.EXECUTABLE));
                            }
                            return ProtosForeignValueClass.handle(handle,
                                    Set.of(ProtosForeignValueClass.Capability.MEMBER_READ));
                        }

                        public boolean canFaithfullyReadMember(
                                ProtosForeignPluginSession session, Object handle, String name) {
                            return handle instanceof Demo && name.equals("assumeStaticExtent");
                        }

                        public Object readMember(
                                ProtosForeignPluginSession session, Object handle, String name) {
                            READS.incrementAndGet();
                            return new Op();
                        }

                        public Object execute(
                                ProtosForeignPluginSession session,
                                Object handle,
                                List<ProtosForeignArgumentValue> arguments) {
                            EXECUTES.incrementAndGet();
                            return 1L;
                        }
                    };
                }
            }
            """;

    /** Borrowed and out pointer declarations are pure Protos metadata: no provider session. */
    @Test
    void pointerDeclarationsDoNotOpenAProviderSession() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized())) {
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    fixture.run(
                                    "FFI: import(\"std:ffi\")\n"
                                            + "bytes: FFI.pointer(FFI.u8)\n"
                                            + "FFI.out(FFI.borrowedPointer(bytes))\n"
                                            + "FFI.out(bytes)\n"
                                            + "FFI.out(FFI.nullable(FFI.pointer(FFI.void)))")
                            .state());
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());
        }
    }

    private static final String POINTER_DECLARATIONS =
            "FFI: import(\"std:ffi\")\n"
                    + IMPORT
                    + "bytes: FFI.pointer(FFI.u8)\n"
                    + "counter: FFI.opaque(ffi, \"ffi_test_counter\")\n"
                    + "owned: FFI.owned(counter, \"ffi_test_counter_free\", FFI.i32)\n";

    /** Session close revokes plain, borrowed and owner-backed pointers before any C entry. */
    @Test
    void pointerCapabilitiesAreRejectedAfterSessionClose() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized())) {
            ProtosActivation observer = fixture.newActorActivation();
            ProtosForeignModuleFacadeValue facade =
                    (ProtosForeignModuleFacadeValue) fixture.eval(IMPORT + "ffi");
            Object plain =
                    fixture.eval(POINTER_DECLARATIONS
                            + "FFI.bind(ffi, \"ffi_test_pointer_octets\", bytes,"
                            + " Array(FFI.i32))(1)");
            Object read =
                    fixture.eval(POINTER_DECLARATIONS
                            + "FFI.bindPreserving(ffi, \"ffi_test_pointer_first\", FFI.i32,"
                            + " Array(bytes))");
            Object counterValue =
                    fixture.eval(POINTER_DECLARATIONS
                            + "FFI.bindPreserving(ffi, \"ffi_test_pointer_counter_value\","
                            + " FFI.i32,"
                            + " Array(FFI.pointer(FFI.void)))");
            Object calls =
                    fixture.eval(POINTER_DECLARATIONS
                            + "FFI.bindPreserving(ffi, \"ffi_test_pointer_calls_count\","
                            + " FFI.i32, Array())");
            Object isLive = fixture.eval(IMPORT + "ffi.isLive");
            Object address =
                    fixture.eval(POINTER_DECLARATIONS
                            + "FFI.addressOf(FFI.bind(ffi, \"ffi_test_counter_new\", owned,"
                            + " Array(FFI.i64))(5))");
            Object lent =
                    fixture.eval(POINTER_DECLARATIONS
                            + "FFI.bind(ffi, \"ffi_test_pointer_octets\","
                            + " FFI.borrowedPointer(bytes), Array(FFI.i32))(1)");
            // Independent declarations of FFI.pointer(FFI.u8) share the pointee identity.
            assertEquals("42", integer(ProtosInvocation.invoke(read, List.of(plain), fixture.root)));
            assertEquals("42", integer(ProtosInvocation.invoke(read, List.of(lent), fixture.root)));
            assertEquals(
                    "5",
                    integer(ProtosInvocation.invoke(counterValue, List.of(address), fixture.root)));
            String entered = integer(ProtosInvocation.invoke(calls, List.of(), fixture.root));
            int destroyedBefore = destroyedCount(fixture, observer);
            ProtosForeignProviderSessionBinding session =
                    facade.attachmentForRuntime().orElseThrow().session();
            // Every capability, the borrowed one included, is still live right before close.
            for (Object pointer : List.of(plain, lent, address)) {
                assertEquals(
                        Boolean.TRUE,
                        ProtosInvocation.invoke(isLive, List.of(pointer), fixture.root));
            }

            session.closeForRuntime();
            session.closeForRuntime();

            // The owner was destroyed exactly once by close; its pointer died with it.
            assertEquals(destroyedBefore + 1, destroyedCount(fixture, observer));
            assertOrdinary(fixture, invoke(fixture, read, plain));
            assertOrdinary(fixture, invoke(fixture, read, lent));
            assertOrdinary(fixture, invoke(fixture, counterValue, address));
            assertOrdinary(fixture, invoke(fixture, isLive, plain));
            assertEquals(entered, String.valueOf(nativeCount(fixture, observer,
                    "ffi_test_pointer_calls_count")));
            assertEquals(destroyedBefore + 1, destroyedCount(fixture, observer));
        }
    }

    /**
     * A NULL result in a non-nullable pointer out fails conversion only after C returned: the
     * owned counter of the same call was already adopted into session custody, stays there, and
     * is destroyed exactly once by session close, never again by a repeated close.
     */
    @Test
    void ownedOutputIsAdoptedBeforePointerConversionFailsAndDestroyedOnce() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(authorized())) {
            ProtosActivation observer = fixture.newActorActivation();
            int liveBaseline = nativeCount(fixture, observer, "ffi_test_live_count");
            int destroyedBaseline = destroyedCount(fixture, observer);
            ProtosForeignModuleFacadeValue facade =
                    (ProtosForeignModuleFacadeValue) fixture.eval(IMPORT + "ffi");
            ProtosExecutionOutcome outcome =
                    fixture.run(POINTER_DECLARATIONS
                            + "FFI.bind(ffi, \"ffi_test_pointer_mixed\", FFI.i32,"
                            + " Array(FFI.out(bytes), FFI.out(owned)))()");
            assertForeign(fixture, outcome, "null-result");
            assertEquals(liveBaseline + 1, nativeCount(fixture, observer, "ffi_test_live_count"));
            assertEquals(destroyedBaseline, destroyedCount(fixture, observer));

            ProtosForeignProviderSessionBinding session =
                    facade.attachmentForRuntime().orElseThrow().session();
            session.closeForRuntime();
            session.closeForRuntime();
            assertEquals(destroyedBaseline + 1, destroyedCount(fixture, observer));
            assertEquals(liveBaseline, nativeCount(fixture, observer, "ffi_test_live_count"));
        }
    }

    /**
     * The deliberately unregistered Native Image shape F(F,F) is a valid ABI signature: on the
     * JVM it links and really enters C, while a rejected argument still never does. The Native
     * Image refusal of the same declaration is covered by dist/validate_native.py.
     */
    @Test
    void unregisteredNativeShapeIsAnActualJvmDowncall() throws Exception {
        String program =
                "FFI: import(\"std:ffi\")\n"
                        + "first: import(\"ffi:first\")\n"
                        + "calls: FFI.bind(first, \"ffi_test_scalar_calls_count\", FFI.i32,"
                        + " Array())\n"
                        + "add: FFI.bind(first, \"ffi_test_f32_add\", FFI.f32,"
                        + " Array(FFI.f32, FFI.f32))\n"
                        + "before: calls()\n"
                        + "sum: add(1.5, 2.25)\n"
                        + "afterSum: calls()\n"
                        + "refused: false\n"
                        + "Error.handle(() => add(1, 2.0), (error) => {\n"
                        + "    refused = (error.parent() === ForeignError) == false\n"
                        + "    error\n"
                        + "})\n"
                        + "{\n"
                        + "    exact: sum == 3.75\n"
                        + "    float: Float.recognizes(sum)\n"
                        + "    entered: afterSum == before + 1\n"
                        + "    refused: refused\n"
                        + "    unchanged: calls() == afterSum\n"
                        + "}\n";
        try (ProtosExternalForeignProviderTest.Fixture fixture =
                distributable(Map.of("library.first", library.toAbsolutePath().toString()))) {
            ProtosExecutionOutcome outcome = fixture.run(program);
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED,
                    outcome.state(),
                    () -> "F(F,F) program failed with " + describe(fixture, outcome.error()));
            ProtosObjectValue published = (ProtosObjectValue) outcome.value();
            for (String slot : List.of("exact", "float", "entered", "refused", "unchanged")) {
                assertEquals(Boolean.TRUE, published.readLocalSlot(slot).orElseThrow(), slot);
            }
        }
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

    @Test
    void distributableProviderAuthorizesNoLibraryByDefault() throws Exception {
        for (Map<String, String> options :
                List.of(
                        Map.<String, String>of(),
                        Map.of("sqlite3.path", library.toAbsolutePath().toString()),
                        Map.of("library.first", library.toAbsolutePath().toString()))) {
            try (ProtosExternalForeignProviderTest.Fixture fixture = distributable(options)) {
                for (String alias : List.of("sqlite3", "ffi_test", "libc", "undeclared")) {
                    assertForeign(
                            fixture,
                            fixture.run("import(\"ffi:" + alias + "\")"),
                            "unauthorized-library");
                }
            }
        }
    }

    @Test
    void distributableProviderLoadsExactlyTheAuthorizedTargets() throws Exception {
        String path = library.toAbsolutePath().toString();
        Map<String, String> options =
                Map.of(
                        "library.first", path,
                        "library.other", path,
                        "library.sqlite3", path,
                        "library.missing", work.resolve("absent").resolve("libnone.so").toString(),
                        "library.unloadable", "libprotos_no_such_library.so.0");
        try (ProtosExternalForeignProviderTest.Fixture fixture = distributable(options)) {
            // Importing std:ffi acquires no library and opens no provider session.
            assertEquals(ProtosExecutionOutcome.State.COMPLETED,
                    fixture.run("FFI: import(\"std:ffi\")\nFFI.out(FFI.i32)").state());
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());

            // Several aliases under one provider; sqlite3 loads only the host's exact target.
            for (String alias : List.of("first", "other", "sqlite3")) {
                assertEquals("7", integer(fixture.eval(identity(alias, 7))), alias);
            }
            assertForeign(fixture, fixture.run("import(\"ffi:third\")"), "unauthorized-library");
            assertForeign(fixture, fixture.run("import(\"ffi:missing\")"), "library-unavailable");
            assertForeign(
                    fixture, fixture.run("import(\"ffi:unloadable\")"), "library-unavailable");

            // Syntactically invalid aliases follow ordinary import resolution.
            for (String rejected :
                    List.of("ffi:libffi_test.so", "ffi:" + path, "ffi:../first", "ffi:first.x")) {
                ProtosExecutionOutcome outcome =
                        fixture.run("import(\"" + rejected + "\")");
                assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state(), rejected);
                assertTrue(
                        outcome.error() == null
                                || outcome.error().parent().orElse(null)
                                        != fixture.standardError("ForeignError"),
                        () -> rejected + ": " + describe(fixture, outcome.error()));
            }
        }
    }

    /** Pure std:ffi declarations, usable from any Actor without native authority. */
    private static final String PURE_DECLARATIONS =
            "FFI: import(\"std:ffi\")\n"
                    + "layout: FFI.struct(Array(FFI.field(\"a\", FFI.i8), FFI.field(\"b\", FFI.i32)))\n"
                    + "P: { a: 0 }\n"
                    + "(FFI.sizeOf(FFI.i16) == 2) && (FFI.sizeOf(layout) == 8)"
                    + " && (FFI.layoutOf(FFI.prototype(layout, P)) === layout)";

    /**
     * Native FFM authority belongs to the RootActor alone. A secondary Actor's import of an
     * authorized library is an ordinary pre-entry Error that opens no compartment or session and
     * leaves nothing cached, before, during and after the RootActor's own session.
     */
    @Test
    void distributableNativeFfmRequiresRootActor() throws Exception {
        String path = library.toAbsolutePath().toString();
        try (ProtosExternalForeignProviderTest.Fixture fixture =
                distributable(Map.of("library.first", path))) {
            ProtosActivation observer = fixture.newActorActivation();
            assertEquals(0, fixture.context.foreignProviderCompartmentCountForTesting());
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());

            assertEquals(Boolean.TRUE, fixture.eval(observer, PURE_DECLARATIONS));
            for (int attempt = 0; attempt < 2; attempt++) {
                assertDeniedOutsideRoot(fixture, observer);
                assertEquals(0, fixture.context.foreignProviderCompartmentCountForTesting());
                assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());
            }
            assertEquals(Boolean.TRUE, fixture.eval(observer, PURE_DECLARATIONS));
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());

            // The RootActor acquires the same authorized library and enters real C.
            assertEquals("3", integer(fixture.eval(identity("first", 3))));
            assertEquals(1, fixture.context.foreignProviderCompartmentCountForTesting());
            assertEquals(1, fixture.context.foreignProviderSessionCountForTesting());
            ProtosForeignModuleFacadeValue rootFacade =
                    (ProtosForeignModuleFacadeValue)
                            fixture.eval("ffi: import(\"ffi:first\")\nffi");
            ProtosForeignProviderSessionBinding session =
                    rootFacade.attachmentForRuntime().orElseThrow().session();
            Object held =
                    fixture.eval("FFI: import(\"std:ffi\")\n"
                            + "FFI.bind(import(\"ffi:first\"), \"ffi_test_i32_identity\","
                            + " FFI.i32, Array(FFI.i32))");

            // The root session neither authorizes nor is shared with the secondary Actor.
            assertDeniedOutsideRoot(fixture, observer);
            assertEquals(1, fixture.context.foreignProviderSessionCountForTesting());
            assertTrue(session.isOpenForRuntime());
            assertEquals("5", integer(invokeValue(fixture, held, 5)));

            session.closeForRuntime();
            session.closeForRuntime();
            assertOrdinary(fixture, invoke(fixture, held, 1));
            // Closing the root session does not authorize the secondary Actor.
            assertDeniedOutsideRoot(fixture, observer);
        }
    }

    /**
     * The canonical foreign ModuleKey load path reaches the same session admission: a secondary
     * Actor cannot bypass it by loading the canonical key directly.
     */
    @Test
    void canonicalForeignModuleKeyCannotBypassRootActorAdmission() throws Exception {
        String path = library.toAbsolutePath().toString();
        try (ProtosExternalForeignProviderTest.Fixture fixture =
                distributable(Map.of("library.first", path))) {
            ProtosActivation observer = fixture.newActorActivation();
            ProtosModuleRuntime runtime =
                    ProtosStandardImportProtocol.runtimeForPrelude(fixture.prelude);
            for (int attempt = 0; attempt < 2; attempt++) {
                com.guillermomolina.protos.runtime.ProtosSignalException failure =
                        org.junit.jupiter.api.Assertions.assertThrows(
                                com.guillermomolina.protos.runtime.ProtosSignalException.class,
                                () ->
                                        runtime.loadCanonicalModule(
                                                FIRST_KEY,
                                                observer));
                assertOrdinary(fixture, failure.error());
                assertNotCached(observer);
            }
            assertEquals(0, fixture.context.foreignProviderCompartmentCountForTesting());
            assertEquals(0, fixture.context.foreignProviderSessionCountForTesting());
        }
    }

    /** RootActor identity alone authorizes no library: the host allowlist stays independent. */
    @Test
    void rootActorStillNeedsTheHostLibraryAuthorization() throws Exception {
        String path = library.toAbsolutePath().toString();
        try (ProtosExternalForeignProviderTest.Fixture fixture =
                distributable(Map.of("library.first", path))) {
            assertForeign(fixture, fixture.run("import(\"ffi:second\")"), "unauthorized-library");
        }
        try (ProtosExternalForeignProviderTest.Fixture fixture = distributable(Map.of())) {
            assertForeign(fixture, fixture.run("import(\"ffi:first\")"), "unauthorized-library");
        }
    }

    /** The canonical foreign ModuleKey of the authorized {@code ffi:first} library. */
    private static final com.guillermomolina.protos.runtime.modules.ProtosModuleKey FIRST_KEY =
            ProtosForeignModuleKey.encode("ffi", "first");

    /** A refused foreign import leaves no cached MODULE record in the refused Actor. */
    private static void assertNotCached(ProtosActivation actor) {
        assertTrue(actor.actorModuleState().lookup(FIRST_KEY).isEmpty());
    }

    /**
     * A secondary Actor's native import fails with exactly the ordinary pre-entry Error and
     * leaves no cached record for the foreign key.
     */
    private static void assertDeniedOutsideRoot(
            ProtosExternalForeignProviderTest.Fixture fixture, ProtosActivation actor) {
        ProtosExecutionOutcome outcome = fixture.run(actor, "import(\"ffi:first\")");
        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
        ProtosObjectValue error = outcome.error();
        assertSame(fixture.standardError("Error"), error.parent().orElseThrow(),
                () -> describe(fixture, error));
        assertNotCached(actor);
    }

    /** Invokes a held Closure directly and answers its result. */
    private static Object invokeValue(
            ProtosExternalForeignProviderTest.Fixture fixture, Object callable, int argument) {
        return ProtosInvocation.invoke(
                callable,
                List.of(com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integer(
                        argument)),
                fixture.root);
    }

    @Test
    void malformedLibraryOptionsFailBeforeAnyNativeAcquisition() throws Exception {
        String path = library.toAbsolutePath().toString();
        for (Map<String, String> options :
                List.of(
                        Map.of("library.first", path, "library.bad.alias", path),
                        Map.of("library.first", path, "library.", path),
                        Map.of("library.first", "relative/libffi_test.so"),
                        Map.of("library.first", ""),
                        Map.of("library.first", "lib\0ffi_test.so"))) {
            try (ProtosExternalForeignProviderTest.Fixture fixture = distributable(options)) {
                assertForeign(
                        fixture, fixture.run("import(\"ffi:first\")"), "invalid-host-option");
            }
        }
    }

    // ---- helpers

    /** The foreign handle of a raw provider value answered to Protos. */
    private static ProtosForeignHandle handle(Object raw) {
        return (ProtosForeignHandle)
                ((com.guillermomolina.protos.runtime.ProtosRawForeignValue) raw).handleForRuntime();
    }

    /** The provider's own session object behind a raw value's exact session binding. */
    private static Object pluginSession(Object raw) {
        return ((ProtosForeignPluginProvider.Session) handle(raw).session().sessionForRuntime())
                .plugin;
    }

    private static Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object diagnostic(Object target, String name) throws Exception {
        java.lang.reflect.Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object slot(ProtosObjectValue object, String name) {
        return object.readLocalSlot(name).orElseThrow(() -> new AssertionError(name));
    }

    private static ProtosExternalForeignProviderTest.Fixture distributable(
            Map<String, String> options) throws Exception {
        return new ProtosExternalForeignProviderTest.Fixture(
                ProtosPolyglotRuntimeHost.openWithForeignProviders(
                        ProtosForeignProviderConfiguration.trustedInProcess(List.of(distributable))
                                .withProviderOptions("protos-ffm", options)),
                true);
    }

    private static String identity(String alias, int value) {
        return "ffi: import(\"ffi:" + alias + "\")\n"
                + "ffi.bind(\"ffi_test_i32_identity\","
                + " ffi.param(ffi.signature(ffi.i32), ffi.i32))(" + value + ")";
    }

    /** Shapes owned by the confined Filesystem downcalls, not by the generic FFM provider. */
    private static final java.util.Set<String> FILESYSTEM_SHAPES =
            java.util.Set.of("P(P,P,P,P,P)", "L(P,I,P,L)", "L(P,P,L)");

    private static final Path METADATA =
            Path.of("src", "main", "resources", "META-INF", "native-image", "com.guillermomolina",
                    "protos", "reachability-metadata.json");

    /** Loads one compiled provider class of the fixture build, isolated from the test classes. */
    private static Class<?> providerClass(java.net.URLClassLoader loader, String simpleName)
            throws Exception {
        return Class.forName("com.guillermomolina.protos.ffm." + simpleName, true, loader);
    }

    private static java.net.URLClassLoader providerLoader() throws Exception {
        return new java.net.URLClassLoader(
                new java.net.URL[] {work.resolve("classes").toUri().toURL()},
                ProtosI144FfmFixtureAbiTest.class.getClassLoader());
    }

    /** Non-variadic downcall shapes of the image metadata, in the same letter notation. */
    private static java.util.Set<String> metadataShapes() throws Exception {
        Map<String, String> letters =
                Map.of("jbyte", "B", "jshort", "S", "jint", "I", "jlong", "L", "jfloat", "F",
                        "jdouble", "D", "void*", "P", "void", "V");
        java.util.Set<String> shapes = new java.util.TreeSet<>();
        java.util.regex.Matcher entry =
                java.util.regex.Pattern.compile(
                                "\\{\\s*\"returnType\":\\s*\"([^\"]+)\",\\s*"
                                        + "\"parameterTypes\":\\s*\\[([^\\]]*)\\]\\s*\\}")
                        .matcher(Files.readString(METADATA));
        while (entry.find()) {
            StringBuilder shape = new StringBuilder(letters.get(entry.group(1))).append('(');
            java.util.regex.Matcher parameter =
                    java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(entry.group(2));
            boolean first = true;
            while (parameter.find()) {
                shape.append(first ? "" : ",").append(letters.get(parameter.group(1)));
                first = false;
            }
            shapes.add(shape.append(')').toString());
        }
        return shapes;
    }

    /**
     * I170: the provider's registered Native shapes and the image metadata are one inventory.
     * Variadic downcalls carry options and are not matched by the plain-entry pattern.
     */
    @Test
    @SuppressWarnings("unchecked")
    void nativeShapeInventoryMatchesTheReachabilityMetadata() throws Exception {
        try (java.net.URLClassLoader loader = providerLoader()) {
            java.lang.reflect.Field field =
                    providerClass(loader, "FfmBinding").getDeclaredField("NATIVE_SHAPES");
            field.setAccessible(true);
            java.util.Set<String> registered =
                    new java.util.TreeSet<>((java.util.Set<String>) field.get(null));
            java.util.Set<String> metadata = metadataShapes();
            java.util.Set<String> missing = new java.util.TreeSet<>(registered);
            missing.removeAll(metadata);
            assertTrue(missing.isEmpty(), () -> "missing from metadata: " + missing);
            java.util.Set<String> extra = new java.util.TreeSet<>(metadata);
            extra.removeAll(registered);
            extra.removeAll(FILESYSTEM_SHAPES);
            assertTrue(extra.isEmpty(), () -> "missing from the provider inventory: " + extra);
            // Every physical shape of the current std:sqlite declarations is registered.
            for (String shape :
                    List.of("I()", "I(P)", "I(P,I)", "I(P,P)", "I(P,I,L)", "I(P,I,D)", "L(P,I)",
                            "D(P,I)", "I(P,P,I,P)", "I(P,P,I,P,P)", "I(P,I,P,I,P)", "P(P,I)")) {
                assertTrue(registered.contains(shape), shape);
            }
            // Exactly the shapes of the fixed-width scalar fixtures, not a Cartesian product.
            java.util.Set<String> scalarShapes =
                    java.util.Set.of("B(B)", "S(S)", "F(F)", "I(P,P,P)", "I(P,P,P,P)");
            for (String shape : scalarShapes) {
                assertTrue(registered.contains(shape), shape);
            }
            java.util.Set<String> narrow = new java.util.TreeSet<>();
            for (String shape : registered) {
                if (shape.matches(".*[BSF].*")) {
                    narrow.add(shape);
                }
            }
            assertEquals(java.util.Set.of("B(B)", "S(S)", "F(F)"), narrow);
            for (String shape : List.of("F(F,F)", "B(S)", "S(B)", "F(D)", "D(F)", "B()")) {
                assertTrue(!registered.contains(shape), shape);
            }
        }
    }

    /**
     * I170: physical shapes derive from the existing carrier layouts, owned destructors and out
     * slots are addresses, and an unregistered shape stays linkable on the JVM only.
     */
    @Test
    @SuppressWarnings("unchecked")
    void physicalShapesFollowTheCarrierLayouts() throws Exception {
        try (java.net.URLClassLoader loader = providerLoader()) {
            Class<?> type = providerClass(loader, "FfmTypes$Type");
            Class<?> scalar = providerClass(loader, "FfmTypes$Scalar");
            Class<?> opaque = providerClass(loader, "FfmTypes$OpaqueType");
            Class<?> out = providerClass(loader, "FfmTypes$OutType");
            Class<?> signature = providerClass(loader, "FfmBinding$Signature");
            java.lang.reflect.Constructor<?> create =
                    signature.getDeclaredConstructor(type, List.class, boolean.class);
            create.setAccessible(true);
            java.lang.reflect.Method shape = signature.getDeclaredMethod("physicalShape");
            shape.setAccessible(true);
            java.lang.reflect.Method linkable = signature.getDeclaredMethod("isLinkable");
            linkable.setAccessible(true);
            java.util.function.Function<String, Object> s =
                    name -> Enum.valueOf((Class) scalar, name);
            java.lang.reflect.Constructor<?> newOpaque =
                    opaque.getDeclaredConstructor(String.class, String.class, boolean.class);
            newOpaque.setAccessible(true);
            Object handle = newOpaque.newInstance("handle", "fixture", false);
            java.lang.reflect.Constructor<?> newOut = out.getDeclaredConstructor(type);
            newOut.setAccessible(true);
            Object outI32 = newOut.newInstance(s.apply("I32"));
            Object outI8 = newOut.newInstance(s.apply("I8"));
            Object outU64 = newOut.newInstance(s.apply("U64"));
            Object outF32 = newOut.newInstance(s.apply("F32"));

            Object[][] cases = {
                {s.apply("I32"), List.of(s.apply("I32"), s.apply("I32")), "I(I,I)"},
                {s.apply("I64"), List.of(s.apply("SIZE_T")), "L(L)"},
                {s.apply("F64"), List.of(s.apply("F64"), s.apply("F64")), "D(D,D)"},
                {s.apply("VOID"), List.of(), "V()"},
                {s.apply("I32"), List.of(handle), "I(P)"},
                {s.apply("VOID"), List.of(handle), "V(P)"},
                {s.apply("I32"), List.of(s.apply("UTF8Z"), s.apply("I32"), outI32), "I(P,I,P)"},
                // Signedness is semantic: one carrier letter per physical width.
                {s.apply("I8"), List.of(s.apply("I8")), "B(B)"},
                {s.apply("U8"), List.of(s.apply("U8")), "B(B)"},
                {s.apply("I16"), List.of(s.apply("I16")), "S(S)"},
                {s.apply("U16"), List.of(s.apply("U16")), "S(S)"},
                {s.apply("U32"), List.of(s.apply("U32")), "I(I)"},
                {s.apply("U64"), List.of(s.apply("U64")), "L(L)"},
                {s.apply("F32"), List.of(s.apply("F32")), "F(F)"},
                {s.apply("I32"), List.of(outI8, outU64, outF32), "I(P,P,P)"},
            };
            for (Object[] c : cases) {
                Object value = create.newInstance(c[0], c[1], false);
                assertEquals(c[2], shape.invoke(value));
                assertEquals(true, linkable.invoke(value));
            }
            // An exact but unregistered shape: valid on the JVM, refused by a Native Image.
            Object unregistered =
                    create.newInstance(
                            s.apply("F64"),
                            List.of(s.apply("F64"), s.apply("F64"), s.apply("F64")),
                            false);
            assertEquals("D(D,D,D)", shape.invoke(unregistered));
            java.lang.reflect.Field field =
                    providerClass(loader, "FfmBinding").getDeclaredField("NATIVE_SHAPES");
            field.setAccessible(true);
            assertTrue(!((java.util.Set<String>) field.get(null)).contains("D(D,D,D)"));
            assertEquals(true, linkable.invoke(unregistered));
            Object narrowUnregistered =
                    create.newInstance(
                            s.apply("F32"), List.of(s.apply("F32"), s.apply("F32")), false);
            assertEquals("F(F,F)", shape.invoke(narrowUnregistered));
            assertTrue(!((java.util.Set<String>) field.get(null)).contains("F(F,F)"));
            assertEquals(true, linkable.invoke(narrowUnregistered));
        }
    }

    private static String integer(Object value) {
        return com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(
                value);
    }

    /** A failed outcome whose error is a ForeignError whose provider category is {@code category}. */
    private static void assertForeign(
            ProtosExternalForeignProviderTest.Fixture fixture,
            ProtosExecutionOutcome outcome,
            String category) {
        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
        ProtosObjectValue error = outcome.error();
        assertSame(fixture.standardError("ForeignError"), error.parent().orElseThrow(),
                () -> describe(fixture, error));
        Object actual = error.localSlotsSnapshot().get("foreignCategory");
        assertTrue(
                actual instanceof com.guillermomolina.protos.runtime.ProtosStringValue string
                        && string.value().equals(category),
                () -> category + " expected: " + describe(fixture, error));
    }

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

    static Path findCompiler() {
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

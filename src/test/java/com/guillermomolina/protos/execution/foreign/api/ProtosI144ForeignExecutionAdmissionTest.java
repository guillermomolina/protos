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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * I144 whole-execution pre-entry admission ({@code acceptsExecution}) of the I085 SPI.
 *
 * <p>Two external plugins are compiled here against the SPI-only class path, with no native code:
 * {@code admitido:demo} validates arity and signature-dependent types in the new hook, and {@code
 * legado:demo} predates the hook. Every rejection must be the ordinary Protos Error, never a
 * {@code ForeignError}, and must not reach {@code execute}; a failure after entry stays a {@code
 * ForeignError}. Projected {@code call} and {@code std:interop.invoke} share the same boundary.
 */
class ProtosI144ForeignExecutionAdmissionTest {
    private static final String ADMIT = "i144a.Admit";
    private static final String M = "lib: import(\"admitido:demo\")\n";
    private static final String I = "I: import(\"std:interop\")\n";

    @TempDir static Path work;
    static Path admit;
    static Path legacy;

    @BeforeAll
    static void buildPluginJars() throws Exception {
        Path sources = Files.createDirectories(work.resolve("src"));
        Path classes = Files.createDirectories(work.resolve("classes"));
        Path admitSource = Files.createDirectories(sources.resolve("i144a")).resolve("Admit.java");
        Files.writeString(admitSource, ADMIT_SOURCE);
        Path legacySource =
                Files.createDirectories(sources.resolve("i144l")).resolve("Legacy.java");
        Files.writeString(legacySource, LEGACY_SOURCE);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<String> arguments =
                new ArrayList<>(
                        List.of(
                                "--release", "25", "-proc:none", "-classpath",
                                ProtosExternalForeignProviderTest.publicClassPath(work), "-d",
                                classes.toString(), admitSource.toString(),
                                legacySource.toString()));
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        assertEquals(
                0,
                javac.run(null, diagnostics, diagnostics, arguments.toArray(String[]::new)),
                diagnostics::toString);
        admit = ProtosExternalForeignProviderTest.jar(work, classes, "i144a", ADMIT);
        legacy = ProtosExternalForeignProviderTest.jar(work, classes, "i144l", "i144l.Legacy");
    }

    @Test
    void providersWithoutTheHookKeepTheirBehavior() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(legacy)) {
            assertEquals("42", integer(fixture.eval("import(\"legado:demo\").twice(21)")));
            // Pre-existing per-argument admission is unchanged.
            assertOrdinary(fixture, fixture.run("import(\"legado:demo\").twice(\"x\")").error());
            // Without the hook, wrong arity still enters the provider, which fails after entry.
            assertForeign(fixture, fixture.run("import(\"legado:demo\").twice()").error());
        }
    }

    @Test
    void wholeSignatureIsValidatedBeforeEntryForProjectedCall() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(admit)) {
            assertEquals("5", integer(fixture.eval(M + "lib.pair(2, \"abc\")")));
            int executes = counter(fixture, "EXECUTES");
            for (String rejected :
                    List.of(
                            "lib.pair(2)",
                            "lib.pair(2, \"abc\", 3)",
                            "lib.pair(2147483648, \"abc\")",
                            "lib.pair(-2147483649, \"abc\")",
                            "lib.pair(\"abc\", 2)",
                            "lib.pair(2, null)",
                            "lib.pair(2, \"a\\u{0}b\")")) {
                assertOrdinary(fixture, fixture.run(M + rejected).error());
            }
            // Every argument is individually acceptable: only the whole-signature hook refuses.
            assertEquals(executes, counter(fixture, "EXECUTES"));
            assertEquals("-2147483643", integer(fixture.eval(M + "lib.pair(-2147483648, \"abcde\")")));
        }
    }

    @Test
    void stdInteropInvokeSharesTheSameBoundary() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(admit)) {
            assertEquals("4", integer(fixture.eval(I + M + "I.invoke(lib.pair, 1, \"abc\")")));
            int executes = counter(fixture, "EXECUTES");
            assertOrdinary(fixture, fixture.run(I + M + "I.invoke(lib.pair, 1)").error());
            assertOrdinary(
                    fixture, fixture.run(I + M + "I.invoke(lib.pair, 4294967296, \"x\")").error());
            assertOrdinary(fixture, fixture.run(I + M + "I.invoke(lib.pair, 1, null)").error());
            assertEquals(executes, counter(fixture, "EXECUTES"));
        }
    }

    @Test
    void failureAfterEntryRemainsAForeignError() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(admit)) {
            ProtosObjectValue first = fixture.run(M + "lib.boom(1, \"x\")").error();
            ProtosObjectValue second = fixture.run(I + M + "I.invoke(lib.boom, 1, \"x\")").error();
            assertForeign(fixture, first);
            assertForeign(fixture, second);
            assertNotSame(first, second);
            assertEquals(2, counter(fixture, "EXECUTES"));
        }
    }

    @Test
    void closedSessionRejectsWithoutReopening() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(admit)) {
            ProtosForeignModuleFacadeValue facade =
                    (ProtosForeignModuleFacadeValue) fixture.eval(M + "lib");
            // A held executable handle, invoked directly (receiver = the handle itself).
            Object pair = fixture.eval(M + "lib.pair");
            ProtosForeignProviderSessionBinding session =
                    facade.attachmentForRuntime().orElseThrow().session();
            session.closeForRuntime();

            assertOrdinary(fixture, invokeFailure(fixture, pair, 1, "x"));
            assertNotForeign(fixture, fixture.run(M + "lib.pair(1, \"x\")").error());
            assertEquals(0, counter(fixture, "VALIDATIONS"));
            assertEquals(0, counter(fixture, "EXECUTES"));
            assertEquals(1, counter(fixture, "SESSIONS"));
            assertEquals(1, counter(fixture, "CLOSES"));
        }
    }

    @Test
    void closeRacingAfterAdmissionNeverEntersOrReopens() throws Exception {
        try (ProtosExternalForeignProviderTest.Fixture fixture = fixture(admit)) {
            ProtosForeignModuleFacadeValue facade =
                    (ProtosForeignModuleFacadeValue) fixture.eval(M + "lib");
            Object pair = fixture.eval(M + "lib.pair");
            ProtosForeignProviderSessionBinding session =
                    facade.attachmentForRuntime().orElseThrow().session();
            // The close lands after the hook admitted the call and before entry.
            setBeforeEntry(fixture, session::closeForRuntime);
            try {
                assertOrdinary(fixture, invokeFailure(fixture, pair, 1, "x"));
            } finally {
                setBeforeEntry(fixture, null);
            }
            assertFalse(session.isOpenForRuntime());
            assertEquals(1, counter(fixture, "VALIDATIONS"));
            assertEquals(0, counter(fixture, "EXECUTES"));
            assertOrdinary(fixture, invokeFailure(fixture, pair, 1, "x"));
            assertEquals(1, counter(fixture, "VALIDATIONS"));
            assertEquals(1, counter(fixture, "SESSIONS"));
            assertEquals(1, counter(fixture, "CLOSES"));
        }
    }

    /** Invokes a held projected value directly and answers the Error it signals. */
    static ProtosObjectValue invokeFailure(
            ProtosExternalForeignProviderTest.Fixture fixture, Object callable, Object... args) {
        List<Object> arguments = new ArrayList<>();
        for (Object argument : args) {
            arguments.add(
                    argument instanceof String text
                            ? new com.guillermomolina.protos.runtime.ProtosStringValue(text)
                            : com.guillermomolina.protos.runtime.ProtosNumericValueSupport
                                    .integer((Integer) argument));
        }
        com.guillermomolina.protos.runtime.ProtosSignalException failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.guillermomolina.protos.runtime.ProtosSignalException.class,
                        () -> ProtosInvocation.invoke(callable, arguments, fixture.root));
        return failure.error();
    }

    // ---- helpers

    private static ProtosExternalForeignProviderTest.Fixture fixture(Path jar) throws Exception {
        return new ProtosExternalForeignProviderTest.Fixture(
                ProtosPolyglotRuntimeHost.openWithForeignProviders(
                        ProtosForeignProviderConfiguration.trustedInProcess(List.of(jar))),
                true);
    }

    private static int counter(ProtosExternalForeignProviderTest.Fixture fixture, String field)
            throws Exception {
        return ProtosExternalForeignProviderTest.counter(fixture, ADMIT, field);
    }

    private static void setBeforeEntry(
            ProtosExternalForeignProviderTest.Fixture fixture, Runnable action) throws Exception {
        Class<?> type = Class.forName(ADMIT, false, fixture.host.foreignProviderLoaderForTesting());
        type.getField("BEFORE_ENTRY").set(null, action);
    }

    private static void assertOrdinary(
            ProtosExternalForeignProviderTest.Fixture fixture, ProtosObjectValue error) {
        assertSame(fixture.standardError("Error"), error.parent().orElseThrow());
    }

    /** Any ordinary pre-entry failure after close: never a ForeignError. */
    private static void assertNotForeign(
            ProtosExternalForeignProviderTest.Fixture fixture, ProtosObjectValue error) {
        assertNotSame(fixture.standardError("ForeignError"), error.parent().orElseThrow());
    }

    private static void assertForeign(
            ProtosExternalForeignProviderTest.Fixture fixture, ProtosObjectValue error) {
        assertSame(fixture.standardError("ForeignError"), error.parent().orElseThrow());
    }

    private static String integer(Object value) {
        return com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(
                value);
    }

    /** {@code pair(i32, utf8-text-without-NUL)} answers {@code number + text.length()}. */
    private static final String ADMIT_SOURCE =
            """
            package i144a;

            import com.guillermomolina.protos.spi.foreign.ProtosForeignArgumentValue;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignPluginEnvironment;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignPluginSession;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignProviderPlugin;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignValueClass;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignValueOperations;
            import java.util.List;
            import java.util.Set;
            import java.util.concurrent.atomic.AtomicInteger;

            public final class Admit implements ProtosForeignProviderPlugin {
                public static final AtomicInteger SESSIONS = new AtomicInteger();
                public static final AtomicInteger CLOSES = new AtomicInteger();
                public static final AtomicInteger VALIDATIONS = new AtomicInteger();
                public static final AtomicInteger EXECUTES = new AtomicInteger();
                public static volatile Runnable BEFORE_ENTRY;

                public static final class Demo {}

                record Op(String name) {}

                public String providerId() { return "i144-admit"; }

                public String scheme() { return "admitido"; }

                public String canonicalTarget(String target) {
                    if (!target.equals("demo")) {
                        throw new IllegalArgumentException("unknown module");
                    }
                    return target;
                }

                public ProtosForeignPluginSession openSession(
                        ProtosForeignPluginEnvironment environment) {
                    SESSIONS.incrementAndGet();
                    return new ProtosForeignPluginSession() {
                        public Object acquireModule(String target) { return new Demo(); }

                        public void close() { CLOSES.incrementAndGet(); }
                    };
                }

                public ProtosForeignValueOperations valueOperations() {
                    return new ProtosForeignValueOperations() {
                        public String language() { return "i144-admit"; }

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
                            return handle instanceof Demo
                                    && (name.equals("pair") || name.equals("boom"));
                        }

                        public Object readMember(
                                ProtosForeignPluginSession session, Object handle, String name) {
                            return new Op(name);
                        }

                        public boolean acceptsExecution(
                                ProtosForeignPluginSession session,
                                Object handle,
                                List<ProtosForeignArgumentValue> arguments) {
                            VALIDATIONS.incrementAndGet();
                            boolean admitted =
                                    handle instanceof Op
                                            && arguments.size() == 2
                                            && arguments.get(0).value() instanceof Long number
                                            && number >= Integer.MIN_VALUE
                                            && number <= Integer.MAX_VALUE
                                            && arguments.get(1).value() instanceof String text
                                            && text.indexOf('\\0') < 0;
                            Runnable before = BEFORE_ENTRY;
                            if (admitted && before != null) {
                                before.run();
                            }
                            return admitted;
                        }

                        public Object execute(
                                ProtosForeignPluginSession session,
                                Object handle,
                                List<ProtosForeignArgumentValue> arguments) {
                            EXECUTES.incrementAndGet();
                            if (((Op) handle).name().equals("boom")) {
                                throw new IllegalStateException("boom");
                            }
                            return (Long) arguments.get(0).value()
                                    + ((String) arguments.get(1).value()).length();
                        }
                    };
                }
            }
            """;

    /** A provider written before I144: it does not override {@code acceptsExecution}. */
    private static final String LEGACY_SOURCE =
            """
            package i144l;

            import com.guillermomolina.protos.spi.foreign.ProtosForeignArgumentValue;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignPluginEnvironment;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignPluginSession;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignProviderPlugin;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignValueClass;
            import com.guillermomolina.protos.spi.foreign.ProtosForeignValueOperations;
            import java.math.BigInteger;
            import java.util.List;
            import java.util.Set;

            public final class Legacy implements ProtosForeignProviderPlugin {
                public static final class Demo {}

                record Twice() {}

                public String providerId() { return "i144-legacy"; }

                public String scheme() { return "legado"; }

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
                        public String language() { return "i144-legacy"; }

                        public ProtosForeignValueClass classify(
                                ProtosForeignPluginSession session, Object handle) {
                            if (handle instanceof BigInteger number) {
                                return ProtosForeignValueClass.integral(number);
                            }
                            if (handle instanceof Twice) {
                                return ProtosForeignValueClass.handle(null,
                                        Set.of(ProtosForeignValueClass.Capability.EXECUTABLE));
                            }
                            return ProtosForeignValueClass.handle(handle,
                                    Set.of(ProtosForeignValueClass.Capability.MEMBER_READ));
                        }

                        public boolean acceptsArgument(ProtosForeignArgumentValue argument) {
                            return argument.kind() == ProtosForeignValueClass.Kind.INTEGER;
                        }

                        public boolean canFaithfullyReadMember(
                                ProtosForeignPluginSession session, Object handle, String name) {
                            return handle instanceof Demo && name.equals("twice");
                        }

                        public Object readMember(
                                ProtosForeignPluginSession session, Object handle, String name) {
                            return new Twice();
                        }

                        public Object execute(
                                ProtosForeignPluginSession session,
                                Object handle,
                                List<ProtosForeignArgumentValue> arguments) {
                            return ((BigInteger) arguments.get(0).value()).shiftLeft(1);
                        }
                    };
                }
            }
            """;
}

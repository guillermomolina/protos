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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.oracle.truffle.api.TruffleOptions;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

/**
 * D205 (LIB022-A): {@code std:runtime.info()} observes the Engine and execution mode of the
 * currently entered Context, through the public Polyglot embedding and the hosted Process path.
 */
final class ProtosRuntimeMetadataEmbeddingTest {
    private static final String CORE = Path.of("protos", "lib", "core").toAbsolutePath().toString();
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    private static Context.Builder builder() {
        return Context.newBuilder(ProtosLanguage.ID).option("protos.CoreRoot", CORE);
    }

    private static String field(Context context, String name) {
        return context.eval(ProtosLanguage.ID, "import(\"std:runtime\").info()." + name)
                .asString();
    }

    private static String expectedPlatform() {
        return TruffleOptions.AOT ? "NATIVE_IMAGE" : "JVM";
    }

    private static void assertContract(Context context, String executionMode) {
        assertEquals("Protos", field(context, "implementation"));
        assertEquals(ProtosVersionIdentity.current(), field(context, "implementationVersion"));
        String engineVersion = context.getEngine().getVersion();
        assertFalse(engineVersion.isBlank());
        assertEquals(engineVersion, field(context, "engineVersion"));
        assertEquals(expectedPlatform(), field(context, "platform"));
        assertEquals(executionMode, field(context, "executionMode"));
    }

    @Test
    void standardEmbeddingReportsNormalModeAndItsEngine() {
        try (Context context = builder().build()) {
            assertContract(context, "NORMAL");
        }
    }

    @Test
    void explicitDebugContextReportsDebugWithoutAffectingANormalContext() {
        try (Context normal = builder().build();
                Context debug =
                        builder()
                                .allowExperimentalOptions(true)
                                .option("protos.Debug", "true")
                                .build()) {
            assertContract(debug, "DEBUG");
            assertContract(normal, "NORMAL");
            assertEquals("DEBUG", field(debug, "executionMode"));
        }
    }

    @Test
    void independentEnginesEachReportTheirOwnEngineVersion() {
        try (Engine first = Engine.create(ProtosLanguage.ID);
                Engine second = Engine.create(ProtosLanguage.ID);
                Context left = builder().engine(first).build();
                Context right = builder().engine(second).build()) {
            assertEquals(first.getVersion(), field(left, "engineVersion"));
            assertEquals(second.getVersion(), field(right, "engineVersion"));
        }
    }

    @Test
    void platformFollowsTruffleAot() {
        try (Context context = builder().build()) {
            String platform = field(context, "platform");
            if (TruffleOptions.AOT) {
                assertEquals("NATIVE_IMAGE", platform);
            } else {
                assertEquals("JVM", platform);
            }
        }
    }

    @Test
    void hostedProcessObservesItsOwnEnteredContext() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                Path.of(CORE),
                                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosStandaloneProcessBootstrap.Result bootstrap =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        List.of(),
                        exactEnvironmentDomain(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        try (ProtosPolyglotRuntimeHost host = ProtosPolyglotRuntimeHost.open()) {
            ProtosPolyglotProcessContext context =
                    host.hostProcess(
                            bootstrap.process(),
                            InputStream.nullInputStream(),
                            OutputStream.nullOutputStream(),
                            OutputStream.nullOutputStream());
            try {
                ProtosObjectValue snapshot =
                        assertInstanceOf(
                                ProtosObjectValue.class,
                                context.evaluatePersistent(
                                        com.oracle.truffle.api.source.Source.newBuilder(
                                                        ProtosLanguage.ID,
                                                        "import(\"std:runtime\").info()",
                                                        "<lib022-a-hosted>")
                                                .mimeType(ProtosLanguage.MIME_TYPE)
                                                .build(),
                                        bootstrap.activation()));
                assertEquals(
                        Set.of(
                                "implementation",
                                "implementationVersion",
                                "engineVersion",
                                "platform",
                                "executionMode"),
                        snapshot.localSlotsSnapshot().keySet());
                assertEquals("Protos", text(snapshot, "implementation"));
                assertEquals(
                        context.engineForTesting().getVersion(), text(snapshot, "engineVersion"));
                assertEquals(expectedPlatform(), text(snapshot, "platform"));
                assertEquals("NORMAL", text(snapshot, "executionMode"));
            } finally {
                bootstrap.process().requestTerminationForRuntime();
                bootstrap.process().awaitTerminationForRuntime();
                context.awaitTerminalDispositionForRuntime();
            }
        }
    }

    private static String text(ProtosObjectValue snapshot, String slot) {
        return assertInstanceOf(
                        ProtosStringValue.class, snapshot.readLocalSlot(slot).orElseThrow())
                .value();
    }

    private static ProtosEnvironmentValue.NativeNameDomain exactEnvironmentDomain() {
        return new ProtosEnvironmentValue.NativeNameDomain() {
            @Override
            public boolean sameCapturedName(String left, String right) {
                return left.equals(right);
            }

            @Override
            public boolean isQueryRepresentable(String name) {
                return !name.contains("=") && name.indexOf('\0') < 0;
            }

            @Override
            public boolean matchesQuery(String captured, String query) {
                return captured.equals(query);
            }
        };
    }
}

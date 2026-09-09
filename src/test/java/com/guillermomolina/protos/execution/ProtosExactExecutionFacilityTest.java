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

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ProtosExactExecutionFacilityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-d1-execution-facility.protos");
    private static final Path ACTOR_ROOT =
            Path.of("protos", "tests", "conformance", "actor");
    private static final Path GROUP_ROOT =
            Path.of("protos", "tests", "conformance", "group");
    private static final Path ACTOR_REQUEST_CASE = ACTOR_ROOT.resolve("spawn-request-echo.protos");
    private static final Path ACTOR_WORKERS = ACTOR_ROOT.resolve("modules").resolve("workers.protos");
    private static final Path GROUP_REQUEST_CASE =
            GROUP_ROOT.resolve("request-selects-one-eligible-member.protos");
    private static final Path GROUP_WORKERS = GROUP_ROOT.resolve("modules").resolve("workers.protos");

    @Test
    void protosFixtureConsumesDetachedCompletedAndFailedOutcomes()
            throws Exception {
        Fixture fixture = fixture();
        Object result =
                fixture.compiler
                        .compile(
                                Files.readString(
                                        FIXTURE,
                                        StandardCharsets.UTF_8))
                        .call(fixture.activation);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void detachedFailedErrorAndCapturedStreamsRemainInspectable()
            throws Exception {
        Fixture fixture = fixture();

        Object errorValue =
                fixture.compiler
                        .compile(
                                "execution(\"1.definitelyMissing()\").error")
                        .call(fixture.activation);
        ProtosObjectValue error =
                assertInstanceOf(ProtosObjectValue.class, errorValue);
        assertSame(
                fixture.prelude
                        .bindings()
                        .readLocalSlot("SlotNotFound")
                        .orElseThrow(),
                error.parent().orElseThrow());

        Object stdoutValue =
                fixture.compiler
                        .compile("execution(\"42\").stdout")
                        .call(fixture.activation);
        ProtosBytesValue stdout =
                assertInstanceOf(ProtosBytesValue.class, stdoutValue);
        assertEquals(java.math.BigInteger.ZERO, stdout.indexedSize());

        Object stderrValue =
                fixture.compiler
                        .compile("execution(\"42\").stderr")
                        .call(fixture.activation);
        ProtosBytesValue stderr =
                assertInstanceOf(ProtosBytesValue.class, stderrValue);
        assertEquals(java.math.BigInteger.ZERO, stderr.indexedSize());
    }

    @Test
    void authorityBearingCompletedValueFailsClosedAtDetachedBoundary()
            throws Exception {
        Fixture fixture = fixture();

        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                fixture.compiler
                                        .compile("execution(\"process\")")
                                        .call(fixture.activation));

        assertSame(
                ProtosCoreErrors.prototype(
                        fixture.activation,
                        ProtosCoreErrors.StandardError.NON_TRANSFERABLE_VALUE),
                signal.error().parent().orElseThrow());
    }


    @Test
    void inspectionKeepsLiveSourceValueInsideFreshProcessUntilInspector()
            throws Exception {
        Fixture fixture = fixture();

        Object result =
                fixture.compiler
                        .compile(
                                "observation: executionInspect("
                                        + "\"future: (() => { 42 }).future()\\n"
                                        + "() => { future.value() }\", "
                                        + "\"(subject) => { subject() }\""
                                        + ")\n"
                                        + "(observation.state === \"completed\") && "
                                        + "(observation.value === 42) && "
                                        + "(observation.error === null)")
                        .call(fixture.activation);

        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void inspectionMaySuspendOnRetainedActorRequestThroughProductionScheduler()
            throws Exception {
        ProtosObjectValue observation =
                inspectionObservation(
                        fixture(ACTOR_WORKERS),
                        ACTOR_REQUEST_CASE,
                        "(subject) => { subject.value() }");

        assertCompletedObservation(observation);
        ProtosIntegerValue value =
                assertInstanceOf(
                        ProtosIntegerValue.class,
                        observation.readLocalSlot("value").orElseThrow());
        assertEquals(BigInteger.valueOf(42), value.value());
    }

    @Test
    void inspectionMaySuspendOnRetainedGroupRequestWithoutSelectingRoutingMember()
            throws Exception {
        ProtosObjectValue observation =
                inspectionObservation(
                        fixture(GROUP_WORKERS),
                        GROUP_REQUEST_CASE,
                        "(subject) => { subject.value() }");

        assertCompletedObservation(observation);
        ProtosIntegerValue value =
                assertInstanceOf(
                        ProtosIntegerValue.class,
                        observation.readLocalSlot("value").orElseThrow());
        assertTrue(
                value.value().equals(BigInteger.ONE)
                        || value.value().equals(BigInteger.TWO),
                "Group request must resolve through exactly one eligible retained fixture member");
    }

    @Test
    void facilityIsBootstrapLocalAndNotPreludeGlobal() throws Exception {
        Fixture fixture = fixture();

        assertTrue(
                fixture.activation
                        .context()
                        .hasLocalSlot(
                                ProtosExactExecutionFacility.BOOTSTRAP_SLOT));
        assertFalse(
                fixture.prelude
                        .bindings()
                        .hasLocalSlot(
                                ProtosExactExecutionFacility.BOOTSTRAP_SLOT));
        assertTrue(
                fixture.activation
                        .context()
                        .hasLocalSlot(
                                ProtosExactExecutionFacility.INSPECTION_BOOTSTRAP_SLOT));
        assertFalse(
                fixture.prelude
                        .bindings()
                        .hasLocalSlot(
                                ProtosExactExecutionFacility.INSPECTION_BOOTSTRAP_SLOT));
    }

    private static ProtosObjectValue inspectionObservation(
            Fixture fixture,
            Path sourcePath,
            String inspectorSource)
            throws Exception {
        Object inspector =
                fixture.activation
                        .context()
                        .readLocalSlot(ProtosExactExecutionFacility.INSPECTION_BOOTSTRAP_SLOT)
                        .orElseThrow();
        return assertInstanceOf(
                ProtosObjectValue.class,
                ProtosInvocation.invoke(
                        inspector,
                        List.of(
                                new ProtosStringValue(
                                        Files.readString(sourcePath, StandardCharsets.UTF_8)),
                                new ProtosStringValue(inspectorSource)),
                        fixture.activation));
    }

    private static void assertCompletedObservation(ProtosObjectValue observation) {
        ProtosStringValue state =
                assertInstanceOf(
                        ProtosStringValue.class,
                        observation.readLocalSlot("state").orElseThrow());
        assertEquals("completed", state.value());
        assertSame(
                ProtosNullValue.INSTANCE,
                observation.readLocalSlot("error").orElseThrow());
    }

    private static Fixture fixture() throws Exception {
        return fixture(new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
    }

    private static Fixture fixture(Path workersSource) throws Exception {
        return fixture(new WorkersResolver(workersSource));
    }

    private static Fixture fixture(ProtosModuleResolver resolver) throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosExactExecutionFacility.install(activation);
        ProtosExactExecutionFacility.installInspection(activation);
        return new Fixture(prelude, activation, new ProtosSourceCompiler());
    }

    private static final class WorkersResolver implements ProtosModuleResolver {
        private static final String SPECIFIER = "workers";
        private static final ProtosModuleKey KEY = new ProtosModuleKey("tool002-g1:workers");
        private final Path source;

        private WorkersResolver(Path source) {
            this.source = source;
        }

        @Override
        public ProtosModuleKey resolve(
                String exactSpecifier,
                Optional<ProtosModuleKey> importingModule)
                throws IOException {
            if (!SPECIFIER.equals(exactSpecifier)) {
                throw new IOException("unknown TOOL002-G1 fixture module: " + exactSpecifier);
            }
            return KEY;
        }

        @Override
        public ProtosModuleSource loadSource(ProtosModuleKey key) throws IOException {
            if (!KEY.equals(key)) {
                throw new IOException(
                        "foreign TOOL002-G1 fixture module key: " + key.canonicalId());
            }
            return ProtosModuleSource.fromPath(key, source);
        }
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation,
            ProtosSourceCompiler compiler) {}
}

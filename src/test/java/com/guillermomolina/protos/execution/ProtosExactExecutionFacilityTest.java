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
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    }

    private static Fixture fixture() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                CORE,
                                new ProtosStandardLibraryModuleResolver(
                                        STANDARD_LIBRARY));
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosExactExecutionFacility.install(activation);
        return new Fixture(
                prelude,
                activation,
                new ProtosSourceCompiler());
    }

    private record Fixture(
            ProtosPrelude prelude,
            ProtosActivation activation,
            ProtosSourceCompiler compiler) {}
}

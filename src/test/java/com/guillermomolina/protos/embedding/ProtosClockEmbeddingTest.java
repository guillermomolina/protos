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

import com.guillermomolina.protos.runtime.ProtosClockCapabilityValue;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;

/** I163 / D253-C1: explicit per-Context Polyglot Clock grant. */
final class ProtosClockEmbeddingTest {
    private static final String CORE =
            Path.of("protos", "lib", "core")
                    .toAbsolutePath().toString();

    private static final String SOURCE =
            """
            initialClock: context.hasSlot("clock")
            initialNetwork: context.hasSlot("network")
            sample: () => { Integer.recognizes(clock.epochNanoseconds()) }
            secondSample: () => { Integer.recognizes(clock.epochNanoseconds()) }
            0
            """;

    private static Context context(boolean allowed) {
        return Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", CORE)
                .option("protos.AllowClock", Boolean.toString(allowed))
                .allowIO(IOAccess.NONE)
                .build();
    }

    private static ProtosEmbeddedProcess embedded(Context context) {
        context.enter();
        try {
            return ProtosLanguageContext.current()
                    .embeddedProcessOrNull();
        } finally {
            context.leave();
        }
    }

    private static Value member(Context context, String name) {
        return context.getBindings(ProtosLanguage.ID)
                .getMember(name);
    }

    @Test
    void parallelAdmissionRejectsClockAuthorityBeforeStartingWork() {
        try (Context context = Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", CORE).option("protos.AllowClock", "true")
                .allowIO(IOAccess.NONE).allowCreateThread(true).build()) {
            assertTrue(context.eval(ProtosLanguage.ID,
                    "denied: NonParallelValue.handle("
                            + "() => { ((x) => { x }).parallel(clock).value(); false },"
                            + "(error) => { true })\n"
                            + "denied && Integer.recognizes(clock.epochNanoseconds())").asBoolean());
            assertEquals(0, embedded(context).processForTesting()
                    .rootActorForRuntime().executionDomain().liveTaskCount(),
                    "handled P admission rejection must leave no live Task to block Context.close()");
        }
    }

    @Test
    void importsDoNotAcquireOrPublishTheRootGrant() {
        try (Context context = context(true)) {
            ProtosEmbeddedModules.install(context, java.util.Map.of("app:clock-probe",
                    "this.hadClock: context.hasSlot(\"clock\")\n"));
            context.eval(ProtosLanguage.ID,
                    "hadRootClock: context.hasSlot(\"clock\")\n"
                            + "probe: import(\"app:clock-probe\")\n"
                            + "hadImportedClock: probe.hadClock\n"
                            + "Instant: import(\"std:datetime/instant\")\n"
                            + "hadModuleClock: Instant.hasSlot(\"clock\")\n0");
            assertTrue(member(context, "hadRootClock").asBoolean());
            assertFalse(member(context, "hadImportedClock").asBoolean());
            assertFalse(member(context, "hadModuleClock").asBoolean());
        }
    }

    @Test
    void authenticInstantCompositionAndEntropyIndependence() {
        try (Context context = context(true)) {
            assertTrue(context.eval(ProtosLanguage.ID,
                    "Instant: import(\"std:datetime/instant\")\n"
                            + "Instant.recognizes(Instant(clock.epochNanoseconds()))").asBoolean());
        }
        try (Context entropy = Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", CORE).option("protos.AllowEntropy", "true")
                .allowIO(IOAccess.NONE).build()) {
            entropy.eval(ProtosLanguage.ID, SOURCE);
            assertFalse(member(entropy, "initialClock").asBoolean());
        }
    }

    @Test
    void absentByDefaultAndExplicitFalse() {
        try (Context implicit = Context.newBuilder(ProtosLanguage.ID)
                    .option("protos.CoreRoot", CORE)
                    .allowIO(IOAccess.NONE)
                    .build()) {
            implicit.eval(ProtosLanguage.ID, SOURCE);
            assertFalse(member(implicit, "initialClock").asBoolean());
            assertTrue(embedded(implicit)
                    .processForTesting()
                    .rootClockForRuntime()
                    .isEmpty());
        }

        try (Context denied = context(false)) {
            denied.eval(ProtosLanguage.ID, SOURCE);
            assertFalse(member(denied, "initialClock").asBoolean());
            assertTrue(embedded(denied)
                    .processForTesting()
                    .rootClockForRuntime()
                    .isEmpty());
        }
    }

    @Test
    void explicitOptionGrantsOnlyClock() {
        try (Context granted = context(true)) {
            granted.eval(ProtosLanguage.ID, SOURCE);

            assertTrue(member(granted, "initialClock").asBoolean());
            assertFalse(member(granted, "initialNetwork").asBoolean());

            ProtosClockCapabilityValue clock =
                    embedded(granted)
                            .processForTesting()
                            .rootClockForRuntime()
                            .orElseThrow();
            assertFalse(clock.retiredForRuntime());

            assertTrue(member(granted, "sample").execute().asBoolean());
            assertTrue(member(granted, "secondSample").execute().asBoolean());
        }
    }

    @Test
    void allowAllAccessDoesNotGrantClock() {
        try (Context context = Context.newBuilder(ProtosLanguage.ID)
                    .option("protos.CoreRoot", CORE)
                    .allowAllAccess(true)
                    .build()) {
            context.eval(ProtosLanguage.ID, SOURCE);

            assertFalse(member(context, "initialClock").asBoolean());
            assertTrue(embedded(context)
                    .processForTesting()
                    .rootClockForRuntime()
                    .isEmpty());
        }
    }

    @Test
    void fileAndNetworkAuthorizationsAreIndependent() {
        try (Context socketOnly = Context.newBuilder(ProtosLanguage.ID)
                    .option("protos.CoreRoot", CORE)
                    .allowIO(IOAccess.newBuilder()
                            .allowHostFileAccess(false)
                            .allowHostSocketAccess(true)
                            .build())
                    .build()) {
            socketOnly.eval(ProtosLanguage.ID, SOURCE);
            assertFalse(member(socketOnly, "initialClock").asBoolean());
            assertTrue(member(socketOnly, "initialNetwork").asBoolean());
        }

        try (Context clockOnly = context(true)) {
            clockOnly.eval(ProtosLanguage.ID, SOURCE);
            assertTrue(member(clockOnly, "initialClock").asBoolean());
            assertFalse(member(clockOnly, "initialNetwork").asBoolean());
        }
    }

    @Test
    void secondEvaluationReceivesNoBootstrapLocalGrant() {
        try (Context context = context(true)) {
            context.eval(ProtosLanguage.ID, SOURCE);
            assertTrue(member(context, "initialClock").asBoolean());

            Value later = context.eval(ProtosLanguage.ID,
                    "context.hasSlot(\"clock\")");
            assertFalse(later.asBoolean());
        }
    }

    @Test
    void closingOneContextDoesNotRevokeAnother() {
        ProtosClockCapabilityValue survivorGrant;
        ProtosClockCapabilityValue retiredGrant;

        try (Context survivor = context(true)) {
            survivor.eval(ProtosLanguage.ID, SOURCE);
            survivorGrant = embedded(survivor)
                    .processForTesting()
                    .rootClockForRuntime()
                    .orElseThrow();

            try (Context closed = context(true)) {
                closed.eval(ProtosLanguage.ID, SOURCE);
                retiredGrant = embedded(closed)
                        .processForTesting()
                        .rootClockForRuntime()
                        .orElseThrow();

                assertNotSame(survivorGrant, retiredGrant);
                assertFalse(retiredGrant.retiredForRuntime());
            }

            assertTrue(retiredGrant.retiredForRuntime());
            assertFalse(survivorGrant.retiredForRuntime());

            assertTrue(member(survivor, "sample").execute().asBoolean());
        }

        assertTrue(survivorGrant.retiredForRuntime());
    }

    @Test
    void fatalRootFailureRevokesGrantBeforeContextClose() {
        try (Context context = context(true)) {
            context.eval(ProtosLanguage.ID, SOURCE);
            ProtosClockCapabilityValue grant =
                    embedded(context)
                            .processForTesting()
                            .rootClockForRuntime()
                            .orElseThrow();

            assertThrows(PolyglotException.class,
                    () -> context.eval(
                            ProtosLanguage.ID, "Error().signal()\n0"));

            assertTrue(grant.retiredForRuntime());
        }
    }
}

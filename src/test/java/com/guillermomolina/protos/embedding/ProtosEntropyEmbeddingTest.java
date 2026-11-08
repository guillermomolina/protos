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

import com.guillermomolina.protos.runtime.ProtosEntropyCapabilityValue;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;

/** I157 / D243-C: explicit per-Context Polyglot Entropy grant. */
final class ProtosEntropyEmbeddingTest {
    private static final String CORE =
            Path.of("protos", "lib", "core")
                    .toAbsolutePath().toString();

    private static final String SOURCE =
            """
            initialEntropy: context.hasSlot("entropy")
            initialNetwork: context.hasSlot("network")
            bytes16: () => { entropy.bytes(16).value().size() }
            bytes32: () => { entropy.bytes(32).value().size() }
            0
            """;

    private static Context context(boolean allowed) {
        return Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", CORE)
                .option("protos.AllowEntropy", Boolean.toString(allowed))
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
    void absentByDefaultAndExplicitFalse() {
        try (Context implicit = Context.newBuilder(ProtosLanguage.ID)
                    .option("protos.CoreRoot", CORE)
                    .allowIO(IOAccess.NONE)
                    .build()) {
            implicit.eval(ProtosLanguage.ID, SOURCE);
            assertFalse(member(implicit, "initialEntropy").asBoolean());
            assertTrue(embedded(implicit)
                    .processForTesting()
                    .rootEntropyForRuntime()
                    .isEmpty());
        }

        try (Context denied = context(false)) {
            denied.eval(ProtosLanguage.ID, SOURCE);
            assertFalse(member(denied, "initialEntropy").asBoolean());
            assertTrue(embedded(denied)
                    .processForTesting()
                    .rootEntropyForRuntime()
                    .isEmpty());
        }
    }

    @Test
    void explicitOptionGrantsOnlyEntropy() {
        try (Context granted = context(true)) {
            granted.eval(ProtosLanguage.ID, SOURCE);

            assertTrue(member(granted, "initialEntropy").asBoolean());
            assertFalse(member(granted, "initialNetwork").asBoolean());

            ProtosEntropyCapabilityValue entropy =
                    embedded(granted)
                            .processForTesting()
                            .rootEntropyForRuntime()
                            .orElseThrow();
            assertFalse(entropy.retiredForRuntime());

            assertEquals(16, member(granted, "bytes16").execute().asInt());
            assertEquals(32, member(granted, "bytes32").execute().asInt());
        }
    }

    @Test
    void allowAllAccessDoesNotGrantEntropy() {
        try (Context context = Context.newBuilder(ProtosLanguage.ID)
                    .option("protos.CoreRoot", CORE)
                    .allowAllAccess(true)
                    .build()) {
            context.eval(ProtosLanguage.ID, SOURCE);

            assertFalse(member(context, "initialEntropy").asBoolean());
            assertTrue(embedded(context)
                    .processForTesting()
                    .rootEntropyForRuntime()
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
            assertFalse(member(socketOnly, "initialEntropy").asBoolean());
            assertTrue(member(socketOnly, "initialNetwork").asBoolean());
        }

        try (Context entropyOnly = context(true)) {
            entropyOnly.eval(ProtosLanguage.ID, SOURCE);
            assertTrue(member(entropyOnly, "initialEntropy").asBoolean());
            assertFalse(member(entropyOnly, "initialNetwork").asBoolean());
        }
    }

    @Test
    void secondEvaluationReceivesNoBootstrapLocalGrant() {
        try (Context context = context(true)) {
            context.eval(ProtosLanguage.ID, SOURCE);
            assertTrue(member(context, "initialEntropy").asBoolean());

            Value later = context.eval(ProtosLanguage.ID,
                    "context.hasSlot(\"entropy\")");
            assertFalse(later.asBoolean());
        }
    }

    @Test
    void closingOneContextDoesNotRevokeAnother() {
        ProtosEntropyCapabilityValue survivorGrant;
        ProtosEntropyCapabilityValue retiredGrant;

        try (Context survivor = context(true)) {
            survivor.eval(ProtosLanguage.ID, SOURCE);
            survivorGrant = embedded(survivor)
                    .processForTesting()
                    .rootEntropyForRuntime()
                    .orElseThrow();

            try (Context closed = context(true)) {
                closed.eval(ProtosLanguage.ID, SOURCE);
                retiredGrant = embedded(closed)
                        .processForTesting()
                        .rootEntropyForRuntime()
                        .orElseThrow();

                assertNotSame(survivorGrant, retiredGrant);
                assertFalse(retiredGrant.retiredForRuntime());
            }

            assertTrue(retiredGrant.retiredForRuntime());
            assertFalse(survivorGrant.retiredForRuntime());

            assertEquals(16,
                    member(survivor, "bytes16").execute().asInt());
        }

        assertTrue(survivorGrant.retiredForRuntime());
    }

    @Test
    void fatalRootFailureRevokesGrantBeforeContextClose() {
        try (Context context = context(true)) {
            context.eval(ProtosLanguage.ID, SOURCE);
            ProtosEntropyCapabilityValue grant =
                    embedded(context)
                            .processForTesting()
                            .rootEntropyForRuntime()
                            .orElseThrow();

            assertThrows(PolyglotException.class,
                    () -> context.eval(
                            ProtosLanguage.ID, "Error().signal()\n0"));

            assertTrue(grant.retiredForRuntime());
        }
    }
}

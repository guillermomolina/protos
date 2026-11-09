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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * I160 / D245-C: actual Protos module and Actor-local Future admission.
 *
 * <p>These tests deliberately use module activations without a bound
 * execution host. Successful cryptographic work is covered separately
 * by the independent vectors and hosted integration suites.
 */
final class ProtosPasswordModuleIntegrationTest {

    private static final Path CORE =
            Path.of("protos", "lib", "core");

    private static final Path LIBRARY =
            Path.of("protos", "lib");

    private static final String IMPORT =
            "Password: import(\"std:crypto/password\")\n";

    private static ProtosPrelude prelude;

    @BeforeAll
    static void bootstrap() throws IOException {
        prelude = new ProtosCoreBootstrap().bootstrap(
                CORE,
                new ProtosStandardLibraryModuleResolver(LIBRARY));
    }

    private static Object completed(
            ProtosActivation activation,
            String source) {

        ProtosObjectValue snippetContext =
                prelude.newExecutionContext();

        activation.context()
                .localSlotsSnapshot()
                .forEach(snippetContext::createLocalSlot);

        ProtosActivation snippetActivation =
                prelude.newModuleActivation(
                        activation.actorModuleState(),
                        activation.currentModuleKey().orElse(null),
                        snippetContext,
                        activation.executionDomain());

        ProtosExecutionOutcome outcome =
                ProtosTestExecutionSupport.execute(
                        source, snippetActivation);

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "Password module evaluation failed: "
                        + outcome.error()
                        + "; trace="
                        + outcome.failureDiagnosticTrace());

        return outcome.value();
    }

    private static String failedKind(Object value) {
        ProtosFutureValue future =
                assertInstanceOf(ProtosFutureValue.class, value);

        assertEquals(
                ProtosFutureValue.State.FAILED,
                future.state());

        ProtosObjectValue error = future.failedError().orElseThrow();

        ProtosStringValue kind = assertInstanceOf(
                ProtosStringValue.class,
                error.readLocalSlot("kind").orElseThrow());

        return kind.value();
    }

    @Test
    void moduleIdentityAndExactPublicSurface() {
        ProtosActivation firstActor = prelude.newModuleActivation();
        ProtosActivation secondActor = prelude.newModuleActivation();

        Object first = completed(
                firstActor,
                "import(\"std:crypto/password\")");

        Object same = completed(
                firstActor,
                "import(\"std:crypto/password\")");

        Object second = completed(
                secondActor,
                "import(\"std:crypto/password\")");

        assertSame(first, same);
        assertNotSame(first, second);

        ProtosArrayValue slotNames = assertInstanceOf(
                ProtosArrayValue.class,
                completed(firstActor, IMPORT + "Password.slotNames()"));

        List<String> names = new ArrayList<>();

        for (Object value : slotNames.indexedSnapshot()) {
            names.add(assertInstanceOf(
                    ProtosStringValue.class, value).value());
        }

        assertEquals(5, names.size());

        assertEquals(
                Set.of(
                        "hash",
                        "hashWithSalt",
                        "verify",
                        "needsRehash",
                        "PasswordError"),
                Set.copyOf(names));

        assertTrue(firstActor.context()
                .readLocalSlot(
                        ProtosPasswordArgon2Facility.BOOTSTRAP_SLOT)
                .isEmpty());
    }

    @Test
    void invalidArgumentsFailWithPasswordErrorFutures() {
        ProtosActivation activation = prelude.newModuleActivation();

        Object invalidPassword = completed(
                activation,
                IMPORT
                        + "Password.hashWithSalt("
                        + "Array(), Encoding.UTF8.encode("
                        + "\"0123456789abcdef\"))");

        assertEquals(
                "invalidArgument",
                failedKind(invalidPassword));

        Object invalidSalt = completed(
                activation,
                IMPORT
                        + "Password.hashWithSalt("
                        + "\"password\", Encoding.UTF8.encode(\"short\"))");

        assertEquals(
                "invalidArgument",
                failedKind(invalidSalt));

        Object invalidVerify = completed(
                activation,
                IMPORT
                        + "Password.verify(Array(), \"invalid\")");

        assertEquals(
                "invalidArgument",
                failedKind(invalidVerify));
    }

    @Test
    void validInputsWithoutHostFailClosedAsUnavailable() {
        ProtosActivation activation = prelude.newModuleActivation();

        Object result = completed(
                activation,
                IMPORT
                        + "Password.hashWithSalt("
                        + "\"password\", "
                        + "Encoding.UTF8.encode(\"0123456789abcdef\"))");

        assertEquals(
                "unavailable",
                failedKind(result));
    }

    @Test
    void errorsAreFreshOccurrences() {
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosFutureValue first = assertInstanceOf(
                ProtosFutureValue.class,
                completed(
                        activation,
                        IMPORT
                                + "Password.hashWithSalt("
                                + "null, Encoding.UTF8.encode("
                                + "\"0123456789abcdef\"))"));

        ProtosFutureValue second = assertInstanceOf(
                ProtosFutureValue.class,
                completed(
                        activation,
                        IMPORT
                                + "Password.hashWithSalt("
                                + "null, Encoding.UTF8.encode("
                                + "\"0123456789abcdef\"))"));

        assertNotSame(first, second);

        ProtosObjectValue firstError =
                first.failedError().orElseThrow();

        ProtosObjectValue secondError =
                second.failedError().orElseThrow();

        assertNotSame(firstError, secondError);

        assertEquals(
                "invalidArgument",
                assertInstanceOf(
                        ProtosStringValue.class,
                        firstError.readLocalSlot("kind")
                                .orElseThrow()).value());

        assertEquals(
                "invalidArgument",
                assertInstanceOf(
                        ProtosStringValue.class,
                        secondError.readLocalSlot("kind")
                                .orElseThrow()).value());
    }
}

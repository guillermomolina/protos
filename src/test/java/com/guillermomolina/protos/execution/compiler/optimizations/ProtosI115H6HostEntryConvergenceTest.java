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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * I115-H6 (experimental): the source-backed Java/polyglot host entry is one compact no-home guest
 * invocation. These tests cover only the admitted experimental contract, not the abandoned
 * ordinary host-entry semantics.
 */
final class ProtosI115H6HostEntryConvergenceTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final long FACTORIAL_20 = 2432902008176640000L;

    private static final String SOURCE =
            """
            factorial: (n) => {
                result: 1

                (n > 1).ifTrue() {
                    result = n * factorial(n - 1)
                }

                result
            }

            run: () => {
                factorial(20)
            }

            identity: (value) => { value }

            literal: () => { 3 }
            """;

    @TempDir Path directory;

    private static Context newContext() {
        return Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", CORE.toAbsolutePath().toString())
                .build();
    }

    private static ProtosEmbeddedProcess embedded(Context context) {
        context.enter();
        try {
            return ProtosLanguageContext.current().embeddedProcessOrNull();
        } finally {
            context.leave();
        }
    }

    @Test
    void polyglotRunReturnsRecursiveFactorial() {
        try (Context context = newContext()) {
            context.eval(ProtosLanguage.ID, SOURCE + "0\n");
            Value run = context.getBindings(ProtosLanguage.ID).getMember("run");
            for (int index = 0; index < 3; index++) {
                Value result = run.execute();
                assertTrue(result.fitsInLong());
                assertEquals(FACTORIAL_20, result.asLong());
            }
        }
    }

    @Test
    void directOneArgumentIdentityReceivesJavaInteger() {
        try (Context context = newContext()) {
            context.eval(ProtosLanguage.ID, SOURCE + "0\n");
            Value identity = context.getBindings(ProtosLanguage.ID).getMember("identity");
            assertEquals(42, identity.execute(42).asInt());
            assertEquals(-1L, identity.execute(-1L).asLong());
            assertEquals(Long.MAX_VALUE, identity.execute(Long.MAX_VALUE).asLong());
        }
    }

    @Test
    void sessionZeroArgumentSourceBackedExecutable() throws Exception {
        Path file = directory.resolve("i115-h6.protos");
        Files.writeString(file, SOURCE, StandardCharsets.UTF_8);
        try (ProtosStandaloneHostedSession session =
                ProtosStandaloneHostedSession.open(CORE, file)) {
            assertEquals(
                    ProtosExecutionOutcome.State.COMPLETED, session.initialOutcome().state());
            Value literal = session.prepareTopLevel("literal").executable();
            assertEquals(3, literal.execute().asInt());
            Value run = session.prepareTopLevel("run").executable();
            assertEquals(FACTORIAL_20, run.execute().asLong());
        }
    }

    /**
     * The experimental entry neither acquires, releases, nor rejects on RootActor ownership: with
     * the token already held it still runs, and leaves the token and host-entry extent untouched.
     */
    @Test
    void hostEntryRunsWithoutOrdinaryRootActorAdmission() {
        try (Context context = newContext()) {
            context.eval(ProtosLanguage.ID, SOURCE + "0\n");
            ProtosEmbeddedProcess process = embedded(context);
            ProtosActorExecutionDomain domain = process.rootExecutionDomain();
            Value run = context.getBindings(ProtosLanguage.ID).getMember("run");

            Thread token = process.enterRootActor();
            try {
                assertFalse(process.permitsUnadmittedNonSuspendingHostEntry());
                assertEquals(FACTORIAL_20, run.execute().asLong());
                assertFalse(
                        process.permitsUnadmittedNonSuspendingHostEntry(),
                        "the entry did not release the held token");
                assertEquals(
                        ProtosActorExecutionDomain.HostEntryExtent.NONE,
                        domain.hostEntryExtentForRuntime());
            } finally {
                process.exitRootActor(token);
            }
            assertEquals(0, domain.liveTaskCount());
        }
    }
}

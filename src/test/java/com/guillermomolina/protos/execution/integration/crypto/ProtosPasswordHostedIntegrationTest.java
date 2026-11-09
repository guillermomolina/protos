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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * I160 / D245-C: complete hosted Protos password-module integration.
 *
 * <p>These scenarios use a real Polyglot Process and its RootActor,
 * not a synthetic module activation or direct provider invocation.
 */
final class ProtosPasswordHostedIntegrationTest {

    private static final String CORE =
            Path.of("protos", "lib", "core")
                    .toAbsolutePath().toString();

    private static final String PREFIX =
            "protos-password-v1:nfc:"
                    + "$argon2id$v=19$m=65536,t=3,p=4$";

    private static final String PASSWORD_RECORD =
            PREFIX
                    + "AAECAwQFBgcICQoLDA0ODw"
                    + "$vUrOhcKV+zjYROFncankOSUoKsmCyGVr5Vou9s8g/Vg";

    private static Context context(boolean entropy) {
        return Context.newBuilder(ProtosLanguage.ID)
                .option("protos.CoreRoot", CORE)
                .option("protos.AllowEntropy", Boolean.toString(entropy))
                .allowIO(IOAccess.NONE)
                .build();
    }

    private static Value evaluate(Context context, String source) {
        try {
            return context.eval(ProtosLanguage.ID, source);
        } catch (PolyglotException failure) {
            String guestKind = "unavailable";

            if (failure.isGuestException()) {
                try {
                    Value guest = failure.getGuestObject();
                    if (guest != null && guest.hasMember("kind")) {
                        Value kind = guest.getMember("kind");
                        if (kind != null && kind.isString()) {
                            guestKind = kind.asString();
                        }
                    }
                } catch (RuntimeException ignored) {
                    guestKind = "not-inspectable";
                }
            }

            throw new AssertionError(
                    "I160_HOSTED_EVAL_FAILURE"
                            + " guestException=" + failure.isGuestException()
                            + " internalError=" + failure.isInternalError()
                            + " syntaxError=" + failure.isSyntaxError()
                            + " cancelled=" + failure.isCancelled()
                            + " hostException=" + failure.isHostException()
                            + " guestKind=" + guestKind
                            + " message=" + failure.getMessage(),
                    failure);
        }
    }

    /**
     * Execute through a host-invoked Closure so Future.value()
     * can suspend and resume on the Actor's Task.
     */
    private static Value evaluateSuspending(
            Context context, String source) {
        evaluate(
                context,
                "i160HostedScenario: () => {\n"
                        + source
                        + "\n}\n");

        Value closure = context.getBindings(ProtosLanguage.ID)
                .getMember("i160HostedScenario");

        assertTrue(
                closure != null && closure.canExecute(),
                "Hosted scenario must expose an executable Protos Closure");

        return closure.execute();
    }

    private static final String FIXED_SALT = """
            salt: Encoding.UTF8.encode("")
            for(i, 0, 16) {
                salt.add(i)
            }
            """;

    @Test
    @Timeout(30)
    void closingContextWithUnobservedPasswordDerivationTerminates() {
        try (Context context = context(false)) {
            Value result = evaluateSuspending(context, """
                    Password: import("std:crypto/password")
                    """ + FIXED_SALT + """
                    pending: Password.hashWithSalt("password", salt)
                    true
                    """);

            assertTrue(result.asBoolean());
        }
    }

    @Test
    @Timeout(180)
    void fixedSaltRoundTripMatchesIndependentLibargon2Record() {
        try (Context context = context(false)) {
            Value result = evaluateSuspending(context, """
                    Password: import("std:crypto/password")
                    """ + FIXED_SALT + """
                    record: Password.hashWithSalt("password", salt).value()
                    valid: Password.verify("password", record).value()
                    wrong: Password.verify("wrong", record).value()
                    valid && (!wrong) &&
                        (record == "%s") &&
                        (!Password.needsRehash(record))
                    """.formatted(PASSWORD_RECORD));

            assertTrue(result.asBoolean());
        }
    }

    @Test
    @Timeout(180)
    void explicitEntropyProducesAUsableRecord() {
        try (Context context = context(true)) {
            Value result = evaluateSuspending(context, """
                    Password: import("std:crypto/password")
                    record: Password.hash("password", entropy).value()
                    valid: Password.verify("password", record).value()
                    valid && (!Password.needsRehash(record))
                    """);

            assertTrue(result.asBoolean());
        }
    }

    @Test
    @Timeout(30)
    void importingPasswordDoesNotMintEntropy() {
        try (Context context = context(false)) {
            Value result = evaluate(context, """
                    Password: import("std:crypto/password")
                    initialEntropy: context.hasSlot("entropy")
                    Assertions: import("std:test/assertions")
                    Assertions.signals(Password.PasswordError, () => {
                        Password.hash("password", null).value()
                    })
                    !initialEntropy
                    """);

            assertTrue(result.asBoolean());
        }
    }

    @Test
    @Timeout(180)
    void independentContextsRetainIndependentModuleState() {
        try (Context first = context(false);
             Context second = context(false)) {

            String source = """
                    Password: import("std:crypto/password")
                    """ + FIXED_SALT + """
                    record: Password.hashWithSalt("password", salt).value()
                    record == "%s"
                    """.formatted(PASSWORD_RECORD);

            assertTrue(evaluateSuspending(first, source).asBoolean());
            assertTrue(evaluateSuspending(second, source).asBoolean());
        }
    }
}

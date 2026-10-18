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

import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

/**
 * D205 (LIB022-B): {@code protos.Inspect} is the only authority of {@code std:inspect} through the
 * public Polyglot embedding; it defaults to false, is independent of {@code protos.Debug}, and
 * inspecting never executes the Closure or changes the Context's execution mode.
 */
final class ProtosInspectEmbeddingTest {
    private static final String CORE = Path.of("protos", "lib", "core").toAbsolutePath().toString();

    private static final String IMPORT_DENIED =
            """
            (() => {
                denied: false
                Error.handle(
                    () => {
                        import("std:inspect")
                    },
                    (error) => {
                        denied = error.parent() === Error
                    }
                )
                denied
            })()
            """;

    private static Context.Builder builder() {
        return Context.newBuilder(ProtosLanguage.ID).option("protos.CoreRoot", CORE);
    }

    private static Context.Builder authorizedBuilder() {
        return builder().option("protos.Inspect", "true");
    }

    private static Context.Builder debug(Context.Builder builder) {
        return builder.allowExperimentalOptions(true).option("protos.Debug", "true");
    }

    private static boolean importDenied(Context context) {
        return context.eval(ProtosLanguage.ID, IMPORT_DENIED).asBoolean();
    }

    private static String inspect(Context context, String operation) {
        return context.eval(
                        ProtosLanguage.ID,
                        "(() => {\n"
                                + "    Inspect: import(\"std:inspect\")\n"
                                + "    square: (x) => x * x\n"
                                + "    Inspect."
                                + operation
                                + "(square)\n"
                                + "})()")
                .asString();
    }

    private static String executionMode(Context context) {
        return context.eval(ProtosLanguage.ID, "import(\"std:runtime\").info().executionMode")
                .asString();
    }

    private static void assertOperationsAdmitted(Context context) {
        for (String operation :
                new String[] {"representation", "callableInfo", "bytecode", "truffleTree"}) {
            String text = inspect(context, operation);
            assertFalse(text.isBlank(), operation);
        }
    }

    // ---- Option matrix ----

    @Test
    void absentOptionDeniesTheImport() {
        try (Context context = builder().build()) {
            assertTrue(importDenied(context));
        }
    }

    @Test
    void explicitFalseDeniesTheImport() {
        try (Context context = builder().option("protos.Inspect", "false").build()) {
            assertTrue(importDenied(context));
        }
    }

    @Test
    void debugAloneDoesNotAuthorizeInspection() {
        try (Context context = debug(builder()).build()) {
            assertTrue(importDenied(context));
            assertEquals("DEBUG", executionMode(context));
        }
    }

    @Test
    void inspectInANormalContextAdmitsEveryOperationAndKeepsNormal() {
        try (Context context = authorizedBuilder().build()) {
            assertFalse(importDenied(context));
            assertEquals("NORMAL", executionMode(context));
            assertOperationsAdmitted(context);
            assertEquals("NORMAL", executionMode(context));
        }
    }

    @Test
    void inspectInADebugContextAdmitsEveryOperationAndKeepsDebug() {
        try (Context context = debug(authorizedBuilder()).build()) {
            assertFalse(importDenied(context));
            assertEquals("DEBUG", executionMode(context));
            assertOperationsAdmitted(context);
            assertEquals("DEBUG", executionMode(context));
        }
    }

    // ---- Per-Context authority on one Engine ----

    @Test
    void contextsOfOneEngineKeepTheirOwnAuthority() {
        try (Engine engine = Engine.create(ProtosLanguage.ID);
                Context denied = builder().engine(engine).build();
                Context allowed = authorizedBuilder().engine(engine).build()) {
            assertFalse(importDenied(allowed));
            assertTrue(importDenied(denied));
            assertOperationsAdmitted(allowed);
            assertTrue(importDenied(denied));
        }
    }

    @Test
    void twoAuthorizedContextsOfOneEngineEachInspectTheirOwnRoots() {
        try (Engine engine = Engine.create(ProtosLanguage.ID);
                Context first = authorizedBuilder().engine(engine).build();
                Context second = authorizedBuilder().engine(engine).build()) {
            String firstBytecode = inspect(first, "bytecode");
            String secondBytecode = inspect(second, "bytecode");
            assertTrue(firstBytecode.contains("Bytecode DSL"), firstBytecode);
            assertTrue(secondBytecode.contains("Bytecode DSL"), secondBytecode);
            assertTrue(inspect(first, "truffleTree").contains("physical Truffle node tree"));
            assertTrue(inspect(second, "truffleTree").contains("physical Truffle node tree"));
        }
    }

    // ---- Truthfulness and non-execution ----

    @Test
    void sourceBackedClosureIsInspectedBeforeAnyExecutionWithoutBeingCalled() {
        try (Context context = authorizedBuilder().build()) {
            boolean neverCalled =
                    context.eval(
                                    ProtosLanguage.ID,
                                    """
                                    (() => {
                                        Inspect: import("std:inspect")
                                        calls: 0
                                        target: (x) => {
                                            calls = calls + 1
                                            x
                                        }
                                        Inspect.callableInfo(target)
                                        Inspect.bytecode(target)
                                        Inspect.truffleTree(target)
                                        Inspect.bytecode(target)
                                        calls == 0
                                    })()
                                    """)
                            .asBoolean();
            assertTrue(neverCalled);
        }
    }

    @Test
    void textsDescribeTheRequestedPhysicalLayer() {
        try (Context context = authorizedBuilder().build()) {
            assertTrue(inspect(context, "representation").contains("Closure (source-backed)"));
            assertTrue(inspect(context, "callableInfo").contains("source-backed Closure"));
            String bytecode = inspect(context, "bytecode");
            assertTrue(bytecode.contains("Bytecode DSL"), bytecode);
            assertTrue(bytecode.lines().count() > 2, bytecode);
            String tree = inspect(context, "truffleTree");
            assertTrue(tree.contains("not Graal compiler IR"), tree);
            assertFalse(tree.contains("x * x"), tree);
        }
    }

    @Test
    void wrongArgumentsSignalOrdinaryErrors() {
        try (Context context = authorizedBuilder().build()) {
            boolean allSignalled =
                    context.eval(
                                    ProtosLanguage.ID,
                                    """
                                    (() => {
                                        Inspect: import("std:inspect")
                                        failures: 0
                                        ordinary: true
                                        expect: (action) => {
                                            Error.handle(action, (error) => {
                                                failures = failures + 1
                                                ordinary = ordinary && (error.parent() === Error)
                                            })
                                        }
                                        expect(() => Inspect.representation())
                                        expect(() => Inspect.callableInfo(1))
                                        expect(() => Inspect.bytecode("text"))
                                        expect(() => Inspect.truffleTree(null))
                                        expect(() => Inspect.bytecode((x) => x, 2))
                                        (failures == 5) && ordinary
                                    })()
                                    """)
                            .asBoolean();
            assertTrue(allSignalled);
        }
    }
}

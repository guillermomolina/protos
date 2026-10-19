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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * D205 (LIB022-B): the public surface of {@code std:inspect}, its ordinary Error failures, the
 * per-operation re-check of the entered Context's {@code protos.Inspect} authority, Actor-local
 * module ownership, and Context-local physical projection without executing the inspected Closure.
 *
 * <p>Authorized operations run in Contexts of an Engine configured with {@code protos.Inspect};
 * the shared test Context of {@link ProtosTestExecutionSupport} is the unauthorized Context.
 */
final class ProtosInspectFacilityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Set<String> OPERATIONS =
            Set.of("representation", "callableInfo", "bytecode", "truffleTree");
    private static final List<String> CLOSURE_OPERATIONS =
            List.of("callableInfo", "bytecode", "truffleTree");
    private static final String SECRET = "lib022-b-secret-constant";

    private static Engine engine;
    private static ProtosPolyglotExecutionContext authorized;
    private static ProtosPolyglotExecutionContext otherAuthorized;
    private static ProtosPrelude prelude;
    private static ProtosActivation caller;
    private static ProtosObjectValue inspect;

    @BeforeAll
    static void bootstrap() throws IOException {
        prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(CORE, new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        engine = Engine.newBuilder(ProtosLanguage.ID).option("protos.Inspect", "true").build();
        authorized = open();
        otherAuthorized = open();
        caller = prelude.newModuleActivation();
        inspect = importInspect(authorized, caller);
    }

    @AfterAll
    static void close() {
        authorized.close();
        otherAuthorized.close();
        engine.close();
    }

    // ---- Public surface ----

    @Test
    void importedModuleExportsExactlyTheFourOperations() {
        assertEquals(OPERATIONS, inspect.localSlotsSnapshot().keySet());
        assertFalse(inspect.hasLocalSlot(ProtosInspectFacility.BOOTSTRAP_SLOT));
    }

    @Test
    void facilityAndOperationsAreNoPreludeBindings() {
        assertTrue(
                prelude.bindings().readLocalSlot(ProtosInspectFacility.BOOTSTRAP_SLOT).isEmpty());
        for (String operation : OPERATIONS) {
            assertTrue(prelude.bindings().readLocalSlot(operation).isEmpty(), operation);
        }
    }

    @Test
    void createdFacilityIsFrozenWithExactlyFourNativeOperations() {
        ProtosObjectValue facility = ProtosInspectFacility.createFacility();
        assertTrue(facility.isFrozen());
        assertEquals(OPERATIONS, facility.localSlotsSnapshot().keySet());
        for (Object operation : facility.localSlotsSnapshot().values()) {
            assertTrue(
                    assertInstanceOf(ProtosClosureValue.class, operation).nativeBody().isPresent());
        }
        assertNotSame(facility, ProtosInspectFacility.createFacility());
    }

    @Test
    void moduleIsCachedPerActorAndEachActorOwnsItsInstance() {
        assertSame(inspect, importInspect(authorized, caller));
        ProtosActivation otherActor = prelude.newModuleActivation();
        ProtosObjectValue other = importInspect(authorized, otherActor);
        assertNotSame(inspect, other);
        assertEquals(OPERATIONS, other.localSlotsSnapshot().keySet());
        assertNotSame(
                inspect.readLocalSlot("bytecode").orElseThrow(),
                other.readLocalSlot("bytecode").orElseThrow());
    }

    // ---- Arity, type and Error identity ----

    @Test
    void everyOperationRejectsZeroAndTwoArgumentsWithFreshOrdinaryErrors() {
        for (String operation : OPERATIONS) {
            ProtosSignalException none = signal(() -> call(operation));
            ProtosSignalException two = signal(() -> call(operation, text("a"), text("b")));
            assertOrdinary(none);
            assertOrdinary(two);
            assertNotSame(none.error(), two.error(), operation);
        }
    }

    @Test
    void closureOperationsRejectNonClosureValues() {
        for (String operation : CLOSURE_OPERATIONS) {
            assertOrdinary(signal(() -> call(operation, text("not a Closure"))));
            assertOrdinary(signal(() -> call(operation, ProtosNullValue.INSTANCE)));
            assertOrdinary(signal(() -> call(operation, ordinaryObject())));
        }
    }

    // ---- representation ----

    @Test
    void representationClassifiesValueFamiliesShallowly() {
        assertFamily(ProtosNullValue.INSTANCE, "null");
        assertFamily(ProtosBooleanValue.TRUE, "Boolean");
        assertFamily(evaluate("1"), "Integer");
        assertFamily(evaluate("1.5"), "Float");
        assertFamily(text(SECRET), "String");
        assertFamily(evaluate("[1, 2]"), "Array");
        assertFamily(evaluate("Encoding.UTF8.encode(\"ab\")"), "Bytes");
        assertFamily(evaluate("Map()"), "Map");
        assertFamily(evaluate("IdentityMap()"), "IdentityMap");
        assertFamily(ordinaryObject(), "Object (ordinary)");
        assertFamily(nativeClosure(), "Closure (native body)");
        assertFamily(evaluate("(x) => x"), "Closure (source-backed)");
    }

    @Test
    void representationNeverRendersContents() {
        String string = success("representation", text(SECRET));
        assertFalse(string.contains(SECRET));
        ProtosObjectValue object = ordinaryObject();
        object.createLocalSlot(SECRET, text(SECRET));
        String objectText = success("representation", object);
        assertFalse(objectText.contains(SECRET));
        String array = success("representation", evaluate("[\"" + SECRET + "\"]"));
        assertFalse(array.contains(SECRET));
    }

    // ---- callableInfo ----

    @Test
    void callableInfoDistinguishesNativeAndSourceBackedClosures() {
        String nativeInfo = success("callableInfo", nativeClosure());
        assertTrue(nativeInfo.contains("native Closure"), nativeInfo);

        String sourceInfo = success("callableInfo", evaluate("(a, b) => \"" + SECRET + "\""));
        assertTrue(sourceInfo.contains("source-backed Closure"), sourceInfo);
        assertTrue(sourceInfo.contains("2 declared parameter"), sourceInfo);
        assertFalse(sourceInfo.contains(SECRET), sourceInfo);
    }

    @Test
    void nativeClosuresAcquireNoFictitiousPhysicalRepresentation() {
        assertOrdinary(signal(() -> call("bytecode", nativeClosure())));
        assertOrdinary(signal(() -> call("truffleTree", nativeClosure())));
    }

    // ---- Physical projection ----

    @Test
    void bytecodeListsRealInstructionsWithoutConstantOperandValues() {
        String bytecode =
                success("bytecode", evaluate("(x) => { x + \"" + SECRET + "\" }"));
        assertTrue(bytecode.contains("Bytecode DSL"), bytecode);
        assertTrue(bytecode.contains("semantic Closure activation root"), bytecode);
        assertTrue(bytecode.lines().count() > 2, bytecode);
        assertFalse(bytecode.contains(SECRET), bytecode);
    }

    @Test
    void truffleTreeIsThePhysicalTreeAndNotCompilerIr() {
        String tree = success("truffleTree", evaluate("(x) => { x + \"" + SECRET + "\" }"));
        assertTrue(tree.contains("physical Truffle node tree"), tree);
        assertTrue(tree.contains("not Graal compiler IR"), tree);
        assertFalse(tree.contains(SECRET), tree);
    }

    @Test
    void inspectionNeverCallsTheInspectedClosure() {
        Object result =
                evaluate(
                        """
                        (() => {
                            Inspect: import("std:inspect")
                            calls: 0
                            target: () => {
                                calls = calls + 1
                            }
                            Inspect.representation(target)
                            Inspect.callableInfo(target)
                            Inspect.bytecode(target)
                            Inspect.truffleTree(target)
                            calls == 0
                        })()
                        """);
        assertSame(ProtosBooleanValue.TRUE, result);
    }

    @Test
    void physicalRootIsTheEnteredContextsOwnCachedProjection() {
        // Warm the module wrappers so only the inspected Closure's projection is counted.
        success("bytecode", evaluate("(y) => y"));
        ProtosClosureValue closure =
                assertInstanceOf(ProtosClosureValue.class, evaluate("(x) => x"));
        int before = projections(authorized);
        success("bytecode", closure);
        assertEquals(before + 1, projections(authorized));
        success("truffleTree", closure);
        assertEquals(before + 1, projections(authorized));

        // Another authorized Context never reuses this Context's root: it builds its own.
        int otherBefore = projections(otherAuthorized);
        int ownAfter = projections(authorized);
        assertInstanceOf(
                ProtosStringValue.class,
                otherAuthorized.callEntered(
                        () ->
                                ProtosInvocation.invokeMessage(
                                        inspect, "bytecode", List.of(closure), caller)));
        assertTrue(projections(otherAuthorized) > otherBefore);
        assertEquals(ownAfter, projections(authorized));
    }

    @Test
    void failedRematerializationSignalsFreshErrorsAndCachesNoProjection() {
        ProtosClosureValue source =
                assertInstanceOf(ProtosClosureValue.class, evaluate("(x) => x"));
        java.util.concurrent.atomic.AtomicInteger attempts =
                new java.util.concurrent.atomic.AtomicInteger();
        ProtosClosureValue deferred =
                source.parallelProjectionDeferred(
                        List.of(),
                        ProtosNullValue.INSTANCE,
                        prelude,
                        () -> {
                            attempts.incrementAndGet();
                            throw new IllegalStateException("rematerialization unavailable");
                        });
        // Warm the module wrappers so only the inspected Closure could add a projection.
        success("bytecode", evaluate("(y) => y"));
        int before = projections(authorized);

        assertTrue(success("callableInfo", deferred).contains("plan: deferred"));
        assertEquals(0, attempts.get());

        ProtosSignalException bytecode = signal(() -> call("bytecode", deferred));
        ProtosSignalException tree = signal(() -> call("truffleTree", deferred));
        assertOrdinary(bytecode);
        assertOrdinary(tree);
        assertNotSame(bytecode.error(), tree.error());
        assertEquals(2, attempts.get());
        assertTrue(deferred.executionPlan().isEmpty());
        assertEquals(before, projections(authorized));
    }

    @Test
    void signallingRematerializationIsConvertedToFreshInspectionErrors() {
        ProtosClosureValue source =
                assertInstanceOf(ProtosClosureValue.class, evaluate("(x) => x"));
        java.util.concurrent.atomic.AtomicInteger attempts =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<ProtosSignalException> originals = new java.util.ArrayList<>();
        ProtosClosureValue deferred =
                source.parallelProjectionDeferred(
                        List.of(),
                        ProtosNullValue.INSTANCE,
                        prelude,
                        () -> {
                            attempts.incrementAndGet();
                            ProtosSignalException original =
                                    new ProtosSignalException(
                                            com.guillermomolina.protos.runtime.ProtosCoreErrors
                                                    .newError(caller));
                            originals.add(original);
                            throw original;
                        });
        success("bytecode", evaluate("(y) => y"));
        int before = projections(authorized);

        assertTrue(success("callableInfo", deferred).contains("plan: deferred"));
        assertEquals(0, attempts.get());

        ProtosSignalException bytecode = signal(() -> call("bytecode", deferred));
        assertEquals(1, attempts.get());
        ProtosSignalException tree = signal(() -> call("truffleTree", deferred));
        assertEquals(2, attempts.get());
        assertOrdinary(bytecode);
        assertOrdinary(tree);
        assertNotSame(bytecode.error(), tree.error());
        for (ProtosSignalException original : originals) {
            assertNotSame(original.error(), bytecode.error());
            assertNotSame(original.error(), tree.error());
        }
        // No plan was installed, so the Closure's body could not have run.
        assertTrue(deferred.executionPlan().isEmpty());
        assertEquals(before, projections(authorized));
    }

    @Test
    void outputWithinTheBoundCarriesNoTruncationMarker() {
        String lines = ProtosInspectFacility.boundedLines("header\n", List.of("a\n", "b\n"));
        assertEquals("header\na\nb\n", lines);
        String text = ProtosInspectFacility.boundedText("header\n", "tree\n");
        assertEquals("header\ntree\n", text);
        assertFalse(lines.contains(ProtosInspectFacility.TRUNCATION_MARKER));
        assertFalse(text.contains(ProtosInspectFacility.TRUNCATION_MARKER));
    }

    @Test
    void oversizedOutputIsTruncatedWithinTheBoundIncludingTheMarker() {
        int max = ProtosInspectFacility.MAX_OUTPUT_CHARS;
        String line = "x".repeat(99) + "\n";
        List<String> many = java.util.Collections.nCopies(2 * max / line.length(), line);
        String lines = ProtosInspectFacility.boundedLines("header\n", many);
        assertTrue(lines.length() <= max, Integer.toString(lines.length()));
        assertTrue(lines.endsWith(ProtosInspectFacility.TRUNCATION_MARKER + "\n"));
        assertTrue(lines.startsWith("header\n" + line));
        // Only whole instruction lines are kept before the marker.
        String kept =
                lines.substring(
                        "header\n".length(),
                        lines.length() - ProtosInspectFacility.TRUNCATION_MARKER.length() - 1);
        assertEquals(0, kept.length() % line.length());

        String text = ProtosInspectFacility.boundedText("header\n", "y".repeat(2 * max));
        assertTrue(text.length() <= max, Integer.toString(text.length()));
        assertTrue(text.endsWith("\n" + ProtosInspectFacility.TRUNCATION_MARKER + "\n"));
        assertFalse(text.isBlank());

        // Exactly at the bound: no truncation.
        String exact = "z".repeat(max - "header\n".length());
        assertEquals(max, ProtosInspectFacility.boundedText("header\n", exact).length());
        assertFalse(
                ProtosInspectFacility.boundedText("header\n", exact)
                        .contains(ProtosInspectFacility.TRUNCATION_MARKER));
    }

    @Test
    void resultsAreOnlyFreshPlainStrings() {
        ProtosClosureValue closure =
                assertInstanceOf(ProtosClosureValue.class, evaluate("(x) => x"));
        for (String operation : OPERATIONS) {
            Object first = call(operation, closure);
            Object second = call(operation, closure);
            assertSame(ProtosStringValue.class, first.getClass(), operation);
            assertFalse(((ProtosStringValue) first).value().isBlank(), operation);
            assertNotSame(first, second, operation);
        }
    }

    // ---- Authority of the entered Context ----

    @Test
    void retainedOperationsAndCachedModulesAreDeniedInAnUnauthorizedContext() {
        ProtosClosureValue closure =
                assertInstanceOf(ProtosClosureValue.class, evaluate("(x) => x"));
        for (String operation : OPERATIONS) {
            ProtosSignalException denied =
                    signal(
                            () ->
                                    ProtosTestExecutionSupport.callEntered(
                                            () ->
                                                    ProtosInvocation.invokeMessage(
                                                            inspect,
                                                            operation,
                                                            List.of(closure),
                                                            caller)));
            assertOrdinary(denied);
        }
        // The module is cached in this Actor, yet the unauthorized import is still rejected.
        Object denied =
                ProtosTestExecutionSupport.evaluate(
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
                        """,
                        caller);
        assertSame(ProtosBooleanValue.TRUE, denied);
    }

    // ---- Helpers ----

    private static ProtosPolyglotExecutionContext open() {
        return ProtosPolyglotExecutionContext.open(
                engine,
                InputStream.nullInputStream(),
                OutputStream.nullOutputStream(),
                OutputStream.nullOutputStream(),
                () -> {});
    }

    private static ProtosObjectValue importInspect(
            ProtosPolyglotExecutionContext context, ProtosActivation activation) {
        return assertInstanceOf(
                ProtosObjectValue.class,
                context.callEntered(
                        () ->
                                ProtosTestExecutionSupport.evaluate(
                                        "import(\"std:inspect\")", activation)));
    }

    private static Object evaluate(String source) {
        return authorized.callEntered(() -> ProtosTestExecutionSupport.evaluate(source, caller));
    }

    private static Object call(String operation, Object... arguments) {
        return authorized.callEntered(
                () ->
                        ProtosInvocation.invokeMessage(
                                inspect, operation, List.of(arguments), caller));
    }

    private static String success(String operation, Object argument) {
        String result =
                assertInstanceOf(ProtosStringValue.class, call(operation, argument)).value();
        assertFalse(result.isBlank(), operation);
        return result;
    }

    private static void assertFamily(Object value, String family) {
        String result = success("representation", value);
        assertTrue(result.contains("family: " + family + "\n"), result);
    }

    private static ProtosSignalException signal(Supplier<?> action) {
        return assertThrows(ProtosSignalException.class, action::get);
    }

    private static void assertOrdinary(ProtosSignalException signal) {
        assertSame(prelude.errorPrototype(), signal.error().parent().orElseThrow());
    }

    private static int projections(ProtosPolyglotExecutionContext context) {
        return context.callEntered(
                () -> ProtosLanguageContext.current().projectedBytecodeExecutionPlanCountForTesting());
    }

    private static ProtosObjectValue ordinaryObject() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject());
    }

    private static ProtosClosureValue nativeClosure() {
        return ProtosClosureValue.nativeClosure(
                (activation, supplied) -> {
                    throw new AssertionError("inspection must not call a native Closure");
                });
    }

    private static ProtosStringValue text(String value) {
        return new ProtosStringValue(value);
    }
}

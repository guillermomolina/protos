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
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * D205 (LIB022-A): the public surface of {@code std:runtime}, the exact five-field snapshot of
 * {@code info()}, its ordinary Error failures, and Actor-local module ownership of the deferred
 * private facility.
 */
final class ProtosRuntimeMetadataFacilityTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Set<String> FIELDS =
            Set.of(
                    "implementation",
                    "implementationVersion",
                    "engineVersion",
                    "platform",
                    "executionMode");

    private static ProtosPrelude prelude;
    private static ProtosActivation caller;
    private static ProtosObjectValue runtime;

    @BeforeAll
    static void bootstrap() throws IOException {
        prelude =
                new ProtosCoreBootstrap()
                        .bootstrap(CORE, new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        caller = prelude.newModuleActivation();
        runtime = importRuntime(caller);
    }

    // ---- 7.1 Public surface ----

    @Test
    void importedModuleExportsExactlyInfo() {
        assertEquals(Set.of("info"), runtime.localSlotsSnapshot().keySet());
        assertFalse(runtime.hasLocalSlot(ProtosRuntimeMetadataFacility.BOOTSTRAP_SLOT));
    }

    @Test
    void facilityIsNoPreludeBinding() {
        assertTrue(
                prelude.bindings()
                        .readLocalSlot(ProtosRuntimeMetadataFacility.BOOTSTRAP_SLOT)
                        .isEmpty());
        assertTrue(prelude.bindings().readLocalSlot("info").isEmpty());
    }

    // ---- 7.2 Snapshot structure ----

    @Test
    void snapshotIsAFrozenOrdinaryObjectWithExactlyFiveStringSlots() {
        ProtosObjectValue snapshot = info();
        assertSame(ProtosObjectValue.class, snapshot.getClass());
        assertSame(ProtosObjectValue.rootObject(), snapshot.parent().orElseThrow());
        Map<String, Object> slots = snapshot.localSlotsSnapshot();
        assertEquals(FIELDS, slots.keySet());
        for (Object value : slots.values()) {
            assertInstanceOf(ProtosStringValue.class, value);
        }
        assertTrue(snapshot.isFrozen());
    }

    // ---- 7.3 Freshness ----

    @Test
    void everyCallAllocatesAFreshSnapshotAndLeavesEarlierOnesUnchanged() {
        ProtosObjectValue first = info();
        Map<String, Object> before = Map.copyOf(first.localSlotsSnapshot());
        ProtosObjectValue second = info();
        assertNotSame(first, second);
        assertEquals(before, first.localSlotsSnapshot());
        assertTrue(first.isFrozen());
    }

    // ---- 7.4 Field values ----

    @Test
    void fieldsDescribeTheEnteredJvmNormalContext() {
        ProtosObjectValue snapshot = info();
        assertEquals("Protos", text(snapshot, "implementation"));
        assertEquals("JVM", text(snapshot, "platform"));
        assertEquals("NORMAL", text(snapshot, "executionMode"));
        assertEquals(ProtosVersionIdentity.current(), text(snapshot, "implementationVersion"));

        String engineVersion =
                ProtosTestExecutionSupport.callEntered(
                        () -> org.graalvm.polyglot.Context.getCurrent().getEngine().getVersion());
        assertFalse(engineVersion.isEmpty());
        assertEquals(engineVersion, text(snapshot, "engineVersion"));
    }

    // ---- 7.5 Error cases ----

    @Test
    void anyArgumentSignalsAFreshOrdinaryError() {
        ProtosSignalException one =
                assertThrows(ProtosSignalException.class, () -> call(text("1")));
        ProtosSignalException two =
                assertThrows(ProtosSignalException.class, () -> call(text("1"), text("2")));
        assertSame(prelude.errorPrototype(), one.error().parent().orElseThrow());
        assertSame(prelude.errorPrototype(), two.error().parent().orElseThrow());
        assertNotSame(one.error(), two.error());
        ProtosSignalException again =
                assertThrows(ProtosSignalException.class, () -> call(text("1")));
        assertNotSame(one.error(), again.error());
    }

    @Test
    void guestWrongArityCallsSignalErrorWithoutEscapingASnapshot() {
        Object result =
                ProtosTestExecutionSupport.evaluate(
                        """
                        (() => {
                            Runtime: import("std:runtime")
                            escaped: false
                            errors: 0
                            Error.handle(
                                () => {
                                    Runtime.info(1)
                                    escaped = true
                                },
                                (error) => {
                                    errors = errors + 1
                                }
                            )
                            Error.handle(
                                () => {
                                    Runtime.info(1, 2)
                                    escaped = true
                                },
                                (error) => {
                                    errors = errors + 1
                                }
                            )
                            (errors == 2) && !escaped
                        })()
                        """,
                        caller);
        assertSame(com.guillermomolina.protos.runtime.ProtosBooleanValue.TRUE, result);
    }

    // ---- 7.6 No accidental authority ----

    @Test
    void snapshotExposesNoAuthorityOrImplementationObject() {
        ProtosObjectValue snapshot = info();
        for (String name :
                List.of(
                        "Context",
                        "Engine",
                        "Actor",
                        "Task",
                        "Process",
                        "RootCallTarget",
                        "inspect",
                        "isCompiled",
                        "jitEnabled")) {
            assertFalse(snapshot.hasLocalSlot(name), name);
        }
        for (Object value : snapshot.localSlotsSnapshot().values()) {
            assertSame(ProtosStringValue.class, value.getClass());
        }
    }

    // ---- 7.7 Module cache and Actor ownership ----

    @Test
    void moduleIsCachedPerActorAndEachActorOwnsItsInstance() {
        assertSame(runtime, importRuntime(caller));

        ProtosActivation otherActor = prelude.newModuleActivation();
        ProtosObjectValue otherRuntime = importRuntime(otherActor);
        assertNotSame(runtime, otherRuntime);
        assertEquals(Set.of("info"), otherRuntime.localSlotsSnapshot().keySet());
        // Each initialization created its own private facility, so the public Closures differ.
        assertNotSame(
                runtime.readLocalSlot("info").orElseThrow(),
                otherRuntime.readLocalSlot("info").orElseThrow());
        ProtosObjectValue snapshot =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosTestExecutionSupport.callEntered(
                                () ->
                                        ProtosInvocation.invokeMessage(
                                                otherRuntime, "info", List.of(), otherActor)));
        assertEquals(FIELDS, snapshot.localSlotsSnapshot().keySet());
    }

    private static ProtosObjectValue importRuntime(ProtosActivation activation) {
        return assertInstanceOf(
                ProtosObjectValue.class,
                ProtosTestExecutionSupport.evaluate("import(\"std:runtime\")", activation));
    }

    private static ProtosObjectValue info() {
        return assertInstanceOf(ProtosObjectValue.class, call());
    }

    private static Object call(Object... arguments) {
        return ProtosTestExecutionSupport.callEntered(
                () -> ProtosInvocation.invokeMessage(runtime, "info", List.of(arguments), caller));
    }

    private static ProtosStringValue text(String value) {
        return new ProtosStringValue(value);
    }

    private static String text(ProtosObjectValue snapshot, String slot) {
        return assertInstanceOf(
                        ProtosStringValue.class, snapshot.readLocalSlot(slot).orElseThrow())
                .value();
    }
}

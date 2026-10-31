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

import com.guillermomolina.protos.analysis.ProtosDocumentSnapshot;
import com.guillermomolina.protos.analysis.ProtosStaticAnalysisCore;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosActorScheduler;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosReplSelectorObservation;
import com.guillermomolina.protos.runtime.ProtosReplSelectorObservation.Provenance;
import com.guillermomolina.protos.runtime.ProtosReplSelectorObservation.Receiver;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtosReplLexicalObservationTest {
    private static ProtosHostedExecutionTestFixture open()
            throws Exception {
        return ProtosHostedExecutionTestFixture.open(
                new ProtosCoreBootstrap().bootstrap(
                        Path.of("protos", "lib", "core")));
    }

    private static ProtosPolyglotProcessContext context(
            ProtosHostedExecutionTestFixture hosted) {
        return (ProtosPolyglotProcessContext)
                hosted.process()
                        .executionHostForRuntime()
                        .orElseThrow();
    }

    @Test
    void reflectsCreationRemovalAndRecreation() throws Exception {
        try (var hosted = open()) {
            var ctx = context(hosted);
            var activation = hosted.activation();

            assertFalse(ctx
                    .tryObservePersistentLexicalNamesForRuntime(activation)
                    .contains("sessionVariable"));

            hosted.evaluatePersistent(
                    "<d227-create>", "sessionVariable: 1");

            assertTrue(ctx
                    .tryObservePersistentLexicalNamesForRuntime(activation)
                    .contains("sessionVariable"));

            hosted.callEntered(() -> {
                activation.context().removeLocalSlot("sessionVariable");
                return null;
            });

            assertFalse(ctx
                    .tryObservePersistentLexicalNamesForRuntime(activation)
                    .contains("sessionVariable"));

            hosted.callEntered(() -> {
                activation.context().createLocalSlot(
                        "sessionVariable", ProtosNullValue.INSTANCE);
                return null;
            });

            assertTrue(ctx
                    .tryObservePersistentLexicalNamesForRuntime(activation)
                    .contains("sessionVariable"));
        }
    }

    @Test
    void contentionFailsClosedWithoutWaiting() throws Exception {
        try (var hosted = open()) {
            var ctx = context(hosted);

            Field field = ProtosPolyglotProcessContext.class
                    .getDeclaredField("persistentRootActorGate");
            field.setAccessible(true);
            ReentrantLock gate =
                    (ReentrantLock) field.get(ctx);

            var executor = Executors.newSingleThreadExecutor();

            gate.lock();
            try {
                var result = executor.submit(() ->
                        ctx.tryObservePersistentLexicalNamesForRuntime(
                                hosted.activation()));

                assertEquals(
                        List.of(),
                        result.get(5, TimeUnit.SECONDS));
            } finally {
                gate.unlock();
                executor.shutdownNow();
            }

            assertFalse(ctx
                    .tryObservePersistentLexicalNamesForRuntime(
                            hosted.activation())
                    .isEmpty());
        }
    }

    @Test
    void closedProcessReturnsNoNames() throws Exception {
        var hosted = open();
        var ctx = context(hosted);
        var activation = hosted.activation();

        try {
            hosted.evaluatePersistent(
                    "<d227-before-close>", "liveBeforeClose: 1");

            assertTrue(ctx
                    .tryObservePersistentLexicalNamesForRuntime(activation)
                    .contains("liveBeforeClose"));
        } finally {
            hosted.close();
        }

        assertEquals(
                List.of(),
                ctx.tryObservePersistentLexicalNamesForRuntime(activation));
    }

    @Test
    void foreignActivationIsRejected() throws Exception {
        try (var first = open(); var second = open()) {
            assertEquals(
                    List.of(),
                    context(first)
                            .tryObservePersistentLexicalNamesForRuntime(
                                    second.activation()));
        }
    }

    private static Map<String, Provenance> selectors(
            ProtosPolyglotProcessContext ctx,
            ProtosActivation activation,
            Receiver receiver) {
        Map<String, Provenance> observed = new LinkedHashMap<>();
        for (var candidate : ctx.tryObservePersistentSelectorNamesForRuntime(
                activation, receiver)) {
            assertNull(
                    observed.put(candidate.name(), candidate.provenance()),
                    "duplicate selector " + candidate.name());
        }
        return observed;
    }

    private static Map<String, Provenance> selectorsOf(
            ProtosHostedExecutionTestFixture hosted, String name) {
        return selectors(
                context(hosted), hosted.activation(), Receiver.lexicalName(name));
    }

    private static ProtosObjectValue binding(
            ProtosHostedExecutionTestFixture hosted, String name) {
        return hosted.callEntered(() -> (ProtosObjectValue)
                hosted.activation().context().readLocalSlot(name).orElseThrow());
    }

    @Test
    void ownDataAndClosureSlotsAreBothSelectors() throws Exception {
        try (var hosted = open()) {
            hosted.evaluatePersistent("<d228-own>",
                    "dog: {\n    name: \"Rex\"\n    speak: () => { \"woof\" }\n}");

            var observed = selectorsOf(hosted, "dog");
            assertEquals(Provenance.OWN, observed.get("name"));
            assertEquals(Provenance.OWN, observed.get("speak"));
            assertEquals(
                    List.of("name", "speak"),
                    observed.keySet().stream().limit(2).toList());
        }
    }

    @Test
    void inheritedSelectorsShadowingAndRemovalFollowDelegation() throws Exception {
        try (var hosted = open()) {
            hosted.evaluatePersistent("<d228-inherit>",
                    "animal: {\n    breathe: () => { 1 }\n    speak: () => { 2 }\n}\n"
                            + "dog: animal {\n    speak: 3\n}");

            var observed = selectorsOf(hosted, "dog");
            assertEquals(Provenance.DELEGATED, observed.get("breathe"));
            assertEquals(Provenance.OWN, observed.get("speak"));

            ProtosObjectValue dog = binding(hosted, "dog");
            hosted.callEntered(() -> dog.removeLocalSlot("speak"));

            assertEquals(Provenance.DELEGATED, selectorsOf(hosted, "dog").get("speak"));
        }
    }

    @Test
    void replacedAndRecreatedBindingsUseCurrentState() throws Exception {
        try (var hosted = open()) {
            hosted.evaluatePersistent("<d228-first>", "pet: { first: 1 }");
            assertTrue(selectorsOf(hosted, "pet").containsKey("first"));

            hosted.evaluatePersistent("<d228-replace>", "pet = { second: 2 }");
            var replaced = selectorsOf(hosted, "pet");
            assertFalse(replaced.containsKey("first"));
            assertTrue(replaced.containsKey("second"));

            hosted.callEntered(() -> hosted.activation().context().removeLocalSlot("pet"));
            assertTrue(selectorsOf(hosted, "pet").isEmpty());

            hosted.evaluatePersistent("<d228-recreate>", "pet: { third: 3 }");
            assertTrue(selectorsOf(hosted, "pet").containsKey("third"));
        }
    }

    @Test
    void nullAndNumbersDelegateWithoutOwnSelectors() throws Exception {
        try (var hosted = open()) {
            hosted.evaluatePersistent("<d228-special>", "nothing: null\nanswer: 42");

            for (String name : List.of("nothing", "answer")) {
                var observed = selectorsOf(hosted, name);
                assertFalse(observed.isEmpty(), name + " is PRESENT and delegates");
                assertTrue(
                        observed.values().stream().allMatch(p -> p == Provenance.DELEGATED),
                        name + " has no ordinary local table");
            }
        }
    }

    @Test
    void absentBindingHasNoFallbackToThis() throws Exception {
        try (var hosted = open()) {
            assertTrue(selectorsOf(hosted, "missingBinding").isEmpty());
        }
    }

    @Test
    void frozenAndClosedObjectsAreObservedWithoutMutation() throws Exception {
        try (var hosted = open()) {
            hosted.evaluatePersistent("<d228-frozen>",
                    "frozenDog: { kept: 1 }\nclosedDog: { held: 2 }");
            ProtosObjectValue frozen = binding(hosted, "frozenDog");
            ProtosObjectValue closed = binding(hosted, "closedDog");
            hosted.callEntered(() -> {
                frozen.freeze();
                closed.close();
                return null;
            });

            assertEquals(Provenance.OWN, selectorsOf(hosted, "frozenDog").get("kept"));
            assertEquals(Provenance.OWN, selectorsOf(hosted, "closedDog").get("held"));
            assertTrue(frozen.isFrozen());
        }
    }

    @Test
    void observationNeverReadsOrExecutesCandidateValuesOrComputedReceivers()
            throws Exception {
        try (var hosted = open()) {
            hosted.evaluatePersistent("<d228-probe>",
                    "count: 0\n"
                            + "probe: { touch: () => { count = count + 1 } }\n"
                            + "makeProbe: () => {\n    count = count + 1\n    probe\n}");

            assertEquals(Provenance.OWN, selectorsOf(hosted, "probe").get("touch"));

            // A computed receiver is never a proven site, so nothing runs.
            String computed = "makeProbe().to";
            assertTrue(new ProtosStaticAnalysisCore().memberCompletionSite(
                    new ProtosDocumentSnapshot("memory:/d228.protos", 0, computed),
                    computed.length()).isEmpty());

            assertSame(
                    ProtosBooleanValue.TRUE,
                    hosted.evaluatePersistent("<d228-count>", "count == 0"));
        }
    }

    @Test
    void selectorObservationFailsClosedOnLifecycleAndContention() throws Exception {
        var hosted = open();
        var ctx = context(hosted);
        var activation = hosted.activation();
        try (var other = open()) {
            hosted.evaluatePersistent("<d228-live>", "dog: { name: 1 }");
            assertFalse(selectorsOf(hosted, "dog").isEmpty());

            assertEquals(List.of(), context(other).tryObservePersistentSelectorNamesForRuntime(
                    activation, Receiver.lexicalName("dog")));

            Field field = ProtosPolyglotProcessContext.class
                    .getDeclaredField("persistentRootActorGate");
            field.setAccessible(true);
            ReentrantLock gate = (ReentrantLock) field.get(ctx);
            var executor = Executors.newSingleThreadExecutor();
            gate.lock();
            try {
                var result = executor.submit(() ->
                        ctx.tryObservePersistentSelectorNamesForRuntime(
                                activation, Receiver.lexicalName("dog")));
                assertEquals(List.of(), result.get(5, TimeUnit.SECONDS));
            } finally {
                gate.unlock();
                executor.shutdownNow();
            }
        } finally {
            hosted.close();
        }

        assertEquals(List.of(), ctx.tryObservePersistentSelectorNamesForRuntime(
                activation, Receiver.lexicalName("dog")));
    }

    @SuppressWarnings("try")
    @Test
    void activeRootActorPermitBlocksObservationNotTheActor() throws Exception {
        try (var hosted = open()) {
            var ctx = context(hosted);
            hosted.evaluatePersistent("<d228-busy>", "dog: { name: 1 }");

            ProtosActorScheduler scheduler = ctx.actorSchedulerForRuntime().orElseThrow();
            try (var held = scheduler.tryAcquireObservation(
                    hosted.process().rootActorForRuntime()).orElseThrow()) {
                assertTrue(selectorsOf(hosted, "dog").isEmpty());
            }

            assertEquals(Provenance.OWN, selectorsOf(hosted, "dog").get("name"));
            // The RootActor itself keeps running persistent evaluation.
            hosted.evaluatePersistent("<d228-after>", "dog.name");

            // The plain REPL RootActor is not attached to the scheduler; a
            // completed observation must not leave it registered.
            var root = hosted.process().rootActorForRuntime();
            assertThrows(IllegalStateException.class,
                    () -> scheduler.submitControl(root, () -> { }));
            scheduler.tryAcquireObservation(root).orElseThrow().close();
        }
    }

    @Test
    void nameAndDelegationBudgetsFailClosed() throws Exception {
        try (var hosted = open()) {
            ProtosActivation activation = hosted.activation();
            hosted.callEntered(() -> {
                ProtosObjectValue small = new ProtosObjectValue(ProtosObjectValue.rootObject());
                for (int index = 0; index < 100; index++) {
                    small.createLocalSlot("slot" + index, ProtosNullValue.INSTANCE);
                }
                ProtosObjectValue wide = new ProtosObjectValue(ProtosObjectValue.rootObject());
                for (int index = 0;
                        index <= ProtosReplSelectorObservation.MAX_EXAMINED_NAMES;
                        index++) {
                    wide.createLocalSlot("slot" + index, ProtosNullValue.INSTANCE);
                }
                activation.context().createLocalSlot("small", small);
                activation.context().createLocalSlot("wide", wide);

                Object shallow = ProtosObjectValue.rootObject();
                for (int depth = 1;
                        depth < ProtosReplSelectorObservation.MAX_DELEGATION_NODES;
                        depth++) {
                    shallow = new ProtosObjectValue(shallow);
                }
                activation.context().createLocalSlot("shallow", shallow);
                activation.context().createLocalSlot("deep", new ProtosObjectValue(shallow));
                return null;
            });

            assertEquals(Provenance.OWN, selectorsOf(hosted, "small").get("slot99"));
            assertTrue(selectorsOf(hosted, "wide").isEmpty());
            assertFalse(selectorsOf(hosted, "shallow").isEmpty());
            assertTrue(selectorsOf(hosted, "deep").isEmpty());
        }
    }

    private static boolean beginDirect(
            ProtosPolyglotProcessContext ctx, ProtosActivation activation) throws Exception {
        Method begin = ProtosPolyglotProcessContext.class
                .getDeclaredMethod("beginDirectRootEntry", ProtosActivation.class);
        begin.setAccessible(true);
        return (Boolean) begin.invoke(ctx, activation);
    }

    private static void endDirect(ProtosPolyglotProcessContext ctx) throws Exception {
        Method end = ProtosPolyglotProcessContext.class.getDeclaredMethod("endDirectRootEntry");
        end.setAccessible(true);
        end.invoke(ctx);
    }

    private static int activeDirect(ProtosPolyglotProcessContext ctx) throws Exception {
        Field field = ProtosPolyglotProcessContext.class
                .getDeclaredField("activeDirectRootEntries");
        field.setAccessible(true);
        return field.getInt(ctx);
    }

    @Test
    void activeDirectRootEntriesBlockObservationIndependently() throws Exception {
        try (var hosted = open()) {
            var ctx = context(hosted);
            var activation = hosted.activation();
            hosted.evaluatePersistent("<d228-direct>", "dog: { name: 1 }");
            assertFalse(selectorsOf(hosted, "dog").isEmpty());

            assertTrue(beginDirect(ctx, activation));
            assertTrue(selectorsOf(hosted, "dog").isEmpty());
            assertTrue(ctx.tryObservePersistentLexicalNamesForRuntime(activation).isEmpty());

            assertTrue(beginDirect(ctx, activation));
            assertEquals(2, activeDirect(ctx));
            endDirect(ctx);
            assertTrue(selectorsOf(hosted, "dog").isEmpty(), "another entry is still active");
            endDirect(ctx);

            assertEquals(0, activeDirect(ctx));
            assertEquals(Provenance.OWN, selectorsOf(hosted, "dog").get("name"));
            assertTrue(ctx.tryObservePersistentLexicalNamesForRuntime(activation)
                    .contains("dog"));
        }
    }

    @Test
    void directEntryRegistrationIsRetiredOnFailureAndNeverGoesNegative() throws Exception {
        try (var hosted = open()) {
            var ctx = context(hosted);
            hosted.evaluatePersistent("<d228-failure>", "dog: { name: 1 }");

            try {
                hosted.execute("<d228-broken>", "broken: (");
            } catch (RuntimeException expectedFailure) {
                // A failed direct entry must still retire its registration.
            }
            try {
                hosted.execute("<d228-error>", "missingBindingForD228.member");
            } catch (RuntimeException expectedFailure) {
                // A guest Error must still retire its registration.
            }

            assertEquals(0, activeDirect(ctx));
            assertFalse(selectorsOf(hosted, "dog").isEmpty());

            var unbalanced = assertThrows(
                    java.lang.reflect.InvocationTargetException.class, () -> endDirect(ctx));
            assertInstanceOf(IllegalStateException.class, unbalanced.getCause());
            assertEquals(0, activeDirect(ctx));
        }
    }

    @Test
    void otherActorEntriesDoNotCountAgainstThisRootActor() throws Exception {
        try (var hosted = open(); var other = open()) {
            var ctx = context(hosted);
            hosted.evaluatePersistent("<d228-other>", "dog: { name: 1 }");

            // An activation whose Actor is not this Process's RootActor is
            // neither counted nor admitted through this gate.
            assertFalse(beginDirect(ctx, other.activation()));
            assertEquals(0, activeDirect(ctx));

            other.execute("<d228-other-run>", "x: 1");
            assertEquals(0, activeDirect(ctx));
            assertEquals(Provenance.OWN, selectorsOf(hosted, "dog").get("name"));
        }
    }

    @Test
    void completedObservationRetainsOnlyInertNames() throws Exception {
        try (var hosted = open()) {
            hosted.evaluatePersistent("<d228-inert>", "dog: { name: 1 }");
            var candidates = context(hosted).tryObservePersistentSelectorNamesForRuntime(
                    hosted.activation(), Receiver.lexicalName("dog"));

            assertFalse(candidates.isEmpty());
            for (var candidate : candidates) {
                assertSame(String.class, candidate.name().getClass());
                assertNotNull(candidate.provenance());
            }
            assertThrows(UnsupportedOperationException.class, () -> candidates.add(
                    new ProtosReplSelectorObservation.Candidate("x", Provenance.OWN)));
        }
    }

    private static boolean observable(ProtosHostedExecutionTestFixture hosted) {
        return hosted.process().lifecycleState() == ProtosProcessRuntime.LifecycleState.RUNNING
                && hosted.process().rootActorForRuntime().lifecycleState()
                        == ProtosActor.LifecycleState.INITIALIZING;
    }

    /*
     * After a public direct entry finishes, no registration remains; while the
     * Process stays eligible, the previously visible selectors are observable
     * again.
     */
    private static void assertRetiredAndObservable(
            ProtosHostedExecutionTestFixture hosted, String path) throws Exception {
        assertEquals(0, activeDirect(context(hosted)), path);
        if (observable(hosted)) {
            assertEquals(Provenance.OWN, selectorsOf(hosted, "dog").get("name"), path);
        }
    }

    private static void attempt(Runnable entry) {
        try {
            entry.run();
        } catch (RuntimeException expectedFailure) {
            // Failure must still retire the registration; checked by the caller.
        }
    }

    @Test
    void publicExecuteRetiresItsEntryOnCompletionAndFailure() throws Exception {
        try (var hosted = open()) {
            hosted.evaluatePersistent("<d228-execute>", "dog: { name: 1 }");

            hosted.execute("<d228-execute-ok>", "1 + 1");
            assertRetiredAndObservable(hosted, "execute completed");

            attempt(() -> hosted.execute("<d228-execute-broken>", "broken: ("));
            assertRetiredAndObservable(hosted, "execute failed");
        }
    }

    @Test
    void publicExecuteFileRetiresItsEntryOnCompletionAndFailure(@TempDir Path directory)
            throws Exception {
        try (var hosted = open()) {
            var ctx = context(hosted);
            var activation = hosted.activation();
            hosted.evaluatePersistent("<d228-file>", "dog: { name: 1 }");

            Path valid = Files.writeString(directory.resolve("valid.protos"), "1 + 1");
            ctx.executeFile(valid, Files.readString(valid), activation);
            assertRetiredAndObservable(hosted, "executeFile completed");

            Path broken = Files.writeString(directory.resolve("broken.protos"), "broken: (");
            attempt(() -> ctx.executeFile(broken, "broken: (", activation));
            assertRetiredAndObservable(hosted, "executeFile failed");
        }
    }

    @Test
    void publicExecuteModuleSourceRetiresItsEntryOnCompletionAndFailure() throws Exception {
        try (var hosted = open()) {
            var ctx = context(hosted);
            var activation = hosted.activation();
            hosted.evaluatePersistent("<d228-module>", "dog: { name: 1 }");

            ctx.executeModuleSource(
                    ProtosModuleSource.fromCharacters(
                            new ProtosModuleKey("d228-module-ok"), "1 + 1"),
                    activation);
            assertRetiredAndObservable(hosted, "executeModuleSource completed");

            attempt(() -> ctx.executeModuleSource(
                    ProtosModuleSource.fromCharacters(
                            new ProtosModuleKey("d228-module-broken"), "broken: ("),
                    activation));
            assertRetiredAndObservable(hosted, "executeModuleSource failed");
        }
    }

    @Test
    void thisReceiverYieldsItsOwnNamesThroughThePublicObservation() throws Exception {
        try (var hosted = open()) {
            var activation = hosted.activation();
            var observed = selectors(context(hosted), activation, Receiver.thisReceiver());

            // Expected own names come from the same names-only projection, so
            // no guest code is evaluated to build the expectation either.
            List<String> expectedOwn = hosted.callEntered(() -> {
                ArrayList<String> names = new ArrayList<>();
                if (activation.receiver() instanceof ProtosObjectValue receiver) {
                    receiver.appendLocalBindingNamesTo(names);
                }
                return names;
            });
            assertEquals(
                    java.util.Set.copyOf(expectedOwn),
                    observed.entrySet().stream()
                            .filter(entry -> entry.getValue() == Provenance.OWN)
                            .map(Map.Entry::getKey)
                            .collect(java.util.stream.Collectors.toSet()));
        }
    }
}

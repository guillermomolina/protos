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

import com.guillermomolina.protos.runtime.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PLAT051-A: generic privileged Standard Library semantic-value rematerialization through Actor
 * and P transfer, exercised with test-only fixture families that no guest code can reach.
 */
final class ProtosSemanticTransferFamilyTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path MAIN = Path.of("src/main/java/com/guillermomolina/protos");

    private static final FixtureFamily PORTABLE =
            new FixtureFamily("std:plat051-fixture/Portable", Mode.PORTABLE);
    private static final FixtureFamily NULL_PAYLOAD =
            new FixtureFamily("std:plat051-fixture/NullPayload", Mode.NULL_PAYLOAD);
    private static final FixtureFamily MALFORMED_PAYLOAD =
            new FixtureFamily("std:plat051-fixture/MalformedPayload", Mode.MALFORMED_PAYLOAD);
    private static final FixtureFamily CLOSURE_PAYLOAD =
            new FixtureFamily("std:plat051-fixture/ClosurePayload", Mode.CLOSURE_PAYLOAD);
    private static final FixtureFamily THROWING =
            new FixtureFamily("std:plat051-fixture/Throwing", Mode.THROWING);
    private static final FixtureFamily OPEN_RESULT =
            new FixtureFamily("std:plat051-fixture/OpenResult", Mode.OPEN_RESULT);
    private static final FixtureFamily FOREIGN_RESULT =
            new FixtureFamily("std:plat051-fixture/ForeignResult", Mode.FOREIGN_RESULT);
    /** Same owner key as PORTABLE but never authorized by any bootstrap. */
    private static final FixtureFamily IMPOSTOR =
            new FixtureFamily("std:plat051-fixture/Portable", Mode.PORTABLE);

    private static ProtosPrelude prelude;
    private static Method parallelCopy;

    @BeforeAll
    static void bootstrap() throws Exception {
        prelude =
                ProtosCoreBootstrap.withSemanticTransferFamiliesForTesting(
                                List.of(
                                        PORTABLE,
                                        NULL_PAYLOAD,
                                        MALFORMED_PAYLOAD,
                                        CLOSURE_PAYLOAD,
                                        THROWING,
                                        OPEN_RESULT,
                                        FOREIGN_RESULT))
                        .bootstrap(CORE);
        Class<?> transfer =
                Class.forName("com.guillermomolina.protos.execution.ProtosParallelRuntime$Transfer");
        parallelCopy =
                transfer.getDeclaredMethod(
                        "copy", Object.class, ProtosActivation.class, IdentityHashMap.class);
        parallelCopy.setAccessible(true);
    }

    @Test
    void authorizedFamilyRematerializesThroughActorWithAliasesDistinctIdentitiesAndCycles() {
        ProtosSemanticTransferValue shared = PORTABLE.mint("a+b");
        ProtosSemanticTransferValue twin = PORTABLE.mint("a+b");
        ProtosObjectValue graph = surroundingGraph(shared, twin);
        int reconstructed = PORTABLE.reconstructed;

        List<Object> copied =
                ProtosActorValueTransfer.snapshotArguments(
                        List.of(shared, graph), prelude.newModuleActivation());

        assertEquals(reconstructed + 2, PORTABLE.reconstructed);
        assertRematerializedGraph(shared, twin, graph, copied.get(0), copied.get(1));
    }

    @Test
    void authorizedFamilyRematerializesThroughPWithAliasesDistinctIdentitiesAndCycles()
            throws Exception {
        ProtosSemanticTransferValue shared = PORTABLE.mint("a+b");
        ProtosSemanticTransferValue twin = PORTABLE.mint("a+b");
        ProtosObjectValue graph = surroundingGraph(shared, twin);
        ProtosActivation activation = prelude.newModuleActivation();
        IdentityHashMap<Object, Object> memo = new IdentityHashMap<>();
        int reconstructed = PORTABLE.reconstructed;

        Object first = parallelCopy.invoke(null, shared, activation, memo);
        Object second = parallelCopy.invoke(null, graph, activation, memo);

        assertEquals(reconstructed + 2, PORTABLE.reconstructed);
        assertRematerializedGraph(shared, twin, graph, first, second);
    }

    @Test
    void ordinaryObjectCannotForgeFamilyThroughNamesSlotsShapeOrDelegation() throws Exception {
        ProtosSemanticTransferValue genuine = PORTABLE.mint("x");
        ProtosObjectValue forged = new ProtosObjectValue(ProtosObjectValue.rootObject());
        forged.createLocalSlot("text", new ProtosStringValue("x"));
        forged.createLocalSlot("ownerModule", new ProtosStringValue("std:plat051-fixture/Portable"));
        forged.createLocalSlot("family", new ProtosStringValue("ProtosSemanticTransferFamily"));
        forged.freeze();
        ProtosObjectValue child = new ProtosObjectValue(genuine);
        child.freeze();
        int extracted = PORTABLE.extracted;

        Object actorForged =
                ProtosActorValueTransfer.snapshotValue(forged, prelude.newModuleActivation());
        Object pForged =
                parallelCopy.invoke(
                        null, forged, prelude.newModuleActivation(), new IdentityHashMap<>());
        assertFalse(actorForged instanceof ProtosSemanticTransferValue);
        assertFalse(pForged instanceof ProtosSemanticTransferValue);
        assertEquals(extracted, PORTABLE.extracted);

        // Delegating to a genuine value confers nothing: the child stays ordinary while its
        // parent edge reaches the rematerialized genuine value.
        ProtosObjectValue actorChild =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosActorValueTransfer.snapshotValue(child, prelude.newModuleActivation()));
        assertFalse(actorChild instanceof ProtosSemanticTransferValue);
        assertNotSame(genuine, actorChild.parent().orElseThrow());
        assertInstanceOf(ProtosSemanticTransferValue.class, actorChild.parent().orElseThrow());
    }

    @Test
    void unauthorizedDescriptorAndMissingAuthorityFailClosedBeforeExtraction() throws Exception {
        ProtosSemanticTransferValue impostor = IMPOSTOR.mint("x");
        assertNonTransferable(impostor, prelude);
        assertNonParallel(impostor, prelude);
        assertEquals(0, IMPOSTOR.extracted);

        ProtosPrelude withoutFamilies = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosSemanticTransferValue genuine = PORTABLE.mint("x");
        int extracted = PORTABLE.extracted;
        assertNonTransferable(genuine, withoutFamilies);
        assertNonParallel(genuine, withoutFamilies);
        assertEquals(extracted, PORTABLE.extracted);
    }

    @Test
    void malformedPayloadsAndInvalidReconstructionsFailClosed() throws Exception {
        for (FixtureFamily family :
                List.of(NULL_PAYLOAD, MALFORMED_PAYLOAD, CLOSURE_PAYLOAD, THROWING, OPEN_RESULT, FOREIGN_RESULT)) {
            ProtosSemanticTransferValue value = family.mint("x");
            assertNonTransferable(value, prelude);
            assertNonParallel(value, prelude);
        }
        ProtosSemanticTransferValue open = PORTABLE.mintOpen("x");
        assertNonTransferable(open, prelude);
        assertNonParallel(open, prelude);

        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosSemanticTransferPayload.of(new ProtosStringValue("protos value")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ProtosSemanticTransferPayload.of(
                                ProtosClosureValue.nativeClosure((a, args) -> ProtosNullValue.INSTANCE)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixtureFamily("user:plat051-fixture/NotStandard", Mode.PORTABLE));
    }

    @Test
    void closureExecutionAndResourceRulesAreUnchanged() throws Exception {
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosClosureValue closure = ProtosClosureValue.nativeClosure((a, args) -> ProtosNullValue.INSTANCE);
        ProtosObjectValue holder = new ProtosObjectValue(ProtosObjectValue.rootObject());
        holder.createLocalSlot("callback", closure);

        assertNonTransferable(closure, prelude);
        assertNonTransferable(holder, prelude);
        assertNonTransferable(activation, prelude);
        assertNonTransferable(prelude.newExecutionContext(), prelude);
        assertNonParallel(prelude.newExecutionContext(), prelude);

        // P keeps its existing projection rule for Closures; it does not become rematerialization.
        Object projected = parallelCopy.invoke(null, closure, activation, new IdentityHashMap<>());
        assertInstanceOf(ProtosClosureValue.class, projected);
        assertNotSame(closure, projected);
    }

    @Test
    void ordinaryGraphTransferNeitherConsultsFamiliesNorImportsModules() throws Exception {
        ProtosObjectValue node = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosArrayValue array = prelude.newArray(List.of(node, node, new ProtosIntegerValue(BigInteger.TWO)));
        node.createLocalSlot("self", node);
        node.createLocalSlot("items", array);
        ProtosActivation activation = prelude.newModuleActivation();
        int extracted = PORTABLE.extracted;
        int reconstructed = PORTABLE.reconstructed;

        ProtosObjectValue actorNode =
                assertInstanceOf(
                        ProtosObjectValue.class, ProtosActorValueTransfer.snapshotValue(node, activation));
        ProtosObjectValue pNode =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        parallelCopy.invoke(null, node, activation, new IdentityHashMap<>()));

        for (ProtosObjectValue copy : List.of(actorNode, pNode)) {
            assertNotSame(node, copy);
            assertEquals(ProtosObjectValue.class, copy.getClass());
            assertSame(copy, copy.readLocalSlot("self").orElseThrow());
            ProtosArrayValue items =
                    assertInstanceOf(ProtosArrayValue.class, copy.readLocalSlot("items").orElseThrow());
            assertSame(copy, items.indexedSnapshot().get(0));
            assertSame(copy, items.indexedSnapshot().get(1));
        }
        assertEquals(extracted, PORTABLE.extracted);
        assertEquals(reconstructed, PORTABLE.reconstructed);
        assertTrue(
                activation.actorModuleState().lookup(PORTABLE.ownerModule()).isEmpty(),
                "recognizing values must not import or initialize any module");
    }

    @Test
    void genericMechanismHasNoRegexBranchAndUsesNoDynamicHostResolution() throws Exception {
        List<String> generic =
                List.of(
                        "runtime/ProtosSemanticTransferFamily.java",
                        "runtime/ProtosSemanticTransferValue.java",
                        "runtime/ProtosSemanticTransferPayload.java",
                        "runtime/ProtosActorValueTransfer.java",
                        "runtime/ProtosPrelude.java",
                        "execution/ProtosParallelRuntime.java");
        for (String relative : generic) {
            String source = Files.readString(MAIN.resolve(relative));
            assertFalse(source.toLowerCase(Locale.ROOT).contains("regex"), relative);
        }
        for (String relative : generic.subList(0, 3)) {
            String source = Files.readString(MAIN.resolve(relative));
            for (String forbidden :
                    List.of(
                            "java.lang.reflect",
                            "Class.forName",
                            "ServiceLoader",
                            "Serializable",
                            "readResolve",
                            "MethodHandle",
                            "ConcurrentHashMap")) {
                assertFalse(source.contains(forbidden), relative + " uses " + forbidden);
            }
        }
    }

    /** Graph: slots shared/again alias one value, twin is distinct with an equal payload, self is a cycle. */
    private static ProtosObjectValue surroundingGraph(
            ProtosSemanticTransferValue shared, ProtosSemanticTransferValue twin) {
        ProtosObjectValue graph = new ProtosObjectValue(ProtosObjectValue.rootObject());
        graph.createLocalSlot("shared", shared);
        graph.createLocalSlot("again", shared);
        graph.createLocalSlot("twin", twin);
        graph.createLocalSlot("self", graph);
        return graph;
    }

    private static void assertRematerializedGraph(
            ProtosSemanticTransferValue shared,
            ProtosSemanticTransferValue twin,
            ProtosObjectValue graph,
            Object copiedShared,
            Object copiedGraph) {
        ProtosObjectValue destination = assertInstanceOf(ProtosObjectValue.class, copiedGraph);
        ProtosSemanticTransferValue rebuilt =
                assertInstanceOf(ProtosSemanticTransferValue.class, copiedShared);
        Object again = destination.readLocalSlot("again").orElseThrow();
        Object rebuiltTwin = destination.readLocalSlot("twin").orElseThrow();

        assertNotSame(graph, destination);
        assertSame(destination, destination.readLocalSlot("self").orElseThrow());
        assertSame(rebuilt, destination.readLocalSlot("shared").orElseThrow());
        assertSame(rebuilt, again);
        assertNotSame(shared, rebuilt);
        assertNotSame(twin, rebuiltTwin);
        assertNotSame(rebuilt, rebuiltTwin);
        assertSame(PORTABLE, rebuilt.family());
        assertTrue(rebuilt.isFrozen());
        assertEquals("a+b", ((ProtosStringValue) rebuilt.readLocalSlot("text").orElseThrow()).value());
        // The callable surface was built by the destination-local reconstructor, not copied.
        assertNotSame(
                shared.readLocalSlot("describe").orElseThrow(),
                rebuilt.readLocalSlot("describe").orElseThrow());
    }

    private static void assertNonTransferable(Object value, ProtosPrelude owner) {
        ProtosSignalException failure =
                assertThrows(
                        ProtosSignalException.class,
                        () -> ProtosActorValueTransfer.snapshotValue(value, owner.newModuleActivation()));
        assertSame(
                owner.bindings().readLocalSlot("NonTransferableValue").orElseThrow(),
                failure.error().parent().orElseThrow());
    }

    private static void assertNonParallel(Object value, ProtosPrelude owner) {
        InvocationTargetException failure =
                assertThrows(
                        InvocationTargetException.class,
                        () ->
                                parallelCopy.invoke(
                                        null,
                                        value,
                                        owner.newModuleActivation(),
                                        new IdentityHashMap<Object, Object>()));
        assertNotNull(failure.getCause());
        assertEquals("NonParallel", failure.getCause().getClass().getSimpleName());
    }

    private enum Mode {
        PORTABLE,
        NULL_PAYLOAD,
        MALFORMED_PAYLOAD,
        CLOSURE_PAYLOAD,
        THROWING,
        OPEN_RESULT,
        FOREIGN_RESULT
    }

    /** Test-only family: payload is one String; values carry a native Closure slot. */
    private static final class FixtureFamily extends ProtosSemanticTransferFamily {
        private final Mode mode;
        int extracted;
        int reconstructed;

        FixtureFamily(String ownerModule, Mode mode) {
            super(new ProtosModuleKey(ownerModule));
            this.mode = mode;
        }

        ProtosSemanticTransferValue mint(String text) {
            ProtosSemanticTransferValue value = mintOpen(text);
            value.freeze();
            return value;
        }

        ProtosSemanticTransferValue mintOpen(String text) {
            ProtosSemanticTransferValue value = newValue(ProtosObjectValue.rootObject());
            value.createLocalSlot("text", new ProtosStringValue(text));
            value.createLocalSlot(
                    "describe",
                    ProtosClosureValue.nativeClosure((a, args) -> new ProtosStringValue(text)));
            return value;
        }

        @Override
        protected ProtosSemanticTransferPayload extract(ProtosSemanticTransferValue value) {
            extracted++;
            Object text = value.readLocalSlot("text").orElse(null);
            return switch (mode) {
                case NULL_PAYLOAD -> null;
                case MALFORMED_PAYLOAD -> ProtosSemanticTransferPayload.of(BigInteger.ONE);
                case CLOSURE_PAYLOAD -> ProtosSemanticTransferPayload.of(value.readLocalSlot("describe").orElseThrow());
                case THROWING -> throw new IllegalStateException("extraction failure");
                default -> ProtosSemanticTransferPayload.of(((ProtosStringValue) text).value());
            };
        }

        @Override
        protected ProtosSemanticTransferValue reconstruct(
                ProtosSemanticTransferPayload payload, ProtosPrelude destination) {
            reconstructed++;
            if (payload.size() != 1 || !(payload.get(0) instanceof String text)) {
                return null;
            }
            return switch (mode) {
                case OPEN_RESULT -> mintOpen(text);
                case FOREIGN_RESULT -> IMPOSTOR.mint(text);
                default -> mint(text);
            };
        }
    }
}

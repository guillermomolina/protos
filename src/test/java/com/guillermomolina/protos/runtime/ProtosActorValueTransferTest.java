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
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosInvocation;
import com.guillermomolina.protos.execution.ProtosStandardTcpListenerProtocol;
import com.guillermomolina.protos.runtime.io.ProtosPathComponents;
import com.guillermomolina.protos.runtime.io.ProtosTcpAcceptorValue;
import com.guillermomolina.protos.runtime.io.ProtosTcpListenerFlow;
import com.guillermomolina.protos.runtime.io.ProtosTcpListenerValue;
import com.guillermomolina.protos.runtime.numeric.ProtosNumericHashKey;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ProtosActorValueTransferTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void scalarValuesCopyWhileCanonicalSingletonsRemainCanonical() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosIntegerValue integer = new ProtosIntegerValue(42);
        ProtosStringValue string = new ProtosStringValue("snapshot");

        List<Object> result =
                ProtosActorValueTransfer.snapshotArguments(
                        List.of(integer, string, ProtosBooleanValue.TRUE, ProtosNullValue.INSTANCE),
                        source);

        // I094: a transfer without Path or other semantic records keeps the unwrapped fast path.
        assertFalse(ProtosActorValueTransfer.requiresMaterialization(result));
        assertSame(
                result,
                ProtosActorValueTransfer.materializeArguments(result, prelude.newModuleActivation()));
        assertNotSame(integer, result.get(0));
        assertTrue(ProtosIdentity.identical(integer, result.get(0)));
        assertNotSame(string, result.get(1));
        assertTrue(ProtosIdentity.identical(string, result.get(1)));
        assertSame(ProtosBooleanValue.TRUE, result.get(2));
        assertSame(ProtosNullValue.INSTANCE, result.get(3));
    }

    @Test
    void pathSnapshotIsInertAndMaterializesWithTheDestinationPreludePrototype() throws Exception {
        ProtosPrelude sourcePrelude = core();
        ProtosPrelude destinationPrelude = core();
        ProtosActivation source = sourcePrelude.newModuleActivation();
        ProtosPathValue path = new ProtosPathValue(sourcePrelude.pathPrototype(), List.of("a", "b"));
        ProtosPathValue twin = new ProtosPathValue(sourcePrelude.pathPrototype(), List.of("a", "b"));

        List<Object> snapshot =
                ProtosActorValueTransfer.snapshotArguments(List.of(path, path, twin), source);

        assertTrue(ProtosActorValueTransfer.requiresMaterialization(snapshot));
        assertEquals(1, snapshot.size());
        assertFalse(snapshot.get(0) instanceof ProtosPathValue, "the snapshot carries no guest Path");

        List<?> delivered =
                ProtosActorValueTransfer.materializeArguments(
                        snapshot, destinationPrelude.newModuleActivation());

        ProtosPathValue copied = assertInstanceOf(ProtosPathValue.class, delivered.get(0));
        ProtosPathValue copiedTwin = assertInstanceOf(ProtosPathValue.class, delivered.get(2));
        assertNotSame(path, copied);
        assertSame(copied, delivered.get(1), "one source Path stays one destination Path");
        assertNotSame(copied, copiedTwin, "equal but distinct Paths stay distinct");
        assertEquals(List.of("a", "b"), copied.components());
        assertEquals(List.of("a", "b"), copiedTwin.components());
        assertSame(
                destinationPrelude.pathPrototype(),
                copied.representedDelegationParent(destinationPrelude));
        assertNotSame(sourcePrelude.pathPrototype(), destinationPrelude.pathPrototype());
        assertThrows(UnsupportedOperationException.class, () -> copied.components().add("c"));

        // Every delivery of one logical snapshot gets its own identities.
        List<?> again =
                ProtosActorValueTransfer.materializeArguments(
                        snapshot, destinationPrelude.newModuleActivation());
        assertNotSame(copied, again.get(0));
        assertSame(again.get(0), again.get(1));
    }

    @Test
    void singlePathValueSnapshotMaterializesInTheDestination() throws Exception {
        ProtosPrelude sourcePrelude = core();
        ProtosPrelude destinationPrelude = core();
        ProtosPathValue path = new ProtosPathValue(sourcePrelude.pathPrototype(), List.of("x"));

        Object snapshot =
                ProtosActorValueTransfer.snapshotValue(path, sourcePrelude.newModuleActivation());
        assertFalse(snapshot instanceof ProtosPathValue);

        ProtosPathValue copied =
                assertInstanceOf(
                        ProtosPathValue.class,
                        ProtosActorValueTransfer.materializeValue(
                                snapshot, destinationPrelude.newModuleActivation()));
        assertEquals(List.of("x"), copied.components());
        assertSame(
                destinationPrelude.pathPrototype(),
                copied.representedDelegationParent(destinationPrelude));
    }

    @Test
    void pathsNestedInArrayMapIdentityMapAndObjectRematerializeWithAliases() throws Exception {
        ProtosPrelude prelude = core();
        ProtosPathValue shared = new ProtosPathValue(prelude.pathPrototype(), List.of("shared"));
        ProtosPathValue twin = new ProtosPathValue(prelude.pathPrototype(), List.of("shared"));
        ProtosArrayValue array = prelude.newArray(List.of(shared, twin, shared));
        ProtosMapValue map = prelude.newMap();
        ProtosNumericHashKey mapHash = ProtosNumericHashKey.fromIdentity(shared);
        map.append(shared, mapHash, shared);
        ProtosIdentityMapValue identityMap =
                new ProtosIdentityMapValue(prelude.identityMapPrototype());
        identityMap.append(shared, ProtosNumericHashKey.fromIdentity(shared), twin);
        ProtosObjectValue holder = new ProtosObjectValue(ProtosObjectValue.rootObject());
        holder.createLocalSlot("path", shared);
        holder.createLocalSlot("array", array);
        holder.createLocalSlot("map", map);
        holder.createLocalSlot("identityMap", identityMap);

        List<Object> snapshot =
                ProtosActorValueTransfer.snapshotArguments(
                        List.of(holder, shared), prelude.newModuleActivation());
        assertTrue(ProtosActorValueTransfer.requiresMaterialization(snapshot));
        List<?> delivered =
                ProtosActorValueTransfer.materializeArguments(snapshot, prelude.newModuleActivation());

        ProtosObjectValue copiedHolder = assertInstanceOf(ProtosObjectValue.class, delivered.get(0));
        ProtosPathValue copied = assertInstanceOf(ProtosPathValue.class, delivered.get(1));
        assertNotSame(shared, copied);
        assertSame(copied, copiedHolder.readLocalSlot("path").orElseThrow());

        ProtosArrayValue copiedArray =
                assertInstanceOf(ProtosArrayValue.class, copiedHolder.readLocalSlot("array").orElseThrow());
        assertSame(copied, copiedArray.indexedAt(0));
        assertSame(copied, copiedArray.indexedAt(2));
        ProtosPathValue copiedTwin = assertInstanceOf(ProtosPathValue.class, copiedArray.indexedAt(1));
        assertNotSame(copied, copiedTwin);
        assertNotSame(twin, copiedTwin);

        ProtosMapValue.Entry mapEntry =
                assertInstanceOf(ProtosMapValue.class, copiedHolder.readLocalSlot("map").orElseThrow())
                        .keyedSnapshot()
                        .get(0);
        assertSame(copied, mapEntry.key());
        assertSame(copied, mapEntry.value());
        assertEquals(mapHash, mapEntry.recordedHash(), "Path structural hash is recorded, not rebuilt");

        ProtosIdentityMapValue.Entry identityEntry =
                assertInstanceOf(
                                ProtosIdentityMapValue.class,
                                copiedHolder.readLocalSlot("identityMap").orElseThrow())
                        .keyedSnapshot()
                        .get(0);
        assertSame(copied, identityEntry.key());
        assertSame(copiedTwin, identityEntry.value());
        for (Object value : List.of(copied, copiedTwin)) {
            assertSame(
                    prelude.pathPrototype(),
                    ((ProtosPathValue) value).representedDelegationParent(prelude));
        }
    }

    @Test
    void pathReplyIsMaterializedInTheRequesterDomainBeforeResolution() throws Exception {
        ProtosPrelude workerPrelude = core();
        ProtosPrelude requesterPrelude = core();
        ProtosActivation requester = requesterPrelude.newModuleActivation();
        ProtosPathValue path = new ProtosPathValue(workerPrelude.pathPrototype(), List.of("reply"));
        Object replySnapshot =
                ProtosActorValueTransfer.snapshotValue(path, workerPrelude.newModuleActivation());
        ProtosFutureValue future =
                new ProtosFutureValue(requesterPrelude.futurePrototype(), requester.executionDomain());

        ProtosActorValueTransfer.resolveRequesterFuture(future, replySnapshot, requester);

        assertEquals(ProtosFutureValue.State.PENDING, future.state());
        assertEquals(1, requester.executionDomain().runnableCount());
        while (requester.executionDomain().dispatchOne()) {
            // Requester-domain materialization.
        }

        ProtosPathValue reply =
                assertInstanceOf(ProtosPathValue.class, future.resolvedValue().orElseThrow());
        assertNotSame(path, reply);
        assertEquals(List.of("reply"), reply.components());
        assertSame(
                requesterPrelude.pathPrototype(),
                reply.representedDelegationParent(requesterPrelude));
    }

    @Test
    void pathComponentsDescriptorOwnsAnImmutableCopy() {
        ArrayList<String> components = new ArrayList<>(List.of("a", "b"));
        ProtosPathComponents descriptor = new ProtosPathComponents(components);
        components.add("c");

        assertEquals(List.of("a", "b"), descriptor.components());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.components().add("d"));
    }

    @Test
    void ordinaryGraphCopyPreservesCyclesAliasingParentsAndState() throws Exception {
        ProtosActivation sourceActivation = core().newModuleActivation();
        ProtosObjectValue parent = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue root = new ProtosObjectValue(parent);
        ProtosObjectValue shared = new ProtosObjectValue(ProtosObjectValue.rootObject());
        root.createLocalSlot("left", shared);
        root.createLocalSlot("right", shared);
        shared.createLocalSlot("back", root);
        parent.createLocalSlot("child", root);
        root.close();

        ProtosObjectValue copied =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        ProtosActorValueTransfer.snapshotValue(root, sourceActivation));
        ProtosObjectValue copiedParent =
                assertInstanceOf(ProtosObjectValue.class, copied.parent().orElseThrow());
        ProtosObjectValue copiedShared =
                assertInstanceOf(ProtosObjectValue.class, copied.readLocalSlot("left").orElseThrow());

        assertNotSame(root, copied);
        assertNotSame(parent, copiedParent);
        assertNotSame(shared, copiedShared);
        assertSame(copiedShared, copied.readLocalSlot("right").orElseThrow());
        assertSame(copied, copiedShared.readLocalSlot("back").orElseThrow());
        assertSame(copied, copiedParent.readLocalSlot("child").orElseThrow());
        assertTrue(copied.isClosed());
        assertTrue(root.isClosed(), "snapshot must not mutate source state");
    }

    @Test
    void aliasesAndCyclesArePreservedAcrossArgumentRootsAndArrayState() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosArrayValue array = prelude.newArray(List.of(ProtosNullValue.INSTANCE));
        array.indexedPut(0, array);
        array.createLocalSlot("self", array);

        List<Object> result =
                ProtosActorValueTransfer.snapshotArguments(List.of(array, array), source);
        ProtosArrayValue copied = assertInstanceOf(ProtosArrayValue.class, result.get(0));

        assertSame(copied, result.get(1));
        assertSame(copied, copied.indexedAt(0));
        assertSame(copied, copied.readLocalSlot("self").orElseThrow());
        assertNotSame(array, copied);
    }

    @Test
    void bytesSnapshotOwnsIndependentIndexedAndLocalState() throws Exception {
        ProtosActivation source = core().newModuleActivation();
        ProtosBytesValue bytes = new ProtosBytesValue(ProtosObjectValue.rootObject());
        bytes.indexedAdd(new ProtosIntegerValue(1L));
        bytes.indexedAdd(new ProtosIntegerValue(2L));
        ProtosObjectValue metadata = new ProtosObjectValue(ProtosObjectValue.rootObject());
        bytes.createLocalSlot("metadata", metadata);

        ProtosBytesValue copied =
                assertInstanceOf(
                        ProtosBytesValue.class,
                        ProtosActorValueTransfer.snapshotValue(bytes, source));
        bytes.indexedPut(0, new ProtosIntegerValue(9));

        assertNotSame(bytes, copied);
        assertEquals(String.valueOf("1"), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(copied.indexedAt(0)));
        assertNotSame(metadata, copied.readLocalSlot("metadata").orElseThrow());
    }

    @Test
    void actorRefRematerializesWithoutChangingSemanticIdentityOrTarget() throws Exception {
        ProtosActivation source = core().newModuleActivation();
        ProtosObjectValue actorRefPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
        ProtosActor actor = new ProtosActor(actorRefPrototype);
        ProtosActorRefValue original = actor.reference();

        ProtosActorRefValue copied =
                assertInstanceOf(
                        ProtosActorRefValue.class,
                        ProtosActorValueTransfer.snapshotValue(original, source));

        assertNotSame(original, copied);
        assertTrue(ProtosIdentity.identical(original, copied));
        assertEquals(ProtosIdentity.identityHash(original), ProtosIdentity.identityHash(copied));
        assertSame(actor, copied.localActorForRuntime());

        actor.beginTermination();
        actor.markTerminated();
        assertTrue(ProtosIdentity.identical(original, copied));
        assertSame(actor, copied.localActorForRuntime());
    }

    @Test
    void completeArgumentSnapshotFailureLeavesSourceGraphUntouched() throws Exception {
        ProtosActivation sourceActivation = core().newModuleActivation();
        ProtosObjectValue safe = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue source = new ProtosObjectValue(ProtosObjectValue.rootObject());
        source.createLocalSlot("safe", safe);
        source.createLocalSlot(
                "bad",
                ProtosClosureValue.nativeClosure(
                        (activation, arguments) -> ProtosNullValue.INSTANCE));

        assertNonTransferable(
                sourceActivation,
                () -> ProtosActorValueTransfer.snapshotArguments(List.of(safe, source), sourceActivation));

        assertSame(safe, source.readLocalSlot("safe").orElseThrow());
        assertEquals(2, source.localSlotsSnapshot().size());
        assertTrue(source.isOpen());
        assertTrue(safe.isOpen());
    }

    @Test
    void executionAndResourceValuesAreRejectedWithStandardNonTransferableValue() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();

        assertNonTransferable(
                source,
                () ->
                        ProtosActorValueTransfer.snapshotValue(
                                ProtosClosureValue.nativeClosure(
                                        (activation, arguments) -> ProtosNullValue.INSTANCE),
                                source));
        assertNonTransferable(
                source,
                () ->
                        ProtosActorValueTransfer.snapshotValue(
                                new ProtosFutureValue(prelude.futurePrototype(), source.executionDomain()),
                                source));
        assertNonTransferable(
                source, () -> ProtosActorValueTransfer.snapshotValue(source.context(), source));
        assertNonTransferable(
                source, () -> ProtosActorValueTransfer.snapshotValue(new Object(), source));
    }

    @Test
    void mapTransferPreservesKeyedStateCyclesAliasesAndDefaultHashBookkeeping() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosMapValue map = prelude.newMap();
        ProtosObjectValue key = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue shared = new ProtosObjectValue(ProtosObjectValue.rootObject());
        shared.createLocalSlot("owner", map);
        map.createLocalSlot("alias", shared);

        ProtosInvocation.invokeMessage(map, "atPut", List.of(key, shared), source);
        ProtosInvocation.invokeMessage(
                map, "atPut", List.of(new ProtosStringValue("self"), map), source);
        map.close();

        ProtosMapValue copied =
                assertInstanceOf(
                        ProtosMapValue.class,
                        ProtosActorValueTransfer.snapshotValue(map, source));
        List<ProtosMapValue.Entry> entries = copied.keyedSnapshot();
        ProtosObjectValue copiedKey =
                assertInstanceOf(ProtosObjectValue.class, entries.get(0).key());
        ProtosObjectValue copiedShared =
                assertInstanceOf(ProtosObjectValue.class, entries.get(0).value());

        assertNotSame(map, copied);
        assertEquals(2, copied.keyedSize());
        assertTrue(copied.isClosed());
        assertSame(copiedShared, copied.readLocalSlot("alias").orElseThrow());
        assertSame(copied, copiedShared.readLocalSlot("owner").orElseThrow());
        assertSame(copied, entries.get(1).value());
        assertEquals(ProtosNumericHashKey.fromIdentity(copiedKey), entries.get(0).recordedHash());
        assertSame(
                copiedShared,
                ProtosInvocation.invokeMessage(copied, "at", List.of(copiedKey), source));
    }

    @Test
    void identityMapTransferRebuildsCopiedIdentityHashesAndPreservesCycles() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosIdentityMapValue map = new ProtosIdentityMapValue(prelude.identityMapPrototype());
        ProtosObjectValue first = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue second = new ProtosObjectValue(ProtosObjectValue.rootObject());

        ProtosInvocation.invokeMessage(map, "atPut", List.of(first, map), source);
        ProtosInvocation.invokeMessage(map, "atPut", List.of(second, first), source);

        ProtosIdentityMapValue copied =
                assertInstanceOf(
                        ProtosIdentityMapValue.class,
                        ProtosActorValueTransfer.snapshotValue(map, source));
        List<ProtosIdentityMapValue.Entry> entries = copied.keyedSnapshot();
        Object copiedFirst = entries.get(0).key();
        Object copiedSecond = entries.get(1).key();

        assertNotSame(first, copiedFirst);
        assertNotSame(second, copiedSecond);
        assertFalse(ProtosIdentity.identical(copiedFirst, copiedSecond));
        assertEquals(
                ProtosNumericHashKey.fromIdentity(copiedFirst),
                entries.get(0).recordedIdentityHash());
        assertEquals(
                ProtosNumericHashKey.fromIdentity(copiedSecond),
                entries.get(1).recordedIdentityHash());
        assertSame(copied, entries.get(0).value());
        assertSame(copiedFirst, entries.get(1).value());
        assertSame(
                copied,
                ProtosInvocation.invokeMessage(copied, "at", List.of(copiedFirst), source));
    }

    @Test
    void transferredActorRefRemainsAValidMapAndIdentityMapKey() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosObjectValue actorRefPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
        ProtosActor actor = new ProtosActor(actorRefPrototype);
        ProtosActorRefValue reference = actor.reference();
        ProtosMapValue map = prelude.newMap();
        ProtosIdentityMapValue identityMap =
                new ProtosIdentityMapValue(prelude.identityMapPrototype());
        ProtosStringValue value = new ProtosStringValue("capability");

        ProtosInvocation.invokeMessage(map, "atPut", List.of(reference, value), source);
        ProtosInvocation.invokeMessage(identityMap, "atPut", List.of(reference, value), source);

        ProtosMapValue copiedMap =
                assertInstanceOf(
                        ProtosMapValue.class,
                        ProtosActorValueTransfer.snapshotValue(map, source));
        ProtosIdentityMapValue copiedIdentityMap =
                assertInstanceOf(
                        ProtosIdentityMapValue.class,
                        ProtosActorValueTransfer.snapshotValue(identityMap, source));
        ProtosActorRefValue query =
                assertInstanceOf(
                        ProtosActorRefValue.class,
                        ProtosActorValueTransfer.snapshotValue(reference, source));

        assertTrue(
                ProtosIdentity.identical(
                        copiedMap.keyedSnapshot().get(0).key(), query));
        assertEquals(
                "capability",
                ((ProtosStringValue)
                                ProtosInvocation.invokeMessage(
                                        copiedMap, "at", List.of(query), source))
                        .value());
        assertEquals(
                "capability",
                ((ProtosStringValue)
                                ProtosInvocation.invokeMessage(
                                        copiedIdentityMap, "at", List.of(query), source))
                        .value());
    }

    @Test
    void keyedCollectionNestedNonTransferableStateStillFailsAtomically() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosMapValue map = prelude.newMap();
        ProtosIdentityMapValue identityMap =
                new ProtosIdentityMapValue(prelude.identityMapPrototype());
        ProtosClosureValue closure =
                ProtosClosureValue.nativeClosure(
                        (activation, arguments) -> ProtosNullValue.INSTANCE);

        ProtosInvocation.invokeMessage(
                map, "atPut", List.of(new ProtosStringValue("bad"), closure), source);
        ProtosInvocation.invokeMessage(
                identityMap, "atPut", List.of(closure, new ProtosStringValue("bad")), source);

        assertNonTransferable(source, () -> ProtosActorValueTransfer.snapshotValue(map, source));
        assertNonTransferable(
                source, () -> ProtosActorValueTransfer.snapshotValue(identityMap, source));
        assertEquals(1, map.keyedSize());
        assertEquals(1, identityMap.keyedSize());
        assertSame(closure, map.keyedSnapshot().get(0).value());
        assertSame(closure, identityMap.keyedSnapshot().get(0).key());
    }

    @Test
    void groupRefRematerializationPreservesSemanticIdentityWithoutCollapsingAcquisitions()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosObjectValue prototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
        UUID groupIdentity = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID restrictionIdentity = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ProtosGroupRefValue original =
                ProtosGroupRefValue.acquireForRuntime(
                        prototype, groupIdentity, restrictionIdentity);
        ProtosGroupRefValue independent =
                ProtosGroupRefValue.acquireForRuntime(
                        prototype, groupIdentity, restrictionIdentity);

        ProtosGroupRefValue first =
                assertInstanceOf(
                        ProtosGroupRefValue.class,
                        ProtosActorValueTransfer.snapshotValue(original, source));
        ProtosGroupRefValue second =
                assertInstanceOf(
                        ProtosGroupRefValue.class,
                        ProtosActorValueTransfer.snapshotValue(first, source));

        assertNotSame(original, first);
        assertNotSame(first, second);
        assertTrue(ProtosIdentity.identical(original, first));
        assertTrue(ProtosIdentity.identical(first, second));
        assertFalse(ProtosIdentity.identical(original, independent));
        assertEquals(ProtosIdentity.identityHash(original), ProtosIdentity.identityHash(first));
        assertEquals(ProtosIdentity.identityHash(first), ProtosIdentity.identityHash(second));
        assertEquals(groupIdentity, first.groupIdentityForRuntime());
        assertEquals(restrictionIdentity, first.restrictionIdentityForRuntime());
        assertSame(prototype, first.representedDelegationParent(prelude));
    }

    @Test
    void transferredGroupRefRemainsAValidMapAndIdentityMapKey() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosObjectValue prototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
        ProtosGroupRefValue reference =
                ProtosGroupRefValue.acquireForRuntime(
                        prototype,
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        UUID.fromString("44444444-4444-4444-4444-444444444444"));
        ProtosMapValue map = prelude.newMap();
        ProtosIdentityMapValue identityMap =
                new ProtosIdentityMapValue(prelude.identityMapPrototype());
        ProtosStringValue value = new ProtosStringValue("group-capability");

        ProtosInvocation.invokeMessage(map, "atPut", List.of(reference, value), source);
        ProtosInvocation.invokeMessage(identityMap, "atPut", List.of(reference, value), source);

        ProtosMapValue copiedMap =
                assertInstanceOf(
                        ProtosMapValue.class,
                        ProtosActorValueTransfer.snapshotValue(map, source));
        ProtosIdentityMapValue copiedIdentityMap =
                assertInstanceOf(
                        ProtosIdentityMapValue.class,
                        ProtosActorValueTransfer.snapshotValue(identityMap, source));
        ProtosGroupRefValue query =
                assertInstanceOf(
                        ProtosGroupRefValue.class,
                        ProtosActorValueTransfer.snapshotValue(reference, source));

        assertTrue(ProtosIdentity.identical(copiedMap.keyedSnapshot().get(0).key(), query));
        assertEquals(
                "group-capability",
                ((ProtosStringValue)
                                ProtosInvocation.invokeMessage(
                                        copiedMap, "at", List.of(query), source))
                        .value());
        assertEquals(
                "group-capability",
                ((ProtosStringValue)
                                ProtosInvocation.invokeMessage(
                                        copiedIdentityMap, "at", List.of(query), source))
                        .value());
    }

    @Test
    void distinctPhysicalGroupRefWrappersWithOneSemanticIdentityRemainIdenticalAcrossOneSnapshot()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosObjectValue prototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
        ProtosGroupRefValue original =
                ProtosGroupRefValue.acquireForRuntime(
                        prototype,
                        UUID.fromString("55555555-5555-5555-5555-555555555555"),
                        UUID.fromString("66666666-6666-6666-6666-666666666666"));
        ProtosGroupRefValue otherWrapper = original.rematerializeForActorTransfer();

        List<Object> copied =
                ProtosActorValueTransfer.snapshotArguments(
                        List.of(original, otherWrapper), source);
        ProtosGroupRefValue first = assertInstanceOf(ProtosGroupRefValue.class, copied.get(0));
        ProtosGroupRefValue second = assertInstanceOf(ProtosGroupRefValue.class, copied.get(1));

        assertNotSame(first, second);
        assertTrue(ProtosIdentity.identical(first, second));
        assertEquals(ProtosIdentity.identityHash(first), ProtosIdentity.identityHash(second));
        assertEquals(first.groupIdentityForRuntime(), second.groupIdentityForRuntime());
        assertEquals(first.restrictionIdentityForRuntime(), second.restrictionIdentityForRuntime());
    }

    @Test
    void standardPreludeObjectsMayRemainPhysicallyShared() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();

        assertSame(
                ProtosObjectValue.rootObject(),
                ProtosActorValueTransfer.snapshotValue(ProtosObjectValue.rootObject(), source));
        assertSame(
                prelude.arrayPrototype(),
                ProtosActorValueTransfer.snapshotValue(prelude.arrayPrototype(), source));
        assertSame(
                prelude.bindings(),
                ProtosActorValueTransfer.snapshotValue(prelude.bindings(), source));
        assertTrue(ProtosObjectValue.rootObject().isFrozen());
        assertTrue(prelude.arrayPrototype().isFrozen());
        assertTrue(prelude.bindings().isFrozen());
    }

    @Test
    void largeIntegersRematerializeExactlyWithoutSlotCopyAndKeepSnapshotAliasing()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        BigInteger twoTo64 = BigInteger.ONE.shiftLeft(64);
        BigInteger huge = BigInteger.ONE.shiftLeft(1000).add(BigInteger.ONE);
        Object positive = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(twoTo64, prelude);
        Object negative = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(twoTo64.negate(), prelude);
        Object shared = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(huge, prelude);
        Object twin = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(huge, prelude);
        ProtosObjectValue nested = new ProtosObjectValue(ProtosObjectValue.rootObject());
        nested.createLocalSlot("shared", shared);
        nested.createLocalSlot("again", shared);
        nested.createLocalSlot("twin", twin);
        ProtosArrayValue array =
                prelude.newArray(List.of(positive, negative, shared, nested));

        List<Object> result =
                ProtosActorValueTransfer.snapshotArguments(List.of(array, shared), source);

        ProtosArrayValue copiedArray = assertInstanceOf(ProtosArrayValue.class, result.get(0));
        ProtosLargeIntegerValue copiedPositive =
                assertInstanceOf(ProtosLargeIntegerValue.class, copiedArray.indexedAt(0));
        ProtosLargeIntegerValue copiedNegative =
                assertInstanceOf(ProtosLargeIntegerValue.class, copiedArray.indexedAt(1));
        ProtosLargeIntegerValue copiedShared =
                assertInstanceOf(ProtosLargeIntegerValue.class, copiedArray.indexedAt(2));
        ProtosObjectValue copiedNested =
                assertInstanceOf(ProtosObjectValue.class, copiedArray.indexedAt(3));
        Object copiedTwin = copiedNested.readLocalSlot("twin").orElseThrow();

        assertNotSame(positive, copiedPositive);
        assertEquals(String.valueOf(String.valueOf(twoTo64)), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(copiedPositive));
        assertEquals(String.valueOf(twoTo64.negate()), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(copiedNegative));
        assertEquals(String.valueOf(String.valueOf(huge)), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(copiedShared));
        // Rematerialized whole: frozen, slotless, and owned by the BigInteger prototype.
        for (ProtosLargeIntegerValue copied :
                List.of(copiedPositive, copiedNegative, copiedShared)) {
            assertTrue(copied.isFrozen());
            assertSame(prelude.bigIntegerPrototype(), copied.parent().orElseThrow());
            ArrayList<String> names = new ArrayList<>();
            copied.appendLocalBindingsTo(names, new ArrayList<>());
            assertTrue(names.isEmpty());
        }
        // One source identity is one copy across roots and slots; a distinct twin stays distinct.
        assertSame(copiedShared, result.get(1));
        assertSame(copiedShared, copiedNested.readLocalSlot("shared").orElseThrow());
        assertSame(copiedShared, copiedNested.readLocalSlot("again").orElseThrow());
        assertNotSame(copiedShared, copiedTwin);
        assertTrue(ProtosIdentity.identical(copiedShared, copiedTwin));
        assertTrue(ProtosIdentity.identical(shared, copiedTwin));
        assertEquals(ProtosIdentity.identityHash(shared), ProtosIdentity.identityHash(copiedTwin));
    }

    @Test
    void largeIntegerMapAndIdentityMapKeysKeepExactRecordedHashesAcrossTransfer()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        BigInteger huge = BigInteger.ONE.shiftLeft(1000).add(BigInteger.ONE);
        Object key = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(huge, prelude);
        ProtosMapValue map = prelude.newMap();
        ProtosIdentityMapValue identityMap =
                new ProtosIdentityMapValue(prelude.identityMapPrototype());
        ProtosStringValue value = new ProtosStringValue("large");
        ProtosInvocation.invokeMessage(map, "atPut", List.of(key, value), source);
        ProtosInvocation.invokeMessage(identityMap, "atPut", List.of(key, value), source);
        ProtosNumericHashKey sourceHash = map.keyedSnapshot().get(0).recordedHash();

        List<Object> result =
                ProtosActorValueTransfer.snapshotArguments(List.of(map, identityMap), source);

        ProtosMapValue copiedMap = assertInstanceOf(ProtosMapValue.class, result.get(0));
        ProtosMapValue.Entry entry = copiedMap.keyedSnapshot().get(0);
        assertInstanceOf(ProtosLargeIntegerValue.class, entry.key());
        // Number.hash is an override, so the exact recorded hash is kept, never Object.hash.
        assertEquals(sourceHash, entry.recordedHash());
        assertNotEquals(ProtosNumericHashKey.fromIdentity(entry.key()), entry.recordedHash());

        ProtosIdentityMapValue copiedIdentityMap =
                assertInstanceOf(ProtosIdentityMapValue.class, result.get(1));
        ProtosIdentityMapValue.Entry identityEntry = copiedIdentityMap.keyedSnapshot().get(0);
        assertEquals(
                ProtosNumericHashKey.fromIdentity(key), identityEntry.recordedIdentityHash());
        assertEquals(
                ProtosNumericHashKey.fromIdentity(identityEntry.key()),
                identityEntry.recordedIdentityHash());

        Object probe = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(huge, prelude);
        ProtosStringValue fromMap =
                assertInstanceOf(
                        ProtosStringValue.class,
                        ProtosInvocation.invokeMessage(copiedMap, "at", List.of(probe), source));
        ProtosStringValue fromIdentityMap =
                assertInstanceOf(
                        ProtosStringValue.class,
                        ProtosInvocation.invokeMessage(
                                copiedIdentityMap, "at", List.of(probe), source));
        assertEquals("large", fromMap.value());
        assertEquals("large", fromIdentityMap.value());
    }

    @Test
    void hostNumericCarriersAndNonTransferableNeighboursOfLargeIntegersFailAtomically()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        Object large = com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerFromHost(BigInteger.ONE.shiftLeft(64), prelude);

        assertNonTransferable(
                source,
                () -> ProtosActorValueTransfer.snapshotValue(BigInteger.ONE.shiftLeft(64), source));
        assertNonTransferable(
                source, () -> ProtosActorValueTransfer.snapshotValue(Long.valueOf(7L), source));
        assertNonTransferable(
                source, () -> ProtosActorValueTransfer.snapshotValue(Double.valueOf(1.0d), source));

        ProtosArrayValue array =
                prelude.newArray(
                        List.of(large, ProtosClosureValue.nativeClosure((a, x) -> large)));
        assertNonTransferable(
                source, () -> ProtosActorValueTransfer.snapshotArguments(List.of(array), source));
        assertSame(large, array.indexedAt(0));
        assertFalse(array.isFrozen());
    }

    // LIB024: Vector and Matrix copy their private numeric payload with ordinary slots and state.
    @Test
    void linearAlgebraValuesCopyPayloadSlotsAndStateWhileSharingStandardPrototypes()
            throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosObjectValue vectorPrototype = linearAlgebraMember(prelude, "Vector");
        ProtosObjectValue matrixPrototype = linearAlgebraMember(prelude, "Matrix");
        Object big = ProtosNumericValueSupport.integerFromHost(BigInteger.ONE.shiftLeft(80), prelude);
        ProtosVectorValue vector =
                new ProtosVectorValue(
                        vectorPrototype,
                        List.of(new ProtosIntegerValue(1), big, new ProtosFloatValue(-0.0d)));
        ProtosMatrixValue matrix =
                new ProtosMatrixValue(
                        matrixPrototype,
                        new ProtosIntegerValue(1),
                        new ProtosIntegerValue(2),
                        List.of(new ProtosIntegerValue(3), new ProtosFloatValue(2.5d)));
        vector.createLocalSlot("self", vector);
        vector.createLocalSlot("other", matrix);
        matrix.createLocalSlot("self", matrix);
        matrix.createLocalSlot("other", vector);
        vector.close();
        matrix.freeze();

        List<Object> result =
                ProtosActorValueTransfer.snapshotArguments(List.of(vector, matrix, vector), source);

        assertFalse(ProtosActorValueTransfer.requiresMaterialization(result));
        ProtosVectorValue copiedVector = assertInstanceOf(ProtosVectorValue.class, result.get(0));
        ProtosMatrixValue copiedMatrix = assertInstanceOf(ProtosMatrixValue.class, result.get(1));
        assertNotSame(vector, copiedVector);
        assertNotSame(matrix, copiedMatrix);
        assertSame(copiedVector, result.get(2));
        assertSame(vectorPrototype, copiedVector.parent().orElseThrow());
        assertSame(matrixPrototype, copiedMatrix.parent().orElseThrow());
        assertSame(copiedVector, copiedVector.readLocalSlot("self").orElseThrow());
        assertSame(copiedMatrix, copiedVector.readLocalSlot("other").orElseThrow());
        assertSame(copiedMatrix, copiedMatrix.readLocalSlot("self").orElseThrow());
        assertSame(copiedVector, copiedMatrix.readLocalSlot("other").orElseThrow());
        assertTrue(copiedVector.isClosed());
        assertTrue(copiedMatrix.isFrozen());

        assertEquals(3, copiedVector.indexedSize());
        for (int index = 0; index < 3; index++) {
            assertTrue(
                    ProtosIdentity.identical(
                            vector.indexedSnapshot().get(index),
                            copiedVector.indexedSnapshot().get(index)));
        }
        assertTrue(ProtosIdentity.identical(new ProtosIntegerValue(1), copiedMatrix.rowsValue()));
        assertTrue(ProtosIdentity.identical(new ProtosIntegerValue(2), copiedMatrix.columnsValue()));
        assertTrue(
                ProtosIdentity.identical(
                        new ProtosFloatValue(2.5d),
                        copiedMatrix.indexedAt(new ProtosIntegerValue(0), new ProtosIntegerValue(1))));

        // A closed Vector keeps atPut; neither side can reach the other's payload.
        vector.indexedPut(new ProtosIntegerValue(0), new ProtosIntegerValue(99));
        assertTrue(
                ProtosIdentity.identical(
                        new ProtosIntegerValue(1), copiedVector.indexedAt(new ProtosIntegerValue(0))));
        copiedVector.indexedPut(new ProtosIntegerValue(1), new ProtosIntegerValue(7));
        assertTrue(ProtosIdentity.identical(big, vector.indexedAt(new ProtosIntegerValue(1))));
        ArrayList<String> names = new ArrayList<>();
        copiedVector.appendLocalBindingsTo(names, new ArrayList<>());
        assertEquals(java.util.Set.of("self", "other"), java.util.Set.copyOf(names));
    }

    @Test
    void linearAlgebraFractionAndComplexElementsMaterializeInTheDestination() throws Exception {
        ProtosPrelude sourcePrelude = core();
        // Actors share one Prelude, whose frozen LinearAlgebra prototypes are shared anchors.
        ProtosPrelude destinationPrelude = sourcePrelude;
        ProtosActivation source = sourcePrelude.newModuleActivation();
        ProtosObjectValue vectorPrototype = linearAlgebraMember(sourcePrelude, "Vector");
        ProtosObjectValue matrixPrototype = linearAlgebraMember(sourcePrelude, "Matrix");
        Object half =
                ProtosNumericValueSupport.divideIntegersExactly(
                        new ProtosIntegerValue(1), new ProtosIntegerValue(2), sourcePrelude);
        Object complex =
                ProtosComplexNumericSupport.normalize(
                        new ProtosIntegerValue(3), new ProtosFloatValue(-0.0d), sourcePrelude);
        ProtosVectorValue vector =
                new ProtosVectorValue(vectorPrototype, List.of(half, complex, half));
        ProtosMatrixValue matrix =
                new ProtosMatrixValue(
                        matrixPrototype,
                        new ProtosIntegerValue(2),
                        new ProtosIntegerValue(1),
                        List.of(complex, half));
        ProtosObjectValue holder = new ProtosObjectValue(ProtosObjectValue.rootObject());
        holder.createLocalSlot("left", vector);
        holder.createLocalSlot("right", vector);
        holder.createLocalSlot("matrix", matrix);

        List<Object> snapshot = ProtosActorValueTransfer.snapshotArguments(List.of(holder), source);
        assertTrue(ProtosActorValueTransfer.requiresMaterialization(snapshot));
        List<?> delivered =
                ProtosActorValueTransfer.materializeArguments(
                        snapshot, destinationPrelude.newModuleActivation());

        ProtosObjectValue copiedHolder = assertInstanceOf(ProtosObjectValue.class, delivered.get(0));
        ProtosVectorValue copiedVector =
                assertInstanceOf(
                        ProtosVectorValue.class, copiedHolder.readLocalSlot("left").orElseThrow());
        ProtosMatrixValue copiedMatrix =
                assertInstanceOf(
                        ProtosMatrixValue.class, copiedHolder.readLocalSlot("matrix").orElseThrow());
        // One object shared by two paths of the graph stays one object.
        assertSame(copiedVector, copiedHolder.readLocalSlot("right").orElseThrow());
        assertSame(vectorPrototype, copiedVector.parent().orElseThrow());
        List<Object> elements = copiedVector.indexedSnapshot();
        assertTrue(ProtosNumericValueSupport.isFraction(elements.get(0)));
        assertTrue(ProtosComplexNumericSupport.isComplex(elements.get(1)));
        assertNotSame(half, elements.get(0));
        assertNotSame(complex, elements.get(1));
        assertTrue(ProtosIdentity.identical(half, elements.get(0)));
        assertTrue(ProtosIdentity.identical(complex, elements.get(1)));
        assertSame(elements.get(0), elements.get(2));
        assertSame(
                elements.get(1),
                copiedMatrix.indexedAt(new ProtosIntegerValue(0), new ProtosIntegerValue(0)));
        for (Object element : copiedMatrix.indexedSnapshot()) {
            assertTrue(ProtosNumericValueSupport.isCurrentNumber(element));
        }
        // Elements read through the public accessors are Numbers, never inert descriptors.
        for (int index = 0; index < copiedVector.indexedSize(); index++) {
            Object element = copiedVector.indexedAt(new ProtosIntegerValue(index));
            assertTrue(ProtosNumericValueSupport.isCurrentNumber(element));
            assertFalse(ProtosNumericValueSupport.isFractionTransferDescriptor(element));
            assertFalse(ProtosComplexNumericSupport.isTransferDescriptor(element));
        }
        assertTrue(
                ProtosComplexNumericSupport.isComplex(
                        copiedMatrix.indexedAt(new ProtosIntegerValue(0), new ProtosIntegerValue(0))));
        assertTrue(
                ProtosNumericValueSupport.isFraction(
                        copiedMatrix.indexedAt(new ProtosIntegerValue(1), new ProtosIntegerValue(0))));
    }

    @Test
    void linearAlgebraDetachedSnapshotKeepsStateAndCyclesAndFailsAtomically() throws Exception {
        ProtosPrelude sourcePrelude = core();
        ProtosActivation source = sourcePrelude.newModuleActivation();
        ProtosObjectValue vectorPrototype = linearAlgebraMember(sourcePrelude, "Vector");
        ProtosObjectValue matrixPrototype = linearAlgebraMember(sourcePrelude, "Matrix");
        Object half =
                ProtosNumericValueSupport.divideIntegersExactly(
                        new ProtosIntegerValue(1), new ProtosIntegerValue(2), sourcePrelude);
        ProtosVectorValue vector = new ProtosVectorValue(vectorPrototype, List.of(half));
        ProtosMatrixValue matrix =
                new ProtosMatrixValue(
                        matrixPrototype,
                        new ProtosIntegerValue(1),
                        new ProtosIntegerValue(1),
                        List.of(half));
        vector.createLocalSlot("self", vector);
        vector.createLocalSlot("matrix", matrix);
        matrix.createLocalSlot("vector", vector);
        vector.freeze();
        matrix.close();

        List<Object> snapshot =
                ProtosActorValueTransfer.snapshotArguments(List.of(vector, matrix), source);
        assertTrue(ProtosActorValueTransfer.requiresMaterialization(snapshot));
        List<?> delivered =
                ProtosActorValueTransfer.materializeArguments(
                        snapshot, sourcePrelude.newModuleActivation());
        ProtosVectorValue copiedVector = assertInstanceOf(ProtosVectorValue.class, delivered.get(0));
        ProtosMatrixValue copiedMatrix = assertInstanceOf(ProtosMatrixValue.class, delivered.get(1));
        assertSame(copiedVector, copiedVector.readLocalSlot("self").orElseThrow());
        assertSame(copiedMatrix, copiedVector.readLocalSlot("matrix").orElseThrow());
        assertSame(copiedVector, copiedMatrix.readLocalSlot("vector").orElseThrow());
        assertTrue(copiedVector.isFrozen());
        assertTrue(copiedMatrix.isClosed());
        Object element = copiedVector.indexedAt(new ProtosIntegerValue(0));
        assertTrue(ProtosNumericValueSupport.isFraction(element));
        assertSame(
                element,
                copiedMatrix.indexedAt(new ProtosIntegerValue(0), new ProtosIntegerValue(0)));
        assertThrows(
                IllegalStateException.class,
                () -> copiedVector.indexedPut(new ProtosIntegerValue(0), new ProtosIntegerValue(1)));

        // A non-transferable slot rejects the whole snapshot; no partial container is published.
        ProtosVectorValue failing = new ProtosVectorValue(vectorPrototype, List.of(half));
        failing.createLocalSlot(
                "behavior", ProtosClosureValue.nativeClosure((activation, supplied) -> half));
        ProtosObjectValue holder = new ProtosObjectValue(ProtosObjectValue.rootObject());
        holder.createLocalSlot("vector", failing);
        assertNonTransferable(
                source, () -> ProtosActorValueTransfer.snapshotArguments(List.of(holder), source));
        assertNonTransferable(
                source, () -> ProtosActorValueTransfer.snapshotValue(failing, source));
        assertSame(half, failing.indexedAt(new ProtosIntegerValue(0)));
        assertTrue(failing.isOpen());
    }

    @Test
    void spawnDelegationPreservesOneDestinationCapabilityAcrossAliasesAndCycles() throws Exception {
        SpawnFixture x = spawnFixture();
        ProtosTcpAcceptorValue acceptor = x.acquire();
        ProtosArrayValue array = x.prelude.newArray(List.of(acceptor, acceptor));
        ProtosMapValue map = x.prelude.newMap();
        ProtosInvocation.invokeMessage(
                map, "atPut", List.of(new ProtosStringValue("k"), acceptor), x.source);
        ProtosIdentityMapValue identityMap =
                new ProtosIdentityMapValue(x.prelude.identityMapPrototype());
        ProtosInvocation.invokeMessage(identityMap, "atPut", List.of(acceptor, acceptor), x.source);
        ProtosObjectValue holder = new ProtosObjectValue(ProtosObjectValue.rootObject());
        holder.createLocalSlot("acceptor", acceptor);
        holder.createLocalSlot("self", holder);
        holder.createLocalSlot("array", array);
        ProtosObjectValue child = new ProtosObjectValue(acceptor);

        List<?> delivered =
                x.spawn(List.of(acceptor, acceptor, array, map, identityMap, holder, child));

        ProtosTcpAcceptorValue destination =
                assertInstanceOf(ProtosTcpAcceptorValue.class, delivered.get(0));
        assertNotSame(acceptor, destination);
        assertSame(destination, delivered.get(1));
        ProtosArrayValue copiedArray = assertInstanceOf(ProtosArrayValue.class, delivered.get(2));
        assertSame(destination, copiedArray.indexedSnapshot().get(0));
        assertSame(destination, copiedArray.indexedSnapshot().get(1));
        ProtosMapValue copiedMap = assertInstanceOf(ProtosMapValue.class, delivered.get(3));
        assertSame(destination, copiedMap.keyedSnapshot().get(0).value());
        ProtosIdentityMapValue copiedIdentityMap =
                assertInstanceOf(ProtosIdentityMapValue.class, delivered.get(4));
        assertSame(destination, copiedIdentityMap.keyedSnapshot().get(0).key());
        assertSame(destination, copiedIdentityMap.keyedSnapshot().get(0).value());
        ProtosObjectValue copiedHolder = assertInstanceOf(ProtosObjectValue.class, delivered.get(5));
        assertSame(destination, copiedHolder.readLocalSlot("acceptor").orElseThrow());
        assertSame(copiedHolder, copiedHolder.readLocalSlot("self").orElseThrow());
        assertSame(copiedArray, copiedHolder.readLocalSlot("array").orElseThrow());
        ProtosObjectValue copiedChild = assertInstanceOf(ProtosObjectValue.class, delivered.get(6));
        assertSame(destination, copiedChild.parent().orElseThrow());
        assertTrue(destination.ownedByForRuntime(x.destination));
        assertFalse(destination.ownedByForRuntime(x.source));
        assertEquals(0, x.backend.accepts.get());
    }

    @Test
    void distinctSpawnDelegatedCapabilitiesStayDistinctOverOneAuthority() throws Exception {
        SpawnFixture x = spawnFixture();
        ProtosTcpAcceptorValue first = x.acquire();
        ProtosTcpAcceptorValue second = x.acquire();

        List<?> delivered = x.spawn(List.of(first, second));

        ProtosTcpAcceptorValue copiedFirst =
                assertInstanceOf(ProtosTcpAcceptorValue.class, delivered.get(0));
        ProtosTcpAcceptorValue copiedSecond =
                assertInstanceOf(ProtosTcpAcceptorValue.class, delivered.get(1));
        assertNotSame(copiedFirst, copiedSecond);
        assertEquals(ProtosFutureValue.State.PENDING, copiedFirst.acceptForRuntime(x.destination).state());
        assertEquals(ProtosFutureValue.State.PENDING, copiedSecond.acceptForRuntime(x.destination).state());
        assertEquals(2, x.backend.accepts.get(), "both capabilities reach the one listener backend");
    }

    @Test
    void spawnDelegationAttenuatesSlotsAndStructuralStateOfClosedOrFrozenSources() throws Exception {
        for (boolean freeze : new boolean[] {false, true}) {
            SpawnFixture x = spawnFixture();
            ProtosTcpAcceptorValue acceptor = x.acquire();
            acceptor.createLocalSlot("label", new ProtosIntegerValue(3));
            acceptor.createLocalSlot("secret", x.listener);
            if (freeze) acceptor.freeze(); else acceptor.close();

            ProtosTcpAcceptorValue destination =
                    assertInstanceOf(ProtosTcpAcceptorValue.class, x.spawn(List.of(acceptor)).get(0));

            assertTrue(destination.isOpen());
            assertTrue(destination.localSlotsSnapshot().isEmpty());
            assertSame(x.prelude.tcpAcceptorPrototypeForRuntime(), destination.parent().orElseThrow());
            assertEquals(freeze, acceptor.isFrozen());
            assertEquals(!freeze, acceptor.isClosed());
            assertEquals(2, acceptor.localSlotsSnapshot().size());
            assertEquals(ProtosFutureValue.State.PENDING, destination.acceptForRuntime(x.destination).state());
            assertEquals(1, x.backend.accepts.get());
        }
    }

    @Test
    void independentlyReachableNonTransferableValueFailsTheWholeSpawnSnapshot() throws Exception {
        SpawnFixture x = spawnFixture();
        ProtosTcpAcceptorValue acceptor = x.acquire();
        acceptor.createLocalSlot("label", new ProtosIntegerValue(3));
        ProtosObjectValue holder = new ProtosObjectValue(ProtosObjectValue.rootObject());
        holder.createLocalSlot("acceptor", acceptor);
        holder.createLocalSlot("listener", x.listener);

        assertNonTransferable(
                x.source,
                () -> ProtosActorValueTransfer.snapshotSpawnArguments(List.of(acceptor, holder), x.source));

        assertTrue(acceptor.isOpen());
        assertEquals(1, acceptor.localSlotsSnapshot().size());
        assertSame(acceptor, holder.readLocalSlot("acceptor").orElseThrow());
        assertSame(x.listener, holder.readLocalSlot("listener").orElseThrow());
        assertEquals(ProtosIoLifecycle.State.OPEN, x.listener.lifecycleForRuntime().state());
        assertEquals(0, x.backend.accepts.get());
    }

    @Test
    void spawnDelegationRequiresSourceActorProcessDomainAndDestinationProcess() throws Exception {
        // Missing source Actor: a standalone activation owns the listener.
        ProtosPrelude prelude = core();
        ProtosActivation standalone = prelude.newModuleActivation();
        CountingListenerBackend standaloneBackend = new CountingListenerBackend();
        ProtosTcpAcceptorValue unhosted =
                acquire(listener(prelude, standalone, standaloneBackend), standalone);
        assertNonTransferable(
                standalone,
                () -> ProtosActorValueTransfer.snapshotSpawnArguments(List.of(unhosted), standalone));

        // Missing source Process: an Actor without Process hosting owns the listener.
        ProtosActor processless = new ProtosActor(prelude.actorRefPrototypeForRuntime());
        ProtosActivation processlessActivation = actorActivation(prelude, processless);
        ProtosTcpAcceptorValue processlessAcceptor =
                acquire(listener(prelude, processlessActivation, standaloneBackend), processlessActivation);
        assertNonTransferable(
                processlessActivation,
                () -> ProtosActorValueTransfer.snapshotSpawnArguments(
                        List.of(processlessAcceptor), processlessActivation));

        // Source domain mismatch: the snapshot is formed outside the capability's owner domain.
        SpawnFixture x = spawnFixture();
        ProtosTcpAcceptorValue acceptor = x.acquire();
        assertNonTransferable(
                x.destination,
                () -> ProtosActorValueTransfer.snapshotSpawnArguments(List.of(acceptor), x.destination));

        // Destination in another Process incarnation fails closed without any authority.
        List<Object> snapshot = ProtosActorValueTransfer.snapshotSpawnArguments(List.of(acceptor), x.source);
        ProtosProcessRuntime otherProcess = new ProtosProcessRuntime(x.prelude.actorRefPrototypeForRuntime());
        ProtosActivation foreignDestination =
                actorActivation(x.prelude, otherProcess.createHostedActorForRuntime(x.prelude.actorRefPrototypeForRuntime()));
        assertThrows(
                IllegalStateException.class,
                () -> ProtosActorValueTransfer.materializeArguments(snapshot, foreignDestination));
        assertThrows(
                IllegalStateException.class,
                () -> ProtosActorValueTransfer.materializeArguments(
                        snapshot, actorActivation(x.prelude, processless)));

        // Listener in an incompatible Process: delegated admission rejects the foreign Actor.
        assertThrows(
                IllegalArgumentException.class,
                () -> x.listener.lifecycleForRuntime().beginDelegatedTcpAcceptForRuntime(foreignDestination));
        assertEquals(0, x.backend.accepts.get());
        assertEquals(0, standaloneBackend.accepts.get());
    }

    @Test
    void ordinarySnapshotsStillRejectTopLevelAndNestedTcpAcceptors() throws Exception {
        SpawnFixture x = spawnFixture();
        ProtosTcpAcceptorValue acceptor = x.acquire();
        ProtosArrayValue nested = x.prelude.newArray(List.of(acceptor));
        ProtosObjectValue holder = new ProtosObjectValue(ProtosObjectValue.rootObject());
        holder.createLocalSlot("inner", nested);

        assertNonTransferable(x.source, () -> ProtosActorValueTransfer.snapshotValue(acceptor, x.source));
        assertNonTransferable(x.source, () -> ProtosActorValueTransfer.snapshotValue(holder, x.source));
        assertNonTransferable(
                x.source, () -> ProtosActorValueTransfer.snapshotArguments(List.of(acceptor), x.source));
        assertNonTransferable(
                x.source, () -> ProtosActorValueTransfer.snapshotArguments(List.of(holder), x.source));
        assertNonTransferable(
                x.source,
                () -> ProtosActorValueTransfer.snapshotValue(new ProtosObjectValue(acceptor), x.source));
    }

    @Test
    void spawnDelegatedCapabilitiesRemainValidMapAndIdentityMapKeys() throws Exception {
        SpawnFixture x = spawnFixture();
        ProtosTcpAcceptorValue plain = x.acquire();
        ProtosTcpAcceptorValue overridden = x.acquire();
        ProtosObjectValue inheriting = new ProtosObjectValue(overridden);
        ProtosMapValue map = x.prelude.newMap();
        ProtosIdentityMapValue identityMap =
                new ProtosIdentityMapValue(x.prelude.identityMapPrototype());
        for (ProtosObjectValue key : List.of(plain, overridden, inheriting)) {
            ProtosInvocation.invokeMessage(map, "atPut", List.of(key, key), x.source);
            ProtosInvocation.invokeMessage(identityMap, "atPut", List.of(key, key), x.source);
        }
        // The source hash override is never executed during snapshot formation and is omitted at
        // the destination, so its stale source bookkeeping must not survive.
        overridden.createLocalSlot("hash", new ProtosIntegerValue(7));

        List<?> delivered =
                x.spawn(List.of(map, identityMap, plain, overridden, plain, overridden));
        ProtosMapValue copiedMap = assertInstanceOf(ProtosMapValue.class, delivered.get(0));
        ProtosIdentityMapValue copiedIdentityMap =
                assertInstanceOf(ProtosIdentityMapValue.class, delivered.get(1));
        Object copiedPlain = delivered.get(2);
        Object copiedOverridden = delivered.get(3);
        assertSame(copiedPlain, delivered.get(4));
        assertSame(copiedOverridden, delivered.get(5));
        assertTrue(((ProtosObjectValue) copiedOverridden).localSlotsSnapshot().isEmpty());
        Object copiedInheriting = copiedMap.keyedSnapshot().get(2).key();
        assertSame(copiedOverridden, ((ProtosObjectValue) copiedInheriting).parent().orElseThrow());

        for (Object key : List.of(copiedPlain, copiedOverridden, copiedInheriting)) {
            assertSame(key, ProtosInvocation.invokeMessage(copiedMap, "at", List.of(key), x.destination));
            assertSame(
                    key, ProtosInvocation.invokeMessage(copiedIdentityMap, "at", List.of(key), x.destination));
        }
        for (ProtosMapValue.Entry entry : copiedMap.keyedSnapshot()) {
            assertEquals(ProtosNumericHashKey.fromIdentity(entry.key()), entry.recordedHash());
        }
    }

    private static SpawnFixture spawnFixture() throws Exception {
        ProtosPrelude prelude = core();
        ProtosProcessRuntime process = new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());
        ProtosActivation source = actorActivation(prelude, process.rootActorForRuntime());
        ProtosActivation destination =
                actorActivation(prelude, process.createHostedActorForRuntime(prelude.actorRefPrototypeForRuntime()));
        CountingListenerBackend backend = new CountingListenerBackend();
        return new SpawnFixture(prelude, source, destination, listener(prelude, source, backend), backend);
    }

    private static ProtosActivation actorActivation(ProtosPrelude prelude, ProtosActor actor) {
        return prelude.newModuleActivation(
                actor.moduleState(), null, prelude.newExecutionContext(), actor.executionDomain());
    }

    private static ProtosTcpListenerValue listener(
            ProtosPrelude prelude, ProtosActivation owner, CountingListenerBackend backend) {
        return new ProtosTcpListenerValue(
                prelude, new Object(), owner, backend, 51000,
                ProtosStandardTcpListenerProtocol::materializeAcceptedConnection);
    }

    private static ProtosTcpAcceptorValue acquire(ProtosTcpListenerValue listener, ProtosActivation owner) {
        return assertInstanceOf(
                ProtosTcpAcceptorValue.class,
                ProtosInvocation.invokeMessage(listener, "acceptCapability", List.of(), owner));
    }

    private record SpawnFixture(
            ProtosPrelude prelude,
            ProtosActivation source,
            ProtosActivation destination,
            ProtosTcpListenerValue listener,
            CountingListenerBackend backend) {
        ProtosTcpAcceptorValue acquire() {
            return ProtosActorValueTransferTest.acquire(listener, source);
        }

        List<?> spawn(List<?> arguments) {
            List<Object> snapshot = ProtosActorValueTransfer.snapshotSpawnArguments(arguments, source);
            assertTrue(ProtosActorValueTransfer.requiresMaterialization(snapshot));
            return ProtosActorValueTransfer.materializeArguments(snapshot, destination);
        }
    }

    private static final class CountingListenerBackend implements ProtosTcpListenerFlow.Backend {
        final AtomicInteger accepts = new AtomicInteger();

        @Override
        public void close(ProtosTcpListenerFlow.CloseCompletion completion) {}

        @Override
        public ProtosTcpListenerFlow.Cancellation accept(ProtosTcpListenerFlow.AcceptCompletion completion) {
            accepts.incrementAndGet();
            return () -> {};
        }
    }

    private static ProtosObjectValue linearAlgebraMember(ProtosPrelude prelude, String name) {
        ProtosObjectValue context = prelude.newExecutionContext();
        prelude.installStandardModuleMembersForRuntime(
                new com.guillermomolina.protos.runtime.modules.ProtosModuleKey(
                        "std:math/LinearAlgebra"),
                context);
        ProtosObjectValue member = (ProtosObjectValue) context.readLocalSlot(name).orElseThrow();
        assertTrue(member.isFrozen());
        assertTrue(prelude.isStandardModuleMemberForRuntime(member));
        return member;
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(CORE);
    }

    private static void assertNonTransferable(ProtosActivation source, Runnable operation) {
        ProtosSignalException signal = assertThrows(ProtosSignalException.class, operation::run);
        assertSame(
                ProtosCoreErrors.prototype(
                        source, ProtosCoreErrors.StandardError.NON_TRANSFERABLE_VALUE),
                signal.error().parent().orElseThrow());
    }
}

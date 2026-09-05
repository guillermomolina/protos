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
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosActorValueTransferTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void scalarValuesCopyWhileCanonicalSingletonsRemainCanonical() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosIntegerValue integer = new ProtosIntegerValue(BigInteger.valueOf(42));
        ProtosStringValue string = new ProtosStringValue("snapshot");
        ProtosPathValue path =
                new ProtosPathValue(
                        prelude.pathPrototype(),
                        false,
                        List.of(new ProtosPathValue.Normal("a"), ProtosPathValue.Parent.INSTANCE));

        List<Object> result =
                ProtosActorValueTransfer.snapshotArguments(
                        List.of(integer, string, ProtosBooleanValue.TRUE, ProtosNullValue.INSTANCE, path),
                        source);

        assertNotSame(integer, result.get(0));
        assertTrue(ProtosIdentity.identical(integer, result.get(0)));
        assertNotSame(string, result.get(1));
        assertTrue(ProtosIdentity.identical(string, result.get(1)));
        assertSame(ProtosBooleanValue.TRUE, result.get(2));
        assertSame(ProtosNullValue.INSTANCE, result.get(3));
        ProtosPathValue copiedPath = assertInstanceOf(ProtosPathValue.class, result.get(4));
        assertNotSame(path, copiedPath);
        assertTrue(path.structurallyEquals(copiedPath));
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
        array.indexedPut(BigInteger.ZERO, array);
        array.createLocalSlot("self", array);

        List<Object> result =
                ProtosActorValueTransfer.snapshotArguments(List.of(array, array), source);
        ProtosArrayValue copied = assertInstanceOf(ProtosArrayValue.class, result.get(0));

        assertSame(copied, result.get(1));
        assertSame(copied, copied.indexedAt(BigInteger.ZERO));
        assertSame(copied, copied.readLocalSlot("self").orElseThrow());
        assertNotSame(array, copied);
    }

    @Test
    void bytesSnapshotOwnsIndependentIndexedAndLocalState() throws Exception {
        ProtosActivation source = core().newModuleActivation();
        ProtosBytesValue bytes = new ProtosBytesValue(ProtosObjectValue.rootObject());
        bytes.indexedAdd(new ProtosIntegerValue(BigInteger.ONE));
        bytes.indexedAdd(new ProtosIntegerValue(BigInteger.TWO));
        ProtosObjectValue metadata = new ProtosObjectValue(ProtosObjectValue.rootObject());
        bytes.createLocalSlot("metadata", metadata);

        ProtosBytesValue copied =
                assertInstanceOf(
                        ProtosBytesValue.class,
                        ProtosActorValueTransfer.snapshotValue(bytes, source));
        bytes.indexedPut(BigInteger.ZERO, new ProtosIntegerValue(BigInteger.valueOf(9)));

        assertNotSame(bytes, copied);
        assertEquals(BigInteger.ONE, ((ProtosIntegerValue) copied.indexedAt(BigInteger.ZERO)).value());
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
                source,
                () ->
                        ProtosActorValueTransfer.snapshotValue(
                                new ProtosByteRegionValue(List.of()), source));
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
        assertEquals(ProtosIdentity.identityHash(copiedKey), entries.get(0).recordedHash());
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
                ProtosIdentity.identityHash(copiedFirst),
                entries.get(0).recordedIdentityHash());
        assertEquals(
                ProtosIdentity.identityHash(copiedSecond),
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

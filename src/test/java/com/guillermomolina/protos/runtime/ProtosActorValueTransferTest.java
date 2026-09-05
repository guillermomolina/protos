/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
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
    void specializedKeyedCollectionsFailInsteadOfSilentlyDroppingTheirState() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation source = prelude.newModuleActivation();
        ProtosMapValue map = prelude.newMap();
        ProtosIdentityMapValue identityMap = new ProtosIdentityMapValue(prelude.identityMapPrototype());

        assertNonTransferable(source, () -> ProtosActorValueTransfer.snapshotValue(map, source));
        assertNonTransferable(
                source, () -> ProtosActorValueTransfer.snapshotValue(identityMap, source));
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

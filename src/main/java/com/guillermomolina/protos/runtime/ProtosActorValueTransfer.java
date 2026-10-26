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

import com.guillermomolina.protos.runtime.io.ProtosFileValue;
import com.guillermomolina.protos.runtime.io.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.io.ProtosPathComponents;
import com.guillermomolina.protos.runtime.io.ProtosTcpConnectionValue;
import com.guillermomolina.protos.runtime.io.ProtosTcpAcceptorValue;
import com.guillermomolina.protos.runtime.io.ProtosTcpListenerValue;
import com.guillermomolina.protos.runtime.numeric.ProtosNumericHashKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Actor-boundary logical value snapshot/copy foundation.
 *
 * <p>The copier owns one memo table for the complete operation so aliases and cycles remain aliases
 * and cycles in the destination graph. Nothing built here is exposed until the complete requested
 * value/vector has copied successfully, which gives later spawn/send/request paths an atomic
 * validation boundary without mutating the source graph.
 *
 * <p>This class deliberately implements Actor transfer rules rather than reusing the P copier:
 * Closures and Actor-local execution state are non-transferable between Actors, ActorRef/GroupRef
 * communication capabilities are rematerialized without copying target mutable state, and an
 * explicitly delegated Process capability becomes a fresh Actor-local proxy to the same authority.
 *
 * <p>A value of a bootstrap-authorized Standard Library semantic transfer family (PLAT051) is
 * never copied. Snapshot formation (the source stage) validates and extracts it into an internal
 * {@link ProtosSemanticTransferRecord}; the snapshot is then a detached transfer graph that must be
 * materialized inside the destination domain by {@link #materializeArguments} or
 * {@link #materializeValue} before guest code observes it. Only snapshots that actually contain
 * records are wrapped in an internal {@link DetachedGraph}; an ordinary snapshot is returned
 * unwrapped and its destination materialization is an O(1) recognition check, never a second pass.
 *
 * <p>A Path (I094) follows the same two stages: the source stage extracts it into an inert
 * {@link ProtosPathComponents} descriptor, and the destination stage builds one Path delegating
 * to the destination Prelude's Path prototype per descriptor identity.
 */
public final class ProtosActorValueTransfer {
    private ProtosActorValueTransfer() {}

    /** Forms one detached Actor-boundary snapshot value or signals NonTransferableValue. */
    public static Object snapshotValue(Object value, ProtosActivation source) {
        Copier copier = new Copier(source, false, false);
        Object copied = copier.copy(value);
        return copier.semanticRecords ? new DetachedGraph(List.of(copied), false) : copied;
    }

    /**
     * Forms one atomic snapshot for an argument vector.
     *
     * <p>One memo is shared by every argument so aliases/cycles spanning argument roots are
     * preserved rather than copied independently.
     */
    public static List<Object> snapshotArguments(List<?> values, ProtosActivation source) {
        return snapshotArgumentVector(values, source, false);
    }

    /**
     * Actor.spawn initialization snapshot (D217, I125-B): {@link #snapshotArguments} plus the
     * spawn-only delegation of physical TcpAcceptor capabilities. No other boundary may use it.
     */
    public static List<Object> snapshotSpawnArguments(List<?> values, ProtosActivation source) {
        return snapshotArgumentVector(values, source, true);
    }

    private static List<Object> snapshotArgumentVector(
            List<?> values, ProtosActivation source, boolean spawnDelegation) {
        Objects.requireNonNull(values, "values");
        Copier copier = new Copier(source, false, spawnDelegation);
        ArrayList<Object> result = new ArrayList<>(values.size());
        for (Object value : values) {
            result.add(copier.copy(value));
        }
        List<Object> snapshot = List.copyOf(result);
        return copier.semanticRecords
                ? List.of(new DetachedGraph(snapshot, spawnDelegation))
                : snapshot;
    }

    /** True when an argument snapshot carries semantic transfer records needing materialization. */
    public static boolean requiresMaterialization(List<?> snapshot) {
        return snapshot.size() == 1 && snapshot.get(0) instanceof DetachedGraph;
    }

    /**
     * Destination stage for an argument snapshot formed by {@link #snapshotArguments}.
     *
     * <p>Must run inside the destination execution domain before the arguments reach guest code.
     * An ordinary snapshot is returned as is. A snapshot carrying semantic transfer records is
     * copied once more under the same Actor transfer rules into a fresh destination-local graph in
     * which every record is materialized exactly once, so aliases, cycles and distinct identities
     * are preserved and every delivery of the same logical snapshot gets its own identities.
     */
    public static List<?> materializeArguments(List<?> snapshot, ProtosActivation destination) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!requiresMaterialization(snapshot)) {
            return snapshot;
        }
        DetachedGraph graph = (DetachedGraph) snapshot.get(0);
        Copier materializer = new Copier(destination, true, graph.spawnDelegation);
        List<Object> roots = graph.roots;
        ArrayList<Object> result = new ArrayList<>(roots.size());
        for (Object root : roots) {
            result.add(materializer.copy(root));
        }
        return List.copyOf(result);
    }

    /** Destination stage for one value formed by {@link #snapshotValue}; see {@link #materializeArguments}. */
    public static Object materializeValue(Object snapshot, ProtosActivation destination) {
        if (!(snapshot instanceof DetachedGraph graph)) {
            return snapshot;
        }
        return new Copier(destination, true, graph.spawnDelegation).copy(graph.roots.get(0));
    }

    /**
     * Resolves a requester-domain request Future with one reply snapshot.
     *
     * <p>An ordinary reply resolves immediately. A reply carrying semantic transfer records is
     * materialized by a targeted runtime completion inside the requester's own execution domain,
     * with the requester's authority, before the Future becomes RESOLVED; if the requester is
     * already terminating the Future is cancelled as termination would cancel it. A materialization
     * failure is an internal inconsistency and fails the Future with a generic Error.
     */
    public static void resolveRequesterFuture(
            ProtosFutureValue future, Object replySnapshot, ProtosActivation requester) {
        Objects.requireNonNull(future, "future");
        Objects.requireNonNull(requester, "requester");
        if (!(replySnapshot instanceof DetachedGraph)) {
            future.resolve(replySnapshot, requester);
            return;
        }
        try {
            requester.executionDomain()
                    .enqueueTargetedFutureCompletionForRuntime(
                            future,
                            () -> {
                                if (!future.isPending()) {
                                    return false;
                                }
                                Object reply;
                                try {
                                    reply = materializeValue(replySnapshot, requester);
                                } catch (RuntimeException inconsistency) {
                                    // Internal inconsistency, contained as a generic Error.
                                    return future.fail(ProtosCoreErrors.newError(requester));
                                }
                                return future.resolve(reply, requester);
                            });
        } catch (IllegalStateException requesterTerminating) {
            future.cancelTerminal();
        }
    }

    /**
     * Internal carrier of a snapshot that contains semantic transfer records. It is never a Protos
     * value and never reaches guest code: every destination boundary unwraps it by
     * materialization.
     */
    private static final class DetachedGraph {
        private final List<Object> roots;
        /** Spawn scope: only such a graph may materialize TcpAcceptor delegation descriptors. */
        private final boolean spawnDelegation;

        private DetachedGraph(List<Object> roots, boolean spawnDelegation) {
            this.roots = roots;
            this.spawnDelegation = spawnDelegation;
        }
    }

    /**
     * One snapshot operation. In the source stage ({@code materializing == false}) semantic values
     * become records. In the destination stage the input is a detached snapshot, records are
     * materialized, and any rejection is an internal inconsistency rather than a guest error.
     */
    private static final class Copier {
        private final ProtosActivation source;
        private final ProtosPrelude prelude;
        private final boolean materializing;
        /** I125-B: Actor.spawn initialization policy; ordinary boundaries never set it. */
        private final boolean spawnDelegation;
        /** True when the snapshot needs destination materialization (records or descriptors). */
        private boolean semanticRecords;
        private final IdentityHashMap<Object, Object> memo = new IdentityHashMap<>();
        private final Set<Object> populating =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Object> populated =
                Collections.newSetFromMap(new IdentityHashMap<>());

        private Copier(ProtosActivation source, boolean materializing, boolean spawnDelegation) {
            this.source = Objects.requireNonNull(source, "source");
            this.materializing = materializing;
            this.spawnDelegation = spawnDelegation;
            this.prelude =
                    source.prelude()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Actor transfer requires an owning Core prelude"));
        }

        private Object copy(Object value) {
            Object destination = allocate(value);
            populate(value);
            return destination;
        }

        /** Allocates shells while following only immutable delegation-parent edges. */
        private Object allocate(Object value) {
            if (value == null) {
                throw nonTransferable();
            }
            if (memo.containsKey(value)) {
                return memo.get(value);
            }

            if (value == ProtosNullValue.INSTANCE
                    || value == ProtosBooleanValue.TRUE
                    || value == ProtosBooleanValue.FALSE) {
                memo.put(value, value);
                return value;
            }
            if (ProtosNumericValueSupport.isFraction(value)) {
                // D197-C source stage: only exact inert components cross; the destination
                // Prelude's Fraction prototype is bound by materialization, once per identity.
                if (materializing) {
                    throw nonTransferable();
                }
                semanticRecords = true;
                return remember(value, ProtosNumericValueSupport.fractionTransferDescriptor(value));
            }
            if (materializing && ProtosNumericValueSupport.isFractionTransferDescriptor(value)) {
                return remember(
                        value,
                        ProtosNumericValueSupport.materializeFractionTransferDescriptor(
                                value, prelude));
            }
            if (ProtosComplexNumericSupport.isComplex(value)) {
                // I090-C1 source stage: only inert component descriptors cross; the destination
                // Prelude rebuilds the components and binds its Complex prototype, once per identity.
                if (materializing) {
                    throw nonTransferable();
                }
                semanticRecords = true;
                return remember(value, ProtosComplexNumericSupport.transferDescriptor(value));
            }
            if (materializing && ProtosComplexNumericSupport.isTransferDescriptor(value)) {
                return remember(
                        value,
                        ProtosComplexNumericSupport.materializeTransferDescriptor(value, prelude));
            }
            Object copiedNumber =
                    ProtosNumericValueSupport.copyCurrentNumberOrNull(value, prelude);
            if (copiedNumber != null) {
                return remember(value, copiedNumber);
            }
            if (value instanceof ProtosStringValue string) {
                return remember(value, string.copyForRuntime());
            }
            if (value instanceof ProtosPathValue path) {
                // I094 source stage: only the inert components cross; the destination Prelude's
                // Path prototype is bound by materialization, once per source identity.
                if (materializing) {
                    throw nonTransferable();
                }
                semanticRecords = true;
                return remember(value, new ProtosPathComponents(path.components()));
            }
            if (materializing && value instanceof ProtosPathComponents descriptor) {
                // I094 destination stage: one destination Path per descriptor identity.
                return remember(
                        value, new ProtosPathValue(prelude.pathPrototype(), descriptor.components()));
            }
            if (value instanceof ProtosEncodingValue encoding) {
                return remember(value, encoding.transferForActorRuntime());
            }
            if (value instanceof ProtosActorRefValue actorRef) {
                return remember(value, actorRef.rematerializeForActorTransfer());
            }
            if (value instanceof ProtosGroupRefValue groupRef) {
                return remember(value, groupRef.rematerializeForActorTransfer());
            }
            if (value instanceof ProtosProcessCapabilityValue processCapability) {
                return remember(value, processCapability.rematerializeForActorTransfer());
            }
            if (value instanceof ProtosEnvironmentValue environment) {
                return remember(value, environment.rematerializeForActorTransfer());
            }
            if (value instanceof ProtosProcessStandardStreamValue stream) {
                return remember(value, stream.rematerializeForActorTransfer());
            }

            if (value instanceof ProtosTcpAcceptorValue acceptor) {
                // D217/I125-B source stage, spawn only: the capability crosses as one inert
                // descriptor per identity. Its parent, slots and structural state are omitted,
                // so nothing reachable only through them is visited.
                if (materializing || !spawnDelegation) {
                    throw nonTransferable();
                }
                ProtosTcpAcceptorValue.SpawnDelegation descriptor;
                try {
                    descriptor = acceptor.prepareSpawnDelegationForRuntime(source);
                } catch (IllegalArgumentException | IllegalStateException rejected) {
                    throw nonTransferable();
                }
                semanticRecords = true;
                return remember(value, descriptor);
            }
            if (value instanceof ProtosTcpAcceptorValue.SpawnDelegation descriptor) {
                // D217/I125-B destination stage: one fresh capability per descriptor identity.
                // Outside the spawn scope a descriptor fails closed.
                if (!materializing || !spawnDelegation) {
                    throw nonTransferable();
                }
                return remember(
                        value,
                        ProtosTcpAcceptorValue.materializeSpawnDelegationForRuntime(
                                descriptor, source));
            }

            // These are explicitly non-transferable Actor-domain/execution/resource values.
            if (value instanceof ProtosClosureValue
                    || value instanceof ProtosSendOperationControl
                    || value instanceof ProtosFutureValue
                    || value instanceof ProtosTask
                    || value instanceof ProtosActivation
                    || value instanceof ProtosFileValue
                    || value instanceof ProtosFilesystemValue
                    || value instanceof ProtosNetworkCapabilityValue
                    || value instanceof ProtosTcpConnectionValue
                    || value instanceof ProtosTcpListenerValue
                    // LIB020-A sealed families are not portable; copying would drop the family.
                    // A Core Fraction or Complex never reaches here: each crossed above as an
                    // inert descriptor (D197-C, I090-C1).
                    || value instanceof ProtosSealedValue) {
                throw nonTransferable();
            }

            // D188: raw foreign references and foreign module facades carry provider-session
            // state that has no Actor transfer contract; copying would drop or smuggle it.
            if (value instanceof ProtosForeignProjectedReceiver) {
                throw nonTransferable();
            }

            if (!(value instanceof ProtosObjectValue object)) {
                if (materializing && value instanceof ProtosSemanticTransferRecord record) {
                    // PLAT051-A2 destination stage: one materialization per record identity.
                    return remember(value, record.materializeForRuntime(source));
                }
                // Unknown Java/native/runtime values are non-transferable by default.
                throw nonTransferable();
            }

            if (isSharedStandardObject(object)) {
                memo.put(value, value);
                populated.add(value);
                return value;
            }
            if (object instanceof ProtosSemanticTransferValue semantic) {
                // PLAT051-A2 source stage: validated and extracted into an inert record, never
                // copied or materialized here; the memo keeps one record per source identity.
                ProtosSemanticTransferRecord record =
                        materializing
                                ? null
                                : ProtosSemanticTransferRecord.prepareForRuntime(semantic, prelude);
                if (record == null) {
                    throw nonTransferable();
                }
                semanticRecords = true;
                return remember(value, record);
            }
            if (object.parent().orElse(null) == prelude.contextPrototype()) {
                // Module/activation execution contexts are Actor-local state.
                throw nonTransferable();
            }

            Object sourceParent =
                    object.parent()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "non-root Protos object lost delegation parent"));
            Object destinationParent = allocate(sourceParent);
            Object shell;
            if (object instanceof ProtosMapValue) {
                shell = new ProtosMapValue(destinationParent);
            } else if (object instanceof ProtosIdentityMapValue) {
                shell = new ProtosIdentityMapValue(destinationParent);
            } else if (object instanceof ProtosArrayValue array) {
                int size = array.indexedSize();
                ArrayList<Object> placeholders = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    placeholders.add(ProtosNullValue.INSTANCE);
                }
                shell = new ProtosArrayValue(destinationParent, placeholders);
            } else if (object instanceof ProtosBytesValue) {
                shell = new ProtosBytesValue(destinationParent);
            } else if (object instanceof ProtosVectorValue vector) {
                // LIB024: the private numeric payload is copied by population, never shared.
                shell = ProtosVectorValue.transferShellForRuntime(
                        destinationParent, vector.indexedSize());
            } else if (object instanceof ProtosMatrixValue matrix) {
                shell = ProtosMatrixValue.transferShellForRuntime(
                        destinationParent,
                        copy(matrix.rowsValue()),
                        copy(matrix.columnsValue()));
            } else {
                shell = new ProtosObjectValue(destinationParent);
            }
            memo.put(value, shell);
            return shell;
        }

        /** Populates previously allocated shells. Re-entry through a cycle observes the shell. */
        private void populate(Object value) {
            if (value == null || populated.contains(value)) {
                return;
            }
            if (!needsPopulation(value)) {
                populated.add(value);
                return;
            }
            if (!populating.add(value)) {
                return;
            }

            try {
                ProtosObjectValue sourceObject = (ProtosObjectValue) value;
                Object parent = sourceObject.parent().orElse(null);
                if (parent != null) {
                    populate(parent);
                }

                ProtosObjectValue destination = (ProtosObjectValue) memo.get(value);
                if (sourceObject instanceof ProtosMapValue sourceMap) {
                    ProtosMapValue destinationMap = (ProtosMapValue) destination;
                    for (ProtosMapValue.Entry entry : sourceMap.keyedSnapshot()) {
                        Object copiedKey = copy(entry.key());
                        Object copiedValue = copy(entry.value());
                        ProtosNumericHashKey recordedHash =
                                usesDefaultObjectHash(entry.key())
                                        ? ProtosNumericHashKey.fromIdentity(copiedKey)
                                        : entry.recordedHash();
                        destinationMap.append(copiedKey, recordedHash, copiedValue);
                    }
                } else if (sourceObject instanceof ProtosIdentityMapValue sourceIdentityMap) {
                    ProtosIdentityMapValue destinationIdentityMap =
                            (ProtosIdentityMapValue) destination;
                    for (ProtosIdentityMapValue.Entry entry : sourceIdentityMap.keyedSnapshot()) {
                        Object copiedKey = copy(entry.key());
                        Object copiedValue = copy(entry.value());
                        destinationIdentityMap.append(
                                copiedKey, ProtosNumericHashKey.fromIdentity(copiedKey), copiedValue);
                    }
                } else if (sourceObject instanceof ProtosArrayValue sourceArray) {
                    ProtosArrayValue destinationArray = (ProtosArrayValue) destination;
                    List<Object> elements = sourceArray.indexedSnapshot();
                    for (int index = 0; index < elements.size(); index++) {
                        destinationArray.indexedPut(
                                index, copy(elements.get(index)));
                    }
                } else if (sourceObject instanceof ProtosBytesValue sourceBytes) {
                    ProtosBytesValue destinationBytes = (ProtosBytesValue) destination;
                    for (Object octet : sourceBytes.indexedSnapshot()) {
                        destinationBytes.indexedAdd(copy(octet));
                    }
                } else if (sourceObject instanceof ProtosVectorValue sourceVector) {
                    ProtosVectorValue destinationVector = (ProtosVectorValue) destination;
                    List<Object> elements = sourceVector.indexedSnapshot();
                    for (int index = 0; index < elements.size(); index++) {
                        destinationVector.transferPutForRuntime(index, copy(elements.get(index)));
                    }
                } else if (sourceObject instanceof ProtosMatrixValue sourceMatrix) {
                    ProtosMatrixValue destinationMatrix = (ProtosMatrixValue) destination;
                    List<Object> elements = sourceMatrix.indexedSnapshot();
                    for (int index = 0; index < elements.size(); index++) {
                        destinationMatrix.transferPutForRuntime(index, copy(elements.get(index)));
                    }
                }

                copyLocalSlots(sourceObject, destination);
                applyMutationState(sourceObject, destination);
                populated.add(value);
            } finally {
                populating.remove(value);
            }
        }

        private boolean needsPopulation(Object value) {
            // I091: every semantic Number is reconstructed by the numeric
            // boundary, not by copying ordinary object slots.
            return value instanceof ProtosObjectValue
                    && !ProtosNumericValueSupport.isCurrentNumber(value)
                    && !isSharedStandardObject((ProtosObjectValue) value)
                    && !(value instanceof ProtosClosureValue)
                    && !(value instanceof ProtosFutureValue);
        }

        /**
         * True only when ordinary Map lookup of this object reaches Object.hash without an
         * intervening override. Rebuilding that recorded hash is required because an ordinary
         * Actor copy has a fresh semantic identity. This inspection is read-only and invokes no
         * Protos hash/equality code during snapshot formation.
         *
         * <p>In a detached snapshot a record stands where a semantic value was, as a key or as a
         * delegation parent; the walk continues through its already-allocated materialized value,
         * whose lookup chain is complete.
         */
        private boolean usesDefaultObjectHash(Object key) {
            Object current = key;
            while (true) {
                if (current instanceof ProtosTcpAcceptorValue
                        && memo.get(current) instanceof ProtosTcpAcceptorValue.SpawnDelegation) {
                    // I125-B: the delegated capability loses its local slots (including any
                    // hash override) and delegates to the canonical prototype at the destination.
                    current = prelude.tcpAcceptorPrototypeForRuntime();
                } else if (current instanceof ProtosObjectValue candidate) {
                    if (candidate.hasLocalSlot("hash")) {
                        return candidate == ProtosObjectValue.rootObject();
                    }
                    current = candidate.parent().orElse(null);
                } else if (current instanceof ProtosSemanticTransferRecord
                        || current instanceof ProtosPathComponents
                        || current instanceof ProtosTcpAcceptorValue.SpawnDelegation) {
                    current = memo.get(current);
                } else {
                    return false;
                }
            }
        }

        private void copyLocalSlots(ProtosObjectValue sourceObject, ProtosObjectValue destination) {
            ArrayList<String> names = new ArrayList<>();
            ArrayList<Object> values = new ArrayList<>();
            sourceObject.appendLocalBindingsTo(names, values);
            for (int index = 0; index < names.size(); index++) {
                destination.createLocalSlot(names.get(index), copy(values.get(index)));
            }
        }

        private static void applyMutationState(
                ProtosObjectValue sourceObject, ProtosObjectValue destination) {
            if (sourceObject.isFrozen()) {
                destination.freeze();
            } else if (sourceObject.isClosed()) {
                destination.close();
            }
        }

        private boolean isSharedStandardObject(ProtosObjectValue object) {
            if (object == ProtosObjectValue.rootObject()
                    || object == prelude.bindings()
                    || prelude.isTcpConnectionPrototypeForRuntime(object)
                    || prelude.isTcpListenerPrototypeForRuntime(object)
                    || prelude.isTcpAcceptorPrototypeForRuntime(object)
                    || prelude.isStandardModuleMemberForRuntime(object)) {
                return true;
            }
            ArrayList<String> names = new ArrayList<>();
            ArrayList<Object> values = new ArrayList<>();
            prelude.bindings().appendLocalBindingsTo(names, values);
            for (int index = 0; index < values.size(); index++) {
                if (values.get(index) == object) {
                    return true;
                }
            }
            return false;
        }

        private Object remember(Object sourceValue, Object destinationValue) {
            memo.put(sourceValue, destinationValue);
            populated.add(sourceValue);
            return destinationValue;
        }

        private RuntimeException nonTransferable() {
            if (materializing) {
                return new IllegalStateException(
                        "PLAT051 internal inconsistency: detached Actor snapshot is not transferable");
            }
            return new ProtosSignalException(
                    ProtosCoreErrors.newOccurrence(
                            source, ProtosCoreErrors.StandardError.NON_TRANSFERABLE_VALUE));
        }
    }
}

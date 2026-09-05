/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import java.math.BigInteger;
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
 * Closures and Actor-local execution state are non-transferable between Actors, while ActorRef is a
 * communication capability that is rematerialized without copying its target Actor.
 */
public final class ProtosActorValueTransfer {
    private ProtosActorValueTransfer() {}

    /** Forms one detached Actor-boundary snapshot value or signals NonTransferableValue. */
    public static Object snapshotValue(Object value, ProtosActivation source) {
        return new Copier(source).copy(value);
    }

    /**
     * Forms one atomic snapshot for an argument vector.
     *
     * <p>One memo is shared by every argument so aliases/cycles spanning argument roots are
     * preserved rather than copied independently.
     */
    public static List<Object> snapshotArguments(List<?> values, ProtosActivation source) {
        Objects.requireNonNull(values, "values");
        Copier copier = new Copier(source);
        ArrayList<Object> result = new ArrayList<>(values.size());
        for (Object value : values) {
            result.add(copier.copy(value));
        }
        return List.copyOf(result);
    }

    private static final class Copier {
        private final ProtosActivation source;
        private final ProtosPrelude prelude;
        private final IdentityHashMap<Object, Object> memo = new IdentityHashMap<>();
        private final Set<Object> populating =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Object> populated =
                Collections.newSetFromMap(new IdentityHashMap<>());

        private Copier(ProtosActivation source) {
            this.source = Objects.requireNonNull(source, "source");
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
            if (value instanceof ProtosIntegerValue integer) {
                return remember(value, new ProtosIntegerValue(integer.value()));
            }
            if (value instanceof ProtosFixedIntegerValue integer) {
                return remember(
                        value,
                        new ProtosFixedIntegerValue(integer.family(), integer.value()));
            }
            if (value instanceof ProtosFloatValue floating) {
                return remember(value, new ProtosFloatValue(floating.value()));
            }
            if (value instanceof ProtosStringValue string) {
                return remember(value, new ProtosStringValue(string.value()));
            }
            if (value instanceof ProtosPathValue path) {
                return remember(
                        value,
                        new ProtosPathValue(
                                prelude.pathPrototype(), path.rooted(), path.components()));
            }
            if (value instanceof ProtosActorRefValue actorRef) {
                return remember(value, actorRef.rematerializeForActorTransfer());
            }

            // These are explicitly non-transferable Actor-domain/execution/resource values.
            if (value instanceof ProtosClosureValue
                    || value instanceof ProtosFutureValue
                    || value instanceof ProtosTask
                    || value instanceof ProtosByteRegionValue
                    || value instanceof ProtosActivation) {
                throw nonTransferable();
            }

            // Keyed collections have semantic state outside ordinary local slots.  They need their
            // own transfer integration (including destination hash/identity-hash bookkeeping) and
            // must never silently degrade into a plain-object copy.
            if (value instanceof ProtosMapValue || value instanceof ProtosIdentityMapValue) {
                throw nonTransferable();
            }

            if (!(value instanceof ProtosObjectValue object)) {
                // Unknown Java/native/runtime values are non-transferable by default.
                throw nonTransferable();
            }

            if (isSharedStandardObject(object)) {
                memo.put(value, value);
                populated.add(value);
                return value;
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
            if (object instanceof ProtosArrayValue array) {
                int size = array.indexedSize().intValueExact();
                ArrayList<Object> placeholders = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    placeholders.add(ProtosNullValue.INSTANCE);
                }
                shell = new ProtosArrayValue(destinationParent, placeholders);
            } else if (object instanceof ProtosBytesValue) {
                shell = new ProtosBytesValue(destinationParent);
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
                if (sourceObject instanceof ProtosArrayValue sourceArray) {
                    ProtosArrayValue destinationArray = (ProtosArrayValue) destination;
                    List<Object> elements = sourceArray.indexedSnapshot();
                    for (int index = 0; index < elements.size(); index++) {
                        destinationArray.indexedPut(
                                BigInteger.valueOf(index), copy(elements.get(index)));
                    }
                } else if (sourceObject instanceof ProtosBytesValue sourceBytes) {
                    ProtosBytesValue destinationBytes = (ProtosBytesValue) destination;
                    for (Object octet : sourceBytes.indexedSnapshot()) {
                        destinationBytes.indexedAdd(copy(octet));
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
            return value instanceof ProtosObjectValue
                    && !isSharedStandardObject((ProtosObjectValue) value)
                    && !(value instanceof ProtosClosureValue)
                    && !(value instanceof ProtosFutureValue)
                    && !(value instanceof ProtosMapValue)
                    && !(value instanceof ProtosIdentityMapValue);
        }

        private void copyLocalSlots(ProtosObjectValue sourceObject, ProtosObjectValue destination) {
            for (Map.Entry<String, Object> slot : sourceObject.localSlotsSnapshot().entrySet()) {
                destination.createLocalSlot(slot.getKey(), copy(slot.getValue()));
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
            if (object == ProtosObjectValue.rootObject() || object == prelude.bindings()) {
                return true;
            }
            for (Object binding : prelude.bindings().localSlotsSnapshot().values()) {
                if (binding == object) {
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

        private ProtosSignalException nonTransferable() {
            return new ProtosSignalException(
                    ProtosCoreErrors.newOccurrence(
                            source, ProtosCoreErrors.StandardError.NON_TRANSFERABLE_VALUE));
        }
    }
}

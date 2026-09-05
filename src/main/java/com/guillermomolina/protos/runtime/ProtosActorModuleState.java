/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Mutable module state owned by exactly one Actor execution domain. */
public final class ProtosActorModuleState {
    public enum InitializationState { INITIALIZING, READY }

    public static final class ModuleRecord {
        private final ProtosObjectValue instance;
        private InitializationState state;

        public ModuleRecord(ProtosObjectValue instance) {
            this.instance = Objects.requireNonNull(instance, "instance");
            this.state = InitializationState.INITIALIZING;
        }

        public ProtosObjectValue instance() { return instance; }
        public InitializationState state() { return state; }
        public void markReady() { state = InitializationState.READY; }
    }

    private final Map<ProtosModuleKey, ModuleRecord> moduleCache = new HashMap<>();

    public Optional<ModuleRecord> lookup(ProtosModuleKey key) {
        return Optional.ofNullable(moduleCache.get(Objects.requireNonNull(key, "key")));
    }

    public void put(ProtosModuleKey key, ModuleRecord record) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(record, "record");
        if (moduleCache.putIfAbsent(key, record) != null) {
            throw new IllegalStateException("module cache already contains canonical key");
        }
    }

    public void removeIfSame(ProtosModuleKey key, ModuleRecord record) {
        moduleCache.remove(Objects.requireNonNull(key, "key"), Objects.requireNonNull(record, "record"));
    }
}

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
package com.guillermomolina.protos.ffm;

import com.guillermomolina.protos.spi.foreign.ProtosForeignPluginSession;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One Actor-owned session of the FFM provider.
 *
 * <p>The session owns, exclusively and without any process-wide registry: the authorized
 * libraries it opened and their {@link SymbolLookup}s, a shared {@link Arena} bounding their
 * lifetime (shared because an Actor may resume on another host thread), the cache of linked
 * bindings, the custody of owned resources, and the borrow epoch. Symbols are resolved only in
 * the authorized library through {@link SymbolLookup#libraryLookup}; never through the default or
 * loader lookups and never through a fallback chain.
 *
 * <p>Close is a cut: once it starts, no new call is admitted; calls already admitted finish
 * before owned resources are released and the arena is closed. Revocation is permanent and a
 * closed session is never reopened.
 *
 * <p>Concurrency: the session monitor {@link #lifecycle} guards only the close flag, the
 * active-call count, and the small caches; it is never held while C code runs (calls hold only
 * the active-call count). {@link #close()} releases the monitor while waiting, so a long C call
 * delays close but never deadlocks with it, and the arena is closed only after the count drops to
 * zero, so no admitted call can observe a closed arena. Without callbacks no C call can re-enter
 * Protos and request this close from inside an admitted call. The monitor is per session: only
 * Actors that actually use the provider pay for it.
 */
final class FfmSession implements ProtosForeignPluginSession {
    private final FfmLibraries.Authorization authorization;
    private final Object lifecycle = new Object();
    private final FfmHandles.Custody custody = new FfmHandles.Custody();
    private final Map<String, FfmLibraryModule> modules = new HashMap<>();
    private final Map<BindingKey, FfmBinding> bindings = new HashMap<>();

    /** Guarded by {@link #lifecycle}. */
    private boolean closing;

    /** Guarded by {@link #lifecycle}. */
    private int activeCalls;

    private volatile long borrowEpoch;

    /** Created on first library acquisition; never at session open. */
    private Arena arena;

    private Linker linker;

    private record BindingKey(String library, String symbol, FfmBinding.Signature signature) {}

    FfmSession(FfmLibraries.Authorization authorization) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    @Override
    public Object acquireModule(String canonicalTarget) {
        beginCall();
        try {
            synchronized (lifecycle) {
                FfmLibraryModule existing = modules.get(canonicalTarget);
                if (existing != null) {
                    return existing;
                }
                if (linker == null) {
                    Linker candidate = Linker.nativeLinker();
                    FfmTypes.requireSupportedPlatform(candidate);
                    linker = candidate;
                    arena = Arena.ofShared();
                }
            }
            // The loader (and the library's own initializers) run outside the monitor; the
            // active-call count keeps the arena open meanwhile.
            SymbolLookup lookup = load(authorization.resolve(canonicalTarget));
            synchronized (lifecycle) {
                FfmLibraryModule raced = modules.get(canonicalTarget);
                if (raced != null) {
                    return raced;
                }
                FfmLibraryModule module = new FfmLibraryModule(this, canonicalTarget, lookup);
                modules.put(canonicalTarget, module);
                return module;
            }
        } finally {
            endCall();
        }
    }

    private SymbolLookup load(FfmLibraries.Location location) {
        try {
            return location.path() != null
                    ? SymbolLookup.libraryLookup(Path.of(location.path()), arena)
                    : SymbolLookup.libraryLookup(location.name(), arena);
        } catch (IllegalArgumentException | IllegalCallerException unavailable) {
            throw new FfmFailure("library-unavailable", "the authorized library could not be loaded");
        }
    }

    /** Whether the session admits new operations. */
    boolean isUsable() {
        synchronized (lifecycle) {
            return !closing;
        }
    }

    long borrowEpoch() {
        return borrowEpoch;
    }

    /** Invalidates every borrowed region handed out so far; only the calling Actor mutates it. */
    void advanceBorrowEpoch() {
        borrowEpoch = borrowEpoch + 1;
    }

    /** Admits one foreign call, or fails once the close cut has started. */
    void beginCall() {
        synchronized (lifecycle) {
            if (closing) {
                throw new FfmFailure("closed-session", "the ffi session is closed");
            }
            activeCalls++;
        }
    }

    void endCall() {
        synchronized (lifecycle) {
            activeCalls--;
            if (activeCalls == 0) {
                lifecycle.notifyAll();
            }
        }
    }

    /** Resolves {@code symbol} in exactly {@code module}'s library and links it, once per key. */
    FfmBinding bind(FfmLibraryModule module, String symbol, FfmBinding.Signature signature) {
        FfmTypes.requireIdentifier(symbol);
        if (module.session() != this || !signature.belongsTo(module.library())) {
            throw new FfmFailure("invalid-declaration", "the declaration belongs to another library");
        }
        beginCall();
        try {
            BindingKey key = new BindingKey(module.library(), symbol, signature);
            synchronized (lifecycle) {
                FfmBinding cached = bindings.get(key);
                if (cached != null) {
                    return cached;
                }
            }
            MemorySegment address =
                    module.lookup()
                            .find(symbol)
                            .orElseThrow(
                                    () ->
                                            new FfmFailure(
                                                    "symbol-not-found",
                                                    "the symbol is not exported by the library"));
            FfmBinding binding =
                    new FfmBinding(this, module.library(), symbol, signature, linker, address);
            synchronized (lifecycle) {
                FfmBinding raced = bindings.putIfAbsent(key, binding);
                return raced != null ? raced : binding;
            }
        } finally {
            endCall();
        }
    }

    /**
     * Wraps one non-null native reference. An owned reference is registered in custody here,
     * before it can be published: no later adoption step exists that could fail.
     */
    FfmHandles.Resource adopt(
            FfmTypes.OpaqueType type, FfmTypes.OwnedType owned, MemorySegment address) {
        FfmHandles.Resource resource =
                new FfmHandles.Resource(this, custody.nextSerial(), type, owned, address);
        if (owned != null) {
            custody.register(resource);
        }
        return resource;
    }

    /**
     * Releases one owned resource: marks it released irreversibly, then calls its declared
     * destructor once and answers the destructor's C result unchanged.
     */
    Object release(FfmHandles.Resource resource) throws Throwable {
        if (resource.session() != this || !resource.isOwned()) {
            throw new FfmFailure("invalid-argument", "not an owned resource of this session");
        }
        beginCall();
        try {
            if (!resource.markReleased()) {
                throw new FfmFailure("released-resource", "the resource was already released");
            }
            custody.forget(resource);
            return destroy(resource);
        } finally {
            endCall();
        }
    }

    private Object destroy(FfmHandles.Resource resource) throws Throwable {
        try {
            return resource.ownedType()
                    .destructor()
                    .invokeDestructor(resource.addressForDestructor());
        } catch (Throwable failure) {
            // The native release state is unknown; it is recorded, never retried.
            resource.markUncertain();
            custody.recordUncertain();
            throw failure;
        }
    }

    int ownedCountForDiagnostics() {
        return custody.ownedCount();
    }

    int uncertainCountForDiagnostics() {
        return custody.uncertainCount();
    }

    @Override
    public void close() {
        synchronized (lifecycle) {
            if (closing) {
                return;
            }
            closing = true;
            boolean interrupted = false;
            while (activeCalls > 0) {
                try {
                    lifecycle.wait();
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        advanceBorrowEpoch();
        List<FfmHandles.Resource> pending = custody.drainForClose();
        for (FfmHandles.Resource resource : pending) {
            if (resource.markReleased()) {
                try {
                    destroy(resource);
                } catch (Throwable ignored) {
                    // Recorded as uncertain by destroy; no recovery is attempted.
                }
            }
        }
        synchronized (lifecycle) {
            modules.clear();
            bindings.clear();
        }
        if (arena != null) {
            arena.close();
        }
    }
}

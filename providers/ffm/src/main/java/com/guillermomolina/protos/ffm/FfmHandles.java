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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Opaque native references and provider-private results of one {@link FfmSession}.
 *
 * <p>Identity of every handle is its own Java object, created per logical acquisition and bound to
 * exactly one session; it never depends on a native address. The address is a private field: it
 * never appears as an Integer, a diagnostic String, a hash, or a readable member, and no Integer
 * converts to an address. A released handle stays released even when C later reuses the same
 * address for another object.
 */
final class FfmHandles {
    /**
     * Defensive private bound of one borrowed-memory copy (16 MiB). It prevents unbounded native
     * reads and Java allocations through this provider; it is not a Protos-wide rule.
     */
    static final long MAX_COPY_BYTES = 16L * 1024 * 1024;

    private FfmHandles() {}

    /** The C {@code void} result; classified as Protos null, never a Java null. */
    enum Absent {
        INSTANCE
    }

    /** Lifecycle of one opaque reference. */
    enum State {
        LIVE,
        /** Released by Protos code or by session close; the destructor ran or was attempted. */
        RELEASED,
        /** The destructor failed abnormally: the native release state is unknown. */
        UNCERTAIN
    }

    /**
     * One retained lifetime condition, observed directly on the existing capability that holds
     * it. Derived capabilities retain their source, so their liveness is the conjunction of every
     * condition along their provenance; revocation needs no registry or descendant list.
     */
    interface Provenance {
        boolean isLive();
    }

    /** An entry of session custody: an owned C resource or an independently allocated root. */
    sealed interface Owned permits Resource, Region {
        long serial();

        /** Irreversibly retires the entry; false when it was already retired. */
        boolean markReleased();
    }

    /** Opaque C reference, owned when {@link #ownedType()} is non-null. */
    static final class Resource implements Provenance, Owned {
        private final FfmSession session;
        private final long serial;
        private final FfmTypes.OpaqueType type;
        private final FfmTypes.OwnedType ownedType;
        private final MemorySegment address;
        private volatile State state = State.LIVE;

        Resource(
                FfmSession session,
                long serial,
                FfmTypes.OpaqueType type,
                FfmTypes.OwnedType ownedType,
                MemorySegment address) {
            this.session = Objects.requireNonNull(session, "session");
            this.serial = serial;
            this.type = Objects.requireNonNull(type, "type");
            this.ownedType = ownedType;
            this.address = Objects.requireNonNull(address, "address");
        }

        FfmSession session() {
            return session;
        }

        FfmTypes.OpaqueType type() {
            return type;
        }

        FfmTypes.OwnedType ownedType() {
            return ownedType;
        }

        boolean isOwned() {
            return ownedType != null;
        }

        State state() {
            return state;
        }

        @Override
        public boolean isLive() {
            return state == State.LIVE && session.isUsable();
        }

        /** The native address, only for marshalling inside one admitted call. */
        MemorySegment addressForCall() {
            if (state != State.LIVE) {
                throw new FfmFailure("released-resource", "the resource was released");
            }
            return address;
        }

        /**
         * Irreversibly marks the reference released before its destructor runs, so the destructor
         * is attempted at most once. Answers false when it was already released.
         */
        @Override
        public boolean markReleased() {
            synchronized (this) {
                if (state != State.LIVE) {
                    return false;
                }
                state = State.RELEASED;
                return true;
            }
        }

        void markUncertain() {
            state = State.UNCERTAIN;
        }

        /** The address for the destructor call that follows {@link #markReleased()}. */
        MemorySegment addressForDestructor() {
            return address;
        }

        @Override
        public long serial() {
            return serial;
        }

        @Override
        public String toString() {
            return "ffi-resource(" + type.name() + ")";
        }
    }

    /**
     * Borrowed native memory, valid only while the session's borrow epoch is unchanged.
     *
     * <p>Invalidating operations: every call of a binding not declared {@code preserving}, every
     * {@code release} (destructor call), and session close. Validity is what the declared
     * contract still accredits, not a physical liveness check: once the epoch moved the region is
     * rejected before any read, and a {@code preserving} declaration is trusted exactly like a C
     * signature, with the same safety limits when it is wrong.
     */
    static final class Borrowed {
        private final FfmSession session;
        private final long epoch;
        private final MemorySegment address;

        Borrowed(FfmSession session, long epoch, MemorySegment address) {
            this.session = Objects.requireNonNull(session, "session");
            this.epoch = epoch;
            this.address = Objects.requireNonNull(address, "address");
        }

        FfmSession session() {
            return session;
        }

        boolean isValid() {
            return session.isUsable() && session.borrowEpoch() == epoch;
        }

        /**
         * Copies exactly {@code length} octets into fresh Java memory. Callers checked validity,
         * the bound, and the session; the read is re-checked here inside the admitted call.
         */
        byte[] copy(long length) {
            if (!isValid()) {
                throw new FfmFailure("expired-borrow", "the borrowed memory is no longer valid");
            }
            if (length < 0 || length > MAX_COPY_BYTES) {
                throw new FfmFailure("invalid-length", "the copy length is out of bounds");
            }
            if (length == 0) {
                return new byte[0];
            }
            return address.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
        }

        @Override
        public String toString() {
            return "ffi-borrowed";
        }
    }

    /**
     * Typed native pointer capability of one session.
     *
     * <p>A pointer returned by C carries no readable extent and no member: it can only travel
     * back as the argument of a compatible pointer parameter. A pointer answered by {@code
     * addressOf} on a native region, or accredited by an extent assertion, additionally carries a
     * known extent (its bounded segment) and read or read/write permission, which {@code view}
     * requires; it still has no destructor and is never registered in custody.
     *
     * <p>Its admissibility intersects every retained lifetime condition: the originating session
     * is usable, the retained owner (when present) is still live, the retained borrow epoch (when
     * epoch-governed) still equals the session epoch, and the retained source capability (a
     * region or an earlier pointer, when present) is still live. Each condition is observed
     * directly on the existing session, resource, region or pointer, so revocation needs no
     * registry or traversal, and a later reuse of the same machine address never restores an
     * earlier capability. A derived pointer never replaces a condition: it adds its own.
     *
     * <p>{@link #isProtosOrigin()} marks a pointer derived only from Protos-owned allocations; it
     * cannot be accredited by a C lifetime anchor, so such an assertion never widens an
     * allocation's bounds.
     */
    static final class Pointer implements Provenance {
        /** Extent of a pointer whose readable size is not accredited. */
        static final long UNKNOWN_EXTENT = -1;

        private final FfmSession session;
        private final FfmTypes.PointerType pointee;
        private final MemorySegment address;
        private final Resource owner;
        private final boolean epochGoverned;
        private final long epoch;
        private final Provenance source;
        private final long extent;
        private final boolean writable;
        private final boolean protosOrigin;

        /** A pass-only C-origin pointer without known extent, as every C result produces. */
        Pointer(
                FfmSession session,
                FfmTypes.PointerType pointee,
                MemorySegment address,
                Resource owner,
                boolean epochGoverned,
                long epoch) {
            this(session, pointee, address, owner, epochGoverned, epoch, null, UNKNOWN_EXTENT,
                    true, false);
        }

        Pointer(
                FfmSession session,
                FfmTypes.PointerType pointee,
                MemorySegment address,
                Resource owner,
                boolean epochGoverned,
                long epoch,
                Provenance source,
                long extent,
                boolean writable,
                boolean protosOrigin) {
            this.session = Objects.requireNonNull(session, "session");
            this.pointee = Objects.requireNonNull(pointee, "pointee").identity();
            this.address = Objects.requireNonNull(address, "address");
            if (address.equals(MemorySegment.NULL)) {
                throw new IllegalArgumentException("a pointer capability is never NULL");
            }
            if (extent != UNKNOWN_EXTENT && address.byteSize() != extent) {
                throw new IllegalArgumentException("a known extent is the bounded segment size");
            }
            this.owner = owner;
            this.epochGoverned = epochGoverned;
            this.epoch = epoch;
            this.source = source;
            this.extent = extent;
            this.writable = writable;
            this.protosOrigin = protosOrigin;
        }

        FfmSession session() {
            return session;
        }

        /** The canonical plain descriptor naming the pointee. */
        FfmTypes.PointerType pointee() {
            return pointee;
        }

        /** The retained owner, or null when the pointer is not owner-backed. */
        Resource owner() {
            return owner;
        }

        boolean isEpochGoverned() {
            return epochGoverned;
        }

        long epoch() {
            return epoch;
        }

        /** The accredited readable extent, or {@link #UNKNOWN_EXTENT}. */
        long extent() {
            return extent;
        }

        boolean isWritable() {
            return writable;
        }

        boolean isProtosOrigin() {
            return protosOrigin;
        }

        @Override
        public boolean isLive() {
            return session.isUsable()
                    && (owner == null || owner.isLive())
                    && (!epochGoverned || session.borrowEpoch() == epoch)
                    && (source == null || source.isLive());
        }

        /** The native address, only for marshalling inside one admitted call. */
        MemorySegment addressForCall() {
            if (!isLive()) {
                throw new FfmFailure("expired-pointer", "the pointer is no longer valid");
            }
            return address;
        }

        /**
         * A pointer with this one's every condition plus {@code anchor}, accrediting exactly
         * {@code segment} with the intersected permission. Callers validated the range.
         */
        Pointer accredit(MemorySegment segment, Provenance anchor, boolean writeAccess) {
            return new Pointer(
                    session,
                    pointee,
                    segment,
                    owner,
                    epochGoverned,
                    epoch,
                    anchor == null ? this : new Both(this, anchor),
                    segment.byteSize(),
                    writable && writeAccess,
                    protosOrigin);
        }

        @Override
        public String toString() {
            return "ffi-pointer";
        }
    }

    /** Conjunction of two retained conditions. */
    private record Both(Provenance first, Provenance second) implements Provenance {
        @Override
        public boolean isLive() {
            return first.isLive() && second.isLive();
        }
    }

    /**
     * Bounded typed native memory of one session: an independently allocated root, or a
     * non-owning view or slice derived from a root, a region or an accredited pointer.
     *
     * <p>A region retains its exact session, a bounded {@link MemorySegment}, immutable physical
     * metadata, its effective permission and its source capability. Only a root owns an arena
     * and enters custody; a view or slice never acquires ownership and is live only while its
     * source is live. A root's release is permanent: it is marked released before its arena
     * closes, and a later allocation at the same address never revives it. Every physical access
     * goes through the bounded segment, so it cannot leave the region.
     */
    static final class Region implements Provenance, Owned {
        private final FfmSession session;
        private final long serial;
        private final Arena arena;
        private final MemorySegment segment;
        private final FfmTypes.Storage storage;
        private final boolean writable;
        private final Provenance source;
        private final boolean protosOrigin;
        private volatile State state = State.LIVE;

        /** An owned root over a fresh zeroed allocation of {@code arena}. */
        Region(
                FfmSession session,
                long serial,
                Arena arena,
                MemorySegment segment,
                FfmTypes.Storage storage) {
            this.session = Objects.requireNonNull(session, "session");
            this.serial = serial;
            this.arena = Objects.requireNonNull(arena, "arena");
            this.segment = Objects.requireNonNull(segment, "segment");
            this.storage = Objects.requireNonNull(storage, "storage");
            this.writable = true;
            this.source = null;
            this.protosOrigin = true;
        }

        private Region(
                FfmSession session,
                MemorySegment segment,
                FfmTypes.Storage storage,
                boolean writable,
                Provenance source,
                boolean protosOrigin) {
            this.session = session;
            this.serial = 0;
            this.arena = null;
            // Permission is enforced by indexed-write admission before every store.
            this.segment = segment;
            this.storage = storage;
            this.writable = writable;
            this.source = Objects.requireNonNull(source, "source");
            this.protosOrigin = protosOrigin;
            if (segment.byteSize() != storage.size()) {
                throw new IllegalArgumentException("a region spans exactly its storage");
            }
        }

        /** A borrowed typed region over the accredited extent of {@code pointer}. */
        static Region view(Pointer pointer, MemorySegment bounded, FfmTypes.Storage storage) {
            return new Region(
                    pointer.session(),
                    bounded.asSlice(0, storage.size()),
                    storage,
                    pointer.isWritable(),
                    pointer,
                    pointer.isProtosOrigin());
        }

        /**
         * A non-owning nested view at {@code offset}; callers validated the range. It retains the
         * effective lifetime source directly: this region when it is an owning root, otherwise
         * this region's own source, so successive slices and views never chain intermediates.
         */
        Region nested(long offset, FfmTypes.Storage child) {
            return new Region(
                    session, segment.asSlice(offset, child.size()), child, writable,
                    isRoot() ? this : source, protosOrigin);
        }

        FfmSession session() {
            return session;
        }

        FfmTypes.Storage storage() {
            return storage;
        }

        boolean isRoot() {
            return arena != null;
        }

        boolean isWritable() {
            return writable;
        }

        boolean isProtosOrigin() {
            return protosOrigin;
        }

        /** The bounded segment, only for access inside one admitted operation. */
        MemorySegment segment() {
            return segment;
        }

        @Override
        public boolean isLive() {
            return state == State.LIVE
                    && session.isUsable()
                    && (source == null || source.isLive());
        }

        @Override
        public long serial() {
            return serial;
        }

        @Override
        public boolean markReleased() {
            if (!isRoot()) {
                return false;
            }
            synchronized (this) {
                if (state != State.LIVE) {
                    return false;
                }
                state = State.RELEASED;
                return true;
            }
        }

        void markUncertain() {
            state = State.UNCERTAIN;
        }

        /** The owned arena, for the single physical release that follows {@link #markReleased}. */
        Arena arenaForRelease() {
            return arena;
        }

        /**
         * The offset of {@code address} inside this root's allocation when {@code length} octets
         * starting there lie entirely within it, or -1. The comparison is overflow-safe and the
         * address never leaves the provider.
         */
        long offsetWithin(MemorySegment address, long length) {
            long start = segment.address();
            long offset = address.address() - start;
            if (Long.compareUnsigned(address.address(), start) < 0
                    || length < 0
                    || offset < 0
                    || offset > segment.byteSize()
                    || length > segment.byteSize() - offset) {
                return -1;
            }
            return offset;
        }

        @Override
        public String toString() {
            return isRoot() ? "ffi-region" : "ffi-view";
        }
    }

    /** Provider-private indexable value: the exact Java elements, read by index. */
    interface Indexed {
        long size();

        Object element(long index);
    }

    /** Independent copy of borrowed memory; each element is one unsigned octet. */
    static final class Copied implements Indexed {
        private final byte[] octets;

        Copied(byte[] octets) {
            this.octets = octets;
        }

        @Override
        public long size() {
            return octets.length;
        }

        @Override
        public Object element(long index) {
            return (long) Byte.toUnsignedInt(octets[Math.toIntExact(index)]);
        }

        @Override
        public String toString() {
            return "ffi-copy";
        }
    }

    /** Ordered result of a binding with out positions: {@code [cResult, out0, out1, ...]}. */
    static final class Result implements Indexed {
        private final List<Object> values;

        Result(List<Object> values) {
            this.values = List.copyOf(values);
        }

        @Override
        public long size() {
            return values.size();
        }

        @Override
        public Object element(long index) {
            return values.get(Math.toIntExact(index));
        }

        @Override
        public String toString() {
            return "ffi-result";
        }
    }

    /**
     * Owned-resource and allocation-root custody of one session. Not shared between sessions;
     * its lock is local to the session and serializes only acquisition, release, and close of
     * that session.
     */
    static final class Custody {
        private final Map<Long, Owned> owned = new LinkedHashMap<>();
        private long nextSerial;
        private boolean closed;
        private int uncertain;

        synchronized long nextSerial() {
            return ++nextSerial;
        }

        /** Registers a fresh owned resource or allocation root before it is published. */
        synchronized void register(Owned entry) {
            if (closed) {
                // The call was admitted before close: keep custody so close still releases it.
                throw new IllegalStateException("custody closed");
            }
            owned.put(entry.serial(), entry);
        }

        synchronized void forget(Owned entry) {
            owned.remove(entry.serial());
        }

        synchronized void recordUncertain() {
            uncertain++;
        }

        synchronized int uncertainCount() {
            return uncertain;
        }

        synchronized int ownedCount() {
            return owned.size();
        }

        /** Closes custody and answers the still-owned entries, most recent first. */
        synchronized List<Owned> drainForClose() {
            closed = true;
            List<Owned> pending = new ArrayList<>(owned.values());
            owned.clear();
            java.util.Collections.reverse(pending);
            return pending;
        }
    }
}

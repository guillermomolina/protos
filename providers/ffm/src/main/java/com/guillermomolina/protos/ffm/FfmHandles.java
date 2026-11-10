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

    /** Opaque C reference, owned when {@link #ownedType()} is non-null. */
    static final class Resource {
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

        boolean isLive() {
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
        boolean markReleased() {
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

        long serial() {
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
     * Owned-resource custody of one session. Not shared between sessions; its lock is local to
     * the session and serializes only acquisition, release, and close of that session.
     */
    static final class Custody {
        private final Map<Long, Resource> owned = new LinkedHashMap<>();
        private long nextSerial;
        private boolean closed;
        private int uncertain;

        synchronized long nextSerial() {
            return ++nextSerial;
        }

        /** Registers a fresh owned resource before it is published to Protos. */
        synchronized void register(Resource resource) {
            if (closed) {
                // The call was admitted before close: keep custody so close still releases it.
                throw new IllegalStateException("custody closed");
            }
            owned.put(resource.serial(), resource);
        }

        synchronized void forget(Resource resource) {
            owned.remove(resource.serial());
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

        /** Closes custody and answers the still-owned resources, most recent first. */
        synchronized List<Resource> drainForClose() {
            closed = true;
            List<Resource> pending = new ArrayList<>(owned.values());
            owned.clear();
            java.util.Collections.reverse(pending);
            return pending;
        }
    }
}

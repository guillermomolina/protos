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

package com.guillermomolina.protos.runtime.io;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * D223-A1 host-neutral name-service backend contract of one Resolver capability.
 *
 * <p>The contract is host-only: it carries one already-admitted lowercase absolute DNS name in
 * and copied numeric address octets out. It never carries guest Protos objects, Java {@code
 * InetAddress} values, native exceptions or platform resolver codes. A backend may invoke the
 * completion on any host thread, at most once per lookup; the flow publishes the outcome in the
 * owning Actor domain and discards every late or duplicate completion.
 */
public interface ProtosResolverBackend {
    /**
     * Bound on the address descriptors one completion may carry before validation. A larger raw
     * response is a {@link FailureKind#LIMIT} failure; this bounds host response materialization
     * independently of the 64 distinct-address result bound applied by the flow.
     */
    int MAX_RAW_ADDRESSES = 256;

    /** Portable backend failure classification; maps one-to-one onto ResolutionError kinds. */
    enum FailureKind {
        NO_ADDRESS("noAddress"),
        UNAVAILABLE("unavailable"),
        DENIED("denied"),
        LIMIT("limit");

        private final String kind;

        FailureKind(String kind) {
            this.kind = kind;
        }

        /** The exact standard ResolutionError {@code kind} string. */
        public String kind() {
            return kind;
        }
    }

    /** One immutable host-only IPv4 (4 octets) or IPv6 (16 octets) address descriptor. */
    final class AddressDescriptor {
        private final byte[] octets;

        public AddressDescriptor(byte[] octets) {
            Objects.requireNonNull(octets, "octets");
            if (octets.length != 4 && octets.length != 16) {
                throw new IllegalArgumentException("address descriptor must have 4 or 16 octets");
            }
            this.octets = octets.clone();
        }

        public int version() {
            return octets.length == 4 ? 4 : 6;
        }

        /** A fresh copy of the network-order octets. */
        public byte[] octets() {
            return octets.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof AddressDescriptor descriptor
                    && Arrays.equals(octets, descriptor.octets);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(octets);
        }
    }

    /** Completion of one submitted lookup; called at most once, on any host thread. */
    interface Completion {
        /** The raw descriptors in advisory order, possibly with duplicates or none. */
        void succeeded(List<AddressDescriptor> addresses);

        void failed(FailureKind kind);
    }

    /** Best-effort cancellation of one submitted lookup; it never promises interruption. */
    @FunctionalInterface
    interface Cancellation {
        void cancel();
    }

    /**
     * Submits one lookup of an admitted lowercase absolute name (terminated by the root dot). A
     * backend that cannot admit the request reports the failure through {@code completion}, which
     * may then run synchronously on the calling thread; it never throws a native exception.
     */
    Cancellation lookup(String absoluteName, Completion completion);

    /**
     * Stops admission and retires queued requests without completing them. Lookups already
     * executing may finish; their completions are then discarded by the flow. Idempotent.
     */
    void close();
}

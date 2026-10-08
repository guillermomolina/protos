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
package com.guillermomolina.protos.execution;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Provider-supplied D188 source semantic classification of one foreign value.
 *
 * <p>The admission substrate converts only the scalar kinds and never infers a source category
 * from physical shape: a provider reports {@link Kind#INTEGER} or {@link Kind#BINARY64} only for
 * values whose source family is integral or binary floating, never because the bits happen to fit.
 * Every other value, including ambiguous null-like, decimal, rational, custom numeric, lossy, and
 * array/map-shaped values, is {@link Kind#RAW}. A raw classification also carries the provider's
 * stable identity key, if any, and the capabilities the generic projection may use.
 */
record ProtosForeignAdmissionDescriptor(
        Kind kind, Object scalar, Object stableIdentityKey, Set<Capability> capabilities) {
    enum Kind {
        BOOLEAN,
        NULL,
        STRING,
        INTEGER,
        BINARY64,
        RAW
    }

    /** Foreign capabilities consulted by the generic projection; never guest-visible. */
    enum Capability {
        EXECUTABLE,
        INSTANTIABLE,
        INDEXED_READ,
        INDEXED_WRITE,
        HASH_ENTRIES,
        /** Ordinary pull iteration is a faithful, unambiguous projection of this value. */
        ITERABLE,
        /**
         * The explicit {@code std:interop} member-read family is supported. It never admits an
         * ordinary member read, which still requires faithful projection per member.
         */
        MEMBER_READ,
        /** The explicit {@code std:interop} member-write family is supported. */
        MEMBER_WRITE
    }

    ProtosForeignAdmissionDescriptor {
        Objects.requireNonNull(kind, "kind");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        boolean validScalar =
                switch (kind) {
                    case BOOLEAN -> scalar instanceof Boolean;
                    case NULL, RAW -> scalar == null;
                    case STRING -> scalar instanceof String;
                    case INTEGER -> scalar instanceof BigInteger;
                    case BINARY64 -> scalar instanceof Double;
                };
        if (!validScalar) {
            throw new IllegalArgumentException("invalid scalar for foreign kind " + kind);
        }
        if (kind != Kind.RAW && (stableIdentityKey != null || !capabilities.isEmpty())) {
            throw new IllegalArgumentException("converted foreign scalars carry no raw metadata");
        }
    }

    static ProtosForeignAdmissionDescriptor booleanValue(boolean value) {
        return new ProtosForeignAdmissionDescriptor(Kind.BOOLEAN, value, null, Set.of());
    }

    /** A true foreign absence/null; never an ambiguous null-like value such as JS undefined. */
    static ProtosForeignAdmissionDescriptor absent() {
        return new ProtosForeignAdmissionDescriptor(Kind.NULL, null, null, Set.of());
    }

    /** Text; it converts only when it is a valid Unicode scalar sequence. */
    static ProtosForeignAdmissionDescriptor text(String value) {
        return new ProtosForeignAdmissionDescriptor(Kind.STRING, value, null, Set.of());
    }

    static ProtosForeignAdmissionDescriptor integral(BigInteger value) {
        return new ProtosForeignAdmissionDescriptor(Kind.INTEGER, value, null, Set.of());
    }

    /** A source binary floating value exactly representable as binary64. */
    static ProtosForeignAdmissionDescriptor binary64(double value) {
        return new ProtosForeignAdmissionDescriptor(Kind.BINARY64, value, null, Set.of());
    }

    /**
     * @param stableIdentityKey provider-defined key whose equality is the stable foreign identity,
     *     or null when the provider has none
     */
    static ProtosForeignAdmissionDescriptor raw(
            Object stableIdentityKey, Set<Capability> capabilities) {
        return new ProtosForeignAdmissionDescriptor(
                Kind.RAW, null, stableIdentityKey, capabilities);
    }

    static ProtosForeignAdmissionDescriptor opaque() {
        return raw(null, EnumSet.noneOf(Capability.class));
    }

    boolean has(Capability capability) {
        return capabilities.contains(capability);
    }
}

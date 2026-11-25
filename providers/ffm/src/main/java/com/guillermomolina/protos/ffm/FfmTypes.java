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

import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, typed ABI descriptors of the I144 R1 subset and their exact conversions.
 *
 * <p>Only the Linux x86-64 LP64 System V ABI is supported: {@code i8}, {@code i16}, {@code i32}
 * and {@code i64} are signed integers of 8, 16, 32 and 64 bits; {@code u8}, {@code u16}, {@code
 * u32}, {@code u64} and {@code sizeT} are unsigned integers of 8, 16, 32, 64 and 64 bits; {@code
 * f32} is IEEE binary32 and {@code f64} IEEE binary64. Signedness is semantic metadata: a signed
 * and an unsigned descriptor of one width share one physical Java carrier, which holds the
 * two's-complement bit pattern of the exact value. {@link #requireSupportedPlatform(Linker)} checks the platform and the native
 * linker's canonical C layouts instead of assuming that C {@code long}, pointers, and {@code
 * size_t} share a size everywhere.
 *
 * <p>No descriptor carries a native address. Every conversion is exact: an out-of-range Integer
 * is rejected, never truncated or wrapped.
 */
final class FfmTypes {
    /** Bounds a logical opaque-type name or symbol name; neither is ever a path. */
    static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");

    static final BigInteger SIZE_T_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    private FfmTypes() {}

    /** One ABI descriptor. Implementations are immutable and never expose an address. */
    sealed interface Type
            permits Scalar, OpaqueType, OwnedType, BorrowedType, PointerType, OutType, InputType,
                    InputLength, CopyDestructor, Remainder, Storage {
        /** The descriptor's spelling, safe for diagnostics; never contains an address. */
        String spelling();
    }

    /** Scalar descriptors, including {@code void} and NUL-terminated UTF-8 input text. */
    enum Scalar implements Type {
        VOID("void"),
        I8("i8"),
        U8("u8"),
        I16("i16"),
        U16("u16"),
        I32("i32"),
        U32("u32"),
        I64("i64"),
        U64("u64"),
        F32("f32"),
        F64("f64"),
        SIZE_T("sizeT"),
        UTF8Z("utf8z");

        private final String spelling;

        Scalar(String spelling) {
            this.spelling = spelling;
        }

        @Override
        public String spelling() {
            return spelling;
        }
    }

    /** Opaque C reference of logical type {@code name} originating in library {@code library}. */
    record OpaqueType(String name, String library, boolean nullable) implements Type {
        OpaqueType {
            requireIdentifier(name);
            Objects.requireNonNull(library, "library");
        }

        OpaqueType asNullable() {
            return new OpaqueType(name, library, true);
        }

        @Override
        public String spelling() {
            return (nullable ? "nullable(" : "opaque(") + name + ")";
        }
    }

    /**
     * Owned opaque reference: a non-null result in this position is registered in the session's
     * custody before it is published and is released exactly once through its declared
     * destructor. Identity is the descriptor object itself: the destructor is a session-bound
     * native binding, so two declarations are distinct descriptors.
     */
    static final class OwnedType implements Type {
        private final OpaqueType opaque;
        private final String destructorSymbol;
        private final FfmBinding destructor;

        OwnedType(OpaqueType opaque, String destructorSymbol, FfmBinding destructor) {
            this.opaque = Objects.requireNonNull(opaque, "opaque");
            this.destructorSymbol = requireIdentifier(destructorSymbol);
            this.destructor = Objects.requireNonNull(destructor, "destructor");
        }

        OpaqueType opaque() {
            return opaque;
        }

        String destructorSymbol() {
            return destructorSymbol;
        }

        FfmBinding destructor() {
            return destructor;
        }

        @Override
        public String spelling() {
            return "owned(" + opaque.name() + ", " + destructorSymbol + ")";
        }

        @Override
        public String toString() {
            return spelling();
        }
    }

    /** Borrowed native memory: readable only through the bounded copy while still valid. */
    enum BorrowedType implements Type {
        INSTANCE;

        @Override
        public String spelling() {
            return "borrowed";
        }
    }

    /**
     * Typed native data pointer declaration: plain, nullable, or borrowed.
     *
     * <p>A plain instance is the canonical pointee identity: the declaring library associates one
     * plain instance with each exact pointee layout, and its nullable and borrowed variants,
     * created once with it, retain that same identity. Identity is the Java object itself, never
     * a number or an address. The descriptor carries no field definitions, layout or extent; it is
     * library-neutral within one session. A pointer passes as {@link ValueLayout#ADDRESS} and
     * a result in this position becomes a pass-only {@link FfmHandles.Pointer}. The borrowed
     * variant is non-nullable, admitted only as a result or {@code out} target, and its results
     * are governed by the session borrow epoch. Distinct from the private {@link BorrowedType}.
     */
    static final class PointerType implements Type {
        private final PointerType identity;
        private final boolean voidPointee;
        private final boolean nullable;
        private final boolean borrowed;
        private final PointerType nullableVariant;
        private final PointerType borrowedVariant;

        /** A fresh plain descriptor, and therefore a fresh pointee identity. */
        PointerType(boolean voidPointee) {
            this.identity = this;
            this.voidPointee = voidPointee;
            this.nullable = false;
            this.borrowed = false;
            this.nullableVariant = new PointerType(this, true, false);
            this.borrowedVariant = new PointerType(this, false, true);
        }

        private PointerType(PointerType plain, boolean nullable, boolean borrowed) {
            this.identity = plain;
            this.voidPointee = plain.voidPointee;
            this.nullable = nullable;
            this.borrowed = borrowed;
            this.nullableVariant = null;
            this.borrowedVariant = null;
        }

        /** The canonical plain descriptor naming this pointer's pointee. */
        PointerType identity() {
            return identity;
        }

        boolean isPlain() {
            return identity == this;
        }

        boolean voidPointee() {
            return voidPointee;
        }

        boolean nullable() {
            return nullable;
        }

        boolean borrowed() {
            return borrowed;
        }

        /** The nullable variant of a plain descriptor. */
        PointerType asNullable() {
            if (!isPlain()) {
                throw new IllegalArgumentException("not a plain pointer descriptor");
            }
            return nullableVariant;
        }

        /** The borrowed result variant of a plain descriptor. */
        PointerType asBorrowed() {
            if (!isPlain()) {
                throw new IllegalArgumentException("not a plain pointer descriptor");
            }
            return borrowedVariant;
        }

        @Override
        public String spelling() {
            String pointee = voidPointee ? "void" : "data";
            if (nullable) {
                return "nullablePointer(" + pointee + ")";
            }
            return (borrowed ? "borrowedPointer(" : "pointer(") + pointee + ")";
        }

        @Override
        public String toString() {
            return spelling();
        }
    }

    /**
     * Immutable physical access metadata of one storage layout of native memory.
     *
     * <p>Every size, alignment, member name, member offset, array count and array stride is
     * supplied by the Protos {@code std:ffi} module, which alone computes C layouts. This class
     * only retains those values and checks their physical consistency defensively (positive
     * sizes, power-of-two alignments, checked extents, aligned members, non-overlapping struct
     * members, union members at offset zero, sufficient array strides); it never computes
     * padding, member order or a replacement layout. One generic representation serves every
     * declaration: scalar and pointer leaves reuse {@link Scalar} and {@link PointerType}.
     *
     * <p>{@link #identity()} is the canonical plain {@link PointerType} whose pointee is this
     * layout, or null for the provider-private byte view of a slice. Metadata is composed one
     * member at a time ({@link #withMember}); each step answers a fresh immutable instance.
     *
     * <p>A complete struct or union instance is also a call descriptor ({@link #isCallGroup}):
     * it passes and returns by value with the physical group layout projected from these same
     * values ({@link #layout}). Identity stays the Java object itself; two separately declared
     * layouts with equal members are distinct descriptors even when their physical layouts, and
     * so their native registrations, coincide.
     */
    static final class Storage implements Type {
        enum Kind {
            SCALAR,
            POINTER,
            STRUCT,
            UNION,
            ARRAY
        }

        /** One named member: its supplied offset and immutable child metadata. */
        record Member(String name, long offset, Storage layout) {}

        /** The element of every byte view: one unsigned octet. */
        static final Storage BYTE =
                new Storage(Kind.SCALAR, 1, 1, Scalar.U8, null, List.of(), null, 0, 0, null);

        private final Kind kind;
        private final long size;
        private final long align;
        private final Scalar scalar;
        private final PointerType pointer;
        private final List<Member> members;
        private final Map<String, Member> byName;
        private final Storage element;
        private final long count;
        private final long stride;
        private final PointerType identity;

        private Storage(
                Kind kind,
                long size,
                long align,
                Scalar scalar,
                PointerType pointer,
                List<Member> members,
                Storage element,
                long count,
                long stride,
                PointerType identity) {
            // Only the provider-private empty byte view may be zero-sized; every public
            // storage layout, and so every allocation, stays positive-sized.
            boolean emptyBytes =
                    kind == Kind.ARRAY
                            && identity == null
                            && element == BYTE
                            && count == 0
                            && stride == 1
                            && align == 1
                            && size == 0;
            if ((size <= 0 && !emptyBytes)
                    || align <= 0
                    || Long.bitCount(align) != 1
                    || size % align != 0) {
                throw new IllegalArgumentException("invalid storage size or alignment");
            }
            if (identity != null && (!identity.isPlain() || identity.voidPointee())) {
                throw new IllegalArgumentException("invalid storage identity");
            }
            this.kind = kind;
            this.size = size;
            this.align = align;
            this.scalar = scalar;
            this.pointer = pointer;
            this.members = List.copyOf(members);
            Map<String, Member> names = new HashMap<>();
            for (Member member : this.members) {
                names.put(member.name(), member);
            }
            this.byName = Map.copyOf(names);
            this.element = element;
            this.count = count;
            this.stride = stride;
            this.identity = identity;
        }

        /** A scalar leaf whose supplied size and alignment equal the native carrier's. */
        static Storage scalar(Scalar scalar, long size, long align, PointerType identity) {
            if (!isNumericScalar(scalar)) {
                throw new IllegalArgumentException("not a storage scalar");
            }
            MemoryLayout carrier = layout(scalar);
            if (carrier.byteSize() != size || carrier.byteAlignment() != align) {
                throw new IllegalArgumentException("scalar storage does not match its carrier");
            }
            return new Storage(
                    Kind.SCALAR, size, align, scalar, null, List.of(), null, 0, 0, identity);
        }

        /** A pointer leaf of a plain or nullable typed pointer declaration. */
        static Storage pointer(PointerType declared, PointerType identity) {
            if (declared.borrowed()) {
                throw new IllegalArgumentException("a borrowed pointer is not storage");
            }
            long bytes = ValueLayout.ADDRESS.byteSize();
            return new Storage(
                    Kind.POINTER, bytes, ValueLayout.ADDRESS.byteAlignment(), null, declared,
                    List.of(), null, 0, 0, identity);
        }

        /** An aggregate with its supplied size and alignment and, initially, no member. */
        static Storage aggregate(String kind, long size, long align, PointerType identity) {
            Kind aggregate =
                    switch (kind) {
                        case "struct" -> Kind.STRUCT;
                        case "union" -> Kind.UNION;
                        default -> throw new IllegalArgumentException("not an aggregate kind");
                    };
            return new Storage(
                    aggregate, size, align, null, null, List.of(), null, 0, 0, identity);
        }

        /** A fixed array with its supplied count and stride. */
        static Storage array(Storage element, long count, long stride, PointerType identity) {
            if (count <= 0 || stride < element.size || stride % element.align != 0) {
                throw new IllegalArgumentException("invalid array stride or count");
            }
            long bytes = Math.multiplyExact(count, stride);
            return new Storage(
                    Kind.ARRAY, bytes, element.align, null, null, List.of(), element, count,
                    stride, identity);
        }

        /**
         * The provider-private octet view of a slice; it names no pointee layout. A zero length
         * is the empty view, which has no admissible index.
         */
        static Storage bytes(long length) {
            return new Storage(Kind.ARRAY, length, 1, null, null, List.of(), BYTE, length, 1, null);
        }

        /**
         * This aggregate extended by one member at its supplied offset: the member must be
         * aligned, lie inside the supplied size, carry a fresh name, and either follow every
         * earlier struct member without overlap or, in a union, start at offset zero.
         */
        Storage withMember(String name, long offset, Storage child) {
            if (kind != Kind.STRUCT && kind != Kind.UNION) {
                throw new IllegalArgumentException("not an aggregate");
            }
            if (name == null
                    || byName.containsKey(name)
                    || offset < 0
                    || offset % child.align != 0
                    || child.align > align
                    || Math.addExact(offset, child.size) > size) {
                throw new IllegalArgumentException("invalid member placement");
            }
            if (kind == Kind.UNION && offset != 0) {
                throw new IllegalArgumentException("a union member starts at offset zero");
            }
            if (kind == Kind.STRUCT && !members.isEmpty()) {
                Member last = members.get(members.size() - 1);
                if (offset < Math.addExact(last.offset(), last.layout().size)) {
                    throw new IllegalArgumentException("struct members overlap");
                }
            }
            List<Member> extended = new ArrayList<>(members);
            extended.add(new Member(name, offset, child));
            return new Storage(kind, size, align, null, null, extended, null, 0, 0, identity);
        }

        Kind kind() {
            return kind;
        }

        long size() {
            return size;
        }

        long align() {
            return align;
        }

        Scalar scalar() {
            return scalar;
        }

        PointerType pointer() {
            return pointer;
        }

        Member member(String name) {
            return byName.get(name);
        }

        /** The members in their supplied order; empty for leaves and arrays. */
        List<Member> members() {
            return members;
        }

        Storage element() {
            return element;
        }

        long count() {
            return count;
        }

        long stride() {
            return stride;
        }

        PointerType identity() {
            return identity;
        }

        boolean isAggregate() {
            return kind == Kind.STRUCT || kind == Kind.UNION || kind == Kind.ARRAY;
        }

        @Override
        public String spelling() {
            return kind.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return "ffi-storage(" + spelling() + ")";
        }
    }

    /**
     * Private length-carrying input region (LIB030). The argument is a Protos String; the callee
     * receives a pointer to a fresh, non-NULL per-call copy of exactly its octets, followed by
     * one NUL that is never counted. {@link #TEXT} uses strict UTF-8 and admits interior NUL;
     * {@link #OCTETS} is a lossless octet transport in which every character is U+0000..U+00FF
     * and denotes exactly one octet (the Latin-1 bijection), so no character interpretation is
     * applied to binary data. Admission bounds the octet length by {@link #MAX_INPUT_BYTES}
     * (the C {@code int} length domain, leaving room for the guard NUL) before native entry;
     * smaller application policies belong to the consuming library. These are provider-private
     * members, not public std:ffi descriptors.
     */
    enum InputType implements Type {
        TEXT("text"),
        OCTETS("octets");

        private final String spelling;

        InputType(String spelling) {
            this.spelling = spelling;
        }

        @Override
        public String spelling() {
            return spelling;
        }
    }

    /**
     * C {@code int} parameter that consumes no Protos argument: the exact octet length of the
     * nearest preceding {@link InputType} parameter of the same call. The length is therefore
     * never supplied by Protos and can never exceed the marshalled region.
     */
    enum InputLength implements Type {
        INSTANCE;

        @Override
        public String spelling() {
            return "inputLength";
        }
    }

    /**
     * Destructor-pointer parameter that consumes no Protos argument and always passes the C
     * destructor sentinel {@code (void (*)(void *)) -1}, by which the callee is instructed to
     * make its own private copy of the preceding input before returning (for SQLite, {@code
     * SQLITE_TRANSIENT}). The constant is ABI-correct as a function-pointer argument, is never
     * dereferenced by the provider, is never derived from a Protos value and is never published.
     * Together with the per-call arena this is the only custody it implies: no input memory
     * outlives the call.
     */
    enum CopyDestructor implements Type {
        INSTANCE;

        static final long SENTINEL = -1L;

        @Override
        public String spelling() {
            return "copyDestructor";
        }
    }

    /**
     * Out-only target describing a {@code const char **} tail written by the callee into the
     * nearest preceding text input ({@code utf8z} or {@link InputType#TEXT}). The pointer is
     * converted, while the per-call input is still alive, into the exact number of unconsumed
     * octets (excluding the terminating NUL); native NULL becomes absent and a pointer outside
     * the input is a provider failure. No address is retained or published.
     */
    enum Remainder implements Type {
        INSTANCE;

        @Override
        public String spelling() {
            return "remainder";
        }
    }

    /**
     * Generic bound of one private input region: its length must fit the C {@code int} passed by
     * {@link InputLength}, and the region plus its guard NUL must fit one Java array.
     */
    static final long MAX_INPUT_BYTES = Integer.MAX_VALUE - 8L;

    /** Whether a parameter is supplied by the provider rather than by a Protos argument. */
    static boolean consumesArgument(Type type) {
        return !(type instanceof OutType
                || type == InputLength.INSTANCE
                || type == CopyDestructor.INSTANCE);
    }

    /** Whether {@code type} is a text input to which an {@code out(remainder)} may refer. */
    static boolean isTextInput(Type type) {
        return type == Scalar.UTF8Z || type == InputType.TEXT;
    }

    /** Output position: consumes no Protos argument; its zeroed slot is read after the call. */
    record OutType(Type target) implements Type {
        OutType {
            if (!isOutTarget(target)) {
                throw new IllegalArgumentException("unsupported out target");
            }
        }

        @Override
        public String spelling() {
            return "out(" + target.spelling() + ")";
        }
    }

    static boolean isOutTarget(Type type) {
        return isNumericScalar(type)
                || type instanceof OpaqueType
                || type instanceof OwnedType
                || type == BorrowedType.INSTANCE
                || type instanceof PointerType
                || type == Remainder.INSTANCE
                || isCallGroup(type);
    }

    /**
     * Whether {@code type} is a complete public struct or union: a by-value argument or result
     * and an {@code out} target. A fixed array, a scalar or pointer leaf, the private byte view
     * and an aggregate still without members are not. Pure: no session, allocation or C call.
     */
    static boolean isCallGroup(Type type) {
        return type instanceof Storage storage
                && (storage.kind() == Storage.Kind.STRUCT
                        || storage.kind() == Storage.Kind.UNION)
                && storage.identity() != null
                && !storage.members().isEmpty()
                && storage.size() > 0
                && storage.align() > 0;
    }

    static boolean isReturnType(Type type) {
        return type == Scalar.VOID
                || (type != Scalar.UTF8Z && type != Remainder.INSTANCE && isOutTarget(type));
    }

    static boolean isParameterType(Type type) {
        return isNumericScalar(type)
                || type == Scalar.UTF8Z
                || type instanceof OpaqueType
                || (type instanceof PointerType pointer && !pointer.borrowed())
                || type instanceof OutType
                || type instanceof InputType
                || type == InputLength.INSTANCE
                || type == CopyDestructor.INSTANCE
                || isCallGroup(type);
    }

    /** Whether {@code type} is a fixed-width integer or floating-point scalar. */
    static boolean isNumericScalar(Type type) {
        return type instanceof Scalar scalar && scalar != Scalar.VOID && scalar != Scalar.UTF8Z;
    }

    /**
     * The library whose handles this descriptor admits, or null for a library-neutral one; typed
     * pointer descriptors are library-neutral within their session.
     */
    static String libraryOf(Type type) {
        return switch (type) {
            case OpaqueType opaque -> opaque.library();
            case OwnedType owned -> owned.opaque().library();
            case OutType out -> libraryOf(out.target());
            default -> null;
        };
    }

    /** The native layout of one non-void descriptor. */
    static MemoryLayout layout(Type type) {
        return switch (type) {
            case Scalar scalar ->
                    switch (scalar) {
                        case I8, U8 -> ValueLayout.JAVA_BYTE;
                        case I16, U16 -> ValueLayout.JAVA_SHORT;
                        case I32, U32 -> ValueLayout.JAVA_INT;
                        case I64, U64, SIZE_T -> ValueLayout.JAVA_LONG;
                        case F32 -> ValueLayout.JAVA_FLOAT;
                        case F64 -> ValueLayout.JAVA_DOUBLE;
                        case UTF8Z -> ValueLayout.ADDRESS;
                        case VOID -> throw new IllegalArgumentException("void has no layout");
                    };
            case OpaqueType ignored -> ValueLayout.ADDRESS;
            case OwnedType ignored -> ValueLayout.ADDRESS;
            case BorrowedType ignored -> ValueLayout.ADDRESS;
            case PointerType ignored -> ValueLayout.ADDRESS;
            case OutType ignored -> ValueLayout.ADDRESS;
            case InputType ignored -> ValueLayout.ADDRESS;
            case InputLength ignored -> ValueLayout.JAVA_INT;
            case CopyDestructor ignored -> ValueLayout.ADDRESS;
            case Remainder ignored -> ValueLayout.ADDRESS;
            case Storage storage -> physicalLayout(storage);
        };
    }

    /**
     * The physical layout of existing storage metadata, built with the standard layout
     * factories: a struct reproduces the supplied member offsets and total size with explicit
     * padding, a union overlaps its members at offset zero, and a fixed array is a sequence of
     * its element. Nothing is computed here: every offset, size and alignment comes from the metadata, and a
     * projection whose size or alignment differs from it is rejected. Layouts are unnamed, so
     * public field names never reach a native registration.
     */
    private static MemoryLayout physicalLayout(Storage storage) {
        MemoryLayout projected =
                switch (storage.kind()) {
                    case SCALAR -> layout(storage.scalar());
                    // The bare address carrier: no target layout, so no pointee extent.
                    case POINTER -> ValueLayout.ADDRESS;
                    case ARRAY -> {
                        MemoryLayout element = physicalLayout(storage.element());
                        if (element.byteSize() != storage.stride()) {
                            throw new IllegalArgumentException("array stride is not its size");
                        }
                        yield MemoryLayout.sequenceLayout(storage.count(), element);
                    }
                    case STRUCT -> {
                        List<MemoryLayout> parts = new ArrayList<>();
                        long cursor = 0;
                        for (Storage.Member member : storage.members()) {
                            long gap = member.offset() - cursor;
                            if (gap > 0) {
                                parts.add(MemoryLayout.paddingLayout(gap));
                            }
                            MemoryLayout child = physicalLayout(member.layout());
                            parts.add(child);
                            cursor = Math.addExact(member.offset(), child.byteSize());
                        }
                        if (storage.size() > cursor) {
                            parts.add(MemoryLayout.paddingLayout(storage.size() - cursor));
                        }
                        yield MemoryLayout.structLayout(parts.toArray(MemoryLayout[]::new));
                    }
                    case UNION -> {
                        List<MemoryLayout> parts = new ArrayList<>();
                        long widest = 0;
                        for (Storage.Member member : storage.members()) {
                            MemoryLayout child = physicalLayout(member.layout());
                            parts.add(child);
                            widest = Math.max(widest, child.byteSize());
                        }
                        // Union padding overlaps the members: it spans the whole rounded size.
                        if (widest < storage.size()) {
                            parts.add(MemoryLayout.paddingLayout(storage.size()));
                        }
                        yield MemoryLayout.unionLayout(parts.toArray(MemoryLayout[]::new));
                    }
                };
        if (projected.byteSize() != storage.size()
                || projected.byteAlignment() != storage.align()) {
            throw new IllegalArgumentException("the physical layout differs from its storage");
        }
        return projected;
    }

    static String requireIdentifier(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid C identifier");
        }
        return name;
    }

    static boolean isIdentifier(Object name) {
        return name instanceof String text && IDENTIFIER.matcher(text).matches();
    }

    /**
     * Rejects every platform but Linux x86-64 LP64. Runs only on first real use, never at
     * provider registration.
     */
    static void requireSupportedPlatform(Linker linker) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!os.equals("linux") || !(arch.equals("amd64") || arch.equals("x86_64"))) {
            throw new FfmFailure("unsupported-platform", "the ffi provider supports Linux x86-64 only");
        }
        var canonical = linker.canonicalLayouts();
        if (byteSize(canonical.get("int")) != 4
                || byteSize(canonical.get("long")) != 8
                || byteSize(canonical.get("long long")) != 8
                || byteSize(canonical.get("size_t")) != 8
                || byteSize(canonical.get("double")) != 8
                || ValueLayout.ADDRESS.byteSize() != 8) {
            throw new FfmFailure("unsupported-platform", "the native ABI is not LP64");
        }
    }

    private static long byteSize(MemoryLayout layout) {
        return layout == null ? -1 : layout.byteSize();
    }

    // ---- exact outbound scalar conversions; each answers null when the value is not admitted

    /** Exact Protos Integer as a BigInteger, or null when {@code value} is not an Integer. */
    static BigInteger exactInteger(Object value) {
        if (value instanceof Long small) {
            return BigInteger.valueOf(small);
        }
        if (value instanceof BigInteger large) {
            return large;
        }
        return null;
    }

    /** Whether {@code value} is an exact Integer of the signed {@code bits}-bit range. */
    private static boolean admitsSigned(Object value, int bits) {
        BigInteger exact = exactInteger(value);
        return exact != null && exact.bitLength() < bits;
    }

    /**
     * Whether {@code value} is an exact Integer of the complete unsigned {@code bits}-bit range,
     * checked before any conversion to a physical two's-complement carrier.
     */
    private static boolean admitsUnsigned(Object value, int bits) {
        BigInteger exact = exactInteger(value);
        return exact != null && exact.signum() >= 0 && exact.bitLength() <= bits;
    }

    /** Whether {@code value} is admitted for scalar descriptor {@code scalar}. */
    static boolean admitsScalar(Scalar scalar, Object value) {
        return switch (scalar) {
            case I8 -> admitsSigned(value, Byte.SIZE);
            case I16 -> admitsSigned(value, Short.SIZE);
            case I32 -> admitsSigned(value, Integer.SIZE);
            case I64 -> admitsSigned(value, Long.SIZE);
            case U8 -> admitsUnsigned(value, Byte.SIZE);
            case U16 -> admitsUnsigned(value, Short.SIZE);
            case U32 -> admitsUnsigned(value, Integer.SIZE);
            case U64 -> admitsUnsigned(value, Long.SIZE);
            case SIZE_T -> {
                BigInteger exact = exactInteger(value);
                yield exact != null && exact.signum() >= 0 && exact.compareTo(SIZE_T_MAX) <= 0;
            }
            // Never an Integer coerced into a floating-point carrier.
            case F32, F64 -> value instanceof Double;
            case UTF8Z -> value instanceof String text && isEncodable(text);
            case VOID -> false;
        };
    }

    /** The native carrier of an admitted scalar argument; callers validated it first. */
    static Object nativeScalar(Scalar scalar, Object value) {
        if (!admitsScalar(scalar, value)) {
            throw new FfmFailure("invalid-argument", "argument does not match " + scalar.spelling());
        }
        return switch (scalar) {
            case I8 -> Byte.valueOf(exactInteger(value).byteValueExact());
            case I16 -> Short.valueOf(exactInteger(value).shortValueExact());
            case I32 -> Integer.valueOf(exactInteger(value).intValueExact());
            case I64 -> Long.valueOf(exactInteger(value).longValueExact());
            // Two's-complement bit pattern of the exact, already admitted unsigned value; the
            // narrowing never wraps an input because the full unsigned range was checked first.
            case U8 -> Byte.valueOf((byte) exactInteger(value).intValue());
            case U16 -> Short.valueOf((short) exactInteger(value).intValue());
            case U32 -> Integer.valueOf(exactInteger(value).intValue());
            case U64, SIZE_T -> Long.valueOf(exactInteger(value).longValue());
            // One IEEE binary32 rounding of the semantic binary64 value (round to nearest even,
            // signed zero, subnormals, overflow to infinity and NaN preserved by Java).
            case F32 -> Float.valueOf((float) ((Double) value).doubleValue());
            case F64 -> value;
            case UTF8Z, VOID -> throw new IllegalStateException("not a direct scalar");
        };
    }

    /** Protos-facing value of one unsigned 64-bit {@code size_t} carrier. */
    static Object unsignedSize(long bits) {
        if (bits >= 0) {
            return bits;
        }
        return new BigInteger(Long.toUnsignedString(bits));
    }

    /**
     * Exact octet length of one admitted private input, or -1 when {@code value} is not
     * admitted: not a String, an unpaired surrogate for {@link InputType#TEXT}, a character above
     * U+00FF for {@link InputType#OCTETS}, or longer than {@link #MAX_INPUT_BYTES}. Pure and
     * allocation-free, so repeated admission does not copy the input.
     */
    static long inputLength(InputType type, Object value) {
        if (!(value instanceof String text)) {
            return -1;
        }
        long count = 0;
        int length = text.length();
        for (int index = 0; index < length; index++) {
            char unit = text.charAt(index);
            if (type == InputType.OCTETS) {
                if (unit > 0xFF) {
                    return -1;
                }
                count++;
            } else if (unit < 0x80) {
                count++;
            } else if (unit < 0x800) {
                count += 2;
            } else if (Character.isHighSurrogate(unit)) {
                if (index + 1 >= length || !Character.isLowSurrogate(text.charAt(index + 1))) {
                    return -1;
                }
                index++;
                count += 4;
            } else if (Character.isLowSurrogate(unit)) {
                return -1;
            } else {
                count += 3;
            }
            if (count > MAX_INPUT_BYTES) {
                return -1;
            }
        }
        return count;
    }

    /** Exact octets of an input already admitted by {@link #inputLength}. */
    static byte[] inputOctets(InputType type, String text) {
        if (type == InputType.TEXT) {
            byte[] octets = strictUtf8(text);
            if (octets == null) {
                throw new FfmFailure("invalid-argument", "text is not valid Unicode");
            }
            return octets;
        }
        byte[] octets = new byte[text.length()];
        for (int index = 0; index < octets.length; index++) {
            octets[index] = (byte) text.charAt(index);
        }
        return octets;
    }

    /** Whether {@code text} is valid Unicode without an interior NUL, so strict UTF-8 applies. */
    static boolean isEncodable(String text) {
        return text.indexOf('\0') < 0 && strictUtf8(text) != null;
    }

    /** Strict UTF-8 octets of {@code text}, or null when it holds an unpaired surrogate. */
    static byte[] strictUtf8(String text) {
        try {
            java.nio.ByteBuffer encoded =
                    java.nio.charset.StandardCharsets.UTF_8
                            .newEncoder()
                            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                            .encode(java.nio.CharBuffer.wrap(text));
            byte[] octets = new byte[encoded.remaining()];
            encoded.get(octets);
            return octets;
        } catch (java.nio.charset.CharacterCodingException invalid) {
            return null;
        }
    }
}

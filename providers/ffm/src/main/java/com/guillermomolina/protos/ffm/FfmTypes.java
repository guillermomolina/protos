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
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, typed ABI descriptors of the I144 R1 subset and their exact conversions.
 *
 * <p>Only the Linux x86-64 LP64 System V ABI is supported: {@code i32} is a signed 32-bit integer,
 * {@code i64} a signed 64-bit integer, {@code sizeT} an unsigned 64-bit integer, and {@code f64}
 * IEEE binary64. {@link #requireSupportedPlatform(Linker)} checks the platform and the native
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
            permits Scalar, OpaqueType, OwnedType, BorrowedType, OutType, InputType, InputLength,
                    CopyDestructor, Remainder {
        /** The descriptor's spelling, safe for diagnostics; never contains an address. */
        String spelling();
    }

    /** Scalar descriptors, including {@code void} and NUL-terminated UTF-8 input text. */
    enum Scalar implements Type {
        VOID("void"),
        I32("i32"),
        I64("i64"),
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
        return type == Scalar.I32
                || type == Scalar.I64
                || type == Scalar.F64
                || type == Scalar.SIZE_T
                || type instanceof OpaqueType
                || type instanceof OwnedType
                || type == BorrowedType.INSTANCE
                || type == Remainder.INSTANCE;
    }

    static boolean isReturnType(Type type) {
        return type == Scalar.VOID
                || (type != Scalar.UTF8Z && type != Remainder.INSTANCE && isOutTarget(type));
    }

    static boolean isParameterType(Type type) {
        return type == Scalar.I32
                || type == Scalar.I64
                || type == Scalar.F64
                || type == Scalar.SIZE_T
                || type == Scalar.UTF8Z
                || type instanceof OpaqueType
                || type instanceof OutType
                || type instanceof InputType
                || type == InputLength.INSTANCE
                || type == CopyDestructor.INSTANCE;
    }

    /** The library whose handles this descriptor admits, or null for a library-neutral one. */
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
                        case I32 -> ValueLayout.JAVA_INT;
                        case I64, SIZE_T -> ValueLayout.JAVA_LONG;
                        case F64 -> ValueLayout.JAVA_DOUBLE;
                        case UTF8Z -> ValueLayout.ADDRESS;
                        case VOID -> throw new IllegalArgumentException("void has no layout");
                    };
            case OpaqueType ignored -> ValueLayout.ADDRESS;
            case OwnedType ignored -> ValueLayout.ADDRESS;
            case BorrowedType ignored -> ValueLayout.ADDRESS;
            case OutType ignored -> ValueLayout.ADDRESS;
            case InputType ignored -> ValueLayout.ADDRESS;
            case InputLength ignored -> ValueLayout.JAVA_INT;
            case CopyDestructor ignored -> ValueLayout.ADDRESS;
            case Remainder ignored -> ValueLayout.ADDRESS;
        };
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

    /** Whether {@code value} is admitted for scalar descriptor {@code scalar}. */
    static boolean admitsScalar(Scalar scalar, Object value) {
        return switch (scalar) {
            case I32 -> {
                BigInteger exact = exactInteger(value);
                yield exact != null
                        && exact.bitLength() < Integer.SIZE;
            }
            case I64 -> {
                BigInteger exact = exactInteger(value);
                yield exact != null && exact.bitLength() < Long.SIZE;
            }
            case SIZE_T -> {
                BigInteger exact = exactInteger(value);
                yield exact != null && exact.signum() >= 0 && exact.compareTo(SIZE_T_MAX) <= 0;
            }
            case F64 -> value instanceof Double;
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
            case I32 -> Integer.valueOf(exactInteger(value).intValueExact());
            case I64 -> Long.valueOf(exactInteger(value).longValueExact());
            // Two's-complement bit pattern of the exact unsigned value; never a wrapped input.
            case SIZE_T -> Long.valueOf(exactInteger(value).longValue());
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

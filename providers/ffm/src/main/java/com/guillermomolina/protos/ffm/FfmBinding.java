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

import com.guillermomolina.protos.spi.foreign.ProtosForeignArgumentValue;
import com.guillermomolina.protos.spi.foreign.ProtosForeignValueClass;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generic downcall binding of one symbol of one authorized library to one declared signature.
 *
 * <p>The native {@link MethodHandle} is linked once, when the binding is created, and reused by
 * every invocation. Each invocation is synchronous, runs inside the current Actor segment, and
 * uses one confined per-call arena for {@code utf8z} text, private length-carrying input regions
 * and {@code out} slots, closed on every exit. A non-zero C status is never interpreted as a
 * failure: it is returned as data.
 */
final class FfmBinding {
    /**
     * Immutable declared signature: return descriptor, ordered parameters, borrow policy.
     *
     * <p>{@code preserving} is a trusted ABI declaration that the C function leaves earlier
     * borrowed regions alive (for example {@code sqlite3_column_bytes}). It is not a physical
     * check of memory lifetime: a wrong declaration has the same consequences as a wrong C
     * signature. Every non-preserving call advances the session's borrow epoch, which revokes
     * every borrowed region obtained before it.
     */
    record Signature(FfmTypes.Type returns, List<FfmTypes.Type> parameters, boolean preserving) {
        Signature {
            if (!FfmTypes.isReturnType(returns)) {
                throw new IllegalArgumentException("unsupported return descriptor");
            }
            parameters = List.copyOf(parameters);
            for (int index = 0; index < parameters.size(); index++) {
                if (!admitsAt(parameters.subList(0, index), parameters.get(index))) {
                    throw new IllegalArgumentException("unsupported parameter descriptor");
                }
            }
        }

        /** Whether {@code parameter} may follow this signature's parameters. */
        boolean canAppend(FfmTypes.Type parameter) {
            return admitsAt(parameters, parameter);
        }

        /**
         * Positional rules of the private provider-supplied parameters: an input length or copy
         * destructor needs a preceding input region, and a remainder output needs a preceding
         * text input.
         */
        private static boolean admitsAt(List<FfmTypes.Type> before, FfmTypes.Type parameter) {
            if (!FfmTypes.isParameterType(parameter)) {
                return false;
            }
            if (parameter == FfmTypes.InputLength.INSTANCE
                    || parameter == FfmTypes.CopyDestructor.INSTANCE) {
                // A length or copy instruction is meaningful only for an input region.
                return before.stream().anyMatch(type -> type instanceof FfmTypes.InputType);
            }
            if (parameter instanceof FfmTypes.OutType out
                    && out.target() == FfmTypes.Remainder.INSTANCE) {
                return before.stream().anyMatch(FfmTypes::isTextInput);
            }
            return true;
        }

        Signature withParameter(FfmTypes.Type parameter) {
            List<FfmTypes.Type> extended = new ArrayList<>(parameters);
            extended.add(parameter);
            return new Signature(returns, extended, preserving);
        }

        Signature asPreserving() {
            return new Signature(returns, parameters, true);
        }

        /** Number of Protos arguments: every non-{@code out} parameter consumes exactly one. */
        int arity() {
            int count = 0;
            for (FfmTypes.Type parameter : parameters) {
                if (FfmTypes.consumesArgument(parameter)) {
                    count++;
                }
            }
            return count;
        }

        boolean hasOutputs() {
            for (FfmTypes.Type parameter : parameters) {
                if (parameter instanceof FfmTypes.OutType) {
                    return true;
                }
            }
            return false;
        }

        FunctionDescriptor descriptor() {
            MemoryLayout[] layouts = new MemoryLayout[parameters.size()];
            for (int index = 0; index < layouts.length; index++) {
                layouts[index] = FfmTypes.layout(parameters.get(index));
            }
            return returns == FfmTypes.Scalar.VOID
                    ? FunctionDescriptor.ofVoid(layouts)
                    : FunctionDescriptor.of(FfmTypes.layout(returns), layouts);
        }

        /** Whether every library-bound descriptor belongs to {@code library}. */
        boolean belongsTo(String library) {
            if (!sameLibrary(returns, library)) {
                return false;
            }
            for (FfmTypes.Type parameter : parameters) {
                if (!sameLibrary(parameter, library)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean sameLibrary(FfmTypes.Type type, String library) {
            String owner = FfmTypes.libraryOf(type);
            return owner == null || owner.equals(library);
        }
    }

    private final FfmSession session;
    private final String library;
    private final String symbol;
    private final Signature signature;
    /** Spread form of the linked downcall: {@code (Object[]) -> Object}. */
    private final MethodHandle spread;

    FfmBinding(
            FfmSession session,
            String library,
            String symbol,
            Signature signature,
            Linker linker,
            MemorySegment address) {
        this.session = Objects.requireNonNull(session, "session");
        this.library = Objects.requireNonNull(library, "library");
        this.symbol = FfmTypes.requireIdentifier(symbol);
        this.signature = Objects.requireNonNull(signature, "signature");
        int count = signature.parameters().size();
        MethodHandle downcall = linker.downcallHandle(address, signature.descriptor());
        this.spread =
                downcall.asType(MethodType.genericMethodType(count))
                        .asSpreader(Object[].class, count);
    }

    FfmSession session() {
        return session;
    }

    String library() {
        return library;
    }

    String symbol() {
        return symbol;
    }

    Signature signature() {
        return signature;
    }

    /**
     * Pure pre-entry validation of one complete argument list: arity, kinds, exact ranges,
     * nullability, interior NUL, Unicode, handle session, and resource state. It performs no
     * foreign operation.
     */
    boolean admits(FfmSession caller, List<ProtosForeignArgumentValue> arguments) {
        if (caller != session || !session.isUsable() || arguments.size() != signature.arity()) {
            return false;
        }
        int next = 0;
        for (FfmTypes.Type parameter : signature.parameters()) {
            if (!FfmTypes.consumesArgument(parameter)) {
                continue;
            }
            if (!admitsArgument(parameter, arguments.get(next++))) {
                return false;
            }
        }
        return true;
    }

    private boolean admitsArgument(FfmTypes.Type parameter, ProtosForeignArgumentValue argument) {
        Object value = argument.value();
        return switch (parameter) {
            case FfmTypes.Scalar scalar ->
                    argument.kind() != ProtosForeignValueClass.Kind.NULL
                            && FfmTypes.admitsScalar(scalar, value);
            case FfmTypes.OpaqueType opaque -> {
                if (argument.kind() == ProtosForeignValueClass.Kind.NULL) {
                    yield opaque.nullable();
                }
                yield value instanceof FfmHandles.Resource resource
                        && resource.session() == session
                        && resource.isLive()
                        && resource.type().name().equals(opaque.name())
                        && resource.type().library().equals(opaque.library());
            }
            case FfmTypes.InputType input ->
                    argument.kind() == ProtosForeignValueClass.Kind.STRING
                            && FfmTypes.inputLength(input, value) >= 0;
            default -> false;
        };
    }

    /**
     * Invokes the native function. Owned results, including owned {@code out} values returned
     * together with an error status, enter the session's custody before the result is published.
     */
    Object invoke(List<ProtosForeignArgumentValue> arguments) throws Throwable {
        session.beginCall();
        try {
            if (!admits(session, arguments)) {
                throw new FfmFailure("invalid-argument", "arguments do not match the signature");
            }
            List<FfmTypes.Type> parameters = signature.parameters();
            Object[] carriers = new Object[parameters.size()];
            MemorySegment[] outSlots = new MemorySegment[parameters.size()];
            // Octet length of each marshalled text/input region (utf8z excludes its NUL); -1
            // elsewhere. Only private input lengths and remainder outputs consult it.
            long[] regionLengths = new long[parameters.size()];
            Object[] values;
            try (Arena call = Arena.ofConfined()) {
                int next = 0;
                long lastInput = -1;
                for (int index = 0; index < carriers.length; index++) {
                    FfmTypes.Type parameter = parameters.get(index);
                    regionLengths[index] = -1;
                    if (parameter instanceof FfmTypes.OutType out) {
                        MemorySegment slot = call.allocate(FfmTypes.layout(out.target()));
                        slot.fill((byte) 0);
                        outSlots[index] = slot;
                        carriers[index] = slot;
                    } else if (parameter == FfmTypes.InputLength.INSTANCE) {
                        // Admitted lengths never exceed MAX_INPUT_BYTES, so this is exact.
                        carriers[index] = Integer.valueOf(Math.toIntExact(lastInput));
                    } else if (parameter == FfmTypes.CopyDestructor.INSTANCE) {
                        carriers[index] =
                                MemorySegment.ofAddress(FfmTypes.CopyDestructor.SENTINEL);
                    } else {
                        Object value = arguments.get(next++).value();
                        carriers[index] = carrier(parameter, value, call);
                        if (FfmTypes.isTextInput(parameter)
                                || parameter instanceof FfmTypes.InputType) {
                            regionLengths[index] = ((MemorySegment) carriers[index]).byteSize() - 1;
                        }
                        if (parameter instanceof FfmTypes.InputType) {
                            lastInput = regionLengths[index];
                        }
                    }
                }
                // Every argument is validated and marshalled: entry into C starts here. A
                // non-preserving call revokes earlier borrowed regions before the downcall, so
                // they stay revoked even if the downcall ends exceptionally; a call rejected
                // above never reaches this point and never advances the epoch.
                if (!signature.preserving()) {
                    session.advanceBorrowEpoch();
                }
                Object raw = (Object) spread.invokeExact(carriers);

                // Stage A: capture every native carrier ([return, out0, out1, ...]) while the
                // call arena still holds the out slots; nothing fallible is converted yet.
                List<FfmTypes.Type> types = new ArrayList<>();
                List<Object> natives = new ArrayList<>();
                types.add(signature.returns());
                natives.add(raw);
                for (int index = 0; index < carriers.length; index++) {
                    if (parameters.get(index) instanceof FfmTypes.OutType out) {
                        types.add(out.target());
                        Object nativeValue = readCarrier(out.target(), outSlots[index]);
                        if (out.target() == FfmTypes.Remainder.INSTANCE) {
                            // Capture only integers while the input region is alive: the
                            // tail address and the bounds of the nearest preceding text input.
                            int source = index - 1;
                            while (!FfmTypes.isTextInput(parameters.get(source))) {
                                source--;
                            }
                            MemorySegment base = (MemorySegment) carriers[source];
                            nativeValue =
                                    new Tail(
                                            ((MemorySegment) nativeValue).address(),
                                            base.address(),
                                            regionLengths[source]);
                        }
                        natives.add(nativeValue);
                    }
                }

                // Stage B: every non-null owned result enters session custody before any
                // conversion can fail, whatever the C status says.
                Object[] adopted = new Object[types.size()];
                for (int index = 0; index < adopted.length; index++) {
                    if (types.get(index) instanceof FfmTypes.OwnedType owned
                            && !((MemorySegment) natives.get(index)).equals(MemorySegment.NULL)) {
                        adopted[index] =
                                session.adopt(
                                        owned.opaque(), owned, (MemorySegment) natives.get(index));
                    }
                }

                // Stage C: fallible conversion. Adopted resources are reused, never adopted
                // twice; if a conversion fails they simply remain in custody until release or
                // session close.
                values = new Object[types.size()];
                for (int index = 0; index < values.length; index++) {
                    values[index] =
                            adopted[index] != null
                                    ? adopted[index]
                                    : fromNative(types.get(index), natives.get(index));
                }
            }
            return signature.hasOutputs() ? new FfmHandles.Result(List.of(values)) : values[0];
        } finally {
            session.endCall();
        }
    }

    /**
     * Invokes a one-address destructor; used only by {@link FfmSession}. The borrow epoch
     * advances once, before the destructor runs, so earlier borrowed regions stay revoked even
     * when the destructor fails.
     */
    Object invokeDestructor(MemorySegment address) throws Throwable {
        session.advanceBorrowEpoch();
        Object raw = (Object) spread.invokeExact(new Object[] {address});
        return signature.returns() == FfmTypes.Scalar.VOID
                ? FfmHandles.Absent.INSTANCE
                : fromNative(signature.returns(), raw);
    }

    /** Integer-only capture of one remainder output; never an address-bearing segment. */
    private record Tail(long tail, long base, long length) {
        Object remaining() {
            if (tail == 0) {
                return FfmHandles.Absent.INSTANCE;
            }
            long offset = tail - base;
            if (offset < 0 || offset > length) {
                throw new FfmFailure("invalid-result", "the tail is outside the input");
            }
            return Long.valueOf(length - offset);
        }
    }

    private Object carrier(FfmTypes.Type parameter, Object value, Arena call) {
        return switch (parameter) {
            case FfmTypes.InputType input -> {
                byte[] octets = FfmTypes.inputOctets(input, (String) value);
                // Never NULL, even for an empty input: callees such as sqlite3_bind_blob treat a
                // NULL pointer as SQL NULL. The trailing NUL is allocated but never counted.
                MemorySegment region = call.allocate(Math.addExact(octets.length, 1L));
                MemorySegment.copy(octets, 0, region, ValueLayout.JAVA_BYTE, 0, octets.length);
                region.set(ValueLayout.JAVA_BYTE, octets.length, (byte) 0);
                yield region;
            }
            case FfmTypes.Scalar scalar when scalar == FfmTypes.Scalar.UTF8Z -> {
                byte[] octets = FfmTypes.strictUtf8((String) value);
                MemorySegment text = call.allocate(octets.length + 1L);
                MemorySegment.copy(octets, 0, text, ValueLayout.JAVA_BYTE, 0, octets.length);
                text.set(ValueLayout.JAVA_BYTE, octets.length, (byte) 0);
                yield text;
            }
            case FfmTypes.Scalar scalar -> FfmTypes.nativeScalar(scalar, value);
            case FfmTypes.OpaqueType ignored ->
                    value == null
                            ? MemorySegment.NULL
                            : ((FfmHandles.Resource) value).addressForCall();
            default -> throw new IllegalStateException("not an argument position");
        };
    }

    /** Reads one out slot's native carrier without converting or rejecting it. */
    private static Object readCarrier(FfmTypes.Type target, MemorySegment slot) {
        return switch (target) {
                    case FfmTypes.Scalar scalar ->
                            switch (scalar) {
                                // Boxed per arm: no numeric promotion between arms.
                                case I32 -> Integer.valueOf(slot.get(ValueLayout.JAVA_INT, 0));
                                case I64, SIZE_T ->
                                        Long.valueOf(slot.get(ValueLayout.JAVA_LONG, 0));
                                case F64 ->
                                        Double.valueOf(slot.get(ValueLayout.JAVA_DOUBLE, 0));
                                default -> throw new IllegalStateException("not an out scalar");
                            };
                    default -> slot.get(ValueLayout.ADDRESS, 0);
                };
    }

    /**
     * Converts one native carrier into a provider value. A non-null owned carrier never reaches
     * here: it was adopted in stage B.
     */
    private Object fromNative(FfmTypes.Type type, Object raw) {
        return switch (type) {
            case FfmTypes.Scalar scalar ->
                    switch (scalar) {
                        case VOID -> FfmHandles.Absent.INSTANCE;
                        case I32 -> Long.valueOf((Integer) raw);
                        case I64 -> (Long) raw;
                        case SIZE_T -> FfmTypes.unsignedSize((Long) raw);
                        case F64 -> (Double) raw;
                        case UTF8Z -> throw new IllegalStateException("utf8z is input only");
                    };
            case FfmTypes.OpaqueType opaque -> {
                MemorySegment address = (MemorySegment) raw;
                if (address.equals(MemorySegment.NULL)) {
                    if (!opaque.nullable()) {
                        throw new FfmFailure("null-result", "a non-nullable reference was NULL");
                    }
                    yield FfmHandles.Absent.INSTANCE;
                }
                yield session.adopt(opaque, null, address);
            }
            case FfmTypes.OwnedType owned -> {
                MemorySegment address = (MemorySegment) raw;
                yield address.equals(MemorySegment.NULL)
                        ? FfmHandles.Absent.INSTANCE
                        : session.adopt(owned.opaque(), owned, address);
            }
            case FfmTypes.BorrowedType ignored -> {
                MemorySegment address = (MemorySegment) raw;
                yield address.equals(MemorySegment.NULL)
                        ? FfmHandles.Absent.INSTANCE
                        : new FfmHandles.Borrowed(session, session.borrowEpoch(), address);
            }
            case FfmTypes.OutType ignored -> throw new IllegalStateException("nested out");
            case FfmTypes.Remainder ignored -> ((Tail) raw).remaining();
            case FfmTypes.InputType ignored -> throw new IllegalStateException("input only");
            case FfmTypes.InputLength ignored -> throw new IllegalStateException("input only");
            case FfmTypes.CopyDestructor ignored ->
                    throw new IllegalStateException("input only");
        };
    }

    @Override
    public String toString() {
        return "ffi-binding(" + symbol + ")";
    }
}

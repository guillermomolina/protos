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
import java.lang.foreign.GroupLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.graalvm.nativeimage.ImageInfo;

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
     * Physical downcall shapes registered for a Native Image executable, one letter per carrier:
     * {@code B} byte, {@code S} short, {@code I} int, {@code L} long, {@code F} float, {@code D}
     * double, {@code P} address and {@code V} void return. Signed and unsigned descriptors of one
     * width share one carrier letter. A by-value struct or union is spelled recursively with its
     * complete physical structure: {@code struct(...)}, {@code union(...)}, {@code
     * sequence(count,element)} and {@code padding(bytes)}, so member order, member carriers,
     * sequence counts and padding all distinguish shapes; field names and layout identities never
     * appear. The image metadata registers exactly these shapes; a declaration of any other shape
     * is refused before symbol lookup or linking there. A JVM links every declaration its linker
     * supports.
     */
    static final Set<String> NATIVE_SHAPES =
            Set.of(
                    "I()", "I(I)", "I(I,I)", "I(P)", "I(P,I)", "I(P,P)", "I(P,I,L)", "I(P,I,D)",
                    "I(P,I,P)", "I(P,P,L)", "I(L,I,P)", "I(I,I,P,P)", "I(P,P,I,P)",
                    "I(P,P,I,P,P)", "I(P,I,P,I,P)", "L()", "L(L)", "L(P)", "L(P,I)", "D(P,I)",
                    "D(D,D)", "P(I)", "P(L)", "P(P,I)", "V()", "V(L)", "V(P)",
                    "B(B)", "S(S)", "I(P,P,P)", "F(F)", "I(P,P,P,P)",
                    // By-value groups of the fixture inventory, one entry per exercised signature.
                    "struct(I,padding(4),D)()",
                    "struct(I,padding(4),D)(struct(I,padding(4),D))",
                    "I(struct(I,padding(4),D))",
                    "struct(L,L,L)(struct(L,L,L))",
                    "union(L,D)(union(L,D))",
                    "union(L,sequence(9,B),padding(16))(union(L,sequence(9,B),padding(16)))",
                    "struct(B,padding(7),struct(I,padding(4),D),sequence(3,S),padding(2),P)"
                            + "(struct(B,padding(7),struct(I,padding(4),D),sequence(3,S),"
                            + "padding(2),P))",
                    "struct(union(L,D),sequence(2,F))(struct(union(L,D),sequence(2,F)))",
                    "struct(I,padding(4),D)(struct(I,padding(4),D),I,P,P)");

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

        /**
         * The physical carrier shape of {@link #descriptor()}, for example {@code I(P,I)} or
         * {@code struct(I,padding(4),D)(struct(I,padding(4),D))}.
         */
        String physicalShape() {
            FunctionDescriptor descriptor = descriptor();
            StringBuilder shape = new StringBuilder();
            shape.append(descriptor.returnLayout().map(Signature::carrier).orElse("V")).append('(');
            List<MemoryLayout> arguments = descriptor.argumentLayouts();
            for (int index = 0; index < arguments.size(); index++) {
                if (index > 0) {
                    shape.append(',');
                }
                shape.append(carrier(arguments.get(index)));
            }
            return shape.append(')').toString();
        }

        /** The recursive physical spelling of one layout, without whitespace. */
        private static String carrier(MemoryLayout layout) {
            if (layout.equals(ValueLayout.JAVA_BYTE)) {
                return "B";
            }
            if (layout.equals(ValueLayout.JAVA_SHORT)) {
                return "S";
            }
            if (layout.equals(ValueLayout.JAVA_FLOAT)) {
                return "F";
            }
            if (layout.equals(ValueLayout.JAVA_INT)) {
                return "I";
            }
            if (layout.equals(ValueLayout.JAVA_LONG)) {
                return "L";
            }
            if (layout.equals(ValueLayout.JAVA_DOUBLE)) {
                return "D";
            }
            if (layout.equals(ValueLayout.ADDRESS)) {
                return "P";
            }
            if (layout instanceof PaddingLayout) {
                return "padding(" + layout.byteSize() + ")";
            }
            if (layout instanceof SequenceLayout sequence) {
                return "sequence(" + sequence.elementCount() + ","
                        + carrier(sequence.elementLayout()) + ")";
            }
            if (layout instanceof GroupLayout aggregate) {
                StringBuilder group =
                        new StringBuilder(aggregate instanceof StructLayout ? "struct(" : "union(");
                List<MemoryLayout> members = aggregate.memberLayouts();
                for (int index = 0; index < members.size(); index++) {
                    if (index > 0) {
                        group.append(',');
                    }
                    group.append(carrier(members.get(index)));
                }
                return group.append(')').toString();
            }
            throw new IllegalArgumentException("unsupported carrier layout");
        }

        /** Whether a by-value struct or union is a direct parameter or the return. */
        private boolean hasDirectGroup() {
            if (FfmTypes.isCallGroup(returns)) {
                return true;
            }
            for (FfmTypes.Type parameter : parameters) {
                if (FfmTypes.isCallGroup(parameter)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Whether this executable can link the signature, decided before any symbol lookup. A
         * Native Image admits only an exactly registered physical shape. A JVM admits every
         * scalar shape and, for a by-value group, whatever the standard linker accepts: the
         * unbound downcall construction validates the descriptor without resolving or calling
         * a symbol, and runs only at binding admission, never per invocation.
         */
        boolean isLinkable() {
            try {
                if (ImageInfo.inImageRuntimeCode()) {
                    return NATIVE_SHAPES.contains(physicalShape());
                }
                if (!hasDirectGroup()) {
                    return true;
                }
                Linker.nativeLinker().downcallHandle(descriptor());
                return true;
            } catch (IllegalArgumentException unsupported) {
                return false;
            }
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
        MethodHandle downcall = linker.downcallHandle(address, signature.descriptor());
        // A by-value group return adds the linker's leading SegmentAllocator parameter, which is
        // neither a Protos argument nor a C parameter; every other shape keeps its C arity.
        int count = downcall.type().parameterCount();
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
            case FfmTypes.PointerType pointer -> {
                if (argument.kind() == ProtosForeignValueClass.Kind.NULL) {
                    yield pointer.nullable();
                }
                // Exact pointee identity; a void parameter passes any live data pointer without
                // dereferencing it, never the converse. Borrowed declarations are results only.
                yield !pointer.borrowed()
                        && value instanceof FfmHandles.Pointer capability
                        && capability.session() == session
                        && capability.isLive()
                        && (capability.pointee() == pointer.identity()
                                || pointer.voidPointee());
            }
            case FfmTypes.InputType input ->
                    argument.kind() == ProtosForeignValueClass.Kind.STRING
                            && FfmTypes.inputLength(input, value) >= 0;
            // Nominal admission: a live region or typed view of this session whose layout is the
            // exact declared one, never a structurally equal layout, a byte slice or a pointer.
            // Read-only views pass: the callee receives a copy.
            case FfmTypes.Storage group ->
                    FfmTypes.isCallGroup(group)
                            && value instanceof FfmHandles.Region region
                            && region.session() == session
                            && region.isLive()
                            && region.storage().identity() == group.identity()
                            && region.segment().byteSize() == group.size()
                            && region.segment().address() % group.align() == 0;
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
            // Independently owned group results of this invocation only, already in custody when
            // allocated; created only by signatures that declare them.
            FfmHandles.Region[] groupOuts = null;
            FfmHandles.Region groupReturn = null;
            Object[] values;
            try (Arena call = Arena.ofConfined()) {
                Object[] invocation;
                try {
                    int next = 0;
                    long lastInput = -1;
                    for (int index = 0; index < carriers.length; index++) {
                        FfmTypes.Type parameter = parameters.get(index);
                        regionLengths[index] = -1;
                        if (parameter instanceof FfmTypes.OutType out
                                && FfmTypes.isCallGroup(out.target())) {
                            // The C out pointer addresses the result root's own storage, never
                            // the call arena, so the result outlives the call.
                            if (groupOuts == null) {
                                groupOuts = new FfmHandles.Region[carriers.length];
                            }
                            groupOuts[index] = session.allocate((FfmTypes.Storage) out.target());
                            carriers[index] = groupOuts[index].segment();
                        } else if (parameter instanceof FfmTypes.OutType out) {
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
                                regionLengths[index] =
                                        ((MemorySegment) carriers[index]).byteSize() - 1;
                            }
                            if (parameter instanceof FfmTypes.InputType) {
                                lastInput = regionLengths[index];
                            }
                        }
                    }
                    invocation = carriers;
                    if (FfmTypes.isCallGroup(signature.returns())) {
                        // The linker writes the by-value result through this allocator straight
                        // into an independently owned root; it is not a public parameter.
                        groupReturn = session.allocate((FfmTypes.Storage) signature.returns());
                        invocation = new Object[carriers.length + 1];
                        invocation[0] = SegmentAllocator.prefixAllocator(groupReturn.segment());
                        System.arraycopy(carriers, 0, invocation, 1, carriers.length);
                    }
                } catch (Throwable failure) {
                    // C was not entered: prepared result roots are released, never published.
                    releasePrepared(groupOuts, groupReturn, failure);
                    throw failure;
                }
                // Every argument is validated and marshalled: entry into C starts here. A
                // non-preserving call revokes earlier borrowed regions before the downcall, so
                // they stay revoked even if the downcall ends exceptionally; a call rejected
                // above never reaches this point and never advances the epoch.
                if (!signature.preserving()) {
                    session.advanceBorrowEpoch();
                }
                Object raw = (Object) spread.invokeExact(invocation);

                // Stage A: capture every native carrier ([return, out0, out1, ...]) while the
                // call arena still holds the out slots; nothing fallible is converted yet. A
                // group result is its already allocated root, never an address to convert.
                List<FfmTypes.Type> types = new ArrayList<>();
                List<Object> natives = new ArrayList<>();
                types.add(signature.returns());
                natives.add(groupReturn != null ? groupReturn : raw);
                for (int index = 0; index < carriers.length; index++) {
                    if (parameters.get(index) instanceof FfmTypes.OutType out) {
                        types.add(out.target());
                        if (groupOuts != null && groupOuts[index] != null) {
                            natives.add(groupOuts[index]);
                            continue;
                        }
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
                // conversion can fail, whatever the C status says. Group roots entered custody
                // when they were allocated and are never adopted again.
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
     * Releases the group result roots of an invocation that failed before entering C, through
     * the ordinary allocation release. A cleanup failure is attached to the original failure and
     * never retried; once the session is closing, its close reclaims what remains in custody.
     */
    private void releasePrepared(
            FfmHandles.Region[] groupOuts, FfmHandles.Region groupReturn, Throwable failure) {
        if (groupOuts != null) {
            for (FfmHandles.Region root : groupOuts) {
                if (root != null) {
                    releaseQuietly(root, failure);
                }
            }
        }
        if (groupReturn != null) {
            releaseQuietly(groupReturn, failure);
        }
    }

    private void releaseQuietly(FfmHandles.Region root, Throwable failure) {
        try {
            session.releaseAllocation(root);
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
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
            // Marshalled before the borrow epoch advances: a borrowed pointer admitted as the
            // input of a non-preserving call is revoked for later use, not for this call.
            case FfmTypes.PointerType ignored ->
                    value == null
                            ? MemorySegment.NULL
                            : ((FfmHandles.Pointer) value).addressForCall();
            // By-value group: one shallow copy of the admitted region into the call arena,
            // before the borrow epoch advances. Pointer members stay pointer bits; their
            // pointees are not copied. The copy is the callee's private parameter storage.
            case FfmTypes.Storage group -> {
                MemorySegment copy = call.allocate(group.size(), group.align());
                MemorySegment.copy(((FfmHandles.Region) value).segment(), 0, copy, 0, group.size());
                yield copy;
            }
            default -> throw new IllegalStateException("not an argument position");
        };
    }

    /** Reads one out slot's native carrier without converting or rejecting it. */
    private static Object readCarrier(FfmTypes.Type target, MemorySegment slot) {
        return switch (target) {
            case FfmTypes.Scalar scalar -> readCarrier(scalar, slot, 0);
            default -> slot.get(ValueLayout.ADDRESS, 0);
        };
    }

    /**
     * Reads the physical carrier of one numeric scalar at {@code offset} of a bounded segment,
     * without converting it; shared by out slots and native regions.
     */
    static Object readCarrier(FfmTypes.Scalar scalar, MemorySegment segment, long offset) {
        return switch (scalar) {
            // Boxed per arm: no numeric promotion between arms.
            case I8, U8 -> Byte.valueOf(segment.get(ValueLayout.JAVA_BYTE, offset));
            case I16, U16 -> Short.valueOf(segment.get(ValueLayout.JAVA_SHORT, offset));
            case I32, U32 -> Integer.valueOf(segment.get(ValueLayout.JAVA_INT, offset));
            case I64, U64, SIZE_T -> Long.valueOf(segment.get(ValueLayout.JAVA_LONG, offset));
            case F32 -> Float.valueOf(segment.get(ValueLayout.JAVA_FLOAT, offset));
            case F64 -> Double.valueOf(segment.get(ValueLayout.JAVA_DOUBLE, offset));
            default -> throw new IllegalStateException("not a storage scalar");
        };
    }

    /**
     * Writes one physical carrier produced by {@link FfmTypes#nativeScalar} for {@code scalar}
     * at {@code offset} of a bounded segment: exactly one typed store.
     */
    static void writeCarrier(
            FfmTypes.Scalar scalar, MemorySegment segment, long offset, Object carrier) {
        switch (scalar) {
            case I8, U8 -> segment.set(ValueLayout.JAVA_BYTE, offset, (Byte) carrier);
            case I16, U16 -> segment.set(ValueLayout.JAVA_SHORT, offset, (Short) carrier);
            case I32, U32 -> segment.set(ValueLayout.JAVA_INT, offset, (Integer) carrier);
            case I64, U64, SIZE_T -> segment.set(ValueLayout.JAVA_LONG, offset, (Long) carrier);
            case F32 -> segment.set(ValueLayout.JAVA_FLOAT, offset, (Float) carrier);
            case F64 -> segment.set(ValueLayout.JAVA_DOUBLE, offset, (Double) carrier);
            default -> throw new IllegalStateException("not a storage scalar");
        }
    }

    /**
     * Protos-facing value of one scalar carrier: exact signed and unsigned widening, unsigned
     * 64-bit values beyond the signed range as BigInteger, and exact binary32 widening with
     * signed zero and NaN preserved. Shared by call results, out slots and native regions.
     */
    static Object fromNativeScalar(FfmTypes.Scalar scalar, Object raw) {
        return switch (scalar) {
            case VOID -> FfmHandles.Absent.INSTANCE;
            case I8 -> Long.valueOf((Byte) raw);
            case U8 -> Long.valueOf(Byte.toUnsignedLong((Byte) raw));
            case I16 -> Long.valueOf((Short) raw);
            case U16 -> Long.valueOf(Short.toUnsignedLong((Short) raw));
            case I32 -> Long.valueOf((Integer) raw);
            case U32 -> Long.valueOf(Integer.toUnsignedLong((Integer) raw));
            case I64 -> (Long) raw;
            case U64, SIZE_T -> FfmTypes.unsignedSize((Long) raw);
            // Exact widening of the binary32 carrier, signed zero and NaN included.
            case F32 -> Double.valueOf((Float) raw);
            case F64 -> (Double) raw;
            case UTF8Z -> throw new IllegalStateException("utf8z is input only");
        };
    }

    /**
     * Converts one native carrier into a provider value. A non-null owned carrier never reaches
     * here: it was adopted in stage B.
     */
    private Object fromNative(FfmTypes.Type type, Object raw) {
        return switch (type) {
            case FfmTypes.Scalar scalar -> fromNativeScalar(scalar, raw);
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
            case FfmTypes.PointerType pointer -> {
                // A fresh pass-only C-origin capability without readable extent; a borrowed one
                // retains the epoch reached after this call's own advancement.
                MemorySegment address = (MemorySegment) raw;
                if (address.equals(MemorySegment.NULL)) {
                    if (!pointer.nullable()) {
                        throw new FfmFailure("null-result", "a non-nullable pointer was NULL");
                    }
                    yield FfmHandles.Absent.INSTANCE;
                }
                yield new FfmHandles.Pointer(
                        session,
                        pointer.identity(),
                        address,
                        null,
                        pointer.borrowed(),
                        session.borrowEpoch());
            }
            case FfmTypes.OutType ignored -> throw new IllegalStateException("nested out");
            case FfmTypes.Remainder ignored -> ((Tail) raw).remaining();
            case FfmTypes.InputType ignored -> throw new IllegalStateException("input only");
            case FfmTypes.InputLength ignored -> throw new IllegalStateException("input only");
            case FfmTypes.CopyDestructor ignored ->
                    throw new IllegalStateException("input only");
            // An owned root allocated for this result before entry; published unchanged.
            case FfmTypes.Storage ignored -> (FfmHandles.Region) raw;
        };
    }

    @Override
    public String toString() {
        return "ffi-binding(" + symbol + ")";
    }
}

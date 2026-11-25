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
import com.guillermomolina.protos.spi.foreign.ProtosForeignFailureInfo;
import com.guillermomolina.protos.spi.foreign.ProtosForeignPluginSession;
import com.guillermomolina.protos.spi.foreign.ProtosForeignValueClass;
import com.guillermomolina.protos.spi.foreign.ProtosForeignValueClass.Capability;
import com.guillermomolina.protos.spi.foreign.ProtosForeignValueOperations;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single I085 value contract of the FFM provider.
 *
 * <p>Classification: library modules (member read), declaration operations and bindings
 * (executable), ABI descriptors and signatures, opaque and owned references, borrowed pointers
 * and typed pointer capabilities (capability-free handles that only travel back as arguments),
 * provider-private indexable results
 * (indexed read and a {@code size} member), C scalars, and the C {@code void} absence. The
 * session's private context (member read) exposes only the numeric scalar descriptors, the typed
 * pointer descriptors and the session-scoped memory operations: physical storage composition
 * from Protos-computed metadata, allocation, allocation-root release,
 * region address, slicing, typed views and trusted extent assertions, plus two private physical
 * supports of the Protos-source aggregate projection: the exact storage of a live region and one
 * whole-region copy between regions of the exact same layout identity. Native regions are indexed
 * (read and write) by exact member name or exact position, never through foreign members, and
 * every indexed access is admitted completely by {@link #acceptsElementRead} or {@link
 * #acceptsElementWrite} before entry.
 *
 * <p>The members of a library module are the minimal provider-private operations a future
 * Protos-source {@code std:ffi} needs: descriptors ({@code void}, {@code i32}, {@code i64}, {@code
 * f64}, {@code sizeT}, {@code utf8z}, {@code borrowed}, and the LIB030 private input descriptors
 * {@code text}, {@code octets}, {@code inputLength}, {@code copyDestructor} and {@code
 * remainder}), {@code opaque(name)}, {@code
 * nullable(opaque)}, {@code owned(opaque, destructorSymbol, void|i32)}, {@code
 * pointerType(voidPointee)}, {@code nullablePointerType(pointer)}, {@code
 * borrowedPointerType(pointer)}, {@code addressOf(owned, voidPointer)}, {@code out(type)}, {@code
 * signature(returnType)}, {@code param(signature, type)}, {@code preserving(signature)}, {@code
 * bind(symbol, signature)}, {@code release(owned)}, {@code isLive(reference)}, and {@code
 * copy(borrowed, length)}. Signatures are composed one parameter at a time from handles and
 * scalars, so no Array crosses the SPI. These operations are external support machinery; they are
 * <strong>not</strong> the normative public {@code std:ffi} API. Nothing here is specific to
 * SQLite or to any other library.
 *
 * <p>Every executable operation is validated completely by {@link #acceptsExecution} before entry
 * (arity, kinds, exact ranges, nullability, interior NUL, Unicode, session, and resource state);
 * a refusal is the ordinary Protos Error. Failures after entry carry only the provider's own
 * sanitized category and message.
 */
final class FfmValueOperations implements ProtosForeignValueOperations {
    static final String LANGUAGE = "ffi";

    /**
     * One operation of one receiver: an authorized library module, or the session's private
     * context (the {@link FfmSession} itself), which is never a library.
     */
    record Operation(Object receiver, Op op) {
        /** The library module receiver, or null for the private context. */
        FfmLibraryModule module() {
            return receiver instanceof FfmLibraryModule module ? module : null;
        }

        FfmSession session() {
            return receiver instanceof FfmLibraryModule module
                    ? module.session()
                    : (FfmSession) receiver;
        }

        @Override
        public String toString() {
            return "ffi-operation(" + op.member + ")";
        }
    }

    /** Where an operation is a member: library modules, the private context, or both. */
    enum Scope {
        LIBRARY,
        CONTEXT,
        BOTH
    }

    enum Op {
        OPAQUE("opaque", 1, Scope.LIBRARY),
        NULLABLE("nullable", 1, Scope.LIBRARY),
        OWNED("owned", 3, Scope.LIBRARY),
        OUT("out", 1, Scope.LIBRARY),
        SIGNATURE("signature", 1, Scope.LIBRARY),
        PARAM("param", 2, Scope.LIBRARY),
        PRESERVING("preserving", 1, Scope.LIBRARY),
        BIND("bind", 2, Scope.LIBRARY),
        /** C-owned resources on a library; allocation roots on the context. */
        RELEASE("release", 1, Scope.BOTH),
        IS_LIVE("isLive", 1, Scope.BOTH),
        COPY("copy", 2, Scope.LIBRARY),
        POINTER_TYPE("pointerType", 1, Scope.BOTH),
        NULLABLE_POINTER_TYPE("nullablePointerType", 1, Scope.BOTH),
        BORROWED_POINTER_TYPE("borrowedPointerType", 1, Scope.BOTH),
        ADDRESS_OF("addressOf", 2, Scope.LIBRARY),
        /** Trusted extent of library-static storage: the receiver library is the anchor. */
        ASSUME_STATIC_EXTENT("assumeStaticExtent", 3, Scope.LIBRARY),
        SCALAR_STORAGE("scalarStorage", 4, Scope.CONTEXT),
        POINTER_STORAGE("pointerStorage", 2, Scope.CONTEXT),
        AGGREGATE_STORAGE("aggregateStorage", 4, Scope.CONTEXT),
        MEMBER_STORAGE("memberStorage", 4, Scope.CONTEXT),
        ARRAY_STORAGE("arrayStorage", 4, Scope.CONTEXT),
        ALLOCATE("allocate", 1, Scope.CONTEXT),
        REGION_ADDRESS("regionAddress", 2, Scope.CONTEXT),
        SLICE("slice", 3, Scope.CONTEXT),
        VIEW("view", 2, Scope.CONTEXT),
        ASSUME_EXTENT("assumeExtent", 4, Scope.CONTEXT),
        /** The exact retained storage of a live layout region; never an address. */
        REGION_STORAGE("regionStorage", 2, Scope.CONTEXT),
        /** One whole copy between live regions of one exact layout identity. */
        COPY_REGION("copyRegion", 2, Scope.CONTEXT);

        final String member;
        final int arity;
        final Scope scope;

        Op(String member, int arity, Scope scope) {
            this.member = member;
            this.arity = arity;
            this.scope = scope;
        }

        boolean onLibrary() {
            return scope != Scope.CONTEXT;
        }

        boolean onContext() {
            return scope != Scope.LIBRARY;
        }
    }

    private static final Map<String, Object> MEMBERS = members();

    /**
     * Members of the private context: the numeric scalar descriptors and only the session-scoped
     * memory and descriptor operations; no library declaration, binding or lookup.
     */
    private static final Map<String, Object> CONTEXT_MEMBERS = contextMembers();

    private static Map<String, Object> contextMembers() {
        java.util.HashMap<String, Object> members = new java.util.HashMap<>();
        for (FfmTypes.Scalar scalar : FfmTypes.Scalar.values()) {
            if (FfmTypes.isNumericScalar(scalar)) {
                members.put(scalar.spelling(), scalar);
            }
        }
        for (Op op : Op.values()) {
            if (op.onContext()) {
                members.put(op.member, op);
            }
        }
        return Map.copyOf(members);
    }

    private static Map<String, Object> members() {
        java.util.HashMap<String, Object> members = new java.util.HashMap<>();
        for (FfmTypes.Scalar scalar : FfmTypes.Scalar.values()) {
            members.put(scalar.spelling(), scalar);
        }
        members.put("borrowed", FfmTypes.BorrowedType.INSTANCE);
        for (FfmTypes.InputType input : FfmTypes.InputType.values()) {
            members.put(input.spelling(), input);
        }
        members.put("inputLength", FfmTypes.InputLength.INSTANCE);
        members.put("copyDestructor", FfmTypes.CopyDestructor.INSTANCE);
        members.put("remainder", FfmTypes.Remainder.INSTANCE);
        for (Op op : Op.values()) {
            if (op.onLibrary()) {
                members.put(op.member, op);
            }
        }
        return Map.copyOf(members);
    }

    @Override
    public String language() {
        return LANGUAGE;
    }

    @Override
    public boolean supportsLongIntegerArguments() {
        return true;
    }

    @Override
    public ProtosForeignValueClass classify(ProtosForeignPluginSession session, Object handle) {
        return switch (handle) {
            case FfmHandles.Absent ignored -> ProtosForeignValueClass.absent();
            case Long number -> ProtosForeignValueClass.integral(number);
            case BigInteger number -> ProtosForeignValueClass.integral(number);
            case Double number -> ProtosForeignValueClass.binary64(number);
            case Boolean flag -> ProtosForeignValueClass.booleanValue(flag);
            case FfmLibraryModule module ->
                    ProtosForeignValueClass.handle(module, Set.of(Capability.MEMBER_READ));
            case FfmSession context when context == session ->
                    ProtosForeignValueClass.handle(context, Set.of(Capability.MEMBER_READ));
            // Indexed by String member name or exact Integer position; never by foreign member.
            case FfmHandles.Region region ->
                    ProtosForeignValueClass.handle(
                            region, Set.of(Capability.INDEXED_READ, Capability.INDEXED_WRITE));
            case FfmTypes.Storage storage -> ProtosForeignValueClass.handle(storage, Set.of());
            case Operation operation ->
                    ProtosForeignValueClass.handle(operation, Set.of(Capability.EXECUTABLE));
            case FfmBinding binding ->
                    ProtosForeignValueClass.handle(binding, Set.of(Capability.EXECUTABLE));
            case FfmHandles.Indexed ignored ->
                    ProtosForeignValueClass.handle(
                            null, Set.of(Capability.INDEXED_READ, Capability.MEMBER_READ));
            case FfmTypes.Type type -> ProtosForeignValueClass.handle(type, Set.of());
            case FfmBinding.Signature signature ->
                    ProtosForeignValueClass.handle(signature, Set.of());
            case FfmHandles.Resource resource -> ProtosForeignValueClass.handle(resource, Set.of());
            case FfmHandles.Borrowed borrowed -> ProtosForeignValueClass.handle(borrowed, Set.of());
            case FfmHandles.Pointer pointer -> ProtosForeignValueClass.handle(pointer, Set.of());
            default -> throw new FfmFailure("internal", "unclassifiable provider value");
        };
    }

    /**
     * Every exported kind crosses; {@link #acceptsExecution} then admits each argument against
     * its exact position. Only the {@code pointerType} declaration admits a Boolean: no binding
     * parameter and no other operation does.
     */
    @Override
    public boolean acceptsArgument(ProtosForeignArgumentValue argument) {
        return true;
    }

    @Override
    public boolean acceptsExecution(
            ProtosForeignPluginSession session,
            Object handle,
            List<ProtosForeignArgumentValue> arguments) {
        if (!(session instanceof FfmSession live) || !live.isUsable()) {
            return false;
        }
        return switch (handle) {
            case FfmBinding binding -> binding.admits(live, arguments);
            case Operation operation -> admits(live, operation, values(arguments));
            default -> false;
        };
    }

    private static Object[] values(List<ProtosForeignArgumentValue> arguments) {
        Object[] values = new Object[arguments.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = arguments.get(index).value();
        }
        return values;
    }

    /** Pure validation of one operation; it touches no native state. */
    private static boolean admits(FfmSession session, Operation operation, Object[] v) {
        if (operation.session() != session || v.length != operation.op().arity) {
            return false;
        }
        FfmLibraryModule module = operation.module();
        if (module == null) {
            return operation.op().onContext() && admitsOnContext(session, operation.op(), v);
        }
        if (!operation.op().onLibrary()) {
            return false;
        }
        String library = module.library();
        return switch (operation.op()) {
            case OPAQUE -> FfmTypes.isIdentifier(v[0]);
            case NULLABLE -> isPlainOpaque(v[0], library);
            case OWNED ->
                    isPlainOpaque(v[0], library)
                            && FfmTypes.isIdentifier(v[1])
                            && (v[2] == FfmTypes.Scalar.VOID || v[2] == FfmTypes.Scalar.I32)
                            && new FfmBinding.Signature(
                                            (FfmTypes.Type) v[2],
                                            List.of((FfmTypes.Type) v[0]),
                                            false)
                                    .isLinkable();
            case OUT ->
                    v[0] instanceof FfmTypes.Type type
                            && FfmTypes.isOutTarget(type)
                            && inLibrary(type, library);
            case SIGNATURE ->
                    v[0] instanceof FfmTypes.Type type
                            && FfmTypes.isReturnType(type)
                            && inLibrary(type, library);
            case PARAM ->
                    v[0] instanceof FfmBinding.Signature signature
                            && v[1] instanceof FfmTypes.Type type
                            && signature.canAppend(type)
                            && inLibrary(type, library);
            case PRESERVING -> v[0] instanceof FfmBinding.Signature;
            case BIND ->
                    FfmTypes.isIdentifier(v[0])
                            && v[1] instanceof FfmBinding.Signature signature
                            && signature.belongsTo(library)
                            && signature.isLinkable();
            case RELEASE ->
                    v[0] instanceof FfmHandles.Resource resource
                            && resource.session() == session
                            && resource.isOwned()
                            && resource.isLive();
            case IS_LIVE -> admitsIsLive(session, v[0]);
            case COPY -> {
                BigInteger length = FfmTypes.exactInteger(v[1]);
                yield v[0] instanceof FfmHandles.Borrowed borrowed
                        && borrowed.session() == session
                        && borrowed.isValid()
                        && length != null
                        && length.signum() >= 0
                        && length.compareTo(BigInteger.valueOf(FfmHandles.MAX_COPY_BYTES)) <= 0;
            }
            case POINTER_TYPE -> v[0] instanceof Boolean;
            case NULLABLE_POINTER_TYPE, BORROWED_POINTER_TYPE -> isPlainPointer(v[0]);
            case ADDRESS_OF ->
                    v[0] instanceof FfmHandles.Resource resource
                            && resource.session() == session
                            && resource.isOwned()
                            && resource.isLive()
                            && resource.type().library().equals(library)
                            && isPlainPointer(v[1])
                            && ((FfmTypes.PointerType) v[1]).voidPointee();
            // The receiver library is the anchor: storage it owns lives as long as the session.
            case ASSUME_STATIC_EXTENT ->
                    v[0] instanceof FfmHandles.Pointer pointer
                            && admitsCAssertion(session, pointer, v[1], v[2]);
            default -> false;
        };
    }

    private static boolean admitsIsLive(FfmSession session, Object value) {
        return (value instanceof FfmHandles.Resource resource && resource.session() == session)
                || (value instanceof FfmHandles.Borrowed borrowed && borrowed.session() == session)
                || (value instanceof FfmHandles.Pointer pointer && pointer.session() == session)
                || (value instanceof FfmHandles.Region region && region.session() == session);
    }

    /** Pure validation of one private-context operation. */
    private static boolean admitsOnContext(FfmSession session, Op op, Object[] v) {
        return switch (op) {
            case POINTER_TYPE -> v[0] instanceof Boolean;
            case NULLABLE_POINTER_TYPE, BORROWED_POINTER_TYPE -> isPlainPointer(v[0]);
            case IS_LIVE -> admitsIsLive(session, v[0]);
            case SCALAR_STORAGE, POINTER_STORAGE, AGGREGATE_STORAGE, MEMBER_STORAGE,
                    ARRAY_STORAGE ->
                    composeOrNull(op, v) != null;
            case ALLOCATE -> v[0] instanceof FfmTypes.Storage;
            case RELEASE ->
                    v[0] instanceof FfmHandles.Region root
                            && root.session() == session
                            && root.isRoot()
                            && root.isLive();
            case REGION_ADDRESS ->
                    v[0] instanceof FfmHandles.Region region
                            && region.session() == session
                            && region.isLive()
                            && isPlainPointer(v[1])
                            && ((FfmTypes.PointerType) v[1]).voidPointee();
            case SLICE -> sliceRange(session, v) != null;
            case VIEW -> admitsView(session, v[0], v[1]);
            case ASSUME_EXTENT -> admitsAssertion(session, v);
            case REGION_STORAGE ->
                    v[0] instanceof FfmHandles.Region region
                            && region.session() == session
                            && region.isLive()
                            && v[1] instanceof Boolean write
                            && region.storage().identity() != null
                            && (!write || region.isWritable());
            case COPY_REGION -> admitsCopy(session, v[0], v[1]);
            default -> false;
        };
    }

    /**
     * A whole-region copy needs two live regions of this session, a writable destination, the
     * exact same non-null layout identity and size, segments spanning exactly that size, and the
     * native alignment of both segments. A structurally equal layout of another identity and a
     * byte view without layout identity are refused.
     */
    private static boolean admitsCopy(FfmSession session, Object targetValue, Object sourceValue) {
        if (!(targetValue instanceof FfmHandles.Region target)
                || !(sourceValue instanceof FfmHandles.Region source)) {
            return false;
        }
        FfmTypes.Storage storage = target.storage();
        return target.session() == session
                && source.session() == session
                && target.isLive()
                && source.isLive()
                && target.isWritable()
                && storage.identity() != null
                && source.storage().identity() == storage.identity()
                && source.storage().size() == storage.size()
                && target.segment().byteSize() == storage.size()
                && source.segment().byteSize() == storage.size()
                && target.segment().address() % storage.align() == 0
                && source.segment().address() % storage.align() == 0;
    }

    /**
     * Composes one storage description from Protos-supplied values, or answers null when they
     * are not admitted. Pure: construction validates without touching native state.
     */
    private static FfmTypes.Storage composeOrNull(Op op, Object[] v) {
        try {
            return switch (op) {
                case SCALAR_STORAGE ->
                        v[0] instanceof FfmTypes.Scalar scalar && isIdentity(v[3])
                                ? FfmTypes.Storage.scalar(
                                        scalar, exactLong(v[1]), exactLong(v[2]),
                                        (FfmTypes.PointerType) v[3])
                                : null;
                case POINTER_STORAGE ->
                        v[0] instanceof FfmTypes.PointerType declared && isIdentity(v[1])
                                ? FfmTypes.Storage.pointer(declared, (FfmTypes.PointerType) v[1])
                                : null;
                case AGGREGATE_STORAGE ->
                        v[0] instanceof String kind && isIdentity(v[3])
                                ? FfmTypes.Storage.aggregate(
                                        kind, exactLong(v[1]), exactLong(v[2]),
                                        (FfmTypes.PointerType) v[3])
                                : null;
                case MEMBER_STORAGE ->
                        v[0] instanceof FfmTypes.Storage aggregate
                                        && v[1] instanceof String name
                                        && v[3] instanceof FfmTypes.Storage child
                                ? aggregate.withMember(name, exactLong(v[2]), child)
                                : null;
                case ARRAY_STORAGE ->
                        v[0] instanceof FfmTypes.Storage element && isIdentity(v[3])
                                ? FfmTypes.Storage.array(
                                        element, exactLong(v[1]), exactLong(v[2]),
                                        (FfmTypes.PointerType) v[3])
                                : null;
                default -> null;
            };
        } catch (IllegalArgumentException | ArithmeticException rejected) {
            return null;
        }
    }

    /** A canonical plain non-void pointer descriptor naming one layout. */
    private static boolean isIdentity(Object value) {
        return isPlainPointer(value) && !((FfmTypes.PointerType) value).voidPointee();
    }

    /** An exact Integer in the signed 64-bit range, or an ArithmeticException. */
    private static long exactLong(Object value) {
        BigInteger exact = FfmTypes.exactInteger(value);
        if (exact == null) {
            throw new ArithmeticException("not an Integer");
        }
        return exact.longValueExact();
    }

    /** The admitted {@code [offset, length]} of a slice, or null. */
    private static long[] sliceRange(FfmSession session, Object[] v) {
        if (!(v[0] instanceof FfmHandles.Region region)
                || region.session() != session
                || !region.isLive()) {
            return null;
        }
        try {
            long offset = exactLong(v[1]);
            long length = exactLong(v[2]);
            long size = region.storage().size();
            if (offset < 0 || length < 0 || offset > size || length > size - offset) {
                return null;
            }
            return new long[] {offset, length};
        } catch (ArithmeticException rejected) {
            return null;
        }
    }

    /**
     * A typed view needs a live pointer of this session with an accredited extent that covers
     * the layout, native alignment, and the exact pointee identity or a void pointee.
     */
    private static boolean admitsView(FfmSession session, Object pointerValue, Object layout) {
        return pointerValue instanceof FfmHandles.Pointer pointer
                && layout instanceof FfmTypes.Storage storage
                && storage.identity() != null
                && pointer.session() == session
                && pointer.isLive()
                && pointer.extent() >= storage.size()
                && (pointer.pointee() == storage.identity() || pointer.pointee().voidPointee())
                && pointer.addressForCall().address() % storage.align() == 0;
    }

    /** The exact access Strings of an extent assertion; null for any other value. */
    private static Boolean writeAccess(Object access) {
        if ("read".equals(access)) {
            return Boolean.FALSE;
        }
        if ("readWrite".equals(access)) {
            return Boolean.TRUE;
        }
        return null;
    }

    /** A positive exact byte length in the signed-addressable range, or -1. */
    private static long assertedLength(Object value) {
        BigInteger exact = FfmTypes.exactInteger(value);
        if (exact == null || exact.signum() <= 0 || exact.bitLength() >= Long.SIZE) {
            return -1;
        }
        return exact.longValueExact();
    }

    /**
     * Common admission of an assertion anchored in C-managed lifetime (an owned C resource, the
     * pointer's own borrow epoch, or library-static storage): a live C-origin pointer of this
     * session, a valid length that never widens an accredited extent, and an access that never
     * upgrades a read-only pointer.
     */
    private static boolean admitsCAssertion(
            FfmSession session, FfmHandles.Pointer pointer, Object lengthValue, Object access) {
        long length = assertedLength(lengthValue);
        Boolean write = writeAccess(access);
        return pointer.session() == session
                && pointer.isLive()
                && !pointer.isProtosOrigin()
                && length > 0
                && write != null
                && (!write || pointer.isWritable())
                && (pointer.extent() == FfmHandles.Pointer.UNKNOWN_EXTENT
                        || length <= pointer.extent());
    }

    /** Admission of the private-context assertion {@code (pointer, length, anchor, access)}. */
    private static boolean admitsAssertion(FfmSession session, Object[] v) {
        if (!(v[0] instanceof FfmHandles.Pointer pointer)) {
            return false;
        }
        return switch (v[2]) {
            case FfmHandles.Region root -> {
                long length = assertedLength(v[1]);
                Boolean write = writeAccess(v[3]);
                yield root.session() == session
                        && root.isRoot()
                        && root.isLive()
                        && pointer.session() == session
                        && pointer.isLive()
                        && length > 0
                        && write != null
                        && (!write || pointer.isWritable())
                        && (pointer.extent() == FfmHandles.Pointer.UNKNOWN_EXTENT
                                || length <= pointer.extent())
                        && root.offsetWithin(pointer.addressForCall(), length) >= 0;
            }
            case FfmHandles.Resource resource ->
                    resource.session() == session
                            && resource.isOwned()
                            && resource.isLive()
                            && admitsCAssertion(session, pointer, v[1], v[3]);
            case FfmHandles.Pointer self ->
                    self == pointer
                            && pointer.isEpochGoverned()
                            && admitsCAssertion(session, pointer, v[1], v[3]);
            case null, default -> false;
        };
    }

    private static boolean isPlainPointer(Object value) {
        return value instanceof FfmTypes.PointerType pointer && pointer.isPlain();
    }

    private static boolean isPlainOpaque(Object value, String library) {
        return value instanceof FfmTypes.OpaqueType opaque
                && !opaque.nullable()
                && opaque.library().equals(library);
    }

    private static boolean inLibrary(FfmTypes.Type type, String library) {
        String owner = FfmTypes.libraryOf(type);
        return owner == null || owner.equals(library);
    }

    @Override
    public boolean canFaithfullyReadMember(
            ProtosForeignPluginSession session, Object handle, String name) {
        if (handle instanceof FfmLibraryModule) {
            return MEMBERS.containsKey(name);
        }
        if (handle instanceof FfmSession context) {
            return context == session && CONTEXT_MEMBERS.containsKey(name);
        }
        return handle instanceof FfmHandles.Indexed && name.equals("size");
    }

    @Override
    public Object readMember(ProtosForeignPluginSession session, Object handle, String name) {
        if (handle instanceof FfmHandles.Indexed indexed && name.equals("size")) {
            return indexed.size();
        }
        if (handle instanceof FfmSession context) {
            Object member = CONTEXT_MEMBERS.get(name);
            if (context != session || member == null) {
                throw new FfmFailure("unknown-member", "unknown ffi member");
            }
            return member instanceof Op op ? new Operation(context, op) : member;
        }
        Object member = MEMBERS.get(name);
        if (!(handle instanceof FfmLibraryModule module) || member == null) {
            throw new FfmFailure("unknown-member", "unknown ffi member");
        }
        return member instanceof Op op ? new Operation(module, op) : member;
    }

    @Override
    public Object execute(
            ProtosForeignPluginSession session,
            Object handle,
            List<ProtosForeignArgumentValue> arguments)
            throws Exception {
        FfmSession live = (FfmSession) session;
        if (!acceptsExecution(session, handle, arguments)) {
            throw new FfmFailure("invalid-argument", "arguments do not match the operation");
        }
        try {
            return switch (handle) {
                case FfmBinding binding -> binding.invoke(arguments);
                case Operation operation -> run(live, operation, values(arguments));
                default -> throw new FfmFailure("not-executable", "not an ffi operation");
            };
        } catch (Exception failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new FfmFailure("native-failure", "the native call failed");
        }
    }

    private static Object run(FfmSession session, Operation operation, Object[] v)
            throws Throwable {
        FfmLibraryModule module = operation.module();
        if (module == null) {
            return runOnContext(session, operation.op(), v);
        }
        return switch (operation.op()) {
            case OPAQUE -> new FfmTypes.OpaqueType((String) v[0], module.library(), false);
            case NULLABLE -> ((FfmTypes.OpaqueType) v[0]).asNullable();
            case OWNED -> {
                FfmTypes.OpaqueType opaque = (FfmTypes.OpaqueType) v[0];
                String symbol = (String) v[1];
                FfmBinding destructor =
                        session.bind(
                                module,
                                symbol,
                                new FfmBinding.Signature(
                                        (FfmTypes.Type) v[2], List.of(opaque), false));
                yield new FfmTypes.OwnedType(opaque, symbol, destructor);
            }
            case OUT -> new FfmTypes.OutType((FfmTypes.Type) v[0]);
            case SIGNATURE -> new FfmBinding.Signature((FfmTypes.Type) v[0], List.of(), false);
            case PARAM -> ((FfmBinding.Signature) v[0]).withParameter((FfmTypes.Type) v[1]);
            case PRESERVING -> ((FfmBinding.Signature) v[0]).asPreserving();
            case BIND -> session.bind(module, (String) v[0], (FfmBinding.Signature) v[1]);
            case RELEASE -> session.release((FfmHandles.Resource) v[0]);
            case IS_LIVE ->
                    Boolean.valueOf(
                            switch (v[0]) {
                                case FfmHandles.Provenance live -> live.isLive();
                                default -> ((FfmHandles.Borrowed) v[0]).isValid();
                            });
            case COPY -> {
                session.beginCall();
                try {
                    long length = FfmTypes.exactInteger(v[1]).longValueExact();
                    yield new FfmHandles.Copied(((FfmHandles.Borrowed) v[0]).copy(length));
                } finally {
                    session.endCall();
                }
            }
            case POINTER_TYPE -> new FfmTypes.PointerType((Boolean) v[0]);
            case NULLABLE_POINTER_TYPE -> ((FfmTypes.PointerType) v[0]).asNullable();
            case BORROWED_POINTER_TYPE -> ((FfmTypes.PointerType) v[0]).asBorrowed();
            case ADDRESS_OF -> {
                // Pass-only, owner-backed: the resource keeps its custody and destructor.
                FfmHandles.Resource owner = (FfmHandles.Resource) v[0];
                yield new FfmHandles.Pointer(
                        session,
                        (FfmTypes.PointerType) v[1],
                        owner.addressForCall(),
                        owner,
                        false,
                        0L);
            }
            case ASSUME_STATIC_EXTENT ->
                    accreditC((FfmHandles.Pointer) v[0], v[1], v[2], null, session);
            default -> throw new FfmFailure("not-executable", "not a library operation");
        };
    }

    private static Object runOnContext(FfmSession session, Op op, Object[] v) {
        return switch (op) {
            case POINTER_TYPE -> new FfmTypes.PointerType((Boolean) v[0]);
            case NULLABLE_POINTER_TYPE -> ((FfmTypes.PointerType) v[0]).asNullable();
            case BORROWED_POINTER_TYPE -> ((FfmTypes.PointerType) v[0]).asBorrowed();
            case IS_LIVE -> Boolean.valueOf(((FfmHandles.Provenance) v[0]).isLive());
            case SCALAR_STORAGE, POINTER_STORAGE, AGGREGATE_STORAGE, MEMBER_STORAGE,
                    ARRAY_STORAGE -> composeOrNull(op, v);
            case ALLOCATE -> session.allocate((FfmTypes.Storage) v[0]);
            case RELEASE -> session.releaseAllocation((FfmHandles.Region) v[0]);
            case REGION_ADDRESS -> {
                FfmHandles.Region region = (FfmHandles.Region) v[0];
                FfmTypes.PointerType identity = region.storage().identity();
                session.beginCall();
                try {
                    requireLive(region);
                    // A byte view names no pointee layout: its address is a void pointer.
                    yield new FfmHandles.Pointer(
                            session,
                            identity != null ? identity : (FfmTypes.PointerType) v[1],
                            region.segment(),
                            null,
                            false,
                            0L,
                            region,
                            region.segment().byteSize(),
                            region.isWritable(),
                            region.isProtosOrigin());
                } finally {
                    session.endCall();
                }
            }
            case SLICE -> {
                long[] range = sliceRange(session, v);
                session.beginCall();
                try {
                    FfmHandles.Region region = (FfmHandles.Region) v[0];
                    requireLive(region);
                    yield region.nested(range[0], FfmTypes.Storage.bytes(range[1]));
                } finally {
                    session.endCall();
                }
            }
            case VIEW -> {
                FfmHandles.Pointer pointer = (FfmHandles.Pointer) v[0];
                session.beginCall();
                try {
                    FfmTypes.Storage storage = (FfmTypes.Storage) v[1];
                    yield FfmHandles.Region.view(pointer, pointer.addressForCall(), storage);
                } finally {
                    session.endCall();
                }
            }
            case ASSUME_EXTENT -> {
                FfmHandles.Pointer pointer = (FfmHandles.Pointer) v[0];
                yield switch (v[2]) {
                    case FfmHandles.Region root -> {
                        long length = assertedLength(v[1]);
                        session.beginCall();
                        try {
                            requireLive(root);
                            long offset = root.offsetWithin(pointer.addressForCall(), length);
                            if (offset < 0) {
                                throw new FfmFailure("invalid-extent", "outside the allocation");
                            }
                            // Inside the actual allocation: a bounded slice, never reinterpret.
                            yield pointer.accredit(
                                    root.segment().asSlice(offset, length),
                                    root,
                                    writeAccess(v[3]));
                        } finally {
                            session.endCall();
                        }
                    }
                    case FfmHandles.Resource resource ->
                            accreditC(pointer, v[1], v[3], resource, session);
                    default -> accreditC(pointer, v[1], v[3], null, session);
                };
            }
            case REGION_STORAGE -> ((FfmHandles.Region) v[0]).storage();
            case COPY_REGION -> {
                session.beginCall();
                try {
                    // Re-checked inside the admitted operation; only a concurrent close fails here.
                    if (!admitsCopy(session, v[0], v[1])) {
                        throw new FfmFailure(
                                "invalid-argument", "the native regions reject the copy");
                    }
                    FfmHandles.Region target = (FfmHandles.Region) v[0];
                    MemorySegment.copy(
                            ((FfmHandles.Region) v[1]).segment(),
                            0,
                            target.segment(),
                            0,
                            target.storage().size());
                    yield target;
                } finally {
                    session.endCall();
                }
            }
            default -> throw new FfmFailure("not-executable", "not a context operation");
        };
    }

    /**
     * The trusted C-lifetime accreditation of an already admitted pointer: its known bounded
     * segment is narrowed, or an unknown extent is asserted through {@code reinterpret}. The
     * assertion is trusted; it does not prove that C storage is valid.
     */
    private static FfmHandles.Pointer accreditC(
            FfmHandles.Pointer pointer,
            Object lengthValue,
            Object access,
            FfmHandles.Provenance anchor,
            FfmSession session) {
        long length = assertedLength(lengthValue);
        session.beginCall();
        try {
            MemorySegment address = pointer.addressForCall();
            MemorySegment bounded =
                    pointer.extent() == FfmHandles.Pointer.UNKNOWN_EXTENT
                            ? address.reinterpret(length)
                            : address.asSlice(0, length);
            return pointer.accredit(bounded, anchor, writeAccess(access));
        } finally {
            session.endCall();
        }
    }

    private static void requireLive(FfmHandles.Provenance capability) {
        if (!capability.isLive()) {
            throw new FfmFailure("expired-region", "the native memory is no longer valid");
        }
    }

    /** One resolved position of a native region: its offset and its storage. */
    private record Position(long offset, FfmTypes.Storage storage) {}

    /**
     * Resolves an exact key of a live region without touching memory: an existing member name of
     * a struct or union, an in-bounds exact Integer of an array or byte view, or exact zero of a
     * scalar or pointer region. Bounds and native alignment are re-checked on the segment.
     */
    private static Position position(FfmSession session, Object handle, Object key) {
        if (!(handle instanceof FfmHandles.Region region)
                || region.session() != session
                || !region.isLive()) {
            return null;
        }
        FfmTypes.Storage storage = region.storage();
        Position position =
                switch (storage.kind()) {
                    case STRUCT, UNION -> {
                        FfmTypes.Storage.Member member =
                                key instanceof String name ? storage.member(name) : null;
                        yield member == null
                                ? null
                                : new Position(member.offset(), member.layout());
                    }
                    case ARRAY -> {
                        BigInteger index = FfmTypes.exactInteger(key);
                        BigInteger count = BigInteger.valueOf(storage.count());
                        yield index == null || index.signum() < 0 || index.compareTo(count) >= 0
                                ? null
                                : new Position(
                                        index.longValueExact() * storage.stride(),
                                        storage.element());
                    }
                    case SCALAR, POINTER -> {
                        BigInteger index = FfmTypes.exactInteger(key);
                        yield index != null && index.signum() == 0
                                ? new Position(0, storage)
                                : null;
                    }
                };
        if (position == null) {
            return null;
        }
        MemorySegment segment = region.segment();
        long size = position.storage().size();
        if (position.offset() > segment.byteSize() - size
                || (segment.address() + position.offset()) % position.storage().align() != 0) {
            return null;
        }
        return position;
    }

    /** Whether {@code argument} may be stored at {@code position} of the writable region. */
    private static boolean admitsStore(
            FfmSession session, Position position, ProtosForeignArgumentValue argument) {
        FfmTypes.Storage storage = position.storage();
        Object value = argument.value();
        boolean absent = argument.kind() == ProtosForeignValueClass.Kind.NULL;
        return switch (storage.kind()) {
            case SCALAR -> !absent && FfmTypes.admitsScalar(storage.scalar(), value);
            case POINTER -> {
                FfmTypes.PointerType declared = storage.pointer();
                if (absent) {
                    yield declared.nullable();
                }
                yield value instanceof FfmHandles.Pointer pointer
                        && pointer.session() == session
                        && pointer.isLive()
                        && (pointer.pointee() == declared.identity() || declared.voidPointee());
            }
            // Compatible aggregate bytes: the exact same layout identity and size, live.
            case STRUCT, UNION, ARRAY ->
                    value instanceof FfmHandles.Region source
                            && source.session() == session
                            && source.isLive()
                            && storage.identity() != null
                            && source.storage().identity() == storage.identity()
                            && source.storage().size() == storage.size();
        };
    }

    @Override
    public boolean acceptsElementRead(
            ProtosForeignPluginSession session, Object handle, ProtosForeignArgumentValue index) {
        if (handle instanceof FfmHandles.Indexed) {
            // Existing private results keep their entered out-of-range failure.
            return true;
        }
        return session instanceof FfmSession live
                && live.isUsable()
                && position(live, handle, index.value()) != null;
    }

    @Override
    public boolean acceptsElementWrite(
            ProtosForeignPluginSession session,
            Object handle,
            ProtosForeignArgumentValue index,
            ProtosForeignArgumentValue value) {
        if (!(session instanceof FfmSession live) || !live.isUsable()) {
            return false;
        }
        Position position = position(live, handle, index.value());
        return position != null
                && ((FfmHandles.Region) handle).isWritable()
                && admitsStore(live, position, value);
    }

    @Override
    public Object readElement(
            ProtosForeignPluginSession session, Object handle, ProtosForeignArgumentValue index) {
        if (handle instanceof FfmHandles.Region region) {
            return readRegion((FfmSession) session, region, index.value());
        }
        BigInteger position = FfmTypes.exactInteger(index.value());
        if (!(handle instanceof FfmHandles.Indexed indexed)
                || position == null
                || position.signum() < 0
                || position.compareTo(BigInteger.valueOf(indexed.size())) >= 0) {
            throw new FfmFailure("invalid-index", "index out of range");
        }
        return indexed.element(position.longValueExact());
    }

    /**
     * One typed read: a scalar through the shared carrier conversion, a pointer field as a
     * pass-only pointer that retains the region (native NULL is null only when nullable), and a
     * nested aggregate as a bounded non-owning view.
     */
    private static Object readRegion(FfmSession session, FfmHandles.Region region, Object key) {
        session.beginCall();
        try {
            Position position = position(session, region, key);
            if (position == null) {
                throw new FfmFailure("invalid-index", "the native region rejects the index");
            }
            FfmTypes.Storage storage = position.storage();
            MemorySegment segment = region.segment();
            return switch (storage.kind()) {
                case SCALAR ->
                        FfmBinding.fromNativeScalar(
                                storage.scalar(),
                                FfmBinding.readCarrier(
                                        storage.scalar(), segment, position.offset()));
                case POINTER -> {
                    MemorySegment address = segment.get(ValueLayout.ADDRESS, position.offset());
                    if (address.equals(MemorySegment.NULL)) {
                        if (!storage.pointer().nullable()) {
                            throw new FfmFailure(
                                    "null-result", "a non-nullable pointer field was NULL");
                        }
                        yield FfmHandles.Absent.INSTANCE;
                    }
                    // Memory content: no extent is inferred and its validity is C's.
                    yield new FfmHandles.Pointer(
                            session,
                            storage.pointer().identity(),
                            address,
                            null,
                            false,
                            0L,
                            region,
                            FfmHandles.Pointer.UNKNOWN_EXTENT,
                            region.isWritable(),
                            false);
                }
                case STRUCT, UNION, ARRAY -> region.nested(position.offset(), storage);
            };
        } finally {
            session.endCall();
        }
    }

    @Override
    public void writeElement(
            ProtosForeignPluginSession session,
            Object handle,
            ProtosForeignArgumentValue index,
            ProtosForeignArgumentValue value) {
        FfmSession live = (FfmSession) session;
        if (!(handle instanceof FfmHandles.Region region)) {
            throw new FfmFailure("not-writable", "not a native region");
        }
        live.beginCall();
        try {
            // Re-checked inside the admitted operation; only a concurrent close can fail here.
            // The one resolved position serves both the check and the physical write.
            Position position = position(live, region, index.value());
            if (position == null
                    || !region.isWritable()
                    || !admitsStore(live, position, value)) {
                throw new FfmFailure("invalid-argument", "the native region rejects the write");
            }
            FfmTypes.Storage storage = position.storage();
            MemorySegment segment = region.segment();
            switch (storage.kind()) {
                case SCALAR ->
                        FfmBinding.writeCarrier(
                                storage.scalar(),
                                segment,
                                position.offset(),
                                FfmTypes.nativeScalar(storage.scalar(), value.value()));
                case POINTER ->
                        segment.set(
                                ValueLayout.ADDRESS,
                                position.offset(),
                                value.kind() == ProtosForeignValueClass.Kind.NULL
                                        ? MemorySegment.NULL
                                        : ((FfmHandles.Pointer) value.value()).addressForCall());
                case STRUCT, UNION, ARRAY ->
                        MemorySegment.copy(
                                ((FfmHandles.Region) value.value()).segment(),
                                0,
                                segment,
                                position.offset(),
                                storage.size());
            }
        } finally {
            live.endCall();
        }
    }

    @Override
    public ProtosForeignFailureInfo describeFailure(Throwable failure) {
        if (failure instanceof FfmFailure known) {
            return new ProtosForeignFailureInfo("ffi", known.category(), known.getMessage());
        }
        return new ProtosForeignFailureInfo("ffi", null, null);
    }
}

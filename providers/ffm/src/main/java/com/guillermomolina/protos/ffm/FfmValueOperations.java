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
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single I085 value contract of the FFM provider.
 *
 * <p>Classification: library modules (member read), declaration operations and bindings
 * (executable), ABI descriptors and signatures, opaque and owned references, borrowed pointers
 * (capability-free handles that only travel back as arguments), provider-private indexable results
 * (indexed read and a {@code size} member), C scalars, and the C {@code void} absence.
 *
 * <p>The members of a library module are the minimal provider-private operations a future
 * Protos-source {@code std:ffi} needs: descriptors ({@code void}, {@code i32}, {@code i64}, {@code
 * f64}, {@code sizeT}, {@code utf8z}, {@code borrowed}, and the LIB030 private input descriptors
 * {@code text}, {@code octets}, {@code inputLength}, {@code copyDestructor} and {@code
 * remainder}), {@code opaque(name)}, {@code
 * nullable(opaque)}, {@code owned(opaque, destructorSymbol, void|i32)}, {@code out(type)}, {@code
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

    /** One declaration operation of one library module. */
    record Operation(FfmLibraryModule module, Op op) {
        @Override
        public String toString() {
            return "ffi-operation(" + op.member + ")";
        }
    }

    enum Op {
        OPAQUE("opaque", 1),
        NULLABLE("nullable", 1),
        OWNED("owned", 3),
        OUT("out", 1),
        SIGNATURE("signature", 1),
        PARAM("param", 2),
        PRESERVING("preserving", 1),
        BIND("bind", 2),
        RELEASE("release", 1),
        IS_LIVE("isLive", 1),
        COPY("copy", 2);

        final String member;
        final int arity;

        Op(String member, int arity) {
            this.member = member;
            this.arity = arity;
        }
    }

    private static final Map<String, Object> MEMBERS = members();

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
            members.put(op.member, op);
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
            default -> throw new FfmFailure("internal", "unclassifiable provider value");
        };
    }

    @Override
    public boolean acceptsArgument(ProtosForeignArgumentValue argument) {
        return argument.kind() != ProtosForeignValueClass.Kind.BOOLEAN;
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

    /** Pure validation of one declaration operation; it touches no native state. */
    private static boolean admits(FfmSession session, Operation operation, Object[] v) {
        FfmLibraryModule module = operation.module();
        if (module.session() != session || v.length != operation.op().arity) {
            return false;
        }
        String library = module.library();
        return switch (operation.op()) {
            case OPAQUE -> FfmTypes.isIdentifier(v[0]);
            case NULLABLE -> isPlainOpaque(v[0], library);
            case OWNED ->
                    isPlainOpaque(v[0], library)
                            && FfmTypes.isIdentifier(v[1])
                            && (v[2] == FfmTypes.Scalar.VOID || v[2] == FfmTypes.Scalar.I32);
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
                            && signature.belongsTo(library);
            case RELEASE ->
                    v[0] instanceof FfmHandles.Resource resource
                            && resource.session() == session
                            && resource.isOwned()
                            && resource.isLive();
            case IS_LIVE ->
                    (v[0] instanceof FfmHandles.Resource resource && resource.session() == session)
                            || (v[0] instanceof FfmHandles.Borrowed borrowed
                                    && borrowed.session() == session);
            case COPY -> {
                BigInteger length = FfmTypes.exactInteger(v[1]);
                yield v[0] instanceof FfmHandles.Borrowed borrowed
                        && borrowed.session() == session
                        && borrowed.isValid()
                        && length != null
                        && length.signum() >= 0
                        && length.compareTo(BigInteger.valueOf(FfmHandles.MAX_COPY_BYTES)) <= 0;
            }
        };
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
        return handle instanceof FfmHandles.Indexed && name.equals("size");
    }

    @Override
    public Object readMember(ProtosForeignPluginSession session, Object handle, String name) {
        if (handle instanceof FfmHandles.Indexed indexed && name.equals("size")) {
            return indexed.size();
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
                            v[0] instanceof FfmHandles.Resource resource
                                    ? resource.isLive()
                                    : ((FfmHandles.Borrowed) v[0]).isValid());
            case COPY -> {
                session.beginCall();
                try {
                    long length = FfmTypes.exactInteger(v[1]).longValueExact();
                    yield new FfmHandles.Copied(((FfmHandles.Borrowed) v[0]).copy(length));
                } finally {
                    session.endCall();
                }
            }
        };
    }

    @Override
    public Object readElement(
            ProtosForeignPluginSession session, Object handle, ProtosForeignArgumentValue index) {
        BigInteger position = FfmTypes.exactInteger(index.value());
        if (!(handle instanceof FfmHandles.Indexed indexed)
                || position == null
                || position.signum() < 0
                || position.compareTo(BigInteger.valueOf(indexed.size())) >= 0) {
            throw new FfmFailure("invalid-index", "index out of range");
        }
        return indexed.element(position.longValueExact());
    }

    @Override
    public ProtosForeignFailureInfo describeFailure(Throwable failure) {
        if (failure instanceof FfmFailure known) {
            return new ProtosForeignFailureInfo("ffi", known.category(), known.getMessage());
        }
        return new ProtosForeignFailureInfo("ffi", null, null);
    }
}

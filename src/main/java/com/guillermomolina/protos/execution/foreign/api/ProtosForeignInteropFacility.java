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

import com.guillermomolina.protos.execution.ProtosForeignAdmissionDescriptor.Capability;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActor;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Private runtime facility of {@code std:interop} (D193, LIB021-A): the explicit foreign
 * operations that Protos source cannot express. The public API is the ordinary Protos module
 * {@code protos/lib/interop/module.protos}, which captures this frozen, stateless facility from its
 * standard initial member and removes the bootstrap slot during initialization.
 *
 * <p>Every operation acts only on a raw foreign reference or attached facade the program already
 * possesses ({@link ProtosForeignHandle#of}), under that handle's exact provider, session, and
 * generation; nothing here opens, selects, or rebinds a provider. Outbound values and Closure
 * callbacks cross through the same D188/D189 path as the ordinary projection, normal results are
 * admitted by D188, and {@link ProtosForeignOperation} owns the entered/not-entered boundary.
 * Ordinary lookup, assignment, and {@code call} projection are not consulted or changed.
 *
 * <p>Each operation requires a pre-entry declaration in the target's admission classification:
 * {@link Capability#EXECUTABLE} for {@code invoke}, {@link Capability#INSTANTIABLE} for {@code
 * instantiate}, and {@link Capability#MEMBER_READ} / {@link Capability#MEMBER_WRITE} for the
 * member families. {@code readMember} deliberately ignores the ordinary per-member fidelity rule
 * ({@link ProtosForeignValueAdapter#canFaithfullyReadMember}).
 */
public final class ProtosForeignInteropFacility {
    public static final ProtosModuleKey MODULE_KEY = ProtosModuleKey.module("std:interop");
    public static final String BOOTSTRAP_SLOT = "_interopFacility";

    /** Private bootstrap slot of {@code std:ffi}; never part of {@code std:interop}. */
    public static final String FFI_NATIVE_CONTEXT_SLOT = "_ffiNativeContextFacility";

    @FunctionalInterface
    private interface Construction {
        Object run(
                ProtosForeignValueAdapter adapter,
                ProtosForeignProviderSession live,
                Object target,
                List<ProtosForeignArgument> arguments)
                throws Exception;
    }

    private ProtosForeignInteropFacility() {}

    /** Creates the frozen facility; it holds no session, provider, or other mutable state. */
    public static ProtosObjectValue createFacility() {
        ProtosObjectValue facility = new ProtosObjectValue(ProtosObjectValue.rootObject());
        facility.createLocalSlot(
                "invoke", ProtosClosureValue.nativeClosure(ProtosForeignInteropFacility::invoke));
        facility.createLocalSlot(
                "instantiate",
                ProtosClosureValue.nativeClosure(ProtosForeignInteropFacility::instantiate));
        facility.createLocalSlot(
                "readMember",
                ProtosClosureValue.nativeClosure(ProtosForeignInteropFacility::readMember));
        facility.createLocalSlot(
                "writeMember",
                ProtosClosureValue.nativeClosure(ProtosForeignInteropFacility::writeMember));
        return facility.freeze();
    }

    /**
     * Creates the frozen private native-context facility of {@code std:ffi}. Like {@link
     * #createFacility} it holds no session, provider or native-memory state: its {@code acquire}
     * operation resolves, on each use, the current RootActor's existing native provider session
     * and answers that same session's private context capability, and its {@code
     * isLibraryAnchor} operation recognizes an attached native library facade without entering
     * foreign code.
     */
    public static ProtosObjectValue createNativeContextFacility() {
        ProtosObjectValue facility = new ProtosObjectValue(ProtosObjectValue.rootObject());
        facility.createLocalSlot(
                "acquire",
                ProtosClosureValue.nativeClosure(
                        ProtosForeignInteropFacility::acquireNativeContext));
        facility.createLocalSlot(
                "isLibraryAnchor",
                ProtosClosureValue.nativeClosure(ProtosForeignInteropFacility::isLibraryAnchor));
        return facility.freeze();
    }

    /**
     * Pure representation recognition of a library-static extent anchor: {@code true} only for
     * an actually attached foreign MODULE facade of the native FFM provider, in the current
     * RootActor, whose open session binding and adapter are exactly those of the supplied raw
     * foreign pointer, and whose target declares member read. It performs no foreign operation:
     * it acquires no session, reads no member, invokes no provider hook and evaluates no guest
     * code, so an ordinary object, a raw foreign reference or another provider's facade is
     * refused without any side effect. Provider admission of the anchored operation still
     * applies afterwards.
     */
    @TruffleBoundary
    private static Object isLibraryAnchor(ProtosActivation activation, List<?> supplied) {
        ProtosPrelude prelude = ProtosForeignProjectedOperations.prelude(activation);
        if (supplied.size() != 2) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        ProtosActor actor = activation.executionDomain().currentActorForRuntime().orElse(null);
        if (actor == null || !actor.isRootActorForRuntime()) {
            return Boolean.FALSE;
        }
        if (!(supplied.get(0) instanceof com.guillermomolina.protos.runtime.ProtosRawForeignValue)
                || !(supplied.get(1) instanceof ProtosForeignModuleFacadeValue facade)) {
            return Boolean.FALSE;
        }
        ProtosForeignHandle pointer = ProtosForeignHandle.of(supplied.get(0));
        ProtosForeignModuleFacadeValue.Attachment attachment =
                facade.attachmentForRuntime().orElse(null);
        if (pointer == null || attachment == null) {
            return Boolean.FALSE;
        }
        ProtosForeignHandle library = attachment.handle();
        ProtosForeignProviderSessionBinding session = library.session();
        return Boolean.valueOf(
                session == pointer.session()
                        && library.adapter() == pointer.adapter()
                        && ProtosExternalProviderPluginLoader.INTEGRATED_PROVIDER_ID.equals(
                                session.providerId().value())
                        && session.isOpenForRuntime()
                        && library.has(Capability.MEMBER_READ));
    }

    /**
     * Runtime authority is established before any provider code runs: the current Actor must be
     * its Process's RootActor, the hosting Process must register the native provider, and that
     * provider must pass the existing admission. Each refusal is the ordinary pre-entry Error.
     * The session is the one {@code ffi:} imports of the same Actor use; the context request
     * itself is an entered foreign operation and its result crosses the ordinary value admission.
     */
    @TruffleBoundary
    private static Object acquireNativeContext(ProtosActivation activation, List<?> supplied) {
        ProtosPrelude prelude = ProtosForeignProjectedOperations.prelude(activation);
        if (!supplied.isEmpty()) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        ProtosActor actor = activation.executionDomain().currentActorForRuntime().orElse(null);
        if (actor == null || !actor.isRootActorForRuntime()) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        if (!(actor.processForRuntime()
                        .flatMap(process -> process.executionHostForRuntime())
                        .orElse(null)
                instanceof ProtosPolyglotProcessContext host)) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        ProtosForeignProviderDescriptor descriptor =
                host.foreignProviderRegistryForRuntime()
                        .lookup(
                                new ProtosForeignProviderId(
                                        ProtosExternalProviderPluginLoader.INTEGRATED_PROVIDER_ID))
                        .orElse(null);
        if (descriptor == null
                || ProtosForeignProviderAdmission.rejection(descriptor).isPresent()) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        ProtosForeignProviderSessionBinding session;
        try {
            session = host.foreignSessionForRuntime(actor, descriptor.id());
        } catch (RuntimeException refused) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        ProtosForeignValueAdapter values = descriptor.values();
        return ProtosForeignOperation.enter(
                session,
                values,
                "nativeContext",
                prelude,
                live ->
                        ProtosForeignValueAdmission.admit(
                                session,
                                values,
                                live,
                                Objects.requireNonNull(
                                        values.acquireSessionContext(live),
                                        "foreign session context"),
                                prelude));
    }

    private static Object invoke(ProtosActivation activation, List<?> supplied) {
        // Reuses the substrate's "execute" operation; the public name does not fork it.
        return construct(
                activation,
                supplied,
                Capability.EXECUTABLE,
                "execute",
                (adapter, live, target, arguments) -> adapter.execute(live, target, arguments));
    }

    private static Object instantiate(ProtosActivation activation, List<?> supplied) {
        return construct(
                activation,
                supplied,
                Capability.INSTANTIABLE,
                "instantiate",
                (adapter, live, target, arguments) ->
                        adapter.instantiate(live, target, arguments));
    }

    @TruffleBoundary
    private static Object construct(
            ProtosActivation activation,
            List<?> supplied,
            Capability required,
            String operation,
            Construction construction) {
        ProtosPrelude prelude = ProtosForeignProjectedOperations.prelude(activation);
        if (supplied.isEmpty()) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        ProtosForeignHandle handle = requireHandle(supplied.get(0), required, prelude);
        ProtosForeignProjectedOperations.Outbound outbound =
                new ProtosForeignProjectedOperations.Outbound(handle, activation, prelude);
        try {
            List<ProtosForeignArgument> arguments = new ArrayList<>(supplied.size() - 1);
            for (Object argument : supplied.subList(1, supplied.size())) {
                arguments.add(outbound.export(argument));
            }
            List<ProtosForeignArgument> exported = List.copyOf(arguments);
            if (required == Capability.EXECUTABLE) {
                ProtosForeignOperation.requireAdmissibleExecution(handle, exported, prelude);
            }
            return ProtosForeignOperation.enter(
                    handle,
                    operation,
                    prelude,
                    outbound.callbacks,
                    live ->
                            ProtosForeignProjectedOperations.admit(
                                    handle,
                                    live,
                                    construction.run(
                                            handle.adapter(), live, handle.target(), exported),
                                    prelude));
        } finally {
            outbound.expire();
        }
    }

    @TruffleBoundary
    private static Object readMember(ProtosActivation activation, List<?> supplied) {
        ProtosPrelude prelude = ProtosForeignProjectedOperations.prelude(activation);
        if (supplied.size() != 2) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        ProtosForeignHandle handle =
                requireHandle(supplied.get(0), Capability.MEMBER_READ, prelude);
        String name = requireName(supplied.get(1), prelude);
        return ProtosForeignOperation.enter(
                handle,
                "readMember",
                prelude,
                live ->
                        ProtosForeignProjectedOperations.admit(
                                handle,
                                live,
                                handle.adapter().readMember(live, handle.target(), name),
                                prelude));
    }

    /** Answers the exact supplied {@code value}: no readback, readmission, or provider token. */
    @TruffleBoundary
    private static Object writeMember(ProtosActivation activation, List<?> supplied) {
        ProtosPrelude prelude = ProtosForeignProjectedOperations.prelude(activation);
        if (supplied.size() != 3) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        ProtosForeignHandle handle =
                requireHandle(supplied.get(0), Capability.MEMBER_WRITE, prelude);
        String name = requireName(supplied.get(1), prelude);
        ProtosForeignProjectedOperations.Outbound outbound =
                new ProtosForeignProjectedOperations.Outbound(handle, activation, prelude);
        try {
            ProtosForeignArgument value = outbound.export(supplied.get(2));
            ProtosForeignOperation.enter(
                    handle,
                    "writeMember",
                    prelude,
                    outbound.callbacks,
                    live -> {
                        handle.adapter().writeMember(live, handle.target(), name, value);
                        return null;
                    });
        } finally {
            outbound.expire();
        }
        return supplied.get(2);
    }

    /** A possessed foreign target whose classification declares {@code required}. */
    private static ProtosForeignHandle requireHandle(
            Object target, Capability required, ProtosPrelude prelude) {
        ProtosForeignHandle handle = ProtosForeignHandle.of(target);
        if (handle == null || !handle.has(required)) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        return handle;
    }

    private static String requireName(Object name, ProtosPrelude prelude) {
        if (name instanceof ProtosStringValue string) {
            return string.value();
        }
        throw ProtosForeignOperation.ordinaryError(prelude);
    }
}

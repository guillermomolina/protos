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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSlotLookupResult;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * D188 Protos-facing projection of raw foreign references and attached foreign module facades.
 *
 * <p>Lookup order for a raw reference: the projected institutions ({@code call}, {@code at},
 * {@code atPut}, {@code each}) that its classification makes faithful and unambiguous; then the
 * ordinary chain of its delegation parent, the root Object, which supplies {@code ==} and
 * {@code hash} but never {@code Object.call}; then a faithful foreign-member fallback whose result
 * is admitted; otherwise the ordinary missing-member failure. A facade is an ordinary object, so
 * its own chain comes first and the projection of its target follows a miss. Institution names are
 * never satisfied by a same-spelling foreign member, and writes are never redirected.
 *
 * <p>Projected members are native Closures selected with the private {@link #PROJECTION_HOME};
 * reading one binds it to the receiver like any method. {@code foreign.member(args)} is the read
 * followed by ordinary invocation of the read value ({@link #isForeignMemberSelection}). Projected
 * {@code each} is a Protos-side pull loop ({@link ProtosForeignEachCall}); inside a Task the
 * structured dispatcher recognizes its body ({@link #isEachImplementation}) and runs the same
 * cursor with ordinary structured callback invocation.
 *
 * <p>A Closure argument of {@code call}, {@code at}, or {@code atPut} crosses only as a D189
 * callback capability scoped to that one operation ({@link ProtosForeignCallbackScope}); every
 * other non-scalar, non-raw argument still fails before entry.
 */
final class ProtosForeignProjectedOperations {
    private static final Set<String> INSTITUTIONS =
            Set.of("call", "at", "atPut", "each", "==", "hash");

    /**
     * Selection home of projected members. It is not reachable from guest code: native bodies never
     * perform {@code super}, and projected Closures never cross an Actor or P boundary.
     */
    private static final ProtosObjectValue PROJECTION_HOME =
            new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();

    /** Marker of every native body owned by this projection; such Closures never transfer. */
    private interface ProjectedBody extends ProtosNativeClosureBody {}

    private static final ProtosClosureValue CALL =
            ProtosClosureValue.nativeClosure(
                    (ProjectedBody) ProtosForeignProjectedOperations::call);
    private static final ProtosClosureValue AT =
            ProtosClosureValue.nativeClosure((ProjectedBody) ProtosForeignProjectedOperations::at);
    private static final ProtosClosureValue AT_PUT =
            ProtosClosureValue.nativeClosure(
                    (ProjectedBody) ProtosForeignProjectedOperations::atPut);
    private static final ProjectedBody EACH_BODY = ProtosForeignProjectedOperations::each;
    private static final ProtosClosureValue EACH = ProtosClosureValue.nativeClosure(EACH_BODY);

    private static final Object NOT_FAITHFUL = new Object();

    private ProtosForeignProjectedOperations() {}

    static Optional<ProtosSlotLookupResult> lookupRaw(
            ProtosForeignHandle handle, String name, ProtosPrelude prelude) {
        ProtosClosureValue institution = institutionOrNull(handle, name);
        if (institution != null) {
            return selected(institution);
        }
        if (name.equals("call")) {
            // A non-executable raw reference has no hidden callability.
            return Optional.empty();
        }
        Optional<ProtosSlotLookupResult> ordinary =
                ProtosValueLookup.lookupOrdinaryChain(
                        ProtosObjectValue.rootObject(), name, prelude);
        return ordinary.isPresent() ? ordinary : fallback(handle, name, prelude);
    }

    static Optional<ProtosSlotLookupResult> lookupFacade(
            ProtosForeignModuleFacadeValue facade,
            ProtosForeignHandle handle,
            String name,
            ProtosPrelude prelude) {
        Optional<ProtosSlotLookupResult> ordinary =
                ProtosValueLookup.lookupOrdinaryChain(facade, name, prelude);
        if (ordinary.isPresent() || handle == null) {
            return ordinary;
        }
        ProtosClosureValue institution = institutionOrNull(handle, name);
        return institution != null ? selected(institution) : fallback(handle, name, prelude);
    }

    /**
     * Publishes the projected {@code call} as an ordinary local slot of a facade whose provider
     * deliberately documents it ({@link ProtosForeignModuleProvider#publishesFacadeCall}); only an
     * unambiguously executable target gets one. The slot then shadows the inherited {@code
     * Object.call} by ordinary lookup, and its activation is the projected execution of the
     * facade's attached target.
     */
    static void publishFacadeCall(ProtosForeignModuleFacadeValue facade, ProtosForeignHandle handle) {
        if (handle.projectsCall()) {
            facade.createLocalSlot("call", CALL);
        }
    }

    private static ProtosClosureValue institutionOrNull(ProtosForeignHandle handle, String name) {
        return switch (name) {
            case "call" -> handle.projectsCall() ? CALL : null;
            case "at" -> handle.projectsAt() ? AT : null;
            case "atPut" -> handle.projectsAtPut() ? AT_PUT : null;
            case "each" -> handle.projectsEach() ? EACH : null;
            default -> null;
        };
    }

    private static Optional<ProtosSlotLookupResult> selected(Object value) {
        return Optional.of(new ProtosSlotLookupResult(value, PROJECTION_HOME));
    }

    private static Optional<ProtosSlotLookupResult> fallback(
            ProtosForeignHandle handle, String name, ProtosPrelude prelude) {
        if (INSTITUTIONS.contains(name)) {
            return Optional.empty();
        }
        Object value =
                ProtosForeignOperation.enter(
                        handle,
                        "readMember",
                        prelude,
                        live -> {
                            if (!handle.adapter()
                                    .canFaithfullyReadMember(live, handle.target(), name)) {
                                return NOT_FAITHFUL;
                            }
                            return ProtosForeignValueAdmission.admit(
                                    handle.session(),
                                    handle.adapter(),
                                    live,
                                    handle.adapter().readMember(live, handle.target(), name));
                        });
        return value == NOT_FAITHFUL ? Optional.empty() : selected(value);
    }

    /**
     * True when a send selected a foreign-member fallback value. Such a value is not a method:
     * the send is the member read followed by ordinary invocation of the read value, so the send
     * site re-targets to that value and its {@code call} selection ({@link #invocationSelection}).
     */
    static boolean isForeignMemberSelection(ProtosSlotLookupResult selected) {
        return selected.home() == PROJECTION_HOME
                && !(selected.value() instanceof ProtosClosureValue);
    }

    /** The ordinary call-protocol selection of {@code value}, failing like any invocation. */
    static ProtosSlotLookupResult invocationSelection(Object value, ProtosActivation caller) {
        try {
            return ProtosValueLookup.lookup(value, "call", caller.preludeOrNullForRuntime())
                    .orElseThrow(
                            () ->
                                    new com.guillermomolina.protos.runtime.ProtosSignalException(
                                            com.guillermomolina.protos.runtime.ProtosCoreErrors
                                                    .newError(caller)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new com.guillermomolina.protos.runtime.ProtosSignalException(
                    com.guillermomolina.protos.runtime.ProtosCoreErrors.newError(caller));
        }
    }

    /** True for projected Closures, which carry provider-session state through their receiver. */
    static boolean isProjectionClosure(ProtosClosureValue closure) {
        return closure.nativeBody().orElse(null) instanceof ProjectedBody;
    }

    /** True for the projected {@code each} body, whose loop needs structured dispatch in a Task. */
    static boolean isEachImplementation(ProtosNativeClosureBody body) {
        return body == EACH_BODY;
    }

    /** The non-Task path; inside a Task the structured dispatcher drives the same cursor. */
    private static Object each(ProtosActivation activation, List<?> supplied) {
        ProtosForeignEachCall each =
                new ProtosForeignEachCall(activation.receiver(), supplied, activation);
        while (each.hasNext()) {
            ProtosInvocation.invoke(each.callback(), List.of(each.current()), activation);
            each.advance();
        }
        return each.finish();
    }

    private static Object call(ProtosActivation activation, List<?> supplied) {
        ProtosForeignHandle handle = requireHandle(activation);
        ProtosPrelude prelude = prelude(activation);
        if (!handle.projectsCall()) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        Outbound outbound = new Outbound(handle, activation, prelude);
        try {
            List<ProtosForeignArgument> arguments = new ArrayList<>(supplied.size());
            for (Object argument : supplied) {
                arguments.add(outbound.export(argument));
            }
            List<ProtosForeignArgument> exported = List.copyOf(arguments);
            return ProtosForeignOperation.enter(
                    handle,
                    "execute",
                    prelude,
                    outbound.callbacks,
                    live ->
                            admit(
                                    handle,
                                    live,
                                    handle.adapter().execute(live, handle.target(), exported)));
        } finally {
            outbound.expire();
        }
    }

    private static Object at(ProtosActivation activation, List<?> supplied) {
        ProtosForeignHandle handle = requireHandle(activation);
        ProtosPrelude prelude = prelude(activation);
        if (!handle.projectsAt() || supplied.size() != 1) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        Outbound outbound = new Outbound(handle, activation, prelude);
        try {
            ProtosForeignArgument index = outbound.export(supplied.get(0));
            return ProtosForeignOperation.enter(
                    handle,
                    "readElement",
                    prelude,
                    outbound.callbacks,
                    live ->
                            admit(
                                    handle,
                                    live,
                                    handle.adapter().readElement(live, handle.target(), index)));
        } finally {
            outbound.expire();
        }
    }

    private static Object atPut(ProtosActivation activation, List<?> supplied) {
        ProtosForeignHandle handle = requireHandle(activation);
        ProtosPrelude prelude = prelude(activation);
        if (!handle.projectsAtPut() || supplied.size() != 2) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        Outbound outbound = new Outbound(handle, activation, prelude);
        try {
            ProtosForeignArgument index = outbound.export(supplied.get(0));
            ProtosForeignArgument value = outbound.export(supplied.get(1));
            ProtosForeignOperation.enter(
                    handle,
                    "writeElement",
                    prelude,
                    outbound.callbacks,
                    live -> {
                        handle.adapter().writeElement(live, handle.target(), index, value);
                        return null;
                    });
        } finally {
            outbound.expire();
        }
        return supplied.get(1);
    }

    private static Object admit(
            ProtosForeignHandle handle, ProtosForeignProviderSession live, Object foreign)
            throws Exception {
        return ProtosForeignValueAdmission.admit(handle.session(), handle.adapter(), live, foreign);
    }

    /**
     * Outbound arguments of one operation. The D189 callback scope exists only once a Closure
     * argument needs it; an operation without one pays nothing. A failure to export any argument
     * is the ordinary pre-entry failure and expires callbacks already prepared.
     */
    private static final class Outbound {
        private final ProtosForeignHandle handle;
        private final ProtosActivation activation;
        private final ProtosPrelude prelude;
        ProtosForeignCallbackScope callbacks;

        Outbound(ProtosForeignHandle handle, ProtosActivation activation, ProtosPrelude prelude) {
            this.handle = handle;
            this.activation = activation;
            this.prelude = prelude;
        }

        ProtosForeignArgument export(Object value) {
            ProtosForeignArgument argument;
            if (value instanceof ProtosClosureValue closure) {
                if (callbacks == null) {
                    callbacks = new ProtosForeignCallbackScope(handle, activation);
                }
                argument =
                        ProtosForeignValueAdmission.exportCallbackOrNull(
                                handle, callbacks.prepare(closure));
            } else {
                argument = ProtosForeignValueAdmission.exportOrNull(handle, value);
            }
            if (argument == null) {
                throw ProtosForeignOperation.ordinaryError(prelude);
            }
            return argument;
        }

        void expire() {
            if (callbacks != null) {
                callbacks.expire();
            }
        }
    }

    private static ProtosForeignHandle requireHandle(ProtosActivation activation) {
        ProtosForeignHandle handle = ProtosForeignHandle.of(activation.receiver());
        if (handle == null) {
            throw ProtosForeignOperation.ordinaryError(prelude(activation));
        }
        return handle;
    }

    private static ProtosPrelude prelude(ProtosActivation activation) {
        return activation.prelude()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "foreign projection requires an owning Core prelude"));
    }
}

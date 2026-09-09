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

import com.guillermomolina.protos.runtime.*;
import com.oracle.truffle.api.source.Source;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * Bootstrap-local, test-neutral exact-source execution facility for bundled tooling.
 *
 * <p>The facility is installed only when a host explicitly grants it to an initial tool module
 * context. It is not a Core/prelude binding, intrinsic, service locator or test framework.
 *
 * <p>The default callable local slot {@code execution} accepts exactly one Protos String
 * containing an already-selected source unit. A host may also install the same mechanism under a
 * different bootstrap-local slot with an already-selected Prelude/module-resolution environment.
 * Every call runs that source through the general TOOL002-B/C mechanisms in a fresh Process with
 * empty arguments/environment, no Filesystem authority and private UTF-8 streams. It returns a
 * caller-local frozen observation object with slots {@code state}, {@code value}, {@code error},
 * {@code stdout}, and {@code stderr}.
 *
 * <p>Completed values or errors that cannot cross the strict detached-observation boundary signal
 * {@code NonTransferableValue} in the calling tool Process instead of leaking child execution
 * state or authority.
 */
public final class ProtosExactExecutionFacility {
    public static final String BOOTSTRAP_SLOT = "execution";
    public static final String INSPECTION_BOOTSTRAP_SLOT = "executionInspect";

    private ProtosExactExecutionFacility() {}

    public static void install(ProtosActivation activation) {
        install(activation, null);
    }

    public static void install(
            ProtosActivation activation,
            ProtosPolyglotRuntimeHost runtimeHost) {
        Objects.requireNonNull(activation, "activation");
        ProtosPrelude executionPrelude =
                activation
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "exact execution facility requires Core prelude"));
        install(activation, BOOTSTRAP_SLOT, executionPrelude, runtimeHost);
    }

    /**
     * Installs one named exact-source execution facility using an already-selected execution
     * Prelude/module-resolution environment.
     *
     * <p>The host chooses both the bootstrap-local slot and the Prelude before installation. The
     * facility performs no module discovery or policy selection of its own.
     */
    public static void install(
            ProtosActivation activation,
            String slotName,
            ProtosPrelude executionPrelude) {
        install(activation, slotName, executionPrelude, null);
    }

    public static void install(
            ProtosActivation activation,
            String slotName,
            ProtosPrelude executionPrelude,
            ProtosPolyglotRuntimeHost runtimeHost) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(slotName, "slotName");
        Objects.requireNonNull(executionPrelude, "executionPrelude");
        if (slotName.isEmpty()) {
            throw new IllegalArgumentException(
                    "exact execution bootstrap slot name must not be empty");
        }
        if (activation.context().hasLocalSlot(slotName)) {
            throw new IllegalStateException(
                    "exact execution bootstrap slot already exists: " + slotName);
        }

        activation
                .context()
                .createLocalSlot(
                        slotName,
                        exactExecutionBootstrapClosure(
                                (callActivation, arguments) ->
                                        execute(
                                                callActivation,
                                                arguments,
                                                executionPrelude,
                                                runtimeHost)));
    }


    /**
     * Shared native-closure construction site for the exact-execution bootstrap family.
     *
     * <p>This remains inside the already-audited non-Core exact-execution boundary. Callers in
     * this package may supply behavior, but they do not create an additional native provider.
     */
    static ProtosClosureValue exactExecutionBootstrapClosure(
            ProtosNativeClosureBody body) {
        return ProtosClosureValue.nativeClosure(
                Objects.requireNonNull(body, "body"));
    }


    /**
     * Installs the test-neutral live-result inspection facility under
     * {@link #INSPECTION_BOOTSTRAP_SLOT} using the caller's already-selected Prelude.
     */
    public static void installInspection(ProtosActivation activation) {
        installInspection(activation, null);
    }

    public static void installInspection(
            ProtosActivation activation,
            ProtosPolyglotRuntimeHost runtimeHost) {
        Objects.requireNonNull(activation, "activation");
        ProtosPrelude executionPrelude =
                activation
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "exact inspection facility requires Core prelude"));
        installInspection(
                activation,
                INSPECTION_BOOTSTRAP_SLOT,
                executionPrelude,
                runtimeHost);
    }

    /**
     * Installs one named live-result inspection facility using an already-selected
     * Prelude/module-resolution environment.
     *
     * <p>The callable accepts exactly two Protos Strings: one exact source unit and one exact
     * inspector source unit. The inspector source must evaluate to a Closure. The source result
     * remains live inside its fresh Process and is supplied directly to that Closure only after
     * cooperative source work reaches idle with no live tasks. Only the inspector's terminal
     * observation crosses the detached boundary.
     */
    public static void installInspection(
            ProtosActivation activation,
            String slotName,
            ProtosPrelude executionPrelude) {
        installInspection(activation, slotName, executionPrelude, null);
    }

    public static void installInspection(
            ProtosActivation activation,
            String slotName,
            ProtosPrelude executionPrelude,
            ProtosPolyglotRuntimeHost runtimeHost) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(slotName, "slotName");
        Objects.requireNonNull(executionPrelude, "executionPrelude");
        if (slotName.isEmpty()) {
            throw new IllegalArgumentException(
                    "exact inspection bootstrap slot name must not be empty");
        }
        if (activation.context().hasLocalSlot(slotName)) {
            throw new IllegalStateException(
                    "exact inspection bootstrap slot already exists: " + slotName);
        }

        activation
                .context()
                .createLocalSlot(
                        slotName,
                        ProtosClosureValue.nativeClosure(
                                (callActivation, arguments) ->
                                        inspect(
                                                callActivation,
                                                arguments,
                                                executionPrelude,
                                                runtimeHost)));
    }

    private static Object execute(
            ProtosActivation caller,
            List<?> arguments,
            ProtosPrelude executionPrelude,
            ProtosPolyglotRuntimeHost runtimeHost) {
        if (arguments.size() != 1
                || !(arguments.get(0) instanceof ProtosStringValue source)) {
            throw ordinaryError(caller);
        }

        ProtosPrelude callerPrelude =
                caller
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "exact execution observation requires caller Core prelude"));
        ProtosCapturedProcessExecution.Request request =
                executionRequest(source, executionPrelude);
        ProtosCapturedProcessExecution.Result result =
                runtimeHost == null
                        ? ProtosCapturedProcessExecution.execute(request)
                        : ProtosCapturedProcessExecution.execute(request, runtimeHost);

        return observation(result, caller, callerPrelude, executionPrelude);
    }


    private static Object inspect(
            ProtosActivation caller,
            List<?> arguments,
            ProtosPrelude executionPrelude,
            ProtosPolyglotRuntimeHost runtimeHost) {
        if (arguments.size() != 2
                || !(arguments.get(0) instanceof ProtosStringValue source)
                || !(arguments.get(1) instanceof ProtosStringValue inspector)) {
            throw ordinaryError(caller);
        }

        ProtosPrelude callerPrelude =
                caller
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "exact inspection observation requires caller Core prelude"));
        InspectionInvocation invocation =
                inspectionInvocation(
                        source,
                        inspector,
                        executionPrelude);
        ProtosCapturedProcessExecution.Result result =
                runtimeHost == null
                        ? ProtosCapturedProcessExecution.executeThenInspect(
                                invocation.request(), invocation.inspector())
                        : ProtosCapturedProcessExecution.executeThenInspect(
                                invocation.request(), invocation.inspector(), runtimeHost);

        return observation(result, caller, callerPrelude, executionPrelude);
    }


    record InspectionInvocation(
            ProtosCapturedProcessExecution.Request request,
            Source inspector) {}

    static InspectionInvocation inspectionInvocation(
            ProtosStringValue source,
            ProtosStringValue inspector,
            ProtosPrelude executionPrelude) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(inspector, "inspector");
        Objects.requireNonNull(executionPrelude, "executionPrelude");

        ProtosEncodingValue utf8 = utf8(executionPrelude);
        ProtosCapturedProcessExecution.Request request =
                new ProtosCapturedProcessExecution.Request(
                        executionPrelude,
                        exactSource(source.value(), "<exact-inspection-source>"),
                        List.of(),
                        exactEnvironmentDomain(),
                        List.of(),
                        new byte[0],
                        utf8,
                        utf8,
                        utf8,
                        null);
        return new InspectionInvocation(
                request,
                exactSource(inspector.value(), "<exact-inspector>"));
    }

    static ProtosCapturedProcessExecution.Request executionRequest(
            ProtosStringValue source,
            ProtosPrelude executionPrelude) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(executionPrelude, "executionPrelude");
        ProtosEncodingValue utf8 = utf8(executionPrelude);
        return new ProtosCapturedProcessExecution.Request(
                executionPrelude,
                exactSource(source.value(), "<exact-execution>"),
                List.of(),
                exactEnvironmentDomain(),
                List.of(),
                new byte[0],
                utf8,
                utf8,
                utf8,
                null);
    }

    private static Source exactSource(String characters, String name) {
        return Source.newBuilder(ProtosLanguage.ID, characters, name)
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
    }

    static ProtosObjectValue observation(
            ProtosCapturedProcessExecution.Result result,
            ProtosActivation caller,
            ProtosPrelude prelude,
            ProtosPrelude executionPrelude) {
        ProtosObjectValue observation =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        String state =
                switch (result.outcome().state()) {
                    case COMPLETED -> "completed";
                    case FAILED -> "failed";
                    case CANCELLED -> "cancelled";
                };
        observation.createLocalSlot("state", new ProtosStringValue(state));

        switch (result.outcome().state()) {
            case COMPLETED -> {
                observation.createLocalSlot(
                        "value",
                        ProtosDetachedExecutionValue.snapshot(
                                result.outcome().value(),
                                executionPrelude,
                                caller));
                observation.createLocalSlot(
                        "error",
                        ProtosNullValue.INSTANCE);
            }
            case FAILED -> {
                observation.createLocalSlot(
                        "value",
                        ProtosNullValue.INSTANCE);
                observation.createLocalSlot(
                        "error",
                        ProtosDetachedExecutionValue.snapshot(
                                result.outcome().error(),
                                executionPrelude,
                                caller));
            }
            case CANCELLED -> {
                observation.createLocalSlot(
                        "value",
                        ProtosNullValue.INSTANCE);
                observation.createLocalSlot(
                        "error",
                        ProtosNullValue.INSTANCE);
            }
        }

        observation.createLocalSlot(
                "stdout",
                frozenBytes(prelude, result.stdout()));
        observation.createLocalSlot(
                "stderr",
                frozenBytes(prelude, result.stderr()));
        observation.freeze();
        return observation;
    }

    private static ProtosBytesValue frozenBytes(
            ProtosPrelude prelude,
            byte[] bytes) {
        ProtosBytesValue value =
                new ProtosBytesValue(prelude.bytesPrototypeForRuntime());
        for (byte octet : bytes) {
            value.indexedAdd(
                    new ProtosIntegerValue(
                            BigInteger.valueOf(octet & 0xff)));
        }
        value.freeze();
        return value;
    }

    private static ProtosEncodingValue utf8(ProtosPrelude prelude) {
        Object value =
                prelude
                        .encodingPrototype()
                        .readLocalSlot("UTF8")
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Core UTF8 Encoding is missing"));
        if (!(value instanceof ProtosEncodingValue encoding)) {
            throw new IllegalStateException(
                    "Core UTF8 binding has the wrong value family");
        }
        return encoding;
    }

    private static ProtosEnvironmentValue.NativeNameDomain exactEnvironmentDomain() {
        return new ProtosEnvironmentValue.NativeNameDomain() {
            @Override
            public boolean sameCapturedName(String left, String right) {
                return left.equals(right);
            }

            @Override
            public boolean isQueryRepresentable(String name) {
                return !name.contains("=") && name.indexOf('\0') < 0;
            }

            @Override
            public boolean matchesQuery(String captured, String query) {
                return captured.equals(query);
            }
        };
    }

    static ProtosSignalException ordinaryError(
            ProtosActivation activation) {
        return new ProtosSignalException(
                ProtosCoreErrors.newError(activation));
    }
}

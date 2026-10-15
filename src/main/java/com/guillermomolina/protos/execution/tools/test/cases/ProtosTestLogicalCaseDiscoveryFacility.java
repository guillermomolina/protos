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
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * TOOL009 authority-free discovery boundary for one suite-native source.
 *
 * <p>The source is evaluated as an imported module with no Process, default Filesystem,
 * CLI-print, or Case-execution bootstrap authority. Only the inert D153 discovery
 * projection is detached into the calling Test Tool execution.
 */
public final class ProtosTestLogicalCaseDiscoveryFacility {
    public static final String BOOTSTRAP_SLOT = "logicalCaseDiscovery";

    private ProtosTestLogicalCaseDiscoveryFacility() {}

    public static void install(
            ProtosActivation activation,
            Path core,
            ProtosModuleResolver fallbackResolver,
            List<ProtosTestToolFileSelectionFacility.CorpusSourceRoot>
                    sourceRoots) {
        install(
                activation,
                core,
                fallbackResolver,
                sourceRoots,
                System.err);
    }

    /**
     * Production installation. BUG021: every discovery failure is reported on {@code diagnostics}
     * with the failing source and its cause before it fails the calling Test Tool execution.
     */
    public static void install(
            ProtosActivation activation,
            Path core,
            ProtosModuleResolver fallbackResolver,
            List<ProtosTestToolFileSelectionFacility.CorpusSourceRoot>
                    sourceRoots,
            PrintStream diagnostics) {
        install(
                activation,
                BOOTSTRAP_SLOT,
                core,
                fallbackResolver,
                sourceRoots,
                diagnostics);
    }

    static void install(
            ProtosActivation activation,
            String slotName,
            Path core,
            ProtosModuleResolver fallbackResolver,
            List<ProtosTestToolFileSelectionFacility.CorpusSourceRoot>
                    sourceRoots,
            PrintStream diagnostics) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(slotName, "slotName");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(fallbackResolver, "fallbackResolver");
        List<ProtosTestToolFileSelectionFacility.CorpusSourceRoot>
                immutableSourceRoots =
                        List.copyOf(
                                Objects.requireNonNull(
                                        sourceRoots,
                                        "sourceRoots"));

        if (slotName.isEmpty()) {
            throw new IllegalArgumentException(
                    "logical Case discovery bootstrap slot name must not be empty");
        }

        if (activation.context().hasLocalSlot(slotName)) {
            throw new IllegalStateException(
                    "logical Case discovery bootstrap slot already exists: "
                            + slotName);
        }

        activation.context()
                .createLocalSlot(
                        slotName,
                        ProtosExactExecutionFacility.exactExecutionBootstrapClosure(
                                (caller, arguments) ->
                                        execute(
                                                caller,
                                                arguments,
                                                core,
                                                fallbackResolver,
                                                immutableSourceRoots,
                                                diagnostics)));
    }

    private static Object execute(
            ProtosActivation caller,
            List<?> arguments,
            Path core,
            ProtosModuleResolver fallbackResolver,
            List<ProtosTestToolFileSelectionFacility.CorpusSourceRoot>
                    sourceRoots,
            PrintStream diagnostics) {
        if (arguments.size() != 2
                || !(arguments.get(0)
                        instanceof ProtosArrayValue sourceAssociation)
                || !(arguments.get(1)
                        instanceof ProtosStringValue source)) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        List<Object> association =
                sourceAssociation.indexedSnapshot();

        if ((association.size() != 2 && association.size() != 3)
                || !(association.get(0)
                        instanceof ProtosStringValue corpusId)
                || !(association.get(1)
                        instanceof ProtosStringValue sourcePath)
                || corpusId.value().isEmpty()
                || sourcePath.value().isEmpty()) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        // TOOL009 Package Non-TOML Publication 2: a third association element is
        // the D133 project-tree CaseAuthority descriptor. Discovery remains
        // authority-free and does not interpret it; it only preserves it
        // opaquely into the discovered CasePlan's own sourceAssociation so the
        // later execution boundary can decode and provision it.
        ProtosArrayValue projectTreeDescriptor = null;

        if (association.size() == 3) {
            if (!(association.get(2) instanceof ProtosArrayValue descriptor)) {
                throw ProtosExactExecutionFacility.ordinaryError(caller);
            }

            projectTreeDescriptor = descriptor;
        }

        /*
         * BUG021: a host exception or Error (for example a Native Image reflection or resource
         * failure) must not escape this boundary. Escaping would leave the calling Test Tool root
         * task non-terminal, so Process termination would wait for it indefinitely. Every
         * discovery failure is reported with its source and fails the caller as an ordinary Error.
         */
        try {
            return discover(
                    caller,
                    corpusId,
                    sourcePath,
                    source,
                    projectTreeDescriptor,
                    core,
                    fallbackResolver,
                    sourceRoots);
        } catch (ProtosTaskCancellationException cancelled) {
            throw cancelled;
        } catch (ProtosSignalException signalled) {
            reportDiscoveryFailure(
                    diagnostics,
                    corpusId,
                    sourcePath,
                    "Error signalled while discovering Test declarations");
            throw signalled;
        } catch (RuntimeException | Error hostFailure) {
            String message = hostFailure.getMessage();
            reportDiscoveryFailure(
                    diagnostics,
                    corpusId,
                    sourcePath,
                    hostFailure.getClass().getName()
                            + (message == null ? "" : ": " + message));
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }
    }

    private static void reportDiscoveryFailure(
            PrintStream diagnostics,
            ProtosStringValue corpusId,
            ProtosStringValue sourcePath,
            String cause) {
        diagnostics.println(
                "Test tool discovery error: "
                        + corpusId.value()
                        + ":"
                        + sourcePath.value()
                        + ": "
                        + cause);
        diagnostics.flush();
    }

    private static Object discover(
            ProtosActivation caller,
            ProtosStringValue corpusId,
            ProtosStringValue sourcePath,
            ProtosStringValue source,
            ProtosArrayValue projectTreeDescriptor,
            Path core,
            ProtosModuleResolver fallbackResolver,
            List<ProtosTestToolFileSelectionFacility.CorpusSourceRoot>
                    sourceRoots) {
        final Path physicalPath;
        try {
            physicalPath =
                    ProtosTestToolFileSelectionFacility
                            .resolveAuthorizedSource(
                                    sourceRoots,
                                    corpusId.value(),
                                    sourcePath.value());
        } catch (IOException failure) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        try (ProtosDirectFileModuleResolver resolver =
                new ProtosDirectFileModuleResolver(
                        physicalPath,
                        source.value(),
                        fallbackResolver)) {
            ProtosPrelude discoveryPrelude =
                    new ProtosCoreBootstrap().bootstrap(
                            core,
                            resolver);

            ProtosActivation declarationActivation =
                    discoveryPrelude.newModuleActivation();

            ProtosExecutionOutcome declaration =
                    ProtosCanonicalInitialModuleExecution.execute(
                            discoveryPrelude,
                            resolver,
                            resolver.entryModule(),
                            declarationActivation);

            requireDeclarationCompleted(
                    declaration,
                    discoveryPrelude,
                    caller);

            ProtosObjectValue module =
                    declarationActivation.actorModuleState()
                            .lookup(resolver.entryModule())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "discovered suite module is not cached"))
                            .instance();

            ArrayList<Object> discoverySourceAssociation = new ArrayList<>();
            discoverySourceAssociation.add(corpusId.copyForRuntime());
            discoverySourceAssociation.add(sourcePath.copyForRuntime());

            if (projectTreeDescriptor != null) {
                discoverySourceAssociation.add(
                        rematerializeDescriptor(
                                projectTreeDescriptor,
                                discoveryPrelude,
                                caller));
            }

            ProtosObjectValue context =
                    discoveryPrelude.newExecutionContext();
            context.createLocalSlot(
                    "sourceAssociation",
                    discoveryPrelude.newFrozenArray(
                            discoverySourceAssociation));
            context.createLocalSlot(
                    "discoverySubject",
                    module);

            ProtosActivation projectionActivation =
                    discoveryPrelude.newModuleActivation(
                            declarationActivation.actorModuleState(),
                            null,
                            context,
                            declarationActivation.executionDomain());

            ProtosExecutionOutcome projection;

            try (ProtosPolyglotExecutionContext executionContext =
                    ProtosPolyglotExecutionContext.open(
                            InputStream.nullInputStream(),
                            OutputStream.nullOutputStream(),
                            OutputStream.nullOutputStream())) {
                projection =
                        executionContext.execute(
                                projectionSource(),
                                projectionActivation);
            }

            return requireCompleted(
                    projection,
                    discoveryPrelude,
                    caller);
        } catch (IOException failure) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }
    }

    /**
     * TOOL009 Package Non-TOML Publication 2: rematerializes the D133 project-tree
     * CaseAuthority descriptor into the fresh discovery Prelude. Arrays are prototype-bound to
     * the Prelude that created them, so the caller's descriptor cannot be reused directly inside
     * the discovery Prelude's own frozen Array; only its four inert String elements cross the
     * boundary.
     */
    private static ProtosArrayValue rematerializeDescriptor(
            ProtosArrayValue descriptor,
            ProtosPrelude discoveryPrelude,
            ProtosActivation caller) {
        List<Object> elements = descriptor.indexedSnapshot();

        if (elements.size() != 4) {
            throw ProtosExactExecutionFacility.ordinaryError(caller);
        }

        ArrayList<Object> copy = new ArrayList<>(elements.size());

        for (Object element : elements) {
            if (!(element instanceof ProtosStringValue value)) {
                throw ProtosExactExecutionFacility.ordinaryError(caller);
            }

            copy.add(value.copyForRuntime());
        }

        ProtosArrayValue rematerialized = discoveryPrelude.newFrozenArray(copy);
        return rematerialized;
    }

    /**
     * Validates declaration execution without transferring its returned value.
     *
     * <p>The imported suite module may complete with a value graph containing
     * Test descriptors and body Closures. That live declaration result belongs
     * exclusively to the discovery execution and must never cross into the
     * calling Test Tool process.
     */
    private static void requireDeclarationCompleted(
            ProtosExecutionOutcome outcome,
            ProtosPrelude sourcePrelude,
            ProtosActivation caller) {
        switch (outcome.state()) {
            case COMPLETED -> {
                return;
            }

            case FAILED -> {
                Object detached =
                        ProtosDetachedExecutionValue.snapshot(
                                outcome.error(),
                                sourcePrelude,
                                caller);

                if (!(detached instanceof ProtosObjectValue error)) {
                    throw new IllegalStateException(
                            "discovery declaration Error detached to a non-object value");
                }

                throw new ProtosSignalException(error);
            }

            case CANCELLED ->
                    throw ProtosExactExecutionFacility.ordinaryError(
                            caller);
        }
    }

    private static Object requireCompleted(
            ProtosExecutionOutcome outcome,
            ProtosPrelude sourcePrelude,
            ProtosActivation caller) {
        return switch (outcome.state()) {
            case COMPLETED ->
                    rematerializeInertProjection(
                            outcome.value(),
                            caller);

            case FAILED -> {
                Object detached =
                        ProtosDetachedExecutionValue.snapshot(
                                outcome.error(),
                                sourcePrelude,
                                caller);

                if (!(detached instanceof ProtosObjectValue error)) {
                    throw new IllegalStateException(
                            "discovery Error detached to a non-object value");
                }

                throw new ProtosSignalException(error);
            }

            case CANCELLED ->
                    throw ProtosExactExecutionFacility.ordinaryError(
                            caller);
        };
    }

    /**
     * Re-materializes only the inert D153 discovery carrier families.
     *
     * <p>This deliberately does not use the generic detached execution copier:
     * a discovery projection contains standard Arrays whose prototype belongs
     * to the discovery Prelude. Following that prototype graph would encounter
     * executable standard-library state even though the projection itself is
     * inert. The discovery ABI is narrower: frozen Arrays and scalar values.
     */
    private static Object rematerializeInertProjection(
            Object value,
            ProtosActivation caller) {
        ProtosPrelude callerPrelude =
                caller
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "logical Case discovery requires caller Core prelude"));

        return rematerializeInertProjection(
                value,
                callerPrelude,
                caller);
    }

    private static Object rematerializeInertProjection(
            Object value,
            ProtosPrelude callerPrelude,
            ProtosActivation caller) {
        if (value == ProtosNullValue.INSTANCE
                || value == ProtosBooleanValue.TRUE
                || value == ProtosBooleanValue.FALSE) {
            return value;
        }

        if (value instanceof ProtosStringValue string) {
            return new ProtosStringValue(
                    string.value());
        }

        if (ProtosNumericValueSupport.isCurrentInteger(value)) {
            return ProtosNumericValueSupport.copyCurrentNumberOrNull(value, callerPrelude);
        }

        if (value instanceof ProtosArrayValue array) {
            ArrayList<Object> elements =
                    new ArrayList<>(
                            array.indexedSnapshot().size());

            for (Object element :
                    array.indexedSnapshot()) {
                elements.add(
                        rematerializeInertProjection(
                                element,
                                callerPrelude,
                                caller));
            }

            return callerPrelude.newFrozenArray(
                    elements);
        }

        throw new ProtosSignalException(
                com.guillermomolina.protos.runtime.ProtosCoreErrors
                        .newOccurrence(
                                caller,
                                com.guillermomolina.protos.runtime.ProtosCoreErrors
                                        .StandardError.NON_TRANSFERABLE_VALUE));
    }

    private static Source projectionSource() {
        return Source.newBuilder(
                        ProtosLanguage.ID,
                        """
                        Discovery: import("self:Discovery")

                        Discovery.projectionFromModule(
                            sourceAssociation,
                            discoverySubject
                        )
                        """,
                        "<tool009-logical-case-discovery>")
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
    }
}

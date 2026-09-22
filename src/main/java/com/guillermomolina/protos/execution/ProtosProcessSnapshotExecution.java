/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE.
 */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosEnvironmentValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.oracle.truffle.api.source.Source;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * D135 deterministic guest-bootstrap execution for the Process-snapshot Test Tool lane.
 *
 * <p>Each invocation creates fresh Process, arguments and Environment snapshot state. This class
 * owns no TestPlan, CaseId, scheduling, resource, reporting or corpus-discovery policy.
 */
public final class ProtosProcessSnapshotExecution {
    private static final List<String> ARGUMENTS =
            List.of("alpha", "\u03b2", new String(Character.toChars(0x1f600)));

    private ProtosProcessSnapshotExecution() {}

    /** D178 phase distinguishing declaration/selection rejection from selected Test completion. */
    public enum CasePhase {
        REMATERIALIZATION_ERROR,
        CASE_EXECUTION
    }

    /** D178 outcome of one selected-Test-authority Logical Case attempt. */
    public record CaseResult(CasePhase phase, ProtosExecutionOutcome outcome) {
        public CaseResult {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    public static ProtosExecutionOutcome execute(String source, ProtosPrelude prelude) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(prelude, "prelude");

        ProtosActivation activation = newSnapshotActivation(prelude);
        Source exactSource = exactSource(source, "<process-snapshot>");

        try (ProtosPolyglotExecutionContext executionContext = openExecutionContext()) {
            return executionContext.execute(exactSource, activation);
        }
    }

    /**
     * D178 selected {@code Test.call()} authority for one Process-snapshot Logical Case.
     *
     * <p>Reuses the exact Process-snapshot bootstrap above to materialize fresh Process/arguments/
     * Environment state, then executes {@code source} once to declare its top-level {@code tests}
     * (discovery-observational: constructing a named Test never invokes its body), resolves exactly
     * one selected Test through the same {@code Discovery.resolveSelectedTest} authority the
     * ordinary suite-native lane uses, and invokes only that selected Test's {@code call()} exactly
     * once. A declaration failure or a signature/selector mismatch is classified {@link
     * CasePhase#REMATERIALIZATION_ERROR} and never reaches the selected Test body.
     */
    public static CaseResult executeCase(
            String source,
            ProtosPrelude prelude,
            List<String> expectedSignature,
            String selector) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(prelude, "prelude");
        Objects.requireNonNull(expectedSignature, "expectedSignature");
        Objects.requireNonNull(selector, "selector");

        ProtosActivation activation = newSnapshotActivation(prelude);
        Source exactSource = exactSource(source, "<process-snapshot>");

        try (ProtosPolyglotExecutionContext executionContext = openExecutionContext()) {
            ProtosExecutionOutcome declaration = executionContext.execute(exactSource, activation);
            if (declaration.state() != ProtosExecutionOutcome.State.COMPLETED) {
                return new CaseResult(CasePhase.REMATERIALIZATION_ERROR, declaration);
            }

            ProtosActivation selectionActivation =
                    selectionActivation(prelude, activation, expectedSignature, selector);

            try {
                executionContext.evaluatePersistent(selectionSource(), selectionActivation);
            } catch (ProtosSignalException mismatch) {
                return new CaseResult(
                        CasePhase.REMATERIALIZATION_ERROR,
                        ProtosExecutionOutcome.failed(mismatch.error()));
            }

            ProtosExecutionOutcome body =
                    executionContext.execute(invocationSource(), selectionActivation);
            return new CaseResult(CasePhase.CASE_EXECUTION, body);
        }
    }

    private static ProtosActivation newSnapshotActivation(ProtosPrelude prelude) {
        ProtosObjectValue argumentsPrototype =
                ProtosStandardProcessArgumentsProtocol.createPrototype();
        ProtosObjectValue environmentPrototype =
                ProtosStandardEnvironmentProtocol.createPrototype();

        List<ProtosEnvironmentValue.NativeEntry> environment = environmentEntries();

        ProtosProcessRuntime primary =
                process(
                        prelude,
                        argumentsPrototype,
                        environmentPrototype,
                        ARGUMENTS,
                        environment);
        ProtosProcessRuntime other =
                process(
                        prelude,
                        argumentsPrototype,
                        environmentPrototype,
                        ARGUMENTS,
                        environment);
        ProtosProcessRuntime empty =
                process(
                        prelude,
                        argumentsPrototype,
                        environmentPrototype,
                        List.of(),
                        List.of());

        ProtosActivation activation = prelude.newModuleActivation();
        activation
                .context()
                .createLocalSlot(
                        "process",
                        primary.provisionCapabilityForRuntime(
                                prelude.processPrototype()));
        activation
                .context()
                .createLocalSlot(
                        "otherProcess",
                        other.provisionCapabilityForRuntime(
                                prelude.processPrototype()));
        activation
                .context()
                .createLocalSlot(
                        "emptyProcess",
                        empty.provisionCapabilityForRuntime(
                                prelude.processPrototype()));
        return activation;
    }

    private static ProtosActivation selectionActivation(
            ProtosPrelude prelude,
            ProtosActivation moduleActivation,
            List<String> expectedSignature,
            String selector) {
        ProtosObjectValue selectionContext = prelude.newExecutionContext();

        ArrayList<Object> selectors = new ArrayList<>(expectedSignature.size());
        for (String name : expectedSignature) {
            selectors.add(new ProtosStringValue(name));
        }

        selectionContext.createLocalSlot("discoverySubject", moduleActivation.context());
        selectionContext.createLocalSlot("expectedSignature", prelude.newFrozenArray(selectors));
        selectionContext.createLocalSlot("selectedSelector", new ProtosStringValue(selector));

        return prelude.newModuleActivation(
                moduleActivation.actorModuleState(),
                null,
                selectionContext,
                moduleActivation.executionDomain());
    }

    private static Source selectionSource() {
        return exactSource(
                """
                Discovery: import("self:Discovery")

                selected:
                    Discovery.resolveSelectedTest(
                        discoverySubject,
                        expectedSignature,
                        selectedSelector
                    )

                selected
                """,
                "<process-snapshot-logical-case-selection>");
    }

    private static Source invocationSource() {
        return exactSource("selected()", "<process-snapshot-logical-case-body>");
    }

    private static ProtosPolyglotExecutionContext openExecutionContext() {
        return ProtosPolyglotExecutionContext.open(
                InputStream.nullInputStream(),
                OutputStream.nullOutputStream(),
                OutputStream.nullOutputStream());
    }

    private static Source exactSource(String characters, String name) {
        return Source.newBuilder(ProtosLanguage.ID, characters, name)
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
    }

    private static ProtosProcessRuntime process(
            ProtosPrelude prelude,
            ProtosObjectValue argumentsPrototype,
            ProtosObjectValue environmentPrototype,
            List<String> arguments,
            List<ProtosEnvironmentValue.NativeEntry> environment) {
        ProtosProcessRuntime process =
                new ProtosProcessRuntime(prelude.actorRefPrototypeForRuntime());

        if (process.establishArgumentsForRuntime(argumentsPrototype, arguments)
                != ProtosProcessRuntime.ArgumentsSnapshotState.AVAILABLE) {
            throw new IllegalStateException(
                    "Process snapshot fixture arguments were not representable");
        }
        if (process.establishEnvironmentForRuntime(
                        environmentPrototype,
                        exactEnvironmentDomain(),
                        environment)
                != ProtosProcessRuntime.EnvironmentSnapshotState.AVAILABLE) {
            throw new IllegalStateException(
                    "Process snapshot fixture environment was not representable");
        }
        return process;
    }

    private static List<ProtosEnvironmentValue.NativeEntry> environmentEntries() {
        String bmp = "\ue000";
        String supplementary = new String(Character.toChars(0x10000));
        return List.of(
                new ProtosEnvironmentValue.NativeEntry(
                        supplementary,
                        "supplementary"),
                new ProtosEnvironmentValue.NativeEntry(
                        "A",
                        "first"),
                new ProtosEnvironmentValue.NativeEntry(
                        bmp,
                        "middle"));
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
}

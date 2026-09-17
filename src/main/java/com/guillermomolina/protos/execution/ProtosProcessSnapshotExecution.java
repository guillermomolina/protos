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
import com.oracle.truffle.api.source.Source;
import java.io.InputStream;
import java.io.OutputStream;
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

    public static ProtosExecutionOutcome execute(String source, ProtosPrelude prelude) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(prelude, "prelude");

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

        Source exactSource =
                Source.newBuilder(
                                ProtosLanguage.ID,
                                source,
                                "<process-snapshot>")
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        try (ProtosPolyglotExecutionContext executionContext =
                ProtosPolyglotExecutionContext.open(
                        InputStream.nullInputStream(),
                        OutputStream.nullOutputStream(),
                        OutputStream.nullOutputStream())) {
            return executionContext.execute(exactSource, activation);
        }
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

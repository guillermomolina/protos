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
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.util.Objects;

/**
 * D135 asynchronous execution adapter for the Process-snapshot Test Tool lane.
 *
 * <p>The underlying bootstrap is fresh for every accepted execution. Async custody,
 * cancellation and caller-domain completion reuse the ordinary exact-execution mechanism.
 */
public final class ProtosAsyncProcessSnapshotExecutionFacility {
    public static final String BOOTSTRAP_SLOT = "processSnapshotExecutionAsync";

    private ProtosAsyncProcessSnapshotExecutionFacility() {}

    public static ProtosAsyncExactExecutionFacility install(
            ProtosActivation activation,
            ProtosPrelude executionPrelude,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(executionPrelude, "executionPrelude");
        Objects.requireNonNull(runtimeHost, "runtimeHost");
        Objects.requireNonNull(submission, "submission");

        return ProtosAsyncExactExecutionFacility.installWithSourceExecution(
                activation,
                BOOTSTRAP_SLOT,
                executionPrelude,
                runtimeHost,
                submission,
                source ->
                        new ProtosCapturedProcessExecution.Result(
                                ProtosProcessSnapshotExecution.execute(
                                        source.value(),
                                        executionPrelude),
                                new byte[0],
                                new byte[0]));
    }
}

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
 * the specific language governing rights and limitations under the LICENSE.
 */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Production host-bootstrap ownership scope for D113 Test Tool resource execution.
 *
 * <p>One invocation observes one immutable environment registry. Each owned Test Tool plan receives
 * C1/C2 bootstrap routes bound to that plan's exact execution Prelude, so future resourceful
 * actor/group/package cases cannot accidentally execute in the primary Test Tool Prelude.
 */
public final class ProtosTestResourceExecutionScope implements AutoCloseable {

    private final List<ProtosTestResourcefulExecutionFacility> facilities;
    private boolean closed;

    private ProtosTestResourceExecutionScope(
            List<ProtosTestResourcefulExecutionFacility> facilities) {
        this.facilities = List.copyOf(facilities);
    }

    /** Installs only the historical primary C1/C2 pair; retained for bounded focal consumers. */
    public static ProtosTestResourceExecutionScope installEmpty(
            ProtosActivation activation,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        Objects.requireNonNull(activation, "activation");
        ProtosPrelude primaryPrelude =
                activation
                        .prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "resourceful execution scope requires Core prelude"));
        return installEmpty(
                activation,
                runtimeHost,
                submission,
                primaryPrelude,
                null,
                null,
                null);
    }

    /** Installs the D113 empty-registry environment for every public Test Tool plan. */
    public static ProtosTestResourceExecutionScope installEmpty(
            ProtosActivation activation,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission,
            ProtosPrelude primaryPrelude,
            ProtosPrelude actorPrelude,
            ProtosPrelude groupPrelude,
            ProtosPrelude packagePrelude) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(runtimeHost, "runtimeHost");
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(primaryPrelude, "primaryPrelude");

        ProtosTestResourceProviderRegistry registry =
                ProtosTestResourceProviderRegistry.empty();
        ArrayList<ProtosTestResourcefulExecutionFacility> facilities =
                new ArrayList<>();

        try {
            installPair(
                    facilities,
                    activation,
                    "resourceExecutionAsync",
                    "resourceExecutionInspectAsync",
                    primaryPrelude,
                    registry,
                    runtimeHost,
                    submission);

            if (actorPrelude != null) {
                installPair(
                        facilities,
                        activation,
                        "actorResourceExecutionAsync",
                        "actorResourceExecutionInspectAsync",
                        actorPrelude,
                        registry,
                        runtimeHost,
                        submission);
            }
            if (groupPrelude != null) {
                installPair(
                        facilities,
                        activation,
                        "groupResourceExecutionAsync",
                        "groupResourceExecutionInspectAsync",
                        groupPrelude,
                        registry,
                        runtimeHost,
                        submission);
            }
            if (packagePrelude != null) {
                installPair(
                        facilities,
                        activation,
                        "packageResourceExecutionAsync",
                        "packageResourceExecutionInspectAsync",
                        packagePrelude,
                        registry,
                        runtimeHost,
                        submission);
            }

            return new ProtosTestResourceExecutionScope(facilities);
        } catch (RuntimeException | Error failure) {
            closeFacilities(facilities);
            throw failure;
        }
    }

    private static void installPair(
            List<ProtosTestResourcefulExecutionFacility> facilities,
            ProtosActivation activation,
            String executionSlot,
            String inspectionSlot,
            ProtosPrelude executionPrelude,
            ProtosTestResourceProviderRegistry registry,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission) {
        facilities.add(
                ProtosTestResourcefulExecutionFacility.installNamed(
                        activation,
                        executionSlot,
                        executionPrelude,
                        registry,
                        runtimeHost,
                        submission));
        facilities.add(
                ProtosTestResourcefulExecutionFacility.installInspectionNamed(
                        activation,
                        inspectionSlot,
                        executionPrelude,
                        registry,
                        runtimeHost,
                        submission));
    }

    private static void closeFacilities(
            List<ProtosTestResourcefulExecutionFacility> facilities) {
        for (int index = facilities.size() - 1; index >= 0; index--) {
            facilities.get(index).close();
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeFacilities(facilities);
    }
}

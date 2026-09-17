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
package com.guillermomolina.protos.cli;

import com.guillermomolina.protos.execution.ProtosAsyncExactExecutionFacility;
import com.guillermomolina.protos.execution.ProtosPolyglotRuntimeHost;
import com.guillermomolina.protos.execution.ProtosTestCaseAuthorityExecutionFacility;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * D133/D134 invocation-owned physical CaseAuthority execution scope for TOOL005-B3B.
 *
 * <p>Each corpus owns an independent exact host binding closed over its trusted physical
 * {@code cases/} root. Corpus identity remains separate from ExecutionRequirementId and from
 * D077/D098 resource accounting.
 */
final class ProtosTestCaseAuthorityExecutionScope implements AutoCloseable {
    static final String CONTENT_IDENTITY_SLOT =
            "packageToolContentIdentityCaseAuthorityExecutionAsync";

    static final String RESOLUTION_INPUT_LOCK_SLOT =
            "packageToolResolutionInputLockCaseAuthorityExecutionAsync";

    static final String RESOLUTION_ROOT_SLOT =
            "packageToolResolutionRootCaseAuthorityExecutionAsync";

    static final String EXECUTION_PLAN_SLOT =
            "packageToolExecutionPlanCaseAuthorityExecutionAsync";

    static final String PROJECT_PROJECTION_SLOT =
            "packageToolProjectProjectionCaseAuthorityExecutionAsync";

    private final List<ProtosTestCaseAuthorityExecutionFacility> facilities;
    private boolean closed;

    private ProtosTestCaseAuthorityExecutionScope(
            List<ProtosTestCaseAuthorityExecutionFacility> facilities) {
        this.facilities = List.copyOf(facilities);
    }

    static ProtosTestCaseAuthorityExecutionScope install(
            ProtosActivation activation,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosAsyncExactExecutionFacility.Submission submission,
            ProtosPrelude packagePrelude,
            Path contentIdentityCases,
            Path resolutionInputLockCases,
            Path resolutionRootCases,
            Path executionPlanCases,
            Path projectProjectionCases) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(runtimeHost, "runtimeHost");
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(packagePrelude, "packagePrelude");
        Objects.requireNonNull(contentIdentityCases, "contentIdentityCases");
        Objects.requireNonNull(resolutionInputLockCases, "resolutionInputLockCases");
        Objects.requireNonNull(resolutionRootCases, "resolutionRootCases");
        Objects.requireNonNull(executionPlanCases, "executionPlanCases");
        Objects.requireNonNull(projectProjectionCases, "projectProjectionCases");

        ArrayList<ProtosTestCaseAuthorityExecutionFacility> facilities =
                new ArrayList<>();

        try {
            facilities.add(
                    ProtosTestCaseAuthorityExecutionFacility.install(
                            activation,
                            CONTENT_IDENTITY_SLOT,
                            packagePrelude,
                            contentIdentityCases,
                            runtimeHost,
                            submission));

            facilities.add(
                    ProtosTestCaseAuthorityExecutionFacility.install(
                            activation,
                            RESOLUTION_INPUT_LOCK_SLOT,
                            packagePrelude,
                            resolutionInputLockCases,
                            runtimeHost,
                            submission));

            facilities.add(
                    ProtosTestCaseAuthorityExecutionFacility.install(
                            activation,
                            RESOLUTION_ROOT_SLOT,
                            packagePrelude,
                            resolutionRootCases,
                            runtimeHost,
                            submission));

            facilities.add(
                    ProtosTestCaseAuthorityExecutionFacility.install(
                            activation,
                            EXECUTION_PLAN_SLOT,
                            packagePrelude,
                            executionPlanCases,
                            runtimeHost,
                            submission));

            facilities.add(
                    ProtosTestCaseAuthorityExecutionFacility.install(
                            activation,
                            PROJECT_PROJECTION_SLOT,
                            packagePrelude,
                            projectProjectionCases,
                            runtimeHost,
                            submission));

            return new ProtosTestCaseAuthorityExecutionScope(
                    facilities);
        } catch (RuntimeException | Error failure) {
            closeFacilities(facilities);
            throw failure;
        }
    }

    private static void closeFacilities(
            List<ProtosTestCaseAuthorityExecutionFacility> facilities) {
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

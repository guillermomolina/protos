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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosPackageExecutionPlanAdapterTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "package");
    private static final Path PROJECT =
            Path.of(
                    "protos",
                    "tests",
                    "package-tool",
                    "execution-plan",
                    "cases",
                    "workspace");
    private static final String BUILD_PLAN =
            "Plan: import(\"self:ExecutionPlan\")\n"
                    + "Plan.build(projectTreeFilesystem)\n";

    @Test
    void detachesPublishedWorkspacePlanIntoImmutableHostData() throws Exception {
        try (Fixture fixture = fixture()) {
            Object raw = fixture.buildPlan();
            ProtosPackageExecutionPlan detached =
                    ProtosPackageExecutionPlanAdapter.detach(raw, PROJECT);

            assertEquals(1, detached.generation());
            assertEquals("root", detached.root().packageId());
            assertEquals(2, detached.packages().size());
            assertEquals(2, detached.dependencies().size());
            assertEquals("", detached.packages().get(0).location());
            assertEquals("libs/a", detached.packages().get(1).location());
            assertEquals("internal/Thing", detached.packages().get(1).exports().get("Public"));

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> detached.packages().add(detached.packages().get(0)));
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> detached.packages().get(1).exports().put("Other", "internal/Thing"));

            ProtosObjectValue rawPlan = (ProtosObjectValue) raw;
            ProtosArrayValue rawPackages =
                    (ProtosArrayValue) rawPlan.readLocalSlot("packages").orElseThrow();
            rawPackages.indexedPut(BigInteger.ZERO, rawPackages.indexedAt(BigInteger.ONE));

            assertEquals("root", detached.root().packageId());
            assertEquals("", detached.packages().get(0).location());
        }
    }

    @Test
    void rejectsMalformedGenerationShapeLocationAndRuntimeAlias() throws Exception {
        try (Fixture fixture = fixture()) {
            ProtosObjectValue plan = (ProtosObjectValue) fixture.buildPlan();

            Object generation = plan.readLocalSlot("generation").orElseThrow();
            plan.assignLocalSlot(
                    "generation", new ProtosIntegerValue(BigInteger.valueOf(2)));
            assertThrows(
                    Exception.class,
                    () -> ProtosPackageExecutionPlanAdapter.detach(plan, PROJECT));
            plan.assignLocalSlot("generation", generation);

            ProtosArrayValue packages =
                    (ProtosArrayValue) plan.readLocalSlot("packages").orElseThrow();
            ProtosObjectValue member =
                    (ProtosObjectValue) packages.indexedAt(BigInteger.ONE);
            Object location = member.readLocalSlot("location").orElseThrow();
            member.assignLocalSlot("location", new ProtosStringValue("../escape"));
            assertThrows(
                    Exception.class,
                    () -> ProtosPackageExecutionPlanAdapter.detach(plan, PROJECT));
            member.assignLocalSlot("location", location);

            ProtosArrayValue dependencies =
                    (ProtosArrayValue) plan.readLocalSlot("dependencies").orElseThrow();
            ProtosObjectValue edge =
                    (ProtosObjectValue) dependencies.indexedAt(BigInteger.ZERO);
            Object alias = edge.readLocalSlot("alias").orElseThrow();
            edge.assignLocalSlot("alias", new ProtosStringValue("bad-name"));
            assertThrows(
                    Exception.class,
                    () -> ProtosPackageExecutionPlanAdapter.detach(plan, PROJECT));
            edge.assignLocalSlot("alias", alias);

            plan.createLocalSlot("unexpected", new ProtosStringValue("x"));
            assertThrows(
                    Exception.class,
                    () -> ProtosPackageExecutionPlanAdapter.detach(plan, PROJECT));
        }
    }

    private static Fixture fixture() throws Exception {
        ProtosNioReadOnlyTreeFilesystemBackend backend =
                new ProtosNioReadOnlyTreeFilesystemBackend(PROJECT);
        assumeTrue(
                backend.secureConfinementAvailable(),
                "host provider has no SecureDirectoryStream");

        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "package",
                        TOOL_ROOT, (TOOL_ROOT).resolveSibling("shared"),
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosHostedExecutionTestFixture hosted =
                ProtosHostedExecutionTestFixture.open(prelude);
        try {
            hosted.installFilesystem("projectTreeFilesystem", backend);
            return new Fixture(backend, hosted);
        } catch (RuntimeException | Error failure) {
            hosted.close();
            backend.close();
            throw failure;
        }
    }

    private record Fixture(
            ProtosNioReadOnlyTreeFilesystemBackend backend,
            ProtosHostedExecutionTestFixture hosted)
            implements AutoCloseable {
        Object buildPlan() {
            ProtosExecutionOutcome outcome =
                    hosted.execute("<package-execution-plan>", BUILD_PLAN);
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state());
            return outcome.value();
        }

        @Override
        public void close() throws Exception {
            try {
                hosted.close();
            } finally {
                backend.close();
            }
        }
    }
}

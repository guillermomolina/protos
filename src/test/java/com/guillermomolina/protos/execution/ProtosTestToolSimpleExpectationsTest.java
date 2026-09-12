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
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosTestToolSimpleExpectationsTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path TOOL_ROOT = Path.of("protos", "tools", "test");
    private static final Path POSITIVE_FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-d3a2-simple-expectations.protos");
    private static final Path UNSUPPORTED_FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-d3a2-unsupported-expectation.protos");
    private static final Path MALFORMED_FIXTURE =
            Path.of(
                    "protos",
                    "tests",
                    "tooling",
                    "tool002-d3a2-malformed-expected.protos");

    @Test
    void simpleExpectationPolicyAndNormalMismatchesAreOwnedByProtos()
            throws Exception {
        Fixture fixture = fixture();

        ProtosExecutionOutcome outcome =
                executeFixture(POSITIVE_FIXTURE, fixture.activation());

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> "Simple expectations "
                        + guestErrorDiagnostic(outcome, fixture.prelude()));
        assertSame(ProtosBooleanValue.TRUE, outcome.value());
    }

    @Test
    void diagnosticManifestCaseSpecAndPlanBoundary() throws Exception {
        Fixture fixture = fixture();
        assertDiagnosticCompletedTrue(
                "Manifest.caseSpec/planCases",
                "Manifest: import(\"self:Manifest\")\n"
                        + "spec: Manifest.caseSpec(Array(\"diagnostic/one.protos\", \"integer\", \"1\"))\n"
                        + "plan: Manifest.frozenTuple(Manifest.frozenTuple(spec))\n"
                        + "cases: Manifest.planCases(plan)\n"
                        + "(cases.size() == 1) &&\n"
                        + "(Manifest.caseId(cases[0]) === \"diagnostic/one.protos\") &&\n"
                        + "(Manifest.caseExpectation(cases[0]) === \"integer\") &&\n"
                        + "(Manifest.caseExpected(cases[0]) === \"1\")\n",
                fixture);
    }

    @Test
    void diagnosticSimpleExpectationClassifierBoundary() throws Exception {
        Fixture fixture = fixture();
        assertDiagnosticCompletedTrue(
                "Runner.isSimpleExpectation",
                "Manifest: import(\"self:Manifest\")\n"
                        + "Runner: import(\"self:Runner\")\n"
                        + "spec: Manifest.caseSpec(Array(\"diagnostic/one.protos\", \"integer\", \"1\"))\n"
                        + "Runner.isSimpleExpectation(spec)\n",
                fixture);
    }

    @Test
    void diagnosticMapConstructionBoundary() throws Exception {
        Fixture fixture = fixture();
        assertDiagnosticCompletedTrue(
                "Map construction",
                "values: Map()\n"
                        + "(values.size() == 0) && (values.containsKey(\"missing\") === false)\n",
                fixture);
    }

    @Test
    void diagnosticExactExecutionBindingBoundary() throws Exception {
        Fixture fixture = fixture();
        assertDiagnosticCompletedTrue(
                "execution(\"true\")",
                "observation: execution(\"true\")\n"
                        + "(observation.state === \"completed\") &&\n"
                        + "(observation.error === null) &&\n"
                        + "(observation.value === true)\n",
                fixture);
    }

    @Test
    void diagnosticEvaluateSimpleBooleanBoundary() throws Exception {
        Fixture fixture = fixture();
        assertDiagnosticCompletedTrue(
                "Runner.evaluateSimple boolean",
                "Manifest: import(\"self:Manifest\")\n"
                        + "Runner: import(\"self:Runner\")\n"
                        + "spec: Manifest.caseSpec(Array(\"diagnostic/true.protos\", \"boolean\", \"true\"))\n"
                        + "result: Runner.evaluateSimple(spec, \"true\", execution)\n"
                        + "Runner.resultPassed(result)\n",
                fixture);
    }

    @Test
    void diagnosticEvaluateSimpleIntegerBoundary() throws Exception {
        Fixture fixture = fixture();
        assertDiagnosticCompletedTrue(
                "Runner.evaluateSimple integer",
                "Manifest: import(\"self:Manifest\")\n"
                        + "Runner: import(\"self:Runner\")\n"
                        + "spec: Manifest.caseSpec(Array(\"diagnostic/one.protos\", \"integer\", \"1\"))\n"
                        + "result: Runner.evaluateSimple(spec, \"1\", execution)\n"
                        + "Runner.resultPassed(result)\n",
                fixture);
    }

    @Test
    void unsupportedExpectationKindFailsClosedAsToolPolicyError()
            throws Exception {
        Fixture fixture = fixture();

        ProtosExecutionOutcome outcome =
                executeFixture(UNSUPPORTED_FIXTURE, fixture.activation());

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
    }

    @Test
    void malformedExpectedIntegerFailsClosedAsToolPolicyError()
            throws Exception {
        Fixture fixture = fixture();

        ProtosExecutionOutcome outcome =
                executeFixture(MALFORMED_FIXTURE, fixture.activation());

        assertEquals(ProtosExecutionOutcome.State.FAILED, outcome.state());
    }

    private static void assertDiagnosticCompletedTrue(
            String boundary,
            String source,
            Fixture fixture)
            throws Exception {
        ProtosExecutionOutcome outcome =
                ProtosRootTaskExecution.execute(
                        new ProtosSourceCompiler().compile(source),
                        fixture.activation());

        assertEquals(
                ProtosExecutionOutcome.State.COMPLETED,
                outcome.state(),
                () -> boundary + " " + guestErrorDiagnostic(outcome, fixture.prelude()));
        assertSame(
                ProtosBooleanValue.TRUE,
                outcome.value(),
                () -> boundary + " completed with unexpected value: " + outcome.value());
    }

    private static ProtosExecutionOutcome executeFixture(
            Path fixture,
            ProtosActivation activation)
            throws Exception {
        return ProtosRootTaskExecution.execute(
                new ProtosSourceCompiler()
                        .compile(
                                Files.readString(
                                        fixture,
                                        StandardCharsets.UTF_8)),
                activation);
    }

    private static Fixture fixture() throws Exception {
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        "test",
                        TOOL_ROOT,
                        new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosExactExecutionFacility.install(activation);
        return new Fixture(activation, prelude);
    }

    private static String guestErrorDiagnostic(
            ProtosExecutionOutcome outcome, ProtosPrelude prelude) {
        if (outcome.error() == null) {
            return "state=" + outcome.state() + ", guestError=<none>";
        }

        ProtosObjectValue error = outcome.error();
        Object parent = error.parent().orElse(null);
        return "state="
                + outcome.state()
                + ", errorClass="
                + error.getClass().getName()
                + ", errorSlots="
                + error.localSlotsSnapshot()
                + ", parentBinding="
                + preludeBindingName(parent, prelude)
                + ", parentClass="
                + (parent == null ? "<none>" : parent.getClass().getName())
                + ", parentSlots="
                + objectSlots(parent);
    }

    private static String preludeBindingName(Object value, ProtosPrelude prelude) {
        if (value == null) {
            return "<none>";
        }
        for (var entry : prelude.bindings().localSlotsSnapshot().entrySet()) {
            if (entry.getValue() == value) {
                return entry.getKey();
            }
        }
        return "<unbound>";
    }

    private static Object objectSlots(Object value) {
        return value instanceof ProtosObjectValue object
                ? object.localSlotsSnapshot()
                : "<not-object>";
    }

    private record Fixture(ProtosActivation activation, ProtosPrelude prelude) {}
}

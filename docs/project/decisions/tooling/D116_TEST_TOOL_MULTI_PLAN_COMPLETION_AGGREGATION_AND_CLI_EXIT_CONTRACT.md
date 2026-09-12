# D116 — Test Tool multi-plan completion aggregation and CLI exit contract

Status: **RATIFIED — Candidate B′ selected**

Allocated: **2026-09-12**

Explicit project-owner approval: **2026-09-12**

Decision issue: GitHub #443

Nature: implementation-independent Test Tool invocation aggregation / CLI classification contract

Triggered by: `TOOL002-I8D5B` closure and I8D5C public-cutover preflight.

Primary consumer: `TOOL002-I8D5C`

Normative language effect: **none**.

## Decision boundary

The public Test Tool currently owns several plans in one invocation:

```text
primary conformance plan
actor plan
group plan
package/TOML plan
```

Historically `Main.protos` executes all of them but returns only the primary `runTuple`, and the Java
bundled-tool host historically discarded that return value and returned exit `0` for any normal
module completion.

D114 ratifies:

```text
0  completed success
1  completed guest/test failure
3  infrastructure-aborted
```

and requires the historical healthy `runTuple` to remain unchanged.

D114 does not itself define how one invocation aggregates guest/test failure across multiple owned
plans. D116 closes that gap.

## Selected contract — Candidate B′

The selected architecture is:

> **aggregate completed classification across every plan actually executed by the invocation, while
> retaining each plan-local result and preserving the historical primary runTuple unchanged as the
> healthy compatibility payload**

The primary plan is primary for compatibility/reporting identity, not privileged for invocation exit
semantics.

## 1. Plan-local results remain local

Each plan keeps its existing runner semantics and runTuple.

D116 does not merge cases from actor/group/package plans into the primary runTuple.

Conceptually:

```text
primaryRun       = runTuple(...)
actorRun         = runTuple(...)
groupRun         = runTuple(...)
packageTomlRun   = runTuple(...)
```

Those local values remain valid evidence for their own plan.

## 2. Invocation-global completed classification aggregates all executed plans

For one `protos test` invocation:

```text
completedFailure =
    primaryRun.failed? ||
    actorRun.failed? ||
    groupRun.failed? ||
    packageTomlRun.failed? ||
    ... future executed plan failures
```

The exact mechanical summary representation may use failed count, passed/selected comparison, a
Boolean or equivalent bundled-Protos-owned structure.

What is durable is that every actually executed owned plan participates.

## 3. Exit 0 requires all executed owned plans to be healthy

A normally completed invocation returns exit `0` only when every executed owned plan is healthy
under that plan's existing guest/test policy.

Therefore:

```text
primary healthy + auxiliary healthy   -> 0
primary failed                         -> 1
actor failed                           -> 1
group failed                           -> 1
package/TOML failed                    -> 1
future owned plan failed               -> 1
```

An auxiliary failure may never be ignored merely because the primary runTuple is healthy.

## 4. Exit 1 is invocation-global completed guest/test failure

If one or more actually executed plans contain ordinary guest/test failure and no infrastructure
abort occurs:

```text
TestRunOutcome.status = "completed"
CLI exit = 1
```

The failure remains guest/test evidence, not infrastructure evidence.

D116 introduces no new failure category inside individual CaseRuns.

## 5. D114 infrastructure-aborted retains precedence

D114 remains unchanged.

If guest/test failures were already produced in any executed plan and a later D108 infrastructure
cutover occurs:

```text
TestRunOutcome.status = "infrastructure-aborted"
CLI exit = 3
```

Already-produced guest/test failures remain preserved as evidence.

Exit precedence does not rewrite those failures into infrastructure failures.

## 6. Historical healthy primary runTuple remains unchanged

The D114 healthy `run` payload remains the historical primary `runTuple`.

D116 does not replace it with:

- a multi-plan tuple;
- a new generalized plan collection;
- a flattened all-cases run;
- an event stream.

This preserves the explicit D114 compatibility guarantee.

Invocation-global classification is additional summary policy, not a mutation of the primary
runTuple.

## 7. Auxiliary runTuples remain internal evidence for the current carrier

Actor/group/package runTuples remain available inside bundled Protos while computing invocation
classification.

D116 does not make them newly public through the current D114 carrier.

A later machine-readable reporter, diagnostics contract or event-stream design may expose them
explicitly through a new bounded decision/API without changing D116 exit semantics.

## 8. The rule generalizes to N plans

D116 is not hard-coded semantically to the current count of four.

The durable rule is:

```text
all actually executed plans owned by one invocation participate
```

Therefore future plans may be added without reopening exit semantics solely because the cardinality
changes.

A plan intentionally not executed contributes nothing.

A case not admitted after D108 infrastructure cutover is governed by D108/D114 rather than
completed-failure aggregation.

## 9. Bundled Protos owns aggregation policy

The Java CLI host must not inspect every plan and recompute guest pass/fail policy.

Bundled Protos owns:

- which owned plans executed;
- each plan's existing run policy;
- invocation completed-failure aggregation;
- the final D114 TestRunOutcome classification data.

The host consumes the already-decided classification for exit/reporting.

This preserves TOOL002's architecture: test policy belongs in the bundled Protos Test Tool, while
Java owns host mechanics.

## 10. No execution short-circuit is implied

D116 defines final completed classification, not fail-fast scheduling.

An early guest/test failure does not by itself cancel later owned plans.

Existing plan execution ownership/order remains unchanged unless another decision explicitly
changes it.

Infrastructure fail-stop remains D108-owned and is distinct.

## 11. Unsupported/skipped semantics remain plan-local

D116 does not redefine existing skipped/unsupported policy.

A plan's runTuple continues to decide its local selected/passed/skipped accounting.

Invocation exit `1` is driven only by ordinary completed guest/test failure according to those
existing plan policies.

No skip is fabricated to represent another plan's failure.

## 12. Machine-readable/event evolution remains additive

D114 already allows a future ordered event/report stream.

D116 is compatible with:

```text
plan-local run results
        |
        +--> invocation aggregate classification
        |
        +--> future event/report stream
```

A future event stream may expose each plan and CaseRun incrementally while preserving the same final
0/1/3 invocation classification.

## 13. Primary-plan identity remains useful

The primary runTuple remains meaningful for compatibility and existing simple consumers.

D116 deliberately avoids making "primary" equivalent to "only plan that matters."

This separates:

```text
payload compatibility identity
from
invocation success authority
```

## 14. Public-cutover implication

After D116 ratification, I8D5C may mechanically implement the public cutover:

1. route all resource-aware owned plans through the ratified D113/D108 path as applicable;
2. preserve each plan-local run result;
3. aggregate completed guest/test failure across all actually executed plans in bundled Protos;
4. project to the D114 TestRunOutcome;
5. let Java consume the bundled-Protos-selected invocation classification;
6. map completed all-healthy to `0`, completed any-guest/test-failure to `1`,
   infrastructure-aborted to `3`, while preserving existing usage `2` and unexpected/internal `70`.

No additional aggregation decision is required merely because multiple current plans exist.

## Comparative audit

The owner approval followed an exhaustive comparison across language test tools, build systems,
multi-module runners and CI/reporting systems.

### Go `go test ./...`

A single command may execute tests across multiple packages.

Individual package results remain package-local, but a failure in any selected package makes the
overall command unsuccessful.

Lesson adopted: local result identity and global command status are separate.

### Cargo workspace test execution

Cargo can execute tests across multiple workspace packages/targets.

Individual test binaries/packages retain their own output, while failure in any participating test
target affects the overall command.

Lesson adopted: an auxiliary package/target is not allowed to fail invisibly behind a successful
"primary" target.

### Maven multi-module reactor

Each module retains its own lifecycle/test result while the reactor builds an overall invocation
result.

A module test failure affects the reactor result even though module identity/reporting remains
separate.

Lesson adopted: aggregation does not require flattening local result carriers.

### Gradle multi-project / multiple Test tasks

Gradle keeps task-local `Test` results and may aggregate reports separately.

The invocation/build outcome reflects failing test tasks across participating projects/tasks.

Lesson adopted: compatibility-local result structures can coexist with invocation-global status.

### Bazel test

Bazel retains target-level test result/summary information while the overall `bazel test` command
fails if selected test targets fail.

Lesson adopted: target-local structured evidence and command-global outcome are distinct layers.

### CTest

CTest runs a selected test set and reports per-test state plus aggregate pass/fail counts.

The overall invocation status reflects failures anywhere in the selected set.

Lesson adopted: completed classification is a property of the invocation, not a privileged first
suite.

### Meson test

Meson reports individual tests/suites while final command success depends on the aggregate run.

Lesson adopted: suite grouping does not exclude a suite from command-level correctness.

### pytest

pytest collects many modules/classes/tests and produces one session exit status from the collected
session.

Failures across the session participate in the global exit classification while item-level reports
remain distinct.

Lesson adopted: session-global classification should reflect every executed owned unit.

### .NET `dotnet test`

A solution/project invocation may execute multiple test assemblies/projects through the test
platform.

Per-test/project evidence remains distinct while failures contribute to the overall test command
status.

Lesson adopted: multi-container execution still requires aggregate final correctness.

### JUnit Platform

JUnit's execution model distinguishes container/test-node result structures from summary
aggregation. A container's own result is not necessarily a recursive aggregate of every child;
listeners/summaries perform broader aggregation.

Lesson adopted: do not mutate the primary/local carrier merely to obtain invocation aggregation.

### CI systems

Jenkins, GitHub Actions and similar orchestrators commonly retain multiple test-report artifacts
while deriving one job/build status from all relevant executed steps/test publishers.

Lesson adopted: summary status and detailed/local evidence are separate responsibilities.

## Candidate comparison

Scores are 1–10.

| Candidate | Future resilience | Scalability | Protos philosophy |
| --- | ---: | ---: | ---: |
| A — primary-plan-only classification | 3.0 | 4.0 | 3.0 |
| B — aggregate all current plans; keep primary run | 9.5 | 9.0 | 9.5 |
| C — replace healthy payload with multi-plan collection | 8.5 | 9.0 | 6.5 |
| D — stop auxiliary plans in public invocation | 3.0 | 5.0 | 3.5 |
| E — event/report stream first | 10.0 | 10.0 | 8.0 |
| **B′ — N-plan invocation aggregate + unchanged primary compatibility payload** | **10.0** | **9.5** | **10.0** |

## Why B′ is selected

B′ matches the dominant mature-tool pattern:

```text
local results remain local
global invocation status aggregates all relevant executed work
```

It also best preserves already-ratified D114 compatibility because the primary runTuple does not
change shape.

The generalization from four current plans to N avoids another semantic gate every time the Test
Tool adds an owned plan.

## Why Candidate A is rejected

A permits this invalid outcome:

```text
primary plan: PASS
actor plan:   FAIL

process exit: 0
```

That would make CI and user automation report success despite an actually executed Test Tool-owned
failure.

It also gives accidental semantic privilege to the oldest plan.

## Why Candidate C is rejected

C makes all plan results first-class immediately, which is attractive for reporting, but it
conflicts with D114's explicit decision to preserve the historical healthy runTuple.

That broader public-carrier change should not be smuggled into exit-code integration.

## Why Candidate D is rejected

D solves aggregation only by reducing current Test Tool coverage.

It would change existing ownership and execution semantics merely to simplify reporting.

## Why Candidate E is deferred

Event-stream-first has the highest theoretical distribution/reporting scalability.

D114 already preserves it as an additive future direction.

Adopting it now would broaden I8D5C far beyond the required public cutover and would not improve the
basic correctness rule that every executed plan must contribute to final classification.

## Strongest argument against B′

Auxiliary runTuples are not exposed as first-class public healthy payload fields today.

A consumer wanting complete multi-plan machine-readable detail will eventually need a richer report
or event API.

That is intentional. D116 optimizes correctness and compatibility now while preserving a clean
future reporting extension point.

## Regret scenario and escape path

If the Test Tool later grows many distributed plans/shards, the final aggregate can be computed
incrementally from an ordered event stream:

```text
events / per-plan summaries
        |
        +--> rich reporter
        |
        +--> same final completedFailure aggregate
```

The D116 exit contract survives unchanged.

## Ratified outcome

D116 ratifies:

```text
D116 = Candidate B′

all actually executed owned plans participate in completed 0/1 classification
plan-local runTuples remain plan-local
historical primary runTuple remains unchanged as healthy D114 compatibility payload
primary identity does not grant exclusive exit authority
completed any guest/test failure -> exit 1
completed all healthy -> exit 0
infrastructure-aborted -> exit 3 with precedence
bundled Protos owns aggregation policy
Java consumes the already-decided classification
rule generalizes to N plans
future event/report streaming remains additive
```

After publication, `TOOL002-I8D5C` is mechanically unblocked for the atomic public Main/CLI cutover.

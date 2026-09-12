# D114 — Test Tool infrastructure-abort result and reporting contract

Status: **RATIFIED — Candidate B′ selected**

Allocated: **2026-09-12**

Explicit project-owner approval: **2026-09-12**

Decision issue: GitHub #440

Nature: implementation-independent Test Tool run-outcome/reporting contract

Triggered by: `TOOL002-I8D4C3` closure after D108 private round-drain/fail-stop composition.

Primary consumer: `TOOL002-I`

Normative language effect: **none**.

## Decision boundary

D108 already requires infrastructure evidence to remain separate from guest-language semantics.

I8D4C3 now preserves enough private scheduler evidence to distinguish:

```text
executed guest CaseRuns
per-attempt infrastructure completions
selected CaseSpecs never admitted after cutover
retained UNSAFE reservation count
```

What remained undecided was the public/tool-level carrier and CLI classification used when
`protos test` terminates after a D108 infrastructure cutover.

D114 decides that boundary.

D114 does not define retry/quarantine/recovery, distributed fault scopes, timeout/kill,
machine-readable report file formats, or a public language-level Test result type.

## Selected contract — Candidate B′

The selected architecture is:

> **outer tagged TestRunOutcome + unchanged historical healthy runTuple + stable
> infrastructure-abort evidence + distinct CLI classification**

Conceptually:

```text
TestRunOutcome
    status
        "completed"
        "infrastructure-aborted"
```

The outer result belongs to Test Tool semantics, not guest-language Error semantics.

## 1. Historical healthy runTuple remains unchanged

A healthy completed invocation retains the already-existing run aggregate:

```text
completed
    run = historical runTuple
```

D114 does not add infrastructure fields to every healthy CaseRun or require resource-free runs to
pay for provider/infrastructure reporting machinery.

This preserves compatibility and the H resource-free fast path.

## 2. Infrastructure-aborted is a distinct outer outcome

When D108 fail-stop occurs, the outer Test Tool outcome is:

```text
infrastructure-aborted
```

It is not:

- a guest `Error`;
- a fabricated failed test;
- a fabricated skipped test;
- a normal completed run with only a different message; or
- an unexpected internal CLI exception.

Infrastructure-abort is a first-class Test Tool invocation outcome.

## 3. Minimum aborted payload

The stable aborted payload preserves at least:

```text
executedCaseRuns
ordinaryUnsupportedCount
infrastructureAttempts
cutoverNotAdmittedCases
retainedUnsafeReservationCount
```

Equivalent field/prototype representation may be chosen mechanically by the implementation, but
these evidence categories and their separation are durable.

`executedCaseRuns` retain TestPlan logical order under D105/D108.

`infrastructureAttempts` retain exact owning-attempt association and deterministic lifecycle
evidence order.

`cutoverNotAdmittedCases` contain exact selected CaseSpecs not admitted because D108 stopped the
invocation.

## 4. Guest and infrastructure outcomes remain orthogonal

D114 preserves all four important cases:

### Guest/test failure, infrastructure healthy

```text
guest CaseRun = failed
infrastructure = absent
outer status = completed
```

This is an ordinary test failure.

### Guest success/failure followed by infrastructure cleanup failure

```text
guest CaseRun = preserved
infrastructure = present
outer status = infrastructure-aborted
```

The guest evidence is not erased or reclassified.

### Infrastructure failure before guest start

```text
guest CaseRun = absent
infrastructure = present
outer status = infrastructure-aborted
```

No guest test result is fabricated.

### Case never admitted after D108 cutover

```text
guest CaseRun = absent
infrastructure attempt = absent for that case
cutoverNotAdmitted = present
```

This is distinct from ordinary unsupported/skipped/non-selected semantics.

## 5. Exit-status classification

The baseline CLI classification is:

```text
0   completed; all executed/selected guest tests successful under existing Test Tool policy
1   completed; one or more ordinary guest/test failures
2   CLI usage error (existing)
3   infrastructure-aborted
70  unexpected/internal tool failure (existing)
```

Exit code `3` is Test Tool infrastructure-abort, not a general new Protos language/runtime status.

## 6. Infrastructure-abort has invocation exit precedence

If guest test failures have already occurred and the same invocation later reaches D108
infrastructure-abort:

```text
exit = 3
```

because the invocation did not complete as a normal test run.

The earlier guest failures remain preserved in `executedCaseRuns`.

Exit precedence does not rewrite evidence classification.

## 7. Internal error remains different from infrastructure-abort

An expected D107/D108 infrastructure failure such as:

- unresolved configured provider;
- provisioning rejection;
- provider transport failure;
- terminalization failure;
- ProviderLease cleanup failure; or
- unsafe capacity disposition

is reported through the D114 infrastructure-aborted carrier.

A defect/unexpected exception in the CLI/Test Tool implementation remains the existing internal
error class and may still use exit `70`.

D114 does not convert arbitrary programming bugs into structured infrastructure evidence.

## 8. Human-readable reporting

The initial human-readable reporter must make the invocation-level infrastructure-abort explicit.

It should preserve/report enough bounded information to identify:

- owning logical provider when applicable;
- affected logical resource key(s) when applicable;
- lifecycle/infrastructure category or phase;
- bounded sanitized diagnostic message;
- count/list of selected cases not admitted by cutover;
- count of retained unsafe reservations; and
- guest test results already produced.

Exact prose/layout is not ratified here.

## 9. Sanitization boundary

Public reporting must not blindly expose host exception internals.

In particular it must not leak:

- credentials/tokens;
- secret-store material;
- provider-private configuration;
- unnecessary filesystem paths;
- private endpoints;
- full host stack traces as ordinary user diagnostics.

Provider logical identity, resource keys, lifecycle category and a bounded sanitized message are
appropriate durable diagnostic concepts.

Unexpected internal failure may retain the CLI's separate debugging/internal-error behavior.

## 10. Retained unsafe capacity is invocation-local evidence

`retainedUnsafeReservationCount` means only that the current invocation did not return some
capacity because safe reuse was unconfirmed.

It does not imply a ratified institution for:

- persistent quarantine;
- poisoned-resource databases;
- worker draining across invocations;
- health recovery;
- automatic replacement; or
- retry.

Those remain future decisions.

## 11. Healthy reporting remains pay-for-use

For a healthy resource-free run:

```text
provider infrastructure evidence = absent
outer status = completed
historical runTuple = retained
```

D114 does not require per-case infrastructure envelopes in the ordinary fast path.

## 12. Machine-readable reporter evolution

The outer outcome is intentionally stable enough for later JSON/JUnit/CI reporters.

Future reporters may project the same evidence without rerunning tests.

D114 does not freeze a JSON schema or JUnit XML mapping.

Any such public serialization is a separate bounded contract.

## 13. Event-stream evolution is additive

An ordered event/report stream is a strong future architecture for very large or distributed test
runs.

D114 deliberately does not make event-stream-first the baseline today.

Future architecture may become:

```text
scheduler
   |\
   | +--> ordered/structured event stream
   |
   +----> final TestRunOutcome aggregate
```

The final aggregate outcome remains useful for CLI exit classification and simple consumers.

An event stream therefore augments rather than replaces B′.

## 14. Main/CLI integration boundary

Public `Main.protos` may convert the private I8D4C3 D108 envelope into the D114 TestRunOutcome.

The host CLI may inspect the completed Test Tool result only to:

- emit the selected human-readable reporting; and
- choose the D114 exit classification.

The host must not recompute guest pass/fail policy or reinterpret infrastructure evidence as guest
semantics.

## Comparative audit

The owner approval followed an exhaustive comparison across test frameworks, build/test
orchestrators, CI systems and remote execution protocols.

### Bazel Remote Execution API

REAPI provides the closest architectural precedent.

Execution infrastructure/status is represented separately from the action result, allowing an
executor to report infrastructure failure without forging an application/test result.

D114 adopts the same lane separation at Test Tool level.

### pytest

pytest distinguishes normal test failure, interruption, internal error and command-line usage
through separate exit classifications and preserves setup/call/teardown evidence.

This strongly supports a distinct infrastructure-abort classification instead of treating every
failure as one failed test.

### JUnit Platform

JUnit distinguishes successful, aborted and failed execution states and separates engine/container
lifecycle from individual test bodies.

D114 similarly preserves framework/tool outcome outside guest test semantics.

### Buck2

Buck2's test orchestration/reporting architecture demonstrates the scalability value of structured
test result states and a separate orchestration layer.

It also motivates D114's future event-stream escape path.

### Go test

Go's tooling scales effectively and provides structured event data, but its final process status is
comparatively coarse.

D114 retains richer evidence rather than collapsing already-available D108 distinctions.

### Cargo/libtest

Cargo/libtest cleanly separates harness execution from application code, but the final success/fail
surface is comparatively coarse for infrastructure diagnosis.

This is a useful simplicity baseline but not sufficient for D108 fidelity.

### CTest

CTest distinguishes pass/fail/not-run and has explicit resource allocation machinery.

However, overloading generic not-run/skipped concepts would lose D108's exact cutover reason, so
D114 keeps a separate run-level category.

### Jenkins

Jenkins commonly distinguishes test-result degradation (`UNSTABLE`) from pipeline/agent
infrastructure failure.

This supports preserving guest failures while allowing infrastructure state to dominate final run
classification.

### GitHub Actions

Actions separates job/runner cancellation/failure from test report artifacts, demonstrating that
the orchestrator owns final infrastructure status while test evidence remains separately useful.

### Kubernetes Job/Pod conditions

Kubernetes separates workload termination/result evidence from scheduling/node/container
infrastructure conditions.

The analogy supports an outer orchestration status rather than guest-result fabrication.

## Candidate comparison

Focused scores are 1–10.

| Candidate | Future resilience | Scalability | Protos philosophy |
| --- | ---: | ---: | ---: |
| A — collapse into ordinary failed tests | 2.5 | 4.0 | 1.0 |
| B — outer tagged outcome; healthy runTuple retained | 10.0 | 9.5 | 10.0 |
| C — one generalized run record | 9.0 | 9.5 | 8.0 |
| D — host exception/fatal process channel | 6.0 | 8.0 | 4.5 |
| E — event/report stream first | 10.0 | 10.0 | 8.0 |
| **B′ — tagged outcome + stable abort payload + exit category** | **10.0** | **9.5** | **10.0** |

## Why B′ is selected

B′ preserves D108 evidence exactly while minimizing disruption to healthy Test Tool execution.

It provides:

- compatibility with the historical `runTuple`;
- explicit infrastructure semantics;
- clean CLI classification;
- machine-readable evolution;
- preserved partial guest evidence; and
- an additive route to future event streaming.

It avoids prematurely replacing a working aggregate runner/reporting model solely to optimize for a
distributed scale that the current Test Tool has not yet implemented.

## Strongest argument against B′

Event-stream-first has better pure scaling characteristics for huge distributed runs because it can
report incrementally without retaining every result until the final aggregate.

That is the strongest argument for Candidate E.

B′ is selected because the event stream can be added later while keeping the same final aggregate,
whereas adopting E now would prematurely force a broader reporting architecture migration.

## Regret scenario and escape path

If future remote execution produces millions of case events or very long-running shards, add a
structured event stream emitted from the same scheduler state.

The final B′ TestRunOutcome remains the end-of-run aggregate and CLI status source.

No guest semantics or D108 lifecycle contract must change.

## Ratified outcome

D114 ratifies:

```text
D114 = Candidate B′

outer tagged TestRunOutcome
historical healthy runTuple unchanged
stable infrastructure-abort evidence
no fabricated guest result
distinct CLI infrastructure-abort classification
sanitized bounded diagnostics
event stream may be added later
```

After publication, TOOL002-I may implement the public Main/CLI reporting cutover together with the
independently ratified D113 host registry-injection contract.

# D134 — CaseAuthority lifecycle failure classification in D108

Status: RATIFIED — Candidate A″ selected  
Issue: #527  
Explicit project-owner approval: 2026-09-15  
Primary consumer: TOOL005-B3B  
Depends on: D108, D114, D116, D126, D129, D133

## Decision

CaseAuthority provisioning, terminalization and teardown failures are Test Tool
infrastructure evidence, not guest/test failures.

Generation 1 uses the existing D108/D114 round-drain fail-stop policy as the
smallest conservative baseline.

```text
D134_SELECTED_CANDIDATE=A_DOUBLE_PRIME

CASE_AUTHORITY_FAILURE_CLASS=INFRASTRUCTURE
CASE_AUTHORITY_FAILURE_IS_GUEST_FAILURE=NO
```

This choice does not mean that a case-scoped authority has an invocation-wide
fault scope by nature.

The baseline stops globally because the current Test Tool has no ratified
fault-scope, quarantine, recovery or unaffected-placement continuation
institution capable of proving that later cases are safe to execute.

## Pre-scheduling boundary

D126 and D129 remain responsible for every error that can be established before
physical authority materialization.

Examples include:

```text
malformed CaseAuthorityDescriptor
unsupported authorityKind
invalid logical fixture identity
missing CorpusBinding
unsupported plan loader
invalid logical TestPlan
```

Such failures must fail closed before scheduling whenever they can be validated
without constructing physical authority.

D134 concerns dynamic lifecycle failures that occur only after a case has been
admitted.

## Provisioning failure before guest execution

If physical CaseAuthority materialization fails before guest execution begins:

```text
guestObservation = absent
infrastructureOutcome = present
```

No CaseRun or guest failure is fabricated.

The admitted attempt reaches terminal infrastructure disposition according to
D108.

## Guest execution followed by successful teardown

If authority provisioning succeeds, guest execution completes, and authority
teardown completes successfully:

```text
guestObservation = ordinary guest evidence
infrastructureOutcome = absent
```

Existing guest success/failure semantics remain unchanged.

## Guest execution followed by teardown failure

If guest execution has already produced evidence and CaseAuthority teardown,
revocation, terminalization or equivalent cleanup later fails:

```text
guestObservation = preserved
infrastructureOutcome = present
```

The guest result is not erased or reclassified.

A guest success remains evidence of guest success.  
A guest failure remains evidence of guest failure.

The enclosing Test Tool invocation nevertheless becomes infrastructure-aborted.

## D108 round-drain behavior

Once CaseAuthority infrastructure failure is observed:

```text
already admitted siblings
    -> continue to their terminal disposition

not-yet-admitted cases
    -> are not admitted

current invocation
    -> infrastructure-aborted
```

Already-admitted work is not abruptly cancelled merely because another attempt
failed infrastructure lifecycle.

This preserves D108 terminal-custody and cleanup guarantees.

## D114 / D116 classification

D114 remains the public Test Tool outcome authority.

```text
ordinary guest/test failure
    -> completed
    -> CLI exit 1

CaseAuthority infrastructure failure
    -> infrastructure-aborted
    -> CLI exit 3
```

Infrastructure-abort retains precedence if ordinary guest failures were produced
earlier in the invocation.

D116 multi-plan aggregation remains unchanged.

## CaseAuthority is not a generic resource

D134 does not reclassify CaseAuthority into D077/D098.

```text
CASE_AUTHORITY_IS_GENERIC_RESOURCE=NO
RESOURCE_CAPACITY_ACCOUNTING_CHANGED=NO
```

A project-tree authority does not synthesize:

```text
ResourceKey
provider
profile
reservation
capacityDisposition
retainedUnsafeReservationCount
```

merely to reuse infrastructure reporting semantics.

CaseAuthority remains the orthogonal TestPlan/Case execution authority ratified
by D129 and D133.

## Internal-error boundary

Expected physical lifecycle failures belong to infrastructure evidence.

Unexpected programming defects in the Test Tool implementation remain internal
tool failure and are not automatically converted into D114 infrastructure
evidence.

D134 does not redefine the existing internal-error classification.

## Why guest failure was rejected

A provisioning failure can occur before any guest code starts.

Reporting:

```text
test failed
```

in that situation would fabricate guest/test evidence from host lifecycle
failure.

That conflicts with the guest/infrastructure evidence separation already
ratified by D108 and D114.

Go testing and JUnit demonstrate that collapsing fixture lifecycle into ordinary
test failure is practical, but that model would discard distinctions that Protos
already preserves deliberately.

## Why a new authority-aborted outcome was rejected

A separate public category such as:

```text
authority-aborted
```

would encode one concrete infrastructure mechanism into the top-level Test Tool
result taxonomy.

The same approach could later proliferate:

```text
worker-aborted
sandbox-aborted
transport-aborted
authority-aborted
```

D114 already owns a suitable general infrastructure-aborted category.

CaseAuthority does not justify another institution.

## Why case-local continuation was not selected yet

A CaseAuthority is case-scoped, but this does not prove that every authority
failure is case-scoped.

Examples of apparently per-case provisioning failures that may indicate broader
host failure include:

```text
file-descriptor exhaustion
disk failure
permission/configuration corruption
filesystem provider failure
sandbox subsystem failure
worker death
remote materializer failure
```

Therefore:

```text
case-scoped authority
does not imply
case-scoped fault
```

Continuing after infrastructure failure without a ratified fault-scope model
would silently assume safety that the Test Tool cannot currently prove.

## Future fault-scoped continuation

D134 intentionally leaves open a later evolution such as:

```text
infrastructureOutcome
    + faultScope

case-local fault
    -> continue independent work

worker-local fault
    -> drain/quarantine worker
    -> continue elsewhere

unknown/global fault
    -> fail-stop
```

This is deferred rather than preimplemented.

Adding it later need not change:

```text
SuiteId
CorpusId
CaseId
ExecutionRequirementId
CaseAuthorityDescriptor
guest-result semantics
```

It would primarily extend D108 scheduling/cutover policy and D114
infrastructure evidence/reporting.

## Deferred capabilities

```text
FAULT_SCOPE=DEFERRED
QUARANTINE=DEFERRED
RETRY=DEFERRED
UNAFFECTED_CASE_CONTINUATION=DEFERRED
REMOTE_RECOVERY=DEFERRED
```

No dormant scaffolding for these capabilities is required by D134.

## Prior-art audit

The decision followed the AGENTS.md exhaustive Dxxx process.

Relevant systems included:

- pytest — setup/call/teardown are distinct phases; fixture failure need not be
  reported as failure of the test body, and session continuation is separately
  controlled;
- Go testing — `TempDir` setup/cleanup failure is folded into the test result,
  demonstrating the strongest simple alternative;
- JUnit — lifecycle exceptions commonly converge into node FAILED state,
  likewise demonstrating the collapsed-result alternative;
- Erlang Common Test — setup and teardown have lifecycle-specific behavior and
  show the complexity that can result from highly configurable lifecycle/result
  interactions;
- cargo-nextest — setup-script failure can terminate the complete run while
  already-running work is handled by the orchestrator;
- Bazel Remote Execution — action/application result remains distinct from
  executor/infrastructure status, providing strong remote-execution precedent;
- Kubernetes — initialization/provisioning outcome remains distinct from
  workload result, while retry/propagation belongs to orchestration policy.

The evidence does not establish one universal propagation policy.

It does strongly support preserving the distinction between guest execution
evidence and infrastructure lifecycle evidence.

## Candidate conclusion

The serious surviving alternatives were:

```text
A″  infrastructure lane + existing round-drain global fail-stop
B   collapse authority lifecycle into ordinary test failure
C   introduce authority-aborted as a new top-level outcome
D′  infrastructure lane + continue later independent cases
E′  fault-scoped hybrid with worker/case/global recovery policy
F   defer TOOL005-B3B and retain the Java wrapper
```

A″ is selected because it:

- preserves D108/D114 evidence fidelity;
- requires no new public result category;
- preserves D129/D133 authority orthogonality;
- pays only for capability needed by TOOL005 today;
- introduces no speculative fault-domain institution;
- keeps the ordinary resource-free execution path unchanged;
- supports bounded parallelism;
- permits later fault-scoped continuation without changing logical identities;
- avoids claiming that physical authority failure is guest behavior.

D′ remains the strongest future evolution candidate once the Test Tool can
actually prove fault isolation.

E′ is intentionally future-compatible rather than future-preimplemented.

## Ratified outcome

```text
D134_SELECTED_CANDIDATE=A_DOUBLE_PRIME

CASE_AUTHORITY_FAILURE_CLASS=INFRASTRUCTURE
CASE_AUTHORITY_FAILURE_IS_GUEST_FAILURE=NO

PRE_GUEST_PROVISION_FAILURE_GUEST_OBSERVATION=ABSENT
PRE_GUEST_PROVISION_FAILURE_INFRASTRUCTURE_OUTCOME=PRESENT

POST_GUEST_TEARDOWN_FAILURE_PRESERVE_GUEST_OBSERVATION=YES
POST_GUEST_TEARDOWN_FAILURE_INFRASTRUCTURE_OUTCOME=PRESENT

ALREADY_ADMITTED_SIBLINGS=DRAIN_TO_TERMINAL
NEW_ADMISSIONS_AFTER_INFRASTRUCTURE_FAILURE=NO

FINAL_OUTCOME=INFRASTRUCTURE_ABORTED
CLI_EXIT=3

CASE_AUTHORITY_IS_GENERIC_RESOURCE=NO
RESOURCE_CAPACITY_ACCOUNTING_CHANGED=NO

FAULT_SCOPE=DEFERRED
QUARANTINE=DEFERRED
RETRY=DEFERRED
UNAFFECTED_CASE_CONTINUATION=DEFERRED
REMOTE_RECOVERY=DEFERRED
```

TOOL005-B3B is therefore released to implement the physical CaseAuthority
lifecycle under D129, D133 and D134.

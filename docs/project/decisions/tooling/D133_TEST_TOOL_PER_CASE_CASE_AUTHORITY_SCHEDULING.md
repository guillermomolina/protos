# D133 — Test Tool per-case CaseAuthority scheduling boundary

Status: RATIFIED — Candidate B′ selected
Issue: #519
Primary consumer: TOOL005-B3B
Depends on: D122, D123, D125, D126, D129, D132

## Decision

Per-case project-tree authority is introduced through an inert,
case-scoped `CaseAuthorityDescriptor` carried by the logical case plan and
materialized only at the scheduling/execution boundary.

The scheduler does not receive a physical filesystem as part of logical case
identity. Instead:

```text
CorpusBinding
    -> TestPlan
        -> CaseSpec
            -> CaseAuthorityDescriptor
                -> scheduling boundary
                    -> CaseAuthority
                        -> execute
                        -> deterministic teardown
```

## Candidate

```text
D133_SELECTED_CANDIDATE=B_PRIME
D133_FUTURE_RESILIENCE=10/10
D133_SCALABILITY=10/10
D133_PROTOS_PHILOSOPHY=10/10
```

## Core boundary

`CaseSpec` remains inert serializable data.

A logical case may carry a `CaseAuthorityDescriptor`, but the descriptor is not a
physical authority and must not contain:

```text
absolute physical path
host worker identity
open file handle
process handle
Java implementation object
temporary directory identity
```

A conceptual descriptor may expose only stable logical provisioning semantics,
for example:

```text
authorityKind = "project-tree"
fixtureIdentity = logical fixture identity
isolation = "case"
lifecycle = "case"
```

The exact serialized field names remain implementation detail unless separately
ratified.

## Materialization timing

The entire logical TestPlan remains validated/materialized before scheduling,
as required by D126.

However, physical project-tree authorities are created only when a case is
admitted to execution.

Therefore:

```text
plan validation/materialization
    -> all logical case descriptors exist

case scheduling
    -> materialize one CaseAuthority

case execution
    -> use that authority

case completion/failure/cancellation
    -> deterministic teardown
```

The Test Tool must not eagerly create all physical project trees merely because
the full TestPlan was materialized.

## Isolation

Each executing project-tree case receives an independent physical authority.

Concurrent cases must never share a writable/mutable project-tree authority.

This remains valid for bounded parallelism:

```text
--jobs N

case A -> authority A
case B -> authority B
...
case N -> authority N
```

A retry or repeated execution of the same logical case receives a fresh physical
authority.

## Identity separation

D125/D126/D129 remain orthogonal:

```text
SuiteId
    -> logical suite identity

CorpusId
    -> corpus/source authority identity

CaseId
    -> logical case identity

ExecutionRequirementId
    -> execution/inspection mechanics

CaseAuthorityDescriptor
    -> logical provisioning requirements

CaseAuthority
    -> physical per-case authority
```

The physical path is never the CaseId.

Changing checkout location, worker, temporary root, remote placement, or local
sandbox location does not change logical identity.

## D108 integration boundary

The public D108 semantics remain the execution aggregation mechanism.

D133 does NOT authorize replacing D108 with one Runner invocation per case.

D133 also does NOT authorize mutating a suite-wide filesystem before each case.

Instead, the existing D108 architecture must gain a bounded, explicit case-scoped
authority bridge that allows each scheduled case to receive its own materialized
CaseAuthority while preserving:

- case ordering;
- bounded parallelism;
- progress observation;
- cancellation;
- run aggregation;
- final invocation outcome;
- existing D108/D114/D116 semantics.

The bridge may be implemented as a runner-owned case authority callback/provider,
a case-scoped execution envelope, or an equivalent mechanism that preserves the
normative contract above. The implementation choice inside those constraints does
not create another decision unless it introduces a materially new semantic axis.

## Resource boundary

A per-case project-tree authority is NOT a D077/D098 generic resource.

Do not model it as a generic provider/resource identity merely to obtain
scheduler injection.

The authority belongs to the TestPlan/Case execution lifecycle.

## Discovery

No filesystem discovery is introduced.

Project-tree fixture membership remains explicit TestPlan/CaseSpec membership.

Forbidden:

```text
recursive fixture discovery
glob-based membership
ambient workspace inference
current-working-directory inference
path-to-identity inference
nearest-directory fallback
```

## Lifecycle

The CaseAuthority lifecycle is exactly case-scoped:

```text
create at scheduling admission
    -> execute
    -> teardown on success
    -> teardown on failure
    -> teardown on cancellation
```

Cleanup must be deterministic and must not depend on successful test completion.

A failed provisioning attempt fails that case closed and must not leave a
logically live authority behind.

## Parallel and future remote execution

The physical authority may be local, sandboxed, containerized or remote in a
future implementation.

D133 intentionally does not encode transport details.

The only invariant is that the executing case sees exactly its own authority.

This permits:

```text
local filesystem
container mount
sandbox root
remote worker workspace
```

without changing CaseId or CorpusId semantics.

## B3B scope

TOOL005-B3B may implement this contract for:

```text
protos/corpus/package-tool/resolution-root
protos/corpus/package-tool/execution-plan
protos/corpus/package-tool/project-projection
```

using:

```text
protos/test/package
```

unless their already-established execution requirements say otherwise.

D133 does not alter corpus membership, Package Tool semantics, or the existing
plain corpus implementation completed by B3A.

## Explicit non-goals

D133 does not:

- change the Protos language specification;
- change package semantics;
- change D125 execution-requirement identity;
- change D126 corpus identity;
- change D129 CaseAuthority identity/lifecycle requirements;
- introduce a generic resource provider for project trees;
- replace D108 with per-case top-level invocations;
- authorize shared mutable suite filesystem state;
- require eager physical project-tree creation.

## Scoring

```text
D133_SELECTED_CANDIDATE=B_PRIME
D133_FUTURE_RESILIENCE=10/10
D133_SCALABILITY=10/10
D133_PROTOS_PHILOSOPHY=10/10
```

## Governance consequence

D133 unblocks the architectural implementation of TOOL005-B3B.

No further decision is required merely to implement the case-authority bridge
specified above.

A new Dxxx decision remains required if implementation discovers a materially
independent semantic axis outside this contract.

## Closure contract

```text
SPECIFICATION_CHANGED=NO
IMPLEMENTATION_CHANGED=NO
IMPLEMENTATION_VERSION_CHANGED=NO
```

The decision record is governance/tooling only.

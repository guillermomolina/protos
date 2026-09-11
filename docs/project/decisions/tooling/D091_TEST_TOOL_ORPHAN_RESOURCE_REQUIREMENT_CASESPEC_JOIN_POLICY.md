# D091 — Test Tool orphan resource-requirement / CaseSpec join policy

Status: **RATIFIED — Candidate A′ selected**

Allocated: **2026-09-11**

Explicit project-owner approval: **2026-09-11**

Decision issue: GitHub #380

Triggered by: `TOOL002-I6C` publication `668f18a621a078d603477071eff74f4a2ff20efe`

Primary consumer: `TOOL002-I` / GitHub #96

Predecessors: `D076`, `D077`, `D087` — RATIFIED

Nature: implementation-independent Test Tool corpus/configuration contract

Normative language effect: **none**.

## Decision boundary

D077 selected sparse strict/versioned TOML resource-requirement data associated
with an existing test corpus, and I6C now parses that data into validated inert
ordered `(case, Requirement)` declarations without joining them to CaseSpecs.

The next implementation boundary is referential integrity: a syntactically and
schema-valid declaration can name a canonical relative `case` that is absent
from the complete associated manifest/TestPlan.

D091 decides only how that orphan declaration is treated. It does not select the
physical sidecar filename, filesystem discovery, catalog CLI/source spelling,
resource scope vocabulary, provider APIs, reservation/fairness/retry/timeout,
sharding/remote policy, or a public `std:toml` API.

## Selected contract — strict full-TestPlan referential integrity

Candidate A′ is selected.

The durable ordering is:

```text
complete manifest
    -> complete CaseSpec/TestPlan construction
    -> D077 requirement-document parsing
    -> strict requirement.case -> CaseSpec join validation
    -> validated immutable planning data
    -> invocation filtering / selection
    -> sharding / placement
    -> reservation / execution
```

The selected rules are:

1. `requirement.case` is an exact intrinsic reference into the **complete
   associated TestPlan**. It is not a selector expression.
2. Every parsed requirement declaration must resolve to **exactly one** existing
   CaseSpec under the already-selected case identity correspondence.
3. A declaration that resolves to no CaseSpec is an invalid corpus/configuration
   condition and fails closed before scheduling or executing cases.
4. Invocation filtering, test selection, sharding, prioritization and placement
   occur only after this validity check and do not redefine whether a declaration
   is orphaned.
5. A CaseSpec that exists in the complete TestPlan but is excluded by the current
   invocation remains a valid referent; it is not an orphan.
6. The requirements document never creates or discovers CaseSpecs and therefore
   cannot materialize a phantom/resource-only test case.
7. Orphan declarations are not silently dropped, downgraded to warning-only
   executable state, or carried into later scheduler/worker stages for delayed
   resolution.
8. Diagnostic wording, aggregation, editor presentation and repair suggestions
   remain implementation/UI concerns and do not change corpus validity.

## Why validation uses the complete plan

Corpus validity and execution selection are separate concerns.

For example, if the complete TestPlan contains `A`, `B` and `C`, and `B` has a
resource requirement, an invocation that selects only `A` does not make `B`'s
metadata stale. Validating against the selected subset would make the same
repository alternate between valid and invalid depending on filters or shards.

D091 therefore establishes one stable validation boundary before any execution
subset is derived.

## Failure invariant

The join establishes one small invariant:

```text
for every D077 requirement declaration R:
    exactly one complete-TestPlan CaseSpec C exists
    such that R.case == C's canonical case identity
```

Failure to establish that invariant is configuration/corpus evidence. It is not
a guest Protos Error fabricated by a test case and it is not a resource-provider
failure.

## Refactor and rename behavior

Strict validation intentionally catches stale intrinsic metadata.

If a resourceful test is renamed from `old.protos` to `new.protos` while its
requirement declaration still names `old.protos`, execution fails closed until
the metadata is reconciled. The alternative would silently make the renamed test
resource-free, potentially converting a deterministic configuration defect into
an intermittent concurrent-resource failure.

This is a feature of the selected model, not incidental implementation strictness.

## Scalability and distributed execution

The contract admits a linear validation strategy:

```text
N complete CaseSpecs -> O(N) identity index
R declarations       -> O(R) expected join/validation
```

After attachment, temporary indexing may be discarded. Shards and workers consume
only already-valid CaseSpecs and therefore do not need an orphan-resolution
protocol.

This preserves D076's inert-planning boundary and scales without making remote
workers, schedulers or resource providers responsible for corpus referential
integrity.

## Prior-art findings

The comparative audit in GitHub #380 covered several materially different
reference models.

### CMake / CTest

CTest metadata such as test properties belongs to already-defined tests; exact
references are validated separately from later execution filtering. This is the
closest direct precedent for validating complete-plan identity before selection.

### JUnit Jupiter

`ResourceLock` metadata is structurally attached to an existing test class or
method, so orphan intrinsic metadata is naturally impossible. D091 recreates
that invariant explicitly because D077 intentionally stores sparse metadata in a
separate document.

### Bazel

Exact labels participate in a statically validated target graph; missing exact
targets are errors. Test filtering is a later execution concern and does not
weaken graph identity validity.

### TypeScript

The exact `files` list fails when a named file does not exist, while set-oriented
selection is represented separately through mechanisms such as `include`. This
supports treating exact identity and selection as distinct contracts.

### Terraform

Statically invalid references remain errors rather than being normalized into
absence by dynamic error-handling helpers. This supports fail-closed configuration
graph integrity.

### Docker Compose

Required object references fail when their referents are absent. Warning/optional
behavior is explicit where supported rather than inferred from a broken required
reference. D077 v1 contains no optional-orphan declaration semantics.

### cargo-nextest and pytest

These ecosystems demonstrate why zero-match behavior is legitimate for selectors
and filter expressions. D077 deliberately did not select selector/filter ownership
for intrinsic resource requirements, so that tolerance does not transfer to its
exact `case` field.

### Kubernetes

Kubernetes demonstrates a valid delayed-resolution model when referenced objects
have independent lifecycles in an eventually consistent control plane. The
complete Protos TestPlan has no such lifecycle: once constructed, a missing case
is stale metadata rather than a temporarily unavailable external object.

## Candidate set

### A′ — fail closed against the complete TestPlan

**Selected.** Validate exact intrinsic references before filtering/sharding and
reject any orphan declaration.

### B — silently ignore orphan declarations

Rejected because a test rename could silently remove resource protection and turn
a deterministic metadata defect into nondeterministic execution behavior.

### C — warn and ignore

Rejected because warning-only execution preserves the same unsafe semantics as
silent ignore. Optionality should be explicit if a future schema ever needs it.

### D — preserve unresolved declarations for later resolution

Rejected for the current static complete-TestPlan architecture. It would make
validity time-dependent and push unresolved corpus state into scheduling,
distribution and worker boundaries without a present need.

### E — materialize phantom/resource-only CaseSpecs

Rejected because the requirements sidecar would become a second test-discovery
authority without owning the source/expectation semantics required to define a
real test case.

### F — configurable strictness

Rejected because `error`, `warn` and `ignore` modes would create multiple
invocation-dependent definitions of corpus validity for the same persisted v1
document.

## Focused scores

Scores are 1–5.

| Candidate | Future resilience | Scalability | Protos alignment |
| --- | ---: | ---: | ---: |
| **A′ — fail closed** | **5.0** | **5.0** | **5.0** |
| B — silent ignore | 1.5 | 2.5 | 1.5 |
| C — warn + ignore | 2.5 | 3.0 | 2.5 |
| D — preserve unresolved | 3.5 | 2.5 | 2.5 |
| E — phantom cases | 1.5 | 2.0 | 1.0 |
| F — configurable strictness | 3.5 | 3.5 | 2.5 |

## Full comparative scorecard

| Criterion | **A′ Fail** | B Ignore | C Warn | D Preserve | E Phantom | F Configurable |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariant preservation | **5.0** | 1.0 | 2.0 | 3.0 | 1.0 | 3.5 |
| Protos alignment | **5.0** | 1.5 | 2.5 | 2.5 | 1.0 | 2.5 |
| Future-option resilience | **5.0** | 1.5 | 2.5 | 3.5 | 1.5 | 3.5 |
| Scalability | **5.0** | 2.5 | 3.0 | 2.5 | 2.0 | 3.5 |
| Conceptual simplicity | **5.0** | 5.0 | 4.0 | 2.5 | 2.0 | 2.0 |
| Portability / implementation freedom | **5.0** | 5.0 | 5.0 | 4.5 | 4.0 | 4.5 |
| Runtime / resource cost | **4.5** | 5.0 | 4.5 | 3.0 | 3.0 | 4.0 |
| Failure / operability | **5.0** | 1.0 | 2.5 | 2.0 | 1.5 | 3.0 |
| Reversibility / migration cost | **4.5** | 2.0 | 3.0 | 3.5 | 1.5 | 3.5 |
| Evidence maturity / implementation risk | **5.0** | 3.0 | 4.0 | 4.0 | 1.5 | 4.0 |

## Future-scenario stress

### Stable IDs or aliases later

D091 does not freeze path strings as eternal identity. A future schema may adopt
stable CaseIds or explicit aliases. The durable invariant remains that an exact
reference must resolve in the complete associated TestPlan.

### Dynamic test discovery later

If Protos intentionally adopts an eventually consistent discovery architecture in
which CaseSpecs legitimately appear after resource metadata is published, a
future schema generation may select staged/dynamic references. D077 v1 remains
strict and deterministic.

### Remote/cached corpus materialization

The manifest and requirements document may later come from packages, CAS or
remote storage. Once the complete logical TestPlan and v1 requirement declarations
are materialized, the same join invariant applies before distribution.

## Regret scenario and escape path

The plausible regret scenario is a future Test Tool where the complete CaseSpec
set is no longer known before scheduling and cases can legitimately appear after
metadata publication.

The escape path is explicit rather than weakening v1:

1. retain D077/D091 v1 strict semantics;
2. define a new discovery/completeness boundary;
3. introduce a versioned later persisted schema with staged/dynamic reference
   semantics if needed; and
4. keep old documents deterministic.

## Strongest argument against A′

Strict validation makes refactors noisier: a large rename requires manifest and
resource metadata to remain coherent in the same publishable state, and a stale
branch cannot execute merely the unaffected filtered subset.

That cost is accepted because allowing execution from a globally inconsistent
corpus would create invocation-dependent validity and can silently remove resource
protection. Editing tools may still present incremental diagnostics or quick fixes
without weakening executable validity.

## Implementation consequence

D091 releases the bounded `TOOL002-I6D` join implementation. I6D may:

- build an index over the complete CaseSpecs;
- validate every I6C declaration against that complete index;
- attach requirements through the existing I5 CaseSpec reconstruction path; and
- fail closed before selection/scheduling when any declaration is orphaned.

I6D must not select a physical requirements filename, filesystem discovery,
catalog source/CLI spelling, scope vocabulary, provider API or another deferred
D077/D087 contract.

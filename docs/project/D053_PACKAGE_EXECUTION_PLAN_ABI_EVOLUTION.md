# D053 — PackageExecutionPlan ABI evolution and external-package representation

Status: **NEEDS_USER_DECISION**
Allocated: **2026-09-09**
Nature: implementation-independent Package Tool ABI / compatibility decision
Triggered by: `TOOL001-F2E3`
Blocks: `TOOL001-F2E3`
Specification revision: **UNCHANGED while unresolved**

## Decision boundary

`TOOL001-F2D1` deliberately froze `PackageExecutionPlanV1` as an exact,
workspace-only inert-data ABI. `TOOL001-F2E3` must extend normal execution to
already-selected, already-materialized and ContentIdentity-verified immutable
registry/Git nodes.

That exposes a durable compatibility question which F2E3 must not settle as a
mechanical implementation detail:

> How does PackageExecutionPlan evolve from the already-published exact
> workspace-only generation-1 shape to a mixed workspace/external graph without
> retroactively changing what generation 1 means or leaking host/store authority
> into inert execution-plan data?

D053 owns that question. Until D053 is ratified, F2E3 must not publish an
extended plan shape, reinterpret generation 1, add a parallel external-plan
sidecar, or introduce host capability/handle identity into the plan.

## Already-fixed constraints

D053 does not reopen these published constraints:

- `PackageExecutionPlanV1` generation 1 is an exact workspace-only shape.
- external source cannot enter executable planning merely because a path,
  locator, URL, revision, PackageId or cache entry was found;
- external material must first pass the closed F2E2 exact selected-root,
  same-capture ContentIdentity verification/custody gate;
- registry immutable identity preserves at least PackageId, exact
  ReleaseVersion and ContentIdentity;
- Git immutable identity preserves at least PackageId, exact revision and
  ContentIdentity;
- dependency aliases, registry/package locators, retrieval/fetch URLs,
  authority endpoints, mirror/cache/store paths and credentials are not
  immutable package/module identity;
- execution-plan data remains inert and contains no Filesystem, File, Process,
  Closure, resolver, network/store authority or mutable cursor;
- normal execution performs no version solving, implicit fetch or lock mutation;
- F2E4 owns defensive host detach plus canonical external ModuleKey/source
  resolver construction;
- F2E5 owns public `protos run` integration.

## Questions D053 must decide

### 1. Generation evolution

Choose whether the external-capable plan:

- extends generation 1;
- introduces a new generation;
- composes generation 1 with another separately versioned structure; or
- uses another compatibility mechanism.

The answer must define what an old generation-1 consumer may assume forever.

### 2. Package/ref representation

Choose the smallest inert representation that can distinguish:

- mutable workspace package instances;
- immutable registry package instances;
- immutable Git package instances;

while preserving the already-fixed identity distinctions above.

### 3. ContentIdentity placement

Decide where method/algorithm/digest identity belongs in the plan and how it
relates to the registry/Git exact release/revision identity, without turning a
transport artifact, host path or custody handle into logical identity.

### 4. Dependency graph representation

Decide how mixed edges are represented and validated, including workspace to
external, external to external, and any permitted external to workspace relation,
without duplicating graph ownership or introducing a second reconciliation
universe.

### 5. Compatibility and future evolution

Define when a future change requires another generation and when acquisition,
store layout, mirror, transport, digest-algorithm support or other machinery may
evolve without changing the plan ABI.

## Candidate design families for review

No option is selected by allocation of D053.

### A — extend generation 1 in place

Keep `generation: 1` and enlarge accepted refs/package records.

Primary risk: a consumer that already accepts generation 1 no longer has a
stable meaning for that generation.

### B — new mixed-capable generation

Freeze generation 1 permanently as workspace-only and introduce a new generation
whose exact shape natively represents workspace/registry/Git package nodes and
mixed dependency edges.

Primary cost: explicit multi-generation compatibility logic.

### C — generation-1 workspace plan plus external sidecar

Keep V1 unchanged and attach a separately versioned external structure.

Primary risk: two coordinated graph authorities with cross-structure
referential-integrity and lifetime requirements.

### D — host handles/capability tokens in the plan

Keep logical data smaller by referring to host-owned verified external state.

Primary risk: authority/lifetime/implementation identity leaks into what is
currently an inert portable planning boundary.

These are starting families, not a claim that the comparison is complete.

## Required evaluation

Before ratification, compare the meaningful alternatives against:

- current Protos package identity/versioning and lock-format decisions;
- compatibility with the exact F2D1 generation-1 contract;
- independent implementation and serialization clarity;
- large dependency graphs;
- multiple versions of one PackageId in the same graph;
- two aliases reaching the same exact immutable package;
- conflicting content for the same PackageId/version;
- Git repository/registry/mirror/cache relocation;
- ContentIdentity algorithm evolution;
- store eviction or physical-layout replacement after planning;
- process/Actor isolation and authority leakage;
- memory/copy cost of large plans;
- deterministic validation/failure locality;
- future package-source/acquisition mechanisms; and
- the Protos principles of ordinary data, explicit distinctions, no pets,
  minimal shared state, pay-only-for-what-you-use and scale by composition.

## Counterexamples the selected design must survive

At minimum, D053 must explain these cases:

1. a workspace root depends on registry package P@1 and P@2 simultaneously;
2. two dependency aliases reach the exact same P@1 + ContentIdentity;
3. two authorities claim the same PackageId/version with different
   ContentIdentity;
4. a registry locator or mirror changes while the exact locked instance does not;
5. a Git repository moves while PackageId/revision/content stay the same;
6. the physical package-store path changes after verification;
7. an old consumer that implements only generation 1 receives a newer plan;
8. a graph contains enough nodes that representation or validation has
   non-linear coordination/memory behavior; and
9. a future acquisition mechanism supplies the same immutable package identity
   without deserving a new runtime/module identity.

## Ratification gate

D053 remains `NEEDS_USER_DECISION` until the project owner explicitly approves
one concrete contract after the comparison, scalability review and attempted
falsification.

Ratification must state:

- the exact generation/compatibility rule;
- the exact inert package/ref/content/dependency shape or structural invariants;
- what generation 1 means permanently;
- what does and does not require a future generation;
- intentionally deferred choices; and
- the resulting unblock condition for `TOOL001-F2E3`.

Allocation of D053 changes no normative Protos specification, implementation
version, executable code, lock-format bytes or PackageExecutionPlan ABI.
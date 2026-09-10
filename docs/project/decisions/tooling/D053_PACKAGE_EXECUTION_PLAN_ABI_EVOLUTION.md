# D053 — PackageExecutionPlan ABI evolution and external-package representation

Status: **RATIFIED**
Allocated: **2026-09-09**
Explicit project-owner approval: **2026-09-09**
Nature: implementation-independent Package Tool ABI / compatibility decision
Triggered by: `TOOL001-F2E3`
Blocks: `TOOL001-F2E3`
Specification revision: **UNCHANGED** — Package Tool ABI/compatibility decision; no Core language semantic change

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

## Ratified decision — generation 2 single mixed graph (B2)

D053 ratifies a new `PackageExecutionPlan` **generation 2** for the
external-capable execution-plan ABI.

Generation 1 is semantically frozen permanently as the exact workspace-only ABI
already published by F2D1. A future implementation is not required to support
generation 1 forever, but no implementation may reinterpret `generation: 1` to
mean a different shape or set of invariants.

Generation 2 is one ordinary inert dependency graph:

```text
PackageExecutionPlanV2 {
    generation: 2
    root: workspace-node-ref

    packages: [
        workspace-package {
            ref: workspace-node-ref
            location
            exports
        }

        external-package {
            ref:
                registry(PackageId, exact ReleaseVersion)
                | git(PackageId, exact revision)
            content: ContentIdentity(method, algorithm, hex)
            exports
        }
    ]

    dependencies: [
        {
            declaring: node-ref
            alias
            target: node-ref
        }
    ]
}
```

The conceptual forms above freeze the semantic domains, not a second lock
grammar. F2E3 should reuse the already-published ordinary lock/ref value model
where mechanically appropriate rather than inventing parallel spellings. Local
implementation representation remains free only where it cannot change the
generation-2 ABI semantics fixed here.

### Node identity and uniqueness

A workspace ref is the existing workspace `PackageId` identity within the
selected workspace execution context.

A registry ref is `PackageId + exact ReleaseVersion`. A Git ref is
`PackageId + exact revision`.

`ContentIdentity` is mandatory exactly once on every immutable external package
node. The complete immutable external package instance is therefore:

```text
registry-ref + ContentIdentity
git-ref      + ContentIdentity
```

Dependency edges carry compact node refs and do not repeat ContentIdentity.

Generation-2 package uniqueness is by exact typed node ref, **not by PackageId
alone**. Multiple exact versions/revisions of one PackageId may coexist in one
graph. Conversely, one exact registry `(PackageId, ReleaseVersion)` or one exact
Git `(PackageId, revision)` cannot appear with conflicting ContentIdentity.

Dependency alias remains an edge-local lookup relation and is never package or
module identity.

### One graph, not a sidecar

Generation 2 has exactly one `packages` relation and one `dependencies` relation
for workspace and external nodes. D053 rejects a second external dependency
sidecar graph.

The structural edge form is uniform:

```text
declaring node-ref + alias -> target node-ref
```

D053 does not authorize dependency relations that the manifest/lock/resolution
model does not already permit. It only ensures that any already-valid exact
mixed graph can be represented without changing graph universes.

### Authority and provenance exclusion

Generation 2 remains ordinary inert data. It contains no:

```text
Filesystem / File / Process / Closure
resolver
host handle or capability token
verified-custody object
absolute external source/store/cache path
registry endpoint, CDN or mirror
Git fetch URL
PackageLocator
ArtifactDigest
credentials or proxy state
network/store authority
mutable cursor or package-manager object
```

Registry locator/authority and Git fetch information remain
resolution/acquisition provenance. F2E2 verified custody remains host authority.
F2E4 mechanically associates exact package identity with the already-verified
custody without inserting that authority into PackageExecutionPlan.

### Root boundary

Generation 2 keeps the current resolution root as a workspace ref. D053 does not
generalize execution to a registry/Git package as resolution root.

Generation 2 is nevertheless structurally capable of a workspace-only graph.
D053 does not require current workspace-only producers to migrate from V1: they
may continue to emit V1. A future producer may choose V2 for workspace-only input
without changing V2 semantics.

## Compatibility and future-generation rule

`generation` identifies the complete structural/semantic ABI contract, not the
Package Tool implementation version.

A new generation is required when a change would invalidate assumptions that a
consumer of the current generation is entitled to make, including at least:

- changing mandatory plan/package/ref/dependency structure;
- changing the semantic identity carried by a ref;
- introducing a new semantic node-ref family with distinct identity rules;
- changing dependency-edge meaning;
- changing the root semantic category; or
- introducing authority/capability semantics into the plan boundary.

A new generation is **not** required merely for:

- a registry endpoint, mirror, CDN or PackageLocator change;
- a Git retrieval-locator move that preserves exact package identity;
- package-store or cache layout/replacement;
- local versus shared/distributed verified custody;
- archive/transport representation changes;
- ArtifactDigest additions outside logical package identity;
- a new acquisition mechanism that yields an existing exact immutable identity;
- host/runtime/backend changes; or
- a newly supported ContentIdentity method/hash algorithm represented inside the
  already-versioned `method + algorithm + hex` domain.

An unsupported plan generation fails closed. D053 does not adopt a tolerant
"approximately compatible" reader rule.

## Scalability result

The selected model admits direct indexing by typed node ref and validation over
one package pass plus one dependency pass. For `P` package nodes and `E`
dependency edges, the graph representation and ordinary indexed validation are
`O(P + E)` rather than requiring cross-sidecar reconciliation.

ContentIdentity is stored once per immutable external node rather than repeated
on incoming/outgoing edges. Multiple aliases targeting the same exact external
package therefore add edges, not duplicate package instances or content digests.

Numeric/local surrogate node IDs may be used as an internal detached-host
optimization if ever justified, but they are not part of the generation-2 ABI.

## Cross-ecosystem review result

The decision was stress-tested against mature package/build ecosystems,
including Cargo, npm/pnpm, Go modules, Maven/Gradle, NuGet, SwiftPM, Python
lockfile tooling, Bazel and Nix.

The selected design deliberately follows the durable patterns that survived that
comparison:

- explicit format/generation evolution instead of retroactively redefining an
  old generation;
- exact resolved graph data rather than performing fresh solving during normal
  execution;
- node-local integrity rather than repeating full content identity on every
  dependency edge;
- multiple exact versions of one logical package when the graph requires them;
- content identity separated from retrieval URL, mirror and physical store path;
- fail-closed handling of unsupported structural generations.

It deliberately rejects ecosystem patterns that conflict with existing Protos
design: physical install path as logical identity, source URL as PackageId,
major-version path rewriting as durable package identity, duplicated compatibility
graphs, tolerant interpretation of unknown generations, and content hash as the
only package-lineage identity.

## Why this is the Protos choice

B2 preserves one small universe: packages are nodes and dependencies are edges at
both small and large scale. It makes semantic distinctions visible without
creating separate graph institutions for external packages.

It satisfies the project principles:

- **ordinary things remain ordinary** — the plan is inert ordinary data;
- **general rules beat special cases** — one graph and one edge relation;
- **pay only for what you use** — V1 workspace-only execution need not acquire
  external verification/store machinery;
- **scale by composition, not by changing universes** — larger/mixed graphs use
  the same node/edge model;
- **generality must be earned** — no premature numeric IDs, feature-bit
  negotiation, generic source-kind universe or external-root execution;
- **minimize shared mutable state / prefer independence** — verified custody is
  kept out of the logical graph and no sidecar graph requires coordinated
  mutation/reconciliation;
- **keep platform differences at the boundary** — no JVM/Path/Filesystem/host
  token enters the ABI.

## Rejected alternatives

### A — extend generation 1 in place

Rejected because F2D1 and the production adapter already define generation 1 as
an exact workspace-only shape. Reinterpreting it would break the meaning of a
published generation identifier.

### B1 — generation 2 with full ContentIdentity repeated in refs/edges

Rejected because it adds no semantic information and amplifies representation,
copy and comparison cost with edge count. ContentIdentity belongs once to the
external package node.

### C — V1 workspace graph plus external sidecar graph

Rejected because one dependency graph would have two structural authorities,
cross-graph referential integrity and permanent coordination complexity.

### D — host handles/capability tokens in the plan

Rejected because a token whose meaning lives in host mutable state leaks
authority/lifetime/implementation identity across an intentionally inert
boundary.

### E — ContentIdentity as the sole external package identity

Rejected because content verification is not a replacement for PackageId
lineage plus exact release/revision identity.

### F — numeric node IDs as ABI identity

Rejected as an unearned representation optimization. A host may intern refs
internally without exposing that machinery as durable ABI.

## Intentionally deferred

D053 does not decide:

- physical package-store or distributed-CAS layout;
- F2E4's concrete custody-to-resolver host data structure;
- host interning/hash-map implementation details;
- acquisition/fetch protocol or credentials;
- registry trust/authentication protocol;
- package publication;
- executable registry/Git resolution roots;
- future new source kinds whose identity semantics differ from workspace,
  registry and Git;
- conditional/platform package-resolution semantics not already selected
  elsewhere; or
- when a future producer should stop emitting generation 1 for workspace-only
  projects.

## Candidate design families for review

Allocation selected no option. After cross-ecosystem, scalability, compatibility and Protos-design review, the project owner explicitly ratified the B2 family defined below.

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

## Ratification closure

The project owner explicitly approved the B2 decision on 2026-09-09 after the
cross-ecosystem comparison, future/scalability review and Protos-design
evaluation.

D053 is therefore `RATIFIED`.

Ratification itself changes no Core normative Protos specification, executable
implementation, Maven implementation version, lock-format bytes or license
terms. It establishes the durable PackageExecutionPlan compatibility contract
that `TOOL001-F2E3` may now implement.

Result:

```text
D053           RATIFIED
TOOL001-F2E3   READY
TOOL001-F2E4   BLOCKED_BY_DEPENDENCIES: TOOL001-F2E3
TOOL001-F2E5   BLOCKED_BY_DEPENDENCIES: TOOL001-F2E4
```
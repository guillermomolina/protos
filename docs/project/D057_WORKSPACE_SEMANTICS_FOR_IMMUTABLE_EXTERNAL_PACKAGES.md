# D057 — Workspace semantics for immutable external packages

Status: **RATIFIED**
Allocated: **2026-09-09**
Explicit project-owner approval: **2026-09-09**
GitHub Issue: **#237**
Triggered by: `TOOL001-F2E3B` / GitHub #236
Parent work: `TOOL001-F2E3` / GitHub #91
Nature: implementation-independent Package Tool / package-model decision
Core specification revision: **UNCHANGED**
Selected policy: **A-strict/refined — workspace is mutable-root/development semantics; immutable package nodes do not activate it**

## Decision boundary

ManifestV1 structurally permits an optional `[workspace]` table whose
`workspace.members` groups the manifest package with additional packages during
workspace development.

`TOOL001-F2E3B` consumes ManifestV1 from an already-selected,
ContentIdentity-verified immutable registry/Git package. Before D057 there was no
durable rule for whether such an external manifest:

- is rejected when it declares `[workspace]`;
- silently ignores the workspace;
- expands members into additional package nodes;
- treats the immutable source as a package bundle; or
- is projected/flattened during publication.

F2E3B must not make that choice as implementation convenience. D057 closes the
current package-model boundary.

## Ratified rule

Under the current Protos package model, one immutable external package node
represents exactly one package identity.

Therefore:

```text
mutable resolution-root / workspace package
    [workspace]
        -> permitted under workspace policy

immutable registry package instance
    [workspace]
        -> not consumable; fail closed

immutable exact-Git package instance
    [workspace]
        -> not consumable; fail closed
```

The rule applies to the **presence of `[workspace]` itself**, including
`members = []`.

An empty workspace declaration is not treated as a special vacuous exception.
The manifest requested workspace semantics in a context whose current immutable
package representation intentionally has no such meaning.

The failure occurs during immutable package-model/preflight validation before
application execution authority is granted.

This decision does not change ManifestV1 structural parsing. `[workspace]`
remains a valid ManifestV1 shape for mutable workspace/development use.

## Package identity invariant

D057 preserves:

```text
Repository != Package
Workspace  != Package
Source     != Package

Package = Package
```

For the current immutable model:

```text
registry node:
    PackageId
    exact ReleaseVersion
    ContentIdentity

Git node:
    PackageId
    exact revision
    ContentIdentity
```

The source repository layout, development workspace topology and consumer
workspace do not alter that package node's meaning.

In particular, one exact external package identity cannot cause new PackageIds
to appear merely because its captured `protos.toml` contains
`workspace.members`.

## Why `[workspace]` is not silently ignored

`[workspace]` is not inert commentary. In ManifestV1 it declares package
topology.

Silently accepting:

```text
[workspace]
members = ["a", "b"]
```

while constructing only the manifest package would make the same field active in
one package context and semantically discarded in another without an explicit
projection boundary.

Protos instead fails where the current representation cannot honor the requested
semantic relation.

## Registry and Git use the same package rule

D057 does not grant exact-Git packages a special implicit multi-package rule.

A Git repository may, in the future, contain many Protos packages. That is a
source-container concern, not evidence that one Git PackageNode should expand
its `[workspace]`.

The current rule remains:

```text
one selected immutable Git package node
    =
one PackageId + exact revision + ContentIdentity
```

Registry and Git therefore share the same immutable package semantics.

## Explicit future multi-package source evolution

D057 deliberately leaves open a future source-acquisition model in which one
immutable repository/archive/source contains multiple packages.

The preferred architectural direction, if real use cases justify it, is
**explicit package selection inside an immutable source**, conceptually:

```text
ImmutableSource
    source identity / exact revision / content identity
        |
        +-- explicit package root/subroot A -> PackageId A
        +-- explicit package root/subroot B -> PackageId B
        +-- explicit package root/subroot C -> PackageId C
```

Each selected package then becomes an ordinary exact PackageNode.

Such a future design must explicitly own at least:

- source-container identity;
- canonical package root/subroot selection;
- PackageId uniqueness;
- whether ContentIdentity is source-wide, package-subtree-specific or both;
- release/revision relation for each package;
- custody sharing between packages selected from one source;
- lock representation;
- ModuleKey consequences; and
- package-level cache/invalidation behavior.

It MUST NOT be introduced by silently interpreting current ManifestV1
`workspace.members` during immutable consumption.

This leaves room for Cargo/Yarn-style package selection in Git monorepos and
Nix-style explicit subroots without making workspace metadata itself immutable
package identity.

## Explicit future publication projection

D057 also leaves open a future `protos publish` contract that may transform a
development workspace into one or more immutable package payloads.

A future publication operation may conceptually perform:

```text
development source workspace
        |
        +-- publish Package A -> immutable Package A payload
        +-- publish Package B -> immutable Package B payload
        +-- publish Package C -> immutable Package C payload
```

Because `protos.toml` participates in ContentIdentity, any publication
projection that removes or rewrites workspace-only metadata deliberately creates
the published logical tree whose ContentIdentity is then computed.

D057 does not select that projection, its CLI, artifact layout, manifest rewrite
rules or compatibility policy.

## Prior-art audit

The owner-approved audit reviewed workspaces, multi-project builds,
package publication and remote multi-package source behavior across a broad set
of ecosystems:

- Rust/Cargo workspaces and Git dependencies;
- npm, Yarn and pnpm workspaces/publication;
- Go modules and `go.work`;
- Python packaging and uv workspaces;
- Ruby/Bundler/RubyGems monorepos;
- Dart/pub workspaces;
- Elixir/Mix umbrellas and Hex publication;
- Erlang/Rebar3 development layouts;
- Haskell Cabal multi-package projects;
- Maven reactor and Gradle multi-project/composite builds;
- .NET solutions/projects and NuGet packaging;
- Swift Package Manager local/remote packages;
- Bazel/Bzlmod modules and root-owned overrides;
- OCaml/opam local pinning;
- Conan and vcpkg development/overlay models; and
- Nix flakes/subflakes as the strongest explicit immutable multi-root
  counterexample.

The dominant portable model separates:

```text
development aggregation/workspace
        from
published/consumed package identity
```

Representative mature behaviors include:

- package-level publication from a workspace;
- development workspace/path relations rewritten or excluded from the
  distributed package representation;
- root-owned local overrides rather than transitive dependency-owned workspace
  authority;
- remote monorepos selecting a specific package/workspace/subroot rather than
  implicitly expanding all members; and
- explicit source/subroot/hash identity when a system intentionally supports
  immutable multi-root sources.

Cargo is particularly relevant: a repository may contain multiple crates and a
Git dependency can select the required package from that source, while packaged
crate metadata does not carry active workspace semantics into consumers. This
demonstrates that rejecting active `[workspace]` on the current immutable
PackageNode does not forbid future Git-monorepo support.

Nix demonstrates the complementary future case: multiple immutable roots can be
safe when subroot selection is explicit and the source is locked/content
identified. That supports a future focused source-container design, not implicit
ManifestV1 workspace expansion.

## Scalability

D057 keeps runtime/preflight graph structure unchanged:

```text
packagesByRef: Map<NodeRef, PackageNode>
edges: Map<(declaringRef, alias), targetRef>
```

For `P` packages and `E` dependency edges, graph validation/construction remains:

```text
time   O(P + E)
memory O(P + E)
```

Normal execution does not need:

- workspace-member discovery inside transitive packages;
- recursive package-root search;
- package creation from physical subdirectories;
- workspace path reconciliation;
- nested-member ownership tables;
- per-worker monorepo layout knowledge; or
- package-store/source paths in PackageExecutionPlan.

A future explicit source-container layer can deduplicate one repository/archive
acquisition while independently verifying/selecting package subroots. That
preserves package-level cache granularity and allows package verification and
execution to parallelize across workers without changing PackageExecutionPlan.

The rule therefore remains suitable for:

- large monorepos;
- package-level caches;
- shared CAS;
- offline execution;
- containers;
- distributed workers;
- remote build/execution; and
- future parallel package acquisition/verification.

## Why this is the most Protos option

The selected rule follows the project philosophy directly:

- **small universe** — one PackageNode remains one package;
- **mechanisms over institutions** — future multi-package sources can use a
  general explicit source + package-selector mechanism rather than turning
  workspace into a runtime institution;
- **ordinary things remain ordinary** — workspace is development aggregation;
  package identity remains package identity;
- **semantic distinctions remain visible** — repository, source, workspace and
  package are not collapsed;
- **general rules beat special cases** — no Git-only expansion and no
  empty-workspace exception;
- **fail where the invariant is violated** — immutable PackageNode +
  `[workspace]` fails at package-model validation;
- **pay only for what is used** — ordinary external packages pay no nested
  workspace discovery/custody machinery;
- **generality must be earned** — source bundles/subroots and publication
  projection remain future focused decisions;
- **independence over coordination** — the producer's development topology does
  not govern the consumer; and
- **scale by composition** — future monorepo packages still become ordinary
  independently identified PackageNodes.

## Rejected alternatives

### Ignore `[workspace]` during immutable consumption

Rejected.

The field declares package topology and is not decorative metadata. Silently
discarding it would create context-dependent field meaning without an explicit
projection boundary.

### Implicitly expand an immutable package's workspace members

Rejected for the current model.

That would invent package-subroot identity, PackageId allocation/validation,
ContentIdentity ownership and custody semantics inside F2E3B.

### Git-only implicit workspace expansion

Rejected.

The fact that Git sources naturally contain repositories does not justify giving
Git PackageNodes different package cardinality semantics from registry nodes.
Future Git monorepo support should select package roots before ordinary
PackageNode construction.

### Members become independently locked nodes automatically

Not selected.

This can be a valid future design only after defining how a source container maps
explicit package roots to exact independently identified nodes.

### Publication-time workspace flattening

Not selected by D057.

It is a strong future direction supported by mature ecosystems, but it requires a
focused package-publication contract because the published manifest/tree and
ContentIdentity may differ deliberately from the development workspace source.

## TOOL001-F2E3B consequence

D057 ratification releases F2E3B.

F2E3B may now mechanically read each verified external ManifestV1 from the same
F2E2 captured Filesystem and:

- fail closed if `manifest.workspace` is non-null;
- apply D056 and fail closed on external `path` dependencies;
- validate manifest PackageId against the exact locked registry/Git ref;
- validate registry manifest ReleaseVersion against the locked release;
- treat Git manifest package version as metadata rather than Git node identity;
- validate/copy exports;
- reconcile registry/Git dependency declarations against exact root-owned lock
  targets; and
- emit uniform inert generation-2 dependency edges.

F2E3B remains implementation work and must still pass focused/full validation
before closure. Parent F2E3 remains open for the later custody/composition slice.

# D056 — Path dependency semantics for immutable external packages

Status: **RATIFIED**
Allocated: **2026-09-09**
Explicit project-owner approval: **2026-09-09**
GitHub Issue: **#233**
Nature: implementation-independent Package Tool / package-model decision
Triggered by: `TOOL001-F2E3`
Releases: remaining `TOOL001-F2E3` external-manifest/dependency validation
Core specification revision: **UNCHANGED**
Selected policy: **A+ — workspace-only path semantics + future root-owned override space**

## Decision boundary

`TOOL001-F2E3-V2-LEAF-PLAN` published the first D053 generation-2
execution-plan constructor while deliberately failing closed on dependency edges
declared by registry/Git packages.

The next F2E3 work can read each immutable external package's exact
`protos.toml` from the same F2E2 ContentIdentity-verified captured Filesystem.
Manifest schema v1 structurally admits `path` as an explicit local-development
dependency source. Before D056 it deliberately did not decide whether such a
declaration remains meaningful when the manifest belongs to an immutable
registry/Git package consumed under another root-owned exact graph.

D056 closes that semantic boundary.

## Ratified rule

A `path` dependency is exclusively a mutable workspace/local-development
relation.

Conceptually:

```text
mutable workspace package
    path dependency
        -> permitted under the existing workspace/path policy

immutable registry package instance
    path dependency
        -> not consumable; fail closed

immutable exact-Git package instance
    path dependency
        -> not consumable; fail closed
```

Normal execution of an immutable registry/Git package MUST NOT interpret,
resolve, canonicalize, follow, substitute or otherwise operationalize that
package's `path` declaration.

The failure occurs at package-model/preflight validation before application
authority is granted.

This rule does not make `path` syntactically invalid in ManifestV1. The same
ManifestV1 shape remains useful while a package is mutable workspace/development
state. The distinction is semantic and depends on whether the package instance
is being consumed as mutable workspace state or as an immutable external
registry/Git node.

## Identity consequence

For an immutable package instance:

```text
same immutable package identity
    =>
same dependency meaning across consumers
```

A registry instance remains identified by:

```text
PackageId
exact ReleaseVersion
ContentIdentity
```

and an exact-Git instance by:

```text
PackageId
exact revision
ContentIdentity
```

No consumer-local path, checkout location, workspace layout, cache/store path or
ambient Filesystem becomes part of that meaning.

In particular, D056 rejects a model in which one identical external
`PackageId + release/revision + ContentIdentity` can resolve `../x` to different
packages in different consumer workspaces.

## Root-owned future substitution space

D056 deliberately does **not** ratify an override, patch, vendor or workspace-
substitution mechanism.

It preserves space for a future explicit resolution-root-owned mechanism with
the following architectural direction:

```text
ordinary immutable dependency relation
        |
        | explicit root-owned future substitution
        v
exact target selected into the root-owned graph
```

If such a feature is later designed:

- it must be declared/owned by the resolution root rather than inherited as
  transitive authority from an immutable dependency;
- it must become part of the root's semantic resolution inputs when applicable;
- the resulting exact target must be recorded through ordinary graph identity;
- normal execution must follow the exact resulting lock graph rather than a
  physical path;
- the override must not alter the immutable dependency's own published meaning;
- PackageExecutionPlan remains pathless and authority-free.

No spelling, override matching rule, precedence, publication behavior or lock
migration is selected by D056.

## Future immutable multi-package/bundle space

D056 also does **not** prohibit a future model in which one immutable source
bundle contains multiple explicitly identified packages.

Prior art such as Nix demonstrates that path-like source composition can be
hermetic when the referenced source becomes an explicitly locked/content-
identified graph input rather than ambient filesystem lookup.

Protos does not currently have the required semantics for:

- one immutable source bundle containing multiple PackageIds;
- package sub-root identity inside such a bundle;
- whether ContentIdentity is per bundle, per package subtree or both;
- independent release/version identity for nested packages;
- cross-package edge identity inside one immutable source;
- module-key consequences for those package roots.

A future demonstrated need may allocate a focused decision for such a source
model. It MUST NOT be introduced by silently reinterpreting ManifestV1 `path`.

## Rejected alternatives

### B — implicit intra-capture path packages

Rejected for D056.

It could be made reproducible, but only by first designing nested/bundled package
identity. Reusing current `path` would silently decide several unowned identity
questions.

### C — external path resolves against consumer workspace

Rejected.

It would make one immutable package acquire consumer-dependent meaning, leak
consumer-local authority into a transitive dependency and undermine
reproducibility, remote execution and distributed/CAS execution.

### D — automatic publication-time manifest rewriting

Not selected.

Cargo, Yarn and pnpm demonstrate useful development-to-published projection, but
in Protos `protos.toml` participates in ContentIdentity. Rewriting `path` into a
registry/Git declaration therefore creates a distinct published logical tree and
needs an explicit future package-publication contract. D056 does not invent that
contract.

### E — keep external path but silently substitute through root lock

Rejected as implicit behavior.

A future explicit root-owned substitution may be valid, but the external
package's `path` spelling is not itself portable authority or immutable
dependency identity. Root substitution must be designed as its own semantic
input rather than inferred from that transitive path.

## Prior-art audit summary

The owner-approved review compared package/dependency behavior across mature
ecosystems including:

- Rust/Cargo;
- npm, Yarn and pnpm;
- Go modules;
- Bazel/Bzlmod;
- Gradle;
- Python packaging, uv and Poetry-style source overrides;
- Ruby/Bundler/RubyGems;
- Dart/pub;
- Elixir/Mix/Hex and Erlang/Rebar3;
- PHP/Composer;
- Haskell/Cabal/Stack;
- OCaml/opam;
- C++ Conan and vcpkg;
- .NET/NuGet;
- Swift Package Manager; and
- Nix.

The dominant reusable pattern is separation between local-development wiring and
portable/transitive package meaning.

Different ecosystems realize it through one or more of:

1. reject local/path dependencies for publication/portable consumption;
2. transform workspace/local relations into ordinary versioned dependencies as
   an explicit publication operation;
3. keep local/editable/override policy under the root consumer rather than
   allowing dependency-owned overrides to propagate transitively; or
4. when paths really are immutable inputs, turn them into explicit locked,
   content-identified source nodes rather than ambient filesystem references.

D056 selects the smallest rule compatible with all four lessons without
prematurely adding publication projection, overrides or bundle identity.

## Scalability and distributed-execution result

The selected policy preserves the D053 execution graph shape:

```text
packagesByRef: Map<NodeRef, PackageNode>
dependency edge: (declaringRef, alias) -> targetRef
```

Validation/execution remains `O(P + E)` in package/edge count with no required
per-edge physical-path lookup.

The runtime does not need:

- one filesystem namespace per transitive path dependency;
- path search/reconciliation;
- consumer-workspace traversal on behalf of immutable dependencies;
- nested-path ownership tables;
- store-layout identity;
- path-based remote-worker coordination.

The same graph therefore remains suitable for local stores, shared CAS,
distributed workers, containers, offline execution and future remote execution.

A future root-owned override mechanism can still resolve to the same exact graph
before normal execution; it need not alter PackageExecutionPlan or runtime
complexity.

## Why this is the most Protos option

The selected A+ rule fits the project's design philosophy:

- **small universe** — no implicit second package universe for transitive paths;
- **ordinary things ordinary** — a local path remains local development state,
  while an immutable package dependency is represented by package identity;
- **semantic distinctions visible** — mutable workspace and immutable external
  consumption remain observably different package contexts;
- **no pets** — no privileged meaning for a path merely because it appeared in
  a published/transitive manifest;
- **fail where the invariant is violated** — immutable external + operational
  path fails at package validation;
- **pay only for what is used** — users not using local overrides pay no
  transitive path-resolution machinery;
- **generality must be earned** — bundle identity, publish projection and
  override policy remain deferred until a real use case requires them;
- **independence over coordination** — a dependency cannot reach into or derive
  meaning from its consumer's ambient workspace;
- **scale by composition** — execution remains ordinary exact PackageNodes plus
  exact dependency edges.

## TOOL001-F2E3 consequence

D056 ratification releases the remaining F2E3 work.

F2E3 may now mechanically read each verified external package's ManifestV1 from
the same F2E2 capture and:

- reject `path` dependencies for registry/Git package nodes;
- validate registry/Git dependency declarations against the root-owned exact lock
  graph;
- preserve exports and exact typed dependency edges in generation 2;
- continue to exclude paths, provenance and authority from PackageExecutionPlan.

The already-published external-leaf fail-closed behavior remains valid evidence
but is no longer the final semantic frontier.

F2E3 remains `IN_PROGRESS` until the external-manifest/dependency construction
and its applicable validation are published.

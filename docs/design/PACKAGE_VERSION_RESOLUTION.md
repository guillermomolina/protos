# Protos Package Version Resolution Audit

Status: exploratory architecture; non-normative
Related distribution design: `docs/design/PACKAGE_DISTRIBUTION.md`
Related manifest audit: `docs/design/PACKAGE_MANIFEST_FORMAT.md`
Related identity/version audit: `docs/design/PACKAGE_IDENTITY_VERSIONING.md`

This document performs the focused dependency-constraint and version-resolution
audit left open by the package identity/versioning work. It defines an initial
exploratory resolution policy without implementing package tooling, changing Core
module semantics, reserving a `LIBxxx` work item, or choosing the physical
`protos.lock` serialization.

The key observation is that Protos does **not** need to begin with a flat
environment in which exactly one version of every package must satisfy every
consumer. Dependency imports are contextual (`dep:<alias>/...`), package-backed
`ModuleKey` identity includes the exact resolved package instance, and the package
design already permits multiple versions to coexist. That lets the initial
resolver solve version pressure structurally instead of manufacturing a global
single-version constraint.

## TOOL001 implementation checkpoint

`TOOL001-D` is CLOSED. D1 implements strict ReleaseVersion parsing/precedence and
D2A-D2D implement dependency constraint v1, including explicit same-core
prerelease admission.

`TOOL001-E` is also CLOSED as a deliberately pure/local version-selection layer.
E1 chooses the highest satisfying ReleaseVersion from already-known candidates.
E2 implements the version-level retained-selection rule: preserve a previously
selected exact ReleaseVersion when that exact value remains present and satisfies
D2; otherwise fall back to E1. This realizes the version-preference ordering from
this audit without pretending that an in-memory retained ReleaseVersion is a
parsed or validated physical lock edge.

Complete candidate eligibility and physical resolution remain unimplemented:
release discovery, PackageId/authority matching, yank/trust/content identity,
language compatibility, transitive dependency closure, `protos.lock`
parsing/serialization, update operations and graph diagnostics are not made
current policy merely by closing E.

## Design goals

The initial resolver should:

- preserve repository-controlled reproducibility through `protos.lock`;
- never make normal execution silently change package selection;
- prefer recent compatible releases when a dependency is intentionally resolved
  or updated;
- allow incompatible versions to coexist when different dependency edges require
  them;
- deduplicate only when edges independently resolve to the same exact package
  instance;
- avoid search-order, filesystem, cache, mirror, or traversal-order semantics;
- keep prerelease adoption explicit;
- make `0.x` behavior explicit instead of hand-waving around SemVer instability;
- reject unnecessarily broad/ambiguous range syntax initially;
- leave room for a stronger global solver later if future package concepts such
  as peer/singleton constraints genuinely require one;
- explain resolution failure through dependency paths rather than an opaque
  "version conflict" message.

## Do not start with a global one-version-per-package solver

Many package managers need a global solver because their execution/link model
requires or strongly prefers one selected version for a package across a large
portion of the graph. Cargo, for example, actively tries to unify SemVer-
compatible crate versions and can fail when compatible versions are constrained
to different exact releases. PubGrub is designed to find a globally consistent
assignment under a set of package constraints and is excellent when that is the
problem being solved.

That is not yet a Protos requirement.

For initial Protos packages, each dependency declaration creates an explicit edge
owned by the declaring package:

```text
Package A
    alias x -> Package P constraint C1

Package B
    alias x -> Package P constraint C2
```

The two `x` aliases are unrelated local names. The resolver may choose different
versions of `P` for those edges when their constraints or update histories differ.
Both versions have distinct package-backed module identity and can coexist.

Therefore initial resolution uses the following rule:

> Resolve each dependency edge in the context of the exact package release that
> declares it. Do not impose a hidden graph-wide singleton version merely because
> two edges name the same `PackageId`.

This removes a large class of dependency-lock conflicts. A future feature that
requires shared/global package identity must justify that stronger coordination
explicitly rather than making every package pay for it now.

## Exact-node canonicalization, not speculative deduplication

Allowing multiple versions does not mean loading duplicate copies of the same
exact package instance.

If two dependency edges independently resolve to the same tuple:

```text
PackageId
exact ReleaseVersion or VCS revision
ContentIdentity
```

then they name the same resolved package node in the resolution root. Their
module imports therefore produce the same canonical package-backed `ModuleKey`
for the same logical module, independent of dependency alias or graph path.

But the resolver should not deliberately select an older version merely to reduce
the number of distinct versions in the graph.

Example:

```text
A -> P >=1.0.0 <1.5.0
B -> P >=1.2.0 <2.0.0
```

If available releases include `1.4.9` and `1.9.0`, initial fresh resolution may
select:

```text
A -> P 1.4.9
B -> P 1.9.0
```

even though `1.4.9` could satisfy both edges. This is intentional. Selecting an
older common release is a different dependency-selection policy, not a harmless
storage optimization. Exact-equal nodes collapse; different selected releases do
not.

This keeps candidate selection local, deterministic, and explainable. Later
resolver generations may add an explicitly versioned optimization policy if real
measurements justify it.

## Version constraint language v1

`ReleaseVersion` remains the strict SemVer-derived value selected by
`PACKAGE_IDENTITY_VERSIONING.md`. A dependency constraint is a separate value
whose syntax defines a set of acceptable `ReleaseVersion`s.

The first constraint language should deliberately be smaller than npm's full
range language.

### Exact requirement

A bare full version means exactly that version:

```text
1.4.2
```

means:

```text
=1.4.2
```

The manifest never interprets a bare full version as an implicit caret range.
If compatibility movement is intended, it is written explicitly.

### Compatible caret requirement

Caret syntax is the concise normal compatibility range:

```text
^1.4.2
```

For `MAJOR > 0`:

```text
^1.4.2  ==  >=1.4.2 <2.0.0
```

For zero-major releases, compatibility narrows around the left-most non-zero
component:

```text
^0.4.2  ==  >=0.4.2 <0.5.0
^0.0.7  ==  >=0.0.7 <0.0.8
^0.0.0  ==  0.0.0
```

This follows the common Cargo/npm interpretation while keeping the rule explicit
in Protos rather than treating `0.x` as ordinary stable-major compatibility.

A future `protos add` command may choose to write a caret requirement by default
when the user asks for the current stable release without supplying a constraint.
That CLI convenience does not change the manifest syntax: the stored `^` remains
explicit.

### Explicit bounded interval

When caret compatibility is not the desired contract, v1 supports a whitespace-
joined conjunction of primitive comparisons over fully specified versions:

```text
>=1.4.2 <1.8.0
>1.4.2 <=1.7.9
```

Supported comparison operators are:

```text
>
>=
<
<=
```

An explicit interval must contain both a lower and an upper bound. Open-ended
requirements such as:

```text
>=1.0.0
<5.0.0
```

are rejected initially because they silently claim compatibility across an
unbounded future or past surface. A package author can widen the bound in a new
release when that compatibility has actually been established.

### Syntax deliberately not included initially

Constraint v1 rejects:

```text
~1.4.2
1.4
1.x
*
1.2.3 - 2.0.0
1.2.3 || 2.0.0
!=1.4.7
latest
stable
main
```

The omissions are intentional:

- tilde is only shorthand for an interval already expressible explicitly;
- partial and wildcard versions create extra implicit expansion rules;
- union/disjoint ranges make the value model and diagnostics more complex;
- mutable labels are selectors, not immutable release constraints;
- exclusion holes can initially be handled by raising/lowering a bounded range or
  by a registry yank when the bad release is owned by that package.

If real package use cases repeatedly require one of these forms, a later
constraint-language generation can add syntax as deterministic sugar or a new
set operation without changing `ReleaseVersion` identity.

## Prerelease eligibility

Stable dependency requirements do not silently select prereleases.

For example:

```text
^1.4.2
>=1.4.2 <2.0.0
```

do not select:

```text
1.5.0-beta.1
2.0.0-rc.1
```

A prerelease becomes eligible only when the constraint explicitly names a
prerelease for the same `MAJOR.MINOR.PATCH` tuple, following the useful intent
boundary established by npm/node-semver.

For example:

```text
^2.0.0-rc.1
```

may select later prereleases of `2.0.0`, the final `2.0.0`, and later stable
versions inside the caret compatibility range, but it does not imply opting into
prereleases of unrelated future tuples such as `2.1.0-beta.1`.

A package that wants only the release-candidate train before the final release can
use an explicit bounded interval such as:

```text
>=2.0.0-rc.1 <2.0.0
```

The exact interval/prerelease set rules must be covered by conformance tests when
implemented; parser-library defaults do not define them.

## Candidate eligibility

Before version preference is considered, a candidate release must be eligible.
At minimum an immutable registry candidate is eligible only when:

1. its `PackageId` matches the dependency relation resolved by the authority;
2. its `ReleaseVersion` satisfies the dependency constraint;
3. prerelease policy admits it;
4. it is not yanked for a fresh selection;
5. its package metadata/content identity are internally valid and trusted under
   the selected authority policy;
6. its declared Protos language/package compatibility is acceptable under the
   project's selected compatibility contract;
7. its transitive dependency closure can itself be resolved.

The exact language/toolchain compatibility model remains a separate audit. The
important resolver rule is that a release known to be incompatible is not chosen
as a fallback merely because it has the highest version number.

## Fresh-selection preference

When no retained lock selection applies, choose candidates in descending SemVer
precedence, subject to eligibility.

Conceptually:

```text
highest eligible stable release first
    ↓ if prereleases were explicitly admitted and outrank/fit appropriately
eligible prerelease candidates according to SemVer precedence
    ↓
next lower eligible candidate on transitive failure
```

The highest version is therefore a preference, not permission to ignore an
unsatisfied transitive closure. If the highest candidate's own dependencies
cannot resolve, the resolver may try the next candidate allowed by the same edge.
If no candidate yields a resolvable closure, that edge fails with a dependency-
path explanation.

This differs from Go Minimal Version Selection. MVS provides valuable high-
fidelity behavior by choosing the maximum of recorded minimum requirements and
not automatically floating to newly published versions. Protos already obtains
that stability from a committed exact lockfile and explicit-update policy, so the
fresh resolver can prefer the highest eligible release without making normal
execution non-reproducible.

## Lock-preserving resolution

An existing valid `protos.lock` has higher preference than newly available
versions.

`protos resolve` is a reconciliation operation, not an update operation.

For every existing locked dependency edge, if its exact target:

- still satisfies the current manifest constraint;
- still has matching package/release/content identity;
- remains valid under required authority/integrity rules; and
- is not invalidated by an explicit manifest/source change;

then `resolve` preserves it even when a newer eligible release exists.

When a manifest change makes a locked edge ineligible, only that edge and the
portion of the graph that genuinely depends on its changed selection need to be
re-resolved. Unaffected locked nodes remain preferred.

Removing a dependency prunes nodes that become unreachable; it does not upgrade
remaining dependencies as a side effect.

This keeps Git diffs small and makes the cause of a lock change attributable to
an explicit manifest or dependency-management operation.

## Update semantics

`update` deliberately removes lock preference from selected dependency edges.

### Targeted update

A targeted update of a root dependency alias should:

1. retain the manifest constraint unless the user explicitly asks to change it;
2. ignore the previously locked exact version for that selected edge;
3. choose the highest currently eligible release under the constraint;
4. resolve the new candidate's transitive dependency edges, preserving already-
   locked exact nodes when they remain valid for those edges where possible;
5. leave graph regions unrelated to the updated dependency locked;
6. prune nodes no longer reachable after the update.

Because multiple versions may coexist, updating one dependency does not require
upgrading a shared package everywhere merely to maintain a graph-global singleton.
If another branch still needs the old release, both can remain locked.

### Full update

A full update intentionally removes version lock preference throughout the
resolution root and fresh-resolves all registry version edges to their highest
eligible candidates under the current manifests and authority state.

The precise CLI spelling (`protos update`, `protos update <alias>`, `--all`, etc.)
is not frozen by this audit; the semantic distinction between targeted and full
unlocking is.

## Yanked releases

Yanking affects future selection, not immutable identity.

Initial policy:

```text
fresh resolve/update
    does not select a yanked release

existing valid lock
    may continue using/fetching its exact yanked release
```

A yank therefore does not silently rewrite a committed lockfile during
`run`, `test`, or `build`.

Explicitly forcing a new dependency onto a yanked version, if ever supported,
requires an explicit exceptional CLI mechanism and is not part of normal
constraint resolution.

Security/advisory tooling may warn that a locked version is yanked or vulnerable,
but warning policy is separate from silently changing executable dependency
selection.

## Normal execution remains resolution-free

The following operations use the exact already-locked graph:

```text
protos run
protos test
protos build
```

They may materialize/fetch exact locked artifacts before Protos code runs, but
they do not select a different version and do not rewrite `protos.lock`.

If the resolution inputs and lock disagree, or if no valid lock exists for a
package that requires dependencies, normal execution fails with a package-state
error directing the user to an explicit resolution operation.

This makes "locked" behavior the default rather than a special CI mode.

## Resolution-root and graph semantics

Exactly one lock graph is authoritative for a resolution root.

For a single-package checkout, the package root is normally the resolution root.
For a workspace/monorepo, the workspace root normally owns one `protos.lock` for
all member packages participating in that resolution.

Dependency-development lockfiles embedded in published library artifacts are not
consulted. When a library is consumed under another resolution root, its manifest
constraints participate in the consumer graph and the consumer's root lock owns
the exact selections.

This keeps resolution control at the root while still allowing every library's
own repository to commit a development lockfile for reproducible CI/tests when
that library itself is the root.

## Lockfile consistency without hashing formatting

Normal execution needs a reliable way to detect whether `protos.toml` resolution
inputs still correspond to `protos.lock`.

Do not hash the raw TOML bytes for this purpose. Comments, whitespace, or an
innocent formatter change must not invalidate an otherwise identical dependency
graph.

Instead the lockfile should carry a versioned digest of the canonical **semantic
resolution inputs** derived from the parsed manifest/workspace model.

Conceptually:

```text
resolution-input-method = protos-resolution-input-v1
resolution-input-digest = <algorithm>:<digest>
```

The canonical input includes only fields that can affect resolution, such as:

- package/workspace identity relevant to resolution;
- dependency aliases, `PackageId`/locator/source requirements, and constraints;
- dependency visibility if/when it affects resolver policy;
- language/package compatibility declarations;
- explicit workspace/path replacement relations;
- any future resolver-affecting target/build dimension.

It excludes:

- comments and TOML formatting;
- descriptions/readme metadata;
- local cache paths;
- credentials;
- mirror/proxy settings;
- timestamps;
- unrelated tool/editor configuration.

This lets the toolchain reject a genuinely stale lock without causing lock churn
for non-semantic manifest edits.

## Resolver generation belongs in the lock

The lockfile must record the resolver-policy generation used to create its graph,
for example conceptually:

```text
lock-format = 1
resolver-version = 1
```

Normal execution of an already-valid lock need not re-run that resolver; it
follows exact recorded graph edges.

A newer toolchain must not silently reinterpret an old lock graph under different
selection rules. When re-resolution/update is requested, the toolchain may either
support the recorded resolver generation or require an explicit migration to a
new one with a clear diff.

This reserves evolution space for future policies such as true global peer
constraints, feature dimensions, public-identity coordination, or different
candidate preferences without pretending those rules never change.

## Determinism requirements

For the same:

```text
resolution inputs
existing lock preference state
available authority metadata
resolver-version
```

resolution must produce the same graph independent of:

- filesystem traversal order;
- dependency declaration order where semantics are otherwise equivalent;
- hash-map iteration order;
- cache hit order;
- mirror/CDN endpoint;
- network response completion order;
- thread scheduling.

Release candidate ordering uses SemVer precedence plus a stable canonical
tie-break only where distinct metadata records would otherwise be indistinguish-
able. Under the release-immutability rules, two different contents cannot legally
share the same `(PackageId, ReleaseVersion)`.

Because v1 resolution is edge-contextual rather than graph-global-singleton,
traversal order must not change which version an unrelated edge selects.

## Failure diagnostics

A failed edge should explain the chain that made it impossible, for example:

```text
app depends on parser ^2.1.0
parser 2.4.0 depends on text >=3.0.0 <4.0.0
no eligible text release in that interval supports language contract 0.1

parser 2.3.2 was also considered
parser 2.3.2 depends on text >=2.5.0 <3.0.0
all matching releases are yanked

therefore app -> parser ^2.1.0 cannot be resolved
```

The implementation may eventually use PubGrub, another backtracking engine, or a
simpler purpose-built resolver internally. Human-readable dependency derivation
is the requirement; adopting a specific algorithm is not.

## Public dependency identity and multiple versions

Multiple versions can expose identity-bearing Protos objects/prototypes across
package APIs. The identity/version audit already identifies this as an important
API-design hazard.

Resolver v1 does **not** hide the issue by forcing one global version. Instead:

- different selected releases remain honestly distinct package/module identities;
- `visibility = "public"` metadata may support warnings/diagnostics once its exact
  contract is designed;
- a future stronger compatibility rule may require coordinated versions for a
  particular public dependency relation;
- such a rule would be an explicit resolver-generation/package-model extension,
  not retroactive evidence that all dependencies must be singletons.

This follows Protos' preference for visible semantic distinctions over hidden
coordination.

## Features, optional dependencies, peers, and platform conditions remain out

Initial resolver v1 does not add:

- Cargo-style feature unification;
- npm-style peer dependencies;
- optional dependency activation expressions;
- arbitrary OS/architecture/environment conditional dependency expressions;
- global singleton/package-instance constraints;
- dynamic dependency generation.

Each of those creates a new dimension of graph identity or coordination. They
should be introduced only after a real package use case and a focused audit show
that ordinary modules/packages cannot express the need cleanly.

## Prior-art conclusions

### Cargo

Cargo strongly validates two choices adopted here: prefer the highest available
eligible version during fresh selection, and give an existing lockfile higher
priority than newly published versions. It also demonstrates the complexity
introduced by trying to unify compatible versions globally. Cargo can fail when
two exact requirements within one SemVer-compatible line cannot be unified, and
can also retain duplicate versions in some broader-range cases.

Protos adopts highest-candidate and lock-preference ideas but rejects graph-wide
SemVer-compatible unification as an initial invariant. Exact node equality is
deduplicated; different versions may coexist.

### Go Minimal Version Selection

Go MVS provides reproducibility and predictable upgrades by selecting the highest
of versions explicitly required as minima rather than floating to every newly
published version. This is elegant in Go's module model.

Protos does not need MVS as its primary freshness-control mechanism because the
root lock is committed and normal execution never re-resolves. When explicit
fresh resolution/update occurs, selecting the highest eligible release better
matches the user's intentional request to reconsider versions.

### npm/node-semver

npm's semver implementation provides useful precedent for caret behavior below
`1.0.0` and for excluding prereleases from ordinary ranges unless prerelease use
was explicitly requested.

Protos deliberately takes only those small rules, not npm's full range language.
Wildcards, partial versions, tilde shorthand, disjunctions, and loose coercion
are not needed in constraint v1.

### Swift Package Manager

SwiftPM validates explicit semantic-version requirements and a root
`Package.resolved` record whose exact versions do not pin downstream consumers of
a library. It also separates resolving from updating conceptually, although many
SwiftPM commands may invoke resolution automatically.

Protos keeps the root-lock ownership idea but makes ordinary run/test/build
stricter: they never change version selection.

### PubGrub

PubGrub is a strong precedent for backtracking version solving with excellent
human-readable conflict explanations. It is especially valuable when many
constraints must participate in one globally consistent version assignment.

Initial Protos resolution intentionally avoids requiring one global version per
`PackageId`, reducing that constraint problem substantially. PubGrub or a related
solver remains an implementation option if future resolver generations add peer,
singleton, feature, or other genuinely global constraints. The package model
should not adopt those constraints merely to justify a sophisticated solver.

## Selected exploratory policy

```text
RELEASE VALUE
    strict SemVer-derived ReleaseVersion from PACKAGE_IDENTITY_VERSIONING.md

CONSTRAINT V1
    1.2.3                 exact
    ^1.2.3                SemVer-compatible interval
    ^0.2.3                >=0.2.3 <0.3.0
    ^0.0.3                >=0.0.3 <0.0.4
    >=1.4.0 <2.0.0        explicit bounded interval

NOT IN V1
    ~ shorthand
    partial versions
    wildcards
    OR/disjoint ranges
    exclusions
    mutable labels
    open-ended intervals

PRERELEASES
    excluded unless explicitly opted into by prerelease constraint

FRESH RESOLUTION
    highest eligible release first
    backtrack to lower eligible release when its transitive closure fails

MULTIPLE VERSIONS
    allowed naturally on different dependency edges
    no graph-global PackageId singleton

DEDUPLICATION
    only exact same resolved package instance collapses
    do not select an older common release merely to reduce duplicates

RESOLVE
    preserve valid locked selections
    repair only stale/changed graph regions

UPDATE TARGET
    unlock selected edge; reconsider its affected closure
    preserve unrelated locked graph

UPDATE ALL
    fresh-resolve all version edges

RUN / TEST / BUILD
    exact lock only
    never rewrite dependency selection

YANK
    excluded from fresh selection
    existing exact lock remains usable

LOCK
    one authoritative graph per resolution root
    includes lock-format and resolver-version
    includes canonical semantic resolution-input digest
    excludes machine-local state
```

## Durability and future evolution

The following are intended to remain durable:

- normal execution does not mutate dependency selection;
- constraints and exact selected versions are different concepts;
- prerelease adoption is explicit;
- dependency edges are package-contextual;
- multiple package versions may coexist;
- exact identical resolved nodes canonicalize to one node;
- lock preference and update intent are distinct;
- resolver behavior is versioned;
- lock staleness is based on semantic inputs, not raw manifest formatting;
- traversal/cache/network order cannot affect the selected graph.

The following remain deliberately evolvable:

- exact CLI spellings for update/resolve operations;
- additional constraint syntax after demonstrated need;
- resolver implementation algorithm;
- diagnostics format;
- public-dependency coordination rules;
- optional/feature/peer/platform dimensions;
- physical lockfile encoding.

## Recommended next audit

Before package implementation begins, the next focused design should define the
**physical lockfile model and canonical graph serialization**:

- textual versus binary encoding;
- canonical node identifiers/order;
- `lock-format` evolution;
- canonical semantic resolution-input digest representation;
- exact representation of registry, VCS, and workspace nodes;
- readable/deterministic Git diffs;
- merge-conflict recovery and regeneration rules;
- which provenance fields are execution-required versus diagnostic-only.

After that, the package model will be sufficiently concrete to design a first
local/no-network implementation slice without committing to a public registry.

## Audit evidence

### AGENTS.md read

- `AGENTS.md`

### docs read

- `docs/design/PACKAGE_DISTRIBUTION.md`
- `docs/design/PACKAGE_MANIFEST_FORMAT.md`
- `docs/design/PACKAGE_IDENTITY_VERSIONING.md`

### spec read

- `spec/semantics/MODULES.md`

### implementation surface inspected

- `src/main/java/com/guillermomolina/protos/runtime/ProtosModuleKey.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosStandardLibraryModuleResolver.java`

### Implementation-relevant constraints derived

- Package resolution remains host/toolchain policy on the existing module
  resolver boundary; Core `import(String)` semantics do not need changing for
  this design.
- Package-backed module identity must come from exact resolved package identity,
  not dependency alias, cache path, or retrieval URL.
- The package resolver must remain deterministic and Actor module caching must see
  one canonical `ModuleKey` for one exact resolved package module.
- This audit changes documentation only and assigns no formal package/library
  implementation work item.

## Prior-art reference entry points

- Cargo resolver: https://doc.rust-lang.org/cargo/reference/resolver.html
- npm/node-semver: https://github.com/npm/node-semver
- Go modules/MVS: https://go.dev/ref/mod
- Go modules overview: https://go.dev/wiki/Modules
- SwiftPM dependency requirements: https://docs.swift.org/swiftpm/documentation/packagemanagerdocs/addingdependencies/
- SwiftPM resolution/update: https://github.com/swiftlang/swift-package-manager/blob/main/Sources/PackageManagerDocs/Documentation.docc/ResolvingPackageVersions.md
- Dart package versioning: https://dart.dev/tools/pub/versioning
- Dart PubGrub solver design: https://github.com/dart-lang/pub/blob/master/doc/solver.md

## TOOL001-F2B1 — canonical manifest resolution-input projection

`TOOL001-F2B1` freezes the semantic inclusion boundary for one already-parsed
manifest participating in a resolution root. It deliberately does **not** choose
workspace filesystem membership, assemble the complete resolution root, hash
bytes, or compare a lock digest. Those steps remain later F2B work.

The purpose of this layer is to prevent stale detection from regressing into a
hash of spelling or formatting. A resolution-input implementation consumes
semantic values owned by their existing policy layers. It does not hash raw TOML
substrings merely because a later semantic owner is missing.

### Per-manifest inclusion matrix

For manifest schema v1, the semantic projection has these owners:

| Manifest value | F2B1 treatment | Reason |
| --- | --- | --- |
| manifest generation | represented by the projection/resolver generation; not repeated as raw TOML | the schema generation is already an interpretation boundary |
| `package.id` | include the parsed opaque String | package identity participates in the resolution root |
| `package.version` | include the canonical D1 `ReleaseVersion` value | root/workspace package version is mutable resolution metadata even though workspace lock refs omit a ReleaseVersion |
| `package.locator` | exclude | local package locator does not select dependency targets |
| `compatibility.language` | include semantically, but construction remains fail-closed until its canonical compatibility owner is frozen | compatibility affects candidate eligibility |
| `exports` | exclude | exports affect module visibility after package selection, not dependency selection |
| `workspace.members` | include the declared membership relation, but complete root assembly remains fail-closed until workspace/path policy interprets and validates those declarations | membership changes which package manifests participate in the root |
| dependency alias | include | the declaring package's alias identifies the dependency edge |
| registry dependency authority/package/constraint | include, with the constraint normalized through D2 semantics | all three affect target eligibility |
| Git dependency repository/revision | include as semantic source requirements; exact-revision policy remains the VCS/source owner | changing either changes the selected immutable source requirement |
| path dependency | include semantically, but construction remains fail-closed until path/workspace policy owns canonical path identity | path interpretation is resolution policy, not schema syntax |

Comments, whitespace, TOML table ordering, quote style, descriptions, export
entries, local cache paths, credentials, mirror/proxy configuration, editor
configuration and timestamps never enter this projection merely because they
appear beside resolver-affecting data.

### Dependency-constraint normalization

The schema-v1 model currently retains the dependency `version` field as String,
while closed D2 owns its meaning. F2B1 therefore forbids hashing that source
String directly.

Canonical constraint projection is derived from
`DependencyConstraint.parse(...)`:

- `exact` projects the canonical parsed ReleaseVersion;
- `caret` projects its canonical lower ReleaseVersion and the `caret` kind;
- `interval` projects the semantic lower comparison first and upper comparison
  second, including each inclusive/exclusive bit and canonical ReleaseVersion,
  independent of source comparison order or tab/space separator spelling.

The original D2 `.text` field is diagnostic/source-preservation data and is not
the semantic digest input.

This means, for example, that accepted interval spellings which D2 parses to the
same lower/upper model do not become different solely because the user reordered
the comparisons or used a different accepted separator.

### Scalar encoding and ordering

When F2B later materializes canonical bytes:

- scalar Strings use the already-frozen F1B1 canonical `qstring` encoding;
- dependency entries are ordered by canonical alias-qstring UTF-8 bytes;
- workspace member declarations are ordered by their canonical declaration
  qstring bytes before workspace policy maps them to participating package
  manifests;
- no iteration order from `Map`, filesystem traversal, manifest source order, or
  resolver traversal may affect the byte stream.

These ordering rules define deterministic projection, not workspace path
semantics.

### Fail closed on unresolved semantic owners

F2B1 establishes a strict rule for later stale detection:

> If a manifest value affects resolution but its canonical semantic owner has not
> been defined, `protos-resolution-input-v1` construction must fail closed. It
> must not substitute raw source text, host path spelling, cache identity, or
> another incidental representation.

At this checkpoint the unresolved owners materially affecting complete
resolution-root input are:

- workspace member path interpretation and validated membership;
- canonical path-dependency identity/relation;
- the language/package compatibility value model;
- complete root/member assembly across the participating manifests.

Consequently F2B1 does **not** make stale detection executable yet.

### F2B continuation boundary

`TOOL001-F2B2` owns the next focused design step: resolution-root assembly,
including workspace membership/path identity and the compatibility value needed
to construct all participating manifest projections without raw-source
fallbacks.

Only after that boundary closes may a later F2B slice choose/consume the hashing
capability, emit the `protos-resolution-input-v1 <algorithm>:<digest>` value and
compare it with `protos.lock`.

## TOOL001-F2B2 — resolution-root/workspace semantic assembly

`TOOL001-F2B2` closes the semantic assembly boundary that F2B1 deliberately left
open. It defines how one root manifest and its explicit local member packages
become one deterministic resolution root before any digest is computed.

This is package-tool policy. It reuses the normative `Path` value model but does
not redefine Core filesystem or Path semantics.

### One active workspace owner per resolution root

The manifest at the resolution root is the only manifest whose
`workspace.members` table expands that resolution root.

A workspace member's own `[workspace]`, when present, is **not recursively
expanded** while that package is participating as a member of an enclosing root.
That declaration remains meaningful if the member package is later used as an
independent resolution root.

This role-sensitive rule is required by lock-format 1: `workspace-member` records
map root-declared member strings directly to workspace PackageIds and carry no
second declaring-workspace reference. Nested workspace expansion would therefore
create graph state that the frozen v1 lock grammar cannot identify.

Accordingly:

- root projection includes the root manifest's active workspace relation;
- member projections do not include their own inactive nested workspace relation;
- no parent-directory walk, sibling scan, glob, manifest search path, or
  dependency lockfile contributes members.

### Canonical workspace-member path

Each root `workspace.members` String is interpreted as one portable,
root-relative package path.

The v1 manifest spelling uses literal `/` as the component separator. It is valid
only when:

- the complete String is non-empty;
- it is not rooted and does not begin with `/`;
- splitting on `/` produces no empty component;
- no component is `.` or `..`.

Each resulting normal component is the exact decoded String value and maps to
one normative `Path.child(component)` step from `Path.relative()`.

There is no case folding, Unicode normalization, host separator rewriting,
drive/UNC interpretation, `realpath`, environment expansion, home expansion, or
filesystem search. Backslash, colon and other characters inside a component have
only the ordinary component-String meaning supplied by `Path`; a host that
cannot represent/access such a path fails at the filesystem boundary rather
than changing package identity.

Because member declarations admit only normal components, the source String is
already the canonical semantic member-path spelling. Leading/trailing slash,
double slash, `.` and `..` variants are rejected rather than normalized to an
alternate spelling.

The physical member manifest is exactly:

```text
<resolution-root>/<member-path>/protos.toml
```

under the resolution-root Filesystem authority.

### Root/member validation

Resolution-root assembly parses the root manifest first and then exactly the
explicit member manifests.

It must reject:

- a member relation that denotes the root package;
- duplicate canonical member paths;
- duplicate PackageIds across root and members;
- a member path that cannot be read as one package root with a valid
  schema-v1 `protos.toml`;
- a member read that escapes the resolution-root Filesystem confinement.

The member's `package.id` is the workspace-node identity recorded by
`workspace-member`. Its package version remains mutable manifest input and is
included by F2B1, but the F1B workspace node reference remains PackageId-only.

Member `protos.lock` files are never consulted while assembling the enclosing
root. One resolution root has one authoritative lock graph.

### Path-dependency resolution

A schema-v1 path dependency is a local relation from its declaring participating
package to another package in the **same assembled resolution root**. It is not
permission to search arbitrary directories.

Its String uses `/` separators and is interpreted relative to the declaring
package directory. For this relation only, `.` and `..` components are accepted
as navigation syntax:

- `.` contributes no movement;
- `..` removes one already-established root-relative normal component;
- a `..` that would move above the resolution root is invalid;
- every other non-empty component contributes one exact normal component.

Leading `/` and empty components remain invalid.

After lexical resolution against the declaring package's canonical root-relative
location, the resulting normal-component path must equal exactly one of:

- the empty root location; or
- one explicit root workspace-member location.

No undeclared directory becomes a package merely because a path dependency
points at it.

The target manifest's PackageId is therefore already known from root assembly.
The semantic dependency projection records:

```text
source-kind = path
target-location = <canonical root-relative package location>
target-PackageId = <workspace PackageId>
```

and does not retain the original `.`/`..` spelling as resolution identity.
Equivalent path expressions resolving to the same declared target are equivalent
resolution input.

### Filesystem aliases do not redefine semantic identity

The canonical relation is the root-relative `Path` structure plus PackageId, not
a host absolute path, current working directory, inode number, drive spelling or
`realpath` result.

Filesystem confinement remains mandatory. A symlink or other host alias cannot
authorize escape outside the resolution root. If the backend cannot preserve the
required confinement while selecting a member/path target, assembly fails rather
than substituting host-canonical absolute path bytes into the semantic input.

Two declared locations that ultimately expose the same package lineage still
collide through the required unique PackageId rule; they are not silently
deduplicated by host file identity.

### Language compatibility value

F2B2 closes the initial semantic owner of schema-v1
`compatibility.language` without inventing SemVer/range behavior.

The value is an opaque exact **LanguageCompatibilityId**:

- its canonical value is the already-decoded non-empty String;
- no trimming, case folding, numeric parsing, SemVer expansion or range syntax
  applies;
- absent compatibility means the package declares no language-compatibility
  restriction;
- present compatibility requires exact equality with the resolution context's
  active LanguageCompatibilityId.

For the current Protos language generation the active compatibility identifier is:

```text
0.1
```

matching the normative language-version generation, not the Maven
implementation version, specification revision, Git commit or runtime vendor.

A future language-compatibility policy that needs ranges or multiple compatible
generations must define a new semantic contract explicitly rather than
reinterpreting existing schema-v1 Strings.

The active resolution-context LanguageCompatibilityId is itself part of the
semantic resolution input because changing it may change candidate eligibility.

### Deterministic assembled model

Conceptually, the complete semantic input before byte serialization is:

```text
ResolutionRootV1 {
    languageCompatibility: LanguageCompatibilityId

    root: ManifestProjection {
        role: root
        location: empty relative Path
        ...
        workspace: [
            { location: canonical member path, packageId: PackageId },
            ...
        ]
    }

    members: [
        {
            location: canonical member path
            packageId: PackageId
            manifest: ManifestProjection(role = member)
        },
        ...
    ]
}
```

Member entries are ordered by canonical member-path qstring UTF-8 bytes, with
PackageId qstring bytes as a defensive tie-break. Duplicate paths/PackageIds are
invalid before ordering.

Dependency aliases inside each manifest remain ordered under F2B1. Path
dependencies are projected only after the complete root/member mapping exists.

No filesystem traversal order, Map insertion order, manifest source order,
host path spelling or resolver traversal order contributes to this model.

### F2B1 role refinement

F2B1's inclusion matrix is role-sensitive for `workspace.members`: the field is
resolution input when the manifest is the active root, because it defines the
member set. The same field on a manifest consumed as an enclosing-root member is
inactive and excluded from that enclosing root's semantic projection.

This is a refinement of the F2B1 ownership rule, not nested-workspace support.

### F2B3 boundary

F2B2 makes the complete semantic resolution-root model well-defined without raw
source fallback. It does not select a cryptographic implementation.

`TOOL001-F2B3` may now define the canonical byte stream over this model and stale
comparison, but executable hashing remains dependency-gated until the repository
has an explicit suitable hashing capability/owner. Package Tool code must not
silently acquire host/JVM hashing authority merely to finish F2.

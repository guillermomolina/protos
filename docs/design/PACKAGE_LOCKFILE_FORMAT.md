# Protos Package Lockfile Format Audit

Status: exploratory architecture; non-normative

Related design:
- `docs/design/PACKAGE_DISTRIBUTION.md`
- `docs/design/PACKAGE_MANIFEST_FORMAT.md`
- `docs/design/PACKAGE_IDENTITY_VERSIONING.md`
- `docs/design/PACKAGE_VERSION_RESOLUTION.md`

This document performs the focused physical-format, canonical-serialization,
Git-workflow, stale-detection, and compatibility audit for `protos.lock`.

It does not implement package tooling, change Core language semantics, create a
`LIBxxx` work item, or select a registry protocol. It also does not decide the
future package-tool self-hosting architecture; that remains a separately queued
audit.

## Primary goals

`protos.lock` is machine-authored repository state. Its design should optimize
for:

- exact reproducibility;
- stable Git diffs;
- deterministic regeneration;
- one authoritative graph per resolution root;
- explicit format and resolver generations;
- exact identity/provenance information without machine-local state;
- safe evolution across future Protos toolchains;
- low merge-conflict amplification;
- human inspectability sufficient for code review;
- no requirement for hand editing.

The lockfile is not the human package manifest and should not inherit a format
merely because `protos.toml` uses TOML.

## Selected direction: canonical textual lockfile

Use a textual, UTF-8 lockfile with a deliberately narrow Protos-owned grammar.

The initial recommendation is **not** to encode `protos.lock` as general TOML,
JSON, YAML, or a binary serialization.

Instead define a small canonical machine format whose only purpose is the resolved
package graph.

Reasons:

- the lockfile is machine-authored, so comments and flexible authoring syntax add
  no value;
- canonical ordering and canonical whitespace can be mandatory rather than style;
- unknown/new fields can be controlled by `lock-format`;
- a purpose-built representation can produce compact, readable diffs without
  inheriting a large configuration data model;
- binary formats make ordinary source review and merge diagnostics unnecessarily
  opaque;
- JSON is serviceable, but punctuation-heavy and less pleasant for large graph
  diffs;
- TOML is appropriate for the human manifest but its flexible equivalent
  representations are not useful for a machine-owned canonical file.

This crosses from zero to one custom lock grammar, so the grammar must remain very
small and data-only. It must not become a second package-manifest language.

## Lock-format 1 header grammar — selected by TOOL001-F1A

`TOOL001-F1A` freezes the **header only**. Body record syntax remains a separate
design slice.

Every lock-format-1 document begins with exactly:

```text
lock-format <decimal>
resolver-version <decimal>
resolution-input <method-token> <algorithm-token>:<lowercase-hex>

```

The fourth line above is empty. Body records, when present, begin immediately
after that one empty line.

The header lexical grammar is:

```text
header =
    "lock-format" SP decimal LF
    "resolver-version" SP decimal LF
    "resolution-input" SP method-token SP digest LF
    LF

decimal =
    "0"
    | nonzero-digit *digit

method-token =
    lower-alpha *(lower-alpha | digit | "-")

algorithm-token =
    lower-alpha *(lower-alpha | digit | "-")

digest =
    algorithm-token ":" 1*lowercase-hex

SP = one ASCII space (0x20)
LF = one line-feed octet (0x0a)
```

Canonical header rules:

- the three keywords above are exact and case-sensitive;
- header lines appear exactly once and in the order shown;
- fields are separated by exactly one ASCII space;
- tabs, carriage returns, leading/trailing whitespace and blank lines inside the
  three header records are invalid;
- canonical decimals have no leading zero except the value `0`;
- method and algorithm tokens are non-empty lowercase ASCII kebab tokens;
- digest text uses lowercase hexadecimal only;
- header records never wrap;
- there is exactly one empty line between the third header record and the first
  body record;
- UTF-8 BOM remains forbidden by the existing canonical-text rule.

The current lock writer generation is expected to emit:

```text
lock-format 1
resolver-version 1
resolution-input protos-resolution-input-v1 sha256:<64-lowercase-hex>

```

F1A deliberately separates **grammar** from later support policy. The lexical
shape can represent other canonical decimal generations/tokens, while a reader
or operation may separately reject an unsupported `lock-format`,
`resolver-version`, resolution-input method, hash algorithm, or digest length.

F1A does not decide body punctuation/keywords, node-key serialization, scalar
escaping, line wrapping for body fields, diagnostic locator emission, artifact
transport digest emission, or source-kind-specific records.


## Illustrative canonical body shape

The F1A header punctuation above is exact. The body punctuation below remains
illustrative pending F1B, while its target data shape is approximately:

```text
lock-format 1
resolver-version 1
resolution-input protos-resolution-input-v1 sha256:<digest>

root pkg:<PackageId>

package pkg:<PackageId>@1.4.2
  locator community/parser
  authority registry:<AuthorityId>
  content protos-package-tree-v1 sha256:<digest>
  dependency json -> pkg:<OtherPackageId>@2.3.1
  dependency util -> git:<PackageId>@<revision>

package pkg:<OtherPackageId>@2.3.1
  locator community/json
  authority registry:<AuthorityId>
  content protos-package-tree-v1 sha256:<digest>

package git:<PackageId>@<revision>
  package-id <PackageId>
  fetch https://example.invalid/repo.git
  revision <full-revision>
  content protos-package-tree-v1 sha256:<digest>
```

This example is illustrative only. The durable decisions are the semantic fields,
canonical ordering, explicit source kinds, and stable references.

## Header

Every lockfile begins with explicit format metadata:

```text
lock-format 1
resolver-version 1
resolution-input <method> <algorithm>:<digest>
```

### `lock-format`

`lock-format` owns the physical serialization and field interpretation.

A toolchain must not silently reinterpret a lockfile using a different format
generation.

If a toolchain cannot read a newer format, it fails clearly and leaves the file
untouched.

A toolchain may read an older supported format without rewriting it during
`run`, `test`, or `build`.

Format upgrading is an explicit dependency-management/tooling operation, never an
incidental effect of normal execution.

### `resolver-version`

`resolver-version` records the policy generation that produced the graph.

Normal execution consumes exact graph edges and does not need to rerun the
resolver merely because a newer resolver exists.

When an explicit `resolve` or `update` operation rewrites the graph under a newer
resolver generation, the resulting lock records that generation.

This keeps "how this graph was selected" distinct from "how this file is encoded."

### Resolution-input digest

The lock records the semantic resolution-input digest defined by
`PACKAGE_VERSION_RESOLUTION.md`.

It must not hash raw `protos.toml` bytes.

The digest covers canonical resolver-affecting manifest/workspace data and ignores
comments, formatting, readme text, mirrors, credentials, timestamps, and other
non-resolution state.

## Node identity

Do not number nodes by encounter order:

```text
package 1
package 2
package 3
```

Traversal-order IDs make unrelated graph changes renumber later nodes and create
large noisy diffs.

References should instead use a canonical stable node key derived from immutable
resolved identity.

Conceptually:

```text
registry node:
    package:<PackageId>@<ReleaseVersion>

VCS node:
    git:<PackageId>@<exact-revision>

workspace node:
    workspace:<PackageId>
```

A node key is a lockfile reference key, not the complete runtime `ModuleKey`.

For immutable registry releases, the lock additionally records
`ContentIdentity`; the existing invariant requires one `(PackageId,
ReleaseVersion)` to map to exactly one content identity in the graph.

If a future source kind needs more disambiguation, `lock-format` can define a
source-specific canonical key without introducing unstable ordinal IDs.

## Node ordering

Node blocks are serialized in a fixed total order.

Initial recommendation:

1. root/workspace metadata;
2. registry nodes ordered by canonical `PackageId`, then SemVer release;
3. VCS nodes ordered by `PackageId`, then exact revision bytes/text;
4. other future source kinds by source-kind token then their canonical identity.

Within every node:

- scalar fields appear in a fixed schema-defined order;
- dependency edges are sorted by dependency alias using bytewise ASCII order for
  the portable alias grammar;
- set-like collections use canonical sorted order;
- no map/hash iteration order may leak into serialization.

The same semantic graph must produce byte-for-byte identical lockfile content
across conforming implementations.

## Canonical text rules

Version 1 should define at least:

- UTF-8 without BOM;
- LF (`\n`) line endings;
- exactly one final newline;
- ASCII field keywords;
- no insignificant alternative whitespace forms;
- no tabs;
- no trailing whitespace;
- no comments;
- no timestamps;
- deterministic escaping for string-like diagnostic fields;
- lowercase canonical algorithm/source-kind tokens where applicable.

A formatter is unnecessary: the writer always emits canonical form.

A reader may either reject non-canonical v1 serialization or accept and require
explicit normalization on a dependency-management operation. Normal execution
must never silently rewrite it.

## What belongs in an immutable registry node

At minimum:

```text
PackageId
ReleaseVersion
AuthorityIdentity / source provenance
ContentIdentity:
    canonicalization method
    hash algorithm
    digest
dependency edges:
    local dependency alias -> exact locked node
```

A current human `PackageLocator` may be included for diagnostics and useful Git
diffs, but it is not node identity.

If the locator changes while `PackageId`, release, content, and edges do not, an
explicit metadata refresh may update only the diagnostic locator. Normal
execution need not rewrite the lock merely because a registry renamed the
package.

The lock must not contain a current mirror/CDN endpoint as release identity.

## VCS nodes

A VCS dependency records:

```text
PackageId
VCS kind
exact immutable revision
ContentIdentity
dependency edges
fetch/provenance locator when required to materialize on a cold machine
```

Branches, tags, `main`, `HEAD`, or similar mutable selectors never appear as the
exact locked revision.

A fetch URL may need to remain in the lock because a new machine needs a starting
point to obtain the exact commit. It is explicitly retrieval/provenance metadata,
not package identity.

Changing that locator is an explicit source metadata update and must not alter
`PackageId`, exact revision, or `ContentIdentity` when they are unchanged.

## Workspace/path nodes

Workspace members are mutable development sources and differ from immutable
external nodes.

The lock may record:

```text
workspace PackageId
workspace member identity/relation
dependency edges
```

It must not record:

- absolute filesystem paths;
- user home directories;
- current source-tree content digest as a lock-validity requirement;
- editor/container mount paths.

The workspace manifest supplies the portable relative membership/path relation.

Ordinary source edits inside a workspace member do not stale the dependency lock.
Build caches may separately hash current source trees.

## Root representation

Exactly one resolution root owns the lock graph.

For a single package, the root package can be represented directly.

For a workspace, root metadata identifies the workspace and member package IDs
participating in the graph.

The lock should not copy every descriptive manifest field. It records only data
needed to identify the graph, validate resolution inputs, materialize exact
dependencies, and reconstruct package dependency edges.

## Edges, not flattened install paths

The lock records dependency relationships:

```text
declaring node
    alias -> exact target node
```

It must not encode a `node_modules`-like physical installation tree or filesystem
placement.

The same exact target node may be referenced by multiple aliases/parents.

Different versions of one `PackageId` remain separate nodes when selected.

This matches Protos's contextual `dep:<alias>/...` model and keeps storage layout
independent from resolution semantics.

## No machine-local state

The authoritative lock graph excludes:

```text
cache directory
package-store path
absolute workspace path
username
home directory
host name
container path
proxy
credentials/token
selected mirror/CDN
download timestamp
last-used timestamp
temporary extraction directory
build output path
```

Such data belongs to machine/user tooling configuration or caches.

The same committed lock must function on developer machines, CI, offline vendored
environments, and corporate mirrors without semantic modification.

## Artifact digests

`ContentIdentity` is mandatory for immutable external nodes.

`ArtifactDigest` is transport metadata and is not required as node identity.

A future registry/cache descriptor may provide several concrete artifacts:

```text
zip -> sha256:A
tar.zst -> sha256:B
```

that all materialize to one:

```text
ContentIdentity -> sha256:C
```

If artifact digests are persisted in `protos.lock` for early download validation,
they must live in an explicitly non-identifying transport section and must not
make archive-format changes require dependency re-resolution.

The initial recommendation is to omit them from the core lock graph and let the
registry/store metadata own transport alternatives.

## Git policy

`protos.lock` is repository-controlled state.

Initial rule:

```text
protos.toml  -> commit
protos.lock  -> commit
```

`protos.lock` should not normally appear in `.gitignore`.

This applies both to applications and to libraries when those repositories are
the active development resolution root.

A published library artifact does not impose its development lock on consumers.
The consumer's resolution-root lock is authoritative.

Whether a deployment/application artifact embeds a lock-equivalent exact closure
is a separate packaging/deployment decision.

## Library publication

A library repository commits its development `protos.lock` so CI and developers
test the same exact graph.

The published library package exposes its manifest constraints and package
metadata, not a nested authoritative dependency lock that overrides the
consumer's resolution.

If the physical package archive happens to carry a copy of the author's
development lock for provenance/debugging in a future format, consumers must
treat it as non-authoritative metadata. The simpler initial recommendation is to
exclude it from published library source artifacts.

## Merge conflicts

Lockfiles are generated artifacts, not collaborative handwritten documents.

When two branches change package requirements:

1. merge/reconcile the human manifests/workspace metadata;
2. discard conflicting generated lock hunks as needed;
3. run the explicit Protos resolution command;
4. review the newly generated canonical `protos.lock`;
5. commit both manifest and lock result together.

Tooling should eventually provide a command that makes this workflow explicit,
for example conceptually:

```text
protos resolve
```

or a future dedicated merge helper.

Do not design the lock syntax around hand-merging dependency graph entries.

However canonical sorting and stable node keys should ensure that independent
changes in unrelated graph regions often merge cleanly with ordinary Git.

## Why stable node keys matter for Git

Bad format:

```text
node 17 = foo
node 18 = bar
node 19 = baz
```

Adding a node earlier can renumber everything.

Preferred:

```text
package:<foo-id>@1.2.3
package:<bar-id>@2.0.0
package:<baz-id>@4.1.7
```

Only added/removed/changed packages and their incoming edges produce diffs.

This is more important than minimizing raw file size.

## Stale-lock behavior

Normal execution checks:

```text
supported lock-format
supported exact graph/source kinds
resolution-input digest matches current semantic inputs
required locked identities/content are internally valid
```

If the semantic resolution inputs changed, execution fails with a package-state
error.

It does not run the resolver and does not rewrite the lock.

Changing only manifest comments/formatting does not stale the lock because the
resolution-input digest is semantic.

Editing source files in a workspace member does not stale the dependency lock
unless the edit also changes resolver-affecting manifest/workspace metadata.

## Missing lockfile

For a package/workspace with third-party or otherwise lock-requiring dependencies:

```text
run/test/build + no protos.lock
    -> error
```

The user runs an explicit resolution operation to create it.

For a trivial standalone package with no lock-requiring dependency graph, the
implementation may later decide whether an empty lock is required for uniformity.
That is a CLI/workflow detail, not a reason to make execution perform implicit
resolution.

## Old and new lock formats

A newer toolchain may support multiple old lock formats.

Rules:

- reading an old supported lock during normal execution does not upgrade it;
- explicit `resolve`/`update` may migrate it to the current writer format;
- migration must preserve exact graph identity unless the operation also
  intentionally changes selection;
- unsupported newer lock formats fail clearly;
- unsupported ancient formats fail clearly rather than guessing;
- no network lookup is performed merely to "fill in" missing old-format fields
  during normal execution.

This deliberately differs from npm's historical behavior of opportunistically
augmenting old lockfiles from `node_modules` or the registry.

## Lock format versus resolver version

Keep these independent.

Example:

```text
lock-format 2
resolver-version 1
```

can mean a more compact serialization of the exact same resolver semantics.

Conversely:

```text
lock-format 2
resolver-version 3
```

can record a graph selected under newer policy.

This separation lets Protos improve Git ergonomics or serialization without
pretending dependency policy changed.

Cargo's history is useful here: its lock format has had multiple generations,
including changes explicitly aimed at reducing merge conflicts. npm likewise
records an integer `lockfileVersion`. Protos should preserve the useful explicit
version boundary while making normal execution stricter about not rewriting
repository state.

## Determinism contract

Given:

```text
same semantic resolution inputs
same resolver generation
same available authoritative package metadata snapshot/state
same explicit operation (resolve/update scope)
same retained valid lock preference where applicable
```

a conforming resolver/writer must produce the same semantic graph and canonical
lock bytes.

Registry metadata availability can legitimately change the result of an explicit
fresh/update resolution. Once committed, ordinary execution follows the exact
lock and does not consult "newer available" state to alter selection.

## Human review

Although machine-owned, the file should remain understandable enough that a code
review can answer:

- which package changed version;
- which package was added/removed;
- which dependency edge changed;
- whether provenance/source kind changed;
- whether exact VCS revision changed;
- whether content identity changed unexpectedly.

A change from:

```text
version 1.4.2
content ...AAA
```

to:

```text
version 1.4.3
content ...BBB
```

should be visually local.

Diagnostic human locators help review, but stable opaque `PackageId` remains the
identity anchor.

## Security consequences

The lockfile is security-sensitive repository state.

A code review should treat changes to:

```text
PackageId
AuthorityIdentity
source kind
ReleaseVersion
VCS revision
ContentIdentity
dependency edges
```

as executable supply-chain changes.

Machine-local retrieval settings are intentionally excluded so a developer cannot
silently commit a corporate mirror, token-bearing URL, or personal cache path as
package semantics.

The package manager must verify `ContentIdentity` after materialization before
executing/importing package source.

## Comparison conclusions

### Cargo

Cargo demonstrates the usefulness of a textual generated lockfile containing
exact package versions, source/revision data, checksums, and dependency edges. It
also shows the value of explicit lock-format generations and later compact formats
designed partly to reduce source-control merge conflicts.

Protos should adopt those properties while avoiding traversal/order-derived
identity and while keeping package identity separate from source location.

### npm

npm's `package-lock.json` demonstrates that an exact dependency tree belongs in
source control and that an explicit `lockfileVersion` is necessary as the format
evolves.

Protos should not copy npm's physical install-tree orientation, hidden
`node_modules/.package-lock.json`, or automatic old-lock augmentation during
normal operations.

### Go

`go.sum` demonstrates the value of committing cryptographic verification data,
but it is primarily a checksum record rather than a full exact contextual
dependency graph.

Protos needs a richer graph because `dep:<alias>` edges and coexistence of
multiple versions are first-class package-resolution facts.

## Rejected alternatives

### Binary lockfile

Rejected initially. It harms Git review/diff/merge ergonomics without solving a
problem that requires binary encoding.

### General YAML

Rejected. The lock is machine-owned and does not benefit from YAML's human
authoring flexibility.

### Raw JSON as initial choice

Viable but not preferred. It is easy to parse and canonicalize, but a narrow
line-oriented Protos lock grammar can produce cleaner graph diffs with fewer
structural delimiters.

JSON remains a reasonable machine protocol format for registry APIs.

### TOML merely because the manifest uses TOML

Rejected as a rationale. Manifest and lock have different authors and goals.

A restricted canonical TOML lock would be technically possible, but parser reuse
alone is not enough reason to couple the two formats.

### Physical install-tree lock

Rejected. Package placement/cache layout is non-semantic and must not define the
dependency graph.

### Ordinal node IDs

Rejected. They amplify diffs and make identity dependent on serialization order.

## Durability classification

Intended durable decisions:

```text
textual repository lock
machine-authored
committed to Git
one lock per resolution root
canonical deterministic serialization
explicit lock-format
explicit resolver-version
semantic resolution-input digest
stable identity-derived node references
explicit alias -> node edges
no machine-local state
no physical install-tree semantics
normal execution never rewrites lock
merge conflicts resolved from manifests + deterministic regeneration
```

Deliberately replaceable details:

```text
exact body punctuation/keywords after the F1A header
exact body line wrapping
whether diagnostic PackageLocator is always emitted
whether artifact transport digests are included
exact canonical escaping syntax
future compact lock-format generations
```

## Deferred package-tool architecture audit

A separate future audit is intentionally queued for the package-management tool
itself, including:

- whether the package manager is implemented primarily in Protos;
- the minimum irreducible host/bootstrap responsibilities;
- how bundled Protos tools are located and launched without project dependency
  resolution;
- capability injection for filesystem/network/crypto/process operations;
- separation of package policy from host mechanisms;
- whether TOML/lock parsing and resolver logic can live in bundled Protos modules;
- a single `protos` CLI frontend dispatching to bundled tools;
- versioning/updating of bundled tooling without reintroducing bootstrap cycles.

This lockfile audit does not prejudge those implementation decisions.

## Recommendation before implementation

After this audit, the package-system design stack is:

```text
distribution model
    defined

manifest model/encoding
    TOML 1.0 + strict Protos schema selected exploratorily

package identity/version/content/authority
    separated

constraint + resolver semantics
    defined exploratorily

lock graph + canonical physical-format direction
    defined exploratorily

package-tool self-hosting/bootstrap architecture
    deferred as next focused architecture audit before major tooling work
```

A first local/no-network package-system implementation should not begin until the
package-tool architecture audit has fixed which parts belong in the host versus
bundled Protos tooling.

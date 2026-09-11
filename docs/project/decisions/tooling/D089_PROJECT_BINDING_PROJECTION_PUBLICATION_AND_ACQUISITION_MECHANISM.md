# D089 — ProjectBinding projection publication and acquisition mechanism

Status: **RATIFIED — Candidate A″ selected**
Decision family: `Dxxx` implementation-independent tooling architecture
GitHub issue: #376
Primary consumer: `LM009-G3P-P3` / #373, then `LM009-G3` / #360
Triggered by: LM009-G3P P2 closure at `45cba862e47773fa5f83b77a6de6911f2426a811`
Allocated: 2026-09-11
Approved: 2026-09-11
Validation class: `GOVERNANCE_DOCUMENTATION_ONLY`

## Decision summary

Protos selects **Candidate A″ — repository-carried, Package-Tool-generated minimal
`protos.project` projection with dual freshness**.

The selected baseline is deliberately asymmetric:

- the canonical Package Tool owns package/project semantics and produces the
  portable projection;
- a later editor-neutral `ProjectBindingProvider` may validate the artifact and
  current metadata mechanically, but does not parse or reinterpret package
  semantics;
- the semantic freshness identity remains the already-owned
  `protos-resolution-input-v1` SHA-256 produced by Package Tool;
- a second `protos-project-metadata-v1` SHA-256 is mechanically recomputable from
  exact repository metadata bytes, allowing a later static language-server process
  to reject stale authority without running guest code;
- the exact candidate root supplies the physical project-root witness only after
  validation. No absolute machine path is persisted in `protos.project`;
- ordinary `.protos` source edits/additions/removals do not stale the projection.
  P1 remains the live bounded source-inventory authority inside already-authorized
  package roots; and
- missing, stale, malformed or unsupported `protos.project` fails that project
  binding closed. The baseline does not auto-run Package Tool or guest code as a
  repair/fallback path.

D089 resolves only the first concrete production/acquisition mechanism deferred by
D085. It does not change D082 workspace-symbol/index semantics, P1 source-membership
semantics, P2 carrier invariants, G4 definition semantics or LM009-H.

## Ratified artifact contract

The baseline portable artifact is exactly:

```text
protos.project
```

It is machine-authored repository state by default, analogous in lifecycle to
`protos.lock`, not an editor cache and not editor-specific configuration. It is not
hidden under `.protos/`, and its name does not encode a particular editor or the
internal Java `ProjectBinding` type.

The artifact contains no absolute path and no complete source-file inventory. It
contains only:

1. project-format generation;
2. the Package-Tool-owned semantic resolution-input identity;
3. the mechanically verifiable metadata-content identity;
4. the root workspace PackageId; and
5. ordered workspace-member canonical location -> PackageId mappings.

The generation-1 canonical text shape is:

```text
project-format 1
resolution-input protos-resolution-input-v1 sha256:<64-lowercase-hex>
metadata-content protos-project-metadata-v1 sha256:<64-lowercase-hex>

root workspace "<root-PackageId>"
workspace-member "<canonical-member-location>" workspace "<member-PackageId>"
...
```

Generation 1 is UTF-8 without BOM and uses LF line endings. Keywords and method/
algorithm tokens are exact lowercase ASCII spellings. Decimal `1` is canonical.
PackageId/member-location scalars use the same canonical qstring discipline already
owned by lock-format generation 1: one canonical encoded representation per Unicode
scalar sequence. Workspace-member records are ordered by the canonical UTF-8 bytes of
the location qstring, with PackageId qstring bytes as deterministic defensive
tie-breaker. Duplicate locations or PackageIds are invalid rather than normalized.

A generation-1 reader fails closed on unknown/extra records, unsupported generation,
unsupported method/algorithm, malformed qstrings, noncanonical ordering, duplicate
identity/location, invalid digest spelling, missing required records or trailing
unparsed material. This is a small data-only interoperability format; it is not a
second manifest language.

## Semantic freshness witness

The line

```text
resolution-input protos-resolution-input-v1 sha256:<digest>
```

carries the existing semantic package-resolution identity produced by the canonical
Package Tool authority. D089 does not redefine that method or permit static tooling to
recompute it independently.

The language server/provider treats this field as opaque Package-Tool-owned authority
except for exact supported method/algorithm/digest-shape validation. It does not parse
`protos.toml`, reconstruct dependency constraints, interpret workspace policy or derive
a new semantic resolution digest.

## Mechanical metadata freshness witness

The line

```text
metadata-content protos-project-metadata-v1 sha256:<digest>
```

is deliberately different. It is a byte-identity freshness witness with no package
semantics. A provider may recompute it without becoming a second Package Tool.

Generation `protos-project-metadata-v1` covers exactly these portable repository files:

1. root `protos.toml`;
2. one `<member-location>/protos.toml` for every workspace-member record in the
   projection, ordered by canonical member location; and
3. root `protos.lock` last.

Ordinary Protos source files are excluded.

The digest input stream is unambiguous and versioned. It is the SHA-256 of:

```text
ASCII("protos-project-metadata-v1\n")
for each input in the exact order above:
    ASCII(decimal UTF-8 byte length of canonical relative path)
    ASCII(":")
    UTF8(canonical relative path)
    ASCII("\n")
    ASCII(decimal exact content byte length)
    ASCII(":")
    exact file bytes
    ASCII("\n")
```

Canonical relative paths are exactly `protos.toml`,
`<canonical-member-location>/protos.toml`, and `protos.lock`; `/` is the portable
separator inherited from workspace-location policy. Decimal lengths contain no sign or
leading zero except the value `0`.

The content bytes are hashed exactly as stored. The provider does not decode or parse
them. Therefore comment-only, whitespace-only, encoding-byte or line-ending changes to
package metadata conservatively invalidate `protos.project`. This may create safe false
staleness, but never silently accepts changed package metadata.

If any listed file is missing, not an ordinary confined file, cannot be read completely,
changes during a coherent validation attempt, or produces a different digest, the
binding fails closed. P3 may use ordinary filesystem-stability techniques to ensure one
validation attempt observes a coherent byte set; it must not repair or reinterpret the
metadata.

## Exact candidate-root acquisition

D085's candidate/root distinction remains authoritative.

A path supplied by an editor, CLI, test harness or future client is only a candidate.
Generation-1 acquisition is:

```text
exact candidate root
    -> read exactly <candidate>/protos.project
    -> validate project-format and canonical text
    -> recompute/compare mechanical metadata witness using only listed exact files
    -> validate the portable package projection structurally
    -> materialize P2 canonicalProjectRoot from the exact validated candidate
    -> bind projected package locations through existing confined package-root authority
    -> P1 current-source inventory
```

The provider does not walk parents, children or siblings; does not locate the nearest
manifest/project; does not promote an LSP workspace folder to semantic identity; and
does not create projects from open documents.

The portable artifact never serializes P2's absolute `canonicalProjectRoot`. The exact
validated candidate root supplies that host-local field during acquisition.

## Publication lifecycle and crash behavior

`protos.project` is produced only by canonical Package Tool/package-authority operations
that already possess the validated package graph/resolution input needed to publish the
projection. D089 does not create a new editor-owned producer or require a new public CLI
spelling.

When an operation updates both canonical lock metadata and `protos.project`, the
preferred generation-1 order is:

```text
1. atomically publish the final canonical protos.lock
2. compute the final mechanical witness including those exact lock bytes
3. atomically publish the final canonical protos.project
```

Each target uses failure-safe atomic replacement through the existing metadata
publication substrate or an equivalent authority-preserving primitive. A cross-file
atomic transaction is not required: because the project witness includes `protos.lock`,
a crash between the two atomic replacements leaves the old/missing project artifact
mechanically stale rather than silently authoritative for the new lock.

A producer must validate the complete final artifact before replacing the target.
Temporary/staging names remain implementation detail and are not part of project
identity.

## Missing/stale behavior

Baseline behavior is fail-closed and pay-for-use:

- valid artifact -> exact ProjectBinding acquisition may proceed;
- missing artifact -> no project binding;
- stale mechanical witness -> no project binding;
- malformed/unsupported artifact -> no project binding;
- package-root confinement/identity mismatch -> no project binding.

Document-local LM009-G1/G2 service remains available where its existing contracts allow
it. Project-wide G3 semantics are unavailable for that candidate until canonical
package tooling republishes valid project authority.

The baseline does **not** automatically invoke Package Tool, spawn guest/Truffle
execution, run a discovery command, rewrite metadata or fall back to folder scanning.
A future explicitly approved opt-in refresh workflow may be layered above the same
provider contract without changing this baseline.

## Source-change behavior

`protos.project` intentionally does not contain the complete mutable source inventory.
Therefore:

```text
edit Foo.protos        -> projection remains valid
add Bar.protos         -> projection remains valid
remove Baz.protos      -> projection remains valid
```

P1 observes current canonical modules only inside the already-authorized package roots.
A metadata change that can affect project/package identity instead invalidates the
projection mechanically until Package Tool republishes it.

This keeps high-frequency source editing separate from low-frequency package authority.

## Comparative systems audit

The D089 audit compared acquisition/publication architecture, not general language
server quality.

### rust-analyzer / rust-project.json

rust-analyzer permits an external build authority to provide an analyzer-facing project
graph rather than embedding every build system's semantics into analysis. Explicit
linked project descriptions and non-Cargo project models demonstrate the durable
`producer -> versioned data -> analyzer` boundary.

**Lesson:** Protos static tooling should consume package-owned project data, not
reimplement the Package Tool.

### CMake File API

CMake owns versioned reply objects generated for tooling clients. Consumers read
producer-owned semantic descriptions instead of parsing CMake project language.

**Lesson:** a versioned producer-owned introspection artifact is a mature mechanism, and
its producer can evolve independently from consumers.

### Meson introspection

Meson publishes machine-oriented introspection state and has an explicit completion /
refresh lifecycle around generated metadata.

**Lesson:** generated project descriptions can remain cheap and editor-neutral while
build/project semantics stay with their producer.

### clangd / compilation databases

clangd consumes build-system-generated compilation databases and composes live,
background, static and remote index layers. Its path-search/fallback heuristics are
useful for C++ ergonomics but are intentionally rejected by D082/D085 for Protos
project identity.

**Lesson:** generated descriptions scale; project discovery heuristics need not be part
of the architecture.

### BSP / SourceKit-LSP

BSP gives the strongest future live-provider precedent: target identity and source
membership come from build authority, not URI ancestry; sources are queried explicitly
and changes can be notified.

**Lesson:** D085's provider boundary should remain replaceable so Protos can migrate to
a daemon/BSP/remote provider when generated sources, dynamic graphs or remote builds
justify it.

### Metals / Bloop / BSP

Metals demonstrates both stages: generated Bloop project descriptions and later/direct
BSP integration.

**Lesson:** selecting a generated artifact now does not preclude migration to a live
provider later.

### HLS / hie-bios

hie-bios makes the build tool responsible for describing a component environment. A
subprocess/cradle can provide exact data and dependency files can trigger reload.

**Lesson:** authority separation is strong, but repeated tool/process acquisition costs
more than Protos needs for the baseline static path.

### TypeScript tsserver

Configured/ExternalProject modes show explicit externally supplied project membership;
InferredProject provides convenient fallback but makes open/path context participate in
project formation.

**Lesson:** preserve explicit project data; reject inferred-project behavior for Protos.

### Bazel-oriented generated descriptions

Large monorepo integrations show that generated project metadata and target-scoped
views can coexist, while a later build-server protocol can take over where graph size
or dynamism warrants it.

**Lesson:** artifact-first does not block large-scale evolution.

### gopls

gopls has excellent snapshot/incremental architecture but deliberately includes
workspace/open-file/module inference to reduce configuration friction.

**Lesson:** retain incremental custody concepts, not inferred project identity.

## Prior-art suitability for D089

| System / mechanism | Future endurance | Scalability | Protos philosophy | Total / 30 |
| --- | ---: | ---: | ---: | ---: |
| rust-analyzer explicit/generated project model | 10 | 10 | 9 | 29 |
| CMake File API producer-owned replies | 10 | 9 | 10 | 29 |
| SourceKit-LSP + BSP | 10 | 10 | 9 | 29 |
| Meson generated introspection | 9 | 9 | 10 | 28 |
| clangd generated compilation database/index layering | 10 | 10 | 8 | 28 |
| Metals generated/BSP evolution | 10 | 9 | 9 | 28 |
| HLS + hie-bios build-owned description | 9 | 7 | 9 | 25 |
| TypeScript ExternalProject | 9 | 9 | 6 | 24 |
| gopls inferred workspace acquisition | 9 | 9 | 4 | 22 |

The recurring durable pattern is:

```text
build/package authority
    -> explicit versioned project description/provider
    -> language analysis
```

A live provider becomes worthwhile when project graphs become sufficiently dynamic or
large to justify its lifecycle cost.

## Candidate set

### A — generated minimal artifact

Correct authority direction and low steady-state cost, but an artifact carrying only an
opaque semantic digest cannot prove to a no-guest later consumer that metadata remains
unchanged.

### A″ — generated artifact + dual freshness — SELECTED

Adds one mechanically recomputable metadata byte witness while leaving semantic package
freshness owned by Package Tool. This is the smallest useful candidate that satisfies
both one-authority and zero-guest steady-state constraints.

### B — on-demand Package Tool subprocess

Faithful authority, but missing/stale binding implies process/guest startup and a larger
failure/lifecycle surface. Retained as a possible explicit future refresh mechanism,
not baseline acquisition.

### C — artifact + automatic subprocess refresh

Good convenience, but silently turns static editor startup into guest execution when
metadata is stale. Retained as possible later opt-in behavior.

### D — long-lived BSP-like/package-provider service

Best when generated sources, remote builds, very large dynamic graphs or incompatible
toolchains justify the protocol/process institution. Premature for the current project.

### E — client/launcher-supplied descriptor

Mechanically workable, but risks making editor/launcher configuration the practical
source of project identity. Clients may supply candidates, not canonical package data.

### F — reuse/extend protos.lock directly

Attractive physical consolidation, but there is no current editor-neutral host/shared
lock reader. Reading it semantically in LSP would either create a second lock parser/
package ABI or require a broader shared package-metadata refactor not selected by D085.
It also couples ProjectBinding acquisition to lock-format evolution.

### G — host/LSP manifest+lock reconstruction

Rejected: creates the second package authority forbidden by D085.

### H — defer

Safe but provides no route to G3 after P1/P2 have isolated the exact missing mechanism.

## Mandatory ten-axis scoring

Scores 1–5. Confidence is **HIGH** unless stated otherwise.

| Criterion | A | A″ | B | C | D | E | F | G | H |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariant preservation | 3 | **5** | 5 | 5 | 5 | 4 | 5 | 2 | 5 |
| Protos alignment | 5 | **5** | 4 | 4 | 4 | 3 | 3 | 1 | 5 |
| Future-option resilience | 4 | **5** | 4 | 5 | 5 | 4 | 3 | 2 | 5 |
| Scalability | 5 | **5** | 3 | 4 | 5 | 4 | 5 | 4 | 5 |
| Conceptual simplicity | 5 | 4 | 4 | 3 | 2 | 4 | 3 | 3 | 5 |
| Portability / implementation freedom | 5 | **5** | 4 | 4 | 5 | 4 | 5 | 3 | 5 |
| Runtime / resource cost | 5 | **5** | 2 | 4 | 3 | 5 | 5 | 5 | 5 |
| Failure / operability | 3 | 4 | 3 | 4 | 4 | 3 | 5 | 2 | 5 |
| Reversibility / migration cost | 4 | **5** | 4 | 5 | 5 | 4 | 2 | 2 | 5 |
| Evidence maturity / implementation risk | 5 | 4 | 5 | 5 | 5 | 4 | 5 | 4 | 5 |
| **Total / 50** | **44** | **47** | **38** | **43** | **43** | **39** | **41** | **28** | **50** |

Arithmetic is not authority. H has the largest safety score because doing nothing
cannot introduce the mechanism, but its feature utility is zero. F remains
operationally attractive but violates the already-selected separation unless a future
shared package metadata ABI exists. A″ is the strongest useful candidate.

## Owner-priority scoring

| Candidate | Future endurance /10 | Scalability /10 | Protos philosophy /10 | Total |
| --- | ---: | ---: | ---: | ---: |
| **A″ dual-freshness `protos.project`** | **10** | **10** | **10** | **30** |
| H defer | 10 | 10 | 10 | 30 (feature utility 0) |
| D BSP/service now | 10 | 10 | 8 | 28 |
| A basic artifact | 8 | 10 | 9 | 27 |
| C artifact + auto-refresh | 9 | 8 | 8 | 25 |
| F reuse lock directly | 7 | 10 | 6 | 23 |
| B subprocess | 8 | 6 | 8 | 22 |
| E client descriptor | 8 | 8 | 6 | 22 |
| G LSP reconstruction | 4 | 8 | 2 | 14 |

## Future/scalability stress analysis

- **Large source tree:** acquisition hashes only package metadata, not source contents;
  P1/G3 remain independently incremental.
- **Many projects:** each exact candidate validates independently; no required process
  per root and one stale project does not poison another.
- **Normal source churn:** no projection regeneration.
- **Manifest/workspace/lock churn:** deterministic fail-closed invalidation until
  canonical Package Tool publication catches up.
- **Fresh clone/CI/devcontainer:** committed `protos.project` can bind without first
  executing guest Package Tool code.
- **Native Image / non-Truffle runtime / Bytecode DSL evolution:** acquisition depends
  on canonical text, filesystem bytes and hashing, not guest runtime internals.
- **Generated/virtual sources later:** replace the provider with a live build/package
  service while retaining the ProjectBinding contract.
- **Huge remote monorepo:** D082's index may become remote and D085's provider may become
  remote independently of this baseline artifact.

## Failure model

D089 prefers visible unavailability to guessed authority. No project binding is created
when artifact parsing, exact-root validation, mechanical freshness, package confinement,
identity consistency or supported-generation checks fail. There is no silent discovery,
auto-refresh or source-tree fallback.

Atomic single-file replacement plus the lock-inclusive witness prevents a partially
updated metadata pair from becoming silently accepted authority. A producer crash may
leave `protos.project` stale or absent, which is recoverable by an explicit canonical
Package Tool operation.

## Strongest argument against A″

A″ introduces a second committed machine-generated project file that partially repeats
facts already encoded in `protos.lock`. Raw-byte freshness also intentionally
invalidates on semantically irrelevant metadata formatting/comment changes. This adds
repository churn and one more format to maintain.

The cost is accepted because the repeated state is tiny and low-churn, while the
benefit is structural: a later static process can validate project authority without
becoming a second package parser/semantic engine and without running guest code.

## Regret scenario and escape path

A future shared host-neutral package metadata ABI, always-on Package Tool service,
generated-source build graph or remote build system could make `protos.project`
redundant.

The escape path is intentionally preserved by D085: add a new
`ProjectBindingProvider` implementation (for example BSP/package daemon/remote service),
keep the artifact provider for a compatibility window, then deprecate generation if the
new provider proves sufficient. P1, P2 and D082/G3 index semantics do not change.

`project-format` and `metadata-content` method generations also permit the artifact and
witness to evolve fail-closed without reinterpreting old generations.

## Explicit deferrals

D089 does **not** select:

- a public Package Tool CLI command dedicated only to `protos.project` refresh;
- automatic editor-triggered Package Tool execution;
- a BSP/package-daemon wire protocol;
- remote project-provider hosting;
- generated/virtual-source semantics;
- dependency or Standard Library workspace-symbol scope;
- persistent/remote symbol-index format;
- G3 fuzzy ranking/result-cap policy;
- G4 definition identity/resolution;
- LM009-H completion/hover/signature/references; or
- the retirement date of `protos.project` if a future shared provider supersedes it.

Each remains subject to its existing owner or a future explicit decision if it crosses a
substantive boundary.

## Downstream release

Ratifying D089 releases **LM009-G3P P3** to implement exactly the approved
`protos.project` producer/reader/provider path and exact candidate-root validation.

P3 may not add automatic Package Tool fallback, discovery heuristics, semantic manifest/
lock parsing or G3 indexing. P4 remains responsible for multi-root isolation, duplicate
binding rejection, freshness invalidation and static no-guest closure evidence.

`LM009-G3` remains **BLOCKED_BY_PROJECT_BINDING_IMPLEMENTATION** until P3 and P4 are
published and #373 closes. D089 ratification alone does not start workspace-symbol
indexing.
